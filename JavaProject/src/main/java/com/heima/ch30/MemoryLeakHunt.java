/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：30-07
 * 课名：内存泄漏排查：从现象到 GC Root 引用链
 * ------------------------------------------------------------
 * 知识点：
 *   - **内存泄漏**在 Java 里的准确说法是"对象已经没用了，但仍然被 GC Root 可达"，所以 GC 收不掉。表现：老年代占用持续上涨、Full GC 后回落很少、最终 `OutOfMemoryError: Java heap space`。
 *   - 排查顺序：**确认是不是泄漏**（Full GC 后老年代是否回落）→ **看对象直方图**（`jmap -histo:live` 谁的数量在涨）→ **导出堆快照**（`jmap -dump` / OOM 自动 dump）→ **在 MAT 里看支配树与 GC Root 路径**。
 *   - 常见泄漏源：**静态集合只增不减**（本地缓存无上限）、**监听器/回调注册后没注销**、**ThreadLocal 用完不 remove**（线程池里线程复用，值一直挂着）、**连接/流未关闭**、**内部类持有外部类**、**队列生产快于消费**。
 *   - `ThreadLocal` 泄漏的根因：`ThreadLocalMap` 的 key 是弱引用、value 是强引用，key 被回收后 value 还在，线程池线程不死就一直留着。
 *   - 判断"缓存"和"泄漏"的区别：缓存有**上限 + 淘汰策略 + 过期时间**，泄漏没有。给本地缓存加 `removeEldestEntry` 或换成带 TTL 的实现。
 *   - 易错：只看 `jmap -histo` 里 `byte[]` 很多就认定泄漏（`byte[]` 往往是受害者，要顺着引用链找持有者）；在生产直接 `jmap -dump` 大堆（会 STW 数秒，优先 `-XX:+HeapDumpOnOutOfMemoryError` 让它自己 dump）。
 * ============================================================
 */

package com.heima.ch30;

import java.util.LinkedHashMap;
import java.util.Map;

// ==================== 示例代码 ====================

public class MemoryLeakHunt {
    /** 反例：无上限的本地缓存，只 put 不 remove */
    static final Map<String, byte[]> LEAKY_CACHE = new LinkedHashMap<>();

    /** 正例：LRU 上限 200，超了就淘汰最久未使用的 */
    static final Map<String, byte[]> BOUNDED_CACHE = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, byte[]> eldest) {
            return size() > 200;
        }
    };

    static final int ENTRY_KB = 4;

    public static void main(String[] args) {
        System.out.println("=== 第一步：判断有没有泄漏（看 Full GC 后老年代是否回落）===");
        long base = usedAfterGc();
        System.out.println("  基线已用堆：" + kb(base) + "KB");

        System.out.println();
        System.out.println("=== 反例：无上限缓存，每 500 次采样一次堆占用 ===");
        long[] samples = new long[5];
        for (int i = 0; i < 5; i++) {
            for (int j = 0; j < 500; j++) {
                LEAKY_CACHE.put("user:" + (i * 500 + j), new byte[ENTRY_KB * 1024]);
            }
            samples[i] = usedAfterGc();
            System.out.println("  写入 " + LEAKY_CACHE.size() + " 条后，已用堆 "
                    + kb(samples[i]) + "KB（相对基线 +" + kb(samples[i] - base) + "KB）");
        }
        System.out.println("  趋势：" + trend(samples));
        System.out.println("  典型特征：每轮都涨，GC 后不回落 -> 泄漏");

        System.out.println();
        System.out.println("=== 第二步：谁在持有？模拟 GC Root 引用链 ===");
        System.out.println("  GC Root: 线程 main 的栈帧");
        System.out.println("    -> MemoryLeakHunt.<clinit> / 静态字段 LEAKY_CACHE");
        System.out.println("      -> LinkedHashMap.table (Node[] 数组)");
        System.out.println("        -> Node.key = \"user:2499\"，Node.value = byte[" + (ENTRY_KB * 1024) + "]");
        System.out.println("  结论：对象不是没人用，而是被静态集合死死引用 -> 只能靠代码修");

        System.out.println();
        System.out.println("=== 第三步：修复后对比（有上限 + LRU）===");
        long[] fixed = new long[5];
        long fixedBase = usedAfterGc();
        for (int i = 0; i < 5; i++) {
            for (int j = 0; j < 500; j++) {
                BOUNDED_CACHE.put("user:" + (i * 500 + j), new byte[ENTRY_KB * 1024]);
            }
            fixed[i] = usedAfterGc();
            System.out.println("  写入 " + ((i + 1) * 500) + " 条后，缓存条数=" + BOUNDED_CACHE.size()
                    + "，已用堆 " + kb(fixed[i]) + "KB（相对基线 +" + kb(fixed[i] - fixedBase) + "KB）");
        }
        System.out.println("  趋势：" + trend(fixed));
        System.out.println("  典型特征：堆占用到上限后走平 -> 这是缓存，不是泄漏");

        System.out.println();
        System.out.println("=== 常见泄漏源清单 ===");
        String[][] leaks = {
                {"静态 Map/List 只增不减", "加容量上限 + 淘汰策略，或换 Caffeine/Redis"},
                {"ThreadLocal 不 remove", "finally 里 remove()，线程池场景必做"},
                {"监听器/回调注册不注销", "生命周期结束时反注册，或用弱引用"},
                {"连接/流未关闭", "try-with-resources"},
                {"内部类持有外部类", "改成 static 嵌套类，或只传需要的字段"},
                {"生产快于消费的队列", "有界队列 + 背压 + 监控队列深度"}
        };
        System.out.printf("  %-26s %s%n", "泄漏源", "修法");
        for (String[] l : leaks) {
            System.out.printf("  %-26s %s%n", l[0], l[1]);
        }

        System.out.println();
        System.out.println("=== 生产必备参数 ===");
        System.out.println("  -XX:+HeapDumpOnOutOfMemoryError");
        System.out.println("  -XX:HeapDumpPath=/data/dump/");
        System.out.println("  -Xlog:gc*:file=/data/log/gc.log:time,uptime:filecount=10,filesize=50M");
        System.out.println("  OOM 现场只发生一次，dump 是唯一能事后复盘的东西");
    }

    static String trend(long[] s) {
        long diff = s[s.length - 1] - s[0];
        if (diff > 1024) {
            return "持续上升（+" + kb(diff) + "KB），疑似泄漏";
        }
        if (diff < -1024) {
            return "下降（" + kb(diff) + "KB），GC 生效";
        }
        return "基本持平（" + kb(diff) + "KB），正常";
    }

    static long usedAfterGc() {
        Runtime rt = Runtime.getRuntime();
        System.gc();
        sleep(20);
        return rt.totalMemory() - rt.freeMemory();
    }

    static long kb(long bytes) {
        return bytes / 1024;
    }

    static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 把 `LEAKY_CACHE` 的 value 换成 1MB 数组、循环 200 次，用 `-Xmx128m` 复现 OOM，并加上 dump 参数找到 dump 文件。
 * 2. 写一个 `ThreadLocal<byte[]>` 在线程池里不 `remove` 的例子，说明为什么线程复用会导致泄漏。
 * 3. 说明"缓存"和"泄漏"在监控曲线上有什么区别，各自该看哪几个指标。
 * ============================================================
 */
