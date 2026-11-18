/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：40-02
 * 课名：调用大模型 API：请求、响应与错误
 * ------------------------------------------------------------
 * 知识点：
 *   - **LLM 服务的标准交互是 HTTP + JSON**（OpenAI 风格已成为事实标准）：请求体核心三件套——`model`（用哪个模型）、`messages`（角色+内容数组）、`temperature` 等采样参数；响应核心是 `choices[0].message.content`。**几乎所有兼容服务（国产模型、开源部署、网关聚合）都遵循这个形状**，学会一个等于学会一类。
 *   - **messages 的角色约定**：`system` 设定身份与规则（放最前，权重最高）；`user` 用户输入；`assistant` 模型自己说过的话（多轮对话时原样回传）。**角色顺序与内容拼接方式直接影响输出质量**。
 *   - **鉴权与网络细节**：API Key 走 `Authorization: Bearer sk-...` 头；密钥放环境变量/配置中心，**绝不进代码库**。Java 侧用 `HttpClient` + 手拼 JSON 或 Jackson——本课程示例全部用纯 JDK 模拟服务端，真实对接时替换 URL 即可。
 *   - **错误必须分类处理**：401 鉴权失败（配置问题）、429 限流（指数退避重试）、500/503 服务端问题（重试+降级）、400 参数错误（修代码，别重试）、超时（设置 connect/read 超时，LLM 生成慢，read 超时要放大到分钟级）。
 *   - **幂等与重试的边界**：重试只对"幂等且未开始计费"的失败安全。流式请求中断后的重试要考虑已发送给用户的部分（前端截断/续写）。**429 的退避要带抖动**（避免整批客户端同时重试形成二次洪峰）。
 *   - **成本意识从第一行代码开始**：每次请求的 token 计费 = 输入 + 输出。系统提示反复发送、把整本文档塞上下文，都是钱。**缓存（相同输入命中缓存）、压缩历史、RAG 只带相关片段**是三大省钱手段。
 *   - 易错点：没设 read 超时（默认 30s 打断长生成）；密钥提交进 git；重试没有指数退避（429 越打越凶）；把 user 输入原样拼进 system（提示注入，40-10 讲防护）；响应 JSON 不校验直接取字段（服务偶尔返回空 choices）。
 * ============================================================
 */

package com.heima.ch40;

import java.util.ArrayList;
import java.util.List;

// ==================== 示例代码 ====================

public class LlmApiCall {
    record Message(String role, String content) {
        String toJson() {
            return "{\"role\":\"" + role + "\",\"content\":\"" + content.replace("\"", "'") + "\"}";
        }
    }

    /** 模拟的 OpenAI 风格请求体 */
    static String buildRequest(String model, List<Message> messages, double temperature) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"model\":\"").append(model).append("\",\"temperature\":")
                .append(temperature).append(",\"messages\":[");
        for (int i = 0; i < messages.size(); i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append(messages.get(i).toJson());
        }
        sb.append("]}");
        return sb.toString();
    }

    /** 模拟服务端：校验请求形状，返回标准响应 */
    static String fakeServer(String requestBody) {
        if (!requestBody.contains("\"model\"")) {
            return "{\"error\":{\"code\":400,\"message\":\"model required\"}}";
        }
        return "{\"choices\":[{\"message\":{\"role\":\"assistant\","
                + "\"content\":\"根据您的订单号查询到：已发货，预计明天送达。\"}}],"
                + "\"usage\":{\"prompt_tokens\":86,\"completion_tokens\":24}}";
    }

    /** 客户端解析：先判错误结构，再取业务字段（顺序不能反） */
    static String parseResponse(String body) {
        if (body.contains("\"error\"")) {
            throw new IllegalStateException("服务端错误: " + body);
        }
        int contentKey = body.indexOf("\"content\":\"");
        int contentEnd = body.indexOf("\"}}", contentKey);
        return body.substring(contentKey + "\"content\":\"".length(), contentEnd);
    }

    static void requestResponse() {
        System.out.println("【一次完整的调用：构造 -> 发送 -> 解析】");
        List<Message> messages = new ArrayList<>();
        messages.add(new Message("system", "你是电商客服助手，回答简短，不确定就说不知道。"));
        messages.add(new Message("user", "订单 2026111701 到哪了？"));
        String req = buildRequest("gpt-4o-mini", messages, 0.2);
        System.out.println("  请求体：" + req);
        String resp = fakeServer(req);
        System.out.println("  响应体：" + resp);
        System.out.println("  提取的回复：" + parseResponse(resp));
        System.out.println();
    }

    static void errorTaxonomy() {
        System.out.println("【错误分类：先分类，再决定重试还是告警】");
        String[][] errors = {
                {"400", "参数错误", "修代码，不重试"},
                {"401/403", "鉴权失败", "检查密钥配置，告警"},
                {"429", "限流", "指数退避 + 抖动重试，必要时降级"},
                {"500/503", "服务端故障", "重试 2~3 次，仍失败走降级话术"},
                {"超时", "生成太慢/网络", "read 超时放大；流式场景断流续传"},
        };
        for (String[] e : errors) {
            System.out.printf("  %-8s %-12s %s%n", e[0], e[1], e[2]);
        }
        System.out.println();
    }

    static void retryPolicy() {
        System.out.println("【指数退避 + 抖动：429 的标准应对】");
        int[] delays = {200, 400, 800, 1600};
        for (int i = 0; i < delays.length; i++) {
            int jitter = (int) (Math.random() * delays[i] / 4);
            System.out.printf("  第 %d 次重试：基线 %5dms + 抖动 %4dms = %5dms%n",
                    i + 1, delays[i], jitter, delays[i] + jitter);
        }
        System.out.println("  退避上限封顶（如 10s），超次告警；抖动防止整批客户端同步重试");
    }

    public static void main(String[] args) {
        requestResponse();
        errorTaxonomy();
        retryPolicy();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 扩展 fakeServer 支持多轮：messages 里含 assistant 角色时，回复引用上一轮内容，验证 messages 的回传机制。
 * 2. 实现"带分类的重试客户端"：模拟 429/500/超时三种失败，分别执行退避重试/快速失败/降级话术，输出每种路径的决策日志。
 * 3. 思考题：为什么 read 超时要远大于 connect 超时？从 LLM"逐 token 生成"的特性推导合理的超时配置。
 * ============================================================
 */
