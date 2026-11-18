/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：24-03
 * 课名：自动配置原理
 * ------------------------------------------------------------
 * 知识点：
 *   - 自动配置的入口是 `@EnableAutoConfiguration`，它通过 `@Import(AutoConfigurationImportSelector.class)` 加载配置类清单。
 *   - Spring Boot 2.7+ 的清单文件是 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`（3.x 沿用）。
 *   - 每个自动配置类上都有一串**条件注解**，只有条件满足才生效：
 *   - `@ConditionalOnClass`：classpath 上有指定类才生效。
 *   - `@ConditionalOnMissingBean`：容器里没有该 Bean 才生效（所以你自己定义的会覆盖默认的）。
 *   - `@ConditionalOnProperty`：配置项为指定值才生效。
 *   - `@EnableConfigurationProperties` 把 `application.yml` 里的配置绑定到 `@ConfigurationProperties` 类。
 *   - 排查自动配置：启动加 `--debug`，控制台会打印 `CONDITIONS EVALUATION REPORT`，明确哪些生效、哪些没生效及原因。
 *   - 易错：以为"引了 starter 就一定生效"，实际上缺数据源、缺配置项时会因为条件不满足而跳过。
 * ============================================================
 */

package com.heima.ch24;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class AutoConfigPrinciple {
    public static void main(String[] args) {
        // 模拟 classpath 上存在的类
        List<String> classpath = List.of(
                "jakarta.servlet.Servlet",
                "com.zaxxer.hikari.HikariDataSource",
                "com.mysql.cj.jdbc.Driver",
                "org.apache.ibatis.session.SqlSessionFactory");
        // 模拟用户自定义的 Bean
        List<String> userBeans = List.of("myDataSource");
        // 模拟配置项
        Map<String, String> props = new LinkedHashMap<>();
        props.put("spring.datasource.url", "jdbc:mysql://localhost:3306/heima_db");
        props.put("mybatis.mapper-locations", "classpath:mapper/*.xml");

        List<AutoConfig> configs = new ArrayList<>();
        configs.add(new AutoConfig("ServletWebServerFactoryAutoConfiguration",
                "jakarta.servlet.Servlet", null, null, "启动内嵌 Tomcat"));
        configs.add(new AutoConfig("DataSourceAutoConfiguration",
                "com.zaxxer.hikari.HikariDataSource", "dataSource",
                "spring.datasource.url", "创建 HikariCP 数据源"));
        configs.add(new AutoConfig("MybatisAutoConfiguration",
                "org.apache.ibatis.session.SqlSessionFactory", "sqlSessionFactory",
                "mybatis.mapper-locations", "创建 SqlSessionFactory 并扫描 Mapper"));

        System.out.println("=== 条件评估报告（模拟 --debug 输出）===");
        for (AutoConfig c : configs) {
            List<String> reasons = new ArrayList<>();
            boolean onClass = classpath.contains(c.onClass);
            if (!onClass) {
                reasons.add("缺少类 " + c.onClass);
            }
            if (c.missingBean != null && userBeans.contains(c.missingBean)) {
                reasons.add("容器已有 Bean " + c.missingBean + "，跳过默认配置");
            }
            if (c.onProperty != null && !props.containsKey(c.onProperty)) {
                reasons.add("缺少配置项 " + c.onProperty);
            }
            if (reasons.isEmpty()) {
                System.out.println("  [生效] " + c.name);
                System.out.println("         -> " + c.action);
            } else {
                System.out.println("  [跳过] " + c.name);
                reasons.forEach(r -> System.out.println("         原因：" + r));
            }
        }

        System.out.println();
        System.out.println("=== 三种条件注解的语义 ===");
        System.out.println("  @ConditionalOnClass      classpath 有某个类才生效（starter 在不在）");
        System.out.println("  @ConditionalOnMissingBean 容器里没有该 Bean 才生效（让你能覆盖默认）");
        System.out.println("  @ConditionalOnProperty    配置项为指定值才生效（功能开关）");

        System.out.println();
        System.out.println("=== 自动配置类清单文件（3.x）===");
        System.out.println("  META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports");
        System.out.println("  内容形如：");
        System.out.println("    org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration");
        System.out.println("    org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration");
    }

    record AutoConfig(String name, String onClass, String missingBean,
                      String onProperty, String action) {
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 启动时加 `--debug`，从 `CONDITIONS EVALUATION REPORT` 里找出 `DataSourceAutoConfiguration` 是否生效及原因。
 * 2. 自己写一个 `@ConditionalOnMissingBean` 的配置类，验证自定义 Bean 能覆盖 Boot 的默认 Bean。
 * 3. 说明 `@ConditionalOnProperty` 的典型使用场景，并给出一个配置示例。
 * ============================================================
 */
