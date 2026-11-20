# -*- coding: utf-8 -*-
LESSONS = [
    dict(
        cls="CacheWarmupDegrade",
        title="缓存预热与降级",
        points=[
            "**缓存预热**：**上线/重启/大促前，把确定会被访问的数据主动写进缓存**——**避免「冷启动瞬间全部回源」**（发布后 1 分钟打挂库的经典事故）。",
            "**预热的三种做法**：**启动时批量加载**（适合小数据量）、**定时任务跑热点清单**（适合可预测热点）、**流量回放/影子流量预热**（适合大促前，80-08）。",
            "**降级（Degrade）**：**缓存不可用时，用「本地缓存 / 静态兜底值 / 直接限流」代替正常路径**——**降级的目标不是「功能完好」，而是「不雪崩」**。",
            "**降级的触发条件**：**缓存错误率 > 阈值**、**缓存 RT > 阈值**、**连接池耗尽**、**手动开关（大促值守）**——**要能自动触发，也要能手動一键**。",
            "**降级的分级**：**L1 只读降级**（写路径保留、读走兜底）、**L2 功能降级**（关掉非核心模块）、**L3 限流熔断**（保住核心链路）——**按影响面从小到大**。",
            "易错点：预热把整个库全加载（内存爆、启动慢）；降级后没恢复机制（一直降级）；降级兜底值不设过期（永远返回旧值）。",
        ],
        code=r'''
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CacheWarmupDegrade {
    static final Map<String, String> DB = new HashMap<>();
    static final Map<String, String> REDIS = new HashMap<>();
    static final Map<String, String> LOCAL = new HashMap<>();

    static int dbCalls;
    static boolean redisDown;
    static final int DB_MS = 2;                     // 单次读库 2ms

    static void init() {
        for (int i = 1; i <= 20; i++) {
            DB.put("sku:" + i, "商品" + i);
        }
    }

    static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    static String loadFromDb(String k) {
        dbCalls++;
        sleep(DB_MS);
        return DB.get(k);
    }

    // ---------- 预热：按热点清单批量灌缓存 ----------
    static void warmupByHotList(List<String> hotKeys) {
        long t0 = System.nanoTime();
        int loaded = 0;
        for (String k : hotKeys) {
            String v = loadFromDb(k);
            if (v != null) {
                REDIS.put(k, v);
                loaded++;
            }
        }
        long ms = (System.nanoTime() - t0) / 1_000_000;
        System.out.printf("【预热】按热点清单加载 %d 个 key 到 Redis，耗时 %dms（单次读库 %dms），DB 调用 %d 次%n",
                loaded, ms, DB_MS, dbCalls);
    }

    // ---------- 正常读 + 降级读 ----------
    static String read(String k, boolean degrade) {
        if (!redisDown) {
            String v = REDIS.get(k);
            if (v != null) {
                return v + "(redis)";
            }
            v = loadFromDb(k);
            if (v != null) {
                REDIS.put(k, v);
            }
            return v == null ? null : v + "(db->redis)";
        }
        if (degrade) {
            String v = LOCAL.get(k);
            if (v != null) {
                return v + "(local-degraded)";
            }
            return "默认兜底文案(degraded)";        // 静态兜底，绝不打库
        }
        return loadFromDb(k) + "(db-no-degrade)";   // 未降级：全部打库
    }

    public static void main(String[] args) {
        init();
        List<String> hot = List.of("sku:1", "sku:2", "sku:3", "sku:4", "sku:5");

        dbCalls = 0;
        warmupByHotList(hot);

        System.out.println();
        System.out.println("【冷启动对比】");
        dbCalls = 0;
        for (String k : hot) {
            read(k, false);
        }
        System.out.printf("  预热后读 5 个热点：DB 调用 %d 次%n", dbCalls);

        REDIS.clear();
        dbCalls = 0;
        for (String k : hot) {
            read(k, false);
        }
        System.out.printf("  不预热读 5 个热点：DB 调用 %d 次（冷启动放大）%n", dbCalls);

        System.out.println();
        System.out.println("【放大估算】发布后第 1 秒，2 万 QPS 全部未命中 -> 2 万次读库");
        System.out.println("  连接池 100、单次读库 20ms -> 上限 = 100 / 0.02s = 5000 次/秒");
        System.out.println("  2 万 QPS 是上限的 4 倍 -> 排队、超时、连接池耗尽，第一秒就把库打挂");
        System.out.println("  -> 预热的意义：上线第一秒就有 90% 命中，回源只剩 2000，落在 5000 的上限内");

        System.out.println();
        System.out.println("【降级】缓存集群宕机，本地缓存兜底");
        LOCAL.putAll(REDIS);
        redisDown = true;
        dbCalls = 0;
        for (String k : hot) {
            System.out.println("  " + k + " -> " + read(k, true));
        }
        System.out.printf("  降级后 DB 调用 %d 次（应为 0）%n", dbCalls);

        dbCalls = 0;
        for (String k : hot) {
            read(k, false);
        }
        System.out.printf("  不降级时 DB 调用 %d 次（可能打挂库）%n", dbCalls);

        System.out.println();
        System.out.println("【降级分级】L1 只读降级 -> L2 关非核心 -> L3 限流熔断");
        System.out.println("【要点】预热要有热点清单；降级要能自动触发 + 手动开关 + 自动恢复");
    }
}
''',
        exercises=[
            "把预热改成「全库加载 1 万个 key」，估算启动耗时与内存占用，说明为什么只预热热点。",
            "给降级加一个「错误率 > 5% 自动开启、连续 60 秒正常自动关闭」的状态机。",
            "思考题：为什么降级兜底要返回「默认文案」而不是「抛异常」？从用户体验与雪崩传播分析。",
        ],
    ),
    dict(
        cls="MultiLevelCache",
        title="多级缓存与 TTL 抖动",
        points=[
            "**多级缓存结构**：**本地缓存（L1，纳秒）→ 分布式缓存（L2，百微秒）→ 数据库（L3，毫秒）**——**每层挡住一部分流量，逐级收敛**。",
            "**每层的容量与 TTL 不同**：**L1 小容量短 TTL（秒级）**、**L2 大容量长 TTL（分钟~小时）**——**TTL 越短越新，越长越省**。",
            "**TTL 抖动（jitter）**：**TTL = 基准 + random(0, 抖动窗口)**——**把同一批写入的 key 的过期时间摊开**，避免整点雪崩（81-03）。",
            "**多级缓存的一致性**：**更新时 L2 删除 + 广播让所有节点删 L1**——**广播丢失靠 L1 短 TTL 兜底**，**所以 L1 的 TTL 就是「最大不一致时间」**。",
            "**穿透率与放大**：**L1 未命中 → L2 未命中 → DB**，**如果 L1 命中率 90%、L2 命中率 90%，DB 只承担 1%**——**这就是多级缓存的乘数效应**。",
            "易错点：L1 设 30 分钟 TTL（等于放弃一致性）；只删 L2 不广播（各节点 L1 长期旧）；抖动窗口大于基准 TTL（等于随机过期）。",
        ],
        code=r'''
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class MultiLevelCache {
    static final Map<String, String> DB = new HashMap<>();
    static final Map<String, String> L2 = new HashMap<>();
    static final Random RND = new Random(11);
    static final int L1_CAPACITY = 20;

    // L1：容量只有 20 条的小 LRU（accessOrder=true，最久未用的先淘汰）
    static class LocalCache extends LinkedHashMap<String, String> {
        LocalCache() {
            super(16, 0.75f, true);
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
            return size() > L1_CAPACITY;
        }
    }

    static LocalCache L1 = new LocalCache();
    static int l1Hit;
    static int l2Hit;
    static int dbHit;

    static int jitterTtl(int baseSec, int windowSec) {
        return baseSec + RND.nextInt(windowSec + 1);
    }

    static String get(String k, boolean useL1) {
        if (useL1) {
            String v = L1.get(k);
            if (v != null) {
                l1Hit++;
                return v;
            }
        }
        String v = L2.get(k);
        if (v != null) {
            l2Hit++;
            if (useL1) {
                L1.put(k, v);                   // 回填 L1
            }
            return v;
        }
        dbHit++;
        v = DB.get(k);
        if (v != null) {
            L2.put(k, v);
            if (useL1) {
                L1.put(k, v);
            }
        }
        return v;
    }

    // 同一份请求序列跑两遍：一遍只有 L2，一遍 L1+L2
    static int[] run(List<String> seq, boolean useL1) {
        L1 = new LocalCache();
        L2.clear();
        l1Hit = 0;
        l2Hit = 0;
        dbHit = 0;
        for (String k : seq) {
            get(k, useL1);
        }
        return new int[]{l1Hit, l2Hit, dbHit};
    }

    static void invalidate(String k) {
        L2.remove(k);
        L1.remove(k);                           // 单机演示；真实环境要广播给所有节点
        System.out.println("  失效 " + k + " -> L1/L2 均已删除（其他节点靠广播 + L1 短 TTL）");
    }

    public static void main(String[] args) {
        for (int i = 1; i <= 1000; i++) {
            DB.put("sku:" + i, "商品" + i);
        }

        // 请求序列：80% 集中在 50 个热 key（超出 L1 的 20 条容量），20% 长尾
        List<String> seq = new ArrayList<>();
        Random r = new Random(11);
        for (int i = 0; i < 10_000; i++) {
            seq.add(i % 10 < 8
                    ? "sku:" + (1 + r.nextInt(50))
                    : "sku:" + (1 + r.nextInt(1000)));
        }

        int[] onlyL2 = run(seq, false);
        int[] both = run(seq, true);
        int total = seq.size();

        System.out.println("同一份 10000 次请求，跑两遍：");
        System.out.printf("  只有 L2      ：L2 命中 %5d，DB 回源 %5d (%.1f%%)%n",
                onlyL2[1], onlyL2[2], onlyL2[2] * 100.0 / total);
        System.out.printf("  L1(%d 条)+L2 ：L1 命中 %5d (%.1f%%)，L2 命中 %5d，DB 回源 %5d (%.1f%%)%n",
                L1_CAPACITY, both[0], both[0] * 100.0 / total, both[1], both[2],
                both[2] * 100.0 / total);
        System.out.printf("  乘数效应：L1 挡掉 %.1f%%，落到 L2 的请求从 %d 降到 %d（降 %.0f%%）%n",
                both[0] * 100.0 / total, onlyL2[1] + onlyL2[2], both[1] + both[2],
                (1 - (both[1] + both[2]) * 1.0 / (onlyL2[1] + onlyL2[2])) * 100);
        System.out.printf("  注意：DB 回源没变（热 key 本来在 L2 就命中），L1 省的是 Redis 的 QPS 与网络往返%n");

        System.out.println();
        System.out.println("【TTL 抖动】同一批写入的 10 个 key，过期时间分布：");
        StringBuilder sb = new StringBuilder("  ");
        for (int i = 0; i < 10; i++) {
            sb.append(jitterTtl(1800, 300)).append("s ");
        }
        System.out.println(sb.toString().trim());
        System.out.println("  基准 1800s + 0~300s 随机，避免 1800s 整点集中过期");

        System.out.println();
        System.out.println("【更新】价格变更");
        DB.put("sku:1", "商品1-新价");
        invalidate("sku:1");
        System.out.println("  下次读 sku:1 -> " + get("sku:1", true) + "（回源并回填两层）");

        System.out.println();
        System.out.println("【要点】L1 短 TTL 定「最大不一致时间」；L2 长 TTL 省内存；TTL 一律加抖动");
    }
}
''',
        exercises=[
            "把 L1_CAPACITY 从 20 调到 60（装得下 50 个热 key），重跑，观察 L1 命中率与「落到 L2 的请求数」如何变化。",
            "给 invalidate 加「广播给 3 个节点」的模拟，演示只删本地不广播时其他节点的旧值。",
            "思考题：为什么 TTL 抖动窗口不能大于基准 TTL？举一个数值例子说明会退化成「随机过期」。",
        ],
    ),
    dict(
        cls="CacheMetrics",
        title="缓存监控与命中率治理",
        points=[
            "**四个必看指标**：**命中率**（hits/(hits+misses)）、**回源 QPS**、**缓存 RT（P99）**、**内存使用率与大 key 数量**——**命中率掉了就是事故前兆**。",
            "**命中率要分层看**：**整体命中率**会掩盖问题，**按 key 前缀/业务线分组**才能定位「哪类数据在打库」（如 `sku:*` 96%、`order:*` 38%）。",
            "**命中率低的四个原因**：**TTL 太短**（频繁过期）、**key 设计太细**（同一对象拆成多个 key，回填不完整）、**缓存容量不足**（LRU 把热 key 挤掉）、**写入路径误删**（写一次删一片）。",
            "**大 key 与热 key**：**大 key**（单个 value 几 MB）会拖慢整个实例、**热 key**（单 key QPS 过高）会打爆单分片——**都要靠采样统计提前发现**。",
            "**监控落地**：**应用侧埋点（命中/未命中/回源耗时）+ 缓存侧指标（内存/连接/慢查询）+ 告警（命中率跌破阈值、回源 QPS 突增）**。",
            "易错点：只监控缓存 RT 不看命中率（RT 好但一直回源）；命中率按总量算不分业务（掩盖局部问题）；不做容量水位告警（OOM 才发现）。",
        ],
        code=r'''import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

public class CacheMetrics {
    static class Counter {
        long hits;
        long misses;
        long totalNanos;

        void hit(long nanos) {
            hits++;
            totalNanos += nanos;
        }

        void miss(long nanos) {
            misses++;
            totalNanos += nanos;
        }

        long total() {
            return hits + misses;
        }

        double hitRate() {
            return total() == 0 ? 0 : (double) hits / total();
        }

        double avgMs() {
            return total() == 0 ? 0 : totalNanos / 1_000_000.0 / total();
        }
    }

    static void report(String name, Counter c, double warnThreshold) {
        String verdict = c.hitRate() < warnThreshold ? "告警：命中率低于 " + (int) (warnThreshold * 100) + "%"
                : "正常";
        System.out.printf("  %-12s 请求=%6d 命中率=%5.1f%% 平均=%.2fms  %s%n",
                name, c.total(), c.hitRate() * 100, c.avgMs(), verdict);
    }

    public static void main(String[] args) {
        Map<String, Counter> byPrefix = new TreeMap<>();
        byPrefix.put("sku:", new Counter());
        byPrefix.put("order:", new Counter());
        byPrefix.put("user:", new Counter());
        byPrefix.put("cfg:", new Counter());

        // 模拟真实流量：sku 命中高、order 命中低（TTL 太短）、user 中等、cfg 极高
        double[] hitRates = {0.96, 0.38, 0.82, 0.995};
        String[] prefixes = {"sku:", "order:", "user:", "cfg:"};
        int[] volumes = {50_000, 20_000, 15_000, 5_000};

        for (int i = 0; i < prefixes.length; i++) {
            Counter c = byPrefix.get(prefixes[i]);
            for (int n = 0; n < volumes[i]; n++) {
                boolean hit = (n % 1000) < hitRates[i] * 1000;
                long cost = hit ? 300_000L : 8_000_000L;      // 命中 0.3ms，未命中 8ms
                if (hit) {
                    c.hit(cost);
                } else {
                    c.miss(cost);
                }
            }
        }

        System.out.println("【按 key 前缀分组】");
        Counter all = new Counter();
        for (Map.Entry<String, Counter> e : byPrefix.entrySet()) {
            report(e.getKey(), e.getValue(), 0.90);
            all.hits += e.getValue().hits;
            all.misses += e.getValue().misses;
            all.totalNanos += e.getValue().totalNanos;
        }
        System.out.println();
        System.out.println("【整体】");
        report("ALL", all, 0.90);
        System.out.println("  -> 整体 81% 只说明「有问题」，说不出是谁：分前缀才看到 order 38%、user 82%");

        System.out.println();
        System.out.println("【order 命中率低的排查清单】");
        Map<String, String> checks = new LinkedHashMap<>();
        checks.put("TTL", "order 列表 TTL=30s，太短 -> 调到 5 分钟 + 抖动");
        checks.put("key 粒度", "把 order:list:user:1 拆成 3 个 key，回填不完整");
        checks.put("容量", "实例内存 80%，LRU 把 order 挤掉 -> 扩内存或降 TTL");
        checks.put("写路径", "每次下单删 order:list:*，误删一片 -> 改为精确删除");
        checks.forEach((k, v) -> System.out.printf("  %-10s %s%n", k, v));

        System.out.println();
        System.out.println("【告警规则】命中率 < 90% 持续 5 分钟 / 回源 QPS 突增 3 倍 / 内存 > 85% / 大 key > 1MB");
    }
}''',
        exercises=[
            "把 order 的 TTL 从 30 秒改成 300 秒，重新计算命中率，说明 TTL 与命中率的定量关系。",
            "给 report 加 P99 统计（用数组存每次耗时），观察命中率正常但 P99 很差的情况。",
            "思考题：为什么「整体命中率」会掩盖问题？举一个两个业务线命中率互相掩盖的例子。",
        ],
    ),
    dict(
        cls="ProductDetailCache",
        title="综合案例：商品详情页缓存改造",
        points=[
            "**场景约束**：**QPS 2 万、DB 上限 3000、P99 要求 50ms、改价 1 秒内可见、大促零点不能雪崩**——**五个约束决定方案，不是「加个 Redis」就完事**。",
            "**方案分层**：**L1 本地缓存（热点商品，1 秒 TTL）+ L2 Redis（全量商品，5 分钟 + 抖动）+ 布隆过滤器（挡穿透）+ 互斥锁（防击穿）**。",
            "**写路径**：**改价先写库 → 删 Redis → 发广播删各节点 L1**——**1 秒内可见靠 L1 的 1 秒 TTL 兜底**，**广播丢失也不会超过 1 秒**。",
            "**大促保护**：**预热 Top 1 万商品**、**TTL 抖动 0~5 分钟**、**限流（超过 2.5 万 QPS 拒绝）**、**降级开关（Redis 挂 → 只读 L1 + 兜底文案）**。",
            "**验证指标**：**命中率 ≥ 95%**、**回源 QPS < 1000**、**P99 < 50ms**、**改价可见延迟 < 1s**、**压测 2.5 万 QPS 无错误**。",
            "易错点：只做 Redis 不做 L1（热点仍打爆单分片）；改价只删 Redis 不广播（L1 长期旧）；不做预热（零点冷启动打穿）。",
        ],
        code=r'''
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
''',
        exercises=[
            "把 L1 的 TTL 从 1000ms 改成 30000ms，重跑改价时间线，说明「可见性」如何退化。",
            "去掉布隆过滤器，统计 2000 次穿透请求带来的 DB 回源增加量。",
            "思考题：为什么单飞锁要「进锁后再查一次 Redis」？去掉这次重查会发生什么？",
        ],
    ),
]
