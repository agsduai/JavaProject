/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：78-02
 * 课名：事务消息与本地消息表
 * ------------------------------------------------------------
 * 知识点：
 *   - **"先写库再发消息"的经典 bug**：**写库成功、发消息失败** -> 下游永远不知道；**先发消息再写库** -> 消息发了但库回滚 -> 下游处理了不存在的数据——**两个操作不在一个事务里，必然有窗口**。
 *   - **本地消息表（Outbox）**：**业务数据和消息记录在同一个本地事务里落库**，**再由后台 relay 轮询未发送消息投递 MQ**——**原子性由本地事务保证，投递由 relay 保证（至少一次）**。
 *   - **事务消息（RocketMQ 风格）**：**先发 half message（对消费者不可见）-> 执行本地事务 -> commit/rollback**；**若 commit 丢失，Broker 回查生产者**——**本质也是"两阶段 + 回查"**。
 *   - **relay 的三个要点**：**按 id 顺序发送**（同聚合保序 78-04）、**发送成功才标记**（至少一次）、**失败重试 + 死信**（78-08）——**消费者必须幂等**（78-03）。
 *   - 与 45 章的关系：45 章的"分布式共识"解决"多副本一致"，**Outbox 解决"本地事务与消息的一致"**——**一个对内，一个对外**。
 *   - 易错点：业务与消息分两个事务（窗口）；relay 先标记后发送（丢消息）；不清理已发送记录（表膨胀）；消费者不幂等（重复投递出问题）。
 * ============================================================
 */

package com.heima.ch78;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class TransactionalOutbox {
    record Order(long id, String status) {
    }

    static class OutboxEvent {
        final long id;
        final long orderId;
        final String type;
        final String payload;
        boolean sent;

        OutboxEvent(long id, long orderId, String type, String payload) {
            this.id = id;
            this.orderId = orderId;
            this.type = type;
            this.payload = payload;
        }

        @Override
        public String toString() {
            return "OutboxEvent{id=" + id + ", order=" + orderId + ", type=" + type
                    + ", sent=" + sent + "}";
        }
    }

    static final Map<Long, Order> orders = new LinkedHashMap<>();
    static final List<OutboxEvent> outbox = new ArrayList<>();
    static long nextEventId = 1;

    /** 本地事务：订单 + outbox 一起提交（要么都成功，要么都回滚） */
    static void createOrder(long orderId) {
        orders.put(orderId, new Order(orderId, "CREATED"));
        outbox.add(new OutboxEvent(nextEventId++, orderId, "OrderCreated",
                "{\"orderId\":" + orderId + "}"));
        System.out.printf("  本地事务提交：order=%d + outbox=%d%n", orderId,
                outbox.get(outbox.size() - 1).id);
    }

    /** relay：扫描未发送事件，投递并标记（至少一次） */
    static int relay(String phase) {
        int sent = 0;
        for (OutboxEvent e : outbox) {
            if (!e.sent) {
                // 模拟投递 MQ
                System.out.printf("  [%s] 投递事件 #%d %s -> MQ%n", phase, e.id, e.type);
                e.sent = true;
                sent++;
            }
        }
        System.out.printf("  [%s] 本轮投递 %d 条%n", phase, sent);
        return sent;
    }

    public static void main(String[] args) {
        System.out.println("【正常路径】");
        createOrder(1001);
        relay("relay-1");

        System.out.println();
        System.out.println("【故障：本地事务提交后、relay 之前进程崩溃】");
        createOrder(1002);
        System.out.println("  *** 崩溃，outbox #2 未发送 ***");
        System.out.println("  outbox 现状：" + outbox);

        System.out.println();
        System.out.println("【重启：relay 从数据库恢复未发送事件】");
        relay("relay-restart");

        System.out.println();
        System.out.println("【重复投递（至少一次语义）】");
        outbox.get(1).sent = false;                 // 模拟标记丢失
        relay("relay-retry");
        System.out.println("  消费者会收到重复事件 -> 必须幂等（78-03）");

        System.out.println();
        System.out.println("【最终状态】");
        orders.forEach((id, o) -> System.out.println("  " + o));
        outbox.forEach(e -> System.out.println("  " + e));

        System.out.println();
        System.out.println("【要点】同库同事务写 outbox；relay 至少一次；消费者幂等；定期清理已发送");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现"订单 + outbox 同事务提交"，模拟提交后崩溃、重启 relay 补发，验证不丢事件。
 * 2. 模拟 relay "先标记后发送"的错误实现，说明为什么会丢消息。
 * 3. 思考题：为什么事务消息需要"回查"？从"本地事务结果未知"与"half message"分析。
 * ============================================================
 */
