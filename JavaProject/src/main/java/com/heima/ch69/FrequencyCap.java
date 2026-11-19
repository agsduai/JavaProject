/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：69-06
 * 课名：频控与去重
 * ------------------------------------------------------------
 * 知识点：
 *   - **频控（Frequency Capping）是"同一个用户对同一个广告最多看几次"**：`同一广告/同一广告主/同一类目` 在 `1 天/7 天` 内最多 N 次——**无频控的用户体验是"一个广告追着你跑"**。
 *   - **频控的维度组合**：**广告级**（单个广告 3 次/天）、**广告主级**（同一广告主 10 次/天）、**类目级**（美妆类 20 次/天）——**层级叠加控制**（69-01 的广告密度是页面级，频控是时间级）。
 *   - **实现：计数 + TTL**：`key = userId + campaignId + 日期`，Redis INCR + EXPIRE——**读多写多的热点**，用批量读写 + 本地缓存降低 Redis 压力（25 章）。
 *   - **频控与召回的联动**：**频控过滤放在召回之后、排序之前**——太早过滤会误杀（阈值未定），太晚浪费排序算力（54-07 曝光过滤同款位置）。
 *   - **与 54-07 的关系**：曝光去重是"同一次浏览不重复"，频控是"一段时间内不超次数"——**两者一起构成"广告疲劳度"控制**。
 *   - 易错点：频控只做广告级不做用户级（同一广告主换素材轰炸）；计数用 DB（高频写打爆）；无 TTL（计数永久累积，广告再也出不去）；频控在排序后（浪费算力）。
 * ============================================================
 */

package com.heima.ch69;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class FrequencyCap {
    /** 频控规则：维度 + 周期 + 上限 */
    record Rule(String dimension, int periodDays, int maxCount) {
    }

    /** 计数存储：key = userId|dimension|value|bucket */
    static final Map<String, Integer> counters = new LinkedHashMap<>();

    static String key(String userId, String dimension, String value, long dayBucket) {
        return userId + "|" + dimension + "|" + value + "|" + dayBucket;
    }

    static long dayBucketOf(long nowMs) {
        return nowMs / 86_400_000L;
    }

    /** 是否允许展示（未超频控） */
    static boolean allowed(String userId, String adId, String advertiser,
                           String category, List<Rule> rules, long nowMs) {
        for (Rule r : rules) {
            String value = switch (r.dimension()) {
                case "ad" -> adId;
                case "advertiser" -> advertiser;
                case "category" -> category;
                default -> "";
            };
            // 简化：周期用"当前天桶"，实际按周期滚动窗口
            String k = key(userId, r.dimension(), value, dayBucketOf(nowMs));
            int used = counters.getOrDefault(k, 0);
            if (used >= r.maxCount()) {
                System.out.printf("    频控拦截：%s（%s）今日已 %d 次，上限 %d%n",
                        r.dimension(), value, used, r.maxCount());
                return false;
            }
        }
        return true;
    }

    static void record(String userId, String adId, String advertiser, String category,
                       List<Rule> rules, long nowMs) {
        for (Rule r : rules) {
            String value = switch (r.dimension()) {
                case "ad" -> adId;
                case "advertiser" -> advertiser;
                case "category" -> category;
                default -> "";
            };
            counters.merge(key(userId, r.dimension(), value, dayBucketOf(nowMs)), 1, Integer::sum);
        }
    }

    public static void main(String[] args) {
        List<Rule> rules = List.of(
                new Rule("ad", 1, 2),
                new Rule("advertiser", 1, 3),
                new Rule("category", 1, 5));
        System.out.println("【频控规则】");
        rules.forEach(r -> System.out.printf("  %-12s 周期 %d 天，上限 %d 次%n",
                r.dimension(), r.periodDays(), r.maxCount()));

        System.out.println();
        System.out.println("【同一用户连续请求同一广告 5 次】");
        long now = 10 * 86_400_000L + 3_600_000L;
        List<String> shown = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            System.out.printf("  第 %d 次请求：%n", i);
            if (allowed("u-1", "ad-1", "brand-X", "beauty", rules, now)) {
                shown.add("ad-1#" + i);
                record("u-1", "ad-1", "brand-X", "beauty", rules, now);
                System.out.println("    展示成功");
            } else {
                System.out.println("    不展示（换其他广告或留空）");
            }
        }
        System.out.println("  实际展示：" + shown.size() + " 次（广告级上限 2）");

        System.out.println();
        System.out.println("【换素材但同广告主：广告主级兜底】");
        for (int i = 1; i <= 3; i++) {
            String adId = "ad-x-" + i;
            System.out.printf("  请求 %s：%s%n", adId,
                    allowed("u-1", adId, "brand-X", "beauty", rules, now)
                            ? "允许（新素材）" : "拦截（广告主已达上限）");
            if (allowed("u-1", adId, "brand-X", "beauty", rules, now)) {
                record("u-1", adId, "brand-X", "beauty", rules, now);
            }
        }

        System.out.println();
        System.out.println("  实现：Redis INCR + 过期（按周期设 TTL）；过滤位置在召回后、排序前（69-02）");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现三级频控（广告/广告主/类目），用 10 次请求验证各层上限生效与拦截顺序。
 * 2. 实现"滚动窗口"频控（不是自然日，而是最近 24 小时），对比自然日桶的差异。
 * 3. 思考题：为什么频控要放在"召回后、排序前"？从"过滤成本"与"误杀风险"两个角度分析。
 * ============================================================
 */
