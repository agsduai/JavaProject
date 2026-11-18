/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：40-04
 * 课名：流式输出与 SSE 协议
 * ------------------------------------------------------------
 * 知识点：
 *   - **为什么要流式**：LLM 逐 token 生成，一个完整回答可能要 5~30 秒。非流式让用户盯着白屏等到超时；流式让首 token 1 秒内出现，**感知延迟下降一个数量级**。ChatGPT 的"打字机效果"就是流式。
 *   - **SSE（Server-Sent Events）是流式的标准载体**：HTTP 长连接 + `Content-Type: text/event-stream`，服务端持续推送 `data: ...` 行、每个事件空行分隔。相比 WebSocket：单向（服务端→客户端）、基于纯 HTTP（过网关/代理友好）、自动重连带 Last-Event-ID。**LLM 场景不需要双向**，SSE 足够。
 *   - **OpenAI 风格的流式协议**：每个 chunk 是 `data: {"choices":[{"delta":{"content":"你"}}]}`，内容增量放 `delta` 而不是 `message`；结束事件是 `data: [DONE]`。客户端逐块解析 `delta.content` 追加到界面。
 *   - **Java 服务端实现要点**：响应头 `text/event-stream` + `Cache-Control: no-cache` + `Connection: keep-alive`；**关闭响应缓冲**（Servlet 的 flushBuffer / WebFlux 直接是 Flux<String>）；每个事件写完必须 flush，否则攒在缓冲区等于没流式。
 *   - **客户端消费的三个坑**：① 按行读但事件以**空行**结束（不能读到 `\n` 就处理）；② 心跳注释行 `: ping` 要忽略（很多网关用它防中间设备断连）；③ 断线重连要带 `Last-Event-ID` 头，服务端可从断点续推。
 *   - **生产化的额外话题**：超时与断流（生成中断时前端要能感知并给"继续"按钮）；多租户限流（流式连接占资源更久）；审计（流式内容也要落日志，逐块拼接后整体记录）；降级（流式不可用时回退到非流式轮询）。
 *   - 易错点：网关开了响应缓冲/压缩导致"看起来还是一次出"；客户端把 SSE 当按行 JSON 解析（事件可以跨多行）；忘记处理 [DONE]（连接挂着直到超时）；异常时直接关流不给客户端终态事件（前端转圈到天荒地老）。
 * ============================================================
 */

package com.heima.ch40;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;

// ==================== 示例代码 ====================

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

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 用 JDK `com.sun.net.httpserver` 写一个真实的 SSE 端点（逐 100ms 推一个 token），用 `HttpClient` 的 BodySubscribers.ofLines 消费，验证打字机效果。
 * 2. 实现断线重连：事件带递增 id，客户端断开后带 Last-Event-ID 请求，服务端从断点继续推，验证无丢失。
 * 3. 思考题：为什么 LLM 场景选 SSE 而不是 WebSocket？列出 SSE 不适用的一个真实场景。
 * ============================================================
 */
