/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：32-04
 * 课名：AQS 与 ReentrantLock
 * ------------------------------------------------------------
 * 知识点：
 *   - **AQS（AbstractQueuedSynchronizer）** 是 JUC 锁的骨架：一个 `volatile int state` 表示同步状态 + 一个 **CLH 双向队列**存放等待线程 + `CAS` 修改 state。
 *   - 两种模式：**独占**（`tryAcquire`/`tryRelease`，如 `ReentrantLock`）与**共享**（`tryAcquireShared`/`tryReleaseShared`，如 `Semaphore`、`CountDownLatch`）。
 *   - 加锁失败流程：`CAS` 改 state 失败 → 包装成 Node 入队 → `LockSupport.park()` 挂起 → 前驱释放时 `unpark()` 唤醒。唤醒后还要重新竞争，不是"排队就一定能拿到"。
 *   - `ReentrantLock` 的可重入靠 state 计数：同一线程再次 `lock()` 时 state 递增，`unlock()` 递减，减到 0 才真正释放。
 *   - **公平锁 vs 非公平锁**：公平锁在 `tryAcquire` 里先 `hasQueuedPredecessors()`，有前驱就排队；非公平锁直接抢，**吞吐更高但可能饿死**。默认非公平。
 *   - `lockInterruptibly()` 允许在等待时被中断（`InterruptedException`），`lock()` 不允许；`tryLock(timeout)` 可设等待上限，是避免死锁的常用手段。
 *   - `ReentrantLock` vs `synchronized`：前者可中断、可超时、可公平、可多条件队列（`newCondition`），后者由 JVM 优化、代码更简洁。JDK 6 之后两者性能接近，优先 `synchronized`，需要高级特性才用 `Lock`。
 *   - `Condition` 把"等待/通知"精确分组：`notFull.await()` / `notFull.signal()` 与 `notEmpty` 分开，避免 `notifyAll` 的无效唤醒。这就是 `ArrayBlockingQueue` 的实现方式。
 *   - 死锁四条件：互斥、持有并等待、不可剥夺、循环等待。破坏任一即可，工程上最常用"统一加锁顺序"。
 *   - 易错：`lock()` 后忘记在 `finally` 里 `unlock()`；`unlock()` 次数多于 `lock()`（抛 `IllegalMonitorStateException`）；用 `tryLock()` 但没判返回值。
 * ============================================================
 */

package com.heima.ch32;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

// ==================== 示例代码 ====================

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

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 给 `BoundedBuffer` 加 `offer(x, timeout)` 方法（队列满时等待指定时间后返回 `false`），用 `awaitNanos` 实现。
 * 2. 构造一个"两个线程按相反顺序加两把锁"的死锁，用 `jstack` 或 `ThreadMXBean.findDeadlockedThreads()` 检测出来。
 * 3. 说明为什么 `unlock()` 必须放在 `finally` 里，举一个不放导致锁泄漏的具体场景。
 * ============================================================
 */
