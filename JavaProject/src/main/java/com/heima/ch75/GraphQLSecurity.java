/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：75-09
 * 课名：安全：内省、批量与注入
 * ------------------------------------------------------------
 * 知识点：
 *   - **内省（Introspection）泄露 schema**：`__schema` 能导出全部类型与字段——**生产环境通常关闭**（或仅内部可用），但**关闭后客户端代码生成要离线做**（75-02）。
 *   - **批量攻击（Batching）**：把 1000 个查询塞进一个请求（`[{query},{query}...]`）——**绕过单请求限流**；**对策**：限制批量数量（如 ≤10）+ 按 operation 数计复杂度。
 *   - **注入风险**：**用户输入拼进查询字符串**（`product(name: "` + input + `")`）——与 SQL 注入同源（36-02）；**用变量（variables）传值**（75-03）。
 *   - **敏感字段与权限**：GraphQL 的字段级权限要**在 resolver 里校验**（74-04 统一入口），不能只靠"客户端不查就行"——**攻击者可以构造任意查询**。
 *   - **与 36 章的关系**：GraphQL 的注入/越权/DoS 与 REST 同源，**只是攻击面变成了"查询形状"**——安全原则不变（输入校验、最小权限、限流）。
 *   - 易错点：生产开内省（schema 泄露，攻击者按图索骥）；不限制批量（限流被绕过）；字段无权限校验（构造查询读敏感字段）；错误信息暴露 schema 细节。
 * ============================================================
 */

package com.heima.ch75;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

// ==================== 示例代码 ====================

public class GraphQLSecurity {
    /** 内省开关 */
    static boolean introspectionEnabled = false;

    static String handleIntrospection(String query) {
        if (query.contains("__schema") || query.contains("__type")) {
            if (!introspectionEnabled) {
                return "拒绝：内省已关闭（生产环境）";
            }
            return "返回 schema 定义（仅开发环境）";
        }
        return "非内省查询";
    }

    /** 批量限制 */
    static final int MAX_BATCH = 10;

    static String handleBatch(List<String> queries) {
        if (queries.size() > MAX_BATCH) {
            return "拒绝：批量过大（" + queries.size() + " > " + MAX_BATCH + "）";
        }
        int totalComplexity = queries.size() * 100;          // 简化：每查询 100
        return "通过：批量 " + queries.size() + " 个，总复杂度 " + totalComplexity;
    }

    /** 注入防护：变量 vs 字符串拼接 */
    static String buildQuery(String userInput, boolean useVariable) {
        if (useVariable) {
            return "query($name: String) { products(name: $name) { id } }  variables={name:\""
                    + userInput + "\"}";
        }
        return "query { products(name: \"" + userInput + "\") { id } }";
    }

    static void injectionDemo() {
        System.out.println("【注入对比】");
        String evil = "\") { id } } query { secret { token } } query { x: products(name: \"";
        System.out.println("  恶意输入：" + evil);
        System.out.println("  拼字符串 -> " + buildQuery(evil, false));
        System.out.println("    查询被截断并注入新字段（数据泄露）");
        System.out.println("  用变量  -> " + buildQuery(evil, true));
        System.out.println("    恶意内容只是 name 的字符串值（安全）");
        System.out.println();
    }

    /** 字段级权限：resolver 内校验 */
    record UserAccount(String id, Set<String> perms) {
    }

    static String resolveField(UserAccount user, String field) {
        String required = switch (field) {
            case "price" -> "product:read:price";
            case "cost" -> "product:read:cost";
            default -> "product:read";
        };
        if (!user.perms().contains(required)) {
            return "字段 " + field + " -> 403（缺 " + required + "）";
        }
        return "字段 " + field + " -> 返回数据";
    }

    public static void main(String[] args) {
        System.out.println("【内省】");
        System.out.println("  开发环境：" + handleIntrospection("{__schema{types{name}}}"));
        introspectionEnabled = true;
        System.out.println("  开发环境（已开）：" + handleIntrospection("{__schema{types{name}}}"));
        introspectionEnabled = false;
        System.out.println("  生产环境：" + handleIntrospection("{__schema{types{name}}}"));

        System.out.println();
        System.out.println("【批量限制】");
        System.out.println("  " + handleBatch(List.of("q1", "q2", "q3")));
        System.out.println("  " + handleBatch(fill(20)));

        System.out.println();
        injectionDemo();

        System.out.println("【字段级权限】");
        UserAccount staff = new UserAccount("u-1", Set.of("product:read"));
        UserAccount manager = new UserAccount("u-2",
                Set.of("product:read", "product:read:price", "product:read:cost"));
        for (String f : new String[]{"name", "price", "cost"}) {
            System.out.println("  staff   " + resolveField(staff, f));
        }
        for (String f : new String[]{"price", "cost"}) {
            System.out.println("  manager " + resolveField(manager, f));
        }

        System.out.println();
        System.out.println("【安全清单】关闭内省 / 限制批量 / 用变量 / 字段级鉴权 / 错误不暴露结构");
    }

    static List<String> fill(int n) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            out.add("q" + i);
        }
        return out;
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现三项防护：内省开关/批量限制/变量传值，用 4 个场景验证拦截效果。
 * 2. 实现字段级权限校验（resolver 内），用"缺 price 权限的用户构造含 price 的查询"验证 403。
 * 3. 思考题：为什么"客户端不查敏感字段"不能当安全边界？从"攻击者可以构造任意查询"分析。
 * ============================================================
 */
