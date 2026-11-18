### 51-01 支付系统全景

**知识点**
- **支付系统是"资金的路由器"**：用户 -> 商户收银台 -> 支付网关（自己）-> 支付渠道（微信/支付宝/银行）-> 清结算 -> 商户账户。**每一跳都是资金**，每一跳的错误都以钱为单位——这就是为什么支付系统有全书最严格的质量标准（36 章安全、39 章一致性、44-09 权限在这里全部强化）。
- **核心域划分**：**收银**（面向用户：聚合支付方式）、**支付核心**（状态机+渠道路由）、**渠道网关**（对接外部支付渠道）、**账务**（复式记账，51-08）、**对账**（51-05）、**清算结算**（资金划拨）、**风控**（51-07）。**域与域之间事件驱动**（49-03），资金路径上强一致。
- **支付系统的三高要求**：**一致性**（一分钱都不能错——39-09 的本地消息表在资金域是标配）、**可靠性**（渠道抖动不能影响用户支付——降级路由多渠道）、**可审计**（每笔资金变动可追溯——事件溯源 49-06 的天然场景）。
- **钱的单位纪律**：全部用**分（long/int）**存储与计算，绝不用 double/float（14 章 BigDecimal 在支付域的纪律升级：整数分单位是行业标准）；展示层才转元。
- **与订单域的关系**：订单域发"待支付"事件 -> 支付域创建支付单 -> 渠道回调 -> 支付域发"支付成功"事件 -> 订单域推进状态。**支付单与订单单分离**（一笔订单可能多笔支付尝试）——单据模型是设计第一课。
- 易错点：金额用浮点（对账差分）；支付单与订单混用（重试语义混乱）；没有幂等键体系（重复扣款）；渠道返回"处理中"被当成失败（状态机断裂）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PaymentOverview {
    record Domain(String name, String duty, String keyDesign) {
    }

    static void domains() {
        System.out.println("【支付系统七个核心域】");
        List<Domain> ds = List.of(
                new Domain("收银", "聚合支付方式，面向用户", "收银台参数与有效期"),
                new Domain("支付核心", "支付单状态机 + 渠道路由", "幂等键体系（51-03）"),
                new Domain("渠道网关", "对接微信/支付宝/银行", "签名验签 + 熔断路由（51-02）"),
                new Domain("账务", "复式记账，账户余额", "借贷平衡（51-08）"),
                new Domain("对账", "渠道账单 vs 本地流水", "差异处理闭环（51-05）"),
                new Domain("清算结算", "资金划拨与分账", "T+1 批次与差错"),
                new Domain("风控", "交易风险识别", "规则+模型，拦截与放行（51-07）"));
        for (Domain d : ds) {
            System.out.printf("  %-6s %-22s %s%n", d.name(), d.duty(), d.keyDesign());
        }
        System.out.println();
    }

    static void moneyDiscipline() {
        System.out.println("【金额纪律：全程用分】");
        String bad = "double amount = 0.1 + 0.2;  // = 0.30000000000000004";
        System.out.println("  反例：" + bad);
        System.out.println("  正例：long cents = 10 + 20;     // = 30 分，整数运算无误差");
        List<String> rules = List.of(
                "存储/计算/传输全用分（long），序列化也用分",
                "展示层才转元（除以 100，注意格式化）",
                "BigDecimal 仅用于费率计算（如 0.6% 手续费），结果回整数分",
                "四舍五入规则统一（银行家舍入 vs 四舍五入，写入契约 46-08）");
        for (String r : rules) {
            System.out.println("  - " + r);
        }
        System.out.println();
    }

    static void orderVsPayment() {
        System.out.println("【订单单 vs 支付单：为什么要两张单】");
        Map<String, String> compare = new LinkedHashMap<>();
        compare.put("订单单（订单域）", "业务事实：买了什么，状态=待支付/已支付/已关闭");
        compare.put("支付单（支付域）", "资金事实：怎么付的，一笔订单可有多笔支付尝试");
        compare.put("关系", "1 个订单 : N 个支付单（用户换渠道重付/超时重试）");
        compare.put("状态推进", "支付单成功 -> 事件 -> 订单推进已支付（唯一推进入口）");
        for (Map.Entry<String, String> e : compare.entrySet()) {
            System.out.printf("  %-16s %s%n", e.getKey(), e.getValue());
        }
        System.out.println("  反例：订单表直接存渠道流水号 -> 多次尝试覆盖混乱，退款找不到依据");
        System.out.println();
    }

    static void eventFlow() {
        System.out.println("【域间事件流（49-03 的支付版）】");
        List<String> flow = List.of(
                "订单域：OrderCreated(待支付) -> 支付域创建支付单",
                "用户支付 -> 渠道回调 -> 支付单 PAID",
                "支付域：PaymentSucceeded -> 订单域（推进已支付）",
                "                       -> 履约域（创建运单，29 章事件链）",
                "                       -> 账务域（记账，借贷分录 51-08）",
                "全部事件经 outbox 保证不丢（39-09），消费幂等（39/43 章）");
        for (String s : flow) {
            System.out.println("  " + s);
        }
    }

    public static void main(String[] args) {
        domains();
        moneyDiscipline();
        orderVsPayment();
        eventFlow();
    }
}
```

**课后练习**
1. 画出你的支付系统域划分图（或基于本章七域裁剪），标注每个域的一致性级别与事件流。
2. 审查项目里的金额字段：找出所有 double/float 金额与"元字符串"存储，改成整数分并记录改造清单。
3. 思考题：为什么支付单与订单必须分离？从"多次支付尝试""部分退款""渠道对账"三个场景分析单据模型。

---

### 51-02 渠道对接与签名验签

**知识点**
- **渠道对接的三件事**：① **请求签名**（用商户私钥对参数签名，渠道验签防篡改）；② **回调验签**（渠道用其私钥回调，你用渠道公钥验签——**验签不过一律拒绝**，36 章安全原则的资金级执行）；③ **证书/密钥管理**（密钥不入库不入 git，KMS/Vault 管理，36 章供应链教训）。
- **渠道协议的共性**：下单接口（返回支付凭据如 prepay_id）、查询接口（主动查单补状态）、回调通知（异步告知结果）、退款接口、对账文件下载（51-05）。**抽象成统一渠道网关接口**（49-04 端口），各渠道适配器实现——新增渠道=新增适配器。
- **渠道返回码的语义分级**：成功 / 处理中（未知态！）/ 失败 ——**"处理中"必须转查询轮询**，绝不能当失败（51-03 状态机）。渠道码翻译进防腐层（49-07），领域只见自己的状态。
- **主动查询补状态**：回调可能丢（渠道侧事故）——**支付单超时未终态 -> 定时查渠道**（1s/5s/30s 退避，指数退避 40-02），查询结果与回调结果幂等合并（先到先得，状态机只进不退）。
- **渠道路由**：多渠道可用时按**成功率/费率/限额**路由——渠道熔断（连续失败摘除，47-03 电路思想）+ 降级到备用渠道（44-08 阶梯）。渠道故障是支付系统最常见的生产事故。
- 易错点：回调不验签直接处理（伪造回调=资金损失）；"处理中"当失败关单（渠道随后成功 -> 已关单但扣了钱 -> 客诉+资损）；密钥进代码库（36 章教训）；渠道超时与支付单超时不匹配（渠道还在处理你已关单）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ChannelIntegration {
    /** 渠道返回码 -> 领域状态（防腐层 49-07） */
    enum PaymentStatus {
        SUCCESS, PENDING, FAILED
    }

    record ChannelResult(PaymentStatus status, String channelTxId, String rawCode) {
    }

    static class ChannelAdapter {
        private static final Map<String, PaymentStatus> CODE_MAP = Map.of(
                "SUCCESS", PaymentStatus.SUCCESS,
                "PROCESSING", PaymentStatus.PENDING,
                "USERPAYING", PaymentStatus.PENDING,
                "FAIL", PaymentStatus.FAILED);

        ChannelResult normalize(String code, String txId, String signature, boolean signatureValid) {
            if (!signatureValid) {
                throw new IllegalStateException("回调验签失败 -> 拒绝处理（防伪造）");
            }
            PaymentStatus s = CODE_MAP.getOrDefault(code, PaymentStatus.PENDING);   // 未知码按 PENDING 走查询
            return new ChannelResult(s, txId, code);
        }
    }

    static void normalizeDemo() {
        System.out.println("【渠道码归一：未知码按 PENDING 处理】");
        ChannelAdapter adapter = new ChannelAdapter();
        for (String[] raw : new String[][]{{"SUCCESS", "ok"}, {"PROCESSING", "ok"},
                {"USERPAYING", "ok"}, {"NEWCODE_X", "ok（未映射）"}}) {
            ChannelResult r = adapter.normalize(raw[0], "ch-tx-1", "", true);
            System.out.printf("  渠道码 %-12s -> %s（txId=%s）%n", raw[0], r.status(), r.channelTxId());
        }
        try {
            adapter.normalize("SUCCESS", "ch-tx-2", "", false);
        } catch (IllegalStateException e) {
            System.out.println("  验签失败：" + e.getMessage());
        }
        System.out.println("  关键：PENDING 不是终态 -> 进入查询轮询（下一段代码）");
        System.out.println();
    }

    static void queryPolling() {
        System.out.println("【主动查询：回调丢失时的补状态机制】");
        long[] backoffs = {1000, 5000, 30000, 60000};
        String current = "PENDING";
        for (long backoff : backoffs) {
            if (current.equals("PENDING")) {
                // 模拟第 3 次查询渠道返回成功
                current = backoff == 30000 ? "SUCCESS" : "PENDING";
                System.out.printf("  等待 %ds 后查询渠道 -> %s%n", backoff / 1000, current);
            }
        }
        System.out.println("  终态后停止轮询；全部轮询用尽 -> 差异单进人工（51-05）");
        System.out.println("  幂等合并：查询与回调谁先到都一样（状态机只进不退，51-03）");
        System.out.println();
    }

    static void routing() {
        System.out.println("【渠道路由：成功率/费率/熔断】");
        record Channel(String name, double feeRate, double successRate, boolean circuitOpen) {
        }
        List<Channel> channels = List.of(
                new Channel("wechat", 0.006, 0.999, false),
                new Channel("alipay", 0.006, 0.998, false),
                new Channel("union-b", 0.005, 0.97, true));   // 熔断中：连续失败被摘除
        for (Channel c : channels) {
            System.out.printf("  %-8s 费率=%.1f%% 成功率=%.1f%% 熔断=%s%n",
                    c.name(), c.feeRate() * 100, c.successRate() * 100,
                    c.circuitOpen() ? "开（摘除）" : "关");
        }
        System.out.println("  路由决策：过滤熔断渠道 -> 剩余按费率/成功率加权选择");
        System.out.println("  熔断恢复：半开探测成功 -> 重新纳入（47-03 同款三态）");
    }

    public static void main(String[] args) {
        normalizeDemo();
        queryPolling();
        routing();
    }
}
```

**课后练习**
1. 为两个虚构渠道实现统一渠道网关接口（端口 49-04），含防腐层翻译与验签失败拒绝，写出翻译矩阵测试。
2. 实现查询轮询器（指数退避 + 终态停止 + 次数用尽进差异单），用回调丢失场景验证补状态。
3. 思考题：为什么"未知渠道码默认 PENDING"比默认 FAILED 安全？从"渠道后续成功"的时序推演两种默认值的资损/客诉差异。

---

### 51-03 支付状态机与幂等

**知识点**
- **支付单状态机是支付核心的骨架**：`CREATED -> PAYING -> SUCCESS / FAILED / CLOSED`，**只进不退**（SUCCESS 不可回 FAILED）；每个转移有前置条件与触发事件——**状态机保证并发回调/查询/关单的合并结果正确**。
- **并发场景的状态机裁决**：回调（成功）与关单任务（超时关闭）同时到达——**用状态机 + 数据库行锁（乐观版本号）串行化**：先到者赢，后到者按当前状态裁决（已 SUCCESS 则关单无效；已 CLOSED 则回调触发"已关闭但有款"差异单进人工/自动退款）。
- **幂等键体系是防重复扣款的根基**：三层——**业务幂等键**（orderId+支付渠道尝试号）、**渠道交易号**（channelTxId 唯一约束）、**回调幂等**（渠道通知 ID 去重表）。**同一支付请求无论重放多少次，最多扣一次款**（39/43 章幂等原则的资金级执行）。
- **创建支付单的幂等**：用户双击支付按钮 -> 两次创建请求 -> **以 orderId+场景为幂等键**返回同一支付单（而不是创建两张）；过期支付单自动关闭（延迟队列 28 章）后允许新建。
- **状态转移的持久化原子性**：状态更新 + 账务记账 + 事件发出必须原子（39-09）——支付域内用**本地事务**（同库）或 outbox；跨域靠事件最终一致。
- 易错点：状态机用 if-else 散落实现（转移规则无法审计，49 章状态建模退步）；重复回调重复记账（幂等缺失，直接资损）；关单与回调竞态未处理（已关单但扣款成功无人处理）；乐观锁缺失（两个并发回调都成功）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PaymentStateMachine {
    enum Status {
        CREATED, PAYING, SUCCESS, FAILED, CLOSED
    }

    /** 状态机：合法转移表（只进不退） */
    static final Map<Status, List<Status>> TRANSITIONS = Map.of(
            Status.CREATED, List.of(Status.PAYING, Status.CLOSED),
            Status.PAYING, List.of(Status.SUCCESS, Status.FAILED, Status.CLOSED));

    static class Payment {
        String id;
        Status status = Status.CREATED;
        long version = 0;                       // 乐观锁版本

        Payment(String id) {
            this.id = id;
        }

        /** CAS 式转移：先到者赢，后到者按当前状态裁决 */
        synchronized boolean transition(Status target, String trigger) {
            List<Status> allowed = TRANSITIONS.get(status);
            if (allowed == null || !allowed.contains(target)) {
                System.out.printf("  [%s] %s --%s--> %s 被拒绝（当前 %s 无此转移）%n",
                        id, status, trigger, target, status);
                return false;
            }
            status = target;
            version++;
            System.out.printf("  [%s] %s --%s--> %s (v%d)%n", id, status, trigger, target, version);
            return true;
        }
    }

    static void raceAdjudication() {
        System.out.println("【竞态裁决：回调成功 vs 超时关单同时到达】");
        Payment p = new Payment("PAY-1");
        p.transition(Status.PAYING, "用户发起");
        boolean closed = p.transition(Status.CLOSED, "关单任务（超时）");
        boolean paid = p.transition(Status.SUCCESS, "渠道回调（晚到）");
        System.out.println("  终态：" + p.status);
        if (closed && paid) {
            System.out.println("  竞态结果：先关单后回调成功 -> 已关单但有款");
            System.out.println("  处置：差异单 -> 自动原路退款 + 告警（51-05 差异闭环）");
        }
        System.out.println("  状态机只进不退：CLOSED 拒绝 SUCCESS 转移 -> 数据不被打乱");
        System.out.println();
    }

    static void idempotencyLayers() {
        System.out.println("【三层幂等键：重复请求最多扣一次款】");
        String[][] layers = {
                {"业务幂等键", "orderId+attemptNo -> 建单接口返回同一支付单", "防用户双击重复建单"},
                {"渠道交易号", "channelTxId 唯一索引", "防回调重放重复记账"},
                {"回调通知 ID", "notifyId 去重表", "渠道同一通知发多次"},
        };
        for (String[] l : layers) {
            System.out.printf("  %-10s %-38s %s%n", l[0], l[1], l[2]);
        }
        Map<String, Integer> dedupe = new LinkedHashMap<>();
        dedupe.put("notify-n1", 1);
        Integer seen = dedupe.putIfAbsent("notify-n1", 2);
        System.out.println("  模拟：同一 notifyId 第二次到达 -> putIfAbsent 发现已存在 -> 忽略（返回首次结果）");
        System.out.println();
    }

    static void atomicity() {
        System.out.println("【状态转移的原子性打包】");
        List<String> bundle = List.of(
                "① 支付单状态更新（乐观锁 version 校验）",
                "② 账务分录插入（51-08 借贷平衡）",
                "③ 事件写入 outbox（PaymentSucceeded）",
                "以上在同一数据库事务内提交 —— 39-09 本地事务的资金级应用");
        for (String s : bundle) {
            System.out.println("  " + s);
        }
        System.out.println("  反例：状态改了事件没发 -> 订单永远待支付（对账发现，51-05）");
    }

    public static void main(String[] args) {
        raceAdjudication();
        idempotencyLayers();
        atomicity();
    }
}
```

**课后练习**
1. 实现完整状态机（转移表 + 乐观锁 + 拒绝日志），并发模拟"回调与关单同时到达"1000 次，验证无数据错乱。
2. 设计三层幂等键的表结构（唯一索引设计），用并发插入验证唯一约束拦截重复。
3. 思考题：为什么状态机"只进不退"是资金安全的根基？从"已成功的支付被标记失败"会引发的下游连锁（订单/履约/账务）分析。

---

### 51-04 异步回调与通知可靠

**知识点**
- **回调是支付结果的主通道，但不可靠**：渠道回调可能重复（多次通知）、乱序（查单与回调交错）、丢失（渠道事故）——**回调处理必须幂等 + 查询兜底**（51-02），回调只是"加速器"，不是"唯一事实来源"。
- **回调处理的黄金流程**：验签 -> 幂等检查（notifyId/channelTxId）-> 状态机裁决 -> 本地事务（状态+账务+outbox）-> 返回渠道应答（success）——**任何一步失败都返回失败应答**让渠道重试（渠道会按退避重发）。
- **应答协议要严格遵守**：微信返回 XML/JSON success、支付宝返回 "success" 字符串——**应答格式错误渠道会无限重发**（打爆接口）；应答 success 但处理失败 = 静默丢事件（对账才能发现）。
- **对外通知（商户侧回调）同样要可靠**：支付成功后通知下游商户——**同样的重试 + 幂等 + 签名**（你是渠道，商户是你的下游），通知失败进重试队列（28 章延迟队列）。
- **回调风暴防护**：渠道重试风暴（网络故障恢复后积压回调齐发）——限流 + 去重后处理；**去重表先行**（验签前先查已处理，省验签开销可权衡）。
- 易错点：处理失败也返回 success（丢事件）；验签前先改状态（伪造回调直接改钱）；回调处理超时（渠道判定失败重发 -> 重复处理，幂等救场）；通知商户不带签名（商户侧无法验真）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CallbackReliability {
    static class CallbackHandler {
        final Map<String, String> processed = new LinkedHashMap<>();    // notifyId -> 应答
        final Map<String, String> payments = new LinkedHashMap<>();     // txId -> 状态

        String handle(String notifyId, String txId, String event,
                      boolean signatureValid, boolean processWillFail) {
            // 1) 幂等前置：同 notifyId 已处理 -> 直接返回首次应答
            String prior = processed.get(notifyId);
            if (prior != null) {
                System.out.printf("  notify=%s 重复 -> 返回首次应答 %s（不重复处理）%n",
                        notifyId, prior);
                return prior;
            }
            // 2) 验签
            if (!signatureValid) {
                return "FAIL:invalid-signature";
            }
            // 3) 处理（状态机 + 记账 + outbox）
            String ack;
            if (processWillFail) {
                ack = "FAIL:processing-error";      // 处理失败 -> 返回失败，渠道会重试
            } else {
                payments.put(txId, event);
                ack = "success";
                processed.put(notifyId, ack);
                System.out.printf("  notify=%s 处理成功：txId=%s -> %s%n", notifyId, txId, event);
            }
            return ack;
        }
    }

    static void flowDemo() {
        System.out.println("【回调处理黄金流程与失败重试】");
        CallbackHandler handler = new CallbackHandler();
        System.out.println("  第一次回调（处理成功）：");
        System.out.println("    应答：" + handler.handle("n-1", "ch-1", "SUCCESS", true, false));
        System.out.println("  渠道重发同通知（网络原因）：");
        System.out.println("    应答：" + handler.handle("n-1", "ch-1", "SUCCESS", true, false));
        System.out.println("  处理中途故障的回调：");
        String ack = handler.handle("n-2", "ch-2", "SUCCESS", true, true);
        System.out.println("    应答：" + ack + " -> 渠道按退避重试 -> 下次成功");
        System.out.println("  关键：失败必须返回失败应答，绝不能返回 success 掩盖问题");
        System.out.println();
    }

    static void ackProtocol() {
        System.out.println("【应答协议：格式错误 = 无限重发】");
        String[][] rows = {
                {"微信支付", "返回 XML/JSON success 标记", "格式错 -> 渠道按退避持续重发"},
                {"支付宝", "纯文本 success", "多余空格都算失败"},
                {"通用原则", "成功才 success；失败返回失败标记", "让渠道的重试机制工作"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-10s %-32s %s%n", r[0], r[1], r[2]);
        }
        System.out.println("  事故对照：处理失败却返回 success -> 渠道认为完成 -> 事件永久丢失");
        System.out.println("  兜底：即便如此，51-05 对账仍能发现（对账是最后防线）");
        System.out.println();
    }

    static void notifyDownstream() {
        System.out.println("【对外通知商户：你是渠道的角色】");
        List<String> duties = List.of(
                "通知内容签名（商户可验真，51-02 反向）",
                "重试策略：指数退避 + 最大次数 + 死信人工（28 章队列语义）",
                "商户确认应答校验（商户回 success 才算送达）",
                "通知查询接口：商户可主动拉取（回调丢失的兜底，对称 51-02）",
                "审计：每次通知的请求/应答/耗时全落库（41-10 台账思想）");
        for (String d : duties) {
            System.out.println("  - " + d);
        }
        System.out.println("  对称性：渠道如何对你，你就如何对商户 —— 协议镜像原则");
    }

    public static void main(String[] args) {
        flowDemo();
        ackProtocol();
        notifyDownstream();
    }
}
```

**课后练习**
1. 实现回调处理器完整版（幂等前置 + 验签 + 状态机 + 失败应答），模拟渠道重试风暴（100 个重复通知）验证去重。
2. 复现"处理失败返回 success"的事故：观察事件丢失如何在下游表现为"订单永远待支付"，然后修复应答逻辑。
3. 思考题：为什么"回调只是加速器，查询才是事实兜底"？从回调丢失、乱序、重复三种失效模式推演只依赖回调的风险。
