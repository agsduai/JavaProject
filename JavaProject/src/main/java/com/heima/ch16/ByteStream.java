/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：16-03
 * 课名：字节流读取与写出
 * ------------------------------------------------------------
 * 知识点：
 *   - 字节流是所有流的基础，以 `byte`（8 位）为单位，能处理任意类型文件（图片、视频、文本）。
 *   - `FileInputStream` 读取：`read()` 读一个字节（返回 0~255，末尾返回 -1）、`read(byte[] b)` 批量读取返回实际读取长度。
 *   - `FileOutputStream` 写出：`write(int b)`、`write(byte[] b)`、`write(byte[] b, int off, int len)`。
 *   - `FileOutputStream(String path, boolean append)` 第二个参数为 `true` 表示追加，默认 `false` 会覆盖原内容。
 *   - 流用完必须关闭，推荐 `try-with-resources` 写法（`try (资源) {}`），异常时自动关闭。
 *   - 易错：一次读一个字节效率极低；`read(byte[] b)` 返回的是"本次读到的字节数"，不是数组长度。
 * ============================================================
 */

package com.heima.ch16;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

// ==================== 示例代码 ====================

public class ByteStream {
    public static void main(String[] args) {
        String path = "demo/byte.txt";

        // 写出：try-with-resources 自动关闭流
        try (FileOutputStream out = new FileOutputStream(path)) {
            out.write("Hello IO".getBytes());        // 写出全部字节
            out.write('\n');
            out.write("第二行".getBytes());
        } catch (IOException e) {
            System.out.println("写出失败：" + e.getMessage());
        }

        // 读取：一次读一个字节
        try (FileInputStream in = new FileInputStream(path)) {
            int b;
            StringBuilder sb = new StringBuilder();
            while ((b = in.read()) != -1) {
                sb.append((char) b);
            }
            System.out.println("逐字节读取：\n" + sb);
        } catch (IOException e) {
            System.out.println("读取失败：" + e.getMessage());
        }

        // 读取：用字节数组批量读，效率更高
        try (FileInputStream in = new FileInputStream(path)) {
            byte[] buffer = new byte[1024];
            int len;
            StringBuilder sb = new StringBuilder();
            while ((len = in.read(buffer)) != -1) {
                sb.append(new String(buffer, 0, len)); // 只转换实际读到的部分
            }
            System.out.println("批量读取：\n" + sb);
        } catch (IOException e) {
            System.out.println("读取失败：" + e.getMessage());
        }

        // 追加写入
        try (FileOutputStream out = new FileOutputStream(path, true)) {
            out.write("\n追加的一行".getBytes());
            System.out.println("追加完成");
        } catch (IOException e) {
            System.out.println("追加失败：" + e.getMessage());
        }
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 用字节流把一段中文字符串写入文件，再用字节流读出来，验证内容一致（注意编码要统一为 UTF-8）。
 * 2. 编写方法 `copyFile(String src, String dest)`，用字节数组缓冲区实现文件复制，并打印复制的总字节数。
 * 3. 统计一个文件中有多少个换行符 `\n`，要求用字节流一次读一个字节实现。
 * ============================================================
 */
