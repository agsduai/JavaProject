/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：46-02
 * 课名：列式存储与 Parquet
 * ------------------------------------------------------------
 * 知识点：
 *   - **行存与列存 answering 的查询不同**：行存（MySQL）按行组织——OLTP 点查改一行快；列存（Parquet）按列组织——分析查询"只碰 3 列扫 10 亿行"时 IO 直降一个数量级。**OLTP 用行存，OLAP 用列存**是存储选型的第一判断。
 *   - **列存的两个物理收益**：**裁剪**（查询只读需要的列文件段）+ **压缩**（同列类型一致、基数低，RLE/字典编码压缩率远高于行存）。真实分析负载压缩比常见 5~10 倍。
 *   - **Parquet 的文件结构**：Row Group（行组，通常 128MB~1GB，行存段）内按 Column Chunk 分列，列内分 Page；**每个 Column Chunk 带统计信息（min/max/null 计数）**——查询谓词（WHERE date='11-17'）直接跳过不含目标的 Row Group，这就是**谓词下推**的物理基础。
 *   - **分区与裁剪的双层结构**：目录分区（date=2026-11-17/ 按目录裁剪）+ 文件内统计（Row Group 裁剪）。**分区键的选择**（高频过滤列、基数适中）直接决定查询速度——分区选错了查询永远全扫。
 *   - **写入端的设计**：小文件是列存的死敌（每个文件一个 footer，打开成本高）——**写入攒批**（每 128MB~1GB 落一个文件）、流式写入用 compaction 合并（46-06）。
 *   - **与 MySQL 的分工**：MySQL 服务交易（行存 + 索引）；Parquet 湖表服务分析（列存 + 扫描）。**同一个业务两类存储并存**，由 CDC（43-09/46-09）连接。
 *   - 易错点：把高频更新数据放列存（更新 = 重写整个文件，代价爆炸）；分区键选低基数列（每分区几行，小文件爆炸）；Row Group 太小（统计信息开销占比高）；忽略 null 与编码列的统计（裁剪失效）。
 * ============================================================
 */

package com.heima.ch46;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class ParquetColumnar {
    /** 模拟 Row Group 统计：min/max 用于谓词裁剪 */
    record RowGroup(int id, String dateMin, String dateMax, double amountMin,
                    double amountMax, long rows, int bytes) {
        boolean mayMatch(String dateFilter) {
            return dateFilter.compareTo(dateMin) >= 0 && dateFilter.compareTo(dateMax) <= 0;
        }
    }

    static void rowGroupPruning() {
        System.out.println("【谓词下推：统计信息跳过无关 Row Group】");
        List<RowGroup> groups = List.of(
                new RowGroup(1, "2026-11-15", "2026-11-15", 10, 500, 1_000_000, 130),
                new RowGroup(2, "2026-11-16", "2026-11-16", 12, 800, 1_000_000, 128),
                new RowGroup(3, "2026-11-17", "2026-11-17", 15, 999, 1_000_000, 132));
        String filter = "2026-11-17";
        long scanned = 0, skipped = 0;
        for (RowGroup g : groups) {
            if (g.mayMatch(filter)) {
                scanned += g.bytes();
                System.out.printf("  RG%d [%s~%s] -> 读取 %dMB%n", g.id(), g.dateMin(), g.dateMax(), g.bytes());
            } else {
                skipped += g.bytes();
                System.out.printf("  RG%d [%s~%s] -> 跳过（统计不含目标日期）%n", g.id(), g.dateMin(), g.dateMax());
            }
        }
        System.out.printf("  扫描 %dMB / 总 %dMB，省 %.0f%% IO%n", scanned, scanned + skipped,
                skipped * 100.0 / (scanned + skipped));
        System.out.println();
    }

    static void columnVsRow() {
        System.out.println("【同一查询：行存 vs 列存的 IO 对比】");
        long rows = 1_000_000_000L;
        int rowWidth = 200, neededCols = 3, colWidth = 8;
        long rowIo = rows * rowWidth;                      // 行存：每行全字段
        long colIo = rows * neededCols * colWidth;         // 列存：只碰 3 列
        System.out.printf("  行存读取 %d 行 × %dB = %.0f GB%n", rows, rowWidth, rowIo / 1024.0 / 1024 / 1024);
        System.out.printf("  列存读取 %d 行 × %d 列 × %dB = %.1f GB（%.0f 倍差距，压缩后更小）%n",
                rows, neededCols, colWidth, colIo / 1024.0 / 1024 / 1024,
                (double) rowIo / colIo);
        System.out.println("  结论：宽表 + 少列查询的分析负载，列存是数量级优势");
        System.out.println();
    }

    static void partitionChoice() {
        System.out.println("【分区键选择：裁剪效率与小文件平衡】");
        String[][] rows = {
                {"按天分区", "每天 500 万行 -> 单文件 300MB", "时间范围查询的主键，常规选择"},
                {"按 shop_id 分区", "50 万商家 -> 每分区 30 行", "小文件灾难，禁止"},
                {"不分区", "全表扫描依赖 Row Group 统计", "小表可行，大表慎用"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-12s %-32s %s%n", r[0], r[1], r[2]);
        }
        System.out.println("  经验：分区后单分区 ≥ 数百 MB；嵌套分区（天+小时）要验证分区数");
        System.out.println();
    }

    static void smallFileProblem() {
        System.out.println("【小文件：列存的慢性病】");
        Map<String, String> impact = new LinkedHashMap<>();
        impact.put("元数据开销", "100 万文件 × 每文件 footer/kv 元数据 = NameNode/目录压力");
        impact.put("调度开销", "每个文件一个 task，调度时间超过计算时间");
        impact.put("治理手段", "写入攒批 + 定时 compaction 合并到目标大小");
        for (Map.Entry<String, String> e : impact.entrySet()) {
            System.out.printf("  %-8s %s%n", e.getKey(), e.getValue());
        }
        System.out.println("  目标文件大小：128MB~1GB；流式写入后每天 compaction 是标配运维");
    }

    public static void main(String[] args) {
        rowGroupPruning();
        columnVsRow();
        partitionChoice();
        smallFileProblem();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 模拟一个 5 列 100 万行的数据集，分别按行存/列存估算"SELECT 2 列 + 日期过滤"的 IO，验证列存优势的数量级。
 * 2. 为订单事实表选择分区键（候选：天/小时/商家/无），列出计算过程（行数、文件大小、典型查询模式）。
 * 3. 思考题：为什么高频更新的表不适合放 Parquet？从文件不可变与更新重写的代价分析，并给出可行的替代方案（CDC + 追加/merge-on-read）。
 * ============================================================
 */
