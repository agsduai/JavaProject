/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：36-04
 * 课名：CSRF 与 SameSite
 * ------------------------------------------------------------
 * 知识点：
 *   - **CSRF 利用的是「浏览器会自动带 Cookie」**：用户登录了 A 站，浏览器存着 A 的会话 Cookie。此时访问恶意站点 B，B 里放一个自动提交的表单指向 A 站的转账接口，浏览器照样把 A 的 Cookie 带上 —— 服务端看到的是「合法用户的合法请求」。
 *   - **关键点：攻击者拿不到响应，但请求已经执行了**。所以「读数据」类 CSRF 危害有限，「写数据/转账/改密码」类才是重灾区。
 *   - **CSRF Token 是标准解法**：服务端给表单下发一个随机 token（存在 session 里），提交时必须带上，服务端比对。攻击者站点读不到这个 token（同源策略），所以伪造不出来。
 *   - **Token 要满足三条**：随机不可预测、与用户会话绑定、**一次性或有时效**。放在隐藏字段或自定义头（`X-CSRF-Token`）里，不要放在 URL（会进 Referer 和日志）。
 *   - **SameSite 是浏览器层的兜底**：`SameSite=Lax`（默认）表示跨站 POST 不带 Cookie，跨站 GET 顶层导航才带；`Strict` 更严，任何跨站都不带；`None` 必须配 `Secure`。`Lax` 能挡住绝大多数 CSRF，但挡不住「同站不同子域」和部分老浏览器。
 *   - **`Lax` 的例外要小心**：顶层 GET 导航会带 Cookie，所以**任何用 GET 做写操作的接口都等于自己拆了防线**。`GET /transfer?to=x&amount=100` 是设计错误。
 *   - **双重提交 Cookie（double submit）**：token 同时放 Cookie 和请求头，服务端比对两者是否一致。不需要服务端存储，适合无状态/多实例，但要求子域完全可信（子域能写 Cookie 就能绕过）。
 *   - **校验 `Origin` / `Referer` 是补充手段**：`Origin` 头在跨站 POST 时一定存在，直接比对白名单，简单有效。`Referer` 可能被隐私设置裁掉，不能作为唯一依据。
 *   - **`SameSite` 与第三方场景冲突**：嵌入式支付、iframe 单点登录、跨站回调都需要 `SameSite=None; Secure`，这时必须靠 Token 防 CSRF。
 *   - **CORS 不是 CSRF 防护**：CORS 限制的是「读响应」，CSRF 攻击根本不需要读响应。把 `Access-Control-Allow-Origin: *` 配上 `Allow-Credentials` 反而更危险。
 *   - 易错点：只在「敏感接口」加 token，漏了新加的接口；token 复用不刷新（被 XSS 偷走后长期可用）；用 GET 做写操作；`SameSite=None` 却没配 `Secure`（Cookie 直接被丢弃）；以为 JSON 接口天然免疫（`text/plain` 表单也能发，只是拿不到响应）。
 * ============================================================
 */

package com.heima.ch36;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

// ==================== 示例代码 ====================

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

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 给 `TransferApi` 加 token 轮换：每次校验成功后作废旧 token 并下发新的，观察「同一页面重复提交」会发生什么，讨论这个体验问题怎么缓解。
 * 2. 实现一个 `CsrfFilter`：对所有非 GET 请求统一校验 `X-CSRF-Token`，GET 请求只放行白名单路径，体会「统一拦截器」比「逐个接口加」可靠在哪。
 * 3. 画一张表：分别列出 `SameSite=Strict/Lax/None` 在「同站请求、跨站表单、跨站链接、跨站 iframe」四种场景下是否携带 Cookie。
 * 4. 思考题：为什么说「CORS 配置错误 + CSRF」组合起来比单独一个更危险？`Access-Control-Allow-Origin: *` 配 `Allow-Credentials: true` 会发生什么？
 * ============================================================
 */
