/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：22-04
 * 课名：坐标依赖与依赖范围
 * ------------------------------------------------------------
 * 知识点：
 *   - `<dependency>` 写在 `<dependencies>` 里，同样用 GAV 定位，`scope` 决定这个 jar 参与哪些环节。
 *   - `compile`（默认）：编译、测试、打包都在，会传递给下游项目。
 *   - `provided`：编译和测试可用，打包不带（如 `servlet-api`，因为服务器已经提供）。
 *   - `runtime`：编译不用、运行要用（如 JDBC 驱动，代码里只出现字符串类名）。
 *   - `test`：只在测试阶段可用，不会打进产物（如 JUnit）。
 *   - 易错：JDBC 驱动写成 `compile` 也能跑，但会让驱动被打进 war 包；规范写法是 `runtime`。
 * ============================================================
 */

package com.heima.ch22;

import java.util.LinkedHashMap;
import java.util.Map;

// ==================== 示例代码 ====================

public class DependencyScope {
    public static void main(String[] args) {
        // scope -> {编译期, 测试期, 打包产物}
        Map<String, boolean[]> table = new LinkedHashMap<>();
        table.put("compile", new boolean[]{true, true, true});
        table.put("provided", new boolean[]{true, true, false});
        table.put("runtime", new boolean[]{false, true, true});
        table.put("test", new boolean[]{false, true, false});

        System.out.printf("%-10s %-8s %-8s %-8s%n", "scope", "编译期", "测试期", "打包");
        System.out.println("------------------------------------------");
        for (Map.Entry<String, boolean[]> e : table.entrySet()) {
            boolean[] v = e.getValue();
            System.out.printf("%-10s %-8s %-8s %-8s%n",
                    e.getKey(), mark(v[0]), mark(v[1]), mark(v[2]));
        }

        System.out.println();
        String[][] deps = {
                {"junit:junit:4.13.2", "test"},
                {"jakarta.servlet:jakarta.servlet-api:6.0.0", "provided"},
                {"com.mysql:mysql-connector-j:8.4.0", "runtime"},
                {"org.mybatis:mybatis:3.5.16", "compile"}
        };
        for (String[] d : deps) {
            System.out.println(d[0] + "  ->  scope=" + d[1]
                    + "，会被打进 jar 吗：" + (table.get(d[1])[2] ? "会" : "不会"));
        }
    }

    private static String mark(boolean b) {
        return b ? "√" : "×";
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 为你的项目列出 4 个依赖，分别标注应该用哪种 scope，并说明理由。
 * 2. 把 JUnit 的 scope 误写成 `compile`，会导致什么后果？
 * 3. 解释为什么 JDBC 驱动适合用 `runtime`，代码里又是怎么找到驱动类的。
 * ============================================================
 */
