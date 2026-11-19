/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：70-05
 * 课名：货币与数字格式
 * ------------------------------------------------------------
 * 知识点：
 *   - **货币的三要素**：**金额（最小单位整数）** + **币种（ISO 4217 代码：CNY/USD/EUR/JPY）** + **展示格式（符号/位置/小数位）**——**金额绝不用浮点存**（14/51 章的分单位原则）。
 *   - **各币种的小数位不同**：多数 2 位（CNY/USD/EUR），**日元 0 位**（JPY 无小数），**部分币种 3 位**（如科威特第纳尔）——**写死 2 位是常见 bug**。
 *   - **`NumberFormat.getCurrencyInstance(Locale)`**：自动处理符号位置（`¥1,234.56` / `1.234,56 €`）、千分位分隔符（`1,234` vs `1.234`）、货币符号——**必须用 Locale 而非硬编码**。
 *   - **数字格式的地域差异**：**千分位与小数点的符号相反**（美式 `1,234.56`，德式 `1.234,56`）；**印度式分组**（`12,34,567` 而非 `123,4567`）；**负号表示**（`-100` vs `(100)`，会计格式）——**不允许用字符串拼接**。
 *   - **汇率与多币种结算**：**汇率是有时效的**（每天更新），**结算要记录"当时的汇率"**（不是用当前汇率重算历史）——**与 51 章的资金快照思想一致**。
 *   - 易错点：金额用 double（精度丢失）；货币符号硬编码 `¥`（美元也用）；小数位写死 2 位（日元错）；汇率不存快照（历史账单对不上）。
 * ============================================================
 */

package com.heima.ch70;

import java.text.NumberFormat;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

// ==================== 示例代码 ====================

public class CurrencyDemo {
    /** 金额：最小单位（分/日元）整数 + 币种 */
    record Money(long amountInMinorUnit, String currencyCode, int minorDigits) {
        String display(Locale locale) {
            NumberFormat nf = NumberFormat.getCurrencyInstance(locale);
            Currency c = Currency.getInstance(currencyCode);
            nf.setCurrency(c);
            nf.setMaximumFractionDigits(c.getDefaultFractionDigits());
            nf.setMinimumFractionDigits(c.getDefaultFractionDigits());
            double major = amountInMinorUnit / Math.pow(10, minorDigits);
            return nf.format(major);
        }

        /** 展示给用户的最小单位换算（日元 0 位，人民币 2 位） */
        String raw() {
            return amountInMinorUnit + " " + currencyCode
                    + "（最小单位，小数位 " + minorDigits + "）";
        }
    }

    static void digitsMatter() {
        System.out.println("【不同币种的小数位】");
        Map<String, Integer> digits = new LinkedHashMap<>();
        digits.put("CNY", Currency.getInstance("CNY").getDefaultFractionDigits());
        digits.put("USD", Currency.getInstance("USD").getDefaultFractionDigits());
        digits.put("JPY", Currency.getInstance("JPY").getDefaultFractionDigits());
        digits.put("KWD", Currency.getInstance("KWD").getDefaultFractionDigits());
        digits.forEach((c, d) -> System.out.printf("  %-4s 小数位=%d%n", c, d));
        System.out.println("  写死 2 位 -> 日元显示 100.00（错），科威特丢精度");
        System.out.println();
    }

    static void displayAcrossLocales() {
        System.out.println("【同一金额在不同 Locale 的展示】");
        Money cny = new Money(123_456, "CNY", 2);      // 1234.56 元
        Money usd = new Money(123_456, "USD", 2);      // 1234.56 美元
        Money jpy = new Money(123_456, "JPY", 0);      // 123456 日元
        Locale[] locales = {Locale.CHINA, Locale.US, Locale.GERMANY};
        for (Locale l : locales) {
            System.out.printf("  %-6s CNY=%-14s USD=%-14s JPY=%s%n",
                    l, cny.display(l), usd.display(l), jpy.display(l));
        }
        System.out.println("  注意德式：1.234,56 €（千分位与小数点相反）");
        System.out.println();
        System.out.println("  原始存储（不含格式）：");
        System.out.println("    " + cny.raw());
        System.out.println("    " + jpy.raw());
    }

    static void exchangeRate() {
        System.out.println("【汇率快照：结算用历史汇率】");
        record Order(String id, long cnyCents, double rateAtOrder, String date) {
        }
        Order o = new Order("9001", 69_000, 7.14, "2026-11-18");
        double usd = o.cnyCents() / 100.0 / o.rateAtOrder();
        System.out.printf("  订单 %s：%.2f CNY，下单日汇率 %.2f -> %.2f USD%n",
                o.id(), o.cnyCents() / 100.0, o.rateAtOrder(), usd);
        System.out.println("  若用今日汇率 7.30 重算 -> 账单变化，历史对不上");
        System.out.println("  原则：汇率随订单落库快照（51-08 资金快照同源）");
    }

    public static void main(String[] args) {
        digitsMatter();
        displayAcrossLocales();
        exchangeRate();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现 Money 类型（最小单位 + 币种 + 小数位），在 3 个 Locale 下展示 CNY/USD/JPY/KWD。
 * 2. 复现"日元写死 2 位小数"的 bug，展示给日本用户的错误显示并修复。
 * 3. 思考题：为什么汇率必须随订单快照？从"历史账单可复现"与"多币种对账"分析。
 * ============================================================
 */
