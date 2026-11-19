/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：72-07
 * 课名：性能特征对比
 * ------------------------------------------------------------
 * 知识点：
 *   - **启动性能**：native **毫秒级**（无类加载/无 JIT 预热）；JVM **秒级**（Spring Boot 10~30s）——**差距 2~3 个数量级**。
 *   - **内存占用**：native 常驻**30~100MB**（无 JVM 元空间/JIT 代码缓存）；JVM **200~500MB**——**小服务差距 3~5 倍**。
 *   - **峰值吞吐**：**JIT 通常更高**（运行期热点优化：内联、去虚拟化、逃逸分析、分层编译）；native **略低 5~20%**（静态分析保守）——**但差距在缩小**（PGO 优化）。
 *   - **尾延迟**：JVM 有 **GC 停顿**（即使 G1/ZGC 也有）；native **无 JIT 编译线程争抢**、GC 更简单——**尾延迟可能更稳定**。
 *   - **预热曲线**：JVM **前 1~5 分钟性能爬坡**（JIT 编译）；native **从一开始就是满性能**——**短任务场景 native 总耗时更低**。
 *   - 与 30 章的关系：30 章讲的 JIT/GC 调优在 native 下**大半失效**（无 JIT、GC 简化）——**调优手段要换**。
 *   - 易错点：只比启动不比峰值（长驻服务可能更慢）；用短基准测试比 native（JVM 没预热完）；忽略预热曲线（JVM 前几分钟差很多）；以为 native 全面优于 JVM。
 * ============================================================
 */

package com.heima.ch72;

import java.util.ArrayList;
import java.util.List;

// ==================== 示例代码 ====================

public class PerformanceCompare {
    /** 预热曲线：JVM 逐步接近峰值，native 从 0 时刻即满性能 */
    static double jvmThroughput(int second) {
        if (second <= 0) {
            return 0;
        }
        // 简化模型：指数爬坡到峰值 10000，5 分钟（300s）接近峰值
        return 10_000 * (1 - Math.exp(-second / 60.0));
    }

    static double nativeThroughput(int second) {
        return 9_000;                    // 静态编译，峰值略低但无爬坡
    }

    static void warmupCurve() {
        System.out.println("【吞吐预热曲线（QPS）】");
        System.out.printf("  %-8s %-12s %-12s %s%n", "时间", "JVM", "native", "说明");
        for (int s : new int[]{1, 10, 30, 60, 120, 300}) {
            System.out.printf("  %-8s %-12.0f %-12.0f %s%n",
                    s + "s", jvmThroughput(s), nativeThroughput(s),
                    jvmThroughput(s) > nativeThroughput(s) ? "JVM 反超" : "native 领先");
        }
        System.out.println("  短任务（<60s）native 总吞吐更高；长驻服务 JVM 反超");
        System.out.println();
    }

    /** 任务时长对"总耗时"的影响：启动时间计入总耗时 */
    static void totalTime() {
        System.out.println("【不同任务时长的总耗时】");
        double jvmStartup = 15, nativeStartup = 0.1;
        double jvmSteadyQps = 10_000, nativeSteadyQps = 9_000;
        System.out.printf("  %-14s %-14s %-14s %s%n", "任务量", "JVM 总耗时", "native 总耗时", "胜者");
        for (int tasks : new int[]{100, 1_000, 100_000, 10_000_000}) {
            double jvmTotal = jvmStartup + tasks / jvmSteadyQps;
            double nativeTotal = nativeStartup + tasks / nativeSteadyQps;
            System.out.printf("  %-14d %-14.2f %-14.2f %s%n", tasks, jvmTotal, nativeTotal,
                    jvmTotal > nativeTotal ? "native" : "JVM");
        }
        System.out.println("  临界点：任务量小 -> native 完胜；任务量大 -> JVM 吞吐优势覆盖启动开销");
        System.out.println();
    }

    static void tailLatency() {
        System.out.println("【尾延迟构成】");
        List<String> rows = new ArrayList<>(List.of(
                "JVM：GC 停顿（ZGC <1ms，但仍有）+ JIT 编译线程 CPU 争抢",
                "native：GC 更简单（无并发标记线程竞争）+ 无 JIT 线程",
                "结论：native 的 P99.9 通常更平稳（但缺少 JIT 优化可能 P50 略差）"));
        rows.forEach(r -> System.out.println("  - " + r));
        System.out.println();
    }

    public static void main(String[] args) {
        warmupCurve();
        totalTime();
        tailLatency();
        System.out.println("  选型：启动敏感/短任务/内存受限 -> native；长驻高吞吐 -> JVM（或先 native 后评估）");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 用预热曲线模型计算"处理 10 万请求"在 JVM 与 native 下的总耗时（含启动），找出临界任务量。
 * 2. 设计一次压测方案：对比 JVM 与 native 的 QPS/P99/内存，要求包含预热与非预热两组。
 * 3. 思考题：为什么"短任务 native 总耗时更低"？从"启动开销在总耗时中的占比"分析。
 * ============================================================
 */
