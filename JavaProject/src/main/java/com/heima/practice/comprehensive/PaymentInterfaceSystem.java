/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 课号：实操 11-02
 * 课名：支付接口体系（接口 + 默认方法 + 多态参数）
 * ------------------------------------------------------------
 * 知识点：
 *   - 【题目要求】定义接口 `Payment`：`boolean pay(double amount)`、`String channel()`、默认方法 `default void printReceipt(double amount)` 输出小票。实现 `AliPay`（手续费 0.6%）、`WeChatPay`（手续费 0.6%）、`BankCardPay`（手续费 0.1%，最低 0.1 元）。编写 `checkout(Payment p, double amount)` 统一收银并打印渠道、手续费、实付金额。用数组存放三种支付方式逐一结算。
 *   - 【考核点】接口定义与实现、默认方法、接口作为参数类型（多态）、方法重写、金额精度处理。
 *   - 【易错点】接口方法默认 `public abstract`，实现类重写时不能降低可见性；默认方法可被实现类覆盖。
 *   - 【提示】手续费可用 `Math.round(x * 100) / 100.0` 保留两位；`checkout` 只依赖 `Payment` 接口，不依赖具体类。
 * ============================================================
 */

package com.heima.practice.comprehensive;

// ==================== 示例代码 ====================

// 技术示范（非本题答案）：接口默认方法
interface PaymentInterfaceSystemPrintable {
    String content();

    default void print() {
        System.out.println("打印内容：" + content());
    }
}

class PaymentInterfaceSystemTicket implements PaymentInterfaceSystemPrintable {
    @Override
    public String content() {
        return "电影票";
    }
}

public class PaymentInterfaceSystem {
    public static void main(String[] args) {
        new PaymentInterfaceSystemTicket().print();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 增加 `Refundable` 接口（退款方法），只让部分支付方式实现，并在结算后判断该渠道是否支持退款。
 * 2. 给 `Payment` 增加静态工厂方法 `static Payment of(String type)`，根据字符串返回对应实现，未知类型返回 null。
 * 3. 设计 `Discountable` 接口（折扣方法），让部分支付方式实现，与 `Payment` 组合完成“折扣 + 手续费”的结算。
 * ============================================================
 */
