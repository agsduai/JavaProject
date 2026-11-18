/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：32-08
 * 课名：原子类、CAS 与 LongAdder
 * ------------------------------------------------------------
 * 知识点：
 *   - **CAS**（Compare-And-Swap）是硬件级原子指令：`cas(内存地址, 期望值, 新值)`，只有当前值等于期望值才更新。Java 通过 `Unsafe.compareAndSwapInt` / `VarHandle` 暴露。
 *   - `AtomicInteger` 的 `incrementAndGet()` 就是"循环 CAS"：读当前值 → 算新值 → CAS，失败就重试（自旋）。
 *   - **ABA 问题**：值从 A 改成 B 又改回 A，CAS 察觉不到。解决办法是加**版本号**（`AtomicStampedReference`）或改用不可变引用（`AtomicReference` 存对象）。
 *   - 自旋的代价：高竞争下大量线程反复失败重试，**空转烧 CPU**。这是 `LongAdder` 出现的动机。
 *   - **LongAdder**：把一个计数器拆成 `Cell[]` 数组，不同线程更新不同 Cell（分散热点），求和时累加所有 Cell。**写快读略慢**，适合"高并发写、低频读"的统计场景。
 *   - `AtomicLong` vs `LongAdder`：前者单点 CAS，竞争激烈时退化；后者空间换时间，吞吐可高一个数量级。需要"实时精确读"时仍用 `AtomicLong`。
 *   - `LongAccumulator` 是 `LongAdder` 的泛化版，可自定义聚合函数（如求最大值、最小值）。
 *   - 原子引用：`AtomicReference<T>`（对象引用）、`AtomicMarkableReference`（引用 + 布尔标记）、`AtomicStampedReference`（引用 + int 版本）。
 *   - 字段更新器：`AtomicIntegerFieldUpdater` 可对 `volatile` 字段做原子更新，省去把字段改成原子类（省内存，适合大量对象的场景）。
 *   - 易错：以为 `volatile` 能替代原子类（`volatile` 只保证可见性，`i++` 仍非原子）；用 `AtomicInteger` 做"先检查再更新"的复合逻辑（应改用 `compareAndSet` 循环）；高并发下仍用 `AtomicLong` 计数。
 * ============================================================
 */

package com.heima.ch32;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicStampedReference;
import java.util.concurrent.atomic.LongAdder;

// ==================== 示例代码 ====================

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

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 用 `AtomicReference` 实现"无锁栈"（`push` 用 CAS 换头节点），说明它相比 `synchronized` 栈的优势与风险。
 * 2. 构造一个真实会失败的 ABA 场景（如栈的 `pop` 后节点被复用），说明为什么仅靠值比较不够。
 * 3. 对比 `LongAdder` 与 `AtomicLong` 在"1 写线程 + 1 读线程"下的读延迟，说明为什么读多场景不该用 `LongAdder`。
 * ============================================================
 */
