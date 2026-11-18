/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：50-04
 * 课名：虚拟线程原理：载体线程与挂载
 * ------------------------------------------------------------
 * 知识点：
 *   - **虚拟线程 = JVM 管理的轻量线程**：不映射内核线程，栈存在堆上（初始几百字节，按需增长）——**百万级虚拟线程可行**。JDK 21 正式发布（Project Loom 的第一块成果）。
 *   - **载体线程（carrier thread）机制**：虚拟线程运行时"挂载"到一个载体线程（ForkJoinPool，默认 = CPU 核数）；**阻塞时（IO/sleep/锁等待）虚拟线程从载体上"卸载"，载体去跑别的虚拟线程**——阻塞不再占用 OS 线程。恢复时重新挂载（可能在另一个载体上）。
 *   - **卸载的条件**：阻塞在可感知的操作（网络 IO、sleep、Object.wait、ReentrantLock）——**JDK 把这些阻塞点改造成了"让出载体"的挂起点**。兼容性奇迹：现有阻塞代码不改就能享受。
 *   - **不卸载的场景（pinning）**：**synchronized 块内阻塞**、native 方法（JNI）内阻塞——载体被钉住，其他虚拟线程无法复用。**JDK 21 的最大坑**（50-06 细讲，JDK 24 已修复 synchronized）。
 *   - **应该怎么用**：**每任务一线程（thread-per-request）回归**——虚拟线程廉价，不再需要池化；`Executors.newVirtualThreadPerTaskExecutor()` 是标准姿势；**不要池化虚拟线程、不要给虚拟线程设上限池**（要限并发用 Semaphore）。
 *   - **可观测**：`Thread.isVirtual()` 判断；JFR 事件（pinning/提交/卸载）；**线程名要设置**（海量虚拟线程的 dump 是茫茫多，命名规范 42-02 同源）。
 *   - 易错点：池化虚拟线程（newFixedThreadPool 包虚拟线程工厂——失去意义还限制并发）；在虚拟线程里跑 CPU 重活（占用载体，影响全部）；用虚拟线程数当并发限流手段（改用 Semaphore 显式限流）；忽略 pinning（synchronized 里调阻塞 IO）。
 * ============================================================
 */

package com.heima.ch50;

import java.time.Duration;
import java.util.concurrent.Executors;

// ==================== 示例代码 ====================

public class VirtualThreadInternals {
    static void mountUnmount() throws Exception {
        System.out.println("【挂载与卸载：阻塞不占载体】");
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 5; i++) {
                final int id = i;
                executor.submit(() -> {
                    System.out.printf("  任务%d 开始（虚拟线程=%s）%n", id, Thread.currentThread());
                    Thread.sleep(100);              // 阻塞点：卸载，载体去跑别的任务
                    System.out.printf("  任务%d 完成（可能换了载体）%n", id);
                    return null;
                });
            }
        }                                    // close 等待全部完成
        System.out.println("  5 个任务并发完成；载体线程只有 CPU 核数个");
        System.out.println();
    }

    static void scaleProof() throws Exception {
        System.out.println("【规模证明：10 万虚拟线程同时 sleep】");
        long start = System.currentTimeMillis();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 100_000; i++) {
                executor.submit(() -> {
                    Thread.sleep(500);
                    return null;
                });
            }
        }
        long cost = System.currentTimeMillis() - start;
        System.out.printf("  10 万任务 × 500ms -> 总耗时 %dms（并发挂起，内存仅 MB 级栈）%n", cost);
        System.out.println("  对照平台线程：10 万 × 1MB 栈 = 100GB，直接不可能");
        System.out.println();
    }

    static void naming() {
        System.out.println("【命名规范：海量线程的可观测前提】");
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            executor.submit(() -> {
                Thread.currentThread().setName("order-pay-" + Thread.currentThread().threadId());
                System.out.println("  " + Thread.currentThread());
                return null;
            });
        }
        System.out.println("  dump/日志里能定位业务语义（42-02 结构化日志同源）");
    }

    public static void main(String[] args) throws Exception {
        mountUnmount();
        scaleProof();
        naming();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实测本章两个示例，记录 10 万虚拟线程的耗时与堆内存（-Xmx256m 下验证）。
 * 2. 写一个 pinning 检测脚本：JFR 事件（jdk.VirtualThreadPinned）统计 pinned 时长 Top 类与方法。
 * 3. 思考题：为什么“每任务一线程”在平台线程时代是错误实践而在虚拟线程时代是推荐？从线程成本与池化目的（复用昂贵资源）的演变分析。
 * ============================================================
 */
