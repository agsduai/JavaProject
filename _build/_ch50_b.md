### 50-05 虚拟线程实战与并发编排

**知识点**
- **虚拟线程的编排原语**：`newVirtualThreadPerTaskExecutor()`（每任务一线程）+ Future/CompletionStage 编排；**结构化并发**（StructuredTaskScope，JDK 21 预览 API——任务生命周期嵌套在作用域内、取消自动传播）理念重要，本课用 Executor + Future 模拟其语义（避免 preview 编译门槛），生产待其转正。
- **扇出聚合模式**（50-11 案例原型）：并行调 N 个下游（各自虚拟线程）-> 全部成功合并 / 任一失败快速失败或降级——**timeout 与取消要显式管理**：`Future.get(timeout)` + 取消其余任务，避免“快的等慢的”。
- **与 Semaphore 的限流配合**：虚拟线程廉价但**下游不便宜**——并发保护下游用 `Semaphore.acquire()`（阻塞在信号量上会卸载，不占载体）——**线程数无限、下游并发有限**的正确表达。
- **与现有设施的关系**：ThreadLocal 可用（每虚拟线程独立副本，但百万副本要算内存账 50-06）；连接池不变——DB 连接仍是稀缺资源，虚拟线程排队等连接，**瓶颈平移到池，吞吐上限由池决定**（池大小重新评估）；Spring Boot 3.2+ 一行开启 `spring.threads.virtual.enabled=true`。
- **错误处理语义**：单任务异常不影响其他任务（各自 Future）；批量场景收集异常而不是快速失败（报告型任务）；关键路径快速失败 + 取消其余（成本控制）。
- 易错点：虚拟线程内再套自定义线程池（资源叠加失控）；Future.get 无超时（一个慢下游拖死整批）；取消不传播（幽灵任务继续跑 43 章概念）；把虚拟线程当异步框架（它是并发原语，生态在追赶 50-07）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class VirtualThreadPractice {
    static String call(String name, long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return name + "-ok";
    }

    static void fanoutAggregation() throws Exception {
        System.out.println("【扇出聚合：并行 3 下游 + 总超时】");
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            long start = System.currentTimeMillis();
            Future<String> f1 = pool.submit(() -> call("stock", 120));
            Future<String> f2 = pool.submit(() -> call("price", 80));
            Future<String> f3 = pool.submit(() -> call("promo", 200));
            String r1 = f1.get(500, TimeUnit.MILLISECONDS);
            String r2 = f2.get(500, TimeUnit.MILLISECONDS);
            String r3 = f3.get(500, TimeUnit.MILLISECONDS);
            System.out.printf("  合并：%s / %s / %s -> 耗时 %dms（≈最慢者而非之和）%n",
                    r1, r2, r3, System.currentTimeMillis() - start);
        }
        System.out.println("  对照串行 400ms；并行收益 = max(下游) —— IO 密集的核心红利");
        System.out.println();
    }

    static void semaphoreGuard() throws Exception {
        System.out.println("【Semaphore：线程无限，下游并发有限】");
        Semaphore permit = new Semaphore(10);       // 下游只承受 10 并发
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<String>> futures = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                futures.add(pool.submit(() -> {
                    permit.acquire();               // 阻塞在信号量：卸载，不占载体
                    try {
                        return call("db-call", 50);
                    } finally {
                        permit.release();
                    }
                }));
            }
            for (Future<String> f : futures) {
                f.get();
            }
        }
        System.out.println("  100 个虚拟线程完成；同一时刻下游最多 10 并发");
        System.out.println("  关键：限流点从“线程池大小”变成“显式 Semaphore”（50-06 池化误区）");
        System.out.println();
    }

    static void timeoutWithCancel() throws Exception {
        System.out.println("【超时与取消：慢下游不拖垮整批】");
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<String> fast = pool.submit(() -> call("fast", 50));
            Future<String> slow = pool.submit(() -> call("slow", 2000));
            String r = fast.get(200, TimeUnit.MILLISECONDS);
            slow.cancel(true);
            System.out.printf("  fast=%s；slow 已取消（释放资源，不拖尾）%n", r);
        }
        System.out.println("  结构化并发（preview API）将把这类模式标准化：作用域内自动取消传播");
        System.out.println("  生产注意：cancel(true) 中断要被任务体响应（sleep/阻塞点感知中断）");
    }

    public static void main(String[] args) throws Exception {
        fanoutAggregation();
        semaphoreGuard();
        timeoutWithCancel();
    }
}
```

**课后练习**
1. 实现扇出聚合的降级版：下游超时返回占位值（合并继续），对比“快速失败”与“降级合并”两种语义的业务差异。
2. 用压测验证“连接池是真瓶颈”：池 10 与池 50 的吞吐差异，推导池大小公式（池 ≈ 目标并发 × 下游耗时占比）。
3. 思考题：为什么虚拟线程时代“限流用 Semaphore 而不是线程池大小”？从线程与并发解耦的本质分析两种限流点的差异。

---

### 50-06 虚拟线程的坑

**知识点**
- **Pinning（钉住）：synchronized 内阻塞不卸载**——虚拟线程阻塞在 `synchronized` 块内时**无法从载体卸载**，载体线程被占死；高并发下“核数个载体”全部被钉住 -> 服务假死。**JDK 21 生产第一坑**（JDK 24 已修复 synchronized 的 pinning，21 必须代码层规避）。
- **pinning 的检测与修复**：JFR 事件 `jdk.VirtualThreadPinned` 记录钉住时长与栈——筛 >10ms 事件按栈聚合 Top 方法；命中 synchronized -> **热点改 ReentrantLock**（可卸载），不必全局替换（先测后改）。
- **池化误区**：`newFixedThreadPool(200, virtualFactory)` 是反模式——池化为了复用昂贵资源，虚拟线程廉价无需复用；池引入排队与上限**反而废掉虚拟线程的价值**。限并发用 Semaphore（显式语义），不用池大小。
- **ThreadLocal 的内存账**：百万虚拟线程 × 每个 ThreadLocal 副本（如大对象缓存）= 内存放大——**审计**每个 ThreadLocal 存储的对象；方案：显式传参 / ScopedValue（preview）/ 限制规模。trace 方案在虚拟线程下 ThreadLocal 可用但要算账（42-02 的 MDC 在虚拟线程的对应改造）。
- **CPU 任务的干扰**：虚拟线程跑长计算霸占载体（无法卸载）——**CPU 活留给固定平台线程池**，虚拟线程只做 IO 编排；混合负载用隔离执行器（47-08 弹性的线程维度）。
- **兼容性排查**：JDBC 阻塞驱动天然兼容（阻塞即卸载）✓；老版本连接池/驱动内部 synchronized+IO 组合要排查；升级 JDK 24 可大幅缓解 synchronized 问题（升级评估项进 41 章 ADR）。
- 易错点：synchronized 包住 HTTP 调用做“防重复”（pinning 灾难）；全局替换 synchronized（应热点优先）；ThreadLocal 缓存大对象（百万副本放大）；开启开关就宣布完成（pinning 与连接池两关都要过）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;

public class VtPitfalls {
    static final ReentrantLock lock = new ReentrantLock();
    static int counter = 0;

    static void pinningAwareness() throws Exception {
        System.out.println("【pinning vs 可卸载锁：同一场景两种实现】");
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            System.out.println("  反模式：synchronized(lockObj){ blockingHttpCall(); } -> 载体被钉住");
            System.out.println("  正解：ReentrantLock.lock(){ blockingHttpCall(); } -> 阻塞时卸载");
            for (int i = 0; i < 8; i++) {
                pool.submit(() -> {
                    lock.lock();
                    try {
                        counter++;                    // 短临界区（无 IO）
                    } finally {
                        lock.unlock();
                    }
                    return null;
                });
            }
        }
        System.out.printf("  ReentrantLock 版计数完成：%d（阻塞可卸载，吞吐正常）%n%n", counter);
    }

    static void pinningDetection() {
        System.out.println("【pinning 检测：JFR 事件法】");
        List<String> steps = List.of(
                "1) 启动加 -XX:StartFlightRecording=filename=rec.jfr",
                "2) 事件 jdk.VirtualThreadPinned 记录“钉住”时长与栈",
                "3) 筛 duration > 10ms，按栈聚合 Top 方法",
                "4) Top 命中 synchronized -> 改 ReentrantLock（热点优先）",
                "5) 回归压测：吞吐与 P99 恢复即修复确认");
        for (String s : steps) {
            System.out.println("  " + s);
        }
        System.out.println();
    }

    static void threadLocalAudit() {
        System.out.println("【ThreadLocal 内存审计】");
        List<String> audit = List.of(
                "盘点：grep ThreadLocal，列出每个存储对象的大小",
                "危险：虚拟线程 × 1KB 副本 × 100 万 = 1GB 隐性放大",
                "方案A：显式传参（traceId 走方法参数）",
                "方案B：ScopedValue（preview）作用域绑定",
                "方案C：保持 ThreadLocal 但限制虚拟线程量（与目标矛盾）");
        for (String a : audit) {
            System.out.println("  " + a);
        }
        System.out.println();
    }

    static void poolMisuse() {
        System.out.println("【池化反模式与正确姿势】");
        String[][] rows = {
                {"反模式", "newFixedThreadPool(200, virtualFactory)", "池化廉价线程=自废武功"},
                {"反模式", "用池大小限并发", "改用 Semaphore（显式语义）"},
                {"正确", "newVirtualThreadPerTaskExecutor", "每任务一线程，随建随弃"},
                {"正确", "限下游并发用 Semaphore；限 CPU 用平台池", "并发控制点分离"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-4s %-40s %s%n", r[0], r[1], r[2]);
        }
    }

    public static void main(String[] args) throws Exception {
        pinningAwareness();
        pinningDetection();
        threadLocalAudit();
        poolMisuse();
    }
}
```

**课后练习**
1. 写一个 pinning 复现器：10 个虚拟线程在 synchronized 内 sleep，观察吞吐坍塌；改 ReentrantLock 对比。
2. 用 JFR 采集模拟程序 1 分钟，统计 pinning Top3 栈，给出修复方案。
3. 思考题：为什么 JDK 24 修复了 synchronized 的 pinning，而 21 时代要靠改代码？从监视器实现与载体卸载点的技术约束分析。

---

### 50-07 响应式 vs 虚拟线程选型

**知识点**
- **两者解决同一问题的两条路**：响应式（2017~）：编程模型换非阻塞，吞吐换复杂度；虚拟线程（2023~）：运行时换载体调度，**保留同步编程模型**。**JDK 21 之后，默认答案从响应式移向虚拟线程**——同步代码 + 海量并发的承诺兑现。
- **决策矩阵六项**：负载类型（IO/CPU 密集，50-01）、并发规模（<10k 虚拟线程够用；极端长连接规模 Netty 生态仍占优）、流式/背压刚需（Flux 语义不可替代）、团队熟练度（Reactor 熟练的沉默成本）、存量栈（WebFlux 不重写）、JDK 版本（虚拟线程需 21+）。**六项打分，不是直觉站队**。
- **推荐形态**：新 CRUD/业务服务 -> MVC + 虚拟线程（默认答案）；实时推送/SSE -> WebFlux（背压语义剩余价值区）；聚合网关 -> 两者皆可按团队栈定；存量 WebFlux -> 保持 + 新代码虚拟线程试点（渐进不重写，49-10 成本框架）。
- **代码与调试体验对比**：同一聚合任务——WebFlux 链式 15 行（栈跨回调、断点难）vs 虚拟线程同步 12 行（栈可读、可单步调试）；**可维护性在功能等价时占优**。
- **生态现状**：R2DBC/WebClient 成熟；虚拟线程生态在追赶——**“兼容现有阻塞客户端”是最大优势**（JDBC/Redis 客户端无需替代品，改造成本近零）。
- 易错点：选型当站队（全站一刀切）；用跑分定选型（自己的 IO 形态才是真实依据）；忽略存量成本（WebFlux 推倒重写是负收益）；忘记响应式的背压/流式语义在特定场景不可替代（40-04 SSE、43 章反压）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.List;

public class ReactiveVsVt {
    static void decisionMatrix() {
        System.out.println("【六项决策打分】");
        String[][] rows = {
                {"负载类型", "IO 密集两者皆可；CPU 密集都不选", "首要过滤项"},
                {"并发规模", "<10k 并发虚拟线程够用；极端规模 Netty", "48-07 连接账"},
                {"流式/背压", "Flux 语义刚需 -> WebFlux", "SSE 推送/无界流"},
                {"团队熟练度", "Reactor 熟 -> 利用沉默成本；否则虚拟线程", "认知成本权重"},
                {"存量栈", "已有 WebFlux 不重写；MVC 渐进加虚拟线程", "重构成本"},
                {"JDK 版本", "虚拟线程需 21+（全书工程基座同版）", "升级是前置项"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-10s %-34s %s%n", r[0], r[1], r[2]);
        }
        System.out.println();
    }

    static void recommendedShapes() {
        System.out.println("【典型架构形态】");
        String[][] shapes = {
                {"新 CRUD/业务服务", "MVC + 虚拟线程 + Boot 3.2 开关", "默认答案"},
                {"聚合网关", "WebFlux 或虚拟线程扇出（50-11）", "按团队栈定"},
                {"实时推送/SSE", "WebFlux（Flux 背压语义）", "响应式剩余价值区"},
                {"存量 WebFlux 服务", "保持 + 新代码虚拟线程试点", "渐进不重写"},
        };
        for (String[] s : shapes) {
            System.out.printf("  %-14s %-36s %s%n", s[0], s[1], s[2]);
        }
        System.out.println();
    }

    static void codeComparison() {
        System.out.println("【同一聚合任务：两种写法对比】");
        System.out.println("  WebFlux：Mono.zip(c1,c2,c3).map(merge) —— 链式约 15 行，栈跨回调");
        System.out.println("  虚拟线程：3 submit + 3 get —— 同步 12 行，栈可读可断点");
        System.out.println("  结论：功能等价时，可维护性占优 —— 49 章架构观在并发层的延续");
        System.out.println();
    }

    static void migrationPrinciples() {
        System.out.println("【WebFlux -> 虚拟线程的渐进原则】");
        List<String> rules = List.of(
                "不重写：存量 WebFlux 维持（重构成本 > 收益）",
                "新边界用新模型：新服务/新模块虚拟线程",
                "观测层两栈通用：trace/日志方案保持（42 章）",
                "试点先行：非核心服务先验证（41-07 灰度纪律）",
                "度量驱动：吞吐/P99/故障率对比后再扩大");
        for (String r : rules) {
            System.out.println("  - " + r);
        }
    }

    public static void main(String[] args) {
        decisionMatrix();
        recommendedShapes();
        codeComparison();
        migrationPrinciples();
    }
}
```

**课后练习**
1. 用六项决策矩阵为你团队的两个服务打分，得出各自的并发模型建议并记录理由。
2. 实现同一聚合接口的双版本（响应式模拟与虚拟线程），压测对比吞吐/P99 与代码可维护性。
3. 思考题：为什么“已有成熟 WebFlux 系统不必重写”？用 49-10 演进式改造的成本框架分析重写与保持的账。

---

### 50-08 CompletableFuture 与两者整合

**知识点**
- **CompletableFuture 是“回调与组合”的中坚**：JDK 8 引入，_supplyAsync/thenApply/thenCombine/allOf 提供组合能力——**它是响应式思想在 JDK 内的最早形态**，也是虚拟线程前“并发编排”的事实标准（32 章 JUC 的编排层）。
- **CF 的位置在三者地图上**：CF（回调组合，JDK 内置）< Reactor（完整响应式流 + 背压）< 虚拟线程（同步语义）——**CF 依然是默认可用项**：不引框架、不用新 JDK 特性、语义够用的编排场景用它。
- **CF 与虚拟线程的配合**：CF 的默认执行器在 JDK 21 之后是虚拟线程执行器（`CompletableFuture` 静态方法的 async 变体走虚拟线程）——**回调写法 + 虚拟线程执行**融合；`orTimeout` 提供超时（48-07 预算思想的 JDK 原生）。
- **CF 的坑**：默认池是 ForkJoinPool.commonPool（CPU 密集导向，IO 任务会饿死其他并行流）——**IO 任务显式传 executor**；异常处理链遗漏（exceptionally/handle 缺失 -> 异常静默吞掉）；thenApply 与 thenApplyAsync 的线程语义混淆。
- **迁移视角**：存量 CF 代码无需重写——虚拟线程时代 CF 仍是有效编排工具；**新代码的简单场景用虚拟线程同步写，复杂组合（扇入/竞速）用 CF**——按表达式复杂度选。
- 易错点：commonPool 跑阻塞 IO（全局并行流与 CF 一起挨饿）；allOf 后不 collect 结果（只等完成不取值）；超时缺失（orTimeout 不用，请求悬挂）；异常链断裂（上一阶段异常未处理，下一阶段不执行也无提示）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class CfIntegration {
    static final ExecutorService VIRTUAL = Executors.newVirtualThreadPerTaskExecutor();

    static CompletableFuture<String> call(String name, long ms) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Thread.sleep(ms);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return name + "-ok";
        }, VIRTUAL);
    }

    static void combination() throws Exception {
        System.out.println("【CF 组合：扇入合并 + 竞速】");
        long start = System.currentTimeMillis();
        CompletableFuture<String> all = call("stock", 120)
                .thenCombine(call("price", 80), (s, p) -> s + "+" + p);
        System.out.println("  thenCombine 合并：" + all.get(1, TimeUnit.SECONDS)
                + "（耗时 " + (System.currentTimeMillis() - start) + "ms）");
        CompletableFuture<String> race = call("fast-source", 50)
                .applyToEither(call("slow-source", 300), s -> s);
        System.out.println("  applyToEither 竞速：" + race.get(1, TimeUnit.SECONDS));
        System.out.println();
    }

    static void timeoutAndError() throws Exception {
        System.out.println("【orTimeout 超时与异常链】");
        CompletableFuture<String> hung = call("hung", 2000)
                .orTimeout(200, TimeUnit.MILLISECONDS)
                .exceptionally(ex -> "timeout-fallback");
        System.out.println("  超时兜底结果：" + hung.get(1, TimeUnit.SECONDS));
        CompletableFuture<String> failed = CompletableFuture.<String>failedFuture(
                new IllegalStateException("downstream error"))
                .handle((v, ex) -> ex == null ? v : "degraded:" + ex.getMessage());
        System.out.println("  handle 转译异常：" + failed.get(1, TimeUnit.SECONDS));
        System.out.println("  规则：每个 CF 链必有超时（orTimeout）与异常终态（handle）");
        System.out.println();
    }

    static void allOfCollect() throws Exception {
        System.out.println("【allOf 的正确姿势：等完成 + 收集结果】");
        List<CompletableFuture<String>> futures = List.of(
                call("a", 50), call("b", 80), call("c", 60));
        CompletableFuture<Void> all = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0]));
        all.get(1, TimeUnit.SECONDS);
        List<String> results = new ArrayList<>();
        for (CompletableFuture<String> f : futures) {
            results.add(f.join());
        }
        System.out.println("  收集结果：" + results);
        System.out.println("  坑对照：只 allOf().get() 不 join() —— 异常与结果都拿不到");
    }

    public static void main(String[] args) throws Exception {
        combination();
        timeoutAndError();
        allOfCollect();
    }
}
```

**课后练习**
1. 用 CF 实现“三下游竞速取最快 + 超时兜底”，对比虚拟线程扇出实现的代码量与可读性。
2. 复现 commonPool 阻塞饥饿：CF 默认执行器跑阻塞任务 + 并行流同时工作，观察互扰；改显式虚拟执行器对比。
3. 思考题：为什么 CF 在虚拟线程时代仍有一席之地？从“组合表达式复杂度”与两种写法的适用边界分析选型。
