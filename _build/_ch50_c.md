### 50-09 高并发 IO 实测对比

**知识点**
- **本课用一组可复现实验回答“虚拟线程到底带来多少”**：三个实验——吞吐对比（固定池 vs 虚拟线程）、并发规模对比（千级 vs 万级任务的内存与调度）、瓶颈平移验证（线程不再是瓶颈后，连接池/下游成为新瓶颈）。**自己测出来的数字才是团队的数字**（46-09 验收思想）。
- **实验设计的控制变量**：任务形态统一（sleep 模拟 IO 100ms）、压测客户端统一、JVM 参数统一（堆大小一致）；**每个实验记录**：完成时间、P99、线程数、内存峰值（42 章实验纪律的并发版）。
- **结果的解读框架**：吞吐提升的来源是“阻塞不占线程”（等待复用）；**提升倍数 ≈ 等待占比**（IO 占 95% 的负载可放大近 20 倍，IO 占 20% 只放大 1.25 倍）——**先测你的等待占比，再预期收益**。
- **内存账**：平台线程 1MB 栈 × 1000 = 1GB；虚拟线程堆上栈 × 10000 ≈ 数百 MB——**在相同 -Xmx 下可承载的并发数差两个数量级**（44-05 显存账的线程版）。
- **CPU 密集对照实验**：同样任务换成 CPU 计算（无 sleep）——虚拟线程与固定池吞吐几乎相同（核数决定）——**验证“虚拟线程只救 IO”**，避免盲目全量替换。
- 易错点：实验不含真实 IO 形态（纯 sleep 低估调度开销、纯 CPU 高估收益）；单次跑分下结论（取多次中位数 42-04 分位数思想）；忽略 JVM 预热（JIT 未热，前几轮数据失真）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class IoBenchmark {
    /** IO 任务：等 100ms（模拟下游） */
    static String ioTask() {
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return "ok";
    }

    /** CPU 任务：无等待，纯计算 */
    static String cpuTask() {
        long x = 0;
        for (int i = 0; i < 2_000_000L; i++) {
            x += i * 31;
        }
        return x == -1 ? "impossible" : "ok";
    }

    static long run(String label, ExecutorService pool, int tasks,
                    java.util.function.Supplier<String> task) throws Exception {
        long start = System.currentTimeMillis();
        List<Future<String>> futures = new ArrayList<>();
        for (int i = 0; i < tasks; i++) {
            futures.add(pool.submit(task::get));
        }
        for (Future<String> f : futures) {
            f.get(60, TimeUnit.SECONDS);
        }
        long cost = System.currentTimeMillis() - start;
        System.out.printf("  %-22s %4d 任务 -> %5dms%n", label, tasks, cost);
        return cost;
    }

    static void throughputExperiment() throws Exception {
        System.out.println("【实验一：IO 任务吞吐（200 任务 × 100ms 等待）】");
        long fixed;
        try (ExecutorService fixedPool = Executors.newFixedThreadPool(20)) {
            fixed = run("固定池(20线程)", fixedPool, 200, IoBenchmark::ioTask);
        }
        long virtual;
        try (ExecutorService vtPool = Executors.newVirtualThreadPerTaskExecutor()) {
            virtual = run("虚拟线程", vtPool, 200, IoBenchmark::ioTask);
        }
        System.out.printf("  提速 %.1f 倍；理论等待占比 95%% -> 上限约 %.0f 倍（排队与调度损耗）%n%n",
                (double) fixed / virtual, 100.0 / 5);
    }

    static void scaleExperiment() throws Exception {
        System.out.println("【实验二：并发规模（2000 任务，同 -Xmx）】");
        try (ExecutorService smallPool = Executors.newFixedThreadPool(200)) {
            run("固定池(200线程) 上限", smallPool, 2000, IoBenchmark::ioTask);
        }
        try (ExecutorService vtPool = Executors.newVirtualThreadPerTaskExecutor()) {
            run("虚拟线程(2000并发)", vtPool, 2000, IoBenchmark::ioTask);
        }
        System.out.println("  固定池要扛 2000 并发需 2000 平台线程 ≈ 2GB 栈；虚拟线程 MB 级");
        System.out.println();
    }

    static void cpuControl() throws Exception {
        System.out.println("【实验三：CPU 任务对照（虚拟线程无收益验证）】");
        try (ExecutorService fixedPool = Executors.newFixedThreadPool(
                Runtime.getRuntime().availableProcessors())) {
            run("固定池(核数) CPU 任务", fixedPool, 40, IoBenchmark::cpuTask);
        }
        try (ExecutorService vtPool = Executors.newVirtualThreadPerTaskExecutor()) {
            run("虚拟线程 CPU 任务", vtPool, 40, IoBenchmark::cpuTask);
        }
        System.out.println("  结论：CPU 密集两者相当（核数决定）—— 虚拟线程只救 IO，全量替换是误用");
    }

    public static void main(String[] args) throws Exception {
        throughputExperiment();
        scaleExperiment();
        cpuControl();
    }
}
```

**课后练习**
1. 完整跑一遍三个实验（调整任务数与 -Xmx），记录你机器上的数字并计算等待占比与实际提速的关系。
2. 把实验换成真实下游（HTTP 调用测试服务），对比 sleep 模拟与真实 IO 的结果差异，分析调度开销占比。
3. 思考题：为什么“等待占比决定收益上限”？从 Amdahl 定律的视角推导虚拟线程收益的理论上界。

---

### 50-10 迁移路线：从线程池到虚拟线程

**知识点**
- **迁移是渐进工程，不是开关项目**：路线——① JDK 升级到 21（前置项，41 章 ADR）；② 开启 `spring.threads.virtual.enabled=true`（Tomcat 请求线程虚拟化）；③ JFR 扫 pinning 热点修复（50-06）；④ 连接池重评估（虚拟线程抬高了“需要的连接数”，池跟不上则瓶颈平移）；⑤ 自建线程池逐个评估（IO 型改虚拟、CPU 型保留）；⑥ 压测对比与灰度（41-07）。
- **改动的优先级**：先“零代码收益”（容器请求线程虚拟化）→ 再“热点收益”（自定义 IO 线程池换虚拟）→ 最后“边缘清理”（调度器/异步任务）。**每一步独立可回退**（49-10 演进纪律）。
- **升级的连带检查**：依赖库的 JDK 21 兼容性（老框架的字节码增强）、-XX 参数变化（GC 默认差异小但需回归）、`synchronized` 热点清单、监控线程数指标语义变化（虚拟线程数远超平台线程数，42 章面板要更新）。
- **连接池重评估公式**：虚拟线程时代池大小 ≈ 目标并发 × 下游耗时占比（等待在 DB 上的并发数）——**池从“限流器”变成“真实资源上限”**，池小则吞吐被封顶（50-05 实验）。
- **回退预案**：开关回退（enabled=false 一行）；灰度按服务/命名空间；回退后 pinning 风险自然消失。
- 易错点：升级 JDK 21 直接全量开虚拟线程（未扫 pinning）；连接池没调（虚拟线程全部堵在 10 个连接上，吞吐不升反降）；监控告警阈值没更新（线程数告警误报海量）；自定义池全部替换（CPU 型任务也换，无收益还有调度开销）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MigrationPath {
    static void roadmap() {
        System.out.println("【六步迁移路线】");
        String[][] steps = {
                {"① JDK 21 升级", "回归测试 + 依赖兼容矩阵", "前置项（41 章 ADR）"},
                {"② 开启开关", "spring.threads.virtual.enabled=true", "零代码收益：请求线程虚拟化"},
                {"③ 扫 pinning", "JFR 事件 Top 栈修复", "50-06，热点 synchronized 改锁"},
                {"④ 连接池重评估", "池大小 ≈ 并发×IO占比", "瓶颈平移预防"},
                {"⑤ 自建池分类", "IO 型换虚拟；CPU 型保留", "逐个评估，不一刀切"},
                {"⑥ 压测灰度", "吞吐/P99 对比 + 命名空间灰度", "41-07 放量纪律"},
        };
        for (String[] s : steps) {
            System.out.printf("  %-12s %-34s %s%n", s[0], s[1], s[2]);
        }
        System.out.println();
    }

    static void poolClassifier() {
        System.out.println("【自建线程池的分类处置】");
        String[][] rows = {
                {"HTTP 扇出池", "IO 密集 -> 换虚拟线程", "收益最大"},
                {"定时任务池", "看任务：IO 型换，CPU 型留", "逐任务判断"},
                {"CPU 计算池", "保留平台线程池（核数大小）", "虚拟线程无收益"},
                {"消费 MQ 线程池", "IO 密集 -> 换虚拟 + Semaphore 保下游", "配合 43 章 lag 伸缩"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-12s %-30s %s%n", r[0], r[1], r[2]);
        }
        System.out.println("  判据：池里任务“等”多还是“算”多 —— 等=换，算=留（50-09 实验）");
        System.out.println();
    }

    static void rollbackPlan() {
        System.out.println("【回退预案：一行开关的价值】");
        List<String> items = List.of(
                "配置开关：enabled=false 即回退平台线程（秒级）",
                "灰度范围：按服务/命名空间推进，出问题只影响局部",
                "回退信号：pinning 导致的吞吐坍塌、连接池耗尽告警、P99 劣化",
                "回退后动作：修复 pinning/池配置 -> 重新灰度（41-07）");
        for (String i : items) {
            System.out.println("  [ ] " + i);
        }
        System.out.println("  对照 41 章发布工程：并发模型变更也是一次发布，全套纪律适用");
    }

    public static void main(String[] args) {
        roadmap();
        poolClassifier();
        rollbackPlan();
    }
}
```

**课后练习**
1. 为你的服务制定六步迁移计划（含每步的验证门禁与回退条件），输出可评审文档。
2. 实现“IO 型池换虚拟线程”的对比压测（迁移前后），记录吞吐/P99/内存三组数字。
3. 思考题：为什么连接池在虚拟线程时代“从限流器变成真实资源上限”？从线程等待不再占资源后瓶颈平移的路径分析。

---

### 50-11 综合案例：聚合网关

**知识点**
- **把本章串成一个经典场景**：BFF/聚合网关——一个用户请求需要并行调 4~6 个下游（商品/价格/库存/营销/评论/推荐），合并后返回。**IO 密集 + 高扇出 + 严格超时**——并发模型的试金石（50-01 的决策原点）。
- **虚拟线程版实现**：每下游一个虚拟线程 + Semaphore 限每个下游并发 + 总超时取消 + 部分失败降级（超时下游返回占位数据而不是整体失败）——**“部分可用优于全失败”的聚合语义**（44-08 降级阶梯的网关版）。
- **超时预算分配**：网关总预算 800ms -> 各下游按重要性分配（商品 400ms/评论 200ms 可失败/推荐 150ms 可失败）——44-08 预算链的网关落地；先完成的先返回（流式聚合可选）。
- **可观测**：每下游耗时打点 + 失败率分下游 + 队列深度（42 章：聚合网关的 SLI 天然按下游维度展开）；取消传播（用户断开 -> 全部下游取消，48-07 同款）。
- **压测验收**：对比三种实现的吞吐/P99——串行（基线）、平台线程池（池大小受限）、虚拟线程（接近理想）——**在真实下游延迟分布下跑**（50-09 实验纪律）。
- 易错点：聚合失败语义一刀切（一个下游挂全页面挂）；超时预算不分级（全部 800ms，慢下游拖死）；无并发保护（大促下游被打爆）；缓存缺失（热点聚合结果缓存，46-10 数据 API 同款）。

**示例代码**
```java
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class AggregationGatewayCase {
    record Downstream(String name, int costMs, boolean critical, int maxConcurrency) {
    }

    static final List<Downstream> DOWNSTREAMS = List.of(
            new Downstream("product", 150, true, 20),
            new Downstream("price", 100, true, 20),
            new Downstream("stock", 120, true, 15),
            new Downstream("reviews", 200, false, 10),
            new Downstream("recommend", 250, false, 10));

    static String call(String name, int costMs) {
        try {
            Thread.sleep(costMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return name + "-data";
    }

    static void aggregate(String requestId) throws Exception {
        System.out.println("---- 请求 " + requestId + " ----");
        long start = System.currentTimeMillis();
        Map<String, String> merged = new LinkedHashMap<>();
        List<String> degraded = new ArrayList<>();
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> futures = new ArrayList<>();
            for (Downstream d : DOWNSTREAMS) {
                Semaphore permit = new Semaphore(d.maxConcurrency());
                int budget = d.critical() ? 400 : 200;
                futures.add(pool.submit(() -> {
                    try {
                        permit.acquire();
                        try {
                            String data = call(d.name(), d.costMs());
                            merged.put(d.name(), data);
                        } finally {
                            permit.release();
                        }
                    } catch (Exception e) {
                        if (d.critical()) {
                            merged.put(d.name(), "error");
                        } else {
                            merged.put(d.name(), "placeholder");
                            degraded.add(d.name());
                        }
                    }
                    return null;
                }));
            }
            for (Future<?> f : futures) {
                f.get(450, TimeUnit.MILLISECONDS);
            }
        }
        long cost = System.currentTimeMillis() - start;
        System.out.printf("  合并结果：%s%n", merged);
        System.out.printf("  降级项：%s  总耗时 %dms（预算 800ms）%n", degraded, cost);
        System.out.println("  语义：关键下游 400ms 预算，非关键 200ms 超时占位 —— 部分可用优于全失败");
        System.out.println();
    }

    static void budgetTable() {
        System.out.println("【超时预算分配表】");
        String[][] rows = {
                {"product/price/stock", "400ms（关键）", "超时 -> error 标记，页面骨架可用"},
                {"reviews/recommend", "200ms（非关键）", "超时 -> 占位数据，不阻塞整体"},
                {"网关总预算", "800ms", "44-08 预算链：上游知道下游最坏值"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-18s %-16s %s%n", r[0], r[1], r[2]);
        }
        System.out.println();
    }

    static void ops() {
        System.out.println("【聚合网关运营清单】");
        List<String> items = List.of(
                "按下游打点：耗时/失败率/超时率（42 章 SLI 按维度展开）",
                "取消传播：用户断开 -> 全部下游取消（48-07）",
                "热点聚合缓存：60s 短缓存挡重复请求（46-10）",
                "下游并发保护：每下游 Semaphore 独立配置",
                "压测验收：串行/池/虚拟线程三版对比（50-09 纪律）",
                "降级演练：单下游故障的页面可用性验证（47-10）");
        for (String i : items) {
            System.out.println("  [ ] " + i);
        }
        System.out.println("  本章收官：并发模型 → 实战场景 → 运营闭环，与 40-04/48-07 的流式与取消贯通");
    }

    public static void main(String[] args) throws Exception {
        aggregate("req-1001");
        aggregate("req-1002（大促高峰，模拟下游抖动）");
        budgetTable();
        ops();
    }
}
```

**课后练习**
1. 实现聚合网关完整版：含每下游独立 Semaphore、超时预算、降级占位与取消传播，压测对比串行/池/虚拟线程三版。
2. 设计聚合结果的短缓存策略（键设计、TTL、失效时机），量化命中率对下游 QPS 的削减。
3. 思考题：为什么聚合网关是“并发模型的试金石”？从 IO 密集度、扇出、超时预算三个维度综合评估三种模型的表现差异。
