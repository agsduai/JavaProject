### 49-09 领域建模实战：营销与优惠

**知识点**
- **营销域是检验建模能力的试金石**：优惠券有“满减/折扣/秒杀价”多种类型、有使用门槛、叠加规则、有效期、库存——**规则交织度高，贫血模型在这里最容易失控**（一个 if-else 大山）。
- **建模的第一步：识别值对象与多态**——“优惠”是策略族：`CouponPolicy` 接口 + `FullReduction`（满减）/ `PercentageDiscount`（折扣）/ `FixedAmount`（固定金额）实现——**策略模式（31 章）在领域内的正名**：不是“代码技巧”，是业务本身的多态。
- **叠加规则是一等公民**：哪些优惠可叠加（优惠券+会员折扣）、不可叠加（两张券互斥）、计算顺序（先商品折扣再满减）——**叠加策略独立成对象**（`StackingRule`），不要散落在计算流程的 if 里。
- **优惠计算的可测试性**：纯函数计算（输入订单上下文 + 优惠清单 -> 输出分摊结果）——**分摊（分摊到每个订单明细）要处理除不尽**（金额尾差按行分摊，最后一行吸收尾差——BigDecimal 必备功夫）。
- **营销上下文与交易上下文的边界**：交易只关心“最终应付金额”与“优惠明细快照”（用于审计与退款）；**算价过程是营销域的事**——交易侧保存计算结果快照而非实时重算（价格快照原则）。
- 易错点：优惠规则写死在订单 Service（营销改动波及交易）；金额用 double（精度灾难，14 章 BigDecimal 的领域级应用）；分摊尾差随机分配（对账差一分钱）；优惠库存与交易扣减跨上下文强一致（用事件+校验兜底，49-03）。

**示例代码**
```java
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class MarketingModeling {
    record Money(BigDecimal amount) {
        static Money of(String v) {
            return new Money(new BigDecimal(v));
        }

        Money minus(Money o) {
            return new Money(amount.subtract(o.amount));
        }

        boolean greaterThan(Money o) {
            return amount.compareTo(o.amount) > 0;
        }

        int cents() {
            return amount.movePointRight(2).intValueExact();
        }
    }

    /** 策略族：优惠本身是多态的业务概念 */
    interface CouponPolicy {
        String name();

        Money discountFor(Money orderAmount);
    }

    record FullReduction(Money threshold, Money off) implements CouponPolicy {
        public String name() {
            return "满" + threshold.amount() + "减" + off.amount();
        }

        public Money discountFor(Money orderAmount) {
            return orderAmount.greaterThan(threshold) || orderAmount.amount().compareTo(threshold.amount()) == 0
                    ? off : Money.of("0");
        }
    }

    record PercentageDiscount(int percent, Money cap) implements CouponPolicy {
        public String name() {
            return percent + "%折扣（上限" + cap.amount() + "）";
        }

        public Money discountFor(Money orderAmount) {
            Money d = new Money(orderAmount.amount()
                    .multiply(BigDecimal.valueOf(percent))
                    .divide(BigDecimal.valueOf(100)));
            return d.greaterThan(cap) ? cap : d;
        }
    }

    /** 叠加规则：独立建模，不散落 if */
    record StackingRule(List<String> exclusiveGroups) {
        boolean canStack(String couponA, String couponB) {
            return true;   // 简化：本例全部可叠，互斥组留作练习
        }
    }

    static void calculate() {
        System.out.println("【订单 100 元，两张券叠加计算】");
        List<CouponPolicy> coupons = List.of(
                new FullReduction(Money.of("80"), Money.of("20")),
                new PercentageDiscount(10, Money.of("15")));
        Money orderAmount = Money.of("100.00");
        Money total = Money.of("0.00");
        for (CouponPolicy c : coupons) {
            Money d = c.discountFor(orderAmount);
            total = new Money(total.amount().add(d.amount()));
            System.out.printf("  %-16s -> 优惠 %s 元%n", c.name(), d.amount());
        }
        Money payable = orderAmount.minus(total);
        System.out.printf("  应付 = 100 - %s = %s 元（%d 分）%n", total.amount(), payable.amount(), payable.cents());
        System.out.println("  快照原则：交易侧保存“优惠明细+应付金额”快照，退款按快照走");
        System.out.println();
    }

    static void allocationTail() {
        System.out.println("【分摊尾差：最后一行吸收】");
        Money payable = Money.of("10.00");
        int[] lineWeights = {1, 1, 1};
        int totalCents = payable.cents();
        int per = totalCents / lineWeights.length;
        int[] alloc = new int[lineWeights.length];
        int assigned = 0;
        for (int i = 0; i < alloc.length; i++) {
            alloc[i] = per;
            assigned += per;
        }
        alloc[alloc.length - 1] += totalCents - assigned;   // 尾差给最后一行
        for (int i = 0; i < alloc.length; i++) {
            System.out.printf("  明细%d 分摊 %d 分%n", i + 1, alloc[i]);
        }
        System.out.printf("  合计 %d 分 = 应付 %d 分（对账恒等）%n", alloc[0] + alloc[1] + alloc[2], totalCents);
        System.out.println("  尾差规则写进契约：随机分配会导致退款与对账差一分钱");
    }

    public static void main(String[] args) {
        calculate();
        allocationTail();
    }
}
```

**课后练习**
1. 给策略族补上 `FixedAmount` 与互斥组 `StackingRule`（两张同类券互斥），用单元测试覆盖叠加矩阵。
2. 复现分摊尾差事故：随机分配 3 行 10 元，构造对账差一分钱的用例，然后改成“最后一行吸收”。
3. 思考题：为什么交易侧保存“优惠快照”而不是“实时重算”？从退款依据、审计、促销下线三个场景分析。

---

### 49-10 遗留系统改造与演进式架构

**知识点**
- **遗留系统不是技术问题，是演进策略问题**：三个月赶工的订单系统跑着 70% 的交易——推倒重写的风险（业务中断、隐性规则丢失）远大于演进改造。**演进式架构（evolutionary architecture）**：增量重构 + 持续可发布。
- **改造的第一步是“知道现状”**：特征检测（failure mode analysis）——耦合度分析、圈复杂度热点、变更频率分布（**改得最频繁的类就是业务核心**，49-01 事件风暴的代码侧佐证）。补测试是改造的安全网（33 章测试金字塔：先给核心路径铺集成测试）。
- **绞杀者路线（49-07）+ 接缝引入（seam）**：在旧代码里先挖出“接缝”（可替换点）——把“直接调库”改成“调接口”（49-04 端口），一步一小步，每步可发布可回退。
- **数据同步期**：新旧库并存的同步方案（43-09 CDC）——双向写期要版本仲裁（45-08 fencing 思想），切换期灰度读。
- **演进式架构的守护**：适应度函数（fitness function）——把架构约束写成自动化检查（依赖方向、分层违例、API 兼容性）进 CI，**让架构随演进不退化**（49-08 ArchUnit 的体系化）。
- **改造的节奏与止损**：每两周一个可发布增量；改造期间冻结大需求（或只允许与改造相关的）；**改造预算有上限**（超过 X 人月仍未见效 -> 重新评估方案）。
- 易错点：大爆炸重写（两年没上线，旧系统还在演进，永远追不上）；只重构不补测试（改一处崩三处）；接缝不清理（重构完留下抽象层垃圾）；没有业务方参与（技术自嗨改造，业务感知为零价值）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class LegacyRefactor {
    static void hotspots() {
        System.out.println("【改造侦察：用数据找改造顺序】");
        record Hotspot(String className, int changeFreq, int cyclomatic, String verdict) {
        }
        List<Hotspot> spots = List.of(
                new Hotspot("OrderService.calcAmount", 42, 85, "核心域 + 高变更 -> 第一个改造"),
                new Hotspot("StockService.deduct", 18, 40, "核心域 -> 第二批"),
                new Hotspot("ReportExporter", 2, 60, "低变更 -> 缓一缓"),
                new Hotspot("LegacyUtil", 1, 120, "复杂但无人动 -> 包裹隔离，不重构"));
        for (Hotspot h : spots) {
            System.out.printf("  %-26s 变更%3d次/月 圈复杂度%3d -> %s%n",
                    h.className(), h.changeFreq(), h.cyclomatic(), h.verdict());
        }
        System.out.println("  侦察公式：变更频率 × 复杂度 = 改造收益；无人动的大泥球优先包裹");
        System.out.println();
    }

    static void seamIntro() {
        System.out.println("【接缝引入：每步可发布可回退】");
        List<String> steps = List.of(
                "第1步：补集成测试（当前行为快照，改造的安全网）",
                "第2步：挖接缝——calcAmount 里的优惠规则抽成接口（端口 49-04）",
                "第3步：旧实现挂接口（行为零变化，发布验证）",
                "第4步：新实现（策略族）并行运行，对比结果（暗发布 41-08）",
                "第5步：切流量 -> 删除旧实现 -> 移除临时开关");
        for (String s : steps) {
            System.out.println("  " + s);
        }
        System.out.println("  每一步都是一次正常发布：改造成本摊进日常迭代");
        System.out.println();
    }

    static void fitnessFunctions() {
        System.out.println("【适应度函数：让架构不退化】");
        record Fitness(String rule, String tool, String trigger) {
        }
        List<Fitness> fs = List.of(
                new Fitness("领域层零框架依赖", "ArchUnit / 依赖树检查", "每次构建"),
                new Fitness("禁止跨上下文 JOIN", "SQL 解析器静态扫描", "每次构建"),
                new Fitness("圈复杂度 < 15", "静态分析阈值", "每次构建"),
                new Fitness("API 向后兼容", "openapi-diff 检查", "每次 PR"),
                new Fitness("测试覆盖率核心域 > 70%", "覆盖率门禁（41 章）", "每次构建"));
        for (Fitness f : fs) {
            System.out.printf("  %-16s %-22s %s%n", f.rule(), f.tool(), f.trigger());
        }
        System.out.println("  本质：架构规则 = 可执行的测试（41-03 流水线闸门的架构版）");
        System.out.println();
    }

    static void budgetDiscipline() {
        System.out.println("【改造的止损纪律】");
        List<String> rules = List.of(
                "预算上限：本阶段改造 ≤ 3 人月，到期复盘收益",
                "需求冻结协调：改造窗口内业务需求排优先级（业务参与决策）",
                "可发布节奏：每两周一个增量上生产（不允许长期分支）",
                "收益度量：改造成本 vs 后续每次需求变更的节省估算",
                "止损线：连续两个增量未见收益 -> 重新评估方案");
        for (String r : rules) {
            System.out.println("  - " + r);
        }
        System.out.println("  演进式改造是投资决策：有预算、有度量、有止损");
    }

    public static void main(String[] args) {
        hotspots();
        seamIntro();
        fitnessFunctions();
        budgetDiscipline();
    }
}
```

**课后练习**
1. 用“变更频率 × 复杂度”给你的代码库做改造侦察，输出前 3 个热点与改造顺序建议。
2. 选一个热点方法走完五步接缝改造（可缩小规模演示），每步记录可发布证据。
3. 思考题：为什么“补测试在前、重构在后”是铁律？从行为快照与回归安全的角度分析顺序不可颠倒的原因。

---

### 49-11 综合案例：订单域建模全流程

**知识点**
- **把本章收束成一次完整建模**：从业务描述出发——事件风暴（49-01）梳理订单域事件 -> 划定上下文与通用语言 -> 识别聚合与不变量（49-02）-> 设计领域事件与协作（49-03）-> 端口适配器落地（49-04）-> CQRS 读模型（49-05）-> 事件溯源决策（49-06）-> 部署形态（49-08）。**一条从业务语言到代码结构的完整映射链**。
- **建模产出物清单**：上下文映射图、事件清单（含消费方）、聚合卡片（不变量/边界/入口）、端口清单、读模型定义、一致性策略表——**每个产出物都是可评审文档**，评审通过才写代码。
- **订单聚合卡片示例**：聚合根 Order；成员 OrderLine/Money（值对象）；不变量：明细和≥0、提交后金额不可变、同 SKU 唯一；边界：不含库存/优惠（跨上下文）；事件：OrderPlaced/OrderPaid/OrderCancelled；仓储：OrderRepository（事件溯源可选 49-06）。
- **建模的迭代性**：第一版聚合卡片在实现中会暴露边界错误（比如发现“金额不可变”在改价场景不成立）——**建模是渐进的**，卡片与代码同步演进（49-10 适应度函数守护）。
- **评审检查单**：通用语言一致（代码命名=业务语言）？聚合够小？不变量闭环？跨聚合只走事件？端口无框架依赖？读模型契约明确？——**六问全过才进开发**。
- 易错点：产出物写完不评审（建模沦为个人行为，团队不认）；聚合卡片与代码漂移（改代码不改卡片）；通用语言只停在设计文档（代码里还是 OrderManager）；一版建模用三年（业务变了模型不跟）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class OrderDomainCase {
    record AggregateCard(String root, List<String> members, List<String> invariants,
                         List<String> events, String storage) {
    }

    static void card() {
        System.out.println("【订单聚合卡片（评审文档）】");
        AggregateCard card = new AggregateCard(
                "Order",
                List.of("OrderLine（实体）", "Money（值对象）", "ShippingSnapshot（值对象）"),
                List.of("明细之和 ≥ 0", "提交后金额不可变", "同 SKU 明细唯一", "取消仅在 PLACED/PAID"),
                List.of("OrderPlaced", "OrderPaid", "OrderCancelled"),
                "事件溯源可选（49-06）：审计需求成立则启用");
        System.out.printf("  聚合根：%s%n", card.root());
        System.out.println("  成员：" + card.members());
        System.out.println("  不变量：");
        for (String i : card.invariants()) {
            System.out.println("    - " + i);
        }
        System.out.println("  领域事件：" + card.events());
        System.out.println("  存储：" + card.storage());
        System.out.println();
    }

    static void consistencyTable() {
        System.out.println("【一致性策略表：每个协作都显式决策】");
        Map<String, String> table = new LinkedHashMap<>();
        table.put("Order -> 库存", "事件 OrderPaid，库存侧预留+校验兜底（最终一致）");
        table.put("Order -> 营销", "事件 OrderCreated 预占券；核销失败进补偿队列");
        table.put("Order -> 履约", "事件 OrderPaid 创建运单（幂等键=orderId）");
        table.put("Order 内部明细", "同聚合强一致（不变量闭环）");
        for (Map.Entry<String, String> e : table.entrySet()) {
            System.out.printf("  %-12s %s%n", e.getKey(), e.getValue());
        }
        System.out.println("  39-09 的跨库事务问题在此表上一目了然：只有一个同聚合，其余全事件");
        System.out.println();
    }

    static void reviewChecklist() {
        System.out.println("【建模评审六问】");
        List<String> qs = List.of(
                "① 代码命名 = 业务通用语言？（出现 Manager/Helper/Util 即红旗）",
                "② 每个聚合的不变量都在聚合内闭环？",
                "③ 跨聚合协作全部事件化？同步调用有强实时理由吗？",
                "④ 领域层依赖树干净（无框架）？",
                "⑤ 读模型契约（写后读）明确？",
                "⑥ 部署形态与伸缩/发布/团队边界匹配？");
        for (String q : qs) {
            System.out.println("  " + q);
        }
        System.out.println("  六问全过才进开发；实现期发现边界错误 -> 回卡片改，禁止硬编码绕过");
        System.out.println();
    }

    static void evolutionNote() {
        System.out.println("【建模的迭代性：一版卡片被实现推翻的例子】");
        List<String> story = List.of(
                "卡片 v1：金额不可变（提交后）",
                "实现期发现：业务有“已支付改价”场景（客服调价）",
                "处理：回到卡片 -> 补充不变量“改价仅 PAID 且走独立审计事件 OrderPriceAdjusted”",
                "代码与卡片同步演进，适应度函数守护不变量测试");
        for (String s : story) {
            System.out.println("  " + s);
        }
        System.out.println("  建模不是一次性设计，是随业务持续对齐的过程（49-10 演进纪律）");
    }

    public static void main(String[] args) {
        card();
        consistencyTable();
        reviewChecklist();
        evolutionNote();
    }
}
```

**课后练习**
1. 为你负责的业务域完成全套建模产出物（映射图/事件清单/聚合卡片/端口清单/一致性策略表）并组织一次评审。
2. 实现订单聚合卡片（含全部不变量的单元测试），验证六问评审清单的每一项。
3. 思考题：为什么“建模产出物必须评审”？从团队通用语言共识与后续维护者理解成本两个角度分析。
