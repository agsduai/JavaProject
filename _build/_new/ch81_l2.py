# -*- coding: utf-8 -*-
LESSONS = [
    dict(
        cls="BloomFilter",
        title="空值缓存与布隆过滤器",
        points=[
            "**空值缓存**：**查不到也写一条 `NULL` 进缓存（TTL 60 秒）**——**最简单**的防穿透手段，缺点是**大量随机 key 会把缓存撑爆**，所以 TTL 必须短。",
            "**布隆过滤器**：**一个位数组 + k 个哈希函数**，**写入时把 k 个位置置 1**，**查询时 k 个位置全为 1 才「可能存在」**——**省内存（1 亿 key 约 114MB @ 1% 误判）**。",
            "**布隆的两种错误**：**不会漏判**（存在的一定说存在），**可能误判**（不存在的也可能说存在）——**误判率由位数组长度 m、哈希个数 k、元素个数 n 决定**。",
            "**标准用法**：**请求先过布隆**，说「不存在」直接返回（挡住 99% 的穿透）；说「可能存在」再查缓存/库——**布隆挡在缓存前面，不是替代缓存**。",
            "**布隆的局限**：**不支持删除**（置 1 的位置无法安全清零，会误伤别的 key）——需要删除时用**计数布隆**或**定期重建**。",
            "易错点：布隆和真实数据不同步（新增商品没进布隆，正常请求被挡）；位数组太小导致误判率飙升；把布隆当唯一数据源（它只能回答「可能在不在」）。",
        ],
        code=r'''import java.util.BitSet;
import java.util.Random;

public class BloomFilter {
    static class Bloom {
        final BitSet bits;
        final int size;
        final int hashCount;
        int inserted;

        Bloom(int expected, double fpp) {
            // m = -n*ln(p) / (ln2)^2 ; k = m/n * ln2
            double ln2 = Math.log(2);
            this.size = (int) Math.ceil(-expected * Math.log(fpp) / (ln2 * ln2));
            this.hashCount = Math.max(1, (int) Math.round((double) size / expected * ln2));
            this.bits = new BitSet(size);
        }

        static int mix(int h) {
            h ^= h >>> 16;
            h *= 0x85EBCA6B;
            h ^= h >>> 13;
            h *= 0xC2B2AE35;
            h ^= h >>> 16;
            return h;
        }

        int[] positions(String key) {
            int h1 = mix(key.hashCode());
            int h2 = mix(key.hashCode() ^ 0x5BD1E995) | 1;   // 奇数步长，位置更均匀
            int[] pos = new int[hashCount];
            for (int i = 0; i < hashCount; i++) {
                pos[i] = Math.floorMod(h1 + i * h2, size);   // 双重哈希
            }
            return pos;
        }

        void add(String key) {
            for (int p : positions(key)) {
                bits.set(p);
            }
            inserted++;
        }

        boolean mightContain(String key) {
            for (int p : positions(key)) {
                if (!bits.get(p)) {
                    return false;                        // 一定不存在
                }
            }
            return true;                                 // 可能存在（可能误判）
        }

        double fpp() {
            double k = hashCount;
            double n = inserted;
            return Math.pow(1 - Math.exp(-k * n / size), k);   // 理论误判率
        }
    }

    public static void main(String[] args) {
        int expected = 10_000;
        Bloom bloom = new Bloom(expected, 0.01);
        System.out.printf("位数组 %d bit（%.1f KB），哈希 %d 个%n",
                bloom.size, bloom.size / 8.0 / 1024, bloom.hashCount);

        for (int i = 0; i < expected; i++) {
            bloom.add("sku:" + i);
        }

        int miss = 0;
        for (int i = 0; i < expected; i++) {
            if (!bloom.mightContain("sku:" + i)) {
                miss++;                                  // 漏判：绝不该发生
            }
        }
        System.out.printf("已存在 key 的漏判数：%d（必须为 0）%n", miss);

        Random rnd = new Random(42);
        int falsePositive = 0;
        int probes = 100_000;
        for (int i = 0; i < probes; i++) {
            if (bloom.mightContain("ghost:" + rnd.nextInt(1_000_000))) {
                falsePositive++;
            }
        }
        System.out.printf("不存在的 key 误判率：%.2f%%（理论 %.1f%%）%n",
                falsePositive * 100.0 / probes, bloom.fpp() * 100);

        System.out.println();
        System.out.println("【用法】请求 -> 布隆说「不存在」-> 直接返回空（挡住绝大多数穿透）");
        System.out.println("        请求 -> 布隆说「可能存在」-> 查缓存 -> 未命中查库 -> 回填");
        System.out.println("【注意】布隆不支持删除；商品上下架要同步布隆，否则会误挡正常请求");
    }
}''',
        exercises=[
            "把误判率目标从 0.01 改成 0.001，观察位数组膨胀多少倍、哈希个数怎么变。",
            "给 Bloom 加一个「计数布隆」版本（每个位置用 byte 计数），实现 remove 方法。",
            "思考题：布隆过滤器为什么「不会漏判但可能误判」？从哈希碰撞的方向分析。",
        ],
    ),
    dict(
        cls="CacheConsistency",
        title="缓存一致性：双写与失效",
        points=[
            "**一致性不是「强一致」而是「收敛」**：**允许短暂不一致，但最终必须收敛到库里的值**——**缓存是加速层，数据库是唯一真相源（Single Source of Truth）**。",
            "**四种更新策略对比**：**先写库再删缓存（推荐）**、**先删缓存再写库（有并发漏洞）**、**先写库再更新缓存（并发写顺序乱）**、**延迟双删（缓解但不彻底）**。",
            "**为什么「先写库再删缓存」最好**：**删是幂等的**，**并发下最坏结果是「多删一次」导致一次回源**，**不会把旧值永久留在缓存里**。",
            "**「先删缓存再写库」的漏洞**：线程 A 删缓存 → 线程 B 读未命中、读库拿到旧值 → A 写库 → B 把旧值回填 → **缓存永久是旧值**。",
            "**「更新缓存」的漏洞**：两个写请求 A、B 并发，A 先写库、B 后写库，但 B 先更新缓存、A 后更新缓存 → **缓存留下 A 的旧值**。",
            "易错点：以为「先写库再删缓存」就绝对一致（删失败会留旧值，需要重试/订阅 binlog 兜底）；把 TTL 当一致性方案（TTL 只管最终收敛，不管窗口内错多久）。",
        ],
        code=r'''import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

public class CacheConsistency {
    static final Map<String, String> DB = new HashMap<>();
    static final Map<String, String> CACHE = new HashMap<>();

    static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    static void reset() {
        DB.clear();
        CACHE.clear();
        DB.put("price:1001", "100");
        CACHE.put("price:1001", "100");
    }

    // 策略一：先写库，再删缓存（推荐）
    static void writeDbThenEvict(String k, String v) {
        DB.put(k, v);
        sleep(30);                 // 写库后、删缓存前的时间窗
        CACHE.remove(k);
    }

    // 策略二：先删缓存，再写库（有并发漏洞）
    static void evictThenWriteDb(String k, String v) {
        CACHE.remove(k);
        sleep(30);
        DB.put(k, v);
    }

    // 策略三：先写库，再更新缓存（并发写顺序乱）
    static void writeDbThenUpdate(String k, String v) {
        DB.put(k, v);
        sleep(30);
        CACHE.put(k, v);
    }

    static String read(String k) {
        String v = CACHE.get(k);
        if (v != null) {
            return v;
        }
        sleep(10);                 // 模拟读库
        v = DB.get(k);
        if (v != null) {
            CACHE.put(k, v);       // 回填
        }
        return v;
    }

    static void runRace(String name, Runnable writer) throws Exception {
        reset();
        AtomicReference<String> observed = new AtomicReference<>("-");
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);

        Thread w = new Thread(() -> {
            try {
                start.await();
                writer.run();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                done.countDown();
            }
        });
        Thread r = new Thread(() -> {
            try {
                start.await();
                sleep(5);           // 读者在写线程刚动手时进入，正好落在时间窗内
                observed.set(read("price:1001"));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                done.countDown();
            }
        });
        w.start();
        r.start();
        start.countDown();
        done.await();

        String cacheNow = CACHE.getOrDefault("price:1001", "(空)");
        String dbNow = DB.get("price:1001");
        String afterRead = read("price:1001");
        System.out.printf("%-16s 并发读到=%-4s 结束时缓存=%-5s 再读一次=%-4s 库=%-4s%n",
                name, observed.get(), cacheNow, afterRead, dbNow);
    }

    // 两个写并发：A 先写库但后更新缓存，B 后写库但先更新缓存 -> 缓存留下 A 的旧值
    static void twoWriters(boolean updateCache) throws Exception {
        reset();
        Thread a = new Thread(() -> {
            DB.put("price:1001", "200");        // A 先写库
            sleep(40);
            if (updateCache) {
                CACHE.put("price:1001", "200");
            } else {
                CACHE.remove("price:1001");
            }
        });
        Thread b = new Thread(() -> {
            sleep(20);
            DB.put("price:1001", "300");        // B 后写库
            if (updateCache) {
                CACHE.put("price:1001", "300"); // B 先更新缓存
            } else {
                CACHE.remove("price:1001");
            }
        });
        a.start();
        b.start();
        a.join();
        b.join();
        System.out.printf("  两写并发(%s)：最终库=%s 缓存=%s -> %s%n",
                updateCache ? "更新缓存" : "删缓存",
                DB.get("price:1001"),
                CACHE.getOrDefault("price:1001", "(空)"),
                updateCache ? "缓存被旧值 200 覆盖 <<<" : "缓存为空，下次读回源拿 300 ✓");
    }

    public static void main(String[] args) throws Exception {
        runRace("写库->删缓存", () -> writeDbThenEvict("price:1001", "200"));
        runRace("删缓存->写库", () -> evictThenWriteDb("price:1001", "200"));
        runRace("写库->更新缓存", () -> writeDbThenUpdate("price:1001", "200"));

        System.out.println();
        System.out.println("【两个写并发】");
        twoWriters(true);
        twoWriters(false);

        System.out.println();
        System.out.println("【结论】");
        System.out.println("  写库->删缓存：并发读者可能读到旧值一次（窗口内），但缓存里没有残留，"
                + "下一次读必然回源拿到新值 -> 收敛");
        System.out.println("  删缓存->写库：读者在写库前读到旧值并回填，缓存里留下旧值，"
                + "再读还是旧值 -> 长期不一致（最坏情况）");
        System.out.println("  写库->更新缓存：两个写并发时后写库的可能先更新缓存，"
                + "缓存被旧值覆盖 -> 不一致");
        System.out.println("  所以：删缓存（不更新）+ TTL 兜底 + 必要时订阅 binlog 补偿删除");
    }
}''',
        exercises=[
            "加第三个线程（两个写请求并发），演示「写库->更新缓存」留下的旧值，打印两次写库的先后顺序。",
            "给「写库->删缓存」加上删除失败重试（失败 3 次后进补偿队列），说明为什么需要兜底。",
            "思考题：为什么「删缓存」是幂等操作而「更新缓存」不是？从「执行次数是否影响最终结果」分析。",
        ],
    ),
    dict(
        cls="DelayedDoubleDelete",
        title="延迟双删与 binlog 订阅",
        points=[
            "**延迟双删**：**写库前删一次缓存 → 写库 → 睡 500ms 再删一次**——**第二次删是为了清掉「写库期间被并发读回填的旧值」**，**延迟要大于一次读库 + 回填的耗时**。",
            "**延迟双删的局限**：**延迟时间靠猜**（读库慢就失效）、**第二次删失败就没兜底**——**所以它只是「降低概率」，不是「保证一致」**。",
            "**binlog 订阅（canal / Debezium 思路）**：**数据库变更写入 binlog → 订阅组件解析 → 投递到 MQ → 缓存服务消费并删 key**——**业务代码零侵入，且删除可重试、可顺序化**。",
            "**binlog 方案的优点**：**写路径不关心缓存**（少一次调用）、**删除失败可重试**、**可回放补数据**、**天然支持多级缓存一起失效**。",
            "**binlog 方案的代价**：**多一个组件要运维**、**有秒级延迟**（不是实时）、**要处理顺序与重复消费（删除幂等所以重复无害）**。",
            "易错点：延迟双删的 sleep 写死 100ms（读库 200ms 时完全无效）；binlog 消费失败不重试（缓存永久脏）；以为 binlog 是「强一致」（它仍是最终一致，只是窗口更小更可控）。",
        ],
        code=r'''
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;

public class DelayedDoubleDelete {
    static final Map<String, String> DB = new HashMap<>();
    static final Map<String, String> CACHE = new HashMap<>();
    static final List<String> LOG = Collections.synchronizedList(new ArrayList<>());

    static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // 读：命中直接返回；未命中先取库快照，再「慢查询」80ms 后回填。
    // snapshotTaken 用来告诉外部线程：快照已经取到，现在可以改库了。
    static String read(String k, CountDownLatch snapshotTaken) {
        String v = CACHE.get(k);
        if (v != null) {
            LOG.add("读 " + k + " 命中缓存=" + v);
            return v;
        }
        v = DB.get(k);                              // 快照：这一刻库里的值
        if (snapshotTaken != null) {
            snapshotTaken.countDown();
        }
        sleep(80);                                  // 模拟 80ms 慢查询
        if (v != null) {
            CACHE.put(k, v);                        // 回填的是 80ms 前的旧值
            LOG.add("读 " + k + " 未命中，读库=" + v + "，80ms 后回填");
        }
        return v;
    }

    static String read(String k) {
        return read(k, null);
    }

    static void scenarioDelayed(long delayMs) throws InterruptedException {
        DB.clear();
        CACHE.clear();
        LOG.clear();
        DB.put("stock:1", "100");
        CACHE.put("stock:1", "100");

        // 固定时序：删缓存 -> 读者取到旧快照 -> 写库 -> 睡 delayMs -> 第二次删 -> 读者回填
        CACHE.remove("stock:1");
        LOG.add("第 1 次删缓存 stock:1");
        CountDownLatch snapshot = new CountDownLatch(1);
        Thread reader = new Thread(() -> read("stock:1", snapshot));
        reader.start();
        snapshot.await();                           // 读者已经拿到 100，正在 80ms 读库
        DB.put("stock:1", "90");
        LOG.add("写库 stock:1=90");
        sleep(delayMs);                             // 第二次删的延迟
        CACHE.remove("stock:1");
        LOG.add("第 2 次删缓存（延迟 " + delayMs + "ms）");
        reader.join();                              // 等读者把旧值回填完
        String after = read("stock:1");             // 再读一次，看缓存里留下了什么
        boolean converged = String.valueOf(after).equals(DB.get("stock:1"));
        System.out.printf("【延迟双删 delay=%dms】最终读到 %s，缓存=%s，库=%s -> %s%n",
                delayMs, after, CACHE.get("stock:1"), DB.get("stock:1"),
                converged ? "收敛" : "仍是旧值，不一致");
        for (String s : LOG) {
            System.out.println("    " + s);
        }
        System.out.println();
    }

    // ---------- binlog 订阅：业务只写库，缓存由订阅端删 ----------
    static class BinlogSubscriber {
        final LinkedBlockingQueue<String> queue = new LinkedBlockingQueue<>();
        int retries;

        void publish(String key) {
            queue.offer(key);                       // 模拟 binlog 事件
        }

        void consumeOnce(boolean failFirst) {
            String k = queue.poll();
            if (k == null) {
                return;
            }
            if (failFirst) {
                retries++;
                queue.offer(k);                     // 消费失败，重新入队（至少一次语义）
                LOG.add("订阅端删除失败，重新入队 " + k);
                return;
            }
            CACHE.remove(k);
            LOG.add("订阅端删除缓存 " + k + "（幂等，重复无害）");
        }
    }

    public static void main(String[] args) throws InterruptedException {
        scenarioDelayed(50);        // 延迟 50ms < 80ms 读库：第二次删早于回填，旧值留下
        scenarioDelayed(150);       // 延迟 150ms > 80ms 读库：第二次删晚于回填，收敛

        System.out.println("========== binlog 订阅 ==========");
        DB.clear();
        CACHE.clear();
        LOG.clear();
        DB.put("stock:2", "50");
        CACHE.put("stock:2", "50");
        BinlogSubscriber sub = new BinlogSubscriber();

        DB.put("stock:2", "40");                    // 业务只写库，完全不碰缓存
        sub.publish("stock:2");                     // 库变更进 binlog
        sub.consumeOnce(true);                      // 第一次消费失败
        sub.consumeOnce(false);                     // 重试成功
        System.out.println("  最终缓存=" + CACHE.getOrDefault("stock:2", "null")
                + "，库=" + DB.get("stock:2") + "，重试次数=" + sub.retries);
        for (String s : LOG) {
            System.out.println("    " + s);
        }

        System.out.println();
        System.out.println("【选型】延迟双删 = 零额外组件、只是降低概率；binlog = 多一个组件、可重试可回放");
        System.out.println("        对一致性要求高（价格/库存）用 binlog；普通信息类用「删缓存 + TTL」即可");
    }
}
''',
        exercises=[
            "把 scenarioDelayed 的读库耗时改成 200ms，测试 delay 多少才能稳定收敛，找出经验公式。",
            "给 BinlogSubscriber 加「重复消费」场景（同一条 binlog 投递两次），验证删除幂等。",
            "思考题：为什么 binlog 方案里「重复消费」是安全的，而「更新缓存」方案里重复消费可能出错？",
        ],
    ),
    dict(
        cls="HotKeyLocalCache",
        title="热点 key 与本地缓存",
        points=[
            "**热点 key**：**单个 key 的 QPS 占全站很大比例**（如秒杀商品、热搜）——**单分片扛不住**，**Redis 集群再大也只落在一个节点上**。",
            "**热点探测**：**滑动窗口统计 key 访问次数**，**超过阈值（如 1 万/秒）判定为热点**，然后**推送本地缓存 / 拆分 key / 限流**。",
            "**本地缓存（进程内）**：**命中不走网络（纳秒级）**，**容量小、各节点独立、更新要广播**——**适合「极热 + 变化不频繁」的数据**（81-09 讲多级）。",
            "**本地缓存的一致性问题**：**N 个节点 N 份副本**，**更新要发广播（MQ/配置中心）**，**广播丢失就有节点长期旧**——**所以本地缓存 TTL 要短（秒级）**。",
            "**热点 key 的三板斧**：**本地缓存挡在前面**、**key 拆分（hot:1:0~9 分散到多个节点）**、**读写分离 + 限流（保护后端）**。",
            "易错点：本地缓存设 1 小时 TTL（更新要等一小时）；用 HashMap 当本地缓存（无上限、无淘汰、会 OOM）；热点 key 拆分后聚合结果算错。",
        ],
        code=r'''
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class HotKeyLocalCache {
    // ---------- 极简 LRU 本地缓存 ----------
    static class LocalCache<K, V> extends LinkedHashMap<K, V> {
        final int capacity;
        long hits;
        long misses;

        LocalCache(int capacity) {
            super(16, 0.75f, true);       // accessOrder=true -> LRU
            this.capacity = capacity;
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
            return size() > capacity;
        }

        V getOrLoad(K k, java.util.function.Function<K, V> loader) {
            V v = get(k);
            if (v != null) {
                hits++;
                return v;
            }
            misses++;
            v = loader.apply(k);
            if (v != null) {
                put(k, v);
            }
            return v;
        }
    }

    // ---------- 滑动窗口热点探测 ----------
    static class HotKeyDetector {
        final Map<String, int[]> windows = new HashMap<>();
        final int buckets;
        final int threshold;

        HotKeyDetector(int buckets, int threshold) {
            this.buckets = buckets;
            this.threshold = threshold;
        }

        void record(String key, long nowSec) {
            int[] w = windows.computeIfAbsent(key, k -> new int[buckets]);
            w[(int) (nowSec % buckets)]++;
        }

        int count(String key) {
            int[] w = windows.get(key);
            if (w == null) {
                return 0;
            }
            int sum = 0;
            for (int v : w) {
                sum += v;
            }
            return sum;
        }

        boolean isHot(String key) {
            return count(key) >= threshold;
        }
    }

    public static void main(String[] args) {
        Map<String, String> redis = new HashMap<>();
        for (int i = 1; i <= 100; i++) {
            redis.put("sku:" + i, "商品" + i);
        }
        LocalCache<String, String> local = new LocalCache<>(16);
        HotKeyDetector detector = new HotKeyDetector(10, 50);

        // 模拟：sku:7 是热点（占 60% 流量），其余长尾
        int tailRedis = 0;                 // 长尾：直接走 Redis
        int hotRedis = 0;                  // 热点：本地未命中才走 Redis
        int hotLocal = 0;                  // 热点：本地命中
        long now = 1000;
        for (int i = 0; i < 1000; i++) {
            String key = (i % 10 < 6) ? "sku:7" : "sku:" + (1 + (i * 37) % 100);
            detector.record(key, now);
            if (detector.isHot(key)) {
                long missBefore = local.misses;
                String v = local.getOrLoad(key, redis::get);
                if (local.misses > missBefore) {
                    hotRedis++;
                } else {
                    hotLocal++;
                }
                if (v == null) {
                    throw new IllegalStateException("热点 key 不存在：" + key);
                }
            } else {
                redis.get(key);            // 还没被判为热点，走 Redis
                tailRedis++;
            }
            if (i % 100 == 99) {
                now++;                     // 每 100 次请求推进 1 秒（滑动窗口按秒记）
            }
        }

        int redisCalls = tailRedis + hotRedis;
        System.out.printf("热点探测：sku:7 窗口内计数=%d，阈值=50，判定=%s%n",
                detector.count("sku:7"), detector.isHot("sku:7"));
        System.out.printf("本地缓存：容量=%d 条，命中 %d 次，未命中 %d 次%n",
                local.capacity, local.hits, local.misses);
        System.out.printf("Redis 调用：%d 次 / 总请求 1000 次（%.1f%%）%n",
                redisCalls, redisCalls / 10.0);
        System.out.printf("  其中 长尾直连 %d 次 + 热点判热前/首次回源 %d 次%n", tailRedis, hotRedis);
        System.out.printf("  热点命中本地缓存 %d 次，这 %d 次完全没走网络%n", hotLocal, hotLocal);

        System.out.println();
        System.out.println("【热点三板斧】");
        System.out.println("  1. 本地缓存：热点 key 落在每个进程内，不打 Redis（纳秒级）");
        System.out.println("  2. key 拆分：hot:1 拆成 hot:1:0 ~ hot:1:9，分散到 10 个分片");
        System.out.println("  3. 限流兜底：热点超出后端能力时，宁可拒绝也不把 DB 打挂");
        System.out.println();
        System.out.println("【本地缓存注意】TTL 必须短（1~5 秒），更新靠广播；否则多节点数据长期不一致");
        System.out.println("【探测注意】判热需要一点时间（本示例前 50 次仍走 Redis），"
                + "所以大促热点要靠「预热 + 人工白名单」补上这段窗口");
    }
}
''',
        exercises=[
            "修正上面的 redisCalls 统计（只统计真正走 Redis 的次数），算出本地缓存把 Redis 调用降低了多少。",
            "把热点阈值从 50 改成 200，观察长尾 key 被误判为热点的数量变化。",
            "思考题：为什么热点 key 拆分后，读取时要做「聚合」？聚合会带来什么新问题？",
        ],
    ),
]
