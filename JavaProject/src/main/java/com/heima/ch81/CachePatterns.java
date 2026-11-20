/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：81-02
 * 课名：缓存读写模式：Cache-Aside 与 Write-Behind
 * ------------------------------------------------------------
 * 知识点：
 *   - **Cache-Aside（旁路缓存）**：**读**时先查缓存、未命中查库并回填；**写**时**先写库、再删缓存**——**最常用**，缓存只是「加速层」，挂了也能退化到库。
 *   - **Read-Through**：**缓存组件自己负责回源**（调用方只跟缓存打交道）——**代码简单**，但**缓存与业务耦合**，回源逻辑要写进缓存层。
 *   - **Write-Through**：**写请求同时写缓存与库（同步）**——**缓存永远新**，但**写延迟翻倍**，且**缓存里可能有大量从没被读过的冷数据**。
 *   - **Write-Behind（回写）**：**先写缓存、异步批量刷库**——**写极快**，但**宕机会丢数据**，只适合**可容忍丢失的计数/埋点**类场景。
 *   - **选型口诀**：**读多写少 → Cache-Aside；强一致读 → Read-Through + 短 TTL；写多但要快 → Write-Behind（能丢）；写少但要准 → Write-Through**。
 *   - 易错点：写时「先删缓存再写库」（并发下会把旧值写回缓存）；Cache-Aside 写成「更新缓存」而不是「删缓存」（并发更新顺序乱）；Write-Behind 用在订单金额上。
 * ============================================================
 */

package com.heima.ch81;

// ==================== 示例代码 ====================

import java.util.HashMap;
import java.util.Map;

public class CachePatterns {
    enum Pattern { CACHE_ASIDE, READ_THROUGH, WRITE_THROUGH, WRITE_BEHIND }

    static class Store {
        final Map<String, String> db = new HashMap<>();
        final Map<String, String> cache = new HashMap<>();
        int dbReads;
        int dbWrites;
        int cacheWrites;
        int cacheHits;
        int cacheMisses;
        int lostOnCrash;
    }

    static String read(Store s, String k) {
        String v = s.cache.get(k);
        if (v != null) {
            s.cacheHits++;
            return v + "@cache";
        }
        s.cacheMisses++;
        s.dbReads++;
        v = s.db.get(k);
        if (v != null) {
            s.cache.put(k, v);
        }
        return v + "@db";
    }

    static void write(Store s, String k, String v, Pattern p) {
        switch (p) {
            case CACHE_ASIDE, READ_THROUGH -> {
                s.dbWrites++;
                s.db.put(k, v);
                s.cache.remove(k);              // 删缓存，不是更新缓存
                s.cacheWrites++;
            }
            case WRITE_THROUGH -> {
                s.dbWrites++;
                s.db.put(k, v);
                s.cache.put(k, v);              // 同步写两份，缓存不会旧
                s.cacheWrites++;
            }
            case WRITE_BEHIND -> {
                s.cache.put(k, v);              // 只落缓存，库还是旧值
                s.cacheWrites++;
                s.lostOnCrash++;                // 宕机就丢这一笔
            }
        }
    }

    static void flush(Store s) {                // Write-Behind 的异步刷盘
        for (Map.Entry<String, String> e : s.cache.entrySet()) {
            if (!e.getValue().equals(s.db.get(e.getKey()))) {
                s.dbWrites++;
                s.db.put(e.getKey(), e.getValue());
                s.lostOnCrash--;
            }
        }
    }

    public static void main(String[] args) {
        System.out.printf("%-14s %-8s %-8s %-8s %-10s %s%n",
                "模式", "读库", "写库", "写缓存", "写后读源", "宕机丢失");
        for (Pattern p : Pattern.values()) {
            Store s = new Store();
            for (int i = 1; i <= 10; i++) {
                s.db.put("item:" + i, "v0-" + i);
            }
            // 100 次读，10 个 key 各 10 次
            for (int round = 0; round < 10; round++) {
                for (int i = 1; i <= 10; i++) {
                    read(s, "item:" + i);
                }
            }
            // 5 次写
            for (int i = 1; i <= 5; i++) {
                write(s, "item:" + i, "v1-" + i, p);
            }
            String source = read(s, "item:1");      // 写后立刻读，看从哪拿到新值
            String where = source.endsWith("@cache") ? "缓存" : "库";
            int lost = s.lostOnCrash;
            if (p == Pattern.WRITE_BEHIND) {
                flush(s);                            // 不宕机则最终刷盘
            }
            System.out.printf("%-14s %-8d %-8d %-8d %-10s %d%n",
                    p, s.dbReads, s.dbWrites, s.cacheWrites, where, lost);
        }

        System.out.println();
        System.out.println("【读结论】前 100 次读四种模式都是 10 次读库（10 个 key 各回源一次），读性能一样");
        System.out.println("          表里的「读库」= 前 100 次的 10 次 + 写后那次读，所以 Cache-Aside 是 11");
        System.out.println("【写结论】Cache-Aside/Read-Through 写完删缓存 -> 写后第一次读要回源（读库 +1）");
        System.out.println("          Write-Through 写两份 -> 写延迟翻倍，但读永远命中且是新的");
        System.out.println("          Write-Behind 只写缓存 -> 写最快，但刷盘前宕机这 5 笔全丢");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 把 Write-Behind 的 flush 去掉，模拟宕机，统计丢失的写次数。
 * 2. 给 Cache-Aside 的写路径改成「先删缓存再写库」，用两个线程并发演示旧值被写回缓存。
 * 3. 思考题：为什么 Cache-Aside 的写路径是「删缓存」而不是「更新缓存」？从并发写顺序分析。
 * ============================================================
 */

