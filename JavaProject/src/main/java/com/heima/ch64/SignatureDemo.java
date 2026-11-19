/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：64-03
 * 课名：API Key、签名与防重放
 * ------------------------------------------------------------
 * 知识点：
 *   - **API Key 是最简单的鉴权**：`X-API-Key: xxx`——**标识调用方**但不加密内容（**必须走 HTTPS**）；适合"低敏感 + 高并发"的服务端调用（64-02 的客户端凭证的简化版）。
 *   - **签名（Signature）解决"内容不可篡改 + 身份可验证"**：对"请求参数 + 时间戳 + nonce + 密钥"做 HMAC 签名（60-02 的 JWT 同款算法），服务端重算比对——**参数被改则签名不符**；**与 51-02 的支付渠道签名同一机制**。
 *   - **防重放的三要素**：**时间戳**（请求有效期 5 分钟，过期拒绝）+ **nonce**（一次性随机串，服务端去重表/Redis TTL）+ **签名覆盖这两项**——**缺一个都能被重放**（只签名不防重放 = 攻击者可原样重发）。
 *   - **密钥的层级**：**AppKey（公开）+ AppSecret（保密）**；**AppSecret 只存服务端**（前端绝不能有，36 章的密钥纪律）；**密钥轮换**（双密钥并行期 + 失效旧密钥）；**泄露应急预案**（立即禁用 + 审计该密钥的历史调用）。
 *   - **签名串的构造要固定**：参数按字典序排序 + 拼接 + 加盐（否则"同样参数不同顺序"签名不同，联调地狱）——**签名规范要写进文档**（64-09）。
 *   - 易错点：签名不含时间戳/nonce（可重放）；nonce 无 TTL（存储无限涨）；参数未排序（联调失败）；AppSecret 放前端（浏览器可看）；密钥泄露不轮换。
 * ============================================================
 */

package com.heima.ch64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

// ==================== 示例代码 ====================

public class SignatureDemo {
    /** 固定签名串：字典序参数 + timestamp + nonce */
    static String canonical(Map<String, String> params, long timestamp, String nonce) {
        TreeMap<String, String> sorted = new TreeMap<>(params);
        List<String> pairs = new ArrayList<>();
        sorted.forEach((k, v) -> pairs.add(k + "=" + v));
        pairs.add("timestamp=" + timestamp);
        pairs.add("nonce=" + nonce);
        return String.join("&", pairs);
    }

    static String hmac(String data, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes("UTF-8"), "HmacSHA256"));
        return Base64.getEncoder().encodeToString(mac.doFinal(data.getBytes("UTF-8")));
    }

    /** 服务端校验：签名 + 时间窗 + nonce 去重 */
    static class Server {
        static final long WINDOW_MS = 5 * 60_000;
        final String secret;
        final Map<String, Long> usedNonce = new LinkedHashMap<>();

        Server(String secret) {
            this.secret = secret;
        }

        String verify(Map<String, String> params, long timestamp, String nonce,
                      String signature, long now) {
            if (Math.abs(now - timestamp) > WINDOW_MS) {
                return "拒绝：时间戳超出 5 分钟窗口";
            }
            String expect;
            try {
                expect = hmac(canonical(params, timestamp, nonce), secret);
            } catch (Exception e) {
                return "拒绝：签名计算失败";
            }
            if (!expect.equals(signature)) {
                return "拒绝：签名不符（参数被篡改或密钥不对）";
            }
            if (usedNonce.containsKey(nonce)) {
                return "拒绝：nonce 已使用（重放）";
            }
            usedNonce.put(nonce, now);
            return "通过";
        }
    }

    public static void main(String[] args) throws Exception {
        String secret = "app-secret-2026";
        Server server = new Server(secret);
        Map<String, String> params = new LinkedHashMap<>();
        params.put("orderId", "9001");
        params.put("amount", "6900");
        long ts = 1_000_000;
        String nonce = "n-abc123";

        String sign = hmac(canonical(params, ts, nonce), secret);
        System.out.println("【正常请求】");
        System.out.println("  签名串：" + canonical(params, ts, nonce));
        System.out.println("  " + server.verify(params, ts, nonce, sign, ts + 1000));

        System.out.println();
        System.out.println("【篡改金额后重签校验】");
        Map<String, String> tampered = new LinkedHashMap<>(params);
        tampered.put("amount", "1");
        System.out.println("  " + server.verify(tampered, ts, nonce, sign, ts + 1000));

        System.out.println();
        System.out.println("【原样重放（同 nonce）】");
        System.out.println("  " + server.verify(params, ts, nonce, sign, ts + 2000));

        System.out.println();
        System.out.println("【过期时间戳】");
        System.out.println("  " + server.verify(params, ts, "n-new", sign, ts + 10 * 60_000));

        System.out.println();
        System.out.println("【新 nonce 的新请求】");
        String sign2 = hmac(canonical(params, ts + 3000, "n-def456"), secret);
        System.out.println("  " + server.verify(params, ts + 3000, "n-def456", sign2, ts + 4000));
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现签名校验服务：参数排序 + timestamp + nonce + HMAC，验证"篡改/重放/过期"三类攻击被拒。
 * 2. 实现密钥轮换：双密钥并行期内新旧密钥都能验签，旧密钥下线后旧签名被拒，输出迁移时间线。
 * 3. 思考题：为什么"只做签名不做防重放"不安全？构造一个"抓包原样重发"的攻击时序说明。
 * ============================================================
 */
