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
