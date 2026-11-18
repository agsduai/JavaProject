/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：26-08
 * 课名：JWT：无状态令牌
 * ------------------------------------------------------------
 * 知识点：
 *   - 传统 Session 的问题：服务端存 session，多实例要共享（粘性会话或 Redis），横向扩展麻烦，跨域也麻烦。
 *   - JWT（JSON Web Token）是**自包含**的令牌：服务端不存，客户端每次带上，服务端验签即可。三部分组成，用 `.` 分隔：
 *   - Header：`{"alg":"HS256","typ":"JWT"}`，Base64URL 编码。
 *   - Payload：claims，标准字段 `sub`（主体）、`iat`（签发时间）、`exp`（过期时间）、`jti`（唯一 id），也可放自定义字段如 `userId`、`role`。**注意 payload 只是 Base64 编码，不是加密，任何人都能解开看**。
 *   - Signature：`HMACSHA256(base64Url(header) + "." + base64Url(payload), secret)`。
 *   - 验签流程：拆三段 -> 重算签名比对 -> 校验 `exp` 未过期 -> 取出 claims。
 *   - 关键原则：
 *   - 绝不在 payload 放密码、手机号、身份证等敏感信息。
 *   - secret 必须足够长（>= 32 字节）且只存在服务端配置/环境变量里，不能提交到 git。
 *   - `exp` 必须设，且不宜过长（一般 30 分钟 ~ 2 小时）；长会话用 refresh token。
 *   - 令牌泄露无法主动失效（无状态），需要"黑名单/白名单"或短期 token + 刷新机制兜底。
 *   - 易错：用 `none` 算法（不验签）导致任意伪造；把 `alg` 交给客户端决定（算法混淆攻击）；secret 硬编码在代码里。
 * ============================================================
 */

package com.heima.ch26;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

// ==================== 示例代码 ====================

public class JwtDemo {
    static final String SECRET = "heima-2026-java-course-secret-key-32bytes!";
    static final Base64.Encoder B64E = Base64.getUrlEncoder().withoutPadding();
    static final Base64.Decoder B64D = Base64.getUrlDecoder();

    public static void main(String[] args) throws Exception {
        System.out.println("=== 签发 token ===");
        String token = createToken(1001L, "张三", "user", 3600);
        System.out.println("  " + token);
        System.out.println("  长度 " + token.length() + " 字符，三段用 . 分隔");

        System.out.println();
        System.out.println("=== 三段内容（注意 payload 是明文可解的）===");
        String[] parts = token.split("\\.");
        System.out.println("  Header  : " + decode(parts[0]));
        System.out.println("  Payload : " + decode(parts[1]));
        System.out.println("  Signature（二进制，不可读）: " + parts[2].substring(0, 16) + "...");

        System.out.println();
        System.out.println("=== 验签通过 ===");
        Map<String, Object> claims = verify(token);
        System.out.println("  claims = " + claims);

        System.out.println();
        System.out.println("=== 篡改 payload：把 role 改成 admin ===");
        String forgedPayload = B64E.encodeToString(
                decode(parts[1]).replace("\"user\"", "\"admin\"").getBytes(StandardCharsets.UTF_8));
        String forged = parts[0] + "." + forgedPayload + "." + parts[2];
        try {
            verify(forged);
        } catch (RuntimeException e) {
            System.out.println("  " + e.getMessage() + "（签名对不上，篡改被识破）");

        }

        System.out.println();
        System.out.println("=== 换一个 secret 签发的 token ===");
        String other = createTokenWithSecret(1001L, "hacker-secret-hacker-secret-hacker!", 3600);
        try {
            verify(other);
        } catch (RuntimeException e) {
            System.out.println("  " + e.getMessage());
        }

        System.out.println();
        System.out.println("=== 过期 token ===");
        String expired = createToken(1001L, "张三", "user", -10);
        try {
            verify(expired);
        } catch (RuntimeException e) {
            System.out.println("  " + e.getMessage());
        }

        System.out.println();
        System.out.println("=== 与 Session 对比 ===");
        Map<String, String> cmp = new LinkedHashMap<>();
        cmp.put("存储位置", "Session 存服务端；JWT 存客户端");
        cmp.put("扩展性", "Session 多实例要共享；JWT 天然无状态");
        cmp.put("注销", "Session 删了即失效；JWT 到期前无法主动失效");
        cmp.put("体积", "Session 只传 sessionId；JWT 每次带完整 claims");
        cmp.forEach((k, v) -> System.out.printf("  %-8s %s%n", k, v));
    }

    static String createToken(long userId, String name, String role, int expireSeconds) throws Exception {
        return createTokenWithSecret(userId, name, role, expireSeconds, SECRET);
    }

    static String createTokenWithSecret(long userId, String secret, int expireSeconds) throws Exception {
        return createTokenWithSecret(userId, "张三", "user", expireSeconds, secret);
    }

    static String createTokenWithSecret(long userId, String name, String role,
                                        int expireSeconds, String secret) throws Exception {
        long now = System.currentTimeMillis() / 1000;
        String header = B64E.encodeToString(
                "{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String payload = B64E.encodeToString(("{\"userId\":" + userId
                + ",\"name\":\"" + name + "\",\"role\":\"" + role + "\""
                + ",\"iat\":" + now + ",\"exp\":" + (now + expireSeconds) + "}")
                .getBytes(StandardCharsets.UTF_8));
        String signingInput = header + "." + payload;
        String signature = B64E.encodeToString(hmac(signingInput, secret));
        return signingInput + "." + signature;
    }

    /** 验签 + 校验过期，返回 claims */
    static Map<String, Object> verify(String token) throws Exception {
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new SecurityException("token 格式错误");
        }
        String expected = B64E.encodeToString(hmac(parts[0] + "." + parts[1], SECRET));
        // 用 MessageDigest.isEqual 做定长比较，避免时序攻击
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                parts[2].getBytes(StandardCharsets.UTF_8))) {
            throw new SecurityException("签名校验失败：token 被篡改或 secret 不一致");
        }
        Map<String, Object> claims = parseClaims(decode(parts[1]));
        long exp = ((Number) claims.get("exp")).longValue();
        long now = System.currentTimeMillis() / 1000;
        if (now > exp) {
            throw new SecurityException("token 已过期，exp=" + exp + "，now=" + now);
        }
        return claims;
    }

    static byte[] hmac(String data, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    }

    static String decode(String b64) {
        return new String(B64D.decode(b64), StandardCharsets.UTF_8);
    }

    /** 极简 JSON 解析：只处理本课生成的扁平结构 */
    static Map<String, Object> parseClaims(String json) {
        Map<String, Object> map = new LinkedHashMap<>();
        String body = json.substring(1, json.length() - 1);
        for (String kv : body.split(",")) {
            String[] p = kv.split(":", 2);
            String key = p[0].replace("\"", "").trim();
            String val = p[1].trim();
            if (val.startsWith("\"")) {
                map.put(key, val.replace("\"", ""));
            } else {
                map.put(key, Long.parseLong(val));
            }
        }
        return map;
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 手写一遍 JWT 的三段结构，说明为什么 payload 不能放密码。
 * 2. 为什么签名要用 `HMAC` 而不是 `MD5(token)`？两者在防篡改上的差别是什么？
 * 3. JWT 无法主动失效，设计一个"用户改密码后立即让旧 token 失效"的方案（提示：Redis 存 token 版本号或 jti 黑名单）。
 * ============================================================
 */
