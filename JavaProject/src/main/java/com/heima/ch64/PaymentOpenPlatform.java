/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：64-11
 * 课名：综合案例：支付开放平台
 * ------------------------------------------------------------
 * 知识点：
 *   - **把本章收口成一个真实的开放平台**：**接入**（OAuth2 客户端凭证 + 签名防重放 64-02/03）-> **治理**（配额与阶梯计费 64-04）-> **契约**（OpenAPI + 版本兼容 64-05/06）-> **SDK**（自动签名/重试/错误映射 64-07）-> **网关**（鉴权链 + 审计 64-08）-> **错误码与文档**（64-09）-> **安全**（越权/注入/滥用 64-10）。
 *   - **支付开放平台的额外要求**：**资金级审计**（每次扣款/退款留痕，51-10）、**幂等键**（`Idempotency-Key`，39/51 章）、**对账**（调用流水 vs 平台账单，51-05）、**可用性 SLA**（99.95% + 公告机制）。
 *   - **验收清单**：**功能**（鉴权/配额/计费/版本）、**安全**（越权测试全绿/签名防重放/注入防护）、**体验**（Quick Start 5 分钟跑通）、**运营**（审计可查/账单可对/废弃有流程）。
 *   - **与 51 章的衔接**：51 章是"支付系统内部怎么设计"，本章是"支付能力如何开放给外部商户"——**同一套状态机与幂等，加上开放平台的治理外壳**。
 *   - 易错点：开放平台无幂等键（商户重试导致重复扣款）；配额与计费对不上（计量不准）；版本变更无公告（商户集成崩）；审计缺参数摘要（纠纷无法举证）。
 * ============================================================
 */

package com.heima.ch64;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

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

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 端到端实现支付开放平台迷你版（鉴权 -> 幂等 -> 配额 -> 计费 -> 审计），用 6 次调用（含重复与超配额）验证全链路。
 * 2. 为平台设计"商户接入包"：Quick Start 文档 + SDK 用法 + 错误码表 + 沙箱账号，组织一次接入演练。
 * 3. 思考题：为什么开放平台的幂等键必须由"商户"提供而不是平台生成？从"重试时的请求识别"与"跨请求关联"分析。
 * ============================================================
 */

/*
 * ============================================================
 * 本章综合练习（无参考答案）
 * ------------------------------------------------------------
 * 【场景】
 * 做一个开放平台：应用注册与 AppKey/Secret、签名与防重放、限流配额、权限 Scope、API 版本演进、错误码规范、开发者文档与沙箱。核心是「第三方不可信」。
 * 【任务】
 * 1. 实现签名机制：参数排序 + 时间戳 + nonce + Secret 做 HMAC，服务端校验签名与时间窗口（如 5 分钟），拒绝重放。
 * 2. 实现分级限流：按应用、按接口、按 IP 三个维度限流，超限返回 429 并带 Retry-After。
 * 3. 设计权限模型：Scope 授权（读订单/写订单/退款），应用只能调用已授权接口，越权返回 403。
 * 4. 设计版本演进：URL 版本（/v1/、/v2/）与字段兼容策略（只加不删、字段废弃走公告期），说明为什么不能直接改老接口。
 * 【验收清单】
 * [ ] 篡改任一参数或复用旧 nonce 的请求被拒绝，错误信息明确。
 * [ ] 限流在阈值处精确生效，不同应用之间互不影响。
 * [ ] 未授权 Scope 的调用被拦截，且不会泄露接口是否存在。
 * [ ] 新旧版本可并行运行，老版本在公告期内仍可用。
 * ============================================================
 */
