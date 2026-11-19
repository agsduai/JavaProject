/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：80-10
 * 课名：性能回归与门禁
 * ------------------------------------------------------------
 * 知识点：
 *   - **性能回归要"可比"**：**同一机器、同一数据集、同一 JVM 参数、同一预热**——**否则测出的差异可能是噪声**（80-02）。
 *   - **门禁的判定**：**基线 vs 候选**，**差异超过阈值（如 P99 +10%）且超过噪声范围**才失败；**阈值太严会频繁误报**（团队关闭门禁），**太松会漏掉真回归**。
 *   - **统计显著性**：**多次运行取中位数/最优**，**看波动范围**（min/max）；**单次对比不可信**——**CI 机器噪声大时尤其要谨慎**。
 *   - **门禁的落点**：**核心接口的基准测试**（JMH）与**端到端压测**（80-08）两层——**前者防代码级退化，后者防容量退化**。
 *   - 与 41 章的关系：41 章 CI 门禁拦"测试失败/扫描问题"，**本章加一道"性能门禁"**——**性能也是质量属性**。
 *   - 易错点：用绝对值阈值（机器不同就废）；不看噪声（误报）；只测一次（随机通过/失败）；门禁不区分核心/非核心接口。
 * ============================================================
 */

package com.heima.ch80;

import java.util.ArrayList;
import java.util.List;

// ==================== 示例代码 ====================

public class PerfRegressionGate {
    record Run(double p50, double p99, double throughput) {
    }

    record Baseline(String name, double p99, double noisePercent) {
    }

    static double median(List<Double> values) {
        List<Double> sorted = new ArrayList<>(values);
        sorted.sort(null);
        int n = sorted.size();
        return n % 2 == 1 ? sorted.get(n / 2)
                : (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2;
    }

    static boolean gate(String name, Baseline base, List<Run> runs, double thresholdPercent) {
        List<Double> p99s = new ArrayList<>();
        List<Double> tps = new ArrayList<>();
        for (Run r : runs) {
            p99s.add(r.p99());
            tps.add(r.throughput());
        }
        double p99 = median(p99s);
        double tp = median(tps);
        double delta = (p99 - base.p99()) / base.p99() * 100;
        double allowed = Math.max(thresholdPercent, base.noisePercent());

        System.out.printf("  %-12s 基线P99=%.0fms 候选P99=%.0fms 变化=%+.1f%% 噪声=±%.1f%%%n",
                name, base.p99(), p99, delta, base.noisePercent());
        System.out.printf("    吞吐中位数=%.0f req/s，P99 波动=[%.0f, %.0f]%n",
                tp, p99s.stream().mapToDouble(Double::doubleValue).min().orElse(0),
                p99s.stream().mapToDouble(Double::doubleValue).max().orElse(0));

        boolean pass = delta <= allowed;
        System.out.printf("    门禁（阈值 %.1f%%）：%s%n", allowed,
                pass ? "通过" : "失败：性能回归");
        return pass;
    }

    public static void main(String[] args) {
        System.out.println("【基线对比（取 5 次运行中位数）】");
        boolean a = gate("order-query",
                new Baseline("order-query", 120, 3),
                List.of(new Run(40, 118, 5_000), new Run(41, 121, 5_050),
                        new Run(39, 119, 5_100), new Run(42, 122, 4_980),
                        new Run(40, 120, 5_020)),
                10);

        System.out.println();
        boolean b = gate("order-create",
                new Baseline("order-create", 200, 5),
                List.of(new Run(60, 230, 2_000), new Run(62, 235, 1_980),
                        new Run(61, 228, 2_020), new Run(60, 232, 2_010),
                        new Run(63, 236, 1_990)),
                10);

        System.out.println();
        boolean c = gate("user-query",
                new Baseline("user-query", 80, 8),
                List.of(new Run(20, 85, 8_000), new Run(21, 86, 7_950),
                        new Run(20, 84, 8_050), new Run(22, 87, 7_980),
                        new Run(21, 85, 8_010)),
                10);

        System.out.println();
        System.out.println("【结果】order-query=" + (a ? "通过" : "失败")
                + "，order-create=" + (b ? "通过" : "失败")
                + "，user-query=" + (c ? "通过" : "失败"));

        System.out.println();
        System.out.println("【门禁设计】");
        String[][] rules = {
                {"阈值", "max(业务阈值, 噪声)", "避免噪声误报"},
                {"多次运行", "5 次取中位数", "单次不可信"},
                {"分层", "核心接口严、非核心宽", "资源有限"},
                {"可解释", "输出基线/候选/波动", "失败能定位"},
        };
        for (String[] r : rules) {
            System.out.printf("  %-8s %-26s %s%n", r[0], r[1], r[2]);
        }

        System.out.println();
        System.out.println("【结论】性能门禁要「可比 + 多次 + 噪声感知」，否则会被团队关掉");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现性能门禁：基线 vs 5 次候选的中位数 P99，超过阈值且超过噪声才失败。
 * 2. 构造"噪声很大"的候选数据，验证门禁不会误报（阈值取 max(业务阈值, 噪声)）。
 * 3. 思考题：为什么"单次对比"不可信？从 CI 机器噪声、JIT、GC 抖动分析。
 * ============================================================
 */
