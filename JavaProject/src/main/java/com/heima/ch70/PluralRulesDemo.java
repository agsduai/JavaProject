/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：70-03
 * 课名：复数规则与性别
 * ------------------------------------------------------------
 * 知识点：
 *   - **"1 个"和"2 个"在不同语言里规则不同**：英语 2 种（one/other）、中文 1 种、俄语 3 种（one/few/many）、阿拉伯语 6 种（zero/one/two/few/many/other）——**只写"单复数"会漏掉大部分语言**。
 *   - **`PluralRules`（ICU/JDK 的 `java.text` 扩展）**：按 Locale + 数量选出类别（`one/few/many/other`），再查对应文案——**JDK 原生能力有限，复杂场景用 ICU4J**（本章用简化实现演示规则）。
 *   - **复数规则的三种类型**：**基数（cardinal）**（数量：1 file / 2 files）、**序数（ordinal）**（第 1 / 第 2 / 第 3）、**范围（range）**（1-2 files）——**序数在日期/排名里必用**。
 *   - **性别（gender）**：法语/德语/西班牙语的形容词与冠词随名词性别变化（`le/un` vs `la/une`）——**翻译系统要支持"带变量的句子片段"**，而不是整句替换（占位符插入时语法要正确）。
 *   - **UI 文案的性别与敬语**：日语敬语层级（です/ます vs だ/である）、韩语敬语——**同一功能的文案在不同场景可能需要多版本**。
 *   - 易错点：只用 if (n == 1) 判断单复数；中文翻译也套英文复数（中文没有复数）；序数用 `n + "th"`（1st/2nd/3rd 特殊）；忽略敬语层级。
 * ============================================================
 */

package com.heima.ch70;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class PluralRulesDemo {
    /** 简化复数规则：语言 -> (数量分类函数) */
    static String pluralCategory(String lang, int n) {
        return switch (lang) {
            case "zh", "ja", "ko" -> "other";                     // 无复数变化
            case "en" -> n == 1 ? "one" : "other";
            case "ru" -> {
                int mod10 = n % 10;
                int mod100 = n % 100;
                if (mod10 == 1 && mod100 != 11) {
                    yield "one";
                }
                if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) {
                    yield "few";
                }
                yield "many";
            }
            case "ar" -> n == 0 ? "zero" : (n == 1 ? "one" : (n == 2 ? "two"
                    : (n % 100 >= 3 && n % 100 <= 10 ? "few" : "many")));
            default -> "other";
        };
    }

    static final Map<String, Map<String, String>> pluralTexts = new LinkedHashMap<>();

    static void initTexts() {
        Map<String, String> en = new LinkedHashMap<>();
        en.put("one", "{0} file");
        en.put("other", "{0} files");
        pluralTexts.put("en", en);

        Map<String, String> zh = new LinkedHashMap<>();
        zh.put("other", "{0} 个文件");
        pluralTexts.put("zh", zh);

        Map<String, String> ru = new LinkedHashMap<>();
        ru.put("one", "{0} файл");
        ru.put("few", "{0} файла");
        ru.put("many", "{0} файлов");
        pluralTexts.put("ru", ru);

        Map<String, String> ar = new LinkedHashMap<>();
        ar.put("zero", "لا ملفات");
        ar.put("one", "ملف واحد");
        ar.put("two", "ملفان");
        ar.put("few", "{0} ملفات");
        ar.put("many", "{0} ملفًا");
        pluralTexts.put("ar", ar);
    }

    static String render(String lang, int n) {
        String category = pluralCategory(lang, n);
        String template = pluralTexts.getOrDefault(lang, Map.of())
                .getOrDefault(category, "{0}");
        return template.replace("{0}", String.valueOf(n));
    }

    /** 序数：1st/2nd/3rd/nth（英语） */
    static String ordinal(int n) {
        int mod100 = n % 100;
        if (mod100 >= 11 && mod100 <= 13) {
            return n + "th";
        }
        return switch (n % 10) {
            case 1 -> n + "st";
            case 2 -> n + "nd";
            case 3 -> n + "rd";
            default -> n + "th";
        };
    }

    public static void main(String[] args) {
        initTexts();
        System.out.println("【复数规则：同一数量在不同语言】");
        for (int n : new int[]{0, 1, 2, 3, 5, 11, 21, 22}) {
            System.out.printf("  n=%-2d en=%-12s zh=%-10s ru=%-12s ar=%s%n",
                    n, render("en", n), render("zh", n), render("ru", n), render("ar", n));
        }

        System.out.println();
        System.out.println("【序数规则】");
        List<Integer> ns = List.of(1, 2, 3, 4, 11, 12, 13, 21, 22, 23);
        StringBuilder sb = new StringBuilder("  ");
        ns.forEach(n -> sb.append(ordinal(n)).append(" "));
        System.out.println(sb);
        System.out.println("  注意 11/12/13 都是 th（不是 11st/12nd/13rd）");

        System.out.println();
        System.out.println("【性别与敬语】");
        System.out.println("  法语：un fichier（阳）/ une image（阴）-> 冠词随名词性别");
        System.out.println("  日语：です/ます（敬语）vs だ（简体）-> 同一功能可能需要两版文案");
        System.out.println("  做法：翻译片段而非整句 + 变量带语法信息（性别/数/敬语级）");

        System.out.println();
        System.out.println("【易错点】");
        System.out.println("  if (n == 1) 只适配英语；中文也套复数规则是多余；实现序数不能只看 n%10");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现多语言复数渲染（en/zh/ru/ar），用 8 个数量验证各语言分类正确。
 * 2. 实现英语序数（含 11/12/13 特例）与"第 N 名"的中文文案，写 10 个用例验证。
 * 3. 思考题：为什么"整句翻译"难以处理性别与敬语？从"变量插入后语法要正确"的角度分析。
 * ============================================================
 */
