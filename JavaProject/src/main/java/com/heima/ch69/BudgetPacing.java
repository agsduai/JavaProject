/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：69-05
 * 课名：预算控制与 pacing
 * ------------------------------------------------------------
 * 知识点：
 *   - **预算控制回答"这个广告主今天还能花多少、该花多快"**：**日预算**（每天限额）、**总预算**（投放期总额）、**分时段预算**（大促期间加速）——**超额必须停止投放**，否则广告主投诉。
 *   - **Pacing（投放节奏）**：把预算均匀分配到全天——**太快**（上午花完，下午无曝光，广告主不满）、**太慢**（预算花不完，平台少收入）——**pacing 是"预算消耗曲线"的控制**。
 *   - **Pacing 的实现**：**概率丢弃**（按 `剩余预算/剩余时间` 计算放行概率）、**令牌桶**（每小时令牌 = 预算/24）、**PID 控制**（按消耗速率偏差调整）——**本质是 44 章的限流思想在预算维度的应用**。
 *   - **预算与竞价的联动**：出价可以**动态调整**（预算充足时按基准价，接近耗尽时降价或停止）——**避免"最后 1 小时抢预算导致出价畸高"**。
 *   - **预算的分布式一致性**（关键难点）：多台广告服务器并行扣预算 -> **必须原子扣减**（Redis 原子操作 25 章、或分段预分配预算）——**扣超了就是资损**（51 章思想）。
 *   - 易错点：预算检查在曝光后（已花超）；无 pacing（预算瞬间花完）；多机并行扣减非原子（超投）；预算变更不实时生效（运营改不了）。
 * ============================================================
 */

package com.heima.ch69;

import java.util.LinkedHashMap;
import java.util.Map;

// ==================== 示例代码 ====================

public class BudgetPacing {
    record Budget(double totalCents, double spentCents, long dayStartMs, long dayEndMs) {
        double remaining() {
            return Math.max(0, totalCents - spentCents);
        }

        /** 理想消耗进度：按时间线性 */
        double expectedSpentRatio(long now) {
            if (now <= dayStartMs) {
                return 0;
            }
            if (now >= dayEndMs) {
                return 1;
            }
            return (now - dayStartMs) / (double) (dayEndMs - dayStartMs);
        }

        double actualSpentRatio() {
            return totalCents == 0 ? 0 : spentCents / totalCents;
        }
    }

    /** 概率 pacing：放行概率 = 剩余预算比例 / 剩余时间比例（钳制到 [0,1]） */
    static double pacingFactor(Budget b, long now) {
        double expected = b.expectedSpentRatio(now);
        double actual = b.actualSpentRatio();
        if (expected <= 0) {
            return 1.0;
        }
        double ratio = (1 - actual) / (1 - expected);
        return Math.max(0, Math.min(1, ratio));
    }

    /** 模拟：按时间推进，观察消耗曲线 */
    static void simulate(double totalBudgetCents, long dayMs, int steps) {
        double spent = 0;
        System.out.printf("【预算 %.0f 分，全天 %d 小时，pacing 模拟】%n", totalBudgetCents, dayMs / 3_600_000);
        for (int i = 1; i <= steps; i++) {
            long now = dayMs * i / steps;
            Budget b = new Budget(totalBudgetCents, spent, 0, dayMs);
            double factor = pacingFactor(b, now);
            double requestCost = totalBudgetCents / steps * 1.5;   // 假设每步请求花费略高于均摊
            double allowProb = Math.min(factor, b.remaining() / Math.max(1, requestCost));
            double cost = requestCost * allowProb;
            spent = Math.min(totalBudgetCents, spent + cost);
            System.out.printf("  第%2d步 时间进度=%3.0f%% 已花=%6.0f（%3.0f%%）放行概率=%.2f%n",
                    i, b.expectedSpentRatio(now) * 100, spent,
                    spent / totalBudgetCents * 100, allowProb);
        }
        System.out.println("  结果：消耗曲线贴合时间进度，不会前半天花完");
        System.out.println();
    }

    static void distributedBudget() {
        System.out.println("【分布式预算扣减】");
        Map<String, String> approaches = new LinkedHashMap<>();
        approaches.put("集中式 Redis 原子扣减", "DECRBY 原子，简单但有网络开销与热点");
        approaches.put("分段预分配", "每台服务器预领一段（如 5%），用完再领，减少竞争");
        approaches.put("本地扣减 + 定期上报", "快但可能超投（需容忍阈值）");
        approaches.forEach((k, v) -> System.out.printf("  %-18s %s%n", k, v));
        System.out.println("  铁律：预算超投 = 资损，宁可少投不可超投（保守扣减）");
    }

    public static void main(String[] args) {
        simulate(10_000, 24 * 3_600_000L, 8);

        System.out.println("【预算不足时的降级】");
        System.out.println("  剩余预算 < 单次最低成本 -> 停止投放并释放请求（不参与竞价）");
        System.out.println("  剩余预算接近耗尽 -> 降低出价（避免预算被瞬间打空）");
        System.out.println();

        distributedBudget();
        System.out.println();
        System.out.println("  与 44 章限流的同构：限流是限「速率」，pacing 是限「总量进度」");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现概率 pacing：给定日预算与时间，输出每小时的放行概率与消耗曲线，验证"不提前花完"。
 * 2. 实现分段预分配预算：3 台服务器各预领 1/3，模拟跨机扣减与"用完再领"，验证不超投。
 * 3. 思考题：为什么"预算超投"比"少投"更严重？从"资损不可逆"与"少投可补偿"分析（51 章思想）。
 * ============================================================
 */
