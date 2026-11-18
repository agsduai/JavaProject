# 第47章 云原生进阶：Service Mesh 与 Serverless

第37章让应用上了 K8s，本章解决"服务之间怎么通话"。治理逻辑散落在各服务的 SDK 里（多语言各写一遍、升级靠发版），Service Mesh 把它们下沉到 Sidecar：重试、灰度、mTLS、故障注入全部变成配置——改策略不重启业务。本章按「从 K8s 到 Mesh → Sidecar 数据面 → 流量治理与故障注入 → mTLS 与零信任 → Mesh 可观测 → Sidecarless 与代价 → Serverless 函数 → 弹性与成本 → 多集群容灾 → 混沌工程 → Mesh 化案例」的顺序讲。所有示例仍是纯 JDK：Sidecar 劫持路径真实推演，金丝雀与超时预算的配置语义真实执行，故障注入实验四步法真跑，启动竞态与双重重试的坑真实复现，不需要 Istio 或 Envoy。

### 47-01 从 K8s 到 Service Mesh

**知识点**
- **K8s 解决了"容器怎么跑"，没解决"服务之间怎么通话"**：重试、超时、熔断、灰度、mTLS 这些治理逻辑散落在每个服务的 SDK 里——**每种语言、每个版本各实现一遍，质量参差**。Service Mesh 把治理逻辑从业务代码抽走，下沉到基础设施层。
- **Mesh 的两层结构**：**数据面**（每个 Pod 旁的代理，转发与治理，如 Envoy）+ **控制面**（下发策略与配置，如 Istiod）。业务代码只管业务，流量治理全部由 Sidecar 执行——**Java 团队不再维护"重试 SDK"**。
- **Mesh 解决的真实痛点**：多语言治理统一（Java/Go/Python 一致行为）；治理逻辑升级不用改业务代码（升级 Sidecar 即全网生效）；流量拓扑自动发现（依赖地图）；mTLS 自动化（证书轮换无人值守）。
- **Mesh 的代价要先看清**：每跳多两代理（延迟 +0.5~2ms）、每 Pod 多一份资源（Sidecar 内存 50~100MB）、运维复杂度上升（控制面本身要高可用）。**服务数量少于 20 个、单语言团队，Mesh 可能是过度设计**——Spring Cloud SDK 方案足够。
- **接入的渐进路径**：先观察（Sidecar 只透传不治理）-> 再治理（重试/超时/灰度策略接管）-> 再安全（mTLS strict）——**逐步收权，随时可退**（41 章灰度思想的基础设施版）。
- **Java 生态的关系**：Spring Cloud（SDK 治理）与 Mesh（Sidecar 治理）是竞争关系——混用时功能重复（SDK 的熔断 + Sidecar 的重试叠加）。**选 Mesh 后 SDK 治理功能应关闭**，避免双重重试放大故障。
- 易错点：SDK 与 Sidecar 双重重试（重试风暴放大，29 章教训）；把 Mesh 当万能（治理问题先问是不是代码 bug）；全量接入不留观察期（问题定位难度陡增）；小规模团队上 Mesh（运维成本大于收益）。

**示例代码**
```java
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class WhyServiceMesh {
    record Concern(String concern, String sdkWay, String meshWay) {
    }

    static void beforeAfter() {
        System.out.println("【治理逻辑：SDK 时代 vs Mesh 时代】");
        List<Concern> concerns = List.of(
                new Concern("重试/超时", "每个服务配 Feign/Resilience4j", "VirtualService 策略，全网统一"),
                new Concern("熔断降级", "Resilience4j 代码级配置", "Sidecar 异常检测自动熔断"),
                new Concern("灰度发布", "网关 + 彩蛋逻辑各自实现", "按 header/比例切流，配置即生效"),
                new Concern("mTLS", "自管证书，轮换脚本", "自动签发轮换，业务无感"),
                new Concern("依赖拓扑", "手工维护文档", "遥测自动生成服务图"));
        for (Concern c : concerns) {
            System.out.printf("  %-8s SDK：%-30s Mesh：%s%n", c.concern(), c.sdkWay(), c.meshWay());
        }
        System.out.println();
    }

    static void layered() {
        System.out.println("【Mesh 两层结构】");
        Map<String, String> layers = new LinkedHashMap<>();
        layers.put("数据面", "Envoy Sidecar：拦截进出 Pod 的流量，执行转发/重试/灰度/mTLS");
        layers.put("控制面", "Istiod：把策略翻译成 Envoy 配置下发；证书签发；服务发现聚合");
        layers.put("业务 Pod", "只有业务容器 + Sidecar 容器，业务代码零治理代码");
        for (Map.Entry<String, String> e : layers.entrySet()) {
            System.out.printf("  %-6s %s%n", e.getKey(), e.getValue());
        }
        System.out.println("  关键理解：治理逻辑是“配置”而不是“代码”——改策略不重启业务");
        System.out.println();
    }

    static void adoptionCurve() {
        System.out.println("【渐进接入：三阶段收权】");
        String[][] phases = {
                {"阶段1 观察", "Sidecar 透传，采集拓扑与延迟基线", "风险为零，产出决策数据"},
                {"阶段2 治理", "重试/超时/灰度接管（SDK 同类功能关闭）", "先在非核心服务验证"},
                {"阶段3 安全", "mTLS PERMISSIVE -> STRICT，零信任落地", "证书体系稳定后切"},
        };
        for (String[] p : phases) {
            System.out.printf("  %-10s %-34s %s%n", p[0], p[1], p[2]);
        }
        System.out.println("  每阶段有回退开关：Sidecar 注入按命名空间灰度");
        System.out.println();
    }

    static void sdkConflict() {
        System.out.println("【双重重试：混用的放大效应】");
        int retriesSdk = 3, retriesMesh = 3;
        int worst = retriesSdk * retriesMesh;
        System.out.printf("  SDK 重试 %d 次 × Sidecar 重试 %d 次 = 最多 %d 次真实请求%n",
                retriesSdk, retriesMesh, worst);
        System.out.println("  下游本来就过载：9 次重试把它彻底打穿（29 章重试风暴）");
        System.out.println("  规则：接入 Mesh 后关闭 SDK 层重试/熔断，治理只留一处");
        System.out.println("  演练：故意下游 500，观察双重重试的请求放大曲线");
    }

    public static void main(String[] args) {
        beforeAfter();
        layered();
        adoptionCurve();
        sdkConflict();
    }
}
```

**课后练习**
1. 盘点你团队 SDK 里的治理功能清单（重试/熔断/灰度），评估"Mesh 接管后需要关闭哪些"，写出冲突矩阵。
2. 设计三阶段接入计划：选一个非核心服务走完观察->治理->mTLS 全流程，记录每阶段的验证证据。
3. 思考题：为什么"服务少于 20 个、单语言团队"不上 Mesh？从运维成本与治理收益的交点分析。

---

### 47-02 Sidecar 与数据面

**知识点**
- **Sidecar 的流量劫持机制**：Pod 网络命名空间内，iptables 把出入流量重定向到 Sidecar 端口——**业务进程对代理无感**（以为在和真实对端通话）。进站流量先过代理再进业务容器，出站反之。
- **Envoy 的配置模型**：Listener（监听）-> Route（路由规则）-> Cluster（上游服务）-> 健康检查/负载均衡；控制面把 VirtualService/DestinationRule 翻译成这些配置。**理解"策略最终编译成 Envoy 配置"才能排查 Mesh 问题**。
- **Sidecar 注入与生命周期**：注入是给 Pod 加一个容器（istio-proxy）；**Sidecar 必须比业务容器先起后停**（否则流量黑洞）——pod 生命周期钩子保证顺序，升级业务镜像时 Sidecar 常驻。
- **超时与重试的执行位置**：重试发生在 Sidecar 而不是业务线程——**业务线程只发起一次调用**，等待时间由 Sidecar 管控（超时预算 44-08 同款思想）。这是"治理下沉"的实质：业务线程不再花时间做重试。
- **常见故障模式**：Sidecar 未就绪业务先起（启动期 503）；iptables 规则冲突（流量绕过代理 -> 遥测丢失）；出站拦截把外部 API 也劫持了（要配出站白名单 ALLOW 外部地址段）。
- **资源与延迟的账**：每跳 Sidecar 增加 ~0.3~1ms 与一次本地环回拷贝；1000 个 Pod = 1000 份 Sidecar 资源（内存 50MB 级）——**延迟与资源开销要进容量规划**（47-06 细讲 sidecarless）。
- 易错点：出站流量默认 BLOCK 后业务调第三方 API 全挂（配 outboundTrafficPolicy）；Sidecar 与业务容器启动顺序错（启动竞态 503）；把业务健康检查端口也被劫持（探针走不通 -> Pod 被反复重启）；Envoy 配置下发的热更新竞态（策略短暂不一致）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.List;

public class SidecarDataplane {
    static void interception() {
        System.out.println("【一次调用的完整路径（带 Sidecar）】");
        List<String> path = List.of(
                "1) 业务容器 A 发起 HTTP -> 目标 order:8080",
                "2) iptables OUTPUT 链劫持 -> 重定向到 Sidecar A 的 15001 端口",
                "3) Sidecar A 查路由：service=order, subset=v2, 重试=2, 超时=200ms",
                "4) mTLS 加密 -> 发往 Sidecar B（目标 Pod）",
                "5) Sidecar B 解密 -> 转发到业务容器 B 的 8080",
                "6) 响应原路返回；两端遥测上报");
        for (String s : path) {
            System.out.println("  " + s);
        }
        System.out.println("  业务代码视角：就像直连 —— 治理全部透明发生");
        System.out.println();
    }

    static void startupRace() {
        System.out.println("【启动竞态：503 的来源与治理】");
        List<String> bad = List.of(
                "Pod 启动：业务容器 3s 就绪，Sidecar 8s 才就绪",
                "就绪探针通过 -> Pod 接流量 -> 流量进 Sidecar（还没配好路由）-> 503");
        for (String s : bad) {
            System.out.println("  " + s);
        }
        List<String> fix = List.of(
                "holdApplicationUntilProxyStarts: true（Sidecar 先起）",
                "就绪探针改为检查“代理链路”而不是业务端口",
                "发布期用 preStop 保证 Sidecar 后停");
        for (String s : fix) {
            System.out.println("  治理：" + s);
        }
        System.out.println("  现象：每次滚动更新开始 30 秒内 5xx 尖刺 —— 典型启动竞态");
        System.out.println();
    }

    static void outboundPolicy() {
        System.out.println("【出站策略：第三方 API 被劫持事故】");
        String[][] modes = {
                {"ALLOW_ANY", "未声明的外部地址直连放行", "宽松，易漏管"},
                {"REGISTRY_ONLY", "只放行网格内服务", "安全，但第三方 API 全断"},
        };
        for (String[] m : modes) {
            System.out.printf("  %-14s %-26s %s%n", m[0], m[1], m[2]);
        }
        System.out.println("  事故：切到 REGISTRY_ONLY 后短信服务不通 -> 配 ServiceEntry 放行白名单");
        System.out.println("  原则：外部依赖显式注册（地址+端口+TLS 策略），默认拒绝");
        System.out.println();
    }

    static void costAccounting() {
        System.out.println("【Sidecar 的资源账】");
        long pods = 500;
        double memPerSidecar = 60, cpuPerSidecar = 0.1;
        System.out.printf("  %d 个 Pod × (内存 %.0fMB + CPU %.1f核) = %.0fGB 内存 / %.0f 核%n",
                pods, memPerSidecar, cpuPerSidecar, pods * memPerSidecar / 1024, pods * cpuPerSidecar);
        double extraLatency = 0.8;
        System.out.printf("  每跳延迟 +%.1fms（进+出各一次代理）%n", extraLatency);
        System.out.println("  结论：Sidecar 成本是网格级的常量开销，进容量规划与 P99 预算");
    }

    public static void main(String[] args) {
        interception();
        startupRace();
        outboundPolicy();
        costAccounting();
    }
}
```

**课后练习**
1. 在 minikube 里注入 Sidecar，用 tcpdump/日志验证"业务以为直连、实际经过代理"的劫持路径。
2. 复现启动竞态 503：关闭 holdApplicationUntilProxyStarts 观察发布期 5xx 尖刺，然后修复并对比。
3. 思考题：为什么"业务线程只发起一次调用"是治理下沉的实质收益？结合 44-08 超时预算分析重试位置对线程池的影响。

---

### 47-03 流量治理与故障注入

**知识点**
- **VirtualService 是流量治理的主战场**：按权重切流（金丝雀，41-07 的基础设施版）、按 header 切流（内部员工先灰）、超时/重试策略（45 章 timeout 预算的落地）、故障注入（混沌）。**所有策略都是配置，秒级生效可回滚**。
- **DestinationRule 管目标侧策略**：子集定义（v1/v2 由 label 划分）、负载均衡算法、连接池大小、熔断（连续 5xx 熔断 N 秒）、TLS 模式。**VirtualService 管怎么路由，DestinationRule 管到那之后怎么办**。
- **金丝雀的 Mesh 版本**：`v1: 95%, v2: 5%` 权重切流，或按 header（`x-canary: true`）定向——**比 41-07 的应用层实现更底层更精准**，且指标天然带版本维度（Sidecar 遥测）。
- **故障注入做混沌实验**：注入延迟（+500ms）/ 注入错误（固定比例 503）——**主动验证依赖方的韧性**（47-10 混沌工程的原语）。注入范围可限定到特定路径与比例。
- **重试与超时的网格级预算**：`timeout: 200ms, retries: 2`——**总预算 = 超时 × (1+重试)**，网关视角（44-08）必须知道下游的这个预算再定自己的；网格级统一配置避免"每个服务自己拍脑袋"。
- **灰度与探针联动**：金丝雀切流前检查 v2 的就绪与错误率（42 章），自动判据（41-07）可接 Mesh 的遥测实现全自动放量。
- 易错点：金丝雀权重切流但会话粘性丢失（同用户在 v1/v2 跳变，41-07 的教训）；重试预算与上游叠加（47-01 双重重试）；故障注入忘删配置（生产持续 500ms 延迟没人发现）；熔断阈值全局一刀切（不同接口容忍度不同）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.List;

public class MeshTrafficPolicy {
    record RouteRule(String subset, int weight, String match) {
    }

    static void canary() {
        System.out.println("【金丝雀切流：两种匹配方式】");
        List<RouteRule> weighted = List.of(
                new RouteRule("v1", 95, "默认"),
                new RouteRule("v2", 5, "默认"));
        List<RouteRule> headered = List.of(
                new RouteRule("v2", 100, "header x-canary=true（内部员工）"),
                new RouteRule("v1", 100, "其余全部流量"));
        System.out.println("  权重模式：");
        for (RouteRule r : weighted) {
            System.out.printf("    subset=%s weight=%d%%（%s）%n", r.subset(), r.weight(), r.match());
        }
        System.out.println("  Header 模式：");
        for (RouteRule r : headered) {
            System.out.printf("    subset=%s -> %s%n", r.subset(), r.match());
        }
        System.out.println("  组合拳：先 header 定向（内部）再权重放量（外部）——41-07 的完整复刻");
        System.out.println();
    }

    static void timeoutBudget() {
        System.out.println("【网格级超时与重试预算】");
        int timeoutMs = 200, retries = 2;
        int worst = timeoutMs * (1 + retries);
        System.out.printf("  策略：timeout=%dms, retries=%d -> 单调用最坏 %dms%n", timeoutMs, retries, worst);
        System.out.println("  上游网关预算必须 > 下游最坏值（44-08 预算分配）：");
        List<String> chain = List.of(
                "gateway: 3000ms 预算",
                "  -> order(VirtualService): 200ms×3 = 600ms",
                "    -> stock: 150ms×2 = 300ms");
        for (String c : chain) {
            System.out.println("  " + c);
        }
        System.out.println("  网格统一配置的价值：预算不再散落在每个服务的代码里");
        System.out.println();
    }

    static void faultInjection() {
        System.out.println("【故障注入：验证依赖方的韧性】");
        record Inject(String target, String type, String spec, String expect) {
        }
        List<Inject> injects = List.of(
                new Inject("order -> stock", "延迟", "固定 +500ms，比例 10%",
                        "调用方超时预算是否足够？熔断是否按预期打开？"),
                new Inject("order -> payment", "错误", "HTTP 503，比例 5%",
                        "降级逻辑触发？用户体验路径？"),
                new Inject("gateway -> order", "中止", "request abort，比例 1%",
                        "监控告警时间线？值班响应速度？"));
        for (Inject i : injects) {
            System.out.printf("  %-16s %-4s %-24s 验证：%s%n",
                    i.target(), i.type(), i.spec(), i.expect());
        }
        System.out.println("  注入三要素：目标 + 类型比例 + 稳态假设（47-10）；实验后立即回收");
        System.out.println();
    }

    static void circuitBreaking() {
        System.out.println("【DestinationRule 熔断：连接池与异常检测】");
        List<String> rules = List.of(
                "连接池：maxConnections=100, http1MaxPendingRequests=50（防排队雪崩）",
                "异常检测：连续 5 个 5xx -> 弹出实例 30s（逐出后探测恢复）",
                "逐出比例上限 50%（防止全部逐出导致零可用）",
                "与 SDK 熔断的差异：网格级统一，不需要每个服务实现");
        for (String r : rules) {
            System.out.println("  - " + r);
        }
        System.out.println("  观察：熔断打开期间错误类型从“超时”变“立即失败”——定位线索");
    }

    public static void main(String[] args) {
        canary();
        timeoutBudget();
        faultInjection();
        circuitBreaking();
    }
}
```

**课后练习**
1. 用 VirtualService 实现完整金丝雀（header 定向 -> 权重放量），期间用遥测验证版本维度错误率，模拟自动判据放量。
2. 设计一次故障注入实验：稳态假设、注入参数、观察指标、中止条件与恢复验证，实验后写报告。
3. 思考题：为什么"重试发生在 Sidecar"改善了业务线程池占用？对比 SDK 重试（占用线程）与 Sidecar 重试（代理层等待）的线程模型差异。

---

### 47-04 mTLS 与零信任

**知识点**
- **零信任的网格化落地**：内网不等于可信（36 章信任边界原则）——Mesh 里**每个服务都有身份（SPIFFE 身份）**，通信默认双向 TLS，授权按"服务身份"而不是 IP。**IP 会变，身份不变**。
- **mTLS 的自动证书管理**：控制面作为 CA 给每个 Sidecar 签发短周期证书（如 24h），自动轮换——**人工管证书的时代结束**。握手由 Sidecar 完成，业务无感。
- **PERMISSIVE 到 STRICT 的迁移**：PERMISSIVE 同时接受明文与 mTLS（过渡期），遥测显示全量加密后切 STRICT（拒绝明文）——**灰度式安全收紧**，与 47-01 三阶段对接。
- **授权策略（AuthorizationPolicy）**：按身份授权——"只有 order 服务可以调 stock.deduct"——**服务间调用的最小权限**（44-09 的四道闸在基础设施层的实现）。拒绝记录可审计。
- **mTLS 的性能与故障面**：握手与加解密开销（毫秒级，连接复用后摊薄）；证书轮换失败的故障模式（Sidecar 证书过期 -> 全网 503）——**证书体系本身要有监控与告警**。
- **与业务安全的关系**：mTLS 解决"传输与身份"，不解决"业务鉴权"（用户级权限仍是业务/网关职责，36 章 JWT）——**两层正交，不互相替代**。
- 易错点：STRICT 切换太急（有服务还没注入 Sidecar，明文流量全断）；把 mTLS 当业务鉴权（服务间身份≠用户身份）；证书轮换窗口无监控（过期事故深夜爆发）；AuthorizationPolicy 过宽（allow all 走形式）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.List;

public class MeshMtls {
    record Identity(String service, String spiffe, String certTtl) {
    }

    static void identityModel() {
        System.out.println("【服务身份：SPIFFE 替代 IP】");
        List<Identity> ids = List.of(
                new Identity("order", "spiffe://prod/ns/prod/sa/order", "24h 自动轮换"),
                new Identity("stock", "spiffe://prod/ns/prod/sa/stock", "24h 自动轮换"),
                new Identity("legacy-job", "未注入 Sidecar -> 无身份", "STRICT 下将被拒绝"));
        for (Identity i : ids) {
            System.out.printf("  %-10s %s（证书 %s）%n", i.service(), i.spiffe(), i.certTtl());
        }
        System.out.println("  Pod IP 重建即变；SPIFFE 身份随服务账号稳定 —— 授权的可靠锚点");
        System.out.println();
    }

    static void strictMigration() {
        System.out.println("【PERMISSIVE -> STRICT 的灰度迁移】");
        List<String> steps = List.of(
                "1) 全网 PERMISSIVE：明文与 mTLS 双收，业务无感",
                "2) 遥测统计：明文流量占比逐日下降（Sidecar 注入覆盖扩大）",
                "3) 明文占比 < 1%%：找出残留明文方（未注入的老服务）",
                "4) 切 STRICT：拒绝一切明文 -> 有明文依赖的服务立刻暴露",
                "5) 回退开关：STRICT -> PERMISSIVE（配置级秒回）");
        for (String s : steps) {
            System.out.println("  " + s);
        }
        System.out.println();
    }

    static void authzPolicy() {
        System.out.println("【授权策略：服务间最小权限】");
        record Rule(String target, String from, String action, String verdict) {
        }
        List<Rule> rules = List.of(
                new Rule("stock.deduct", "仅 order 服务", "ALLOW", "核心写接口收紧"),
                new Rule("stock.query", "order + reporting", "ALLOW", "只读放开给报表"),
                new Rule("stock.admin", "任何人", "DENY", "网格层直接拒绝"),
                new Rule("stock.*", "guest-job（无身份）", "DENY（STRICT 默认）", "无身份即无信任"));
        for (Rule r : rules) {
            System.out.printf("  %-12s 来自 %-16s %s -> %s%n",
                    r.target(), r.from(), r.action(), r.verdict());
        }
        System.out.println("  拒绝日志进审计（44-09 trace 思想）：谁试图越权一目了然");
        System.out.println();
    }

    static void layering() {
        System.out.println("【mTLS 与业务安全的分层】");
        List<String> layers = List.of(
                "传输与身份层（Mesh）：服务间 mTLS + 服务级授权 —— “谁在调我”",
                "业务鉴权层（网关/应用）：用户 JWT、角色权限 —— “用户能干什么”",
                "数据层（DB）：行级权限、字段脱敏 —— “能看哪些数据”");
        for (String l : layers) {
            System.out.println("  " + l);
        }
        System.out.println("  正交不替代：mTLS 通过不代表用户已鉴权；JWT 有效不代表调用方服务可信");
    }

    public static void main(String[] args) {
        identityModel();
        strictMigration();
        authzPolicy();
        layering();
    }
}
```

**课后练习**
1. 在你的网格（或模拟）完成 PERMISSIVE -> STRICT 迁移演练，统计明文流量下降曲线与 STRICT 切换后的拒绝日志。
2. 写出核心链路的 AuthorizationPolicy 矩阵（至少 4 条规则），演示一次"未注入服务被拒绝"的审计记录。
3. 思考题：为什么服务间 mTLS 不能替代用户级 JWT 鉴权？从身份主体（服务 vs 用户）与信任传递的断点分析。

### 47-05 Mesh 的可观测性

**知识点**
- **Mesh 送的第二份大礼：零侵入的全量遥测**——每个 Sidecar 天然上报请求级指标（QPS/延迟/错误率，带 source/destination/version 标签）与调用拓扑。**42 章埋点清单里的“接口四信号”，Mesh 场景自动获得**。
- **Mesh 指标的独特维度**：`source_workload`、`destination_service`、`response_code`、`canary=v2`——**跨服务的错误归属**（谁先报错）与版本对比（41-07）开箱即用。业务代码仍需补业务指标（下单量等），**基础设施指标与业务指标互补**。
- **trace 传播的网格协助**：Sidecar 自动注入/传递 trace headers（W3C traceparent），**应用只需要把头接起来**——42-07 的“断裂点”清单在 Mesh 下大幅缩短（异步与手写 HTTP 仍要自己传）。
- **Mesh 自身的可观测**：控制面（istiod 配置分发成功率、证书签发延迟）、数据面（Envoy 配置版本一致性、连接池水位）——**网格是新的基础设施组件，也要四信号**（42-05），否则“策略没生效”这种故障无法定位。
- **遥测的量与采样**：Sidecar 全量上报基础指标（便宜），访问日志（envoy access log）按需开启（量大），trace 采样走 42-08 的策略（头部 1% + 尾部全保错误）。
- 易错点：只看业务指标不看网格指标（策略未生效时业务指标照旧，错误无从归因）；访问日志全开（存储爆炸）；把 Mesh 遥测当业务监控的替代（业务语义指标缺失）；控制面无监控（配置分发失败全网无感）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MeshObservability {
    static void freeMetrics() {
        System.out.println("【Sidecar 免费送的指标（42-01 埋点清单的对账）】");
        Map<String, String> auto = new LinkedHashMap<>();
        auto.put("QPS/延迟/错误率", "按 source/destination/version 维度全量上报");
        auto.put("依赖拓扑", "服务图自动生成（42-07 扇出分析）");
        auto.put("trace 传播", "headers 自动注入/传递（应用补业务 span）");
        Map<String, String> manual = new LinkedHashMap<>();
        manual.put("业务指标", "下单量/支付成功率（Sidecar 不懂业务）");
        manual.put("资源内因", "JVM 内存池、连接池（42-05 饱和度）");
        for (Map.Entry<String, String> e : auto.entrySet()) {
            System.out.printf("  自动：%-16s %s%n", e.getKey(), e.getValue());
        }
        for (Map.Entry<String, String> e : manual.entrySet()) {
            System.out.printf("  仍需埋：%-14s %s%n", e.getKey(), e.getValue());
        }
        System.out.println();
    }

    static void blameAttribution() {
        System.out.println("【错误归属：source/destination 维度的威力】");
        record Row(String src, String dst, String code, long qps, String verdict) {
        }
        List<Row> rows = List.of(
                new Row("gateway", "order", "500", 4, "order 内部错误（真正的病灶）"),
                new Row("order", "stock", "503", 42, "stock 过载/熔断（上游表象）"),
                new Row("order", "db", "-", 0, "无连接错误 -> 排除 DB"));
        for (Row r : rows) {
            System.out.printf("  %-8s -> %-6s code=%-4s %3d rps  %s%n",
                    r.src(), r.dst(), r.code(), r.qps(), r.verdict());
        }
        System.out.println("  归因链：gateway 看到的 500，根因在 order<-stock 的 503");
        System.out.println("  对照无 Mesh：只有各自服务的日志，归属靠人翻");
        System.out.println();
    }

    static void meshSelfOps() {
        System.out.println("【网格自身的四信号：别忘了自己也是组件】");
        List<String> signals = List.of(
                "控制面：配置分发成功率/延迟（策略没生效=隐性故障）",
                "证书：签发延迟与到期监控（47-04 轮换事故预防）",
                "数据面：Sidecar 配置版本一致性、Envoy 内存水位",
                "遥测管道：指标丢弃率（上报过载时先丢遥测再丢请求）");
        for (String s : signals) {
            System.out.println("  - " + s);
        }
        System.out.println("  排障口诀：策略不生效 -> 先查分发，再查 Pod 是否注入");
        System.out.println();
    }

    static void samplingPolicy() {
        System.out.println("【遥测的量治理（42-08 在网格的落地）】");
        List<String> rules = List.of(
                "基础指标全量（聚合数字，便宜）",
                "访问日志按需开启：排障期临时开 + 生产默认关",
                "trace 头部 1% + 错误全保（尾部采样在网关聚合）",
                "指标 label 基数红线依旧：不把 userId 塞进网格指标");
        for (String r : rules) {
            System.out.println("  - " + r);
        }
    }

    public static void main(String[] args) {
        freeMetrics();
        blameAttribution();
        meshSelfOps();
        samplingPolicy();
    }
}
```

**课后练习**
1. 接入网格前后各采集一次遥测，制作“埋点清单对账表”（哪些从手工埋点变成免费赠送）。
2. 用 source/destination 指标做一次错误归因演练：注入 stock 故障，从 gateway 视角追到根因服务并记录耗时。
3. 思考题：为什么“策略没生效”是 Mesh 特有的隐性故障？设计一套控制面分发健康度的监控指标。

---

### 47-06 Sidecarless 与 Mesh 的代价

**知识点**
- **Sidecar 模式的成本结构**：每个 Pod 一份代理（资源 + 启动延迟 + 升级风暴——500 个 Pod 升级 Sidecar 就是 500 份重启）。**规模化后这些成本线性放大**，大集群（数千 Pod）开始不可忽视。
- **Sidecarless 的两种形态**：**每节点共享代理**（node-level proxy，如 Cilium eBPF 数据面——内核态拦截，开销更低）与 **Ambient 模式**（ztunnel 每节点共享 L4 层 + 可选 L7 waypoint）——**把“每 Pod 一份”变成“每节点一份”**。
- **eBPF 的角色**：内核态完成流量拦截与部分处理（重定向、L4 负载均衡、观测点），**绕过用户态代理的部分开销**；L7（HTTP 语义）处理仍需用户态组件。性能收益显著但可观测与策略模型随之变化。
- **Mesh 的总拥有成本（TCO）账**：资源账（Sidecar × Pod 数）、延迟账（每跳 +0.5~1ms）、运维账（控制面 HA + 版本兼容 + 排障技能）、机会账（SDK 迁移成本）。**规模化前算总账**，不要为“时髦”上 Mesh。
- **选型决策树**：多语言 + 服务 >50 + 治理需求复杂 -> Mesh 收益明确；单语言 + 服务少 -> SDK；超大集群 -> Sidecarless Mesh（Ambient/Cilium）。**没有普适答案，按集群规模与治理诉求定**。
- 易错点：只算资源不算运维账（控制面故障 = 全网故障）；Sidecar 升级全量齐步走（发布风暴，应分批灰度）；eBPF 方案忽略 L7 能力缺口（HTTP 灰度/重试仍需 L7 层）；为小集群引入 Ambient（学习与迁移成本大于收益）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.List;

public class MeshCost {
        record PodCount(int pods, double sidecarMemGb, int upgradeBlast, String note) {
        }

    static void scaleMath() {
        System.out.println("【规模放大：Sidecar 成本随 Pod 数线性增长】");
        List<PodCount> scales = List.of(
                new PodCount(50, 3, 50, "小集群：成本可忽略"),
                new PodCount(500, 30, 500, "中型：容量规划必须包含"),
                new PodCount(5000, 300, 5000, "大型：Sidecarless 开始有意义"));
        for (PodCount p : scales) {
            System.out.printf("  %5d Pod：Sidecar 内存 %.0fGB，升级影响面 %d Pod%n",
                    p.pods(), p.sidecarMemGb(), p.upgradeBlast());
        }
        System.out.println("  升级风暴：Sidecar CVE 修复 = 全量重启代理 -> 分批灰度 + 节点池隔离");
        System.out.println();
    }

    static void sidecarless() {
        System.out.println("【Sidecarless：每节点一份代替每 Pod 一份】");
        String[][] rows = {
                {"每 Pod Sidecar", "独立资源、强隔离、升级=逐 Pod 重启", "中小规模"},
                {"每节点 ztunnel(ambient)", "L4 共享，资源省 60%+，Pod 无感", "大规模网格"},
                {"eBPF 数据面", "内核态拦截，延迟最低", "性能敏感 + 具备内核能力"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-24s %-34s %s%n", r[0], r[1], r[2]);
        }
        System.out.println("  共同点：数据面下沉后，L7 策略（HTTP 重试/灰度）需要独立 waypoint 层");
        System.out.println();
    }

    static void tcoChecklist() {
        System.out.println("【Mesh TCO 总账清单】");
        List<String> items = List.of(
                "资源：Sidecar/节点代理 × 全量 Pod + 控制面 HA",
                "延迟：每跳 +0.5~1ms 进 P99 预算（42-05 SLO 影响）",
                "运维：控制面版本兼容、排障技能栈、证书体系",
                "迁移：SDK 治理功能下线 + 团队培训",
                "收益对账：治理统一省下的重复建设（多语言 SDK 维护成本）");
        for (String i : items) {
            System.out.println("  [ ] " + i);
        }
        System.out.println("  决策：收益 > 总成本才立项，试点先行");
    }

    static void upgradeDiscipline() {
        System.out.println("【Sidecar 升级的纪律】");
        List<String> steps = List.of(
                "CVE 公告 -> 影响面评估 -> 版本兼容矩阵核对",
                "分批：test 命名空间 -> 边缘服务 -> 核心链路（41-07 灰度思想）",
                "每批观察：5xx、延迟、连接池指标，达标再推进",
                "回退预案：控制面回滚 + Sidecar 版本锁定");
        for (String s : steps) {
            System.out.println("  " + s);
        }
        System.out.println("  反例：全量齐步升级 -> Envoy 配置兼容 bug -> 全网 503");
    }

    public static void main(String[] args) {
        scaleMath();
        sidecarless();
        tcoChecklist();
        upgradeDiscipline();
    }
}
```

**课后练习**
1. 用你的集群规模算 Mesh TCO 总账（资源/延迟/运维/迁移四项），给出“上/不上”的量化结论。
2. 设计一次 Sidecar 升级的分批方案（批次划分、观察指标、回退预案），并演示一批失败后的回退。
3. 思考题：为什么 L7 策略在 Sidecarless 架构中需要独立的 waypoint 层？从 L4 与 L7 语义处理的差异分析。

---

### 47-07 Serverless 函数

**知识点**
- **FaaS 的交付单位是“函数”而不是“服务”**：上传代码 -> 平台负责伸缩与运维 -> 按调用计费（空闲零成本）。适合**事件驱动、突发、无状态**的工作负载：图片处理、Webhook、定时任务、轻量 API。
- **冷启动是 FaaS 的第一约束**：函数实例从零拉起（容器创建 + 运行时初始化 + 连接建立），Java 冷启动秒级（比 Go/Node 慢）——**对策**：预留实例（保底并发）、快照恢复（Firecracker snapshot）、GraalVM native-image（Java 的主流答案，启动毫秒级）。
- **无状态是硬约束**：本地内存/文件不持久（实例随时回收）——**状态外置**到 Redis/DB/对象存储；连接池语义变化（不能常驻长连接，用短连或代理）。44-05 的本地模型不适合 FaaS（大模型常驻）——**有状态与常驻组件不适合 FaaS**。
- **事件模型与幂等**：FaaS 常由消息/定时触发——**至少一次投递**（28 章语义），函数必须幂等；重试与死信由平台管，业务要配置。
- **可观测性外包给平台**：平台日志/指标开箱即用（42 章的简化版），但**自定义指标与 trace 要主动埋**；冷启动率、并发上限、错误率是 FaaS 特有指标。
- **与 K8s 服务的关系**：核心常驻服务（有状态、长连接、稳定 QPS）留在 K8s；**边缘场景（突发/事件/低频）用 FaaS**——混合架构是常态，不是二选一。
- 易错点：把核心交易链路放 FaaS（冷启动抖动不可接受）；函数里建常驻连接池（实例回收泄漏）；忽略至少一次语义（重复处理无幂等）；供应商锁定无迁移预案（平台私有 API 大面积使用）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ServerlessFn {
    static void coldStartMath() {
        System.out.println("【冷启动分解：为什么 Java 慢】");
        record Phase(String phase, long jvmMs, long nativeMs) {
        }
        List<Phase> phases = List.of(
                new Phase("容器/沙箱拉起", 300, 120),
                new Phase("运行时初始化", 1200, 80),
                new Phase("框架/Spring 上下文", 2500, 150),
                new Phase("首请求 JIT 预热", 800, 20));
        long jvm = phases.stream().mapToLong(Phase::jvmMs).sum();
        long nat = phases.stream().mapToLong(Phase::nativeMs).sum();
        for (Phase p : phases) {
            System.out.printf("  %-18s JVM=%-6d Native=%d%n", p.phase(), p.jvmMs(), p.nativeMs());
        }
        System.out.printf("  合计：JVM %dms vs GraalVM native %dms%n", jvm, nat);
        System.out.println("  对策链：native-image -> 预留实例 -> 快照恢复 -> 缩小依赖");
        System.out.println();
    }

    static void statelessRules() {
        System.out.println("【无状态硬约束的状态外置】");
        String[][] rows = {
                {"会话", "Redis/JWT 无会话", "本地内存不可依赖"},
                {"计数/幂等键", "Redis SETNX + TTL", "函数实例随时回收"},
                {"大文件", "对象存储直传", "本地磁盘临时且小"},
                {"常驻连接", "短连/HTTP 代理", "池化被回收打断"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-10s %-24s %s%n", r[0], r[1], r[2]);
        }
        System.out.println("  判据：这个函数回收重建后，行为是否完全一致？");
        System.out.println();
    }

    static void eventSemantics() {
        System.out.println("【事件投递：至少一次 + 平台重试】");
        List<String> flow = List.of(
                "触发：消息队列/定时器/HTTP",
                "投递失败/超时 -> 平台按策略重试（指数退避）",
                "多次失败 -> 死信队列（28 章语义在 FaaS 的复用）",
                "业务函数必须幂等（28/43 章原则）：按事件 ID 去重",
                "定时器注意：错过触发点的补偿语义要确认（错过的 cron 是否补跑）");
        for (String s : flow) {
            System.out.println("  " + s);
        }
        System.out.println();
    }

    static void fitMatrix() {
        System.out.println("【哪些负载适合 FaaS】");
        Map<String, String> fit = new LinkedHashMap<>();
        fit.put("适合", "图片/文件处理、Webhook、低频定时任务、突发活动页");
        fit.put("不适合", "核心交易链路、长连接服务、大模型推理（常驻 GPU）、高 QPS 稳态服务");
        fit.put("混合策略", "K8s 常驻核心 + FaaS 处理边缘事件，事件总线衔接");
        for (Map.Entry<String, String> e : fit.entrySet()) {
            System.out.printf("  %-6s %s%n", e.getKey(), e.getValue());
        }
    }

    public static void main(String[] args) {
        coldStartMath();
        statelessRules();
        eventSemantics();
        fitMatrix();
    }
}
```

**课后练习**
1. 把一个低频定时任务迁移到 FaaS（或模拟），记录冷启动耗时与成本对比（常驻 vs 按调用）。
2. 用 GraalVM native-image（或模拟数据）改造一个 Spring 函数，量化启动时间改善，并评估 AOT 对反射/动态代理的限制。
3. 思考题：为什么“核心交易链路不适合 FaaS”？从冷启动 P99、长连接、可观测深度三个维度分析。

---

### 47-08 弹性伸缩与成本

**知识点**
- **K8s 弹性三层**：**HPA**（水平自动扩缩，按 CPU/内存/自定义指标）、**VPA**（垂直调整单 Pod 资源，需重启）、**Cluster Autoscaler**（节点池扩缩）。**FaaS 的弹性内置**（并发驱动），K8s 的弹性要自己配。
- **伸缩的正确指标**：CPU 是滞后指标——**更贴近用户的是 QPS/并发/队列深度**（42-05 饱和度信号做伸缩输入）。缩容要防抖（稳定窗口），避免流量抖动引发雪崩式伸缩。
- **KEDA 事件驱动伸缩**：按消息 lag、队列深度扩缩——**43 章消费积压直接驱动副本数**（lag>阈值 -> 扩容），缩容到零支持（低频消费者白天 0 副本）。
- **Spot/抢占式实例的成本杠杆**：抢占式节点便宜 60~90%，适合无状态可重启负载（批处理、CI）；**核心服务不跑 Spot**（节点随时被回收）。组合：核心服务按需 + 批处理 Spot。
- **成本可观测**：按命名空间/服务归因成本（request×时长）、闲置检测（requests 远高于实际用量 -> rightsizing）、资源配额防失控。**41-10 DORA 的成本版：成本也进例会**。
- **弹性演练**：扩容速度实测（从触发到就绪的分钟级延迟——发布/扩容都有滞后），压测验证 HPA 配置的响应曲线（42 章方法论）。
- 易错点：HPA 目标 CPU 设太高（打满才扩，已晚）；min/max 无上限（故障循环扩容烧钱）；缩容抖动（稳定窗口没配）；Spot 跑核心服务（被回收即事故）；VPA 与 HPA 同指标冲突（一个加 Pod 一个加内存打架）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.List;

public class ElasticCost {
    static void hpaConfig() {
        System.out.println("【HPA 配置：指标、阈值与防抖】");
        record Param(String name, String value, String why) {
        }
        List<Param> params = List.of(
                new Param("目标指标", "QPS/并发（优先）或 CPU 65%", "CPU 滞后，QPS 贴近用户"),
                new Param("min/max 副本", "3 ~ 20", "下限保可用，上限防失控"),
                new Param("稳定窗口", "缩容 300s / 扩容 60s", "防流量抖动引发振荡"),
                new Param("行为策略", "扩容最多 +100%/15s", "防止瞬间翻倍打挂下游"));
        for (Param p : params) {
            System.out.printf("  %-10s %-26s %s%n", p.name(), p.value(), p.why());
        }
        System.out.println("  压测验证：阶梯加压，记录“QPS -> 副本数”的响应滞后");
        System.out.println();
    }

    static void kedaLag() {
        System.out.println("【KEDA：消费积压驱动伸缩】");
        record Trigger(String metric, String rule, String action) {
        }
        List<Trigger> ts = List.of(
                new Trigger("kafka lag", "lag > 10000 -> 扩容", "43-08 反压的自动处置"),
                new Trigger("队列空闲", "lag=0 持续 10min -> 缩到 0", "低频消费者夜间零成本"),
                new Trigger("冷启动保护", "min=1（有状态消费者）", "缩到零要求无状态/可冷启"));
        for (Trigger t : ts) {
            System.out.printf("  %-12s %-24s %s%n", t.metric(), t.rule(), t.action());
        }
        System.out.println();
    }

    static void spotStrategy() {
        System.out.println("【Spot 实例的成本杠杆】");
        List<String> rules = List.of(
                "适合：批处理、CI、无状态可重启服务（43 章流作业的批模式）",
                "不适合：共识集群（45-10）、核心常驻、有状态主节点",
                "组合：核心服务按需节点池 + 批处理 Spot 节点池 + 亲和性隔离",
                "兜底：Spot 回收告警 -> 工作负载漂移到按需池");
        for (String r : rules) {
            System.out.println("  - " + r);
        }
        System.out.println("  收益：批处理成本降 60~90%；风险：2 分钟回收通知要能处理");
        System.out.println();
    }

    static void costObservability() {
        System.out.println("【成本可观测与治理】");
        List<String> items = List.of(
                "按 namespace/服务归因：request×时长折算月成本（41-10 台账思想）",
                "闲置检测：requests 与实际用量 diff -> rightsizing 建议",
                "配额：namespace ResourceQuota 防失控",
                "月度成本例会：Top5 成本服务与优化动作");
        for (String i : items) {
            System.out.println("  [ ] " + i);
        }
        System.out.println("  原则：成本是架构决策的输入，不是季度末的惊讶");
    }

    public static void main(String[] args) {
        hpaConfig();
        kedaLag();
        spotStrategy();
        costObservability();
    }
}
```

**课后练习**
1. 为你的服务配置 HPA（QPS 指标 + 防抖参数），压测验证扩缩曲线并记录响应滞后。
2. 用 KEDA 把一个消费者的副本数绑定到 lag，构造积压场景验证自动扩容与缩容到零。
3. 思考题：为什么 Spot 实例对共识集群是禁区？结合 45-10 成员变更与节点回收的关系分析。

---

### 47-09 多集群与容灾

**知识点**
- **单集群的爆炸半径**：控制面故障 = 全部服务失联；版本升级停机窗口 = 全业务窗口。**多集群是爆炸半径的物理隔离**——但多集群也引入了多倍的运维与一致性问题。
- **多集群的三种形态**：**主备**（备集群冷/温待命，故障时切换——简单，切换慢）；**双活**（两集群同时接流量，按地域就近——成本高，容灾最快）；**联邦/多集群 Mesh**（跨集群服务发现与统一治理——复杂度最高）。
- **容灾的目标值**：RTO（恢复时间目标）与 RPO（数据丢失容忍）——**演练出的真实值才算数**（41-11 演练纪律的容灾版）。切换手册要写到“值班可执行”的粒度。
- **跨集群流量切分**：DNS/全局负载均衡按地域与健康度切流——**健康检查 + 自动 failover**；跨集群金丝雀（先切 5% 验证再全量，41-07 思想）。
- **数据层是容灾的真正难点**：应用多活容易，**数据库多活难**（跨机房写入延迟与冲突，39-09 跨库一致性的终极版）——常见折衷：单元化（按用户分片归属机房）或主从 + 手动切换。
- **演练纪律**：每季度真实切换演练（不是纸面预案）；演练包含数据库主从切换、DNS 生效时间、缓存预热时长——**切换耗时的真实数据决定 RTO 承诺**。
- 易错点：双集群没有双向数据同步（切换后数据断层）；演练只切流量不切数据（真实事故时才发现数据层不可切）；DNS TTL 太长（切流生效慢）；跨集群配置漂移（两集群配置不同步，切换后行为不一致）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.List;

public class MultiCluster {
    record Topology(String mode, String rto, String cost, String fit) {
    }

    static void topologies() {
        System.out.println("【三种多集群形态】");
        List<Topology> tps = List.of(
                new Topology("主备", "30min~小时级", "低（备集群缩容待命）", "预算有限，RTO 宽松"),
                new Topology("双活", "分钟级", "高（两倍资源+数据同步）", "核心业务，RTO 要求高"),
                new Topology("联邦 Mesh", "秒级（自动 failover）", "最高", "大规模多地域"));
        for (Topology t : tps) {
            System.out.printf("  %-8s RTO=%-14s 成本=%-24s %s%n", t.mode(), t.rto(), t.cost(), t.fit());
        }
        System.out.println();
    }

    static void drillTimeline() {
        System.out.println("【一次真实切换演练的时间线】");
        record Step(String clock, String action, String measured) {
        }
        List<Step> steps = List.of(
                new Step("T+0", "决策切换（主集群 A 故障）", "IC 确认，预案启动"),
                new Step("T+2min", "数据库主从切换（B 集群提升主）", "实测 90s"),
                new Step("T+3min", "DNS/全局 LB 切流", "TTL 60s，生效实测 2min"),
                new Step("T+5min", "B 集群缓存预热", "命中率恢复到 85% 耗时 2min"),
                new Step("T+7min", "业务验证核心链路", "冒烟 6 项通过"),
                new Step("T+10min", "对外公告恢复", "RTO 实测 10 分钟"));
        for (Step s : steps) {
            System.out.printf("  %s  %-28s %s%n", s.clock(), s.action(), s.measured());
        }
        System.out.println("  关键发现：缓存预热是切换最容易被低估的环节");
        System.out.println();
    }

    static void dataLayerReality() {
        System.out.println("【数据层：容灾的真正难点】");
        String[][] rows = {
                {"单主 + 从库", "主挂后提升从库（RPO=异步复制差量）", "简单，切换期间短暂只读"},
                {"双向同步", "两集群都可写，冲突检测（45-07 向量时钟）", "复杂，适合单元化"},
                {"单元化", "用户按分片归属机房，写不跨机房", "大厂标准答案，改造成本高"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-10s %-34s %s%n", r[0], r[1], r[2]);
        }
        System.out.println("  原则：应用层的多活必须先回答数据层方案，否则是伪多活");
        System.out.println();
    }

    static void configSync() {
        System.out.println("【跨集群配置一致性】");
        List<String> items = List.of(
                "配置与编排用 Git 统一管理（GitOps），两集群从同一仓库渲染",
                "定期 diff 两集群实际状态 vs 声明状态（45 章对账思想）",
                "变更在测试集群验证后自动同步（41 章流水线复用）",
                "漂移告警：手工改动 24h 内必须收敛或回滚");
        for (String i : items) {
            System.out.println("  [ ] " + i);
        }
        System.out.println("  反例：B 集群 JVM 参数比 A 旧一版 -> 切换后性能异常");
    }

    public static void main(String[] args) {
        topologies();
        drillTimeline();
        dataLayerReality();
        configSync();
    }
}
```

**课后练习**
1. 为你的业务选择多集群形态并计算 RTO/RPO 承诺，列出切换手册（值班可执行粒度）。
2. 做一次真实切换演练（或桌面推演），重点测量数据库切换、DNS 生效、缓存预热三段耗时。
3. 思考题：为什么“应用多活而数据单主”是伪多活？从主库故障时写路径的状态分析，给出单元化如何解决。

---

### 47-10 混沌工程

**知识点**
- **混沌工程的定义：在受控条件下主动注入故障，验证系统韧性的科学方法**——不是“乱搞生产”，而是有稳态假设、有实验假设、有中止条件的实验设计（47-03 的故障注入原语 + 41-11 演练纪律 + 42-11 复盘方法）。
- **四步实验法**：① 定义稳态（正常时的指标基线）；② 假设（注入 X 后系统仍满足稳态）；③ 注入（延迟/错误/资源耗尽/节点消灭）；④ 观察与验证（假设成立或发现缺陷）。**每次实验产出要么是“韧性确认”，要么是“改进项”**。
- **实验的爆炸半径控制**：从最小流量开始（金丝雀注入）、非高峰时段、可随时中止（一键停止）、生产前先在预发验证。**“第一次混沌实验打挂生产”是教训不是方法论**——Netflix 也是从基础设施演练开始的。
- **注入的层次**：基础设施层（杀节点/磁盘满）、网络层（延迟/分区/丢包）、应用层（依赖超时/错误注入/时钟跳变）、数据层（慢查询/主从延迟放大）。**从低层开始逐层深入**。
- **实验对象优先级**：依赖最多且无降级的服务 > 刚上线的弹性配置 > 新的容灾机制。**每次大事故后的改进项都应该变成下一次实验的验证目标**（42-11 复盘的闭环）。
- **组织形态**：混沌实验要审批与值守（像发布一样走流程，41-10）；游戏日（game day）团队协作演练；实验结果入库形成韧性知识库。
- 易错点：无稳态假设的“乱注入”（出事了不知道是不是注入导致）；在生产高峰做实验；实验发现缺陷不跟进修复（白炸）；把混沌当猎奇（没有把改进项闭环到 backlog）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ChaosEngineering {
    record Experiment(String name, String steadyState, String hypothesis, String inject,
                      String abort, String outcome) {
    }

    static void experimentDesign() {
        System.out.println("【实验设计：下单依赖库存超时韧性】");
        List<Experiment> exps = List.of(
                new Experiment("EXP-01 库存延迟",
                        "下单成功率>99.5%，P99<300ms",
                        "注入 200ms 延迟后仍满足稳态",
                        "Mesh 故障注入：延迟+200ms，比例 20%",
                        "成功率<99% 立即中止",
                        "假设成立：重试+降级生效，P99+40ms"),
                new Experiment("EXP-02 库存错误",
                        "同上",
                        "5% 503 注入下熔断按预期打开",
                        "错误注入：503 比例 5%",
                        "错误率>5% 或大面积客诉",
                        "发现缺陷：熔断阈值过宽 -> 调整为连续 5 次"),
                new Experiment("EXP-03 节点消灭",
                        "同上",
                        "随机杀 1 个 order Pod 无感",
                        "kill -9 + K8s 自愈",
                        "副本不可用超 2 分钟",
                        "韧性确认：30s 内新 Pod 接管"));
        for (Experiment e : exps) {
            System.out.printf("  %s%n    稳态：%s%n    假设：%s%n    注入：%s%n    中止：%s%n    结果：%s%n",
                    e.name(), e.steadyState(), e.hypothesis(), e.inject(), e.abort(), e.outcome());
        }
        System.out.println();
    }

    static void blastRadius() {
        System.out.println("【爆炸半径控制】");
        List<String> controls = List.of(
                "时间：非高峰 + 值班在岗 + 变更冻结期外",
                "范围：金丝雀比例注入（5% 起），只影响部分用户",
                "中止：一键停止注入 + 自动中止阈值（成功率跌破）",
                "预演：相同实验先在 staging 完整跑通",
                "审批：实验单审批（41-10 发布单同构）");
        for (String c : controls) {
            System.out.println("  - " + c);
        }
        System.out.println("  红线：资金链路首次实验必须在预发；生产实验最小化用户影响");
        System.out.println();
    }

    static void closedLoop() {
        System.out.println("【实验产出进闭环：韧性的持续改进】");
        Map<String, String> loop = new LinkedHashMap<>();
        loop.put("EXP-02 发现熔断过宽", "改进项：调整阈值 -> 下次实验验证 -> 关闭");
        loop.put("EXP-01 确认降级有效", "知识库：记录“stock 延迟 200ms 无影响”作为容量依据");
        loop.put("历史事故 INC-456", "复现实验：连接池耗尽注入 -> 验证修复（42-11）");
        for (Map.Entry<String, String> e : loop.entrySet()) {
            System.out.printf("  %-20s %s%n", e.getKey(), e.getValue());
        }
        System.out.println("  判定：每次事故的修复，都应有对应的混沌实验证明它真的修好了");
    }

    public static void main(String[] args) {
        experimentDesign();
        blastRadius();
        closedLoop();
    }
}
```

**课后练习**
1. 设计并执行（或桌面推演）三个混沌实验（延迟/错误/资源耗尽），完整写出四步实验法文档。
2. 为实验平台实现“一键中止”与自动中止阈值（成功率跌破即停），并用模拟故障验证。
3. 思考题：为什么“无稳态假设的注入”是危险的？从事故归因与实验可信度两个角度分析稳态基线的作用。

---

### 47-11 综合案例：Mesh 化的订单服务

**知识点**
- **把本章串成一条 Mesh 化改造全程**：现状盘点（SDK 治理清单）-> 三阶段接入（观察 -> 治理 -> mTLS，47-01）-> 灰度发布迁移到 VirtualService（47-03）-> 遥测接管（47-05）-> 混沌验证（47-10）-> TCO 复盘（47-06）。**41 章的发布工程与 42 章的可观测在基础设施层重逢**。
- **阶段成果量化**：治理代码删除（SDK 重试/熔断配置 -1200 行）、灰度效率（应用层实现 3 天 -> 配置 10 分钟）、mTLS 覆盖 100%（证书轮换零人工）、错误归因时间（平均 25 分钟 -> 8 分钟，47-05 归因链）。**每个收益都要有 before/after 数据**。
- **迁移中的坑实录**：双重重试放大（47-01，灰度期出现）、启动竞态 503（47-02，发布尖刺）、出站策略断短信（47-02）、熔断阈值与 SDK 残留冲突——**每个坑都是回退开关救的场**。
- **混沌验收**：改造完成的标志不是“功能正常”，而是“故障实验通过”——依赖延迟/错误注入、节点消灭、证书轮换演练全部达标。
- **运营常态化**：策略变更走流水线（41 章思想）、网格四信号监控（47-05）、Sidecar 升级分批纪律（47-06）、季度混沌游戏日。
- 易错点：Mesh 化后不管 SDK 技术债（旧配置残留引发行为叠加）；把“接入完成”当终点（策略随业务演进）；忽略团队技能转型（排障从读代码变成读 Envoy 配置）；指标面板未更新到网格维度（42 章面板仍按旧埋点）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MeshCase {
    record Milestone(String phase, String actions, String metric, String result) {
    }

    static void migrationJourney() {
        System.out.println("【order 服务 Mesh 化的三个月】");
        List<Milestone> ms = List.of(
                new Milestone("第1-2周 观察", "注入 Sidecar，透传不治理", "拓扑与延迟基线",
                        "识别出 3 个隐性依赖（未注册的内部调用）"),
                new Milestone("第3-6周 治理", "重试/超时/熔断迁到网格；SDK 同类配置下线",
                        "治理代码 -1200 行；灰度效率 3天->10分钟",
                        "踩坑：双重重试放大（灰度期发现并修正）"),
                new Milestone("第7-10周 安全", "mTLS PERMISSIVE -> STRICT；授权策略矩阵",
                        "mTLS 覆盖 100%；证书轮换零人工",
                        "踩坑：出站策略断短信（补 ServiceEntry）"),
                new Milestone("第11-12周 验收", "混沌实验 + TCO 复盘", "3 个实验全部达标",
                        "错误归因 25min -> 8min"));
        for (Milestone m : ms) {
            System.out.printf("  %-12s %s%n    指标：%s%n    结果：%s%n",
                    m.phase(), m.actions(), m.metric(), m.result());
        }
        System.out.println();
    }

    static void lessons() {
        System.out.println("【踩坑实录与回退开关】");
        record Pitfall(String issue, String rootCause, String fix, String guard) {
        }
        List<Pitfall> pits = List.of(
                new Pitfall("灰度期 5xx 放大 3 倍", "SDK 重试 × Sidecar 重试叠加",
                        "下线 SDK 重试", "接入清单强制核对"),
                new Pitfall("每次发布前 30s 尖刺", "Sidecar 启动竞态",
                        "holdApplicationUntilProxyStarts", "发布指标盯 5xx"),
                new Pitfall("短信全断", "REGISTRY_ONLY 出站策略",
                        "ServiceEntry 白名单", "外部依赖清单入目录"));
        for (Pitfall p : pits) {
            System.out.printf("  %-20s 根因=%-22s 修复=%-28s 防复发=%s%n",
                    p.issue(), p.rootCause(), p.fix(), p.guard());
        }
        System.out.println("  每个坑都有回退开关兜底：策略配置级回退，秒级生效");
        System.out.println();
    }

    static void steadyStateOps() {
        System.out.println("【常态化运营清单】");
        List<String> items = List.of(
                "策略变更走流水线（评审+灰度+审计，41 章流程）",
                "网格四信号监控 + 证书到期告警（47-05）",
                "Sidecar 升级季度排期，分批推进（47-06）",
                "混沌游戏日：每季度 2-3 个实验（47-10）",
                "策略目录：所有 VirtualService/DestinationRule 有 owner 与用途注释",
                "技能建设：团队 Envoy/网格排障培训（Anthos 式知识沉淀）");
        for (String i : items) {
            System.out.println("  [ ] " + i);
        }
        System.out.println("  终局思维：Mesh 是长期基础设施，接入只是开始，运营才是价值");
    }

    public static void main(String[] args) {
        migrationJourney();
        lessons();
        steadyStateOps();
    }
}
```

**课后练习**
1. 为你的服务制定 Mesh 化路线图（三阶段 + 里程碑指标 + 踩坑预案），输出一份可评审的方案文档。
2. 建立策略目录模板（owner/用途/回退方式），盘点现有全部流量策略并登记。
3. 思考题：为什么 Mesh 化的验收标准是“混沌实验通过”而不是“功能正常”？从韧性验证与回归测试的层次差异分析。
