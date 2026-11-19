/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：61-05
 * 课名：调度幂等与重复执行防护
 * ------------------------------------------------------------
 * 知识点：
 *   - **调度系统的三大重复来源**：**中心多实例重复触发**（61-02 的锁缺失）、**故障转移重派**（执行器"看起来挂了"但实际跑完了）、**人工重跑**（忘了上次已成功）——**三种都会导致任务跑两次**。
 *   - **幂等的三层保障**：① **触发层去重**（同一任务同一时间片只触发一次，分布式锁/唯一约束）；② **执行层幂等**（任务逻辑本身可重入：按分区覆盖写、按主键 upsert、先删后插）；③ **结果层校验**（重跑后校验结果未翻倍，46-08 质量规则）。
 *   - **"至少一次"与"恰好一次"的取舍**：调度系统通常保证**至少一次触发**（宁可重跑不能漏跑），**幂等由任务实现方保证**——**这与 28 章 MQ 的语义完全一致**（43-06 的同款结论：幂等是端到端正确性的最后一公里）。
 *   - **重跑的正确姿势**：**按分区/按天覆盖**（`INSERT OVERWRITE PARTITION`，59-10）、**先清理目标再写**（清 -> 写 -> 校验三步）、**重跑前确认上游数据完整**（46-08）——**"直接追加"是最常见的错误**。
 *   - **补数（backfill）**：历史缺口批量重跑——**按天串行**（跨天依赖不能并行，61-04）、**限速**（不压垮源库，46-09）、**进度可观测**（跑完哪天/还剩几天）。
 *   - 易错点：任务"先查后写"非原子（两个实例同时判断"未处理"）；重跑直接追加（数据翻倍）；补数并行跑跨天依赖（下游读到半成品）；无执行记录（不知道跑没跑过）。
 * ============================================================
 */

package com.heima.ch61;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class IdempotentJob {
    /** 任务执行记录：jobId + bizDate + 分片 为唯一键 */
    record Execution(String jobId, String bizDate, int shard, String status, long finishedAt) {
    }

    static final Map<String, Execution> history = new LinkedHashMap<>();

    static String key(String jobId, String bizDate, int shard) {
        return jobId + "#" + bizDate + "#" + shard;
    }

    /** 触发层：已成功则跳过（可重跑需显式 force） */
    static boolean shouldRun(String jobId, String bizDate, int shard, boolean force) {
        Execution prev = history.get(key(jobId, bizDate, shard));
        if (!force && prev != null && prev.status().equals("SUCCESS")) {
            System.out.printf("  [%s] 已成功于 %d -> 跳过（防重复）%n", key(jobId, bizDate, shard),
                    prev.finishedAt());
            return false;
        }
        return true;
    }

    /** 执行层：幂等写入（按分片覆盖，模拟"先删后写"） */
    static void runIdempotent(String jobId, String bizDate, int shard, List<String> target) {
        target.removeIf(row -> row.startsWith(jobId + "#" + bizDate + "#" + shard + "#"));
        for (int i = 0; i < 3; i++) {
            target.add(jobId + "#" + bizDate + "#" + shard + "#row-" + i);
        }
        history.put(key(jobId, bizDate, shard),
                new Execution(jobId, bizDate, shard, "SUCCESS", System.currentTimeMillis() % 100000));
        System.out.printf("  执行 %s 分片%d -> 覆盖写入 3 行（幂等）%n", jobId, shard);
    }

    public static void main(String[] args) {
        List<String> target = new ArrayList<>();
        System.out.println("【首次执行】");
        if (shouldRun("closeOrder", "2026-11-18", 0, false)) {
            runIdempotent("closeOrder", "2026-11-18", 0, target);
        }
        System.out.println("  目标数据行数：" + target.size());

        System.out.println();
        System.out.println("【重复触发（故障转移重派）】");
        if (shouldRun("closeOrder", "2026-11-18", 0, false)) {
            runIdempotent("closeOrder", "2026-11-18", 0, target);
        }
        System.out.println("  目标数据行数：" + target.size() + "（未翻倍）");

        System.out.println();
        System.out.println("【强制重跑（人工补数）】");
        if (shouldRun("closeOrder", "2026-11-18", 0, true)) {
            runIdempotent("closeOrder", "2026-11-18", 0, target);
        }
        System.out.println("  目标数据行数：" + target.size() + "（覆盖而非追加）");
        System.out.println();
        System.out.println("  三层保障：触发去重 + 执行覆盖 + 结果校验");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现完整幂等执行器：触发去重（分布式锁模拟）+ 按分片覆盖写 + 结果校验（行数/金额合计），用"重复触发三次"验证结果不翻倍。
 * 2. 实现补数器：按天串行重跑 + 限速 + 进度输出，模拟跨 5 天的数据缺口修复。
 * 3. 思考题：为什么调度系统选择"至少一次"而不是"恰好一次"？从"漏跑的数据缺失不可恢复"与"重跑可幂等"的风险不对称分析。
 * ============================================================
 */
