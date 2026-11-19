/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：61-09
 * 课名：调度可观测与积压治理
 * ------------------------------------------------------------
 * 知识点：
 *   - **调度的四个核心观测指标**（42 章方法论在调度上的特化）：**成功率**（按任务维度，失败率超标告警）、**耗时分布**（P50/P99，慢任务识别）、**及时性**（实际开始时间 vs 计划时间，延迟告警）、**积压**（待执行任务数/失败任务数）。
 *   - **告警的分级**：**失败告警**（任务失败，按任务重要性分 P1/P2）、**延迟告警**（超过 SLA 未完成，61-04）、**积压告警**（待执行/待重试队列长度，46-10 新鲜度）。
 *   - **执行日志的结构化**（42-02 的应用）：每次执行记录 `jobId/bizDate/shard/状态/开始/结束/错误摘要/重试次数`——**日志平台按 jobId 检索整个执行历史**（46-03 时间线重建）。
 *   - **积压治理**：积压原因分类（**产出不足**——执行器不够/分片太少；**消费过慢**——任务本身慢；**上游未就绪**——依赖等待）——**对症下药**：扩执行器、加并发分片、优化任务、查上游。
 *   - **调度的成本视角**：任务数 × 频率 × 耗时 = 计算资源消耗——**高频小任务可以合并**（每小时跑 60 次不如每分钟一次）；**低价值任务下调度**（45 章容量与成本）。
 *   - 易错点：只监控"任务是否跑"不监控"跑多久"（慢任务拖垮 SLA 无人知）；告警无分级（值班被次要任务淹没）；日志无 bizDate（无法按日期追溯）；积压只看总数不看原因（扩容解决不了上游问题）。
 * ============================================================
 */

package com.heima.ch61;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class SchedulerObservability {
    record Run(String jobId, String bizDate, long startedAtMs, long costMs, boolean success) {
    }

    /** 指标汇总：成功率 / P50 / P99 / 及时性 */
    static void metrics(List<Run> runs, long scheduledAtMs, long slaMs) {
        long total = runs.size();
        long failed = runs.stream().filter(r -> !r.success()).count();
        List<Long> costs = new ArrayList<>(runs.stream().map(Run::costMs).sorted().toList());
        long p50 = costs.get(Math.min(costs.size() - 1, (int) (costs.size() * 0.5)));
        long p99 = costs.get(Math.min(costs.size() - 1, (int) (costs.size() * 0.99)));
        long lateStart = runs.stream()
                .filter(r -> r.startedAtMs() - scheduledAtMs > 60_000).count();
        long overSla = runs.stream().filter(r -> r.costMs() > slaMs).count();

        System.out.printf("  总执行=%d 失败=%d（成功率 %.1f%%）%n", total, failed,
                (total - failed) * 100.0 / total);
        System.out.printf("  耗时 P50=%dms P99=%dms%n", p50, p99);
        System.out.printf("  延迟启动（>60s）=%d 超 SLA（>%dms）=%d%n", lateStart, slaMs, overSla);
    }

    /** 积压原因分类 */
    static String diagnose(int pending, int failed, boolean upstreamReady,
                           int executorCount, long avgCostMs) {
        if (!upstreamReady) {
            return "上游未就绪 -> 查依赖任务（61-04），扩容无用";
        }
        if (failed > pending / 2) {
            return "失败重试堆积 -> 先查失败根因（61-06），不是容量问题";
        }
        if (pending > executorCount * 10) {
            return "执行器不足 -> 扩容执行器或增加分片（61-03）";
        }
        if (avgCostMs > 300_000) {
            return "任务本身过慢 -> 优化任务（59 章调优），扩容治标";
        }
        return "正常";
    }

    public static void main(String[] args) {
        System.out.println("【指标汇总：closeOrderJob 近 20 次执行】");
        List<Run> runs = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            runs.add(new Run("closeOrderJob", "2026-11-" + (10 + i),
                    10_000L * i + (i == 12 ? 120_000 : 5_000),
                    8_000 + i * 500L, i != 7));
        }
        metrics(runs, 0, 30_000);

        System.out.println();
        System.out.println("【积压诊断】");
        Map<String, String> cases = new LinkedHashMap<>();
        cases.put("上游未就绪", diagnose(50, 5, false, 4, 20_000));
        cases.put("失败堆积", diagnose(50, 40, true, 4, 20_000));
        cases.put("执行器不足", diagnose(200, 5, true, 4, 20_000));
        cases.put("任务过慢", diagnose(30, 2, true, 10, 400_000));
        cases.forEach((k, v) -> System.out.printf("  %-8s -> %s%n", k, v));
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现调度看板：按任务维度统计成功率/P50/P99/延迟次数，输出"需要关注的任务 Top3"并给出原因假设。
 * 2. 实现积压分类器（61-09 的四个判据），用 6 组参数验证诊断结论正确。
 * 3. 思考题：为什么"只监控失败率"不够？构造一个"全部成功但每次都超时"的场景说明及时性指标的必要性。
 * ============================================================
 */
