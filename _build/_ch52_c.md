### 52-09 综合毕业案例②：演进路线与里程碑

**知识点**
- **架构不是一次画成的，是按里程碑长出来的**（49-10 演进思想的全系统版）：M1 单体 MVP（3 个月，验证业务）-> M2 读写分离+缓存（用户过万）-> M3 微服务拆分+MQ（团队扩编）-> M4 分库分表+搜索（数据过亿）-> M5 容器化+流水线（41/37 章）-> M6 可观测+多活（42/47 章）-> M7 湖仓+AI（46/40 章）。**每个里程碑有触发条件（业务指标）与验收标准**。
- **里程碑的触发条件写法**："订单量 > 5000 万 且 写 QPS > 2000 且 优化手段用尽 -> 启动分库分表"（39-01 判断式的落地）——**用业务指标驱动架构演进，不凭感觉**。
- **每个里程碑的验收**：SLO 达标、演练通过、ADR 归档、成本核算——**里程碑完成 = 能力沉淀完成**，不是"部署完成"。
- **演进中的债务管理**：每个里程碑都会产生债务（临时方案/技术欠账）——**债务登记进 backlog 并定期偿还**（49-10 预算纪律）；连续两个里程碑不还债 -> 架构劣化预警。
- **回看全书章节在里程碑中的位置**：M1 用 2~14 章；M2 用 25/39-02；M3 用 28/29/49；M4 用 38/39；M5 用 37/41；M6 用 42/47；M7 用 40/43/44/46/48——**学习路线与架构演进路线同构**。
- 易错点：里程碑无触发条件（跟着感觉走）；跳级演进（M1 直接上 M5 全家桶，业务未验证基础设施先行）；里程碑无验收（部署完就宣布完成）；债务不登记（欠账隐形增长直到爆炸）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.List;

public class EvolutionRoadmap {
    record Milestone(String id, String name, String trigger, String scope, String chapters) {
    }

    static void roadmap() {
        System.out.println("【电商平台七个里程碑】");
        List<Milestone> ms = List.of(
                new Milestone("M1", "单体 MVP", "业务立项", "核心下单/支付跑通（标准三层）", "2~14 章"),
                new Milestone("M2", "读写分离+缓存", "日活 > 1 万，读压力显现", "MySQL 主从 + Redis 热点", "25/39-04"),
                new Milestone("M3", "微服务+MQ", "团队 > 8 人，发布冲突频发", "按上下文拆分 + 事件驱动", "28/29/49"),
                new Milestone("M4", "分库分表+搜索", "订单 5000 万，写 QPS 2000", "16 分片 + ES 搜索", "38/39"),
                new Milestone("M5", "容器化+流水线", "发布频率 > 日 3 次", "K8s + CI/CD + 灰度", "37/41"),
                new Milestone("M6", "可观测+容灾", "P1 事故复盘不足", "三支柱 + 多集群 + 混沌", "42/47"),
                new Milestone("M7", "湖仓+AI", "数据分析需求 + 客服成本", "Iceberg + RAG/Agent", "40/43/44/46/48"));
        for (Milestone m : ms) {
            System.out.printf("  %s %-14s 触发：%s%n    范围：%s（对应章节：%s）%n",
                    m.id(), m.name(), m.trigger(), m.scope(), m.chapters());
        }
        System.out.println();
    }

    static void milestoneGate() {
        System.out.println("【里程碑验收门（完成 = 能力沉淀）】");
        List<String> gates = List.of(
                "SLO 达标：本里程碑承诺的非功能指标全绿（52-08 看板）",
                "演练通过：新能力的故障场景演练（降级/切换/回滚）",
                "ADR 归档：本阶段的全部决策入档（52-02）",
                "成本核算：新增基础设施/人力成本入报表（48-09）",
                "债务登记：临时方案与技术欠账进 backlog（定期偿还）");
        for (String g : gates) {
            System.out.println("  [ ] " + g);
        }
        System.out.println();
    }

    static void debtManagement() {
        System.out.println("【技术债的登记与偿还】");
        record Debt(String item, String introducedIn, String risk, String plan) {
        }
        List<Debt> debts = List.of(
                new Debt("搜索同步是双写（未上 CDC）", "M4", "一致性窗口大", "M6 换 CDC（38-09）"),
                new Debt("订单库单实例", "M1", "单点 + 容量", "M4 分库分表"),
                new Debt("配置硬编码", "M1", "变更需发版", "M5 配置中心"),
                new Debt("无混沌演练", "M6 前", "韧性未知", "M6 季度游戏日（47-10）"));
        for (Debt d : debts) {
            System.out.printf("  %-20s 引入@%s 风险=%-14s 偿还：%s%n",
                    d.item(), d.introducedIn(), d.risk(), d.plan());
        }
        System.out.println("  纪律：每债必有偿还里程碑 —— 债不是不做，是排期做（41-10 预算思想）");
    }

    public static void main(String[] args) {
        roadmap();
        milestoneGate();
        debtManagement();
    }
}
```

**课后练习**
1. 为你的业务制定七里程碑路线图（触发条件/范围/验收），并标注当前位置与下一个里程碑的启动条件。
2. 登记你系统的技术债清单（引入点/风险/偿还计划），把"未偿还且无计划"的债务标红。
3. 思考题：为什么"业务指标触发架构演进"优于"技术驱动演进"？从投入产出与业务验证先行两个角度分析。

---

### 52-10 综合毕业案例③：故障演练与毕业答辩

**知识点**
- **毕业答辩的两个部分**：**故障演练现场**（给你一个注入故障的模拟环境，限时定位与恢复——验证"能排障"）与**架构答辩**（评审者围绕你的设计追问——验证"能设计与权衡"）。**这是 52-07 清单的最终考试**。
- **演练题的出题原则**：从真实事故库出题（42-11/51 章案例）、故障链嵌套（表象掩盖根因）、时间压力真实（生产感）——**考过程纪律（42 章 SOP）而不只是答案**。
- **答辩的追问网络**：每个决策被追问三层——为什么选它（52-02 ADR）？失败了怎么办（降级/回滚）？规模 ×10 会怎样（演进路线 52-09）？**答不上第三层说明没想过演进**。
- **评分维度**：定位路径（有 SOP 还是乱试）、恢复速度（RTO 意识）、权衡表达（52-04 取舍模板）、知识连接（跨章引用 52-06 地图）——**四维评分，不是对错二分**。
- 易错点：演练只背答案（换了故障形态就懵）；答辩只讲方案不讲代价（52-04 的教训）；故障定位跳过证据（靠猜，42 章纪律缺失）；时间管理失控（前 10 分钟讲不完主流程）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class GradDefense {
    record DrillScenario(String fault, String symptom, String sopPath, String expectedRto) {
    }

    static void drillScenarios() {
        System.out.println("【演练题库（三选一限时 40 分钟）】");
        List<DrillScenario> drills = List.of(
                new DrillScenario("下单 P99 突增 5 倍",
                        "用户反馈卡，错误率正常",
                        "指标分桶（42-10）-> trace 定位段 -> 日志证据 -> 修复",
                        "20 分钟内恢复"),
                new DrillScenario("支付回调丢失",
                        "渠道账单 vs 本地对不上（长款）",
                        "对账差异单（51-05）-> 查单补处理 -> 复核",
                        "30 分钟内闭环"),
                new DrillScenario("MQ 积压 50 万",
                        "消费 lag 持续增长（43-08）",
                        "lag 指标 -> 消费者吞吐分析 -> 扩容/批量/跳过策略",
                        "1 小时内追平"));
        for (DrillScenario d : drills) {
            System.out.printf("  故障：%s%n    表象：%s%n    期望路径：%s%n    RTO：%s%n",
                    d.fault(), d.symptom(), d.sopPath(), d.expectedRto());
        }
        System.out.println();
    }

    static void defenseQuestions() {
        System.out.println("【答辩追问网络：三层深挖示例】");
        Map<String, List<String>> q = new LinkedHashMap<>();
        q.put("你用了 Redis 预扣库存", List.of(
                "为什么不用 DB 行锁？（39-01 热点行）",
                "Redis 挂了怎么办？（L4 兜底/对账补偿，44-08）",
                "流量 ×10 会怎样？（分桶预扣/多实例，39-05）"));
        q.put("你选了模块化单体", List.of(
                "为什么不上微服务？（49-08 触发条件未满足）",
                "未来怎么拆？（上下文边界已立，触发即拆）",
                "模块边界怎么守护？（ArchUnit 进 CI，49-08）"));
        for (Map.Entry<String, List<String>> e : q.entrySet()) {
            System.out.println("  " + e.getKey() + "：");
            for (String s : e.getValue()) {
                System.out.println("    - " + s);
            }
        }
        System.out.println("  第三问（规模演进）是区分度所在 —— 52-09 路线的价值");
        System.out.println();
    }

    static void scoring() {
        System.out.println("【四维评分表】");
        String[][] rows = {
                {"定位路径", "有 SOP：指标->trace->日志->修复", "42 章纪律"},
                {"恢复速度", "RTO 意识 + 先恢复后优化", "51-10 原则"},
                {"权衡表达", "每个决策带代价（52-04 模板）", "49 章 ADR 纪律"},
                {"知识连接", "跨章引用与思想迁移（52-06）", "地图索引能力"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-8s %-30s 来源：%s%n", r[0], r[1], r[2]);
        }
        System.out.println("  通过线：四维均达 60% 且无零分项 —— 毕业认证");
    }

    public static void main(String[] args) {
        drillScenarios();
        defenseQuestions();
        scoring();
    }
}
```

**课后练习**
1. 组织一次模拟答辩：同伴出题（演练+追问网络），你限时完成并录音复盘，对照四维评分自评。
2. 把你此前做过的综合案例整理成"答辩作品集"（每个案例一页：问题/方案/权衡/证据）。
3. 思考题：为什么答辩追问"规模 ×10"最能区分水平？从局部最优与演进准备的差异分析（52-09 路线的存在意义）。

---

### 52-11 毕业寄语：持续学习的工程人生

**知识点**
- **毕业不是终点，是"具备自学能力"的起点**：技术栈五年一换（本书 491 课也会过时），**不变的是十思想（52-06）与学习闭环（44-10 自我版：输入->实践->复盘->输出）**。本书交付的不是知识清单，是"遇到新问题知道往哪走"的判断力。
- **持续学习的工程化**：像管理代码一样管理学习——输入源（文档/源码/事故复盘）定期更新、实践项目（每季度一个 side project）、输出（博客/分享/开源，费曼式巩固）、复盘（错题本：踩过的坑分类归档 42-11）。
- **技术趋势的判断力**：趋势判断看三样——**问题是否长期存在**（一致性/性能/成本永远在）、**生态是否成熟**（生产可用 vs 演示可用）、**迁移成本是否可控**（49 章演进框架）。**AI 时代 Java 工程师的位置**：模型之上的一切——数据、编排、工程化、可靠性（40/44/48 章的位置论）。
- **职业的三条护城河**：**深度**（一个域做到团队无人能替代 52-07）、**广度**（知识地图让协作无障碍）、**信誉**（交付可信、复盘诚实、决策有据——ADR/台账/复盘的复利）。
- **给毕业生的三句话**：① 先让它跑起来，再让它跑得对，再让它跑得快——**顺序不可颠倒**；② 每个故障都是学费，交了要收据（复盘闭环）；③ 对复杂度保持敬畏，对简单保持追求（49 章复杂度投资论的终身实践）。
- 易错点：收藏代替学习（收藏夹里的文章不会自动变成能力）；追逐全部新技术（52-03 反模式）；停止输出（输入再多不内化）；单打独斗（社区/同事是最好的加速器）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ClosingWords {
    static void learningLoop() {
        System.out.println("【学习闭环：像管理代码一样管理学习】");
        List<String> loop = List.of(
                "输入：文档/源码/事故复盘（每周固定时段，信息源季度更新）",
                "实践：每季度一个 side project（用新学的思想做一个真实小系统）",
                "复盘：错题本 —— 踩过的坑分类归档（42-11 的个人版）",
                "输出：博客/分享/开源（费曼式内化，输出倒逼输入）",
                "度量：季度自评（52-07 清单滚动）—— 学习也有 SLI");
        for (String s : loop) {
            System.out.println("  " + s);
        }
        System.out.println();
    }

    static void trendJudgment() {
        System.out.println("【技术趋势判断三问】");
        Map<String, String> qs = new LinkedHashMap<>();
        qs.put("问题长期存在吗？", "一致性/性能/成本/复杂度是永恒命题（52-06 十思想）");
        qs.put("生态成熟吗？", "生产可用 vs 演示可用：查生产案例与故障文章");
        qs.put("迁移可控吗？", "演进式引入还是推倒重来（49-10 框架）");
        for (Map.Entry<String, String> e : qs.entrySet()) {
            System.out.printf("  %-10s %s%n", e.getKey(), e.getValue());
        }
        List<String> positions = List.of(
                "AI 时代的 Java 位置：模型之上的一切 —— 数据管道、编排、工程化、可靠性",
                "本书 40/44/48 章正是这个位置的完整演练",
                "判断：框架会变，'把不确定的技术装进可靠的工程'永远稀缺");
        for (String p : positions) {
            System.out.println("  " + p);
        }
        System.out.println();
    }

    static void threeSentences() {
        System.out.println("【给毕业生的三句话】");
        List<String> words = List.of(
                "① 先跑起来，再跑得对，再跑得快 —— 顺序不可颠倒",
                "② 每个故障都是学费 —— 交了要收据（复盘闭环）",
                "③ 对复杂度保持敬畏，对简单保持追求 —— 49 章复杂度投资论的终身实践");
        for (String w : words) {
            System.out.println("  " + w);
        }
        System.out.println();
        System.out.println("  从第 2 章的一个变量，到第 52 章的平台架构 ——");
        System.out.println("  真正毕业的标志：下一个新问题出现时，你知道往哪走。");
    }

    public static void main(String[] args) {
        learningLoop();
        trendJudgment();
        threeSentences();
    }
}
```

**课后练习**
1. 制定你的下一季度学习闭环计划（输入源/实践项目/输出目标/复盘节奏），写进个人 OKR。
2. 用趋势判断三问评估一个你正在关注的新技术，输出 300 字判断报告。
3. 思考题：为什么"输出"是学习闭环中最容易被忽略却最重要的一环？从费曼技巧与知识内化的机制分析。
