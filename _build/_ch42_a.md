### 42-01 为什么需要可观测性

**知识点**
- **监控回答"系统有没有问题"，可观测性回答"为什么有问题"**：监控是预设问题（CPU 高吗？错误率超了吗？），可观测性是任意问题的探索能力——出了事先看什么、能不能看出东西。**不预设问题也能排查，才是可观测**。
- **三支柱各管一层**：**指标（Metrics）**聚合数字，回答"系统整体健康吗"，便宜、快、适合告警；**日志（Logs）**离散事件明细，回答"当时发生了什么"，量大、可检索；**链路（Traces）**请求的跨服务路径，回答"慢在哪一跳、错在哪一步"。三者通过关联字段（trace_id）互相跳转。
- **白盒监控优于黑盒监控**：黑盒（从外部探测：接口 200 吗）只能告诉你"坏了"；白盒（内部暴露指标：队列堆积、缓存命中率）在用户感知前就告警。**好的监控体系黑白结合，以白盒为主**。
- **可观测性是设计出来的不是加出来的**：代码里埋点（关键路径打指标、异常带上下文日志、请求传递 trace_id）、中间件暴露指标、统一日志格式——**上线后再补可观测性，等于在运行的火车上装仪表盘**。
- **告警与排障的分层**：告警要少而准（对接值班），排障信息要多而全（对接排查）。**把告警当排障信息源用，值班人员会被淹死**——这是 42-06 的主题。
- **成本意识**：日志与链路是存储大户，采样与保留策略（42-08）从第一天就要设计。可观测性平台的成本随业务线性增长，不治理就是隐形的第二套核心系统。
- 易错点：只装了监控组件（Prometheus/ELK）没在代码里埋点（白盒能力为零）；三支柱数据无法关联（日志没带 trace_id，只能靠时间猜）；把"有 Grafana 面板"当成有可观测性（没人看的面板等于没有）；线上排障靠登录机器 grep（无结构化日志时唯一手段，说明平台缺位）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class WhyObservability {
    record Symptom(String symptom, String blackBoxSays, String whiteBoxSays) {
    }

    static void monitoringVsObservability() {
        System.out.println("【同一批症状：黑盒 vs 白盒的信息量】");
        List<Symptom> cases = List.of(
                new Symptom("接口偶发 500", "“服务不健康”", "下单库连接池耗尽，等待队列峰值 47"),
                new Symptom("高峰期变慢", "“响应时间上升”", "缓存命中率 99%->82%，回源 QPS 翻倍"),
                new Symptom("夜间 CPU 尖刺", "“凌晨 2 点异常”", "定时任务全量统计走了无索引查询"));
        for (Symptom s : cases) {
            System.out.printf("  %-12s 黑盒：%-12s 白盒：%s%n", s.symptom(), s.blackBoxSays(), s.whiteBoxSays());
        }
        System.out.println("  白盒能力 = 代码里主动埋的指标与日志，组件装上不等于能力出现");
        System.out.println();
    }

    static void threePillars() {
        System.out.println("【三支柱的分工与关联】");
        String[][] rows = {
                {"指标", "聚合数字（QPS/P99/错误率）", "告警与趋势", "便宜，存储 O(采样点)"},
                {"日志", "离散事件明细", "查明细节与上下文", "贵，保留期短"},
                {"链路", "一次请求的跨服务路径", "定位慢/错在哪一跳", "按采样存储"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-4s %-26s %-12s %s%n", r[0], r[1], r[2], r[3]);
        }
        Map<String, String> join = new LinkedHashMap<>();
        join.put("告警(指标)", "P99 超阈值 -> 点开该时段日志");
        join.put("日志", "错误日志带 trace_id=a1b2 -> 跳链路");
        join.put("链路", "span order->stock 耗时 800ms -> 锁定下游");
        for (Map.Entry<String, String> e : join.entrySet()) {
            System.out.println("  关联：" + e.getKey() + " -> " + e.getValue());
        }
        System.out.println("  trace_id 是三支柱的通用钥匙，埋点时就该规划");
        System.out.println();
    }

    static void instrumentChecklist() {
        System.out.println("【代码侧埋点清单：上线前自检】");
        List<String> items = List.of(
                "每个对外接口：QPS、错误率、延迟分位数（带 route 与 version 标签）",
                "关键业务事件打业务指标：下单量、支付成功率",
                "异常日志带上下文：userId/orderId/trace_id，不打印敏感字段",
                "资源指标：连接池使用率、队列长度、缓存命中率（白盒核心）",
                "trace_id 全链路传递：HTTP 头 -> RPC -> MQ 消息头",
                "日志结构化：JSON 格式，禁止拼字符串");
        for (String i : items) {
            System.out.println("  [ ] " + i);
        }
    }

    public static void main(String[] args) {
        monitoringVsObservability();
        threePillars();
        instrumentChecklist();
    }
}
```

**课后练习**
1. 对你负责的服务做一次"黑盒/白盒盘点"：列出当前能回答与不能回答的 5 个问题，补齐缺失的埋点。
2. 实现"三支柱关联"的最小原型：一条错误日志带 trace_id，一个指标带同 trace 维度，演示告警 -> 日志 -> 链路的跳转路径。
3. 思考题：为什么说"上线后再补可观测性像在运行的火车上装仪表盘"？列出三个必须在设计期决定的可观测性参数。

---

### 42-02 结构化日志与采集管道

**知识点**
- **非结构化日志是可观测性的第一杀手**：`log.info("用户" + userId + "下单成功")` 人类能读，机器无法可靠解析——字段名漂移、格式随心情变。**结构化日志（JSON 每行一条）让日志变成可查询的数据**：字段固定、类型明确、可索引。
- **一条合格日志的字段骨架**：`timestamp`（毫秒级）、`level`（DEBUG/INFO/WARN/ERROR）、`service`、`trace_id`/`span_id`、`message`（人类可读的一句话）、业务上下文（userId/orderId）、异常堆栈（error 级）。**骨架全团队统一**，业务字段按需扩展。
- **级别的语义纪律**：ERROR 表示"需要人处理的异常"（打多了=狼来了）；WARN 表示"偏离预期但自动恢复"（重试成功、降级触发）；INFO 是关键业务事件（下单、支付、状态迁移）。**DEBUG 生产默认关闭，需要时动态开**（开关按请求灰度）。
- **采集管道的三段**：应用输出 stdout/文件 -> 采集器（Filebeat/Fluent Bit）转发 -> 中心化存储（ES/日志平台）。**应用不直接写中心**（网络抖动不应影响业务进程），本地缓冲 + 重试由采集器负责。
- **日志的上下文传播**：MDC（Mapped Diagnostic Context）把 trace_id/userId 放进线程上下文，日志框架每次输出自动带上——**避免每条日志手工拼**。跨线程（异步任务）要显式传递，漏传是 trace 断裂的常见原因。
- **敏感信息治理**：日志出现密码/token/身份证 = 合规事故。规范：脱敏注解统一处理、日志扫描工具进 CI（36 章的敏感信息检查在这里落地）。
- 易错点：循环里打 INFO（日志洪水）；异常被 catch 后只打 message 不打堆栈（排障断线索）；多行堆栈被采集器按行拆散（要配置多行合并）；日志级别全靠 System.out（无级别无格式不可采集）。

**示例代码**
```java
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class StructuredLogging {
    /** 极简结构化日志器：演示字段骨架与 MDC 思想 */
    static class Logger {
        final String service;

        Logger(String service) {
            this.service = service;
        }

        String format(String level, String message, Map<String, Object> ctx) {
            StringBuilder sb = new StringBuilder("{");
            sb.append("\"ts\":\"").append(Instant.now()).append("\",");
            sb.append("\"level\":\"").append(level).append("\",");
            sb.append("\"service\":\"").append(service).append("\",");
            sb.append("\"trace_id\":\"").append(MDC.get("trace_id")).append("\",");
            sb.append("\"message\":\"").append(message).append("\"");
            for (Map.Entry<String, Object> e : ctx.entrySet()) {
                sb.append(",\"").append(e.getKey()).append("\":\"").append(e.getValue()).append("\"");
            }
            return sb.append("}").toString();
        }

        void info(String message, Map<String, Object> ctx) {
            emit("INFO", message, ctx);
        }

        void error(String message, Exception e, Map<String, Object> ctx) {
            ctx.put("exception", e.getClass().getSimpleName());
            emit("ERROR", message, ctx);
        }

        private void emit(String level, String message, Map<String, Object> ctx) {
            System.out.println(format(level, message, ctx));
        }
    }

    /** 线程上下文（MDC 的思想）：trace_id 自动进每条日志 */
    static class MDC {
        static final ThreadLocal<String> CTX = new ThreadLocal<>();

        static void put(String key, String value) {
            if ("trace_id".equals(key)) {
                CTX.set(value);
            }
        }

        static String get(String key) {
            return "trace_id".equals(key) ? CTX.get() : null;
        }
    }

    static void goodVsBad() {
        System.out.println("【同一次下单：拼字符串 vs 结构化】");
        Logger log = new Logger("order-service");
        MDC.put("trace_id", UUID.randomUUID().toString().substring(0, 8));
        System.out.println("  反例：log.info(\"用户1001下单成功，订单号" + 9001 + "金额69.0\")");
        System.out.println("    -> 机器无法稳定提取 userId/orderId，换个人写法就变");
        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("userId", "1001");
        ctx.put("orderId", "9001");
        ctx.put("amount", "69.0");
        log.info("下单成功", ctx);
        System.out.println("  正例如上：字段固定，可按 userId/orderId 精确检索");
        System.out.println();
    }

    static void levelDiscipline() {
        System.out.println("【级别的语义：ERROR 是给值班看的】");
        record Event(String event, String level, String why) {
        }
        List<Event> events = List.of(
                new Event("支付回调验签失败", "ERROR", "资金相关，需要人工介入"),
                new Event("调用库存服务超时后重试成功", "WARN", "偏离预期但自愈"),
                new Event("订单创建", "INFO", "关键业务事件"),
                new Event("循环里的每条记录处理", "DEBUG", "生产默认关"));
        for (Event e : events) {
            System.out.printf("  %-16s %-5s %s%n", e.event(), e.level(), e.why());
        }
        System.out.println("  ERROR 频率应接近 0：日志告警群里 ERROR 刷屏 = 无人再看");
        System.out.println();
    }

    static void pipeline() {
        System.out.println("【采集管道：应用不直连中心】");
        List<String> path = List.of(
                "应用 -> stdout（容器环境）或滚动文件",
                "采集器（Filebeat/Fluent Bit）tail + 多行合并（堆栈成一条）",
                "本地缓冲队列：中心不可达时先落盘，恢复后重试",
                "中心存储（ES）按天索引，冷数据转归档");
        for (String s : path) {
            System.out.println("  " + s);
        }
        System.out.println("  应用进程只管快速写出（异步 appender），采集的可靠性归采集器");
    }

    public static void main(String[] args) {
        goodVsBad();
        levelDiscipline();
        pipeline();
    }
}
```

**课后练习**
1. 把你项目里 5 条拼字符串日志改造成结构化日志（MDC + 固定字段），验证按 orderId 检索的效率差异。
2. 实现"异步线程丢失 trace_id"的修复：用 TaskDecorator/手动传递让线程池任务也能带上 trace_id，并写一个测试证明。
3. 思考题：为什么应用进程不应直接把日志推给中心存储？从进程稳定性与采集可靠性分离的角度分析。

---

### 42-03 日志存储与查询

**知识点**
- **日志存储的核心矛盾是"量大与可查"**：一个中型服务一天几十亿条日志，全量存 ES 成本爆炸。**分层存储是标准解**：热数据（7~14 天，ES 可检索）、温数据（30~90 天，压缩归档可回捞）、冷数据（合规期，对象存储）。**保留期按排障真实需求定，不按"以防万一"定**。
- **索引按天滚动与查询路由**：`log-2026.11.17` 每天一个索引，查询带时间范围就只碰相关索引——**不带时间范围的日志查询是平台事故**（全索引扫描）。日志平台默认强制时间窗（如最多 7 天）。
- **日志查询的正确姿势**：先缩小范围（服务 + 时间窗 + level）再搜关键词；避免前置通配符（`*error`）；高频查询场景靠字段过滤而不是全文匹配。**让排障的人第一次查询就能命中，靠的是规范的字段而非搜索技巧**。
- **日志量的治理**：采样（DEBUG 按比例打）、聚合（循环日志合并成一条汇总）、分级开关（生产只 INFO+）。**日志量治理是成本工程**：一条日志的成本 = 写入 + 存储 + 索引 + 查询。
- **从日志到证据链**：排障时日志的价值在于"时间线重建"——同一 trace_id 的日志按时间排序就是一次请求的完整生命史；同一 orderId 跨天检索就是业务对象的变更史。**日志平台的导出与分享功能（永久链接）是协作的基础**。
- 易错点：日志量随业务增长没人管（成本失控）；堆栈多行被拆散导致检索出来的错误日志不可读；把日志平台当调试终端（DEBUG 全量开）；敏感字段明文入库（合规风险 + 泄露面扩大）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class LogStoreQuery {
    record LogEntry(String ts, String level, String service, String traceId,
                    String message, String hot) {
    }

    /** 模拟按天索引：查询只碰时间窗内的索引 */
    static List<String> dailyIndices(String from, String to) {
        List<String> all = List.of("log-2026.11.10", "log-2026.11.11", "log-2026.11.12",
                "log-2026.11.13", "log-2026.11.14", "log-2026.11.15", "log-2026.11.16",
                "log-2026.11.17");
        return all.stream().filter(idx -> {
            String day = idx.substring(4);
            return day.compareTo(from) >= 0 && day.compareTo(to) <= 0;
        }).toList();
    }

    static void tieredStorage() {
        System.out.println("【分层存储：热/温/冷的成本阶梯】");
        String[][] tiers = {
                {"热", "7~14 天", "ES 全文可检索", "最贵：副本+索引"},
                {"温", "30~90 天", "压缩归档，回捞分钟级", "中：对象存储"},
                {"冷", "合规期（如 1 年）", "不可检索，仅审计取证", "最便宜"},
        };
        for (String[] t : tiers) {
            System.out.printf("  %-3s 保留%-10s %-20s 成本=%s%n", t[0], t[1], t[2], t[3]);
        }
        System.out.println("  保留期依据：线上事故 90% 在 7 天内完成复盘 -> 热数据 14 天够用");
        System.out.println();
    }

    static void queryDiscipline() {
        System.out.println("【查询纪律：先收窄再搜索】");
        List<String> indices = dailyIndices("2026.11.15", "2026.11.17");
        System.out.printf("  时间窗 11.15~11.17 -> 只碰 %d 个索引（%s）%n",
                indices.size(), indices);
        String[][] queries = {
                {"service=order AND level=ERROR AND 关键词", "PASS：字段过滤先行"},
                {"*退款失败*", "FAIL：前置通配符全索引扫"},
                {"无时间范围 + 关键词", "FAIL：平台级事故，默认拒绝"},
        };
        for (String[] q : queries) {
            System.out.printf("  查询 %-32s %s%n", q[0], q[1]);
        }
        System.out.println();
    }

    static void timelineRebuild() {
        System.out.println("【时间线重建：trace_id 是排障的骨架】");
        List<LogEntry> logs = List.of(
                new LogEntry("10:00:00.101", "INFO", "gateway", "a1b2", "收到请求 POST /order", "热"),
                new LogEntry("10:00:00.105", "INFO", "order", "a1b2", "校验通过，扣库存", "热"),
                new LogEntry("10:00:00.900", "WARN", "stock", "a1b2", "库存服务超时，重试1", "热"),
                new LogEntry("10:00:01.100", "INFO", "stock", "a1b2", "重试成功", "热"),
                new LogEntry("10:00:01.120", "INFO", "order", "a1b2", "下单成功 9001", "热"));
        logs.stream().sorted(Comparator.comparing(LogEntry::ts))
                .forEach(l -> System.out.printf("  %s [%-5s] %-8s %s%n",
                        l.ts(), l.level(), l.service(), l.message()));
        System.out.println("  结论：stock 服务重试耗时 200ms，整体链路 1.019s —— 一目了然");
        System.out.println("  同一 trace_id 的日志 = 一次请求的完整生命史");
    }

    public static void main(String[] args) {
        tieredStorage();
        queryDiscipline();
        timelineRebuild();
    }
}
```

**课后练习**
1. 为你的日志平台设计分层保留策略（热/温/冷的天数与依据），估算每月存储成本并给出一条降本手段。
2. 实现"多行堆栈合并"的最小模拟：给定一组带堆栈的文本行，输出合并后的 JSON 日志条数，验证采集器配置的效果。
3. 思考题：为什么日志平台要强制时间窗？从索引数量与查询扇出推导无界查询的成本曲线。

---

### 42-04 指标模型与时间序列

**知识点**
- **指标 = 时间序列 + 维度标签**：`http_server_requests_seconds{service="order",route="/order",version="2.4.1"}`。**维度（label）决定可切片性**：带 version 才能做金丝雀对比（41-07），带 route 才能定位慢接口。**维度基数是设计红线**：userId 做 label 会产生百万级序列，直接打爆存储（基数爆炸是监控平台的第一事故）。
- **四种指标类型**：Counter（只增计数：请求总数）、Gauge（瞬时值：连接池大小）、Histogram（分桶：延迟分布，算 P99）、Summary（客户端分位数，难聚合，少用）。**延迟必须用 Histogram**：跨实例聚合需要原始分桶。
- **P99 的正确理解**：99% 的请求比这快。它比平均值敏感（平均值掩盖长尾）、比 Max 稳定（Max 被单次 GC 抖动污染）。**观测长尾用 P99，抓极端异常看 P99.9 或 Max**。
- **采集模型：Pull vs Push**：Prometheus 拉模型（服务暴露 /metrics，Prometheus 定期抓，发现服务挂了=抓不到=up=0）；Push（StatsD/推网关，任务型短进程用）。**Pull 天然带健康检查语义**。
- **时间序列的降采样与聚合**：原始精度 15s 保留 30 天，5m 粒度保留 1 年（ Thanos/VictoriaMetrics 的分层）。查询时的 rate/increase 函数处理 Counter 的单调性（重启清零要用 clamp 处理）。
- **指标的命名纪律**：`<namespace>_<subsystem>_<name>_<unit>`（如 `http_request_duration_seconds`），单位进名字，复数不加。命名混乱的指标库半年后没人敢用。
- 易错点：userId/orderId 当 label（基数爆炸）；用平均值当 SLO（掩盖长尾）；Counter 直接画图忘记 rate（曲线只升不降没有信息量）；指标不带 service/version 标签（跨服务对比无从谈起）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class MetricsModel {
    /** Histogram 分桶：桶边界（秒），统计落桶数 */
    static double[] BUCKETS = {0.01, 0.05, 0.1, 0.25, 0.5, 1, 2.5, 5, 10};

    static int bucketOf(double seconds) {
        for (int i = 0; i < BUCKETS.length; i++) {
            if (seconds <= BUCKETS[i]) {
                return i;
            }
        }
        return BUCKETS.length;   // +Inf
    }

    static void histogramQuantile() {
        System.out.println("【Histogram -> 分位数：跨实例聚合的基础】");
        int[] counts = {820, 150, 20, 6, 3, 1, 0, 0, 0, 0};   // 各桶累计
        int total = 1000;
        double target = 0.99 * total;
        long cum = 0;
        double p99 = Double.NaN;
        for (int i = 0; i < counts.length; i++) {
            cum += counts[i];
            if (cum >= target && Double.isNaN(p99)) {
                p99 = i < BUCKETS.length ? BUCKETS[i] : Double.POSITIVE_INFINITY;
            }
        }
        System.out.println("  请求延迟分布（1000 次）：");
        System.out.printf("  P50 ≈ %.2fs  P90 ≈ %.2fs  P99 ≈ %.2fs%n", 0.05, 0.25, p99);
        System.out.println("  分桶粒度决定分位数精度：桶太粗 P99 只能落在桶边界上");
        System.out.println("  聚合语义：先合并各实例的桶计数再算分位数（Summary 做不到）");
        System.out.println();
    }

    static void cardinalityDanger() {
        System.out.println("【基数爆炸：label 设计的红线】");
        record Series(String label, int cardinality, String verdict) {
        }
        List<Series> labels = List.of(
                new Series("service", 12, "安全：服务数有限"),
                new Series("route", 86, "安全：接口路径有限"),
                new Series("version", 4, "安全：同时在跑的版本数"),
                new Series("user_id", 5_000_000, "灾难：每个用户一条时间序列"),
                new Series("order_id", 800_000_000, "灾难：存储直接打爆"));
        for (Series s : labels) {
            System.out.printf("  %-9s 基数=%-12d %s%n", s.label(), s.cardinality(), s.verdict());
        }
        System.out.println("  规则：label 的取值必须有限可枚举；无限值进日志不进指标");
        System.out.println();
    }

    static void rateOfCounter() {
        System.out.println("【Counter -> rate：让单调计数有意义】");
        // 每分钟采样一次的请求总数
        TreeMap<Integer, Long> counter = new TreeMap<>();
        long v = 0;
        for (int min = 0; min <= 10; min++) {
            v += 120 + (min % 3) * 30;
            counter.put(min, v);
        }
        System.out.println("  原始曲线（只增）：");
        for (Map.Entry<Integer, Long> e : counter.entrySet()) {
            System.out.printf("    min%02d 总数=%d%n", e.getKey(), e.getValue());
        }
        System.out.println("  rate(2m) 曲线（每分钟增量≈120~180 QPS）才有波动信息");
        System.out.println("  注意：服务重启 counter 归零，rate 函数要能处理负增量");
        System.out.println();
    }

    static void pullVsPush() {
        System.out.println("【Pull 的健康语义】");
        List<String> notes = List.of(
                "Prometheus 每 15s 抓一次 /metrics",
                "实例挂掉 -> 抓取失败 -> up{instance}=0 -> 天然的健康检查指标",
                "短生命周期任务（定时批处理）抓不到 -> 用 PushGateway 主动推",
                "抓取目标靠服务发现（K8s SD），扩缩容自动纳入");
        for (String n : notes) {
            System.out.println("  - " + n);
        }
    }

    public static void main(String[] args) {
        histogramQuantile();
        cardinalityDanger();
        rateOfCounter();
        pullVsPush();
    }
}
```

**课后练习**
1. 实现一个带 Histogram 的模拟指标器：记录 1000 个延迟样本，输出各桶计数与 P50/P90/P99，并对比"实例 A+B 先聚合再算分位"与"各自算分位再平均"的差异。
2. 审计你项目的指标 label：找出基数最大的三个 label，给出处理方案（改日志/降维/删除）。
3. 思考题：为什么延迟监控必须用 Histogram 而 Summary 难以跨实例聚合？从分位数计算的可合并性推导。

---

### 42-05 黄金指标与 SLO

**知识点**
- **Google SRE 四大黄金信号**：**延迟**（多快，分成功/失败请求看）、**流量**（多少请求）、**错误**（多少失败）、**饱和度**（资源多满：CPU/内存/连接池/队列）。任何服务先补齐这四类指标再谈别的——**四信号覆盖 80% 的事故发现**。
- **SLI/SLO/SLA 的层级**：SLI 是指标（P99 延迟、成功率），SLO 是目标（P99 < 200ms 占 99.9%），SLA 是对外违约条款（SLO + 赔付）。**工程团队对 SLO 负责，商务对 SLA 负责**。
- **错误预算（error budget）是 SLO 的工程化表达**：SLO 99.9% = 每月 43 分钟不可用预算。预算花完 -> 冻结功能发布只修可靠性；预算充足 -> 可以激进发布。**错误预算把"稳定 vs 快速迭代"的争吵变成数字决策**。
- **SLO 要按用户体验定义而不是按机器定义**：用户关心"下单成功率"而不是"CPU<80%"。基础设施指标是原因，SLO 是结果——**告警挂在结果上，排障看原因**。
- **依赖服务的 SLO 传导**：你的服务依赖库存服务，它的 P99 800ms 会吃掉你的延迟预算。**依赖方的 SLO 是你 SLO 的输入**，跨团队 SLO 协议（谁对谁承诺什么）是微服务治理的一部分。
- **饱和度信号最有预见性**：连接池 85%、磁盘 80%、队列持续增长——这些信号在事故前就亮。**延迟/错误是事故本身，饱和度是事故的前兆**。
- 易错点：SLO 定 100%（不可能达成，告警必然误报）；SLO 只看平均值（掩盖长尾）；错误预算花完还继续冲需求（SLO 形同虚设）；每个指标都设告警（告警通胀，42-06 处理）。

**示例代码**
```java
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SloGoldenSignals {
    record Signal(String name, String question, String example, String role) {
    }

    static void goldenFour() {
        System.out.println("【四大黄金信号：订单服务】");
        List<Signal> signals = List.of(
                new Signal("延迟", "用户等多久？", "P99: 成功 180ms / 失败 950ms", "SLO 主指标"),
                new Signal("流量", "系统扛多少？", "QPS 1200，大促 8000", "容量规划输入"),
                new Signal("错误", "失败率多少？", "HTTP 5xx 0.05% + 业务失败 0.3%", "SLO 主指标"),
                new Signal("饱和度", "资源还剩多少？", "连接池 62%，队列 12，磁盘 55%", "事故前兆"));
        for (Signal s : signals) {
            System.out.printf("  %-4s %-10s %-34s %s%n", s.name(), s.question(), s.example(), s.role());
        }
        System.out.println("  延迟要分开成功/失败请求：失败快速返回会拉低延迟假象");
        System.out.println();
    }

    static void errorBudget() {
        System.out.println("【错误预算：把稳定性变成可花的钱】");
        double slo = 99.9;
        int minutesPerMonth = 30 * 24 * 60;
        double budget = minutesPerMonth * (1 - slo / 100);
        System.out.printf("  SLO %.1f%% -> 每月不可用预算 %.0f 分钟（30 天口径）%n", slo, budget);
        record Spend(String event, double minutes) {
        }
        List<Spend> spends = List.of(
                new Spend("11-05 发布故障", 12),
                new Spend("11-12 依赖库存服务故障", 18),
                new Spend("11-20 慢查询拖垮主库", 9));
        double used = 0;
        for (Spend s : spends) {
            used += s.minutes();
            System.out.printf("  %-18s -%.0f 分钟（累计 %.0f/%.0f）%n", s.event(), s.minutes(), used, budget);
        }
        System.out.printf("  预算余额 %.0f 分钟 -> %s%n", budget - used,
                budget - used < 5 ? "耗尽：冻结功能发布，专注可靠性" : "健康：可正常迭代");
        System.out.println();
    }

    static void sloDesign() {
        System.out.println("【SLO 设计：用户视角 + 分层传导】");
        Map<String, String> slis = new LinkedHashMap<>();
        slis.put("下单成功率", "（成功单数/下单请求数）> 99.95%");
        slis.put("下单 P99", "< 300ms（其中库存依赖承诺 P99 < 150ms）");
        slis.put("查询可用性", "月度 99.9%");
        for (Map.Entry<String, String> e : slis.entrySet()) {
            System.out.printf("  %-10s %s%n", e.getKey(), e.getValue());
        }
        System.out.println("  反例：CPU < 80% 不是 SLO（那是原因不是结果）");
        System.out.println("  传导：依赖方 SLO 破约 = 你的预算被扣 -> 跨团队 SLO 协议");
        System.out.println();
    }

    static void saturationFirst() {
        System.out.println("【饱和度是前兆：按剩余量告警】");
        record Resource(String name, String warn, String critical, String lead) {
        }
        List<Resource> rs = List.of(
                new Resource("DB 连接池", "> 80% 持续 5m", "> 90%", "连接池耗尽前 10 分钟"),
                new Resource("磁盘", "> 75%", "> 85%", "提前数天"),
                new Resource("MQ 积压", "> 5 万条", "> 20 万", "消费能力不足的前兆"),
                new Resource("线程池队列", "> 100", "持续增长", "线程耗尽前 5 分钟"));
        for (Resource r : rs) {
            System.out.printf("  %-10s WARN %-14s CRITICAL %-8s 领先=%s%n",
                    r.name(), r.warn(), r.critical(), r.lead());
        }
        System.out.println("  饱和度告警的价值：在用户感知前介入");
    }

    public static void main(String[] args) {
        goldenFour();
        errorBudget();
        sloDesign();
        saturationFirst();
    }
}
```

**课后练习**
1. 为你的核心服务定义一套 SLO：3 条 SLI、目标值、测量口径，并算出对应的月度错误预算。
2. 实现错误预算记账器：输入本月故障清单（时长），输出预算消耗曲线与"是否冻结发布"的判定。
3. 思考题：为什么饱和度告警能"在用户感知前介入"而错误率告警不能？从两类信号的物理含义推导各自的最佳告警时机。
