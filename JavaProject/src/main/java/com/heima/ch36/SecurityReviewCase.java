/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：36-11
 * 课名：综合案例：一次接口安全评审
 * ------------------------------------------------------------
 * 知识点：
 *   - **安全评审不是「跑个扫描器」，而是按攻击面逐项过**。工具只能发现已知模式，业务逻辑漏洞（越权、重复提交、金额篡改）只能靠人看。
 *   - **九个必查维度**：认证与会话、授权与数据归属、输入校验、输出编码、数据保护（存储/传输/脱敏）、配置与响应头、依赖与供应链、日志与监控、业务逻辑。每个维度都要有「查什么」和「怎么验」。前八个是技术面，最后一个是业务面——「重复退款」「金额篡改」这类问题都出在这里，扫描器一个都发现不了。
 *   - **一条合格的发现要写清五件事**：位置（哪个接口哪一行）、证据（怎么复现）、影响（能造成什么后果）、修复（具体怎么改）、验证（改完怎么确认）。只写「存在越权风险」等于没写。
 *   - **分级与放行门禁**：严重（可导致数据泄露/资金损失/远程执行）与高危必须在**上线前**修完；中危可以带条件上线（有缓解措施 + 排期）；低危进迭代。门禁要写进 CI，不能靠开会决定。
 *   - **修复优先级不等于严重级别**：还要乘上「利用难度」和「暴露面」。一个公网可达、无需登录的严重漏洞，优先级远高于需要内网权限的高危漏洞。
 *   - **每个发现都要映射一个回归测试**：否则修完过两个月重构又回来了。测试要写成「用 A 的 token 请求 B 的数据，期望 404」这种可执行的断言。
 *   - **评审要覆盖「异常路径」**：正常流程大家都测，漏洞往往在「参数为空」「id 不存在」「重复提交」「并发」里。
 *   - **复盘比清单更重要**：这些问题是「需求阶段没写安全要求」「评审只看功能」「测试只测正常路径」还是「赶上线跳过了卡点」？找到流程原因，才能避免下一批同样的漏洞。
 *   - **安全左移**：在设计阶段做威胁建模（36-01），编码阶段用安全基线（36-02~36-09），提交阶段用 SCA 与 SAST 卡点（36-10），上线前做这一课的评审。
 *   - 易错点：评审只做一次，之后新接口不再过；只评审「新增代码」不看被改动的老接口；发现写了但没有责任人和截止时间；把「已加 WAF 规则」当作代码修复。
 * ============================================================
 */

package com.heima.ch36;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class SecurityReviewCase {
    enum Severity {
        CRITICAL("严重", 4),
        HIGH("高危", 3),
        MEDIUM("中危", 2),
        LOW("低危", 1);

        final String cn;
        final int weight;

        Severity(String cn, int weight) {
            this.cn = cn;
            this.weight = weight;
        }
    }

    /** 评审对象：POST /api/order/{id}/refund */
    record Finding(String area, Severity sev, String issue, String fix, String test) {
    }

    static final List<Finding> FINDINGS = List.of(
            new Finding("授权", Severity.CRITICAL,
                    "退款接口只按 orderId 查订单，未校验归属，任意用户可退别人的单",
                    "查询条件加 owner_id = 当前用户；管理员跨用户时也限制在本租户",
                    "用 A 的 token 退 B 的订单，期望 404"),
            new Finding("依赖与供应链", Severity.CRITICAL,
                    "log4j-core 2.14.1，存在 Log4Shell（CVSS 10.0），且用户输入进了日志",
                    "升级到 2.17.1；升级前先关闭 JNDI lookup 作为临时缓解",
                    "构造 ${jndi:ldap://...} 作为订单备注，确认不再发起外连"),
            new Finding("业务逻辑", Severity.HIGH,
                    "退款无幂等控制，重复提交会退多次",
                    "引入幂等键 + 数据库唯一约束（order_id, idempotency_key）",
                    "同一请求并发提交 10 次，确认只产生 1 条退款记录"),
            new Finding("认证与会话", Severity.HIGH,
                    "refresh token 永不过期、不轮换，泄露后可长期换取 access token",
                    "7 天过期 + 一次性轮换；检测旧 token 复用则注销整条会话",
                    "用已轮换的旧 refresh 换 token，期望 401 且会话失效"),
            new Finding("数据保护", Severity.HIGH,
                    "退款日志打印完整银行卡号与持卡人姓名",
                    "日志层脱敏（保留后四位），审计日志同样脱敏",
                    "grep 日志文件，确认无完整卡号"),
            new Finding("输入校验", Severity.MEDIUM,
                    "退款金额未与订单金额比对，可提交超过订单金额的值",
                    "服务端按订单重算可退金额，客户端传值仅作参考",
                    "提交金额 99999 退款 29 元的订单，期望 400"),
            new Finding("配置与响应头", Severity.MEDIUM,
                    "缺少 CSP 与 HSTS，响应头暴露 Server: nginx/1.18.0",
                    "补齐安全响应头；去掉版本信息；CSP 先 Report-Only 观察",
                    "用 curl -I 检查头是否齐全"),
            new Finding("日志与监控", Severity.MEDIUM,
                    "退款操作无审计日志，事后无法追溯谁退的",
                    "记录 谁/何时/哪笔单/金额/来源IP/请求ID，独立存储只追加",
                    "执行一次退款，确认审计表有对应记录"),
            new Finding("业务逻辑", Severity.LOW,
                    "退款接口无速率限制，可被脚本高频调用",
                    "按用户 + 订单维度限流（如 5 次/分钟）",
                    "连续调用 20 次，期望后 15 次返回 429"));

    static final String[] AREAS = {
            "认证与会话", "授权", "输入校验", "输出编码",
            "数据保护", "配置与响应头", "依赖与供应链", "日志与监控", "业务逻辑",
    };

    static void walkthrough() {
        System.out.println("【评审对象】POST /api/order/{id}/refund  退款接口");
        System.out.println("【九个维度逐项过】");
        Map<String, String> checks = new LinkedHashMap<>();
        checks.put("认证与会话", "token 有效期、refresh 轮换、注销是否即时生效");
        checks.put("授权", "数据归属、管理员边界、批量与导出入口");
        checks.put("输入校验", "金额/数量边界、类型、长度、白名单");
        checks.put("输出编码", "错误信息是否回显、JSON 是否直接拼 HTML");
        checks.put("数据保护", "卡号/手机号是否落日志、传输是否强制 HTTPS");
        checks.put("配置与响应头", "CSP/HSTS/nosniff、版本信息、CORS 白名单");
        checks.put("依赖与供应链", "SCA 扫描、传递依赖、动态版本");
        checks.put("日志与监控", "审计日志、异常告警、是否有敏感字段");
        checks.put("业务逻辑", "幂等、限额、状态机、并发");
        int i = 1;
        for (Map.Entry<String, String> e : checks.entrySet()) {
            System.out.printf("  %d. %s：%s%n", i++, pad(e.getKey(), 14), e.getValue());
        }
        System.out.println();
    }

    static void findingTable() {
        System.out.println("【发现清单（按严重级别排序）】");
        List<Finding> sorted = new ArrayList<>(FINDINGS);
        sorted.sort(Comparator.comparingInt((Finding f) -> f.sev().weight).reversed());
        System.out.println("  " + pad("级别", 6) + " | " + pad("维度", 16) + " | 问题");
        for (Finding f : sorted) {
            System.out.println("  " + pad(f.sev().cn, 6) + " | " + pad(f.area(), 16) + " | " + f.issue());
        }
        System.out.println();
        System.out.println("  共 " + FINDINGS.size() + " 条，全部可复现、可验证");
        System.out.println();
    }

    static void detail() {
        System.out.println("【单条发现要写清五件事】");
        Finding f = FINDINGS.get(0);
        System.out.println("  位置：" + f.area() + " -> 退款接口 OrderRefundController.refund()");
        System.out.println("  问题：" + f.issue());
        System.out.println("  影响：任意登录用户可对他人的订单发起退款，直接造成资金损失");
        System.out.println("  修复：" + f.fix());
        System.out.println("  验证：" + f.test());
        System.out.println();
        System.out.println("  只写「存在越权风险」等于没写 —— 开发不知道怎么改，测试不知道怎么验");
        System.out.println();
    }

    static void gate() {
        System.out.println("【放行门禁：能不能上线】");
        long critical = FINDINGS.stream().filter(f -> f.sev() == Severity.CRITICAL).count();
        long high = FINDINGS.stream().filter(f -> f.sev() == Severity.HIGH).count();
        long medium = FINDINGS.stream().filter(f -> f.sev() == Severity.MEDIUM).count();
        long low = FINDINGS.stream().filter(f -> f.sev() == Severity.LOW).count();
        System.out.printf("  严重 %d 个，高危 %d 个，中危 %d 个，低危 %d 个%n",
                critical, high, medium, low);
        System.out.println();
        System.out.println("  规则：");
        System.out.println("    严重 > 0        -> 禁止上线，修完重审");
        System.out.println("    高危 > 0        -> 禁止上线，或在有等效缓解时由安全负责人签字特批");
        System.out.println("    中危 > 0        -> 可带条件上线：必须有缓解措施 + 明确排期");
        System.out.println("    仅低危          -> 可上线，进迭代");
        System.out.println();
        String decision = critical > 0 ? "禁止上线" : (high > 0 ? "禁止上线" : "可上线");
        System.out.println("  本次结论：" + decision + "（严重 " + critical + " 个必须清零）");
        System.out.println();
    }

    static void fixPlan() {
        System.out.println("【修复计划：按批次排】");
        Map<String, List<Finding>> buckets = new LinkedHashMap<>();
        buckets.put("上线前必须完成", new ArrayList<>());
        buckets.put("上线后一周内", new ArrayList<>());
        buckets.put("本迭代内", new ArrayList<>());
        for (Finding f : FINDINGS) {
            switch (f.sev()) {
                case CRITICAL, HIGH -> buckets.get("上线前必须完成").add(f);
                case MEDIUM -> buckets.get("上线后一周内").add(f);
                case LOW -> buckets.get("本迭代内").add(f);
            }
        }
        int i = 1;
        for (Map.Entry<String, List<Finding>> e : buckets.entrySet()) {
            System.out.printf("  %s（%d 条）%n", e.getKey(), e.getValue().size());
            for (Finding f : e.getValue()) {
                System.out.printf("    %d) [%s] %s%n", i++, f.area(), f.issue());
            }
        }
        System.out.println();
        System.out.println("  每条都要有责任人和截止时间，否则等于没排");
        System.out.println();
    }

    static void regression() {
        System.out.println("【回归测试：一条发现一个用例】");
        int i = 1;
        for (Finding f : FINDINGS) {
            System.out.printf("  %d. [%s] %s%n", i++, f.sev().cn, f.test());
        }
        System.out.println();
        System.out.println("  这些用例要进 CI，不能只放在评审报告里");
        System.out.println();
    }

    static void coverage() {
        System.out.println("【维度覆盖度：有没有漏审】");
        for (String area : AREAS) {
            long n = FINDINGS.stream().filter(f -> f.area().equals(area)).count();
            String status;
            if (n == 0) {
                status = "本次无发现";
            } else if (n == 1) {
                status = "1 条";
            } else {
                status = n + " 条";
            }
            System.out.println("  " + pad(area, 18) + " -> " + status);
        }
        System.out.println();
        System.out.println("  「无发现」不等于「没问题」，只等于「这次没看出来」");
        System.out.println("  输出编码本次无发现，是因为该接口只返回 JSON，但前端的渲染页面仍需单独审");
        System.out.println();
    }

    static void whyMissed() {
        System.out.println("【复盘：这些为什么会溜进来】");
        String[][] rows = {
                {"需求阶段", "需求文档没写安全要求", "加「安全验收条件」到 DoD"},
                {"设计阶段", "没做威胁建模，没想到越权", "对涉及资金/个人数据的接口强制建模"},
                {"编码阶段", "照抄了老代码的错误写法", "提供安全基线代码模板与工具类"},
                {"测试阶段", "只测正常路径，没测双账号", "把越权/幂等/并发写进用例模板"},
                {"上线阶段", "赶时间跳过了安全卡点", "CI 卡点不可绕过，特批要留痕"},
        };
        for (String[] r : rows) {
            System.out.println("  " + pad(r[0], 10) + " | " + pad(r[1], 26) + " | " + r[2]);
        }
        System.out.println();
        System.out.println("  修 9 个漏洞只是治标；改掉上面 5 条流程才是治本");
        System.out.println();
    }

    static void finalReport() {
        System.out.println("【一页纸结论】");
        long blocking = FINDINGS.stream()
                .filter(f -> f.sev() == Severity.CRITICAL || f.sev() == Severity.HIGH)
                .count();
        System.out.println("  评审范围：退款接口 POST /api/order/{id}/refund");
        System.out.println("  评审维度：9 个，覆盖认证/授权/输入/输出/数据/配置/依赖/日志/业务");
        System.out.println("  发现问题：" + FINDINGS.size() + " 条，其中阻塞项 " + blocking + " 条");
        System.out.println("  结论：禁止上线，阻塞项清零后重审");
        System.out.println();
        System.out.println("  建议的次序：");
        System.out.println("    1) 升级 log4j（不改代码，最快见效）");
        System.out.println("    2) 退款接口加数据级授权（改动小，风险最高）");
        System.out.println("    3) 加幂等与金额校验（涉及表结构，需要联调）");
        System.out.println("    4) 日志脱敏 + 审计日志（可并行）");
        System.out.println("    5) 补响应头与限流（最后做，影响面小）");
        System.out.println();
        System.out.println("  安全评审的价值不在于「找出几个漏洞」，");
        System.out.println("  而在于让下一个接口在写的时候就避开这些坑。");
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
        walkthrough();
        findingTable();
        detail();
        gate();
        fixPlan();
        regression();
        coverage();
        whyMissed();
        finalReport();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 给 `Finding` 增加「利用难度」与「暴露面」两个字段，重写排序逻辑，让「公网可达 + 易利用」的项自动排到最前。
 * 2. 把 `gate()` 改成一个可配置的门禁：阈值（严重/高危数量）从参数传入，输出是否放行及理由。
 * 3. 为「重复提交」这条发现设计完整的测试方案：并发数、断言、如何构造幂等键、如何验证唯一约束生效。
 * 4. 思考题：为什么「已加 WAF 规则」不能算作代码层漏洞的修复？举一个 WAF 被绕过的例子。
 * ============================================================
 */
