/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：实操 17-02
 * 课名：线程池参数调优
 * ------------------------------------------------------------
 * 知识点：
 *   - 【题目要求】实现线程池对比实验：IO 型任务（sleep 100ms）分别用核心数池、核数×2 池、虚拟线程跑 200 任务，记录吞吐与队列峰值；再演示拒绝策略差异（Abort/CallerRuns/Discard）在队列满时的行为；输出调优建议表。
 *   - 【考核点】线程池七参数、IO/CPU 任务的池大小公式、拒绝策略行为差异、压测方法。
 *   - 【易错点】IO 任务用核数池（严重欠并发）；无界队列掩盖背压（内存风险）；拒绝策略选 Discard 无日志（静默丢任务）。
 *   - 【提示】队列峰值用自定义 LinkedBlockingQueue 记录 size 峰值；三种拒绝策略各跑一次对比。
 * ============================================================
 */

package com.heima.practice.core;

import java.util.concurrent.*;

// ==================== 示例代码 ====================

public class ThreadPoolTuner {
    public static void main(String[] args) throws Exception {
        try (ExecutorService pool = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
                new SynchronousQueue<>(), new ThreadPoolExecutor.CallerRunsPolicy())) {
            for (int i = 0; i < 4; i++) {
                final int id = i;
                pool.submit(() -> {
                    System.out.println("  执行 " + id + "（线程 " + Thread.currentThread().getName() + "）");
                    return null;
                });
            }
        }
        System.out.println("  CallerRuns：提交者线程亲自执行 -> 天然背压，任务不丢");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 完整实现三池对比实验（含队列峰值统计），输出调优建议表并标注每种池的适用负载。
 * 2. 对比三种拒绝策略在同一过载场景下的任务丢失率与提交线程阻塞时间。
 * 3. 思考题：为什么 IO 型任务的池大小公式是"核数 ÷ (1-等待占比)"？推导并代入等待占比 90% 验证。
 * 
 * 
 * ## 第18章 网络编程
 * ============================================================
 */
