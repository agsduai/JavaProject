/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：30-04
 * 课名：垃圾收集器：从 Serial 到 ZGC
 * ------------------------------------------------------------
 * 知识点：
 *   - **Serial / Serial Old**：单线程，STW 全程暂停，简单、内存开销小，适合客户端与容器小堆。
 *   - **ParNew / Parallel Scavenge + Parallel Old**：多线程并行，追求吞吐量（`-XX:+UseParallelGC`），适合批处理。
 *   - **CMS**：并发标记清除，追求低停顿，但有**内存碎片**、**并发模式失败**（Concurrent Mode Failure）导致退化成 Serial Old 的长停顿；JDK 14 已移除。
 *   - **G1**（JDK 9+ 默认）：把堆切成等大 Region，按"回收价值"优先回收（Garbage First），可设**目标停顿时间** `-XX:MaxGCPauseMillis=200`；大对象用 Humongous Region。
 *   - **ZGC**（JDK 15+ 生产可用）：染色指针 + 读屏障，停顿与堆大小几乎无关（亚毫秒级），支持 TB 级堆；JDK 21 已是分代 ZGC。
 *   - **Shenandoah**：与 ZGC 类似目标，低延迟，OpenJDK 提供。
 *   - 选型：容器小堆（< 4GB）用 G1；大堆 + 极低延迟用 ZGC；吞吐优先的离线任务用 Parallel；不要在生产用 Serial（除非单核小内存）。
 *   - 易错：以为"并发收集器完全不 STW"（初始标记、最终标记、转移阶段仍有短暂停顿）；把 CMS 的碎片问题带到 G1 上（G1 是复制算法，但 Humongous 分配仍可能触发 Full GC）。
 * ============================================================
 */

package com.heima.ch30;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class GcCollectors {
    public static void main(String[] args) {
        System.out.println("=== 当前 JVM 实际使用的收集器 ===");
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            System.out.println("  " + gc.getName()
                    + "：次数=" + gc.getCollectionCount()
                    + "，累计耗时=" + gc.getCollectionTime() + "ms");
        }
        System.out.println("  JDK 21 默认：G1（新生代 + 老年代各一个收集器实例）");

        System.out.println();
        System.out.println("=== 收集器对比 ===");
        Map<String, String[]> table = new LinkedHashMap<>();
        table.put("Serial", new String[]{"单线程", "全程 STW", "客户端/小堆", "简单、内存占用小"});
        table.put("Parallel", new String[]{"多线程", "全程 STW", "批处理/吞吐优先", "吞吐最高"});
        table.put("CMS", new String[]{"并发", "低停顿", "已废弃", "碎片 + 并发失败"});
        table.put("G1", new String[]{"Region 分治", "可设目标停顿", "通用/默认", "可预测停顿"});
        table.put("ZGC", new String[]{"染色指针", "亚毫秒级", "大堆/极低延迟", "停顿与堆大小无关"});
        System.out.printf("  %-10s %-14s %-14s %-16s %s%n", "收集器", "工作方式", "停顿", "适用场景", "特点");
        for (Map.Entry<String, String[]> e : table.entrySet()) {
            String[] v = e.getValue();
            System.out.printf("  %-10s %-14s %-14s %-16s %s%n", e.getKey(), v[0], v[1], v[2], v[3]);
        }

        System.out.println();
        System.out.println("=== G1 的 Region 布局（堆 4GB，Region 1MB）===");
        int regions = 4096;
        int eden = (int) (regions * 0.4);
        int survivor = (int) (regions * 0.05);
        int old = (int) (regions * 0.45);
        int humongous = regions - eden - survivor - old;
        System.out.println("  Eden " + eden + " 个 + Survivor " + survivor + " 个 + Old " + old
                + " 个 + Humongous " + humongous + " 个 = " + regions);
        System.out.println("  回收时按 Region 的「垃圾占比 × 大小」排序，优先回收收益最高的");

        System.out.println();
        System.out.println("=== 停顿目标与代价 ===");
        int[] targets = {50, 100, 200, 500};
        for (int t : targets) {
            double youngRatio = t < 100 ? 0.3 : t < 300 ? 0.45 : 0.6;
            System.out.printf("  MaxGCPauseMillis=%3dms -> 新生代占比约 %.0f%%%s%n",
                    t, youngRatio * 100,
                    t < 100 ? "（停顿更短，但 GC 更频繁、吞吐下降）" : "");
        }
        System.out.println("  停顿目标不是越小越好：太小会让每次只回收少量 Region，GC 次数暴增");

        System.out.println();
        System.out.println("=== 实测：一次内存压力下的收集器表现 ===");
        List<byte[]> keep = new ArrayList<>();
        long begin = System.currentTimeMillis();
        for (int i = 0; i < 300; i++) {
            keep.add(new byte[256 * 1024]);
            if (i % 100 == 0) {
                keep.subList(0, keep.size() / 2).clear();
            }
        }
        long cost = System.currentTimeMillis() - begin;
        System.out.println("  分配 + 部分释放 300 x 256KB 耗时 " + cost + "ms，当前持有 " + keep.size() + " 块");
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            System.out.println("  " + gc.getName() + " 次数=" + gc.getCollectionCount()
                    + " 耗时=" + gc.getCollectionTime() + "ms");
        }
        System.out.println("  真实调优要看：GC 次数、单次停顿、Full GC 占比、晋升速率");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 用 `-XX:+UseSerialGC`、`-XX:+UseParallelGC`、`-XX:+UseG1GC` 分别启动示例，对比输出与耗时。
 * 2. 解释 G1 为什么能"可预测停顿"，Region 在其中起什么作用。
 * 3. ZGC 的停顿为什么与堆大小几乎无关？从染色指针与并发转移的角度说明。
 * ============================================================
 */
