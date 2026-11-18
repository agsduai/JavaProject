/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 课号：12-09
 * 课名：ArrayList集合
 * ------------------------------------------------------------
 * 知识点：
 *   - 数组长度固定、增删要手动搬元素；ArrayList 集合长度可变、自动扩容，适合存"数量不确定"的一批数据。
 *   - `ArrayList<E>` 在 java.util 包下，E 是泛型：限定集合只能存这一种类型，如 ArrayList<String>；尖括号里只能写引用类型。
 *   - 基本类型要存进集合需要用包装类：ArrayList<Integer>、ArrayList<Double>（JDK5 之后 int 与 Integer 自动装箱、拆箱）。
 *   - 五个常用方法：add(e) 添加、get(i) 获取、remove 删除、set(i, e) 修改、size() 获取长度；集合可以直接打印查看内容。
 *   - remove(整数) 默认按索引删除；想按内容删除要传对象，如 list.remove(Integer.valueOf(10))，这是初学者常见的歧义点。
 *   - 遍历删除陷阱：for-each 或普通 for 正序删除会漏删元素，for-each 中直接 remove 还会报 ConcurrentModificationException；解决办法是倒序遍历删除，或使用迭代器的 remove 方法。
 * ============================================================
 */

package com.heima.ch12;

import java.util.ArrayList;

// ==================== 示例代码 ====================

public class ArrayListDemo {
    public static void main(String[] args) {
        // 创建集合，泛型限定只能存 String
        ArrayList<String> list = new ArrayList<>();

        // add 添加元素
        list.add("Java");
        list.add("Python");
        list.add("C++");
        System.out.println(list); // [Java, Python, C++]，集合可以直接打印

        // get 根据索引获取，size 获取长度
        System.out.println(list.get(0));            // Java
        System.out.println("集合长度：" + list.size()); // 3

        // set 修改指定索引的元素
        list.set(1, "Go");
        System.out.println(list); // [Java, Go, C++]

        // remove 按索引删除
        list.remove(2);
        System.out.println(list); // [Java, Go]

        // ===== 遍历删除陷阱：删除所有包含 a 的元素 =====
        ArrayList<String> words = new ArrayList<>();
        words.add("abc");
        words.add("aaa");
        words.add("bbb");
        words.add("acc");

        // 注意：正序遍历删除会漏删元素，for-each 中删除会报异常
        // 正确做法：倒序遍历删除
        for (int i = words.size() - 1; i >= 0; i--) {
            if (words.get(i).contains("a")) {
                words.remove(i);
            }
        }
        System.out.println(words); // [bbb]
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 创建 ArrayList<Integer>，存入 10 个 1~100 的随机整数，遍历求出最大值、最小值与总和。
 * 2. 创建 ArrayList<String> 存 5 个电影名，键盘录入一个电影名，存在则删除并提示删除成功，不存在则提示未找到。
 * 3. 创建 ArrayList<Integer> 存入 8 个含重复值的整数，删除其中所有偶数后输出剩余元素。
 * ============================================================
 */
