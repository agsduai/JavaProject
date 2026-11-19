/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：64-02
 * 课名：OAuth2 四种授权模式
 * ------------------------------------------------------------
 * 知识点：
 *   - **OAuth2 解决的是"授权而不共享密码"**：第三方应用获得**有限权限的令牌**（scope），而不是用户的账号密码——**令牌有三要素**：有效期、权限范围（scope）、可撤销。
 *   - **四种授权模式**：**授权码（Authorization Code）**——最安全，适合有后端的应用（授权码 + PKCE 防拦截）；**隐式（Implicit）**——已废弃（令牌直接暴露在前端）；**密码模式（Resource Owner Password）**——高度可信的第一方应用（如自家 App），不推荐；**客户端凭证（Client Credentials）**——服务间调用（无用户参与）。
 *   - **授权码模式的关键交互**：`/authorize` 返回 code（一次性、短有效期）-> 后端用 `code + client_secret` 换 token -> **code 不能被前端截获**（PKCE 用 code_verifier/challenge 进一步保护公共客户端）。
 *   - **令牌的使用**：`Authorization: Bearer <token>`；**access_token 短期**（分钟~小时）+ **refresh_token 长期**（用于换新 access_token，可撤销）——**刷新令牌轮换**（每次刷新换新的 refresh_token）能降低泄露风险。
 *   - **scope 与最小权限**：按能力划分 scope（`order:read`/`order:write`）——**与 36 章的越权防护同源**（44-09 的权限四道闸）；**授权页面展示 scope 给用户**（知情同意）。
 *   - 易错点：用隐式模式（token 泄露）；code 无有效期（被重放）；refresh_token 永久有效且不轮换（泄露即长期有效）；scope 粒度过粗（一个 token 能改所有数据）。
 * ============================================================
 */

package com.heima.ch64;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// ==================== 示例代码 ====================

public class OAuth2Flow {
    record Client(String clientId, String secret, List<String> allowedScopes) {
    }

    record Code(String value, String clientId, String userId, List<String> scopes,
                long expireAtMs, boolean used) {
    }

    record Token(String accessToken, String refreshToken, String userId,
                 List<String> scopes, long expireAtMs) {
    }

    static final Map<String, Code> codes = new LinkedHashMap<>();
    static final Map<String, Token> tokens = new LinkedHashMap<>();

    /** 第一步：用户授权 -> 发放 code（一次性、短有效期） */
    static String authorize(Client client, String userId, List<String> requestedScopes, long now) {
        for (String s : requestedScopes) {
            if (!client.allowedScopes().contains(s)) {
                return "拒绝：scope " + s + " 未在客户端白名单内";
            }
        }
        String code = UUID.randomUUID().toString().substring(0, 8);
        codes.put(code, new Code(code, client.clientId(), userId, requestedScopes,
                now + 60_000, false));
        return "code=" + code + "（60s 有效，一次性）";
    }

    /** 第二步：后端用 code + secret 换 token */
    static String exchange(Client client, String code, long now) {
        Code c = codes.get(code);
        if (c == null) {
            return "失败：code 不存在";
        }
        if (c.used()) {
            return "失败：code 已被使用（重放攻击）";
        }
        if (c.expireAtMs() < now) {
            return "失败：code 已过期";
        }
        if (!c.clientId().equals(client.clientId())) {
            return "失败：code 与客户端不匹配";
        }
        codes.put(code, new Code(c.value(), c.clientId(), c.userId(), c.scopes(),
                c.expireAtMs(), true));
        String access = UUID.randomUUID().toString().substring(0, 10);
        String refresh = UUID.randomUUID().toString().substring(0, 10);
        tokens.put(access, new Token(access, refresh, c.userId(), c.scopes(), now + 3_600_000));
        return "access_token=" + access + " scope=" + c.scopes() + "（1h 有效）";
    }

    /** 客户端凭证模式：服务间调用（无用户） */
    static String clientCredentials(Client client, long now) {
        String access = UUID.randomUUID().toString().substring(0, 10);
        tokens.put(access, new Token(access, null, "service:" + client.clientId(),
                client.allowedScopes(), now + 3_600_000));
        return "service token=" + access + " scopes=" + client.allowedScopes();
    }

    /** 校验：token 有效性 + scope */
    static String call(String token, String requiredScope, long now) {
        Token t = tokens.get(token);
        if (t == null) {
            return "401 无效令牌";
        }
        if (t.expireAtMs() < now) {
            return "401 令牌过期（可用 refresh_token 换新）";
        }
        if (!t.scopes().contains(requiredScope)) {
            return "403 scope 不足（需要 " + requiredScope + "，实际 " + t.scopes() + "）";
        }
        return "200 调用成功（user=" + t.userId() + "）";
    }

    public static void main(String[] args) {
        Client client = new Client("app-1001", "s3cret",
                List.of("order:read", "order:write"));
        long now = 0;
        System.out.println("【授权码模式】");
        String codeMsg = authorize(client, "user-501", List.of("order:read"), now);
        System.out.println("  授权：" + codeMsg);
        String code = codeMsg.replaceAll(".*code=([^（]+).*", "$1");
        System.out.println("  换 token：" + exchange(client, code, now + 1000));
        System.out.println("  重放同一 code：" + exchange(client, code, now + 2000));
        System.out.println("  越权 scope 申请：" + authorize(client, "user-501",
                List.of("user:delete"), now));
        System.out.println();
        System.out.println("【令牌使用与 scope 校验】");
        String access = tokens.keySet().iterator().next();
        System.out.println("  读订单：" + call(access, "order:read", now + 5000));
        System.out.println("  写订单：" + call(access, "order:write", now + 5000));
        System.out.println("  过期后：" + call(access, "order:read", now + 4_000_000));
        System.out.println();
        System.out.println("【客户端凭证】" + clientCredentials(client, now));
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现完整授权码流程：authorize -> code -> exchange -> 调用校验，验证 code 一次性、过期、客户端绑定三个约束。
 * 2. 实现 refresh_token 轮换：旧 refresh 使用后立即失效并签发新的，构造"旧 refresh 重放"验证被拒。
 * 3. 思考题：为什么隐式模式被废弃？从"令牌在前端暴露面"与 PKCE 的替代作用分析。
 * ============================================================
 */
