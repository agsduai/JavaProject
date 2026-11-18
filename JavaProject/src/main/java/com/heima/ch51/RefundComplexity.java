/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：51-06
 * 课名：退款链路
 * ------------------------------------------------------------
 * 知识点：
 *   - **退款比支付复杂一个量级**：支付是单向流转，退款涉及**部分退款、多次退款、退款上限、原路退回、退款失败重试**——每一项都能引出错账。**退款总额不可超实付**是不变量（聚合校验 49-02 的资金版）。
 *   - **部分退款的分摊**：退 30 元从哪些明细扣？**按明细分摊 + 尾差规则**（49-09 的分摊算法在退款复用）；优惠分摊退回规则（优惠券部分退还还是作废）是业务契约（46-08），必须在创建订单时就定义。
 *   - **原路退回的渠道约束**：退回原支付渠道（微信付的退微信）——**渠道退款单号幂等**、退款可能 PENDING（渠道处理中）、退款失败要重试（渠道余额不足等临时错）；**退款也有对账**（渠道退款账单 vs 本地退款流水）。
 *   - **退款状态机**：`REFUND_CREATED -> REFUNDING -> SUCCESS / FAILED`——与支付状态机同构（51-03 原则复用），**退款不可超实付**的校验在"创建退款单"时以剩余可退金额计算。
 *   - **退款与履约的联动**：退款成功 -> 触发库存回补（事件）、优惠券是否返还（规则）、发票红冲（合规）——**退款是跨域事件源**（49-03 的发出方）。
 *   - 易错点：可退金额计算不含已退款（超退资损）；部分退款分摊不一致（对账差分）；退款 PENDING 当失败重发（渠道重复退款）；退款成功后不回补库存（超卖数据错乱）。
 * ============================================================
 */

package com.heima.ch51;

import java.util.ArrayList;
import java.util.List;

// ==================== 示例代码 ====================

public class RefundComplexity {
    /** 订单聚合视角：可退金额 = 实付 - 已退（不变量） */
    static class Order {
        final String orderId;
        final long paidCents;
        long refundedCents = 0;
        final List<String> refundIds = new ArrayList<>();

        Order(String orderId, long paidCents) {
            this.orderId = orderId;
            this.paidCents = paidCents;
        }

        long refundableCents() {
            return paidCents - refundedCents;
        }

        /** 创建退款单：不变量校验在聚合内 */
        String applyRefund(long amountCents, String refundId) {
            if (amountCents <= 0) {
                throw new IllegalArgumentException("退款金额必须为正");
            }
            if (amountCents > refundableCents()) {
                throw new IllegalArgumentException(
                        "可退 " + refundableCents() + " 分，申请 " + amountCents + " 分 -> 超退拒绝");
            }
            refundedCents += amountCents;
            refundIds.add(refundId);
            System.out.printf("  退款单 %s：%d 分（剩余可退 %d 分）%n",
                    refundId, amountCents, refundableCents());
            return refundId;
        }
    }

    static void refundFlow() {
        System.out.println("【部分退款与超退拦截】");
        Order order = new Order("9001", 6900);
        order.applyRefund(2000, "RF-1");        // 部分退 20 元
        order.applyRefund(4900, "RF-2");        // 再退剩余
        try {
            order.applyRefund(100, "RF-3");     // 已退满 -> 超退
        } catch (IllegalArgumentException e) {
            System.out.println("  超退被聚合拒绝：" + e.getMessage());
        }
        System.out.println("  不变量「累计退款 ≤ 实付」在聚合内闭环 —— 49-02 的资金应用");
        System.out.println();
    }

    static void refundStateMachine() {
        System.out.println("【退款单状态机与 PENDING 处理】");
        String[][] flow = {
                {"REFUND_CREATED", "校验可退额 -> 发起渠道退款", "本地事务"},
                {"REFUNDING", "渠道受理中（PENDING）", "查询轮询（51-02 同款）"},
                {"REFUND_SUCCESS", "渠道确认退款", "触发库存回补/券返还事件"},
                {"REFUND_FAILED", "渠道明确失败", "重试或人工；可退额度恢复"},
        };
        for (String[] f : flow) {
            System.out.printf("  %-16s %-28s %s%n", f[0], f[1], f[2]);
        }
        System.out.println("  关键：REFUNDING 不当失败重发（渠道重复退款）—— 查询确认终态");
        System.out.println();
    }

    static void refundAllocation() {
        System.out.println("【部分退款的分摊：明细级扣减】");
        long[] linePaid = {3000, 2000, 1900};      // 三明细实付（分）
        long refund = 3000;
        long remaining = refund;
        List<Long> alloc = new ArrayList<>();
        for (int i = 0; i < linePaid.length; i++) {
            long take = Math.min(remaining, linePaid[i]);
            alloc.add(take);
            remaining -= take;
        }
        for (int i = 0; i < alloc.size(); i++) {
            System.out.printf("  明细%d 实付 %d -> 退 %d（剩余可退 %d）%n",
                    i + 1, linePaid[i], alloc.get(i), linePaid[i] - alloc.get(i));
        }
        System.out.println("  尾差规则与 49-09 相同：契约定义，最后行吸收");
        System.out.println("  优惠券分摊退回规则在订单创建时即定义（46-08 契约）");
    }

    public static void main(String[] args) {
        refundFlow();
        refundStateMachine();
        refundAllocation();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现订单聚合的完整退款 API（多次/部分/超退拦截），并发退款 1000 次验证不变量永不破坏。
 * 2. 复现"REFUNDING 当失败重发"事故：渠道重复退款资损场景，修复为查询确认终态。
 * 3. 思考题：为什么"可退金额 = 实付 - 已退"必须放聚合而不是放在退款服务里计算？从并发退款与不变量守卫的角度分析。
 * ============================================================
 */
