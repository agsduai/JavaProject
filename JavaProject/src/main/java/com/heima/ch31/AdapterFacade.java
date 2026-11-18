/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：31-06
 * 课名：适配器模式与外观模式
 * ------------------------------------------------------------
 * 知识点：
 *   - **适配器**：把一个类的接口转换成客户端期望的另一个接口，解决"接口不兼容但功能要复用"的问题。典型场景：接入第三方 SDK、老系统改造、替换中间件。
 *   - 两种写法：**类适配器**（继承被适配者 + 实现目标接口，受单继承限制）、**对象适配器**（组合被适配者，推荐，能适配多个实现）。
 *   - 适配器只做"翻译"：参数格式转换、方法名映射、返回值包装。**不要在里面写业务逻辑**，否则它会变成"隐形业务层"，最难维护。
 *   - 双向适配器：同时实现两个接口，让两边互相调用，用于渐进式替换老系统。
 *   - **外观（Facade）**：为一组复杂子系统提供统一的高层接口，降低调用方的认知负担。它不封装子系统本身，子系统仍可被直接使用。
 *   - 外观 vs 适配器的区别：适配器改变**接口形状**（一个变另一个），外观**简化调用流程**（多个变一个）；外观不改变子系统的接口。
 *   - 外观的边界：只做"编排 + 顺序 + 异常归一"，不要在外观里塞业务规则，否则又造出一个上帝类。
 *   - 易错：适配器里抛原始异常（调用方无法统一处理），应转成自己的异常体系；外观把子系统的返回 `null` 直接透出；类适配器因继承把被适配者的所有方法都暴露出去。
 * ============================================================
 */

package com.heima.ch31;

// ==================== 示例代码 ====================

public class AdapterFacade {
    /** 我方统一接口 */
    interface SmsSender {
        boolean send(String phone, String text);
    }

    /** 第三方老接口：方法名、参数、返回值都不同 */
    static class LegacySmsApi {
        int sendMessage(String[] mobiles, String content, int priority) {
            System.out.printf("    [Legacy] 批量发送 %d 条，优先级 %d，内容：%s%n",
                    mobiles.length, priority, content);
            return mobiles.length;
        }
    }

    /** 对象适配器：组合 + 翻译，只做接口转换 */
    static class LegacySmsAdapter implements SmsSender {
        private final LegacySmsApi api = new LegacySmsApi();

        public boolean send(String phone, String text) {
            if (phone == null || phone.length() != 11) {
                return false;
            }
            int sent = api.sendMessage(new String[]{phone}, text, 1);
            return sent == 1;
        }
    }

    /** 另一家服务商：接口长得像，但参数语义不同（毫秒 vs 秒） */
    static class CloudSmsApi {
        boolean push(String mobile, String msg, long delaySeconds) {
            System.out.printf("    [Cloud] 发送到 %s，延迟 %d 秒：%s%n", mobile, delaySeconds, msg);
            return true;
        }
    }

    static class CloudSmsAdapter implements SmsSender {
        private final CloudSmsApi api = new CloudSmsApi();

        public boolean send(String phone, String text) {
            return api.push(phone, text, 0);
        }
    }

    /** 外观：把下单涉及的 4 个子系统收成一个方法 */
    static class InventoryService {
        boolean lock(String sku, int qty) {
            System.out.println("    [库存] 锁定 " + sku + " x" + qty);
            return true;
        }
    }

    static class PromotionService {
        long discount(String sku, long cents) {
            System.out.println("    [优惠] 计算折扣");
            return cents >= 10000 ? 1500 : 0;
        }
    }

    static class PaymentService {
        boolean pay(long cents) {
            System.out.println("    [支付] 扣款 " + cents / 100.0 + " 元");
            return true;
        }
    }

    static class OrderFacade {
        private final InventoryService inventory = new InventoryService();
        private final PromotionService promotion = new PromotionService();
        private final PaymentService payment = new PaymentService();
        private final SmsSender sms = new LegacySmsAdapter();

        /** 调用方只需这一个方法，不用知道 4 个子系统的顺序 */
        boolean placeOrder(String phone, String sku, int qty, long unitCents) {
            if (!inventory.lock(sku, qty)) {
                return false;
            }
            long total = unitCents * qty;
            long pay = total - promotion.discount(sku, total);
            if (!payment.pay(pay)) {
                return false;
            }
            sms.send(phone, "下单成功，实付 " + pay / 100.0 + " 元");
            return true;
        }
    }

    public static void main(String[] args) {
        System.out.println("=== 1. 适配器：统一两种第三方短信 ===");
        SmsSender[] senders = {new LegacySmsAdapter(), new CloudSmsAdapter()};
        for (SmsSender s : senders) {
            System.out.println("  使用 " + s.getClass().getSimpleName() + "：");
            System.out.println("    结果 = " + s.send("13800138000", "您的验证码是 8888"));
        }
        System.out.println("  非法手机号：");
        System.out.println("    结果 = " + senders[0].send("123", "x"));

        System.out.println();
        System.out.println("=== 2. 外观：一次调用编排 4 个子系统 ===");
        OrderFacade facade = new OrderFacade();
        boolean ok = facade.placeOrder("13800138000", "SKU-A1001", 2, 6990);
        System.out.println("  下单结果 = " + ok);

        System.out.println();
        System.out.println("=== 适配器 vs 外观 ===");
        System.out.println("  适配器：接口形状转换（一个接口变另一个），只翻译不加工");
        System.out.println("  外观：调用流程简化（多个变一个），只编排不封装");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 写一个类适配器版本的 `LegacySmsAdapter`（继承 `LegacySmsApi`），说明它为什么把父类所有方法都暴露了。
 * 2. 给 `OrderFacade` 增加"取消订单"流程（反向：退款 → 解锁库存 → 发通知），说明编排逻辑应该放在哪一层。
 * 3. 找一个真实场景（如日志框架 SLF4J 适配 Log4j），说明适配器在其中扮演的角色。
 * ============================================================
 */
