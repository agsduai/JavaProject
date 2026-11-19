/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：75-05
 * 课名：N+1 与 DataLoader
 * ------------------------------------------------------------
 * 知识点：
 *   - **N+1 是 GraphQL 的头号性能杀手**：查 10 个商品（1 次）-> 每个商品取 brand（10 次）-> **共 11 次查询**；嵌套两层则 N×M——**REST 手写接口可以一次 join，GraphQL 的字段级取数天然分散**。
 *   - **DataLoader 的解法：批量化 + 缓存**——把**同一事件循环内的多次单个请求合并成一次批量请求**（`load(id)` 收集 -> `loadMany(ids)` 一次查库），**请求级缓存**避免同请求内重复加载。
 *   - **DataLoader 的两个保证**：**批量**（同一 tick 的请求合并）与**缓存**（同 key 只查一次）——**实现关键**：延迟到下一微任务统一执行（`queueMicrotask`/`CompletableFuture`）。
 *   - **批量化要求"取数接口支持批量"**：`findByIds(List<Long>)` 而不是 `findById(Long)`——**下游服务要提供批量接口**（没有就自己 `IN` 查询）。
 *   - 与 54/46 章的关系：与推荐的"批量特征加载"、大数据的"列裁剪"同源——**减少往返是通用优化**（39-02 的批量化思想）。
 *   - 易错点：不用 DataLoader（N+1）；DataLoader 的缓存跨请求复用（数据陈旧）；批量接口无上限（一次 IN 10 万）；嵌套 DataLoader 未分页（75-08 的复杂度）。
 * ============================================================
 */

package com.heima.ch75;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// ==================== 示例代码 ====================

public class DataLoaderDemo {
    record Product(long id, String name, long brandId) {
    }

    record Brand(long id, String name) {
    }

    static final List<Product> products = List.of(
            new Product(1, "手机壳", 10),
            new Product(2, "耳机", 10),
            new Product(3, "充电器", 20),
            new Product(4, "数据线", 20),
            new Product(5, "支架", 30));

    static final Map<Long, Brand> brands = new LinkedHashMap<>();

    static {
        brands.put(10L, new Brand(10, "自有品牌"));
        brands.put(20L, new Brand(20, "第三方"));
        brands.put(30L, new Brand(30, "配件厂"));
    }

    /** 模拟 DB：按 id 批量查（DataLoader 需要的下游能力） */
    static Map<Long, Brand> findBrandsByIds(Set<Long> ids) {
        System.out.printf("    [DB] 批量查询品牌 %s%n", ids);
        Map<Long, Brand> out = new LinkedHashMap<>();
        for (Long id : ids) {
            Brand b = brands.get(id);
            if (b != null) {
                out.put(id, b);
            }
        }
        return out;
    }

    /** DataLoader：收集 -> 批量 -> 分配 */
    static class BrandLoader {
        final Set<Long> queue = new LinkedHashSet<>();
        final Map<Long, Brand> cache = new LinkedHashMap<>();
        boolean scheduled = false;

        /** 单个加载：先入队，返回延迟结果（这里用同步 flush 简化演示合并效果） */
        Brand load(long brandId) {
            if (cache.containsKey(brandId)) {
                System.out.printf("    [cache] 命中品牌 %d%n", brandId);
                return cache.get(brandId);
            }
            queue.add(brandId);
            if (!scheduled) {
                scheduled = true;
            }
            return null;                     // 真实实现返回 CompletableFuture
        }

        /** 在事件循环末尾调用：一次批量取回所有入队的 key */
        void dispatch() {
            if (queue.isEmpty()) {
                return;
            }
            Map<Long, Brand> loaded = findBrandsByIds(queue);
            cache.putAll(loaded);
            queue.clear();
            scheduled = false;
        }
    }

    /** 不用 DataLoader：每个 product 单独查 */
    static void withoutLoader() {
        System.out.println("【不用 DataLoader（N+1）】");
        int queries = 1;                     // 查商品列表
        for (Product p : products) {
            System.out.printf("    [DB] 查品牌 %d（商品 %s）%n", p.brandId(), p.name());
            queries++;
        }
        System.out.printf("  查询次数：%d（1 + %d）%n", queries, products.size());
        System.out.println();
    }

    /** 用 DataLoader：先全部 load（入队），再 dispatch 一次 */
    static void withLoader() {
        System.out.println("【用 DataLoader（批量 + 缓存）】");
        BrandLoader loader = new BrandLoader();
        int queries = 1;                     // 查商品列表
        for (Product p : products) {
            loader.load(p.brandId());
        }
        System.out.println("  阶段一：收集完 5 个 brandId（尚未查库）");
        loader.dispatch();
        queries++;
        System.out.println("  阶段二：一次批量查回所有品牌");
        for (Product p : products) {
            Brand b = loader.load(p.brandId());       // 全部命中缓存
            System.out.printf("    商品 %-6s -> 品牌 %s%n", p.name(), b.name());
        }
        System.out.printf("  查询次数：%d（1 + 1）%n", queries);
        System.out.println();
    }

    public static void main(String[] args) {
        withoutLoader();
        withLoader();

        System.out.println("【要点】");
        System.out.println("  1) DataLoader = 批量化 + 请求级缓存");
        System.out.println("  2) 下游需提供批量接口（findByIds / IN 查询）");
        System.out.println("  3) 缓存只在单次请求内有效（请求结束销毁，避免脏数据）");
        System.out.println("  4) 批量要有上限（IN 10 万会打挂 DB，需分批）");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现 DataLoader：收集/批量/缓存，用"5 个商品取 brand"验证查询次数从 6 降到 2。
 * 2. 扩展为两层嵌套（product -> brand -> parentBrand），观察 N×M 并说明 DataLoader 如何逐层批量化。
 * 3. 思考题：为什么"DataLoader 缓存不能跨请求"？从"数据更新后读到旧值"分析。
 * ============================================================
 */
