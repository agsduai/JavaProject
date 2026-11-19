/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：78-05
 * 课名：事件溯源与 CQRS
 * ------------------------------------------------------------
 * 知识点：
 *   - **事件溯源（Event Sourcing）**：**不存当前状态，只存状态变化的事件流**——**状态 = 事件回放**；**审计天然完整、可重建任意时点快照**（45-02 的 MVCC 思想在业务层的版本）。
 *   - **事件存储的三个要求**：**只追加**（append-only）、**按聚合 ID 有序**（version 递增）、**乐观并发**（expectedVersion 冲突检测）——**不能更新/删除事件，只能追加补偿事件**。
 *   - **CQRS（命令查询职责分离）**：**写侧走事件流**（命令 -> 事件），**读侧走投影**（事件 -> 读模型）——**读写模型可以不同**（写侧规范、读侧为查询优化）。
 *   - **投影（Projection）**：**订阅事件更新读模型**，**可重放重建**（改了读模型逻辑就重放）；**投影要幂等**（78-03），**位点要持久化**（从哪继续）。
 *   - 与 49 章的关系：49 章 DDD 的"聚合/领域事件"是事件溯源的基础，**本章把"领域事件"变成"唯一事实来源"**。
 *   - 易错点：事件里塞"当前全量状态"（失去溯源意义）；允许修改历史事件；投影与事件不在同一事务（最终一致，可接受但要知道）；不存 version（并发写覆盖）。
 * ============================================================
 */

package com.heima.ch78;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class EventSourcingCqrs {
    record Event(long orderId, int version, String type, double amount) {
    }

    static final Map<Long, List<Event>> eventStore = new LinkedHashMap<>();
    static final Map<Long, Double> readModel = new LinkedHashMap<>();   // 订单总额投影

    /** 追加事件：乐观并发（expectedVersion） */
    static boolean append(long orderId, int expectedVersion, String type, double amount) {
        List<Event> stream = eventStore.computeIfAbsent(orderId, k -> new ArrayList<>());
        int current = stream.size();
        if (current != expectedVersion) {
            System.out.printf("  并发冲突：order=%d 期望版本 %d，实际 %d%n",
                    orderId, expectedVersion, current);
            return false;
        }
        Event e = new Event(orderId, current + 1, type, amount);
        stream.add(e);
        System.out.printf("  追加事件 order=%d v%d %s %.2f%n",
                orderId, e.version(), e.type(), e.amount());
        project(e);
        return true;
    }

    /** 投影：事件 -> 读模型（幂等：按 version 去重） */
    static void project(Event e) {
        List<Event> stream = eventStore.get(e.orderId());
        // 简化：只处理最新版本，重复事件直接忽略
        if (e.version() != stream.size()) {
            return;
        }
        double total = readModel.getOrDefault(e.orderId(), 0.0);
        switch (e.type()) {
            case "OrderCreated" -> total = 0;
            case "ItemAdded" -> total += e.amount();
            case "ItemRemoved" -> total -= e.amount();
            case "OrderCancelled" -> total = 0;
            default -> { }
        }
        readModel.put(e.orderId(), total);
    }

    /** 回放聚合：从事件流重建状态 */
    static Map<String, Object> rebuild(long orderId) {
        List<Event> stream = eventStore.getOrDefault(orderId, List.of());
        double total = 0;
        String status = "NEW";
        int items = 0;
        for (Event e : stream) {
            switch (e.type()) {
                case "OrderCreated" -> status = "CREATED";
                case "ItemAdded" -> {
                    total += e.amount();
                    items++;
                }
                case "ItemRemoved" -> {
                    total -= e.amount();
                    items--;
                }
                case "OrderCancelled" -> status = "CANCELLED";
                default -> { }
            }
        }
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("orderId", orderId);
        state.put("status", status);
        state.put("items", items);
        state.put("total", total);
        state.put("version", stream.size());
        return state;
    }

    public static void main(String[] args) {
        System.out.println("【命令 -> 事件】");
        append(1001, 0, "OrderCreated", 0);
        append(1001, 1, "ItemAdded", 69.0);
        append(1001, 2, "ItemAdded", 129.0);
        append(1001, 3, "ItemRemoved", 69.0);
        append(1002, 0, "OrderCreated", 0);
        append(1002, 1, "ItemAdded", 199.0);
        append(1002, 2, "OrderCancelled", 0);

        System.out.println();
        System.out.println("【乐观并发：两个客户端同时基于 v4 追加】");
        append(1001, 4, "ItemAdded", 10.0);
        append(1001, 4, "ItemAdded", 20.0);          // 第二个冲突

        System.out.println();
        System.out.println("【事件流（唯一事实来源）】");
        eventStore.forEach((id, list) -> {
            System.out.println("  order " + id + "：");
            list.forEach(e -> System.out.printf("    v%d %-14s %.2f%n",
                    e.version(), e.type(), e.amount()));
        });

        System.out.println();
        System.out.println("【读模型（投影）】");
        readModel.forEach((id, total) -> System.out.printf("  order %d 总额=%.2f%n", id, total));

        System.out.println();
        System.out.println("【回放重建聚合状态】");
        eventStore.keySet().forEach(id -> System.out.println("  " + rebuild(id)));

        System.out.println();
        System.out.println("【要点】只追加 + version 乐观锁 + 投影幂等 + 可重放；事件不可改，只能补偿");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现追加 + 乐观并发（expectedVersion），模拟两个客户端冲突，验证只有一个成功。
 * 2. 写一个投影：从订单事件流计算"已支付总额"，并验证重放后结果一致。
 * 3. 思考题：事件溯源为什么"不能修改历史事件"？从"审计可信 + 可重放"分析，并说明如何纠正错误。
 * ============================================================
 */
