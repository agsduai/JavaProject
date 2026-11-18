/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：17-07
 * 课名：死锁、wait与notify
 * ------------------------------------------------------------
 * 知识点：
 *   - 死锁：两个或多个线程互相持有对方需要的锁，并且都不释放，程序永久阻塞。
 *   - 产生死锁的四个必要条件：互斥、持有并等待、不可抢占、循环等待；破坏任意一个即可避免。
 *   - 避免死锁的实用做法：所有线程按同一顺序获取锁、设置锁超时（`tryLock`）、减少锁的嵌套。
 *   - `wait()` 让当前线程释放锁并等待，`notify()` 唤醒一个等待线程，`notifyAll()` 唤醒全部。
 *   - 这三个方法必须写在 `synchronized` 块中，且调用者必须是锁对象，否则抛 `IllegalMonitorStateException`。
 *   - 易错：`wait` 应该放在 `while` 循环里判断条件，用 `if` 可能被"虚假唤醒"导致逻辑错误。
 * ============================================================
 */

package com.heima.ch17;

// ==================== 示例代码 ====================

class DeadlockWaitNotifySharedResource {
    private int data = 0;
    private boolean hasData = false;

    // 生产者：有数据就等待，没有就生产并通知
    public synchronized void produce(int value) throws InterruptedException {
        while (hasData) {           // 用 while 防止虚假唤醒
            wait();                 // 释放锁并等待
        }
        data = value;
        hasData = true;
        System.out.println("生产：" + data);
        notifyAll();                // 唤醒等待的消费者
    }

    // 消费者：没数据就等待，有就消费并通知
    public synchronized int consume() throws InterruptedException {
        while (!hasData) {
            wait();
        }
        hasData = false;
        System.out.println("消费：" + data);
        notifyAll();
        return data;
    }
}

public class DeadlockWaitNotify {
    public static void main(String[] args) throws InterruptedException {
        DeadlockWaitNotifySharedResource resource = new DeadlockWaitNotifySharedResource();

        Thread producer = new Thread(() -> {
            for (int i = 1; i <= 3; i++) {
                try {
                    resource.produce(i);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }, "生产者");

        Thread consumer = new Thread(() -> {
            for (int i = 1; i <= 3; i++) {
                try {
                    resource.consume();
                } catch (InterruptedException e) {
                    return;
                }
            }
        }, "消费者");

        producer.start();
        consumer.start();
        producer.join();
        consumer.join();
        System.out.println("生产消费完成");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 写一个死锁程序：线程 A 先锁 obj1 再锁 obj2，线程 B 先锁 obj2 再锁 obj1，运行后观察阻塞现象。
 * 2. 把上面的死锁改成"按固定顺序获取锁"，验证死锁被消除。
 * 3. 用 `wait`/`notifyAll` 实现两个线程交替打印奇数和偶数（1~20）。
 * ============================================================
 */
