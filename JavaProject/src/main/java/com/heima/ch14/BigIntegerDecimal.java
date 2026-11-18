/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 课号：14-04
 * 课名：BigInteger和BigDecimal
 * ------------------------------------------------------------
 * 知识点：
 *   - BigInteger 表示任意大小的整数，long 装不下时使用；推荐用字符串构造 `new BigInteger("...")`，或用 `BigInteger.valueOf(long)`。
 *   - BigInteger 和 BigDecimal 都是不可变对象，add/subtract/multiply/divide 等运算不会改变原对象，而是返回新对象。
 *   - BigDecimal 解决二进制浮点数的精度丢失问题（如 double 算出 0.1 + 0.2 ≠ 0.3），适合金额等精确计算场景。
 *   - 创建建议：`new BigDecimal("0.1")` 字符串构造或 `BigDecimal.valueOf(0.1)`；避免 `new BigDecimal(0.1)`，它会把 double 本身的误差带入。
 *   - divide 除不尽时（如 10÷3）必须指定小数位数和舍入模式 RoundingMode（HALF_UP 为四舍五入），否则抛 ArithmeticException。
 *   - 比较数值大小用 compareTo；equals 会连同精度一起比较，`1.0` 和 `1.00` 用 equals 结果为 false。
 * ============================================================
 */

package com.heima.ch14;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

// ==================== 示例代码 ====================

public class BigIntegerDecimal {
    public static void main(String[] args) {
        // BigInteger：long 存不下的大整数运算
        BigInteger big1 = new BigInteger("99999999999999999999999999");
        BigInteger big2 = BigInteger.valueOf(100);
        System.out.println("大数加法：" + big1.add(big2));
        System.out.println("大数乘法：" + big1.multiply(big2));
        System.out.println("2 的 100 次方：" + BigInteger.valueOf(2).pow(100));

        // BigDecimal：解决浮点精度问题
        System.out.println(0.1 + 0.2); // double 直接算：0.30000000000000004
        BigDecimal d1 = new BigDecimal("0.1"); // 推荐用字符串构造
        BigDecimal d2 = BigDecimal.valueOf(0.2);
        System.out.println("精确加法：" + d1.add(d2)); // 0.3

        // 四则运算：add 加 / subtract 减 / multiply 乘 / divide 除
        BigDecimal price = new BigDecimal("19.99");
        BigDecimal count = BigDecimal.valueOf(3);
        System.out.println("商品总价：" + price.multiply(count)); // 59.97

        // 除不尽时必须指定精度和舍入模式，否则抛 ArithmeticException
        BigDecimal result = new BigDecimal("10")
                .divide(new BigDecimal("3"), 2, RoundingMode.HALF_UP);
        System.out.println("10 ÷ 3 保留 2 位：" + result); // 3.33

        // 比较大小用 compareTo，equals 会连精度一起比
        System.out.println(new BigDecimal("1.0").equals(new BigDecimal("1.00")));    // false
        System.out.println(new BigDecimal("1.0").compareTo(new BigDecimal("1.00"))); // 0
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 分别用 double 和 BigDecimal 计算 0.1 + 0.2 + 0.3，把两个结果都打印出来，对比它们是否一致。
 * 2. 超市结算：单价 3.5 元的商品买了 7 件，用 BigDecimal 计算总价；再把 100 元平均分给 3 人，计算每人应付金额并保留 2 位小数（四舍五入）。
 * 3. 用 BigInteger 计算 100 的阶乘（100!）并输出结果，然后统计这个结果一共有多少位数字。
 * ============================================================
 */
