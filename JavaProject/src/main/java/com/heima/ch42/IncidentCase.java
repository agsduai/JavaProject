/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：42-11
 * 课名：综合案例：一次线上事故的可观测复盘
 * ------------------------------------------------------------
 * 知识点：
 *   - **用三支柱 + 剖析完整复盘一次真实形态的事故**：大促期间下单 P99 从 180ms 涨到 2.1s、错误率 0.3%->4%。按"告警 -> 指标定位 -> 日志收窄 -> 链路锁定 -> 剖析定根因 -> 修复 -> 复盘改进"的完整路径走一遍，**本章之前每一课的工具都在这条路径上各就各位**。
 *   - **指标层的判断**：错误率与延迟同时劣化但 CPU 正常 -> 排除容量问题；按 route 维度切片 -> 只有 /order 劣化；按 version 维度 -> 新旧版本都慢 -> **排除新版本引入**（版本维度指标在 41-07 埋下的价值）。方向收敛到"共享下游"。
 *   - **日志层的收窄**：按 trace_id 抽慢请求 -> 顶部模式是 stock 服务 db.update 慢；按 orderId 聚合 -> 慢请求集中在"秒杀商品"。**日志的分组统计（而非逐条阅读）给出模式**。
 *   - **链路层的锁定**：慢 trace 的 span 树显示 stock.deduct 内 db.update 占 92%；对比正常 trace -> SQL 形态差异（`SELECT ... FOR UPDATE` 的行锁竞争）。
 *   - **剖析层的根因**：JFR/锁剖析显示热点在单行 update；结合 DB 侧 `SHOW PROCESSLIST` -> **热点行锁**：秒杀商品的库存行被全部请求串行竞争。第 32 章（CAS 预扣）与第 39 章（分片）的方案在此会师。
 *   - **复盘产出**：改进项落在系统——库存扣减改 Redis 预扣（第 32 章方案）、慢 SQL 进 CI 检查、连接池饱和度告警提前、大促预案增加热点商品压测。**一次事故让整个可观测与架构体系进化一档**。
 *   - 易错点：复盘只写"库存服务慢了"没有根因链（下次复发）；修复只改现象（加超时）不改结构（行锁竞争仍在）；事故期间先优化后恢复（正确顺序：先恢复业务——切流/降级，再优化）。
 * ============================================================
 */

package com.heima.ch42;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class IncidentCase {
    record Step(int no, String pillar, String question, String finding) {
    }

    static void investigation() {
        System.out.println("【事故：大促下单 P99 180ms -> 2.1s，错误率 4%】");
        List<Step> steps = List.of(
                new Step(1, "告警", "要不要介入？", "P1：成功率 96%（阈值 99%），20:31 触发"),
                new Step(2, "指标", "哪里坏了？", "按 route 切片：仅 /order 劣化；按 version：新旧都慢 -> 共享下游问题"),
                new Step(3, "指标", "资源是瓶颈吗？", "CPU 45%、连接池 30% 正常 -> 排除容量，指向锁/等待"),
                new Step(4, "日志", "什么模式？", "慢 trace 的 top 模式：stock db.update；慢请求集中在秒杀商品"),
                new Step(5, "链路", "慢在哪一跳？", "span 树：stock.deduct 内 db.update 占 92%；SQL 带 FOR UPDATE"),
                new Step(6, "剖析", "根因是什么？", "锁剖析热点：单行 update 行锁竞争（热点商品串行化）"),
                new Step(7, "处置", "先恢复还是先优化？", "先恢复：秒杀商品限流 + 降级非秒杀链路 -> P99 回落 300ms"));
        for (Step s : steps) {
            System.out.printf("  步骤%d [%-4s] %-14s %s%n", s.no(), s.pillar(), s.question(), s.finding());
        }
        System.out.println("  恢复时间 18 分钟；每一步的问题都有数据回答");
        System.out.println();
    }

    static void rootCauseChain() {
        System.out.println("【根因链：连问三层为什么】");
        List<String> chain = List.of(
                "为什么慢？秒杀商品的库存行被 FOR UPDATE 串行竞争（热点行锁）",
                "为什么热点行锁？库存扣减直接打 DB，单商品单行成为全局串行点",
                "为什么没有预案？大促预案压测用的是均匀商品分布，热点商品场景缺失");
        for (int i = 0; i < chain.size(); i++) {
            System.out.printf("  为什么#%d：%s%n", i + 1, chain.get(i));
        }
        System.out.println("  根因结论：架构级（DB 行锁扛热点），修复 = Redis 预扣 + 分段库存（32/39 章方案）");
        System.out.println();
    }

    static void improvements() {
        System.out.println("【改进项：落在系统上，带负责人与验证】");
        record Item(String action, String owner, String verify) {
        }
        List<Item> items = List.of(
                new Item("库存扣减改 Redis 预扣 + 异步落库", "库存组", "压测 10 万 QPS 无行锁等待"),
                new Item("秒杀链路热点商品专项压测进大促预案", "测试组", "预案清单新增用例"),
                new Item("慢 SQL（FOR UPDATE on 热点）进 CI 检查", "平台组", "CI 规则上线"),
                new Item("锁等待剖析接入告警联动", "平台组", "下次 CPU 事故 5 分钟出热点"));
        for (Item i : items) {
            System.out.printf("  [ ] %-34s 负责人=%-6s 验证=%s%n", i.action(), i.owner(), i.verify());
        }
        System.out.println("  复盘有效的唯一标准：三个月后同类问题不再复发");
    }

    public static void main(String[] args) {
        investigation();
        rootCauseChain();
        improvements();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 把你经历过的（或虚构的）一次事故按本章七步路径完整复盘，每一步标注"用了哪个可观测工具、看到了什么数据"。
 * 2. 针对一次"CPU 正常但接口慢"的模拟事故，写出指标/日志/链路/剖析四层的排查脚本（查什么、怎么判断）。
 * 3. 思考题：为什么事故处置的正确顺序是"先恢复业务再优化结构"？从用户影响面与修复风险两个角度分析本章第 7 步的决策。
 * ============================================================
 */
