/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 课号：11-01
 * 课名：什么是继承？
 * ------------------------------------------------------------
 * 知识点：
 *   - 继承是面向对象三大特征（封装、继承、多态）之一，用关键字 `extends` 实现，语法：`public class 子类 extends 父类 {}`
 *   - 父类（也叫基类、超类）存放多个子类共有的属性和方法；子类（也叫派生类）只写自己独有的内容
 *   - 核心好处：代码复用，把重复代码抽到父类，子类直接拿来用；同时让类与类之间产生关系，为后面学多态打基础
 *   - 子类可以像使用自己的成员一样使用父类中非私有的成员
 *   - Java 中一个类只能有一个直接父类（单继承）
 *   - 使用场景判断：当多个类出现大量相同的属性和方法时，就该把共同点抽取到一个父类中
 * ============================================================
 */

package com.heima.ch11;

// ==================== 示例代码 ====================

public class InheritanceIntro {
    public static void main(String[] args) {
        // 创建父类对象
        InheritanceIntroAnimal a = new InheritanceIntroAnimal();
        a.eat();

        // 创建子类对象：eat 是从父类继承来的，lookHome 是子类自己的
        InheritanceIntroDog d = new InheritanceIntroDog();
        d.eat();
        d.lookHome();
    }
}

// 父类：存放共有的属性和方法
class InheritanceIntroAnimal {
    public void eat() {
        System.out.println("动物都要吃东西");
    }
}

// 子类：通过 extends 继承父类，不用再重复写 eat 方法
class InheritanceIntroDog extends InheritanceIntroAnimal {
    public void lookHome() {
        System.out.println("狗在看家");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 定义父类 Person（属性：姓名、年龄；方法：吃饭 eat），让 Student 子类继承 Person，并新增学习方法 study，在测试类中创建 Student 对象并调用两个方法。
 * 2. 定义父类 Car（属性：品牌、颜色；方法：行驶 run），让 RacingCar 子类继承并新增漂移方法 drift，体会“子类不用再写父类已有的方法”。
 * 3. 思考题：如果 Dog 和 Bird 都需要“飞”这个方法，应该把它放到父类 Animal 里吗？用文字写出你的判断理由。
 * ============================================================
 */
