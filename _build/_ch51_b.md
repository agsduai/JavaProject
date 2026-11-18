### 51-05 对账系统

**知识点**
- **对账是资金安全的最后防线**：回调丢失、状态机 bug、渠道异常——所有上游失误最终都会体现在"渠道账单 vs 本地流水"的差异里。**每天 T+1 对账一次是底线**（高频交易可小时级），对账发现的问题必须有闭环处理流程。
- **对账的输入与流程**：渠道账单文件（下载/解析/入库）-> 与本地支付流水按**渠道交易号**对齐 -> 三态分类：**两边一致**（通过）、**长款**（渠道有本地无——回调丢失/处理失败）、**短款**（本地有渠道无——重复记账/伪造）-> 差异单进处理流程。
- **差异处理的分级闭环**：长款（渠道成功本地未处理）-> 自动补处理（查单 -> 状态机推进 -> 补记账）；短款（本地成功渠道无）-> 严重问题（可能资损/伪造）-> 立即冻结 + 人工排查；金额不一致 -> 精确到分的差异记录 -> 人工裁决。**差异单是工单，有 SLA 有责任人**（41-10 台账思想）。
- **对账文件本身的可靠性**：渠道账单可能分片、有总额行（校验行数与金额合计）、字符集/分隔符差异（46-09 数据集成的老问题）——**文件级校验先行**（总额对不上不进明细对账，先查文件解析）。
- **对账的粒度与时效**：日对账（T+1）兜底 + **准实时对账**（小时级增量）缩短发现窗口——**发现延迟决定资损敞口**（凌晨丢 1000 笔回调，第二天才对账 = 敞口一天）。
- 易错点：对账结果只出报表无闭环（差异单无人处理）；只对金额不对状态（状态漏推发现不了）；渠道账单字段假设过强（渠道改版解析崩）；差异单无 SLA（积压一个月）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ReconciliationSystem {
    record LocalTx(String channelTxId, long amountCents, String status) {
    }

    record ChannelTx(String channelTxId, long amountCents, String status) {
    }

    enum DiffType {
        MATCH, LONG, SHORT, AMOUNT_MISMATCH
    }

    record Diff(DiffType type, String channelTxId, String detail, String action) {
    }

    static void reconcile() {
        System.out.println("【日终对账：渠道账单 vs 本地流水】");
        Map<String, LocalTx> local = new LinkedHashMap<>();
        local.put("ch-1", new LocalTx("ch-1", 6900, "SUCCESS"));
        local.put("ch-2", new LocalTx("ch-2", 3900, "SUCCESS"));
        local.put("ch-3", new LocalTx("ch-3", 5900, "SUCCESS"));
        Map<String, ChannelTx> channel = new LinkedHashMap<>();
        channel.put("ch-1", new ChannelTx("ch-1", 6900, "SUCCESS"));
        channel.put("ch-2", new ChannelTx("ch-2", 3900, "SUCCESS"));
        channel.put("ch-4", new ChannelTx("ch-4", 12900, "SUCCESS"));   // 渠道有本地无（回调丢失）
        channel.put("ch-3", new ChannelTx("ch-3", 590, "SUCCESS"));     // 金额不一致
        List<Diff> diffs = new ArrayList<>();
        for (Map.Entry<String, ChannelTx> e : channel.entrySet()) {
            LocalTx lt = local.get(e.getKey());
            if (lt == null) {
                diffs.add(new Diff(DiffType.LONG, e.getKey(),
                        "渠道成功本地缺失", "查单补处理：推进状态机 + 补记账"));
            } else if (lt.amountCents() != e.getValue().amountCents()) {
                diffs.add(new Diff(DiffType.AMOUNT_MISMATCH, e.getKey(),
                        "本地 " + lt.amountCents() + " vs 渠道 " + e.getValue().amountCents(),
                        "冻结 + 人工裁决（精确到分）"));
            } else {
                diffs.add(new Diff(DiffType.MATCH, e.getKey(), "一致", "通过"));
            }
        }
        for (String id : local.keySet()) {
            if (!channel.containsKey(id)) {
                diffs.add(new Diff(DiffType.SHORT, id, "本地成功渠道无",
                        "严重：疑似伪造/重复记账 -> 冻结 + 立即排查"));
            }
        }
        for (Diff d : diffs) {
            System.out.printf("  [%-15s] %-6s %s%n    处置：%s%n", d.type(), d.channelTxId(),
                    d.detail(), d.action());
        }
        System.out.println();
    }

    static void fileValidation() {
        System.out.println("【账单文件先校验，再进明细对账】");
        record Check(String item, String method, String result) {
        }
        List<Check> checks = List.of(
                new Check("总行数", "文件行数 = 明细行 + 头尾行", "PASS"),
                new Check("金额合计", "明细金额合计 = 汇总行金额", "FAIL：差 12900 分"),
                new Check("处理", "总额不平 -> 不进明细对账", "FAIL：先查解析/分片"));
        for (Check c : checks) {
            System.out.printf("  %-8s %-30s %s%n", c.item(), c.method(), c.result());
        }
        System.out.println("  原因：文件分片下载漏了一段 -> 明细对账会误报海量短款");
        System.out.println("  文件级校验把「解析问题」与「真实差异」分开 —— 排障不混线");
        System.out.println();
    }

    static void slaLoop() {
        System.out.println("【差异单的 SLA 闭环】");
        List<String> loop = List.of(
                "差异单生成 -> 分级：长款（自动可处理）/ 短款（冻结人工）/ 金额差（人工）",
                "长款自动补处理：查单 -> 状态机推进 -> 补记账 -> 复核",
                "短款冻结：相关支付单挂起，禁止后续操作，4 小时内响应",
                "月度差异分析：分类统计根因（回调丢失/状态机 bug/渠道异常）-> 进改进项",
                "目标：差异单平均处理时长 < 24h，长款自动处理率 > 90%");
        for (String s : loop) {
            System.out.println("  " + s);
        }
        System.out.println("  对账不是报表，是闭环流程 —— 42-11 复盘思想在资金域的常态运行");
    }

    public static void main(String[] args) {
        reconcile();
        fileValidation();
        slaLoop();
    }
}
```

**课后练习**
1. 实现对账器完整版（本地/渠道 Map 对齐 + 四类差异 + 差异单分级），构造四类差异各一例验证。
2. 实现账单文件校验器（行数/总额/编码），注入"分片丢失"场景验证文件级拦截。
3. 思考题：为什么"短款"比"长款"更危险？从资金安全（伪造/资损）与处理自动化的可能性两个角度对比。

---

### 51-06 退款链路

**知识点**
- **退款比支付复杂一个量级**：支付是单向流转，退款涉及**部分退款、多次退款、退款上限、原路退回、退款失败重试**——每一项都能引出错账。**退款总额不可超实付**是不变量（聚合校验 49-02 的资金版）。
- **部分退款的分摊**：退 30 元从哪些明细扣？**按明细分摊 + 尾差规则**（49-09 的分摊算法在退款复用）；优惠分摊退回规则（优惠券部分退还还是作废）是业务契约（46-08），必须在创建订单时就定义。
- **原路退回的渠道约束**：退回原支付渠道（微信付的退微信）——**渠道退款单号幂等**、退款可能 PENDING（渠道处理中）、退款失败要重试（渠道余额不足等临时错）；**退款也有对账**（渠道退款账单 vs 本地退款流水）。
- **退款状态机**：`REFUND_CREATED -> REFUNDING -> SUCCESS / FAILED`——与支付状态机同构（51-03 原则复用），**退款不可超实付**的校验在"创建退款单"时以剩余可退金额计算。
- **退款与履约的联动**：退款成功 -> 触发库存回补（事件）、优惠券是否返还（规则）、发票红冲（合规）——**退款是跨域事件源**（49-03 的发出方）。
- 易错点：可退金额计算不含已退款（超退资损）；部分退款分摊不一致（对账差分）；退款 PENDING 当失败重发（渠道重复退款）；退款成功后不回补库存（超卖数据错乱）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.List;

public class RefundComplexity {
    /** 订单聚合视角：可退金额 = 实付 - 已退（不变量） */
    static class Order {
        final String orderId;
        final long paidCents;
        long refundedCents = 0;
        final List<String> refundIds = new ArrayList<>();

        Order(String orderId, long paidCents) {
            this.orderId = orderId;
            this.paidCents = paidCents;
        }

        long refundableCents() {
            return paidCents - refundedCents;
        }

        /** 创建退款单：不变量校验在聚合内 */
        String applyRefund(long amountCents, String refundId) {
            if (amountCents <= 0) {
                throw new IllegalArgumentException("退款金额必须为正");
            }
            if (amountCents > refundableCents()) {
                throw new IllegalArgumentException(
                        "可退 " + refundableCents() + " 分，申请 " + amountCents + " 分 -> 超退拒绝");
            }
            refundedCents += amountCents;
            refundIds.add(refundId);
            System.out.printf("  退款单 %s：%d 分（剩余可退 %d 分）%n",
                    refundId, amountCents, refundableCents());
            return refundId;
        }
    }

    static void refundFlow() {
        System.out.println("【部分退款与超退拦截】");
        Order order = new Order("9001", 6900);
        order.applyRefund(2000, "RF-1");        // 部分退 20 元
        order.applyRefund(4900, "RF-2");        // 再退剩余
        try {
            order.applyRefund(100, "RF-3");     // 已退满 -> 超退
        } catch (IllegalArgumentException e) {
            System.out.println("  超退被聚合拒绝：" + e.getMessage());
        }
        System.out.println("  不变量「累计退款 ≤ 实付」在聚合内闭环 —— 49-02 的资金应用");
        System.out.println();
    }

    static void refundStateMachine() {
        System.out.println("【退款单状态机与 PENDING 处理】");
        String[][] flow = {
                {"REFUND_CREATED", "校验可退额 -> 发起渠道退款", "本地事务"},
                {"REFUNDING", "渠道受理中（PENDING）", "查询轮询（51-02 同款）"},
                {"REFUND_SUCCESS", "渠道确认退款", "触发库存回补/券返还事件"},
                {"REFUND_FAILED", "渠道明确失败", "重试或人工；可退额度恢复"},
        };
        for (String[] f : flow) {
            System.out.printf("  %-16s %-28s %s%n", f[0], f[1], f[2]);
        }
        System.out.println("  关键：REFUNDING 不当失败重发（渠道重复退款）—— 查询确认终态");
        System.out.println();
    }

    static void refundAllocation() {
        System.out.println("【部分退款的分摊：明细级扣减】");
        long[] linePaid = {3000, 2000, 1900};      // 三明细实付（分）
        long refund = 3000;
        long remaining = refund;
        List<Long> alloc = new ArrayList<>();
        for (int i = 0; i < linePaid.length; i++) {
            long take = Math.min(remaining, linePaid[i]);
            alloc.add(take);
            remaining -= take;
        }
        for (int i = 0; i < alloc.size(); i++) {
            System.out.printf("  明细%d 实付 %d -> 退 %d（剩余可退 %d）%n",
                    i + 1, linePaid[i], alloc.get(i), linePaid[i] - alloc.get(i));
        }
        System.out.println("  尾差规则与 49-09 相同：契约定义，最后行吸收");
        System.out.println("  优惠券分摊退回规则在订单创建时即定义（46-08 契约）");
    }

    public static void main(String[] args) {
        refundFlow();
        refundStateMachine();
        refundAllocation();
    }
}
```

**课后练习**
1. 实现订单聚合的完整退款 API（多次/部分/超退拦截），并发退款 1000 次验证不变量永不破坏。
2. 复现"REFUNDING 当失败重发"事故：渠道重复退款资损场景，修复为查询确认终态。
3. 思考题：为什么"可退金额 = 实付 - 已退"必须放聚合而不是放在退款服务里计算？从并发退款与不变量守卫的角度分析。

---

### 51-07 资金安全与风控

**知识点**
- **资金安全的三道防线**：**事前风控**（交易前识别风险：限流/拦截/增强验证）、**事中校验**（支付链路的参数与权限校验，44-09 四道闸的资金版）、**事后对账与审计**（51-05 + 操作审计不可抵赖）。**任何一道都不能独立兜底**。
- **风控的规则与模型**：规则层（单用户限额、频次、黑名单、地域异常——确定性高、可解释）；模型层（评分/异常检测——覆盖规则盲区）；**决策动作分级**：放行 / 增强验证（二次确认/短信）/ 拦截（44-09 的分级思想）。
- **操作审计与最小权限**：后台的退款/调价/改单操作——**操作人+审批+全量审计日志**（44-09 trace 的资金版）；退款审批走双人（41-10 紧急通道的资金版）；**系统账号与人工操作权限分离**。
- **防内部作恶**：改金额接口只允许服务调用（外部不可达）、金额以数据库为准（模型输出仅参考 40-06）、敏感操作二次确认（独立确认通道）——**信任边界在资金域一寸不让**（36 章原则）。
- **限额体系**：单笔/单日/单月限额（用户级、商户级、渠道级）——限额是风控的硬约束，**超限直接拒绝**而不是风控评分。
- 易错点：风控只拦用户不防内部（内部改单无审批）；规则拦截无解释（客诉无法处理）；审计日志可篡改（无 append-only 与权限隔离）；限额定错了单位（分/元混淆导致限额失效）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.List;

public class FundSafety {
    enum Decision {
        ALLOW, CHALLENGE, BLOCK
    }

    record RiskResult(Decision decision, String reason, String action) {
    }

    static class RiskEngine {
        RiskResult evaluate(String userId, long amountCents, int recentCount) {
            // 规则层：确定性约束（硬限额优先）
            if (amountCents > 500_000) {
                return new RiskResult(Decision.BLOCK, "超单笔限额 5000 元", "拒绝并记录");
            }
            if (recentCount > 20) {
                return new RiskResult(Decision.BLOCK, "频次异常（>20 次/时）", "拒绝，疑似撞库");
            }
            // 规则层：软风险 -> 增强验证
            if (amountCents > 100_000) {
                return new RiskResult(Decision.CHALLENGE, "大额交易", "二次确认/短信验证");
            }
            // 模型层评分（示意）
            double score = 0.15;
            if (score > 0.9) {
                return new RiskResult(Decision.BLOCK, "模型高风险", "拒绝");
            }
            return new RiskResult(Decision.ALLOW, "通过", "正常放行");
        }
    }

    static void threeLines() {
        System.out.println("【三道防线：事前/事中/事后】");
        List<String> lines = List.of(
                "事前：风控引擎（限额/频次/模型）-> 决策 ALLOW/CHALLENGE/BLOCK",
                "事中：四道闸（工具/参数/动作/预算 44-09）+ 验签 + 状态机",
                "事后：对账（51-05）+ 审计（操作不可抵赖）+ 差异闭环");
        for (String l : lines) {
            System.out.println("  " + l);
        }
        System.out.println("  单道防线都会漏：组合冗余才构成安全（36-01 纵深防御）");
        System.out.println();
    }

    static void decisionDemo() {
        System.out.println("【风控决策分级演示】");
        RiskEngine engine = new RiskEngine();
        List<Object[]> cases2 = new ArrayList<>();
        cases2.add(new Object[]{"u1", 500_500L, 2});
        cases2.add(new Object[]{"u2", 150_000L, 2});
        cases2.add(new Object[]{"u3", 6900L, 25});
        cases2.add(new Object[]{"u4", 6900L, 3});
        for (Object[] c : cases2) {
            RiskResult r = engine.evaluate((String) c[0], (Long) c[1], (Integer) c[2]);
            System.out.printf("  user=%s 金额=%d 分 近1h=%d 次 -> %s（%s）动作：%s%n",
                    c[0], c[1], c[2], r.decision(), r.reason(), r.action());
        }
        System.out.println();
    }

    static void auditDuties() {
        System.out.println("【操作审计与内部防线】");
        List<String> duties = List.of(
                "退款/调价/改单：操作人 + 审批人 + 变更前后快照（不可抵赖）",
                "append-only 审计存储：日志不可修改删除（权限隔离）",
                "金额以 DB 为准：模型/请求参数仅参考（40-06 原则）",
                "敏感操作独立确认通道（44-09）：不信任文本确认",
                "系统账号权限最小化：支付服务账号只给所需表（36-01）");
        for (String d : duties) {
            System.out.println("  - " + d);
        }
        System.out.println("  审计不是日志附件，是法律证据 —— 存储与权限按证据标准建设");
    }

    public static void main(String[] args) {
        threeLines();
        decisionDemo();
        auditDuties();
    }
}
```

**课后练习**
1. 实现风控引擎完整版（硬限额/频次/大额增强验证/模型评分四级），写出决策矩阵测试。
2. 设计后台退款操作的审计表结构（操作人/审批人/前后快照），演示一次"无审批被拦截"。
3. 思考题：为什么资金域的审计存储要 append-only？从法律证据效力与内部作恶防线两个角度分析。

---

### 51-08 清结算与复式记账

**知识点**
- **账务的基石是复式记账**：每笔资金变动记**借贷两方**（借：应收/银行存款，贷：收入/应付）——**有借必有贷，借贷必相等**；任何时刻试算平衡（借方合计 = 贷方合计）是账务正确性的硬校验。**"钱去哪了"每一分都可追**。
- **账户体系**：用户账户、商户账户、平台收入账户、渠道清算户——**账户只是记账的维度**，余额 = 该账户分录的合计。内部户（平台收入）与外部户（用户/商户）分账簿。
- **清算与结算**：**清算**算清楚谁欠谁多少（渠道 T+1 账单 -> 应结算金额，扣除手续费）；**结算**实际资金划拨（打款给商户）。**结算依赖对账通过**（51-05 未平的差异暂停结算）。
- **分账与手续费**：平台型业务（用户付 100 -> 商户 90 + 平台 10）的分账分录——每笔交易拆多条分录，**手续费、退款、调价都影响分账**——分账规则是商户契约（46-08）。
- **账务与业务事件的原子性**：支付成功事件 -> 账务分录（51-03 同事务打包）——**账务落后于业务 = 账实不符**，日终"业务流水 vs 账务分录"再对一遍（第二层对账）。
- 易错点：单式记账（只有流水没有借贷，对不平）；分录与业务事件不同事务（丢分录）；手续费计算舍入不一致（渠道与本地差分）；退款分录方向搞反（试算失衡）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.List;

public class ClearingLedger {
    /** 分录：复式记账（借贷相等） */
    record Entry(String account, String direction, long amountCents) {
        boolean isDebit() {
            return "DEBIT".equals(direction);
        }
    }

    record Voucher(String voucherId, List<Entry> entries, String bizType) {
        boolean balanced() {
            long debit = entries.stream().filter(Entry::isDebit).mapToLong(Entry::amountCents).sum();
            long credit = entries.stream().filter(e -> !e.isDebit()).mapToLong(Entry::amountCents).sum();
            return debit == credit;
        }
    }

    static void voucherDemo() {
        System.out.println("【一笔支付的分录：用户付 100 元，手续费 0.6 元，商户入账 99.4 元】");
        List<Entry> entries = List.of(
                new Entry("渠道清算户", "DEBIT", 10000),          // 借：收到渠道资金
                new Entry("商户应付户", "CREDIT", 9940),          // 贷：应付商户
                new Entry("平台收入户", "CREDIT", 60));           // 贷：手续费收入
        Voucher v = new Voucher("V-1", entries, "PAYMENT");
        for (Entry e : entries) {
            System.out.printf("  %-6s %-8s %d 分%n", e.account(), e.isDebit() ? "借" : "贷", e.amountCents());
        }
        System.out.printf("  借方合计 %d = 贷方合计 %d -> 试算平衡：%b%n",
                entries.stream().filter(Entry::isDebit).mapToLong(Entry::amountCents).sum(),
                entries.stream().filter(e -> !e.isDebit()).mapToLong(Entry::amountCents).sum(),
                v.balanced());
        System.out.println("  10000 = 9940 + 60 —— 每一分钱都有去向");
        System.out.println();
    }

    static void refundVoucher() {
        System.out.println("【退款分录：反向冲正】");
        List<Entry> entries = List.of(
                new Entry("商户应付户", "DEBIT", 2000),           // 借：冲减应付商户
                new Entry("渠道清算户", "CREDIT", 2000));         // 贷：资金退回用户
        Voucher v = new Voucher("V-2", entries, "REFUND");
        System.out.printf("  退款 20 元：借商户应付 %d = 贷渠道清算 %d -> 平衡：%b%n",
                2000, 2000, v.balanced());
        System.out.println("  方向搞反 -> 试算失衡当场发现 —— 复式记账自带纠错检测");
        System.out.println();
    }

    static void clearingFlow() {
        System.out.println("【清算结算：从渠道账单到打款】");
        List<String> flow = List.of(
                "T+1 渠道账单 -> 对账通过（51-05）",
                "清算：应收渠道金额 - 手续费 = 应结算商户额（分账规则）",
                "结算批次生成 -> 财务复核 -> 打款指令",
                "差异未平的商户 -> 暂停结算（差额进待处理）",
                "结算完成 -> 分录核销 -> 商户余额可查");
        for (String s : flow) {
            System.out.println("  " + s);
        }
        System.out.println("  对账是结算的前置闸门：差异未平不放款 —— 资金安全顺序不可颠倒");
    }

    public static void main(String[] args) {
        voucherDemo();
        refundVoucher();
        clearingFlow();
    }
}
```

**课后练习**
1. 实现分录生成器：支付/退款/手续费三类业务生成借贷平衡分录，注入"方向搞反"验证试算失衡检测。
2. 设计商户分账规则表（费率/封顶/舍入），实现一笔 100 元交易的分账分录并与渠道账单核对。
3. 思考题：为什么复式记账"自带纠错检测"？从借贷平衡的数学约束与单式记账的对账成本对比分析。
