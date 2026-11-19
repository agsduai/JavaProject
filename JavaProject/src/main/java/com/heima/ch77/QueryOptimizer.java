/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：77-07
 * 课名：执行计划与优化器
 * ------------------------------------------------------------
 * 知识点：
 *   - **优化器是基于成本的（CBO）**：**枚举访问路径**（全表扫描/主键/二级索引/覆盖索引）与 **join 顺序**，**用统计信息估算行数**，**选总成本最低的计划**——**统计信息过期会导致选错计划**（这是"昨天还快今天慢"的常见原因）。
 *   - **访问路径的成本模型**：**全表扫描 = 页数（顺序 IO）**；**索引点查 = 树高 + 回表（随机 IO，权重更高）**；**覆盖索引 = 树高（不回表）**——**随机 IO 通常按 4 倍顺序 IO 计**，所以"小表全扫"可能比"索引回表"更便宜。
 *   - **join 的三种算法**：**Nested Loop**（小表驱动，走索引）、**Hash Join**（大表等值，内存哈希）、**Sort-Merge Join**（有序输入）——**MySQL 8 之前主要 NL，8.0.20+ 有 Hash Join**（本章按通用模型讲）。
 *   - **join 顺序的搜索空间**：**N 张表有 N! 种顺序**，优化器用**贪心/动态规划**剪枝——**小表驱动、结果集小的先 join** 是经验法则，但**最终看成本**。
 *   - 与 54 章的关系：54 章推荐系统的"召回排序"也是"多路候选 + 成本/收益打分"，**优化器就是 SQL 的"打分器"**。
 *   - 易错点：只看 `EXPLAIN` 的 type 不看 rows（估算行数才是关键）；统计信息不更新（`ANALYZE TABLE`）；join 字段无索引（笛卡尔积）；`SELECT *` 让覆盖索引失效。
 * ============================================================
 */

package com.heima.ch77;

import java.util.ArrayList;
import java.util.List;

// ==================== 示例代码 ====================

public class QueryOptimizer {
    record Table(String name, long rows, long pages, double filterSelectivity) {
        long resultRows() {
            return Math.max(1, (long) (rows * filterSelectivity));
        }
    }

    record Plan(String path, double cost, long estRows) {
    }

    /** 全表扫描：顺序 IO，成本 = 页数 */
    static Plan fullScan(Table t) {
        return new Plan(t.name() + " 全表扫描", t.pages(), t.resultRows());
    }

    /** 二级索引：树高 + 回表随机 IO（按 4 倍权重） */
    static Plan indexScan(Table t) {
        double cost = 3 + t.resultRows() * 4.0 / 100;     // 每页 100 行，随机 IO 权重 4
        return new Plan(t.name() + " 二级索引 + 回表", cost, t.resultRows());
    }

    /** 覆盖索引：树高 + 叶子扫描，不回表 */
    static Plan coveringIndex(Table t) {
        double cost = 3 + t.resultRows() * 1.0 / 100;
        return new Plan(t.name() + " 覆盖索引", cost, t.resultRows());
    }

    static Plan choose(Table t) {
        List<Plan> plans = List.of(fullScan(t), indexScan(t), coveringIndex(t));
        Plan best = plans.get(0);
        for (Plan p : plans) {
            if (p.cost() < best.cost()) {
                best = p;
            }
        }
        System.out.printf("  %-8s rows=%-8d pages=%-5d -> 选择 %-20s cost=%.1f%n",
                t.name(), t.rows(), t.pages(), best.path(), best.cost());
        return best;
    }

    /** join 顺序：枚举排列，成本 = 中间结果行数之和 */
    static void joinOrder(List<Table> tables) {
        int[] idx = new int[tables.size()];
        for (int i = 0; i < idx.length; i++) {
            idx[i] = i;
        }
        List<int[]> perms = new ArrayList<>();
        permute(idx, 0, perms);

        double bestCost = Double.MAX_VALUE;
        String bestOrder = "";
        for (int[] p : perms) {
            double cost = 0;
            long rows = 1;
            StringBuilder order = new StringBuilder();
            for (int i : p) {
                Table t = tables.get(i);
                rows = Math.max(rows, t.resultRows());
                cost += rows;
                order.append(t.name()).append(' ');
            }
            if (cost < bestCost) {
                bestCost = cost;
                bestOrder = order.toString().trim();
            }
        }
        System.out.printf("  枚举 %d 种 join 顺序 -> 最优：%s（成本 %.0f）%n",
                perms.size(), bestOrder, bestCost);
    }

    static void permute(int[] a, int k, List<int[]> out) {
        if (k == a.length) {
            out.add(a.clone());
            return;
        }
        for (int i = k; i < a.length; i++) {
            int tmp = a[k];
            a[k] = a[i];
            a[i] = tmp;
            permute(a, k + 1, out);
            tmp = a[k];
            a[k] = a[i];
            a[i] = tmp;
        }
    }

    public static void main(String[] args) {
        System.out.println("【单表访问路径选择】");
        List<Table> tables = List.of(
                new Table("orders", 1_000_000, 20_000, 0.001),
                new Table("users", 100_000, 2_000, 0.05),
                new Table("dict", 100, 1, 1.0));
        tables.forEach(QueryOptimizer::choose);

        System.out.println();
        System.out.println("【三表 join 顺序】");
        joinOrder(tables);

        System.out.println();
        System.out.println("【成本模型说明】");
        System.out.println("  全表扫描 = 页数（顺序 IO）");
        System.out.println("  索引回表 = 3 + 行数/100 * 4（随机 IO 权重 4）");
        System.out.println("  覆盖索引 = 3 + 行数/100（不回表）");
        System.out.println("  小表全扫可能优于大表索引回表：dict 只有 1 页");

        System.out.println();
        System.out.println("【统计信息过期的影响】");
        System.out.println("  rows 估算偏小 -> 优化器误选索引回表 -> 实际扫大量行 -> 慢查询");
        System.out.println("  对策：ANALYZE TABLE、监控执行计划变化、慢 SQL 回归");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 调整三张表的行数与选择度，观察"全表扫描/索引/覆盖索引"的选择如何变化。
 * 2. 给 join 成本加入"驱动表结果集"权重，验证"小表驱动"是否总是最优。
 * 3. 思考题：为什么"统计信息过期"会导致计划突变？从 CBO 依赖 rows 估算分析，并给出监控方案。
 * ============================================================
 */
