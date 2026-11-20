/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：81-08
 * 课名：缓存预热与降级
 * ------------------------------------------------------------
 * 知识点：
 *   - **缓存预热**：**上线/重启/大促前，把确定会被访问的数据主动写进缓存**——**避免「冷启动瞬间全部回源」**（发布后 1 分钟打挂库的经典事故）。
 *   - **预热的三种做法**：**启动时批量加载**（适合小数据量）、**定时任务跑热点清单**（适合可预测热点）、**流量回放/影子流量预热**（适合大促前，80-08）。
 *   - **降级（Degrade）**：**缓存不可用时，用「本地缓存 / 静态兜底值 / 直接限流」代替正常路径**——**降级的目标不是「功能完好」，而是「不雪崩」**。
 *   - **降级的触发条件**：**缓存错误率 > 阈值**、**缓存 RT > 阈值**、**连接池耗尽**、**手动开关（大促值守）**——**要能自动触发，也要能手動一键**。
 *   - **降级的分级**：**L1 只读降级**（写路径保留、读走兜底）、**L2 功能降级**（关掉非核心模块）、**L3 限流熔断**（保住核心链路）——**按影响面从小到大**。
 *   - 易错点：预热把整个库全加载（内存爆、启动慢）；降级后没恢复机制（一直降级）；降级兜底值不设过期（永远返回旧值）。
 * ============================================================
 */

package com.heima.ch81;

// ==================== 示例代码 ====================


import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CacheWarmupDegrade {
    static final Map<String, String> DB = new HashMap<>();
    static final Map<String, String> REDIS = new HashMap<>();
    static final Map<String, String> LOCAL = new HashMap<>();

    static int dbCalls;
    static boolean redisDown;
    static final int DB_MS = 2;                     // 单次读库 2ms

    static void init() {
        for (int i = 1; i <= 20; i++) {
            DB.put("sku:" + i, "商品" + i);
        }
    }

    static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    static String loadFromDb(String k) {
        dbCalls++;
        sleep(DB_MS);
        return DB.get(k);
    }

    // ---------- 预热：按热点清单批量灌缓存 ----------
    static void warmupByHotList(List<String> hotKeys) {
        long t0 = System.nanoTime();
        int loaded = 0;
        for (String k : hotKeys) {
            String v = loadFromDb(k);
            if (v != null) {
                REDIS.put(k, v);
                loaded++;
            }
        }
        long ms = (System.nanoTime() - t0) / 1_000_000;
        System.out.printf("【预热】按热点清单加载 %d 个 key 到 Redis，耗时 %dms（单次读库 %dms），DB 调用 %d 次%n",
                loaded, ms, DB_MS, dbCalls);
    }

    // ---------- 正常读 + 降级读 ----------
    static String read(String k, boolean degrade) {
        if (!redisDown) {
            String v = REDIS.get(k);
            if (v != null) {
                return v + "(redis)";
            }
            v = loadFromDb(k);
            if (v != null) {
                REDIS.put(k, v);
            }
            return v == null ? null : v + "(db->redis)";
        }
        if (degrade) {
            String v = LOCAL.get(k);
            if (v != null) {
                return v + "(local-degraded)";
            }
            return "默认兜底文案(degraded)";        // 静态兜底，绝不打库
        }
        return loadFromDb(k) + "(db-no-degrade)";   // 未降级：全部打库
    }

    public static void main(String[] args) {
        init();
        List<String> hot = List.of("sku:1", "sku:2", "sku:3", "sku:4", "sku:5");

        dbCalls = 0;
        warmupByHotList(hot);

        System.out.println();
        System.out.println("【冷启动对比】");
        dbCalls = 0;
        for (String k : hot) {
            read(k, false);
        }
        System.out.printf("  预热后读 5 个热点：DB 调用 %d 次%n", dbCalls);

        REDIS.clear();
        dbCalls = 0;
        for (String k : hot) {
            read(k, false);
        }
        System.out.printf("  不预热读 5 个热点：DB 调用 %d 次（冷启动放大）%n", dbCalls);

        System.out.println();
        System.out.println("【放大估算】发布后第 1 秒，2 万 QPS 全部未命中 -> 2 万次读库");
        System.out.println("  连接池 100、单次读库 20ms -> 上限 = 100 / 0.02s = 5000 次/秒");
        System.out.println("  2 万 QPS 是上限的 4 倍 -> 排队、超时、连接池耗尽，第一秒就把库打挂");
        System.out.println("  -> 预热的意义：上线第一秒就有 90% 命中，回源只剩 2000，落在 5000 的上限内");

        System.out.println();
        System.out.println("【降级】缓存集群宕机，本地缓存兜底");
        LOCAL.putAll(REDIS);
        redisDown = true;
        dbCalls = 0;
        for (String k : hot) {
            System.out.println("  " + k + " -> " + read(k, true));
        }
        System.out.printf("  降级后 DB 调用 %d 次（应为 0）%n", dbCalls);

        dbCalls = 0;
        for (String k : hot) {
            read(k, false);
        }
        System.out.printf("  不降级时 DB 调用 %d 次（可能打挂库）%n", dbCalls);

        System.out.println();
        System.out.println("【降级分级】L1 只读降级 -> L2 关非核心 -> L3 限流熔断");
        System.out.println("【要点】预热要有热点清单；降级要能自动触发 + 手动开关 + 自动恢复");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 把预热改成「全库加载 1 万个 key」，估算启动耗时与内存占用，说明为什么只预热热点。
 * 2. 给降级加一个「错误率 > 5% 自动开启、连续 60 秒正常自动关闭」的状态机。
 * 3. 思考题：为什么降级兜底要返回「默认文案」而不是「抛异常」？从用户体验与雪崩传播分析。
 * ============================================================
 */

