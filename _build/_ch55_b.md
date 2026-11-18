### 55-07 租户生命周期

**知识点**
- **租户生命周期四阶段各有工程任务**：**开通**（ provision：初始化数据/默认配置/试用期配额——自动化开通是 SaaS 的获客基础，销售手工开通的 SaaS 扩展不动）；**运行**（配额计量/升级降级/支持工单）；**升级**（套餐变更：配额即时生效、独立库租户的资源扩容 47-08）；**退租**（数据导出交付 -> 保留期 -> 彻底清除，55-04 删除权）。
- **开通自动化（provisioning）的原子性**：开通涉及多步（建租户记录/初始化配置/发欢迎通知/计费开户）——**任一步失败要可重试且幂等**（49 章状态机：开通中/已开通/开通失败），**开通失败留"半开通"租户是常见事故**（能登录但没数据）。
- **退租的数据处置是合规硬要求**：导出交付（租户要带走数据）-> **保留期**（如 30 天可恢复）-> 彻底清除（业务表/缓存/ES/备份标记/日志脱敏 55-04）——**清除要有证据**（清除核验清单，GDPR 删除权）。
- **试用转化**：试用期配额与到期策略（到期降级而不是直接删数据——转化留余地）。
- 易错点：开通多步无状态机（半开通）；退租即删（合规与纠纷风险）；升级配额不即时（支付了还受限，客诉）；生命周期事件无审计（商务与技术口径不一致）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.List;

public class TenantLifecycle {
    enum State {
        PROVISIONING, ACTIVE, SUSPENDED, EXPORTING, PURGED
    }

    static String provision(List<String> steps, java.util.function.Predicate<String> failAt) {
        List<String> done = new ArrayList<>();
        for (String step : steps) {
            if (failAt.test(step)) {
                System.out.printf("  步骤 %s 失败 -> 状态=开通失败（可重试，幂等）%n    已完成：%s%n",
                        step, done);
                return "FAILED";
            }
            done.add(step);
        }
        System.out.printf("  开通完成：%s%n", done);
        return "ACTIVE";
    }

    public static void main(String[] args) {
        List<String> steps = List.of("创建租户记录", "初始化配置", "初始化数据", "计费开户");
        provision(steps, s -> false);
        System.out.println();
        provision(steps, s -> s.equals("计费开户"));       // 模拟最后一步失败
        System.out.println("  半开通的危害：能登录但没数据/没计费 —— 状态机+幂等重试是解");
    }
}
```

**课后练习**
1. 实现租户开通状态机（幂等步骤 + 失败重试 + 半开通检测），注入两处失败验证恢复。
2. 实现退租三段流程（导出/保留期/清除）与清除核验清单，输出合规证据。
3. 思考题：为什么退租要"保留期"而不是立即清除？从纠纷处理、误操作恢复、合规保留三方面分析。

---

### 55-08 噪声邻居与性能隔离

**知识点**
- **噪声邻居（noisy neighbor）是共享架构的头号风险**：一个大租户的重任务（批量导出/报表/压测自己）耗尽共享资源（DB CPU/连接池/带宽），**所有租户一起变慢**——共享表/独立库共享实例都有此风险，独立部署天然免疫。
- **防护三件套**：① **配额限流**（租户级 API 配额 + 批处理并发上限，55-06 配置上限）；② **资源分组**（批处理任务进独立线程池/独立节点池，47-08 隔离）；③ **实时检测与自动处置**（租户级指标监控 -> 异常租户自动限流/隔离，42 章告警联动）。
- **检测信号**：按租户维度的 DB 时间、API 耗时、队列占用——**单租户占用共享资源 >X% 即告警**（46-10 的租户维度）；识别后的动作：限流 -> 降级 -> 沟通（通知租户升级独立资源）。
- **大租户的升级路径**：噪声邻居反复出现 -> 引导升级独立库/独立部署（商业化与技术联动）——**架构方案成为售卖选项**。
- 易错点：无租户维度监控（发现问题但不知道是谁）；限流一刀切（影响正常流量）；批处理与在线同池（55-01 的共享表风险）；大租户议价后无限资源（议价要带技术约束）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class NoisyNeighborDemo {
    record TenantUsage(String tenant, double dbTimePct, long apiCalls) {
    }

    static List<String> detect(List<TenantUsage> usage, double alarmPct) {
        List<String> actions = new ArrayList<>();
        for (TenantUsage u : usage) {
            if (u.dbTimePct() > alarmPct) {
                actions.add("[" + u.tenant() + "] DB 时间占比 "
                        + String.format("%.0f%%", u.dbTimePct() * 100)
                        + " -> 自动限流 + 通知升级独立资源");
            }
        }
        if (actions.isEmpty()) {
            actions.add("无噪声邻居");
        }
        return actions;
    }

    public static void main(String[] args) {
        List<TenantUsage> usage = List.of(
                new TenantUsage("t-small-1", 0.02, 5000),
                new TenantUsage("t-big-7", 0.55, 120000),      // 大租户吃掉一半 DB
                new TenantUsage("t-small-2", 0.03, 6000));
        System.out.println(detect(usage, 0.3));
        System.out.println("  其余租户的 P99 同时劣化 —— 检测信号就是按租户分桶的 DB 时间");
    }
}
```

**课后练习**
1. 实现噪声邻居检测器（租户维度资源统计 + 阈值动作），模拟大租户压测场景的自动限流。
2. 实现"批处理资源分组"：导出/报表任务独立线程池 + 租户级并发上限，对比混池时的 P99。
3. 思考题：为什么噪声邻居的最终解是"商业化升级"而非纯技术？从成本转嫁与资源隔离的投入产出分析。

---

### 55-09 多租户下的可观测

**知识点**
- **可观测的租户维度化**（42 章体系的多租户特化）：**指标**带 tenant 标签（基数评估！大 SaaS 万级租户不能全量打标签——**用 TopN 租户标签 + 其余聚合**，42-04 基数红线）；**日志**带 tenant_id 字段（42-02 检索）；**trace** 属性带租户（42-07）。
- **租户级 SLA 是 SaaS 的产品承诺**：每租户的可用性/延迟分位——**聚合指标掩盖单个大客户的劣化**（平均 50ms 里有个租户 2s）——**TopN 客户单独看板**（46-10 服务化）。
- **告警的租户维度**：单租户错误率异常（可能该租户的配置/数据问题）vs 全局错误率（平台问题）——**两级告警定位**（42-06 分级）；按租户的告警通知路由（企业版客户有专属通道，42 章告警对接）。
- **成本与配额的可观测**：租户资源消耗报表（计费依据 + 容量规划 55-05）——**观测即商业数据**。
- 易错点：全量租户打指标标签（基数爆炸，42-04）；告警不分平台级/租户级（值班被单租户问题淹没）；租户级日志成本失控（42-03 分层存储按租户分级——企业版保留长）。

**示例代码**
```java
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TenantObservability {
    static void metricStrategy() {
        System.out.println("【指标标签的租户策略：TopN + 聚合】");
        record Tenant(String id, long qps) {
        }
        List<Tenant> tenants = List.of(
                new Tenant("t-big-1", 8000), new Tenant("t-big-2", 5000),
                new Tenant("t-long-tail-1", 30), new Tenant("t-long-tail-2", 20));
        int topN = 2;
        long longTailQps = 0;
        for (Tenant t : tenants) {
            if (t.qps() >= 3000) {
                System.out.printf("  指标带标签 tenant=%s（TopN 独立序列）%n", t.id());
            } else {
                longTailQps += t.qps();
            }
        }
        System.out.printf("  长尾租户聚合为一个序列（qps=%d），基数受控%n", longTailQps);
        System.out.println("  排障时：长尾租户用日志/trace 按 tenant_id 查，不用指标（42-03）");
        System.out.println();
    }

    static void twoLevelAlert() {
        Map<String, String> alerts = new LinkedHashMap<>();
        alerts.put("平台级", "全局错误率 > 1% -> 全员告警（影响所有租户）");
        alerts.put("租户级", "单租户错误率 > 5% -> 租户支持队列（专属通道）");
        alerts.forEach((k, v) -> System.out.printf("  %-4s %s%n", k, v));
        System.out.println("  区分价值：单租户异常通常是该租户配置/数据问题，不是平台事故");
    }

    public static void main(String[] args) {
        metricStrategy();
        twoLevelAlert();
    }
}
```

**课后练习**
1. 实现租户指标策略（TopN 打标签 + 长尾聚合），验证基数受控与 TopN 排障可用。
2. 实现两级告警路由（平台级 -> 值班；租户级 -> 客户支持队列），构造两类事故验证路由。
3. 思考题：为什么"聚合指标掩盖单个大客户劣化"？构造一个平均正常但大客户 P99 爆炸的数据集，说明 TopN 看板的必要性。

---

### 55-10 SaaS 安全与合规

**知识点**
- **SaaS 合规的特殊面**：**数据主权**（租户数据的存储地/跨境传输约束——出海 SaaS 的分地域部署）、**删除权**（55-07 退租清除证据）、**数据处理协议（DPA）**（与租户的合同约定处理范围——AI 功能用租户数据训练要明示，40 章合规的 SaaS 版）。
- **租户令牌与 API 安全**：租户 API Key（scope 限定，44-07 MCP scope 同源）+ 轮换机制 + 用量可观测——**API Key 泄露是 SaaS 高频事故**（监控异常调用模式 51-07 风控）。
- **备份隔离与恢复演练**：共享备份的恢复按租户解析（55-04）；**备份自身的访问控制**（备份泄露=全租户泄露）；恢复演练验证 RPO/RTO（47-09）。
- **员工访问租户数据**：支持工单需要看租户数据——**最小化 + 审批 + 审计 + 脱敏视图**（51-07 审计的 SaaS 版）；**"租户授权查看"**模式（租户主动授权支持人员临时代理访问）是最佳实践。
- 易错点：多地域部署的数据同步违反主权；API Key 永不轮换；员工直接查库无审计；DPA 与实际数据处理不一致（法律风险）。
- **合规清单的落地形态**：合规要求 -> 技术控制（加密/审计/隔离）-> 检查项进 CI 与运营流程（49-10 适应度函数）。

**示例代码**
```java
import java.util.LinkedHashMap;
import java.util.Map;

public class SaaSCompliance {
    static void supportAccess() {
        System.out.println("【员工访问租户数据：授权代理模式】");
        Map<String, String> flow = new LinkedHashMap<>();
        flow.put("1", "租户管理员在产品内发起授权（选范围+时长）");
        flow.put("2", "支持人员获得临时代理令牌（scope 限定）");
        flow.put("3", "访问全程审计：谁/何时/看了什么");
        flow.put("4", "授权到期自动回收，租户可随时撤销");
        flow.forEach((k, v) -> System.out.println("  " + k + ". " + v));
        System.out.println();
        System.out.println("  对照：支持人员直接查库 —— 无租户同意、无审计、越权风险");
    }

    static void dataResidency() {
        System.out.println("【数据主权：分地域部署】");
        String[][] rows = {
                {"EU 租户", "EU 区域部署 + 数据不出域", "GDPR"},
                {"CN 租户", "CN 区域部署", "数据安全法/个保法"},
                {"GLOBAL", "按租户归属路由到对应区域", "注册时确定归属地"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-8s %-30s 合规=%s%n", r[0], r[1], r[2]);
        }
        System.out.println("  架构含义：47-09 多地域部署在 SaaS 里首先是合规要求");
    }

    public static void main(String[] args) {
        supportAccess();
        dataResidency();
    }
}
```

**课后练习**
1. 实现授权代理访问（令牌 scope + 审计 + 到期回收），演示支持工单的合规访问流。
2. 设计分地域部署方案（租户归属路由/数据不出域/跨区聚合的合规处理），写出 ADR。
3. 思考题：为什么 SaaS 的 AI 功能（如用租户数据训练模型）需要显式租户同意？从 DPA 约束与信任关系分析 44 章合规的 SaaS 特化。

---

### 55-11 综合案例：多租户 CMS 落地

**知识点**
- **把全章收束成一个可交付的多租户产品**：多租户内容管理系统（CMS）——租户开通自动化、共享表+tenant_id（中小租户）+ 大租户独立库、租户上下文贯穿、配额计费、租户级配置与功能开关、噪声邻居防护、租户维度观测、合规（导出/删除/授权访问）。
- **落地检查单**：切面七项全覆盖（55-01）、隔离方案分级（55-02）、上下文全链路（55-03）、双键校验（55-04）、配额对账（55-05）、配置校验（55-06）、生命周期状态机（55-07）、噪声检测（55-08）、两级告警（55-09）、合规清单（55-10）。
- **上线前的渗透自测**：跨租户越权用例集（改 tenantId/伪造头/枚举资源）、配额绕过、删除残留核验——**SaaS 渗透测试的核心就是跨租户**（49-07 ACL 测试的 SaaS 版）。
- 易错点：检查单只覆盖技术不覆盖商务流程（开通/退租与计费联动）；演示租户与生产租户同池；租户级配置与平台配置冲突无仲裁记录。
- **SaaS 工程师的成长线**：从"实现多租户功能"到"设计租户分级与隔离策略"到"治理噪声邻居与合规"——**多租户是架构能力与商业理解的交汇点**（52 章总纲的 SaaS 注脚）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.List;

public class TenantCmsCase {
    record Gate(String area, String check, String evidence) {
    }

    static void launchGates() {
        System.out.println("【多租户 CMS 上线门禁】");
        List<Gate> gates = List.of(
                new Gate("切面覆盖", "七切面租户维度齐全", "清单核验（55-01）"),
                new Gate("数据隔离", "拦截器注入 + 双键校验 + 架构测试", "越权用例集全绿（55-04）"),
                new Gate("上下文", "异步/定时/全链路传播", "并发跨租户测试（55-03）"),
                new Gate("配额计费", "计量准确 + 账单对账", "并发计量证据（55-05）"),
                new Gate("噪声防护", "租户级检测 + 自动限流", "大租户压测记录（55-08）"),
                new Gate("合规", "导出审批 + 删除核验 + 授权访问", "合规证据包（55-10）"));
        for (Gate g : gates) {
            System.out.printf("  %-8s %-28s 证据：%s%n", g.area(), g.check(), g.evidence());
        }
        System.out.println();
        System.out.println("  渗透自测：跨租户越权用例集全部 404/拒绝 —— SaaS 上线的底线");
    }

    public static void main(String[] args) {
        launchGates();
    }
}
```

**课后练习**
1. 为毕业设计实现一个迷你多租户 CMS（至少覆盖 55-01~55-06 六课的能力），组织一次上线门禁评审。
2. 编写跨租户渗透测试用例集（≥10 条），全部执行并输出报告。
3. 思考题：为什么多租户架构"牵一发而动全身"？从本章门禁清单的面积反推为什么 55-01 说它是"贯穿性架构约束"。
