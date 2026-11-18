/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：15-08
 * 课名：Set接口与HashSet去重原理
 * ------------------------------------------------------------
 * 知识点：
 *   - `Set` 无序（不保证存取顺序）、不可重复、无索引，不能用普通 for 按下标遍历。
 *   - `HashSet` 底层是哈希表（JDK8 起为数组 + 链表 + 红黑树），增删查平均时间复杂度接近 O(1)。
 *   - 去重依据：先比 `hashCode()`，哈希值不同一定不是同一元素；哈希值相同再比 `equals()`，返回 `true` 才判定重复。
 *   - 自定义对象要参与去重，必须同时重写 `hashCode()` 和 `equals()`，只重写一个会导致去重失效或性能退化。
 *   - 存储过程：用 `hashCode` 经扰动函数算出下标 → 该位置为空直接放 → 有元素则用 `equals` 逐个比较。
 *   - 易错：`HashSet` 的"无序"是指不保证顺序，不是随机；`LinkedHashSet` 才能保证插入顺序。
 * ============================================================
 */

package com.heima.ch15;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

// ==================== 示例代码 ====================

class HashSetDedupStudent {
    private String name;
    private int age;

    public HashSetDedupStudent(String name, int age) {
        this.name = name;
        this.age = age;
    }

    // 参与去重必须重写 hashCode：属性相同则哈希值相同
    @Override
    public int hashCode() {
        return Objects.hash(name, age);
    }

    // equals 判断属性内容是否一致
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        HashSetDedupStudent student = (HashSetDedupStudent) o;
        return age == student.age && Objects.equals(name, student.name);
    }

    @Override
    public String toString() {
        return name + "(" + age + ")";
    }
}

public class HashSetDedup {
    public static void main(String[] args) {
        Set<String> set = new HashSet<>();
        set.add("Java");
        set.add("MySQL");
        set.add("Java");   // 重复元素，添加失败
        System.out.println("元素个数：" + set.size()); // 2
        System.out.println("内容：" + set);            // 顺序不保证

        // 自定义对象去重：重写 hashCode 与 equals 后属性相同即视为重复
        Set<HashSetDedupStudent> students = new HashSet<>();
        students.add(new HashSetDedupStudent("张三", 18));
        students.add(new HashSetDedupStudent("张三", 18)); // 被判定为重复
        students.add(new HashSetDedupStudent("李四", 20));
        System.out.println("学生人数：" + students.size()); // 2
        System.out.println(students);
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 创建一个 `HashSet<Integer>`，存入 10 个 1~20 之间的随机数，利用集合特性自动去重，输出结果和元素个数。
 * 2. 定义 `Book` 类（书名、作者、价格），把 5 本书（其中 2 本完全相同）放入 `HashSet`，验证去重是否生效。
 * 3. 删除 `Student` 类中的 `hashCode` 方法，只保留 `equals`，观察去重结果的变化并解释原因。
 * ============================================================
 */
