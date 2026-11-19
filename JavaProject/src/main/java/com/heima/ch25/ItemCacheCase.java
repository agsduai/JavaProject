/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：25-11
 * 课名：综合案例：商品详情缓存
 * ------------------------------------------------------------
 * 知识点：
 *   - 完整链路：接口层校验参数 -> 查缓存 -> 命中返回 -> 未命中加互斥锁 -> 双重检查 -> 查库 -> 回写缓存（带随机 TTL）-> 返回。
 *   - 缓存对象设计：`item:{id}:detail` 存 JSON（String 类型），`item:{id}:stock` 单独存库存（Hash 或 String），因为库存变化频率远高于详情。
 *   - 更新策略：改商品 -> 更新库 -> 删 `detail` 缓存（库存走单独的实时读写或消息驱动）。
 *   - 空值保护：查库为 null 时缓存 `"NULL"` 字符串 60 秒，配合参数校验挡住 `id <= 0`。
 *   - 可观测：记录命中率、回源次数、锁等待次数，命中率低于阈值要告警（可能是 key 设计或 TTL 有问题）。
 *   - 代码组织：缓存逻辑抽到 `ItemCacheService`，Controller 只负责参数与响应，Service 负责业务与缓存，Mapper 只碰数据库。
 *   - 易错：缓存与数据库在同一个方法里用同一个事务，事务未提交就回写缓存，其它请求可能读到旧值并缓存下来（应 `TransactionSynchronization` 在提交后删缓存）。
 * ============================================================
 */

package com.heima.ch25;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

// ==================== 示例代码 ====================

public class ItemCacheCase {
    // 模拟数据库
    static final Map<Long, String> ITEM_DB = new LinkedHashMap<>();
    // 模拟 Redis
    static final Map<String, String> REDIS = new ConcurrentHashMap<>();
    static final Map<String, ReentrantLock> LOCKS = new ConcurrentHashMap<>();
    static final AtomicInteger DB_QUERY = new AtomicInteger();
    static final AtomicInteger HIT = new AtomicInteger();
    static final AtomicInteger MISS = new AtomicInteger();

    public static void main(String[] args) throws InterruptedException {
        ITEM_DB.put(1001L, "{\"id\":1001,\"name\":\"机械键盘\",\"price\":399}");
        ITEM_DB.put(1002L, "{\"id\":1002,\"name\":\"无线鼠标\",\"price\":129}");

        System.out.println("=== 正常请求 ===");
        System.out.println("  " + getItem(1001L));
        System.out.println("  " + getItem(1001L));
        System.out.println("  " + getItem(1002L));

        System.out.println();
        System.out.println("=== 非法 id 与不存在的商品 ===");
        System.out.println("  " + getItem(-1L));
        System.out.println("  " + getItem(9999L));
        System.out.println("  " + getItem(9999L) + "（第二次走空值缓存，没查库）");

        System.out.println();
        System.out.println("=== 并发请求热点商品（模拟缓存刚失效）===");
        REDIS.remove("item:1001:detail");
        Thread[] ts = new Thread[30];
        for (int i = 0; i < ts.length; i++) {
            ts[i] = new Thread(() -> getItem(1001L));
        }
        for (Thread t : ts) {
            t.start();
        }
        for (Thread t : ts) {
            t.join();
        }

        System.out.println();
        System.out.println("=== 更新商品 ===");
        ITEM_DB.put(1001L, "{\"id\":1001,\"name\":\"机械键盘 Pro\",\"price\":459}");
        updateItem(1001L);
        System.out.println("  更新后读取：" + getItem(1001L));

        System.out.println();
        System.out.println("=== 运行指标 ===");
        System.out.println("  缓存命中 " + HIT.get() + " 次，未命中 " + MISS.get() + " 次");
        System.out.println("  数据库实际查询 " + DB_QUERY.get() + " 次");
        System.out.println("  当前缓存 key：" + REDIS.keySet());
    }

    static String getItem(Long id) {
        if (id == null || id <= 0) {
            return "参数非法：id 必须为正数";
        }
        String key = "item:" + id + ":detail";
        String cached = REDIS.get(key);
        if (cached != null) {
            HIT.incrementAndGet();
            return "NULL".equals(cached) ? "商品不存在（空值缓存）" : cached;
        }
        MISS.incrementAndGet();

        ReentrantLock lock = LOCKS.computeIfAbsent(key, k -> new ReentrantLock());
        lock.lock();
        try {
            cached = REDIS.get(key);       // 双重检查
            if (cached != null) {
                HIT.incrementAndGet();
                return "NULL".equals(cached) ? "商品不存在（空值缓存）" : cached;
            }
            DB_QUERY.incrementAndGet();
            String item = ITEM_DB.get(id);
            int ttl = 1800 + (int) (id % 300);   // 随机 TTL，防雪崩
            REDIS.put(key, item == null ? "NULL" : item);
            return item == null
                    ? "商品不存在（已回写空值，TTL=60s）"
                    : item + "（TTL=" + ttl + "s）";
        } finally {
            lock.unlock();
        }
    }

    static void updateItem(Long id) {
        // 1. 更新数据库（已在上方直接改 ITEM_DB）
        // 2. 删除缓存，而不是更新缓存
        REDIS.remove("item:" + id + ":detail");
        System.out.println("  已更新数据库并删除缓存 item:" + id + ":detail");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 把本案例改造成"库存单独缓存"：`item:{id}:stock` 用 `DECRBY` 扣减，说明详情缓存与库存缓存为什么要分开。
 * 2. 为什么回写缓存要放在事务提交之后？写出用 `TransactionSynchronizationManager.registerSynchronization` 的代码骨架。
 * 3. 给这个案例加上"命中率统计 + 低于 80% 告警"的逻辑，说明还需要采集哪些指标。
 * ============================================================
 */

/*
 * ============================================================
 * 本章综合练习（无参考答案）
 * ------------------------------------------------------------
 * 【场景】
 * 给商品详情接口加缓存：Cache-Aside 读写、TTL 与随机抖动、穿透与击穿防护、分布式锁防重建，最后验证缓存与数据库的一致性。
 * 【任务】
 * 1. 实现 Cache-Aside：先查缓存，未命中查库回填并设置 TTL（加 0~300 秒随机抖动），更新时先更新库再删缓存。
 * 2. 复现缓存穿透：查一个不存在的 id，用空值缓存或布隆过滤器（手写简版位数组）拦截，对比加防护前后的查库次数。
 * 3. 复现缓存击穿：热点 key 过期瞬间 100 个线程并发查库，用互斥锁（SETNX + 过期时间）只放一个线程重建，其余等待。
 * 4. 说明缓存雪崩的三种应对（TTL 打散、多级缓存、限流降级），并在代码里至少实现 TTL 打散。
 * 【验收清单】
 * [ ] 命中缓存时控制台不打印查库日志，未命中时打印一次并回填。
 * [ ] 穿透防护生效后，查不存在的 id 不再打到数据库。
 * [ ] 击穿场景下并发 100 次只有 1 次查库，其余命中缓存或等待后命中。
 * [ ] 更新数据后缓存被删除，下一次查询拿到新值，不会长期脏读。
 * ============================================================
 */
