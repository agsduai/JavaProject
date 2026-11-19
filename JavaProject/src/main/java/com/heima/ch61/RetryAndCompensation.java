/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：61-06
 * 课名：失败重试与补偿
 * ------------------------------------------------------------
 * 知识点：
 *   - **失败分类决定重试策略**（40-02 的错误分类在调度上的应用）：**可重试**（网络抖动/下游临时不可用——指数退避重试）、**不可重试**（数据错误/参数错误——立即失败告警）、**部分失败**（分片中部分失败——只重跑失败分片）。**无脑重试是事故放大器**（重试风暴把下游打穿，44-08）。
 *   - **重试的退避与上限**：指数退避（1s/2s/4s/8s）+ 最大次数（3~5 次）+ **抖动**（避免所有实例同时重试）——与 40-02 的重试策略完全同构。
 *   - **补偿 vs 重试**：**重试**是"再试一次同一操作"；**补偿**是"用反向操作抵消已产生的影响"（51 章的退款、39-09 的 Cancel）——**调度任务的补偿**典型是"失败后回滚已写入的分区"或"标记数据不可用"（46-08 的阻断规则）。
 *   - **失败的可观测**：失败任务的**错误摘要 + 重试次数 + 最终状态**（SUCCESS/FAILED/DEAD）；**死信任务**（超过重试上限）进人工处理队列，**不能静默丢弃**（28 章死信语义）。
 *   - **上游失败的下游影响**：DAG 中上游失败 -> 下游跳过并**标记"依赖未就绪"**（而不是跑出错误结果 61-04）；补数完成后**自动触发下游**（或人工确认后触发）。
 *   - 易错点：对"数据错误"重试（永远失败还占资源）；重试无上限（死循环）；失败只打日志不告警（数据缺口无人知）；补偿逻辑本身失败（需要补偿的补偿，必须人工兜底）。
 * ============================================================
 */

package com.heima.ch61;

import java.util.ArrayList;
import java.util.List;

// ==================== 示例代码 ====================

public class RetryAndCompensation {
    enum FailureKind {
        TRANSIENT, PERMANENT, PARTIAL
    }

    record Attempt(int no, long backoffMs, String result) {
    }

    /** 按失败类型决定重试策略 */
    static List<Attempt> retryPlan(FailureKind kind, int maxRetry) {
        List<Attempt> plan = new ArrayList<>();
        if (kind == FailureKind.PERMANENT) {
            plan.add(new Attempt(3, 0, "不重试，直接进入死信 + 告警"));
            return plan;
        }
        long backoff = 1000;
        for (int i = 1; i <= maxRetry; i++) {
            long jitter = i * 137L % 500;                 // 抖动，避免同步重试
            plan.add(new Attempt(i, backoff + jitter,
                    kind == FailureKind.PARTIAL ? "只重跑失败分片" : "重跑整个任务"));
            backoff *= 2;
        }
        plan.add(new Attempt(maxRetry + 1, 0, "仍失败 -> 死信 + 人工"));
        return plan;
    }

    /** 补偿：失败后回滚已写入的分区 */
    static void compensate(List<String> writtenPartitions, List<String> livePartitions) {
        System.out.println("  补偿：回滚本次已写入的分区 " + writtenPartitions);
        livePartitions.removeAll(writtenPartitions);
        System.out.println("  回滚后线上分区：" + livePartitions + "（下游读到的是上一次的完整数据）");
    }

    public static void main(String[] args) {
        System.out.println("【重试计划：临时故障】");
        retryPlan(FailureKind.TRANSIENT, 3).forEach(a ->
                System.out.printf("  第%d次 退避=%5dms %s%n", a.no(), a.backoffMs(), a.result()));
        System.out.println();
        System.out.println("【重试计划：永久故障】");
        retryPlan(FailureKind.PERMANENT, 3).forEach(a ->
                System.out.printf("  第%d次 %s%n", a.no(), a.result()));
        System.out.println();
        System.out.println("【补偿：半写入失败】");
        List<String> live = new ArrayList<>(List.of("dt=2026-11-16", "dt=2026-11-17"));
        List<String> written = new ArrayList<>(List.of("dt=2026-11-18"));
        live.addAll(written);                              // 已写入但不完整
        compensate(written, live);
        System.out.println("  原则：宁可让下游用旧数据，也不让它读到半成品");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现重试执行器：按失败类型分流（可重试/不可重试/部分失败）+ 指数退避 + 抖动 + 死信，用三种故障各跑一遍输出轨迹。
 * 2. 实现"半写入补偿"：任务写到第 3 个分区失败，回滚已写的分区并标记该日期不可用，验证下游读到的是旧完整数据。
 * 3. 思考题：为什么"补偿逻辑本身也可能失败"？设计一个"补偿失败"的人工兜底流程（告警/工单/数据标记）。
 * ============================================================
 */
