/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 课号：11-04
 * 课名：继承中成员变量的特点
 * ------------------------------------------------------------
 * 知识点：
 *   - 变量的访问遵循就近原则：先找局部范围（方法内部），再找本类成员位置，找不到才逐级向上去父类找
 *   - 子类成员变量与父类成员变量同名时，子类对象默认访问到的是子类的那个变量
 *   - 想访问父类的同名变量，用 `super.变量名`；想明确访问本类成员变量，用 `this.变量名`
 *   - `this` 代表本类对象的引用；`super` 代表父类的存储空间（对象中父类那一部分）
 *   - 局部变量、子类成员变量、父类成员变量三者同名时：直接写变量名取局部，`this.` 取本类，`super.` 取父类
 *   - 易错点：就近原则只发生在“找不到才向上找”，一旦本类有同名变量，就不会再往父类找
 * ============================================================
 */

package com.heima.ch11;

// ==================== 示例代码 ====================

public class InheritedVariable {
    public static void main(String[] args) {
        InheritedVariableSon z = new InheritedVariableSon();
        z.show();
    }
}

class InheritedVariableFather {
    String name = "父类的name";
}

class InheritedVariableSon extends InheritedVariableFather {
    String name = "子类的name";

    public void show() {
        String name = "局部变量name";
        System.out.println(name);        // 就近原则：先取局部变量
        System.out.println(this.name);   // this.：取子类成员变量
        System.out.println(super.name);  // super.：取父类成员变量
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 编写父类 Fu（成员变量 `int num = 10`）、子类 Zi（成员变量 `int num = 20`），在 Zi 的方法中分别打印 num、this.num、super.num，先写出预期输出再运行验证。
 * 2. 在第 1 题基础上，给 Zi 的方法再增加一个局部变量 `int num = 30`，重新分析三个输出的值，并用文字说明就近原则的查找顺序。
 * 3. 定义父类 Animal 有成员变量 `int legs = 4`，子类 Dog 不定义 legs，直接在子类方法中打印 legs，分析这个值是从哪里来的。
 * ============================================================
 */
