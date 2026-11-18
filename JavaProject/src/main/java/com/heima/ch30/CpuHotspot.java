/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：30-08
 * 课名：CPU 飙高与线程状态分析
 * ------------------------------------------------------------
 * 知识点：
 *   - 排查顺序：`top -Hp <pid>` 找到占 CPU 最高的**线程 id**（十进制）→ 转十六进制 → `jstack <pid> | grep -A 30 <nid>` 找到该线程的栈 → 看它卡在哪个方法。
 *   - 线程状态要分清：**RUNNABLE**（在跑或在等 CPU）、**BLOCKED**（等 synchronized 锁）、**WAITING/TIMED_WAITING**（`wait`/`park`/`sleep`）、**NEW/TERMINATED**。
 *   - "CPU 高"通常有三类：**死循环/正则回溯**（RUNNABLE 且栈顶是自己的业务方法）、**频繁 GC**（栈顶是 `GCTaskThread`，要转去看 GC 日志）、**大量线程上下文切换**（线程数暴涨、`BLOCKED` 多）。
 *   - "CPU 不高但接口慢"更常见：线程都在 `WAITING` 等下游（连接池耗尽、锁竞争、`sleep` 式重试）。这时候看的是**线程池活跃数**与**等待队列长度**，不是 CPU。
 *   - **线程数不是越多越好**：CPU 密集型的合理线程数 ≈ 核数 + 1；IO 密集型 ≈ 核数 × (1 + 等待时间/计算时间)，但受下游连接池上限约束。
 *   - 易错：拿 `jstack` 只抓一次（瞬时快照可能刚好错过）；用 `grep` 匹配线程名而不是 nid（nid 才是线程号）；看到 `RUNNABLE` 就以为在执行业务代码（可能在读 socket，属于 IO 等待但状态仍是 RUNNABLE）。
 * ============================================================
 */

package com.heima.ch30;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

// ==================== 示例代码 ====================

public class CpuHotspot {
    static volatile boolean running = true;
    static volatile long sink;

    public static void main(String[] args) throws Exception {
        ThreadMXBean tmx = ManagementFactory.getThreadMXBean();
        if (tmx.isThreadCpuTimeSupported() && !tmx.isThreadCpuTimeEnabled()) {
            tmx.setThreadCpuTimeEnabled(true);
        }
        System.out.println("=== 模拟一个「CPU 飙高」的线上进程 ===");
        System.out.println("  biz-hot-1 / biz-hot-2：业务线程里写了死循环（反例）");
        System.out.println("  idle-worker：正常轮询线程，大部分时间在 sleep");
        System.out.println("  cpu-time-supported=" + tmx.isThreadCpuTimeSupported()
                + "，cpu-time-enabled=" + tmx.isThreadCpuTimeEnabled());

        Thread hot1 = new Thread(() -> spin("biz-hot-1"), "biz-hot-1");
        Thread hot2 = new Thread(() -> spin("biz-hot-2"), "biz-hot-2");
        Thread idle = new Thread(() -> {
            while (running) {
                sleep(50);
            }
        }, "idle-worker");
        for (Thread t : new Thread[]{hot1, hot2, idle}) {
            t.setDaemon(true);
            t.start();
        }

        sleep(300);
        System.out.println();
        System.out.println("=== 第一步：按 CPU 时间排序，找最热的线程 ===");
        long[] ids = {hot1.threadId(), hot2.threadId(), idle.threadId()};
        long[] before = cpuSnapshot(tmx, ids);
        long wallStart = System.nanoTime();
        sleep(500);
        long wallMs = (System.nanoTime() - wallStart) / 1_000_000;
        long[] after = cpuSnapshot(tmx, ids);

        List<Row> rows = new ArrayList<>();
        for (int i = 0; i < ids.length; i++) {
            long deltaMs = (after[i] - before[i]) / 1_000_000;
            double pct = wallMs > 0 ? deltaMs * 100.0 / wallMs : 0;
            rows.add(new Row(ids[i], threadName(tmx, ids[i]), deltaMs, pct));
        }
        rows.sort(Comparator.comparingDouble((Row r) -> r.cpuPct).reversed());
        System.out.printf("  %-8s %-14s %10s %8s%n", "nid", "线程名", "CPU时间ms", "占用率");
        for (Row r : rows) {
            System.out.printf("  %-8s %-14s %10d %7.1f%%%n",
                    Long.toHexString(r.id), r.name, r.cpuMs, r.cpuPct);
        }

        System.out.println();
        System.out.println("=== 第二步：把最热线程的 nid 转十六进制，去 jstack 里找 ===");
        Row hottest = rows.get(0);
        System.out.println("  top -Hp <pid> 看到十进制 tid=" + hottest.id
                + " -> 十六进制 nid=0x" + Long.toHexString(hottest.id));
        System.out.println("  jstack <pid> | grep -A 30 'nid=0x" + Long.toHexString(hottest.id) + "'");
        System.out.println("  该线程当前状态与栈顶：");
        ThreadInfo ti = tmx.getThreadInfo(hottest.id, 8);
        if (ti != null) {
            System.out.println("    \"" + ti.getThreadName() + "\" " + ti.getThreadState()
                    + (ti.getLockName() == null ? "" : " on " + ti.getLockName()));
            for (StackTraceElement e : ti.getStackTrace()) {
                System.out.println("      at " + e);
            }
        }
        System.out.println("  栈顶是业务方法 -> 死循环/算法问题；栈顶是 GCTaskThread -> 去看 GC");
        System.out.println("  （JVM 内部线程名：GC Thread#、G1 Young RemSet Sampling、C2 CompilerThread）");

        System.out.println();
        System.out.println("=== 第三步：线程状态分布 ===");
        ThreadInfo[] all = tmx.dumpAllThreads(false, false);
        int runnable = 0, blocked = 0, waiting = 0, timed = 0, other = 0;
        for (ThreadInfo info : all) {
            switch (info.getThreadState()) {
                case RUNNABLE -> runnable++;
                case BLOCKED -> blocked++;
                case WAITING -> waiting++;
                case TIMED_WAITING -> timed++;
                default -> other++;
            }
        }
        System.out.println("  RUNNABLE=" + runnable + " BLOCKED=" + blocked
                + " WAITING=" + waiting + " TIMED_WAITING=" + timed + " 其他=" + other);
        System.out.println("  总线程数=" + tmx.getThreadCount() + "，峰值=" + tmx.getPeakThreadCount());
        System.out.println("  判读：RUNNABLE 多 + 业务栈 -> CPU 型；WAITING 多 -> 等下游型");

        System.out.println();
        System.out.println("=== 第四步：线程池该开多大 ===");
        int cores = Runtime.getRuntime().availableProcessors();
        System.out.println("  本机核数：" + cores);
        System.out.println("  CPU 密集型：核数 + 1 = " + (cores + 1));
        int ioThreads = cores * (1 + 200 / 20);
        System.out.println("  IO 密集型（等待 200ms / 计算 20ms）：核数 x (1 + 10) = " + ioThreads);
        System.out.println("  但还要受下游连接池上限约束：线程再多，连接只有 20 个，多出来的都在排队");

        running = false;
        hot1.join(100);
        hot2.join(100);
        idle.join(100);
        System.out.println();
        System.out.println("  已停止模拟线程，sink=" + (sink != 0));
    }

    /** 反例：死循环占满一个核 */
    static void spin(String tag) {
        long x = tag.hashCode();
        while (running) {
            x = x * 6364136223846793005L + 1442695040888963407L;
            sink = x;
        }
    }

    static long[] cpuSnapshot(ThreadMXBean tmx, long[] ids) {
        long[] out = new long[ids.length];
        for (int i = 0; i < ids.length; i++) {
            out[i] = tmx.getThreadCpuTime(ids[i]);
        }
        return out;
    }

    static String threadName(ThreadMXBean tmx, long id) {
        ThreadInfo info = tmx.getThreadInfo(id);
        return info == null ? "unknown" : info.getThreadName();
    }

    static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    record Row(long id, String name, long cpuMs, double cpuPct) {
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 用 `jstack` 抓取示例进程，找到 `biz-hot-1` 的 nid 与栈顶方法，写出完整命令。
 * 2. 造一个 20 个线程抢同一把锁的例子，观察 `BLOCKED` 数量与 `jstack` 里的 `waiting to lock`。
 * 3. 一个 8 核机器跑 IO 密集型任务（等待 90ms、计算 10ms），估算线程数上限，并说明为什么实际还要打折。
 * ============================================================
 */
