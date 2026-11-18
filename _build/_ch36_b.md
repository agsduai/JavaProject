### 36-03 XSS 与输出编码

**知识点**
- **XSS 的本质是「用户输入被当成代码执行」**。浏览器拿到 HTML 后无法区分「这段是页面作者写的」还是「这段是用户填的」，只要用户数据出现在 HTML/JS/CSS/URL 的代码位置上，就会被当代码解析。
- **三种类型**：存储型（脏数据进了数据库，所有访问者中招，危害最大）、反射型（脏数据从 URL 回显到页面，靠诱导点击）、DOM 型（后端完全没参与，前端 `innerHTML = location.hash` 自己把自己打穿）。
- **按上下文编码，不是「编码一次就完事」**：HTML 正文、HTML 属性、JS 字符串、CSS、URL 参数，五种上下文的转义规则完全不同。把 `&lt;` 塞进 `onclick="..."` 里，依然能被闭合引号后执行。
- **HTML 实体编码**：`&` `<` `>` `"` `'` 至少这五个。属性值必须用引号包起来，不加引号的属性（`<div class=xxx>`）遇到空格就断了，编码也救不了。
- **富文本要用白名单**：不能只做编码（那会连 `<b>` 都显示成文本），也不能只做黑名单（`<script>` 之外还有 `<img onerror>`、`<svg onload>`、`<iframe srcdoc>` 几十种）。用成熟的净化库（如 OWASP Java HTML Sanitizer），只允许明确的标签和属性。
- **CSP 是最后一道网**：`Content-Security-Policy: default-src 'self'` 让内联脚本和外部域脚本默认失效。即使编码漏了一处，CSP 也能挡住执行。禁止 `unsafe-inline`、`unsafe-eval`。
- **`HttpOnly` 让 Cookie 读不到**：XSS 最常见的目的是偷 Cookie。给会话 Cookie 加 `HttpOnly`，JS 就取不到 `document.cookie`，能显著降低危害（但仍可代替用户发请求）。
- **`innerHTML` 是危险 API**：能用 `textContent` 就别用 `innerHTML`。前端框架的插值（Vue `{{ }}`、React `{}`）默认转义，但 `v-html`、`dangerouslySetInnerHTML` 会关掉保护，用之前必须过净化。
- **不要用 `document.write`、`eval`、`new Function`** 拼接任何用户数据。URL 里的 `javascript:` 伪协议也要拦（`<a href="javascript:...">`）。
- 易错点：只在入库时编码（导致二次编码，`&amp;lt;`）；只在输出时编码但漏了某个字段；用 `replace("<", "&lt;")` 但没处理 `>`；把 JSON 直接嵌进 `<script>` 里（`</script>` 会提前闭合）；以为「只允许数字」的字段不用管（前端校验可绕过，后端还是得转义）。

**示例代码**
```java
import java.util.LinkedHashMap;
import java.util.Map;


public class XssEncoding {
    /** 五种上下文，各自一套转义规则 */
    enum Ctx {
        HTML_BODY("HTML 正文", "& < > \" '"),
        HTML_ATTR("HTML 属性值", "& < > \" '"),
        JS_STRING("JS 字符串", "\\ ' \" < > / 换行 U+2028 U+2029"),
        URL_PARAM("URL 参数", "除 unreserved 外全部 %XX"),
        CSS_VALUE("CSS 值", "非字母数字全部转义");

        final String name;
        final String note;

        Ctx(String name, String note) {
            this.name = name;
            this.note = note;
        }
    }

    static String htmlEscape(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                case '\'' -> sb.append("&#x27;");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    static String jsEscape(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '\'' -> sb.append("\\'");
                case '"' -> sb.append("\\\"");
                case '<' -> sb.append("\\x3c");
                case '>' -> sb.append("\\x3e");
                case '/' -> sb.append("\\/");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\u2028' -> sb.append("\\u2028");
                case '\u2029' -> sb.append("\\u2029");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    static String urlEncode(String s) {
        StringBuilder sb = new StringBuilder();
        for (byte b : s.getBytes(java.nio.charset.StandardCharsets.UTF_8)) {
            int v = b & 0xFF;
            char c = (char) v;
            boolean unreserved = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9') || c == '-' || c == '_' || c == '.' || c == '~';
            if (unreserved) {
                sb.append(c);
            } else {
                sb.append('%').append(String.format("%02X", v));
            }
        }
        return sb.toString();
    }

    static String cssEscape(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')) {
                sb.append(c);
            } else {
                sb.append('\\').append(Integer.toHexString(c)).append(' ');
            }
        }
        return sb.toString();
    }

    static String encode(Ctx ctx, String s) {
        return switch (ctx) {
            case HTML_BODY, HTML_ATTR -> htmlEscape(s);
            case JS_STRING -> jsEscape(s);
            case URL_PARAM -> urlEncode(s);
            case CSS_VALUE -> cssEscape(s);
        };
    }

    static void contexts() {
        System.out.println("【同一个输入，五种上下文五种编码】");
        String evil = "<img src=x onerror=alert(1)>";
        System.out.println("  原始输入：" + evil);
        for (Ctx c : Ctx.values()) {
            System.out.printf("  %-12s -> %s%n", c.name, encode(c, evil));
        }
        System.out.println();
        System.out.println("  只有编码后的结果落在「值」的位置上才安全：");
        System.out.println("    <div>" + encode(Ctx.HTML_BODY, evil) + "</div>");
        System.out.println("    <input value=\"" + encode(Ctx.HTML_ATTR, evil) + "\">");
        System.out.println("    <script>var name = '" + encode(Ctx.JS_STRING, evil) + "';</script>");
        System.out.println("    <a href=\"/s?q=" + encode(Ctx.URL_PARAM, evil) + "\">搜索</a>");
        System.out.println();
    }

    static void wrongContext() {
        System.out.println("【编码对了但上下文错了，照样被打穿】");
        String evil = "\" onmouseover=\"alert(1)";
        String escaped = htmlEscape(evil);
        System.out.println("  输入：" + evil);
        System.out.println("  编码后：" + escaped);
        System.out.println("  放进没加引号的属性：<div class=" + escaped + ">x</div>");
        System.out.println("    -> 引号被转义了，但属性本来就没引号，空格直接开了新属性");
        System.out.println("  正确：属性值必须加引号 <div class=\"" + escaped + "\">x</div>");
        System.out.println();
        System.out.println("  另一个经典错误：把 JSON 直接塞进 <script>");
        String json = "{\"name\":\"</script><script>alert(1)</script>\"}";
        System.out.println("    <script>var data = " + json + ";</script>");
        System.out.println("    -> 浏览器遇到 </script> 就认为脚本结束，后面的变成新脚本");
        System.out.println("    正确：先做 JS 转义，或干脆用 <script type=\"application/json\"> + 前端解析");
        System.out.println();
    }

    static void sanitizer() {
        System.out.println("【富文本：白名单净化，不是编码也不是黑名单】");
        String input = "<p>你好 <b>世界</b></p>"
                + "<img src=x onerror=alert(1)>"
                + "<script>alert('xss')</script>"
                + "<a href=\"javascript:alert(1)\">点我</a>"
                + "<iframe srcdoc=\"<script>alert(1)</script>\"></iframe>";
        System.out.println("  原始：" + input);
        System.out.println("  白名单允许：p, b, i, a[href^=http]");
        System.out.println("  净化后：<p>你好 <b>世界</b></p>");
        System.out.println("  被去掉：img（带 onerror）、script、a（javascript: 协议）、iframe");
        System.out.println("  黑名单为什么不行：");
        String[] bypasses = {
                "<img src=x onerror=alert(1)>",
                "<svg onload=alert(1)>",
                "<body onpageshow=alert(1)>",
                "<details open ontoggle=alert(1)>",
                "<iframe srcdoc=\"<script>alert(1)</script>\">",
                "<math><mtext><table><mglyph><style><img src=x onerror=alert(1)>",
        };
        for (String b : bypasses) {
            System.out.println("    " + b);
        }
        System.out.println("  永远会有你没想到的标签，所以只允许「确定安全」的那几个");
        System.out.println();
    }

    static void csp() {
        System.out.println("【CSP：编码漏了，还有最后一层】");
        Map<String, String> policies = new LinkedHashMap<>();
        policies.put("default-src 'self'", "默认只允许同源资源");
        policies.put("script-src 'self' https://cdn.example.com", "脚本只从白名单域加载");
        policies.put("object-src 'none'", "禁掉 Flash/插件类对象");
        policies.put("base-uri 'self'", "防 <base> 标签劫持相对路径");
        policies.put("frame-ancestors 'none'", "禁止被其他站点 iframe 嵌套（防点击劫持）");
        policies.put("form-action 'self'", "表单只能提交到本站");
        policies.put("upgrade-insecure-requests", "把 http 资源自动升级为 https");
        policies.forEach((k, v) -> System.out.printf("  %-48s %s%n", k, v));
        System.out.println();
        System.out.println("  绝不加：'unsafe-inline'（内联脚本可执行）、'unsafe-eval'（eval 可用）");
        System.out.println("  内联脚本要用 nonce：script-src 'nonce-随机值'，每次请求换一个");
        System.out.println();
    }

    static void cookieFlags() {
        System.out.println("【Cookie 三个标记，配合 XSS 降危害】");
        String[][] flags = {
                {"HttpOnly", "JS 读不到 document.cookie，偷不走会话"},
                {"Secure", "只在 HTTPS 上发送，防明文链路被截"},
                {"SameSite=Lax", "跨站请求不带 Cookie，防 CSRF（下一课展开）"},
        };
        for (String[] f : flags) {
            System.out.printf("  %-14s %s%n", f[0], f[1]);
        }
        System.out.println("  示例：Set-Cookie: SESSION=abc123; HttpOnly; Secure; SameSite=Lax; Path=/");
        System.out.println("  注意：HttpOnly 只防「读」，不防「代替你发请求」，所以授权仍要严");
        System.out.println();
    }

    static void dangerousApis() {
        System.out.println("【前端高危写法对照】");
        String[][] rows = {
                {"el.innerHTML = userInput", "el.textContent = userInput"},
                {"document.write(userInput)", "用 DOM API 创建节点"},
                {"eval(userInput)", "JSON.parse(userInput) + 校验结构"},
                {"new Function(userInput)", "预定义函数 + 白名单参数"},
                {"<a href=\"${userUrl}\">", "校验协议只允许 http/https"},
                {"v-html=\"userInput\"", "{{ userInput }}（默认转义）"},
                {"dangerouslySetInnerHTML", "先过净化库再渲染"},
        };
        for (String[] r : rows) {
            System.out.printf("  危险 %-32s 安全 %s%n", r[0], r[1]);
        }
        System.out.println("  框架的插值默认转义，一旦你手动关掉保护，就等于自己开了后门");
        System.out.println();
    }

    static void domXss() {
        System.out.println("【DOM 型 XSS：后端完全没参与】");
        String hash = "#<img src=x onerror=alert(1)>";
        System.out.println("  URL: https://shop.example.com/#" + hash.substring(1));
        System.out.println("  前端代码：document.getElementById('t').innerHTML = location.hash;");
        System.out.println("    -> 后端日志干干净净，WAF 也看不到，因为 payload 在 # 后面");
        System.out.println("  修复：textContent，或取出后做 HTML 编码");
        System.out.println("  同类危险源：location.search、document.referrer、window.name、postMessage 的 data");
        System.out.println();
    }

    public static void main(String[] args) {
        contexts();
        wrongContext();
        sanitizer();
        csp();
        cookieFlags();
        dangerousApis();
        domXss();
    }
}
```

**课后练习**
1. 补全 `encode` 方法，加上 `HTML_ATTR_UNQUOTED` 上下文：说明为什么这个上下文「无论怎么编码都不安全」，正确做法是什么。
2. 写一个 `sanitize(String html, Set<String> allowedTags)`：用正则做粗粒度净化，再用至少三种绕过 payload 测试它，记录哪些绕过去了。
3. 给一个页面设计 CSP：包含内联脚本（用 nonce）、CDN 脚本、内联样式、图片域名，写出完整的 `Content-Security-Policy` 头。
4. 思考题：为什么「入库时转义」会导致二次编码问题？在什么场景下（比如 API 同时给 Web 和 App 用）必须改成「输出时编码」？

---

### 36-04 CSRF 与 SameSite

**知识点**
- **CSRF 利用的是「浏览器会自动带 Cookie」**：用户登录了 A 站，浏览器存着 A 的会话 Cookie。此时访问恶意站点 B，B 里放一个自动提交的表单指向 A 站的转账接口，浏览器照样把 A 的 Cookie 带上 —— 服务端看到的是「合法用户的合法请求」。
- **关键点：攻击者拿不到响应，但请求已经执行了**。所以「读数据」类 CSRF 危害有限，「写数据/转账/改密码」类才是重灾区。
- **CSRF Token 是标准解法**：服务端给表单下发一个随机 token（存在 session 里），提交时必须带上，服务端比对。攻击者站点读不到这个 token（同源策略），所以伪造不出来。
- **Token 要满足三条**：随机不可预测、与用户会话绑定、**一次性或有时效**。放在隐藏字段或自定义头（`X-CSRF-Token`）里，不要放在 URL（会进 Referer 和日志）。
- **SameSite 是浏览器层的兜底**：`SameSite=Lax`（默认）表示跨站 POST 不带 Cookie，跨站 GET 顶层导航才带；`Strict` 更严，任何跨站都不带；`None` 必须配 `Secure`。`Lax` 能挡住绝大多数 CSRF，但挡不住「同站不同子域」和部分老浏览器。
- **`Lax` 的例外要小心**：顶层 GET 导航会带 Cookie，所以**任何用 GET 做写操作的接口都等于自己拆了防线**。`GET /transfer?to=x&amount=100` 是设计错误。
- **双重提交 Cookie（double submit）**：token 同时放 Cookie 和请求头，服务端比对两者是否一致。不需要服务端存储，适合无状态/多实例，但要求子域完全可信（子域能写 Cookie 就能绕过）。
- **校验 `Origin` / `Referer` 是补充手段**：`Origin` 头在跨站 POST 时一定存在，直接比对白名单，简单有效。`Referer` 可能被隐私设置裁掉，不能作为唯一依据。
- **`SameSite` 与第三方场景冲突**：嵌入式支付、iframe 单点登录、跨站回调都需要 `SameSite=None; Secure`，这时必须靠 Token 防 CSRF。
- **CORS 不是 CSRF 防护**：CORS 限制的是「读响应」，CSRF 攻击根本不需要读响应。把 `Access-Control-Allow-Origin: *` 配上 `Allow-Credentials` 反而更危险。
- 易错点：只在「敏感接口」加 token，漏了新加的接口；token 复用不刷新（被 XSS 偷走后长期可用）；用 GET 做写操作；`SameSite=None` 却没配 `Secure`（Cookie 直接被丢弃）；以为 JSON 接口天然免疫（`text/plain` 表单也能发，只是拿不到响应）。

**示例代码**
```java
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;


public class CsrfDefense {
    enum SameSite {
        STRICT("Strict", "任何跨站请求都不带"),
        LAX("Lax", "跨站 POST 不带，顶层 GET 导航带"),
        NONE("None", "总是带，必须配 Secure");

        final String header;
        final String note;

        SameSite(String header, String note) {
            this.header = header;
            this.note = note;
        }
    }

    record Cookie(String name, String value, boolean httpOnly, boolean secure, SameSite sameSite) {
        String setCookie() {
            StringBuilder sb = new StringBuilder(name + "=" + value + "; Path=/");
            if (httpOnly) {
                sb.append("; HttpOnly");
            }
            if (secure) {
                sb.append("; Secure");
            }
            return sb.append("; SameSite=").append(sameSite.header).toString();
        }

        /** 浏览器决定要不要带上这个 Cookie */
        boolean sentOn(boolean crossSite, boolean topLevelNavigation, String method) {
            if (!crossSite) {
                return true;
            }
            return switch (sameSite) {
                case STRICT -> false;
                case LAX -> topLevelNavigation && method.equals("GET");
                case NONE -> secure;
            };
        }
    }

    record Request(String method, String origin, boolean crossSite,
                   boolean topLevelNavigation, Map<String, String> cookies,
                   Map<String, String> headers, Map<String, String> form) {
        String cookie(String n) {
            return cookies.get(n);
        }

        String form(String n) {
            return form.get(n);
        }

        String header(String n) {
            return headers.get(n);
        }
    }

    /** 一个极简的「转账」接口，逐步加防护 */
    static class TransferApi {
        final Set<String> allowedOrigins = Set.of("https://shop.example.com");
        final Set<String> issuedTokens = new LinkedHashSet<>();
        final Map<String, String> sessionTokens = new LinkedHashMap<>();
        boolean requireToken;
        boolean checkOrigin;
        boolean requirePost;
        int transferred;

        void issueToken(String sessionId) {
            String token = "csrf-" + Integer.toHexString((sessionId + issuedTokens.size()).hashCode());
            issuedTokens.add(token);
            sessionTokens.put(sessionId, token);
        }

        String result(Request r) {
            if (requirePost && !r.method().equals("POST")) {
                return "405 只接受 POST";
            }
            if (checkOrigin) {
                String origin = r.header("Origin");
                if (origin == null || !allowedOrigins.contains(origin)) {
                    return "403 Origin 不在白名单：" + origin;
                }
            }
            if (requireToken) {
                String sessionId = r.cookie("SESSION");
                String expected = sessionTokens.get(sessionId);
                String provided = r.form("csrf_token");
                if (provided == null) {
                    provided = r.header("X-CSRF-Token");
                }
                if (expected == null || !expected.equals(provided)) {
                    return "403 CSRF token 缺失或不匹配";
                }
            }
            String amount = r.form("amount");
            transferred += amount == null ? 0 : Integer.parseInt(amount);
            return "200 转账成功，累计 " + transferred + " 元";
        }
    }

    static void attackScenario() {
        System.out.println("【CSRF 是怎么发生的】");
        System.out.println("  1. 用户在 shop.example.com 登录，浏览器存下 SESSION=abc123");
        System.out.println("  2. 用户被诱导打开 evil.example.net，页面里藏着：");
        System.out.println("       <form action=\"https://shop.example.com/transfer\" method=\"POST\">");
        System.out.println("         <input name=\"to\" value=\"attacker\">");
        System.out.println("         <input name=\"amount\" value=\"9999\">");
        System.out.println("       </form><script>document.forms[0].submit()</script>");
        System.out.println("  3. 浏览器提交时自动带上 shop.example.com 的 Cookie");
        System.out.println("  4. 服务端看到「已登录用户发起的合法请求」，转账执行");
        System.out.println("  攻击者全程读不到响应，但钱已经走了");
        System.out.println();
    }

    static void tokenDefense() {
        System.out.println("【CSRF Token：攻击者伪造不出来的随机值】");
        TransferApi api = new TransferApi();
        api.requirePost = true;
        api.requireToken = true;
        api.issueToken("session-abc");

        Map<String, String> cookies = Map.of("SESSION", "session-abc");

        Request legit = new Request("POST", "https://shop.example.com", false, false,
                cookies, Map.of(), Map.of("to", "friend", "amount", "100",
                "csrf_token", api.sessionTokens.get("session-abc")));
        System.out.println("  正常请求（带 token）：" + api.result(legit));

        Request forged = new Request("POST", "https://evil.example.net", true, false,
                cookies, Map.of(), Map.of("to", "attacker", "amount", "9999"));
        System.out.println("  伪造请求（无 token）：" + api.result(forged));

        Request guessed = new Request("POST", "https://evil.example.net", true, false,
                cookies, Map.of(), Map.of("to", "attacker", "amount", "9999",
                "csrf_token", "csrf-guess"));
        System.out.println("  伪造请求（猜 token）：" + api.result(guessed));
        System.out.println("  攻击者站点读不到 shop.example.com 的页面，所以拿不到这个 token");
        System.out.println();
    }

    static void sameSiteBehavior() {
        System.out.println("【SameSite 三种取值下的 Cookie 发送行为】");
        String[][] cases = {
                {"同站 POST", "false", "false", "POST"},
                {"跨站 POST（表单提交）", "true", "false", "POST"},
                {"跨站顶层 GET（点链接）", "true", "true", "GET"},
                {"跨站 iframe GET", "true", "false", "GET"},
        };
        for (SameSite ss : SameSite.values()) {
            Cookie c = new Cookie("SESSION", "abc123", true, true, ss);
            System.out.printf("  SameSite=%-6s %s%n", ss.header, c.setCookie());
            for (String[] t : cases) {
                boolean cross = Boolean.parseBoolean(t[1]);
                boolean top = Boolean.parseBoolean(t[2]);
                boolean sent = c.sentOn(cross, top, t[3]);
                System.out.printf("      %-22s -> %s%n", t[0], sent ? "带 Cookie（有 CSRF 风险）" : "不带（安全）");
            }
        }
        System.out.println("  Lax 的漏洞：跨站顶层 GET 仍会带 Cookie，所以写操作绝不能用 GET");
        System.out.println("  None 只在 Secure 下才生效，否则浏览器直接丢弃这个 Cookie");
        System.out.println();
    }

    static void getIsDangerous() {
        System.out.println("【用 GET 做写操作 = 自己拆掉 SameSite】");
        TransferApi api = new TransferApi();
        api.requirePost = false;
        api.requireToken = false;
        api.checkOrigin = false;

        Request getForged = new Request("GET", "https://evil.example.net", true, true,
                Map.of("SESSION", "abc123"), Map.of(),
                Map.of("to", "attacker", "amount", "9999"));
        System.out.println("  恶意页面里放一张图片：");
        System.out.println("    <img src=\"https://shop.example.com/transfer?to=attacker&amount=9999\">");
        System.out.println("  这是跨站顶层 GET，Lax 也会带 Cookie -> " + api.result(getForged));
        System.out.println("  修复：写操作一律 POST/PUT/DELETE，并校验 token 或 Origin");
        System.out.println();
    }

    static void originCheck() {
        System.out.println("【Origin 校验：最省事的一道补充防线】");
        TransferApi api = new TransferApi();
        api.requirePost = true;
        api.checkOrigin = true;
        String[] origins = {"https://shop.example.com", "https://evil.example.net", null};
        for (String o : origins) {
            Map<String, String> headers = o == null ? Map.of() : Map.of("Origin", o);
            Request r = new Request("POST", o, true, false, Map.of("SESSION", "abc123"),
                    headers, Map.of("to", "x", "amount", "1"));
            String shown = o == null ? "(缺失)" : o;
            System.out.printf("  Origin=%-28s -> %s%n", shown, api.result(r));
        }
        System.out.println("  跨站 POST 时 Origin 一定存在，直接比对白名单，成本极低");
        System.out.println("  但 Origin 可以缺失（部分老客户端），所以只能当补充，不能当唯一防线");
        System.out.println();
    }

    static void doubleSubmit() {
        System.out.println("【双重提交 Cookie：无状态方案】");
        System.out.println("  服务端下发：Set-Cookie: csrf=随机值（不加 HttpOnly，前端要读）");
        System.out.println("  前端提交时：把同一个值放进 X-CSRF-Token 头");
        System.out.println("  服务端校验：Cookie 里的 csrf == 请求头里的 X-CSRF-Token");
        String cookieToken = "t-9f3a1c";
        String[][] attempts = {
                {"正常", cookieToken, cookieToken},
                {"攻击者伪造（读不到 Cookie）", cookieToken, "t-guess"},
                {"攻击者自己种 Cookie", "t-attacker", "t-attacker"},
        };
        for (String[] a : attempts) {
            boolean ok = a[1].equals(a[2]);
            System.out.printf("  %-26s cookie=%-12s header=%-12s -> %s%n",
                    a[0], a[1], a[2], ok ? "通过" : "拒绝");
        }
        System.out.println("  最后一行说明：如果攻击者能往你的域写 Cookie（子域被控），这个方案就破了");
        System.out.println("  所以它适合「单域、子域可信」的场景，否则用服务端存储的 token");
        System.out.println();
    }

    static void commonMistakes() {
        System.out.println("【常见错误清单】");
        String[][] rows = {
                {"只给部分接口加 token", "新接口上线就漏，统一拦截器兜底"},
                {"token 放 URL", "会进 Referer、日志、浏览器历史"},
                {"token 永不过期", "被 XSS 偷走后长期可用，应绑定会话并轮换"},
                {"用 GET 做写操作", "Lax 也挡不住，必须改成 POST"},
                {"SameSite=None 不配 Secure", "浏览器直接丢弃 Cookie，功能直接坏掉"},
                {"以为 CORS 能防 CSRF", "CORS 限制读响应，CSRF 不需要读"},
                {"JSON 接口不做防护", "text/plain 表单也能发，只是读不到响应"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-26s %s%n", r[0], r[1]);
        }
        System.out.println();
    }

    static void layered() {
        System.out.println("【推荐组合：三层一起上】");
        System.out.println("  第一层 SameSite=Lax + Secure + HttpOnly  浏览器默认拦截大部分跨站请求");
        System.out.println("  第二层 CSRF Token（或双重提交）          攻击者读不到，伪造不出来");
        System.out.println("  第三层 Origin / Referer 白名单校验       零成本兜底");
        System.out.println("  另外：写操作只用 POST/PUT/DELETE；敏感操作二次验证（密码/短信）");
        System.out.println();
    }

    public static void main(String[] args) {
        attackScenario();
        tokenDefense();
        sameSiteBehavior();
        getIsDangerous();
        originCheck();
        doubleSubmit();
        commonMistakes();
        layered();
    }
}
```

**课后练习**
1. 给 `TransferApi` 加 token 轮换：每次校验成功后作废旧 token 并下发新的，观察「同一页面重复提交」会发生什么，讨论这个体验问题怎么缓解。
2. 实现一个 `CsrfFilter`：对所有非 GET 请求统一校验 `X-CSRF-Token`，GET 请求只放行白名单路径，体会「统一拦截器」比「逐个接口加」可靠在哪。
3. 画一张表：分别列出 `SameSite=Strict/Lax/None` 在「同站请求、跨站表单、跨站链接、跨站 iframe」四种场景下是否携带 Cookie。
4. 思考题：为什么说「CORS 配置错误 + CSRF」组合起来比单独一个更危险？`Access-Control-Allow-Origin: *` 配 `Allow-Credentials: true` 会发生什么？

---
