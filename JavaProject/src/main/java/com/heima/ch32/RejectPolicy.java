/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：32-02
 * 课名：四种拒绝策略与线程池监控
 * ------------------------------------------------------------
 * 知识点：
 *   - 四种内置策略：**AbortPolicy**（默认，抛 `RejectedExecutionException`）、**CallerRunsPolicy**（调用线程自己跑，形成天然背压）、**DiscardPolicy**（静默丢弃，最危险）、**DiscardOldestPolicy**（丢队列最老的，再重试提交）。
 *   - `CallerRunsPolicy` 是唯一自带背压的：队列满了以后，提交任务的那个线程（可能是 Tomcat 的 IO 线程）被迫执行任务，从而拖慢上游，让流量自然降下来。代价是可能阻塞 Web 容器线程。
 *   - `DiscardPolicy` 静默丢弃且**不抛异常、不打日志**，线上几乎不能用；如果一定要丢，至少自定义策略里打日志 + 计数。
 *   - 自定义拒绝策略的常见做法：落库/落盘待重试、写本地消息表、降级返回兜底值、打点上报后抛出业务异常。
 *   - 监控指标：**活跃线程数**（`getActiveCount`）、**池大小**（`getPoolSize`）、**队列长度**（`getQueue().size()`）、**已完成任务数**（`getCompletedTaskCount`）、**最大池大小**（`getLargestPoolSize`）。
 *   - 关键告警：队列长度持续增长（消费速度跟不上）、活跃线程数长期等于 max（容量不足）、拒绝次数 > 0（已经在丢任务）。
 *   - `ThreadPoolExecutor` 提供 `beforeExecute` / `afterExecute` / `terminated` 三个钩子，可在这里做耗时统计与异常捕获（`afterExecute` 拿不到 `submit` 的异常，需要包 `FutureTask`）。
 *   - 动态调参：`setCorePoolSize` / `setMaximumPoolSize` 可以在运行期改（注意 `core > max` 会抛异常），配合配置中心可实现"不重启扩容"。
 *   - 易错：拒绝策略里做重活（同步写库）导致提交线程被拖死；只监控队列长度不看拒绝数；多个业务共用一个池，一个慢业务把池占满拖垮全部。
 * ============================================================
 */

package com.heima.ch32;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

// ==================== 示例代码 ====================

public class RejectPolicy {
    static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** 自定义策略：先计数，再降级返回，不静默丢弃 */
    static class CountingReject implements RejectedExecutionHandler {
        final AtomicInteger rejected = new AtomicInteger();
        final AtomicInteger fallback = new AtomicInteger();

        public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
            rejected.incrementAndGet();
            fallback.incrementAndGet();
            System.out.printf("    [自定义拒绝] 累计拒绝 %d 次，已降级为同步兜底处理%n",
                    rejected.get());
        }
    }

    /** 可观测的池：钩子统计耗时 */
    static class MonitoredPool extends ThreadPoolExecutor {
        final AtomicInteger completed = new AtomicInteger();
        long totalNanos = 0;

        MonitoredPool(int core, int max, int queue, RejectedExecutionHandler h) {
            super(core, max, 100, TimeUnit.MILLISECONDS,
                    new ArrayBlockingQueue<>(queue), h);
        }

        @Override
        protected void beforeExecute(Thread t, Runnable r) {
            super.beforeExecute(t, r);
        }

        @Override
        protected void afterExecute(Runnable r, Throwable t) {
            super.afterExecute(r, t);
            completed.incrementAndGet();
            if (t != null) {
                System.out.println("    [afterExecute] 捕获异常: " + t);
            }
        }

        void dump(String tag) {
            System.out.printf("    [%s] 池=%d 活跃=%d 队列=%d 已完成=%d 峰值=%d%n",
                    tag, getPoolSize(), getActiveCount(), getQueue().size(),
                    getCompletedTaskCount(), getLargestPoolSize());
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("=== 1. AbortPolicy：直接抛异常 ===");
        ThreadPoolExecutor abort = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(1), new ThreadPoolExecutor.AbortPolicy());
        abort.execute(() -> sleep(400));
        abort.execute(() -> sleep(400));
        try {
            abort.execute(() -> System.out.println("    不该出现"));
        } catch (Exception e) {
            System.out.println("    第 3 个任务被拒：" + e.getClass().getSimpleName());
        }
        abort.shutdownNow();

        System.out.println();
        System.out.println("=== 2. CallerRunsPolicy：调用线程自己跑（背压） ===");
        ThreadPoolExecutor callerRuns = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(1), new ThreadPoolExecutor.CallerRunsPolicy());
        long t0 = System.nanoTime();
        for (int i = 1; i <= 3; i++) {
            final int id = i;
            callerRuns.execute(() -> {
                System.out.printf("    任务%d 执行线程=%s%n", id, Thread.currentThread().getName());
                sleep(150);
            });
        }
        callerRuns.shutdown();
        callerRuns.awaitTermination(3, TimeUnit.SECONDS);
        System.out.printf("  main 线程被拖住 %.0f ms（这就是背压：上游变慢）%n",
                (System.nanoTime() - t0) / 1e6);

        System.out.println();
        System.out.println("=== 3. DiscardPolicy：静默丢弃（危险） ===");
        AtomicInteger executed = new AtomicInteger();
        ThreadPoolExecutor discard = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(1), new ThreadPoolExecutor.DiscardPolicy());
        for (int i = 0; i < 5; i++) {
            discard.execute(() -> {
                executed.incrementAndGet();
                sleep(120);
            });
        }
        sleep(700);
        System.out.println("  提交 5 个，实际执行 " + executed.get() + " 个，无异常、无日志");
        System.out.println("  结论：线上禁止裸用 DiscardPolicy");
        discard.shutdownNow();

        System.out.println();
        System.out.println("=== 4. DiscardOldestPolicy：丢最老的再试 ===");
        AtomicInteger ran = new AtomicInteger();
        ThreadPoolExecutor oldest = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(1), new ThreadPoolExecutor.DiscardOldestPolicy());
        for (int i = 1; i <= 4; i++) {
            final int id = i;
            oldest.execute(() -> {
                ran.incrementAndGet();
                System.out.println("    实际执行 任务" + id);
                sleep(150);
            });
        }
        oldest.shutdown();
        oldest.awaitTermination(3, TimeUnit.SECONDS);
        System.out.println("  提交 4 个，执行 " + ran.get() + " 个（中间的被挤掉了）");

        System.out.println();
        System.out.println("=== 5. 自定义策略 + 监控 ===");
        CountingReject counter = new CountingReject();
        MonitoredPool pool = new MonitoredPool(1, 1, 1, counter);
        for (int i = 1; i <= 6; i++) {
            final int id = i;
            pool.execute(() -> {
                sleep(120);
                if (id == 2) {
                    throw new IllegalStateException("模拟业务异常");
                }
            });
        }
        pool.dump("提交后立刻");
        sleep(400);
        pool.dump("运行中");
        sleep(600);
        pool.dump("接近结束");
        pool.shutdown();
        pool.awaitTermination(3, TimeUnit.SECONDS);
        System.out.println("  拒绝次数 = " + counter.rejected.get() + "，已完成 = " + pool.completed.get());

        System.out.println();
        System.out.println("=== 6. 监控指标与告警 ===");
        String[][] metrics = {
                {"getQueue().size()", "持续增长 = 消费跟不上", "> 1000 且 5 分钟不降"},
                {"getActiveCount()", "长期等于 max = 容量不足", ">= max 持续 10 分钟"},
                {"拒绝次数", "已经在丢任务", "> 0 立即告警"},
                {"getCompletedTaskCount()", "吞吐基线", "环比骤降 50%"},
                {"任务耗时 P99", "单任务变慢", "超过 SLA"}
        };
        for (String[] m : metrics) {
            System.out.printf("  %-24s %-26s 阈值：%s%n", m[0], m[1], m[2]);
        }
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 用 `CallerRunsPolicy` 做一个"提交 100 个任务"的实验，统计 main 线程被执行了几次，说明背压如何传导到上游。
 * 2. 写一个自定义拒绝策略，把被拒任务写入本地文件（`Files.writeString` 追加），并说明为什么不能在里面同步写数据库。
 * 3. 用 `afterExecute` 统计任务耗时，验证 `submit` 抛出的异常能否被它捕获，若不能请说明原因。
 * ============================================================
 */
