/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 课号：11-11
 * 课名：什么是多态？
 * ------------------------------------------------------------
 * 知识点：
 *   - 多态：同一种类型的对象，执行同一个行为时表现出不同的状态；代码层面就是“父类引用指向子类对象”
 *   - 语法格式：`父类类型 变量名 = new 子类类型();`，例如 `Person p = new Student();`
 *   - 多态的三个前提，缺一不可：① 有继承（或实现）关系 ② 有方法重写 ③ 父类引用指向子类对象
 *   - 多态的好处：提高程序的扩展性和灵活性，配合“父类类型做方法参数”可以实现统一入口、多种行为
 *   - 记忆口诀（针对成员方法）：编译看左边，运行看右边 —— 编译时检查父类中有没有这个方法，运行时执行子类重写后的逻辑
 *   - 多态是继承、方法重写结合在一起的自然结果，是面向对象三大特征中最灵活的一个
 * ============================================================
 */

package com.heima.ch11;

// ==================== 示例代码 ====================

public class PolymorphismIntro {
    public static void main(String[] args) {
        // 多态：父类引用指向子类对象
        PolymorphismIntroPerson p = new PolymorphismIntroStudent();
        p.sayHello(); // 编译看左边（PolymorphismIntroPerson 有 sayHello），运行看右边（执行 PolymorphismIntroStudent 重写的版本）

        p = new PolymorphismIntroTeacher();
        p.sayHello(); // 同一个变量指向不同对象，行为不同 —— 这就是多态
    }
}

class PolymorphismIntroPerson {
    public void sayHello() {
        System.out.println("你好，我是人");
    }
}

class PolymorphismIntroStudent extends PolymorphismIntroPerson {
    @Override
    public void sayHello() {
        System.out.println("你好，我是学生，正在上课");
    }
}

class PolymorphismIntroTeacher extends PolymorphismIntroPerson {
    @Override
    public void sayHello() {
        System.out.println("你好，我是老师，正在讲课");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 编写 Animal 及其子类 Cat、Dog，用 Animal 类型的变量分别指向两种子类对象并调用同名方法，观察输出的不同行为。
 * 2. 编写方法 `public static void show(Animal a)`，在调用处分别传入 Cat 对象和 Dog 对象，体会“统一入口、多种行为”的扩展性。
 * 3. 思考题：把 Student 的 sayHello 重写删掉后再执行 `Person p = new Student(); p.sayHello();`，输出会是什么？写出你的推断和依据。
 * ============================================================
 */
