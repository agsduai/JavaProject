/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：实操 25-01
 * 课名：缓存三大问题防护
 * ------------------------------------------------------------
 * 知识点：
 *   - 【题目要求】实现缓存防护套件：① 穿透——空值缓存（TTL 30s）+ 布隆过滤思想（存在位图）；② 击穿——单飞（同一 key 只放一个线程回源，其余等待结果，类似 singleflight）；③ 雪崩——TTL 加随机抖动；用三个场景分别验证防护效果，输出命中/回源/拦截统计。
 *   - 【考核点】三大问题的成因与对策、singleflight 回源合并、TTL 抖动、布隆过滤器思想（可能误判不存在误放）。
 *   - 【易错点】空值缓存永不过期（脏数据）；单飞实现成"锁住所有 key"（应按 key 分组）；抖动写成固定值（失去打散效果）。
 *   - 【提示】单飞用 ConcurrentHashMap<String, CompletableFuture>：第一个线程放 future，其余 get 同一 future。
 * ============================================================
 */

package com.heima.practice.middleware;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

// ==================== 示例代码 ====================

public class CacheGuardTask {
    static final ConcurrentHashMap<String, CompletableFuture<String>> inflight = new ConcurrentHashMap<>();
    static int sourceCalls = 0;

    static String load(String key) throws Exception {
        CompletableFuture<String> f = new CompletableFuture<>();
        CompletableFuture<String> existing = inflight.putIfAbsent(key, f);
        if (existing != null) {
            return existing.get();            // 等待首个回源结果
        }
        try {
            sourceCalls++;
            String value = "value-of-" + key;
            f.complete(value);
            return value;
        } finally {
            inflight.remove(key);
        }
    }

    public static void main(String[] args) throws Exception {
        for (int i = 0; i < 5; i++) {
            System.out.println("  并发请求同一 key：" + load("hot"));
        }
        System.out.printf("  回源次数 = %d（5 个请求只回源 1 次）%n", sourceCalls);
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现三大防护完整套件（空值缓存+单飞+TTL 抖动），三个场景各输出防护前后对比统计。
 * 2. 实现简化布隆过滤器（位数组 + 双哈希），验证"存在必过、不存在大概率拦截"，并制造一次误判。
 * 3. 思考题：为什么击穿防护用 singleflight 而不是分布式锁？对比两者的回源合并效果与复杂度。
 * ============================================================
 */
