/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：实操 15-02
 * 课名：Map 去重与分组倒排
 * ------------------------------------------------------------
 * 知识点：
 *   - 【题目要求】实现商品标签倒排：给定商品列表（id + 标签数组），构建 Map<标签, List<商品id>>（computeIfAbsent）；实现按标签集合的"与查询"（取交集）；实现去重（同 id 合并标签取并集）输出合并后清单；三个功能各一个方法。
 *   - 【考核点】HashMap/computeIfAbsent、ArrayList 值列表维护、集合交集、LinkedHashMap 保序。
 *   - 【易错点】get 后手动判空再 add（应使用 computeIfAbsent）；交集直接 removeAll 修改原集合；去重用双重循环 O(n²)。
 *   - 【提示】交集用"最小集合驱动 + contains"或转 Set 复用 retainAll 的副本。
 * ============================================================
 */

package com.heima.practice.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class MapInvertTask {
    public static void main(String[] args) {
        Map<String, List<String>> index = new LinkedHashMap<>();
        addTo(index, "科幻", "书A");
        addTo(index, "科幻", "书B");
        addTo(index, "历史", "书C");
        System.out.println("  倒排：" + index);
    }

    static void addTo(Map<String, List<String>> index, String tag, String item) {
        index.computeIfAbsent(tag, k -> new ArrayList<>()).add(item);
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 完整实现三个方法，用 10 个商品（含重复 id）验证倒排、与查询、合并去重。
 * 2. 把值集合换成 TreeSet，观察输出变化并说明何时需要有序去重。
 * 3. 思考题：computeIfAbsent 与"get+判空+put"在并发场景下的差异是什么？
 * 
 * 
 * ## 第16章 IO 流与 File
 * ============================================================
 */
