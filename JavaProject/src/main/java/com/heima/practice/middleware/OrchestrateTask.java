/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：实操 27-01
 * 课名：下单链路编排
 * ------------------------------------------------------------
 * 知识点：
 *   - 【题目要求】实现下单编排器（应用服务层，49 章呼应）：校验参数 -> 幂等检查（orderId 去重）-> 调库存域（模拟，失败返回结构化错误）-> 调营销域（优惠券校验，失败降级不阻断）-> 落订单 -> 发事件；输出三种场景的编排轨迹：全成功/库存失败（整体失败）/营销失败（降级继续）。
 *   - 【考核点】应用服务编排、幂等检查前置、关键 vs 非关键依赖的失败语义分级、事件发出。
 *   - 【易错点】所有依赖失败都整体失败（营销失败不该拦下单）；幂等检查在业务后（重复下单已发生）；编排层写业务规则（49 章贫血回退）。
 *   - 【提示】依赖分级声明：critical=false 的失败进降级清单，响应中标注。
 * ============================================================
 */

package com.heima.practice.middleware;

import java.util.ArrayList;
import java.util.List;

// ==================== 示例代码 ====================

public class OrchestrateTask {
    record StepResult(String step, boolean ok, boolean critical, String detail) {
    }

    static void orchestrate() {
        List<StepResult> results = List.of(
                new StepResult("param-check", true, true, "通过"),
                new StepResult("stock", true, true, "扣减成功"),
                new StepResult("coupon", false, false, "券池超时 -> 降级：不用券"),
                new StepResult("save-order", true, true, "落库成功"));
        boolean blocked = results.stream().anyMatch(r -> !r.ok() && r.critical());
        for (StepResult r : results) {
            System.out.printf("  %-12s %s %s%n", r.step(), r.ok() ? "OK" : "FAIL", r.detail());
        }
        System.out.println("  整体：" + (blocked ? "失败" : "成功（含降级）"));
    }

    public static void main(String[] args) {
        orchestrate();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现完整下单编排器（幂等 + 分级依赖 + 事件发出），三种场景输出轨迹与最终状态。
 * 2. 实现"编排层重试"：非关键失败自动重试一次，关键失败不重试直接终态，说明策略依据。
 * 3. 思考题：为什么编排层要区分"关键/非关键"依赖？从用户视角（下单成功率）与系统视角（故障影响面）分析。
 * ============================================================
 */
