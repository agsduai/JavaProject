/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：78-04
 * 课名：顺序消息与分区键
 * ------------------------------------------------------------
 * 知识点：
 *   - **顺序的两种语义**：**全局有序**（所有消息一个队列，吞吐受限）与**分区有序**（同一 key 进同一分区，分区内 FIFO）——**绝大多数业务只需要"同一订单/同一用户有序"**。
 *   - **分区键的选择**：**用业务聚合根 ID**（orderId/userId）做 key，**保证同一聚合的事件进同一分区**；**不要用随机 key**（顺序丢失）、**不要用热点 key**（分区倾斜）。
 *   - **顺序与重试的冲突**：**分区内某条消息失败，后面的消息必须等待**（否则乱序）——**这就是队头阻塞（head-of-line blocking）**；**对策：失败快速转死信/暂停分区**（78-08）。
 *   - **顺序与并发的权衡**：**同一 key 串行、不同 key 并行**——**分区数决定最大并行度**，**key 的基数决定倾斜程度**。
 *   - 与 62 章的关系：62 章讲 Kafka"按 key 分区保序"的存储原理，**本章讲"业务上怎么选 key、怎么处理失败"**。
 *   - 易错点：用随机/时间做 key（同一订单分散）；失败无限重试（阻塞整个分区）；分区数太少（并行度不足）；分区数太多（顺序保障变弱、管理成本高）。
 * ============================================================
 */

package com.heima.ch78;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// ==================== 示例代码 ====================

public class OrderedMessaging {
    static final int PARTITIONS = 3;
    static final Map<Integer, List<String>> queues = new LinkedHashMap<>();
    static final Map<Integer, Integer> offsets = new LinkedHashMap<>();
    static final Set<String> failing = new LinkedHashSet<>();
    static final Map<String, Integer> attempts = new LinkedHashMap<>();

    static {
        for (int p = 0; p < PARTITIONS; p++) {
            queues.put(p, new ArrayList<>());
            offsets.put(p, 0);
        }
    }

    static int partitionOf(String key) {
        return Math.floorMod(key.hashCode(), PARTITIONS);
    }

    static void produce(String key, String msg) {
        int p = partitionOf(key);
        queues.get(p).add(msg);
        System.out.printf("  生产 key=%-8s -> 分区 %d：%s%n", key, p, msg);
    }

    /** 消费一个分区：遇到失败消息则阻塞（保序） */
    static void drain(int p) {
        List<String> q = queues.get(p);
        int off = offsets.get(p);
        while (off < q.size()) {
            String msg = q.get(off);
            if (failing.contains(msg)) {
                int n = attempts.merge(msg, 1, Integer::sum);
                System.out.printf("  分区 %d 队头 %s 失败（第 %d 次）-> 阻塞，后续消息等待%n",
                        p, msg, n);
                return;
            }
            System.out.printf("  分区 %d 消费 offset=%d：%s%n", p, off, msg);
            off++;
            offsets.put(p, off);
        }
        System.out.printf("  分区 %d 已清空%n", p);
    }

    public static void main(String[] args) {
        System.out.println("【生产：同一订单的事件进同一分区】");
        produce("order-1", "OrderCreated");
        produce("order-1", "OrderPaid");
        produce("order-1", "OrderShipped");
        produce("order-2", "OrderCreated");
        produce("order-2", "OrderPaid");
        produce("order-3", "OrderCreated");

        System.out.println();
        System.out.println("  分区分布：" + queues);

        System.out.println();
        System.out.println("【正常消费】");
        for (int p = 0; p < PARTITIONS; p++) {
            System.out.println("  -- 分区 " + p);
            drain(p);
        }

        System.out.println();
        System.out.println("【故障：order-1 的 OrderPaid 处理失败】");
        produce("order-1", "OrderRefunded");
        String bad = "OrderPaid";
        // 重置 offset 模拟重放
        int p1 = partitionOf("order-1");
        offsets.put(p1, 0);
        queues.get(p1).clear();
        queues.get(p1).addAll(List.of("OrderCreated", "OrderPaid", "OrderShipped", "OrderRefunded"));
        failing.add(bad);

        drain(p1);
        System.out.println("  -> 分区 " + p1 + " 卡在 OrderPaid，OrderShipped/Refunded 都不能处理");
        System.out.println("     （保序的代价：队头阻塞）");

        System.out.println();
        System.out.println("【其他分区不受影响】");
        produce("order-2", "OrderCompleted");
        drain(partitionOf("order-2"));

        System.out.println();
        System.out.println("【处理方案】失败消息转死信（78-08）或暂停分区人工介入，不能跳过");
        System.out.println("  关键指标：分区积压、失败次数、最长阻塞时长（78-09）");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现按 orderId 分区，验证同一订单事件顺序消费、不同订单可并行。
 * 2. 模拟"队头消息失败"，观察后续消息被阻塞，并设计"转死信 + 继续消费"的方案。
 * 3. 思考题：为什么"全局有序"吞吐低？从"单队列串行 + 分区并行"分析。
 * ============================================================
 */
