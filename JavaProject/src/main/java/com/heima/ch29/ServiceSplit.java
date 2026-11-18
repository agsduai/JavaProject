/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：29-02
 * 课名：服务拆分原则与边界
 * ------------------------------------------------------------
 * 知识点：
 *   - **按业务能力拆（DDD 限界上下文）**：一个服务对应一个业务领域，拥有自己的数据和规则。订单服务管订单，商品服务管商品，不要出现"订单服务直接改商品表"。
 *   - **数据库私有**：每个服务独占自己的库/表，跨服务只能通过 API。共享数据库是微服务最大的反模式——表结构一改，所有服务一起挂。
 *   - 拆分粒度：一个服务应该能被**一个小团队（5~8 人）**完整负责，包含从接口到存储的全部。太细会导致调用链爆炸，太粗失去隔离收益。
 *   - 聚合边界看"一致性要求"：必须强一致的放在同一个服务同一个库（订单 + 订单明细）；可以最终一致的拆开（订单 + 积分）。
 *   - 服务接口要**面向业务**而不是面向表：`POST /orders` 而不是 `insertOrderRow`。接口一旦发布就是契约，改动要考虑兼容。
 *   - 易错：把"代码分层"当成"服务拆分"；两个服务共用一个数据库连接池；拆分后仍然跨库 join。
 * ============================================================
 */

package com.heima.ch29;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class ServiceSplit {
    /** 一个服务：名字 + 拥有的表 + 对外能力 */
    record Service(String name, List<String> tables, List<String> apis) { }

    public static void main(String[] args) {
        List<Service> services = new ArrayList<>();
        services.add(new Service("user-service",
                List.of("t_user", "t_user_address"),
                List.of("GET /users/{id}", "POST /users", "GET /users/{id}/addresses")));
        services.add(new Service("item-service",
                List.of("t_item", "t_item_sku", "t_category"),
                List.of("GET /items/{id}", "GET /items?page=", "POST /items")));
        services.add(new Service("order-service",
                List.of("t_order", "t_order_item", "t_order_status_log"),
                List.of("POST /orders", "GET /orders/{id}", "POST /orders/{id}/cancel")));
        services.add(new Service("points-service",
                List.of("t_points_account", "t_points_log"),
                List.of("POST /points/earn", "GET /points/{userId}")));

        System.out.println("=== 服务边界：每个服务独占自己的表 ===");
        Map<String, String> owner = new HashMap<>();
        boolean conflict = false;
        for (Service s : services) {
            System.out.println("  " + s.name());
            for (String t : s.tables()) {
                if (owner.containsKey(t)) {
                    System.out.println("    ! 表 " + t + " 同时属于 " + owner.get(t) + " 和 " + s.name() + "（共享数据库，反模式）");
                    conflict = true;
                }
                owner.put(t, s.name());
                System.out.println("    " + t);
            }
        }
        System.out.println("  表归属冲突=" + conflict);

        System.out.println();
        System.out.println("=== 对外能力（面向业务，不面向表）===");
        for (Service s : services) {
            for (String api : s.apis()) {
                System.out.println("  " + s.name() + "  " + api);
            }
        }

        System.out.println();
        System.out.println("=== 跨服务调用：只能走 API，不能跨库 join ===");
        Map<String, String> flow = new LinkedHashMap<>();
        flow.put("1. order-service 收到下单请求", "本地事务：写 t_order + t_order_item（强一致，同库）");
        flow.put("2. 调 item-service 扣库存", "HTTP POST /items/{id}/deduct，失败则整单失败");
        flow.put("3. 发 order.created 事件", "points-service 异步加积分（最终一致，可补偿）");
        for (Map.Entry<String, String> e : flow.entrySet()) {
            System.out.println("  " + e.getKey() + " => " + e.getValue());
        }

        System.out.println();
        System.out.println("=== 一致性边界判断 ===");
        System.out.println("  必须强一致（同服务同库）：订单主表 + 订单明细 + 状态流水");
        System.out.println("  可以最终一致（跨服务异步）：订单 -> 积分、订单 -> 物流单、订单 -> 搜索索引");
        System.out.println("  判据：业务能否容忍「几秒内不一致」，能容忍就不要用分布式事务硬扛");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 为"商品 + 库存 + 价格"设计服务边界：库存应该和商品同服务还是独立？说明理由。
 * 2. 找出共享数据库会引发的至少三个具体故障场景。
 * 3. 为什么"订单明细"必须和"订单主表"在同一个库？如果拆开会遇到什么问题？
 * ============================================================
 */
