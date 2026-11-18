/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：15-06
 * 课名：泛型类与泛型方法
 * ------------------------------------------------------------
 * 知识点：
 *   - 泛型就是把类型当成参数，写在尖括号里，使用时再指定具体类型，让编译器帮我们做类型检查。
 *   - 泛型类：`class Box<T> { private T data; }`，创建对象时 `new Box<String>()` 指定 T 为 String。
 *   - 泛型方法：`public <T> T getFirst(List<T> list)`，类型参数写在返回值前面。
 *   - 好处：把运行期的 `ClassCastException` 提前到编译期发现，同时省去强制类型转换。
 *   - 泛型只在编译期有效，编译后会被擦除（类型擦除），运行时看不到泛型信息。
 *   - 易错：`new T()`、`new T[]()` 都不允许，因为运行时 T 已经被擦除了。
 * ============================================================
 */

package com.heima.ch15;

import java.util.ArrayList;
import java.util.List;

// ==================== 示例代码 ====================

class GenericClassMethodBox<T> {
    private T data;

    public void set(T data) {
        this.data = data;
    }

    public T get() {
        return data;
    }
}

public class GenericClassMethod {
    public static void main(String[] args) {
        // 泛型类：同一个 GenericClassMethodBox 可以装不同类型，且类型安全
        GenericClassMethodBox<String> stringBox = new GenericClassMethodBox<>();
        stringBox.set("你好");
        String text = stringBox.get();   // 不需要强转
        System.out.println(text.length()); // 3

        GenericClassMethodBox<Integer> intBox = new GenericClassMethodBox<>();
        intBox.set(100);
        int number = intBox.get();
        System.out.println(number + 1);  // 101

        // 泛型方法：调用时由参数推断类型
        System.out.println(firstElement(List.of("A", "B", "C"))); // A
        System.out.println(firstElement(List.of(10, 20)));        // 10

        // 自己实现一个简单的泛型工具方法
        List<String> names = new ArrayList<>();
        names.add("张三");
        names.add("李四");
        System.out.println("最后一个：" + lastElement(names)); // 李四
    }

    public static <T> T firstElement(List<T> list) {
        if (list.isEmpty()) {
            return null;
        }
        return list.get(0);
    }

    public static <T> T lastElement(List<T> list) {
        if (list.isEmpty()) {
            return null;
        }
        return list.get(list.size() - 1);
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 定义泛型类 `Pair<A, B>`，保存两个不同类型的值，提供 `getFirst`、`getSecond` 方法和 `toString`。
 * 2. 编写泛型方法 `<T> void swap(List<T> list, int i, int j)`，交换列表中两个位置的元素。
 * 3. 编写泛型方法 `<T extends Comparable<T>> T max(List<T> list)`，返回列表中的最大值。
 * ============================================================
 */
