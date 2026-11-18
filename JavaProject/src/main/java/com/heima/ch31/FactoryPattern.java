/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：31-03
 * 课名：工厂模式：简单工厂、工厂方法、抽象工厂
 * ------------------------------------------------------------
 * 知识点：
 *   - **简单工厂**：一个静态方法按 `type` 造对象。优点是调用方只认接口；缺点是新增产品要改工厂，违反开闭，且工厂会越来越胖。
 *   - **工厂方法**：每种产品一个工厂类，`Factory` 接口只声明 `create()`，新增产品 = 新增"产品类 + 工厂类"，符合开闭。
 *   - **抽象工厂**：一个工厂负责创建**一族**相关产品（如"支付渠道 + 对账文件 + 退款接口"）。换一家服务商时整族一起换，不会出现"支付宝支付配微信对账"的错配。
 *   - 三者是递进关系：产品单一且稳定 → 简单工厂；产品会不断新增 → 工厂方法；产品成族出现 → 抽象工厂。
 *   - 抽象工厂的代价：新增"一族里的新产品种类"（如再加"发票"）要改所有工厂接口，这是它的固有缺陷，属于"维度方向"的选择题。
 *   - 工厂的职责边界：**只负责创建**，不要在工厂里塞业务逻辑（下单、扣库存），否则工厂变成"上帝类"。
 *   - 与 DI 容器的关系：Spring 的 `BeanFactory` 本质就是"配置驱动的工厂"，把 `new` 从代码里搬到配置/注解，这也是依赖倒置的落地方式。
 *   - 易错：工厂返回 `null` 表示"不支持"（调用方漏判就 NPE），应抛带上下文的异常或返回 `Optional`；把简单工厂当万能，`switch` 涨到 30 个分支。
 * ============================================================
 */

package com.heima.ch31;

// ==================== 示例代码 ====================

public class FactoryPattern {
    /** 产品接口：支付渠道 */
    interface PayChannel {
        String name();

        boolean pay(long cents);
    }

    static class AliPay implements PayChannel {
        public String name() {
            return "支付宝";
        }

        public boolean pay(long cents) {
            System.out.printf("    [支付宝] 扣款 %.2f 元%n", cents / 100.0);
            return true;
        }
    }

    static class WeChatPay implements PayChannel {
        public String name() {
            return "微信支付";
        }

        public boolean pay(long cents) {
            System.out.printf("    [微信] 扣款 %.2f 元%n", cents / 100.0);
            return true;
        }
    }

    static class BankCardPay implements PayChannel {
        public String name() {
            return "银行卡";
        }

        public boolean pay(long cents) {
            System.out.printf("    [银行卡] 扣款 %.2f 元%n", cents / 100.0);
            return true;
        }
    }

    /** 简单工厂：调用方只认 key，不认实现类 */
    static class PayChannelFactory {
        static PayChannel create(String type) {
            switch (type) {
                case "alipay":
                    return new AliPay();
                case "wechat":
                    return new WeChatPay();
                case "bank":
                    return new BankCardPay();
                default:
                    throw new IllegalArgumentException("不支持的支付渠道: " + type);
            }
        }
    }

    /** 抽象工厂：一族产品 = 支付 + 对账 */
    interface Reconciler {
        String desc();
    }

    static class AliReconciler implements Reconciler {
        public String desc() {
            return "支付宝日对账单（CSV）";
        }
    }

    static class WeChatReconciler implements Reconciler {
        public String desc() {
            return "微信支付对账单（Excel）";
        }
    }

    interface PayKit {
        PayChannel pay();

        Reconciler reconciler();
    }

    static class AliKit implements PayKit {
        public PayChannel pay() {
            return new AliPay();
        }

        public Reconciler reconciler() {
            return new AliReconciler();
        }
    }

    static class WeChatKit implements PayKit {
        public PayChannel pay() {
            return new WeChatPay();
        }

        public Reconciler reconciler() {
            return new WeChatReconciler();
        }
    }

    public static void main(String[] args) {
        System.out.println("=== 简单工厂：按 key 造对象 ===");
        for (String type : new String[]{"alipay", "wechat", "bank"}) {
            PayChannel ch = PayChannelFactory.create(type);
            System.out.println("  渠道: " + ch.name());
            ch.pay(19900);
        }
        try {
            PayChannelFactory.create("unionpay");
        } catch (IllegalArgumentException e) {
            System.out.println("  未知渠道被拒绝: " + e.getMessage());
        }

        System.out.println();
        System.out.println("=== 抽象工厂：整族一起换，不会错配 ===");
        String provider = "wechat";
        PayKit kit = "alipay".equals(provider) ? new AliKit() : new WeChatKit();
        System.out.println("  支付: " + kit.pay().name());
        kit.pay().pay(29900);
        System.out.println("  对账: " + kit.reconciler().desc());

        System.out.println();
        System.out.println("=== 三种工厂对比 ===");
        System.out.println("  简单工厂   1 个方法 + switch     新增产品要改工厂（违反开闭）");
        System.out.println("  工厂方法   1 产品 1 工厂类       新增产品只加类，符合开闭");
        System.out.println("  抽象工厂   1 工厂 1 产品族       换供应商整族切换，维度增加要改接口");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 给简单工厂加"云闪付"渠道，说明要改哪几处；再用工厂方法实现一遍，对比改动范围。
 * 2. 给抽象工厂增加"退款"产品，列出所有需要改动的类，并说明这暴露了抽象工厂的什么缺陷。
 * 3. 用 `Map<String, Supplier<PayChannel>>` 重写简单工厂，去掉 `switch`，说明这样改的收益与代价。
 * ============================================================
 */
