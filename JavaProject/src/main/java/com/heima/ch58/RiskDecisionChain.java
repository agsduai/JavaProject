/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：58-10
 * 课名：风控决策全链路
 * ------------------------------------------------------------
 * 知识点：
 *   - **风控不是一个规则引擎，而是一条链路**：埋点采集（设备/行为）-> 特征计算（实时+近线）-> 名单预检（58-07）-> 规则链（58-05）-> 模型评分（可与规则融合）-> 决策聚合（动作分级）-> 执行（拦截/挑战/放行）-> 结果回流（58-09）。
 *   - **决策延迟预算**：端到端 200ms 内——特征 60ms + 名单 5ms + 规则 20ms + 模型 80ms + 聚合执行 10ms，**每段可降级**（模型超时用规则兜底，44-08 阶梯）。
 *   - **规则与模型的关系**：**规则管"确定性红线"**（黑名单/限额/地区禁售）、**模型管"概率性判断"**（团伙欺诈/羊毛党）；**融合方式**：规则先筛（成本低）-> 模型对幸存者打分 -> 决策聚合（规则 BLOCK 直接生效，模型分数参与 CHALLENGE 判断）。
 *   - **决策的可解释与可申诉**：输出"决策 + 命中规则/模型分 + 关键特征"三件套——**用户申诉与监管审计都要这份证据**（44-09 trace 的风控版）。
 *   - **误判的兜底路径**：CHALLENGE（二次验证）是"不确定时的最佳中间态"——**把 BLOCK 的很多场景降为 CHALLENGE，客诉大降而风险可控**（分级决策的价值）。
 *   - 易错点：全链路无降级（模型超时整笔挂）；规则与模型结论冲突无仲裁（同笔交易两套结论）；决策无证据（申诉无法处理）；CHALLENGE 滥用（用户被反复打扰，转化下跌）。
 * ============================================================
 */

package com.heima.ch58;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class RiskDecisionChain {
    record Decision(String eventId, String action, List<String> evidence,
                    double modelScore, String degraded) {
    }

    record Ctx(String eventId, long amountCents, boolean blacklisted,
               boolean newDevice, double modelScore, boolean modelTimeout) {
    }

    /** 全链路：名单 -> 规则（红线） -> 模型（概率） -> 聚合 */
    static Decision decide(Ctx ctx) {
        List<String> evidence = new java.util.ArrayList<>();
        String degraded = "none";

        // 1) 名单：硬红线，最短路径
        if (ctx.blacklisted()) {
            evidence.add("名单命中：黑名单");
            return new Decision(ctx.eventId(), "BLOCK", evidence, 0, degraded);
        }

        // 2) 规则：确定性红线
        if (ctx.amountCents() > 500_000) {
            evidence.add("规则命中：单笔超限（>5000 元）");
            return new Decision(ctx.eventId(), "BLOCK", evidence, 0, degraded);
        }
        if (ctx.newDevice()) {
            evidence.add("规则命中：新设备首笔交易");
        }

        // 3) 模型：概率判断（可降级）
        double score;
        if (ctx.modelTimeout()) {
            score = -1;
            degraded = "model-timeout -> 规则兜底（新设备转人工验证）";
            evidence.add("模型超时，降级为规则兜底");
            score = ctx.newDevice() ? 70 : 20;
        } else {
            score = ctx.modelScore();
            evidence.add("模型评分：" + String.format("%.0f", score));
        }

        // 4) 聚合：分级决策
        String action;
        if (score >= 80) {
            action = "BLOCK";
        } else if (score >= 50 || ctx.newDevice()) {
            action = "CHALLENGE";
        } else if (score >= 30) {
            action = "LOG";
        } else {
            action = "ALLOW";
        }
        return new Decision(ctx.eventId(), action, evidence, score, degraded);
    }

    public static void main(String[] args) {
        List<Ctx> cases = List.of(
                new Ctx("e-1", 6900, true, false, 10, false),
                new Ctx("e-2", 900_000, false, false, 10, false),
                new Ctx("e-3", 69_000, false, true, 40, false),
                new Ctx("e-4", 69_000, false, true, 85, false),
                new Ctx("e-5", 69_000, false, false, 0, true));
        for (Ctx c : cases) {
            Decision d = decide(c);
            System.out.printf("  %-4s -> %-9s 证据=%s%s%n", d.eventId(), d.action(),
                    d.evidence(), d.degraded().equals("none") ? "" : "  [降级:" + d.degraded() + "]");
        }
        System.out.println();
        System.out.println("  证据三件套随决策落库 -> 申诉与监管可解释");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现完整决策链路（含模型降级、动作分级、证据落库），构造 6 类场景验证各分支。
 * 2. 对比"把 CHALLENGE 全改 BLOCK"与"当前分级"的客诉量与拦截效果（用模拟数据），量化分级决策的价值。
 * 3. 思考题：为什么模型超时要"降级为规则兜底"而不是"直接放行"？从资损风险与体验损失的对比分析。
 * ============================================================
 */
