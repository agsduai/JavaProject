### 40-04 流式输出与 SSE 协议

**知识点**
- **为什么要流式**：LLM 逐 token 生成，一个完整回答可能要 5~30 秒。非流式让用户盯着白屏等到超时；流式让首 token 1 秒内出现，**感知延迟下降一个数量级**。ChatGPT 的"打字机效果"就是流式。
- **SSE（Server-Sent Events）是流式的标准载体**：HTTP 长连接 + `Content-Type: text/event-stream`，服务端持续推送 `data: ...` 行、每个事件空行分隔。相比 WebSocket：单向（服务端→客户端）、基于纯 HTTP（过网关/代理友好）、自动重连带 Last-Event-ID。**LLM 场景不需要双向**，SSE 足够。
- **OpenAI 风格的流式协议**：每个 chunk 是 `data: {"choices":[{"delta":{"content":"你"}}]}`，内容增量放 `delta` 而不是 `message`；结束事件是 `data: [DONE]`。客户端逐块解析 `delta.content` 追加到界面。
- **Java 服务端实现要点**：响应头 `text/event-stream` + `Cache-Control: no-cache` + `Connection: keep-alive`；**关闭响应缓冲**（Servlet 的 flushBuffer / WebFlux 直接是 Flux<String>）；每个事件写完必须 flush，否则攒在缓冲区等于没流式。
- **客户端消费的三个坑**：① 按行读但事件以**空行**结束（不能读到 `\n` 就处理）；② 心跳注释行 `: ping` 要忽略（很多网关用它防中间设备断连）；③ 断线重连要带 `Last-Event-ID` 头，服务端可从断点续推。
- **生产化的额外话题**：超时与断流（生成中断时前端要能感知并给"继续"按钮）；多租户限流（流式连接占资源更久）；审计（流式内容也要落日志，逐块拼接后整体记录）；降级（流式不可用时回退到非流式轮询）。
- 易错点：网关开了响应缓冲/压缩导致"看起来还是一次出"；客户端把 SSE 当按行 JSON 解析（事件可以跨多行）；忘记处理 [DONE]（连接挂着直到超时）；异常时直接关流不给客户端终态事件（前端转圈到天荒地老）。

**示例代码**
```java
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;

public class SseStreaming {
    /** 服务端：把模型的 token 序列包装成 SSE 事件流 */
    static String[] modelTokens() {
        return new String[]{"您好", "，", "您的订单", "已发货", "，", "预计", "明天", "送达", "。"};
    }

    static String formatEvent(String data) {
        return "data: " + data + "\n\n";
    }

    static void serverSide() {
        System.out.println("【服务端推流：事件格式与 [DONE] 终止】");
        String headers = "HTTP/1.1 200 OK\n"
                + "Content-Type: text/event-stream\n"
                + "Cache-Control: no-cache\n"
                + "Connection: keep-alive\n";
        System.out.println(headers.replace("\n", "\n  "));
        StringBuilder wire = new StringBuilder();
        for (String token : modelTokens()) {
            String chunk = "{\"choices\":[{\"delta\":{\"content\":\"" + token + "\"}}]}";
            wire.append(formatEvent(chunk));
        }
        wire.append(formatEvent("[DONE]"));
        System.out.println("  --- 事件流（\\n\\n 为事件分隔） ---");
        System.out.println(wire.toString().replace("\n", "\n  "));
        System.out.println();
    }

    /** 客户端：按“空行分隔的事件”解析，而不是按行 */
    static void clientSide() throws IOException {
        System.out.println("【客户端解析：按事件读、忽略注释、识别 DONE】");
        StringBuilder wire = new StringBuilder();
        wire.append(": ping\n\n");        // 心跳注释行
        for (String token : modelTokens()) {
            wire.append(formatEvent("{\"choices\":[{\"delta\":{\"content\":\"" + token + "\"}}]}"));
        }
        wire.append(formatEvent("[DONE]"));
        BufferedReader reader = new BufferedReader(new StringReader(wire.toString()));
        StringBuilder answer = new StringBuilder();
        StringBuilder event = new StringBuilder();
        String line;
        boolean done = false;
        while ((line = reader.readLine()) != null) {
            if (line.isEmpty()) {                       // 空行 = 事件结束
                String ev = event.toString();
                event.setLength(0);
                if (ev.startsWith(":")) {
                    continue;                            // 心跳注释，忽略
                }
                if (ev.startsWith("data: ")) {
                    String payload = ev.substring(6);
                    if (payload.equals("[DONE]")) {
                        done = true;
                        break;
                    }
                    answer.append(extractDelta(payload));
                }
            } else {
                event.append(line);
            }
        }
        System.out.println("  拼接出的回答：" + answer);
        System.out.println("  收到 [DONE]：" + done + " -> 主动关闭连接，更新对话历史");
        System.out.println();
    }

    static String extractDelta(String json) {
        String key = "\"content\":\"";
        int start = json.indexOf(key);
        int end = json.indexOf("\"}}", start);
        return json.substring(start + key.length(), end);
    }

    static void pitfalls() {
        System.out.println("【生产四个坑】");
        String[][] items = {
                {"网关缓冲", "Nginx 默认 proxy_buffering on 会攒包 -> 加 X-Accel-Buffering: no"},
                {"异常无终态", "服务端出错也要发一个 error 事件再关流，别让前端转圈"},
                {"审计", "流式内容逐块落盘困难 -> 服务端拼接完整回答后整体入库"},
                {"心跳", "长时间无 token 时推 : ping，防止 LB/代理掐断空闲连接"},
        };
        for (String[] i : items) {
            System.out.printf("  %-8s %s%n", i[0], i[1]);
        }
    }

    public static void main(String[] args) throws IOException {
        serverSide();
        clientSide();
        pitfalls();
    }
}
```

**课后练习**
1. 用 JDK `com.sun.net.httpserver` 写一个真实的 SSE 端点（逐 100ms 推一个 token），用 `HttpClient` 的 BodySubscribers.ofLines 消费，验证打字机效果。
2. 实现断线重连：事件带递增 id，客户端断开后带 Last-Event-ID 请求，服务端从断点继续推，验证无丢失。
3. 思考题：为什么 LLM 场景选 SSE 而不是 WebSocket？列出 SSE 不适用的一个真实场景。

---

### 40-05 多轮对话与上下文管理

**知识点**
- **多轮对话的内存结构就是 messages 数组**：每轮把新增的 user 消息与 assistant 回复 append 进去，下次请求整体发送。**历史长度 = token 成本线性增长 + 终将超窗口**，上下文管理是长对话系统的核心工程问题。
- **三种裁剪策略**：① **滑动窗口**（保留最近 N 轮，最简单，丢早期信息）；② **摘要压缩**（早期对话让模型总结成一段塞进 system 或开头，细节丢失要点保留）；③ **混合**（系统提示 + 长期摘要 + 最近 K 轮原文）。客服/助手类产品几乎都用混合。
- **什么必须永远保留**：用户的核心属性（姓名、偏好、当前业务单号）在对话中一旦出现就要持续在上下文里——通常做成**槽位（slot）**单独维护，不依赖对话历史。槽位是"结构化记忆"，比"祈祷模型记住第 3 轮说过的话"可靠。
- **截断的边界感**：裁剪按轮次不按字符——半截对话（user 有 assistant 无）会破坏模型的对话结构感；系统提示永远不裁；工具调用轮（40-06）的配对消息要么全保要么全删。
- **会话存储**：按 sessionId 存 Redis（TTL 对齐会话时长），序列化 messages + 槽位。**上下文不是越全越好**——不相关的长历史会稀释注意力（模型更容易"被带偏"），摘要压缩反而提升回答质量。
- **上下文更新的一致性**： assistant 回复失败/中断时历史如何记（记 user + 记错误占位，不能让下一轮结构残缺）；并发同 session 消息要串行处理。
- 易错点：历史无限增长直到 400 报错；截断恰好截掉"当前业务单号"导致答非所问（槽位化可避免）；把工具调用的中间消息残留在历史里（下次请求 400 invalid messages）；摘要由低质模型生成导致关键信息丢失（摘要 Prompt 也要测试）。

**示例代码**
```java
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ChatMemory {
    record Msg(String role, String content) {
    }

    static class Session {
        final List<Msg> system = new ArrayList<>();
        final Map<String, String> slots = new LinkedHashMap<>();     // 结构化记忆
        final Deque<Msg> recent = new ArrayDeque<>();                // 最近 K 轮
        String summary = "";                                         // 长期摘要
        int maxRecent = 4;

        List<Msg> buildContext() {
            List<Msg> ctx = new ArrayList<>(system);
            if (!summary.isEmpty()) {
                ctx.add(new Msg("system", "历史摘要：" + summary));
            }
            ctx.addAll(recent);
            return ctx;
        }

        void addUser(String text) {
            recent.add(new Msg("user", text));
            while (recent.size() > maxRecent) {
                Msg oldest = recent.pollFirst();
                summary = compact(summary, oldest.content());
            }
        }

        void addAssistant(String text) {
            recent.add(new Msg("assistant", text));
            while (recent.size() > maxRecent) {
                summary = compact(summary, recent.pollFirst().content());
            }
        }

        String compact(String old, String text) {
            return (old.isEmpty() ? "" : old + " ") + text.substring(0, Math.min(12, text.length())) + "…";
        }
    }

    static void contextGrowth() {
        System.out.println("【上下文随轮次线性增长：必须管理】");
        int[] turns = {5, 20, 50};
        for (int t : turns) {
            double tokens = t * 120;      // 每轮约 120 token
            System.out.printf("  %2d 轮对话 ≈ %6.0f token（无管理时成本与长度同步涨）%n", t, tokens);
        }
        System.out.println("  裁剪策略：滑动窗口保最近轮 + 摘要保早期信息 + 槽位保关键事实");
        System.out.println();
    }

    static void sessionDemo() {
        System.out.println("【一个会话的上下文构建过程】");
        Session s = new Session();
        s.system.add(new Msg("system", "你是客服助手。用户资料以【用户资料】为准。"));
        s.slots.put("用户", "小王");
        s.slots.put("当前订单", "2026111701");
        s.addUser("我的订单 2026111701 到哪了？");
        s.addAssistant("您的订单已发货，预计明天送达。");
        s.addUser("换货的话地址怎么填？");
        s.addAssistant("换货地址请填您下单时的收货地址。");
        s.addUser("那退货运费谁出？");
        s.addAssistant("质量问题退货运费我们承担。");
        s.addUser("好的，那什么时候到账？");
        System.out.println("  槽位（永远注入 system）：" + s.slots);
        System.out.println("  摘要（早期轮压缩）：" + (s.summary.isEmpty() ? "（空）" : s.summary));
        List<Msg> ctx = s.buildContext();
        System.out.println("  最终发送的 messages：");
        for (Msg m : ctx) {
            System.out.printf("    [%s] %s%n", m.role(), m.content());
        }
        System.out.println();
    }

    static void truncationRules() {
        System.out.println("【裁剪的边界规则】");
        List<String> rules = List.of(
                "系统提示永不裁剪",
                "按完整轮次裁（user+assistant 成对），不留半轮",
                "工具调用消息与其响应必须同进同出（40-06）",
                "槽位信息脱离历史单独注入，防止被裁掉",
                "摘要压缩后抽查关键事实是否还在（订单号/金额）");
        for (String r : rules) {
            System.out.println("  - " + r);
        }
    }

    public static void main(String[] args) {
        contextGrowth();
        sessionDemo();
        truncationRules();
    }
}
```

**课后练习**
1. 实现"混合策略"的完整会话管理（槽位 + 摘要 + 滑动窗口），模拟 30 轮对话，打印每轮实际发送的 messages 总 token 数曲线。
2. 制造"截断丢关键信息"事故：把"订单号在第 2 轮"的对话裁剪到只留最近 2 轮，观察第 8 轮"我的订单到哪了"如何答非所问，再用槽位修复。
3. 思考题：为什么摘要压缩可能反而提升回答质量？从"注意力稀释"的角度分析超长历史的负面影响。

---

### 40-06 Function Calling：让模型调用你的代码

**知识点**
- **模型不会执行代码，它只是"决定该调用什么工具"**：你在请求里声明工具清单（名称、参数 schema、描述），模型分析用户输入后输出**调用意图**（工具名 + JSON 参数）；**真正执行的是你的 Java 代码**，执行结果作为 tool 角色消息回传，模型基于结果继续生成回答。这个循环就是 Agent 的最小形态。
- **工具定义的三个字段决定调用准确率**：name（动词+名词，如 query_order_status）、description（一句话说清什么时候该用/不该用，模型靠它选择）、parameters（JSON Schema，字段描述越具体，参数填得越对）。**description 是模型选择工具的唯一依据**，写得含糊模型就乱选。
- **执行循环是标准的四步**：用户输入 → 模型返回 tool_calls → 你的代码执行并把结果以 `{"role":"tool","tool_call_id":...}` 回传 → 模型基于结果生成最终回复。**一次用户输入可能触发多次工具调用**（先查订单再查物流），要循环直到模型输出普通文本。
- **安全边界**：模型决定的参数**必须校验**——金额、地址、数量等敏感参数要做业务校验（地址必须属于该用户、退款金额不超过实付）；高危操作（退款、改手机号）要二次确认或白名单用户。**模型是决策建议者，权限系统才是执行者**。
- **工具结果要精炼**：回传结果不是数据库原始行——挑模型需要的字段、截断长文本、失败时返回结构化错误（{"error":"order_not_found"}）让模型能解释。塞一个大 JSON 进上下文又贵又降质量。
- **并行与串行**：相互独立的调用可并行（查物流+查售后政策）；有依赖的必须串行（先拿订单号才能查物流）。多数框架支持并行 tool_calls，自己实现时按 schema 标注依赖。
- 易错点：工具 description 与另一个工具边界不清（模型 50/50 乱选）；参数不做校验直接执行（提示注入改退款金额）；工具执行异常抛给模型（应返回结构化错误让模型说人话）；循环无上限（模型反复调工具直到上下文爆炸，要设最大轮次）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class FunctionCalling {
    /** 工具清单：description 是模型选择的唯一依据 */
    record Tool(String name, String description, String paramsSchema) {
        String toJson() {
            return "{\"name\":\"" + name + "\",\"description\":\"" + description
                    + "\",\"parameters\":" + paramsSchema + "}";
        }
    }

    static final List<Tool> TOOLS = List.of(
            new Tool("query_order", "查询指定订单号的状态与物流信息。当用户询问订单进展时使用。",
                    "{\"orderId\":{\"type\":\"string\"}}"),
            new Tool("process_refund", "为订单发起退款。仅在用户明确要求退款且订单状态为已支付时使用。",
                    "{\"orderId\":{\"type\":\"string\"},\"amount\":{\"type\":\"number\"}}"));

    /** 模拟模型决策：根据用户输入选择工具与参数 */
    static record ToolCall(String tool, Map<String, Object> args) {
    }

    static ToolCall modelDecide(String userInput) {
        if (userInput.contains("到哪了") || userInput.contains("物流")) {
            Map<String, Object> args = new LinkedHashMap<>();
            args.put("orderId", extractOrderId(userInput));
            return new ToolCall("query_order", args);
        }
        if (userInput.contains("退款")) {
            Map<String, Object> args = new LinkedHashMap<>();
            args.put("orderId", extractOrderId(userInput));
            args.put("amount", 69.0);
            return new ToolCall("process_refund", args);
        }
        return null;    // 无需工具，直接回答
    }

    static String extractOrderId(String text) {
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (Character.isDigit(c)) {
                sb.append(c);
            }
        }
        return sb.length() >= 8 ? sb.toString() : "unknown";
    }

    /** Java 侧真实执行 + 业务校验（模型只是建议者） */
    static String execute(ToolCall call) {
        return switch (call.tool()) {
            case "query_order" -> {
                String id = (String) call.args().get("orderId");
                yield "unknown".equals(id)
                        ? "{\"error\":\"order_id_missing\"}"
                        : "{\"orderId\":\"" + id + "\",\"status\":\"已发货\",\"carrier\":\"顺丰\"}";
            }
            case "process_refund" -> {
                double amount = (double) call.args().get("amount");
                String id = (String) call.args().get("orderId");
                // 安全校验：金额不能超过该订单实付（示例硬编码 69）
                if (amount > 69.0) {
                    yield "{\"error\":\"amount_exceeds_paid\",\"paid\":69.0}";
                }
                if ("unknown".equals(id)) {
                    yield "{\"error\":\"order_id_missing\"}";
                }
                yield "{\"accepted\":true,\"refundId\":\"RF" + id + "\",\"eta\":\"1-3天\"}";
            }
            default -> "{\"error\":\"unknown_tool\"}";
        };
    }

    static void loop() {
        System.out.println("【一次带工具调用的完整循环】");
        System.out.println("  注册工具：" + TOOLS.get(0).toJson());
        System.out.println();
        String user = "我的订单 2026111701 到哪了？";
        System.out.println("  user: " + user);
        ToolCall call = modelDecide(user);
        System.out.println("  model: 需要调用工具 -> " + call.tool() + " args=" + call.args());
        String result = execute(call);
        System.out.println("  java 执行结果（tool 消息回传）：" + result);
        System.out.println("  model 最终回复：您的订单已发货，承运商顺丰，预计明天送达。");
        System.out.println();
    }

    static void safetyCheck() {
        System.out.println("【模型给出的参数必须过业务校验】");
        ToolCall injected = new ToolCall("process_refund",
                Map.of("orderId", "2026111701", "amount", 6900.0));
        System.out.println("  模型输出：process_refund amount=" + injected.args().get("amount"));
        String result = execute(injected);
        System.out.println("  执行结果：" + result);
        System.out.println("  攻击路径：提示注入让模型填一个大额参数 -> 业务校验拦截（金额>实付69）");
        System.out.println("  原则：金额/地址/手机号等敏感参数以数据库为准，模型参数只做参考");
        System.out.println();
    }

    static void loopGuard() {
        System.out.println("【循环护栏】");
        List<String> guards = List.of(
                "最大工具轮次（如 5 次），超限生成兜底回复",
                "同一工具同一参数连续重复 -> 直接终止（防死循环）",
                "工具执行超时 -> 返回结构化错误，不无限等",
                "执行异常 -> {\"error\":...} 回传让模型解释，绝不把堆栈给用户");
        for (String g : guards) {
            System.out.println("  - " + g);
        }
    }

    public static void main(String[] args) {
        loop();
        safetyCheck();
        loopGuard();
    }
}
```

**课后练习**
1. 扩展到两次工具调用链：用户问"订单到哪了，出了问题怎么退款"，模型先 query_order 再 process_refund，实现循环执行并保留 messages 中的 tool 配对消息。
2. 实现循环护栏：最大轮次 5、同参数重复检测、超时 3s，用模拟的"固执模型"（反复调同一工具）验证护栏生效。
3. 思考题：为什么"工具结果精炼回传"既省钱又提升质量？从上下文长度与注意力两个角度分析。
