/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：实操 18-01
 * 课名：TCP Echo 与连接管理
 * ------------------------------------------------------------
 * 知识点：
 *   - 【题目要求】实现 TCP Echo：服务端（ServerSocket 线程池模型，虚拟线程每连接一线程）+ 客户端（发 5 条消息收 5 条回显）；演示半包/粘包（一次发送两条短消息连发，接收端按分隔符拆包）；连接关闭的优雅处理（客户端关流 -> 服务端 read 返回 -1）。
 *   - 【考核点】ServerSocket/Socket API、TCP 无消息边界（粘包半包）、按分隔符/长度头拆包、连接生命周期。
 *   - 【易错点】假设一次 read 收到完整消息；服务端单线程处理（阻塞其他客户端）；忘记 flush（数据滞留缓冲）。
 *   - 【提示】协议设计两种方案：分隔符（\n）与长度头（4 字节 int）——实现分隔符版，长度头版留练习。
 * ============================================================
 */

package com.heima.practice.core;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

// ==================== 示例代码 ====================

public class TcpEchoTask {
    public static void main(String[] args) throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            int port = server.getLocalPort();
            Thread serverThread = new Thread(() -> {
                try (Socket conn = server.accept();
                     BufferedReader in = new BufferedReader(
                             new InputStreamReader(conn.getInputStream()));
                     PrintWriter out = new PrintWriter(conn.getOutputStream(), true)) {
                    String line;
                    while ((line = in.readLine()) != null) {
                        out.println("echo:" + line);
                    }
                } catch (IOException ignored) {
                }
            });
            serverThread.start();
            try (Socket client = new Socket("127.0.0.1", port);
                 PrintWriter out = new PrintWriter(client.getOutputStream(), true);
                 BufferedReader in = new BufferedReader(
                         new InputStreamReader(client.getInputStream()))) {
                for (int i = 0; i < 3; i++) {
                    out.println("msg-" + i);
                    System.out.println("  收到：" + in.readLine());
                }
            }
            serverThread.join();
        }
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现完整 Echo（多客户端并发：虚拟线程每连接一线程），验证 5 个客户端同时收发。
 * 2. 实现"长度头协议"拆包（4 字节 int + body），构造粘包场景验证正确拆分。
 * 3. 思考题：为什么 TCP 需要应用层定义消息边界？从 TCP 的字节流语义与 UDP 的数据报语义对比分析。
 * ============================================================
 */
