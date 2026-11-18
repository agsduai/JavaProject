/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：34-03
 * 课名：链表：反转、中点、环检测与合并
 * ------------------------------------------------------------
 * 知识点：
 *   - **链表 vs 数组**：链表插入删除 `O(1)`（前提是已持有前驱节点），随机访问 `O(n)`；数组相反。链表节点分散在堆上，**缓存局部性差**，所以即使复杂度更优，小数据量下常常跑不过数组。
 *   - **哨兵节点（dummy head）**能消除"头节点特判"，让插入删除逻辑统一。凡是链表题，先加哨兵通常能少一半分支。
 *   - **反转链表**用三指针：`prev / cur / next`，每步先存 `next` 再改 `cur.next = prev`，最后返回 `prev`。写错的典型是改完指针才去取 `next`，链断了。
 *   - **快慢指针**是链表的核心技巧：`slow` 走 1 步、`fast` 走 2 步。快指针到尾部时慢指针在中点；如果两者相遇说明有环。
 *   - **Floyd 判环**分两步：先让快慢指针相遇，再把一个指针放回头部、两者同速前进，再次相遇点就是**环入口**。原理：头到入口距离 = 相遇点到入口距离（绕环整数圈）。
 *   - **合并两个有序链表**：比较头节点、摘下较小的接到结果尾部，最后把没走完的那条整段接上。时间 `O(m+n)`、空间 `O(1)`。
 *   - **双向链表**多一个 `prev`，删除节点不需要前驱（`node.prev` 直接可用），这是 `LinkedList` 和 LRU 的基础。代价是每个节点多一个引用。
 *   - **删除倒数第 k 个**：快指针先走 k 步，再快慢同步走，快指针到尾时慢指针指向待删节点的前驱。
 *   - 易错：遍历时 `cur = cur.next` 写在修改 `cur.next` 之前（丢链）；空链表 / 单节点没做边界；快慢指针 `fast.next.next` 没判空导致 NPE；返回了 `head` 而不是新的头。
 * ============================================================
 */

package com.heima.ch34;

// ==================== 示例代码 ====================

public class LinkedListDemo {
    /** 单链表节点 */
    static final class Node {
        final int val;
        Node next;

        Node(int val) {
            this.val = val;
        }
    }

    /** 双向链表节点 */
    static final class DNode {
        final int val;
        DNode prev;
        DNode next;

        DNode(int val) {
            this.val = val;
        }
    }

    static Node build(int... values) {
        Node dummy = new Node(0);
        Node tail = dummy;
        for (int v : values) {
            tail.next = new Node(v);
            tail = tail.next;
        }
        return dummy.next;
    }

    static String print(Node head) {
        StringBuilder sb = new StringBuilder();
        for (Node p = head; p != null; p = p.next) {
            if (sb.length() > 0) {
                sb.append(" -> ");
            }
            sb.append(p.val);
        }
        return sb.length() == 0 ? "(空)" : sb.toString();
    }

    /** 三指针反转 */
    static Node reverse(Node head) {
        Node prev = null;
        Node cur = head;
        while (cur != null) {
            Node next = cur.next;
            cur.next = prev;
            prev = cur;
            cur = next;
        }
        return prev;
    }

    /** 快慢指针找中点；偶数个节点时返回靠左的那个 */
    static Node middle(Node head) {
        Node slow = head;
        Node fast = head;
        while (fast != null && fast.next != null && fast.next.next != null) {
            slow = slow.next;
            fast = fast.next.next;
        }
        return slow;
    }

    /** Floyd 判环 + 求环入口 */
    static Node cycleEntry(Node head) {
        Node slow = head;
        Node fast = head;
        while (fast != null && fast.next != null) {
            slow = slow.next;
            fast = fast.next.next;
            if (slow == fast) {
                Node p = head;
                while (p != slow) {
                    p = p.next;
                    slow = slow.next;
                }
                return p;
            }
        }
        return null;
    }

    /** 合并两条有序链表，复用原节点 */
    static Node merge(Node a, Node b) {
        Node dummy = new Node(0);
        Node tail = dummy;
        while (a != null && b != null) {
            if (a.val <= b.val) {
                tail.next = a;
                a = a.next;
            } else {
                tail.next = b;
                b = b.next;
            }
            tail = tail.next;
        }
        tail.next = a != null ? a : b;
        return dummy.next;
    }

    /** 删除倒数第 k 个节点（k 从 1 开始） */
    static Node removeNthFromEnd(Node head, int k) {
        Node dummy = new Node(0);
        dummy.next = head;
        Node fast = dummy;
        Node slow = dummy;
        for (int i = 0; i < k; i++) {
            if (fast.next == null) {
                return head;
            }
            fast = fast.next;
        }
        while (fast.next != null) {
            fast = fast.next;
            slow = slow.next;
        }
        slow.next = slow.next.next;
        return dummy.next;
    }

    /** 双向链表：尾部 O(1) 插入、已知节点 O(1) 删除 */
    static final class DoublyList {
        private final DNode head = new DNode(0);
        private final DNode tail = new DNode(0);
        private int size;

        DoublyList() {
            head.next = tail;
            tail.prev = head;
        }

        void addLast(int v) {
            DNode node = new DNode(v);
            node.prev = tail.prev;
            node.next = tail;
            tail.prev.next = node;
            tail.prev = node;
            size++;
        }

        void remove(DNode node) {
            node.prev.next = node.next;
            node.next.prev = node.prev;
            size--;
        }

        DNode nodeAt(int index) {
            DNode p = head.next;
            for (int i = 0; i < index; i++) {
                p = p.next;
            }
            return p;
        }

        String forward() {
            StringBuilder sb = new StringBuilder();
            for (DNode p = head.next; p != tail; p = p.next) {
                if (sb.length() > 0) {
                    sb.append(" <-> ");
                }
                sb.append(p.val);
            }
            return sb.toString();
        }

        String backward() {
            StringBuilder sb = new StringBuilder();
            for (DNode p = tail.prev; p != head; p = p.prev) {
                if (sb.length() > 0) {
                    sb.append(" <-> ");
                }
                sb.append(p.val);
            }
            return sb.toString();
        }

        int size() {
            return size;
        }
    }

    public static void main(String[] args) {
        System.out.println("=== 1. 反转链表 ===");
        Node head = build(1, 2, 3, 4, 5);
        System.out.println("  原链表: " + print(head));
        System.out.println("  反转后: " + print(reverse(head)));

        System.out.println();
        System.out.println("=== 2. 快慢指针找中点 ===");
        System.out.println("  长度 5: 中点 = " + middle(build(1, 2, 3, 4, 5)).val);
        System.out.println("  长度 6: 中点 = " + middle(build(1, 2, 3, 4, 5, 6)).val + "（靠左）");

        System.out.println();
        System.out.println("=== 3. Floyd 判环与找环入口 ===");
        Node acyclic = build(1, 2, 3, 4);
        System.out.println("  无环链表 entry = " + cycleEntry(acyclic));

        Node c1 = new Node(10);
        Node c2 = new Node(20);
        Node c3 = new Node(30);
        Node c4 = new Node(40);
        Node c5 = new Node(50);
        c1.next = c2;
        c2.next = c3;
        c3.next = c4;
        c4.next = c5;
        c5.next = c3;
        Node entry = cycleEntry(c1);
        System.out.println("  有环链表 entry.val = " + (entry == null ? "null" : entry.val) + "（期望 30）");

        System.out.println();
        System.out.println("=== 4. 合并两条有序链表 ===");
        Node m1 = build(1, 3, 5, 7);
        Node m2 = build(2, 4, 6, 8, 9);
        System.out.println("  A = " + print(m1));
        System.out.println("  B = " + print(m2));
        System.out.println("  合并 = " + print(merge(m1, m2)));

        System.out.println();
        System.out.println("=== 5. 删除倒数第 k 个节点 ===");
        System.out.println("  删倒数第 2 个: " + print(removeNthFromEnd(build(1, 2, 3, 4, 5), 2)));
        System.out.println("  删倒数第 1 个: " + print(removeNthFromEnd(build(1, 2, 3, 4, 5), 1)));
        System.out.println("  删倒数第 5 个: " + print(removeNthFromEnd(build(1, 2, 3, 4, 5), 5)));
        System.out.println("  k 超长时原样返回: " + print(removeNthFromEnd(build(1, 2), 9)));

        System.out.println();
        System.out.println("=== 6. 双向链表 ===");
        DoublyList dl = new DoublyList();
        for (int i = 1; i <= 5; i++) {
            dl.addLast(i * 11);
        }
        System.out.println("  正向: " + dl.forward());
        System.out.println("  反向: " + dl.backward());
        DNode target = dl.nodeAt(2);
        dl.remove(target);
        System.out.println("  删除第 3 个节点(" + target.val + ")后: " + dl.forward()
                + "，size=" + dl.size());
        System.out.println("  已知节点删除是 O(1)，因为不需要从头找前驱。");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现"每 k 个节点一组反转链表"（`1->2->3->4->5`，k=2 → `2->1->4->3->5`），要求空间 `O(1)`。
 * 2. 判断两条单链表是否相交，若相交返回第一个公共节点。要求时间 `O(m+n)`、空间 `O(1)`（提示：先求长度差）。
 * 3. 实现双向链表的 `addFirst`、`removeFirst`、`removeLast`，并用 10000 次随机操作验证 `size` 与实际节点数一致。
 * 4. 用链表实现"两数相加"：`(2->4->3) + (5->6->4)` 表示 `342 + 465 = 807`，输出 `7->0->8`。
 * ============================================================
 */
