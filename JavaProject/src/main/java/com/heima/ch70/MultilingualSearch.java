/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：70-10
 * 课名：多语言下的搜索与匹配
 * ------------------------------------------------------------
 * 知识点：
 *   - **多语言搜索的四个难题**：**分词**（中文/日文无空格，英文按空格 38-03）、**大小写与重音**（`Café` 应匹配 `cafe`）、**同义词与词形还原**（`running` 匹配 `run`）、**排序与高亮**（Collator 70-06）。
 *   - **查询侧与分析侧的归一化**：**存入时归一化**（NFC 70-06 + 小写 + 去重音）**与查询时同样归一化**——**两边不一致就搜不到**（38-02 的 analyzer 不一致问题）。
 *   - **多字段策略**：同一字段存两份——**原始字段**（展示用，保留重音与大小写）+ **归一化字段**（搜索用，小写去重音）——**用 `analyzer` 的 normalizer 或应用层处理**。
 *   - **跨语言同义词**：`手机` / `mobile` / `smartphone` 映射到同一概念——**需要维护同义词表**（38-03 的自定义词典）；**语言识别**决定用哪套分词与同义词（70-08 的 Accept-Language）。
 *   - **与 38 章的关系**：38 章讲倒排与分词，本章讲"多语言场景下的分词与归一化差异"——**技术相同，语言适配不同**。
 *   - 易错点：只对查询归一化不对索引归一化（搜不到）；重音敏感导致 `café` 搜不到 `cafe`；中文用英文分词器（逐字切）；跨语言同义词缺失。
 * ============================================================
 */

package com.heima.ch70;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// ==================== 示例代码 ====================

public class MultilingualSearch {
    /** 归一化：NFC + 小写 + 去重音（NFC 分解后去掉组合字符） */
    static String normalize(String text) {
        String nfc = Normalizer.normalize(text, Normalizer.Form.NFD);
        StringBuilder sb = new StringBuilder();
        for (char c : nfc.toCharArray()) {
            if (Character.getType(c) != Character.NON_SPACING_MARK) {
                sb.append(c);
            }
        }
        return sb.toString().toLowerCase();
    }

    /** 语言识别（简化：按字符集占比） */
    static String detectLanguage(String text) {
        long cjk = text.chars().filter(c -> c >= 0x4E00 && c <= 0x9FFF).count();
        long cyrillic = text.chars().filter(c -> c >= 0x0400 && c <= 0x04FF).count();
        if (cjk > 0) {
            return "zh";
        }
        if (cyrillic > 0) {
            return "ru";
        }
        return "en";
    }

    /** 分词：中文按二元切分（简化），英文按空格 */
    static List<String> tokenize(String text, String lang) {
        List<String> tokens = new ArrayList<>();
        if (lang.equals("zh")) {
            for (int i = 0; i < text.length() - 1; i++) {
                tokens.add(text.substring(i, i + 2));
            }
        } else {
            for (String t : text.split("\\s+")) {
                if (!t.isBlank()) {
                    tokens.add(t);
                }
            }
        }
        return tokens;
    }

    /** 同义词表：跨语言映射到统一概念 */
    static final Map<String, String> SYNONYMS = new LinkedHashMap<>();

    static {
        SYNONYMS.put("手机", "phone");
        SYNONYMS.put("mobile", "phone");
        SYNONYMS.put("smartphone", "phone");
        SYNONYMS.put("手提电话", "phone");
        SYNONYMS.put("cafe", "cafe");
        SYNONYMS.put("coffee shop", "cafe");
    }

    static void index(Map<String, List<String>> index, String docId, String text) {
        String lang = detectLanguage(text);
        String norm = normalize(text);
        for (String token : tokenize(norm, lang)) {
            String concept = SYNONYMS.getOrDefault(token, token);
            index.computeIfAbsent(concept, k -> new ArrayList<>()).add(docId);
        }
    }

    static Set<String> search(Map<String, List<String>> index, String query) {
        String lang = detectLanguage(query);
        String norm = normalize(query);
        Set<String> hits = new LinkedHashSet<>();
        for (String token : tokenize(norm, lang)) {
            String concept = SYNONYMS.getOrDefault(token, token);
            hits.addAll(index.getOrDefault(concept, List.of()));
        }
        return hits;
    }

    public static void main(String[] args) {
        System.out.println("【归一化】");
        for (String s : new String[]{"Café", "CAFÉ", "cafe"}) {
            System.out.printf("  %-6s -> %s%n", s, normalize(s));
        }

        System.out.println();
        System.out.println("【语言识别与分词】");
        String[] samples = {"手机壳", "Mobile case", "телефон"};
        for (String s : samples) {
            String lang = detectLanguage(s);
            System.out.printf("  %-14s lang=%-3s tokens=%s%n", s, lang,
                    tokenize(normalize(s), lang));
        }

        System.out.println();
        Map<String, List<String>> index = new LinkedHashMap<>();
        index(index, "d1", "苹果 手机 壳");
        index(index, "d2", "Mobile phone case");
        index(index, "d3", "smartphone 保护套");
        index(index, "d4", "Café latte");
        System.out.println("【倒排索引】");
        index.forEach((k, v) -> System.out.printf("  %-10s -> %s%n", k, v));

        System.out.println();
        System.out.println("【跨语言查询】");
        for (String q : new String[]{"手机", "phone", "mobile", "CAFE", "café"}) {
            System.out.printf("  查询 %-8s -> 命中 %s%n", q, search(index, q));
        }

        System.out.println();
        System.out.println("  要点：索引与查询用同一套归一化 + 分词 + 同义词（38-02 的 analyzer 一致性）");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现归一化 + 语言识别 + 分词 + 同义词索引，用中英混合文档验证跨语言查询命中。
 * 2. 复现"只归一化查询不归一化索引"导致的搜不到，修复后验证 `Café` 能搜到 `cafe`。
 * 3. 思考题：为什么"中文按二元切分"是简化的可用方案？对比词典分词（38-03）的召回与精度差异。
 * ============================================================
 */
