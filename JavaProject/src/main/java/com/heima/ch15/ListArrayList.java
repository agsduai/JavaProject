/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：15-03
 * 课名：List接口与ArrayList
 * ------------------------------------------------------------
 * 知识点：
 *   - `List` 有序（存入顺序就是取出顺序）、可重复、有索引，可以用下标访问元素。
 *   - 特有方法：`add(int index, E e)` 指定位置插入、`get(int index)` 取元素、`set(int index, E e)` 修改并返回旧值、`remove(int index)` 按索引删除。
 *   - `ArrayList` 底层是数组，查询快（按下标直接定位）、增删慢（要移动元素）。
 *   - `ArrayList` 初始容量为 10，容量不够时按 1.5 倍扩容。
 *   - 插入位置越界会抛 `IndexOutOfBoundsException`，合法范围是 `0 ~ size()`。
 *   - 易错：`list.remove(1)` 删除的是索引 1 的元素；想删除元素 1 要写 `list.remove(Integer.valueOf(1))`。
 * ============================================================
 */

package com.heima.ch15;

import java.util.ArrayList;
import java.util.List;

// ==================== 示例代码 ====================

public class ListArrayList {
    public static void main(String[] args) {
        List<String> list = new ArrayList<>();
        list.add("A");
        list.add("B");
        list.add("C");
        System.out.println("原始：" + list);       // [A, B, C]

        list.add(1, "X");                        // 在下标 1 处插入
        System.out.println("插入后：" + list);     // [A, X, B, C]

        System.out.println("下标 2 的元素：" + list.get(2)); // B
        String old = list.set(0, "AA");           // 修改并返回旧值
        System.out.println("被替换的旧值：" + old + "，现在：" + list);

        String removed = list.remove(1);          // 按索引删除，返回被删元素
        System.out.println("删掉的是：" + removed + "，剩下：" + list);

        // 基本类型包装类的坑：remove(1) 删索引，remove(Integer.valueOf(1)) 删元素
        List<Integer> nums = new ArrayList<>();
        nums.add(10);
        nums.add(20);
        nums.add(1);
        nums.remove(1);                           // 删索引 1，即 20
        System.out.println("remove(1) 之后：" + nums); // [10, 1]
        nums.remove(Integer.valueOf(1));          // 删元素 1
        System.out.println("按值删除后：" + nums);      // [10]
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 创建一个 `ArrayList<String>` 存入 5 个城市名，在中间位置插入一个新城市，再删除第一个城市，最后打印全部内容。
 * 2. 用一个 `List<Integer>` 保存 10 个整数，把下标为偶数的元素全部乘以 2，要求用 `set` 方法原地修改。
 * 3. 编写方法 `insertSorted(List<Integer> list, int value)`，把 value 插入到升序列表中，并保持列表仍然升序。
 * ============================================================
 */
