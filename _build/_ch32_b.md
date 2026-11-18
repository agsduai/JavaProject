### 32-07 并发工具类：CountDownLatch、CyclicBarrier、Semaphore、Phaser

**知识点**
- **CountDownLatch**：一次性倒计时。`await()` 等计数归零，`countDown()` 减一。**不能重置**，用完即废。典型用途：主线程等 N 个子任务全部完成、服务启动等待依赖就绪。
- **CyclicBarrier**：可循环的栅栏。N 个线程互相等待，全部到达后一起放行，并可选执行一个 `barrierAction`。**可重置**（`reset()` 或自动进入下一轮）。典型用途：分片计算后合并、多轮迭代。
- 二者区别：Latch 是"一个线程等多个线程"，计数只减不增、不可重用；Barrier 是"多个线程互相等"，可重复使用。
- **Semaphore**：许可证模型，`acquire()` 拿许可、`release()` 还许可。用于**限流**（控制同时访问的线程数）和**资源池**（连接池、车位）。公平模式可保证 FIFO。
- Semaphore 的坑：`release()` 不校验是否 `acquire()` 过，多调会凭空增加许可；`acquire()` 不放在 `try` 外，异常时许可泄漏。
- **Phaser**：更灵活的多阶段同步器，支持**动态注册/注销**参与者、多阶段推进。`arriveAndAwaitAdvance()` 到达并等下一阶段，`arriveAndDeregister()` 到达后退出。
- Phaser 适合"分阶段流水线"：阶段 1 全员准备 → 阶段 2 全员计算 → 阶段 3 全员汇总，且参与者数量可变。
- `Exchanger`：两线程交换数据，`exchange(v)` 阻塞直到对方也调用。适合"双缓冲"场景。
- 选择口诀：**等一批任务结束** → Latch；**多轮集合点** → Barrier；**限制并发数** → Semaphore；**动态多阶段** → Phaser；**两两交换** → Exchanger。
- 易错：Latch 的 `countDown()` 写在 `finally` 外（异常时永久等待）；Barrier 的 `await()` 没设超时（某个线程挂了全体卡死）；Semaphore 用来做"互斥锁"却忘了公平性导致饥饿。

**示例代码**
```java
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Exchanger;
import java.util.concurrent.Phaser;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class SyncTools {
    static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("=== 1. CountDownLatch：主线程等 3 个初始化完成 ===");
        CountDownLatch ready = new CountDownLatch(3);
        String[] deps = {"数据库连接池", "Redis 客户端", "配置中心"};
        long t0 = System.nanoTime();
        for (String d : deps) {
            new Thread(() -> {
                sleep(120);
                System.out.println("    " + d + " 就绪");
                ready.countDown();
            }, d).start();
        }
        ready.await(5, TimeUnit.SECONDS);
        System.out.printf("  全部就绪，总耗时 %.0f ms（并行，不是 360ms）%n",
                (System.nanoTime() - t0) / 1e6);
        System.out.println("  注意：Latch 计数归零后不能重置，下次要 new 一个新的");

        System.out.println();
        System.out.println("=== 2. CyclicBarrier：3 人集合后一起出发，跑两轮 ===");
        AtomicInteger round = new AtomicInteger(1);
        CyclicBarrier barrier = new CyclicBarrier(3, () ->
                System.out.println("    [栅栏动作] 第 " + round.getAndIncrement() + " 轮全员到齐，出发"));
        List<Thread> runners = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            final int id = i;
            Thread t = new Thread(() -> {
                for (int r = 0; r < 2; r++) {
                    sleep(50L * id);
                    System.out.println("    选手" + id + " 到达第 " + (r + 1) + " 轮集合点");
                    try {
                        barrier.await(3, TimeUnit.SECONDS);
                    } catch (Exception e) {
                        System.out.println("    选手" + id + " 等待异常: " + e.getClass().getSimpleName());
                        return;
                    }
                }
            }, "runner-" + i);
            runners.add(t);
            t.start();
        }
        for (Thread t : runners) {
            t.join();
        }
        System.out.println("  Barrier 自动重置，可循环使用（Latch 做不到）");

        System.out.println();
        System.out.println("=== 3. Semaphore：限制同时 2 个线程访问 ===");
        Semaphore slots = new Semaphore(2);
        AtomicInteger concurrent = new AtomicInteger();
        AtomicInteger peak = new AtomicInteger();
        CountDownLatch done = new CountDownLatch(6);
        for (int i = 1; i <= 6; i++) {
            final int id = i;
            new Thread(() -> {
                try {
                    slots.acquire();
                    try {
                        int now = concurrent.incrementAndGet();
                        peak.accumulateAndGet(now, Math::max);
                        System.out.printf("    任务%d 进入，当前并发=%d，剩余许可=%d%n",
                                id, now, slots.availablePermits());
                        sleep(150);
                    } finally {
                        concurrent.decrementAndGet();
                        slots.release();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            }, "task-" + i).start();
        }
        done.await(10, TimeUnit.SECONDS);
        System.out.println("  峰值并发 = " + peak.get() + "（许可数 2，不会超）");

        System.out.println();
        System.out.println("=== 4. Semaphore 做连接池 ===");
        Semaphore pool = new Semaphore(2, true);
        String[] conns = {"conn-A", "conn-B"};
        AtomicInteger borrowSeq = new AtomicInteger();
        for (int i = 0; i < 4; i++) {
            final int id = i;
            new Thread(() -> {
                try {
                    pool.acquire();
                    try {
                        int idx = borrowSeq.getAndIncrement() % 2;
                        System.out.printf("    请求%d 借到 %s（可用=%d）%n",
                                id, conns[idx], pool.availablePermits());
                        sleep(80);
                    } finally {
                        pool.release();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, "client-" + i).start();
        }
        sleep(800);

        System.out.println();
        System.out.println("=== 5. Phaser：动态参与者的三阶段流水线 ===");
        Phaser phaser = new Phaser(1) {
            @Override
            protected boolean onAdvance(int phase, int registered) {
                System.out.println("    [Phaser] 第 " + phase + " 阶段结束，剩余参与者 " + registered);
                // 只剩主线程一个参与者时终止，否则主线程会在 arriveAndAwaitAdvance 里永远等下去
                return registered == 1;
            }
        };
        String[] workers = {"A", "B", "C"};
        for (String w : workers) {
            phaser.register();
            new Thread(() -> {
                System.out.println("    " + w + " 阶段1：准备数据");
                phaser.arriveAndAwaitAdvance();
                System.out.println("    " + w + " 阶段2：计算");
                phaser.arriveAndAwaitAdvance();
                System.out.println("    " + w + " 阶段3：输出结果");
                phaser.arriveAndDeregister();
            }, "w-" + w).start();
        }
        while (!phaser.isTerminated()) {
            phaser.arriveAndAwaitAdvance();
        }
        System.out.println("  所有阶段完成，Phaser 已终止");

        System.out.println();
        System.out.println("=== 6. Exchanger：两线程交换缓冲区 ===");
        Exchanger<String> exchanger = new Exchanger<>();
        new Thread(() -> {
            String mine = "生产者缓冲[数据]";
            try {
                String other = exchanger.exchange(mine);
                System.out.println("    生产者换到: " + other);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "producer").start();
        new Thread(() -> {
            String mine = "消费者缓冲[空]";
            try {
                String other = exchanger.exchange(mine);
                System.out.println("    消费者换到: " + other);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "consumer").start();
        sleep(400);

        System.out.println();
        System.out.println("=== 7. 选型对照 ===");
        String[][] t = {
                {"CountDownLatch", "一等多，一次性", "服务启动等待依赖"},
                {"CyclicBarrier", "多等多的集合点，可重用", "分片计算后合并"},
                {"Semaphore", "限制并发数", "限流、连接池、车位"},
                {"Phaser", "动态参与者 + 多阶段", "流水线、分批处理"},
                {"Exchanger", "两两交换", "双缓冲"}
        };
        for (String[] r : t) {
            System.out.printf("  %-16s %-24s %s%n", r[0], r[1], r[2]);
        }
    }
}
```

**课后练习**
1. 用 `CountDownLatch` 实现"3 个任务并行执行，任一失败则整体失败"（提示：配合 `AtomicReference` 记录第一个异常）。
2. 用 `Semaphore(1, true)` 实现互斥锁，说明它与 `ReentrantLock` 在可重入性上的差异。
3. 把 Phaser 示例改成"阶段 2 只有 A、B 参与，C 提前退出"，用 `arriveAndDeregister` 实现并观察 `onAdvance` 的注册数变化。

### 32-08 原子类、CAS 与 LongAdder

**知识点**
- **CAS**（Compare-And-Swap）是硬件级原子指令：`cas(内存地址, 期望值, 新值)`，只有当前值等于期望值才更新。Java 通过 `Unsafe.compareAndSwapInt` / `VarHandle` 暴露。
- `AtomicInteger` 的 `incrementAndGet()` 就是"循环 CAS"：读当前值 → 算新值 → CAS，失败就重试（自旋）。
- **ABA 问题**：值从 A 改成 B 又改回 A，CAS 察觉不到。解决办法是加**版本号**（`AtomicStampedReference`）或改用不可变引用（`AtomicReference` 存对象）。
- 自旋的代价：高竞争下大量线程反复失败重试，**空转烧 CPU**。这是 `LongAdder` 出现的动机。
- **LongAdder**：把一个计数器拆成 `Cell[]` 数组，不同线程更新不同 Cell（分散热点），求和时累加所有 Cell。**写快读略慢**，适合"高并发写、低频读"的统计场景。
- `AtomicLong` vs `LongAdder`：前者单点 CAS，竞争激烈时退化；后者空间换时间，吞吐可高一个数量级。需要"实时精确读"时仍用 `AtomicLong`。
- `LongAccumulator` 是 `LongAdder` 的泛化版，可自定义聚合函数（如求最大值、最小值）。
- 原子引用：`AtomicReference<T>`（对象引用）、`AtomicMarkableReference`（引用 + 布尔标记）、`AtomicStampedReference`（引用 + int 版本）。
- 字段更新器：`AtomicIntegerFieldUpdater` 可对 `volatile` 字段做原子更新，省去把字段改成原子类（省内存，适合大量对象的场景）。
- 易错：以为 `volatile` 能替代原子类（`volatile` 只保证可见性，`i++` 仍非原子）；用 `AtomicInteger` 做"先检查再更新"的复合逻辑（应改用 `compareAndSet` 循环）；高并发下仍用 `AtomicLong` 计数。

**示例代码**
```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicStampedReference;
import java.util.concurrent.atomic.LongAdder;

public class AtomicCas {
    static final int THREADS = 16;
    static final int PER_THREAD = 50000;

    interface Counter {
        void inc();

        long get();
    }

    static long bench(String name, Counter c) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch done = new CountDownLatch(THREADS);
        long t0 = System.nanoTime();
        for (int t = 0; t < THREADS; t++) {
            pool.submit(() -> {
                for (int i = 0; i < PER_THREAD; i++) {
                    c.inc();
                }
                done.countDown();
            });
        }
        done.await(60, TimeUnit.SECONDS);
        long ms = (System.nanoTime() - t0) / 1_000_000;
        pool.shutdownNow();
        System.out.printf("  %-14s 结果=%-9d 耗时=%4d ms%n", name, c.get(), ms);
        return ms;
    }

    public static void main(String[] args) throws Exception {
        long expected = (long) THREADS * PER_THREAD;
        System.out.println("=== 1. 三种计数器对比（" + THREADS + " 线程 × " + PER_THREAD + " 次 = " + expected + "）===");

        long atomicMs = bench("AtomicInteger", new Counter() {
            final AtomicInteger v = new AtomicInteger();

            public void inc() {
                v.incrementAndGet();
            }

            public long get() {
                return v.get();
            }
        });

        long adderMs = bench("LongAdder", new Counter() {
            final LongAdder v = new LongAdder();

            public void inc() {
                v.increment();
            }

            public long get() {
                return v.sum();
            }
        });

        long syncMs = bench("synchronized", new Counter() {
            long v;

            public synchronized void inc() {
                v++;
            }

            public synchronized long get() {
                return v;
            }
        });

        System.out.printf("  结论：LongAdder 比 AtomicInteger 快约 %.1f 倍，"
                + "synchronized 约为其 %.1f 倍耗时%n",
                (double) atomicMs / Math.max(1, adderMs), (double) syncMs / Math.max(1, adderMs));

        System.out.println();
        System.out.println("=== 2. CAS 手写自旋 ===");
        AtomicInteger spin = new AtomicInteger(0);
        int ok = 0;
        for (int i = 0; i < 1000; i++) {
            while (true) {
                int cur = spin.get();
                int next = cur + 1;
                if (spin.compareAndSet(cur, next)) {
                    ok++;
                    break;
                }
            }
        }
        System.out.println("  自旋 1000 次成功 " + ok + " 次，最终值 = " + spin.get());

        System.out.println();
        System.out.println("=== 3. ABA 问题演示 ===");
        AtomicInteger plain = new AtomicInteger(100);
        int snapshot = plain.get();
        plain.compareAndSet(100, 200);
        plain.compareAndSet(200, 100);
        boolean abaMissed = plain.compareAndSet(snapshot, 999);
        System.out.println("  值经历 100->200->100，CAS(100,999) 结果 = " + abaMissed
                + "（察觉不到中间变化）");

        AtomicStampedReference<Integer> stamped = new AtomicStampedReference<>(100, 0);
        int[] snap = new int[1];
        int seen = stamped.get(snap);
        stamped.compareAndSet(100, 200, snap[0], snap[0] + 1);
        stamped.compareAndSet(200, 100, snap[0] + 1, snap[0] + 2);
        boolean stampedOk = stamped.compareAndSet(seen, 999, snap[0], snap[0] + 1);
        System.out.println("  带版本号：期望值=" + seen + " 期望版本=" + snap[0]
                + " 实际版本=" + stamped.getStamp() + " -> CAS 结果 = " + stampedOk);
        System.out.println("  结论：版本号让 ABA 现形，CAS 失败");

        System.out.println();
        System.out.println("=== 4. 复合操作必须用 CAS 循环 ===");
        AtomicInteger stock = new AtomicInteger(5);
        int sold = 0;
        for (int i = 0; i < 10; i++) {
            while (true) {
                int cur = stock.get();
                if (cur <= 0) {
                    break;
                }
                if (stock.compareAndSet(cur, cur - 1)) {
                    sold++;
                    break;
                }
            }
        }
        System.out.println("  库存 5，10 次请求，成功扣减 " + sold + " 次，剩余 " + stock.get());
        System.out.println("  如果用 if (stock.get() > 0) stock.decrementAndGet()，会超卖");

        System.out.println();
        System.out.println("=== 5. LongAdder 的适用边界 ===");
        String[][] t = {
                {"高并发写 + 低频读", "LongAdder", "分散热点，吞吐最高"},
                {"需要实时精确值", "AtomicLong", "sum() 是弱一致的"},
                {"需要 CAS 语义", "AtomicLong", "LongAdder 没有 compareAndSet"},
                {"低并发", "两者差不多", "LongAdder 多了 Cell 开销"},
                {"求最大值/自定义聚合", "LongAccumulator", "可传聚合函数"}
        };
        for (String[] r : t) {
            System.out.printf("  %-24s -> %-16s %s%n", r[0], r[1], r[2]);
        }
    }
}
```

**课后练习**
1. 用 `AtomicReference` 实现"无锁栈"（`push` 用 CAS 换头节点），说明它相比 `synchronized` 栈的优势与风险。
2. 构造一个真实会失败的 ABA 场景（如栈的 `pop` 后节点被复用），说明为什么仅靠值比较不够。
3. 对比 `LongAdder` 与 `AtomicLong` 在"1 写线程 + 1 读线程"下的读延迟，说明为什么读多场景不该用 `LongAdder`。

### 32-09 线程协作：wait/notify、BlockingQueue 与 ThreadLocal

**知识点**
- `wait()` / `notify()` 必须在**持有该对象监视器**（`synchronized`）时调用，否则抛 `IllegalMonitorStateException`。`wait()` 会释放锁并挂起，被唤醒后要重新抢锁。
- **必须用 `while` 而不是 `if` 包住 `wait()`**：被唤醒不代表条件成立（可能是虚假唤醒，也可能被别人抢先消费）。
- `notify()` 只唤醒一个（随机），`notifyAll()` 唤醒全部。用 `if` + `notify` 的组合是经典 bug 源。
- `BlockingQueue` 已经封装好了"锁 + 条件 + 循环判断"，生产代码优先用它而不是手写 `wait/notify`。
- 常用实现：`ArrayBlockingQueue`（有界数组，单锁双条件）、`LinkedBlockingQueue`（链表，默认无界）、`SynchronousQueue`（容量 0，直接交接）、`PriorityBlockingQueue`（优先级）、`DelayQueue`（延迟到期才可取）。
- `BlockingQueue` 的四组方法：抛异常（`add`/`remove`/`element`）、返回特殊值（`offer`/`poll`/`peek`）、阻塞（`put`/`take`）、超时（`offer(e,t,u)`/`poll(t,u)`）。
- `ThreadLocal` 为每个线程保存独立副本，底层是 `Thread` 里的 `ThreadLocalMap`（key 是 `ThreadLocal` 的**弱引用**，value 是强引用）。
- **ThreadLocal 内存泄漏**：key 被 GC 后变成 `null`，但 value 仍被 `ThreadLocalMap` 强引用，且线程池的线程长期存活 → value 无法回收。**必须 `remove()`**，尤其在 `finally` 里。
- `ThreadLocal` 的正确用法：请求级上下文（`traceId`、`userId`）、非线程安全对象的每线程实例（`SimpleDateFormat`）。用完必须清理，线程池场景尤其重要。
- `InheritableThreadLocal` 能把值传给子线程，但**线程池里复用线程不会重新继承**，跨线程传递应改用显式参数或 `TransmittableThreadLocal` 类方案。
- 易错：`wait()` 没在 `synchronized` 块里；`if` 代替 `while`；`ThreadLocal` 用完不 `remove`；用 `ThreadLocal` 存大对象（每个线程一份，内存翻倍）。

**示例代码**
```java
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class ThreadCooperation {
    static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** 手写 wait/notify：while 循环 + notifyAll 是正确姿势 */
    static class HandmadeQueue {
        private final int[] buf = new int[3];
        private int head, tail, count;

        synchronized void put(int v) throws InterruptedException {
            while (count == buf.length) {
                wait();
            }
            buf[tail] = v;
            tail = (tail + 1) % buf.length;
            count++;
            notifyAll();
        }

        synchronized int take() throws InterruptedException {
            while (count == 0) {
                wait();
            }
            int v = buf[head];
            head = (head + 1) % buf.length;
            count--;
            notifyAll();
            return v;
        }
    }

    /** 带延迟的任务 */
    static class DelayTask implements Delayed {
        final String name;
        final long readyAt;

        DelayTask(String name, long delayMs) {
            this.name = name;
            this.readyAt = System.currentTimeMillis() + delayMs;
        }

        public long getDelay(TimeUnit unit) {
            return unit.convert(readyAt - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
        }

        public int compareTo(Delayed o) {
            return Long.compare(getDelay(TimeUnit.MILLISECONDS), o.getDelay(TimeUnit.MILLISECONDS));
        }
    }

    /** ThreadLocal：每线程一份 */
    static final ThreadLocal<String> CONTEXT = new ThreadLocal<>();
    static final AtomicInteger LEAK_COUNT = new AtomicInteger();

    public static void main(String[] args) throws Exception {
        System.out.println("=== 1. 手写 wait/notify ===");
        HandmadeQueue q = new HandmadeQueue();
        Thread p = new Thread(() -> {
            for (int i = 1; i <= 5; i++) {
                try {
                    q.put(i);
                    System.out.println("    生产 " + i);
                    sleep(40);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }, "producer");
        Thread c = new Thread(() -> {
            for (int i = 1; i <= 5; i++) {
                try {
                    System.out.println("    消费 " + q.take());
                    sleep(90);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }, "consumer");
        p.start();
        c.start();
        p.join();
        c.join();
        System.out.println("  要点：wait 必须包在 while 里（防虚假唤醒），通知用 notifyAll");

        System.out.println();
        System.out.println("=== 2. BlockingQueue：生产代码首选 ===");
        BlockingQueue<String> bq = new ArrayBlockingQueue<>(2);
        Thread producer = new Thread(() -> {
            for (int i = 1; i <= 4; i++) {
                try {
                    bq.put("订单" + i);
                    System.out.println("    put 订单" + i + "，队列大小=" + bq.size());
                } catch (InterruptedException e) {
                    return;
                }
            }
        }, "p");
        Thread consumer = new Thread(() -> {
            for (int i = 1; i <= 4; i++) {
                try {
                    sleep(100);
                    System.out.println("    take " + bq.take() + "，队列大小=" + bq.size());
                } catch (InterruptedException e) {
                    return;
                }
            }
        }, "c");
        producer.start();
        consumer.start();
        producer.join();
        consumer.join();

        System.out.println();
        System.out.println("=== 3. 四种队列行为对比 ===");
        BlockingQueue<String> abq = new ArrayBlockingQueue<>(1);
        abq.put("x");
        System.out.println("  ArrayBlockingQueue(1) 已满：");
        System.out.println("    add   -> " + tryRun(() -> abq.add("y")));
        System.out.println("    offer -> " + abq.offer("y") + "（不阻塞，返回 false）");
        System.out.println("    put   -> 会阻塞（此处跳过）");

        BlockingQueue<String> sq = new SynchronousQueue<>();
        new Thread(() -> {
            try {
                sleep(150);
                System.out.println("    交接线程取走: " + sq.take());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "handoff").start();
        long t0 = System.nanoTime();
        sq.put("直接交接的数据");
        System.out.printf("  SynchronousQueue.put 阻塞了 %.0f ms（直到有人取）%n",
                (System.nanoTime() - t0) / 1e6);

        System.out.println();
        System.out.println("=== 4. DelayQueue：到期才能取 ===");
        DelayQueue<DelayTask> dq = new DelayQueue<>();
        dq.put(new DelayTask("5分钟后关单", 300));
        dq.put(new DelayTask("1分钟后提醒", 100));
        dq.put(new DelayTask("立即通知", 0));
        long start = System.nanoTime();
        while (!dq.isEmpty()) {
            DelayTask t = dq.take();
            System.out.printf("    取出 %-12s 距开始 %4d ms%n", t.name,
                    (System.nanoTime() - start) / 1_000_000);
        }
        System.out.println("  顺序按到期时间，不是入队顺序");

        System.out.println();
        System.out.println("=== 5. ThreadLocal：每线程独立副本 ===");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        for (int i = 1; i <= 4; i++) {
            final int id = i;
            pool.submit(() -> {
                CONTEXT.set("trace-" + id);
                sleep(20);
                System.out.println("    任务" + id + " 在 " + Thread.currentThread().getName()
                        + " 读到 " + CONTEXT.get());
            });
        }
        pool.shutdown();
        pool.awaitTermination(3, TimeUnit.SECONDS);

        System.out.println();
        System.out.println("=== 6. ThreadLocal 泄漏：线程池复用导致串数据 ===");
        ExecutorService pool2 = Executors.newSingleThreadExecutor();
        pool2.submit(() -> CONTEXT.set("上一个请求的 userId=999"));
        pool2.submit(() -> {
            String v = CONTEXT.get();
            System.out.println("  未清理时，下一个任务读到 = " + v + "（脏数据！）");
            LEAK_COUNT.incrementAndGet();
        });
        pool2.shutdown();
        pool2.awaitTermination(2, TimeUnit.SECONDS);
        CONTEXT.remove();

        ExecutorService pool3 = Executors.newSingleThreadExecutor();
        pool3.submit(() -> {
            try {
                CONTEXT.set("userId=1");
            } finally {
                CONTEXT.remove();
            }
        });
        pool3.submit(() -> System.out.println("  finally 里 remove 后读到 = " + CONTEXT.get() + "（干净）"));
        pool3.shutdown();
        pool3.awaitTermination(2, TimeUnit.SECONDS);

        System.out.println();
        System.out.println("=== 7. 协作方式选择 ===");
        String[][] t = {
                {"简单两线程通知", "wait/notify", "必须 while + synchronized"},
                {"生产者-消费者", "BlockingQueue", "已封装锁与条件"},
                {"延迟任务", "DelayQueue", "按到期时间出队"},
                {"请求上下文", "ThreadLocal + remove", "线程池必须清理"},
                {"跨线程传递上下文", "显式参数 / TTL", "Inheritable 在池里失效"}
        };
        for (String[] r : t) {
            System.out.printf("  %-20s -> %-26s %s%n", r[0], r[1], r[2]);
        }
    }

    static String tryRun(Runnable r) {
        try {
            r.run();
            return "成功";
        } catch (Exception e) {
            return e.getClass().getSimpleName();
        }
    }
}
```

**课后练习**
1. 把 `HandmadeQueue` 的 `while` 改成 `if`，构造"两个消费者 + 一个生产者"的场景，尝试复现错误（提示：可用 `notifyAll` 唤醒两个消费者）。
2. 用 `DelayQueue` 实现"订单 30 分钟未支付自动关闭"，说明为什么它比 `Timer` 更适合（提示：`Timer` 单线程、任务异常会终止整个定时器）。
3. 写一个 `ThreadLocal` 泄漏的复现程序（用弱引用观察 key 被回收），并说明为什么线程池场景必须 `remove`。

### 32-10 并发问题诊断：死锁、线程转储与性能瓶颈

**知识点**
- **死锁**四条件：互斥、持有并等待、不可剥夺、循环等待。检测手段：`jstack <pid>`、`jcmd <pid> Thread.print`、`ThreadMXBean.findDeadlockedThreads()`、`jconsole` 的"检测死锁"按钮。
- 死锁的典型代码：两个线程按相反顺序获取两把锁。修复方式：**统一加锁顺序**、用 `tryLock(timeout)` 放弃重试、缩小锁范围。
- **线程转储（thread dump）** 读法：先看线程状态分布（`BLOCKED` 多 = 锁竞争；`WAITING` 多 = 等 IO 或条件；`RUNNABLE` 但 CPU 高 = 死循环），再看 `BLOCKED` 线程的 `waiting to lock` 与持锁者。
- `jstack` 输出的关键字段：线程名、`tid`/`nid`、`java.lang.Thread.State`、`at ...` 调用栈、`- waiting to lock <0x...>`、`- locked <0x...>`、`Found one Java-level deadlock`。
- **CPU 飙高**排查四步：`top` 找进程 → `top -Hp <pid>` 找线程 → `printf '%x' <tid>` 转十六进制 → `jstack <pid> | grep -A 20 <nid>` 定位栈。
- **线程数暴涨**：常见于"每次请求 new Thread"、线程池 `max` 设成 `Integer.MAX_VALUE`、`CachedThreadPool` 在 IO 阻塞时无限扩张。
- **锁竞争严重**的表现：大量线程 `BLOCKED`，`jstack` 里同一把锁反复出现；解决方向是缩小临界区、分段锁、无锁化（CAS/ThreadLocal）。
- 其他常见病：`WAITING` 卡在 `park` 且无超时（可能永久等待）、`TIMED_WAITING` 卡在 socket read（下游没响应）、线程泄漏（线程数只增不减）。
- 工具矩阵：`jps`（找进程）、`jstack`（线程栈）、`jstat -gcutil`（GC）、`jmap -histo`（对象直方图）、`arthas`（在线诊断、`thread -b` 直接找阻塞源头）。
- 易错：只看 CPU 不看线程状态；`jstack` 只抓一次（要间隔抓 3 次看是否同一处卡住）；在生产用 `kill -3` 前不确认日志路径。

**示例代码**
```java
import java.lang.management.LockInfo;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public class ConcurrencyDiagnose {
    static final Object LOCK_A = new Object();
    static final Object LOCK_B = new Object();

    static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("=== 1. 制造死锁（相反加锁顺序）===");
        CountDownLatch bothStarted = new CountDownLatch(2);
        Thread t1 = new Thread(() -> {
            synchronized (LOCK_A) {
                bothStarted.countDown();
                sleep(100);
                synchronized (LOCK_B) {
                    System.out.println("    t1 拿到两把锁（不该出现）");
                }
            }
        }, "deadlock-t1");
        Thread t2 = new Thread(() -> {
            synchronized (LOCK_B) {
                bothStarted.countDown();
                sleep(100);
                synchronized (LOCK_A) {
                    System.out.println("    t2 拿到两把锁（不该出现）");
                }
            }
        }, "deadlock-t2");
        t1.setDaemon(true);
        t2.setDaemon(true);
        t1.start();
        t2.start();
        bothStarted.await(2, TimeUnit.SECONDS);
        sleep(300);

        System.out.println();
        System.out.println("=== 2. 用 ThreadMXBean 检测死锁 ===");
        ThreadMXBean tmx = ManagementFactory.getThreadMXBean();
        long[] deadlocked = tmx.findDeadlockedThreads();
        if (deadlocked == null) {
            System.out.println("  未检测到死锁");
        } else {
            System.out.println("  检测到 " + deadlocked.length + " 个死锁线程：");
            ThreadInfo[] infos = tmx.getThreadInfo(deadlocked, true, true);
            for (ThreadInfo ti : infos) {
                System.out.println("    线程: " + ti.getThreadName() + " 状态=" + ti.getThreadState());
                LockInfo lock = ti.getLockInfo();
                if (lock != null) {
                    System.out.println("      等待锁: " + lock.getClassName() + "@"
                            + Integer.toHexString(lock.getIdentityHashCode()));
                }
                LockInfo[] held = ti.getLockedSynchronizers();
                for (LockInfo h : held) {
                    System.out.println("      持有锁: " + h.getClassName() + "@"
                            + Integer.toHexString(h.getIdentityHashCode()));
                }
                StackTraceElement[] st = ti.getStackTrace();
                if (st.length > 0) {
                    System.out.println("      栈顶: " + st[0]);
                }
            }
            System.out.println();
            System.out.println("  修复思路：统一加锁顺序 A->B，或改用 tryLock 超时放弃");
        }

        System.out.println();
        System.out.println("=== 3. 修复版：统一顺序 + tryLock 兜底 ===");
        ReentrantLock la = new ReentrantLock();
        ReentrantLock lb = new ReentrantLock();
        CountDownLatch done = new CountDownLatch(2);

        Runnable safeTask = () -> {
            boolean ok1 = false, ok2 = false;
            try {
                ok1 = la.tryLock(100, TimeUnit.MILLISECONDS);
                ok2 = lb.tryLock(100, TimeUnit.MILLISECONDS);
                if (ok1 && ok2) {
                    System.out.println("    " + Thread.currentThread().getName() + " 同时拿到 A、B");
                } else {
                    System.out.println("    " + Thread.currentThread().getName()
                            + " 超时放弃（避免死锁）");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                if (ok2) {
                    lb.unlock();
                }
                if (ok1) {
                    la.unlock();
                }
                done.countDown();
            }
        };
        new Thread(safeTask, "safe-1").start();
        new Thread(safeTask, "safe-2").start();
        done.await(3, TimeUnit.SECONDS);

        System.out.println();
        System.out.println("=== 4. 线程状态快照（自己实现一个简版 jstack）===");
        Thread[] all = new Thread[Thread.activeCount() + 10];
        int n = Thread.enumerate(all);
        System.out.printf("  %-22s %-14s %s%n", "线程名", "状态", "栈顶");
        for (int i = 0; i < n; i++) {
            Thread t = all[i];
            if (t == null || !t.isAlive()) {
                continue;
            }
            StackTraceElement[] st = t.getStackTrace();
            String top = st.length > 0 ? st[0].toString() : "-";
            if (top.length() > 62) {
                top = top.substring(0, 59) + "...";
            }
            System.out.printf("  %-22s %-14s %s%n", cut(t.getName(), 22), t.getState(), top);
        }

        System.out.println();
        System.out.println("=== 5. 锁竞争演示：BLOCKED 从哪来 ===");
        final Object hotLock = new Object();
        CountDownLatch go = new CountDownLatch(1);
        CountDownLatch fin = new CountDownLatch(4);
        for (int i = 1; i <= 4; i++) {
            final int id = i;
            new Thread(() -> {
                try {
                    go.await();
                    synchronized (hotLock) {
                        System.out.println("    线程" + id + " 进入临界区");
                        sleep(120);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    fin.countDown();
                }
            }, "hot-" + i).start();
        }
        go.countDown();
        sleep(60);
        int blocked = 0;
        Thread[] snap = new Thread[Thread.activeCount() + 10];
        int m = Thread.enumerate(snap);
        for (int i = 0; i < m; i++) {
            if (snap[i] != null && snap[i].getState() == Thread.State.BLOCKED) {
                blocked++;
                System.out.println("    观察到 BLOCKED: " + snap[i].getName());
            }
        }
        System.out.println("  当前 BLOCKED 线程数 = " + blocked + "（都在等 hotLock）");
        fin.await(3, TimeUnit.SECONDS);

        System.out.println();
        System.out.println("=== 6. CPU 飙高排查四步 ===");
        String[] steps = {
                "1. top 找到 CPU 最高的进程 PID",
                "2. top -Hp <PID> 找到该进程内 CPU 最高的线程 TID",
                "3. printf '%x' <TID> 把十进制 TID 转成十六进制 nid",
                "4. jstack <PID> | grep -A 30 'nid=0x<nid>' 定位到具体代码行"
        };
        for (String s : steps) {
            System.out.println("  " + s);
        }
        System.out.println("  arthas 一行搞定：thread -n 3（CPU 前 3）、thread -b（找阻塞源头）");

        System.out.println();
        System.out.println("=== 7. 线程状态含义 ===");
        String[][] states = {
                {"NEW", "已创建未 start", "正常"},
                {"RUNNABLE", "运行或等 CPU/IO", "大量且 CPU 高 -> 死循环"},
                {"BLOCKED", "等 synchronized 锁", "多 -> 锁竞争严重"},
                {"WAITING", "wait/park 无超时", "可能永久等待"},
                {"TIMED_WAITING", "sleep/wait(t)/parkNanos", "多 -> 下游慢"},
                {"TERMINATED", "已结束", "正常"}
        };
        for (String[] s : states) {
            System.out.printf("  %-15s %-24s %s%n", s[0], s[1], s[2]);
        }
    }

    static String cut(String s, int len) {
        return s.length() <= len ? s : s.substring(0, len - 3) + "...";
    }
}
```

**课后练习**
1. 用 `jstack` 抓取本课死锁示例的线程转储，找到 `Found one Java-level deadlock` 段落并解释每一行的含义。
2. 写一个"CPU 100% 死循环"的示例，用 `top -Hp` + `jstack` 定位到具体行号，记录完整步骤。
3. 解释为什么"抓一次 jstack 不够"，说明间隔抓 3 次能额外发现什么问题。

### 32-11 综合案例：高并发秒杀链路

**知识点**
- 秒杀的核心矛盾：**瞬时流量远大于库存与系统容量**。解法是"层层削减"：前端限流 → 网关限流 → 应用层限流 → 缓存预扣 → 异步下单 → 数据库最终扣减。
- **超卖**的根因是"检查库存"与"扣减库存"不是原子操作。单机用 `CAS`/`synchronized`，分布式用 Redis `DECR`（Lua 保证原子）或数据库 `update ... where stock >= n`。
- **限流**三种算法：计数器（有临界问题）、**滑动窗口**（更平滑）、**令牌桶**（允许突发）、**漏桶**（恒定速率）。秒杀常用令牌桶 + 排队。
- **异步下单**：请求先入队（内存队列/MQ），返回"排队中"，消费者按数据库能力匀速消费。这样"削峰"把瞬时 10 万 QPS 摊平成持续 500 QPS。
- **幂等**：同一用户重复点击不能重复下单。用 `userId + activityId` 做唯一键，`putIfAbsent` 占位或数据库唯一索引兜底。
- **库存预扣 + 最终一致**：Redis 预扣成功才允许下单，下单失败要**回补库存**（补偿），并记录流水以便对账。
- 秒杀接口的设计原则：**快失败**（库存没了立刻返回售罄，不排队）、**少依赖**（不在主链路做 RPC）、**可降级**（风控超时直接放行或拒绝，按业务定）。
- 压测要点：关注 **QPS、P99、错误率、库存准确性**四个指标，其中"库存准确性"（是否超卖/少卖）比性能更关键。
- 易错：用"查库存 → if > 0 → 扣减"两步（必然超卖）；限流只在网关做（内网直调绕过）；异步下单没有幂等（重复消费导致重复扣款）；库存回补没做（少卖）。

**示例代码**
```java
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class SeckillCase {
    /** 令牌桶限流：容量 + 匀速补充 */
    static class TokenBucket {
        private final int capacity;
        private final double refillPerMs;
        private double tokens;
        private long lastRefill = System.currentTimeMillis();
        private final AtomicLong passed = new AtomicLong();
        private final AtomicLong rejected = new AtomicLong();

        TokenBucket(int capacity, double refillPerSecond) {
            this.capacity = capacity;
            this.refillPerMs = refillPerSecond / 1000.0;
            this.tokens = capacity;
        }

        synchronized boolean tryAcquire() {
            long now = System.currentTimeMillis();
            tokens = Math.min(capacity, tokens + (now - lastRefill) * refillPerMs);
            lastRefill = now;
            if (tokens >= 1) {
                tokens -= 1;
                passed.incrementAndGet();
                return true;
            }
            rejected.incrementAndGet();
            return false;
        }
    }

    /** 秒杀核心服务 */
    static class SeckillService {
        private final int stock;
        private final AtomicInteger remaining;
        private final Map<String, Boolean> bought = new ConcurrentHashMap<>();
        private final BlockingQueue<String> orderQueue = new ArrayBlockingQueue<>(10000);
        private final AtomicInteger created = new AtomicInteger();
        private final AtomicInteger soldOut = new AtomicInteger();
        private final AtomicInteger duplicate = new AtomicInteger();
        private final AtomicInteger rollback = new AtomicInteger();

        SeckillService(int stock) {
            this.stock = stock;
            this.remaining = new AtomicInteger(stock);
        }

        /** 主链路：幂等 -> 预扣库存 -> 入队；全内存，毫秒级返回 */
        String seckill(long userId, long activityId) {
            String key = userId + ":" + activityId;
            if (bought.putIfAbsent(key, Boolean.TRUE) != null) {
                duplicate.incrementAndGet();
                return "REPEAT";
            }
            while (true) {
                int cur = remaining.get();
                if (cur <= 0) {
                    bought.remove(key);
                    soldOut.incrementAndGet();
                    return "SOLD_OUT";
                }
                if (remaining.compareAndSet(cur, cur - 1)) {
                    break;
                }
            }
            if (!orderQueue.offer(key)) {
                remaining.incrementAndGet();
                bought.remove(key);
                return "QUEUE_FULL";
            }
            return "QUEUED";
        }

        /** 消费者：按数据库能力匀速创建订单 */
        void startConsumers(int n) {
            for (int i = 0; i < n; i++) {
                Thread t = new Thread(() -> {
                    while (true) {
                        try {
                            String key = orderQueue.poll(200, TimeUnit.MILLISECONDS);
                            if (key == null) {
                                if (Thread.currentThread().isInterrupted()) {
                                    return;
                                }
                                continue;
                            }
                            Thread.sleep(1);
                            if (key.hashCode() % 200 == 0) {
                                rollback.incrementAndGet();
                                remaining.incrementAndGet();
                                bought.remove(key);
                            } else {
                                created.incrementAndGet();
                            }
                        } catch (InterruptedException e) {
                            return;
                        }
                    }
                }, "order-consumer-" + i);
                t.setDaemon(true);
                t.start();
            }
        }

        String report() {
            return String.format("库存=%d 剩余=%d 已下单=%d 售罄拒绝=%d 重复=%d 回补=%d 队列=%d",
                    stock, remaining.get(), created.get(), soldOut.get(),
                    duplicate.get(), rollback.get(), orderQueue.size());
        }
    }

    public static void main(String[] args) throws Exception {
        int stock = 100;
        int users = 2000;
        SeckillService svc = new SeckillService(stock);
        svc.startConsumers(4);
        TokenBucket bucket = new TokenBucket(500, 50000);

        System.out.println("=== 秒杀链路：限流 -> 幂等 -> 预扣 -> 异步下单 ===");
        System.out.println("  库存 " + stock + "，模拟 " + users + " 个用户同时抢");
        System.out.println("  其中 200 个用户重复点击 2 次（验证幂等）");

        ExecutorService pool = Executors.newFixedThreadPool(64);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(users + 200);
        Map<String, AtomicInteger> results = new ConcurrentHashMap<>();
        for (String k : new String[]{"QUEUED", "SOLD_OUT", "REPEAT", "LIMITED"}) {
            results.put(k, new AtomicInteger());
        }

        long t0 = System.nanoTime();
        for (int i = 1; i <= users; i++) {
            final long uid = i;
            pool.submit(() -> {
                try {
                    start.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                String r;
                if (!bucket.tryAcquire()) {
                    r = "LIMITED";
                } else {
                    r = svc.seckill(uid, 1001L);
                }
                results.get(r).incrementAndGet();
                done.countDown();
            });
        }
        for (int i = 1; i <= 200; i++) {
            final long uid = i;
            pool.submit(() -> {
                try {
                    start.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                if (bucket.tryAcquire()) {
                    String r = svc.seckill(uid, 1001L);
                    results.get(r).incrementAndGet();
                } else {
                    results.get("LIMITED").incrementAndGet();
                }
                done.countDown();
            });
        }
        start.countDown();
        done.await(30, TimeUnit.SECONDS);
        long ms = (System.nanoTime() - t0) / 1_000_000;
        pool.shutdownNow();

        System.out.printf("%n  请求处理耗时 %d ms%n", ms);
        System.out.println("  结果分布：");
        for (Map.Entry<String, AtomicInteger> e : results.entrySet()) {
            System.out.printf("    %-10s %d%n", e.getKey(), e.getValue().get());
        }
        System.out.println("  限流：放行 " + bucket.passed.get() + "，拒绝 " + bucket.rejected.get());

        System.out.println();
        System.out.println("  等待异步下单消费完...");
        for (int i = 0; i < 60; i++) {
            if (svc.orderQueue.isEmpty()) {
                Thread.sleep(300);
                break;
            }
            Thread.sleep(100);
        }
        System.out.println("  " + svc.report());

        System.out.println();
        System.out.println("=== 正确性校验 ===");
        int created = svc.created.get();
        int remain = svc.remaining.get();
        boolean noOversell = created <= stock;
        boolean consistent = (created + remain + svc.rollback.get()) == stock;
        System.out.println("  是否超卖（已下单 <= 库存）：" + noOversell);
        System.out.println("  库存守恒（已下单 + 剩余 + 回补 == 初始库存）：" + consistent);
        System.out.println("  重复请求被拦截：" + svc.duplicate.get() + " 次");

        System.out.println();
        System.out.println("=== 链路设计要点 ===");
        String[][] points = {
                {"1. 限流", "令牌桶 500 容量 + 5万/秒补充", "挡住明显超量的流量"},
                {"2. 幂等", "userId:activityId 唯一键 putIfAbsent", "重复点击不重复下单"},
                {"3. 预扣", "CAS 循环扣减，扣不动立刻返回售罄", "不会超卖，快失败"},
                {"4. 异步", "入队后立即返回 QUEUED", "把瞬时峰值摊平"},
                {"5. 消费", "4 个消费者匀速建单", "保护数据库"},
                {"6. 补偿", "失败回补库存 + 释放幂等键", "不少卖，可重试"}
        };
        for (String[] p : points) {
            System.out.printf("  %-10s %-40s %s%n", p[0], p[1], p[2]);
        }
    }
}
```

**课后练习**
1. 把 `remaining` 从 `AtomicInteger` 换成 `synchronized` 方法，对比吞吐差异，说明为什么高并发下 CAS 更优。
2. 去掉幂等判断（直接删 `putIfAbsent`），观察"重复用户"是否真的重复下单，统计超卖量。
3. 把令牌桶容量改成 50、补充速率改成 1000/秒，重新跑，说明"限流越紧，售罄越快还是越慢"。
4. 设计"库存回补失败"的兜底方案（提示：本地消息表 + 定时对账），说明为什么不能只靠内存补偿。
