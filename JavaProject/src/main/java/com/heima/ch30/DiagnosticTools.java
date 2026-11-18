/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：30-06
 * 课名：常用排查工具：jps / jstat / jstack / jmap
 * ------------------------------------------------------------
 * 知识点：
 *   - **jps**：列出当前用户的 Java 进程与主类，`-l` 显示完整类名，`-v` 显示 JVM 参数。
 *   - **jstat**：看 GC 与内存池实时数据。`jstat -gcutil <pid> 1000` 每秒打印各分区使用率与 GC 次数/耗时，是判断"是否频繁 Full GC"的首选。
 *   - **jstack**：导出线程栈，定位**死锁**、**CPU 飙高**、**线程阻塞**。`jstack -l <pid>` 会额外打印锁信息，末尾直接给 "Found one Java-level deadlock"。
 *   - **jmap**：`jmap -histo:live <pid>` 看对象数量与占用（快速定位谁在涨）；`jmap -dump:format=b,file=heap.hprof <pid>` 导出堆快照供 MAT 分析。
 *   - **jcmd**：JDK 7+ 的统一入口，`jcmd <pid> VM.flags`、`GC.heap_info`、`Thread.print`、`GC.class_histogram` 都能干，生产推荐优先用 jcmd（jmap 在部分 JDK 版本会 STW）。
 *   - **arthas**：阿里开源诊断工具，`dashboard`、`thread`、`watch`、`trace` 可以不停机定位方法耗时与入参出参。
 *   - 生产注意：`jmap -dump` 会 STW（尤其大堆），要评估影响；`-XX:+HeapDumpOnOutOfMemoryError` 让 JVM 在 OOM 时自动 dump，是必备参数。
 *   - 易错：dump 文件几个 GB 直接拉到本地打开（应该先在服务器上 `jmap -histo` 缩小范围）；只看 CPU 不看 GC（很多"CPU 高"其实是 GC 线程在拼命回收）。
 * ============================================================
 */

package com.heima.ch30;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.lang.management.MemoryUsage;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;

// ==================== 示例代码 ====================

public class DiagnosticTools {
    public static void main(String[] args) throws Exception {
        String pid = ManagementFactory.getRuntimeMXBean().getName();
        System.out.println("=== jps 等价信息 ===");
        System.out.println("  当前进程：" + pid + "（pid@host）");
        System.out.println("  主类：" + DiagnosticTools.class.getName());
        System.out.println("  启动参数：" + ManagementFactory.getRuntimeMXBean().getInputArguments());

        System.out.println();
        System.out.println("=== jstat -gcutil 等价：各分区使用率 ===");
        System.out.printf("  %-26s %6s %6s %6s%n", "内存池", "已用%", "已提交MB", "最大MB");
        for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
            if (pool.getType() != MemoryType.HEAP) {
                continue;
            }
            MemoryUsage u = pool.getUsage();
            double pct = u.getMax() > 0 ? u.getUsed() * 100.0 / u.getMax() : 0;
            System.out.printf("  %-26s %5.1f%% %8d %8d%n", pool.getName(), pct,
                    u.getCommitted() / 1024 / 1024, u.getMax() / 1024 / 1024);
        }
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            System.out.println("  " + gc.getName() + "：YGC/FGC 次数=" + gc.getCollectionCount()
                    + "，累计停顿=" + gc.getCollectionTime() + "ms");
        }

        System.out.println();
        System.out.println("=== jstack 等价：线程状态统计 ===");
        ThreadMXBean tmx = ManagementFactory.getThreadMXBean();
        ThreadInfo[] infos = tmx.dumpAllThreads(false, false);
        java.util.Map<String, Integer> stateCount = new java.util.TreeMap<>();
        for (ThreadInfo ti : infos) {
            stateCount.merge(ti.getThreadState().name(), 1, Integer::sum);
        }
        System.out.println("  总线程数 " + tmx.getThreadCount() + "，峰值 " + tmx.getPeakThreadCount());
        stateCount.forEach((k, v) -> System.out.println("    " + k + " = " + v));

        System.out.println();
        System.out.println("=== 死锁检测（jstack 末尾的 Found one Java-level deadlock）===");
        Object lockA = new Object();
        Object lockB = new Object();
        Thread t1 = new Thread(() -> {
            synchronized (lockA) {
                sleep(50);
                synchronized (lockB) {
                    System.out.println("  t1 拿到两把锁（不会执行）");
                }
            }
        }, "worker-A");
        Thread t2 = new Thread(() -> {
            synchronized (lockB) {
                sleep(50);
                synchronized (lockA) {
                    System.out.println("  t2 拿到两把锁（不会执行）");
                }
            }
        }, "worker-B");
        t1.setDaemon(true);
        t2.setDaemon(true);
        t1.start();
        t2.start();
        sleep(300);

        long[] deadlocked = tmx.findDeadlockedThreads();
        if (deadlocked != null) {
            System.out.println("  检测到 " + deadlocked.length + " 个死锁线程：");
            for (ThreadInfo ti : tmx.getThreadInfo(deadlocked, true, true)) {
                System.out.println("    " + ti.getThreadName() + " 状态=" + ti.getThreadState());
                for (StackTraceElement e : ti.getStackTrace()) {
                    System.out.println("      at " + e);
                }
                System.out.println("    等待锁：" + (ti.getLockName() == null ? "-" : ti.getLockName())
                        + "，持有锁：" + (ti.getLockOwnerName() == null ? "-" : ti.getLockOwnerName()));
            }
        } else {
            System.out.println("  未检测到死锁（本次调度未形成环）");
        }

        System.out.println();
        System.out.println("=== jmap -histo 等价：对象数量 Top ===");
        System.out.println("  用 jmap -histo:live <pid> 查看，输出列：#instances、#bytes、class name");
        System.out.println("  常见可疑对象：byte[]、char[]、HashMap$Node、ArrayList、业务 DTO");
        System.out.println("  若某个业务类实例数持续增长且不下降 -> 内存泄漏嫌疑");

        System.out.println();
        System.out.println("=== 工具速查 ===");
        String[][] tools = {
                {"jps -lvm", "看进程、主类、JVM 参数"},
                {"jstat -gcutil <pid> 1000", "每秒看各分区使用率与 GC 次数"},
                {"jstack -l <pid>", "线程栈 + 死锁检测"},
                {"jmap -histo:live <pid>", "对象直方图，找谁在涨"},
                {"jmap -dump:format=b,file=a.hprof <pid>", "导出堆快照给 MAT"},
                {"jcmd <pid> GC.heap_info", "堆概况（生产优先用 jcmd）"},
                {"arthas trace <class> <method>", "不停机看方法耗时与入参"}
        };
        for (String[] t : tools) {
            System.out.printf("  %-42s %s%n", t[0], t[1]);
        }
    }

    static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 启动示例并在另一个终端执行 `jps -l`、`jstat -gcutil <pid> 1000`，对照代码输出理解每列含义。
 * 2. 用 `jstack -l <pid>` 找到示例中的死锁，读出两个线程各自持有的锁。
 * 3. 说明为什么生产环境优先用 `jcmd` 而不是 `jmap`，`jmap -dump` 有什么风险。
 * ============================================================
 */
