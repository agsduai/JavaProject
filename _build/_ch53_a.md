### 53-01 阿里生态全景与选位

**知识点**
- **Spring Cloud Alibaba 是通用 Spring Cloud 规范的阿里实现集**：29 章讲的注册中心/配置中心/网关/熔断是**抽象接口**，阿里栈给出具体落地——Nacos（注册+配置二合一）、Sentinel（限流熔断）、Seata（分布式事务）、Dubbo（RPC）、RocketMQ（消息，28 章已讲语义）。**学过 29 章的原理，本章只学"这套实现怎么用、有什么差异"**。
- **组件选位图**：注册发现=Nacos naming；配置=Nacos config（对比 Apollo/Azure 类）；RPC=Dubbo 或 OpenFeign；限流熔断=Sentinel（对比 Hystrix/Resilience4j）；分布式事务=Seata（对比 39 章本地消息表）；链路=SkyWalking（对比 42 章 OpenTelemetry）。**每个组件都有"通用替代品"**——掌握原理后替换组件是低成本的。
- **版本与兼容矩阵是第一坑**：Spring Boot / Spring Cloud / Spring Cloud Alibaba 三者版本必须按官方矩阵对齐（如 Boot 3.2.x 对应 SC 2023.0.x 对应 SCA 2023.0.x）——**错配的典型症状**：Nacos 注册成功但服务发现为空、配置监听不生效。上线前把矩阵写进 ADR。
- **与自研/开源替代的取舍**：阿里栈的优势是**文档全、社区活跃、国内生产案例多**；代价是**与特定云厂商生态绑定较深**（ACK/ARMS 联动）。多云或出海系统要评估替代组件的迁移成本（49 章演进框架）。
- **本章的模拟策略**：全部组件用纯 JDK 模拟核心行为（Nacos 的心跳注册表、Sentinel 的滑动窗口、Seata 的两阶段、Dubbo 的代理调用）——**机制理解优先于 API 记忆**，API 在真实环境一周即可上手。
- **生产落地清单预览**：注册中心集群化（45 章共识）、配置灰度（41 章思想）、限流规则持久化（Nacos 存储）、事务模式选型（AT vs TCC 51-09）、链路采样（42-08）——**每个组件的"生产化动作"本章逐个讲**。
- 易错点：组件堆砌（每个都上但没用对场景）；版本错配硬扛；把 Sentinel 当配额管理（它是流量防护不是计费）；Seata AT 模式用在高性能核心链路（全局锁的代价）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AlibabaLandscape {
    record Component(String name, String duty, String abstraction, String alternative) {
    }

    static void landscape() {
        System.out.println("【阿里栈组件选位图】");
        List<Component> cs = List.of(
                new Component("Nacos", "注册中心 + 配置中心", "DiscoveryClient / Config", "Eureka/ZK/Consul"),
                new Component("Sentinel", "限流/熔断/降级", "CircuitBreaker 接口", "Resilience4j/Hystrix"),
                new Component("Seata", "分布式事务", "PlatformTransactionManager 扩展", "本地消息表/TCC 手写"),
                new Component("Dubbo", "RPC 框架", "服务调用抽象", "OpenFeign/gRPC"),
                new Component("RocketMQ", "消息队列", "Message 抽象", "Kafka/RabbitMQ（28 章）"),
                new Component("SkyWalking", "链路追踪", "Observability API", "OpenTelemetry（42 章）"));
        for (Component c : cs) {
            System.out.printf("  %-10s %-16s 抽象=%-28s 替代=%s%n",
                    c.name(), c.duty(), c.abstraction(), c.alternative());
        }
        System.out.println("  学过原理：换实现是配置级成本；只学 API：换实现是重学");
        System.out.println();
    }

    static void versionMatrix() {
        System.out.println("【版本矩阵：三件套对齐是第一坑】");
        Map<String, String> matrix = new LinkedHashMap<>();
        matrix.put("Spring Boot", "3.2.x");
        matrix.put("Spring Cloud", "2023.0.x");
        matrix.put("Spring Cloud Alibaba", "2023.0.x");
        for (Map.Entry<String, String> e : matrix.entrySet()) {
            System.out.printf("  %-20s %s%n", e.getKey(), e.getValue());
        }
        List<String> symptoms = List.of(
                "错配症状1：Nacos 注册成功但服务发现列表为空",
                "错配症状2：配置监听不回调（@RefreshScope 失效）",
                "错配症状3：Sentinel 注解不生效（切面未注册）");
        for (String s : symptoms) {
            System.out.println("  " + s);
        }
        System.out.println("  处方：版本矩阵进 ADR；升级按官方兼容表三件套同步");
        System.out.println();
    }

    static void simulationStrategy() {
        System.out.println("【本章模拟策略：机制优先】");
        List<String> rules = List.of(
                "Nacos：用内存注册表模拟心跳注册/剔除（45 章共识底座）",
                "Sentinel：滑动窗口 + 令牌桶真实计算（42/44 章思想）",
                "Seata：两阶段提交状态机真跑（45 章基础）",
                "Dubbo：JDK 代理模拟 RPC 调用与负载均衡（50 章基础）",
                "目的：生产环境 API 一周上手，机制不懂则处处是坑");
        for (String r : rules) {
            System.out.println("  " + r);
        }
    }

    public static void main(String[] args) {
        landscape();
        versionMatrix();
        simulationStrategy();
    }
}
```

**课后练习**
1. 画出你公司（或假想项目）的阿里栈选位图：每个组件标注"用的什么、替代品是谁、替换成本"。
2. 实地验证版本错配的一个症状：故意用不匹配的 SC 版本起服务，记录现象与诊断过程。
3. 思考题：为什么"学原理 > 学 API"？用 Nacos 与 Eureka 的切换成本做论证（都实现注册中心抽象）。

---

### 53-02 Nacos 注册中心实战

**知识点**
- **Nacos naming 的核心行为**（29 章原理的 Nacos 版）：服务注册（PUT /nacos/v1/ns/instance）、心跳续约（默认 5s，15s 未收到标记不健康，30s 剔除——**比 ZK 宽松，容忍网络抖动**）、服务发现（客户端拉取 + UDP 推送变更）、保护阈值（健康实例占比低于阈值时返回全部实例——**防止雪崩，牺牲准确性**）。
- **临时实例 vs 持久实例**：临时实例（ephemeral=true，默认）——客户端心跳维持，掉线自动删（适合服务发现）；持久实例——服务端存储，健康检查由服务端主动探（适合数据库/Redis 等基础设施登记）。**选错的典型事故**：把 DB 实例注册成临时实例，网络抖动被剔除导致配置漂移告警。
- **集群与一致性**：Nacos 集群用 Raft（45 章正是它的底座）维护持久数据，临时实例走 **Distro 协议**（AP 倾向：异步复制，分区时各自可用）——**注册中心选 AP**（29 章结论的组件级印证：宁可列表短暂不全，不可全部停服）。
- **命名空间与分组**：namespace 隔离环境（dev/test/prod 各一个），group 隔离业务域——**环境隔离用 namespace 而不是 group**（group 是逻辑分域，跨 group 不互通）。混淆两者的后果：test 的服务发现到 prod 实例。
- **生产化动作**：3 节点集群（45-10 共识规模）、客户端心跳参数调优（GC 停顿 >5s 的服务调大超时）、保护阈值配置、注册中心自身监控（53-01 版本矩阵 + 42 章四信号）。
- 易错点：注册中心单点（全公司服务发现挂）；临时/持久实例混用错场景；namespace 与 group 混淆；保护阈值不理解就关掉（雪崩防线消失）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.List;

public class NacosRegistry {
    record Instance(String service, String ip, boolean healthy, boolean ephemeral) {
    }

    /** 保护阈值：健康实例不足时返回全部（宁返回可能坏的，不返回空） */
    static List<Instance> discover(List<Instance> all, String service, double protectThreshold) {
        List<Instance> healthy = all.stream()
                .filter(i -> i.service().equals(service) && i.healthy())
                .toList();
        List<Instance> allOfService = all.stream()
                .filter(i -> i.service().equals(service))
                .toList();
        double healthyRatio = allOfService.isEmpty() ? 0
                : (double) healthy.size() / allOfService.size();
        if (healthyRatio < protectThreshold && !allOfService.isEmpty()) {
            System.out.printf("  健康占比 %.2f < 阈值 %.2f -> 保护模式：返回全部 %d 实例%n",
                    healthyRatio, protectThreshold, allOfService.size());
            return allOfService;
        }
        System.out.printf("  正常返回健康实例 %d 个%n", healthy.size());
        return healthy;
    }

    public static void main(String[] args) {
        List<Instance> registry = new ArrayList<>(List.of(
                new Instance("order", "10.0.0.1", true, true),
                new Instance("order", "10.0.0.2", false, true),
                new Instance("order", "10.0.0.3", false, true)));
        discover(registry, "order", 0.5);
        System.out.println();
        System.out.println("  保护模式的意义：宁可把流量给可能恢复的实例，也不让上游无服务可用");
        System.out.println("  对照：关闭保护 -> 返回 0 个实例 -> 上游全线 503（雪崩起点）");
    }
}
```

**课后练习**
1. 实现完整 Nacos 模拟器（注册/心跳/剔除/保护阈值/订阅回调），复现"1 实例假死 -> 保护模式 -> 恢复"全周期。
2. 对比临时实例与持久实例的行为差异（心跳维持 vs 服务端探测），各给一个适用场景。
3. 思考题：为什么注册中心选 AP 而配置中心选 CP（53-03）？从"服务列表短暂不全"与"配置分发错误"的后果差异论证。

---

### 53-03 Nacos 配置中心与灰度

**知识点**
- **Nacos config 的核心机制**：配置按 dataId+group+namespace 三元组定位；客户端**长轮询**（默认 30s 超时窗口，配置变更立即返回——比纯轮询省且快）；MD5 校验防重推；**@RefreshScope** 的实现本质是 Bean 销毁重建（49 章生命周期思想）。
- **配置灰度发布**：Nacos 支持**按 IP/ Beta 发布**——配置先推给指定实例，验证后再全量（41-07 金丝雀的配置版）；**回滚 = 发布历史版本**（历史变更保留，45-06 MVCC 同源）。
- **配置治理三原则**：① **配置分类**——数据库连接类（低频高敏）/业务开关类（高频）分 dataId；② **变更规范**——先在 test namespace 验证 + 变更说明必填 + 灰度；③ **敏感配置**——Jasypt 加密或接 KMS，明文密码进配置中心 = 36 章密钥纪律违规。
- **大配置的坑**：单个 dataId 过大（推送包限制与解析压力）；@RefreshScope 滥用（大对象重建导致抖动，应只标注真正需要热更的 Bean）；**配置监听线程数**默认较小，大量 dataId 监听要调优。
- **与 45-11 的对照**：45-11 手写了配置客户端（快照/单调/拉齐），Nacos 全部内置——**手写过的机制在产品里逐个对应**：快照=本地缓存文件、单调=revision、拉齐=兜底全量拉取。
- 易错点：长轮询超时调太短（请求风暴）；灰度验证期把 Beta 当全量（未全量就宣布完成）；配置变更无审计（谁改的查不到，41-10）；RefreshScope 标注在启动重的 Bean（变更瞬间 GC/重建风暴）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.List;

public class NacosConfig {
    record ConfigSnapshot(String dataId, long version, String content, String md5) {
    }

    /** 长轮询：变更立即返回，无变更等满窗口 */
    static void longPolling(List<ConfigSnapshot> server, ConfigSnapshot local, int windowMs) {
        ConfigSnapshot hit = server.stream()
                .filter(s -> s.dataId().equals(local.dataId()) && s.version() > local.version())
                .findFirst().orElse(null);
        if (hit != null) {
            System.out.printf("  [%dms] 检测到变更 v%d -> 拉取并应用（MD5=%s）%n",
                    windowMs / 1000, hit.version(), hit.md5());
        } else {
            System.out.printf("  [%dms] 无变更，进入下一轮长轮询%n", windowMs / 1000);
        }
    }

    public static void main(String[] args) {
        ConfigSnapshot local = new ConfigSnapshot("order.yaml", 41, "timeout=3000", "md5-41");
        List<ConfigSnapshot> server = List.of(local,
                new ConfigSnapshot("order.yaml", 42, "timeout=5000", "md5-42"));
        longPolling(server, local, 30000);
        System.out.println();
        System.out.println("  对照纯轮询：30s 一次全量拉取 vs 长轮询变更秒级感知 + 无变更零数据传输");
    }
}
```

**课后练习**
1. 实现完整配置客户端（长轮询 + MD5 校验 + @RefreshScope 模拟重建 + 本地快照），服务端 3 版变更输出轨迹。
2. 实现配置灰度发布：Beta 按 IP 列表推送，验证未灰度实例仍用旧版本，全量后切换。
3. 思考题：为什么长轮询比纯轮询与纯推送都更适合配置中心？从"变更时效、无变更开销、实现复杂度"三维度对比。

---

### 53-04 Dubbo RPC 原理与调用

**知识点**
- **Dubbo 调用的核心分层**：代理层（50 章 Mapper 代理同款：接口 -> Invoker）-> 集群容错（Failover 重试其他实例/Failfast 不重试/Failsafe 忽略——**按语义选**）-> 负载均衡（Random/LeastActive/一致性哈希——48-08 水位路由思想）-> 协议层（默认 dubbo 协议：Netty 长连接 + 异步）。
- **RPC 与 REST 的取舍**：Dubbo（TCP 长连接、二进制序列化 hessian2、内网微服务间高频调用——性能与治理好）；REST/Feign（HTTP、通用性强、跨语言/对外友好）。**内网高性能用 Dubbo，对外或跨语言用 REST**——常见混合：对外 REST 网关 + 内网 Dubbo。
- **泛化调用与泛化实现**：泛化调用（无接口依赖也能调，Map 传参）用于网关与测试平台；**泛化是网关转发 RPC 的标准解**（44-08 的 RPC 版）。
- **序列化的安全坑**：hessian2 反序列化漏洞历史（36-08 同源）——**Dubbo 3 建议 Triple 协议（gRPC 兼容）**；不要把 Dubbo 端口暴露公网（注册中心里 IP 可见）。
- **参数回调与隐式传参**：RpcContext 传递 trace_id/租户上下文（42 章链路传播的 Dubbo 版）；**隐式传参要显式清理**（线程复用残留是事故源）。
- 易错点：Failover 重试非幂等接口（重复下单）；超时只在 provider 配（consumer 侧不配，被 provider 慢拖死）；序列化对象过大（每次传输几 MB）；注册中心挂了 Dubbo 集群仍可用（本地缓存订阅列表）——不了解这个特性会误判故障。

**示例代码**
```java
import java.lang.reflect.Proxy;
import java.util.Random;

public class DubboRpcDemo {
    interface StockService {
        String deduct(long sku);
    }

    /** Failover：失败换实例重试（幂等语义才可用） */
    static String invokeFailover(String[] instances, java.util.function.Function<String, String> call) {
        Random r = new Random();
        for (int i = 0; i < instances.length; i++) {
            String inst = instances[r.nextInt(instances.length)];
            try {
                return call.apply(inst);
            } catch (RuntimeException e) {
                System.out.printf("  实例 %s 失败：%s -> Failover 换下一实例%n", inst, e.getMessage());
            }
        }
        throw new IllegalStateException("全部实例失败");
    }

    public static void main(String[] args) {
        StockService stock = (StockService) Proxy.newProxyInstance(
                StockService.class.getClassLoader(), new Class<?>[]{StockService.class},
                (proxy, method, args1) -> invokeFailover(
                        new String[]{"inst-1", "inst-2", "inst-3"},
                        inst -> {
                            if (inst.equals("inst-1")) {
                                throw new RuntimeException("连接拒绝");
                            }
                            return "deducted-on-" + inst;
                        }));
        System.out.println("  结果：" + stock.deduct(1001L));
    }
}
```

**课后练习**
1. 实现完整 Dubbo 模拟（代理 + Failover/Failfast 两策略 + LeastActive 负载均衡），对比非幂等接口用 Failover 的重复调用风险。
2. 实现 RpcContext 隐式传参（trace_id 传递 + finally 清理），验证线程复用残留被清理。
3. 思考题：为什么"注册中心挂了 Dubbo 集群仍可用"？从本地订阅缓存与注册中心职责（发现 vs 通信）分析，并说明这如何影响故障判断。

---

### 53-05 Sentinel 限流熔断实战

**知识点**
- **Sentinel 的资源模型**：一切皆资源（resource），规则挂在资源上——**流控规则**（QPS/线程数，直接/关联/链路）、**熔断降级**（慢调用比例/异常比例/异常数，47-03 三态同构）、**热点限流**（按参数值限流——爆款 SKU 单独配额，39 章热点问题的组件级解）、**系统自适应保护**（按 Load/RT 全局兜底）。
- **滑窗统计是 Sentinel 的心脏**（44 章滑动窗口同源）：秒级滑窗统计 QPS/RT/异常数，规则判定基于窗口数据——**窗口粒度决定灵敏与抖动的平衡**。
- **流控效果**：快速失败（默认）/Warm Up（预热爬坡，防冷启动被打爆——缓存未热时）/排队等待（匀速通过，削峰）——**按场景选效果**：秒杀用排队+队列阈值，常规 API 用快速失败。
- **规则持久化**：默认内存（重启丢）——**生产必须推模式持久化到 Nacos**（规则变 config dataId，控制台修改推 Nacos，客户端监听）——41 章配置审计纪律适用。
- **与手写限流的对照**：44 章手写过令牌桶/Semaphore——Sentinel=滑动窗口统计+规则引擎+控制台三合一；**理解手写后，Sentinel 只是"把这三件事产品化"**。
- 易错点：流控规则只配 QPS 不配线程数（线程耗尽型故障未防）；热点限流参数索引错（限了个寂寞）；规则不持久化（重启归零）；链路模式下入口资源名写错（规则不生效）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.List;

public class SentinelGuard {
    /** 滑动窗口统计：最近 N 秒失败率 -> 熔断判定（慢调用比例模式） */
    record Sample(long ts, long rtMs, boolean error) {
    }

    static boolean slowRatioExceeded(List<Sample> window, long slowThreshold,
                                     double ratioThreshold, int minRequest) {
        List<Sample> recent = window.stream()
                .filter(s -> s.rtMs() >= slowThreshold || s.error())
                .toList();
        if (window.size() < minRequest) {
            return false;                       // 样本不足不熔断（统计意义）
        }
        double ratio = (double) recent.size() / window.size();
        return ratio >= ratioThreshold;
    }

    public static void main(String[] args) {
        List<Sample> window = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            window.add(new Sample(i, i < 6 ? 900 : 50, false));   // 6 个慢调用
        }
        boolean tripped = slowRatioExceeded(window, 500, 0.5, 5);
        System.out.printf("  慢调用比例 %.0f%% -> 熔断器 %s%n",
                60.0, tripped ? "OPEN" : "CLOSED");
        System.out.println("  对照：minRequest 未满足时不熔断 —— 防样本少误判");
    }
}
```

**课后练习**
1. 实现完整滑动窗口熔断（慢调用比例 + 异常比例双规则 + 半开恢复 47-03），输出三态迁移日志。
2. 实现热点限流：按 SKU 参数限流（爆款 SKU 100 QPS，其他 1000），验证参数维度生效。
3. 思考题：为什么 Sentinel 规则持久化必须推模式而非拉模式？从规则生效时效与控制台体验分析。

---

### 53-06 Seata 分布式事务

**知识点**
- **Seata 的三种模式**：**AT**（自动补偿：一阶段本地提交+undo_log，二阶段异步删除或反向补偿——**业务无侵入但全局锁竞争**）、**TCC**（Try/Confirm/Cancel 手写三接口——高性能但侵入，51-09 的资金冻结场景）、**Saga**（长流程状态机编排）。**选型**：核心资金链路 TCC，一般业务 AT，超长流程 Saga（39-09 光谱的组件级印证）。
- **AT 模式的全局锁**：TC（事务协调器）维护全局行锁——**同一行在全局事务提交前不能被其他全局事务修改**；这是 AT 隔离性的来源，也是吞吐瓶颈：**热点行（爆款库存）的 AT 会排队**——39 章热点方案在 AT 模式下依然必要。
- **undo_log 的机制**：一阶段解析前后镜像存 undo_log（同本地事务），二阶段提交则异步删，回滚则按镜像反向补偿——**回滚失败（脏写）需要人工处理**（undo_log 校验不一致时告警）。
- **Seata 的坑**：全局事务超时（默认 60s，长业务要调）；undo_log 表每个业务库都要建；@GlobalTransactional 与本地事务的边界（**方法内不要吞异常**，否则 TC 认为成功）；性能敏感接口的 AT 全局锁等待。
- 易错点：所有跨服务事务都上 AT（全局锁拖垮核心链路）；undo_log 缺失（回滚时报表不存在）；TC 单点（生产要集群）；业务侧自行 commit 让 Seata 失明。

**示例代码**
```java
import java.util.ArrayList;
import java.util.List;

public class SeataAtDemo {
    /** AT 两阶段：undo_log 反向补偿 */
    record UndoLog(String table, String pk, String before, String after) {
        String rollbackSql() {
            return "UPDATE " + table + " SET ... WHERE pk=" + pk + " （恢复 before 镜像: " + before + "）";
        }
    }

    static void twoPhase(boolean globalCommit) {
        List<UndoLog> logs = List.of(
                new UndoLog("stock", "sku=1001", "qty=50", "qty=49"),
                new UndoLog("account", "u=1001", "balance=10000", "balance=9400"));
        System.out.println("  一阶段：各本地事务提交 + undo_log 落库（全局锁已持有）");
        if (globalCommit) {
            System.out.println("  二阶段提交：异步删除 undo_log（业务数据已是最新）");
        } else {
            System.out.println("  二阶段回滚：按镜像反向补偿");
            for (UndoLog log : logs) {
                System.out.println("    " + log.rollbackSql());
            }
        }
        System.out.println();
    }

    public static void main(String[] args) {
        twoPhase(true);
        twoPhase(false);
        System.out.println("  全局锁提示：stock sku=1001 在全局事务期间对其他全局事务不可写");
        System.out.println("  热点行场景：AT 排队 -> 改用 TCC 预扣或 Redis（39 章方案）");
    }
}
```

**课后练习**
1. 实现 AT 两阶段模拟器（undo_log 生成/提交删除/回滚补偿 + 脏写检测），输出三场景轨迹。
2. 对比 AT 与 TCC 在"爆款扣减"场景的吞吐（模拟全局锁排队 vs 预扣），量化差异。
3. 思考题：为什么 AT 的全局锁让热点行吞吐骤降？从 TC 锁的粒度与全局事务持有时长分析，并给出三种缓解方案。

---

### 53-07 Gateway 与鉴权链路

**知识点**
- **Spring Cloud Gateway（阿里栈常用 SCG）的核心模型**：Route（路由：谓词匹配 -> 目标 URI）+ Predicate（断言：Path/Header/Method/Time）+ Filter（前置/后置修改请求响应）。**44-08 AI 网关与 47-03 流量治理的路由模型在此统一**——网关的本质就是"谓词匹配 + 过滤器链"。
- **网关鉴权链路**：登录态校验（JWT 验签，26-02）-> 权限粗校验（路由级白名单）-> 上下文注入（userId/租户写入 header 传下游，44-09 的"服务间身份"）-> 限流（按用户维度 44-08）——**网关是零信任的第一道闸**（47-04：内网不等于可信）。
- **网关的高频坑**：Filter 顺序错误（鉴权在限流后，被限流的请求也验签浪费）；响应体篡改需读 body（内存缓冲，大响应慎改）；WebSocket 转发配置遗漏；**全局过滤器抛异常未转统一响应**（用户收到 500 而非业务错误）。
- **与 Sentinel 的联动**：网关维度的流控（按 route/按用户）——Sentinel gateway adapter 支持参数维度（同 53-05 热点限流）。
- 易错点：网关做业务逻辑（网关只做横切：鉴权/限流/路由/观测）；header 注入未清理同名外部头（伪造 userId 头穿透）；鉴权信息只在网关验、下游不校验（内网裸奔，47-04）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.List;

public class GatewayChainDemo {
    record FilterDef(int order, String name, boolean blocking) {
    }

    record Result(int status, String body) {
    }

    static Result run(List<FilterDef> filters, String jwt, String path) {
        for (FilterDef f : filters) {
            switch (f.name()) {
                case "rate-limit" -> System.out.println("  [限流] 通过");
                case "auth" -> {
                    if (jwt == null || jwt.isBlank()) {
                        return new Result(401, "{\"code\":\"UNAUTHORIZED\"}");
                    }
                    System.out.println("  [鉴权] 通过，注入 X-User-Id");
                }
                case "route" -> System.out.println("  [路由] -> " + path);
                default -> {
                }
            }
        }
        return new Result(200, "{\"ok\":true}");
    }

    public static void main(String[] args) {
        List<FilterDef> filters = List.of(new FilterDef(1, "rate-limit", false),
                new FilterDef(2, "auth", true), new FilterDef(3, "route", false));
        Result r1 = run(filters, "valid-jwt", "/order/create");
        System.out.println("  合法请求 -> " + r1.status());
        Result r2 = run(filters, "", "/order/create");
        System.out.println("  无凭证 -> " + r2.status() + "（鉴权短路，不进路由）");
    }
}
```

**课后练习**
1. 实现完整网关链（限流/鉴权/路由三过滤器 + 统一异常响应），三个场景（正常/无凭证/限流触发）输出日志。
2. 实现"外部同名头清理"：请求自带 X-User-Id 头时被网关覆盖——验证伪造穿透被阻断。
3. 思考题：为什么网关只做横切逻辑？列出三个"业务逻辑下沉到网关"引发的问题。

---

### 53-08 链路追踪 SkyWalking

**知识点**
- **SkyWalking 的架构**：Agent（字节码增强，零侵入埋点——42-07 的自动探针）-> OAP 收集聚合（gRPC 上报）-> 存储（ES/达梦）-> UI。**与 OpenTelemetry 的关系**：OTel 是 API/协议标准，SkyWalking 是带 UI 的完整 APM——42-08 的"概念 vs 组件"论断在此印证。
- **字节码增强的原理**：Agent 启动时改写字节码（Transformer），在插件匹配的类方法前后插入埋点——**零侵入的代价是版本兼容**（框架升级后插件失效），42 章的"自动探针为主 + 关键手工补充"在此有工程细节。
- **SkyWalking 的特色功能**：拓扑图自动生成、告警规则（OAP 内置 Webhook——42-06 告警对接）、JVM 指标内置采集、** Allegro：无侵入性能剖析**（48-09 火焰图的平台化）。
- **采样与存储**：Agent 采样率配置（42-08 头部采样）；存储 TTL 管理（ES 索引按天，46-03 生命周期思想）；OAP 集群水平扩展。
- 易错点：Agent 版本与框架不兼容（埋点静默失效——**上线后必须验证 trace 完整性**）；OAP 单点；ES 存储无 TTL（磁盘爆）；采样率全量（42-08 成本教训）。
- **与 42 章的自建观测对照**：42 章手写了 Span 树/采样/告警——SkyWalking 是这些概念的产品化集合。

**示例代码**
```java
import java.util.LinkedHashMap;
import java.util.Map;

public class AgentInstrumentDemo {
    static void simulate(String frameworkVersion, boolean agentPluginSupports) {
        Map<String, String> trace = new LinkedHashMap<>();
        if (agentPluginSupports) {
            trace.put("span-1", "http GET /order (auto)");
            trace.put("span-2", "mysql SELECT (auto)");
        } else {
            trace.put("span-1", "http GET /order (auto)");
            trace.put("gap", "mysql 调用未被增强 -> 链路断裂（42-07 断裂点）");
        }
        System.out.printf("  框架版本 %s / 插件支持=%b：%s%n",
                frameworkVersion, agentPluginSupports, trace);
        System.out.println();
    }

    public static void main(String[] args) {
        simulate("3.2.x", true);
        simulate("3.5.x(新)", false);
        System.out.println("  上线验证清单：任抽 3 条请求核对 span 完整性 —— 静默失效的防线");
    }
}
```

**课后练习**
1. 模拟 Agent 字节码增强：为给定类方法生成"前后插桩"的伪代码，验证三框架版本的插件匹配。
2. 实现"trace 完整性巡检"：抽样请求核对首尾 span 与中间断裂点，输出缺失报告（42-07 断裂点清单的自动化）。
3. 思考题：为什么 SkyWalking 的"字节码增强"比"SDK 显式埋点"更适合大规模推广？从侵入成本与版本兼容代价的权衡分析。

---

### 53-09 生态整合：一个订单服务的阿里栈落地

**知识点**
- **把全部组件串到一个服务上**：订单服务接入——Nacos（注册+配置）、Sentinel（下单资源限流+熔断）、Dubbo（调库存）/RocketMQ（发事件）、Seata AT（订单-账务跨库）、SkyWalking（链路）——**53-01 选位图的落地版**。
- **接入顺序与风险**：先注册发现（基础设施）-> 再配置中心（参数化）-> 再 RPC（调用链改造）-> 再 Sentinel（防护）-> 再 Seata（事务改造，侵入最大放后面）-> 最后链路（观测补全）。**每步可独立回退**（49-10 演进纪律）。
- **配置清单示例**：Nacos 两个 dataId（application.yaml + sentinel-rules）；Dubbo 超时与重试（53-04）；Seata 模式与 undo_log；SkyWalking agent 参数（采样率/服务名）——**配置总览表是接入验收物**。
- **集成测试清单**：注册发现 OK、配置热更 OK、限流触发 OK、跨服务事务回滚 OK、trace 完整 OK——**五项全过才算接入完成**（53-01 预览的落地）。
- 易错点：一次性全接入（故障域叠加，无法归因）；Seata 放最先（undo_log 建表/版本兼容阻塞主线）；接入后无配置总览（运维交接灾难）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.List;

public class AliStackIntegration {
    record Step(int order, String action, String risk, String rollback) {
    }

    static void roadmap() {
        System.out.println("【订单服务接入阿里栈：六步路线】");
        List<Step> steps = List.of(
                new Step(1, "Nacos 注册发现", "低：本地缓存兜底", "退回直连 IP 配置"),
                new Step(2, "Nacos 配置中心", "低：参数化而已", "退回本地 yaml"),
                new Step(3, "Dubbo 调库存", "中：调用链改造", "退回 Feign/直连"),
                new Step(4, "Sentinel 防护", "低：规则可空", "规则清空即无防护原样"),
                new Step(5, "Seata AT（订单-账务）", "高：事务语义变化", "退回本地消息表（39-09）"),
                new Step(6, "SkyWalking 链路", "低：观测增强", "摘 agent 即回退"));
        for (Step s : steps) {
            System.out.printf("  步骤%d %-20s 风险=%-18s 回退=%s%n",
                    s.order(), s.action(), s.risk(), s.rollback());
        }
        System.out.println("  顺序原则：低风险先上，事务改造最后（49-10 演进纪律）");
    }

    public static void main(String[] args) {
        roadmap();
        System.out.println();
        List<String> gates = List.of(
                "[ ] 注册发现：3 实例互相可见，摘 1 个自动剔除",
                "[ ] 配置：热更生效 + 灰度验证",
                "[ ] Sentinel：限流触发正确 + 规则持久化（重启不丢）",
                "[ ] Seata：跨服务回滚演练 + undo_log 就位",
                "[ ] SkyWalking：trace 完整性巡检通过（53-08）");
        for (String g : gates) {
            System.out.println("  " + g);
        }
    }
}
```

**课后练习**
1. 为你的服务制定六步接入路线（含每步风险与回退），输出接入验收清单。
2. 演练"Seata 回滚"：订单-账务跨库回滚演示（或模拟），验证 undo_log 反向补偿。
3. 思考题：为什么 Seata 接入放在最后？从侵入度、全局锁风险与"其他步骤依赖稳定基础"三个角度论证顺序。

---
