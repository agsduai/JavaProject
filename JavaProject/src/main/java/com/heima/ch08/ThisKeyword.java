/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 课号：8-06
 * 课名：就近原则和this关键字
 * ------------------------------------------------------------
 * 知识点：
 *   - 就近原则：方法中使用某个变量名时，先在方法内部（局部变量）找，找到了就用局部的；找不到才去成员位置找。
 *   - 问题：当成员变量与局部变量（比如 setter 的形参）重名时，就近原则会让方法内的名字代表局部变量，出现"name = name 自己赋给自己"的情况，成员变量赋值失败。
 *   - this 的作用：代表当前对象（谁调用这个方法，this 就是谁），用 `this.成员变量` 明确访问成员位置的同名变量。
 *   - 标准写法：`this.成员变量 = 形参;`，这是 setter 方法中的固定套路。
 *   - 没有重名问题时 this 可以省略，省略后仍然遵循"就近原则"找到成员变量。
 *   - 开发中即使不重名也建议养成习惯：涉及成员变量的地方尽量写清楚，避免歧义。
 * ============================================================
 */

package com.heima.ch08;

// ==================== 示例代码 ====================

class ThisKeywordThisStudent {
    private String name;
    private int age;

    // this.name 是成员变量，等号右边的 name 是形参（局部变量）
    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name; // 没有重名，this 可以省略
    }

    public void setAge(int age) {
        this.age = age;
    }

    public int getAge() {
        return age;
    }

    public void show() {
        // 局部变量与成员变量重名时的对比
        int age = 99; // 局部变量
        System.out.println("局部变量age：" + age);      // 99（就近原则）
        System.out.println("成员变量age：" + this.age); // this 明确指向成员变量
    }
}

public class ThisKeyword {
    public static void main(String[] args) {
        ThisKeywordThisStudent stu = new ThisKeywordThisStudent();
        stu.setName("阿玮");
        stu.setAge(18);
        stu.show();
        System.out.println(stu.getName() + " " + stu.getAge());
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 写一个 Teacher 类，属性 name、salary 用 private 修饰，setter 的形参名与成员变量同名，用 this 完成赋值，并写测试类验证。
 * 2. 故意写错版本：把 setter 中的 this.name = name 改成 name = name，运行后打印对象的 name 属性，观察并解释你看到的现象。
 * 3. 在一个 show() 方法中定义与成员变量同名的局部变量，同时打印局部变量和 this.成员变量，用输出结果说明"就近原则"和 this 的区别。
 * ============================================================
 */
