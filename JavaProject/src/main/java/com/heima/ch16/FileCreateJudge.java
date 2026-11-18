/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：16-01
 * 课名：File类的创建与判断
 * ------------------------------------------------------------
 * 知识点：
 *   - `File` 是文件和目录路径名的抽象表示，它本身不代表文件内容，只描述"路径"。
 *   - 三种构造：`File(String pathname)`、`File(String parent, String child)`、`File(File parent, String child)`。
 *   - 路径分隔符跨平台写法：用 `File.separator`，或直接写 `/`（Windows 也支持）。
 *   - 判断方法：`exists()` 是否存在、`isFile()` 是否文件、`isDirectory()` 是否目录、`length()` 文件大小（字节）。
 *   - 创建方法：`createNewFile()` 创建文件（已存在返回 false）、`mkdir()` 创建单级目录、`mkdirs()` 创建多级目录。
 *   - 易错：相对路径的基准是"项目根目录"（IDEA 中就是模块所在目录），不是 `src` 目录。
 * ============================================================
 */

package com.heima.ch16;

import java.io.File;
import java.io.IOException;

// ==================== 示例代码 ====================

public class FileCreateJudge {
    public static void main(String[] args) throws IOException {
        // 相对路径以项目根目录为基准
        File file = new File("demo/hello.txt");
        File dir = new File("demo");
        File deepDir = new File("demo/a/b/c");

        // mkdirs 会一次性创建不存在的多级目录
        System.out.println("创建多级目录：" + deepDir.mkdirs());
        System.out.println("目录是否存在：" + dir.exists());     // true
        System.out.println("是目录吗：" + dir.isDirectory());   // true
        System.out.println("是文件吗：" + dir.isFile());        // false

        // 创建文件，父目录必须已存在
        if (!file.exists()) {
            System.out.println("创建文件：" + file.createNewFile());
        }
        System.out.println("文件是否存在：" + file.exists());   // true
        System.out.println("文件大小：" + file.length() + " 字节"); // 0

        // 用父路径 + 子路径拼接，跨平台更安全
        File child = new File(dir, "note" + File.separator + "readme.txt");
        System.out.println("拼接后的路径：" + child.getPath());
        System.out.println("绝对路径：" + child.getAbsolutePath());
        System.out.println("文件名：" + child.getName());        // readme.txt
        System.out.println("父路径：" + child.getParent());
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 在项目根目录下创建 `test/2026/11` 三级目录，并在其中创建一个空文件 `log.txt`，打印该文件的绝对路径和大小。
 * 2. 编写方法 `boolean isTextFile(File f)`，判断传入的 `File` 是否是已存在的 `.txt` 文件。
 * 3. 分别用三种构造方法创建指向同一个文件的 `File` 对象，打印它们的 `getPath()` 并比较差异。
 * ============================================================
 */
