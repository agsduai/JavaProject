### 44-05 本地模型部署与量化

**知识点**
- **为什么要本地部署**：数据不出域（合规硬约束）、网络延迟（内网高频调用）、成本（海量低价值任务自建更便宜）、可用性（内网断网也能跑）。**云端 API 与本地模型不是二选一，是路由问题（44-08）**。
- **本地部署的主流形态**：Ollama（最易上手，一键拉模型）、vLLM（生产级高吞吐，PagedAttention）、llama.cpp（CPU/边缘设备）。Java 对接 OpenAI 兼容接口——**Ollama/vLLM 都暴露 OpenAI 风格 API，40-02 的对接代码原样复用**。
- **模型大小的现实感**：7B 模型（FP16 权重约 14GB）单卡 24G 可跑；70B 需要 4 卡或量化。**参数量 × 精度 = 显存需求**，选型的第一问是"你有什么卡"。
- **量化（quantization）用精度换显存**：FP16 -> INT8（体积减半，质量损失极小）-> INT4（再减半，损失可感知）。主流格式 GGUF（llama.cpp/Ollama 用）与 AWQ/GPTQ（vLLM 用）。**经验：4-bit 量化的小模型在抽取/分类任务上常可接受，创作类任务质量下降明显**——量化后必须跑评测（44-10）。
- **吞吐与并发的权衡**：vLLM 的 continuous batching 让多请求共享算力，吞吐远高于逐条；Ollama 默认并发低（适合个人）。**"本地模型慢"多数是部署方式问题而不是模型问题**。
- **本地小模型的能力边界**：7B~14B 量化模型适合：分类/抽取/改写/简单摘要；不适合：复杂推理、长上下文精细理解、前沿知识。**"小模型干粗活、大模型干细活"的路由策略**（44-08）是本地部署的最佳实践。
- 易错点：显存预算只算权重不算 KV Cache（上下文一长 OOM）；量化后不跑评测直接上（质量塌了不知道）；用 CPU 跑大模型等十分钟（边缘场景才用 CPU）；本地模型不带幂等重试（和云端一样的工程要求）。

**示例代码**
```java
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class LocalLlm {
    record ModelOption(String name, int params, String quant, double vramGb, String fit) {
        double weightGb() {
            return params * (quant.contains("INT4") ? 0.5 : quant.contains("INT8") ? 1.0 : 2.0);
        }
    }

    static void vramBudget() {
        System.out.println("【显存预算：权重 + KV Cache】");
        List<ModelOption> opts = List.of(
                new ModelOption("Qwen2.5-7B", 7, "FP16", 0, "单卡24G 可跑"),
                new ModelOption("Qwen2.5-7B", 7, "INT8", 0, "约 7G 权重"),
                new ModelOption("Qwen2.5-7B", 7, "INT4", 0, "约 3.5G 权重，长上下文余量大"),
                new ModelOption("Qwen2.5-72B", 72, "INT4", 0, "约 36G+，需多卡"));
        for (ModelOption m : opts) {
            double w = m.weightGb();
            double kv = 2.0;   // 8K 上下文的 KV Cache 估算（示意）
            System.out.printf("  %-14s %-4s 权重≈%5.1fGB + KV≈%.0fGB -> 建议 %.0fGB 显存%n",
                    m.name(), m.quant(), w, kv, w + kv + 1);
        }
        System.out.println("  规则：上下文越长 KV Cache 越大；预算只算权重必翻车");
        System.out.println();
    }

    static void openaiCompat() {
        System.out.println("【Ollama 的 OpenAI 兼容接口：40-02 代码原样复用】");
        String req = "{\"model\":\"qwen2.5:7b-instruct\",\"temperature\":0.2,"
                + "\"messages\":[{\"role\":\"user\",\"content\":\"把这段文本抽成 JSON...\"}]}";
        String resp = "{\"choices\":[{\"message\":{\"role\":\"assistant\","
                + "\"content\":\"{\\\"sku\\\":\\\"A100\\\",\\\"stock\\\":12}\"}}]}";
        System.out.println("  请求（指向 http://localhost:11434/v1/chat/completions）：");
        System.out.println("    " + req);
        System.out.println("  响应：" + resp);
        System.out.println("  换模型的改动 = 换 base_url 与 model 字段 —— 路由层（44-08）的基础");
        System.out.println();
    }

    static void quantTradeoff() {
        System.out.println("【量化的取舍：必须用评测验证】");
        record Task(String name, String fp16Score, String int4Score, String verdict) {
        }
        List<Task> tasks = List.of(
                new Task("意图分类（5类）", "94.2%", "93.8%", "可接受 -> 用 INT4"),
                new Task("字段抽取（订单）", "91.5%", "90.1%", "可接受，JSON 校验兜底"),
                new Task("营销文案生成", "8.2/10", "6.9/10", "明显下降 -> 创作任务用 FP16/云端"),
                new Task("复杂多步推理", "71%", "58%", "大幅下降 -> 路由到云端大模型"));
        for (Task t : tasks) {
            System.out.printf("  %-14s FP16=%-8s INT4=%-8s %s%n",
                    t.name(), t.fp16Score(), t.int4Score(), t.verdict());
        }
        System.out.println("  量化不是免费的：不同任务的损失差异巨大，评测说话");
        System.out.println();
    }

    static void servingChoice() {
        System.out.println("【部署形态选择】");
        List<String> rows = List.of(
                "个人/POC：Ollama 一键起，OpenAI 兼容，并发低",
                "生产高吞吐：vLLM（continuous batching + PagedAttention），吞吐数倍",
                "边缘/无 GPU：llama.cpp CPU 推理，慢但可用",
                "托管折中：云厂商一键部署（兼顾合规与运维省心）");
        for (String r : rows) {
            System.out.println("  - " + r);
        }
    }

    public static void main(String[] args) {
        vramBudget();
        openaiCompat();
        quantTradeoff();
        servingChoice();
    }
}
```

**课后练习**
1. 用 Ollama 部署一个 7B 模型，用 40-02 的客户端代码对接，跑通"文本抽取"任务并记录延迟与吞吐。
2. 对比 FP16 与 INT4 在"字段抽取/文案生成"两个任务上的质量（各 20 条样本），量化差异并写出路由建议。
3. 思考题：为什么"本地模型不带幂等重试"是错误？本地部署改变了什么、没改变什么？

---

### 44-06 模型微调与 LoRA

**知识点**
- **什么时候需要微调，什么时候不需要**：提示词能解决的不微调（few-shot/结构化输出，40-03）；知识更新用 RAG（40-08）不用微调；**微调的正当场景**：稳定的输出风格/格式（难以用提示描述）、垂直领域术语理解、小模型替代大模型（蒸馏，省成本）、指令遵循风格统一。
- **微调 ≠ 灌知识**：把文档塞进训练数据让模型"记住"，效果差且昂贵还容易过拟合——**知识该用 RAG（可更新、可溯源），能力该用微调**（怎么回答、什么格式、什么风格）。
- **LoRA 的核心思想**：冻结原模型权重，只训练注入的低秩小矩阵（rank 8~64），**可训练参数只有全量的 0.1%~1%**。收益：单卡可训、训练快、多个 LoRA 适配器可热切换（一个基座 + 多个业务 LoRA）。
- **训练数据是成败的关键**：几百到几千条高质量样本（问答对/输入输出对）胜过十万条垃圾数据。**数据治理**：格式统一、去重、清洗噪声、留出验证集。数据质量差是微调失败的 80% 原因。
- **过拟合与灾难性遗忘**：训练太多轮模型只会背训练样本（泛化失效）、或忘掉原本能力（通用问答变差）。**对策**：早停（验证集分数拐点）、保留通用数据混合训练、LoRA 的低 rank 本身有正则效应。
- **评估与上线**：微调模型与基座模型在**同一评测集**上对比（44-10），通过才发布；LoRA 适配器可灰度切换——**微调也要走"评测 -> 灰度 -> 全量"的发布流程**（41 章思想复用）。
- 易错点：用微调解决知识时效问题（应该 RAG）；训练数据与真实分布脱节（评测集满分、线上翻车）；不设验证集盲训（过拟合不可见）；微调后不做基座对比直接替换线上模型。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class FineTuning {
    record Sample(String input, String output, String source) {
    }

    static void whenToFinetune() {
        System.out.println("【决策：RAG / 提示词 / 微调 的边界】");
        String[][] rows = {
                {"产品知识问答", "RAG", "知识需更新、需溯源"},
                {"固定格式抽取", "提示词 + JSON 模式", "格式可描述，无需训练"},
                {"报告生成风格统一", "微调（LoRA）", "风格难以用提示词描述"},
                {"术语理解（医疗/法律）", "微调 + RAG 混合", "术语能力训练，知识检索"},
                {"小模型替代大模型", "微调（蒸馏）", "用大模型生成数据教小模型，省成本"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-14s %-16s %s%n", r[0], r[1], r[2]);
        }
        System.out.println("  口诀：知识用 RAG，能力用微调，格式用提示词");
        System.out.println();
    }

    static void loraIntuition() {
        System.out.println("【LoRA：冻结大矩阵，只训小增量】");
        long baseParams = 7_000_000_000L;
        long loraParams = 20_000_000;
        System.out.printf("  基座 %.0fB 参数冻结 + LoRA %.0fM 参数训练（占比 %.3f%%）%n",
                baseParams / 1e9, loraParams / 1e6, loraParams * 100.0 / baseParams);
        System.out.println("  收益：单卡 24G 可训、训练小时级、适配器按业务热切换");
        System.out.println("  一个基座 + 多个 LoRA：order-lora（工单风格）、support-lora（客服语气）");
        System.out.println();
    }

    static void dataQuality() {
        System.out.println("【训练数据治理：质量的 80/20】");
        record Issue(String problem, String effect, String fix) {
        }
        List<Issue> issues = List.of(
                new Issue("格式不统一", "模型输出格式漂移", "统一 schema 校验后入库"),
                new Issue("重复样本", "过拟合到重复项", "去重（语义+精确）"),
                new Issue("分布偏差", "评测满分、线上翻车", "按线上真实分布采样"),
                new Issue("无验证集", "过拟合不可见", "留 10~20% 验证，早停"));
        for (Issue i : issues) {
            System.out.printf("  %-10s %-16s %s%n", i.problem(), i.effect(), i.fix());
        }
        System.out.println("  规模经验：500~5000 条高质量样本足够多数风格/格式任务");
        System.out.println();
    }

    static void evalGate() {
        System.out.println("【发布闸门：微调模型 vs 基座对比】");
        record Eval(String task, String base, String finetuned, String verdict) {
        }
        List<Eval> evals = List.of(
                new Eval("目标：工单风格一致性", "61%", "88%", "PASS：目标达成"),
                new Eval("回归：通用问答", "92%", "90%", "PASS：遗忘可接受（<3%）"),
                new Eval("回归：JSON 抽取", "90%", "84%", "FAIL：验证集没覆盖，回去补数据"));
        for (Eval e : evals) {
            System.out.printf("  %-18s 基座=%-6s 微调=%-6s %s%n",
                    e.task(), e.base(), e.finetuned(), e.verdict());
        }
        System.out.println("  未通过不发布：微调必须与基座同评测集对比，双门槛（目标+回归）");
    }

    public static void main(String[] args) {
        whenToFinetune();
        loraIntuition();
        dataQuality();
        evalGate();
    }
}
```

**课后练习**
1. 用你的业务数据构造 200 条微调样本（含 schema 校验与去重），写出数据治理清单与验证集划分方案。
2. 在 Ollama 上体验一个已微调的社区模型 vs 基座模型，对比同一评测集（10 条）上的表现差异。
3. 思考题：为什么"知识用 RAG，能力用微调"是工程上的黄金划分？从更新成本、溯源、规模效应三个角度分析。

---

### 44-07 MCP 与工具生态

**知识点**
- **MCP（Model Context Protocol）解决的问题是"N 个应用 × M 个工具"的 M×N 集成爆炸**：每个 AI 应用都要重写一遍"怎么调数据库/怎么发邮件/怎么查 Jira"。MCP 把工具做成**标准化服务（MCP Server）**，任何支持协议的客户端（MCP Host）即插即用——**M×N 变成 M+N**。
- **MCP 的三个原语**：**Tools**（模型可调用的函数，等同 40-06 的工具）、**Resources**（可读取的数据资源，如文件/记录）、**Prompts**（预置提示模板）。客户端发现 Server 能力（能力协商）后把工具清单注入模型。
- **协议形态**：stdio（本地进程，最简单）与 HTTP+SSE（远程服务）。Server 是独立进程——**Java 写 MCP Server 就是写一个标准化工具服务**，工具实现仍是你的 Java 代码，MCP 只是壳。
- **MCP 改变的工程格局**：工具的**复用与治理**集中化——权限、审计、限流在 Server 层统一做（44-09 的权限检查下沉到工具服务）；工具升级与应用解耦。
- **生态与自建的分界**：通用能力（GitHub/Jira/数据库/文件系统）用社区 Server；核心业务工具（查订单、退款）自建 Server 并纳入正常研发流程（测试/评审/监控）。**自建 Server 也是生产服务**，不是脚本。
- **安全是 MCP 的第一议题**：接入第三方 Server = 给 Agent 新的能力，**能力协商清单要人工审批**；Server 返回的内容也是"输入"（可能带注入，40-03 的防线照用）；工具描述（description）被模型信任，恶意 Server 可以诱导调用。
- 易错点：把 MCP Server 当轻量脚本（无鉴权无审计直接上）；接入社区 Server 不审权限（Agent 获得了删除文件能力）；工具描述含糊导致模型乱选（40-06 的经验在 MCP 同样适用）；本地 stdio Server 无并发控制（阻塞 Agent 循环）。

**示例代码**
```java
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class McpEcosystem {
    /** MCP Server 的能力清单（模型可见的契约） */
    record McpTool(String name, String desc, String scope, String approval) {
    }

    static void capabilityHandshake() {
        System.out.println("【能力协商：Host 发现 Server 的工具清单】");
        List<McpTool> tools = List.of(
                new McpTool("jira_query", "按 JQL 查询工单", "read:jira", "预审批通过"),
                new McpTool("db_query", "执行只读 SQL", "read:dw", "预审批通过"),
                new McpTool("jira_transition", "变更工单状态", "write:jira", "每次调用需确认"),
                new McpTool("fs_delete", "删除文件", "write:fs", "拒绝接入（高危）"));
        System.out.println("  Server: jira-server + internal-db-server");
        for (McpTool t : tools) {
            System.out.printf("  %-16s %-16s 权限域=%-10s 接入=%s%n",
                    t.name(), t.desc(), t.scope(), t.approval());
        }
        System.out.println("  接入策略：能力清单人工审批，高危工具直接拒绝");
        System.out.println();
    }

    static void callFlow() {
        System.out.println("【一次 MCP 工具调用的完整链路】");
        List<String> steps = List.of(
                "1) Host 把已接入工具的清单（name/desc/schema）注入模型",
                "2) 模型输出 tool_use: {name: db_query, args: {sql: ...}}",
                "3) Host 权限检查（scope 匹配 + 敏感工具二次确认）",
                "4) Host 经协议调用 Server -> Server 执行返回结果",
                "5) 结果作为 tool_result 回传模型（40-06 循环不变）");
        for (String s : steps) {
            System.out.println("  " + s);
        }
        System.out.println("  对模型而言 MCP 工具与内置工具无差别；差别在治理层");
        System.out.println();
    }

    static void serverAsService() {
        System.out.println("【自建 MCP Server = 生产服务】");
        Map<String, String> checklist = new LinkedHashMap<>();
        checklist.put("鉴权", "token 校验 + 按调用方的 scope 限权");
        checklist.put("审计", "每次调用记录调用方/参数/耗时");
        checklist.put("限流", "防 Agent 循环打爆下游（44-02 护栏外的第二道）");
        checklist.put("测试", "工具实现走正常单测 + 集成测试");
        for (Map.Entry<String, String> e : checklist.entrySet()) {
            System.out.printf("  %-4s %s%n", e.getKey(), e.getValue());
        }
        System.out.println("  社区 Server 用于通用能力；业务 Server 必须进正常研发流程");
        System.out.println();
    }

    static void securityNotes() {
        System.out.println("【MCP 特有的安全点】");
        List<String> notes = List.of(
                "工具描述是模型的输入：恶意 Server 可写诱导性描述 -> 接入前人工审阅",
                "Server 返回内容也是输入：防二次注入（40-03 包裹 + 输出校验）",
                "权限最小化：read Server 不给 write scope，宁可多建 Server",
                "版本锁定：Server 升级 = 能力变化，变更走审批");
        for (String n : notes) {
            System.out.println("  - " + n);
        }
    }

    public static void main(String[] args) {
        capabilityHandshake();
        callFlow();
        serverAsService();
        securityNotes();
    }
}
```

**课后练习**
1. 用官方 SDK（或手写 stdio 协议）实现一个业务 MCP Server（如"查订单状态"），完成能力清单、鉴权与审计日志。
2. 设计你团队的 MCP 接入审批表：第三方 Server 的工具清单、权限域、审批结论，演示一次"高危工具拒绝接入"。
3. 思考题：为什么 MCP 的价值是"M×N 变 M+N"？对比接入前后每新增一个 AI 应用/一个工具的开发工作量。

---

### 44-08 AI 网关与多模型路由

**知识点**
- **多模型是常态，统一入口是刚需**：业务已经同时面对云端大模型、云端小模型、本地模型（44-05）——每个调用方各自管鉴权/重试/限流是灾难。**AI 网关是所有模型调用的唯一入口**：统一协议、统一鉴权、统一可观测。
- **网关的核心职责**：**协议转换**（OpenAI 风格 -> 各家私有 API）、**密钥管理**（业务侧只见网关 token，真实 Key 在网关）、**限流与配额**（按业务方分配预算）、**路由与降级**（44-10 分级路由的执行层）、**成本归因**（每请求记账到业务方）。
- **路由策略的三层**：**规则路由**（意图/长度/成本标签 -> 指定模型，确定性高）；**模型路由**（用小模型判断该任务需要大模型吗）；**降级路由**（主模型失败/超时 -> 备用 -> 本地兜底）。40-10 的降级阶梯在网关层统一实现，业务方零感知。
- **语义缓存是网关的增值项**：相同/语义相近请求直接返回缓存（40-10 的缓存层上移到网关，全局共享）。**缓存命中率的监控与失效策略**（知识库更新后相关缓存失效）是缓存正确性的关键。
- **可观测性下沉到网关**：tokens、模型版本、首 token 延迟、缓存命中率全部网关记录（42 章指标体系），**成本按业务方/意图归因出报表**——这是 AI 功能成本治理的执行点。
- **AI 网关的失败语义**：网关自己是单点——部署要高可用；超时预算要留给上游（网关超时 + 上游超时叠加会让用户等到天荒地老）。
- 易错点：业务方直连各家 API（密钥散落、无归因）；网关超时没做预算分配（叠加超时）；语义缓存阈值过松（错误答案被缓存，40-10 的教训）；降级路由把"重试打同一个过载模型"（应切换到备用池）。

**示例代码**
```java
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AiGateway {
    record ModelRoute(String name, String tier, double costPer1k, int p99Ms, String fit) {
    }

    static final List<ModelRoute> POOL = List.of(
            new ModelRoute("cloud-large", "large", 0.04, 2200, "复杂推理/长文"),
            new ModelRoute("cloud-small", "small", 0.004, 600, "分类/抽取"),
            new ModelRoute("local-7b-int4", "local", 0.0002, 900, "高频粗活/内网合规"));

    static void routeDecision() {
        System.out.println("【路由决策：规则先行，降级兜底】");
        record Call(String intent, String route, String note) {
        }
        List<Call> calls = List.of(
                new Call("意图分类", "cloud-small", "规则路由：分类类目 -> small"),
                new Call("工单摘要", "local-7b", "合规路由：内网数据不出域"),
                new Call("复杂诊断", "cloud-large", "能力路由：需要推理"),
                new Call("复杂诊断(超时)", "cloud-large -> cloud-small", "降级路由：主超时切备用"),
                new Call("任意(网关故障)", "本地兜底", "终极降级：local 恒在线"));
        for (Call c : calls) {
            System.out.printf("  %-16s -> %-22s %s%n", c.intent(), c.route(), c.note());
        }
        System.out.println();
    }

    static void costAttribution() {
        System.out.println("【成本归因：网关的记账本】");
        record Ledger(String biz, String model, long tokens, double cost) {
        }
        List<Ledger> ledger = List.of(
                new Ledger("客服-意图分类", "small", 1_800_000, 7.2),
                new Ledger("客服-复杂问答", "large", 120_000, 4.8),
                new Ledger("工单-风格改写", "local", 900_000, 0.2));
        double total = 0;
        for (Ledger l : ledger) {
            total += l.cost();
            System.out.printf("  %-14s %-6s %8d tok  %.1f 元%n", l.biz(), l.model(), l.tokens(), l.cost());
        }
        System.out.printf("  日合计 %.1f 元 -> 按业务方出周报，谁烧钱一目了然%n", total);
        System.out.println();
    }

    static void timeoutBudget() {
        System.out.println("【超时预算：网关与上游不许叠加】");
        int userBudget = 3000;
        int gatewayOverhead = 100;
        int upstream = userBudget - gatewayOverhead;
        System.out.printf("  用户预算 %dms = 网关自身 %dms + 上游调用 %dms%n",
                userBudget, gatewayOverhead, upstream);
        System.out.println("  降级链路预算：large 2000ms 超时 -> 切 small（剩余 900ms）");
        System.out.println("  反例：网关 3s + 上游 3s -> 用户等 6s 预算翻倍");
        System.out.println();
    }

    static void gatewayChecklist() {
        System.out.println("【网关上线清单】");
        List<String> items = List.of(
                "统一 OpenAI 兼容协议，业务方只认网关 token",
                "规则/模型/降级三层路由配置化（改路由不发版）",
                "语义缓存（阈值 0.95+，知识库更新可定向失效）",
                "全量记账：tokens/模型/业务方/首 token 延迟",
                "网关自身高可用 + 超时预算分配",
                "密钥只在网关，轮换不影响业务方");
        for (String i : items) {
            System.out.println("  [ ] " + i);
        }
    }

    public static void main(String[] args) {
        routeDecision();
        costAttribution();
        timeoutBudget();
        gatewayChecklist();
    }
}
```

**课后练习**
1. 实现迷你 AI 网关（模拟）：协议转发 + 规则路由 + 超时降级 + 成本记账，用 20 个模拟请求验证路由与降级日志。
2. 设计你团队的模型池与路由表：至少 3 个模型、4 类意图的规则、2 级降级，并给出超时预算分配。
3. 思考题：为什么语义缓存放在网关层比放在业务层好？从缓存共享面、失效广播、成本归因三个角度分析。
