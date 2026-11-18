/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：32-09
 * 课名：线程协作：wait/notify、BlockingQueue 与 ThreadLocal
 * ------------------------------------------------------------
 * 知识点：
 *   - `wait()` / `notify()` 必须在**持有该对象监视器**（`synchronized`）时调用，否则抛 `IllegalMonitorStateException`。`wait()` 会释放锁并挂起，被唤醒后要重新抢锁。
 *   - **必须用 `while` 而不是 `if` 包住 `wait()`**：被唤醒不代表条件成立（可能是虚假唤醒，也可能被别人抢先消费）。
 *   - `notify()` 只唤醒一个（随机），`notifyAll()` 唤醒全部。用 `if` + `notify` 的组合是经典 bug 源。
 *   - `BlockingQueue` 已经封装好了"锁 + 条件 + 循环判断"，生产代码优先用它而不是手写 `wait/notify`。
 *   - 常用实现：`ArrayBlockingQueue`（有界数组，单锁双条件）、`LinkedBlockingQueue`（链表，默认无界）、`SynchronousQueue`（容量 0，直接交接）、`PriorityBlockingQueue`（优先级）、`DelayQueue`（延迟到期才可取）。
 *   - `BlockingQueue` 的四组方法：抛异常（`add`/`remove`/`element`）、返回特殊值（`offer`/`poll`/`peek`）、阻塞（`put`/`take`）、超时（`offer(e,t,u)`/`poll(t,u)`）。
 *   - `ThreadLocal` 为每个线程保存独立副本，底层是 `Thread` 里的 `ThreadLocalMap`（key 是 `ThreadLocal` 的**弱引用**，value 是强引用）。
 *   - **ThreadLocal 内存泄漏**：key 被 GC 后变成 `null`，但 value 仍被 `ThreadLocalMap` 强引用，且线程池的线程长期存活 → value 无法回收。**必须 `remove()`**，尤其在 `finally` 里。
 *   - `ThreadLocal` 的正确用法：请求级上下文（`traceId`、`userId`）、非线程安全对象的每线程实例（`SimpleDateFormat`）。用完必须清理，线程池场景尤其重要。
 *   - `InheritableThreadLocal` 能把值传给子线程，但**线程池里复用线程不会重新继承**，跨线程传递应改用显式参数或 `TransmittableThreadLocal` 类方案。
 *   - 易错：`wait()` 没在 `synchronized` 块里；`if` 代替 `while`；`ThreadLocal` 用完不 `remove`；用 `ThreadLocal` 存大对象（每个线程一份，内存翻倍）。
 * ============================================================
 */

package com.heima.ch32;

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

// ==================== 示例代码 ====================

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

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 把 `HandmadeQueue` 的 `while` 改成 `if`，构造"两个消费者 + 一个生产者"的场景，尝试复现错误（提示：可用 `notifyAll` 唤醒两个消费者）。
 * 2. 用 `DelayQueue` 实现"订单 30 分钟未支付自动关闭"，说明为什么它比 `Timer` 更适合（提示：`Timer` 单线程、任务异常会终止整个定时器）。
 * 3. 写一个 `ThreadLocal` 泄漏的复现程序（用弱引用观察 key 被回收），并说明为什么线程池场景必须 `remove`。
 * ============================================================
 */
