/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：70-08
 * 课名：前端与多端一致性
 * ------------------------------------------------------------
 * 知识点：
 *   - **多端一致性的三个层面**：**文案一致**（同一 key 在各端翻译相同）、**格式一致**（日期/货币/数字格式相同）、**时区一致**（同一瞬时在各端显示的时间）——**后端返回"数据 + Locale"比返回"已格式化字符串"更可靠**（各端本地格式化）。
 *   - **两种架构选择的权衡**：**后端格式化**（返回 `$1,234.56`，各端直接展示——一致但无法按端适配）；**前端格式化**（返回 `1234.56 + USD`，各端用本地库格式化——灵活但要保证库行为一致）。
 *   - **推荐：后端返回结构化数据 + 前端按 Locale 格式化**；**唯一例外**是"服务端生成的 PDF/邮件/推送文案"（必须后端格式化，因为不经过客户端）。
 *   - **兜底与降级**：`Accept-Language` 协商（客户端偏好 -> 服务端匹配 -> 兜底默认语言）；**语言切换要清除缓存**（否则看到旧语言 55-01 的缓存键）。
 *   - **与 55 章的关系**：多租户 SaaS 的租户可配置默认语言（55-06），用户可覆盖（用户级配置）——**语言也是配置分层的一层**。
 *   - 易错点：后端返回已格式化的字符串（前端无法按用户 Locale 调整）；时区用服务器默认（不同机房显示不同）；语言切换不清缓存；`Accept-Language` 解析错误（q 权重）。
 * ============================================================
 */

package com.heima.ch70;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// ==================== 示例代码 ====================

public class MultiPlatformI18n {
    /** 后端返回的结构化数据（不预格式化） */
    record MoneyDto(long amountInMinorUnit, String currency, int minorDigits) {
    }

    record ApiResponse(MoneyDto amount, long timestampMs, String languageHint) {
    }

    /** 前端按 Locale 格式化 */
    static String formatForClient(ApiResponse resp, Locale clientLocale) {
        MoneyDto m = resp.amount();
        NumberFormat nf = NumberFormat.getCurrencyInstance(clientLocale);
        nf.setCurrency(java.util.Currency.getInstance(m.currency()));
        nf.setMaximumFractionDigits(m.minorDigits());
        nf.setMinimumFractionDigits(m.minorDigits());
        double major = m.amountInMinorUnit() / Math.pow(10, m.minorDigits());
        return nf.format(major);
    }

    /** Accept-Language 协商：解析 q 权重 */
    static Locale negotiate(String acceptLanguage) {
        if (acceptLanguage == null || acceptLanguage.isBlank()) {
            return Locale.US;
        }
        String best = null;
        double bestQ = -1;
        for (String part : acceptLanguage.split(",")) {
            String[] tokens = part.trim().split(";");
            String tag = tokens[0].trim();
            double q = 1.0;
            if (tokens.length > 1 && tokens[1].contains("q=")) {
                try {
                    q = Double.parseDouble(tokens[1].split("q=")[1].trim());
                } catch (NumberFormatException e) {
                    q = 0;
                }
            }
            if (q > bestQ) {
                bestQ = q;
                best = tag;
            }
        }
        if (best == null) {
            return Locale.US;
        }
        return best.contains("-")
                ? Locale.forLanguageTag(best)
                : new Locale(best);
    }

    public static void main(String[] args) {
        System.out.println("【后端返回结构化数据，前端按 Locale 格式化】");
        ApiResponse resp = new ApiResponse(new MoneyDto(123_456, "USD", 2),
                1_763_000_000_000L, "en");
        for (Locale l : new Locale[]{Locale.US, Locale.GERMANY, Locale.JAPAN}) {
            System.out.printf("  %-6s -> %s%n", l, formatForClient(resp, l));
        }

        System.out.println();
        System.out.println("【Accept-Language 协商】");
        String[] headers = {
                "zh-CN,zh;q=0.9,en;q=0.8",
                "en-US,en;q=0.9",
                "fr;q=0.5,de;q=0.8",
                ""
        };
        for (String h : headers) {
            System.out.printf("  %-32s -> %s%n", "[" + h + "]", negotiate(h));
        }

        System.out.println();
        System.out.println("【语言切换与缓存】");
        Map<String, String> cache = new LinkedHashMap<>();
        cache.put("u-1@zh_CN@order:9001", "订单已支付");
        cache.put("u-1@en_US@order:9001", "Order paid");
        System.out.println("  缓存键必须含语言：" + cache.keySet());
        System.out.println("  切换语言 -> 缓存键变 -> 自然读到新语言（无需清理）");

        System.out.println();
        System.out.println("【两种架构取舍】");
        List<String> notes = new ArrayList<>();
        notes.add("后端格式化：一致但无法按端调整（已牺牲灵活性）");
        notes.add("前端格式化：灵活但要保证各端库行为一致");
        notes.add("服务端产物（PDF/邮件/推送）必须后端格式化（不经过客户端）");
        notes.forEach(n -> System.out.println("  - " + n));
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现 Accept-Language 解析（含 q 权重），用 4 组 header 验证协商结果。
 * 2. 对比"后端格式化"与"前端格式化"两种 API 设计，各写一个响应示例并说明适用场景。
 * 3. 思考题：为什么"缓存键必须含语言"？构造"切换语言后看到旧文案"的用例并给出修复方案。
 * ============================================================
 */
