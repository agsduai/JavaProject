/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：15-07
 * 课名：泛型通配符与上下限
 * ------------------------------------------------------------
 * 知识点：
 *   - `?` 是无界通配符，表示"任意类型"，常用于只读场景，如 `void print(List<?> list)`。
 *   - `? extends Number` 是上限通配符，表示 Number 或其子类（Integer、Double 等），只能读不能写。
 *   - `? super Integer` 是下限通配符，表示 Integer 或其父类（Number、Object），可以写入。
 *   - 记忆口诀：上限只读（生产者），下限只写（消费者）。
 *   - 泛型不支持协变：`List<Integer>` 不是 `List<Number>` 的子类型，所以需要通配符来放宽。
 *   - 易错：`List<Object> list = new ArrayList<String>()` 编译不通过，必须写 `List<?>` 或 `List<? extends Object>`。
 * ============================================================
 */

package com.heima.ch15;

import java.util.ArrayList;
import java.util.List;

// ==================== 示例代码 ====================

public class GenericWildcard {
    public static void main(String[] args) {
        List<Integer> ints = List.of(1, 2, 3);
        List<Double> doubles = List.of(1.5, 2.5);
        List<String> names = List.of("张三", "李四");

        printAll(ints);    // 无界通配符：什么集合都能传
        printAll(doubles);
        printAll(names);

        System.out.println("整数和：" + sum(ints));     // 6.0
        System.out.println("小数和：" + sum(doubles));  // 4.0
        // sum(names); // 编译报错：String 不是 Number 的子类

        List<Number> numbers = new ArrayList<>();
        addIntegers(numbers);  // 下限通配符：可以往里写 Integer
        System.out.println("写入后：" + numbers); // [10, 20]
    }

    // 无界通配符：只读遍历，最通用
    public static void printAll(List<?> list) {
        for (Object o : list) {
            System.out.print(o + " ");
        }
        System.out.println();
    }

    // 上限通配符：Number 及其子类，可以安全地当 Number 读
    public static double sum(List<? extends Number> list) {
        double total = 0;
        for (Number n : list) {
            total += n.doubleValue();
        }
        return total;
    }

    // 下限通配符：Integer 及其父类，可以安全地写入 Integer
    public static void addIntegers(List<? super Integer> list) {
        list.add(10);
        list.add(20);
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 编写方法 `printNumbers(List<? extends Number> list)`，输出集合中的最大值和最小值。
 * 2. 编写方法 `fill(List<? super Integer> list, int count)`，向集合中填入 count 个 0。
 * 3. 解释为什么 `List<Integer>` 不能赋值给 `List<Number>`，并给出两种可行的替代写法。
 * ============================================================
 */
