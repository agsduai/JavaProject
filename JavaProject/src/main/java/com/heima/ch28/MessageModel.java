/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：28-02
 * 课名：两种消息模型：点对点与发布订阅
 * ------------------------------------------------------------
 * 知识点：
 *   - **点对点（Queue）**：一条消息只会被**一个**消费者拿到，多个消费者是竞争关系，天然做负载均衡，适合"任务分发"（发邮件、生成报表）。
 *   - **发布订阅（Topic）**：一条消息会被**每个**订阅组各收到一份，组内仍然是竞争消费，适合"事件广播"（订单创建后积分、短信、物流各自处理）。
 *   - 关键区别在"消费组"：Kafka/RocketMQ 的 Topic 下挂多个 consumer group，组间广播、组内负载；RabbitMQ 的 fanout 交换机是纯广播，每个队列都收到。
 *   - 语义差异决定重试策略：点对点任务失败重投只影响自己；发布订阅里某个订阅者反复失败会拖慢整个消费组，通常单独配死信队列。
 *   - 易错：把"多消费者"当成"广播"——同组内加机器只会分走消息，不会让每台都收到；想广播必须用不同的消费组。
 * ============================================================
 */

package com.heima.ch28;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class MessageModel {
    /** 点对点：一条消息只能被取走一次 */
    static class PointToPointQueue {
        private final List<String> buffer = new ArrayList<>();

        void send(String msg) {
            buffer.add(msg);
        }

        String receive() {
            return buffer.isEmpty() ? null : buffer.remove(0);
        }

        int size() {
            return buffer.size();
        }
    }

    /** 发布订阅：每个订阅者各有一份信箱 */
    static class Topic {
        private final String name;
        private final Map<String, List<String>> mailboxes = new HashMap<>();

        Topic(String name) {
            this.name = name;
        }

        void subscribe(String consumer) {
            mailboxes.putIfAbsent(consumer, new ArrayList<>());
        }

        void publish(String msg) {
            for (List<String> box : mailboxes.values()) {
                box.add(msg);
            }
        }

        List<String> drain(String consumer) {
            List<String> box = mailboxes.get(consumer);
            if (box == null) {
                return List.of();
            }
            List<String> copy = new ArrayList<>(box);
            box.clear();
            return copy;
        }
    }

    public static void main(String[] args) {
        System.out.println("=== 点对点：多个消费者竞争，消息不重复 ===");
        PointToPointQueue queue = new PointToPointQueue();
        for (int i = 1; i <= 5; i++) {
            queue.send("TASK-" + i);
        }
        System.out.println("  队列初始 " + queue.size() + " 条");
        System.out.println("  消费者A 取到 " + queue.receive() + "、" + queue.receive());
        System.out.println("  消费者B 取到 " + queue.receive() + "（不会和A重复）");
        System.out.println("  剩余 " + queue.size() + " 条待取");

        System.out.println();
        System.out.println("=== 发布订阅：每个订阅组都收到全量 ===");
        Topic topic = new Topic("order.created");
        topic.subscribe("积分服务");
        topic.subscribe("短信服务");
        topic.subscribe("物流服务");
        topic.publish("ORDER-1001");
        topic.publish("ORDER-1002");
        for (String consumer : List.of("积分服务", "短信服务", "物流服务")) {
            System.out.println("  " + consumer + " 收到 " + topic.drain(consumer) + "（topic=" + topic.name + "）");
        }

        System.out.println();
        System.out.println("=== 同一订阅组内加机器：只会分摊，不会重复 ===");
        topic.publish("ORDER-1003");
        List<String> shared = topic.drain("积分服务");
        System.out.println("  积分服务组内 2 台机器，本轮总共只消费 " + shared.size() + " 条：" + shared);
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 给 `PointToPointQueue` 加上"取走但处理失败要还回队列"的方法，思考它与 28-04 的 nack 是什么关系。
 * 2. 用发布订阅模型实现"用户注册成功后，积分服务、优惠券服务、欢迎邮件服务各自处理"，画出消息流向。
 * 3. 解释为什么"给同一个消费组加机器"不能解决消息重复消费的问题，而只能提升吞吐。
 * ============================================================
 */
