### 42-06 告警设计与降噪

**知识点**
- **告警的第一原则：每一条告警都必须需要一个"人"采取行动**。发到群里没人处理的告警是噪音，噪音的累积效应是**告警疲劳**——真警报也被无视。宁可漏掉"可自愈的小事"，不可淹没"需要人的大事"。
- **告警的三级分类**：P1（用户受损，电话/短信，5 分钟响应）、P2（风险积累，IM 通知，30 分钟响应）、P3（提示观察，日报汇总）。**分级依据是用户影响面，不是技术惊悚程度**。
- **告警规则的正确形状**：`症状 + 持续时间 + 影响范围`。"P99 > 500ms" 不是告警，"P99 > 500ms 持续 5 分钟且影响下单主链路"才是。**瞬时抖动（GC、网络毛刺）用持续时间过滤**，机器周期性任务用时间窗屏蔽。
- **降噪三板斧**：**聚合**（同一事故的 30 条同源告警合并成 1 条事件，按根因分组）、**抑制**（DB 挂了 -> 抑制它下游全部服务的连接错误告警，只发根因）、**静默**（已知维护窗口、已知变更期间，人工静默并记录）。告警平台要支持抑制链与静默窗口，这是工具能力不是纪律问题。
- **自愈优于告警**：能自动恢复的（进程挂了拉起、主从切换、限流兜底）先自愈再告警"已自愈"（P3）。**值班名单里出现"重启一下就好"的告警，就是自动化欠账**。
- **告警闭环度量**：每条告警记录"是否有效"（真实故障/误报/已知问题），月度统计误报率与响应时长。**误报率 > 30% 的规则要么修要么删**。
- 易错点：一条规则告所有实例（实例越多告警越雪崩，应聚合到服务级）；把曲线抖动设成告警（没有持续时间条件）；告警直接发到没有响应责任的群（无 owner）；删掉烦人的告警而不是修好它（问题还在，只是没人看见了）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AlertDesign {
    record Alert(String id, String symptom, int durationMin, String scope, String level,
                 boolean actionable) {
    }

    static void classify() {
        System.out.println("【告警分级：按用户影响面】");
        List<Alert> alerts = List.of(
                new Alert("A1", "下单成功率跌破 99%", 5, "全部用户", "P1（电话）", true),
                new Alert("A2", "库存服务 P99 > 800ms", 10, "下单链路", "P2（IM）", true),
                new Alert("A3", "缓存命中率下降 10%", 30, "内部回源增多", "P3（日报）", true),
                new Alert("A4", "某实例单次 GC 2 秒", 1, "无用户影响", "不告警", false));
        for (Alert a : alerts) {
            System.out.printf("  %-3s %-22s 持续%2dmin %-8s %s  可行动=%s%n",
                    a.id(), a.symptom(), a.durationMin(), a.scope(), a.level(), a.actionable());
        }
        System.out.println("  A4 这类瞬时抖动用持续时间条件自然过滤");
        System.out.println();
    }

    static void dedupeAndSuppress() {
        System.out.println("【降噪：聚合与抑制链】");
        record Raw(String time, String service, String msg) {
        }
        List<Raw> raw = List.of(
                new Raw("02:01:03", "db", "主库连接拒绝"),
                new Raw("02:01:05", "order", "DB 连接超时"),
                new Raw("02:01:05", "stock", "DB 连接超时"),
                new Raw("02:01:06", "order", "下单失败率上升"),
                new Raw("02:01:06", "pay", "DB 连接超时"));
        System.out.println("  原始告警 5 条：");
        for (Raw r : raw) {
            System.out.printf("    %s %-6s %s%n", r.time(), r.service(), r.msg());
        }
        System.out.println("  聚合抑制后 1 条事件：");
        System.out.println("    [P1] 根因：主库 db 连接拒绝（02:01:03）");
        System.out.println("    受影响下游：order/stock/pay（连接超时被抑制，不单独通知）");
        System.out.println("    行动：检查主库进程与连接数；下游无需逐个处理");
        System.out.println();
    }

    static void selfHealing() {
        System.out.println("【自愈优于告警：能自动恢复的先恢复】");
        String[][] rows = {
                {"实例进程挂", "K8s 自动重建", "P3 告警「已自愈」"},
                {"Redis 主挂", "哨兵自动切换", "P2 告警「已切换，请验证」"},
                {"接口限流触发", "网关限流兜底", "P2 告警「限流生效，观察」"},
                {"主库连接拒绝", "无法自愈", "P1 告警，需要人"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-10s -> %-14s %s%n", r[0], r[1], r[2]);
        }
        System.out.println("  检验标准：值班群里不再出现「重启一下就好」");
        System.out.println();
    }

    static void feedbackLoop() {
        System.out.println("【告警闭环：每条告警标注是否有效】");
        Map<String, Integer> outcome = new LinkedHashMap<>();
        outcome.put("真实故障（需行动）", 42);
        outcome.put("误报（规则问题）", 31);
        outcome.put("已知问题（待修）", 12);
        int total = outcome.values().stream().mapToInt(Integer::intValue).sum();
        for (Map.Entry<String, Integer> e : outcome.entrySet()) {
            System.out.printf("  %-16s %d 条（%.0f%%）%n", e.getKey(), e.getValue(), e.getValue() * 100.0 / total);
        }
        System.out.printf("  误报率 %.0f%% -> 超 30%% 红线：逐条修规则或删除%n",
                outcome.get("误报（规则问题）") * 100.0 / total);
        System.out.println("  告警质量是运营出来的：每月例会过一遍误报清单");
    }

    public static void main(String[] args) {
        classify();
        dedupeAndSuppress();
        selfHealing();
        feedbackLoop();
    }
}
```

**课后练习**
1. 重构你项目的告警规则清单：按 P1/P2/P3 重新分级，给每条规则补"持续时间 + 影响范围"条件，删除不可行动的规则。
2. 实现一个迷你告警聚合器：输入带时间戳的告警流，按根因服务分组输出事件（含抑制的下游列表）。
3. 思考题：为什么"删掉烦人的告警"是危险的，而"修好规则"是正确的？从信息损失与问题潜伏两个角度分析。

---

### 42-07 分布式链路追踪

**知识点**
- **微服务排障的根本困难是"一个请求的真相散落在 N 个服务的日志里"**：链路追踪把一次请求经过的所有服务记录成一棵 **Span 树**——根 Span 是用户请求，子 Span 是各下游调用，每个 Span 带服务名、耗时、状态、标签。**Trace = 请求的完整旅程，Span = 旅程中的一段**。
- **上下文传播是追踪的地基**：入口生成 `trace_id` + `span_id`，每次下游调用把（trace_id, parent_span_id）放进 HTTP 头（W3C 标准头 `traceparent`）或 MQ 消息属性；服务端解析后继续生成子 span。**任何一环丢失传播（异步线程池、手写 HTTP 调用）就是链路断裂**，排查时只能靠时间猜。
- **Span 的关键标签**：`peer`（调了谁）、`status`（ok/error）、错误详情、以及业务标签（orderId）。**耗时 + 状态 + 关系**三要素让"慢在哪一跳、错在哪一步"变成一次查询。
- **追踪的价值场景**：慢请求定位（根 span 展开，最深的子 span 就是嫌疑人）、跨服务错误归属（哪层先抛的错）、容量分析（每个下游的扇出与扇入）、依赖地图（自动生成服务拓扑）。
- **埋点的三个层次**：自动探针（agent 字节码增强，零代码侵入，覆盖 JDBC/HTTP/Redis 客户端）、SDK 显式埋点（框架 hook，需要少量代码）、手工埋点（业务关键段）。**生产以自动探针为主 + 关键业务手工补充**。
- **追踪与日志/指标的联动**：Span 带上 trace_id 后，日志与指标都能按 trace 维度关联（42-01 的"通用钥匙"）。OpenTelemetry 正在统一三支柱的 API 与数据模型——**学概念比学具体组件重要，组件三年一换**。
- 易错点：只在 HTTP 层埋点，JDBC/Redis/MQ 这类"真正慢的地方"没覆盖；异步线程池丢上下文（trace 断裂）；Span 里塞大对象（一个 span 几 MB，存储爆炸）；每跳都建 span 但不打耗时标签（有结构没信息）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.List;

public class TraceSpans {
    /** Span：一次请求中的一段工作 */
    record Span(String spanId, String parentSpanId, String service, String op,
                long startMs, long endMs, String status, String peer) {

        long costMs() {
            return endMs - startMs;
        }

        int depth() {
            int d = 0;
            String p = parentSpanId;
            // 本演示用简单缩进层级代替真实 parent 查找
            return d;
        }
    }

    static void buildTrace() {
        System.out.println("【一次下单请求的 Span 树】");
        List<Span> spans = List.of(
                new Span("s1", null, "gateway", "POST /order", 0, 1020, "ok", "-"),
                new Span("s2", "s1", "order", "createOrder", 5, 1010, "ok", "-"),
                new Span("s3", "s2", "order", "auth.check", 6, 12, "ok", "auth"),
                new Span("s4", "s2", "order", "db.insert", 13, 45, "ok", "mysql"),
                new Span("s5", "s2", "stock", "deduct", 46, 1000, "ok", "stock-rpc"),
                new Span("s6", "s5", "stock", "db.update", 50, 980, "ok", "mysql"));
        printTree(spans, null, 0);
        Span root = spans.get(0);
        System.out.printf("%n  总耗时 %dms，最深的子链：s5(stock.deduct %dms) -> s6(db.update %dms)%n",
                root.costMs(), spans.get(4).costMs(), spans.get(5).costMs());
        System.out.println("  结论：90% 的时间耗在库存服务的 DB 更新 —— 下一步优化方向明确");
        System.out.println();
    }

    private static void printTree(List<Span> spans, String parent, int indent) {
        for (Span s : spans) {
            if ((parent == null) == (s.parentSpanId() == null) && (parent == null || parent.equals(s.parentSpanId()))) {
                String tree = "  ".repeat(indent) + (indent > 0 ? "└─ " : "");
                System.out.printf("  %s%-10s %-16s %-12s %4dms [%s] peer=%s%n",
                        tree, s.service(), s.op(), s.spanId(), s.costMs(), s.status(), s.peer());
                printTree(spans, s.spanId(), indent + 1);
            }
        }
    }

    static void propagation() {
        System.out.println("【上下文传播：traceparent 头与断裂点】");
        String header = "traceparent: 00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";
        System.out.println("  W3C 格式：" + header);
        System.out.println("  解析：version=00 trace_id=4bf9...4736 parent_span=00f0...02b7 flags=01");
        List<String> breakPoints = List.of(
                "异步线程池：Runnable 里直接执行 -> 上下文没拷贝 -> 断裂（用装饰器修复）",
                "手写 HttpClient：没把头复制进新请求 -> 断裂",
                "MQ 消费：生产者没写消息属性 -> 断裂",
                "定时任务入口：没有 trace -> 应新建根 span");
        for (String b : breakPoints) {
            System.out.println("  断裂点：" + b);
        }
        System.out.println();
    }

    static void whatTraceSolves() {
        System.out.println("【追踪解决的四个排障问题】");
        List<String> items = List.of(
                "慢请求：展开 span 树，最深子链即嫌疑人（本章示例：库存 DB）",
                "错误归属：从根到叶找第一个 error 状态的 span",
                "扇出分析：根 span 的直接子 span 数 = 每请求下游调用量",
                "依赖地图：按 (caller, callee) 聚合 span 自动生成拓扑");
        for (String s : items) {
            System.out.println("  - " + s);
        }
    }

    public static void main(String[] args) {
        buildTrace();
        propagation();
        whatTraceSolves();
    }
}
```

**课后练习**
1. 用 OpenTelemetry SDK（或手写 Span 类）为一条两层调用链建 span 树，故意在异步线程里丢上下文，观察断裂现象后修复。
2. 实现链路分析器：输入 span 列表，输出"最耗时子链"与"第一个 error 的 span"，用一次慢请求样本验证。
3. 思考题：为什么说"学追踪概念比学组件重要"？对比 Zipkin/Jaeger/SkyWalking 的数据模型与 OpenTelemetry 的标准化方向。

---

### 42-08 采样与链路成本

**知识点**
- **全量追踪的成本不可承受**：每个请求几十个 span、每天几十亿请求——全量存储的硬件成本超过业务本身。**采样是链路追踪的必备设计**，采样策略直接决定"你想抓的问题能不能抓到"。
- **头部采样（head sampling）**：入口处按概率决定整条链路是否记录（如 1%），实现简单、成本确定；缺点是**低频异常靠撞运气**——每秒 5 次的错误在 1% 采样下平均 20 秒才有一条。
- **尾部采样（tail sampling）**：请求结束后再决定是否保留，可以**按结果采样**——所有 error 100% 保留、慢请求（P99 以上）100% 保留、正常请求采样 1%。这是问题驱动的理想方案，代价是采集器要**缓存整条链路等它结束**（内存与延迟成本）。
- **两种策略组合是生产标准**：头部 10% 打底 + 尾部"error/慢请求全保"。**漏斗要和 SLO 对齐**：你承诺 P99<300ms，那 >300ms 的请求就该全保留。
- **采样的代价意识**：span 的存储量 = QPS × span 数 × 采样率 × 平均大小。降本的杠杆按大到小：降采样率 > 减 span 数（合并客户端 span）> 压缩。**保留期一般 7~14 天**（与日志同步）。
- **采样影响统计正确性**：1% 采样的 P99 统计不可信（样本太少）。原则：**指标从全量数据算（Prometheus 抓取不分采样），采样只影响"链路明细"的保留**。两者混用是常见误用。
- 易错点：全量追踪直接上生产（成本爆炸）；只配了头部采样导致排查"偶发 500"无链路可看；尾部采样缓存队列被打爆（高流量时 OOM）；把采样率当 SLO 统计口径（统计失真）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class TraceSampling {
    record Request(String id, int costMs, boolean error, boolean keep) {
    }

    static void headVsTail() {
        System.out.println("【头部 10% vs 尾部按结果：同样 20 个请求的保留差异】");
        List<Request> reqs = new ArrayList<>();
        Random r = new Random(42);
        for (int i = 0; i < 20; i++) {
            int cost = i == 7 ? 850 : 80 + r.nextInt(150);
            boolean err = i == 11;
            reqs.add(new Request("r" + i, cost, err, false));
        }
        int headKept = 0;
        for (Request req : reqs) {
            boolean keep = r.nextInt(100) < 10;
            if (keep) {
                headKept++;
            }
        }
        int tailKept = 0;
        for (Request req : reqs) {
            boolean keep = req.error() || req.costMs() > 300;
            if (keep) {
                tailKept++;
            }
            reqs.set(reqs.indexOf(req), new Request(req.id(), req.costMs(), req.error(), keep));
        }
        System.out.printf("  头部采样 10%%：保留 %d 条（随机，错误 r11 大概率丢失）%n", headKept);
        System.out.println("  尾部采样（error 全保 + >300ms 全保）：");
        for (Request req : reqs) {
            if (req.keep()) {
                System.out.printf("    保留 %s cost=%dms error=%s%n",
                        req.id(), req.costMs(), req.error());
            }
        }
        System.out.println("  你要抓的问题（偶发错误、慢请求）100% 在保留集合里");
        System.out.println();
    }

    static void costModel() {
        System.out.println("【成本模型：QPS × span数 × 采样率 × 大小】");
        long qps = 2000, spansPerReq = 30;
        double sampleRate = 0.01;
        long dailySpans = Math.round(qps * 86400 * spansPerReq * sampleRate);
        long dailyGb = dailySpans * 512 / 1024 / 1024 / 1024;
        System.out.printf("  QPS=%d, span/请求=%d, 采样 1%%%n", qps, spansPerReq);
        System.out.printf("  每日 span 数 ≈ %d 亿条级 -> 存储约 %d GB/天（按 512B/span）%n",
                dailySpans / 100_000_000, dailyGb);
        System.out.println("  降本杠杆：采样 1%->0.5% 减半；合并客户端 span 减 span 数；保留期 14d->7d 减半");
        System.out.println();
    }

    static void statsIntegrity() {
        System.out.println("【采样与统计的正确边界】");
        List<String> rules = List.of(
                "指标（QPS/P99/错误率）：从全量请求算，不经过采样",
                "链路明细：按策略采样，用于个案排查",
                "错误率告警如果基于采样数据 -> 统计失真，绝对禁止",
                "尾部采样的缓存队列要有上限与降级（超限退回头部采样）");
        for (String s : rules) {
            System.out.println("  - " + s);
        }
    }

    public static void main(String[] args) {
        headVsTail();
        costModel();
        statsIntegrity();
    }
}
```

**课后练习**
1. 实现一个尾部采样器：规则（error 100%、P99 慢请求 100%、其余 1%）+ 缓存上限（超限退头部采样），用 10 万模拟请求统计各类样本的保留率。
2. 用本章成本模型算你公司的追踪预算：代入真实 QPS 与 span 数，给出三个降本杠杆的量化效果。
3. 思考题：为什么"错误率告警基于采样数据"是绝对禁止的？从统计一致性与告警可信度推导指标与链路的数据边界。

---

### 42-09 持续剖析与资源画像

**知识点**
- **指标告诉你 CPU 高了，Profile 告诉你 CPU 在干什么**：持续剖析（continuous profiling）周期性采集进程的调用栈样本（CPU、内存分配、锁等待、IO），聚合成火焰图。**它是"指标 -> 代码行"的最后一跳**，与第 30 章 JVM 工具（jstack 一次性快照）的区别在于持续与聚合。
- **火焰图的读法**：横轴是 CPU 时间占比（越宽越热），纵轴是调用栈深度（上层的由下层调用）。**找宽的平顶**——又宽又不能继续展开的帧就是热点。采样型 profiler 开销 1~2%，可常驻生产。
- **四类剖析各有用途**：CPU profile（热点函数、正则灾难、JSON 序列化）、alloc profile（内存分配大户，GC 压力来源）、block/lock profile（锁竞争）、off-CPU（等待 IO）。**"CPU 不高但慢"的谜题通常在 off-CPU profile 里解开**。
- **持续剖析的服务形态**：agent 常驻采集（每 10~60s 一轮）-> 上报到剖析平台 -> 按服务/时间/版本聚合对比。**版本对比（diff 火焰图）是发布验证利器**：2.4.0 vs 2.4.1 的 CPU 热点变化一眼可见。
- **与指标的联动**：CPU 饱和度告警触发 -> 平台自动拉取该时段 CPU profile -> 热点函数直出。**告警到根因的自动化路径**是可观测性平台的进阶形态。
- **Go/Java 的生态差异**：Go 的 pprof 是标杆（低成本常驻）；Java 主流是 async-profiler（event-based，低开销）+ JFR（JDK 自带，持续记录）。JFR 是 Java 团队的默认起点——**JDK 21 的 JFR 已经能覆盖 CPU/alloc/lock 全场景**。
- 易错点：只在出事时 attach 工具（错过现场，CPU 尖峰是瞬时的）；采样 profiler 与侵入式 tracer 混淆（前者低开销可常驻）；只看 CPU profile 忽略 alloc（很多"GC 频繁"的真凶是某个函数狂分配）；火焰图存档不保留（没法做版本对比）。

**示例代码**
```java
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ContinuousProfiling {
    /** 模拟 CPU profile：函数名 -> 自身采样数 */
    record Frame(String name, int samples, String parent) {
        double pct(int total) {
            return samples * 100.0 / total;
        }
    }

    static void flameReading() {
        System.out.println("【火焰图数据：一次 30s 的 CPU 采样（共 3000 样本）】");
        List<Frame> frames = List.of(
                new Frame("main.run", 3000, null),
                new Frame("http.handle", 2400, "main.run"),
                new Frame("json.serialize", 1050, "http.handle"),
                new Frame("regex.match", 720, "http.handle"),
                new Frame("db.query", 400, "http.handle"),
                new Frame("gc.pause", 100, "main.run"));
        int total = 3000;
        for (Frame f : frames) {
            String bar = "█".repeat(Math.max(1, (int) (f.pct(total) / 4)));
            System.out.printf("  %-18s %6.1f%% %s%n", f.name(), f.pct(total), bar);
        }
        System.out.println("  读法：json.serialize 与 regex.match 是两个“宽平顶” -> 热点");
        System.out.println("  行动：正则预编译 + JSON 缓存，预计省 40% CPU");
        System.out.println();
    }

    static void profileTypes() {
        System.out.println("【四类剖析的适用问题】");
        String[][] rows = {
                {"cpu", "CPU 高、吞吐低", "热点函数、正则灾难、序列化"},
                {"alloc", "GC 频繁、内存涨", "分配大户（临时对象工厂）"},
                {"lock", "线程多但吞吐低", "锁竞争热点"},
                {"off-cpu", "CPU 不高但慢", "等待 IO、锁等待、连接池等待"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-8s %-16s %s%n", r[0], r[1], r[2]);
        }
        System.out.println("  「CPU 不高但接口慢」-> 先看 off-CPU：多数是等待而非计算");
        System.out.println();
    }

    static void jfrPath() {
        System.out.println("【Java 团队的默认起点：JFR】");
        List<String> steps = List.of(
                "启动参数：-XX:StartFlightRecording=duration=60s,filename=... 或常驻 JFR",
                "jcmd <pid> JFR.start duration=60s（在线触发）",
                "jfr print/summary 分析；或接持续剖析平台聚合",
                "开销 1~2%，可常驻生产（区别于 jstat 轮询的侵入）");
        for (String s : steps) {
            System.out.println("  " + s);
        }
        System.out.println("  进阶：async-profiler 出火焰图 + 平台化聚合 + 版本 diff");
    }

    public static void main(String[] args) {
        flameReading();
        profileTypes();
        jfrPath();
    }
}
```

**课后练习**
1. 用 JFR 采集你的应用 60 秒（压测中），输出 top 5 CPU 热点方法，并给出一条可执行的优化。
2. 实现"alloc 热点"分析：模拟两个版本的分配数据，输出 diff 火焰图数据，找出 2.4.1 新引入的分配大户。
3. 思考题：为什么"CPU 不高但接口慢"要先看 off-CPU 而不是 CPU？从等待与计算的资源占用差异推导排查顺序。

---

### 42-10 仪表盘与事故响应

**知识点**
- **仪表盘是"为回答具体问题"而生的**：一张图回答一个问题——"服务整体健康吗"（服务总览）、"下单链路哪一环慢"（链路视图）、"这次发布效果如何"（版本对比）。**没有问题的仪表盘是装饰**，团队仪表盘数量应少于团队人数。
- **服务总览页的标准结构**：顶部 SLO 状态灯（绿/黄/红）+ 错误预算剩余 -> 中部黄金四信号（延迟/流量/错误/饱和度）-> 底部依赖健康。**30 秒内让人判断"要不要介入"**。
- **告警 -> 仪表盘 -> 排障的动线**：告警卡片带跳转链接（直达相关仪表盘与日志查询）——事故响应的每一步都不应手工找入口。**响应速度是动线设计的函数**。
- **事故响应的角色分工**：指挥（IC）统筹决策、通信（CL）对外同步、操作（OML）执行处置。**小团队可以一人分饰，但角色不能缺**——没有指挥的事故响应就是一群人各自为战。
- **响应的时间纪律**：确认（ack）< 5 分钟、初步定级 < 10 分钟、对外同步每 15 分钟一次（哪怕"仍在排查"）。**沉默的值班比故障本身更伤信任**。
- **复盘（postmortem）是无责文化下的改进引擎**：时间线（告警->响应->恢复）、根因链（为什么告警晚了/恢复慢了）、改进项（带负责人与期限）。**改进行动落在系统上而非"下次注意"上**——与第 41 章的发布演练、本章的告警闭环衔接。
- 易错点：仪表盘堆满 30 张图（没人看得过来）；告警不带跳转链接（响应从"找入口"开始）；事故期间全员围观不改代码却没人对外同步；复盘变成追责会（下次没人敢上报问题）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.List;

public class DashboardIncident {
    record Panel(String question, String charts, String source) {
    }

    static void dashboardDesign() {
        System.out.println("【order 服务的三张仪表盘】");
        List<Panel> panels = List.of(
                new Panel("服务整体健康吗？",
                        "SLO 状态灯 + 预算余额 + 四信号 + 依赖健康", "服务总览（值班首屏）"),
                new Panel("下单链路哪一环慢？",
                        "链路各跳 P99 柱状对比 + 慢请求 trace 抽样", "链路视图"),
                new Panel("这次发布效果如何？",
                        "version 维度错误率/延迟对比 + 金丝雀进度", "版本对比"));
        for (Panel p : panels) {
            System.out.printf("  问题：%s%n    图表：%s  来源：%s%n", p.question(), p.charts(), p.source());
        }
        System.out.println("  每张图回答一个问题；回答不了问题的图删掉");
        System.out.println();
    }

    static void incidentTimeline() {
        System.out.println("【一次 P1 事故的响应时间线】");
        record Event(String clock, String role, String action) {
        }
        List<Event> events = List.of(
                new Event("20:03", "告警", "P1：下单成功率 97%（阈值99%）"),
                new Event("20:05", "值班OML", "ack + 打开服务总览：错误集中在下单，P99 正常"),
                new Event("20:08", "IC", "定级 P1，建立作战群，CL 开始对外同步"),
                new Event("20:12", "OML", "日志按 trace_id 检索：库存服务连接超时为主"),
                new Event("20:18", "OML", "库存服务确认连接池耗尽 -> 扩容 + 重启"),
                new Event("20:24", "IC", "指标恢复，观察 15 分钟后关闭"),
                new Event("20:40", "CL", "对外恢复通知；复盘排期 48h 内"));
        for (Event e : events) {
            System.out.printf("  %s [%-7s] %s%n", e.clock(), e.role(), e.action());
        }
        System.out.println("  总时长 21 分钟；动线顺畅 = 告警带链接 + 仪表盘回答问题");
        System.out.println();
    }

    static void postmortem() {
        System.out.println("【复盘模板：无责文化 + 系统化改进】");
        List<String> sections = List.of(
                "影响面：多少用户/多少金额/持续时长",
                "时间线：告警->ack->定位->恢复 的真实时间戳",
                "根因链：连接池耗尽 <- 慢查询 <- 缺索引（问三层为什么）",
                "为什么告警晚了 3 分钟 / 为什么扩容用了 6 分钟",
                "改进项：[系统] 慢查询进 CI 检查；[系统] 连接池饱和度告警提前到 80%",
                "每条改进：负责人 + 截止时间 + 验证方式");
        for (String s : sections) {
            System.out.println("  - " + s);
        }
        System.out.println("  检验：三个月后同类问题是否复发 —— 复盘有效的唯一标准");
    }

    public static void main(String[] args) {
        dashboardDesign();
        incidentTimeline();
        postmortem();
    }
}
```

**课后练习**
1. 为你的核心服务做一张"服务总览"仪表盘（哪怕手工表格），实现 30 秒判断健康状态的结构。
2. 给团队现有告警补齐"跳转链接"（告警 -> 仪表盘 -> 日志查询），用一次模拟告警走完整动线并记录耗时。
3. 思考题：为什么复盘的改进行动必须落在系统上而不是"下次注意"上？用第 41 章演练机制与本章告警闭环解释"系统化改进"的可持续性。

---

### 42-11 综合案例：一次线上事故的可观测复盘

**知识点**
- **用三支柱 + 剖析完整复盘一次真实形态的事故**：大促期间下单 P99 从 180ms 涨到 2.1s、错误率 0.3%->4%。按"告警 -> 指标定位 -> 日志收窄 -> 链路锁定 -> 剖析定根因 -> 修复 -> 复盘改进"的完整路径走一遍，**本章之前每一课的工具都在这条路径上各就各位**。
- **指标层的判断**：错误率与延迟同时劣化但 CPU 正常 -> 排除容量问题；按 route 维度切片 -> 只有 /order 劣化；按 version 维度 -> 新旧版本都慢 -> **排除新版本引入**（版本维度指标在 41-07 埋下的价值）。方向收敛到"共享下游"。
- **日志层的收窄**：按 trace_id 抽慢请求 -> 顶部模式是 stock 服务 db.update 慢；按 orderId 聚合 -> 慢请求集中在"秒杀商品"。**日志的分组统计（而非逐条阅读）给出模式**。
- **链路层的锁定**：慢 trace 的 span 树显示 stock.deduct 内 db.update 占 92%；对比正常 trace -> SQL 形态差异（`SELECT ... FOR UPDATE` 的行锁竞争）。
- **剖析层的根因**：JFR/锁剖析显示热点在单行 update；结合 DB 侧 `SHOW PROCESSLIST` -> **热点行锁**：秒杀商品的库存行被全部请求串行竞争。第 32 章（CAS 预扣）与第 39 章（分片）的方案在此会师。
- **复盘产出**：改进项落在系统——库存扣减改 Redis 预扣（第 32 章方案）、慢 SQL 进 CI 检查、连接池饱和度告警提前、大促预案增加热点商品压测。**一次事故让整个可观测与架构体系进化一档**。
- 易错点：复盘只写"库存服务慢了"没有根因链（下次复发）；修复只改现象（加超时）不改结构（行锁竞争仍在）；事故期间先优化后恢复（正确顺序：先恢复业务——切流/降级，再优化）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class IncidentCase {
    record Step(int no, String pillar, String question, String finding) {
    }

    static void investigation() {
        System.out.println("【事故：大促下单 P99 180ms -> 2.1s，错误率 4%】");
        List<Step> steps = List.of(
                new Step(1, "告警", "要不要介入？", "P1：成功率 96%（阈值 99%），20:31 触发"),
                new Step(2, "指标", "哪里坏了？", "按 route 切片：仅 /order 劣化；按 version：新旧都慢 -> 共享下游问题"),
                new Step(3, "指标", "资源是瓶颈吗？", "CPU 45%、连接池 30% 正常 -> 排除容量，指向锁/等待"),
                new Step(4, "日志", "什么模式？", "慢 trace 的 top 模式：stock db.update；慢请求集中在秒杀商品"),
                new Step(5, "链路", "慢在哪一跳？", "span 树：stock.deduct 内 db.update 占 92%；SQL 带 FOR UPDATE"),
                new Step(6, "剖析", "根因是什么？", "锁剖析热点：单行 update 行锁竞争（热点商品串行化）"),
                new Step(7, "处置", "先恢复还是先优化？", "先恢复：秒杀商品限流 + 降级非秒杀链路 -> P99 回落 300ms"));
        for (Step s : steps) {
            System.out.printf("  步骤%d [%-4s] %-14s %s%n", s.no(), s.pillar(), s.question(), s.finding());
        }
        System.out.println("  恢复时间 18 分钟；每一步的问题都有数据回答");
        System.out.println();
    }

    static void rootCauseChain() {
        System.out.println("【根因链：连问三层为什么】");
        List<String> chain = List.of(
                "为什么慢？秒杀商品的库存行被 FOR UPDATE 串行竞争（热点行锁）",
                "为什么热点行锁？库存扣减直接打 DB，单商品单行成为全局串行点",
                "为什么没有预案？大促预案压测用的是均匀商品分布，热点商品场景缺失");
        for (int i = 0; i < chain.size(); i++) {
            System.out.printf("  为什么#%d：%s%n", i + 1, chain.get(i));
        }
        System.out.println("  根因结论：架构级（DB 行锁扛热点），修复 = Redis 预扣 + 分段库存（32/39 章方案）");
        System.out.println();
    }

    static void improvements() {
        System.out.println("【改进项：落在系统上，带负责人与验证】");
        record Item(String action, String owner, String verify) {
        }
        List<Item> items = List.of(
                new Item("库存扣减改 Redis 预扣 + 异步落库", "库存组", "压测 10 万 QPS 无行锁等待"),
                new Item("秒杀链路热点商品专项压测进大促预案", "测试组", "预案清单新增用例"),
                new Item("慢 SQL（FOR UPDATE on 热点）进 CI 检查", "平台组", "CI 规则上线"),
                new Item("锁等待剖析接入告警联动", "平台组", "下次 CPU 事故 5 分钟出热点"));
        for (Item i : items) {
            System.out.printf("  [ ] %-34s 负责人=%-6s 验证=%s%n", i.action(), i.owner(), i.verify());
        }
        System.out.println("  复盘有效的唯一标准：三个月后同类问题不再复发");
    }

    public static void main(String[] args) {
        investigation();
        rootCauseChain();
        improvements();
    }
}
```

**课后练习**
1. 把你经历过的（或虚构的）一次事故按本章七步路径完整复盘，每一步标注"用了哪个可观测工具、看到了什么数据"。
2. 针对一次"CPU 正常但接口慢"的模拟事故，写出指标/日志/链路/剖析四层的排查脚本（查什么、怎么判断）。
3. 思考题：为什么事故处置的正确顺序是"先恢复业务再优化结构"？从用户影响面与修复风险两个角度分析本章第 7 步的决策。
