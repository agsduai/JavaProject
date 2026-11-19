/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：80-05
 * 课名：CPU Profiling 与火焰图
 * ------------------------------------------------------------
 * 知识点：
 *   - **采样式 Profiler 的原理**：**每隔固定时间（如 10ms）抓一次线程栈**，**统计"栈顶/整条栈"出现的次数**，**次数越多 = CPU 时间越多**——**采样开销低（1%~3%），适合生产**。
 *   - **火焰图的读法**：**横轴是样本占比（不是时间顺序）**、**纵轴是调用深度**、**每个框的宽度 = 该函数及其子调用的占比**——**找"最宽的顶层框"和"没有子调用的宽框（self time）"**。
 *   - **区分 CPU 火焰图与 Wall-clock 火焰图**：**CPU 火焰图只统计"正在跑"的栈**（不包含阻塞）；**Wall 火焰图统计"墙上时间"**（包含等待/IO）——**排查"慢"要看 Wall，排查"CPU 高"要看 CPU**。
 *   - **常见热点**：**序列化/反序列化、正则、字符串拼接、锁竞争、GC（在火焰图外）**——**先看宽框，再看调用链**。
 *   - 与 30/42 章的关系：30 章讲 JVM 指标，42 章讲可观测性，**本章讲"怎么从栈采样定位到具体代码行"**。
 *   - 易错点：只看 CPU 火焰图排查 IO 慢（看不到阻塞）；采样率太低（长尾丢失）；不看 self time（误判"父函数慢"）；在生产全量采样（开销）。
 * ============================================================
 */

package com.heima.ch80;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class FlameGraph {
    /** 一次采样：从根到叶的栈 */
    record Sample(List<String> stack) {
    }

    static final List<Sample> samples = new ArrayList<>();

    static void sample(String... frames) {
        samples.add(new Sample(List.of(frames)));
    }

    /** 按"函数 -> 出现样本数（含子调用）"聚合 */
    static Map<String, Integer> totalCounts() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Sample s : samples) {
            for (String f : s.stack()) {
                counts.merge(f, 1, Integer::sum);
            }
        }
        return counts;
    }

    /** self time：栈顶函数才计 self */
    static Map<String, Integer> selfCounts() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Sample s : samples) {
            String top = s.stack().get(s.stack().size() - 1);
            counts.merge(top, 1, Integer::sum);
        }
        return counts;
    }

    /** 文本火焰图：缩进表示深度，数字是包含子调用的样本数 */
    static void printFlame() {
        Map<String, Map<String, Integer>> tree = new LinkedHashMap<>();
        for (Sample s : samples) {
            Map<String, Integer> level = tree.computeIfAbsent("root",
                    k -> new LinkedHashMap<>());
            for (String f : s.stack()) {
                level.merge(f, 1, Integer::sum);
                level = tree.computeIfAbsent(f, k -> new LinkedHashMap<>());
            }
        }
        Map<String, Integer> counts = totalCounts();
        System.out.println("【火焰图（缩进=深度，数字=样本数）】");
        printNode(tree, "root", 0, samples.size(), counts);
    }

    static void printNode(Map<String, Map<String, Integer>> tree, String node,
                          int depth, int total, Map<String, Integer> counts) {
        if (!node.equals("root")) {
            int count = counts.getOrDefault(node, 0);
            double pct = total == 0 ? 0 : count * 100.0 / total;
            System.out.printf("  %s%-22s %3d (%4.1f%%)%n", "  ".repeat(depth), node, count, pct);
        }
        for (String child : tree.getOrDefault(node, Map.of()).keySet()) {
            printNode(tree, child, depth + 1, total, counts);
        }
    }

    public static void main(String[] args) {
        // 构造采样：60% 时间在 JSON 序列化，25% 在正则，15% 在 DB 等待（wall）
        for (int i = 0; i < 60; i++) {
            sample("main", "handleRequest", "serialize", "writeJson");
        }
        for (int i = 0; i < 25; i++) {
            sample("main", "handleRequest", "validate", "matchRegex");
        }
        for (int i = 0; i < 15; i++) {
            sample("main", "handleRequest", "queryDb", "awaitIo");
        }

        System.out.println("【样本总数】" + samples.size());

        System.out.println();
        System.out.println("【总占比（含子调用）】");
        totalCounts().forEach((f, c) -> System.out.printf("  %-18s %3d (%4.1f%%)%n",
                f, c, c * 100.0 / samples.size()));

        System.out.println();
        System.out.println("【Self 占比（栈顶）】");
        selfCounts().forEach((f, c) -> System.out.printf("  %-18s %3d (%4.1f%%)%n",
                f, c, c * 100.0 / samples.size()));

        System.out.println();
        printFlame();

        System.out.println();
        System.out.println("【读图】");
        System.out.println("  writeJson 占比 60%：最宽的叶子 -> CPU 热点");
        System.out.println("  awaitIo 占比 15%：CPU 火焰图看不到等待，要看 Wall-clock");
        System.out.println("  handleRequest 很宽但 self 很小 -> 它只是调用者，不是热点");

        System.out.println();
        System.out.println("【优化顺序】先优化 writeJson（60%），再看向量化的正则（25%）");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 构造 100 个采样栈，聚合总占比与 self 占比，找出真正的 CPU 热点。
 * 2. 用文本火焰图打印调用树，验证"宽叶子 = 热点、宽父节点 = 调用者"。
 * 3. 思考题：为什么"CPU 火焰图看不到 IO 等待"？从"采样只抓运行中线程"分析，并说明 Wall-clock 火焰图的用途。
 * ============================================================
 */
