/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：24-04
 * 课名：配置文件与Profile
 * ------------------------------------------------------------
 * 知识点：
 *   - 两种格式：`application.properties`（`key=value`）和 `application.yml`（缩进层级），YAML 更易读、支持列表和嵌套对象。
 *   - 配置优先级（高 → 低）：命令行参数 → 环境变量 → `application-{profile}.yml` → `application.yml`。
 *   - `spring.profiles.active=dev` 激活环境；`application-dev.yml`、`application-prod.yml` 分别存各环境配置。
 *   - 一个 YAML 文件里可用 `---` 分隔多个文档，配合 `spring.config.activate.on-profile` 实现多环境。
 *   - `@Value("${key}")` 注入单个配置项；`@ConfigurationProperties(prefix = "xxx")` 批量绑定到一个对象。
 *   - 敏感信息（密码、密钥）不要硬编码，用环境变量或配置中心。
 *   - 易错：YAML 用 Tab 缩进会解析失败，必须用空格；键名大小写敏感。
 * ============================================================
 */

package com.heima.ch24;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class ConfigAndProfile {
    public static void main(String[] args) {
        // 模拟三个来源的配置，按优先级从低到高
        Map<String, String> base = new LinkedHashMap<>();
        base.put("server.port", "8080");
        base.put("spring.datasource.url", "jdbc:mysql://localhost:3306/heima_db");
        base.put("logging.level.root", "info");

        Map<String, String> dev = new LinkedHashMap<>();
        dev.put("server.port", "9090");
        dev.put("logging.level.root", "debug");
        dev.put("spring.datasource.url", "jdbc:mysql://localhost:3306/heima_db_dev");

        Map<String, String> prod = new LinkedHashMap<>();
        prod.put("server.port", "80");
        prod.put("logging.level.root", "warn");
        prod.put("spring.datasource.url", "jdbc:mysql://10.0.0.8:3306/heima_db");

        Map<String, String> cli = new LinkedHashMap<>();
        cli.put("server.port", "8888");   // 命令行参数优先级最高

        System.out.println("=== 不激活 profile ===");
        print(merge(base));

        System.out.println();
        System.out.println("=== 激活 dev ===");
        print(merge(base, dev));

        System.out.println();
        System.out.println("=== 激活 prod + 命令行覆盖端口 ===");
        print(merge(base, prod, cli));

        System.out.println();
        System.out.println("=== YAML 多文档写法 ===");
        System.out.println("""
                spring:
                  application:
                    name: heima-demo
                ---
                spring:
                  config:
                    activate:
                      on-profile: dev
                server:
                  port: 9090
                ---
                spring:
                  config:
                    activate:
                      on-profile: prod
                server:
                  port: 80
                """);

        System.out.println("=== 优先级（高 -> 低）===");
        List<String> order = new ArrayList<>(List.of(
                "命令行参数 --server.port=8888",
                "Java 系统属性 -Dserver.port=8888",
                "操作系统环境变量 SERVER_PORT",
                "application-{profile}.yml",
                "application.yml"));
        for (int i = 0; i < order.size(); i++) {
            System.out.println("  " + (i + 1) + ". " + order.get(i));
        }

        System.out.println();
        System.out.println("注意：YAML 必须用空格缩进，用 Tab 会解析失败。");
    }

    @SafeVarargs
    private static Map<String, String> merge(Map<String, String>... layers) {
        Map<String, String> result = new LinkedHashMap<>();
        for (Map<String, String> layer : layers) {
            result.putAll(layer);   // 后面的覆盖前面的
        }
        return result;
    }

    private static void print(Map<String, String> config) {
        config.forEach((k, v) -> System.out.printf("  %-28s = %s%n", k, v));
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 建 `application.yml` + `application-dev.yml` + `application-prod.yml`，用 `spring.profiles.active` 切换，验证端口与日志级别变化。
 * 2. 用 `@ConfigurationProperties(prefix = "app")` 把一组配置绑定到 `AppProperties` 类，并在 Controller 里注入使用。
 * 3. 说明为什么数据库密码不应该写在 `application.yml` 里，给出两种更安全的做法。
 * ============================================================
 */
