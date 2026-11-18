/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：16-05
 * 课名：缓冲流
 * ------------------------------------------------------------
 * 知识点：
 *   - 缓冲流在内存中维护一个缓冲区（默认 8192 字节/字符），减少与磁盘的交互次数，显著提升效率。
 *   - 四种缓冲流：`BufferedInputStream`、`BufferedOutputStream`、`BufferedReader`、`BufferedWriter`。
 *   - `BufferedReader` 特有 `readLine()`：一次读一整行，读到末尾返回 `null`（不含换行符）。
 *   - `BufferedWriter` 特有 `newLine()`：写入一个跨平台换行符。
 *   - 包装流只需把基本流作为构造参数传入：`new BufferedReader(new FileReader(path))`。
 *   - 易错：缓冲流的 `close()` 会同时关闭被包装的流，不需要也不应该重复关闭底层流。
 * ============================================================
 */

package com.heima.ch16;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

// ==================== 示例代码 ====================

public class BufferedStream {
    public static void main(String[] args) {
        String path = "demo/buffered.txt";

        // BufferedWriter：按行写
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(path))) {
            writer.write("第一行：Java");
            writer.newLine();  // 跨平台换行
            writer.write("第二行：IO流");
            writer.newLine();
            writer.write("第三行：缓冲流");
        } catch (IOException e) {
            System.out.println("写出失败：" + e.getMessage());
        }

        // BufferedReader：readLine 按行读
        try (BufferedReader reader = new BufferedReader(new FileReader(path))) {
            String line;
            int lineNo = 1;
            while ((line = reader.readLine()) != null) { // 末尾返回 null
                System.out.println("第 " + lineNo + " 行：" + line);
                lineNo++;
            }
        } catch (IOException e) {
            System.out.println("读取失败：" + e.getMessage());
        }

        // 统计行数：readLine 读到 null 为止
        System.out.println("总行数：" + countLines(path));
    }

    public static int countLines(String path) {
        int count = 0;
        try (BufferedReader reader = new BufferedReader(new FileReader(path))) {
            while (reader.readLine() != null) {
                count++;
            }
        } catch (IOException e) {
            System.out.println("读取失败：" + e.getMessage());
        }
        return count;
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 用缓冲流复制一个文本文件，并在每行前加上行号，输出到新文件。
 * 2. 读取一个文本文件，统计其中非空行的数量以及最长一行的长度。
 * 3. 用 `BufferedWriter` 生成一个含 100 行的文件，再用 `BufferedReader` 统计其中包含特定关键字的行数。
 * ============================================================
 */
