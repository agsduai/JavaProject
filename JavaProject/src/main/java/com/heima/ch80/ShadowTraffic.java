/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：80-08
 * 课名：全链路压测
 * ------------------------------------------------------------
 * 知识点：
 *   - **全链路压测的目标**：**在生产（或生产同构）环境验证"全链路容量"**，**包括网关/服务/DB/缓存/MQ/第三方**——**单接口压测发现不了链路瓶颈**。
 *   - **流量染色**：**压测请求带特殊标记**（header `x-shadow: true`），**一路透传到下游**，**各组件识别标记走影子资源**——**不染色会污染生产数据**。
 *   - **影子表/影子库**：**压测写操作落到影子表**（同结构、不同表名/库），**读也走影子**；**压测结束清理**——**只读压测可不建影子，写压测必须**。
 *   - **隔离与熔断**：**压测流量要能一键停止**、**下游有压测限流**、**禁止压测触发真实短信/支付**——**安全是压测的前提**。
 *   - 与 41/56 章的关系：41 章发布、56 章容量，**本章是"容量验证的实战"**——**压测是演练，不是上线**。
 *   - 易错点：不染色（脏数据）；压测写真实表（资损）；不设停止开关（失控）；只压核心接口（漏链路）；压测数据不清理（垃圾数据）。
 * ============================================================
 */

package com.heima.ch80;

import java.util.LinkedHashMap;
import java.util.Map;

// ==================== 示例代码 ====================

public class ShadowTraffic {
    record Request(String path, String user, boolean shadow, Map<String, String> headers) {
    }

    record Response(int code, String body, String dataSource) {
    }

    static final Map<String, String> prodDb = new LinkedHashMap<>();
    static final Map<String, String> shadowDb = new LinkedHashMap<>();

    static Response handle(Request req) {
        // 1) 识别染色
        boolean shadow = req.shadow() || "true".equals(req.headers().get("x-shadow"));
        // 2) 路由到影子资源
        Map<String, String> db = shadow ? shadowDb : prodDb;
        String source = shadow ? "shadow-db" : "prod-db";

        // 3) 写操作（影子不污染生产）
        if (req.path().startsWith("/order/create")) {
            db.put(req.user(), "order-" + req.user());
            return new Response(200, "created in " + source, source);
        }

        // 4) 读操作
        String v = db.getOrDefault(req.user(), "none");
        return new Response(200, "read " + v + " from " + source, source);
    }

    public static void main(String[] args) {
        System.out.println("【正常流量】");
        Response r1 = handle(new Request("/order/create", "alice", false, Map.of()));
        System.out.println("  " + r1);
        System.out.println("  生产库：" + prodDb);

        System.out.println();
        System.out.println("【压测流量（header 染色）】");
        Response r2 = handle(new Request("/order/create", "alice", false,
                Map.of("x-shadow", "true")));
        System.out.println("  " + r2);
        System.out.println("  影子库：" + shadowDb);
        System.out.println("  生产库：" + prodDb + "（未被污染）");

        System.out.println();
        System.out.println("【压测读】");
        System.out.println("  " + handle(new Request("/order/query", "alice", true, Map.of())));

        System.out.println();
        System.out.println("【隔离检查】");
        String[][] checks = {
                {"流量染色", "x-shadow 透传全链路", "否则污染生产"},
                {"影子表", "写走影子库/表", "结构同、数据隔离"},
                {"停止开关", "一键停压测", "防止失控"},
                {"下游保护", "压测流量限流/熔断", "不打挂第三方"},
                {"数据清理", "压测后清影子数据", "避免垃圾"},
        };
        for (String[] c : checks) {
            System.out.printf("  %-10s %-26s %s%n", c[0], c[1], c[2]);
        }

        System.out.println();
        System.out.println("【结论】压测的第一原则是「不伤害生产」：染色 + 影子 + 开关 + 清理");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现请求染色与影子库路由，验证压测写不污染生产数据。
 * 2. 给压测加"一键停止"与"下游限流"，说明失控时的处置流程。
 * 3. 思考题：为什么"只压核心接口"不够？从"链路瓶颈可能在非核心依赖"分析。
 * ============================================================
 */
