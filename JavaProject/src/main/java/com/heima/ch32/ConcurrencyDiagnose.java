/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：32-10
 * 课名：并发问题诊断：死锁、线程转储与性能瓶颈
 * ------------------------------------------------------------
 * 知识点：
 *   - **死锁**四条件：互斥、持有并等待、不可剥夺、循环等待。检测手段：`jstack <pid>`、`jcmd <pid> Thread.print`、`ThreadMXBean.findDeadlockedThreads()`、`jconsole` 的"检测死锁"按钮。
 *   - 死锁的典型代码：两个线程按相反顺序获取两把锁。修复方式：**统一加锁顺序**、用 `tryLock(timeout)` 放弃重试、缩小锁范围。
 *   - **线程转储（thread dump）** 读法：先看线程状态分布（`BLOCKED` 多 = 锁竞争；`WAITING` 多 = 等 IO 或条件；`RUNNABLE` 但 CPU 高 = 死循环），再看 `BLOCKED` 线程的 `waiting to lock` 与持锁者。
 *   - `jstack` 输出的关键字段：线程名、`tid`/`nid`、`java.lang.Thread.State`、`at ...` 调用栈、`- waiting to lock <0x...>`、`- locked <0x...>`、`Found one Java-level deadlock`。
 *   - **CPU 飙高**排查四步：`top` 找进程 → `top -Hp <pid>` 找线程 → `printf '%x' <tid>` 转十六进制 → `jstack <pid> | grep -A 20 <nid>` 定位栈。
 *   - **线程数暴涨**：常见于"每次请求 new Thread"、线程池 `max` 设成 `Integer.MAX_VALUE`、`CachedThreadPool` 在 IO 阻塞时无限扩张。
 *   - **锁竞争严重**的表现：大量线程 `BLOCKED`，`jstack` 里同一把锁反复出现；解决方向是缩小临界区、分段锁、无锁化（CAS/ThreadLocal）。
 *   - 其他常见病：`WAITING` 卡在 `park` 且无超时（可能永久等待）、`TIMED_WAITING` 卡在 socket read（下游没响应）、线程泄漏（线程数只增不减）。
 *   - 工具矩阵：`jps`（找进程）、`jstack`（线程栈）、`jstat -gcutil`（GC）、`jmap -histo`（对象直方图）、`arthas`（在线诊断、`thread -b` 直接找阻塞源头）。
 *   - 易错：只看 CPU 不看线程状态；`jstack` 只抓一次（要间隔抓 3 次看是否同一处卡住）；在生产用 `kill -3` 前不确认日志路径。
 * ============================================================
 */

package com.heima.ch32;

import java.lang.management.LockInfo;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

// ==================== 示例代码 ====================

public class ConcurrencyDiagnose {
    static final Object LOCK_A = new Object();
    static final Object LOCK_B = new Object();

    static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("=== 1. 制造死锁（相反加锁顺序）===");
        CountDownLatch bothStarted = new CountDownLatch(2);
        Thread t1 = new Thread(() -> {
            synchronized (LOCK_A) {
                bothStarted.countDown();
                sleep(100);
                synchronized (LOCK_B) {
                    System.out.println("    t1 拿到两把锁（不该出现）");
                }
            }
        }, "deadlock-t1");
        Thread t2 = new Thread(() -> {
            synchronized (LOCK_B) {
                bothStarted.countDown();
                sleep(100);
                synchronized (LOCK_A) {
                    System.out.println("    t2 拿到两把锁（不该出现）");
                }
            }
        }, "deadlock-t2");
        t1.setDaemon(true);
        t2.setDaemon(true);
        t1.start();
        t2.start();
        bothStarted.await(2, TimeUnit.SECONDS);
        sleep(300);

        System.out.println();
        System.out.println("=== 2. 用 ThreadMXBean 检测死锁 ===");
        ThreadMXBean tmx = ManagementFactory.getThreadMXBean();
        long[] deadlocked = tmx.findDeadlockedThreads();
        if (deadlocked == null) {
            System.out.println("  未检测到死锁");
        } else {
            System.out.println("  检测到 " + deadlocked.length + " 个死锁线程：");
            ThreadInfo[] infos = tmx.getThreadInfo(deadlocked, true, true);
            for (ThreadInfo ti : infos) {
                System.out.println("    线程: " + ti.getThreadName() + " 状态=" + ti.getThreadState());
                LockInfo lock = ti.getLockInfo();
                if (lock != null) {
                    System.out.println("      等待锁: " + lock.getClassName() + "@"
                            + Integer.toHexString(lock.getIdentityHashCode()));
                }
                LockInfo[] held = ti.getLockedSynchronizers();
                for (LockInfo h : held) {
                    System.out.println("      持有锁: " + h.getClassName() + "@"
                            + Integer.toHexString(h.getIdentityHashCode()));
                }
                StackTraceElement[] st = ti.getStackTrace();
                if (st.length > 0) {
                    System.out.println("      栈顶: " + st[0]);
                }
            }
            System.out.println();
            System.out.println("  修复思路：统一加锁顺序 A->B，或改用 tryLock 超时放弃");
        }

        System.out.println();
        System.out.println("=== 3. 修复版：统一顺序 + tryLock 兜底 ===");
        ReentrantLock la = new ReentrantLock();
        ReentrantLock lb = new ReentrantLock();
        CountDownLatch done = new CountDownLatch(2);

        Runnable safeTask = () -> {
            boolean ok1 = false, ok2 = false;
            try {
                ok1 = la.tryLock(100, TimeUnit.MILLISECONDS);
                ok2 = lb.tryLock(100, TimeUnit.MILLISECONDS);
                if (ok1 && ok2) {
                    System.out.println("    " + Thread.currentThread().getName() + " 同时拿到 A、B");
                } else {
                    System.out.println("    " + Thread.currentThread().getName()
                            + " 超时放弃（避免死锁）");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                if (ok2) {
                    lb.unlock();
                }
                if (ok1) {
                    la.unlock();
                }
                done.countDown();
            }
        };
        new Thread(safeTask, "safe-1").start();
        new Thread(safeTask, "safe-2").start();
        done.await(3, TimeUnit.SECONDS);

        System.out.println();
        System.out.println("=== 4. 线程状态快照（自己实现一个简版 jstack）===");
        Thread[] all = new Thread[Thread.activeCount() + 10];
        int n = Thread.enumerate(all);
        System.out.printf("  %-22s %-14s %s%n", "线程名", "状态", "栈顶");
        for (int i = 0; i < n; i++) {
            Thread t = all[i];
            if (t == null || !t.isAlive()) {
                continue;
            }
            StackTraceElement[] st = t.getStackTrace();
            String top = st.length > 0 ? st[0].toString() : "-";
            if (top.length() > 62) {
                top = top.substring(0, 59) + "...";
            }
            System.out.printf("  %-22s %-14s %s%n", cut(t.getName(), 22), t.getState(), top);
        }

        System.out.println();
        System.out.println("=== 5. 锁竞争演示：BLOCKED 从哪来 ===");
        final Object hotLock = new Object();
        CountDownLatch go = new CountDownLatch(1);
        CountDownLatch fin = new CountDownLatch(4);
        for (int i = 1; i <= 4; i++) {
            final int id = i;
            new Thread(() -> {
                try {
                    go.await();
                    synchronized (hotLock) {
                        System.out.println("    线程" + id + " 进入临界区");
                        sleep(120);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    fin.countDown();
                }
            }, "hot-" + i).start();
        }
        go.countDown();
        sleep(60);
        int blocked = 0;
        Thread[] snap = new Thread[Thread.activeCount() + 10];
        int m = Thread.enumerate(snap);
        for (int i = 0; i < m; i++) {
            if (snap[i] != null && snap[i].getState() == Thread.State.BLOCKED) {
                blocked++;
                System.out.println("    观察到 BLOCKED: " + snap[i].getName());
            }
        }
        System.out.println("  当前 BLOCKED 线程数 = " + blocked + "（都在等 hotLock）");
        fin.await(3, TimeUnit.SECONDS);

        System.out.println();
        System.out.println("=== 6. CPU 飙高排查四步 ===");
        String[] steps = {
                "1. top 找到 CPU 最高的进程 PID",
                "2. top -Hp <PID> 找到该进程内 CPU 最高的线程 TID",
                "3. printf '%x' <TID> 把十进制 TID 转成十六进制 nid",
                "4. jstack <PID> | grep -A 30 'nid=0x<nid>' 定位到具体代码行"
        };
        for (String s : steps) {
            System.out.println("  " + s);
        }
        System.out.println("  arthas 一行搞定：thread -n 3（CPU 前 3）、thread -b（找阻塞源头）");

        System.out.println();
        System.out.println("=== 7. 线程状态含义 ===");
        String[][] states = {
                {"NEW", "已创建未 start", "正常"},
                {"RUNNABLE", "运行或等 CPU/IO", "大量且 CPU 高 -> 死循环"},
                {"BLOCKED", "等 synchronized 锁", "多 -> 锁竞争严重"},
                {"WAITING", "wait/park 无超时", "可能永久等待"},
                {"TIMED_WAITING", "sleep/wait(t)/parkNanos", "多 -> 下游慢"},
                {"TERMINATED", "已结束", "正常"}
        };
        for (String[] s : states) {
            System.out.printf("  %-15s %-24s %s%n", s[0], s[1], s[2]);
        }
    }

    static String cut(String s, int len) {
        return s.length() <= len ? s : s.substring(0, len - 3) + "...";
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 用 `jstack` 抓取本课死锁示例的线程转储，找到 `Found one Java-level deadlock` 段落并解释每一行的含义。
 * 2. 写一个"CPU 100% 死循环"的示例，用 `top -Hp` + `jstack` 定位到具体行号，记录完整步骤。
 * 3. 解释为什么"抓一次 jstack 不够"，说明间隔抓 3 次能额外发现什么问题。
 * ============================================================
 */
