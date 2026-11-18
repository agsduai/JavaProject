### 39-04 读写分离与主从延迟

**知识点**
- **读写分离的本质是"按一致性要求路由"**：主库承担全部写入与强一致读，从库承担报表、列表等可容忍延迟的读。MySQL 一主多从是标配，复制基于 binlog（逻辑复制），从库重放主库的事务流。
- **主从延迟无法归零，只能管理**：单线程回放（老版本）、大事务、从库跑重查询、DDL 都会拉大 `Seconds_Behind_Master`。典型场景：写库成功 → 立即读从库 → **读不到刚写的数据**，用户看到"订单消失"，客服工单随即到达。
- **强一致读必须回主库**：写后立即读（下单成功页）、支付回调校验、唯一性检查，都走主库。实现手段：代码里用路由注解强制主库、或同一请求内写后读绑定主库（sticky routing，几秒内该用户请求全走主）。
- **半同步复制是折衷**：异步复制主库提交即返回（可能丢最近事务）；半同步要求至少一个从库收到 binlog 才算提交成功，牺牲一点写延迟换 RPO 收敛；组复制（MGR）更严格但成本高。
- **延迟的工程对策**：① 关键路径强制主库；② 版本号校验（读到的数据版本 < 本次写入版本就重试/回主库）；③ 从库只服务"不需要最新数据"的查询（历史订单、报表）；④ 监控 Seconds_Behind_Master 告警阈值（如 >10s）。
- **路由实现层次**：框架层（Spring 的 AbstractRoutingDataSource / ShardingSphere 的主从分离）、代理层（ProxySQL/MaxScale）、云上直接买数据库代理。应用侧要做的核心是把"读写意图"表达清楚。
- 易错点：写后读走了从库（下单页空白）；把报表大查询放主库（拖慢交易）；从库数量无脑加（每多一个从库主库 binlog 线程压力都增加，且延迟更难收敛）；故障切换后应用连接串没切（脑裂期双写）。

**示例代码**
```java
import java.util.HashMap;
import java.util.Map;

public class ReadWriteSplit {
    /** 简化数据源：主库实时，从库带固定延迟 */
    static class DataSource {
        final String name;
        final long lagMs;
        final Map<String, String> data = new HashMap<>();

        DataSource(String name, long lagMs) {
            this.name = name;
            this.lagMs = lagMs;
        }

        void write(String key, String value, long nowMs) {
            data.put(key, value + "@" + nowMs);
        }

        String read(String key, long nowMs) {
            String v = data.get(key);
            if (v == null) {
                return null;
            }
            long writtenAt = Long.parseLong(v.substring(v.indexOf('@') + 1));
            return (nowMs - writtenAt) >= lagMs ? v : null;   // 还没复制过来 -> 读不到
        }
    }

    static DataSource master = new DataSource("master", 0);
    static DataSource replica = new DataSource("replica-1", 2000);   // 延迟 2 秒

    static void lagProblem() {
        System.out.println("【主从延迟事故：下单成功页读不到订单】");
        long t = 0;
        master.write("order:1001", "PAID", t);
        String fromReplica = replica.read("order:1001", t);
        System.out.println("  t+0ms   主库写入 order:1001=PAID");
        System.out.println("  t+50ms  用户跳转订单页 -> 读从库 -> "
                + (fromReplica == null ? "null（读不到！页面空白）" : fromReplica));
        System.out.println("  t+2000ms 从库追平 -> 读到 PAID");
        System.out.println("  用户感知：钱扣了，订单没了 -> 客服工单 +1");
        System.out.println();
    }

    static void stickyRouting() {
        System.out.println("【对策一：写后读绑定主库（sticky routing）】");
        long writeAt = 1000;
        master.write("order:1002", "PAID", writeAt);
        long readAt = writeAt + 300;
        boolean sticky = readAt - writeAt < 5000;
        String read = sticky ? master.read("order:1002", readAt)
                : replica.read("order:1002", readAt);
        System.out.printf("  写于 %dms，读于 %dms，窗口 %dms 内 -> %s%n",
                writeAt, readAt, 5000, sticky ? "路由主库" : "路由从库");
        System.out.println("  读到：" + read);
        System.out.println("  实现：会话级标记，写操作后 N 秒内该用户读请求全走主库");
        System.out.println();
    }

    static void versionGuard() {
        System.out.println("【对策二：版本号校验，读旧了就重试主库】");
        long writeAt = 5000;
        master.write("order:1003", "PAID", writeAt);
        String fromReplica = replica.read("order:1003", writeAt + 100);   // 未复制完 -> null
        if (fromReplica == null) {
            System.out.println("  从库尚未复制到该版本 -> 回退主库读");
            System.out.println("  主库读到：" + master.read("order:1003", writeAt + 100));
        }
        System.out.println("  实现细节：写入时记录版本号，读到旧版本（写时间 < 我写的版本）就回主库");
        System.out.println("  适合点查（订单详情），不适合列表（无法对每行做版本比较）");
        System.out.println();
    }

    static void routingMatrix() {
        System.out.println("【路由决策表：把一致性要求说清楚】");
        String[][] rows = {
                {"写入", "主库", "唯一写入点"},
                {"写后立即读", "主库", "下单页、支付回调、唯一性校验"},
                {"列表搜索", "从库", "延迟 2~5s 可接受，产品话术兜底"},
                {"报表/导出", "从库或专用从库", "大查询隔离，别污染交易链路"},
                {"后台管理", "从库", "运营查看允许少量延迟"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-10s -> %-14s %s%n", r[0], r[1], r[2]);
        }
    }

    public static void main(String[] args) {
        lagProblem();
        stickyRouting();
        versionGuard();
        routingMatrix();
    }
}
```

**课后练习**
1. 本机搭主从复制（或用 docker-compose），写入后立即读从库复现"读不到"，记录 Seconds_Behind_Master 变化曲线。
2. 为你的项目实现主从路由：给"下单成功页"加 sticky routing，用接口测试证明写后读不再空白。
3. 思考题：为什么列表页适合从库而点查不适合？从"能否对单行做版本比较"的角度分析两种对策的适用边界。

---

### 39-05 分库分表的拆分策略

**知识点**
- **垂直拆分按业务切，水平拆分按数据切**：垂直分库把订单、商品、用户拆成独立库（第 29 章的服务拆分在数据层的投影）；垂直分表把大字段、低频字段拆出去（订单表拆出 order_detail 的大文本）。水平分表把同一张表按分片键切成 N 张同构表。
- **水平拆分先解决"分片键选谁"**：分片键决定了数据分布，也决定了**所有查询的形状**。选 `user_id`：同一用户的订单在一起，"查某用户的订单"走单分片，但"平台按时间查全部订单"要扫全分片。选 `order_id`：单订单查询快，但用户侧列表要广播。**没有完美分片键，只有业务主路径的取舍**。
- **基因法让多维度都能命中单分片**：把 user_id 的低位"基因"嵌进 order_id（如 order_id = 时间戳 | user_id 低 8 位 | 序列），按 order_id 查和按 user_id 查都路由到同一分片。这是 C 端系统最常用的技巧，39-07 的分布式 ID 会实现它。
- **冗余一份"异构索引表"解决第二个维度**：下单时同时写 order 表（按 user_id 分片）与 order_by_shop 索引表（按 shop_id 分片），商家侧查索引表拿 order_id 列表再回主表。用"一次多写"换"多维度单分片查询"，代价是写放大与一致性维护。
- **不参与分片的查询怎么办**：后台报表、风控全量扫描这类查询不追求实时，走**异构副本**（同步到 ES/数仓/未分片的从库）而不是扫全分片。广播查询是分库分表的头号性能陷阱。
- **中间件两种形态**：SDK 模式（ShardingSphere-JDBC，嵌入应用，无中间层，性能好但升级随应用）与 Proxy 模式（独立代理，语言无关，多一跳延迟）。无论哪种，**跨分片 join、跨分片事务、聚合归并**都是它们的软肋，设计时绕开而不是硬用。
- 易错点：用自增主键当分片键还希望按用户查（必然广播）；分片键选了会变数据的字段（用户换城市？订单改状态？迁移地狱）；一张 800 列的大宽表直接水平拆（先把垂直拆完再水平）；分片后仍写 `JOIN` 多表 SQL（跨分片 join 语义与性能都不可控）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ShardingStrategy {
    record Order(long orderId, long userId, double amount) {
    }

    /** 8 张表：t_order_0 ~ t_order_7，按 user_id 路由 */
    static final Map<Integer, List<Order>> SHARDS = new LinkedHashMap<>();
    static final int SHARD_COUNT = 8;

    static void verticalFirst() {
        System.out.println("【拆分顺序：先垂直，后水平】");
        String[][] steps = {
                {"垂直分库", "订单库 / 商品库 / 用户库 物理独立", "对应微服务边界"},
                {"垂直分表", "order 拆出 order_detail(大文本)、order_stat(低频统计)",
                        "热表变窄，缓存友好"},
                {"水平分表", "order -> t_order_0..7（按 user_id）", "解决单表行数与写入热点"},
                {"水平分库", "每 4 张表一个库实例", "解决单机连接数/磁盘/IO 上限"},
        };
        for (String[] s : steps) {
            System.out.printf("  %-8s %-44s %s%n", s[0], s[1], s[2]);
        }
        System.out.println();
    }

    static int routeByUser(long userId) {
        return (int) (userId % SHARD_COUNT);
    }

    static void shardKeyChoice() {
        System.out.println("【分片键选择：主路径决定一切】");
        List<Order> orders = List.of(
                new Order(90001, 101, 99.0),
                new Order(90002, 102, 199.0),
                new Order(90003, 101, 59.0));
        for (Order o : orders) {
            SHARDS.computeIfAbsent(routeByUser(o.userId()), k -> new ArrayList<>()).add(o);
        }
        for (Map.Entry<Integer, List<Order>> e : SHARDS.entrySet()) {
            System.out.printf("  t_order_%d: %s%n", e.getKey(), e.getValue());
        }
        System.out.println("  按 user_id 查：route(101)=" + routeByUser(101)
                + " -> 只查 1 个分片 ✓");
        System.out.println("  按时间查全部：8 个分片全部广播 -> 后台查询应走异构副本");
        System.out.println();
    }

    static void geneEmbedding() {
        System.out.println("【基因法：把 user_id 基因埋进 order_id】");
        long userId = 101;
        long gene = userId & 0xFF;                    // 取 user_id 低 8 位作基因
        long orderId = (1700000000L << 20) | (gene << 12) | 1234;   // 时间戳|基因|序列
        long extracted = (orderId >> 12) & 0xFF;
        System.out.printf("  userId=%d 基因=%d -> orderId=%d%n", userId, gene, orderId);
        System.out.printf("  从 orderId 反提取基因=%d -> 同一路由分片 t_order_%d%n",
                extracted, extracted % SHARD_COUNT);
        System.out.println("  按 orderId 查与按 userId 查命中同一分片，两个维度都免除广播");
        System.out.println();
    }

    static void indexTable() {
        System.out.println("【冗余索引表：第三个维度（商家侧）怎么办】");
        System.out.println("  主表：t_order（按 user_id 分片）");
        System.out.println("  索引表：t_order_by_shop（按 shop_id 分片，只存 shop_id/order_id/user_id）");
        System.out.println("  商家查订单：先查索引表拿 order_id 列表 -> 回主表取详情（仍在单分片）");
        System.out.println("  代价：双写（同事务或本地消息表）、索引表只增不改、一致性对账");
        System.out.println("  原则：宁可写放大，不可查询广播");
    }

    public static void main(String[] args) {
        verticalFirst();
        shardKeyChoice();
        geneEmbedding();
        indexTable();
    }
}
```

**课后练习**
1. 为你的核心业务表确定分片键：列出前 5 个高频查询，标注每个查询在所选分片键下是"单分片"还是"广播"，并给出广播查询的异构方案。
2. 实现基因法 ID：user_id 埋基因生成 order_id，验证两个维度路由到同一分片，写出位段图。
3. 思考题：为什么"宁可写放大，不可查询广播"？从 QPS 与分片数的乘积效应推导广播查询在高峰期的灾难性成本。

---

### 39-06 分片路由与扩容

**知识点**
- **路由算法两大流派**：**hash 取模**（`shard = hash(key) % N`）数据分布均匀，但扩容时几乎全部数据要迁移（模数变了）；**range 范围**（按时间/ID 区间）扩容只加新区间，但热点集中在最新区间，且需要维护区间表。
- **一致性哈希解决"扩容只迁一小部分"**：把分片与 key 都映射到环上，key 归顺时针遇到的第一个节点。节点数从 N 变 N+1 时，只有"环上新节点与前一个节点之间"的区间数据需要迁移（约 1/N+1），其余不动。虚拟节点（每物理节点 100~200 个虚拟槽位）让分布更均匀。
- **固定槽数（slot）方案是工程主流**：不直接对节点取模，而是对 1024/4096 个槽取模，槽再映射到节点。扩容时只调整"槽→节点"映射（迁移部分槽的数据），路由公式永远不变。Redis Cluster 的 16384 slot 就是这个思想。
- **扩容动作三步**：① 新节点加入，重新分配槽；② 按映射双写（新数据同时写新旧槽位）；③ 后台迁移存量数据，迁完切读、下线旧槽。**全程不停服**的关键是双写期与对账。
- **翻倍扩容最省心**：8 分片 -> 16 分片时，旧分片 i 的数据一半搬到 i+8（`hash % 16` 与 `hash % 8` 的关系保证只迁一半且目标可计算）。所以**分片数从一开始就选 2 的幂**，给翻倍留好路。
- **range 按时间分片的天然搭档**：订单按月分表，最新表承载写入热点，历史表归档——查询带时间条件直接路由单表。适合"时间维度主导"的业务（日志、交易流水），不适合"最新数据最热且查询不带时间"的场景。
- 易错点：直接 `% N` 后期扩容（全量迁移，等于重做一遍）；一致性哈希不配虚拟节点（分布倾斜）；迁移期间只双写不回滚旧写（故障时丢数据窗口）；按 range 分片但查询不带 range 条件（退化为广播）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class ShardRouting {
    /** hash 取模：8 分片 */
    static int modRoute(long key, int n) {
        return (int) (Math.abs(hash(key)) % n);
    }

    static long hash(long key) {
        return key * 31 + 7;
    }

    static void expansionCost() {
        System.out.println("【取模扩容的迁移成本】");
        int n = 1_000;
        int moved = 0;
        for (long i = 1; i <= n; i++) {
            if (modRoute(i, 8) != modRoute(i, 16)) {
                moved++;
            }
        }
        System.out.printf("  8 分片 -> 16 分片：%d%% 的数据换了分片%n", moved);
        moved = 0;
        for (long i = 1; i <= n; i++) {
            if (modRoute(i, 8) != modRoute(i, 9)) {
                moved++;
            }
        }
        System.out.printf("  8 分片 -> 9  分片：%d%% 的数据换了分片（几乎全部）%n", moved);
        System.out.println("  结论：扩容要翻倍（8->16），不翻倍等于重做数据分布");
        System.out.println();
    }

    /** 一致性哈希：虚拟节点 + TreeMap 环 */
    static class ConsistentHash {
        final TreeMap<Long, String> ring = new TreeMap<>();

        ConsistentHash(List<String> nodes, int vnodes) {
            for (String n : nodes) {
                for (int v = 0; v < vnodes; v++) {
                    ring.put(Math.abs(hash(n.hashCode() * 1000L + v)), n);
                }
            }
        }

        String route(long key) {
            Map.Entry<Long, String> e = ring.ceilingEntry(hash(key));
            return e != null ? e.getValue() : ring.firstEntry().getValue();
        }

        long hash(long key) {
            return key * 1103515245L + 12345;
        }
    }

    static void consistentHashDemo() {
        System.out.println("【一致性哈希：加一个节点只迁 1/(N+1)】");
        ConsistentHash c3 = new ConsistentHash(List.of("db1", "db2", "db3"), 100);
        ConsistentHash c4 = new ConsistentHash(List.of("db1", "db2", "db3", "db4"), 100);
        int moved = 0, total = 10_000;
        for (long i = 0; i < total; i++) {
            if (!c3.route(i).equals(c4.route(i))) {
                moved++;
            }
        }
        System.out.printf("  %d 个 key 从 3 节点扩到 4 节点：迁移 %d%%（理论 25%%）%n",
                total, moved / (total / 100));
        for (long probe : new long[]{7, 77, 777}) {
            System.out.printf("  key %d: 3节点=%s -> 4节点=%s%n",
                    probe, c3.route(probe), c4.route(probe));
        }
        System.out.println("  虚拟节点 100 个/台：让环上分布均匀，避免某台独占半环");
        System.out.println();
    }

    static void slotPlan() {
        System.out.println("【固定槽位：路由公式永不变，扩容只改映射】");
        int slots = 1024;
        Map<Integer, String> slotMap = new LinkedHashMap<>();
        for (int s = 0; s < slots; s++) {
            slotMap.put(s, s < 512 ? "db-a" : "db-b");
        }
        long key = 9527;
        int slot = (int) (Math.abs(hash(key)) % slots);
        System.out.printf("  key %d -> slot %d -> 节点 %s%n", key, slot, slotMap.get(slot));
        System.out.println("  扩容 db-c：把 slot 512~682 划给它，只迁移这 171 个槽的数据");
        System.out.println("  双写期：写旧槽+新槽，对账完成后切读，停旧槽");
        System.out.println("  Redis Cluster(16384 slot) 与部分分库中间件都是此方案");
    }

    static void rangeByTime() {
        System.out.println("【range 按月分表：热点集中但扩容无痛】");
        String[][] months = {{"2026-01", "orders_202601（300 万行，仍在写）"},
                {"2026-02", "orders_202602（280 万行，仍在写）"},
                {"2025-12", "orders_202512（320 万行，只读可归档）"}};
        for (String[] m : months) {
            System.out.printf("  %s -> %s%n", m[0], m[1]);
        }
        System.out.println("  查询带时间条件 -> 直接定位单表；跨月查询 -> 联合多表（有限广播）");
        System.out.println("  新增一个月 = 加一张表，无需迁移任何存量数据");
    }

    public static void main(String[] args) {
        expansionCost();
        consistentHashDemo();
        slotPlan();
        rangeByTime();
    }
}
```

**课后练习**
1. 用示例代码实测：10 万 key 从 8 分片扩到 16 分片与 9 分片各迁移多少，验证"翻倍只迁一半"。
2. 实现一个 4096 槽的双节点槽位表，扩容到三节点（划出 1/3 槽位），写出迁移的槽位区间与对账步骤。
3. 思考题：hash、range、slot 三种方案分别适合什么业务形态？给出你所在项目的主表选型与理由。
