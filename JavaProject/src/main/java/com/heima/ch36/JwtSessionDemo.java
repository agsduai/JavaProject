/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：36-07
 * 课名：JWT 与 Session 安全
 * ------------------------------------------------------------
 * 知识点：
 *   - **Session 是「服务端有状态」**：登录后服务端存一份会话（内存/Redis），浏览器只拿一个随机 `SESSIONID`。好处是可随时吊销、能存任意数据；代价是集群要共享存储（Redis），且有状态。
 *   - **JWT 是「客户端有状态」**：把用户信息 + 签名打包成一个字符串发给客户端，服务端不存。好处是无状态、好扩展；代价是**签发后无法撤销**（除非引入黑名单，那又变成有状态了）。
 *   - **JWT 三段式**：`base64url(header).base64url(payload).base64url(signature)`。前两段只是 **Base64 编码，不是加密** —— 任何人都能解开看内容，所以**绝不能往 payload 里放密码、身份证号等敏感数据**。
 *   - **签名才是防篡改的东西**：`signature = HMAC-SHA256(base64(header) + "." + base64(payload), secret)`（对称）或 RSA/ECDSA 私钥签名（非对称）。校验时要**重算签名并常量时间比较**，不能只解出 payload 就用。
 *   - **`alg: none` 是经典大坑**：库如果信任头部的 `alg` 字段，攻击者改成 `"alg":"none"` 并去掉签名就能伪造。防护：**服务端固定允许的算法白名单**，绝不读客户端的 alg 决定用什么验证。
 *   - **对称密钥泄露 = 所有人都能签发 token**：HS256 的 secret 必须 ≥ 256 位随机（`SecureRandom`），不能是 `"secret"`、项目名、配置里的默认值。多服务共享密钥时更要走密钥管理（KMS/Vault），支持轮换。
 *   - **一定要校验的字段**：`exp`（过期时间，必须短，如 15 分钟）、`nbf`（生效时间）、`iss`（签发者）、`aud`（受众，防止 A 系统的 token 用在 B 系统）、`sub`（主体）。跳过任何一个都可能导致「过期 token 能用」「跨系统串用」。
 *   - **access token 短 + refresh token 长**是标准组合：access 15 分钟无状态快速校验；refresh 存服务端（可撤销、可轮换、绑定设备），用于换新 access。refresh 只能用一次（一次性轮换），旧的就作废。
 *   - **放哪里**：JWT 放 `Authorization: Bearer` 头优于放 Cookie（不吃 CSRF）；若必须放 Cookie，则要 `HttpOnly + Secure + SameSite`，并额外加 CSRF token。
 *   - **别把 JWT 当 session 硬套**：需要「立刻踢下线」「查看在线设备」「改权限立即生效」的场景，纯 JWT 做不到，要么短过期 + 黑名单，要么老老实实用带状态的 Session。
 *   - 易错点：把敏感信息塞 payload；secret 用默认值或提交进 Git；`exp` 设成一年；不校验 `aud`/`iss`；客户端解析 payload 就信任里面的 role（必须以服务端校验签名为准）；token 出现在 URL/日志里。
 * ============================================================
 */

package com.heima.ch36;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

// ==================== 示例代码 ====================

public class JwtSessionDemo {
    static final SecureRandom RANDOM = new SecureRandom();

    /** 极简 JWT：HS256 签名，仅用于教学，真实项目用 nimbus-jose-jwt / jjwt */
    static class Jwt {
        static String b64url(byte[] b) {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
        }

        static byte[] b64urlDecode(String s) {
            return Base64.getUrlDecoder().decode(s);
        }

        static byte[] hmacSha256(String secret, String data) {
            try {
                Mac mac = Mac.getInstance("HmacSHA256");
                mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
                return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        static String sign(String secret, String headerJson, String payloadJson) {
            String h = b64url(headerJson.getBytes(StandardCharsets.UTF_8));
            String p = b64url(payloadJson.getBytes(StandardCharsets.UTF_8));
            String sig = b64url(hmacSha256(secret, h + "." + p));
            return h + "." + p + "." + sig;
        }

        static String[] split(String token) {
            String[] parts = token.split("\\.", -1);
            if (parts.length != 3) {
                throw new IllegalArgumentException("格式错误：段数=" + parts.length);
            }
            return parts;
        }

        /** 只看内容，不验签 —— 必须清楚这一步是不可信的 */
        static String peekPayload(String token) {
            return new String(b64urlDecode(split(token)[1]), StandardCharsets.UTF_8);
        }

        static boolean constantTimeEquals(byte[] a, byte[] b) {
            if (a.length != b.length) {
                return false;
            }
            int diff = 0;
            for (int i = 0; i < a.length; i++) {
                diff |= a[i] ^ b[i];
            }
            return diff == 0;
        }

        /** 正确校验：固定算法 + 重算签名 + 校验 exp/aud/iss */
        static String verify(String secret, String token, String expectedAud, long nowSec) {
            String[] parts = split(token);
            String header = new String(b64urlDecode(parts[0]), StandardCharsets.UTF_8);
            if (!header.contains("\"alg\":\"HS256\"")) {
                return "拒绝：算法不在白名单（只接受 HS256）";
            }
            byte[] expected = hmacSha256(secret, parts[0] + "." + parts[1]);
            byte[] actual = b64urlDecode(parts[2]);
            if (!constantTimeEquals(expected, actual)) {
                return "拒绝：签名不匹配（已被篡改）";
            }
            String payload = new String(b64urlDecode(parts[1]), StandardCharsets.UTF_8);
            long exp = readLong(payload, "\"exp\":");
            long nbf = readLong(payload, "\"nbf\":");
            String aud = readString(payload, "\"aud\":\"");
            String iss = readString(payload, "\"iss\":\"");
            if (nowSec < nbf) {
                return "拒绝：尚未生效（nbf）";
            }
            if (nowSec > exp) {
                return "拒绝：已过期（exp）";
            }
            if (!expectedAud.equals(aud)) {
                return "拒绝：受众不符（aud=" + aud + "）";
            }
            if (!"auth.example.com".equals(iss)) {
                return "拒绝：签发者不符（iss=" + iss + "）";
            }
            return "通过：" + payload;
        }

        static long readLong(String json, String key) {
            int i = json.indexOf(key);
            if (i < 0) {
                return 0;
            }
            int j = i + key.length();
            int k = j;
            while (k < json.length() && (Character.isDigit(json.charAt(k)) || json.charAt(k) == '-')) {
                k++;
            }
            return Long.parseLong(json.substring(j, k));
        }

        static String readString(String json, String key) {
            int i = json.indexOf(key);
            if (i < 0) {
                return "";
            }
            int j = i + key.length();
            int k = json.indexOf('"', j);
            return json.substring(j, k);
        }
    }

    static final String SECRET = newSecret();

    static String newSecret() {
        byte[] b = new byte[32];
        RANDOM.nextBytes(b);
        return Base64.getEncoder().encodeToString(b);
    }

    static void structure() {
        System.out.println("【JWT 长什么样：三段，前两段只是 Base64】");
        String token = Jwt.sign(SECRET, "{\"alg\":\"HS256\",\"typ\":\"JWT\"}",
                "{\"sub\":\"user-1001\",\"role\":\"user\",\"iss\":\"auth.example.com\","
                        + "\"aud\":\"api.example.com\",\"nbf\":1000,\"exp\":1900}");
        String[] parts = Jwt.split(token);
        System.out.println("  header  : " + new String(Jwt.b64urlDecode(parts[0]), StandardCharsets.UTF_8));
        System.out.println("  payload : " + new String(Jwt.b64urlDecode(parts[1]), StandardCharsets.UTF_8));
        System.out.println("  sig     : " + parts[2].substring(0, 20) + "...");
        System.out.println("  注意：payload 用任何一个 base64 工具都能解开，不是加密");
        System.out.println("  -> 所以绝不放密码、身份证、手机号");
        System.out.println();
    }

    static void tamperDetection() {
        System.out.println("【签名防篡改：改一个字符就验不过】");
        String token = Jwt.sign(SECRET, "{\"alg\":\"HS256\",\"typ\":\"JWT\"}",
                "{\"sub\":\"user-1001\",\"role\":\"user\",\"iss\":\"auth.example.com\","
                        + "\"aud\":\"api.example.com\",\"nbf\":1000,\"exp\":1900}");
        String[] parts = Jwt.split(token);
        // 攻击者把 role 从 user 改成 admin
        String evil = Jwt.sign(SECRET, "{\"alg\":\"HS256\",\"typ\":\"JWT\"}",
                "{\"sub\":\"user-1001\",\"role\":\"admin\",\"iss\":\"auth.example.com\","
                        + "\"aud\":\"api.example.com\",\"nbf\":1000,\"exp\":1900}");
        System.out.println("  正常 token 校验 -> " + Jwt.verify(SECRET, token, "api.example.com", 1500));
        System.out.println();
        System.out.println("  把 payload 换成 role=admin，但不重算签名（保留原 sig）：");
        String forged = parts[0] + "." + Jwt.split(evil)[1] + "." + parts[2];
        System.out.println("    " + Jwt.verify(SECRET, forged, "api.example.com", 1500));
        System.out.println("  换了 secret 重新签：");
        String otherKey = "attacker-key-not-the-real-one-attacker-key-1234";
        String forged2 = Jwt.sign(otherKey, "{\"alg\":\"HS256\",\"typ\":\"JWT\"}",
                "{\"sub\":\"user-1001\",\"role\":\"admin\",\"iss\":\"auth.example.com\","
                        + "\"aud\":\"api.example.com\",\"nbf\":1000,\"exp\":1900}");
        System.out.println("    " + Jwt.verify(SECRET, forged2, "api.example.com", 1500));
        System.out.println();
    }

    static void algNoneAttack() {
        System.out.println("【alg=none：信任头部算法的后果】");
        String header = Jwt.b64url("{\"alg\":\"none\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String payload = Jwt.b64url(("{\"sub\":\"user-1001\",\"role\":\"admin\","
                + "\"iss\":\"auth.example.com\",\"aud\":\"api.example.com\","
                + "\"nbf\":1000,\"exp\":9999999999}").getBytes(StandardCharsets.UTF_8));
        String token = header + "." + payload + ".";
        System.out.println("  伪造 token：" + token.substring(0, 50) + "...（签名段为空）");
        System.out.println("  我们的实现：" + Jwt.verify(SECRET, token, "api.example.com", 1500));
        System.out.println("  不校验算法的库会直接解出 role=admin 并放行");
        System.out.println("  防护：算法白名单写死在服务端，忽略客户端 header 里的 alg");
        System.out.println();
    }

    static void expiryAndAudience() {
        System.out.println("【必须校验的字段：exp / nbf / aud / iss】");
        String J = "{\"sub\":\"user-1001\",\"role\":\"user\",\"iss\":\"auth.example.com\","
                + "\"aud\":\"api.example.com\",\"nbf\":1000,\"exp\":1900}";
        String token = Jwt.sign(SECRET, "{\"alg\":\"HS256\",\"typ\":\"JWT\"}", J);
        System.out.printf("  now=1500（有效期内）   -> %s%n", Jwt.verify(SECRET, token, "api.example.com", 1500));
        System.out.printf("  now= 500（早于 nbf）   -> %s%n", Jwt.verify(SECRET, token, "api.example.com", 500));
        System.out.printf("  now=2000（晚于 exp）   -> %s%n", Jwt.verify(SECRET, token, "api.example.com", 2000));
        System.out.printf("  aud 传错（另一个系统）  -> %s%n", Jwt.verify(SECRET, token, "billing.example.com", 1500));
        System.out.println("  少校验任何一个，都会留下「过期能用」「跨系统串用」的口子");
        System.out.println();
    }

    static void sessionVsJwt() {
        System.out.println("【Session 还是 JWT：按需求选】");
        String[][] rows = {
                {"撤销 / 踢下线", "随时，删掉即失效", "做不到（除非加黑名单）"},
                {"水平扩展", "需共享存储（Redis）", "无状态，天然好扩展"},
                {"存储开销", "服务端每会话一条", "服务端不存，客户端每请求带"},
                {"能存的数据", "任意，只在服务端", "少量、非敏感（会暴露）"},
                {"篡改风险", "低（只有随机 ID）", "靠签名，实现错就危险"},
                {"适合场景", "后台管理、需要即时踢人", "开放 API、多服务、移动端"},
        };
        for (String[] r : rows) {
            System.out.println("  " + pad(r[0], 16) + " | " + pad(r[1], 24) + " | " + r[2]);
        }
        System.out.println();
    }

    static void accessAndRefresh() {
        System.out.println("【access + refresh：短token快速验，长token可撤销】");
        System.out.println("  登录 -> 发 access(15min, 无状态) + refresh(7天, 服务端存)");
        System.out.println("  每次请求带 access，服务端只验签不查库，快");
        System.out.println("  access 过期 -> 用 refresh 换新 access + 新 refresh（一次性轮换）");
        System.out.println("  旧 refresh 用过即作废；检测到旧 refresh 被复用 -> 整条会话注销（被盗信号）");
        System.out.println("  退出登录 / 改密码 -> 删服务端的 refresh，立刻生效");
        System.out.println();
        Map<String, String> sessions = new LinkedHashMap<>();
        sessions.put("refresh-abc", "user-1001");
        System.out.println("  服务端存的 refresh 列表：" + sessions.keySet());
        sessions.remove("refresh-abc");
        System.out.println("  用户点退出后：" + sessions.keySet() + "  -> access 15 分钟内仍有效，refresh 已死");
        System.out.println();
    }

    static void whereToStore() {
        System.out.println("【token 放哪里】");
        String[][] rows = {
                {"Authorization: Bearer", "不吃 CSRF，移动端友好", "推荐给 API 调用"},
                {"HttpOnly Cookie", "JS 读不到，防 XSS 窃取", "要配 SameSite + CSRF token"},
                {"localStorage", "方便", "XSS 一来 token 直接丢，不推荐"},
                {"URL 参数", "省事", "进日志/Referer/历史，禁止"},
        };
        for (String[] r : rows) {
            System.out.println("  " + pad(r[0], 24) + " | " + pad(r[1], 26) + " | " + r[2]);
        }
        System.out.println();
    }

    static void commonMistakes() {
        System.out.println("【JWT 高频错误清单】");
        String[] items = {
                "secret 是默认值/项目名/提交进 Git -> 谁都能签",
                "HS256 密钥太短（< 32 字节）-> 可被离线爆破",
                "放敏感信息进 payload（payload 只是编码）",
                "exp 设一年，token 泄露等于长期有效",
                "不校验 aud/iss -> A 系统 token 用于 B 系统",
                "信任客户端传来的 role，不重新从库里查权限",
                "算法取客户端 header 的 alg -> alg=none 伪造",
                "把 JWT 当万能方案，却要求「立刻踢下线」",
        };
        int i = 1;
        for (String s : items) {
            System.out.printf("  %d. %s%n", i++, s);
        }
        System.out.println();
    }

    static String pad(String s, int width) {
        int w = 0;
        for (int i = 0; i < s.length(); i++) {
            w += s.charAt(i) > 0x2E80 ? 2 : 1;
        }
        return s + " ".repeat(Math.max(width - w, 0));
    }

    public static void main(String[] args) {
        structure();
        tamperDetection();
        algNoneAttack();
        expiryAndAudience();
        sessionVsJwt();
        accessAndRefresh();
        whereToStore();
        commonMistakes();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 给 `Jwt.verify` 增加「黑名单」检查：维护一个已注销 `jti` 集合，实现「签发后仍能撤销」的折中方案，并分析它带来的状态回退。
 * 2. 实现 refresh token 的一次性轮换：记录 `jti`，检测到同一 `jti` 被用第二次时判定为泄露并注销整个会话链。
 * 3. 写一段代码说明「为什么 payload 不是加密的」：用 Base64 解码一个真实 JWT 的 payload 并打印。
 * 4. 思考题：多服务共享一个 HS256 secret 有哪些风险？改用 RS256/ES256 非对称签名能解决什么、又引入什么开销？
 * ============================================================
 */
