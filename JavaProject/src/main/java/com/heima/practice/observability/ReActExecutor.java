/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：实操 44-01
 * 课名：ReAct 循环执行器
 * ------------------------------------------------------------
 * 知识点：
 *   - 【题目要求】实现 ReAct 循环执行器：工具注册表（查库存/计算/校验预算，模拟实现）、循环控制器（Thought->Action->Observation，最大 8 轮）、护栏（同一 Action+参数连续 2 次终止、工具错误返回结构化信息、偏离原计划重规划提示）；用“爆款补货”任务跑出完整轨迹。
 *   - 【考核点】循环终止条件、重复检测、错误作为信息（结构化回传）、轨迹可审计。
 *   - 【易错点】无最大轮数（死循环）；Observation 塞大 JSON（上下文爆炸）；Thought 不落轨迹（不可审计）。
 *   - 【提示】轨迹是 Agent 的行车记录仪：每轮 (thought, action, args, result, tokens) 全量落表。
 * ============================================================
 */

package com.heima.practice.observability;

import java.util.ArrayList;
import java.util.List;

// ==================== 示例代码 ====================

public class ReActExecutor {
    static void run(List<String> actions) {
        String last = null;
        int repeat = 0;
        for (int i = 0; i < actions.size(); i++) {
            String a = actions.get(i);
            if (a.equals(last)) {
                repeat++;
            } else {
                repeat = 0;
            }
            last = a;
            if (repeat >= 2) {
                System.out.printf("  轮%d 动作 %s 连续重复 -> 强制终止%n", i + 1, a);
                return;
            }
            System.out.printf("  轮%d 执行 %s%n", i + 1, a);
        }
        System.out.println("  正常完成");
    }

    public static void main(String[] args) {
        run(List.of("query", "query", "query", "calc"));
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现完整 ReAct 执行器：注册 3 个模拟工具 + 四道护栏，用补货任务跑通正常轨迹与“工具反复失败”轨迹。
 * 2. 给循环加检查点：第 N 轮崩溃后从检查点恢复，验证已完成步骤不重做。
 * 3. 思考题：为什么 Observation 必须精炼而 Thought 可以全量保留？从上下文成本与审计价值两个维度分析。
 * ============================================================
 */
