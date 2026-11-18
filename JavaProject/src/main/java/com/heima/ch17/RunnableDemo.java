/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：17-03
 * 课名：实现Runnable接口创建线程
 * ------------------------------------------------------------
 * 知识点：
 *   - 创建线程方式二：定义类实现 `Runnable` 接口，重写 `run()`，再交给 `Thread` 执行：`new Thread(runnable).start()`。
 *   - 优点：只实现接口，还能继承其他类；多个线程可以共享同一个 `Runnable` 对象的数据。
 *   - `Thread` 构造方法可同时传入 `Runnable` 和线程名：`new Thread(r, "线程名")`。
 *   - 匿名内部类/Lambda 写法：`new Thread(() -> {...}).start()`，代码最简洁。
 *   - 方式三 `Callable` + `FutureTask` 可以拿到返回值，后面线程池会用到。
 *   - 易错：`Runnable` 的 `run()` 没有返回值、不能抛受检异常，需要返回结果时选 `Callable`。
 * ============================================================
 */

package com.heima.ch17;

// ==================== 示例代码 ====================

class RunnableDemoTicketTask implements Runnable {
    // 共享数据：多个线程共用同一个 Runnable 对象时，这份数据是共享的
    private int tickets = 10;

    @Override
    public void run() {
        while (true) {
            if (tickets <= 0) {
                break;
            }
            System.out.println(Thread.currentThread().getName() + " 卖出第 " + tickets + " 张票");
            tickets--;
            try {
                Thread.sleep(10); // 模拟出票耗时
            } catch (InterruptedException e) {
                return;
            }
        }
    }
}

public class RunnableDemo {
    public static void main(String[] args) {
        // 方式二：实现 Runnable 接口
        RunnableDemoTicketTask task = new RunnableDemoTicketTask();
        Thread t1 = new Thread(task, "窗口1");
        Thread t2 = new Thread(task, "窗口2");
        t1.start();
        t2.start();

        // Lambda 写法：适合简单的任务
        Thread lambdaThread = new Thread(() -> {
            for (int i = 1; i <= 3; i++) {
                System.out.println("Lambda 线程：" + i);
            }
        }, "Lambda线程");
        lambdaThread.start();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 用实现 `Runnable` 的方式创建 3 个线程，共同打印 1~30 的数字，观察数字是否重复或遗漏。
 * 2. 用 Lambda 写法创建线程，循环打印 5 次当前线程名和当前时间戳。
 * 3. 比较继承 `Thread` 和实现 `Runnable` 两种方式的优缺点，各写出一个适用场景。
 * ============================================================
 */
