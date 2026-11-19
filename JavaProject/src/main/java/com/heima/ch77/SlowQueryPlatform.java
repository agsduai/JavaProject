/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：77-11
 * 课名：综合案例：慢查询治理平台
 * ------------------------------------------------------------
 * 知识点：
 *   - **把本章收口成一个治理闭环**：**采集慢日志**（77-01）-> **归一化 fingerprint**（把常量替换为占位符）-> **聚合排序**（总耗时/次数/最大耗时）-> **EXPLAIN 归因**（77-07/77-08）-> **索引建议**（77-09）-> **治理看门禁**（回归对比）。
 *   - **SQL 归一化的价值**：**同一条 SQL 的不同参数应聚成一组**（`WHERE id=1` 与 `id=2` 是同一 fingerprint），**否则慢日志会被参数淹没**——**归一化要处理数字、字符串、IN 列表**。
 *   - **排序口径**：**总耗时 = 次数 × 平均耗时**（治理收益最大）；**最大耗时**（体验最差）；**扫描行数**（IO 成本）——**三个口径要一起看**。
 *   - **治理的闭环**：**发现 -> 归因 -> 改写/加索引 -> 回归验证 -> 加入门禁**——**没有回归的优化不可信**（80-10 性能门禁）。
 *   - 与 42 章的关系：慢查询治理平台是"可观测性"在数据库侧的落地——**采集、聚合、告警、归因**四步与 APM 同构。
 *   - 易错点：只看单条最慢（忽略高频中慢）；不做归一化（无法聚合）；建议索引不看选择度；优化后不回归。
 * ============================================================
 */

package com.heima.ch77;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// ==================== 示例代码 ====================

public class SlowQueryPlatform {
    record SlowLog(String sql, long costMs, long scannedRows, long returnedRows, String user) {
    }

    record Fingerprint(String pattern, long count, long totalMs, long maxMs,
                       long totalScanned, String sample) {
        double avgMs() {
            return (double) totalMs / count;
        }
    }

    /** 归一化：字符串、数字、IN 列表都替换成 ? */
    static String normalize(String sql) {
        String s = sql.replaceAll("'[^']*'", "?");
        s = s.replaceAll("\\b\\d+\\b", "?");
        s = s.replaceAll("IN\\s*\\([^)]*\\)", "IN (?)");
        s = s.replaceAll("\\s+", " ").trim();
        return s;
    }

    /** 从 WHERE 中提取等值列，给出索引建议 */
    static List<String> suggestIndex(String sql) {
        List<String> cols = new ArrayList<>();
        Matcher m = Pattern.compile("(?i)\\bwhere\\b(.*?)(?:\\border\\b|\\blimit\\b|$)")
                .matcher(sql);
        if (m.find()) {
            Matcher eq = Pattern.compile("(\\w+)\\s*=\\s*\\?").matcher(m.group(1));
            while (eq.find()) {
                cols.add(eq.group(1));
            }
        }
        return cols;
    }

    public static void main(String[] args) {
        List<SlowLog> logs = List.of(
                new SlowLog("SELECT * FROM orders WHERE user_id = 1 AND status = 2", 820, 120_000, 3, "app"),
                new SlowLog("SELECT * FROM orders WHERE user_id = 2 AND status = 2", 910, 130_000, 5, "app"),
                new SlowLog("SELECT * FROM orders WHERE user_id = 3 AND status = 2", 760, 110_000, 2, "app"),
                new SlowLog("SELECT * FROM orders WHERE created_at >= '2026-11-01'", 2100, 900_000, 20_000, "report"),
                new SlowLog("SELECT * FROM users WHERE name LIKE '%张%'", 1500, 400_000, 1_000, "crm"),
                new SlowLog("SELECT * FROM orders WHERE order_no = 'A1001'", 12, 1, 1, "app"),
                new SlowLog("SELECT * FROM orders WHERE order_no = 'A1002'", 9, 1, 1, "app"));

        Map<String, List<SlowLog>> grouped = new LinkedHashMap<>();
        for (SlowLog log : logs) {
            grouped.computeIfAbsent(normalize(log.sql()), k -> new ArrayList<>()).add(log);
        }

        List<Fingerprint> fps = new ArrayList<>();
        for (Map.Entry<String, List<SlowLog>> e : grouped.entrySet()) {
            List<SlowLog> list = e.getValue();
            long totalMs = 0;
            long maxMs = 0;
            long scanned = 0;
            for (SlowLog l : list) {
                totalMs += l.costMs();
                maxMs = Math.max(maxMs, l.costMs());
                scanned += l.scannedRows();
            }
            fps.add(new Fingerprint(e.getKey(), list.size(), totalMs, maxMs, scanned,
                    list.get(0).sql()));
        }
        fps.sort(Comparator.comparingLong(Fingerprint::totalMs).reversed());

        System.out.println("【慢查询聚合（按总耗时排序）】");
        int rank = 0;
        for (Fingerprint f : fps) {
            rank++;
            System.out.printf("%n  #%d fingerprint: %s%n", rank, f.pattern());
            System.out.printf("     次数=%d 总耗时=%dms 平均=%.0fms 最大=%dms 扫描行=%,d%n",
                    f.count(), f.totalMs(), f.avgMs(), f.maxMs(), f.totalScanned());
            if (f.avgMs() <= 50) {
                System.out.println("     索引建议：已很快，无需新增索引");
            } else {
                List<String> idx = suggestIndex(f.pattern());
                System.out.println("     索引建议：" + (idx.isEmpty() ? "无等值条件，考虑范围/覆盖"
                        : "联合索引 " + idx + "（等值列在前）"));
            }
        }

        System.out.println();
        System.out.println("【治理优先级】");
        System.out.println("  1) 高频中慢（总耗时最高）：orders 的 user_id+status");
        System.out.println("  2) 低频极慢（体验最差）：报表 created_at 范围 + 覆盖索引");
        System.out.println("  3) 前导模糊：改搜索引擎/前缀匹配");
        System.out.println("  4) 已经很快的（order_no 唯一索引）：不动");

        System.out.println();
        System.out.println("【闭环】采集 -> 归一化 -> 聚合 -> EXPLAIN 归因 -> 改写/索引 -> 回归门禁");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现 SQL 归一化（字符串/数字/IN 列表），把 10 条慢日志聚成 3 个 fingerprint。
 * 2. 为聚合结果加"总扫描行数"排序，对比"按总耗时"排序的治理优先级差异。
 * 3. 思考题：为什么"高频中慢"往往比"低频极慢"治理收益更高？从总耗时与用户体验分布分析。
 * ============================================================
 */
