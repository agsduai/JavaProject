### 43-05 状态管理与容错

**知识点**
- **流处理是"常驻的有状态计算"**：窗口累计值、去重集合、join 缓存都是**状态**——它们必须活在进程里（快）又能被持久化（挂了可恢复）。**状态管理是流系统与"无状态微服务"的本质区别**。
- **状态的两类存储**：**算子本地状态**（RocksDB/堆内，读写快，计算用）+ **周期性快照**（checkpoint，写到分布式存储 HDFS/S3，恢复用）。**本地状态是主副本，checkpoint 是保险**。
- **Checkpoint 机制（Chandy-Lamport 快照）**：Source 插入屏障（barrier）随数据流流动，barrier 到达某算子时该算子把当前状态快照到远端存储；barrier 对齐保证"快照时点前后数据不混"。**恢复 = 所有算子回到最近一次 checkpoint 的状态，从 Source 重放之后的数据**。
- **恢复依赖"可重放源"**：Kafka 的 offset 就是重放指针——checkpoint 里存了"当时消费到 offset X"，故障后从 X 重读。**不可重放的源（实时 HTTP 推送）配 checkpoint 毫无意义**——这也是"流处理要对接可重放消息系统"的架构原因。
- **状态治理是运维主战场**：TTL（key 1 天不活动就清理，防止状态无限膨胀）、状态大小监控（RocksDB 落盘量、checkpoint 大小与耗时）、大状态拆分（按 key 哈希分散）。**状态失控 = checkpoint 超时 = 一直恢复失败 = 作业死亡循环**。
- **checkpoint 与可用性的平衡**：间隔太密（1s）→ 开销大；太疏（10min）→ 恢复要重放 10 分钟数据。典型 30s~2min，大状态作业放宽并加增量 checkpoint（只传变化部分）。
- 易错点：状态无 TTL（运行三月 RocksDB 500GB）；checkpoint 一直失败没人看（恢复时才发现作业早就"假活"）；把业务数据只存本地内存（挂了全丢）；恢复期间重复消费未做幂等（43-06）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class StateCheckpoint {
    /** 算子状态：窗口累计 + 最近 offset */
    static class OperatorState {
        final Map<String, Double> windowTotals = new LinkedHashMap<>();
        long kafkaOffset = 0;
        int version = 0;

        void process(String key, double amount, long offset) {
            windowTotals.merge(key, amount, Double::sum);
            kafkaOffset = offset;
        }

        Map<String, Object> snapshot() {
            Map<String, Object> snap = new LinkedHashMap<>(windowTotals);
            snap.put("__offset", kafkaOffset);
            return snap;
        }

        void restore(Map<String, Object> snap) {
            windowTotals.clear();
            for (Map.Entry<String, Object> e : snap.entrySet()) {
                if (!e.getKey().equals("__offset")) {
                    windowTotals.put(e.getKey(), (Double) e.getValue());
                } else {
                    kafkaOffset = (long) e.getValue();
                }
            }
        }
    }

    static void checkpointCycle() {
        System.out.println("【一个 checkpoint 周期与故障恢复】");
        OperatorState state = new OperatorState();
        state.process("11-17", 69.0, 100);
        state.process("11-17", 199.0, 101);
        Map<String, Object> snap = state.snapshot();
        System.out.println("  T0 checkpoint#1：" + snap + " -> 持久化到 S3");
        state.process("11-17", 29.0, 102);          // 这条之后发生故障
        System.out.println("  T1 继续处理 offset=102（GMV=297）后作业崩溃");
        OperatorState recovered = new OperatorState();
        recovered.restore(snap);
        System.out.println("  恢复：状态回到 checkpoint#1（GMV=268, offset=101）");
        System.out.println("  重放：从 Kafka offset=101 重读 -> 重新处理 102 -> GMV=297");
        System.out.println("  重复消费了 offset=102！下游必须幂等（43-06）");
        System.out.println();
    }

    static void ttlDiscipline() {
        System.out.println("【状态 TTL：防止状态无限膨胀】");
        record StateItem(String key, String ttl, String effect) {
        }
        List<StateItem> items = List.of(
                new StateItem("去重集合（订单ID）", "24h", "一天前的订单不会再来，清理"),
                new StateItem("窗口累计", "size+允许迟到", "窗口关闭即删"),
                new StateItem("join 左流缓存", "10min", "按 join 语义定"),
                new StateItem("无 TTL 的 user 状态", "——", "三个月 500GB，checkpoint 超时"));
        for (StateItem i : items) {
            System.out.printf("  %-18s TTL=%-14s %s%n", i.key(), i.ttl(), i.effect());
        }
        System.out.println("  规则：每个状态都要回答“什么时候可以删”");
        System.out.println();
    }

    static void healthSignals() {
        System.out.println("【流作业的存活判据：checkpoint 是心跳】");
        List<String> signals = List.of(
                "checkpoint 成功率 100%（失败 1 次即告警，连续失败=作业病危）",
                "checkpoint 耗时 < 间隔的 50%（否则永远在 checkpoint）",
                "Source 消费延迟（lag）持续增长 = 处理不过来",
                "RocksDB 落盘量增速稳定（突然暴涨=状态泄漏）");
        for (String s : signals) {
            System.out.println("  - " + s);
        }
        System.out.println("  对照：进程活着但 checkpoint 失败一周 —— “假活”，恢复时才发现状态早没了");
    }

    public static void main(String[] args) {
        checkpointCycle();
        ttlDiscipline();
        healthSignals();
    }
}
```

**课后练习**
1. 实现示例的状态快照/恢复闭环：注入故障点（处理 2 条后崩溃），验证恢复 + 重放后最终结果正确，并记录重复消费的条数。
2. 为你的流作业（或模拟作业）做状态清单：每个状态的类型、TTL、大小估算，找出无 TTL 的状态并补上。
3. 思考题：为什么"不可重放的源配 checkpoint 毫无意义"？从恢复语义推导流系统对上游消息系统的架构要求。

---

### 43-06 Exactly-Once 语义

**知识点**
- **三条可靠性等级**：At-most-once（最多一次：可能丢，简单最快）；At-least-once（至少一次：不丢但可能重，最常用默认）；Exactly-once（精确一次：不丢不重，成本最高）。**选择依据是业务对"丢"与"重"的容忍度**：监控大屏丢了可容忍（at-most-once 也行），支付计费重了就是事故（exactly-once 或幂等兜底）。
- **Exactly-once 的两个层面**：**端到端** = 框架内部状态精确一次（checkpoint 保证）+ **外部副作用精确一次**（写 Kafka/DB/MQ 的结果不重不漏）。框架内部容易，外部难——**外部系统必须参与两阶段提交或提供幂等**。
- **实现手段一：幂等写入**（最常用）：下游按主键 upsert（`INSERT ... ON DUPLICATE KEY UPDATE`）、Kafka 按键写可覆盖。**重放覆盖旧结果，天然收敛**。窗口结果带主键（窗口起点）就是为幂等服务。
- **实现手段二：两阶段提交（2PC）**：Flink 的 TwoPhaseCommitSink——预提交（写事务暂存）+ checkpoint 成功后正式提交。要求下游支持事务（Kafka 事务 API），代价是延迟（数据要等 commit 才可见）。
- **实现手段三：事务性写入下游**（DB 侧）：把"状态更新 + 输出写入"放进下游事务，靠下游原子性兜底。流框架只保证 at-least-once，精确性交给下游约束。
- **"幂等 + at-least-once" ≈ exactly-once**：这是工程上最实用的等价——不追求框架级精确，而是**让每个写操作幂等**，重复执行结果不变。第 28 章消费幂等、第 39 章本地消息表，同一思想贯穿。
- 易错点：以为开启 checkpoint 就端到端 exactly-once（只保了内部状态）；下游写非幂等（insert 重复数据）；2PC 下游不支持事务还硬用（事务泄漏）；把"窗口结果对不上"归罪于框架（多数是幂等缺失或时间语义错）。

**示例代码**
```java
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ExactlyOnce {
    record WindowResult(String windowKey, double gmv, int version) {
    }

    /** 幂等 sink：窗口结果按主键覆盖 */
    static class IdempotentSink {
        final Map<String, WindowResult> table = new LinkedHashMap<>();

        void write(WindowResult r) {
            WindowResult existing = table.get(r.windowKey());
            if (existing == null || r.version() >= existing.version()) {
                table.put(r.windowKey(), r);
                System.out.printf("  写入 %s gmv=%.1f v%d（覆盖式）%n",
                        r.windowKey(), r.gmv(), r.version());
            } else {
                System.out.printf("  忽略 %s v%d（已有 v%d，旧版本）%n",
                        r.windowKey(), r.version(), existing.version());
            }
        }
    }

    static void idempotentReplay() {
        System.out.println("【重放场景：同一窗口结果被写两次】");
        IdempotentSink sink = new IdempotentSink();
        sink.write(new WindowResult("11-17", 268.0, 1));
        // 故障恢复后重放，窗口重算出相同结果（版本相同）或更新结果（版本更高）
        sink.write(new WindowResult("11-17", 268.0, 1));
        sink.write(new WindowResult("11-17", 297.0, 2));
        System.out.println("  最终表：" + table(sink));
        System.out.println("  无论重放多少次，结果收敛 —— 这就是幂等的价值");
        System.out.println();
    }

    static Map<String, WindowResult> table(IdempotentSink sink) {
        return sink.table;
    }

    static void levelChoice() {
        System.out.println("【按业务选等级，不默认最高】");
        String[][] rows = {
                {"监控指标曲线", "at-least-once + 幂等（按窗口键覆盖）", "偶尔重复被覆盖，无损"},
                {"实时大屏 GMV", "幂等 upsert（窗口键主键）", "修正可见，最终一致"},
                {"计费/佣金入账", "事务写入 + 去重表", "钱的事，宁可复杂不可错"},
                {"风控事件流", "at-least-once + 下游幂等消费", "与 28 章消费幂等同款"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-12s %-34s %s%n", r[0], r[1], r[2]);
        }
        System.out.println();
    }

    static void twoPhase() {
        System.out.println("【2PC sink：预提交与正式提交】");
        List<String> steps = List.of(
                "1) sink 收到数据 -> 写入下游事务（未提交，预提交态）",
                "2) checkpoint barrier 到达 -> 状态快照（含事务句柄）",
                "3) checkpoint 成功 -> 正式 commit 事务（数据对外可见）",
                "4) 失败 -> 事务 abort，恢复后从 checkpoint 重放");
        for (String s : steps) {
            System.out.println("  " + s);
        }
        System.out.println("  代价：数据可见延迟 = checkpoint 间隔；要求下游支持事务");
        System.out.println("  实用主义：多数场景「幂等 + at-least-once」已等价于业务上的精确一次");
    }

    public static void main(String[] args) {
        idempotentReplay();
        levelChoice();
        twoPhase();
    }
}
```

**课后练习**
1. 把 43-05 的恢复重放接上幂等 sink，验证重复消费后最终结果仍然正确；再去掉幂等，观察重复数据。
2. 为你的流作业下游设计幂等方案：主键选择（窗口键/业务键）、版本号策略、旧版本忽略规则。
3. 思考题：为什么"幂等 + at-least-once"在工程上等价于 exactly-once？举一个该等价不成立的反例（幂等键设计错误的场景）。

---

### 43-07 流式 Join 与维度关联

**知识点**
- **流上的 Join 难在"两边都在动"**：批处理 join 时两张表都静止；流上订单流与支付流都在到达，**必须各自缓存等待配对**——缓存多大、等多久，是流 join 的核心参数。
- **窗口 join**：两边各按窗口截取，窗口内互相配对（如 10 分钟内订单与支付匹配）。实现简单，代价是**边界订单丢失**（订单 11:59:58、支付 00:00:01 跨窗口）。适合"相关性窗口明确"的场景。
- **区间 join（interval join）**：按业务时间区间配对——`支付时间 BETWEEN 下单时间 AND 下单时间+30min`。语义精确（每个订单在自己区间内找支付），是订单-支付类 join 的标准解。实现上要缓存左流直到区间终点（水位线推进才清理）。
- **维表关联（stream-table join）**：订单流关联商品维表——维表是"慢变的静态数据"。三种实现：**同步查库**（简单但 QPS 高时打爆 DB）、**本地缓存 + 定期刷新**（热路径首选，代价是短暂不一致）、**维表变更流广播**（CDC 推送变更，一致性最好）。
- **迟到与配对失败的处理**：等了 30 分钟没有支付的订单——进"未配对旁路"（对账用），不能静默丢弃。**join 的输出要覆盖三种情况：配对成功、左等待、右等待**。
- **状态量估算**：join 缓存 = 左流速率 × 区间长度。订单 1000 QPS × 30 分钟 = 180 万条在缓存——**区间长度与状态量的乘积关系**决定 join 设计。
- 易错点：join 缓存无清理（状态爆炸，43-05）；用处理时间做区间（乱序下配对错乱）；维表本地缓存无刷新（商品改价后订单一直用旧价）；配对失败的订单静默丢失（财务对不上）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class StreamJoin {
    record Order(long id, long eventTs, double amount) {
    }

    record Payment(long orderId, long eventTs) {
    }

    /** 区间 join：支付需落在订单后 30 分钟内（秒） */
    static final long INTERVAL_SEC = 1800;

    static void intervalJoin() {
        System.out.println("【区间 join：订单流 × 支付流】");
        List<Order> orders = List.of(
                new Order(1, 100, 199.0),
                new Order(2, 200, 69.0),
                new Order(3, 300, 129.0));
        List<Payment> payments = List.of(
                new Payment(1, 150),          // 50s 后支付：命中
                new Payment(2, 2400),         // 2200s 后支付：超区间
                new Payment(3, 320));         // 20s：命中
        for (Order o : orders) {
            Payment hit = payments.stream()
                    .filter(p -> p.orderId() == o.id())
                    .filter(p -> p.eventTs() >= o.eventTs()
                            && p.eventTs() <= o.eventTs() + INTERVAL_SEC)
                    .findFirst().orElse(null);
            if (hit != null) {
                System.out.printf("  配对成功：订单%d（%.0f元）<-> 支付（延迟 %ds）%n",
                        o.id(), o.amount(), hit.eventTs() - o.eventTs());
            } else {
                System.out.printf("  未配对：订单%d -> 进入未配对旁路（对账）%n", o.id());
            }
        }
        System.out.println("  左流缓存存活 = 区间长度；水位线推进过区间终点才清理");
        System.out.println();
    }

    static void dimensionLookup() {
        System.out.println("【维表关联：三种实现的取舍】");
        String[][] rows = {
                {"同步查库", "每事件查商品表", "简单，QPS 高时打爆 DB"},
                {"本地缓存+刷新", "热数据常驻，30s 刷新", "首选；短暂不一致可接受"},
                {"维表 CDC 广播", "变更流推到每个算子", "一致性最好；要处理广播状态合并"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-12s %-24s %s%n", r[0], r[1], r[2]);
        }
        record Order2(long id, String cacheVersion, String realPrice, String verdict) {
        }
        List<Order2> cases = List.of(
                new Order2(101, "价格59（30s前）", "59", "一致，正常"),
                new Order2(102, "价格59（刚刷新前）", "49（刚改价）", "短暂不一致：财务口径要声明"));
        for (Order2 c : cases) {
            System.out.printf("  订单%d 缓存=%s 实际=%s -> %s%n",
                    c.id(), c.cacheVersion(), c.realPrice(), c.verdict());
        }
        System.out.println();
    }

    static void stateBudget() {
        System.out.println("【join 的状态预算】");
        long qps = 1000, intervalSec = 1800;
        long cacheSize = qps * intervalSec;
        System.out.printf("  左流 %d QPS × 区间 %ds = %d 万条在缓存%n",
                qps, intervalSec, cacheSize / 10000);
        System.out.printf("  每条 200B -> 约 %d MB 状态（RocksDB 可扛，但要监控）%n", cacheSize * 200 / 1024 / 1024);
        System.out.println("  缩减手段：缩短区间（业务允许吗）、预过滤（只缓存未支付订单）");
    }

    public static void main(String[] args) {
        intervalJoin();
        dimensionLookup();
        stateBudget();
    }
}
```

**课后练习**
1. 实现区间 join 的完整模拟：含"未配对旁路"与"缓存清理（水位线推进）"，用 1000 单验证三种输出（配对/左等待/超时）的比例。
2. 对比维表关联三种实现：以商品改价为测试用例，观察同步查库/本地缓存/CDC 广播在一致性上的差异。
3. 思考题：为什么 join 缓存的清理必须由水位线驱动而不是定时器？从乱序数据"回来配对"的可能性推导。
