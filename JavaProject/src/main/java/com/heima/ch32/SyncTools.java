/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：32-07
 * 课名：并发工具类：CountDownLatch、CyclicBarrier、Semaphore、Phaser
 * ------------------------------------------------------------
 * 知识点：
 *   - **CountDownLatch**：一次性倒计时。`await()` 等计数归零，`countDown()` 减一。**不能重置**，用完即废。典型用途：主线程等 N 个子任务全部完成、服务启动等待依赖就绪。
 *   - **CyclicBarrier**：可循环的栅栏。N 个线程互相等待，全部到达后一起放行，并可选执行一个 `barrierAction`。**可重置**（`reset()` 或自动进入下一轮）。典型用途：分片计算后合并、多轮迭代。
 *   - 二者区别：Latch 是"一个线程等多个线程"，计数只减不增、不可重用；Barrier 是"多个线程互相等"，可重复使用。
 *   - **Semaphore**：许可证模型，`acquire()` 拿许可、`release()` 还许可。用于**限流**（控制同时访问的线程数）和**资源池**（连接池、车位）。公平模式可保证 FIFO。
 *   - Semaphore 的坑：`release()` 不校验是否 `acquire()` 过，多调会凭空增加许可；`acquire()` 不放在 `try` 外，异常时许可泄漏。
 *   - **Phaser**：更灵活的多阶段同步器，支持**动态注册/注销**参与者、多阶段推进。`arriveAndAwaitAdvance()` 到达并等下一阶段，`arriveAndDeregister()` 到达后退出。
 *   - Phaser 适合"分阶段流水线"：阶段 1 全员准备 → 阶段 2 全员计算 → 阶段 3 全员汇总，且参与者数量可变。
 *   - `Exchanger`：两线程交换数据，`exchange(v)` 阻塞直到对方也调用。适合"双缓冲"场景。
 *   - 选择口诀：**等一批任务结束** → Latch；**多轮集合点** → Barrier；**限制并发数** → Semaphore；**动态多阶段** → Phaser；**两两交换** → Exchanger。
 *   - 易错：Latch 的 `countDown()` 写在 `finally` 外（异常时永久等待）；Barrier 的 `await()` 没设超时（某个线程挂了全体卡死）；Semaphore 用来做"互斥锁"却忘了公平性导致饥饿。
 * ============================================================
 */

package com.heima.ch32;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Exchanger;
import java.util.concurrent.Phaser;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

// ==================== 示例代码 ====================

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

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 用 `CountDownLatch` 实现"3 个任务并行执行，任一失败则整体失败"（提示：配合 `AtomicReference` 记录第一个异常）。
 * 2. 用 `Semaphore(1, true)` 实现互斥锁，说明它与 `ReentrantLock` 在可重入性上的差异。
 * 3. 把 Phaser 示例改成"阶段 2 只有 A、B 参与，C 提前退出"，用 `arriveAndDeregister` 实现并观察 `onAdvance` 的注册数变化。
 * ============================================================
 */
