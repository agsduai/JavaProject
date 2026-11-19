/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：75-06
 * 课名：Mutation 与订阅
 * ------------------------------------------------------------
 * 知识点：
 *   - **Query 只读、Mutation 写**：**Mutation 的字段串行执行**（同一请求里多个 mutation 按顺序，避免竞争）——**与 Query 的并行相反**，这是规范要求。
 *   - **Mutation 的输入用输入类型**（75-02）：`createProduct(input: ProductInput!)`——**参数集中、便于校验（24-02 同款）**、便于演进（加字段不破坏签名）。
 *   - **Mutation 的返回值要"够用"**：返回创建后的对象（含 id）或受影响的数量——**客户端常需要"写后立即读"**（45-02 的 read-your-writes），返回对象可省一次查询。
 *   - **订阅（Subscription）是长连接推送**：基于 WebSocket（65-02 的 gRPC/WS 之外的选择），适合"数据变化通知"；**生产上更常用 MQ/SSE 替代**（43/65 章）——**订阅是 GraphQL 里最少用的部分**。
 *   - 与 64 章的关系：Mutation 等于 REST 的 POST/PUT/DELETE，**幂等键、版本兼容、错误码规范同样适用**（39/64-05）。
 *   - 易错点：Mutation 幂等键缺失（重试导致重复创建）；多个 mutation 放一个请求（部分失败语义复杂）；用订阅做业务事件总线（不可靠，应走 MQ）；Mutation 返回全量对象（响应过大）。
 * ============================================================
 */

package com.heima.ch75;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class MutationDemo {
    record ProductInput(String name, double price, String idempotencyKey) {
    }

    record Product(long id, String name, double price) {
    }

    static final Map<String, Product> idemIndex = new LinkedHashMap<>();
    static final List<Product> products = new ArrayList<>();
    static long nextId = 100;

    /** Mutation：幂等 + 校验 + 返回创建对象 */
    static Map<String, Object> createProduct(ProductInput input) {
        Map<String, Object> result = new LinkedHashMap<>();
        // 1) 幂等
        Product existing = idemIndex.get(input.idempotencyKey());
        if (existing != null) {
            result.put("product", existing);
            result.put("idempotent", true);
            return result;
        }
        // 2) 校验
        List<String> errors = new ArrayList<>();
        if (input.name() == null || input.name().isBlank()) {
            errors.add("name 必填");
        }
        if (input.price() <= 0) {
            errors.add("price 必须大于 0");
        }
        if (!errors.isEmpty()) {
            result.put("errors", errors);
            return result;
        }
        // 3) 写库
        Product p = new Product(++nextId, input.name(), input.price());
        products.add(p);
        idemIndex.put(input.idempotencyKey(), p);
        result.put("product", p);
        result.put("idempotent", false);
        return result;
    }

    /** 订阅：基于事件的推送（简化演示） */
    static class EventBus {
        final Map<String, List<Map<String, Object>>> mailbox = new LinkedHashMap<>();

        void subscribe(String clientId, String topic) {
            mailbox.computeIfAbsent(clientId, k -> new ArrayList<>());
            System.out.printf("  客户端 %s 订阅 %s%n", clientId, topic);
        }

        void publish(String topic, Object payload) {
            System.out.printf("  发布 %s：%s%n", topic, payload);
            mailbox.forEach((client, list) ->
                    list.add(Map.of("topic", topic, "payload", payload)));
        }

        void drain(String clientId) {
            System.out.printf("  客户端 %s 收到：%s%n", clientId,
                    mailbox.getOrDefault(clientId, List.of()));
            mailbox.getOrDefault(clientId, List.of()).clear();
        }
    }

    public static void main(String[] args) {
        System.out.println("【Mutation：创建商品】");
        Map<String, Object> r1 = createProduct(new ProductInput("手机壳", 69.0, "idem-1"));
        System.out.println("  " + r1);
        System.out.println("  重复提交同一 idempotencyKey：");
        Map<String, Object> r2 = createProduct(new ProductInput("手机壳", 69.0, "idem-1"));
        System.out.println("  " + r2 + "（幂等命中，未新建）");

        System.out.println();
        System.out.println("【校验失败】");
        System.out.println("  " + createProduct(new ProductInput("", -1, "idem-2")));

        System.out.println();
        System.out.println("【多个 Mutation 串行执行】");
        List<ProductInput> batch = List.of(
                new ProductInput("耳机", 129.0, "idem-3"),
                new ProductInput("充电器", 39.0, "idem-4"));
        for (ProductInput in : batch) {
            createProduct(in);
            System.out.println("  执行下一个（串行，非并行）");
        }
        System.out.printf("  当前商品数：%d%n", products.size());

        System.out.println();
        System.out.println("【订阅（长连接推送）】");
        EventBus bus = new EventBus();
        bus.subscribe("client-1", "product.created");
        bus.publish("product.created", "手机壳");
        bus.drain("client-1");

        System.out.println();
        System.out.println("  规范：Query 并行 / Mutation 串行；生产推送更常用 MQ 或 SSE（43/65 章）");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现 Mutation：幂等键 + 校验 + 返回对象，用"重复提交/校验失败/正常"三场景验证。
 * 2. 实现订阅的简化版：客户端订阅 + 事件发布 + 消息投递，说明为何生产上更常用 MQ。
 * 3. 思考题：为什么规范要求"Mutation 串行执行"？从"写操作竞争"与"客户端预期"分析。
 * ============================================================
 */
