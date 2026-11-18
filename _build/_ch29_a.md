### 29-01 从单体到微服务：拆与不拆

**知识点**
- **单体应用**：一个进程、一个数据库、一次部署。优点是简单——本地方法调用、事务天然一致、排查一条日志链；缺点是耦合——改一个模块要全量回归、扩一个热点功能要整体扩容、一个死循环拖垮全站、技术栈被锁死。
- **微服务**：按业务能力拆成多个独立部署的服务，各自有进程、数据库、发布节奏。优点是可以独立扩缩容、故障隔离、团队并行；代价是**分布式复杂度**——远程调用会超时、网络会分区、事务不再原子、链路变长排查变难。
- 康威定律：系统架构会向组织沟通结构收敛。团队没拆开就强行拆服务，只会得到"分布式单体"——服务拆了但每次发版还得一起发。
- 判断标准不是"大不大"而是"变化频率是否不同、扩缩容需求是否不同、故障是否应该隔离"。日活几千的内部系统，单体 + 模块化往往是最优解。
- 演进路径：单体（模块化分包）→ 抽出第一个真正独立的服务（通常是用户/认证或消息通知）→ 按瓶颈逐步拆 → 配套注册中心、网关、配置中心、链路追踪。
- 易错：一上来就按技术分层拆（controller 服务、service 服务、dao 服务），这会让一次请求跨 3 个服务，延迟翻倍还没有任何隔离收益。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MonolithToMicro {
    record Module(String name, int changePerMonth, int qps, boolean critical) { }

    public static void main(String[] args) {
        List<Module> modules = new ArrayList<>();
        modules.add(new Module("用户与认证", 2, 300, true));
        modules.add(new Module("商品", 6, 2000, true));
        modules.add(new Module("订单", 8, 800, true));
        modules.add(new Module("积分", 1, 100, false));
        modules.add(new Module("消息通知", 1, 150, false));
        modules.add(new Module("报表导出", 3, 5, false));

        System.out.println("=== 单体：所有模块一个进程 ===");
        int totalQps = 0;
        for (Module m : modules) {
            totalQps += m.qps();
        }
        System.out.println("  总 QPS " + totalQps + "，扩缩容只能整体进行");
        System.out.println("  报表导出（5 QPS）跑满 CPU 时，商品（2000 QPS）一起变慢");

        System.out.println();
        System.out.println("=== 拆分判断：变化频率 / 流量特征 / 故障隔离 ===");
        System.out.printf("  %-12s %-10s %-10s %-10s %s%n", "模块", "月变更", "QPS", "核心链路", "建议");
        for (Module m : modules) {
            String advice;
            if (m.qps() >= 1500 || m.changePerMonth() >= 6) {
                advice = "优先拆（流量或变更频率独立）";
            } else if (!m.critical() && m.qps() < 200) {
                advice = "可后拆（旁路，故障不该影响主链路）";
            } else {
                advice = "先留在单体，模块化分包即可";
            }
            System.out.printf("  %-12s %-10d %-10d %-10s %s%n",
                    m.name(), m.changePerMonth(), m.qps(), m.critical() ? "是" : "否", advice);
        }

        System.out.println();
        System.out.println("=== 拆分后的调用代价 ===");
        int localCallUs = 1;
        int remoteCallMs = 5;
        System.out.println("  本地方法调用约 " + localCallUs + "us，跨服务 HTTP 调用约 " + remoteCallMs + "ms");
        System.out.println("  一次请求跨 3 个服务 = " + (3 * remoteCallMs) + "ms 纯网络开销（还没算业务）");
        System.out.println("  -> 拆得越细，越要用聚合/批量调用把往返次数压下来");

        System.out.println();
        System.out.println("=== 反面案例：按技术分层拆 ===");
        Map<String, String> bad = new LinkedHashMap<>();
        bad.put("controller-service", "只做参数校验和转发");
        bad.put("service-service", "只做业务编排");
        bad.put("dao-service", "只做数据访问");
        for (Map.Entry<String, String> e : bad.entrySet()) {
            System.out.println("  " + e.getKey() + "：" + e.getValue());
        }
        System.out.println("  一次请求跨 3 跳，任何一层扩容都要连带扩容，事务无法保证，纯亏");
    }
}
```

**课后练习**
1. 给你做过的项目列一张模块表，标注月变更次数与 QPS，用示例里的规则判断先拆哪个。
2. 说明"分布式单体"的典型特征，以及它比单体更糟的原因。
3. 为什么按技术分层拆分服务是反模式？举一个具体的坏处。

### 29-02 服务拆分原则与边界

**知识点**
- **按业务能力拆（DDD 限界上下文）**：一个服务对应一个业务领域，拥有自己的数据和规则。订单服务管订单，商品服务管商品，不要出现"订单服务直接改商品表"。
- **数据库私有**：每个服务独占自己的库/表，跨服务只能通过 API。共享数据库是微服务最大的反模式——表结构一改，所有服务一起挂。
- 拆分粒度：一个服务应该能被**一个小团队（5~8 人）**完整负责，包含从接口到存储的全部。太细会导致调用链爆炸，太粗失去隔离收益。
- 聚合边界看"一致性要求"：必须强一致的放在同一个服务同一个库（订单 + 订单明细）；可以最终一致的拆开（订单 + 积分）。
- 服务接口要**面向业务**而不是面向表：`POST /orders` 而不是 `insertOrderRow`。接口一旦发布就是契约，改动要考虑兼容。
- 易错：把"代码分层"当成"服务拆分"；两个服务共用一个数据库连接池；拆分后仍然跨库 join。

**示例代码**
```java
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ServiceSplit {
    /** 一个服务：名字 + 拥有的表 + 对外能力 */
    record Service(String name, List<String> tables, List<String> apis) { }

    public static void main(String[] args) {
        List<Service> services = new ArrayList<>();
        services.add(new Service("user-service",
                List.of("t_user", "t_user_address"),
                List.of("GET /users/{id}", "POST /users", "GET /users/{id}/addresses")));
        services.add(new Service("item-service",
                List.of("t_item", "t_item_sku", "t_category"),
                List.of("GET /items/{id}", "GET /items?page=", "POST /items")));
        services.add(new Service("order-service",
                List.of("t_order", "t_order_item", "t_order_status_log"),
                List.of("POST /orders", "GET /orders/{id}", "POST /orders/{id}/cancel")));
        services.add(new Service("points-service",
                List.of("t_points_account", "t_points_log"),
                List.of("POST /points/earn", "GET /points/{userId}")));

        System.out.println("=== 服务边界：每个服务独占自己的表 ===");
        Map<String, String> owner = new HashMap<>();
        boolean conflict = false;
        for (Service s : services) {
            System.out.println("  " + s.name());
            for (String t : s.tables()) {
                if (owner.containsKey(t)) {
                    System.out.println("    ! 表 " + t + " 同时属于 " + owner.get(t) + " 和 " + s.name() + "（共享数据库，反模式）");
                    conflict = true;
                }
                owner.put(t, s.name());
                System.out.println("    " + t);
            }
        }
        System.out.println("  表归属冲突=" + conflict);

        System.out.println();
        System.out.println("=== 对外能力（面向业务，不面向表）===");
        for (Service s : services) {
            for (String api : s.apis()) {
                System.out.println("  " + s.name() + "  " + api);
            }
        }

        System.out.println();
        System.out.println("=== 跨服务调用：只能走 API，不能跨库 join ===");
        Map<String, String> flow = new LinkedHashMap<>();
        flow.put("1. order-service 收到下单请求", "本地事务：写 t_order + t_order_item（强一致，同库）");
        flow.put("2. 调 item-service 扣库存", "HTTP POST /items/{id}/deduct，失败则整单失败");
        flow.put("3. 发 order.created 事件", "points-service 异步加积分（最终一致，可补偿）");
        for (Map.Entry<String, String> e : flow.entrySet()) {
            System.out.println("  " + e.getKey() + " => " + e.getValue());
        }

        System.out.println();
        System.out.println("=== 一致性边界判断 ===");
        System.out.println("  必须强一致（同服务同库）：订单主表 + 订单明细 + 状态流水");
        System.out.println("  可以最终一致（跨服务异步）：订单 -> 积分、订单 -> 物流单、订单 -> 搜索索引");
        System.out.println("  判据：业务能否容忍「几秒内不一致」，能容忍就不要用分布式事务硬扛");
    }
}
```

**课后练习**
1. 为"商品 + 库存 + 价格"设计服务边界：库存应该和商品同服务还是独立？说明理由。
2. 找出共享数据库会引发的至少三个具体故障场景。
3. 为什么"订单明细"必须和"订单主表"在同一个库？如果拆开会遇到什么问题？

### 29-03 注册中心与服务发现

**知识点**
- 微服务实例的 IP 和端口是**动态的**（容器重建、弹性扩容都会变），硬编码地址必然失效。注册中心解决"服务 B 现在有哪些可用实例"。
- 流程：服务启动 → 向注册中心**注册**（服务名 + IP + 端口 + 元数据）→ 定期**心跳**续约 → 消费者**拉取/订阅**实例列表 → 本地缓存 + 变更推送。
- 健康检查两种模式：**客户端心跳**（服务自己上报，简单但可能"假活"）与**服务端探测**（注册中心主动探活，更准但有延迟）。
- 主流实现：Nacos（注册 + 配置二合一，国内主流）、Eureka（AP，自我保护模式）、Consul（CP，强一致）、ZooKeeper（CP，临时节点）。
- CAP 取舍：注册中心通常选 **AP**——网络分区时宁可返回稍旧的实例列表，也不能让整个调用链不可用。短暂的不一致由客户端重试和熔断兜底。
- 易错：消费者每次调用都查一次注册中心（应本地缓存 + 订阅推送）；实例下线后本地缓存不刷新（要靠推送或定时全量拉取）；把注册中心当配置中心用。

**示例代码**
```java
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RegistryDiscovery {
    /** 一个服务实例 */
    static class Instance {
        final String host;
        final int port;
        long lastHeartbeat;
        boolean healthy = true;

        Instance(String host, int port, long now) {
            this.host = host;
            this.port = port;
            this.lastHeartbeat = now;
        }

        String addr() {
            return host + ":" + port;
        }
    }

    /** 极简注册中心 */
    static class Registry {
        final Map<String, List<Instance>> services = new HashMap<>();
        static final long TTL = 30_000;

        void register(String service, Instance ins, long now) {
            services.computeIfAbsent(service, k -> new ArrayList<>()).add(ins);
            System.out.println("  注册 " + service + " -> " + ins.addr());
        }

        void heartbeat(String service, String addr, long now) {
            for (Instance i : services.getOrDefault(service, List.of())) {
                if (i.addr().equals(addr)) {
                    i.lastHeartbeat = now;
                }
            }
        }

        /** 剔除超过 TTL 未续约的实例 */
        int evict(long now) {
            int removed = 0;
            for (Map.Entry<String, List<Instance>> e : services.entrySet()) {
                List<Instance> list = e.getValue();
                for (int i = list.size() - 1; i >= 0; i--) {
                    if (now - list.get(i).lastHeartbeat > TTL) {
                        System.out.println("  剔除失联实例 " + e.getKey() + " -> " + list.get(i).addr());
                        list.remove(i);
                        removed++;
                    }
                }
            }
            return removed;
        }

        List<Instance> discover(String service) {
            List<Instance> list = new ArrayList<>();
            for (Instance i : services.getOrDefault(service, List.of())) {
                if (i.healthy) {
                    list.add(i);
                }
            }
            return list;
        }
    }

    public static void main(String[] args) {
        Registry registry = new Registry();
        long now = 0;
        System.out.println("=== 服务启动时注册 ===");
        registry.register("item-service", new Instance("10.0.0.11", 8081, now), now);
        registry.register("item-service", new Instance("10.0.0.12", 8081, now), now);
        registry.register("item-service", new Instance("10.0.0.13", 8081, now), now);

        System.out.println();
        System.out.println("=== 消费者发现实例（本地缓存，不是每次调用都查）===");
        List<Instance> cached = registry.discover("item-service");
        System.out.println("  本地缓存实例：" + cached.stream().map(Instance::addr).toList());

        System.out.println();
        System.out.println("=== 心跳续约：10.0.0.11 和 12 正常，13 挂掉 ===");
        now += 20_000;
        registry.heartbeat("item-service", "10.0.0.11:8081", now);
        registry.heartbeat("item-service", "10.0.0.12:8081", now);
        registry.evict(now);
        System.out.println("  当前可用：" + registry.discover("item-service").stream().map(Instance::addr).toList());

        System.out.println();
        System.out.println("=== TTL 到期后剔除失联实例 ===");
        now += 20_000;
        registry.heartbeat("item-service", "10.0.0.11:8081", now);
        registry.heartbeat("item-service", "10.0.0.12:8081", now);
        int removed = registry.evict(now);
        System.out.println("  本轮剔除 " + removed + " 个，可用实例："
                + registry.discover("item-service").stream().map(Instance::addr).toList());

        System.out.println();
        System.out.println("=== 扩容：新实例加入，推送变更 ===");
        registry.register("item-service", new Instance("10.0.0.14", 8081, now), now);
        System.out.println("  消费者收到推送，缓存刷新为："
                + registry.discover("item-service").stream().map(Instance::addr).toList());
        System.out.println("  CAP 取舍：注册中心选 AP，分区时宁可给旧列表也不能整体不可用");
    }
}
```

**课后练习**
1. 给 `Registry` 加一个"实例主动下线（优雅停机）"的方法，对比与心跳超时的区别。
2. 说明为什么消费者要缓存实例列表并订阅变更，而不是每次调用都查注册中心。
3. 对比 Nacos 与 Eureka 在 CAP、健康检查、配置管理上的差异。

### 29-04 远程调用：RPC 与 HTTP

**知识点**
- **RPC（Dubbo/gRPC）**：基于接口的代理调用，像调本地方法；序列化用 Protobuf/Hessian，二进制、体积小、性能高；强契约（接口 + DTO）。
- **HTTP（REST/OpenFeign）**：文本协议（JSON），跨语言、可读、易调试、网关友好；性能略低，但生态和可观测性更好。
- 选择：内部高频调用、追求性能用 RPC；对外 API、跨团队、跨语言用 HTTP。很多公司两者并存。
- 一次远程调用的组成：**建连/复用**（连接池）、**序列化**、**网络传输**、**反序列化**。耗时里最容易被忽略的是建连——没有连接池时每次 TCP 三次握手 + TLS 握手能占掉大半。
- 超时必须显式设置，且要小于上游的超时（超时预算逐层递减），否则上游先断开、下游还在白干。
- 重试只能用于**幂等**接口，且要限制次数 + 退避，否则雪崩时重试会放大流量 3 倍。
- 易错：不设超时（默认无限等，线程池耗尽）；把非幂等的 `POST /orders` 加入自动重试，用户被扣两次钱；传输大对象（整个聚合根）而不是只传需要的字段。

**示例代码**
```java
import java.util.ArrayList;
import java.util.List;

public class RemoteCall {
    /** 模拟一次调用的耗时分解 */
    static class CallProfile {
        long connectUs;
        long serializeUs;
        long networkUs;
        long deserializeUs;

        long totalUs() {
            return connectUs + serializeUs + networkUs + deserializeUs;
        }

        @Override
        public String toString() {
            return String.format("建连=%dus 序列化=%dus 网络=%dus 反序列化=%dus 合计=%dus",
                    connectUs, serializeUs, networkUs, deserializeUs, totalUs());
        }
    }

    static CallProfile httpCall(boolean reuseConnection, int payloadKb) {
        CallProfile p = new CallProfile();
        p.connectUs = reuseConnection ? 5 : 2000;          // 无连接池要三次握手
        p.serializeUs = payloadKb * 12L;                    // JSON 文本，体积大
        p.networkUs = payloadKb * 8L;
        p.deserializeUs = payloadKb * 10L;
        return p;
    }

    static CallProfile rpcCall(boolean reuseConnection, int payloadKb) {
        CallProfile p = new CallProfile();
        p.connectUs = reuseConnection ? 3 : 1500;
        p.serializeUs = payloadKb * 2L;                     // 二进制，体积小
        p.networkUs = payloadKb * 4L;
        p.deserializeUs = payloadKb * 2L;
        return p;
    }

    public static void main(String[] args) {
        System.out.println("=== 同一次调用：HTTP vs RPC ===");
        System.out.println("  HTTP " + httpCall(true, 20));
        System.out.println("  RPC  " + rpcCall(true, 20));

        System.out.println();
        System.out.println("=== 连接池的影响（最容易忽略的一项）===");
        System.out.println("  无连接池 HTTP " + httpCall(false, 20));
        System.out.println("  有连接池 HTTP " + httpCall(true, 20));
        long saved = httpCall(false, 20).totalUs() - httpCall(true, 20).totalUs();
        System.out.println("  单次省 " + saved + "us；1000 QPS 下每秒省 " + saved * 1000 / 1000 + "ms 的线程占用");

        System.out.println();
        System.out.println("=== 大对象传输的代价 ===");
        int[] sizes = {5, 20, 100, 500};
        for (int kb : sizes) {
            CallProfile h = httpCall(true, kb);
            System.out.println("  payload " + kb + "KB -> HTTP " + h.totalUs() / 1000 + "ms，RPC "
                    + rpcCall(true, kb).totalUs() / 1000 + "ms");
        }
        System.out.println("  建议：只传需要的字段，列表接口分页，别把整个聚合根丢过去");

        System.out.println();
        System.out.println("=== 超时预算：逐层递减 ===");
        List<int[]> budget = new ArrayList<>();
        budget.add(new int[]{3000, 800, 300});
        System.out.println("  网关 3000ms > 订单服务 800ms > 商品服务 300ms");
        System.out.println("  上游必须比下游长，否则上游已断开、下游还在白干");
        for (int[] b : budget) {
            System.out.println("  层超时 " + b[0] + " / " + b[1] + " / " + b[2] + "ms");
            break;
        }

        System.out.println();
        System.out.println("=== 重试只能用于幂等接口 ===");
        System.out.println("  GET  /items/1       可重试");
        System.out.println("  PUT  /items/1       可重试（幂等）");
        System.out.println("  POST /orders        不可自动重试（会重复下单），需幂等键");
        System.out.println("  重试放大：3 次重试 + 上游超时 = 下游流量变成 4 倍");
    }
}
```

**课后练习**
1. 用示例的数据算出"payload 100KB、500 QPS、无连接池"时每秒浪费多少建连时间。
2. 设计一套超时预算：网关 2s、订单服务、商品服务、库存服务分别设多少？说明依据。
3. 为什么 `POST /orders` 不能自动重试？如果业务方要求"必须重试"，你会怎么改造。

### 29-05 负载均衡与重试策略

**知识点**
- 客户端负载均衡（Spring Cloud LoadBalancer、Dubbo）在调用方本地选实例，少一跳、更快；服务端负载均衡（Nginx、LVS）集中转发，运维简单。
- 常见算法：**轮询**（均匀，适合实例同质）、**加权轮询**（按机器配置分配）、**最少连接/最小活跃**（适合长耗时请求）、**一致性哈希**（同一 key 落同一实例，利于缓存命中）、**随机**（简单，大样本下接近均匀）。
- 健康实例才参与分配：注册中心标记下线的实例要立刻从本地列表中摘除，否则会持续打到挂掉的节点。
- 重试要区分错误类型：**连接失败**可以重试另一台实例；**业务异常（4xx）**不该重试；**超时**要看接口是否幂等。
- 重试策略：固定间隔、指数退避、带抖动的退避（避免所有客户端同时重试造成"重试风暴"）。
- 熔断与重试配合：重试次数用尽后触发熔断，直接把后续请求快速失败，给下游恢复时间。
- 易错：重试仍打同一台实例（没有换节点）；重试次数配 5 次且每层都重试，实际放大 5^3 倍。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class LoadBalanceRetry {
    static final List<String> HEALTHY = new ArrayList<>(List.of("10.0.0.11", "10.0.0.12", "10.0.0.13"));
    static final List<String> ALL = new ArrayList<>(List.of("10.0.0.11", "10.0.0.12", "10.0.0.13", "10.0.0.14"));

    static String roundRobin(int[] cursor) {
        String pick = HEALTHY.get(cursor[0] % HEALTHY.size());
        cursor[0]++;
        return pick;
    }

    static String leastActive(int[] active) {
        int best = 0;
        for (int i = 1; i < active.length; i++) {
            if (active[i] < active[best]) {
                best = i;
            }
        }
        return HEALTHY.get(best);
    }

    static String consistentHash(String key) {
        int idx = Math.floorMod(key.hashCode(), HEALTHY.size());
        return HEALTHY.get(idx);
    }

    public static void main(String[] args) {
        System.out.println("=== 轮询 ===");
        int[] cursor = {0};
        Map<String, Integer> stat = new LinkedHashMap<>();
        for (int i = 0; i < 9; i++) {
            String pick = roundRobin(cursor);
            stat.merge(pick, 1, Integer::sum);
        }
        System.out.println("  9 次分配：" + stat);

        System.out.println();
        System.out.println("=== 最少活跃连接 ===");
        int[] active = {7, 2, 5};
        for (int i = 0; i < 3; i++) {
            String pick = leastActive(active);
            int idx = HEALTHY.indexOf(pick);
            active[idx]++;
            System.out.println("  选 " + pick + "（当前活跃 " + (active[idx] - 1) + " -> " + active[idx] + "）");
        }

        System.out.println();
        System.out.println("=== 一致性哈希：同一用户固定落同一实例（利于本地缓存）===");
        for (String user : List.of("u1001", "u1002", "u1001", "u1003", "u1002")) {
            System.out.println("  " + user + " -> " + consistentHash(user));
        }

        System.out.println();
        System.out.println("=== 摘除不健康实例 ===");
        System.out.println("  注册中心标记 10.0.0.13 下线后，本地列表变为 " + HEALTHY);
        System.out.println("  仍在 ALL 里的 10.0.0.14 未注册，不参与分配：" + ALL);

        System.out.println();
        System.out.println("=== 重试：换节点 + 退避 + 抖动 ===");
        Random random = new Random(42);
        String[] nodes = {"10.0.0.11", "10.0.0.12", "10.0.0.13"};
        for (int attempt = 1; attempt <= 3; attempt++) {
            String node = nodes[attempt - 1];
            boolean ok = attempt == 3;
            long backoff = (long) (100 * Math.pow(2, attempt - 1) * (0.5 + random.nextDouble()));
            System.out.println("  第 " + attempt + " 次 -> " + node + "，结果=" + (ok ? "成功" : "超时")
                    + (ok ? "" : "，退避 " + backoff + "ms（含抖动）"));
            if (ok) {
                break;
            }
        }

        System.out.println();
        System.out.println("=== 重试放大计算 ===");
        int layers = 3;
        int retries = 3;
        long amplified = (long) Math.pow(retries + 1, layers);
        System.out.println("  " + layers + " 层调用每层重试 " + retries + " 次 => 最坏放大 " + amplified + " 倍");
        System.out.println("  对策：只在最外层重试，内层超时后快速失败");
    }
}
```

**课后练习**
1. 实现加权轮询：实例 A/B/C 权重 5/3/2，输出 10 次分配结果并验证比例。
2. 说明"每层都重试 3 次、共 3 层"为什么会放大到 64 倍，给出治理方案。
3. 一致性哈希在实例扩容时会迁移多少 key？普通取模又会迁移多少？

### 29-06 网关：路由、鉴权、限流

**知识点**
- 网关是所有外部流量的**唯一入口**，承担：路由转发、统一鉴权、限流熔断、协议转换、日志埋点、跨域处理。它的存在让内部服务不必各自实现这些横切能力。
- 路由规则：按路径前缀（`/api/order/**` → order-service）、按域名、按 Header。路由要支持**灰度**（按用户 ID 或 Header 分流到新版本）。
- 鉴权放在网关：统一校验 token，把解析出的用户 ID 放进请求头传给下游（下游信任内网头，不再重复解析）。注意内部头必须由网关**覆盖**而非透传，否则外部可以伪造。
- 限流三个维度：**QPS/并发数**（令牌桶、漏桶、滑动窗口）、**用户级**（防单用户刷接口）、**接口级**（保护慢接口）。限流算法选令牌桶（允许突发）或漏桶（严格匀速）。
- 网关自身要高可用：多实例 + 前面挂 SLB，网关不能成为单点。
- 易错：网关做了鉴权但下游还信任外部传入的 `X-User-Id`（越权）；限流只做全局限流，一个用户刷满把所有人挡住；网关里写业务逻辑，越写越厚变成"网关巨石"。

**示例代码**
```java
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ApiGateway {
    /** 路由规则 */
    record Route(String prefix, String target, boolean needAuth) { }

    static final List<Route> ROUTES = new ArrayList<>(List.of(
            new Route("/api/users", "user-service", false),
            new Route("/api/items", "item-service", false),
            new Route("/api/orders", "order-service", true),
            new Route("/api/points", "points-service", true)
    ));

    /** 令牌桶：容量 capacity，每秒补充 rate 个 */
    static class TokenBucket {
        final int capacity;
        final int rate;
        double tokens;
        long lastRefillMs;

        TokenBucket(int capacity, int rate, long now) {
            this.capacity = capacity;
            this.rate = rate;
            this.tokens = capacity;
            this.lastRefillMs = now;
        }

        boolean tryAcquire(long now) {
            double add = (now - lastRefillMs) / 1000.0 * rate;
            tokens = Math.min(capacity, tokens + add);
            lastRefillMs = now;
            if (tokens >= 1) {
                tokens -= 1;
                return true;
            }
            return false;
        }
    }

    static String route(String path) {
        for (Route r : ROUTES) {
            if (path.startsWith(r.prefix())) {
                return r.target();
            }
        }
        return null;
    }

    static boolean needAuth(String path) {
        for (Route r : ROUTES) {
            if (path.startsWith(r.prefix())) {
                return r.needAuth();
            }
        }
        return true;
    }

    public static void main(String[] args) {
        System.out.println("=== 路由转发 ===");
        for (String p : List.of("/api/items/1", "/api/orders/9", "/api/points/1001", "/api/unknown")) {
            String target = route(p);
            System.out.println("  " + p + " -> " + (target == null ? "404 无匹配路由" : target));
        }

        System.out.println();
        System.out.println("=== 统一鉴权 + 覆盖内部头（防伪造）===");
        Map<String, String> headers = new HashMap<>();
        headers.put("X-User-Id", "9999");
        System.out.println("  外部请求自带 X-User-Id=" + headers.get("X-User-Id") + "（伪造）");
        String token = "Bearer valid-token-for-u1001";
        if (needAuth("/api/orders") && token.startsWith("Bearer ")) {
            headers.put("X-User-Id", "u1001");
            System.out.println("  网关校验 token 通过，强制覆盖 X-User-Id=" + headers.get("X-User-Id"));
        }
        System.out.println("  下游只信任网关写入的头，绝不透传外部同名头");

        System.out.println();
        System.out.println("=== 接口级限流：令牌桶 容量5 / 每秒2 ===");
        TokenBucket bucket = new TokenBucket(5, 2, 0);
        long now = 0;
        int allowed = 0;
        int rejected = 0;
        for (int i = 1; i <= 12; i++) {
            boolean ok = bucket.tryAcquire(now);
            System.out.println("  t=" + now + "ms 第 " + i + " 个请求 -> " + (ok ? "放行" : "429 限流"));
            if (ok) {
                allowed++;
            } else {
                rejected++;
            }
            now += 300;
        }
        System.out.println("  放行 " + allowed + "，拒绝 " + rejected);
        System.out.println("  令牌桶允许突发（初始 5 个），漏桶则严格匀速");

        System.out.println();
        System.out.println("=== 限流维度 ===");
        Map<String, String> dims = new LinkedHashMap<>();
        dims.put("全局 QPS", "保护整个集群，防止被打穿");
        dims.put("单用户 QPS", "防刷接口，避免一个用户挤占所有人");
        dims.put("单接口 QPS", "保护慢接口（如导出报表）不拖垮线程池");
        dims.put("单 IP 连接数", "防爬虫和异常客户端");
        for (Map.Entry<String, String> e : dims.entrySet()) {
            System.out.println("  " + e.getKey() + "：" + e.getValue());
        }
    }
}
```

**课后练习**
1. 把令牌桶换成漏桶，用同样的请求序列对比放行结果。
2. 设计按用户 ID 的限流：同一用户每秒最多 3 次，超限返回 429，写出核心数据结构。
3. 说明"网关鉴权 + 下游透传外部头"会导致什么安全问题，给出修复方式。
