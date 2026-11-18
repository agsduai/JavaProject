/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：30-01
 * 课名：JVM 运行时内存结构
 * ------------------------------------------------------------
 * 知识点：
 *   - **线程私有**三块：**程序计数器**（当前字节码行号，唯一不会 OOM 的区域）、**虚拟机栈**（每个方法一个栈帧：局部变量表、操作数栈、动态链接、返回地址）、**本地方法栈**（Native 方法）。
 *   - **线程共享**两块：**堆**（对象实例与数组，GC 主战场，可 OOM）、**方法区/元空间**（类元信息、常量、静态变量，JDK 8 起用本地内存实现，叫 Metaspace）。
 *   - 栈溢出 `StackOverflowError` 由递归过深或栈帧过大引起，调 `-Xss` 可以改栈大小；堆溢出 `OutOfMemoryError: Java heap space` 由对象持续增长引起，调 `-Xmx`。
 *   - 元空间溢出 `OutOfMemoryError: Metaspace` 常见于动态生成类（CGLIB 代理、反射、热部署），调 `-XX:MaxMetaspaceSize`。
 *   - **直接内存**（NIO 的 `DirectByteBuffer`）不在堆里，不受 `-Xmx` 限制，但受物理内存限制，溢出报 `OutOfMemoryError: Direct buffer memory`。
 *   - 字符串常量池在 JDK 7 之后移到了堆里；`String.intern()` 会往池里放引用，滥用会撑大堆。
 *   - 易错：以为 `-Xmx` 就是进程总内存（还要加元空间、直接内存、线程栈、JVM 自身开销）；把"堆大"当成"性能好"（堆越大，单次 Full GC 越久）。
 * ============================================================
 */

package com.heima.ch30;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.lang.management.MemoryUsage;

// ==================== 示例代码 ====================

public class JvmMemoryLayout {
    public static void main(String[] args) {
        System.out.println("=== JVM 基本信息 ===");
        System.out.println("  Java 版本：" + System.getProperty("java.version"));
        System.out.println("  虚拟机：" + System.getProperty("java.vm.name") + " "
                + System.getProperty("java.vm.version"));
        Runtime rt = Runtime.getRuntime();
        System.out.println("  可用处理器：" + rt.availableProcessors());

        System.out.println();
        System.out.println("=== 堆内存（-Xms / -Xmx 控制）===");
        System.out.println("  最大堆 " + mb(rt.maxMemory()) + "MB");
        System.out.println("  已申请 " + mb(rt.totalMemory()) + "MB");
        System.out.println("  已使用 " + mb(rt.totalMemory() - rt.freeMemory()) + "MB");

        System.out.println();
        System.out.println("=== 内存池明细（堆 / 非堆）===");
        for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
            MemoryUsage u = pool.getUsage();
            String kind = pool.getType() == MemoryType.HEAP ? "堆  " : "非堆";
            System.out.printf("  %s %-24s used=%5dMB committed=%5dMB max=%5dMB%n",
                    kind, pool.getName(), mb(u.getUsed()), mb(u.getCommitted()), mb(u.getMax()));
        }

        System.out.println();
        System.out.println("=== 各区域职责与溢出类型 ===");
        String[][] regions = {
                {"程序计数器", "线程私有", "当前字节码行号", "不会 OOM"},
                {"虚拟机栈", "线程私有", "-Xss", "StackOverflowError（递归过深）"},
                {"本地方法栈", "线程私有", "-Xss", "StackOverflowError"},
                {"堆", "线程共享", "-Xms/-Xmx", "OutOfMemoryError: Java heap space"},
                {"元空间", "线程共享", "-XX:MaxMetaspaceSize", "OutOfMemoryError: Metaspace"},
                {"直接内存", "进程级", "-XX:MaxDirectMemorySize", "OutOfMemoryError: Direct buffer memory"}
        };
        System.out.printf("  %-12s %-10s %-22s %s%n", "区域", "归属", "参数", "溢出表现");
        for (String[] r : regions) {
            System.out.printf("  %-12s %-10s %-22s %s%n", r[0], r[1], r[2], r[3]);
        }

        System.out.println();
        System.out.println("=== 栈帧：方法调用一次压一帧 ===");
        System.out.println("  depth(0) -> " + depth(0) + " 层返回，每层一个栈帧");
        System.out.println("  栈帧内容：局部变量表 + 操作数栈 + 动态链接 + 返回地址");
    }

    /** 演示栈帧压栈与出栈 */
    static int depth(int n) {
        if (n >= 5) {
            return n;
        }
        return depth(n + 1);
    }

    static long mb(long bytes) {
        return bytes < 0 ? -1 : bytes / 1024 / 1024;
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 用 `-Xss256k` 跑一个深度 10000 的递归，观察 `StackOverflowError`，再改回默认值对比。
 * 2. 用 `-Xmx64m` 启动本课示例，观察最大堆输出，并说明为什么进程实际内存大于 64MB。
 * 3. 列出一次方法调用在虚拟机栈上产生的栈帧内容，说明局部变量表里放什么。
 * ============================================================
 */
