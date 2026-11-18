/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：40-09
 * 课名：LangChain4j 与 Spring AI 的分层思想
 * ------------------------------------------------------------
 * 知识点：
 *   - **框架解决的是"模型之上的重复工程"**：模型调用、消息管理、流式处理、工具调用循环、RAG 组件（加载器/切块/向量库/检索器）、记忆存储——这些每个 AI 应用都要写一遍，框架把它们标准化成组件。**LangChain4j 与 Spring AI 是 Java 生态两大主流**。
 *   - **Spring AI 的分层模型**：`ChatClient`（门面，流式 API 构建 prompt）→ `ChatModel`（模型适配层，OpenAI/Ollama/通义…同一接口）→ 底层 HTTP。**换模型只换配置不换代码**——前提是你只用标准能力；深度依赖某家特性（如某些私有 function 格式）会锁死。
 *   - **LangChain4j 的 AI Services 抽象**：定义一个 Java 接口（方法签名即业务语义），框架用动态代理把"方法调用 → Prompt 组装 → 模型调用 → 结果解析"全自动完成，配合 `@UserMessage/@SystemMessage` 注解。**把 LLM 调用变成类型安全的方法调用**，是最"Java"的设计。
 *   - **记忆组件的接口化**：`ChatMemory` 接口 + 内存/Redis/数据库实现，与 40-05 的手写 Session 同构——框架只是把你该写的接口先写好了。理解手写实现的意义就在于**框架失效时能自己补**。
 *   - **RAG 组件链**：DocumentLoader → Splitter → EmbeddingModel → VectorStore（ES/pgvector/Milvus）→ Retriever → ContentRetriever 注入 ChatClient。每一环都有接口与多实现，**理解数据流比记住类名重要**。
 *   - **框架的代价**：抽象层屏蔽了原始请求细节，排查问题时看不到真实 HTTP 报文；版本迭代快，API 不稳定；复杂工具循环仍要自己管。**规范建议：先手写一遍最小实现（本课程的方式），再用框架提效**。
 *   - 易错点：把框架当魔法（出了问题不知从哪查，不知道它发了什么请求）；深度绑定某模型私有特性导致迁移成本巨大；升级框架版本后 Prompt 组装行为变化没有回归测试；忽略框架的自动重试/超时配置与业务超时策略冲突。
 * ============================================================
 */

package com.heima.ch40;

import java.util.ArrayList;
import java.util.List;

// ==================== 示例代码 ====================

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

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 把 40-08 的 RAG 流水线接入本课的 ChatClient（检索结果自动注入），对比"带资料/不带资料"两个版本对同一问题的回答差异。
 * 2. 用动态代理实现支持多方法的多业务 AI 接口（如 `refundPolicy(String q)`、`logisticsQuery(String q)`），每个方法绑定不同系统提示。
 * 3. 思考题：框架屏蔽了真实 HTTP 报文，如何在生产环境保留可观测性？（提示：日志拦截器/模型适配层包装）
 * ============================================================
 */
