/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：59-09
 * 课名：资源模型与推测执行
 * ------------------------------------------------------------
 * 知识点：
 *   - **Spark 的资源三件套**：`executor-memory`（堆内存，含计算与缓存）、`executor-cores`（每个 Executor 的并发槽位）、`executor 数量`——**总核数 = executor 数 × cores**，Task 并发 = 总核数。
 *   - **内存分区**：堆内分**执行内存**（Shuffle/Join/排序的缓冲）与**存储内存**（cache/persist）——两者可动态借用（Spark 3.x 统一内存管理），**执行内存不足会溢写磁盘（严重拖慢）**，存储内存被挤占会淘汰缓存。
 *   - **caching 的正确姿势**：**只在多次使用的 DataFrame 上 cache**（一次性的 cache 是浪费）；`persist(MEMORY_AND_DISK)` 防止 OOM；**cache 用完 `unpersist`**（否则占内存到作业结束）。
 *   - **推测执行（Speculative Execution）**：慢 Task 在其他节点并行启动副本，**先完成者胜出**——对付硬件抖动/GC 长尾；**代价**：资源翻倍（只在稳定环境关闭，异构集群开启）；**输出必须幂等**（重复执行写同一分区）。
 *   - **资源调优的判据**：**Executor 不宜过大**（单 Executor 核数 4~5 为宜，太大 GC 停顿影响所有 Task；太小调度开销高）；**内存按"每核 4~8GB"估算**（缓存放存储内存，Shuffle 缓冲按核数）；YARN/K8s 的动态分配（47-08 弹性的 Spark 版）按需申请。
 *   - 易错点：`cache()` 后不 `unpersist`（内存泄漏到作业结束）；Executor 内存全给计算（cache 无处放、Shuffle 频繁溢写）；推测执行开着但输出非幂等（数据重复）；只看 CPU 不看 GC（30 章 GC 日志的 Spark 版）。
 * ============================================================
 */

package com.heima.ch59;

import java.util.ArrayList;
import java.util.List;

// ==================== 示例代码 ====================

public class ResourceModel {
    record ExecutorConfig(int executors, int coresPerExecutor, int heapMb) {
        int totalCores() {
            return executors * coresPerExecutor;
        }

        /** 统一内存：执行与存储各占一半（0.5 简化模型，真实为 0.6/0.5 可借） */
        long executionMemMb() {
            return (long) (heapMb * 0.5) * executors;
        }

        long storageMemMb() {
            return (long) (heapMb * 0.5) * executors;
        }
    }

    /** 评估配置是否合理 */
    static void assess(ExecutorConfig c, long inputMb, int partitions) {
        System.out.printf("  Executor=%d×%d核，堆=%dMB -> 总核=%d，执行内存=%dMB%n",
                c.executors(), c.coresPerExecutor(), c.heapMb(),
                c.totalCores(), c.executionMemMb());
        if (c.coresPerExecutor() > 5) {
            System.out.println("    提示：单 Executor 核数 >5，GC 停顿会同时卡住多个 Task");
        }
        if (c.coresPerExecutor() < 2 && c.executors() > 50) {
            System.out.println("    提示：Executor 过碎，调度与心跳开销升高");
        }
        if (partitions > c.totalCores() * 10) {
            System.out.println("    提示：分区/核数 >10，波次过多，长尾放大（59-04）");
        }
        long perPartitionMb = inputMb / Math.max(1, partitions);
        if (perPartitionMb > c.executionMemMb() / Math.max(1, c.totalCores())) {
            System.out.println("    风险：单分区数据 > 每 Tasks 可用执行内存 -> 溢写磁盘");
        }
    }

    /** 推测执行的取舍 */
    static void speculation() {
        System.out.println("【推测执行：长尾任务的对冲】");
        record Task(String id, long ms, String note) {
        }
        List<Task> tasks = List.of(
                new Task("t-1", 500, "正常"),
                new Task("t-2", 800, "正常"),
                new Task("t-3", 5000, "慢节点（磁盘/GC 抖动）"));
        long sequential = tasks.stream().mapToLong(Task::ms).sum();
        long withSpeculation = Math.max(800, Math.max(500, 500)) + 500;   // 副本 500ms 后启动
        System.out.printf("  无推测执行：Stage 时长 = 最慢 Task = %dms%n",
                tasks.stream().mapToLong(Task::ms).max().orElse(0));
        System.out.printf("  有推测执行：慢 Task 被副本超越 -> 约 %dms（副本启动延迟 500ms）%n",
                withSpeculation);
        System.out.println("  代价：资源浪费；前提：输出幂等（重算不产生重复数据）");
    }

    public static void main(String[] args) {
        System.out.println("【资源配置评估】");
        assess(new ExecutorConfig(10, 4, 8192), 50_000, 200);
        System.out.println();
        assess(new ExecutorConfig(8, 8, 4096), 50_000, 200);
        System.out.println();
        speculation();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 为 64 核、512GB 内存的集群设计三套 Executor 配置（大/中/小），按"每核内存""Executor 数"评估并说明推荐方案。
 * 2. 实现"每 Task 可用执行内存"计算器，输入分区数据量判断溢写风险，给出调整建议。
 * 3. 思考题：为什么推测执行要求输出幂等？构造一个"两份副本都完成"导致数据翻倍的场景并说明防护。
 * ============================================================
 */
