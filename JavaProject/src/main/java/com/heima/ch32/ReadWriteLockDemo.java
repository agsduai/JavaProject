/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：32-05
 * 课名：读写锁与 StampedLock
 * ------------------------------------------------------------
 * 知识点：
 *   - `ReentrantReadWriteLock`：**读读不互斥**、**读写互斥**、**写写互斥**。适合"读多写少"（如缓存、配置）。
 *   - 锁降级：持有写锁 → 获取读锁 → 释放写锁，可以保证"释放写锁后仍能读到自己的修改"。**锁升级（读→写）不支持**，会死锁。
 *   - 读写锁的公平性问题：非公平模式下，读锁可能不断插队导致写锁**饥饿**。`new ReentrantReadWriteLock(true)` 可缓解，但吞吐下降。
 *   - **StampedLock**（JDK 8+）：提供三种模式——**写锁**（`writeLock`）、**悲观读**（`readLock`）、**乐观读**（`tryOptimisticRead` + `validate`）。
 *   - 乐观读不加锁，读完用 `validate(stamp)` 校验"读期间有没有写"。没写就直接用结果，有写则退化成悲观读重试。适合"读极多、写极少"。
 *   - StampedLock 的坑：**不可重入**（同一线程重复 `writeLock` 会死锁）、**不支持 Condition**、`stamp` 用完必须配对释放。
 *   - 选择建议：读多写少且能容忍读锁阻塞写 → 读写锁；读远多于写且读操作很短 → StampedLock 乐观读；写多 → 直接 `synchronized` 或 `ReentrantLock`。
 *   - 读写锁的 `getReadLockCount()` / `getWriteHoldCount()` 可用于诊断"读锁泄漏"（读锁没释放会导致写锁永远拿不到）。
 *   - 易错：在持有读锁时去获取写锁（死锁）；StampedLock 的乐观读里做了耗时操作（validate 失败率高，白做）；把 `stamp` 变量复用导致校验错乱。
 * ============================================================
 */

package com.heima.ch32;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.StampedLock;

// ==================== 示例代码 ====================

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

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 在持有读锁的情况下尝试 `rw.writeLock().lock()`，观察是否死锁，并解释原因。
 * 2. 给 `StampedCache` 加一个"写时复制"版本（`volatile Map` + 写时 `new HashMap`），对比它和 StampedLock 的适用场景。
 * 3. 统计 10 读 1 写、100 读 1 写两种负载下，读写锁与 StampedLock 的耗时差异，说明结论。
 * ============================================================
 */
