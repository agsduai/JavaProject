/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：66-02
 * 课名：类加载与 Instrumentation
 * ------------------------------------------------------------
 * 知识点：
 *   - **类加载的三步**：**加载**（读取字节码 -> 生成 Class 对象）、**链接**（验证/准备/解析）、**初始化**（执行 `<clinit>`）——**Agent 拦截的时机是"加载"阶段**：拿到字节码数组，改完再交给 JVM 定义类。
 *   - **双亲委派与 Agent 的关系**：Transformer 在**所有类加载器**的加载路径上都会被调用（含 JDK 类、三方库、应用类）——**`className` 是内部格式**（`com/heima/Foo`，斜杠），**要过滤**（只增强目标包，否则性能与误伤双问题）。
 *   - **`Instrumentation` 的核心 API**：`addTransformer(t, canRetransform)`、`retransformClasses(...)`（重跑 transformer）、`redefineClasses(...)`（直接替换字节码）、`isModifiableClass(...)`（判断能否改）。
 *   - **Transformer 的返回值语义**：返回 `null` = 不修改；返回新 `byte[]` = 用新字节码定义类；**抛异常 = 类加载失败**（应用启动崩）——**所以 transformer 必须包 try-catch，增强失败要降级为"原样加载"**（44-08 降级思想），否则 Agent 会变成故障源。
 *   - **性能要求**：Transformer 对**每个类**都调用（一个应用几千上万类），**必须极快**（微秒级）：先做字符串前缀过滤，再解析字节码；**不要在里面做 IO 或重计算**。
 *   - 易错点：Transformer 抛异常（应用起不来）；不过滤类名（增强 JDK 类导致诡异问题）；在 transformer 里做 IO（启动慢十倍）；忘记 `canRetransform` 标志（后续无法重新增强）。
 * ============================================================
 */

package com.heima.ch66;

import java.util.ArrayList;
import java.util.List;

// ==================== 示例代码 ====================

public class InstrumentationDemo {
    /** 简化的 ClassFileTransformer：入参类名与字节码，返回 null 或新字节码 */
    interface Transformer {
        byte[] transform(String className, byte[] classBytes);
    }

    /** 简化版 Instrumentation：注册 transformer 链，按顺序应用 */
    static class MiniInstrumentation {
        final List<Transformer> transformers = new ArrayList<>();
        final List<String> loadLog = new ArrayList<>();

        void addTransformer(Transformer t) {
            transformers.add(t);
            System.out.println("  注册 transformer：" + t.getClass().getSimpleName());
        }

        /** 模拟类加载：依次过 transformer，任一返回非 null 则替换字节码 */
        byte[] loadClass(String className, byte[] bytes) {
            byte[] current = bytes;
            for (Transformer t : transformers) {
                try {
                    byte[] next = t.transform(className, current);
                    if (next != null) {
                        current = next;
                        loadLog.add(className + " 被 " + t.getClass().getSimpleName() + " 增强");
                    }
                } catch (RuntimeException e) {
                    loadLog.add(className + " 增强失败 -> 降级为原样加载（" + e.getMessage() + "）");
                }
            }
            return current;
        }
    }

    /** 目标包过滤：只增强 com/heima/ 下的类（性能与安全双要求） */
    static class PackageFilterTransformer implements Transformer {
        final String prefix;

        PackageFilterTransformer(String prefix) {
            this.prefix = prefix;
        }

        public byte[] transform(String className, byte[] classBytes) {
            if (!className.startsWith(prefix)) {
                return null;                        // 快速否定，不做任何解析
            }
            return (">>" + new String(classBytes)).getBytes();   // 简化：标记增强
        }
    }

    /** 会抛异常的 transformer：验证降级 */
    static class BuggyTransformer implements Transformer {
        public byte[] transform(String className, byte[] classBytes) {
            if (className.contains("Broken")) {
                throw new IllegalStateException("模拟增强失败");
            }
            return null;
        }
    }

    public static void main(String[] args) {
        MiniInstrumentation inst = new MiniInstrumentation();
        inst.addTransformer(new PackageFilterTransformer("com/heima/"));
        inst.addTransformer(new BuggyTransformer());

        System.out.println();
        System.out.println("【类加载模拟】");
        String[][] classes = {
                {"com/heima/OrderService", "class OrderService"},
                {"java/lang/String", "class String"},
                {"org/spring/Bean", "class Bean"},
                {"com/heima/BrokenService", "class BrokenService"}};
        for (String[] c : classes) {
            byte[] loaded = inst.loadClass(c[0], c[1].getBytes());
            System.out.printf("  %-26s -> %s%n", c[0],
                    new String(loaded).startsWith(">>") ? "已增强" : "原样");
        }
        System.out.println();
        System.out.println("【加载日志】");
        inst.loadLog.forEach(l -> System.out.println("  " + l));
        System.out.println();
        System.out.println("  要点：命名空间用斜杠；先前缀过滤；异常降级为原样加载");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现完整 Transformer 链：包过滤 + 计时 + 异常降级，用 5 个类（含 1 个会抛异常的）验证增强与降级。
 * 2. 用真实 Agent API 写一个统计"每个包加载了多少类"的 Agent，挂到一个 Spring Boot 应用观察输出。
 * 3. 思考题：为什么 Transformer 必须对异常做降级？从"Agent 是旁路组件"与"增强失败不应影响主流程"分析。
 * ============================================================
 */
