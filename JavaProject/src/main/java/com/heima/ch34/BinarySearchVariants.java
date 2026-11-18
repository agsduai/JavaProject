/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：34-10
 * 课名：二分查找及其变体
 * ------------------------------------------------------------
 * 知识点：
 *   - 二分查找的前提是**有序**（或至少"单调可判定"）。核心是每次排除一半，`O(log n)`。100 万元素最多 20 次比较。
 *   - **区间定义要全程一致**。两种主流写法：**闭区间 `[lo, hi]`**（`while (lo <= hi)`，`hi = mid - 1`）和**左闭右开 `[lo, hi)`**（`while (lo < hi)`，`hi = mid`）。混用必然死循环或漏元素。
 *   - **`mid = (lo + hi) / 2` 会整数溢出**（`lo + hi` 超过 `int` 上限）。正确写法是 `lo + (hi - lo) / 2` 或 `(lo + hi) >>> 1`。这是 JDK 曾修复过的真实 bug。
 *   - **`while (lo <= hi)` 还是 `<`** 取决于区间是闭还是半开。判断标准：如果 `hi` 是"可达的下标"就用 `<=`，如果 `hi` 是"边界外的位置"就用 `<`。
 *   - **四种变体**是二分真正的价值所在，比"找一个数"难得多：
 *   - **找第一个 ≥ target**（lower bound）：即使 `a[mid] == target` 也继续往左收，返回 `lo`。
 *   - **找第一个 > target**（upper bound）：`a[mid] <= target` 时右移。
 *   - **找最后一个 ≤ target**：等价于 `upperBound - 1`。
 *   - **找旋转数组最小值**：判断 `a[mid]` 与 `a[hi]` 的关系决定收缩哪边。
 *   - **二分答案**是二分的进阶用法：当"判定某个值 x 是否可行"是单调的（x 可行则更大/更小也可行），就可以二分 x 本身。典型题：切绳子、运货能力、最小最大值。
 *   - **浮点二分**用固定迭代次数（100 次足够到 1e-30）而不是 `while (hi - lo > 1e-9)`，避免精度死循环。
 *   - **`Arrays.binarySearch` 的坑**：找到返回下标，**找不到返回 `-(插入点) - 1`**（负数），所以判断"是否存在"必须 `>= 0`。很多人写成 `!= -1` 就错了。
 *   - 易错：`mid` 溢出；`lo = mid` 而不是 `mid + 1` 导致死循环；旋转数组没判有序半边；对无序数组二分（结果不可预测）；浮点二分用等值判断。
 * ============================================================
 */

package com.heima.ch34;

import java.util.Arrays;

// ==================== 示例代码 ====================

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

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现 `findPeakElement`：在"任意相邻元素不相等"的数组中找一个峰值下标，要求 `O(log n)`，说明为什么无序数组也能二分。
 * 2. 实现"在二维矩阵中查找目标值"：每行升序、每列升序，要求 `O(m+n)`；再实现"每行升序且下一行首元素大于上一行末元素"的 `O(log(mn))` 版本。
 * 3. 用二分答案解决"分割数组的最大值"：把非负数组分成 k 段，使各段和的最大值最小，返回该最小值。
 * 4. 实现 `sqrt(int x)` 的整数二分版本（返回向下取整），要求不使用 `Math.sqrt`，并处理 `x=0`、`x=1`、`x=Integer.MAX_VALUE`。
 * ============================================================
 */
