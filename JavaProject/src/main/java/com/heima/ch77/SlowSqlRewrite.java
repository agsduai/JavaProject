/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：77-08
 * 课名：慢 SQL 诊断与改写
 * ------------------------------------------------------------
 * 知识点：
 *   - **索引失效的六大场景**：**列上做函数/运算**（`DATE(col)=...`）、**前导模糊**（`LIKE '%x'`）、**隐式类型转换**（字符串列传数字）、**OR 连接不同列**、**NOT IN / !=**（低选择度）、**联合索引不满足最左前缀**——**每一条都能用"改写"救回来**。
 *   - **深分页的两种解法**：**游标分页**（`WHERE id > lastId LIMIT n`）与**延迟关联**（先用覆盖索引取主键，再回表）——**`LIMIT 1000000, 20` 的本质是"扫描 100 万行再丢弃"**。
 *   - **诊断四步**：**看慢日志（耗时/扫描行数）-> EXPLAIN（type/key/rows/Extra）-> 判断瓶颈（IO/CPU/锁）-> 改写并回归**——**没有 EXPLAIN 的改写都是猜测**。
 *   - **改写的边界**：**能改 SQL 就不加索引**（索引有写放大）；**能加索引就不拆表**；**必须拆表时先保 SQL 兼容**（77-10）。
 *   - 与 64 章的关系：开放平台的"慢接口治理"与"慢 SQL 治理"同构——**先度量、再定位、后优化、最后设门禁**。
 *   - 易错点：给列加函数还怪索引失效；`LIKE '%x%'` 硬扛；深分页不改；`SELECT *`；用 `OR` 又不想改 `UNION`。
 * ============================================================
 */

package com.heima.ch77;

import java.util.ArrayList;
import java.util.List;

// ==================== 示例代码 ====================

public class SlowSqlRewrite {
    record Case(String name, String badSql, String problem, long tableRows,
                long scannedRows, String rewrite, long rewrittenScan) {
        double speedup() {
            return (double) scannedRows / Math.max(1, rewrittenScan);
        }
    }

    public static void main(String[] args) {
        List<Case> cases = new ArrayList<>();
        cases.add(new Case(
                "函数包裹列",
                "SELECT * FROM orders WHERE DATE(created_at) = '2026-11-18'",
                "列上做函数，索引失效，全表扫描",
                10_000_000, 10_000_000,
                "WHERE created_at >= '2026-11-18 00:00:00' AND created_at < '2026-11-19 00:00:00'",
                8_000));
        cases.add(new Case(
                "前导模糊",
                "SELECT * FROM users WHERE name LIKE '%张%'",
                "前导 % 无法走 B+ 树，全表扫描",
                2_000_000, 2_000_000,
                "改用全文索引/搜索引擎；或 WHERE name LIKE '张%'（前缀匹配）",
                4_000));
        cases.add(new Case(
                "隐式转换",
                "SELECT * FROM orders WHERE order_no = 20261118001",
                "order_no 是 varchar，数字比较触发隐式转换，索引失效",
                10_000_000, 10_000_000,
                "WHERE order_no = '20261118001'（保持类型一致）",
                1));
        cases.add(new Case(
                "OR 跨列",
                "SELECT * FROM orders WHERE user_id = 1 OR status = 2",
                "OR 两侧列不同，优化器可能放弃索引",
                10_000_000, 10_000_000,
                "UNION ALL 两条分别走索引：user_id=1 / status=2",
                60_000));
        cases.add(new Case(
                "深分页",
                "SELECT * FROM orders ORDER BY id LIMIT 1000000, 20",
                "扫描 100 万行再丢弃，越翻越慢",
                10_000_000, 1_000_020,
                "WHERE id > 1000000 ORDER BY id LIMIT 20（游标分页）",
                20));
        cases.add(new Case(
                "回表过多",
                "SELECT * FROM orders WHERE user_id = 1 ORDER BY created_at LIMIT 20",
                "二级索引后回表 20 次可接受；若 SELECT * 且无覆盖则放大",
                10_000_000, 20,
                "联合索引 (user_id, created_at) + 只取需要列（覆盖索引）",
                3));

        System.out.println("【慢 SQL 案例库】");
        for (Case c : cases) {
            System.out.printf("%n  [%s] %s%n", c.name(), c.badSql());
            System.out.println("    问题：" + c.problem());
            System.out.printf("    扫描行数：%,d -> 改写后 %,d（提升 %.0f 倍）%n",
                    c.scannedRows(), c.rewrittenScan(), c.speedup());
            System.out.println("    改写：" + c.rewrite());
        }

        System.out.println();
        System.out.println("【诊断四步】");
        String[][] steps = {
                {"1. 慢日志", "耗时、扫描行数、返回行数", "先找最贵的 SQL"},
                {"2. EXPLAIN", "type/key/rows/Extra", "确认是否走索引、估算行数"},
                {"3. 定位", "IO/CPU/锁等待", "区分慢在扫描还是等待"},
                {"4. 改写+回归", "改写后对比扫描行数", "防止改坏，纳入门禁"},
        };
        for (String[] s : steps) {
            System.out.printf("  %-10s %-30s %s%n", s[0], s[1], s[2]);
        }

        System.out.println();
        System.out.println("【改写优先级】改 SQL > 加索引 > 拆表；每次只改一处并回归");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 为六类失效场景各构造一条 SQL，写出改写前后扫描行数估算与 EXPLAIN 关注点。
 * 2. 实现"延迟关联"改写：先用覆盖索引取主键再回表，比较与 `SELECT *` 的 IO 差异。
 * 3. 思考题：为什么"隐式类型转换"会让索引失效？从 B+ 树按类型比较与函数转换分析。
 * ============================================================
 */
