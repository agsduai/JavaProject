/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：27-10
 * 课名：接口文档与联调
 * ------------------------------------------------------------
 * 知识点：
 *   - 文档即契约：接口一旦发布，字段名、类型、是否必填、错误码就是承诺。改字段名等于破坏契约，要么加版本（`/api/v2/items`），要么新老字段并存一段时间。
 *   - 必写的六项：URL + 方法、请求头（是否需要 token）、请求参数（类型/必填/范围/示例）、响应结构（含错误码）、错误码表、幂等性说明。
 *   - `OpenAPI`/`Swagger` 的价值：文档从代码生成，不会和实现脱节；还能生成前端 SDK、做契约测试。手写 Word 文档三天就过期。
 *   - 联调顺序：先定契约 -> 后端出 mock（返回假数据）-> 前端并行开发 -> 后端实现完切真实接口 -> 契约测试兜底。
 *   - Mock 的意义不是"造假数据"，而是把"前端等后端"的串行变成并行。
 *   - 契约测试：用同一份 schema 校验请求和响应，字段少一个、类型变一个立刻失败。
 *   - 常见坑：`null` 和"字段不存在"混用（前端无法区分"没查"和"查了是空"）；分页返回 `list` 有时叫 `records` 有时叫 `rows`；时间格式一会时间戳一会 `yyyy-MM-dd`；枚举传中文。
 *   - 易错：文档写了 `size` 最大 100，代码里没校验；文档说 401 返回 `{code,msg}`，实际返回了 HTML 错误页（被网关拦了）。
 * ============================================================
 */

package com.heima.ch27;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class ApiContract {
    /** 字段定义 */
    record Field(String name, String type, boolean required, String rule, String example) { }

    /** 接口定义 */
    record Api(String method, String path, String summary, List<Field> request,
               List<Field> response, List<String> errors) { }

    public static void main(String[] args) {
        List<Api> apis = new ArrayList<>();
        apis.add(new Api("POST", "/api/items", "新增商品",
                List.of(
                        new Field("name", "string", true, "2~64 字符", "机械键盘"),
                        new Field("priceCent", "integer", true, "> 0，单位分", "39900"),
                        new Field("stock", "integer", true, "0~999999", "100"),
                        new Field("description", "string", false, "<= 500 字符", "87 键 红轴")),
                List.of(
                        new Field("code", "integer", true, "200/400/409", "201"),
                        new Field("msg", "string", true, "-", "ok"),
                        new Field("data.id", "integer", true, "-", "42"),
                        new Field("data.name", "string", true, "-", "机械键盘"),
                        new Field("data.price", "string", true, "两位小数字符串", "399.00")),
                List.of("400 参数格式错误", "409 商品名重复", "401 未登录")));

        apis.add(new Api("GET", "/api/items", "分页查询商品",
                List.of(
                        new Field("page", "integer", false, ">= 1，默认 1", "1"),
                        new Field("size", "integer", false, "1~100，默认 10", "10"),
                        new Field("sort", "string", false, "id|price|stock|createdAt", "price,desc")),
                List.of(
                        new Field("code", "integer", true, "-", "200"),
                        new Field("data.list", "array", true, "-", "[{...}]"),
                        new Field("data.total", "integer", true, ">= 0", "23"),
                        new Field("data.page", "integer", true, ">= 1", "1"),
                        new Field("data.size", "integer", true, "1~100", "10"),
                        new Field("data.pages", "integer", true, ">= 0", "3")),
                List.of("400 排序字段不支持")));

        for (Api api : apis) {
            printApi(api);
        }

        System.out.println("=== 契约校验：响应字段是否齐全 ===");
        Map<String, Object> actual = new LinkedHashMap<>();
        actual.put("code", 200);
        actual.put("data", Map.of("list", List.of(), "total", 0, "page", 1, "size", 10, "pages", 0));
        // 故意漏掉 msg，验证校验能发现
        List<String> missing = checkResponse(apis.get(1), actual);
        System.out.println("  实际响应: " + actual.keySet());
        System.out.println("  缺失字段: " + (missing.isEmpty() ? "无" : missing));

        System.out.println();
        System.out.println("=== 契约破坏的三种典型场景 ===");
        Map<String, String> breaks = new LinkedHashMap<>();
        breaks.put("改字段名", "price -> amount：前端全部拿到 undefined，页面显示 NaN");
        breaks.put("改类型", "price 从字符串 '399.00' 变成数字 39900：前端不乘除直接显示 39900 元");
        breaks.put("改语义", "null 从'未查询'变成'查了为空'：前端无法区分，缓存逻辑出错");
        breaks.forEach((k, v) -> System.out.printf("  %-8s %s%n", k, v));
        System.out.println("  正确做法：加新字段并保留旧字段一个版本周期，前端迁移完再删");

        System.out.println();
        System.out.println("=== 联调流程 ===");
        String[] steps = {
                "1. 后端写 OpenAPI 契约（字段/类型/必填/错误码）",
                "2. 用契约生成 mock 服务，返回符合 schema 的假数据",
                "3. 前端对接 mock 开发，不阻塞",
                "4. 后端实现，CI 里跑契约测试（响应必须匹配 schema）",
                "5. 联调：切真实地址，只处理真实差异",
        };
        for (String s : steps) {
            System.out.println("  " + s);
        }
    }

    static void printApi(Api api) {
        System.out.println("=== " + api.method() + " " + api.path() + "  " + api.summary() + " ===");
        System.out.println("  请求参数:");
        if (api.request().isEmpty()) {
            System.out.println("    （无）");
        }
        for (Field f : api.request()) {
            System.out.printf("    %-14s %-8s %-8s %-18s 例: %s%n",
                    f.name(), f.type(), f.required() ? "必填" : "可选", f.rule(), f.example());
        }
        System.out.println("  响应字段:");
        for (Field f : api.response()) {
            System.out.printf("    %-14s %-8s %-8s %s%n", f.name(), f.type(),
                    f.required() ? "必有" : "可空", f.rule());
        }
        System.out.println("  错误码: " + api.errors());
        System.out.println();
    }

    /** 契约测试：逐个检查必填字段是否存在 */
    static List<String> checkResponse(Api api, Map<String, Object> actual) {
        List<String> missing = new ArrayList<>();
        for (Field f : api.response()) {
            if (!f.required()) {
                continue;
            }
            if (!hasPath(actual, f.name())) {
                missing.add(f.name());
            }
        }
        return missing;
    }

    /** 支持 data.list 这种点号路径 */
    static boolean hasPath(Map<String, Object> root, String path) {
        Object cur = root;
        for (String part : path.split("\\.")) {
            if (!(cur instanceof Map)) {
                return false;
            }
            Map<?, ?> map = (Map<?, ?>) cur;
            if (!map.containsKey(part)) {
                return false;
            }
            cur = map.get(part);
        }
        return true;
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 给"取消订单"接口写一份完整契约（URL、方法、请求、响应、错误码、幂等说明）。
 * 2. 为什么"响应里 `null` 表示未查询"和"表示查了是空"必须区分？设计一个能区分两者的返回结构。
 * 3. 契约测试能发现哪些问题、发现不了哪些问题？举一个"契约测试通过但线上仍然出错"的例子。
 * ============================================================
 */
