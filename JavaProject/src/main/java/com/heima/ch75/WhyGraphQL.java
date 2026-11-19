/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：75-01
 * 课名：为什么需要 GraphQL
 * ------------------------------------------------------------
 * 知识点：
 *   - **REST 的两个痛点**：**过度获取**（列表页只要 id+name，接口却返回 30 个字段）与**获取不足**（一个页面要调 5 个接口再前端拼装）——**GraphQL 让客户端声明"我要哪些字段"，一次请求拿全**。
 *   - **GraphQL 的核心三个概念**：**Schema**（服务端定义的类型系统，强类型）、**Query**（客户端声明要什么，形状即结果）、**Resolver**（每个字段怎么取数）——**"查询的形状 = 响应的形状"**是它最直观的价值。
 *   - **与 REST 的关系不是替代**：**REST 适合"资源固定、缓存友好"的公开 API**（64 章）；**GraphQL 适合"多端需求差异大、字段组合多"的 BFF 层**（75-10）——**很多公司两者并存**：对外 REST，对内 GraphQL。
 *   - **GraphQL 的代价**：**缓存难**（单端点 POST，HTTP 缓存失效，需要持久化查询/字段级缓存）、**N+1 风险**（75-05）、**复杂度攻击**（深层嵌套查询，75-08）——**它不是免费的午餐**。
 *   - 与 64 章的关系：64 章讲"REST 开放平台怎么治理"，本章讲"另一种 API 形态的治理"——**治理问题同构**（鉴权/限流/版本/错误码）。
 *   - 易错点：为了"技术先进"上 GraphQL（团队与场景不匹配）；不做复杂度限制（被一个查询打挂）；把所有业务塞进一个巨型 schema（变成分布式单体）；忽略 N+1（性能比 REST 还差）。
 * ============================================================
 */

package com.heima.ch75;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class WhyGraphQL {
    record Product(long id, String name, double price, String brand, String desc,
                   int stock, String category, List<String> images) {
    }

    static void restProblem() {
        Product p = new Product(1, "手机壳", 69.0, "自有", "液态硅胶...", 500,
                "配件", List.of("a.jpg", "b.jpg"));
        System.out.println("【REST 的两个问题】");
        System.out.println("  列表页只要 id+name，REST 返回全部 8 个字段：");
        System.out.println("    过度获取：响应 " + estimateBytes(p) + " 字节，实际需要约 40 字节");
        System.out.println("  详情页要商品+品牌+库存+图片，REST 可能要调 3 个接口：");
        System.out.println("    GET /products/1 + GET /brands/自有 + GET /stocks/1");
        System.out.println("    获取不足：前端要拼装，且三次往返");
        System.out.println();
    }

    static int estimateBytes(Product p) {
        return (p.name() + p.brand() + p.desc() + p.category()
                + p.images().toString()).length() + 60;
    }

    static void graphqlIdea() {
        System.out.println("【GraphQL：客户端声明形状】");
        String query = """
                {
                  product(id: 1) {
                    name
                    price
                  }
                }""";
        System.out.println("  查询：");
        query.lines().forEach(l -> System.out.println("    " + l));
        System.out.println("  响应（形状与查询一致）：");
        System.out.println("    { \"data\": { \"product\": { \"name\": \"手机壳\", \"price\": 69.0 } } }");
        System.out.println("  只取两个字段 -> 一次请求、无过度获取");

        System.out.println();
        System.out.println("  详情页一次拿全：");
        String detail = "{ product(id: 1) { name price brand stock images } }";
        System.out.println("    " + detail);
        System.out.println("    -> 一个请求拿到四个字段（无需 3 次往返）");
        System.out.println();
    }

    static void tradeoffs() {
        System.out.println("【GraphQL 的代价】");
        String[][] rows = {
                {"HTTP 缓存", "URL 即资源，CDN 可缓存", "单端点 POST，HTTP 缓存失效"},
                {"N+1", "接口内自行控制", "字段级取数易 N+1（75-05）"},
                {"复杂度攻击", "接口固定，风险可控", "深嵌套查询可打挂（75-08）"},
                {"可观测", "按 URL 统计", "按 operation name 统计"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-12s REST=%-22s GraphQL=%s%n", r[0], r[1], r[2]);
        }
        System.out.println();
        System.out.println("  结论：对外 REST（缓存友好），对内 BFF 用 GraphQL（聚合灵活）");
    }

    public static void main(String[] args) {
        restProblem();
        graphqlIdea();
        tradeoffs();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 找一个你项目的详情页，列出"REST 需要几次请求 / GraphQL 一次能不能拿全"，量化往返次数。
 * 2. 对比 REST 与 GraphQL 在"缓存/版本/可观测"三个方面的治理差异，写出选型建议。
 * 3. 思考题：为什么"把所有业务塞进一个 GraphQL schema"会变成分布式单体？从"耦合与发布"分析。
 * ============================================================
 */
