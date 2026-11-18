/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：15-10
 * 课名：Map接口与HashMap
 * ------------------------------------------------------------
 * 知识点：
 *   - `Map<K, V>` 存储键值对，键唯一、值可重复，一个键对应一个值。
 *   - 常用方法：`put(K, V)` 添加或覆盖（返回被覆盖的旧值）、`get(K)` 取值、`remove(K)` 删除、`containsKey`/`containsValue`。
 *   - `size()` 返回键值对数量，`isEmpty()` 判断是否为空，`clear()` 清空。
 *   - `put` 遇到已存在的键会覆盖旧值，并返回旧值；键不存在时返回 `null`。
 *   - `HashMap` 底层与 `HashSet` 相同（哈希表），键的去重同样依赖 `hashCode` + `equals`。
 *   - 易错：`Map` 不是 `Collection` 的子接口，不能用增强 for 直接遍历，必须先转成 `Set`。
 * ============================================================
 */

package com.heima.ch15;

import java.util.HashMap;
import java.util.Map;

// ==================== 示例代码 ====================

public class MapHashMap {
    public static void main(String[] args) {
        Map<String, Integer> scores = new HashMap<>();
        scores.put("张三", 90);
        scores.put("李四", 85);
        scores.put("王五", 78);
        System.out.println("原始：" + scores);

        // 键已存在：覆盖并返回旧值
        Integer old = scores.put("张三", 95);
        System.out.println("被覆盖的旧值：" + old);  // 90
        System.out.println("覆盖后：" + scores);

        // 键不存在时 put 返回 null
        System.out.println("新增赵六返回：" + scores.put("赵六", 60)); // null

        System.out.println("张三的成绩：" + scores.get("张三"));      // 95
        System.out.println("不存在的键：" + scores.get("钱七"));       // null
        System.out.println("包含李四吗：" + scores.containsKey("李四")); // true
        System.out.println("有 60 分吗：" + scores.containsValue(60));  // true

        System.out.println("删除李四：" + scores.remove("李四")); // 85
        System.out.println("键值对数量：" + scores.size());      // 3
        System.out.println("最终：" + scores);
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 用 `HashMap<String, Integer>` 统计一个字符串中每个字符出现的次数，输出统计结果。
 * 2. 用 `HashMap` 保存 5 个学生的姓名和成绩，找出成绩最高的学生并输出。
 * 3. 编写方法 `merge(Map<String, Integer> map, String key, int value)`，若键已存在则累加值，否则新增键值对。
 * ============================================================
 */
