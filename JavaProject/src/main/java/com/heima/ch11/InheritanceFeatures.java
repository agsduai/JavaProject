/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 课号：11-03
 * 课名：继承的特点
 * ------------------------------------------------------------
 * 知识点：
 *   - Java 只支持单继承：一个子类只能有一个直接父类，`class A extends B, C` 这种写法是非法的
 *   - Java 支持多层继承：A 继承 B，B 又继承 C，子类可以把“爷爷类”的公有成员也一并继承过来使用
 *   - 每种继承关系只能有一个直接父类；但一个父类可以同时被多个子类继承（如 Person 可以被 Student、Teacher 共同继承）
 *   - 不支持多继承的原因：如果两个父类中有同名方法，子类不知道该用哪一个，会产生二义性
 *   - 所有类的顶级父类是 Object：任何一个类不写 extends，默认直接继承 Object
 *   - 记忆：直接父类只能有一个，间接父类（各级祖先类）的公有成员照常继承
 * ============================================================
 */

package com.heima.ch11;

// ==================== 示例代码 ====================

public class InheritanceFeatures {
    public static void main(String[] args) {
        InheritanceFeaturesStudent s = new InheritanceFeaturesStudent();
        s.study();                        // 自己的方法
        s.eat();                          // 间接父类 InheritanceFeaturesAnimal 的方法（多层继承而来）
        s.sleep();                        // 直接父类 InheritanceFeaturesPerson 的方法
        System.out.println(s.toString()); // 顶级父类 Object 的方法
    }
}

// 间接父类（爷爷类）
class InheritanceFeaturesAnimal {
    public void eat() {
        System.out.println("吃东西");
    }
}

// 直接父类：InheritanceFeaturesPerson 继承 InheritanceFeaturesAnimal；同时可以被多个子类继承
class InheritanceFeaturesPerson extends InheritanceFeaturesAnimal {
    public void sleep() {
        System.out.println("睡觉");
    }
}

// 子类：只能写一个直接父类，不能写成 class InheritanceFeaturesStudent extends InheritanceFeaturesPerson, InheritanceFeaturesAnimal
class InheritanceFeaturesStudent extends InheritanceFeaturesPerson {
    public void study() {
        System.out.println("学习");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 编译测试：先尝试写出“一个类同时继承两个类”的代码，再用编译器验证，观察并记录编译器给出的错误提示。
 * 2. 设计三层继承结构：Grandpa（方法：讲故事 tellStory）→ Father（方法：工作 work）→ Son（方法：打游戏 playGame），在测试类中用 Son 对象依次调用三个方法，验证多层继承的效果。
 * 3. 设计“一父多子”结构：父类 Shape（方法：绘制 draw），三个子类 Circle、Rectangle、Triangle，验证同一个父类可以被多个子类同时继承。
 * ============================================================
 */
