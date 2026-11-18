/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 课号：5-02
 * 课名：三道方法练习
 * ------------------------------------------------------------
 * 知识点：
 *   - 练习一（判断素数）：定义方法 `isPrime(int num)`，num 是素数返回 true，否则返回 false。思路：小于 2 直接返回 false；从 2 开始试除，存在约数就不是素数（试到平方根即可提前结束）。
 *   - 练习二（求较大值）：定义方法 `getMax(int a, int b)`，返回两个整数中较大的一个。思路：用 if 判断，或用三元运算符 `a >= b ? a : b`。
 *   - 练习三（数组求和）：定义方法 `getSum(int[] arr)`，返回数组所有元素的总和。思路：遍历累加，循环终点用 `arr.length`。
 *   - 共同方法：先按"要不要返回值、要不要形参"确定方法签名，再填方法体，最后在 main 中调用验证。
 * ============================================================
 */

package com.heima.ch05;

// ==================== 示例代码 ====================

public class MethodPractice {
    public static void main(String[] args) {
        System.out.println("17 是否为素数：" + isPrime(17)); // true
        System.out.println("较大值：" + getMax(10, 23));     // 23
        int[] arr = {1, 2, 3, 4, 5};
        System.out.println("数组总和：" + getSum(arr));      // 15
    }

    // 练习一：判断 num 是否为素数
    public static boolean isPrime(int num) {
        if (num < 2) {
            return false;
        }
        for (int i = 2; i * i <= num; i++) {
            if (num % i == 0) {
                return false; // 找到约数就不是素数
            }
        }
        return true;
    }

    // 练习二：返回两个整数中的较大值
    public static int getMax(int a, int b) {
        return a >= b ? a : b;
    }

    // 练习三：返回整数数组的总和
    public static int getSum(int[] arr) {
        int sum = 0;
        for (int i = 0; i < arr.length; i++) {
            sum += arr[i];
        }
        return sum;
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 定义方法 `countEven(int[] arr)`，统计并返回数组中偶数的个数。
 * 2. 定义方法 `getMaxOfThree(int a, int b, int c)`，返回三个整数中的最大值。
 * 3. 定义方法 `contains(int[] arr, int data)`，判断数组中是否包含 data，包含返回 true，否则返回 false。
 * ============================================================
 */
