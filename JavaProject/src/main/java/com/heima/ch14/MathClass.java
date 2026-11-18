/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 课号：14-01
 * 课名：Math类和两道算法题
 * ------------------------------------------------------------
 * 知识点：
 *   - Math 位于 java.lang 包，使用时不需要 import；构造方法私有、无法创建对象，所有方法都是静态的，直接 `Math.xxx()` 调用。
 *   - `abs` 求绝对值；`ceil` 向上取整、`floor` 向下取整（参数和返回值都是 double）；`round` 四舍五入，返回 long。
 *   - `max`/`min` 取两个数的最大、最小值；`pow(a, b)` 求 a 的 b 次幂；`sqrt` 开平方根，返回 double。
 *   - `random()` 生成 `[0.0, 1.0)` 之间的随机小数，乘上范围长度再平移起点，即可得到任意区间的随机数。
 *   - 统计一个整数的位数有两种思路：循环除以 10 并计数；或借助 `Math.log10` 取对数后再取整。
 *   - 易错：`Math.abs(Integer.MIN_VALUE)` 的结果仍是负数（int 溢出），需要先转成 long 再取绝对值。
 * ============================================================
 */

package com.heima.ch14;

// ==================== 示例代码 ====================

public class MathClass {
    public static void main(String[] args) {
        System.out.println(Math.abs(-8));      // 8 绝对值
        System.out.println(Math.ceil(3.1));    // 4.0 向上取整
        System.out.println(Math.floor(3.9));   // 3.0 向下取整
        System.out.println(Math.round(3.5));   // 4 四舍五入
        System.out.println(Math.max(10, 20));  // 20 最大值
        System.out.println(Math.pow(2, 10));   // 1024.0 幂运算
        System.out.println(Math.sqrt(16));     // 4.0 开平方
        System.out.println(Math.random());     // [0.0, 1.0) 的随机小数

        // 算法题：统计一个整数的位数
        System.out.println(countDigits(0));     // 1
        System.out.println(countDigits(4567));  // 4
        System.out.println(countDigits(-908));  // 3
    }

    // 循环除以 10 并计数，直到变成 0
    public static int countDigits(int num) {
        if (num == 0) {
            return 1; // 0 只有一位，特殊处理
        }
        long n = Math.abs((long) num); // 先转 long 再取绝对值，防止溢出
        int count = 0;
        while (n > 0) {
            n = n / 10;
            count++;
        }
        return count;
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 判断一个非负整数是否是完全平方数：编写方法接收一个 int 参数，若存在整数 k 使得 k*k 恰好等于该数，输出"是完全平方数"，否则输出"不是完全平方数"。示例：16 → 是；17 → 不是；0 → 是。
 * 2. 编写方法 `randomInRange(int min, int max)`，利用 Math.random 返回 [min, max] 范围内的随机整数，并循环生成 1000 个结果验证它们全部落在范围内。
 * 3. 不使用循环，改用 `Math.log10` 实现统计整数位数的方法，并测试 0、负数、Integer.MAX_VALUE 三种特殊情况，观察结果是否正确。
 * ============================================================
 */
