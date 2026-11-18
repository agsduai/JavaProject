/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：48-09
 * 课名：成本工程
 * ------------------------------------------------------------
 * 知识点：
 *   - **推理成本的构成**：GPU 时长成本（常驻 + 峰值扩容）+ token 计费成本（云端 API）+ 存储与网络（权重分发、出网）。**优化杠杆按大到小：路由降级（44-08）> 缓存复用 > 输出长度控制 > 引擎加速（48-05）> 采购议价**。
 *   - **输出长度是成本的第一变量**：回答越长成本越高——**控制手段**：max_tokens 上限、Prompt 明确“简短回答”、结构化输出（JSON 更短）、按意图配置不同 max_tokens（分类 50 token、摘要 400 token）。**max_tokens 忘配是常见成本事故**。
 *   - **缓存复用的三层**：语义缓存（40-10）、前缀 KV 共享（48-02）、模板结果缓存（FAQ 固定话术）——**三层命中率加起来通常节省 20~40%**。
 *   - **批处理任务走离线池**：离线评测、数据回填、批量摘要——用 Spot/低优先级实例（47-08），**与在线池物理隔离**（互不抢占）。
 *   - **成本归因与预算**：44-08 网关记账（业务方/意图维度）+ 月度预算与超额告警——**成本报表进业务方视野**（谁消耗谁负责），优化动作可追踪。
 *   - **蒸馏降本的长线**：44-06 用大模型造数据微调小模型替代——**在线成本下降一个数量级**，但质量门禁不可省。
 *   - 易错点：成本只算总量不归因（不知道谁烧的）；max_tokens 缺省（失控输出）；缓存阈值过松（错误答案扩散，40-10 教训）；离线任务挤占在线池（大促当天评测任务把线上打挂）。
 * ============================================================
 */

package com.heima.ch48;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class InferenceCost {
    static void levers() {
        System.out.println("【成本杠杆：按收益排序】");
        record Lever(String name, String action, String saving) {
        }
        List<Lever> levers = List.of(
                new Lever("路由降级", "分类/粗活走小模型（44-08）", "-60~80% 单次成本"),
                new Lever("缓存三层", "语义缓存+前缀共享+模板缓存", "-20~40% 总量"),
                new Lever("输出控制", "max_tokens + 简短指令 + 结构化", "-30% 输出长度"),
                new Lever("引擎加速", "quantization + batching（48-05）", "吞吐翻倍=单位成本减半"),
                new Lever("蒸馏替换", "小模型替代大模型（44-06）", "长线数量级下降"));
        for (Lever l : levers) {
            System.out.printf("  %-8s %-30s %s%n", l.name(), l.action(), l.saving());
        }
        System.out.println();
    }

    static void maxTokensIncident() {
        System.out.println("【max_tokens 缺省事故：一句话回答变千字文】");
        record Intent(String name, int properMax, int missingMax, double costFactor) {
        }
        List<Intent> intents = List.of(
                new Intent("意图分类", 50, 4096, 8.0),
                new Intent("订单查询话术", 120, 4096, 4.0),
                new Intent("营销文案", 1500, 4096, 1.0));
        for (Intent i : intents) {
            System.out.printf("  %-10s 合理上限=%-5d 缺省=%-5d 成本放大 ×%.1f%n",
                    i.name(), i.properMax, i.missingMax, i.costFactor);
        }
        System.out.println("  修复：按意图配置 max_tokens + Prompt 写明“只输出 JSON”");
        System.out.println("  监控：输出长度分布突变（P50 翻倍）-> 配置回归告警");
        System.out.println();
    }

    static void costAttribution() {
        System.out.println("【归因报表：谁在烧钱】");
        record Row(String biz, String intent, long tokens, double cost, String action) {
        }
        List<Row> rows = List.of(
                new Row("客服", "复杂问答", 42_000_000, 1680.0, "路由 30% 到本地池"),
                new Row("客服", "意图分类", 8_000_000, 32.0, "已优化"),
                new Row("运营", "周报生成", 6_000_000, 240.0, "输出控制 -30%"),
                new Row("数据", "离线评测", 5_000_000, 0.0, "迁离线 Spot 池"));
        for (Row r : rows) {
            System.out.printf("  %-4s %-8s %10d tok  %8.0f 元  动作：%s%n",
                    r.biz(), r.intent(), r.tokens(), r.cost(), r.action());
        }
        System.out.println("  月度例会：Top3 成本项必有优化动作与目标值（41-10 DORA 的成本版）");
        System.out.println();
    }

    static void offlinePool() {
        System.out.println("【离线批任务与在线池隔离】");
        List<String> rules = List.of(
                "物理隔离：离线评测/回填用独立 Spot 节点池（47-08）",
                "优先级：在线请求永远 > 离线任务（抢占方向单向）",
                "错峰：离线任务调度到夜间低谷",
                "限额：离线池消耗上限，防止失控批量任务烧穿预算");
        for (String r : rules) {
            System.out.println("  - " + r);
        }
        System.out.println("  事故对照：评测任务与在线同池 -> 大促当天被打挂");
    }

    public static void main(String[] args) {
        levers();
        maxTokensIncident();
        costAttribution();
        offlinePool();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 给你的 AI 功能做成本归因报表（按业务方/意图），找出 Top1 成本项并落地一个优化杠杆，量化前后差异。
 * 2. 排查 max_tokens 配置：列出全部意图的合理上限与当前配置，找出缺失或过大的项并修复。
 * 3. 思考题：为什么“路由降级”是收益最大的成本杠杆？从流量分布（分类类流量占比）与单价差的角度量化。
 * ============================================================
 */
