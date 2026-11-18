### 46-01 从数仓到数据湖

**知识点**
- **数仓与数据湖的分野是 schema 何时确定**：数仓（schema-on-write）——数据写入前先定义表结构，质量高但灵活差；数据湖（schema-on-read）——原始数据原样存（JSON/日志/CSV），用到时再解释，灵活但容易变"数据沼泽"（无目录、无格式、无质量）。**湖仓一体（lakehouse）= 湖的灵活 + 仓的治理**。
- **数据平台的典型分层**（43-09 实时数仓分层的离线对应）：ODS 原始层（原样备份）、DWD 明细层（清洗/拆列/关联）、DWS 汇总层（预聚合）、ADS 应用层（报表/接口）。**层与层之间的 transform 用 SQL/DAG 调度（Airflow 类）编排**。
- **为什么"直接把数据丢 HDFS"会变沼泽**：无 schema 契约（上游改列下游炸）、无元数据（没人知道这张表是谁的）、无小文件治理（千万个小文件拖垮文件系统）。**治理先行：表注册、责任人、格式规范、生命周期**（46-08 细讲）。
- **湖仓一体的技术三角**：**表格式**（Iceberg/Hudi/Delta：给"一堆文件"加事务与版本）、**引擎**（Spark/Trino/Flink 统一读写）、**目录/元数据**（Hive Metastore 或 Glue 类）。**表格式是湖仓一体的核心创新**——它让"目录里的文件"拥有了数据库表的能力（46-05）。
- **批流一体的数据侧形态**：同一份湖表既被离线批任务读（T+1 报表）、也被流任务写（CDC 持续写入，43-09）——**存储统一后"Lambda 双跑"（43-01）在数据侧真正消失**。
- **Java 工程师的位置**：数据平台的服务化层（46-10）——特征 API、指标 API、数据导出服务，多为 Java 实现；Flink 作业本身也是 Java/SQL。**数据平台不是"数据团队专属"，业务 Java 团队是主要生产者与消费者**。
- 易错点：把数据湖当备份盘（丢进去不管，三个月后没人认识）；每张表都没有责任人（坏了没人修）；不分层全在 ADS 直接改（口径失控）；以为湖仓一体是买一个产品（核心是治理流程 + 表格式选型）。

**示例代码**
```java
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class WarehouseToLake {
    record Zone(String name, String schema, String format, String owner, String sla) {
    }

    static void zones() {
        System.out.println("【订单主题的四层分区】");
        List<Zone> zones = List.of(
                new Zone("ODS", "原样（schema-on-read）", "JSON/Parquet 混合", "同步管道组", "与源库延迟<10min"),
                new Zone("DWD", "写入前定义（on-write）", "Parquet", "数据组-小李", "每日 T+1 或小时级"),
                new Zone("DWS", "写入前定义", "Parquet", "数据组-小李", "汇总口径审计"),
                new Zone("ADS", "写入前定义", "Parquet/接口", "报表组", "大屏与导出"));
        for (Zone z : zones) {
            System.out.printf("  %-4s schema=%-22s 格式=%-18s 责任人=%-10s SLA=%s%n",
                    z.name(), z.schema(), z.format(), z.owner(), z.sla());
        }
        System.out.println("  ODS 用 on-read 保真，往上层逐级 on-write 收紧质量");
        System.out.println();
    }

    static void swampSymptoms() {
        System.out.println("【数据沼泽的四个症状与处方】");
        record Symptom(String sign, String cause, String cure) {
        }
        List<Symptom> symptoms = List.of(
                new Symptom("无人认领的表 3000 张", "建表无注册", "目录 + 责任人强制"),
                new Symptom("上游加列下游全红", "无 schema 契约", "契约测试进 CI（46-08）"),
                new Symptom("查询越来越慢", "小文件百万级", "compaction 排期（46-06）"),
                new Symptom("两个口径打架", "同一指标多处实现", "指标平台统一（46-10）"));
        for (Symptom s : symptoms) {
            System.out.printf("  %-18s 原因：%-12s 处方：%s%n", s.sign(), s.cause(), s.cure());
        }
        System.out.println();
    }

    static void lakehouseTriangle() {
        System.out.println("【湖仓一体的技术三角】");
        Map<String, String> triangle = new LinkedHashMap<>();
        triangle.put("表格式", "Iceberg/Hudi/Delta：文件之上加事务、版本、演进");
        triangle.put("计算引擎", "Spark（批）/Trino（即席）/Flink（流）读同一张表");
        triangle.put("元数据目录", "Hive Metastore/Glue：表在哪、schema 是什么");
        for (Map.Entry<String, String> e : triangle.entrySet()) {
            System.out.printf("  %-6s %s%n", e.getKey(), e.getValue());
        }
        System.out.println("  判断成熟度：有没有“多引擎写同一张表不出乱子”的验证");
        System.out.println();
    }

    static void batchStreamMerge() {
        System.out.println("【数据侧的批流一体：Lambda 双跑消失】");
        List<String> rows = List.of(
                "过去：离线表（Hive T+1）+ 实时链路（自建）双份代码双份数据",
                "现在：同一张 Iceberg 表，Flink CDC 持续写入 + Spark 离线读",
                "收益：口径单一（43-09 的 diff 监控失去必要性）、修复可回放（46-05 时间旅行）",
                "代价：表格式运维（compaction/快照清理）成为新工作");
        for (String r : rows) {
            System.out.println("  " + r);
        }
    }

    public static void main(String[] args) {
        zones();
        swampSymptoms();
        lakehouseTriangle();
        batchStreamMerge();
    }
}
```

**课后练习**
1. 为你的业务画四层分区图（ODS/DWD/DWS/ADS），标注每层的 schema 策略、格式与责任人，检查是否存在沼泽症状。
2. 列出你团队"两张表口径打架"的实例（或虚构），设计统一的指标定义文档并指出放置位置（46-10 指标平台）。
3. 思考题：为什么说"表格式是湖仓一体的核心创新"？对比"目录里的文件"与"数据库表"在事务与版本上的能力差距。

---

### 46-02 列式存储与 Parquet

**知识点**
- **行存与列存 answering 的查询不同**：行存（MySQL）按行组织——OLTP 点查改一行快；列存（Parquet）按列组织——分析查询"只碰 3 列扫 10 亿行"时 IO 直降一个数量级。**OLTP 用行存，OLAP 用列存**是存储选型的第一判断。
- **列存的两个物理收益**：**裁剪**（查询只读需要的列文件段）+ **压缩**（同列类型一致、基数低，RLE/字典编码压缩率远高于行存）。真实分析负载压缩比常见 5~10 倍。
- **Parquet 的文件结构**：Row Group（行组，通常 128MB~1GB，行存段）内按 Column Chunk 分列，列内分 Page；**每个 Column Chunk 带统计信息（min/max/null 计数）**——查询谓词（WHERE date='11-17'）直接跳过不含目标的 Row Group，这就是**谓词下推**的物理基础。
- **分区与裁剪的双层结构**：目录分区（date=2026-11-17/ 按目录裁剪）+ 文件内统计（Row Group 裁剪）。**分区键的选择**（高频过滤列、基数适中）直接决定查询速度——分区选错了查询永远全扫。
- **写入端的设计**：小文件是列存的死敌（每个文件一个 footer，打开成本高）——**写入攒批**（每 128MB~1GB 落一个文件）、流式写入用 compaction 合并（46-06）。
- **与 MySQL 的分工**：MySQL 服务交易（行存 + 索引）；Parquet 湖表服务分析（列存 + 扫描）。**同一个业务两类存储并存**，由 CDC（43-09/46-09）连接。
- 易错点：把高频更新数据放列存（更新 = 重写整个文件，代价爆炸）；分区键选低基数列（每分区几行，小文件爆炸）；Row Group 太小（统计信息开销占比高）；忽略 null 与编码列的统计（裁剪失效）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ParquetColumnar {
    /** 模拟 Row Group 统计：min/max 用于谓词裁剪 */
    record RowGroup(int id, String dateMin, String dateMax, double amountMin,
                    double amountMax, long rows, int bytes) {
        boolean mayMatch(String dateFilter) {
            return dateFilter.compareTo(dateMin) >= 0 && dateFilter.compareTo(dateMax) <= 0;
        }
    }

    static void rowGroupPruning() {
        System.out.println("【谓词下推：统计信息跳过无关 Row Group】");
        List<RowGroup> groups = List.of(
                new RowGroup(1, "2026-11-15", "2026-11-15", 10, 500, 1_000_000, 130),
                new RowGroup(2, "2026-11-16", "2026-11-16", 12, 800, 1_000_000, 128),
                new RowGroup(3, "2026-11-17", "2026-11-17", 15, 999, 1_000_000, 132));
        String filter = "2026-11-17";
        long scanned = 0, skipped = 0;
        for (RowGroup g : groups) {
            if (g.mayMatch(filter)) {
                scanned += g.bytes();
                System.out.printf("  RG%d [%s~%s] -> 读取 %dMB%n", g.id(), g.dateMin(), g.dateMax(), g.bytes());
            } else {
                skipped += g.bytes();
                System.out.printf("  RG%d [%s~%s] -> 跳过（统计不含目标日期）%n", g.id(), g.dateMin(), g.dateMax());
            }
        }
        System.out.printf("  扫描 %dMB / 总 %dMB，省 %.0f%% IO%n", scanned, scanned + skipped,
                skipped * 100.0 / (scanned + skipped));
        System.out.println();
    }

    static void columnVsRow() {
        System.out.println("【同一查询：行存 vs 列存的 IO 对比】");
        long rows = 1_000_000_000L;
        int rowWidth = 200, neededCols = 3, colWidth = 8;
        long rowIo = rows * rowWidth;                      // 行存：每行全字段
        long colIo = rows * neededCols * colWidth;         // 列存：只碰 3 列
        System.out.printf("  行存读取 %d 行 × %dB = %.0f GB%n", rows, rowWidth, rowIo / 1024.0 / 1024 / 1024);
        System.out.printf("  列存读取 %d 行 × %d 列 × %dB = %.1f GB（%.0f 倍差距，压缩后更小）%n",
                rows, neededCols, colWidth, colIo / 1024.0 / 1024 / 1024,
                (double) rowIo / colIo);
        System.out.println("  结论：宽表 + 少列查询的分析负载，列存是数量级优势");
        System.out.println();
    }

    static void partitionChoice() {
        System.out.println("【分区键选择：裁剪效率与小文件平衡】");
        String[][] rows = {
                {"按天分区", "每天 500 万行 -> 单文件 300MB", "时间范围查询的主键，常规选择"},
                {"按 shop_id 分区", "50 万商家 -> 每分区 30 行", "小文件灾难，禁止"},
                {"不分区", "全表扫描依赖 Row Group 统计", "小表可行，大表慎用"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-12s %-32s %s%n", r[0], r[1], r[2]);
        }
        System.out.println("  经验：分区后单分区 ≥ 数百 MB；嵌套分区（天+小时）要验证分区数");
        System.out.println();
    }

    static void smallFileProblem() {
        System.out.println("【小文件：列存的慢性病】");
        Map<String, String> impact = new LinkedHashMap<>();
        impact.put("元数据开销", "100 万文件 × 每文件 footer/kv 元数据 = NameNode/目录压力");
        impact.put("调度开销", "每个文件一个 task，调度时间超过计算时间");
        impact.put("治理手段", "写入攒批 + 定时 compaction 合并到目标大小");
        for (Map.Entry<String, String> e : impact.entrySet()) {
            System.out.printf("  %-8s %s%n", e.getKey(), e.getValue());
        }
        System.out.println("  目标文件大小：128MB~1GB；流式写入后每天 compaction 是标配运维");
    }

    public static void main(String[] args) {
        rowGroupPruning();
        columnVsRow();
        partitionChoice();
        smallFileProblem();
    }
}
```

**课后练习**
1. 模拟一个 5 列 100 万行的数据集，分别按行存/列存估算"SELECT 2 列 + 日期过滤"的 IO，验证列存优势的数量级。
2. 为订单事实表选择分区键（候选：天/小时/商家/无），列出计算过程（行数、文件大小、典型查询模式）。
3. 思考题：为什么高频更新的表不适合放 Parquet？从文件不可变与更新重写的代价分析，并给出可行的替代方案（CDC + 追加/merge-on-read）。

---

### 46-03 对象存储与数据分层

**知识点**
- **对象存储（S3/OSS/MinIO）已是数据湖的默认底座**：无限容量、按量付费、高持久性（11 个 9）；代价是**没有目录语义与原子 rename**（列出桶要分页、改名 = 复制+删除）。**HDFS 正在被对象存储替代**（存算分离）。
- **存算分离的架构意义**：计算集群（Spark/Trino）按需起停，数据常驻对象存储——**计算与数据各自弹性**（HDFS 时代计算与存储绑死在一批机器上）。代价：数据不在本地（网络读），靠缓存与列裁剪补。
- **对象存储的语义陷阱**：**rename 不原子**——"先把数据写到 tmp 再 rename 到正式目录"的提交模式在对象存储上不安全；**这就是表格式（46-05）存在的理由之一**：用元数据清单（meta.json 指向文件列表）实现原子提交，而不是依赖文件系统 rename。
- **数据分层布局**：ODS 原样桶（低频访问、生命周期 90 天转归档）、加工层桶（频繁读）、归档层（合规保留，最低成本）。**生命周期策略**让冷数据自动降级——数据平台的"冷热分离"（39-10 思想的数据版）。
- **一致性模型**：对象存储普遍提供强一致读（写后立即可见），但**列举与 rename 的语义与本地文件系统不同**——依赖"目录列表"的程序要按前缀分页设计。
- **成本结构**：存储费 + 请求费 + 出网流量费——**出网（跨云/下载）是隐形大头**，数据不出域（内网分析）同时省钱与合规。
- 易错点：把对象存储当文件系统用（大量小文件 + rename 依赖，性能与正确性双崩）；生命周期没配（十年日志躺在热存储烧钱）；跨云出网无预算（账单惊吓）；安全上桶策略过宽（匿名可读 = 数据泄露，36 章教训）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.List;

public class ObjectStorage {
    static void hdfsToS3() {
        System.out.println("【存算分离：HDFS 时代 vs 对象存储时代】");
        String[][] rows = {
                {"容量扩展", "加机器（存储计算绑定）", "桶容量无限，独立扩展"},
                {"弹性", "计算不能单独缩", "计算集群按需起停"},
                {"文件语义", "目录+原子 rename", "扁平 key，rename=复制+删除"},
                {"一致性", "rename 原子提交", "需表格式补原子提交"},
                {"成本", "整机持有", "按量（存储/请求/流量）"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-8s %-26s %s%n", r[0], r[1], r[2]);
        }
        System.out.println();
    }

    static void renameTrap() {
        System.out.println("【rename 陷阱：提交模式的安全实现】");
        List<String> unsafe = List.of(
                "1) 写数据到 /data/tmp/part-1.parquet",
                "2) rename /data/tmp/* -> /data/official/",
                "3) 对象存储 rename 非原子：部分文件已移动时进程挂 -> 半提交状态");
        for (String s : unsafe) {
            System.out.println("  " + s);
        }
        List<String> safe = List.of(
                "表格式做法：先写数据文件（任意位置，幂等命名）",
                "          再原子替换“元数据清单”（指向新文件列表）",
                "          清单替换是单对象覆盖 = 原子；读端按清单找文件");
        for (String s : safe) {
            System.out.println("  " + s);
        }
        System.out.println("  这正是 Iceberg/Delta/Hudi 的提交原理（46-05）");
        System.out.println();
    }

    static void tiering() {
        System.out.println("【数据分层与生命周期】");
        record Tier(String layer, String storage, String retention, String cost) {
        }
        List<Tier> tiers = List.of(
                new Tier("热：DWD/DWS", "标准存储", "在线 90 天", "基准价"),
                new Tier("温：ODS 近期", "低频存储", "90~365 天", "约 40% 价"),
                new Tier("冷：归档", "归档/深冷", "合规期 1~3 年", "约 10% 价，取回贵"));
        for (Tier t : tiers) {
            System.out.printf("  %-12s %-10s %-12s %s%n", t.layer(), t.storage(), t.retention(), t.cost());
        }
        System.out.println("  生命周期规则自动降级：超 90 天未访问 -> 低频；超 1 年 -> 归档");
        System.out.println("  39-10 冷热分离思想在对象存储上的原生支持");
        System.out.println();
    }

    static void costTrap() {
        System.out.println("【成本三要素：存储/请求/出网】");
        List<String> notes = List.of(
                "出网流量是隐形大头：跨云导出 10TB 可能比存储一年还贵",
                "请求费：百万小文件的 LIST/GET 请求费超过存储费",
                "治理：数据不出域（内网分析）、小文件治理、生命周期三层兜底",
                "安全：桶策略最小化（36 章），禁止匿名读与宽泛 IAM");
        for (String n : notes) {
            System.out.println("  - " + n);
        }
    }

    public static void main(String[] args) {
        hdfsToS3();
        renameTrap();
        tiering();
        costTrap();
    }
}
```

**课后练习**
1. 为你的数据平台设计桶布局与生命周期策略（分层/保留期/降级规则），估算当前与一年后的存储成本。
2. 复现 rename 陷阱：模拟"rename 中途失败"，观察半提交状态对下游的影响，然后实现清单式原子提交修复。
3. 思考题：为什么对象存储"没有原子 rename"催生了表格式？从提交原子性与并发写两个角度分析。

---

### 46-04 Hive 与元数据管理

**知识点**
- **Hive 的历史贡献是把 SQL 带到大数据**：表 = 目录 + schema + 分区的映射（Metastore 记录），SQL 编译成分布式作业。今天 Hive 引擎本身渐渐淡出，**Hive Metastore（ HMS ）仍是元数据事实标准**——Spark/Trino/Flink 都从它读"表在哪、什么格式"。
- **Metastore 记录什么**：库/表/列 schema、分区列表、存储位置与格式、表属性。**它是"数据目录"的雏形**（46-08 治理的载体）：表注册即入 HMS，无注册不可查询。
- **分区管理的运维成本**：每天加分区（`ADD PARTITION`）在高表数场景成为管理负担——Iceberg 类表格式把分区做成表数据的一部分（隐藏分区），**分区演进不再破坏数据**。
- **schema on read 的双刃剑**：ODS 层原样存储靠读时解释，灵活但**列类型错误在读时才爆**——规范：ODS 保留原始 payload + 注册层给出推断 schema 与校验报告。
- **权限与审计**：HMS 层做库表权限（Ranger 类），配合桶策略（46-03）与行级权限——**数据权限的落点在元数据层而不是散在各引擎**。
- **小表大表的管理差异**：维表小表直接全量缓存（43-07）；事实大表分区 + 增量。**元数据里记录表的规模与更新频率**，供引擎做执行计划与调度决策。
- 易错点：元数据与实际文件漂移（手工删了文件，HMS 还指向它——查询报错）；分区手工 ADD 漏掉（数据在目录里但查不到）；权限只配在引擎层（换引擎权限失效）；表无注释无责任人（治理无从下手）。

**示例代码**
```java
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class HiveMetastore {
    record TableMeta(String name, String location, String format, String partitionCol,
                     String owner, long rowsEstimate) {
    }

    static Map<String, TableMeta> metastore = new LinkedHashMap<>();

    static void register() {
        metastore.put("ods.orders", new TableMeta("ods.orders",
                "s3://lake/ods/orders", "JSON", "dt", "sync-team", 0));
        metastore.put("dwd.orders", new TableMeta("dwd.orders",
                "s3://lake/dwd/orders", "Parquet", "dt", "data-lee", 8_600_000_000L));
        metastore.put("dim.product", new TableMeta("dim.product",
                "s3://lake/dim/product", "Parquet", null, "data-lee", 500_000L));
        System.out.println("【HMS 注册表：表在哪、谁负责、多大】");
        for (TableMeta t : metastore.values()) {
            System.out.printf("  %-12s 格式=%-8s 分区=%-4s 责任人=%-9s 行数≈%,d%n",
                    t.name(), t.format(), t.partitionCol() == null ? "-" : t.partitionCol(),
                    t.owner(), t.rowsEstimate());
        }
        System.out.println("  治理基线：无注册的表不可查询；每表必有 owner");
        System.out.println();
    }

    static void driftProblem() {
        System.out.println("【元数据漂移：手工操作的代价】");
        System.out.println("  有人直接在对象存储删了 dt=2026-11-15 目录：");
        TableMeta dwd = metastore.get("dwd.orders");
        System.out.printf("  HMS 仍记录分区 dt=2026-11-15 -> %s%n", dwd.location() + "/dt=2026-11-15");
        System.out.println("  查询报错：文件不存在；报表失败；排查 2 小时发现是手工删桶");
        List<String> cures = List.of(
                "删除必须走表格式/SQL（msck repair 或 drop partition）",
                "每日校验：HMS 分区清单 vs 实际目录 diff 告警",
                "桶策略：生产数据目录禁止人工写删权限");
        for (String c : cures) {
            System.out.println("  处方：" + c);
        }
        System.out.println();
    }

    static void schemaOnRead() {
        System.out.println("【ODS 的 schema-on-read：保真与风险的平衡】");
        String rawJson = "{\"id\":9001,\"amt\":\"69.0\",\"ts\":\"2026-11-17T10:00:00Z\"}";
        System.out.println("  原始 payload：" + rawJson);
        System.out.println("  风险点：amt 是字符串（类型漂移）；ts 是 ISO 格式（与 DWD 统一口径）");
        List<String> rules = List.of(
                "ODS 层保留原样 payload（审计可回放）",
                "注册层提供推断 schema + 每日校验报告（类型漂移检测）",
                "DWD 层做类型归一（on-write），下游永远拿干净列");
        for (String r : rules) {
            System.out.println("  " + r);
        }
        System.out.println();
    }

    static void permissionLayer() {
        System.out.println("【权限落点：元数据层统一，而不是散在引擎】");
        Map<String, String> grants = new LinkedHashMap<>();
        grants.put("analyst-1", "dwd.orders(只读) + dim.*(只读)");
        grants.put("etl-bot", "dwd.*(读写) + ods.*(读)");
        grants.put("external-bi", "ads.*(只读，行级：仅本部门)");
        for (Map.Entry<String, String> e : grants.entrySet()) {
            System.out.printf("  %-12s %s%n", e.getKey(), e.getValue());
        }
        System.out.println("  换引擎（Spark->Trino）权限不变 —— 因为权限跟着表不跟着引擎");
    }

    public static void main(String[] args) {
        register();
        driftProblem();
        schemaOnRead();
        permissionLayer();
    }
}
```

**课后练习**
1. 为你的数据资产建一份元数据登记表（表/位置/格式/分区/责任人/规模），并实现"分区清单 vs 实际目录"的每日 diff 校验脚本。
2. 演示 schema-on-read 的类型漂移事故：ODS 中金额列从数字变字符串，观察下游 DWD 的反应，落地校验报告方案。
3. 思考题：为什么数据权限应该落在元数据层而不是各引擎？从"换引擎权限不失效"与审计一致性两个角度分析。
