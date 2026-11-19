/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：69-02
 * 课名：广告检索：定向与倒排
 * ------------------------------------------------------------
 * 知识点：
 *   - **定向（Targeting）是"找符合投放条件的广告"**：**人群定向**（年龄/性别/兴趣/人群包）、**地域定向**（城市/商圈/POI）、**时段定向**（工作日 9-18 点）、**设备/上下文定向**（App/页面类目）——**每个定向条件是一个倒排索引**。
 *   - **倒排检索的实现**：为每个定向维度建 `值 -> 广告 id 集合` 的倒排（如 `female -> {ad1, ad3, ad7}`），查询时**取各维度的交集**——**与 38-02 的倒排布尔查询完全同构**。
 *   - **检索的性能要求**：广告请求 QPS 极高、延迟预算极紧（RTB 要求 **100ms 内出价**）——**倒排要全内存、求交要用 Roaring Bitmap**（位图交并极快），而不是 List 遍历。
 *   - **扩展定向的取舍**：定向越细 -> 广告数越少（可能无广告可出）-> **需要"扩展"策略**（放宽地域到全市、放宽兴趣到类目）——**召回不足时的降级**（54-10 冷启动的广告版）。
 *   - **人群包（DMP）**：把"高价值用户/已购用户/相似人群"预先算成用户 id 集合（离线 46 章），在线按 userId 判断是否命中——**人群包是广告定向里最贵也最有效的维度**。
 *   - 易错点：定向条件用 List 求交（慢）；无召回兜底（细定向导致空广告）；人群包更新不及时（投给了已购用户）；定向维度爆炸（组合数过多）。
 * ============================================================
 */

package com.heima.ch69;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class AdTargeting {
    /** 倒排：维度 -> (值 -> 广告位图) */
    static final Map<String, Map<String, BitSet>> inverted = new LinkedHashMap<>();

    record Ad(int id, String name) {
    }

    static final List<Ad> ads = new ArrayList<>();

    static void index(int adId, String dimension, String value, int totalAds) {
        inverted.computeIfAbsent(dimension, k -> new LinkedHashMap<>())
                .computeIfAbsent(value, k -> new BitSet(totalAds + 1))
                .set(adId);
    }

    /** 求交：按维度取交集（位图 AND） */
    static BitSet retrieve(Map<String, String> targeting, int totalAds) {
        BitSet result = new BitSet(totalAds + 1);
        result.set(1, totalAds + 1);                 // 初始全集
        Map<String, Integer> perDim = new LinkedHashMap<>();
        for (Map.Entry<String, String> q : targeting.entrySet()) {
            BitSet bucket = inverted.getOrDefault(q.getKey(), Map.of())
                    .get(q.getValue());
            int before = result.cardinality();
            if (bucket == null) {
                result.clear();                      // 该维度无匹配 -> 空集
            } else {
                result.and(bucket);
            }
            perDim.put(q.getKey(), result.cardinality() - before);
        }
        System.out.println("  各维度命中变化：" + perDim);
        return result;
    }

    /** 召回不足时的扩展：逐级放宽 */
    static BitSet retrieveWithFallback(Map<String, String> targeting, int totalAds) {
        Map<String, String> relaxed = new LinkedHashMap<>(targeting);
        BitSet result = retrieve(relaxed, totalAds);
        if (!result.isEmpty()) {
            return result;
        }
        System.out.println("  召回为空 -> 放宽兴趣到类目");
        relaxed.remove("interest");
        result = retrieve(relaxed, totalAds);
        if (!result.isEmpty()) {
            return result;
        }
        System.out.println("  仍为空 -> 放宽地域到全国");
        relaxed.remove("city");
        return retrieve(relaxed, totalAds);
    }

    public static void main(String[] args) {
        ads.add(new Ad(1, "美妆 A"));
        ads.add(new Ad(2, "数码 B"));
        ads.add(new Ad(3, "美妆 C"));
        ads.add(new Ad(4, "母婴 D"));
        ads.add(new Ad(5, "通用 E"));
        int total = ads.size();

        index(1, "gender", "female", total);
        index(3, "gender", "female", total);
        index(5, "gender", "female", total);
        index(2, "gender", "male", total);
        index(4, "gender", "female", total);
        index(1, "city", "杭州", total);
        index(2, "city", "杭州", total);
        index(4, "city", "上海", total);
        index(5, "city", "杭州", total);
        index(1, "interest", "beauty", total);
        index(3, "interest", "beauty", total);
        index(2, "interest", "digital", total);

        System.out.println("【倒排索引】");
        inverted.forEach((dim, map) -> System.out.printf("  %-8s %s%n", dim, map));

        System.out.println();
        System.out.println("【定向检索：女性 + 杭州 + 美妆】");
        Map<String, String> q = new LinkedHashMap<>();
        q.put("gender", "female");
        q.put("city", "杭州");
        q.put("interest", "beauty");
        BitSet hits = retrieve(q, total);
        System.out.print("  命中广告：");
        for (int i = hits.nextSetBit(0); i >= 0; i = hits.nextSetBit(i + 1)) {
            System.out.print(ads.get(i - 1).name() + " ");
        }
        System.out.println();

        System.out.println();
        System.out.println("【召回不足 -> 逐级放宽】");
        Map<String, String> tight = new LinkedHashMap<>();
        tight.put("gender", "male");
        tight.put("city", "上海");
        tight.put("interest", "beauty");
        BitSet r = retrieveWithFallback(tight, total);
        System.out.print("  最终命中：");
        for (int i = r.nextSetBit(0); i >= 0; i = r.nextSetBit(i + 1)) {
            System.out.print(ads.get(i - 1).name() + " ");
        }
        System.out.println();
        System.out.println();
        System.out.println("  性能要点：位图交并（Roaring Bitmap）比 List 求交快一个数量级");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现倒排定向检索：3 个维度 × 10 个广告，用位图求交，验证 5 组查询的命中结果。
 * 2. 实现三级扩展兜底（兴趣 -> 地域 -> 全量），构造"细定向无结果"场景验证逐级放宽。
 * 3. 思考题：为什么 RTB 要求 100ms 内出价？从"用户等待页面渲染"与"竞价窗口"两个约束分析。
 * ============================================================
 */
