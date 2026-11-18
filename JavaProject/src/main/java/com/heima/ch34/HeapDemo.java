/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：34-07
 * 课名：堆与优先队列
 * ------------------------------------------------------------
 * 知识点：
 *   - **堆**是**完全二叉树**，用数组存储：下标 `i` 的左右孩子是 `2i+1` / `2i+2`，父节点是 `(i-1)/2`。完全二叉树"中间没有空洞"，所以数组存不浪费空间，且不需要指针。
 *   - **大顶堆**：每个节点 ≥ 它的孩子，所以堆顶是全局最大值。**小顶堆**反之。JDK 的 `PriorityQueue` 默认是**小顶堆**，要大顶堆得传 `Comparator.reverseOrder()`。
 *   - **上浮（sift up）**用于插入：新元素放数组末尾，然后不断和父节点比较交换，直到满足堆性质。`O(log n)`。
 *   - **下沉（sift down）**用于删除堆顶：把末尾元素换到堆顶，然后不断和较大的孩子比较交换。`O(log n)`。
 *   - **建堆是 `O(n)` 不是 `O(n log n)`**：从最后一个非叶子节点（`n/2 - 1`）往前逐个下沉。越靠近底层的节点越多但下沉距离越短，求和后是线性。这个结论常被考。
 *   - **堆排序**：建大顶堆 → 反复把堆顶换到末尾、堆大小减一、新堆顶下沉。时间 `O(n log n)`、空间 `O(1)`、**不稳定**。
 *   - **`PriorityQueue` 的坑**：不是线程安全的（并发用 `PriorityBlockingQueue`）；不允许 `null`；`iterator()` **不保证有序**（只有 `poll` 才有序）；初始容量小于 1 抛异常；`Comparator` 返回 0 时元素顺序不确定。
 *   - **Top-K 问题**用"大小为 k 的堆"：求最大的 k 个用小顶堆（堆顶是当前 k 个里最小的，来了更大的就替换），时间 `O(n log k)`，空间 `O(k)`。比全排序 `O(n log n)` 更适合海量数据。
 *   - **多路归并**用堆：k 个有序链表/文件归并，堆里放每条链的当前头，每次取最小再补下一个，`O(N log k)`。
 *   - **延迟删除**技巧：要删堆中任意元素时，不真的删（`O(n)` 查找），而是记一个"已删除"计数表，`poll` 出来时如果已删除就丢弃。这是滑动窗口最值、任务调度常用手法。
 *   - 易错：`PriorityQueue` 遍历打印以为有序；用 `-` 号取反做"大顶堆"但数值溢出（`Integer.MIN_VALUE`）；`siftDown` 只比较左孩子忘了右孩子；建堆从 `n-1` 开始（多做了无用功）。
 * ============================================================
 */

package com.heima.ch34;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Random;

// ==================== 示例代码 ====================

public class HeapDemo {
    /** 手写大顶堆 */
    static final class MaxHeap {
        private int[] data = new int[8];
        private int size;

        int size() {
            return size;
        }

        boolean isEmpty() {
            return size == 0;
        }

        private void ensure(int need) {
            if (need > data.length) {
                data = Arrays.copyOf(data, Math.max(need, data.length * 2));
            }
        }

        void add(int v) {
            ensure(size + 1);
            data[size] = v;
            siftUp(size);
            size++;
        }

        int peek() {
            if (size == 0) {
                throw new IllegalStateException("堆为空");
            }
            return data[0];
        }

        int poll() {
            if (size == 0) {
                throw new IllegalStateException("堆为空");
            }
            int top = data[0];
            data[0] = data[--size];
            siftDown(0, size);
            return top;
        }

        private void siftUp(int i) {
            while (i > 0) {
                int parent = (i - 1) >>> 1;
                if (data[parent] >= data[i]) {
                    break;
                }
                swap(i, parent);
                i = parent;
            }
        }

        private void siftDown(int i, int n) {
            while (true) {
                int left = i * 2 + 1;
                int right = left + 1;
                int largest = i;
                if (left < n && data[left] > data[largest]) {
                    largest = left;
                }
                if (right < n && data[right] > data[largest]) {
                    largest = right;
                }
                if (largest == i) {
                    break;
                }
                swap(i, largest);
                i = largest;
            }
        }

        private void swap(int i, int j) {
            int t = data[i];
            data[i] = data[j];
            data[j] = t;
        }

        /** O(n) 建堆：从最后一个非叶子节点往前下沉 */
        static MaxHeap heapify(int[] values) {
            MaxHeap h = new MaxHeap();
            h.data = Arrays.copyOf(values, Math.max(values.length, 1));
            h.size = values.length;
            for (int i = values.length / 2 - 1; i >= 0; i--) {
                h.siftDown(i, h.size);
            }
            return h;
        }

        String raw() {
            return Arrays.toString(Arrays.copyOf(data, size));
        }
    }

    /** 堆排序：原地、O(n log n)、不稳定 */
    static void heapSort(int[] a) {
        int n = a.length;
        for (int i = n / 2 - 1; i >= 0; i--) {
            siftDown(a, i, n);
        }
        for (int end = n - 1; end > 0; end--) {
            int t = a[0];
            a[0] = a[end];
            a[end] = t;
            siftDown(a, 0, end);
        }
    }

    private static void siftDown(int[] a, int i, int n) {
        while (true) {
            int l = i * 2 + 1;
            int r = l + 1;
            int big = i;
            if (l < n && a[l] > a[big]) {
                big = l;
            }
            if (r < n && a[r] > a[big]) {
                big = r;
            }
            if (big == i) {
                return;
            }
            int t = a[i];
            a[i] = a[big];
            a[big] = t;
            i = big;
        }
    }

    /** Top-K：用小顶堆，堆顶是当前 k 个最大里最小的那个 */
    static int[] topK(int[] nums, int k) {
        PriorityQueue<Integer> minHeap = new PriorityQueue<>();
        for (int v : nums) {
            if (minHeap.size() < k) {
                minHeap.offer(v);
            } else if (v > minHeap.peek()) {
                minHeap.poll();
                minHeap.offer(v);
            }
        }
        int[] out = new int[minHeap.size()];
        for (int i = out.length - 1; i >= 0; i--) {
            out[i] = minHeap.poll();
        }
        return out;
    }

    /** 多路归并：k 条有序数组合成一条 */
    static int[] mergeK(int[]... arrays) {
        record Cursor(int[] arr, int idx) { }
        PriorityQueue<Cursor> pq = new PriorityQueue<>(Comparator.comparingInt(c -> c.arr()[c.idx()]));
        int total = 0;
        for (int[] arr : arrays) {
            total += arr.length;
            if (arr.length > 0) {
                pq.offer(new Cursor(arr, 0));
            }
        }
        int[] out = new int[total];
        int p = 0;
        while (!pq.isEmpty()) {
            Cursor c = pq.poll();
            out[p++] = c.arr()[c.idx()];
            if (c.idx() + 1 < c.arr().length) {
                pq.offer(new Cursor(c.arr(), c.idx() + 1));
            }
        }
        return out;
    }

    /** 延迟删除：滑动窗口中位数 / 任意删除场景的通用手法 */
    static final class LazyDeleteHeap {
        private final PriorityQueue<Integer> heap;
        private final Map<Integer, Integer> pending = new HashMap<>();
        private int logicalSize;

        LazyDeleteHeap(boolean max) {
            heap = max ? new PriorityQueue<>(Comparator.reverseOrder()) : new PriorityQueue<>();
        }

        void add(int v) {
            heap.offer(v);
            logicalSize++;
        }

        void remove(int v) {
            pending.merge(v, 1, Integer::sum);
            logicalSize--;
        }

        int peek() {
            while (!heap.isEmpty() && pending.getOrDefault(heap.peek(), 0) > 0) {
                int dead = heap.poll();
                pending.merge(dead, -1, Integer::sum);
            }
            if (heap.isEmpty()) {
                throw new IllegalStateException("逻辑上已空");
            }
            return heap.peek();
        }

        int size() {
            return logicalSize;
        }
    }

    public static void main(String[] args) {
        System.out.println("=== 1. 大顶堆插入与出堆 ===");
        MaxHeap heap = new MaxHeap();
        for (int v : new int[]{35, 10, 50, 20, 5, 60, 15}) {
            heap.add(v);
            System.out.println("  add(" + v + ") 后数组 = " + heap.raw() + "，堆顶=" + heap.peek());
        }
        StringBuilder out = new StringBuilder();
        while (!heap.isEmpty()) {
            out.append(heap.poll()).append(' ');
        }
        System.out.println("  依次出堆（降序）: " + out.toString().trim());

        System.out.println();
        System.out.println("=== 2. O(n) 建堆 vs 逐个插入 ===");
        int[] vals = {3, 9, 2, 1, 4, 5};
        MaxHeap h1 = MaxHeap.heapify(vals.clone());
        System.out.println("  heapify(" + Arrays.toString(vals) + ") 数组 = " + h1.raw());
        MaxHeap h2 = new MaxHeap();
        for (int v : vals) {
            h2.add(v);
        }
        System.out.println("  逐个 add 后数组 = " + h2.raw());
        System.out.println("  两者都满足堆性质，但 heapify 只做 O(n) 次比较。");

        System.out.println();
        System.out.println("=== 3. 堆排序 ===");
        int[] sortMe = {5, 2, 9, 1, 7, 3, 8, 6, 4};
        System.out.println("  排序前: " + Arrays.toString(sortMe));
        heapSort(sortMe);
        System.out.println("  排序后: " + Arrays.toString(sortMe) + "（原地、O(n log n)、不稳定）");

        System.out.println();
        System.out.println("=== 4. Top-K：n=10000 取最大 5 个 ===");
        Random rnd = new Random(7);
        int[] big = new int[10_000];
        for (int i = 0; i < big.length; i++) {
            big[i] = rnd.nextInt(100_000);
        }
        System.out.println("  topK(5) = " + Arrays.toString(topK(big, 5)));
        int[] sortedAll = big.clone();
        Arrays.sort(sortedAll);
        System.out.println("  全排序后最后 5 个 = "
                + Arrays.toString(Arrays.copyOfRange(sortedAll, sortedAll.length - 5, sortedAll.length)));
        System.out.println("  堆方案 O(n log k) 只需 k 个额外空间，不必全排序。");

        System.out.println();
        System.out.println("=== 5. 多路归并 ===");
        int[] a1 = {1, 4, 7, 10};
        int[] a2 = {2, 5, 8, 11, 14};
        int[] a3 = {3, 6, 9, 12};
        System.out.println("  A=" + Arrays.toString(a1) + " B=" + Arrays.toString(a2) + " C=" + Arrays.toString(a3));
        System.out.println("  归并 = " + Arrays.toString(mergeK(a1, a2, a3)));

        System.out.println();
        System.out.println("=== 6. 延迟删除 ===");
        LazyDeleteHeap lazy = new LazyDeleteHeap(true);
        for (int v : new int[]{5, 1, 9, 7, 3}) {
            lazy.add(v);
        }
        System.out.println("  初始最大值 = " + lazy.peek());
        lazy.remove(9);
        System.out.println("  删除 9 后最大值 = " + lazy.peek() + "，逻辑 size=" + lazy.size());
        lazy.remove(7);
        System.out.println("  再删 7 后最大值 = " + lazy.peek());

        System.out.println();
        System.out.println("=== 7. PriorityQueue 的遍历不保证有序 ===");
        PriorityQueue<Integer> pq = new PriorityQueue<>(List.of(5, 1, 9, 7, 3));
        List<Integer> iterated = new ArrayList<>(pq);
        System.out.println("  遍历结果: " + iterated + "（只是数组顺序）");
        List<Integer> polled = new ArrayList<>();
        while (!pq.isEmpty()) {
            polled.add(pq.poll());
        }
        System.out.println("  poll 结果: " + polled + "（才是有序的）");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现 `MedianFinder`：支持 `addNum` 与 `findMedian`，用一个大顶堆 + 小顶堆维护数据流的中位数，要求 `addNum` 为 `O(log n)`。
 * 2. 实现"合并 k 条有序链表"（不是数组），要求时间 `O(N log k)`、空间 `O(k)`，并处理 `null` 链表。
 * 3. 用堆实现一个任务调度器：任务有 `priority` 和 `deadline`，按优先级执行，同优先级按 deadline 先到先服务。
 * 4. 证明"从 `n/2-1` 开始下沉建堆是 `O(n)`"：对 n=1024 统计 `heapify` 的总比较次数，与 `n log n` 对比。
 * ============================================================
 */
