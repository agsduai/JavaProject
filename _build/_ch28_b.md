### 28-05 消费幂等：重复消息怎么办

**知识点**
- 至少一次（at-least-once）投递必然带来重复：网络超时重投、消费者 ack 前宕机、Broker 主从切换都可能让同一条消息被消费两次。MQ 普遍不提供"恰好一次"，业务侧必须自己做幂等。
- 幂等的判定基准是**业务唯一键**，不是消息 ID：同一条消息可能被重新包装（重试时 msgId 变了），但"订单号 + 事件类型"永远唯一。
- 三种落地方式：**去重表**（唯一索引，插入成功才处理）、**状态机**（只允许 `待支付 → 已支付`，重复的"已支付"事件直接丢弃）、**Redis SETNX**（短窗口去重，适合高频事件）。
- 去重表要和处理逻辑在**同一个事务**里，否则"插入去重记录成功但业务回滚"会让消息被永久丢弃。
- 幂等不是"永远只处理一次"，而是"重复处理的结果和只处理一次相同"。查类操作天然幂等，加类操作必须靠唯一键。
- 易错：用 `select` 判断存在再 `insert`（并发下两个线程都查到不存在）；去重记录设置 TTL 太短，超过窗口的重复消息仍会重复执行。

**示例代码**
```java
import java.util.HashSet;
import java.util.Set;

public class ConsumeIdempotent {
    /** 去重表：模拟数据库唯一索引 */
    static final Set<String> IDEMPOTENT_TABLE = new HashSet<>();
    /** 业务表：订单 -> 状态 */
    static final java.util.Map<String, String> ORDER_STATUS = new java.util.HashMap<>();
    static int pointsAdded = 0;

    /** 处理"订单已支付"事件：加积分 + 改状态 */
    static String handlePaid(String orderNo, String eventId) {
        String key = orderNo + ":PAID";
        if (!IDEMPOTENT_TABLE.add(key)) {
            System.out.println("    事件 " + eventId + " 重复投递 => 去重表命中，直接返回上次结果，积分不再增加");
            return "DUPLICATE";
        }
        String status = ORDER_STATUS.get(orderNo);
        if ("PAID".equals(status)) {
            System.out.println("    事件 " + eventId + " 状态已是 PAID => 状态机拦截");
            return "IGNORED";
        }
        ORDER_STATUS.put(orderNo, "PAID");
        pointsAdded += 100;
        System.out.println("    事件 " + eventId + " 首次处理：状态 WAIT_PAY -> PAID，积分 +100（累计 " + pointsAdded + "）");
        return "OK";
    }

    public static void main(String[] args) {
        ORDER_STATUS.put("ORD-1", "WAIT_PAY");
        System.out.println("=== 场景一：同一条消息被重复投递 3 次 ===");
        handlePaid("ORD-1", "msg-a");
        handlePaid("ORD-1", "msg-a-retry");
        handlePaid("ORD-1", "msg-a-retry2");
        System.out.println("  最终状态=" + ORDER_STATUS.get("ORD-1") + "，积分=" + pointsAdded);

        System.out.println();
        System.out.println("=== 场景二：并发两个消费者同时处理（先 select 再 insert 的坑）===");
        System.out.println("  线程A: select 去重表 -> 不存在");
        System.out.println("  线程B: select 去重表 -> 不存在");
        System.out.println("  线程A: insert 成功，加积分");
        System.out.println("  线程B: insert 唯一键冲突 -> 必须捕获异常回滚，而不是继续加积分");
        System.out.println("  结论：靠唯一索引兜底，不能靠先查后插");

        System.out.println();
        System.out.println("=== 场景三：去重记录 TTL 太短 ===");
        System.out.println("  去重记录 1 分钟后过期，而重试队列的延迟是 5 分钟");
        System.out.println("  -> 第 5 分钟的重复消息查不到记录，积分被加了第二次");
        System.out.println("  经验：TTL >= 消息最大重试窗口 + 业务幂等期（常见 24h 或 7d）");
    }
}
```

**课后练习**
1. 把去重表换成"订单状态机"实现，去掉 `IDEMPOTENT_TABLE`，验证重复消息仍被拦截。
2. 说明为什么"先 select 判断再 insert"在并发下会失效，唯一索引在其中起什么作用。
3. 给示例加上"处理失败要回滚去重记录"的逻辑，并解释为什么必须与业务同事务。

### 28-06 顺序消息与延迟消息

**知识点**
- **顺序性**只在"同一业务键"上有意义：同一个订单的"创建 → 支付 → 取消"必须有序，不同订单之间互不影响。
- 全局有序的代价极大（单分区单消费者，吞吐直接掉到单机上限）。正确做法是**分区有序**：按 `orderId % 分区数` 选分区，同一订单永远进同一分区，分区内单线程消费。
- 顺序的三个前提缺一不可：生产端按 key 路由、Broker 单分区内 FIFO、消费端单线程（或多线程但按 key 分派到同一线程）。
- **延迟消息**：下单 30 分钟未支付自动取消、预约提醒、超时重试。实现方式有延迟队列（RabbitMQ 延迟交换机）、定时扫描（扫表，简单但精度差）、时间轮（Kafka 生态常用）。
- 延迟消息的时间精度与堆积量成反比：精度越高，越需要分桶（按分钟/秒分多个队列）。
- 易错：消费端用了线程池却忘了按 key 分派，同一订单被两个线程并发处理，顺序彻底乱了。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class OrderAndDelayMessage {
    static final int PARTITIONS = 3;
    static final List<List<String>> TOPIC = new ArrayList<>();

    static {
        for (int i = 0; i < PARTITIONS; i++) {
            TOPIC.add(new ArrayList<>());
        }
    }

    /** 生产端：按 orderId 哈希选分区，保证同一订单进同一分区 */
    static void send(String orderId, String event) {
        int partition = Math.floorMod(orderId.hashCode(), PARTITIONS);
        TOPIC.get(partition).add(orderId + " " + event);
        System.out.println("  发送 " + orderId + " " + event + " -> 分区 " + partition);
    }

    public static void main(String[] args) {
        System.out.println("=== 分区有序：同一订单固定落同一分区 ===");
        send("ORD-1", "CREATED");
        send("ORD-2", "CREATED");
        send("ORD-1", "PAID");
        send("ORD-1", "SHIPPED");
        send("ORD-2", "CANCELLED");
        send("ORD-1", "FINISHED");

        for (int p = 0; p < PARTITIONS; p++) {
            System.out.println("  分区 " + p + " 顺序消费：" + TOPIC.get(p));
        }

        System.out.println();
        System.out.println("=== 反例：同一订单被分到不同分区 ===");
        List<String> wrong = new ArrayList<>();
        wrong.add("ORD-1 CREATED");
        wrong.add("ORD-1 PAID");
        System.out.println("  两个消费者并行处理：" + wrong);
        System.out.println("  -> PAID 可能先于 CREATED 生效，状态机报非法流转");

        System.out.println();
        System.out.println("=== 延迟消息：下单 30 分钟未支付自动取消 ===");
        Map<String, Long> dueAt = new LinkedHashMap<>();
        long now = 0L;
        dueAt.put("ORD-3", now + 30 * 60_000L);
        dueAt.put("ORD-4", now + 5 * 60_000L);
        System.out.println("  投递延迟消息：ORD-3 在 +30min，ORD-4 在 +5min");
        long[] checkpoints = {5 * 60_000L, 30 * 60_000L};
        for (long cp : checkpoints) {
            System.out.println("  时间推进到 +" + cp / 60_000 + "min，扫描到期消息：");
            for (Map.Entry<String, Long> e : dueAt.entrySet()) {
                if (e.getValue() <= cp) {
                    System.out.println("    到期 " + e.getKey() + " -> 查支付状态，未支付则取消");
                }
            }
        }
    }
}
```

**课后练习**
1. 把分区数改成 5，验证同一订单仍落同一分区，并统计各分区消息量是否均衡。
2. 设计一个"下单 30 分钟未支付自动取消"的定时扫描方案，说明扫描间隔与数据库压力的关系。
3. 解释为什么"消费端用线程池并发消费"会破坏顺序，给出两种修复方式。

### 28-07 消息积压的排查与处理

**知识点**
- 积压先看三个数：**生产速率**、**消费速率**、**当前 lag**。只有生产 > 消费才是真积压，否则只是瞬时抖动。
- 常见原因：消费者逻辑变慢（慢 SQL、同步 RPC 超时）、消费线程数不足、某条消息一直失败在重试、Broker 磁盘/网络瓶颈、分区数太少导致并行度上不去。
- 应急处理顺序：先扩容消费者（不超过分区数）→ 再优化消费逻辑（批量、异步、去掉同步调用）→ 最后才考虑丢弃非核心消息。
- 扩容有上限：Kafka 的并行度上限就是分区数，消费者比分区多出来的实例会空转。所以"预留足够分区"是设计期就要做的决定。
- 长期方案：监控 lag 并告警、消费端加超时熔断、把重逻辑拆成二级异步。
- 易错：一看到积压就重启消费者（重复消费 + 更慢）；盲目加机器但不加分分区；把死循环的消息留在队首反复重试。

**示例代码**
```java
public class BacklogDiagnosis {
    public static void main(String[] args) {
        System.out.println("=== 第一步：判断是真积压还是瞬时抖动 ===");
        int producedPerSec = 1000;
        int consumedPerSec = 400;
        int lag = 50_000;
        System.out.println("  生产 " + producedPerSec + "/s，消费 " + consumedPerSec + "/s，积压 " + lag);
        int net = producedPerSec - consumedPerSec;
        System.out.println("  净增长 " + net + "/s => " + (net > 0 ? "真积压，缺口会持续扩大" : "不是积压"));
        if (net > 0) {
            System.out.println("  按当前速度，" + lag / net + "s 后积压翻倍");
        }

        System.out.println();
        System.out.println("=== 第二步：定位瓶颈 ===");
        String[] causes = {
            "消费线程数 4，分区数 12 -> 并行度没用满，可以扩到 12 个消费者",
            "单条处理 80ms 里 60ms 是同步 RPC -> 改成批量查或本地缓存",
            "有 1 条消息反复失败占住队首 -> 进死信队列，别阻塞后面",
            "Broker 磁盘 IO 打满 -> 扩容或降低刷盘频率"
        };
        for (String c : causes) {
            System.out.println("  - " + c);
        }

        System.out.println();
        System.out.println("=== 第三步：扩容效果测算 ===");
        int partitions = 12;
        int[] consumers = {4, 8, 12, 16};
        for (int c : consumers) {
            int effective = Math.min(c, partitions);
            int capacity = effective * consumedPerSec / 4;
            System.out.println("  消费者 " + c + " 个 -> 有效并行 " + effective
                    + "，消费能力约 " + capacity + "/s"
                    + (c > partitions ? "（多出的 " + (c - partitions) + " 个空转）" : ""));
        }
        System.out.println("  结论：并行度上限 = 分区数，设计期就要预留分区");

        System.out.println();
        System.out.println("=== 第四步：把消费逻辑改成批量 ===");
        int batchSize = 100;
        int perMsgCost = 5;
        int batchOverhead = 200;
        int single = 1000 * perMsgCost;
        int batch = (1000 / batchSize) * (batchOverhead + batchSize * perMsgCost);
        System.out.println("  逐条处理 1000 条耗时 " + single + "ms");
        System.out.println("  批量 100 条/次耗时 " + batch + "ms，提升约 " + single / batch + " 倍");
    }
}
```

**课后练习**
1. 给示例加上"积压告警阈值"，lag 超过 10 万时输出告警，并说明阈值怎么定。
2. 分析"分区 12、消费者 16"时多出的 4 个实例在做什么，为什么不继续扩。
3. 设计一个消费端超时熔断方案：某下游连续超时 N 次后直接失败进死信，避免拖死整个消费者。

### 28-08 延迟队列与定时任务的选型

**知识点**
- 定时任务三种形态：**单机 `@Scheduled`**（简单，多实例会重复执行）、**分布式调度平台**（XXL-JOB/ElasticJob，有分片和失败重试）、**延迟队列**（精确到秒级，适合海量单条任务）。
- 选型看任务特征：固定周期全量扫描（对账、清理）用调度平台；海量单条延迟（订单超时）用延迟队列；少量低频任务用单机定时器 + 分布式锁。
- 延迟队列的三种实现：RabbitMQ 延迟交换机（TTL + DLX）、RocketMQ 延迟等级、Redis ZSet（score 存到期时间戳 + 轮询）。
- Redis ZSet 方案的要点：`ZADD key score member`，消费端 `ZRANGEBYSCORE 0 now LIMIT 0 N` 取到期任务，用 `ZREM` 的返回值（删除成功数）保证多实例下只有一个抢到。
- 定时任务必须幂等：调度平台重试、多实例重叠执行都会导致重复跑。
- 易错：用 `@Scheduled` 做订单超时取消，部署 3 个实例后同一订单被取消 3 次；ZSet 取任务不加 `ZREM` 原子性判断，多实例重复处理。

**示例代码**
```java
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class DelayQueueDesign {
    /** 用 TreeMap 模拟 Redis ZSet：key=到期时间戳，value=任务列表 */
    static final TreeMap<Long, List<String>> ZSET = new TreeMap<>();

    static void addTask(long dueAt, String taskId) {
        ZSET.computeIfAbsent(dueAt, k -> new ArrayList<>()).add(taskId);
        System.out.println("  加入延迟队列：" + taskId + "，到期时间 +" + dueAt + "ms");
    }

    /** 模拟 ZRANGEBYSCORE + ZREM：只有删除成功的实例才执行 */
    static List<String> pollDue(long now, int limit) {
        List<String> got = new ArrayList<>();
        while (got.size() < limit && !ZSET.isEmpty()) {
            Map.Entry<Long, List<String>> first = ZSET.firstEntry();
            if (first.getKey() > now) {
                break;
            }
            List<String> tasks = first.getValue();
            String task = tasks.remove(0);
            got.add(task);
            if (tasks.isEmpty()) {
                ZSET.remove(first.getKey());
            }
        }
        return got;
    }

    public static void main(String[] args) {
        System.out.println("=== 三种定时方案对比 ===");
        System.out.println("  单机 @Scheduled      ：零依赖，多实例重复执行，需要分布式锁");
        System.out.println("  分布式调度平台       ：分片、失败重试、可视化，适合周期性全量任务");
        System.out.println("  延迟队列（ZSet/DLX） ：秒级精度、海量单条任务，适合订单超时");

        System.out.println();
        System.out.println("=== Redis ZSet 延迟队列 ===");
        addTask(30_000, "ORDER-1:cancel");
        addTask(5_000, "ORDER-2:cancel");
        addTask(30_000, "ORDER-3:cancel");
        addTask(60_000, "COUPON-9:expire");

        long[] ticks = {5_000, 30_000, 60_000};
        for (long now : ticks) {
            System.out.println("  时间 +" + now + "ms 轮询：");
            List<String> due = pollDue(now, 10);
            if (due.isEmpty()) {
                System.out.println("    无到期任务");
            }
            for (String t : due) {
                System.out.println("    实例A 抢到并执行 " + t);
            }
        }

        System.out.println();
        System.out.println("=== 多实例竞争：ZREM 返回值决定谁执行 ===");
        ZSET.clear();
        addTask(1_000, "ORDER-4:cancel");
        System.out.println("  实例A 执行 ZREM -> 返回 1，抢到，执行取消");
        System.out.println("  实例B 执行 ZREM -> 返回 0，说明已被抢走，直接跳过");
        System.out.println("  剩余队列=" + ZSET);
        System.out.println("  注意：任务本身仍要幂等，ZREM 只保证不重复抢，不保证业务只成功一次");
    }
}
```

**课后练习**
1. 把 `TreeMap` 换成 `ConcurrentSkipListMap` 并加多线程轮询，验证不会重复取到同一任务。
2. 说明为什么"单机 `@Scheduled` + 3 个实例"会重复执行，给出两种修复方式。
3. 对比 RabbitMQ 延迟交换机与 Redis ZSet 的精度、可靠性、运维成本。

### 28-09 事务消息与本地消息表

**知识点**
- 核心矛盾：**数据库事务和消息发送不在同一个事务里**。先发消息再提交事务 → 事务回滚了消息已投出（下游多一条脏数据）；先提交事务再发消息 → 发消息失败（下游永远收不到）。
- **本地消息表**（最常用、最可靠）：业务表和消息表在同一个本地事务里写入，再由定时任务扫描"待发送"记录投递 MQ，投递成功后改状态。靠"本地事务原子性 + 定时补偿"保证最终一致。
- **事务消息**（RocketMQ）：先发半消息 → Broker 存下来但不投递 → 执行本地事务 → 提交/回滚半消息 → Broker 对未知状态回查。本质也是"两阶段 + 回查"。
- 两种方案都要**下游幂等**，因为补偿重投必然带来重复。
- 补偿扫描要有上限和告警：一直发不出去的消息不能无限重试，超过 N 次要人工介入。
- 易错：把消息发送放在 `@Transactional` 方法内但用的是"发送成功即返回"的异步客户端，事务还没提交消息就出去了；补偿任务没有加锁，多实例重复投递。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TransactionMessage {
    /** 业务表 */
    static final Map<String, String> ORDER_TABLE = new LinkedHashMap<>();
    /** 本地消息表：msgId -> 状态 */
    static final Map<String, String> MSG_TABLE = new LinkedHashMap<>();
    /** 模拟 MQ */
    static final List<String> MQ = new ArrayList<>();

    /** 一个本地事务里同时写业务表和消息表 */
    static boolean createOrderInTx(String orderNo, boolean failAfterWrite) {
        try {
            ORDER_TABLE.put(orderNo, "CREATED");
            MSG_TABLE.put("MSG-" + orderNo, "PENDING");
            System.out.println("  事务内：写订单 " + orderNo + " + 写消息 MSG-" + orderNo + "(PENDING)");
            if (failAfterWrite) {
                throw new IllegalStateException("库存不足，回滚");
            }
            System.out.println("  事务提交：订单和消息一起生效");
            return true;
        } catch (RuntimeException e) {
            ORDER_TABLE.remove(orderNo);
            MSG_TABLE.remove("MSG-" + orderNo);
            System.out.println("  事务回滚：" + e.getMessage() + "，订单和消息都不存在");
            return false;
        }
    }

    /** 补偿任务：扫描 PENDING 投递 MQ */
    static int relay() {
        int sent = 0;
        for (Map.Entry<String, String> e : MSG_TABLE.entrySet()) {
            if ("PENDING".equals(e.getValue())) {
                MQ.add(e.getKey());
                e.setValue("SENT");
                sent++;
                System.out.println("  补偿任务投递 " + e.getKey() + " -> MQ，状态 PENDING => SENT");
            }
        }
        return sent;
    }

    public static void main(String[] args) {
        System.out.println("=== 正常路径 ===");
        createOrderInTx("ORD-1", false);
        relay();
        System.out.println("  MQ=" + MQ + "，消息表=" + MSG_TABLE);

        System.out.println();
        System.out.println("=== 业务失败：消息不会漏出去 ===");
        createOrderInTx("ORD-2", true);
        int sent = relay();
        System.out.println("  本轮补偿投递 " + sent + " 条，MQ=" + MQ);
        System.out.println("  -> 事务回滚把消息表记录也回滚了，下游不会收到脏事件");

        System.out.println();
        System.out.println("=== 反例：先发消息再提交事务 ===");
        MQ.add("MSG-ORD-3");
        System.out.println("  发消息成功，MQ=" + MQ);
        System.out.println("  随后事务回滚，订单没建成 -> 下游收到一条不存在的订单事件");

        System.out.println();
        System.out.println("=== 反例：先提交事务再发消息 ===");
        ORDER_TABLE.put("ORD-4", "CREATED");
        MSG_TABLE.put("MSG-ORD-4", "PENDING");
        System.out.println("  订单已提交，此时发消息进程崩溃 -> 消息表留着 PENDING");
        System.out.println("  靠补偿任务兜底：relay() 下次扫描仍会把它投出去");
        relay();
        System.out.println("  最终 MQ=" + MQ + "，消息表=" + MSG_TABLE);
    }
}
```

**课后练习**
1. 给补偿任务加上"最多重试 5 次，超过标记 FAILED 并告警"的逻辑。
2. 画出本地消息表的完整时序：业务写库 → 消息写库 → 提交 → 补偿投递 → 下游消费 → 幂等。
3. 对比事务消息与本地消息表的可靠性、侵入性、运维成本，说明各自适用场景。

### 28-10 消息中间件选型与常见坑

**知识点**
- **RabbitMQ**：延迟队列、死信、优先级支持好，单机吞吐万级，适合业务消息（订单、通知）。**Kafka**：十万级吞吐、分区有序、生态强，适合日志、埋点、流处理，但延迟消息和死信要靠自己实现。**RocketMQ**：事务消息、延迟等级、消息回溯，介于两者之间，适合电商交易链路。
- 选型维度：吞吐、延迟、可靠性语义、顺序性、延迟消息、运维成本、团队熟悉度。不要只看吞吐。
- 常见坑：消息体过大（几百 KB 的 JSON 塞进消息，Broker 压力大且难排查）→ 只传 ID，让消费者回查；主题设计过细（一业务一 topic，运维爆炸）→ 按领域划分。
- 消费端连接数、预取数（prefetch）配置不当会严重影响吞吐：prefetch 太小消费者空转，太大单机吃满。
- 监控必备：生产/消费 TPS、lag、死信数量、重试次数、Broker 磁盘与连接数。
- 易错：生产环境用默认 guest/guest 账号；队列没有设 TTL 导致无限堆积；没有做消息体 schema 版本管理，改字段直接让老消费者反序列化失败。

**示例代码**
```java
import java.util.LinkedHashMap;
import java.util.Map;

public class MqSelection {
    record Mq(String name, int tps, String order, String delayMsg, String txMsg, String fit) { }

    public static void main(String[] args) {
        Map<String, Mq> table = new LinkedHashMap<>();
        table.put("RabbitMQ", new Mq("RabbitMQ", 20_000, "队列内有序", "原生支持", "不支持", "业务消息、通知、延迟任务"));
        table.put("Kafka", new Mq("Kafka", 1_000_000, "分区有序", "需自实现", "支持(事务)", "日志、埋点、流处理"));
        table.put("RocketMQ", new Mq("RocketMQ", 100_000, "分区有序", "延迟等级", "原生支持", "电商交易、订单链路"));

        System.out.println("=== 选型对比 ===");
        System.out.printf("%-12s %-10s %-10s %-10s %-12s %s%n",
                "产品", "吞吐(条/s)", "顺序性", "延迟消息", "事务消息", "典型场景");
        for (Mq m : table.values()) {
            System.out.printf("%-12s %-10d %-10s %-10s %-12s %s%n",
                    m.name(), m.tps(), m.order(), m.delayMsg(), m.txMsg(), m.fit());
        }

        System.out.println();
        System.out.println("=== 常见坑与对策 ===");
        String[][] pitfalls = {
                {"消息体几百 KB 的 JSON", "只传业务 ID，消费者回查；大对象放对象存储"},
                {"一个业务一个 topic", "按领域划分 topic，用 tag/header 区分事件"},
                {"prefetch=1 导致空转", "按消费耗时调整，批量场景调大到几十"},
                {"队列无 TTL 无限堆积", "设置消息 TTL + 最大长度，溢出进死信"},
                {"默认 guest 账号", "独立账号 + vhost 隔离 + 最小权限"},
                {"消息体字段直接改", "加版本号，消费者兼容新旧两版"}
        };
        for (String[] p : pitfalls) {
            System.out.println("  - " + p[0] + " => " + p[1]);
        }

        System.out.println();
        System.out.println("=== 容量估算示例 ===");
        long peakQps = 5_000;
        int msgBytes = 2 * 1024;
        int retentionDays = 3;
        long dailyBytes = peakQps * 3600L * 8 * msgBytes;
        long totalBytes = dailyBytes * retentionDays;
        System.out.println("  峰值 " + peakQps + " qps，消息 " + msgBytes + "B，保留 " + retentionDays + " 天");
        System.out.println("  日增约 " + dailyBytes / 1024 / 1024 / 1024 + "GB，总需约 "
                + totalBytes / 1024 / 1024 / 1024 + "GB（含副本要再乘 3）");
    }
}
```

**课后练习**
1. 为"订单超时取消"和"用户行为埋点"分别选一个 MQ，说明理由。
2. 消息体 500KB、峰值 2000 qps、保留 7 天，估算需要的磁盘容量（含 3 副本）。
3. 列出你在 MQ 监控面板上必须看到的 5 个指标，并说明每个指标异常的典型原因。

### 28-11 综合案例：订单异步履约

**知识点**
- 完整链路：下单（同步扣库存 + 建订单 + 写本地消息表）→ 补偿任务投递 `order.created` → 积分/短信/物流三个消费组各自消费 → 失败重试 → 超限进死信 → 人工补偿。
- 每一步都要有**可观测性**：traceId 贯穿生产与消费，消费日志带 msgId 和业务键，死信有告警。
- 幂等点分布：积分消费按 `orderNo` 去重、短信按 `orderNo + 模板` 去重、物流按 `orderNo` 状态机。
- 降级策略：短信服务长时间不可用时，先落库待发，不阻塞其它消费者；积分可以延迟补，物流必须最终送达。
- 本章所有能力在这个案例里合流：可靠投递（28-03）、手动 ack 与死信（28-04）、幂等（28-05）、顺序（28-06）、本地消息表（28-09）。
- 易错：把三个下游写成一个消费者串行调用，一个慢全慢；消费失败直接吞掉异常，日志里查不到。

**示例代码**
```java
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class OrderAsyncCase {
    static final Map<String, String> ORDER = new HashMap<>();
    static final Map<String, String> MSG = new HashMap<>();
    static final List<String> MQ = new ArrayList<>();
    static final Set<String> POINTS_DEDUP = new HashSet<>();
    static final Map<String, Integer> POINTS = new HashMap<>();
    static final List<String> SMS_SENT = new ArrayList<>();
    static final Map<String, String> LOGISTICS = new HashMap<>();
    static final List<String> DEAD = new ArrayList<>();
    static final Map<String, Integer> RETRY = new HashMap<>();

    static void log(String traceId, String msg) {
        System.out.println("  [" + traceId + "] " + msg);
    }

    /** 下单：同步核心链路 + 本地消息表 */
    static String placeOrder(String traceId, String orderNo, String userId) {
        log(traceId, "扣库存 OK");
        ORDER.put(orderNo, "CREATED");
        MSG.put("MSG-" + orderNo, "PENDING");
        log(traceId, "建订单 " + orderNo + " + 写消息表(PENDING)，同一事务提交");
        return orderNo;
    }

    /** 补偿投递 */
    static void relay(String traceId) {
        for (Map.Entry<String, String> e : MSG.entrySet()) {
            if ("PENDING".equals(e.getValue())) {
                MQ.add(e.getKey());
                e.setValue("SENT");
                log(traceId, "投递 " + e.getKey() + " -> MQ");
            }
        }
    }

    /** 三个消费组分别消费，各自幂等 */
    static void consume(String traceId, String msgId, String orderNo, String userId) {
        // 积分
        if (POINTS_DEDUP.add(orderNo)) {
            POINTS.merge(userId, 100, Integer::sum);
            log(traceId, "积分组：+100，累计 " + POINTS.get(userId));
        } else {
            log(traceId, "积分组：去重命中，跳过");
        }
        // 短信（模拟第一次失败，进重试）
        int attempt = RETRY.merge(msgId + ":sms", 1, Integer::sum);
        if (attempt == 1) {
            log(traceId, "短信组：网关超时，nack 进重试队列（第 1 次）");
        } else {
            SMS_SENT.add(orderNo);
            log(traceId, "短信组：重试成功，已发送（第 " + attempt + " 次）");
        }
        // 物流
        if (!LOGISTICS.containsKey(orderNo)) {
            LOGISTICS.put(orderNo, "WAIT_PICK");
            log(traceId, "物流组：创建发货单，状态 WAIT_PICK");
        } else {
            log(traceId, "物流组：已存在发货单，跳过");
        }
    }

    public static void main(String[] args) {
        String traceId = "trace-8f2a";
        System.out.println("=== 下单 ===");
        String orderNo = placeOrder(traceId, "ORD-20261117-001", "u1001");
        System.out.println("  接口返回成功，用户不用等积分/短信/物流");

        System.out.println();
        System.out.println("=== 补偿任务投递 + 消费 ===");
        relay(traceId);
        for (String msgId : MQ) {
            consume(traceId, msgId, orderNo, "u1001");
        }

        System.out.println();
        System.out.println("=== 重试轮 ===");
        for (String msgId : MQ) {
            consume(traceId, msgId, orderNo, "u1001");
        }

        System.out.println();
        System.out.println("=== 重复投递（模拟 ack 前宕机）===");
        consume(traceId, "MSG-" + orderNo + "-dup", orderNo, "u1001");

        System.out.println();
        System.out.println("=== 最终状态 ===");
        System.out.println("  订单=" + ORDER);
        System.out.println("  消息表=" + MSG);
        System.out.println("  积分=" + POINTS);
        System.out.println("  短信=" + SMS_SENT);
        System.out.println("  物流=" + LOGISTICS);
        System.out.println("  死信=" + DEAD + "（本次无）");
        System.out.println("  结论：重复消息被幂等挡住，失败消息靠重试恢复，核心链路不受下游影响");
    }
}
```

**课后练习**
1. 把短信消费改成"连续失败 3 次进死信"，并打印死信告警。
2. 给案例加上"物流服务整体不可用"的降级：消息暂存，不影响积分和短信。
3. 用一张时序图画出 traceId 从下单到三个消费者的完整流转。
