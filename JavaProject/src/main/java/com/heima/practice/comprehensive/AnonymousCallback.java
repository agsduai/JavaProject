/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 课号：实操 11-03
 * 课名：匿名内部类实现事件回调
 * ------------------------------------------------------------
 * 知识点：
 *   - 【题目要求】定义接口 `ClickListener { void onClick(String msg); }`，编写 `Button` 类（`setOnClickListener(ClickListener l)`、`click()` 触发回调）。要求：① 用匿名内部类注册监听器；② 用 lambda 改写同一逻辑；③ 用成员内部类再实现一种监听。主方法分别演示三种写法。
 *   - 【考核点】成员内部类、匿名内部类、接口回调、lambda 与函数式接口的关系、外部类成员访问。
 *   - 【易错点】匿名内部类访问局部变量必须是 effectively final；lambda 只能用于函数式接口（仅一个抽象方法）。
 *   - 【提示】`Button.click()` 中判空后再调用 `listener.onClick("按钮被点击")`。
 * ============================================================
 */

package com.heima.practice.comprehensive;

// ==================== 示例代码 ====================

// 技术示范（非本题答案）：匿名内部类实现接口
public class AnonymousCallback {
    interface Greeting {
        void say(String name);
    }

    public static void main(String[] args) {
        Greeting g = new Greeting() {
            @Override
            public void say(String name) {
                System.out.println("你好，" + name);
            }
        };
        g.say("阿玮");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 给 `Button` 增加 `setOnLongClickListener`，用匿名内部类实现长按逻辑（长按 3 秒触发）。
 * 2. 用 lambda 改写全部监听器，比较两种写法的代码量与可读性。
 * 3. 设计 `Timer` 类，循环 3 次、每次 `Thread.sleep(1000)` 后触发回调，用匿名内部类注册并在回调中打印第几次触发。
 * 
 * 
 * ## 第12章 常见API
 * ============================================================
 */
