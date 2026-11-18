/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：19-04
 * 课名：获取成员方法并调用
 * ------------------------------------------------------------
 * 知识点：
 *   - `getMethods()` 获取所有 public 方法（含父类）；`getDeclaredMethods()` 获取本类全部方法（含私有）。
 *   - `getMethod(String name, Class... params)` 按方法名和参数类型获取指定 public 方法。
 *   - `invoke(Object obj, Object... args)` 调用方法，返回值是方法的返回结果；静态方法 obj 传 `null`。
 *   - 私有方法需要 `setAccessible(true)`；`Method.getName()`、`getParameterTypes()`、`getReturnType()` 用于获取方法信息。
 *   - 反射调用方法的性能低于直接调用，频繁调用场景应缓存 `Method` 对象。
 *   - 易错：`invoke` 参数类型必须匹配，传 `Integer` 给 `int` 形参会自动拆箱，但传 `null` 给基本类型会抛异常。
 * ============================================================
 */

package com.heima.ch19;

import java.lang.reflect.Method;

// ==================== 示例代码 ====================

class ReflectMethodCalculator {
    public int add(int a, int b) {
        return a + b;
    }

    public String greet(String name) {
        return "你好，" + name;
    }

    private double secret(double x) {
        return x * x;
    }

    public static void staticInfo() {
        System.out.println("这是一个静态方法");
    }
}

public class ReflectMethod {
    public static void main(String[] args) throws Exception {
        Class<ReflectMethodCalculator> clazz = ReflectMethodCalculator.class;
        ReflectMethodCalculator calc = new ReflectMethodCalculator();

        // 获取所有 public 方法（包含 Object 继承来的）
        System.out.println("public 方法数量：" + clazz.getMethods().length);
        System.out.println("本类声明的方法数量：" + clazz.getDeclaredMethods().length);

        // 调用 public 方法
        Method add = clazz.getMethod("add", int.class, int.class);
        Object result = add.invoke(calc, 10, 20);
        System.out.println("add 结果：" + result);
        System.out.println("返回值类型：" + add.getReturnType().getSimpleName());
        System.out.println("参数个数：" + add.getParameterCount());

        Method greet = clazz.getMethod("greet", String.class);
        System.out.println("greet 结果：" + greet.invoke(calc, "张三"));

        // 调用私有方法
        Method secret = clazz.getDeclaredMethod("secret", double.class);
        secret.setAccessible(true);
        System.out.println("私有方法结果：" + secret.invoke(calc, 3.0));

        // 调用静态方法：obj 传 null
        Method staticInfo = clazz.getMethod("staticInfo");
        staticInfo.invoke(null);
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 用反射调用 `String` 类的 `length`、`substring`、`toUpperCase` 方法，输出调用结果。
 * 2. 编写方法 `Object call(Object obj, String methodName, Object... args)`，按方法名动态调用方法并返回结果。
 * 3. 用反射统计一个类中所有 public 方法的数量，并列出方法名和参数个数。
 * ============================================================
 */
