/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：58-05
 * 课名：规则链与冲突消解
 * ------------------------------------------------------------
 * 知识点：
 *   - **规则不是孤立的，而是"链"**：一笔交易依次经过反欺诈 -> 限额 -> 黑白名单 -> 营销适用性，**前一个规则的输出可以成为后一个的输入**（如"风险分"）。规则链（Rule Chain）是比单条规则更贴近业务的抽象。
 *   - **冲突消解（Conflict Resolution）四种策略**：**最高优先级优先**（priority max）、**最高危动作为准**（most-severe wins）、**全部执行取并集**（叠加动作：拦截 + 打标 + 告警）、**first-match**（58-04）——**业务语义决定策略，且要写进文档**。
 *   - **RETE 算法的直觉**（专业引擎）：把规则条件拆成"模式网络"，**新事实到达时只沿匹配路径传播**，避免全量重算——适合"数百条规则 × 高频事实"的场景；**简单规则集（<50 条）用线性扫描完全够**。
 *   - **规则链的短路语义**：BLOCK 之后是否还执行后续规则？——**"命中拦截即终止"（省算力）vs "继续收集证据"（便于复盘）** 是产品决策；常见折衷：BLOCK 终止，但记录"本可命中"的后续规则（dry-run 痕迹）。
 *   - **规则链的可观测**：每条规则的命中次数、命中率、耗时——**不命中的规则要能解释**（是条件太严还是特征缺失），这是规则治理的日常（58-09）。
 *   - 易错点：链上规则互相矛盾无裁决（重复告警/漏拦）；短路策略未定义（同笔交易结果不稳定）；规则数上百仍线性扫描不优化（性能可接受但要注意特征计算成本）；无命中日志（规则形同黑盒）。
 * ============================================================
 */

package com.heima.ch58;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class RuleChain {
    record Rule(String id, String stage, String expr, String action, int priority) {
    }

    record Fact(String eventId, Map<String, Object> features) {
    }

    /** 极简求值：支持 "k>n" 与布尔特征 */
    static boolean eval(String expr, Fact f) {
        if (expr.equals("always")) {
            return true;
        }
        if (expr.contains(">")) {
            String[] p = expr.split(">");
            Object v = f.features().get(p[0].strip());
            return v instanceof Number n && n.longValue() > Long.parseLong(p[1].strip());
        }
        return Boolean.TRUE.equals(f.features().get(expr));
    }

    /** 分阶段执行 + 最高危裁决 + 命中终止（记录后续可命中项） */
    static void run(List<Rule> chain, Fact fact) {
        Map<String, List<String>> hitsByStage = new LinkedHashMap<>();
        String finalAction = "ALLOW";
        List<String> canaryHits = new ArrayList<>();
        boolean blocked = false;

        for (Rule r : chain) {
            if (blocked) {
                if (eval(r.expr(), fact)) {
                    canaryHits.add(r.id());          // 未执行但记录"本可命中"
                }
                continue;
            }
            boolean hit = eval(r.expr(), fact);
            System.out.printf("  [%s] %-6s 条件=[%-18s] %s%n",
                    r.stage(), r.id(), r.expr(), hit ? "命中 -> " + r.action() : "未命中");
            if (hit) {
                hitsByStage.computeIfAbsent(r.stage(), k -> new ArrayList<>()).add(r.id());
                if (severity(r.action()) >= severity(finalAction)) {
                    finalAction = r.action();
                }
                if (r.action().equals("BLOCK")) {
                    blocked = true;
                }
            }
        }
        System.out.println("  最终裁决：" + finalAction);
        System.out.println("  命中分布：" + hitsByStage);
        if (!canaryHits.isEmpty()) {
            System.out.println("  短路后本可命中：" + canaryHits + "（保留复盘证据）");
        }
    }

    static int severity(String action) {
        return switch (action) {
            case "BLOCK" -> 3;
            case "CHALLENGE" -> 2;
            case "LOG" -> 1;
            default -> 0;
        };
    }

    public static void main(String[] args) {
        List<Rule> chain = List.of(
                new Rule("F-1", "反欺诈", "deviceNew", "CHALLENGE", 80),
                new Rule("F-2", "反欺诈", "riskScore>80", "BLOCK", 100),
                new Rule("L-1", "限额", "amount>500000", "BLOCK", 90),
                new Rule("M-1", "名单", "inBlacklist", "BLOCK", 100),
                new Rule("P-1", "营销", "always", "LOG", 10));
        Map<String, Object> features = new LinkedHashMap<>();
        features.put("deviceNew", true);
        features.put("riskScore", 90L);
        features.put("amount", 800_000L);
        features.put("inBlacklist", false);
        run(chain, new Fact("e-1", features));
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现完整规则链：分阶段 + 四种冲突消解策略可切换 + 短路与"可命中记录"，用同一事实对比四种策略的输出差异。
 * 2. 实现"规则链性能画像"：统计每条规则的求值次数/命中率/耗时，找出"永远不命中"的规则并分析原因。
 * 3. 思考题：为什么"BLOCK 后短路但仍记录可命中项"是好的折衷？从算力成本与事故复盘的证据需求分析。
 * ============================================================
 */
