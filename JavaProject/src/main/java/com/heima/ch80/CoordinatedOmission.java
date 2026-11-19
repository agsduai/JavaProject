/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：80-04
 * 课名：协调遗漏与开放式模型
 * ------------------------------------------------------------
 * 知识点：
 *   - **协调遗漏（Coordinated Omission）**：**压测工具等上一个响应回来才发下一个请求**（闭环模型）——**服务卡住时，工具也停发**，**卡顿期间没有新样本，延迟被严重低估**。
 *   - **闭环 vs 开放模型**：**闭环**（固定并发，响应慢则发得慢）适合"模拟真实用户"；**开放**（固定到达率，按计划发）适合"模拟流量"——**压测要用开放模型或至少修正**。
 *   - **正确度量**：**按"计划发送时间"算延迟**（`完成时间 - 计划时间`），**卡顿期间排队的请求全部计入长尾**——**这样 P99 才会暴露问题**。
 *   - **与 80-03 的联系**：**协调遗漏直接制造"假的好 P99"**——**很多"压测通过、上线就崩"都是这个原因**。
 *   - 与 41 章的关系：41 章金丝雀/容量，**本章讲"压测数据可不可信"**——**不可信的压测比不压更危险**。
 *   - 易错点：用闭环压测算延迟（低估）；不记录"计划时间"（只记开始/结束）；压测工具单机瓶颈（压不上去）；不模拟真实流量分布（全等比例）。
 * ============================================================
 */

package com.heima.ch80;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// ==================== 示例代码 ====================

public class CoordinatedOmission {
    static final long SERVICE_MS = 100;
    static final long PAUSE_START = 2_000;
    static final long PAUSE_MS = 5_000;

    static long serviceTime(long now) {
        if (now >= PAUSE_START && now < PAUSE_START + PAUSE_MS) {
            return PAUSE_MS;                          // 卡顿：请求被堵住
        }
        return SERVICE_MS;
    }

    /** 闭环：上一个完成后才发下一个，延迟只算"服务时间" */
    static List<Long> closedLoop(long durationMs) {
        List<Long> latencies = new ArrayList<>();
        long now = 0;
        while (now < durationMs) {
            long cost = serviceTime(now);
            latencies.add(cost);                      // 工具视角：开始到结束
            now += cost;
        }
        return latencies;
    }

    /** 开放：按固定到达率发，延迟 = 完成 - 计划时间 */
    static List<Long> openLoop(long durationMs, long intervalMs) {
        List<Long> latencies = new ArrayList<>();
        long serverFree = 0;
        for (long planned = 0; planned < durationMs; planned += intervalMs) {
            long start = Math.max(planned, serverFree);
            long cost = serviceTime(start);
            long done = start + cost;
            latencies.add(done - planned);            // 包含排队等待
            serverFree = done;
        }
        return latencies;
    }

    static void stats(String label, List<Long> latencies) {
        List<Long> sorted = new ArrayList<>(latencies);
        Collections.sort(sorted);
        double avg = sorted.stream().mapToLong(Long::longValue).average().orElse(0);
        long p99 = sorted.get((int) Math.ceil(sorted.size() * 0.99) - 1);
        long max = sorted.get(sorted.size() - 1);
        System.out.printf("  %-10s 样本=%-4d 平均=%6.0fms P99=%6dms MAX=%6dms%n",
                label, sorted.size(), avg, p99, max);
    }

    public static void main(String[] args) {
        long duration = 20_000;

        System.out.println("【闭环压测（等响应再发）】");
        List<Long> closed = closedLoop(duration);
        stats("closed", closed);

        System.out.println();
        System.out.println("【开放压测（固定 10 req/s）】");
        List<Long> open = openLoop(duration, 100);
        stats("open", open);

        System.out.println();
        System.out.println("【差异原因】");
        System.out.println("  闭环：卡顿 5s 期间工具停发，只有 1 个样本记录 5s 延迟");
        System.out.println("  开放：卡顿期间按计划发了 50 个请求，全部排队 5s");
        System.out.println("  -> 开放模型 P99 才反映真实用户体验");

        System.out.println();
        System.out.println("【修正方法】");
        String[][] fixes = {
                {"记录计划时间", "延迟=完成-计划", "而不是完成-开始"},
                {"开放模型", "固定到达率", "wrk2/JMH 的 throughput 模式"},
                {"补样本", "卡顿期间按理论值补齐", "HdrHistogram 的 recordValueWithExpectedInterval"},
        };
        for (String[] f : fixes) {
            System.out.printf("  %-12s %-24s %s%n", f[0], f[1], f[2]);
        }

        System.out.println();
        System.out.println("【结论】闭环压测适合「模拟并发用户」，算容量要看开放模型/修正后的延迟");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 模拟"服务卡顿 5 秒"，分别用闭环/开放模型统计 P99，解释差异。
 * 2. 用"完成时间 - 计划时间"修正闭环样本，对比修正前后的 P99。
 * 3. 思考题：为什么"压测通过、上线就崩"常与协调遗漏有关？从"长尾样本缺失"分析。
 * ============================================================
 */
