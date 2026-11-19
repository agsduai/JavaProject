/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：66-01
 * 课名：Agent 全景：premain 与 agentmain
 * ------------------------------------------------------------
 * 知识点：
 *   - **Java Agent 是"在 JVM 启动前或运行中增强类行为"的官方机制**：通过 `-javaagent:xxx.jar` 或 `VirtualMachine.attach` 挂载，**拿到 `Instrumentation` 实例**，从而在类加载时（或已加载后）修改字节码——**这是 APM、热修复、诊断工具的技术底座**（60-08 的 SkyWalking Agent 就在这里）。
 *   - **两种入口**：**premain**（`-javaagent` 启动时加载，**在 main 之前执行**，可注册 ClassFileTransformer 拦截后续所有类加载）；**agentmain**（运行时 attach，**可重定义已加载的类**，用于热更新与动态诊断）。
 *   - **Agent 的三件套**：`MANIFEST.MF` 声明入口（`Premain-Class`/`Agent-Class`/`Can-Redefine-Classes`）、**入口方法签名**（`premain(String args, Instrumentation inst)`）、**Transformer 实现**（`transform(className, bytes) -> newBytes 或 null`）。
 *   - **能做什么与不能做什么**：能改方法体（插桩、埋点、耗时统计）、能改类结构（加字段/方法，有限制）、不能改**方法签名**（参数/返回值）、不能改**已有字段的类型**、不能删方法（JVM 的 redefinition 限制）。
 *   - **与 60 章的关系**：60-08 讲了"SkyWalking Agent 怎么用字节码增强做零侵入埋点"，本章讲"**自己怎么写一个 Agent**"——从使用者视角到实现者视角。
 *   - 易错点：以为 Agent 能任意改类（受 redefinition 限制）；premain 里做耗时操作（拖慢整个 JVM 启动）；Transformer 抛异常（类加载失败，应用直接崩）；忘记 `Can-Retransform-Classes`（attach 后无法重定义）。
 * ============================================================
 */

package com.heima.ch66;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class AgentOverview {
    record Entry(String name, String when, String capability, String limit) {
    }

    static void entries() {
        System.out.println("【两种 Agent 入口】");
        List<Entry> entries = List.of(
                new Entry("premain", "-javaagent 启动时，main 之前",
                        "拦截后续所有类加载（ClassFileTransformer）", "不能改已加载的类"),
                new Entry("agentmain", "运行时 attach（VirtualMachine）",
                        "可重定义已加载的类（retransform/redefine）", "受 redefinition 限制"));
        for (Entry e : entries) {
            System.out.printf("  %-10s %-28s 能力=%-32s 限制=%s%n",
                    e.name(), e.when(), e.capability(), e.limit());
        }
        System.out.println();
    }

    static void lifecycle() {
        System.out.println("【预加载链路（premain）】");
        List<String> steps = List.of(
                "1) JVM 解析 -javaagent:agent.jar",
                "2) 读取 MANIFEST.MF 的 Premain-Class",
                "3) 加载入口类，调用 premain(String args, Instrumentation inst)",
                "4) Agent 注册 ClassFileTransformer",
                "5) JVM 继续启动 -> 每个类加载时回调 transformer",
                "6) Transformer 返回 null（不改）或 newBytes（改后加载）");
        steps.forEach(s -> System.out.println("  " + s));
        System.out.println();
    }

    static void limits() {
        System.out.println("【能力边界（redefinition 限制）】");
        Map<String, String> limits = new LinkedHashMap<>();
        limits.put("可改", "方法体指令、常量池引用、已有方法的实现");
        limits.put("可加", "新方法、新字段（Instrumentation.redefineClasses 有限支持）");
        limits.put("不可改", "方法签名（参数/返回值）、字段类型、父类、接口列表");
        limits.put("不可删", "已有方法/字段");
        limits.forEach((k, v) -> System.out.printf("  %-4s %s%n", k, v));
        System.out.println();
        System.out.println("  推论：Agent 增强是「包裹与插桩」，不是「重构类结构」");
    }

    public static void main(String[] args) {
        entries();
        lifecycle();
        limits();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 写一个最小 premain Agent（打印所有加载的类名并计数），用 `-javaagent` 挂到任意程序上验证输出。
 * 2. 对比 premain 与 agentmain 的时机与能力差异，各举一个真实用途（APM vs 热修复）。
 * 3. 思考题：为什么 Agent 不能改方法签名？从 JVM 的类元数据与已编译调用点的链接方式分析。
 * ============================================================
 */
