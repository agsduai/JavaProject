/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：27-01
 * 课名：接口设计：URL、方法、状态码
 * ------------------------------------------------------------
 * 知识点：
 *   - RESTful 的核心是"用名词表示资源，用 HTTP 方法表示动作"：`GET /api/items` 查列表、`GET /api/items/1` 查单个、`POST /api/items` 新增、`PUT /api/items/1` 全量更新、`PATCH /api/items/1` 局部更新、`DELETE /api/items/1` 删除。
 *   - URL 里不要出现动词：`/api/getItemList`、`/api/deleteItem?id=1` 都是反例。动作实在表达不了才用子资源，例如 `POST /api/orders/1/cancel`。
 *   - 层级表达归属：订单里的一条明细是 `/api/orders/1/items/3`，不要写成 `/api/orderItem?orderId=1&itemId=3`。
 *   - 状态码要"说到做到"：200 成功、201 创建成功、204 删除成功无返回体、400 参数错、401 未登录、403 无权限、404 不存在、409 冲突（用户名重复）、422 业务校验失败、500 服务端异常。
 *   - 统一响应体：`{code, msg, data}`。HTTP 状态码表达"协议层结果"，`code` 表达"业务层结果"，两者不要混用（全部返回 200 然后靠 code 判断，会让网关、监控、重试策略全部失效）。
 *   - 分页参数统一：`page`（从 1 开始）、`size`（默认 10，上限 100）、`sort`（字段+方向）。排序字段必须走白名单，否则就是 SQL 注入入口。
 *   - 易错：`GET` 请求带请求体（很多网关会丢掉）；`PUT` 当成 `PATCH` 用（前端只传一个字段，结果其它字段被置空）；URL 用驼峰（`/api/itemList`），服务器和 CDN 对大小写敏感容易踩坑。
 * ============================================================
 */

package com.heima.ch27;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class RestApiDesign {
    /** 一条路由：方法 + 路径模板 + 说明 */
    record Route(String method, String pattern, String desc, int okStatus) { }

    static final List<Route> ROUTES = new ArrayList<>();

    public static void main(String[] args) {
        ROUTES.add(new Route("GET", "/api/items", "分页查询商品", 200));
        ROUTES.add(new Route("GET", "/api/items/{id}", "查询商品详情", 200));
        ROUTES.add(new Route("POST", "/api/items", "新增商品", 201));
        ROUTES.add(new Route("PUT", "/api/items/{id}", "全量更新商品", 200));
        ROUTES.add(new Route("PATCH", "/api/items/{id}", "局部更新商品", 200));
        ROUTES.add(new Route("DELETE", "/api/items/{id}", "删除商品", 204));
        ROUTES.add(new Route("POST", "/api/orders", "创建订单", 201));
        ROUTES.add(new Route("POST", "/api/orders/{id}/cancel", "取消订单", 200));
        ROUTES.add(new Route("GET", "/api/orders/{id}/items/{itemId}", "查询订单明细", 200));

        System.out.println("=== 接口清单 ===");
        System.out.printf("%-7s %-34s %-12s %s%n", "方法", "路径", "成功状态码", "说明");
        for (Route r : ROUTES) {
            System.out.printf("%-7s %-34s %-12d %s%n", r.method(), r.pattern(), r.okStatus(), r.desc());
        }

        System.out.println();
        System.out.println("=== 路由匹配（含路径变量）===");
        String[][] requests = {
                {"GET", "/api/items"},
                {"GET", "/api/items/42"},
                {"POST", "/api/items"},
                {"DELETE", "/api/items/42"},
                {"GET", "/api/orders/7/items/3"},
                {"POST", "/api/orders/7/cancel"},
                {"GET", "/api/getItemList"},
                {"PUT", "/api/items/42"},
        };
        for (String[] req : requests) {
            System.out.printf("  %-6s %-30s -> %s%n", req[0], req[1], dispatch(req[0], req[1]));
        }

        System.out.println();
        System.out.println("=== 统一响应体 ===");
        System.out.println("  成功: " + result(200, "ok", Map.of("id", 42, "name", "机械键盘")));
        System.out.println("  参数错: " + result(400, "size 必须在 1~100 之间", null));
        System.out.println("  未登录: " + result(401, "请先登录", null));
        System.out.println("  冲突: " + result(409, "商品名称已存在", null));
        System.out.println("  服务错: " + result(500, "服务器繁忙，请稍后重试", null));
    }

    /** 极简路由：先比方法，再比路径模板 */
    static String dispatch(String method, String path) {
        for (Route r : ROUTES) {
            if (!r.method().equals(method)) {
                continue;
            }
            Map<String, String> vars = match(r.pattern(), path);
            if (vars != null) {
                String extra = vars.isEmpty() ? "" : " 参数=" + vars;
                return r.okStatus() + " " + r.desc() + extra;
            }
        }
        return "404 无此接口（或方法不匹配）: " + method + " " + path;
    }

    /** 把 /api/items/{id} 和 /api/items/42 对上，取出 id=42 */
    static Map<String, String> match(String pattern, String path) {
        String[] p = pattern.split("/");
        String[] a = path.split("/");
        if (p.length != a.length) {
            return null;
        }
        Map<String, String> vars = new LinkedHashMap<>();
        for (int i = 0; i < p.length; i++) {
            if (p[i].startsWith("{") && p[i].endsWith("}")) {
                if (a[i].isEmpty()) {
                    return null;
                }
                vars.put(p[i].substring(1, p[i].length() - 1), a[i]);
            } else if (!p[i].equals(a[i])) {
                return null;
            }
        }
        return vars;
    }

    static Map<String, Object> result(int code, String msg, Object data) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("msg", msg);
        body.put("data", data);
        return body;
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 给"商品上下架"设计接口：说出 URL、方法、成功状态码，以及为什么它不适合用 `PUT /api/items/{id}` 表达。
 * 2. 为什么"全部返回 HTTP 200，靠 body 里的 code 判断"是坏设计？从网关限流、监控告警、客户端重试三个角度说明。
 * 3. `PATCH` 和 `PUT` 的区别是什么？如果前端用 `PUT` 只传了 `name` 字段，服务端直接整体覆盖会发生什么？
 * ============================================================
 */
