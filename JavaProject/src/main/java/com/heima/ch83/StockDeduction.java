/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：83-06
 * 课名：库存扣减与防超卖
 * ------------------------------------------------------------
 * 知识点：
 *   - **超卖的根因不是「扣减慢」，而是「读-判-写」三步不是原子的**：**两个请求都读到库存 1，都判断「够」，都写 0，结果卖出 2 件**。
 *   - **扣减必须在存储层做原子操作**：**`update stock set qty = qty - 1 where sku = ? and qty >= 1`**，**靠影响行数判断是否成功**，**这是最可靠的一道闸**。
 *   - **内存里用 CAS 是同一思路**：**`compareAndSet(old, old - n)`，失败就重试**——**注意重试要有次数上限，否则高并发下会活锁**。
 *   - **分段库存（库存分桶）能降低热点**：**把 100 件拆成 4 段各 25 件，请求按 hash 落到某段**——**总成功数不变，但落在同一段上的并发请求从 200 降到约 50，冲突面按段数线性下降**。
 *   - **预扣与实扣要分开**：**下单预扣、支付实扣、超时回补**——**预扣不实扣，库存会被未付款订单长期占用**，**要有超时回补任务**。
 *   - 易错点：先查库存再扣（中间有时间窗）；扣减失败却已经把订单落库；回补时忘了幂等导致库存越补越多；分段库存的段容量写死导致某段先空、整体还有货。
 * ============================================================
 */

package com.heima.ch83;

// ==================== 示例代码 ====================

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class StockDeduction {
    // 朴素库存：读-判-写，三步之间没有原子性
    // 用一道栅栏把「读」和「写」拆开：所有线程先读完，再一起写，
    // 把真实环境里几十微秒的并发窗口放大成必然发生
    static class NaiveStock {
        long qty;
        final int expectReaders;
        final AtomicInteger readers = new AtomicInteger();

        NaiveStock(long qty, int expectReaders) {
            this.qty = qty;
            this.expectReaders = expectReaders;
        }

        boolean deduct() {
            long cur = qty;                      // 第 1 步：读
            if (cur <= 0) {
                return false;
            }
            readers.incrementAndGet();
            while (readers.get() < expectReaders) {   // 等所有线程都读完
                Thread.onSpinWait();
            }
            qty = cur - 1;                       // 第 3 步：写（覆盖别人的写）
            return true;
        }
    }

    // CAS 库存：比较并交换，失败重试
    static class CasStock {
        final AtomicLong qty;
        final AtomicInteger retries = new AtomicInteger();

        CasStock(long qty) {
            this.qty = new AtomicLong(qty);
        }

        boolean deduct() {
            while (true) {
                long cur = qty.get();
                if (cur <= 0) {
                    return false;
                }
                if (qty.compareAndSet(cur, cur - 1)) {
                    return true;
                }
                retries.incrementAndGet();
            }
        }
    }

    // 分段库存：按 hash 落到某一段，段内再 CAS
    static class SegmentedStock {
        final CasStock[] segs;

        SegmentedStock(long total, int segCount) {
            segs = new CasStock[segCount];
            long per = total / segCount;
            long extra = total % segCount;
            for (int i = 0; i < segCount; i++) {
                segs[i] = new CasStock(per + (i < extra ? 1 : 0));
            }
        }

        boolean deduct(String key) {
            int idx = Math.floorMod(key.hashCode(), segs.length);
            // 本段空了就试下一段，避免「某段空、整体还有货」
            for (int i = 0; i < segs.length; i++) {
                if (segs[(idx + i) % segs.length].deduct()) {
                    return true;
                }
            }
            return false;
        }

        long left() {
            long s = 0;
            for (CasStock c : segs) {
                s += c.qty.get();
            }
            return s;
        }
    }

    static int runConcurrent(int threads, java.util.function.IntConsumer task) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger ok = new AtomicInteger();
        for (int i = 0; i < threads; i++) {
            final int id = i;
            new Thread(() -> {
                try {
                    start.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                try {
                    task.accept(id);
                } finally {
                    done.countDown();
                }
            }).start();
        }
        start.countDown();
        done.await();
        return ok.get();
    }

    public static void main(String[] args) throws Exception {
        int stock = 100;
        int buyers = 200;

        System.out.println("【1. 朴素写法：库存 100，200 人各买 1 件】");
        NaiveStock naive = new NaiveStock(stock, buyers);
        AtomicInteger naiveOk = new AtomicInteger();
        runConcurrent(buyers, id -> {
            if (naive.deduct()) {
                naiveOk.incrementAndGet();
            }
        });
        System.out.println("  用栅栏强制「200 个线程先全部读完，再一起写」，把微秒级窗口放大成必然");
        System.out.printf("  卖出 %d 件，库存变量停在 %d，超卖 %d 件%n",
                naiveOk.get(), naive.qty, naiveOk.get() - stock);
        System.out.printf("  %d 次写入互相覆盖，只剩最后一次生效：这就是「读-判-写」不是原子的代价%n",
                naiveOk.get() - (stock - naive.qty));
        System.out.println("  注意超卖不是「扣慢了」，而是「判断和扣减之间有缝」——把窗口放大到必然发生，才看得见。");

        System.out.println();
        System.out.println("【2. CAS 写法：同样的 200 人】");
        CasStock cas = new CasStock(stock);
        AtomicInteger casOk = new AtomicInteger();
        runConcurrent(buyers, id -> {
            if (cas.deduct()) {
                casOk.incrementAndGet();
            }
        });
        System.out.printf("  卖出 %d 件，剩余库存 %d，超卖 %d 件%n",
                casOk.get(), cas.qty.get(), Math.max(0, casOk.get() - stock));
        System.out.printf("  CAS 失败重试 %d 次（说明确实在竞争，但结果是对的）%n", cas.retries.get());
        System.out.println("  成功次数恒等于库存数，这是防超卖的核心：让「判断 + 扣减」变成一个原子操作。");

        System.out.println();
        System.out.println("【3. 分段库存：100 件拆 4 段】");
        SegmentedStock seg = new SegmentedStock(stock, 4);
        for (int i = 0; i < 4; i++) {
            System.out.printf("  段 %d 初始 %d 件%n", i, seg.segs[i].qty.get());
        }
        // 先算清「竞争面」：200 个 key 按同一套 hash 落到 4 段，各段分到多少
        int[] bucket = new int[4];
        for (int i = 0; i < buyers; i++) {
            bucket[Math.floorMod(("user-" + i).hashCode(), 4)]++;
        }
        System.out.printf("  200 个请求按 hash 落到 4 段：%d / %d / %d / %d%n",
                bucket[0], bucket[1], bucket[2], bucket[3]);
        long singlePairs = (long) buyers * (buyers - 1) / 2;
        long segPairs = 0;
        for (int n : bucket) {
            segPairs += (long) n * (n - 1) / 2;
        }
        System.out.printf("  同一把锁上的潜在冲突对数：单段 %d 对 -> 四段合计 %d 对（约 1/%d）%n",
                singlePairs, segPairs, singlePairs / Math.max(1, segPairs));

        AtomicInteger segOk = new AtomicInteger();
        runConcurrent(buyers, id -> {
            if (seg.deduct("user-" + id)) {
                segOk.incrementAndGet();
            }
        });
        System.out.printf("  卖出 %d 件，剩余 %d 件，超卖 %d 件（总成功数不受分段影响）%n",
                segOk.get(), seg.left(), Math.max(0, segOk.get() - stock));
        int totalRetry = 0;
        for (CasStock c : seg.segs) {
            totalRetry += c.retries.get();
        }
        System.out.printf("  四段实际 CAS 失败合计 %d 次（本次运行观测值，随线程调度浮动）%n", totalRetry);
        System.out.println("  真正的收益是结构性的：每段只有约 1/4 的请求在抢，冲突面按段数线性摊薄；");
        System.out.println("  重试次数是观测值不能当结论，冲突对数才是可复现的口径。");

        System.out.println();
        System.out.println("【4. 预扣 / 实扣 / 回补 的三段式】");
        long stockQty = 10;
        long reserved = 3;
        System.out.printf("  初始库存 %d，下单预扣 %d -> 可售 %d%n", stockQty, reserved, stockQty - reserved);
        System.out.printf("  支付成功实扣 %d -> 可售 %d（预扣转为实扣，总量不变）%n", reserved, stockQty - reserved);
        System.out.printf("  另一单超时回补 %d -> 可售 %d%n", 2, stockQty - reserved + 2);
        System.out.println("  预扣不实扣、超时不回补，库存就会被未付款订单长期占死。");

        System.out.println();
        System.out.println("【5. 回补必须幂等，否则越补越多】");
        System.out.println("  先看不做幂等：同一订单 order-1 的回补请求重投 3 次");
        long buggy = 5;
        for (int i = 1; i <= 3; i++) {
            buggy = buggy + 1;
            System.out.printf("    第 %d 次回补 -> 库存 %d%n", i, buggy);
        }
        System.out.printf("  库存从 5 涨到 %d，凭空多出 %d 件——这 2 件就是「无中生有」的货%n", buggy, buggy - 6);

        System.out.println();
        System.out.println("  再看带幂等键：同一 order-1 只认第一次");
        List<String> rolledBack = new ArrayList<>();
        long q = 5;
        for (int i = 1; i <= 3; i++) {
            long before = q;
            q = rollback(q, rolledBack, "order-1");
            System.out.printf("    第 %d 次回补 -> 库存 %d%s%n", i, q, q == before ? "（重复请求，跳过）" : "");
        }
        System.out.printf("  库存停在 %d，只加了一次%n", q);
    }

    static long rollback(long qty, List<String> done, String orderId) {
        if (done.contains(orderId)) {
            return qty;
        }
        done.add(orderId);
        return qty + 1;
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 把线程数从 200 改成 2000，观察 CAS 重试次数的量级变化，说明为什么高并发下要限流。
 * 2. 把分段数从 4 改成 16，验证总成功数仍为 100，并说明段数上限由什么决定。
 * 3. 思考题：如果库存扣减成功但订单落库失败，怎么保证不丢库存？给出补偿方案与幂等键设计。
 * ============================================================
 */

