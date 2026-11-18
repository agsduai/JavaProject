/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：19-05
 * 课名：反射综合案例
 * ------------------------------------------------------------
 * 知识点：
 *   - 反射最常见的应用：把配置文件/数据库中的"属性名 → 属性值"自动填充到对象中（对象映射）。
 *   - 通用套路：读取 Map/Properties → 遍历键值 → `getDeclaredField` 找到字段 → `setAccessible` → `set`。
 *   - 反射 + 泛型：`<T> T mapToObject(Map<String, Object> data, Class<T> clazz)` 是 ORM 框架的雏形。
 *   - 反射可以突破封装，但会破坏类型安全，业务代码应尽量少用，框架内部才大量使用。
 *   - 反射的性能开销主要来自方法查找和访问检查，缓存 `Class`/`Method`/`Field` 可以显著提升。
 *   - 易错：字段名大小写必须与 Map 的 key 完全一致，否则找不到字段。
 * ============================================================
 */

package com.heima.ch19;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

// ==================== 示例代码 ====================

class ReflectCaseBook {
    private String title;
    private String author;
    private double price;
    private int stock;

    @Override
    public String toString() {
        return "ReflectCaseBook{title='" + title + "', author='" + author
                + "', price=" + price + ", stock=" + stock + "}";
    }
}

public class ReflectCase {
    public static void main(String[] args) throws Exception {
        Map<String, Object> data = new HashMap<>();
        data.put("title", "Java 编程思想");
        data.put("author", "Bruce Eckel");
        data.put("price", 108.0);
        data.put("stock", 30);

        // 把 Map 自动映射成对象
        ReflectCaseBook book = mapToObject(data, ReflectCaseBook.class);
        System.out.println(book);

        // 把对象自动转回 Map
        Map<String, Object> back = objectToMap(book);
        System.out.println("转回 Map：" + back);

        // 反射执行 getter：打印所有以 get 开头的方法返回值
        System.out.println("---- 反射调用 getter ----");
        printGetters(book);
    }

    // 通用映射：Map -> 对象
    public static <T> T mapToObject(Map<String, Object> data, Class<T> clazz) throws Exception {
        T obj = clazz.getDeclaredConstructor().newInstance();
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            try {
                Field field = clazz.getDeclaredField(entry.getKey());
                field.setAccessible(true);
                field.set(obj, convert(field.getType(), entry.getValue()));
            } catch (NoSuchFieldException e) {
                System.out.println("字段不存在，忽略：" + entry.getKey());
            }
        }
        return obj;
    }

    // 通用映射：对象 -> Map
    public static Map<String, Object> objectToMap(Object obj) throws Exception {
        Map<String, Object> map = new HashMap<>();
        for (Field field : obj.getClass().getDeclaredFields()) {
            field.setAccessible(true);
            map.put(field.getName(), field.get(obj));
        }
        return map;
    }

    // 简单类型转换
    private static Object convert(Class<?> type, Object value) {
        if (value == null) {
            return null;
        }
        String s = value.toString();
        if (type == int.class || type == Integer.class) {
            return Integer.parseInt(s);
        }
        if (type == double.class || type == Double.class) {
            return Double.parseDouble(s);
        }
        if (type == long.class || type == Long.class) {
            return Long.parseLong(s);
        }
        if (type == boolean.class || type == Boolean.class) {
            return Boolean.parseBoolean(s);
        }
        return value;
    }

    private static void printGetters(Object obj) throws Exception {
        for (java.lang.reflect.Method m : obj.getClass().getDeclaredMethods()) {
            if (m.getName().startsWith("get") && m.getParameterCount() == 0) {
                m.setAccessible(true);
                System.out.println(m.getName() + "() = " + m.invoke(obj));
            }
        }
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 读取一个 `Properties` 配置文件，用反射把其中的键值自动填充到一个 JavaBean 对象中。
 * 2. 编写通用方法 `void printObject(Object obj)`，用反射打印对象的所有字段名和字段值（不依赖 toString）。
 * 3. 说明反射为什么能"突破封装"，并举例说明框架中哪些功能依赖反射实现。
 * ============================================================
 */
