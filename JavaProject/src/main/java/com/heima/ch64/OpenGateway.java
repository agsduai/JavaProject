/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：64-08
 * 课名：API 网关治理：鉴权、审计、灰度
 * ------------------------------------------------------------
 * 知识点：
 *   - **开放平台网关与内部网关的差异**：内部网关（47-03）管**服务路由与熔断**；开放网关还要管**第三方身份（AppKey/令牌）、配额、计费、审计、请求/响应改写**——**它是"对外能力的统一收口"**。
 *   - **鉴权链的顺序**：**IP 白名单**（可选）-> **AppKey 识别** -> **签名校验/令牌校验** -> **scope 校验** -> **配额检查** -> **路由转发**——**顺序错了会浪费算力**（先验签再查配额，配额超限的请求白验签）。
 *   - **审计的内容**：`appId/接口/参数摘要（脱敏）/耗时/状态/错误码/客户端 IP`——**每次调用留痕**（计费依据 51-05、纠纷举证、安全分析 36 章）；**参数脱敏**（36-09）。
 *   - **灰度与降级**：**按 appId 灰度**（重点客户先上新版本 41-07）、**按接口降级**（某接口故障时返回缓存或 503，44-08）、**配额动态调整**（大促期临时提升）。
 *   - **网关的自身保护**：**请求体大小限制**（防大包攻击）、**超时与并发上限**、**响应压缩**、**防止内部错误泄露堆栈**（36 章）。
 *   - 易错点：鉴权链顺序错（浪费算力或绕过校验）；审计无脱敏（日志泄露敏感数据）；网关做业务逻辑（47 章原则）；无限流（第三方打爆平台）。
 * ============================================================
 */

package com.heima.ch64;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class OpenGateway {
    record Request(String appId, String path, String method, String signature,
                   String timestamp, List<String> scopes, long bodyBytes) {
    }

    record Decision(int status, String reason, String stage) {
    }

    /** 鉴权链：顺序即性能与安全 */
    static Decision handle(Request req, Map<String, List<String>> appScopes,
                           Map<String, Long> quotaLeft, long maxBodyBytes, long now) {
        if (req.bodyBytes() > maxBodyBytes) {
            return new Decision(413, "请求体过大", "size-check");
        }
        List<String> allowed = appScopes.get(req.appId());
        if (allowed == null) {
            return new Decision(401, "未知应用（AppKey 无效）", "app-identify");
        }
        if (req.signature() == null || req.signature().isBlank()) {
            return new Decision(401, "缺少签名", "signature");
        }
        long ts;
        try {
            ts = Long.parseLong(req.timestamp());
        } catch (NumberFormatException e) {
            return new Decision(400, "时间戳格式错误", "signature");
        }
        if (Math.abs(now - ts) > 300_000) {
            return new Decision(401, "时间戳过期", "signature");
        }
        for (String scope : req.scopes()) {
            if (!allowed.contains(scope)) {
                return new Decision(403, "scope 不足：" + scope, "scope");
            }
        }
        long left = quotaLeft.getOrDefault(req.appId(), 0L);
        if (left <= 0) {
            return new Decision(429, "配额耗尽", "quota");
        }
        quotaLeft.put(req.appId(), left - 1);
        return new Decision(200, "转发到后端（剩余配额 " + (left - 1) + "）", "route");
    }

    static void audit() {
        System.out.println("【审计记录字段】");
        List<String> fields = List.of(
                "appId / 接口路径 / 方法（谁调了什么）",
                "参数摘要（脱敏：手机号 138****1234，36-09）",
                "耗时 / 状态码 / 错误码（性能与失败分析）",
                "客户端 IP / UA（安全分析，36 章）",
                "trace_id（串联内部链路，42-07）");
        fields.forEach(f -> System.out.println("  - " + f));
        System.out.println();
    }

    public static void main(String[] args) {
        Map<String, List<String>> appScopes = new LinkedHashMap<>();
        appScopes.put("app-1001", List.of("order:read", "order:write"));
        Map<String, Long> quota = new LinkedHashMap<>();
        quota.put("app-1001", 2L);

        long now = 1_000_000;
        List<Request> requests = new ArrayList<>(List.of(
                new Request("app-1001", "/open/orders", "GET", "sig", String.valueOf(now),
                        List.of("order:read"), 1024),
                new Request("app-1001", "/open/orders", "GET", "sig", String.valueOf(now - 600_000),
                        List.of("order:read"), 1024),
                new Request("app-9999", "/open/orders", "GET", "sig", String.valueOf(now),
                        List.of("order:read"), 1024),
                new Request("app-1001", "/open/orders", "POST", "sig", String.valueOf(now),
                        List.of("user:delete"), 1024),
                new Request("app-1001", "/open/orders", "POST", "sig", String.valueOf(now),
                        List.of("order:write"), 2 * 1024 * 1024)));
        System.out.println("【鉴权链逐项拦截】");
        for (Request r : requests) {
            Decision d = handle(r, appScopes, quota, 1024 * 1024, now);
            System.out.printf("  %-8s %-6s scope=%-14s -> %d %-30s（%s）%n",
                    r.appId(), r.method(), r.scopes(), d.status(), d.reason(), d.stage());
        }
        System.out.println();
        System.out.println("【配额耗尽】");
        Decision d = handle(new Request("app-1001", "/open/orders", "GET", "sig",
                String.valueOf(now), List.of("order:read"), 512), appScopes, quota,
                1024 * 1024, now);
        System.out.printf("  %d %s%n", d.status(), d.reason());
        System.out.println();
        audit();
        System.out.println("  顺序价值：先拦大小/身份/签名/scope，再扣配额 —— 不浪费算力也不误扣配额");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现开放网关鉴权链：大小限制 -> AppKey -> 签名/时间戳 -> scope -> 配额 -> 路由，用 6 个请求验证各层拦截。
 * 2. 实现审计脱敏：对手机号/身份证/银行卡做掩码，验证日志中无明文。
 * 3. 思考题：为什么"配额检查要放在签名校验之后"？从"防止无效请求消耗配额"与"算力成本"两个角度分析。
 * ============================================================
 */
