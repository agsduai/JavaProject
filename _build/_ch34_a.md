### 34-01 复杂度分析与均摊分析

**知识点**
- **大 O 记号**描述的是"输入规模 n 增大时，操作次数增长的量级"，不是精确次数。写 `O(2n+3)` 没意义，直接写 `O(n)`。它衡量的是**趋势**，所以 n 很小时常数项会主导，测不出差别。
- 常见增长阶从快到慢：`O(1)` < `O(log n)` < `O(n)` < `O(n log n)` < `O(n²)` < `O(2ⁿ)` < `O(n!)`。工程上要警惕的是**跨阶**：把 `O(n²)` 优化成 `O(n log n)` 才有质变，`O(n)` 里省一半常数只是量变。
- **对数阶从哪来**：每一步把问题规模砍半（二分查找、归并排序的层数、平衡树的高度、快速幂）。看到"每次减半"就该想到 `log n`。
- **最好 / 最坏 / 平均**要分开说。快速排序平均 `O(n log n)`、最坏 `O(n²)`；哈希表平均 `O(1)`、最坏 `O(n)`（全部冲突）。面试和评审时只报平均复杂度是耍流氓。
- **空间复杂度**同样重要：归并排序 `O(n)` 额外空间，快排 `O(log n)` 递归栈，原地堆排序 `O(1)`。用空间换时间是常见取舍，但要有上限意识。
- **均摊分析（amortized）**：单次操作可能很贵，但把一串操作平均下来很便宜。动态数组扩容那一次是 `O(n)`，但每插入一个元素平均只搬移常数次，所以 `add` 均摊 `O(1)`。注意"均摊"不等于"每次都快"——某一次请求真的会卡。
- **嵌套循环不等于 `O(n²)`**：要看内层循环的边界。`for (i=0;i<n;i++) for (j=i;j<n;j++)` 是 `n(n+1)/2`，仍是 `O(n²)`；但 `for (i=0;i<n;i++) for (j=0;j<32;j++)` 是 `O(n)`，因为内层是常数。
- **字符串拼接是隐藏的 `O(n²)`**：`s += x` 每次都新建数组并复制，循环 n 次就是 `O(n²)`。用 `StringBuilder`。
- **递归的复杂度**要写递归式：归并排序 `T(n)=2T(n/2)+O(n)` → `O(n log n)`；斐波那契朴素递归 `T(n)=T(n-1)+T(n-2)` → `O(2ⁿ)`，加记忆化降到 `O(n)`。
- 易错：拿小数据量（n=100）测性能就下结论；只看平均复杂度忽略最坏情况（哈希碰撞攻击就是打最坏）；把 `O(n log n)` 的排序套在已经有序的数据上还以为很快（插入排序此时是 `O(n)`）。

**示例代码**
```java
import java.util.Arrays;


public class ComplexityAnalysis {
    /** 统计"基本操作"次数，用它代替真实耗时来观察增长趋势 */
    private static long ops;

    static int constant(int[] a) {
        ops++;
        return a[0];
    }

    static int linear(int[] a) {
        int sum = 0;
        for (int v : a) {
            ops++;
            sum += v;
        }
        return sum;
    }

    static int logarithmic(int[] sorted, int key) {
        int lo = 0;
        int hi = sorted.length - 1;
        while (lo <= hi) {
            ops++;
            int mid = (lo + hi) >>> 1;
            if (sorted[mid] == key) {
                return mid;
            }
            if (sorted[mid] < key) {
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        return -1;
    }

    static void nLogN(int[] a) {
        mergeSort(a, 0, a.length - 1, new int[a.length]);
    }

    private static void mergeSort(int[] a, int lo, int hi, int[] buf) {
        if (lo >= hi) {
            return;
        }
        int mid = (lo + hi) >>> 1;
        mergeSort(a, lo, mid, buf);
        mergeSort(a, mid + 1, hi, buf);
        int i = lo;
        int j = mid + 1;
        int k = lo;
        while (i <= mid && j <= hi) {
            ops++;
            buf[k++] = a[i] <= a[j] ? a[i++] : a[j++];
        }
        while (i <= mid) {
            ops++;
            buf[k++] = a[i++];
        }
        while (j <= hi) {
            ops++;
            buf[k++] = a[j++];
        }
        System.arraycopy(buf, lo, a, lo, hi - lo + 1);
    }

    static void quadratic(int[] a) {
        // 冒泡排序：比较次数恒为 n(n-1)/2，与数据是否有序无关
        for (int i = 0; i < a.length - 1; i++) {
            for (int j = 0; j < a.length - 1 - i; j++) {
                ops++;
                if (a[j] > a[j + 1]) {
                    int t = a[j];
                    a[j] = a[j + 1];
                    a[j + 1] = t;
                }
            }
        }
    }

    /** 动态数组：扩容 2 倍，统计累计搬移次数以验证均摊 O(1) */
    static final class GrowableArray {
        private int[] data = new int[1];
        private int size;
        private long copied;
        private int grows;

        void add(int v) {
            if (size == data.length) {
                copied += size;
                grows++;
                data = Arrays.copyOf(data, data.length * 2);
            }
            data[size++] = v;
        }

        long copied() {
            return copied;
        }

        int grows() {
            return grows;
        }
    }

    /** 自定义函数式接口：接收 int[]，避免 Consumer<int[]> 的装箱歧义 */
    @FunctionalInterface
    interface ArrayTask {
        void run(int[] data);
    }

    private static void measure(String label, int[] data, ArrayTask task) {
        ops = 0;
        long start = System.nanoTime();
        task.run(data);
        long cost = System.nanoTime() - start;
        System.out.printf("  %-12s 操作次数=%9d  %8.2f 次/元素  耗时=%6.2f ms%n",
                label, ops, ops / (double) data.length, cost / 1_000_000.0);
    }

    public static void main(String[] args) {
        System.out.println("=== 1. 同一份数据，五种复杂度的实际操作次数 ===");
        int[] data = new int[8192];
        for (int i = 0; i < data.length; i++) {
            data[i] = i * 3 % 1000;
        }
        int[] sorted = data.clone();
        Arrays.sort(sorted);

        measure("O(1)", data, ComplexityAnalysis::constant);
        measure("O(log n)", sorted, a -> logarithmic(a, a[a.length - 1] + 1));
        measure("O(n)", data, ComplexityAnalysis::linear);
        measure("O(n log n)", data.clone(), ComplexityAnalysis::nLogN);
        measure("O(n²)", data.clone(), ComplexityAnalysis::quadratic);

        System.out.println();
        System.out.println("=== 2. 规模翻倍时操作次数的变化（看倍数就能反推阶）===");
        System.out.printf("%8s %12s %12s %12s %12s%n", "n", "O(n)", "O(n log n)", "O(n²)", "log2(n)");
        for (int n = 512; n <= 8192; n *= 2) {
            int[] arr = new int[n];
            for (int i = 0; i < n; i++) {
                arr[i] = (i * 7919) % n;
            }
            ops = 0;
            linear(arr);
            long lin = ops;
            ops = 0;
            nLogN(arr.clone());
            long nlogn = ops;
            ops = 0;
            quadratic(arr.clone());
            long quad = ops;
            System.out.printf("%8d %12d %12d %12d %12d%n",
                    n, lin, nlogn, quad, 31 - Integer.numberOfLeadingZeros(n));
        }
        System.out.println("  结论：n 翻倍时 O(n) 变 2 倍、O(n log n) 略多于 2 倍、O(n²) 变 4 倍。");

        System.out.println();
        System.out.println("=== 3. 均摊分析：扩容 2 倍的动态数组 ===");
        System.out.printf("%10s %12s %10s %14s%n", "插入次数", "累计搬移", "扩容次数", "平均搬移/次");
        for (int n : new int[]{16, 256, 4096, 65536}) {
            GrowableArray arr = new GrowableArray();
            for (int i = 0; i < n; i++) {
                arr.add(i);
            }
            System.out.printf("%10d %12d %10d %14.3f%n",
                    n, arr.copied(), arr.grows(), arr.copied() / (double) n);
        }
        System.out.println("  结论：平均搬移次数趋近 1（常数），所以 add 是均摊 O(1)；");
        System.out.println("        但第 2^k+1 次插入那一下真的搬了 2^k 个元素，那一次是 O(n)。");

        System.out.println();
        System.out.println("=== 4. 字符串拼接的隐藏 O(n²) ===");
        for (int n : new int[]{2000, 4000, 8000}) {
            long t1 = System.nanoTime();
            String s = "";
            for (int i = 0; i < n; i++) {
                s += "x";
            }
            long t2 = System.nanoTime();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < n; i++) {
                sb.append('x');
            }
            long t3 = System.nanoTime();
            System.out.printf("  n=%-6d String+= %7.2f ms   StringBuilder %7.4f ms  (长度 %d)%n",
                    n, (t2 - t1) / 1e6, (t3 - t2) / 1e6, s.length());
        }
        System.out.println("  String 版本耗时随 n 呈平方增长，StringBuilder 近似线性。");
    }
}
```

**课后练习**
1. 写一个方法 `countTriples(int[] a)`，统计满足 `i<j<k` 且 `a[i]+a[j]+a[k]==0` 的三元组个数，先用三重循环实现并统计操作次数，再尝试用"排序 + 双指针"降到 `O(n²)`，对比 n=200 时的操作次数。
2. 分别实现"每次扩容 +1"和"每次扩容 ×2"的动态数组，插入 10000 个元素，打印累计搬移次数，解释为什么前者是 `O(n²)` 而后者均摊 `O(1)`。
3. 用递归实现斐波那契并统计调用次数，画出 n=10/20/30 的增长倍数；再加一个 `long[] memo` 缓存，比较调用次数差异。
4. 判断下列代码的时间复杂度并说明理由：`for (int i = 1; i < n; i *= 2) for (int j = 0; j < i; j++) sum++;`

---

### 34-02 动态数组与扩容策略

**知识点**
- 动态数组（`ArrayList` 的底层）就是"数组 + size 计数器 + 扩容策略"。数组本身长度固定，所谓"动态"是**装满后换一个更大的数组并复制**。
- **扩容倍数**是关键取舍。1.5 倍（JDK `ArrayList` 的选择）比 2 倍更省内存，且 1.5 倍的历史空间可以被后续分配复用；2 倍扩容次数少、搬移少，但峰值内存更高（新旧数组同时存在）。C++ `vector` 用 2 倍，Java 用 1.5 倍。
- **均摊成本**：按几何级数扩容（×1.5 或 ×2），n 次插入的总搬移量是 `O(n)`，均摊每次 `O(1)`。按固定长度扩容（+10）则总搬移 `O(n²)`，绝对不能用。
- **缩容**要谨慎：只在 `size < capacity/4` 时缩到一半，阈值取 1/4 而不是 1/2，否则在临界点反复 add/remove 会引发**抖动**（不断扩容缩容）。
- **指定初始容量**能省掉前期多次扩容。已知要装 10 万个元素时 `new ArrayList<>(100_000)`，比默认容量一路扩上去快且省。
- **插入/删除的代价**：尾部 `add` 是 `O(1)`；头部或中间插入要 `System.arraycopy` 搬移后面所有元素，是 `O(n)`。所以"频繁头部插入"要换 `LinkedList` 或 `ArrayDeque`。
- **`System.arraycopy` 是 native 方法**，比手写 for 循环快得多，内部按内存块拷贝。自己实现搬移一定要用它。
- **fail-fast 迭代器**：用 `modCount` 记录结构修改次数，迭代器创建时快照一份，每次 `next()` 比对，不一致就抛 `ConcurrentModificationException`。它只是"尽力检测"，不保证一定发现。
- **删除时用迭代器的 `remove()`** 而不是集合的 `remove()`，否则必然触发 fail-fast。
- 易错：`new int[0]` 当容量（每次 add 都扩容）；扩容后忘记把旧数组引用置空（大数组泄漏）；用 `==` 比较两个 `IntList` 内容；在 `for (Integer x : list)` 里调 `list.remove(x)`。

**示例代码**
```java
import java.util.Arrays;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.NoSuchElementException;

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
```

**课后练习**
1. 给 `IntList` 增加 `removeIf(IntPredicate)` 方法，要求一次遍历完成且只搬移一次，并保证 `modCount` 正确递增。
2. 把 `ensureCapacity` 改成 2 倍扩容，用 `simulate` 对比 n=1_000_000 时 ×1.5 与 ×2.0 的总搬移量和扩容次数，说明 JDK 为什么选 1.5。
3. 实现 `IntList` 的 `equals` 与 `hashCode`，要求两个内容相同的列表相等，并写出 3 个测试用例（含容量不同、长度不同）。
4. 用迭代器的 `remove()` 实现"删除所有偶数"，说明为什么不能直接调 `list.removeAt()`。

---

### 34-03 链表：反转、中点、环检测与合并

**知识点**
- **链表 vs 数组**：链表插入删除 `O(1)`（前提是已持有前驱节点），随机访问 `O(n)`；数组相反。链表节点分散在堆上，**缓存局部性差**，所以即使复杂度更优，小数据量下常常跑不过数组。
- **哨兵节点（dummy head）**能消除"头节点特判"，让插入删除逻辑统一。凡是链表题，先加哨兵通常能少一半分支。
- **反转链表**用三指针：`prev / cur / next`，每步先存 `next` 再改 `cur.next = prev`，最后返回 `prev`。写错的典型是改完指针才去取 `next`，链断了。
- **快慢指针**是链表的核心技巧：`slow` 走 1 步、`fast` 走 2 步。快指针到尾部时慢指针在中点；如果两者相遇说明有环。
- **Floyd 判环**分两步：先让快慢指针相遇，再把一个指针放回头部、两者同速前进，再次相遇点就是**环入口**。原理：头到入口距离 = 相遇点到入口距离（绕环整数圈）。
- **合并两个有序链表**：比较头节点、摘下较小的接到结果尾部，最后把没走完的那条整段接上。时间 `O(m+n)`、空间 `O(1)`。
- **双向链表**多一个 `prev`，删除节点不需要前驱（`node.prev` 直接可用），这是 `LinkedList` 和 LRU 的基础。代价是每个节点多一个引用。
- **删除倒数第 k 个**：快指针先走 k 步，再快慢同步走，快指针到尾时慢指针指向待删节点的前驱。
- 易错：遍历时 `cur = cur.next` 写在修改 `cur.next` 之前（丢链）；空链表 / 单节点没做边界；快慢指针 `fast.next.next` 没判空导致 NPE；返回了 `head` 而不是新的头。

**示例代码**
```java
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
```

**课后练习**
1. 实现"每 k 个节点一组反转链表"（`1->2->3->4->5`，k=2 → `2->1->4->3->5`），要求空间 `O(1)`。
2. 判断两条单链表是否相交，若相交返回第一个公共节点。要求时间 `O(m+n)`、空间 `O(1)`（提示：先求长度差）。
3. 实现双向链表的 `addFirst`、`removeFirst`、`removeLast`，并用 10000 次随机操作验证 `size` 与实际节点数一致。
4. 用链表实现"两数相加"：`(2->4->3) + (5->6->4)` 表示 `342 + 465 = 807`，输出 `7->0->8`。

---

### 34-04 栈、队列与单调栈/单调队列

**知识点**
- **栈**（LIFO）的两个动作只有 `push` / `pop`，天然适合"最近相关性"问题：括号匹配、表达式求值、函数调用、撤销操作、DFS。
- **队列**（FIFO）适合"按到达顺序处理"：BFS、任务排队、消息缓冲。`ArrayDeque` 同时能当栈和队列用，比 `Stack` 类和 `LinkedList` 都快（`Stack` 是同步的，`LinkedList` 每节点有额外开销）。
- **用数组实现栈**要注意 `top` 的含义：指向"下一个空位"还是"栈顶元素"。选一种并全程一致，否则 `push`/`pop` 的边界会错一位。
- **用两个栈实现队列**：`in` 栈负责入队，`out` 栈负责出队；`out` 空时把 `in` 全部倒入 `out`。每个元素最多被搬两次，均摊 `O(1)`。
- **单调栈**解决"下一个更大/更小元素"：维护一个**从栈底到栈顶单调递减**的栈，新元素入栈前把比它小的全部弹出，被弹出的元素就找到了自己的"下一个更大值"。
- 单调栈的复杂度是 `O(n)` 而不是 `O(n²)`，因为**每个元素最多入栈一次、出栈一次**，总操作次数 `2n`。这是"用栈把嵌套循环摊平"的典型。
- **单调队列**解决"滑动窗口最值"：队列里存下标，保持对应值单调递减；窗口右移时队首过期就弹出，新元素入队前把比它小的弹出。队首永远是窗口最大值。
- **循环队列**用 `(head + 1) % capacity` 推进，用 `size` 而不是"head == tail"区分空和满（否则空满条件相同）。
- 易错：`ArrayDeque` 不允许 `null`（`push(null)` 抛 NPE）；`Stack` 的 `peek()` 空栈抛 `EmptyStackException`；单调栈存值还是存下标没想清楚（需要算距离时必须存下标）；窗口题忘记判断队首是否已经滑出窗口。

**示例代码**
```java
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;

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
```

**课后练习**
1. 用单调栈求"每个元素左右两边第一个比它小的值"，返回两个数组，要求整体 `O(n)`。
2. 实现 `MinStack`，支持 `push`/`pop`/`top`/`getMin`，全部 `O(1)`（提示：辅助栈存"当前最小值的历史"）。
3. 用栈实现逆波兰表达式求值：输入 `["2","1","+","3","*"]` 输出 `9`，并处理除零与非法表达式。
4. 实现"每日温度"：给定气温数组，返回每天需要等待几天才会升温，没有更暖的一天则填 0（例：`[73,74,75,71,69,72,76,73]` → `[1,1,4,2,1,1,0,0]`）。
