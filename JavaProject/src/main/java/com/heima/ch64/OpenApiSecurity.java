/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：64-10
 * 课名：API 安全：越权、注入与滥用
 * ------------------------------------------------------------
 * 知识点：
 *   - **开放 API 的攻击面比内部大**：**越权**（用 A 应用的 token 访问 B 应用的数据）、**注入**（SQL/命令/模板，36 章）、**滥用**（撞库、爬虫、刷配额）、**重放**（64-03）、**参数污染**（重复参数、类型混淆）。
 *   - **越权防护的三要素**：**身份绑定资源**（查询必须带 `appId`/`tenantId` 条件，55-03 的同款）、**双键校验**（资源归属 + 调用方，越权返回 404 不泄露存在性）、**scope 最小化**（64-02）。
 *   - **注入防护**（36 章在开放 API 的强化）：**所有输入走参数化**（SQL 21-01）、**JSON 解析限深限大**（防炸弹）、**路径参数白名单**（防目录穿越）、**不把用户输入拼进命令/模板**。
 *   - **滥用防护**：**设备指纹/行为分析**（异常调用模式）、**验证码/人机校验**（注册与敏感接口）、**配额 + 限流**（64-04）、**黑名单与封禁**（58-07 的名单体系）、**接口级风控**（58-10 的决策链）。
 *   - **安全响应头与错误信息**：不返回堆栈、不泄露内部路径与版本（36 章）、`X-Content-Type-Options` 等（36-10）。
 *   - 易错点：只校验 token 不校验资源归属（横向越权）；错误信息带 SQL/堆栈（信息泄露）；限流按 IP（代理后全是一个 IP）；无恶意调用监控（被刷爆才发现）。
 * ============================================================
 */

package com.heima.ch64;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

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

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现越权测试用例集（≥8 条）：改 resourceId、伪造 appId、跨 scope，全部应返回 404/403 且不泄露存在性。
 * 2. 实现滥用检测：基于"调用量 + 失败率 + 参数异常率"三维判定，用 6 组数据验证分级处置（观察/限流/封禁）。
 * 3. 思考题：为什么越权要返回 404 而不是 403？从"存在性枚举"与"攻击者侦察成本"分析（55-04 的同款结论）。
 * ============================================================
 */
