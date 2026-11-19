/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：57-04
 * 课名：模式匹配：instanceof 与 switch
 * ------------------------------------------------------------
 * 知识点：
 *   - **`instanceof` 模式匹配**（Java 16 定稿）：`if (obj instanceof String s && s.length() > 3)`——**声明即赋值**，消除"判断+强转"两行变一行，且**作用域受流分析保护**（短路后 s 才可见）。替代 11 章 `instanceof` + 强转的样板。
 *   - **switch 模式匹配**（Java 21 定稿）：`case Integer i -> ...`、`case null -> ...`、`case String s when s.length() > 5 -> ...`（**when 守卫**）——支持**类型模式、null 处理、守卫条件**，配合 sealed 实现穷举。
 *   - **顺序与支配性**：模式 case 自上而下匹配，**编译器校验"不可达模式"**（子类型写在父类型之后报错）；**必须穷举**（sealed 时编译器可判定，非 sealed 需 `default`）。
 *   - **null 的显式处理**：传统 switch 遇 null 抛 NPE；**模式 switch 允许 `case null`**——"缺失值"不再靠 try-catch 兜底（44 章 null 安全的语言级支持）。
 *   - **与多态的关系**：如果行为可以放在子类里（虚方法分派），就不该用类型 switch——**类型判断是"外部行为无法绑定到类型"时的工具**（如跨模块处理第三方类型、事件分发、AST 遍历）。
 *   - **工程收益**：以 11 章的 `printInfo` 为例，sealed 事件 + 模式 switch 后，**新增一个事件类型会把所有未处理点编译错误暴露**——比"记得去改所有 if-else"可靠一个量级。
 *   - 易错点：用类型 switch 替代多态（可扩展性反而变差）；忘记 `case null`（运行期 NPE）；非 sealed 类型漏 default（编译错误但误以为是语法问题）；守卫条件用副作用（when 里改状态）。
 * ============================================================
 */

package com.heima.ch57;

import java.util.List;

// ==================== 示例代码 ====================

public class PatternMatching {
    sealed interface Payment permits CardPay, WalletPay, BankTransfer {
    }

    record CardPay(String cardNo, long cents) implements Payment {
    }

    record WalletPay(String walletId, long cents) implements Payment {
    }

    record BankTransfer(String bankCode, long cents) implements Payment {
    }

    /** 类型模式 + 守卫：小额卡支付走快速通道 */
    static String route(Payment p) {
        return switch (p) {
            case CardPay c when c.cents() < 100_00 -> "快速通道（卡小额）:" + c.cardNo();
            case CardPay c -> "标准通道（卡）:" + c.cardNo();
            case WalletPay w -> "钱包通道:" + w.walletId();
            case BankTransfer b -> "网银通道:" + b.bankCode();
        };
    }

    /** instanceof 模式：作用域受流分析保护 */
    static void describe(Object o) {
        if (o instanceof String s && s.startsWith("ORD")) {
            System.out.println("  订单号：" + s.toUpperCase());
        } else if (o instanceof Number n) {
            System.out.println("  数值：" + n.doubleValue());
        } else {
            System.out.println("  不支持的类型：" + (o == null ? "null" : o.getClass().getSimpleName()));
        }
    }

    public static void main(String[] args) {
        List<Payment> payments = List.of(
                new CardPay("6222****1234", 5000),
                new CardPay("6222****8888", 500_00),
                new WalletPay("w-1001", 3000),
                new BankTransfer("ICBC", 200_00));
        for (Payment p : payments) {
            System.out.println("  " + route(p));
        }
        describe("ord-9001");
        describe(42);
        describe(null);
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 用 sealed + 模式 switch 重写 11 章的 `instanceof` 分支逻辑，对比代码行数与"新增子类时的编译提示"。
 * 2. 实现 `sealed interface JsonValue permits JsonNull, JsonBool, JsonNum, JsonStr, JsonArr, JsonObj`，写 `toJsonString` 递归处理（练习 57-05 的嵌套解构前置）。
 * 3. 思考题：类型 switch 与多态分派的边界在哪里？举一个"用错方向"的代码并改成正确形态。
 * ============================================================
 */
