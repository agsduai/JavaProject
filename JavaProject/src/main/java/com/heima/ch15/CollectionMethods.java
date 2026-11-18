/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：15-02
 * 课名：Collection的常用方法
 * ------------------------------------------------------------
 * 知识点：
 *   - `add(E e)` 添加元素，成功返回 `true`；`remove(Object o)` 删除指定元素，删除成功返回 `true`。
 *   - `contains(Object o)` 判断是否包含某元素；`isEmpty()` 判断集合是否为空；`size()` 返回元素个数。
 *   - `clear()` 清空集合；`toArray()` 把集合转成数组。
 *   - 集合重写了 `toString`，直接打印集合名就能看到 `[元素1, 元素2]` 的内容。
 *   - `List` 特有的 `remove(int index)` 与 `Collection` 的 `remove(Object o)` 重载，参数传 `int` 时删的是索引，不是元素。
 *   - 易错：`contains` 和 `remove` 底层依赖 `equals`，自定义对象不重写 `equals` 时比较的是地址，往往判断不出来。
 * ============================================================
 */

package com.heima.ch15;

import java.util.ArrayList;
import java.util.Collection;

// ==================== 示例代码 ====================

public class CollectionMethods {
    public static void main(String[] args) {
        Collection<String> c = new ArrayList<>();
        System.out.println(c.add("Java"));      // true
        c.add("MySQL");
        c.add("JDBC");
        System.out.println("集合内容：" + c);    // [Java, MySQL, JDBC]
        System.out.println("元素个数：" + c.size());  // 3

        System.out.println("包含 Java 吗：" + c.contains("Java")); // true
        System.out.println("删除 MySQL：" + c.remove("MySQL"));    // true
        System.out.println("删除后：" + c);      // [Java, JDBC]

        System.out.println("是否为空：" + c.isEmpty()); // false
        Object[] arr = c.toArray();
        System.out.println("转数组后长度：" + arr.length); // 2

        c.clear();
        System.out.println("清空后：" + c + "，是否为空：" + c.isEmpty()); // []，true
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 向 `Collection<String>` 中依次添加 "红"、"黄"、"蓝"，再用增强 for 遍历打印，最后删除 "黄" 并输出剩余元素。
 * 2. 编写方法 `countContains(Collection<String> c, String keyword)`，统计集合中包含指定关键字的元素个数。
 * 3. 定义 `Student` 类（姓名、年龄），把两个属性完全相同的对象分别加入集合，测试 `contains` 的返回结果，并解释原因。
 * ============================================================
 */
