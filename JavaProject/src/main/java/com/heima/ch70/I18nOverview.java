/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：70-01
 * 课名：i18n 全景：语言、地区与格式
 * ------------------------------------------------------------
 * 知识点：
 *   - **国际化（i18n）与本地化（l10n）是两件事**：**i18n 是"架构上支持多语言"**（把文案抽离、格式化可配置、布局可伸缩）；**l10n 是"具体某个市场的适配"**（翻译、货币、日期格式、配色、合规）。**先有 i18n 能力，才能高效做 l10n**。
 *   - **`Locale` 是语言 + 地区的二元组**：`zh_CN`（中文-中国）、`en_US`（英语-美国）、`en_GB`（英语-英国）、`pt_BR`（葡语-巴西）——**同一个语言在不同地区格式不同**（日期/货币/数字），**只用语言不用地区是常见错误**。
 *   - **五个必须本地化的维度**：**文案**（ResourceBundle 70-02）、**数字与货币**（70-05）、**日期时间与时区**（70-04）、**排序与比较**（Collator 70-06）、**复数与性别**（70-03）——**少一个就会在某个市场"看起来不对劲"**。
 *   - **不要做的事**：**在代码里拼句子**（`"Hello " + name` 在日语/德语语序不同，应该用占位符 + 翻译）；**用字符串比较排序**（中文/德文排序规则不同 70-06）；**用 `new Date()` 无时区**（70-04）；**假设文案长度固定**（德语通常比英语长 30%，UI 会溢出）。
 *   - **与 55/67 章的关系**：多租户 SaaS 的"区域部署"（55-10）与位置数据的"坐标系差异"（67-02）都是本地化问题——**i18n 是出海系统的地基**。
 *   - 易错点：只翻文案不管格式（日期显示成美式，用户看不懂）；代码拼句子（翻译后语序错乱）；硬编码货币符号；忽略 RTL（阿拉伯语从右往左）。
 * ============================================================
 */

package com.heima.ch70;

import java.text.DateFormat;
import java.text.NumberFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

// ==================== 示例代码 ====================

public class I18nOverview {
    static void localeAnatomy() {
        System.out.println("【Locale = 语言 + 地区】");
        Map<String, String> locales = new LinkedHashMap<>();
        locales.put("zh_CN", "中文（中国）：¥1,234.56 / 2026年11月18日");
        locales.put("en_US", "English (US)：$1,234.56 / Nov 18, 2026");
        locales.put("en_GB", "English (UK)：£1,234.56 / 18 Nov 2026");
        locales.put("de_DE", "Deutsch：1.234,56 € / 18.11.2026");
        locales.forEach((k, v) -> System.out.printf("  %-6s %s%n", k, v));
        System.out.println("  同一语言不同地区格式不同 -> 必须带地区");
        System.out.println();
    }

    static void fiveDimensions() {
        System.out.println("【五个必须本地化的维度】");
        String[][] rows = {
                {"文案", "ResourceBundle / 翻译文件", "70-02"},
                {"数字与货币", "NumberFormat / 货币符号位置", "70-05"},
                {"日期时间", "DateFormat / 时区", "70-04"},
                {"排序比较", "Collator / 语言排序规则", "70-06"},
                {"复数与性别", "复数规则 / 性别变化", "70-03"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-10s %-28s %s%n", r[0], r[1], r[2]);
        }
        System.out.println();
    }

    /** 演示：同一份数据在三个 Locale 下的呈现 */
    static void demo() {
        double amount = 1234.56;
        Date date = new Date(1_763_000_000_000L);
        Locale[] locales = {Locale.CHINA, Locale.US, Locale.GERMANY};
        System.out.println("【同一数据的三种呈现】");
        for (Locale l : locales) {
            NumberFormat nf = NumberFormat.getCurrencyInstance(l);
            DateFormat df = DateFormat.getDateInstance(DateFormat.DEFAULT, l);
            System.out.printf("  %-6s 金额=%-14s 日期=%s%n",
                    l.toString(), nf.format(amount), df.format(date));
        }
        System.out.println();
        System.out.println("【反例】在代码里拼句子");
        String name = "Alice";
        System.out.println("  \"Hello \" + name -> 日语应为「こんにちは、Alice さん」，德语应调整语序");
        System.out.println("  正解：\"greeting=こんにちは、{0}さん\" 占位符 + 翻译（70-02）");
    }

    public static void main(String[] args) {
        localeAnatomy();
        fiveDimensions();
        demo();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 用 `Locale` 与 `NumberFormat`/`DateFormat` 输出同一数据在 4 个地区的呈现，对比差异。
 * 2. 检查项目里 5 处"代码拼句子"的写法，改造成占位符 + 资源文件的形式。
 * 3. 思考题：为什么"只翻译文案"不足以支撑出海？从日期格式与货币位置的用户认知差异分析。
 * ============================================================
 */
