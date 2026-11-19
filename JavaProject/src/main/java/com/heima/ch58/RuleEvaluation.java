/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：58-09
 * 课名：规则效果评估与误杀治理
 * ------------------------------------------------------------
 * 知识点：
 *   - **规则上线只是开始，评估是常态**：每条规则要有**命中率/准确率/误杀率**——准确率 = 真命中 ÷ 总命中（需要人工标注样本或事后归因）；**长期零命中的规则要下线**（闲置规则是维护负担）。
 *   - **误杀的发现渠道**：客诉（滞后但真实）、**人工抽检**（每日抽样标注）、**申诉闭环**（用户申诉 -> 标注 -> 反馈到规则）——**申诉数据是最宝贵的准确率样本**（44-10 点踩样本的风控版）。
 *   - **评估的指标体系**：拦截量、拦截率、误杀率（误拦/总拦截）、漏拦率（事后发现的风险事件/未拦截总量）、决策耗时、规则贡献度（去掉这条规则会少拦多少）——**贡献度用于"规则精简"**。
 *   - **规则精简与反冗余**：几百条规则后必然出现"重复覆盖"（两条规则拦同一批人）与"死规则"（永不命中）——**季度做一次规则体检**：合并重复、下线闲置、参数复用。
 *   - **规则效果的可解释性**：对业务方解释"为什么拦他"——**命中规则 + 关键特征值 + 阈值**（44-09 证据链的风控版）；监管要求"可解释"（如信贷风控）。
 *   - 易错点：只看拦截量不看误杀（拦得多不等于拦得对）；规则无贡献度分析（冗余堆积）；申诉数据不回流（同样的误杀反复发生）；无抽检（准确率永远未知）。
 * ============================================================
 */

package com.heima.ch58;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class RuleEvaluation {
    record Hit(String ruleId, String eventId, boolean confirmedRisk) {
    }

    record Stats(String ruleId, int hits, int truePositives, double hitRate, double precision) {
    }

    /** 规则级评估：命中数、准确率、贡献度（去掉后少拦多少） */
    static Map<String, Stats> evaluate(List<Hit> hits, long totalEvents) {
        Map<String, Stats> out = new LinkedHashMap<>();
        Map<String, int[]> agg = new LinkedHashMap<>();
        for (Hit h : hits) {
            int[] a = agg.computeIfAbsent(h.ruleId(), k -> new int[2]);
            a[0]++;
            if (h.confirmedRisk()) {
                a[1]++;
            }
        }
        agg.forEach((rid, a) -> out.put(rid, new Stats(rid, a[0], a[1],
                a[0] * 100.0 / totalEvents,
                a[0] == 0 ? 0 : a[1] * 100.0 / a[0])));
        return out;
    }

    /** 规则体检：闲置（零命中）/ 低准确率 / 高贡献 */
    static List<String> healthCheck(Map<String, Stats> stats) {
        List<String> findings = new ArrayList<>();
        stats.forEach((rid, s) -> {
            if (s.hits() == 0) {
                findings.add(rid + "：闲置规则（零命中）-> 下线候选");
            } else if (s.precision() < 50) {
                findings.add(rid + "：准确率 " + String.format("%.0f%%", s.precision())
                        + " -> 误杀偏高，需调阈值或加白名单");
            } else if (s.precision() >= 90) {
                findings.add(rid + "：准确率 " + String.format("%.0f%%", s.precision())
                        + " -> 高价值规则，保持");
            }
        });
        return findings;
    }

    public static void main(String[] args) {
        List<Hit> hits = new ArrayList<>();
        // R-1：高准确率（20 次命中 19 次确认）
        for (int i = 0; i < 20; i++) {
            hits.add(new Hit("R-1", "e-1-" + i, i < 19));
        }
        // R-2：低准确率（10 次命中仅 3 次确认）
        for (int i = 0; i < 10; i++) {
            hits.add(new Hit("R-2", "e-2-" + i, i < 3));
        }
        // R-3：零命中（不在列表里 -> stats 里不会出现，用空规则显式说明）
        Map<String, Stats> stats = evaluate(hits, 10_000);
        stats.put("R-3", new Stats("R-3", 0, 0, 0, 0));

        System.out.println("【规则评估（样本 10000 事件）】");
        stats.forEach((rid, s) -> System.out.printf(
                "  %-4s 命中%3d 命中率%.2f%% 准确率%.0f%%%n",
                rid, s.hits(), s.hitRate(), s.precision()));
        System.out.println();
        System.out.println("【体检结论】");
        healthCheck(stats).forEach(f -> System.out.println("  - " + f));
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现规则评估完整版（含误杀率、漏拦率、规则贡献度），用 1 万事件 + 人工标注样本跑出报表。
 * 2. 设计"申诉闭环"：用户申诉 -> 标注 -> 规则误杀统计 -> 阈值调整建议，画出数据流并实现最小版本。
 * 3. 思考题：为什么"零命中规则"要下线？从维护成本、认知负担、误改风险三个角度分析，并说明下线前要确认什么。
 * ============================================================
 */
