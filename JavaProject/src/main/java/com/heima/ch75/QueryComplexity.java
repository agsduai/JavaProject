/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：75-08
 * 课名：深度与复杂度限制
 * ------------------------------------------------------------
 * 知识点：
 *   - **GraphQL 的独特攻击面：查询形状由客户端决定**——一个深层嵌套查询（`product { brand { products { brand ... } } }`）能让服务端**指数级放大**查询量，**比 REST 的"固定接口"危险得多**。
 *   - **三种限制**：**深度限制**（maxDepth，如 10 层）、**复杂度限制**（每个字段权重 × 数量求和，如 1000 分）、**时间限制**（执行超时）——**三者组合**，缺一个都能被绕。
 *   - **复杂度计算**：给字段配权重（标量 1、列表 10、关联 20），查询时乘上 `first` 参数的倍数——**`products(first: 1000) { reviews(first: 1000) }` = 100 万条**，必须按"参数 × 子字段"累计。
 *   - **与 69-04 的关系**：广告预算是"钱"的限制，GraphQL 复杂度是"算力"的限制——**同一种"防止单请求拖垮系统"的思想**（44-08 限流的查询版）。
 *   - **持久化查询（Persisted Query）是更彻底的方案**：客户端只发查询 ID（服务端存好查询），**无法构造任意查询**——适合自有客户端（App/Web），开放平台仍需复杂度限制。
 *   - 易错点：只限深度不限复杂度（宽查询绕过）；复杂度不乘列表参数（`first: 100000`）；不做超时（慢查询拖垮）；生产开内省（75-09 泄露 schema）。
 * ============================================================
 */

package com.heima.ch75;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class QueryComplexity {
    /** 字段权重：标量 1、列表 10、关联 20 */
    static final Map<String, Integer> fieldWeight = new LinkedHashMap<>();

    static {
        fieldWeight.put("id", 1);
        fieldWeight.put("name", 1);
        fieldWeight.put("price", 1);
        fieldWeight.put("products", 10);
        fieldWeight.put("brand", 20);
        fieldWeight.put("reviews", 20);
    }

    record FieldNode(String name, Integer first, List<FieldNode> children) {
    }

    /** 计算复杂度：字段权重 × 列表倍数 的递归求和 */
    static int complexity(FieldNode node) {
        int base = fieldWeight.getOrDefault(node.name(), 5);
        int multiplier = node.first() == null ? 1 : Math.max(1, node.first());
        int self = base * multiplier;
        int children = 0;
        for (FieldNode c : node.children()) {
            children += complexity(c);
        }
        return self + children * multiplier;
    }

    static int depth(FieldNode node) {
        int max = 0;
        for (FieldNode c : node.children()) {
            max = Math.max(max, depth(c));
        }
        return 1 + max;
    }

    static final int MAX_DEPTH = 8;
    static final int MAX_COMPLEXITY = 5000;

    static String validate(FieldNode q) {
        int d = depth(q);
        int c = complexity(q);
        System.out.printf("  查询 %-40s 深度=%-2d 复杂度=%-6d -> ", q.name() + "…", d, c);
        if (d > MAX_DEPTH) {
            return "拒绝：深度超限（" + d + " > " + MAX_DEPTH + "）";
        }
        if (c > MAX_COMPLEXITY) {
            return "拒绝：复杂度超限（" + c + " > " + MAX_COMPLEXITY + "）";
        }
        return "通过";
    }

    public static void main(String[] args) {
        System.out.println("【正常查询】");
        FieldNode normal = new FieldNode("products", 10, List.of(
                new FieldNode("name", null, List.of()),
                new FieldNode("price", null, List.of()),
                new FieldNode("brand", null, List.of(
                        new FieldNode("name", null, List.of())))));
        System.out.println("  " + validate(normal));

        System.out.println();
        System.out.println("【宽查询：first 放大】");
        FieldNode wide = new FieldNode("products", 1000, List.of(
                new FieldNode("name", null, List.of()),
                new FieldNode("reviews", 1000, List.of(
                        new FieldNode("name", null, List.of())))));
        System.out.println("  " + validate(wide));

        System.out.println();
        System.out.println("【深查询：嵌套】");
        FieldNode deep = new FieldNode("products", 10, List.of(
                new FieldNode("brand", null, List.of(
                        new FieldNode("products", 10, List.of(
                                new FieldNode("brand", null, List.of(
                                        new FieldNode("products", 10, List.of(
                                                new FieldNode("brand", null,
                                                        List.of())))))))))));
        System.out.println("  " + validate(deep));

        System.out.println();
        System.out.println("【复杂度模型说明】");
        System.out.println("  complexity(node) = weight(node) × first(node)");
        System.out.println("                   + Σ complexity(child) × first(node)");
        System.out.println("  关键：列表参数会放大子字段的权重（嵌套列表是乘数）");

        System.out.println();
        System.out.println("【三道防线】");
        Map<String, String> lines = new LinkedHashMap<>();
        lines.put("深度限制", "maxDepth 8~10，防深层嵌套");
        lines.put("复杂度限制", "权重 × 参数 累计，超阈值拒绝（建议 1000~5000）");
        lines.put("执行超时", "单请求 ≤ 3s，超时终止");
        lines.forEach((k, v) -> System.out.printf("  %-8s %s%n", k, v));
        System.out.println();
        System.out.println("  更彻底：持久化查询（客户端只发查询 ID，75-09）");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现复杂度计算器（权重 × first 递归求和），用"正常/宽/深"三个查询验证拦截。
 * 2. 加入"列表参数放大"测试：`products(first: 100000)` 单层是否被拒，说明为何只看深度不够。
 * 3. 思考题：为什么"持久化查询"比"复杂度限制"更安全？从"无法构造任意查询"分析。
 * ============================================================
 */
