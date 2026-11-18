/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：39-02
 * 课名：索引优化与执行计划
 * ------------------------------------------------------------
 * 知识点：
 *   - **EXPLAIN 是唯一可信的证据**：type 从好到坏：`system > const > eq_ref > ref > range > index > ALL`，出现 ALL（全表扫描）基本就是事故；rows 是预估扫描行数；key/Extra 说明用没用索引、有没有回表、有没有 filesort。
 *   - **最左前缀原则**：联合索引 `(a, b, c)` 只能支持 a、a+b、a+b+c 的查询。条件里跳过 a 直接查 b，索引就用不上；a 有条件但 b 范围查询，c 就断了（范围之后失效）。**联合索引的列顺序 = 等值列在前、范围列在后、排序列跟随**。
 *   - **回表与覆盖索引**：二级索引存的是（索引列+主键），SELECT 的列不在索引里就要回主键树再取一次。`SELECT id, status FROM t WHERE status=?` 用联合索引 `(status, id)` 可以**索引覆盖**不回表，Extra 显示 `Using index`。大查询覆盖索引常常是十倍差距。
 *   - **索引选择性**：区分度 = 不重复值数/总行数。`status` 只有 3 个值，单独索引选择性差，但联合索引里作为前缀做等值过滤依然有效；`性别` 这种字段单独建索引是浪费。
 *   - **隐式类型转换是隐形索引杀手**：`WHERE phone = 13800000000`（字符串列配数字）触发转换，索引失效；函数包住列（`DATE(create_time)=...`）同样失效，应改写为范围条件 `create_time >= '...' AND < '...'`。
 *   - **深分页的 index 层优化**：`LIMIT 100000, 10` 要扫过前 10 万行。先用覆盖索引取出目标主键（延迟关联），再用主键回表，扫描成本从"全字段×10万"降到"索引×10万"。
 *   - 易错点：为每个查询条件单独建一堆单列索引（MySQL 只会用一个，联合索引才是正解）；`ORDER BY` 列不在索引里导致 filesort； believing `FORCE INDEX` 能解决设计问题（治标）；线上加索引用 `ALGORITHM=INPLACE, LOCK=NONE` 忘了确认版本支持（8.0 大多支持但要看列类型）。
 * ============================================================
 */

package com.heima.ch39;

import java.util.ArrayList;
import java.util.List;

// ==================== 示例代码 ====================

public class SlowQueryOptimize {
    /** 模拟联合索引前缀消耗：等值列可被优化器重排，范围列之后失效 */
    static int usablePrefix(List<String> eqCols, String rangeCol, String[] indexCols) {
        int used = 0;
        for (String idxCol : indexCols) {
            if (eqCols.contains(idxCol)) {
                used++;
            } else if (idxCol.equals(rangeCol)) {
                used++;               // 范围列本身可用，其后全部失效
                break;
            } else if (used < eqCols.size() && !eqCols.contains(idxCol)) {
                break;                // 前缀列缺席，断链
            }
        }
        return used;
    }

    static void leftmostRule() {
        System.out.println("【最左前缀：联合索引 (user_id, status, create_time)】");
        String[] indexCols = {"user_id", "status", "create_time"};
        String[][] queries = {
                {"user_id=,status=,create_time>", "等值x2+范围", "ref，时间列走范围"},
                {"user_id=,status=", "等值x2", "ref"},
                {"user_id=", "等值x1", "ref"},
                {"status=,user_id=", "等值x2（顺序故意颠倒）", "优化器重排等值列，仍可全用"},
                {"status=,create_time>", "跳过 user_id", "前缀断链，0 列可用"},
        };
        for (String[] q : queries) {
            List<String> eq = new ArrayList<>();
            String range = null;
            for (String part : q[0].split(",")) {
                if (part.endsWith("=")) {
                    eq.add(part.substring(0, part.length() - 1));
                } else {
                    range = part.substring(0, part.length() - 1);
                }
            }
            int used = usablePrefix(eq, range, indexCols);
            System.out.printf("  WHERE %-34s %-16s -> 用上 %d 列（%s）%n", q[0], q[1], used, q[2]);
        }
        System.out.println("  规则：等值列按索引顺序消耗；范围列可用但截断其后；缺前缀列=全废");
        System.out.println();
    }

    static void coveringIndex() {
        System.out.println("【覆盖索引：回表次数决定查询速度】");
        int matchRows = 10_000;
        System.out.printf("  SELECT * WHERE status=PAID 命中 %d 行：%n", matchRows);
        System.out.printf("    索引(status)        ：%d 次索引扫描 + %d 次回表取全字段%n", matchRows, matchRows);
        System.out.printf("    索引(status,id)     ：%d 次索引扫描 + 0 次回表（Using index）%n", matchRows);
        System.out.println("    延迟关联：先取 100 个主键再回表 -> 只回表 100 次");
        System.out.println("  设计口诀：高频查询的 SELECT 列尽量进联合索引，代价是索引变宽");
        System.out.println();
    }

    static void indexKillers() {
        System.out.println("【四个让索引失效的真实写法】");
        String[][] cases = {
                {"WHERE phone = 13800000000", "字符串列配数字 -> 隐式转换全表扫"},
                {"WHERE DATE(create_time)=CURDATE()", "函数包列 -> 改为时间范围条件"},
                {"WHERE name LIKE '%机'", "前置 % -> 只能放弃 B+ 树（或用倒排/前缀索引）"},
                {"WHERE status != 'PAID'", "不等于 -> 通常仍需扫描，靠选择性与数据分布判断"},
        };
        for (String[] c : cases) {
            System.out.printf("  %-38s %s%n", c[0], c[1]);
        }
        System.out.println();
    }

    static void explainChecklist() {
        System.out.println("【EXPLAIN 审核五步】");
        List<String> steps = List.of(
                "type 是不是 ALL/index -> 基本要 ref 以上",
                "rows 与实际命中数差几个数量级 -> 统计信息过期或索引选择错",
                "key 是否是预期索引 -> 不是就查最左前缀/类型转换",
                "Extra 有 Using filesort/Using temporary -> 排序与分组列进索引",
                "Extra 有 Using index -> 覆盖索引生效，记录为优化成果");
        for (String s : steps) {
            System.out.println("  " + s);
        }
    }

    public static void main(String[] args) {
        leftmostRule();
        coveringIndex();
        indexKillers();
        explainChecklist();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 对你项目里最慢的三条 SQL 各做一次 EXPLAIN，把 type/rows/key/Extra 记录成表格，并给出优化前后对比。
 * 2. 为 `(user_id, status, create_time)` 联合索引发明三个"能用到 3 列"与三个"只能用到 1 列"的查询，说明判断依据。
 * 3. 把一条 `LIKE '%关键词%'` 的需求改造成可行的方案（前缀索引 / 倒排 / 搜索引擎），写出选型理由。
 * 4. 思考题：为什么"等值列在前、范围列在后"是联合索引排序的黄金法则？从索引的有序性推导结论。
 * ============================================================
 */
