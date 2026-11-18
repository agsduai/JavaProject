### 46-05 Iceberg 表格式

**知识点**
- **表格式回答"怎么让一堆文件成为一张有事务的表"**：Iceberg 的答案是**元数据清单树**——snapshot（快照）指向 manifest list，manifest 记录数据文件清单与统计。**表状态 = 当前快照指针；提交 = 原子替换指针**——在无原子 rename 的对象存储上实现了事务（46-03 的解法落地）。
- **ACID 能力**：多个写入方并发提交时通过**乐观并发 + 冲突检测**——两个写都基于同一快照，各自产出新快照，先提交者赢，后提交者若与已提交变更的文件冲突则重试。**"写冲突重试"与 45-06 的 txn 语义同源**。
- **时间旅行（time travel）**：任意历史快照可查询——`SELECT ... VERSION AS OF snapshot-42`。**用途**：误操作回滚（指针回退即回滚，秒级）、审计重现（上周报表为什么是这个数）、实验复现。**快照保留策略**决定可回溯窗口与存储成本。
- **隐藏分区（hidden partitioning）**：分区信息在元数据里（`days(ts)`），用户写 SQL 不需要知道分区列——**分区从用户负担变成引擎知识**，分区演进（天 -> 小时）不破坏旧数据。
- **schema 演进**：加列/改列名/删列都不重写数据（旧文件 + 新 schema 元数据兼容映射）——**演进成本从"全表重写"降到"改元数据"**。
- **增量消费**：快照间 diff 即变更流——Flink/Spark 结构化流直接订阅 Iceberg 增量（43-09 CDC 链路的湖端形态）。
- 易错点：快照不过期（存储无限膨胀，保留 7 天 + 定期 expire）；并发写无冲突检测（两个引擎覆盖写同一文件）；把 Iceberg 当普通目录手工改文件（元数据与数据漂移，46-04 同款病）；小文件治理被忽略（写入频繁时 manifest 膨胀）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class IcebergTableFormat {
    record DataFile(String path, long rows, String dateMin, String dateMax) {
    }

    record Snapshot(long id, String parentId, List<DataFile> files, String operation) {
    }

    /** 当前快照指针（原子替换的对象） */
    static long currentSnapshot = 0;
    static Map<Long, Snapshot> snapshots = new LinkedHashMap<>();

    static void commitFlow() {
        System.out.println("【一次提交：写文件 -> 原子替换快照指针】");
        Snapshot s0 = new Snapshot(0, null, List.of(), "init");
        snapshots.put(0L, s0);
        System.out.println("  当前快照 #0（空表）");
        List<DataFile> f1 = List.of(new DataFile("data/part-0001.parquet", 1_000_000,
                "2026-11-17", "2026-11-17"));
        Snapshot s1 = new Snapshot(1, "#0", f1, "append");
        snapshots.put(1L, s1);
        currentSnapshot = 1;
        System.out.println("  写 data/part-0001.parquet（幂等命名）-> 提交快照 #1 <- 原子替换指针");
        List<DataFile> f2 = new ArrayList<>(f1);
        f2.add(new DataFile("data/part-0002.parquet", 900_000, "2026-11-18", "2026-11-18"));
        snapshots.put(2L, new Snapshot(2, "#1", f2, "append"));
        currentSnapshot = 2;
        System.out.println("  第二次提交 -> 快照 #2（指针指向新清单，旧文件不重写）");
        System.out.println();
    }

    static void concurrentWrite() {
        System.out.println("【并发提交：乐观并发与冲突检测】");
        System.out.println("  写入方 A、B 都基于快照 #2：");
        System.out.println("  A 先提交 -> 快照 #3（当前指针 = 3）");
        System.out.println("  B 提交时发现自己基于的 #2 已不是当前快照：");
        boolean conflict = true;   // B 的文件与 A 的变更文件重叠
        System.out.println("    冲突检测：" + (conflict
                ? "文件重叠 -> B 重试：基于 #3 重算后提交 -> 快照 #4"
                : "文件不重叠 -> 直接提交（快照链线性化）"));
        System.out.println("  同一层语义：46-03 的“清单原子替换” + 乐观锁");
        System.out.println();
    }

    static void timeTravel() {
        System.out.println("【时间旅行：回滚与审计】");
        Snapshot s1 = snapshots.get(1L);
        System.out.printf("  VERSION AS OF #1 -> 只看 %d 个文件（11-17 的数据）%n", s1.files().size());
        System.out.println("  误操作回滚：把当前指针从 #4 回退到 #3（原子操作，秒级）");
        currentSnapshot = 3;
        System.out.println("  当前快照 = " + currentSnapshot + "（数据回到发布前状态）");
        System.out.println("  审计重现：“上周报表为什么是 860 万”-> 查当时快照即可复现");
        System.out.println("  保留策略：快照保留 7 天 + 每日一存（expire_snapshots 排期）");
        System.out.println();
    }

    static void evolution() {
        System.out.println("【隐藏分区与 schema 演进】");
        System.out.println("  分区：PARTITIONED BY days(ts) —— SQL 里 WHERE ts >= '...' 自动裁剪");
        System.out.println("  分区演进：天 -> 小时，旧数据保持天分区，新数据小时分区（元数据共存）");
        System.out.println("  schema：新增列 refund_status -> 只改元数据，旧文件缺列按 null 读取");
        System.out.println("  重命名：列改名通过映射完成，无需重写数据");
        System.out.println("  对比 Hive 分区：加列要重建表/重写数据 —— 演进成本数量级差异");
    }

    public static void main(String[] args) {
        commitFlow();
        concurrentWrite();
        timeTravel();
        evolution();
    }
}
```

**课后练习**
1. 实现迷你 Iceberg（快照指针 + 清单 + 乐观并发重试），模拟两个写入方并发提交，验证冲突检测与线性化。
2. 用时间旅行修复一次"误删数据"：构造误操作快照，指针回退恢复，记录回滚耗时并与传统备份恢复对比。
3. 思考题：为什么隐藏分区能把"分区演进"的代价从重写数据降到改元数据？从分区信息存储位置的变化推导。

---

### 46-06 批流一体存储

**知识点**
- **批流写同一张湖表是湖仓一体的试金石**：Flink CDC 持续写入（秒级一批小文件）+ Spark 每日批读——**写入频繁产生小文件**，读取要求文件足够大——矛盾由 **compaction（合并）机制**解决。
- **两种读模式**：**copy-on-write（COW）**——写时重写受影响文件，读永远干净（读快写贵）；**merge-on-read（MOR）**——写入追加 delta 文件，读时合并 base+delta（写快读贵）。**选型看读写比例**：读多写少选 COW，写多读少或延迟敏感选 MOR。
- **小文件的治理节奏**：流写入每分钟一个小文件 × 1440 分钟 = 每天 8.6 万文件/分区——**异步 compaction**（每 10 分钟合并一次 delta，每天把整分区重整为目标大小）是流式湖表的标配运维。
- **快照与 compaction 的关系**：compaction 本身也是一次提交（产生新快照），**读端不受影响**（快照一致性保证读到的始终是完整状态）；合并后旧文件进"待回收"（快照过期后物理删除）。
- **Flink 写湖的一致性**：checkpoint（43-05）成功后才提交 Iceberg 快照——**流作业的 exactly-once 与湖表事务对齐**：checkpoint 间隔 = 数据可见延迟。
- **下游感知**：46-05 的增量消费让下游订阅"compaction 后的新快照"，或者直接消费数据变更（append 流）。**消费模式选择**决定下游时效（分钟级 vs 天级）。
- 易错点：流写入不做 compaction（小文件雪崩，读取越来越慢）；compaction 与写入抢资源（错峰排期）；MOR 的读放大没预估（查询变慢 3~5 倍才发现）；checkpoint 间隔设太长（下游数据可见延迟跟随变长）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.List;

public class LakeStreaming {
    record File(String name, String kind, long sizeMb) {
    }

    static void cowVsMor() {
        System.out.println("【COW vs MOR：读写代价互换】");
        record Write(long baseFiles, long deltaFiles, String mode) {
        }
        List<String> rows = List.of(
                "COW：更新 5% 的行 -> 重写受影响 base 文件（如 30 个 300MB） -> 写贵，读零合并",
                "MOR：更新 5% 的行 -> 只追加 2 个 delta 文件 -> 写便宜，读时合并 base+delta",
                "选型：报表读多写少 -> COW；实时写入延迟敏感 -> MOR + 异步 compaction");
        for (String r : rows) {
            System.out.println("  " + r);
        }
        System.out.println();
    }

    static void compactionSchedule() {
        System.out.println("【流写入的小文件治理：两级合并】");
        List<File> files = new ArrayList<>();
        for (int i = 0; i < 1440; i++) {
            files.add(new File("part-" + i, "delta", 12));
        }
        System.out.printf("  流写入一天：%d 个 delta 文件（每个 %dMB）%n", files.size(), 12);
        long deltaTotal = 1440L * 12;
        System.out.printf("  每 10 分钟异步合并 delta -> 144 个中型文件；每日全量重整 -> 目标 %dMB×N 个%n", 300);
        System.out.printf("  合并后存储不变（%dMB 级），文件数从 %d 降到数十个%n", deltaTotal, files.size());
        System.out.println("  排期原则：compaction 错开业务高峰，限速执行（39-10 分批思想）");
        System.out.println();
    }

    static void checkpointAligned() {
        System.out.println("【Flink 写湖：checkpoint 与快照对齐】");
        List<String> flow = List.of(
                "1) Flink 处理 CDC 事件，数据缓存在算子状态（43-05）",
                "2) checkpoint barrier 到达 -> 状态快照成功",
                "3) 成功后才向 Iceberg 提交本轮数据（产生新快照）",
                "4) checkpoint 失败 -> 本轮不提交，恢复后重放（幂等：按文件名去重）",
                "5) 数据可见延迟 ≈ checkpoint 间隔（43-06 的端到端一致性落地）");
        for (String s : flow) {
            System.out.println("  " + s);
        }
        System.out.println("  实践：大屏要 1 分钟时效 -> checkpoint 60s；报表 T+1 -> 5 分钟即可");
        System.out.println();
    }

    static void downstreamModes() {
        System.out.println("【下游消费模式选择】");
        String[][] rows = {
                {"订阅数据增量", "分钟级时效", "准实时指标、特征回填"},
                {"订阅 compaction 后快照", "小时级", "对账与报表（读性能优先）"},
                {"每日批读最新快照", "T+1", "传统报表（兼容旧调度）"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-22s %-10s %s%n", r[0], r[1], r[2]);
        }
        System.out.println("  同一张表三种消费方式并存 —— 存储统一后各取所需");
    }

    public static void main(String[] args) {
        cowVsMor();
        compactionSchedule();
        checkpointAligned();
        downstreamModes();
    }
}
```

**课后练习**
1. 模拟 MOR 读放大：base 10 个文件 + delta 200 个文件的分区，统计一次全表查询的合并成本，对比 compaction 前后。
2. 实现流写入 + 两级 compaction 的调度模拟，验证文件数曲线（写入上升/合并回落）与存储总量稳定。
3. 思考题：为什么"checkpoint 成功才提交快照"能对齐流作业与湖表的事务？从 43-06 幂等与 46-05 乐观并发两个机制推导端到端 exactly-once。

---

### 46-07 MPP 与即席查询

**知识点**
- **MPP（大规模并行处理）引擎服务"即席分析"**：Trino/StarRocks/Doris 在内存中把查询拆到多节点并行执行——**分析师写一条 SQL 秒级出结果**，区别于 Spark 批作业（分钟级调度）。**交互式分析是数据平台体验的分水岭**。
- **CBO（基于代价的优化）依赖统计信息**：行数、NDV（列基数）、直方图——**统计过期的执行计划必然翻车**（本该裁剪的全表扫）。湖表的统计来自 46-02 的文件统计与 ANALYZE 收集。
- **物化视图（materialized view）是预聚合的工程化**：高频查询模式（日 GMV、类目销量）预计算存储——**查询从扫明细变成扫汇总**，秒级变毫秒级；代价是**刷新与一致性**（T+刷新、实时刷新、查询改写命中）。
- **资源隔离是共享集群的秩序**：即席查询大扫描会拖垮整集群——按业务方/查询类型分队列（prod 报表队列 / adhoc 队列），限制扫描上限与并发（42 章限流思想）。**"一条 SQL 打挂数据平台"是真实事故**。
- **查询治理**：慢查询排行榜（42 章 slow log 思想）、扫描量告警、强制 LIMIT、结果集大小限制。**分析师的教育与工具护栏并行**。
- **与湖表的关系**：MPP 引擎直接查 Iceberg（外部表）或把热数据本地化（StarRocks 内表 + 湖加速）——**热数据进引擎、冷数据留湖**是常见的两级架构。
- 易错点：不配资源队列（即席与生产报表互相干扰）；统计信息从不更新（执行计划劣化无人知）；物化视图刷新与源表脱节（数字对不上）；把 MPP 当 OLTP 用（高频点查应该走 MySQL/Redis）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class MppAdhoc {
    record Query(String sql, String queue, long scanGb, long costMs) {
    }

    static void resourceQueue() {
        System.out.println("【资源队列：一条大 SQL 不能拖垮平台】");
        List<Query> queries = List.of(
                new Query("日报聚合（dwd.orders 30 天）", "prod-etl", 3200, 42_000),
                new Query("分析师：类目销量探索", "adhoc", 180, 8_500),
                new Query("分析师：全表 SELECT * ", "adhoc", 5800, -1));
        for (Query q : queries) {
            String verdict = q.costMs() < 0 ? "拦截：扫描上限 3TB，SELECT * 无 LIMIT"
                    : String.format("执行 %ds（%s 队列）", q.costMs() / 1000, q.queue());
            System.out.printf("  扫描 %5dGB  %-28s -> %s%n", q.scanGb(), q.sql(), verdict);
        }
        System.out.println("  队列隔离：prod 队列保 SLA，adhoc 限并发限扫描");
        System.out.println();
    }

    static void statsMatter() {
        System.out.println("【统计信息过期：执行计划翻车现场】");
        System.out.println("  查询：WHERE dt='2026-11-17' AND shop_id=88");
        System.out.println("  过期统计：shop_id NDV=1000（实际 50 万）-> 误判选择性 -> 放弃分区裁剪 -> 全表扫");
        List<String> fixes = List.of(
                "写入后自动 ANALYZE（统计随数据更新）",
                "慢查询排行榜 + 扫描量告警（劣化当天发现）",
                "执行计划 diff：新计划扫描量突增即告警");
        for (String f : fixes) {
            System.out.println("  对策：" + f);
        }
        System.out.println();
    }

    static void materializedView() {
        System.out.println("【物化视图：高频查询的预计算】");
        record Mv(String def, String refresh, long queryMs, String verdict) {
        }
        List<Mv> mvs = List.of(
                new Mv("daily_gmv：按天聚合 GMV", "每日 0:30 刷新", 45, "命中改写，毫秒级"),
                new Mv("category_sales：类目小时销量", "每小时刷新", 120, "命中"),
                new Mv("任意维度即席查询", "-", 8_500, "不命中 -> 走明细表"));
        for (Mv m : mvs) {
            System.out.printf("  %-26s 刷新=%-12s %dms  %s%n", m.def(), m.refresh(), m.queryMs(), m.verdict());
        }
        System.out.println("  一致性：查询改写必须校验刷新时间，跨刷新窗口的口径要声明（T+0.5h）");
        System.out.println();
    }

    static void hotColdSplit() {
        System.out.println("【两级架构：热数据进引擎，冷数据留湖】");
        List<String> rows = List.of(
                "近 90 天明细 -> StarRocks 内表（本地列存，点查毫秒级）",
                "全量历史 -> Iceberg 湖表（Trino 查询，秒级）",
                "加速通道：湖查询的热分区自动缓存进引擎（cache + 异步物化）",
                "收益：明细查询不撑爆引擎存储，历史查询不迁移数据");
        for (String r : rows) {
            System.out.println("  " + r);
        }
        System.out.println("  39-10 冷热分离 + 46-03 生命周期在查询层的延续");
    }

    public static void main(String[] args) {
        resourceQueue();
        statsMatter();
        materializedView();
        hotColdSplit();
    }
}
```

**课后练习**
1. 为你的 MPP 集群设计队列与限流规则（队列名/并发/扫描上限），并用一条大 SQL 演示拦截。
2. 演示统计信息过期导致的计划劣化：对比 ANALYZE 前后同一查询的扫描量与耗时。
3. 思考题：为什么"一条 SELECT * 能打挂数据平台"？从共享集群的资源隔离与队列调度机制分析护栏的设计要点。
