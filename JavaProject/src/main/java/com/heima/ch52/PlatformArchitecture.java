/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：52-08
 * 课名：综合毕业案例①：电商平台总架构
 * ------------------------------------------------------------
 * 知识点：
 *   - **毕业案例把全书串成一个系统**：电商平台——网关（37/47）-> 交易（49 建模/39 分片）-> 支付（51）-> 履约（29/43）-> 搜索（38）-> 推荐（40/44）-> 可观测（42）-> 发布（41）-> 容器化（37）-> 数据湖（46）-> AI 客服（40/44）。**每一章是系统的一块拼图**。
 *   - **总架构的表达**：一页架构图（分层：接入/服务/数据/基础设施）+ 关键决策表（每个组件的选型理由引用 ADR）+ 非功能指标表（52-01）。**表达也是能力：让评审者 10 分钟看懂**。
 *   - **组件间的连接语义**：同步调用只出现在强实时路径（下单->库存），其余事件驱动（订单事件扇出履约/积分/搜索）——**52-03 决策树的系统级应用**。
 *   - **规模假设驱动**：峰值 3 万 QPS、订单三年 5.4 亿 -> 16 分片、搜索 ES 5 分片、推理池 4 卡——**每个数字可追溯到 52-01 估算**。
 *   - 易错点：架构图画了组件没画数据流（评审者看不到请求怎么走）；决策无引用（为什么 ES 5 分片说不出）；指标无来源（拍脑袋数字）；图与实际系统脱节（架构图只在评审时存在）。
 * ============================================================
 */

package com.heima.ch52;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class PlatformArchitecture {
    record Component(String layer, String name, String tech, String adrRef) {
    }

    static void onePageArch() {
        System.out.println("【电商平台一页架构（分层 + 关键决策引用）】");
        List<Component> comps = List.of(
                new Component("接入", "API 网关", "Mesh + 限流（47-03）", "ADR-011"),
                new Component("服务", "交易服务", "模块化单体->微服务（49-08）", "ADR-004"),
                new Component("服务", "支付服务", "状态机+多渠道（51 章）", "ADR-009"),
                new Component("服务", "搜索服务", "ES 5 分片 + CDC（38 章）", "ADR-007"),
                new Component("服务", "AI 客服", "RAG+网关路由（40/44 章）", "ADR-014"),
                new Component("数据", "订单库", "16 分片（39 章）+ 基因 ID", "ADR-005"),
                new Component("数据", "湖仓", "Iceberg + Flink CDC（46 章）", "ADR-016"),
                new Component("基础设施", "K8s + Mesh", "滚动/金丝雀（37/47 章）", "ADR-002"),
                new Component("基础设施", "可观测", "日志/指标/链路（42 章）", "ADR-013"));
        for (Component c : comps) {
            System.out.printf("  %-8s %-10s %-30s 依据：%s%n", c.layer(), c.name(), c.tech(), c.adrRef());
        }
        System.out.println("  每个组件都有 ADR 引用 —— 架构与决策一体（52-02）");
        System.out.println();
    }

    static void dataFlow() {
        System.out.println("【数据流：一个订单的完整生命】");
        List<String> flow = List.of(
                "用户 -> 网关（限流/鉴权）-> 交易服务（订单聚合落库）",
                "  -> 事件 OrderCreated -> 营销预占券 / 搜索索引预告",
                "用户支付 -> 支付服务（51 章状态机）-> PaymentSucceeded",
                "  -> 履约建运单 / 库存扣减确认 / 账务分录 / 搜索可见 / AI 客服知识可答",
                "T+1 -> CDC 进湖 -> 指标/报表/特征（46 章）",
                "全程 trace_id 贯通（42 章）-> 任意环节可回放");
        for (String s : flow) {
            System.out.println("  " + s);
        }
        System.out.println("  同步仅出现在强实时路径；其余全部事件驱动（52-03 树）");
        System.out.println();
    }

    static void nfrBoard() {
        System.out.println("【非功能指标看板（52-01 表的实现态）】");
        Map<String, String> nfr = new LinkedHashMap<>();
        nfr.put("下单 P99", "280ms（目标 300）✓");
        nfr.put("支付可用性", "99.992%（目标 99.99）✓");
        nfr.put("搜索延迟", "P99 180ms（目标 200）✓");
        nfr.put("基础设施成本", "GMV 2.6%（上限 3%）✓");
        nfr.put("发布频率", "日均 3.2 次（41 章流水线支撑）");
        for (Map.Entry<String, String> e : nfr.entrySet()) {
            System.out.printf("  %-10s %s%n", e.getKey(), e.getValue());
        }
        System.out.println("  指标全部可追溯：SLO/预算/告警三件套在线（42 章）");
    }

    public static void main(String[] args) {
        onePageArch();
        dataFlow();
        nfrBoard();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 为毕业案例画出你的一页架构图 + 决策表 + 指标看板，找同伴做 10 分钟评审测试。
 * 2. 把 48 章里你做过的综合案例标记到总架构的对应组件上，检查覆盖与缺口。
 * 3. 思考题：为什么"架构图必须画数据流而不只是组件"？从评审者理解成本与系统行为可验证性分析。
 * ============================================================
 */
