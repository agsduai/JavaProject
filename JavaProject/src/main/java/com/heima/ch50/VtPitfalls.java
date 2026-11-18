/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：50-06
 * 课名：虚拟线程的坑
 * ------------------------------------------------------------
 * 知识点：
 *   - **Pinning（钉住）：synchronized 内阻塞不卸载**——虚拟线程阻塞在 `synchronized` 块内时**无法从载体卸载**，载体线程被占死；高并发下“核数个载体”全部被钉住 -> 服务假死。**JDK 21 生产第一坑**（JDK 24 已修复 synchronized 的 pinning，21 必须代码层规避）。
 *   - **pinning 的检测与修复**：JFR 事件 `jdk.VirtualThreadPinned` 记录钉住时长与栈——筛 >10ms 事件按栈聚合 Top 方法；命中 synchronized -> **热点改 ReentrantLock**（可卸载），不必全局替换（先测后改）。
 *   - **池化误区**：`newFixedThreadPool(200, virtualFactory)` 是反模式——池化为了复用昂贵资源，虚拟线程廉价无需复用；池引入排队与上限**反而废掉虚拟线程的价值**。限并发用 Semaphore（显式语义），不用池大小。
 *   - **ThreadLocal 的内存账**：百万虚拟线程 × 每个 ThreadLocal 副本（如大对象缓存）= 内存放大——**审计**每个 ThreadLocal 存储的对象；方案：显式传参 / ScopedValue（preview）/ 限制规模。trace 方案在虚拟线程下 ThreadLocal 可用但要算账（42-02 的 MDC 在虚拟线程的对应改造）。
 *   - **CPU 任务的干扰**：虚拟线程跑长计算霸占载体（无法卸载）——**CPU 活留给固定平台线程池**，虚拟线程只做 IO 编排；混合负载用隔离执行器（47-08 弹性的线程维度）。
 *   - **兼容性排查**：JDBC 阻塞驱动天然兼容（阻塞即卸载）✓；老版本连接池/驱动内部 synchronized+IO 组合要排查；升级 JDK 24 可大幅缓解 synchronized 问题（升级评估项进 41 章 ADR）。
 *   - 易错点：synchronized 包住 HTTP 调用做“防重复”（pinning 灾难）；全局替换 synchronized（应热点优先）；ThreadLocal 缓存大对象（百万副本放大）；开启开关就宣布完成（pinning 与连接池两关都要过）。
 * ============================================================
 */

package com.heima.ch50;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;

// ==================== 示例代码 ====================

public class VtPitfalls {
    static final ReentrantLock lock = new ReentrantLock();
    static int counter = 0;

    static void pinningAwareness() throws Exception {
        System.out.println("【pinning vs 可卸载锁：同一场景两种实现】");
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            System.out.println("  反模式：synchronized(lockObj){ blockingHttpCall(); } -> 载体被钉住");
            System.out.println("  正解：ReentrantLock.lock(){ blockingHttpCall(); } -> 阻塞时卸载");
            for (int i = 0; i < 8; i++) {
                pool.submit(() -> {
                    lock.lock();
                    try {
                        counter++;                    // 短临界区（无 IO）
                    } finally {
                        lock.unlock();
                    }
                    return null;
                });
            }
        }
        System.out.printf("  ReentrantLock 版计数完成：%d（阻塞可卸载，吞吐正常）%n%n", counter);
    }

    static void pinningDetection() {
        System.out.println("【pinning 检测：JFR 事件法】");
        List<String> steps = List.of(
                "1) 启动加 -XX:StartFlightRecording=filename=rec.jfr",
                "2) 事件 jdk.VirtualThreadPinned 记录“钉住”时长与栈",
                "3) 筛 duration > 10ms，按栈聚合 Top 方法",
                "4) Top 命中 synchronized -> 改 ReentrantLock（热点优先）",
                "5) 回归压测：吞吐与 P99 恢复即修复确认");
        for (String s : steps) {
            System.out.println("  " + s);
        }
        System.out.println();
    }

    static void threadLocalAudit() {
        System.out.println("【ThreadLocal 内存审计】");
        List<String> audit = List.of(
                "盘点：grep ThreadLocal，列出每个存储对象的大小",
                "危险：虚拟线程 × 1KB 副本 × 100 万 = 1GB 隐性放大",
                "方案A：显式传参（traceId 走方法参数）",
                "方案B：ScopedValue（preview）作用域绑定",
                "方案C：保持 ThreadLocal 但限制虚拟线程量（与目标矛盾）");
        for (String a : audit) {
            System.out.println("  " + a);
        }
        System.out.println();
    }

    static void poolMisuse() {
        System.out.println("【池化反模式与正确姿势】");
        String[][] rows = {
                {"反模式", "newFixedThreadPool(200, virtualFactory)", "池化廉价线程=自废武功"},
                {"反模式", "用池大小限并发", "改用 Semaphore（显式语义）"},
                {"正确", "newVirtualThreadPerTaskExecutor", "每任务一线程，随建随弃"},
                {"正确", "限下游并发用 Semaphore；限 CPU 用平台池", "并发控制点分离"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-4s %-40s %s%n", r[0], r[1], r[2]);
        }
    }

    public static void main(String[] args) throws Exception {
        pinningAwareness();
        pinningDetection();
        threadLocalAudit();
        poolMisuse();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 写一个 pinning 复现器：10 个虚拟线程在 synchronized 内 sleep，观察吞吐坍塌；改 ReentrantLock 对比。
 * 2. 用 JFR 采集模拟程序 1 分钟，统计 pinning Top3 栈，给出修复方案。
 * 3. 思考题：为什么 JDK 24 修复了 synchronized 的 pinning，而 21 时代要靠改代码？从监视器实现与载体卸载点的技术约束分析。
 * ============================================================
 */
