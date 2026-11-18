/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：15-05
 * 课名：LinkedList与链表结构
 * ------------------------------------------------------------
 * 知识点：
 *   - `LinkedList` 底层是双向链表，每个节点保存"前一个节点地址 + 数据 + 后一个节点地址"。
 *   - 查询慢（要从头或从尾逐个找），增删快（只改相邻节点的指针，不移动数据）。
 *   - 特有方法：`addFirst`/`addLast`、`getFirst`/`getLast`、`removeFirst`/`removeLast`，可以当栈或队列使用。
 *   - `LinkedList` 实现了 `Deque` 接口，所以 `push`/`pop`/`offer`/`poll` 也能用。
 *   - 选择建议：查询多用 `ArrayList`，频繁在头尾增删用 `LinkedList`；实际开发中 `ArrayList` 使用频率更高。
 *   - 易错：对 `LinkedList` 用普通 for 加 `get(i)` 遍历，时间复杂度会退化成 O(n²)。
 * ============================================================
 */

package com.heima.ch15;

import java.util.LinkedList;

// ==================== 示例代码 ====================

public class LinkedListDemo {
    public static void main(String[] args) {
        LinkedList<String> list = new LinkedList<>();
        list.add("B");
        list.addFirst("A");   // 头部添加
        list.addLast("D");    // 尾部添加
        list.add(2, "C");
        System.out.println("链表内容：" + list); // [A, B, C, D]

        System.out.println("第一个：" + list.getFirst()); // A
        System.out.println("最后一个：" + list.getLast()); // D
        System.out.println("删除第一个：" + list.removeFirst()); // A
        System.out.println("剩余：" + list);  // [B, C, D]

        // 当栈用：后进先出
        LinkedList<Integer> stack = new LinkedList<>();
        stack.push(1);
        stack.push(2);
        stack.push(3);
        System.out.println("出栈：" + stack.pop()); // 3
        System.out.println("栈内：" + stack);       // [2, 1]

        // 当队列用：先进先出
        LinkedList<String> queue = new LinkedList<>();
        queue.offer("第一个");
        queue.offer("第二个");
        queue.offer("第三个");
        System.out.println("出队：" + queue.poll()); // 第一个
        System.out.println("队列剩余：" + queue);     // [第二个, 第三个]
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 用 `LinkedList` 模拟一个浏览器的"后退"功能：访问 4 个页面后连续后退 2 次，输出当前页面。
 * 2. 用 `LinkedList` 实现一个简单的括号匹配检查器，判断字符串中的 `()`、`[]`、`{}` 是否成对出现。
 * 3. 分别用 `ArrayList` 和 `LinkedList` 在头部插入 10000 个元素，用 `System.currentTimeMillis()` 记录耗时并比较。
 * ============================================================
 */
