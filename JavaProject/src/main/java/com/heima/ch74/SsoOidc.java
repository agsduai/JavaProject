/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：74-02
 * 课名：SSO 与 OIDC 流程
 * ------------------------------------------------------------
 * 知识点：
 *   - **SSO 的价值是"一次登录，多系统通行"**：企业有 20 个内部系统，用户不该登录 20 次——**认证集中在 IdP（身份提供方）**，业务系统（SP）信任 IdP 的断言。
 *   - **OIDC 是 OAuth2 之上的身份层**：OAuth2 只给"访问令牌"（能干什么），**OIDC 加了 `id_token`（JWT，声明"你是谁"）**——**OIDC = OAuth2 + 身份声明**，是现代 SSO 的标准协议（64-02 的延伸）。
 *   - **授权码 + PKCE 的 SSO 流程**：用户访问系统 A -> 未登录跳 IdP -> IdP 认证后带 code 回 A -> A 用 code 换 id_token/access_token -> A 建立本地会话——**code 一次性 + 短有效期**（64-02 同款防护）。
 *   - **单点登出（SLO）更难**：登出 IdP 后，各系统本地会话要失效——**做法**：IdP 通知各 SP（back-channel logout）或 **短期 token + 定期校验**——**跨域会话同步是 SSO 的工程难点**。
 *   - **与 74-08 的关系**：SSO 解决"认证"，但**每个系统仍要自己做授权**（IdP 不知道业务权限）——**别把 SSO 当权限系统**。
 *   - 易错点：id_token 不验签（伪造身份）；不校验 `iss`/`aud`/`exp`（令牌给错系统用）；code 可重复使用；单点登出不实现（登出 IdP 后各系统还能用）。
 * ============================================================
 */

package com.heima.ch74;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// ==================== 示例代码 ====================

public class SsoOidc {
    record IdToken(String iss, String aud, String sub, long exp, String nonce,
                   String signature) {
        String describe() {
            return "iss=" + iss + " aud=" + aud + " sub=" + sub + " exp=" + exp;
        }
    }

    static final Map<String, Boolean> usedCodes = new LinkedHashMap<>();

    /** IdP：认证后签发 code */
    static String authorize(String clientId, String redirectUri, String user, long now) {
        String code = UUID.randomUUID().toString().substring(0, 8);
        usedCodes.put(code, false);
        System.out.printf("  IdP 认证 %s -> 签发 code=%s（一次性，60s）%n", user, code);
        return code;
    }

    /** SP：用 code 换 token（含 PKCE 校验，简化） */
    static String exchange(String code, String clientId, String expectedAud) {
        Boolean used = usedCodes.get(code);
        if (used == null) {
            return "失败：code 不存在";
        }
        if (used) {
            return "失败：code 已被使用（重放）";
        }
        usedCodes.put(code, true);
        IdToken token = new IdToken("https://idp.example.com", expectedAud, "user-1001",
                9_999_999, "n-abc", "sig-valid");
        System.out.printf("  SP 换 token 成功：%s%n", token.describe());
        return "成功：建立本地会话（sub=user-1001）";
    }

    /** SP 验签 + 声明校验（生产必须做） */
    static String validate(IdToken t, String expectedIss, String expectedAud, long now) {
        if (!"sig-valid".equals(t.signature())) {
            return "拒绝：签名无效（伪造身份）";
        }
        if (!expectedIss.equals(t.iss())) {
            return "拒绝：iss 不符（令牌来自别的 IdP）";
        }
        if (!expectedAud.equals(t.aud())) {
            return "拒绝：aud 不符（令牌给别的系统用的）";
        }
        if (now > t.exp()) {
            return "拒绝：已过期";
        }
        return "通过：可建立会话";
    }

    static void singleLogout() {
        System.out.println("【单点登出（SLO）】");
        Map<String, String> approaches = new LinkedHashMap<>();
        approaches.put("back-channel 通知", "IdP 主动调各 SP 的登出接口（需注册 SP 回调）");
        approaches.put("短期 token", "access_token 15 分钟 + 定期校验 IdP（最多滞后 15 分钟）");
        approaches.put("前端轮询", "SP 定时查 IdP 会话状态（成本高，兜底用）");
        approaches.forEach((k, v) -> System.out.printf("  %-16s %s%n", k, v));
        System.out.println("  工程取舍：安全要求高用 back-channel；一般系统用短期 token 可接受");
    }

    public static void main(String[] args) {
        System.out.println("【SSO 登录流程】");
        String code = authorize("app-order", "https://order.example.com/callback", "alice", 1000);
        System.out.println("  " + exchange(code, "app-order", "app-order"));
        System.out.println("  " + exchange(code, "app-order", "app-order"));

        System.out.println();
        System.out.println("【令牌校验】");
        IdToken good = new IdToken("https://idp.example.com", "app-order", "user-1001",
                9_999_999, "n", "sig-valid");
        System.out.println("  " + validate(good, "https://idp.example.com", "app-order", 1000));
        System.out.println("  " + validate(good, "https://idp.example.com", "app-other", 1000));
        System.out.println("  " + validate(new IdToken("https://idp.example.com", "app-order",
                "u", 500, "n", "sig-valid"), "https://idp.example.com", "app-order", 1000));

        System.out.println();
        singleLogout();
        System.out.println();
        System.out.println("  边界：SSO 只解决认证，业务权限仍由各系统自己管（74-04）");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现 SSO 流程：authorize/exchange/validate，验证 code 一次性与令牌三项校验（iss/aud/exp）。
 * 2. 对比 back-channel 登出与短期 token 的"安全性与实现成本"，给出你系统的选择。
 * 3. 思考题：为什么 SSO"不能替代授权"？从 IdP 不知道业务权限模型分析。
 * ============================================================
 */
