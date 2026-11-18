/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：49-06
 * 课名：事件溯源与投影
 * ------------------------------------------------------------
 * 知识点：
 *   - **事件溯源（Event Sourcing）把"状态"变成"事件的派生品"**：不存"订单当前状态"，存"订单的全部变更事件"；当前状态 = 事件按序回放的结果。**事件流是唯一真源（source of truth）**——审计天然完整、时间旅行免费（46-05 同源）、任何视图可重建。
 *   - **快照（snapshot）解决回放成本**：事件千条后回放变慢——**每 N 条事件存一个状态快照**，恢复 = 快照 + 之后的事件重放（43-05 checkpoint 的同源思想：状态自持 + 日志重放）。
 *   - **事件溯源 ≠ 事件消息**：溯源事件是"领域事实的完整记录"（真源），消息是"传递通知"（可丢可重）；**溯源事件流要求不可变存储与完整保留**——存储成本与治理成本更高。
 *   - **版本化与升级**：事件 schema 演进（加字段/改语义）是事件溯源的最大运维负担——**upcaster（事件升级器）**把旧版本事件在读时转换为新版本；避免破坏性变更。
 *   - **适用边界**：审计强需求（金融账务、合规）、需要重建任意历史视图（51-08 账务）、复杂协作需要回放调试——**普通 CRUD 用事件溯源是自找麻烦**（查询、修复、心智成本全上升）。
 *   - **与 CQRS 的关系**：事件溯源天然伴生 CQRS（写侧是事件流，读侧是投影视图）——但两者可独立使用（49-05）。
 *   - 易错点：把事件溯源当潮流上 CRUD 系统（复杂度爆炸）；事件 schema 无版本管理（历史事件读不出来）；快照策略缺失（回放越来越慢）；敏感数据进事件流（加密与脱敏难题，36 章合规约束）。
 * ============================================================
 */

package com.heima.ch49;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class EventSourcing {
    /** 事件流：真源 */
    sealed interface OrderEvent permits Created, Paid, Cancelled {
    }

    record Created(String orderId, int amountCents) implements OrderEvent {
    }

    record Paid(String orderId, long paidAtMs) implements OrderEvent {
    }

    record Cancelled(String orderId, String reason) implements OrderEvent {
    }

    /** 状态 = 事件回放的派生品 */
    record OrderState(String orderId, int amountCents, String status) {
    }

    static OrderState replay(List<OrderEvent> events, OrderState snapshot) {
        OrderState s = snapshot;
        for (OrderEvent e : events) {
            if (e instanceof Created c) {
                s = new OrderState(c.orderId(), c.amountCents(), "CREATED");
            } else if (e instanceof Paid) {
                s = new OrderState(s.orderId(), s.amountCents(), "PAID");
            } else if (e instanceof Cancelled) {
                s = new OrderState(s.orderId(), s.amountCents(), "CANCELLED");
            }
        }
        return s;
    }

    static void replayDemo() {
        System.out.println("【事件回放：状态是派生品】");
        List<OrderEvent> stream = List.of(
                new Created("9001", 6900),
                new Paid("9001", System.currentTimeMillis()));
        OrderState s = replay(stream, null);
        System.out.printf("  回放 %d 个事件 -> 状态 %s%n", stream.size(), s);
        List<OrderEvent> withCancel = new ArrayList<>(stream);
        withCancel.add(new Cancelled("9001", "用户申请"));
        System.out.printf("  追加 Cancelled -> %s%n", replay(withCancel, null));
        System.out.println("  任意历史时点：截断事件流即可重建当时状态（时间旅行）");
        System.out.println();
    }

    static void snapshotFlow() {
        System.out.println("【快照：回放成本的控制】");
        int totalEvents = 5000, snapshotEvery = 1000;
        List<OrderEvent> tail = new ArrayList<>();
        System.out.printf("  每 %d 个事件存一个快照 -> 恢复 = 快照 + %d 条尾部事件重放%n",
                snapshotEvery, totalEvents % snapshotEvery);
        System.out.printf("  对比全量回放 %d 条：快照路径只重放 %d 条（成本降 %.0f%%）%n",
                totalEvents, totalEvents % snapshotEvery,
                (1 - totalEvents % snapshotEvery / (double) totalEvents) * 100);
        System.out.println("  快照本身也要版本化：状态 schema 变更时快照作废重建");
        System.out.println();
    }

    static void versionUpgrade() {
        System.out.println("【事件 schema 演进：upcaster】");
        record PaidV1(String orderId) {
        }
        System.out.println("  旧事件 PaidV1(orderId) 缺 paidAtMs 字段：");
        System.out.println("  upcaster：读时转换 PaidV1 -> Paid(orderId, paidAtMs=0 默认)");
        List<String> rules = List.of(
                "只加不改：新字段给默认值，禁止改语义",
                "upcaster 链：V1 -> V2 -> V3 逐级升级，历史事件永远可读",
                "破坏性变更 -> 新事件类型（PaidV2），不修改旧类型");
        for (String r : rules) {
            System.out.println("  - " + r);
        }
        System.out.println();
    }

    static void fitDecision() {
        System.out.println("【适用决策：值不值得事件溯源】");
        String[][] rows = {
                {"账务/资金流水", "值得", "审计是业务需求本身（51-08）"},
                {"订单生命周期", "酌情", "事件流对账与重现有价值，成本可控"},
                {"普通 CRUD", "不值得", "查询/修复/心智成本全上升"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-12s %-6s %s%n", r[0], r[1], r[2]);
        }
        System.out.println("  门槛三问：需要完整审计吗？需要重建历史视图吗？愿意承担 schema 演进成本吗？");
    }

    public static void main(String[] args) {
        replayDemo();
        snapshotFlow();
        versionUpgrade();
        fitDecision();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现完整事件溯源聚合：事件流 + 回放 + 快照 + 状态查询，构造 2000 条事件验证快照加速效果。
 * 2. 为 Paid 事件设计 V1->V2 演进（加字段），实现 upcaster 并验证历史事件仍可回放。
 * 3. 思考题：为什么"事件溯源与 CQRS 天然伴生"？从"写侧不可查询、读侧需投影"的结构必然性分析。
 * ============================================================
 */
