/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：实操 15-01
 * 课名：Stream 流水线统计报表
 * ------------------------------------------------------------
 * 知识点：
 *   - 【题目要求】给定订单列表（订单号/金额/状态/城市），用 Stream 完成四项统计：各状态订单数、各城市 GMV（降序 Top3）、金额高于平均值的所有订单号、用 groupingBy + counting 统计状态分布；输出格式化报表。
 *   - 【考核点】Stream 中间/终端操作、collect 与 Collectors（groupingBy/summingLong/counting）、Comparator 排序、Optional 处理。
 *   - 【易错点】多次遍历同一流（流只能消费一次）；groupingBy 后误用 sum 而非 summingLong；排序比较器方向颠倒。
 *   - 【提示】报表风格输出：先算平均值（terminal），再过滤高于平均——注意平均值要用集合先落袋。
 * ============================================================
 */

package com.heima.practice.core;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// ==================== 示例代码 ====================

public class StreamPipelineTask {
    record Sale(String city, long amount) {
    }

    public static void main(String[] args) {
        List<Sale> sales = List.of(new Sale("杭州", 100), new Sale("杭州", 200),
                new Sale("上海", 300), new Sale("上海", 50), new Sale("北京", 80));
        Map<String, Long> byCity = sales.stream().collect(
                Collectors.groupingBy(Sale::city, Collectors.summingLong(Sale::amount)));
        byCity.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(2)
                .forEach(e -> System.out.printf("  %s GMV=%d%n", e.getKey(), e.getValue()));
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 完整实现四项统计报表，并用 20 条订单验证各统计口径（含边界：空列表、全相同金额）。
 * 2. 用 parallelStream 重跑统计，验证结果一致并测量加速比（记录线程开销的观察）。
 * 3. 思考题：为什么 Stream 只能消费一次？从"管道与终端操作"的语义分析并给出复用数据的正确姿势。
 * ============================================================
 */
