/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：24-02
 * 课名：第一个Spring Boot工程
 * ------------------------------------------------------------
 * 知识点：
 *   - 工程结构：启动类放在最外层包（如 `com.heima`），其它包（`controller`、`service`、`mapper`）都放在它下面，才能被默认扫描到。
 *   - `@SpringBootApplication` 是三个注解的组合：`@SpringBootConfiguration` + `@EnableAutoConfiguration` + `@ComponentScan`。
 *   - 启动类必须放在能被扫描到的位置；放到 `com.heima.web` 下会导致 `com.heima.service` 不被扫描。
 *   - `SpringApplication.run(App.class, args)` 返回值是 `ConfigurableApplicationContext`，即 IOC 容器本体。
 *   - `application.yml` 里可改端口 `server.port`、应用名 `spring.application.name`、日志级别。
 *   - 易错：端口被占用时启动失败，报 `Port 8080 was already in use`；改端口或杀掉占用进程。
 * ============================================================
 */

package com.heima.ch24;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class FirstBootApp {
    public static void main(String[] args) {
        System.out.println("=== 启动类 ===");
        System.out.println("""
                @SpringBootApplication
                public class HeimaApplication {
                    public static void main(String[] args) {
                        SpringApplication.run(HeimaApplication.class, args);
                    }
                }
                """);

        // 模拟 @ComponentScan：从启动类所在包开始，扫描所有子包
        String basePackage = "com.heima";
        List<String> candidates = List.of(
                "com.heima.controller.StudentController",
                "com.heima.service.StudentService",
                "com.heima.service.impl.StudentServiceImpl",
                "com.heima.mapper.StudentMapper",
                "com.other.util.StringUtil");   // 不在扫描范围内

        System.out.println("=== 组件扫描（basePackage = " + basePackage + "）===");
        List<String> scanned = new ArrayList<>();
        for (String c : candidates) {
            boolean hit = c.startsWith(basePackage + ".");
            System.out.println((hit ? "  [注册] " : "  [跳过] ") + c);
            if (hit) {
                scanned.add(c);
            }
        }
        System.out.println("共注册 " + scanned.size() + " 个候选组件");

        System.out.println();
        System.out.println("=== 启动过程 ===");
        List<String> steps = List.of(
                "创建 SpringApplication 实例",
                "推断应用类型（SERVLET / REACTIVE / NONE）",
                "加载 ApplicationContextInitializer 与 Listener",
                "准备 Environment（读 application.yml、命令行参数、环境变量）",
                "创建并刷新 IOC 容器：扫描组件、执行自动配置、实例化单例 Bean",
                "启动内嵌 Web 服务器（Tomcat）",
                "发布 ApplicationReadyEvent，启动完成");
        for (int i = 0; i < steps.size(); i++) {
            System.out.println("  " + (i + 1) + ". " + steps.get(i));
        }

        System.out.println();
        System.out.println("=== application.yml ===");
        Map<String, Object> yml = new LinkedHashMap<>();
        yml.put("server.port", 8080);
        yml.put("spring.application.name", "heima-demo");
        yml.put("logging.level.com.heima", "debug");
        yml.forEach((k, v) -> System.out.println("  " + k + ": " + v));

        System.out.println();
        System.out.println("启动成功后访问：http://localhost:8080/");
        System.out.println("若报 Port 8080 was already in use，改 server.port 或结束占用进程。");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 用 Spring Initializr 或 IDEA 新建一个 Boot 工程，跑通 `HelloController`，访问 `http://localhost:8080/hello`。
 * 2. 把启动类移到 `com.heima.web` 包下，观察 `com.heima.service` 里的 `@Service` 是否还被注入，解释原因。
 * 3. 把端口改成 9090，验证生效；再说明 `application.yml` 和 `application.properties` 的优先级关系。
 * ============================================================
 */
