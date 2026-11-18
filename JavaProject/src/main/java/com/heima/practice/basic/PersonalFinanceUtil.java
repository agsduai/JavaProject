/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 课号：实操 5-01
 * 课名：个人财务工具类
 * ------------------------------------------------------------
 * 知识点：
 *   - 【题目要求】设计 `FinanceUtil` 工具类，提供：`double monthAverage(double[] incomes)`、`double max(double[] arr)`、`boolean isOverspend(double[] arr, double budget)`，以及重载的 `double total(double[] arr)` 和 `double total(double[] arr, int from, int to)`（求指定区间和，含首尾）。主方法用示例数据调用全部方法并输出结果。
 *   - 【考核点】方法定义与调用、形参与实参、返回值、方法重载、数组作参数、边界校验。
 *   - 【易错点】重载要求“同名不同参”，与返回值无关；区间求和要求 `from <= to` 且下标合法。
 *   - 【提示】工具类方法建议全部 `public static`，便于直接 `类名.方法名()` 调用。
 * ============================================================
 */

package com.heima.practice.basic;

// ==================== 示例代码 ====================

// 技术示范（非本题答案）：同名方法的重载
public class PersonalFinanceUtil {
    public static void main(String[] args) {
        double[] data = {100, 200, 300};
        System.out.println(sum(data));
        System.out.println(sum(data, 0, 2));
    }

    public static double sum(double[] arr) {
        double s = 0;
        for (double v : arr) {
            s += v;
        }
        return s;
    }

    public static double sum(double[] arr, int from, int to) {
        double s = 0;
        for (int i = from; i <= to; i++) {
            s += arr[i];
        }
        return s;
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 增加 `double median(double[] arr)` 求中位数（先排序，偶数个取中间两数平均）。
 * 2. 增加 `boolean isSorted(double[] arr)` 判断数组是否升序，并在主方法中测试。
 * 3. 增加 `void printReport(double[] arr)` 输出月报（总额、均值、最大、最小），金额保留两位小数。
 * ============================================================
 */
