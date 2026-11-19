/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：70-06
 * 课名：排序与比较（Collator）
 * ------------------------------------------------------------
 * 知识点：
 *   - **字符串的默认排序（`compareTo`）按 Unicode 码点**，**不等于语言排序**：中文按拼音还是笔画？德语 `ä` 排在 `a` 还是 `z`？瑞典语 `å` 在 `z` 之后——**用 `compareTo` 排序多语言列表会得到"看起来随机"的顺序**。
 *   - **`Collator` 按语言规则排序**：`Collator.getInstance(Locale.CHINA)` 按拼音（或笔画，取决于 JDK 实现）、`Locale.GERMAN` 让 `ä` 等同 `a`、`Locale.SWEDISH` 让 `å` 在 `z` 后——**排序规则是语言文化的一部分**。
 *   - **排序强度的三级**：**PRIMARY**（忽略大小写与重音：`a` = `A` = `á`）、**SECONDARY**（区分重音）、**TERTIARY**（区分大小写）——**列表排序通常用 PRIMARY，搜索匹配用更低强度**。
 *   - **分解（Normalization）**：同一个字符有"预组合"（`é` 一个码点）与"组合"（`e` + 组合重音）两种表示——**排序/比较前要 `Normalizer.normalize(s, NFC)`**，否则相等字符串判不相等（35-xx Unicode 的坑）。
 *   - **与 54/38 章的关系**：搜索的"前缀匹配/排序"在多语言下都要 Collator（70-10）；推荐列表的多语言排序同理。
 *   - 易错点：用 `String.compareTo` 排多语言列表；不归一化就做字典序比较（同字不同编码）；排序强度选错（区分大小写导致 `Apple` 与 `apple` 不相等）；数据库排序规则与代码不一致。
 * ============================================================
 */

package com.heima.ch70;

import java.text.Collator;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

// ==================== 示例代码 ====================

public class CollationDemo {
    static void unicodeVsCollator() {
        List<String> words = new ArrayList<>(Arrays.asList(
                "apple", "Banana", "cherry", "Äpfel", "Zebra", "árbol"));
        List<String> byCodePoint = new ArrayList<>(words);
        byCodePoint.sort(Comparator.naturalOrder());
        List<String> byEnglish = new ArrayList<>(words);
        byEnglish.sort(Collator.getInstance(Locale.ENGLISH));
        List<String> byGerman = new ArrayList<>(words);
        byGerman.sort(Collator.getInstance(Locale.GERMAN));

        System.out.println("【同一批词的不同排序】");
        System.out.println("  原始：" + words);
        System.out.println("  compareTo（码点）：" + byCodePoint);
        System.out.println("  Collator(en)：    " + byEnglish);
        System.out.println("  Collator(de)：    " + byGerman);
        System.out.println("  差异：大写字母位置、Ä 与 A 的关系因语言而变");
        System.out.println();
    }

    static void strength() {
        System.out.println("【排序强度】");
        Collator c = Collator.getInstance(Locale.ENGLISH);
        String[][] pairs = {{"Apple", "apple"}, {"cafe", "café"}, {"a", "á"}};
        for (int s : new int[]{Collator.PRIMARY, Collator.SECONDARY, Collator.TERTIARY}) {
            c.setStrength(s);
            String label = s == Collator.PRIMARY ? "PRIMARY（忽略大小写与重音）"
                    : (s == Collator.SECONDARY ? "SECONDARY（区分重音）" : "TERTIARY（区分大小写）");
            StringBuilder sb = new StringBuilder();
            for (String[] p : pairs) {
                sb.append(p[0]).append(" vs ").append(p[1]).append("=")
                        .append(c.compare(p[0], p[1])).append("  ");
            }
            System.out.printf("  %-30s %s%n", label, sb);
        }
        System.out.println("  列表排序用 PRIMARY；登录名/唯一校验要 TERTIARY 或显式规则");
        System.out.println();
    }

    static void normalization() {
        String composed = "\u00e9";                  // é 单码点
        String decomposed = "e\u0301";               // e + 组合重音
        System.out.println("【归一化】");
        System.out.printf("  composed   = %s 长度=%d%n", composed, composed.length());
        System.out.printf("  decomposed = %s 长度=%d%n", decomposed, decomposed.length());
        System.out.println("  裸 equals：" + composed.equals(decomposed));
        String n1 = Normalizer.normalize(composed, Normalizer.Form.NFC);
        String n2 = Normalizer.normalize(decomposed, Normalizer.Form.NFC);
        System.out.println("  NFC 后 equals：" + n1.equals(n2));
        System.out.println("  规则：比较/排序/存库前统一归一化（NFC）");
        System.out.println();
    }

    public static void main(String[] args) {
        unicodeVsCollator();
        strength();
        normalization();
        System.out.println("【实践建议】");
        System.out.println("  1) 展示排序：Collator.getInstance(userLocale)");
        System.out.println("  2) 数据库排序规则要与代码一致（否则分页错乱）");
        System.out.println("  3) 搜索/去重前先 NFC 归一化");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 用 Collator 对中英德三语混合列表排序，对比 `compareTo` 结果，说明差异原因。
 * 2. 演示排序强度对"Apple/apple"与"cafe/café"比较结果的影响（三个强度各跑一次）。
 * 3. 思考题：为什么"数据库排序规则与代码不一致"会导致分页错乱？从"两次查询的排序基准不同"分析。
 * ============================================================
 */
