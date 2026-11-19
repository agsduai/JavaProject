/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：80-09
 * 课名：瓶颈定位与扩容决策
 * ------------------------------------------------------------
 * 知识点：
 *   - **利特尔法则（Little's Law）**：**L = λ × W**（并发数 = 到达率 × 平均停留时间）——**已知两个可推第三个**，**扩容前先算"需要多少并发"**。
 *   - **利用率与排队**：**ρ = λ / μ**（到达率/服务率）；**ρ 接近 1 时排队时间非线性上升**（M/M/1 的 `Wq = ρ/(μ-λ)`）——**70% 利用率是常见的安全水位**（80% 已接近拐点）。
 *   - **扩容决策三问**：**瓶颈是资源还是依赖**（CPU/连接池/DB/下游）、**扩容能否线性提升**（有状态服务/共享 DB 往往不能）、**成本是否可接受**（云成本 56 章）。
 *   - **拐点识别**：**压测时逐步加并发，画"并发-吞吐-延迟"曲线**——**吞吐不再上升、延迟陡增的点就是拐点**（不是 CPU 100% 才算）。
 *   - 与 56 章的关系：56 章讲"容量管理与水位"，**本章讲"用排队论算清楚该扩多少"**。
 *   - 易错点：线性外推容量（忽略排队非线性）；只扩应用不扩 DB（DB 成新瓶颈）；不设水位告警（临时扩容来不及）；扩容后不复测（配置漂移）。
 * ============================================================
 */

package com.heima.ch80;

// ==================== 示例代码 ====================

public class CapacityModel {
    /** 利特尔法则：L = λ * W */
    static double littleConcurrency(double arrivalRatePerSec, double latencySec) {
        return arrivalRatePerSec * latencySec;
    }

    /** M/M/1 平均排队时间：Wq = ρ / (μ - λ) */
    static double queueWaitSec(double arrivalRate, double serviceRate) {
        if (arrivalRate >= serviceRate) {
            return Double.POSITIVE_INFINITY;
        }
        double rho = arrivalRate / serviceRate;
        return rho / (serviceRate - arrivalRate);
    }

    /** M/M/1 平均停留时间：W = 1/(μ-λ) */
    static double responseSec(double arrivalRate, double serviceRate) {
        if (arrivalRate >= serviceRate) {
            return Double.POSITIVE_INFINITY;
        }
        return 1.0 / (serviceRate - arrivalRate);
    }

    public static void main(String[] args) {
        double serviceRate = 100;                    // 单实例 100 req/s

        System.out.println("【利特尔法则】");
        double lambda = 80;
        double latency = 0.05;                       // 50ms
        System.out.printf("  λ=%.0f/s，W=%.3fs -> 并发 L=%.1f%n",
                lambda, latency, littleConcurrency(lambda, latency));

        System.out.println();
        System.out.println("【利用率与排队（单实例 μ=100/s）】");
        System.out.printf("  %-8s %-8s %-12s %-12s %s%n",
                "λ", "ρ", "排队Wq", "响应W", "判断");
        double[] rates = {50, 70, 80, 90, 95, 99};
        for (double r : rates) {
            double rho = r / serviceRate;
            double wq = queueWaitSec(r, serviceRate);
            double w = responseSec(r, serviceRate);
            String verdict = rho <= 0.7 ? "安全" : rho < 0.85 ? "接近拐点" : "危险";
            System.out.printf("  %-8.0f %-8.2f %-12.3f %-12.3f %s%n",
                    r, rho, wq, w, verdict);
        }

        System.out.println();
        System.out.println("【扩容计算】");
        double targetRps = 450;
        double perInstance = 100;
        double safeUtilization = 0.7;
        int need = (int) Math.ceil(targetRps / (perInstance * safeUtilization));
        System.out.printf("  目标 %.0f req/s，单实例 %.0f，安全水位 %.0f%% -> 至少 %d 个实例%n",
                targetRps, perInstance, safeUtilization * 100, need);
        System.out.printf("  若只按 100%% 利用率算：%d 个（没有余量，延迟会爆炸）%n",
                (int) Math.ceil(targetRps / perInstance));

        System.out.println();
        System.out.println("【拐点识别】");
        int[][] curve = {
                {10, 950, 12},
                {30, 2_800, 15},
                {50, 4_600, 20},
                {70, 6_200, 35},
                {85, 6_800, 90},
                {95, 6_900, 400},
        };
        System.out.printf("  %-8s %-12s %-10s %s%n", "并发", "吞吐(req/s)", "P99(ms)", "判断");
        for (int[] p : curve) {
            String verdict = p[2] < 50 ? "线性区" : p[2] < 150 ? "拐点" : "过载区";
            System.out.printf("  %-8d %-12d %-10d %s%n", p[0], p[1], p[2], verdict);
        }

        System.out.println();
        System.out.println("【结论】按 70% 水位扩容；扩容前确认瓶颈不是 DB/下游/连接池");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 用利特尔法则计算"目标 QPS 下需要多少并发"，并与压测结果对照。
 * 2. 画出"并发-吞吐-延迟"曲线，找出拐点并解释为什么吞吐不再上升。
 * 3. 思考题：为什么"按 100% 利用率算实例数"会出事？从 M/M/1 排队非线性分析。
 * ============================================================
 */
