/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：72-03
 * 课名：反射、资源与代理配置
 * ------------------------------------------------------------
 * 知识点：
 *   - **AOT 需要三类显式配置**：**反射**（`reflect-config.json`：哪些类/方法/字段会被反射调用）、**资源**（`resource-config.json`：哪些文件要打进镜像）、**动态代理**（`proxy-config.json`：运行时生成的代理接口）——**配置缺失 = 运行期 `NoSuchMethodException`/`FileNotFoundException`**。
 *   - **反射配置的粒度**：类级（`allDeclaredMethods`/`allDeclaredFields`）与精确级（指定方法名与签名）——**粒度越细体积越小**，但维护成本越高；**实践：先开类级跑通，再逐步收细**。
 *   - **与 66 章 Agent 的关系**：Agent 增强的是"字节码"，AOT 需要的是"可达性声明"——**两者都在处理"运行时才知道的东西"，但方向相反**（Agent 是运行时改，AOT 是构建期定）。
 *   - **Spring Boot 3 的 AOT 处理**：框架自己生成大量 `reflect-config`（通过注解处理器扫描 `@Configuration`/`@Bean`/`@Entity`），**用户只需补"框架推断不到的部分"**（自定义反射、动态代理、资源文件）——**这就是 Spring Boot 3 支持 native 的方式**（72-05）。
 *   - **配置的诊断**：native-image 的 `--trace-class-initialization`、`-H:+PrintAnalysisCallTree`、运行期 `-agentlib:native-image-agent`（跑一遍收集配置）——**"跑一遍收集"是最实用的方法**。
 *   - 易错点：反射配置遗漏（运行期才报错）；资源文件没配置（`getResource` 返回 null）；动态代理没声明（`Proxy.newProxyInstance` 失败）；配置过度（全量 `allDeclared*` 导致体积膨胀）。
 * ============================================================
 */

package com.heima.ch72;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class AotConfiguration {
    record ReflectEntry(String className, List<String> methods, List<String> fields,
                        boolean allDeclared) {
        String toJson() {
            String methodsJson = methods.isEmpty() ? "[]" : methods.toString();
            return String.format(
                    "{\"name\":\"%s\",\"allDeclaredMethods\":%b,\"methods\":%s}",
                    className, allDeclared, methodsJson);
        }
    }

    record ProxyEntry(List<String> interfaces) {
        String toJson() {
            return "{\"interfaces\":" + interfaces + "}";
        }
    }

    record ResourceEntry(String pattern) {
        String toJson() {
            return "{\"pattern\":\"" + pattern + "\"}";
        }
    }

    static final List<ReflectEntry> reflectConfig = new ArrayList<>();
    static final List<ProxyEntry> proxyConfig = new ArrayList<>();
    static final List<ResourceEntry> resourceConfig = new ArrayList<>();

    static void collect() {
        reflectConfig.add(new ReflectEntry("com.heima.User", List.of("getName"), List.of(),
                false));
        reflectConfig.add(new ReflectEntry("com.heima.Order", List.of(), List.of(),
                true));
        proxyConfig.add(new ProxyEntry(List.of("com.heima.OrderService")));
        resourceConfig.add(new ResourceEntry("application.yml"));
        resourceConfig.add(new ResourceEntry("i18n/messages_zh_CN.properties"));
    }

    /** 校验：模拟运行期反射/资源访问，检查配置是否覆盖 */
    static List<String> validate(List<String> reflectUses, List<String> resourceUses,
                                 List<List<String>> proxyUses) {
        List<String> issues = new ArrayList<>();
        for (String use : reflectUses) {
            String cls = use.split("#")[0];
            String member = use.contains("#") ? use.split("#")[1] : null;
            boolean covered = reflectConfig.stream().anyMatch(e ->
                    e.className().equals(cls)
                            && (e.allDeclared() || member == null || e.methods().contains(member)));
            if (!covered) {
                issues.add("反射未配置：" + use);
            }
        }
        for (String res : resourceUses) {
            boolean covered = resourceConfig.stream()
                    .anyMatch(r -> res.startsWith(r.pattern().replace("**", ""))
                            || res.equals(r.pattern()));
            if (!covered) {
                issues.add("资源未配置：" + res);
            }
        }
        for (List<String> ifaces : proxyUses) {
            boolean covered = proxyConfig.stream()
                    .anyMatch(p -> p.interfaces().equals(ifaces));
            if (!covered) {
                issues.add("动态代理未配置：" + ifaces);
            }
        }
        return issues;
    }

    public static void main(String[] args) {
        collect();
        System.out.println("【reflect-config.json 片段】");
        reflectConfig.forEach(e -> System.out.println("  " + e.toJson()));
        System.out.println("【proxy-config.json】");
        proxyConfig.forEach(e -> System.out.println("  " + e.toJson()));
        System.out.println("【resource-config.json】");
        resourceConfig.forEach(e -> System.out.println("  " + e.toJson()));

        System.out.println();
        System.out.println("【运行期访问校验】");
        List<String> reflectUses = List.of(
                "com.heima.User#getName",
                "com.heima.Order#getAmount",
                "com.heima.Product#getPrice");
        List<String> resourceUses = List.of(
                "application.yml",
                "i18n/messages_zh_CN.properties",
                "static/logo.png");
        List<List<String>> proxyUses = List.of(
                List.of("com.heima.OrderService"),
                List.of("com.heima.UserService"));
        List<String> issues = validate(reflectUses, resourceUses, proxyUses);
        issues.forEach(i -> System.out.println("  " + i));

        System.out.println();
        System.out.println("【最实用的收集方法】");
        Map<String, String> method = new LinkedHashMap<>();
        method.put("步骤 1", "用 -agentlib:native-image-agent 跑一遍完整测试用例");
        method.put("步骤 2", "自动生成 reflect/resource/proxy 配置");
        method.put("步骤 3", "人工复核（去掉测试期临时反射的噪音）");
        method.put("步骤 4", "配置进版本库，随代码演进");
        method.forEach((k, v) -> System.out.printf("  %-6s %s%n", k, v));
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现配置校验器：给定运行期的反射/资源/代理使用清单，检查是否被配置覆盖，输出缺失项。
 * 2. 用 `native-image-agent`（或模拟）收集一份配置，对比"手工配置"与"agent 生成"的差异。
 * 3. 思考题：为什么"类级 `allDeclared*`"会让体积膨胀？从"整类方法/字段全部保留"与"可达性剪枝失效"分析。
 * ============================================================
 */
