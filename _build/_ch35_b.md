### 35-06 HTTP 缓存：强缓存与协商缓存

**知识点**
- 缓存的价值是**省掉整条链路**：不发请求 > 发请求但回 304 > 回 200。所以优化的优先级永远是「能不请求就不请求」。
- **强缓存**：浏览器直接用本地副本，**一个字节都不发给服务器**。靠 `Cache-Control: max-age=秒` 或 `Expires`（绝对时间，已过时）控制。命中时 DevTools 显示 `from disk cache` / `from memory cache`。
- **协商缓存**：本地副本过期了，但还想确认能不能复用，于是带上校验器问服务器。服务器回 `304 Not Modified`（无 body），浏览器继续用本地副本。**省的是 body 带宽，不是往返**。
- **两组校验器**：`ETag` ↔ `If-None-Match`（内容指纹，优先级高）；`Last-Modified` ↔ `If-Modified-Since`（秒级精度，文件被改回原内容也判为变了）。两者都给时以 ETag 为准。
- **`ETag` 强弱之分**：强 ETag 要求字节完全一致；弱 ETag 带 `W/` 前缀，表示语义等价即可。压缩代理会改写 body，所以它会削弱 ETag。
- **`Cache-Control` 常用指令**：`max-age=N`（新鲜期）、`s-maxage`（只对共享缓存/CDN 生效）、`no-cache`（**可以存，但每次用前必须校验**）、`no-store`（**完全不许存**，含敏感数据）、`private`（只允许浏览器存）、`public`（CDN 也可存）、`must-revalidate`（过期后必须校验，不许用旧副本）、`immutable`（内容永不变，连刷新都不校验）。
- **`no-cache` 与 `no-store` 最容易被搞混**：前者是「先问再用」，后者是「根本别存」。存密码、token、身份证号的响应必须 `no-store`。
- **`Vary` 决定缓存键的维度**：`Vary: Accept-Encoding` 表示 gzip 版和原文版是两个缓存条目；`Vary: *` 等于不可缓存。漏了 `Vary` 会把 A 用户的个性化页面喂给 B 用户。
- **缓存决策顺序**：先看 `Cache-Control`，没有才看 `Expires`，都没有则用启发式（`Last-Modified` 的 10%）。所以只给 `Last-Modified` 的响应也会被强缓存，很多人不知道。
- **最佳实践**：带内容哈希的静态资源（`app.3f9a2c.js`）用 `max-age=31536000, immutable`；HTML 入口用 `no-cache`（每次都校验）；接口数据按业务定，用户私有数据 `private, no-store` 或短 `max-age`。
- 易错点：给 `index.html` 设一年强缓存导致发版后用户白屏；以为 304 能省 RTT（不能，只省 body）；用 `Last-Modified` 做精确变更检测（秒级精度不够）。

**示例代码**
```java
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;


public class HttpCache {
    /** 一条缓存条目：内容 + 校验器 + 新鲜期 */
    static class CacheEntry {
        final String url;
        final String body;
        final String etag;
        final long lastModifiedEpochSec;
        final int maxAgeSec;
        final boolean noStore;
        final boolean noCache;
        final String vary;
        long storedAtSec;

        CacheEntry(String url, String body, String etag, long lastModifiedEpochSec,
                   int maxAgeSec, boolean noStore, boolean noCache, String vary, long storedAtSec) {
            this.url = url;
            this.body = body;
            this.etag = etag;
            this.lastModifiedEpochSec = lastModifiedEpochSec;
            this.maxAgeSec = maxAgeSec;
            this.noStore = noStore;
            this.noCache = noCache;
            this.vary = vary;
            this.storedAtSec = storedAtSec;
        }

        boolean fresh(long nowSec) {
            return !noCache && !noStore && nowSec - storedAtSec < maxAgeSec;
        }

        String cacheControl() {
            StringBuilder sb = new StringBuilder();
            if (noStore) {
                sb.append("no-store");
            } else if (noCache) {
                sb.append("no-cache");
            } else {
                sb.append("max-age=").append(maxAgeSec);
            }
            if (vary != null) {
                sb.append(", Vary: ").append(vary);
            }
            return sb.toString();
        }
    }

    /** 极简浏览器缓存 + 极简服务器 */
    static class Browser {
        final Map<String, CacheEntry> store = new LinkedHashMap<>();
        int requests;
        int bytesFromNetwork;

        void get(String url, Server server, long nowSec, Map<String, String> requestHeaders) {
            CacheEntry e = store.get(url);
            if (e == null) {
                System.out.println("  [" + url + "] 本地无缓存 -> 发请求");
                fetch(url, server, nowSec);
                return;
            }
            if (e.fresh(nowSec)) {
                System.out.printf("  [%s] 强缓存命中（剩余 %d 秒）-> 不请求，from disk cache%n",
                        url, e.maxAgeSec - (nowSec - e.storedAtSec));
                return;
            }
            System.out.printf("  [%s] 缓存已过期 -> 带校验器协商%n", url);
            Map<String, String> h = new LinkedHashMap<>(requestHeaders);
            if (e.etag != null) {
                h.put("If-None-Match", e.etag);
            }
            h.put("If-Modified-Since", String.valueOf(e.lastModifiedEpochSec));
            int code = server.handle(url, h);
            requests++;
            if (code == 304) {
                System.out.println("    服务器回 304，复用本地副本，只付了一个 RTT");
                e.storedAtSec = nowSec;
            } else {
                System.out.println("    服务器回 200，内容变了，替换本地副本");
                fetch(url, server, nowSec);
            }
        }

        private void fetch(String url, Server server, long nowSec) {
            CacheEntry e = server.serve(url);
            requests++;
            if (e.noStore) {
                System.out.println("    响应 no-store：用完即弃，不写入缓存");
                return;
            }
            e.storedAtSec = nowSec;
            store.put(url, e);
            bytesFromNetwork += e.body.length();
            System.out.printf("    200 OK，缓存策略 [%s]，body %d 字符%n", e.cacheControl(), e.body.length());
        }
    }

    static class Server {
        final Map<String, CacheEntry> resources = new LinkedHashMap<>();
        int hits200;
        int hits304;

        void put(CacheEntry e) {
            resources.put(e.url, e);
        }

        CacheEntry serve(String url) {
            hits200++;
            CacheEntry src = resources.get(url);
            return new CacheEntry(src.url, src.body, src.etag, src.lastModifiedEpochSec,
                    src.maxAgeSec, src.noStore, src.noCache, src.vary, 0);
        }

        int handle(String url, Map<String, String> headers) {
            CacheEntry src = resources.get(url);
            if (src == null) {
                hits200++;
                return 404;
            }
            String inm = headers.get("If-None-Match");
            if (inm != null && inm.equals(src.etag)) {
                hits304++;
                return 304;
            }
            String ims = headers.get("If-Modified-Since");
            if (inm == null && ims != null
                    && Long.parseLong(ims) >= src.lastModifiedEpochSec) {
                hits304++;
                return 304;
            }
            hits200++;
            return 200;
        }
    }

    static void strongCache() {
        System.out.println("【强缓存：max-age 内完全不发请求】");
        Server server = new Server();
        server.put(new CacheEntry("/logo.png", "PNG-BYTES", "\"v1\"", 1000, 3600, false, false, null, 0));
        Browser browser = new Browser();
        long now = 10_000;
        for (int i = 0; i < 3; i++) {
            System.out.printf(" t=%ds%n", now - 10_000);
            browser.get("/logo.png", server, now, Map.of());
            now += 20;
        }
        System.out.println("  t=3601s，超过 max-age");
        browser.get("/logo.png", server, 10_000 + 3601, Map.of());
        System.out.printf("  合计：网络请求 %d 次，服务器 200=%d 次 304=%d 次%n",
                browser.requests, server.hits200, server.hits304);
        System.out.println();
    }

    static void revalidate() {
        System.out.println("【协商缓存：过期后靠 ETag 拿 304】");
        Server server = new Server();
        server.put(new CacheEntry("/api/profile", "{\"name\":\"张三\"}", "\"abc123\"", 5_000, 60, false, false, null, 0));
        Browser browser = new Browser();
        long now = 20_000;
        browser.get("/api/profile", server, now, Map.of());
        now += 30;
        System.out.println("  t=+30s，仍在 max-age=60 内");
        browser.get("/api/profile", server, now, Map.of());
        now += 40;
        System.out.println("  t=+70s，已过期 -> 带 If-None-Match 协商，内容没变拿 304");
        browser.get("/api/profile", server, now, Map.of());
        System.out.println("  内容真的改了之后：");
        server.put(new CacheEntry("/api/profile", "{\"name\":\"李四\"}", "\"def456\"", 6_000, 60, false, false, null, 0));
        browser.get("/api/profile", server, now + 10, Map.of());
        System.out.printf("  服务器 200=%d 次，304=%d 次%n", server.hits200, server.hits304);
        System.out.println();
    }

    static void directives() {
        System.out.println("【Cache-Control 指令速查】");
        String[][] rows = {
                {"max-age=3600", "新鲜期 3600 秒，期内不发请求"},
                {"s-maxage=600", "只约束 CDN 等共享缓存，浏览器仍看 max-age"},
                {"no-cache", "可以存，但每次使用前必须向服务器校验（通常回 304）"},
                {"no-store", "完全不许存，敏感数据必须用它"},
                {"private", "只允许浏览器缓存，CDN 不许存"},
                {"public", "CDN 也可以缓存"},
                {"must-revalidate", "过期后必须校验，不许用陈旧副本兜底"},
                {"immutable", "内容永不改变，连强制刷新都跳过校验"},
                {"stale-while-revalidate=30", "先返回旧副本，后台异步更新，30 秒内允许"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-28s %s%n", r[0], r[1]);
        }
        System.out.println();
    }

    static void etagVsLastModified() {
        System.out.println("【ETag 与 Last-Modified 的差别】");
        System.out.println("  Last-Modified 只到秒：1 秒内改两次，第二次的改动检测不到");
        System.out.println("  Last-Modified 语义是「修改时间」：文件内容改回原样，时间也变了，会误判为已变");
        System.out.println("  ETag 是内容指纹：只有字节真的变了才变，精度最高");
        System.out.println("  两者同时存在时，服务器应以 If-None-Match 为准（RFC 9110）");
        System.out.println("  压缩代理会改 body，强 ETag 会失效，所以常见做法是弱 ETag：W/\"abc\"");
        System.out.println();
    }

    static void varyTrap() {
        System.out.println("【Vary 漏配的事故现场】");
        System.out.println("  /home 返回个性化内容，响应头只有 Cache-Control: max-age=60");
        System.out.println("  CDN 把「张三的首页」缓存了 60 秒，期间所有用户都看到张三的数据");
        System.out.println("  正确做法：Vary: Cookie（或干脆 private, no-store）");
        System.out.println("  Vary 决定缓存键的维度：Vary: Accept-Encoding 让 gzip 版与原文版分开存");
        System.out.println("  Vary: * 等于宣告「不可缓存」");
        System.out.println();
    }

    static void strategy() {
        System.out.println("【一套能上线的缓存策略】");
        System.out.println("  index.html           Cache-Control: no-cache            发版立即生效");
        System.out.println("  app.3f9a2c.js        Cache-Control: max-age=31536000, immutable   文件名带哈希");
        System.out.println("  /api/public/config   Cache-Control: public, max-age=60");
        System.out.println("  /api/me              Cache-Control: private, no-store    含个人信息");
        System.out.println("  关键：内容一变，文件名就要变。否则一年强缓存就是事故");
        System.out.println();
    }

    public static void main(String[] args) {
        strongCache();
        revalidate();
        directives();
        etagVsLastModified();
        varyTrap();
        strategy();
    }
}
```

**课后练习**
1. 给 `CacheEntry` 加 `stale-while-revalidate` 支持：过期但在宽限期内先返回旧副本，并标记「需要后台刷新」。
2. 实现 `Vary` 的真实分桶：按请求头 `Accept-Encoding` 的值把缓存拆成多个条目，验证 gzip 与原文不会互相污染。
3. 写一个 `shouldStore(CacheEntry e, boolean sharedCache)`：`no-store` 一律不存；`private` 在共享缓存中不存；无 `max-age` 但有 `Expires` 时按 `Expires` 计算；都没有时按 `Last-Modified` 的 10% 做启发式。
4. 思考题：为什么给静态资源加内容哈希后可以用 `immutable`，而 HTML 不行？如果 CDN 缓存了旧 HTML 指向已被删除的旧 JS，会出现什么现象？

---

### 35-07 HTTPS 与 TLS 握手

**知识点**
- HTTPS = HTTP + TLS。它解决三件事：**机密性**（对称加密传数据）、**完整性**（MAC/AEAD 防篡改）、**身份认证**（证书证明「这个公钥真属于这个域名」）。缺了第三件，前两件都能被中间人轻易破解。
- **为什么用混合加密**：非对称加密（RSA/ECDHE）慢，只用来协商出会话密钥；真正传数据用对称加密（AES-GCM/ChaCha20-Poly1305），快几十到几百倍。
- **证书链**：服务器证书 ← 中间 CA ← 根 CA。浏览器/系统内置根证书，逐级验签直到信任锚。任何一级签名不对、域名不匹配、过期，都会报错。
- **证书里有什么**：域名（SAN）、公钥、有效期、签发者、签名算法、扩展（是否 CA、用途）。**CN 字段已废弃**，现代浏览器只看 SAN。
- **TLS 1.2 握手（ECDHE）**：ClientHello（支持的套件 + 随机数 + 密钥交换参数）→ ServerHello + Certificate + ServerKeyExchange + ServerHelloDone → ClientKeyExchange + ChangeCipherSpec + Finished → 服务端 Finished。**2-RTT**。
- **TLS 1.3 握手**：ClientHello 里直接带 key_share，服务端回 ServerHello + 证书 + Finished，**1-RTT** 就能发应用数据。而且删掉了 RSA 密钥交换、CBC 模式、RC4、SHA-1 等一堆历史包袱。
- **前向保密（forward secrecy）**：用 ECDHE 而非 RSA 密钥交换，每次会话的私钥用完即弃。即使服务器长期私钥将来泄露，也解不开历史流量。这是 TLS 1.3 强制 ECDHE 的原因。
- **SNI**：一个 IP 上托管多个 HTTPS 站点时，客户端在 ClientHello 里用 SNI 明文中说明要访问哪个域名 —— 这是隐私缺口，所以有 ECH（Encrypted Client Hello）。
- **HSTS**：响应头 `Strict-Transport-Security: max-age=31536000; includeSubDomains; preload` 告诉浏览器「以后这个域名只用 HTTPS」，防止 SSL 剥离攻击。首次访问仍是 HTTP，所以有 preload 列表。
- **常见配置错误**：证书链不完整（部分客户端能过、安卓报错）；用了 CBC 模式（BEAST/POODLE）；开着 TLS 1.0/1.1；私钥权限过宽；把 `verify=false` 写进生产代码。
- 易错点：以为 HTTPS 能防「服务器被黑」（不能，它只保护传输）；以为证书验证只是「有没有证书」（要验签、验域名、验有效期、验吊销）；把 `InsecureSkipVerify` 当成解决自签证书的正式方案。

**示例代码**
```java
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


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
```

**课后练习**
1. 把 `keyExchange` 改成三组不同素数，验证「p 太小会被暴力破解」：写一个循环从 1 试到 p，找出私钥，说明为什么真实场景要求 2048 位以上。
2. 实现 `verifyChain(List<Cert> chain, String host, long now)`：逐级校验 issuer 是否等于上一级 subject、是否过期、leaf 是否 CA，返回第一个失败原因。
3. 模拟中间人：把 `clientPub` 替换成攻击者的公钥，说明为什么没有证书校验时双方仍会「成功」协商出**不同**的密钥，以及证书校验如何阻断这一步。
4. 思考题：为什么 TLS 1.3 的 0-RTT 数据可能被重放？如果支付接口接受 0-RTT 的 POST，会有什么后果？

---

### 35-08 HTTP/2 多路复用与帧

**知识点**
- HTTP/2 把报文改成**二进制帧**：每个帧有 9 字节头（长度 24 位、类型 8 位、标志 8 位、流 ID 31 位），载荷是帧内容。文本协议变成二进制，解析更快也更严格。
- **一条 TCP 连接承载所有请求**，每个请求是一条**流（stream）**，用奇数 ID 由客户端发起、偶数 ID 由服务端发起。帧可以交错发送，接收端按流 ID 归位 —— 这就是**多路复用**。
- **解决的是应用层队头阻塞**：HTTP/1.1 里请求 B 必须等 A 的响应写完；HTTP/2 里 A 和 B 的帧可以交替上路。但**传输层队头阻塞没解决**，因为底层还是一条 TCP 字节流 —— 这是 HTTP/3 换 QUIC 的动机。
- **HPACK 头部压缩**：静态表（61 个常见头，如 `:method: GET`）+ 动态表（本次连接里出现过的头）+ 哈夫曼编码。第二个请求的头部可能只有几个字节。
- **伪头部**：`:method`、`:path`、`:scheme`、`:authority` 替代请求行。它们必须出现在所有普通头之前。
- **流状态机**：`idle` → `open` → `half-closed (local/remote)` → `closed`。还有 `reserved` 用于服务端推送。乱序发帧、在 closed 流上发帧都是协议错误，要回 `RST_STREAM` 或 `GOAWAY`。
- **流量控制是两级的**：连接级窗口 + 流级窗口，默认 65535 字节，靠 `WINDOW_UPDATE` 扩大。接收方可以只给某条流小窗口，实现「慢请求别占满带宽」。
- **优先级**：HTTP/2 原本有复杂的依赖树（`PRIORITY` 帧），实际效果不好，RFC 9113 已废弃，改用 `Priority` 头 + `PRIORITY_UPDATE` 帧的简化方案。
- **服务端推送（Server Push）已被弃用**：浏览器无法精确知道客户端是否已有缓存，推了常常是浪费。Chrome 已移除。
- **`h2` 与 `h2c`**：`h2` 是 TLS 上的（ALPN 协商），`h2c` 是明文升级（`Upgrade: h2c`），生产基本只用 `h2`。
- **常见误区**：以为 HTTP/2 一定更快（高丢包链路上单连接反而更糟，因为一个包丢了所有流都等）；以为多路复用等于无限并发（`SETTINGS_MAX_CONCURRENT_STREAMS` 有限制）；以为 HTTP/2 不需要域名分片（确实不需要，分片反而有害，因为要建多条连接）。
- 易错点：在 HTTP/2 里继续用 `Connection: keep-alive`（这是 HTTP/1.1 的头，HTTP/2 禁止连接相关头）；忘记 `:authority` 导致虚拟主机路由失败；用 HTTP/2 明文端口跑 `h2`（必须 TLS + ALPN）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


public class Http2Mux {
    enum FrameType {
        HEADERS, DATA, SETTINGS, WINDOW_UPDATE, RST_STREAM, PING, GOAWAY
    }

    /** 一个 HTTP/2 帧：9 字节头里的关键字段 */
    record Frame(FrameType type, int streamId, int flags, String payload) {
        int wireSize() {
            return 9 + payload.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        }

        String flagText() {
            return switch (type) {
                case HEADERS -> (flags & 1) != 0 ? "END_STREAM" : (flags & 4) != 0 ? "END_HEADERS" : "-";
                case DATA -> (flags & 1) != 0 ? "END_STREAM" : "-";
                default -> "-";
            };
        }
    }

    /** 极简 HPACK：静态表 + 动态表 + 「已存在就只发索引」 */
    static class Hpack {
        static final List<String> STATIC = List.of(
                ":authority", ":method", ":method: GET", ":method: POST",
                ":path", ":path: /", ":scheme", ":scheme: https",
                ":status", ":status: 200", ":status: 404",
                "accept-encoding: gzip, deflate", "user-agent", "cookie");
        final Map<String, Integer> dynamic = new LinkedHashMap<>();
        int nextIndex = 62;

        int encode(String header) {
            int idx = STATIC.indexOf(header);
            if (idx >= 0) {
                return idx + 1;
            }
            Integer d = dynamic.get(header);
            if (d != null) {
                return d;
            }
            dynamic.put(header, nextIndex++);
            return -1;      // -1 表示要发字面量
        }

        int encodedSize(String header) {
            return encode(header) > 0 ? 1 : 1 + header.length();
        }
    }

    static void http11VsH2() {
        System.out.println("【HTTP/1.1：三条连接，各自排队】");
        String[][] h1 = {
                {"连接1", "GET /a.css  -> 等 120ms -> 响应"},
                {"连接2", "GET /b.js   -> 等 90ms  -> 响应"},
                {"连接3", "GET /c.png  -> 等 200ms -> 响应"},
        };
        for (String[] r : h1) {
            System.out.printf("  %s  %s%n", r[0], r[1]);
        }
        System.out.println("  总耗时约 200ms，但要占 3 条 TCP 连接（3 次握手 + 3 次慢启动）");
        System.out.println();

        System.out.println("【HTTP/2：一条连接，帧交错】");
        List<Frame> wire = new ArrayList<>();
        wire.add(new Frame(FrameType.HEADERS, 1, 4, ":method: GET /a.css"));
        wire.add(new Frame(FrameType.HEADERS, 3, 4, ":method: GET /b.js"));
        wire.add(new Frame(FrameType.HEADERS, 5, 4, ":method: GET /c.png"));
        wire.add(new Frame(FrameType.DATA, 1, 0, "a.css-part1"));
        wire.add(new Frame(FrameType.DATA, 3, 0, "b.js-part1"));
        wire.add(new Frame(FrameType.DATA, 5, 1, "c.png-all"));
        wire.add(new Frame(FrameType.DATA, 1, 1, "a.css-part2"));
        wire.add(new Frame(FrameType.DATA, 3, 1, "b.js-part2"));
        for (Frame f : wire) {
            System.out.printf("  %-12s stream=%-2d flags=%-10s %s%n",
                    f.type(), f.streamId(), f.flagText(), f.payload());
        }
        System.out.println("  同一个 TCP 连接，c.png 先完成，不必等 a.css 发完");
        System.out.println("  总耗时约等于最慢那一条，且只付 1 次握手");
        System.out.println();
    }

    static void hpack() {
        System.out.println("【HPACK：第二个请求的头几乎不用传】");
        Hpack hpack = new Hpack();
        String[][] requests = {
                {"GET", "/index.html", "gzip, deflate"},
                {"GET", "/app.js", "gzip, deflate"},
                {"GET", "/logo.png", "gzip, deflate"},
        };
        int total = 0;
        for (String[] r : requests) {
            String[] headers = {
                    ":method: " + r[0],
                    ":path: " + r[1],
                    "accept-encoding: " + r[2],
                    "user-agent: demo/1.0",
            };
            StringBuilder detail = new StringBuilder();
            int size = 0;
            for (String h : headers) {
                int idx = hpack.encode(h);
                int s = idx > 0 ? 1 : 1 + h.length();
                size += s;
                detail.append(idx > 0 ? ("#" + idx + " ") : ("LIT(" + h + ") "));
            }
            total += size;
            System.out.printf("  %-12s 头部编码 %2d B：%s%n", r[1], size, detail.toString().trim());
        }
        System.out.printf("  合计 %d B；若按 HTTP/1.1 明文传，三个请求的头部约 %d B%n",
                total, 3 * 80);
        System.out.println("  真实 HPACK 还会对字面量做哈夫曼编码，常见头 1 字节就能表示");
        System.out.println();
    }

    static void streamStates() {
        System.out.println("【流状态机：谁先 END_STREAM，谁先半关闭】");
        Map<String, String> states = new LinkedHashMap<>();
        states.put("idle", "尚未使用，或收到 HEADERS 之前的初始态");
        states.put("open", "双方都可以发帧");
        states.put("half-closed (local)", "本端发了 END_STREAM，只能收不能发");
        states.put("half-closed (remote)", "对端发了 END_STREAM，只能发不能收");
        states.put("closed", "流结束，再发帧就是协议错误");
        states.put("reserved (remote)", "服务端推送预留（已弃用）");
        states.forEach((k, v) -> System.out.printf("  %-22s %s%n", k, v));
        System.out.println();
        System.out.println("  客户端 GET 流程：HEADERS(END_HEADERS) -> open");
        System.out.println("                  -> DATA(END_STREAM)        -> half-closed(local)");
        System.out.println("                  -> 收 HEADERS + DATA(END_STREAM) -> closed");
        System.out.println();
    }

    static void flowControl() {
        System.out.println("【两级流量控制：连接窗口 + 流窗口】");
        int connWindow = 65535;
        Map<Integer, Integer> streamWindow = new HashMap<>();
        streamWindow.put(1, 65535);
        streamWindow.put(3, 16384);      // 给慢请求一个小窗口
        System.out.printf("  连接窗口 %d B；流 1 窗口 %d B；流 3 窗口 %d B%n",
                connWindow, streamWindow.get(1), streamWindow.get(3));

        int[] dataByStream3 = {8000, 8000, 8000};
        for (int i = 0; i < dataByStream3.length; i++) {
            int want = dataByStream3[i];
            int allowed = Math.min(want, Math.min(connWindow, streamWindow.get(3)));
            connWindow -= allowed;
            streamWindow.put(3, streamWindow.get(3) - allowed);
            System.out.printf("  流 3 第 %d 块想发 %d B，实际允许 %d B；连接窗口剩 %d，流窗口剩 %d%n",
                    i + 1, want, allowed, connWindow, streamWindow.get(3));
            if (allowed < want) {
                System.out.println("    窗口不足，必须等接收方发 WINDOW_UPDATE");
            }
        }
        System.out.println("  流窗口可以按流单独调小：慢接口不会把整条连接的带宽吃光");
        System.out.println();
    }

    static void limitsAndTraps() {
        System.out.println("【上限与常见坑】");
        System.out.println("  SETTINGS_MAX_CONCURRENT_STREAMS：同时活跃的流数上限，超了要排队");
        System.out.println("  SETTINGS_MAX_FRAME_SIZE：单帧最大 16384 B，大响应必须切成多个 DATA 帧");
        System.out.println("  禁止连接相关头：Connection、Keep-Alive、Transfer-Encoding、Upgrade");
        System.out.println("  伪头部必须在普通头之前，且每个请求必须有 :method :scheme :path");
        System.out.println("  明文 h2c 需要 Upgrade 协商，生产环境基本不用；浏览器只认 TLS + ALPN 的 h2");
        System.out.println("  单连接 = 单 TCP 流：丢一个包，所有流一起等 -> 这正是 HTTP/3 要解决的问题");
        System.out.println();
    }

    public static void main(String[] args) {
        http11VsH2();
        hpack();
        streamStates();
        flowControl();
        limitsAndTraps();
    }
}
```

**课后练习**
1. 给 `Frame` 加 `wireSize()` 的完整实现，并统计上面 8 个帧在线上总共占多少字节（含 9 字节帧头）。
2. 实现 `Hpack.decode(List<Integer> indices)`，把编码后的索引序列还原成头部列表，验证第二个请求能复用动态表。
3. 写一个流状态机 `Stream`：`onHeaders`、`onData`、`onEndStream`、`onRst`，非法迁移时抛异常，覆盖上面列出的五种状态。
4. 思考题：为什么 HTTP/2 在丢包率高的移动网络上可能比 HTTP/1.1 还慢？HTTP/3 用 QUIC 后这个问题如何缓解？

---
