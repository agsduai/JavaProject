/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：35-05
 * 课名：HTTP/1.1 报文格式与状态码
 * ------------------------------------------------------------
 * 知识点：
 *   - **报文结构**：请求 = 请求行（`GET /path HTTP/1.1`）+ 头部（`Key: Value`，每行一个）+ 空行 + 可选 body。响应 = 状态行（`HTTP/1.1 200 OK`）+ 头部 + 空行 + body。**空行是分界线，不能省**。
 *   - **方法语义**：`GET` 查（安全、幂等）、`POST` 提交（不安全、不幂等）、`PUT` 全量替换（幂等）、`PATCH` 局部更新（不保证幂等）、`DELETE` 删除（幂等）、`HEAD` 只要头部、`OPTIONS` 探测能力（CORS 预检就用它）。
 *   - **幂等的实际意义**：网络超时后客户端敢不敢重试。`GET`/`PUT`/`DELETE` 重试一次结果一样；`POST` 重试可能下两单，所以要么加幂等键，要么别自动重试。
 *   - **状态码分五类**：`1xx` 信息（`101 Switching Protocols` 升级 WebSocket）、`2xx` 成功（`200`、`201 Created`、`204 No Content`、`206 Partial Content`）、`3xx` 重定向（`301` 永久、`302` 临时、`304 Not Modified`、`307/308` 保持方法与 body）、`4xx` 客户端错（`400`、`401` 未认证、`403` 无权限、`404`、`405` 方法不允许、`409` 冲突、`429` 限流）、`5xx` 服务端错（`500`、`502` 网关收到坏响应、`503` 不可用、`504` 网关超时）。
 *   - **`401` 与 `403` 的区别**：401 是「你是谁我不知道」，要带 `WWW-Authenticate`；403 是「我知道你是谁，但你不能干这个」。搞混会让前端不知道该跳登录还是提示无权限。
 *   - **`301` 与 `302` 的代价**：301 会被浏览器长期缓存，改回来很难；不确定时用 302/307。`307`/`308` 保证重定向后方法和 body 不变，`301`/`302` 历史上会把 POST 改成 GET。
 *   - **`Host` 头是 HTTP/1.1 强制的**：一台 IP 上跑多个站点靠它区分虚拟主机。缺了它服务器只能回 400。
 *   - **`Content-Length` 与 `Transfer-Encoding: chunked` 二选一**：知道长度就用前者；边生成边发（大文件、流式响应）用后者，每块前面写十六进制长度，最后以 `0` 长度块结束。两个同时出现是请求走私的经典入口，服务端必须拒绝。
 *   - **`Connection: keep-alive`** 让一条 TCP 连接跑多个请求，省掉重复握手；HTTP/1.1 默认开启。但一条连接同一时刻只能有一个在途请求，这就是**应用层队头阻塞**。
 *   - 易错点：把 `204` 当 `200` 用还带 body（规范禁止）；用 `200` 返回错误信息（监控和网关都识别不了）；`Content-Length` 写成字符数而不是字节数（中文会截断）。
 * ============================================================
 */

package com.heima.ch35;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// ==================== 示例代码 ====================

public class HttpMessage {
    /** 解析后的请求：方法、路径、版本、头部（大小写不敏感）、body */
    record Request(String method, String path, String version,
                   Map<String, String> headers, String body) {
        String header(String name) {
            return headers.get(name.toLowerCase(Locale.ROOT));
        }
    }

    static Request parseRequest(String raw) {
        int split = raw.indexOf("\r\n\r\n");
        String head = split < 0 ? raw : raw.substring(0, split);
        String body = split < 0 ? "" : raw.substring(split + 4);
        String[] lines = head.split("\r\n");
        String[] first = lines[0].split(" ");
        Map<String, String> headers = new LinkedHashMap<>();
        for (int i = 1; i < lines.length; i++) {
            int c = lines[i].indexOf(':');
            if (c > 0) {
                headers.put(lines[i].substring(0, c).trim().toLowerCase(Locale.ROOT),
                        lines[i].substring(c + 1).trim());
            }
        }
        return new Request(first[0], first[1], first[2], headers, body);
    }

    static void parseDemo() {
        String raw = "POST /api/orders?from=web HTTP/1.1\r\n"
                + "Host: shop.example.com\r\n"
                + "Content-Type: application/json; charset=utf-8\r\n"
                + "Content-Length: 22\r\n"
                + "Connection: keep-alive\r\n"
                + "\r\n"
                + "{\"sku\":\"A1\",\"count\":2}";
        Request req = parseRequest(raw);
        System.out.println("【解析请求报文】");
        System.out.printf("  请求行：method=%s path=%s version=%s%n", req.method(), req.path(), req.version());
        System.out.println("  头部：");
        req.headers().forEach((k, v) -> System.out.printf("    %-14s = %s%n", k, v));
        System.out.printf("  body：%s（%d 字节）%n",
                req.body(), req.body().getBytes(StandardCharsets.UTF_8).length);

        int declared = Integer.parseInt(req.header("content-length"));
        int actual = req.body().getBytes(StandardCharsets.UTF_8).length;
        System.out.printf("  Content-Length=%d，实际=%d -> %s%n",
                declared, actual, declared == actual ? "一致" : "不一致，服务端应回 400");
        System.out.println();
    }

    static void lengthVsChunked() {
        System.out.println("【两种 body 定界方式】");
        System.out.println("  Content-Length: 22   一次说清总长度，适合已知大小的响应");
        String chunked = "HTTP/1.1 200 OK\r\n"
                + "Transfer-Encoding: chunked\r\n"
                + "\r\n"
                + "7\r\n{\"name\"\r\n"
                + "6\r\n:\"张三\"\r\n"
                + "1\r\n}\r\n"
                + "0\r\n\r\n";
        System.out.println("  Transfer-Encoding: chunked  边算边发，每块前缀是十六进制长度");
        System.out.println("  原始字节（按行展示，行尾是 CRLF）：");
        for (String line : chunked.split("\r\n")) {
            System.out.println("    | " + line);
        }
        System.out.println("  以 0 长度块结束。两个头同时出现必须拒绝 —— 这是请求走私的入口");
        System.out.println();
    }

    static String reason(int code) {
        return switch (code) {
            case 101 -> "Switching Protocols";
            case 200 -> "OK";
            case 201 -> "Created";
            case 204 -> "No Content";
            case 206 -> "Partial Content";
            case 301 -> "Moved Permanently";
            case 302 -> "Found";
            case 304 -> "Not Modified";
            case 307 -> "Temporary Redirect";
            case 400 -> "Bad Request";
            case 401 -> "Unauthorized";
            case 403 -> "Forbidden";
            case 404 -> "Not Found";
            case 405 -> "Method Not Allowed";
            case 409 -> "Conflict";
            case 429 -> "Too Many Requests";
            case 500 -> "Internal Server Error";
            case 502 -> "Bad Gateway";
            case 503 -> "Service Unavailable";
            case 504 -> "Gateway Timeout";
            default -> "Unknown";
        };
    }

    static String meaning(int code) {
        return switch (code / 100) {
            case 1 -> "信息性：协议切换或继续发送";
            case 2 -> "成功：请求已被接受并处理";
            case 3 -> "重定向：需要去另一个地址取资源";
            case 4 -> "客户端错误：请求本身有问题，重试无用";
            case 5 -> "服务端错误：服务自身或上游出问题";
            default -> "";
        };
    }

    static void statusTable() {
        System.out.println("【状态码：记住语义比背全表重要】");
        int[] codes = {101, 200, 201, 204, 206, 301, 302, 304, 307,
                400, 401, 403, 404, 405, 409, 429, 500, 502, 503, 504};
        for (int c : codes) {
            System.out.printf("  %d %-20s %s%n", c, reason(c), meaning(c));
        }
        System.out.println();
    }

    static void idempotentRetry() {
        System.out.println("【超时之后敢不敢重试】");
        String[][] cases = {
                {"GET", "/orders/1001", "true", "安全且幂等，重试无副作用"},
                {"PUT", "/orders/1001", "true", "整体替换，重试结果相同"},
                {"DELETE", "/orders/1001", "true", "删两次和删一次结果一样"},
                {"POST", "/orders", "false", "重试可能创建两笔订单，需幂等键"},
                {"PATCH", "/orders/1001", "false", "增量更新重试会叠加"},
        };
        for (String[] c : cases) {
            System.out.printf("  %-7s %-14s 幂等=%-5s %s%n", c[0], c[1], c[2], c[3]);
        }
        System.out.println("  所以网关重试只对幂等方法开放；POST 要靠 Idempotency-Key 兜底");
        System.out.println();
    }

    static void keepAliveHOL() {
        System.out.println("【keep-alive 省了什么，又卡在哪】");
        System.out.println("  省：每次请求不再付 TCP 三次握手 + 慢启动的代价");
        System.out.println("  卡：一条连接同一时刻只能有一个在途请求");
        List<String> queue = new ArrayList<>(List.of("请求A(慢)", "请求B", "请求C"));
        System.out.println("  连接上排队的请求：" + queue);
        System.out.println("  请求A 处理了 800 ms，B 和 C 只能干等 —— 应用层队头阻塞");
        System.out.println("  浏览器对策：对同一域名开 6 条连接（所以域名分片曾经有用）");
        System.out.println("  协议对策：HTTP/2 多路复用，把请求拆成帧交错发送");
        System.out.println();
    }

    static void commonMistakes() {
        System.out.println("【三个高频坑】");
        System.out.println("  1) 204 带 body：规范禁止，部分客户端会读到脏数据");
        System.out.println("  2) 业务错误回 200：网关、监控、重试策略全部失效，应该用 4xx/5xx");
        System.out.println("  3) Content-Length 用 String.length()：中文按字节算，UTF-8 下「张三」是 6 字节不是 2");
        System.out.printf("     验证：\"张三\".length()=%d，UTF-8 字节数=%d%n",
                "张三".length(), "张三".getBytes(StandardCharsets.UTF_8).length);
        System.out.println();
    }

    public static void main(String[] args) {
        parseDemo();
        lengthVsChunked();
        statusTable();
        idempotentRetry();
        keepAliveHOL();
        commonMistakes();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 给 `parseRequest` 加上对 `Transfer-Encoding: chunked` 的解码，把分块 body 拼成完整字符串并校验结尾是否为 0 长度块。
 * 2. 实现 `parseResponse(String raw)`，解析状态行与头部，并对 3xx 响应检查是否带 `Location`。
 * 3. 写一个 `validateRequest(Request req)`：缺少 `Host` 回 400，`Content-Length` 与实际 body 字节数不符回 400，`Content-Length` 与 `Transfer-Encoding` 同时存在回 400，返回状态码。
 * 4. 思考题：为什么 `302` 在历史上会把 POST 改成 GET，而 `307` 不会？这对支付类回调接口意味着什么？
 * ============================================================
 */
