/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：22-08
 * 课名：综合案例：打一个可执行jar
 * ------------------------------------------------------------
 * 知识点：
 *   - 普通 `mvn package` 打出的 jar 不含依赖，直接 `java -jar` 会报 `NoClassDefFoundError`。
 *   - 两种解决办法：`maven-shade-plugin`（把依赖解压后一起塞进一个 jar）、`maven-assembly-plugin`（打成带 `lib` 目录的压缩包）。
 *   - 可执行 jar 的关键是 `META-INF/MANIFEST.MF` 里的 `Main-Class`，它告诉 JVM 从哪个类开始执行。
 *   - `maven-jar-plugin` 通过 `<manifest><mainClass>` 生成该字段；路径用全限定类名，不带 `.class`。
 *   - `target/classes` 里的包结构会原样进入 jar 的根目录，所以 `com/heima/App.class` 在 jar 里也是这个路径。
 *   - 易错：`Main-Class` 写错不会在打包时报错，只在 `java -jar` 时抛 `ClassNotFoundException`。
 * ============================================================
 */

package com.heima.ch22;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

// ==================== 示例代码 ====================

public class ExecutableJar {
    public static void main(String[] args) throws IOException {
        Path work = Files.createTempDirectory("jar-demo-");
        Path jarPath = work.resolve("demo-1.0.0.jar");

        // 1. 准备 MANIFEST：Main-Class 是 java -jar 的入口
        Manifest manifest = new Manifest();
        Attributes attrs = manifest.getMainAttributes();
        attrs.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attrs.put(Attributes.Name.MAIN_CLASS, "com.heima.App");
        attrs.put(new Attributes.Name("Created-By"), "Maven 3.9.6");

        // 2. 把"编译产物"按包路径写进 jar
        try (OutputStream fos = Files.newOutputStream(jarPath);
             JarOutputStream jos = new JarOutputStream(fos, manifest)) {

            // 真实项目里这些是 target/classes 下的 .class 文件
            addEntry(jos, "com/heima/App.class", "（这里是编译后的字节码）");
            addEntry(jos, "com/heima/App$1.class", "（匿名内部类的字节码）");
            addEntry(jos, "application.properties", "app.name=demo\n");
        }

        System.out.println("已生成：" + jarPath.getFileName()
                + "，大小 " + Files.size(jarPath) + " 字节");
        System.out.println();

        // 3. 读回来验证结构
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            System.out.println("MANIFEST 中的入口类：" + jar.getManifest()
                    .getMainAttributes().getValue("Main-Class"));
            System.out.println("jar 内条目：");
            jar.stream().forEach(e -> System.out.println("  " + e.getName()));
        }

        System.out.println();
        System.out.println("运行方式：java -jar demo-1.0.0.jar");
        System.out.println("若 Main-Class 写错，打包不报错，运行时才抛 ClassNotFoundException。");

        // 清理临时文件
        Files.deleteIfExists(jarPath);
        Files.deleteIfExists(work);
    }

    private static void addEntry(JarOutputStream jos, String name, String content)
            throws IOException {
        jos.putNextEntry(new JarEntry(name));
        jos.write(content.getBytes(StandardCharsets.UTF_8));
        jos.closeEntry();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 给一个含第三方依赖的项目配置 `maven-shade-plugin`，打出 `java -jar` 能直接运行的包，并用 `jar tf` 检查内容。
 * 2. 说明 `maven-jar-plugin` 和 `maven-shade-plugin` 打的包有什么区别，各自适用什么场景。
 * 3. 一个 `java -jar` 报 `NoClassDefFoundError: com/mysql/cj/jdbc/Driver` 的包，问题最可能出在哪一步？给出排查顺序。
 * ============================================================
 */
