### 52-01 需求分析与容量估算

**知识点**
- **系统设计的第一步不是画架构图，是量化问题**：多少用户、多少 QPS、多少数据、多大延迟容忍——**没有数字的架构讨论都是玄学**。容量估算给后续每个决策（缓存/分片/引擎）提供标尺。
- **估算的标准动作**：① 日活/峰值比例（日活 100 万 -> 峰值 QPS = 日活 × 人均请求数 ÷ 86400 × 集中系数，通常再乘 3~5 倍峰值因子）；② **读写比**（读多写少决定缓存收益，写多决定分库压力）；③ 数据增长（三年后量，39-01）；④ 单请求成本（token/字节/DB 行）。
- **估算的价值在范围不是精度**：算出"峰值 QPS 约 2000±500"就足够定架构——**不需要精确到个位**，需要的是量级正确（100 还是 10000 的差别是架构级的）。
- **非功能需求的量化**：可用性目标（99.9% = 月 43 分钟）、延迟 P99、一致性级别（45-02）、成本上限——**每个都写进需求文档**，否则架构评审没有裁决依据。
- **反向估算校验需求**：估算出"需要 20 台机器 + 5 人团队维护"时，回头看需求是否真的值得——**容量估算也是可行性检查**（商业模式撑不起架构成本就砍需求）。
- 易错点：按峰值常备容量（47-08 弹性才是答案）；把日活当 QPS（差两个数量级）；忽略读写比（缓存决策失效）；非功能需求不量化（"要快""要稳"无法验收）。

**示例代码**
```java
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RequirementCapacity {
    static void estimateFlow() {
        System.out.println("【订单系统容量估算五步】");
        long dau = 1_000_000;
        double reqPerUser = 10;                 // 人均下单相关请求/日
        double concentration = 0.4;             // 集中在 20% 时段（86400×0.2）
        double peakFactor = 3.0;                // 大促/突发峰值因子
        long avgQps = Math.round(dau * reqPerUser / (86400 * 0.2));
        long peakQps = Math.round(avgQps * peakFactor);
        System.out.printf("  日活 %d × 人均 %.0f 请求 = 日请求 %.0f 万%n", dau, reqPerUser, dau * reqPerUser / 1e4);
        System.out.printf("  集中时段（20%% 天时间）平均 QPS ≈ %d%n", avgQps);
        System.out.printf("  峰值 QPS ≈ %d（×%.0f 峰值因子）%n", peakQps, peakFactor);
        System.out.printf("  读写比 10:1 -> 读 %d / 写 %d QPS（写是分库依据，39 章）%n",
                peakQps / 11 * 10, peakQps / 11);
        System.out.println();
    }

    static void nfrTable() {
        System.out.println("【非功能需求量化表（写进需求文档）】");
        Map<String, String> nfr = new LinkedHashMap<>();
        nfr.put("可用性", "核心链路 99.95%（月 22 分钟预算，42-05）");
        nfr.put("延迟", "下单 P99 < 300ms；列表 P99 < 50ms");
        nfr.put("一致性", "库存扣减强一致；评论最终一致（45-02）");
        nfr.put("数据增长", "订单三年 5.4 亿行 -> 16 分片（39-01）");
        nfr.put("成本上限", "基础设施 ≤ GMV 的 3%");
        for (Map.Entry<String, String> e : nfr.entrySet()) {
            System.out.printf("  %-6s %s%n", e.getKey(), e.getValue());
        }
        System.out.println("  每项都可验收 —— 架构评审的裁决依据");
        System.out.println();
    }

    static void feasibilityCheck() {
        System.out.println("【反向校验：架构成本 vs 商业价值】");
        record Cost(String item, String estimate, String verdict) {
        }
        List<Cost> costs = List.of(
                new Cost("基础设施", "18 卡 GPU + 40 节点 ≈ 月 30 万", "占 GMV 2.5% -> 通过"),
                new Cost("人力", "5 人长期维护", "核心域值得"),
                new Cost("风险", "新架构学习成本 3 个月", "纳入排期"));
        for (Cost c : costs) {
            System.out.printf("  %-6s %-28s %s%n", c.item(), c.estimate(), c.verdict());
        }
        System.out.println("  估算的终点是决策：做、不做、还是砍需求");
    }

    public static void main(String[] args) {
        estimateFlow();
        nfrTable();
        feasibilityCheck();
    }
}
```

**课后练习**
1. 为你的业务做完整容量估算（QPS/读写比/数据增长/非功能表），每个数字标注推导过程。
2. 找一个"要快、要稳"的模糊需求，改写成可验收的非功能量化表。
3. 思考题：为什么容量估算"要范围不要精度"？从架构决策的敏感性（量级变化才改变决策）分析。

---

### 52-02 架构决策记录 ADR

**知识点**
- **ADR（Architecture Decision Record）把"为什么这么设计"变成资产**：三个月后新人问"为什么用模块化单体不用微服务？"——没有 ADR 就只能靠考古或问离职的人。**ADR 记录：背景、选项、决策、后果**，一页一个决策。
- **ADR 的结构（轻量模板）**：# 决策标题 / 日期 / 状态（提议中/已接受/已废弃）/ **背景**（什么问题逼出这个决策）/ **选项**（至少 2~3 个，各自利弊）/ **决策**（选了什么）/ **后果**（正面+负面+回顾条件）。**负面后果必须诚实记录**——这是 ADR 区别于宣传文档的关键。
- **ADR 与评审的关系**：ADR 是评审的输入与输出——评审通过即状态"已接受"；**决策变更 = 新 ADR 取代旧 ADR**（不修改旧的，保留决策历史，49 章事件溯源思想）。
- **哪些决策值得 ADR**：影响面大（全局架构/存储选型/并发模型 50-07）、难回退（分库分表/事件溯源）、有争议（团队分歧大）——**日常小决策不写**（写 ADR 的成本也要管理，40-10）。
- **存放在哪**：与代码同仓库（docs/adr/）——**代码与决策同源演进**，PR 评审时决策可见。
- 易错点：ADR 只记结论不记选项（新人无法理解决策依据）；负面后果不写（决策不可信）；废弃的 ADR 被删除（历史丢失）；ADR 与代码漂移（决策已变文档不更新）。

**示例代码**
```java
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ArchitectureDecision {
    static void adrTemplate() {
        System.out.println("【ADR-007：订单系统并发模型选型】");
        Map<String, String> adr = new LinkedHashMap<>();
        adr.put("状态", "已接受（2026-11）");
        adr.put("背景", "大促下单峰值 QPS 3000，现有 200 线程池并发上限不足；WebFlux 团队不熟");
        adr.put("选项A", "迁移 WebFlux + R2DBC：吞吐达标，但全链路改造成本 3 人月，团队零经验");
        adr.put("选项B", "虚拟线程（JDK21）：零代码改造 + 连接池重评估，团队学习成本近零");
        adr.put("选项C", "线程池扩到 1000：内存 1GB+，上下文切换劣化 P99，不推荐");
        adr.put("决策", "选项 B（六项打分 50-07：存量栈/团队熟练度/JDK 已满足）");
        adr.put("正面后果", "零改造获得并发扩展；同步代码可维护");
        adr.put("负面后果", "需排查 pinning；连接池成为新瓶颈需重评估；JDK24 前避开 synchronized");
        adr.put("回顾条件", "并发需求超 1 万连接或 JDK 升级时重审");
        for (Map.Entry<String, String> e : adr.entrySet()) {
            System.out.printf("  %-6s %s%n", e.getKey(), e.getValue());
        }
        System.out.println();
    }

    static void adrDiscipline() {
        System.out.println("【ADR 纪律】");
        List<String> rules = List.of(
                "一页一决策：超过两页说明把多个决策混在一起",
                "选项至少 2 个：只有一个选项的决策不需要 ADR（照做即可）",
                "负面后果诚实：写出来才有人帮你看风险",
                "决策取代而非修改：ADR-012 废弃 ADR-007，历史保留",
                "与代码同仓库：PR 里可见，评审有据");
        for (String r : rules) {
            System.out.println("  - " + r);
        }
        System.out.println();
    }

    static void adrInventory() {
        System.out.println("【全书的 ADR 候选清单（哪些决策值得记录）】");
        List<String> candidates = List.of(
                "37 章：基础镜像选型（jre/alpine/distroless 的取舍）",
                "39 章：分片键选型与分片数（不可回退，必须记录）",
                "41 章：分支策略与发布阶梯",
                "42 章：日志保留期与成本策略",
                "43 章：流批一体边界（哪些场景不上流）",
                "45 章：共识集群规模与跨机房取舍",
                "46 章：表格式选型（Iceberg vs Hudi）",
                "48 章：模型池与路由策略（44-08）",
                "50 章：并发模型（本章示例）");
        for (String c : candidates) {
            System.out.println("  - " + c);
        }
        System.out.println("  检验：这些决策三个月后新人能否独立理解为什么？");
    }

    public static void main(String[] args) {
        adrTemplate();
        adrDiscipline();
        adrInventory();
    }
}
```

**课后练习**
1. 为你团队最近的一个架构决策补写 ADR（含至少 2 个选项与诚实的负面后果）。
2. 盘点你的系统：列出 5 个"应该有 ADR 但没有"的决策，补写一份并组织评审。
3. 思考题：为什么 ADR 记录"被否掉的选项"与"选中的选项"同样重要？从决策可审计与新场景复用判断两个角度分析。

---

### 52-03 技术选型决策树：全书选型回顾

**知识点**
- **技术选型的方法论只有一句话：从问题出发，不是从技术出发**——每一章的选型都可以还原成"什么问题 -> 候选 -> 判据 -> 结论"的决策树。本章把 48 章的选型串成一张可复用的决策地图。
- **存储选型树**：点查+事务 -> MySQL（行存+索引 39 章）；全文/多维列表 -> ES（38 章）；KV 缓存 -> Redis（25 章）；时序/日志 -> 分层存储（42-03）；分析扫描 -> Parquet 湖表（46 章）；海量分片 -> 分库分表（39 章）。**判据：访问模式（点查/扫描）+ 一致性要求 + 数据规模**。
- **并发模型树**：IO 密集普通业务 -> 虚拟线程；流式背压 -> WebFlux；CPU 密集 -> 平台池；超高连接 -> Netty（50 章）。
- **一致性方案树**：同聚合 -> 本地事务；跨域 -> 事件+幂等（outbox）；资金冻结 -> TCC；对账兜底永远在（39/51 章）。
- **AI 集成树**：知识问答 -> RAG；格式稳定 -> Prompt+校验；风格能力 -> 微调；简单任务 -> 小模型（40/44 章路由）。
- 易错点：从流行度选型（K8s/微服务/事件溯源全套硬上）；选型无判据（"大家都用"不是理由）；一次选型终身不变（选型是可逆性优先，49 章演进思想）；忽略运维成本（引入组件=引入一套运维）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TechSelectionTree {
    static void storageTree() {
        System.out.println("【存储选型决策树】");
        String[][] rows = {
                {"点查+强事务", "MySQL（行存+索引 39-02）", "访问模式：单行读写"},
                {"全文/多维检索", "ES（38 章倒排）", "分词/评分/聚合需求"},
                {"热点缓存", "Redis（25 章五种结构）", "读多写少 + 可容忍失效"},
                {"海量分析扫描", "Parquet 湖表（46-02 列存）", "宽表少列扫描"},
                {"超大规模单表", "分库分表（39-05）", "先治理后拆分"},
                {"审计溯源", "事件流（49-06）", "审计是业务需求本身"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-10s %-24s 判据=%s%n", r[0], r[1], r[2]);
        }
        System.out.println();
    }

    static void selectionRecord() {
        System.out.println("【一次完整选型的记录格式】");
        Map<String, String> record = new LinkedHashMap<>();
        record.put("问题", "商品搜索 LIKE 撑不住（38-01）");
        record.put("候选", "MySQL 全文索引 / ES / 自建倒排");
        record.put("判据", "分词质量、运维成本、与现有栈集成、数据同步成熟度");
        record.put("结论", "ES + CDC 同步（38-09）；自建倒排学习价值高但生产不选");
        record.put("回退", "ES 不可用 -> 降级数据库查询（体验降级可用）");
        for (Map.Entry<String, String> e : record.entrySet()) {
            System.out.printf("  %-4s %s%n", e.getKey(), e.getValue());
        }
        System.out.println("  回退方案是选型的一部分 —— 不可逆选型要格外谨慎（45 章教训）");
        System.out.println();
    }

    static void antiPatterns() {
        System.out.println("【选型反模式】");
        List<String> anti = List.of(
                "简历驱动：为面试加分引入不需要的组件（运维税）",
                "一步到位：业务 10 人团队上来就全套微服务+湖仓+K8s",
                "跑分定生死：实验室跑分 ≠ 自己的 IO 形态（50-09）",
                "忽视运维：引入 Nacos/MQ/ES 各一套，运维人数没加",
                "不可逆选型不谨慎：分片键/事件 schema 想当然",
                "选型不写 ADR：三个月后无人知道为什么");
        for (String a : anti) {
            System.out.println("  - " + a);
        }
        System.out.println("  每一条反模式都对应一个真实失败案例的原型");
    }

    public static void main(String[] args) {
        storageTree();
        selectionRecord();
        antiPatterns();
    }
}
```

**课后练习**
1. 把你团队最近 3 次技术选型还原成"问题->候选->判据->结论"格式，检查缺失项。
2. 基于本章决策树，为你的业务做一次完整存储选型（含回退方案）并写 ADR。
3. 思考题：为什么"可逆性"是选型的隐含第一判据？从 45 章共识成员变更、39 章分片键等不可逆决策的代价归纳。

---

### 52-04 系统设计题方法论

**知识点**
- **系统设计题（面试/评审通用）考的是"结构化的思考过程"而不是"背过的架构"**：标准四步——① **澄清需求**（功能边界 + 非功能量化，52-01）；② **估算与约束**（QPS/存储/带宽）；③ **高层设计**（核心组件与数据流，先跑通主流程）；④ **深入细节**（按面试官/评审关注点展开：数据模型/一致性/故障）。**每步都有产出物，边画边讲**。
- **设计的展开顺序：主流程优先**——先画"一个请求的完整生命"（下单：网关->订单->库存->支付），**再展开旁支**（缓存/搜索/推荐）——从主流程开始永远不会跑题；细节深挖由提问方引导（评审/面试是对话不是演讲）。
- **必备的讨论点清单**：数据模型（核心表与索引）、一致性方案（39/45 章光谱）、容量与扩展（分片/缓存）、故障与降级（每一层的失败模式）、权衡取舍（每个决策的代价）——**没有取舍讨论的设计是背图**。
- **常见的失败模式**：上来就画全景大图（没有需求澄清）；只讲 happy path（没有故障思考）；被追问细节就慌（平时没有量化习惯）；过度设计（20 人团队讲 K8s 多集群）。
- **与真实架构评审的关系**：方法论同一套——**评审就是多人、有上下文的系统设计题**；52-01/02 的估算与 ADR 是设计题的书面版。
- 易错点：背"标准答案架构"（面试官一追问就露馅）；数字张口就来（无推导过程）；只画图不说话（沟通占分一半）；把熟悉栈当万能答案（每题都"上 Redis 上 MQ"）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.List;

public class SystemDesignMethod {
    static void fourSteps() {
        System.out.println("【系统设计四步法：以「设计秒杀系统」为例】");
        List<String> steps = List.of(
                "① 澄清：100 件商品 × 10 万人抢？支付限时？超卖零容忍？（先问清再动手）",
                "② 估算：瞬时 20 万 QPS 打在单商品上 -> 单点热 key -> 核心矛盾是热点行（39 章）",
                "③ 高层：网关限流 -> Redis 预扣（32/44 章）-> 异步落库 -> MQ 削峰 -> 支付",
                "④ 深挖：Redis 挂了怎么办（L4 兜底）/ 重复下单（幂等）/ 库存与订单一致（39-09）");
        for (String s : steps) {
            System.out.println("  " + s);
        }
        System.out.println("  每一步有产出物：需求清单 / 数字 / 架构图 / 关键决策表");
        System.out.println();
    }

    static void mainPathFirst() {
        System.out.println("【主流程优先：先跑通再展开】");
        List<String> order = List.of(
                "1) 一个请求的完整生命（主链路 6~8 个组件）",
                "2) 数据模型（核心表 + 关键索引）",
                "3) 旁支展开（按提问方向：缓存策略/搜索/风控）",
                "4) 故障思考（每个组件挂了会怎样）");
        for (String o : order) {
            System.out.println("  " + o);
        }
        System.out.println("  反例：先画全景大图（10 个组件互相连线）-> 讲不清主流程 -> 失败");
        System.out.println();
    }

    static void tradeoffTalking() {
        System.out.println("【取舍表达模板：每个决策都带代价】");
        String[][] rows = {
                {"选 Redis 预扣", "吞吐解决热点", "代价：Redis 依赖 + 一致性靠对账兜底"},
                {"选 MQ 削峰", "写入平滑", "代价：到账延迟 + 消费积压风险"},
                {"选本地消息表", "可靠投递", "代价：多一张表 + 投递延迟"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-10s 收益：%-12s 代价：%s%n", r[0], r[1], r[2]);
        }
        System.out.println("  能主动说出代价 = 真理解（49 章 ADR 的负面后果纪律）");
    }

    public static void main(String[] args) {
        fourSteps();
        mainPathFirst();
        tradeoffTalking();
    }
}
```

**课后练习**
1. 用四步法完整做一道系统设计题（秒杀/短链/ feed 流任选），限时 45 分钟并录音复盘。
2. 为你的系统写"主流程一页图"（一个请求的完整生命 + 数据模型），作为评审的开场材料。
3. 思考题：为什么"主动说出代价"比"方案更优"更能体现水平？从决策可信度与工程成熟度的信号分析。
