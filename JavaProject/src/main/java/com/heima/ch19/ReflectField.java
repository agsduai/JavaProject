/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：19-03
 * 课名：获取成员变量并赋值
 * ------------------------------------------------------------
 * 知识点：
 *   - `getFields()` 获取所有 public 字段（含继承的）；`getDeclaredFields()` 获取本类全部字段（含私有）。
 *   - `getField(String name)` / `getDeclaredField(String name)` 按名字获取指定字段。
 *   - `set(Object obj, Object value)` 给字段赋值；`get(Object obj)` 取出字段值。
 *   - 私有字段同样需要 `setAccessible(true)` 才能读写。
 *   - `Field.getType()` 返回字段类型，`getName()` 返回字段名，`getModifiers()` 返回修饰符。
 *   - 易错：静态字段赋值时 `set` 的第一个参数可以传 `null`；实例字段传 `null` 会抛 `NullPointerException`。
 * ============================================================
 */

package com.heima.ch19;

import java.lang.reflect.Field;

// ==================== 示例代码 ====================

class ReflectFieldUser {
    public String username;
    private String password;
    private int age;
    private static String systemName = "学习系统";

    public ReflectFieldUser(String username, String password, int age) {
        this.username = username;
        this.password = password;
        this.age = age;
    }

    @Override
    public String toString() {
        return "ReflectFieldUser{username='" + username + "', password='" + password
                + "', age=" + age + ", system=" + systemName + "}";
    }
}

public class ReflectField {
    public static void main(String[] args) throws Exception {
        Class<ReflectFieldUser> clazz = ReflectFieldUser.class;
        ReflectFieldUser user = new ReflectFieldUser("zhangsan", "123456", 18);

        // 获取所有字段
        System.out.println("本类全部字段：");
        for (Field f : clazz.getDeclaredFields()) {
            System.out.println("  " + f.getName() + " : " + f.getType().getSimpleName());
        }

        // 读写 public 字段
        Field username = clazz.getField("username");
        System.out.println("原用户名：" + username.get(user));
        username.set(user, "lisi");
        System.out.println("修改后：" + username.get(user));

        // 读写 private 字段：需要暴力反射
        Field password = clazz.getDeclaredField("password");
        password.setAccessible(true);
        System.out.println("原密码：" + password.get(user));
        password.set(user, "abc888");
        System.out.println("修改后：" + password.get(user));

        // 静态字段：对象参数传 null
        Field systemName = clazz.getDeclaredField("systemName");
        systemName.setAccessible(true);
        System.out.println("静态字段原值：" + systemName.get(null));
        systemName.set(null, "反射系统");
        System.out.println("静态字段新值：" + systemName.get(null));

        System.out.println("最终对象：" + user);
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 用反射获取一个类的全部字段，输出每个字段的名称、类型和修饰符。
 * 2. 编写方法 `void setField(Object obj, String fieldName, Object value)`，通过反射给指定字段赋值（支持私有字段）。
 * 3. 用反射把所有 `String` 类型的字段值替换成"*"，实现简单的脱敏工具。
 * ============================================================
 */
