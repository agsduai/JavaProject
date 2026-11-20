# -*- coding: utf-8 -*-
LESSONS = [
    dict(
        cls="CacheCostBenefit",
        title="缓存的收益与代价",
        points=[
            "**缓存的本质**：**用内存换时间**，把「每次都算 / 每次都查库」变成「一次算好、多次读取」——**收益 ≈ 命中率 × 单次回源耗时**，命中率 90% 时平均延迟掉到原来的 1/10 量级。",
            "**三种代价**：**一致性代价**（数据可能旧）、**内存代价**（缓存也是钱）、**复杂度代价**（失效、雪崩、热点都要额外处理）——**缓存不是免费加速，是把「慢」换成「可能不一致」**。",
            "**什么时候不该加缓存**：**数据量小且查询本来就快**、**强一致且不能容忍旧值**（如余额扣减）、**写多读少**——**读远多于写（10:1 以上）才是缓存的主场**。",
            "**命中率是第一指标**：**命中率 = 命中次数 / 总请求**，**低于 80% 基本白干**，还白担一致性风险（81-10 讲怎么量）。",
            "与 25/38 章的关系：25 章讲 Redis 用法、38 章讲 ES 检索，**本章讲「怎么设计缓存层」**——**模式、故障、一致性、监控**四件套。",
            "易错点：只算「快了」不算「旧了」；不看命中率；把缓存当数据库用（缓存丢了就全崩）；TTL 全设同一个值（雪崩）。",
        ],
        code=r'''import java.util.LinkedHashMap;
import java.util.Map;

public class CacheCostBenefit {
    record Scenario(String name, double hitRate, double dbMs, double cacheMs,
                    double qps, double gb, double costPerGbMonth) {
    }

    static void compare(Scenario s) {
        double avgNoCache = s.dbMs();
        double avgWithCache = s.hitRate() * s.cacheMs() + (1 - s.hitRate()) * s.dbMs();
        double dbQpsNoCache = s.qps();
        double dbQpsWithCache = s.qps() * (1 - s.hitRate());
        System.out.printf("【%s】命中率 %.0f%%%n", s.name(), s.hitRate() * 100);
        System.out.printf("  平均延迟：%6.1fms -> %6.1fms（快 %.1f 倍）%n",
                avgNoCache, avgWithCache, avgNoCache / avgWithCache);
        System.out.printf("  回源 QPS：%8.0f -> %8.0f（降 %.0f%%）%n",
                dbQpsNoCache, dbQpsWithCache,
                (1 - dbQpsWithCache / dbQpsNoCache) * 100);
        System.out.printf("  内存成本：%.0f GB -> %.0f 元/月%n",
                s.gb(), s.gb() * s.costPerGbMonth());
        System.out.println();
    }

    public static void main(String[] args) {
        compare(new Scenario("商品详情（高命中）", 0.95, 25, 0.6, 20_000, 40, 60));
        compare(new Scenario("订单列表（低命中）", 0.55, 25, 0.6, 8_000, 60, 60));
        compare(new Scenario("余额查询（不该缓存）", 0.30, 8, 0.5, 3_000, 20, 60));

        System.out.println("【判断】");
        Map<String, String> rules = new LinkedHashMap<>();
        rules.put("命中率 < 80%", "先别加缓存：回源太多，收益覆盖不了复杂度");
        rules.put("强一致 + 金额", "不加缓存或只加极短 TTL，用数据库/行锁保证正确");
        rules.put("读:写 > 10:1", "缓存主场，值得投入");
        rules.put("缓存挂了会全崩", "先做降级与限流，再加缓存");
        rules.forEach((k, v) -> System.out.printf("  %-16s %s%n", k, v));

        System.out.println();
        System.out.println("【结论】缓存不是「有就快」，而是「算清楚才快」："
                + "命中率、延迟收益、内存成本、一致性风险，四项一起评估。");
    }
}''',
        exercises=[
            "给你负责的一个接口，估算命中率与「回源 QPS 下降比例」，判断值不值得加缓存。",
            "把某个场景的命中率从 0.95 改成 0.5，观察延迟收益如何塌陷，说明为什么「缓存要设计 key 粒度」。",
            "思考题：为什么「余额扣减」这类写操作不适合 Cache-Aside？从「旧值被读到的后果」分析。",
        ],
    ),
    dict(
        cls="CachePatterns",
        title="缓存读写模式：Cache-Aside 与 Write-Behind",
        points=[
            "**Cache-Aside（旁路缓存）**：**读**时先查缓存、未命中查库并回填；**写**时**先写库、再删缓存**——**最常用**，缓存只是「加速层」，挂了也能退化到库。",
            "**Read-Through**：**缓存组件自己负责回源**（调用方只跟缓存打交道）——**代码简单**，但**缓存与业务耦合**，回源逻辑要写进缓存层。",
            "**Write-Through**：**写请求同时写缓存与库（同步）**——**缓存永远新**，但**写延迟翻倍**，且**缓存里可能有大量从没被读过的冷数据**。",
            "**Write-Behind（回写）**：**先写缓存、异步批量刷库**——**写极快**，但**宕机会丢数据**，只适合**可容忍丢失的计数/埋点**类场景。",
            "**选型口诀**：**读多写少 → Cache-Aside；强一致读 → Read-Through + 短 TTL；写多但要快 → Write-Behind（能丢）；写少但要准 → Write-Through**。",
            "易错点：写时「先删缓存再写库」（并发下会把旧值写回缓存）；Cache-Aside 写成「更新缓存」而不是「删缓存」（并发更新顺序乱）；Write-Behind 用在订单金额上。",
        ],
        code=r'''import java.util.HashMap;
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
}''',
        exercises=[
            "把 Write-Behind 的 flush 去掉，模拟宕机，统计丢失的写次数。",
            "给 Cache-Aside 的写路径改成「先删缓存再写库」，用两个线程并发演示旧值被写回缓存。",
            "思考题：为什么 Cache-Aside 的写路径是「删缓存」而不是「更新缓存」？从并发写顺序分析。",
        ],
    ),
    dict(
        cls="CacheProblems",
        title="穿透、击穿、雪崩",
        points=[
            "**穿透（Penetration）**：**查一个数据库里根本不存在的 key**，缓存永远不命中，**每次都打到库**——**攻击者用随机 id 就能把库打挂**。",
            "**击穿（Breakdown）**：**某个热点 key 恰好过期的那一瞬间**，**大量并发同时回源**——**一个 key 打穿一层缓存**（81-07 的热点 key 更常见）。",
            "**雪崩（Avalanche）**：**大量 key 在同一时刻集中过期**，或**缓存集群整体宕机**，**流量全部涌向数据库**——**击穿是单点，雪崩是面**。",
            "**三类对策**：穿透 → **空值缓存 + 布隆过滤器**（81-04）；击穿 → **互斥锁 / 逻辑过期 + 单飞（singleflight）**；雪崩 → **TTL 加随机抖动 + 多级缓存 + 熔断降级**。",
            "**逻辑过期 vs 互斥锁**：**互斥锁**让其他线程等待（延迟高但不返回旧值）；**逻辑过期**让一个线程刷新、其他线程先返回旧值（可用性优先）——**按业务容忍度选**。",
            "易错点：用 `if (cache == null) rebuild()` 但没加锁（等于没防击穿）；空值缓存不设 TTL（脏数据永驻）；TTL 全设 30 分钟（整点雪崩）。",
        ],
        code=r'''import java.util.HashMap;
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
}''',
        exercises=[
            "给 get 方法加互斥锁（synchronized 或 ReentrantLock），重跑击穿实验，观察打库次数变成 1。",
            "把雪崩实验里的 TTL 改成「基准 30 分钟 + 0~5 分钟随机」，统计单位时间内的过期峰值。",
            "思考题：为什么「空值缓存」的 TTL 要短（如 60 秒）？如果设成 24 小时会有什么问题？",
        ],
    ),
]
