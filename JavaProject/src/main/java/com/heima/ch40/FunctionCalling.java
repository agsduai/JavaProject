/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：40-06
 * 课名：Function Calling：让模型调用你的代码
 * ------------------------------------------------------------
 * 知识点：
 *   - **模型不会执行代码，它只是"决定该调用什么工具"**：你在请求里声明工具清单（名称、参数 schema、描述），模型分析用户输入后输出**调用意图**（工具名 + JSON 参数）；**真正执行的是你的 Java 代码**，执行结果作为 tool 角色消息回传，模型基于结果继续生成回答。这个循环就是 Agent 的最小形态。
 *   - **工具定义的三个字段决定调用准确率**：name（动词+名词，如 query_order_status）、description（一句话说清什么时候该用/不该用，模型靠它选择）、parameters（JSON Schema，字段描述越具体，参数填得越对）。**description 是模型选择工具的唯一依据**，写得含糊模型就乱选。
 *   - **执行循环是标准的四步**：用户输入 → 模型返回 tool_calls → 你的代码执行并把结果以 `{"role":"tool","tool_call_id":...}` 回传 → 模型基于结果生成最终回复。**一次用户输入可能触发多次工具调用**（先查订单再查物流），要循环直到模型输出普通文本。
 *   - **安全边界**：模型决定的参数**必须校验**——金额、地址、数量等敏感参数要做业务校验（地址必须属于该用户、退款金额不超过实付）；高危操作（退款、改手机号）要二次确认或白名单用户。**模型是决策建议者，权限系统才是执行者**。
 *   - **工具结果要精炼**：回传结果不是数据库原始行——挑模型需要的字段、截断长文本、失败时返回结构化错误（{"error":"order_not_found"}）让模型能解释。塞一个大 JSON 进上下文又贵又降质量。
 *   - **并行与串行**：相互独立的调用可并行（查物流+查售后政策）；有依赖的必须串行（先拿订单号才能查物流）。多数框架支持并行 tool_calls，自己实现时按 schema 标注依赖。
 *   - 易错点：工具 description 与另一个工具边界不清（模型 50/50 乱选）；参数不做校验直接执行（提示注入改退款金额）；工具执行异常抛给模型（应返回结构化错误让模型说人话）；循环无上限（模型反复调工具直到上下文爆炸，要设最大轮次）。
 * ============================================================
 */

package com.heima.ch40;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

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

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 扩展到两次工具调用链：用户问"订单到哪了，出了问题怎么退款"，模型先 query_order 再 process_refund，实现循环执行并保留 messages 中的 tool 配对消息。
 * 2. 实现循环护栏：最大轮次 5、同参数重复检测、超时 3s，用模拟的"固执模型"（反复调同一工具）验证护栏生效。
 * 3. 思考题：为什么"工具结果精炼回传"既省钱又提升质量？从上下文长度与注意力两个角度分析。
 * ============================================================
 */
