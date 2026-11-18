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
