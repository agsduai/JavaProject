/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：42-07
 * 课名：分布式链路追踪
 * ------------------------------------------------------------
 * 知识点：
 *   - **微服务排障的根本困难是"一个请求的真相散落在 N 个服务的日志里"**：链路追踪把一次请求经过的所有服务记录成一棵 **Span 树**——根 Span 是用户请求，子 Span 是各下游调用，每个 Span 带服务名、耗时、状态、标签。**Trace = 请求的完整旅程，Span = 旅程中的一段**。
 *   - **上下文传播是追踪的地基**：入口生成 `trace_id` + `span_id`，每次下游调用把（trace_id, parent_span_id）放进 HTTP 头（W3C 标准头 `traceparent`）或 MQ 消息属性；服务端解析后继续生成子 span。**任何一环丢失传播（异步线程池、手写 HTTP 调用）就是链路断裂**，排查时只能靠时间猜。
 *   - **Span 的关键标签**：`peer`（调了谁）、`status`（ok/error）、错误详情、以及业务标签（orderId）。**耗时 + 状态 + 关系**三要素让"慢在哪一跳、错在哪一步"变成一次查询。
 *   - **追踪的价值场景**：慢请求定位（根 span 展开，最深的子 span 就是嫌疑人）、跨服务错误归属（哪层先抛的错）、容量分析（每个下游的扇出与扇入）、依赖地图（自动生成服务拓扑）。
 *   - **埋点的三个层次**：自动探针（agent 字节码增强，零代码侵入，覆盖 JDBC/HTTP/Redis 客户端）、SDK 显式埋点（框架 hook，需要少量代码）、手工埋点（业务关键段）。**生产以自动探针为主 + 关键业务手工补充**。
 *   - **追踪与日志/指标的联动**：Span 带上 trace_id 后，日志与指标都能按 trace 维度关联（42-01 的"通用钥匙"）。OpenTelemetry 正在统一三支柱的 API 与数据模型——**学概念比学具体组件重要，组件三年一换**。
 *   - 易错点：只在 HTTP 层埋点，JDBC/Redis/MQ 这类"真正慢的地方"没覆盖；异步线程池丢上下文（trace 断裂）；Span 里塞大对象（一个 span 几 MB，存储爆炸）；每跳都建 span 但不打耗时标签（有结构没信息）。
 * ============================================================
 */

package com.heima.ch42;

import java.util.ArrayList;
import java.util.List;

// ==================== 示例代码 ====================

public class TraceSpans {
    /** Span：一次请求中的一段工作 */
    record Span(String spanId, String parentSpanId, String service, String op,
                long startMs, long endMs, String status, String peer) {

        long costMs() {
            return endMs - startMs;
        }

        int depth() {
            int d = 0;
            String p = parentSpanId;
            // 本演示用简单缩进层级代替真实 parent 查找
            return d;
        }
    }

    static void buildTrace() {
        System.out.println("【一次下单请求的 Span 树】");
        List<Span> spans = List.of(
                new Span("s1", null, "gateway", "POST /order", 0, 1020, "ok", "-"),
                new Span("s2", "s1", "order", "createOrder", 5, 1010, "ok", "-"),
                new Span("s3", "s2", "order", "auth.check", 6, 12, "ok", "auth"),
                new Span("s4", "s2", "order", "db.insert", 13, 45, "ok", "mysql"),
                new Span("s5", "s2", "stock", "deduct", 46, 1000, "ok", "stock-rpc"),
                new Span("s6", "s5", "stock", "db.update", 50, 980, "ok", "mysql"));
        printTree(spans, null, 0);
        Span root = spans.get(0);
        System.out.printf("%n  总耗时 %dms，最深的子链：s5(stock.deduct %dms) -> s6(db.update %dms)%n",
                root.costMs(), spans.get(4).costMs(), spans.get(5).costMs());
        System.out.println("  结论：90% 的时间耗在库存服务的 DB 更新 —— 下一步优化方向明确");
        System.out.println();
    }

    private static void printTree(List<Span> spans, String parent, int indent) {
        for (Span s : spans) {
            if ((parent == null) == (s.parentSpanId() == null) && (parent == null || parent.equals(s.parentSpanId()))) {
                String tree = "  ".repeat(indent) + (indent > 0 ? "└─ " : "");
                System.out.printf("  %s%-10s %-16s %-12s %4dms [%s] peer=%s%n",
                        tree, s.service(), s.op(), s.spanId(), s.costMs(), s.status(), s.peer());
                printTree(spans, s.spanId(), indent + 1);
            }
        }
    }

    static void propagation() {
        System.out.println("【上下文传播：traceparent 头与断裂点】");
        String header = "traceparent: 00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";
        System.out.println("  W3C 格式：" + header);
        System.out.println("  解析：version=00 trace_id=4bf9...4736 parent_span=00f0...02b7 flags=01");
        List<String> breakPoints = List.of(
                "异步线程池：Runnable 里直接执行 -> 上下文没拷贝 -> 断裂（用装饰器修复）",
                "手写 HttpClient：没把头复制进新请求 -> 断裂",
                "MQ 消费：生产者没写消息属性 -> 断裂",
                "定时任务入口：没有 trace -> 应新建根 span");
        for (String b : breakPoints) {
            System.out.println("  断裂点：" + b);
        }
        System.out.println();
    }

    static void whatTraceSolves() {
        System.out.println("【追踪解决的四个排障问题】");
        List<String> items = List.of(
                "慢请求：展开 span 树，最深子链即嫌疑人（本章示例：库存 DB）",
                "错误归属：从根到叶找第一个 error 状态的 span",
                "扇出分析：根 span 的直接子 span 数 = 每请求下游调用量",
                "依赖地图：按 (caller, callee) 聚合 span 自动生成拓扑");
        for (String s : items) {
            System.out.println("  - " + s);
        }
    }

    public static void main(String[] args) {
        buildTrace();
        propagation();
        whatTraceSolves();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 用 OpenTelemetry SDK（或手写 Span 类）为一条两层调用链建 span 树，故意在异步线程里丢上下文，观察断裂现象后修复。
 * 2. 实现链路分析器：输入 span 列表，输出"最耗时子链"与"第一个 error 的 span"，用一次慢请求样本验证。
 * 3. 思考题：为什么说"学追踪概念比学组件重要"？对比 Zipkin/Jaeger/SkyWalking 的数据模型与 OpenTelemetry 的标准化方向。
 * ============================================================
 */
