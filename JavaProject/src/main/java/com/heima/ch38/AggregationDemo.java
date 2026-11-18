/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：38-07
 * 课名：聚合分析
 * ------------------------------------------------------------
 * 知识点：
 *   - **聚合 = "在搜索结果上做统计"**：与 SQL 的 GROUP BY 对应，但跑在倒排/列存(doc_values)上、针对的是**本次查询命中的集合**，天然支持"先过滤再统计"。
 *   - **两大类聚合**：桶聚合（bucket）按字段切分组——terms（类目、状态）、range（价格段）、date_histogram（按天）；度量聚合（metric）算数值——avg/max/sum/cardinality/percentiles。桶里可以再嵌桶、再嵌度量，构成"类目 → 价格分布 → 均价"的多层报表。
 *   - **聚合字段必须开 doc_values**（keyword/数值默认开）：text 字段聚合需要 fielddata（整字段加载进堆内存），是性能杀手，规范禁止。这就是 38-04 强调状态字段用 keyword 的原因。
 *   - **terms 聚合不是精确 top**：默认按分片本地 top N（shard_size）合并，可能漏掉稀疏桶。需要精确时调大 `shard_size` 或用 `composite` 分页聚合。cardinality 也是近似值（HyperLogLog）。
 *   - **聚合与搜索上下文的关系**：默认聚合作用于 query 命中的文档；`post_filter` 在聚合之后才过滤（用于"点了类目筛选但左侧类目栏仍显示全部"）；`global` 聚合完全无视查询条件。
 *   - **分页聚合**：terms 聚合取 top 10 之外的桶要用 composite（按 after_key 翻页），不能靠 from/size —— 聚合没有深度分页问题但有桶数上限（search.max_buckets）。
 *   - 易错点：对 text 字段做 terms 聚合（报 fielddata 错或按分词分组）；date_histogram 不设 calendar_interval 逻辑（按天/月写错）；聚合桶数上万（响应巨大 + JVM 压力，应改 composite 或减少粒度）；把聚合当实时 OLAP 大数据量扫描（超过亿级的分析应走专门数仓）。
 * ============================================================
 */

package com.heima.ch38;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

// ==================== 示例代码 ====================

public class AggregationDemo {
    record Doc(long id, String category, String status, double price, String day) {
    }

    static final List<Doc> HITS = List.of(
            new Doc(1, "phone", "ONSALE", 69.0, "2026-11-15"),
            new Doc(2, "phone", "ONSALE", 39.0, "2026-11-15"),
            new Doc(3, "laptop", "ONSALE", 14999.0, "2026-11-16"),
            new Doc(4, "phone", "OFFSALE", 59.0, "2026-11-16"),
            new Doc(5, "stand", "ONSALE", 29.0, "2026-11-17"),
            new Doc(6, "stand", "ONSALE", 35.0, "2026-11-17"));

    static void termsAgg() {
        System.out.println("【terms 聚合：类目分布】");
        Map<String, Integer> buckets = new TreeMap<>();
        for (Doc d : HITS) {
            buckets.merge(d.category(), 1, Integer::sum);
        }
        buckets.entrySet().stream()
                .sorted((a, b) -> b.getValue() - a.getValue())
                .forEach(e -> System.out.printf("  %-8s doc_count=%d%n", e.getKey(), e.getValue()));
        System.out.println("  字段类型必须是 keyword（doc_values），text 聚合 = 禁止");
        System.out.println();
    }

    static void metricInsideBucket() {
        System.out.println("【桶内嵌度量：每个类目的均价与最高价】");
        Map<String, List<Double>> byCat = new LinkedHashMap<>();
        for (Doc d : HITS) {
            byCat.computeIfAbsent(d.category(), k -> new java.util.ArrayList<>()).add(d.price());
        }
        for (Map.Entry<String, List<Double>> e : byCat.entrySet()) {
            List<Double> ps = e.getValue();
            double avg = ps.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            double max = ps.stream().mapToDouble(Double::doubleValue).max().orElse(0);
            System.out.printf("  %-8s count=%d avg=%.1f max=%.1f%n",
                    e.getKey(), ps.size(), avg, max);
        }
        System.out.println("  DSL 结构：aggs: {by_cat: {terms: {field: category},");
        System.out.println("             aggs: {avg_price: {avg: {field: price}}}}}");
        System.out.println();
    }

    static void histogramAndDate() {
        System.out.println("【直方图类聚合：价格段与按天】");
        Map<String, Integer> priceBuckets = new LinkedHashMap<>();
        String[] ranges = {"0-50", "50-100", "100+"};
        for (Doc d : HITS) {
            String b = d.price() < 50 ? ranges[0] : d.price() < 100 ? ranges[1] : ranges[2];
            priceBuckets.merge(b, 1, Integer::sum);
        }
        System.out.println("  price range：" + priceBuckets);
        Map<String, Integer> byDay = new TreeMap<>();
        for (Doc d : HITS) {
            byDay.merge(d.day(), 1, Integer::sum);
        }
        System.out.println("  date_histogram(calendar_interval=day)：" + byDay);
        System.out.println();
    }

    static void postFilterIdea() {
        System.out.println("【post_filter：聚合看全集，结果看筛选集】");
        System.out.println("  页面布局：左侧类目栏要显示“全部命中里的类目分布”，");
        System.out.println("  结果列表只显示“用户已选类目”的文档");
        Map<String, Integer> aggBuckets = new TreeMap<>();
        for (Doc d : HITS) {
            aggBuckets.merge(d.category(), 1, Integer::sum);
        }
        List<Doc> filtered = HITS.stream().filter(d -> d.category().equals("phone")).toList();
        System.out.println("  聚合（post_filter 之前）：" + aggBuckets);
        System.out.println("  结果（post_filter 之后）  ："
                + filtered.stream().map(d -> d.id()).toList());
        System.out.println("  若把类目筛选放进 query，左侧栏就只剩一个类目——经典 bug");
    }

    public static void main(String[] args) {
        termsAgg();
        metricInsideBucket();
        histogramAndDate();
        postFilterIdea();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 为商品搜索页写出完整 DSL：bool 过滤（在售）+ terms 聚合（类目、品牌）+ 桶内均价 + post_filter（用户选中的品牌），并解释各部分为何放在那个上下文。
 * 2. 用 date_histogram 实现"近 30 天每日订单量与销售额"，写出聚合树；再把每日订单量换成分时段（小时）直方图，对比两种 interval 的适用场景。
 * 3. 思考题：为什么 terms 聚合在多分片下可能漏桶？写出 shard_size、size、总文档数三者的关系，以及一个"必须精确"的应对方案。
 * ============================================================
 */
