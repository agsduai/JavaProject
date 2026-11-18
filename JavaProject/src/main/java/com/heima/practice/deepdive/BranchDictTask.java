/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：实操 31-01
 * 课名：策略模式重构
 * ------------------------------------------------------------
 * 知识点：
 *   - 【题目要求】给定 200 行风格的 if-else 计价逻辑（会员等级 × 优惠类型 × 叠加），先用"分支字典"重构（Map<String, Function>），再用策略接口 + 枚举注册重构（49 章营销建模呼应）；三个版本做同输入同输出的回归验证；输出"新增一种优惠的改动面"对比（原 N 行改动 vs 新增 1 类）。
 *   - 【考核点】分支字典、策略模式、开闭原则的量化验证、回归测试先行（49-10）。
 *   - 【易错点】策略粒度过细（每个 if 一个类）；重构无回归验证（行为漂移）；字典 key 用魔法字符串。
 *   - 【提示】先写 20 组输入输出的"行为快照"（黄金样本），重构后逐一比对——这是重构安全网。
 * ============================================================
 */

package com.heima.practice.deepdive;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

// ==================== 示例代码 ====================

public class BranchDictTask {
    static final Map<String, java.util.function.UnaryOperator<Integer>> RULES = new LinkedHashMap<>();

    static {
        RULES.put("vip", x -> x * 90 / 100);
        RULES.put("svip", x -> x * 80 / 100);
        RULES.put("new", x -> x - 500);
    }

    static int apply(String type, int price) {
        return RULES.getOrDefault(type, java.util.function.UnaryOperator.identity()).apply(price);
    }

    public static void main(String[] args) {
        for (String t : new String[]{"vip", "svip", "new", "unknown"}) {
            System.out.printf("  %-8s %d -> %d%n", t, 10000, apply(t, 10000));
        }
        System.out.println("  新增优惠 = 加一行注册，不改主流程");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 完成"黄金样本 -> 分支字典 -> 策略接口"三步重构，每步跑回归并输出 diff 为 0 的证据。
 * 2. 为策略实现"叠加规则"（49-09 StackingRule），验证重构后叠加逻辑的可扩展性。
 * 3. 思考题：什么时候不该用策略模式？从分支数量、变化频率与可读性的三角关系分析。
 * ============================================================
 */
