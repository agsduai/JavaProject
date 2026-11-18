/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 课号：11-17
 * 课名：接口在JDK8和9的新特性
 * ------------------------------------------------------------
 * 知识点：
 *   - JDK8 之前接口只能是“常量 + 抽象方法”；从 JDK8 开始，接口中还可以定义默认方法和静态方法
 *   - 默认方法：`public default 返回值类型 方法名() {}`，实现类可以选择重写，也可以不重写直接继承使用
 *   - 默认方法的意义：给接口“打补丁”，向已经写好的接口新增方法时，不强制所有实现类都去重写
 *   - 静态方法：`public static 返回值类型 方法名() {}`，只能用 `接口名.方法名()` 调用，不能用实现类对象调用
 *   - JDK9 开始接口可以有私有方法：普通私有方法 `private xxx()` 给默认方法复用代码；静态私有方法 `private static xxx()` 给静态方法复用代码
 *   - 记忆总结：默认方法实现类可重写可不重写；静态方法只能接口名调用；私有方法是接口内部“自己人”用的，外界访问不到
 * ============================================================
 */

package com.heima.ch11;

// ==================== 示例代码 ====================

// 演示 JDK8 / JDK9 接口新特性
interface InterfaceNewFeaturesGreeting {
    // 抽象方法：实现类必须重写
    void abstractMethod();

    // JDK8：默认方法，实现类可重写也可不重写
    public default void defaultMethod() {
        System.out.println("接口的默认方法");
        show(); // JDK9 私有方法：给默认方法复用
    }

    // JDK8：静态方法，只能通过 接口名.方法名 调用
    public static void staticMethod() {
        System.out.println("接口的静态方法");
        staticShow(); // JDK9 静态私有方法：给静态方法复用
    }

    // JDK9：普通私有方法，供默认方法复用重复代码
    private void show() {
        System.out.println("接口的私有方法");
    }

    // JDK9：静态私有方法，供静态方法复用重复代码
    private static void staticShow() {
        System.out.println("接口的静态私有方法");
    }
}

class InterfaceNewFeaturesGreetingImpl implements InterfaceNewFeaturesGreeting {
    @Override
    public void abstractMethod() {
        System.out.println("实现类重写抽象方法");
    }

    // 默认方法可以不重写直接用；需要改造时也可以重写，写法如下：
    // @Override
    // public void defaultMethod() {
    //     System.out.println("实现类重写后的默认方法");
    // }
}

public class InterfaceNewFeatures {
    public static void main(String[] args) {
        InterfaceNewFeaturesGreeting i = new InterfaceNewFeaturesGreetingImpl();
        i.abstractMethod(); // 抽象方法：执行实现类重写的版本
        i.defaultMethod();  // 默认方法：实现类没重写，直接使用接口里的默认实现

        // 静态方法只能用接口名调用；写成 i.staticMethod() 会编译报错
        InterfaceNewFeaturesGreeting.staticMethod();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 定义接口 Play，含抽象方法 play() 和默认方法 rest()（打印“休息中”），实现类 Football 只重写 play()，测试类验证 rest() 不重写也能直接调用。
 * 2. 在第 1 题的接口中增加静态方法 info() 打印“这是运动接口”，分别用接口名和实现类对象两种方式调用，记录哪种方式报错并说明原因。
 * 3. 在接口中分别增加一个普通私有方法和一个静态私有方法，让默认方法和静态方法各复用一次，并总结两种私有方法分别服务于谁。
 * ============================================================
 */
