/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 课号：11-09
 * 课名：子类到底能继承父类的哪些内容？
 * ------------------------------------------------------------
 * 知识点：
 *   - 构造方法：不能被继承。子类构造里只是默认调用了 `super()`，“调用”不等于“继承”
 *   - 私有成员：子类实际上会继承（拥有），但因为 private 的限制无法直接访问，即“有但拿不到”，只能通过父类提供的公共 getter/setter 等方法间接使用
 *   - 公有成员：完全继承，子类在任何地方都可以直接使用
 *   - protected 成员：归属父类，但专门给子类开“绿色通道”，子类可以直接访问
 *   - 记忆口诀：构造不继承；私有“有但拿不到”；公共和 protected 随意用
 *   - 验证手段：可以用 javap 等反编译工具查看子类字节码，确认哪些成员真的属于子类
 * ============================================================
 */

package com.heima.ch11;

// ==================== 示例代码 ====================

public class InheritedContent {
    public static void main(String[] args) {
        InheritedContentSon z = new InheritedContentSon();
        z.publicMethod(); // 公有方法：直接继承使用
        z.usePrivate();   // 通过父类提供的公有方法，间接使用父类私有成员

        // z.money = 100;      // 编译报错：私有成员“有但拿不到”
        // z.privateMethod();  // 编译报错：私有方法同理

        new InheritedContentSon(100); // 构造方法不能继承，只能由子类构造通过 super(...) 借用
    }
}

class InheritedContentFather {
    private int money = 100; // 私有成员变量

    public void publicMethod() { // 公有成员
        System.out.println("公有方法");
    }

    private void privateMethod() { // 私有成员
        System.out.println("私有方法");
    }

    // 父类主动提供公有方法，让外界间接使用私有成员
    public void usePrivate() {
        System.out.println("父类私有money的值：" + money);
        privateMethod(); // 父类自己内部可以访问自己的私有成员
    }

    public InheritedContentFather(int x) {
        System.out.println("父类有参构造：" + x);
    }
}

class InheritedContentSon extends InheritedContentFather {
    public InheritedContentSon() {
        super(1); // 父类没有空参构造，必须手动调用有参构造
    }

    public InheritedContentSon(int x) {
        super(x);
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 编写父类 Fu，含私有变量 secret 和公有方法 getSecret()；在子类 Zi 中分别尝试直接访问 secret 和调用 getSecret()，验证“私有成员有但拿不到，只能间接使用”。
 * 2. 编写父类 Car 只提供有参构造，子类 RacingCar 先不写任何构造并编译，观察报错信息；补上正确的子类构造后运行，用文字说明“构造方法不能继承”时子类该如何处理。
 * 3. 定义父类，其中 public、protected、private 三种修饰的变量各一个，在子类中逐一访问，把“能编译通过的”和“报错的”整理成一张表格。
 * ============================================================
 */
