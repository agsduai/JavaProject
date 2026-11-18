/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 课号：11-06
 * 课名：继承中构造方法的特点
 * ------------------------------------------------------------
 * 知识点：
 *   - 构造方法不能被继承，但子类构造方法中默认会先执行父类的构造方法
 *   - 子类构造方法的第一行默认隐藏了一句 `super()`：先调用父类的空参构造，再执行子类构造自己的内容
 *   - 原因：父类的属性要先初始化完成，子类才能安全地使用
 *   - 如果父类没有空参构造（例如只写了有参构造），子类就必须在构造第一行手动用 `super(实参)` 调用父类的有参构造
 *   - `super(...)` 和 `this(...)` 都必须写在构造方法的第一行，所以二者不能同时出现
 *   - 分析技巧：看到“new 子类对象”，就先沿着 super 链条找到最顶层的父类构造开始执行，再一层层回到子类
 * ============================================================
 */

package com.heima.ch11;

// ==================== 示例代码 ====================

public class InheritedConstructor {
    public static void main(String[] args) {
        // 创建子类对象：先执行父类构造，再执行子类构造
        InheritedConstructorStudent s1 = new InheritedConstructorStudent();
        System.out.println("----------");
        // 子类有参构造通过 super(实参) 把父类属性交给父类构造赋值
        InheritedConstructorStudent s2 = new InheritedConstructorStudent("阿玮", 18);
        System.out.println(s2.getName() + "..." + s2.getAge());
    }
}

// 父类：手动写了有参构造，默认的空参构造就不存在了
class InheritedConstructorPerson {
    private String name;
    private int age;

    public InheritedConstructorPerson(String name, int age) {
        this.name = name;
        this.age = age;
        System.out.println("父类的有参构造");
    }

    public String getName() { return name; }
    public int getAge() { return age; }
}

// 子类
class InheritedConstructorStudent extends InheritedConstructorPerson {
    public InheritedConstructorStudent() {
        // 隐藏的 super() 会报错，因为父类没有空参构造
        // 所以必须手动调用父类的有参构造
        super("小黑", 20);
        System.out.println("子类的空参构造");
    }

    public InheritedConstructorStudent(String name, int age) {
        super(name, age); // 必须写在第一行，借用父类构造给父类属性赋值
        System.out.println("子类的有参构造");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 编写父类 Fu（空参构造打印“父类构造”）、子类 Zi（空参构造打印“子类构造”），创建 Zi 对象，先写出预期输出再运行，分析执行顺序。
 * 2. 父类 Person 只写有参构造 Person(String name)，子类 Student 先“什么都不写”、再“只写空参构造不调用 super”，分别观察编译错误，最后补上正确的 super 调用使程序运行。
 * 3. 父类 Fu 与子类 Zi 都提供空参和有参构造并在其中打印标记语句，分别通过空参路径和有参路径创建 Zi 对象，分析两种路径下“父类构造与子类构造”的先后顺序，并画出执行流程图。
 * ============================================================
 */
