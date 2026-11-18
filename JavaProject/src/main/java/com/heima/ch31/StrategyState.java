/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：31-07
 * 课名：策略模式与状态模式
 * ------------------------------------------------------------
 * 知识点：
 *   - **策略模式**：把一组可互换的算法各自封装成类，运行时选择其一。它把"选择逻辑"和"算法实现"分开，消灭按类型铺开的长 `if/else`。
 *   - 策略的组成：`Strategy` 接口（算法契约）+ 多个 `ConcreteStrategy`（实现）+ `Context`（持有策略并委托调用）。
 *   - 策略的选择方式：调用方传入、`Map` 注册表按 key 取、Spring 注入 `List<Strategy>` 后按 `supports()` 匹配。注册表方式最常用。
 *   - 策略的局限：客户端必须知道有哪些策略（要传 key）；策略类数量会膨胀；策略之间无法自然共享中间状态。
 *   - **状态模式**：对象在内部状态改变时改变它的行为，看起来像"换了类"。与策略的结构几乎一样，但**意图相反**：策略由外部选择、状态由内部流转。
 *   - 状态机的关键：**状态迁移表**比 `if` 嵌套可靠得多。把 `(当前状态, 事件) -> 下一状态` 写成表，新增状态只需加行，且能一眼看出所有合法路径。
 *   - 状态模式 vs 状态枚举 + switch：状态少（3 个以内）、迁移简单时，枚举 + switch 更直观；状态多、每个状态行为差异大时，状态模式更好。
 *   - 两者最容易被混淆的判断：**谁决定用哪个**。外部传入 = 策略；由对象自己根据事件切换 = 状态。
 *   - 易错：策略里访问 `Context` 的可变字段造成耦合；状态机允许非法迁移（如"已取消"还能"发货"）；把状态和业务状态（数据库 status 字段）搞成两套不一致的定义。
 * ============================================================
 */

package com.heima.ch31;

import java.util.LinkedHashMap;
import java.util.Map;

// ==================== 示例代码 ====================

public class StrategyState {
    /** ===== 策略：运费计算 ===== */
    interface ShippingStrategy {
        String key();

        long fee(long weightGram, long amountCents);
    }

    static class FlatStrategy implements ShippingStrategy {
        public String key() {
            return "flat";
        }

        public long fee(long weightGram, long amountCents) {
            return 800;
        }
    }

    static class WeightStrategy implements ShippingStrategy {
        public String key() {
            return "weight";
        }

        public long fee(long weightGram, long amountCents) {
            long kg = (weightGram + 999) / 1000;
            return 500 + kg * 200;
        }
    }

    static class FreeOverStrategy implements ShippingStrategy {
        public String key() {
            return "freeOver";
        }

        public long fee(long weightGram, long amountCents) {
            return amountCents >= 9900 ? 0 : 1000;
        }
    }

    /** 注册表：新增策略只加类 + 一行注册 */
    static class ShippingCalculator {
        private final Map<String, ShippingStrategy> registry = new LinkedHashMap<>();

        ShippingCalculator register(ShippingStrategy s) {
            registry.put(s.key(), s);
            return this;
        }

        long calc(String key, long weightGram, long amountCents) {
            ShippingStrategy s = registry.get(key);
            if (s == null) {
                throw new IllegalArgumentException("未知运费策略: " + key);
            }
            return s.fee(weightGram, amountCents);
        }
    }

    /** ===== 状态：订单状态机 ===== */
    enum OrderState {
        CREATED, PAID, SHIPPED, FINISHED, CANCELLED
    }

    enum OrderEvent {
        PAY, SHIP, CONFIRM, CANCEL
    }

    static class OrderStateMachine {
        /** (状态, 事件) -> 下一状态；没有条目 = 非法迁移 */
        private static final Map<String, OrderState> TABLE = new LinkedHashMap<>();

        static {
            put(OrderState.CREATED, OrderEvent.PAY, OrderState.PAID);
            put(OrderState.CREATED, OrderEvent.CANCEL, OrderState.CANCELLED);
            put(OrderState.PAID, OrderEvent.SHIP, OrderState.SHIPPED);
            put(OrderState.PAID, OrderEvent.CANCEL, OrderState.CANCELLED);
            put(OrderState.SHIPPED, OrderEvent.CONFIRM, OrderState.FINISHED);
        }

        static void put(OrderState s, OrderEvent e, OrderState next) {
            TABLE.put(s + "|" + e, next);
        }

        private OrderState state = OrderState.CREATED;

        OrderState state() {
            return state;
        }

        boolean fire(OrderEvent e) {
            OrderState next = TABLE.get(state + "|" + e);
            if (next == null) {
                System.out.printf("    非法迁移：%s --%s--> ?%n", state, e);
                return false;
            }
            System.out.printf("    %s --%s--> %s%n", state, e, next);
            state = next;
            return true;
        }
    }

    public static void main(String[] args) {
        System.out.println("=== 1. 策略：同一份数据，三种运费算法 ===");
        ShippingCalculator calc = new ShippingCalculator()
                .register(new FlatStrategy())
                .register(new WeightStrategy())
                .register(new FreeOverStrategy());
        long[][] cases = {{500, 5000}, {2500, 5000}, {2500, 12000}};
        for (long[] c : cases) {
            System.out.printf("  重量 %4dg 金额 %6.2f 元:%n", c[0], c[1] / 100.0);
            for (String key : new String[]{"flat", "weight", "freeOver"}) {
                System.out.printf("    %-9s = %5.2f 元%n", key, calc.calc(key, c[0], c[1]) / 100.0);
            }
        }
        try {
            calc.calc("express", 100, 100);
        } catch (IllegalArgumentException e) {
            System.out.println("  " + e.getMessage());
        }

        System.out.println();
        System.out.println("=== 2. 状态：合法路径走得通 ===");
        OrderStateMachine m = new OrderStateMachine();
        m.fire(OrderEvent.PAY);
        m.fire(OrderEvent.SHIP);
        m.fire(OrderEvent.CONFIRM);
        System.out.println("  最终状态 = " + m.state());

        System.out.println();
        System.out.println("=== 3. 状态：非法路径被拒绝 ===");
        OrderStateMachine m2 = new OrderStateMachine();
        m2.fire(OrderEvent.SHIP);
        m2.fire(OrderEvent.CONFIRM);
        m2.fire(OrderEvent.CANCEL);
        System.out.println("  状态未被污染 = " + m2.state());

        System.out.println();
        System.out.println("=== 策略 vs 状态 ===");
        System.out.println("  策略：外部选择算法，算法之间互不感知，用哪个由调用方决定");
        System.out.println("  状态：内部驱动迁移，每个状态知道能接受哪些事件，用哪个由对象自己决定");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 给 `ShippingCalculator` 增加"按地区阶梯计价"策略，要求不改 `ShippingCalculator`，说明这依赖哪条原则。
 * 2. 在状态迁移表里补上"已发货可退货"（`SHIPPED --REFUND--> CANCELLED`），说明加一行会影响哪些已有路径。
 * 3. 用状态枚举 + switch 重写 `OrderStateMachine`，对比两种写法在"新增状态"时的改动量。
 * ============================================================
 */
