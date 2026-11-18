### 48-06 容量规划与 QPS

**知识点**
- **推理容量的单位是 token/秒，不是 QPS**：QPS × 平均输出 token = 引擎吞吐需求——**两个 10 QPS 服务（输出 100 vs 2000 token）需要的算力差 20 倍**。容量规划的输入是 token 流量画像。
- **规划公式链**：`需求吞吐 = QPS × 平均输出 token`；`单卡吞吐 = 压测最优工作点吞吐`（48-03 曲线）；`所需卡数 = 需求吞吐 ÷ 单卡吞吐 × 冗余系数(1.5~2)`。**冗余给灰度/故障/峰值留余量**——与 42-05 错误预算同样的保守哲学。
- **峰值画像决定弹性策略**：白天高峰/夜间低谷（K8s 按时段扩缩）、大促尖峰（预留池）、工作日与周末差异——**按画像分时段容量，而不是按峰值常驻**（47-08 成本思想）。
- **排队论直觉**：利用率越高排队越长（M/M/c 的非线性）——**90% 利用率的推理服务 P99 必然爆炸**，生产目标利用率 60~70%。这是“压测 32 并发很好、上线 40 并发就崩”的原因。
- **多模型池的容量分配**：大模型池（复杂请求）、小模型池（分类）、本地池（合规）——按 44-10 路由后的流量分布分配容量，**路由变更会改容量需求**（联动的容量管理）。
- **扩容的滞后**：GPU 供给（云上申请分钟级、采购月级）——**容量水位告警前置**（峰值吞吐 70% 即告警），扩容演练提前于业务增长。
- 易错点：按 QPS 而非 token 估容量（差一个数量级）；单卡吞吐用官方跑分（自己的 prompt 长度/输出分布才是真实值）；目标利用率 90%+（排队延迟非线性爆炸）；容量告警晚于用户感知（水位 70% 不告警）。

**示例代码**
```java
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ServingCapacity {
    static void tokenVsQps() {
        System.out.println("【容量单位：token/秒 吞噬 QPS 直觉】");
        record Service(String name, int qps, int avgOut, long tokPerSec) {
        }
        List<Service> services = List.of(
                new Service("意图分类", 100, 20, 2_000),
                new Service("工单摘要", 20, 400, 8_000),
                new Service("营销文案", 5, 1500, 7_500));
        for (Service s : services) {
            System.out.printf("  %-8s QPS=%-4d 平均输出=%-5d -> %6d tok/s 需求%n",
                    s.name(), s.qps(), s.avgOut(), s.tokPerSec());
        }
        System.out.println("  20 QPS 的摘要服务比 100 QPS 的分类更吃算力 —— 按输出算，不按请求数");
        System.out.println();
    }

    static void capacityFormula() {
        System.out.println("【卡数规划公式链】");
        long demand = 30_000;                 // 集群 tok/s 需求（峰值）
        double perGpu = 900;                  // 压测最优工作点单卡吞吐
        double utilization = 0.65;            // 生产目标利用率（排队论）
        double redundancy = 1.3;              // 故障/灰度冗余
        double need = demand / (perGpu * utilization) * redundancy;
        System.out.printf("  需求 %d tok/s ÷ (单卡 %.0f × 利用率 %.2f) × 冗余 %.1f = %.0f 卡%n",
                demand, perGpu, utilization, redundancy, need);
        System.out.println("  三个人为参数都要有依据：利用率来自排队论（60~70%），冗余来自 SLO 承诺");
        System.out.println();
    }

    static void utilizationCliff() {
        System.out.println("【利用率悬崖：90% 的 P99 灾难】");
        record Load(double util, int queueDepth, double p99Factor) {
        }
        List<Load> loads = List.of(
                new Load(0.5, 1, 1.2),
                new Load(0.65, 2, 1.6),
                new Load(0.8, 5, 3.0),
                new Load(0.9, 12, 8.0),
                new Load(0.95, 30, 20.0));
        for (Load l : loads) {
            System.out.printf("  利用率 %.0f%% -> 排队深度≈%2d，P99 延迟放大 ×%.1f%n",
                    l.util() * 100, l.queueDepth(), l.p99Factor());
        }
        System.out.println("  排队论非线性：最后 10% 的利用率吃掉 6 倍延迟");
        System.out.println("  生产目标：峰值利用率 60~70%，水位 70% 告警");
        System.out.println();
    }

    static void capacityAlarm() {
        System.out.println("【容量水位告警（前置于用户感知）】");
        List<String> rules = List.of(
                "峰值时段吞吐达压测最优点的 70% -> 容量预警（扩容立项）",
                "网关排队深度持续 > 0 -> 当前副本不足",
                "GPU 利用率均值 > 75% 持续 30min -> 排查批配置或扩容",
                "路由流量分布变化（44-10 改路由）-> 重算各池容量");
        for (String r : rules) {
            System.out.println("  - " + r);
        }
        System.out.println("  联动：容量预警 -> 47-08 弹性（K8s 扩副本）或采购流程");
    }

    public static void main(String[] args) {
        tokenVsQps();
        capacityFormula();
        utilizationCliff();
        capacityAlarm();
    }
}
```

**课后练习**
1. 用真实流量画 token 需求画像（分时段），代入公式链算出所需卡数，并标注三个人为参数的依据。
2. 压测找出“利用率悬崖”：阶梯加压记录 P99 与利用率，标出你的服务应设的目标利用率。
3. 思考题：为什么 44-08 的路由变更会引发容量重算？构造一个“路由把流量从小模型切到大模型”导致大池容量告警的场景。

---

### 48-07 流式服务与连接管理

**知识点**
- **推理的流式连接是长连接**：一个请求占连接 5~60 秒（生成期间）——**连接数 ≈ 并发请求数**，容量账多一条“每副本最大并发流”（受引擎批上限约束，48-03）。
- **断流与续传**：客户端网络抖动断开 -> 引擎侧生成是否继续？**策略**：断开即取消（省算力，用户刷新重问）或服务端缓冲续传（SSE Last-Event-ID，40-04）——**按业务选择**：客服场景“断开即取消”更省成本，产品文案场景“缓冲续传”体验更好。
- **背压与取消的传播**：用户关闭页面 -> 客户端断流 -> 网关 -> 引擎取消该请求（释放 KV 与批槽位）——**取消链路必须打通**，否则“用户走了 GPU 还在算”（成本黑洞）。44-08 超时预算的终止传播。
- **SSE 生产的推理特有问题**：心跳与生成间隙（长思考阶段无 token 输出 -> 中间设备断连，40-04 的 ping 复用）；错误事件格式统一（引擎错误 -> 网关转译成业务错误事件）。
- **多模态/长上下文的连接画像**：输入大（上传图片/prompt 长）时 TTFT 长，连接更久——**连接超时与读超时要按画像配置**，不能套普通 API 的 30s。
- 易错点：取消不传播（幽灵计算烧钱）；中间设备断长连接（无心跳）；SSE 缓冲被网关开启（40-04 的坑在推理网关重现）；连接超时照抄普通服务配置（长生成被掐）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.List;

public class ServingStreaming {
    static void connectionMath() {
        System.out.println("【长连接的容量账】");
        double avgDurationSec = 15;
        int concurrentStreams = 32;
        System.out.printf("  单副本并发流 %d，平均时长 %.0fs -> 每秒新连接承载 ≈ %.1f 个%n",
                concurrentStreams, avgDurationSec, concurrentStreams / avgDurationSec);
        System.out.printf("  5000 tok/s 集群（平均输出 300）≈ %.0f 请求/秒 -> 需要 %.0f 个并发流槽位%n",
                5000 / 300.0, 5000 / 300.0 * avgDurationSec);
        System.out.println("  并发流槽位 = 引擎批上限（48-03 压测得出），网关排队长度与之匹配");
        System.out.println();
    }

    static void cancelPropagation() {
        System.out.println("【取消传播：用户走了，算力要停】");
        List<String> chain = List.of(
                "用户关闭页面 -> 客户端断开 SSE",
                "网关检测断连 -> 向引擎发送取消（abort 请求）",
                "引擎释放该请求的批槽位与 KV（PagedAttention 回收）",
                "计费只算已生成 token（44-08 归因）",
                "指标：cancelled_ratio —— 突增说明产品断流率高或网关误判");
        for (String s : chain) {
            System.out.println("  " + s);
        }
        System.out.println("  反例：取消链路缺失 -> 幽灵生成：用户已离开，GPU 继续算 30s");
        System.out.println();
    }

    static void keepalivePolicy() {
        System.out.println("【心跳与超时的推理特调】");
        String[][] rows = {
                {"SSE 心跳", "生成间隙（长思考/重试）发 : ping", "40-04 同款，防中间设备断连"},
                {"读超时", "网关 120s（按输出画像）", "普通 API 的 30s 会掐长生成"},
                {"空闲超时", "60s 无任何字节 -> 断开", "防挂死连接占槽位"},
                {"断开策略", "客服=取消即止；文案=服务端缓冲续传", "按业务成本与体验选择"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-8s %-30s %s%n", r[0], r[1], r[2]);
        }
        System.out.println();
    }

    static void sseGateway() {
        System.out.println("【推理网关的 SSE 转译职责】");
        List<String> duties = List.of(
                "协议转换：引擎事件 -> 统一业务事件（delta/usage/error）",
                "usage 尾包：本次请求 tokens_in/out（44-08 记账）",
                "错误转译：引擎 500/OOM -> 业务可理解的错误事件（40-02 分类）",
                "缓冲关闭：X-Accel-Buffering: no（40-04 陷阱在推理网关重现）",
                "Last-Event-ID 续传：按业务策略启用（40-04）");
        for (String d : duties) {
            System.out.println("  - " + d);
        }
        System.out.println("  网关是推理服务的“协议适配层 + 记账层”，与 44-08 AI 网关合一");
    }

    public static void main(String[] args) {
        connectionMath();
        cancelPropagation();
        keepalivePolicy();
        sseGateway();
    }
}
```

**课后练习**
1. 按你的流量画像计算并发流槽位需求，并与引擎批上限对齐，写出网关排队配置。
2. 实现取消传播闭环（模拟）：客户端断开 -> 网关 -> 引擎取消 -> 资源释放，验证“取消后不再计费”。
3. 思考题：为什么“断开即取消”与“缓冲续传”没有绝对正确答案？用客服与文案两个场景的成本/体验权衡说明。

---

### 48-08 高可用与多副本路由

**知识点**
- **推理集群的高可用形态**：多副本无状态（模型权重相同）-> 任意副本可服务；**有状态的例外**：KV 会话亲和（48-02 多轮复用）——亲和路由与负载均衡的矛盾要用“亲和 TTL + 超载时降级重算”平衡。
- **路由权重与真实容量对齐**：48-06 的各池容量不同 -> 网关按“剩余槽位/水位”加权（least-outstanding），**而不是轮询**——热副本不排队、冷副本不闲置（42 章负载均衡思想的推理版）。
- **故障语义**：副本 OOM/挂掉 -> 网关摘除 + 在途请求重试到其他副本（**幂等前提**：流式请求中断后重试要客户端可接受——40-04 断线策略）；引擎错误分类决定是否重试（402 上下文超限不可重试）。
- **优先级与抢占**：VIP/内部诊断请求可插队；过载时低优先级请求被抢占（48-04 引擎抢占）或网关直接拒绝（降级阶梯 44-10 的 L4）——**优先级队列是过载时的秩序**。
- **发布与回滚**：新引擎版本/新量化权重走灰度（48-05 验证门禁后）——**金丝雀副本**承接 5% 流量，指标对比后全量（41-07 思想的推理版）。
- **跨可用区部署**：模型权重多副本分属 AZ（GPU 供给与故障域），路由感知 AZ 延迟——**GPU 配额的跨区规划**（47-09 多集群的数据层思想在 GPU 池的应用）。
- 易错点：轮询路由无视水位（热副本排队爆炸）；会话亲和无限 TTL（KV 占满单副本）；OOM 副本未摘除（连续失败 5 分钟才摘）；灰度只比延迟不比质量（加速配置的质量回退上线）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.List;

public class ServingHA {
    record Replica(String name, int maxStreams, int inflight, double blockPct, boolean healthy) {
        double load() {
            return (double) inflight / maxStreams;
        }
    }

    static void weightedRouting() {
        System.out.println("【按水位加权：least-outstanding 路由】");
        List<Replica> replicas = List.of(
                new Replica("gpu-1", 32, 28, 80, true),
                new Replica("gpu-2", 32, 12, 45, true),
                new Replica("gpu-3", 32, 30, 88, true));
        for (Replica r : replicas) {
            System.out.printf("  %-6s 在途 %2d/%2d（%.0f%%） block池 %.0f%%%n",
                    r.name(), r.inflight(), r.maxStreams(), r.load() * 100, r.blockPct());
        }
        Replica best = replicas.stream()
                .filter(r -> r.healthy && r.blockPct < 85)
                .min((a, b) -> Double.compare(a.load(), b.load())).orElse(null);
        System.out.printf("  新请求 -> %s（最低水位且 block 池健康）%n", best.name());
        System.out.println("  对照轮询：轮到 gpu-3（88% 水位）-> 排队 + 抢占风险");
        System.out.println();
    }

    static void affinityVsBalance() {
        System.out.println("【会话亲和与负载均衡的平衡】");
        List<String> rules = List.of(
                "亲和 TTL：会话 KV 保留 10 分钟（TTL 内粘同副本）",
                "超载降级：亲和副本水位 > 90% -> 放弃亲和，换副本重算（体验优先）",
                "KV 优雅淘汰：TTL 到期回收（LRU），防单副本 KV 占满",
                "指标：affinity_hit_rate（低于 80% 说明容量不足或 TTL 不合理）");
        for (String r : rules) {
            System.out.println("  - " + r);
        }
        System.out.println();
    }

    static void failureSemantics() {
        System.out.println("【副本故障的分级处理】");
        String[][] rows = {
                {"网络抖动/单请求 5xx", "重试其他副本（流式请求按断线策略）", "自动，秒级"},
                {"副本 OOM 反复", "摘除副本 + 告警 + 触发重建", "分钟级"},
                {"模型加载失败（发布期）", "金丝雀副本隔离，全量留在旧版本", "发布门禁拦截"},
                {"引擎全局错误（配置坏）", "降级路由切备用池（44-08 L2）", "自动+告警"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-20s %-34s %s%n", r[0], r[1], r[2]);
        }
        System.out.println("  幂等前提：流式中断重试要客户端可接受（40-04 断线策略声明）");
        System.out.println();
    }

    static void canaryReplica() {
        System.out.println("【新引擎/新权重的金丝雀发布】");
        List<String> steps = List.of(
                "1) 新版本独立副本（不接流量），离线评测门禁（48-05）",
                "2) 切 5% 流量：TTFT/TPOT/质量抽检对比基线",
                "3) 指标达标 -> 25% -> 100%（41-07 放量节奏）",
                "4) 异常 -> 副本级回滚（秒级，权重归零）",
                "5) 全量后旧版本保留 24h 作回滚池");
        for (String s : steps) {
            System.out.println("  " + s);
        }
        System.out.println("  与 41 章发布工程的唯一差异：验证指标多了 TTFT/TPOT 与质量抽检");
    }

    public static void main(String[] args) {
        weightedRouting();
        affinityVsBalance();
        failureSemantics();
        canaryReplica();
    }
}
```

**课后练习**
1. 实现水位加权路由器（模拟副本与请求流），对比轮询与 least-outstanding 的 P99 与排队深度。
2. 设计会话亲和策略（TTL/超载降级/淘汰），构造单副本过载场景验证降级路径。
3. 思考题：为什么推理金丝雀的验证指标必须包含“质量抽检”而普通服务不用？从加速配置对输出质量的影响（48-05）推导。

---

### 48-09 成本工程

**知识点**
- **推理成本的构成**：GPU 时长成本（常驻 + 峰值扩容）+ token 计费成本（云端 API）+ 存储与网络（权重分发、出网）。**优化杠杆按大到小：路由降级（44-08）> 缓存复用 > 输出长度控制 > 引擎加速（48-05）> 采购议价**。
- **输出长度是成本的第一变量**：回答越长成本越高——**控制手段**：max_tokens 上限、Prompt 明确“简短回答”、结构化输出（JSON 更短）、按意图配置不同 max_tokens（分类 50 token、摘要 400 token）。**max_tokens 忘配是常见成本事故**。
- **缓存复用的三层**：语义缓存（40-10）、前缀 KV 共享（48-02）、模板结果缓存（FAQ 固定话术）——**三层命中率加起来通常节省 20~40%**。
- **批处理任务走离线池**：离线评测、数据回填、批量摘要——用 Spot/低优先级实例（47-08），**与在线池物理隔离**（互不抢占）。
- **成本归因与预算**：44-08 网关记账（业务方/意图维度）+ 月度预算与超额告警——**成本报表进业务方视野**（谁消耗谁负责），优化动作可追踪。
- **蒸馏降本的长线**：44-06 用大模型造数据微调小模型替代——**在线成本下降一个数量级**，但质量门禁不可省。
- 易错点：成本只算总量不归因（不知道谁烧的）；max_tokens 缺省（失控输出）；缓存阈值过松（错误答案扩散，40-10 教训）；离线任务挤占在线池（大促当天评测任务把线上打挂）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class InferenceCost {
    static void levers() {
        System.out.println("【成本杠杆：按收益排序】");
        record Lever(String name, String action, String saving) {
        }
        List<Lever> levers = List.of(
                new Lever("路由降级", "分类/粗活走小模型（44-08）", "-60~80% 单次成本"),
                new Lever("缓存三层", "语义缓存+前缀共享+模板缓存", "-20~40% 总量"),
                new Lever("输出控制", "max_tokens + 简短指令 + 结构化", "-30% 输出长度"),
                new Lever("引擎加速", "quantization + batching（48-05）", "吞吐翻倍=单位成本减半"),
                new Lever("蒸馏替换", "小模型替代大模型（44-06）", "长线数量级下降"));
        for (Lever l : levers) {
            System.out.printf("  %-8s %-30s %s%n", l.name(), l.action(), l.saving());
        }
        System.out.println();
    }

    static void maxTokensIncident() {
        System.out.println("【max_tokens 缺省事故：一句话回答变千字文】");
        record Intent(String name, int properMax, int missingMax, double costFactor) {
        }
        List<Intent> intents = List.of(
                new Intent("意图分类", 50, 4096, 8.0),
                new Intent("订单查询话术", 120, 4096, 4.0),
                new Intent("营销文案", 1500, 4096, 1.0));
        for (Intent i : intents) {
            System.out.printf("  %-10s 合理上限=%-5d 缺省=%-5d 成本放大 ×%.1f%n",
                    i.name(), i.properMax, i.missingMax, i.costFactor);
        }
        System.out.println("  修复：按意图配置 max_tokens + Prompt 写明“只输出 JSON”");
        System.out.println("  监控：输出长度分布突变（P50 翻倍）-> 配置回归告警");
        System.out.println();
    }

    static void costAttribution() {
        System.out.println("【归因报表：谁在烧钱】");
        record Row(String biz, String intent, long tokens, double cost, String action) {
        }
        List<Row> rows = List.of(
                new Row("客服", "复杂问答", 42_000_000, 1680.0, "路由 30% 到本地池"),
                new Row("客服", "意图分类", 8_000_000, 32.0, "已优化"),
                new Row("运营", "周报生成", 6_000_000, 240.0, "输出控制 -30%"),
                new Row("数据", "离线评测", 5_000_000, 0.0, "迁离线 Spot 池"));
        for (Row r : rows) {
            System.out.printf("  %-4s %-8s %10d tok  %8.0f 元  动作：%s%n",
                    r.biz(), r.intent(), r.tokens(), r.cost(), r.action());
        }
        System.out.println("  月度例会：Top3 成本项必有优化动作与目标值（41-10 DORA 的成本版）");
        System.out.println();
    }

    static void offlinePool() {
        System.out.println("【离线批任务与在线池隔离】");
        List<String> rules = List.of(
                "物理隔离：离线评测/回填用独立 Spot 节点池（47-08）",
                "优先级：在线请求永远 > 离线任务（抢占方向单向）",
                "错峰：离线任务调度到夜间低谷",
                "限额：离线池消耗上限，防止失控批量任务烧穿预算");
        for (String r : rules) {
            System.out.println("  - " + r);
        }
        System.out.println("  事故对照：评测任务与在线同池 -> 大促当天被打挂");
    }

    public static void main(String[] args) {
        levers();
        maxTokensIncident();
        costAttribution();
        offlinePool();
    }
}
```

**课后练习**
1. 给你的 AI 功能做成本归因报表（按业务方/意图），找出 Top1 成本项并落地一个优化杠杆，量化前后差异。
2. 排查 max_tokens 配置：列出全部意图的合理上限与当前配置，找出缺失或过大的项并修复。
3. 思考题：为什么“路由降级”是收益最大的成本杠杆？从流量分布（分类类流量占比）与单价差的角度量化。

---

### 48-10 推理可观测

**知识点**
- **推理服务的 SLI 多于普通服务**：TTFT、TPOT、队列深度、批利用率、block 水位、prefill/decode 时间分解——**42 章四信号框架的推理特化**。核心原则不变：告警挂在用户体验（TTFT/错误率），排障看内部（水位/分解）。
- **延迟分解是排障的第一刀**：总延迟 = 排队 + prefill + decode + 网络传输——**每段分别打点**：排队涨 = 容量不足；prefill 涨 = 长输入/干扰；decode 涨 = 批过大/带宽瓶颈。**不做分解就只能猜**。
- **与输入特征联动**：TTFT 与输入长度强相关、总延迟与输出长度强相关——**按输入/输出长度分桶看指标**（42-04 维度设计的推理版），否则长尾被平均掩盖。
- **质量观测与性能观测并行**：加速配置变更后**质量抽检率**与性能指标同屏（48-05 双门）；点踩率与 TTFT 异常的时间相关性排查。
- **42 章方法论的全套复用**：错误预算（TTFT SLO）、告警降噪（聚合同一副本故障）、链路追踪（网关->引擎的 trace）、容量水位告警（48-06）——**本书观测体系在推理服务的一比一移植**，只是指标语义换成了生成过程。
- 易错点：只监控总延迟不分解（无法定位瓶颈段）；不分桶看分位数（长请求污染 P99）；质量与性能割裂观测（加速变更后只看性能）；引擎内部指标不暴露（block/批利用率黑盒）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.List;

public class InferenceObservability {
    record Segment(String name, String signal, String highMeans) {
    }

    static void latencyDecomposition() {
        System.out.println("【延迟分解：总延迟 = 排队+prefill+decode+网络】");
        List<Segment> segs = List.of(
                new Segment("排队", "网关等待时间", "容量不足（48-06 扩容）"),
                new Segment("prefill", "输入编码时间", "输入过长/长 prefill 干扰（48-03）"),
                new Segment("decode", "TPOT × 输出长度", "批过大/带宽瓶颈（48-03/05）"),
                new Segment("网络", "传输与网关开销", "SSE 缓冲/中间设备（40-04）"));
        for (Segment s : segs) {
            System.out.printf("  %-6s 信号=%-18s 偏高=%s%n", s.name(), s.signal(), s.highMeans());
        }
        List<String> sample = List.of(
                "异常请求样本：排队 40ms + prefill 300ms + decode 2400ms + 网络 60ms",
                "结论：decode 占 86% -> 长输出 + TPOT 偏高 -> 查批配置或加速项");
        for (String s : sample) {
            System.out.println("  " + s);
        }
        System.out.println();
    }

    static void bucketByLength() {
        System.out.println("【按输入/输出长度分桶：消除长尾污染】");
        String[][] buckets = {
                {"短入短出（<500/300）", "TTFT 380ms / 总 12s", "交互对话主场景"},
                {"长入短出（8K/300）", "TTFT 1.9s / 总 13s", "RAG 大文档场景，TTFT 单独 SLO"},
                {"短入长出（500/2K）", "TTFT 350ms / 总 62s", "文案场景，总时长单独承诺"},
        };
        for (String[] b : buckets) {
            System.out.printf("  %-22s %s%n", b[0], b[1]);
        }
        System.out.println("  统一 SLO 会把三个场景搅在一起 —— 分桶后各自达标才叫达标");
        System.out.println();
    }

    static void qualityPerfTogether() {
        System.out.println("【质量与性能同屏：加速变更的看板】");
        record Change(String config, String tps, String tpot, String quality, String verdict) {
        }
        List<Change> changes = List.of(
                new Change("基线 FP16", "900 tok/s", "30ms", "94.2%", "基准"),
                new Change("+FP8 量化", "1650 tok/s", "31ms", "93.8%", "PASS：吞吐+83% 质量持平"),
                new Change("+投机解码", "2100 tok/s", "28ms", "88.5%", "FAIL：质量门未过 -> 关闭"));
        for (Change c : changes) {
            System.out.printf("  %-12s 吞吐=%-10s TPOT=%-6s 质量=%-7s %s%n",
                    c.config(), c.tps(), c.tpot(), c.quality(), c.verdict());
        }
        System.out.println("  48-05 双门的仪表化：变更前后同屏对比，一票否决");
        System.out.println();
    }

    static void observabilityChecklist() {
        System.out.println("【推理服务观测清单（42 章清单的特化）】");
        List<String> items = List.of(
                "TTFT/TPOT 分桶分位（输入长度 × 输出长度）",
                "排队深度与排队时长（网关层）",
                "引擎内部：批利用率、block 水位、抢占次数",
                "取消率与幽灵计算检测（48-07）",
                "质量抽检率 + 点踩率（与性能同屏）",
                "成本指标：tokens_in/out、每千次会话成本（48-09）");
        for (String i : items) {
            System.out.println("  [ ] " + i);
        }
        System.out.println("  告警挂用户侧（TTFT/错误率），排障看内部（水位/分解）—— 42-05 原则");
    }

    public static void main(String[] args) {
        latencyDecomposition();
        bucketByLength();
        qualityPerfTogether();
        observabilityChecklist();
    }
}
```

**课后练习**
1. 给你的推理服务补齐延迟分解打点（排队/prefill/decode），用一次慢请求样本做归因。
2. 建立分桶看板（输入×输出长度），对比统一 SLO 与分桶 SLO 的达标判定差异。
3. 思考题：为什么“质量抽检与性能同屏”是加速变更看板的必备设计？从 48-05 双门与 44-10 评测门禁的闭环关系分析。

---

### 48-11 综合案例：推理集群的容量与降级演练

**知识点**
- **把本章收束成一次完整演练**：大促预告流量 ×3 -> 容量核算（48-06 公式链）-> 扩容与压测验证（48-03 曲线）-> 降级预案（44-08 阶梯：切小模型/本地池/静态话术）-> 混沌注入验证（47-10：单副本 OOM、网关故障、依赖 RAG 的检索超时）-> 复盘改进。**40/44 章的 AI 应用与本章的推理底座在此合流**。
- **演练时间线**：T-7 天容量核算与扩容申请 -> T-3 天压测定批上限 -> T-1 天降级预案演练 -> T0 大促值守（水位看板 + 自动判据）-> T+1 复盘。**容量是预案，演练才使预案可信**。
- **降级阶梯的推理版**：L1 大模型全量 -> L2 大模型限批（降吞吐保核心）-> L3 小模型接管全量 -> L4 本地池/静态话术——**每级都预先压测过**（L3 的质量影响要有评测数据支撑）。
- **故障注入清单**：单副本 OOM（验证摘除与水位路由）、控制面/网关故障（验证降级路由）、RAG 检索超时（验证 L4 兜底与 TTFT 表现）、依赖知识库更新任务挤占（验证离线池隔离 48-09）。
- **复盘产出**：容量公式参数修正（压测修正单卡吞吐）、降级触发阈值修正、新增监控项（48-10 清单）、成本预估更新（48-09）。**一次演练让容量-降级-观测-成本四条线同时进化**。
- 易错点：扩容只加卡不压测（批上限没调，新卡跑不满）；降级预案没压测（L3 小模型质量未知）；演练只测吞吐不测质量（降级后回答质量崩了没人知道）；复盘不更新公式参数（下次容量核算还在用旧数据）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class InferenceCase {
    record DrillStep(int day, String action, String gate, String result) {
    }

    static void drillTimeline() {
        System.out.println("【大促推理保障演练：流量 ×3 预告】");
        List<DrillStep> steps = List.of(
                new DrillStep(-7, "容量核算：峰值 token 需求 ×3 -> 卡数重算", "48-06 公式链",
                        "12 卡 -> 18 卡，扩容申请通过"),
                new DrillStep(-3, "压测新副本：确定批上限与最优工作点", "TPOT<50ms",
                        "批上限 40，单卡吞吐 1100 tok/s"),
                new DrillStep(-1, "降级演练：注入主池故障 -> 切小模型 -> 质量抽检", "质量门+性能门",
                        "L3 接管 8 分钟，抽检通过率 91%（可接受）"),
                new DrillStep(-1, "混沌注入：单副本 OOM / RAG 超时", "摘除<60s、L4 兜底生效",
                        "全部达标，发现 RAG 超时兜底话术缺失 -> 补充"),
                new DrillStep(0, "大促值守：水位看板 + 自动判据", "峰值利用率<70%",
                        "峰值 66%，TTFT P95 720ms 达标"));
        for (DrillStep s : steps) {
            System.out.printf("  T%+dd %s%n      门禁：%s -> %s%n", s.day(), s.action(), s.gate(), s.result());
        }
        System.out.println();
    }

    static void degradeLadder() {
        System.out.println("【推理降级阶梯（44-08 的推理特化）】");
        String[][] rows = {
                {"L1 大模型全量", "正常态", "TTFT<800ms SLO"},
                {"L2 大模型限批", "过载：降低批上限保核心意图", "吞吐降 30%，核心意图优先"},
                {"L3 小模型接管", "主池不可用/严重过载", "质量抽检 91%（预先评测）"},
                {"L4 本地池+静态话术", "云端全断", "FAQ 命中率 78%，兜底话术"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-16s %-26s %s%n", r[0], r[1], r[2]);
        }
        System.out.println("  每级都压测过 + 质量评测过 —— 44-10 的门禁在降级路径同样生效");
        System.out.println();
    }

    static void retroUpdates() {
        System.out.println("【复盘产出：四个参数的修正】");
        Map<String, String> updates = new LinkedHashMap<>();
        updates.put("容量公式", "单卡吞吐 900 -> 1100（批上限调优后压测值）");
        updates.put("降级阈值", "主池错误率 8% -> 5% 触发 L3（缩短用户受损窗口）");
        updates.put("监控清单", "新增 RAG 检索超时独立告警（演练发现的盲区）");
        updates.put("成本预估", "峰值日成本上浮 22%（扩容+冗余），进预算");
        for (Map.Entry<String, String> e : updates.entrySet()) {
            System.out.printf("  %-6s %s%n", e.getKey(), e.getValue());
        }
        System.out.println("  闭环：参数更新 -> 下次演练验证 —— 预案的生命力来自迭代");
        System.out.println();
    }

    static void closingChecklist() {
        System.out.println("【推理平台上线总检查单（全书 AI 线收口）】");
        List<String> items = List.of(
                "容量：token 画像 + 公式链 + 水位告警（48-06）",
                "性能：压测曲线定批上限，加速项过双门（48-03/05）",
                "可用：水位路由 + 亲和策略 + 故障分级（48-08）",
                "成本：归因报表 + 输出控制 + 离线池隔离（48-09）",
                "观测：延迟分解 + 分桶看板 + 质量同屏（48-10）",
                "工程：网关排队 + 取消传播 + SSE 特调（48-07）",
                "演练：容量/降级/混沌全流程季度化（本章）",
                "上层：Prompt/RAG/Agent 依赖本底座（40/44 章）");
        for (String i : items) {
            System.out.println("  [ ] " + i);
        }
        System.out.println("  全书至此：从字面量到推理集群，44+8 章形成完整闭环");
    }

    public static void main(String[] args) {
        drillTimeline();
        degradeLadder();
        retroUpdates();
        closingChecklist();
    }
}
```

**课后练习**
1. 为你的推理服务策划一次完整演练（容量核算->压测->降级->混沌->复盘），输出演练报告。
2. 实现降级阶梯的自动触发（错误率/水位判据），注入主池故障验证 L2/L3/L4 的切换时序与质量抽检。
3. 思考题：为什么“复盘必须更新容量公式与降级阈值”？从预案可信度（演练出真值）与组织记忆（参数留存）两个角度分析。
