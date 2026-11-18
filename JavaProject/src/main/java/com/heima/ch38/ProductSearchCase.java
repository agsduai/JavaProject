/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：38-11
 * 课名：综合案例：商品搜索全链路
 * ------------------------------------------------------------
 * 知识点：
 *   - **把全章串成一条查询链路**：用户输入 → 查询预处理（去噪、同义扩展）→ 分词（ik_smart）→ bool 查询（must 全文 + filter 条件 + should 加权）→ BM25 评分 + function_score 业务加权 → 分页（search_after 或 from+size）→ 聚合（类目/品牌导航栏）→ 高亮返回 → 前端渲染。每一课的知识都在这条链路上各就各位。
 *   - **索引设计**：title/description 用 text（ik_max_word 索引侧），category/brand/status 用 keyword，price 用 scaled_float，销量与上架时间作为 function_score 输入；`copy_to` 把 title+brand+category 拼进一个 all 字段简化查询。
 *   - **查询侧四层过滤**：keyword（全文 must，参与评分）、条件（filter：状态/价格区间/类目，不评分可缓存）、业务加权（function_score：销量+时效）、排序兜底（score desc, id asc 保证 search_after 唯一）。
 *   - **左侧导航栏的聚合要用"全集"**：terms 聚合挂在 query 之后、post_filter 之前，用户选了品牌后类目栏仍显示所有类目的计数（否则用户只能看到单一类目，无法切换——38-07 的 post_filter 知识点）。
 *   - **高亮与返回裁剪**：highlight 标记命中词片段；`_source` 只取前端需要的字段（不要把整个文档原样返回）；空结果时降级（放宽 minimum_should_match 或去掉业务过滤再查一次）。
 *   - **上线前验收清单**：mapping 全部显式声明；同步链路有对账兜底；压测目标 QPS 达标（P99 < 200ms）；排序盲测（改评分参数前后对比 top 10）；空查询/超长查询/特殊字符等边界用例。
 *   - 易错点：搜索接口每次请求都全量查 ES 拉所有字段（用 _source 过滤 + 分页）；没有降级的空结果页（体验断裂）；聚合与结果用了不同过滤条件导致导航栏计数与结果数不一致；上线后从不看慢查询日志（slow log 是搜索质量的第一观测面）。
 * ============================================================
 */

package com.heima.ch38;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class ProductSearchCase {
    record Product(long id, String title, String category, String brand,
                   String status, double price, int sales, long listedDaysAgo) {
    }

    static final List<Product> ES_VIEW = List.of(
            new Product(1, "苹果 iPhone 15 官方手机壳", "phone", "Apple", "ONSALE", 69, 5200, 10),
            new Product(2, "小米手机 透明保护壳", "phone", "Xiaomi", "ONSALE", 39, 8100, 30),
            new Product(3, "苹果 MacBook Pro 14", "laptop", "Apple", "ONSALE", 14999, 900, 90),
            new Product(4, "华为 Mate60 手机壳 皮质", "phone", "Huawei", "OFFSALE", 59, 300, 60),
            new Product(5, "手机支架 铝合金 桌面", "stand", "Baseus", "ONSALE", 29, 12000, 5));

    record SearchRequest(String keyword, String category, String brand,
                         double minPrice, double maxPrice, boolean onSaleOnly) {
    }

    /** 查询预处理：小写 + 同义扩展 */
    static List<String> preprocess(String input) {
        List<String> terms = new ArrayList<>();
        for (String t : input.toLowerCase().split("\\s+")) {
            terms.add(t);
            if (t.equals("苹果")) {
                terms.add("apple");          // 同义词扩展：苹果 <-> apple
            }
        }
        return terms;
    }

    static boolean titleMatch(Product p, List<String> terms) {
        String lower = (p.title() + " " + p.brand()).toLowerCase();
        return terms.stream().anyMatch(lower::contains);   // 至少 1 个词命中（should 语义）
    }

    static double score(Product p, List<String> terms) {
        String lower = (p.title() + " " + p.brand()).toLowerCase();
        double bm25 = terms.stream().filter(lower::contains).count() * 2.0;
        double salesBoost = 1.0 + p.sales() / 100000.0;
        double recencyBoost = 1.0 + Math.max(0, 60 - p.listedDaysAgo()) / 200.0;
        return bm25 * salesBoost * recencyBoost;
    }

    static void execute(SearchRequest req) {
        System.out.println("---- 查询：" + req.keyword() + " category=" + req.category()
                + " brand=" + req.brand() + " price=" + req.minPrice() + "~" + req.maxPrice()
                + (req.onSaleOnly() ? " 仅在售" : ""));
        List<String> terms = preprocess(req.keyword());
        // 1) 全文 must（参与评分）
        List<Product> matched = new ArrayList<>();
        for (Product p : ES_VIEW) {
            if (titleMatch(p, terms)) {
                matched.add(p);
            }
        }
        // 2) filter 条件（不评分、可缓存）
        matched = matched.stream()
                .filter(p -> req.category() == null || req.category().equals(p.category()))
                .filter(p -> req.brand() == null || req.brand().equalsIgnoreCase(p.brand()))
                .filter(p -> p.price() >= req.minPrice() && p.price() <= req.maxPrice())
                .filter(p -> !req.onSaleOnly() || "ONSALE".equals(p.status()))
                .toList();
        // 3) 评分排序（score desc, id asc 保证游标唯一）
        List<Product> ranked = matched.stream()
                .sorted(Comparator.comparingDouble((Product p) -> score(p, terms)).reversed()
                        .thenComparingLong(Product::id))
                .toList();
        for (Product p : ranked) {
            System.out.printf("    [%.3f] %d %s（%s）￥%.0f 销量%d%n",
                    score(p, terms), p.id(), p.title(), p.brand(), p.price(), p.sales());
        }
        // 4) 聚合：挂在 filter 全集上（post_filter 语义），导航栏始终显示全部类目
        Map<String, Integer> catAgg = new LinkedHashMap<>();
        List<Product> aggScope = ES_VIEW.stream()
                .filter(p -> req.brand() == null || req.brand().equalsIgnoreCase(p.brand()))
                .toList();
        for (Product p : aggScope) {
            catAgg.merge(p.category(), 1, Integer::sum);
        }
        System.out.println("    类目导航栏：" + catAgg);
        System.out.println();
    }

    static void highlightDemo() {
        System.out.println("【高亮与 _source 裁剪】");
        Product p = ES_VIEW.get(0);
        String hl = p.title().replace("手机壳", "<em>手机壳</em>");
        System.out.println("  返回字段：id/title(高亮)/price/brand —— 不回传 sales、listedDays 等");
        System.out.println("  高亮片段：" + hl);
    }

    public static void main(String[] args) {
        execute(new SearchRequest("苹果 手机", null, null, 0, 999999, true));
        execute(new SearchRequest("苹果 手机", "phone", null, 0, 100, true));
        execute(new SearchRequest("支架", null, "Baseus", 0, 999999, false));
        highlightDemo();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 把本章案例在你的 ES 里真实落地：建索引（显式 mapping）→ bulk 写入 100 条商品 → 实现查询 DSL（bool+function_score+聚合+post_filter+高亮）→ 对比预期结果。
 * 2. 压测与调优：对搜索接口做 500 QPS 压测，记录 P99；打开 slow log（阈值 200ms），找到最慢的一条查询并给出优化（filter 化、聚合瘦身、_source 裁剪）。
 * 3. 设计空结果降级：关键词无命中时自动放宽（去掉品牌过滤 / 降低 minimum_should_match / 推荐同类目热销），实现并说明每级降级的触发条件。
 * 4. 思考题：商品搜索里"相关性与销量谁该排前面"？给出你的 function_score 设计，并说明在什么业务阶段应该把天平偏向哪边。
 * ============================================================
 */
