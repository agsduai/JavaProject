/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：80-06
 * 课名：内存分析与 GC 日志
 * ------------------------------------------------------------
 * 知识点：
 *   - **GC 日志是"免费的体检报告"**：**每次 GC 的类型、耗时、回收前后堆大小、晋升老年代大小**——**只看"总暂停时间"不够，要看 P99 暂停与 Full GC 频率**。
 *   - **三个关键指标**：**GC 吞吐**（`1 - 总暂停/总运行`，目标 > 95%）、**最大暂停**（P99 是否满足 SLO）、**晋升速率**（老年代增长快 -> 可能泄漏或分配过大）。
 *   - **分配热点**：**短命对象多**（Young GC 频繁但快）、**大对象/长寿命对象多**（晋升快、Full GC 风险）——**用分配采样（Allocation Profiling）定位"谁在疯狂 new"**。
 *   - **泄漏的迹象**：**Full GC 后老年代仍持续上涨**、**GC 越来越频繁但回收量很小**——**先 dump 堆再分析引用链**（30 章的 MAT 思路）。
 *   - 与 30 章的关系：30 章讲"JVM 内存结构与 GC 算法"，**本章讲"从 GC 日志与分配采样读出问题"**。
 *   - 易错点：只看平均暂停（忽略 P99）；把 Young GC 频繁当故障（可能正常）；不记录 GC 日志（事后无据）；堆越大越好（暂停更长）。
 * ============================================================
 */

package com.heima.ch80;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// ==================== 示例代码 ====================

public class GcAnalysis {
    /** 解析一条简化 GC 日志：类型 + 暂停 ms + 回收后堆 MB */
    record GcEvent(String type, double pauseMs, long heapAfterMb) {
    }

    static final Pattern PATTERN = Pattern.compile(
            "\\[GC (\\w[\\w ]*?) ([\\d.]+)ms\\] heapAfter=(\\d+)MB");

    static List<GcEvent> parse(List<String> lines) {
        List<GcEvent> events = new ArrayList<>();
        for (String line : lines) {
            Matcher m = PATTERN.matcher(line);
            if (m.find()) {
                events.add(new GcEvent(m.group(1).trim(),
                        Double.parseDouble(m.group(2)),
                        Long.parseLong(m.group(3))));
            }
        }
        return events;
    }

    static void analyze(List<GcEvent> events, double totalRunSeconds) {
        double totalPause = 0;
        double maxPause = 0;
        List<Double> pauses = new ArrayList<>();
        long fullGc = 0;
        long youngGc = 0;
        for (GcEvent e : events) {
            totalPause += e.pauseMs();
            maxPause = Math.max(maxPause, e.pauseMs());
            pauses.add(e.pauseMs());
            if (e.type().toLowerCase().contains("full")) {
                fullGc++;
            } else {
                youngGc++;
            }
        }
        pauses.sort(null);
        double p99 = pauses.isEmpty() ? 0
                : pauses.get((int) Math.ceil(pauses.size() * 0.99) - 1);

        System.out.println("【GC 概览】");
        System.out.printf("  Young GC=%d 次，Full GC=%d 次，总暂停=%.0fms%n",
                youngGc, fullGc, totalPause);
        System.out.printf("  最大暂停=%.0fms，P99 暂停=%.0fms%n", maxPause, p99);
        System.out.printf("  GC 吞吐=%.2f%%（目标 >95%%）%n",
                (1 - totalPause / (totalRunSeconds * 1000)) * 100);

        System.out.println();
        System.out.println("【堆变化】");
        long prev = -1;
        for (GcEvent e : events) {
            String trend = prev < 0 ? "-" : (e.heapAfterMb() > prev ? "↑" : "↓");
            System.out.printf("  %-12s %6.1fms 回收后=%3dMB %s%n",
                    e.type(), e.pauseMs(), e.heapAfterMb(), trend);
            prev = e.heapAfterMb();
        }
    }

    static void allocationHotspots() {
        System.out.println();
        System.out.println("【分配热点（采样）】");
        Map<String, Long> alloc = new LinkedHashMap<>();
        alloc.put("StringBuilder.toString", 420L);
        alloc.put("HashMap.Node", 310L);
        alloc.put("byte[]", 260L);
        alloc.put("BigDecimal", 90L);
        alloc.put("LogEvent", 60L);
        long total = alloc.values().stream().mapToLong(Long::longValue).sum();
        alloc.forEach((k, v) -> System.out.printf("  %-24s %4dMB (%4.1f%%)%n",
                k, v, v * 100.0 / total));
        System.out.println("  前两项占 66% -> 优先优化字符串拼接与临时 Map");
    }

    public static void main(String[] args) {
        List<String> lines = List.of(
                "[GC Young 12.0ms] heapAfter=120MB",
                "[GC Young 15.0ms] heapAfter=130MB",
                "[GC Young 11.0ms] heapAfter=125MB",
                "[GC Young 18.0ms] heapAfter=140MB",
                "[GC Young 14.0ms] heapAfter=135MB",
                "[GC Full 820.0ms] heapAfter=310MB",
                "[GC Young 20.0ms] heapAfter=320MB",
                "[GC Young 22.0ms] heapAfter=330MB",
                "[GC Full 910.0ms] heapAfter=335MB",
                "[GC Young 25.0ms] heapAfter=340MB");

        List<GcEvent> events = parse(lines);
        analyze(events, 300);
        allocationHotspots();

        System.out.println();
        System.out.println("【诊断】");
        System.out.println("  Full GC 2 次、每次 800ms+，且回收后堆仍 310MB+ -> 疑似泄漏");
        System.out.println("  Young GC 频率正常、暂停 10~25ms -> 不是主要问题");
        System.out.println("  下一步：dump 堆 -> 找增长最快的对象 -> 查引用链");

        System.out.println();
        System.out.println("【GC 调优顺序】先减少分配（代码）> 再调堆/收集器 > 最后才考虑换硬件");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 解析 10 条 GC 日志，计算总暂停、P99 暂停、GC 吞吐与 Full GC 次数。
 * 2. 识别"Full GC 后老年代仍上涨"的模式，给出 dump/引用链分析步骤。
 * 3. 思考题：为什么"Young GC 频繁"不一定是问题？从"短命对象 + 复制算法"成本分析。
 * ============================================================
 */
