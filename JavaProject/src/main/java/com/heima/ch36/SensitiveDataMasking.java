/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：36-09
 * 课名：敏感信息泄露与日志脱敏
 * ------------------------------------------------------------
 * 知识点：
 *   - **敏感信息泄露是最常见也最容易被忽视的漏洞**：不在业务逻辑里，而在「顺手打的一条日志」「异常栈直接返回前端」「报错页面显示 SQL」。
 *   - **数据分级**：公开 / 内部 / 机密 / 绝密。身份证、手机号、银行卡、密码、token、密钥、健康记录属于机密以上，任何出口（日志、响应、监控、消息队列、缓存、备份、截图）都要脱敏或加密。
 *   - **脱敏不是「删掉」，而是「保留可用性」**：手机号 `138****8888`、身份证 `110101********1234`、卡号 `**** **** **** 1234`。客服需要按后四位查单，所以后四位要留。
 *   - **日志要分层**：DEBUG 可以详细但要本地开关；生产默认 INFO，且**日志本身不能带敏感字段**。用日志框架的脱敏（如 Logback 的 `ReplacingCompositeConverter`、Log4j2 的 `PatternLayout` 替换）做兜底，但不要只靠它。
 *   - **异常不能直接回前端**：`e.printStackTrace()` 到响应体，会泄露类名、路径、SQL、中间件版本。应该返回统一错误码 + 请求 ID，详细信息只进服务端日志。
 *   - **HTTP 响应头也会泄露**：`Server: nginx/1.18.0`、`X-Powered-By: PHP/7.4`、`X-AspNet-Version`。关掉或改掉版本信息。
 *   - **响应体要按需返回**：`SELECT *` 后把整个实体序列化，会把 `password_hash`、`salt`、内部备注一起发出去。用 DTO 显式挑字段，不要「图省事全返回」。
 *   - **前端 Map/JSON 里的隐藏字段**：`<input type="hidden" value="user_id=1">`、注释里的 TODO、source map 里未压缩的代码，都是泄露源。
 *   - **错误信息要统一**：登录失败统一提示「用户名或密码错误」，不要区分「用户不存在」/「密码错误」（用户枚举）。忘记密码同理。
 *   - **脱敏要正则在「输出前」做，而不是在「输入时」做**：输入时脱敏会破坏业务（比如你还要用完整手机号发短信），输出时脱敏才对。
 *   - **审计日志与业务日志分开**：谁在什么时候改了什么，要能追溯，但审计日志本身也要防篡改、防泄露。
 *   - 易错点：把脱敏写在 `toString()` 里以为万无一失（反射、序列化、`Objects.toString` 可能绕过）；日志里打印完整请求体；把 token 拼进 URL；监控大盘直接显示原始数据。
 * ============================================================
 */

package com.heima.ch36;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// ==================== 示例代码 ====================

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

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 给 `Desensitizer` 增加「地址脱敏」（保留到街道，门牌打码）和「银行卡 BIN 保留」规则，并说明各自为什么这样设计。
 * 2. 写一个「日志安全断言」工具：给定一行日志，检测是否包含手机号/身份证/token 模式，命中则告警。
 * 3. 把 `regexMask` 改成支持部分保留（如手机号保留后四位），并讨论正则脱敏的误伤与漏网场景。
 * 4. 思考题：为什么脱敏要放在「输出前」而不是「输入时」？举一个输入时脱敏会破坏业务的例子。
 * ============================================================
 */
