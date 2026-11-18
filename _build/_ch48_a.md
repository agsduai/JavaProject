### 48-01 推理与训练的分界

**知识点**
- **训练与推理是两种完全不同的负载**：训练（几天离线，追求吞吐，梯度计算，需要全量数据）vs 推理（7×24 在线，追求延迟与并发，只做前向计算）。**Java 工程师日常面对的是推理服务**——它是“一个特殊的常驻服务”，可以套用全书的服务工程方法论（容量、降级、观测），但多了 GPU 与生成过程的特殊性。
- **自回归生成的两阶段**：**prefill（预填充）**——把全部输入 prompt 一次性算完（并行，算力密集）；**decode（解码）**——逐 token 生成，每步只算一个 token（访存密集，受显存带宽限制）。**两阶段的资源画像完全不同**：prefill 吃算力，decode 吃带宽——这是推理优化的主线（48-03 的 batching 复杂性来源）。
- **延迟的两个指标**：**TTFT**（首 token 时间，主要由 prefill 决定，用户感知“多快开始回答”）与 **TPOT/ITL**（每 token 时间，用户感知“打字机速度”）。**SLO 要分开承诺**：TTFT < 800ms、TPOT < 50ms 是典型目标。
- **推理服务与传统服务的差异点**：GPU 是稀缺且单价的（容量规划按卡算）；请求处理时间与输出长度成正比（不可预知，排队模型复杂）；批处理能大幅提升吞吐但延迟随之波动（48-03）。
- **Java 的位置**：推理引擎本体（vLLM/TensorRT-LLM）是 Python/C++，但**服务化层全是 Java 的领地**：网关路由（44-08）、排队与限流、会话管理、可观测、成本归因——48 章的服务端工程都在 Java 侧。
- **按输出长度规划容量**：平均输出 300 token 的请求处理时间 ≈ 300 × TPOT + TTFT——**输出长度分布是容量计算的输入**，必须在真实流量中测量而不是拍脑袋。
- 易错点：把推理请求当普通 HTTP 请求做容量（忽略输出长度方差导致长尾爆炸）；TTFT 与总延迟混为一谈（SLO 无法落地）；忽略 prefill/decode 的资源差异（批配置盲目）；认为加机器线性扩吞吐（GPU 利用率与 batching 才是第一杠杆）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.List;

public class InferenceBasics {
    record Phase(String name, String work, String bottleneck, String metric) {
    }

    static void twoPhases() {
        System.out.println("【自回归生成：prefill 与 decode】");
        List<Phase> phases = List.of(
                new Phase("prefill", "一次算完整个输入 prompt（并行矩阵乘）", "GPU 算力", "决定 TTFT"),
                new Phase("decode", "每步只算 1 个新 token（循环 N 次）", "显存带宽（每步读全部权重/KV）", "决定 TPOT"));
        for (Phase p : phases) {
            System.out.printf("  %-7s %-32s 瓶颈=%-12s %s%n",
                    p.name(), p.work(), p.bottleneck(), p.metric());
        }
        System.out.println("  直觉：输入 2000 token 是“一口气”，输出 300 token 是“300 小步”");
        System.out.println();
    }

    static void latencyMath() {
        System.out.println("【单请求延迟估算（容量规划的原子公式）】");
        double ttft = 350;          // prefill 时间，随输入长度增长
        double tpot = 30;           // 每-token 时间
        int[] outputs = {100, 300, 1000};
        for (int out : outputs) {
            double total = ttft + out * tpot;
            System.out.printf("  输出 %4d token -> 总延迟 %5.0fms（TTFT %.0f + %d×%.0f）%n",
                    out, total, ttft, out, tpot);
        }
        System.out.println("  关键：输出长度方差决定延迟方差 —— 长尾来自“话痨回答”");
        System.out.println("  容量输入：真实流量的输出长度分布（P50/P95），不是平均值");
        System.out.println();
    }

    static void sloSplit() {
        System.out.println("【SLO 拆分：TTFT 与 TPOT 分开承诺】");
        String[][] rows = {
                {"TTFT", "< 800ms（P95）", "用户感知“开始响应”的速度", "受 prefill 与排队影响"},
                {"TPOT", "< 50ms（P95）", "打字机流畅度", "受 decode 带宽与批干扰影响"},
                {"总时长", "按输出长度分桶承诺", "短答/长答分开看", "混看会被长请求污染"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-7s %-20s %-18s %s%n", r[0], r[1], r[2], r[3]);
        }
        System.out.println("  对照 42-05：推理服务的 SLI 天然多一个维度（输出长度）");
        System.out.println();
    }

    static void serviceLayers() {
        System.out.println("【推理服务的分层：Java 在哪一层】");
        List<String> layers = List.of(
                "GPU/内核层：CUDA、显存、NVLink（平台层）",
                "推理引擎层：vLLM/TensorRT-LLM（continuous batching、KV 管理）",
                "服务层：网关路由、排队限流、会话、降级 —— Java 领地",
                "业务层：Prompt 组装、RAG、工具（40/44 章）");
        for (String l : layers) {
            System.out.println("  " + l);
        }
        System.out.println("  分界清晰：引擎调参是平台组的事，服务工程是 Java 的事");
    }

    public static void main(String[] args) {
        twoPhases();
        latencyMath();
        sloSplit();
        serviceLayers();
    }
}
```

**课后练习**
1. 用真实流量（或模拟）统计输出长度分布（P50/P95/P99），按本章公式估算三种分位的请求延迟。
2. 为你的推理服务写 SLO：TTFT/TPOT/总时长分桶承诺，并说明每项的测量方法。
3. 思考题：为什么 decode 阶段是“访存密集”而不是“算力密集”？从每步要读全部权重与 KV 的角度推导 batching 能省什么。

---

### 48-02 KV Cache 原理

**知识点**
- **为什么需要 KV Cache**：decode 每生成一个 token 都要对“整段上文”做注意力计算——不缓存的话每步都重算全部历史的 K/V 投影。**KV Cache 把已算过的 Key/Value 存下来**，每步只算新 token 的 Q/K/V：生成从 O(n²) 重复计算变成 O(n) 线性追加。
- **显存账是推理的容量核心**：KV Cache 大小 = 2(K和V) × 层数 × KV头数 × 头维度 × 精度字节数 × 序列长度 × 并发数。**7B 模型 8K 上下文单请求的 KV 可达 GB 级**——显存大头不是权重而是 KV（长上下文时）。
- **上下文长度与并发是显存的两面**：单请求超长上下文（128K）会吃光 KV 空间；高并发会让 KV 争抢显存触发驱逐/重算。**容量规划 = 权重 + KV（并发×长度）+ 激活余量**（44-05 的显存预算在推理服务的精确版）。
- **省 KV 的技术线**：**GQA/MQA**（KV 头数比注意力头少，KV 直接除以组数）、**量化 KV**（FP8/INT8 存储）、**滑动窗口/稀疏注意力**（只留局部窗口）、**前缀共享**（相同 system prompt 只存一份，多请求共享——44-03 记忆与 40-08 RAG 的系统提示前缀是天然受益者）。
- **KV 与会话管理的关系**：多轮对话把历史 KV 保留可跳过 prefill（缓存命中，TTFT 直降）——**会话黏住同一实例**才能复用（48-08 路由的亲和性需求）。
- 易错点：显存预算只算权重（长上下文必 OOM）；并发压测不带上真实上下文长度（KV 差几个数量级）；会话不粘实例（每轮全量 prefill，TTFT 与成本双爆炸）；长前缀重复计算（未用前缀共享）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.List;

public class KvCache {
    /** KV 显存估算（字节）：2×layers×kvHeads×headDim×bytes×seqLen */
    static double kvBytes(int layers, int kvHeads, int headDim, int bytesPer, int seqLen) {
        return 2.0 * layers * kvHeads * headDim * (double) bytesPer * seqLen;
    }

    static void memoryMath() {
        System.out.println("【KV 显存账：7B 模型（32 层，GQA 8 头，headDim=128）】");
        int layers = 32, kvHeads = 8, headDim = 128;
        List<int[]> cases = List.of(
                new int[]{2, 2048},     // FP16, 2K 上下文
                new int[]{2, 32768},    // FP16, 32K
                new int[]{1, 32768});   // FP8, 32K
        String[] labels = {"FP16 2K", "FP16 32K", "FP8 32K"};
        for (int i = 0; i < cases.size(); i++) {
            int[] c = cases.get(i);
            double per = kvBytes(layers, kvHeads, headDim, c[0], c[1]);
            System.out.printf("  %-9s 单请求 KV ≈ %.2f GB；32 并发 ≈ %.0f GB%n",
                    labels[i], per / 1e9, per * 32 / 1e9);
        }
        System.out.println("  对照：7B 权重 FP16 ≈ 14GB —— 长上下文时 KV 反超权重");
        System.out.println("  结论：并发 × 上下文长度 是显存的两条乘数腿");
        System.out.println();
    }

    static void gqaBenefit() {
        System.out.println("【GQA：KV 头分组，缓存直接除以组数】");
        int heads = 32, kvHeadsMha = 32, kvHeadsGqa = 8;
        System.out.printf("  MHA：KV 头 = %d（与注意力头相同）%n", kvHeadsMha);
        System.out.printf("  GQA：KV 头 = %d（32 头共享 8 组 KV）-> KV 显存 /%.0f%n",
                kvHeadsGqa, (double) kvHeadsMha / kvHeadsGqa);
        System.out.println("  质量损失极小（现代主流模型标配），容量收益 4 倍");
        System.out.println();
    }

    static void prefixSharing() {
        System.out.println("【前缀共享：相同 system prompt 只算一次】");
        String sysPrompt = "system: 你是客服助手…（2200 token 的长系统提示）";
        int sessions = 50;
        System.out.println("  场景：" + sessions + " 个并发会话共用同一 system prompt");
        double perPrefill = 2200 * 30 / 1000.0;   // 粗略 ms，演示用
        System.out.printf("  不共享：每会话 prefill %.0fms × %d = %.0fms 总算力%n",
                perPrefill, sessions, perPrefill * sessions);
        System.out.println("  共享：首个会话 prefill 后，其余命中 KV 复用 -> TTFT 降到毫秒级");
        System.out.println("  受益场景：RAG 固定指令段（40-08）、固定角色 prompt（40-03）");
        System.out.println();
    }

    static void sessionAffinity() {
        System.out.println("【多轮对话的 KV 复用需要会话黏性】");
        List<String> rounds = List.of(
                "轮1：输入 2000 token -> prefill 全量，KV 保留在本实例",
                "轮2（同实例）：只 prefill 新增 300 token -> TTFT 低",
                "轮2（异实例）：重新 prefill 全部 2300 token -> TTFT 高 + 成本高");
        for (String r : rounds) {
            System.out.println("  " + r);
        }
        System.out.println("  路由需求：sessionId 粘住同一副本（48-08），KV 淘汰要有 TTL");
    }

    public static void main(String[] args) {
        memoryMath();
        gqaBenefit();
        prefixSharing();
        sessionAffinity();
    }
}
```

**课后练习**
1. 用你的模型参数（层数/头数/精度）计算 8K/32K 上下文的 KV 显存账，并得出最大并发数。
2. 实测前缀共享收益：固定长 system prompt + 50 个并发短问题，对比开启/关闭 prefix caching 的 TTFT。
3. 思考题：为什么多轮对话“换实例重算”比“黏实例复用”贵得多？用 prefill 成本与 TTFT 两个角度量化。

---

### 48-03 Continuous Batching

**知识点**
- **为什么 batching 是吞吐的第一杠杆**：decode 是访存密集——单请求每步读全部权重只产出 1 个 token，GPU 利用率极低；**把多个请求的 decode 步骤拼在一起**，一次读权重产出 N 个 token——**权重读取成本被 N 摊薄**，吞吐近似线性提升（直到算力饱和）。
- **static batching 的浪费**：传统批把一组请求凑满一起算、一起结束——**短请求等长请求**（批内最长的决定整批时间），GPU 大量空转。**continuous batching**：每步迭代动态进出——完成的请求立刻退出、新请求立刻加入，GPU 永远满负荷。
- **吞吐与延迟的交换曲线**：批越大吞吐越高、TPOT 越差（每步算更多请求）。**批次上限是 SLO 旋钮**：TPOT < 50ms 的约束反解出最大并发批（受显存与算力双重限制）。压测画出“并发-吞吐-TPOT”曲线是容量规划的核心实验。
- **prefill 与 decode 的干扰**：一个长 prefill（2 万 token 输入）会把同批所有 decode 卡住（TTFT/ITL 尖刺）——**prefill 与 decode 分离调度**（分批/分实例）是 vLLM 新版与专用推理集群的方向。
- **Java 侧的配合**：网关的排队深度与引擎的并发上限要匹配——**在引擎前排队（网关）而不是让引擎内部无限排队**（44-08 超时预算 + 42 章限流的推理版）。
- 易错点：用 static batching 思维估吞吐（实际 continuous 提升数倍）；批上限拍脑袋（压测曲线说话）；长 prefill 与 decode 混跑（ITL 尖刺归因错误）；网关不限流直接打引擎（队列积压在引擎内，TTFT 失控）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.List;

public class ContinuousBatching {
    static void staticWaste() {
        System.out.println("【static batching 的空转：批内最长决定一切】");
        record Req(String id, int outTokens) {
        }
        List<Req> batch = List.of(
                new Req("r1", 40), new Req("r2", 380), new Req("r3", 120), new Req("r4", 50));
        int maxOut = batch.stream().mapToInt(Req::outTokens).max().orElse(0);
        int wasted = 0;
        for (Req r : batch) {
            wasted += maxOut - r.outTokens();
            System.out.printf("  %s 输出 %3d token -> 空转 %3d 步%n", r.id(), r.outTokens(),
                    maxOut - r.outTokens());
        }
        System.out.printf("  批结束于最长者 %d token；累计空转 %d 步（GPU 白跑）%n", maxOut, wasted);
        System.out.println("  continuous batching：r1 完成即退出，槽位立刻给新请求");
        System.out.println();
    }

    static void throughputCurve() {
        System.out.println("【并发-吞吐-TPOT 的压测曲线（示意）】");
        record Point(int concurrency, double tokPerSec, double tpotMs) {
        }
        List<Point> points = List.of(
                new Point(1, 45, 22), new Point(8, 320, 25), new Point(16, 580, 28),
                new Point(32, 820, 39), new Point(64, 880, 73), new Point(128, 870, 148));
        for (Point p : points) {
            String flag = p.tpotMs() > 50 ? "  <- 超出 TPOT SLO" : "";
            System.out.printf("  并发%4d 吞吐%6.0f tok/s  TPOT=%3.0fms%s%n",
                    p.concurrency(), p.tokPerSec(), p.tpotMs(), flag);
        }
        System.out.println("  最优工作点：吞吐增长放缓且 TPOT 触线的位置（本例 ≈ 32~48 并发）");
        System.out.println("  旋钮：max_num_seqs（引擎批上限）+ 网关排队深度");
        System.out.println();
    }

    static void prefillInterference() {
        System.out.println("【长 prefill 干扰 decode 的尖刺】");
        List<String> events = List.of(
                "10:00:01 批内 32 个请求正常 decode，TPOT 稳定 30ms",
                "10:00:03 新请求 A 进入：prompt 20000 token -> prefill 占用整批 1.8s",
                "10:00:03~05 同批 decode 全部停顿 -> ITL 尖刺 1800ms（用户感知卡顿）",
                "对策：prefill 分批上限（chunked prefill）/ 专用 prefill 实例");
        for (String e : events) {
            System.out.println("  " + e);
        }
        System.out.println("  归因提示：ITL 尖刺与“长输入请求到达”时间对齐 -> prefill 干扰");
        System.out.println();
    }

    static void gatewayCoop() {
        System.out.println("【网关与引擎的排队分工】");
        List<String> rules = List.of(
                "引擎只暴露安全并发上限（压测确定，如 32）",
                "网关在引擎前排队：FIFO + 按优先级（VIP 请求插队）",
                "排队超时 > 网关超时预算（44-08）：等不到就降级（切小模型/本地）",
                "排队深度暴露为指标（42 章）：depth 持续增长 = 容量不足信号");
        for (String r : rules) {
            System.out.println("  - " + r);
        }
        System.out.println("  反例：网关不限流，引擎内部排队 -> TTFT 无界增长，SLO 全灭");
    }

    public static void main(String[] args) {
        staticWaste();
        throughputCurve();
        prefillInterference();
        gatewayCoop();
    }
}
```

**课后练习**
1. 压测你的推理服务（或用模拟数据），画出“并发-吞吐-TPOT”曲线，标出满足 TPOT<50ms 的最大并发与对应吞吐。
2. 模拟 static 与 continuous batching：给定一批不同输出长度的请求，对比总完成时间与 GPU 空转步数。
3. 思考题：为什么“排队放在网关而不是引擎”能保住 TTFT？从排队策略灵活性（优先级/降级）与引擎批上限的关系分析。

---

### 48-04 PagedAttention 与显存管理

**知识点**
- **传统 KV 管理的浪费**：每个请求预分配“最大长度”的连续 KV 空间（如按 2048 预留）——**实际输出长短不一，平均浪费 60%~80%**；连续内存还有外部碎片。vLLM 的 PagedAttention 把操作系统分页思想搬进 KV 管理。
- **分页机制**：KV 空间切成固定大小的 block（如 16 token/块），按需分配、逻辑连续物理分散——**显存利用率从 ~40% 提到 90%+**，同卡并发数翻倍以上。
- **Copy-on-Write 与前缀共享的配合**：多个请求共享相同前缀的 block（只读），各自 fork 出私有 block——**48-02 的前缀共享在 block 层面的实现**；beam search 等分支解码也由此变便宜。
- **显存压力的运行时表现**：block 池耗尽 -> 新请求排队/抢占（低优先级请求的 KV 被换出重算）——**抢占是延迟尖刺的来源之一**，监控 block 池水位（42 章饱和度信号）。
- **与量化的联动**：KV 也可量化（FP8），block 粒度下按需应用——**48-02 的省显存技术与 48-04 的管理技术叠加使用**。
- **Java 侧的关联**：网关看到的“引擎容量”= block 池水位决定的实际并发——**把 block 水位暴露成网关路由依据**（48-08 多副本路由的权重输入）。
- 易错点：显存“看起来还多”却并发上不去（block 碎片/预留策略）；抢占频繁（长请求被换出重算，延迟翻倍）；block 大小配置不当（太小管理开销大，太大浪费尾块）；忽略 max_model_len 与 KV 池的比例关系。

**示例代码**
```java
import java.util.ArrayList;
import java.util.List;

public class PagedAttention {
    static void wasteVsPaged() {
        System.out.println("【预留式 vs 分页式：显存利用率对比】");
        int maxLen = 2048, blockTokens = 16;
        int[] actual = {300, 1500, 60, 900};
        int reserved = actual.length * maxLen;
        int paged = 0;
        for (int len : actual) {
            paged += (int) Math.ceil((double) len / blockTokens) * blockTokens;
        }
        System.out.printf("  预留式：%d 请求 × %d = %d token 槽位%n", actual.length, maxLen, reserved);
        System.out.printf("  分页式：实际占用 %d token 槽位（利用率 %.0f%% vs %.0f%%）%n",
                paged, paged * 100.0 / reserved, 100.0);
        System.out.println("  同卡并发能力因此提升 2~3 倍 —— vLLM 吞吐优势的物理来源");
        System.out.println();
    }

    static void blockLifecycle() {
        System.out.println("【block 生命周期：分配/共享/回收】");
        List<String> flow = List.of(
                "1) 请求到达：prefill 后按实际长度分 block（16 token/块）",
                "2) 多请求共享 system prompt block（引用计数，只读）",
                "3) decode 每生成 16 token 追加一个新 block（按需）",
                "4) 请求完成 -> 私有 block 回收；共享 block 引用-1，归零才回收",
                "5) 池水位低 -> 抢占策略：换出低优先级请求的 KV（可重算恢复）");
        for (String s : flow) {
            System.out.println("  " + s);
        }
        System.out.println();
    }

    static void preemptionSignal() {
        System.out.println("【抢占：延迟尖刺的隐形来源】");
        record Event(String clock, String what, String impact) {
        }
        List<Event> events = List.of(
                new Event("10:00:05", "block 池 92%（水位告警阈值 85%）", "新请求开始排队"),
                new Event("10:00:06", "抢占 1 个低优先级长请求", "该请求延迟翻倍（重算）"),
                new Event("10:00:08", "池回落到 70%", "恢复正常"));
        for (Event e : events) {
            System.out.printf("  %s  %-32s %s%n", e.clock(), e.what(), e.impact());
        }
        System.out.println("  监控：block 水位、抢占次数（42 章饱和度 -> 事故前兆）");
        System.out.println("  处置：降低批上限 / 缩短 max_model_len / 扩容副本");
        System.out.println();
    }

    static void tuning() {
        System.out.println("【block 相关参数的调优】");
        String[][] rows = {
                {"block 大小", "16 token 常规；小=碎片少管理开销大", "默认值起步，压测微调"},
                {"gpu_memory_utilization", "0.85~0.92（留激活与碎片余量）", "过高触发 OOM，过低浪费"},
                {"max_model_len", "按业务真实需要设（不要默认最大）", "直接决定单请求 KV 上限"},
                {"swap/重算策略", "优先重算（decode 便宜）或换出（显存紧）", "按显存带宽与容量权衡"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-22s %-32s %s%n", r[0], r[1], r[2]);
        }
        System.out.println("  原则：参数组合靠压测确定，一次只动一个");
    }

    public static void main(String[] args) {
        wasteVsPaged();
        blockLifecycle();
        preemptionSignal();
        tuning();
    }
}
```

**课后练习**
1. 实现一个简化 block 管理器（分页分配/引用计数共享/回收），对比预留式与分页式在不同长度分布下的利用率。
2. 构造显存压力场景（并发逼近池上限），观察抢占事件与受影响请求的延迟，记录水位与尖刺的对应关系。
3. 思考题：为什么“重算被抢占请求”通常优于“换出到 CPU 内存”？从 decode 的计算成本与 PCIe 带宽的成本对比分析。

---

### 48-05 推理加速技术全景

**知识点**
- **加速的技术栈分四层，逐层叠加**：**模型层**（GQA/MQA、MoE 稀疏激活、更优架构）、**数值层**（FP8/INT8 量化权重与 KV、低秩近似）、**算法层**（投机解码、chunked prefill）、**系统层**（continuous batching、PagedAttention、算子融合/FlashAttention）。**44-05 的量化是部署视角，本课是引擎视角**——同一技术两面。
- **FlashAttention 的本质**：把注意力的中间矩阵不落显存（分块在 SRAM 里算完）——**省的不是计算而是显存读写**（访存是主瓶颈的又一次印证），长上下文的必备优化。
- **投机解码（speculative decoding）**：小草稿模型快速猜 K 个 token，大模型一次并行验证——**验证成本低而生成并行化**，接受率高的任务（代码补全、格式化输出）加速 2~3 倍；接受率低的任务反而变慢——**按任务特征选配**。
- **chunked prefill**：把长 prompt 切块与 decode 混排——**消除 48-03 的 prefill 干扰尖刺**，TTFT 与 ITL 兼得。
- **MoE 的推理特性**：每 token 只激活部分专家——权重显存大但计算稀疏；**专家负载均衡**影响实际吞吐（热点专家成瓶颈）。
- **加速的验证纪律**：每项技术用**同一评测集验证质量**（44-10）+ 同一压测验证性能——**“更快但更笨”的加速是负优化**；多项叠加时逐项归因（一次只开一个）。
- 易错点：叠满全部加速项不看质量（投机解码+量化叠加后质量崩）；长输出任务用投机解码（接受率低反慢）；FlashAttention 版本与模型不兼容（数值差异影响输出）；MoE 路由热点未监控（吞吐骤降难排查）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.List;

public class InferenceAcceleration {
    record Layer(String layer, String tech, String gain, String cost) {
    }

    static void stack() {
        System.out.println("【四层加速栈】");
        List<Layer> layers = List.of(
                new Layer("模型层", "GQA / MoE", "KV 显存 /4，稀疏计算", "需模型支持"),
                new Layer("数值层", "FP8/INT8 权重与 KV", "显存与带宽减半", "需评测质量"),
                new Layer("算法层", "投机解码 / chunked prefill", "decode 并行 / 尖刺消除", "接受率依赖任务"),
                new Layer("系统层", "FlashAttention / batching", "访存大幅下降 / 吞吐数倍", "与模型版本绑定"));
        for (Layer l : layers) {
            System.out.printf("  %-6s %-28s %-24s %s%n", l.layer(), l.tech(), l.gain(), l.cost());
        }
        System.out.println("  叠加顺序：先系统层（成熟收益大），再数值层，算法层按任务");
        System.out.println();
    }

    static void speculativeDemo() {
        System.out.println("【投机解码：猜 K 个，验证一次】");
        String[] draft = {"您好", "，", "订单", "已", "发货"};
        boolean[] accepted = {true, true, true, true, false};
        int acceptedCount = 0;
        for (boolean a : accepted) {
            if (a) {
                acceptedCount++;
            }
        }
        System.out.println("  草稿模型猜 5 个 token：" + String.join("", draft));
        System.out.printf("  大模型一次验证：接受 %d/5（接受率 %.0f%%）-> 本轮产出 %d 个 token 用 1 次大模型前向%n",
                acceptedCount, acceptedCount * 100.0 / accepted.length, acceptedCount);
        System.out.println("  适用：代码/JSON/固定话术（接受率高）；不适用：创意长文（接受率低）");
        System.out.println("  评测先行：接受率低于 50% 的任务关闭投机解码");
        System.out.println();
    }

    static void flashAttention() {
        System.out.println("【FlashAttention：省显存读写而非计算】");
        System.out.println("  朴素注意力：N×N 中间矩阵写显存（32K 上下文 = 数十 GB 中间量）");
        System.out.println("  FlashAttention：分块在 SRAM 内完成，不落显存中间矩阵");
        System.out.println("  收益：长上下文可跑 + 速度提升；代价：实现与硬件/精度强绑定");
        System.out.println("  验证点：开启前后同一评测集输出 diff（数值一致性）");
        System.out.println();
    }

    static void validationGate() {
        System.out.println("【加速的验收双门】");
        List<String> gates = List.of(
                "质量门：44-10 评测集通过率不回退（每次加速配置变更必跑）",
                "性能门：压测吞吐提升量化，且 TPOT/TTFT 达标",
                "归因纪律：多项加速一次只开一个，逐项确认收益与质量",
                "回退预案：每项加速有独立开关（配置回退即关）");
        for (String g : gates) {
            System.out.println("  - " + g);
        }
        System.out.println("  反例：FP8 + 投机 + Flash 一次全开 -> 吞吐上升但质量塌陷，排查一周");
    }

    public static void main(String[] args) {
        stack();
        speculativeDemo();
        flashAttention();
        validationGate();
    }
}
```

**课后练习**
1. 为你的推理服务制定加速叠加路线（四层各选一项），每项写出验收标准（质量门 + 性能门）。
2. 实测投机解码在“JSON 抽取”与“创意写作”两类任务上的加速比与接受率，得出关闭阈值建议。
3. 思考题：为什么 FlashAttention 的收益随上下文长度增长而放大？从 N×N 中间矩阵的显存读写量推导。
