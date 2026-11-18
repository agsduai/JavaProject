/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：26-09
 * 课名：登录鉴权闭环
 * ------------------------------------------------------------
 * 知识点：
 *   - 完整流程：`POST /login`（账号+密码）-> 校验密码 -> 签发 token -> 返回给前端 -> 前端存起来（内存/localStorage）-> 后续请求带 `Authorization: Bearer <token>` -> 拦截器验签 -> 放行并把用户信息放入上下文。
 *   - 密码存储：**绝不能明文**，也不能只用 MD5（彩虹表可查）。正确做法是加盐哈希 + 慢哈希（BCrypt、Argon2），每次盐不同，同一密码两次哈希结果不同。
 *   - 登录接口本身要放行，其它接口默认拦截；用白名单（`/login`、`/register`、静态资源）而不是黑名单。
 *   - 令牌放哪：`localStorage` 简单但会被 XSS 读取；`HttpOnly Cookie` 能防 XSS 但要注意 CSRF。移动端/前后端分离项目常用 `Authorization` 头。
 *   - 刷新机制：access token 短期（30min）+ refresh token 长期（7 天，存 Redis 可撤销）。access 过期时用 refresh 换新的，避免频繁重新登录。
 *   - 登出：无状态 token 无法真正失效，通常把 jti 或 token 加入 Redis 黑名单直到其自然过期，或维护"用户 token 版本号"。
 *   - 易错：登录接口返回用户密码字段（序列化时要用 `@JsonIgnore`）；token 放在 URL 参数里（会进日志和浏览器历史）；没校验 token 的 `alg`。
 * ============================================================
 */

package com.heima.ch26;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

// ==================== 示例代码 ====================

public class LoginFlow {
    /** 模拟用户表：username -> (salt, passwordHash) */
    static final Map<String, String[]> USERS = new LinkedHashMap<>();
    static final Base64.Encoder B64 = Base64.getEncoder();

    public static void main(String[] args) throws Exception {
        // 注册：密码加盐 + PBKDF2 慢哈希
        register("zhangsan", "Passw0rd!");
        register("lisi", "123456");

        System.out.println("=== 注册后存的是什么 ===");
        for (Map.Entry<String, String[]> e : USERS.entrySet()) {
            System.out.println("  " + e.getKey() + " salt=" + e.getValue()[0]
                    + " hash=" + e.getValue()[1].substring(0, 24) + "...");
        }

        System.out.println();
        System.out.println("=== 登录成功 ===");
        Map<String, String> resp = login("zhangsan", "Passw0rd!");
        System.out.println("  " + resp);
        String token = resp.get("token");

        System.out.println();
        System.out.println("=== 登录失败：密码错误 ===");
        System.out.println("  " + login("zhangsan", "wrong"));

        System.out.println();
        System.out.println("=== 用户不存在（提示要模糊，不能告诉攻击者账号是否存在）===");
        System.out.println("  " + login("nobody", "whatever"));

        System.out.println();
        System.out.println("=== 带 token 访问受保护接口 ===");
        System.out.println("  " + access(token, "/api/user/profile"));

        System.out.println();
        System.out.println("=== 不带 token ===");
        System.out.println("  " + access(null, "/api/user/profile"));

        System.out.println();
        System.out.println("=== 密码为什么不能用 MD5 ===");
        String md5a = md5("123456");
        String md5b = md5("123456");
        System.out.println("  MD5 两次结果相同：" + md5a.equals(md5b) + " -> " + md5a);
        System.out.println("  彩虹表里 123456 的 MD5 一查就知道原文");
        System.out.println("  PBKDF2 加盐后两次结果不同，攻击者无法批量反查");
    }

    static void register(String username, String rawPassword) throws Exception {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        String hash = pbkdf2(rawPassword, salt);
        USERS.put(username, new String[]{B64.encodeToString(salt), hash});
    }

    static Map<String, String> login(String username, String rawPassword) throws Exception {
        String[] stored = USERS.get(username);
        Map<String, String> result = new LinkedHashMap<>();
        if (stored == null) {
            result.put("code", "401");
            result.put("msg", "用户名或密码错误");
            return result;
        }
        String hash = pbkdf2(rawPassword, Base64.getDecoder().decode(stored[0]));
        if (!MessageDigest.isEqual(hash.getBytes(StandardCharsets.UTF_8),
                stored[1].getBytes(StandardCharsets.UTF_8))) {
            result.put("code", "401");
            result.put("msg", "用户名或密码错误");
            return result;
        }
        result.put("code", "200");
        result.put("msg", "登录成功");
        result.put("token", JwtMini.create(username, 1800));
        return result;
    }

    static String access(String token, String path) {
        if (token == null || token.isBlank()) {
            return "401 未登录：请先调用 /login 获取 token";
        }
        try {
            Map<String, Object> claims = JwtMini.verify(token);
            return "200 放行 " + path + "，当前用户=" + claims.get("sub");
        } catch (RuntimeException e) {
            return "401 " + e.getMessage();
        }
    }

    static String pbkdf2(String password, byte[] salt) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 10000, 256);
        SecretKeyFactory f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        return B64.encodeToString(f.generateSecret(spec).getEncoded());
    }

    static String md5(String s) throws Exception {
        return B64.encodeToString(
                MessageDigest.getInstance("MD5").digest(s.getBytes(StandardCharsets.UTF_8)));
    }

    /** 极简 JWT，仅演示闭环 */
    static class JwtMini {
        static final String SECRET = "heima-2026-demo-secret-key-32-bytes!!";
        static final Base64.Encoder E = Base64.getUrlEncoder().withoutPadding();
        static final Base64.Decoder D = Base64.getUrlDecoder();

        static String create(String username, int expireSeconds) {
            long now = System.currentTimeMillis() / 1000;
            String h = E.encodeToString("{\"alg\":\"HS256\"}".getBytes(StandardCharsets.UTF_8));
            String p = E.encodeToString(("{\"sub\":\"" + username + "\",\"exp\":"
                    + (now + expireSeconds) + "}").getBytes(StandardCharsets.UTF_8));
            return h + "." + p + "." + E.encodeToString(sign(h + "." + p));
        }

        static Map<String, Object> verify(String token) {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                throw new SecurityException("token 格式错误");
            }
            if (!MessageDigest.isEqual(E.encodeToString(sign(parts[0] + "." + parts[1]))
                    .getBytes(StandardCharsets.UTF_8), parts[2].getBytes(StandardCharsets.UTF_8))) {
                throw new SecurityException("token 无效");
            }
            String payload = new String(D.decode(parts[1]), StandardCharsets.UTF_8);
            long exp = Long.parseLong(payload.replaceAll(".*\"exp\":(\\d+).*", "$1"));
            if (System.currentTimeMillis() / 1000 > exp) {
                throw new SecurityException("token 已过期");
            }
            Map<String, Object> claims = new LinkedHashMap<>();
            claims.put("sub", payload.replaceAll(".*\"sub\":\"([^\"]+)\".*", "$1"));
            claims.put("exp", exp);
            return claims;
        }

        static byte[] sign(String data) {
            try {
                javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
                mac.init(new javax.crypto.spec.SecretKeySpec(
                        SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
                return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 为什么注册接口返回的密码哈希也不能返回给前端？说出至少两个序列化层面的防护手段。
 * 2. 设计 access token + refresh token 的完整时序，说明 refresh token 为什么要存 Redis 而 access token 不用。
 * 3. 用户修改密码后如何让已签发的 token 立刻失效？给出两种方案并比较优缺点。
 * ============================================================
 */
