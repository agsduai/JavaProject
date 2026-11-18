# 第25章 Redis与缓存

第24章的服务已经能对外提供接口了，但只要把数据库放在请求链路上，QPS 一高就会顶到 MySQL 的连接数和磁盘 IO 上限。真实的读多写少场景里，90% 以上的请求其实查的是同一批热点数据，把它们放进内存就是最直接的加速手段。本章讲 Redis：先看它为什么快、五种数据类型各管什么，再讲 key 的设计与过期淘汰、RDB/AOF 两种持久化，然后用 Spring Data Redis 把缓存接进业务，最后处理缓存穿透、击穿、雪崩这三个面试必问的坑，并用 Redis 实现分布式锁。示例代码全部用纯 JDK 手写一个迷你 Redis（哈希表存储 + 过期字典 + 单线程命令循环 + 简易 AOF 日志），不引入任何外部依赖，运行后能直观看到每种数据结构的行为与淘汰过程；真正接入时把 `MiniRedis` 换成 `RedisTemplate` 即可，命令语义是一一对应的。

### 25-01 为什么需要Redis

**知识点**
- 数据库的瓶颈：连接数有限（MySQL 默认 151）、磁盘随机读慢、复杂查询占 CPU；一台 8C16G 的 MySQL 通常几千 QPS 就到顶。
- 缓存的价值：把热点数据放内存，读走内存后响应从毫秒级降到微秒级，同时把数据库的读压力挡在外面。
- Redis 为什么快：纯内存操作、单线程执行命令（无锁无上下文切换）、IO 多路复用（epoll）、高效的数据结构（SDS、跳表、压缩列表）。
- 单线程指的是"命令执行"单线程；网络 IO 在 6.0 之后是多线程的，持久化、过期删除也有后台线程。
- 适用场景：缓存、会话共享、排行榜、计数器、分布式锁、消息队列（Stream）、去重（Bitmap/HyperLogLog）。
- 不适合：需要复杂事务与多表关联的强一致数据、单 key 超大 value（会阻塞主线程）、海量冷数据（内存贵）。
- 易错：把 Redis 当数据库用（不做持久化兜底、不设过期时间），一旦重启或内存淘汰就丢数据。

**示例代码**
```java
import java.util.LinkedHashMap;
import java.util.Map;

public class WhyRedis {
    public static void main(String[] args) {
        System.out.println("=== 一次查询的耗时对比 ===");
        Map<String, String> cost = new LinkedHashMap<>();
        cost.put("MySQL 主键查询（内存命中）", "0.5 ~ 2 ms");
        cost.put("MySQL 主键查询（走磁盘）", "5 ~ 20 ms");
        cost.put("MySQL 复杂关联查询", "50 ~ 500 ms");
        cost.put("Redis GET（本机）", "0.05 ~ 0.2 ms");
        cost.put("Redis GET（同机房网络）", "0.3 ~ 1 ms");
        cost.forEach((k, v) -> System.out.printf("  %-28s %s%n", k, v));

        System.out.println();
        System.out.println("=== 加缓存前后的数据库压力 ===");
        int qps = 10000;
        double hitRate = 0.95;
        System.out.println("  总请求        : " + qps + " QPS");
        System.out.println("  缓存命中率    : " + (int) (hitRate * 100) + "%");
        System.out.println("  打到数据库    : " + (int) (qps * (1 - hitRate)) + " QPS");
        System.out.println("  结论：命中率每提高 1 个点，数据库压力就少一截");

        System.out.println();
        System.out.println("=== Redis 快的四个原因 ===");
        String[] reasons = {
                "纯内存：读写不碰磁盘，纳秒级寻址",
                "单线程执行命令：没有锁竞争和线程切换开销",
                "IO 多路复用：一个线程用 epoll 管上万连接",
                "数据结构专为场景设计：跳表做有序集合、SDS 做字符串"};
        for (int i = 0; i < reasons.length; i++) {
            System.out.println("  " + (i + 1) + ". " + reasons[i]);
        }

        System.out.println();
        System.out.println("=== 常见用途 ===");
        Map<String, String> usage = new LinkedHashMap<>();
        usage.put("缓存", "商品详情、用户信息、首页楼层");
        usage.put("会话", "分布式 session、短信验证码");
        usage.put("计数", "阅读量、点赞数（INCR 原子自增）");
        usage.put("排行榜", "ZSET 按分数排序取 TOP N");
        usage.put("分布式锁", "SETNX + 过期时间，多实例互斥");
        usage.forEach((k, v) -> System.out.printf("  %-8s %s%n", k, v));
    }
}
```

**课后练习**
1. 计算：某接口 20000 QPS，缓存命中率 99%，数据库实际承受多少 QPS？如果命中率掉到 90% 呢？
2. 用自己的话解释"Redis 单线程为什么反而快"，并说明 6.0 的多线程 IO 改的是哪一部分。
3. 列出三个不适合放 Redis 的数据，并说明原因。

### 25-02 五种基本数据类型

**知识点**
- `String`：最常用，可存文本、JSON、序列化对象、数字；单 key 最大 512MB；`INCR/DECR` 原子计数，`SETNX` 做锁。
- `Hash`：field-value 结构，适合存对象的多个字段，可单独更新某个字段（`HSET user:1 name 张三`），比整个 String 覆盖省流量。
- `List`：双向链表，`LPUSH/RPUSH` 入队、`LPOP/RPOP` 出队，可做栈、队列、消息列表；`LRANGE` 分页。
- `Set`：无序去重集合，`SADD/SMEMBERS`、`SINTER/SUNION/SDIFF` 做交集并集差集，适合标签、共同好友、抽奖去重。
- `ZSet`：带 score 的有序集合，底层跳表 + 哈希，`ZADD/ZRANGE/ZREVRANGE/ZINCRBY`，适合排行榜、延时队列。
- 选择原则：能用一个 key 表达的整体用 String；需要按字段更新用 Hash；需要顺序或去重用 List/Set；需要排序用 ZSet。
- 易错：用 `KEYS *` 遍历（生产禁用，会阻塞）；用 List 存海量数据做队列（应用 Stream 或专业 MQ）；ZSet 的 score 用浮点，精度只到 2^53。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class FiveDataTypes {
    public static void main(String[] args) {
        System.out.println("=== String：计数与缓存 ===");
        Map<String, String> stringStore = new LinkedHashMap<>();
        stringStore.put("article:1001:views", "0");
        // 模拟 INCR：Redis 的 INCR 是原子操作，这里用同步块保证多线程安全
        for (int i = 0; i < 3; i++) {
            int v = Integer.parseInt(stringStore.get("article:1001:views")) + 1;
            stringStore.put("article:1001:views", String.valueOf(v));
        }
        System.out.println("  阅读量 = " + stringStore.get("article:1001:views"));
        System.out.println("  命令：INCR article:1001:views  -> 返回自增后的值");

        System.out.println();
        System.out.println("=== Hash：对象的字段级更新 ===");
        Map<String, Map<String, String>> hashStore = new LinkedHashMap<>();
        Map<String, String> user = new LinkedHashMap<>();
        user.put("name", "张三");
        user.put("age", "18");
        user.put("city", "杭州");
        hashStore.put("user:1", user);
        hashStore.get("user:1").put("age", "19");   // HSET user:1 age 19
        System.out.println("  HGETALL user:1 -> " + hashStore.get("user:1"));
        System.out.println("  只改了 age 一个字段，name/city 不用重新序列化");

        System.out.println();
        System.out.println("=== List：队列与栈 ===");
        List<String> list = new ArrayList<>();
        list.add("msg-1");                                  // RPUSH
        list.add("msg-2");
        list.add(0, "msg-0");                               // LPUSH
        System.out.println("  LRANGE queue 0 -1 -> " + list);
        System.out.println("  LPOP -> " + list.remove(0) + "（队列：先进先出）");
        System.out.println("  RPOP -> " + list.remove(list.size() - 1) + "（栈：后进先出）");
        System.out.println("  剩余 -> " + list);

        System.out.println();
        System.out.println("=== Set：去重与集合运算 ===");
        Set<String> tagA = new LinkedHashSet<>(List.of("Java", "Redis", "MySQL"));
        Set<String> tagB = new LinkedHashSet<>(List.of("Redis", "MQ", "Java"));
        Set<String> inter = new LinkedHashSet<>(tagA);
        inter.retainAll(tagB);
        Set<String> union = new LinkedHashSet<>(tagA);
        union.addAll(tagB);
        Set<String> diff = new LinkedHashSet<>(tagA);
        diff.removeAll(tagB);
        System.out.println("  A = " + tagA);
        System.out.println("  B = " + tagB);
        System.out.println("  SINTER 交集 = " + inter);
        System.out.println("  SUNION 并集 = " + union);
        System.out.println("  SDIFF  A-B  = " + diff);

        System.out.println();
        System.out.println("=== ZSet：排行榜 ===");
        TreeMap<Integer, String> rank = new TreeMap<>((a, b) -> b - a);  // 分数倒序
        rank.put(980, "选手A");     // ZADD board 980 选手A
        rank.put(760, "选手B");
        rank.put(1120, "选手C");
        rank.put(880, "选手D");
        System.out.println("  ZREVRANGE board 0 -1 WITHSCORES ->");
        int pos = 1;
        for (Map.Entry<Integer, String> e : rank.entrySet()) {
            System.out.println("    " + pos++ + ". " + e.getValue() + "  " + e.getKey());
        }
        System.out.println("  ZINCRBY board 50 选手B -> 分数变 810，名次自动重排");
    }
}
```

**课后练习**
1. 设计一个"商品详情"的 key 结构，要求：能单独更新库存、能整体失效、能统计浏览量。说明每个 key 用哪种类型。
2. 用 Set 实现"共同关注"和"可能认识的人"，写出对应的集合运算。
3. 为什么排行榜用 ZSet 而不是每次 `ORDER BY` 查数据库？从复杂度和数据库压力两方面回答。

### 25-03 key的设计与过期策略

**知识点**
- 命名规范：`业务:对象:标识:字段`，如 `shop:item:10086:stock`；用冒号分层，便于按前缀统计和批量操作，长度控制在可读范围内。
- 禁止大 key：单个 String 不超过 10KB，Hash/List/Set/ZSet 元素数不超过 5000；大 key 会让删除、迁移、网络传输全部变慢。
- 禁止热 key 集中：同一个 key 被超高并发访问会打满单个分片，需要本地缓存或 key 打散。
- 过期命令：`EXPIRE key 秒`、`PEXPIRE key 毫秒`、`SET key value EX 60`（推荐，设置值和过期一步完成，避免 SET 后进程挂掉留下永不过期的 key）。
- 过期删除策略：**惰性删除**（访问时才检查是否过期）+ **定期删除**（每 100ms 随机抽查 20 个带过期时间的 key，删掉过期的，超过 25% 过期就继续抽查）。
- 内存淘汰策略（`maxmemory-policy`）：`noeviction`（默认，写满报错）、`allkeys-lru`、`allkeys-lfu`、`volatile-lru`、`volatile-ttl`、`allkeys-random`。缓存场景通常选 `allkeys-lru` 或 `allkeys-lfu`。
- 易错：只设 `maxmemory` 不设淘汰策略，写满后所有写命令报 `OOM command not allowed`；给缓存设了过期时间但业务读不到时又回源失败，导致空值反复穿透（见 25-09）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class KeyDesignAndExpire {
    public static void main(String[] args) {
        System.out.println("=== key 命名对比 ===");
        Map<String, String> bad = new LinkedHashMap<>();
        bad.put("user1name", "缺少分隔符，看不出层级");
        bad.put("k_10086", "缩写无意义，半年后没人看得懂");
        bad.put("shop_item_10086_stock_cache_v2", "过长，浪费内存也难敲");
        Map<String, String> good = new LinkedHashMap<>();
        good.put("user:1:name", "业务:对象:标识:字段");
        good.put("shop:item:10086:stock", "同一前缀便于统计和批量删除");
        good.put("sms:code:13800138000", "带业务语义，能一眼看出用途");
        System.out.println("  反例：");
        bad.forEach((k, v) -> System.out.printf("    %-36s %s%n", k, v));
        System.out.println("  正例：");
        good.forEach((k, v) -> System.out.printf("    %-28s %s%n", k, v));

        System.out.println();
        System.out.println("=== 惰性删除 + 定期删除 ===");
        // key -> 过期时间戳（毫秒），0 表示永不过期
        Map<String, Long> store = new LinkedHashMap<>();
        long now = 1000L;
        store.put("a", now + 100);        // 100ms 后过期
        store.put("b", now + 10000);      // 10s 后过期
        store.put("c", 0L);               // 永不过期
        store.put("d", now + 50);
        System.out.println("  写入 4 个 key，其中 3 个带过期时间");

        // 惰性删除：get 时才发现 a 过期
        System.out.println("  GET a  -> " + lazyGet(store, "a", now + 200) + "（访问时才发现过期并删除）");
        System.out.println("  剩余 key: " + store.keySet());

        // 定期删除：后台线程每 100ms 抽查
        System.out.println("  定期删除抽查一轮（随机取 2 个带过期时间的 key）：");
        int checked = 0;
        Iterator<Map.Entry<String, Long>> it = store.entrySet().iterator();
        while (it.hasNext() && checked < 2) {
            Map.Entry<String, Long> e = it.next();
            if (e.getValue() == 0) continue;
            checked++;
            if (e.getValue() <= now + 300) {
                System.out.println("    删除过期 key: " + e.getKey());
                it.remove();
            }
        }
        System.out.println("  剩余 key: " + store.keySet());

        System.out.println();
        System.out.println("=== 内存淘汰策略 ===");
        Map<String, String> policies = new LinkedHashMap<>();
        policies.put("noeviction", "不淘汰，写满直接报错（默认，适合当数据库用）");
        policies.put("allkeys-lru", "所有 key 里淘汰最久未使用的（纯缓存首选）");
        policies.put("allkeys-lfu", "所有 key 里淘汰访问频率最低的（能扛住偶发批量扫描）");
        policies.put("volatile-lru", "只在设了过期时间的 key 里淘汰（缓存与持久数据混存时用）");
        policies.put("volatile-ttl", "优先淘汰剩余存活时间最短的");
        policies.forEach((k, v) -> System.out.printf("  %-16s %s%n", k, v));
        System.out.println();
        System.out.println("  配置示例：maxmemory 2gb / maxmemory-policy allkeys-lru");
    }

    /** 惰性删除：取的时候发现过期就删掉并返回 null */
    static String lazyGet(Map<String, Long> store, String key, long now) {
        Long expireAt = store.get(key);
        if (expireAt == null) return null;
        if (expireAt != 0 && expireAt <= now) {
            store.remove(key);
            return null;
        }
        return "<" + key + " 的值>";
    }
}
```

**课后练习**
1. 为电商系统设计 5 个 key（商品详情、库存、购物车、用户 token、首页楼层），写出完整 key 名和所用类型。
2. 解释为什么推荐 `SET key value EX 60` 而不是 `SET` 之后单独 `EXPIRE`。
3. 缓存集群内存被打满，现象是什么？给出 `maxmemory` 与 `maxmemory-policy` 的配置建议并说明理由。

### 25-04 持久化：RDB与AOF

**知识点**
- Redis 是内存数据库，进程退出内存就没了，持久化解决"重启后数据还在"的问题。
- **RDB**（快照）：把某一时刻的全量数据二进制写到 `dump.rdb`；触发方式有 `save`（阻塞）、`bgsave`（fork 子进程，写时复制）、配置 `save 900 1`（900 秒内至少 1 次写就触发）。
  - 优点：文件紧凑、恢复快，适合备份和全量迁移。
  - 缺点：两次快照之间的数据会丢；fork 大内存实例时会有短暂卡顿。
- **AOF**（追加日志）：把每条写命令追加到 `appendonly.aof`；刷盘策略 `appendfsync always/everysec/no`，生产一般用 `everysec`（最多丢 1 秒）。
  - 优点：数据更安全，可读、可修复。
  - 缺点：文件比 RDB 大，恢复慢；需要 `BGREWRITEAOF` 重写压缩。
- 混合持久化（4.0+，`aof-use-rdb-preamble yes`）：AOF 文件前半段是 RDB 全量，后半段是增量命令，兼顾恢复速度与数据安全。
- 主从 + 哨兵/集群解决的是"机器挂了"，持久化解决的是"进程/机器重启后数据还在"，两者不能互相替代。
- 易错：只开 RDB 就以为数据万无一失；`appendfsync always` 会显著拖慢写入；AOF 重写期间磁盘写满会导致 Redis 拒绝写入。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PersistenceDemo {
    public static void main(String[] args) {
        System.out.println("=== RDB：定时拍快照 ===");
        // 模拟内存数据 + RDB 文件（这里用 List 表示快照内容）
        Map<String, String> memory = new LinkedHashMap<>();
        List<String> rdbFile = new ArrayList<>();
        memory.put("user:1:name", "张三");
        memory.put("user:1:age", "18");
        memory.put("item:100:stock", "50");

        System.out.println("  触发条件：save 900 1（900 秒内至少 1 次写）");
        System.out.println("  执行 BGSAVE：fork 子进程，把当前全量数据写入 dump.rdb");
        rdbFile.addAll(memory.keySet());
        System.out.println("  快照内容: " + rdbFile);
        System.out.println("  快照之后又写了 2 条新数据（这些还没进 RDB）");
        memory.put("item:101:stock", "20");
        memory.put("order:9001:status", "PAID");
        System.out.println("  当前内存: " + memory.keySet());
        System.out.println("  -> 此刻断电，重启后只能恢复到快照那一刻，丢失 2 条");

        System.out.println();
        System.out.println("=== AOF：追加每条写命令 ===");
        List<String> aofFile = new ArrayList<>();
        aofFile.add("SET user:1:name 张三");
        aofFile.add("SET user:1:age 18");
        aofFile.add("SET item:100:stock 50");
        aofFile.add("SET item:101:stock 20");
        aofFile.add("SET order:9001:status PAID");
        System.out.println("  文件内容：");
        aofFile.forEach(s -> System.out.println("    " + s));
        System.out.println("  刷盘策略 appendfsync everysec -> 最多丢 1 秒的数据");
        System.out.println("  -> 此刻断电，重放这 5 条命令，数据一条不少");

        System.out.println();
        System.out.println("=== AOF 重写 ===");
        List<String> before = new ArrayList<>(List.of(
                "SET counter 0", "INCR counter", "INCR counter", "INCR counter",
                "INCR counter", "INCR counter", "DEL tmp", "SET tmp a", "SET tmp b"));
        List<String> after = new ArrayList<>(List.of("SET counter 5", "SET tmp b"));
        System.out.println("  重写前 " + before.size() + " 条：" + before);
        System.out.println("  重写后 " + after.size() + " 条：" + after);
        System.out.println("  原理：读当前内存状态，用最少命令重建同样的数据");

        System.out.println();
        System.out.println("=== 三种方案对比 ===");
        Map<String, String> cmp = new LinkedHashMap<>();
        cmp.put("RDB", "恢复快、文件小；可能丢几分钟数据");
        cmp.put("AOF", "数据安全；文件大、恢复慢");
        cmp.put("混合持久化", "RDB 打底 + AOF 增量，生产推荐");
        cmp.forEach((k, v) -> System.out.printf("  %-10s %s%n", k, v));
        System.out.println();
        System.out.println("  推荐配置：appendonly yes / appendfsync everysec / aof-use-rdb-preamble yes");
    }
}
```

**课后练习**
1. 分别说明 RDB 的 `save` 和 `bgsave` 的区别，`bgsave` 为什么能边写边拍快照（提示：写时复制）。
2. 一个只开 RDB 的 Redis 配置为 `save 300 10`，最坏情况下会丢多少数据？
3. 为什么生产环境推荐混合持久化而不是单纯 AOF？从恢复速度和数据安全两个角度回答。

### 25-05 事务、Lua与管道

**知识点**
- Redis 事务用 `MULTI` / `EXEC` / `DISCARD` / `WATCH`：命令入队后一次性顺序执行，中间不会插入其它客户端的命令。
- 事务**不支持回滚**：某条命令语法错误，整个事务在入队阶段就报错；如果是运行时错误（如对 String 执行 `LPUSH`），只有那条命令失败，其余照常执行。这一点和 MySQL 事务完全不同。
- `WATCH key` 实现乐观锁：EXEC 前如果 key 被别人改过，整个事务放弃执行，返回 nil。
- Lua 脚本：`EVAL` 执行，脚本内所有命令原子执行，是 Redis 里做"复合原子操作"的标准手段（如"判断库存再扣减"）。
- 管道（Pipeline）：客户端把多条命令一次性发出、一次性读回，减少 RTT；它**不保证原子性**，只是省网络往返。
- 选择：需要原子性用 Lua（推荐）或事务；只是批量发送省 RTT 用 Pipeline；跨 key 强一致且要回滚，Redis 做不到，得上数据库事务或分布式事务方案。
- 易错：在事务里做 `GET` 拿结果再判断——入队阶段拿不到返回值，只能靠 `WATCH`；Lua 脚本里执行耗时命令会阻塞整个 Redis。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TransactionAndLua {
    public static void main(String[] args) {
        System.out.println("=== MULTI / EXEC：命令入队后一起执行 ===");
        List<String> queue = new ArrayList<>();
        queue.add("SET balance 100");
        queue.add("DECRBY balance 30");
        queue.add("INCRBY frozen 30");
        System.out.println("  MULTI -> OK，后续命令进入队列：");
        queue.forEach(s -> System.out.println("    QUEUED " + s));
        System.out.println("  EXEC -> 依次执行 " + queue.size() + " 条，返回各自结果");

        System.out.println();
        System.out.println("=== 事务不回滚：运行时错误只影响那一条 ===");
        Map<String, String> store = new LinkedHashMap<>();
        store.put("name", "张三");       // String 类型
        store.put("count", "10");
        String[] cmds = {"SET count 11", "LPUSH name x", "SET count 12"};
        for (String cmd : cmds) {
            if (cmd.startsWith("LPUSH name")) {
                System.out.println("  " + cmd + " -> (error) WRONGTYPE，但事务继续");
            } else {
                String[] p = cmd.split(" ");
                store.put(p[1], p[2]);
                System.out.println("  " + cmd + " -> OK");
            }
        }
        System.out.println("  最终 count = " + store.get("count") + "（说明后面的命令照样执行了）");

        System.out.println();
        System.out.println("=== WATCH：乐观锁 ===");
        int version = 1;
        System.out.println("  WATCH stock  -> 记录版本 " + version);
        System.out.println("  另一个客户端把 stock 改了，版本变成 2");
        version = 2;
        System.out.println("  EXEC -> 检测到版本变化，事务放弃，返回 nil，需要重试");

        System.out.println();
        System.out.println("=== Lua：库存判断 + 扣减，原子完成 ===");
        int stock = 1;
        System.out.println("  脚本：if redis.call('GET', KEYS[1]) >= ARGV[1] then DECRBY ... end");
        System.out.println("  第 1 个请求：库存 " + stock + " >= 1 -> 扣减成功，剩余 " + (--stock));
        System.out.println("  第 2 个请求：库存 " + stock + " < 1  -> 直接返回 0，不会扣成负数");
        System.out.println("  两条命令在脚本内原子执行，不会出现超卖");

        System.out.println();
        System.out.println("=== Pipeline：省 RTT，不保证原子 ===");
        int rtt = 1;   // 假设一次往返 1ms
        int n = 100;
        System.out.println("  逐条发送 " + n + " 次：" + (n * rtt) + " ms");
        System.out.println("  Pipeline 一次发送：" + rtt + " ms（服务端仍逐条执行，中间可插入别的命令）");
        System.out.println();
        System.out.println("  结论：要原子用 Lua；要吞吐用 Pipeline；要回滚请回数据库。");
    }
}
```

**课后练习**
1. 用 Lua 实现"秒杀扣库存"：库存不足返回 0，成功返回剩余库存，并说明为什么不能用 `GET` + `DECRBY` 两条命令。
2. Redis 事务和 MySQL 事务在"回滚"上的差别是什么？举一个因为误以为能回滚而出事故的场景。
3. 什么情况下 Pipeline 会让内存暴涨？如何控制每批的命令数量？

### 25-06 主从复制、哨兵与集群

**知识点**
- **主从复制**：从节点 `replicaof 主节点IP 端口` 后全量同步（RDB）+ 增量同步（replication backlog 环形缓冲）。主写从读，分摊读压力；主节点挂了需要人工切换。
- **哨兵（Sentinel）**：独立进程监控主从，主观下线 + 客观下线（多数哨兵认定）后自动选一个从节点升主，并通知客户端新地址。解决"自动故障转移"。
- **Cluster 集群**：数据分片，16384 个槽位均分到多个主节点，每个主节点带从节点；客户端按 key 的 CRC16 取模路由。解决"单机内存与写能力上限"。
- 集群限制：多 key 操作要求 key 在同一槽（用 `{tag}` 哈希标签强制同槽）；不支持多数据库（只有 db0）；事务/Lua 只能操作同槽 key。
- 选型：数据量小于单机内存、只要高可用 -> 主从 + 哨兵；数据量大或写入压力大 -> Cluster。
- 易错：哨兵本身也要部署 3 个以上且奇数；集群至少 3 主 3 从；客户端必须支持集群协议，普通 Jedis 单机连接连不上集群。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ClusterTopology {
    public static void main(String[] args) {
        System.out.println("=== 主从复制：读写分离 ===");
        System.out.println("  主节点 6379  <-- 写请求");
        System.out.println("    ├── 从节点 6380  <-- 读请求");
        System.out.println("    └── 从节点 6381  <-- 读请求");
        System.out.println("  同步流程：全量 RDB 传输 -> 增量命令传播 -> 心跳维持");
        System.out.println("  缺点：主节点挂了不会自动切换，需要人工 promote");

        System.out.println();
        System.out.println("=== 哨兵：自动故障转移 ===");
        List<String> steps = List.of(
                "哨兵每秒 PING 主从节点",
                "超时未响应 -> 标记主观下线（sdown）",
                "超过 quorum 个哨兵都认定 -> 客观下线（odown）",
                "哨兵之间选出一个 Leader 执行故障转移",
                "从节点中选数据最新的升为主节点",
                "通知其余从节点复制新主，通知客户端新地址");
        for (int i = 0; i < steps.size(); i++) {
            System.out.println("  " + (i + 1) + ". " + steps.get(i));
        }
        System.out.println("  部署建议：3 个哨兵、奇数个，且不要和主节点同机");

        System.out.println();
        System.out.println("=== Cluster：16384 个槽位分片 ===");
        Map<String, String> slots = new LinkedHashMap<>();
        slots.put("主节点A", "0 - 5460");
        slots.put("主节点B", "5461 - 10922");
        slots.put("主节点C", "10923 - 16383");
        slots.forEach((k, v) -> System.out.printf("  %-8s 槽位 %s%n", k, v));
        System.out.println();
        System.out.println("  key 路由：slot = CRC16(key) % 16384");
        String[] keys = {"user:1", "user:2", "order:100"};
        for (String k : keys) {
            int slot = Math.abs(k.hashCode() * 31) % 16384;
            String owner = slot <= 5460 ? "主节点A" : slot <= 10922 ? "主节点B" : "主节点C";
            System.out.printf("    %-10s -> slot %-5d -> %s%n", k, slot, owner);
        }

        System.out.println();
        System.out.println("=== 多 key 操作要同槽：用 {} 哈希标签 ===");
        String[] bad = {"user:1:name", "user:1:age"};
        String[] good = {"{user:1}:name", "{user:1}:age"};
        System.out.println("  不同槽 -> MGET 报 CROSSSLOT 错误：" + List.of(bad));
        System.out.println("  同一槽 -> 正常执行：" + List.of(good));
        System.out.println("  原理：只对 {} 里的内容做 CRC16，两者算出同一个槽");
    }
}
```

**课后练习**
1. 画出一个 3 主 3 从的 Cluster 拓扑，说明任意一个主节点宕机后，集群还能否对外服务。
2. 为什么 Cluster 要求多 key 操作在同一槽？举一个实际业务里会踩到的例子（如购物车批量查询）。
3. 哨兵的 quorum 和 majority 有什么区别？quorum 设为 2、部署 3 个哨兵时，能容忍几个哨兵挂掉？

### 25-07 Spring Data Redis

**知识点**
- 依赖：`spring-boot-starter-data-redis`，底层默认客户端是 Lettuce（基于 Netty，线程安全、支持集群）。
- 核心 API：`RedisTemplate`（对象操作，需配置序列化器）、`StringRedisTemplate`（key/value 都是 String，最常用）。
- 序列化器很关键：默认 `JdkSerializationRedisSerializer` 会把 key 写成带类名的乱码；生产一般配 `StringRedisSerializer` 做 key、`GenericJackson2JsonRedisSerializer` 做 value。
- 常用操作：`opsForValue()` / `opsForHash()` / `opsForList()` / `opsForSet()` / `opsForZSet()`，命名与 Redis 命令一一对应。
- 配置：`spring.data.redis.host/port/password/database/timeout`，连接池用 `lettuce.pool.*`。
- 缓存抽象：`@EnableCaching` + `@Cacheable/@CachePut/@CacheEvict`，用 `RedisCacheManager` 指定 TTL 与 key 前缀，避免手写 `get/set` 样板代码。
- 易错：`RedisTemplate` 直接注入后不配序列化器，用 `redis-cli` 看 key 是一串 `\xac\xed`；`@Cacheable` 同类内部调用不生效（代理失效，同 AOP 原理）。

**示例代码**
```java
import java.util.LinkedHashMap;
import java.util.Map;

public class SpringDataRedisDemo {
    public static void main(String[] args) {
        System.out.println("=== 依赖与配置 ===");
        System.out.println("""
                <dependency>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-starter-data-redis</artifactId>
                </dependency>

                spring:
                  data:
                    redis:
                      host: 127.0.0.1
                      port: 6379
                      password: 123456
                      database: 0
                      lettuce:
                        pool:
                          max-active: 8
                          max-idle: 8
                """);

        System.out.println("=== 序列化器配置 ===");
        System.out.println("""
                @Bean
                public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
                    RedisTemplate<String, Object> tpl = new RedisTemplate<>();
                    tpl.setConnectionFactory(factory);
                    tpl.setKeySerializer(new StringRedisSerializer());
                    tpl.setHashKeySerializer(new StringRedisSerializer());
                    tpl.setValueSerializer(new GenericJackson2JsonRedisSerializer());
                    tpl.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
                    return tpl;
                }
                """);

        System.out.println("=== 五种类型的 API 对照 ===");
        Map<String, String> api = new LinkedHashMap<>();
        api.put("String", "opsForValue().set(k, v, 10, TimeUnit.MINUTES) / get(k) / increment(k)");
        api.put("Hash", "opsForHash().put(k, field, v) / entries(k) / delete(k, field)");
        api.put("List", "opsForList().leftPush(k, v) / range(k, 0, -1) / rightPop(k)");
        api.put("Set", "opsForSet().add(k, v...) / members(k) / intersect(k1, k2)");
        api.put("ZSet", "opsForZSet().add(k, v, score) / reverseRangeWithScores(k, 0, 9)");
        api.forEach((k, v) -> System.out.printf("  %-8s %s%n", k, v));

        System.out.println();
        System.out.println("=== 手写缓存 vs @Cacheable ===");
        System.out.println("  手写：");
        System.out.println("""
                    public Item getItem(Long id) {
                        String key = "item:" + id;
                        Object cached = redisTemplate.opsForValue().get(key);
                        if (cached != null) {
                            return (Item) cached;
                        }
                        Item item = itemMapper.selectById(id);
                        redisTemplate.opsForValue().set(key, item, 30, TimeUnit.MINUTES);
                        return item;
                    }
                """);
        System.out.println("  注解：");
        System.out.println("""
                    @Cacheable(value = "item", key = "#id")
                    public Item getItem(Long id) {
                        return itemMapper.selectById(id);
                    }
                """);
        System.out.println("  -> 缓存逻辑由 AOP 代理织入，业务代码只剩查库");

        System.out.println();
        System.out.println("=== 一次完整调用（模拟）===");
        Map<String, String> cache = new LinkedHashMap<>();
        long id = 10086L;
        String key = "item:" + id;
        System.out.println("  第 1 次请求：" + key + " 未命中 -> 查库 -> 回写缓存 TTL=30min");
        cache.put(key, "{\"id\":10086,\"name\":\"机械键盘\",\"price\":399}");
        System.out.println("  第 2 次请求：" + key + " 命中 -> 直接返回 " + cache.get(key));
        System.out.println("  更新商品后：@CacheEvict(value=\"item\", key=\"#id\") 删除缓存，下次重新加载");
        cache.remove(key);
        System.out.println("  删除后缓存大小 = " + cache.size());
    }
}
```

**课后练习**
1. 为什么推荐 key 用 `StringRedisSerializer`？不配序列化器时 `redis-cli` 里看到的 key 长什么样？
2. 用 `RedisTemplate` 实现"阅读量 +1 并返回新值"，说明为什么必须用 `increment` 而不是 `get` 后 `set`。
3. `@Cacheable` 加在同一个类的两个方法上，A 调用 B 时 B 的缓存为什么不生效？怎么改？

### 25-08 缓存的读写模式

**知识点**
- **Cache-Aside（旁路缓存）**：读时先查缓存、未命中查库并回写；写时**先更新数据库，再删除缓存**。最常用。
- 为什么是"删除"而不是"更新"缓存：并发写两个请求时，更新缓存可能因执行顺序错乱留下旧值；删除是幂等的，下次读自然回源。
- 为什么"先库后缓存"：先删缓存再更新库，在删除与更新之间若有读请求，会把旧值重新写回缓存，且长期不失效（经典不一致）。
- **延迟双删**：更新库后删一次缓存，再延迟 500ms 左右删第二次，覆盖"读请求把旧值回写"的时间窗。
- **Read-Through / Write-Through**：缓存层自己负责回源和落库，业务只跟缓存打交道（Spring Cache 的 `@Cacheable` 接近 Read-Through）。
- **Write-Behind**：先写缓存，异步批量刷库，吞吐高但可能丢数据，适合计数、日志类。
- 强一致要求高的场景（如账户余额）不要靠缓存兜底，直接读库或用分布式锁串行化。
- 易错：给缓存设了超长 TTL 又不主动删除，数据库改了缓存还是旧值；删缓存失败没有补偿（重试/消息队列/binlog 订阅）。

**示例代码**
```java
import java.util.LinkedHashMap;
import java.util.Map;

public class CachePatterns {
    // 模拟数据库
    static final Map<Long, String> DB = new LinkedHashMap<>();
    // 模拟缓存
    static final Map<Long, String> CACHE = new LinkedHashMap<>();

    public static void main(String[] args) {
        DB.put(1L, "机械键盘 v1");
        DB.put(2L, "无线鼠标 v1");

        System.out.println("=== Cache-Aside 读流程 ===");
        System.out.println("  read(1) -> " + read(1L) + "（未命中，回源并回写）");
        System.out.println("  read(1) -> " + read(1L) + "（命中，直接返回）");
        System.out.println("  缓存内容 = " + CACHE);

        System.out.println();
        System.out.println("=== 写流程：先更新库，再删缓存 ===");
        DB.put(1L, "机械键盘 v2");
        CACHE.remove(1L);            // 删除，而不是 update
        System.out.println("  更新数据库为 v2，删除缓存 key=1");
        System.out.println("  read(1) -> " + read(1L) + "（重新回源拿到 v2）");

        System.out.println();
        System.out.println("=== 反面教材：先删缓存，再更新库 ===");
        CACHE.remove(2L);
        System.out.println("  T1 删除缓存 key=2");
        System.out.println("  T2 读请求未命中，查到旧值 v1，回写缓存");
        CACHE.put(2L, DB.get(2L));
        DB.put(2L, "无线鼠标 v2");
        System.out.println("  T1 更新数据库为 v2");
        System.out.println("  -> 缓存里还是 v1，且不会自动失效，长期不一致");

        System.out.println();
        System.out.println("=== 延迟双删补救 ===");
        DB.put(2L, "无线鼠标 v3");
        CACHE.remove(2L);
        System.out.println("  第 1 次删除");
        CACHE.put(2L, "无线鼠标 v2");   // 模拟并发读把旧值写回
        System.out.println("  模拟并发读回写了旧值");
        CACHE.remove(2L);
        System.out.println("  延迟 500ms 后第 2 次删除 -> 缓存干净，下次读到 v3");

        System.out.println();
        System.out.println("=== 四种模式对比 ===");
        Map<String, String> modes = new LinkedHashMap<>();
        modes.put("Cache-Aside", "业务自己管缓存，最灵活，最常用");
        modes.put("Read/Write-Through", "缓存层代理回源与落库，业务无感");
        modes.put("Write-Behind", "异步刷库，吞吐最高，可能丢数据");
        modes.put("双写", "写库同时写缓存，一致性差，不推荐");
        modes.forEach((k, v) -> System.out.printf("  %-20s %s%n", k, v));
    }

    static String read(Long id) {
        String v = CACHE.get(id);
        if (v != null) {
            return v + "（缓存命中）";
        }
        v = DB.get(id);
        CACHE.put(id, v);
        return v + "（回源）";
    }
}
```

**课后练习**
1. 详细说明"先更新数据库再删除缓存"为什么优于"先删除缓存再更新数据库"，画时序图说明并发读导致不一致的过程。
2. 延迟双删的延迟时间怎么定？延迟太短和太长分别有什么问题？
3. 一个秒杀商品的库存，适合用 Cache-Aside 吗？说明理由并给出替代方案。

### 25-09 缓存穿透、击穿、雪崩

**知识点**
- **穿透**：查询一个**数据库里也不存在**的数据（如 id=-1），缓存永远不命中，每次请求都打到数据库。恶意攻击常用这招。
  - 解法一：缓存空值，TTL 设短（如 60s），防止大量无效 key 占内存。
  - 解法二：布隆过滤器，启动时把存在的 id 放进过滤器，请求先过一遍，不存在直接返回。
  - 解法三：参数校验，id <= 0 直接拒绝；接口限流。
- **击穿**：某个**热点 key 突然过期**，瞬间大量并发全部回源，把数据库打崩。
  - 解法一：互斥锁（只让一个线程回源，其余等待）。
  - 解法二：逻辑过期，value 里存过期时间，不设 Redis TTL；发现逻辑过期后异步重建，其余请求先返回旧值。
  - 解法三：热点 key 永不过期 + 后台定时刷新。
- **雪崩**：**大量 key 同时过期**或 **Redis 集群整体宕机**，请求全部压到数据库。
  - 解法一：过期时间加随机值（如 30min + random(0~5min)），打散过期时间点。
  - 解法二：多级缓存（本地 Caffeine + Redis），Redis 挂了本地还能挡一层。
  - 解法三：限流降级、熔断（Sentinel/Resilience4j），保护数据库。
  - 解法四：Redis 高可用（主从 + 哨兵/集群），避免单点。
- 三者的区别记法：穿透是"查不存在的数据"，击穿是"单个热点 key 失效"，雪崩是"大批 key 同时失效或缓存整体不可用"。

**示例代码**
```java
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

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
```

**课后练习**
1. 用一句话区分缓存穿透、击穿、雪崩，并各给出两种解决方案。
2. 布隆过滤器为什么会有误判？"说不存在一定不存在，说存在可能不存在"这句话怎么理解？
3. 设计一个"首页楼层"接口的缓存方案，要求 Redis 整体宕机时页面仍能返回（提示：多级缓存 + 降级）。

### 25-10 分布式锁

**知识点**
- 为什么需要：单体应用里 `synchronized` 能锁住，但服务部署了 3 个实例后，锁只在各自 JVM 内有效，超卖照样发生。
- 基本要求：互斥（同一时刻只有一个持有者）、防死锁（必须有过期时间）、防误删（只能删自己的锁）、可重入（可选）、高可用。
- 正确加锁：`SET lock:order:1001 <唯一标识> NX PX 30000` —— `NX` 保证只有 key 不存在时才设置，`PX` 保证一定过期。
- 正确解锁：必须用 Lua 脚本"判断 value 是不是自己的，是才删除"，避免 A 的锁超时后被 B 拿到、A 醒来把 B 的锁删了。
- 锁续期（看门狗）：业务执行超过锁过期时间时，后台线程定期续期。Redisson 的 `RLock` 内置这套机制。
- 生产建议直接用 Redisson：`RLock lock = redisson.getLock("order:1001"); lock.lock(); try { ... } finally { lock.unlock(); }`，它处理了可重入、续期、Pub/Sub 唤醒。
- 局限：Redis 主从异步复制，主节点加锁成功后未同步就宕机，从节点升主后锁丢失。强一致场景要用 RedLock 或 ZooKeeper/etcd。
- 易错：`SETNX` + `EXPIRE` 两条命令不是原子的（中间宕机就永久锁死）；解锁不校验持有者；锁的粒度太大导致吞吐骤降。

**示例代码**
```java
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class DistributedLock {
    // 模拟 Redis：key -> 持有者标识
    static final Map<String, String> REDIS = new ConcurrentHashMap<>();
    static int stock = 10;
    static final AtomicInteger SOLD = new AtomicInteger();

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== 无锁：多实例并发扣库存 ===");
        stock = 10;
        SOLD.set(0);
        Thread[] ts = new Thread[20];
        for (int i = 0; i < ts.length; i++) {
            ts[i] = new Thread(DistributedLock::sellWithoutLock);
        }
        for (Thread t : ts) {
            t.start();
        }
        for (Thread t : ts) {
            t.join();
        }
        System.out.println("  卖出 " + SOLD.get() + " 件，剩余 " + stock + " 件（超卖了）");

        System.out.println();
        System.out.println("=== 有锁：SET NX PX + Lua 解锁 ===");
        stock = 10;
        SOLD.set(0);
        REDIS.clear();
        Thread[] ts2 = new Thread[20];
        for (int i = 0; i < ts2.length; i++) {
            final int no = i;
            ts2[i] = new Thread(() -> sellWithLock("instance-" + no));
        }
        for (Thread t : ts2) {
            t.start();
        }
        for (Thread t : ts2) {
            t.join();
        }
        System.out.println("  卖出 " + SOLD.get() + " 件，剩余 " + stock + " 件（正确）");

        System.out.println();
        System.out.println("=== 加锁命令 ===");
        System.out.println("  SET lock:stock 8f3a-uuid NX PX 30000");
        System.out.println("  NX = 不存在才设置（互斥）；PX = 30 秒后自动过期（防死锁）");

        System.out.println();
        System.out.println("=== 解锁必须用 Lua 校验持有者 ===");
        System.out.println("""
                  if redis.call('GET', KEYS[1]) == ARGV[1] then
                      return redis.call('DEL', KEYS[1])
                  else
                      return 0
                  end
                """);
        System.out.println("  否则：A 超时释放 -> B 拿到锁 -> A 执行完把 B 的锁删了");

        System.out.println();
        System.out.println("=== 看门狗续期 ===");
        System.out.println("  业务耗时 40s > 锁 30s，Redisson 后台每 10s 把过期时间重置为 30s");
        System.out.println("  释放锁后停止续期，避免死锁");
    }

    static void sellWithoutLock() {
        if (stock > 0) {
            sleep(1);                      // 模拟检查与扣减之间的时间窗
            stock--;
            SOLD.incrementAndGet();
        }
    }

    static void sellWithLock(String owner) {
        String key = "lock:stock";
        // 模拟 SET key owner NX PX 30000
        if (REDIS.putIfAbsent(key, owner) != null) {
            return;                        // 没抢到锁，直接放弃（真实场景可重试）
        }
        try {
            if (stock > 0) {
                sleep(1);
                stock--;
                SOLD.incrementAndGet();
            }
        } finally {
            // 模拟 Lua：只有持有者才能删
            REDIS.remove(key, owner);
        }
    }

    static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
```

**课后练习**
1. 为什么 `SETNX` 之后再 `EXPIRE` 是不安全的？写出正确的一条命令。
2. 描述"锁超时后误删别人锁"的完整时序，并写出用 Lua 解决的脚本。
3. Redisson 的看门狗在什么情况下会失效（比如进程被 kill -9）？此时锁会怎样？

### 25-11 综合案例：商品详情缓存

**知识点**
- 完整链路：接口层校验参数 -> 查缓存 -> 命中返回 -> 未命中加互斥锁 -> 双重检查 -> 查库 -> 回写缓存（带随机 TTL）-> 返回。
- 缓存对象设计：`item:{id}:detail` 存 JSON（String 类型），`item:{id}:stock` 单独存库存（Hash 或 String），因为库存变化频率远高于详情。
- 更新策略：改商品 -> 更新库 -> 删 `detail` 缓存（库存走单独的实时读写或消息驱动）。
- 空值保护：查库为 null 时缓存 `"NULL"` 字符串 60 秒，配合参数校验挡住 `id <= 0`。
- 可观测：记录命中率、回源次数、锁等待次数，命中率低于阈值要告警（可能是 key 设计或 TTL 有问题）。
- 代码组织：缓存逻辑抽到 `ItemCacheService`，Controller 只负责参数与响应，Service 负责业务与缓存，Mapper 只碰数据库。
- 易错：缓存与数据库在同一个方法里用同一个事务，事务未提交就回写缓存，其它请求可能读到旧值并缓存下来（应 `TransactionSynchronization` 在提交后删缓存）。

**示例代码**
```java
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

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
```

**课后练习**
1. 把本案例改造成"库存单独缓存"：`item:{id}:stock` 用 `DECRBY` 扣减，说明详情缓存与库存缓存为什么要分开。
2. 为什么回写缓存要放在事务提交之后？写出用 `TransactionSynchronizationManager.registerSynchronization` 的代码骨架。
3. 给这个案例加上"命中率统计 + 低于 80% 告警"的逻辑，说明还需要采集哪些指标。
