/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：77-03
 * 课名：缓冲池与 LRU
 * ------------------------------------------------------------
 * 知识点：
 *   - **缓冲池是数据库性能的第一道防线**：**磁盘页读入内存后按页缓存**，**命中则零磁盘 IO**——**OLTP 场景命中率通常 > 99%**，**命中率下降往往意味着缓存被大查询污染**。
 *   - **淘汰算法**：**朴素 LRU**（最近最少使用）简单但**全表扫描会把热数据挤出去**（缓存污染）；**InnoDB 用改进 LRU**（young/old 分区 + 中点插入），**大扫描只污染 old 区**——**这是"全表扫描拖慢整个库"的机制**。
 *   - **预读（read-ahead）**：**顺序访问时一次多读几页**（线性预读/随机预读），**提升范围扫描吞吐**，但**预读过多会浪费缓冲池**。
 *   - **脏页与刷盘**：**修改先改缓冲池页并写 redo**（77-06），**脏页由后台线程择机刷盘**——**刷盘抖动会造成延迟毛刺**（checkpoint 压力）。
 *   - 与 30 章的关系：30 章讲 JVM 内存与 GC，**缓冲池是"数据库的堆"**——**同样有容量、淘汰、命中率、抖动问题**。
 *   - 易错点：认为"加内存一定快"（命中率已 99.9% 时收益极小）；大报表直连主库（污染缓存）；不监控命中率；把"逻辑读高"误判为慢（逻辑读是内存操作）。
 * ============================================================
 */

package com.heima.ch77;

import java.util.LinkedHashMap;
import java.util.Map;

// ==================== 示例代码 ====================

public class BufferPoolLru {
    static class LruPool extends LinkedHashMap<Long, String> {
        private static final long serialVersionUID = 1L;
        final int capacity;
        long hits;
        long misses;
        long evictions;

        LruPool(int capacity) {
            super(16, 0.75f, true);                 // accessOrder = true
            this.capacity = capacity;
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, String> eldest) {
            boolean evict = size() > capacity;
            if (evict) {
                evictions++;
            }
            return evict;
        }

        String access(long pageId) {
            String v = get(pageId);
            if (v == null) {
                misses++;
                v = "page-" + pageId;
                put(pageId, v);
            } else {
                hits++;
            }
            return v;
        }

        double hitRate() {
            long total = hits + misses;
            return total == 0 ? 0 : (double) hits / total;
        }
    }

    /** 80/20 倾斜访问：80% 请求落在 20% 的热页 */
    static void workload(LruPool pool, int requests, boolean skewed) {
        long seed = 42;
        for (int i = 0; i < requests; i++) {
            seed = (seed * 1103515245 + 12345) & 0x7fffffffL;
            long pageId;
            if (skewed) {
                if (seed % 100 < 80) {
                    pageId = seed % 200;            // 热区：0..199
                } else {
                    pageId = 200 + seed % 800;      // 冷区：200..999
                }
            } else {
                pageId = seed % 1000;               // 均匀
            }
            pool.access(pageId);
        }
    }

    static void report(String label, LruPool pool, int requests) {
        System.out.printf("  %-18s 容量=%-4d 命中率=%5.1f%% 淘汰=%-6d 物理读=%d/%d%n",
                label, pool.capacity, pool.hitRate() * 100, pool.evictions,
                pool.misses, requests);
    }

    public static void main(String[] args) {
        int requests = 100_000;

        System.out.println("【均匀访问（无热点）】");
        for (int cap : new int[]{50, 100, 200, 500}) {
            LruPool pool = new LruPool(cap);
            workload(pool, requests, false);
            report("uniform", pool, requests);
        }

        System.out.println();
        System.out.println("【80/20 倾斜访问（有热点）】");
        for (int cap : new int[]{50, 100, 200, 500}) {
            LruPool pool = new LruPool(cap);
            workload(pool, requests, true);
            report("skewed", pool, requests);
        }

        System.out.println();
        System.out.println("【缓存污染：一次全表扫描】");
        LruPool pool = new LruPool(100);
        workload(pool, 10_000, true);
        double before = pool.hitRate();
        for (long p = 1000; p < 2000; p++) {
            pool.access(p);                          // 扫描 1000 个冷页
        }
        workload(pool, 10_000, true);
        System.out.printf("  扫描前命中率=%.1f%%，扫描后=%.1f%%（热页被挤出）%n",
                before * 100, pool.hitRate() * 100);
        System.out.println("  InnoDB 的 young/old 分区就是为缓解这个问题");

        System.out.println();
        System.out.println("【结论】热点越集中，命中率对容量越不敏感；全表扫描会污染缓存");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 用 80/20 与均匀两种负载，测不同容量下的命中率，画出"容量-命中率"曲线并找拐点。
 * 2. 模拟"全表扫描前后"的命中率变化，说明缓存污染，并设计"大查询走从库"的隔离方案。
 * 3. 思考题：为什么 InnoDB 的 LRU 要把新页插到"中点"而不是头部？从"防止一次性扫描挤掉热数据"分析。
 * ============================================================
 */
