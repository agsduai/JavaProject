/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：59-02
 * 课名：RDD：弹性分布式数据集
 * ------------------------------------------------------------
 * 知识点：
 *   - **RDD 的五个核心属性**：① **分区列表**（数据被切成 N 份）；② **依赖关系**（从哪个父 RDD 转换来）；③ **计算函数**（每个分区的计算逻辑）；④ **分区器**（可选，用于 Shuffle 前的数据分布，59-05）；⑤ **首选位置**（数据本地性，任务调度到数据所在节点）。
 *   - **RDD 的两种操作**：**转换（Transformation）**（map/filter/join/groupBy，惰性，返回新 RDD）与**行动（Action）**（count/collect/saveAsTextFile，触发计算）——**"转换不执行"是理解 Spark 的基石**（59-03）。
 *   - **"弹性"的三层含义**：**分区可重算**（血统容错 59-06）、**内存与磁盘可切换**（内存不够溢写本地盘）、**任务可重试**（失败任务在别的 Executor 重跑）。
 *   - **RDD 的不可变性**：每次转换产生新 RDD，原 RDD 不变——**这让血统可以追溯、任务可以重算**（49-06 事件溯源的同款思想：不可变的历史 -> 可重放的结果）。
 *   - **RDD vs DataFrame**：RDD 是"无结构的对象集合"（强类型但优化器无从下手）；DataFrame 是"带 schema 的行集合"（优化器可重排/裁剪/下推，59-07）。**现代写法优先 DataFrame**，RDD 只用于"需要细粒度控制"或"非结构化数据"。
 *   - 易错点：以为 `rdd.map()` 立刻执行（惰性）；在每个 map 里创建数据库连接（连接数爆炸）；用 `collect()` 拉全量数据到 Driver（OOM 的经典原因）；RDD 里放不可序列化对象（Executor 反序列化失败）。
 * ============================================================
 */

package com.heima.ch59;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

// ==================== 示例代码 ====================

public class RddDemo {
    /** 极简 RDD：分区列表 + 依赖 + 计算函数 */
    static final class MiniRdd<T> {
        final List<List<T>> partitions;
        final String lineage;

        MiniRdd(List<List<T>> partitions, String lineage) {
            this.partitions = partitions;
            this.lineage = lineage;
        }

        static <T> MiniRdd<T> fromPartitions(List<List<T>> data, String label) {
            return new MiniRdd<>(data, label);
        }

        /** 转换：惰性 —— 只记录血统，不计算（本实现为了可视化会提前构造分区结构） */
        <R> MiniRdd<R> map(Function<T, R> fn, String opName) {
            List<List<R>> out = new ArrayList<>();
            for (List<T> p : partitions) {
                List<R> mapped = new ArrayList<>();
                for (T item : p) {
                    mapped.add(fn.apply(item));
                }
                out.add(mapped);
            }
            return new MiniRdd<>(out, lineage + " -> " + opName);
        }

        MiniRdd<T> filter(java.util.function.Predicate<T> pred, String opName) {
            List<List<T>> out = new ArrayList<>();
            for (List<T> p : partitions) {
                List<T> kept = new ArrayList<>();
                for (T item : p) {
                    if (pred.test(item)) {
                        kept.add(item);
                    }
                }
                out.add(kept);
            }
            return new MiniRdd<>(out, lineage + " -> " + opName);
        }

        /** 行动：触发收集（真实 Spark 返回结果到 Driver 或写存储） */
        List<T> collect() {
            List<T> all = new ArrayList<>();
            partitions.forEach(all::addAll);
            return all;
        }

        long count() {
            return partitions.stream().mapToLong(List::size).sum();
        }

        int partitionCount() {
            return partitions.size();
        }
    }

    public static void main(String[] args) {
        var rdd = MiniRdd.fromPartitions(
                List.of(List.of(6900L, 3900L), List.of(12900L, 500L), List.of(20000L)),
                "orders(3 partitions)");
        System.out.println("  分区数：" + rdd.partitionCount() + " 记录数：" + rdd.count());
        System.out.println("  血统：" + rdd.lineage);

        var filtered = rdd.filter(v -> v >= 3900L, "filter(>=3900)");
        var mapped = filtered.map(v -> v / 100, "map(cents->yuan)");
        System.out.println("  变换后血统：" + mapped.lineage);
        System.out.println("  行动 collect：" + mapped.collect() + "（此时才真正物化结果）");
        System.out.println("  各分区：" + mapped.partitions);
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现一个 MiniRdd：支持 map/filter/flatMap/count/collect，并打印血统链，验证"转换只记录、行动才计算"。
 * 2. 用 MiniRdd 实现"按分区求和"与"全局求和"（后者需要跨分区合并），说明数据何时需要移动（预示 59-05 Shuffle）。
 * 3. 思考题：RDD 的不可变性带来了哪些能力？分别对应"血统容错""并发安全""可重放"三个场景说明。
 * ============================================================
 */
