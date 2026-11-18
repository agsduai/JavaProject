/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：18-05
 * 课名：TCP文件上传
 * ------------------------------------------------------------
 * 知识点：
 *   - 文件上传流程：客户端用 `FileInputStream` 读文件 → 通过 Socket 输出流发送 → 服务端从输入流读并写入 `FileOutputStream`。
 *   - 关键约定：客户端发送完文件后必须 `shutdownOutput()`，服务端才知道"数据到此为止"。
 *   - 文件名可以在发送文件内容前先发一行文本告诉服务端，服务端先读这一行再读文件字节。
 *   - 服务端收到文件后要回一条确认消息，让客户端知道上传成功。
 *   - 二进制文件（图片、压缩包）必须用字节流，不能用字符流，否则文件会损坏。
 *   - 易错：服务端用 `readLine()` 读文件名、再用字节流读内容时，两者不能共用同一个 `BufferedReader`，否则会吞掉文件开头的字节。
 * ============================================================
 */

package com.heima.ch18;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

// ==================== 示例代码 ====================

public class FileUpload {
    public static void main(String[] args) throws Exception {
        int port = 8877;
        File uploadDir = new File("demo/upload");
        uploadDir.mkdirs();

        // 服务端
        Thread server = new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(port)) {
                System.out.println("文件服务端启动，端口：" + port);
                try (Socket socket = serverSocket.accept()) {
                    DataInputStream in = new DataInputStream(
                            new BufferedInputStream(socket.getInputStream()));

                    // 先读文件名长度和文件名
                    int nameLength = in.readInt();
                    byte[] nameBytes = new byte[nameLength];
                    in.readFully(nameBytes);
                    String fileName = new String(nameBytes, "UTF-8");
                    System.out.println("接收文件名：" + fileName);

                    // 再读文件内容
                    File dest = new File(uploadDir, fileName);
                    long total = 0;
                    try (FileOutputStream out = new FileOutputStream(dest)) {
                        byte[] buffer = new byte[4096];
                        int len;
                        while ((len = in.read(buffer)) != -1) {
                            out.write(buffer, 0, len);
                            total += len;
                        }
                    }
                    System.out.println("文件已保存：" + dest.getAbsolutePath() + "，共 " + total + " 字节");

                    PrintWriter writer = new PrintWriter(
                            new java.io.OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);
                    writer.println("上传成功：" + fileName + "，" + total + " 字节");
                }
            } catch (Exception e) {
                System.out.println("服务端异常：" + e.getMessage());
            }
        });
        server.start();

        Thread.sleep(300);

        // 准备一个测试文件
        File src = new File("demo/upload_src.txt");
        try (FileOutputStream out = new FileOutputStream(src)) {
            out.write("这是要上传的文件内容。\n第二行。".getBytes("UTF-8"));
        }

        // 客户端上传
        try (Socket socket = new Socket("127.0.0.1", port)) {
            BufferedOutputStream out = new BufferedOutputStream(socket.getOutputStream());
            byte[] nameBytes = src.getName().getBytes("UTF-8");
            java.io.DataOutputStream dataOut = new java.io.DataOutputStream(out);
            dataOut.writeInt(nameBytes.length);
            dataOut.write(nameBytes);

            try (FileInputStream fileIn = new FileInputStream(src)) {
                byte[] buffer = new byte[4096];
                int len;
                while ((len = fileIn.read(buffer)) != -1) {
                    dataOut.write(buffer, 0, len);
                }
            }
            dataOut.flush();
            socket.shutdownOutput(); // 告诉服务端：文件发完了

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), "UTF-8"));
            System.out.println("服务端回应：" + reader.readLine());
        }

        server.join();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 改造上传程序，让服务端把收到的文件改名（如加上时间戳前缀）后保存，避免同名文件覆盖。
 * 2. 增加上传进度提示：客户端每发送 1KB 就打印一次已发送的百分比。
 * 3. 实现"上传图片"：用字节流把一张图片从客户端传到服务端，比较传输前后文件大小是否一致。
 * ============================================================
 */
