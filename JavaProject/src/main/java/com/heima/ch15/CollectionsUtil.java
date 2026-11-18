/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：15-13
 * 课名：Collections工具类与不可变集合
 * ------------------------------------------------------------
 * 知识点：
 *   - `Collections` 是操作集合的工具类，方法全是静态的，位于 `java.util` 包。
 *   - 常用方法：`sort(List)` 排序、`reverse(List)` 反转、`shuffle(List)` 随机打乱、`max`/`min` 求最值、`swap` 交换。
 *   - `Collections.sort` 的第二种重载可以传 `Comparator` 自定义排序规则。
 *   - 不可变集合：`List.of(...)`、`Set.of(...)`、`Map.of(...)`，创建后不能增删改，长度固定。
 *   - 不可变集合好处：线程安全、可以放心共享；`List.of` 不允许 `null` 元素，重复元素会抛 `IllegalArgumentException`。
 *   - 易错：`Collections.sort` 只能排序 `List`，不能排序 `Set`；不可变集合调用 `add` 会抛 `UnsupportedOperationException`。
 * ============================================================
 */

package com.heima.ch15;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// ==================== 示例代码 ====================

public class CollectionsUtil {
    public static void main(String[] args) {
        List<Integer> nums = new ArrayList<>(List.of(5, 2, 9, 1, 7));

        Collections.sort(nums);                       // 升序
        System.out.println("升序：" + nums);           // [1, 2, 5, 7, 9]

        Collections.sort(nums, (a, b) -> b - a);      // 自定义降序
        System.out.println("降序：" + nums);           // [9, 7, 5, 2, 1]

        Collections.reverse(nums);
        System.out.println("反转：" + nums);           // [1, 2, 5, 7, 9]

        System.out.println("最大值：" + Collections.max(nums)); // 9
        System.out.println("最小值：" + Collections.min(nums)); // 1

        Collections.swap(nums, 0, 4);
        System.out.println("交换首尾：" + nums);        // [9, 2, 5, 7, 1]

        Collections.shuffle(nums);
        System.out.println("随机打乱：" + nums);

        // 不可变集合：创建后不能修改
        List<String> fruits = List.of("苹果", "香蕉", "橙子");
        System.out.println("不可变集合：" + fruits);
        try {
            fruits.add("西瓜"); // 抛异常
        } catch (UnsupportedOperationException e) {
            System.out.println("不可变集合不能添加元素");
        }
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 创建一个含 10 个随机数的 `ArrayList`，用 `Collections` 完成排序、反转、打乱并分别输出。
 * 2. 用 `List.of` 创建一周七天的不可变集合，尝试修改并捕获异常，输出提示信息。
 * 3. 编写方法 `List<String> sortedNames(List<String> names)`，返回按长度升序排列的新列表，不修改原列表。
 * ============================================================
 */
