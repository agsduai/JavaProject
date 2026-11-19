/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：75-10
 * 课名：BFF 与联邦架构
 * ------------------------------------------------------------
 * 知识点：
 *   - **BFF（Backend For Frontend）是 GraphQL 最典型的落点**：**每个前端（App/Web/小程序）一个 BFF**，BFF 用 GraphQL 聚合后端多个微服务的 REST/RPC，**前端只面对一个"按需取数"的端点**。
 *   - **BFF 的价值**：**聚合**（一次请求拿多个服务的数据）、**裁剪**（只返回前端要的字段）、**适配**（不同端不同结构）、**隔离**（后端服务不感知前端差异）——**与 50-11 的聚合网关同源，只是查询形状可声明**。
 *   - **联邦（Federation）解决"多个团队各自维护子图"**：每个团队发布自己的子 schema（Product/Brand/Review），网关**合并成一个超级图**并按字段路由到对应子图——**避免"一个巨型 schema 由一个团队维护"**（75-01 的反模式）。
 *   - **与 29/50 章的关系**：BFF 是"API 组合层"，联邦是"schema 组合层"——**两者都在解决"前端需要的数据散落在多个服务"**（50-11 聚合网关的 GraphQL 版）。
 *   - 易错点：BFF 里写业务逻辑（变成第二个后端）；联邦的实体定义不一致（同一实体在不同子图字段冲突）；BFF 无缓存（每次都穿透下游）；一个 BFF 服务所有端（又变成巨型 schema）。
 * ============================================================
 */

package com.heima.ch75;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class BffFederation {
    /** 子图：团队 -> 它能提供的类型与字段 */
    record Subgraph(String team, String type, List<String> fields) {
    }

    static final List<Subgraph> subgraphs = new ArrayList<>();

    static void seed() {
        subgraphs.add(new Subgraph("商品团队", "Product", List.of("id", "name", "price")));
        subgraphs.add(new Subgraph("品牌团队", "Product", List.of("brand")));
        subgraphs.add(new Subgraph("评价团队", "Product", List.of("reviews")));
        subgraphs.add(new Subgraph("库存团队", "Product", List.of("stock")));
    }

    /** 联邦：合并超级图 + 字段路由表 */
    static Map<String, String> buildRouting() {
        Map<String, String> routing = new LinkedHashMap<>();
        for (Subgraph s : subgraphs) {
            for (String f : s.fields()) {
                String key = s.type() + "." + f;
                if (routing.containsKey(key)) {
                    System.out.printf("  冲突：%s 被 %s 与 %s 同时声明 -> 需联邦评审%n",
                            key, routing.get(key), s.team());
                } else {
                    routing.put(key, s.team());
                }
            }
        }
        return routing;
    }

    /** BFF 聚合：按查询字段分派到对应团队 */
    static Map<String, Object> query(Map<String, String> routing, List<String> fields) {
        Map<String, Object> out = new LinkedHashMap<>();
        int calls = 0;
        for (String f : fields) {
            String team = routing.getOrDefault("Product." + f, "未知");
            if (team.equals("未知")) {
                out.put(f, null);
                System.out.printf("    %-8s -> 无子图提供（返回 null）%n", f);
                continue;
            }
            out.put(f, "[" + team + " 提供] " + f + " 的值");
            calls++;
            System.out.printf("    %-8s -> 路由到 %-8s 调用 %d%n", f, team, calls);
        }
        System.out.printf("  合计 %d 次下游调用（同团队字段可合并为 1 次）%n", calls);
        return out;
    }

    static void bffVsApiGateway() {
        System.out.println("【BFF vs API 网关】");
        String[][] rows = {
                {"职责", "路由/限流/鉴权", "聚合/裁剪/适配"},
                {"取数形状", "固定（按接口）", "客户端声明"},
                {"变更频率", "低", "随前端需求变化"},
                {"典型技术", "REST 网关（64-08）", "GraphQL BFF"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-8s 网关=%-18s BFF=%s%n", r[0], r[1], r[2]);
        }
        System.out.println();
    }

    public static void main(String[] args) {
        seed();
        System.out.println("【子图清单】");
        subgraphs.forEach(s -> System.out.printf("  %-8s %-8s 字段=%s%n",
                s.team(), s.type(), s.fields()));
        System.out.println();

        System.out.println("【联邦合并 + 路由表】");
        Map<String, String> routing = buildRouting();
        routing.forEach((k, v) -> System.out.printf("  %-16s -> %s%n", k, v));
        System.out.println();

        System.out.println("【BFF 按查询字段分派】");
        System.out.println("  查询：product { name price brand reviews }");
        Map<String, Object> data = query(routing, List.of("name", "price", "brand", "reviews"));
        System.out.println("  结果：" + data.keySet());

        System.out.println();
        System.out.println("【不存在的字段】");
        query(routing, List.of("name", "supplier"));

        System.out.println();
        bffVsApiGateway();
        System.out.println("  原则：BFF 只做聚合与适配，不写业务逻辑（业务仍在后端服务）");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现联邦路由表：4 个子图合并，验证字段路由到正确团队、冲突能被检出。
 * 2. 实现 BFF 聚合：一次查询跨 3 个团队，验证"同团队字段合并调用"的优化。
 * 3. 思考题：为什么"BFF 里写业务逻辑"是反模式？从"职责重复"与"两端不一致"分析。
 * ============================================================
 */
