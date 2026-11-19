/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：80-02
 * 课名：基准测试与 JMH 思想
 * ------------------------------------------------------------
 * 知识点：
 *   - **微基准测试的三大陷阱**：**没有预热**（JIT 还没编译）、**死代码消除**（结果没被使用，编译器直接删）、**测量噪声**（GC/其他进程）——**JMH 就是为解决这些而生的**。
 *   - **预热（Warmup）**：**JIT 分层编译需要时间**，**前几千次执行可能慢 10 倍**——**必须丢弃预热阶段的数据**，**预热轮数按方法复杂度定**（简单方法 1~2 秒，复杂 3~5 秒）。
 *   - **测量方式**：**固定迭代次数**（测总耗时）或**固定时间**（测吞吐）；**取多次运行的统计**（平均值 + 置信区间），**不是跑一次就下结论**。
 *   - **Blackhole/volatile 消费结果**：**把结果写入 `volatile` 字段或 `Blackhole.consume`**，**防止 JIT 判定"无用代码"而删除**。
 *   - 与 30 章的关系：30 章讲"JVM 性能调优"，**本章讲"怎么科学地测量一段代码"**——**没有可信测量，调优就是玄学**。
 *   - 易错点：不预热（把 JIT 时间算进去）；结果不消费（测了个空）；单次运行（噪声当结论）；在压测线程里做 IO/日志（测的不是目标）。
 * ============================================================
 */

package com.heima.ch80;

// ==================== 示例代码 ====================

public class BenchmarkHarness {
    static volatile long sink;

    /** 被测实现 A：循环累加 */
    static long sumLoop(int n) {
        long sum = 0;
        for (int i = 1; i <= n; i++) {
            sum += i;
        }
        return sum;
    }

    /** 被测实现 B：公式 */
    static long sumFormula(int n) {
        return (long) n * (n + 1) / 2;
    }

    record Result(String name, double nsPerOp, double opsPerSec) {
    }

    static Result measure(String name, int n, int warmupRounds, int measureRounds,
                          int iterations) {
        // 预热：结果必须消费，否则可能被优化掉
        for (int r = 0; r < warmupRounds; r++) {
            for (int i = 0; i < iterations; i++) {
                sink = name.equals("loop") ? sumLoop(n) : sumFormula(n);
            }
        }
        long best = Long.MAX_VALUE;
        long total = 0;
        for (int r = 0; r < measureRounds; r++) {
            long t0 = System.nanoTime();
            for (int i = 0; i < iterations; i++) {
                sink = name.equals("loop") ? sumLoop(n) : sumFormula(n);
            }
            long cost = System.nanoTime() - t0;
            best = Math.min(best, cost);
            total += cost;
        }
        double avgNs = (double) total / measureRounds / iterations;
        double bestNs = (double) best / iterations;
        System.out.printf("  %-8s 平均=%8.2f ns/op 最快=%8.2f ns/op 吞吐=%12.0f ops/s%n",
                name, avgNs, bestNs, 1_000_000_000.0 / bestNs);
        return new Result(name, bestNs, 1_000_000_000.0 / bestNs);
    }

    public static void main(String[] args) {
        int n = 1_000;
        int iterations = 200_000;

        System.out.println("【预热前后对比（loop）】");
        long coldStart = System.nanoTime();
        for (int i = 0; i < 1000; i++) {
            sink = sumLoop(n);
        }
        double cold = (double) (System.nanoTime() - coldStart) / 1000;
        System.out.printf("  冷启动（前 1000 次）：%.1f ns/op%n", cold);

        System.out.println();
        System.out.println("【正式测量（预热 3 轮，测量 5 轮）】");
        Result loop = measure("loop", n, 3, 5, iterations);
        Result formula = measure("formula", n, 3, 5, iterations);

        System.out.println();
        System.out.printf("  公式比循环快 %.1f 倍%n", loop.nsPerOp() / formula.nsPerOp());

        System.out.println();
        System.out.println("【陷阱演示】");
        System.out.println("  1) 不预热：冷启动数据混入，结论偏慢");
        System.out.println("  2) 不消费结果：JIT 可能把整个循环删掉，测出 0 ns");
        System.out.println("  3) 单次运行：GC/调度噪声大，至少 5 轮取中位数/最优");
        System.out.println("  4) 在计时区间做日志/IO：测到的是 IO 不是算法");

        System.out.println();
        System.out.println("【JMH 对应】@Warmup / @Measurement / @Fork / Blackhole.consume");
        System.out.println("  生产基准：用 JMH，不要手写 nanoTime（本章手写只为讲原理）");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 手写一个基准测试：预热 3 轮、测量 5 轮，对比两种实现的 ns/op。
 * 2. 故意去掉"结果消费"，观察测试结果是否异常（可能接近 0），解释死代码消除。
 * 3. 思考题：为什么"单次基准测试"不可信？从 JIT、GC、CPU 调度噪声分析。
 * ============================================================
 */
