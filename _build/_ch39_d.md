### 39-10 冷热分离与数据归档

**知识点**
- **80/20 法则在业务数据上同样成立**：绝大多数系统 80% 以上的行是"一年内没被读过"的冷数据，却占着主表的 B+ 树、缓存与备份窗口。**归档是性价比最高的"分库分表替代方案"**——很多声称需要分表的系统，归档完主表直接回到千万行以内。
- **冷热分离的判定标准**：访问频率（近 90 天命中率）、查询模式（是否必须与热数据同表查询，如"最近三个月订单"）、法规要求（交易数据保留 N 年）。冷数据特点：只读、查询低频且通常按时间批量。
- **三种归档形态**：**同库归档表**（orders -> orders_archive，最简单，但同一实例仍有压力）；**归档库/实例**（独立实例，主库直接瘦身，最常用）；**对象存储/数据仓库**（压缩转储 + 查询走数仓，适合超长保留期）。按"查询需求有多强"选择。
- **归档任务的正确姿势**：按时间批次（每天搬一天前已完结订单），`INSERT INTO archive SELECT ... LIMIT N` + `DELETE ... LIMIT N` 分批执行，**绝不一个事务搬千万行**（大事务锁表 + 主从延迟爆炸）。搬运要有进度表与断点续跑。
- **一致性窗口**：搬走的那一刻起，查询"历史订单"要去归档库——**应用层路由必须先上线**（按时间判断查主表还是归档库），否则用户查不到已归档订单。稳妥顺序：双查期（先查主表查不到再查归档）→ 验证 → 停双查。
- **归档不是删除**：法规要求的保留期要真保留；物理删除要有不可逆的备份（对象存储 + 校验和）。"归档完就从备份里也删掉"是重大合规事故。
- 易错点：归档任务无监控（半夜失败没人发现，主表继续膨胀）；归档后索引没同步建（归档库查询慢）；把"可能还要用的数据"归档到查询极慢的存储（用户投诉）；回捞流程没演练（真要恢复一条订单时手忙脚乱）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.List;

public class HotColdArchive {
    record Order(long id, String status, long createTime) {
    }

    static final long ARCHIVE_BEFORE = 1704000000000L;   // 2024-01-01

    static boolean isCold(Order o) {
        return o.createTime() < ARCHIVE_BEFORE;
    }

    static List<Order> hot = new ArrayList<>();
    static List<Order> archive = new ArrayList<>();

    static void initData() {
        hot.clear();
        archive.clear();
        // 前 499 条在归档线之前（冷），其余在归档线之后（热）
        for (long i = 1; i <= 1000; i++) {
            long t = ARCHIVE_BEFORE + (i - 500) * 900_000L;
            hot.add(new Order(i, i % 5 == 0 ? "DONE" : "PAID", t));
        }
        System.out.printf("  初始主表 %d 行（其中 499 行是归档线之前的冷数据）%n", hot.size());
    }

    static void batchArchive() {
        System.out.println("【分批归档：每批 N 行，绝不一个事务搬全量】");
        initData();
        int batchSize = 100, moved = 0, round = 0;
        while (round < 20) {
            List<Order> batch = new ArrayList<>();
            for (Order o : hot) {
                if (isCold(o) && batch.size() < batchSize) {
                    batch.add(o);
                }
            }
            if (batch.isEmpty()) {
                break;
            }
            hot.removeAll(batch);
            archive.addAll(batch);
            moved += batch.size();
            round++;
            System.out.printf("  批次 %2d：搬走 %d 行，主表剩 %d 行，归档 %d 行%n",
                    round, batch.size(), hot.size(), archive.size());
        }
        System.out.printf("  完成：主表 %d 行（瘦了 %.0f%%），归档库 %d 行%n",
                hot.size(), 100.0 * moved / (moved + hot.size()), archive.size());
        System.out.println("  SQL 模板：INSERT INTO orders_archive SELECT ... WHERE id BETWEEN a AND b;");
        System.out.println("          DELETE FROM orders WHERE id BETWEEN a AND b;（同一事务，每批 1000）");
        System.out.println();
    }

    static void queryRouting() {
        System.out.println("【查询路由：应用必须知道冷热边界】");
        Order recent = hot.get(hot.size() - 1);
        Order old = archive.get(0);
        Order probe = recent;
        String route = isCold(probe) ? "归档库" : "主库";
        System.out.printf("  查订单 %d（%s）-> 路由：%s -> %s%n",
                probe.id(), isCold(probe) ? "冷" : "热", route, "命中");
        probe = old;
        route = isCold(probe) ? "归档库" : "主库";
        System.out.printf("  查订单 %d（%s）-> 路由：%s -> %s%n",
                probe.id(), isCold(probe) ? "冷" : "热", route, "命中");
        System.out.println("  过渡期双查：主表查不到 -> 归档库兜底 -> 两边都有以主表为准");
        System.out.println("  上线顺序：路由改造先于归档任务，禁止先搬数据再改代码");
        System.out.println();
    }

    static void checklist() {
        System.out.println("【归档上线检查单】");
        List<String> items = List.of(
                "冷热判定规则经业务确认（法务：保留期）",
                "归档库索引按查询模式建（时间+订单号，不是照抄主表）",
                "应用路由/双查逻辑已上线并验证",
                "批次大小压测过（主从延迟在阈值内）",
                "进度表 + 失败重跑 + 归档告警",
                "回捞流程演练过一次（归档 -> 主库恢复单条）",
                "备份与合规：归档数据保留与销毁策略书面化");
        for (String i : items) {
            System.out.println("  [ ] " + i);
        }
    }

    public static void main(String[] args) {
        batchArchive();
        queryRouting();
        checklist();
    }
}
```

**课后练习**
1. 写一个真实归档脚本（SQL 或程序）：分批搬运 + 进度记录 + 失败重跑，在测试库跑通并记录每批的主从延迟变化。
2. 复现"先归档后改路由"的事故：归档一批数据但应用仍查主表，观察用户视角现象，然后按正确顺序重做。
3. 思考题：为什么归档库的索引通常不需要照抄主表？从冷数据的查询模式推导索引设计原则。

---

### 39-11 综合案例：订单系统从单表到分库分表

**知识点**
- **完整演进路线回顾**：慢查询治理（39-02）→ 深分页优化（39-03）→ 读写分离（39-04）→ 缓存挡读 → 历史归档（39-10）→ 仍然撑不住 → 水平分库分表（39-05/06）→ 分布式 ID（39-07）→ 跨分片查询治理（39-08）→ 跨库一致性（39-09）。**每一步都有前置条件与回退方案**，不是一步跳到分库分表。
- **最终架构**：4 库 × 8 表（32 分片），分片键 user_id（基因法嵌入 order_id），一套订单库承载 C 端主路径；商家维度走冗余索引表；后台统计走异构（binlog 同步到数仓）；历史订单按月归档到归档库。**每个查询维度都有明确归宿，没有广播查询**。
- **ID 设计落地**：雪花 64 位改造——41 位时间戳 + 4 位库号 + 6 位表号基因（取 user_id 哈希低 10 位）+ 13 位序列，既全局唯一又能反解路由。**ID 即路由信息**，任何分片只要拿到 ID 就知道数据在哪。
- **迁移方案（双写三阶段）**：① 存量迁移：按 id 范围分批搬历史数据到 32 分片，校验和比对；② 增量双写：新旧库同写（旧为准），对账任务持续比对差异；③ 切读：灰度按用户尾号切读流量到新库，观察一周全量切换，旧库降级为只读备份一个月后下线。**每阶段都有回退开关**。
- **验收指标**：单用户订单查询 P99 < 50ms（单分片）；下单链路 P99 < 300ms（含跨库扣库存）；无广播查询（后台报表全部走数仓）；对账差异为零；主从延迟稳定 < 2s。
- **回归测试清单**：所有高频 SQL 逐条确认路由（单分片/基因/索引表/异构）；深分页全部改游标；事务边界重新梳理（跨库的改本地消息表）；连接池重新评估（32 分片 × 应用实例数的乘积效应）。
- 易错点：迁移期旧库新库时间戳不一致导致对账误报（用业务版本号不用时间）；灰度切读时忘记切写（读新写旧，数据分裂）；连接数爆炸（每应用实例连 32 库，应用实例 20 台就是 640 连接，必须用连接池上限 + Proxy 收敛）；迁移完成立即下线旧库（至少保留一个月只读）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class OrderShardingCase {
    static final int DB_COUNT = 4, TABLE_COUNT = 8, TOTAL = DB_COUNT * TABLE_COUNT;
    static final long GENE_BITS = 10, SEQ_BITS = 12;

    /** 基因路由：user_id 哈希的低 10 位作为基因嵌入 order_id */
    static long geneOf(long userId) {
        return Math.abs(userId * 2654435761L) & 1023;
    }

    record OrderId(long value) {
        /** 41 位毫秒时间戳 | 10 位基因 | 12 位序列 = 63 位 */
        static OrderId of(long userId, long seq) {
            long ts = System.currentTimeMillis() & ((1L << 41) - 1);
            long s = seq & ((1L << SEQ_BITS) - 1);
            return new OrderId((ts << (GENE_BITS + SEQ_BITS)) | (geneOf(userId) << SEQ_BITS) | s);
        }

        long gene() {
            return (value >> SEQ_BITS) & 1023;
        }

        int db() {
            return (int) (gene() % DB_COUNT);
        }

        int table() {
            return (int) (gene() % TABLE_COUNT);
        }
    }

    record Order(OrderId id, long userId, double amount) {
    }

    static void routingMatrix() {
        System.out.println("【32 分片的最终路由矩阵】");
        long userId = 8123;
        long gene = geneOf(userId);
        int db = (int) (gene % DB_COUNT), table = (int) (gene % TABLE_COUNT);
        System.out.printf("  user_id=%d 的基因=%d -> 列表查询路由：库 %d 表 %d（单分片）%n",
                userId, gene, db, table);
        OrderId oid = OrderId.of(userId, 42);
        System.out.printf("  该用户的 orderId=%d -> 反解基因 %d -> 库 %d 表 %d（同一个分片）%n",
                oid.value(), oid.gene(), oid.db(), oid.table());
        System.out.println("  按 orderId 查详情与按 user_id 查列表命中同一分片，两个维度都免广播");
        System.out.println("  商家侧查询 -> 冗余索引表 t_order_by_shop（shop_id 分片）");
        System.out.println("  后台统计 -> binlog -> 数仓（绝不广播生产库）");
        System.out.println("  历史订单 -> 归档库（按月，路由按时间）");
        System.out.println();
    }

    static void migrationPhases() {
        System.out.println("【双写迁移三阶段与回退开关】");
        String[][] phases = {
                {"阶段1 存量搬迁", "按 id 范围分批搬到 32 分片，校验和比对", "失败：清空重来（旧库仍为准）"},
                {"阶段2 增量双写", "新写流量同时进新旧库（旧为准），对账任务跑差", "失败：关双写开关回到单库"},
                {"阶段3 切读灰度", "按用户尾号 1% -> 10% -> 100% 切读新库", "失败：读开关回退旧库，秒级"},
        };
        for (String[] p : phases) {
            System.out.printf("  %-12s %-38s 回退：%s%n", p[0], p[1], p[2]);
        }
        System.out.println("  铁律：任何阶段旧库保持只读前一律是唯一真源；切换后旧库只读保留 30 天");
        System.out.println();
    }

    static void sqlAudit() {
        System.out.println("【SQL 审计：每条查询必须有明确路由】");
        String[][] audits = {
                {"用户查自己的订单列表", "user_id 哈希 -> 单分片", "PASS"},
                {"订单详情页", "orderId 基因反解 -> 单分片", "PASS"},
                {"商家查今日订单", "索引表按 shop_id -> 单分片 + 回主表", "PASS"},
                {"运营按状态导出全量", "binlog 异构数仓", "PASS（禁广播）"},
                {"客服查三天前订单", "时间路由归档库", "PASS"},
        };
        for (String[] a : audits) {
            System.out.printf("  %-18s %-34s %s%n", a[0], a[1], a[2]);
        }
        System.out.println("  审计原则：出现“广播到 32 片”的日常 SQL，就是架构缺陷，不是优化项");
        System.out.println();
    }

    static void acceptance() {
        System.out.println("【验收指标与回归清单】");
        Map<String, String> metrics = new LinkedHashMap<>();
        metrics.put("单用户订单列表 P99", "< 50ms（单分片点查 + 游标分页）");
        metrics.put("下单链路 P99", "< 300ms（含跨库扣库存走本地消息表）");
        metrics.put("主从延迟", "稳定 < 2s，无周期性尖刺");
        metrics.put("对账差异", "连续 7 天为零");
        metrics.put("连接数", "应用实例 × 分片数在连接池预算内");
        for (Map.Entry<String, String> e : metrics.entrySet()) {
            System.out.printf("  %-16s %s%n", e.getKey(), e.getValue());
        }
        System.out.println("  回归：高频 SQL 全量过路由审计；深分页全改游标；跨库事务全部消息化");
    }

    public static void main(String[] args) {
        routingMatrix();
        migrationPhases();
        sqlAudit();
        acceptance();
    }
}
```

**课后练习**
1. 为你的业务画出"从单表到分库分表"的完整演进图（含每一步的触发条件与回退方案），标注哪些步骤在你的场景可以跳过。
2. 实现三阶段迁移模拟：内存双写 + 对账任务 + 灰度切读，故意制造一次对账差异并修复，记录全流程日志。
3. 做一次连接数测算：应用 20 实例 × 32 分片 × 连接池 5，得出总连接数与数据库上限的对比，给出 Proxy 收敛方案。
4. 思考题：为什么"ID 即路由"（基因法）是分库分表后最重要的架构决策之一？从故障排查、缓存键设计、跨系统对账三个场景说明它带来的便利。
