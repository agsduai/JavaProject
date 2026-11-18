### 39-07 分布式 ID：雪花算法

**知识点**
- **分库分表后自增主键必然冲突**：两台机器各发 1、2、3，合并就是重复。业务需要**全局唯一且趋势递增**的 ID——递增保证 B+ 树顺序插入（随机主键会导致页分裂与缓存抖动），唯一保证路由与合并正确。
- **雪花算法（Snowflake）的三段结构（64 位）**：1 位符号恒 0 + 41 位毫秒时间戳（约可用 69 年）+ 10 位机器 ID（5 位数据中心 + 5 位机器，或全给机器=1024 台）+ 12 位序列号（同一毫秒内最多 4096 个）。**每毫秒每机器可发 4096 个 ID，每秒约 400 万**。
- **机器 ID 的分配是运维问题**：写死在配置里会人工重复；常用方案：ZooKeeper 顺序节点、数据库注册表、K8s StatefulSet 序号。**同一毫秒同一机器 ID 才可能撞**，机器 ID 冲突 = 灾难，必须中心化分配。
- **时钟回拨是雪花算法的头号故障**：NTP 校时把机器时间拨回去，时间戳变小，会发出与过去重复的 ID。对策：① 回拨 < 5ms 忙等追平；② 回拨大则报错拒发（拒绝服务优于数据重复）；③ 用"上次时间戳"维护单调性，`timestamp = max(now, lastTs)`。
- **序列号用尽**：单机单毫秒超过 4096 个请求，序列溢出——标准做法是**自旋等待下一毫秒**（把时钟推进到 lastTs+1）。高并发场景这是正常分支，不是异常。
- **趋势递增不等于严格递增**：多机器并发时，后发出的 ID 可能小于另一台刚发出的 ID（时间戳精度是毫秒）。排序场景要么容忍（按 create_time 排），要么牺牲并发换严格递增（号段模式）。
- **号段模式（Leaf）作为对照**：数据库里存当前最大值，应用一次取一段（如 1000 个）缓存在内存里用完再取。实现简单、ID 短，但取号段时重启会"跳号"，且强依赖数据库可用性。双 buffer 预取消除取号延迟。
- 易错点：机器 ID 重复（两台机器 ID 相同同时发号）；忽略时钟回拨（NTP 一定会来）；把雪花 ID 当严格排序键（跨机器不严格）；41 位时间戳的起始纪元乱设导致 69 年寿命缩水。

**示例代码**
```java
public class DistributedId {
    /** 雪花算法可运行实现：标准位分配 1+41+10+12 */
    static class Snowflake {
        private static final long EPOCH = 1735689600000L;  // 2025-01-01 起始纪元
        private static final long WORKER_BITS = 10, SEQ_BITS = 12;
        private static final long MAX_WORKER = (1L << WORKER_BITS) - 1;
        private static final long MAX_SEQ = (1L << SEQ_BITS) - 1;

        private final long workerId;
        private long lastTs = -1;
        private long seq = 0;

        Snowflake(long workerId) {
            if (workerId < 0 || workerId > MAX_WORKER) {
                throw new IllegalArgumentException("workerId 超范围: " + workerId);
            }
            this.workerId = workerId;
        }

        synchronized long nextId() {
            long now = System.currentTimeMillis();
            if (now < lastTs) {                       // 时钟回拨
                long rollback = lastTs - now;
                if (rollback <= 5) {                  // 小回拨：忙等追平
                    while (System.currentTimeMillis() <= lastTs) {
                        Thread.onSpinWait();
                    }
                    now = System.currentTimeMillis();
                } else {                              // 大回拨：拒发，宁停勿重
                    throw new IllegalStateException("时钟回拨 " + rollback + "ms，拒绝发号");
                }
            }
            if (now == lastTs) {
                seq = (seq + 1) & MAX_SEQ;            // 同毫秒序列递增
                if (seq == 0) {                       // 序列用尽：等到下一毫秒
                    while (System.currentTimeMillis() <= lastTs) {
                        Thread.onSpinWait();
                    }
                    now = System.currentTimeMillis();
                }
            } else {
                seq = 0;
            }
            lastTs = now;
            return ((now - EPOCH) << (WORKER_BITS + SEQ_BITS))
                    | (workerId << SEQ_BITS)
                    | seq;
        }

        static String dissect(long id) {
            long ts = (id >> (WORKER_BITS + SEQ_BITS)) + EPOCH;
            long worker = (id >> SEQ_BITS) & MAX_WORKER;
            long seq = id & MAX_SEQ;
            return String.format("时间戳=%d worker=%d seq=%d", ts, worker, seq);
        }
    }

    static void basicIssue() {
        Snowflake sf = new Snowflake(7);
        System.out.println("【同一毫秒连续发号：worker=7】");
        long first = sf.nextId();
        long second = sf.nextId();
        System.out.println("  id1=" + first);
        System.out.println("  id2=" + second);
        System.out.println("  id1 拆解：" + Snowflake.dissect(first));
        System.out.println("  趋势递增：id2 > id1 -> " + (second > first));
        System.out.println();
    }

    static void throughput() {
        System.out.println("【发号吞吐：单机每秒理论上限】");
        long perMs = 4096;
        System.out.printf("  12 位序列 -> 每毫秒 %d 个 -> 每秒约 %d 万个%n",
                perMs, perMs * 1000 / 10000);
        Snowflake sf = new Snowflake(1);
        long t0 = System.nanoTime();
        long[] ids = new long[100_000];
        for (int i = 0; i < ids.length; i++) {
            ids[i] = sf.nextId();
        }
        long ms = Math.max(1, (System.nanoTime() - t0) / 1_000_000);
        System.out.printf("  实测 %d 个耗时 %dms（含跨毫秒等待）%n", ids.length, ms);
        System.out.println();
    }

    static void twoWorkers() {
        System.out.println("【两台机器并发发号：唯一但全局不严格递增】");
        Snowflake w1 = new Snowflake(101), w2 = new Snowflake(202);
        long a = w1.nextId(), b = w2.nextId(), c = w1.nextId();
        System.out.printf("  w1:%d  w2:%d  w1:%d%n", a, b, c);
        System.out.println("  同毫秒时 w1 的第二个 ID 也可能小于 w2 的第一个（毫秒粒度内无全局序）");
        System.out.println("  排序需求：按 create_time + id 双键，或接受「近似有序」");
        System.out.println();
    }

    static void epochNote() {
        System.out.println("【纪元与寿命：41 位时间戳用 69 年】");
        long epoch = 1735689600000L;
        long maxTs = (1L << 41) - 1;
        System.out.printf("  纪元 %d + 2^41ms ≈ 纪元 + %.0f 年%n", epoch, maxTs / 3.15e10);
        System.out.println("  纪元取「项目上线日」而不是 1970，寿命从今天起算 69 年");
    }

    public static void main(String[] args) {
        basicIssue();
        throughput();
        twoWorkers();
        epochNote();
    }
}
```

**课后练习**
1. 实现双 worker 并发发号（各发 1 万个），用 Set 验证无重复，并统计"全局逆序对"数量，体会"趋势递增≠严格递增"。
2. 模拟时钟回拨：改造示例，允许注入时间源，触发 3ms 与 50ms 两种回拨，验证忙等与拒绝两种分支。
3. 实现号段模式（双 buffer）：数据库表模拟号段，对比雪花与号段的 ID 长度、有序性、依赖项。
4. 思考题：为什么"机器 ID 中心化分配"比"配置文件写死"更安全？给出两种自动分配方案与各自的失败模式。

---

### 39-08 跨分片查询与聚合

**知识点**
- **分片后的查询分三类**：① 带分片键 → 单分片，性能不变；② 带分片键以外的唯一索引（如按 order_id 查，路由靠基因）→ 单分片；③ 不带任何路由键（后台统计、全局搜索）→ **广播**，扇出到所有分片再归并。设计目标永远是让查询落在前两类。
- **归并排序是跨分片查询的核心动作**：每个分片返回本地 top N（LIMIT N 不是 LIMIT offset+N —— 分片内也要取够排序数据），协调层做**多路归并**取全局 top N。K 路归并成本 O(K×N)，K 是分片数。第 34 章的堆在这里直接复用：K 个流各出队首进小顶堆。
- **分页归并的陷阱**：全局 `LIMIT 1000, 20` 时每分片要取前 1020 条，页越深每分片成本越高——**跨分片分页继承第 39-03 课的全部问题**。游标分页（全局唯一排序键）在分片场景同样成本恒定，是首选。
- **聚合归并的语义**：COUNT/SUM/MAX 可直接分片值相加/比较（可分解）；AVG 不能直接平均（要 SUM 与 COUNT 分别汇总再算）；COUNT DISTINCT 与 MEDIAN **不可分解**，必须收集明细在协调层重算，数据量大时只能用近似或异构副本。
- **GROUP BY 归并**：每分片按组返回（group, aggValue），协调层按组合并（SUM 相加、AVG 用两元组）。组数多时协调层内存压力上升，组数爆炸的报表应走异构（同步到数仓/ES）而不是实时广播聚合。
- **order by 全局唯一性问题**：按 create_time 排序时各分片时钟独立、同毫秒冲突，归并顺序不稳定——与 search_after 相同，**全局排序键必须附加唯一列（id）兜底**。
- 易错点：以为 `LIMIT 10` 在每个分片取 10 条就够了（排序不均匀时会漏，必须每分片取够 N 且在协调层重排序）；AVG 各分片平均后平均（数学错误）；把管理后台的全量查询直接打在生产分库（应该打异构副本）；跨分片 JOIN 硬上（中间件大多退化为内存 join，先把数据冗余到一起再查）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

public class CrossShardQuery {
    record Order(long orderId, long userId, double amount, long createTime) {
    }

    /** 4 个分片的本地数据（模拟同构表） */
    static List<List<Order>> shards() {
        List<List<Order>> s = new ArrayList<>();
        s.add(List.of(new Order(11, 1, 200, 1001), new Order(12, 2, 350, 1005)));
        s.add(List.of(new Order(21, 3, 500, 1003), new Order(22, 1, 90, 1007)));
        s.add(List.of(new Order(31, 2, 120, 1002), new Order(32, 4, 800, 1006)));
        s.add(List.of(new Order(41, 5, 30, 1004), new Order(42, 3, 660, 1008)));
        return s;
    }

    static void topNMerge() {
        System.out.println("【全局 top 3 金额：K 路归并】");
        List<List<Order>> shards = shards();
        record Entry(Order o, int shard) {
        }
        PriorityQueue<Entry> heap = new PriorityQueue<>(
                Comparator.comparingDouble((Entry e) -> e.o().amount()).reversed());
        // 每个分片本地排序取 top3（真实 SQL：ORDER BY amount DESC LIMIT 3）
        for (int i = 0; i < shards.size(); i++) {
            List<Order> localTop = shards.get(i).stream()
                    .sorted(Comparator.comparingDouble(Order::amount).reversed())
                    .limit(3).toList();
            System.out.printf("  分片%d 本地top: %s%n", i, localTop.stream().map(o -> o.orderId()).toList());
            if (!localTop.isEmpty()) {
                heap.add(new Entry(localTop.get(0), i));
            }
        }
        List<Order> globalTop = new ArrayList<>();
        List<List<Order>> cursors = new ArrayList<>();
        for (int i = 0; i < shards.size(); i++) {
            cursors.add(shards.get(i).stream()
                    .sorted(Comparator.comparingDouble(Order::amount).reversed()).toList());
        }
        while (globalTop.size() < 3 && !heap.isEmpty()) {
            Entry e = heap.poll();
            globalTop.add(e.o());
            int next = cursors.get(e.shard()).indexOf(e.o()) + 1;
            if (next < cursors.get(e.shard()).size()) {
                heap.add(new Entry(cursors.get(e.shard()).get(next), e.shard()));
            }
        }
        System.out.println("  全局 top3: " + globalTop.stream().map(Order::orderId).toList());
        System.out.println("  关键：每分片本地取 LIMIT N（不是 offset+N），协调层归并重排");
        System.out.println();
    }

    static void aggregations() {
        System.out.println("【聚合归并的可分解性】");
        long count = 0;
        double sum = 0, naiveAvgSum = 0;
        Map<Long, Double> sumByUser = new LinkedHashMap<>();
        Map<Long, Long> cntByUser = new LinkedHashMap<>();
        for (List<Order> s : shards()) {
            long c = s.size();
            double sm = s.stream().mapToDouble(Order::amount).sum();
            count += c;
            sum += sm;
            naiveAvgSum += sm / c;                    // 错误做法：平均的平均
            for (Order o : s) {
                sumByUser.merge(o.userId(), o.amount(), Double::sum);
                cntByUser.merge(o.userId(), 1L, Long::sum);
            }
        }
        System.out.printf("  COUNT：分片相加 = %d ✓%n", count);
        System.out.printf("  SUM  ：分片相加 = %.0f ✓%n", sum);
        System.out.printf("  AVG  ：先汇总 SUM/COUNT 再除 = %.1f；平均的平均 = %.1f ✗%n",
                sum / count, naiveAvgSum);
        System.out.println("  COUNT DISTINCT / MEDIAN：不可分解 -> 收集明细重算或近似");
        System.out.println("  GROUP BY 用户：");
        for (Long u : sumByUser.keySet()) {
            System.out.printf("    user %d: sum=%.0f count=%d%n", u, sumByUser.get(u), cntByUser.get(u));
        }
        System.out.println();
    }

    static void pagingRule() {
        System.out.println("【跨分片分页：每分片必须取 offset+N】");
        int offset = 1000, limit = 20;
        System.out.printf("  全局 LIMIT %d, %d -> 每分片取前 %d 条，4 分片共 %d 条%n",
                offset, limit, offset + limit, (offset + limit) * 4);
        System.out.println("  深页成本 = 分片数 × 页深，继承 39-03 的所有问题");
        System.out.println("  首选：全局游标（create_time+id 唯一键），每分片 WHERE 键 > 游标 LIMIT N");
        System.out.println("  兜底：后台深翻页走异构副本（ES/数仓），不占交易库");
    }

    public static void main(String[] args) {
        topNMerge();
        aggregations();
        pagingRule();
    }
}
```

**课后练习**
1. 把 topNMerge 升级为"全局第 2 页（offset=3, limit=3）"，写出每分片需要取多少条，并用数据验证归并结果正确。
2. 实现一个跨分片 GROUP BY（按用户 SUM/COUNT）+ 全局排序取 top2 用户，解释为什么 AVG 必须带 COUNT 归并。
3. 思考题：COUNT DISTINCT 在协调层重算的内存上限如何估算？什么规模后必须改用近似算法或异构副本？

---

### 39-09 跨库事务与一致性

**知识点**
- **分库后单库事务失效**：订单在 A 库、库存扣减在 B 库，本地事务罩不住两个库。可选方案从强到弱：**2PC/XA**（强一致，协调者单点、锁持有时间长，吞吐差）、**TCC**（Try-Confirm-Cancel，业务侵入大但性能好）、**本地消息表**（最终一致，实现简单，最常用）、**Saga**（长流程编排，正反补偿）。
- **本地消息表是 80% 场景的正确答案**：业务表更新与"待发送消息"记录在**同一个本地事务**里提交，后台任务扫描消息表投递到 MQ，下游消费成功回执。保证"本地事务成功则消息一定发出去"，最终一致由重试 + 幂等兜底。第 28 章已讲过 MQ 侧，本章补数据库侧的表结构。
- **TCC 的本质是把"预留"显式化**：Try 冻结库存（扣到 frozen 字段）、Confirm 真实扣减、Cancel 释放冻结。三个接口都要幂等、都要允许空回滚（Cancel 在 Try 未到达时也要成功）、都要防悬挂（Cancel 之后到达的 Try 拒绝）。**业务侵入极大**，只用于资金等强一致场景。
- **Saga 适合长流程**：下单 → 扣库存 → 扣优惠券 → 通知物流，每步有补偿动作，失败时按已执行步骤反向补偿。没有隔离性（中间态可见），需要业务接受"补偿期间数据不一致"。
- **能不分事务就不分**：很多"跨库事务"其实可以通过**调整边界**消除——把强关联的表放同一个库（按用户分片天然做到）、用状态机 + 异步核对替代实时强一致（订单状态 PENDING → 核对任务修复）、先落库后异步。
- **对账是所有最终一致方案的收尾**：定时比对两侧（订单 vs 支付流水、库存 vs 扣减记录），差异进差异表人工/自动修复。**架构承认最终一致，对账保证误差收敛**。
- 易错点：用 XA 硬扛高并发（锁等待拖垮连接池）；TCC 忘写空回滚与防悬挂（Cancel 比 Try 先到，资金状态错乱）；本地消息表只加不扫（消息堆积，下游永远收不到）；补偿动作不幂等（重试一次多扣一次）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CrossDbConsistency {
    /** 本地消息表 */
    record Outbox(long id, String topic, String payload, String status, int retry) {
    }

    static Map<Long, String> orderDb = new LinkedHashMap<>();      // A 库：订单
    static Map<Long, Integer> stockDb = new LinkedHashMap<>();     // B 库：库存
    static List<Outbox> outbox = new ArrayList<>();
    static List<String> audit = new ArrayList<>();

    static void localMessageTable() {
        System.out.println("【本地消息表：一次扣库存的完整闭环】");
        long orderId = 9001, productId = 55;
        // 本地事务 1（A 库）：插入订单 + 写 outbox，原子提交
        orderDb.put(orderId, "CREATED");
        outbox.add(new Outbox(1, "OrderCreated", "order=9001,product=55,qty=1", "PENDING", 0));
        System.out.println("  A 库事务：订单 9001 = CREATED + outbox#1 PENDING（同一事务）");
        // 后台投递
        Outbox m = outbox.get(0);
        audit.add("投递 outbox#1 -> MQ -> 库存服务");
        // 本地事务 2（B 库）：扣库存 + 回执（幂等：按 orderId 判重）
        if (!audit.contains("扣减55 for 9001")) {
            stockDb.merge(productId, -1, Integer::sum);
            audit.add("扣减55 for 9001（幂等键=orderId）");
        }
        outbox.set(0, new Outbox(1, m.topic(), m.payload(), "SENT", m.retry() + 1));
        System.out.println("  B 库事务：库存 55 " + stockDb.get(productId) + "（消费幂等）");
        System.out.println("  outbox 状态：" + outbox.get(0).status() + "，retry=" + outbox.get(0).retry());
        System.out.println("  失败路径：投递失败 retry+1，退避重试；N 次后进死信告警");
        System.out.println();
    }

    static void tccDemo() {
        System.out.println("【TCC：预留 -> 确认/取消，以及空回滚】");
        int stock = 10, frozen = 0;
        boolean tryOk = stock >= 1;
        if (tryOk) {
            stock--;
            frozen++;
            System.out.println("  Try：库存 10 -> " + stock + "，冻结 " + frozen + "（预留但不提交）");
        }
        boolean confirm = true;
        if (confirm) {
            frozen--;
            System.out.println("  Confirm：冻结释放，真实扣减完成 -> " + stock);
        } else {
            stock += frozen;
            frozen = 0;
            System.out.println("  Cancel：解冻归还 -> " + stock);
        }
        System.out.println("  空回滚场景：Try 网络丢失，Cancel 先到 -> 必须成功返回（记空标记）");
        System.out.println("  悬挂场景：Cancel 后 Try 才到达 -> 检查空标记直接拒绝");
        System.out.println("  三个接口全部要求幂等 + 幂等键（xid）");
        System.out.println();
    }

    static void reconcile() {
        System.out.println("【对账：最终一致的收敛装置】");
        orderDb.put(9002L, "CREATED");
        // 模拟丢一条消息：库存没扣
        List<Long> paidOrders = new ArrayList<>(List.of(9001L));
        List<Long> deducted = new ArrayList<>(List.of(9001L));
        for (Long id : paidOrders) {
            if (!deducted.contains(id)) {
                audit.add("对账差异：订单 " + id + " 已创建但未扣库存 -> 自动补偿");
            }
        }
        for (String s : audit) {
            System.out.println("  " + s);
        }
        System.out.println("  对账 SQL：两边按业务键 LEFT JOIN，diff 进差异表");
        System.out.println("  修复策略：可自动（补发消息）/ 需人工（金额对不上），分级处理");
    }

    public static void main(String[] args) {
        localMessageTable();
        tccDemo();
        reconcile();
    }
}
```

**课后练习**
1. 实现完整的本地消息表：含 PENDING/SENT/DEAD 状态、指数退避重试、消费幂等键，用并发消费验证不会重复扣减。
2. 实现带空回滚与防悬挂的 TCC 三接口（内存版），构造"Cancel 先到"的时序测试，证明资金状态正确。
3. 思考题：为什么说"调整边界消除事务"优于任何分布式事务方案？举出一个你可以通过按用户分片消除跨库事务的场景。
