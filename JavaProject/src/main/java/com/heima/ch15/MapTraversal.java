/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：15-11
 * 课名：Map的遍历与嵌套集合
 * ------------------------------------------------------------
 * 知识点：
 *   - 遍历方式一：`keySet()` 拿到所有键，再逐个 `get` 取值，适合需要键的场景。
 *   - 遍历方式二：`entrySet()` 拿到所有键值对对象 `Map.Entry`，`getKey()`/`getValue()` 直接取值，效率最高。
 *   - 遍历方式三：`forEach((k, v) -> ...)`，JDK8 的 Lambda 写法，代码最简洁。
 *   - `Map.Entry` 是 `Map` 的静态内部接口，代表一个键值对。
 *   - 嵌套集合：`Map<String, List<Student>>` 表示"班级 → 学生列表"，实际项目中非常常见。
 *   - 易错：遍历 `entrySet` 时调用 `map.remove` 同样会抛并发修改异常，要删就用迭代器的 `remove`。
 * ============================================================
 */

package com.heima.ch15;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class MapTraversal {
    public static void main(String[] args) {
        Map<String, Integer> scores = new HashMap<>();
        scores.put("张三", 90);
        scores.put("李四", 85);
        scores.put("王五", 78);

        // 方式一：keySet 遍历键
        for (String name : scores.keySet()) {
            System.out.println(name + " = " + scores.get(name));
        }

        // 方式二：entrySet 遍历键值对，效率最高
        for (Map.Entry<String, Integer> entry : scores.entrySet()) {
            System.out.println(entry.getKey() + " -> " + entry.getValue());
        }

        // 方式三：Lambda 写法
        scores.forEach((k, v) -> System.out.println("forEach：" + k + " 考了 " + v));

        // 嵌套集合：班级 -> 学生姓名列表
        Map<String, List<String>> classes = new HashMap<>();
        List<String> classA = new ArrayList<>(List.of("张三", "李四"));
        List<String> classB = new ArrayList<>(List.of("王五", "赵六", "钱七"));
        classes.put("一班", classA);
        classes.put("二班", classB);

        for (Map.Entry<String, List<String>> entry : classes.entrySet()) {
            System.out.println(entry.getKey() + " 共 " + entry.getValue().size() + " 人：" + entry.getValue());
        }
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 用三种方式遍历同一个 `Map<String, String>`（省份 → 省会），输出所有键值对。
 * 2. 统计一段英文句子中每个单词出现的次数，按次数从高到低输出前 3 个单词。
 * 3. 用 `Map<String, List<Integer>>` 保存每个学生的多门成绩，计算每个学生的平均分并输出。
 * ============================================================
 */
