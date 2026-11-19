# 第64章 开放平台与 API 治理

第27章讲了 RESTful 接口怎么写好，本章讲"接口如何开放给不可信的第三方并可持续运营"。内部 API 与开放 API 的差别不是技术而是信任模型：鉴权、配额、计费、审计、版本兼容全部按外部标准设计。本章按「开放平台全景 → OAuth2 四种模式 → 签名与防重放 → 配额限流与计费 → 版本演进与兼容 → 契约优先与 OpenAPI → SDK 与开发者体验 → 网关治理 → 错误码与文档 → 越权注入与滥用 → 支付开放平台案例」的顺序讲。所有示例仍是纯 JDK：授权码一次性与重放拒绝、HMAC 签名与时间窗 nonce、日配额与 QPS 双控、阶梯计费、契约 diff 兼容判定、SDK 重试判据、网关鉴权链、错误码注册表、IDOR 双键校验全部真实运行。

### 64-01 开放平台全景：从内部 API 到生态

**知识点**
- **内部 API 与开放 API 的差别不是技术，是"信任模型"**：内部 API 假设调用方可信（内网、同团队）；开放 API 假设**调用方不可信**（第三方开发者、可能的攻击者）——**鉴权、配额、审计、版本兼容全部要按"外部"标准设计**（47-04 零信任思想）。
- **开放平台的四个组成**：**接入层**（OAuth2/API Key/签名）、**治理层**（配额/限流/计费/审计）、**契约层**（OpenAPI 文档/SDK/错误码）、**运营层**（开发者门户/工单/SLA）——**技术只是其中一半，另一半是开发者体验**。
- **开放平台的商业意义**：API 是"能力的零售"——**调用量即收入**，因此**配额与计费是核心功能而非附属**（64-04）；**稳定性即口碑**（99.95% 的 API 比 99.9% 的贵得多）。
- **与 27 章 RESTful 的关系**：27 章讲"接口怎么写好"，本章讲"接口怎么开放给不可信的第三方并可持续运营"——**同一套 HTTP，不同的治理要求**。
- **开放平台的三种开放程度**：**完全公开**（注册即用，如天气 API）、**审核制**（提交资质后开通权限）、**定向合作**（白名单 + 定制 SLA）——**权限分级决定接入复杂度**。
- 易错点：把内部 API 直接暴露公网（无配额无签名，36 章的攻击面）；版本策略缺失（改接口即破坏所有集成方）；文档与实现不一致（开发者信任崩塌）；没有沙箱环境（第三方只能拿生产试错）。

**示例代码**
```java
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class OpenPlatformOverview {
    record Layer(String name, String duty, String keyRisk) {
    }

    static void layers() {
        System.out.println("【开放平台四层】");
        List<Layer> layers = List.of(
                new Layer("接入层", "身份认证与请求校验", "鉴权漏洞 / 密钥泄露"),
                new Layer("治理层", "配额、限流、计费、审计", "被滥用 / 账单纠纷"),
                new Layer("契约层", "文档、SDK、错误码、版本", "集成方踩坑 / 破坏性变更"),
                new Layer("运营层", "门户、工单、SLA 公告", "信任流失"));
        for (Layer l : layers) {
            System.out.printf("  %-6s %-26s 风险：%s%n", l.name(), l.duty(), l.keyRisk());
        }
        System.out.println();
    }

    static void trustModel() {
        System.out.println("【信任模型的差异】");
        Map<String, String> diff = new LinkedHashMap<>();
        diff.put("身份", "内部：服务间 mTLS（47-04） / 开放：OAuth2 + API Key（64-02/03）");
        diff.put("配额", "内部：按服务限流 / 开放：按应用+接口+日/月配额（64-04）");
        diff.put("审计", "内部：trace 采样 / 开放：每次调用留痕（计费与纠纷依据）");
        diff.put("兼容", "内部：可协同升级 / 开放：公测->稳定，破坏性变更需新版本（64-05）");
        diff.forEach((k, v) -> System.out.printf("  %-4s %s%n", k, v));
        System.out.println();
    }

    static void opennessLevels() {
        System.out.println("【开放程度三档】");
        String[][] rows = {
                {"完全公开", "注册即用 + 低配额", "天气/汇率类公开数据"},
                {"审核制", "提交资质 + 按权限开通", "支付/物流/身份核验"},
                {"定向合作", "白名单 + 定制 SLA 与配额", "大型商户/平台对接"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-8s %-26s %s%n", r[0], r[1], r[2]);
        }
    }

    public static void main(String[] args) {
        layers();
        trustModel();
        opennessLevels();
    }
}
```

**课后练习**
1. 为你公司的能力设计一个开放 API（选一个真实能力），画出四层内容并标注每层的风险点。
2. 对比"内部 API"与"开放 API"在身份、配额、审计、兼容策略上的差异，写成团队规范。
3. 思考题：为什么"开发者体验"是开放平台的核心竞争力？从集成成本与生态规模的正反馈分析。

---

### 64-02 OAuth2 四种授权模式

**知识点**
- **OAuth2 解决的是"授权而不共享密码"**：第三方应用获得**有限权限的令牌**（scope），而不是用户的账号密码——**令牌有三要素**：有效期、权限范围（scope）、可撤销。
- **四种授权模式**：**授权码（Authorization Code）**——最安全，适合有后端的应用（授权码 + PKCE 防拦截）；**隐式（Implicit）**——已废弃（令牌直接暴露在前端）；**密码模式（Resource Owner Password）**——高度可信的第一方应用（如自家 App），不推荐；**客户端凭证（Client Credentials）**——服务间调用（无用户参与）。
- **授权码模式的关键交互**：`/authorize` 返回 code（一次性、短有效期）-> 后端用 `code + client_secret` 换 token -> **code 不能被前端截获**（PKCE 用 code_verifier/challenge 进一步保护公共客户端）。
- **令牌的使用**：`Authorization: Bearer <token>`；**access_token 短期**（分钟~小时）+ **refresh_token 长期**（用于换新 access_token，可撤销）——**刷新令牌轮换**（每次刷新换新的 refresh_token）能降低泄露风险。
- **scope 与最小权限**：按能力划分 scope（`order:read`/`order:write`）——**与 36 章的越权防护同源**（44-09 的权限四道闸）；**授权页面展示 scope 给用户**（知情同意）。
- 易错点：用隐式模式（token 泄露）；code 无有效期（被重放）；refresh_token 永久有效且不轮换（泄露即长期有效）；scope 粒度过粗（一个 token 能改所有数据）。

**示例代码**
```java
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
```

**课后练习**
1. 实现完整授权码流程：authorize -> code -> exchange -> 调用校验，验证 code 一次性、过期、客户端绑定三个约束。
2. 实现 refresh_token 轮换：旧 refresh 使用后立即失效并签发新的，构造"旧 refresh 重放"验证被拒。
3. 思考题：为什么隐式模式被废弃？从"令牌在前端暴露面"与 PKCE 的替代作用分析。

---

### 64-03 API Key、签名与防重放

**知识点**
- **API Key 是最简单的鉴权**：`X-API-Key: xxx`——**标识调用方**但不加密内容（**必须走 HTTPS**）；适合"低敏感 + 高并发"的服务端调用（64-02 的客户端凭证的简化版）。
- **签名（Signature）解决"内容不可篡改 + 身份可验证"**：对"请求参数 + 时间戳 + nonce + 密钥"做 HMAC 签名（60-02 的 JWT 同款算法），服务端重算比对——**参数被改则签名不符**；**与 51-02 的支付渠道签名同一机制**。
- **防重放的三要素**：**时间戳**（请求有效期 5 分钟，过期拒绝）+ **nonce**（一次性随机串，服务端去重表/Redis TTL）+ **签名覆盖这两项**——**缺一个都能被重放**（只签名不防重放 = 攻击者可原样重发）。
- **密钥的层级**：**AppKey（公开）+ AppSecret（保密）**；**AppSecret 只存服务端**（前端绝不能有，36 章的密钥纪律）；**密钥轮换**（双密钥并行期 + 失效旧密钥）；**泄露应急预案**（立即禁用 + 审计该密钥的历史调用）。
- **签名串的构造要固定**：参数按字典序排序 + 拼接 + 加盐（否则"同样参数不同顺序"签名不同，联调地狱）——**签名规范要写进文档**（64-09）。
- 易错点：签名不含时间戳/nonce（可重放）；nonce 无 TTL（存储无限涨）；参数未排序（联调失败）；AppSecret 放前端（浏览器可看）；密钥泄露不轮换。

**示例代码**
```java
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

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
```

**课后练习**
1. 实现签名校验服务：参数排序 + timestamp + nonce + HMAC，验证"篡改/重放/过期"三类攻击被拒。
2. 实现密钥轮换：双密钥并行期内新旧密钥都能验签，旧密钥下线后旧签名被拒，输出迁移时间线。
3. 思考题：为什么"只做签名不做防重放"不安全？构造一个"抓包原样重发"的攻击时序说明。

---

### 64-04 配额、限流与计费

**知识点**
- **配额（Quota）与限流（Rate Limit）的区别**：**配额是"一段时间内的总量"**（每日 10 万次，64 章的核心），**限流是"瞬时的速率"**（每秒 100 次）——**两者组合才是完整的保护**（44-08 限流 + 本章配额）。
- **配额的三级维度**：**应用级**（整个 App 的额度）、**接口级**（不同接口不同额度，读多写少）、**时间窗**（日/月配额 + 峰值速率）——**配额超限返回 429 + 剩余额度信息**（开发者可自助排查）。
- **计费模型**：**按调用次数**（简单）、**按阶梯**（前 1 万免费，之后递减单价）、**按资源**（存储/流量/算力）、**包年包月 + 超额**——**计费与配额共用一套计量**（64-04 的计量是 51-05 对账的输入）。
- **计量要准确且可对账**：每次调用记录 `appId/接口/耗时/结果/计费单位`——**计量数据与账单对账**（51-05 思想）；**计量失败要有兜底**（异步补记、日终对账）。
- **限流的实现**（44 章手写 + 53-05 Sentinel）：令牌桶（允许突发）、漏桶（匀速）、滑动窗口（精确）——**开放平台通常用"令牌桶 + 日配额"组合**（突发容忍 + 总量控制）。
- 易错点：只有速率限流无总量配额（一天被打 1 亿次）；配额超限返回 500（应为 429 + 明确信息）；计量丢失（账单对不上）；配额恢复时间不明确（开发者不知道何时重置）。

**示例代码**
```java
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class QuotaAndBilling {
    /** 应用配额：日总量 + 每秒速率 */
    record AppQuota(String appId, long dailyLimit, long qpsLimit) {
    }

    static class Usage {
        long usedToday = 0;
        long windowStartMs = 0;
        long windowCount = 0;

        /** 返回 null 表示放行，否则返回拒绝原因 */
        String consume(AppQuota quota, long now) {
            if (now - windowStartMs >= 1000) {
                windowStartMs = now;
                windowCount = 0;
            }
            if (usedToday + 1 > quota.dailyLimit()) {
                return "429 日配额耗尽（" + quota.dailyLimit() + "），次日 00:00 重置";
            }
            if (windowCount + 1 > quota.qpsLimit()) {
                return "429 触发速率限制（" + quota.qpsLimit() + " QPS），请退避重试";
            }
            usedToday++;
            windowCount++;
            return null;
        }
    }

    /** 阶梯计费：前 1 万免费，1~10 万单价 0.01，10 万以上 0.005 */
    static double bill(long calls) {
        long tier1 = Math.min(calls, 10_000);
        long tier2 = Math.min(Math.max(calls - 10_000, 0), 90_000);
        long tier3 = Math.max(calls - 100_000, 0);
        return tier1 * 0 + tier2 * 0.01 + tier3 * 0.005;
    }

    public static void main(String[] args) {
        AppQuota quota = new AppQuota("app-1001", 5, 2);
        Usage usage = new Usage();
        System.out.println("【配额与速率双控（日 5 次，2 QPS）】");
        for (int i = 1; i <= 4; i++) {
            String r = usage.consume(quota, 0);
            System.out.printf("  第%d次 @0ms -> %s%n", i, r == null ? "200 放行" : r);
        }
        String r5 = usage.consume(quota, 0);
        System.out.printf("  第5次 @0ms -> %s%n", r5 == null ? "200 放行" : r5);
        String r6 = usage.consume(quota, 0);
        System.out.printf("  第6次 @0ms -> %s%n", r6 == null ? "200 放行" : r6);
        System.out.printf("  第6次 @1500ms（新窗口）-> %s%n",
                usage.consume(quota, 1500) == null ? "200 放行" : "429");

        System.out.println();
        System.out.println("【阶梯计费】");
        for (long calls : new long[]{5_000, 50_000, 200_000}) {
            System.out.printf("  调用 %6d 次 -> %.2f 元%n", calls, bill(calls));
        }

        System.out.println();
        System.out.println("【配额超限的响应设计】");
        Map<String, String> design = new LinkedHashMap<>();
        design.put("状态码", "429 Too Many Requests（不是 500）");
        design.put("响应头", "X-RateLimit-Limit / Remaining / Reset");
        design.put("响应体", "错误码 + 重试建议 + 升级入口");
        design.put("计量", "每次调用落明细，日终与账单对账（51-05）");
        design.forEach((k, v) -> System.out.printf("  %-6s %s%n", k, v));
    }
}
```

**课后练习**
1. 实现"日配额 + QPS 限流"双控，验证四类场景（正常/超速率/超日配额/窗口重置）并输出 429 响应头设计。
2. 实现阶梯计费与计量明细对账：模拟 10 万次调用，验证"账单金额 = 明细聚合"。
3. 思考题：为什么配额超限要返回 429 而不是 500？从"开发者可自助处理"与"区分系统故障与限流"两个角度分析。

---

### 64-05 API 版本演进与兼容策略

**知识点**
- **版本策略的核心是"给集成方留出升级时间"**：**URL 版本**（`/v1/orders`，最直观）、**Header 版本**（`Accept-Version: 2`，URL 稳定）、**参数版本**（`?version=2`，最不推荐）——**主流是 URL 版本**（清晰、易缓存、易观测）。
- **兼容的判定标准**：**新增可选字段/新增接口 = 兼容**（调用方可忽略）；**删字段/改类型/改语义/改必填 = 破坏性**（必须新版本）；**改错误码含义**也是破坏性（调用方按码分支）。
- **三种演进手段**：**并行支持**（v1/v2 同时跑，v1 标记废弃并给下线日期）、**适配层**（v2 内部转 v1，减少维护成本）、**灰度迁移**（新版本先小流量，41 章）。
- **废弃（Deprecation）的规范**：`Deprecation`/`Sunset` 响应头 + 文档标注 + **提前 6 个月通知** + **调用量监控**（还有谁在调）——**"静默下线"是开放平台的大忌**。
- **契约测试是兼容性的保障**（64-06）：每次发布对"契约快照"做 diff，**破坏性变更在 CI 阶段就被拦下**（41-03 门禁）。
- 易错点：改字段语义但不升版本（调用方逻辑静默出错）；废弃无通知（集成方线上崩）；版本数量无限膨胀（维护成本线性涨）；新版本文档缺失（集成方只能看代码）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ApiVersioning {
    record Field(String name, String type, boolean required) {
    }

    record ApiContract(String version, List<Field> fields, List<String> endpoints,
                       String status) {
    }

    enum ChangeType {
        ADD_OPTIONAL, ADD_ENDPOINT, REMOVE_FIELD, CHANGE_TYPE, CHANGE_REQUIRED, CHANGE_SEMANTIC
    }

    /** 变更分类：是否破坏兼容 */
    static boolean isBreaking(ChangeType type) {
        return switch (type) {
            case ADD_OPTIONAL, ADD_ENDPOINT -> false;
            case REMOVE_FIELD, CHANGE_TYPE, CHANGE_REQUIRED, CHANGE_SEMANTIC -> true;
        };
    }

    static void diff(ApiContract v1, ApiContract v2, List<ChangeType> changes) {
        System.out.println("【契约 diff】v" + v1.version() + " -> v" + v2.version());
        List<String> breaking = new ArrayList<>();
        for (ChangeType c : changes) {
            System.out.printf("  %-16s -> %s%n", c, isBreaking(c) ? "破坏性" : "兼容");
            if (isBreaking(c)) {
                breaking.add(c.name());
            }
        }
        if (breaking.isEmpty()) {
            System.out.println("  结论：可直接发布同一大版本（小版本升级）");
        } else {
            System.out.println("  结论：必须新开大版本 + 并行支持 + 废弃通知：" + breaking);
        }
        System.out.println();
    }

    static void deprecationPolicy() {
        System.out.println("【废弃规范】");
        Map<String, String> policy = new LinkedHashMap<>();
        policy.put("响应头", "Deprecation: true / Sunset: 2027-06-01");
        policy.put("文档", "接口列表标记 deprecated，给出迁移指南");
        policy.put("通知", "提前 6 个月 + 每月提醒，直到调用量归零");
        policy.put("监控", "按 appId 统计 v1 调用量，联系尾部集成方");
        policy.put("下线", "调用量归零 + 公告期结束 -> 关闭并保留 30 天兜底");
        policy.forEach((k, v) -> System.out.printf("  %-6s %s%n", k, v));
        System.out.println();
    }

    public static void main(String[] args) {
        ApiContract v1 = new ApiContract("1", List.of(
                new Field("orderId", "string", true),
                new Field("amount", "int", true),
                new Field("status", "string", true)), List.of("/v1/orders"), "deprecated");
        ApiContract v2 = new ApiContract("2", List.of(
                new Field("orderId", "string", true),
                new Field("amount", "long", true),
                new Field("status", "enum", true),
                new Field("channel", "string", false)), List.of("/v2/orders"), "stable");
        System.out.println("  v1 字段：" + v1.fields());
        System.out.println("  v2 字段：" + v2.fields());
        System.out.println();
        diff(v1, v2, List.of(ChangeType.CHANGE_TYPE, ChangeType.CHANGE_REQUIRED,
                ChangeType.ADD_OPTIONAL, ChangeType.ADD_ENDPOINT));
        deprecationPolicy();
        System.out.println("  铁律：新增字段可兼容；删/改/改语义一律新版本");
    }
}
```

**课后练习**
1. 为你的 API 制定版本策略：URL 版本 + 契约快照 + 破坏性变更判定表，并写一份废弃公告模板。
2. 实现契约 diff 工具：对比两版 OpenAPI 字段（新增/删除/类型/必填），自动输出"兼容/破坏"结论。
3. 思考题：为什么"改字段语义"也算破坏性变更？构造"amount 从分改为元"导致集成方金额放大 100 倍的用例。

---

### 64-06 契约优先与 OpenAPI

**知识点**
- **契约优先（Contract-First）**：先写 **OpenAPI 规范**（YAML/JSON），再生成服务端骨架与客户端 SDK——**契约为唯一事实源**，代码与文档不漂移（46-08 数据契约的 API 版）。
- **OpenAPI 的核心内容**：路径与操作、请求/响应 schema、参数、错误码、鉴权方式、示例——**它同时是文档、测试依据、SDK 生成源、Mock 服务源**。
- **契约的三种用途**：**生成代码**（服务端/客户端骨架）、**生成 Mock**（前端在服务未上线时联调）、**契约测试**（验证实现是否符合契约，64-05 的保障）。
- **契约评审（API Review）**：新增/变更接口先改契约并评审（命名/错误码/分页/幂等/权限），**通过后再写实现**——**与 49-11 建模评审同构：先评审设计再写代码**。
- **契约的治理**：契约进 Git（版本化）、CI 校验（lint + 向后兼容检查 64-05）、发布后自动更新开发者门户（64-09）。
- 易错点：先写代码后补文档（必然漂移）；契约里没有错误码（调用方无法处理异常）；示例缺失（集成方靠猜）；契约变更不评审（破坏兼容）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ContractFirst {
    record ApiSpec(String path, String method, String summary, List<String> params,
                   List<String> responses, List<String> auth) {
    }

    /** 契约 lint：检查必备要素 */
    static List<String> lint(ApiSpec spec) {
        List<String> issues = new ArrayList<>();
        if (spec.summary() == null || spec.summary().isBlank()) {
            issues.add("缺少接口说明（summary）");
        }
        if (spec.responses().stream().noneMatch(r -> r.startsWith("200"))) {
            issues.add("缺少成功响应定义");
        }
        if (spec.responses().stream().noneMatch(r -> r.startsWith("4"))) {
            issues.add("缺少错误响应定义（调用方无法处理异常）");
        }
        if (spec.auth().isEmpty()) {
            issues.add("未声明鉴权方式");
        }
        if (spec.params().stream().noneMatch(p -> p.contains("example"))) {
            issues.add("参数缺少示例（集成方只能靠猜）");
        }
        return issues;
    }

    static void generatedArtifacts() {
        System.out.println("【一份契约，五种产出】");
        Map<String, String> outputs = new LinkedHashMap<>();
        outputs.put("服务端骨架", "接口签名 + DTO 类 + 参数校验注解");
        outputs.put("客户端 SDK", "方法 + 类型 + 重试/签名（64-07）");
        outputs.put("Mock 服务", "前端在服务未上线时联调");
        outputs.put("契约测试", "实现 vs 契约的自动比对（CI 门禁）");
        outputs.put("开发者文档", "门户自动渲染（64-09）");
        outputs.forEach((k, v) -> System.out.printf("  %-8s %s%n", k, v));
        System.out.println();
    }

    static void reviewChecklist() {
        System.out.println("【API 评审清单】");
        List<String> items = List.of(
                "命名：资源复数 + 动作语义（GET /orders/{id}）",
                "分页：游标优先，避免 offset 深翻页（39-03）",
                "幂等：写接口支持幂等键（39/43 章）",
                "错误码：业务码 + 可读消息 + 定位字段（64-09）",
                "鉴权：每个接口声明所需 scope（64-02）",
                "兼容：新增可选/不删不改（64-05）",
                "示例：请求与响应都带示例");
        items.forEach(i -> System.out.println("  - " + i));
    }

    public static void main(String[] args) {
        ApiSpec bad = new ApiSpec("/orders", "POST", "", List.of("body"),
                List.of("200 ok"), List.of());
        ApiSpec good = new ApiSpec("/orders", "POST", "创建订单（支持幂等键）",
                List.of("header Idempotency-Key example=req-9001", "body example={\"amount\":6900}"),
                List.of("201 创建成功", "409 幂等冲突", "429 配额超限"),
                List.of("scope: order:write"));
        System.out.println("【契约 lint：差示例】");
        lint(bad).forEach(i -> System.out.println("  ✗ " + i));
        System.out.println("【契约 lint：好示例】");
        List<String> issues = lint(good);
        System.out.println("  " + (issues.isEmpty() ? "✓ 通过全部检查" : issues));
        System.out.println();
        generatedArtifacts();
        reviewChecklist();
    }
}
```

**课后练习**
1. 为一个"查询订单"接口写完整 OpenAPI 契约（含参数示例、三类响应、鉴权 scope），跑一遍 lint 确保零问题。
2. 实现契约测试雏形：把契约定义转成断言（响应字段/状态码/错误码），对 mock 实现做验证。
3. 思考题：为什么"契约先行"能防止文档漂移？从"单一事实源"与"生成物不可手改"两个角度分析。

---

### 64-07 SDK 设计与开发者体验

**知识点**
- **SDK 是开放平台的"第一印象"**：开发者先看 SDK 好不好用，再决定是否接入——**SDK 的质量直接影响生态规模**。
- **SDK 的必备能力**：**类型安全**（方法 + DTO，不传 Map）、**鉴权自动化**（签名/令牌刷新由 SDK 处理，64-02/03）、**重试与退避**（对 429/5xx 自动，40-02）、**超时与连接池**、**错误映射**（统一异常类型，带业务码）、**日志与调试**（可开关的请求日志）。
- **SDK 的分层**：**传输层**（HTTP/签名/重试）-> **API 层**（每个接口一个方法）-> **模型层**（DTO）——**业务方只碰 API 层**；**SDK 必须与契约同源生成**（64-06），避免手写漂移。
- **多语言 SDK 的成本**：契约生成 + 少量手写增强（每个语言）；**一致性测试**（同一请求在 Java/Go SDK 发出相同签名与报文）——**跨语言一致性是 SDK 质量的硬指标**。
- **开发者体验（DX）的细节**：**一条命令跑通示例**（Quick Start）、**沙箱环境**（64-01）、**错误信息可行动**（"signature mismatch: 检查参数排序"而不是"invalid request"）、**升级指南**（64-05）。
- 易错点：SDK 把密钥硬编码进示例（泄露）；重试对非幂等接口默认开启（重复下单）；错误吞掉只返回 null；SDK 版本与服务版本绑定（升级耦合）。

**示例代码**
```java
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SdkDesign {
    record SdkCapability(String name, String why, String failureIfMissing) {
    }

    /** SDK 的错误映射：统一异常 + 可行动信息 */
    static class ApiException extends RuntimeException {
        final int httpStatus;
        final String bizCode;
        final String hint;

        ApiException(int httpStatus, String bizCode, String message, String hint) {
            super(message);
            this.httpStatus = httpStatus;
            this.bizCode = bizCode;
            this.hint = hint;
        }

        String actionable() {
            return "[" + httpStatus + "/" + bizCode + "] " + getMessage() + " -> " + hint;
        }
    }

    /** 重试策略：只对幂等方法与 429/5xx 重试 */
    static boolean shouldRetry(String method, int status, boolean idempotentKeyPresent) {
        boolean retryableStatus = status == 429 || status >= 500;
        boolean safeMethod = List.of("GET", "HEAD", "PUT", "DELETE").contains(method)
                || idempotentKeyPresent;
        return retryableStatus && safeMethod;
    }

    static void capabilities() {
        System.out.println("【SDK 六项必备能力】");
        List<SdkCapability> caps = List.of(
                new SdkCapability("类型安全", "编译期发现错误", "调用方到处传 Map，线上才炸"),
                new SdkCapability("鉴权自动化", "签名/令牌刷新由 SDK 处理", "每个调用方自己实现签名，错法五花八门"),
                new SdkCapability("重试退避", "429/5xx 自动退避", "调用方无脑重试打爆平台（44-08）"),
                new SdkCapability("超时与连接池", "避免连接泄漏", "生产环境句柄耗尽"),
                new SdkCapability("错误映射", "统一异常 + 业务码", "调用方只能看 HTTP 状态猜原因"),
                new SdkCapability("可开关日志", "联调可开、生产默认关", "日志泄露密钥或淹没磁盘"));
        caps.forEach(c -> System.out.printf("  %-10s %-24s 缺失后果：%s%n",
                c.name(), c.why(), c.failureIfMissing()));
        System.out.println();
    }

    static void dxChecklist() {
        System.out.println("【开发者体验清单】");
        Map<String, String> dx = new LinkedHashMap<>();
        dx.put("Quick Start", "5 分钟内跑通第一个成功调用（含沙箱密钥）");
        dx.put("沙箱", "与生产隔离的环境，可随意试错");
        dx.put("错误信息", "可行动：指出哪个参数/字段/如何修复");
        dx.put("示例", "每个接口都有 curl + SDK 双版本");
        dx.put("升级", "版本变更指南 + 兼容性说明（64-05）");
        dx.forEach((k, v) -> System.out.printf("  %-10s %s%n", k, v));
    }

    public static void main(String[] args) {
        capabilities();
        System.out.println("【错误映射示例】");
        ApiException e = new ApiException(401, "SIGNATURE_MISMATCH",
                "签名校验失败", "检查参数是否按字典序排序、时间戳是否在 5 分钟内");
        System.out.println("  " + e.actionable());
        System.out.println();
        System.out.println("【重试判据】");
        String[][] cases = {
                {"GET", "503", "无", "重试（幂等 + 5xx）"},
                {"POST", "503", "有幂等键", "重试（幂等键保证不重复）"},
                {"POST", "503", "无幂等键", "不重试（可能重复下单）"},
                {"POST", "429", "无幂等键", "不重试（先退避，由调用方决定）"},
                {"GET", "400", "无", "不重试（参数错误重试无意义）"},
        };
        for (String[] c : cases) {
            boolean retry = shouldRetry(c[0], Integer.parseInt(c[1]),
                    c[2].contains("有"));
            System.out.printf("  %-4s %-3s %-10s -> %s（%s）%n", c[0], c[1], c[2],
                    retry ? "重试" : "不重试", c[3]);
        }
        System.out.println();
        dxChecklist();
    }
}
```

**课后练习**
1. 为一个虚构的订单查询 API 写 SDK 设计文档（分层、六项能力、错误映射表、重试判据）。
2. 实现 SDK 重试策略：按方法/状态码/幂等键判定是否重试，用 8 组用例验证，并说明"POST 无幂等键不重试"的理由。
3. 思考题：为什么"SDK 与契约同源生成"能降低维护成本？从多语言一致性与手写漂移两个角度分析。

---

### 64-08 API 网关治理：鉴权、审计、灰度

**知识点**
- **开放平台网关与内部网关的差异**：内部网关（47-03）管**服务路由与熔断**；开放网关还要管**第三方身份（AppKey/令牌）、配额、计费、审计、请求/响应改写**——**它是"对外能力的统一收口"**。
- **鉴权链的顺序**：**IP 白名单**（可选）-> **AppKey 识别** -> **签名校验/令牌校验** -> **scope 校验** -> **配额检查** -> **路由转发**——**顺序错了会浪费算力**（先验签再查配额，配额超限的请求白验签）。
- **审计的内容**：`appId/接口/参数摘要（脱敏）/耗时/状态/错误码/客户端 IP`——**每次调用留痕**（计费依据 51-05、纠纷举证、安全分析 36 章）；**参数脱敏**（36-09）。
- **灰度与降级**：**按 appId 灰度**（重点客户先上新版本 41-07）、**按接口降级**（某接口故障时返回缓存或 503，44-08）、**配额动态调整**（大促期临时提升）。
- **网关的自身保护**：**请求体大小限制**（防大包攻击）、**超时与并发上限**、**响应压缩**、**防止内部错误泄露堆栈**（36 章）。
- 易错点：鉴权链顺序错（浪费算力或绕过校验）；审计无脱敏（日志泄露敏感数据）；网关做业务逻辑（47 章原则）；无限流（第三方打爆平台）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class OpenGateway {
    record Request(String appId, String path, String method, String signature,
                   String timestamp, List<String> scopes, long bodyBytes) {
    }

    record Decision(int status, String reason, String stage) {
    }

    /** 鉴权链：顺序即性能与安全 */
    static Decision handle(Request req, Map<String, List<String>> appScopes,
                           Map<String, Long> quotaLeft, long maxBodyBytes, long now) {
        if (req.bodyBytes() > maxBodyBytes) {
            return new Decision(413, "请求体过大", "size-check");
        }
        List<String> allowed = appScopes.get(req.appId());
        if (allowed == null) {
            return new Decision(401, "未知应用（AppKey 无效）", "app-identify");
        }
        if (req.signature() == null || req.signature().isBlank()) {
            return new Decision(401, "缺少签名", "signature");
        }
        long ts;
        try {
            ts = Long.parseLong(req.timestamp());
        } catch (NumberFormatException e) {
            return new Decision(400, "时间戳格式错误", "signature");
        }
        if (Math.abs(now - ts) > 300_000) {
            return new Decision(401, "时间戳过期", "signature");
        }
        for (String scope : req.scopes()) {
            if (!allowed.contains(scope)) {
                return new Decision(403, "scope 不足：" + scope, "scope");
            }
        }
        long left = quotaLeft.getOrDefault(req.appId(), 0L);
        if (left <= 0) {
            return new Decision(429, "配额耗尽", "quota");
        }
        quotaLeft.put(req.appId(), left - 1);
        return new Decision(200, "转发到后端（剩余配额 " + (left - 1) + "）", "route");
    }

    static void audit() {
        System.out.println("【审计记录字段】");
        List<String> fields = List.of(
                "appId / 接口路径 / 方法（谁调了什么）",
                "参数摘要（脱敏：手机号 138****1234，36-09）",
                "耗时 / 状态码 / 错误码（性能与失败分析）",
                "客户端 IP / UA（安全分析，36 章）",
                "trace_id（串联内部链路，42-07）");
        fields.forEach(f -> System.out.println("  - " + f));
        System.out.println();
    }

    public static void main(String[] args) {
        Map<String, List<String>> appScopes = new LinkedHashMap<>();
        appScopes.put("app-1001", List.of("order:read", "order:write"));
        Map<String, Long> quota = new LinkedHashMap<>();
        quota.put("app-1001", 2L);

        long now = 1_000_000;
        List<Request> requests = new ArrayList<>(List.of(
                new Request("app-1001", "/open/orders", "GET", "sig", String.valueOf(now),
                        List.of("order:read"), 1024),
                new Request("app-1001", "/open/orders", "GET", "sig", String.valueOf(now - 600_000),
                        List.of("order:read"), 1024),
                new Request("app-9999", "/open/orders", "GET", "sig", String.valueOf(now),
                        List.of("order:read"), 1024),
                new Request("app-1001", "/open/orders", "POST", "sig", String.valueOf(now),
                        List.of("user:delete"), 1024),
                new Request("app-1001", "/open/orders", "POST", "sig", String.valueOf(now),
                        List.of("order:write"), 2 * 1024 * 1024)));
        System.out.println("【鉴权链逐项拦截】");
        for (Request r : requests) {
            Decision d = handle(r, appScopes, quota, 1024 * 1024, now);
            System.out.printf("  %-8s %-6s scope=%-14s -> %d %-30s（%s）%n",
                    r.appId(), r.method(), r.scopes(), d.status(), d.reason(), d.stage());
        }
        System.out.println();
        System.out.println("【配额耗尽】");
        Decision d = handle(new Request("app-1001", "/open/orders", "GET", "sig",
                String.valueOf(now), List.of("order:read"), 512), appScopes, quota,
                1024 * 1024, now);
        System.out.printf("  %d %s%n", d.status(), d.reason());
        System.out.println();
        audit();
        System.out.println("  顺序价值：先拦大小/身份/签名/scope，再扣配额 —— 不浪费算力也不误扣配额");
    }
}
```

**课后练习**
1. 实现开放网关鉴权链：大小限制 -> AppKey -> 签名/时间戳 -> scope -> 配额 -> 路由，用 6 个请求验证各层拦截。
2. 实现审计脱敏：对手机号/身份证/银行卡做掩码，验证日志中无明文。
3. 思考题：为什么"配额检查要放在签名校验之后"？从"防止无效请求消耗配额"与"算力成本"两个角度分析。

---

### 64-09 错误码与文档体系

**知识点**
- **错误码是开放 API 的"契约中的契约"**：调用方按码分支处理——**码必须稳定、唯一、可分类**（`AUTH_401_INVALID_SIGN`、`QUOTA_429_EXCEEDED`、`BIZ_400_ORDER_NOT_FOUND`）。
- **错误响应的标准结构**：`code`（机器可读）+ `message`（人可读，可展示给终端用户）+ `requestId`（排查用）+ **`hint`/`field`（可行动信息，64-07）**——**不要只返回 "error"**。
- **错误码的分层**：**协议层**（HTTP 状态码：401/403/404/429/5xx）、**平台层**（鉴权/配额/签名）、**业务层**（订单不存在/库存不足）——**调用方先看 HTTP 码，再看业务码**（64-08 的鉴权链对应）。
- **文档体系的三件套**：**接口参考**（OpenAPI 渲染，64-06）、**指南**（Quick Start/鉴权/错误处理/最佳实践）、**变更日志**（每个版本的破坏性与新特性，64-05）。
- **文档质量的判据**：**照着文档能独立跑通**（含鉴权、含错误处理、含重试示例）——**文档里的每个示例都要被 CI 跑过**（否则很快过期）。
- 易错点：错误码随版本变化（调用方逻辑崩）；同一个含义多个码（无法统一处理）；message 直接暴露内部堆栈（36 章）；文档示例未验证（照抄报错）。

**示例代码**
```java
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ErrorCodeSystem {
    record ErrorCode(String code, int httpStatus, String message, String hint,
                     String category) {
    }

    static final Map<String, ErrorCode> REGISTRY = new LinkedHashMap<>();

    static void register() {
        REGISTRY.put("AUTH.MISSING_SIGNATURE", new ErrorCode("AUTH.MISSING_SIGNATURE", 401,
                "缺少签名", "在 Header 中提供 X-Signature", "平台"));
        REGISTRY.put("AUTH.INVALID_SIGNATURE", new ErrorCode("AUTH.INVALID_SIGNATURE", 401,
                "签名校验失败", "检查参数是否字典序排序、时间戳是否在 5 分钟内", "平台"));
        REGISTRY.put("AUTH.SCOPE_DENIED", new ErrorCode("AUTH.SCOPE_DENIED", 403,
                "权限不足", "申请对应 scope 或联系平台开通", "平台"));
        REGISTRY.put("QUOTA.EXCEEDED", new ErrorCode("QUOTA.EXCEEDED", 429,
                "调用配额已耗尽", "次 00:00 重置，或申请提额", "平台"));
        REGISTRY.put("BIZ.ORDER_NOT_FOUND", new ErrorCode("BIZ.ORDER_NOT_FOUND", 404,
                "订单不存在", "确认 orderId 是否正确或是否属于当前应用", "业务"));
        REGISTRY.put("SYS.INTERNAL", new ErrorCode("SYS.INTERNAL", 500,
                "服务暂时不可用", "请稍后重试并携带 requestId 联系支持", "系统"));
    }

    /** 标准错误响应 */
    static String render(String code, String requestId) {
        ErrorCode ec = REGISTRY.get(code);
        if (ec == null) {
            return "{\"code\":\"SYS.UNKNOWN\",\"message\":\"未知错误\",\"requestId\":\""
                    + requestId + "\"}";
        }
        return String.format(
                "{\"code\":\"%s\",\"message\":\"%s\",\"hint\":\"%s\",\"requestId\":\"%s\"}",
                ec.code(), ec.message(), ec.hint(), requestId);
    }

    static void docKit() {
        System.out.println("【文档三件套】");
        Map<String, String> kit = new LinkedHashMap<>();
        kit.put("接口参考", "OpenAPI 渲染，含参数/示例/错误码（64-06）");
        kit.put("指南", "Quick Start + 鉴权 + 错误处理 + 重试 + 最佳实践");
        kit.put("变更日志", "每版本的新增/废弃/破坏性变更（64-05）");
        kit.forEach((k, v) -> System.out.printf("  %-6s %s%n", k, v));
        System.out.println();
        System.out.println("【文档验收判据】");
        List<String> checks = List.of(
                "照着文档能从 0 跑通第一个成功调用",
                "每个错误码都能查到原因与处理建议",
                "示例代码被 CI 真实执行（防过期）",
                "鉴权与配额规则有专门章节");
        checks.forEach(c -> System.out.println("  [ ] " + c));
    }

    public static void main(String[] args) {
        register();
        System.out.println("【错误码注册表】");
        REGISTRY.values().forEach(ec -> System.out.printf("  %-22s %3d %-8s %s%n",
                ec.code(), ec.httpStatus(), ec.category(), ec.message()));
        System.out.println();
        System.out.println("【标准错误响应】");
        System.out.println("  " + render("AUTH.INVALID_SIGNATURE", "req-7f3a"));
        System.out.println("  " + render("BIZ.ORDER_NOT_FOUND", "req-8b21"));
        System.out.println("  " + render("NOT.REGISTERED", "req-9c02"));
        System.out.println();
        docKit();
    }
}
```

**课后练习**
1. 设计一套错误码规范（分层 + 命名 + HTTP 码映射），注册 10 个错误码并实现标准响应渲染。
2. 写一份"错误处理指南"：调用方应如何处理 401/403/429/5xx/业务错误，各给一段伪代码。
3. 思考题：为什么错误码不能随版本变化？从"调用方按码分支"与"兼容性成本"分析（64-05 的延伸）。

---

### 64-10 API 安全：越权、注入与滥用

**知识点**
- **开放 API 的攻击面比内部大**：**越权**（用 A 应用的 token 访问 B 应用的数据）、**注入**（SQL/命令/模板，36 章）、**滥用**（撞库、爬虫、刷配额）、**重放**（64-03）、**参数污染**（重复参数、类型混淆）。
- **越权防护的三要素**：**身份绑定资源**（查询必须带 `appId`/`tenantId` 条件，55-03 的同款）、**双键校验**（资源归属 + 调用方，越权返回 404 不泄露存在性）、**scope 最小化**（64-02）。
- **注入防护**（36 章在开放 API 的强化）：**所有输入走参数化**（SQL 21-01）、**JSON 解析限深限大**（防炸弹）、**路径参数白名单**（防目录穿越）、**不把用户输入拼进命令/模板**。
- **滥用防护**：**设备指纹/行为分析**（异常调用模式）、**验证码/人机校验**（注册与敏感接口）、**配额 + 限流**（64-04）、**黑名单与封禁**（58-07 的名单体系）、**接口级风控**（58-10 的决策链）。
- **安全响应头与错误信息**：不返回堆栈、不泄露内部路径与版本（36 章）、`X-Content-Type-Options` 等（36-10）。
- 易错点：只校验 token 不校验资源归属（横向越权）；错误信息带 SQL/堆栈（信息泄露）；限流按 IP（代理后全是一个 IP）；无恶意调用监控（被刷爆才发现）。

**示例代码**
```java
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class OpenApiSecurity {
    record Resource(String id, String ownerAppId, String data) {
    }

    /** 双键校验：资源归属 + 调用方；越权与不存在返回相同（不泄露存在性） */
    static String fetch(String callerAppId, String resourceId,
                        Map<String, Resource> store) {
        Resource r = store.get(resourceId);
        if (r == null || !r.ownerAppId().equals(callerAppId)) {
            return "404 NOT_FOUND（不区分不存在与越权）";
        }
        return "200 " + r.data();
    }

    /** 参数污染：重复参数/类型混淆的防御 */
    static String parseAmount(String raw) {
        if (raw == null || raw.isBlank()) {
            return "拒绝：缺少参数";
        }
        if (raw.contains(",")) {
            return "拒绝：检测到重复参数（参数污染）";
        }
        try {
            long v = Long.parseLong(raw);
            return v > 0 ? "接受：" + v : "拒绝：必须为正整数";
        } catch (NumberFormatException e) {
            return "拒绝：类型错误（期望整数分）";
        }
    }

    /** 滥用检测：单位时间调用量 + 失败率 */
    static String abuseCheck(int callsPerMin, double failRate) {
        if (callsPerMin > 6000) {
            return "封禁候选：调用量异常（>6000/min）";
        }
        if (failRate > 0.5 && callsPerMin > 600) {
            return "封禁候选：高失败率 + 高频（疑似撞库/探测）";
        }
        return "正常";
    }

    static void checklist() {
        System.out.println("【开放 API 安全检查单】");
        List<String> items = List.of(
                "越权：每条查询带调用方归属条件，越权返回 404（55-04）",
                "注入：SQL 参数化、JSON 限深限大、路径参数白名单（36-02）",
                "重放：时间戳 + nonce + 签名（64-03）",
                "滥用：配额 + 行为分析 + 黑名单（64-04/58-07）",
                "泄露：错误不返回堆栈/版本/内部路径（36-09）",
                "密钥：AppSecret 仅服务端、可轮换（64-03）");
        items.forEach(i -> System.out.println("  - " + i));
    }

    public static void main(String[] args) {
        Map<String, Resource> store = new LinkedHashMap<>();
        store.put("ord-1", new Resource("ord-1", "app-A", "订单A数据"));
        store.put("ord-2", new Resource("ord-2", "app-B", "订单B数据"));
        System.out.println("【越权（IDOR）防护】");
        System.out.println("  app-A 访问 ord-1：" + fetch("app-A", "ord-1", store));
        System.out.println("  app-A 访问 ord-2：" + fetch("app-A", "ord-2", store));
        System.out.println("  app-A 访问 ord-9：" + fetch("app-A", "ord-9", store));
        System.out.println();
        System.out.println("【参数污染防御】");
        for (String raw : new String[]{"6900", "6900,1", "abc", "-5", ""}) {
            System.out.printf("  amount=%-8s -> %s%n", "[" + raw + "]", parseAmount(raw));
        }
        System.out.println();
        System.out.println("【滥用检测】");
        System.out.printf("  正常：%s%n", abuseCheck(300, 0.02));
        System.out.printf("  高频：%s%n", abuseCheck(9000, 0.01));
        System.out.printf("  撞库：%s%n", abuseCheck(1200, 0.85));
        System.out.println();
        checklist();
    }
}
```

**课后练习**
1. 实现越权测试用例集（≥8 条）：改 resourceId、伪造 appId、跨 scope，全部应返回 404/403 且不泄露存在性。
2. 实现滥用检测：基于"调用量 + 失败率 + 参数异常率"三维判定，用 6 组数据验证分级处置（观察/限流/封禁）。
3. 思考题：为什么越权要返回 404 而不是 403？从"存在性枚举"与"攻击者侦察成本"分析（55-04 的同款结论）。

---

### 64-11 综合案例：支付开放平台

**知识点**
- **把本章收口成一个真实的开放平台**：**接入**（OAuth2 客户端凭证 + 签名防重放 64-02/03）-> **治理**（配额与阶梯计费 64-04）-> **契约**（OpenAPI + 版本兼容 64-05/06）-> **SDK**（自动签名/重试/错误映射 64-07）-> **网关**（鉴权链 + 审计 64-08）-> **错误码与文档**（64-09）-> **安全**（越权/注入/滥用 64-10）。
- **支付开放平台的额外要求**：**资金级审计**（每次扣款/退款留痕，51-10）、**幂等键**（`Idempotency-Key`，39/51 章）、**对账**（调用流水 vs 平台账单，51-05）、**可用性 SLA**（99.95% + 公告机制）。
- **验收清单**：**功能**（鉴权/配额/计费/版本）、**安全**（越权测试全绿/签名防重放/注入防护）、**体验**（Quick Start 5 分钟跑通）、**运营**（审计可查/账单可对/废弃有流程）。
- **与 51 章的衔接**：51 章是"支付系统内部怎么设计"，本章是"支付能力如何开放给外部商户"——**同一套状态机与幂等，加上开放平台的治理外壳**。
- 易错点：开放平台无幂等键（商户重试导致重复扣款）；配额与计费对不上（计量不准）；版本变更无公告（商户集成崩）；审计缺参数摘要（纠纷无法举证）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PaymentOpenPlatform {
    record Merchant(String appId, String name, long dailyQuota, boolean enabled) {
    }

    record ApiCall(String appId, String endpoint, String idempotencyKey,
                   long amountCents, long atMs) {
    }

    record Result(int status, String code, String hint, boolean charged) {
    }

    static final Map<String, Merchant> merchants = new LinkedHashMap<>();
    static final Map<String, Long> usageToday = new LinkedHashMap<>();
    static final Map<String, Result> idempotentCache = new LinkedHashMap<>();
    static final List<String> audit = new ArrayList<>();

    static void init() {
        merchants.put("app-pay-1", new Merchant("app-pay-1", "商户甲", 3, true));
        merchants.put("app-pay-2", new Merchant("app-pay-2", "商户乙", 100, false));
    }

    static Result call(ApiCall call) {
        Merchant m = merchants.get(call.appId());
        if (m == null || !m.enabled()) {
            return new Result(403, "AUTH.APP_DISABLED", "应用未开通或已停用", false);
        }
        String idemKey = call.appId() + "#" + call.idempotencyKey();
        Result cached = idempotentCache.get(idemKey);
        if (cached != null) {
            audit.add(call.appId() + " 重复请求（幂等键命中）-> 返回首次结果");
            return cached;
        }
        long used = usageToday.getOrDefault(call.appId(), 0L);
        if (used >= m.dailyQuota()) {
            audit.add(call.appId() + " 配额耗尽");
            return new Result(429, "QUOTA.EXCEEDED", "次日 00:00 重置或申请提额", false);
        }
        usageToday.put(call.appId(), used + 1);
        Result ok = new Result(200, "OK", "支付已受理", true);
        idempotentCache.put(idemKey, ok);
        audit.add(call.appId() + " " + call.endpoint() + " 金额 " + call.amountCents()
                + " 分 idem=" + call.idempotencyKey());
        return ok;
    }

    static void billing() {
        System.out.println("【计费与对账】");
        usageToday.forEach((appId, calls) -> {
            double fee = calls * 0.002;
            System.out.printf("  %-10s 调用 %d 次 -> 费用 %.3f 元%n", appId, calls, fee);
        });
        long totalCalls = usageToday.values().stream().mapToLong(Long::longValue).sum();
        long auditedCalls = audit.stream().filter(a -> a.contains("金额")).count();
        System.out.printf("  对账：计量调用 %d 次 vs 审计记录 %d 条 -> %s%n",
                totalCalls, auditedCalls, totalCalls == auditedCalls ? "一致" : "差异需排查");
        System.out.println();
    }

    public static void main(String[] args) {
        init();
        System.out.println("【开放能力调用】");
        List<ApiCall> calls = List.of(
                new ApiCall("app-pay-1", "/open/pay", "idem-1", 6900, 1000),
                new ApiCall("app-pay-1", "/open/pay", "idem-1", 6900, 1100),
                new ApiCall("app-pay-1", "/open/pay", "idem-2", 3900, 1200),
                new ApiCall("app-pay-1", "/open/pay", "idem-3", 12900, 1300),
                new ApiCall("app-pay-1", "/open/pay", "idem-4", 5000, 1400),
                new ApiCall("app-pay-2", "/open/pay", "idem-9", 9900, 1500));
        for (ApiCall c : calls) {
            Result r = call(c);
            System.out.printf("  %-10s %-10s idem=%-6s -> %d %-18s %s%n",
                    c.appId(), c.endpoint(), c.idempotencyKey(), r.status(),
                    r.code(), r.hint());
        }
        System.out.println();
        System.out.println("【审计流水】");
        audit.forEach(a -> System.out.println("  " + a));
        System.out.println();
        billing();
        System.out.println("【上线验收】");
        Map<String, String> gates = new LinkedHashMap<>();
        gates.put("安全", "越权用例全绿 + 签名防重放 + 注入防护（64-10）");
        gates.put("一致性", "幂等键并发测试零重复扣款（39/51 章）");
        gates.put("计费", "计量与账单对账一致（51-05）");
        gates.put("契约", "OpenAPI 通过 lint + 破坏性变更拦截（64-05/06）");
        gates.put("体验", "Quick Start 5 分钟跑通 + 错误码可行动（64-07/09）");
        gates.forEach((k, v) -> System.out.printf("  %-4s %s%n", k, v));
    }
}
```

**课后练习**
1. 端到端实现支付开放平台迷你版（鉴权 -> 幂等 -> 配额 -> 计费 -> 审计），用 6 次调用（含重复与超配额）验证全链路。
2. 为平台设计"商户接入包"：Quick Start 文档 + SDK 用法 + 错误码表 + 沙箱账号，组织一次接入演练。
3. 思考题：为什么开放平台的幂等键必须由"商户"提供而不是平台生成？从"重试时的请求识别"与"跨请求关联"分析。
