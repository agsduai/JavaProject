/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：17-04
 * 课名：线程的常用方法
 * ------------------------------------------------------------
 * 知识点：
 *   - `getName()`/`setName()` 获取和设置线程名；`currentThread()` 获取当前线程对象。
 *   - `getPriority()`/`setPriority(int)` 设置优先级（1~10，默认 5），只是"建议"，不保证生效。
 *   - `sleep(long)` 休眠；`join()` 让调用线程等待该线程执行完毕；`join(long)` 最多等待指定毫秒。
 *   - `setDaemon(true)` 设为守护线程：主线程结束时守护线程自动结束，GC 线程就是守护线程。
 *   - `interrupt()` 中断线程：会让 `sleep`/`wait` 抛 `InterruptedException`，并清除中断标记。
 *   - 易错：`join` 是"谁调用谁等待"，`t.join()` 写在主线程里表示主线程等 t 结束。
 * ============================================================
 */

package com.heima.ch17;

// ==================== 示例代码 ====================

public class ThreadMethods {
    public static void main(String[] args) throws InterruptedException {
        Thread worker = new Thread(() -> {
            for (int i = 1; i <= 3; i++) {
                System.out.println(Thread.currentThread().getName() + " 工作中 " + i);
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    System.out.println("工作线程被中断，提前结束");
                    return;
                }
            }
            System.out.println("工作线程执行完毕");
        }, "工作线程");

        worker.setPriority(Thread.MAX_PRIORITY); // 10
        worker.start();

        // join：主线程等待 worker 结束
        worker.join();
        System.out.println("主线程在 worker 结束后才继续");

        // 守护线程：主线程结束它就结束
        Thread daemon = new Thread(() -> {
            while (true) {
                System.out.println("守护线程运行中...");
                try {
                    Thread.sleep(300);
                } catch (InterruptedException e) {
                    return;
                }
            }
        });
        daemon.setDaemon(true);
        daemon.start();

        // 中断演示
        Thread sleeper = new Thread(() -> {
            try {
                Thread.sleep(5000);
                System.out.println("睡满了 5 秒");
            } catch (InterruptedException e) {
                System.out.println("sleep 被打断：" + e.getMessage());
            }
        });
        sleeper.start();
        Thread.sleep(300);
        sleeper.interrupt(); // 打断 sleep

        Thread.sleep(500);
        System.out.println("main 即将结束，守护线程也会随之结束");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 创建两个线程，一个休眠 1 秒，一个不休眠，用 `join` 保证主线程在两个线程都结束后再打印"全部完成"。
 * 2. 创建 5 个线程分别设置不同优先级（1、3、5、7、10），运行后统计各自的执行次数，说明优先级是否绝对生效。
 * 3. 创建一个守护线程每秒打印一次心跳，主线程 2 秒后结束，验证守护线程会自动退出。
 * ============================================================
 */
