/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 课号：11-15
 * 课名：接口和接口中的成员？
 * ------------------------------------------------------------
 * 知识点：
 *   - 接口用 `interface` 定义：`public interface 接口名 {}`；实现用 `implements`：`public class 类名 implements 接口名 {}`
 *   - 接口是一种行为的抽象规范，代表“某种能力/规则”；类实现接口表示它具备这种能力
 *   - JDK7 前（含 JDK7）接口中只有两种成员：常量（默认 public static final，可省略不写）和抽象方法（默认 public abstract，可省略不写）
 *   - 接口不能创建对象（不能 new），接口也没有构造方法，必须通过实现类来使用
 *   - 实现类必须重写接口中的全部抽象方法；如果不想重写全部，实现类只能声明为抽象类
 *   - 一个类可以实现多个接口（用逗号隔开），也可以在继承一个父类的同时实现接口，写法顺序：extends 在前，implements 在后
 * ============================================================
 */

package com.heima.ch11;

// ==================== 示例代码 ====================

// 接口：定义“会游泳”这个规则
interface InterfaceMembersSwimmable {
    // 常量：public static final 可省略不写
    public static final int MAX_DEPTH = 100;

    // 抽象方法：public abstract 可省略不写
    public abstract void swim();
}

// 实现类：必须重写接口中的全部抽象方法
class InterfaceMembersFrog implements InterfaceMembersSwimmable {
    @Override
    public void swim() {
        System.out.println("青蛙在蛙泳");
    }
}

public class InterfaceMembers {
    public static void main(String[] args) {
        // InterfaceMembersSwimmable s = new InterfaceMembersSwimmable(); // 编译报错：接口不能创建对象

        InterfaceMembersSwimmable s = new InterfaceMembersFrog(); // 多态：接口引用指向实现类对象
        s.swim();

        // 接口中的常量：用 接口名.常量名 直接访问
        System.out.println("最大下潜深度：" + InterfaceMembersSwimmable.MAX_DEPTH);
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 定义接口 Speak（抽象方法 speak()），实现类 Chinese、English 各自重写打印不同语言的问候语，测试类中用接口引用分别指向两个实现类对象并调用。
 * 2. 定义接口 Fly（含常量 MAX_SPEED = 1000 和抽象方法 fly()），实现类 Plane 完成重写并打印常量值，验证接口常量的访问方式。
 * 3. 分别尝试：让实现类只重写接口中的部分抽象方法、直接 new 接口对象，记录两次编译报错的信息，并总结接口的两条硬性规则。
 * ============================================================
 */
