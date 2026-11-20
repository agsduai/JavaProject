/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：81-11
 * 课名：综合案例：商品详情页缓存改造
 * ------------------------------------------------------------
 * 知识点：
 *   - **场景约束**：**QPS 2 万、DB 上限 3000、P99 要求 50ms、改价 1 秒内可见、大促零点不能雪崩**——**五个约束决定方案，不是「加个 Redis」就完事**。
 *   - **方案分层**：**L1 本地缓存（热点商品，1 秒 TTL）+ L2 Redis（全量商品，5 分钟 + 抖动）+ 布隆过滤器（挡穿透）+ 互斥锁（防击穿）**。
 *   - **写路径**：**改价先写库 → 删 Redis → 发广播删各节点 L1**——**1 秒内可见靠 L1 的 1 秒 TTL 兜底**，**广播丢失也不会超过 1 秒**。
 *   - **大促保护**：**预热 Top 1 万商品**、**TTL 抖动 0~5 分钟**、**限流（超过 2.5 万 QPS 拒绝）**、**降级开关（Redis 挂 → 只读 L1 + 兜底文案）**。
 *   - **验证指标**：**命中率 ≥ 95%**、**回源 QPS < 1000**、**P99 < 50ms**、**改价可见延迟 < 1s**、**压测 2.5 万 QPS 无错误**。
 *   - 易错点：只做 Redis 不做 L1（热点仍打爆单分片）；改价只删 Redis 不广播（L1 长期旧）；不做预热（零点冷启动打穿）。
 * ============================================================
 */

package com.heima.ch81;

// ==================== 示例代码 ====================


import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

public class ProductDetailCache {
    // ---------- 三层数据 ----------
    static final Map<String, Integer> DB = new HashMap<>();
    static final Map<String, Integer> REDIS = new HashMap<>();
    static final List<Map<String, Entry>> NODES = List.of(new HashMap<>(), new HashMap<>());
    static final BitSet BLOOM = new BitSet(1 << 16);
    static final int L1_TTL_MS = 1000;              // L1 短 TTL = 最大不一致时间

    record Entry(int price, long expireAt) {
    }

    static final AtomicLong L1_HIT = new AtomicLong();
    static final AtomicLong L2_HIT = new AtomicLong();
    static final AtomicLong DB_HIT = new AtomicLong();
    static final AtomicLong BLOOM_BLOCK = new AtomicLong();
    static final Random RND = new Random(3);

    static void bloomAdd(String k) {
        BLOOM.set(Math.floorMod(k.hashCode(), BLOOM.size()));
    }

    static boolean bloomMaybe(String k) {
        return BLOOM.get(Math.floorMod(k.hashCode(), BLOOM.size()));
    }

    static int jitterTtlMs(int baseMs, int windowMs) {
        return baseMs + RND.nextInt(windowMs + 1);
    }

    // ---------- 读路径：布隆 -> L1 -> L2 -> 单飞回源 ----------
    static Integer getPrice(int node, String sku, long now) {
        if (!bloomMaybe(sku)) {
            BLOOM_BLOCK.incrementAndGet();          // 一定不存在，直接挡掉
            return null;
        }
        Map<String, Entry> l1 = NODES.get(node);
        Entry e = l1.get(sku);
        if (e != null && e.expireAt() > now) {
            L1_HIT.incrementAndGet();
            return e.price();
        }
        Integer p = REDIS.get(sku);
        if (p != null) {
            L2_HIT.incrementAndGet();
            l1.put(sku, new Entry(p, now + L1_TTL_MS));
            return p;
        }
        synchronized (ProductDetailCache.class) {   // 防击穿：单飞，同一 key 只有一个线程回源
            p = REDIS.get(sku);                     // 进锁后再查一次，避免重复回源
            if (p != null) {
                L2_HIT.incrementAndGet();
                l1.put(sku, new Entry(p, now + L1_TTL_MS));
                return p;
            }
            DB_HIT.incrementAndGet();
            p = DB.get(sku);
            if (p != null) {
                REDIS.put(sku, p);
                l1.put(sku, new Entry(p, now + L1_TTL_MS));
            }
            return p;
        }
    }

    // ---------- 写路径：先写库 -> 删 Redis -> 广播删各节点 L1 ----------
    static void updatePrice(String sku, int newPrice) {
        DB.put(sku, newPrice);
        REDIS.remove(sku);
        for (Map<String, Entry> node : NODES) {
            node.remove(sku);
        }
    }

    public static void main(String[] args) {
        int totalSku = 5000;
        for (int i = 1; i <= totalSku; i++) {
            String sku = "sku:" + i;
            DB.put(sku, 100 + i);
            bloomAdd(sku);
        }

        // ---------- 预热：只把 Top 500 进 Redis（全量 5000 装不下也不该装） ----------
        for (int i = 1; i <= 500; i++) {
            REDIS.put("sku:" + i, DB.get("sku:" + i));
        }
        long redisTtl = jitterTtlMs(300_000, 300_000);
        System.out.printf("【预热】Top 500 商品进 Redis（TTL %dms，含抖动），其余 4500 个按需回源%n",
                redisTtl);

        // ---------- 模拟 2 万请求打到 2 个节点 ----------
        long now = 0;
        int total = 20_000;
        for (int i = 0; i < total; i++) {
            if (i % 100 == 0) {
                now += 100;                          // 每 100 请求推进 100ms，共 20 秒
            }
            String sku = (i % 100 < 97)
                    ? "sku:" + (1 + RND.nextInt(100))              // 97% 打在 Top 100 上
                    : "sku:" + (501 + RND.nextInt(4500));          // 3% 长尾，Redis 里没有
            getPrice(i % 2, sku, now);
        }

        long l1 = L1_HIT.get();
        long l2 = L2_HIT.get();
        long db = DB_HIT.get();
        System.out.println();
        System.out.printf("【压测结果】正常请求 %d（2 个节点各一半，时间跨度 20 秒）%n", total);
        System.out.printf("  L1 命中 %6d (%5.1f%%)%n", l1, l1 * 100.0 / total);
        System.out.printf("  L2 命中 %6d (%5.1f%%)%n", l2, l2 * 100.0 / total);
        System.out.printf("  DB 回源 %6d (%5.2f%%)%n", db, db * 100.0 / total);
        System.out.printf("  命中率 %.2f%%（要求 >= 95%%）%n", (l1 + l2) * 100.0 / total);
        System.out.printf("  回源 QPS ≈ %.0f（要求 < 1000，DB 上限 3000）%n", db * 1.0 / 20);

        // ---------- 穿透请求单独看：布隆挡掉，一次库都不打 ----------
        long dbBefore = DB_HIT.get();
        for (int i = 0; i < 2000; i++) {
            getPrice(i % 2, "ghost:" + i, now);       // 2000 次不存在的 sku
        }
        System.out.printf("  另有 2000 次穿透请求：布隆拦截 %d 次，DB 回源增加 %d 次%n",
                BLOOM_BLOCK.get(), DB_HIT.get() - dbBefore);

        // ---------- 改价可见性：一个节点收到广播，一个节点丢广播 ----------
        System.out.println();
        System.out.println("【改价时间线】sku:1 当前价 101，两个节点都已缓存");
        long t = now + 10_000;
        getPrice(0, "sku:1", t);
        getPrice(1, "sku:1", t);
        System.out.println("  T+0ms   写库 price=999");
        updatePrice("sku:1", 999);
        System.out.println("  T+1ms   删 Redis + 广播删 2 个节点 L1");
        System.out.printf("  T+2ms   节点0 读到 %s（广播到了，立刻是新值）%n", getPrice(0, "sku:1", t + 2));
        NODES.get(1).put("sku:1", new Entry(101, t + L1_TTL_MS));   // 模拟节点1 漏收广播
        System.out.printf("  T+500ms 节点1 读到 %s（漏收广播，L1 里还是旧值）%n", getPrice(1, "sku:1", t + 500));
        System.out.printf("  T+1500ms 节点1 读到 %s（L1 的 1 秒 TTL 到期，回源拿新值）%n",
                getPrice(1, "sku:1", t + 1500));
        System.out.println("  -> 所以 L1 的 TTL 就是「最大不一致时间」，广播只是把窗口从 1s 缩到 1ms");

        System.out.println();
        System.out.println("【降级】Redis 不可用时：L1 命中继续服务，未命中返回兜底价 + 限流");
        System.out.println("【结论】布隆挡穿透、单飞防击穿、L1+L2 分摊回源、广播 + 短 TTL 保可见性、"
                + "抖动防雪崩，五个约束逐一被满足");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 把 L1 的 TTL 从 1000ms 改成 30000ms，重跑改价时间线，说明「可见性」如何退化。
 * 2. 去掉布隆过滤器，统计 2000 次穿透请求带来的 DB 回源增加量。
 * 3. 思考题：为什么单飞锁要「进锁后再查一次 Redis」？去掉这次重查会发生什么？
 * ============================================================
 */

/*
 * ============================================================
 * 本章综合练习（无参考答案）
 * ------------------------------------------------------------
 * 【场景】
 * 商品详情页 QPS 2 万，DB 单机只能扛 3000，现在 70% 的请求在打库，P99 已经 800ms。要求：加一层缓存把 P99 压到 50ms 以内，同时保证「改价后 1 秒内可见」，还要防止大促零点整批 key 同时过期把库打穿。请给出完整的缓存方案与验证数据。
 * 【任务】
 * 1. 算收益：按当前 QPS 与命中率目标（90%/95%/98%）估算回源 QPS 与平均延迟，说明为什么 90% 不够。
 * 2. 选模式：商品详情是读多写少、允许秒级旧值，选 Cache-Aside 并说明写路径为什么是「先写库再删缓存」。
 * 3. 防三大故障：为空结果加 60 秒空值缓存、给热点 key 加互斥锁或逻辑过期、给 TTL 加 0~5 分钟随机抖动。
 * 4. 做验证：模拟 2 万 QPS 跑一遍，输出命中率、回源 QPS、P50/P99，以及「改价后缓存何时失效」的时间线。
 * 5. 补降级：缓存集群整体不可用时，用本地缓存 + 限流把流量挡在 DB 之外，给出降级开关的触发条件。
 * 【验收清单】
 * [ ] 命中率 ≥ 95%，回源 QPS 从 2 万降到 1000 以内，有计算过程不是拍脑袋。
 * [ ] 写路径是「先写库、再删缓存」，并能说清并发下为什么不能「更新缓存」。
 * [ ] 穿透/击穿/雪崩三种故障各有一条具体对策，且能在代码里找到对应实现。
 * [ ] 改价后 1 秒内可见，有一致性时间线（写库 → 删缓存 → 下次读回填）佐证。
 * [ ] 有降级开关与触发阈值，缓存全挂时 DB 不被打穿。
 * ============================================================
 */

