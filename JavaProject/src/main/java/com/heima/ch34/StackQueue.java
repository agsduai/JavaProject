/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：34-04
 * 课名：栈、队列与单调栈/单调队列
 * ------------------------------------------------------------
 * 知识点：
 *   - **栈**（LIFO）的两个动作只有 `push` / `pop`，天然适合"最近相关性"问题：括号匹配、表达式求值、函数调用、撤销操作、DFS。
 *   - **队列**（FIFO）适合"按到达顺序处理"：BFS、任务排队、消息缓冲。`ArrayDeque` 同时能当栈和队列用，比 `Stack` 类和 `LinkedList` 都快（`Stack` 是同步的，`LinkedList` 每节点有额外开销）。
 *   - **用数组实现栈**要注意 `top` 的含义：指向"下一个空位"还是"栈顶元素"。选一种并全程一致，否则 `push`/`pop` 的边界会错一位。
 *   - **用两个栈实现队列**：`in` 栈负责入队，`out` 栈负责出队；`out` 空时把 `in` 全部倒入 `out`。每个元素最多被搬两次，均摊 `O(1)`。
 *   - **单调栈**解决"下一个更大/更小元素"：维护一个**从栈底到栈顶单调递减**的栈，新元素入栈前把比它小的全部弹出，被弹出的元素就找到了自己的"下一个更大值"。
 *   - 单调栈的复杂度是 `O(n)` 而不是 `O(n²)`，因为**每个元素最多入栈一次、出栈一次**，总操作次数 `2n`。这是"用栈把嵌套循环摊平"的典型。
 *   - **单调队列**解决"滑动窗口最值"：队列里存下标，保持对应值单调递减；窗口右移时队首过期就弹出，新元素入队前把比它小的弹出。队首永远是窗口最大值。
 *   - **循环队列**用 `(head + 1) % capacity` 推进，用 `size` 而不是"head == tail"区分空和满（否则空满条件相同）。
 *   - 易错：`ArrayDeque` 不允许 `null`（`push(null)` 抛 NPE）；`Stack` 的 `peek()` 空栈抛 `EmptyStackException`；单调栈存值还是存下标没想清楚（需要算距离时必须存下标）；窗口题忘记判断队首是否已经滑出窗口。
 * ============================================================
 */

package com.heima.ch34;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;

// ==================== 示例代码 ====================

public class StackQueue {
    /** 数组栈：top 指向下一个空位 */
    static final class ArrayStack {
        private int[] data = new int[4];
        private int top;

        void push(int v) {
            if (top == data.length) {
                data = Arrays.copyOf(data, data.length * 2);
            }
            data[top++] = v;
        }

        int pop() {
            if (top == 0) {
                throw new IllegalStateException("栈为空");
            }
            return data[--top];
        }

        int peek() {
            if (top == 0) {
                throw new IllegalStateException("栈为空");
            }
            return data[top - 1];
        }

        boolean isEmpty() {
            return top == 0;
        }

        int size() {
            return top;
        }
    }

    /** 链式队列：head 出队、tail 入队 */
    static final class LinkedQueue {
        private static final class QNode {
            final int val;
            QNode next;

            QNode(int val) {
                this.val = val;
            }
        }

        private QNode head;
        private QNode tail;
        private int size;

        void offer(int v) {
            QNode node = new QNode(v);
            if (tail == null) {
                head = node;
                tail = node;
            } else {
                tail.next = node;
                tail = node;
            }
            size++;
        }

        int poll() {
            if (head == null) {
                throw new IllegalStateException("队列为空");
            }
            int v = head.val;
            head = head.next;
            if (head == null) {
                tail = null;
            }
            size--;
            return v;
        }

        boolean isEmpty() {
            return head == null;
        }

        int size() {
            return size;
        }
    }

    /** 两个栈实现队列 */
    static final class TwoStackQueue {
        private final Deque<Integer> in = new ArrayDeque<>();
        private final Deque<Integer> out = new ArrayDeque<>();

        void offer(int v) {
            in.push(v);
        }

        int poll() {
            if (out.isEmpty()) {
                while (!in.isEmpty()) {
                    out.push(in.pop());
                }
            }
            if (out.isEmpty()) {
                throw new IllegalStateException("队列为空");
            }
            return out.pop();
        }
    }

    /** 单调栈：每个元素右边第一个更大的值，没有则 -1 */
    static int[] nextGreater(int[] nums) {
        int[] ans = new int[nums.length];
        Arrays.fill(ans, -1);
        Deque<Integer> stack = new ArrayDeque<>();
        for (int i = 0; i < nums.length; i++) {
            while (!stack.isEmpty() && nums[stack.peek()] < nums[i]) {
                ans[stack.pop()] = nums[i];
            }
            stack.push(i);
        }
        return ans;
    }

    /** 单调队列：滑动窗口最大值 */
    static int[] maxSlidingWindow(int[] nums, int k) {
        if (nums.length == 0 || k <= 0) {
            return new int[0];
        }
        int[] ans = new int[nums.length - k + 1];
        Deque<Integer> window = new ArrayDeque<>();
        for (int i = 0; i < nums.length; i++) {
            while (!window.isEmpty() && window.peekFirst() <= i - k) {
                window.pollFirst();
            }
            while (!window.isEmpty() && nums[window.peekLast()] <= nums[i]) {
                window.pollLast();
            }
            window.offerLast(i);
            if (i >= k - 1) {
                ans[i - k + 1] = nums[window.peekFirst()];
            }
        }
        return ans;
    }

    /** 括号匹配 */
    static boolean validParentheses(String s) {
        Deque<Character> stack = new ArrayDeque<>();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '(':
                case '[':
                case '{':
                    stack.push(c);
                    break;
                case ')':
                    if (stack.isEmpty() || stack.pop() != '(') {
                        return false;
                    }
                    break;
                case ']':
                    if (stack.isEmpty() || stack.pop() != '[') {
                        return false;
                    }
                    break;
                case '}':
                    if (stack.isEmpty() || stack.pop() != '{') {
                        return false;
                    }
                    break;
                default:
                    break;
            }
        }
        return stack.isEmpty();
    }

    /** 循环队列：用 size 区分空与满 */
    static final class CircularQueue {
        private final int[] data;
        private int head;
        private int size;

        CircularQueue(int capacity) {
            data = new int[capacity];
        }

        boolean offer(int v) {
            if (size == data.length) {
                return false;
            }
            data[(head + size) % data.length] = v;
            size++;
            return true;
        }

        int poll() {
            if (size == 0) {
                throw new IllegalStateException("队列为空");
            }
            int v = data[head];
            head = (head + 1) % data.length;
            size--;
            return v;
        }

        int capacity() {
            return data.length;
        }

        int size() {
            return size;
        }
    }

    public static void main(String[] args) {
        System.out.println("=== 1. 数组栈 ===");
        ArrayStack stack = new ArrayStack();
        for (int i = 1; i <= 5; i++) {
            stack.push(i * i);
        }
        System.out.println("  栈大小=" + stack.size() + "，peek=" + stack.peek());
        StringBuilder popped = new StringBuilder();
        while (!stack.isEmpty()) {
            popped.append(stack.pop()).append(' ');
        }
        System.out.println("  出栈顺序（LIFO）: " + popped.toString().trim());

        System.out.println();
        System.out.println("=== 2. 链式队列 ===");
        LinkedQueue queue = new LinkedQueue();
        for (int i = 1; i <= 5; i++) {
            queue.offer(i * 100);
        }
        StringBuilder polled = new StringBuilder();
        while (!queue.isEmpty()) {
            polled.append(queue.poll()).append(' ');
        }
        System.out.println("  出队顺序（FIFO）: " + polled.toString().trim());

        System.out.println();
        System.out.println("=== 3. 两个栈实现队列 ===");
        TwoStackQueue tsq = new TwoStackQueue();
        tsq.offer(1);
        tsq.offer(2);
        tsq.offer(3);
        System.out.println("  连续入队 1,2,3 后出队: " + tsq.poll() + " " + tsq.poll());
        tsq.offer(4);
        System.out.println("  再入队 4 后出队: " + tsq.poll() + " " + tsq.poll());

        System.out.println();
        System.out.println("=== 4. 单调栈：下一个更大元素 ===");
        int[] nums = {2, 1, 2, 4, 3};
        System.out.println("  输入: " + Arrays.toString(nums));
        System.out.println("  输出: " + Arrays.toString(nextGreater(nums)));
        System.out.println("  暴力解法是 O(n²)，单调栈是 O(n)：每个元素最多进出栈各一次。");

        System.out.println();
        System.out.println("=== 5. 单调队列：滑动窗口最大值 ===");
        int[] win = {1, 3, -1, -3, 5, 3, 6, 7};
        for (int k = 1; k <= 4; k++) {
            System.out.println("  k=" + k + " -> " + Arrays.toString(maxSlidingWindow(win, k)));
        }

        System.out.println();
        System.out.println("=== 6. 括号匹配 ===");
        List<String> cases = new ArrayList<>(List.of(
                "()", "()[]{}", "(]", "([)]", "{[]}", "", "(((", "a(b)c[d]"));
        for (String c : cases) {
            System.out.printf("  %-10s -> %s%n", "\"" + c + "\"", validParentheses(c));
        }

        System.out.println();
        System.out.println("=== 7. 循环队列 ===");
        CircularQueue cq = new CircularQueue(3);
        System.out.println("  offer(1)=" + cq.offer(1) + " offer(2)=" + cq.offer(2)
                + " offer(3)=" + cq.offer(3));
        System.out.println("  满了再 offer(4)=" + cq.offer(4) + "（容量 3）");
        System.out.println("  poll=" + cq.poll() + "，再 offer(4)=" + cq.offer(4) + "，size=" + cq.size());
        System.out.println("  继续出队: " + cq.poll() + " " + cq.poll() + " " + cq.poll());
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 用单调栈求"每个元素左右两边第一个比它小的值"，返回两个数组，要求整体 `O(n)`。
 * 2. 实现 `MinStack`，支持 `push`/`pop`/`top`/`getMin`，全部 `O(1)`（提示：辅助栈存"当前最小值的历史"）。
 * 3. 用栈实现逆波兰表达式求值：输入 `["2","1","+","3","*"]` 输出 `9`，并处理除零与非法表达式。
 * 4. 实现"每日温度"：给定气温数组，返回每天需要等待几天才会升温，没有更暖的一天则填 0（例：`[73,74,75,71,69,72,76,73]` → `[1,1,4,2,1,1,0,0]`）。
 * ============================================================
 */
