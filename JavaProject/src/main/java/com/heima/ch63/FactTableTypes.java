/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：63-06
 * 课名：事实表三种类型
 * ------------------------------------------------------------
 * 知识点：
 *   - **事务事实表**：**每次业务事件一行**（一次下单/一次支付/一次退款）——粒度最细、最灵活，**不可变**（事件是事实，不更新）；**分析任何维度都能下钻**，是事实表的主力。
 *   - **周期快照事实表**：**固定周期一行**（每天/每月的账户余额、库存量）——用于**状态趋势分析**；**半可加度量**（余额可跨维度加、不能跨时间加，63-02）。
 *   - **累积快照事实表**：**一个业务对象一行，随流程推进更新多个时间戳**（下单时间/支付时间/发货时间/完成时间）——**用于流程效率分析**（各环节耗时、漏斗转化）；**是唯一"会被更新"的事实表**。
 *   - **三者的选择**：**"发生了什么"用事务表**（订单、支付、退款）；**"现在的状态"用周期快照**（库存、余额、用户数）；**"流程走了多久"用累积快照**（订单全生命周期）。
 *   - **与 43/59 章的关系**：事务事实表常由 CDC 或流处理写入（43-09）；周期快照由批处理生成（59-11）；累积快照需要"状态合并"（按业务主键 upsert，59-10 的 MERGE）。
 *   - 易错点：用事务表算"当前库存"（要全量重算，慢）；用周期快照算"交易明细"（粒度不够）；累积快照的时间戳不更新（漏斗分析失效）；三种表混在一个 Topic/表里。
 * ============================================================
 */

package com.heima.ch63;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class FactTableTypes {
    record TxnFact(long eventId, String orderId, String type, long amountCents, long atMs) {
    }

    record SnapshotFact(String date, int sku, int stock, long stockValueCents) {
    }

    record AccumulatingFact(String orderId, Long placedAt, Long paidAt, Long shippedAt,
                            Long finishedAt, String status) {
    }

    static void transactionFact() {
        System.out.println("【事务事实表：每次事件一行】");
        List<TxnFact> facts = List.of(
                new TxnFact(1, "9001", "CREATED", 6900, 1000),
                new TxnFact(2, "9001", "PAID", 6900, 1100),
                new TxnFact(3, "9002", "CREATED", 3900, 1200),
                new TxnFact(4, "9002", "REFUNDED", -3900, 1300));
        facts.forEach(f -> System.out.printf("  #%d %s %-9s %6d分 @%d%n",
                f.eventId(), f.orderId(), f.type(), f.amountCents(), f.atMs()));
        long gmv = facts.stream().filter(f -> f.type().equals("PAID"))
                .mapToLong(TxnFact::amountCents).sum();
        System.out.println("  GMV（支付金额）= " + gmv + " 分（可加、可任意维度下钻）");
        System.out.println();
    }

    static void periodicSnapshot() {
        System.out.println("【周期快照：每天一行，记录状态】");
        List<SnapshotFact> snaps = List.of(
                new SnapshotFact("2026-11-16", 1, 100, 690_000),
                new SnapshotFact("2026-11-17", 1, 80, 552_000),
                new SnapshotFact("2026-11-18", 1, 60, 414_000));
        snaps.forEach(s -> System.out.printf("  %s sku=%d 库存=%-4d 库存金额=%d分%n",
                s.date(), s.sku(), s.stock(), s.stockValueCents()));
        long sumAcrossTime = snaps.stream().mapToLong(SnapshotFact::stockValueCents).sum();
        System.out.println("  跨天求和 = " + sumAcrossTime + "（错误！余额类半可加，不能跨时间累加）");
        System.out.println("  正确用法：看趋势/取某天快照（最新库存 60）");
        System.out.println();
    }

    static void accumulatingSnapshot() {
        System.out.println("【累积快照：一单一行，随流程更新时间戳】");
        Map<String, AccumulatingFact> table = new LinkedHashMap<>();
        table.put("9001", new AccumulatingFact("9001", 1000L, null, null, null, "CREATED"));
        System.out.println("  初始：" + table.get("9001"));

        table.put("9001", new AccumulatingFact("9001", 1000L, 1100L, null, null, "PAID"));
        table.put("9001", new AccumulatingFact("9001", 1000L, 1100L, 1500L, null, "SHIPPED"));
        table.put("9001", new AccumulatingFact("9001", 1000L, 1100L, 1500L, 3000L, "FINISHED"));
        AccumulatingFact f = table.get("9001");
        System.out.println("  最终：" + f);
        System.out.printf("  漏斗耗时：下单->支付 %dms，支付->发货 %dms，发货->完成 %dms%n",
                f.paidAt() - f.placedAt(), f.shippedAt() - f.paidAt(),
                f.finishedAt() - f.shippedAt());
        System.out.println();
        System.out.println("  唯一会被更新的事实表 -> 用于流程效率与漏斗分析");
    }

    public static void main(String[] args) {
        transactionFact();
        periodicSnapshot();
        accumulatingSnapshot();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 为同一业务（订单）建三种事实表，分别回答"昨天 GMV""当前库存""下单到完成平均耗时"，说明为什么必须用三种表。
 * 2. 实现累积快照的更新逻辑：按 orderId upsert 四个时间戳 + 状态，构造"跳步（未发货直接完成）"异常数据并检出。
 * 3. 思考题：为什么周期快照的度量是"半可加"？用库存跨天求和的错误结果说明。
 * ============================================================
 */
