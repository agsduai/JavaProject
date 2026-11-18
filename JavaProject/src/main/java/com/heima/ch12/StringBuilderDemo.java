/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 课号：12-05
 * 课名：StringBuilder
 * ------------------------------------------------------------
 * 知识点：
 *   - StringBuilder 是可变的字符串容器，所有拼接、修改都在同一个对象上进行，比 String 用 + 拼接效率高很多。
 *   - 常用方法：append 追加内容（返回自身，支持链式编程）、reverse 反转、length 获取长度、toString 转回 String。
 *   - 构造方法：`new StringBuilder()` 创建空容器，`new StringBuilder("内容")` 创建带初始内容的容器。
 *   - String 与 StringBuilder 互相转换：String 用构造方法转进去，StringBuilder 用 toString() 转回来。
 *   - 使用场景：循环拼接、频繁修改内容用 StringBuilder；内容固定不变的字符串用 String。
 *   - 底层维护一个数组，容量不够时自动扩容，使用时"只管往里装"。
 * ============================================================
 */

package com.heima.ch12;

// ==================== 示例代码 ====================

public class StringBuilderDemo {
    public static void main(String[] args) {
        // 创建一个空的 StringBuilder 容器
        StringBuilder sb = new StringBuilder();

        // append 追加内容，返回自身，可以链式调用
        sb.append("黑马").append("程序员").append(2026).append(true);
        System.out.println(sb); // 黑马程序员2026true

        // reverse 反转容器中的内容
        sb.reverse();
        System.out.println(sb); // eurt6202员序程马黑

        // length 获取字符个数
        System.out.println(sb.length()); // 13

        // toString 转回 String 类型
        String s = sb.toString();
        System.out.println(s.toUpperCase()); // EURT6202员序程马黑

        // 验证效率：循环拼接 10 万次，StringBuilder 非常快
        long start = System.currentTimeMillis();
        StringBuilder sb2 = new StringBuilder();
        for (int i = 0; i < 100000; i++) {
            sb2.append(i);
        }
        long end = System.currentTimeMillis();
        System.out.println("StringBuilder 拼接耗时：" + (end - start) + " 毫秒");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 键盘录入一个字符串，用 StringBuilder 反转后输出。
 * 2. 把 int 数组 {1, 2, 3} 拼接成 "[1, 2, 3]" 格式的字符串输出，要求用 StringBuilder 完成。
 * 3. 分别用 String 的 + 和 StringBuilder 循环拼接 1~100000 的数字，用 System.currentTimeMillis() 统计两种方式的耗时并对比。
 * ============================================================
 */
