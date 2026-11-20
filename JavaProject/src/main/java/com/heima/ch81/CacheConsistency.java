/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：81-05
 * 课名：缓存一致性：双写与失效
 * ------------------------------------------------------------
 * 知识点：
 *   - **一致性不是「强一致」而是「收敛」**：**允许短暂不一致，但最终必须收敛到库里的值**——**缓存是加速层，数据库是唯一真相源（Single Source of Truth）**。
 *   - **四种更新策略对比**：**先写库再删缓存（推荐）**、**先删缓存再写库（有并发漏洞）**、**先写库再更新缓存（并发写顺序乱）**、**延迟双删（缓解但不彻底）**。
 *   - **为什么「先写库再删缓存」最好**：**删是幂等的**，**并发下最坏结果是「多删一次」导致一次回源**，**不会把旧值永久留在缓存里**。
 *   - **「先删缓存再写库」的漏洞**：线程 A 删缓存 → 线程 B 读未命中、读库拿到旧值 → A 写库 → B 把旧值回填 → **缓存永久是旧值**。
 *   - **「更新缓存」的漏洞**：两个写请求 A、B 并发，A 先写库、B 后写库，但 B 先更新缓存、A 后更新缓存 → **缓存留下 A 的旧值**。
 *   - 易错点：以为「先写库再删缓存」就绝对一致（删失败会留旧值，需要重试/订阅 binlog 兜底）；把 TTL 当一致性方案（TTL 只管最终收敛，不管窗口内错多久）。
 * ============================================================
 */

package com.heima.ch81;

// ==================== 示例代码 ====================

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

public class CacheConsistency {
    static final Map<String, String> DB = new HashMap<>();
    static final Map<String, String> CACHE = new HashMap<>();

    static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    static void reset() {
        DB.clear();
        CACHE.clear();
        DB.put("price:1001", "100");
        CACHE.put("price:1001", "100");
    }

    // 策略一：先写库，再删缓存（推荐）
    static void writeDbThenEvict(String k, String v) {
        DB.put(k, v);
        sleep(30);                 // 写库后、删缓存前的时间窗
        CACHE.remove(k);
    }

    // 策略二：先删缓存，再写库（有并发漏洞）
    static void evictThenWriteDb(String k, String v) {
        CACHE.remove(k);
        sleep(30);
        DB.put(k, v);
    }

    // 策略三：先写库，再更新缓存（并发写顺序乱）
    static void writeDbThenUpdate(String k, String v) {
        DB.put(k, v);
        sleep(30);
        CACHE.put(k, v);
    }

    static String read(String k) {
        String v = CACHE.get(k);
        if (v != null) {
            return v;
        }
        sleep(10);                 // 模拟读库
        v = DB.get(k);
        if (v != null) {
            CACHE.put(k, v);       // 回填
        }
        return v;
    }

    static void runRace(String name, Runnable writer) throws Exception {
        reset();
        AtomicReference<String> observed = new AtomicReference<>("-");
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);

        Thread w = new Thread(() -> {
            try {
                start.await();
                writer.run();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                done.countDown();
            }
        });
        Thread r = new Thread(() -> {
            try {
                start.await();
                sleep(5);           // 读者在写线程刚动手时进入，正好落在时间窗内
                observed.set(read("price:1001"));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                done.countDown();
            }
        });
        w.start();
        r.start();
        start.countDown();
        done.await();

        String cacheNow = CACHE.getOrDefault("price:1001", "(空)");
        String dbNow = DB.get("price:1001");
        String afterRead = read("price:1001");
        System.out.printf("%-16s 并发读到=%-4s 结束时缓存=%-5s 再读一次=%-4s 库=%-4s%n",
                name, observed.get(), cacheNow, afterRead, dbNow);
    }

    // 两个写并发：A 先写库但后更新缓存，B 后写库但先更新缓存 -> 缓存留下 A 的旧值
    static void twoWriters(boolean updateCache) throws Exception {
        reset();
        Thread a = new Thread(() -> {
            DB.put("price:1001", "200");        // A 先写库
            sleep(40);
            if (updateCache) {
                CACHE.put("price:1001", "200");
            } else {
                CACHE.remove("price:1001");
            }
        });
        Thread b = new Thread(() -> {
            sleep(20);
            DB.put("price:1001", "300");        // B 后写库
            if (updateCache) {
                CACHE.put("price:1001", "300"); // B 先更新缓存
            } else {
                CACHE.remove("price:1001");
            }
        });
        a.start();
        b.start();
        a.join();
        b.join();
        System.out.printf("  两写并发(%s)：最终库=%s 缓存=%s -> %s%n",
                updateCache ? "更新缓存" : "删缓存",
                DB.get("price:1001"),
                CACHE.getOrDefault("price:1001", "(空)"),
                updateCache ? "缓存被旧值 200 覆盖 <<<" : "缓存为空，下次读回源拿 300 ✓");
    }

    public static void main(String[] args) throws Exception {
        runRace("写库->删缓存", () -> writeDbThenEvict("price:1001", "200"));
        runRace("删缓存->写库", () -> evictThenWriteDb("price:1001", "200"));
        runRace("写库->更新缓存", () -> writeDbThenUpdate("price:1001", "200"));

        System.out.println();
        System.out.println("【两个写并发】");
        twoWriters(true);
        twoWriters(false);

        System.out.println();
        System.out.println("【结论】");
        System.out.println("  写库->删缓存：并发读者可能读到旧值一次（窗口内），但缓存里没有残留，"
                + "下一次读必然回源拿到新值 -> 收敛");
        System.out.println("  删缓存->写库：读者在写库前读到旧值并回填，缓存里留下旧值，"
                + "再读还是旧值 -> 长期不一致（最坏情况）");
        System.out.println("  写库->更新缓存：两个写并发时后写库的可能先更新缓存，"
                + "缓存被旧值覆盖 -> 不一致");
        System.out.println("  所以：删缓存（不更新）+ TTL 兜底 + 必要时订阅 binlog 补偿删除");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 加第三个线程（两个写请求并发），演示「写库->更新缓存」留下的旧值，打印两次写库的先后顺序。
 * 2. 给「写库->删缓存」加上删除失败重试（失败 3 次后进补偿队列），说明为什么需要兜底。
 * 3. 思考题：为什么「删缓存」是幂等操作而「更新缓存」不是？从「执行次数是否影响最终结果」分析。
 * ============================================================
 */

