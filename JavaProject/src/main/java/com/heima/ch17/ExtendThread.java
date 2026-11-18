/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：17-02
 * 课名：继承Thread类创建线程
 * ------------------------------------------------------------
 * 知识点：
 *   - 创建线程方式一：定义类继承 `Thread`，重写 `run()` 方法，创建对象后调用 `start()` 启动。
 *   - `start()` 的作用：启动新线程，由 JVM 调用 `run()`；直接调用 `run()` 只是普通方法调用，仍在当前线程执行。
 *   - 线程执行顺序由 CPU 调度决定，每次运行结果可能不同，这是多线程的"随机性"。
 *   - `Thread.sleep(long millis)` 让当前线程休眠指定毫秒，需要处理 `InterruptedException`。
 *   - 缺点：Java 单继承，继承了 `Thread` 就不能再继承其他类，扩展性差。
 *   - 易错：同一个 `Thread` 对象不能 `start()` 两次，第二次会抛 `IllegalThreadStateException`。
 * ============================================================
 */

package com.heima.ch17;

// ==================== 示例代码 ====================

class ExtendThreadMyThread extends Thread {
    public ExtendThreadMyThread(String name) {
        super(name); // 设置线程名
    }

    @Override
    public void run() {
        for (int i = 1; i <= 3; i++) {
            System.out.println(getName() + " 执行第 " + i + " 次");
            try {
                Thread.sleep(100); // 休眠 100ms，让出 CPU
            } catch (InterruptedException e) {
                System.out.println(getName() + " 被中断");
                return;
            }
        }
    }
}

public class ExtendThread {
    public static void main(String[] args) {
        ExtendThreadMyThread t1 = new ExtendThreadMyThread("线程A");
        ExtendThreadMyThread t2 = new ExtendThreadMyThread("线程B");

        t1.start(); // 启动新线程，JVM 内部调用 run
        t2.start();

        // 主线程继续执行，与 t1/t2 并发
        for (int i = 1; i <= 3; i++) {
            System.out.println("main 执行第 " + i + " 次");
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                return;
            }
        }

        System.out.println("main 结束，但线程A/B 可能还在运行");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 定义 `PrintThread` 继承 `Thread`，循环打印 1~100 的偶数，创建两个线程同时运行，观察输出是否交替。
 * 2. 写程序演示 `start()` 和 `run()` 的区别：分别调用两者，说明输出结果差异的原因。
 * 3. 创建 3 个线程分别打印 A、B、C 各 10 次，用 `sleep` 控制节奏，观察调度顺序。
 * ============================================================
 */
