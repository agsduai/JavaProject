/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：66-10
 * 课名：Agent 安全与治理
 * ------------------------------------------------------------
 * 知识点：
 *   - **Agent 是"最高权限的旁路"**：它能读改任意类、能拿到所有参数（含敏感数据 36 章）、能访问 JVM 内部——**一旦被滥用或被攻破，等于应用完全失守**。
 *   - **三类安全风险**：**权限过大**（Agent 无沙箱，等同应用进程权限）、**数据泄露**（埋点采集了密码/身份证，36-09）、**供应链**（引入来路不明的 Agent jar = 引入后门，36-10 依赖治理）。
 *   - **治理的五条**：**来源可信**（只用自研或审计过的 Agent）、**最小增强**（只增强需要的类）、**数据脱敏**（埋点参数脱敏后再上报 68-03）、**可关闭**（配置开关 + 运行时卸载）、**审计**（Agent 装了哪些、增强了什么、上报到哪）。
 *   - **与 36 章的对应**：Agent 是"运行时注入"，**与依赖注入/反序列化同级的安全面**——**生产环境禁止动态 attach 未知 Agent**（`-Djdk.attach.allowAttachSelf` 等开关要收紧）。
 *   - **合规视角**：Agent 采集的数据可能含个人信息——**要符合 68 章的分类分级与最小必要原则**；**跨境部署的 Agent 上报数据不能出境**（68-06）。
 *   - 易错点：生产环境允许任意 attach（安全后门）；埋点采集全量参数（敏感泄露）；Agent jar 来源不明（供应链风险）；无审计（不知道装了哪些 Agent）。
 * ============================================================
 */

package com.heima.ch66;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class AgentSecurity {
    /** 埋点参数的脱敏（68-03 的最小版） */
    static String mask(String field, String value) {
        if (value == null) {
            return null;
        }
        if (field.contains("phone") && value.length() >= 7) {
            return value.substring(0, 3) + "****" + value.substring(value.length() - 4);
        }
        if (field.contains("idCard") && value.length() >= 8) {
            return value.substring(0, 4) + "**********" + value.substring(value.length() - 2);
        }
        if (field.contains("password") || field.contains("token")) {
            return "***";
        }
        return value;
    }

    /** Agent 注册表：来源/增强范围/数据去向/可关闭 */
    record AgentInfo(String name, String source, List<String> enhancedPackages,
                     String sink, boolean killSwitch) {
    }

    static final List<AgentInfo> registry = new ArrayList<>();

    static void register(AgentInfo info) {
        registry.add(info);
        System.out.printf("  注册 Agent：%s 来源=%s 增强=%s 上报=%s 可关闭=%b%n",
                info.name(), info.source(), info.enhancedPackages(), info.sink(),
                info.killSwitch());
    }

    static String evaluate(AgentInfo info) {
        List<String> issues = new ArrayList<>();
        if (!info.source().equals("self") && !info.source().equals("audited")) {
            issues.add("来源不可信（供应链风险）");
        }
        if (info.enhancedPackages().contains("*")) {
            issues.add("增强范围过宽（应最小化）");
        }
        if (!info.killSwitch()) {
            issues.add("无开关（出问题无法停用）");
        }
        if (info.sink().contains("external") && info.sink().contains("raw")) {
            issues.add("原始数据出境（合规风险，68-06）");
        }
        return issues.isEmpty() ? "通过" : "拒绝：" + issues;
    }

    public static void main(String[] args) {
        System.out.println("【参数脱敏】");
        System.out.printf("  phone=13800001234 -> %s%n", mask("phone", "13800001234"));
        System.out.printf("  idCard=110101199001011234 -> %s%n",
                mask("idCard", "110101199001011234"));
        System.out.printf("  password=abc123 -> %s%n", mask("password", "abc123"));
        System.out.println();

        System.out.println("【Agent 治理注册表】");
        register(new AgentInfo("apm-agent", "self", List.of("com.heima.service"),
                "internal-otel", true));
        register(new AgentInfo("unknown-agent", "third-party", List.of("*"),
                "external-raw", false));
        register(new AgentInfo("debug-agent", "audited", List.of("com.heima.order"),
                "local-file", true));
        System.out.println();

        System.out.println("【准入评估】");
        for (AgentInfo info : registry) {
            System.out.printf("  %-14s -> %s%n", info.name(), evaluate(info));
        }
        System.out.println();

        System.out.println("【治理五条】");
        Map<String, String> rules = new LinkedHashMap<>();
        rules.put("来源可信", "自研或审计过的 Agent，禁止来路不明 jar");
        rules.put("最小增强", "只增强需要的包，禁止全量");
        rules.put("数据脱敏", "参数脱敏后再上报（36-09/68-03）");
        rules.put("可关闭", "配置开关 + removeTransformer");
        rules.put("审计", "装了哪些、增强了什么、数据去向");
        rules.forEach((k, v) -> System.out.printf("  %-6s %s%n", k, v));
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现 Agent 准入评估器：来源/增强范围/数据去向/开关四维打分，用 4 个 Agent 示例验证"拒绝与通过"。
 * 2. 实现埋点脱敏：对手机号/身份证/密码/token 四类字段掩码，验证日志中无明文。
 * 3. 思考题：为什么"生产环境禁止动态 attach 未知 Agent"？从"等同代码注入"与"供应链攻击"两个角度分析。
 * ============================================================
 */
