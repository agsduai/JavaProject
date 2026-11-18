### 29-07 配置中心与动态刷新

**知识点**
- 配置散落在每个服务的 `application.yml` 里，改一个开关要重新打包发布 10 个服务——配置中心解决"集中管理 + 动态生效"。
- 配置分层：**公共配置**（注册中心地址、日志格式）→ **应用级配置**（数据库、线程池）→ **环境级配置**（dev/test/prod 不同值）→ **实例级配置**（灰度开关）。优先级从低到高，后者覆盖前者。
- 动态刷新：配置中心推送变更 → 客户端收到通知 → 更新本地缓存 → 触发 `@RefreshScope` 类重建或监听器回调。注意：**不是所有配置都能热更新**（数据库连接池大小、JVM 参数、线程池核心数通常需要重启）。
- 灰度与回滚：配置变更要能按实例/按比例生效，并且能一键回滚到上一版本。配置错误导致的故障比代码 bug 更常见。
- 安全：数据库密码等敏感配置要加密存储，客户端本地不能明文落盘；配置中心的访问要鉴权。
- 易错：把配置中心当"实时数据库"频繁读写（配置是低频变更，不是状态存储）；改了配置但客户端没有监听，以为生效了；配置中心挂了服务无法启动（应支持本地快照兜底）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ConfigCenter {
    /** 配置项：key -> (value, 版本号) */
    static class ConfigItem {
        String value;
        int version;

        ConfigItem(String value, int version) {
            this.value = value;
            this.version = version;
        }
    }

    /** 极简配置中心 */
    static class Center {
        final Map<String, ConfigItem> store = new LinkedHashMap<>();
        final List<String> listeners = new ArrayList<>();

        void put(String key, String value) {
            ConfigItem old = store.get(key);
            int v = old == null ? 1 : old.version + 1;
            store.put(key, new ConfigItem(value, v));
            System.out.println("  [中心] 更新 " + key + " = " + value + "（v" + v + "），推送变更");
            for (String l : listeners) {
                System.out.println("    -> 通知 " + l);
            }
        }
    }

    /** 客户端：本地快照 + 监听 */
    static class Client {
        final String name;
        final Map<String, String> snapshot = new HashMap<>();
        boolean hotReloadable = true;

        Client(String name, Center center) {
            this.name = name;
            center.listeners.add(name);
        }

        void load(Center center, String key) {
            ConfigItem item = center.store.get(key);
            if (item != null) {
                snapshot.put(key, item.value);
                System.out.println("  [" + name + "] 加载 " + key + " = " + item.value);
            }
        }

        void onChange(String key, String value) {
            if (!hotReloadable) {
                System.out.println("  [" + name + "] 收到 " + key + " 变更，但该项需要重启生效，暂存待下次启动");
                return;
            }
            snapshot.put(key, value);
            System.out.println("  [" + name + "] 热更新 " + key + " -> " + value);
        }
    }

    public static void main(String[] args) {
        Center center = new Center();
        center.store.put("app.timeout.ms", new ConfigItem("3000", 1));
        center.store.put("app.retry.max", new ConfigItem("3", 1));
        center.store.put("db.pool.size", new ConfigItem("20", 1));

        Client order = new Client("order-service", center);
        Client item = new Client("item-service", center);
        for (String k : List.of("app.timeout.ms", "app.retry.max", "db.pool.size")) {
            order.load(center, k);
            item.load(center, k);
        }

        System.out.println();
        System.out.println("=== 改超时：可热更新 ===");
        center.put("app.timeout.ms", "1500");
        order.onChange("app.timeout.ms", "1500");
        item.onChange("app.timeout.ms", "1500");

        System.out.println();
        System.out.println("=== 改连接池大小：需要重启 ===");
        order.hotReloadable = false;
        center.put("db.pool.size", "50");
        order.onChange("db.pool.size", "50");

        System.out.println();
        System.out.println("=== 配置优先级（后者覆盖前者）===");
        Map<String, String> merged = new LinkedHashMap<>();
        merged.put("公共配置 timeout=3000", "注册中心、日志格式");
        merged.put("应用配置 timeout=2000", "数据库、线程池");
        merged.put("环境配置 timeout=1000", "prod 覆盖 test");
        merged.put("实例配置 timeout=800", "灰度实例单独调小");
        for (Map.Entry<String, String> e : merged.entrySet()) {
            System.out.println("  " + e.getKey() + "  ← " + e.getValue());
        }
        System.out.println("  最终生效 timeout=800ms");

        System.out.println();
        System.out.println("=== 配置中心不可用：本地快照兜底 ===");
        System.out.println("  客户端启动时先从本地 snapshot 文件加载，再尝试连中心刷新");
        System.out.println("  -> 中心宕机不影响已有实例启动，只是拿不到最新配置");
        System.out.println("  版本回滚：中心保留最近 N 个版本，出问题一键回退 v" + (center.store.get("app.timeout.ms").version - 1));
    }
}
```

**课后练习**
1. 给 `Center` 加版本历史与 `rollback(key)` 方法，打印回滚后的值。
2. 列出你项目中 5 个配置项，逐个判断能否热更新，并说明原因。
3. 配置中心整体不可用时，服务应该如何启动？画出降级流程。

### 29-08 熔断、降级与服务隔离

**知识点**
- **雪崩**：下游 B 变慢 → 调用 B 的线程全部阻塞 → A 的线程池耗尽 → A 也不可用 → 上游 C 跟着挂。一个慢接口能拖垮整条链路。
- **熔断器三态**：`CLOSED`（正常放行，统计失败率）→ 失败率超阈值 → `OPEN`（直接快速失败，不发起调用）→ 等待窗口后 → `HALF_OPEN`（放少量试探请求）→ 成功则回 `CLOSED`，失败则回 `OPEN`。
- **降级**：熔断或异常时返回兜底结果——缓存旧值、默认值、空列表、友好提示。降级要**有损但可用**，不能降级成报错。
- **隔离**：线程池隔离（每个下游独立线程池，慢的不拖累快的）、信号量隔离（轻量，限制并发数）、集群隔离（不同业务用不同实例）。
- 熔断参数：失败率阈值（如 50%）、最小请求数（样本太少不熔断）、熔断时长（给下游恢复时间）、半开试探数量。
- 易错：熔断后没有兜底，直接把异常抛给用户；熔断时长设太短（下游还没恢复就放流量）；所有下游共用一个线程池，等于没隔离。

**示例代码**
```java
public class CircuitBreaker {
    enum State { CLOSED, OPEN, HALF_OPEN }

    static class Breaker {
        State state = State.CLOSED;
        int total;
        int failed;
        long openUntilMs;
        int halfOpenPermits;

        final int minRequests = 10;
        final double failRateThreshold = 0.5;
        final long openDurationMs = 5000;
        final int halfOpenMax = 3;

        /** 返回 true 表示放行调用 */
        boolean allow(long now) {
            if (state == State.OPEN) {
                if (now >= openUntilMs) {
                    state = State.HALF_OPEN;
                    halfOpenPermits = halfOpenMax;
                    System.out.println("    [熔断器] 冷却结束 -> HALF_OPEN，允许 " + halfOpenMax + " 个试探");
                } else {
                    return false;
                }
            }
            if (state == State.HALF_OPEN) {
                if (halfOpenPermits <= 0) {
                    return false;
                }
                halfOpenPermits--;
            }
            return true;
        }

        void record(boolean ok, long now) {
            total++;
            if (!ok) {
                failed++;
            }
            if (state == State.HALF_OPEN) {
                if (ok) {
                    System.out.println("    [熔断器] 试探成功 -> CLOSED，恢复正常");
                    reset();
                } else {
                    System.out.println("    [熔断器] 试探失败 -> 重新 OPEN");
                    trip(now);
                }
                return;
            }
            if (total >= minRequests) {
                double rate = (double) failed / total;
                if (rate >= failRateThreshold) {
                    System.out.println("    [熔断器] 失败率 " + Math.round(rate * 100) + "% 超阈值 -> OPEN");
                    trip(now);
                }
            }
        }

        void trip(long now) {
            state = State.OPEN;
            openUntilMs = now + openDurationMs;
            reset();
            state = State.OPEN;
            openUntilMs = now + openDurationMs;
        }

        void reset() {
            total = 0;
            failed = 0;
        }
    }

    /** 下游服务：前若干次失败，之后恢复 */
    static boolean callDownstream(int seq) {
        return seq > 12;
    }

    /** 降级兜底 */
    static String fallback() {
        return "降级：返回缓存中的旧商品列表（可能不是最新）";
    }

    public static void main(String[] args) {
        Breaker breaker = new Breaker();
        long now = 0;
        int seq = 0;

        System.out.println("=== 下游持续失败：熔断器打开 ===");
        for (int i = 1; i <= 20; i++) {
            seq++;
            now += 100;
            if (!breaker.allow(now)) {
                System.out.println("  第 " + i + " 次请求：熔断 OPEN，直接走降级 -> " + fallback());
                continue;
            }
            boolean ok = callDownstream(seq);
            System.out.println("  第 " + i + " 次请求：调用下游 -> " + (ok ? "成功" : "超时"));
            breaker.record(ok, now);
            if (!ok) {
                System.out.println("    -> " + fallback());
            }
        }

        System.out.println();
        System.out.println("=== 冷却 5s 后进入半开，试探恢复 ===");
        now += 5000;
        for (int i = 21; i <= 26; i++) {
            seq++;
            now += 100;
            if (!breaker.allow(now)) {
                System.out.println("  第 " + i + " 次请求：仍不放行 -> 降级");
                continue;
            }
            boolean ok = callDownstream(seq);
            System.out.println("  第 " + i + " 次请求：试探调用 -> " + (ok ? "成功" : "失败"));
            breaker.record(ok, now);
        }

        System.out.println();
        System.out.println("=== 隔离方式对比 ===");
        System.out.println("  线程池隔离：每个下游独立池（如 20 线程），慢的下游只耗尽自己的池");
        System.out.println("  信号量隔离：只限并发数，不换线程，开销小但无法超时中断");
        System.out.println("  集群隔离：核心业务和非核心业务部署在不同实例组，互不影响");
        System.out.println("  反例：所有下游共用一个 200 线程池，一个慢接口就能拖垮全部");
    }
}
```

**课后练习**
1. 把 `minRequests` 改成 20、阈值改成 0.3，重跑示例并观察熔断时机变化。
2. 实现"线程池隔离"：为商品服务和积分服务各建一个固定大小线程池，验证慢的不会占满另一个。
3. 设计一个降级矩阵：列出 5 个下游，分别给出"熔断后返回什么"。

### 29-09 分布式事务：从 2PC 到最终一致

**知识点**
- 本地事务靠数据库 ACID，跨服务后没有统一的锁和日志，只能用**补偿**或**协调者**来近似。
- **2PC/XA**：协调者先 `prepare` 所有参与者，全部就绪再 `commit`。强一致，但同步阻塞、协调者单点、性能差，不适合高并发互联网场景。
- **TCC**（Try-Confirm-Cancel）：业务层实现预留/确认/取消三个接口。性能好、可控性强，但侵入大，要处理空回滚、悬挂、幂等三个经典问题。
- **Saga**：把长事务拆成一串本地事务，失败时按反序执行补偿。适合长流程（旅游预订：机票 + 酒店 + 租车），但**没有隔离性**，中间状态对外可见。
- **本地消息表 / 事务消息**：只保证"本地操作 + 消息发送"原子，下游异步消费，是互联网最常用的**最终一致**方案（详见第 28-09 课）。
- **最大努力通知**：不断重试通知对方，直到成功或达到上限，适合对账、支付回调这类"必须通知到但可以晚"的场景。
- 选型口诀：强一致且低频 → 2PC；高并发且能补偿 → TCC/Saga；只要最终一致 → 本地消息表；对外通知 → 最大努力通知。
- 易错：以为加了 `@Transactional` 就能跨服务；TCC 的 Cancel 没有做幂等，重试时把已确认的又取消了；Saga 补偿顺序搞反。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DistributedTx {
    /** 参与者：账户，余额 + 冻结 */
    static class Account {
        final String name;
        int balance;
        int frozen;

        Account(String name, int balance) {
            this.name = name;
            this.balance = balance;
        }

        boolean tryFreeze(int amount) {
            if (balance < amount) {
                return false;
            }
            balance -= amount;
            frozen += amount;
            System.out.println("    [" + name + "] Try：冻结 " + amount + "，余额 " + balance + "，冻结中 " + frozen);
            return true;
        }

        void confirm(int amount) {
            frozen -= amount;
            System.out.println("    [" + name + "] Confirm：扣减冻结 " + amount + "，余额 " + balance + "，冻结中 " + frozen);
        }

        void cancel(int amount) {
            frozen -= amount;
            balance += amount;
            System.out.println("    [" + name + "] Cancel：解冻 " + amount + "，余额 " + balance + "，冻结中 " + frozen);
        }
    }

    public static void main(String[] args) {
        System.out.println("=== TCC：转账 100（A -> B）===");
        Account a = new Account("A", 500);
        Account b = new Account("B", 100);
        int amount = 100;

        List<Runnable> confirms = new ArrayList<>();
        List<Runnable> cancels = new ArrayList<>();

        boolean okA = a.tryFreeze(amount);
        if (okA) {
            confirms.add(() -> a.confirm(amount));
            cancels.add(() -> a.cancel(amount));
        }
        boolean okB = b.tryFreeze(amount);
        if (okB) {
            confirms.add(() -> b.confirm(amount));
            cancels.add(() -> b.cancel(amount));
        }

        System.out.println("  Try 阶段结果：A=" + okA + "，B=" + okB);
        if (okA && okB) {
            System.out.println("  全部 Try 成功 -> 执行 Confirm");
            confirms.forEach(Runnable::run);
        } else {
            System.out.println("  有 Try 失败 -> 反序执行 Cancel");
            for (int i = cancels.size() - 1; i >= 0; i--) {
                cancels.get(i).run();
            }
        }
        System.out.println("  最终：A 余额 " + a.balance + "，B 余额 " + b.balance);

        System.out.println();
        System.out.println("=== Saga：机票 -> 酒店 -> 租车，酒店失败 ===");
        List<String> done = new ArrayList<>();
        boolean hotelOk = false;
        if (true) {
            done.add("机票已订");
            System.out.println("  步骤1 订机票 成功");
        }
        if (hotelOk) {
            done.add("酒店已订");
            System.out.println("  步骤2 订酒店 成功");
        } else {
            System.out.println("  步骤2 订酒店 失败 -> 反序补偿");
            for (int i = done.size() - 1; i >= 0; i--) {
                System.out.println("    补偿：" + done.get(i).replace("已订", "已取消"));
            }
            System.out.println("  步骤3 租车 未执行");
        }

        System.out.println();
        System.out.println("=== 方案对比 ===");
        Map<String, String> table = new LinkedHashMap<>();
        table.put("2PC/XA", "强一致，同步阻塞，协调者单点，适合低频强一致");
        table.put("TCC", "高性能可控，侵入大，要处理空回滚/悬挂/幂等");
        table.put("Saga", "适合长流程，无隔离性，中间状态可见");
        table.put("本地消息表", "最终一致，最常用，需下游幂等");
        table.put("最大努力通知", "反复重试直到成功，适合对账与支付回调");
        for (Map.Entry<String, String> e : table.entrySet()) {
            System.out.println("  " + e.getKey() + "：" + e.getValue());
        }

        System.out.println();
        System.out.println("=== TCC 三个坑 ===");
        System.out.println("  空回滚：Try 没执行就收到 Cancel -> Cancel 要能识别并直接返回成功");
        System.out.println("  悬挂：Cancel 先到，Try 后到 -> Try 要检查是否已 Cancel，拒绝执行");
        System.out.println("  幂等：Confirm/Cancel 重试多次 -> 结果必须一致");
    }
}
```

**课后练习**
1. 给 `Account.tryFreeze` 加上"已 Cancel 则拒绝"的标记，演示悬挂问题被解决。
2. 说明为什么 2PC 在高并发场景下性能差，从锁持有时间的角度分析。
3. 为"下单扣库存 + 扣优惠券 + 加积分"设计事务方案，说明每一步用哪种模式。

### 29-10 链路追踪与日志聚合

**知识点**
- 微服务下一个请求跨多个服务，传统单机日志无法串起来。**链路追踪**用 `traceId`（整条链路）+ `spanId`（单个调用）把调用树还原出来。
- 传递方式：HTTP 头（`traceparent`/`X-B3-TraceId`）、MQ 消息属性、线程上下文（`ThreadLocal` + 线程池透传）。**异步和线程池是 traceId 最容易丢的地方**。
- 采样：全量采集成本太高，通常按 1%~10% 采样，但**出错的请求必须 100% 采集**（尾部采样）。
- 日志聚合：ELK/Loki 集中存储，按 `traceId` 一次查出整条链路的所有日志。日志格式必须结构化（JSON），字段固定：时间、级别、traceId、服务名、方法、耗时、业务键。
- 关键指标：每个 span 的耗时、下游错误率、慢调用 Top N。用这些定位"到底慢在哪一跳"。
- 易错：只在入口生成 traceId，下游重新生成导致链路断裂；日志里打业务对象 `toString()` 带出敏感信息；日志级别全开导致磁盘打满。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TraceLogging {
    /** 一个 span：一次方法/一次远程调用 */
    record Span(String traceId, String spanId, String parentId, String service,
                String method, long startMs, long costMs, boolean error) { }

    static final List<Span> SPANS = new ArrayList<>();
    static long clock = 0;

    static String newId(String prefix, int seq) {
        return prefix + "-" + Integer.toHexString(seq);
    }

    static Span record(String traceId, String spanId, String parentId, String service,
                       String method, long costMs, boolean error) {
        Span s = new Span(traceId, spanId, parentId, service, method, clock, costMs, error);
        SPANS.add(s);
        clock += costMs;
        return s;
    }

    public static void main(String[] args) {
        String traceId = "trace-4a1f";
        System.out.println("=== 一次下单请求的调用树（traceId=" + traceId + "）===");

        Span gateway = record(traceId, newId("s", 1), null, "gateway", "POST /api/orders", 0, false);
        Span order = record(traceId, newId("s", 2), gateway.spanId(), "order-service", "createOrder", 0, false);
        Span item = record(traceId, newId("s", 3), order.spanId(), "item-service", "deductStock", 120, false);
        Span user = record(traceId, newId("s", 4), order.spanId(), "user-service", "getAddress", 35, false);
        Span points = record(traceId, newId("s", 5), order.spanId(), "points-service", "addPoints", 480, true);
        Span db = record(traceId, newId("s", 6), order.spanId(), "order-service", "insertOrder", 60, false);

        long total = 0;
        for (Span s : SPANS) {
            total += s.costMs();
        }
        SPANS.set(0, new Span(gateway.traceId(), gateway.spanId(), null, gateway.service(),
                gateway.method(), 0, total, false));

        for (Span s : SPANS) {
            String indent = s.parentId() == null ? "" : "  ";
            System.out.printf("%s[%s] %-14s %-18s %5dms %s  spanId=%s parent=%s%n",
                    indent, s.traceId(), s.service(), s.method(), s.costMs(),
                    s.error() ? "ERROR" : "OK", s.spanId(), s.parentId());
        }

        System.out.println();
        System.out.println("=== 定位慢在哪一跳 ===");
        Span slowest = SPANS.get(0);
        for (Span s : SPANS) {
            if (s.costMs() > slowest.costMs()) {
                slowest = s;
            }
        }
        System.out.println("  最慢：" + slowest.service() + "." + slowest.method() + " " + slowest.costMs() + "ms");
        System.out.println("  总耗时 " + total + "ms，慢调用占 " + (slowest.costMs() * 100 / total) + "%");

        System.out.println();
        System.out.println("=== 结构化日志（按 traceId 聚合）===");
        Map<String, String> logLine = new LinkedHashMap<>();
        logLine.put("ts", "2026-11-17T10:00:01.234");
        logLine.put("level", "ERROR");
        logLine.put("traceId", traceId);
        logLine.put("service", "points-service");
        logLine.put("method", "addPoints");
        logLine.put("costMs", "480");
        logLine.put("bizKey", "ORD-20261117-001");
        logLine.put("msg", "下游超时，已进重试队列");
        System.out.println("  " + logLine);
        System.out.println("  一条命令捞出整条链路：grep '" + traceId + "' app-*.log");

        System.out.println();
        System.out.println("=== traceId 最容易丢的三个地方 ===");
        System.out.println("  1. 线程池：提交任务时没把 MDC 上下文带过去 -> 用包装 Runnable 透传");
        System.out.println("  2. MQ：发送时没放进消息头 -> 消费端从 header 恢复 traceId");
        System.out.println("  3. 异步回调：CompletableFuture 默认用公共线程池，上下文丢失");
        System.out.println("  采样：正常请求 1%~10%，错误请求 100%（尾部采样）");
    }
}
```

**课后练习**
1. 给示例加一个"按服务统计平均耗时与错误率"的汇总输出。
2. 实现一个包装 `Runnable` 的 `TraceRunnable`，把 traceId 从提交线程传到执行线程。
3. 设计日志字段规范：至少列出 8 个必填字段，并说明敏感字段如何处理。

### 29-11 综合案例：微服务化的下单链路

**知识点**
- 把前面所有能力拼起来：网关统一鉴权限流 → 订单服务编排 → 调用商品服务扣库存（超时 + 重试 + 熔断）→ 调用用户服务取地址 → 本地事务写订单 → 发消息异步加积分 → 全链路 traceId。
- 每一步都要有明确的**失败策略**：库存不足 → 直接失败；用户服务超时 → 用默认地址或让用户重填（可降级）；积分失败 → 不影响下单（异步 + 重试）。
- 关键设计：**核心链路只包含必须同步的步骤**，任何"可以晚点做"的都移到异步。
- 熔断与降级在案例中的位置：商品服务熔断时快速失败（不能超卖），积分服务熔断时静默降级（不影响下单）。
- 配置中心提供超时/重试/开关参数，出问题可以动态调小超时或关闭某条旁路。
- 易错：把积分调用放在订单事务里（事务被远程调用拉长，锁持有时间暴增）；库存扣减失败后没有回滚已扣的优惠券。

**示例代码**
```java
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MicroserviceOrderCase {
    static final Map<String, Integer> STOCK = new HashMap<>(Map.of("ITEM-1", 10));
    static final Map<String, String> ORDERS = new LinkedHashMap<>();
    static final Map<String, Integer> POINTS = new HashMap<>();
    static final List<String> MQ = new ArrayList<>();
    static final List<String> TRACE = new ArrayList<>();

    /** 简单熔断器：连续失败 3 次打开 5s */
    static class Breaker {
        int fails;
        long openUntil;
        boolean allow(long now) {
            return now >= openUntil;
        }
        void record(boolean ok, long now) {
            if (ok) {
                fails = 0;
            } else if (++fails >= 3) {
                openUntil = now + 5000;
                log("WARN", "积分服务熔断打开 5s");
            }
        }
    }

    static void log(String level, String msg) {
        String line = "[trace-" + Integer.toHexString(TRACE.size() + 1) + "] " + level + " " + msg;
        TRACE.add(line);
        System.out.println("  " + line);
    }

    /** 商品服务：扣库存（核心链路，失败即整单失败） */
    static boolean deductStock(String itemId, int qty) {
        Integer cur = STOCK.get(itemId);
        if (cur == null || cur < qty) {
            log("ERROR", "item-service 扣库存失败：库存不足 " + cur);
            return false;
        }
        STOCK.put(itemId, cur - qty);
        log("INFO", "item-service 扣库存成功，剩余 " + (cur - qty));
        return true;
    }

    /** 用户服务：取地址（可降级） */
    static String getAddress(String userId, boolean timeout) {
        if (timeout) {
            log("WARN", "user-service 超时 300ms -> 降级：使用用户上次地址");
            return "默认地址（降级）";
        }
        log("INFO", "user-service 返回地址");
        return "北京市海淀区";
    }

    /** 积分服务：异步 + 熔断 + 幂等 */
    static void addPointsAsync(Breaker breaker, String orderNo, String userId, long now) {
        if (!breaker.allow(now)) {
            log("WARN", "points-service 熔断中，消息留在队列稍后重试");
            return;
        }
        try {
            if (orderNo.endsWith("BAD")) {
                throw new IllegalStateException("积分服务 500");
            }
            POINTS.merge(userId, 100, Integer::sum);
            breaker.record(true, now);
            log("INFO", "points-service 加积分成功，累计 " + POINTS.get(userId));
        } catch (RuntimeException e) {
            breaker.record(false, now);
            log("ERROR", "points-service 失败：" + e.getMessage() + " -> 进重试队列");
        }
    }

    public static void main(String[] args) {
        long now = 0;
        Breaker pointsBreaker = new Breaker();

        System.out.println("=== 场景一：正常下单 ===");
        createOrder(pointsBreaker, "ORD-001", "u1001", "ITEM-1", 2, false, now);

        System.out.println();
        System.out.println("=== 场景二：用户服务超时，降级继续 ===");
        createOrder(pointsBreaker, "ORD-002", "u1001", "ITEM-1", 1, true, now);

        System.out.println();
        System.out.println("=== 场景三：库存不足，核心链路失败 ===");
        createOrder(pointsBreaker, "ORD-003", "u1002", "ITEM-1", 999, false, now);

        System.out.println();
        System.out.println("=== 场景四：积分服务连续失败 -> 熔断 ===");
        for (int i = 0; i < 3; i++) {
            addPointsAsync(pointsBreaker, "ORD-BAD-" + i, "u1003", now);
        }
        addPointsAsync(pointsBreaker, "ORD-004", "u1003", now);
        System.out.println("  等待 5s 冷却后恢复：");
        now += 5000;
        addPointsAsync(pointsBreaker, "ORD-005", "u1003", now);

        System.out.println();
        System.out.println("=== 最终状态 ===");
        System.out.println("  库存=" + STOCK);
        System.out.println("  订单=" + ORDERS);
        System.out.println("  积分=" + POINTS);
        System.out.println("  待投递消息=" + MQ);
        System.out.println("  链路日志条数=" + TRACE.size());
    }

    static void createOrder(Breaker breaker, String orderNo, String userId, String itemId,
                            int qty, boolean userTimeout, long now) {
        log("INFO", "gateway 鉴权通过，限流放行，userId=" + userId);
        if (!deductStock(itemId, qty)) {
            log("ERROR", "order-service 下单失败，返回 409");
            return;
        }
        String address = getAddress(userId, userTimeout);
        ORDERS.put(orderNo, "CREATED:" + address);
        log("INFO", "order-service 写订单 " + orderNo + " 成功（本地事务）");
        MQ.add("order.created:" + orderNo);
        log("INFO", "投递 order.created -> MQ");
        addPointsAsync(breaker, orderNo, userId, now);
    }
}
```

**课后练习**
1. 给案例加上"优惠券扣减"步骤，并保证库存扣减失败时优惠券能回滚。
2. 把积分调用改成真正的异步（投 MQ 后立即返回），重新输出链路日志，对比总耗时。
3. 画出这个案例的完整架构图，标出网关、各服务、MQ、配置中心、注册中心的位置与调用方向。
