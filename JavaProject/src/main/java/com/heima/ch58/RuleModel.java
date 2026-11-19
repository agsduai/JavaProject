/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：58-02
 * 课名：规则模型：条件、动作与优先级
 * ------------------------------------------------------------
 * 知识点：
 *   - **条件的三种表达形式**：**表达式字符串**（`amount > 5000 && hour < 6`，灵活但要解析）、**结构化条件树**（AND/OR/比较节点，可视化编辑友好）、**决策表行**（条件组合 -> 动作，业务方最易读 58-04）。**生产常用"结构化树 + 表达式"混合**：UI 编辑树，存储为表达式。
 *   - **动作的语义分级**：**放行**（ALLOW）、**观察**（LOG，不拦只记）、**挑战**（CHALLENGE，二次验证/短信）、**拦截**（BLOCK）——**与 51-07 风控决策分级一致**，规则输出的是"决策建议"，最终裁决在决策层（可能有多规则投票）。
 *   - **优先级的意义**：多条规则同时命中时**按优先级取最高危动作**（BLOCK 优先于 CHALLENGE）或**取第一条命中**（first-match）——**语义必须显式定义**，否则规则打架时行为不可预测。
 *   - **规则的生命周期字段**：生效时间窗（活动期规则）、适用渠道（App/Web）、灰度比例（58-08）、版本号（回滚锚点）、创建人与审批人（51-10 审计）。
 *   - **规则与特征解耦**：规则里引用的是**特征名**（`deviceAge`/`hour`/`userLevel`），不是直接查库——**特征由特征层统一计算**（58-06），规则引擎只管"用特征做判断"，这让规则可单测（喂特征快照即可）。
 *   - 易错点：优先级无定义（同命中时结果随机）；规则无时间窗（活动结束规则还在生效）；规则直接查库（性能与可测性双崩）；动作只有"拦截"（缺少观察档，无法灰度验证）。
 * ============================================================
 */

package com.heima.ch58;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class RuleModel {
    record Rule(String id, String expr, Decision action, int priority,
                long effectiveFrom, long effectiveTo, boolean enabled) {

        boolean active(long now) {
            return enabled && now >= effectiveFrom && now <= effectiveTo;
        }
    }

    enum Decision {
        ALLOW(0), LOG(1), CHALLENGE(2), BLOCK(3);

        final int severity;

        Decision(int severity) {
            this.severity = severity;
        }
    }

    record Fact(String eventId, Map<String, Object> features) {
        boolean bool(String k) {
            return Boolean.TRUE.equals(features.get(k));
        }

        long num(String k) {
            Object v = features.get(k);
            return v instanceof Number n ? n.longValue() : 0L;
        }
    }

    /** 极简求值：支持 amount 比较与布尔特征（真实实现见 58-03） */
    static boolean eval(String expr, Fact fact) {
        String e = expr.strip();
        if (e.equals("always")) {
            return true;
        }
        if (e.contains(">")) {
            String[] p = e.split(">");
            return fact.num(p[0].strip()) > Long.parseLong(p[1].strip());
        }
        return fact.bool(e);
    }

    /** 取最高危动作（优先级相同时按 severity 兜底） */
    static Decision decide(List<Rule> rules, Fact fact, long now) {
        List<Rule> hits = new ArrayList<>();
        for (Rule r : rules) {
            if (r.active(now) && eval(r.expr(), fact)) {
                hits.add(r);
            }
        }
        return hits.stream()
                .max(Comparator.comparingInt(Rule::priority)
                        .thenComparingInt(r -> r.action().severity))
                .map(Rule::action)
                .orElse(Decision.ALLOW);
    }

    public static void main(String[] args) {
        long now = 1000;
        List<Rule> rules = List.of(
                new Rule("R-1", "amount > 500", Decision.BLOCK, 100, 0, 9999, true),
                new Rule("R-2", "night", Decision.CHALLENGE, 50, 0, 9999, true),
                new Rule("R-3", "amount > 9000", Decision.BLOCK, 100, 0, 500, true));
        System.out.println("  大额+夜间：" + decide(rules, new Fact("e1",
                Map.<String, Object>of("amount", 800L, "night", true)), now) + "（BLOCK 优先级最高）");
        System.out.println("  小额+夜间：" + decide(rules, new Fact("e2",
                Map.<String, Object>of("amount", 100L, "night", true)), now) + "（CHALLENGE）");
        System.out.println("  过期规则不生效：" + decide(rules, new Fact("e3",
                Map.<String, Object>of("amount", 20000L, "night", false)), 600) + "（R-3 已过期）");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现完整规则模型（条件树 + 时间窗 + 渠道 + 动作分级），写 10 条规则并验证优先级裁决。
 * 2. 为"同一笔交易命中 BLOCK 与 CHALLENGE 各一条"设计裁决策略（最高危 vs 最高优先级），说明两种语义的适用场景。
 * 3. 思考题：为什么规则要引用"特征名"而不是直接查库？从可测试性、性能、规则与数据解耦三方面分析。
 * ============================================================
 */
