/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：18-04
 * 课名：TCP多客户端与聊天室
 * ------------------------------------------------------------
 * 知识点：
 *   - 一个 `ServerSocket` 可以接受多个连接，每次 `accept` 返回一个新的 `Socket`，需为每个连接单独开线程。
 *   - 聊天室核心：服务端保存所有客户端连接的输出流集合，收到一条消息就转发给所有人。
 *   - 转发时用 `CopyOnWriteArrayList` 或加锁的 `List` 保存客户端，避免并发修改异常。
 *   - 客户端需要两个线程：一个负责读服务端消息（接收），一个负责读键盘输入并发送。
 *   - 客户端下线要通知服务端移除连接，否则转发时会抛异常。
 *   - 易错：直接在 `accept` 循环里处理读写，会导致只能服务一个客户端，必须"一个连接一个线程"。
 * ============================================================
 */

package com.heima.ch18;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

// ==================== 示例代码 ====================

public class ChatRoom {
    // 保存所有在线客户端的输出流
    private static final List<PrintWriter> CLIENTS = new CopyOnWriteArrayList<>();

    public static void main(String[] args) throws Exception {
        int port = 8899;

        Thread server = new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(port)) {
                System.out.println("聊天室服务端启动，端口：" + port);
                while (true) {
                    Socket socket = serverSocket.accept();
                    System.out.println("新用户上线：" + socket.getPort());
                    // 每个客户端一个线程
                    new Thread(() -> handleClient(socket)).start();
                }
            } catch (Exception e) {
                System.out.println("服务端异常：" + e.getMessage());
            }
        });
        server.setDaemon(true);
        server.start();

        Thread.sleep(300);

        // 启动两个客户端，模拟群聊
        Thread clientA = new Thread(() -> runClient("用户A", "大家好，我是A"));
        Thread clientB = new Thread(() -> runClient("用户B", "我是B，很高兴认识大家"));
        clientA.start();
        clientB.start();
        clientA.join();
        clientB.join();

        Thread.sleep(500);
        System.out.println("演示结束，在线客户端数：" + CLIENTS.size());
    }

    private static void handleClient(Socket socket) {
        PrintWriter writer = null;
        try {
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), "UTF-8"));
            writer = new PrintWriter(
                    new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);
            CLIENTS.add(writer);

            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("收到消息：" + line);
                // 群发给所有客户端
                for (PrintWriter client : CLIENTS) {
                    client.println(line);
                }
            }
        } catch (Exception e) {
            System.out.println("客户端连接异常：" + e.getMessage());
        } finally {
            CLIENTS.remove(writer);
            try {
                socket.close();
            } catch (Exception ignored) {
            }
        }
    }

    private static void runClient(String name, String message) {
        try (Socket socket = new Socket("127.0.0.1", 8899)) {
            PrintWriter writer = new PrintWriter(
                    new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), "UTF-8"));

            writer.println(name + "：" + message);
            String response = reader.readLine();
            System.out.println(name + " 收到转发：" + response);
        } catch (Exception e) {
            System.out.println(name + " 异常：" + e.getMessage());
        }
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 在聊天室基础上增加"用户名"前缀，让每条转发消息都带上发送者名字。
 * 2. 增加"上线/下线提示"：新客户端连接或断开时，向所有在线客户端广播提示消息。
 * 3. 说明为什么保存客户端连接要用 `CopyOnWriteArrayList`，用普通 `ArrayList` 会出现什么问题。
 * ============================================================
 */
