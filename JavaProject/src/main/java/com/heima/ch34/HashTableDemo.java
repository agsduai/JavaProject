/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：34-05
 * 课名：哈希表原理与冲突处理
 * ------------------------------------------------------------
 * 知识点：
 *   - 哈希表用"**数组 + 哈希函数 + 冲突解决**"把查找从 `O(n)` 降到平均 `O(1)`。核心是**把 key 映射到一个下标**，然后直接访问那个槽位。
 *   - **好的哈希函数**要求：计算快、分布均匀、对输入微小变化敏感。`hashCode()` 返回 int，还要经过扰动（`h ^ (h >>> 16)`）再取模，否则低位相同的 key 会全部撞在一起。
 *   - **取模用 `(n - 1) & hash` 而不是 `%`**：前提是容量为 2 的幂。位运算比取模快，且 `n-1` 的二进制全 1 能保留 hash 的低位。这是 JDK `HashMap` 容量必须是 2 的幂的原因。
 *   - **冲突解决两大流派**：**链地址法**（同槽位挂链表/红黑树，JDK 用法）和**开放寻址法**（线性探测/二次探测/双重哈希，冲突了就往后找空位）。链地址法删除简单、装载因子可以超过 1；开放寻址法缓存友好但删除要打"墓碑"标记。
 *   - **装载因子（load factor）** = 元素数 / 桶数。超过阈值（JDK 默认 0.75）就扩容。0.75 是"空间 vs 冲突"的经验平衡点：太高冲突多查询慢，太低浪费内存。
 *   - **JDK 8 的树化**：单桶链表长度 ≥ 8 且总容量 ≥ 64 时，链表转红黑树，最坏查询从 `O(n)` 降到 `O(log n)`。长度降到 6 再退回链表。
 *   - **扩容（resize）**要重新分配所有元素。JDK 8 利用"容量是 2 的幂"这个性质，把每个元素分成"低位链"和"高位链"两条，不需要重新计算 hash，只判断 `(hash & oldCap) == 0`。
 *   - **`equals` 与 `hashCode` 的契约**：`equals` 相等则 `hashCode` 必须相等；`hashCode` 相等 `equals` 不一定相等。重写 `equals` 必须重写 `hashCode`，否则放进 `HashSet` 就找不回来了。
 *   - **可变对象做 key 是灾难**：对象进桶后修改了参与 `hashCode` 的字段，就再也定位不到。key 要用不可变类型（`String`、`Integer`、`record`）。
 *   - 易错：`hashCode` 用可变字段；只重写 `equals`；用 `%` 取模但容量不是 2 的幂；以为 `HashMap` 线程安全（并发扩容会丢数据甚至死循环，JDK 8 已修死循环但仍不安全）。
 * ============================================================
 */

package com.heima.ch34;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

// ==================== 示例代码 ====================

public class HashTableDemo {
    /** 链地址法哈希表：容量 2 的幂 + 扰动 + 树化阈值统计 */
    static final class ChainedMap<K, V> {
        private static final int DEFAULT_CAP = 16;
        private static final float LOAD_FACTOR = 0.75f;
        private static final int TREEIFY_THRESHOLD = 8;

        static final class Entry<K, V> {
            final int hash;
            final K key;
            V value;
            Entry<K, V> next;

            Entry(int hash, K key, V value, Entry<K, V> next) {
                this.hash = hash;
                this.key = key;
                this.value = value;
                this.next = next;
            }
        }

        private Entry<K, V>[] table;
        private int size;
        private int threshold;
        private int resizeCount;
        private int maxChain;

        @SuppressWarnings({"unchecked", "rawtypes"})
        ChainedMap() {
            table = (Entry<K, V>[]) new Entry[DEFAULT_CAP];
            threshold = (int) (DEFAULT_CAP * LOAD_FACTOR);
        }

        static int spread(Object key) {
            int h;
            return key == null ? 0 : (h = key.hashCode()) ^ (h >>> 16);
        }

        int capacity() {
            return table.length;
        }

        int size() {
            return size;
        }

        int resizeCount() {
            return resizeCount;
        }

        int maxChain() {
            return maxChain;
        }

        V put(K key, V value) {
            int hash = spread(key);
            int index = (table.length - 1) & hash;
            for (Entry<K, V> e = table[index]; e != null; e = e.next) {
                if (e.hash == hash && Objects.equals(e.key, key)) {
                    V old = e.value;
                    e.value = value;
                    return old;
                }
            }
            table[index] = new Entry<>(hash, key, value, table[index]);
            size++;
            int chain = chainLength(index);
            if (chain > maxChain) {
                maxChain = chain;
            }
            if (chain >= TREEIFY_THRESHOLD) {
                System.out.println("    [提示] 桶 " + index + " 链表长度=" + chain
                        + "，真实 HashMap 在此会树化");
            }
            if (size > threshold) {
                resize();
            }
            return null;
        }

        V get(K key) {
            int hash = spread(key);
            int index = (table.length - 1) & hash;
            for (Entry<K, V> e = table[index]; e != null; e = e.next) {
                if (e.hash == hash && Objects.equals(e.key, key)) {
                    return e.value;
                }
            }
            return null;
        }

        V remove(K key) {
            int hash = spread(key);
            int index = (table.length - 1) & hash;
            Entry<K, V> prev = null;
            for (Entry<K, V> e = table[index]; e != null; prev = e, e = e.next) {
                if (e.hash == hash && Objects.equals(e.key, key)) {
                    if (prev == null) {
                        table[index] = e.next;
                    } else {
                        prev.next = e.next;
                    }
                    size--;
                    return e.value;
                }
            }
            return null;
        }

        private int chainLength(int index) {
            int n = 0;
            for (Entry<K, V> e = table[index]; e != null; e = e.next) {
                n++;
            }
            return n;
        }

        /** JDK 8 式扩容：按 (hash & oldCap) 拆成低位链和高位链，无需重算 hash */
        @SuppressWarnings({"unchecked", "rawtypes"})
        private void resize() {
            Entry<K, V>[] oldTable = table;
            int oldCap = oldTable.length;
            int newCap = oldCap << 1;
            Entry<K, V>[] newTable = (Entry<K, V>[]) new Entry[newCap];
            for (int i = 0; i < oldCap; i++) {
                Entry<K, V> loHead = null;
                Entry<K, V> loTail = null;
                Entry<K, V> hiHead = null;
                Entry<K, V> hiTail = null;
                for (Entry<K, V> e = oldTable[i]; e != null; e = e.next) {
                    if ((e.hash & oldCap) == 0) {
                        if (loTail == null) {
                            loHead = e;
                        } else {
                            loTail.next = e;
                        }
                        loTail = e;
                    } else {
                        if (hiTail == null) {
                            hiHead = e;
                        } else {
                            hiTail.next = e;
                        }
                        hiTail = e;
                    }
                }
                if (loTail != null) {
                    loTail.next = null;
                    newTable[i] = loHead;
                }
                if (hiTail != null) {
                    hiTail.next = null;
                    newTable[i + oldCap] = hiHead;
                }
            }
            table = newTable;
            threshold = (int) (newCap * LOAD_FACTOR);
            resizeCount++;
        }

        String buckets() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < table.length; i++) {
                int len = chainLength(i);
                if (len > 0) {
                    sb.append("    [" ).append(i).append("] ");
                    for (Entry<K, V> e = table[i]; e != null; e = e.next) {
                        sb.append(e.key).append("=").append(e.value).append(" -> ");
                    }
                    sb.append("null\n");
                }
            }
            return sb.toString();
        }
    }

    /** 开放寻址法（线性探测），演示删除必须打墓碑 */
    static final class OpenAddressMap {
        private static final Object TOMBSTONE = new Object();
        private Object[] keys;
        private Object[] values;
        private int size;
        private int probes;

        OpenAddressMap(int capacity) {
            keys = new Object[capacity];
            values = new Object[capacity];
        }

        int probes() {
            return probes;
        }

        void put(Object key, Object value) {
            int cap = keys.length;
            int index = Math.floorMod(key.hashCode(), cap);
            int firstTombstone = -1;
            for (int step = 0; step < cap; step++) {
                probes++;
                int i = (index + step) % cap;
                if (keys[i] == TOMBSTONE) {
                    if (firstTombstone < 0) {
                        firstTombstone = i;
                    }
                } else if (keys[i] == null) {
                    int target = firstTombstone >= 0 ? firstTombstone : i;
                    keys[target] = key;
                    values[target] = value;
                    size++;
                    return;
                } else if (keys[i].equals(key)) {
                    values[i] = value;
                    return;
                }
            }
            if (firstTombstone >= 0) {
                keys[firstTombstone] = key;
                values[firstTombstone] = value;
                size++;
                return;
            }
            throw new IllegalStateException("表已满");
        }

        Object get(Object key) {
            int cap = keys.length;
            int index = Math.floorMod(key.hashCode(), cap);
            for (int step = 0; step < cap; step++) {
                probes++;
                int i = (index + step) % cap;
                if (keys[i] == null) {
                    return null;
                }
                if (keys[i] != TOMBSTONE && keys[i].equals(key)) {
                    return values[i];
                }
            }
            return null;
        }

        boolean remove(Object key) {
            int cap = keys.length;
            int index = Math.floorMod(key.hashCode(), cap);
            for (int step = 0; step < cap; step++) {
                probes++;
                int i = (index + step) % cap;
                if (keys[i] == null) {
                    return false;
                }
                if (keys[i] != TOMBSTONE && keys[i].equals(key)) {
                    keys[i] = TOMBSTONE;
                    values[i] = null;
                    size--;
                    return true;
                }
            }
            return false;
        }
    }

    /** 故意写坏的 key：hashCode 恒定，全部落一个桶 */
    static final class BadKey {
        final String name;

        BadKey(String name) {
            this.name = name;
        }

        @Override
        public int hashCode() {
            return 42;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof BadKey && ((BadKey) o).name.equals(name);
        }

        @Override
        public String toString() {
            return name;
        }
    }

    /** 参与 hashCode 的可变字段 —— 反面教材 */
    static final class MutableKey {
        String id;

        MutableKey(String id) {
            this.id = id;
        }

        @Override
        public int hashCode() {
            return Objects.hash(id);
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof MutableKey && ((MutableKey) o).id.equals(id);
        }
    }

    public static void main(String[] args) {
        System.out.println("=== 1. 基本操作与桶分布 ===");
        ChainedMap<String, Integer> map = new ChainedMap<>();
        String[] words = {"apple", "banana", "cherry", "date", "elderberry", "fig"};
        for (int i = 0; i < words.length; i++) {
            map.put(words[i], i);
        }
        System.out.println("  size=" + map.size() + " capacity=" + map.capacity());
        System.out.println("  get(\"cherry\") = " + map.get("cherry"));
        System.out.println("  remove(\"banana\") = " + map.remove("banana"));
        System.out.println("  get(\"banana\") 移除后 = " + map.get("banana"));
        System.out.print(map.buckets());

        System.out.println();
        System.out.println("=== 2. 扩容：16 -> 32，元素被拆到 i 和 i+16 两个桶 ===");
        for (int i = 0; i < 30; i++) {
            map.put("key-" + i, i);
        }
        System.out.println("  插入 36 个后 capacity=" + map.capacity()
                + " resizeCount=" + map.resizeCount() + " size=" + map.size());
        System.out.println("  扩容不需要重算 hash：只看 (hash & oldCap) 是 0 还是 1。");

        System.out.println();
        System.out.println("=== 3. 哈希函数质量：好 key vs 坏 key ===");
        ChainedMap<Integer, String> good = new ChainedMap<>();
        for (int i = 0; i < 32; i++) {
            good.put(i, "v" + i);
        }
        System.out.println("  连续整数 key: maxChain=" + good.maxChain() + "（分布均匀）");

        ChainedMap<BadKey, String> bad = new ChainedMap<>();
        for (int i = 0; i < 8; i++) {
            bad.put(new BadKey("k" + i), "v" + i);
        }
        System.out.println("  恒定 hashCode 的 key: maxChain=" + bad.maxChain()
                + "（退化成链表，查询 O(n)）");

        System.out.println();
        System.out.println("=== 4. 开放寻址法：冲突后线性探测 ===");
        OpenAddressMap oa = new OpenAddressMap(8);
        // 构造 3 个必然落在同一槽位的 key
        for (int i = 0; i < 3; i++) {
            oa.put(new Key8(i * 8), "v" + i);
        }
        System.out.println("  3 个同槽 key 写入后探测次数=" + oa.probes());
        oa.put(new Key8(0), "updated");
        System.out.println("  更新 key=0 后 get(0) = " + oa.get(new Key8(0)));
        oa.remove(new Key8(8));
        System.out.println("  删除 key=8 后 get(8) = " + oa.get(new Key8(8)) + "（墓碑生效）");
        System.out.println("  get(16) = " + oa.get(new Key8(16)) + "（删除留下的墓碑不阻断探测链）");

        System.out.println();
        System.out.println("=== 5. 可变 key 的反面教材 ===");
        ChainedMap<MutableKey, String> broken = new ChainedMap<>();
        MutableKey k = new MutableKey("order-1");
        broken.put(k, "订单详情");
        System.out.println("  放入时 get = " + broken.get(new MutableKey("order-1")));
        k.id = "order-2";
        System.out.println("  改了参与 hashCode 的字段后 get(new MutableKey(\"order-1\")) = "
                + broken.get(new MutableKey("order-1")) + "（找不回来了）");
        System.out.println("  结论：key 必须不可变，否则哈希值变了就定位不到原桶。");

        System.out.println();
        System.out.println("=== 6. 装载因子对冲突的影响 ===");
        for (float lf : new float[]{0.5f, 0.75f, 0.9f}) {
            int cap = 1024;
            int limit = (int) (cap * lf);
            List<List<Integer>> buckets = new ArrayList<>();
            for (int i = 0; i < cap; i++) {
                buckets.add(new ArrayList<>());
            }
            for (int i = 0; i < limit; i++) {
                int h = i * 0x9E3779B9;
                h ^= h >>> 16;
                buckets.get((cap - 1) & h).add(i);
            }
            int max = 0;
            int empty = 0;
            for (List<Integer> b : buckets) {
                max = Math.max(max, b.size());
                if (b.isEmpty()) {
                    empty++;
                }
            }
            System.out.printf("  装载因子 %.2f：最长桶=%d 空桶=%d/%d%n", lf, max, empty, cap);
        }
    }

    /** 固定落点的 key，方便演示线性探测 */
    static final class Key8 {
        final int v;

        Key8(int v) {
            this.v = v;
        }

        @Override
        public int hashCode() {
            return v;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key8 && ((Key8) o).v == v;
        }

        @Override
        public String toString() {
            return "K" + v;
        }
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 给 `ChainedMap` 实现真正的树化：当单桶链表长度 ≥ 8 时，把该桶转成你手写的红黑树（或至少是 `TreeMap` 逻辑），并测量 10 万个"恶意同 hash" key 的查询耗时变化。
 * 2. 对比链地址法与线性探测法在装载因子 0.9 时的平均探测次数（用随机 key 跑 10 万次），解释为什么 JDK 选 0.75。
 * 3. 用 `record` 定义一个 `Point(int x, int y)` 作为 key，放进 `HashMap` 验证能正确取回；再写一个不重写 `hashCode` 的普通类做同样实验，说明差异。
 * 4. 实现一个"按访问顺序淘汰"的 `LinkedHashMap`（`accessOrder=true`），观察 `get` 之后遍历顺序的变化。
 * ============================================================
 */
