/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：75-11
 * 课名：综合案例：商品 GraphQL 网关
 * ------------------------------------------------------------
 * 知识点：
 *   - **把本章收口成一个可运行的商品网关**：**Schema**（75-02）-> **解析**（75-03）-> **执行 + Resolver**（75-04）-> **DataLoader 解 N+1**（75-05）-> **Mutation 幂等**（75-06）-> **部分成功**（75-07）-> **复杂度限制**（75-08）-> **安全**（75-09）-> **联邦路由**（75-10）。
 *   - **一次完整请求的链路**：解析查询 -> 校验（深度/复杂度/字段存在）-> 鉴权（字段级 74-04）-> 执行（resolver + DataLoader 批量）-> 组装响应（data + errors）。
 *   - **验收清单**：查询形状与 schema 一致、N+1 已消除、复杂度超限被拒、字段级鉴权生效、部分失败可定位、Mutation 幂等。
 *   - 与全书的关系：GraphQL 网关把 54（数据）、74（权限）、42（可观测）、64（治理）在"API 形态"上组合——**它和 69/70/71 章一样，是"能力的组装"**。
 *   - 易错点：无复杂度限制；字段不鉴权；N+1 未处理；错误无 code；Mutation 无幂等。
 * ============================================================
 */

package com.heima.ch75;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

// ==================== 示例代码 ====================

public class ProductGraphqlCase {
    record Product(long id, String name, double price, double cost, long brandId) {
    }

    record Brand(long id, String name) {
    }

    static final Map<Long, Product> products = new LinkedHashMap<>();
    static final Map<Long, Brand> brands = new LinkedHashMap<>();
    static final Map<String, Object> stats = new LinkedHashMap<>();

    static void seed() {
        products.put(1L, new Product(1, "手机壳", 69.0, 20.0, 10));
        products.put(2L, new Product(2, "耳机", 129.0, 60.0, 10));
        products.put(3L, new Product(3, "充电器", 39.0, 15.0, 20));
        brands.put(10L, new Brand(10, "自有品牌"));
        brands.put(20L, new Brand(20, "第三方"));
    }

    /** 字段级权限（74-04/75-09） */
    static boolean canRead(Set<String> perms, String field) {
        return switch (field) {
            case "cost" -> perms.contains("product:read:cost");
            case "price" -> perms.contains("product:read:price") || perms.contains("product:read");
            default -> perms.contains("product:read");
        };
    }

    /** DataLoader：批量取品牌（75-05） */
    static class BrandBatch {
        final java.util.Set<Long> queue = new java.util.LinkedHashSet<>();
        final Map<Long, Brand> cache = new LinkedHashMap<>();

        void load(long id) {
            if (!cache.containsKey(id)) {
                queue.add(id);
            }
        }

        void dispatch() {
            if (queue.isEmpty()) {
                return;
            }
            stats.put("brandBatchQueries", (Integer) stats.getOrDefault("brandBatchQueries", 0) + 1);
            System.out.printf("    [DB] 批量查品牌 %s（1 次）%n", queue);
            for (Long id : queue) {
                Brand b = brands.get(id);
                if (b != null) {
                    cache.put(id, b);
                }
            }
            queue.clear();
        }

        Brand get(long id) {
            return cache.get(id);
        }
    }

    record FieldNode(String name, List<FieldNode> children) {
    }

    static int complexity(FieldNode n) {
        int self = n.name().equals("products") ? 10 : 1;
        int sum = self;
        for (FieldNode c : n.children()) {
            sum += complexity(c);
        }
        return sum;
    }

    static void execute(Set<String> perms, List<String> fields, int maxComplexity) {
        FieldNode root = new FieldNode("products", fields.stream()
                .map(f -> new FieldNode(f, List.of())).toList());
        int c = complexity(root);
        System.out.printf("  复杂度=%d（上限 %d）-> %s%n", c, maxComplexity,
                c > maxComplexity ? "拒绝" : "通过");
        if (c > maxComplexity) {
            return;
        }

        List<Map<String, Object>> data = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        BrandBatch batch = new BrandBatch();
        stats.put("productQueries", (Integer) stats.getOrDefault("productQueries", 0) + 1);
        System.out.println("    [DB] 查商品列表（1 次）");

        // 阶段一：收集所有 brandId（DataLoader）
        if (fields.contains("brand")) {
            for (Product p : products.values()) {
                batch.load(p.brandId());
            }
            batch.dispatch();
        }

        // 阶段二：组数据（字段级权限）
        for (Product p : products.values()) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (String f : fields) {
                if (!canRead(perms, f)) {
                    row.put(f, null);
                    errors.add(p.id() + "." + f + ": FORBIDDEN");
                    continue;
                }
                switch (f) {
                    case "name" -> row.put(f, p.name());
                    case "price" -> row.put(f, p.price());
                    case "cost" -> row.put(f, p.cost());
                    case "brand" -> {
                        Brand b = batch.get(p.brandId());
                        row.put(f, b == null ? null : b.name());
                    }
                    default -> {
                        row.put(f, null);
                        errors.add(p.id() + "." + f + ": UNKNOWN_FIELD");
                    }
                }
            }
            data.add(row);
        }

        System.out.println("  结果行数：" + data.size());
        System.out.println("  样本：" + data.get(0));
        System.out.println("  错误：" + (errors.isEmpty() ? "[]" : errors));
        System.out.printf("  统计：商品查询 %s 次，品牌批量查询 %s 次%n",
                stats.get("productQueries"), stats.get("brandBatchQueries"));
        System.out.println();
    }

    public static void main(String[] args) {
        seed();
        System.out.println("【查询一：客服（无 cost 权限）取 name/price/brand】");
        execute(Set.of("product:read"), List.of("name", "price", "brand"), 100);

        System.out.println("【查询二：财务（有 cost 权限）取 cost】");
        execute(Set.of("product:read", "product:read:cost"), List.of("name", "cost"), 100);

        System.out.println("【查询三：复杂度超限】");
        execute(Set.of("product:read"), List.of("name", "price", "brand", "cost"), 10);

        System.out.println("【查询四：非法字段】");
        execute(Set.of("product:read"), List.of("name", "supplier"), 100);

        System.out.println("【验收】schema 一致 / N+1 消除（品牌 1 次批量）/ 复杂度限制 / 字段鉴权 / 错误可定位");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 端到端实现商品 GraphQL 网关（查询/权限/DataLoader/复杂度），用 4 个查询验证各项防护。
 * 2. 加入 Mutation（创建商品 + 幂等键）与"部分成功"响应，验证写路径的规范。
 * 3. 思考题：为什么"字段级鉴权必须在 resolver 内"？从"客户端可构造任意查询"分析（75-09 的呼应）。
 * ============================================================
 */
