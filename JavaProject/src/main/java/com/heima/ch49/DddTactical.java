/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：49-02
 * 课名：战术设计：聚合、实体与值对象
 * ------------------------------------------------------------
 * 知识点：
 *   - **聚合（Aggregate）是事务与一致性的边界**：聚合根（Aggregate Root）是外部唯一入口，聚合内部对象只能被根引用——**跨聚合引用必须用 ID，不能持对象引用**。这条纪律让"哪些修改可以放在一个事务里"有了明确答案：**一个事务只改一个聚合**（39-09 跨库事务的根本答案）。
 *   - **实体 vs 值对象的判据**：**实体**有身份（订单 9001 换了地址还是订单 9001），靠 ID 区分、有生命周期；**值对象**无身份靠属性区分（金额 69.00 元与另一个 69.00 元不可区分），不可变、可替换。**"它有 ID 吗"是第一个问的问题**。
 *   - **聚合划小原则**：聚合越小事务越短、并发冲突越少——**跨聚合协作用领域事件最终一致**（订单聚合与库存聚合之间用 OrderPaid 事件，而不是一个巨大聚合包住两者）。大聚合是 DDD 最常见的失败原因。
 *   - **不变量（invariant）归属聚合**："订单明细之和 ≥ 0"、"同一 SKU 明细唯一"这类规则必须**在一个事务内可验证**——它们定义聚合边界（规则涉及谁，聚合就包含谁）。
 *   - **领域事件建模**：过去时命名（OrderPaid/OrderCancelled）、不可变、带业务字段与发生时间——**事件是跨聚合/跨上下文的粘合剂**（28 章 MQ、39 章本地消息表的领域层根基）。
 *   - **仓储（Repository）只为聚合根定义**：`OrderRepository.findById/save`——聚合整体存取；聚合内部对象通过根访问，不单独建 Repository。
 *   - 易错点：聚合划成"一张表一个聚合"（OrderItem 单独聚合 -> 一致性无处安放）；聚合间持对象引用（加载一个聚合拖出半个库）；值对象做成可变实体（金额还能 set）；领域事件带可变引用（事件应不可变）。
 * ============================================================
 */

package com.heima.ch49;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

// ==================== 示例代码 ====================

public class DddTactical {
    /** 值对象：不可变、靠属性判等 */
    record Money(BigDecimal amount, String currency) {
        Money add(Money other) {
            if (!currency.equals(other.currency)) {
                throw new IllegalArgumentException("币种不同不可相加");
            }
            return new Money(amount.add(other.amount), currency);
        }

        boolean isPositive() {
            return amount.compareTo(BigDecimal.ZERO) > 0;
        }
    }

    /** 实体：订单明细（有身份，但只在聚合内可见） */
    static class OrderLine {
        final String sku;
        int quantity;
        Money unitPrice;

        OrderLine(String sku, int quantity, Money unitPrice) {
            this.sku = sku;
            this.quantity = quantity;
            this.unitPrice = unitPrice;
        }

        Money subtotal() {
            return new Money(unitPrice.amount().multiply(BigDecimal.valueOf(quantity)),
                    unitPrice.currency());
        }
    }

    /** 聚合根：唯一入口，不变量在聚合内闭环 */
    static class Order {
        final String orderId;
        final List<OrderLine> lines = new ArrayList<>();
        private Money coupon;
        private String status = "DRAFT";

        Order(String orderId, Money currency) {
            this.orderId = orderId;
            this.coupon = currency;
        }

        void addLine(String sku, int qty, Money unitPrice) {
            if (!"DRAFT".equals(status)) {
                throw new IllegalStateException("已下单订单不可改明细");
            }
            lines.add(new OrderLine(sku, qty, unitPrice));
        }

        Money subtotal() {
            return lines.stream().map(OrderLine::subtotal)
                    .reduce(new Money(BigDecimal.ZERO, coupon.currency()), Money::add);
        }

        /** 不变量校验：抵扣不可超订单额 —— 规则收在聚合内 */
        void applyCoupon(Money discount) {
            if (!discount.isPositive()) {
                throw new IllegalArgumentException("抵扣必须为正");
            }
            if (discount.amount().compareTo(subtotal().amount()) > 0) {
                throw new IllegalArgumentException("抵扣不可超过订单总额");
            }
            this.coupon = discount;
        }

        void submit() {
            if (lines.isEmpty()) {
                throw new IllegalStateException("空订单不可提交");
            }
            this.status = "PLACED";
            System.out.println("  事件发布: OrderPlaced(orderId=" + orderId + ")");
        }
    }

    static void tacticalDemo() {
        System.out.println("【聚合内交互：规则与不变量闭环】");
        Money cny = new Money(new BigDecimal("10.00"), "CNY");
        Order order = new Order("9001", cny);
        order.addLine("SKU-A", 2, cny);
        order.addLine("SKU-B", 1, new Money(new BigDecimal("25.00"), "CNY"));
        System.out.printf("  小计 = %s 元%n", order.subtotal().amount());
        order.applyCoupon(new Money(new BigDecimal("5.00"), "CNY"));
        System.out.println("  应用优惠券 5 元 -> 通过（不超小计 45 元）");
        try {
            order.applyCoupon(new Money(new BigDecimal("999.00"), "CNY"));
        } catch (IllegalArgumentException e) {
            System.out.println("  超额抵扣被聚合拒绝：" + e.getMessage());
        }
        order.submit();
        System.out.println();
    }

    static void boundaries() {
        System.out.println("【聚合边界三问】");
        List<String> questions = List.of(
                "① 这条不变量要在哪个事务内成立？（涉及谁，聚合就含谁）",
                "② 谁是外部访问的唯一入口？（聚合根，其余全内部）",
                "③ 跨聚合协作怎么传？（ID 引用 + 领域事件，一个事务只改一个聚合）");
        for (String q : questions) {
            System.out.println("  " + q);
        }
        System.out.println("  反例：OrderItem 单独成聚合 -> “明细之和>=0”跨了两个事务 -> 不变量失效");
    }

    public static void main(String[] args) {
        tacticalDemo();
        boundaries();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 为订单聚合补上"同一 SKU 明细唯一"与"提交后金额不可变"两条不变量，写单元测试验证。
 * 2. 把你项目里某个可变"金额类"改造成不可变值对象，列出改造后代码调用方式的变化。
 * 3. 思考题：为什么"一个事务只改一个聚合"能根本回答 39-09 的跨库事务问题？从聚合边界与事务边界重合的推导读解。
 * ============================================================
 */
