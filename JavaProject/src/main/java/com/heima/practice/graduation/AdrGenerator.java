/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：实操 52-02
 * 课名：ADR 生成器
 * ------------------------------------------------------------
 * 知识点：
 *   - 【题目要求】实现 ADR 生成器：决策输入（标题/背景/选项列表/选中项/负面后果/回顾条件）-> 校验（选项 ≥2、负面后果非空、回顾条件存在）-> 输出格式化 ADR 文档（Markdown）+ 决策索引（追加到 ADR 索引表）；输入不合规时输出整改提示（52-02 纪律的自动化）。
 *   - 【考核点】ADR 结构（背景/选项/决策/后果）、纪律校验（选项数/负面后果）、决策索引与历史保留。
 *   - 【易错点】只有结论没有选项；负面后果空泛（“可能有风险”）；索引不更新（文档孤岛）。
 *   - 【提示】生成器输出后自动追加索引行（编号/标题/状态/日期）——决策资产化管理。
 * ============================================================
 */

package com.heima.practice.graduation;

import java.util.ArrayList;
import java.util.List;

// ==================== 示例代码 ====================

public class AdrGenerator {
    static List<String> validate(String title, List<String> options, String negative) {
        List<String> errors = new ArrayList<>();
        if (title == null || title.isBlank()) {
            errors.add("缺少标题");
        }
        if (options.size() < 2) {
            errors.add("选项少于 2 个：单一选项不需要 ADR");
        }
        if (negative == null || negative.length() < 20) {
            errors.add("负面后果过于空泛，请写具体风险");
        }
        return errors;
    }

    public static void main(String[] args) {
        System.out.println(validate("并发模型选型",
                List.of("虚拟线程", "WebFlux"), "需排查 pinning，连接池将成为新瓶颈"));
        System.out.println(validate("并发模型选型", List.of("虚拟线程"), "有风险"));
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现 ADR 生成器完整版：含校验、Markdown 渲染、索引追加，生成你系统的三个真实决策文档。
 * 2. 实现“决策取代”：新 ADR 生成时自动把被取代的旧 ADR 状态改为 superseded（历史不删）。
 * 3. 思考题：为什么 ADR 校验器能长期保住决策质量？从“纪律自动化”与人工评审的互补关系分析。
 * ============================================================
 */
