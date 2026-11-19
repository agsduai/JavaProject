/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：72-02
 * 课名：AOT 与封闭世界假设
 * ------------------------------------------------------------
 * 知识点：
 *   - **封闭世界假设（Closed-World Assumption）是 AOT 的地基**：**构建期必须能看到"运行时会被用到的所有代码"**——不能在运行时动态加载未知类（`Class.forName` 的字符串参数、脚本引擎、动态代理生成类）。
 *   - **AOT 的三步**：**静态分析**（从入口点（main）出发，找到所有可达的类与方法）-> **编译**（生成机器码 + 堆布局 + 元数据）-> **链接**（打包成可执行文件）。
 *   - **被"剪掉"的代码**：不可达的方法会被删除（这是体积小的原因）——**但通过反射调用的方法在分析时"看起来不可达"**，会被误删（72-03 的配置就是解决这个）。
 *   - **与 JIT 的对比**：JIT **运行期**看到真实调用（可内联、可去虚拟化、可逃逸分析）；AOT **构建期**只能按静态信息保守优化——**信息缺失 = 优化保守 = 峰值略低**。
 *   - **AOT 友好的代码写法**：**显式注册反射**（72-03）、**避免动态类加载**、**用工厂/注解处理器替代反射**、**序列化框架用 AOT 友好版本**（如 Jackson 的 AOT 支持）。
 *   - 易错点：以为"代码不用改就能 AOT"（动态特性必然要配置）；忽略类初始化时机（72-04）；用脚本引擎/JSP 等运行时编译特性（AOT 无解）；分析不完整导致运行期 NoClassDefFoundError。
 * ============================================================
 */

package com.heima.ch72;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

// ==================== 示例代码 ====================

public class ClosedWorld {
    /** 模拟类图：类 -> 直接引用的类（构建期可见的静态引用） */
    static final Map<String, List<String>> staticRefs = new LinkedHashMap<>();

    static void addClass(String name, String... refs) {
        staticRefs.put(name, List.of(refs));
    }

    /** 静态可达性分析：从入口 BFS */
    static Set<String> reachable(String entry) {
        Set<String> visited = new HashSet<>();
        List<String> queue = new ArrayList<>();
        queue.add(entry);
        while (!queue.isEmpty()) {
            String cur = queue.remove(0);
            if (!visited.add(cur)) {
                continue;
            }
            for (String ref : staticRefs.getOrDefault(cur, List.of())) {
                if (!visited.contains(ref)) {
                    queue.add(ref);
                }
            }
        }
        return visited;
    }

    public static void main(String[] args) {
        addClass("Main", "OrderController", "ReflectionLoader");
        addClass("OrderController", "OrderService");
        addClass("OrderService", "OrderDao", "OrderSerializer");
        addClass("OrderDao", "DataSource");
        addClass("OrderSerializer", "OrderDto");
        addClass("ReflectionLoader");                     // 只通过反射加载
        addClass("OrderDto");
        addClass("PluginA");                              // 通过 Class.forName 加载
        addClass("PluginB");

        Set<String> reachable = reachable("Main");
        System.out.println("【静态可达类（AOT 会编译进镜像）】");
        reachable.forEach(c -> System.out.println("  " + c));

        List<String> all = new ArrayList<>(staticRefs.keySet());
        List<String> pruned = new ArrayList<>();
        for (String c : all) {
            if (!reachable.contains(c)) {
                pruned.add(c);
            }
        }
        System.out.println();
        System.out.println("【被剪掉的类（分析时不可达）】" + pruned);
        System.out.println("  风险：PluginA/PluginB 是运行时 Class.forName 加载的 -> 被误删");
        System.out.println("  解法：显式注册（72-03 的 reflection-config）");

        System.out.println();
        System.out.println("【封闭世界 vs 开放世界】");
        Map<String, String> compare = new LinkedHashMap<>();
        compare.put("AOT", "构建期确定全部代码；动态加载必须提前声明");
        compare.put("JIT", "运行期边加载边编译；Class.forName 任意字符串都能工作");
        compare.forEach((k, v) -> System.out.printf("  %-4s %s%n", k, v));

        System.out.println();
        System.out.println("【AOT 友好的写法】");
        List<String> friendly = List.of(
                "用接口 + 显式实现替代反射实例化",
                "用注解处理器在构建期生成代码（而非运行时反射）",
                "序列化框架选 AOT 支持版本（如 Jackson 的 AOT 模块）",
                "避免脚本引擎 / JSP / 运行时字节码生成");
        friendly.forEach(f -> System.out.println("  - " + f));
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现静态可达性分析器：给定类图与入口，输出"可达类/被剪类"，构造反射加载的类被误删的场景。
 * 2. 改造一段"用 `Class.forName` 实例化插件"的代码为"显式注册工厂"，说明为何对 AOT 友好。
 * 3. 思考题：为什么"封闭世界假设"必然导致"反射需要配置"？从静态分析无法推断字符串参数分析。
 * ============================================================
 */
