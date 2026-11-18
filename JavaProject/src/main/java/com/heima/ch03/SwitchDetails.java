/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 课号：3-07
 * 课名：switch的其他知识点
 * ------------------------------------------------------------
 * 知识点：
 *   - default 的位置灵活，可以写在最前或中间；省略 break 时它会继续往下执行，一般建议放最后并写上 break
 *   - case 穿透：case 中不写 break 时，执行完本 case 会继续执行下一个 case 的语句体，直到遇到 break 或 switch 结束
 *   - 穿透的妙用：把结果相同的多个 case 连续书写并共用一个语句体，可以简化代码
 *   - JDK12 新特性：箭头表达式 `case 值 -> 语句体`，不用写 break、不会穿透，还支持多值匹配 `case 1, 2, 3 ->`
 *   - switch 不支持 long、float、double、boolean 类型
 * ============================================================
 */

package com.heima.ch03;

import java.util.Scanner;

// ==================== 示例代码 ====================

public class SwitchDetails {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        System.out.println("请输入星期几（1~7）：");
        int week = sc.nextInt();
        // 利用 case 穿透把工作日的多个值合并到同一个出口
        switch (week) {
            case 1:
            case 2:
            case 3:
            case 4:
            case 5:
                System.out.println("工作日");
                break;
            case 6:
            case 7:
                System.out.println("休息日");
                break;
            default:
                System.out.println("输入有误");
                break;
        }
        // JDK12+ 箭头表达式：不用写 break，也不会穿透
        switch (week) {
            case 1, 2, 3, 4, 5 -> System.out.println("上班搬砖");
            case 6, 7 -> System.out.println("快乐周末");
            default -> System.out.println("输入有误");
        }
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 键盘录入 1~7，用 case 穿透判断"工作日 / 休息日"，要求把结果相同的 case 合并书写。
 * 2. 键盘录入月份 1~12，用 JDK12 箭头表达式输出对应季节（3~5 春季、6~8 夏季、9~11 秋季、12/1/2 冬季），非法月份输出"输入有误"。
 * 3. 分析题：switch 中 default 写在最前面且所有 case 都不写 break 时，输入一个与任何 case 都不匹配的值，程序会执行哪些语句？输出什么？
 * 
 * ## 循环（3-08 ~ 3-20）
 * ============================================================
 */
