/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：59-04
 * 课名：分区与并行度
 * ------------------------------------------------------------
 * 知识点：
 *   - **分区数是并行度的物理上限**：分区数 = 并发 Task 数的上限（一个分区一个 Task）。**分区太少**（如 1 个）-> 只有 1 个 Task 跑，其余 Executor 空闲；**分区太多**（如 10 万小分区）-> Task 调度开销超过计算本身。
 *   - **经验值**：**单分区数据量 128MB~1GB**；总分区数 ≈ 集群可用核数 × 2~3（让 Task 有波次感，避免长尾影响整体）；**Shuffle 后分区数可单独调整**（`spark.sql.shuffle.partitions` 默认 200，小数据量要下调）。
 *   - **分区与文件的关系**：读文件时**分区数 ≈ 文件大小 ÷ 128MB**（HDFS 块大小），小文件多会导致分区过多（46-02 小文件问题的 Spark 侧）——**读前先 compaction**。
 *   - **`repartition` vs `coalesce`**：`repartition(n)` 全量 Shuffle（可增可减，均匀）；`coalesce(n)` 仅合并（减少分区不 Shuffle，可能不均）——**减分区优先 coalesce，增分区或要均匀用 repartition**。
 *   - **数据本地性**：Task 尽量调度到数据所在节点（`NODE_LOCAL` > `RACK_LOCAL` > `ANY`）——**分区过大 + 副本少时，远程拉取会严重拖慢**（46-03 存算分离的代价之一）。
 *   - 易错点：用默认 200 分区跑小数据（任务开销占大头）；`coalesce(1)` 写单文件（并行度归零）；分区数按"总核数"而非"核数×2~3"设（长尾拖慢整体）；不监控 Task 时长分布（倾斜的前兆 59-08）。
 * ============================================================
 */

package com.heima.ch59;

import java.util.ArrayList;
import java.util.List;

// ==================== 示例代码 ====================

public class PartitionDemo {
    /** 按目标分区大小切分总数据量 */
    static int partitionsBySize(long totalBytes, long targetBytes) {
        return (int) Math.max(1, (totalBytes + targetBytes - 1) / targetBytes);
    }

    /** 评估分区是否合理：单分区大小 + Task 波次 */
    static void assess(int partitions, long totalBytes, int cores) {
        double perPartitionMb = totalBytes / 1024.0 / 1024 / partitions;
        double waves = partitions / (double) Math.max(1, cores);
        System.out.printf("  分区=%d 单分区≈%.0fMB 波次≈%.1f%n", partitions, perPartitionMb, waves);
        if (perPartitionMb > 1024) {
            System.out.println("    问题：单分区过大 -> 内存压力/GC 长尾");
        }
        if (perPartitionMb < 100) {
            System.out.println("    问题：分区过碎 -> 调度开销超过计算");
        }
        if (waves < 1) {
            System.out.println("    问题：分区数少于核数 -> 资源闲置");
        }
        if (waves > 10) {
            System.out.println("    提示：波次偏多，长尾 Task 会拖慢整体");
        }
    }

    /** coalesce 与 repartition 的语义差异（用分布模拟） */
    static void compareRebalance() {
        List<Integer> before = new ArrayList<>(List.of(100, 100, 0, 0, 0, 0));
        System.out.println("  原始分区数据量：" + before);
        List<Integer> coalesced = List.of(before.get(0) + before.get(1), 0, 0, 0);   // 只合并，不搬移
        System.out.println("  coalesce(4)：    " + coalesced + "（不均，但零 Shuffle）");
        List<Integer> repartitioned = List.of(50, 50, 50, 50);                       // 全量重分布
        System.out.println("  repartition(4)： " + repartitioned + "（均匀，但全量 Shuffle）");
    }

    public static void main(String[] args) {
        long totalBytes = 20L * 1024 * 1024 * 1024;      // 20GB
        int cores = 32;
        System.out.println("【分区数评估：20GB 数据 / 32 核】");
        for (long target : new long[]{512L * 1024 * 1024, 256L * 1024 * 1024}) {
            int p = partitionsBySize(totalBytes, target);
            System.out.printf("目标分区 %dMB -> %d 个分区%n", target / 1024 / 1024, p);
            assess(p, totalBytes, cores);
        }
        System.out.println();
        compareRebalance();
        System.out.println("  口诀：单分区 128MB~1GB，波次 2~3，减分区用 coalesce");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 为 50GB 数据、64 核集群计算推荐分区数（按 128MB/512MB 两档），并评估波次与单分区大小。
 * 2. 用模拟数据对比 `coalesce` 与 `repartition` 后的分区均匀度与"搬移数据量"，说明各自的适用场景。
 * 3. 思考题：为什么"分区数 = 核数"不是最优？从长尾 Task（最后一个 Task 决定总时长）与波次调度分析。
 * ============================================================
 */
