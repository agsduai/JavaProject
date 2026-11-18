/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：17-08
 * 课名：线程池
 * ------------------------------------------------------------
 * 知识点：
 *   - 线程池复用已创建的线程，避免频繁创建销毁的开销，同时能控制并发数量。
 *   - `Executors` 工厂方法：`newFixedThreadPool(n)` 固定数量、`newCachedThreadPool()` 按需扩容、`newSingleThreadExecutor()` 单线程。
 *   - `ExecutorService.submit(Callable)` 提交任务并返回 `Future`，`future.get()` 阻塞获取结果。
 *   - `execute(Runnable)` 无返回值；`shutdown()` 不再接受新任务但执行完已提交的，`shutdownNow()` 立即中断。
 *   - 底层是 `ThreadPoolExecutor`：核心线程数、最大线程数、空闲存活时间、任务队列、拒绝策略。
 *   - 易错：`newFixedThreadPool` 的任务队列无界，任务积压可能 OOM；生产环境推荐手动 `new ThreadPoolExecutor` 指定队列容量。
 * ============================================================
 */

package com.heima.ch17;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

// ==================== 示例代码 ====================

public class ThreadPoolDemo {
    public static void main(String[] args) throws InterruptedException, ExecutionException {
        // 固定 3 个线程的线程池
        ExecutorService pool = Executors.newFixedThreadPool(3);

        // execute：提交无返回值的任务
        for (int i = 1; i <= 5; i++) {
            final int taskId = i;
            pool.execute(() -> {
                System.out.println(Thread.currentThread().getName()
                        + " 执行任务 " + taskId);
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        // submit + Callable：提交有返回值的任务
        List<Future<Integer>> futures = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            final int n = i;
            Callable<Integer> task = () -> {
                int sum = 0;
                for (int j = 1; j <= n * 10; j++) {
                    sum += j;
                }
                return sum;
            };
            futures.add(pool.submit(task));
        }
        for (Future<Integer> future : futures) {
            System.out.println("计算结果：" + future.get()); // 阻塞等待结果
        }

        // 关闭线程池
        pool.shutdown();
        if (!pool.awaitTermination(2, TimeUnit.SECONDS)) {
            pool.shutdownNow();
        }
        System.out.println("线程池已关闭：" + pool.isShutdown());
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 用固定大小为 4 的线程池提交 10 个任务，每个任务打印线程名和任务编号，观察线程是否被复用。
 * 2. 用 `submit` 提交 5 个 `Callable` 任务计算 1~n 的和，收集所有结果并输出总和。
 * 3. 手动创建 `ThreadPoolExecutor`，指定核心线程数 2、最大线程数 4、队列容量 2，提交 8 个任务，观察拒绝策略的触发。
 * ============================================================
 */
