### 50-01 阻塞与非阻塞的并发模型

**知识点**
- **高并发 IO 的本质矛盾：线程昂贵，等待免费**——平台线程（传统 Thread）1:1 映射内核线程，默认 1MB 栈，万级并发就耗尽内存与调度；但大部分时间线程在“等 IO”（DB/HTTP 响应），CPU 闲着。**并发模型的一切设计都是在解决“怎么用少量线程扛大量等待”**。
- **三种并发模型**：① **阻塞 + 线程池**（简单，线程数=并发上限，池满了排队）；② **非阻塞回调/响应式**（少量事件循环线程 + 异步回调，吞吐高但代码变“回调地狱/响应式链”）；③ **虚拟线程**（阻塞语义 + 海量线程，代码同步写、运行异步化——JDK 21 的答案）。**三者解决同一个问题，体验截然不同**。
- **响应式的代价在心智**：整条调用链必须全异步（一个阻塞调用卡死事件循环）、栈不可读（异常堆栈跨回调）、调试困难。**虚拟线程的承诺是“同步代码获得异步吞吐”**——这是 Java 官方对响应式复杂性的回应。
- **选型的第一问：IO 密集还是 CPU 密集**——IO 密集（等 DB/HTTP）虚拟线程收益巨大；CPU 密集虚拟线程无收益（核数决定），线程池依旧正确。
- **配套生态**：响应式要全套非阻塞驱动（R2DBC/非阻塞 HTTP 客户端）；虚拟线程**现有 JDBC 阻塞驱动直接可用**（这正是它省心的根源）。40-04 SSE 与 48-07 流式服务的连接并发也在此框架下重审。
- 易错点：CPU 密集任务用虚拟线程（只增加调度开销）；响应式链里混入阻塞调用（事件循环被卡，全服务卡顿）；把线程池大小调大当作高并发方案（上下文切换与内存双重反噬）；以为异步 = 更快单请求（异步提升吞吐，单请求延迟可能反而略增）。

**示例代码**
```java
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BlockingVsNonBlocking {
    /** 模拟一次下游 IO：等 100ms 返回 */
    static String callDownstream(String req) {
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return "resp:" + req;
    }

    static void model1() throws Exception {
        System.out.println("【模型①：阻塞 + 固定线程池】");
        try (ExecutorService pool = Executors.newFixedThreadPool(10)) {
            long start = System.currentTimeMillis();
            List<String> results = new ArrayList<>();
            List<java.util.concurrent.Future<String>> futures = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                final String req = "req-" + i;
                futures.add(pool.submit(() -> callDownstream(req)));
            }
            for (java.util.concurrent.Future<String> f : futures) {
                results.add(f.get());
            }
            long cost = System.currentTimeMillis() - start;
            System.out.printf("  10 线程处理 20 个请求（每个等 100ms）-> %dms%n", cost);
            System.out.println("  特点：代码直观；并发上限=线程数（第 11 个请求排队）");
        }
        System.out.println();
    }

    static void model2() throws Exception {
        System.out.println("【模型②：虚拟线程（阻塞语义 + 海量线程）】");
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            long start = System.currentTimeMillis();
            List<java.util.concurrent.Future<String>> futures = new ArrayList<>();
            for (int i = 0; i < 1000; i++) {
                final String req = "req-" + i;
                futures.add(pool.submit(() -> callDownstream(req)));
            }
            for (java.util.concurrent.Future<String> f : futures) {
                f.get();
            }
            long cost = System.currentTimeMillis() - start;
            System.out.printf("  1000 个虚拟线程（阻塞写法）-> %dms（1000 个平台线程不可想象）%n", cost);
        }
        System.out.println("  特点：同步代码 + 万级并发 —— 模型①的心智 + 更高吞吐");
        System.out.println();
    }

    static void model3() {
        System.out.println("【模型③：非阻塞/响应式（少量事件循环）】");
        System.out.println("  事件循环线程数 ≈ CPU 核数，等待期间线程处理别的请求");
        List<String> traits = List.of(
                "吞吐：极高（少量线程扛大量连接，Netty/Reactor 生态）",
                "代码：Mono/Flux 链式（不可阻塞、异常跨回调、栈难读）",
                "生态约束：全链路非阻塞（驱动/客户端都要非阻塞版本）",
                "适用：网关/推送/高扇出聚合（50-11 案例）");
        for (String t : traits) {
            System.out.println("  " + t);
        }
        System.out.println();
    }

    static void decision() {
        System.out.println("【第一决策：负载类型】");
        String[][] rows = {
                {"IO 密集（等 DB/HTTP）", "虚拟线程首选，响应式次之", "等待占比高才值得换模型"},
                {"CPU 密集（计算/压缩）", "固定大小线程池 = 核数", "虚拟线程无收益，只加调度开销"},
                {"超高并发连接（>10k）", "响应式/Netty", "百万连接场景虚拟线程也吃紧"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-16s %-22s %s%n", r[0], r[1], r[2]);
        }
    }

    public static void main(String[] args) throws Exception {
        model1();
        model2();
        model3();
        decision();
    }
}
```

**课后练习**
1. 实测三模型：同一 IO 任务（100ms 延迟 × 500 请求）分别用固定池/虚拟线程/模拟异步跑，记录吞吐与内存（-Xmx 限制下观察）。
2. 列出你系统的每个服务的负载类型（IO/CPU 密集），标注并发模型建议。
3. 思考题：为什么“单请求延迟”在三模型下差异不大而“吞吐”差异巨大？从线程等待的复用机制推导。

---

### 50-02 Reactor 核心：Mono/Flux 与背压

**知识点**
- **响应式的数据结构只有两个**：`Mono<T>`（0~1 个元素的异步序列）与 `Flux<T>`（0~N 个）。**它们是“未来的流”**：声明处理管道，订阅时才执行（惰性）。理解“声明 vs 执行”的分离是 Reactor 的入门关。
- **操作符即管道**：map/filter/flatMap/concatMap/zip/merge——**flatMap 决定并发度**（flatMapSequential 保序，flatMap 并发乱序完成）；错误传播沿管道（onErrorResume/onErrorMap），相当于同步代码的 try-catch 在管道里流动。
- **背压（backpressure）**：消费者控制生产者速度（request(n) 拉取）——43-08 反压在应用层的对应机制；无界流（无限 Flux）+ 慢消费者必须背压策略（buffer/drop/latest），否则内存爆炸。
- **订阅才执行**：`flux.map(...)` 只是搭管道；`subscribe()` 才启动——**忘了订阅是响应式第一 bug**（代码写了没效果）；WebFlux 框架帮你订阅，但测试与后台任务要自己 subscribe。
- **阻塞是响应式的毒药**：管道中任何一步调用阻塞 API（JDBC/Thread.sleep/同步锁）会卡死事件循环线程——**检测手段**：BlockHound 类工具在测试中拦截阻塞调用。
- **上下文传递**：MDC/trace_id 在响应式里不能用 ThreadLocal 直传（线程切换）——用 Reactor Context 或改造日志方案（42-02 在响应式的对应改造）。
- 易错点：Mono 里做阻塞 IO（卡死 worker 线程）；subscribe 泄漏（无限流不取消）；flatMap 无界并发（下游被打爆，要 concurrency 参数）；异常没接住（onError 缺失 -> 管道静默终止）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;
import java.util.concurrent.TimeUnit;

public class ReactorCore {
    /** JDK 自带的 Reactive Streams API（java.util.concurrent.Flow）演示背压 */
    static class TickSubscriber implements Flow.Subscriber<Integer> {
        private Flow.Subscription subscription;
        private final String name;
        private int received = 0;

        TickSubscriber(String name) {
            this.name = name;
        }

        public void onSubscribe(Flow.Subscription s) {
            this.subscription = s;
            s.request(3);                       // 背压：只拉 3 个（消费者控制速度）
            System.out.printf("  [%s] 订阅成功，request(3)%n", name);
        }

        public void onNext(Integer item) {
            received++;
            System.out.printf("  [%s] 收到 %d（共 %d）%n", name, item, received);
            if (received % 3 == 0) {
                subscription.request(3);        // 处理完再拉下一批
            }
        }

        public void onError(Throwable t) {
            System.out.printf("  [%s] 错误：%s%n", name, t.getMessage());
        }

        public void onComplete() {
            System.out.printf("  [%s] 完成%n", name);
        }
    }

    static void backpressureDemo() throws Exception {
        System.out.println("【背压：消费者 request(n) 控制生产速度】");
        try (SubmissionPublisher<Integer> publisher = new SubmissionPublisher<>()) {
            publisher.subscribe(new TickSubscriber("slow-consumer"));
            for (int i = 1; i <= 10; i++) {
                int lag = publisher.estimateMaximumLag();
                publisher.submit(i);
                TimeUnit.MILLISECONDS.sleep(50);
                if (i == 5) {
                    System.out.printf("  [生产者] 已提交 5 个，未消费积压（lag 估计）≈ %d%n", lag);
                }
            }
            publisher.close();
            TimeUnit.MILLISECONDS.sleep(300);
        }
        System.out.println("  生产 > 消费时：积压可观测、消费者限流拉取 —— 43-08 反压的应用层原语");
        System.out.println();
    }

    static void lazyVsEager() {
        System.out.println("【声明 vs 订阅：管道搭好不执行，订阅才跑】");
        List<String> executed = new ArrayList<>();
        Runnable pipeline = () -> {           // 模拟“声明好的管道”
            executed.add("map(加工)");
            executed.add("filter(过滤)");
        };
        System.out.println("  管道已声明：" + executed.isEmpty() + "（尚未执行任何步骤）");
        System.out.println("  订阅（subscribe）触发执行：");
        pipeline.run();
        System.out.println("  执行轨迹：" + executed);
        System.out.println("  响应式第一 bug：搭好管道忘了订阅 —— 代码写了没效果");
        System.out.println();
    }

    static void blockingPoison() {
        System.out.println("【阻塞是响应式的毒药】");
        List<String> consequences = List.of(
                "Mono 里调 JDBC 阻塞 50ms -> 事件循环线程被占 50ms",
                "事件循环线程 = CPU 核数（如 8）-> 并发上限骤降为 160 QPS",
                "对照：全部非阻塞时同 8 线程可扛数千并发",
                "检测：BlockHound 类工具在测试期拦截阻塞调用",
                "铁律：响应式链路里禁止 Thread.sleep / JDBC / 同步锁 IO");
        for (String c : consequences) {
            System.out.println("  " + c);
        }
        System.out.println();
    }

    public static void main(String[] args) throws Exception {
        backpressureDemo();
        lazyVsEager();
        blockingPoison();
    }
}
```

**课后练习**
1. 用 Flow API 实现一个"并发拉取 3 个下游 -> 合并结果"的管道，对比串行处理的总耗时。
2. 构造"慢消费者 + 无界生产"的场景，观察积压增长，然后加背压策略（限 request 速率）验证行为。
3. 思考题：为什么"忘了订阅"是响应式最常见的 bug？从声明与执行分离的语义分析，列出三个典型漏订阅场景。

---

### 50-03 WebFlux 的分层与陷阱

**知识点**
- **WebFlux 与 MVC 的并存**：Spring 5+ 两种栈——Servlet 栈（Tomcat，阻塞友好）与 Reactive 栈（Netty，WebFlux）。**选 WebFlux 的前提是整条链路非阻塞**：R2DBC（数据库）、WebClient（HTTP）、无阻塞锁。混栈是最常见的翻车起点。
- **Controller 返回 Mono/Flux 即接入异步**：框架负责订阅——**返回值类型就是契约**（返回 Mono 的接口天然支持流式/背压，40-04 SSE 在 WebFlux 下就是 Flux 返回值）。
- **陷阱清单**：① Mono 里 .block()（直接阻塞事件循环，测试环境看似正常、生产高并发雪崩）；② 在 controller 里手工 subscribe（自己订阅后框架拿不到结果，返回空）；③ 链路中同步锁/ThreadLocal（线程切换失效）；④ 日志 MDC 丢 trace（50-02 的上下文问题）；⑤ R2DBC 没上（DB 还是 JDBC 阻塞——整个改造白做）。
- **什么场景值得 WebFlux**：聚合网关、长连接推送、高扇出并行调用（下游多个 + 各自延迟）；**普通 CRUD 服务用 MVC + 虚拟线程更省心**（50-07 选型）。
- **与虚拟线程的关系**：JDK 21 后"为吞吐而 WebFlux"的必要性下降——**同步代码 + 虚拟线程达到相近吞吐**；WebFlux 的剩余价值在流式/背压语义（SSE、实时推送）与生态（Gateway/R2DBC 已是事实标准）。
- 易错点：为"显得先进"上 WebFlux（团队不熟 Reactor，隐蔽 bug 群）；block() 出现在生产代码（压测才暴露）；webflux + JDBC 混搭（假异步真阻塞）；错误处理缺失（onErrorReturn 全局兜底漏配）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class WebFluxPatterns {
    /** 用“小事件循环”模拟：2 个 worker 线程扛全部请求 */
    static final ExecutorService EVENT_LOOP = Executors.newFixedThreadPool(2);
    static final List<String> CALLS = new ArrayList<>();

    static String blockingDbCall() {
        try {
            Thread.sleep(50);                   // 模拟 JDBC 阻塞
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return "row";
    }

    static void eventLoopStarvation() throws Exception {
        System.out.println("【事件循环饥饿：一个阻塞调用拖垮全部并发】");
        for (int i = 0; i < 10; i++) {
            EVENT_LOOP.submit(() -> {
                CALLS.add(blockingDbCall());    // 毒药：worker 线程上阻塞 50ms
                return null;
            });
        }
        EVENT_LOOP.shutdown();
        EVENT_LOOP.awaitTermination(5, TimeUnit.SECONDS);
        System.out.printf("  10 个请求 × 50ms 阻塞 ÷ 2 个 worker = 至少 %.0fms 才能清空%n", 10 * 50 / 2.0);
        System.out.println("  期间这 2 个线程无法处理任何其他请求 —— 并发上限被阻塞锁死");
        System.out.println("  WebFlux 等价场景：Mono.fromCallable(::blockingDbCall) 未切弹性调度器");
        System.out.println();
    }

    static void subscribeTrap() {
        System.out.println("【手工 subscribe 陷阱：返回值拿不到】");
        List<String> wrong = List.of(
                "错误写法：pipeline.subscribe() 之后 return null",
                "    -> 框架拿到的是空响应（你自己订阅，框架没东西可消费）",
                "正确写法：return pipeline（把管道交给框架订阅）",
                "后台任务例外：无人消费的管道才需要自己 subscribe 并管理生命周期");
        for (String w : wrong) {
            System.out.println("  " + w);
        }
        System.out.println();
    }

    static void checklist() {
        System.out.println("【WebFlux 上线检查单】");
        List<String> items = List.of(
                "全链路非阻塞：R2DBC/WebClient，禁 JDBC/RestTemplate 阻塞客户端",
                "无 .block()：CI 静态扫描 + BlockHound 测试拦截",
                "无手工 subscribe 泄漏：返回管道交给框架",
                "trace_id 方案改造（Context 或 MDC 桥），禁裸 ThreadLocal",
                "错误兜底：全局 onError 处理 + 每条管道 onErrorReturn",
                "团队培训：Reactor 调试与排障技能确认");
        for (String i : items) {
            System.out.println("  [ ] " + i);
        }
        System.out.println("  任何一项不达标 -> 选 MVC + 虚拟线程（50-07）");
    }

    public static void main(String[] args) throws Exception {
        eventLoopStarvation();
        subscribeTrap();
        checklist();
    }
}
```

**课后练习**
1. 用"小事件循环"模拟复现阻塞饥饿：2 个 worker 承载 20 个含阻塞调用的任务，记录清空时间；去掉阻塞再对比。
2. 对比同一聚合接口的 MVC 与 WebFlux 实现，从代码行数、可读性、压测吞吐三个维度记录结论。
3. 思考题：为什么"一个 .block()"能在生产雪崩？从事件循环线程数与请求并发的关系推导传播范围。

---

### 50-04 虚拟线程原理：载体线程与挂载

**知识点**
- **虚拟线程 = JVM 管理的轻量线程**：不映射内核线程，栈存在堆上（初始几百字节，按需增长）——**百万级虚拟线程可行**。JDK 21 正式发布（Project Loom 的第一块成果）。
- **载体线程（carrier thread）机制**：虚拟线程运行时"挂载"到一个载体线程（ForkJoinPool，默认 = CPU 核数）；**阻塞时（IO/sleep/锁等待）虚拟线程从载体上"卸载"，载体去跑别的虚拟线程**——阻塞不再占用 OS 线程。恢复时重新挂载（可能在另一个载体上）。
- **卸载的条件**：阻塞在可感知的操作（网络 IO、sleep、Object.wait、ReentrantLock）——**JDK 把这些阻塞点改造成了"让出载体"的挂起点**。兼容性奇迹：现有阻塞代码不改就能享受。
- **不卸载的场景（pinning）**：**synchronized 块内阻塞**、native 方法（JNI）内阻塞——载体被钉住，其他虚拟线程无法复用。**JDK 21 的最大坑**（50-06 细讲，JDK 24 已修复 synchronized）。
- **应该怎么用**：**每任务一线程（thread-per-request）回归**——虚拟线程廉价，不再需要池化；`Executors.newVirtualThreadPerTaskExecutor()` 是标准姿势；**不要池化虚拟线程、不要给虚拟线程设上限池**（要限并发用 Semaphore）。
- **可观测**：`Thread.isVirtual()` 判断；JFR 事件（pinning/提交/卸载）；**线程名要设置**（海量虚拟线程的 dump 是茫茫多，命名规范 42-02 同源）。
- 易错点：池化虚拟线程（newFixedThreadPool 包虚拟线程工厂——失去意义还限制并发）；在虚拟线程里跑 CPU 重活（占用载体，影响全部）；用虚拟线程数当并发限流手段（改用 Semaphore 显式限流）；忽略 pinning（synchronized 里调阻塞 IO）。

**示例代码**
```java
import java.time.Duration;
import java.util.concurrent.Executors;

public class VirtualThreadInternals {
    static void mountUnmount() throws Exception {
        System.out.println("【挂载与卸载：阻塞不占载体】");
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 5; i++) {
                final int id = i;
                executor.submit(() -> {
                    System.out.printf("  任务%d 开始（虚拟线程=%s）%n", id, Thread.currentThread());
                    Thread.sleep(100);              // 阻塞点：卸载，载体去跑别的任务
                    System.out.printf("  任务%d 完成（可能换了载体）%n", id);
                    return null;
                });
            }
        }                                    // close 等待全部完成
        System.out.println("  5 个任务并发完成；载体线程只有 CPU 核数个");
        System.out.println();
    }

    static void scaleProof() throws Exception {
        System.out.println("【规模证明：10 万虚拟线程同时 sleep】");
        long start = System.currentTimeMillis();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 100_000; i++) {
                executor.submit(() -> {
                    Thread.sleep(500);
                    return null;
                });
            }
        }
        long cost = System.currentTimeMillis() - start;
        System.out.printf("  10 万任务 × 500ms -> 总耗时 %dms（并发挂起，内存仅 MB 级栈）%n", cost);
        System.out.println("  对照平台线程：10 万 × 1MB 栈 = 100GB，直接不可能");
        System.out.println();
    }

    static void naming() {
        System.out.println("【命名规范：海量线程的可观测前提】");
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            executor.submit(() -> {
                Thread.currentThread().setName("order-pay-" + Thread.currentThread().threadId());
                System.out.println("  " + Thread.currentThread());
                return null;
            });
        }
        System.out.println("  dump/日志里能定位业务语义（42-02 结构化日志同源）");
    }

    public static void main(String[] args) throws Exception {
        mountUnmount();
        scaleProof();
        naming();
    }
}
```

**课后练习**
1. 实测本章两个示例，记录 10 万虚拟线程的耗时与堆内存（-Xmx256m 下验证）。
2. 写一个 pinning 检测脚本：JFR 事件（jdk.VirtualThreadPinned）统计 pinned 时长 Top 类与方法。
3. 思考题：为什么“每任务一线程”在平台线程时代是错误实践而在虚拟线程时代是推荐？从线程成本与池化目的（复用昂贵资源）的演变分析。

---
