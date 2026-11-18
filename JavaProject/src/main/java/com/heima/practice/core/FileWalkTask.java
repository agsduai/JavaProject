/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：实操 16-01
 * 课名：文件遍历统计器
 * ------------------------------------------------------------
 * 知识点：
 *   - 【题目要求】对指定目录递归遍历：统计文件数/目录数/总字节数，找最大文件；按扩展名分组统计（.java/.md/...）；跳过隐藏目录；输出树形概览（缩进两级即可）。
 *   - 【考核点】Files.walk/DirectoryStream、File 属性读取、分组统计、递归与异常处理（权限拒绝）。
 *   - 【易错点】walk 不关流（try-with-resources）；符号链接导致死循环；IOException 在 lambda 里无法抛出需包装。
 *   - 【提示】Files.walkFileTree 或 Files.walk（Stream）二选一，统一在 catch 里记录跳过原因。
 * ============================================================
 */

package com.heima.practice.core;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.atomic.AtomicLong;

// ==================== 示例代码 ====================

public class FileWalkTask {
    public static void main(String[] args) throws IOException {
        AtomicLong bytes = new AtomicLong();
        Files.walkFileTree(Paths.get("."), new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                bytes.addAndGet(attrs.size());
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException e) {
                System.out.println("  跳过：" + file + "（" + e.getClass().getSimpleName() + "）");
                return FileVisitResult.CONTINUE;
            }
        });
        System.out.println("  当前目录总字节：" + bytes.get());
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 完整实现遍历统计器（含扩展名分组与最大文件），对项目某真实目录运行并输出报告。
 * 2. 加入"深度限制"参数（maxDepth），验证深层目录的截断行为。
 * 3. 思考题：为什么遍历时要用 AtomicLong 而不是普通 long？结合 visitFile 的调用时机分析。
 * ============================================================
 */
