/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 课号：11-14
 * 课名：抽象类和抽象方法
 * ------------------------------------------------------------
 * 知识点：
 *   - 抽象方法：用 `abstract` 修饰、没有方法体、直接以分号结束的方法，格式：`public abstract void eat();`
 *   - 抽象类：含有抽象方法的类必须用 `abstract` 修饰，格式：`public abstract class 类名 {}`
 *   - 抽象类不能创建对象（不能 new），它存在的意义就是被继承，由子类创建对象
 *   - 子类继承抽象类后必须重写父类的全部抽象方法；如果不重写全部，子类自己也只能声明为抽象类
 *   - 抽象类中可以有构造方法（供子类 `super()` 调用完成父类属性初始化）、成员变量和普通方法
 *   - 意义：抽象类是“规则制定者”——强制要求子类必须具备哪些行为，同时把共性代码抽取出来复用
 * ============================================================
 */

package com.heima.ch11;

// ==================== 示例代码 ====================

public class AbstractClass {
    public static void main(String[] args) {
        // AbstractClassAnimal a = new AbstractClassAnimal(); // 编译报错：抽象类不能创建对象

        AbstractClassAnimal a = new AbstractClassCat("汤姆"); // 多态：父类引用指向子类对象
        a.eat();   // 运行看右边，执行子类重写的方法
        a.sleep(); // 调用抽象类中的普通方法
    }
}

// 抽象类：含抽象方法的类必须用 abstract 修饰
abstract class AbstractClassAnimal {
    private String name;

    // 抽象类可以有构造方法，供子类 super() 调用
    public AbstractClassAnimal() {
    }

    public AbstractClassAnimal(String name) {
        this.name = name;
    }

    public String getName() { return name; }

    // 抽象方法：没有方法体，只定规则，强制子类重写
    public abstract void eat();

    // 抽象类中也可以写普通方法，给子类直接复用
    public void sleep() {
        System.out.println(getName() + "在睡觉");
    }
}

// 子类：必须重写父类的全部抽象方法
class AbstractClassCat extends AbstractClassAnimal {
    public AbstractClassCat() {
    }

    public AbstractClassCat(String name) {
        super(name);
    }

    @Override
    public void eat() {
        System.out.println(getName() + "吃鱼");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 编写抽象类 Shape（抽象方法 getArea()），子类 Circle、Rectangle 分别重写并计算面积，测试类中用多态创建对象并打印面积（圆周率取 3.14）。
 * 2. 分别尝试：给抽象类创建对象、给抽象方法写上方法体、让子类不重写全部抽象方法，逐一记录编译器的报错提示。
 * 3. 设计抽象类 Employee（属性 name、抽象方法 work()），子类 Manager 与 Cook 各自实现 work()；再在抽象类中编写普通方法 show() 打印姓名，验证“抽象类 = 抽象规则 + 共性代码”。
 * ============================================================
 */
