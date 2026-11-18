/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：30-03
 * 课名：垃圾回收基础：可达性分析与分代
 * ------------------------------------------------------------
 * 知识点：
 *   - 判断对象死活用**可达性分析**（不是引用计数，引用计数解决不了循环引用）：从 **GC Roots** 出发能走到的对象才存活。
 *   - GC Roots 包括：虚拟机栈中的局部变量、方法区静态属性、常量引用、本地方法栈的 JNI 引用、活跃线程、同步锁持有的对象。
 *   - **分代假说**：绝大多数对象朝生夕死；熬过越多次 GC 的对象越难死。于是堆分成 **Eden + Survivor(×2) + Old**，新生代用复制算法，老年代用标记-清除/标记-整理。
 *   - **对象晋升**：在 Survivor 之间复制一次年龄 +1，达到 `-XX:MaxTenuringThreshold`（默认 15）进老年代；**动态年龄判断**：Survivor 中同龄对象总和超过一半，年龄 ≥ 该值的直接晋升。
 *   - **大对象直接进老年代**（`-XX:PretenureSizeThreshold`），避免在 Eden/Survivor 之间反复复制。
 *   - **Minor GC** 只扫新生代（快，几十毫秒）；**Major/Full GC** 扫全堆（慢，可能秒级，会 STW）。调优的核心目标通常是"减少 Full GC 次数与时长"。
 *   - 易错：以为 `System.gc()` 一定会立刻回收（只是建议，`-XX:+DisableExplicitGC` 可直接忽略）；把"对象不可达"和"对象被回收"划等号（还要看 finalize/虚引用，且实际由 GC 时机决定）。
 * ============================================================
 */

package com.heima.ch30;

import java.util.ArrayList;
import java.util.List;

// ==================== 示例代码 ====================

public class GcBasics {
    /** 模拟一个节点，可形成环 */
    static class Node {
        final String name;
        Node next;

        Node(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    static final List<byte[]> STATIC_HOLDER = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        System.out.println("=== 引用计数解决不了循环引用 ===");
        Node a = new Node("A");
        Node b = new Node("B");
        a.next = b;
        b.next = a;
        System.out.println("  A.next=" + a.next + "，B.next=" + b.next + "，互相引用，计数都不为 0");
        a = null;
        b = null;
        System.out.println("  但 A、B 从 GC Root 已不可达 -> 可达性分析能正确回收");

        System.out.println();
        System.out.println("=== GC Roots 示例 ===");
        String local = "栈上的局部变量（GC Root）";
        STATIC_HOLDER.add(new byte[1024]);
        System.out.println("  1. 虚拟机栈局部变量：" + local);
        System.out.println("  2. 静态属性：" + STATIC_HOLDER.size() + " 个对象被 static 持有");
        System.out.println("  3. 常量池引用、JNI 引用、活跃线程、锁对象");

        System.out.println();
        System.out.println("=== 分代结构 ===");
        System.out.println("  ┌──────────── 新生代 (Young) ────────────┐┌── 老年代 (Old) ──┐");
        System.out.println("  │  Eden  │ Survivor0 │ Survivor1        ││  长期存活对象    │");
        System.out.println("  └────────────────────────────────────────┘└─────────────────┘");
        System.out.println("  默认比例 Eden:S0:S1 = 8:1:1（-XX:SurvivorRatio=8）");

        System.out.println();
        System.out.println("=== 对象晋升过程 ===");
        int age = 0;
        int threshold = 15;
        for (int gc = 1; gc <= 3; gc++) {
            age++;
            System.out.println("  第 " + gc + " 次 Minor GC：存活对象在 S0/S1 间复制，年龄=" + age
                    + (age >= threshold ? " -> 晋升老年代" : " -> 仍在新生代"));
        }
        System.out.println("  动态年龄判断：Survivor 中同龄对象总和 > 50%，该年龄及以上直接晋升");

        System.out.println();
        System.out.println("=== Minor GC vs Full GC ===");
        System.out.println("  Minor GC：只扫新生代，STW 短（几 ms ~ 几十 ms），频率高");
        System.out.println("  Full GC ：扫全堆 + 元空间，STW 长（几百 ms ~ 秒），频率低但危害大");

        System.out.println();
        System.out.println("=== 实测：制造一批短命对象，观察 GC 次数 ===");
        long before = gcCount();
        for (int round = 0; round < 20; round++) {
            List<byte[]> tmp = new ArrayList<>();
            for (int i = 0; i < 200; i++) {
                tmp.add(new byte[1024 * 32]);
            }
            tmp.clear();
        }
        long after = gcCount();
        System.out.println("  分配约 " + (20 * 200 * 32 / 1024) + "MB 短命对象，GC 次数变化 "
                + before + " -> " + after);

        System.out.println();
        System.out.println("=== 大对象直接进老年代 ===");
        byte[] big = new byte[8 * 1024 * 1024];
        System.out.println("  分配 8MB 数组，若超过 PretenureSizeThreshold 直接进老年代，避免反复复制");
        System.out.println("  大对象引用保持：" + (big.length > 0));
    }

    static long gcCount() {
        long sum = 0;
        for (java.lang.management.GarbageCollectorMXBean gc
                : java.lang.management.ManagementFactory.getGarbageCollectorMXBeans()) {
            long c = gc.getCollectionCount();
            sum += c < 0 ? 0 : c;
        }
        return sum;
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 用 `-Xmn32m -XX:+PrintGCDetails`（JDK 8）或 `-Xlog:gc*`（JDK 9+）运行示例，读出 Minor GC 次数与耗时。
 * 2. 解释为什么引用计数无法回收循环引用，而可达性分析可以。
 * 3. 说明"动态年龄判断"在什么情况下会让对象提前晋升，会带来什么后果。
 * ============================================================
 */
