/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：实操 42-01
 * 课名：Histogram 分位数计算器
 * ------------------------------------------------------------
 * 知识点：
 *   - 【题目要求】实现 Histogram：桶边界 {10,50,100,250,500,1000,2500}（毫秒）+ 累计计数；支持三件事——① 记录样本落桶；② 跨实例聚合（桶计数相加后再算分位数）；③ 输出 P50/P90/P99 与“两实例先聚合再算分位 vs 各自算分位再平均”的对比。
 *   - 【考核点】分桶与累计计数、分位数插值逻辑、可合并性（Histogram 可聚合而 Summary 不可）、分桶粒度对精度的影响。
 *   - 【易错点】桶计数用“区间数”而非“累计数”（分位数计算要用 cumulative）；桶边界开闭混乱；P99 直接取上界导致精度误差未声明。
 *   - 【提示】分位数 = 找到累计数首次超过 target 的桶，桶宽内可线性插值（声明近似即可）。
 * ============================================================
 */

package com.heima.practice.observability;

import java.util.ArrayList;
import java.util.List;

// ==================== 示例代码 ====================

public class HistogramTask {
    static double quantile(int[] cumulative, double[] upper, double q) {
        long total = cumulative[cumulative.length - 1];
        double target = q * total;
        for (int i = 0; i < cumulative.length; i++) {
            if (cumulative[i] >= target) {
                return upper[i];
            }
        }
        return upper[upper.length - 1];
    }

    public static void main(String[] args) {
        int[] cumulative = {50, 80, 95, 100};
        double[] upper = {50, 100, 200, 400};
        List<String> qs = List.of("P50", "P90", "P99");
        double[] q = {0.5, 0.9, 0.99};
        for (int i = 0; i < qs.size(); i++) {
            System.out.printf("  %s ≈ %.0fms%n", qs.get(i), quantile(cumulative, upper, q[i]));
        }
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现完整 Histogram：三实例样本合并算 P50/P90/P99，对比“先聚合再算”与“各自算再平均”的误差。
 * 2. 调整桶边界（加粗 0~500 两桶），量化分桶粒度对 P99 精度的影响。
 * 3. 思考题：为什么 Summary 无法跨实例聚合？从分位数计算需要原始分布的角度分析。
 * ============================================================
 */
