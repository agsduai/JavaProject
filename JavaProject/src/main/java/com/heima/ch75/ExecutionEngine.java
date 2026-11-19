/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：75-04
 * 课名：执行引擎与 Resolver
 * ------------------------------------------------------------
 * 知识点：
 *   - **执行引擎的工作**：按 AST 逐字段调用 **Resolver**（`resolve(parent, args, context)`），**返回值的形状必须匹配 schema**——对象字段递归调用其子字段 resolver，标量直接返回。
 *   - **Resolver 的四类实现**：**内存取数**（已加载的对象字段）、**服务调用**（RPC/HTTP 调下游）、**数据加载**（DB 查询，N+1 风险点 75-05）、**计算派生**（字段由其他字段算出）。
 *   - **Context 贯穿整个请求**：放认证信息（74 章）、请求 ID（42 章 trace）、数据加载器（75-05）——**resolver 不直接依赖全局状态**。
 *   - **执行顺序**：**同一层的字段并行执行**（可并发取数），**父子串行**（父 resolve 出对象才能取子字段）——**这决定了 N+1 的形态**（父返回 N 个对象，每个对象的子字段各查一次）。
 *   - 与 64-09 的关系：GraphQL 的错误是"字段级"的（部分成功 75-07），不是"整体失败"——**resolver 抛错只影响该字段**。
 *   - 易错点：resolver 里做重活（没缓存/没批处理）；resolver 返回类型与 schema 不符（运行期报错）；context 无 traceId（排障困难）；把业务逻辑写进 resolver（应该调服务层）。
 * ============================================================
 */

package com.heima.ch75;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class ExecutionEngine {
    record Product(long id, String name, double price, long brandId) {
    }

    record Brand(long id, String name) {
    }

    record Review(long productId, int score, String content) {
    }

    /** 模拟数据源 */
    static final Map<Long, Product> productDb = new LinkedHashMap<>();
    static final Map<Long, Brand> brandDb = new LinkedHashMap<>();
    static final List<Review> reviewDb = new ArrayList<>();

    static void seed() {
        productDb.put(1L, new Product(1, "手机壳", 69.0, 10));
        productDb.put(2L, new Product(2, "蓝牙耳机", 129.0, 10));
        brandDb.put(10L, new Brand(10, "自有品牌"));
        reviewDb.add(new Review(1, 5, "好用"));
        reviewDb.add(new Review(1, 4, "一般"));
        reviewDb.add(new Review(2, 5, "音质好"));
    }

    /** Resolver 注册表：字段路径 -> 取数函数 */
    interface Resolver {
        Object resolve(Object parent, Map<String, String> args, Map<String, Object> ctx);
    }

    static final Map<String, Resolver> resolvers = new LinkedHashMap<>();

    static void register() {
        resolvers.put("Query.product", (p, a, ctx) -> {
            long id = Long.parseLong(a.getOrDefault("id", "0"));
            Map<String, Object> stats = stats(ctx);
            stats.put("dbQuery", (Integer) stats.getOrDefault("dbQuery", 0) + 1);
            return productDb.get(id);
        });
        resolvers.put("Product.brand", (p, a, ctx) -> {
            Product prod = (Product) p;
            Map<String, Object> stats = stats(ctx);
            stats.put("dbQuery", (Integer) stats.getOrDefault("dbQuery", 0) + 1);                     // N+1 发生点
            return brandDb.get(prod.brandId());
        });
        resolvers.put("Product.reviews", (p, a, ctx) -> {
            Product prod = (Product) p;
            Map<String, Object> stats = stats(ctx);
            stats.put("dbQuery", (Integer) stats.getOrDefault("dbQuery", 0) + 1);                     // N+1 发生点
            return reviewDb.stream().filter(r -> r.productId() == prod.id()).toList();
        });
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> stats(Map<String, Object> ctx) {
        return (Map<String, Object>) ctx.computeIfAbsent("stats",
                k -> new LinkedHashMap<String, Object>());
    }

    /** 简化执行：product(id){name price brand{name} reviews{score}} */
    static Map<String, Object> execute(long productId, boolean wantBrand,
                                       boolean wantReviews, Map<String, Object> ctx) {
        Map<String, Object> out = new LinkedHashMap<>();
        Map<String, String> args = Map.of("id", String.valueOf(productId));
        Object p = resolvers.get("Query.product").resolve(null, args, ctx);
        if (p == null) {
            out.put("product", null);
            return out;
        }
        Product prod = (Product) p;
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("name", prod.name());
        fields.put("price", prod.price());
        if (wantBrand) {
            Object b = resolvers.get("Product.brand").resolve(prod, Map.of(), ctx);
            fields.put("brand", b == null ? null
                    : Map.of("name", ((Brand) b).name()));
        }
        if (wantReviews) {
            Object rs = resolvers.get("Product.reviews").resolve(prod, Map.of(), ctx);
            List<Map<String, Object>> list = new ArrayList<>();
            for (Object o : (List<?>) rs) {
                list.add(Map.of("score", ((Review) o).score()));
            }
            fields.put("reviews", list);
        }
        out.put("product", fields);
        return out;
    }

    public static void main(String[] args) {
        seed();
        register();

        Map<String, Object> ctx = new LinkedHashMap<>();
        System.out.println("【执行：product(1) { name price brand{name} reviews{score} }】");
        System.out.println("  结果：" + execute(1, true, true, ctx));
        System.out.println("  DB 查询次数：" + stats(ctx).get("dbQuery"));

        System.out.println();
        Map<String, Object> ctx2 = new LinkedHashMap<>();
        System.out.println("【执行：product(1) { name price }（不取关联）】");
        System.out.println("  结果：" + execute(1, false, false, ctx2));
        System.out.println("  DB 查询次数：" + stats(ctx2).get("dbQuery") + "（只查主表）");

        System.out.println();
        System.out.println("【执行顺序】同层字段可并行；父子串行（先有 product 才能取 brand）");
        System.out.println("  N+1 形态：父返回 N 个对象 -> 每个对象的 brand 各查一次（75-05 解决）");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 扩展执行引擎：支持"列表查询 products(first: 10) + 每项取 brand"，统计 DB 查询次数（观察 N+1）。
 * 2. 把 resolver 的"数据访问"抽成服务层调用，说明"业务逻辑不进 resolver"的收益。
 * 3. 思考题：为什么"同层字段可并行"？从"父对象已就绪"与"字段间无依赖"分析。
 * ============================================================
 */
