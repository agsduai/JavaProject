### 45-05 ZooKeeper 与 ZAB

**知识点**
- **ZooKeeper 的定位是"协调服务"而不是"数据库"**：它提供分层命名空间（znode 树）、 watches（变更通知）、临时节点（会话绑定的存活性信号）、顺序节点（全局递增编号）——**用这四样积木拼出选主、分布式锁、配置订阅、队列**。存数据是次要能力（单 znode 默认 1MB 上限）。
- **ZAB 协议 = Raft 的近亲**：同样是过半投票选主 + 日志复制（ZAB 叫"崩溃恢复与消息广播"两阶段）。理解了 Raft（45-03/04）就理解了 ZK 的可用性边界：**3~5 节点、写走 leader、过半确认**。
- **临时节点是"存活性"的原语**：客户端 session 断开（心跳超时）时其临时节点自动删除——**选主的经典实现**：所有候选者创建 `/master` 临时节点，创建成功者为主，其余 watch 该节点，节点消失即触发重新竞选。**它比心跳探测可靠**：session 失效由 ZK 集群裁决（共识），不是客户端自己的观察（45-01 脑裂的反例）。
- **顺序节点是"公平锁与队列"的原语**：`/lock/lock-0000012` 全局递增，拿最小编号者持锁、其余 watch 前一个编号——避免惊群，保证公平。**这是分布式锁的标准姿势**（45-08 的 Redis 方案对比）。
- **Watch 是一次性触发**：收到通知后要重新注册（或用 Curator 缓存封装），**watch 通知不保证送达内容**，只保证"有变化"——读取最新值必须重查。
- **ZK 的运维红线**：写吞吐低（全集群过半确认，每秒几千写已是极限）；session 超时调太短（GC 停顿就掉 session -> 临时节点消失 -> 选主抖动，29 章 Nacos 为什么最终不用 ZK 做注册中心的原因之一）；**不要把 ZK 当消息队列或大数据存储用**。
- 易错点：用非临时节点做选主（进程挂了节点还在，双主）；watch 只注册一次（丢后续变更）；把大数据量塞 znode（写放大拖垮集群）；session 超时与业务 GC 停顿不匹配（频繁重连风暴）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.List;

public class ZookeeperConcepts {
    record Znode(String path, String data, boolean ephemeral, int seq) {
    }

    static void leaderElection() {
        System.out.println("【选主：临时节点 + watch】");
        List<Znode> znodes = List.of(
                new Znode("/election/n-0000011", "hostA", true, 11),
                new Znode("/election/n-0000012", "hostB", true, 12));
        Znode master = znodes.stream()
                .min((a, b) -> Integer.compare(a.seq(), b.seq())).orElse(null);
        System.out.println("  三个候选者都创建顺序临时节点，编号最小者为主：");
        System.out.printf("  主 = %s（%s）%n", master.path(), master.data());
        System.out.println("  hostB watch /election/n-0000011");
        System.out.println("  hostA session 断开 -> 临时节点自动删除 -> hostB 收到通知 ->");
        System.out.println("       重查编号 -> 自己最小 -> 当选");
        System.out.println("  存活性由 ZK 集群（共识）裁决，而非节点自我观察 —— 无脑裂");
        System.out.println();
    }

    static void fairLock() {
        System.out.println("【公平锁：顺序节点编号排队】");
        List<String> holders = List.of("/lock/lock-0000012(A)", "/lock/lock-0000013(B)",
                "/lock/lock-0000014(C)");
        String acquired = holders.get(0);
        System.out.println("  排队状态：" + holders);
        System.out.printf("  A（编号最小）持锁；B watch 0012，C watch 0013%n");
        System.out.println("  A 释放（删节点）-> B 收到通知 -> 自己成为最小 -> 持锁");
        System.out.println("  对比单 watch /lock：锁释放时所有等待者同时醒来抢（惊群）");
        System.out.println("  对照 45-08 Redis 锁：ZK 锁自带公平性与存活性，代价是每次操作走共识");
        System.out.println();
    }

    static void watchSemantics() {
        System.out.println("【Watch：一次性触发，必须重注册】");
        List<String> flow = List.of(
                "1) hostB getData(/config, watch=true) 得到值 v5",
                "2) /config 变为 v6 -> hostB 收到通知（不含新值！）",
                "3) hostB 重新 getData + 重新注册 watch -> 拿到 v6",
                "4) 若在“收到通知”与“重查”之间又变了 v7 -> 下次通知保证不丢变化");
        for (String s : flow) {
            System.out.println("  " + s);
        }
        System.out.println("  生产实践：用 Curator NodeCache/TreeCache 封装，避免裸 watch 的时序坑");
        System.out.println();
    }

    static void opsRedlines() {
        System.out.println("【ZK 运维红线】");
        List<String> rules = List.of(
                "znode 数据 ≤ 1MB（默认），大配置拆分或走配置中心",
                "session 超时 > 最大 GC 停顿（否则掉线风暴 -> 选主抖动）",
                "写 QPS 有限（过半确认）：高频写场景用本地缓存 + watch 推送",
                "不把 ZK 当注册中心全量心跳（29 章：注册中心对写吞吐的要求超出 ZK 舒适区）",
                "集群 3 或 5 台（quorum），生产禁止单机");
        for (String r : rules) {
            System.out.println("  - " + r);
        }
    }

    public static void main(String[] args) {
        leaderElection();
        fairLock();
        watchSemantics();
        opsRedlines();
    }
}
```

**课后练习**
1. 用临时节点 + 顺序节点实现一个公平分布式锁模拟器（内存版），验证"编号最小者持锁、释放后依次传递"，并构造惊群对照。
2. 解释 GC 停顿 8 秒、session 超时 5 秒的集群会发生什么连锁反应，给出参数修正方案。
3. 思考题：为什么 29 章的注册中心最终用"心跳 + 内存表"而不是 ZK？从写吞吐与会话模型对比注册中心的负载形态。

---

### 45-06 etcd 与线性一致读

**知识点**
- **etcd 是 K8s 的元数据底座，也是 Raft 的参考实现**：与 ZK 定位相似（协调服务），但协议更现代（Raft）、支持 MVCC 多版本与事务（mini-transactions：if-then-else）、**gRPC 端到端**。K8s 的一切对象都存 etcd——它挂了 K8s 只读不写。
- **MVCC 是 etcd 的重要设计**：每次写产生全局递增 revision，键值对保留历史版本——**watch 按 revision 流式消费不漏事件**（对比 ZK watch 的一次性触发），**compact 修剪旧版本**（不压缩历史会无限膨胀）。
- **串行读 vs 线性一致读**：串行读（默认）只读本地状态——**便宜但可能读到旧主数据**（分区中旧 leader 的内存状态）；线性一致读走 ReadIndex：leader 向多数派确认自己权威再读（45-04）。**K8s 用 quorum 读保关键路径，监控用串行读省代价**——分级一致性的实践样本（45-02）。
- **lease 是 etcd 的活性原语**：客户端申请带 TTL 的 lease，key attach 到 lease，续约失败过期则 key 自动删除——**与 ZK 临时节点同构的选主/存活性方案**。
- **事务（txn）让"检查并写入"原子化**：`if value(k)==A then put(k,B) else put(k,C)`——**分布式锁、防并发写、leader 切换保护的实现基座**。没有 txn 时这些要靠外部锁。
- **运维视角**：etcd 对磁盘延迟极敏感（fsync 是写路径关键段，磁盘 >10ms 延迟会导致心跳超时换届）——**专用 SSD + 隔离部署**是 K8s 生产铁律；compact 与 defrag 要排期。
- 易错点：把 etcd 当通用数据库存业务数据（K8s 元数据专用定位）；不配 compact（revision 无限增长拖垮查询）；串行读用于选主结果判断（读到旧主）；lease TTL 太短（抖动即 key 删除，业务雪崩）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class EtcdLinearRead {
    /** MVCC：revision -> (key -> value) */
    static TreeMap<Integer, Map<String, String>> history = new TreeMap<>();
    static int rev = 0;

    static void put(String key, String value) {
        rev++;
        history.computeIfAbsent(rev, k -> new java.util.LinkedHashMap<>()).put(key, value);
        System.out.printf("  put %s=%s -> revision=%d%n", key, value, rev);
    }

    static void mvccWatch() {
        System.out.println("【MVCC：watch 按 revision 流式消费不漏事件】");
        put("config/timeout", "30");
        put("config/timeout", "60");
        put("config/retries", "3");
        System.out.println("  watch 从 revision=1 开始回放：");
        for (Map.Entry<Integer, Map<String, String>> e : history.entrySet()) {
            System.out.printf("    rev=%d %s%n", e.getKey(), e.getValue());
        }
        System.out.println("  断线重连：从上次收到的 revision 续看 —— 不像 ZK watch 会丢中间变化");
        System.out.println("  compact(revision=2)：旧版本回收，watch 起点 <3 的客户端报错需全量同步");
        System.out.println();
    }

    static void txnAtomic() {
        System.out.println("【txn：检查并写入的原子化】");
        String stored = history.get(rev).get("config/timeout");
        boolean match = "60".equals(stored);
        System.out.printf("  txn: if config/timeout==60 then put(90) else put(read_current)%n");
        System.out.printf("  当前值=%s -> %s%n", stored,
                match ? "条件成立 -> 写入 90" : "条件不成立 -> 走 else 分支");
        if (match) {
            put("config/timeout", "90");
        }
        System.out.println("  用途：leader 切换保护、防并发写、锁的实现基座");
        System.out.println();
    }

    static void readModes() {
        System.out.println("【两种读模式：K8s 的分级实践】");
        String[][] rows = {
                {"串行读(默认)", "本地状态，0 RTT", "可能读到旧主数据", "监控/大盘"},
                {"线性一致读", "ReadIndex 过半确认，+1 RTT", "权威值", "选主判断/关键写前置"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-10s %-26s %-16s %s%n", r[0], r[1], r[2], r[3]);
        }
        System.out.println("  铁律：判断“谁是 leader”必须线性一致读，否则可能基于旧主做决策");
        System.out.println();
    }

    static void opsNotes() {
        System.out.println("【磁盘延迟是 etcd 的命门】");
        List<String> notes = List.of(
                "写路径关键段 = fsync：磁盘 >10ms -> 心跳超时 -> 反复换届",
                "生产铁律：专用 SSD/NVMe，与业务负载隔离部署",
                "compact 排期（保留 N 万 revision）；defrag 回收碎片空间",
                "监控：commit 耗时 P99、慢 fsync 告警（42 章方法论）");
        for (String n : notes) {
            System.out.println("  - " + n);
        }
    }

    public static void main(String[] args) {
        mvccWatch();
        txnAtomic();
        readModes();
        opsNotes();
    }
}
```

**课后练习**
1. 实现带 revision 的 MVCC 键值存储模拟器：支持按 revision 回放 watch 与 compact，验证断线重连不漏事件。
2. 用 txn 实现"防并发写"：两个客户端同时尝试 `if value==old then put(new)`，验证只有一个成功。
3. 思考题：为什么 etcd 对磁盘延迟比对网络延迟更敏感？从 Raft 落盘与心跳超时的关系推导。

---

### 45-07 分布式时钟与顺序

**知识点**
- **分布式系统的顺序不能靠物理时钟**：两台机器的时钟各有漂移（ppm 级），NTP 校准有毫秒级误差且可能回拨——**用时间戳排序事件 = 依赖一个不可靠的全局参照**（43-02 事件时间的教训在元数据层的同款）。
- **时钟回拨的破坏力**：39-07 雪花 ID 拒绝回拨保 ID 唯一；45-03 任期号绝不用时钟保顺序；43-02 服务端打点但容忍乱序——**共识系统直接放弃物理时钟做顺序**。
- **逻辑时钟（Lamport Clock）**：每个事件带 (counter, nodeId)，事件发生 counter++，收到消息 counter=max(local, remote)+1——**保证因果序**：a -> b 则 a 的时钟 < b 的时钟。但它不能区分"并发"与"先后"（时钟相等无法判定谁是因）。
- **向量时钟（Vector Clock）**：每个节点维护全量计数向量，比较向量可判定**因果先后或并发**——Dynamo 类系统用它检测冲突（两个并发写 -> 冲突交给业务合并）。代价：向量长度 = 节点数，扩展性受限。
- **混合时钟（HLC/Hybrid Logical Clock）**：物理时间戳 + 逻辑计数组合——保因果序又接近物理时间（对分桶/限流友好）。CockroachDB/Yugabyte 的选择，也是 43 章窗口聚合与共识世界的桥。
- **外部一致性的真解是原子钟/TrueTime**：Spanner 用 GPS+原子钟给出有界误差的时间（±几 ms），等待误差过去再提交——**用硬件砸出全局顺序**，普通公司不复制，但知道天花板在哪。
- 易错点：用 `System.currentTimeMillis` 排序分布式事件（漂移+回拨双重坑）；把 NTP 精度想成毫秒级（公网 NTP 可能几十 ms 且依赖网络）；逻辑时钟当物理时间用（不能做分桶/限流）；冲突检测缺失直接 last-write-wins（丢数据）。

**示例代码**
```java
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DistributedClocks {
    /** Lamport 逻辑时钟：counter + nodeId */
    static class Lamport {
        long counter = 0;
        final String node;

        Lamport(String node) {
            this.node = node;
        }

        long localEvent() {
            return ++counter;
        }

        long onReceive(long remote) {
            counter = Math.max(counter, remote) + 1;
            return counter;
        }

        String stamp() {
            return "(" + counter + "," + node + ")";
        }
    }

    static void lamportDemo() {
        System.out.println("【Lamport 时钟：跨节点保持因果序】");
        Lamport a = new Lamport("A"), b = new Lamport("B");
        long e1 = a.localEvent();                       // A 本地事件
        long e2 = a.localEvent();
        long sent = e2;                                  // A 发消息
        long e3 = b.onReceive(sent);                     // B 收到消息
        long e4 = b.localEvent();
        System.out.printf("  A: 本地事件 %s -> %s%n", e1, e2);
        System.out.printf("  A 发送消息(携带 %d) -> B 收到 -> %s -> 本地 %s%n", sent, e3, e4);
        System.out.println("  因果链 A.e2 -> B.e3：时钟 2 < 4，因果可判");
        System.out.println("  局限：A.e1(1) 与 B.e3(3) 无因果但时钟可比 —— 并发无法识别");
        System.out.println();
    }

    static void vectorClock() {
        System.out.println("【向量时钟：判别并发写冲突】");
        record VC(Map<String, Long> v) {
            boolean before(VC o) {
                boolean allLE = true, someLT = false;
                for (Map.Entry<String, Long> e : v.entrySet()) {
                    long other = o.v().getOrDefault(e.getKey(), 0L);
                    if (e.getValue() > other) {
                        allLE = false;
                    }
                    if (e.getValue() < other) {
                        someLT = true;
                    }
                }
                return allLE && someLT;
            }

            boolean concurrent(VC o) {
                return !before(o) && !o.before(this);
            }
        }
        Map<String, Long> a1 = new LinkedHashMap<>();
        a1.put("A", 2L);
        a1.put("B", 1L);
        Map<String, Long> b1 = new LinkedHashMap<>();
        b1.put("A", 1L);
        b1.put("B", 2L);
        VC va = new VC(a1), vb = new VC(b1);
        System.out.println("  客户端A 写入版本 " + a1);
        System.out.println("  客户端B 写入版本 " + b1 + "（基于同一旧版本，未见过 A 的写）");
        System.out.printf("  判定：%s%n", va.concurrent(vb) ? "并发 -> 冲突！需合并或业务裁决" : "有因果 -> 取较新者");
        System.out.println("  对照 last-write-wins：按墙钟比较会静默丢掉一方写入");
        System.out.println();
    }

    static void hybridClock() {
        System.out.println("【混合时钟：物理 + 逻辑的折衷】");
        long wallMs = 1763350000000L;
        long logical = 3;
        System.out.printf("  HLC = 物理部分 %d + 逻辑部分 %d%n", wallMs, logical);
        List<String> benefits = List.of(
                "保留物理时间语义：可做时间窗口分桶（43-03 兼容）",
                "同毫秒内用逻辑位排序：解决毫秒内并发",
                "时钟漂移被物理位吸收，回拨只影响逻辑位");
        for (String b : benefits) {
            System.out.println("  - " + b);
        }
        System.out.println("  CockroachDB/Yugabyte 的选择：对外像物理时间，内部保证因果");
    }

    public static void main(String[] args) {
        lamportDemo();
        vectorClock();
        hybridClock();
    }
}
```

**课后练习**
1. 实现 Lamport 时钟的三节点消息交互模拟，验证所有因果对的时钟序，并构造一对"时钟可比但实际并发"的事件。
2. 实现向量时钟冲突检测：两个客户端基于不同副本并发写，输出冲突判定与合并策略建议。
3. 思考题：为什么 45-03 的任期号选择"单调计数"而不是"物理时间"？从回拨与并发两个故障场景推导。

---

### 45-08 租约与分布式锁

**知识点**
- **锁的本质问题是在不可靠网络上的互斥**：持锁者崩溃但锁还在它名下（其他人永远拿不到）——**租约（lease）解法：锁带 TTL，到期自动失效**。TTL 之内持锁者可用，之外系统自动回收。
- **租约的时钟依赖**：TTL 的计时在服务端（共识系统裁决，可靠）；**客户端基于"剩余租约时间"做的乐观假设依赖本地时钟有界偏移**（Lease Read 的理论基础）。时钟漂移大时要么缩短租约要么放弃乐观。
- **Fencing Token 是防脑裂的最后防线**：锁服务每次发放锁都带**单调递增编号**；下游存储只接受编号更大的请求——即使旧持锁者（GC 停顿后醒来）还以为自己在持锁，它的旧编号请求会被拒绝。**没有 fencing 的分布式锁在高 GC 场景必然出事**。
- **GC 停顿的经典事故链**：A 拿锁 -> A 进入 GC 停顿 10s -> 租约过期锁转给 B -> B 修改数据 -> A 醒来以为自己持锁继续写 -> 覆盖 B。**每一步都"合理"，结局是数据错乱**——fencing 编号让 A 的写入被拒。
- **ZK 锁 vs Redis 锁的取舍**：ZK（45-05）自带公平性、存活性、 fencing 语义（顺序节点编号）——但每次加锁走共识，延迟与吞吐受限；Redis 锁（SETNX+TTL）快但**单实例锁在主从切换时会丢**（RedLock 的争议），需要 fencing 兜底。**结论：性能敏感 + 能容忍偶发失效 -> Redis 锁 + fencing；正确性敏感 -> ZK/etcd 锁**。
- **能不用分布式锁就不用**：很多"用锁"的场景其实是"防重复执行"——幂等键（39-09）、数据库唯一约束、乐观版本号往往更简单可靠。**锁是协调成本最高的同步原语，最后才考虑**。
- 易错点：锁不设 TTL（持锁者崩溃死锁）；锁续约失败还继续执行（应先检查租约有效性）；无 fencing（旧持锁者污染新持锁者的数据）；把锁当业务互斥的万能药（幂等能解决的不用锁）。

**示例代码**
```java
import java.util.LinkedHashMap;
import java.util.Map;

public class LeaseFencing {
    /** 锁授权：租约到期时间 + 单调递增 fencing 编号 */
    record LockGrant(String owner, long expiresAt, int fencingToken) {
    }

    /** 锁服务：租约 + fencing token */
    static class LockService {
        static int tokenSeq = 0;
        final Map<String, LockGrant> held = new LinkedHashMap<>();

        LockGrant acquire(String key, String owner, long ttlMs, long now) {
            LockGrant cur = held.get(key);
            if (cur != null && cur.expiresAt() > now) {
                return null;                     // 仍被持有
            }
            LockGrant g = new LockGrant(owner, now + ttlMs, ++tokenSeq);
            held.put(key, g);
            System.out.printf("  %s 获得锁 %s，fencing=%d，TTL=%dms%n",
                    owner, key, g.fencingToken(), ttlMs);
            return g;
        }
    }

    /** 下游存储：只接受更大 fencing 编号 */
    static class FencedStore {
        int maxToken = 0;

        boolean write(int token, String value) {
            if (token <= maxToken) {
                System.out.printf("  拒绝写入 fencing=%d（当前最大 %d）—— 旧持锁者的过期写入%n",
                        token, maxToken);
                return false;
            }
            maxToken = token;
            System.out.printf("  接受写入 fencing=%d value=%s%n", token, value);
            return true;
        }
    }

    static void gcIncident() {
        System.out.println("【GC 停顿事故链：无 fencing 的悲剧】");
        LockService svc = new LockService();
        FencedStore store = new FencedStore();
        long now = 0;
        LockGrant a = svc.acquire("order-lock", "A", 5000, now);
        System.out.println("  A 拿锁进入业务 -> GC 停顿 8 秒...");
        now = 8000;
        LockGrant b = svc.acquire("order-lock", "B", 5000, now);   // A 租约已过期
        store.write(b.fencingToken(), "B 修改: 库存=18");
        System.out.println("  A 醒来，以为自己还持锁：");
        store.write(a.fencingToken(), "A 修改: 库存=25");
        System.out.println("  无 fencing：A 覆盖了 B —— 数据错乱");
        System.out.println("  有 fencing：A 的旧编号写入被拒绝（上面已演示拒绝路径）");
        System.out.println();
    }

    static void fencingDemo() {
        System.out.println("【fencing 生效现场】");
        FencedStore store = new FencedStore();
        store.write(5, "B 的写入");
        store.write(3, "旧持锁者 A 的迟到写入");
        store.write(6, "C 的合法写入");
        System.out.println("  编号单调 + 下游拒绝旧编号 = 过期者无法破坏现状");
        System.out.println();
    }

    static void alternatives() {
        System.out.println("【先问能不能不用锁】");
        String[][] rows = {
                {"防重复执行", "幂等键 + 唯一约束（39-09）", "无锁，天然可靠"},
                {"防并发覆盖", "乐观版本号 CAS（32 章）", "无锁，冲突重试"},
                {"资源配额互斥", "数据库原子 UPDATE ... WHERE 剩余>0", "无锁，行锁内部化"},
                {"真全局互斥（极少数）", "ZK/etcd 锁 + fencing", "有锁，走共识"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-12s %-32s %s%n", r[0], r[1], r[2]);
        }
        System.out.println("  口诀：幂等 > 版本号 > 数据库约束 > 分布式锁");
    }

    public static void main(String[] args) {
        gcIncident();
        fencingDemo();
        alternatives();
    }
}
```

**课后练习**
1. 完整实现租约锁服务（TTL、续约、过期回收）+ FencedStore，重演本章 GC 事故链，验证 fencing 的拦截效果。
2. 把一个你现有的"分布式锁"场景改造成幂等键或乐观版本号方案，对比两者代码量与故障面。
3. 思考题：为什么 Redis 主从切换会"丢锁"？从主从异步复制与锁过期写路径推导，并说明 fencing 如何兜底。
