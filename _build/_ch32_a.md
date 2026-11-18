### 32-01 线程池核心参数与执行流程

**知识点**
- 七个核心参数：`corePoolSize`（核心线程数）、`maximumPoolSize`（最大线程数）、`keepAliveTime` + `unit`（非核心线程空闲存活时间）、`workQueue`（任务队列）、`threadFactory`（线程工厂，用于命名与异常处理）、`handler`（拒绝策略）。
- 执行流程是**严格按顺序的四步**：① 核心线程未满 → 建核心线程；② 核心满了 → 入队列；③ 队列满了 → 建非核心线程直到 `max`；④ 仍满 → 拒绝策略。注意"队列优先于扩容"，这解释了为什么用无界队列时 `maximumPoolSize` 形同虚设。
- 线程数估算：**CPU 密集** → `N + 1`；**IO 密集** → `N × (1 + 等待时间/计算时间)`，粗略可取 `2N`。N 是核数。这只是起点，必须压测校准。
- 队列选择决定行为：`ArrayBlockingQueue`（有界，推荐）、`LinkedBlockingQueue`（默认 `Integer.MAX_VALUE`，等于无界）、`SynchronousQueue`（不存任务，直接交给线程）、`PriorityBlockingQueue`（带优先级）。
- 线程池状态：`RUNNING → SHUTDOWN`（`shutdown()`，不收新任务但做完队列）→ `STOP`（`shutdownNow()`，中断正在执行的）→ `TIDYING → TERMINATED`。
- `submit` 与 `execute` 的区别：`submit` 返回 `Future`，**异常被吞进 Future**，不调用 `get()` 就永远看不到；`execute` 直接抛到线程的 `UncaughtExceptionHandler`。
- 线程池不要用 `Executors` 快捷方法：`newFixedThreadPool`/`newSingleThreadExecutor` 用无界队列会 OOM，`newCachedThreadPool` 的 `max` 是 `Integer.MAX_VALUE` 会创建过多线程。阿里规约明确要求手动 `new ThreadPoolExecutor`。
- 易错：以为"设了 max 就会开到 max"（队列没满就不会扩）；忘记 `shutdown()` 导致 JVM 不退出；核心线程不预热（第一次请求才创建，有毛刺）。

**示例代码**
```java
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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
```

**课后练习**
1. 把 `ArrayBlockingQueue(2)` 改成 `LinkedBlockingQueue()`，重新跑"任务 1~6"，说明峰值并发和拒绝次数如何变化。
2. 用 `N*(1+W/C)` 估算一个"每次请求等 DB 100ms、CPU 计算 10ms、目标 500 QPS"的线程数，并说明这个估算忽略了什么。
3. 给线程池加 `allowCoreThreadTimeOut(true)`，观察空闲后池大小是否归零，说明适用场景与风险。

### 32-02 四种拒绝策略与线程池监控

**知识点**
- 四种内置策略：**AbortPolicy**（默认，抛 `RejectedExecutionException`）、**CallerRunsPolicy**（调用线程自己跑，形成天然背压）、**DiscardPolicy**（静默丢弃，最危险）、**DiscardOldestPolicy**（丢队列最老的，再重试提交）。
- `CallerRunsPolicy` 是唯一自带背压的：队列满了以后，提交任务的那个线程（可能是 Tomcat 的 IO 线程）被迫执行任务，从而拖慢上游，让流量自然降下来。代价是可能阻塞 Web 容器线程。
- `DiscardPolicy` 静默丢弃且**不抛异常、不打日志**，线上几乎不能用；如果一定要丢，至少自定义策略里打日志 + 计数。
- 自定义拒绝策略的常见做法：落库/落盘待重试、写本地消息表、降级返回兜底值、打点上报后抛出业务异常。
- 监控指标：**活跃线程数**（`getActiveCount`）、**池大小**（`getPoolSize`）、**队列长度**（`getQueue().size()`）、**已完成任务数**（`getCompletedTaskCount`）、**最大池大小**（`getLargestPoolSize`）。
- 关键告警：队列长度持续增长（消费速度跟不上）、活跃线程数长期等于 max（容量不足）、拒绝次数 > 0（已经在丢任务）。
- `ThreadPoolExecutor` 提供 `beforeExecute` / `afterExecute` / `terminated` 三个钩子，可在这里做耗时统计与异常捕获（`afterExecute` 拿不到 `submit` 的异常，需要包 `FutureTask`）。
- 动态调参：`setCorePoolSize` / `setMaximumPoolSize` 可以在运行期改（注意 `core > max` 会抛异常），配合配置中心可实现"不重启扩容"。
- 易错：拒绝策略里做重活（同步写库）导致提交线程被拖死；只监控队列长度不看拒绝数；多个业务共用一个池，一个慢业务把池占满拖垮全部。

**示例代码**
```java
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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
```

**课后练习**
1. 用 `CallerRunsPolicy` 做一个"提交 100 个任务"的实验，统计 main 线程被执行了几次，说明背压如何传导到上游。
2. 写一个自定义拒绝策略，把被拒任务写入本地文件（`Files.writeString` 追加），并说明为什么不能在里面同步写数据库。
3. 用 `afterExecute` 统计任务耗时，验证 `submit` 抛出的异常能否被它捕获，若不能请说明原因。

### 32-03 CompletableFuture 异步编排

**知识点**
- `CompletableFuture` 解决"多个异步任务如何组合"：串行（`thenApply`/`thenCompose`）、并行（`thenCombine`/`allOf`/`anyOf`）、异常（`exceptionally`/`handle`/`whenComplete`）。
- `thenApply` vs `thenCompose`：前者做**同步转换**（`T -> U`），后者做**返回另一个 Future 的转换**（`T -> CompletionStage<U>`），用错会得到 `CompletableFuture<CompletableFuture<U>>`。
- `thenApply` vs `thenApplyAsync`：不带 `Async` 的在**上一个任务完成的线程**上执行（可能是主线程），带 `Async` 的交给线程池。涉及阻塞操作时必须用 `Async` 并显式传线程池，否则会占住不该占的线程。
- `allOf` 等全部完成（返回 `CompletableFuture<Void>`，要自己收集结果），`anyOf` 等任意一个完成（返回 `Object`，要强转）。
- 默认线程池是 `ForkJoinPool.commonPool()`，**并行度 = 核数 - 1**，且是全局共享的。IO 密集任务必须传自定义池，否则会打满公共池影响其他代码。
- 超时控制：Java 9+ 的 `orTimeout`（抛 `TimeoutException`）与 `completeOnTimeout`（给默认值）。这是防止"一个慢依赖拖死整条链路"的关键。
- 异常处理三条路：`exceptionally`（只处理异常，返回兜底值）、`handle`（无论成败都执行，能拿到异常）、`whenComplete`（副作用，不改变结果，异常会继续往下传）。
- 常见坑：`get()` 不设超时（无限等待）；`join()` 抛的是 `CompletionException`（要 `getCause()`）；忘记 `allOf` 后处理异常（一个失败其余仍跑，但 `allOf` 整体失败）。
- 易错：在 `thenApply` 里做 RPC（占住公共池线程）；`allOf` 后直接 `join` 不看异常；线程池没 `shutdown` 导致 JVM 不退出。

**示例代码**
```java
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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
```

**课后练习**
1. 用 `thenCompose` 改写"查用户 → 用用户 ID 查订单"的串行链，说明它与 `thenApply` 的返回类型差异。
2. 实现"4 个服务并行，任一失败则整体降级"的逻辑，对比 `allOf + exceptionally` 与逐个 `handle` 的写法。
3. 把 `orTimeout` 的 300ms 改成 3000ms，观察程序等待时间的变化，说明为什么超时值必须小于上游调用的超时预算。

### 32-04 AQS 与 ReentrantLock

**知识点**
- **AQS（AbstractQueuedSynchronizer）** 是 JUC 锁的骨架：一个 `volatile int state` 表示同步状态 + 一个 **CLH 双向队列**存放等待线程 + `CAS` 修改 state。
- 两种模式：**独占**（`tryAcquire`/`tryRelease`，如 `ReentrantLock`）与**共享**（`tryAcquireShared`/`tryReleaseShared`，如 `Semaphore`、`CountDownLatch`）。
- 加锁失败流程：`CAS` 改 state 失败 → 包装成 Node 入队 → `LockSupport.park()` 挂起 → 前驱释放时 `unpark()` 唤醒。唤醒后还要重新竞争，不是"排队就一定能拿到"。
- `ReentrantLock` 的可重入靠 state 计数：同一线程再次 `lock()` 时 state 递增，`unlock()` 递减，减到 0 才真正释放。
- **公平锁 vs 非公平锁**：公平锁在 `tryAcquire` 里先 `hasQueuedPredecessors()`，有前驱就排队；非公平锁直接抢，**吞吐更高但可能饿死**。默认非公平。
- `lockInterruptibly()` 允许在等待时被中断（`InterruptedException`），`lock()` 不允许；`tryLock(timeout)` 可设等待上限，是避免死锁的常用手段。
- `ReentrantLock` vs `synchronized`：前者可中断、可超时、可公平、可多条件队列（`newCondition`），后者由 JVM 优化、代码更简洁。JDK 6 之后两者性能接近，优先 `synchronized`，需要高级特性才用 `Lock`。
- `Condition` 把"等待/通知"精确分组：`notFull.await()` / `notFull.signal()` 与 `notEmpty` 分开，避免 `notifyAll` 的无效唤醒。这就是 `ArrayBlockingQueue` 的实现方式。
- 死锁四条件：互斥、持有并等待、不可剥夺、循环等待。破坏任一即可，工程上最常用"统一加锁顺序"。
- 易错：`lock()` 后忘记在 `finally` 里 `unlock()`；`unlock()` 次数多于 `lock()`（抛 `IllegalMonitorStateException`）；用 `tryLock()` 但没判返回值。

**示例代码**
```java
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class AqsReentrantLock {
    static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** 用 ReentrantLock + 两个 Condition 实现有界缓冲区 */
    static class BoundedBuffer<T> {
        private final Object[] items;
        private int putIdx, takeIdx, count;
        private final ReentrantLock lock = new ReentrantLock();
        private final Condition notFull = lock.newCondition();
        private final Condition notEmpty = lock.newCondition();

        BoundedBuffer(int cap) {
            items = new Object[cap];
        }

        void put(T x) throws InterruptedException {
            lock.lockInterruptibly();
            try {
                while (count == items.length) {
                    notFull.await();
                }
                items[putIdx] = x;
                if (++putIdx == items.length) {
                    putIdx = 0;
                }
                count++;
                notEmpty.signal();
            } finally {
                lock.unlock();
            }
        }

        @SuppressWarnings("unchecked")
        T take() throws InterruptedException {
            lock.lockInterruptibly();
            try {
                while (count == 0) {
                    notEmpty.await();
                }
                T x = (T) items[takeIdx];
                items[takeIdx] = null;
                if (++takeIdx == items.length) {
                    takeIdx = 0;
                }
                count--;
                notFull.signal();
                return x;
            } finally {
                lock.unlock();
            }
        }

        int size() {
            lock.lock();
            try {
                return count;
            } finally {
                lock.unlock();
            }
        }
    }

    /** 可重入验证：同一线程递归加锁 */
    static class ReentrantDemo {
        private final ReentrantLock lock = new ReentrantLock();
        private int depth;

        void outer() {
            lock.lock();
            try {
                depth++;
                System.out.println("    outer 拿到锁，holdCount = " + lock.getHoldCount());
                inner();
            } finally {
                lock.unlock();
            }
        }

        void inner() {
            lock.lock();
            try {
                depth++;
                System.out.println("    inner 再次拿到锁，holdCount = " + lock.getHoldCount());
            } finally {
                lock.unlock();
            }
        }
    }

    /** 公平 vs 非公平：观察获取顺序 */
    static void fairnessTest(boolean fair) throws Exception {
        ReentrantLock lock = new ReentrantLock(fair);
        StringBuilder order = new StringBuilder();
        Thread[] ts = new Thread[5];
        for (int i = 0; i < 5; i++) {
            final int id = i;
            ts[i] = new Thread(() -> {
                lock.lock();
                try {
                    order.append(id).append(" ");
                    sleep(20);
                } finally {
                    lock.unlock();
                }
            }, "t" + i);
        }
        for (Thread t : ts) {
            t.start();
            sleep(5);
        }
        for (Thread t : ts) {
            t.join();
        }
        System.out.println("  fair=" + fair + " 获取顺序: " + order.toString().trim());
    }

    public static void main(String[] args) throws Exception {
        System.out.println("=== 1. 可重入：state 计数 ===");
        new ReentrantDemo().outer();

        System.out.println();
        System.out.println("=== 2. Condition：生产者-消费者精确唤醒 ===");
        BoundedBuffer<Integer> buf = new BoundedBuffer<>(2);
        AtomicInteger consumed = new AtomicInteger();
        Thread producer = new Thread(() -> {
            for (int i = 1; i <= 6; i++) {
                try {
                    buf.put(i);
                    System.out.println("    生产 " + i + "，当前 size=" + buf.size());
                    sleep(60);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }, "producer");
        Thread consumer = new Thread(() -> {
            while (consumed.get() < 6) {
                try {
                    Integer v = buf.take();
                    consumed.incrementAndGet();
                    System.out.println("    消费 " + v + "，当前 size=" + buf.size());
                    sleep(100);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }, "consumer");
        producer.start();
        consumer.start();
        producer.join(5000);
        consumer.join(5000);
        System.out.println("  最终消费数 = " + consumed.get());

        System.out.println();
        System.out.println("=== 3. tryLock 超时：避免无限等待 ===");
        ReentrantLock lock = new ReentrantLock();
        Thread holder = new Thread(() -> {
            lock.lock();
            try {
                sleep(600);
            } finally {
                lock.unlock();
            }
        }, "holder");
        holder.start();
        sleep(50);
        boolean got = lock.tryLock(200, TimeUnit.MILLISECONDS);
        System.out.println("  tryLock(200ms) 结果 = " + got + "（锁被 holder 占着）");
        if (got) {
            lock.unlock();
        }
        holder.join();

        System.out.println();
        System.out.println("=== 4. lockInterruptibly：等待时可被打断 ===");
        ReentrantLock lock2 = new ReentrantLock();
        Thread holder2 = new Thread(() -> {
            lock2.lock();
            try {
                sleep(800);
            } finally {
                lock2.unlock();
            }
        }, "holder2");
        holder2.start();
        sleep(50);
        Thread waiter = new Thread(() -> {
            try {
                lock2.lockInterruptibly();
                try {
                    System.out.println("    意外拿到锁");
                } finally {
                    lock2.unlock();
                }
            } catch (InterruptedException e) {
                System.out.println("    等待中被中断，放弃获取锁（这是 lock() 做不到的）");
            }
        }, "waiter");
        waiter.start();
        sleep(150);
        waiter.interrupt();
        waiter.join();
        holder2.join();

        System.out.println();
        System.out.println("=== 5. 公平 vs 非公平 ===");
        fairnessTest(true);
        fairnessTest(false);
        System.out.println("  公平锁严格按到达顺序，非公平锁允许插队（吞吐更高，但可能饿死）");

        System.out.println();
        System.out.println("=== 6. AQS 结构速记 ===");
        System.out.println("  state（volatile int）：锁被重入几次 / 信号量剩几个 / 倒计时剩几");
        System.out.println("  CLH 双向队列：抢不到锁的线程包装成 Node 入队，park 挂起");
        System.out.println("  唤醒：前驱释放时 unpark 后继，后继醒来重新 CAS 抢，不是直接给");
        System.out.println("  独占 tryAcquire/tryRelease   共享 tryAcquireShared/tryReleaseShared");
    }
}
```

**课后练习**
1. 给 `BoundedBuffer` 加 `offer(x, timeout)` 方法（队列满时等待指定时间后返回 `false`），用 `awaitNanos` 实现。
2. 构造一个"两个线程按相反顺序加两把锁"的死锁，用 `jstack` 或 `ThreadMXBean.findDeadlockedThreads()` 检测出来。
3. 说明为什么 `unlock()` 必须放在 `finally` 里，举一个不放导致锁泄漏的具体场景。

### 32-05 读写锁与 StampedLock

**知识点**
- `ReentrantReadWriteLock`：**读读不互斥**、**读写互斥**、**写写互斥**。适合"读多写少"（如缓存、配置）。
- 锁降级：持有写锁 → 获取读锁 → 释放写锁，可以保证"释放写锁后仍能读到自己的修改"。**锁升级（读→写）不支持**，会死锁。
- 读写锁的公平性问题：非公平模式下，读锁可能不断插队导致写锁**饥饿**。`new ReentrantReadWriteLock(true)` 可缓解，但吞吐下降。
- **StampedLock**（JDK 8+）：提供三种模式——**写锁**（`writeLock`）、**悲观读**（`readLock`）、**乐观读**（`tryOptimisticRead` + `validate`）。
- 乐观读不加锁，读完用 `validate(stamp)` 校验"读期间有没有写"。没写就直接用结果，有写则退化成悲观读重试。适合"读极多、写极少"。
- StampedLock 的坑：**不可重入**（同一线程重复 `writeLock` 会死锁）、**不支持 Condition**、`stamp` 用完必须配对释放。
- 选择建议：读多写少且能容忍读锁阻塞写 → 读写锁；读远多于写且读操作很短 → StampedLock 乐观读；写多 → 直接 `synchronized` 或 `ReentrantLock`。
- 读写锁的 `getReadLockCount()` / `getWriteHoldCount()` 可用于诊断"读锁泄漏"（读锁没释放会导致写锁永远拿不到）。
- 易错：在持有读锁时去获取写锁（死锁）；StampedLock 的乐观读里做了耗时操作（validate 失败率高，白做）；把 `stamp` 变量复用导致校验错乱。

**示例代码**
```java
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.StampedLock;

public class ReadWriteLockDemo {
    static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** 读写锁版缓存 */
    static class RwCache {
        private final Map<String, String> map = new HashMap<>();
        private final ReentrantReadWriteLock rw = new ReentrantReadWriteLock();
        final AtomicInteger hits = new AtomicInteger();
        final AtomicInteger misses = new AtomicInteger();

        String get(String k) {
            rw.readLock().lock();
            try {
                String v = map.get(k);
                if (v == null) {
                    misses.incrementAndGet();
                } else {
                    hits.incrementAndGet();
                }
                return v;
            } finally {
                rw.readLock().unlock();
            }
        }

        void put(String k, String v) {
            rw.writeLock().lock();
            try {
                map.put(k, v);
            } finally {
                rw.writeLock().unlock();
            }
        }

        /** 锁降级：写锁 -> 读锁 -> 释放写锁，保证读到自己刚写的数据 */
        String putAndRead(String k, String v) {
            rw.writeLock().lock();
            try {
                map.put(k, v);
                rw.readLock().lock();
            } finally {
                rw.writeLock().unlock();
            }
            try {
                return map.get(k);
            } finally {
                rw.readLock().unlock();
            }
        }

        void dump() {
            System.out.println("    读锁数=" + rw.getReadLockCount()
                    + " 写锁持有=" + rw.isWriteLocked()
                    + " 命中=" + hits.get() + " 未命中=" + misses.get());
        }
    }

    /** StampedLock 版：乐观读 */
    static class StampedCache {
        private final Map<String, String> map = new HashMap<>();
        private final StampedLock sl = new StampedLock();
        final AtomicInteger optimisticOk = new AtomicInteger();
        final AtomicInteger fallback = new AtomicInteger();

        void put(String k, String v) {
            long stamp = sl.writeLock();
            try {
                map.put(k, v);
            } finally {
                sl.unlockWrite(stamp);
            }
        }

        String get(String k) {
            long stamp = sl.tryOptimisticRead();
            String v = map.get(k);
            if (sl.validate(stamp)) {
                optimisticOk.incrementAndGet();
                return v;
            }
            fallback.incrementAndGet();
            stamp = sl.readLock();
            try {
                return map.get(k);
            } finally {
                sl.unlockRead(stamp);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("=== 1. 读读并发：4 个读线程同时跑 ===");
        RwCache cache = new RwCache();
        cache.put("k1", "v1");
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(4);
        long t0 = System.nanoTime();
        for (int i = 0; i < 4; i++) {
            new Thread(() -> {
                try {
                    start.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                String v = cache.get("k1");
                System.out.println("    " + Thread.currentThread().getName()
                        + " 读到 " + v + "，耗时 0ms（读读不互斥）");
                done.countDown();
            }, "reader-" + i).start();
        }
        start.countDown();
        done.await();
        System.out.printf("  4 个读总耗时 %.1f ms（若互斥会是 4 倍）%n", (System.nanoTime() - t0) / 1e6);
        cache.dump();

        System.out.println();
        System.out.println("=== 2. 读写互斥：写的时候读要等 ===");
        Thread writer = new Thread(() -> {
            cache.put("k2", "v2-slow");
            System.out.println("    写线程开始长时间持有写锁（300ms）");
            cache.put("k2", "v2-final");
            sleep(300);
        }, "writer");
        writer.start();
        sleep(30);
        long r0 = System.nanoTime();
        String v = cache.get("k2");
        System.out.printf("    读线程等待了 %.0f ms 才拿到（读写互斥）%n", (System.nanoTime() - r0) / 1e6);
        writer.join();

        System.out.println();
        System.out.println("=== 3. 锁降级 ===");
        System.out.println("  putAndRead 返回 = " + cache.putAndRead("k3", "v3"));
        System.out.println("  降级后仍持读锁读到自己的写，且写锁已释放，其他写线程可进");

        System.out.println();
        System.out.println("=== 4. StampedLock 乐观读 ===");
        StampedCache sc = new StampedCache();
        sc.put("hot", "value-1");
        for (int i = 0; i < 100; i++) {
            sc.get("hot");
        }
        System.out.println("  纯读 100 次：乐观读成功 " + sc.optimisticOk.get()
                + " 次，退化 " + sc.fallback.get() + " 次（无写时全部走乐观路径）");

        Thread updater = new Thread(() -> {
            for (int i = 0; i < 20; i++) {
                sc.put("hot", "value-" + (i + 2));
                sleep(2);
            }
        }, "updater");
        updater.start();
        int before = sc.fallback.get();
        while (updater.isAlive()) {
            sc.get("hot");
        }
        System.out.println("  并发写期间：退化次数增加 " + (sc.fallback.get() - before)
                + " 次（validate 发现版本变了，改用悲观读）");

        System.out.println();
        System.out.println("=== 5. 选型对照 ===");
        String[][] t = {
                {"读多写少", "ReentrantReadWriteLock", "读读并行，读写互斥"},
                {"读极多写极少、读很短", "StampedLock 乐观读", "无锁读，validate 校验"},
                {"写多", "ReentrantLock / synchronized", "读写锁反而更慢"},
                {"需要 Condition", "ReentrantLock / 读写锁", "StampedLock 不支持"}
        };
        for (String[] r : t) {
            System.out.printf("  %-22s -> %-28s %s%n", r[0], r[1], r[2]);
        }
    }
}
```

**课后练习**
1. 在持有读锁的情况下尝试 `rw.writeLock().lock()`，观察是否死锁，并解释原因。
2. 给 `StampedCache` 加一个"写时复制"版本（`volatile Map` + 写时 `new HashMap`），对比它和 StampedLock 的适用场景。
3. 统计 10 读 1 写、100 读 1 写两种负载下，读写锁与 StampedLock 的耗时差异，说明结论。

### 32-06 并发容器：ConcurrentHashMap 与 CopyOnWrite

**知识点**
- `HashMap` 线程不安全：并发 `put` 可能丢数据、JDK 8 之前还会形成环形链表导致 CPU 100%。多线程必须换 `ConcurrentHashMap`。
- `ConcurrentHashMap`（JDK 8+）：**CAS + synchronized 锁单个桶头节点**，锁粒度是"一个桶"，不是整表。扩容时多线程协助迁移（`helpTransfer`）。
- `size()` 是**估算值**（`baseCount` + `CounterCell[]` 分散计数），并发下不精确；`mappingCount()` 返回 `long`，更适合大表。
- 复合操作必须用原子方法：`putIfAbsent`、`computeIfAbsent`、`computeIfPresent`、`merge`、`replace(k, old, new)`。`if (!map.containsKey(k)) map.put(k, v)` 是典型的"检查-执行"竞态。
- `ConcurrentHashMap` **不允许 null 键值**（`HashMap` 允许），因为 `get` 返回 null 无法区分"不存在"和"值为 null"。这也是它能在并发下无歧义判断的原因。
- `CopyOnWriteArrayList`：写时复制整个数组，读操作完全无锁。适合**读多写极少**（如监听器列表、配置白名单），写多会疯狂复制，性能极差。
- `CopyOnWriteArraySet` 基于前者实现；`Collections.synchronizedList` 是全局锁，并发度远低于 COW（读也互斥）。
- 弱一致性迭代器：`ConcurrentHashMap` 的迭代器不抛 `ConcurrentModificationException`，但**不保证看到最新数据**，也不保证是某一时刻的快照。
- 易错：用 `size()` 做业务判断（并发下不准）；在 `computeIfAbsent` 的映射函数里修改同一个 map（可能死循环/抛异常）；对 `CopyOnWriteArrayList` 做高频写；用 `Collections.synchronizedMap` 替代 CHM 还期待高并发。

**示例代码**
```java
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class ConcurrentContainers {
    static final int THREADS = 16;
    static final int PER_THREAD = 2000;

    /** 并发累加：对比 HashMap 与 ConcurrentHashMap */
    static int runWithMap(Map<String, Integer> map, boolean safe) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch done = new CountDownLatch(THREADS);
        for (int t = 0; t < THREADS; t++) {
            pool.submit(() -> {
                for (int i = 0; i < PER_THREAD; i++) {
                    if (safe) {
                        map.merge("count", 1, Integer::sum);
                    } else {
                        Integer old = map.get("count");
                        map.put("count", old == null ? 1 : old + 1);
                    }
                }
                done.countDown();
            });
        }
        done.await(30, TimeUnit.SECONDS);
        pool.shutdownNow();
        Integer v = map.get("count");
        return v == null ? 0 : v;
    }

    public static void main(String[] args) throws Exception {
        int expected = THREADS * PER_THREAD;
        System.out.println("=== 1. HashMap 并发累加（预期 " + expected + "）===");
        int bad = runWithMap(new HashMap<>(), false);
        System.out.println("  HashMap 结果 = " + bad + "，丢失 " + (expected - bad) + " 次");
        System.out.println("  原因：get-put 是复合操作，中间被其他线程插队");

        System.out.println();
        System.out.println("=== 2. ConcurrentHashMap + merge（原子）===");
        int good = runWithMap(new ConcurrentHashMap<>(), true);
        System.out.println("  ConcurrentHashMap 结果 = " + good + "，误差 " + (expected - good));

        System.out.println();
        System.out.println("=== 3. 原子方法：computeIfAbsent 只创建一次 ===");
        ConcurrentHashMap<String, List<String>> groups = new ConcurrentHashMap<>();
        AtomicInteger created = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch done = new CountDownLatch(8);
        for (int t = 0; t < 8; t++) {
            final int id = t;
            pool.submit(() -> {
                for (int i = 0; i < 100; i++) {
                    groups.computeIfAbsent("g1", k -> {
                        created.incrementAndGet();
                        return Collections.synchronizedList(new ArrayList<>());
                    }).add("t" + id + "-" + i);
                }
                done.countDown();
            });
        }
        done.await(10, TimeUnit.SECONDS);
        pool.shutdownNow();
        System.out.println("  computeIfAbsent 里创建 List 的次数 = " + created.get() + "（应为 1）");
        System.out.println("  列表元素数 = " + groups.get("g1").size() + "（应为 800）");

        System.out.println();
        System.out.println("=== 4. putIfAbsent 实现幂等占位 ===");
        ConcurrentHashMap<String, String> idempotent = new ConcurrentHashMap<>();
        AtomicInteger success = new AtomicInteger();
        AtomicInteger duplicate = new AtomicInteger();
        ExecutorService pool2 = Executors.newFixedThreadPool(8);
        CountDownLatch done2 = new CountDownLatch(8);
        for (int t = 0; t < 8; t++) {
            pool2.submit(() -> {
                for (int i = 0; i < 200; i++) {
                    if (idempotent.putIfAbsent("REQ-001", "processing") == null) {
                        success.incrementAndGet();
                    } else {
                        duplicate.incrementAndGet();
                    }
                }
                done2.countDown();
            });
        }
        done2.await(10, TimeUnit.SECONDS);
        pool2.shutdownNow();
        System.out.println("  首次占位成功 = " + success.get() + "（应为 1），被判定重复 = " + duplicate.get());

        System.out.println();
        System.out.println("=== 5. 不允许 null 键值 ===");
        ConcurrentHashMap<String, String> chm = new ConcurrentHashMap<>();
        try {
            chm.put("k", null);
        } catch (NullPointerException e) {
            System.out.println("  put(k, null) -> NullPointerException（get 返回 null 才有唯一含义）");
        }
        System.out.println("  HashMap 允许 null：" + new HashMap<String, String>() {{
            put("k", null);
        }}.containsKey("k"));

        System.out.println();
        System.out.println("=== 6. CopyOnWriteArrayList：读无锁 ===");
        CopyOnWriteArrayList<String> cow = new CopyOnWriteArrayList<>(List.of("A", "B", "C"));
        Thread reader = new Thread(() -> {
            long t0 = System.nanoTime();
            int sum = 0;
            for (int i = 0; i < 100000; i++) {
                for (String s : cow) {
                    sum += s.length();
                }
            }
            System.out.printf("  读 100000 轮耗时 %.1f ms（读无锁，sum=%d）%n",
                    (System.nanoTime() - t0) / 1e6, sum);
        }, "reader");
        Thread writer2 = new Thread(() -> {
            for (int i = 0; i < 5; i++) {
                cow.add("W" + i);
                System.out.println("    写入 W" + i + "，当前大小 " + cow.size());
            }
        }, "writer");
        reader.start();
        writer2.start();
        reader.join();
        writer2.join();
        System.out.println("  迭代过程中写入不抛 ConcurrentModificationException");
        System.out.println("  代价：每次写都复制整个数组，写多时性能极差");

        System.out.println();
        System.out.println("=== 7. 容器选型 ===");
        String[][] t = {
                {"键值并发读写", "ConcurrentHashMap", "桶级锁 + CAS"},
                {"读多写极少列表", "CopyOnWriteArrayList", "写时复制，读无锁"},
                {"高频写列表", "ConcurrentLinkedQueue", "无锁链表"},
                {"需要有序并发映射", "ConcurrentSkipListMap", "跳表，O(log n)"},
                {"简单同步包装", "Collections.synchronizedXxx", "全局锁，并发度低"}
        };
        for (String[] r : t) {
            System.out.printf("  %-22s -> %-28s %s%n", r[0], r[1], r[2]);
        }
    }
}
```

**课后练习**
1. 用 `computeIfPresent` 实现"库存扣减，扣到 0 时移除键"，说明为什么不能用 `get` + `put`。
2. 对比 `CopyOnWriteArrayList` 与 `Collections.synchronizedList` 在"1 写 100 读"场景下的耗时，解释差异来源。
3. 说明 `ConcurrentHashMap.size()` 在并发下为什么不准，并给出一个不依赖精确 size 的限流实现思路。
