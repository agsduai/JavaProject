/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 课号：2-06
 * 课名：数据类型
 * ------------------------------------------------------------
 * 知识点：
 *   - Java 有 8 种基本数据类型：byte(1字节)、short(2)、int(4)、long(8)、float(4)、double(8)、char(2)、boolean(1)。
 *   - 整数字面量默认 int，小数字面量默认 double；给 long 赋大值要加 L，给 float 赋值要加 F。
 *   - byte 取值范围 -128~127，int 约 ±21 亿，long 约 ±922 亿亿。
 *   - 选择原则：默认用 int 和 double，明确需要小范围、大范围或节省内存时才用 byte/short/long/float。
 *   - String 是引用类型，不属于 8 种基本类型。
 * ============================================================
 */

package com.heima.ch02;

// ==================== 示例代码 ====================

public class DataType {
    public static void main(String[] args) {
        // 整数类型
        byte num1 = 10;            // 1字节：-128 ~ 127
        short num2 = 200;          // 2字节
        int num3 = 100000;         // 4字节：整数默认类型
        long num4 = 8000000000L;   // 8字节：超出int范围必须加L
        // 浮点类型
        float num5 = 3.14F;        // 4字节：必须加F
        double num6 = 3.1415926;   // 8字节：小数默认类型
        // 字符与布尔
        char c = 'A';              // 2字节：单个字符
        boolean flag = true;       // true或false
        System.out.println(num1);
        System.out.println(num2);
        System.out.println(num3);
        System.out.println(num4);
        System.out.println(num5);
        System.out.println(num6);
        System.out.println(c);
        System.out.println(flag);
        // int的取值范围
        System.out.println("int最大值：" + Integer.MAX_VALUE);
        System.out.println("int最小值：" + Integer.MIN_VALUE);
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 为以下信息选择合适的类型定义变量并输出：年龄、身高、性别（单个字符）、是否已婚、银行卡余额（万元级小数）。
 * 2. 定义一个 long 变量保存 11 位手机号 13812345678，再定义一个 byte 变量保存它的最大值 127，输出两者；若把手机号直接赋值给 int 变量会发生什么，把结论写在注释里。
 * 3. 挑战：分别用 float 和 double 存储 3.14159265358979 并输出，观察两次打印的差异，并在注释里写出原因。
 * ============================================================
 */
