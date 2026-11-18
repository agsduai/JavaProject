/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：44-04
 * 课名：多智能体协作
 * ------------------------------------------------------------
 * 知识点：
 *   - **多智能体的价值是"分工与制衡"**：单一 Agent 上下文有限、角色混乱（又写又审）；拆成多个专职 Agent——规划者、执行者、审查者——每个上下文干净、职责单一。**制衡（审查者否决执行者）是质量保障的核心**。
 *   - **三种协作拓扑**：**流水线**（A -> B -> C 顺序传递，确定性最高）；**协调者 + 工人**（一个 orchestrator 分发任务收结果，最常用）；**辩论制**（两个 Agent 独立给答案再互相评审，用于高风险决策）。**拓扑越自由越难测试**。
 *   - **通信即消息，不是共享大脑**：Agent 之间传递的是**结构化消息**（任务、结果、质疑），各自维护自己的上下文。共享的是"黑板"（44-03）而非全部记忆——**全量共享 = 上下文爆炸 + 互相污染**。
 *   - **协调者的职责**：拆解任务、选择工人、校验结果、处理失败重试、聚合输出。**协调者本身也是 Agent，也要护栏**（最大轮数、预算）。协调者的失败模式：死循环派工（工人互相踢皮球）。
 *   - **成本与延迟的乘积**：多智能体 = 多次模型调用（串行则延迟线性涨）。**能单 Agent 解决就别多 Agent**——多智能体的合理触发：任务可并行、需要独立视角制衡、单上下文装不下。
 *   - **可测试性**：多智能体的确定性靠"固定拓扑 + mock 工人"——测试协调者时工人返回桩数据（40-09 的框架思想）。**拓扑不固定的多智能体无法回归测试**，等于上线赌博。
 *   - 易错点：为炫技上多智能体（单 Agent 就够，成本×N）；Agent 间传自然语言长文本（信息丢失，应传结构化数据）；没有失败处理（一个工人挂了全链路卡死）；评审 Agent 与执行 Agent 用同一个模型同一提示（制衡失效，互相点头）。
 * ============================================================
 */

package com.heima.ch44;

import java.util.ArrayList;
import java.util.List;

// ==================== 示例代码 ====================

public class MultiAgent {
    /** 协调者 + 工人 + 审查者 */
    record Msg(String from, String to, String type, String payload) {
    }

    static void orchestratorFlow() {
        System.out.println("【任务：生成周报（协调者 + 2 工人 + 1 审查者）】");
        List<Msg> msgs = List.of(
                new Msg("user", "orchestrator", "task", "生成订单系统周报"),
                new Msg("orchestrator", "worker-data", "subtask", "统计本周单量/GMV/错误率"),
                new Msg("worker-data", "orchestrator", "result", "单量 12.4 万，GMV 860 万，错误率 0.3%"),
                new Msg("orchestrator", "worker-analyst", "subtask", "对比上周，给出 3 条趋势解读"),
                new Msg("worker-analyst", "orchestrator", "result", "GMV +12%（大促预热）；错误率环比持平..."),
                new Msg("orchestrator", "reviewer", "review", "请校验数字与结论一致性"),
                new Msg("reviewer", "orchestrator", "verdict", "GMV 数字与明细一致；解读 2 与数据矛盾 -> 打回"),
                new Msg("orchestrator", "worker-analyst", "rework", "解读2 需重写（数据不支持）"),
                new Msg("worker-analyst", "orchestrator", "result", "解读2 修正：错误率持平但 P99 +8%..."),
                new Msg("orchestrator", "user", "final", "周报完成（经审查者确认）"));
        for (Msg m : msgs) {
            System.out.printf("  %-14s -> %-14s %-8s %s%n",
                    m.from(), m.to(), m.type(), m.payload());
        }
        System.out.println("  制衡生效：审查者打回 1 条与数据矛盾的解读");
        System.out.println();
    }

    static void topologyChoice() {
        System.out.println("【三种拓扑的确定性与适用】");
        String[][] rows = {
                {"流水线", "A->B->C 固定顺序", "确定性高，最易测试", "文档处理流水线"},
                {"协调者+工人", "动态分发，结构可控", "常用，需护栏", "多源调研、周报生成"},
                {"辩论制", "独立答案 + 互评", "成本高，适合高风险", "资金相关决策复核"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-10s %-22s %-16s %s%n", r[0], r[1], r[2], r[3]);
        }
        System.out.println();
    }

    static void testability() {
        System.out.println("【多智能体的回归测试：mock 工人】");
        List<String> steps = List.of(
                "固定拓扑：协调者的分发规则写死（哪些子任务给谁）",
                "工人桩：测试协调者时工人返回预录结果（含失败样本）",
                "评审断言：协调者对“审查否决”必须重派而非直接放行",
                "边界样本：工人超时/返回空/返回畸形 JSON 的处理");
        for (String s : steps) {
            System.out.println("  - " + s);
        }
        System.out.println("  与 40-09 AI Services 的 mock 思想一致：拓扑固定才能测试");
    }

    public static void main(String[] args) {
        orchestratorFlow();
        topologyChoice();
        testability();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现协调者+工人的最小多智能体（模拟模型）：完成"周报生成"任务，验证审查者否决后的重派流程。
 * 2. 用 mock 工人为协调者写 4 个测试用例（正常/工人超时/空结果/审查否决），全部通过后说明为什么拓扑必须固定。
 * 3. 思考题：为什么"执行者与审查者用同一个模型同一提示"会让制衡失效？从偏差相关性的角度分析，并给出至少两个降低相关性的手段。
 * ============================================================
 */
