/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：58-06
 * 课名：实时特征与规则计算
 * ------------------------------------------------------------
 * 知识点：
 *   - **规则的效果取决于特征的质量**：`riskScore`/`deviceAge`/`hourHeat` 这些特征由**特征层**计算（43 章流处理 + 46-10 特征平台），**规则引擎只消费特征快照**——两者解耦后规则可单测、特征可独立演进。
 *   - **特征的三类时效**：**实时**（本次会话点击/近 1 分钟失败次数，Flink 窗口 43-03）、**近线**（近 1 小时/1 天统计）、**离线**（用户画像 T+1）——**风控决策通常"实时 + 近线"组合**，离线特征只做辅助。
 *   - **特征缺失的处理**：规则里引用了没算出来的特征——**必须显式降级**（默认值/跳过该规则/拒绝），**静默当 false 是最危险的**（本该拦的放过了）。规则引擎要暴露"特征缺失清单"（46-10 新鲜度监控）。
 *   - **特征计算的一致性**：规则测试用的特征快照必须与线上同源同口径（54-04 训练-推理一致性在风控的对应）——**规则回归测试 = 喂历史特征快照重放**。
 *   - **特征与规则的成本账**：一次决策要算 30 个特征（含 3 次 Redis 查询 + 1 次模型推理）——**决策延迟预算**（50ms~200ms）要按特征分项分配，热点特征预计算缓存。
 *   - 易错点：规则直接查库算特征（性能与一致性双崩）；特征缺失静默 false（漏拦）；特征窗口与规则语义不匹配（"近 1 天"用 UTC 还是本地时区）；无特征版本（规则复现困难）。
 * ============================================================
 */

package com.heima.ch58;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class RealtimeFeatures {
    /** 特征快照：带来源与新鲜度 */
    record Feature(String name, Object value, long computedAtMs, String source) {
    }

    record FeatureSet(Map<String, Feature> features, long snapshotAtMs) {
        /** 缺失或过期的特征清单 */
        List<String> missingOrStale(List<String> required, long maxAgeMs) {
            return required.stream().filter(n -> {
                Feature f = features.get(n);
                return f == null || snapshotAtMs - f.computedAtMs() > maxAgeMs;
            }).toList();
        }
    }

    /** 规则：引用的特征必须声明，缺失时按策略处理 */
    record Rule(String id, List<String> requires, String expr, String action,
                String onMissing) {          // DENY / SKIP / DEFAULT
    }

    static String evaluate(Rule r, FeatureSet set, long maxAgeMs) {
        List<String> missing = set.missingOrStale(r.requires(), maxAgeMs);
        if (!missing.isEmpty()) {
            return switch (r.onMissing()) {
                case "DENY" -> r.id() + " -> BLOCK（特征缺失：" + missing + "，保守拒绝）";
                case "SKIP" -> r.id() + " -> SKIP（特征缺失：" + missing + "）";
                default -> r.id() + " -> 按默认值求值";
            };
        }
        Object amount = set.features().get("amount").value();
        Object black = set.features().get("inBlacklist").value();
        boolean hit = (amount instanceof Number n && n.longValue() > 500_000)
                || Boolean.TRUE.equals(black);
        return r.id() + " -> " + (hit ? r.action() : "PASS");
    }

    public static void main(String[] args) {
        long now = 1_000_000;
        Map<String, Feature> fs = new LinkedHashMap<>();
        fs.put("amount", new Feature("amount", 800_000L, now - 20, "realtime"));
        fs.put("inBlacklist", new Feature("inBlacklist", false, now - 5000, "nearline"));
        var set = new FeatureSet(fs, now);

        List<Rule> rules = List.of(
                new Rule("R-1", List.of("amount", "inBlacklist"), "amount>500000", "BLOCK", "DENY"),
                new Rule("R-2", List.of("deviceAge"), "deviceAge>30", "CHALLENGE", "SKIP"),
                new Rule("R-3", List.of("riskScore"), "riskScore>80", "BLOCK", "DENY"));
        System.out.println("【特征齐全的规则】");
        System.out.println("  " + evaluate(rules.get(0), set, 60_000));
        System.out.println("【特征缺失：SKIP 策略】");
        System.out.println("  " + evaluate(rules.get(1), set, 60_000));
        System.out.println("【特征缺失：DENY 策略（保守）】");
        System.out.println("  " + evaluate(rules.get(2), set, 60_000));
        System.out.println();
        System.out.println("  铁律：缺失策略必须显式声明 —— 静默 false 等于漏拦");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现特征快照服务：三类时效特征（实时/近线/离线）+ 新鲜度校验 + 缺失清单，为 5 条规则提供求值。
 * 2. 对比三种缺失策略（DENY/SKIP/DEFAULT）在同一条规则上的决策差异，给出选择判据（规则的重要性）。
 * 3. 思考题：为什么"特征缺失静默当 false"是最危险的默认？构造一笔"本该拦截但因特征缺失放行"的资损用例。
 * ============================================================
 */
