/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：实操 24-01
 * 课名：自动配置模拟
 * ------------------------------------------------------------
 * 知识点：
 *   - 【题目要求】模拟 Spring Boot 自动配置：条件注解 `@ConditionalOnClass("redis")` 与 `@ConditionalOnMissingBean`；类路径探测（模拟 Map 存在性）-> 满足条件注册默认 Bean -> 用户显式定义的 Bean 优先（MissingBean 检查）；输出容器装配过程（哪些自动配置生效、哪些被用户覆盖）。
 *   - 【考核点】自动配置原理（条件装配）、约定优于配置、用户覆盖优先级、装配日志可观测。
 *   - 【易错点】以为自动配置是"魔法"（本质是条件化注册）；用户 Bean 与自动 Bean 冲突时报错而非覆盖；条件探测顺序未定义。
 *   - 【提示】按"用户 Bean 先注册 -> 自动配置只补缺失"的顺序实现，即 MissingBean 语义。
 * ============================================================
 */

package com.heima.practice.framework;

import java.util.HashMap;
import java.util.Map;

// ==================== 示例代码 ====================

public class AutoConfigTask {
    record BeanDef(String name, String source) {
    }

    static Map<String, BeanDef> container = new HashMap<>();

    static void register(String name, String source, boolean condition, boolean missing) {
        if (condition && missing) {
            container.put(name, new BeanDef(name, source));
            System.out.printf("  注册 %s（来源：%s）%n", name, source);
        } else if (condition && !missing) {
            System.out.printf("  跳过 %s：用户已定义%n", name);
        } else {
            System.out.printf("  跳过 %s：条件不满足%n", name);
        }
    }

    public static void main(String[] args) {
        container.put("myCache", new BeanDef("myCache", "user"));      // 用户已定义
        register("cache", "auto-config", true, !container.containsKey("myCache"));
        register("queue", "auto-config", false, true);                 // classpath 无 MQ -> 不装配
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现完整自动配置模拟（classpath 探测 + MissingBean + 装配日志），演示"引入依赖即生效"与"用户覆盖"。
 * 2. 实现 `@ConditionalOnProperty`（配置开关控制装配），演示开关切换两种实现。
 * 3. 思考题：为什么 Spring Boot 能"零配置启动"？从"约定 + 条件装配 + starter 聚合"三层拆解其原理。
 * ============================================================
 */
