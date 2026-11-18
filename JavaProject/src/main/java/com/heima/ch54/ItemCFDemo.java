/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：54-03
 * 课名：协同过滤与相似度
 * ------------------------------------------------------------
 * 知识点：
 *   - **协同过滤（CF）的核心假设：相似的人喜欢相似的东西**。**ItemCF**（工业主流）："看过 A 的人也看了 B"——物品相似度矩阵；**UserCF**："与你相似的用户喜欢什么"——用户相似度。**ItemCF 更稳**（物品关系比用户兴趣变化慢、可离线预计算、可解释"因为你看过 X"）。
 *   - **相似度计算**：余弦相似（40-07 同款）/ Jaccard / 改进版（惩罚热门物品：热门共同出现不能说明相似—— IDF 加权）。**共现矩阵 + 归一化**是 ItemCF 的实现骨架。
 *   - **工程实现**：离线计算物品相似度矩阵（46 章批处理）-> 存储 Top K 相似（每物品只存最相似的 K 个）-> 在线：用户历史 -> 查相似表 -> 候选聚合打分。**矩阵分片与稀疏存储**是规模化的关键。
 *   - **衰减与时效**：行为权重随时间衰减（近期行为更能代表兴趣）；热门惩罚防止"人人看过 iPhone"把 iPhone 推到所有相似表首位。
 *   - 易错点：不惩罚热门物品（推荐全是大爆款）；相似度没有下限截断（弱相关也进候选）；离线频率过高（算力浪费）或过低（兴趣漂移跟不上）；未去重已购物品。
 * ============================================================
 */

package com.heima.ch54;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class ItemCFDemo {
    /** ItemCF：共现 -> 余弦 -> 热门惩罚（示意） */
    static Map<String, Map<String, Double>> sim = new LinkedHashMap<>();

    static void build(List<List<String>> userHistories) {
        Map<String, Integer> itemPop = new LinkedHashMap<>();
        Map<String, Map<String, Integer>> co = new LinkedHashMap<>();
        for (List<String> history : userHistories) {
            for (String a : history) {
                itemPop.merge(a, 1, Integer::sum);
                for (String b : history) {
                    if (!a.equals(b)) {
                        co.computeIfAbsent(a, k -> new LinkedHashMap<>()).merge(b, 1, Integer::sum);
                    }
                }
            }
        }
        for (var e : co.entrySet()) {
            Map<String, Double> row = new LinkedHashMap<>();
            for (var inner : e.getValue().entrySet()) {
                // 余弦近似：共现 / sqrt(流行度积)，惩罚热门
                double denom = Math.sqrt(itemPop.get(e.getKey()) * (double) itemPop.get(inner.getKey()));
                row.put(inner.getKey(), inner.getValue() / denom);
            }
            sim.put(e.getKey(), row);
        }
    }

    public static void main(String[] args) {
        build(List.of(
                List.of("手机壳", "钢化膜", "充电器"),
                List.of("手机壳", "钢化膜"),
                List.of("充电器", "数据线"),
                List.of("iPhone", "手机壳")));
        System.out.println("  手机壳 的相似表：" + sim.get("手机壳"));
        System.out.println("  钢化膜 排前（强相关），iPhone 被热门惩罚压低");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现完整 ItemCF（共现/余弦/热门惩罚/TopK 截断），用 20 个用户行为验证"手机壳"的相似表合理。
 * 2. 实现在线推荐：输入用户历史 -> 查相似表 -> 聚合去重 -> 输出 TopN；对比"惩罚热门"开关的推荐差异。
 * 3. 思考题：为什么工业界 ItemCF 比 UserCF 更主流？从物品关系稳定性、计算规模、可解释性三方面论证。
 * ============================================================
 */
