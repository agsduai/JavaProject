/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：73-06
 * 课名：防盗链与签名 URL
 * ------------------------------------------------------------
 * 知识点：
 *   - **私有文件的访问控制只有一条路：签名 URL**——应用校验用户权限后，**签发一个带过期时间与签名的临时 URL**（`?expires=...&signature=...`），CDN/对象存储验签通过才允许下载——**URL 本身就是"临时通行证"**。
 *   - **签名的三要素**：**资源路径 + 过期时间 + 密钥**（HMAC 签名，64-03 同款）——**任何一项被改，签名就不符**；**过期时间必须服务端校验**（不能用客户端时间）。
 *   - **防盗链（Referer 校验）的局限**：`Referer` 头可以伪造（curl 直接带）、可以缺失（隐私模式/直接访问）——**只能防"简单盗链"，不能防恶意**；**真正防护是签名 URL**（每次访问都要服务端授权）。
 *   - **与 CDN 的配合**：**CDN 回源鉴权**（CDN 回源带签名）、**CDN 边缘鉴权**（CDN 自己验签，不消耗回源）——大流量场景用边缘鉴权。
 *   - **与 64-03 的关系**：签名 URL 就是"一次性的 API 签名"——**同样的 HMAC 机制，不同的参数**（资源路径替代请求参数）。
 *   - 易错点：签名不过期（链接永久有效，泄露即失守）；过期时间用客户端时间校验（可绕过）；密钥在 URL 里明文（应只出现签名）；只做 Referer 校验就以为安全。
 * ============================================================
 */

package com.heima.ch73;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

// ==================== 示例代码 ====================

public class SignedUrl {
    static final String SECRET = "storage-secret-key";

    static String hmac(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET.getBytes("UTF-8"), "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(data.getBytes("UTF-8")));
        } catch (Exception e) {
            throw new IllegalStateException("sign failed", e);
        }
    }

    /** 签发：path + expires 一并签名 */
    static String sign(String path, long expiresAtMs) {
        String raw = path + "|" + expiresAtMs;
        return path + "?expires=" + expiresAtMs + "&sign=" + hmac(raw);
    }

    /** 校验：路径/过期/签名 */
    static String verify(String url, long nowMs) {
        int q = url.indexOf('?');
        if (q < 0) {
            return "拒绝：缺少签名参数";
        }
        String path = url.substring(0, q);
        Map<String, String> params = new LinkedHashMap<>();
        for (String kv : url.substring(q + 1).split("&")) {
            String[] p = kv.split("=", 2);
            params.put(p[0], p.length > 1 ? p[1] : "");
        }
        if (!params.containsKey("expires") || !params.containsKey("sign")) {
            return "拒绝：参数不完整";
        }
        long expires;
        try {
            expires = Long.parseLong(params.get("expires"));
        } catch (NumberFormatException e) {
            return "拒绝：expires 非法";
        }
        if (nowMs > expires) {
            return "拒绝：已过期（" + (nowMs - expires) / 1000 + " 秒前）";
        }
        String expect = hmac(path + "|" + expires);
        if (!expect.equals(params.get("sign"))) {
            return "拒绝：签名不符（路径或过期时间被改）";
        }
        return "通过：允许下载 " + path;
    }

    /** 防盗链：Referer 校验（仅辅助） */
    static String refererCheck(String referer, String allowedHost) {
        if (referer == null || referer.isBlank()) {
            return "Referer 缺失（可能是直接访问/隐私模式）-> 不能作为唯一防线";
        }
        if (!referer.startsWith("https://" + allowedHost)) {
            return "Referer 不符（" + referer + "）-> 拒绝";
        }
        return "Referer 通过（但可伪造，仍需签名）";
    }

    public static void main(String[] args) {
        long now = 1_000_000;
        long ttl = 300_000;

        System.out.println("【签发与校验】");
        String url = sign("private/contract-9001.pdf", now + ttl);
        System.out.println("  签发：" + url);
        System.out.println("  " + verify(url, now + 1000));

        System.out.println();
        System.out.println("【篡改路径】");
        String tampered = url.replace("contract-9001", "contract-9002");
        System.out.println("  " + verify(tampered, now + 1000));

        System.out.println();
        System.out.println("【篡改过期时间（延长）】");
        String extended = url.replace("expires=" + (now + ttl),
                "expires=" + (now + ttl * 100));
        System.out.println("  " + verify(extended, now + 1000));

        System.out.println();
        System.out.println("【过期后访问】");
        System.out.println("  " + verify(url, now + ttl + 1));

        System.out.println();
        System.out.println("【Referer 防盗链（辅助手段）】");
        System.out.println("  " + refererCheck("https://shop.example.com/detail", "shop.example.com"));
        System.out.println("  " + refererCheck("https://evil.example.net/hotlink", "shop.example.com"));
        System.out.println("  " + refererCheck(null, "shop.example.com"));

        System.out.println();
        System.out.println("【要点】TTL 5~15 分钟、服务端校验过期、路径与过期一起签名、密钥不进 URL");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现签名 URL：签发/校验/篡改检测/过期拒绝，用 4 个场景验证。
 * 2. 实现"CDN 边缘鉴权"：CDN 用同一密钥验签，验证回源请求被签名保护。
 * 3. 思考题：为什么"Referer 校验不能作为主要防线"？从"可伪造/可缺失"两个特征分析（并说明它防的是什么）。
 * ============================================================
 */
