### 51-09 分布式事务在支付

**知识点**
- **支付域内的一致性分层**：**强一致**（支付单状态+账务分录+outbox 同库同事务，51-03）；**最终一致**（跨域：订单/履约/积分，事件驱动 49-03）；**对账兜底**（51-05 收口）——39-09 的方案光谱在资金域的取舍：**XA 不用（锁与吞吐）、TCC 用于资金冻结场景、本地消息表是主力**。
- **TCC 在支付的真实场景**：**余额冻结**（账户系统：Try 冻结额度、Confirm 扣减、Cancel 解冻）——预扣资源的场景 TCC 语义最自然；**空回滚与悬挂的防护**（44 章同款三态纪律）在资金上绝对不能省。
- **本地消息表的支付版细节**：outbox 与支付单同库（同事务）-> 投递 PaymentSucceeded；**消息按订单号分区保序**（39-08）；**消费幂等键 = 事件 ID**；投递失败指数退避 + 死信告警——**"事件丢了"在支付域等于"订单永远待支付"**，可靠级别最高。
- **反向补偿**：支付成功但下游履约失败——**支付不回滚**（钱已到账），下游走补偿（重发履约/人工）——**"钱的事实不可逆，业务流程可补偿"**是支付域分布式事务的核心认知。
- **一致性监控**：业务流水 vs 账务分录日终核对（51-08 第二层对账）、outbox 积压告警、事件消费 lag——**一致性指标进告警**（42 章方法论）。
- 易错点：跨域用同步调用串联（一条链断全链断，49-08 分布式单体）；消息无分区保序（状态乱序回退）；消费幂等缺失（重复履约/重复发货）；把"支付成功"事件的消费失败当可丢（必须死信+人工）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PaymentTransactions {
    /** TCC：账户余额冻结（资金域最自然的 TCC 场景） */
    static class AccountTcc {
        final Map<String, long[]> accounts = new ConcurrentHashMap<>();   // user -> {可用, 冻结}

        AccountTcc() {
            accounts.put("u1", new long[]{100_000, 0});
        }

        boolean tryFreeze(String user, long amount) {
            long[] a = accounts.get(user);
            if (a[0] < amount) {
                return false;
            }
            a[0] -= amount;
            a[1] += amount;
            System.out.printf("  Try：冻结 %d 分（可用 %d / 冻结 %d）%n", amount, a[0], a[1]);
            return true;
        }

        void confirm(String user, long amount) {
            long[] a = accounts.get(user);
            a[1] -= amount;
            System.out.printf("  Confirm：扣除冻结 %d 分（冻结剩 %d）%n", amount, a[1]);
        }

        void cancel(String user, long amount) {
            long[] a = accounts.get(user);
            a[1] -= amount;
            a[0] += amount;
            System.out.printf("  Cancel：解冻 %d 分（可用恢复 %d）%n", amount, a[0]);
        }
    }

    static void tccFlow() {
        System.out.println("【TCC 三阶段与空回滚防护】");
        AccountTcc tcc = new AccountTcc();
        boolean ok = tcc.tryFreeze("u1", 6900);
        if (ok) {
            tcc.confirm("u1", 6900);
        } else {
            tcc.cancel("u1", 6900);
        }
        System.out.println("  空回滚场景：Try 网络丢失，Cancel 先到 -> 记空标记，Cancel 成功返回");
        System.out.println("  悬挂场景：Cancel 之后 Try 才到 -> 检查空标记直接拒绝");
        System.out.println("  幂等键 = 事务 xid（44-09 同款），三接口全部幂等");
        System.out.println();
    }

    static void outboxDelivery() {
        System.out.println("【outbox 投递与分区保序】");
        record OutboxEvent(String eventId, String orderId, String type) {
        }
        List<OutboxEvent> outbox = List.of(
                new OutboxEvent("e1", "9001", "PaymentSucceeded"),
                new OutboxEvent("e2", "9001", "RefundSucceeded"),
                new OutboxEvent("e3", "9002", "PaymentSucceeded"));
        System.out.println("  投递路由：按 orderId 分区（同一订单事件保序）");
        Map<String, List<String>> partitioned = new java.util.LinkedHashMap<>();
        for (OutboxEvent e : outbox) {
            partitioned.computeIfAbsent(e.orderId(), k -> new ArrayList<>()).add(e.type());
        }
        for (Map.Entry<String, List<String>> e : partitioned.entrySet()) {
            System.out.printf("  分区[%s] -> %s%n", e.getKey(), e.getValue());
        }
        System.out.println("  消费幂等：eventId 去重表（51-03 三层幂等的跨域层）");
        System.out.println("  监控：outbox 积压 >0 持续 5min 告警（42 章）");
        System.out.println();
    }

    static void compensatationPrinciple() {
        System.out.println("【反向补偿：钱不可逆，流程可补偿】");
        List<String> cases = List.of(
                "支付成功但履约失败 -> 钱不退（支付事实成立），履约重试/人工补发",
                "支付成功但用户申请退款 -> 走退款链路（51-06），不是回滚支付",
                "积分发放失败 -> 补偿队列重发，不影响支付状态",
                "原则：支付状态机是事实锚点，其他域围绕它补偿（49-03）");
        for (String c : cases) {
            System.out.println("  " + c);
        }
        System.out.println("  对照错误做法：履约失败把支付状态回滚 FAILED -> 账实不符 + 客诉");
    }

    public static void main(String[] args) {
        tccFlow();
        outboxDelivery();
        compensatationPrinciple();
    }
}
```

**课后练习**
1. 实现 AccountTcc 完整版（空回滚标记 + 悬挂拒绝 + 幂等），并发 Try/Cancel 竞态验证资金状态正确。
2. 实现 outbox 投递器（分区保序 + 幂等消费 + 死信），注入"消费失败"验证重试与告警路径。
3. 思考题：为什么"支付成功但履约失败"不能回滚支付？从资金事实不可逆与补偿机制的设计哲学分析。

---

### 51-10 支付高可用与降级

**知识点**
- **支付不可用的代价是 GMV 直接损失**——支付可用性目标通常是 99.99%（全年 53 分钟）。**单渠道故障 = 降级路由；渠道全故障 = 收银台降级（引导用户稍后/线下）；自己的支付核心故障 = 最严重事故**——每一层都有预案。
- **渠道路由的容灾设计**：主备渠道（主渠道熔断自动切备）、渠道配额与限额监控（渠道侧限额临近告警）、**渠道健康度实时看板**（47-05 拓扑思想）——**渠道故障演练**（47-10：模拟主渠道全挂，验证自动切换时长）。
- **支付核心的降级阶梯**：L1 正常 -> L2 关闭非核心功能（营销支付方式/分期）保核心 -> L3 排队限流（收银台提示排队）-> L4 线下预案（二维码轮询/延长订单有效期）。**预案要业务可执行**（值班手册粒度）。
- **核心链路的资源隔离**：支付核心的 DB/缓存与营销等系统隔离（39/47 章资源隔离思想）——**大促营销活动不能挤垮支付**；接口级限流（秒杀支付并发保护）。
- **演练清单**：渠道熔断切换、回调风暴、DB 主从切换（39-04）、对账文件缺失、时钟异常——**支付演练频率高于普通系统**（季度 -> 月度）。
- 易错点：只有单渠道（无容灾，渠道挂=全站停付）；降级预案纸面化（未演练）；营销活动共享支付资源（挤兑）；值班手册不含支付特有动作（查单/差异单处理流程）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.List;

public class PaymentHa {
    record Ladder(String level, String trigger, String action) {
    }

    static void degradeLadder() {
        System.out.println("【支付降级阶梯】");
        List<Ladder> ladder = List.of(
                new Ladder("L1 正常", "渠道健康率 > 99.9%", "全功能"),
                new Ladder("L2 精简", "渠道成功率 < 99%（持续 5min）", "关闭分期/营销支付方式，保核心"),
                new Ladder("L3 排队", "核心渠道全降级", "收银台排队提示，限并发进支付"),
                new Ladder("L4 线下预案", "支付核心故障", "延长订单有效期 + 客服话术 + 线下收款"));
        for (Ladder l : ladder) {
            System.out.printf("  %-10s 触发：%-30s %s%n", l.level(), l.trigger(), l.action());
        }
        System.out.println("  每级都有自动触发判据 + 值班手册动作（44-08 阶梯的资金版）");
        System.out.println();
    }

    static void channelFailover() {
        System.out.println("【渠道故障切换演练：主渠道全挂】");
        List<String> timeline = List.of(
                "T+0    主渠道（微信）成功率跌至 95% -> 熔断打开（47-03）",
                "T+10s  路由自动切备用渠道（支付宝）-> 用户无感",
                "T+1min 在途 PENDING 单 -> 查询轮询加速（51-02）",
                "T+5min  备用渠道限额告警 -> 提升限额申请/第三渠道预热",
                "T+30min 主渠道恢复 -> 半开探测 -> 逐步放量回切",
                "复盘：切换全自动，用户端仅见支付方式排序变化");
        for (String s : timeline) {
            System.out.println("  " + s);
        }
        System.out.println("  验收指标：切换时间 < 1min，无用户可感知中断");
        System.out.println();
    }

    static void isolationRules() {
        System.out.println("【资源隔离：营销不能挤垮支付】");
        List<String> rules = List.of(
                "DB 隔离：支付核心独立实例（或独立 schema+资源组）",
                "缓存隔离：支付幂等键缓存独立实例",
                "限流：支付接口独立限流阈值，营销接口单独配额",
                "部署：支付核心独立集群（或独立命名空间+节点池亲和）",
                "依赖：支付不依赖营销/积分服务可用性（事件解耦 49-03）");
        for (String r : rules) {
            System.out.println("  - " + r);
        }
        System.out.println("  事故对照：大促营销活动共享 DB -> 连接池耗尽 -> 支付失败 = GMV 直接损失");
        System.out.println();
    }

    static void drillList() {
        System.out.println("【支付月度演练清单】");
        List<String> drills = List.of(
                "渠道熔断切换（主渠道全挂 -> 备用）",
                "回调风暴（1 万积压回调齐发）-> 去重与限流",
                "DB 主从切换 + 在途支付单恢复（39-04）",
                "对账文件缺失/格式变更 -> 解析降级与人工通道",
                "查询轮询全量触发（渠道查询接口压测）",
                "差异单处理 SLA 抽查（51-05）");
        for (String d : drills) {
            System.out.println("  [ ] " + d);
        }
        System.out.println("  支付演练频率：月度（普通系统季度）—— 资金系统的特殊纪律");
    }

    public static void main(String[] args) {
        degradeLadder();
        channelFailover();
        isolationRules();
        drillList();
    }
}
```

**课后练习**
1. 实现渠道路由器（熔断三态 + 成功率加权 + 自动切换），注入主渠道故障验证切换时间与用户无感。
2. 编写支付值班手册的降级章节（L2~L4 的触发判据、执行动作、恢复条件），并桌面推演一次。
3. 思考题：为什么支付系统演练频率要高于普通系统？从资损不可逆、渠道依赖外部性、故障发现窗口三个角度分析。

---

### 51-11 综合案例：一笔支付的端到端时序

**知识点**
- **把全章串成一笔支付的完整生命**：用户下单 -> 收银台 -> 支付单创建（幂等）-> 渠道下单（路由+签名）-> 用户支付 -> 渠道回调（验签+幂等+状态机）-> outbox 事件 -> 订单推进/账务分录/履约 -> 对账核销 ->（可选）退款。**51 章每一课在这条时序上各就各位**。
- **时序中的每一跳都有"失败分支"**：渠道下单失败（路由切备）、用户中途放弃（支付单超时关闭）、回调丢失（查询轮询）、重复回调（幂等去重）、退款申请（可退校验）——**主流程 8 步，失败分支 12 个**，每个分支都有明确处置。
- **验收清单**：幂等三键、状态机全覆盖、验签全通过、对账零差异、演练全达标——**支付系统上线前的最后关卡**（41 章发布工程的资金特化）。
- **复盘文化**：每笔资金差异单都是一次微型复盘（42-11），根因分类进改进项；**支付系统的成熟度由它处理过的异常种类决定**。
- 易错点：只测主流程（失败分支无测试覆盖）；时序图与代码漂移（文档过时）；新渠道接入无全分支用例（异常分支漏处理）；支付链路无 trace_id 贯通（排障翻多个系统日志）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PaymentCase {
    record Step(int no, String actor, String action, String failBranch) {
    }

    static void happyPath() {
        System.out.println("【一笔成功支付的主流程】");
        List<Step> steps = List.of(
                new Step(1, "用户/订单域", "创建订单（OrderCreated 事件）", "-"),
                new Step(2, "支付域", "收银台 -> 创建支付单（幂等键 orderId）", "重复请求返回同一单"),
                new Step(3, "支付域", "渠道路由 -> 渠道下单（签名）", "失败切备用渠道"),
                new Step(4, "用户", "完成支付（输密码/人脸）", "中途放弃 -> 超时关单"),
                new Step(5, "渠道", "异步回调（验签）", "丢失 -> 查询轮询兜底"),
                new Step(6, "支付域", "状态机 PAYING->SUCCESS + 账务分录 + outbox", "重复回调 -> 幂等去重"),
                new Step(7, "下游域", "订单推进/履约/积分（事件消费）", "消费失败 -> 重试+死信"),
                new Step(8, "支付域", "T+1 对账核销 -> 清结算", "差异 -> 差异单闭环"));
        for (Step s : steps) {
            System.out.printf("  步骤%d [%s] %s%n    失败分支：%s%n",
                    s.no(), s.actor(), s.action(), s.failBranch());
        }
        System.out.println("  主流程 8 步，失败分支 12+ —— 每个分支都有测试与处置");
        System.out.println();
    }

    static void incidentLedger() {
        System.out.println("【异常事件台账：系统的成熟度由异常种类决定】");
        Map<String, String> ledger = new LinkedHashMap<>();
        ledger.put("长款（回调丢失）", "查询轮询兜底 -> 自动补处理 -> 复核");
        ledger.put("已关单但有款", "差异单 -> 自动原路退款 + 告警");
        ledger.put("渠道 PENDING 悬挂", "轮询用尽 -> 差异单人工");
        ledger.put("重复退款申请", "聚合可退校验拦截");
        ledger.put("账务与流水不平", "第二层对账 -> 定位丢分录事务");
        for (Map.Entry<String, String> e : ledger.entrySet()) {
            System.out.printf("  %-14s %s%n", e.getKey(), e.getValue());
        }
        System.out.println("  每类异常：发现渠道 -> 处置流程 -> 根因分类 -> 改进项（42-11）");
        System.out.println();
    }

    static void launchGate() {
        System.out.println("【支付上线验收门】");
        List<String> gates = List.of(
                "幂等：三层幂等键压测（并发 1000 重复请求，零重复扣款）",
                "状态机：全转移路径测试 + 竞态并发测试",
                "安全：验签全覆盖 + 密钥管理检查 + 审计抽查",
                "对账：三类差异单全走一遍闭环",
                "演练：渠道切换/回调风暴/DB 切换全达标",
                "观测：trace 全链贯通 + 支付 SLI 看板 + 告警就绪");
        for (String g : gates) {
            System.out.println("  [ ] " + g);
        }
        System.out.println("  全过才放量：支付系统没有“先上线再说”");
        System.out.println("  本章收官：36 安全 + 39 一致性 + 41 发布 + 42 观测 + 49 建模在此汇成资金工程");
    }

    public static void main(String[] args) {
        happyPath();
        incidentLedger();
        launchGate();
    }
}
```

**课后练习**
1. 端到端实现迷你支付链路（内存模拟）：建单幂等 -> 渠道下单 -> 回调 -> 状态机 -> outbox -> 对账，覆盖主流程与 3 个失败分支。
2. 绘制你这笔支付的全时序图（含失败分支），标注每跳的幂等键与一致性级别。
3. 思考题：为什么"支付系统的成熟度由异常种类决定"？从主流程同质化与异常处理的差异积累两个角度分析。
