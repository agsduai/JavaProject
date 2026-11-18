/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：38-03
 * 课名：中文分词与 IK 分析器
 * ------------------------------------------------------------
 * 知识点：
 *   - **英文靠空格分词，中文没有天然边界**：把"保护手机壳"切成 `保护 / 手机壳` 还是 `保护手 / 机壳`，直接决定搜不搜得到。ES 的默认 standard 分词器对中文是**逐字切分**（"手机壳" → 手/机/壳），单字能命中但语义全无。
 *   - **IK 分词器的两种模式**：`ik_max_word`（最细粒度："中华人民共和国" → 中华人民共和国/人民/共和/国）适合**索引**，词覆盖全，召回率高；`ik_smart`（粗粒度，切出最合理的词）适合**查询**，避免查询词过度切分导致噪声。标准组合就是：索引用 max_word，查询用 smart。
 *   - **自定义词典解决领域词**：人名、品牌名、行业术语会被切错（"钉钉"切成"钉/钉"）。IK 支持 `main.dic` 扩展词与 `stopword.dic` 停用词，ES 里还可以用 `_synonyms` 做同义词（"苹果,apple"互相可搜）。
 *   - **歧义与切分歧**：同一串字有多个合理切法（"研究生命起源" → 研究/生命/起源 或 研/究/生/命）。基于词典+规则（最大匹配、最短路径）或统计模型（HMM/CRF）。IK 内置 HMM 处理未登录词（词典里没有的新词，尤其是人名）。
 *   - **手写分词器的入门算法是最大正向匹配**：从左到右取最长能匹配词典的子串；还有逆向最大匹配（从右往左）与双向最大匹配（两个都做再取切分更少的）。理解它足够看懂 IK 的大部分行为。
 *   - **分词粒度的工程权衡**：粒度细 → 召回率高但噪声多；粒度粗 → 精准但漏召回。电商标题搜索常用"粗粒度 + 同义词"，日志搜索反而常用 keyword + 通配的折衷方案。
 *   - 易错点：索引改了分词器但没重建索引（mapping 里的 analyzer 只对新写入生效，老 segment 还是旧切分，搜出来的结果是"半新半旧"）；把高频停用词（的/了/是）留在索引里，倒排词典被垃圾词淹没；同义词方向配反（只配了 apple->苹果，用户搜"苹果"仍然找不到 iPhone）。
 * ============================================================
 */

package com.heima.ch38;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// ==================== 示例代码 ====================

public class ChineseTokenizer {
    static final Set<String> DICT = new HashSet<>(List.of(
            "中华人民共和国", "人民", "共和", "国", "手机", "手机壳", "保护",
            "保护壳", "透明", "研究", "生命", "起源", "研究生", "苹果", "iphone"));

    /** 最大正向匹配：从最长的候选开始尝试 */
    static List<String> forwardMaxMatch(String text, int maxLen) {
        List<String> result = new ArrayList<>();
        int i = 0;
        while (i < text.length()) {
            int matched = 0;
            for (int len = Math.min(maxLen, text.length() - i); len >= 1; len--) {
                String candidate = text.substring(i, i + len);
                if (DICT.contains(candidate.toLowerCase())) {
                    matched = len;
                    result.add(candidate);
                    break;
                }
            }
            if (matched == 0) {          // 词典没有 -> 单字兜底（HMM 在真实 IK 里干这活）
                result.add(String.valueOf(text.charAt(i)));
                matched = 1;
            }
            i += matched;
        }
        return result;
    }

    static void demoMaxMatch() {
        System.out.println("【最大正向匹配（maxLen=7）】");
        for (String text : new String[]{"中华人民共和国万岁", "透明手机保护壳", "研究生命起源"}) {
            System.out.printf("  %-12s -> %s%n", text, forwardMaxMatch(text, 7));
        }
        System.out.println("  「研究生命起源」切出 研究/生命/起源 —— 换成逐字对比即可看出歧义");
        System.out.println();
    }

    static void ikTwoModes() {
        System.out.println("【IK 两种粒度：索引细、查询粗】");
        String title = "中华人民共和国";
        List<String> indexed = List.of("中华人民共和国", "人民", "共和", "国");   // ik_max_word
        List<String> queried = List.of("中华人民共和国");                        // ik_smart
        System.out.println("  ik_max_word（写入索引）：" + indexed);
        System.out.println("  ik_smart（查询解析）  ：" + queried);
        System.out.println("  查询词在索引的倒排表里全部可命中 -> 召回有保障");
        System.out.println("  反例：查询也用 max_word 拆出 人民/共和 -> 单词命中大量无关文档");
        System.out.println();
    }

    static void customDict() {
        System.out.println("【领域词与同义词：分词质量的最后一公里】");
        List<String> before = forwardMaxMatch("钉钉会议手机壳", 7);
        System.out.println("  未加领域词：「钉钉会议手机壳」-> " + before + "（钉钉被切碎）");
        DICT.add("钉钉");
        DICT.add("会议");
        List<String> after = forwardMaxMatch("钉钉会议手机壳", 7);
        System.out.println("  加入领域词后            -> " + after);
        System.out.println("  同义词表：苹果,iphone => 搜「苹果」也能召回 iPhone 文档");
        System.out.println("  停用词表：的/了/是 不进倒排，词典体积与查询噪声同时下降");
    }

    public static void main(String[] args) {
        demoMaxMatch();
        ikTwoModes();
        customDict();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现逆向最大匹配，与正向版本在「研究生命起源」「中华人民共和国」两个句子上对比切分结果，解释双向最大匹配如何利用两者差异。
 * 2. 为你的业务列一份"必收领域词清单"（至少 10 个词），并写出验证方法：加词典前后对同一查询的召回对比。
 * 3. 思考题：为什么索引用 ik_max_word、查询用 ik_smart 是推荐组合？如果把两者互换，搜索质量会向哪个方向变化？
 * ============================================================
 */
