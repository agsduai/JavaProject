### 49-05 CQRS 读写分离建模

**知识点**
- **CQRS（命令查询职责分离）把写模型与读模型分开建模**：写模型（聚合）保证不变量与事务，为业务正确性服务；读模型（查询视图）为查询体验服务——**两者可以不同结构、不同存储、不同一致性**。"一个模型既当事务守卫又当查询视图"是复杂系统的常见负担。
- **读写不对称的根源**：写要满足不变量（订单聚合完整），查要跨表聚合展示（订单列表要商品名/用户名/物流状态）——**写模型稠密归一，读模型稀疏反范式**。强行用一个模型两头迁就（大宽表 + 事务守卫）两头都难受。
- **CQRS 的实现光谱**：轻量级——同一个库，写走聚合、读走 SQL 视图（**90% 场景够用**）；重量级——写库 + 事件投影到读库（ES/宽表），最终一致（第 38 章 ES 同步的架构身份就是"读模型投影"）。
- **事件驱动投影**：领域事件 -> 投影器（Projection）更新读模型——**读模型可整体重建**（事件回放，46-05 时间旅行同源思想），坏了重建而不是修补。
- **一致性的明确契约**：写后读自己的写（45-02）在读模型上如何保证——版本号 + 等待投影 + 超时回主读；**契约写进设计文档**。
- **何时需要重量级 CQRS**：查询维度与事务维度严重分歧（多维度列表/全文搜索/报表）、读性能独立伸缩、读模型可容忍秒级延迟。**不需要时重量级 CQRS 是分布式事务税**。
- 易错点：轻量级需求上了双库（维护投影管道，成本翻倍）；读模型无版本契约（写后读旧值客诉）；投影不可重建（读模型坏了手工修数据）；把 CQRS 理解成"必须事件溯源"（CQRS 可独立使用）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CqrsPattern {
    /** 写模型：聚合，保证不变量 */
    static class OrderWriteModel {
        final Map<String, Integer> orders = new LinkedHashMap<>();   // id -> amountCents
        final Map<String, String> statuses = new LinkedHashMap<>();

        void place(String orderId, int amountCents) {
            if (amountCents <= 0) {
                throw new IllegalArgumentException("金额必须为正");
            }
            orders.put(orderId, amountCents);
            statuses.put(orderId, "PAID");
            System.out.printf("  [写模型] 订单 %s 落库（不变量已校验）%n", orderId);
        }
    }

    /** 读模型：为列表查询而生的反范式视图 */
    record OrderListItem(String orderId, String status, String userName,
                         String productNames, int amountCents) {
    }

    static class OrderListView {
        final List<OrderListItem> rows = new ArrayList<>();

        /** 投影器：订阅事件更新视图（可整体重建） */
        void project(String orderId, String status, String userName,
                     String productNames, int amountCents) {
            rows.add(new OrderListItem(orderId, status, userName, productNames, amountCents));
            System.out.printf("  [投影] 视图行更新：%s（%s %s %s）%n", orderId, status, userName, productNames);
        }

        List<OrderListItem> listByUser(String userName) {
            List<OrderListItem> hits = rows.stream()
                    .filter(r -> r.userName().equals(userName)).toList();
            System.out.printf("  [读模型] 按 user=%s 查询 -> %d 行（一次反范式读，无 join）%n",
                    userName, hits.size());
            return hits;
        }
    }

    static void twoModels() {
        System.out.println("【同一业务：写模型与读模型各自成形】");
        OrderWriteModel write = new OrderWriteModel();
        write.place("9001", 6900);
        OrderListView view = new OrderListView();
        view.project("9001", "PAID", "小王", "iPhone 手机壳, 手机支架", 6900);
        view.listByUser("小王");
        System.out.println("  写模型关心不变量，读模型关心展示 —— 结构不同天经地义");
        System.out.println();
    }

    static void spectrum() {
        System.out.println("【CQRS 实现光谱：从轻到重】");
        String[][] rows = {
                {"L1 同库视图", "SQL 视图/接口聚合", "90% 场景，零管道成本"},
                {"L2 双模型同库", "写走聚合表、读走宽表（同库同步）", "查询形状差异大时"},
                {"L3 事件投影异构", "事件 -> ES/宽表，最终一致", "全文检索/多维列表（38 章正是 L3）"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-12s %-32s %s%n", r[0], r[1], r[2]);
        }
        System.out.println("  升级原则：从 L1 开始，查询需求推不动再升");
        System.out.println();
    }

    static void rebuild() {
        System.out.println("【投影可重建：读模型的修复姿势】");
        List<String> steps = List.of(
                "1) 读模型损坏/口径变更 -> 不修补，重建",
                "2) 清空视图 -> 从事件流重放（46-05 时间旅行同源）",
                "3) 双写期：新旧视图并存，对比一致后切流",
                "4) 投影器幂等：同一事件重放结果不变（39 章幂等原则）");
        for (String s : steps) {
            System.out.println("  " + s);
        }
        System.out.println("  前提：事件是完整真源（Event Sourcing 的价值由此显现，49-06）");
        System.out.println();
    }

    static void consistencyContract() {
        System.out.println("【写后读契约：读模型的最后一块拼图】");
        List<String> rules = List.of(
                "写侧返回版本号（事件 revision）",
                "读侧校验：投影版本 >= 写入版本才返回；否则回退写模型读",
                "超时兜底：等待投影 500ms 超时 -> 回写模型（正确性优先）",
                "契约写进接口文档（42 章新鲜度元数据同款思想）");
        for (String r : rules) {
            System.out.println("  - " + r);
        }
        System.out.println("  反例：支付成功页读到“未支付”-> 客诉 —— 45-02 read-your-writes 的落地");
    }

    public static void main(String[] args) {
        twoModels();
        spectrum();
        rebuild();
        consistencyContract();
    }
}
```

**课后练习**
1. 为订单列表实现 L1 轻量 CQRS（聚合写 + SQL 视图读），识别出"哪一步查询形状差异大"再评估是否升 L3。
2. 实现"投影重建"模拟：故意破坏视图，从事件流重放恢复，验证投影器幂等。
3. 思考题：为什么"轻量级 CQRS 从 L1 开始"是纪律？从投影管道的维护成本与查询需求的真实分布分析。

---

### 49-06 事件溯源与投影

**知识点**
- **事件溯源（Event Sourcing）把"状态"变成"事件的派生品"**：不存"订单当前状态"，存"订单的全部变更事件"；当前状态 = 事件按序回放的结果。**事件流是唯一真源（source of truth）**——审计天然完整、时间旅行免费（46-05 同源）、任何视图可重建。
- **快照（snapshot）解决回放成本**：事件千条后回放变慢——**每 N 条事件存一个状态快照**，恢复 = 快照 + 之后的事件重放（43-05 checkpoint 的同源思想：状态自持 + 日志重放）。
- **事件溯源 ≠ 事件消息**：溯源事件是"领域事实的完整记录"（真源），消息是"传递通知"（可丢可重）；**溯源事件流要求不可变存储与完整保留**——存储成本与治理成本更高。
- **版本化与升级**：事件 schema 演进（加字段/改语义）是事件溯源的最大运维负担——**upcaster（事件升级器）**把旧版本事件在读时转换为新版本；避免破坏性变更。
- **适用边界**：审计强需求（金融账务、合规）、需要重建任意历史视图（51-08 账务）、复杂协作需要回放调试——**普通 CRUD 用事件溯源是自找麻烦**（查询、修复、心智成本全上升）。
- **与 CQRS 的关系**：事件溯源天然伴生 CQRS（写侧是事件流，读侧是投影视图）——但两者可独立使用（49-05）。
- 易错点：把事件溯源当潮流上 CRUD 系统（复杂度爆炸）；事件 schema 无版本管理（历史事件读不出来）；快照策略缺失（回放越来越慢）；敏感数据进事件流（加密与脱敏难题，36 章合规约束）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class EventSourcing {
    /** 事件流：真源 */
    sealed interface OrderEvent permits Created, Paid, Cancelled {
    }

    record Created(String orderId, int amountCents) implements OrderEvent {
    }

    record Paid(String orderId, long paidAtMs) implements OrderEvent {
    }

    record Cancelled(String orderId, String reason) implements OrderEvent {
    }

    /** 状态 = 事件回放的派生品 */
    record OrderState(String orderId, int amountCents, String status) {
    }

    static OrderState replay(List<OrderEvent> events, OrderState snapshot) {
        OrderState s = snapshot;
        for (OrderEvent e : events) {
            if (e instanceof Created c) {
                s = new OrderState(c.orderId(), c.amountCents(), "CREATED");
            } else if (e instanceof Paid) {
                s = new OrderState(s.orderId(), s.amountCents(), "PAID");
            } else if (e instanceof Cancelled) {
                s = new OrderState(s.orderId(), s.amountCents(), "CANCELLED");
            }
        }
        return s;
    }

    static void replayDemo() {
        System.out.println("【事件回放：状态是派生品】");
        List<OrderEvent> stream = List.of(
                new Created("9001", 6900),
                new Paid("9001", System.currentTimeMillis()));
        OrderState s = replay(stream, null);
        System.out.printf("  回放 %d 个事件 -> 状态 %s%n", stream.size(), s);
        List<OrderEvent> withCancel = new ArrayList<>(stream);
        withCancel.add(new Cancelled("9001", "用户申请"));
        System.out.printf("  追加 Cancelled -> %s%n", replay(withCancel, null));
        System.out.println("  任意历史时点：截断事件流即可重建当时状态（时间旅行）");
        System.out.println();
    }

    static void snapshotFlow() {
        System.out.println("【快照：回放成本的控制】");
        int totalEvents = 5000, snapshotEvery = 1000;
        List<OrderEvent> tail = new ArrayList<>();
        System.out.printf("  每 %d 个事件存一个快照 -> 恢复 = 快照 + %d 条尾部事件重放%n",
                snapshotEvery, totalEvents % snapshotEvery);
        System.out.printf("  对比全量回放 %d 条：快照路径只重放 %d 条（成本降 %.0f%%）%n",
                totalEvents, totalEvents % snapshotEvery,
                (1 - totalEvents % snapshotEvery / (double) totalEvents) * 100);
        System.out.println("  快照本身也要版本化：状态 schema 变更时快照作废重建");
        System.out.println();
    }

    static void versionUpgrade() {
        System.out.println("【事件 schema 演进：upcaster】");
        record PaidV1(String orderId) {
        }
        System.out.println("  旧事件 PaidV1(orderId) 缺 paidAtMs 字段：");
        System.out.println("  upcaster：读时转换 PaidV1 -> Paid(orderId, paidAtMs=0 默认)");
        List<String> rules = List.of(
                "只加不改：新字段给默认值，禁止改语义",
                "upcaster 链：V1 -> V2 -> V3 逐级升级，历史事件永远可读",
                "破坏性变更 -> 新事件类型（PaidV2），不修改旧类型");
        for (String r : rules) {
            System.out.println("  - " + r);
        }
        System.out.println();
    }

    static void fitDecision() {
        System.out.println("【适用决策：值不值得事件溯源】");
        String[][] rows = {
                {"账务/资金流水", "值得", "审计是业务需求本身（51-08）"},
                {"订单生命周期", "酌情", "事件流对账与重现有价值，成本可控"},
                {"普通 CRUD", "不值得", "查询/修复/心智成本全上升"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-12s %-6s %s%n", r[0], r[1], r[2]);
        }
        System.out.println("  门槛三问：需要完整审计吗？需要重建历史视图吗？愿意承担 schema 演进成本吗？");
    }

    public static void main(String[] args) {
        replayDemo();
        snapshotFlow();
        versionUpgrade();
        fitDecision();
    }
}
```

**课后练习**
1. 实现完整事件溯源聚合：事件流 + 回放 + 快照 + 状态查询，构造 2000 条事件验证快照加速效果。
2. 为 Paid 事件设计 V1->V2 演进（加字段），实现 upcaster 并验证历史事件仍可回放。
3. 思考题：为什么"事件溯源与 CQRS 天然伴生"？从"写侧不可查询、读侧需投影"的结构必然性分析。

---

### 49-07 上下文集成与防腐层

**知识点**
- **上下文之间的集成模式**：**共享内核**（共用部分模型，耦合强，慎用）、**客户-供应商**（下游适配上游）、**防腐层（ACL，Anti-Corruption Layer）**（在边界上做模型翻译，保护自己的领域不被外部模型污染）、**事件集成**（49-03 的默认选项）。**防腐层是跨上下文集成的标配防线**。
- **防腐层解决什么**：第三方/遗留系统的模型与你的领域模型不一致——**翻译发生在边界适配器**，领域内永远说自己的通用语言。"外部返回 status=A1/B2" vs 你的 OrderStatus 枚举——没有 ACL，外部枚举会渗透进领域代码。
- **遗留系统集成是 ACL 的主战场**：不改老系统（改不动），在边界写翻译层——**绞杀者模式（Strangler Fig）**：新系统逐功能接管，ACL 承接过渡期流量，旧功能逐个"绞杀"下线。
- **ACL 的实现位置**：适配器层（49-04 六边形的外圈）——`PaymentGatewayAdapter` 把渠道返回码翻译为领域事件；**领域层只认识自己的语言**。
- **集成测试的重心**：ACL 的翻译正确性是跨上下文 Bug 高发区——**翻译表（映射矩阵）驱动测试**：外部状态 × 内部状态全覆盖（40-03 few-shot 边界示例同源思想）。
- 易错点：外部模型直接进领域（DTO 直接当聚合用）；ACL 退化成透传（没有真正翻译）；绞杀者改造无流量开关（无法回退旧系统）；翻译表无测试（外部升级一改，静默错译）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AntiCorruptionLayer {
    /** 外部遗留系统的模型（不能渗透进领域） */
    static class LegacyPaymentResponse {
        String retCode;      // "00"=成功, "A1"=处理中, "B2"=失败
        String retMsg;
        String amt;          // 单位：分（字符串！）
    }

    /** 领域自己的语言 */
    enum PaymentStatus {
        SUCCESS, PENDING, FAILED
    }

    /** 防腐层：翻译发生在边界 */
    static class PaymentGatewayAdapter {
        record Translation(LegacyPaymentResponse raw, PaymentStatus status, int amountCents) {
        }

        private static final Map<String, PaymentStatus> CODE_MAP = Map.of(
                "00", PaymentStatus.SUCCESS,
                "A1", PaymentStatus.PENDING,
                "B2", PaymentStatus.FAILED);

        Translation translate(LegacyPaymentResponse resp) {
            PaymentStatus status = CODE_MAP.getOrDefault(resp.retCode, PaymentStatus.FAILED);
            int cents;
            try {
                cents = Integer.parseInt(resp.amt);
            } catch (NumberFormatException e) {
                cents = -1;                      // 异常金额 -> FAILED，绝不透传垃圾
            }
            return new Translation(resp, cents >= 0 ? status : PaymentStatus.FAILED, cents);
        }
    }

    static void aclDemo() {
        System.out.println("【防腐层：外部模型在边界被翻译】");
        PaymentGatewayAdapter adapter = new PaymentGatewayAdapter();
        for (String[] raw : new String[][]{{"00", "OK", "6900"}, {"A1", "处理中", "6900"},
                {"B2", "银行拒绝", "6900"}, {"X9", "未知码", "abc"}}) {
            LegacyPaymentResponse resp = new LegacyPaymentResponse();
            resp.retCode = raw[0];
            resp.retMsg = raw[1];
            resp.amt = raw[2];
            var t = adapter.translate(resp);
            System.out.printf("  外部 retCode=%-3s amt=%-5s -> 领域状态=%s 金额=%d 分%n",
                    resp.retCode, resp.amt, t.status(), t.amountCents());
        }
        System.out.println("  领域代码只见 PaymentStatus.SUCCESS —— 未知码被安全兜底");
        System.out.println();
    }

    static void stranglerPlan() {
        System.out.println("【绞杀者模式：逐步替换遗留订单系统】");
        List<String> steps = List.of(
                "阶段0：新订单上下文上线，ACL 承接遗留系统的读取（数据仍由旧系统写）",
                "阶段1：新订单接管创建（写切新，读兼容旧）—— 按流量灰度",
                "阶段2：查询/报表切新 —— 旧系统只处理历史只读",
                "阶段3：历史数据迁湖（46-09）-> 旧系统下线，ACL 移除");
        for (String s : steps) {
            System.out.println("  " + s);
        }
        System.out.println("  每阶段有流量开关可回退（41-07 灰度纪律的基础设施版）");
        System.out.println();
    }

    static void translationTests() {
        System.out.println("【翻译矩阵测试：ACL 的单元测试核心】");
        List<String> cases = List.of(
                "retCode 全枚举 × amt 合法/非法/空 -> 状态与金额全覆盖",
                "新增外部码 X9（未映射）-> 默认 FAILED 而不是崩溃",
                "amt 字段类型漂移（分->元）-> 校验规则当场拦截（46-08 契约同源）");
        for (String c : cases) {
            System.out.println("  - " + c);
        }
        System.out.println("  ACL 是外部系统的“看门人”：翻译矩阵就是它的测试用例集");
    }

    public static void main(String[] args) {
        aclDemo();
        stranglerPlan();
        translationTests();
    }
}
```

**课后练习**
1. 为一个第三方接口（或虚构）写完整防腐层：翻译矩阵 + 未知码兜底 + 金额校验，并用翻译矩阵测试全覆盖。
2. 制定一个绞杀者改造计划：三个阶段各自的范围、流量开关、回退条件。
3. 思考题：为什么防腐层"宁做翻译也不透传"？从领域语言纯度与外部变更影响面两个角度分析。

---

### 49-08 DDD 与微服务、单体落地

**知识点**
- **DDD 与微服务的关系：上下文是服务的理论依据**——29 章按业务拆服务，本质就是按限界上下文切（49-01）。**一个上下文 = 一个（或一组）服务**；上下文间用事件与 API 集成（上下文映射图 = 服务调用图）。
- **不是每个上下文都要独立微服务**：上下文与服务的映射是弹性决策——小团队可以把 2~3 个小上下文放进一个模块化单体（modular monolith），**用包边界 + 架构测试（ArchUnit 类）守护边界**，等服务规模再拆。**先模块化单体、再按痛点拆微服务**是多数团队的正确路径。
- **模块化单体的纪律**：包间只通过公共接口调用（internal 包不可跨模块引用）、独立构建模块（Maven 多模块）、边界测试（依赖方向检查）——**单体里的边界 discipline 是未来拆分的全部基础**。
- **拆分的触发条件**：上下文要独立伸缩（吞吐曲线不同）、独立部署节奏（发布频率冲突）、独立技术栈、团队边界（康威定律）——**没有这些痛点就别拆**（29 章的拆分代价在 DDD 视角下更清晰）。
- **数据库的对应决策**：模块化单体可共享库但分 schema（禁止跨模块 join，49-05 的读模型解决跨上下文查询）；拆微服务时按上下文分库（39 章的分片在上下文内做）。
- 易错点：分布式单体（服务拆了但耦合还在：同步调用链、共享库、共享库表）；一步到位微服务（上下文边界都没摸清就拆，拆错再合并成本极高）；边界无守护（单体三个月后包互相乱引，形同虚设）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.List;

public class DddMicroservices {
    record ContextService(String context, String deployment, String data, String trigger) {
    }

    static void mappingMatrix() {
        System.out.println("【上下文 -> 部署形态的映射决策】");
        List<ContextService> rows = List.of(
                new ContextService("交易", "独立微服务", "独立库（按用户分片 39 章）",
                        "吞吐曲线独立 + 发布频率高"),
                new ContextService("营销", "模块化单体中的模块", "共享库分 schema",
                        "规则常变但吞吐一般"),
                new ContextService("通知", "轻量服务", "独立库", "简单但需要事件消费"),
                new ContextService("后台配置", "单体模块", "共享库", "无独立伸缩需求"));
        for (ContextService r : rows) {
            System.out.printf("  %-6s %-16s %-20s 触发：%s%n",
                    r.context(), r.deployment(), r.data(), r.trigger());
        }
        System.out.println("  同一家公司两种形态并存：按上下文逐个决策，不一刀切");
        System.out.println();
    }

    static void modularMonolith() {
        System.out.println("【模块化单体的边界纪律】");
        List<String> rules = List.of(
                "Maven 多模块：order / inventory / marketing / shared-kernel",
                "包边界：模块间只经 public 接口调用，internal 包禁止跨引",
                "架构测试：ArchUnit 规则进 CI（跨模块引 internal 即红）",
                "数据：同库不同 schema，禁止跨模块 JOIN（49-05 读模型解决）",
                "事件：进程内事件总线起步，拆服务时平移到 MQ");
        for (String r : rules) {
            System.out.println("  - " + r);
        }
        System.out.println("  收益：单体的开发效率 + 拆分时边界现成 —— 零重构拆微服务");
        System.out.println();
    }

    static void splitTriggers() {
        System.out.println("【拆微服务的触发条件：满足才拆】");
        List<String> triggers = List.of(
                "伸缩差异：交易 QPS 5000，营销 200 -> 独立伸缩收益明显",
                "发布冲突：营销周更三次常改表结构，交易被连带回归",
                "团队边界：康威定律——团队按上下文分工后服务随之拆",
                "技术需求：营销要引入规则引擎/独立缓存策略");
        for (String t : triggers) {
            System.out.println("  - " + t);
        }
        System.out.println("  反面：为了简历好看拆 15 个微服务 -> 分布式单体（最差形态）");
        System.out.println();
    }

    static void distributedMonolith() {
        System.out.println("【识别分布式单体：拆了等于没拆】");
        List<String> smells = List.of(
                "同步调用链：A->B->C->D 一次用户操作穿四个服务",
                "共享库：五个服务连同一个库的同一批表",
                "发布联动：任何服务改动都要全量回归",
                "事件缺失：全部靠同步 RPC，无领域事件（49-03）",
                "上下文翻译缺失：B 直接复用 A 的 DTO（无防腐层 49-07）");
        for (String s : smells) {
            System.out.println("  " + s);
        }
        System.out.println("  处方：回到上下文映射重划边界 + 事件化集成 + 每服务独立数据");
    }

    public static void main(String[] args) {
        mappingMatrix();
        modularMonolith();
        splitTriggers();
        distributedMonolith();
    }
}
```

**课后练习**
1. 为你的系统做"上下文 -> 部署形态"映射决策表（≥4 个上下文），标注每行的触发条件。
2. 用 ArchUnit（或手写依赖检查脚本）给模块化单体立边界规则，故意违规一次验证 CI 拦截。
3. 思考题：为什么"分布式单体是最差形态"？从复杂度（分布式税）与收益（边界收益为零）的对比分析。
