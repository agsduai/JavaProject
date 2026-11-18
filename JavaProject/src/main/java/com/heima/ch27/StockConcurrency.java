/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：27-06
 * 课名：事务与并发扣库存
 * ------------------------------------------------------------
 * 知识点：
 *   - 事务的四个特性：原子性（要么全成功要么全回滚）、一致性（约束不被破坏）、隔离性（并发互不干扰）、持久性（提交后不丢）。
 *   - 事务边界放在 Service：`@Transactional` 标在"一个完整业务用例"的方法上，不要标在 Repository 的单条 SQL 上（那样多步操作就不在同一个事务里了）。
 *   - 超卖的根因是"读-改-写"三步不原子：两个线程同时读到 `stock=1`，都判断"够"，都写回 `0`，结果卖出 2 件。加 `synchronized` 只在单机有效，多实例部署立刻失效。
 *   - 三种正确做法：
 *   - 数据库原子更新：`UPDATE item SET stock = stock - ? WHERE id = ? AND stock >= ?`，看影响行数判断是否成功。最简单、最推荐。
 *   - 乐观锁：表加 `version` 字段，`UPDATE ... SET stock=?, version=version+1 WHERE id=? AND version=?`，影响 0 行说明被别人改过，重试。
 *   - 悲观锁：`SELECT ... FOR UPDATE`，锁住行再改。适合冲突激烈的场景，但容易锁等待、死锁。
 *   - 隔离级别：读未提交 / 读已提交 / 可重复读（MySQL 默认）/ 串行化。级别越高并发越差；`可重复读` 靠 MVCC + 间隙锁解决大部分幻读。
 *   - 死锁预防：多个资源按固定顺序加锁（永远先锁 id 小的）；事务尽量短；`UPDATE` 走主键或索引，避免锁全表。
 *   - 长事务的代价：持有行锁时间长、undo log 膨胀、连接池被占满。远程调用、发消息、写文件绝不能放在事务里。
 *   - 易错：同类内部 `this.method()` 调用带 `@Transactional` 的方法，事务不生效（没走代理）；异常被 `catch` 住没重新抛出，事务不回滚（默认只回滚 `RuntimeException`）。
 * ============================================================
 */

package com.heima.ch27;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

// ==================== 示例代码 ====================

public class StockConcurrency {
    static final int STOCK = 100;
    static final int THREADS = 20;
    static final int PER_THREAD = 10;

    public static void main(String[] args) throws Exception {
        System.out.println("库存 " + STOCK + "，线程 " + THREADS + "，每人请求 " + PER_THREAD
                + " 次，总请求 " + (THREADS * PER_THREAD));

        System.out.println();
        System.out.println("=== 方案一：先查库存再扣（错误写法）===");
        int[] naive = runNaive();
        System.out.println("  卖出=" + naive[0] + " 剩余=" + naive[1]
                + (naive[0] > STOCK ? "  <-- 超卖 " + (naive[0] - STOCK) + " 件" : "  <-- 恰好没超卖，纯属运气"));

        System.out.println();
        System.out.println("=== 方案二：数据库原子更新（推荐）===");
        int[] atomic = runAtomic();
        System.out.println("  卖出=" + atomic[0] + " 剩余=" + atomic[1]
                + (atomic[0] == STOCK ? "  <-- 正确" : "  <-- 有问题"));

        System.out.println();
        System.out.println("=== 方案三：乐观锁 version + 重试 ===");
        int[] opt = runOptimistic();
        System.out.println("  卖出=" + opt[0] + " 剩余=" + opt[1] + " 版本冲突次数=" + opt[2]
                + (opt[0] == STOCK ? "  <-- 正确" : "  <-- 有问题"));

        System.out.println();
        System.out.println("=== 为什么 synchronized 不够 ===");
        System.out.println("  单机 20 线程：synchronized 有效");
        System.out.println("  两台机器各 20 线程：两把锁互不相干，照样超卖");
        System.out.println("  所以并发控制要落到数据库（行锁/CAS）或 Redis 上");

        System.out.println();
        System.out.println("=== 事务回滚演示：扣库存成功但建订单失败 ===");
        TransactionDemo.run();
    }

    /** 模拟"读出来 -> 处理一会儿 -> 写回去"，拉大竞态窗口 */
    static void busyWait(long nanos) {
        long end = System.nanoTime() + nanos;
        while (System.nanoTime() < end) {
            Thread.onSpinWait();
        }
    }

    static int[] runNaive() throws Exception {
        AtomicInteger stock = new AtomicInteger(STOCK);
        AtomicInteger sold = new AtomicInteger();
        runAll(() -> {
            if (stock.get() > 0) {
                busyWait(50_000);          // 查完到写回之间的空档
                stock.decrementAndGet();
                sold.incrementAndGet();
            }
        });
        return new int[]{sold.get(), stock.get()};
    }

    static int[] runAtomic() throws Exception {
        AtomicInteger stock = new AtomicInteger(STOCK);
        AtomicInteger sold = new AtomicInteger();
        runAll(() -> {
            if (tryDeduct(stock)) {
                sold.incrementAndGet();
            }
        });
        return new int[]{sold.get(), stock.get()};
    }

    /** 等价于 UPDATE item SET stock = stock - 1 WHERE id = ? AND stock >= 1 */
    static boolean tryDeduct(AtomicInteger stock) {
        while (true) {
            int cur = stock.get();
            if (cur <= 0) {
                return false;
            }
            if (stock.compareAndSet(cur, cur - 1)) {
                return true;
            }
        }
    }

    static int[] runOptimistic() throws Exception {
        OptimisticRow row = new OptimisticRow(STOCK);
        AtomicInteger sold = new AtomicInteger();
        runAll(() -> {
            if (row.deduct()) {
                sold.incrementAndGet();
            }
        });
        return new int[]{sold.get(), row.stock(), row.conflicts()};
    }

    /** 一行记录：stock + version 打包成一个不可变数组，CAS 整体替换 */
    static class OptimisticRow {
        private final AtomicReference<int[]> state;
        private final AtomicInteger conflicts = new AtomicInteger();

        OptimisticRow(int stock) {
            state = new AtomicReference<>(new int[]{stock, 0});
        }

        boolean deduct() {
            while (true) {
                int[] cur = state.get();
                if (cur[0] <= 0) {
                    return false;
                }
                int[] next = new int[]{cur[0] - 1, cur[1] + 1};
                if (state.compareAndSet(cur, next)) {
                    return true;
                }
                conflicts.incrementAndGet();
            }
        }

        int stock() {
            return state.get()[0];
        }

        int conflicts() {
            return conflicts.get();
        }
    }

    interface Task {
        void run();
    }

    static void runAll(Task task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch gate = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(THREADS * PER_THREAD);
        for (int i = 0; i < THREADS * PER_THREAD; i++) {
            pool.execute(() -> {
                try {
                    gate.await();
                    task.run();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        gate.countDown();
        done.await(30, TimeUnit.SECONDS);
        pool.shutdown();
        pool.awaitTermination(5, TimeUnit.SECONDS);
    }

    /** 事务：记录 undo 日志，失败时倒序回滚 */
    static class TransactionDemo {
        static void run() {
            int[] stock = {10};                 // 用数组装，才能被 lambda 捕获
            java.util.List<Runnable> undo = new java.util.ArrayList<>();
            try {
                // 1. 扣库存
                stock[0]--;
                undo.add(() -> {
                    stock[0]++;
                    System.out.println("    rollback: stock 恢复为 " + stock[0]);
                });
                System.out.println("  扣库存后 stock=" + stock[0]);

                // 2. 建订单（这里故意抛异常，模拟唯一索引冲突）
                undo.add(() -> System.out.println("    rollback: delete order ORD20261117001"));
                throw new IllegalStateException("Duplicate entry 'ORD20261117001' for key 'uk_order_no'");
            } catch (RuntimeException e) {
                System.out.println("  捕获异常: " + e.getMessage());
                for (int i = undo.size() - 1; i >= 0; i--) {
                    undo.get(i).run();
                }
                System.out.println("  事务已回滚，stock=" + stock[0] + "，订单未创建");
            }
        }
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. `UPDATE item SET stock = stock - 1 WHERE id = ? AND stock >= 1` 为什么天然不会超卖？从数据库行锁和影响行数两个角度解释。
 * 2. 乐观锁在什么场景下会退化成"大量重试、CPU 打满"？给出两种缓解办法。
 * 3. 一个下单方法里先扣库存、再调支付网关、再发短信，为什么要把后两步移出事务？如果支付成功了但本地事务回滚了，怎么处理？
 * ============================================================
 */
