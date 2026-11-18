/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：25-09
 * 课名：缓存穿透、击穿、雪崩
 * ------------------------------------------------------------
 * 知识点：
 *   - **穿透**：查询一个**数据库里也不存在**的数据（如 id=-1），缓存永远不命中，每次请求都打到数据库。恶意攻击常用这招。
 *   - 解法一：缓存空值，TTL 设短（如 60s），防止大量无效 key 占内存。
 *   - 解法二：布隆过滤器，启动时把存在的 id 放进过滤器，请求先过一遍，不存在直接返回。
 *   - 解法三：参数校验，id <= 0 直接拒绝；接口限流。
 *   - **击穿**：某个**热点 key 突然过期**，瞬间大量并发全部回源，把数据库打崩。
 *   - 解法一：互斥锁（只让一个线程回源，其余等待）。
 *   - 解法二：逻辑过期，value 里存过期时间，不设 Redis TTL；发现逻辑过期后异步重建，其余请求先返回旧值。
 *   - 解法三：热点 key 永不过期 + 后台定时刷新。
 *   - **雪崩**：**大量 key 同时过期**或 **Redis 集群整体宕机**，请求全部压到数据库。
 *   - 解法一：过期时间加随机值（如 30min + random(0~5min)），打散过期时间点。
 *   - 解法二：多级缓存（本地 Caffeine + Redis），Redis 挂了本地还能挡一层。
 *   - 解法三：限流降级、熔断（Sentinel/Resilience4j），保护数据库。
 *   - 解法四：Redis 高可用（主从 + 哨兵/集群），避免单点。
 *   - 三者的区别记法：穿透是"查不存在的数据"，击穿是"单个热点 key 失效"，雪崩是"大批 key 同时失效或缓存整体不可用"。
 * ============================================================
 */

package com.heima.ch25;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

// ==================== 示例代码 ====================

public class CacheProblems {
    static final Map<Long, String> DB = new ConcurrentHashMap<>();
    static final Map<Long, String> CACHE = new ConcurrentHashMap<>();
    static final AtomicInteger DB_HITS = new AtomicInteger();

    public static void main(String[] args) {
        DB.put(1L, "商品A");
        DB.put(2L, "商品B");

        System.out.println("=== 穿透：查不存在的数据 ===");
        DB_HITS.set(0);
        for (int i = 0; i < 100; i++) {
            getWithoutProtection(-1L);
        }
        System.out.println("  100 次请求 id=-1，数据库被访问 " + DB_HITS.get() + " 次");
        CACHE.clear();
        DB_HITS.set(0);
        for (int i = 0; i < 100; i++) {
            getWithNullCache(-1L);
        }
        System.out.println("  缓存空值后，数据库只被访问 " + DB_HITS.get() + " 次（首次回源）");

        System.out.println();
        System.out.println("=== 击穿：热点 key 过期，并发回源 ===");
        CACHE.clear();
        DB_HITS.set(0);
        ReentrantLock lock = new ReentrantLock();
        for (int i = 0; i < 50; i++) {
            getWithMutex(1L, lock);
        }
        System.out.println("  50 个并发请求，加互斥锁后数据库只被访问 " + DB_HITS.get() + " 次");

        System.out.println();
        System.out.println("=== 雪崩：过期时间加随机值 ===");
        Map<Long, Integer> ttlPlan = new LinkedHashMap<>();
        for (long id = 1; id <= 6; id++) {
            int base = 1800;                                  // 30 分钟
            int jitter = (int) (id * 37 % 300);               // 模拟随机 0~299 秒
            ttlPlan.put(id, base + jitter);
        }
        System.out.println("  固定 TTL 1800s -> 6 个 key 在同一秒集体失效");
        System.out.println("  加随机抖动后 -> " + ttlPlan);
        System.out.println("  失效时间点被打散，数据库压力被摊平");

        System.out.println();
        System.out.println("=== 三种问题对照 ===");
        Map<String, String> table = new LinkedHashMap<>();
        table.put("穿透", "数据不存在 | 缓存空值 / 布隆过滤器 / 参数校验");
        table.put("击穿", "单个热点 key 失效 | 互斥锁 / 逻辑过期 / 永不过期");
        table.put("雪崩", "大批 key 失效或 Redis 挂了 | 随机 TTL / 多级缓存 / 限流降级");
        table.forEach((k, v) -> System.out.printf("  %-4s %s%n", k, v));
    }

    /** 无保护：每次穿透到数据库 */
    static String getWithoutProtection(Long id) {
        String v = CACHE.get(id);
        if (v != null) {
            return v;
        }
        DB_HITS.incrementAndGet();
        return DB.get(id);
    }

    /** 缓存空值：null 也缓存，TTL 短 */
    static String getWithNullCache(Long id) {
        if (CACHE.containsKey(id)) {
            String v = CACHE.get(id);
            return "NULL".equals(v) ? null : v;
        }
        DB_HITS.incrementAndGet();
        String v = DB.get(id);
        CACHE.put(id, v == null ? "NULL" : v);
        return v;
    }

    /** 互斥锁：只让一个线程回源 */
    static String getWithMutex(Long id, ReentrantLock lock) {
        String v = CACHE.get(id);
        if (v != null) {
            return v;
        }
        lock.lock();
        try {
            v = CACHE.get(id);           // 双重检查，别的线程可能已经回源完成
            if (v != null) {
                return v;
            }
            DB_HITS.incrementAndGet();
            v = DB.get(id);
            CACHE.put(id, v);
            return v;
        } finally {
            lock.unlock();
        }
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 用一句话区分缓存穿透、击穿、雪崩，并各给出两种解决方案。
 * 2. 布隆过滤器为什么会有误判？"说不存在一定不存在，说存在可能不存在"这句话怎么理解？
 * 3. 设计一个"首页楼层"接口的缓存方案，要求 Redis 整体宕机时页面仍能返回（提示：多级缓存 + 降级）。
 * ============================================================
 */
