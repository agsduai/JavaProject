/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：32-06
 * 课名：并发容器：ConcurrentHashMap 与 CopyOnWrite
 * ------------------------------------------------------------
 * 知识点：
 *   - `HashMap` 线程不安全：并发 `put` 可能丢数据、JDK 8 之前还会形成环形链表导致 CPU 100%。多线程必须换 `ConcurrentHashMap`。
 *   - `ConcurrentHashMap`（JDK 8+）：**CAS + synchronized 锁单个桶头节点**，锁粒度是"一个桶"，不是整表。扩容时多线程协助迁移（`helpTransfer`）。
 *   - `size()` 是**估算值**（`baseCount` + `CounterCell[]` 分散计数），并发下不精确；`mappingCount()` 返回 `long`，更适合大表。
 *   - 复合操作必须用原子方法：`putIfAbsent`、`computeIfAbsent`、`computeIfPresent`、`merge`、`replace(k, old, new)`。`if (!map.containsKey(k)) map.put(k, v)` 是典型的"检查-执行"竞态。
 *   - `ConcurrentHashMap` **不允许 null 键值**（`HashMap` 允许），因为 `get` 返回 null 无法区分"不存在"和"值为 null"。这也是它能在并发下无歧义判断的原因。
 *   - `CopyOnWriteArrayList`：写时复制整个数组，读操作完全无锁。适合**读多写极少**（如监听器列表、配置白名单），写多会疯狂复制，性能极差。
 *   - `CopyOnWriteArraySet` 基于前者实现；`Collections.synchronizedList` 是全局锁，并发度远低于 COW（读也互斥）。
 *   - 弱一致性迭代器：`ConcurrentHashMap` 的迭代器不抛 `ConcurrentModificationException`，但**不保证看到最新数据**，也不保证是某一时刻的快照。
 *   - 易错：用 `size()` 做业务判断（并发下不准）；在 `computeIfAbsent` 的映射函数里修改同一个 map（可能死循环/抛异常）；对 `CopyOnWriteArrayList` 做高频写；用 `Collections.synchronizedMap` 替代 CHM 还期待高并发。
 * ============================================================
 */

package com.heima.ch32;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

// ==================== 示例代码 ====================

public class ConcurrentContainers {
    static final int THREADS = 16;
    static final int PER_THREAD = 2000;

    /** 并发累加：对比 HashMap 与 ConcurrentHashMap */
    static int runWithMap(Map<String, Integer> map, boolean safe) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch done = new CountDownLatch(THREADS);
        for (int t = 0; t < THREADS; t++) {
            pool.submit(() -> {
                for (int i = 0; i < PER_THREAD; i++) {
                    if (safe) {
                        map.merge("count", 1, Integer::sum);
                    } else {
                        Integer old = map.get("count");
                        map.put("count", old == null ? 1 : old + 1);
                    }
                }
                done.countDown();
            });
        }
        done.await(30, TimeUnit.SECONDS);
        pool.shutdownNow();
        Integer v = map.get("count");
        return v == null ? 0 : v;
    }

    public static void main(String[] args) throws Exception {
        int expected = THREADS * PER_THREAD;
        System.out.println("=== 1. HashMap 并发累加（预期 " + expected + "）===");
        int bad = runWithMap(new HashMap<>(), false);
        System.out.println("  HashMap 结果 = " + bad + "，丢失 " + (expected - bad) + " 次");
        System.out.println("  原因：get-put 是复合操作，中间被其他线程插队");

        System.out.println();
        System.out.println("=== 2. ConcurrentHashMap + merge（原子）===");
        int good = runWithMap(new ConcurrentHashMap<>(), true);
        System.out.println("  ConcurrentHashMap 结果 = " + good + "，误差 " + (expected - good));

        System.out.println();
        System.out.println("=== 3. 原子方法：computeIfAbsent 只创建一次 ===");
        ConcurrentHashMap<String, List<String>> groups = new ConcurrentHashMap<>();
        AtomicInteger created = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch done = new CountDownLatch(8);
        for (int t = 0; t < 8; t++) {
            final int id = t;
            pool.submit(() -> {
                for (int i = 0; i < 100; i++) {
                    groups.computeIfAbsent("g1", k -> {
                        created.incrementAndGet();
                        return Collections.synchronizedList(new ArrayList<>());
                    }).add("t" + id + "-" + i);
                }
                done.countDown();
            });
        }
        done.await(10, TimeUnit.SECONDS);
        pool.shutdownNow();
        System.out.println("  computeIfAbsent 里创建 List 的次数 = " + created.get() + "（应为 1）");
        System.out.println("  列表元素数 = " + groups.get("g1").size() + "（应为 800）");

        System.out.println();
        System.out.println("=== 4. putIfAbsent 实现幂等占位 ===");
        ConcurrentHashMap<String, String> idempotent = new ConcurrentHashMap<>();
        AtomicInteger success = new AtomicInteger();
        AtomicInteger duplicate = new AtomicInteger();
        ExecutorService pool2 = Executors.newFixedThreadPool(8);
        CountDownLatch done2 = new CountDownLatch(8);
        for (int t = 0; t < 8; t++) {
            pool2.submit(() -> {
                for (int i = 0; i < 200; i++) {
                    if (idempotent.putIfAbsent("REQ-001", "processing") == null) {
                        success.incrementAndGet();
                    } else {
                        duplicate.incrementAndGet();
                    }
                }
                done2.countDown();
            });
        }
        done2.await(10, TimeUnit.SECONDS);
        pool2.shutdownNow();
        System.out.println("  首次占位成功 = " + success.get() + "（应为 1），被判定重复 = " + duplicate.get());

        System.out.println();
        System.out.println("=== 5. 不允许 null 键值 ===");
        ConcurrentHashMap<String, String> chm = new ConcurrentHashMap<>();
        try {
            chm.put("k", null);
        } catch (NullPointerException e) {
            System.out.println("  put(k, null) -> NullPointerException（get 返回 null 才有唯一含义）");
        }
        System.out.println("  HashMap 允许 null：" + new HashMap<String, String>() {{
            put("k", null);
        }}.containsKey("k"));

        System.out.println();
        System.out.println("=== 6. CopyOnWriteArrayList：读无锁 ===");
        CopyOnWriteArrayList<String> cow = new CopyOnWriteArrayList<>(List.of("A", "B", "C"));
        Thread reader = new Thread(() -> {
            long t0 = System.nanoTime();
            int sum = 0;
            for (int i = 0; i < 100000; i++) {
                for (String s : cow) {
                    sum += s.length();
                }
            }
            System.out.printf("  读 100000 轮耗时 %.1f ms（读无锁，sum=%d）%n",
                    (System.nanoTime() - t0) / 1e6, sum);
        }, "reader");
        Thread writer2 = new Thread(() -> {
            for (int i = 0; i < 5; i++) {
                cow.add("W" + i);
                System.out.println("    写入 W" + i + "，当前大小 " + cow.size());
            }
        }, "writer");
        reader.start();
        writer2.start();
        reader.join();
        writer2.join();
        System.out.println("  迭代过程中写入不抛 ConcurrentModificationException");
        System.out.println("  代价：每次写都复制整个数组，写多时性能极差");

        System.out.println();
        System.out.println("=== 7. 容器选型 ===");
        String[][] t = {
                {"键值并发读写", "ConcurrentHashMap", "桶级锁 + CAS"},
                {"读多写极少列表", "CopyOnWriteArrayList", "写时复制，读无锁"},
                {"高频写列表", "ConcurrentLinkedQueue", "无锁链表"},
                {"需要有序并发映射", "ConcurrentSkipListMap", "跳表，O(log n)"},
                {"简单同步包装", "Collections.synchronizedXxx", "全局锁，并发度低"}
        };
        for (String[] r : t) {
            System.out.printf("  %-22s -> %-28s %s%n", r[0], r[1], r[2]);
        }
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 用 `computeIfPresent` 实现"库存扣减，扣到 0 时移除键"，说明为什么不能用 `get` + `put`。
 * 2. 对比 `CopyOnWriteArrayList` 与 `Collections.synchronizedList` 在"1 写 100 读"场景下的耗时，解释差异来源。
 * 3. 说明 `ConcurrentHashMap.size()` 在并发下为什么不准，并给出一个不依赖精确 size 的限流实现思路。
 * ============================================================
 */
