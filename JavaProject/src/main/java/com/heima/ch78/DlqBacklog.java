/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：78-08
 * 课名：死信队列与积压治理
 * ------------------------------------------------------------
 * 知识点：
 *   - **重试要有边界**：**瞬时故障**（网络抖动/下游超时）重试有效；**永久故障**（数据非法/逻辑 bug）重试无意义——**必须区分，否则一条坏消息阻塞整个分区**（78-04）。
 *   - **退避策略**：**固定间隔**（简单但可能打爆下游）、**指数退避**（100ms、200ms、400ms...）、**带抖动**（避免重试风暴同步）——**重试次数与总时长都要设上限**。
 *   - **死信队列（DLQ）**：**超过重试上限的消息转入 DLQ**，**分区继续消费**（解除队头阻塞）——**DLQ 要有告警、可查询、可重放**（修复后重新投递）。
 *   - **积压诊断三步**：**看 lag（生产-消费）**、**看消费速率与生产速率**、**看单条耗时**——**lag 持续增长 = 消费能力不足**（加消费者/优化单条/扩容分区）。
 *   - 与 28/62 章的关系：28 章讲"MQ 的确认/重试/死信"机制，62 章讲"Kafka Lag"，**本章讲"积压发生时的处置流程"**。
 *   - 易错点：无限重试（阻塞分区）；DLQ 不告警（坏消息静默堆积）；DLQ 不重放（数据永久丢失）；只看 lag 不看速率（误判）。
 * ============================================================
 */

package com.heima.ch78;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class DlqBacklog {
    record Message(String id, String payload) {
    }

    static final int MAX_RETRY = 3;
    static final List<Message> dlq = new ArrayList<>();
    static final Map<String, Integer> attempts = new LinkedHashMap<>();
    static final List<String> consumed = new ArrayList<>();

    /** 消费一条：失败按指数退避重试，超过上限进 DLQ */
    static void consume(Message msg, boolean permanentFailure) {
        int attempt = attempts.getOrDefault(msg.id(), 0);
        while (attempt < MAX_RETRY) {
            attempt++;
            attempts.put(msg.id(), attempt);
            System.out.printf("  消费 %s 第 %d 次 ... ", msg.id(), attempt);
            if (!permanentFailure && attempt >= 2) {
                System.out.println("成功（瞬时故障已恢复）");
                consumed.add(msg.id());
                return;
            }
            long backoff = 100L * (1L << (attempt - 1));
            System.out.printf("失败，退避 %dms%n", backoff);
        }
        System.out.printf("  超过 %d 次 -> 转入死信队列%n", MAX_RETRY);
        dlq.add(msg);
    }

    /** 积压：生产 - 消费 */
    static void backlog(long produced, long consumedCount, long consumeRatePerSec,
                        long produceRatePerSec) {
        long lag = produced - consumedCount;
        System.out.printf("  produced=%,d consumed=%,d lag=%,d%n", produced, consumedCount, lag);
        System.out.printf("  生产速率=%,d/s 消费速率=%,d/s -> %s%n",
                produceRatePerSec, consumeRatePerSec,
                produceRatePerSec > consumeRatePerSec ? "积压将持续增长" : "可收敛");
        if (produceRatePerSec > consumeRatePerSec) {
            double hours = (double) lag / (produceRatePerSec - consumeRatePerSec) / 3600;
            System.out.printf("  按当前速率，%.1f 小时后 lag 翻倍（需扩容/优化）%n", hours);
        }
    }

    public static void main(String[] args) {
        System.out.println("【瞬时故障：重试后成功】");
        consume(new Message("msg-1", "order-1"), false);
        System.out.println();

        System.out.println("【永久故障：重试 3 次后进 DLQ，分区继续】");
        consume(new Message("msg-2", "bad-data"), true);
        consume(new Message("msg-3", "order-3"), false);

        System.out.println();
        System.out.println("【DLQ】");
        dlq.forEach(m -> System.out.println("  " + m));
        System.out.println("  告警 -> 人工修复 -> 重放（replay）-> 消费者幂等（78-03）");

        System.out.println();
        System.out.println("【积压诊断】");
        backlog(1_000_000, 800_000, 2_000, 2_500);
        System.out.println();
        backlog(1_000_000, 800_000, 3_000, 2_500);

        System.out.println();
        System.out.println("【积压处置顺序】");
        String[][] steps = {
                {"1. 看 lag 趋势", "持续增长还是脉冲", "区分流量突增与消费故障"},
                {"2. 看单条耗时", "消费线程在等什么", "下游慢/锁/大消息"},
                {"3. 扩容", "加消费者/加分区", "分区数决定并行上限"},
                {"4. 降级", "跳过非关键消息/批量", "先止血再根治"},
                {"5. 复盘", "坏消息进 DLQ 的原因", "补校验/补告警"},
        };
        for (String[] s : steps) {
            System.out.printf("  %-14s %-24s %s%n", s[0], s[1], s[2]);
        }
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现指数退避重试（最多 3 次），失败转 DLQ，验证分区不再被阻塞。
 * 2. 给定生产/消费速率，计算 lag 翻倍时间，并给出扩容方案。
 * 3. 思考题：为什么"无限重试"比"进 DLQ"更危险？从队头阻塞与磁盘堆积分析。
 * ============================================================
 */
