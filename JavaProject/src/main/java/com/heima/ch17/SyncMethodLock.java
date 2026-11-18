/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：17-06
 * 课名：同步方法与Lock锁
 * ------------------------------------------------------------
 * 知识点：
 *   - 同步方法：把 `synchronized` 写在方法声明上，锁对象是 `this`（静态方法则是类对象）。
 *   - `Lock` 是 JDK5 引入的接口，`ReentrantLock` 是最常用实现，比 `synchronized` 更灵活。
 *   - `lock()` 加锁、`unlock()` 解锁；解锁必须放在 `finally` 中，保证异常时也能释放锁。
 *   - `tryLock()` 尝试加锁，立即返回 `boolean`，可避免无限等待；`tryLock(timeout, unit)` 可设置等待时间。
 *   - 相比 `synchronized`，`Lock` 支持公平锁（`new ReentrantLock(true)`）、可中断、可超时。
 *   - 易错：忘记 `unlock()` 会导致死锁；`lock()` 与 `unlock()` 次数必须配对。
 * ============================================================
 */

package com.heima.ch17;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

// ==================== 示例代码 ====================

class SyncMethodLockAccount {
    private int balance = 1000;
    private final Lock lock = new ReentrantLock();

    // 同步方法：锁对象是 this
    public synchronized void withdrawSync(int amount) {
        if (balance >= amount) {
            balance -= amount;
            System.out.println(Thread.currentThread().getName()
                    + " 取款 " + amount + "，余额：" + balance);
        } else {
            System.out.println(Thread.currentThread().getName() + " 余额不足");
        }
    }

    // Lock 锁：更灵活，必须在 finally 中解锁
    public void withdrawLock(int amount) {
        lock.lock();
        try {
            if (balance >= amount) {
                balance -= amount;
                System.out.println(Thread.currentThread().getName()
                        + " 取款 " + amount + "，余额：" + balance);
            } else {
                System.out.println(Thread.currentThread().getName() + " 余额不足");
            }
        } finally {
            lock.unlock(); // 无论是否异常都要解锁
        }
    }

    // tryLock：拿不到锁就放弃，避免死等
    public boolean tryWithdraw(int amount) {
        if (lock.tryLock()) {
            try {
                if (balance >= amount) {
                    balance -= amount;
                    return true;
                }
                return false;
            } finally {
                lock.unlock();
            }
        }
        System.out.println(Thread.currentThread().getName() + " 没抢到锁，稍后再试");
        return false;
    }

    public int getBalance() {
        return balance;
    }
}

public class SyncMethodLock {
    public static void main(String[] args) throws InterruptedException {
        SyncMethodLockAccount account = new SyncMethodLockAccount();
        Thread t1 = new Thread(() -> account.withdrawLock(300), "张三");
        Thread t2 = new Thread(() -> account.withdrawLock(300), "李四");
        Thread t3 = new Thread(() -> account.withdrawLock(600), "王五");

        t1.start();
        t2.start();
        t3.start();
        t1.join();
        t2.join();
        t3.join();

        System.out.println("最终余额：" + account.getBalance());
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 分别用同步方法和 `ReentrantLock` 实现一个计数器，各启动 10 个线程累加 1000 次，比较最终结果是否正确。
 * 2. 写程序演示"忘记 `unlock` 导致死锁"：故意在加锁后抛异常且不解锁，观察另一个线程被永久阻塞。
 * 3. 用 `tryLock` 实现一个限时抢购：3 个线程抢 1 个名额，抢不到锁的线程直接提示"稍后再试"。
 * ============================================================
 */
