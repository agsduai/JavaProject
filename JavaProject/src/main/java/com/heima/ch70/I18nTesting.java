/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：70-09
 * 课名：测试：伪本地化与回归
 * ------------------------------------------------------------
 * 知识点：
 *   - **伪本地化（Pseudo-localization）是 i18n 测试的核心手段**：把英文文案自动"伪翻译"成 `[Ĥéļļö ŵöŕļð !!!]`（加特殊字符 + 拉长 30%）——**一眼看出"哪里没被抽离""哪里布局会溢出"**，不需要等真实翻译。
 *   - **伪本地化的三个检查**：**硬编码检测**（没被包裹的文案仍是英文）、**布局溢出**（加长后 UI 截断）、**字符渲染**（非 ASCII 字符的字体缺失）。
 *   - **回归测试的三层**：**UI 走查**（截图对比各语言）、**功能回归**（关键流程在多语言下跑通）、**文案比对**（翻译完整性 CI 70-07）——**前两项靠人工/截图，第三项自动化**。
 *   - **假语言（pseudolocale）与真实语言的差异**：伪本地化只能测"格式与布局"，**测不出翻译质量**（准确性/语气）——**质量仍要母语审校**（70-07）。
 *   - **与 33/41 章的关系**：i18n 测试进 CI（翻译完整性）+ 截图回归（视觉对比）+ 多语言环境回归（41-03 的测试矩阵扩展）。
 *   - 易错点：只测英文（其他语言上线才发现问题）；伪本地化只加长不检查字符（emoji/RTL 遗漏）；UI 截图回归不做（布局溢出无人知）；翻译质量靠机器翻译不审校。
 * ============================================================
 */

package com.heima.ch70;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class I18nTesting {
    static final String ACCENT = "áéíóúàèìòùâêîôûäëïöüñç";
    static final String ACCENT_UPPER = "ÁÉÍÓÚÀÈÌÒÙÂÊÎÔÛÄËÏÖÜÑÇ";

    /** 伪翻译：字符替换（保持 ASCII 之外的全角化） + 拉长 30% */
    static String pseudo(String source) {
        StringBuilder sb = new StringBuilder("[");
        boolean upper = true;
        for (char c : source.toCharArray()) {
            if (Character.isLetter(c)) {
                int idx = (c | 0x20) - 'a';
                if (idx >= 0 && idx < ACCENT.length()) {
                    char rep = upper && Character.isUpperCase(c)
                            ? ACCENT_UPPER.charAt(idx) : ACCENT.charAt(idx);
                    sb.append(rep);
                } else {
                    sb.append(c);
                }
                upper = false;
            } else {
                sb.append(c);
                upper = true;
            }
        }
        int pad = (int) Math.ceil(source.length() * 0.3);
        for (int i = 0; i < pad; i++) {
            sb.append('·');
        }
        return sb.append("]").toString();
    }

    /** 硬编码检测：伪翻译后仍是原英文 -> 说明没被抽离 */
    static List<String> detectHardcoded(List<String> rawTexts) {
        List<String> hardcoded = new ArrayList<>();
        for (String raw : rawTexts) {
            String p = pseudo(raw);
            // 简化的判定：若伪翻译结果中字母占比过高且与原文长度接近，视为"未生效"
            long letters = p.chars().filter(Character::isLetter).count();
            if (letters > raw.length() * 0.8) {
                hardcoded.add(raw);
            }
        }
        return hardcoded;
    }

    /** 布局溢出检测：伪翻译后长度超过容器上限 */
    static List<String> detectOverflow(Map<String, String> texts, int maxWidth) {
        List<String> overflow = new ArrayList<>();
        texts.forEach((key, text) -> {
            String p = pseudo(text);
            if (p.length() > maxWidth) {
                overflow.add(key + " 伪翻译后 " + p.length() + " > 上限 " + maxWidth);
            }
        });
        return overflow;
    }

    public static void main(String[] args) {
        System.out.println("【伪本地化示例】");
        List<String> texts = List.of(
                "Settings", "Order paid", "Close", "Submit", "Welcome back");
        texts.forEach(t -> System.out.printf("  %-14s -> %s%n", t, pseudo(t)));

        System.out.println();
        System.out.println("【硬编码检测】");
        List<String> uiTexts = new ArrayList<>(texts);
        uiTexts.add("Hello");                          // 未抽离（假设）
        List<String> hard = detectHardcoded(uiTexts);
        System.out.println("  疑似硬编码（伪翻译未生效）：" + hard);
        System.out.println("  实际做法：所有 UI 文案都走资源束，伪翻译后必然变形");

        System.out.println();
        System.out.println("【布局溢出检测（容器宽 18 字符）】");
        Map<String, String> keyed = new LinkedHashMap<>();
        keyed.put("btn.close", "Close");
        keyed.put("label.settings", "Settings");
        keyed.put("msg.long", "Please check your network connection");
        detectOverflow(keyed, 18).forEach(o -> System.out.println("  " + o));

        System.out.println();
        System.out.println("【i18n 测试三层】");
        Map<String, String> layers = new LinkedHashMap<>();
        layers.put("自动化", "翻译完整性 + 占位符校验（70-07，进 CI）");
        layers.put("伪本地化", "硬编码/溢出/字符渲染（本章）");
        layers.put("人工走查", "多语言截图对比 + 关键流程回归");
        layers.forEach((k, v) -> System.out.printf("  %-6s %s%n", k, v));
        System.out.println();
        System.out.println("  伪本地化测不了翻译质量（准确性/语气）-> 仍需母语审校");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现伪翻译器（字符变形 + 拉长 30%）与硬编码检测，用 6 条文案验证。
 * 2. 实现布局溢出检测：给定 5 条文案与容器宽度，输出会溢出的 key 清单。
 * 3. 思考题：为什么"伪本地化不能替代母语审校"？从"格式问题"与"语义/语气问题"的分工分析。
 * ============================================================
 */
