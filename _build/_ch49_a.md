### 49-01 领域驱动设计总览

**知识点**
- **DDD 解决的问题是"复杂业务在代码中的表达"**：贫血模型（POJO + 一堆 Service）把业务规则散落在 Service 方法里，改一个促销规则要横跨五个类；DDD 把**业务规则收进领域模型**，让代码结构映射业务结构。**业务复杂时 DDD 是投资，业务简单时是负担**——CRUD 系统不必上 DDD。
- **两条腿：战略设计与战术设计**：战略设计回答"系统怎么拆"——限界上下文（Bounded Context）、通用语言（Ubiquitous Language）、上下文映射（Context Map）；战术设计回答"一个上下文内部怎么写"——聚合（Aggregate）、实体（Entity）、值对象（Value Object）、领域服务、领域事件。**战略先于战术**：上下文划错了，战术写得再漂亮也是错的模型。
- **通用语言是 DDD 的灵魂**：业务说"核销"、代码里就该有 `redeem`（而不是 `updateStatus2`）——**语言统一后，沟通成本与代码歧义同步消失**。看到代码里出现"管理器/工具/助手"类命名，多半是通用语言缺失。
- **限界上下文 ≠ 微服务**（29 章的拆分其实就是在划上下文）：一个上下文内语言统一（"商品"在库存上下文指 SKU，在营销上下文指可参加活动的商品池）——**同一个词在不同上下文含义不同，跨上下文必须转换**。上下文可以包含多个服务，也可以一个服务承载多个小上下文。
- **DDD 与分层的关系**：领域层（模型与规则）在最里、应用层（用例编排）在其上、基础设施（DB/MQ）在外——**依赖方向永远指向领域层**（49-04 六边形架构）。
- **落地路径**：事件风暴（Event Storming）工作坊梳理业务事件 -> 按事件聚类划上下文 -> 每个上下文内识别聚合 -> 写代码。**从"事件"出发比从"表"出发更接近业务**。
- 易错点：给 CRUD 系统硬套 DDD（聚合根/仓储全套上，代码量翻倍收益为零）；一个"大模型"贯穿全系统（没有上下文边界，语义混乱）；战术设计先行（聚合划错）；把 DDD 等同于"代码分层模板"（DDD 是思维方式不是目录结构）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DddOverview {
    record ContextMap(String context, String language, String keyConcept, String integration) {
    }

    static void strategic() {
        System.out.println("【战略设计：电商的限界上下文映射】");
        List<ContextMap> ctxs = List.of(
                new ContextMap("交易上下文", "订单（含金额/状态机）", "Order 不可变快照", "发布 OrderPaid 事件"),
                new ContextMap("库存上下文", "扣减（只关心 SKU 与数量）", "StockItem", "订阅 OrderPaid 扣库存"),
                new ContextMap("营销上下文", "核销（优惠券兑换）", "Coupon Redemption", "订阅 OrderCreated 预占"),
                new ContextMap("履约上下文", "发货（收货地址/物流）", "Shipment", "订阅 OrderPaid 创建运单"));
        for (ContextMap c : ctxs) {
            System.out.printf("  %-8s 通用语言=%-18s 核心概念=%-18s 集成=%s%n",
                    c.context(), c.language(), c.keyConcept(), c.integration());
        }
        System.out.println("  同一个“商品”：库存里是 SKU，营销里是活动商品池 —— 上下文内各自建模");
        System.out.println();
    }

    static void anemicVsRich() {
        System.out.println("【贫血 vs 充血：同一规则的两种归属】");
        System.out.println("  规则：订单总价 = 明细之和 - 优惠券 - 积分抵扣（优惠券不可超订单额）");
        Map<String, String> anemic = new LinkedHashMap<>();
        anemic.put("OrderPO", "只有 getter/setter");
        anemic.put("OrderService.calcAmount()", "规则全在这里（还有 CouponService、PointService 各管一段）");
        anemic.put("改规则", "找 Service -> 三个类都可能被改 -> 交叉影响");
        for (Map.Entry<String, String> e : anemic.entrySet()) {
            System.out.printf("  贫血  %-22s %s%n", e.getKey(), e.getValue());
        }
        List<String> rich = List.of(
                "Order.addCoupon(coupon) -> 规则在聚合内， coupon 超额直接抛业务异常",
                "Order.totalAmount() 是聚合的派生属性（不落库，实时计算）",
                "改规则 -> 只改 Order 一个类 -> 单元测试聚焦聚合");
        for (String r : rich) {
            System.out.println("  充血  " + r);
        }
        System.out.println();
    }

    static void eventStorming() {
        System.out.println("【事件风暴：从业务事件出发建模】");
        List<String> steps = List.of(
                "1) 橙色便利贴：领域事件（订单已创建/已支付/已发货...）按时间线排布",
                "2) 蓝色：命令（创建订单/支付）与触发者（用户/定时任务/事件）",
                "3) 黄色：实体与聚合（哪些事件总是绑在一起发生）",
                "4) 粉色：外部系统（支付渠道/物流）",
                "5) 聚类：事件紧密关联的划进同一上下文 —— 上下文边界自己浮现");
        for (String s : steps) {
            System.out.println("  " + s);
        }
        System.out.println("  产出：上下文映射图 + 每个上下文的事件清单 —— 直接对应 29 章的服务拆分");
        System.out.println();
    }

    static void whenDdd() {
        System.out.println("【决策：什么复杂度值得 DDD】");
        String[][] rows = {
                {"后台管理 CRUD", "不用", "贫血 + Service 直改库，简单直接"},
                {"规则会变的核心域", "全量 DDD", "促销/库存/计费：规则收进聚合"},
                {"支撑域（如通知）", "轻量", "上下文划清 + 事件驱动，战术从简"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-14s %s%n", r[0], r[1]);
        }
        System.out.println("  核心域重投入、非核心域轻投入 —— DDD 也是成本管理");
    }

    public static void main(String[] args) {
        strategic();
        anemicVsRich();
        eventStorming();
        whenDdd();
    }
}
```

**课后练习**
1. 用事件风暴的方法梳理你负责的业务（列出 ≥8 个领域事件），聚类出限界上下文，画出上下文映射图。
2. 找一段贫血代码（或虚构），把其中一条业务规则搬进聚合，对比改动时的影响面差异。
3. 思考题：为什么"同一个词在不同上下文含义不同"反而健康？从语义歧义与模型准确性角度分析跨上下文翻译的必要性。

---

### 49-02 战术设计：聚合、实体与值对象

**知识点**
- **聚合（Aggregate）是事务与一致性的边界**：聚合根（Aggregate Root）是外部唯一入口，聚合内部对象只能被根引用——**跨聚合引用必须用 ID，不能持对象引用**。这条纪律让"哪些修改可以放在一个事务里"有了明确答案：**一个事务只改一个聚合**（39-09 跨库事务的根本答案）。
- **实体 vs 值对象的判据**：**实体**有身份（订单 9001 换了地址还是订单 9001），靠 ID 区分、有生命周期；**值对象**无身份靠属性区分（金额 69.00 元与另一个 69.00 元不可区分），不可变、可替换。**"它有 ID 吗"是第一个问的问题**。
- **聚合划小原则**：聚合越小事务越短、并发冲突越少——**跨聚合协作用领域事件最终一致**（订单聚合与库存聚合之间用 OrderPaid 事件，而不是一个巨大聚合包住两者）。大聚合是 DDD 最常见的失败原因。
- **不变量（invariant）归属聚合**："订单明细之和 ≥ 0"、"同一 SKU 明细唯一"这类规则必须**在一个事务内可验证**——它们定义聚合边界（规则涉及谁，聚合就包含谁）。
- **领域事件建模**：过去时命名（OrderPaid/OrderCancelled）、不可变、带业务字段与发生时间——**事件是跨聚合/跨上下文的粘合剂**（28 章 MQ、39 章本地消息表的领域层根基）。
- **仓储（Repository）只为聚合根定义**：`OrderRepository.findById/save`——聚合整体存取；聚合内部对象通过根访问，不单独建 Repository。
- 易错点：聚合划成"一张表一个聚合"（OrderItem 单独聚合 -> 一致性无处安放）；聚合间持对象引用（加载一个聚合拖出半个库）；值对象做成可变实体（金额还能 set）；领域事件带可变引用（事件应不可变）。

**示例代码**
```java
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class DddTactical {
    /** 值对象：不可变、靠属性判等 */
    record Money(BigDecimal amount, String currency) {
        Money add(Money other) {
            if (!currency.equals(other.currency)) {
                throw new IllegalArgumentException("币种不同不可相加");
            }
            return new Money(amount.add(other.amount), currency);
        }

        boolean isPositive() {
            return amount.compareTo(BigDecimal.ZERO) > 0;
        }
    }

    /** 实体：订单明细（有身份，但只在聚合内可见） */
    static class OrderLine {
        final String sku;
        int quantity;
        Money unitPrice;

        OrderLine(String sku, int quantity, Money unitPrice) {
            this.sku = sku;
            this.quantity = quantity;
            this.unitPrice = unitPrice;
        }

        Money subtotal() {
            return new Money(unitPrice.amount().multiply(BigDecimal.valueOf(quantity)),
                    unitPrice.currency());
        }
    }

    /** 聚合根：唯一入口，不变量在聚合内闭环 */
    static class Order {
        final String orderId;
        final List<OrderLine> lines = new ArrayList<>();
        private Money coupon;
        private String status = "DRAFT";

        Order(String orderId, Money currency) {
            this.orderId = orderId;
            this.coupon = currency;
        }

        void addLine(String sku, int qty, Money unitPrice) {
            if (!"DRAFT".equals(status)) {
                throw new IllegalStateException("已下单订单不可改明细");
            }
            lines.add(new OrderLine(sku, qty, unitPrice));
        }

        Money subtotal() {
            return lines.stream().map(OrderLine::subtotal)
                    .reduce(new Money(BigDecimal.ZERO, coupon.currency()), Money::add);
        }

        /** 不变量校验：抵扣不可超订单额 —— 规则收在聚合内 */
        void applyCoupon(Money discount) {
            if (!discount.isPositive()) {
                throw new IllegalArgumentException("抵扣必须为正");
            }
            if (discount.amount().compareTo(subtotal().amount()) > 0) {
                throw new IllegalArgumentException("抵扣不可超过订单总额");
            }
            this.coupon = discount;
        }

        void submit() {
            if (lines.isEmpty()) {
                throw new IllegalStateException("空订单不可提交");
            }
            this.status = "PLACED";
            System.out.println("  事件发布: OrderPlaced(orderId=" + orderId + ")");
        }
    }

    static void tacticalDemo() {
        System.out.println("【聚合内交互：规则与不变量闭环】");
        Money cny = new Money(new BigDecimal("10.00"), "CNY");
        Order order = new Order("9001", cny);
        order.addLine("SKU-A", 2, cny);
        order.addLine("SKU-B", 1, new Money(new BigDecimal("25.00"), "CNY"));
        System.out.printf("  小计 = %s 元%n", order.subtotal().amount());
        order.applyCoupon(new Money(new BigDecimal("5.00"), "CNY"));
        System.out.println("  应用优惠券 5 元 -> 通过（不超小计 45 元）");
        try {
            order.applyCoupon(new Money(new BigDecimal("999.00"), "CNY"));
        } catch (IllegalArgumentException e) {
            System.out.println("  超额抵扣被聚合拒绝：" + e.getMessage());
        }
        order.submit();
        System.out.println();
    }

    static void boundaries() {
        System.out.println("【聚合边界三问】");
        List<String> questions = List.of(
                "① 这条不变量要在哪个事务内成立？（涉及谁，聚合就含谁）",
                "② 谁是外部访问的唯一入口？（聚合根，其余全内部）",
                "③ 跨聚合协作怎么传？（ID 引用 + 领域事件，一个事务只改一个聚合）");
        for (String q : questions) {
            System.out.println("  " + q);
        }
        System.out.println("  反例：OrderItem 单独成聚合 -> “明细之和>=0”跨了两个事务 -> 不变量失效");
    }

    public static void main(String[] args) {
        tacticalDemo();
        boundaries();
    }
}
```

**课后练习**
1. 为订单聚合补上"同一 SKU 明细唯一"与"提交后金额不可变"两条不变量，写单元测试验证。
2. 把你项目里某个可变"金额类"改造成不可变值对象，列出改造后代码调用方式的变化。
3. 思考题：为什么"一个事务只改一个聚合"能根本回答 39-09 的跨库事务问题？从聚合边界与事务边界重合的推导读解。

---

### 49-03 聚合设计与领域事件实战

**知识点**
- **聚合间协作的三种模式**：① 同步调用（聚合 A 直接调 B —— 耦合最强，慎用）；② **领域事件最终一致**（A 发事件，B 订阅处理 —— 默认选择）；③ 过程管理器（Saga，编排跨聚合流程，39-09 TCC/本地消息表的领域版）。**默认选事件，强实时才同步**。
- **事件的设计原则**：过去时命名、携带"消费者所需最小字段"（ orderId + paidAmount + paidAt，不要整个聚合序列化）、不可变、带事件 ID（消费幂等键）。**事件瘦身**是消费者稳定的前提——事件字段即契约（46-08 数据契约的同源思想）。
- **事件的可靠发布**：进程内事件（Spring 事件总线，同事务）与跨服务事件（MQ）不同——**跨服务必须本地消息表/outbox**（39-09），"聚合保存 + 事件入库"同事务提交，后台投递。
- **同一上下文内的一致性策略**：强一致规则放聚合内（同事务）；跨聚合规则用事件 + 消费端校验（最终一致窗口内业务可接受吗？不能接受就合并进同一聚合或同步调用）。
- **用例编排归应用层**：应用服务（Application Service）负责"加载聚合 -> 调领域方法 -> 保存 -> 发事件"的编排，**不含业务规则**（规则在聚合）——贫血与充血的分界就在这层是否膨胀。
- **测试策略**：聚合单元测试（纯内存，无 Mock 依赖）跑不变量；应用层测试用例编排（Mock 仓储）；事件消费者测试幂等与失败重试（40-09 mock 思想）。
- 易错点：事件带聚合全量字段（契约爆炸，上游改字段下游全炸）；进程内事件当跨服务用（无持久化，宕机丢事件）；应用服务里写满业务规则（充血退化回贫血）；事件消费无幂等（重放翻倍）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DomainEventFlow {
    /** 领域事件：过去时、不可变、最小字段 */
    record OrderPaid(String eventId, String orderId, String userId, long paidAtMs, int amountCents) {
    }

    /** 聚合内产生事件（聚合不负责投递） */
    static class Order {
        final List<Object> pendingEvents = new ArrayList<>();

        void markPaid(String orderId, String userId, int cents) {
            pendingEvents.add(new OrderPaid("EV-" + orderId, orderId, userId,
                    System.currentTimeMillis(), cents));
            System.out.println("  聚合产生事件 OrderPaid（等待应用层投递）");
        }
    }

    /** 应用层：编排 + outbox 同事务 */
    static class OrderApplicationService {
        final Map<String, List<Object>> outbox = new LinkedHashMap<>();   // 待投递事件

        void pay(String orderId, String userId, int cents) {
            Order order = new Order();                      // 真实场景先查仓储
            order.markPaid(orderId, userId, cents);
            // 同事务：保存聚合 + 事件入 outbox（39-09 本地消息表）
            outbox.put(orderId, order.pendingEvents);
            System.out.println("  应用层：聚合保存 + 事件入 outbox（同一事务）");
        }
    }

    static void flow() {
        System.out.println("【完整事件流：聚合 -> outbox -> MQ -> 消费者】");
        OrderApplicationService app = new OrderApplicationService();
        app.pay("9001", "1001", 6900);
        System.out.println("  后台投递线程：outbox -> MQ（失败重试，N 次进死信）");
        // 消费者（库存上下文）
        OrderPaid ev = new OrderPaid("EV-9001", "9001", "1001", 0, 6900);
        boolean first = true;
        for (int attempt = 1; attempt <= 2; attempt++) {
            String seenKey = ev.eventId();
            boolean processed = seenKey.equals("EV-9001") && first;
            System.out.printf("  消费尝试#%d：幂等键=%s -> %s%n", attempt, seenKey,
                    processed ? "首次处理：扣减库存" : "重复事件 -> 忽略（幂等生效）");
            if (processed) {
                first = false;
            }
        }
        System.out.println();
    }

    static void eventDesignRules() {
        System.out.println("【事件设计五条军规】");
        List<String> rules = List.of(
                "过去时命名：OrderPaid / OrderCancelled（不是 PayOrder）",
                "最小字段：消费者要什么给什么，不序列化整个聚合",
                "不可变：事件是历史事实，无 setter",
                "带事件 ID：消费幂等的天然键（39 章/43 章同一思想）",
                "带发生时间与版本：消费端排序与乱序防护（39-08）");
        for (String r : rules) {
            System.out.println("  - " + r);
        }
        System.out.println();
    }

    static void consistencyChoice() {
        System.out.println("【同一上下文：强一致还是事件？】");
        String[][] rows = {
                {"订单-明细", "同一聚合（强一致）", "不变量必须同事务"},
                {"订单-库存", "事件最终一致", "超卖由库存侧兜底（预留+校验）"},
                {"订单-优惠券核销", "事件 + 消费端校验", "核销失败进补偿队列"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-12s %-16s %s%n", r[0], r[1], r[2]);
        }
        System.out.println("  判断式：最终一致窗口内业务可接受吗？不可接受 -> 合并聚合或同步");
    }

    public static void main(String[] args) {
        flow();
        eventDesignRules();
        consistencyChoice();
    }
}
```

**课后练习**
1. 为订单上下文补全事件清单（OrderPlaced/OrderPaid/OrderCancelled...），为每个事件写最小字段集与消费方列表。
2. 实现 outbox 模式模拟：聚合保存与事件入 outbox 同事务，后台投递失败重试，验证消费幂等。
3. 思考题：为什么"应用服务不含业务规则"是充血模型的底线？举一个规则漏到应用服务后引发的复用问题。

---

### 49-04 六边形架构与端口适配器

**知识点**
- **六边形架构（端口与适配器）的依赖规则**：**领域层在最内圈，不依赖任何外圈**；应用层编排用例；外圈的适配器（Web 控制器、DB 仓储、MQ 生产者）实现领域定义的"端口"（接口）。**依赖倒置（11 章 DIP 原则）的架构级应用**：不是领域依赖 DB，是 DB 适配器实现领域接口。
- **端口的两种方向**：**驱动侧端口**（左：用例接口，如 `PlaceOrderUseCase`，Web 控制器调用它）与**被驱动侧端口**（右：领域需要的出口，如 `OrderRepository`、`PaymentGateway`，由基础设施适配器实现）。**领域定义接口，外部实现接口**——业务可测试性（纯内存 Mock）与可替换性（换 DB 不改领域）由此而来。
- **与三层架构的对照**：传统三层 Controller->Service->Dao（领域逻辑渗进 Service，Service 依赖 Dao 具体实现）；六边形 Controller->Application(端口)->Domain<-Adapter(实现端口)。**箭头方向相反**——这是本质差异不是目录差异。
- **收益的验证方式**：领域模块 `mvn compile` 不依赖 Spring/JDBC 任何框架 jar（**无框架依赖测试**）；换存储时领域零改动；单元测试不启动容器。
- **何时值得**：核心域、规则复杂、生命周期长（三年以上维护）的系统；一次性活动页/CRUD 用标准三层更省。**架构也是成本决策**（49-01 的复杂度投资论）。
- 易错点：六边形只学目录不学依赖（领域层 import MyBatis，白搭）；端口粒度错（一个端口一个方法爆炸，按用例聚类）；为六边形而六边形（简单模块硬拆四层）；领域对象直接当 DTO 返回 Web（聚合边界泄漏到 API）。

**示例代码**
```java
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class HexagonalArch {
    /** 被驱动侧端口：领域定义，基础设施实现 */
    interface OrderRepository {
        void save(String orderId, int amountCents, String status);
    }

    /** 领域服务（纯业务，零框架依赖） */
    static class OrderDomainService {
        private final OrderRepository repo;      // 依赖接口，不依赖实现

        OrderDomainService(OrderRepository repo) {
            this.repo = repo;
        }

        String placeOrder(String orderId, int amountCents) {
            if (amountCents <= 0) {
                throw new IllegalArgumentException("金额必须为正");
            }
            String status = "PLACED";
            repo.save(orderId, amountCents, status);
            return status;
        }
    }

    /** 适配器 1：内存实现（单元测试用） */
    static class InMemoryOrderRepository implements OrderRepository {
        final Map<String, String> store = new LinkedHashMap<>();

        public void save(String orderId, int amountCents, String status) {
            store.put(orderId, amountCents + ":" + status);
        }
    }

    /** 适配器 2：模拟 MyBatis 实现（生产用，领域零感知） */
    static class MybatisOrderRepository implements OrderRepository {
        public void save(String orderId, int amountCents, String status) {
            System.out.println("  [MyBatis] INSERT INTO orders ... (" + orderId + ")");
        }
    }

    static void dependencyDemo() {
        System.out.println("【依赖倒置：领域定义端口，适配器实现】");
        OrderDomainService svc1 = new OrderDomainService(new InMemoryOrderRepository());
        System.out.println("  单测（内存适配器，无框架）：" + svc1.placeOrder("9001", 6900));
        OrderDomainService svc2 = new OrderDomainService(new MybatisOrderRepository());
        System.out.println("  生产（MyBatis 适配器）：" + svc2.placeOrder("9002", 9900));
        System.out.println("  领域代码两处完全相同 —— 换存储领域零改动");
        System.out.println();
    }

    static void hexagonMap() {
        System.out.println("【六边形的端口清单（订单上下文）】");
        String[][] ports = {
                {"驱动侧", "PlaceOrderUseCase", "Web 控制器 / MQ 监听器 调用"},
                {"驱动侧", "CancelOrderUseCase", "客服后台调用"},
                {"被驱动侧", "OrderRepository", "JPA/MyBatis 适配器实现"},
                {"被驱动侧", "PaymentGateway", "渠道适配器实现（51-02）"},
                {"被驱动侧", "EventPublisher", "MQ 适配器实现（outbox）"},
        };
        for (String[] p : ports) {
            System.out.printf("  %-6s %-20s %s%n", p[0], p[1], p[2]);
        }
        System.out.println();
    }

    static void testabilityProof() {
        System.out.println("【收益验证：无框架依赖测试】");
        List<String> checks = List.of(
                "mvn dependency:tree | grep -v spring —— domain 模块零框架依赖",
                "聚合单测纯内存运行 < 50ms（不启动 Spring 上下文）",
                "换存储演练：Mock 仓储 -> 真仓储，领域代码 diff 为 0",
                "用例编排可 Mock：应用层测试只 Mock 端口");
        for (String c : checks) {
            System.out.println("  [ ] " + c);
        }
        System.out.println("  达标即“真六边形”，只有目录结构而无依赖规则即“假六边形”");
    }

    public static void main(String[] args) {
        dependencyDemo();
        hexagonMap();
        testabilityProof();
    }
}
```

**课后练习**
1. 把你项目的某个 Service 改造为端口适配器形态：领域接口 + 两个实现（内存/真实），跑通"无框架依赖单测"。
2. 列出你模块的完整端口清单（驱动侧/被驱动侧），检查是否存在"领域 import 框架"的违例并用依赖树验证。
3. 思考题：为什么"领域不依赖框架"是可测试性的根源？从 Mock 成本与容器启动开销的角度量化对比。
