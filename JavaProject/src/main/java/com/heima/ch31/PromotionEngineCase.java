/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：31-11
 * 课名：综合案例：优惠计算引擎重构
 * ------------------------------------------------------------
 * 知识点：
 *   - 综合运用：**策略**（多种优惠算法）+ **责任链**（优惠叠加顺序与互斥）+ **建造者**（构造复杂促销规则）+ **值对象**（金额）+ **模板方法**（统一的计算骨架）。
 *   - 优惠计算的真实难点不在"算"，而在**规则组合**：满减、折扣、券、会员价可能互斥、可能叠加、可能有先后顺序，顺序不同结果不同。
 *   - 工程做法：把每个优惠抽象成 `Promotion`，带 `priority`（顺序）、`stackable`（能否叠加）、`canApply`（适用条件）、`apply`（计算结果）。
 *   - 计算骨架固定：按优先级排序 → 依次判断是否适用 → 判断是否与已应用规则冲突 → 应用 → 记录明细。这样"加一种优惠"只是加一个类。
 *   - **必须输出明细**：只给最终价，出问题时无法解释"为什么是 87.3 元"。生产系统里优惠明细是客诉排查的第一手材料。
 *   - 金额一律用 `long` 存分，避免 `double` 精度问题；四舍五入规则要显式（`Math.round` 还是"分以下抹零"），且全链路统一。
 *   - 边界要显式处理：优惠后不能为负、优惠总额不能超过商品总额、券不可与折扣同享、过期券不可用。
 *   - 易错：用 `double` 算钱；顺序硬编码在 `if` 里；优惠明细不落库；叠加规则写在注释里而不是代码里。
 * ============================================================
 */

package com.heima.ch31;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

// ==================== 示例代码 ====================

public class PromotionEngineCase {
    /** 值对象：金额（分） */
    record Money(long cents) {
        static Money ofYuan(double y) {
            return new Money(Math.round(y * 100));
        }

        Money minus(Money o) {
            return new Money(Math.max(0, cents - o.cents));
        }

        Money percentOff(int pct) {
            return new Money(cents - Math.round(cents * pct / 100.0));
        }

        @Override
        public String toString() {
            return String.format("%.2f", cents / 100.0);
        }
    }

    /** 促销规则：策略 + 元数据 */
    interface Promotion {
        String name();

        int priority();

        /** 能否与已应用的规则叠加 */
        boolean stackable();

        boolean canApply(Money current, Cart cart);

        Money apply(Money current, Cart cart);
    }

    /** 购物车上下文 */
    static class Cart {
        final Money original;
        final int memberLevel;
        final boolean hasCoupon;
        final boolean couponExpired;

        Cart(Money original, int memberLevel, boolean hasCoupon, boolean couponExpired) {
            this.original = original;
            this.memberLevel = memberLevel;
            this.hasCoupon = hasCoupon;
            this.couponExpired = couponExpired;
        }
    }

    static class MemberDiscount implements Promotion {
        public String name() {
            return "会员折扣";
        }

        public int priority() {
            return 10;
        }

        public boolean stackable() {
            return true;
        }

        public boolean canApply(Money cur, Cart c) {
            return c.memberLevel >= 2;
        }

        public Money apply(Money cur, Cart c) {
            return cur.percentOff(c.memberLevel >= 3 ? 15 : 8);
        }
    }

    static class FullReduction implements Promotion {
        public String name() {
            return "满300减50";
        }

        public int priority() {
            return 20;
        }

        public boolean stackable() {
            return true;
        }

        public boolean canApply(Money cur, Cart c) {
            return cur.cents() >= 30000;
        }

        public Money apply(Money cur, Cart c) {
            return cur.minus(Money.ofYuan(50));
        }
    }

    static class Coupon implements Promotion {
        public String name() {
            return "满200减30券";
        }

        public int priority() {
            return 30;
        }

        /** 券不可与会员折扣同享：这是业务规则，写在代码里而不是注释里 */
        public boolean stackable() {
            return false;
        }

        public boolean canApply(Money cur, Cart c) {
            return c.hasCoupon && !c.couponExpired && cur.cents() >= 20000;
        }

        public Money apply(Money cur, Cart c) {
            return cur.minus(Money.ofYuan(30));
        }
    }

    static class ExpiredCoupon implements Promotion {
        public String name() {
            return "过期券（不可用）";
        }

        public int priority() {
            return 30;
        }

        public boolean stackable() {
            return false;
        }

        public boolean canApply(Money cur, Cart c) {
            return c.hasCoupon && c.couponExpired;
        }

        public Money apply(Money cur, Cart c) {
            return cur;
        }
    }

    /** 计算明细行 */
    record Line(String promotion, Money before, Money after, String note) {
    }

    /** 建造者：组装引擎 */
    static class EngineBuilder {
        private final List<Promotion> list = new ArrayList<>();

        EngineBuilder add(Promotion p) {
            list.add(p);
            return this;
        }

        PromotionEngine build() {
            list.sort(Comparator.comparingInt(Promotion::priority));
            return new PromotionEngine(list);
        }
    }

    /** 模板骨架：排序 -> 适用 -> 冲突 -> 应用 -> 记账 */
    static class PromotionEngine {
        private final List<Promotion> promotions;

        PromotionEngine(List<Promotion> promotions) {
            this.promotions = promotions;
        }

        static EngineBuilder builder() {
            return new EngineBuilder();
        }

        List<Line> run(Cart cart) {
            List<Line> lines = new ArrayList<>();
            Money cur = cart.original;
            boolean usedNonStackable = false;

            for (Promotion p : promotions) {
                if (!p.canApply(cur, cart)) {
                    lines.add(new Line(p.name(), cur, cur, "条件不满足"));
                    continue;
                }
                if (usedNonStackable && p.stackable()) {
                    lines.add(new Line(p.name(), cur, cur, "与不可叠加规则冲突"));
                    continue;
                }
                if (!p.stackable() && usedNonStackable) {
                    lines.add(new Line(p.name(), cur, cur, "已有不可叠加规则"));
                    continue;
                }
                Money next = p.apply(cur, cart);
                String note = next.cents() == cur.cents() ? "无变化" : "已应用";
                lines.add(new Line(p.name(), cur, next, note));
                cur = next;
                if (!p.stackable()) {
                    usedNonStackable = true;
                }
            }
            return lines;
        }

        Money total(Cart cart) {
            List<Line> lines = run(cart);
            return lines.isEmpty() ? cart.original : lines.get(lines.size() - 1).after();
        }
    }

    public static void main(String[] args) {
        PromotionEngine engine = PromotionEngine.builder()
                .add(new MemberDiscount())
                .add(new FullReduction())
                .add(new Coupon())
                .add(new ExpiredCoupon())
                .build();

        System.out.println("=== 场景 1：会员3级 + 满300 + 有效券 ===");
        Cart c1 = new Cart(Money.ofYuan(400), 3, true, false);
        print(engine, c1);

        System.out.println();
        System.out.println("=== 场景 2：普通会员 + 金额不足 + 过期券 ===");
        Cart c2 = new Cart(Money.ofYuan(150), 1, true, true);
        print(engine, c2);

        System.out.println();
        System.out.println("=== 场景 3：会员2级 + 满300 + 无券 ===");
        Cart c3 = new Cart(Money.ofYuan(320), 2, false, false);
        print(engine, c3);

        System.out.println();
        System.out.println("=== 场景 4：大额 + 券与会员折扣的叠加边界 ===");
        Cart c4 = new Cart(Money.ofYuan(1000), 3, true, false);
        print(engine, c4);

        System.out.println();
        System.out.println("=== 设计要点 ===");
        System.out.println("  1. 加一种优惠 = 加一个类 + 一次 register，不改引擎");
        System.out.println("  2. 顺序由 priority 决定，不靠 if 的书写顺序");
        System.out.println("  3. 叠加/互斥是数据（stackable），不是注释");
        System.out.println("  4. 金额全程用分（long），最后才格式化");
        System.out.println("  5. 每一步都产出明细，客诉可解释");
    }

    static void print(PromotionEngine engine, Cart cart) {
        System.out.printf("  原价 %s 元（会员%d级，券=%s%s）%n",
                cart.original, cart.memberLevel,
                cart.hasCoupon ? "有" : "无",
                cart.couponExpired ? "/已过期" : "");
        for (Line l : engine.run(cart)) {
            System.out.printf("    %-12s %8s -> %8s  %s%n",
                    l.promotion(), l.before(), l.after(), l.note());
        }
        System.out.printf("  应付 %s 元%n", engine.total(cart));
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 增加"限时秒杀价"优惠（优先级最高、不可叠加），验证它与其他规则的冲突处理是否正确。
 * 2. 给 `run` 增加"优惠总额不超过商品总额"的保护，说明应该在哪一层做这个校验。
 * 3. 把 `stackable` 扩展成"可叠加组"（同组互斥、不同组可叠加），说明数据结构要怎么改。
 * 4. 为这个引擎写 5 个测试用例（含边界：0 元、优惠超过总价、券刚过期），说明哪些是等价类、哪些是边界值。
 * ============================================================
 */
