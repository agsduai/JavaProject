/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：15-12
 * 课名：TreeMap与比较器
 * ------------------------------------------------------------
 * 知识点：
 *   - `TreeMap` 底层是红黑树，按键自动排序，遍历时输出顺序即为排序后的顺序。
 *   - 排序依据：键实现 `Comparable` 用自然排序；否则在构造时传入 `Comparator`。
 *   - `TreeMap` 判断键重复同样是看比较结果是否为 0，返回 0 的键会覆盖原值。
 *   - `Comparable` 是"内部比较器"，类实现后只能有一种默认排序；`Comparator` 是"外部比较器"，可以随时传入不同规则。
 *   - `Comparator` 常用静态方法：`comparing`、`thenComparing`、`reversed`、`nullsFirst`。
 *   - 易错：`Comparator` 返回 `o1 - o2` 在数值极大时可能溢出，推荐写 `Integer.compare(o1, o2)`。
 * ============================================================
 */

package com.heima.ch15;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

// ==================== 示例代码 ====================

class TreeMapComparatorEmployee {
    private String name;
    private int salary;

    public TreeMapComparatorEmployee(String name, int salary) {
        this.name = name;
        this.salary = salary;
    }

    public String getName() {
        return name;
    }

    public int getSalary() {
        return salary;
    }

    @Override
    public String toString() {
        return name + ":" + salary;
    }
}

public class TreeMapComparator {
    public static void main(String[] args) {
        // 键是 String，默认字典序
        Map<String, String> capitals = new TreeMap<>();
        capitals.put("shanghai", "上海");
        capitals.put("beijing", "北京");
        capitals.put("guangzhou", "广州");
        System.out.println("按 key 排序：" + capitals); // beijing, guangzhou, shanghai

        // 自定义比较器：按 key 长度排序，长度相同按字典序
        Map<String, String> byLength = new TreeMap<>(
                Comparator.comparingInt(String::length).thenComparing(Comparator.naturalOrder()));
        byLength.put("java", "语言");
        byLength.put("sql", "查询");
        byLength.put("jdbc", "连接");
        System.out.println("按长度排序：" + byLength);

        // 对象列表排序：多级比较器
        List<TreeMapComparatorEmployee> employees = new ArrayList<>();
        employees.add(new TreeMapComparatorEmployee("张三", 12000));
        employees.add(new TreeMapComparatorEmployee("李四", 15000));
        employees.add(new TreeMapComparatorEmployee("王五", 12000));
        employees.sort(Comparator.comparingInt(TreeMapComparatorEmployee::getSalary)
                .reversed()
                .thenComparing(TreeMapComparatorEmployee::getName));
        System.out.println("薪资降序：" + employees);
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 用 `TreeMap` 保存学生姓名和成绩，分别按姓名和按成绩两种规则输出。
 * 2. 定义 `Product` 类（名称、价格、销量），用 `Comparator` 实现"先按销量降序，销量相同按价格升序"。
 * 3. 用 `TreeMap` 统计一段文字中各字符出现次数，并按字符升序输出结果。
 * ============================================================
 */
