/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：62-01
 * 课名：Kafka 全景：日志即消息
 * ------------------------------------------------------------
 * 知识点：
 *   - **Kafka 的本质是"分布式的、只能追加的日志"**：消息追加到分区末尾，消费者按偏移量顺序读——**它不是"队列"（读完即删），而是"日志"（按时间/大小保留）**。这个设计带来三个能力：**多消费组独立消费**（同一份数据被多个业务各自消费）、**重放**（重置 offset 重读历史，43 章可重放源的基础）、**高吞吐**（顺序追加是磁盘最快的写模式）。
 *   - **四个核心组件**：**Broker**（单台服务器）、**Topic**（逻辑分类）、**Partition**（物理分片，**并行与顺序的单位**）、**Replica**（副本，可靠性）。**Topic 是逻辑概念，Partition 才是存储与并发的实体**。
 *   - **Kafka 与 28/43 章的关系**：28 章讲 MQ 的**使用语义**（可靠投递/幂等/死信），43 章讲**流处理的窗口与状态**；本章补**存储与复制的内部机制**——**知道 ISR 与 HW 才能理解"为什么消息会丢/为什么消费到重复"**。
 *   - **顺序性的边界**：Kafka 只保证**单分区内有序**，跨分区无序——**需要全局有序就只能单分区**（吞吐受限）；**业务保序的正确做法是按业务键（订单 id）分区**（39-08 分区键思想）。
 *   - **吞吐的来源**（62-09 细讲）：顺序写 + 页缓存 + 零拷贝 + 批量压缩 + 分区并行——**Kafka 的高吞吐不是魔法，是这五件事的叠加**。
 *   - 易错点：以为 Kafka 是"队列"（读完不删，按保留策略清理）；以为全局有序（只有分区内有序）；Topic 分区数随意定（分区数是并行上限，创建后只能增不能减）；用默认分区策略导致热点（key=null 时轮询，带 key 时哈希）。
 * ============================================================
 */

package com.heima.ch62;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class KafkaOverview {
    record Partition(int id, List<String> log, long endOffset) {
        Partition append(String record) {
            log.add(record);
            return new Partition(id, log, endOffset + 1);
        }
    }

    /** 按 key 哈希分区：同一 key 恒在同一分区（保序的前提） */
    static int partitionOf(String key, int partitions) {
        return Math.abs(key.hashCode()) % partitions;
    }

    static void appendDemo() {
        System.out.println("【按 key 分区：同一订单的消息进同一分区】");
        Map<Integer, List<String>> byPartition = new LinkedHashMap<>();
        for (int i = 0; i < 3; i++) {
            byPartition.put(i, new ArrayList<>());
        }
        String[][] messages = {
                {"order-9001", "CREATED"}, {"order-9002", "CREATED"},
                {"order-9001", "PAID"}, {"order-9002", "PAID"},
                {"order-9001", "SHIPPED"}};
        for (String[] m : messages) {
            int p = partitionOf(m[0], 3);
            byPartition.get(p).add(m[0] + ":" + m[1]);
        }
        byPartition.forEach((p, log) ->
                System.out.printf("  partition-%d：%s%n", p, log));
        System.out.println("  order-9001 的三条消息在同一分区 -> 分区内有序（消费端按序处理）");
        System.out.println();
    }

    static void replayDemo() {
        System.out.println("【多消费组 + 重放：日志而非队列】");
        List<String> log = List.of("m1", "m2", "m3", "m4");
        Map<String, Long> groupOffsets = new LinkedHashMap<>();
        groupOffsets.put("group-order", 4L);
        groupOffsets.put("group-data", 2L);
        for (Map.Entry<String, Long> e : groupOffsets.entrySet()) {
            List<String> remaining = log.subList(e.getValue().intValue(), log.size());
            System.out.printf("  %-12s offset=%d -> 下次消费 %s%n",
                    e.getKey(), e.getValue(), remaining);
        }
        System.out.println("  同一份日志，两个消费组各自维护 offset（互不影响）");
        System.out.println("  重放：把 offset 重置为 0 即可重读全部历史（43-01 可重放源）");
    }

    public static void main(String[] args) {
        appendDemo();
        replayDemo();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现一个"分区有序性验证"：同一业务键的消息发到同一分区，跨键的消息分散到不同分区，用 100 条消息验证。
 * 2. 用 Kafka（或内存模拟）跑两个消费组消费同一 Topic，验证各自 offset 独立、互不影响。
 * 3. 思考题：为什么 Kafka 不做全局有序？从"单分区吞吐上限"与"分区并行"的取舍分析，并给出"业务保序"的正确做法。
 * ============================================================
 */
