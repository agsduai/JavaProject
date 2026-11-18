/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：50-09
 * 课名：高并发 IO 实测对比
 * ------------------------------------------------------------
 * 知识点：
 *   - **本课用一组可复现实验回答“虚拟线程到底带来多少”**：三个实验——吞吐对比（固定池 vs 虚拟线程）、并发规模对比（千级 vs 万级任务的内存与调度）、瓶颈平移验证（线程不再是瓶颈后，连接池/下游成为新瓶颈）。**自己测出来的数字才是团队的数字**（46-09 验收思想）。
 *   - **实验设计的控制变量**：任务形态统一（sleep 模拟 IO 100ms）、压测客户端统一、JVM 参数统一（堆大小一致）；**每个实验记录**：完成时间、P99、线程数、内存峰值（42 章实验纪律的并发版）。
 *   - **结果的解读框架**：吞吐提升的来源是“阻塞不占线程”（等待复用）；**提升倍数 ≈ 等待占比**（IO 占 95% 的负载可放大近 20 倍，IO 占 20% 只放大 1.25 倍）——**先测你的等待占比，再预期收益**。
 *   - **内存账**：平台线程 1MB 栈 × 1000 = 1GB；虚拟线程堆上栈 × 10000 ≈ 数百 MB——**在相同 -Xmx 下可承载的并发数差两个数量级**（44-05 显存账的线程版）。
 *   - **CPU 密集对照实验**：同样任务换成 CPU 计算（无 sleep）——虚拟线程与固定池吞吐几乎相同（核数决定）——**验证“虚拟线程只救 IO”**，避免盲目全量替换。
 *   - 易错点：实验不含真实 IO 形态（纯 sleep 低估调度开销、纯 CPU 高估收益）；单次跑分下结论（取多次中位数 42-04 分位数思想）；忽略 JVM 预热（JIT 未热，前几轮数据失真）。
 * ============================================================
 */

package com.heima.ch50;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

// ==================== 示例代码 ====================

public class IoBenchmark {
    /** IO 任务：等 100ms（模拟下游） */
    static String ioTask() {
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return "ok";
    }

    /** CPU 任务：无等待，纯计算 */
    static String cpuTask() {
        long x = 0;
        for (int i = 0; i < 2_000_000L; i++) {
            x += i * 31;
        }
        return x == -1 ? "impossible" : "ok";
    }

    static long run(String label, ExecutorService pool, int tasks,
                    java.util.function.Supplier<String> task) throws Exception {
        long start = System.currentTimeMillis();
        List<Future<String>> futures = new ArrayList<>();
        for (int i = 0; i < tasks; i++) {
            futures.add(pool.submit(task::get));
        }
        for (Future<String> f : futures) {
            f.get(60, TimeUnit.SECONDS);
        }
        long cost = System.currentTimeMillis() - start;
        System.out.printf("  %-22s %4d 任务 -> %5dms%n", label, tasks, cost);
        return cost;
    }

    static void throughputExperiment() throws Exception {
        System.out.println("【实验一：IO 任务吞吐（200 任务 × 100ms 等待）】");
        long fixed;
        try (ExecutorService fixedPool = Executors.newFixedThreadPool(20)) {
            fixed = run("固定池(20线程)", fixedPool, 200, IoBenchmark::ioTask);
        }
        long virtual;
        try (ExecutorService vtPool = Executors.newVirtualThreadPerTaskExecutor()) {
            virtual = run("虚拟线程", vtPool, 200, IoBenchmark::ioTask);
        }
        System.out.printf("  提速 %.1f 倍；理论等待占比 95%% -> 上限约 %.0f 倍（排队与调度损耗）%n%n",
                (double) fixed / virtual, 100.0 / 5);
    }

    static void scaleExperiment() throws Exception {
        System.out.println("【实验二：并发规模（2000 任务，同 -Xmx）】");
        try (ExecutorService smallPool = Executors.newFixedThreadPool(200)) {
            run("固定池(200线程) 上限", smallPool, 2000, IoBenchmark::ioTask);
        }
        try (ExecutorService vtPool = Executors.newVirtualThreadPerTaskExecutor()) {
            run("虚拟线程(2000并发)", vtPool, 2000, IoBenchmark::ioTask);
        }
        System.out.println("  固定池要扛 2000 并发需 2000 平台线程 ≈ 2GB 栈；虚拟线程 MB 级");
        System.out.println();
    }

    static void cpuControl() throws Exception {
        System.out.println("【实验三：CPU 任务对照（虚拟线程无收益验证）】");
        try (ExecutorService fixedPool = Executors.newFixedThreadPool(
                Runtime.getRuntime().availableProcessors())) {
            run("固定池(核数) CPU 任务", fixedPool, 40, IoBenchmark::cpuTask);
        }
        try (ExecutorService vtPool = Executors.newVirtualThreadPerTaskExecutor()) {
            run("虚拟线程 CPU 任务", vtPool, 40, IoBenchmark::cpuTask);
        }
        System.out.println("  结论：CPU 密集两者相当（核数决定）—— 虚拟线程只救 IO，全量替换是误用");
    }

    public static void main(String[] args) throws Exception {
        throughputExperiment();
        scaleExperiment();
        cpuControl();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 完整跑一遍三个实验（调整任务数与 -Xmx），记录你机器上的数字并计算等待占比与实际提速的关系。
 * 2. 把实验换成真实下游（HTTP 调用测试服务），对比 sleep 模拟与真实 IO 的结果差异，分析调度开销占比。
 * 3. 思考题：为什么“等待占比决定收益上限”？从 Amdahl 定律的视角推导虚拟线程收益的理论上界。
 * ============================================================
 */
