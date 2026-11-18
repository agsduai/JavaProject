/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：16-04
 * 课名：字符流读取与写出
 * ------------------------------------------------------------
 * 知识点：
 *   - 字符流以字符为单位，专门处理文本，能正确处理中文（一个中文在 UTF-8 中占 3 个字节）。
 *   - `FileReader` 读取：`read()` 读一个字符、`read(char[] cbuf)` 批量读取。
 *   - `FileWriter` 写出：`write(String)`、`write(char[])`、`write(String, int off, int len)`。
 *   - `FileWriter` 同样有追加模式构造：`new FileWriter(path, true)`。
 *   - `FileWriter` 内部有缓冲区，`close()` 会先刷新再关闭；不关闭可能丢数据，也可手动 `flush()`。
 *   - 易错：用字节流直接读中文再 `(char)` 强转会出现乱码，文本文件应优先用字符流。
 * ============================================================
 */

package com.heima.ch16;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

// ==================== 示例代码 ====================

public class CharStream {
    public static void main(String[] args) {
        String path = "demo/char.txt";

        // 字符流写出：直接写字符串，无需转字节
        try (FileWriter writer = new FileWriter(path)) {
            writer.write("你好，字符流！\n");
            writer.write("第二行中文内容\n");
            writer.write("第三行");
            writer.flush(); // 手动刷新，确保数据落盘
        } catch (IOException e) {
            System.out.println("写出失败：" + e.getMessage());
        }

        // 字符流读取：一次读一个字符
        try (FileReader reader = new FileReader(path)) {
            int c;
            StringBuilder sb = new StringBuilder();
            while ((c = reader.read()) != -1) {
                sb.append((char) c);
            }
            System.out.println("逐字符读取：\n" + sb);
        } catch (IOException e) {
            System.out.println("读取失败：" + e.getMessage());
        }

        // 字符数组批量读取
        try (FileReader reader = new FileReader(path)) {
            char[] buffer = new char[8];
            int len;
            StringBuilder sb = new StringBuilder();
            while ((len = reader.read(buffer)) != -1) {
                sb.append(buffer, 0, len);
            }
            System.out.println("批量读取：\n" + sb);
        } catch (IOException e) {
            System.out.println("读取失败：" + e.getMessage());
        }

        // 追加写入
        try (FileWriter writer = new FileWriter(path, true)) {
            writer.write("\n追加：第四行");
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
 * 1. 用 `FileWriter` 把一个字符串数组的每个元素写成一行，再用 `FileReader` 读回来并统计行数。
 * 2. 读取一个文本文件，把其中所有英文小写字母转成大写后写入新文件。
 * 3. 比较字节流和字符流读写同一段中文内容的结果，说明为什么字符流更适合处理文本。
 * ============================================================
 */
