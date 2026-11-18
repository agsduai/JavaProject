/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：19-01
 * 课名：反射入门与获取Class对象
 * ------------------------------------------------------------
 * 知识点：
 *   - 反射：在程序**运行期间**动态获取类的信息（成员变量、方法、构造器）并操作对象的机制。
 *   - 正射是"先有类再 new 对象"，反射是"先拿到 Class 对象，再反向创建对象、调用方法"。
 *   - 获取 Class 对象的三种方式：`类名.class`、`对象.getClass()`、`Class.forName("全限定类名")`。
 *   - `Class.forName` 会触发类加载和静态代码块执行，是框架最常用的方式（配置文件中写类名）。
 *   - 同一个类在 JVM 中只有一个 Class 对象，三种方式拿到的都是同一个。
 *   - 易错：`Class.forName` 的类名必须是**全限定名**（含包名），写错会抛 `ClassNotFoundException`。
 * ============================================================
 */

package com.heima.ch19;

// ==================== 示例代码 ====================

class ReflectBasicDemoPerson {
    static {
        System.out.println("ReflectBasicDemoPerson 类的静态代码块执行了");
    }

    private String name = "张三";

    public void sayHello() {
        System.out.println("你好，我是" + name);
    }
}

public class ReflectBasicDemo {
    public static void main(String[] args) throws Exception {
        // 方式一：类名.class
        Class<ReflectBasicDemoPerson> c1 = ReflectBasicDemoPerson.class;

        // 方式二：对象.getClass()
        ReflectBasicDemoPerson p = new ReflectBasicDemoPerson();
        Class<? extends ReflectBasicDemoPerson> c2 = p.getClass();

        // 方式三：Class.forName（传入全限定类名，会触发类加载）
        // 这里用 getName() 动态取全限定名，避免把包名和类名写死在字符串里
        Class<?> c3 = Class.forName(ReflectBasicDemoPerson.class.getName());

        System.out.println("三种方式是否同一个对象：" + (c1 == c2) + "，" + (c1 == c3));
        System.out.println("类名：" + c1.getName());
        System.out.println("简单类名：" + c1.getSimpleName());
        System.out.println("包名：" + c1.getPackageName());
        System.out.println("是否是接口：" + c1.isInterface());
        System.out.println("父类：" + c1.getSuperclass().getName());

        // 通过 Class 对象反向创建实例
        Object obj = c1.getDeclaredConstructor().newInstance();
        System.out.println("反射创建的对象：" + obj.getClass().getSimpleName());
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 用三种方式分别获取 `String` 类的 Class 对象，比较它们是否相等并打印类名、包名。
 * 2. 用 `Class.forName` 加载自己写的一个类，观察它的静态代码块是否执行，并解释原因。
 * 3. 编写方法 `boolean isSameClass(Object a, Object b)`，用反射判断两个对象的类型是否完全一致。
 * ============================================================
 */
