/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：81-01
 * 课名：缓存的收益与代价
 * ------------------------------------------------------------
 * 知识点：
 *   - **缓存的本质**：**用内存换时间**，把「每次都算 / 每次都查库」变成「一次算好、多次读取」——**收益 ≈ 命中率 × 单次回源耗时**，命中率 90% 时平均延迟掉到原来的 1/10 量级。
 *   - **三种代价**：**一致性代价**（数据可能旧）、**内存代价**（缓存也是钱）、**复杂度代价**（失效、雪崩、热点都要额外处理）——**缓存不是免费加速，是把「慢」换成「可能不一致」**。
 *   - **什么时候不该加缓存**：**数据量小且查询本来就快**、**强一致且不能容忍旧值**（如余额扣减）、**写多读少**——**读远多于写（10:1 以上）才是缓存的主场**。
 *   - **命中率是第一指标**：**命中率 = 命中次数 / 总请求**，**低于 80% 基本白干**，还白担一致性风险（81-10 讲怎么量）。
 *   - 与 25/38 章的关系：25 章讲 Redis 用法、38 章讲 ES 检索，**本章讲「怎么设计缓存层」**——**模式、故障、一致性、监控**四件套。
 *   - 易错点：只算「快了」不算「旧了」；不看命中率；把缓存当数据库用（缓存丢了就全崩）；TTL 全设同一个值（雪崩）。
 * ============================================================
 */

package com.heima.ch81;

// ==================== 示例代码 ====================

import java.util.LinkedHashMap;
import java.util.Map;

public class CacheCostBenefit {
    record Scenario(String name, double hitRate, double dbMs, double cacheMs,
                    double qps, double gb, double costPerGbMonth) {
    }

    static void compare(Scenario s) {
        double avgNoCache = s.dbMs();
        double avgWithCache = s.hitRate() * s.cacheMs() + (1 - s.hitRate()) * s.dbMs();
        double dbQpsNoCache = s.qps();
        double dbQpsWithCache = s.qps() * (1 - s.hitRate());
        System.out.printf("【%s】命中率 %.0f%%%n", s.name(), s.hitRate() * 100);
        System.out.printf("  平均延迟：%6.1fms -> %6.1fms（快 %.1f 倍）%n",
                avgNoCache, avgWithCache, avgNoCache / avgWithCache);
        System.out.printf("  回源 QPS：%8.0f -> %8.0f（降 %.0f%%）%n",
                dbQpsNoCache, dbQpsWithCache,
                (1 - dbQpsWithCache / dbQpsNoCache) * 100);
        System.out.printf("  内存成本：%.0f GB -> %.0f 元/月%n",
                s.gb(), s.gb() * s.costPerGbMonth());
        System.out.println();
    }

    public static void main(String[] args) {
        compare(new Scenario("商品详情（高命中）", 0.95, 25, 0.6, 20_000, 40, 60));
        compare(new Scenario("订单列表（低命中）", 0.55, 25, 0.6, 8_000, 60, 60));
        compare(new Scenario("余额查询（不该缓存）", 0.30, 8, 0.5, 3_000, 20, 60));

        System.out.println("【判断】");
        Map<String, String> rules = new LinkedHashMap<>();
        rules.put("命中率 < 80%", "先别加缓存：回源太多，收益覆盖不了复杂度");
        rules.put("强一致 + 金额", "不加缓存或只加极短 TTL，用数据库/行锁保证正确");
        rules.put("读:写 > 10:1", "缓存主场，值得投入");
        rules.put("缓存挂了会全崩", "先做降级与限流，再加缓存");
        rules.forEach((k, v) -> System.out.printf("  %-16s %s%n", k, v));

        System.out.println();
        System.out.println("【结论】缓存不是「有就快」，而是「算清楚才快」："
                + "命中率、延迟收益、内存成本、一致性风险，四项一起评估。");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 给你负责的一个接口，估算命中率与「回源 QPS 下降比例」，判断值不值得加缓存。
 * 2. 把某个场景的命中率从 0.95 改成 0.5，观察延迟收益如何塌陷，说明为什么「缓存要设计 key 粒度」。
 * 3. 思考题：为什么「余额扣减」这类写操作不适合 Cache-Aside？从「旧值被读到的后果」分析。
 * ============================================================
 */

