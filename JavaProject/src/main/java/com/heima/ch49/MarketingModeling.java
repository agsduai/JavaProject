/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：49-09
 * 课名：领域建模实战：营销与优惠
 * ------------------------------------------------------------
 * 知识点：
 *   - **营销域是检验建模能力的试金石**：优惠券有“满减/折扣/秒杀价”多种类型、有使用门槛、叠加规则、有效期、库存——**规则交织度高，贫血模型在这里最容易失控**（一个 if-else 大山）。
 *   - **建模的第一步：识别值对象与多态**——“优惠”是策略族：`CouponPolicy` 接口 + `FullReduction`（满减）/ `PercentageDiscount`（折扣）/ `FixedAmount`（固定金额）实现——**策略模式（31 章）在领域内的正名**：不是“代码技巧”，是业务本身的多态。
 *   - **叠加规则是一等公民**：哪些优惠可叠加（优惠券+会员折扣）、不可叠加（两张券互斥）、计算顺序（先商品折扣再满减）——**叠加策略独立成对象**（`StackingRule`），不要散落在计算流程的 if 里。
 *   - **优惠计算的可测试性**：纯函数计算（输入订单上下文 + 优惠清单 -> 输出分摊结果）——**分摊（分摊到每个订单明细）要处理除不尽**（金额尾差按行分摊，最后一行吸收尾差——BigDecimal 必备功夫）。
 *   - **营销上下文与交易上下文的边界**：交易只关心“最终应付金额”与“优惠明细快照”（用于审计与退款）；**算价过程是营销域的事**——交易侧保存计算结果快照而非实时重算（价格快照原则）。
 *   - 易错点：优惠规则写死在订单 Service（营销改动波及交易）；金额用 double（精度灾难，14 章 BigDecimal 的领域级应用）；分摊尾差随机分配（对账差一分钱）；优惠库存与交易扣减跨上下文强一致（用事件+校验兜底，49-03）。
 * ============================================================
 */

package com.heima.ch49;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

// ==================== 示例代码 ====================

public class MarketingModeling {
    record Money(BigDecimal amount) {
        static Money of(String v) {
            return new Money(new BigDecimal(v));
        }

        Money minus(Money o) {
            return new Money(amount.subtract(o.amount));
        }

        boolean greaterThan(Money o) {
            return amount.compareTo(o.amount) > 0;
        }

        int cents() {
            return amount.movePointRight(2).intValueExact();
        }
    }

    /** 策略族：优惠本身是多态的业务概念 */
    interface CouponPolicy {
        String name();

        Money discountFor(Money orderAmount);
    }

    record FullReduction(Money threshold, Money off) implements CouponPolicy {
        public String name() {
            return "满" + threshold.amount() + "减" + off.amount();
        }

        public Money discountFor(Money orderAmount) {
            return orderAmount.greaterThan(threshold) || orderAmount.amount().compareTo(threshold.amount()) == 0
                    ? off : Money.of("0");
        }
    }

    record PercentageDiscount(int percent, Money cap) implements CouponPolicy {
        public String name() {
            return percent + "%折扣（上限" + cap.amount() + "）";
        }

        public Money discountFor(Money orderAmount) {
            Money d = new Money(orderAmount.amount()
                    .multiply(BigDecimal.valueOf(percent))
                    .divide(BigDecimal.valueOf(100)));
            return d.greaterThan(cap) ? cap : d;
        }
    }

    /** 叠加规则：独立建模，不散落 if */
    record StackingRule(List<String> exclusiveGroups) {
        boolean canStack(String couponA, String couponB) {
            return true;   // 简化：本例全部可叠，互斥组留作练习
        }
    }

    static void calculate() {
        System.out.println("【订单 100 元，两张券叠加计算】");
        List<CouponPolicy> coupons = List.of(
                new FullReduction(Money.of("80"), Money.of("20")),
                new PercentageDiscount(10, Money.of("15")));
        Money orderAmount = Money.of("100.00");
        Money total = Money.of("0.00");
        for (CouponPolicy c : coupons) {
            Money d = c.discountFor(orderAmount);
            total = new Money(total.amount().add(d.amount()));
            System.out.printf("  %-16s -> 优惠 %s 元%n", c.name(), d.amount());
        }
        Money payable = orderAmount.minus(total);
        System.out.printf("  应付 = 100 - %s = %s 元（%d 分）%n", total.amount(), payable.amount(), payable.cents());
        System.out.println("  快照原则：交易侧保存“优惠明细+应付金额”快照，退款按快照走");
        System.out.println();
    }

    static void allocationTail() {
        System.out.println("【分摊尾差：最后一行吸收】");
        Money payable = Money.of("10.00");
        int[] lineWeights = {1, 1, 1};
        int totalCents = payable.cents();
        int per = totalCents / lineWeights.length;
        int[] alloc = new int[lineWeights.length];
        int assigned = 0;
        for (int i = 0; i < alloc.length; i++) {
            alloc[i] = per;
            assigned += per;
        }
        alloc[alloc.length - 1] += totalCents - assigned;   // 尾差给最后一行
        for (int i = 0; i < alloc.length; i++) {
            System.out.printf("  明细%d 分摊 %d 分%n", i + 1, alloc[i]);
        }
        System.out.printf("  合计 %d 分 = 应付 %d 分（对账恒等）%n", alloc[0] + alloc[1] + alloc[2], totalCents);
        System.out.println("  尾差规则写进契约：随机分配会导致退款与对账差一分钱");
    }

    public static void main(String[] args) {
        calculate();
        allocationTail();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 给策略族补上 `FixedAmount` 与互斥组 `StackingRule`（两张同类券互斥），用单元测试覆盖叠加矩阵。
 * 2. 复现分摊尾差事故：随机分配 3 行 10 元，构造对账差一分钱的用例，然后改成“最后一行吸收”。
 * 3. 思考题：为什么交易侧保存“优惠快照”而不是“实时重算”？从退款依据、审计、促销下线三个场景分析。
 * ============================================================
 */
