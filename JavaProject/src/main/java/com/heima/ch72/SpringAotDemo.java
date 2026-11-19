/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：72-05
 * 课名：Spring Boot 3 AOT 与 native
 * ------------------------------------------------------------
 * 知识点：
 *   - **Spring Boot 3 支持 native 的关键是 AOT 引擎**：构建时（`mvn -Pnative native:compile` 或 `spring-boot:process-aot`）**扫描应用上下文 -> 生成 `reflect-config`/`proxy-config`/`resource-config`/`BeanDefinition` 代码**——**把"运行期反射装配"变成"构建期生成的代码"**（60-02 的 IOC 在 AOT 下的变形）。
 *   - **AOT 处理的三类产物**：**反射/资源/代理配置**（72-03）、**Bean 注册代码**（生成 `ApplicationContextInitializer`）、**代理类**（CGLIB 代理在构建期生成）——**所以启动时几乎不反射**。
 *   - **限制与约束**：**`@Profile` 在构建期固定**（运行时切 profile 需要在构建期决定，或多 profile 构建）、**懒加载语义受限**、**`@Bean` 方法返回类型必须可分析**、**部分动态特性不可用**（如 `@Scheduled` 的 cron 在运行期可改，但有些动态注册不行）。
 *   - **GraalVM 的 Spring Boot 支持模块**：官方提供 `spring-boot-starter-native`（或 `org.springframework.aot` 依赖）处理框架层的 native 适配——**用户只需处理业务代码的动态特性**。
 *   - **与 37/47 章的关系**：37 章容器化（镜像小）、47 章 Serverless（冷启动）——**native 镜像把两者结合**（几十 MB 镜像 + 毫秒启动）。
 *   - 易错点：以为"加个依赖就能 native"（业务反射仍要配置）；profile 在运行期切换（构建期已固定）；用反射读配置类（配置需在构建期可见）；不跑集成测试（native 下的行为差异需验证）。
 * ============================================================
 */

package com.heima.ch72;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class SpringAotDemo {
    record BeanDef(String name, String type, String scope, boolean lazy,
                   List<String> deps) {
    }

    /** 模拟 AOT 引擎：扫描 Bean 定义 -> 生成注册代码（而非运行期反射） */
    static String generateRegistrar(List<BeanDef> beans) {
        StringBuilder sb = new StringBuilder();
        sb.append("// 构建期生成，勿修改\n");
        sb.append("public class GeneratedBeanRegistrar implements ApplicationContextInitializer {\n");
        sb.append("  public void initialize(ConfigurableApplicationContext ctx) {\n");
        for (BeanDef b : beans) {
            sb.append("    ctx.registerBean(\"").append(b.name()).append("\", ")
                    .append(b.type()).append(".class, () -> new ").append(b.type())
                    .append("(");
            for (int i = 0; i < b.deps().size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append("ctx.getBean(\"").append(b.deps().get(i)).append("\")");
            }
            sb.append("));\n");
        }
        sb.append("  }\n}\n");
        return sb.toString();
    }

    /** 检查 Bean 定义是否 AOT 友好 */
    static List<String> check(List<BeanDef> beans) {
        List<String> issues = new ArrayList<>();
        for (BeanDef b : beans) {
            if (b.type().contains("InvocationHandler")) {
                issues.add(b.name() + "：动态代理类型无法在构建期确定");
            }
            if (b.scope().equals("prototype") && b.lazy()) {
                issues.add(b.name() + "：prototype + lazy 在 AOT 下语义受限");
            }
        }
        return issues;
    }

    public static void main(String[] args) {
        List<BeanDef> beans = List.of(
                new BeanDef("dataSource", "DataSource", "singleton", false, List.of()),
                new BeanDef("orderDao", "OrderDao", "singleton", false, List.of("dataSource")),
                new BeanDef("orderService", "OrderService", "singleton", false,
                        List.of("orderDao")),
                new BeanDef("requestCtx", "RequestCtx", "prototype", true, List.of()));

        System.out.println("【Bean 定义（运行期反射装配的形态）】");
        beans.forEach(b -> System.out.printf("  %-12s %-16s scope=%-10s lazy=%-5s deps=%s%n",
                b.name(), b.type(), b.scope(), b.lazy(), b.deps()));

        System.out.println();
        System.out.println("【AOT 生成的注册器（构建期固定）】");
        System.out.println(generateRegistrar(beans));

        System.out.println();
        System.out.println("【AOT 友好性检查】" + (check(beans).isEmpty() ? "通过" : check(beans)));

        System.out.println();
        System.out.println("【构建命令与产物】");
        Map<String, String> build = new LinkedHashMap<>();
        build.put("处理 AOT", "mvn spring-boot:process-aot");
        build.put("编译原生", "mvn -Pnative native:compile");
        build.put("产物", "target/应用名（可执行文件，约 60~120MB）");
        build.put("运行", "./应用名（0.1s 启动，无 JVM）");
        build.forEach((k, v) -> System.out.printf("  %-8s %s%n", k, v));

        System.out.println();
        System.out.println("【Spring Boot 3 的 native 限制清单】");
        List<String> limits = List.of(
                "@Profile 在构建期固定（多环境需分别构建或运行期用配置类）",
                "懒加载语义受限（AOT 生成的是静态注册）",
                "@Bean 方法返回类型需可分析（避免 Object/泛型擦除）",
                "动态注册 Bean（BeanDefinitionRegistryPostProcessor 动态加类）需显式声明");
        limits.forEach(l -> System.out.println("  - " + l));
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现 Bean 注册器代码生成：从 Bean 定义列表生成 `ApplicationContextInitializer` 风格的注册代码。
 * 2. 列出 Spring Boot 3 native 的 4 条限制，各给一个"会踩坑"的业务写法与规避方式。
 * 3. 思考题：为什么 `@Profile` 在 native 下必须在构建期固定？从"条件装配代码是构建期生成的"分析。
 * ============================================================
 */
