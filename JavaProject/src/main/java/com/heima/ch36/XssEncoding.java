/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：36-03
 * 课名：XSS 与输出编码
 * ------------------------------------------------------------
 * 知识点：
 *   - **XSS 的本质是「用户输入被当成代码执行」**。浏览器拿到 HTML 后无法区分「这段是页面作者写的」还是「这段是用户填的」，只要用户数据出现在 HTML/JS/CSS/URL 的代码位置上，就会被当代码解析。
 *   - **三种类型**：存储型（脏数据进了数据库，所有访问者中招，危害最大）、反射型（脏数据从 URL 回显到页面，靠诱导点击）、DOM 型（后端完全没参与，前端 `innerHTML = location.hash` 自己把自己打穿）。
 *   - **按上下文编码，不是「编码一次就完事」**：HTML 正文、HTML 属性、JS 字符串、CSS、URL 参数，五种上下文的转义规则完全不同。把 `&lt;` 塞进 `onclick="..."` 里，依然能被闭合引号后执行。
 *   - **HTML 实体编码**：`&` `<` `>` `"` `'` 至少这五个。属性值必须用引号包起来，不加引号的属性（`<div class=xxx>`）遇到空格就断了，编码也救不了。
 *   - **富文本要用白名单**：不能只做编码（那会连 `<b>` 都显示成文本），也不能只做黑名单（`<script>` 之外还有 `<img onerror>`、`<svg onload>`、`<iframe srcdoc>` 几十种）。用成熟的净化库（如 OWASP Java HTML Sanitizer），只允许明确的标签和属性。
 *   - **CSP 是最后一道网**：`Content-Security-Policy: default-src 'self'` 让内联脚本和外部域脚本默认失效。即使编码漏了一处，CSP 也能挡住执行。禁止 `unsafe-inline`、`unsafe-eval`。
 *   - **`HttpOnly` 让 Cookie 读不到**：XSS 最常见的目的是偷 Cookie。给会话 Cookie 加 `HttpOnly`，JS 就取不到 `document.cookie`，能显著降低危害（但仍可代替用户发请求）。
 *   - **`innerHTML` 是危险 API**：能用 `textContent` 就别用 `innerHTML`。前端框架的插值（Vue `{{ }}`、React `{}`）默认转义，但 `v-html`、`dangerouslySetInnerHTML` 会关掉保护，用之前必须过净化。
 *   - **不要用 `document.write`、`eval`、`new Function`** 拼接任何用户数据。URL 里的 `javascript:` 伪协议也要拦（`<a href="javascript:...">`）。
 *   - 易错点：只在入库时编码（导致二次编码，`&amp;lt;`）；只在输出时编码但漏了某个字段；用 `replace("<", "&lt;")` 但没处理 `>`；把 JSON 直接嵌进 `<script>` 里（`</script>` 会提前闭合）；以为「只允许数字」的字段不用管（前端校验可绕过，后端还是得转义）。
 * ============================================================
 */

package com.heima.ch36;

import java.util.LinkedHashMap;
import java.util.Map;

// ==================== 示例代码 ====================

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

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 补全 `encode` 方法，加上 `HTML_ATTR_UNQUOTED` 上下文：说明为什么这个上下文「无论怎么编码都不安全」，正确做法是什么。
 * 2. 写一个 `sanitize(String html, Set<String> allowedTags)`：用正则做粗粒度净化，再用至少三种绕过 payload 测试它，记录哪些绕过去了。
 * 3. 给一个页面设计 CSP：包含内联脚本（用 nonce）、CDN 脚本、内联样式、图片域名，写出完整的 `Content-Security-Policy` 头。
 * 4. 思考题：为什么「入库时转义」会导致二次编码问题？在什么场景下（比如 API 同时给 Web 和 App 用）必须改成「输出时编码」？
 * ============================================================
 */
