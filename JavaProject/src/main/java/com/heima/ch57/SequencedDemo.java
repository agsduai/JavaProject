/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：57-08
 * 课名：Sequenced Collections 与集合增强
 * ------------------------------------------------------------
 * 知识点：
 *   - **Sequenced Collections（Java 21 定稿）补齐了"有序集合"的统一抽象**：`SequencedCollection`（addFirst/addLast、getFirst/getLast、reversed()）、`SequencedSet`（去重有序）、`SequencedMap`（putFirst/putLast、pollFirstEntry、reversed()）——**List/Deque/LinkedHashSet/LinkedHashMap 全部实现它**。
 *   - **解决的历史痛点**：取"第一个元素"过去要写 `list.get(0)` / `deque.peekFirst()` / `set.iterator().next()` 三套 API；**现在统一 `getFirst()`**（空集合抛 `NoSuchElementException`，语义明确）。
 *   - **`reversed()` 是视图不是拷贝**：`list.reversed()` 返回反向视图，改动会双向可见——**与 `Collections.reverse()`（原地修改）完全不同**，这是最大的误用点。
 *   - **集合工厂增强**：`List.of` / `Set.of` / `Map.of` 的不可变语义（16-02 已有）、`Stream.toList()`（不可变）、`List.copyOf`（防御性拷贝）——**现代 Java 的默认选择是不可变集合**，可变集合需要显式 `new ArrayList<>()`。
 *   - **与 15 章的关系**：15 章讲集合选型（List/Set/Map 语义）；本章讲"顺序视图"的统一 APIs——**老代码里那些 `get(0)`、`iterator().next()` 可以统一替换**。
 *   - 易错点：`reversed()` 当拷贝用（改视图影响原集合）；空集合 `getFirst()` 抛异常未处理；`List.of` 里放 null（直接 NPE）；`Stream.toList()` 返回不可变却尝试 add。
 * ============================================================
 */

package com.heima.ch57;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;

// ==================== 示例代码 ====================

public class SequencedDemo {
    public static void main(String[] args) {
        System.out.println("【统一的首尾访问】");
        var list = new ArrayList<>(List.of("A", "B", "C"));
        var deque = new java.util.ArrayDeque<>(List.of("X", "Y", "Z"));
        var linkedSet = new LinkedHashSet<>(List.of(1, 2, 3));
        System.out.printf("  List     first=%s last=%s%n", list.getFirst(), list.getLast());
        System.out.printf("  Deque    first=%s last=%s%n", deque.getFirst(), deque.getLast());
        System.out.printf("  Set      first=%d last=%d%n", linkedSet.getFirst(), linkedSet.getLast());

        System.out.println("【reversed 是视图，不是拷贝】");
        var view = list.reversed();
        System.out.println("  视图：" + view);
        view.add("D");                       // 通过视图加在"最前"
        System.out.println("  原列表：" + list + "（视图修改双向可见）");
        view.removeLast();                   // 撤销，恢复原状

        System.out.println("【SequencedMap】");
        var map = new LinkedHashMap<String, Integer>();
        map.putFirst("last-added-first", 1);
        map.putLast("appended", 2);
        map.put("middle", 3);
        System.out.println("  顺序：" + map.keySet());
        System.out.println("  首个 entry：" + map.firstEntry());
        System.out.println("  反转视图：" + map.reversed().keySet());

        System.out.println("【不可变集合的现代默认】");
        var immutable = List.of("a", "b");
        try {
            immutable.add("c");
        } catch (UnsupportedOperationException e) {
            System.out.println("  List.of 不可变：add 抛 UnsupportedOperationException");
        }
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 把项目里所有 `get(0)` / `iterator().next()` / `peekFirst()` 统一替换为 `getFirst()`，记录替换数量与可读性收益。
 * 2. 实现一个"最近访问列表"（LRU 思想）：用 `LinkedHashSet` + `getFirst/removeLast` 实现，验证容量上限淘汰。
 * 3. 思考题：为什么 `reversed()` 设计成视图而不是拷贝？从内存成本与"双向一致性"的取舍分析，并说明何时必须显式拷贝。
 * ============================================================
 */
