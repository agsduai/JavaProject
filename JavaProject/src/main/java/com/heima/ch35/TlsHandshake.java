/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：35-07
 * 课名：HTTPS 与 TLS 握手
 * ------------------------------------------------------------
 * 知识点：
 *   - HTTPS = HTTP + TLS。它解决三件事：**机密性**（对称加密传数据）、**完整性**（MAC/AEAD 防篡改）、**身份认证**（证书证明「这个公钥真属于这个域名」）。缺了第三件，前两件都能被中间人轻易破解。
 *   - **为什么用混合加密**：非对称加密（RSA/ECDHE）慢，只用来协商出会话密钥；真正传数据用对称加密（AES-GCM/ChaCha20-Poly1305），快几十到几百倍。
 *   - **证书链**：服务器证书 ← 中间 CA ← 根 CA。浏览器/系统内置根证书，逐级验签直到信任锚。任何一级签名不对、域名不匹配、过期，都会报错。
 *   - **证书里有什么**：域名（SAN）、公钥、有效期、签发者、签名算法、扩展（是否 CA、用途）。**CN 字段已废弃**，现代浏览器只看 SAN。
 *   - **TLS 1.2 握手（ECDHE）**：ClientHello（支持的套件 + 随机数 + 密钥交换参数）→ ServerHello + Certificate + ServerKeyExchange + ServerHelloDone → ClientKeyExchange + ChangeCipherSpec + Finished → 服务端 Finished。**2-RTT**。
 *   - **TLS 1.3 握手**：ClientHello 里直接带 key_share，服务端回 ServerHello + 证书 + Finished，**1-RTT** 就能发应用数据。而且删掉了 RSA 密钥交换、CBC 模式、RC4、SHA-1 等一堆历史包袱。
 *   - **前向保密（forward secrecy）**：用 ECDHE 而非 RSA 密钥交换，每次会话的私钥用完即弃。即使服务器长期私钥将来泄露，也解不开历史流量。这是 TLS 1.3 强制 ECDHE 的原因。
 *   - **SNI**：一个 IP 上托管多个 HTTPS 站点时，客户端在 ClientHello 里用 SNI 明文中说明要访问哪个域名 —— 这是隐私缺口，所以有 ECH（Encrypted Client Hello）。
 *   - **HSTS**：响应头 `Strict-Transport-Security: max-age=31536000; includeSubDomains; preload` 告诉浏览器「以后这个域名只用 HTTPS」，防止 SSL 剥离攻击。首次访问仍是 HTTP，所以有 preload 列表。
 *   - **常见配置错误**：证书链不完整（部分客户端能过、安卓报错）；用了 CBC 模式（BEAST/POODLE）；开着 TLS 1.0/1.1；私钥权限过宽；把 `verify=false` 写进生产代码。
 *   - 易错点：以为 HTTPS 能防「服务器被黑」（不能，它只保护传输）；以为证书验证只是「有没有证书」（要验签、验域名、验有效期、验吊销）；把 `InsecureSkipVerify` 当成解决自签证书的正式方案。
 * ============================================================
 */

package com.heima.ch35;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class TlsHandshake {
    /** 极简 DH 参数：真实用 RFC 7919 的 2048 位素数与生成元，这里为了可读性用小素数 */
    static final BigInteger P = BigInteger.valueOf(1_000_003);
    static final BigInteger G = BigInteger.valueOf(5);

    record Cert(String subject, String san, String issuer, long notAfterEpochSec,
                BigInteger publicKey, boolean ca) {
    }

    static class Ca {
        final String name;
        final BigInteger privateKey;
        final BigInteger publicKey;

        Ca(String name, BigInteger privateKey) {
            this.name = name;
            this.privateKey = privateKey;
            this.publicKey = G.modPow(privateKey, P);
        }

        Cert issue(String san, BigInteger subjectKey, long notAfter) {
            return new Cert(san, san, name, notAfter, subjectKey, false);
        }
    }

    /** 用对称密钥做一次真实的加解密演示（XOR 只是演示，真实用 AES-GCM）
     *  密文必须以 byte[] 传递：中途转成 String 会被 UTF-8 解码改写，解不回来 */
    static byte[] xor(byte[] in, byte[] key) {
        byte[] out = new byte[in.length];
        for (int i = 0; i < in.length; i++) {
            out[i] = (byte) (in[i] ^ key[i % key.length]);
        }
        return out;
    }

    static byte[] sha256(String s) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static void keyExchange() {
        System.out.println("【ECDHE 的数学本质：双方各自算，结果相同】");
        BigInteger serverPriv = BigInteger.valueOf(48271);
        BigInteger clientPriv = BigInteger.valueOf(16807);

        BigInteger serverPub = G.modPow(serverPriv, P);
        BigInteger clientPub = G.modPow(clientPriv, P);
        System.out.printf("  server 私钥 a=%d，公开 A=g^a mod p=%d%n", serverPriv, serverPub);
        System.out.printf("  client 私钥 b=%d，公开 B=g^b mod p=%d%n", clientPriv, clientPub);

        BigInteger serverSecret = clientPub.modPow(serverPriv, P);
        BigInteger clientSecret = serverPub.modPow(clientPriv, P);
        System.out.printf("  server 算 B^a=%d%n  client 算 A^b=%d%n", serverSecret, clientSecret);
        System.out.println("  相同 -> 中间人只看到 A、B，反推 a/b 要解离散对数，计算上不可行");
        System.out.println("  每次会话都换一对临时密钥，所以叫「临时」ECDHE：前向保密靠它");
        System.out.println();

        byte[] sessionKey = sha256(serverSecret.toString());
        String plain = "POST /pay HTTP/1.1 ... card=6222****1234";
        byte[] cipher = xor(plain.getBytes(StandardCharsets.UTF_8), sessionKey);
        byte[] recovered = xor(cipher, sessionKey);
        System.out.printf("  会话密钥（SHA-256 前 8 字节）：%s%n",
                HexFormat.of().formatHex(sessionKey).substring(0, 16));
        System.out.println("  明文：" + plain);
        System.out.println("  密文（Base64）：" + java.util.Base64.getEncoder().encodeToString(cipher));
        System.out.println("  解密：" + new String(recovered, StandardCharsets.UTF_8));
        System.out.println("  密文必须以 byte[] 传递：先转 String 再转回来会被 UTF-8 解码改坏");
        System.out.println("  真实协议用 AES-GCM：同时给机密性和完整性，篡改一个字节就解不开");
        System.out.println();
    }

    static void verifyChain() {
        System.out.println("【证书链校验：四道关】");
        Ca root = new Ca("GlobalRoot CA", BigInteger.valueOf(999_331));
        Ca inter = new Ca("GlobalRoot Server CA", BigInteger.valueOf(777_733));

        BigInteger leafKey = G.modPow(BigInteger.valueOf(123_457), P);
        Cert leaf = inter.issue("shop.example.com", leafKey, 1_800_000_000L);

        System.out.printf("  leaf  subject=%s  issuer=%s%n", leaf.subject(), leaf.issuer());
        System.out.println("  中间 CA 名称=" + inter.name + "，根 CA 名称=" + root.name);

        boolean chainOk = leaf.issuer().equals(inter.name);
        System.out.println("  1) 链是否连得上：" + (chainOk ? "是" : "否"));
        boolean domainOk = leaf.san().equals("shop.example.com");
        System.out.println("  2) 域名是否匹配 SAN：" + (domainOk ? "是" : "否"));
        boolean timeOk = 1_700_000_000L < leaf.notAfterEpochSec();
        System.out.println("  3) 是否在有效期内：" + (timeOk ? "是" : "否"));
        boolean caOk = !leaf.ca() && inter.name.contains("CA");
        System.out.println("  4) 用途是否正确（leaf 不能是 CA）：" + (caOk ? "是" : "否"));

        System.out.println("  常见故障：只发了 leaf 没发中间证书 -> 浏览器能自动补，安卓和 curl 直接失败");
        System.out.println("  只看 CN 不看 SAN -> 通配符、多域名场景直接误判");
        System.out.println();
    }

    static void tls12Vs13() {
        System.out.println("【握手轮次对比】");
        String[][] t12 = {
                {"1", "ClientHello：套件列表 + client_random + ECDHE 参数"},
                {"1", "ServerHello + Certificate + ServerKeyExchange + ServerHelloDone"},
                {"2", "ClientKeyExchange + ChangeCipherSpec + Finished"},
                {"2", "ChangeCipherSpec + Finished  -> 可以发应用数据"},
        };
        String[][] t13 = {
                {"1", "ClientHello：套件 + key_share（直接带 ECDHE 公钥）+ 支持的组"},
                {"1", "ServerHello + {EncryptedExtensions, Certificate, CertVerify, Finished}  -> 可发数据"},
                {"2", "客户端 Finished + 应用数据（也可在 1-RTT 内一起发）"},
        };
        System.out.println("  TLS 1.2（ECDHE，2-RTT）：");
        for (String[] r : t12) {
            System.out.printf("    RTT %s  %s%n", r[0], r[1]);
        }
        System.out.println("  TLS 1.3（1-RTT）：");
        for (String[] r : t13) {
            System.out.printf("    RTT %s  %s%n", r[0], r[1]);
        }
        System.out.println("  TLS 1.3 删掉的：RSA 密钥交换（无前向保密）、CBC、RC4、SHA-1、压缩、重协商");
        System.out.println("  0-RTT：会话恢复时第一个包就带数据，但可被重放，只该用于幂等请求");
        System.out.println();
    }

    static void hstsAndSni() {
        System.out.println("【HSTS 与 SNI】");
        System.out.println("  没有 HSTS：用户输 example.com -> HTTP 明文 -> 中间人 302 到钓鱼站");
        System.out.println("  有 HSTS：浏览器本地记住「只用 HTTPS」，直接跳过明文那一步");
        System.out.println("  Strict-Transport-Security: max-age=31536000; includeSubDomains; preload");
        System.out.println("  首次访问无法防（还没拿到 HSTS 头），所以有浏览器内置的 preload 列表");
        System.out.println();
        System.out.println("  SNI：一个 IP 多个站点，ClientHello 里明文写域名，服务器据此选证书");
        System.out.println("  副作用：旁观者能看出你访问哪个站点；对策是 ECH（加密 ClientHello）");
        System.out.println();
    }

    static void checklist() {
        System.out.println("【上线检查清单】");
        Map<String, String> items = new LinkedHashMap<>();
        items.put("协议版本", "只开 TLS 1.2 / 1.3，关掉 1.0、1.1、SSLv3");
        items.put("套件", "优先 AEAD：TLS_AES_128_GCM_SHA256、TLS_CHACHA20_POLY1305_SHA256");
        items.put("密钥交换", "只用 ECDHE，保证前向保密");
        items.put("证书链", "服务器必须下发 leaf + 中间证书，别指望客户端补");
        items.put("HSTS", "max-age 至少半年，确认全站 HTTPS 后再加 includeSubDomains");
        items.put("客户端校验", "生产代码禁止 trustAll / InsecureSkipVerify");
        items.put("私钥", "权限 600，放密钥管理服务，别进 Git");
        items.forEach((k, v) -> System.out.printf("  %-10s %s%n", k, v));
        System.out.println();
    }

    public static void main(String[] args) {
        keyExchange();
        verifyChain();
        tls12Vs13();
        hstsAndSni();
        checklist();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 把 `keyExchange` 改成三组不同素数，验证「p 太小会被暴力破解」：写一个循环从 1 试到 p，找出私钥，说明为什么真实场景要求 2048 位以上。
 * 2. 实现 `verifyChain(List<Cert> chain, String host, long now)`：逐级校验 issuer 是否等于上一级 subject、是否过期、leaf 是否 CA，返回第一个失败原因。
 * 3. 模拟中间人：把 `clientPub` 替换成攻击者的公钥，说明为什么没有证书校验时双方仍会「成功」协商出不同的密钥，以及证书校验如何阻断这一步。
 * 4. 思考题：为什么 TLS 1.3 的 0-RTT 数据可能被重放？如果支付接口接受 0-RTT 的 POST，会有什么后果？
 * ============================================================
 */
