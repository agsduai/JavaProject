/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：32-01
 * 课名：线程池核心参数与执行流程
 * ------------------------------------------------------------
 * 知识点：
 *   - 七个核心参数：`corePoolSize`（核心线程数）、`maximumPoolSize`（最大线程数）、`keepAliveTime` + `unit`（非核心线程空闲存活时间）、`workQueue`（任务队列）、`threadFactory`（线程工厂，用于命名与异常处理）、`handler`（拒绝策略）。
 *   - 执行流程是**严格按顺序的四步**：① 核心线程未满 → 建核心线程；② 核心满了 → 入队列；③ 队列满了 → 建非核心线程直到 `max`；④ 仍满 → 拒绝策略。注意"队列优先于扩容"，这解释了为什么用无界队列时 `maximumPoolSize` 形同虚设。
 *   - 线程数估算：**CPU 密集** → `N + 1`；**IO 密集** → `N × (1 + 等待时间/计算时间)`，粗略可取 `2N`。N 是核数。这只是起点，必须压测校准。
 *   - 队列选择决定行为：`ArrayBlockingQueue`（有界，推荐）、`LinkedBlockingQueue`（默认 `Integer.MAX_VALUE`，等于无界）、`SynchronousQueue`（不存任务，直接交给线程）、`PriorityBlockingQueue`（带优先级）。
 *   - 线程池状态：`RUNNING → SHUTDOWN`（`shutdown()`，不收新任务但做完队列）→ `STOP`（`shutdownNow()`，中断正在执行的）→ `TIDYING → TERMINATED`。
 *   - `submit` 与 `execute` 的区别：`submit` 返回 `Future`，**异常被吞进 Future**，不调用 `get()` 就永远看不到；`execute` 直接抛到线程的 `UncaughtExceptionHandler`。
 *   - 线程池不要用 `Executors` 快捷方法：`newFixedThreadPool`/`newSingleThreadExecutor` 用无界队列会 OOM，`newCachedThreadPool` 的 `max` 是 `Integer.MAX_VALUE` 会创建过多线程。阿里规约明确要求手动 `new ThreadPoolExecutor`。
 *   - 易错：以为"设了 max 就会开到 max"（队列没满就不会扩）；忘记 `shutdown()` 导致 JVM 不退出；核心线程不预热（第一次请求才创建，有毛刺）。
 * ============================================================
 */

package com.heima.ch32;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

// ==================== 示例代码 ====================

public class ThreadPoolBasics {
    /** 可观测的线程工厂：命名 + 计数 + 异常打印 */
    static class NamedFactory implements ThreadFactory {
        private final String prefix;
        private final AtomicInteger seq = new AtomicInteger(1);

        NamedFactory(String prefix) {
            this.prefix = prefix;
        }

        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, prefix + "-" + seq.getAndIncrement());
            t.setUncaughtExceptionHandler((th, e) ->
                    System.out.println("    [未捕获] " + th.getName() + " -> " + e));
            return t;
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("=== 1. 参数与执行顺序 ===");
        System.out.println("  ① 核心未满 -> 建核心线程");
        System.out.println("  ② 核心已满 -> 入队列");
        System.out.println("  ③ 队列已满 -> 建非核心线程直到 max");
        System.out.println("  ④ 仍然满 -> 拒绝策略");

        AtomicInteger running = new AtomicInteger();
        AtomicInteger peak = new AtomicInteger();
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                2, 4, 200, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(2),
                new NamedFactory("order"),
                new ThreadPoolExecutor.AbortPolicy());
        pool.allowCoreThreadTimeOut(false);

        CountDownLatch done = new CountDownLatch(6);
        for (int i = 1; i <= 6; i++) {
            final int id = i;
            try {
                pool.execute(() -> {
                    int now = running.incrementAndGet();
                    peak.accumulateAndGet(now, Math::max);
                    System.out.printf("    任务%d 由 %s 执行（当前并发=%d, 池大小=%d, 队列=%d）%n",
                            id, Thread.currentThread().getName(), now,
                            pool.getPoolSize(), pool.getQueue().size());
                    sleep(300);
                    running.decrementAndGet();
                    done.countDown();
                });
            } catch (Exception e) {
                System.out.println("    任务" + id + " 被拒绝：" + e.getClass().getSimpleName());
                done.countDown();
            }
        }
        done.await(10, TimeUnit.SECONDS);
        System.out.println("  峰值并发 = " + peak.get() + "（max=4，核心=2，队列=2）");

        System.out.println();
        System.out.println("=== 2. 无界队列让 max 失效 ===");
        ThreadPoolExecutor unbounded = new ThreadPoolExecutor(
                1, 10, 0, TimeUnit.SECONDS,
                new java.util.concurrent.LinkedBlockingQueue<>(),
                new NamedFactory("unbounded"));
        for (int i = 0; i < 50; i++) {
            unbounded.execute(() -> sleep(20));
        }
        Thread.sleep(50);
        System.out.println("  池大小 = " + unbounded.getPoolSize() + "（max=10，但队列没满，永远不会扩到 10）");
        System.out.println("  队列长度 = " + unbounded.getQueue().size() + "（无界队列的风险：OOM 而不是拒绝）");
        unbounded.shutdownNow();

        System.out.println();
        System.out.println("=== 3. submit 吞异常 vs execute 抛出 ===");
        ThreadPoolExecutor p2 = new ThreadPoolExecutor(
                1, 1, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(10),
                new NamedFactory("err"));
        p2.execute(() -> {
            throw new IllegalStateException("execute 的异常会打到 UncaughtExceptionHandler");
        });
        p2.submit(() -> {
            throw new IllegalStateException("submit 的异常被包进 Future");
        });
        sleep(200);
        System.out.println("  （上面只看到一条 [未捕获]，submit 的异常被静默吞掉了）");
        p2.shutdown();

        System.out.println();
        System.out.println("=== 4. 线程数估算参考 ===");
        int n = Runtime.getRuntime().availableProcessors();
        System.out.println("  本机核数 N = " + n);
        System.out.println("  CPU 密集（如加解密、压缩）：约 " + (n + 1));
        System.out.println("  IO 密集（如 RPC、DB）：约 " + (2 * n) + "，或按 N*(1+等待/计算) 精算");
        System.out.println("  结论：公式只给起点，必须用压测看 P99 与队列堆积");

        pool.shutdown();
        pool.awaitTermination(5, TimeUnit.SECONDS);
        System.out.println();
        System.out.println("=== 5. 关闭 ===");
        System.out.println("  shutdown()    不再收新任务，队列里的继续跑完");
        System.out.println("  shutdownNow() 中断正在跑的，返回没跑的任务列表");
        System.out.println("  最终状态 = " + pool.getQueue().size() + " 个残留任务, isShutdown=" + pool.isShutdown());
    }

    static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    static void showQueue(BlockingQueue<?> q) {
        System.out.println("    队列类型 = " + q.getClass().getSimpleName());
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 把 `ArrayBlockingQueue(2)` 改成 `LinkedBlockingQueue()`，重新跑"任务 1~6"，说明峰值并发和拒绝次数如何变化。
 * 2. 用 `N*(1+W/C)` 估算一个"每次请求等 DB 100ms、CPU 计算 10ms、目标 500 QPS"的线程数，并说明这个估算忽略了什么。
 * 3. 给线程池加 `allowCoreThreadTimeOut(true)`，观察空闲后池大小是否归零，说明适用场景与风险。
 * ============================================================
 */
