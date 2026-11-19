# 第80章 性能压测与Profiling

性能问题最怕"凭感觉"：平均延迟很好看，P99 已经爆炸；压测报告很漂亮，上线就雪崩。本章把测量做成一套可复用的方法：指标、基准、分位、模型、采样、瓶颈、容量、门禁。本章按「性能方法论 → 基准测试与JMH思想 → 延迟分布 → 协调遗漏 → CPU火焰图 → GC与内存 → 锁与线程 → 全链路压测 → 容量决策 → 性能回归门禁 → 双十一压测案例」的顺序讲。所有示例仍是纯 JDK：USE/RED分析、预热与死代码消除、直方图分位、闭环/开放模型对比、栈采样火焰图、GC日志解析、线程dump锁热点、影子流量染色、利特尔法则与排队曲线、5次运行中位数门禁、阶梯加压与安全承载全部真实运行。

### 80-01 性能工程方法论

**知识点**
- **性能的三个维度**：**吞吐（QPS/TPS）**、**延迟（P50/P99/P999）**、**错误率**——**三者必须一起看**：只提吞吐会把延迟打爆，只看平均延迟会掩盖长尾（80-03）。
- **USE 方法（资源视角）**：**Utilization（使用率）**、**Saturation（饱和度/排队）**、**Errors（错误）**——**对 CPU/内存/磁盘/网络逐个检查**，**饱和比使用率更早暴露瓶颈**。
- **RED 方法（服务视角）**：**Rate（请求速率）**、**Errors（错误率）**、**Duration（延迟分布）**——**微服务/接口层用 RED，主机/资源层用 USE**。
- **测量先于优化**：**没有基线就没有"变快"**；**先定位瓶颈（CPU/IO/锁/下游），再改**——**凭感觉优化往往改错地方**（80-05 的火焰图）。
- 与 30/56 章的关系：30 章 JVM 调优、56 章稳定性容量，**本章讲"怎么系统地测量与定位"**——**工具、方法、指标**三件套。
- 易错点：只看平均值（长尾被平均掉）；只压测不监控（不知道瓶颈）；把"CPU 高"当根因（可能是在自旋/GC）；不设错误率阈值（压测把下游打挂）。

**示例代码**
```java
import java.util.LinkedHashMap;
import java.util.Map;

public class PerformanceMethodology {
    record Resource(String name, double utilization, double saturation,
                    long errors, double queueDepth) {
    }

    record ServiceMetric(String name, double qps, double errorRate,
                         double p50Ms, double p99Ms, double p999Ms) {
    }

    static void use(java.util.List<Resource> resources) {
        System.out.println("【USE：资源视角】");
        System.out.printf("  %-8s %-10s %-12s %-8s %-8s %s%n",
                "资源", "使用率", "饱和度", "错误", "排队", "判断");
        for (Resource r : resources) {
            String verdict;
            if (r.errors() > 0) {
                verdict = "错误：先查故障";
            } else if (r.saturation() > 0.8 || r.queueDepth() > 10) {
                verdict = "饱和：瓶颈在这里";
            } else if (r.utilization() > 0.8) {
                verdict = "高使用率：接近瓶颈";
            } else {
                verdict = "正常";
            }
            System.out.printf("  %-8s %8.0f%% %11.0f%% %8d %8.0f %s%n",
                    r.name(), r.utilization() * 100, r.saturation() * 100,
                    r.errors(), r.queueDepth(), verdict);
        }
        System.out.println();
    }

    static void red(java.util.List<ServiceMetric> services) {
        System.out.println("【RED：服务视角】");
        for (ServiceMetric s : services) {
            System.out.printf("  %-10s QPS=%-8.0f 错误率=%-5.2f%% P50=%-6.1f P99=%-7.1f P999=%.1f%n",
                    s.name(), s.qps(), s.errorRate() * 100,
                    s.p50Ms(), s.p99Ms(), s.p999Ms());
        }
        System.out.println();
    }

    public static void main(String[] args) {
        use(java.util.List.of(
                new Resource("CPU", 0.72, 0.30, 0, 2),
                new Resource("内存", 0.85, 0.10, 0, 0),
                new Resource("磁盘", 0.95, 0.90, 0, 45),
                new Resource("网络", 0.40, 0.05, 12, 1)));

        red(java.util.List.of(
                new ServiceMetric("gateway", 12_000, 0.02, 8, 45, 120),
                new ServiceMetric("order", 3_500, 0.10, 20, 180, 900),
                new ServiceMetric("payment", 800, 0.50, 35, 420, 2_000)));

        System.out.println("【定位顺序】");
        String[][] steps = {
                {"1. 看 RED", "哪个接口错误/延迟异常", "先找用户可感知的问题"},
                {"2. 看 USE", "哪个资源饱和", "CPU/磁盘/连接池/线程池"},
                {"3. 看依赖", "下游超时/重试", "重试会放大流量（80-04）"},
                {"4. 看长尾", "P99/P999 与 P50 的差距", "长尾常来自 GC/锁/慢查询"},
        };
        for (String[] s : steps) {
            System.out.printf("  %-10s %-26s %s%n", s[0], s[1], s[2]);
        }

        System.out.println();
        Map<String, String> conclusion = new LinkedHashMap<>();
        conclusion.put("磁盘", "使用率 95% + 排队 45 -> 第一瓶颈");
        conclusion.put("payment", "错误率 0.5% + P999 2s -> 下游/锁问题");
        conclusion.put("CPU", "72% 不是瓶颈，先别扩容");
        System.out.println("【结论】");
        conclusion.forEach((k, v) -> System.out.printf("  %-8s %s%n", k, v));
    }
}
```

**课后练习**
1. 给你的服务画一张 USE 表（CPU/内存/磁盘/网络/连接池），标出饱和项。
2. 用 RED 指标对比三个接口，判断应该先优化哪一个。
3. 思考题：为什么"CPU 使用率高"不一定是瓶颈？从"自旋/GC/系统调用"举反例。

---

### 80-02 基准测试与 JMH 思想

**知识点**
- **微基准测试的三大陷阱**：**没有预热**（JIT 还没编译）、**死代码消除**（结果没被使用，编译器直接删）、**测量噪声**（GC/其他进程）——**JMH 就是为解决这些而生的**。
- **预热（Warmup）**：**JIT 分层编译需要时间**，**前几千次执行可能慢 10 倍**——**必须丢弃预热阶段的数据**，**预热轮数按方法复杂度定**（简单方法 1~2 秒，复杂 3~5 秒）。
- **测量方式**：**固定迭代次数**（测总耗时）或**固定时间**（测吞吐）；**取多次运行的统计**（平均值 + 置信区间），**不是跑一次就下结论**。
- **Blackhole/volatile 消费结果**：**把结果写入 `volatile` 字段或 `Blackhole.consume`**，**防止 JIT 判定"无用代码"而删除**。
- 与 30 章的关系：30 章讲"JVM 性能调优"，**本章讲"怎么科学地测量一段代码"**——**没有可信测量，调优就是玄学**。
- 易错点：不预热（把 JIT 时间算进去）；结果不消费（测了个空）；单次运行（噪声当结论）；在压测线程里做 IO/日志（测的不是目标）。

**示例代码**
```java
public class BenchmarkHarness {
    static volatile long sink;

    /** 被测实现 A：循环累加 */
    static long sumLoop(int n) {
        long sum = 0;
        for (int i = 1; i <= n; i++) {
            sum += i;
        }
        return sum;
    }

    /** 被测实现 B：公式 */
    static long sumFormula(int n) {
        return (long) n * (n + 1) / 2;
    }

    record Result(String name, double nsPerOp, double opsPerSec) {
    }

    static Result measure(String name, int n, int warmupRounds, int measureRounds,
                          int iterations) {
        // 预热：结果必须消费，否则可能被优化掉
        for (int r = 0; r < warmupRounds; r++) {
            for (int i = 0; i < iterations; i++) {
                sink = name.equals("loop") ? sumLoop(n) : sumFormula(n);
            }
        }
        long best = Long.MAX_VALUE;
        long total = 0;
        for (int r = 0; r < measureRounds; r++) {
            long t0 = System.nanoTime();
            for (int i = 0; i < iterations; i++) {
                sink = name.equals("loop") ? sumLoop(n) : sumFormula(n);
            }
            long cost = System.nanoTime() - t0;
            best = Math.min(best, cost);
            total += cost;
        }
        double avgNs = (double) total / measureRounds / iterations;
        double bestNs = (double) best / iterations;
        System.out.printf("  %-8s 平均=%8.2f ns/op 最快=%8.2f ns/op 吞吐=%12.0f ops/s%n",
                name, avgNs, bestNs, 1_000_000_000.0 / bestNs);
        return new Result(name, bestNs, 1_000_000_000.0 / bestNs);
    }

    public static void main(String[] args) {
        int n = 1_000;
        int iterations = 200_000;

        System.out.println("【预热前后对比（loop）】");
        long coldStart = System.nanoTime();
        for (int i = 0; i < 1000; i++) {
            sink = sumLoop(n);
        }
        double cold = (double) (System.nanoTime() - coldStart) / 1000;
        System.out.printf("  冷启动（前 1000 次）：%.1f ns/op%n", cold);

        System.out.println();
        System.out.println("【正式测量（预热 3 轮，测量 5 轮）】");
        Result loop = measure("loop", n, 3, 5, iterations);
        Result formula = measure("formula", n, 3, 5, iterations);

        System.out.println();
        System.out.printf("  公式比循环快 %.1f 倍%n", loop.nsPerOp() / formula.nsPerOp());

        System.out.println();
        System.out.println("【陷阱演示】");
        System.out.println("  1) 不预热：冷启动数据混入，结论偏慢");
        System.out.println("  2) 不消费结果：JIT 可能把整个循环删掉，测出 0 ns");
        System.out.println("  3) 单次运行：GC/调度噪声大，至少 5 轮取中位数/最优");
        System.out.println("  4) 在计时区间做日志/IO：测到的是 IO 不是算法");

        System.out.println();
        System.out.println("【JMH 对应】@Warmup / @Measurement / @Fork / Blackhole.consume");
        System.out.println("  生产基准：用 JMH，不要手写 nanoTime（本章手写只为讲原理）");
    }
}
```

**课后练习**
1. 手写一个基准测试：预热 3 轮、测量 5 轮，对比两种实现的 ns/op。
2. 故意去掉"结果消费"，观察测试结果是否异常（可能接近 0），解释死代码消除。
3. 思考题：为什么"单次基准测试"不可信？从 JIT、GC、CPU 调度噪声分析。

---

### 80-03 延迟分布与分位数

**知识点**
- **平均值是延迟的"谎言"**：**99 个请求 10ms、1 个请求 10s，平均只有 110ms**，**但那个用户等了 10 秒**——**必须看分位数（P50/P95/P99/P999）**。
- **分位数的含义**：**P99 = 99% 的请求快于该值**——**P99 高说明长尾严重**；**P999 用于核心链路**（支付/登录）——**不同业务选不同分位**。
- **直方图（Histogram）**：**把延迟分桶统计**，**可算任意分位、可跨实例聚合**——**生产用直方图（Prometheus Histogram），不要用平均**。
- **长尾的常见来源**：**GC 停顿、锁等待、慢查询、下游超时重试、冷缓存**——**P99 优化要针对长尾，不是优化平均值**（80-05 的火焰图）。
- 与 42 章的关系：42 章可观测性讲"Histogram 分桶算分位"，**本章讲"为什么必须看分位、怎么看"**。
- 易错点：SLO 用平均延迟（掩盖长尾）；分位数跨实例"平均"（不能平均分位数，要合并直方图）；只看 P99 不看 P999；采样率过低（长尾样本不足）。

**示例代码**
```java
import java.util.Arrays;

public class LatencyDistribution {
    static int percentile(int[] sorted, double p) {
        int idx = (int) Math.ceil(p / 100.0 * sorted.length) - 1;
        idx = Math.max(0, Math.min(sorted.length - 1, idx));
        return sorted[idx];
    }

    static void histogram(int[] sorted, int bucketMs) {
        System.out.println("【直方图（桶宽 " + bucketMs + "ms）】");
        int max = sorted[sorted.length - 1];
        int buckets = max / bucketMs + 1;
        int[] counts = new int[buckets];
        for (int v : sorted) {
            counts[v / bucketMs]++;
        }
        for (int i = 0; i < buckets; i++) {
            if (counts[i] == 0) {
                continue;
            }
            System.out.printf("  %4d~%4dms %5d %s%n", i * bucketMs,
                    (i + 1) * bucketMs - 1, counts[i], "#".repeat(Math.min(60, counts[i])));
        }
    }

    public static void main(String[] args) {
        int[] latencies = new int[1000];
        Arrays.fill(latencies, 0, 990, 10);          // 99% 请求 10ms
        for (int i = 990; i < 1000; i++) {
            latencies[i] = 10_000;                   // 1% 请求 10s
        }
        Arrays.sort(latencies);

        System.out.println("【样本：990 个 10ms + 10 个 10s】");
        long sum = 0;
        for (int v : latencies) {
            sum += v;
        }
        double avg = (double) sum / latencies.length;
        System.out.printf("  平均值=%.1fms%n", avg);
        System.out.printf("  P50=%dms P95=%dms P99=%dms P999=%dms MAX=%dms%n",
                percentile(latencies, 50), percentile(latencies, 95),
                percentile(latencies, 99), percentile(latencies, 99.9),
                latencies[latencies.length - 1]);

        System.out.println();
        System.out.println("  平均值看起来很好（110ms），但 1% 用户等了 10 秒");
        System.out.println("  P99.9=10s 才是真实长尾；平均值把长尾稀释了");

        System.out.println();
        histogram(latencies, 1000);

        System.out.println();
        System.out.println("【分位数不能跨实例平均】");
        int[] a = {10, 10, 10, 10, 1000};
        int[] b = {20, 20, 20, 20, 2000};
        System.out.printf("  实例 A P99=%dms，实例 B P99=%dms%n",
                percentile(a, 99), percentile(b, 99));
        System.out.println("  错误做法：(1000+2000)/2=1500ms");
        System.out.println("  正确做法：合并直方图后重新算分位（样本加权）");

        System.out.println();
        System.out.println("【SLO 建议】");
        String[][] slo = {
                {"普通接口", "P99 < 200ms", "允许少量长尾"},
                {"核心接口", "P999 < 500ms", "支付/登录"},
                {"批处理", "P99 < 5s", "吞吐优先"},
        };
        for (String[] s : slo) {
            System.out.printf("  %-8s %-18s %s%n", s[0], s[1], s[2]);
        }
    }
}
```

**课后练习**
1. 构造"99 个快 + 1 个极慢"的样本，对比平均值与 P99，说明平均值为何误导。
2. 实现直方图分桶并计算 P50/P95/P99，验证与排序法结果一致。
3. 思考题：为什么"分位数不能求平均"？从"分位是顺序统计量"分析，并给出正确聚合方式。

---

### 80-04 协调遗漏与开放式模型

**知识点**
- **协调遗漏（Coordinated Omission）**：**压测工具等上一个响应回来才发下一个请求**（闭环模型）——**服务卡住时，工具也停发**，**卡顿期间没有新样本，延迟被严重低估**。
- **闭环 vs 开放模型**：**闭环**（固定并发，响应慢则发得慢）适合"模拟真实用户"；**开放**（固定到达率，按计划发）适合"模拟流量"——**压测要用开放模型或至少修正**。
- **正确度量**：**按"计划发送时间"算延迟**（`完成时间 - 计划时间`），**卡顿期间排队的请求全部计入长尾**——**这样 P99 才会暴露问题**。
- **与 80-03 的联系**：**协调遗漏直接制造"假的好 P99"**——**很多"压测通过、上线就崩"都是这个原因**。
- 与 41 章的关系：41 章金丝雀/容量，**本章讲"压测数据可不可信"**——**不可信的压测比不压更危险**。
- 易错点：用闭环压测算延迟（低估）；不记录"计划时间"（只记开始/结束）；压测工具单机瓶颈（压不上去）；不模拟真实流量分布（全等比例）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CoordinatedOmission {
    static final long SERVICE_MS = 100;
    static final long PAUSE_START = 2_000;
    static final long PAUSE_MS = 5_000;

    static long serviceTime(long now) {
        if (now >= PAUSE_START && now < PAUSE_START + PAUSE_MS) {
            return PAUSE_MS;                          // 卡顿：请求被堵住
        }
        return SERVICE_MS;
    }

    /** 闭环：上一个完成后才发下一个，延迟只算"服务时间" */
    static List<Long> closedLoop(long durationMs) {
        List<Long> latencies = new ArrayList<>();
        long now = 0;
        while (now < durationMs) {
            long cost = serviceTime(now);
            latencies.add(cost);                      // 工具视角：开始到结束
            now += cost;
        }
        return latencies;
    }

    /** 开放：按固定到达率发，延迟 = 完成 - 计划时间 */
    static List<Long> openLoop(long durationMs, long intervalMs) {
        List<Long> latencies = new ArrayList<>();
        long serverFree = 0;
        for (long planned = 0; planned < durationMs; planned += intervalMs) {
            long start = Math.max(planned, serverFree);
            long cost = serviceTime(start);
            long done = start + cost;
            latencies.add(done - planned);            // 包含排队等待
            serverFree = done;
        }
        return latencies;
    }

    static void stats(String label, List<Long> latencies) {
        List<Long> sorted = new ArrayList<>(latencies);
        Collections.sort(sorted);
        double avg = sorted.stream().mapToLong(Long::longValue).average().orElse(0);
        long p99 = sorted.get((int) Math.ceil(sorted.size() * 0.99) - 1);
        long max = sorted.get(sorted.size() - 1);
        System.out.printf("  %-10s 样本=%-4d 平均=%6.0fms P99=%6dms MAX=%6dms%n",
                label, sorted.size(), avg, p99, max);
    }

    public static void main(String[] args) {
        long duration = 20_000;

        System.out.println("【闭环压测（等响应再发）】");
        List<Long> closed = closedLoop(duration);
        stats("closed", closed);

        System.out.println();
        System.out.println("【开放压测（固定 10 req/s）】");
        List<Long> open = openLoop(duration, 100);
        stats("open", open);

        System.out.println();
        System.out.println("【差异原因】");
        System.out.println("  闭环：卡顿 5s 期间工具停发，只有 1 个样本记录 5s 延迟");
        System.out.println("  开放：卡顿期间按计划发了 50 个请求，全部排队 5s");
        System.out.println("  -> 开放模型 P99 才反映真实用户体验");

        System.out.println();
        System.out.println("【修正方法】");
        String[][] fixes = {
                {"记录计划时间", "延迟=完成-计划", "而不是完成-开始"},
                {"开放模型", "固定到达率", "wrk2/JMH 的 throughput 模式"},
                {"补样本", "卡顿期间按理论值补齐", "HdrHistogram 的 recordValueWithExpectedInterval"},
        };
        for (String[] f : fixes) {
            System.out.printf("  %-12s %-24s %s%n", f[0], f[1], f[2]);
        }

        System.out.println();
        System.out.println("【结论】闭环压测适合「模拟并发用户」，算容量要看开放模型/修正后的延迟");
    }
}
```

**课后练习**
1. 模拟"服务卡顿 5 秒"，分别用闭环/开放模型统计 P99，解释差异。
2. 用"完成时间 - 计划时间"修正闭环样本，对比修正前后的 P99。
3. 思考题：为什么"压测通过、上线就崩"常与协调遗漏有关？从"长尾样本缺失"分析。

---

### 80-05 CPU Profiling 与火焰图

**知识点**
- **采样式 Profiler 的原理**：**每隔固定时间（如 10ms）抓一次线程栈**，**统计"栈顶/整条栈"出现的次数**，**次数越多 = CPU 时间越多**——**采样开销低（1%~3%），适合生产**。
- **火焰图的读法**：**横轴是样本占比（不是时间顺序）**、**纵轴是调用深度**、**每个框的宽度 = 该函数及其子调用的占比**——**找"最宽的顶层框"和"没有子调用的宽框（self time）"**。
- **区分 CPU 火焰图与 Wall-clock 火焰图**：**CPU 火焰图只统计"正在跑"的栈**（不包含阻塞）；**Wall 火焰图统计"墙上时间"**（包含等待/IO）——**排查"慢"要看 Wall，排查"CPU 高"要看 CPU**。
- **常见热点**：**序列化/反序列化、正则、字符串拼接、锁竞争、GC（在火焰图外）**——**先看宽框，再看调用链**。
- 与 30/42 章的关系：30 章讲 JVM 指标，42 章讲可观测性，**本章讲"怎么从栈采样定位到具体代码行"**。
- 易错点：只看 CPU 火焰图排查 IO 慢（看不到阻塞）；采样率太低（长尾丢失）；不看 self time（误判"父函数慢"）；在生产全量采样（开销）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class FlameGraph {
    /** 一次采样：从根到叶的栈 */
    record Sample(List<String> stack) {
    }

    static final List<Sample> samples = new ArrayList<>();

    static void sample(String... frames) {
        samples.add(new Sample(List.of(frames)));
    }

    /** 按"函数 -> 出现样本数（含子调用）"聚合 */
    static Map<String, Integer> totalCounts() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Sample s : samples) {
            for (String f : s.stack()) {
                counts.merge(f, 1, Integer::sum);
            }
        }
        return counts;
    }

    /** self time：栈顶函数才计 self */
    static Map<String, Integer> selfCounts() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Sample s : samples) {
            String top = s.stack().get(s.stack().size() - 1);
            counts.merge(top, 1, Integer::sum);
        }
        return counts;
    }

    /** 文本火焰图：缩进表示深度，数字是包含子调用的样本数 */
    static void printFlame() {
        Map<String, Map<String, Integer>> tree = new LinkedHashMap<>();
        for (Sample s : samples) {
            Map<String, Integer> level = tree.computeIfAbsent("root",
                    k -> new LinkedHashMap<>());
            for (String f : s.stack()) {
                level.merge(f, 1, Integer::sum);
                level = tree.computeIfAbsent(f, k -> new LinkedHashMap<>());
            }
        }
        Map<String, Integer> counts = totalCounts();
        System.out.println("【火焰图（缩进=深度，数字=样本数）】");
        printNode(tree, "root", 0, samples.size(), counts);
    }

    static void printNode(Map<String, Map<String, Integer>> tree, String node,
                          int depth, int total, Map<String, Integer> counts) {
        if (!node.equals("root")) {
            int count = counts.getOrDefault(node, 0);
            double pct = total == 0 ? 0 : count * 100.0 / total;
            System.out.printf("  %s%-22s %3d (%4.1f%%)%n", "  ".repeat(depth), node, count, pct);
        }
        for (String child : tree.getOrDefault(node, Map.of()).keySet()) {
            printNode(tree, child, depth + 1, total, counts);
        }
    }

    public static void main(String[] args) {
        // 构造采样：60% 时间在 JSON 序列化，25% 在正则，15% 在 DB 等待（wall）
        for (int i = 0; i < 60; i++) {
            sample("main", "handleRequest", "serialize", "writeJson");
        }
        for (int i = 0; i < 25; i++) {
            sample("main", "handleRequest", "validate", "matchRegex");
        }
        for (int i = 0; i < 15; i++) {
            sample("main", "handleRequest", "queryDb", "awaitIo");
        }

        System.out.println("【样本总数】" + samples.size());

        System.out.println();
        System.out.println("【总占比（含子调用）】");
        totalCounts().forEach((f, c) -> System.out.printf("  %-18s %3d (%4.1f%%)%n",
                f, c, c * 100.0 / samples.size()));

        System.out.println();
        System.out.println("【Self 占比（栈顶）】");
        selfCounts().forEach((f, c) -> System.out.printf("  %-18s %3d (%4.1f%%)%n",
                f, c, c * 100.0 / samples.size()));

        System.out.println();
        printFlame();

        System.out.println();
        System.out.println("【读图】");
        System.out.println("  writeJson 占比 60%：最宽的叶子 -> CPU 热点");
        System.out.println("  awaitIo 占比 15%：CPU 火焰图看不到等待，要看 Wall-clock");
        System.out.println("  handleRequest 很宽但 self 很小 -> 它只是调用者，不是热点");

        System.out.println();
        System.out.println("【优化顺序】先优化 writeJson（60%），再看向量化的正则（25%）");
    }
}
```

**课后练习**
1. 构造 100 个采样栈，聚合总占比与 self 占比，找出真正的 CPU 热点。
2. 用文本火焰图打印调用树，验证"宽叶子 = 热点、宽父节点 = 调用者"。
3. 思考题：为什么"CPU 火焰图看不到 IO 等待"？从"采样只抓运行中线程"分析，并说明 Wall-clock 火焰图的用途。

---

### 80-06 内存分析与 GC 日志

**知识点**
- **GC 日志是"免费的体检报告"**：**每次 GC 的类型、耗时、回收前后堆大小、晋升老年代大小**——**只看"总暂停时间"不够，要看 P99 暂停与 Full GC 频率**。
- **三个关键指标**：**GC 吞吐**（`1 - 总暂停/总运行`，目标 > 95%）、**最大暂停**（P99 是否满足 SLO）、**晋升速率**（老年代增长快 -> 可能泄漏或分配过大）。
- **分配热点**：**短命对象多**（Young GC 频繁但快）、**大对象/长寿命对象多**（晋升快、Full GC 风险）——**用分配采样（Allocation Profiling）定位"谁在疯狂 new"**。
- **泄漏的迹象**：**Full GC 后老年代仍持续上涨**、**GC 越来越频繁但回收量很小**——**先 dump 堆再分析引用链**（30 章的 MAT 思路）。
- 与 30 章的关系：30 章讲"JVM 内存结构与 GC 算法"，**本章讲"从 GC 日志与分配采样读出问题"**。
- 易错点：只看平均暂停（忽略 P99）；把 Young GC 频繁当故障（可能正常）；不记录 GC 日志（事后无据）；堆越大越好（暂停更长）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GcAnalysis {
    /** 解析一条简化 GC 日志：类型 + 暂停 ms + 回收后堆 MB */
    record GcEvent(String type, double pauseMs, long heapAfterMb) {
    }

    static final Pattern PATTERN = Pattern.compile(
            "\\[GC (\\w[\\w ]*?) ([\\d.]+)ms\\] heapAfter=(\\d+)MB");

    static List<GcEvent> parse(List<String> lines) {
        List<GcEvent> events = new ArrayList<>();
        for (String line : lines) {
            Matcher m = PATTERN.matcher(line);
            if (m.find()) {
                events.add(new GcEvent(m.group(1).trim(),
                        Double.parseDouble(m.group(2)),
                        Long.parseLong(m.group(3))));
            }
        }
        return events;
    }

    static void analyze(List<GcEvent> events, double totalRunSeconds) {
        double totalPause = 0;
        double maxPause = 0;
        List<Double> pauses = new ArrayList<>();
        long fullGc = 0;
        long youngGc = 0;
        for (GcEvent e : events) {
            totalPause += e.pauseMs();
            maxPause = Math.max(maxPause, e.pauseMs());
            pauses.add(e.pauseMs());
            if (e.type().toLowerCase().contains("full")) {
                fullGc++;
            } else {
                youngGc++;
            }
        }
        pauses.sort(null);
        double p99 = pauses.isEmpty() ? 0
                : pauses.get((int) Math.ceil(pauses.size() * 0.99) - 1);

        System.out.println("【GC 概览】");
        System.out.printf("  Young GC=%d 次，Full GC=%d 次，总暂停=%.0fms%n",
                youngGc, fullGc, totalPause);
        System.out.printf("  最大暂停=%.0fms，P99 暂停=%.0fms%n", maxPause, p99);
        System.out.printf("  GC 吞吐=%.2f%%（目标 >95%%）%n",
                (1 - totalPause / (totalRunSeconds * 1000)) * 100);

        System.out.println();
        System.out.println("【堆变化】");
        long prev = -1;
        for (GcEvent e : events) {
            String trend = prev < 0 ? "-" : (e.heapAfterMb() > prev ? "↑" : "↓");
            System.out.printf("  %-12s %6.1fms 回收后=%3dMB %s%n",
                    e.type(), e.pauseMs(), e.heapAfterMb(), trend);
            prev = e.heapAfterMb();
        }
    }

    static void allocationHotspots() {
        System.out.println();
        System.out.println("【分配热点（采样）】");
        Map<String, Long> alloc = new LinkedHashMap<>();
        alloc.put("StringBuilder.toString", 420L);
        alloc.put("HashMap.Node", 310L);
        alloc.put("byte[]", 260L);
        alloc.put("BigDecimal", 90L);
        alloc.put("LogEvent", 60L);
        long total = alloc.values().stream().mapToLong(Long::longValue).sum();
        alloc.forEach((k, v) -> System.out.printf("  %-24s %4dMB (%4.1f%%)%n",
                k, v, v * 100.0 / total));
        System.out.println("  前两项占 66% -> 优先优化字符串拼接与临时 Map");
    }

    public static void main(String[] args) {
        List<String> lines = List.of(
                "[GC Young 12.0ms] heapAfter=120MB",
                "[GC Young 15.0ms] heapAfter=130MB",
                "[GC Young 11.0ms] heapAfter=125MB",
                "[GC Young 18.0ms] heapAfter=140MB",
                "[GC Young 14.0ms] heapAfter=135MB",
                "[GC Full 820.0ms] heapAfter=310MB",
                "[GC Young 20.0ms] heapAfter=320MB",
                "[GC Young 22.0ms] heapAfter=330MB",
                "[GC Full 910.0ms] heapAfter=335MB",
                "[GC Young 25.0ms] heapAfter=340MB");

        List<GcEvent> events = parse(lines);
        analyze(events, 300);
        allocationHotspots();

        System.out.println();
        System.out.println("【诊断】");
        System.out.println("  Full GC 2 次、每次 800ms+，且回收后堆仍 310MB+ -> 疑似泄漏");
        System.out.println("  Young GC 频率正常、暂停 10~25ms -> 不是主要问题");
        System.out.println("  下一步：dump 堆 -> 找增长最快的对象 -> 查引用链");

        System.out.println();
        System.out.println("【GC 调优顺序】先减少分配（代码）> 再调堆/收集器 > 最后才考虑换硬件");
    }
}
```

**课后练习**
1. 解析 10 条 GC 日志，计算总暂停、P99 暂停、GC 吞吐与 Full GC 次数。
2. 识别"Full GC 后老年代仍上涨"的模式，给出 dump/引用链分析步骤。
3. 思考题：为什么"Young GC 频繁"不一定是问题？从"短命对象 + 复制算法"成本分析。

---

### 80-07 锁竞争与线程分析

**知识点**
- **线程状态是排障第一现场**：**RUNNABLE（真在跑/等 CPU）**、**BLOCKED（等 synchronized 锁）**、**WAITING/TIMED_WAITING（等条件/超时）**——**大量 BLOCKED 说明锁竞争，大量 WAITING 可能是下游/队列**。
- **锁竞争的两个指标**：**等待线程数**与**持锁时间**——**持锁时间长的临界区是元凶**（锁内做 IO/RPC 是经典错误）。
- **死锁检测**：**线程 dump 中找"BLOCKED 且互相等待对方持有的锁"**——**JVM 会自动检测并打印 `Found one Java-level deadlock`**（32 章）。
- **减少竞争的手段**：**缩小临界区**（锁内只做内存操作）、**分段锁**（ConcurrentHashMap）、**无锁化**（CAS/不可变）、**线程本地化**（ThreadLocal）——**先测量再选**。
- 与 32 章的关系：32 章讲 Java 并发原语，**本章讲"线上线程 dump 怎么读、锁热点怎么找"**。
- 易错点：看到 CPU 高就加线程（锁竞争更严重）；锁内做 RPC（持锁时间爆炸）；用 `Thread.sleep` 等锁（伪等待）；不看线程 dump 就猜。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class LockProfiling {
    record ThreadInfo(String name, String state, String waitingOn, long blockedMs) {
    }

    static void stateSummary(List<ThreadInfo> threads) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (ThreadInfo t : threads) {
            counts.merge(t.state(), 1, Integer::sum);
        }
        System.out.println("【线程状态分布】");
        counts.forEach((k, v) -> System.out.printf("  %-14s %d%n", k, v));
        System.out.println();
    }

    /** 锁热点：按 waitingOn 聚合等待线程数与总阻塞时间 */
    static void hotLocks(List<ThreadInfo> threads) {
        Map<String, Integer> waiters = new LinkedHashMap<>();
        Map<String, Long> blocked = new LinkedHashMap<>();
        for (ThreadInfo t : threads) {
            if (t.waitingOn() == null) {
                continue;
            }
            waiters.merge(t.waitingOn(), 1, Integer::sum);
            blocked.merge(t.waitingOn(), t.blockedMs(), Long::sum);
        }
        System.out.println("【锁热点】");
        waiters.forEach((lock, n) -> System.out.printf(
                "  %-28s 等待线程=%d 累计阻塞=%dms%n", lock, n, blocked.get(lock)));
        System.out.println();
    }

    /** 死锁：A 等 B 的锁、B 等 A 的锁 */
    static void deadlock(List<ThreadInfo> threads) {
        Map<String, String> threadToLock = new LinkedHashMap<>();
        Map<String, String> lockOwner = new LinkedHashMap<>();
        // 示例：直接按线程名与 waitingOn 构造等待图
        Map<String, String> waitsFor = new LinkedHashMap<>();
        waitsFor.put("http-nio-1", "http-nio-2");
        waitsFor.put("http-nio-2", "http-nio-1");

        System.out.println("【死锁检测】");
        Set<String> visited = new LinkedHashSet<>();
        for (String t : waitsFor.keySet()) {
            List<String> path = new ArrayList<>();
            String cur = t;
            while (cur != null && !path.contains(cur)) {
                path.add(cur);
                cur = waitsFor.get(cur);
            }
            if (cur != null) {
                System.out.println("  发现环：" + path + " -> " + cur);
                break;
            }
        }
        System.out.println("  JVM 会打印 Found one Java-level deadlock + 双方持有的锁");
        System.out.println();
    }

    public static void main(String[] args) {
        List<ThreadInfo> threads = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            threads.add(new ThreadInfo("worker-" + i, "RUNNABLE", null, 0));
        }
        for (int i = 0; i < 12; i++) {
            threads.add(new ThreadInfo("http-nio-" + i, "BLOCKED",
                    "com.example.OrderService.lock", 300 + i * 20L));
        }
        for (int i = 0; i < 5; i++) {
            threads.add(new ThreadInfo("db-pool-" + i, "WAITING",
                    "java.util.concurrent.LinkedBlockingQueue.take", 5_000));
        }
        for (int i = 0; i < 3; i++) {
            threads.add(new ThreadInfo("scheduler-" + i, "TIMED_WAITING", null, 0));
        }

        stateSummary(threads);
        hotLocks(threads);
        deadlock(threads);

        System.out.println("【解读】");
        System.out.println("  12 个 http 线程 BLOCKED 在同一把锁 -> 锁竞争严重");
        System.out.println("  5 个 db 线程 WAITING 在队列 -> 连接池空闲（不是瓶颈）");
        System.out.println("  8 个 RUNNABLE -> 正常处理中");
        System.out.println();
        System.out.println("【优化】缩小临界区（锁内不要做 RPC/IO）或分段锁；先看持锁时间最长的代码");
    }
}
```

**课后练习**
1. 解析一份线程 dump（状态/等待锁/阻塞时长），统计状态分布与锁热点。
2. 构造两个线程互相等待的锁顺序，检测死锁环并给出修复方案。
3. 思考题：为什么"锁内做 RPC"是性能杀手？从"持锁时间 × 等待线程数"分析。

---

### 80-08 全链路压测

**知识点**
- **全链路压测的目标**：**在生产（或生产同构）环境验证"全链路容量"**，**包括网关/服务/DB/缓存/MQ/第三方**——**单接口压测发现不了链路瓶颈**。
- **流量染色**：**压测请求带特殊标记**（header `x-shadow: true`），**一路透传到下游**，**各组件识别标记走影子资源**——**不染色会污染生产数据**。
- **影子表/影子库**：**压测写操作落到影子表**（同结构、不同表名/库），**读也走影子**；**压测结束清理**——**只读压测可不建影子，写压测必须**。
- **隔离与熔断**：**压测流量要能一键停止**、**下游有压测限流**、**禁止压测触发真实短信/支付**——**安全是压测的前提**。
- 与 41/56 章的关系：41 章发布、56 章容量，**本章是"容量验证的实战"**——**压测是演练，不是上线**。
- 易错点：不染色（脏数据）；压测写真实表（资损）；不设停止开关（失控）；只压核心接口（漏链路）；压测数据不清理（垃圾数据）。

**示例代码**
```java
import java.util.LinkedHashMap;
import java.util.Map;

public class ShadowTraffic {
    record Request(String path, String user, boolean shadow, Map<String, String> headers) {
    }

    record Response(int code, String body, String dataSource) {
    }

    static final Map<String, String> prodDb = new LinkedHashMap<>();
    static final Map<String, String> shadowDb = new LinkedHashMap<>();

    static Response handle(Request req) {
        // 1) 识别染色
        boolean shadow = req.shadow() || "true".equals(req.headers().get("x-shadow"));
        // 2) 路由到影子资源
        Map<String, String> db = shadow ? shadowDb : prodDb;
        String source = shadow ? "shadow-db" : "prod-db";

        // 3) 写操作（影子不污染生产）
        if (req.path().startsWith("/order/create")) {
            db.put(req.user(), "order-" + req.user());
            return new Response(200, "created in " + source, source);
        }

        // 4) 读操作
        String v = db.getOrDefault(req.user(), "none");
        return new Response(200, "read " + v + " from " + source, source);
    }

    public static void main(String[] args) {
        System.out.println("【正常流量】");
        Response r1 = handle(new Request("/order/create", "alice", false, Map.of()));
        System.out.println("  " + r1);
        System.out.println("  生产库：" + prodDb);

        System.out.println();
        System.out.println("【压测流量（header 染色）】");
        Response r2 = handle(new Request("/order/create", "alice", false,
                Map.of("x-shadow", "true")));
        System.out.println("  " + r2);
        System.out.println("  影子库：" + shadowDb);
        System.out.println("  生产库：" + prodDb + "（未被污染）");

        System.out.println();
        System.out.println("【压测读】");
        System.out.println("  " + handle(new Request("/order/query", "alice", true, Map.of())));

        System.out.println();
        System.out.println("【隔离检查】");
        String[][] checks = {
                {"流量染色", "x-shadow 透传全链路", "否则污染生产"},
                {"影子表", "写走影子库/表", "结构同、数据隔离"},
                {"停止开关", "一键停压测", "防止失控"},
                {"下游保护", "压测流量限流/熔断", "不打挂第三方"},
                {"数据清理", "压测后清影子数据", "避免垃圾"},
        };
        for (String[] c : checks) {
            System.out.printf("  %-10s %-26s %s%n", c[0], c[1], c[2]);
        }

        System.out.println();
        System.out.println("【结论】压测的第一原则是「不伤害生产」：染色 + 影子 + 开关 + 清理");
    }
}
```

**课后练习**
1. 实现请求染色与影子库路由，验证压测写不污染生产数据。
2. 给压测加"一键停止"与"下游限流"，说明失控时的处置流程。
3. 思考题：为什么"只压核心接口"不够？从"链路瓶颈可能在非核心依赖"分析。

---

### 80-09 瓶颈定位与扩容决策

**知识点**
- **利特尔法则（Little's Law）**：**L = λ × W**（并发数 = 到达率 × 平均停留时间）——**已知两个可推第三个**，**扩容前先算"需要多少并发"**。
- **利用率与排队**：**ρ = λ / μ**（到达率/服务率）；**ρ 接近 1 时排队时间非线性上升**（M/M/1 的 `Wq = ρ/(μ-λ)`）——**70% 利用率是常见的安全水位**（80% 已接近拐点）。
- **扩容决策三问**：**瓶颈是资源还是依赖**（CPU/连接池/DB/下游）、**扩容能否线性提升**（有状态服务/共享 DB 往往不能）、**成本是否可接受**（云成本 56 章）。
- **拐点识别**：**压测时逐步加并发，画"并发-吞吐-延迟"曲线**——**吞吐不再上升、延迟陡增的点就是拐点**（不是 CPU 100% 才算）。
- 与 56 章的关系：56 章讲"容量管理与水位"，**本章讲"用排队论算清楚该扩多少"**。
- 易错点：线性外推容量（忽略排队非线性）；只扩应用不扩 DB（DB 成新瓶颈）；不设水位告警（临时扩容来不及）；扩容后不复测（配置漂移）。

**示例代码**
```java
public class CapacityModel {
    /** 利特尔法则：L = λ * W */
    static double littleConcurrency(double arrivalRatePerSec, double latencySec) {
        return arrivalRatePerSec * latencySec;
    }

    /** M/M/1 平均排队时间：Wq = ρ / (μ - λ) */
    static double queueWaitSec(double arrivalRate, double serviceRate) {
        if (arrivalRate >= serviceRate) {
            return Double.POSITIVE_INFINITY;
        }
        double rho = arrivalRate / serviceRate;
        return rho / (serviceRate - arrivalRate);
    }

    /** M/M/1 平均停留时间：W = 1/(μ-λ) */
    static double responseSec(double arrivalRate, double serviceRate) {
        if (arrivalRate >= serviceRate) {
            return Double.POSITIVE_INFINITY;
        }
        return 1.0 / (serviceRate - arrivalRate);
    }

    public static void main(String[] args) {
        double serviceRate = 100;                    // 单实例 100 req/s

        System.out.println("【利特尔法则】");
        double lambda = 80;
        double latency = 0.05;                       // 50ms
        System.out.printf("  λ=%.0f/s，W=%.3fs -> 并发 L=%.1f%n",
                lambda, latency, littleConcurrency(lambda, latency));

        System.out.println();
        System.out.println("【利用率与排队（单实例 μ=100/s）】");
        System.out.printf("  %-8s %-8s %-12s %-12s %s%n",
                "λ", "ρ", "排队Wq", "响应W", "判断");
        double[] rates = {50, 70, 80, 90, 95, 99};
        for (double r : rates) {
            double rho = r / serviceRate;
            double wq = queueWaitSec(r, serviceRate);
            double w = responseSec(r, serviceRate);
            String verdict = rho <= 0.7 ? "安全" : rho < 0.85 ? "接近拐点" : "危险";
            System.out.printf("  %-8.0f %-8.2f %-12.3f %-12.3f %s%n",
                    r, rho, wq, w, verdict);
        }

        System.out.println();
        System.out.println("【扩容计算】");
        double targetRps = 450;
        double perInstance = 100;
        double safeUtilization = 0.7;
        int need = (int) Math.ceil(targetRps / (perInstance * safeUtilization));
        System.out.printf("  目标 %.0f req/s，单实例 %.0f，安全水位 %.0f%% -> 至少 %d 个实例%n",
                targetRps, perInstance, safeUtilization * 100, need);
        System.out.printf("  若只按 100%% 利用率算：%d 个（没有余量，延迟会爆炸）%n",
                (int) Math.ceil(targetRps / perInstance));

        System.out.println();
        System.out.println("【拐点识别】");
        int[][] curve = {
                {10, 950, 12},
                {30, 2_800, 15},
                {50, 4_600, 20},
                {70, 6_200, 35},
                {85, 6_800, 90},
                {95, 6_900, 400},
        };
        System.out.printf("  %-8s %-12s %-10s %s%n", "并发", "吞吐(req/s)", "P99(ms)", "判断");
        for (int[] p : curve) {
            String verdict = p[2] < 50 ? "线性区" : p[2] < 150 ? "拐点" : "过载区";
            System.out.printf("  %-8d %-12d %-10d %s%n", p[0], p[1], p[2], verdict);
        }

        System.out.println();
        System.out.println("【结论】按 70% 水位扩容；扩容前确认瓶颈不是 DB/下游/连接池");
    }
}
```

**课后练习**
1. 用利特尔法则计算"目标 QPS 下需要多少并发"，并与压测结果对照。
2. 画出"并发-吞吐-延迟"曲线，找出拐点并解释为什么吞吐不再上升。
3. 思考题：为什么"按 100% 利用率算实例数"会出事？从 M/M/1 排队非线性分析。

---

### 80-10 性能回归与门禁

**知识点**
- **性能回归要"可比"**：**同一机器、同一数据集、同一 JVM 参数、同一预热**——**否则测出的差异可能是噪声**（80-02）。
- **门禁的判定**：**基线 vs 候选**，**差异超过阈值（如 P99 +10%）且超过噪声范围**才失败；**阈值太严会频繁误报**（团队关闭门禁），**太松会漏掉真回归**。
- **统计显著性**：**多次运行取中位数/最优**，**看波动范围**（min/max）；**单次对比不可信**——**CI 机器噪声大时尤其要谨慎**。
- **门禁的落点**：**核心接口的基准测试**（JMH）与**端到端压测**（80-08）两层——**前者防代码级退化，后者防容量退化**。
- 与 41 章的关系：41 章 CI 门禁拦"测试失败/扫描问题"，**本章加一道"性能门禁"**——**性能也是质量属性**。
- 易错点：用绝对值阈值（机器不同就废）；不看噪声（误报）；只测一次（随机通过/失败）；门禁不区分核心/非核心接口。

**示例代码**
```java
import java.util.ArrayList;
import java.util.List;

public class PerfRegressionGate {
    record Run(double p50, double p99, double throughput) {
    }

    record Baseline(String name, double p99, double noisePercent) {
    }

    static double median(List<Double> values) {
        List<Double> sorted = new ArrayList<>(values);
        sorted.sort(null);
        int n = sorted.size();
        return n % 2 == 1 ? sorted.get(n / 2)
                : (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2;
    }

    static boolean gate(String name, Baseline base, List<Run> runs, double thresholdPercent) {
        List<Double> p99s = new ArrayList<>();
        List<Double> tps = new ArrayList<>();
        for (Run r : runs) {
            p99s.add(r.p99());
            tps.add(r.throughput());
        }
        double p99 = median(p99s);
        double tp = median(tps);
        double delta = (p99 - base.p99()) / base.p99() * 100;
        double allowed = Math.max(thresholdPercent, base.noisePercent());

        System.out.printf("  %-12s 基线P99=%.0fms 候选P99=%.0fms 变化=%+.1f%% 噪声=±%.1f%%%n",
                name, base.p99(), p99, delta, base.noisePercent());
        System.out.printf("    吞吐中位数=%.0f req/s，P99 波动=[%.0f, %.0f]%n",
                tp, p99s.stream().mapToDouble(Double::doubleValue).min().orElse(0),
                p99s.stream().mapToDouble(Double::doubleValue).max().orElse(0));

        boolean pass = delta <= allowed;
        System.out.printf("    门禁（阈值 %.1f%%）：%s%n", allowed,
                pass ? "通过" : "失败：性能回归");
        return pass;
    }

    public static void main(String[] args) {
        System.out.println("【基线对比（取 5 次运行中位数）】");
        boolean a = gate("order-query",
                new Baseline("order-query", 120, 3),
                List.of(new Run(40, 118, 5_000), new Run(41, 121, 5_050),
                        new Run(39, 119, 5_100), new Run(42, 122, 4_980),
                        new Run(40, 120, 5_020)),
                10);

        System.out.println();
        boolean b = gate("order-create",
                new Baseline("order-create", 200, 5),
                List.of(new Run(60, 230, 2_000), new Run(62, 235, 1_980),
                        new Run(61, 228, 2_020), new Run(60, 232, 2_010),
                        new Run(63, 236, 1_990)),
                10);

        System.out.println();
        boolean c = gate("user-query",
                new Baseline("user-query", 80, 8),
                List.of(new Run(20, 85, 8_000), new Run(21, 86, 7_950),
                        new Run(20, 84, 8_050), new Run(22, 87, 7_980),
                        new Run(21, 85, 8_010)),
                10);

        System.out.println();
        System.out.println("【结果】order-query=" + (a ? "通过" : "失败")
                + "，order-create=" + (b ? "通过" : "失败")
                + "，user-query=" + (c ? "通过" : "失败"));

        System.out.println();
        System.out.println("【门禁设计】");
        String[][] rules = {
                {"阈值", "max(业务阈值, 噪声)", "避免噪声误报"},
                {"多次运行", "5 次取中位数", "单次不可信"},
                {"分层", "核心接口严、非核心宽", "资源有限"},
                {"可解释", "输出基线/候选/波动", "失败能定位"},
        };
        for (String[] r : rules) {
            System.out.printf("  %-8s %-26s %s%n", r[0], r[1], r[2]);
        }

        System.out.println();
        System.out.println("【结论】性能门禁要「可比 + 多次 + 噪声感知」，否则会被团队关掉");
    }
}
```

**课后练习**
1. 实现性能门禁：基线 vs 5 次候选的中位数 P99，超过阈值且超过噪声才失败。
2. 构造"噪声很大"的候选数据，验证门禁不会误报（阈值取 max(业务阈值, 噪声)）。
3. 思考题：为什么"单次对比"不可信？从 CI 机器噪声、JIT、GC 抖动分析。

---

### 80-11 综合案例：一次双十一压测

**知识点**
- **把本章收口成一次完整压测**：**目标设定（80-01）-> 模型选择（80-04）-> 基准/压测（80-02/80-08）-> 指标分析（80-03）-> 瓶颈定位（80-05~80-07）-> 容量决策（80-09）-> 回归门禁（80-10）**。
- **压测目标要可量化**：**目标 QPS、P99 上限、错误率上限、持续时间**（如"峰值 5 万 QPS、P99 < 200ms、错误率 < 0.1%、持续 30 分钟"）——**没有目标就无法判定通过**。
- **阶梯加压**：**每 5 分钟提升 20% 流量**，**观察吞吐/延迟/错误率/资源水位**——**在拐点前停下，留出安全余量**（80-09）。
- **压测报告**：**目标 -> 环境 -> 模型 -> 曲线 -> 瓶颈 -> 结论 -> 风险与建议**——**报告要能让决策者做扩容/限流决定**。
- 与 56 章的关系：56 章"稳定性工程"是日常，**本章是"大促前的实战演练"**——**压测发现的问题，比大促当天发现便宜 100 倍**。
- 易错点：无目标（压到哪算哪）；闭环模型（80-04 低估）；不染色（脏数据）；只看平均值；压完不沉淀报告。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Double11LoadTest {
    record Sample(int minute, long qps, double p99Ms, double errorRate,
                  double cpu, double dbConnUtilization) {
    }

    record Goal(long targetQps, double p99LimitMs, double errorRateLimit) {
    }

    static final Goal goal = new Goal(50_000, 200, 0.001);

    /** 阶梯加压：每分钟 +20%，直到目标 */
    static List<Sample> ramp() {
        List<Sample> samples = new ArrayList<>();
        long qps = 10_000;
        int minute = 0;
        while (qps <= goal.targetQps() * 1.2) {
            minute += 1;
            double load = (double) qps / goal.targetQps();
            double p99 = 40 + 160 * Math.pow(load, 3);          // 非线性
            double err = load > 1.05 ? (load - 1.05) * 0.05 : 0.0002;
            double cpu = Math.min(0.99, 0.25 + 0.6 * load);
            double db = Math.min(0.99, 0.2 + 0.75 * load);
            samples.add(new Sample(minute, qps, p99, err, cpu, db));
            qps = Math.round(qps * 1.2);
        }
        return samples;
    }

    static void report(List<Sample> samples) {
        System.out.println("【阶梯加压曲线】");
        System.out.printf("  %-6s %-9s %-9s %-9s %-7s %-8s %s%n",
                "分钟", "QPS", "P99(ms)", "错误率", "CPU", "DB连接", "判断");
        for (Sample s : samples) {
            String verdict;
            if (s.errorRate() > goal.errorRateLimit()) {
                verdict = "错误率超标";
            } else if (s.p99Ms() > goal.p99LimitMs()) {
                verdict = "P99 超标";
            } else if (s.dbConnUtilization() > 0.85) {
                verdict = "DB 接近瓶颈";
            } else if (s.cpu() > 0.85) {
                verdict = "CPU 接近瓶颈";
            } else {
                verdict = "正常";
            }
            System.out.printf("  %-6d %-9d %-9.0f %-9.4f %-7.0f%% %-8.0f%% %s%n",
                    s.minute(), s.qps(), s.p99Ms(), s.errorRate() * 100,
                    s.cpu() * 100, s.dbConnUtilization() * 100, verdict);
        }
    }

    static void conclusion(List<Sample> samples) {
        long safeQps = 0;
        for (Sample s : samples) {
            if (s.p99Ms() <= goal.p99LimitMs()
                    && s.errorRate() <= goal.errorRateLimit()
                    && s.dbConnUtilization() <= 0.85) {
                safeQps = s.qps();
            }
        }
        System.out.println();
        System.out.println("【容量结论】");
        System.out.printf("  目标 QPS=%,d，安全承载=%,d（P99/错误率/DB 水位均达标）%n",
                goal.targetQps(), safeQps);
        System.out.printf("  安全余量=%.1f%%%n", (1 - (double) safeQps / goal.targetQps()) * 100);

        if (safeQps < goal.targetQps()) {
            System.out.println("  未达标 -> 扩容或限流");
        } else {
            System.out.println("  达标 -> 可支撑大促，但仍需保留降级预案");
        }

        System.out.println();
        System.out.println("【瓶颈】");
        Sample last = samples.get(samples.size() - 1);
        System.out.printf("  最高压时 CPU=%.0f%%，DB 连接=%.0f%% -> DB 连接池是首要瓶颈%n",
                last.cpu() * 100, last.dbConnUtilization() * 100);
        System.out.println("  下一步：扩大连接池 / 读写分离 / 缓存热点 / SQL 优化（77 章）");
    }

    static void reportCard() {
        System.out.println();
        System.out.println("【压测报告模板】");
        Map<String, String> report = new LinkedHashMap<>();
        report.put("目标", "峰值 5 万 QPS，P99<200ms，错误率<0.1%，持续 30 分钟");
        report.put("环境", "生产同构，影子库，流量染色");
        report.put("模型", "开放模型，阶梯加压每分钟 +20%");
        report.put("曲线", "QPS-延迟-错误率-资源水位");
        report.put("瓶颈", "DB 连接池 85%+，P99 在 5.5 万 QPS 后陡增");
        report.put("结论", "安全承载 4.5 万 QPS，需扩容 DB 连接或加缓存");
        report.put("风险", "未覆盖支付回调峰值，需单独压测");
        report.forEach((k, v) -> System.out.printf("  %-6s %s%n", k, v));
    }

    public static void main(String[] args) {
        List<Sample> samples = ramp();
        report(samples);
        conclusion(samples);
        reportCard();

        System.out.println();
        System.out.println("【验收】目标可量化 / 模型不遗漏 / 曲线有拐点 / 结论可决策 / 报告可沉淀");
    }
}
```

**课后练习**
1. 设定一组压测目标（QPS/P99/错误率），用阶梯加压模拟并找出安全承载点。
2. 调整 DB 连接池水位曲线，观察瓶颈从 DB 转移到 CPU，说明"瓶颈会迁移"。
3. 思考题：为什么"压测报告"比"压测数据"更重要？从"决策者需要结论而非原始曲线"分析。

## 本章综合练习

### 综合题：双十一压测与调优

**场景**

给订单系统做压测与调优：压测模型设计、阶梯加压找拐点、瓶颈定位（CPU/IO/锁/GC/连接池）、JVM 调优、限流保护、容量结论。最后给出「能扛多少 QPS」的量化答案。

**任务**
1. 设计压测模型：按真实流量比例构造读写比（如 9:1）、参数化数据、思考时间，说明为什么不能只压一个接口。
2. 阶梯加压：从 100 QPS 起每 5 分钟加 100，记录吞吐与 P99，找出吞吐不再上升而延迟陡增的拐点。
3. 定位瓶颈：用线程栈、GC 日志、慢查询、连接池等待时间判断是 CPU 打满、GC 停顿、锁竞争还是下游超时。
4. 做调优对比：调整线程池/连接池/GC 参数/缓存命中率，每次只改一项，记录前后数据，形成调优记录表。

**验收清单**
- [ ] 压测模型贴近真实流量，参数不集中在少数热点 key。
- [ ] 能找到明确的性能拐点并给出对应 QPS 数值。
- [ ] 瓶颈定位有证据（火焰图/GC 日志/慢 SQL），不是猜的。
- [ ] 调优记录表显示每项改动的收益，最终给出「单机/集群可承载 QPS」的结论与余量建议。
