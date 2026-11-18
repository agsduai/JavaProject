/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：20-08
 * 课名：索引与视图
 * ------------------------------------------------------------
 * 知识点：
 *   - 索引是帮助 MySQL 快速查找数据的数据结构（B+ 树），类似书的目录，能大幅提升查询速度。
 *   - 创建索引：`create index 索引名 on 表名 (字段);`，删除用 `drop index 索引名 on 表名;`。
 *   - 主键自动创建聚簇索引；`unique` 约束字段自动创建唯一索引。
 *   - 索引失效常见场景：`like '%xx'` 前置通配、字段上使用函数、隐式类型转换、`or` 连接非索引字段。
 *   - 视图是虚拟表，保存的是查询语句而非数据：`create view 视图名 as select ...`。
 *   - 视图作用：简化复杂查询、隐藏敏感字段、统一数据口径。
 *   - 易错：索引不是越多越好，会降低增删改速度并占用空间；小表、低区分度字段不必建索引。
 * ============================================================
 */

package com.heima.ch20;

import java.util.ArrayList;
import java.util.List;

// ==================== 示例代码 ====================

/**
 * 索引失效的判断在代码层面体现为"查询条件是否能让数据库用上索引"。
 * 这里用规则校验的方式，演示代码里如何提前拦截会导致全表扫描的写法。
 */
public class IndexRuleChecker {
    /** 检查 like 写法是否会导致索引失效 */
    static boolean likeUsesIndex(String pattern) {
        return !pattern.startsWith("%") && !pattern.startsWith("_");
    }

    /** 检查条件字段上是否套了函数 */
    static boolean hasFunctionOnColumn(String condition) {
        String lower = condition.toLowerCase();
        return lower.contains("date(") || lower.contains("substr(")
                || lower.contains("upper(") || lower.contains("lower(")
                || lower.contains("+ 0") || lower.contains("cast(");
    }

    public static void main(String[] args) {
        List<String> patterns = new ArrayList<>(List.of("张%", "%张", "%张%", "张_"));
        for (String p : patterns) {
            System.out.println("like '" + p + "' 命中索引：" + likeUsesIndex(p));
        }

        String[] conditions = {
                "name = ?",
                "date(create_time) = '2026-11-17'",
                "substr(name, 1, 1) = '张'",
                "age = ?"
        };
        for (String c : conditions) {
            System.out.println(c + " -> 字段上有函数：" + hasFunctionOnColumn(c));
        }

        // 改正示例：把函数挪到比较值一侧
        System.out.println("改前：date(create_time) = '2026-11-17'");
        System.out.println("改后：create_time >= '2026-11-17 00:00:00' "
                + "and create_time < '2026-11-18 00:00:00'");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 给 `book` 表的书名和作者字段分别建索引，用 `explain` 对比建索引前后的执行计划差异。
 * 2. 创建一个视图，展示 `book` 表的书名、作者和价格（价格只显示 8 折后的结果），并查询该视图。
 * 3. 列出至少 4 种会导致索引失效的写法，并各给出一个改正示例。
 * ============================================================
 */
