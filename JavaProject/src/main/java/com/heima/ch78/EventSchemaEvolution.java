/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：78-07
 * 课名：事件 Schema 演进与契约
 * ------------------------------------------------------------
 * 知识点：
 *   - **事件是"跨团队的契约"**：**生产者改了字段，所有消费者都可能崩**——**事件 schema 要像 API 一样版本化**（64-07 的契约 diff 思想）。
 *   - **兼容性三种**：**向后兼容**（新消费者读旧事件）、**向前兼容**（旧消费者读新事件）、**破坏性变更**（删除/改类型/改语义）——**只加可选字段是安全的，删字段/改类型是破坏性的**。
 *   - **演进策略**：**加字段给默认值**（缺失时兜底）、**双写过渡**（新旧字段并存一段时间）、**版本化 topic/事件名**（`order.created.v1` -> `v2`）、**Schema Registry**（集中校验，生产前拦截）。
 *   - **事件内容设计**：**放"事实 + 标识"**（orderId、amount、currency），**不放"可变快照"**（用户当前等级）——**消费者需要详情时回查**（避免事件变成"数据同步"）。
 *   - 与 64 章的关系：64-07 讲"REST 契约 diff 兼容判定"，**事件契约的规则完全同构**——**加可选安全、删必填破坏**。
 *   - 易错点：直接改字段类型（`int` -> `string`）；删除字段（旧消费者 NPE）；用事件传全量实体（耦合）；不建 schema 校验（上线才发现）。
 * ============================================================
 */

package com.heima.ch78;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class EventSchemaEvolution {
    /** 事件统一用 Map 表示（贴近 JSON），版本号显式携带 */
    static Map<String, String> v1(long orderId, double amount) {
        Map<String, String> e = new LinkedHashMap<>();
        e.put("eventType", "OrderCreated");
        e.put("schemaVersion", "1");
        e.put("orderId", String.valueOf(orderId));
        e.put("amount", String.valueOf(amount));
        return e;
    }

    static Map<String, String> v2(long orderId, double amount, String currency) {
        Map<String, String> e = v1(orderId, amount);
        e.put("schemaVersion", "2");
        e.put("currency", currency);                 // 新增可选字段
        return e;
    }

    /** 旧消费者读 v2 事件：忽略未知字段，缺失字段用默认值 */
    static String oldConsumer(Map<String, String> e) {
        String orderId = e.getOrDefault("orderId", "?");
        double amount = Double.parseDouble(e.getOrDefault("amount", "0"));
        return "order=" + orderId + " amount=" + amount + "（旧消费者忽略 currency）";
    }

    /** 新消费者读 v1 事件：currency 缺失时兜底 */
    static String newConsumer(Map<String, String> e) {
        String currency = e.getOrDefault("currency", "CNY");
        return "order=" + e.get("orderId") + " amount=" + e.get("amount")
                + " currency=" + currency + "（新消费者兼容旧事件）";
    }

    record Change(String type, String detail, boolean compatible) {
    }

    static void compatibility() {
        List<Change> changes = List.of(
                new Change("新增可选字段", "v2 加 currency", true),
                new Change("新增必填字段", "消费者无法构造默认值", false),
                new Change("删除字段", "旧消费者读不到", false),
                new Change("字段改类型", "int -> string，解析失败", false),
                new Change("枚举加值", "旧消费者 switch 未覆盖", false),
                new Change("改字段语义", "amount 从元变分", false));
        System.out.println("【变更兼容性】");
        for (Change c : changes) {
            System.out.printf("  %-12s %-26s %s%n", c.type(), c.detail(),
                    c.compatible() ? "兼容" : "破坏性");
        }
        System.out.println();
    }

    public static void main(String[] args) {
        Map<String, String> e1 = v1(1001, 69.0);
        Map<String, String> e2 = v2(1002, 129.0, "USD");

        System.out.println("【事件样本】");
        System.out.println("  v1: " + e1);
        System.out.println("  v2: " + e2);

        System.out.println();
        System.out.println("【跨版本消费】");
        System.out.println("  " + oldConsumer(e2));
        System.out.println("  " + newConsumer(e1));
        System.out.println("  " + newConsumer(e2));

        System.out.println();
        compatibility();

        System.out.println("【演进策略】");
        System.out.println("  1) 只加可选字段，给默认值");
        System.out.println("  2) 破坏性变更 -> 新版本事件（order.created.v2），双写过渡");
        System.out.println("  3) Schema Registry 生产前校验，CI 做兼容性 diff");
        System.out.println("  4) 事件只放事实与标识，详情让消费者回查");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 给 `OrderCreated` 加一个可选字段，验证旧/新消费者都能正常处理。
 * 2. 模拟一次"删除字段"的破坏性变更，写出旧消费者的失败现象与双写过渡方案。
 * 3. 思考题：为什么"事件里放全量实体"会增加耦合？从"消费者依赖字段 + 生产者不敢改"分析。
 * ============================================================
 */
