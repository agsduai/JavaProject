/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：43-01
 * 课名：流与批的分界
 * ------------------------------------------------------------
 * 知识点：
 *   - **批处理是"数据先齐再算"，流处理是"数据边来边算"**：日报/月报跑批（Hive/Spark SQL）等 24 点切分区；实时大屏/实时风控（Flink）每秒处理增量。**分界的本质不是速度，是有无"数据终结点"**。
 *   - **Lambda 架构的历史包袱**：早期实时框架不保证正确性，于是"批出准数 + 流出快数"两套代码双跑再对账——同样的逻辑写两遍是维护地狱。**现代流处理（Flink 的 Exactly-Once + 事件时间）让"流批一体"成为可能**：一套代码，流跑实时、批跑历史回放。
 *   - **什么时候必须流处理**：延迟要求秒级以内（风控拦截要在支付前）、持续聚合（大盘每秒刷新）、CDC 增量同步（binlog 不能等天级）。**能跑批的就别上流**——流系统的运维复杂度（状态、水位线、反压）远高于批。
 *   - **流处理的心智模型转变**：数据无界（没有"跑完"）、计算常驻（7×24 进程）、故障要自恢复（状态可重建）、时间要显式处理（事件时间 vs 处理时间，43-02）。**把批思维带进流系统，第一个坑就是"为什么数据对不上"**。
 *   - **流的三个可靠性等级**：At-most-once（可能丢）、At-least-once（可能重）、Exactly-once（不丢不重）。等级越高成本越高，**按业务选等级而不是默认最高**（43-06 细讲）。
 *   - **Java 工程师的位置**：实时计算的主力框架 Flink 是 JVM 生态，Java 是一等公民。业务侧更多是"把逻辑交付给流系统"（UDF、规则引擎、特征计算）与"消费流的结果"。
 *   - 易错点：把实时当"跑得快的批"（忽略无界与常驻）；两套代码双跑不对账（Lambda 架构的隐性成本）；所有场景都上流处理（运维复杂度爆炸）；忽略流系统的常驻成本（7×24 集群哪怕没有数据也在烧钱）。
 * ============================================================
 */

package com.heima.ch43;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class StreamVsBatch {
    record Order(long id, long ts, double amount) {
    }

    static void batchMindset() {
        System.out.println("【批处理：数据先齐再算】");
        List<Order> dayOrders = List.of(
                new Order(1, dayStart() + 100, 69.0),
                new Order(2, dayStart() + 200, 199.0),
                new Order(3, dayStart() + 300, 29.0));
        double total = dayOrders.stream().mapToDouble(Order::amount).sum();
        System.out.printf("  23:59:59 后跑批：当日 %d 单，GMV %.1f 元 —— 输入完整，结果确定%n",
                dayOrders.size(), total);
        System.out.println("  特点：输入有界、可重跑、延迟天级、运维简单");
        System.out.println();
    }

    static long dayStart() {
        return 1763347200000L;
    }

    static void streamMindset() {
        System.out.println("【流处理：数据边来边算】");
        List<Order> arriving = List.of(
                new Order(4, dayStart() + 400, 59.0),
                new Order(5, dayStart() + 500, 129.0));
        double running = 297.0;   // 之前累计
        for (Order o : arriving) {
            running += o.amount();
            System.out.printf("  t+%dms 到达订单 %d -> 实时 GMV 刷新为 %.1f 元%n",
                    o.ts() - dayStart(), o.id(), running);
        }
        System.out.println("  特点：输入无界、计算常驻 7×24、秒级输出、要管状态与容错");
        System.out.println("  关键问题：这条“实时 GMV”到 24 点会和批跑的一致吗？（43-02/06 回答）");
        System.out.println();
    }

    static void whenToStream() {
        System.out.println("【决策：必须流 vs 应该批】");
        String[][] rows = {
                {"支付前拦截风险", "流（秒级）", "批等不到这笔交易发生"},
                {"实时大屏/监控", "流", "展示要求秒级刷新"},
                {"binlog 增量同步", "流（CDC）", "天级批无法满足一致性窗口"},
                {"日报/月报/对账", "批", "延迟可接受，批更简单可靠"},
                {"历史特征回填", "批", "输入是有界的历史数据"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-14s %-12s %s%n", r[0], r[1], r[2]);
        }
        System.out.println();
    }

    static void lambdaCost() {
        System.out.println("【Lambda 架构的双份成本】");
        Map<String, String> dup = new LinkedHashMap<>();
        dup.put("业务逻辑", "批 SQL 一份 + 流代码一份（同逻辑两实现）");
        dup.put("对账", "每日批跑完与流结果 diff，差异排障");
        dup.put("修复", "逻辑变更要改两处，先后上线节奏不一致就出差异");
        for (Map.Entry<String, String> e : dup.entrySet()) {
            System.out.printf("  %-6s %s%n", e.getKey(), e.getValue());
        }
        System.out.println("  现代解法：流批一体（同一套 API）+ 事件时间 + Exactly-Once");
        System.out.println("  流结果可信后，批只负责历史回填与审计");
    }

    public static void main(String[] args) {
        batchMindset();
        streamMindset();
        whenToStream();
        lambdaCost();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 为你的业务列一张"流 vs 批"决策表（≥6 个场景），标注选择与理由，找出至少一个"能批就别流"的场景。
 * 2. 实现"流式 GMV"模拟器：逐条到达订单实时累加，同时按天分桶，验证 24 点切分时的边界处理。
 * 3. 思考题：为什么说"把批思维带进流系统第一个坑就是数据对不上"？列出批思维在流系统里失效的三个假设。
 * ============================================================
 */
