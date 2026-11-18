### 40-07 Embedding 与向量检索

**知识点**
- **Embedding 把文本变成向量，把"相似"变成"距离"**：一段文本经过 embedding 模型映射成一个高维向量（如 1536 维），语义相近的文本向量距离近——"手机壳"与"保护套"的向量很近，与"扳手"很远。**语义检索的地基**。
- **余弦相似度是标准度量**：两个向量的夹角余弦，1 表示方向相同，0 正交，-1 相反。文本场景关注"方向"而非"长度"，所以用余弦而不是欧氏距离。检索实现就是**在所有文档向量里找与查询向量夹角最小的 K 个**。
- **向量数据库解决"海量向量的近邻查找"**：暴力扫是 O(N×d)，亿级不可行。近似最近邻（ANN）索引——HNSW 图、IVF 聚类、乘积量化（PQ）——牺牲一点点召回率换百倍速度。Java 侧对接 ES 的 `dense_vector` + KNN 或专用库（Milvus/pgvector）。本课用纯 JDK 演示暴力检索的完整逻辑（数据量小时够用，几十万条加 ANN）。
- **embedding 的两个重要性质**：① **不能比较不同模型的向量**（维度与语义空间都不同，换了模型必须全量重建）；② **向量携带语义但不携带"精确事实"**——"iPhone 15 售价 5999"这句话的向量与"iPhone 15 价格"近，但检索结果仍要回原文档核实数值。
- **混合检索（hybrid search）是生产标配**：关键词检索（BM25，精确匹配强）+ 向量检索（语义泛化强）各取 top N，用 RRF（Reciprocal Rank Fusion）或加权融合排序。**只向量检索会漏掉"型号、订单号"这类必须精确命中的查询**。
- **重排（rerank）提精度**：召回是"快而糙"（取 top 100），用交叉编码器（cross-encoder）对"查询-文档对"精细打分取 top 5——两阶段是检索的标准架构，与搜索引擎的粗排/精排同理。
- 易错点：向量检索当成精确检索用（订单号查询语义模糊化后召回错误）；embedding 模型换版本不重建索引（新旧向量混在一个空间，检索质量崩塌）；只做向量检索不做混合（关键词强需求场景翻车）；把 embedding 存成 float 明文进日志（维度大、日志爆炸）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class EmbeddingSearch {
    /** 简化版 embedding：4 维"语义空间"，向量手工指定以演示机制 */
    static Map<String, double[]> corpus = new LinkedHashMap<>();

    static void initCorpus() {
        // 维度语义示意：[手机配件, 电脑, 售后, 价格]
        corpus.put("手机壳 保护套 软壳", new double[]{0.95, 0.05, 0.1, 0.3});
        corpus.put("笔记本电脑 硬件参数", new double[]{0.1, 0.9, 0.1, 0.2});
        corpus.put("退货退款 政策说明", new double[]{0.1, 0.1, 0.95, 0.1});
        corpus.put("会员价格 优惠券", new double[]{0.2, 0.2, 0.2, 0.9});
    }

    static double cosine(double[] a, double[] b) {
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    static void semanticSearch() {
        System.out.println("【语义检索：查询向量与语料逐一算余弦】");
        initCorpus();
        double[] query = {0.9, 0.1, 0.15, 0.25};   // “手机保护套多少钱”
        record Hit(String doc, double score) {
        }
        List<Hit> hits = new ArrayList<>();
        for (Map.Entry<String, double[]> e : corpus.entrySet()) {
            hits.add(new Hit(e.getKey(), cosine(query, e.getValue())));
        }
        hits.sort(Comparator.comparingDouble(Hit::score).reversed());
        for (Hit h : hits) {
            System.out.printf("  %.4f  %s%n", h.score(), h.doc());
        }
        System.out.println("  “多少钱”命中了价格向量、“保护套”命中了手机壳向量 —— 词不匹配但语义匹配");
        System.out.println();
    }

    static void hybridFuse() {
        System.out.println("【混合检索：BM25 排名 + 向量排名 用 RRF 融合】");
        // 查询“手机壳 2026111701”：关键词检索强命中订单号文档，向量检索强命中壳类文档
        record Doc(String id, double bm25Rank, double vectorRank) {
        }
        List<Doc> docs = List.of(
                new Doc("订单详情页(含2026111701)", 1, 8),
                new Doc("手机壳商品页", 6, 1),
                new Doc("物流说明", 3, 4));
        int k = 60;    // RRF 常数
        for (Doc d : docs) {
            double rrf = 1.0 / (k + d.bm25Rank()) + 1.0 / (k + d.vectorRank());
            System.out.printf("  %-18s bm25排名=%.0f 向量排名=%.0f -> RRF=%.5f%n",
                    d.id(), d.bm25Rank(), d.vectorRank(), rrf);
        }
        System.out.println("  订单号这种精确 token 只有 BM25 找得到；语义近邻只有向量找得到");
        System.out.println("  生产结论：向量检索与关键词检索必须同时存在");
        System.out.println();
    }

    static void pitfalls() {
        System.out.println("【三个高频事故】");
        String[][] items = {
                {"换模型不重建", "A 模型的查询向量 vs B 模型的文档向量 -> 完全无意义，必须全量重嵌"},
                {"只向量无关键词", "搜“iPhone15 壳”被泛化成各种手机配件 -> 加 BM25 混合"},
                {"无重排", "召回 top100 里正确文档排第 30 -> 两阶段：粗召回 + 交叉编码精排"},
        };
        for (String[] i : items) {
            System.out.printf("  %-12s %s%n", i[0], i[1]);
        }
    }

    public static void main(String[] args) {
        semanticSearch();
        hybridFuse();
        pitfalls();
    }
}
```

**课后练习**
1. 把示例扩到 12 条语料 + 8 维向量（自己设计语义维度），实现"输入查询向量返回 top3"的检索器，并用 5 个查询验证语义相关性。
2. 实现 RRF 融合检索器：给定 BM25 与向量两路排名列表，输出融合后的 top5，比较"只用向量/只用关键词/混合"三种结果差异。
3. 思考题：为什么 embedding 向量换模型必须全量重建？从"语义空间是模型私有"的角度解释，并给出重建期间的灰度方案。

---

### 40-08 RAG：检索增强生成

**知识点**
- **RAG 解决模型的两个先天缺陷**：不知道私有知识（产品文档、内部制度）与不知道新知识（训练截止后）。做法是**检索相关片段塞进上下文，让模型"开卷考试"**——回答基于你给的资料而不是参数记忆，幻觉率大幅下降。
- **完整流水线五步**：① **切块（chunking）**：文档按语义段切 300~800 token，带 10~20% 重叠防切断语义；② **嵌入**：每块算向量入库；③ **检索**：混合检索取 top K 块；④ **拼 Prompt**：资料块 + 来源编号 + 问题 + "仅基于资料回答"；⑤ **生成 + 引用校验**：要求输出带 [来源N]，程序校验引用真实存在。
- **切块策略决定检索上限**：太碎（100 token）→ 单块信息不足，答案不完整；太粗（2000 token）→ 一块里塞了太多主题，向量语义被稀释。**按标题/章节结构切优于纯按字数切**，表格与代码块不可拦腰截断。
- **引用与溯源是 RAG 的质量护栏**：模型输出"根据资料3，退货运费由平台承担"，用户可点开资料 3 验证。**没有引用的 RAG 只是幻觉换了件衣服**；程序要校验引用编号真实、且引用的块确实包含被声称的内容（抽查关键词）。
- **检索不到时的诚实路径**：相似度最高分低于阈值 → 诚实回答"资料中没有找到"，转人工。**宁可说不知道，不可编造**——这是产品决策也是 Prompt 明文规则。
- **多文档冲突**：2024 版政策说 A、2025 版说 B，检索都可能命中。治理手段：文档带版本元数据，Prompt 注入"以最新版本为准"，冲突样本进测试集。
- 易错点：把整本文档塞上下文当 RAG（贵、慢、窗口超限、注意力稀释）；切块无重叠导致关键句子被切断；检索结果与问题毫不相关还硬塞给模型（问什么答什么，模型被迫编）；RAG 知识库更新无流程（文档改了向量库没更新，答案一直是旧的）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RagPipeline {
    record Chunk(String source, String text, double[] vector) {
    }

    static List<Chunk> chunks = new ArrayList<>();

    static void chunking() {
        System.out.println("【切块：按结构切、带重叠、块内语义完整】");
        String doc = "退货政策：\n  1.7天内无理由退货，运费买家承担。\n  2.质量问题退货，运费商家承担。\n  3.生鲜类不支持无理由退货。";
        String[] parts = doc.split("\n");
        for (int i = 0; i < parts.length; i++) {
            String text = i > 0 && i < parts.length - 1
                    ? parts[i] + "（重叠：" + nextHead(parts[i + 1]) + "）"
                    : parts[i];
            double[] vec = {i == 2 ? 0.95 : 0.2, i == 1 ? 0.95 : 0.3, 0.1};
            chunks.add(new Chunk("退货政策#" + (i + 1), text, vec));
            System.out.printf("  块%d [%s] %s%n", i + 1, "退货政策#" + (i + 1), text);
        }
        System.out.println("  300~800 token/块，10~20% 重叠；表格与代码块不可拦腰截断");
        System.out.println();
    }

    static String nextHead(String s) {
        return s.length() <= 8 ? s : s.substring(0, 8) + "…";
    }

    static List<Chunk> retrieve(double[] query, int topK, double minScore) {
        record Scored(Chunk c, double s) {
        }
        List<Scored> scored = new ArrayList<>();
        for (Chunk c : chunks) {
            double dot = 0, na = 0, nb = 0;
            for (int i = 0; i < c.vector().length; i++) {
                dot += c.vector()[i] * query[i];
                na += c.vector()[i] * c.vector()[i];
                nb += query[i] * query[i];
            }
            scored.add(new Scored(c, dot / (Math.sqrt(na) * Math.sqrt(nb))));
        }
        scored.sort((a, b) -> Double.compare(b.s(), a.s()));
        List<Chunk> out = new ArrayList<>();
        for (Scored s : scored) {
            if (s.s() < minScore) {
                break;
            }
            out.add(s.c());
        }
        return out.size() > topK ? out.subList(0, topK) : out;
    }

    static void fullPipeline() {
        System.out.println("【完整 RAG：检索 -> 拼 Prompt -> 生成带引用 -> 校验】");
        double[] query = {0.9, 0.3, 0.1};    // “质量问题退货运费谁出”
        List<Chunk> top = retrieve(query, 2, 0.3);
        StringBuilder material = new StringBuilder();
        for (int i = 0; i < top.size(); i++) {
            material.append("[资料").append(i + 1).append("] ").append(top.get(i).text()).append("\n");
        }
        System.out.println("  检索到 " + top.size() + " 块：");
        System.out.println(material);
        String systemPrompt = "仅基于以下资料回答，回答格式：结论 + [资料N]。资料不足时回答“未在资料中找到”。";
        System.out.println("  system: " + systemPrompt);
        // 模拟生成（规则版模型）：命中“质量问题退货，运费商家承担”
        String answer = null;
        for (int i = 0; i < top.size(); i++) {
            if (top.get(i).text().contains("质量问题")) {
                answer = "质量问题退货运费由商家承担。" + "[资料" + (i + 1) + "]";
            }
        }
        if (answer == null) {
            answer = "未在资料中找到";
        }
        System.out.println("  生成回答：" + answer);
        boolean refValid = answer.contains("[资料") && top.size() >= 1;
        System.out.println("  引用校验：" + (refValid ? "引用编号真实存在 -> PASS" : "FAIL"));
        System.out.println();
    }

    static void noAnswerPath() {
        System.out.println("【检索为空：诚实路径】");
        double[] irrelevant = {0.05, 0.05, 0.9};   // “公司年会什么时候开”
        List<Chunk> top = retrieve(irrelevant, 2, 0.6);
        System.out.println("  最高相似度低于阈值 0.6，检索到 " + top.size() + " 块");
        System.out.println("  回答：“未在资料中找到，已为您转人工。”");
        System.out.println("  原则：宁可转人工，不可编造 —— 写进 Prompt 也写进产品流程");
    }

    public static void main(String[] args) {
        chunking();
        fullPipeline();
        noAnswerPath();
    }
}
```

**课后练习**
1. 为一份真实业务文档实现完整 RAG（切块 + 向量 + 检索 + 引用校验），准备 10 个问题验证回答质量，其中 2 个是知识库外的问题验证"诚实路径"。
2. 对比三种切块粒度（200/500/1500 token）对检索质量的影响：用同一组问题测命中率，记录结论。
3. 思考题：为什么"引用校验"要做成程序逻辑而不是提示词约束？从模型输出不可保证性的角度设计三层防线。

---

### 40-09 LangChain4j 与 Spring AI 的分层思想

**知识点**
- **框架解决的是"模型之上的重复工程"**：模型调用、消息管理、流式处理、工具调用循环、RAG 组件（加载器/切块/向量库/检索器）、记忆存储——这些每个 AI 应用都要写一遍，框架把它们标准化成组件。**LangChain4j 与 Spring AI 是 Java 生态两大主流**。
- **Spring AI 的分层模型**：`ChatClient`（门面，流式 API 构建 prompt）→ `ChatModel`（模型适配层，OpenAI/Ollama/通义…同一接口）→ 底层 HTTP。**换模型只换配置不换代码**——前提是你只用标准能力；深度依赖某家特性（如某些私有 function 格式）会锁死。
- **LangChain4j 的 AI Services 抽象**：定义一个 Java 接口（方法签名即业务语义），框架用动态代理把"方法调用 → Prompt 组装 → 模型调用 → 结果解析"全自动完成，配合 `@UserMessage/@SystemMessage` 注解。**把 LLM 调用变成类型安全的方法调用**，是最"Java"的设计。
- **记忆组件的接口化**：`ChatMemory` 接口 + 内存/Redis/数据库实现，与 40-05 的手写 Session 同构——框架只是把你该写的接口先写好了。理解手写实现的意义就在于**框架失效时能自己补**。
- **RAG 组件链**：DocumentLoader → Splitter → EmbeddingModel → VectorStore（ES/pgvector/Milvus）→ Retriever → ContentRetriever 注入 ChatClient。每一环都有接口与多实现，**理解数据流比记住类名重要**。
- **框架的代价**：抽象层屏蔽了原始请求细节，排查问题时看不到真实 HTTP 报文；版本迭代快，API 不稳定；复杂工具循环仍要自己管。**规范建议：先手写一遍最小实现（本课程的方式），再用框架提效**。
- 易错点：把框架当魔法（出了问题不知从哪查，不知道它发了什么请求）；深度绑定某模型私有特性导致迁移成本巨大；升级框架版本后 Prompt 组装行为变化没有回归测试；忽略框架的自动重试/超时配置与业务超时策略冲突。

**示例代码**
```java
import java.util.ArrayList;
import java.util.List;

public class AiFrameworkDemo {
    /** 模拟 ChatModel 接口：所有模型适配器的统一形状 */
    interface ChatModel {
        String call(String system, String user);
    }

    static class MockChatModel implements ChatModel {
        public String call(String system, String user) {
            if (user.contains("运费")) {
                return "质量问题退货运费由商家承担。";
            }
            return "（模拟回答）收到：" + user;
        }
    }

    /** 模拟 ChatClient 门面：链式构建 + 统一出口 */
    static class ChatClient {
        private final ChatModel model;
        private String system = "";
        private final List<String> retrieverInject = new ArrayList<>();

        ChatClient(ChatModel model) {
            this.model = model;
        }

        ChatClient system(String s) {
            this.system = s;
            return this;
        }

        ChatClient withRetrieved(String material) {
            retrieverInject.add(material);
            return this;
        }

        String prompt(String user) {
            StringBuilder sb = new StringBuilder(system).append("\n");
            for (String m : retrieverInject) {
                sb.append("资料：").append(m).append("\n");
            }
            return sb.append("\n问题：").append(user).toString();
        }

        String call(String user) {
            return model.call(system, prompt(user));
        }
    }

    /** 模拟 AI Services：接口 + 动态代理 = 类型安全的模型调用 */
    interface SupportAssistant {
        String answer(String question);
    }

    static SupportAssistant buildAssistant(ChatClient client) {
        return (SupportAssistant) java.lang.reflect.Proxy.newProxyInstance(
                SupportAssistant.class.getClassLoader(),
                new Class<?>[]{SupportAssistant.class},
                (proxy, method, args) -> client.call((String) args[0]));
    }

    static void layeredCall() {
        System.out.println("【分层调用：门面 -> 模型适配 -> 输出】");
        ChatClient client = new ChatClient(new MockChatModel())
                .system("你是售后助手，仅基于资料回答。")
                .withRetrieved("[资料1] 质量问题退货，运费商家承担。");
        System.out.println("  组装后的 Prompt：\n----");
        System.out.println(client.prompt("质量问题退货运费谁出？"));
        System.out.println("----");
        System.out.println("  回答：" + client.call("质量问题退货运费谁出？"));
        System.out.println("  换模型 = 换 ChatModel 实现（OpenAI/Ollama/通义），门面代码不动");
        System.out.println();
    }

    static void aiServices() {
        System.out.println("【AI Services：把模型调用变成普通方法】");
        SupportAssistant assistant = buildAssistant(new ChatClient(new MockChatModel())
                .system("你是售后助手。"));
        String answer = assistant.answer("质量问题退货运费谁出？");
        System.out.println("  assistant.answer(...) -> " + answer);
        System.out.println("  LangChain4j 用同样思路：@UserMessage 注解 + 代理生成真实调用");
        System.out.println("  收益：类型安全、可单元测试（Mock ChatModel）、职责清晰");
        System.out.println();
    }

    static void frameworkMap() {
        System.out.println("【组件映射：手写实现 vs 框架组件】");
        String[][] map = {
                {"40-02 请求响应", "ChatModel / ChatClient"},
                {"40-05 会话记忆", "ChatMemory（InMemory/Redis）"},
                {"40-06 工具循环", "ToolCallingExecutor / @Tool"},
                {"40-07 向量检索", "EmbeddingModel + VectorStore"},
                {"40-08 RAG 流水线", "DocumentLoader + Splitter + ContentRetriever"},
        };
        for (String[] m : map) {
            System.out.printf("  %-18s -> %s%n", m[0], m[1]);
        }
        System.out.println("  结论：手写过一遍，框架只是把你写的东西标准化");
    }

    public static void main(String[] args) {
        layeredCall();
        aiServices();
        frameworkMap();
    }
}
```

**课后练习**
1. 把 40-08 的 RAG 流水线接入本课的 ChatClient（检索结果自动注入），对比"带资料/不带资料"两个版本对同一问题的回答差异。
2. 用动态代理实现支持多方法的多业务 AI 接口（如 `refundPolicy(String q)`、`logisticsQuery(String q)`），每个方法绑定不同系统提示。
3. 思考题：框架屏蔽了真实 HTTP 报文，如何在生产环境保留可观测性？（提示：日志拦截器/模型适配层包装）
