/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：16-02
 * 课名：File的遍历与删除
 * ------------------------------------------------------------
 * 知识点：
 *   - `list()` 返回 `String[]`（只含名称），`listFiles()` 返回 `File[]`（含完整路径信息）。
 *   - `listFiles(FileFilter filter)` 可以按条件过滤，`accept` 返回 `true` 才保留。
 *   - `delete()` 删除文件或**空目录**；目录非空时删除失败，必须先删掉里面的内容。
 *   - 递归删除目录的套路：先遍历子项递归删除，最后删自己。
 *   - 递归遍历要注意：目录层级过深可能栈溢出，`listFiles` 在无权限时会返回 `null`。
 *   - 易错：`delete()` 不抛异常、只返回 `boolean`，删除失败往往被静默忽略，务必检查返回值。
 * ============================================================
 */

package com.heima.ch16;

import java.io.File;

// ==================== 示例代码 ====================

public class FileTraversalDelete {
    public static void main(String[] args) {
        File dir = new File("demo");
        if (!dir.exists()) {
            dir.mkdirs();
            System.out.println("目录不存在，已创建：" + dir.getAbsolutePath());
        }

        // listFiles 获取目录下所有 File 对象
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                System.out.println((f.isDirectory() ? "[目录] " : "[文件] ") + f.getName());
            }
        }

        // 过滤器：只保留 .txt 文件
        File[] txtFiles = dir.listFiles(pathname ->
                pathname.isFile() && pathname.getName().endsWith(".txt"));
        if (txtFiles != null) {
            System.out.println("txt 文件个数：" + txtFiles.length);
        }

        // 递归统计目录下所有文件的总大小
        System.out.println("总大小：" + getTotalSize(dir) + " 字节");
    }

    public static long getTotalSize(File dir) {
        long size = 0;
        File[] files = dir.listFiles();
        if (files == null) {
            return 0;
        }
        for (File f : files) {
            if (f.isFile()) {
                size += f.length();
            } else {
                size += getTotalSize(f); // 目录递归进去继续统计
            }
        }
        return size;
    }

    // 递归删除：先删子项，再删自己
    public static boolean deleteDir(File dir) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) {
                    deleteDir(f);
                } else {
                    f.delete();
                }
            }
        }
        return dir.delete();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 递归遍历某个目录，输出所有 `.java` 文件的绝对路径，并统计总个数。
 * 2. 编写方法 `copyTree(File src, File dest)` 的目录结构骨架：只创建相同结构的空目录，不复制文件内容。
 * 3. 编写方法删除某个目录，要求先打印将被删除的所有文件路径，再执行删除，最后验证目录已不存在。
 * ============================================================
 */
