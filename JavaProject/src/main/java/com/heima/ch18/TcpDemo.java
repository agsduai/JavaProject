/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：18-03
 * 课名：TCP通信：客户端与服务端
 * ------------------------------------------------------------
 * 知识点：
 *   - TCP 是面向连接的协议：通信前先"三次握手"建立连接，传输可靠、有序、不丢数据。
 *   - 服务端：`ServerSocket(port)` → `accept()` 阻塞等待连接，返回一个 `Socket`。
 *   - 客户端：`new Socket(host, port)` 连接服务端，连接成功后双方各有一个 `Socket`。
 *   - 通信靠流：`getInputStream()` 读数据，`getOutputStream()` 写数据，可用转换流包装成字符流。
 *   - 关闭顺序：先关输出流（`shutdownOutput` 或 `close`），再关输入流，最后关 Socket；服务端 `accept` 会阻塞，需用线程处理多个连接。
 *   - 易错：客户端写数据后不关闭输出流，服务端 `read` 会一直阻塞等待"读完"。
 * ============================================================
 */

package com.heima.ch18;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

// ==================== 示例代码 ====================

public class TcpDemo {
    public static void main(String[] args) throws Exception {
        int port = 8888;

        // 服务端线程
        Thread server = new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(port)) {
                System.out.println("服务端启动，端口：" + port);
                try (Socket socket = serverSocket.accept()) { // 阻塞等待连接
                    System.out.println("客户端已连接：" + socket.getInetAddress().getHostAddress());

                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(socket.getInputStream(), "UTF-8"));
                    PrintWriter writer = new PrintWriter(
                            new java.io.OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);

                    String line = reader.readLine();
                    System.out.println("服务端收到：" + line);

                    writer.println("你好客户端，我已收到：" + line);
                }
            } catch (Exception e) {
                System.out.println("服务端异常：" + e.getMessage());
            }
        });
        server.start();

        Thread.sleep(300); // 等服务端就绪

        // 客户端
        try (Socket socket = new Socket("127.0.0.1", port)) {
            System.out.println("客户端已连接到服务端");
            PrintWriter writer = new PrintWriter(
                    new java.io.OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), "UTF-8"));

            writer.println("你好服务端，我是客户端");
            String response = reader.readLine();
            System.out.println("客户端收到：" + response);
        }

        server.join();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现一个 TCP 程序：客户端发送自己的姓名，服务端回复"欢迎你，xxx"。
 * 2. 实现客户端发送一个整数，服务端返回它的平方，客户端打印结果。
 * 3. 说明 TCP 三次握手的作用，以及为什么服务端 `accept` 需要放在循环或线程中。
 * ============================================================
 */
