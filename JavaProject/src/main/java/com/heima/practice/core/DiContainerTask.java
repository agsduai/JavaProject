/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：实操 19-02
 * 课名：依赖注入容器
 * ------------------------------------------------------------
 * 知识点：
 *   - 【题目要求】实现迷你 DI 容器：注解 `@Component` 与 `@Inject`（字段注入）；容器启动扫描已知类列表 -> 反射创建实例 -> 对标 @Inject 的字段 setAccessible 后注入依赖 -> get(Class) 获取；支持单例缓存与循环依赖检测（创建中标记）；用"订单服务依赖库存服务"演示三层注入。
 *   - 【考核点】反射创建实例、字段注入（setAccessible）、单例缓存、循环依赖检测（构建中集合）。
 *   - 【易错点】循环依赖无限递归（栈溢出）；每次 get 都 new（未单例）；构造器私有类的实例化处理。
 *   - 【提示】循环依赖检测用"创建中 Set"：进入创建前加入，完成后移除，再次遇到即报循环。
 * ============================================================
 */

package com.heima.practice.core;

import java.util.HashMap;
import java.util.Map;

// ==================== 示例代码 ====================

public class DiContainerTask {
    static final Map<Class<?>, Object> cache = new HashMap<>();

    static <T> T get(Class<T> cls) throws Exception {
        Object o = cache.get(cls);
        if (o == null) {
            o = cls.getDeclaredConstructor().newInstance();
            cache.put(cls, o);
        }
        return cls.cast(o);
    }

    static class Config {
    }

    public static void main(String[] args) throws Exception {
        Config a = get(Config.class);
        Config b = get(Config.class);
        System.out.println("  单例验证（同一实例）：" + (a == b));
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现完整 DI 容器（扫描 + 字段注入 + 单例 + 循环检测），用三层依赖链演示，并注入循环依赖验证报错。
 * 2. 支持 @Component("name") 命名与 @Inject 按类型+名字双维度查找。
 * 3. 思考题：Spring 的三级缓存为什么能解决"setter 循环依赖"却解决不了"构造器循环依赖"？结合本课"创建中标记"的时机分析。
 * ============================================================
 */
