/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：59-07
 * 课名：Spark SQL 与 Catalyst 优化器
 * ------------------------------------------------------------
 * 知识点：
 *   - **DataFrame 的主流地位来自 Catalyst 优化器**：SQL/DataFrame 先转成**逻辑计划**（未解析 -> 已解析 -> 优化后），再转**物理计划**（选 join 策略、决定分区、插入 Shuffle），最后生成 RDD 执行——**同一段 SQL 的好坏取决于优化器给了什么计划**。
 *   - **四个关键优化规则**：① **谓词下推（Predicate Pushdown）**——filter 尽量推到数据源（Parquet 的 Row Group 裁剪 46-02 由它触发）；② **列裁剪（Column Pruning）**——只读用到的列；③ **常量折叠/表达式简化**；④ **Join 重排序与策略选择**（Broadcast Hash Join / Sort Merge Join / Shuffle Hash Join）。
 *   - **Join 策略的选择依据**：**小表（<10MB 默认阈值）自动 Broadcast**；大表 join 大表用 **Sort Merge Join**（两侧先按 key 排序再归并，Shuffle 后有序）；**Shuffle Hash Join** 适用于一侧远小于另一侧且分区内能放下哈希表。
 *   - **AQE（Adaptive Query Execution，Spark 3.x 默认开启）**：**运行时**根据 Shuffle 统计信息调整：合并小分区（coalesce partitions）、**动态切换 Broadcast Join**（Shuffle 后才发现某侧很小）、**动态处理倾斜**（59-08）——**这让很多手工调参变得不必要**。
 *   - **`explain` 是唯一可信的证据**：`df.explain(true)` 看四段计划；Spark UI 的 SQL 页看每个算子的实际行数与耗时——**"优化"必须基于计划与实际统计，而不是猜**。
 *   - 易错点：用 UDF 包住列（谓词下推失效，36/39 章"函数包列索引失效"的 Spark 版）；不看 explain 就调参数；小表 join 不广播（默认阈值被改小或表超阈值）；以为 DataFrame 一定比 RDD 快（自定义逻辑复杂时 RDD 反而清晰）。
 * ============================================================
 */

package com.heima.ch59;

import java.util.ArrayList;
import java.util.List;

// ==================== 示例代码 ====================

public class CatalystDemo {
    record PlanStep(String stage, String plan, String note) {
    }

    static void planEvolution() {
        System.out.println("【SQL 到执行的计划演进】");
        List<PlanStep> steps = List.of(
                new PlanStep("未解析逻辑计划", "Project(name) <- Filter(amount>10000) <- Relation(orders)",
                        "表/列未绑定"),
                new PlanStep("已解析逻辑计划", "绑定到 catalog 的 orders 表与列类型", "schema 校验"),
                new PlanStep("优化后逻辑计划", "Filter 下推到 Relation（谓词下推）；只保留 name 列（列裁剪）",
                        "Catalyst 规则应用"),
                new PlanStep("物理计划", "Scan(parquet, rowGroup min/max 裁剪) -> Filter -> BroadcastHashJoin(dim)",
                        "选择 join 策略 + 分区"),
                new PlanStep("执行 RDD", "生成 Task 在 Executor 执行", "真正跑起来"));
        for (PlanStep s : steps) {
            System.out.printf("  %-14s %s%n       %s%n", s.stage(), s.plan(), s.note());
        }
        System.out.println();
    }

    /** Join 策略决策：表大小 + 阈值 */
    static String chooseJoinStrategy(long leftBytes, long rightBytes, long broadcastThreshold) {
        if (Math.min(leftBytes, rightBytes) <= broadcastThreshold) {
            return "BroadcastHashJoin（小表广播，零 Shuffle）";
        }
        return "SortMergeJoin（两侧 Shuffle + 排序 + 归并）";
    }

    /** AQE 的三个运行时优化 */
    static void aqeBenefits() {
        System.out.println("【AQE 运行时优化】");
        String[][] rows = {
                {"合并小分区", "Shuffle 后 200 个 5MB 分区 -> 合并成 10 个 100MB", "减少 Task 调度开销"},
                {"动态 Broadcast", "预估 500MB 的表 Shuffle 后实际仅 8MB -> 改广播", "消除一次大 Shuffle"},
                {"动态倾斜处理", "发现某分区数据量是均值 5 倍 -> 自动拆分", "缓解长尾（59-08）"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-14s %-46s %s%n", r[0], r[1], r[2]);
        }
        System.out.println();
    }

    public static void main(String[] args) {
        planEvolution();
        long threshold = 10L * 1024 * 1024;
        System.out.println("【Join 策略选择】");
        System.out.println("  订单 20GB join 商品维度 8MB  -> "
                + chooseJoinStrategy(20L << 30, 8L << 20, threshold));
        System.out.println("  订单 20GB join 用户表 2GB    -> "
                + chooseJoinStrategy(20L << 30, 2L << 30, threshold));
        System.out.println();
        aqeBenefits();
        System.out.println("  UDF 反模式：filter(udf(amount)) -> 谓词下推失效，全表扫描后过滤");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 写两条等价 SQL（一条用 UDF 包列、一条用原生表达式），用 `explain` 对比计划差异，说明下推失效的代价。
 * 2. 实现 Join 策略选择器（输入两侧大小、阈值、是否已排序），输出推荐策略并说明理由。
 * 3. 思考题：为什么 AQE 不能完全替代人工调优？从"统计信息不全""倾斜的根因在业务"两个角度分析。
 * ============================================================
 */
