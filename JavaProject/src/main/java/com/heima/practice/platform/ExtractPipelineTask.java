/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：实操 40-01
 * 课名：结构化抽取管道
 * ------------------------------------------------------------
 * 知识点：
 *   - 【题目要求】为客服工单实现“意图抽取管道”：规则模拟模型输出 JSON（intent/orderId/emotion）-> schema 校验（字段齐全、枚举合法、orderId 纯数字或 null）-> 校验失败重试（最多 2 次，重试时在提示中附上错误说明）-> 全部失败转人工。用 6 条输入（含 2 条会失败的）验证全流程。
 *   - 【考核点】结构化输出契约、schema 校验设计、失败重试与错误反馈（40-03）、兜底路径（转人工）。
 *   - 【易错点】重试不带错误说明（模型无从修正）；校验失败静默丢弃（无兜底）；JSON 直接反序列化不校验。
 *   - 【提示】把“校验错误”结构化为列表传回提示——这是真实 LLM 应用的重试姿势。
 * ============================================================
 */

package com.heima.practice.platform;

import java.util.ArrayList;
import java.util.List;

// ==================== 示例代码 ====================

public class ExtractPipelineTask {
    record Attempt(int no, String output, List<String> errors) {
    }

    static List<String> validate(String output) {
        List<String> errors = new ArrayList<>();
        if (!output.startsWith("{")) {
            errors.add("输出必须是 JSON");
        }
        if (!output.contains("\"city\"")) {
            errors.add("缺少 city 字段");
        }
        return errors;
    }

    public static void main(String[] args) {
        String[] outputs = {"not-json", "{\"name\":\"x\"}", "{\"name\":\"x\",\"city\":\"杭州\"}"};
        int round = 0;
        for (String o : outputs) {
            round++;
            List<String> errs = validate(o);
            System.out.printf("  第%d轮：%s -> %s%n", round, o, errs.isEmpty() ? "通过" : errs);
            if (errs.isEmpty()) {
                break;
            }
        }
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现完整抽取管道：含错误反馈式重试与转人工兜底，用 6 条输入（2 条必失败）验证日志轨迹。
 * 2. 设计工单抽取的 schema 文档（字段/类型/枚举/兜底值），并用 20 条真实风格的输入统计通过率。
 * 3. 思考题：为什么“把校验错误回传给模型”能显著提升重试成功率？对比“原样重试”的行为差异。
 * ============================================================
 */
