/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：实操 19-01
 * 课名：注解扫描与迷你路由
 * ------------------------------------------------------------
 * 知识点：
 *   - 【题目要求】定义注解 `@Route(path)` 与 `@Method(GET/POST)`；写三个带注解的处理类方法；实现"扫描器"：遍历类的方法（getDeclaredMethods），读取注解构建路由表（path -> Method 实例）；实现"分发器"：按 path+method 反射调用（invoke）并返回结果；模拟 404 与 405 两种未命中。
 *   - 【考核点】自定义注解（@Retention RUNTIME/@Target）、反射读取注解、Method.invoke、路由表设计、404/405 语义。
 *   - 【易错点】Retention 写成 SOURCE（运行时拿不到）；invoke 忘记 setAccessible（私有方法）；路由匹配用前缀 contains（应精确或分段匹配）。
 *   - 【提示】路由表 key 用 "GET /users" 组合键；分发器演示参数注入（简单类型从模拟请求取值）。
 * ============================================================
 */

package com.heima.practice.core;

import java.lang.annotation.*;
import java.lang.reflect.Method;

// ==================== 示例代码 ====================

public class RouteScannerTask {
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface Job {
        String value();
    }

    static class Tasks {
        @Job("backup")
        void doBackup() {
            System.out.println("  执行备份");
        }

        @Job("cleanup")
        void doCleanup() {
            System.out.println("  执行清理");
        }
    }

    public static void main(String[] args) throws Exception {
        Tasks tasks = new Tasks();
        for (Method m : Tasks.class.getDeclaredMethods()) {
            Job job = m.getAnnotation(Job.class);
            if (job != null && job.value().equals("cleanup")) {
                m.invoke(tasks);
            }
        }
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现完整迷你路由（扫描表 + 分发器 + 404/405），用三个路由演示命中与未命中。
 * 2. 支持路径参数（/users/{id}），实现参数注入 invoke（简单类型转换）。
 * 3. 思考题：为什么 Spring MVC 的 @RequestMapping 本质上就是本课的放大版？从"注解即元数据、框架即扫描器+分发器"的结构分析。
 * ============================================================
 */
