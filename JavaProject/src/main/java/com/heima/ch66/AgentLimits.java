/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：66-07
 * 课名：Agent 的边界：不能做什么
 * ------------------------------------------------------------
 * 知识点：
 *   - **JVM 的 redefinition 限制是硬边界**：**不能改方法签名**（参数/返回值）、**不能改字段类型**、**不能删除方法/字段**、**不能改父类与接口列表**——**Agent 是"增强"，不是"重构"**。
 *   - **能不能加方法/字段？** 标准 `Instrumentation` **不支持新增方法或字段**（只能改已有方法的实现、改常量池引用）；**ByteBuddy 的 `redefine` 可有限新增**（通过重写类结构，但不是所有场景都安全，且需 `Can-Redefine-Classes`）。
 *   - **不能拦截的场景**：**JDK 内部类**（`java.*` 常被限制增强，且极危险）、**native 方法**（无字节码可改）、**已被 JIT 编译的热点代码**（retransform 会触发重新编译，但时机与开销不可控）、**final/private 方法**（可增强但没有虚调用语义）。
 *   - **Agent 与技术选型的边界**：**业务逻辑增强用 AOP（60-04）**；**跨切面（日志/监控/安全）才用 Agent**——**Agent 是"最后手段"，因为它不可见、难调试、有版本耦合**。
 *   - **与 59/60 的呼应**：59-10 的"表结构演进要重建"、60-03 的"Bean 生命周期不可逆"——**任何"运行时修改"都有结构约束**，Agent 也不例外。
 *   - 易错点：以为 Agent 能"任意改代码"（redefinition 限制）；尝试增强 JDK 类（风险极高且常失败）；用 Agent 替代正常设计（不可见的技术债）；忽略 JIT 与 retransform 的交互（改完行为可能不立即生效）。
 * ============================================================
 */

package com.heima.ch66;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class AgentLimits {
    record Capability(String change, boolean allowed, String reason) {
    }

    static void matrix() {
        System.out.println("【redefinition 能力矩阵】");
        List<Capability> caps = List.of(
                new Capability("修改方法体（插桩/替换实现）", true, "最常用，APM 全靠它"),
                new Capability("修改常量池引用（如换字符串）", true, "改常量值可行"),
                new Capability("新增方法", false, "标准 Instrumentation 不支持"),
                new Capability("新增字段", false, "同上"),
                new Capability("修改方法签名", false, "调用点已链接，无法改"),
                new Capability("修改字段类型", false, "内存布局已确定"),
                new Capability("删除方法/字段", false, "同上"),
                new Capability("修改父类/接口列表", false, "类型体系不可变"));
        for (Capability c : caps) {
            System.out.printf("  %-32s %s  原因：%s%n", c.change(),
                    c.allowed() ? "允许" : "禁止", c.reason());
        }
        System.out.println();
    }

    static void notEnhanceable() {
        System.out.println("【难以增强的场景】");
        Map<String, String> cases = new LinkedHashMap<>();
        cases.put("JDK 内部类", "java.* 增强极危险，多数环境禁止");
        cases.put("native 方法", "没有字节码，无处插桩");
        cases.put("已被 JIT 编译的热点", "retransform 需重新编译，时机与开销不可控");
        cases.put("final/private 方法", "可改字节码，但无虚调用语义，切点匹配易错");
        cases.forEach((k, v) -> System.out.printf("  %-16s %s%n", k, v));
        System.out.println();
    }

    static void chooseTool() {
        System.out.println("【工具选型边界】");
        String[][] rows = {
                {"业务横切逻辑（日志/事务）", "Spring AOP（60-04）", "声明式、可调试、无版本耦合"},
                {"老系统零改造接入监控", "Java Agent（本章）", "不可见、有耦合，但成本最低"},
                {"热修复线上 bug", "Agent + redefine（66-08）", "应急手段，事后必须回归代码"},
                {"纯诊断（看栈/看字节码）", "jstack/javap/arthas", "不改变行为，最安全"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-24s -> %-28s %s%n", r[0], r[1], r[2]);
        }
        System.out.println();
        System.out.println("  原则：能改代码就别用 Agent；必须零侵入时才用，并把它当「基础设施」治理（66-10）");
    }

    public static void main(String[] args) {
        matrix();
        notEnhanceable();
        chooseTool();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 用真实 Agent 尝试"给类新增一个方法"，记录报错信息并确认限制（用 `javassist`/ByteBuddy redefine 也试一次对比）。
 * 2. 列出你项目里"适合 Agent"与"应该改代码"的场景各 3 个，说明判据（侵入性 vs 可维护性）。
 * 3. 思考题：为什么"不能改方法签名"是 JVM 的结构性限制？从调用点已编译为固定描述符的角度分析。
 * ============================================================
 */
