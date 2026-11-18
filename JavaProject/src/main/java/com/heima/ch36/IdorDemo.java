/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：36-05
 * 课名：越权访问与 IDOR
 * ------------------------------------------------------------
 * 知识点：
 *   - **越权分两种**：**水平越权**（同一角色之间互相看数据，A 用户看 B 用户的订单）和**垂直越权**（低权限干高权限的事，普通用户调管理员接口）。前者更常见，后者危害更大。
 *   - **IDOR（Insecure Direct Object Reference）** 是水平越权的典型形态：接口直接拿前端传的 `id` 去查，不校验「这条数据是不是你的」。`GET /api/order/1001` 改成 `1002` 就能看别人的单。
 *   - **根本原因不是「id 可枚举」，而是「授权缺失」**：就算 id 换成 UUID 不可猜，只要别人把链接分享出去、或者从日志/Referer 里拿到，依然越权。UUID 只是「提高门槛」，不是防护。
 *   - **正确姿势是「数据级授权」**：查询条件里必须带上当前用户的归属（`WHERE id = ? AND user_id = ?`），或者在查到后立刻判断 `if (!order.ownerId().equals(currentUser)) deny()`。**先鉴权再返回，不能先返回再判断**。
 *   - **授权要集中，不要分散**：每个接口手写一遍 `if` 必然漏。用拦截器 + 注解（如 `@RequirePermission("order:read")`）、或在 Service 层统一入口做校验。**新增接口时最容易忘**，所以要让它「默认拒绝」。
 *   - **`userId` 绝不能让前端传**：从会话/token 里取当前用户，而不是从请求参数里取。`POST /api/order?userId=1002` 这种设计等于把授权交给攻击者。
 *   - **对象归属要贯穿到底**：列表查询、详情、修改、删除、导出、下载附件 —— 每一个能接触到该对象的入口都要校验。常见漏洞是「详情校验了，导出没校验」。
 *   - **批量接口是高危点**：`POST /api/order/batch-delete` 传一个 id 数组，如果只校验第一个，后面全放过。必须逐个校验或一次性用 `IN` + `user_id` 条件过滤。
 *   - **管理员接口要独立鉴权**：不能靠「前端不显示入口」。管理端点应放在独立路径 + 独立鉴权中间件 + 可选 IP 白名单。
 *   - **间接引用（indirect reference）能降低风险**：对外暴露的 id 用「映射表」或「带签名的短期 token」，服务端反查真实 id。适合下载、分享链接等场景。
 *   - 易错点：只校验「是否登录」不校验「是否有权」；用 `@PreAuthorize` 但 SpEL 写错（`#id == authentication.principal.id` 拿不到值）；前端隐藏按钮就以为安全；测试只用自己的账号测，从不用两个账号互测。
 * ============================================================
 */

package com.heima.ch36;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class IdorDemo {
    record User(int id, String name, String role, int tenant) {
    }

    record Order(int id, int ownerId, int tenant, String item, int amount) {
    }

    static final List<User> USERS = List.of(
            new User(1, "张三", "user", 7),
            new User(2, "李四", "user", 7),
            new User(3, "王五", "admin", 7),
            new User(4, "赵六", "user", 9));

    static final List<Order> ORDERS = List.of(
            new Order(1001, 1, 7, "机械键盘", 399),
            new Order(1002, 2, 7, "显示器", 1299),
            new Order(1003, 1, 7, "鼠标垫", 29),
            new Order(1004, 4, 9, "耳机", 599));

    record Result(int status, String body) {
        @Override
        public String toString() {
            return status + " " + body;
        }
    }

    /** 四种实现，逐层加防护 */
    static class OrderApi {
        /** 错误示范一：完全不校验 */
        Result naive(int orderId) {
            for (Order o : ORDERS) {
                if (o.id() == orderId) {
                    return new Result(200, o.item() + " / " + o.amount() + " 元 / owner=" + o.ownerId());
                }
            }
            return new Result(404, "not found");
        }

        /** 错误示范二：只校验登录 */
        Result loginOnly(int orderId, User current) {
            if (current == null) {
                return new Result(401, "未登录");
            }
            return naive(orderId);
        }

        /** 正确：数据级授权，查询条件带归属 */
        Result scoped(int orderId, User current) {
            if (current == null) {
                return new Result(401, "未登录");
            }
            for (Order o : ORDERS) {
                if (o.id() == orderId && o.ownerId() == current.id() && o.tenant() == current.tenant()) {
                    return new Result(200, o.item() + " / " + o.amount() + " 元");
                }
            }
            return new Result(404, "not found");
        }

        /** 管理员可跨用户，但仍限制在本租户 */
        Result scopedAdmin(int orderId, User current) {
            if (current == null) {
                return new Result(401, "未登录");
            }
            for (Order o : ORDERS) {
                if (o.id() != orderId) {
                    continue;
                }
                boolean admin = current.role().equals("admin") && o.tenant() == current.tenant();
                boolean owner = o.ownerId() == current.id();
                if (admin || owner) {
                    return new Result(200, o.item() + " / " + o.amount() + " 元");
                }
            }
            return new Result(404, "not found");
        }

        /** 错误示范：userId 由前端传 */
        Result trustingParam(int orderId, int claimedUserId) {
            for (Order o : ORDERS) {
                if (o.id() == orderId && o.ownerId() == claimedUserId) {
                    return new Result(200, o.item() + " / " + o.amount() + " 元");
                }
            }
            return new Result(404, "not found");
        }
    }

    static void idorBasics() {
        System.out.println("【IDOR：把 id 改一下就看到别人的数据】");
        OrderApi api = new OrderApi();
        User zhangsan = USERS.get(0);
        User lisi = USERS.get(1);

        System.out.println("  张三（id=1）查自己的 1001：" + api.naive(1001));
        System.out.println("  张三（id=1）查李四的 1002：" + api.naive(1002) + "  <- 越权成功");
        System.out.println("  李四（id=2）查自己的 1002：" + api.naive(1002));
        System.out.println();
        System.out.println("  只校验登录也挡不住：");
        System.out.println("    张三已登录，查 1002 -> " + api.loginOnly(1002, zhangsan));
        System.out.println("  「登录了」不等于「这条数据归你」");
        System.out.println();
    }

    static void scopedQuery() {
        System.out.println("【正确写法：把归属写进查询条件】");
        OrderApi api = new OrderApi();
        User zhangsan = USERS.get(0);
        User lisi = USERS.get(1);
        System.out.println("  SQL: SELECT * FROM orders WHERE id = ? AND owner_id = ? AND tenant_id = ?");
        System.out.println("  张三查 1001 -> " + api.scoped(1001, zhangsan));
        System.out.println("  张三查 1002 -> " + api.scoped(1002, zhangsan) + "  <- 查不到，和不存在一样");
        System.out.println("  李四查 1002 -> " + api.scoped(1002, lisi));
        System.out.println("  关键点：越权时返回 404 而不是 403，避免泄露「这条数据存在」");
        System.out.println();
    }

    static void verticalPrivilege() {
        System.out.println("【垂直越权：普通用户调管理员接口】");
        OrderApi api = new OrderApi();
        User zhangsan = USERS.get(0);
        User wangwu = USERS.get(2);
        User zhaoliu = USERS.get(3);
        System.out.println("  张三(user) 查 1002 -> " + api.scopedAdmin(1002, zhangsan));
        System.out.println("  王五(admin, 租户7) 查 1002 -> " + api.scopedAdmin(1002, wangwu) + "  <- 同租户可管");
        System.out.println("  王五(admin, 租户7) 查 1004 -> " + api.scopedAdmin(1004, wangwu) + "  <- 跨租户也不行");
        System.out.println("  赵六(user, 租户9) 查 1004 -> " + api.scopedAdmin(1004, zhaoliu));
        System.out.println("  管理员身份也要受租户边界约束，否则一个租户的管理员能看全平台");
        System.out.println();
    }

    static void trustingClientId() {
        System.out.println("【userId 从参数里取 = 把授权交给攻击者】");
        OrderApi api = new OrderApi();
        System.out.println("  请求 POST /api/order/detail  body: {orderId:1002, userId:2}");
        System.out.println("  张三把 userId 改成 2 -> " + api.trustingParam(1002, 2));
        System.out.println("  正确：userId 从会话/token 里取，请求体里的 userId 一律忽略");
        System.out.println();
    }

    static void uuidIsNotAuth() {
        System.out.println("【换 UUID 只是提高门槛，不是防护】");
        String[] ids = {"1001", "550e8400-e29b-41d4-a716-446655440000"};
        System.out.println("  自增 id：" + ids[0] + " -> 改一个数字就能遍历");
        System.out.println("  UUID：" + ids[1]);
        System.out.println("    猜不出来，但会出现在：分享链接、Referer、日志、截图、客服工单");
        System.out.println("    一旦泄露，照样越权 —— 所以授权校验不能省");
        System.out.println();
    }

    static void batchAndExport() {
        System.out.println("【批量接口与导出：最容易漏的地方】");
        OrderApi api = new OrderApi();
        User zhangsan = USERS.get(0);
        int[] ids = {1001, 1002, 1003};
        System.out.println("  POST /api/order/batch  ids=" + java.util.Arrays.toString(ids));
        System.out.println("    错误实现（只校验第一个 id，其余直接放行）：");
        for (int i = 0; i < ids.length; i++) {
            boolean first = i == 0;
            Result r = first ? api.scoped(ids[i], zhangsan) : api.naive(ids[i]);
            System.out.printf("      id=%d -> %s%s%n", ids[i], r, first ? "" : "  <- 未校验");
        }
        System.out.println("    正确实现（每个 id 都过一遍 scoped）：");
        for (int id : ids) {
            System.out.printf("      id=%d -> %s%n", id, api.scoped(id, zhangsan));
        }
        System.out.println("    或一条 SQL 搞定：WHERE id IN (?,?,?) AND owner_id = ?");
        System.out.println();
        System.out.println("  同类高危入口：导出 CSV、下载附件、打印、消息推送、分享链接");
        System.out.println("  规律：凡是「按 id 取数据」的地方，都要问一句「这条归谁」");
        System.out.println();
    }

    static void centralize() {
        System.out.println("【授权要集中：默认拒绝，新增接口自动受保护】");
        Map<String, String> endpointPolicy = new LinkedHashMap<>();
        endpointPolicy.put("GET /api/order/{id}", "owner-or-admin");
        endpointPolicy.put("GET /api/admin/users", "admin-only");
        endpointPolicy.put("GET /api/report/export", "owner-scoped");
        endpointPolicy.put("GET /api/health", "public");
        endpointPolicy.put("GET /api/order/{id}/invoice", "MISSING");
        endpointPolicy.forEach((k, v) -> {
            String mark = v.equals("MISSING") ? "  <- 未登记，必须默认拒绝" : "";
            System.out.printf("  %-30s %s%s%n", k, v, mark);
        });
        System.out.println("  做法：拦截器读「策略表」，没登记的一律 403");
        System.out.println("  这样新接口上线时忘了配策略，是「不能用」而不是「裸奔」");
        System.out.println();
    }

    static void testingMethod() {
        System.out.println("【怎么测越权：永远用两个账号】");
        String[] steps = {
                "准备两个同角色账号 A、B，各自造一条数据",
                "用 A 的 token 请求 B 的数据 id，期望 404/403",
                "用 A 的 token 请求管理员接口，期望 403",
                "把请求体里的 userId/tenantId 改成别人的，期望被忽略",
                "批量接口里混入别人的 id，期望整批拒绝或只返回自己的",
                "导出/下载/分享链接逐个试一遍",
        };
        int i = 1;
        for (String s : steps) {
            System.out.printf("  %d. %s%n", i++, s);
        }
        System.out.println("  只用自己账号测，是越权漏洞最常见的漏测原因");
        System.out.println();
    }

    static void indirectReference() {
        System.out.println("【间接引用：对外不暴露真实 id】");
        Map<String, Integer> shareTokens = new LinkedHashMap<>();
        shareTokens.put("tok-a1b2c3", 1001);
        shareTokens.put("tok-d4e5f6", 1003);
        System.out.println("  分享链接：https://shop.example.com/share/tok-a1b2c3");
        shareTokens.forEach((tok, orderId) ->
                System.out.printf("  %s -> 内部订单 %d%n", tok, orderId));
        System.out.println("  好处：token 可撤销、可设过期、可绑定查看者");
        System.out.println("  但服务端拿到 token 反查后，仍要校验当前用户是否有权看这条");
        System.out.println();
    }

    public static void main(String[] args) {
        idorBasics();
        scopedQuery();
        verticalPrivilege();
        trustingClientId();
        uuidIsNotAuth();
        batchAndExport();
        centralize();
        testingMethod();
        indirectReference();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 给 `OrderApi` 加一个 `listOrders(User current, int page, int size)`：只返回当前用户可见的订单，并说明为什么「查出来再过滤」不如「查询条件里带归属」。
 * 2. 实现一个「策略表 + 拦截器」骨架：未登记的端点默认 403，登记为 `owner-scoped` 的端点自动注入归属条件。
 * 3. 写一组越权测试用例（伪代码即可）：覆盖详情、修改、删除、批量、导出、管理员接口六个入口。
 * 4. 思考题：为什么越权时推荐返回 404 而不是 403？在什么场景下 403 反而更合适？
 * ============================================================
 */
