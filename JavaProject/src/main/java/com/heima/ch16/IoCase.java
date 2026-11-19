/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：16-10
 * 课名：IO综合案例：文件内容统计与批量重命名
 * ------------------------------------------------------------
 * 知识点：
 *   - 综合案例的解题套路：明确输入（源文件/目录）→ 选择流（文本用字符流，二进制用字节流）→ 处理数据 → 输出结果。
 *   - 统计类需求适合用 `Map<String, Integer>` 累计词频，再排序输出。
 *   - 批量重命名：`File.renameTo(File dest)` 或 `Files.move`，注意新名字不能与已有文件重名。
 *   - 编码统一是文本处理的前提，读写两端都用 UTF-8 可以避免绝大多数乱码。
 *   - 异常处理：单个文件失败不应中断整个批处理，捕获异常后记录并继续。
 *   - 易错：重命名时如果目标文件已存在，`renameTo` 在 Windows 上会直接失败，要先判断。
 * ============================================================
 */

package com.heima.ch16;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class IoCase {
    public static void main(String[] args) {
        String path = "demo/summary.txt";

        // 准备测试数据
        try (java.io.FileWriter writer = new java.io.FileWriter(path)) {
            writer.write("java io stream\n");
            writer.write("java collection map\n");
            writer.write("io stream file\n");
        } catch (IOException e) {
            System.out.println("准备数据失败：" + e.getMessage());
            return;
        }

        // 统计每个单词出现的次数
        Map<String, Integer> wordCount = new HashMap<>();
        int totalChars = 0;
        int totalLines = 0;

        try (BufferedReader reader = new BufferedReader(new FileReader(path))) {
            String line;
            while ((line = reader.readLine()) != null) {
                totalLines++;
                totalChars += line.length();
                String[] words = line.trim().split("\\s+");
                for (String word : words) {
                    if (word.isEmpty()) {
                        continue;
                    }
                    wordCount.merge(word.toLowerCase(), 1, Integer::sum);
                }
            }
        } catch (IOException e) {
            System.out.println("读取失败：" + e.getMessage());
        }

        System.out.println("总行数：" + totalLines + "，总字符数：" + totalChars);
        System.out.println("单词频次（降序）：");
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(wordCount.entrySet());
        entries.sort(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder())
                .thenComparing(Map.Entry.comparingByKey()));
        entries.forEach(e -> System.out.println("  " + e.getKey() + " = " + e.getValue()));

        // 批量重命名演示：把 demo/rename 下的 .tmp 改成 .txt
        java.io.File dir = new java.io.File("demo/rename");
        if (!dir.exists()) {
            dir.mkdirs();
            try {
                new java.io.File(dir, "a.tmp").createNewFile();
                new java.io.File(dir, "b.tmp").createNewFile();
            } catch (IOException e) {
                System.out.println("创建测试文件失败：" + e.getMessage());
            }
        }
        java.io.File[] files = dir.listFiles((d, name) -> name.endsWith(".tmp"));
        if (files != null) {
            for (java.io.File f : files) {
                String newName = f.getName().replace(".tmp", ".txt");
                java.io.File dest = new java.io.File(dir, newName);
                boolean ok = f.renameTo(dest);
                System.out.println("重命名 " + f.getName() + " -> " + newName + "：" + (ok ? "成功" : "失败"));
            }
        }
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 统计一个英文文本文件中的单词总数、不同单词数，并输出出现次数最多的 3 个单词。
 * 2. 编写方法 `int countKeyword(File file, String keyword)`，统计文件中包含关键字的行数，要求处理读取异常。
 * 3. 编写一个批量重命名工具：把指定目录下所有文件名中的空格替换成下划线，并输出每个文件的处理结果。
 * ============================================================
 */

/*
 * ============================================================
 * 本章综合练习（无参考答案）
 * ------------------------------------------------------------
 * 【场景】
 * 给一个目录下的日志文件做一次体检：统计行数、按级别（INFO/WARN/ERROR）分类计数、找出出现最多的错误信息，再把文件名批量加上日期前缀。File、缓冲流、Properties 与 Files 都要用上。
 * 【任务】
 * 1. 用 File 遍历目录，过滤出 .log 文件，输出文件名、大小、最后修改时间，目录不存在时给出提示而不是抛异常。
 * 2. 用 BufferedReader 逐行读取，按级别计数，统计总行数与 ERROR 占比，结果用 printf 对齐输出。
 * 3. 把出现次数最多的前 3 条 ERROR 信息存入 Map 并排序输出，说明为什么用 LinkedHashMap 与 TreeMap 结果不同。
 * 4. 从 config.properties 读取日期前缀配置，用 Files.move 批量重命名文件，重名时自动加序号避免覆盖。
 * 【验收清单】
 * [ ] 空目录、单文件、含子目录三种情况都能正常处理。
 * [ ] 行数统计与 wc -l 结果一致，级别计数之和等于总行数。
 * [ ] 批量重命名后原文件名可在日志中追溯，不会静默覆盖已有文件。
 * [ ] 所有流都用 try-with-resources 关闭，没有资源泄漏。
 * ============================================================
 */
