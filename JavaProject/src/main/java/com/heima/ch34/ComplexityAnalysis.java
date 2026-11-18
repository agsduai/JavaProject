/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：34-01
 * 课名：复杂度分析与均摊分析
 * ------------------------------------------------------------
 * 知识点：
 *   - **大 O 记号**描述的是"输入规模 n 增大时，操作次数增长的量级"，不是精确次数。写 `O(2n+3)` 没意义，直接写 `O(n)`。它衡量的是**趋势**，所以 n 很小时常数项会主导，测不出差别。
 *   - 常见增长阶从快到慢：`O(1)` < `O(log n)` < `O(n)` < `O(n log n)` < `O(n²)` < `O(2ⁿ)` < `O(n!)`。工程上要警惕的是**跨阶**：把 `O(n²)` 优化成 `O(n log n)` 才有质变，`O(n)` 里省一半常数只是量变。
 *   - **对数阶从哪来**：每一步把问题规模砍半（二分查找、归并排序的层数、平衡树的高度、快速幂）。看到"每次减半"就该想到 `log n`。
 *   - **最好 / 最坏 / 平均**要分开说。快速排序平均 `O(n log n)`、最坏 `O(n²)`；哈希表平均 `O(1)`、最坏 `O(n)`（全部冲突）。面试和评审时只报平均复杂度是耍流氓。
 *   - **空间复杂度**同样重要：归并排序 `O(n)` 额外空间，快排 `O(log n)` 递归栈，原地堆排序 `O(1)`。用空间换时间是常见取舍，但要有上限意识。
 *   - **均摊分析（amortized）**：单次操作可能很贵，但把一串操作平均下来很便宜。动态数组扩容那一次是 `O(n)`，但每插入一个元素平均只搬移常数次，所以 `add` 均摊 `O(1)`。注意"均摊"不等于"每次都快"——某一次请求真的会卡。
 *   - **嵌套循环不等于 `O(n²)`**：要看内层循环的边界。`for (i=0;i<n;i++) for (j=i;j<n;j++)` 是 `n(n+1)/2`，仍是 `O(n²)`；但 `for (i=0;i<n;i++) for (j=0;j<32;j++)` 是 `O(n)`，因为内层是常数。
 *   - **字符串拼接是隐藏的 `O(n²)`**：`s += x` 每次都新建数组并复制，循环 n 次就是 `O(n²)`。用 `StringBuilder`。
 *   - **递归的复杂度**要写递归式：归并排序 `T(n)=2T(n/2)+O(n)` → `O(n log n)`；斐波那契朴素递归 `T(n)=T(n-1)+T(n-2)` → `O(2ⁿ)`，加记忆化降到 `O(n)`。
 *   - 易错：拿小数据量（n=100）测性能就下结论；只看平均复杂度忽略最坏情况（哈希碰撞攻击就是打最坏）；把 `O(n log n)` 的排序套在已经有序的数据上还以为很快（插入排序此时是 `O(n)`）。
 * ============================================================
 */

package com.heima.ch34;

import java.util.Arrays;

// ==================== 示例代码 ====================

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

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 写一个方法 `countTriples(int[] a)`，统计满足 `i<j<k` 且 `a[i]+a[j]+a[k]==0` 的三元组个数，先用三重循环实现并统计操作次数，再尝试用"排序 + 双指针"降到 `O(n²)`，对比 n=200 时的操作次数。
 * 2. 分别实现"每次扩容 +1"和"每次扩容 ×2"的动态数组，插入 10000 个元素，打印累计搬移次数，解释为什么前者是 `O(n²)` 而后者均摊 `O(1)`。
 * 3. 用递归实现斐波那契并统计调用次数，画出 n=10/20/30 的增长倍数；再加一个 `long[] memo` 缓存，比较调用次数差异。
 * 4. 判断下列代码的时间复杂度并说明理由：`for (int i = 1; i < n; i *= 2) for (int j = 0; j < i; j++) sum++;`
 * ============================================================
 */
