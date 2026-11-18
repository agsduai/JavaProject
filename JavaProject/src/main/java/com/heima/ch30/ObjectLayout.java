/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：30-02
 * 课名：对象创建与内存布局
 * ------------------------------------------------------------
 * 知识点：
 *   - 对象创建流程：类加载检查 → 分配内存（**指针碰撞**或**空闲列表**）→ 初始化零值 → 设置对象头 → 执行构造方法。
 *   - 内存布局：**对象头**（Mark Word + 类型指针）、**实例数据**、**对齐填充**（8 字节对齐）。
 *   - **Mark Word** 复用存储：哈希码、GC 分代年龄、锁标志位、偏向线程 ID。这就是"锁升级"能复用同一块内存的原因。
 *   - **指针压缩**（`-XX:+UseCompressedOops`，64 位默认开启）：对象引用从 8 字节压到 4 字节，堆小于 32GB 时生效，能省 30%~50% 内存。
 *   - **TLAB**（Thread Local Allocation Buffer）：每个线程在 Eden 里预占一小块，分配对象不用加锁，是"分配飞快"的关键。
 *   - **逃逸分析**：对象没逃出方法，就可能被**栈上分配**或**标量替换**，甚至整个对象被拆成几个局部变量。这解释了"为什么小对象不一定进堆"。
 *   - 对象大小估算：`对象头 12B（压缩后）+ 字段 + 对齐`。一个只有 `int` 字段的对象通常 16B；加一个 `long` 变 24B。
 *   - 易错：堆开到 40GB 却不知道指针压缩失效（内存反而更紧张）；用 `Integer` 当计数器（每个都是对象）；在循环里创建大量短命大对象，把 Eden 冲爆。
 * ============================================================
 */

package com.heima.ch30;

// ==================== 示例代码 ====================

public class ObjectLayout {
    /** 只有 int：12B 头 + 4B 字段 = 16B（正好对齐） */
    static class IntHolder {
        int value;
    }

    /** 12B 头 + 8B long = 20B，对齐到 24B */
    static class LongHolder {
        long value;
    }

    /** 12B 头 + 4B int + 8B long = 24B，对齐到 24B */
    static class MixedHolder {
        int a;
        long b;
    }

    /** 12B 头 + 4B 引用（压缩）= 16B */
    static class RefHolder {
        Object ref;
    }

    public static void main(String[] args) {
        System.out.println("=== 对象内存布局 ===");
        System.out.println("  | Mark Word (8B) | 类型指针 (4B, 压缩后) | 实例数据 | 对齐填充 |");
        System.out.println("  64 位未压缩时类型指针 8B，对象头 16B；压缩后对象头 12B");

        System.out.println();
        System.out.println("=== 常见对象大小估算 ===");
        System.out.printf("  %-14s %-8s %-8s %s%n", "类型", "头部", "字段", "估算总大小");
        System.out.printf("  %-14s %-8s %-8s %s%n", "IntHolder", "12B", "4B", "16B");
        System.out.printf("  %-14s %-8s %-8s %s%n", "LongHolder", "12B", "8B", "24B（20B 对齐）");
        System.out.printf("  %-14s %-8s %-8s %s%n", "MixedHolder", "12B", "12B", "24B");
        System.out.printf("  %-14s %-8s %-8s %s%n", "RefHolder", "12B", "4B", "16B");

        System.out.println();
        System.out.println("=== 实测：不同字段组合的分配开销 ===");
        int n = 1_000_000;
        measure("IntHolder", n, () -> {
            IntHolder[] arr = new IntHolder[n];
            for (int i = 0; i < n; i++) {
                arr[i] = new IntHolder();
            }
            return arr.length;
        });
        measure("LongHolder", n, () -> {
            LongHolder[] arr = new LongHolder[n];
            for (int i = 0; i < n; i++) {
                arr[i] = new LongHolder();
            }
            return arr.length;
        });
        measure("MixedHolder", n, () -> {
            MixedHolder[] arr = new MixedHolder[n];
            for (int i = 0; i < n; i++) {
                arr[i] = new MixedHolder();
            }
            return arr.length;
        });

        System.out.println();
        System.out.println("=== 装箱 vs 基本类型：内存差 4 倍以上 ===");
        long before = used();
        int[] prim = new int[n];
        for (int i = 0; i < n; i++) {
            prim[i] = i;
        }
        long primCost = used() - before;
        before = used();
        Integer[] boxed = new Integer[n];
        for (int i = 0; i < n; i++) {
            boxed[i] = i;
        }
        long boxedCost = used() - before;
        System.out.println("  int[" + n + "] 约 " + primCost / 1024 + "KB");
        System.out.println("  Integer[" + n + "] 约 " + boxedCost / 1024 + "KB（每个对象都有对象头）");
        System.out.println("  合计引用保留：" + (prim.length + boxed.length));

        System.out.println();
        System.out.println("=== 逃逸分析：对象没逃出方法就可以被优化掉 ===");
        int sum = 0;
        for (int i = 0; i < 100_000; i++) {
            sum += localSum(i, i + 1);
        }
        System.out.println("  循环内创建临时对象 10 万次，sum=" + sum + "（JIT 可做标量替换，不真的分配）");
        System.out.println("  触发条件：对象不逃逸 + 方法被 JIT 编译（热点代码）");

        System.out.println();
        System.out.println("=== TLAB：分配不加锁 ===");
        System.out.println("  每个线程在 Eden 预占一块 TLAB，new 对象先在自己的 TLAB 里指针碰撞");
        System.out.println("  TLAB 用完才需要 CAS 申请新块 -> 这就是多线程下分配依然很快的原因");
    }

    /** 对象不逃逸：只在方法内使用 */
    static int localSum(int a, int b) {
        int[] pair = {a, b};
        return pair[0] + pair[1];
    }

    static long used() {
        Runtime rt = Runtime.getRuntime();
        System.gc();
        return rt.totalMemory() - rt.freeMemory();
    }

    interface Alloc {
        int run();
    }

    static void measure(String name, int n, Alloc alloc) {
        long begin = System.nanoTime();
        int size = alloc.run();
        long cost = (System.nanoTime() - begin) / 1_000_000;
        System.out.printf("  分配 %s x%d 耗时 %dms，返回 %d%n", name, n, cost, size);
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 用 `-XX:-UseCompressedOops` 重启示例，对比引用字段对象的估算大小变化。
 * 2. 给 `MixedHolder` 调整字段顺序（`long` 放前面），说明为什么总大小可能变小。
 * 3. 解释为什么 `Integer[]` 比 `int[]` 占用的内存远不止 4 倍，从对象头与对齐的角度分析。
 * ============================================================
 */
