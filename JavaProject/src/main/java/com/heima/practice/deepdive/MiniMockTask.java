/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：实操 33-02
 * 课名：Mock 框架原理
 * ------------------------------------------------------------
 * 知识点：
 *   - 【题目要求】实现迷你 Mock 框架（50-09 框架模拟呼应）：when(mock.method(args)).thenReturn(value) 语法的简化版——代理记录调用与参数 -> 打桩表（参数匹配 -> 预设返回）-> 验证模式（verify 调用次数）；用"订单服务 mock 仓储"演示单元测试隔离；输出打桩命中/未打桩默认值/验证失败三种场景。
 *   - 【考核点】动态代理记录调用、打桩表（参数匹配）、verify 验证、测试隔离边界。
 *   - 【易错点】未打桩方法返回 null 引发 NPE（默认值策略）；参数匹配用 equals（对象需自定义 equals）；verify 时机错误（先验证后调用）。
 *   - 【提示】未打桩返回类型默认值（数值 0、对象 null、集合空）——记录该行为进框架文档。
 * ============================================================
 */

package com.heima.practice.deepdive;

import java.util.LinkedHashMap;
import java.util.Map;

// ==================== 示例代码 ====================

public class MiniMockTask {
    static final Map<String, String> stubs = new LinkedHashMap<>();

    static String invoke(String method, String arg) {
        return stubs.getOrDefault(method + ":" + arg, "default-" + method);
    }

    public static void main(String[] args) {
        stubs.put("findById:1001", "found-user");
        System.out.println("  已打桩：" + invoke("findById", "1001"));
        System.out.println("  未打桩：" + invoke("findById", "9999") + "（默认值策略）");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现完整 Mock 框架（代理记录 + 打桩 + verify），订单服务测试演示"仓储 Mock + 业务断言"。
 * 2. 实现"参数匹配器"（any()/eq()），支持任意参数打桩并验证匹配优先级（精确 > any）。
 * 3. 思考题：为什么"过度 Mock"会让测试失去意义？从 Mock 边界（只 mock 依赖不 mock 被测对象）与实现耦合两个角度分析。
 * 
 * 
 * ## 第34章 数据结构与算法进阶
 * ============================================================
 */
