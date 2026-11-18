/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：40-07
 * 课名：Embedding 与向量检索
 * ------------------------------------------------------------
 * 知识点：
 *   - **Embedding 把文本变成向量，把"相似"变成"距离"**：一段文本经过 embedding 模型映射成一个高维向量（如 1536 维），语义相近的文本向量距离近——"手机壳"与"保护套"的向量很近，与"扳手"很远。**语义检索的地基**。
 *   - **余弦相似度是标准度量**：两个向量的夹角余弦，1 表示方向相同，0 正交，-1 相反。文本场景关注"方向"而非"长度"，所以用余弦而不是欧氏距离。检索实现就是**在所有文档向量里找与查询向量夹角最小的 K 个**。
 *   - **向量数据库解决"海量向量的近邻查找"**：暴力扫是 O(N×d)，亿级不可行。近似最近邻（ANN）索引——HNSW 图、IVF 聚类、乘积量化（PQ）——牺牲一点点召回率换百倍速度。Java 侧对接 ES 的 `dense_vector` + KNN 或专用库（Milvus/pgvector）。本课用纯 JDK 演示暴力检索的完整逻辑（数据量小时够用，几十万条加 ANN）。
 *   - **embedding 的两个重要性质**：① **不能比较不同模型的向量**（维度与语义空间都不同，换了模型必须全量重建）；② **向量携带语义但不携带"精确事实"**——"iPhone 15 售价 5999"这句话的向量与"iPhone 15 价格"近，但检索结果仍要回原文档核实数值。
 *   - **混合检索（hybrid search）是生产标配**：关键词检索（BM25，精确匹配强）+ 向量检索（语义泛化强）各取 top N，用 RRF（Reciprocal Rank Fusion）或加权融合排序。**只向量检索会漏掉"型号、订单号"这类必须精确命中的查询**。
 *   - **重排（rerank）提精度**：召回是"快而糙"（取 top 100），用交叉编码器（cross-encoder）对"查询-文档对"精细打分取 top 5——两阶段是检索的标准架构，与搜索引擎的粗排/精排同理。
 *   - 易错点：向量检索当成精确检索用（订单号查询语义模糊化后召回错误）；embedding 模型换版本不重建索引（新旧向量混在一个空间，检索质量崩塌）；只做向量检索不做混合（关键词强需求场景翻车）；把 embedding 存成 float 明文进日志（维度大、日志爆炸）。
 * ============================================================
 */

package com.heima.ch40;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class EmbeddingSearch {
    /** 简化版 embedding：4 维"语义空间"，向量手工指定以演示机制 */
    static Map<String, double[]> corpus = new LinkedHashMap<>();

    static void initCorpus() {
        // 维度语义示意：[手机配件, 电脑, 售后, 价格]
        corpus.put("手机壳 保护套 软壳", new double[]{0.95, 0.05, 0.1, 0.3});
        corpus.put("笔记本电脑 硬件参数", new double[]{0.1, 0.9, 0.1, 0.2});
        corpus.put("退货退款 政策说明", new double[]{0.1, 0.1, 0.95, 0.1});
        corpus.put("会员价格 优惠券", new double[]{0.2, 0.2, 0.2, 0.9});
    }

    static double cosine(double[] a, double[] b) {
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    static void semanticSearch() {
        System.out.println("【语义检索：查询向量与语料逐一算余弦】");
        initCorpus();
        double[] query = {0.9, 0.1, 0.15, 0.25};   // “手机保护套多少钱”
        record Hit(String doc, double score) {
        }
        List<Hit> hits = new ArrayList<>();
        for (Map.Entry<String, double[]> e : corpus.entrySet()) {
            hits.add(new Hit(e.getKey(), cosine(query, e.getValue())));
        }
        hits.sort(Comparator.comparingDouble(Hit::score).reversed());
        for (Hit h : hits) {
            System.out.printf("  %.4f  %s%n", h.score(), h.doc());
        }
        System.out.println("  “多少钱”命中了价格向量、“保护套”命中了手机壳向量 —— 词不匹配但语义匹配");
        System.out.println();
    }

    static void hybridFuse() {
        System.out.println("【混合检索：BM25 排名 + 向量排名 用 RRF 融合】");
        // 查询“手机壳 2026111701”：关键词检索强命中订单号文档，向量检索强命中壳类文档
        record Doc(String id, double bm25Rank, double vectorRank) {
        }
        List<Doc> docs = List.of(
                new Doc("订单详情页(含2026111701)", 1, 8),
                new Doc("手机壳商品页", 6, 1),
                new Doc("物流说明", 3, 4));
        int k = 60;    // RRF 常数
        for (Doc d : docs) {
            double rrf = 1.0 / (k + d.bm25Rank()) + 1.0 / (k + d.vectorRank());
            System.out.printf("  %-18s bm25排名=%.0f 向量排名=%.0f -> RRF=%.5f%n",
                    d.id(), d.bm25Rank(), d.vectorRank(), rrf);
        }
        System.out.println("  订单号这种精确 token 只有 BM25 找得到；语义近邻只有向量找得到");
        System.out.println("  生产结论：向量检索与关键词检索必须同时存在");
        System.out.println();
    }

    static void pitfalls() {
        System.out.println("【三个高频事故】");
        String[][] items = {
                {"换模型不重建", "A 模型的查询向量 vs B 模型的文档向量 -> 完全无意义，必须全量重嵌"},
                {"只向量无关键词", "搜“iPhone15 壳”被泛化成各种手机配件 -> 加 BM25 混合"},
                {"无重排", "召回 top100 里正确文档排第 30 -> 两阶段：粗召回 + 交叉编码精排"},
        };
        for (String[] i : items) {
            System.out.printf("  %-12s %s%n", i[0], i[1]);
        }
    }

    public static void main(String[] args) {
        semanticSearch();
        hybridFuse();
        pitfalls();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 把示例扩到 12 条语料 + 8 维向量（自己设计语义维度），实现"输入查询向量返回 top3"的检索器，并用 5 个查询验证语义相关性。
 * 2. 实现 RRF 融合检索器：给定 BM25 与向量两路排名列表，输出融合后的 top5，比较"只用向量/只用关键词/混合"三种结果差异。
 * 3. 思考题：为什么 embedding 向量换模型必须全量重建？从"语义空间是模型私有"的角度解释，并给出重建期间的灰度方案。
 * ============================================================
 */
