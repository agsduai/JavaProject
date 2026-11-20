/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：81-03
 * 课名：穿透、击穿、雪崩
 * ------------------------------------------------------------
 * 知识点：
 *   - **穿透（Penetration）**：**查一个数据库里根本不存在的 key**，缓存永远不命中，**每次都打到库**——**攻击者用随机 id 就能把库打挂**。
 *   - **击穿（Breakdown）**：**某个热点 key 恰好过期的那一瞬间**，**大量并发同时回源**——**一个 key 打穿一层缓存**（81-07 的热点 key 更常见）。
 *   - **雪崩（Avalanche）**：**大量 key 在同一时刻集中过期**，或**缓存集群整体宕机**，**流量全部涌向数据库**——**击穿是单点，雪崩是面**。
 *   - **三类对策**：穿透 → **空值缓存 + 布隆过滤器**（81-04）；击穿 → **互斥锁 / 逻辑过期 + 单飞（singleflight）**；雪崩 → **TTL 加随机抖动 + 多级缓存 + 熔断降级**。
 *   - **逻辑过期 vs 互斥锁**：**互斥锁**让其他线程等待（延迟高但不返回旧值）；**逻辑过期**让一个线程刷新、其他线程先返回旧值（可用性优先）——**按业务容忍度选**。
 *   - 易错点：用 `if (cache == null) rebuild()` 但没加锁（等于没防击穿）；空值缓存不设 TTL（脏数据永驻）；TTL 全设 30 分钟（整点雪崩）。
 * ============================================================
 */

package com.heima.ch81;

// ==================== 示例代码 ====================

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

public class CacheProblems {
    static final Map<String, String> DB = new HashMap<>();
    static final Map<String, String> CACHE = new HashMap<>();
    static final AtomicInteger DB_HITS = new AtomicInteger();
    static final Random RND = new Random(7);

    static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    static String queryDb(String key) {
        DB_HITS.incrementAndGet();
        sleep(20);                                  // 模拟 20ms 慢查询
        return DB.get(key);
    }

    static String get(String key, boolean nullCache) {
        String v = CACHE.get(key);
        if (v != null) {
            return v.equals("NULL") ? null : v;
        }
        v = queryDb(key);
        if (v == null && nullCache) {
            CACHE.put(key, "NULL");                 // 空值也缓存，短 TTL
        } else if (v != null) {
            CACHE.put(key, v);
        }
        return v;
    }

    static void penetration() {
        DB_HITS.set(0);
        CACHE.clear();
        for (int i = 0; i < 200; i++) {
            get("user:" + (1000 + i % 50), false);    // 全是不存在的 id，50 个轮流
        }
        int without = DB_HITS.get();
        DB_HITS.set(0);
        CACHE.clear();
        for (int i = 0; i < 200; i++) {
            get("user:" + (1000 + i % 50), true);
        }
        System.out.printf("【穿透】无空值缓存 打库 %3d 次；有空值缓存 打库 %3d 次%n",
                without, DB_HITS.get());
        System.out.println("  200 次请求 50 个不存在的 id 轮流打（每个 id 4 次）：有空值缓存时每个 id 只回源一次，"
                + "之后都命中「NULL」");
    }

    static void breakdown() throws Exception {
        DB_HITS.set(0);
        CACHE.clear();
        DB.put("hot:sku:1", "iPhone");
        int threads = 50;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        for (int i = 0; i < threads; i++) {
            new Thread(() -> {
                try {
                    start.await();
                    get("hot:sku:1", false);        // 热点 key 刚过期，50 线程同时回源
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            }).start();
        }
        start.countDown();
        done.await();
        System.out.printf("【击穿】50 并发打同一个过期热点 key，打库 %d 次（本应 1 次）%n",
                DB_HITS.get());
    }

    static void avalanche() {
        DB_HITS.set(0);
        CACHE.clear();
        for (int i = 0; i < 200; i++) {
            DB.put("cfg:" + i, "v" + i);
        }
        for (int i = 0; i < 200; i++) {            // 所有 key 同一时刻过期
            CACHE.remove("cfg:" + i);
            get("cfg:" + i, false);
        }
        System.out.printf("【雪崩】200 个 key 同时过期，一次性打库 %d 次%n", DB_HITS.get());
        System.out.println("  对策：TTL = 基准 + random(0, 300s)，把过期时间摊开");
    }

    public static void main(String[] args) throws Exception {
        penetration();
        breakdown();
        avalanche();
        System.out.println();
        System.out.println("【总结】穿透防「不存在」，击穿防「同时回源」，雪崩防「同时过期」。");
        System.out.println("  穿透 -> 空值缓存 + 布隆过滤器；击穿 -> 互斥锁 / 逻辑过期；"
                + "雪崩 -> TTL 抖动 + 降级");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 给 get 方法加互斥锁（synchronized 或 ReentrantLock），重跑击穿实验，观察打库次数变成 1。
 * 2. 把雪崩实验里的 TTL 改成「基准 30 分钟 + 0~5 分钟随机」，统计单位时间内的过期峰值。
 * 3. 思考题：为什么「空值缓存」的 TTL 要短（如 60 秒）？如果设成 24 小时会有什么问题？
 * ============================================================
 */

