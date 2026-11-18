/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：28-06
 * 课名：顺序消息与延迟消息
 * ------------------------------------------------------------
 * 知识点：
 *   - **顺序性**只在"同一业务键"上有意义：同一个订单的"创建 → 支付 → 取消"必须有序，不同订单之间互不影响。
 *   - 全局有序的代价极大（单分区单消费者，吞吐直接掉到单机上限）。正确做法是**分区有序**：按 `orderId % 分区数` 选分区，同一订单永远进同一分区，分区内单线程消费。
 *   - 顺序的三个前提缺一不可：生产端按 key 路由、Broker 单分区内 FIFO、消费端单线程（或多线程但按 key 分派到同一线程）。
 *   - **延迟消息**：下单 30 分钟未支付自动取消、预约提醒、超时重试。实现方式有延迟队列（RabbitMQ 延迟交换机）、定时扫描（扫表，简单但精度差）、时间轮（Kafka 生态常用）。
 *   - 延迟消息的时间精度与堆积量成反比：精度越高，越需要分桶（按分钟/秒分多个队列）。
 *   - 易错：消费端用了线程池却忘了按 key 分派，同一订单被两个线程并发处理，顺序彻底乱了。
 * ============================================================
 */

package com.heima.ch28;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class OrderAndDelayMessage {
    static final int PARTITIONS = 3;
    static final List<List<String>> TOPIC = new ArrayList<>();

    static {
        for (int i = 0; i < PARTITIONS; i++) {
            TOPIC.add(new ArrayList<>());
        }
    }

    /** 生产端：按 orderId 哈希选分区，保证同一订单进同一分区 */
    static void send(String orderId, String event) {
        int partition = Math.floorMod(orderId.hashCode(), PARTITIONS);
        TOPIC.get(partition).add(orderId + " " + event);
        System.out.println("  发送 " + orderId + " " + event + " -> 分区 " + partition);
    }

    public static void main(String[] args) {
        System.out.println("=== 分区有序：同一订单固定落同一分区 ===");
        send("ORD-1", "CREATED");
        send("ORD-2", "CREATED");
        send("ORD-1", "PAID");
        send("ORD-1", "SHIPPED");
        send("ORD-2", "CANCELLED");
        send("ORD-1", "FINISHED");

        for (int p = 0; p < PARTITIONS; p++) {
            System.out.println("  分区 " + p + " 顺序消费：" + TOPIC.get(p));
        }

        System.out.println();
        System.out.println("=== 反例：同一订单被分到不同分区 ===");
        List<String> wrong = new ArrayList<>();
        wrong.add("ORD-1 CREATED");
        wrong.add("ORD-1 PAID");
        System.out.println("  两个消费者并行处理：" + wrong);
        System.out.println("  -> PAID 可能先于 CREATED 生效，状态机报非法流转");

        System.out.println();
        System.out.println("=== 延迟消息：下单 30 分钟未支付自动取消 ===");
        Map<String, Long> dueAt = new LinkedHashMap<>();
        long now = 0L;
        dueAt.put("ORD-3", now + 30 * 60_000L);
        dueAt.put("ORD-4", now + 5 * 60_000L);
        System.out.println("  投递延迟消息：ORD-3 在 +30min，ORD-4 在 +5min");
        long[] checkpoints = {5 * 60_000L, 30 * 60_000L};
        for (long cp : checkpoints) {
            System.out.println("  时间推进到 +" + cp / 60_000 + "min，扫描到期消息：");
            for (Map.Entry<String, Long> e : dueAt.entrySet()) {
                if (e.getValue() <= cp) {
                    System.out.println("    到期 " + e.getKey() + " -> 查支付状态，未支付则取消");
                }
            }
        }
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 把分区数改成 5，验证同一订单仍落同一分区，并统计各分区消息量是否均衡。
 * 2. 设计一个"下单 30 分钟未支付自动取消"的定时扫描方案，说明扫描间隔与数据库压力的关系。
 * 3. 解释为什么"消费端用线程池并发消费"会破坏顺序，给出两种修复方式。
 * ============================================================
 */
