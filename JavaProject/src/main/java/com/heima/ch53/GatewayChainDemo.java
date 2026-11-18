/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：53-07
 * 课名：Gateway 与鉴权链路
 * ------------------------------------------------------------
 * 知识点：
 *   - **Spring Cloud Gateway（阿里栈常用 SCG）的核心模型**：Route（路由：谓词匹配 -> 目标 URI）+ Predicate（断言：Path/Header/Method/Time）+ Filter（前置/后置修改请求响应）。**44-08 AI 网关与 47-03 流量治理的路由模型在此统一**——网关的本质就是"谓词匹配 + 过滤器链"。
 *   - **网关鉴权链路**：登录态校验（JWT 验签，26-02）-> 权限粗校验（路由级白名单）-> 上下文注入（userId/租户写入 header 传下游，44-09 的"服务间身份"）-> 限流（按用户维度 44-08）——**网关是零信任的第一道闸**（47-04：内网不等于可信）。
 *   - **网关的高频坑**：Filter 顺序错误（鉴权在限流后，被限流的请求也验签浪费）；响应体篡改需读 body（内存缓冲，大响应慎改）；WebSocket 转发配置遗漏；**全局过滤器抛异常未转统一响应**（用户收到 500 而非业务错误）。
 *   - **与 Sentinel 的联动**：网关维度的流控（按 route/按用户）——Sentinel gateway adapter 支持参数维度（同 53-05 热点限流）。
 *   - 易错点：网关做业务逻辑（网关只做横切：鉴权/限流/路由/观测）；header 注入未清理同名外部头（伪造 userId 头穿透）；鉴权信息只在网关验、下游不校验（内网裸奔，47-04）。
 * ============================================================
 */

package com.heima.ch53;

import java.util.ArrayList;
import java.util.List;

// ==================== 示例代码 ====================

public class GatewayChainDemo {
    record FilterDef(int order, String name, boolean blocking) {
    }

    record Result(int status, String body) {
    }

    static Result run(List<FilterDef> filters, String jwt, String path) {
        for (FilterDef f : filters) {
            switch (f.name()) {
                case "rate-limit" -> System.out.println("  [限流] 通过");
                case "auth" -> {
                    if (jwt == null || jwt.isBlank()) {
                        return new Result(401, "{\"code\":\"UNAUTHORIZED\"}");
                    }
                    System.out.println("  [鉴权] 通过，注入 X-User-Id");
                }
                case "route" -> System.out.println("  [路由] -> " + path);
                default -> {
                }
            }
        }
        return new Result(200, "{\"ok\":true}");
    }

    public static void main(String[] args) {
        List<FilterDef> filters = List.of(new FilterDef(1, "rate-limit", false),
                new FilterDef(2, "auth", true), new FilterDef(3, "route", false));
        Result r1 = run(filters, "valid-jwt", "/order/create");
        System.out.println("  合法请求 -> " + r1.status());
        Result r2 = run(filters, "", "/order/create");
        System.out.println("  无凭证 -> " + r2.status() + "（鉴权短路，不进路由）");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现完整网关链（限流/鉴权/路由三过滤器 + 统一异常响应），三个场景（正常/无凭证/限流触发）输出日志。
 * 2. 实现"外部同名头清理"：请求自带 X-User-Id 头时被网关覆盖——验证伪造穿透被阻断。
 * 3. 思考题：为什么网关只做横切逻辑？列出三个"业务逻辑下沉到网关"引发的问题。
 * ============================================================
 */
