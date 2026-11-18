/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：17-01
 * 课名：进程、线程、并发与并行
 * ------------------------------------------------------------
 * 知识点：
 *   - 进程是操作系统分配资源的最小单位，一个进程可以包含多个线程；线程是 CPU 调度的最小单位。
 *   - 并发：多个任务在**同一时间段**内交替执行（单核也能并发，靠快速切换）。
 *   - 并行：多个任务在**同一时刻**同时执行（需要多核 CPU）。
 *   - Java 程序天生多线程：`main` 是主线程，还有 GC 线程、异常处理线程等在后台运行。
 *   - 多线程的好处：充分利用多核 CPU、提高响应速度；代价：线程安全问题、上下文切换开销。
 *   - 易错：线程越多越快是错误认知，线程数超过核心数后频繁切换反而降低性能。
 * ============================================================
 */

package com.heima.ch17;

// ==================== 示例代码 ====================

public class ThreadConcept {
    public static void main(String[] args) {
        // 主线程：每个 Java 程序启动时自动创建
        Thread main = Thread.currentThread();
        System.out.println("主线程名称：" + main.getName());      // main
        System.out.println("主线程优先级：" + main.getPriority()); // 5
        System.out.println("可用处理器核心数：" + Runtime.getRuntime().availableProcessors());

        // 查看 JVM 中正在运行的线程
        System.out.println("JVM 启动时包含的线程：");
        Thread.getAllStackTraces().keySet().stream()
                .map(Thread::getName)
                .sorted()
                .forEach(name -> System.out.println("  - " + name));

        // 并发演示：单线程顺序执行两个任务
        long start = System.currentTimeMillis();
        task("任务A");
        task("任务B");
        System.out.println("顺序执行耗时：" + (System.currentTimeMillis() - start) + " ms");
    }

    public static void task(String name) {
        long sum = 0;
        for (int i = 0; i < 10_000_000; i++) {
            sum += i;
        }
        System.out.println(name + " 计算完成，结果：" + sum);
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 写一个程序打印当前线程名称、优先级、是否为守护线程，并列出 JVM 中所有线程名。
 * 2. 用一句话分别解释"并发"和"并行"，并各举一个生活中的例子。
 * 3. 运行一个计算密集型任务，分别用 1 个线程和 4 个线程完成，记录耗时并分析结果。
 * ============================================================
 */
