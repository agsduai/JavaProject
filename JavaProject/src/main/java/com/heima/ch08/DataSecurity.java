/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 课号：8-04
 * 课名：面向对象中数据安全的问题
 * ------------------------------------------------------------
 * 知识点：
 *   - 问题：属性不加限制时，外界可以随意赋值，甚至赋不合理的值（如 age = -100），程序不报错但数据不合理。
 *   - 原因：成员变量直接暴露给了外界，任何地方都能通过"对象名.属性"读取和修改。
 *   - 解决：用 private 关键字把成员变量私有化，让它只能在当前类内部被访问。
 *   - private 之后外界无法直接访问，需要在本类中提供 public 的 getter（取值）和 setter（赋值）方法作为公共入口。
 *   - 校验逻辑可以写在 setter 中，只有合法的数据才允许赋给成员变量，非法数据被拦截。
 *   - 封装：隐藏实现细节，仅对外提供公共访问方式；private 是封装最直接的体现，好处是提高安全性、复用性，降低使用成本。
 * ============================================================
 */

package com.heima.ch08;

// ==================== 示例代码 ====================

class DataSecuritySafeStudent {
    // 用 private 私有化，外界不能直接访问
    private String name;
    private int age;

    // setter：给外界提供设置 age 的入口，可以在里面做校验
    public void setAge(int a) {
        if (a >= 0 && a <= 120) {
            age = a;
        } else {
            System.out.println("年龄不合理，赋值失败");
        }
    }

    // getter：给外界提供读取 age 的入口
    public int getAge() {
        return age;
    }

    public void setName(String n) {
        name = n;
    }

    public String getName() {
        return name;
    }
}

public class DataSecurity {
    public static void main(String[] args) {
        DataSecuritySafeStudent stu = new DataSecuritySafeStudent();

        // stu.age = -100;  // private 之后这行报错，无法直接访问
        stu.setAge(-100);   // 走 setter，非法值被校验拦下
        stu.setName("小黑");

        System.out.println(stu.getName()); // 小黑
        System.out.println(stu.getAge());  // 0（非法值没有被赋进去）
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 定义一个 Person 类，private 修饰 name 和 age，通过 setter 把 age 设置为 200，运行观察结果，说明 private 加校验是如何阻止非法数据的。
 * 2. 思考并写下结论：如果一个类只提供 setter 不提供 getter，外界还能不能读取到属性值？这算不算另一种"更彻底"的封装方式？
 * 3. 把 8-02 的 Phone 类改造成 private + getter/setter 的版本，并保证 price 不能被设置为负数。
 * ============================================================
 */
