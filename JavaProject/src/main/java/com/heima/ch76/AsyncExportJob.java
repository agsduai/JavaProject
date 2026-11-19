/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：76-08
 * 课名：异步导出任务
 * ------------------------------------------------------------
 * 知识点：
 *   - **异步任务的完整生命周期**：**提交**（返回 taskId）-> **排队**（队列/线程池）-> **执行**（进度上报）-> **完成/失败**（结果落对象存储 73 章）-> **通知**（站内信/邮件/轮询）——**用户拿 taskId 查询进度，而不是等 HTTP 响应**。
 *   - **任务状态机**：`PENDING -> RUNNING -> SUCCESS/FAILED/CANCELLED`——**状态流转要幂等**（重复提交返回同一 taskId 76-01），**失败可重试**（重试次数上限）。
 *   - **进度上报**：**已处理行数/总行数**（或"当前阶段"）——**用户能看到"3 万/10 万"就不会以为卡死**；进度写 Redis/DB（不是内存，多实例可见）。
 *   - **资源隔离**：**导出线程池独立于业务线程池**（大导出不拖垮在线接口 44-08）——**队列有界 + 拒绝策略**（满了返回"排队中"），**并发数受限**。
 *   - 与 61 章的关系：与"分布式调度/工作流"同构——**任务编排、重试、超时、补偿**的完整套路（61-08）。
 *   - 易错点：异步任务用主线程池（拖垮在线）；进度只在内存（多实例看不到）；无失败重试（一次失败就丢）；无过期清理（历史任务堆积 73-10）。
 * ============================================================
 */

package com.heima.ch76;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

// ==================== 示例代码 ====================

public class AsyncExportJob {
    enum State { PENDING, RUNNING, SUCCESS, FAILED, CANCELLED }

    static class Job {
        final String taskId;
        final int totalRows;
        volatile State state = State.PENDING;
        volatile int processed;
        volatile String error;
        volatile String fileUrl;
        final AtomicInteger retries = new AtomicInteger();
        int plannedFailures;

        Job(String taskId, int totalRows) {
            this(taskId, totalRows, 0);
        }

        Job(String taskId, int totalRows, int plannedFailures) {
            this.taskId = taskId;
            this.totalRows = totalRows;
            this.plannedFailures = plannedFailures;
        }

        int percent() {
            return totalRows == 0 ? 0 : (int) (processed * 100L / totalRows);
        }

        Map<String, Object> snapshot() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("taskId", taskId);
            m.put("state", state);
            m.put("processed", processed);
            m.put("total", totalRows);
            m.put("percent", percent() + "%");
            m.put("fileUrl", fileUrl);
            m.put("error", error);
            return m;
        }
    }

    /** 独立线程池：2 线程 + 有界队列（不拖垮业务） */
    static final ThreadPoolExecutor exportPool = new ThreadPoolExecutor(
            2, 2, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(3),
            new ThreadPoolExecutor.AbortPolicy());

    static final Map<String, Job> jobs = new LinkedHashMap<>();

    /** 提交：幂等（同 taskId 复用） */
    static synchronized String submit(String taskId, int rows) {
        return submit(taskId, rows, 0);
    }

    /** plannedFailures：前 N 次执行模拟失败，用于演示重试 */
    static synchronized String submit(String taskId, int rows, int plannedFailures) {
        if (jobs.containsKey(taskId)) {
            System.out.println("  重复提交 -> 复用任务 " + taskId);
            return taskId;
        }
        Job job = new Job(taskId, rows, plannedFailures);
        jobs.put(taskId, job);
        try {
            exportPool.execute(() -> run(job));
            System.out.println("  提交成功 -> " + taskId + "（排队/执行中）");
        } catch (RuntimeException e) {
            job.state = State.FAILED;
            job.error = "QUEUE_FULL";
            System.out.println("  队列已满 -> 任务 " + taskId + " 被拒（应提示稍后重试）");
        }
        return taskId;
    }

    /** 执行：进度上报 + 失败重试（最多 2 次） */
    static void run(Job job) {
        job.state = State.RUNNING;
        try {
            if (job.plannedFailures > 0) {
                job.plannedFailures--;
                throw new IllegalStateException("模拟下游抖动");
            }
            int batch = 1000;
            while (job.processed < job.totalRows) {
                int step = Math.min(batch, job.totalRows - job.processed);
                Thread.sleep(20);                     // 模拟每批耗时
                job.processed += step;
                if (job.processed % 5000 == 0 || job.processed == job.totalRows) {
                    System.out.printf("    [%s] 进度 %d/%d（%d%%）%n",
                            job.taskId, job.processed, job.totalRows, job.percent());
                }
            }
            job.fileUrl = "https://cdn.example.com/export/" + job.taskId + ".xlsx";
            job.error = null;                     // 成功后清除上次失败信息
            job.state = State.SUCCESS;
            System.out.printf("    [%s] 完成 -> %s%n", job.taskId, job.fileUrl);
        } catch (Exception e) {
            job.error = e.getMessage();
            if (job.retries.incrementAndGet() <= 2) {
                System.out.printf("    [%s] 失败重试第 %d 次%n", job.taskId, job.retries.get());
                job.processed = 0;
                run(job);
            } else {
                job.state = State.FAILED;
                System.out.printf("    [%s] 最终失败：%s%n", job.taskId, job.error);
            }
        }
    }

    static void waitDone(String taskId, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            Job j = jobs.get(taskId);
            if (j.state == State.SUCCESS || j.state == State.FAILED) {
                return;
            }
            Thread.sleep(50);
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("【提交导出任务】");
        submit("task-1", 5000);
        submit("task-1", 5000);
        submit("task-2", 3000, 1);
        waitDone("task-1", 5000);
        waitDone("task-2", 5000);

        System.out.println();
        System.out.println("【查询任务状态】");
        jobs.values().forEach(j -> System.out.println("  " + j.snapshot()));

        System.out.println();
        System.out.println("【队列满：拒绝而非无限堆积】");
        for (int i = 0; i < 12; i++) {
            submit("bulk-" + i, 200);
        }
        exportPool.shutdown();
        exportPool.awaitTermination(5, TimeUnit.SECONDS);

        System.out.println();
        System.out.println("【要点】独立线程池 / 幂等提交 / 进度可查 / 失败重试 / 队列有界");
        System.out.println("  生产：进度写 Redis（多实例可见），结果文件落对象存储（73 章），过期清理（73-10）");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现异步导出：提交返回 taskId、状态查询、进度百分比，用"5 千行任务"验证进度递增。
 * 2. 加入失败重试（最多 2 次）与队列拒绝（有界队列满时返回 QUEUE_FULL），观察状态机流转。
 * 3. 思考题：为什么"导出任务必须用独立线程池"？从"在线接口延迟"与"资源隔离"分析（44-08）。
 * ============================================================
 */
