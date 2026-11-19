/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：80-03
 * 课名：延迟分布与分位数
 * ------------------------------------------------------------
 * 知识点：
 *   - **平均值是延迟的"谎言"**：**99 个请求 10ms、1 个请求 10s，平均只有 110ms**，**但那个用户等了 10 秒**——**必须看分位数（P50/P95/P99/P999）**。
 *   - **分位数的含义**：**P99 = 99% 的请求快于该值**——**P99 高说明长尾严重**；**P999 用于核心链路**（支付/登录）——**不同业务选不同分位**。
 *   - **直方图（Histogram）**：**把延迟分桶统计**，**可算任意分位、可跨实例聚合**——**生产用直方图（Prometheus Histogram），不要用平均**。
 *   - **长尾的常见来源**：**GC 停顿、锁等待、慢查询、下游超时重试、冷缓存**——**P99 优化要针对长尾，不是优化平均值**（80-05 的火焰图）。
 *   - 与 42 章的关系：42 章可观测性讲"Histogram 分桶算分位"，**本章讲"为什么必须看分位、怎么看"**。
 *   - 易错点：SLO 用平均延迟（掩盖长尾）；分位数跨实例"平均"（不能平均分位数，要合并直方图）；只看 P99 不看 P999；采样率过低（长尾样本不足）。
 * ============================================================
 */

package com.heima.ch80;

import java.util.Arrays;

// ==================== 示例代码 ====================

public class LatencyDistribution {
    static int percentile(int[] sorted, double p) {
        int idx = (int) Math.ceil(p / 100.0 * sorted.length) - 1;
        idx = Math.max(0, Math.min(sorted.length - 1, idx));
        return sorted[idx];
    }

    static void histogram(int[] sorted, int bucketMs) {
        System.out.println("【直方图（桶宽 " + bucketMs + "ms）】");
        int max = sorted[sorted.length - 1];
        int buckets = max / bucketMs + 1;
        int[] counts = new int[buckets];
        for (int v : sorted) {
            counts[v / bucketMs]++;
        }
        for (int i = 0; i < buckets; i++) {
            if (counts[i] == 0) {
                continue;
            }
            System.out.printf("  %4d~%4dms %5d %s%n", i * bucketMs,
                    (i + 1) * bucketMs - 1, counts[i], "#".repeat(Math.min(60, counts[i])));
        }
    }

    public static void main(String[] args) {
        int[] latencies = new int[1000];
        Arrays.fill(latencies, 0, 990, 10);          // 99% 请求 10ms
        for (int i = 990; i < 1000; i++) {
            latencies[i] = 10_000;                   // 1% 请求 10s
        }
        Arrays.sort(latencies);

        System.out.println("【样本：990 个 10ms + 10 个 10s】");
        long sum = 0;
        for (int v : latencies) {
            sum += v;
        }
        double avg = (double) sum / latencies.length;
        System.out.printf("  平均值=%.1fms%n", avg);
        System.out.printf("  P50=%dms P95=%dms P99=%dms P999=%dms MAX=%dms%n",
                percentile(latencies, 50), percentile(latencies, 95),
                percentile(latencies, 99), percentile(latencies, 99.9),
                latencies[latencies.length - 1]);

        System.out.println();
        System.out.println("  平均值看起来很好（110ms），但 1% 用户等了 10 秒");
        System.out.println("  P99.9=10s 才是真实长尾；平均值把长尾稀释了");

        System.out.println();
        histogram(latencies, 1000);

        System.out.println();
        System.out.println("【分位数不能跨实例平均】");
        int[] a = {10, 10, 10, 10, 1000};
        int[] b = {20, 20, 20, 20, 2000};
        System.out.printf("  实例 A P99=%dms，实例 B P99=%dms%n",
                percentile(a, 99), percentile(b, 99));
        System.out.println("  错误做法：(1000+2000)/2=1500ms");
        System.out.println("  正确做法：合并直方图后重新算分位（样本加权）");

        System.out.println();
        System.out.println("【SLO 建议】");
        String[][] slo = {
                {"普通接口", "P99 < 200ms", "允许少量长尾"},
                {"核心接口", "P999 < 500ms", "支付/登录"},
                {"批处理", "P99 < 5s", "吞吐优先"},
        };
        for (String[] s : slo) {
            System.out.printf("  %-8s %-18s %s%n", s[0], s[1], s[2]);
        }
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 构造"99 个快 + 1 个极慢"的样本，对比平均值与 P99，说明平均值为何误导。
 * 2. 实现直方图分桶并计算 P50/P95/P99，验证与排序法结果一致。
 * 3. 思考题：为什么"分位数不能求平均"？从"分位是顺序统计量"分析，并给出正确聚合方式。
 * ============================================================
 */
