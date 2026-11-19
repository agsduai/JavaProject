/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：59-03
 * 课名：惰性求值与 DAG 调度
 * ------------------------------------------------------------
 * 知识点：
 *   - **惰性求值的本质：先构图，后优化，再执行**——转换操作只构建"逻辑执行图"，直到遇到行动操作才提交作业。**好处**：优化器整体看得到全图（可合并操作、可重排），避免"边算边存中间结果"。
 *   - **窄依赖可以流水线执行**：`map -> filter -> map` 三个窄依赖算子可以在**同一个 Task 内逐条记录连续处理**（pipeline），无需落盘中间结果——**这是 Spark 快的核心原因之一**。
 *   - **DAG 切分为 Stage 的规则：以 Shuffle 为界**——遇到宽依赖（59-06）就切出新 Stage；**一个 Stage 内全是窄依赖**，可以 pipeline；Stage 之间通过 Shuffle 数据交换落盘。
 *   - **Task 是 Stage 的执行单元**：一个 Stage 的 Task 数 = 其最后一个 RDD 的分区数（59-04）；Task 被分发到数据所在的 Executor（数据本地性）。
 *   - **DAG 的可视化与调优**：`df.explain(true)` 看逻辑/物理计划、Spark UI 看 Stage 与 Task 分布——**"为什么这个作业慢"的第一步永远是看 DAG 里有没有意外的 Shuffle**。
 *   - 易错点：以为转换会执行（惰性）；在循环里反复 `count()`（每次触发全量计算）；不知道 `repartition` 会引入 Shuffle（59-05）；算子顺序不当导致 Filter 没被下推（DataFrame 会自动下推，RDD 不会）。
 * ============================================================
 */

package com.heima.ch59;

import java.util.ArrayList;
import java.util.List;

// ==================== 示例代码 ====================

public class DagDemo {
    /** 算子：标明依赖类型 */
    record Op(String name, boolean shuffles) {
    }

    /** 把算子序列按 Shuffle 边界切成 Stage */
    static List<List<Op>> toStages(List<Op> ops) {
        List<List<Op>> stages = new ArrayList<>();
        List<Op> current = new ArrayList<>();
        for (Op op : ops) {
            current.add(op);
            if (op.shuffles()) {                 // 宽依赖是 Stage 边界
                stages.add(new ArrayList<>(current));
                current.clear();
            }
        }
        if (!current.isEmpty()) {
            stages.add(current);
        }
        return stages;
    }

    static void plan(List<Op> ops, List<Integer> partitionsPerStage) {
        System.out.println("【算子序列】" + ops);
        List<List<Op>> stages = toStages(ops);
        System.out.println("【Stage 切分（以 Shuffle 为界）】");
        for (int i = 0; i < stages.size(); i++) {
            System.out.printf("  Stage %d：%s（Task 数=%d）%n", i, stages.get(i),
                    i < partitionsPerStage.size() ? partitionsPerStage.get(i) : -1);
        }
        long shuffleCount = ops.stream().filter(Op::shuffles).count();
        System.out.printf("  Shuffle 次数：%d（每次都是全量数据重分布 + 落盘）%n", shuffleCount);
        System.out.println();
    }

    public static void main(String[] args) {
        System.out.println("【好的顺序：先过滤再聚合】");
        plan(List.of(
                new Op("load", false),
                new Op("filter(amount>10000)", false),
                new Op("select(cols)", false),
                new Op("groupBy(city).sum(amount)", true),
                new Op("sort(desc)", true)), List.of(8, 200, 1));

        System.out.println("【坏的顺序：先聚合再过滤】");
        plan(List.of(
                new Op("load", false),
                new Op("groupBy(city).sum(amount)", true),
                new Op("filter(city='杭州')", false)), List.of(8, 200));
        System.out.println("  同样结果，Shuffle 数据量差一个数量级 —— 算子顺序决定性能");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现 Stage 切分器：输入算子序列（标注宽/窄依赖），输出 Stage 列表、每个 Stage 的 Task 数，并对比两种算子顺序的 Shuffle 数据量估算。
 * 2. 为你的一个 Spark（或 SQL）作业画出 DAG，标出所有 Shuffle 点，找出"可以移动到 Shuffle 之前"的过滤。
 * 3. 思考题：为什么"先 filter 后 join"通常比"先 join 后 filter"快？从 Shuffle 数据量与中间结果规模推导。
 * ============================================================
 */
