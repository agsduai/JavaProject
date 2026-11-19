/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：58-11
 * 课名：综合案例：支付风控规则引擎
 * ------------------------------------------------------------
 * 知识点：
 *   - **把本章收口成一个可运行的支付风控引擎**：规则模型（58-02）-> 表达式求值（58-03）-> 决策表（58-04）-> 链式执行与冲突消解（58-05）-> 特征快照（58-06）-> 名单预检（58-07）-> 热更新影响面（58-08）-> 效果评估（58-09）-> 全链路决策（58-10）。
 *   - **验收标准**：① 同一笔交易多次决策结果稳定（无随机）；② 证据链完整（命中什么规则、特征值多少）；③ 规则变更可回滚（版本）；④ 影响面可预览（历史回放）；⑤ 延迟可控（每段耗时可见）。
 *   - **工程化的三件套**：规则库版本化（Git 或配置中心）、回归样本集（历史交易 + 标注）、监控看板（命中率/准确率/延迟 58-09）——**缺一件就只是"能跑的 Demo"**。
 *   - **风控的边界认知**：规则引擎是"业务规则的可配置化"，**它不替代模型、不替代人工审核**——三层防线（规则红线 + 模型概率 + 人工兜底）的组合才是完整风控。
 *   - 易错点：把规则引擎当"万能拦截器"（所有风险都靠规则）；无回归样本（改规则靠猜）；证据链缺失（申诉处理不了）；忽略延迟（规则越多越慢，特征计算才是大头）。
 * ============================================================
 */

package com.heima.ch58;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class PaymentRiskEngine {
    record Rule(String id, String expr, String action, int priority, String version) {
    }

    record FeatureSet(Map<String, Long> numbers, Map<String, Boolean> flags) {
    }

    record Verdict(String decision, List<String> evidence, List<String> versions) {
    }

    /** 极简求值：支持 "k>n" 与布尔 flag */
    static boolean hit(Rule r, FeatureSet fs) {
        String e = r.expr();
        if (e.equals("always")) {
            return true;
        }
        if (e.contains(">")) {
            String[] p = e.split(">");
            Long v = fs.numbers().get(p[0].strip());
            return v != null && v > Long.parseLong(p[1].strip());
        }
        return Boolean.TRUE.equals(fs.flags().get(e));
    }

    static Verdict evaluate(List<Rule> rules, FeatureSet fs) {
        List<String> evidence = new ArrayList<>();
        List<String> versions = new ArrayList<>();
        String decision = "ALLOW";
        int bestSeverity = 0;
        List<Rule> sorted = new ArrayList<>(rules);
        sorted.sort((a, b) -> Integer.compare(b.priority(), a.priority()));
        for (Rule r : sorted) {
            if (hit(r, fs)) {
                evidence.add(r.id() + "[" + r.expr() + "] -> " + r.action());
                versions.add(r.version());
                int sev = switch (r.action()) {
                    case "BLOCK" -> 3;
                    case "CHALLENGE" -> 2;
                    case "LOG" -> 1;
                    default -> 0;
                };
                if (sev > bestSeverity) {
                    bestSeverity = sev;
                    decision = r.action();
                }
            }
        }
        return new Verdict(decision, evidence, versions);
    }

    /** 影响面预览：新旧规则集对历史样本的决策差异 */
    static void preview(List<Rule> oldRules, List<Rule> newRules, List<FeatureSet> samples) {
        long changed = 0;
        for (FeatureSet fs : samples) {
            String a = evaluate(oldRules, fs).decision();
            String b = evaluate(newRules, fs).decision();
            if (!a.equals(b)) {
                changed++;
            }
        }
        System.out.printf("  影响面：%d 笔样本中 %d 笔决策变化（%.1f%%）%n",
                samples.size(), changed, changed * 100.0 / samples.size());
    }

    public static void main(String[] args) {
        List<Rule> rules = List.of(
                new Rule("R-AMT-BIG", "amount>500000", "BLOCK", 100, "v3"),
                new Rule("R-BLACK", "inBlacklist", "BLOCK", 100, "v1"),
                new Rule("R-DEVICE", "newDevice", "CHALLENGE", 80, "v2"),
                new Rule("R-NIGHT", "nightTrade", "LOG", 20, "v1"));

        Map<String, Long> nums = new LinkedHashMap<>();
        nums.put("amount", 800_000L);
        Map<String, Boolean> flags = new LinkedHashMap<>();
        flags.put("inBlacklist", false);
        flags.put("newDevice", true);
        flags.put("nightTrade", true);

        Verdict v = evaluate(rules, new FeatureSet(nums, flags));
        System.out.println("【决策】" + v.decision());
        System.out.println("【证据链】");
        v.evidence().forEach(e -> System.out.println("  - " + e));
        System.out.println("【规则版本】" + v.versions());
        System.out.println();

        List<Rule> tightened = new ArrayList<>(rules);
        tightened.add(new Rule("R-AMT-MID", "amount>100000", "CHALLENGE", 60, "v4"));
        List<FeatureSet> samples = new ArrayList<>();
        for (long amt : new long[]{50_000, 120_000, 300_000, 900_000}) {
            samples.add(new FeatureSet(Map.of("amount", amt),
                    Map.of("inBlacklist", false, "newDevice", false, "nightTrade", false)));
        }
        System.out.println("【新规则 v4 影响面预览】");
        preview(rules, tightened, samples);
        System.out.println("  决策稳定 + 证据完整 + 影响面可预览 = 可上线的规则引擎");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 端到端实现支付风控引擎（第五章至第十章的全部能力），用 20 笔模拟交易输出决策、证据链与规则版本。
 * 2. 为引擎补充"规则回滚"：保存两版规则集，验证回滚后同一批样本的决策完全恢复。
 * 3. 思考题：为什么"决策稳定"是规则引擎的硬要求？从同一笔交易被不同节点/不同时刻判定不一致的后果（客诉/申诉/审计）分析。
 * ============================================================
 */

/*
 * ============================================================
 * 本章综合练习（无参考答案）
 * ------------------------------------------------------------
 * 【场景】
 * 做一个支付风控引擎：规则模型（条件+动作+优先级）、表达式求值、决策表、规则链与冲突消解、名单管理、热更新与灰度、效果评估与误杀治理。
 * 【任务】
 * 1. 定义规则模型：条件（金额、频次、设备、地域）、动作（通过、拒绝、人工审核）、优先级、生效时间，用对象而非硬编码 if 表达。
 * 2. 实现表达式求值器：支持比较、逻辑组合、括号，把规则配置字符串解析成可执行判断，并防止非法表达式导致崩溃。
 * 3. 实现规则链与冲突消解：多条规则命中时按优先级与动作严重度决策（拒绝 > 审核 > 通过），输出命中的全部规则。
 * 4. 实现名单管理：黑名单直接拒绝、白名单跳过规则，支持热更新（改配置不重启）与灰度（只对 10% 流量生效）。
 * 【验收清单】
 * [ ] 新增一条规则只需改配置，不需要改代码、不需要重启。
 * [ ] 冲突场景下决策结果符合预设优先级，且能追溯命中了哪几条规则。
 * [ ] 黑名单命中直接拒绝，白名单用户不触发任何规则。
 * [ ] 灰度期间只有目标比例流量走新规则，可随时回滚。
 * ============================================================
 */
