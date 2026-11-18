/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：38-06
 * 课名：相关性评分与 BM25
 * ------------------------------------------------------------
 * 知识点：
 *   - **相关性回答"哪条结果排前面"**：没有评分的搜索只是过滤。Lucene 默认用 BM25：得分由**词频 TF**（这篇文档里该词出现几次，有饱和上限——出现 100 次不比 10 次强多少）、**逆文档频率 IDF**（越稀有的词越有区分度："的"贡献为 0，"iPhone"贡献很大）、**字段长度归一**（短字段里命中一个词比长字段命中权重高）三项合成。
 *   - **TF-IDF 是 BM25 的前身**：TF×log(总文档/含词文档)。BM25 在其上加两个可调参数：k1（词频饱和速度，默认 1.2）与 b（字段长度归一强度，默认 0.75），实践中 BM25 的排序明显更稳。
 *   - **多字段评分**：multi_match 的 `best_fields`（默认，取最高分字段）、`most_fields`（各字段分相加，多分析器互补）、`cross_fields`（把多字段当一个大字段，对"姓 名"跨字段短语很关键）。boost 参数放大某字段的权重（标题 boost=3，描述 boost=1）。
 *   - **function_score 做"业务加权"**：相关性只是排序的一半，另一半是业务信号——销量高一点、新上架一点、库存充足一点。`function_score` 在 BM25 分数上乘/加一个函数（field_value_factor、随机、衰减 gauss），是搜索排序从"纯文本相关"走向"业务排序"的正式入口。
 *   - **rescore 二次精排**：第一轮用便宜的方法取 top 100~200，再对这批用短语匹配/更贵函数精排取 top 10 —— 大索引上"既要召回又要排序质量"的标准折衷。
 *   - **explain 是评分的显微镜**：`explain: true` 返回每个 term 的 IDF/TF/字段长度明细，排不对的时候不要猜，直接看公式每一项。
 *   - 易错点：Boost 乱调（×10 之后其他字段等于不存在，排序被单一字段劫持）；把"价格低"塞进 function_score 的加法里（价格影响远大于 BM25，直接变成"按价格排序"）；对长文本字段忘开 `norms:false`（无谓的内存）；期望"改动评分参数立即生效"（要重建或 rescore 验证，并压测排序质量）。
 * ============================================================
 */

package com.heima.ch38;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class RelevanceScore {
    record Doc(long id, String title, int sales) {
    }

    static final List<Doc> DOCS = List.of(
            new Doc(1, "手机壳", 500),
            new Doc(2, "手机壳 手机支架", 120),
            new Doc(3, "透明手机保护壳 手机壳", 80));

    /** BM25 可读版（单项查询）：IDF × 词频项（含字段长度归一） */
    static double bm25(int tf, int df, long totalDocs, int fieldLen, double avgLen,
                       double k1, double b) {
        double idf = Math.log(1 + (totalDocs - df + 0.5) / (df + 0.5));
        double tfTerm = tf * (k1 + 1)
                / (tf + k1 * (1 - b + b * fieldLen / avgLen));
        return idf * tfTerm;
    }

    static void scoreTable() {
        System.out.println("【同一查询词“手机壳”，三篇文档的得分要素】");
        long total = DOCS.size();
        String term = "手机壳";
        int df = 2;   // 文档1、3 含“手机壳”（本例按词项精确计数）
        double avgLen = DOCS.stream().mapToDouble(d -> d.title().length()).average().orElse(1);
        for (Doc d : DOCS) {
            int tf = countOccurrences(d.title(), term);
            int len = d.title().length();
            double score = bm25(tf, df, total, len, avgLen, 1.2, 0.75);
            System.out.printf("  doc%d tf=%d len=%-12d score=%.4f%n", d.id(), tf, len, score);
        }
        System.out.println("  要点：tf 有饱和（重复出现增益递减）；字段越短归一后越占优");
        System.out.println();
    }

    private static int countOccurrences(String text, String term) {
        int c = 0, idx = 0;
        while ((idx = text.indexOf(term, idx)) >= 0) {
            c++;
            idx += term.length();
        }
        return c;
    }

    static void idfSense() {
        System.out.println("【IDF 的直觉：稀有词才有区分度】");
        String[][] terms = {{"的", "df≈全部文档", "≈0.0"}, {"手机", "df=8000/100000", "≈2.5"},
                {"iphone15", "df=300/100000", "≈5.8"}};
        for (String[] t : terms) {
            System.out.printf("  词 %-10s 文档频率 %-14s IDF≈%s%n", t[0], t[1], t[2]);
        }
        System.out.println("  查询分词后：停用词几乎不加分，品牌型号词主导排序");
        System.out.println();
    }

    static void functionScore() {
        System.out.println("【BM25 × 业务信号：function_score 演示】");
        double bm25Weight = 1.0, salesWeight = 0.0005;
        for (Doc d : DOCS) {
            double bm25 = bm25(countOccurrences(d.title(), "手机壳"), df(), DOCS.size(),
                    d.title().length(), avgLen(), 1.2, 0.75);
            double boost = 1.0 + salesWeight * d.sales();
            System.out.printf("  doc%d bm25=%.3f 销量=%d 业务加权=×%.2f -> %.3f%n",
                    d.id(), bm25, d.sales(), boost, bm25 * boost);
        }
        System.out.println("  设计原则：业务因子做乘法且幅度温和，让“相关性”仍是第一排序键");
        System.out.println("  加法陷阱：+sales/1000 会直接淹没 BM25，排序退化为按销量");
    }

    static int df() {
        return 2;
    }

    static double avgLen() {
        return DOCS.stream().mapToDouble(d -> d.title().length()).average().orElse(1);
    }

    public static void main(String[] args) {
        scoreTable();
        idfSense();
        functionScore();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 手算验证：文档 A（tf=3, len=6）与文档 B（tf=3, len=60）查询同一词，用示例公式算分并解释字段长度归一的方向。
 * 2. 给商品搜索设计一个 function_score：相关性 × 业务加权（销量 + 时效衰减 gauss(now, 30d, 180d)），写出公式并论证权重幅度不会劫持排序。
 * 3. 用 explain 复盘一次"排序不符合直觉"的查询，把每个 term 的 IDF/TF 项摘出来，指出真正导致错序的那一项。
 * 4. 思考题：为什么 rescore 要限定在 top N 上做？如果对全部命中结果精排，代价曲线是什么形状？
 * ============================================================
 */
