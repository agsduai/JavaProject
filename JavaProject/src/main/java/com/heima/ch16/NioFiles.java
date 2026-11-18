/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：16-09
 * 课名：NIO与Files工具类
 * ------------------------------------------------------------
 * 知识点：
 *   - NIO（New IO）从 JDK7 起提供 `java.nio.file` 包，核心是 `Path` 和 `Files`，API 比传统 IO 更简洁。
 *   - `Path.of("demo/a.txt")` 创建路径对象；`Files.exists`、`Files.isDirectory`、`Files.size` 等做判断。
 *   - `Files.readAllLines(path)` 一次性读所有行；`Files.readString(path)` 读成字符串（JDK11+）。
 *   - `Files.writeString(path, content)`、`Files.write(path, lines)` 写文件，可指定 `StandardOpenOption.APPEND`。
 *   - `Files.copy`、`Files.move`、`Files.delete`、`Files.createDirectories` 完成常见文件操作。
 *   - 易错：`Files.readAllLines` 会把整个文件读进内存，大文件应改用 `Files.lines` 流式处理。
 * ============================================================
 */

package com.heima.ch16;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

// ==================== 示例代码 ====================

public class NioFiles {
    public static void main(String[] args) {
        Path path = Path.of("demo", "nio.txt");

        try {
            // 创建父目录（存在也不报错）
            Files.createDirectories(path.getParent());

            // 写入：整体覆盖
            Files.writeString(path, "第一行 NIO\n第二行 Files\n", StandardCharsets.UTF_8);

            // 追加
            Files.writeString(path, "第三行 追加\n",
                    StandardCharsets.UTF_8, StandardOpenOption.APPEND);

            System.out.println("文件大小：" + Files.size(path) + " 字节");
            System.out.println("是否存在：" + Files.exists(path));

            // 读取全部内容
            System.out.println("---- readString ----");
            System.out.println(Files.readString(path, StandardCharsets.UTF_8));

            // 按行读取
            System.out.println("---- readAllLines ----");
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size(); i++) {
                System.out.println((i + 1) + ": " + lines.get(i));
            }

            // 流式读取：适合大文件
            System.out.println("---- Files.lines ----");
            try (var stream = Files.lines(path, StandardCharsets.UTF_8)) {
                System.out.println("非空行数：" + stream.filter(s -> !s.isBlank()).count());
            }

            // 复制文件
            Path copy = Path.of("demo", "nio_copy.txt");
            Files.copy(path, copy, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            System.out.println("复制完成：" + copy.toAbsolutePath());
        } catch (IOException e) {
            System.out.println("NIO 操作失败：" + e.getMessage());
        }
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 用 `Files` 工具类创建一个文本文件，写入 5 行内容，再统计行数、字符数和文件字节大小。
 * 2. 用 `Files.copy` 复制一个文件，并用 `Files.move` 把它改名，最后用 `Files.delete` 删除原文件。
 * 3. 用 `Files.lines` 流式读取一个大文本文件，统计其中包含指定关键字的行数（要求不把整个文件读入内存）。
 * ============================================================
 */
