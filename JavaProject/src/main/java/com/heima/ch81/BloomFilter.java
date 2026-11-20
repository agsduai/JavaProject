/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：81-04
 * 课名：空值缓存与布隆过滤器
 * ------------------------------------------------------------
 * 知识点：
 *   - **空值缓存**：**查不到也写一条 `NULL` 进缓存（TTL 60 秒）**——**最简单**的防穿透手段，缺点是**大量随机 key 会把缓存撑爆**，所以 TTL 必须短。
 *   - **布隆过滤器**：**一个位数组 + k 个哈希函数**，**写入时把 k 个位置置 1**，**查询时 k 个位置全为 1 才「可能存在」**——**省内存（1 亿 key 约 114MB @ 1% 误判）**。
 *   - **布隆的两种错误**：**不会漏判**（存在的一定说存在），**可能误判**（不存在的也可能说存在）——**误判率由位数组长度 m、哈希个数 k、元素个数 n 决定**。
 *   - **标准用法**：**请求先过布隆**，说「不存在」直接返回（挡住 99% 的穿透）；说「可能存在」再查缓存/库——**布隆挡在缓存前面，不是替代缓存**。
 *   - **布隆的局限**：**不支持删除**（置 1 的位置无法安全清零，会误伤别的 key）——需要删除时用**计数布隆**或**定期重建**。
 *   - 易错点：布隆和真实数据不同步（新增商品没进布隆，正常请求被挡）；位数组太小导致误判率飙升；把布隆当唯一数据源（它只能回答「可能在不在」）。
 * ============================================================
 */

package com.heima.ch81;

// ==================== 示例代码 ====================

import java.util.BitSet;
import java.util.Random;

public class BloomFilter {
    static class Bloom {
        final BitSet bits;
        final int size;
        final int hashCount;
        int inserted;

        Bloom(int expected, double fpp) {
            // m = -n*ln(p) / (ln2)^2 ; k = m/n * ln2
            double ln2 = Math.log(2);
            this.size = (int) Math.ceil(-expected * Math.log(fpp) / (ln2 * ln2));
            this.hashCount = Math.max(1, (int) Math.round((double) size / expected * ln2));
            this.bits = new BitSet(size);
        }

        static int mix(int h) {
            h ^= h >>> 16;
            h *= 0x85EBCA6B;
            h ^= h >>> 13;
            h *= 0xC2B2AE35;
            h ^= h >>> 16;
            return h;
        }

        int[] positions(String key) {
            int h1 = mix(key.hashCode());
            int h2 = mix(key.hashCode() ^ 0x5BD1E995) | 1;   // 奇数步长，位置更均匀
            int[] pos = new int[hashCount];
            for (int i = 0; i < hashCount; i++) {
                pos[i] = Math.floorMod(h1 + i * h2, size);   // 双重哈希
            }
            return pos;
        }

        void add(String key) {
            for (int p : positions(key)) {
                bits.set(p);
            }
            inserted++;
        }

        boolean mightContain(String key) {
            for (int p : positions(key)) {
                if (!bits.get(p)) {
                    return false;                        // 一定不存在
                }
            }
            return true;                                 // 可能存在（可能误判）
        }

        double fpp() {
            double k = hashCount;
            double n = inserted;
            return Math.pow(1 - Math.exp(-k * n / size), k);   // 理论误判率
        }
    }

    public static void main(String[] args) {
        int expected = 10_000;
        Bloom bloom = new Bloom(expected, 0.01);
        System.out.printf("位数组 %d bit（%.1f KB），哈希 %d 个%n",
                bloom.size, bloom.size / 8.0 / 1024, bloom.hashCount);

        for (int i = 0; i < expected; i++) {
            bloom.add("sku:" + i);
        }

        int miss = 0;
        for (int i = 0; i < expected; i++) {
            if (!bloom.mightContain("sku:" + i)) {
                miss++;                                  // 漏判：绝不该发生
            }
        }
        System.out.printf("已存在 key 的漏判数：%d（必须为 0）%n", miss);

        Random rnd = new Random(42);
        int falsePositive = 0;
        int probes = 100_000;
        for (int i = 0; i < probes; i++) {
            if (bloom.mightContain("ghost:" + rnd.nextInt(1_000_000))) {
                falsePositive++;
            }
        }
        System.out.printf("不存在的 key 误判率：%.2f%%（理论 %.1f%%）%n",
                falsePositive * 100.0 / probes, bloom.fpp() * 100);

        System.out.println();
        System.out.println("【用法】请求 -> 布隆说「不存在」-> 直接返回空（挡住绝大多数穿透）");
        System.out.println("        请求 -> 布隆说「可能存在」-> 查缓存 -> 未命中查库 -> 回填");
        System.out.println("【注意】布隆不支持删除；商品上下架要同步布隆，否则会误挡正常请求");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 把误判率目标从 0.01 改成 0.001，观察位数组膨胀多少倍、哈希个数怎么变。
 * 2. 给 Bloom 加一个「计数布隆」版本（每个位置用 byte 计数），实现 remove 方法。
 * 3. 思考题：布隆过滤器为什么「不会漏判但可能误判」？从哈希碰撞的方向分析。
 * ============================================================
 */

