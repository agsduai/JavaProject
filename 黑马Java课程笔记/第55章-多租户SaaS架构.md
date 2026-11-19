# 第55章 多租户 SaaS 架构

一套系统服务多家客户、按订阅收费——多租户不是加一个字段，而是贯穿数据、缓存、消息、日志、监控、限流、配置的**架构级约束**，漏一处就是跨租户事故。本章按「SaaS 总览与七切面 → 隔离三方案 → 租户上下文 → 数据安全边界 → 配额计费 → 配置分层 → 生命周期 → 噪声邻居 → 租户可观测 → 合规清单 → 多租户 CMS 案例」的顺序讲。所有示例仍是纯 JDK：无租户查询拦截、双键越权校验、配额原子计量、配置四层叠加、噪声邻居检测全部真实运行。

### 55-01 SaaS 与多租户总览

**知识点**
- **SaaS 的商业本质：一套系统服务多家客户，按订阅收费**——与私有化部署（一套系统一个客户）相对。多租户（multi-tenancy）是 SaaS 的架构前提：**同一套代码与基础设施服务多个租户（tenant），租户间数据与体验严格隔离**。
- **隔离的三个层级（核心决策）**：**独立部署**（每租户一套完整系统——隔离最强、成本最高，适合大客户/强合规）、**独立库共享应用**（应用共享，库按租户分——折中）、**共享库共享表**（表带 tenant_id 列——成本最低、密度最高，绝大多数租户的选择）。**按租户分级混合使用**（大客户独立部署、中小客户共享）是成熟 SaaS 的标准形态。
- **多租户引入的系统性复杂度**：**一切都要带租户维度**——数据（tenant_id）、缓存键、消息路由、日志标签、监控指标、限流配额、配置分层——**漏一处就是跨租户事故**（数据泄露是 SaaS 最严重的故障）。
- **与 45-47 章的关系**：多租户改变了容量规划（噪声邻居）、可观测（租户维度）、安全（租户边界）——**是贯穿性的架构约束而非一个功能**。
- **选型的关键问题**：客户对隔离的合规要求（金融/医疗强独立）、租户规模分布（是否存在超级大租户）、成本敏感度、团队运维能力——**52-05 权衡框架的 SaaS 版**。
- 易错点：先共享后拆（改造痛苦，一开始就要定隔离策略）；租户维度漏埋（缓存/日志/监控任一漏了都是事故源）；把多租户当"加个字段"（是贯穿性架构约束）；所有租户一刀切独立部署（成本爆炸）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.List;

public class SaaSOverview {
    record IsolationModel(String mode, String dataLayout, String cost, String fit) {
    }

    static void models() {
        System.out.println("【租户隔离三方案】");
        List<IsolationModel> models = List.of(
                new IsolationModel("独立部署", "每租户独立系统+库", "最高（×N 倍资源）",
                        "大客户/强合规/定制深"),
                new IsolationModel("独立库", "共享应用，库按租户分", "中（连接池×N）",
                        "中等客户、数据量差异大"),
                new IsolationModel("共享表", "表带 tenant_id 列", "最低（密度最高）",
                        "绝大多数中小租户"));
        for (IsolationModel m : models) {
            System.out.printf("  %-6s 数据=%-20s 成本=%-16s 适合=%s%n",
                    m.mode(), m.dataLayout(), m.cost(), m.fit());
        }
        System.out.println("  成熟 SaaS：按租户分级混合（52-05 权衡）");
        System.out.println();
    }

    static void tenantDimensions() {
        System.out.println("【必须带租户维度的系统切面】");
        String[][] dims = {
                {"数据", "所有业务表 tenant_id + 索引首列"},
                {"缓存", "缓存键前缀 tenant:id（25 章）"},
                {"消息", "MQ tag/属性带租户，消费按租户路由"},
                {"日志", "结构化字段 tenant_id（42-02 检索维度）"},
                {"监控", "指标带 tenant 标签（42-04 基数评估！）"},
                {"限流", "配额按租户（44-08）"},
                {"配置", "租户级配置分层（55-06）"},
        };
        for (String[] d : dims) {
            System.out.printf("  %-4s %s%n", d[0], d[1]);
        }
        System.out.println("  口诀：任何一个切面漏租户维度 = 潜在跨租户事故");
    }

    public static void main(String[] args) {
        models();
        tenantDimensions();
    }
}
```

**课后练习**
1. 为一个假想 SaaS 产品做租户分级设计：定义大/中/小租户的隔离方案与成本占比。
2. 对照七切面清单审查一个（或假想）系统，列出缺失租户维度的切面与后果。
3. 思考题：为什么"先共享后拆"是痛苦路线？从 tenant_id 后补（回填历史数据+改造全表索引）的成本分析。

---

### 55-02 租户隔离三方案

**知识点**
- **共享表方案（默认）的工程要点**：**所有业务表首列 tenant_id + 联合索引以 tenant_id 打头**（`(tenant_id, ...)`，否则跨租户扫描）；唯一约束要含 tenant_id（不同租户允许同名）；所有查询强制带租户条件（55-03 的拦截器自动化，不能靠自觉）。
- **独立库方案的管理成本**：连接池 × 租户数（39/51 章连接数爆炸问题的 SaaS 版——**连接池按租户懒加载 + 上限治理**）；schema 变更 × N（迁移脚本循环执行，进度可观测 46-09）；**跨租户聚合查询**（运营报表）需要单独的数据汇聚通道（CDC 到数仓 46 章）。
- **独立部署方案的应用治理**：每租户独立发布流水线（41 章）或统一镜像分实例配置；**配置与代码版本漂移治理**（47-09 跨集群漂移的同款问题）；成本核算天然清晰（租户自付）。
- **方案的迁移路径**：共享表 -> 独立库（大租户搬出）：按 tenant_id 迁移数据（39-11 双写三阶段复用）——**隔离方案不是终身绑定**，但迁移要趁早规划。
- **数据层选型矩阵**：共享表适合 MySQL 单体或分片（tenant_id 进分片键考量）；独立库适合每个库再分片（大租户 39 章方案）。
- 易错点：索引不带 tenant_id（查询退化全表）；唯一约束漏租户（租户 A 阻止租户 B 建同名）；共享表的 tenant_id 可空（空=跨租户数据，必须 NOT NULL）；独立库连接池无上限（租户数增长拖垮应用）。

**示例代码**
```java
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TenantIsolation {
    /** 共享表：租户条件强制注入（拦截器思想） */
    record Query(String table, String tenantId, Map<String, String> conditions) {
    }

    static String buildSql(Query q) {
        if (q.tenantId() == null || q.tenantId().isBlank()) {
            throw new IllegalStateException("拒绝无租户条件的查询（防跨租户泄露）");
        }
        StringBuilder sb = new StringBuilder("SELECT * FROM ").append(q.table())
                .append(" WHERE tenant_id = '").append(q.tenantId()).append("'");
        q.conditions().forEach((k, v) ->
                sb.append(" AND ").append(k).append(" = '").append(v).append("'"));
        return sb.toString();
    }

    public static void main(String[] args) {
        System.out.println("  " + buildSql(new Query("orders", "t-100",
                Map.of("status", "PAID"))));
        try {
            buildSql(new Query("orders", "", Map.of()));
        } catch (IllegalStateException e) {
            System.out.println("  无租户查询被拦截：" + e.getMessage());
        }
        System.out.println();
        System.out.println("  索引设计：INDEX (tenant_id, status) —— 租户列必须打头（39-02）");
    }
}
```

**课后练习**
1. 实现三方案的内存模拟：共享表（带租户查询构建）、独立库（按租户路由 Map）、独立部署（配置模板），各演示一个查询。
2. 设计独立库方案的连接池治理（懒加载 + 上限 + LRU 淘汰），模拟 100 租户场景的资源曲线。
3. 思考题：为什么共享表的唯一约束必须含 tenant_id？构造两个租户同名数据的冲突用例说明。

---

### 55-03 租户上下文与路由

**知识点**
- **租户上下文（TenantContext）的传播链**：入口解析租户（子域名/JWT claim/请求头）-> ThreadLocal/Context 存储 -> 全链路透传（线程池要传递——50 章 ThreadLocal 审计在多租户的场景化）-> 落库自动注入（MyBatis 拦截器改写 SQL，55-02）。
- **自动注入 vs 手动传参**：**自动注入（SQL 拦截器改写 WHERE tenant_id）能防止"漏租户条件"的人为事故**——但魔法化要配套：拦截器日志可观测、绕过机制显式声明（管理后台跨租户查询用显式 API）。
- **异步与定时任务的租户传播**：线程池任务丢失上下文（50-05 装饰器传递）；定时任务无请求上下文——**显式遍历租户执行**（按租户循环），且要配额限速（大租户任务不让小租户饿死）。
- **MQ 的租户路由**：消息带租户属性 -> 消费者按租户处理；**按租户分区的顺序性**（同租户消息有序，39-08 分区键加租户）；**租户级消费限速**（噪声邻居防护 55-08）。
- 易错点：上下文只覆盖同步链（异步丢失 -> 数据写错租户，最严重事故）；租户来源可伪造（HTTP 头直接信任，应从 JWT 取——44-09）；拦截器改写吞掉非业务 SQL（运维 SQL 被改写出错）。

**示例代码**
```java
import java.util.HashMap;
import java.util.Map;

public class TenantContext {
    static final ThreadLocal<String> CTX = new ThreadLocal<>();

    static void set(String tenantId) {
        CTX.set(tenantId);
    }

    static String require() {
        String t = CTX.get();
        if (t == null) {
            throw new IllegalStateException("租户上下文缺失 —— 拒绝执行（防跨租户写）");
        }
        return t;
    }

    static void clear() {
        CTX.remove();               // 线程复用必须清理（50-06）
    }

    static void writeRecord(String data) {
        String tenant = require();
        System.out.printf("  写入数据 %s（tenant=%s）%n", data, tenant);
    }

    public static void main(String[] args) {
        set("t-100");
        writeRecord("order-9001");
        clear();                    // 线程归还前清理
        try {
            writeRecord("order-9002");
        } catch (IllegalStateException e) {
            System.out.println("  上下文缺失被拦截：" + e.getMessage());
        }
    }
}
```

**课后练习**
1. 实现完整租户上下文（来源解析 + 线程池装饰器传递 + 落库注入 + 强制校验），异步场景验证不丢租户。
2. 实现 MyBatis 拦截器模拟：改写 SQL 自动加 tenant_id 条件，验证绕过声明机制。
3. 思考题：为什么租户 ID 必须从 JWT 取而不能信任请求头？构造一个伪造 X-Tenant-Id 头读取他租数据的攻击时序。

---

### 55-04 数据隔离的安全边界

**知识点**
- **跨租户泄露是 SaaS 的"核级事故"**：一条 SQL 少了租户条件、一个缓存键漏前缀、一次导出未过滤——都会把 A 租户数据给 B 租户。**防线必须多层**（36 章纵深防御）：拦截器自动注入（第一层）-> 代码审查 checklist -> 架构测试（ArchUnit 断言所有 Mapper 方法带租户）-> 渗透测试（跨租户越权用例 49-07 ACL 测试同源）。
- **越权（IDOR）的租户版**：36-05 的 IDOR 是用户级（改 userId 看别人订单），**租户版是改 tenantId**——防护一致：**资源归属校验必须双键**（tenant_id + 资源所属租户），"查到了但不是你的"要 404 而不是 403（不泄露存在性）。
- **导出与集成的高危面**：报表导出（全租户 vs 本租户权限）、开放 API（租户令牌 scope）、数据迁移导出——**导出是泄露主通道**，审批 + 脱敏 + 水印（46-09 合规）。
- **备份与恢复的租户边界**：共享表备份天然全租户——**恢复单租户**需要按租户解析（成本高，提前设计恢复预案）；独立库天然按库恢复（隔离方案的隐性收益）。
- 易错点：测试环境用生产数据（跨租户合规事故）；日志打印全量数据（泄露面 36-09）；租户删除后的数据残留（备份/缓存/ES 索引残留——GDPR 删除权 55-10）。

**示例代码**
```java
import java.util.LinkedHashMap;
import java.util.Map;

public class TenantDataSafety {
    /** 资源归属双键校验：tenant + 资源所属租户一致 */
    static String getOrder(String callerTenant, String orderId,
                           Map<String, String> orders) {
        String owner = orders.get(orderId);
        if (owner == null || !owner.equals(callerTenant)) {
            return "404 Not Found（不泄露存在性）";
        }
        return "200 订单数据";
    }

    public static void main(String[] args) {
        Map<String, String> orders = new LinkedHashMap<>();
        orders.put("o-1", "t-100");
        orders.put("o-2", "t-200");
        System.out.println("  t-100 查 o-1 -> " + getOrder("t-100", "o-1", orders));
        System.out.println("  t-200 查 o-1 -> " + getOrder("t-200", "o-1", orders));
        System.out.println("  t-200 查不存在 -> " + getOrder("t-200", "o-9", orders));
        System.out.println("  关键：越权与不存在返回相同（不帮攻击者枚举）");
    }
}
```

**课后练习**
1. 实现双键归属校验 + 统一 404 语义，写跨租户越权测试用例集（枚举他租资源）。
2. 实现"租户删除的完整清除"：业务表/缓存/ES 索引/备份标记，输出清除清单核验。
3. 思考题：为什么越权返回 404 而不是 403？从信息泄露（存在性枚举）与攻击者侦察成本的角度分析。

---

### 55-05 租户配额与计费

**知识点**
- **配额是 SaaS 的商业模式技术化**：订阅等级（免费/专业/企业）-> 资源配额（用户数/存储/API 调用量/功能开关）——**配额系统 = 计量 + 限额 + 超额处理**。
- **计量（metering）**：API 调用计数（Redis INCR 日窗口）、存储用量（定时统计）、功能用量——**计量数据是账单依据**（51 章对账思想：计量流水 vs 账单对账）；**计量的性能**：高频调用计数用 Redis 原子 INCR 而不是查库累加。
- **限额与超额策略**：硬限额（超了拒绝 + 明确错误码与升级引导）、软限额（超了告警宽限）、**突发容忍**（令牌桶 44 章允许短时突发）；**超额不误伤**：免费用户超限时"读可用写受限"比全拒绝体验好（产品决策）。
- **计费的扣费顺序与幂等**：月度扣费失败（重试 + 宽限期 + 服务降级阶梯，51 章支付链路复用）；**扣费幂等**（账单号唯一）。
- 易错点：计量不准（多计/漏计引发纠纷）；限额检查在业务后（超配额已消费）；硬限额无升级引导（用户流失）；计量数据可篡改（内部作恶 51-07）。

**示例代码**
```java
import java.util.LinkedHashMap;
import java.util.Map;

public class TenantQuotaBilling {
    static class QuotaMeter {
        final long limit;
        final Map<String, Long> daily = new LinkedHashMap<>();

        QuotaMeter(long limit) {
            this.limit = limit;
        }

        /** 原子计量 + 限额判断（返回是否放行） */
        synchronized boolean consume(String tenant, String day, long n) {
            long used = daily.merge(day, n, Long::sum);
            if (used > limit) {
                daily.merge(day, -n, Long::sum);       // 超额回退（不放行）
                System.out.printf("  [%s] 日配额 %d 已满 -> 拒绝（错误码 QUOTA_EXCEEDED + 升级引导）%n",
                        tenant, limit);
                return false;
            }
            System.out.printf("  [%s] 计量 +%d（已用 %d/%d）%n", tenant, n, used, limit);
            return true;
        }
    }

    public static void main(String[] args) {
        QuotaMeter meter = new QuotaMeter(1000);
        meter.consume("t-100", "2026-11-18", 800);
        meter.consume("t-100", "2026-11-18", 300);    // 超额
        meter.consume("t-100", "2026-11-18", 200);    // 回退后可继续
    }
}
```

**课后练习**
1. 实现完整配额系统（计量/限额/超额回退/升级引导错误码），并发消费 1000 次验证计数精确。
2. 实现"超额降级阶梯"：免费租户超限后读可用写受限（44-08 阶梯的商业版），对比全拒绝的体验差异。
3. 思考题：为什么计量流水要与账单对账？构造一次"Redis 计数丢失"导致少计费的场景与对账修复流程。

---

### 55-06 租户配置分层

**知识点**
- **SaaS 配置的四层叠加**：平台默认 -> 租户级覆盖 -> 用户级覆盖 -> 请求级参数——**优先级从低到高**（40-03 配置优先级的 SaaS 版）；租户级配置是 SaaS 的"定制化"产品能力（logo/主题/业务参数/功能开关）。
- **租户配置的存储与分发**：配置表（tenant_id+key+value）或租户独立配置块；**热更新**（53-03 长轮询/推送，租户维度更细）；**配置变更审计**（谁改的租户配置，51-10 审计）。
- **功能开关（feature flag）的租户维度**：同一功能按租户灰度（54-08 分流思想：按租户而非用户）——**SaaS 的功能发布天然按租户灰度**（大客户先上）。
- **配置校验与回滚**：租户配置错误只影响该租户（爆炸半径天然小）——但**校验仍必须**（46-08 契约：类型/范围/依赖）；**租户级回滚**（配置历史版本）。
- 易错点：租户配置无校验（一个租户的错配置拖垮共享应用——噪声邻居的配置版）；默认值硬编码散落（应集中声明）；配置缓存无租户键（55-01 切面清单）。
- **与噪声邻居的联动**：租户配置里常含"资源类配置"（批处理并发/导出行数上限）——**大租户的激进配置要受平台上限约束**（55-08）。

**示例代码**
```java
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class TenantConfig {
    static final Map<String, String> PLATFORM_DEFAULT = Map.of(
            "page.size", "20", "export.rows", "10000", "theme", "light");
    static final Map<String, Map<String, String>> tenantOverride = new HashMap<>();

    static String get(String tenant, String key) {
        Map<String, String> ov = tenantOverride.get(tenant);
        if (ov != null && ov.containsKey(key)) {
            return ov.get(key);            // 租户覆盖
        }
        return PLATFORM_DEFAULT.get(key);  // 平台默认
    }

    public static void main(String[] args) {
        tenantOverride.put("t-big", new LinkedHashMap<>(Map.of("page.size", "100")));
        System.out.println("  小租户 page.size = " + get("t-small", "page.size"));
        System.out.println("  大租户 page.size = " + get("t-big", "page.size") + "（租户覆盖）");
        System.out.println();
        System.out.println("  上限约束：大租户 page.size 平台硬上限 100（55-08 噪声邻居防护）");
    }
}
```

**课后练习**
1. 实现四层配置叠加（平台/租户/用户/请求级），写优先级测试用例。
2. 实现租户配置的校验器（类型/范围/平台上限）与变更审计，演示一次坏配置被拦截。
3. 思考题：为什么租户级配置错误"只影响该租户"但不等于"可以不校验"？从共享应用资源与噪声邻居角度分析。

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

## 本章综合练习

### 综合题：多租户 CMS 落地

**场景**

做一个多租户内容管理系统：租户隔离方案选型、租户上下文与路由、数据隔离安全边界、配额计费、配置分层、噪声邻居隔离。核心是「一个租户绝不能看到另一个租户的数据」。

**任务**
1. 对比三种隔离方案（独立库、共享库独立 schema、共享表加 tenant_id），给出本项目选型与理由。
2. 实现租户上下文：请求头解析租户 → ThreadLocal 传递 → 数据访问层自动加租户条件，并处理异步线程中上下文丢失。
3. 实现越权防护：所有查询强制带 tenant_id，写一个测试用例证明伪造租户 id 无法读到他人数据。
4. 实现配额与隔离：按租户限制请求速率与存储量，单租户超限不影响其他租户（噪声邻居）。

**验收清单**
- [ ] 任何查询都不可能出现跨租户数据，越权测试用例全部被拦截。
- [ ] 异步任务中租户上下文正确传递（或明确拒绝执行）。
- [ ] 配额超限时只影响该租户，其他租户响应时间无明显变化。
- [ ] 配置分层生效：平台级默认配置 + 租户级覆盖，改租户配置不影响其他租户。
