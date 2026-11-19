/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：59-06
 * 课名：宽窄依赖与容错
 * ------------------------------------------------------------
 * 知识点：
 *   - **窄依赖（Narrow Dependency）**：一个父分区只被一个子分区使用（`map`/`filter`/`union`）——**分区失败只需重算对应的父分区**，成本低，且支持 pipeline 执行（59-03）。
 *   - **宽依赖（Wide Dependency）**：一个子分区依赖多个父分区（`groupByKey`/`join`/`repartition`）——**分区失败要重算全部父分区**，成本高，且必然产生 Shuffle 边界。
 *   - **血统（Lineage）容错机制**：RDD 记录"从哪来、怎么算"，**Executor 失败时按血统重算丢失的分区**（而非重跑整个作业）——这是"弹性"的本义。**血统链越长，重算代价越大**。
 *   - **检查点（Checkpoint）截断血统**：迭代算法（机器学习/图计算）血统会拉长到几百步——**定期 checkpoint 落盘，切断血统**（43-05 checkpoint 的离线版）；DataFrame 的 `checkpoint()` 同理。
 *   - **推测执行（Speculative Execution）**：慢 Task（`straggler`）在其他节点**并行跑一份**，谁先完成用谁——**对付长尾**（硬件降级/GC 抖动导致），代价是资源浪费（59-09 细讲）。
 *   - **容错与幂等**：重算/重跑要求**输出幂等**（按分区覆盖写、按主键 upsert）——**否则重跑产生重复数据**（39/43 章幂等原则的离线版）。
 *   - 易错点：血统过长（重算成本爆炸，应 checkpoint）；用 `cache()` 代替 checkpoint（缓存丢了血统还在，仍要重算）；输出非幂等（重跑数据翻倍）；把宽依赖当窄依赖理解（以为是分区级重算）。
 * ============================================================
 */

package com.heima.ch59;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class LineageDemo {
    record Partition(int id, List<String> data) {
    }

    /** 窄依赖：一对一映射，失败只重算对应分区 */
    static Partition narrow(Partition parent, java.util.function.UnaryOperator<String> fn) {
        return new Partition(parent.id(), parent.data().stream().map(fn).toList());
    }

    /** 宽依赖：需要全部父分区，失败要重算所有 */
    static List<Partition> wide(List<Partition> parents, int outputPartitions) {
        Map<Integer, List<String>> buckets = new LinkedHashMap<>();
        for (int i = 0; i < outputPartitions; i++) {
            buckets.put(i, new ArrayList<>());
        }
        for (Partition p : parents) {
            for (String item : p.data()) {
                int bucket = Math.abs(item.hashCode()) % outputPartitions;
                buckets.get(bucket).add(item);
            }
        }
        List<Partition> out = new ArrayList<>();
        buckets.forEach((id, data) -> out.add(new Partition(id, data)));
        return out;
    }

    static void failureCost() {
        List<Partition> input = List.of(
                new Partition(0, List.of("a1", "a2")),
                new Partition(1, List.of("b1", "b2")),
                new Partition(2, List.of("c1", "c2")));
        System.out.println("【窄依赖失败代价】");
        List<Partition> narrowed = new ArrayList<>();
        for (Partition p : input) {
            narrowed.add(narrow(p, s -> s.toUpperCase()));
        }
        System.out.println("  分区 1 失败 -> 只需重算父分区 1（" + input.get(1).data() + "）");
        System.out.println("  重算成本：1 个分区");

        System.out.println("【宽依赖失败代价】");
        List<Partition> widened = wide(narrowed, 2);
        System.out.println("  输出分区：" + widened);
        System.out.println("  分区 0 失败 -> 需要全部 3 个父分区重算并重新 Shuffle");
        System.out.println("  重算成本：3 个分区 + 全量 Shuffle");
        System.out.println();
    }

    /** 血统链与 checkpoint 截断 */
    static void checkpointTruncate() {
        StringBuilder lineage = new StringBuilder("load");
        for (int i = 1; i <= 100; i++) {
            lineage.append(" -> op").append(i);
        }
        System.out.println("【血统长度】100 步迭代后血统长度 = 100（重算要跑 100 步）");
        System.out.println("  checkpoint 每 20 步一次 -> 失败时最多重算 20 步");
        System.out.println("  迭代算法（ML/图计算）必须 checkpoint，否则单分区失败 = 重跑全部");
    }

    public static void main(String[] args) {
        failureCost();
        checkpointTruncate();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现窄/宽依赖的失败重算模拟：随机让一个分区失败，统计两种依赖下的"重算分区数"与"数据移动量"。
 * 2. 为一段 200 步的迭代算法设计 checkpoint 策略（间隔与存储位置），量化"最坏重算步数"的改善。
 * 3. 思考题：为什么"输出必须幂等"是容错的前提？构造一个重跑后数据翻倍的场景并给出修复（按分区覆盖写）。
 * ============================================================
 */
