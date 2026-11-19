/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：62-10
 * 课名：运维与积压排查
 * ------------------------------------------------------------
 * 知识点：
 *   - **积压（Consumer Lag）= 生产速率 > 消费速率**：`lag = logEndOffset - consumerOffset`——**Lag 是消费健康度的第一指标**（42 章饱和度思想）。
 *   - **积压的四类原因与处置**：① **消费者不足**（加消费者，但受分区数上限 62-06）；② **消费逻辑慢**（优化处理/批量/异步，59 章调优）；③ **消费者反复 Rebalance**（查 `max.poll.interval.ms`，62-06）；④ **生产突增**（削峰/扩容/降级）。
 *   - **排查的观测面**：**主题级**（生产/消费速率、Lag 曲线）、**分区级**（哪个分区 Lag 集中——倾斜 59-08）、**消费者级**（poll 耗时、提交间隔）、**Broker 级**（磁盘、网络、ISR 收缩 62-03）。
 *   - **Lag 突然不动的两种解释**：**消费者挂了**（看消费者存活/日志）或**生产也停了**（Lag 不变但速率都为 0）——**看速率而不是只看 Lag**。
 *   - **运维动作的红线**：**不要随便重置 offset**（重放会重复处理，需业务确认幂等）；**不要让消费者数长期 > 分区数**（浪费资源）；**不要在业务高峰做分区扩容**（会触发 Rebalance）。
 *   - 易错点：只看 Lag 不看速率（误判）；Lag 高就加消费者（分区数不够时无效）；重置 offset 未评估重复影响；分区倾斜不管（个别分区 Lag 大拖慢整体）。
 * ============================================================
 */

package com.heima.ch62;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class LagDiagnosis {
    record PartitionLag(int partition, long endOffset, long consumerOffset, double produceRate,
                        double consumeRate) {
        long lag() {
            return endOffset - consumerOffset;
        }

        String trend() {
            double net = produceRate - consumeRate;
            if (Math.abs(net) < 0.01) {
                return net == 0 ? "稳定" : (net > 0 ? "缓慢增长" : "缓慢下降");
            }
            return net > 0 ? "增长（生产 > 消费）" : "下降（消费 > 生产）";
        }
    }

    static void diagnose(List<PartitionLag> partitions) {
        long totalLag = partitions.stream().mapToLong(PartitionLag::lag).sum();
        System.out.printf("  总 Lag=%d%n", totalLag);
        for (PartitionLag p : partitions) {
            System.out.printf("    p%-2d lag=%-8d 生产=%.1f/s 消费=%.1f/s -> %s%n",
                    p.partition(), p.lag(), p.produceRate(), p.consumeRate(), p.trend());
        }
        long max = partitions.stream().mapToLong(PartitionLag::lag).max().orElse(0);
        double avg = totalLag / (double) partitions.size();
        if (max > avg * 3) {
            System.out.println("    倾斜：最大分区 Lag 是均值的 3 倍以上 -> 查 key 倾斜（59-08）");
        }
        long stalled = partitions.stream()
                .filter(p -> p.produceRate() == 0 && p.consumeRate() == 0).count();
        if (stalled == partitions.size()) {
            System.out.println("    全部速率归零 -> 检查消费者存活与生产端状态");
        } else if (partitions.stream().anyMatch(p -> p.produceRate() > 0 && p.consumeRate() == 0)) {
            System.out.println("    消费速率为零 -> 消费者卡死（处理阻塞/死锁/异常退出）");
        }
    }

    static void playbook() {
        System.out.println("【积压处置手册】");
        Map<String, String> book = new LinkedHashMap<>();
        book.put("消费者不足", "加消费者（<= 分区数），否则先扩分区");
        book.put("处理慢", "批量化/异步化/优化 SQL，看单条处理耗时");
        book.put("反复 Rebalance", "查 max.poll.interval.ms 与处理耗时（62-06）");
        book.put("分区倾斜", "按 key 拆分热点（39/59-08）");
        book.put("生产突增", "上游削峰/限流，或临时扩容");
        book.forEach((k, v) -> System.out.printf("  %-14s %s%n", k, v));
    }

    public static void main(String[] args) {
        System.out.println("【场景一：正常消费】");
        diagnose(List.of(
                new PartitionLag(0, 10_000, 9_900, 100, 100),
                new PartitionLag(1, 10_000, 9_950, 100, 100)));
        System.out.println();
        System.out.println("【场景二：单分区倾斜】");
        diagnose(List.of(
                new PartitionLag(0, 50_000, 20_000, 500, 60),
                new PartitionLag(1, 10_000, 9_980, 100, 100),
                new PartitionLag(2, 10_000, 9_990, 100, 100)));
        System.out.println();
        System.out.println("【场景三：消费者卡死】");
        diagnose(List.of(
                new PartitionLag(0, 30_000, 10_000, 120, 0),
                new PartitionLag(1, 30_000, 10_000, 120, 0)));
        System.out.println();
        playbook();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现 Lag 诊断器：分区级 Lag/速率/趋势 + 倾斜检测 + 卡死检测，用三组数据验证结论（62-10 场景）。
 * 2. 设计一套 Kafka 监控指标清单（主题/分区/消费者/Broker 四层），标注每项的告警阈值依据。
 * 3. 思考题：为什么"只看 Lag 不看速率"会误判？构造一个"生产停了、Lag 不变"的场景说明。
 * ============================================================
 */
