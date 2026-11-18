### 34-09 排序算法对比与稳定性

**知识点**
- **稳定性**指相等元素排序后相对顺序不变。`Integer` 排序看不出差别，但"先按价格排、再按销量排"这种多关键字排序完全依赖稳定性——不稳定就会把上一次的顺序打乱。
- 稳定排序：冒泡、插入、归并、计数/基数。**不稳定**排序：选择、快速、堆、希尔。JDK 的 `Arrays.sort(int[])` 用**双轴快排**（不稳定），`Arrays.sort(Object[])` 用 **TimSort**（归并变体，稳定）——因为对象数组常常需要多关键字排序。
- **插入排序**在"基本有序"的数据上接近 `O(n)`，所以 TimSort 先找自然有序段（run），再归并。这就是为什么对近乎有序的数组，`Arrays.sort(Object[])` 比快排快。
- **快速排序**的关键在 **partition**：选基准、把小于基准的放左边、大于的放右边。经典坑是"全部相等"时退化成 `O(n²)`，要用**三路快排**（小于/等于/大于）或随机基准。
- **快排最坏 `O(n²)`** 出现在每次基准都是最值时（有序数组 + 取首元素作基准）。随机化基准能把最坏情况概率降到极低，但**不能消除**——真正的"最坏情况保证"要靠归并或堆。
- **归并排序**稳定、`O(n log n)` 恒定，代价是 `O(n)` 额外空间。适合链表排序（链表归并不需要额外数组）和外部排序（大文件分块排完再归并）。
- **堆排序**原地、`O(n log n)` 恒定、不稳定。它的比较次数比快排多（缓存不友好），所以实际比快排慢，但空间 `O(1)` 是优势。
- **计数排序**要求数据范围小且是整数，`O(n+k)`；**基数排序**按位分配收集，`O(d(n+k))`。两者都是**非比较排序**，下界不受 `Ω(n log n)` 约束。
- **比较排序的下界是 `Ω(n log n)`**：n 个元素有 n! 种排列，每次比较只提供 1 bit 信息，需要 `log2(n!) ≈ n log n` 次比较。这条下界决定了不可能有更快的通用比较排序。
- 易错：以为 `Arrays.sort` 对所有类型都稳定；快排递归不判 `lo >= hi` 导致栈溢出；归并时 `buf` 每次 new 导致 GC 压力；`Comparator` 不满足传递性（`a<b, b<c` 但 `a>c`）导致 TimSort 抛 `IllegalArgumentException: Comparison method violates its general contract`。

**示例代码**
```java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

public class SortingCompare {
    /** 统计比较与交换次数，用于横向对比 */
    static final class Stats {
        long compares;
        long swaps;
        long nanos;

        @Override
        public String toString() {
            return String.format("比较=%8d 交换=%8d 耗时=%7.2fms", compares, swaps, nanos / 1e6);
        }
    }

    interface Sorter {
        void sort(int[] a, Stats s);
    }

    static final Sorter BUBBLE = (a, s) -> {
        for (int i = 0; i < a.length - 1; i++) {
            boolean swapped = false;
            for (int j = 0; j < a.length - 1 - i; j++) {
                s.compares++;
                if (a[j] > a[j + 1]) {
                    int t = a[j];
                    a[j] = a[j + 1];
                    a[j + 1] = t;
                    s.swaps++;
                    swapped = true;
                }
            }
            if (!swapped) {
                break;
            }
        }
    };

    static final Sorter INSERTION = (a, s) -> {
        for (int i = 1; i < a.length; i++) {
            int key = a[i];
            int j = i - 1;
            while (j >= 0) {
                s.compares++;
                if (a[j] <= key) {
                    break;
                }
                a[j + 1] = a[j];
                s.swaps++;
                j--;
            }
            a[j + 1] = key;
        }
    };

    static final Sorter SELECTION = (a, s) -> {
        for (int i = 0; i < a.length - 1; i++) {
            int min = i;
            for (int j = i + 1; j < a.length; j++) {
                s.compares++;
                if (a[j] < a[min]) {
                    min = j;
                }
            }
            if (min != i) {
                int t = a[i];
                a[i] = a[min];
                a[min] = t;
                s.swaps++;
            }
        }
    };

    static final Sorter MERGE = (a, s) -> mergeSort(a, 0, a.length - 1, new int[a.length], s);

    private static void mergeSort(int[] a, int lo, int hi, int[] buf, Stats s) {
        if (lo >= hi) {
            return;
        }
        int mid = (lo + hi) >>> 1;
        mergeSort(a, lo, mid, buf, s);
        mergeSort(a, mid + 1, hi, buf, s);
        int i = lo;
        int j = mid + 1;
        int k = lo;
        while (i <= mid && j <= hi) {
            s.compares++;
            buf[k++] = a[i] <= a[j] ? a[i++] : a[j++];
        }
        while (i <= mid) {
            buf[k++] = a[i++];
        }
        while (j <= hi) {
            buf[k++] = a[j++];
        }
        System.arraycopy(buf, lo, a, lo, hi - lo + 1);
        s.swaps += hi - lo + 1;
    }

    static final Sorter QUICK = (a, s) -> quickSort(a, 0, a.length - 1, s);

    private static void quickSort(int[] a, int lo, int hi, Stats s) {
        while (lo < hi) {
            int p = partition(a, lo, hi, s);
            // 先递归短的一侧，长的用循环，把栈深度压到 O(log n)
            if (p - lo < hi - p) {
                quickSort(a, lo, p - 1, s);
                lo = p + 1;
            } else {
                quickSort(a, p + 1, hi, s);
                hi = p - 1;
            }
        }
    }

    private static int partition(int[] a, int lo, int hi, Stats s) {
        int pivot = a[hi];
        int i = lo;
        for (int j = lo; j < hi; j++) {
            s.compares++;
            if (a[j] < pivot) {
                int t = a[i];
                a[i] = a[j];
                a[j] = t;
                s.swaps++;
                i++;
            }
        }
        int t = a[i];
        a[i] = a[hi];
        a[hi] = t;
        s.swaps++;
        return i;
    }

    /** 三路快排：解决"大量重复元素"导致的退化 */
    static void quickSort3Way(int[] a, int lo, int hi) {
        if (lo >= hi) {
            return;
        }
        int pivot = a[lo + (hi - lo) / 2];
        int lt = lo;
        int gt = hi;
        int i = lo;
        while (i <= gt) {
            if (a[i] < pivot) {
                swap(a, lt++, i++);
            } else if (a[i] > pivot) {
                swap(a, i, gt--);
            } else {
                i++;
            }
        }
        quickSort3Way(a, lo, lt - 1);
        quickSort3Way(a, gt + 1, hi);
    }

    private static void swap(int[] a, int i, int j) {
        int t = a[i];
        a[i] = a[j];
        a[j] = t;
    }

    static final Sorter HEAP = (a, s) -> {
        int n = a.length;
        for (int i = n / 2 - 1; i >= 0; i--) {
            heapDown(a, i, n, s);
        }
        for (int end = n - 1; end > 0; end--) {
            swap(a, 0, end);
            s.swaps++;
            heapDown(a, 0, end, s);
        }
    };

    private static void heapDown(int[] a, int i, int n, Stats s) {
        while (true) {
            int l = i * 2 + 1;
            int r = l + 1;
            int big = i;
            if (l < n) {
                s.compares++;
                if (a[l] > a[big]) {
                    big = l;
                }
            }
            if (r < n) {
                s.compares++;
                if (a[r] > a[big]) {
                    big = r;
                }
            }
            if (big == i) {
                return;
            }
            swap(a, i, big);
            s.swaps++;
            i = big;
        }
    }

    /** 计数排序：非比较排序，O(n+k)，要求整数且范围可控 */
    static int[] countingSort(int[] a) {
        if (a.length == 0) {
            return new int[0];
        }
        int min = a[0];
        int max = a[0];
        for (int v : a) {
            min = Math.min(min, v);
            max = Math.max(max, v);
        }
        int[] count = new int[max - min + 1];
        for (int v : a) {
            count[v - min]++;
        }
        int[] out = new int[a.length];
        int p = 0;
        for (int v = min; v <= max; v++) {
            for (int c = 0; c < count[v - min]; c++) {
                out[p++] = v;
            }
        }
        return out;
    }

    /** 记录类：用稳定排序实现"多关键字排序" */
    static final class Order {
        final String id;
        final int price;
        final int sales;

        Order(String id, int price, int sales) {
            this.id = id;
            this.price = price;
            this.sales = sales;
        }

        @Override
        public String toString() {
            return id + "(价" + price + ",销" + sales + ")";
        }
    }

    private static void run(String name, Sorter sorter, int[] original) {
        int[] copy = original.clone();
        Stats s = new Stats();
        long t0 = System.nanoTime();
        sorter.sort(copy, s);
        s.nanos = System.nanoTime() - t0;
        boolean ok = isSorted(copy);
        System.out.printf("  %-10s %s %s%n", name, s, ok ? "OK" : "排序错误!");
    }

    private static boolean isSorted(int[] a) {
        for (int i = 1; i < a.length; i++) {
            if (a[i - 1] > a[i]) {
                return false;
            }
        }
        return true;
    }

    public static void main(String[] args) {
        System.out.println("=== 1. 随机数据 n=20000 横向对比 ===");
        Random rnd = new Random(42);
        int[] random = new int[20_000];
        for (int i = 0; i < random.length; i++) {
            random[i] = rnd.nextInt(100_000);
        }
        run("冒泡", BUBBLE, Arrays.copyOf(random, 3000));
        run("插入", INSERTION, Arrays.copyOf(random, 3000));
        run("选择", SELECTION, Arrays.copyOf(random, 3000));
        System.out.println("  （以上三个 O(n²) 只用 3000 个元素，否则太慢）");
        run("归并", MERGE, random);
        run("快排", QUICK, random);
        run("堆排", HEAP, random);

        System.out.println();
        System.out.println("=== 2. 基本有序时插入排序的优势 ===");
        int[] nearlySorted = new int[20_000];
        for (int i = 0; i < nearlySorted.length; i++) {
            nearlySorted[i] = i;
        }
        for (int i = 0; i < 20; i++) {
            int p = rnd.nextInt(nearlySorted.length);
            int q = rnd.nextInt(nearlySorted.length);
            swap(nearlySorted, p, q);
        }
        run("插入", INSERTION, nearlySorted);
        run("快排", QUICK, nearlySorted);
        System.out.println("  插入排序比较次数接近 n，快排仍是 n log n —— TimSort 就是利用这一点。");

        System.out.println();
        System.out.println("=== 3. 全部相等的数据：经典快排退化 ===");
        int[] allSame = new int[5000];
        Arrays.fill(allSame, 7);
        run("快排(取末位)", QUICK, allSame);
        long t0 = System.nanoTime();
        int[] copy3 = allSame.clone();
        quickSort3Way(copy3, 0, copy3.length - 1);
        long t1 = System.nanoTime();
        System.out.printf("  三路快排      比较次数很少，耗时=%7.2fms %s%n",
                (t1 - t0) / 1e6, isSorted(copy3) ? "OK" : "错误");
        System.out.println("  三路快排把「等于基准」的元素一次归位，重复元素越多越快。");

        System.out.println();
        System.out.println("=== 4. 稳定性验证：先按价格、再按销量 ===");
        List<Order> orders = new ArrayList<>(List.of(
                new Order("A", 100, 50),
                new Order("B", 200, 80),
                new Order("C", 100, 90),
                new Order("D", 200, 10),
                new Order("E", 100, 20)));
        System.out.println("  原始: " + orders);

        List<Order> stable = new ArrayList<>(orders);
        stable.sort(Comparator.comparingInt(o -> o.price));   // 第一次：价格
        stable.sort(Comparator.comparingInt(o -> o.sales));   // 第二次：销量
        System.out.println("  TimSort 两次排序: " + stable);
        System.out.println("  销量相同时价格顺序保持，说明第二次排序是稳定的。");

        List<Order> byPriceThenSales = new ArrayList<>(orders);
        byPriceThenSales.sort(Comparator.comparingInt((Order o) -> o.price)
                .thenComparingInt(o -> o.sales));
        System.out.println("  直接用组合 Comparator: " + byPriceThenSales);
        System.out.println("  结论：多关键字排序要么用组合 Comparator，要么依赖稳定排序。");

        System.out.println();
        System.out.println("=== 5. 计数排序：非比较排序突破 O(n log n) ===");
        int[] scores = {88, 92, 75, 88, 100, 60, 92, 75, 88};
        System.out.println("  原始成绩: " + Arrays.toString(scores));
        System.out.println("  计数排序: " + Arrays.toString(countingSort(scores)));
        System.out.println("  分数范围 0~100，只需 101 个桶，时间 O(n+k)。");

        System.out.println();
        System.out.println("=== 6. 不稳定排序的反例 ===");
        List<int[]> pairs = new ArrayList<>();
        pairs.add(new int[]{1, 1});
        pairs.add(new int[]{1, 2});
        pairs.add(new int[]{0, 3});
        pairs.add(new int[]{0, 4});
        List<int[]> unstable = new ArrayList<>(pairs);
        // 模拟选择排序：每轮选最小值换到前面，会打乱相等元素的相对顺序
        for (int i = 0; i < unstable.size() - 1; i++) {
            int min = i;
            for (int j = i + 1; j < unstable.size(); j++) {
                if (unstable.get(j)[0] < unstable.get(min)[0]) {
                    min = j;
                }
            }
            if (min != i) {
                int[] t = unstable.get(i);
                unstable.set(i, unstable.get(min));
                unstable.set(min, t);
            }
        }
        System.out.print("  选择排序后 (key, 原始序): ");
        for (int[] p : unstable) {
            System.out.print(Arrays.toString(p) + " ");
        }
        System.out.println();
        System.out.println("  key=0 的两个元素原始序 3,4 变成了 3,4 之外的其他组合 —— 稳定性被破坏。");
    }
}
```

**课后练习**
1. 实现"链表归并排序"，要求时间 `O(n log n)`、空间 `O(log n)`（只用递归栈），并与数组版归并对比内存占用。
2. 实现随机化快排（随机选基准）与"三数取中"快排，在 100 万个有序数据上对比耗时，说明为什么两者都比"取末位"安全。
3. 用计数排序实现"给 1000 万个 0~255 的字节排序"，统计耗时并与 `Arrays.sort(int[])` 对比。
4. 构造一个让 `Comparator` 违反传递性的例子（如 `a<b, b<c, c<a`），用 `List.sort` 触发 `IllegalArgumentException`，并说明为什么 TimSort 要检查这一点。

---

### 34-10 二分查找及其变体

**知识点**
- 二分查找的前提是**有序**（或至少"单调可判定"）。核心是每次排除一半，`O(log n)`。100 万元素最多 20 次比较。
- **区间定义要全程一致**。两种主流写法：**闭区间 `[lo, hi]`**（`while (lo <= hi)`，`hi = mid - 1`）和**左闭右开 `[lo, hi)`**（`while (lo < hi)`，`hi = mid`）。混用必然死循环或漏元素。
- **`mid = (lo + hi) / 2` 会整数溢出**（`lo + hi` 超过 `int` 上限）。正确写法是 `lo + (hi - lo) / 2` 或 `(lo + hi) >>> 1`。这是 JDK 曾修复过的真实 bug。
- **`while (lo <= hi)` 还是 `<`** 取决于区间是闭还是半开。判断标准：如果 `hi` 是"可达的下标"就用 `<=`，如果 `hi` 是"边界外的位置"就用 `<`。
- **四种变体**是二分真正的价值所在，比"找一个数"难得多：
  - **找第一个 ≥ target**（lower bound）：即使 `a[mid] == target` 也继续往左收，返回 `lo`。
  - **找第一个 > target**（upper bound）：`a[mid] <= target` 时右移。
  - **找最后一个 ≤ target**：等价于 `upperBound - 1`。
  - **找旋转数组最小值**：判断 `a[mid]` 与 `a[hi]` 的关系决定收缩哪边。
- **二分答案**是二分的进阶用法：当"判定某个值 x 是否可行"是单调的（x 可行则更大/更小也可行），就可以二分 x 本身。典型题：切绳子、运货能力、最小最大值。
- **浮点二分**用固定迭代次数（100 次足够到 1e-30）而不是 `while (hi - lo > 1e-9)`，避免精度死循环。
- **`Arrays.binarySearch` 的坑**：找到返回下标，**找不到返回 `-(插入点) - 1`**（负数），所以判断"是否存在"必须 `>= 0`。很多人写成 `!= -1` 就错了。
- 易错：`mid` 溢出；`lo = mid` 而不是 `mid + 1` 导致死循环；旋转数组没判有序半边；对无序数组二分（结果不可预测）；浮点二分用等值判断。

**示例代码**
```java
import java.util.Arrays;

public class BinarySearchVariants {
    /** 基础版：闭区间 [lo, hi]，找不到返回 -1 */
    static int basic(int[] a, int target) {
        int lo = 0;
        int hi = a.length - 1;
        while (lo <= hi) {
            int mid = lo + (hi - lo) / 2;
            if (a[mid] == target) {
                return mid;
            }
            if (a[mid] < target) {
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        return -1;
    }

    /** lower bound：第一个 >= target 的下标，不存在返回 a.length */
    static int lowerBound(int[] a, int target) {
        int lo = 0;
        int hi = a.length;
        while (lo < hi) {
            int mid = lo + (hi - lo) / 2;
            if (a[mid] < target) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo;
    }

    /** upper bound：第一个 > target 的下标 */
    static int upperBound(int[] a, int target) {
        int lo = 0;
        int hi = a.length;
        while (lo < hi) {
            int mid = lo + (hi - lo) / 2;
            if (a[mid] <= target) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo;
    }

    /** 最后一个 <= target 的下标，不存在返回 -1 */
    static int lastLessOrEqual(int[] a, int target) {
        return upperBound(a, target) - 1;
    }

    /** 旋转有序数组（无重复）中找最小值 */
    static int rotatedMin(int[] a) {
        int lo = 0;
        int hi = a.length - 1;
        while (lo < hi) {
            int mid = lo + (hi - lo) / 2;
            if (a[mid] > a[hi]) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return a[lo];
    }

    /** 旋转有序数组（无重复）中查 target */
    static int rotatedSearch(int[] a, int target) {
        int lo = 0;
        int hi = a.length - 1;
        while (lo <= hi) {
            int mid = lo + (hi - lo) / 2;
            if (a[mid] == target) {
                return mid;
            }
            if (a[lo] <= a[mid]) {
                // 左半边有序
                if (target >= a[lo] && target < a[mid]) {
                    hi = mid - 1;
                } else {
                    lo = mid + 1;
                }
            } else {
                // 右半边有序
                if (target > a[mid] && target <= a[hi]) {
                    lo = mid + 1;
                } else {
                    hi = mid - 1;
                }
            }
        }
        return -1;
    }

    /** 二分答案：把 length 根绳子切成等长 pieces 段，求最大单段长度 */
    static double maxRopeLength(int[] ropes, int pieces) {
        double lo = 0;
        double hi = 0;
        for (int r : ropes) {
            hi = Math.max(hi, r);
        }
        for (int i = 0; i < 100; i++) {
            double mid = (lo + hi) / 2;
            int count = 0;
            for (int r : ropes) {
                count += (int) (r / mid);
            }
            if (count >= pieces) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        return lo;
    }

    /** 二分答案：capacity 天内运完所有包裹的最小载重 */
    static int minShipCapacity(int[] weights, int days) {
        int lo = 0;
        int hi = 0;
        for (int w : weights) {
            lo = Math.max(lo, w);
            hi += w;
        }
        while (lo < hi) {
            int mid = lo + (hi - lo) / 2;
            if (canShip(weights, days, mid)) {
                hi = mid;
            } else {
                lo = mid + 1;
            }
        }
        return lo;
    }

    private static boolean canShip(int[] weights, int days, int capacity) {
        int need = 1;
        int cur = 0;
        for (int w : weights) {
            if (cur + w > capacity) {
                need++;
                cur = 0;
            }
            cur += w;
        }
        return need <= days;
    }

    /** 用 JDK 的 binarySearch 做"插入点" */
    static int insertPosition(int[] a, int target) {
        int idx = Arrays.binarySearch(a, target);
        return idx >= 0 ? idx : -(idx + 1);
    }

    public static void main(String[] args) {
        int[] sorted = {1, 3, 3, 3, 5, 7, 9, 11};
        System.out.println("=== 1. 基础查找 ===");
        System.out.println("  数组: " + Arrays.toString(sorted));
        for (int t : new int[]{3, 5, 4, 1, 11, 12}) {
            System.out.printf("  查找 %2d -> index=%d%n", t, basic(sorted, t));
        }

        System.out.println();
        System.out.println("=== 2. lower/upper bound：处理重复元素 ===");
        System.out.printf("  %-18s %s%n", "target", "lower  upper  last<=  count");
        for (int t : new int[]{3, 4, 5, 0, 11, 12}) {
            int lb = lowerBound(sorted, t);
            int ub = upperBound(sorted, t);
            System.out.printf("  %-18d %-6d %-6d %-7d %d%n",
                    t, lb, ub, lastLessOrEqual(sorted, t), ub - lb);
        }
        System.out.println("  count = upper - lower 就是 target 出现次数，不用遍历。");

        System.out.println();
        System.out.println("=== 3. 旋转有序数组 ===");
        int[] rotated = {4, 5, 6, 7, 0, 1, 2};
        System.out.println("  数组: " + Arrays.toString(rotated));
        System.out.println("  最小值 = " + rotatedMin(rotated));
        for (int t : new int[]{0, 4, 7, 3}) {
            System.out.printf("  查找 %d -> index=%d%n", t, rotatedSearch(rotated, t));
        }
        System.out.println("  关键：每次至少有一半是有序的，判断 target 是否落在有序半边。");

        System.out.println();
        System.out.println("=== 4. 二分答案：切绳子 ===");
        int[] ropes = {802, 743, 457, 539};
        System.out.println("  绳子: " + Arrays.toString(ropes));
        for (int k : new int[]{11, 20, 100}) {
            System.out.printf("  切 %3d 段 -> 最大单段长度 = %.2f%n", k, maxRopeLength(ropes, k));
        }
        System.out.println("  " + maxRopeLength(ropes, 11) + " 这里说明：二分的是「答案」本身，不是数组下标。");

        System.out.println();
        System.out.println("=== 5. 二分答案：运货最小载重 ===");
        int[] weights = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
        System.out.println("  包裹: " + Arrays.toString(weights));
        for (int d : new int[]{5, 3, 1}) {
            System.out.printf("  %2d 天运完 -> 最小载重 = %d%n", d, minShipCapacity(weights, d));
        }
        System.out.println("  可行性判定是单调的（载重越大越容易运完），所以可以二分载重。");

        System.out.println();
        System.out.println("=== 6. Arrays.binarySearch 的返回值陷阱 ===");
        int[] arr = {10, 20, 30, 40};
        System.out.println("  数组: " + Arrays.toString(arr));
        for (int t : new int[]{20, 25, 5, 50}) {
            int raw = Arrays.binarySearch(arr, t);
            System.out.printf("  查 %2d -> 原始返回=%3d  是否找到=%-5s  插入点=%d%n",
                    t, raw, raw >= 0, insertPosition(arr, t));
        }
        System.out.println("  找不到时返回 -(插入点)-1，所以判断存在必须用 >= 0，不能用 != -1。");

        System.out.println();
        System.out.println("=== 7. 比较次数：二分 vs 线性 ===");
        int n = 1_000_000;
        int[] big = new int[n];
        for (int i = 0; i < n; i++) {
            big[i] = i * 2;
        }
        int target = 1_999_998;
        int linearSteps = 0;
        for (int i = 0; i < n; i++) {
            linearSteps++;
            if (big[i] == target) {
                break;
            }
        }
        int lo = 0;
        int hi = n - 1;
        int binarySteps = 0;
        while (lo <= hi) {
            binarySteps++;
            int mid = lo + (hi - lo) / 2;
            if (big[mid] == target) {
                break;
            }
            if (big[mid] < target) {
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        System.out.println("  n=1,000,000，查找 " + target);
        System.out.println("  线性查找比较 " + linearSteps + " 次");
        System.out.println("  二分查找比较 " + binarySteps + " 次（log2(1e6)≈20）");
    }
}
```

**课后练习**
1. 实现 `findPeakElement`：在"任意相邻元素不相等"的数组中找一个峰值下标，要求 `O(log n)`，说明为什么无序数组也能二分。
2. 实现"在二维矩阵中查找目标值"：每行升序、每列升序，要求 `O(m+n)`；再实现"每行升序且下一行首元素大于上一行末元素"的 `O(log(mn))` 版本。
3. 用二分答案解决"分割数组的最大值"：把非负数组分成 k 段，使各段和的最大值最小，返回该最小值。
4. 实现 `sqrt(int x)` 的整数二分版本（返回向下取整），要求不使用 `Math.sqrt`，并处理 `x=0`、`x=1`、`x=Integer.MAX_VALUE`。

---

### 34-11 综合案例：LRU 缓存与实时排行榜

**知识点**
- **LRU（Least Recently Used）** 的语义：容量满时淘汰"最久未被访问"的条目。`get` 和 `put` 都必须 `O(1)`，这要求两个结构配合：**哈希表**负责 `O(1)` 定位，**双向链表**负责 `O(1)` 调整顺序。
- 链表顺序约定：**头部是最近使用**，**尾部是最久未使用**。`get` 命中时把节点移到头部；`put` 新节点插头部；容量超了删尾部的 `prev`。
- 用**哨兵头尾节点**能消除"链表为空""只有一个节点"的特判，所有插入删除逻辑统一。
- **`LinkedHashMap` 自带 LRU**：构造时传 `accessOrder=true`，重写 `removeEldestEntry` 即可。手写版的价值在于理解原理，生产里优先用 `LinkedHashMap`。
- **LFU（Least Frequently Used）** 按访问频次淘汰，频次相同再按最久未用。实现要用"频次 → 双向链表"的桶，且维护 `minFreq` 以便 `O(1)` 淘汰。
- **排行榜**的需求是"实时更新分数 + 查 Top-N + 查某人排名"。用 `TreeMap<分数, 有序集合>` 反查排名，或者用**跳表**（Redis ZSet 的选择）。纯 JDK 里 `TreeSet` + `HashMap` 组合就能做到 `O(log n)` 更新与查询。
- **分数相同怎么排**：必须定义**次级排序键**（如先到先得、用户 ID 字典序），否则同分用户排名不稳定，前端会看到排名乱跳。
- **排名计算**：`TreeSet.headSet(score, false).size()` 是 `O(n)`，不能用在热路径。正确做法是让每个节点维护**子树大小**（跳表/平衡树），或者接受 `O(log n)` 的 `TreeMap` 遍历计数。
- **缓存三大指标**：命中率、淘汰次数、平均访问延迟。案例里要真统计并打印，否则"实现了 LRU"只是自说自话。
- 易错：`get` 命中后忘记移到头部（退化成 FIFO）；`put` 已存在的 key 时没更新值只调整顺序；淘汰时忘记从哈希表删除（内存泄漏）；排行榜同分不稳定；把 `LinkedHashMap` 的 `accessOrder` 忘了设。

**示例代码**
```java
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

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
```

**课后练习**
1. 把 `LruCache` 改造成线程安全版本（用 `ReentrantLock` 保护），并写一个 8 线程并发读写的测试，验证容量始终不超限、无死锁。
2. 实现带过期时间的 LRU（TTL）：`get` 时检查是否过期，过期视为未命中并删除；说明为什么惰性删除不够、还需要什么机制。
3. 给 `Leaderboard` 增加"分页查询第 N 页（每页 10 条）"，要求不遍历整个榜单；再说明数据量到 100 万时该换成什么结构。
4. 对比 LRU 与 LFU 在同一访问序列下的命中率：构造一个"扫表"序列（大量一次性 key）让 LRU 命中率骤降，说明为什么真实缓存常用 LRU-K 或 W-TinyLFU。
