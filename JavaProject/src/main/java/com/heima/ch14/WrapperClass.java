/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 课号：14-03
 * 课名：包装类
 * ------------------------------------------------------------
 * 知识点：
 *   - 8 种基本类型对应 8 种包装类：byte→Byte、short→Short、int→Integer、long→Long、float→Float、double→Double、char→Character、boolean→Boolean（int 和 char 需要特殊记忆）。
 *   - 包装类让基本类型拥有对象的能力：泛型集合、工具方法等都要求包装类。
 *   - 自动装箱：int → Integer，底层调用 `Integer.valueOf`；自动拆箱：Integer → int，底层调用 `intValue`，都由编译器自动完成。
 *   - `Integer.parseInt("123")` 把字符串转成 int，是最常用的转换方法；格式非法会抛 NumberFormatException。
 *   - 缓存池陷阱：IntegerCache 事先缓存了 -128 ~ 127 的 Integer 对象，该范围内 `==` 比较为 true，超出范围则为 false（新对象）。
 *   - 结论：包装类比较内容一律用 equals；Integer 变量为 null 时自动拆箱会抛 NullPointerException。
 * ============================================================
 */

package com.heima.ch14;

// ==================== 示例代码 ====================

public class WrapperClass {
    public static void main(String[] args) {
        // 自动装箱：基本类型 -> 包装类（底层是 Integer.valueOf）
        Integer a = 100;
        // 自动拆箱：包装类 -> 基本类型（底层是 intValue）
        int b = a + 1;
        System.out.println("拆箱运算结果：" + b);

        // 字符串转整数：最常用的方式
        int num = Integer.parseInt("12345");
        System.out.println("转换后加 1：" + (num + 1));
        // Integer.parseInt("12a3"); // 运行时抛 NumberFormatException

        // 缓存池陷阱：-128 ~ 127 之间指向同一个对象
        Integer x = 127;
        Integer y = 127;
        System.out.println(x == y);      // true，命中缓存池
        Integer m = 128;
        Integer n = 128;
        System.out.println(m == n);      // false，超出范围是新对象
        System.out.println(m.equals(n)); // true，包装类比较内容必须用 equals

        // 易错：null 拆箱会抛空指针
        Integer empty = null;
        // int danger = empty; // NullPointerException
        System.out.println("安全写法：" + (empty == null ? 0 : empty));
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 定义字符串 `"666"`，把它转成 int 后乘以 2 并输出结果；再尝试用 `Integer.parseInt` 转换字符串 `"66a6"`，运行后阅读控制台异常信息，记录异常类型和出错原因。
 * 2. 编写方法 `parseIntSafely(String s)`：字符串合法时返回对应的整数，不合法时返回 -1 且程序不能崩溃；在 main 中分别用 `"1024"`、`"12x"`、空字符串三种输入测试。
 * 3. 先预测下面每个输出的值再运行验证：`Integer a = 100, b = 100, c = 200, d = 200;`，分别输出 `a == b`、`c == d`、`a.equals(b)`、`c.equals(d)` 的结果；若有预测错误的，把所有 `==` 比较改写成 equals 并解释为什么 `==` 不可靠。
 * ============================================================
 */
