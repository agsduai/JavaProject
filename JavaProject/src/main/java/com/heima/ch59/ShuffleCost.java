/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：59-05
 * 课名：Shuffle 原理与代价
 * ------------------------------------------------------------
 * 知识点：
 *   - **Shuffle 是"跨分区的数据重分布"**：`groupByKey`/`join`/`distinct`/`repartition` 都需要把相同 key 的数据聚到一起——**必须写本地临时文件、再经网络拉取**（Spark 的 Shuffle 是"Map 端写文件 + Reduce 端拉取"模型）。
 *   - **Shuffle 的三项成本**：① **磁盘 IO**（Map 端写、Reduce 端读）；② **网络传输**（跨节点拉取）；③ **内存**（拉取缓冲与聚合状态）。**一次 Shuffle 的成本常是整个作业的主要开销**——所以"减少 Shuffle 次数、缩小 Shuffle 数据量"是第一优化方向。
 *   - **Map 端预聚合（combiner）**：`reduceByKey`/`aggregateByKey` 在 Map 端先本地聚合（同 key 先合并再发），**大幅减少网络数据量**；`groupByKey` 不做预聚合（全量移动）——**能用 reduceByKey 就不用 groupByKey**（这是 Spark 最经典的优化建议）。
 *   - **Shuffle 文件数量**：Map Task 数 × Reduce Task 数 个文件（或合并机制减少），**小分区 + 大分区数 = 海量小文件**（46-02 的问题在 Shuffle 侧再现）——`spark.shuffle.file.buffer` 与合并（consolidation）参数可缓解。
 *   - **Shuffle 的替代方案**：**Broadcast Join**（小表广播到每个 Executor，大表不移动，零 Shuffle——59-07 的典型优化）；**分桶表**（预分区存储，join 时免 Shuffle，46-05 表格式的分桶）；**预聚合表**（DWS 层 46-01）。
 *   - 易错点：`groupByKey` 处理大 key（单 key 全量数据压在一个 Task）；Shuffle 分区数默认 200 跑大作业（单分区过大）；不监控 Shuffle 读写量（Spark UI 的 Shuffle Read/Write 是首要指标）；join 大表时不用 broadcast（小表被 Shuffle 白白移动）。
 * ============================================================
 */

package com.heima.ch59;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class ShuffleCost {
    record Row(String key, long amount) {
    }

    /** 对比 groupByKey 与 reduceByKey 的网络传输量 */
    static void compareAggregation(List<Row> rows) {
        Map<String, List<Long>> grouped = new LinkedHashMap<>();
        for (Row r : rows) {
            grouped.computeIfAbsent(r.key(), k -> new ArrayList<>()).add(r.amount());
        }
        long groupByKeyBytes = grouped.values().stream().mapToLong(List::size).sum() * 8;

        Map<String, Long> reduced = new LinkedHashMap<>();
        for (Row r : rows) {
            reduced.merge(r.key(), r.amount(), Long::sum);
        }
        long reduceByKeyBytes = reduced.size() * 8L;          // 每 key 一个聚合值

        System.out.printf("  记录数=%d 唯一 key=%d%n", rows.size(), grouped.size());
        System.out.printf("  groupByKey 网络量 ≈ %d 字节（全量移动）%n", groupByKeyBytes);
        System.out.printf("  reduceByKey 网络量 ≈ %d 字节（Map 端预聚合）%n", reduceByKeyBytes);
        System.out.printf("  优化收益：%.1f 倍%n", (double) groupByKeyBytes / reduceByKeyBytes);
    }

    /** Broadcast Join：小表不 Shuffle，大表原地映射 */
    static void broadcastJoin(List<Row> bigTable, Map<String, String> smallDim) {
        long shuffledRows = 0;                                 // 大表不移动
        List<String> out = new ArrayList<>();
        for (Row r : bigTable) {
            String dimValue = smallDim.getOrDefault(r.key(), "UNKNOWN");
            out.add(r.key() + "=" + dimValue);
        }
        System.out.printf("  Broadcast Join：大表 %d 行零 Shuffle，小表 %d 行广播%n",
                bigTable.size(), smallDim.size());
        System.out.println("  示例输出：" + out.subList(0, Math.min(3, out.size())));
    }

    public static void main(String[] args) {
        List<Row> rows = new ArrayList<>();
        String[] keys = {"a", "b", "c", "d"};
        for (int i = 0; i < 1000; i++) {
            rows.add(new Row(keys[i % keys.length], 100 + i));
        }
        System.out.println("【聚合算子对比】");
        compareAggregation(rows);
        System.out.println();
        System.out.println("【Broadcast Join】");
        Map<String, String> dim = Map.of("a", "手机", "b", "电脑", "c", "配件");
        broadcastJoin(rows.subList(0, 10), dim);
        System.out.println();
        System.out.println("  优化优先级：消除 Shuffle > 缩小 Shuffle 量 > 调 Shuffle 参数");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 用模拟数据量化 `groupByKey` 与 `reduceByKey` 的网络传输量差异（不同 key 基数），说明为什么"大 key 基数"时差距缩小。
 * 2. 为"订单表 join 商品维度表（1 万行）"设计 Broadcast Join，估算省下的 Shuffle 数据量。
 * 3. 思考题：为什么"小表广播"能零 Shuffle？从"移动数据 vs 移动代码"的取舍分析（并说明广播表大小的上限考量）。
 * ============================================================
 */
