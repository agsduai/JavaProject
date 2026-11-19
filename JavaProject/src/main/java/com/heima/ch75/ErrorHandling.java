/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：75-07
 * 课名：错误处理与部分成功
 * ------------------------------------------------------------
 * 知识点：
 *   - **GraphQL 的错误是"字段级"的**：响应包含 `data`（部分成功的数据）+ `errors`（失败字段的错误列表）——**一个字段失败不影响其他字段**，这是与 REST"整体成功/失败"最大的差异。
 *   - **错误对象的结构**：`message`（人读）、`path`（出错字段路径）、`locations`（查询位置）、`extensions.code`（机器码 64-09）——**客户端按 path 定位、按 code 分支**。
 *   - **两种错误来源**：**字段解析失败**（resolver 抛错，字段置 null 并进 errors）与**请求级错误**（语法错误/校验失败，无 data 只有 errors）——**要区分处理**。
 *   - **非空字段错误的传播**：`name: String!` 的 resolver 返回 null -> **错误向上冒泡，最近的"可空祖先"被置 null**——**这就是"一个 null 毁掉整棵树"的机制**（75-02 的非空纪律）。
 *   - 与 64-09 的关系：错误码体系（`extensions.code`）与 REST 的 `code` 同构——**GraphQL 只是把错误放进了 `errors` 数组**。
 *   - 易错点：所有错误都抛 500（丢失字段级信息）；不设 `extensions.code`（客户端无法程序化处理）；非空字段返回 null（整树变 null）；把内部堆栈塞进 message（36 章泄露）。
 * ============================================================
 */

package com.heima.ch75;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class ErrorHandling {
    record GraphQLError(String message, List<String> path, String code,
                        Map<String, Object> extensions) {
        Map<String, Object> toJson() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("message", message);
            m.put("path", path);
            m.put("extensions", Map.of("code", code));
            return m;
        }
    }

    record Response(Map<String, Object> data, List<GraphQLError> errors) {
        String describe() {
            return "data=" + data + " errors=" + (errors.isEmpty() ? "[]"
                    : errors.stream().map(GraphQLError::code).toList());
        }
    }

    static void partialSuccess() {
        System.out.println("【部分成功：一个字段失败，其余正常】");
        Map<String, Object> data = new LinkedHashMap<>();
        List<GraphQLError> errors = new ArrayList<>();
        // product.name 成功
        data.put("name", "手机壳");
        // product.price 成功
        data.put("price", 69.0);
        // product.brand 失败（下游超时）
        data.put("brand", null);
        errors.add(new GraphQLError("品牌服务超时", List.of("product", "brand"),
                "DOWNSTREAM_TIMEOUT", Map.of("service", "brand")));
        // product.reviews 成功
        data.put("reviews", List.of(Map.of("score", 5)));
        Response r = new Response(data, errors);
        System.out.println("  " + r.describe());
        System.out.println("  客户端：能展示的展示，brand 位置显示占位符");
        errors.forEach(e -> System.out.println("    错误 " + e.toJson()));
        System.out.println();
    }

    static void nonNullBubbling() {
        System.out.println("【非空字段错误的冒泡】");
        System.out.println("  schema: product { name: String!  brand: Brand }");
        System.out.println("  若 name（非空）的 resolver 返回 null：");
        System.out.println("    name 报错 -> 冒泡到最近的“可空祖先” product -> product 变 null");
        System.out.println("    -> 整棵树 data.product = null（其他顶层字段不受影响）");
        System.out.println();
        System.out.println("  若 brand（可空）返回 null：只断在该字段，product 仍完整");
        System.out.println("  教训：非空要克制（75-02）");
        System.out.println();
    }

    static void requestLevelError() {
        System.out.println("【请求级错误（无 data）】");
        Response r = new Response(null, List.of(
                new GraphQLError("字段 pricee 不存在", List.of(), "GRAPHQL_VALIDATION_FAILED",
                        Map.of("suggestion", "price"))));
        System.out.println("  " + r.describe());
        System.out.println("  这类错误在解析/校验阶段就发生，不执行任何 resolver");
        System.out.println();
    }

    static void codeTable() {
        System.out.println("【错误码约定（与 64-09 对齐）】");
        String[][] rows = {
                {"UNAUTHENTICATED", "401", "未登录/令牌无效"},
                {"FORBIDDEN", "403", "已登录但无权限（74 章）"},
                {"NOT_FOUND", "404", "资源不存在或越权（统一 404）"},
                {"VALIDATION_FAILED", "400", "参数校验失败"},
                {"DOWNSTREAM_TIMEOUT", "504", "下游超时（可重试）"},
                {"INTERNAL", "500", "未预期错误（不暴露堆栈）"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-20s HTTP %-4s %s%n", r[0], r[1], r[2]);
        }
        System.out.println();
        System.out.println("  规范：extensions.code 稳定（客户端分支），message 给人看，不泄露内部信息");
    }

    public static void main(String[] args) {
        partialSuccess();
        nonNullBubbling();
        requestLevelError();
        codeTable();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现"部分成功"响应：3 个字段中 1 个失败，输出 data + errors，验证客户端可定位。
 * 2. 演示非空字段冒泡：`name: String!` 返回 null 时 product 变 null，对比可空字段的行为。
 * 3. 思考题：为什么 GraphQL 的"部分成功"对前端更友好但更难监控？从"错误分散"与"成功率统计"分析。
 * ============================================================
 */
