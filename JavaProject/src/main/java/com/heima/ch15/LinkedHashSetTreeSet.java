/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：15-09
 * 课名：LinkedHashSet与TreeSet
 * ------------------------------------------------------------
 * 知识点：
 *   - `LinkedHashSet` 继承 `HashSet`，底层在哈希表基础上额外维护一条双向链表，因此**存取顺序一致**。
 *   - `TreeSet` 底层是红黑树，元素**自动排序**，增删查时间复杂度 O(log n)。
 *   - `TreeSet` 排序规则：元素实现 `Comparable` 接口则用 `compareTo`；否则创建时传入 `Comparator`。
 *   - `TreeSet` 判断重复用的是 `compareTo`/`compare` 返回 0，而不是 `equals`，返回值 0 的元素加不进去。
 *   - 三种 Set 的选择：只要去重用 `HashSet`，要保留插入顺序用 `LinkedHashSet`，要排序用 `TreeSet`。
 *   - 易错：向 `TreeSet` 添加没有实现 `Comparable` 且未传比较器的对象，会抛 `ClassCastException`。
 * ============================================================
 */

package com.heima.ch15;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;

// ==================== 示例代码 ====================

public class LinkedHashSetTreeSet {
    public static void main(String[] args) {
        // LinkedHashSet：存取顺序一致
        Set<String> linked = new LinkedHashSet<>();
        linked.add("C");
        linked.add("A");
        linked.add("B");
        linked.add("A"); // 重复，加不进去
        System.out.println("LinkedHashSet：" + linked); // [C, A, B]

        // TreeSet：自然排序（字符串按字典序）
        Set<String> tree = new TreeSet<>();
        tree.add("banana");
        tree.add("apple");
        tree.add("cherry");
        System.out.println("TreeSet 字典序：" + tree); // [apple, banana, cherry]

        // TreeSet 存自定义对象：必须给出比较规则
        TreeSet<Integer> scores = new TreeSet<>((a, b) -> b - a); // 降序
        scores.add(88);
        scores.add(95);
        scores.add(70);
        scores.add(88); // 比较结果为 0，视为重复
        System.out.println("降序成绩：" + scores); // [95, 88, 70]

        // 常用导航方法：first/last 按"排序规则"取首尾，降序规则下首元素就是最大值
        System.out.println("排序后第一个：" + scores.first()); // 95
        System.out.println("排序后最后一个：" + scores.last()); // 70
        System.out.println("比 90 小的元素：" + scores.headSet(90)); // [95]（按降序规则 95 排在 90 前）
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 用 `LinkedHashSet` 去掉一段文字中的重复字符，并保持字符原本出现的顺序。
 * 2. 用 `TreeSet` 对 8 个学生的成绩排序，分别按升序和降序输出。
 * 3. 定义 `Person` 类（姓名、年龄），用 `TreeSet` 按年龄升序、年龄相同按姓名排序，输出全部对象。
 * ============================================================
 */
