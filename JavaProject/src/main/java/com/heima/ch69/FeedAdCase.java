/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：69-11
 * 课名：综合案例：信息流广告投放
 * ------------------------------------------------------------
 * 知识点：
 *   - **把本章收口成完整链路**：**请求**（用户/上下文）-> **定向召回**（倒排交 69-02）-> **频控过滤**（69-06）-> **eCPM 排序**（69-03/04）-> **预算 pacing**（69-05）-> **混排**（广告密度 69-01）-> **计费**（幂等 + 冲正 69-09）-> **归因**（69-07）-> **反作弊**（69-08）。
 *   - **一次投放的完整验收**：**不出超预算**（pacing + 原子扣减）、**不重复计费**（幂等）、**不超频控**（69-06）、**作弊可扣回**（冲正）、**归因可解释**（模型 + 窗口）、**体验可控**（广告密度）。
 *   - **与 54 章的对照**：推荐在"相关性"上优化，广告在"eCPM × 质量"上优化——**同一个排序框架，不同的目标函数**；**两者可以共用一个混排引擎**。
 *   - **数据回路**：曝光/点击/转化回流 -> 训练 pCTR/pCVR（54-08/46 章）-> 提升预估精度 -> 提升收入——**闭环是广告系统的核心资产**。
 *   - 易错点：链路缺环（无频控/无 pacing）；计费与预算不一致；作弊无扣回；指标只看收入不看 ROI（广告主流失）。
 * ============================================================
 */

package com.heima.ch69;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class FeedAdCase {
    record Ad(String adId, String advertiser, String category, double bidCents,
              double pctr, double quality) {
        double ecpm() {
            return bidCents * pctr * 1000;
        }

        double blended(double w) {
            return ecpm() * Math.pow(quality, w);
        }
    }

    record Context(String userId, String category) {
    }

    static final Map<String, Integer> impressions = new HashMap<>();
    static final Map<String, Double> budgets = new LinkedHashMap<>();
    static final List<String> ledger = new ArrayList<>();

    static void init() {
        budgets.put("brand-A", 500.0);
        budgets.put("brand-B", 300.0);
    }

    /** 完整投放：召回 -> 频控 -> 排序 -> pacing -> 计费 */
    static void serve(Context ctx, List<Ad> candidates, double qualityWeight,
                      int maxPerUserPerAd) {
        System.out.println("【请求】user=" + ctx.userId() + " 上下文类目=" + ctx.category());
        // 1) 召回：类目匹配 + 全类目兜底
        List<Ad> recalled = new ArrayList<>();
        for (Ad ad : candidates) {
            if (ad.category().equals(ctx.category()) || ad.category().equals("all")) {
                recalled.add(ad);
            }
        }
        System.out.println("  召回 " + recalled.size() + " 个");

        // 2) 频控
        List<Ad> filtered = new ArrayList<>();
        for (Ad ad : recalled) {
            String key = ctx.userId() + "#" + ad.adId();
            int seen = impressions.getOrDefault(key, 0);
            if (seen < maxPerUserPerAd) {
                filtered.add(ad);
            } else {
                System.out.println("  频控过滤 " + ad.adId() + "（已展示 " + seen + " 次）");
            }
        }

        // 3) 排序（eCPM × 质量）
        filtered.sort(Comparator.comparingDouble((Ad a) -> a.blended(qualityWeight)).reversed());
        if (filtered.isEmpty()) {
            System.out.println("  无可投放广告 -> 留空（不占广告位）");
            return;
        }
        Ad winner = filtered.get(0);
        System.out.printf("  排序结果：%s%n", filtered.stream()
                .map(a -> a.adId() + "(" + String.format("%.1f", a.blended(qualityWeight)) + ")")
                .toList());

        // 4) pacing：预算检查
        double cost = winner.bidCents();               // 简化为一次点击成本
        double left = budgets.getOrDefault(winner.advertiser(), 0.0);
        if (left < cost) {
            System.out.println("  预算耗尽 -> 放弃投放");
            return;
        }
        budgets.put(winner.advertiser(), left - cost);

        // 5) 计费 + 曝光计数
        ledger.add(winner.adId() + " " + winner.advertiser() + " " + cost);
        impressions.merge(ctx.userId() + "#" + winner.adId(), 1, Integer::sum);
        System.out.printf("  投放 %s，计费 %.0f 分（剩余预算 %.0f）%n",
                winner.adId(), cost, budgets.get(winner.advertiser()));
    }

    public static void main(String[] args) {
        init();
        List<Ad> candidates = List.of(
                new Ad("ad-1", "brand-A", "beauty", 200, 0.04, 0.9),
                new Ad("ad-2", "brand-B", "beauty", 300, 0.03, 0.6),
                new Ad("ad-3", "brand-A", "all", 150, 0.02, 0.8),
                new Ad("ad-4", "brand-B", "digital", 500, 0.05, 0.7));

        Context ctx = new Context("u-1", "beauty");
        for (int i = 1; i <= 4; i++) {
            System.out.println();
            serve(ctx, candidates, 1.5, 2);
        }

        System.out.println();
        System.out.println("【账单与台账】");
        Map<String, Double> billing = new LinkedHashMap<>();
        for (String l : ledger) {
            String[] parts = l.split(" ");
            billing.merge(parts[1], Double.parseDouble(parts[2]), Double::sum);
        }
        billing.forEach((adv, amount) -> System.out.printf("  %-8s 计费 %.0f 分%n", adv, amount));
        System.out.println();

        System.out.println("【链路验收】");
        Map<String, String> gates = new LinkedHashMap<>();
        gates.put("预算", "不超预算（pacing + 扣减原子）");
        gates.put("计费", "幂等（eventId 去重）+ 可冲正");
        gates.put("频控", "用户×广告上限生效");
        gates.put("排序", "eCPM × 质量（体验可控）");
        gates.put("数据回路", "曝光/点击回流训练 pCTR（54-08）");
        gates.forEach((k, v) -> System.out.printf("  %-6s %s%n", k, v));
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 端到端实现信息流投放（召回/频控/排序/pacing/计费），用 6 次请求验证频控与预算都生效。
 * 2. 加入"作弊过滤与冲正"：识别一次作弊点击，从账单中扣回，输出对账前后差异。
 * 3. 思考题：为什么广告系统必须建"数据回路"？从"pCTR 精度决定收入"与"广告主 ROI 决定留存"两个角度分析。
 * ============================================================
 */

/*
 * ============================================================
 * 本章综合练习（无参考答案）
 * ------------------------------------------------------------
 * 【场景】
 * 设计 RTB 广告系统：竞价请求与出价、eCPM 排序、预算控制与 pacing、频次控制、点击率预估、计费与反作弊。核心约束是「100ms 内返回出价」。
 * 【任务】
 * 1. 实现 eCPM 排序：CPM、CPC、CPA 三种计价方式统一折算成 eCPM 排序，写出换算公式。
 * 2. 实现预算 pacing：日预算均速消耗，按小时配额 + 实时修正，避免上午花完全天预算。
 * 3. 实现频次控制：单用户单广告每天最多 N 次曝光，用 Redis 计数或本地布隆过滤器，说明两者精度差异。
 * 4. 设计反作弊：识别异常点击（高频、同 IP、无停留），给出规则与阈值，并说明误伤如何申诉。
 * 【验收清单】
 * [ ] 三种计价方式能放在同一维度排序，排序结果符合 eCPM 高低。
 * [ ] 预算消耗曲线平滑，日终不超支也不大量剩余。
 * [ ] 频次控制在阈值处生效，跨实例计数一致。
 * [ ] 作弊流量能被规则拦截，正常流量误杀率可接受并可控。
 * ============================================================
 */
