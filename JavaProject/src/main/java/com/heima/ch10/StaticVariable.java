/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 课号：10-01
 * 课名：static修饰成员变量
 * ------------------------------------------------------------
 * 知识点：
 *   - static 是静态修饰符，被它修饰的成员变量叫静态变量（也叫类变量），属于类而不属于某个对象。
 *   - 静态变量被该类的所有对象共享，全类只有一份；非静态成员变量则是每个对象各有一份。
 *   - 调用格式：推荐"类名.静态变量名"；用对象名调用语法上也允许，但不推荐（IDEA 会提示警告）。
 *   - 应用场景：当某个数据需要被所有对象共享时使用，例如统计一共创建了多少个对象、在线人数等。
 *   - 静态变量随类加载而存在，优先于对象出现：类加载时它已经有了，此时可能还没有任何对象。
 *   - 记忆口诀：静态的属于类，非静态的属于对象。
 * ============================================================
 */

package com.heima.ch10;

// ==================== 示例代码 ====================

class StaticVariableStaticStudent {
    private String name;
    // 静态变量：属于类，被所有对象共享一份，随类加载而存在
    public static int count = 0;

    public StaticVariableStaticStudent(String name) {
        this.name = name;
        count++; // 每创建一个对象，共享的 count 就加 1
    }

    public void show() {
        System.out.println("姓名：" + name + "，已创建人数：" + count);
    }
}

public class StaticVariable {
    public static void main(String[] args) {
        StaticVariableStaticStudent s1 = new StaticVariableStaticStudent("阿玮");
        StaticVariableStaticStudent s2 = new StaticVariableStaticStudent("小黑");
        StaticVariableStaticStudent s3 = new StaticVariableStaticStudent("小明");

        s1.show(); // 姓名：阿玮，已创建人数：3
        s3.show(); // 姓名：小明，已创建人数：3（共享同一份 count）

        // 静态变量推荐用"类名.静态变量"调用
        System.out.println("共创建：" + StaticVariableStaticStudent.count);

        // 对象名调用语法允许但不推荐，IDEA 会给出警告
        System.out.println("s1看到的count：" + s1.count);
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 定义 Player 类，静态变量 onlineCount 表示在线人数，每创建一个对象加 1，写测试类创建 3 个对象后用类名打印 onlineCount。
 * 2. 分别用 s1.count 和 s2.count 打印静态变量的值，观察两个"不同对象"看到的结果是否一样，并解释为什么。
 * 3. 思考题：如果需求改为"每个学生有自己的学号（1、2、3……依次递增）"，学号应该用静态变量还是非静态变量？写出你的方案和理由，并用代码验证。
 * ============================================================
 */
