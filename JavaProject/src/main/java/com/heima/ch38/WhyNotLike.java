/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：38-01
 * 课名：为什么不用数据库 LIKE
 * ------------------------------------------------------------
 * 知识点：
 *   - **`LIKE '%关键词%'` 的问题不是慢，而是"不理解语言"**：它只能匹配连续子串。搜"手机壳"匹配不到含"手机 保护壳"的商品，搜"苹果"匹配不到"iPhone"（没有同义与分词概念），且前置 `%` 让索引彻底失效，千万行表全表扫描。
 *   - **全文检索的思路完全不同**：先把文本**切成词（分词）**，再建"词 → 文档列表"的映射，这就是**倒排索引**。查询时不再逐行扫描，而是查表取交集，复杂度与命中文档数相关而不是与总文档数相关。
 *   - **倒排索引 = 图书馆的"关键词卡片柜"**：正排（数据库）是"一书一柜按编号找书"，倒排是"先翻关键词卡片，卡片上写着哪些书提到它"。查询"手机 壳"变成：取"手机"的卡片 ∩ 取"壳"的卡片。
 *   - **Elasticsearch 的三个核心概念**：Index（类似表）、Document（JSON 文档，类似行）、Inverted Index（内部结构）。底层是 Lucene：每次写入先进内存缓冲，定期 refresh 成不可变 segment，这就是"近实时（NRT）搜索，默认 1 秒可见"的原因。
 *   - **ES 不适合做的事**：强事务（写入可见有延迟）、频繁更新（倒排索引更新代价高，本质是标记删除+重建）、复杂关联查询（没有 join，靠冗余或应用层组装）。**搜索库与数据库是分工，不是替代**：MySQL 存真源，ES 存搜索视图。
 *   - **ES 的典型适用场景**：商品搜索、日志检索（ELK）、站内全文、自动补全、相似内容推荐、多维聚合分析报表。
 *   - 易错点：把 ES 当主存储（ES 丢数据无法从自身恢复）；搜不到就怪 ES（十有八九是分词器没配对，`match` 查询被分词后没有任何 term 命中）；用 ES 做"每秒上千次更新"的字段（改成把易变字段留在数据库，搜索时回查）。
 * ============================================================
 */

package com.heima.ch38;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class WhyNotLike {
    record Product(long id, String title) {
    }

    static final List<Product> DB = List.of(
            new Product(1, "苹果 iPhone 15 官方手机壳"),
            new Product(2, "小米手机 保护壳 透明款"),
            new Product(3, "华为 Mate60 皮套保护壳"),
            new Product(4, "苹果 MacBook Pro 14"),
            new Product(5, "手机支架 桌面铝合金"));

    /** 数据库式 LIKE：连续子串匹配，前置 % 无法用索引 */
    static List<Product> likeSearch(List<Product> all, String keyword) {
        List<Product> hits = new ArrayList<>();
        for (Product p : all) {
            if (p.title().contains(keyword)) {
                hits.add(p);
            }
        }
        return hits;
    }

    static void likeLimits() {
        System.out.println("【LIKE 的两个盲区】");
        String[][] cases = {
                {"手机壳", "没有任何标题含连续子串“手机壳” -> 0 命中，但用户想要的就是它"},
                {"苹果", "只命中标题含“苹果”的，iPhone 属于同义词世界，LIKE 永远不知道"},
        };
        for (String[] c : cases) {
            List<Product> hits = likeSearch(DB, c[0]);
            System.out.printf("  搜索“%s”：LIKE 命中 %d 条  —— %s%n", c[0], hits.size(), c[1]);
        }
        System.out.println("  性能：LIKE '%x%' 需要扫描全部 5 行中的每一行；10 万行就是 10 万次 contains");
        System.out.println();
    }

    /** 全文检索式：先分词，再按词表命中 */
    static void invertedThinking() {
        System.out.println("【倒排索引思想：查询从“扫文档”变成“查词表”】");
        Map<String, List<Long>> postings = new LinkedHashMap<>();
        addTo(postings, "苹果", 1);
        addTo(postings, "iphone", 1);
        addTo(postings, "手机", 1);
        addTo(postings, "壳", 1);
        addTo(postings, "小米", 2);
        addTo(postings, "手机", 2);
        addTo(postings, "保护壳", 2);
        addTo(postings, "华为", 3);
        addTo(postings, "保护壳", 3);
        addTo(postings, "手机", 5);
        System.out.println("  倒排表（词 -> 含此词的文档）：");
        for (Map.Entry<String, List<Long>> e : postings.entrySet()) {
            System.out.printf("    %-8s -> %s%n", e.getKey(), e.getValue());
        }
        // 查询“手机 壳”：手机->[1,2,5] 壳->[1] 取交集
        List<Long> a = postings.get("手机"), b = postings.get("壳");
        List<Long> and = new ArrayList<>(a);
        and.retainAll(b);
        System.out.println("  查询「手机 壳」：[1,2,5] ∩ [1] = " + and + " -> 命中文档1");
        System.out.println("  复杂度与命中数相关，与文档总量无关 —— 这是检索快的本质");
        System.out.println();
    }

    private static void addTo(Map<String, List<Long>> postings, String term, long doc) {
        postings.computeIfAbsent(term, k -> new ArrayList<>()).add(doc);
    }

    static void divisionOfLabor() {
        System.out.println("【数据库与搜索库的分工】");
        String[][] rows = {
                {"真源数据", "MySQL（事务、约束、强一致）"},
                {"搜索视图", "ES（倒排索引、评分、聚合），数据由同步管道写入"},
                {"写入路径", "业务 -> MySQL -> 同步(binlog/MQ) -> ES"},
                {"查询路径", "列表搜索走 ES，订单详情等强一致查询回 MySQL"},
                {"故障恢复", "ES 丢了可以从 MySQL 全量重建，反之不成立"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-8s %s%n", r[0], r[1]);
        }
    }

    public static void main(String[] args) {
        likeLimits();
        invertedThinking();
        divisionOfLabor();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 对你自己项目的核心表，列出 3 个"LIKE 永远做不好"的搜索需求，逐条说明需要的分词/同义/权重能力。
 * 2. 手工把 5 条商品标题按自己的分词规则写入倒排表（纸面即可），执行查询「保护壳 华为」写出交集过程。
 * 3. 思考题：为什么 ES 写入后默认 1 秒才可搜？如果把 refresh 间隔调成 1s -> 60s，写入吞吐与实时性分别怎么变？
 * ============================================================
 */
