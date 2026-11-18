/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：19-02
 * 课名：获取构造方法并创建对象
 * ------------------------------------------------------------
 * 知识点：
 *   - `getConstructors()` 获取所有 **public** 构造器；`getDeclaredConstructors()` 获取全部（含私有）。
 *   - `getConstructor(Class... params)` 按参数类型获取指定 public 构造器；`getDeclaredConstructor` 对应全部。
 *   - `newInstance(Object... args)` 用构造器创建对象，参数类型和个数必须与构造器匹配。
 *   - 私有构造器需要 `setAccessible(true)` 取消访问检查才能使用（暴力反射）。
 *   - 无参构造器可以直接用 `class对象.newInstance()`（已过时），推荐 `getDeclaredConstructor().newInstance()`。
 *   - 易错：用 `getConstructor` 找私有构造器会抛 `NoSuchMethodException`，要用 `getDeclaredConstructor`。
 * ============================================================
 */

package com.heima.ch19;

import java.lang.reflect.Constructor;

// ==================== 示例代码 ====================

class ReflectConstructorStudent {
    private String name;
    private int age;

    public ReflectConstructorStudent() {
        System.out.println("无参构造器执行");
    }

    public ReflectConstructorStudent(String name, int age) {
        this.name = name;
        this.age = age;
        System.out.println("有参构造器执行：" + name + ", " + age);
    }

    private ReflectConstructorStudent(String name) {
        this.name = name;
        System.out.println("私有构造器执行：" + name);
    }

    @Override
    public String toString() {
        return "ReflectConstructorStudent{name='" + name + "', age=" + age + "}";
    }
}

public class ReflectConstructor {
    public static void main(String[] args) throws Exception {
        Class<ReflectConstructorStudent> clazz = ReflectConstructorStudent.class;

        // 获取所有 public 构造器
        System.out.println("public 构造器数量：" + clazz.getConstructors().length);
        System.out.println("全部构造器数量（含私有）：" + clazz.getDeclaredConstructors().length);

        // 用无参构造器创建对象
        ReflectConstructorStudent s1 = clazz.getDeclaredConstructor().newInstance();
        System.out.println(s1);

        // 用有参构造器创建对象
        Constructor<ReflectConstructorStudent> ctor = clazz.getConstructor(String.class, int.class);
        ReflectConstructorStudent s2 = ctor.newInstance("李四", 20);
        System.out.println(s2);

        // 私有构造器：暴力反射
        Constructor<ReflectConstructorStudent> privateCtor = clazz.getDeclaredConstructor(String.class);
        privateCtor.setAccessible(true); // 取消访问权限检查
        ReflectConstructorStudent s3 = privateCtor.newInstance("王五");
        System.out.println(s3);
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 定义一个含无参、有参、私有三种构造器的类，用反射分别通过三种构造器创建对象。
 * 2. 编写方法 `Object create(String className, Object... args)`，根据类名和参数反射创建对象。
 * 3. 说明 `setAccessible(true)` 的作用和风险，以及在什么场景下需要用它。
 * ============================================================
 */
