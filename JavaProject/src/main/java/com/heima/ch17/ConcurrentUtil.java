/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：17-09
 * 课名：并发工具类与原子类
 * ------------------------------------------------------------
 * 知识点：
 *   - `AtomicInteger`/`AtomicLong` 基于 CAS（比较并交换）实现无锁线程安全，`incrementAndGet()` 是原子自增。
 *   - `ConcurrentHashMap` 是线程安全的 Map，采用分段/CAS 优化，读操作几乎无锁，性能远高于 `Collections.synchronizedMap`。
 *   - `CopyOnWriteArrayList` 写时复制：写操作加锁复制新数组，读操作无锁，适合读多写少。
 *   - `CountDownLatch` 倒计时门闩：`countDown()` 减一，`await()` 等计数归零。
 *   - `Semaphore` 信号量控制同时访问的线程数；`CyclicBarrier` 让一组线程互相等待到齐。
 *   - 易错：`i++` 不是原子操作（读-改-写三步），多线程下必须用原子类或加锁。
 * ============================================================
 */

package com.heima.ch17;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

// ==================== 示例代码 ====================

public class ConcurrentUtil {
    public static void main(String[] args) throws InterruptedException {
        int threadCount = 10;
        int loop = 1000;

        // 普通 int：多线程累加结果不对
        int[] normal = {0};
        // 原子类：CAS 保证原子性
        AtomicInteger atomic = new AtomicInteger(0);

        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            pool.execute(() -> {
                for (int j = 0; j < loop; j++) {
                    normal[0]++;
                    atomic.incrementAndGet();
                }
                latch.countDown(); // 计数减一
            });
        }

        latch.await(); // 等待所有线程执行完
        System.out.println("普通 int 结果：" + normal[0] + "（期望 " + threadCount * loop + "）");
        System.out.println("AtomicInteger 结果：" + atomic.get());

        // ConcurrentHashMap：并发安全的 Map
        ConcurrentHashMap<String, Integer> map = new ConcurrentHashMap<>();
        map.put("a", 1);
        map.merge("a", 1, Integer::sum);
        System.out.println("ConcurrentHashMap：" + map);

        // CopyOnWriteArrayList：读多写少场景
        CopyOnWriteArrayList<String> cowList = new CopyOnWriteArrayList<>();
        cowList.add("A");
        cowList.add("B");
        System.out.println("CopyOnWriteArrayList：" + cowList);

        // 对照：普通 ArrayList 在多线程写入下会丢数据
        List<Integer> unsafeList = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            pool.execute(() -> {
                for (int j = 0; j < 100; j++) {
                    unsafeList.add(j);
                }
            });
        }

        pool.shutdown();
        pool.awaitTermination(3, java.util.concurrent.TimeUnit.SECONDS);
        System.out.println("普通 ArrayList 大小：" + unsafeList.size() + "（期望 1000，可能更小）");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 用 `AtomicInteger` 和 `synchronized` 两种方式各实现一个线程安全计数器，10 个线程各加 10000 次，比较结果与耗时。
 * 2. 用 `CountDownLatch` 模拟"等待 5 个下载任务全部完成后再合并文件"。
 * 3. 用 `Semaphore` 限制最多 3 个线程同时访问打印资源，共提交 10 个任务，观察并发数是否受控。
 * ============================================================
 */

/*
 * ============================================================
 * 本章综合练习（无参考答案）
 * ------------------------------------------------------------
 * 【场景】
 * 用售票场景暴露线程安全问题，再用三种方式解决，最后把任务交给线程池统一调度并统计耗时。目标是把「共享状态 + 锁 + 线程池」串成一条线。
 * 【任务】
 * 1. 写一个不加锁的售票程序，用 3 个线程各卖 100 张票，复现超卖或重复售票，并说明为什么会出现。
 * 2. 分别用 synchronized 同步代码块、同步方法、ReentrantLock 三种方式修复，对比吞吐与代码形态。
 * 3. 用固定大小线程池提交 20 个任务，统计总耗时并与串行执行对比，说明线程池复用了什么。
 * 4. 用 wait/notify 实现生产者消费者：一个线程放数据、一个线程取数据，队列满或空时正确阻塞。
 * 【验收清单】
 * [ ] 未加锁版本能稳定复现问题（多跑几次至少出现一次）。
 * [ ] 三种加锁版本都恰好卖出 100 张票，不多不少。
 * [ ] 线程池版本总耗时明显低于串行版本，且线程名能体现复用。
 * [ ] 生产者消费者版本不出现死锁，程序能正常退出。
 * ============================================================
 */
