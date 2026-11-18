/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 课号：5-05
 * 课名：方法的作业
 * ------------------------------------------------------------
 * 知识点：
 *   - 作业一（判断闰年）：方法签名 `public static boolean isLeapYear(int year)`，是闰年返回 true。闰年条件：能被 4 整除且不能被 100 整除，或能被 400 整除。
 *   - 作业二（复制数组）：方法签名 `public static int[] copyArray(int[] arr)`，返回一个内容与 arr 完全相同的新数组，要求修改新数组不影响原数组。
 *   - 作业三（拼接字符串）：方法签名 `public static String arrayToString(int[] arr)`，把数组拼成 `[1, 2, 3]` 这种格式的字符串并返回。
 *   - 作业四（平均分）：方法签名 `public static double getAverage(int[] arr)`，返回数组所有元素的平均分，结果保留两位小数。
 *   - 统一要求：每个方法补上 `/** * /` 文档注释（说明功能、@param、@return）；在 main 中调用并验证；每个作业独立成一个类完成。
 * ============================================================
 */

package com.heima.ch05;

// ==================== 示例代码 ====================

public class MethodHomework {
    public static void main(String[] args) {
        // 作业规范：每个方法都要有文档注释，示例写法如下
        System.out.println(getGreeting("同学"));
        System.out.println("8 是偶数：" + isEven(8));
    }

    /**
     * 获取一句问候语（演示方法文档注释的规范写法）
     * @param name 要问候的人名
     * @return 拼接好的问候语
     */
    public static String getGreeting(String name) {
        return "你好，" + name + "，欢迎学习 Java！";
    }

    /**
     * 判断一个整数是否为偶数
     * @param num 被判断的整数
     * @return 偶数返回 true，奇数返回 false
     */
    public static boolean isEven(int num) {
        return num % 2 == 0;
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 作业一：定义方法 `public static boolean isLeapYear(int year)` 判断闰年，用 2024、1900、2000 三组数据验证。
 * 2. 作业二：定义方法 `public static int[] copyArray(int[] arr)` 复制数组，并验证修改新数组不影响原数组。
 * 3. 作业三：定义方法 `public static String arrayToString(int[] arr)`，把数组拼成 `[1, 2, 3]` 格式的字符串返回。
 * 4. 作业四：定义方法 `public static double getAverage(int[] arr)`，返回数组所有元素的平均分并保留两位小数。
 * ============================================================
 */
