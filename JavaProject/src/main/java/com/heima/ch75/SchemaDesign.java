/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：75-02
 * 课名：Schema 与类型系统
 * ------------------------------------------------------------
 * 知识点：
 *   - **Schema 是 GraphQL 的契约**：**对象类型**（`type Product { id: ID! name: String! }`）、**标量**（ID/String/Int/Float/Boolean，可自定义 Date 等）、**枚举**、**接口与联合**（多态）、**输入类型**（Mutation 参数）——**强类型是 GraphQL 的核心优势**（客户端可静态校验）。
 *   - **`!` 表示非空**：`name: String!` 表示"一定返回字符串"，`String` 表示"可能为 null"——**非空字段的 resolver 不能返回 null**（否则整棵树报错 75-07）。
 *   - **Schema 即文档**：内省查询（`__schema`）能导出完整类型定义（`75-09` 的安全注意）；**代码生成**（客户端从 schema 生成类型安全的请求代码）是 GraphQL 生态最大价值之一（64-07 的 SDK 生成思路）。
 *   - **与 49 章建模的关系**：GraphQL schema 是"API 层的类型建模"（面向前端需求），领域模型是"业务层的建模"——**schema 可以组合多个领域模型**（BFF 的价值）。
 *   - 易错点：全字段非空（一有 null 就整树报错）；把数据库表直接映射成 schema（暴露内部结构）；不定义输入类型（Mutation 参数散落）；schema 与实现不同步（无生成/校验）。
 * ============================================================
 */

package com.heima.ch75;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class SchemaDesign {
    /** 最小 schema 模型：类型 -> 字段(名, 类型, 非空) */
    record FieldDef(String name, String type, boolean nonNull) {
        String toSdl() {
            return name + ": " + type + (nonNull ? "!" : "");
        }
    }

    record TypeDef(String name, String kind, List<FieldDef> fields) {
        String toSdl() {
            StringBuilder sb = new StringBuilder(kind + " " + name + " {\n");
            fields.forEach(f -> sb.append("  ").append(f.toSdl()).append("\n"));
            return sb.append("}").toString();
        }
    }

    static void schema() {
        List<TypeDef> types = List.of(
                new TypeDef("Product", "type", List.of(
                        new FieldDef("id", "ID", true),
                        new FieldDef("name", "String", true),
                        new FieldDef("price", "Float", true),
                        new FieldDef("brand", "Brand", false),
                        new FieldDef("stock", "Int", false),
                        new FieldDef("status", "ProductStatus", true),
                        new FieldDef("reviews", "[Review!]", true))),
                new TypeDef("Brand", "type", List.of(
                        new FieldDef("id", "ID", true),
                        new FieldDef("name", "String", true))),
                new TypeDef("Review", "type", List.of(
                        new FieldDef("id", "ID", true),
                        new FieldDef("score", "Int", true),
                        new FieldDef("content", "String", false))),
                new TypeDef("ProductInput", "input", List.of(
                        new FieldDef("name", "String", true),
                        new FieldDef("price", "Float", true))));
        types.forEach(t -> {
            System.out.println("  " + t.toSdl().replace("\n", "\n  "));
            System.out.println();
        });
        System.out.println("【枚举】");
        System.out.println("  enum ProductStatus { ONSALE OFFSALE SOLD_OUT }");
        System.out.println();
        System.out.println("【Query 与 Mutation 根类型】");
        System.out.println("  type Query { product(id: ID!): Product products(first: Int): [Product!]! }");
        System.out.println("  type Mutation { createProduct(input: ProductInput!): Product! }");
        System.out.println();
    }

    static void nullability() {
        System.out.println("【非空 vs 可空】");
        System.out.println("  name: String!  -> resolver 返回 null 会向上冒泡报错（整棵树 null）");
        System.out.println("  brand: Brand   -> 返回 null 合法（客户端要处理）");
        System.out.println("  原则：确实必有的用 !，其余留可空（否则一个 null 毁掉整个响应）");
        System.out.println();
        System.out.println("【反模式】把数据库表字段一一映射：暴露 created_by/version 等内部字段");
    }

    public static void main(String[] args) {
        schema();
        nullability();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 为"商品 + 品牌 + 评论"设计 GraphQL schema（含枚举、输入类型、Query/Mutation 根），用 SDL 写出。
 * 2. 判断 10 个字段该不该加 `!`，并说明"错加非空"的后果（整树报错）。
 * 3. 思考题：为什么"schema 直接映射数据库表"是反模式？从"暴露内部结构"与"前端需求变化"分析。
 * ============================================================
 */
