/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：80-07
 * 课名：锁竞争与线程分析
 * ------------------------------------------------------------
 * 知识点：
 *   - **线程状态是排障第一现场**：**RUNNABLE（真在跑/等 CPU）**、**BLOCKED（等 synchronized 锁）**、**WAITING/TIMED_WAITING（等条件/超时）**——**大量 BLOCKED 说明锁竞争，大量 WAITING 可能是下游/队列**。
 *   - **锁竞争的两个指标**：**等待线程数**与**持锁时间**——**持锁时间长的临界区是元凶**（锁内做 IO/RPC 是经典错误）。
 *   - **死锁检测**：**线程 dump 中找"BLOCKED 且互相等待对方持有的锁"**——**JVM 会自动检测并打印 `Found one Java-level deadlock`**（32 章）。
 *   - **减少竞争的手段**：**缩小临界区**（锁内只做内存操作）、**分段锁**（ConcurrentHashMap）、**无锁化**（CAS/不可变）、**线程本地化**（ThreadLocal）——**先测量再选**。
 *   - 与 32 章的关系：32 章讲 Java 并发原语，**本章讲"线上线程 dump 怎么读、锁热点怎么找"**。
 *   - 易错点：看到 CPU 高就加线程（锁竞争更严重）；锁内做 RPC（持锁时间爆炸）；用 `Thread.sleep` 等锁（伪等待）；不看线程 dump 就猜。
 * ============================================================
 */

package com.heima.ch80;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// ==================== 示例代码 ====================

public class LockProfiling {
    record ThreadInfo(String name, String state, String waitingOn, long blockedMs) {
    }

    static void stateSummary(List<ThreadInfo> threads) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (ThreadInfo t : threads) {
            counts.merge(t.state(), 1, Integer::sum);
        }
        System.out.println("【线程状态分布】");
        counts.forEach((k, v) -> System.out.printf("  %-14s %d%n", k, v));
        System.out.println();
    }

    /** 锁热点：按 waitingOn 聚合等待线程数与总阻塞时间 */
    static void hotLocks(List<ThreadInfo> threads) {
        Map<String, Integer> waiters = new LinkedHashMap<>();
        Map<String, Long> blocked = new LinkedHashMap<>();
        for (ThreadInfo t : threads) {
            if (t.waitingOn() == null) {
                continue;
            }
            waiters.merge(t.waitingOn(), 1, Integer::sum);
            blocked.merge(t.waitingOn(), t.blockedMs(), Long::sum);
        }
        System.out.println("【锁热点】");
        waiters.forEach((lock, n) -> System.out.printf(
                "  %-28s 等待线程=%d 累计阻塞=%dms%n", lock, n, blocked.get(lock)));
        System.out.println();
    }

    /** 死锁：A 等 B 的锁、B 等 A 的锁 */
    static void deadlock(List<ThreadInfo> threads) {
        Map<String, String> threadToLock = new LinkedHashMap<>();
        Map<String, String> lockOwner = new LinkedHashMap<>();
        // 示例：直接按线程名与 waitingOn 构造等待图
        Map<String, String> waitsFor = new LinkedHashMap<>();
        waitsFor.put("http-nio-1", "http-nio-2");
        waitsFor.put("http-nio-2", "http-nio-1");

        System.out.println("【死锁检测】");
        Set<String> visited = new LinkedHashSet<>();
        for (String t : waitsFor.keySet()) {
            List<String> path = new ArrayList<>();
            String cur = t;
            while (cur != null && !path.contains(cur)) {
                path.add(cur);
                cur = waitsFor.get(cur);
            }
            if (cur != null) {
                System.out.println("  发现环：" + path + " -> " + cur);
                break;
            }
        }
        System.out.println("  JVM 会打印 Found one Java-level deadlock + 双方持有的锁");
        System.out.println();
    }

    public static void main(String[] args) {
        List<ThreadInfo> threads = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            threads.add(new ThreadInfo("worker-" + i, "RUNNABLE", null, 0));
        }
        for (int i = 0; i < 12; i++) {
            threads.add(new ThreadInfo("http-nio-" + i, "BLOCKED",
                    "com.example.OrderService.lock", 300 + i * 20L));
        }
        for (int i = 0; i < 5; i++) {
            threads.add(new ThreadInfo("db-pool-" + i, "WAITING",
                    "java.util.concurrent.LinkedBlockingQueue.take", 5_000));
        }
        for (int i = 0; i < 3; i++) {
            threads.add(new ThreadInfo("scheduler-" + i, "TIMED_WAITING", null, 0));
        }

        stateSummary(threads);
        hotLocks(threads);
        deadlock(threads);

        System.out.println("【解读】");
        System.out.println("  12 个 http 线程 BLOCKED 在同一把锁 -> 锁竞争严重");
        System.out.println("  5 个 db 线程 WAITING 在队列 -> 连接池空闲（不是瓶颈）");
        System.out.println("  8 个 RUNNABLE -> 正常处理中");
        System.out.println();
        System.out.println("【优化】缩小临界区（锁内不要做 RPC/IO）或分段锁；先看持锁时间最长的代码");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 解析一份线程 dump（状态/等待锁/阻塞时长），统计状态分布与锁热点。
 * 2. 构造两个线程互相等待的锁顺序，检测死锁环并给出修复方案。
 * 3. 思考题：为什么"锁内做 RPC"是性能杀手？从"持锁时间 × 等待线程数"分析。
 * ============================================================
 */
