/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：实操 18-02
 * 课名：HTTP 客户端与状态码
 * ------------------------------------------------------------
 * 知识点：
 *   - 【题目要求】用 java.net.http.HttpClient 对本机临时 HttpServer（com.sun.net.httpserver）做四类请求：GET 200、GET 404、POST JSON 带头、GET 302 不跟随（获取 Location）；输出每请求的状态码/关键头/耗时；实现简单重试（仅对 5xx 与 IO 异常，指数退避）。
 *   - 【考核点】HttpClient/HttpRequest/HttpResponse、状态码语义、Header 操作、重试设计（只重试幂等与临时错误）。
 *   - 【易错点】POST 不设 Content-Type（服务端解析失败）；对 POST 默认自动重试（非幂等风险）；跟随重定向吞掉 302 观察点。
 *   - 【提示】HttpServer 起在随机端口（port 0），注册 /ok、/notfound、/redirect、/echo 四个 handler。
 * ============================================================
 */

package com.heima.practice.core;

import java.net.URI;
import java.net.http.*;

// ==================== 示例代码 ====================

public class HttpClientTask {
    public static void main(String[] args) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://httpbin.org/status/200"))
                .header("Accept", "application/json")
                .build();
        try {
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            System.out.printf("  状态码=%d 耗时由服务端决定%n", resp.statusCode());
        } catch (Exception e) {
            System.out.println("  离线环境：本课真实运行请起本地 HttpServer（48 章模式）");
        }
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 完整实现四类请求 + 本地 HttpServer + 重试器（退避与不重试 POST 的区分），输出请求报告。
 * 2. 实现"超时与取消"：connectTimeout 2s / request timeout 3s，验证慢响应被取消并记录。
 * 3. 思考题：为什么 POST 默认不应自动重试？构造一个"重试导致重复下单"的时序说明幂等性约束。
 * 
 * 
 * ## 第19章 反射与注解
 * ============================================================
 */
