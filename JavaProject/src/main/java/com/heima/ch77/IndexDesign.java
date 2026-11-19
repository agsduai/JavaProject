/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：77-09
 * 课名：索引设计方法论
 * ------------------------------------------------------------
 * 知识点：
 *   - **索引设计三问**：**哪些列出现在 WHERE/JOIN/ORDER BY**、**选择度多高**（`distinct/rows`）、**是否需要覆盖查询列**——**选择度低的列（性别/状态）单独建索引几乎无用**。
 *   - **联合索引的最左前缀**：**`(a,b,c)` 能支持 `a`、`a,b`、`a,b,c`**，**不能支持 `b` 或 `b,c`**——**等值列在前、范围列在后**（范围列后面的列无法用于定位）。
 *   - **三星索引**：**一星：WHERE 列在索引中**；**二星：ORDER BY 列顺序一致**；**三星：SELECT 列被索引覆盖**——**三星索引是"理想态"，实际按查询频率取舍**。
 *   - **索引的代价**：**写放大**（每次 INSERT/UPDATE 维护所有索引）、**空间**（二级索引存主键）、**优化器选择成本**（索引太多反而选错）——**索引不是越多越好**。
 *   - 与 77-02 的关系：**B+ 树的页分裂与回表成本决定了索引设计**——**设计的是"树"，不是"字段清单"**。
 *   - 易错点：给每个 WHERE 列单独建索引（应建联合索引）；联合索引顺序随意（等值在前）；冗余索引（`(a)` 与 `(a,b)` 前者冗余）；不删无用索引。
 * ============================================================
 */

package com.heima.ch77;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

// ==================== 示例代码 ====================

public class IndexDesign {
    record Column(String name, long distinct, long rows) {
        double selectivity() {
            return (double) distinct / rows;
        }
    }

    static boolean leftmostPrefix(List<String> index, List<String> where) {
        if (where.size() > index.size()) {
            return false;
        }
        for (int i = 0; i < where.size(); i++) {
            if (!index.get(i).equals(where.get(i))) {
                return false;
            }
        }
        return true;
    }

    static boolean covering(List<String> index, List<String> select) {
        return index.containsAll(select);
    }

    static boolean redundant(List<String> a, List<String> b) {
        // a 是 b 的前缀 -> a 冗余
        if (a.size() >= b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (!a.get(i).equals(b.get(i))) {
                return false;
            }
        }
        return true;
    }

    public static void main(String[] args) {
        long rows = 10_000_000;
        List<Column> columns = List.of(
                new Column("user_id", 2_000_000, rows),
                new Column("status", 5, rows),
                new Column("created_at", 3_000_000, rows),
                new Column("amount", 1_000_000, rows));
        System.out.println("【列选择度】");
        columns.forEach(c -> System.out.printf("  %-12s distinct=%,d 选择度=%.3f%n",
                c.name(), c.distinct(), c.selectivity()));

        System.out.println();
        System.out.println("【候选联合索引】");
        List<List<String>> candidates = List.of(
                List.of("user_id", "created_at"),
                List.of("status", "created_at"),
                List.of("user_id", "status", "created_at"));
        List<String> where = List.of("user_id");
        List<String> select = List.of("created_at", "amount");
        for (List<String> idx : candidates) {
            boolean prefix = leftmostPrefix(idx, where);
            boolean cover = covering(idx, select);
            System.out.printf("  %-34s 最左前缀=%s 覆盖=%s%n",
                    idx, prefix ? "是" : "否", cover ? "是" : "否");
        }

        System.out.println();
        System.out.println("【查询：WHERE user_id=? ORDER BY created_at LIMIT 20】");
        System.out.println("  推荐索引：(user_id, created_at, amount)");
        System.out.println("  理由：user_id 等值在前，created_at 排序紧随其后，amount 覆盖查询");
        System.out.println("  -> 一星（WHERE）+ 二星（ORDER BY）+ 三星（覆盖）");

        System.out.println();
        System.out.println("【冗余索引检测】");
        List<List<String>> existing = new ArrayList<>(List.of(
                List.of("user_id"),
                List.of("user_id", "created_at"),
                List.of("status")));
        Set<Integer> drop = new LinkedHashSet<>();
        for (int i = 0; i < existing.size(); i++) {
            for (int j = 0; j < existing.size(); j++) {
                if (i != j && redundant(existing.get(i), existing.get(j))) {
                    drop.add(i);
                }
            }
        }
        for (int i = 0; i < existing.size(); i++) {
            System.out.printf("  %-30s %s%n", existing.get(i),
                    drop.contains(i) ? "冗余（被更长前缀覆盖）" : "保留");
        }

        System.out.println();
        System.out.println("【设计原则】等值在前、范围在后、排序列跟随、覆盖常用列、及时删冗余");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 给"订单表按用户查最近 20 条"设计联合索引，写出最左前缀与覆盖判断。
 * 2. 实现"冗余索引检测"：判断 `(a)` 是否被 `(a,b)` 覆盖，并给出删除建议。
 * 3. 思考题：为什么"选择度低的列"不适合单独建索引？从 B+ 树区分度与回表行数分析。
 * ============================================================
 */
