### 52-05 非功能需求与权衡

**知识点**
- **架构的本质是非功能需求的权衡**：功能需求（下单/支付）大家都能实现；**拉开差距的是非功能**——一致性级别、可用性目标、延迟分位、成本上限、可演进性（49 章）。52-01 量化的非功能表，本章讲它们之间的**交换关系**。
- **不可能三角与局部三角**：全局的 CAP（45-02）；**局部的"一致-可用-成本"三角**——强一致+高可用+低成本不可兼得（强一致要共识集群=成本；高可用要冗余=成本）。**每条业务线在三角上选自己的点**（45-02 分级承诺）。
- **延迟 vs 一致性**：读本地副本（快）vs 读多数派（准）——45-06 K8s 分级读的普适化：**每个接口声明自己的取舍**。
- **可用性 vs 复杂度**：双活容灾（RTO 分钟级）的代价是双向同步与冲突处理（47-09）——**复杂度本身就是故障源**：99.99% 的目标配 99.9% 的容灾系统，反而更不可用。**权衡的终极指标是"端到端可用性"**。
- **演进性 vs 简单性**：事件溯源/六边形架构提升演进能力，代价是当下复杂度（49 章）——**判断标准是系统寿命**：三年以上核心域值得投资演进性；短期项目简单优先。
- **成本 vs 一切**：每个技术决策最终都有钱的味道（44-09/48-09）——**成本是非功能需求的仲裁者**：无法证明 ROI 的复杂度都该砍（52-01 反向校验）。
- 易错点：所有指标都拉满（没有取舍的设计必然在某处崩）；用组件数量衡量架构先进性；忽视团队认知成本（复杂度税最终由人交）；非功能需求不进验收（上线后才暴露）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.List;

public class NonFunctionalTradeoff {
    record Tradeoff(String axis, String sideA, String sideB, String arbiter) {
    }

    static void tradeoffMap() {
        System.out.println("【五大权衡轴与仲裁者】");
        List<Tradeoff> axes = List.of(
                new Tradeoff("一致性 vs 延迟", "读多数派（准）", "读本地副本（快）", "按接口分级（45-02）"),
                new Tradeoff("可用性 vs 复杂度", "双活容灾", "简单主备", "端到端可用性（47-09）"),
                new Tradeoff("演进性 vs 简单性", "事件溯源/六边形", "标准三层", "系统寿命（49 章）"),
                new Tradeoff("性能 vs 成本", "大池常驻", "按需弹性", "成本占比 ROI（48-09）"),
                new Tradeoff("吞吐 vs 隔离", "共享集群", "独立部署", "爆炸半径（47 章队列）"));
        for (Tradeoff t : axes) {
            System.out.printf("  %-14s A=%-12s B=%-12s 仲裁=%s%n",
                    t.axis(), t.sideA(), t.sideB(), t.arbiter());
        }
        System.out.println("  每条业务线在每条轴上选自己的点 —— 没有全局最优解");
        System.out.println();
    }

    static void complexityTax() {
        System.out.println("【复杂度税：每引入一个组件的隐性成本】");
        List<String> taxes = List.of(
                "运维税：一套监控/升级/故障预案（42 章观测 + 47 章演练）",
                "认知税：团队学习与排障技能栈（50-07 团队权重）",
                "故障面税：新组件自身会挂（51-10 隔离纪律）",
                "集成税：协议/版本/兼容矩阵（44-08 网关收敛）",
                "决策：组件引入的 ROI = 收益 - 四项税（44-10 评测思维的架构版）");
        for (String t : taxes) {
            System.out.println("  - " + t);
        }
        System.out.println();
    }

    static void e2eAvailability() {
        System.out.println("【端到端可用性：复杂度反噬的计算】");
        double coreApp = 0.9999, disasterSystem = 0.999, db = 0.9995;
        double chain = coreApp * disasterSystem * db;
        System.out.printf("  应用 99.99%% × 容灾系统 99.9%% × DB 99.95%% = %.4f%%%n", chain * 100);
        System.out.println("  串联链路：任何一环的复杂度都在稀释端到端可用性");
        System.out.println("  结论：为提升 0.05% 引入 0.1% 故障率的组件是负收益");
        System.out.println("  正确姿势：并行冗余（1-(1-a)^n）而非串联叠加");
    }

    public static void main(String[] args) {
        tradeoffMap();
        complexityTax();
        e2eAvailability();
    }
}
```

**课后练习**
1. 为你的系统在五条权衡轴上标注当前的位置，找出一条"该移动但没移动"的轴并给出迁移方案。
2. 计算"为提升可用性引入新组件"的端到端可用性变化（串联 vs 并行），验证复杂度反噬。
3. 思考题：为什么"成本是非功能需求的仲裁者"？用 ROI 框架重新审视你团队最近引入的一个组件。

---

### 52-06 全书知识地图

**知识点**
- **48 章 + 4 章进阶的知识可以折叠成五条主线**：① **语言与运行时线**（2~14 章：语法->OOP->API->JVM 心智）；② **业务工程线**（15~29 章：集合->IO->并发->网络->DB->框架->MQ->微服务->实战）；③ **深度与质量线**（30~36 章：JVM 调优->设计模式->并发 JUC->测试->算法->网络->安全）；④ **平台与规模线**（37~48 章：容器->搜索->海量数据->AI->CI/CD->可观测->流处理->共识->湖仓->云原生->推理）；⑤ **资金与收官线**（49~52 章：DDD->虚拟线程->支付->总纲）。**每条线内部是依赖链，线之间是支撑关系**。
- **知识地图的用法**：排障时定位章节（"接口慢"-> 39 索引/42 观测/30 JVM）、学习时查漏（每章课后练习即自测）、设计时索引选型（52-03 决策树）。
- **全书反复出现的十个核心思想**：幂等、最终一致与对账、事件驱动、分层与边界、缓存、限流降级、可观测、灰度与回滚、成本意识、演进式改造——**技术会过时，思想可迁移**（52-11 持续学习的根基）。
- **章与章的显式协作**（本书设计时的呼应网络）：38 章 CDC 依赖 39 章 ID、44 章 Agent 依赖 42 章观测、48 章推理依赖 47 章弹性、51 章支付依赖 36/39/41/42 章——**真实系统也是这样编织的**。
- 易错点：学完就散（章节间无连接，用时想不起）；只记工具不记思想（框架三年一换）；没有自己的知识库（笔记/ADR/复盘缺失）。

**示例代码**
```java
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class KnowledgeMap {
    static void fiveLines() {
        System.out.println("【五条主线（章号即坐标）】");
        Map<String, String> lines = new LinkedHashMap<>();
        lines.put("语言运行时", "2 语法 -> 8 OOP -> 12 API -> 14 类库 -> 30 JVM");
        lines.put("业务工程", "15 集合 -> 20 DB -> 23 MyBatis -> 24/26 Spring -> 28 MQ -> 29 微服务");
        lines.put("深度质量", "31 模式 -> 32 JUC -> 33 测试 -> 34 算法 -> 35 网络 -> 36 安全");
        lines.put("平台规模", "37 容器 -> 38 搜索 -> 39 海量 -> 40/44 AI -> 41 CI/CD -> 42 观测 -> 43 流 -> 45~48 平台");
        lines.put("资金收官", "49 DDD -> 50 并发新范式 -> 51 支付 -> 52 总纲");
        for (Map.Entry<String, String> e : lines.entrySet()) {
            System.out.printf("  %-6s %s%n", e.getKey(), e.getValue());
        }
        System.out.println();
    }

    static void tenIdeas() {
        System.out.println("【贯穿全书的十个思想（技术会过时，思想迁移）】");
        List<String> ideas = List.of(
                "幂等：39 迁移 / 43 流 / 51 支付 —— 重复执行结果不变",
                "最终一致+对账：39-09 / 43-09 / 51-05 —— 快与对的收敛装置",
                "事件驱动：28 MQ / 49 领域事件 —— 解耦与可扩展的粘合剂",
                "分层与边界：29 上下文 / 49 聚合 —— 复杂度的切割术",
                "缓存：25 Redis / 38-08 分页 / 48-09 推理缓存 —— 便宜的层次",
                "限流降级：29 网关 / 44-08 阶梯 / 51-10 —— 过载时的秩序",
                "可观测：42 平台 / 48-10 推理 —— 从黑盒到证据链",
                "灰度与回滚：41 发布 / 44-10 评测 —— 变更的安全网",
                "成本意识：44-09 / 46-03 / 48-09 —— 工程决策的仲裁者",
                "演进式改造：49-10 / 50-10 —— 系统是长出来的不是画出来的");
        for (String i : ideas) {
            System.out.println("  - " + i);
        }
        System.out.println();
    }

    static void crossRefs() {
        System.out.println("【跨章呼应网络（真实系统同样编织）】");
        List<String> refs = List.of(
                "38 ES 的数据 <- 39 章 CDC 与分布式 ID 基因",
                "44 Agent 的可观测 <- 42 章 trace 与告警",
                "48 推理弹性 <- 47 章 HPA 与节点池",
                "51 支付的审计 <- 49 章事件溯源思想",
                "41 流水线的质量门禁 <- 33 章测试金字塔",
                "52 的每个选型 <- 各章的判据与教训");
        for (String r : refs) {
            System.out.println("  " + r);
        }
        System.out.println("  学习建议：遇到新问题时按地图索引章节，而不是线性重读");
    }

    public static void main(String[] args) {
        fiveLines();
        tenIdeas();
        crossRefs();
    }
}
```

**课后练习**
1. 画出你自己的知识地图（五条主线 + 你的薄弱章标注），制定补强计划。
2. 从十个思想中选两个，各找一个**本书之外**的应用场景（证明思想可迁移）。
3. 思考题：为什么"思想比工具长寿"？用本书中已被淘汰或正在演进的具体技术（如 Hystrix -> Resilience4j -> Mesh）佐证。

---

### 52-07 毕业能力清单与自测

**知识点**
- **毕业的标志不是"学完"，是"能独立交付"**：能力清单按四级递进——**能实现**（照需求写出可运行代码）、**能排障**（生产问题独立定位）、**能设计**（新系统从需求到架构）、**能演进**（老系统安全改造）。**每一级都有可执行的验收证据**。
- **自测的方法是"做"而不是"答"**：每个能力项配一个实操任务（建索引排慢查询、写幂等支付链路、做一次故障演练）——**做出来的才算数**（33 章测试思想对学习本身的应用）。
- **清单的三个来源**：全书章节练习（每课课后题）、综合案例的实现（各章综合案例真做）、工作实战（真实工单/复盘）。**三者交叉验证**。
- **薄弱点的定位与补强**：自测不通过 -> 回对应章（52-06 地图索引）-> 重做课后练习 -> 再实战验证——**学习闭环**（44-10 评测驱动的自我版）。
- 易错点：只看视频/笔记不写代码（"看会了"是幻觉）；清单全打勾但无证据（自我感动）；只补短板不建长板（工程价值来自长板）；学完不复盘（错误模式重复出现）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class GraduationChecklist {
    record Ability(String level, String item, String evidence) {
    }

    static void checklist() {
        System.out.println("【毕业能力清单（每项都要实操证据）】");
        List<Ability> items = List.of(
                new Ability("能实现", "多线程安全扣减库存（32 章）", "并发测试通过 + 无超卖"),
                new Ability("能实现", "Redis 缓存 + 三大问题防护（25 章）", "穿透/击穿/雪崩各有方案"),
                new Ability("能排障", "接口 P99 突增定位（30/42 章）", "复现一次完整排查记录"),
                new Ability("能排障", "OOM/死锁诊断（30 章）", "jmap/jstack 证据链"),
                new Ability("能设计", "订单域建模到落地（49 章）", "聚合卡片 + 实现 + 测试"),
                new Ability("能设计", "分库分表方案（39 章）", "分片键论证 + 迁移方案"),
                new Ability("能设计", "支付幂等链路（51 章）", "三层幂等并发验证"),
                new Ability("能演进", "遗留模块接缝改造（49-10）", "五步改造 + 行为快照"),
                new Ability("能演进", "虚拟线程迁移（50-10）", "六步路线 + 压测对比"));
        for (Ability a : items) {
            System.out.printf("  %-4s %-24s 证据：%s%n", a.level(), a.item(), a.evidence());
        }
        System.out.println();
    }

    static void selfTestProtocol() {
        System.out.println("【自测协议：做出来才算数】");
        List<String> protocol = List.of(
                "每项能力 -> 挑对应课后练习或综合案例真做",
                "计时：实现类 ≤ 2h，排障类 ≤ 1h（模拟生产时限）",
                "证据归档：代码/截图/复盘文档进个人仓库",
                "不通过 -> 回章节重学 -> 换一个变体题再测（防背题）",
                "每月一轮：清单滚动自测（44-10 评测集思想）");
        for (String p : protocol) {
            System.out.println("  " + p);
        }
        System.out.println();
    }

    static void strengthStrategy() {
        System.out.println("【长板策略：工程价值来自不可替代性】");
        String[][] rows = {
                {"全栈但都浅", "协作可参与", "不可替代性低"},
                {"一域深（支付/性能/数据）", "团队该问题找你", "长板即价值"},
                {"长板 + 地图广度", "深一域、通全局", "最优形态"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-14s %-14s %s%n", r[0], r[1], r[2]);
        }
        System.out.println("  建议：选一个核心域深挖（本书对应线全通），广度靠 52-06 地图索引维持");
    }

    public static void main(String[] args) {
        checklist();
        selfTestProtocol();
        strengthStrategy();
    }
}
```

**课后练习**
1. 完成清单中至少 4 项的实操证据（真做），归档到个人仓库并记录耗时。
2. 用自测协议跑一轮，找出 2 个薄弱项，制定一个月补强计划。
3. 思考题：为什么"做出来才算数"是能力认证的唯一标准？从 33 章"我以为对 vs 我验证过对"的测试哲学迁移分析。

---

### 52-08 综合毕业案例①：电商平台总架构

**知识点**
- **毕业案例把全书串成一个系统**：电商平台——网关（37/47）-> 交易（49 建模/39 分片）-> 支付（51）-> 履约（29/43）-> 搜索（38）-> 推荐（40/44）-> 可观测（42）-> 发布（41）-> 容器化（37）-> 数据湖（46）-> AI 客服（40/44）。**每一章是系统的一块拼图**。
- **总架构的表达**：一页架构图（分层：接入/服务/数据/基础设施）+ 关键决策表（每个组件的选型理由引用 ADR）+ 非功能指标表（52-01）。**表达也是能力：让评审者 10 分钟看懂**。
- **组件间的连接语义**：同步调用只出现在强实时路径（下单->库存），其余事件驱动（订单事件扇出履约/积分/搜索）——**52-03 决策树的系统级应用**。
- **规模假设驱动**：峰值 3 万 QPS、订单三年 5.4 亿 -> 16 分片、搜索 ES 5 分片、推理池 4 卡——**每个数字可追溯到 52-01 估算**。
- 易错点：架构图画了组件没画数据流（评审者看不到请求怎么走）；决策无引用（为什么 ES 5 分片说不出）；指标无来源（拍脑袋数字）；图与实际系统脱节（架构图只在评审时存在）。

**示例代码**
```java
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PlatformArchitecture {
    record Component(String layer, String name, String tech, String adrRef) {
    }

    static void onePageArch() {
        System.out.println("【电商平台一页架构（分层 + 关键决策引用）】");
        List<Component> comps = List.of(
                new Component("接入", "API 网关", "Mesh + 限流（47-03）", "ADR-011"),
                new Component("服务", "交易服务", "模块化单体->微服务（49-08）", "ADR-004"),
                new Component("服务", "支付服务", "状态机+多渠道（51 章）", "ADR-009"),
                new Component("服务", "搜索服务", "ES 5 分片 + CDC（38 章）", "ADR-007"),
                new Component("服务", "AI 客服", "RAG+网关路由（40/44 章）", "ADR-014"),
                new Component("数据", "订单库", "16 分片（39 章）+ 基因 ID", "ADR-005"),
                new Component("数据", "湖仓", "Iceberg + Flink CDC（46 章）", "ADR-016"),
                new Component("基础设施", "K8s + Mesh", "滚动/金丝雀（37/47 章）", "ADR-002"),
                new Component("基础设施", "可观测", "日志/指标/链路（42 章）", "ADR-013"));
        for (Component c : comps) {
            System.out.printf("  %-8s %-10s %-30s 依据：%s%n", c.layer(), c.name(), c.tech(), c.adrRef());
        }
        System.out.println("  每个组件都有 ADR 引用 —— 架构与决策一体（52-02）");
        System.out.println();
    }

    static void dataFlow() {
        System.out.println("【数据流：一个订单的完整生命】");
        List<String> flow = List.of(
                "用户 -> 网关（限流/鉴权）-> 交易服务（订单聚合落库）",
                "  -> 事件 OrderCreated -> 营销预占券 / 搜索索引预告",
                "用户支付 -> 支付服务（51 章状态机）-> PaymentSucceeded",
                "  -> 履约建运单 / 库存扣减确认 / 账务分录 / 搜索可见 / AI 客服知识可答",
                "T+1 -> CDC 进湖 -> 指标/报表/特征（46 章）",
                "全程 trace_id 贯通（42 章）-> 任意环节可回放");
        for (String s : flow) {
            System.out.println("  " + s);
        }
        System.out.println("  同步仅出现在强实时路径；其余全部事件驱动（52-03 树）");
        System.out.println();
    }

    static void nfrBoard() {
        System.out.println("【非功能指标看板（52-01 表的实现态）】");
        Map<String, String> nfr = new LinkedHashMap<>();
        nfr.put("下单 P99", "280ms（目标 300）✓");
        nfr.put("支付可用性", "99.992%（目标 99.99）✓");
        nfr.put("搜索延迟", "P99 180ms（目标 200）✓");
        nfr.put("基础设施成本", "GMV 2.6%（上限 3%）✓");
        nfr.put("发布频率", "日均 3.2 次（41 章流水线支撑）");
        for (Map.Entry<String, String> e : nfr.entrySet()) {
            System.out.printf("  %-10s %s%n", e.getKey(), e.getValue());
        }
        System.out.println("  指标全部可追溯：SLO/预算/告警三件套在线（42 章）");
    }

    public static void main(String[] args) {
        onePageArch();
        dataFlow();
        nfrBoard();
    }
}
```

**课后练习**
1. 为毕业案例画出你的一页架构图 + 决策表 + 指标看板，找同伴做 10 分钟评审测试。
2. 把 48 章里你做过的综合案例标记到总架构的对应组件上，检查覆盖与缺口。
3. 思考题：为什么"架构图必须画数据流而不只是组件"？从评审者理解成本与系统行为可验证性分析。
