/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：64-04
 * 课名：配额、限流与计费
 * ------------------------------------------------------------
 * 知识点：
 *   - **配额（Quota）与限流（Rate Limit）的区别**：**配额是"一段时间内的总量"**（每日 10 万次，64 章的核心），**限流是"瞬时的速率"**（每秒 100 次）——**两者组合才是完整的保护**（44-08 限流 + 本章配额）。
 *   - **配额的三级维度**：**应用级**（整个 App 的额度）、**接口级**（不同接口不同额度，读多写少）、**时间窗**（日/月配额 + 峰值速率）——**配额超限返回 429 + 剩余额度信息**（开发者可自助排查）。
 *   - **计费模型**：**按调用次数**（简单）、**按阶梯**（前 1 万免费，之后递减单价）、**按资源**（存储/流量/算力）、**包年包月 + 超额**——**计费与配额共用一套计量**（64-04 的计量是 51-05 对账的输入）。
 *   - **计量要准确且可对账**：每次调用记录 `appId/接口/耗时/结果/计费单位`——**计量数据与账单对账**（51-05 思想）；**计量失败要有兜底**（异步补记、日终对账）。
 *   - **限流的实现**（44 章手写 + 53-05 Sentinel）：令牌桶（允许突发）、漏桶（匀速）、滑动窗口（精确）——**开放平台通常用"令牌桶 + 日配额"组合**（突发容忍 + 总量控制）。
 *   - 易错点：只有速率限流无总量配额（一天被打 1 亿次）；配额超限返回 500（应为 429 + 明确信息）；计量丢失（账单对不上）；配额恢复时间不明确（开发者不知道何时重置）。
 * ============================================================
 */

package com.heima.ch64;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

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

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现"日配额 + QPS 限流"双控，验证四类场景（正常/超速率/超日配额/窗口重置）并输出 429 响应头设计。
 * 2. 实现阶梯计费与计量明细对账：模拟 10 万次调用，验证"账单金额 = 明细聚合"。
 * 3. 思考题：为什么配额超限要返回 429 而不是 500？从"开发者可自助处理"与"区分系统故障与限流"两个角度分析。
 * ============================================================
 */
