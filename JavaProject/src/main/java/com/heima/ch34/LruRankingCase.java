/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：34-11
 * 课名：综合案例：LRU 缓存与实时排行榜
 * ------------------------------------------------------------
 * 知识点：
 *   - **LRU（Least Recently Used）** 的语义：容量满时淘汰"最久未被访问"的条目。`get` 和 `put` 都必须 `O(1)`，这要求两个结构配合：**哈希表**负责 `O(1)` 定位，**双向链表**负责 `O(1)` 调整顺序。
 *   - 链表顺序约定：**头部是最近使用**，**尾部是最久未使用**。`get` 命中时把节点移到头部；`put` 新节点插头部；容量超了删尾部的 `prev`。
 *   - 用**哨兵头尾节点**能消除"链表为空""只有一个节点"的特判，所有插入删除逻辑统一。
 *   - **`LinkedHashMap` 自带 LRU**：构造时传 `accessOrder=true`，重写 `removeEldestEntry` 即可。手写版的价值在于理解原理，生产里优先用 `LinkedHashMap`。
 *   - **LFU（Least Frequently Used）** 按访问频次淘汰，频次相同再按最久未用。实现要用"频次 → 双向链表"的桶，且维护 `minFreq` 以便 `O(1)` 淘汰。
 *   - **排行榜**的需求是"实时更新分数 + 查 Top-N + 查某人排名"。用 `TreeMap<分数, 有序集合>` 反查排名，或者用**跳表**（Redis ZSet 的选择）。纯 JDK 里 `TreeSet` + `HashMap` 组合就能做到 `O(log n)` 更新与查询。
 *   - **分数相同怎么排**：必须定义**次级排序键**（如先到先得、用户 ID 字典序），否则同分用户排名不稳定，前端会看到排名乱跳。
 *   - **排名计算**：`TreeSet.headSet(score, false).size()` 是 `O(n)`，不能用在热路径。正确做法是让每个节点维护**子树大小**（跳表/平衡树），或者接受 `O(log n)` 的 `TreeMap` 遍历计数。
 *   - **缓存三大指标**：命中率、淘汰次数、平均访问延迟。案例里要真统计并打印，否则"实现了 LRU"只是自说自话。
 *   - 易错：`get` 命中后忘记移到头部（退化成 FIFO）；`put` 已存在的 key 时没更新值只调整顺序；淘汰时忘记从哈希表删除（内存泄漏）；排行榜同分不稳定；把 `LinkedHashMap` 的 `accessOrder` 忘了设。
 * ============================================================
 */

package com.heima.ch34;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

// ==================== 示例代码 ====================

public class LruRankingCase {
    /** 手写 LRU：HashMap + 双向链表（带哨兵） */
    static final class LruCache<K, V> {
        private static final class Node<K, V> {
            final K key;
            V value;
            Node<K, V> prev;
            Node<K, V> next;

            Node(K key, V value) {
                this.key = key;
                this.value = value;
            }
        }

        private final int capacity;
        private final Map<K, Node<K, V>> index = new HashMap<>();
        private final Node<K, V> head = new Node<>(null, null);
        private final Node<K, V> tail = new Node<>(null, null);
        private long hits;
        private long misses;
        private long evictions;

        LruCache(int capacity) {
            if (capacity <= 0) {
                throw new IllegalArgumentException("容量必须为正: " + capacity);
            }
            this.capacity = capacity;
            head.next = tail;
            tail.prev = head;
        }

        V get(K key) {
            Node<K, V> node = index.get(key);
            if (node == null) {
                misses++;
                return null;
            }
            hits++;
            moveToFront(node);
            return node.value;
        }

        void put(K key, V value) {
            Node<K, V> node = index.get(key);
            if (node != null) {
                node.value = value;
                moveToFront(node);
                return;
            }
            if (index.size() == capacity) {
                Node<K, V> victim = tail.prev;
                unlink(victim);
                index.remove(victim.key);
                evictions++;
            }
            Node<K, V> fresh = new Node<>(key, value);
            index.put(key, fresh);
            linkFirst(fresh);
        }

        private void moveToFront(Node<K, V> node) {
            unlink(node);
            linkFirst(node);
        }

        private void unlink(Node<K, V> node) {
            node.prev.next = node.next;
            node.next.prev = node.prev;
        }

        private void linkFirst(Node<K, V> node) {
            node.next = head.next;
            node.prev = head;
            head.next.prev = node;
            head.next = node;
        }

        List<K> keysInOrder() {
            List<K> out = new ArrayList<>();
            for (Node<K, V> p = head.next; p != tail; p = p.next) {
                out.add(p.key);
            }
            return out;
        }

        double hitRate() {
            long total = hits + misses;
            return total == 0 ? 0 : hits / (double) total;
        }

        long hits() {
            return hits;
        }

        long misses() {
            return misses;
        }

        long evictions() {
            return evictions;
        }
    }

    /** LFU：频次桶 + 最小频次指针 */
    static final class LfuCache<K, V> {
        private static final class Node<K, V> {
            final K key;
            V value;
            int freq = 1;
            Node<K, V> prev;
            Node<K, V> next;

            Node(K key, V value) {
                this.key = key;
                this.value = value;
            }
        }

        /** 频次桶：内部是双向链表，头部最新 */
        private static final class Bucket<K, V> {
            final Node<K, V> head = new Node<>(null, null);
            final Node<K, V> tail = new Node<>(null, null);

            Bucket() {
                head.next = tail;
                tail.prev = head;
            }

            void addFirst(Node<K, V> n) {
                n.next = head.next;
                n.prev = head;
                head.next.prev = n;
                head.next = n;
            }

            void remove(Node<K, V> n) {
                n.prev.next = n.next;
                n.next.prev = n.prev;
            }

            boolean isEmpty() {
                return head.next == tail;
            }
        }

        private final int capacity;
        private final Map<K, Node<K, V>> index = new HashMap<>();
        private final Map<Integer, Bucket<K, V>> buckets = new HashMap<>();
        private int minFreq;
        private long evictions;

        LfuCache(int capacity) {
            this.capacity = capacity;
        }

        V get(K key) {
            Node<K, V> node = index.get(key);
            if (node == null) {
                return null;
            }
            touch(node);
            return node.value;
        }

        void put(K key, V value) {
            if (capacity <= 0) {
                return;
            }
            Node<K, V> node = index.get(key);
            if (node != null) {
                node.value = value;
                touch(node);
                return;
            }
            if (index.size() == capacity) {
                Bucket<K, V> bucket = buckets.get(minFreq);
                Node<K, V> victim = bucket.tail.prev;
                bucket.remove(victim);
                if (bucket.isEmpty()) {
                    buckets.remove(minFreq);
                }
                index.remove(victim.key);
                evictions++;
            }
            Node<K, V> fresh = new Node<>(key, value);
            index.put(key, fresh);
            buckets.computeIfAbsent(1, k -> new Bucket<>()).addFirst(fresh);
            minFreq = 1;
        }

        private void touch(Node<K, V> node) {
            int oldFreq = node.freq;
            Bucket<K, V> oldBucket = buckets.get(oldFreq);
            oldBucket.remove(node);
            if (oldBucket.isEmpty()) {
                buckets.remove(oldFreq);
                if (minFreq == oldFreq) {
                    minFreq = oldFreq + 1;
                }
            }
            node.freq = oldFreq + 1;
            buckets.computeIfAbsent(node.freq, k -> new Bucket<>()).addFirst(node);
        }

        long evictions() {
            return evictions;
        }

        int minFreq() {
            return minFreq;
        }
    }

    /** 排行榜：分数 -> 用户集合（同分按先到先得序号稳定排序） */
    static final class Leaderboard {
        private static final class Entry implements Comparable<Entry> {
            final String user;
            final int score;
            final long seq;

            Entry(String user, int score, long seq) {
                this.user = user;
                this.score = score;
                this.seq = seq;
            }

            @Override
            public int compareTo(Entry o) {
                // 分数高在前；同分时 seq 小（先到）在前
                int c = Integer.compare(o.score, score);
                return c != 0 ? c : Long.compare(seq, o.seq);
            }
        }

        private final TreeSet<Entry> board = new TreeSet<>();
        private final Map<String, Entry> byUser = new HashMap<>();
        private long seq;

        void submit(String user, int score) {
            Entry old = byUser.remove(user);
            if (old != null) {
                board.remove(old);
            }
            Entry fresh = new Entry(user, score, seq++);
            board.add(fresh);
            byUser.put(user, fresh);
        }

        List<String> topN(int n) {
            List<String> out = new ArrayList<>();
            int i = 0;
            for (Entry e : board) {
                if (i++ >= n) {
                    break;
                }
                out.add((i) + ". " + e.user + " = " + e.score);
            }
            return out;
        }

        /** 排名从 1 开始；不在榜返回 -1 */
        int rankOf(String user) {
            Entry e = byUser.get(user);
            if (e == null) {
                return -1;
            }
            return board.headSet(e, false).size() + 1;
        }

        /** 分数区间内的人数：[low, high] */
        int countInRange(int low, int high) {
            Entry from = new Entry("", high, Long.MIN_VALUE);
            Entry to = new Entry("", low - 1, Long.MIN_VALUE);
            return board.subSet(from, true, to, false).size();
        }

        int size() {
            return board.size();
        }
    }

    /** 用 LinkedHashMap 三行实现 LRU，作为对照 */
    static final class SimpleLru<K, V> extends LinkedHashMap<K, V> {
        private static final long serialVersionUID = 1L;
        private final int capacity;

        SimpleLru(int capacity) {
            super(16, 0.75f, true);
            this.capacity = capacity;
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
            return size() > capacity;
        }
    }

    public static void main(String[] args) {
        System.out.println("=== 1. 手写 LRU：容量 3 ===");
        LruCache<String, Integer> lru = new LruCache<>(3);
        lru.put("A", 1);
        lru.put("B", 2);
        lru.put("C", 3);
        System.out.println("  放入 A,B,C 后（头部最新）: " + lru.keysInOrder());
        System.out.println("  get(A) = " + lru.get("A") + "，A 被提到头部: " + lru.keysInOrder());
        lru.put("D", 4);
        System.out.println("  放入 D 触发淘汰（尾部 B 被踢）: " + lru.keysInOrder());
        System.out.println("  get(B) = " + lru.get("B") + "（已淘汰）");
        lru.put("A", 100);
        System.out.println("  更新 A=100 后: " + lru.keysInOrder() + "，get(A)=" + lru.get("A"));

        System.out.println();
        System.out.println("=== 2. 命中率统计：访问序列 A B A C A B D ===");
        LruCache<String, Integer> measured = new LruCache<>(2);
        String[] trace = {"A", "B", "A", "C", "A", "B", "D"};
        for (String k : trace) {
            Integer v = measured.get(k);
            if (v == null) {
                measured.put(k, 1);
                System.out.println("  get(" + k + ") 未命中 -> 回源加载并放入缓存");
            } else {
                System.out.println("  get(" + k + ") 命中，值=" + v);
            }
        }
        System.out.printf("  命中=%d 未命中=%d 淘汰=%d 命中率=%.1f%%%n",
                measured.hits(), measured.misses(), measured.evictions(), measured.hitRate() * 100);
        System.out.println("  缓存不是「接上就快」，命中率才是关键指标。");

        System.out.println();
        System.out.println("=== 3. LFU 对比：容量 2，访问 A A B C A ===");
        LfuCache<String, Integer> lfu = new LfuCache<>(2);
        lfu.put("A", 1);
        lfu.put("B", 2);
        System.out.println("  get(A) = " + lfu.get("A") + "（A 频次变 2）");
        System.out.println("  get(A) = " + lfu.get("A") + "（A 频次变 3）");
        lfu.put("C", 3);
        System.out.println("  放入 C 触发淘汰：B 频次 1 最低，被淘汰");
        System.out.println("  get(B) = " + lfu.get("B") + "（已淘汰），get(A) = " + lfu.get("A")
                + "，get(C) = " + lfu.get("C"));
        System.out.println("  LFU 保护高频条目，但对「曾经很热、现在不再访问」的条目不友好。");

        System.out.println();
        System.out.println("=== 4. 排行榜：提交成绩并查 Top-N ===");
        Leaderboard board = new Leaderboard();
        board.submit("小明", 880);
        board.submit("小红", 950);
        board.submit("小刚", 880);
        board.submit("小丽", 720);
        board.submit("小强", 990);
        System.out.println("  Top 5:");
        for (String line : board.topN(5)) {
            System.out.println("    " + line);
        }
        System.out.println("  小明(880) 排名 = " + board.rankOf("小明"));
        System.out.println("  小刚(880) 排名 = " + board.rankOf("小刚") + "（同分时先提交的排前面）");
        System.out.println("  不在榜用户排名 = " + board.rankOf("路人甲"));

        System.out.println();
        System.out.println("=== 5. 成绩更新与区间统计 ===");
        board.submit("小丽", 980);
        System.out.println("  小丽刷到 980 后排名 = " + board.rankOf("小丽"));
        System.out.println("  Top 3 变为:");
        for (String line : board.topN(3)) {
            System.out.println("    " + line);
        }
        System.out.println("  900~990 分段人数 = " + board.countInRange(900, 990));
        System.out.println("  总人数 = " + board.size() + "（重复提交不会增加人数）");

        System.out.println();
        System.out.println("=== 6. LinkedHashMap 版 LRU（对照）===");
        SimpleLru<String, Integer> simple = new SimpleLru<>(2);
        simple.put("x", 1);
        simple.put("y", 2);
        simple.get("x");
        simple.put("z", 3);
        System.out.println("  容量 2，放入 x,y，访问 x，再放 z -> 剩余 key = " + simple.keySet());
        System.out.println("  accessOrder=true 时 get 也会改变顺序，这是 LRU 的关键开关。");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 把 `LruCache` 改造成线程安全版本（用 `ReentrantLock` 保护），并写一个 8 线程并发读写的测试，验证容量始终不超限、无死锁。
 * 2. 实现带过期时间的 LRU（TTL）：`get` 时检查是否过期，过期视为未命中并删除；说明为什么惰性删除不够、还需要什么机制。
 * 3. 给 `Leaderboard` 增加"分页查询第 N 页（每页 10 条）"，要求不遍历整个榜单；再说明数据量到 100 万时该换成什么结构。
 * 4. 对比 LRU 与 LFU 在同一访问序列下的命中率：构造一个"扫表"序列（大量一次性 key）让 LRU 命中率骤降，说明为什么真实缓存常用 LRU-K 或 W-TinyLFU。
 * ============================================================
 */
