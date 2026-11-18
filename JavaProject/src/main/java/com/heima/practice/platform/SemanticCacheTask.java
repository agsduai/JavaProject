/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：实操 40-02
 * 课名：语义缓存层
 * ------------------------------------------------------------
 * 知识点：
 *   - 【题目要求】实现语义缓存：输入规范化（去标点/小写/去空格）做精确缓存键 + “向量相似度”简化为关键词重合率（≥0.9 视为相同问题）；缓存命中返回缓存值并计数，未命中调“模拟模型”并写缓存；统计 20 条请求（含 8 条语义重复）的命中率与节省的 token 数。
 *   - 【考核点】缓存键设计、相似阈值与误答风险的平衡（40-10）、命中统计与成本节省量化。
 *   - 【易错点】阈值过低（不同问题被误命中，返回错误答案）；缓存无 TTL（知识更新后旧答案残留）；命中率只算次数不算 token 节省。
 *   - 【提示】重合率 = 交集词数 / 并集词数（Jaccard 简化版）；阈值定 0.9 并在测试中验证边界样本。
 * ============================================================
 */

package com.heima.practice.platform;

import java.util.HashSet;
import java.util.Set;

// ==================== 示例代码 ====================

public class SemanticCacheTask {
    static String normalize(String s) {
        return s.replace("？", "").replace("?", "").replace(" ", "").toLowerCase();
    }

    static double jaccard(String a, String b) {
        Set<Character> sa = new HashSet<>(), sb = new HashSet<>();
        for (char c : a.toCharArray()) {
            sa.add(c);
        }
        for (char c : b.toCharArray()) {
            sb.add(c);
        }
        long inter = sa.stream().filter(sb::contains).count();
        long union = sa.size() + sb.size() - inter;
        return union == 0 ? 0 : (double) inter / union;
    }

    public static void main(String[] args) {
        System.out.printf("  「退货运费谁出」 vs 「退货运费谁出？」 规范化相等：%b%n",
                normalize("退货运费谁出").equals(normalize("退货运费谁出？")));
        System.out.printf("  与「退货运费谁承担」 字符重合率=%.2f%n",
                jaccard(normalize("退货运费谁出"), normalize("退货运费谁承担")));
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现完整语义缓存：规范化 + Jaccard 相似 + TTL，用 20 条请求统计命中率与节省 token 数。
 * 2. 构造 3 个“阈值过低导致误命中”的反例（如“退款政策”vs“退款进度”），验证 0.9 阈值的必要性。
 * 3. 思考题：为什么语义缓存的失效策略要关联知识库更新？设计“知识库版本号参与缓存键”的方案。
 * 
 * 
 * ## 第41章 CI/CD 与发布工程
 * ============================================================
 */
