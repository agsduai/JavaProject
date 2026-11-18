### 43-01 流与批的分界

**知识点**
- **批处理是"数据先齐再算"，流处理是"数据边来边算"**：日报/月报跑批（Hive/Spark SQL）等 24 点切分区；实时大屏/实时风控（Flink）每秒处理增量。**分界的本质不是速度，是有无"数据终结点"**。
- **Lambda 架构的历史包袱**：早期实时框架不保证正确性，于是"批出准数 + 流出快数"两套代码双跑再对账——同样的逻辑写两遍是维护地狱。**现代流处理（Flink 的 Exactly-Once + 事件时间）让"流批一体"成为可能**：一套代码，流跑实时、批跑历史回放。
- **什么时候必须流处理**：延迟要求秒级以内（风控拦截要在支付前）、持续聚合（大盘每秒刷新）、CDC 增量同步（binlog 不能等天级）。**能跑批的就别上流**——流系统的运维复杂度（状态、水位线、反压）远高于批。
- **流处理的心智模型转变**：数据无界（没有"跑完"）、计算常驻（7×24 进程）、故障要自恢复（状态可重建）、时间要显式处理（事件时间 vs 处理时间，43-02）。**把批思维带进流系统，第一个坑就是"为什么数据对不上"**。
- **流的三个可靠性等级**：At-most-once（可能丢）、At-least-once（可能重）、Exactly-once（不丢不重）。等级越高成本越高，**按业务选等级而不是默认最高**（43-06 细讲）。
- **Java 工程师的位置**：实时计算的主力框架 Flink 是 JVM 生态，Java 是一等公民。业务侧更多是"把逻辑交付给流系统"（UDF、规则引擎、特征计算）与"消费流的结果"。
- 易错点：把实时当"跑得快的批"（忽略无界与常驻）；两套代码双跑不对账（Lambda 架构的隐性成本）；所有场景都上流处理（运维复杂度爆炸）；忽略流系统的常驻成本（7×24 集群哪怕没有数据也在烧钱）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class StreamVsBatch {
    record Order(long id, long ts, double amount) {
    }

    static void batchMindset() {
        System.out.println("【批处理：数据先齐再算】");
        List<Order> dayOrders = List.of(
                new Order(1, dayStart() + 100, 69.0),
                new Order(2, dayStart() + 200, 199.0),
                new Order(3, dayStart() + 300, 29.0));
        double total = dayOrders.stream().mapToDouble(Order::amount).sum();
        System.out.printf("  23:59:59 后跑批：当日 %d 单，GMV %.1f 元 —— 输入完整，结果确定%n",
                dayOrders.size(), total);
        System.out.println("  特点：输入有界、可重跑、延迟天级、运维简单");
        System.out.println();
    }

    static long dayStart() {
        return 1763347200000L;
    }

    static void streamMindset() {
        System.out.println("【流处理：数据边来边算】");
        List<Order> arriving = List.of(
                new Order(4, dayStart() + 400, 59.0),
                new Order(5, dayStart() + 500, 129.0));
        double running = 297.0;   // 之前累计
        for (Order o : arriving) {
            running += o.amount();
            System.out.printf("  t+%dms 到达订单 %d -> 实时 GMV 刷新为 %.1f 元%n",
                    o.ts() - dayStart(), o.id(), running);
        }
        System.out.println("  特点：输入无界、计算常驻 7×24、秒级输出、要管状态与容错");
        System.out.println("  关键问题：这条“实时 GMV”到 24 点会和批跑的一致吗？（43-02/06 回答）");
        System.out.println();
    }

    static void whenToStream() {
        System.out.println("【决策：必须流 vs 应该批】");
        String[][] rows = {
                {"支付前拦截风险", "流（秒级）", "批等不到这笔交易发生"},
                {"实时大屏/监控", "流", "展示要求秒级刷新"},
                {"binlog 增量同步", "流（CDC）", "天级批无法满足一致性窗口"},
                {"日报/月报/对账", "批", "延迟可接受，批更简单可靠"},
                {"历史特征回填", "批", "输入是有界的历史数据"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-14s %-12s %s%n", r[0], r[1], r[2]);
        }
        System.out.println();
    }

    static void lambdaCost() {
        System.out.println("【Lambda 架构的双份成本】");
        Map<String, String> dup = new LinkedHashMap<>();
        dup.put("业务逻辑", "批 SQL 一份 + 流代码一份（同逻辑两实现）");
        dup.put("对账", "每日批跑完与流结果 diff，差异排障");
        dup.put("修复", "逻辑变更要改两处，先后上线节奏不一致就出差异");
        for (Map.Entry<String, String> e : dup.entrySet()) {
            System.out.printf("  %-6s %s%n", e.getKey(), e.getValue());
        }
        System.out.println("  现代解法：流批一体（同一套 API）+ 事件时间 + Exactly-Once");
        System.out.println("  流结果可信后，批只负责历史回填与审计");
    }

    public static void main(String[] args) {
        batchMindset();
        streamMindset();
        whenToStream();
        lambdaCost();
    }
}
```

**课后练习**
1. 为你的业务列一张"流 vs 批"决策表（≥6 个场景），标注选择与理由，找出至少一个"能批就别流"的场景。
2. 实现"流式 GMV"模拟器：逐条到达订单实时累加，同时按天分桶，验证 24 点切分时的边界处理。
3. 思考题：为什么说"把批思维带进流系统第一个坑就是数据对不上"？列出批思维在流系统里失效的三个假设。

---

### 43-02 时间语义：事件时间与处理时间

**知识点**
- **同一笔订单有三个时间**：**事件时间**（业务发生时刻，订单里的 create_time）、**处理时间**（流系统收到它的机器时钟）、**摄入时间**（进入框架的时刻）。**用哪个时间分桶，决定了统计口径**。
- **处理时间的陷阱在乱序与延迟**：网络抖动让订单 B（11:59:58 生成）比订单 A（11:59:59 生成）晚到——按处理时间分桶，B 掉进了第二天，GMV 在 24 点边界处永远对不上批结果。**事件时间按业务发生时刻分桶，乱序不改变归属**。
- **事件时间的前提是数据带可信时间戳**：源头的钟不准（客户端时间可改）、时钟回拨（39-07 的雪花时钟回拨同一问题）、字段缺失——**时间戳治理是流处理的隐形工程**：服务端打点为主、客户端时间为参考、格式统一 UTC 毫秒。
- **两种语义的适用场景**：处理时间适合"现在系统怎么样"（监控类，低延迟优先）；事件时间适合"业务到底发生了什么"（计费/对账/报表，正确性优先）。**钱相关的统计必须事件时间**。
- **迟到数据的两种命运**：事件时间窗口已关闭后到达的数据——丢弃（丢 GMV）或允许迟到（更新窗口结果，需要窗口能重算，43-04 水位线控制）。**"允许迟到多久"是业务决策**：电商订单通常允许 5~15 分钟。
- **回溯（backfill）的正确性**：事件时间让"重放昨天流量"得到与当时一致的窗口结果——这是流批一体的根基；处理时间重放会得到完全不同的分桶。
- 易错点：用处理时间算 GMV（跨天边界永远对不上）；信任客户端时间戳（可伪造可漂移）；窗口关闭=数据丢失（没设计迟到策略）；时间字段混用秒/毫秒（差三个数量级的分桶）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TimeSemantics {
    record Order(long id, long eventTs, long processTs, double amount) {
        String eventDay() {
            return eventTs >= DAY2 ? "11-18" : "11-17";
        }

        String processDay() {
            return processTs >= DAY2 ? "11-18" : "11-17";
        }
    }

    static final long DAY2 = 1763433600000L;   // 11-18 00:00

    static void disorderDemo() {
        System.out.println("【跨天边界的两笔订单：两种语义的不同归属】");
        List<Order> orders = List.of(
                new Order(101, DAY2 - 1000, DAY2 + 800, 199.0),    // 23:59:59 生成，晚到 0.8s
                new Order(102, DAY2 + 500, DAY2 + 900, 69.0));     // 00:00:00.5 生成
        for (Order o : orders) {
            System.out.printf("  订单%d 事件时间=%s 处理时间=%s%n",
                    o.id(), o.eventDay(), o.processDay());
        }
        System.out.println("  订单 101 事件时间属 11-17，处理时间却落到 11-18");
        System.out.println("  处理时间 GMV：11-17 少 199 元 —— 与批结果永远差这一笔");
        System.out.println("  事件时间 GMV：与批完全一致（按业务发生时刻归属）");
        System.out.println();
    }

    static void timestampGovernance() {
        System.out.println("【时间戳治理：事件时间的前提】");
        String[][] rows = {
                {"客户端打点", "钟不准/可伪造", "仅作参考，服务端打点为准"},
                {"服务端打点", "统一 NTP，相对可信", "主来源（网关入口打点）"},
                {"时钟回拨", "短暂倒退", "单调化处理（39-07 同款方案）"},
                {"单位混乱", "秒 vs 毫秒", "统一 UTC 毫秒，schema 声明"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-10s %-16s %s%n", r[0], r[1], r[2]);
        }
        System.out.println();
    }

    static void latePolicy() {
        System.out.println("【迟到数据的两种命运：业务决策】");
        record LateOrder(long id, int lateSec, String decision, String reason) {
        }
        List<LateOrder> lates = List.of(
                new LateOrder(201, 30, "允许迟到：更新窗口", "GMV 精确性优先"),
                new LateOrder(202, 300, "允许迟到（上限 5 分钟）", "支付回调链路可能慢"),
                new LateOrder(203, 3600, "丢弃 -> 进旁路统计", "一小时前的订单不该影响实时大屏"));
        for (LateOrder l : lates) {
            System.out.printf("  订单%d 迟到 %ds -> %s（%s）%n",
                    l.id(), l.lateSec(), l.decision(), l.reason());
        }
        System.out.println("  “允许迟到多久”写进需求文档，不是技术拍脑袋");
        System.out.println();
    }

    static void replayValue() {
        System.out.println("【事件时间的回放价值：重放=复现】");
        System.out.println("  把昨天的原始事件流按事件时间重放：");
        System.out.println("  事件时间窗口 -> 得到与昨天完全一致的统计（可用于修复错误统计）");
        System.out.println("  处理时间窗口 -> 得到“按今天重放时刻”的统计（毫无用处）");
        System.out.println("  结论：事件时间是流批一体与可修复性的根基");
    }

    public static void main(String[] args) {
        disorderDemo();
        timestampGovernance();
        latePolicy();
        replayValue();
    }
}
```

**课后练习**
1. 用示例数据扩展成 1000 单模拟：按两种语义分别统计两天的 GMV，量化处理时间在边界处的误差。
2. 为你的业务制定时间戳治理规范：主时钟源、格式、回拨处理、缺失兜底，并检查现有数据源是否达标。
3. 思考题：为什么"钱相关的统计必须事件时间"？从对账一致性与重放修复两个角度推导。

---

### 43-03 窗口：滚动、滑动与会话

**知识点**
- **窗口是把无界流切成可聚合片段的机制**，三种基础形态：**滚动窗口**（tumbling，固定长度不重叠：每分钟 GMV）、**滑动窗口**（sliding，固定长度带步长重叠：近 5 分钟 QPS 每 10 秒刷新一次）、**会话窗口**（session，按活动间隔动态划分：用户一次浏览会话）。
- **窗口的关键参数语义**：滚动窗口只有 size；滑动窗口有 size（统计范围）与 slide（刷新频率），slide < size 时同一事件归属多个窗口（被多次统计）；会话窗口的参数是**间隔 gap**（gap 内无活动则窗口关闭）。
- **窗口归属由事件时间决定**（43-02 的直接应用）：事件落在 `[窗口起点, 终点)`。**窗口是"开区间闭区间"的口径问题**，与批 SQL 的 `GROUP BY date_trunc` 完全对齐——对不上批就是口径错了。
- **迟到与窗口生命周期**：窗口在其事件时间终点 + 水位线（43-04）到达后触发计算；允许迟到期间窗口保持"可更新"状态。**窗口状态的存在时间 = size + 迟到容忍**，直接影响状态存储量。
- **会话窗口的实现成本**：每个 key 维护"当前会话"状态，gap 内合并、gap 外新开——状态按活跃用户数计。大促日活跃千万级时会话窗口的状态量要提前估算。
- **窗口与下游的契约**：窗口结果带（窗口起点、终点、水位线）元数据输出——下游要知道"这个 11:59 的数字是几点几分最终确定的"。**不带窗口元数据的输出无法被信任**。
- 易错点：滑动窗口理解成"每 slide 秒重新统计全部"（事件重复计入多个窗口是设计而非 bug，但下游消费要明确）；会话 gap 设得太小（会话被切碎）；窗口边界口径与批 SQL 不一致（对不上账）；窗口结果没有"最终/临时"标记（下游把临时值当最终值）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class WindowsDemo {
    record Event(long ts, double amount) {
    }

    /** 事件流（相对秒） */
    static final List<Event> EVENTS = List.of(
            new Event(5, 60), new Event(35, 80), new Event(65, 40),
            new Event(95, 100), new Event(125, 70), new Event(155, 30));

    static void tumbling() {
        System.out.println("【滚动窗口：size=60s，每分钟 GMV】");
        TreeMap<Long, Double> windows = new TreeMap<>();
        for (Event e : EVENTS) {
            long win = e.ts() / 60;          // 归属哪一分钟
            windows.merge(win, e.amount(), Double::sum);
        }
        for (Map.Entry<Long, Double> w : windows.entrySet()) {
            System.out.printf("  [min%d, min%d)  GMV=%.0f 元%n", w.getKey(), w.getKey() + 1, w.getValue());
        }
        System.out.println("  特点：不重叠、每个事件只属一个窗口；等价于批的 GROUP BY 分钟");
        System.out.println();
    }

    static void sliding() {
        System.out.println("【滑动窗口：size=120s，slide=60s（近2分钟GMV每分钟刷新）】");
        for (long slide = 0; slide <= 2; slide++) {
            long start = slide * 60, end = start + 120;
            double sum = EVENTS.stream()
                    .filter(e -> e.ts() >= start && e.ts() < end)
                    .mapToDouble(Event::amount).sum();
            System.out.printf("  窗口[%3d, %3d) 当前累计 %.0f 元（刷新于 slide=%d）%n",
                    start, end, sum, slide);
        }
        System.out.println("  事件 ts=65 同时落在窗口[0,120) 与 [60,180)：被统计两次是设计使然");
        System.out.println("  下游消费契约：滑动结果用于“当前水平”，不用于累加求和");
        System.out.println();
    }

    static void sessionWindow() {
        System.out.println("【会话窗口：gap=20s，按用户活动划分】");
        record Click(long userId, long ts) {
        }
        List<Click> clicks = List.of(
                new Click(1, 0), new Click(1, 10), new Click(1, 28),   // 10<20 合并；28-10=18<20 合并
                new Click(1, 60),                                       // 60-28=32>20 新会话
                new Click(2, 5), new Click(2, 90));                     // gap 85 -> 各自成会话
        Map<Long, List<Long>> sessions = new LinkedHashMap<>();
        for (Click c : clicks) {
            List<Long> list = sessions.computeIfAbsent(c.userId(), k -> new ArrayList<>());
            if (!list.isEmpty() && c.ts() - list.get(list.size() - 1) > 20) {
                list = new ArrayList<>();
                sessions.put(c.userId(), list);
            }
            list.add(c.ts());
        }
        for (Map.Entry<Long, List<Long>> e : sessions.entrySet()) {
            System.out.printf("  用户%d 会话数=%d：%s%n", e.getKey(),
                    e.getValue().size(), e.getValue());
        }
        System.out.println("  参数是间隔而非长度：会话边界由数据自己决定");
        System.out.println();
    }

    static void lifecycle() {
        System.out.println("【窗口生命周期：状态存在多久】");
        long sizeSec = 60, allowedLateness = 300;
        System.out.printf("  size=%ds + 允许迟到=%ds -> 每个窗口状态存活 %d 秒%n",
                sizeSec, allowedLateness, sizeSec + allowedLateness);
        System.out.println("  状态量估算：活跃窗口数 = 360s 内的事件 key 数（大促需提前算）");
        System.out.println("  输出契约：结果带 (窗口起止, 是否最终) 元数据，下游据此消费");
    }

    public static void main(String[] args) {
        tumbling();
        sliding();
        sessionWindow();
        lifecycle();
    }
}
```

**课后练习**
1. 用示例数据实现三种窗口，验证"滑动窗口的事件重复归属"与"会话窗口的动态合并"行为，画出每个事件的归属矩阵。
2. 为"近 5 分钟错误率每 30 秒刷新"设计窗口参数（size/slide），并说明为什么结果不能下游累加。
3. 思考题：为什么窗口输出必须带"是否最终"标记？结合允许迟到机制说明下游把临时值当最终值的业务后果。

---

### 43-04 水位线与乱序处理

**知识点**
- **水位线（watermark）回答"什么时候窗口可以关"**：事件时间窗口不能等全部数据（数据无界），需要一个时间进度信号——"水位线 = T 表示：T 之前的事件应该都到了（除非超过容忍度）"。**水位线是流系统对"数据完备性"的乐观声明**。
- **水位线的生成**：通常 = 当前最大事件时间 - 有界乱序容忍度（如 5 秒）。事件到达会推进水位线；**某个分区/源停滞会拖住全局水位线**（所有上游的最小值）——这是"水位线卡住"故障的根源（空闲分区要发 idle 标记）。
- **水位线触发窗口**：水位线越过窗口终点（终点 + 允许迟到）时触发计算并关闭窗口。**容忍度是"正确性与延迟"的交换**：容忍 5s -> 结果最多晚 5s+窗口长，但 5s 内的乱序都能正确归属；容忍 0 -> 最快但乱序全部迟到丢弃。
- **迟到数据的旁路（side output）**：超过允许迟到的数据不进窗口，写入旁路流单独统计（"迟到丢弃数"本身是监控指标——突然升高说明上游时钟或网络出问题）。
- **水位线的正确性边界**：它不是"保证不会有更早的数据"，而是"声明不会有"。事件时间 < 当前水位线的数据就是迟到数据。**水位线本质是赌"乱序有界"**，赌输的部分走旁路。
- **幂等更新替代完美水位线**：下游支持重算（窗口结果带版本）时，水位线可以激进、迟到数据重发更新结果——**"可以修正的输出"比"完美的水位线"更工程化**（与 42 章窗口"是否最终"标记衔接）。
- 易错点：水位线容忍度拍脑袋（不测真实乱序分布）；空闲分区拖住全局水位线（窗口迟迟不触发，表现为"实时大屏卡住"）；把水位线当精确时钟（它是启发式）；迟到数据静默丢弃（旁路统计缺失，丢数没人知道）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.List;

public class WatermarkDemo {
    record Arrival(String order, long eventTs, long arrivalSec) {
    }

    /** 乱序容忍 5s；事件相对时间（秒） */
    static final long TOLERANCE = 5;

    static void watermarkWalkthrough() {
        System.out.println("【水位线推进：最大事件时间 - 容忍度】");
        List<Arrival> arrivals = List.of(
                new Arrival("o1", 100, 100),   // 正常到达
                new Arrival("o2", 108, 109),
                new Arrival("o3", 104, 111),   // 乱序：比 o2 的 108 小
                new Arrival("o4", 120, 120));
        for (Arrival a : arrivals) {
            System.out.printf("  %s 到达：eventTs=%d -> WM = %d - %d = %d%n",
                    a.order(), a.eventTs(), Math.max(a.eventTs(), 0), TOLERANCE,
                    Math.max(a.eventTs() - TOLERANCE, 0));
        }
        System.out.println("  窗口 [95,105) 在 WM >= 105+迟到容忍 时触发：o3(eventTs=104) 赶上了");
        System.out.println("  若容忍度=0：o3 在 WM=108 时才到 -> 已超窗口终点 -> 迟到");
        System.out.println();
    }

    static void idleSource() {
        System.out.println("【空闲分区拖住全局水位线】");
        List<String[]> sources = List.of(
                new String[]{"partition-0", "maxEventTs=200", "活跃"},
                new String[]{"partition-1", "maxEventTs=198", "活跃"},
                new String[]{"partition-2", "maxEventTs=180（该分区没流量了）", "空闲"});
        for (String[] s : sources) {
            System.out.printf("  %-12s %-30s %s%n", s[0], s[1], s[2]);
        }
        System.out.println("  全局 WM = min(195, 193, 175) = 175 <- 被空闲分区拖住");
        System.out.println("  现象：大屏“卡住”不刷新，窗口迟迟不关闭");
        System.out.println("  对策：源空闲检测 + idle 标记（不参与 WM 取 min）");
        System.out.println();
    }

    static void sideOutput() {
        System.out.println("【迟到旁路：丢弃也要被看见】");
        record Late(String order, int lateSec, String fate) {
        }
        List<Late> lates = List.of(
                new Late("o9", 8, "允许迟到（5s）内：更新窗口结果"),
                new Late("o10", 60, "超限：写入旁路流，计数+1"));
        for (Late l : lates) {
            System.out.printf("  %s 迟到 %ds -> %s%n", l.order(), l.lateSec(), l.fate());
        }
        System.out.println("  旁路指标 late_dropped_count 突增 = 上游时钟/网络异常的信号");
        System.out.println("  静默丢弃 = 丢数没人知道 —— 旁路统计必须有");
    }

    public static void main(String[] args) {
        watermarkWalkthrough();
        idleSource();
        sideOutput();
    }
}
```

**课后练习**
1. 生成一段带乱序的事件流（乱序 0~15s 随机），实测不同容忍度（0/5/10s）下的"正确归属率 vs 结果延迟"，画出交换曲线。
2. 模拟"空闲分区拖住水位线"：三个源一个停止发数据，观察窗口触发延迟，然后实现 idle 标记修复。
3. 思考题：为什么说水位线是"赌乱序有界"？结合旁路统计说明"赌输的部分"如何被工程化兜底。
