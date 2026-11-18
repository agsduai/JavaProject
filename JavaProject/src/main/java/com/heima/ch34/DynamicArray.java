/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：34-02
 * 课名：动态数组与扩容策略
 * ------------------------------------------------------------
 * 知识点：
 *   - 动态数组（`ArrayList` 的底层）就是"数组 + size 计数器 + 扩容策略"。数组本身长度固定，所谓"动态"是**装满后换一个更大的数组并复制**。
 *   - **扩容倍数**是关键取舍。1.5 倍（JDK `ArrayList` 的选择）比 2 倍更省内存，且 1.5 倍的历史空间可以被后续分配复用；2 倍扩容次数少、搬移少，但峰值内存更高（新旧数组同时存在）。C++ `vector` 用 2 倍，Java 用 1.5 倍。
 *   - **均摊成本**：按几何级数扩容（×1.5 或 ×2），n 次插入的总搬移量是 `O(n)`，均摊每次 `O(1)`。按固定长度扩容（+10）则总搬移 `O(n²)`，绝对不能用。
 *   - **缩容**要谨慎：只在 `size < capacity/4` 时缩到一半，阈值取 1/4 而不是 1/2，否则在临界点反复 add/remove 会引发**抖动**（不断扩容缩容）。
 *   - **指定初始容量**能省掉前期多次扩容。已知要装 10 万个元素时 `new ArrayList<>(100_000)`，比默认容量一路扩上去快且省。
 *   - **插入/删除的代价**：尾部 `add` 是 `O(1)`；头部或中间插入要 `System.arraycopy` 搬移后面所有元素，是 `O(n)`。所以"频繁头部插入"要换 `LinkedList` 或 `ArrayDeque`。
 *   - **`System.arraycopy` 是 native 方法**，比手写 for 循环快得多，内部按内存块拷贝。自己实现搬移一定要用它。
 *   - **fail-fast 迭代器**：用 `modCount` 记录结构修改次数，迭代器创建时快照一份，每次 `next()` 比对，不一致就抛 `ConcurrentModificationException`。它只是"尽力检测"，不保证一定发现。
 *   - **删除时用迭代器的 `remove()`** 而不是集合的 `remove()`，否则必然触发 fail-fast。
 *   - 易错：`new int[0]` 当容量（每次 add 都扩容）；扩容后忘记把旧数组引用置空（大数组泄漏）；用 `==` 比较两个 `IntList` 内容；在 `for (Integer x : list)` 里调 `list.remove(x)`。
 * ============================================================
 */

package com.heima.ch34;

import java.util.Arrays;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.NoSuchElementException;

// ==================== 示例代码 ====================

public class DynamicArray {
    /** 手写动态数组：1.5 倍扩容 + 1/4 阈值缩容 + fail-fast 迭代器 */
    static final class IntList implements Iterable<Integer> {
        private static final int DEFAULT_CAPACITY = 4;
        private static final int MAX_CAPACITY = 1 << 30;

        private int[] data;
        private int size;
        private int modCount;
        private int growCount;
        private int shrinkCount;
        private long moved;

        IntList() {
            this(DEFAULT_CAPACITY);
        }

        IntList(int capacity) {
            if (capacity < 0) {
                throw new IllegalArgumentException("容量不能为负: " + capacity);
            }
            data = new int[Math.max(capacity, 1)];
        }

        int size() {
            return size;
        }

        int capacity() {
            return data.length;
        }

        int growCount() {
            return growCount;
        }

        int shrinkCount() {
            return shrinkCount;
        }

        long moved() {
            return moved;
        }

        private void checkElementIndex(int i) {
            if (i < 0 || i >= size) {
                throw new IndexOutOfBoundsException("index=" + i + ", size=" + size);
            }
        }

        int get(int i) {
            checkElementIndex(i);
            return data[i];
        }

        int set(int i, int value) {
            checkElementIndex(i);
            int old = data[i];
            data[i] = value;
            return old;
        }

        void add(int value) {
            ensureCapacity(size + 1);
            data[size++] = value;
            modCount++;
        }

        void add(int index, int value) {
            if (index < 0 || index > size) {
                throw new IndexOutOfBoundsException("index=" + index + ", size=" + size);
            }
            ensureCapacity(size + 1);
            int tail = size - index;
            if (tail > 0) {
                System.arraycopy(data, index, data, index + 1, tail);
                moved += tail;
            }
            data[index] = value;
            size++;
            modCount++;
        }

        int removeAt(int index) {
            checkElementIndex(index);
            int old = data[index];
            int tail = size - index - 1;
            if (tail > 0) {
                System.arraycopy(data, index + 1, data, index, tail);
                moved += tail;
            }
            data[--size] = 0;
            modCount++;
            shrinkIfNeeded();
            return old;
        }

        int indexOf(int value) {
            for (int i = 0; i < size; i++) {
                if (data[i] == value) {
                    return i;
                }
            }
            return -1;
        }

        private void ensureCapacity(int need) {
            if (need <= data.length) {
                return;
            }
            int oldCap = data.length;
            int newCap = oldCap + (oldCap >> 1);
            if (newCap < need) {
                newCap = need;
            }
            if (newCap > MAX_CAPACITY) {
                newCap = MAX_CAPACITY;
            }
            growCount++;
            moved += size;
            data = Arrays.copyOf(data, newCap);
            System.out.printf("    [扩容] %d -> %d（当前 size=%d）%n", oldCap, newCap, size);
        }

        private void shrinkIfNeeded() {
            // 阈值取 1/4：避免在 1/2 附近 add/remove 反复抖动
            if (data.length > DEFAULT_CAPACITY && size < data.length / 4) {
                int oldCap = data.length;
                int newCap = Math.max(DEFAULT_CAPACITY, oldCap / 2);
                shrinkCount++;
                moved += size;
                data = Arrays.copyOf(data, newCap);
                System.out.printf("    [缩容] %d -> %d（当前 size=%d）%n", oldCap, newCap, size);
            }
        }

        @Override
        public Iterator<Integer> iterator() {
            return new Iterator<Integer>() {
                private int cursor;
                private final int expectedModCount = modCount;

                @Override
                public boolean hasNext() {
                    return cursor < size;
                }

                @Override
                public Integer next() {
                    if (expectedModCount != modCount) {
                        throw new ConcurrentModificationException("迭代期间集合被修改");
                    }
                    if (cursor >= size) {
                        throw new NoSuchElementException();
                    }
                    return data[cursor++];
                }
            };
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < size; i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(data[i]);
            }
            return sb.append(']').toString();
        }
    }

    /** 按倍数扩容：总搬移量随 n 的增长趋势 */
    static long simulateGrowByFactor(int n, double factor) {
        int cap = 1;
        int size = 0;
        long moved = 0;
        for (int i = 0; i < n; i++) {
            if (size == cap) {
                moved += size;
                cap = Math.max((int) Math.ceil(cap * factor), cap + 1);
            }
            size++;
        }
        return moved;
    }

    /** 按固定步长扩容：每次只多 step 个槽位 */
    static long simulateGrowByStep(int n, int step) {
        int cap = step;
        int size = 0;
        long moved = 0;
        for (int i = 0; i < n; i++) {
            if (size == cap) {
                moved += size;
                cap += step;
            }
            size++;
        }
        return moved;
    }

    public static void main(String[] args) {
        System.out.println("=== 1. 扩容过程（默认容量 4，1.5 倍增长）===");
        IntList list = new IntList();
        for (int i = 1; i <= 12; i++) {
            list.add(i * 10);
        }
        System.out.println("  " + list + "  size=" + list.size() + " capacity=" + list.capacity());

        System.out.println();
        System.out.println("=== 2. 中间插入要搬移尾部元素 ===");
        IntList ordered = new IntList(8);
        for (int i = 0; i < 6; i++) {
            ordered.add(i);
        }
        long before = ordered.moved();
        ordered.add(0, 99);
        System.out.println("  add(0, 99) 后 = " + ordered + "，本次搬移 " + (ordered.moved() - before) + " 个元素");
        before = ordered.moved();
        ordered.add(3, 77);
        System.out.println("  add(3, 77) 后 = " + ordered + "，本次搬移 " + (ordered.moved() - before) + " 个元素");
        System.out.println("  结论：头部插入 O(n)，尾部插入 O(1)。");

        System.out.println();
        System.out.println("=== 3. 缩容（size 降到容量 1/4 以下才缩）===");
        IntList shrinkDemo = new IntList();
        for (int i = 0; i < 20; i++) {
            shrinkDemo.add(i);
        }
        System.out.println("  装 20 个后 capacity=" + shrinkDemo.capacity());
        for (int i = 0; i < 17; i++) {
            shrinkDemo.removeAt(shrinkDemo.size() - 1);
        }
        System.out.println("  删到 size=" + shrinkDemo.size() + " 后 capacity=" + shrinkDemo.capacity()
                + "，缩容次数=" + shrinkDemo.shrinkCount());

        System.out.println();
        System.out.println("=== 4. fail-fast 迭代器 ===");
        IntList failFast = new IntList();
        for (int i = 0; i < 5; i++) {
            failFast.add(i);
        }
        try {
            for (Integer v : failFast) {
                System.out.println("  读到 " + v);
                if (v == 2) {
                    failFast.add(100);
                }
            }
        } catch (ConcurrentModificationException e) {
            System.out.println("  捕获 ConcurrentModificationException: " + e.getMessage());
        }

        System.out.println();
        System.out.println("=== 5. 扩容倍数对比（n=100 万时的总搬移量）===");
        int n = 1_000_000;
        long by15 = simulateGrowByFactor(n, 1.5);
        long by20 = simulateGrowByFactor(n, 2.0);
        long byStep = simulateGrowByStep(n, 10);
        System.out.printf("  %-10s %14s %14s%n", "扩容策略", "总搬移次数", "平均每次");
        System.out.printf("  %-10s %14d %14.2f%n", "×1.5", by15, by15 / (double) n);
        System.out.printf("  %-10s %14d %14.2f%n", "×2.0", by20, by20 / (double) n);
        System.out.printf("  %-10s %14d %14.2f%n", "+10", byStep, byStep / (double) n);
        System.out.println("  几何扩容的均摊搬移是常数；固定长度扩容是 O(n²)，绝不可用。");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 给 `IntList` 增加 `removeIf(IntPredicate)` 方法，要求一次遍历完成且只搬移一次，并保证 `modCount` 正确递增。
 * 2. 把 `ensureCapacity` 改成 2 倍扩容，用 `simulate` 对比 n=1_000_000 时 ×1.5 与 ×2.0 的总搬移量和扩容次数，说明 JDK 为什么选 1.5。
 * 3. 实现 `IntList` 的 `equals` 与 `hashCode`，要求两个内容相同的列表相等，并写出 3 个测试用例（含容量不同、长度不同）。
 * 4. 用迭代器的 `remove()` 实现"删除所有偶数"，说明为什么不能直接调 `list.removeAt()`。
 * ============================================================
 */
