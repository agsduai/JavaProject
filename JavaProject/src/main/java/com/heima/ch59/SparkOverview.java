/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：59-01
 * 课名：离线计算的定位与 Spark 全景
 * ------------------------------------------------------------
 * 知识点：
 *   - **离线计算是"数据已全部落地后的大批量加工"**：T+1 报表、用户画像、特征回填、对账——**与流处理（43 章）的分工是"有界数据 vs 无界数据"**（43-01 的分界再来一次，这次站在批的一侧）。**能跑批的别上流**，43-01 的结论在这里是主角。
 *   - **Spark 的四层结构**：**Spark Core**（RDD、调度、内存管理）-> **Spark SQL**（DataFrame/Dataset + Catalyst 优化器）-> **Structured Streaming**（微批，43-10）；**Spark on YARN/K8s**（资源管理层）。**Core 是地基，SQL 是事实上的主入口**。
 *   - **Spark 相对 MapReduce 的三大改进**：① **内存计算**（中间结果不落盘，MR 每步落 HDFS）；② **DAG 优化**（多步算子合并，MR 只有 map/shuffle/reduce 三段）；③ **惰性求值 + 血统容错**（59-03/59-06）。
 *   - **执行模型的一句话总结**：Driver 解析作业成 DAG -> 切分 Stage -> 分发 Task 到 Executor -> Executor 在内存里迭代计算 -> Shuffle 时落盘 -> 结果回 Driver（或写湖表 46 章）。
 *   - **Spark 不是数据库**：无索引、无事务、无并发更新——**它是"一次大规模扫描加工"的引擎**；交互式点查用 MPP（46-07），事务用 MySQL。
 *   - **学习重点排序**：分区与并行度（59-04）-> Shuffle（59-05）-> 数据倾斜（59-08）-> 资源模型（59-09）——**80% 的 Spark 性能问题在这四件事里**。
 *   - 易错点：用 Spark 做点查（该用 MPP）；小数据量上 Spark（调度开销大于计算）；把 DataFrame 与 RDD 混着用不设断点（优化器失效）；以为"内存计算"意味着不落盘（Shuffle 必落盘）。
 * ============================================================
 */

package com.heima.ch59;

import java.util.List;

// ==================== 示例代码 ====================

public class SparkOverview {
    record Layer(String name, String duty, String keyConcept) {
    }

    static void architecture() {
        System.out.println("【Spark 四层结构】");
        List<Layer> layers = List.of(
                new Layer("Spark Core", "RDD + 调度 + 内存管理", "分区/血统/惰性求值"),
                new Layer("Spark SQL", "DataFrame/Dataset + Catalyst", "逻辑计划 -> 物理计划"),
                new Layer("Structured Streaming", "微批流处理（43-10）", "增量查询"),
                new Layer("资源管理", "YARN / K8s / Standalone", "Executor 申请与释放"));
        for (Layer l : layers) {
            System.out.printf("  %-22s %-26s %s%n", l.name(), l.duty(), l.keyConcept());
        }
        System.out.println();
    }

    static void vsMapReduce() {
        System.out.println("【相对 MapReduce 的三个关键改进】");
        String[][] rows = {
                {"中间结果", "每步落 HDFS", "内存/本地盘，仅在 Shuffle 落盘"},
                {"执行模型", "固定 map-shuffle-reduce 三段", "任意 DAG，多算子合并"},
                {"容错", "重跑整个作业", "血统重算受影响分区（59-06）"},
                {"开发入口", "Java API 为主", "SQL/DataFrame 为主（59-07）"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-8s MR=%-26s Spark=%s%n", r[0], r[1], r[2]);
        }
        System.out.println();
    }

    public static void main(String[] args) {
        architecture();
        vsMapReduce();
        System.out.println("  性能问题 80% 集中在：分区/并行度、Shuffle、倾斜、资源模型");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 画出你的数据加工任务在 Spark 中的执行路径（Driver -> Stage -> Task），标注哪些步骤会落盘。
 * 2. 用一个真实需求判断"该批还是该流"（43-01 决策表），说明延迟、成本、复杂度三方面理由。
 * 3. 思考题：为什么 Spark 说"内存计算"却仍然绕不开 Shuffle 落盘？从数据交换的物理必然性分析。
 * ============================================================
 */
