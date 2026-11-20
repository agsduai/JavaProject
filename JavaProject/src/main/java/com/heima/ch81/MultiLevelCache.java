/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：81-09
 * 课名：多级缓存与 TTL 抖动
 * ------------------------------------------------------------
 * 知识点：
 *   - **多级缓存结构**：**本地缓存（L1，纳秒）→ 分布式缓存（L2，百微秒）→ 数据库（L3，毫秒）**——**每层挡住一部分流量，逐级收敛**。
 *   - **每层的容量与 TTL 不同**：**L1 小容量短 TTL（秒级）**、**L2 大容量长 TTL（分钟~小时）**——**TTL 越短越新，越长越省**。
 *   - **TTL 抖动（jitter）**：**TTL = 基准 + random(0, 抖动窗口)**——**把同一批写入的 key 的过期时间摊开**，避免整点雪崩（81-03）。
 *   - **多级缓存的一致性**：**更新时 L2 删除 + 广播让所有节点删 L1**——**广播丢失靠 L1 短 TTL 兜底**，**所以 L1 的 TTL 就是「最大不一致时间」**。
 *   - **穿透率与放大**：**L1 未命中 → L2 未命中 → DB**，**如果 L1 命中率 90%、L2 命中率 90%，DB 只承担 1%**——**这就是多级缓存的乘数效应**。
 *   - 易错点：L1 设 30 分钟 TTL（等于放弃一致性）；只删 L2 不广播（各节点 L1 长期旧）；抖动窗口大于基准 TTL（等于随机过期）。
 * ============================================================
 */

package com.heima.ch81;

// ==================== 示例代码 ====================


import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class MultiLevelCache {
    static final Map<String, String> DB = new HashMap<>();
    static final Map<String, String> L2 = new HashMap<>();
    static final Random RND = new Random(11);
    static final int L1_CAPACITY = 20;

    // L1：容量只有 20 条的小 LRU（accessOrder=true，最久未用的先淘汰）
    static class LocalCache extends LinkedHashMap<String, String> {
        LocalCache() {
            super(16, 0.75f, true);
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
            return size() > L1_CAPACITY;
        }
    }

    static LocalCache L1 = new LocalCache();
    static int l1Hit;
    static int l2Hit;
    static int dbHit;

    static int jitterTtl(int baseSec, int windowSec) {
        return baseSec + RND.nextInt(windowSec + 1);
    }

    static String get(String k, boolean useL1) {
        if (useL1) {
            String v = L1.get(k);
            if (v != null) {
                l1Hit++;
                return v;
            }
        }
        String v = L2.get(k);
        if (v != null) {
            l2Hit++;
            if (useL1) {
                L1.put(k, v);                   // 回填 L1
            }
            return v;
        }
        dbHit++;
        v = DB.get(k);
        if (v != null) {
            L2.put(k, v);
            if (useL1) {
                L1.put(k, v);
            }
        }
        return v;
    }

    // 同一份请求序列跑两遍：一遍只有 L2，一遍 L1+L2
    static int[] run(List<String> seq, boolean useL1) {
        L1 = new LocalCache();
        L2.clear();
        l1Hit = 0;
        l2Hit = 0;
        dbHit = 0;
        for (String k : seq) {
            get(k, useL1);
        }
        return new int[]{l1Hit, l2Hit, dbHit};
    }

    static void invalidate(String k) {
        L2.remove(k);
        L1.remove(k);                           // 单机演示；真实环境要广播给所有节点
        System.out.println("  失效 " + k + " -> L1/L2 均已删除（其他节点靠广播 + L1 短 TTL）");
    }

    public static void main(String[] args) {
        for (int i = 1; i <= 1000; i++) {
            DB.put("sku:" + i, "商品" + i);
        }

        // 请求序列：80% 集中在 50 个热 key（超出 L1 的 20 条容量），20% 长尾
        List<String> seq = new ArrayList<>();
        Random r = new Random(11);
        for (int i = 0; i < 10_000; i++) {
            seq.add(i % 10 < 8
                    ? "sku:" + (1 + r.nextInt(50))
                    : "sku:" + (1 + r.nextInt(1000)));
        }

        int[] onlyL2 = run(seq, false);
        int[] both = run(seq, true);
        int total = seq.size();

        System.out.println("同一份 10000 次请求，跑两遍：");
        System.out.printf("  只有 L2      ：L2 命中 %5d，DB 回源 %5d (%.1f%%)%n",
                onlyL2[1], onlyL2[2], onlyL2[2] * 100.0 / total);
        System.out.printf("  L1(%d 条)+L2 ：L1 命中 %5d (%.1f%%)，L2 命中 %5d，DB 回源 %5d (%.1f%%)%n",
                L1_CAPACITY, both[0], both[0] * 100.0 / total, both[1], both[2],
                both[2] * 100.0 / total);
        System.out.printf("  乘数效应：L1 挡掉 %.1f%%，落到 L2 的请求从 %d 降到 %d（降 %.0f%%）%n",
                both[0] * 100.0 / total, onlyL2[1] + onlyL2[2], both[1] + both[2],
                (1 - (both[1] + both[2]) * 1.0 / (onlyL2[1] + onlyL2[2])) * 100);
        System.out.printf("  注意：DB 回源没变（热 key 本来在 L2 就命中），L1 省的是 Redis 的 QPS 与网络往返%n");

        System.out.println();
        System.out.println("【TTL 抖动】同一批写入的 10 个 key，过期时间分布：");
        StringBuilder sb = new StringBuilder("  ");
        for (int i = 0; i < 10; i++) {
            sb.append(jitterTtl(1800, 300)).append("s ");
        }
        System.out.println(sb.toString().trim());
        System.out.println("  基准 1800s + 0~300s 随机，避免 1800s 整点集中过期");

        System.out.println();
        System.out.println("【更新】价格变更");
        DB.put("sku:1", "商品1-新价");
        invalidate("sku:1");
        System.out.println("  下次读 sku:1 -> " + get("sku:1", true) + "（回源并回填两层）");

        System.out.println();
        System.out.println("【要点】L1 短 TTL 定「最大不一致时间」；L2 长 TTL 省内存；TTL 一律加抖动");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 把 L1_CAPACITY 从 20 调到 60（装得下 50 个热 key），重跑，观察 L1 命中率与「落到 L2 的请求数」如何变化。
 * 2. 给 invalidate 加「广播给 3 个节点」的模拟，演示只删本地不广播时其他节点的旧值。
 * 3. 思考题：为什么 TTL 抖动窗口不能大于基准 TTL？举一个数值例子说明会退化成「随机过期」。
 * ============================================================
 */

