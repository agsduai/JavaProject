/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 课号：10-05
 * 课名：final关键字
 * ------------------------------------------------------------
 * 知识点：
 *   - final 表示"最终的、不可改变的"，可以修饰类、方法、变量三种东西。
 *   - final 修饰类：该类不能被继承，JDK 中的 String、Integer 等核心类都是 final 类。
 *   - final 修饰方法：该方法不能被重写（重写的细节在继承章节学习）。
 *   - final 修饰基本类型变量：变量的值不能再被修改；修饰引用类型变量：地址值不能再改，但对象内部的属性可以改。
 *   - final 修饰的变量就是常量，命名规范：全部大写，多个单词之间用下划线分隔，如 MAX_SCORE、DEFAULT_NAME。
 *   - 常量通常配合 static 一起使用（static final），成为类常量，可以直接用类名调用。
 * ============================================================
 */

package com.heima.ch10;

// ==================== 示例代码 ====================

public class FinalKeyword {
    // final + static 组合：类常量，命名全大写、下划线分隔
    public static final double PI = 3.1415926535;
    public static final int MAX_SCORE = 100;
    public static final String DEFAULT_NAME = "阿玮";

    public static void main(String[] args) {
        // final 修饰基本类型变量：值不能再改
        final int a = 10;
        // a = 20; // 编译报错：final 变量不能被重新赋值
        System.out.println(a);

        // final 修饰引用类型变量：地址值不能改，但对象内部属性可以改
        final FinalKeywordFinalPhone p = new FinalKeywordFinalPhone();
        p.brand = "华为";      // 正确：改的是堆中对象的内容
        System.out.println(p.brand);
        // p = new FinalKeywordFinalPhone(); // 编译报错：final 引用不能再指向别的对象

        // 常量直接用类名调用
        System.out.println(PI);
        System.out.println(MAX_SCORE);
        System.out.println(DEFAULT_NAME);
    }
}

class FinalKeywordFinalPhone {
    String brand;
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 按命名规范定义两个常量 WEEK_DAYS = 7 和 MAX_SPEED = 120，在 main 中打印它们，并尝试给常量重新赋值观察报错信息。
 * 2. 预测输出题：一个 final 引用指向 Person 对象，先执行 p.age = 20 能否成功？再执行 p = new Person() 能否成功？写出预测结论并用代码验证。
 * 3. 尝试写一个类去继承 String，把编译器报错信息记录下来，加深"final 修饰的类不能被继承"的印象。
 * ============================================================
 */
