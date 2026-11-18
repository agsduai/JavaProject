/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：实操 41-01
 * 课名：流水线闸门模拟器
 * ------------------------------------------------------------
 * 知识点：
 *   - 【题目要求】定义流水线阶段序列（compile 30s / lint 60s / unit-test 180s 并行 / package 45s / deploy-test 60s / smoke 90s，全部 blocking），输入各阶段执行结果（PASS/FAIL），输出关键路径耗时与“失败后后续 SKIP”的闸门行为；支持并行段（取并行段最大耗时）。
 *   - 【考核点】关键路径计算、闸门阻断语义、并行段耗时取 max、失败即停。
 *   - 【易错点】并行段耗时用求和（应为 max）；失败后仍继续执行后续阶段；跳过阶段不计入耗时。
 *   - 【提示】把阶段建模为 (名称, 耗时, 是否并行组成员, 是否 blocking)，先算关键路径，再按结果序列推演。
 * ============================================================
 */

package com.heima.practice.platform;

import java.util.List;

// ==================== 示例代码 ====================

public class PipelineGateTask {
    record Stage(String name, int costSec, boolean parallel) {
    }

    public static void main(String[] args) {
        List<Stage> stages = List.of(
                new Stage("build", 60, false),
                new Stage("test-a", 120, true),
                new Stage("test-b", 180, true),
                new Stage("deploy", 45, false));
        int path = 0;
        int i = 0;
        while (i < stages.size()) {
            Stage s = stages.get(i);
            if (s.parallel()) {
                int groupMax = 0;
                while (i < stages.size() && stages.get(i).parallel()) {
                    groupMax = Math.max(groupMax, stages.get(i).costSec());
                    i++;
                }
                path += groupMax;
            } else {
                path += s.costSec();
                i++;
            }
        }
        System.out.println("  关键路径 = " + path + "s");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现完整闸门模拟器：含失败 SKIP 传播与并行段耗时，用“unit-test 失败”与“全绿”两组输入验证。
 * 2. 给流水线加“并行段失败策略”：并行组内一个失败是否等其余完成（先算完整报告再判）？写出你的决策与理由。
 * 3. 思考题：为什么“红灯 30 分钟没人处理”会摧毁流水线价值？结合团队行为分析闸门可信度的形成条件。
 * ============================================================
 */
