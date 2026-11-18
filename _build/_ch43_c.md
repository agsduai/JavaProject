### 43-08 反压与性能调优

**知识点**
- **反压（backpressure）是流系统的"堵车"**：下游算子处理不过来，压力沿数据流向上游传导，最终表现为 **Source 消费延迟（lag）持续增长**。它是流作业最常见的病，症状是"lag 涨"，根因却五花八门。
- **定位反压的标准路径**：先看 lag（确认堵了）→ 看 Flink WebUI 的反压状态（哪个算子繁忙背压高）→ 看该算子的火焰图（42-09）→ 找热点。**热点通常四类：数据倾斜、外部调用慢、状态过大、序列化开销**。
- **数据倾斜是流处理的第一性能杀手**：某个 key（大卖家、爆款商品、某个用户）占 50% 流量，单并行子任务累死其他空闲。对策：**加盐打散**（key 加随机后缀聚合两次）、**局部预聚合**（先本地聚合再全局）、热点 key 单独通道。
- **外部调用是第二杀手**：算子里同步调 HTTP/DB，单条 50ms × 1000 QPS = 并发不够直接堵。对策：**批量调用**（攒 100 条一次查）、**异步 IO**（AsyncFunction 并发多路）、**本地缓存**（维表 43-07）。
- **状态过大拖慢一切**：RocksDB 读写放大、checkpoint 越来越久。对策：TTL（43-05）、状态键设计（少存大对象）、增量 checkpoint。
- **扩容的边界**：并行度提高受三面墙限制——Source 分区数（Kafka 分区 12 个，并行度 24 也是白搭）、keyBy 后的 key 数（倾斜 key 无法再分）、下游系统容量（DB 扛不住更高并发写）。**扩容前先确认瓶颈不是代码**。
- 易错点：lag 一涨就加机器（倾斜时加多少都堵在同一个子任务）；反压只看 CPU（CPU 空闲也会反压——在等外部 IO）；批量大小拍脑袋（太大 checkpoint 变重）；忽略序列化（对象太大，网络传输成瓶颈）。

**示例代码**
```java
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Backpressure {
    static void locate() {
        System.out.println("【反压定位路径：lag -> 算子 -> 火焰图】");
        record Stage(String op, String status, String hint) {
        }
        List<Stage> stages = List.of(
                new Stage("source(kafka)", "繁忙(背压高)", "被下游拖住，不是根因"),
                new Stage("map(解析)", "空闲", "正常"),
                new Stage("keyBy+window", "繁忙(背压高)", "嫌疑最大 -> 看子任务分布"),
                new Stage("sink(jdbc)", "繁忙(背压高)", "也可能是根因 -> 看写入耗时"));
        for (Stage s : stages) {
            System.out.printf("  %-14s %-12s %s%n", s.op(), s.status(), s.hint());
        }
        System.out.println("  WebUI 显示 window 子任务：subtask-0 繁忙、subtask-1~3 空闲 -> 数据倾斜");
        System.out.println();
    }

    static void skewAndFix() {
        System.out.println("【数据倾斜：加盐打散两次聚合】");
        record RawKey(String key, int records) {
        }
        List<RawKey> raw = List.of(
                new RawKey("shop=001（爆款）", 520),
                new RawKey("shop=002", 30),
                new RawKey("shop=003", 25));
        for (RawKey r : raw) {
            System.out.printf("  原始分布 %s: %d 条 -> 单子任务扛 52%% 流量%n", r.key(), r.records());
        }
        System.out.println("  预聚合（两阶段）：");
        Map<String, Integer> salted = new LinkedHashMap<>();
        salted.put("shop=001#0", 180);
        salted.put("shop=001#1", 170);
        salted.put("shop=001#2", 170);
        for (Map.Entry<String, Integer> e : salted.entrySet()) {
            System.out.printf("    局部聚合 %s -> %d 条（打散到 3 个子任务）%n", e.getKey(), e.getValue());
        }
        System.out.println("    全局聚合：合并加盐分片 -> 结果与不加盐一致");
        System.out.println();
    }

    static void externalCallFix() {
        System.out.println("【外部调用慢：批量 + 异步】");
        double perCall = 50;                    // ms
        int qps = 1000;
        System.out.printf("  同步逐条调外部接口：%.0fms × %d QPS 需要并发 %.0f%n",
                perCall, qps, perCall * qps / 1000);
        System.out.println("  优化一：攒批 100 条一次查 -> 外部调用降为 10 次/s");
        System.out.println("  优化二：AsyncFunction 并发 50 路 -> 吞吐提升 50 倍");
        System.out.println("  优化三：维表本地缓存（43-07）-> 热点 key 免外部调用");
        System.out.println("  注意：异步不改顺序语义要带请求上下文（乱序完成按事件时间重排）");
        System.out.println();
    }

    static void scaleLimit() {
        System.out.println("【扩容的三面墙】");
        String[][] walls = {
                {"Source 分区数", "Kafka 12 分区，并行 24 -> 12 个空转", "先扩分区或确认源是瓶颈"},
                {"key 基数", "keyBy(shopId)，倾斜 shop 无法拆", "加盐或热点单独通道"},
                {"下游容量", "JDBC sink 1200 写/s 到顶", "扩下游连接/批量，或换存储"},
        };
        for (String[] w : walls) {
            System.out.printf("  %-12s %-36s %s%n", w[0], w[1], w[2]);
        }
        System.out.println("  顺序：先代码（倾斜/外部调用/状态），再扩容——加机器是最贵的手段");
    }

    public static void main(String[] args) {
        locate();
        skewAndFix();
        externalCallFix();
        scaleLimit();
    }
}
```

**课后练习**
1. 构造一个倾斜数据源（一个 key 占 60%），实测单子任务 lag 与加盐打散后的均衡效果，对比聚合结果一致性。
2. 把同步外部调用改造成异步批量（模拟 50ms 延迟与 1000 QPS），对比改造前后的吞吐与 lag 曲线。
3. 思考题：为什么"CPU 空闲也可能反压"？从算子等待外部 IO 的角度解释，并给出对应的监控指标。

---

### 43-09 CDC 与实时数仓

**知识点**
- **CDC（Change Data Capture）把数据库的每次变更变成事件流**：基于 binlog（Canal/Debezium/Flink CDC）伪装成从库订阅变更，业务零侵入。第 38 章的"ES 数据同步"、第 39 章的"异构数仓"，落地的技术底座就是 CDC。
- **实时数仓的分层**：ODS（binlog 原始层，对应每张表）-> DWD（明细层：清洗、拆列、维表关联）-> DWS（汇总层：实时聚合窗口）-> ADS（应用层：大屏/接口）。**与离线数仓同名分层，实时化替代 T+1**。
- **CDC 的语义细节**：binlog 是 ROW 格式才带完整前后镜像；一条 UPDATE 在 CDC 里是"before + after"两个影像，下游要做对比；**DELETE 也要传播**（下游删数据，漏了就是幻影行）。
- **全量 + 增量的衔接（snapshot reading）**：新表接入先全量快照再接 binlog 增量，衔接点用"快照时的高水位 offset"保证不重不漏——这是 Flink CDC 的核心卖点，省去手工衔接脚本。
- **schema 变更的传播**：源表加列，CDC 下游怎么办——主流方案：下游手动兼容（新字段容忍缺失）或框架自动同步（Flink CDC 3.x schema evolution）。**schema 变更是 CDC 管道的运营常态**，要有演练。
- **实时数仓与离线的口径治理**：同一指标（GMV）实时与离线各算一份，**口径必须同一份定义**（哪些订单算、退款怎么扣），否则"大屏 5000 万、报表 4900 万"的解释成本巨大（43-01 Lambda 架构的遗留问题）。
- 易错点：binlog 还是 ROW 格式之外的（STATEMENT）下游无法解析；DELETE 事件被忽略（下游幻影数据）；全量增量手工衔接出错（重复或漏段）；实时与离线口径独立演化（数字打架）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CdcWarehouse {
    record Row(String id, String status, double amount) {
    }

    /** 一条 CDC 事件：表 + 操作 + before/after */
    record CdcEvent(String table, String op, Row before, Row after, long binlogOffset) {
        String describe() {
            return switch (op) {
                case "INSERT" -> "新增 id=" + after.id();
                case "UPDATE" -> "更新 id=" + after.id() + " " + before.status() + "->" + after.status();
                case "DELETE" -> "删除 id=" + before.id();
                default -> "未知";
            };
        }
    }

    static void eventSemantics() {
        System.out.println("【CDC 事件语义：INSERT/UPDATE/DELETE 都要传播】");
        List<CdcEvent> events = List.of(
                new CdcEvent("orders", "INSERT", null, new Row("9001", "PAID", 199.0), 101),
                new CdcEvent("orders", "UPDATE",
                        new Row("9001", "PAID", 199.0), new Row("9001", "REFUNDED", 199.0), 108),
                new CdcEvent("orders", "DELETE", new Row("9002", "CREATED", 59.0), null, 112));
        for (CdcEvent e : events) {
            System.out.printf("  offset=%d %s%n", e.binlogOffset(), e.describe());
        }
        System.out.println("  下游数仓按 offset 应用：INSERT 插、UPDATE 改、DELETE 删");
        System.out.println("  忽略 DELETE -> 下游出现 id=9002 的幻影订单 —— 对账永远差一笔");
        System.out.println();
    }

    static void snapshotHandoff() {
        System.out.println("【全量快照与增量的无缝衔接】");
        List<String> steps = List.of(
                "1) 记录当前 binlog 位点（高水位 offset=500）",
                "2) 全量快照 SELECT 一致性读（相当于 offset=500 时刻的数据）",
                "3) 快照写入下游",
                "4) 从 offset=500 开始消费增量 —— 与快照正好衔接，不重不漏",
                "5) 校验：行数 + checksum 抽查");
        for (String s : steps) {
            System.out.println("  " + s);
        }
        System.out.println("  手工衔接的坑：快照与位点的时间差 -> 重段或漏段");
        System.out.println();
    }

    static void layered() {
        System.out.println("【实时数仓分层与口径治理】");
        Map<String, String> layers = new LinkedHashMap<>();
        layers.put("ODS", "binlog 原样入库（每表一 topic）");
        layers.put("DWD", "清洗 + 维表关联（订单明细实时化）");
        layers.put("DWS", "实时聚合（每分钟 GMV，43-03 窗口）");
        layers.put("ADS", "大屏/接口直接消费");
        for (Map.Entry<String, String> e : layers.entrySet()) {
            System.out.printf("  %-4s %s%n", e.getKey(), e.getValue());
        }
        System.out.println("  口径治理：GMV 定义（含退款？含取消？）实时与离线共用一份定义文档");
        System.out.println("  差异监控：实时值 vs 离线值每日 diff，> 0.5% 告警");
    }

    public static void main(String[] args) {
        eventSemantics();
        snapshotHandoff();
        layered();
    }
}
```

**课后练习**
1. 实现"binlog 影像到下游表"的应用器：处理 INSERT/UPDATE/DELETE 三类事件，验证漏掉 DELETE 时的幻影数据现象。
2. 模拟全量增量衔接：给 1000 行做快照（记录位点 500），位点 498~502 的增量事件重放，验证不重不漏的校验方法。
3. 思考题：为什么实时与离线的"口径必须同一份定义"？举一个 GMV 口径分歧导致两个数字打架的具体场景。

---

### 43-10 Flink 与 Spark Streaming 概念对比

**知识点**
- **两家框架的根本差异在时间模型**：Spark Streaming（微批 micro-batch）把流当"一小段一小段的批"（DStream/Structured Streaming 的触发间隔），天然批思维；Flink 是真正的逐事件流引擎（DataFlow 模型），事件时间/水位线/状态是一等公民。**"流批一体"的深度 Flink 更彻底**。
- **微批的代价与收益**：代价是延迟下限=批间隔（100ms~秒级）与窗口语义的近似；收益是吞吐高效、模型简单、复用批优化器。**秒级延迟够用的报表类，微批完全胜任**——不必迷信"真流"。
- **Flink 的核心优势场景**：亚秒延迟（风控/告警）、复杂事件时间语义（精确窗口 + 迟到处理）、大状态（RocksDB）、精确一次 sink（2PC）。Spark 优势：SQL 生态统一、批资源复用、团队已有 Spark 基建。
- **API 演进趋势趋同**：Flink SQL 与 Spark Structured Streaming 的 SQL 接口都支持窗口/水位线/(join)——**声明式 SQL 是实时计算的终局形态**，代码 API 是兜底。选型看团队技能栈与运维能力，而不是 API 花哨程度。
- **运维复杂度对比**：Flink 作业是常驻有状态进程（checkpoint、恢复、升级要 savepoint）；Spark 批任务跑完即退（Spark Streaming 的 trigger AvailableNow 其实是"带流外观的批"）。**把批任务迁移上 Flink 要接受"7×24 服务"的运维等级**。
- **选型决策树**：延迟要求 <1s 或强事件时间 -> Flink；T+1 或分钟级且已有 Spark 体系 -> Spark；两者都不熟且是纯 SQL 报表 -> 云厂商托管（省运维）。**没有银弹，只有场景匹配**。
- 易错点：用微批做亚秒级风控（延迟下限不允许）；把 Flink 作业当批任务（没有 savepoint 就升级 = 状态全丢）；迷信框架基准测试（自己的数据倾斜一次就推翻所有 benchmark）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.List;

public class FlinkVsSpark {
    record Capability(String dim, String flink, String spark, String guidance) {
    }

    static void compare() {
        System.out.println("【能力对比：按场景看差异】");
        List<Capability> rows = List.of(
                new Capability("延迟模型", "逐事件，毫秒级", "微批，触发间隔为下限",
                        "亚秒风控选 Flink"),
                new Capability("事件时间", "一等公民（WM/迟到/窗口）", "Structured Streaming 支持，微批近似",
                        "精确口径选 Flink"),
                new Capability("状态管理", "RocksDB 大状态 + TTL", "状态在批模型内管理",
                        "亿级 key 状态选 Flink"),
                new Capability("SQL 生态", "Flink SQL 成熟", "Spark SQL 最成熟",
                        "纯 SQL 报表两者皆可"),
                new Capability("运维形态", "7×24 常驻作业", "跑完即退（AvailableNow）",
                        "团队运维能力是约束"),
                new Capability("批复用", "批模式有限复用", "同一引擎批流共用",
                        "已有 Spark 基建优先"));
        for (Capability r : rows) {
            System.out.printf("  %-8s %-26s %-28s %s%n", r.dim(), r.flink(), r.spark(), r.guidance());
        }
        System.out.println();
    }

    static void microBatchFit() {
        System.out.println("【微批够用的场景：不要过度设计】");
        List<String> cases = List.of(
                "实时报表（1 分钟刷新可接受）-> 微批 30s 一次，吞吐高效",
                "夜间准实时对账（5 分钟级）-> 微批甚至 cron 批",
                "风控拦截（<1s 必须）-> 只能真流（Flink）",
                "实时特征计算（亚秒 + 大状态）-> Flink 的主场");
        for (String c : cases) {
            System.out.println("  - " + c);
        }
        System.out.println("  判断式：延迟预算决定引擎，而不是“哪个更流行”");
        System.out.println();
    }

    static void opsDifference() {
        System.out.println("【运维形态差异：常驻 vs 即退】");
        List<String> flink = List.of(
                "升级：savepoint 快照 -> 停作业 -> 新版本从 savepoint 恢复（无 savepoint = 状态重算）",
                "扩容：改并行度也要走 savepoint（Rescale 触发状态重分布）",
                "观测：checkpoint 心跳（43-05）+ lag + 反压三件套");
        for (String s : flink) {
            System.out.println("  Flink：" + s);
        }
        List<String> spark = List.of(
                "升级：改代码重跑即可（状态在批间传递或外部化）",
                "失败：重跑该批（幂等 sink 才安全）",
                "观测：每批耗时与积压");
        for (String s : spark) {
            System.out.println("  Spark：" + s);
        }
        System.out.println("  结论：Flink 的能力溢价对应的是运维溢价");
    }

    public static void main(String[] args) {
        compare();
        microBatchFit();
        opsDifference();
    }
}
```

**课后练习**
1. 为三个场景（1 分钟报表/亚秒风控/5 分钟对账）分别选引擎并说明理由，形成一份团队选型决策记录。
2. 模拟 Flink savepoint 升级流程：写状态快照 -> 修改逻辑 -> 恢复，对比"无 savepoint 直接重启"的数据差异。
3. 思考题：为什么说"延迟预算决定引擎"？列出从业务需求推导延迟预算的三步方法。

---

### 43-11 综合案例：实时大屏（订单成交监控）

**知识点**
- **把本章串成一条实时链路**：MySQL 订单（binlog）-> CDC 采集（43-09）-> Kafka -> Flink 清洗关联维表（43-07）-> 事件时间窗口聚合（43-02/03）-> 水位线控制关闭（43-04）-> 幂等写入 Redis（43-06）-> 大屏接口读 Redis。**每个环节都对应前面一课的设计决策**。
- **大屏的需求即参数**：GMV 每分钟刷新（滚动窗口 60s + 总累计）、成交笔数、实时趋势线（滑动窗口近 10 分钟）、TOP 商品榜（窗口聚合 + 排序）。**每个指标都能追溯到窗口参数与口径定义**——口径文档（含退款是否扣除）是需求评审的一部分。
- **正确性保障**：事件时间语义（跨天准确）、幂等 upsert（重放无损）、未配对旁路（对账兜底）、与离线口径每日 diff。**大屏数字敢被 CEO 盯，靠的是这条链路**。
- **运维保障**：checkpoint 心跳、lag 监控、反压定位预案（43-08）、水位线空闲源标记、窗口结果"是否最终"标记（前端闪烁提示临时值）。
- **容量与降级**：大促峰值 QPS 提前压测（重点测倾斜 key——爆款商品）、Redis 写入批量合并、降级预案（CDC 断流时大屏显示"数据延迟"而不是停更数字）。
- 易错点：大屏只做"显示"不管口径（数字被质疑时无人能解释）；实时与离线两套口径独立演化（数字打架）；不做峰值倾斜压测（大促当天爆款 key 堵死）；降级预案缺失（CDC 断流大屏冻结没人发现）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class RealtimeDashboard {
    /** 指标层：Redis 模拟（幂等 upsert） */
    static Map<String, Double> redis = new LinkedHashMap<>();

    record OrderEvent(long id, long eventTs, String shopId, double amount, boolean refunded) {
    }

    static List<OrderEvent> genEvents() {
        List<OrderEvent> events = new ArrayList<>();
        long base = 1763347200000L;
        for (int i = 0; i < 8; i++) {
            events.add(new OrderEvent(9000 + i, base + i * 7000,
                    i % 3 == 0 ? "shop-001(爆款)" : "shop-00" + (i % 3 + 1),
                    69 + i * 10, false));
        }
        return events;
    }

    static void pipeline() {
        System.out.println("【链路总览：CDC -> Kafka -> Flink -> Redis -> 大屏】");
        List<String> steps = List.of(
                "1) MySQL binlog -> CDC（Debezium/Flink CDC）-> topic ods_orders",
                "2) Flink 清洗：过滤测试单 + 维表关联（商品类目，本地缓存）",
                "3) 事件时间滚动窗口 60s：GMV/笔数（keyBy shopId，倾斜加盐）",
                "4) 滑动窗口 10min/1min：趋势线",
                "5) 幂等写 Redis（窗口键主键 upsert）",
                "6) 大屏接口轮询 Redis，展示「是否最终」标记");
        for (String s : steps) {
            System.out.println("  " + s);
        }
        System.out.println();
    }

    static void aggregate() {
        System.out.println("【一分钟滚动窗口聚合（含倾斜加盐）】");
        List<OrderEvent> events = genEvents();
        TreeMap<Long, Double> plain = new TreeMap<>();
        for (OrderEvent e : events) {
            plain.merge(e.eventTs() / 60000, e.amount(), Double::sum);
        }
        for (Map.Entry<Long, Double> w : plain.entrySet()) {
            System.out.printf("  窗口 min%d GMV=%.1f 元（含爆款 shop-001）%n", w.getKey(), w.getValue());
        }
        System.out.println("  倾斜处理：shop-001 加盐 shop-001#0~2 局部聚合，全局合并结果一致");
        System.out.println();
    }

    static void idempotentWrite() {
        System.out.println("【幂等写入与最终标记】");
        // 窗口 [w, w+60s) 首次触发
        redis.put("gmv:11-17:min0", 69.0);
        System.out.println("  T+61s 首次触发：gmv:min0=69.0 (临时)");
        redis.put("gmv:11-17:min0", 139.0);   // 迟到订单更新
        System.out.println("  T+64s 迟到更新：gmv:min0=139.0 (临时)");
        redis.put("gmv:11-17:min0", 139.0);
        System.out.println("  T+364s 水位线越过窗口+5min -> 最终态：gmv:min0=139.0 (final)");
        System.out.println("  大屏契约：临时值带闪烁提示，最终值定格 —— 下游不再误读");
        System.out.println();
    }

    static void ops() {
        System.out.println("【上线保障清单】");
        List<String> items = List.of(
                "口径文档：GMV 是否含退款/取消，与离线对账共用定义",
                "对账：实时 vs 离线每日 diff > 0.5% 告警",
                "压测：大促峰值 + 爆款倾斜 key 专项",
                "监控：checkpoint 心跳 / lag / 反压 / 水位线卡住",
                "降级：CDC 断流 -> 大屏显示“数据延迟 N 分钟”而非冻结数字",
                "回放：原始事件流在 Kafka 保留 24h，统计错误可重放修复");
        for (String i : items) {
            System.out.println("  [ ] " + i);
        }
    }

    public static void main(String[] args) {
        pipeline();
        aggregate();
        idempotentWrite();
        ops();
    }
}
```

**课后练习**
1. 端到端实现迷你版实时大屏（内存模拟全链路）：CDC 事件 -> 窗口聚合 -> 幂等写 -> 接口读，注入一笔迟到订单验证"临时 -> 最终"标记。
2. 写出大屏三个指标的口径文档（GMV/笔数/趋势线），包括退款处理、窗口参数、与离线对账的 diff 规则。
3. 思考题：为什么"CEO 盯着的大屏"对可观测性与正确性的要求反而最高？从数字被质疑时的解释链路（口径 -> 窗口 -> 链路）推导。
