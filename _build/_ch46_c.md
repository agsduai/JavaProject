### 46-08 数据质量与治理

**知识点**
- **数据质量问题的成本随时间指数上涨**：上游悄悄改了枚举值，当天无人发现 -> 下游聚合全错 -> 报表决策错误 -> 一个月后发现 -> 回溯修复所有历史分区。**质量检查必须"在写入时"而不是"在使用时"**。
- **数据契约（data contract）是生产者与消费者的合同**：schema（列名/类型/可空）、语义（单位、口径、枚举值域）、SLA（更新频率、延迟上限）、变更流程（加列兼容、删列走两版本）。**契约以版本化文档 + 自动校验存在**，不是聊天记录。
- **质量规则的四个层级**：**完整性**（非空率、行数波动）、**有效性**（枚举值域、格式、范围）、**一致性**（跨表核对：订单表总额 = 支付表总额）、**及时性**（分区准时产出）。每条规则带**告警阈值与阻断策略**（软警告 vs 硬阻断下游）。
- **血缘（lineage）回答"这个数字从哪来"**：列级血缘把 ADS 报表指标一路追到 ODS 原始字段——**排障与影响面分析的基础设施**：上游表变更，血缘自动列出全部受影响下游（41-09 影响面思想的数据库版）。
- **数据目录（catalog）是治理的门面**：表注册、责任人、业务描述、质量评分、使用热度——**让"找数据"从问人变成搜目录**。没有目录的团队 50% 的时间花在"这张表是什么"上。
- **变更管理**：上游 schema 变更走契约评审（兼容性检查）-> 通知订阅方 -> 灰度切换——**与 41-09 数据库变更协同同构**，只是粒度是表。
- 易错点：质量规则只在报表出事后补（永远慢一步）；契约只有文档没有校验（形同虚设）；血缘只有表级没有列级（影响面太粗）；质量评分没人看（治理变成表格式主义）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DataGovernance {
    record DqRule(String name, String kind, String expr, double threshold, boolean blocking) {
    }

    static void qualityChecks() {
        System.out.println("【DWD.orders 的每日质量检查】");
        List<DqRule> rules = List.of(
                new DqRule("非空率", "完整性", "order_id null 比例", 0.001, true),
                new DqRule("行数波动", "完整性", "今日行数 / 7 日均值", 0.3, true),
                new DqRule("金额范围", "有效性", "amount >= 0 且 < 10万", 0.001, true),
                new DqRule("状态枚举", "有效性", "status ∈ {PAID,REFUNDED,...}", 0, true),
                new DqRule("跨表一致", "一致性", "orders.amount 合计 = payments 合计", 0.005, false));
        for (DqRule r : rules) {
            System.out.printf("  %-8s %-6s %-30s 阈值=%.3f %s%n",
                    r.name(), r.kind(), r.expr(), r.threshold(), r.blocking() ? "硬阻断" : "告警");
        }
        System.out.println("  阻断规则失败 -> 下游分区标记不可用，禁止消费");
        System.out.println();
    }

    static void contract() {
        System.out.println("【数据契约：上游 orders 事件】");
        Map<String, String> schema = new LinkedHashMap<>();
        schema.put("order_id", "string, 非空, 全局唯一");
        schema.put("amount", "decimal(10,2), 非空, >0, 单位=元");
        schema.put("status", "enum(PAID, REFUNDED, CLOSED)");
        schema.put("event_ts", "utc 毫秒, 服务端打点");
        for (Map.Entry<String, String> e : schema.entrySet()) {
            System.out.printf("  %-9s %s%n", e.getKey(), e.getValue());
        }
        List<String> clauses = List.of(
                "SLA：事件延迟 P99 < 60s；断流 > 5min 告警",
                "兼容变更：允许加列；改类型/删列需两版本过渡 + 30 天通知",
                "校验：生产者侧 schema 校验拦截 + 消费者侧质量规则兜底");
        for (String c : clauses) {
            System.out.println("  " + c);
        }
        System.out.println("  契约是版本化文档 + 自动校验，不是口头约定");
        System.out.println();
    }

    static void lineage() {
        System.out.println("【列级血缘：ads.daily_gmv 的来路】");
        List<String> path = List.of(
                "ads.daily_gmv.gmv",
                "  <- dws.orders_hourly.amount_sum",
                "    <- dwd.orders.amount（清洗后）",
                "      <- ods.orders.payload.amt（原始）",
                "        <- MySQL orders.amount（binlog）");
        for (String p : path) {
            System.out.println("  " + p);
        }
        System.out.println("  用途1 排障：GMV 错了 -> 沿血缘逐层核对是哪层引入");
        System.out.println("  用途2 影响面：ods.orders 加列 -> 血缘列出全部下游（41-09 思想）");
        System.out.println();
    }

    static void catalogScore() {
        System.out.println("【数据目录与质量评分】");
        record Entry(String table, String owner, double quality, String heat) {
        }
        List<Entry> entries = List.of(
                new Entry("dwd.orders", "data-lee", 0.98, "高（12 个下游）"),
                new Entry("dwd.clicks", "data-zhao", 0.72, "中（3 个下游）"),
                new Entry("tmp.test_001", "无（僵尸表）", 0.0, "90 天未使用 -> 下线清单"));
        for (Entry e : entries) {
            System.out.printf("  %-14s 责任人=%-10s 质量=%.2f 热度=%s%n",
                    e.table(), e.owner(), e.quality(), e.heat());
        }
        System.out.println("  治理闭环：评分驱动责任人修复；僵尸表走下线流程而不是无限堆积");
    }

    public static void main(String[] args) {
        qualityChecks();
        contract();
        lineage();
        catalogScore();
    }
}
```

**课后练习**
1. 为你最核心的 DWD 表写数据契约（schema/语义/SLA/变更流程），并实现三条质量规则的真实校验脚本。
2. 用血缘解决一次"报表数字不对"：从 ADS 指标逐层下钻定位引入层，记录定位耗时。
3. 思考题：为什么"硬阻断"规则要谨慎使用？从"阻断下游产出的连锁影响"与"错误数据扩散"的代价对比分析分级策略。

---

### 46-09 数据集成与同步

**知识点**
- **数据集成的管道形态**：**库 -> 湖**（业务库 CDC 全量+增量进 ODS，43-09 的离线延伸）、**文件 -> 湖**（日志/埋点批量导入）、**湖 -> 库**（ADS 结果回写业务库/Redis 供查询）、**湖 -> 湖**（跨域共享/备份）。每条管道都要回答：全量还是增量、频率、一致性校验。
- **全量与增量的组合模式**：首日全量快照 + 之后增量（CDC/文件时间戳）；**大表拆段**（按主键范围并行拉取）；**幂等写入**（按主键覆盖，重跑无损——39-11 迁移思想在数据平台的复用）。
- **一致性校验是集成的验收标准**：行数核对、checksum 抽查、抽样比对关键列——**"同步完成"必须由校验声明**（43-09 对账思想）；差异进差异表自动修复或告警人工。
- **延迟分层**：交易类近实时（CDC 分钟级）、分析类小时级、报表 T+1——**每张表声明自己的 SLA**，管道按 SLA 选技术（CDC vs 定时批）。
- **管道的运维**：断点续传（offset/checkpoint）、重跑幂等、速率限制（不拖垮源库，39 章归档同款）、告警与补数流程（某天分区失败 -> 重跑该分区）。
- **跨域共享的合规**：数据出域（给第三方）走审批 + 脱敏 + 水印——**集成层是合规检查点**（36 章敏感数据治理的落点）。
- 易错点：增量靠"update_time 轮询"但源表无索引（拖垮源库）；全量重跑无幂等（重复行翻倍）；校验缺失（静默丢数一个月）；补数流程缺失（失败分区永远缺数据）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DataIngestion {
    record Pipeline(String name, String mode, String freq, String sla, String check) {
    }

    static void pipelineMatrix() {
        System.out.println("【订单主题的同步管道清单】");
        List<Pipeline> pipes = List.of(
                new Pipeline("MySQL->ODS（orders）", "CDC 全量+增量", "分钟级", "延迟<10min", "行数+checksum"),
                new Pipeline("埋点日志->ODS", "文件批量", "小时级", "每小时产出", "行数+抽样比对"),
                new Pipeline("ADS->Redis（榜单）", "全量覆盖", "分钟级", "大屏时效", "键数+抽 5 条比对"),
                new Pipeline("湖->合作方", "T+1 导出", "天级", "9 点前", "审批+脱敏校验"));
        for (Pipeline p : pipes) {
            System.out.printf("  %-24s %-12s %-6s SLA=%-10s 验收=%s%n",
                    p.name(), p.mode(), p.freq(), p.sla(), p.check());
        }
        System.out.println();
    }

    static void idempotentFullLoad() {
        System.out.println("【全量重跑的幂等设计】");
        record Batch(String range, String targetFile, String status) {
        }
        List<Batch> batches = List.of(
                new Batch("id 0~100万", "part-0000.parquet", "完成"),
                new Batch("id 100万~200万", "part-0001.parquet", "失败（重跑中）"),
                new Batch("id 200万~300万", "part-0002.parquet", "完成"));
        for (Batch b : batches) {
            System.out.printf("  %s -> %s【%s】%n", b.range(), b.targetFile(), b.status());
        }
        System.out.println("  幂等三要素：目标文件按主键范围命名（覆盖写）+ 完成标记表 + 断点续跑");
        System.out.println("  重跑 id 100万~200万：覆盖同名文件，绝不产生重复行");
        System.out.println();
    }

    static void consistencyCheck() {
        System.out.println("【同步验收：三层校验】");
        record Check(String layer, String method, String result) {
        }
        List<Check> checks = List.of(
                new Check("行数", "源 count(8600万) vs 湖 count(8600万)", "PASS"),
                new Check("checksum", "按主键分 100 段抽样聚合比对", "PASS"),
                new Check("关键列抽查", "随机 1000 条逐字段比对", "2 条 amount 不一致 -> 进差异表"));
        for (Check c : checks) {
            System.out.printf("  %-8s %-34s %s%n", c.layer(), c.method(), c.result());
        }
        System.out.println("  差异处理：差异表 -> 定位（binlog 位点差）-> 修复 -> 复检");
        System.out.println("  原则：没有校验的同步 = 没有同步");
        System.out.println();
    }

    static void backfillOps() {
        System.out.println("【补数与重跑的运维闭环】");
        List<String> flow = List.of(
                "分区失败 -> 告警（哪个分区/哪个管道/失败原因）",
                "一键重跑该分区（幂等保证安全）",
                "重跑前检查：源数据仍在（保留期）、下游是否需要级联重跑",
                "重跑后自动触发质量校验（46-08）-> 通过才通知下游",
                "月度统计：补数频率是管道健康度指标");
        for (String s : flow) {
            System.out.println("  " + s);
        }
        System.out.println("  反例：失败分区手工补数据 -> 无校验 -> 错误数据进报表");
    }

    public static void main(String[] args) {
        pipelineMatrix();
        idempotentFullLoad();
        consistencyCheck();
        backfillOps();
    }
}
```

**课后练习**
1. 为你的核心表设计同步管道矩阵（≥4 条管道），每条标注模式/频率/SLA/验收方式。
2. 实现幂等全量加载模拟器：分批 + 完成标记 + 断点续跑，注入中途失败验证重跑不产生重复行。
3. 思考题：为什么"update_time 轮询"做增量在无索引大表上是危险的？从源库负载与增量完整性两个角度给出替代方案。

---

### 46-10 数据平台服务化

**知识点**
- **数据平台的价值在"被业务系统消费"**：报表是终点，**特征 API（推荐/风控实时取数）、指标 API（运营后台）、榜单缓存（大屏/首页）**是数据反哺业务的出口——46 章所有铺垫（分层、质量、MPP）最终为了这几个服务。
- **指标平台统一口径**：指标定义（名称/口径/维度/负责人）注册一次，报表/大屏/接口全部引用——**"GMV 是多少"只有一个权威答案**（46-01 打架问题的终解）。指标变更走评审，历史口径留档。
- **特征平台**：离线特征（湖表 T+1）与在线特征（Redis 实时）**同一套定义**——训练与推理特征一致性（"训练用昨天数据、线上用今天数据"的口径错位是模型效果劣化的隐形元凶）。
- **数据 API 的工程要求**：与业务 API 同标准（40-09 分层、限流、鉴权、监控）；**查询下推到正确的存储**（点查走 Redis/StarRocks，聚合走 MPP）；大结果集异步导出（对象存储 + 通知）。
- **数据的 SLA 与告警**：API 可用性、数据新鲜度（"数据最后更新于 5 分钟前"要暴露）、下游依赖的数据分区就绪通知（调度事件驱动而不是轮询）。
- **组织协作**：数据团队提供平台与规范，业务团队生产与消费——**自助化是目标**（自助建表模板、自助质量规则、自助指标注册），全部人工排队的服务不可扩展。
- 易错点：指标口径散落在各报表 SQL 里（没人说得清哪个准）；特征线上线下两套定义（训练-推理偏差）；数据 API 走了错误存储（点查打到 Trino 全表扫）；"数据就绪"靠人盯着（下游 9 点开工发现上游 10 点才产出）。

**示例代码**
```java
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DataPlatformService {
    static void metricRegistry() {
        System.out.println("【指标平台：GMV 只有一个定义】");
        record Metric(String name, String formula, String dims, String owner, List<String> consumers) {
        }
        List<Metric> metrics = List.of(
                new Metric("gmv_daily", "SUM(dwd.orders.amount) WHERE status=PAID",
                        "date/category/channel", "data-lee",
                        List.of("大屏", "运营日报", "运营后台 API")),
                new Metric("order_count", "COUNT(DISTINCT order_id)", "date/status", "data-lee",
                        List.of("大屏", "周报")));
        for (Metric m : metrics) {
            System.out.printf("  %-10s 口径=%s%n      维度=%-20s 负责人=%s 消费方=%d 个%n",
                    m.name(), m.formula(), m.dims(), m.owner(), m.consumers().size());
        }
        System.out.println("  变更流程：改口径 -> 评审 -> 版本留档 -> 消费方通知（41-10 审计思想）");
        System.out.println();
    }

    static void featureConsistency() {
        System.out.println("【特征平台：训练与推理同源】");
        record Feature(String name, String offline, String online, String consistent) {
        }
        List<Feature> feats = List.of(
                new Feature("user_p30_click", "湖表 T+1 聚合", "Redis 实时累加", "口径一致：近30天点击"),
                new Feature("shop_hot_score", "湖表计算", "湖表快照回灌 Redis", "同一份计算逻辑"));
        for (Feature f : feats) {
            System.out.printf("  %-14s 离线=%-14s 在线=%-16s %s%n",
                    f.name(), f.offline(), f.online(), f.consistent());
        }
        System.out.println("  反例：离线口径=去重点击，在线口径=原始点击 -> 训练与线上分布不一致");
        System.out.println("  对策：特征定义注册化，离线在线由同一份代码/SQL 生成");
        System.out.println();
    }

    static void dataApi() {
        System.out.println("【数据 API 的存储路由】");
        record Api(String path, String storage, String latency, String note) {
        }
        List<Api> apis = List.of(
                new Api("GET /metrics/gmv?date=", "StarRocks 物化视图", "45ms", "预聚合命中"),
                new Api("GET /rank/products", "Redis（ADS 回灌）", "3ms", "大屏榜单"),
                new Api("GET /export/orders?month=", "异步导出->OSS", "分钟级", "大结果集不走同步链路"));
        for (Api a : apis) {
            System.out.printf("  %-28s %-18s %-7s %s%n", a.path(), a.storage(), a.latency(), a.note());
        }
        System.out.println("  反例：点查打到 Trino 扫明细 -> 8s 超时 —— 存储选型错位");
        System.out.println();
    }

    static void freshnessSignal() {
        System.out.println("【数据新鲜度必须可查询】");
        List<String> items = List.of(
                "每个 API 响应带 meta.data_freshness（分区时间）",
                "分区就绪事件驱动下游调度（DAG 依赖，不靠轮询）",
                "新鲜度 SLA 告警：日报数据 9 点未产出即告警",
                "新鲜度降级策略：分区缺失时 API 显式返回“数据延迟”而非旧值冒充");
        for (String i : items) {
            System.out.println("  - " + i);
        }
        System.out.println("  42 章可观测思想在数据平台的落地：数据也有 SLI/SLO");
    }

    public static void main(String[] args) {
        metricRegistry();
        featureConsistency();
        dataApi();
        freshnessSignal();
    }
}
```

**课后练习**
1. 为你的团队注册 3 个核心指标（口径/维度/消费方），实现一个"口径唯一"的指标查询接口原型。
2. 排查一次"训练-推理特征偏差"（可虚构）：从特征定义、离线口径、在线口径三个环节定位不一致点并修复。
3. 思考题：为什么"数据新鲜度"是数据 API 的 SLA 之一？从下游决策依赖与错误数据冒充新鲜的风险两个角度分析。

---

### 46-11 综合案例：订单主题湖仓

**知识点**
- **把本章串成一条完整数据链路**：MySQL 订单（binlog）-> Flink CDC 写 Iceberg ODS（46-05/06，checkpoint 对齐）-> 每日批清洗 DWD（Parquet 分区 + 质量校验 46-08）-> DWS 预聚合 -> ADS 物化视图（46-07）-> 指标 API/大屏/回灌 Redis（46-10）。**每一课的设计决策在链路上各就各位**。
- **关键设计参数**：ODS 按 CDC 写（MOR + 每 10 分钟 compaction）、DWD 按 COW 天分区（读优先）、快照保留 7 天 + 每日过期、ADS 回灌 Redis 分钟级。**参数背后都是读写比例与时效 SLA 的权衡**。
- **质量与治理贯通**：契约（46-08）约束上游事件、每层产出跑质量规则、血缘覆盖全链、目录登记责任人。**一次口径变更的影响面靠血缘在 10 分钟内评估完**。
- **故障与修复叙事**：上游误把退款金额写负数 -> 质量规则当日拦截（金额范围）-> 阻断 DWD 产出 -> 修复后重跑该分区（幂等）-> 时间旅行核对修复前后差异 -> 下游自动恢复。**全链路无人工救火**。
- **上线检查单**：管道 SLA 与告警、质量规则覆盖率、compaction 排期、快照过期排期、权限最小化、成本三层兜底、演练（分区失败重跑/时间旅行回滚/下游新鲜度降级）。
- 易错点：全链路只有 ODS 没有 DWD（下游直接扫原始 JSON）；质量规则上线后从不演练（规则失效无人知）；ADS 与 DWS 口径两套代码（指标打架复发）；时间旅行当成日常修复手段滥用（应修根因，回滚只是止血）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class LakehouseCase {
    record Stage(String name, String tech, String cadence, String qualityGate) {
    }

    static void architecture() {
        System.out.println("【订单主题湖仓：从 binlog 到大屏】");
        List<Stage> stages = List.of(
                new Stage("ODS", "Iceberg(MOR) + Flink CDC", "秒级写入/10min compaction", "契约校验（46-08）"),
                new Stage("DWD", "Iceberg(COW) 天分区", "每日 1:00 批", "质量规则硬阻断"),
                new Stage("DWS", "预聚合表", "每日 2:00", "跨表一致性核对"),
                new Stage("ADS", "物化视图 + Redis 回灌", "分钟级", "新鲜度 SLA"),
                new Stage("服务", "指标 API / 大屏 / 榜单", "实时读", "新鲜度元数据随响应返回"));
        for (Stage s : stages) {
            System.out.printf("  %-4s %-26s %-24s %s%n", s.name(), s.tech(), s.cadence(), s.qualityGate());
        }
        System.out.println();
    }

    static void incidentReplay() {
        System.out.println("【一次负数金额事故的全自动处置】");
        List<String> timeline = List.of(
                "09:12 上游 bug：refund_amount 写入负数",
                "09:13 ODS 契约校验发现异常（字段范围外）-> 记录旁路不阻断 ODS（保真）",
                "09:14 DWD 质量规则（amount>=0）触发硬阻断 -> 该分区标记不可用",
                "09:20 上游修复 -> 重跑当日增量（幂等覆盖）",
                "09:25 质量规则复检通过 -> 分区恢复 -> 下游自动续跑",
                "10:00 时间旅行对比修复前后快照 -> 确认无残留错误数据",
                "复盘：契约文档补充“金额非负”条款 + 上游加单元测试");
        for (String t : timeline) {
            System.out.println("  " + t);
        }
        System.out.println("  全程 48 分钟，零人工救火 —— 质量规则 + 幂等重跑 + 时间旅行的组合拳");
        System.out.println();
    }

    static void opsChecklist() {
        System.out.println("【湖仓上线检查单】");
        Map<String, String> items = new LinkedHashMap<>();
        items.put("管道", "CDC lag 告警、批任务失败告警、补数流程演练");
        items.put("存储", "compaction 排期、快照过期排期、生命周期降级");
        items.put("质量", "规则覆盖率 >80% 核心表、阻断/告警分级、月度误报复盘");
        items.put("治理", "血缘覆盖、目录登记、僵尸表下线流程");
        items.put("成本", "存储分层、出网预算、小文件治理");
        items.put("安全", "桶策略最小化、列级权限、出域审批");
        for (Map.Entry<String, String> e : items.entrySet()) {
            System.out.printf("  %-4s %s%n", e.getKey(), e.getValue());
        }
        System.out.println("  演练清单：分区失败重跑 / 时间旅行回滚 / 新鲜度降级，每季度一次");
    }

    public static void main(String[] args) {
        architecture();
        incidentReplay();
        opsChecklist();
    }
}
```

**课后练习**
1. 端到端实现迷你湖仓（内存模拟）：ODS->DWD->ADS 三层 + 质量规则 + 幂等重跑 + 快照回退，跑通本章事故叙事。
2. 为你的业务写四层分区设计文档（技术选型 + 参数 + 质量门槛 + SLA），标注每个参数的权衡理由。
3. 思考题：为什么说"时间旅行是止血手段而不是修复手段"？从根因修复、审计、存储成本三个角度界定它的使用边界。
