/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：实操 34-01
 * 课名：LRU 缓存实现
 * ------------------------------------------------------------
 * 知识点：
 *   - 【题目要求】实现 LRU 缓存三版：① 哈希表 + 双向链表手写（O(1) get/put）；② LinkedHashMap(accessOrder=true) 版；③ 带 TTL 过期的扩展版（过期惰性删除）；用随机访问序列验证三版行为一致；标注每版的容量淘汰次数统计与时间复杂度。
 *   - 【考核点】双向链表 + 哈希表组合、LinkedHashMap accessOrder 语义、TTL 惰性删除、复杂度分析。
 *   - 【易错点】手写链表的摘除漏 prev/next 指针；LinkedHashMap 重写 removeEldestEntry 的边界；TTL 惰性删除在读时才清理（size 不准需补偿）。
 *   - 【提示】淘汰次数是缓存调优的关键指标（淘汰频繁=容量不足或访问模式不友好）。
 * ============================================================
 */

package com.heima.practice.deepdive;

import java.util.LinkedHashMap;
import java.util.Map;

// ==================== 示例代码 ====================

public class LruTask {
    public static void main(String[] args) {
        Map<String, String> lru = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                return size() > 3;
            }
        };
        lru.put("a", "1");
        lru.put("b", "2");
        lru.put("c", "3");
        lru.get("a");                       // a 变为最新
        lru.put("d", "4");                  // 淘汰最久未用的 b
        System.out.println("  LRU 现状：" + lru.keySet() + "（b 被淘汰）");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现手写双向链表版（O(1)）与 LinkedHashMap 版行为对拍（随机序列 1000 次一致）。
 * 2. 加 TTL 扩展版（惰性删除 + size 补偿），验证过期键在读时清除与容量淘汰共存。
 * 3. 思考题：为什么 LRU 对"扫描型访问"不友好？构造一次全量扫描把热数据全部挤出的用例，并给出 LFU/分桶 LRU 的改进思路。
 * ============================================================
 */
