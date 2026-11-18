/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 课号：实操 9-01
 * 课名：对象内存与 this 分析实验
 * ------------------------------------------------------------
 * 知识点：
 *   - 【题目要求】编写 `Counter` 类（属性 `count`，方法 `increment()` 中使用 `this.count++`）。创建两个对象分别调用多次，输出各自 count；再编写 `Counter copyOf(Counter other)` 返回新对象，验证两个对象互不影响。最后写出内存分配说明：对象在堆、引用在栈、this 指向调用者。
 *   - 【考核点】对象内存分配、引用、this 的本质、对象独立性、方法返回对象。
 *   - 【易错点】`copyOf` 若直接返回 `other` 则是同一个对象，必须 `new`；this 不能在 static 方法中使用。
 *   - 【提示】可打印 `System.identityHashCode` 验证是否为同一对象。
 * ============================================================
 */

package com.heima.practice.advanced;

// ==================== 示例代码 ====================

// 技术示范（非本题答案）：this 解决就近原则
public class ObjectMemoryThisLab {
    private String name;

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public static void main(String[] args) {
        ObjectMemoryThisLab p = new ObjectMemoryThisLab();
        p.setName("小明");
        System.out.println(p.getName());
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 画出 `Person p = new Person()` 的内存分配图，标出栈中引用与堆中对象。
 * 2. 写一个 static 方法并尝试在其中使用 `this`，记录编译错误信息并解释原因。
 * 3. 两个引用指向同一对象时，通过任一引用修改属性，另一个引用看到的值如何变化？写代码实验验证并解释。
 * 
 * 
 * ## 第10章 面向对象进阶
 * ============================================================
 */
