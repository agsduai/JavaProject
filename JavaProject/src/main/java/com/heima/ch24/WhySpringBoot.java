/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：24-01
 * 课名：为什么需要Spring Boot
 * ------------------------------------------------------------
 * 知识点：
 *   - 传统 Spring 项目的三大痛点：配置量大（XML 或 Java Config 一大片）、依赖版本要自己对齐、部署要外部 Tomcat。
 *   - Spring Boot 的三个核心能力：**起步依赖**（starter 一次性引入一组配套 jar）、**自动配置**（按 classpath 上的类自动装配 Bean）、**内嵌容器**（`java -jar` 直接起服务）。
 *   - "约定优于配置"：默认端口 8080、默认扫描启动类所在包及其子包、默认配置文件 `application.yml`。
 *   - 它不是替代 Spring，而是 Spring 的"脚手架"；底层仍然是 IOC 容器、AOP、MVC。
 *   - 版本策略：`2.x` 对应 JDK 8+，`3.x` 要求 JDK 17+，本项目统一用 JDK 21 + Spring Boot 3.x。
 *   - 易错：`spring-boot-starter-parent` 管的是"依赖版本"，`spring-boot-starter-web` 管的是"功能依赖"，两者职责不同。
 * ============================================================
 */

package com.heima.ch24;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class WhySpringBoot {
    public static void main(String[] args) {
        System.out.println("=== 传统 Spring 项目启动要做的事 ===");
        List<String> traditional = List.of(
                "web.xml 配置 DispatcherServlet",
                "spring-mvc.xml 配置视图解析器、注解驱动",
                "applicationContext.xml 配置数据源、事务管理器",
                "手工对齐 spring / mybatis / mysql 驱动的版本",
                "下载并配置外部 Tomcat，部署 war 包");
        traditional.forEach(s -> System.out.println("  - " + s));

        System.out.println();
        System.out.println("=== Spring Boot 项目启动要做的事 ===");
        List<String> boot = List.of(
                "pom.xml 引入 spring-boot-starter-web",
                "写一个带 @SpringBootApplication 的启动类",
                "运行 main 方法");
        boot.forEach(s -> System.out.println("  - " + s));
        System.out.println("  共 " + boot.size() + " 步，对比 " + traditional.size() + " 步");

        System.out.println();
        System.out.println("=== starter 帮你引入了什么 ===");
        Map<String, List<String>> starters = new LinkedHashMap<>();
        starters.put("spring-boot-starter-web", List.of(
                "spring-webmvc", "spring-web", "tomcat-embed-core",
                "jackson-databind", "spring-boot-starter-json"));
        starters.put("spring-boot-starter-jdbc", List.of(
                "spring-jdbc", "HikariCP", "spring-tx"));
        starters.put("mybatis-spring-boot-starter", List.of(
                "mybatis", "mybatis-spring", "spring-boot-starter-jdbc"));

        starters.forEach((k, v) -> {
            System.out.println("  " + k);
            v.forEach(dep -> System.out.println("      +- " + dep));
        });

        System.out.println();
        System.out.println("=== 自动配置做了什么 ===");
        List<String> autoConfig = new ArrayList<>();
        autoConfig.add("发现 classpath 有 tomcat-embed -> 启动内嵌 Tomcat，默认端口 8080");
        autoConfig.add("发现 classpath 有 DataSource 驱动 -> 创建 DataSource Bean");
        autoConfig.add("发现 classpath 有 mybatis -> 创建 SqlSessionFactory、扫描 Mapper");
        autoConfig.add("发现 classpath 有 jackson -> 注册 JSON 消息转换器");
        autoConfig.forEach(s -> System.out.println("  - " + s));

        System.out.println();
        System.out.println("结论：Boot 没有魔法，它只是把\"看见什么类就装配什么 Bean\"写成了规则。");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 列出一个传统 Spring MVC 项目必须手写的 5 个配置文件，说明 Spring Boot 分别用什么机制替代了它们。
 * 2. 解释 starter 和 parent 的区别，为什么说 starter 是"依赖聚合"而不是"代码"。
 * 3. Spring Boot 3.x 为什么要求 JDK 17+？从 Jakarta EE 命名空间迁移的角度回答。
 * ============================================================
 */
