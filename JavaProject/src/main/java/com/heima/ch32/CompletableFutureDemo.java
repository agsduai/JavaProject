/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：32-03
 * 课名：CompletableFuture 异步编排
 * ------------------------------------------------------------
 * 知识点：
 *   - `CompletableFuture` 解决"多个异步任务如何组合"：串行（`thenApply`/`thenCompose`）、并行（`thenCombine`/`allOf`/`anyOf`）、异常（`exceptionally`/`handle`/`whenComplete`）。
 *   - `thenApply` vs `thenCompose`：前者做**同步转换**（`T -> U`），后者做**返回另一个 Future 的转换**（`T -> CompletionStage<U>`），用错会得到 `CompletableFuture<CompletableFuture<U>>`。
 *   - `thenApply` vs `thenApplyAsync`：不带 `Async` 的在**上一个任务完成的线程**上执行（可能是主线程），带 `Async` 的交给线程池。涉及阻塞操作时必须用 `Async` 并显式传线程池，否则会占住不该占的线程。
 *   - `allOf` 等全部完成（返回 `CompletableFuture<Void>`，要自己收集结果），`anyOf` 等任意一个完成（返回 `Object`，要强转）。
 *   - 默认线程池是 `ForkJoinPool.commonPool()`，**并行度 = 核数 - 1**，且是全局共享的。IO 密集任务必须传自定义池，否则会打满公共池影响其他代码。
 *   - 超时控制：Java 9+ 的 `orTimeout`（抛 `TimeoutException`）与 `completeOnTimeout`（给默认值）。这是防止"一个慢依赖拖死整条链路"的关键。
 *   - 异常处理三条路：`exceptionally`（只处理异常，返回兜底值）、`handle`（无论成败都执行，能拿到异常）、`whenComplete`（副作用，不改变结果，异常会继续往下传）。
 *   - 常见坑：`get()` 不设超时（无限等待）；`join()` 抛的是 `CompletionException`（要 `getCause()`）；忘记 `allOf` 后处理异常（一个失败其余仍跑，但 `allOf` 整体失败）。
 *   - 易错：在 `thenApply` 里做 RPC（占住公共池线程）；`allOf` 后直接 `join` 不看异常；线程池没 `shutdown` 导致 JVM 不退出。
 * ============================================================
 */

package com.heima.ch32;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

// ==================== 示例代码 ====================

public class CompletableFutureDemo {
    static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** 模拟远程调用：故意有延迟 */
    static String call(String name, long ms, boolean fail) {
        sleep(ms);
        if (fail) {
            throw new IllegalStateException(name + " 服务异常");
        }
        return name + "(" + ms + "ms)";
    }

    public static void main(String[] args) throws Exception {
        ExecutorService pool = new ThreadPoolExecutor(8, 8, 0, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(64),
                r -> {
                    Thread t = new Thread(r, "biz-" + System.nanoTime() % 1000);
                    t.setDaemon(true);
                    return t;
                });

        System.out.println("=== 1. 串行：thenApply / thenCompose ===");
        String serial = CompletableFuture
                .supplyAsync(() -> call("用户服务", 100, false), pool)
                .thenApply(u -> {
                    System.out.println("    thenApply 执行线程 = " + Thread.currentThread().getName());
                    return u + " -> 订单服务";
                })
                .thenApply(o -> o + " -> 支付服务")
                .join();
        System.out.println("  结果 = " + serial);
        System.out.println("  耗时约 100ms（串行只有一次真实延迟）");

        System.out.println();
        System.out.println("=== 2. 并行：thenCombine 两路合并 ===");
        long t1 = System.nanoTime();
        CompletableFuture<String> user = CompletableFuture.supplyAsync(
                () -> call("用户", 300, false), pool);
        CompletableFuture<String> coupon = CompletableFuture.supplyAsync(
                () -> call("优惠", 300, false), pool);
        String combined = user.thenCombine(coupon, (u, c) -> u + " + " + c).join();
        System.out.printf("  结果 = %s，耗时 %.0f ms（并行，约等于最慢的一路）%n",
                combined, (System.nanoTime() - t1) / 1e6);

        System.out.println();
        System.out.println("=== 3. allOf：等全部完成并收集结果 ===");
        long t2 = System.nanoTime();
        List<CompletableFuture<String>> futures = new ArrayList<>();
        String[] names = {"库存", "价格", "评价", "推荐"};
        long[] delays = {200, 150, 350, 100};
        for (int i = 0; i < names.length; i++) {
            final String n = names[i];
            final long d = delays[i];
            futures.add(CompletableFuture.supplyAsync(() -> call(n, d, false), pool));
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture<?>[0])).join();
        List<String> results = new ArrayList<>();
        for (CompletableFuture<String> f : futures) {
            results.add(f.join());
        }
        System.out.printf("  结果 = %s%n  耗时 %.0f ms（约等于最慢的 350ms）%n",
                results, (System.nanoTime() - t2) / 1e6);

        System.out.println();
        System.out.println("=== 4. anyOf：谁先回用谁 ===");
        CompletableFuture<String> fast = CompletableFuture.supplyAsync(
                () -> call("缓存A", 100, false), pool);
        CompletableFuture<String> slow = CompletableFuture.supplyAsync(
                () -> call("缓存B", 500, false), pool);
        Object first = CompletableFuture.anyOf(fast, slow).join();
        System.out.println("  先返回 = " + first);

        System.out.println();
        System.out.println("=== 5. 异常处理三条路 ===");
        String withDefault = CompletableFuture
                .supplyAsync(() -> call("风控", 50, true), pool)
                .exceptionally(e -> {
                    System.out.println("    exceptionally 收到: " + e.getCause().getMessage());
                    return "风控降级：默认通过";
                })
                .join();
        System.out.println("  exceptionally 结果 = " + withDefault);

        String handled = CompletableFuture
                .supplyAsync(() -> call("账务", 50, true), pool)
                .handle((r, e) -> e == null ? r : "handle 兜底")
                .join();
        System.out.println("  handle 结果 = " + handled);

        try {
            CompletableFuture
                    .supplyAsync(() -> call("物流", 50, true), pool)
                    .whenComplete((r, e) -> System.out.println(
                            "    whenComplete 观察到异常: " + (e == null ? "无" : e.getMessage())))
                    .join();
        } catch (CompletionException e) {
            System.out.println("  whenComplete 不吞异常，最终抛出: " + e.getCause().getMessage());
        }

        System.out.println();
        System.out.println("=== 6. 超时控制 ===");
        try {
            CompletableFuture.supplyAsync(() -> call("慢依赖", 1500, false), pool)
                    .orTimeout(300, TimeUnit.MILLISECONDS)
                    .join();
        } catch (CompletionException e) {
            System.out.println("  orTimeout 触发: " + e.getCause().getClass().getSimpleName());
        }
        String fallback = CompletableFuture
                .supplyAsync(() -> call("慢依赖", 1500, false), pool)
                .completeOnTimeout("超时兜底值", 300, TimeUnit.MILLISECONDS)
                .join();
        System.out.println("  completeOnTimeout 结果 = " + fallback);

        System.out.println();
        System.out.println("=== 7. 默认池并行度 ===");
        System.out.println("  核数 = " + Runtime.getRuntime().availableProcessors());
        System.out.println("  commonPool 并行度 = " + java.util.concurrent.ForkJoinPool.commonPool().getParallelism());
        System.out.println("  结论：IO 任务不要用默认池，必须传自己的线程池");

        pool.shutdownNow();
        pool.awaitTermination(2, TimeUnit.SECONDS);
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 用 `thenCompose` 改写"查用户 → 用用户 ID 查订单"的串行链，说明它与 `thenApply` 的返回类型差异。
 * 2. 实现"4 个服务并行，任一失败则整体降级"的逻辑，对比 `allOf + exceptionally` 与逐个 `handle` 的写法。
 * 3. 把 `orTimeout` 的 300ms 改成 3000ms，观察程序等待时间的变化，说明为什么超时值必须小于上游调用的超时预算。
 * ============================================================
 */
