/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：实操 20-01
 * 课名：SQL 语义模拟器
 * ------------------------------------------------------------
 * 知识点：
 *   - 【题目要求】用内存表（List<Map<String,Object>>）实现迷你 SQL 引擎：支持 SELECT 列/带 WHERE（=、>、LIKE 前缀）/ORDER BY/LIMIT 的执行器；实现一个 JOIN（订单表 join 用户表取用户名）；输出每条 SQL 的"执行计划"（扫描行数 -> 过滤后行数）。
 *   - 【考核点】SELECT/WHERE/ORDER/LIMIT 语义、内连接语义、执行计划概念（扫描 vs 过滤）。
 *   - 【易错点】LIKE 把前缀匹配实现成 contains（应 startsWith，38 章索引原理呼应）；JOIN 产生笛卡尔积未按键过滤；ORDER 对 null 值的排序位置未定义。
 *   - 【提示】先实现"全表扫描版本"，再实现"有索引版本"（Map<key, rows>），对比扫描行数——索引价值的代码级呈现。
 * ============================================================
 */

package com.heima.practice.framework;

import java.util.List;
import java.util.stream.Collectors;

// ==================== 示例代码 ====================

public class MiniSqlTask {
    record User(long id, String name, int age) {
    }

    public static void main(String[] args) {
        List<User> users = List.of(new User(1, "a", 20), new User(2, "b", 30),
                new User(3, "c", 25));
        List<User> hit = users.stream()
                .filter(u -> u.age() > 22)
                .sorted((a, b) -> Integer.compare(b.age(), a.age()))
                .limit(2)
                .collect(Collectors.toList());
        System.out.println("  WHERE age>22 ORDER BY age DESC LIMIT 2 -> " + hit);
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现完整迷你 SQL 引擎（SELECT/WHERE/ORDER/LIMIT/JOIN），跑 5 条 SQL 并输出执行计划。
 * 2. 实现"索引加速"：给 user.id 建哈希索引，对比点查的扫描行数（10 vs 1）。
 * 3. 思考题：为什么 LIKE 前缀匹配能走索引而后缀不行？从 B+ 树有序性推导（39-02）。
 * ============================================================
 */
