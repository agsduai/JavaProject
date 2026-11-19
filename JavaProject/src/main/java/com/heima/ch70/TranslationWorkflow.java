/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：70-07
 * 课名：翻译工作流与质量
 * ------------------------------------------------------------
 * 知识点：
 *   - **翻译不是"把文件丢给翻译公司"**：完整流程是 **文案抽取 -> 翻译 -> 审校 -> 回填 -> 验证**——**每个环节都要有工具与校验**，否则漏翻/错翻/串行号。
 *   - **文案抽取的两种方式**：**代码扫描**（正则找字面量，70-02 的 CI 检查）+ **资源文件导出**（从 properties/XLIFF 导出给翻译平台）——**业界标准格式是 XLIFF**（带上下文/状态/备注）。
 *   - **翻译质量的三道关**：**完整性**（key 全覆盖）、**占位符一致性**（`{0}` 不能翻译丢）、**长度适配**（德语长 30%，UI 要能容纳）——**前两项可自动化，第三项靠 UI 测试**。
 *   - **上下文缺失是最大质量杀手**：`"Close"` 是"关闭"还是"靠近"？**给翻译者上下文**（界面截图/字段说明）——**翻译平台要支持 context 字段**。
 *   - **持续本地化（continuous localization）**：文案变更自动同步到翻译平台 -> 翻译完成自动回填 -> CI 校验完整性——**与 41 章流水线结合**。
 *   - 易错点：占位符被翻译（`{0}` 变 `{0 }`）；漏翻（缺 key 显示原文或 `!key!`）；长度溢出（UI 截断）；无上下文（"Close"翻错）。
 * ============================================================
 */

package com.heima.ch70;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// ==================== 示例代码 ====================

public class TranslationWorkflow {
    record Entry(String key, String source, String target, String context) {
    }

    /** 校验一：完整性（target 是否缺失） */
    static List<String> checkCompleteness(List<Entry> entries) {
        List<String> issues = new ArrayList<>();
        for (Entry e : entries) {
            if (e.target() == null || e.target().isBlank()) {
                issues.add("缺翻译：" + e.key());
            }
        }
        return issues;
    }

    /** 校验二：占位符一致性 */
    static List<String> checkPlaceholders(List<Entry> entries) {
        Pattern p = Pattern.compile("\\{\\d+}");
        List<String> issues = new ArrayList<>();
        for (Entry e : entries) {
            if (e.target() == null) {
                continue;
            }
            List<String> src = new ArrayList<>();
            Matcher m1 = p.matcher(e.source());
            while (m1.find()) {
                src.add(m1.group());
            }
            List<String> tgt = new ArrayList<>();
            Matcher m2 = p.matcher(e.target());
            while (m2.find()) {
                tgt.add(m2.group());
            }
            if (!src.equals(tgt)) {
                issues.add("占位符不一致：" + e.key() + " 原文=" + src + " 译文=" + tgt);
            }
        }
        return issues;
    }

    /** 校验三：长度膨胀（德语/俄语通常更长） */
    static List<String> checkLength(List<Entry> entries, double maxRatio) {
        List<String> issues = new ArrayList<>();
        for (Entry e : entries) {
            if (e.target() == null || e.source().isEmpty()) {
                continue;
            }
            double ratio = e.target().length() / (double) e.source().length();
            if (ratio > maxRatio) {
                issues.add(String.format("长度超限：%s 膨胀 %.0f%%（阈值 %.0f%%）",
                        e.key(), (ratio - 1) * 100, (maxRatio - 1) * 100));
            }
        }
        return issues;
    }

    static void workflow() {
        System.out.println("【翻译工作流五步】");
        Map<String, String> steps = new LinkedHashMap<>();
        steps.put("抽取", "扫描字面量 + 导出资源文件（XLIFF，带 context）");
        steps.put("翻译", "翻译平台；提供界面截图/字段说明作为上下文");
        steps.put("审校", "母语审校；重点检查术语一致性与语气");
        steps.put("回填", "自动回填资源文件（按 key 对应）");
        steps.put("验证", "CI 校验：完整性 + 占位符 + 长度；UI 走查");
        steps.forEach((k, v) -> System.out.printf("  %-4s %s%n", k, v));
        System.out.println();
    }

    public static void main(String[] args) {
        List<Entry> entries = List.of(
                new Entry("order.paid", "Order {0} paid: {1}", "订单 {0} 已支付：{1}",
                        "订单详情页标题"),
                new Entry("btn.close", "Close", "关闭", "对话框按钮"),
                new Entry("msg.hello", "Hello, {0}!", "こんにちは、{0}さん", "首页问候"),
                new Entry("msg.missing", "Welcome back", null, "登录后提示"),
                new Entry("btn.submit", "Submit", "提交", "表单按钮"),
                new Entry("err.timeout", "Request timed out, please retry", "请求超时，请重试",
                        "错误提示"),
                new Entry("label.long", "Settings", "Einstellungen und Benutzereinstellungen",
                        "设置入口"));

        System.out.println("【校验结果】");
        List<String> c1 = checkCompleteness(entries);
        List<String> c2 = checkPlaceholders(entries);
        List<String> c3 = checkLength(entries, 2.0);

        System.out.println("  完整性：" + (c1.isEmpty() ? "通过" : c1));
        System.out.println("  占位符：" + (c2.isEmpty() ? "通过" : c2));
        System.out.println("  长度：" + (c3.isEmpty() ? "通过" : c3));

        System.out.println();
        System.out.println("【占位符错误示例】把 {0} 误译成 {O}（字母 O）");
        Entry broken = new Entry("x", "Hello {0}", "你好 {O}", "测试");
        System.out.println("  " + checkPlaceholders(List.of(broken)));

        System.out.println();
        workflow();
        System.out.println("  最大质量杀手：无上下文（Close = 关闭 还是 靠近？）");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现三项校验器（完整性/占位符/长度），用 7 条翻译数据验证能检出全部问题。
 * 2. 设计"翻译上下文"字段规范（页面/元素类型/长度上限/术语表），并给 5 个易错词写出上下文。
 * 3. 思考题：为什么"漏翻"应该在 CI 阶段拦截而不是运行期兜底？从"用户可见的 `!key!`"与"构建成本"分析。
 * ============================================================
 */
