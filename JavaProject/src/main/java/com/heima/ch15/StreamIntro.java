/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：15-14
 * 课名：Stream流入门
 * ------------------------------------------------------------
 * 知识点：
 *   - Stream 是 JDK8 引入的流式 API，用于对集合做"声明式"的批量处理，代码比循环更简洁。
 *   - 三步走：获取流（`list.stream()`）→ 中间操作（`filter`、`map`、`sorted`、`limit`、`distinct`）→ 终结操作（`forEach`、`collect`、`count`、`sum`）。
 *   - 中间操作是**惰性**的，只有遇到终结操作才会真正执行；一个流只能被消费一次，用完就失效。
 *   - `filter` 过滤、`map` 映射转换、`sorted` 排序、`distinct` 去重、`limit`/`skip` 分页。
 *   - `collect(Collectors.toList())` 收集成集合，`Collectors.groupingBy` 分组，`Collectors.joining` 拼接字符串。
 *   - 易错：Stream 不修改原集合；把 `stream()` 的结果赋值给变量后重复使用会抛 `IllegalStateException`。
 * ============================================================
 */

package com.heima.ch15;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// ==================== 示例代码 ====================

class StreamIntroEmployee {
    private String name;
    private String dept;
    private int salary;

    public StreamIntroEmployee(String name, String dept, int salary) {
        this.name = name;
        this.dept = dept;
        this.salary = salary;
    }

    public String getName() {
        return name;
    }

    public String getDept() {
        return dept;
    }

    public int getSalary() {
        return salary;
    }

    @Override
    public String toString() {
        return name + "(" + salary + ")";
    }
}

public class StreamIntro {
    public static void main(String[] args) {
        List<StreamIntroEmployee> list = new ArrayList<>();
        list.add(new StreamIntroEmployee("张三", "研发部", 15000));
        list.add(new StreamIntroEmployee("李四", "研发部", 18000));
        list.add(new StreamIntroEmployee("王五", "市场部", 12000));
        list.add(new StreamIntroEmployee("赵六", "市场部", 13000));
        list.add(new StreamIntroEmployee("钱七", "财务部", 11000));

        // 过滤 + 排序 + 输出
        list.stream()
                .filter(e -> e.getSalary() > 12000)
                .sorted((a, b) -> b.getSalary() - a.getSalary())
                .forEach(e -> System.out.println("高薪：" + e));

        // 映射：只取姓名，收集成列表
        List<String> names = list.stream()
                .map(StreamIntroEmployee::getName)
                .collect(Collectors.toList());
        System.out.println("所有姓名：" + names);

        // 分组：按部门分组
        Map<String, List<StreamIntroEmployee>> byDept = list.stream()
                .collect(Collectors.groupingBy(StreamIntroEmployee::getDept));
        byDept.forEach((dept, emps) -> System.out.println(dept + " -> " + emps));

        // 统计：总人数、总薪资
        long count = list.stream().filter(e -> e.getSalary() >= 12000).count();
        int total = list.stream().mapToInt(StreamIntroEmployee::getSalary).sum();
        System.out.println("薪资不低于 12000 的人数：" + count + "，总薪资：" + total);

        // 字符串拼接
        String joined = list.stream().map(StreamIntroEmployee::getName).collect(Collectors.joining("、"));
        System.out.println("姓名拼接：" + joined);
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 给定 `List<Integer>`，用 Stream 过滤出所有偶数、平方后去重、按降序输出前 5 个。
 * 2. 给定 `List<String>`，用 Stream 过滤长度大于 3 的字符串，转成大写并收集到新列表。
 * 3. 给定员工列表，用 Stream 按部门分组统计各部门的平均薪资，并输出结果。
 * ============================================================
 */
