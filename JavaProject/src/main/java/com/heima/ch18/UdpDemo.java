/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：18-02
 * 课名：UDP通信
 * ------------------------------------------------------------
 * 知识点：
 *   - UDP 是面向无连接的协议：发送方不管对方在不在、收没收到，直接发，速度快但不保证可靠。
 *   - 核心类：`DatagramSocket`（发送/接收端）、`DatagramPacket`（数据包，包含数据 + 目标 IP + 端口）。
 *   - 发送流程：创建 `DatagramSocket` → 构造 `DatagramPacket(byte[], length, InetAddress, port)` → `send()`。
 *   - 接收流程：创建 `DatagramSocket(port)` 绑定端口 → 构造空包 `DatagramPacket(byte[], length)` → `receive()` 阻塞等待。
 *   - UDP 单包大小受 MTU 限制，一般不超过 64KB；适合视频直播、游戏、DNS 等能容忍丢包的场景。
 *   - 易错：接收端 `receive` 是阻塞的，没有数据时会一直卡住；`getData()` 返回的是整个缓冲区，要按 `getLength()` 截取。
 * ============================================================
 */

package com.heima.ch18;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;

// ==================== 示例代码 ====================

public class UdpDemo {
    public static void main(String[] args) throws Exception {
        int port = 9999;

        // 接收端：单独线程持续监听
        Thread receiver = new Thread(() -> {
            try (DatagramSocket socket = new DatagramSocket(port)) {
                System.out.println("UDP 接收端已启动，监听端口：" + port);
                byte[] buffer = new byte[1024];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet); // 阻塞等待数据

                String msg = new String(packet.getData(), 0, packet.getLength(),
                        StandardCharsets.UTF_8);
                System.out.println("收到来自 " + packet.getAddress().getHostAddress()
                        + ":" + packet.getPort() + " 的消息：" + msg);
            } catch (Exception e) {
                System.out.println("接收端异常：" + e.getMessage());
            }
        });
        receiver.start();

        Thread.sleep(300); // 等接收端就绪

        // 发送端
        try (DatagramSocket socket = new DatagramSocket()) {
            byte[] data = "你好，UDP！".getBytes(StandardCharsets.UTF_8);
            InetAddress target = InetAddress.getByName("127.0.0.1");
            DatagramPacket packet = new DatagramPacket(data, data.length, target, port);
            socket.send(packet);
            System.out.println("UDP 消息已发送");
        }

        receiver.join();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 用 UDP 实现一个简单的"点对点消息"程序：发送端发送一句话，接收端打印出来。
 * 2. 用 UDP 实现发送端连续发送 5 条消息，接收端循环接收 5 次并编号打印。
 * 3. 说明 UDP 为什么会出现丢包，以及在什么场景下可以接受丢包。
 * ============================================================
 */
