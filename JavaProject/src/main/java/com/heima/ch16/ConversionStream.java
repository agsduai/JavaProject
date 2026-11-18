/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：16-06
 * 课名：转换流与编码
 * ------------------------------------------------------------
 * 知识点：
 *   - 转换流用于"字节流 ↔ 字符流"的桥梁，同时可以指定字符编码，解决乱码问题。
 *   - `InputStreamReader(InputStream in, String charsetName)`：字节输入流转字符输入流。
 *   - `OutputStreamWriter(OutputStream out, String charsetName)`：字节输出流转字符输出流。
 *   - 常见编码：UTF-8（中文 3 字节）、GBK（中文 2 字节）；编码与解码用的字符集必须一致，否则乱码。
 *   - JDK11 起可以用 `Files.readString`/`Files.writeString` 直接指定编码，写法更简洁。
 *   - 易错：用 GBK 写入却用 UTF-8 读取，会出现"锟斤拷"这类乱码；中文 Windows 默认编码可能是 GBK。
 * ============================================================
 */

package com.heima.ch16;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

// ==================== 示例代码 ====================

public class ConversionStream {
    public static void main(String[] args) {
        String path = "demo/encoding.txt";

        // 用 UTF-8 编码写出
        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(path), StandardCharsets.UTF_8))) {
            writer.write("编码测试：UTF-8 写出");
            writer.newLine();
            writer.write("第二行中文");
        } catch (IOException e) {
            System.out.println("写出失败：" + e.getMessage());
        }

        // 用 UTF-8 读取，编码一致才不会乱码
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(path), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("UTF-8 读取：" + line);
            }
        } catch (IOException e) {
            System.out.println("读取失败：" + e.getMessage());
        }

        // 故意用 GBK 读取 UTF-8 文件，观察乱码
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(path), "GBK"))) {
            System.out.println("GBK 读取（乱码演示）：" + reader.readLine());
        } catch (IOException e) {
            System.out.println("读取失败：" + e.getMessage());
        }

        // 演示字符串与字节数组的编解码
        String text = "你好";
        byte[] utf8 = text.getBytes(StandardCharsets.UTF_8);
        byte[] gbk = text.getBytes(java.nio.charset.Charset.forName("GBK"));
        System.out.println("UTF-8 字节数：" + utf8.length); // 6
        System.out.println("GBK 字节数：" + gbk.length);     // 4
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 用 GBK 编码写一个文件，再用 UTF-8 读取，记录乱码现象；然后把读取编码改成 GBK，验证内容恢复正常。
 * 2. 把一个 UTF-8 编码的文件转成 GBK 编码的新文件，要求用转换流实现。
 * 3. 统计一段中文文本分别用 UTF-8 和 GBK 编码后的字节数，并解释差异原因。
 * ============================================================
 */
