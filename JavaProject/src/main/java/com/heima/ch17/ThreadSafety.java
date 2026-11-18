/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：17-05
 * 课名：线程安全问题与synchronized
 * ------------------------------------------------------------
 * 知识点：
 *   - 线程安全问题：多个线程同时操作同一份共享数据，导致结果与预期不符（如超卖、少卖）。
 *   - 产生条件：多线程 + 共享数据 + 至少有一个线程在修改数据。
 *   - `synchronized` 同步代码块：`synchronized (锁对象) { ... }`，同一时刻只允许一个线程进入。
 *   - `synchronized` 修饰方法：锁对象是 `this`；修饰静态方法：锁对象是当前类的 `Class` 对象。
 *   - 锁对象必须是所有线程共享的同一个对象，`new Object()` 写在方法内部会失效。
 *   - 易错：锁的范围过大影响性能，过小起不到保护作用；用 `String` 常量当锁容易与别处冲突。
 * ============================================================
 */

package com.heima.ch17;

// ==================== 示例代码 ====================

class ThreadSafetyTicketSeller implements Runnable {
    private int tickets = 20;
    private final Object lock = new Object(); // 锁对象必须共享

    @Override
    public void run() {
        while (true) {
            // 同步代码块：同一时刻只有一个线程能进入
            synchronized (lock) {
                if (tickets <= 0) {
                    System.out.println("票已售完");
                    return;
                }
                try {
                    Thread.sleep(5); // 模拟检查余量的耗时
                } catch (InterruptedException e) {
                    return;
                }
                tickets--;
                System.out.println(Thread.currentThread().getName()
                        + " 卖出一张，剩余：" + tickets);
            }
        }
    }
}

public class ThreadSafety {
    public static void main(String[] args) throws InterruptedException {
        ThreadSafetyTicketSeller seller = new ThreadSafetyTicketSeller();
        Thread[] threads = new Thread[3];
        for (int i = 0; i < threads.length; i++) {
            threads[i] = new Thread(seller, "窗口" + (i + 1));
            threads[i].start();
        }
        for (Thread t : threads) {
            t.join();
        }
        System.out.println("售票结束，剩余票数应为 0");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 复现"超卖"问题：去掉 `synchronized`，用 3 个线程卖 20 张票，观察剩余票数是否变成负数，再修复。
 * 2. 用 `synchronized` 修饰实例方法实现两个线程交替存取同一个账户余额，保证不会出现负数。
 * 3. 解释为什么 `synchronized` 的锁对象不能写在方法内部 `new Object()`，并用代码验证结果。
 * ============================================================
 */
