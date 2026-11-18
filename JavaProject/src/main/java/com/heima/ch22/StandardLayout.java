/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：22-03
 * 课名：标准目录结构与pom.xml
 * ------------------------------------------------------------
 * 知识点：
 *   - Maven 强制约定目录：`src/main/java`（源码）、`src/main/resources`（配置）、`src/test/java`（测试源码）、`src/test/resources`（测试配置）、`target`（构建输出）。
 *   - 只要按约定放文件，就不用告诉 Maven"源码在哪"，这是"约定优于配置"的直接体现。
 *   - `pom.xml` 必备四要素：`modelVersion`（固定 4.0.0）、`groupId`、`artifactId`、`version`。
 *   - 常用可选元素：`packaging`（jar/war/pom，默认 jar）、`properties`（版本变量）、`dependencies`、`build`。
 *   - `target` 目录是构建产物，可以随时删；`mvn clean` 就是删它。它不应该提交到 Git。
 *   - 易错：把源码直接放在 `src` 根下、或者包名与目录层级对不上，Maven 会"找不到符号"，因为编译时只扫 `src/main/java`。
 * ============================================================
 */

package com.heima.ch22;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

// ==================== 示例代码 ====================

public class StandardLayout {
    public static void main(String[] args) throws IOException {
        Path root = Files.createTempDirectory("maven-layout-");
        List<String> dirs = List.of(
                "src/main/java/com/heima",
                "src/main/resources",
                "src/test/java/com/heima",
                "src/test/resources",
                "target");
        for (String d : dirs) {
            Files.createDirectories(root.resolve(d));
        }
        Files.writeString(root.resolve("src/main/java/com/heima/App.java"),
                "package com.heima; public class App { }");
        Files.writeString(root.resolve("src/main/resources/application.properties"), "app.name=demo");
        Files.writeString(root.resolve("pom.xml"), "<project/>");

        System.out.println("标准工程结构：");
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted().forEach(p -> {
                if (p.equals(root)) {
                    System.out.println("my-project/");
                    return;
                }
                Path rel = root.relativize(p);
                String name = rel.getFileName().toString() + (Files.isDirectory(p) ? "/" : "");
                System.out.println("  ".repeat(rel.getNameCount()) + name);
            });
        }

        // 包名与目录必须一一对应：com.heima.App -> com/heima/App.java
        String className = "com.heima.App";
        String expected = "src/main/java/" + className.replace('.', '/') + ".java";
        System.out.println();
        System.out.println("类 " + className + " 的源码应放在：" + expected);
        System.out.println("该文件存在吗：" + Files.exists(root.resolve(expected)));

        // 清理临时目录
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        }
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 用 Maven 生成一个空工程（`mvn archetype:generate` 或 IDEA 新建），对照笔记画出它的目录树。
 * 2. 把 `App.java` 里的包名改成 `com.heima.demo`，但文件还放在 `com/heima` 下，执行 `mvn compile` 看报什么错，并解释原因。
 * 3. 说明 `src/main/resources` 和 `src/main/java` 下的非 `.java` 文件在打包后分别去了哪里。
 * ============================================================
 */
