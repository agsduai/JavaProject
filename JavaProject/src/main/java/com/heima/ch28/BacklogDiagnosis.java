/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：28-07
 * 课名：消息积压的排查与处理
 * ------------------------------------------------------------
 * 知识点：
 *   - 积压先看三个数：**生产速率**、**消费速率**、**当前 lag**。只有生产 > 消费才是真积压，否则只是瞬时抖动。
 *   - 常见原因：消费者逻辑变慢（慢 SQL、同步 RPC 超时）、消费线程数不足、某条消息一直失败在重试、Broker 磁盘/网络瓶颈、分区数太少导致并行度上不去。
 *   - 应急处理顺序：先扩容消费者（不超过分区数）→ 再优化消费逻辑（批量、异步、去掉同步调用）→ 最后才考虑丢弃非核心消息。
 *   - 扩容有上限：Kafka 的并行度上限就是分区数，消费者比分区多出来的实例会空转。所以"预留足够分区"是设计期就要做的决定。
 *   - 长期方案：监控 lag 并告警、消费端加超时熔断、把重逻辑拆成二级异步。
 *   - 易错：一看到积压就重启消费者（重复消费 + 更慢）；盲目加机器但不加分分区；把死循环的消息留在队首反复重试。
 * ============================================================
 */

package com.heima.ch28;

// ==================== 示例代码 ====================

public class BacklogDiagnosis {
    public static void main(String[] args) {
        System.out.println("=== 第一步：判断是真积压还是瞬时抖动 ===");
        int producedPerSec = 1000;
        int consumedPerSec = 400;
        int lag = 50_000;
        System.out.println("  生产 " + producedPerSec + "/s，消费 " + consumedPerSec + "/s，积压 " + lag);
        int net = producedPerSec - consumedPerSec;
        System.out.println("  净增长 " + net + "/s => " + (net > 0 ? "真积压，缺口会持续扩大" : "不是积压"));
        if (net > 0) {
            System.out.println("  按当前速度，" + lag / net + "s 后积压翻倍");
        }

        System.out.println();
        System.out.println("=== 第二步：定位瓶颈 ===");
        String[] causes = {
            "消费线程数 4，分区数 12 -> 并行度没用满，可以扩到 12 个消费者",
            "单条处理 80ms 里 60ms 是同步 RPC -> 改成批量查或本地缓存",
            "有 1 条消息反复失败占住队首 -> 进死信队列，别阻塞后面",
            "Broker 磁盘 IO 打满 -> 扩容或降低刷盘频率"
        };
        for (String c : causes) {
            System.out.println("  - " + c);
        }

        System.out.println();
        System.out.println("=== 第三步：扩容效果测算 ===");
        int partitions = 12;
        int[] consumers = {4, 8, 12, 16};
        for (int c : consumers) {
            int effective = Math.min(c, partitions);
            int capacity = effective * consumedPerSec / 4;
            System.out.println("  消费者 " + c + " 个 -> 有效并行 " + effective
                    + "，消费能力约 " + capacity + "/s"
                    + (c > partitions ? "（多出的 " + (c - partitions) + " 个空转）" : ""));
        }
        System.out.println("  结论：并行度上限 = 分区数，设计期就要预留分区");

        System.out.println();
        System.out.println("=== 第四步：把消费逻辑改成批量 ===");
        int batchSize = 100;
        int perMsgCost = 5;
        int batchOverhead = 200;
        int single = 1000 * perMsgCost;
        int batch = (1000 / batchSize) * (batchOverhead + batchSize * perMsgCost);
        System.out.println("  逐条处理 1000 条耗时 " + single + "ms");
        System.out.println("  批量 100 条/次耗时 " + batch + "ms，提升约 " + single / batch + " 倍");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 给示例加上"积压告警阈值"，lag 超过 10 万时输出告警，并说明阈值怎么定。
 * 2. 分析"分区 12、消费者 16"时多出的 4 个实例在做什么，为什么不继续扩。
 * 3. 设计一个消费端超时熔断方案：某下游连续超时 N 次后直接失败进死信，避免拖死整个消费者。
 * ============================================================
 */
