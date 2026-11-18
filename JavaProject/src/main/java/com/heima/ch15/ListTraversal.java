/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：15-04
 * 课名：List的遍历与并发修改异常
 * ------------------------------------------------------------
 * 知识点：
 *   - 三种遍历方式：普通 for（有索引才能用）、增强 for（写法最简洁）、迭代器 `Iterator`。
 *   - 迭代器的三个方法：`hasNext()` 判断还有没有元素、`next()` 取出下一个元素、`remove()` 删除刚取出的元素。
 *   - 并发修改异常 `ConcurrentModificationException`：用集合自身的 `add`/`remove` 在遍历过程中改动集合，迭代器检测到"修改次数"不一致就会抛出。
 *   - 解决办法：用迭代器自己的 `remove()`；或者用普通 for 倒序遍历删除；或者先收集要删的元素，遍历结束后统一删除。
 *   - 增强 for 底层就是迭代器，所以增强 for 中调用集合的删除方法同样会抛异常。
 *   - 易错：`for (String s : list) { list.remove(s); }` 是新手最常写出的崩溃代码。
 * ============================================================
 */

package com.heima.ch15;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

// ==================== 示例代码 ====================

public class ListTraversal {
    public static void main(String[] args) {
        List<String> list = new ArrayList<>();
        list.add("张三");
        list.add("李四");
        list.add("王五");
        list.add("赵六");

        // 方式一：普通 for，靠索引
        for (int i = 0; i < list.size(); i++) {
            System.out.println("普通 for：" + list.get(i));
        }

        // 方式二：增强 for，底层是迭代器
        for (String name : list) {
            System.out.println("增强 for：" + name);
        }

        // 方式三：迭代器，遍历过程中删除元素必须用它自己的 remove
        Iterator<String> it = list.iterator();
        while (it.hasNext()) {
            String name = it.next();
            if ("李四".equals(name)) {
                it.remove(); // 安全删除，不会抛并发修改异常
            }
        }
        System.out.println("删除李四后：" + list); // [张三, 王五, 赵六]

        // 反向演示：普通 for 倒序遍历删除，同样安全
        List<Integer> nums = new ArrayList<>(List.of(1, 2, 3, 4, 5, 6));
        for (int i = nums.size() - 1; i >= 0; i--) {
            if (nums.get(i) % 2 == 0) {
                nums.remove(i);
            }
        }
        System.out.println("去掉偶数后：" + nums); // [1, 3, 5]
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 用三种方式分别遍历同一个 `List<String>`，统计元素个数，比较三种写法的优缺点。
 * 2. 一个 `List<Integer>` 中有重复的奇数，用迭代器删除所有小于 10 的元素，输出结果。
 * 3. 复现一次 `ConcurrentModificationException`（在增强 for 中调用 `list.remove`），再把代码改成安全写法，并说明原因。
 * ============================================================
 */
