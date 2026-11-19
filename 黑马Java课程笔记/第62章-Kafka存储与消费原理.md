# 第62章 Kafka 存储与消费原理

第28章讲了 MQ 的使用语义，第43章讲了流处理的时间与状态，本章补上组件内核：Kafka 为什么既快又能保证可靠。它不是"读完即删的队列"，而是"只能追加的分布式日志"——多消费组独立 offset、可重放、顺序追加写。本章按「日志即消息 → 分区副本与选主 → ISR 与 HW/LEO → 段文件与稀疏索引 → 生产者幂等与事务 → 消费组与 Rebalance → Offset 语义与精确一次 → 保留与压缩策略 → 性能五要素 → 积压排查 → 订单事件流案例」的顺序讲。所有示例仍是纯 JDK：按 key 分区保序、ISR/HW 推进、稀疏索引定位、幂等去重、事务未提交不可见、Range/Cooperative 分配、三种提交语义、Lag 诊断全部真实推演。

### 62-01 Kafka 全景：日志即消息

**知识点**
- **Kafka 的本质是"分布式的、只能追加的日志"**：消息追加到分区末尾，消费者按偏移量顺序读——**它不是"队列"（读完即删），而是"日志"（按时间/大小保留）**。这个设计带来三个能力：**多消费组独立消费**（同一份数据被多个业务各自消费）、**重放**（重置 offset 重读历史，43 章可重放源的基础）、**高吞吐**（顺序追加是磁盘最快的写模式）。
- **四个核心组件**：**Broker**（单台服务器）、**Topic**（逻辑分类）、**Partition**（物理分片，**并行与顺序的单位**）、**Replica**（副本，可靠性）。**Topic 是逻辑概念，Partition 才是存储与并发的实体**。
- **Kafka 与 28/43 章的关系**：28 章讲 MQ 的**使用语义**（可靠投递/幂等/死信），43 章讲**流处理的窗口与状态**；本章补**存储与复制的内部机制**——**知道 ISR 与 HW 才能理解"为什么消息会丢/为什么消费到重复"**。
- **顺序性的边界**：Kafka 只保证**单分区内有序**，跨分区无序——**需要全局有序就只能单分区**（吞吐受限）；**业务保序的正确做法是按业务键（订单 id）分区**（39-08 分区键思想）。
- **吞吐的来源**（62-09 细讲）：顺序写 + 页缓存 + 零拷贝 + 批量压缩 + 分区并行——**Kafka 的高吞吐不是魔法，是这五件事的叠加**。
- 易错点：以为 Kafka 是"队列"（读完不删，按保留策略清理）；以为全局有序（只有分区内有序）；Topic 分区数随意定（分区数是并行上限，创建后只能增不能减）；用默认分区策略导致热点（key=null 时轮询，带 key 时哈希）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class KafkaOverview {
    record Partition(int id, List<String> log, long endOffset) {
        Partition append(String record) {
            log.add(record);
            return new Partition(id, log, endOffset + 1);
        }
    }

    /** 按 key 哈希分区：同一 key 恒在同一分区（保序的前提） */
    static int partitionOf(String key, int partitions) {
        return Math.abs(key.hashCode()) % partitions;
    }

    static void appendDemo() {
        System.out.println("【按 key 分区：同一订单的消息进同一分区】");
        Map<Integer, List<String>> byPartition = new LinkedHashMap<>();
        for (int i = 0; i < 3; i++) {
            byPartition.put(i, new ArrayList<>());
        }
        String[][] messages = {
                {"order-9001", "CREATED"}, {"order-9002", "CREATED"},
                {"order-9001", "PAID"}, {"order-9002", "PAID"},
                {"order-9001", "SHIPPED"}};
        for (String[] m : messages) {
            int p = partitionOf(m[0], 3);
            byPartition.get(p).add(m[0] + ":" + m[1]);
        }
        byPartition.forEach((p, log) ->
                System.out.printf("  partition-%d：%s%n", p, log));
        System.out.println("  order-9001 的三条消息在同一分区 -> 分区内有序（消费端按序处理）");
        System.out.println();
    }

    static void replayDemo() {
        System.out.println("【多消费组 + 重放：日志而非队列】");
        List<String> log = List.of("m1", "m2", "m3", "m4");
        Map<String, Long> groupOffsets = new LinkedHashMap<>();
        groupOffsets.put("group-order", 4L);
        groupOffsets.put("group-data", 2L);
        for (Map.Entry<String, Long> e : groupOffsets.entrySet()) {
            List<String> remaining = log.subList(e.getValue().intValue(), log.size());
            System.out.printf("  %-12s offset=%d -> 下次消费 %s%n",
                    e.getKey(), e.getValue(), remaining);
        }
        System.out.println("  同一份日志，两个消费组各自维护 offset（互不影响）");
        System.out.println("  重放：把 offset 重置为 0 即可重读全部历史（43-01 可重放源）");
    }

    public static void main(String[] args) {
        appendDemo();
        replayDemo();
    }
}
```

**课后练习**
1. 实现一个"分区有序性验证"：同一业务键的消息发到同一分区，跨键的消息分散到不同分区，用 100 条消息验证。
2. 用 Kafka（或内存模拟）跑两个消费组消费同一 Topic，验证各自 offset 独立、互不影响。
3. 思考题：为什么 Kafka 不做全局有序？从"单分区吞吐上限"与"分区并行"的取舍分析，并给出"业务保序"的正确做法。

---

### 62-02 分区、副本与 Leader 选举

**知识点**
- **分区是并行的单位，副本是可靠的单位**：每个分区有 **1 个 Leader + N 个 Follower**（`replication.factor`），**生产与消费都只走 Leader**（Kafka 2.4+ 支持 Follower 读，但默认仍走 Leader），Follower 只负责**同步复制**。
- **副本分配要跨 Broker**：同一分区的 Leader 与 Follower 落在不同 Broker（甚至不同机架，`broker.rack`）——**否则 Broker 一挂，分区的主副一起丢**（`replica.assignment` 的机架感知）。
- **Leader 选举不是选举算法**：Kafka 的 Leader 选举由 **Controller**（某个 Broker 兼任）**从 ISR（同步副本集合）中选一个**（通常选第一个）——**它不是 Raft/Paxos（45 章）**，而是"基于 ISR 的指定式选主"，**ISR 为空时分区不可用**（`unclean.leader.election.enable=false` 时）。
- **Controller 的角色**：管理分区 Leader 分配、Broker 上下线、Topic 创建删除——**Controller 挂了会重新竞选**（ZK 时代的 controller epoch / KRaft 时代的元数据仲裁），**Controller 的故障会导致短暂的元数据操作失败**。
- **分区数与并行度**：**消费者数超过分区数时，多余消费者空闲**（62-06 的分配规则）——**分区数是消费并行度的硬上限**（59-04 的分区思想在 Kafka 的对应）。
- 易错点：单副本部署（Broker 挂了数据丢）；副本数与 Broker 数相同但同机架（机架故障全挂）；允不允许 unclean 选举没想清楚（可用性 vs 数据一致性）；分区数创建后想减少（不可能，只能建新 Topic 迁移）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PartitionReplica {
    record Replica(String broker, String rack, boolean inSync, long leo) {
    }

    record Partition(int id, List<Replica> replicas, String leader) {
        List<String> isr() {
            List<String> out = new ArrayList<>();
            for (Replica r : replicas) {
                if (r.inSync()) {
                    out.add(r.broker());
                }
            }
            return out;
        }

        /** 从 ISR 选 Leader（Controller 语义：选第一个 ISR） */
        String electLeader(boolean allowUnclean) {
            List<String> candidates = isr();
            if (!candidates.isEmpty()) {
                return candidates.get(0);
            }
            if (allowUnclean) {
                return replicas.get(0).broker();
            }
            return null;                                   // 不可用
        }
    }

    static void faultTolerance() {
        System.out.println("【副本与故障容忍】");
        Partition p = new Partition(0, List.of(
                new Replica("broker-1", "rack-a", true, 100),
                new Replica("broker-2", "rack-b", true, 99),
                new Replica("broker-3", "rack-c", true, 100)), "broker-1");
        System.out.println("  ISR：" + p.isr() + " Leader=" + p.leader());
        System.out.println("  broker-1 挂 -> 新 Leader=" + p.electLeader(false) + "（从 ISR 选）");

        Partition broken = new Partition(1, List.of(
                new Replica("broker-1", "rack-a", false, 100),
                new Replica("broker-2", "rack-b", false, 99)), "broker-1");
        System.out.println("  ISR 为空（全部落后）-> 禁 unclean 选举：" + broken.electLeader(false)
                + "（分区不可用）");
        System.out.println("  允许 unclean 选举：" + broken.electLeader(true)
                + "（恢复可用但可能丢数据）");
        System.out.println();
    }

    static void assignment() {
        System.out.println("【副本分布：机架感知】");
        Map<String, List<String>> racks = new LinkedHashMap<>();
        racks.put("rack-a", List.of("broker-1", "broker-2"));
        racks.put("rack-b", List.of("broker-3", "broker-4"));
        System.out.println("  集群：" + racks);
        System.out.println("  好：分区 0 的副本 {broker-1(a), broker-3(b)} -> 单机架故障仍可用");
        System.out.println("  坏：分区 0 的副本 {broker-1(a), broker-2(a)} -> rack-a 挂全丢");
        System.out.println("  replication.factor=3 + broker.rack 配置 = 跨机架容灾");
    }

    public static void main(String[] args) {
        faultTolerance();
        assignment();
    }
}
```

**课后练习**
1. 实现分区副本模型：3 副本跨 3 机架，模拟"单机架故障/单 Broker 故障/两副本落后"三种场景，输出可用性与数据一致性结论。
2. 计算 6 个 Broker、3 机架、`replication.factor=3` 的分区副本分配方案，验证任一台机器挂掉后每个分区仍有 ≥1 个 ISR 副本。
3. 思考题：为什么 Kafka 的 Leader 选举不采用 Raft？从"依赖外部协调（ZK/KRaft）"与"分区级选主"的设计差异分析。

---

### 62-03 ISR 与 HW/LEO：可靠性的账本

**知识点**
- **三个偏移量概念**：**LEO（Log End Offset）**——每个副本自己的日志末尾位置；**HW（High Watermark）**——**所有 ISR 副本都已同步到的位置**；**消费者可见位置 = HW**——**HW 之后的消息即使 Leader 已写入，消费者也读不到**（未确认安全）。
- **ISR（In-Sync Replicas）的定义**：与 Leader 保持同步的副本集合（`replica.lag.time.max.ms` 内追上了 Leader 的 LEO）——**ISR 是动态的**：Follower 落后超时被踢出 ISR，追上后再加入。
- **可靠性公式**：**已提交（committed）= 已复制到所有 ISR 且 ≤ HW 的消息**——`acks=all` 时，生产者收到确认意味着消息已进入所有 ISR；**`min.insync.replicas` 决定最少几个 ISR 才接受写入**（ISR 不足时拒绝写入，保护不丢数据）。
- **HW 的推进与截断**：Leader 收到 Follower 的拉取请求后更新 Follower 的 LEO，**取所有 ISR 的最小 LEO 作为新 HW**；**Leader 切换时**，新 Leader 会**截断到自己的 HW**（丢弃未提交的消息）——这就是"为什么 acks=1 可能丢消息"。
- **`acks` 三档**：`0`（不等待，最快可能丢）、`1`（Leader 写入即确认，Leader 挂可能丢）、`all/-1`（所有 ISR 确认，最安全最慢）——**金融/订单用 all + min.insync.replicas=2，日志/埋点可用 1**。
- 易错点：`acks=all` 但 `min.insync.replicas=1`（ISR 只剩 Leader 时仍接受写入，等于 acks=1）；以为 HW 之前的消息一定不丢（取决于 ISR 与 unclean 配置）；不看 ISR 收缩告警（磁盘慢导致 Follower 落后，可靠性悄悄下降）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class IsrAndWatermark {
    record ReplicaState(String broker, long leo, boolean inSync) {
    }

    record PartitionState(List<ReplicaState> replicas, long hw) {
    }

    /** HW = min(LEO of ISR)；ISR 为空时 HW 不推进 */
    static PartitionState refresh(List<ReplicaState> replicas, long currentHw) {
        long minLeo = Long.MAX_VALUE;
        boolean any = false;
        for (ReplicaState r : replicas) {
            if (r.inSync()) {
                minLeo = Math.min(minLeo, r.leo());
                any = true;
            }
        }
        long hw = any ? Math.max(currentHw, minLeo) : currentHw;
        return new PartitionState(replicas, hw);
    }

    /** 生产者写入：按 acks 决定何时确认 */
    static String produce(PartitionState st, String acks, int minIsr, long newLeo) {
        List<ReplicaState> updated = new ArrayList<>();
        long leaderLeo = 0;
        for (ReplicaState r : st.replicas()) {
            if (r.broker().equals(leader(st))) {
                leaderLeo = newLeo;
                updated.add(new ReplicaState(r.broker(), newLeo, r.inSync()));
            } else {
                updated.add(r);
            }
        }
        long isrCount = updated.stream().filter(ReplicaState::inSync).count();
        if (acks.equals("all") && isrCount < minIsr) {
            return "拒绝写入：ISR=" + isrCount + " < min.insync.replicas=" + minIsr;
        }
        return switch (acks) {
            case "0" -> "立即返回（不等待）-> 可能丢";
            case "1" -> "Leader 写入即返回（LEO=" + leaderLeo + "）-> Leader 挂可能丢";
            default -> "所有 ISR 确认后返回（ISR=" + isrCount + "）-> 已提交";
        };
    }

    static String leader(PartitionState st) {
        return st.replicas().get(0).broker();
    }

    public static void main(String[] args) {
        System.out.println("【HW 推进：取 ISR 最小 LEO】");
        List<ReplicaState> replicas = List.of(
                new ReplicaState("broker-1", 105, true),
                new ReplicaState("broker-2", 100, true),
                new ReplicaState("broker-3", 98, false));      // 落后被踢出 ISR
        PartitionState st = refresh(replicas, 95);
        System.out.println("  ISR=[broker-1(105), broker-2(100)]，滞后 broker-3(98) 不在 ISR");
        System.out.println("  HW=min(105,100)=100 -> 消费者只能读到 offset<100");
        System.out.println();

        System.out.println("【acks 三档的行为】");
        System.out.println("  acks=0  -> " + produce(st, "0", 2, 106));
        System.out.println("  acks=1  -> " + produce(st, "1", 2, 106));
        System.out.println("  acks=all-> " + produce(st, "all", 2, 106));
        System.out.println();

        System.out.println("【ISR 不足时拒绝写入（保护不丢）】");
        PartitionState shrunk = new PartitionState(List.of(
                new ReplicaState("broker-1", 110, true),
                new ReplicaState("broker-2", 95, false),
                new ReplicaState("broker-3", 93, false)), 108);
        System.out.println("  " + produce(shrunk, "all", 2, 111));
        System.out.println();
        System.out.println("  可靠性公式：committed = 已复制到所有 ISR 且 <= HW");
    }
}
```

**课后练习**
1. 实现 HW/LEO 模拟器：3 副本动态加入/退出 ISR，逐步写入消息并输出每步的 LEO/HW/ISR，验证"HW 只前进不后退"。
2. 对比 `acks=1` 与 `acks=all` 在"Leader 写入后立即宕机"场景下的数据丢失量，说明 `min.insync.replicas` 的作用。
3. 思考题：为什么消费者只能读到 HW 之前的消息？从"未提交消息在 Leader 切换时会被截断"的角度推导。

---

### 62-04 日志存储：段文件与索引

**知识点**
- **分区日志被切成多个 Segment 文件**：`00000000000000000100.log`（数据）、`.index`（偏移量索引）、`.timeindex`（时间戳索引）——**单文件过大（1GB 默认）就滚动新段**；**文件名是段的起始 offset**。
- **索引的作用**：`.index` 是**稀疏索引**（每写 N 条记录一条索引项，默认 4KB 间隔），**用二分查到"小于目标 offset 的最近索引项"，再顺序扫描**——不是 B+ 树，是"稀疏索引 + 顺序扫"，因为磁盘顺序读很快。
- **查找一条消息的路径**：按 offset 定位段（文件名二分）-> 查 `.index` 定位接近位置 -> 从该位置顺序扫到目标 offset ——**这就是 Kafka 读"任意位置"都能很快的原因**。
- **清理（retention）按段进行**：`retention.ms`/`retention.bytes` 到期后**删除整个段文件**（不是逐条删）——所以"删除延迟粒度 = 一个段的时间跨度"；`log.segment.bytes` 与 `retention` 要配合（段太大导致过期后迟迟不删）。
- **压缩（compaction）是另一种清理**：按 key 保留最后一条（`cleanup.policy=compact`），适合"状态快照类"Topic（如配置、用户画像最新值）——**与按时间删除互斥/可组合**。
- 易错点：段文件过大导致 retention 回收不及时（磁盘涨）；用 offset 索引查找时间范围（应该用 `.timeindex`）；compact 与 delete 混用没想清楚语义；磁盘只按总量告警不看"段数/索引大小"。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class LogStorage {
    record Record(long offset, long timestamp, String key, String value) {
    }

    record Segment(long baseOffset, long baseTimestamp, List<Record> records) {
        long lastOffset() {
            return records.isEmpty() ? baseOffset - 1 : records.get(records.size() - 1).offset();
        }

        long sizeHint() {
            return records.size();                     // 简化：条数代替字节数
        }
    }

    /** 稀疏索引：每 interval 条记录一个索引项 */
    static Map<Long, Integer> sparseIndex(Segment seg, int interval) {
        Map<Long, Integer> index = new LinkedHashMap<>();
        for (int i = 0; i < seg.records().size(); i += interval) {
            index.put(seg.records().get(i).offset(), i);
        }
        return index;
    }

    /** 按 offset 查找：选段 -> 索引定位 -> 顺序扫 */
    static String find(List<Segment> segments, long target, int interval) {
        Segment chosen = null;
        for (Segment s : segments) {
            if (s.baseOffset() <= target && target <= s.lastOffset()) {
                chosen = s;
                break;
            }
        }
        if (chosen == null) {
            return "offset " + target + " 不在任何段（已被 retention 删除）";
        }
        Map<Long, Integer> index = sparseIndex(chosen, interval);
        int start = 0;
        for (Map.Entry<Long, Integer> e : index.entrySet()) {
            if (e.getKey() <= target) {
                start = e.getValue();
            } else {
                break;
            }
        }
        int scanned = 0;
        for (int i = start; i < chosen.records().size(); i++) {
            scanned++;
            if (chosen.records().get(i).offset() == target) {
                return "命中 " + chosen.records().get(i).value()
                        + "（段 " + chosen.baseOffset() + "，从索引位置扫了 " + scanned + " 条）";
            }
        }
        return "未找到";
    }

    public static void main(String[] args) {
        List<Segment> segments = new ArrayList<>();
        long offset = 0;
        for (int segIdx = 0; segIdx < 3; segIdx++) {
            List<Record> records = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                records.add(new Record(offset, 1000 + offset, "k" + (offset % 4), "v" + offset));
                offset++;
            }
            segments.add(new Segment(segIdx * 10L, 1000 + segIdx * 10L, records));
        }
        System.out.println("【段文件布局】");
        segments.forEach(s -> System.out.printf("  段 base=%d..%d（%d 条）%n",
                s.baseOffset(), s.lastOffset(), s.records().size()));

        System.out.println();
        System.out.println("【稀疏索引示例（间隔 4）】");
        System.out.println("  段 0 索引：" + sparseIndex(segments.get(0), 4));

        System.out.println();
        System.out.println("【按 offset 查找】");
        System.out.println("  " + find(segments, 3, 4));
        System.out.println("  " + find(segments, 22, 4));
        System.out.println("  " + find(segments, 99, 4));
        System.out.println();
        System.out.println("  retention 删除整个段（不是逐条），offset 25 被删后新消息从 25 继续");
    }
}
```

**课后练习**
1. 实现段与索引模拟器：1000 条消息切成 5 段，验证"按 offset 查找只扫 1 段 + 索引后的少数几条"。
2. 实现 compact 策略：按 key 保留最新值，用 20 条同 key 的记录验证结果（对比 delete 策略）。
3. 思考题：为什么 Kafka 用稀疏索引而不是 B+ 树？从"顺序追加 + 顺序扫描"的访问模式与写入成本分析。

---

### 62-05 生产者：幂等与事务

**知识点**
- **生产者发送的可靠性三要素**：`acks`（确认级别 62-03）、`retries`（重试次数）、`enable.idempotence`（幂等）——**幂等生产者（默认开启）通过 `PID + 序列号` 让 Broker 去重**，解决"重试导致的重复写入"（39/43 章幂等原则的 Kafka 实现）。
- **幂等 ≠ 恰好一次**：幂等只保证"**单个生产者单会话**内不重复"，**跨会话（重启后新 PID）或跨生产者**仍会重复——**端到端恰好一次需要"事务 + 消费端幂等"**（62-07）。
- **Kafka 事务**：`initTransactions -> beginTransaction -> send -> sendOffsetsToTransaction -> commitTransaction`——**把"生产消息"与"提交消费 offset"打包成一个原子操作**，实现"读-处理-写"的 exactly-once（43-06 的 2PC sink 就是它）。
- **事务的前提**：`transactional.id`（跨会话保持标识）、`isolation.level=read_committed`（消费者只读已提交）——**未提交的消息对 read_committed 消费者不可见**（与 HW 同一层语义）。
- **批次与压缩**：`linger.ms`（等一会儿凑批）+ `batch.size` + `compression.type`（lz4/zstd）——**吞吐与延迟的旋钮**（48-03 批处理思想的 Kafka 版）；`max.in.flight.requests.per.connection` 影响乱序风险（开启幂等后安全）。
- 易错点：以为开了幂等就"绝对不重复"（跨会话仍可能）；事务超时（`transaction.timeout.ms`）导致提交失败；`read_committed` 消费者看不到未提交消息被误判为"消息丢了"；linger 调太大（延迟升高）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ProducerSemantics {
    record Envelope(String pid, long seq, String key, String value, boolean committed) {
    }

    /** Broker 端幂等去重：按 (pid, seq) 幂等 */
    static class Broker {
        final Map<String, Long> lastSeqByPid = new LinkedHashMap<>();
        final List<Envelope> log = new ArrayList<>();
        boolean pendingTx = false;

        String append(String pid, long seq, String key, String value, boolean transactional,
                      boolean commitNow) {
            Long last = lastSeqByPid.get(pid);
            if (last != null && seq <= last) {
                return "DUPLICATE 丢弃（pid=" + pid + " seq=" + seq + " <= last=" + last + "）";
            }
            lastSeqByPid.put(pid, seq);
            log.add(new Envelope(pid, seq, key, value, !transactional || commitNow));
            return "APPEND ok（" + (transactional && !commitNow ? "未提交" : "已提交") + "）";
        }

        List<Envelope> readCommitted() {
            return log.stream().filter(Envelope::committed).toList();
        }
    }

    static void idempotentDemo() {
        System.out.println("【幂等生产者：重试不重复】");
        Broker broker = new Broker();
        System.out.println("  第 1 次发送：" + broker.append("pid-1", 1, "k", "m1", false, true));
        System.out.println("  重试同一条：" + broker.append("pid-1", 1, "k", "m1", false, true));
        System.out.println("  第 2 条：    " + broker.append("pid-1", 2, "k", "m2", false, true));
        System.out.println("  日志条数=" + broker.log.size() + "（重复被去重）");
        System.out.println();
    }

    static void transactionDemo() {
        System.out.println("【事务：未提交对 read_committed 不可见】");
        Broker broker = new Broker();
        System.out.println("  txn 内写 2 条（未提交）：");
        System.out.println("    " + broker.append("pid-tx", 1, "k", "a", true, false));
        System.out.println("    " + broker.append("pid-tx", 2, "k", "b", true, false));
        System.out.println("  read_committed 可见：" + broker.readCommitted().size() + " 条");
        System.out.println("  提交后再写一条已提交：");
        System.out.println("    " + broker.append("pid-tx", 3, "k", "c", true, true));
        System.out.println("  read_committed 可见：" + broker.readCommitted().size() + " 条");
        System.out.println();
        System.out.println("  跨会话（新 pid）不共享去重状态 -> 仍需消费端幂等（43-06）");
    }

    public static void main(String[] args) {
        idempotentDemo();
        transactionDemo();
    }
}
```

**课后练习**
1. 实现幂等生产者模拟：pid + seq 去重，模拟"网络超时重试 3 次"验证 Broker 只落一条。
2. 实现事务生产者：模拟"发送 3 条 + 提交 offset"原子操作，断言失败回滚后消费者看不到这批消息。
3. 思考题：为什么"幂等生产者"不能保证端到端恰好一次？从跨会话 PID 变化与"消费侧重复处理"两个角度分析。

---

### 62-06 消费者组与 Rebalance

**知识点**
- **消费者组的分配规则**：**一个分区只能被组内一个消费者消费**（组内互斥），**一个消费者可以消费多个分区**；分配策略（Range/RoundRobin/Sticky/CooperativeSticky）决定"哪个消费者拿哪几个分区"。
- **消费并行度的上限 = 分区数**：10 个分区 -> 最多 10 个消费者有活干（第 11 个空闲）——**这是"分区数创建后只能增"的根本原因**（62-02）。
- **Rebalance（再平衡）**：消费者加入/离开/订阅变化/分区数变化时，组内重新分配分区——**期间消费暂停**（Stop-The-World），**频繁 Rebalance 是消费延迟的常见原因**。
- **Rebalance 的三次演进**：Eager（全部停、全量重分配）-> Cooperative（增量式，只迁移变化的分区）-> **Static Membership**（`group.instance.id`，短暂重启不触发 Rebalance）——**生产建议 Cooperative + 合理 session.timeout**。
- **Rebalance 的触发源排查**：`max.poll.interval.ms` 超时（处理太慢被踢出组，最常见的"莫名 Rebalance"）、心跳线程与处理线程分离（`heartbeat.interval.ms`）、GC 长时间停顿（30 章）。
- 易错点：消费者处理慢导致 `max.poll.interval.ms` 超时（反复 Rebalance）；消费者数 > 分区数（多余空闲）；用 Eager 策略（每次全量重分配，抖动大）；静态成员未配置（滚动发布触发多次 Rebalance）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ConsumerGroup {
    /** Range 分配：按消费者数量均分（余数给前面的消费者） */
    static Map<String, List<Integer>> rangeAssign(List<String> consumers, int partitions) {
        Map<String, List<Integer>> out = new LinkedHashMap<>();
        int base = partitions / consumers.size();
        int remain = partitions % consumers.size();
        int next = 0;
        for (int i = 0; i < consumers.size(); i++) {
            List<Integer> mine = new ArrayList<>();
            int n = base + (i < remain ? 1 : 0);
            for (int j = 0; j < n; j++) {
                mine.add(next++);
            }
            out.put(consumers.get(i), mine);
        }
        return out;
    }

    /** Cooperative 再平衡：只迁移"多余"的分区，保留原有归属 */
    static Map<String, Set<Integer>> cooperative(Map<String, Set<Integer>> current,
                                                 List<String> consumers, int partitions) {
        Map<String, Set<Integer>> out = new LinkedHashMap<>();
        consumers.forEach(c -> out.put(c, new LinkedHashSet<>()));
        // 1) 保留仍然合法的归属
        current.forEach((consumer, parts) -> {
            if (out.containsKey(consumer)) {
                out.get(consumer).addAll(parts);
            }
        });
        // 2) 找出未分配的分区
        Set<Integer> assigned = new LinkedHashSet<>();
        out.values().forEach(assigned::addAll);
        List<Integer> orphan = new ArrayList<>();
        for (int i = 0; i < partitions; i++) {
            if (!assigned.contains(i)) {
                orphan.add(i);
            }
        }
        // 3) 按消费者负载均衡地补齐（简化：逐个分给当前最少者）
        for (Integer p : orphan) {
            String target = consumers.stream()
                    .min((a, b) -> Integer.compare(out.get(a).size(), out.get(b).size()))
                    .orElse(consumers.get(0));
            out.get(target).add(p);
        }
        return out;
    }

    public static void main(String[] args) {
        System.out.println("【Range 分配：6 分区 / 3 消费者】");
        rangeAssign(List.of("c-1", "c-2", "c-3"), 6)
                .forEach((c, p) -> System.out.printf("  %s -> %s%n", c, p));

        System.out.println();
        System.out.println("【消费者数 > 分区数：多余的闲置】");
        rangeAssign(List.of("c-1", "c-2", "c-3", "c-4", "c-5", "c-6", "c-7", "c-8"), 6)
                .forEach((c, p) -> System.out.printf("  %s -> %s%s%n", c, p,
                        p.isEmpty() ? "（空闲，消费并行上限=分区数）" : ""));

        System.out.println();
        System.out.println("【Cooperative 再平衡：扩到 4 消费者】");
        Map<String, Set<Integer>> before = new LinkedHashMap<>();
        before.put("c-1", new LinkedHashSet<>(List.of(0, 1)));
        before.put("c-2", new LinkedHashSet<>(List.of(2, 3)));
        before.put("c-3", new LinkedHashSet<>(List.of(4, 5)));
        Map<String, Set<Integer>> after = cooperative(before,
                List.of("c-1", "c-2", "c-3", "c-4"), 6);
        after.forEach((c, p) -> System.out.printf("  %s -> %s%n", c, p));
        System.out.println("  只有 c-3 的 5 号分区迁移给 c-4 -> 其余消费者不受影响（增量）");
    }
}
```

**课后练习**
1. 实现 Range 与 Cooperative 两种分配策略，用"3->4 消费者扩容"场景对比迁移的分区数量（Cooperative 应更少）。
2. 复现"处理过慢导致 Rebalance"：模拟消费者处理 200ms/条、`max.poll.interval.ms=5s`，观察被踢出组与重复消费。
3. 思考题：为什么"分区数 = 消费并行度上限"？从"组内分区互斥"的分配规则推导，并说明分区数规划的依据。

---

### 62-07 Offset 管理与精确一次

**知识点**
- **Offset 提交的两种方式**：**自动提交**（`enable.auto.commit=true`，按 `auto.commit.interval.ms` 周期性提交）与**手动提交**（`commitSync`/`commitAsync`）——**自动提交会重复消费**（"处理完成"与"offset 提交"之间崩溃 -> 重启后重复处理上次窗口）；**业务处理必须手动提交**（先处理成功后提交）。
- **提交时机决定语义**：**先提交后处理** -> 可能丢（提交了但没处理完）；**先处理后提交** -> 至少一次（可能重复）；**事务内提交** -> 恰好一次（62-05 的事务把"消息输出 + offset 提交"绑定）。
- **重复消费的幂等兜底**（39/43 章）：即使做到"处理后提交"，**Rebalance 或事务超时仍可能重复**——消费端必须有幂等键（业务 ID/事件 ID），**Kafka 的 offset 语义是"至少一次"，恰好一次靠端到端设计**。
- **Offset 存储位置**：`__consumer_offsets` 内部 Topic（按 group + partition 存），**重置 offset**（`--to-earliest/--to-latest/--to-offset`）是重放的入口（43-01 可重放源）。
- **`isolation.level`**：`read_uncommitted`（默认，能看到未提交事务消息）与 `read_committed`（只读已提交，配合事务实现 EOS）——**用事务时必须配 `read_committed`**。
- 易错点：自动提交 + 长处理（重复消费）；提交了 offset 但处理失败（数据丢失）；重置 offset 未停消费者（重复处理）；`read_committed` 下看不到未提交消息被当成"丢消息"。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class OffsetSemantics {
    record Message(long offset, String key, String value) {
    }

    record ConsumerState(long processedOffset, long committedOffset) {
        ConsumerState process(Message m) {
            return new ConsumerState(m.offset(), committedOffset);
        }

        ConsumerState commit() {
            return new ConsumerState(processedOffset, processedOffset);
        }
    }

    /** 模拟"处理 3 条后崩溃"，对比两种提交策略的重启行为 */
    static void compareCommitStrategy(boolean commitBeforeProcess) {
        List<Message> log = List.of(
                new Message(0, "k1", "m1"), new Message(1, "k2", "m2"),
                new Message(2, "k3", "m3"), new Message(3, "k4", "m4"));
        long committed = 0;
        List<String> processed = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Message m = log.get(i);
            if (commitBeforeProcess) {
                committed = m.offset();                 // 先提交
                processed.add(m.value());
            } else {
                processed.add(m.value());               // 先处理
                committed = m.offset();                 // 后提交
            }
        }
        // 崩溃：最后一次提交之前的都不会重放
        List<String> afterRestart = new ArrayList<>();
        for (Message m : log) {
            if (m.offset() > committed) {
                afterRestart.add(m.value());
            }
        }
        System.out.printf("  %s提交：已处理=%s committed=%d -> 重启后从 %s 开始%n",
                commitBeforeProcess ? "先" : "后", processed, committed, afterRestart);
        if (commitBeforeProcess) {
            System.out.println("    风险：offset 已提交但处理未完成的那条 -> 丢失（至少一次被破坏）");
        } else {
            System.out.println("    语义：至少一次（可能重复处理 committed 之外的部分）");
        }
    }

    static void eosWithTransaction() {
        System.out.println("【恰好一次：事务把输出与 offset 绑定】");
        Map<String, String> steps = new LinkedHashMap<>();
        steps.put("1", "beginTransaction（含 offset 提交）");
        steps.put("2", "处理消息 -> 产出结果到下游 Topic");
        steps.put("3", "sendOffsetsToTransaction（offset 纳入同一事务）");
        steps.put("4", "commitTransaction：结果与 offset 一起原子生效");
        steps.put("5", "失败则 abort：下游无脏数据、offset 不前进");
        steps.forEach((k, v) -> System.out.printf("  %s. %s%n", k, v));
        System.out.println();
        System.out.println("  前提：transactional.id + read_committed + 消费端仍幂等（跨会话兜底）");
    }

    public static void main(String[] args) {
        System.out.println("【提交策略对比】");
        compareCommitStrategy(true);
        System.out.println();
        compareCommitStrategy(false);
        System.out.println();
        eosWithTransaction();
    }
}
```

**课后练习**
1. 实现消费者 offset 语义模拟：自动提交/手动提交/事务提交三种模式，在"处理到第 3 条崩溃"场景下对比最终结果（丢失/重复/恰好一次）。
2. 实现"重置 offset 重放"：把 group offset 置 0 重读全部历史，验证与"重放期间新写入消息"的边界。
3. 思考题：为什么 Kafka 自称"至少一次"，而 EOS 要靠事务 + 幂等？从"跨会话/跨系统"的边界分析 43-06 的等价结论。

---

### 62-08 消息保留、压缩与清理策略

**知识点**
- **两种清理策略**：`cleanup.policy=delete`（按时间/大小删段，日志类）与 `cleanup.policy=compact`（按 key 保留最新值，状态类）——**可组合**（`delete,compact`：既按时间删又保留最新 key）。
- **compact 的语义**：同一个 key 的旧值被清理，**只留最新一条**；**墓碑消息（tombstone，value=null）**表示"该 key 被删除"，保留一段时间后清除——**消费端重放 compact Topic 得到的是"最终状态快照"**（49-06 事件溯源的快照思想）。
- **compact 的触发与整理**：后台清理线程周期性扫描段 -> 生成新的整理段 -> 旧段延迟删除；**compact 不保证即时**（可能同时看到新旧值，消费端要"后写覆盖"语义）。
- **保留策略的参数配合**：`retention.ms`/`retention.bytes` + `log.segment.bytes`（段太大 -> 过期删不掉）+ `log.retention.check.interval.ms`（检查频率）——**三者共同决定"数据实际能留多久"**。
- **场景对应**：日志/埋点（delete，7 天）、订单事件（delete + 长保留，可重放对账）、配置/画像最新值（compact）、CDC 表同步（compact，下游拿到每个主键的最新镜像 43-09）。
- 易错点：高频 key 用 compact 但 key 基数极大（整理开销与存储都高）；tombstone 配置不当（删掉的 key 又"复活"）；delete 策略下以为"消费过就删"（删除与消费无关）；段大小与保留期不匹配（过期数据迟迟不删）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RetentionPolicy {
    record Record(long offset, long timestamp, String key, String value) {
        boolean isTombstone() {
            return value == null;
        }
    }

    /** delete 策略：按时间删除（保留最近 N 个时间单位） */
    static List<Record> applyDelete(List<Record> log, long now, long retentionMs) {
        List<Record> kept = new ArrayList<>();
        for (Record r : log) {
            if (now - r.timestamp() <= retentionMs) {
                kept.add(r);
            }
        }
        System.out.printf("  delete：总 %d 条 -> 保留 %d 条（窗口 %dms）%n",
                log.size(), kept.size(), retentionMs);
        return kept;
    }

    /** compact 策略：每个 key 只留最新一条；tombstone 保留（视配置）后清除 */
    static List<Record> applyCompact(List<Record> log, boolean keepTombstone) {
        Map<String, Record> latest = new LinkedHashMap<>();
        for (Record r : log) {
            latest.put(r.key(), r);                    // 后写覆盖
        }
        List<Record> out = new ArrayList<>();
        latest.forEach((k, r) -> {
            if (r.isTombstone() && !keepTombstone) {
                return;                                // 已删除的 key 彻底移除
            }
            out.add(r);
        });
        System.out.printf("  compact：总 %d 条（%d 个 key）-> 保留 %d 条%n",
                log.size(), latest.size(), out.size());
        return out;
    }

    public static void main(String[] args) {
        long now = 1_000_000;
        List<Record> log = new ArrayList<>();
        log.add(new Record(0, now - 900_000, "user:1", "{\"level\":\"NEW\"}"));
        log.add(new Record(1, now - 800_000, "user:2", "{\"level\":\"NEW\"}"));
        log.add(new Record(2, now - 500_000, "user:1", "{\"level\":\"VIP\"}"));
        log.add(new Record(3, now - 100_000, "user:2", null));      // 删除 user:2
        log.add(new Record(4, now - 50_000, "user:3", "{\"level\":\"NEW\"}"));

        System.out.println("【原始日志】");
        log.forEach(r -> System.out.printf("  offset=%d key=%-8s value=%s%n",
                r.offset(), r.key(), r.value()));
        System.out.println();

        applyDelete(log, now, 600_000);
        System.out.println();
        applyCompact(log, false);
        System.out.println("  对比：delete 保留时间窗口，compact 保留最新状态（消费端得到快照）");
        System.out.println();
        System.out.println("【策略选型】");
        Map<String, String> choice = new LinkedHashMap<>();
        choice.put("日志/埋点", "delete（按时间），7~30 天");
        choice.put("订单事件", "delete + 长保留（可重放对账）");
        choice.put("配置/画像", "compact（每 key 最新值）");
        choice.put("CDC 表同步", "compact（每主键最新镜像，43-09）");
        choice.forEach((k, v) -> System.out.printf("  %-10s %s%n", k, v));
    }
}
```

**课后练习**
1. 实现两种清理策略（delete/compact）并组合（`delete,compact`），用 30 条含 tombstone 的记录验证输出。
2. 分析"段大小 1GB + 保留 7 天"在"日增 100GB"场景下的实际磁盘占用，给出参数调整建议。
3. 思考题：为什么 CDC 用 compact 而不是 delete？从"下游需要每个主键的最新镜像"与"重放时不重复应用旧值"分析。

---

### 62-09 性能模型：顺序写、零拷贝、批量

**知识点**
- **Kafka 高吞吐的五个来源**：① **顺序追加写**（磁盘顺序写接近内存速度，随机写慢几个数量级）；② **页缓存（Page Cache）**（写入先进 OS 页缓存，由内核刷盘，避免 JVM 堆 GC 压力）；③ **零拷贝（sendfile）**（读消息时数据从页缓存直接到网卡，跳过用户态拷贝——60-07 的零拷贝在 Kafka 的应用）；④ **批量 + 压缩**（生产者凑批、Broker 整批存储、消费者整批拉取）；⑤ **分区并行**（多分区多 Broker 并行）。
- **"利用页缓存而非 JVM 堆"是关键决策**：消息不进 JVM 堆（避免 GC 与对象开销），由 OS 管理缓存——**代价是"重启后首次读会走磁盘"**（缓存冷）；**Kafka 不应与其他重 IO 进程混部**（页缓存被抢）。
- **批量的三个参数**：`batch.size`（批次字节）、`linger.ms`（等待凑批时间）、`compression.type`（压缩算法）——**吞吐与延迟的权衡**（48-03 的批处理同构）；**压缩在生产者做、Broker 原样存、消费者解压**（节省网络与磁盘）。
- **消费者吞吐**：`fetch.min.bytes`/`fetch.max.wait.ms`（攒够再返回）、`max.poll.records`（每次 poll 条数）——**小批量高频 poll 增加往返**，大批量增加处理延迟。
- **磁盘与网络是最终瓶颈**：单 Broker 吞吐经验值（顺序写 100~200MB/s 每盘）；**副本复制占用网络**（`replication.factor` 翻倍网络流量）——**扩容靠加 Broker 与分区**（62-02）。
- 易错点：linger=0（不凑批，吞吐低）；压缩算法选 gzip（CPU 高，推荐 lz4/zstd）；把 Kafka 与 ES/数据库混部（页缓存争抢）；单分区追求高吞吐（分区才并行）。

**示例代码**
```java
import java.util.LinkedHashMap;
import java.util.Map;

public class PerformanceModel {
    /** 顺序写 vs 随机写的量级对比（经验值） */
    static void diskIo() {
        System.out.println("【磁盘 IO 的量级差异】");
        String[][] rows = {
                {"机械盘顺序写", "100~200 MB/s", "追加日志"},
                {"机械盘随机写", "0.1~1 MB/s（约 100 IOPS）", "B 树随机更新"},
                {"SSD 顺序写", "500~3000 MB/s", "日志/大文件"},
                {"SSD 随机写", "50~500 MB/s", "数据库"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-14s %-26s %s%n", r[0], r[1], r[2]);
        }
        System.out.println("  顺序写比随机写快 2~3 个数量级 -> 这是「日志式」存储的根基");
        System.out.println();
    }

    /** 零拷贝：传统 4 次拷贝 vs sendfile 的 2 次 */
    static void zeroCopy() {
        System.out.println("【读消息的两条路径】");
        Map<String, String> paths = new LinkedHashMap<>();
        paths.put("传统 read+write", "磁盘->页缓存->用户态缓冲->socket 缓冲->网卡（4 次拷贝 + 2 次上下文切换）");
        paths.put("sendfile 零拷贝", "磁盘->页缓存->网卡（2 次拷贝，不经用户态）");
        paths.put("实际效果", "大消息批量消费时 CPU 占用与延迟显著下降");
        paths.forEach((k, v) -> System.out.printf("  %-14s %s%n", k, v));
        System.out.println();
    }

    /** 批量参数对吞吐与延迟的影响（简化模型） */
    static void batching(int batchSize, int lingerMs, int messages, int messageBytes) {
        int batches = (int) Math.ceil((double) messages / batchSize);
        double networkBytes = (double) messages * messageBytes;
        double latencyMs = lingerMs + batches * 0.5;          // 简化：每批一次往返
        System.out.printf("  batch=%d linger=%dms -> 批次数=%d 传输=%.1fMB 估算延迟=%.1fms%n",
                batchSize, lingerMs, batches, networkBytes / 1024 / 1024, latencyMs);
    }

    public static void main(String[] args) {
        diskIo();
        zeroCopy();
        System.out.println("【批量参数权衡（10 万条 × 512B）】");
        batching(1, 0, 100_000, 512);
        batching(1000, 5, 100_000, 512);
        batching(5000, 20, 100_000, 512);
        System.out.println("  结论：批量越大吞吐越高、延迟越高；按业务 SLA 选 linger");
        System.out.println();
        System.out.println("  五要素：顺序写 + 页缓存 + 零拷贝 + 批量压缩 + 分区并行");
    }
}
```

**课后练习**
1. 用压测（或模拟参数）对比 `linger.ms=0/5/20` 与 `batch.size` 三档组合的吞吐与 P99 延迟，画出权衡曲线。
2. 计算"3 副本 + 1 倍网络复制"下写入 100MB/s 时 Broker 的实际出口带宽需求，判断集群网络是否是瓶颈。
3. 思考题：为什么 Kafka 把消息放页缓存而不是 JVM 堆？从 GC 压力、对象开销、重启恢复三个角度分析。

---

### 62-10 运维与积压排查

**知识点**
- **积压（Consumer Lag）= 生产速率 > 消费速率**：`lag = logEndOffset - consumerOffset`——**Lag 是消费健康度的第一指标**（42 章饱和度思想）。
- **积压的四类原因与处置**：① **消费者不足**（加消费者，但受分区数上限 62-06）；② **消费逻辑慢**（优化处理/批量/异步，59 章调优）；③ **消费者反复 Rebalance**（查 `max.poll.interval.ms`，62-06）；④ **生产突增**（削峰/扩容/降级）。
- **排查的观测面**：**主题级**（生产/消费速率、Lag 曲线）、**分区级**（哪个分区 Lag 集中——倾斜 59-08）、**消费者级**（poll 耗时、提交间隔）、**Broker 级**（磁盘、网络、ISR 收缩 62-03）。
- **Lag 突然不动的两种解释**：**消费者挂了**（看消费者存活/日志）或**生产也停了**（Lag 不变但速率都为 0）——**看速率而不是只看 Lag**。
- **运维动作的红线**：**不要随便重置 offset**（重放会重复处理，需业务确认幂等）；**不要让消费者数长期 > 分区数**（浪费资源）；**不要在业务高峰做分区扩容**（会触发 Rebalance）。
- 易错点：只看 Lag 不看速率（误判）；Lag 高就加消费者（分区数不够时无效）；重置 offset 未评估重复影响；分区倾斜不管（个别分区 Lag 大拖慢整体）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class LagDiagnosis {
    record PartitionLag(int partition, long endOffset, long consumerOffset, double produceRate,
                        double consumeRate) {
        long lag() {
            return endOffset - consumerOffset;
        }

        String trend() {
            double net = produceRate - consumeRate;
            if (Math.abs(net) < 0.01) {
                return net == 0 ? "稳定" : (net > 0 ? "缓慢增长" : "缓慢下降");
            }
            return net > 0 ? "增长（生产 > 消费）" : "下降（消费 > 生产）";
        }
    }

    static void diagnose(List<PartitionLag> partitions) {
        long totalLag = partitions.stream().mapToLong(PartitionLag::lag).sum();
        System.out.printf("  总 Lag=%d%n", totalLag);
        for (PartitionLag p : partitions) {
            System.out.printf("    p%-2d lag=%-8d 生产=%.1f/s 消费=%.1f/s -> %s%n",
                    p.partition(), p.lag(), p.produceRate(), p.consumeRate(), p.trend());
        }
        long max = partitions.stream().mapToLong(PartitionLag::lag).max().orElse(0);
        double avg = totalLag / (double) partitions.size();
        if (max > avg * 3) {
            System.out.println("    倾斜：最大分区 Lag 是均值的 3 倍以上 -> 查 key 倾斜（59-08）");
        }
        long stalled = partitions.stream()
                .filter(p -> p.produceRate() == 0 && p.consumeRate() == 0).count();
        if (stalled == partitions.size()) {
            System.out.println("    全部速率归零 -> 检查消费者存活与生产端状态");
        } else if (partitions.stream().anyMatch(p -> p.produceRate() > 0 && p.consumeRate() == 0)) {
            System.out.println("    消费速率为零 -> 消费者卡死（处理阻塞/死锁/异常退出）");
        }
    }

    static void playbook() {
        System.out.println("【积压处置手册】");
        Map<String, String> book = new LinkedHashMap<>();
        book.put("消费者不足", "加消费者（<= 分区数），否则先扩分区");
        book.put("处理慢", "批量化/异步化/优化 SQL，看单条处理耗时");
        book.put("反复 Rebalance", "查 max.poll.interval.ms 与处理耗时（62-06）");
        book.put("分区倾斜", "按 key 拆分热点（39/59-08）");
        book.put("生产突增", "上游削峰/限流，或临时扩容");
        book.forEach((k, v) -> System.out.printf("  %-14s %s%n", k, v));
    }

    public static void main(String[] args) {
        System.out.println("【场景一：正常消费】");
        diagnose(List.of(
                new PartitionLag(0, 10_000, 9_900, 100, 100),
                new PartitionLag(1, 10_000, 9_950, 100, 100)));
        System.out.println();
        System.out.println("【场景二：单分区倾斜】");
        diagnose(List.of(
                new PartitionLag(0, 50_000, 20_000, 500, 60),
                new PartitionLag(1, 10_000, 9_980, 100, 100),
                new PartitionLag(2, 10_000, 9_990, 100, 100)));
        System.out.println();
        System.out.println("【场景三：消费者卡死】");
        diagnose(List.of(
                new PartitionLag(0, 30_000, 10_000, 120, 0),
                new PartitionLag(1, 30_000, 10_000, 120, 0)));
        System.out.println();
        playbook();
    }
}
```

**课后练习**
1. 实现 Lag 诊断器：分区级 Lag/速率/趋势 + 倾斜检测 + 卡死检测，用三组数据验证结论（62-10 场景）。
2. 设计一套 Kafka 监控指标清单（主题/分区/消费者/Broker 四层），标注每项的告警阈值依据。
3. 思考题：为什么"只看 Lag 不看速率"会误判？构造一个"生产停了、Lag 不变"的场景说明。

---

### 62-11 综合案例：订单事件流的 Kafka 落地

**知识点**
- **把本章收口成一条真实的订单事件流**：生产者（订单服务）按 `orderId` 分区 -> `acks=all` + 幂等 -> Topic（`delete` 保留 7 天，可重放对账）-> 多个消费组各司其职（履约/积分/搜索/数仓同步）-> 消费端手动提交 + 幂等键 -> Lag 监控与积压处置。
- **Topic 设计要点**：**分区数按消费并行度规划**（预计 10 个消费者 -> 至少 10 分区，留余量 12）；**key 用 orderId**（同订单事件有序）；**副本 3 + 机架感知**（容灾）；**retention 与业务对账窗口对齐**（7 天足够 T+1 对账与短期重放）。
- **消费组设计**：**一个业务一个消费组**（履约组/积分组/数仓组各自独立 offset，互不影响 62-01）；**消费者数 ≤ 分区数**；**组内消费者处理能力要均衡**（否则倾斜 62-10）。
- **一致性链路**：生产端幂等 + `acks=all` -> Broker 侧 HW 保证已提交 -> 消费端"处理后提交 + 业务幂等" -> 最终一致（39/43 章）+ 对账兜底（39-09）。
- **失败处理**：消费失败重试（本地重试队列 + 延迟队列 28-02）-> 超过次数进死信 Topic -> 告警与人工重放（重放要带幂等，62-07）。
- 易错点：分区数按"当前消费者数"定（扩容无空间）；所有业务共用一个消费组（消费进度互相拖累）；消费端自动提交（重复/丢）；死信无监控（消息静默丢失）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class OrderEventStreamCase {
    record Event(long offset, String orderId, String type) {
    }

    record ConsumerGroupState(String group, long committedOffset, List<String> processedIds,
                              boolean autoCommit) {
    }

    /** 消费：处理后提交 + 幂等（eventId 去重） */
    static ConsumerGroupState consume(ConsumerGroupState state, List<Event> log,
                                      int maxPoll, boolean failOnce) {
        long processed = 0;
        for (Event e : log) {
            if (e.offset() <= state.committedOffset()) {
                continue;
            }
            if (processed >= maxPoll) {
                break;
            }
            String eventId = state.group() + "#" + e.offset();
            if (state.processedIds().contains(eventId)) {
                System.out.printf("    [%s] offset=%d 重复事件 -> 跳过（幂等）%n",
                        state.group(), e.offset());
                continue;
            }
            if (failOnce) {
                System.out.printf("    [%s] offset=%d 处理失败 -> 不提交 offset（消息重投）%n",
                        state.group(), e.offset());
                return state;
            }
            state.processedIds().add(eventId);
            processed++;
        }
        long newCommitted = state.committedOffset() + processed;
        System.out.printf("    [%s] 处理 %d 条 -> committed offset=%d%n",
                state.group(), processed, newCommitted);
        return new ConsumerGroupState(state.group(), newCommitted,
                state.processedIds(), state.autoCommit());
    }

    public static void main(String[] args) {
        List<Event> log = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            log.add(new Event(i, "order-" + (9001 + i / 2),
                    i % 2 == 0 ? "OrderCreated" : "OrderPaid"));
        }
        System.out.println("【Topic 设计】");
        Map<String, String> topic = new LinkedHashMap<>();
        topic.put("分区数", "12（按 10 消费者规划 + 余量）");
        topic.put("key", "orderId（同订单事件有序）");
        topic.put("副本", "3 + broker.rack（跨机架）");
        topic.put("保留", "delete 7 天（覆盖 T+1 对账与短期重放）");
        topic.put("生产", "acks=all + 幂等（min.insync.replicas=2）");
        topic.forEach((k, v) -> System.out.printf("  %-6s %s%n", k, v));

        System.out.println();
        System.out.println("【消费组独立推进】");
        var fulfillment = new ConsumerGroupState("group-fulfillment", 0, new ArrayList<>(), false);
        var points = new ConsumerGroupState("group-points", 0, new ArrayList<>(), false);
        System.out.println("  履约组：");
        fulfillment = consume(fulfillment, log, 10, false);
        System.out.println("  积分组（只消费了一半就宕机）：");
        points = consume(points, log, 3, false);
        points = consume(points, log, 10, false);
        System.out.println("    两组 offset 互不影响：" + fulfillment.committedOffset()
                + " vs " + points.committedOffset());

        System.out.println();
        System.out.println("【消费失败：重投与幂等】");
        var retry = new ConsumerGroupState("group-search", 0, new ArrayList<>(), false);
        retry = consume(retry, log, 10, true);
        retry = consume(retry, log, 10, false);
        System.out.println();
        System.out.println("  一致性链路：生产幂等 + acks=all + 处理后提交 + 业务幂等 + 对账兜底");
    }
}
```

**课后练习**
1. 端到端实现订单事件流模拟：3 个消费组、分区按 orderId、失败重投、幂等去重，输出各组最终状态。
2. 为"履约消费组"设计死信方案：失败 3 次进死信 Topic + 告警 + 人工重放（带幂等），画出数据流。
3. 思考题：为什么"一个业务一个消费组"？构造"两个业务共用一个组"导致消费进度互相拖累的场景并说明后果。

## 本章综合练习

### 综合题：手写一个迷你消息队列

**场景**

用纯 JDK 实现迷你 Kafka：分区与顺序写、分段日志与索引、消费位点与重复消费、消费者组再均衡、ISR 与 acks 语义。目标是搞清「Kafka 为什么快、为什么可能重复」。

**任务**
1. 实现分区与顺序追加写：每个分区一个 append-only 文件，写入只追加不修改，说明顺序写为什么比随机写快。
2. 实现分段与稀疏索引：按大小滚动新段，用 offset 索引快速定位，验证查任意 offset 只需读一个段。
3. 实现消费位点：消费者提交 offset，模拟「处理成功但提交前崩溃」验证会重复消费，说明至少一次语义。
4. 实现 acks 语义：acks=0/1/all 三种配置下，模拟 broker 崩溃验证消息可能丢失的场景。

**验收清单**
- [ ] 同一分区内消息严格有序，跨分区不保证有序（并有说明）。
- [ ] 按 offset 查找能命中正确段与位置，不需要全文件扫描。
- [ ] 能复现重复消费并说明幂等消费的必要性。
- [ ] acks=all 时已确认的消息在单 broker 崩溃后不丢（或说明需要几个副本）。
