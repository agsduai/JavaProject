/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：18-06
 * 课名：HTTP请求与URL访问
 * ------------------------------------------------------------
 * 知识点：
 *   - HTTP 是应用层协议，基于 TCP，一次请求一次响应，服务端默认端口 80（HTTPS 443）。
 *   - 请求方法：GET 把参数拼在 URL 后，POST 把参数放在请求体中；GET 有长度限制、会留在历史记录里。
 *   - 状态码：200 成功、301/302 重定向、400 参数错误、404 资源不存在、500 服务器内部错误。
 *   - `URL` 类表示统一资源定位符，`openStream()` 可以直接读取网页内容；`openConnection()` 返回 `HttpURLConnection` 可设置请求头、方法。
 *   - `HttpURLConnection` 的 `setRequestMethod`、`setRequestProperty`、`getResponseCode`、`getInputStream` 是常用 API。
 *   - 易错：读取 `getErrorStream()` 才能拿到 4xx/5xx 响应体；连接用完要 `disconnect()` 释放资源。
 * ============================================================
 */

package com.heima.ch18;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;

// ==================== 示例代码 ====================

public class HttpUrlDemo {
    public static void main(String[] args) {
        String urlStr = "https://www.example.com";
        HttpURLConnection conn = null;
        try {
            // JDK 20 起 URL 构造器已过时，推荐 URI.create(...).toURL()
            URL url = URI.create(urlStr).toURL();
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);  // 连接超时
            conn.setReadTimeout(5000);     // 读取超时
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");

            int code = conn.getResponseCode();
            System.out.println("响应状态码：" + code);
            System.out.println("响应类型：" + conn.getContentType());

            if (code == 200) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    int lineCount = 0;
                    while ((line = reader.readLine()) != null) {
                        lineCount++;
                        if (lineCount <= 10) {
                            System.out.println(line);
                        }
                    }
                    System.out.println("共读取 " + lineCount + " 行");
                }
            } else {
                System.out.println("请求失败，状态码：" + code);
            }
        } catch (Exception e) {
            System.out.println("请求异常：" + e.getMessage());
        } finally {
            if (conn != null) {
                conn.disconnect(); // 释放连接
            }
        }
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 用 `URL` 读取一个公开网页的前 20 行内容并输出，打印响应状态码和内容类型。
 * 2. 用 `HttpURLConnection` 发送一个 GET 请求，统计返回内容的字符数。
 * 3. 说明 GET 和 POST 的区别，以及 404 和 500 分别代表什么问题。
 * ============================================================
 */

/*
 * ============================================================
 * 本章综合练习（无参考答案）
 * ------------------------------------------------------------
 * 【场景】
 * 做一个能在本机跑通的多客户端聊天室：服务端广播消息、客户端收发消息、支持文件上传，最后用 HttpClient 访问一个公开接口验证 HTTP 请求格式。UDP 与 TCP 的差异在对比中体现。
 * 【任务】
 * 1. 用 UDP 实现一个回声服务：客户端发一句、服务端回一句，打印双方端口，说明 UDP 为什么不需要建立连接。
 * 2. 用 TCP 实现服务端 + 多客户端聊天室：服务端为每个连接开一个线程，收到消息后广播给其他客户端。
 * 3. 在聊天室基础上加文件上传：客户端发送文件名与字节流，服务端落盘并回执字节数。
 * 4. 用 HttpURLConnection 或 HttpClient 发起一次 GET 请求，打印状态码与响应头，说明 URL 的各个组成部分。
 * 【验收清单】
 * [ ] 三个客户端同时在线，任一客户端发消息其余都能收到。
 * [ ] 文件上传后服务端文件大小与客户端发送字节数一致。
 * [ ] 客户端断开后服务端能感知并移除连接，不残留死线程。
 * [ ] HTTP 请求能打印出 2xx 状态码与 Content-Type。
 * ============================================================
 */
