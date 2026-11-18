### 41-08 特性开关与暗发布

**知识点**
- **特性开关把"部署"与"发布"解耦**：代码随时合入 main 部署上线，新功能由开关控制是否对用户可见。**部署 100 次，功能发布 1 次**——这是 trunk-based 开发的基础设施，也是发布风险从"一次性大爆炸"变成"随时小步释放"的支点。
- **开关的四类生命周期**：发布开关（release toggle，功能上线后几天内删除）、运维开关（ops toggle，如降级到大促写保护）、实验开关（experiment，A/B 用完删除）、权限开关（permission，长期存在如付费功能）。**前三类是技术债，必须设过期时间**——堆积的"僵尸开关"会把代码分支逻辑变成迷宫。
- **暗发布（dark launch）**：新代码在真实流量下运行但用户无感知——把结果写日志不返回用户，用真实负载验证性能与正确性（影子流量）。"新推荐算法算了但不展示，对比效果一周"是暗发布的典型用法。
- **开关的实现层次**：最简单是配置中心布尔值；进阶带灰度规则（按用户/渠道/百分比）；再进阶与实验平台打通（分流+效果归因）。**开关读取必须在热路径上便宜**（本地缓存 + 秒级同步），否则开关本身就是故障源。
- **开关的测试矩阵爆炸**：N 个开关 = 2^N 种组合。对策：开关声明默认值、组合测试只测核心路径、CI 里跑"全开/全关"两组回归。
- **删除开关也是发布**：移除旧分支代码时走正常 PR + 测试，**"功能已全量、代码可删"要写进发布清单**。永远不删的开关 = 永远的死代码分支。
- 易错点：开关默认值是"开"（忘了关闭就直接全量）；开关逻辑散落在几十个 if 里没有统一收口；开关名不可读（`flag1`、`test2`，半年后没人知道它控制什么）；用开关做长期权限管理（应上权限系统，开关系统承担不了审计职责）。

**示例代码**
```java
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class FeatureFlags {
    record Flag(String name, String kind, boolean defaultValue, String expire, String owner) {
    }

    static Map<String, Flag> flags = new LinkedHashMap<>();

    static void flagRegistry() {
        System.out.println("【开关注册表：名字可读、默认值、过期时间、责任人】");
        flags.put("order.batch-cancel", new Flag("order.batch-cancel", "release", false,
                "2026-12-31 全量后删除", "订单组-小王"));
        flags.put("search.vector-rank", new Flag("search.vector-rank", "experiment", false,
                "实验结束（2026-12-15）", "搜索组-小李"));
        flags.put("pay.write-protect", new Flag("pay.write-protect", "ops", false,
                "大促结束删除", "支付组"));
        flags.put("vip.discount", new Flag("vip.discount", "permission", true,
                "长期保留（转权限系统候选）", "产品"));
        for (Flag f : flags.values()) {
            System.out.printf("  %-20s %-11s 默认=%-5s 过期=%-24s 责任=%s%n",
                    f.name(), f.kind(), f.defaultValue(), f.expire(), f.owner());
        }
        System.out.println("  release/experiment 开关必须有删除计划，否则注册表拒绝新增");
        System.out.println();
    }

    static void decoupleDeployRelease() {
        System.out.println("【部署与发布解耦：代码先行，功能后放】");
        List<String> timeline = List.of(
                "11-01 合入批量取消代码（flag=false），部署 3 次，用户无感",
                "11-10 内部账号 flag=true（权限开关），收集反馈",
                "11-15 5% 用户灰度，错误率与转化观察",
                "11-20 全量 flag=true",
                "12-01 确认稳定 -> 删除开关与旧分支代码（一次正常 PR）");
        for (String t : timeline) {
            System.out.println("  " + t);
        }
        System.out.println("  对照：不开开关 = 11-20 一次性大爆炸，出问题只能整体回滚");
        System.out.println();
    }

    static void darkLaunch() {
        System.out.println("【暗发布：真跑但用户无感】");
        record Req(String query, String oldResult, String newResult, String action) {
        }
        List<Req> reqs = List.of(
                new Req("手机壳", "商品A", "商品C", "日志记录 diff，返回旧结果"),
                new Req("退款 政策", "FAQ-3", "FAQ-3", "一致，标记为正常"));
        for (Req r : reqs) {
            System.out.printf("  查询「%s」 旧=%-8s 新=%-8s -> %s%n",
                    r.query(), r.oldResult(), r.newResult(), r.action());
        }
        System.out.println("  一周后统计：新算法 CTR 提升 +2.1% 且 P99 持平 -> 开放灰度");
        System.out.println("  影子流量的三种用途：性能压测、正确性对比、效果实验");
        System.out.println();
    }

    static void testMatrix() {
        System.out.println("【开关组合的测试策略】");
        List<String> rules = List.of(
                "核心链路跑「全关」与「全开」两组回归（CI 固定两档）",
                "开关间声明依赖：B 依赖 A 开启时才生效，注册表校验",
                "组合爆炸（2^N）不可测 -> 限制同模块活跃开关 <= 3",
                "默认值即兜底：开关系统不可用时按默认值执行（fail safe）");
        for (String r : rules) {
            System.out.println("  - " + r);
        }
    }

    public static void main(String[] args) {
        flagRegistry();
        decoupleDeployRelease();
        darkLaunch();
        testMatrix();
    }
}
```

**课后练习**
1. 为你的项目实现一个极简开关服务（配置中心布尔值 + 本地缓存秒级同步），验证热路径读取开销 < 0.1ms。
2. 给一个新功能设计完整的"部署-暗发布-灰度-全量-删开关"时间线，其中每一步的验证动作与责任人写清。
3. 思考题：为什么"僵尸开关"是技术债？从代码分支组合数与认知负担两个角度量化 N 个僵尸开关的维护成本。

---

### 41-09 数据库变更与发布协同

**知识点**
- **发布事故的最大来源不是代码，是代码与数据库变更的时序错配**：代码新版本需要新列，DDL 还没跑 -> 启动即报错；DDL 先跑删了旧列 -> 旧版本还在读 -> 崩。**协同的核心是"每一步状态下，正在运行的版本集合都能正常工作"**。
- **schema 变更必须走迁移工具与版本化脚本**（Flyway/Liquibase）：`V17__add_refund_status.sql` 进代码库，流水线在部署前自动执行，执行记录入库可审计。**禁止手工在库上改结构**（41-05 的漂移话题在数据库上的最严重形态）。
- **兼容性迁移的扩容三步法（expand-contract）**：① expand：加列/加表，新旧代码都兼容；② 部署新版本双读写；③ contract：确认旧版本下线后，删除旧列/旧表。**每一步独立发布，全程新旧可共存**。破坏性操作（DROP/改类型）只出现在 contract 步。
- **反向兼容（rollback-safe）检验**：每个迁移脚本要回答"如果发布失败回滚代码，旧代码在这个 schema 上能不能跑？"——加列可空可以，改列类型不行。**不可反向兼容的变更必须在发布计划里显式声明并准备数据回滚方案**。
- **大表的 DDL 是高危操作**：MySQL 8.0 的 Online DDL 大多 INSTANT/INPLACE，但改列类型、加全文本地索引仍可能长时间锁；DDL 工具（gh-ost/pt-online-schema-change）用影子表+触发器/binlog 同步。**DDL 执行前估算行数与锁表时长，避开高峰，主从延迟告警盯紧**。
- **数据回填（backfill）与 DDL 分离**：加列（秒级）与回填千万行（小时级）分开跑，回填用分批脚本 + 限速，避免一个 UPDATE 锁全表拖垮主从。
- 易错点：迁移脚本没有 down（回滚）路径；expand 和 contract 合并成一次发布（新旧版本无法共存）；DDL 在高峰期执行拖垮主从；回填脚本不分批（undo log 爆炸 + 复制延迟）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.List;

public class DbMigration {
    record Migration(String version, String sql, String kind, String rollbackSafe) {
    }

    static final List<Migration> PLAN = List.of(
            new Migration("V17", "ALTER TABLE orders ADD COLUMN refund_status VARCHAR(16) NULL", "expand",
                    "回滚安全：新列可空，旧代码不读"),
            new Migration("V18", "回填脚本：UPDATE orders SET refund_status='NONE' 批量分批", "backfill",
                    "与 DDL 分离，限速执行"),
            new Migration("V19", "部署新版本（读+写 refund_status）", "deploy", "双版本共存期开始"),
            new Migration("V20", "ALTER TABLE orders DROP COLUMN old_flag", "contract",
                    "不可回滚！必须确认旧版本全部下线"));

    static void expandContract() {
        System.out.println("【expand-contract 三步法：每一步新旧共存都安全】");
        for (Migration m : PLAN) {
            System.out.printf("  %-4s %-6s %-52s %s%n",
                    m.version(), m.kind(), m.sql(), m.rollbackSafe());
        }
        System.out.println("  contract 步骤是单向门：执行前必须确认旧版本实例数为 0");
        System.out.println();
    }

    static void versionTimeline() {
        System.out.println("【时序推演：任何时刻任何版本都能跑】");
        record Phase(String when, String schema, String versions, String verdict) {
        }
        List<Phase> phases = List.of(
                new Phase("T1 expand 后", "+refund_status(NULL)", "v1, v2 均未部署", "v1 正常：不认识新列也无所谓"),
                new Phase("T2 v2 上线", "+refund_status", "v1 与 v2 共存", "v1 读写忽略新列；v2 正常读写"),
                new Phase("T3 v1 下线", "+refund_status(有值)", "仅 v2", "双读写稳定"),
                new Phase("T4 contract", "-old_flag", "仅 v2", "删除列，v2 不依赖旧列"));
        for (Phase p : phases) {
            System.out.printf("  %-14s schema=%-22s 版本=%-12s %s%n",
                    p.when(), p.schema(), p.versions(), p.verdict());
        }
        System.out.println();
    }

    static void ddlRisk() {
        System.out.println("【大表 DDL 的风险分级】");
        String[][] rows = {
                {"加可空列", "MySQL8 INSTANT 秒级", "低风险，流水线直接跑"},
                {"加索引", "INPLACE，不锁写但耗 IO", "评估行数，避开高峰"},
                {"改列类型", "多数重建全表", "用 gh-ost/pt-osc，或拆两列过渡"},
                {"DROP 列", "INSTANT 但不可逆", "contract 步，人工确认"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-10s %-24s %s%n", r[0], r[1], r[2]);
        }
        System.out.println("  执行清单：估行数 -> 预估时长 -> 主从延迟告警就位 -> 错峰执行");
        System.out.println();
    }

    static void backfill() {
        System.out.println("【回填：分批限速，不与 DDL 混跑】");
        long total = 50_000_000, batch = 5_000;
        long batches = total / batch;
        System.out.printf("  5000 万行按每批 %d 行 -> %d 批，每批间隔 100ms 限速%n", batch, batches);
        System.out.println("  批间 sleep 让主从延迟收敛；脚本可断点续跑（记录最大已回填 id）");
        System.out.println("  完成 marker：回填进度表 100% 后才能进入依赖该列的部署");
    }

    public static void main(String[] args) {
        expandContract();
        versionTimeline();
        ddlRisk();
        backfill();
    }
}
```

**课后练习**
1. 为一个真实需求（新增退款状态）写出完整迁移计划：V17~V20 四步 + 每步的回滚方案，标注"单向门"位置。
2. 用 gh-ost（或模拟脚本）体验大表加索引的影子表流程，记录主从延迟变化与限速参数。
3. 思考题：为什么"迁移脚本必须回答反向兼容问题"？举一个发布失败回滚后旧代码在新 schema 上崩溃的例子。

---

### 41-10 发布自动化与审计

**知识点**
- **发布的理想形态是"按钮工程"**：触发 = 打版本 tag；执行 = 流水线全自动（测试 -> 制品 -> 晋升 -> 部署 -> 验证）；人工只出现在两处——prod 放行审批与异常时的回滚决定。**自动化不是减少步骤，是把步骤变成可重复、可审计的机器动作**。
- **发布单（change record）是审计单元**：谁、何时、发布了什么（制品版本）、到哪些环境、变更类型（常规/紧急/回滚）、审批人、验证结果、工单关联。**每一条生产变更都应能从发布单回溯到提交与验证证据**。
- **紧急发布的绿色通道与代价**：紧急修复也走流水线但允许并行审批、跳过部分低风险门禁（如 staging 观察 24h）。**"紧急"是路径更快，不是没有记录**——事故后的"谁改了什么"如果答不出来，紧急发布就成了二次事故源。
- **审批的最小化原则**：审批人要有上下文（变更内容 + 影响面 + 验证证据），自动汇总进审批卡片；高频低风险变更（纯配置、文案）降级为自动放行 + 事后抽查。**审批膨胀 = 团队学会绕过流程**。
- **发布窗口与冻结期**：大促/月末结算冻结发布（只允许 hotfix）；常规发布窗口避开周五下午与节前。冻结期是业务节奏，写进日历并自动拦截。
- **度量发布工程**：部署频率、变更前置时间（commit->prod）、变更失败率、MTTR——DORA 四指标。**度量驱动改进，而不是 KPI 表演**。
- 易错点：跳板机上手工 `kubectl set image`（无记录无审计）；发布单事后补填（等于没有）；紧急发布习惯化（每次都说紧急，流程形同虚设）；审批链条 5 层（变更前置时间被审批拖到一周）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ReleaseAudit {
    record ReleaseRecord(String id, String artifact, String operator, String approver,
                         String type, String result, String refs) {
    }

    static final List<ReleaseRecord> LEDGER = List.of(
            new ReleaseRecord("R-3001", "order:2.4.1-142", "ci-bot", "lead-wang",
                    "常规", "成功：金丝雀 1h 全量", "JIRA-1234"),
            new ReleaseRecord("R-3002", "order:2.4.2-143", "ci-bot", "lead-wang",
                    "紧急", "成功：跳过 staging 观察，事故修复", "INC-456"),
            new ReleaseRecord("R-3003", "order:2.4.1-141", "ci-bot", "auto(回滚)",
                    "回滚", "成功：金丝雀错误率触发自动回退", "R-3001"));

    static void ledger() {
        System.out.println("【发布台账：每条生产变更一行】");
        for (ReleaseRecord r : LEDGER) {
            System.out.printf("  %-7s %-18s %-9s %-10s %-4s %s%n        %s%n",
                    r.id(), r.artifact(), r.operator(), r.approver(), r.type(), r.result(), r.refs());
        }
        System.out.println("  可回答：生产现在跑什么？谁批的？出事时改了什么？");
        System.out.println();
    }

    static void emergencyLane() {
        System.out.println("【紧急通道：更快，而不是更少记录】");
        Map<String, String> normal = new LinkedHashMap<>();
        normal.put("门禁", "全量测试 + staging 观察 24h + 金丝雀");
        normal.put("审批", "双人（变更人 + 负责人）");
        Map<String, String> emergency = new LinkedHashMap<>();
        emergency.put("门禁", "全量测试 + 金丝雀（跳过 staging 观察期）");
        emergency.put("审批", "单人 + 事后 24h 内补复盘");
        System.out.println("  常规：" + normal);
        System.out.println("  紧急：" + emergency);
        System.out.println("  共同点：发布单、制品版本、审计记录一样不少");
        System.out.println("  约束：紧急发布占比 > 10% -> 流程有问题，触发复盘");
        System.out.println();
    }

    static void dora() {
        System.out.println("【DORA 四指标：发布工程的体检表】");
        record Metric(String name, String def, String elite, String ours) {
        }
        List<Metric> ms = List.of(
                new Metric("部署频率", "prod 发布次数/天", "按需（日多次）", "2 次/周"),
                new Metric("变更前置时间", "commit -> prod", "< 1 天", "3 天"),
                new Metric("变更失败率", "引发降级/回滚的比例", "< 15%", "8%"),
                new Metric("MTTR", "故障恢复时长", "< 1 小时", "40 分钟"));
        for (Metric m : ms) {
            System.out.printf("  %-12s %-18s 精英=%-14s 我们=%s%n",
                    m.name(), m.def(), m.elite(), m.ours());
        }
        System.out.println("  短板定位：前置时间 3 天主要卡在人工审批 -> 审批最小化");
    }

    public static void main(String[] args) {
        ledger();
        emergencyLane();
        dora();
    }
}
```

**课后练习**
1. 为你的团队设计发布单模板与台账存储（表格或数据库），实现"发布单 -> 制品 -> 提交"三层链接可点击跳转。
2. 统计你团队过去一个月的 DORA 四指标，找出最差一项并给出改进动作与目标值。
3. 思考题：为什么"紧急发布跳过审批但不能跳过记录"？从事后追责与流程信任两个角度分析。

---

### 41-11 综合案例：一条完整的发布流水线

**知识点**
- **把本章串成一条端到端流水线**：提交（Conventional Commits）-> PR 流水线（编译/检查/测试）-> 合入 main -> 构建制品（版本号+签名）-> 自动部署 test -> 冒烟 -> 人工放行 prod -> 金丝雀 1%~100%（自动判据）-> 发布单归档 -> 观察与复盘。**第 37 章的容器、第 41 章的流程在此汇合**。
- **每一步的"通过标准"都要机器可判**：测试通过率 100%、扫描无 Critical、冒烟接口 200、金丝雀错误率相对基线 +2% 以内。**写不进判据的验证就是装饰**。
- **故障演练贯穿**：流水线红灯演练（挂掉一个测试看拦截）、金丝雀自动回退演练（制造错误率飙升）、回滚演练（切回旧制品的实测时长）。**演练过的故障才叫预案，没演练过的叫想象**。
- **一次真实发布的时间预算**：PR 验证 8 分钟、构建 3 分钟、test 冒烟 5 分钟、金丝雀全程 90 分钟——**从 commit 到全量约 2 小时，全程无需盯守**（金丝雀自动判据接管）。
- **发布文化**：发布是日常动作不是仪式——高频小步、失败自动回退、台账自动记录。**发布越频繁，单次风险越小**（变更小、演练多、回滚熟练）。
- 易错点：流水线全绿但没人看过部署结果（验证缺失）；金丝雀判据写了没接自动回退（指标只是装饰）；发布单与制品脱节（审计断链）；把本章当作一次性建设（发布工程是持续运营的）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.List;

public class PipelineCase {
    enum StepResult {
        PASS, FAIL, ROLLBACK
    }

    record Phase(int no, String name, String action, String gate, StepResult result) {
    }

    static void fullPipeline() {
        System.out.println("【order 服务 2.4.1-142 的完整发布时间线】");
        List<Phase> phases = List.of(
                new Phase(1, "提交", "feat(order): 批量取消 + 单测", "Conventional 格式校验", StepResult.PASS),
                new Phase(2, "PR 流水线", "编译30s + lint/scan60s + 单测180s", "全绿 + 1 人审批", StepResult.PASS),
                new Phase(3, "构建制品", "jar + 镜像，签名入库", "版本号含提交摘要", StepResult.PASS),
                new Phase(4, "test 环境", "自动部署 + 冒烟 5 项接口", "接口全部 200", StepResult.PASS),
                new Phase(5, "staging", "脱敏数据回归 + 压测 P99 达标", "无 Critical 漏洞", StepResult.PASS),
                new Phase(6, "prod 金丝雀", "1% -> 5% -> 25% -> 50% -> 100%",
                        "错误率相对基线 +2% 内", StepResult.PASS),
                new Phase(7, "归档", "发布单 R-3001 自动生成", "制品/审批/证据齐全", StepResult.PASS));
        int minute = 0;
        for (Phase p : phases) {
            System.out.printf("  T+%dmin 步骤%d %-12s %s%n        闸门：%s -> %s%n",
                    minute, p.no(), p.name(), p.action(), p.gate(), p.result());
            minute += switch (p.no()) {
                case 1, 2 -> 8;
                case 3 -> 3;
                case 4, 5 -> 5;
                default -> 90;
            };
        }
        System.out.printf("  commit 到全量约 %d 分钟，全程无需盯守%n", minute);
        System.out.println();
    }

    static void drills() {
        System.out.println("【三项常态化演练】");
        record Drill(String name, String action, String expect, String measured) {
        }
        List<Drill> drills = List.of(
                new Drill("红灯拦截", "在 PR 分支故意提交失败测试", "流水线 FAIL，PR 无法合并",
                        "实测拦截生效"),
                new Drill("金丝雀回退", "向金丝雀实例注入 5xx", "错误率超阈值自动回退并告警",
                        "实测 2 分 10 秒完成回退"),
                new Drill("回滚", "部署上一版本制品", "回滚到稳定版，时长 < 2 分钟",
                        "实测 1 分 30 秒"));
        for (Drill d : drills) {
            System.out.printf("  %-8s %s%n    期望：%s%n    实测：%s%n",
                    d.name(), d.action(), d.expect(), d.measured());
        }
        System.out.println("  演练频率：每迭代一次，结果进发布复盘");
        System.out.println();
    }

    static void timeBudget() {
        System.out.println("【发布时间预算与人工介入点】");
        List<String> rows = List.of(
                "自动段（约 50 分钟）：PR 验证 -> 构建 -> test/staging 部署与验证",
                "人工点 1：prod 放行审批（1 分钟，看到的是自动汇总的变更内容+验证证据）",
                "自动段（约 90 分钟）：金丝雀放量，自动判据接管（暂停/告警/回退）",
                "人工点 2（仅异常）：回退决定或紧急通道");
        for (String r : rows) {
            System.out.println("  " + r);
        }
        System.out.println("  原则：人工只出现在「决策」，一切「执行」都自动化");
    }

    public static void main(String[] args) {
        fullPipeline();
        drills();
        timeBudget();
    }
}
```

**课后练习**
1. 把本章时间线在你的项目真实跑一遍（可以缩小规模），记录每步实际耗时与"通过证据"，形成你的发布手册第一版。
2. 实现金丝雀自动判据与自动回退的闭环（模拟指标流即可），并做一次"注入 5xx"演练记录回退时长。
3. 思考题：为什么说"发布越频繁，单次风险越小"？用本章 DORA 指标与演练机制解释高频发布的风险控制闭环。
