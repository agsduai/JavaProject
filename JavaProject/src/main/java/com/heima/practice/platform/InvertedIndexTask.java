/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：实操 38-01
 * 课名：倒排索引布尔检索器
 * ------------------------------------------------------------
 * 知识点：
 *   - 【题目要求】给定 10 篇文档（自行设计），分词（空格+小写）建倒排索引；实现布尔检索 `term1 AND term2 NOT term3` 与短语查询（两词相邻）；输出每个查询的命中文档与交集过程。
 *   - 【考核点】倒排索引构建、布尔集合运算（交/并/差）、最短表驱动求交、位置信息与短语查询。
 *   - 【易错点】未排序的倒排表无法双指针求交；NOT 误用 removeAll 前未复制集合；短语查询忽略位置。
 *   - 【提示】倒排表存 ArrayList 并在插入时保持有序；短语查询额外存每词在各文档中的位置列表。
 * ============================================================
 */

package com.heima.practice.platform;

import java.util.ArrayList;
import java.util.List;

// ==================== 示例代码 ====================

public class InvertedIndexTask {
    static List<Integer> intersect(List<Integer> a, List<Integer> b) {
        List<Integer> r = new ArrayList<>();
        int i = 0, j = 0;
        while (i < a.size() && j < b.size()) {
            if (a.get(i).equals(b.get(j))) {
                r.add(a.get(i));
                i++;
                j++;
            } else if (a.get(i) < b.get(j)) {
                i++;
            } else {
                j++;
            }
        }
        return r;
    }

    public static void main(String[] args) {
        List<Integer> vip = List.of(1, 3, 5, 9);
        List<Integer> active = List.of(3, 5, 7);
        System.out.println("  VIP ∩ 活跃 = " + intersect(vip, active));
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现完整倒排检索器：含 AND/OR/NOT 三种组合与短语查询，用 5 个查询验证并输出求交步数。
 * 2. 统计“用最短表驱动 vs 固定用第一张表”的比较次数差异，量化驱动表选择的收益。
 * 3. 思考题：为什么倒排表要保持有序？从跳表加速与双指针求交两个用途分析。
 * ============================================================
 */
