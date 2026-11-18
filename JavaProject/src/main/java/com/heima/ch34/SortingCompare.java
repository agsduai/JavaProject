/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：34-09
 * 课名：排序算法对比与稳定性
 * ------------------------------------------------------------
 * 知识点：
 *   - **稳定性**指相等元素排序后相对顺序不变。`Integer` 排序看不出差别，但"先按价格排、再按销量排"这种多关键字排序完全依赖稳定性——不稳定就会把上一次的顺序打乱。
 *   - 稳定排序：冒泡、插入、归并、计数/基数。**不稳定**排序：选择、快速、堆、希尔。JDK 的 `Arrays.sort(int[])` 用**双轴快排**（不稳定），`Arrays.sort(Object[])` 用 **TimSort**（归并变体，稳定）——因为对象数组常常需要多关键字排序。
 *   - **插入排序**在"基本有序"的数据上接近 `O(n)`，所以 TimSort 先找自然有序段（run），再归并。这就是为什么对近乎有序的数组，`Arrays.sort(Object[])` 比快排快。
 *   - **快速排序**的关键在 **partition**：选基准、把小于基准的放左边、大于的放右边。经典坑是"全部相等"时退化成 `O(n²)`，要用**三路快排**（小于/等于/大于）或随机基准。
 *   - **快排最坏 `O(n²)`** 出现在每次基准都是最值时（有序数组 + 取首元素作基准）。随机化基准能把最坏情况概率降到极低，但**不能消除**——真正的"最坏情况保证"要靠归并或堆。
 *   - **归并排序**稳定、`O(n log n)` 恒定，代价是 `O(n)` 额外空间。适合链表排序（链表归并不需要额外数组）和外部排序（大文件分块排完再归并）。
 *   - **堆排序**原地、`O(n log n)` 恒定、不稳定。它的比较次数比快排多（缓存不友好），所以实际比快排慢，但空间 `O(1)` 是优势。
 *   - **计数排序**要求数据范围小且是整数，`O(n+k)`；**基数排序**按位分配收集，`O(d(n+k))`。两者都是**非比较排序**，下界不受 `Ω(n log n)` 约束。
 *   - **比较排序的下界是 `Ω(n log n)`**：n 个元素有 n! 种排列，每次比较只提供 1 bit 信息，需要 `log2(n!) ≈ n log n` 次比较。这条下界决定了不可能有更快的通用比较排序。
 *   - 易错：以为 `Arrays.sort` 对所有类型都稳定；快排递归不判 `lo >= hi` 导致栈溢出；归并时 `buf` 每次 new 导致 GC 压力；`Comparator` 不满足传递性（`a<b, b<c` 但 `a>c`）导致 TimSort 抛 `IllegalArgumentException: Comparison method violates its general contract`。
 * ============================================================
 */

package com.heima.ch34;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

// ==================== 示例代码 ====================

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

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现"链表归并排序"，要求时间 `O(n log n)`、空间 `O(log n)`（只用递归栈），并与数组版归并对比内存占用。
 * 2. 实现随机化快排（随机选基准）与"三数取中"快排，在 100 万个有序数据上对比耗时，说明为什么两者都比"取末位"安全。
 * 3. 用计数排序实现"给 1000 万个 0~255 的字节排序"，统计耗时并与 `Arrays.sort(int[])` 对比。
 * 4. 构造一个让 `Comparator` 违反传递性的例子（如 `a<b, b<c, c<a`），用 `List.sort` 触发 `IllegalArgumentException`，并说明为什么 TimSort 要检查这一点。
 * ============================================================
 */
