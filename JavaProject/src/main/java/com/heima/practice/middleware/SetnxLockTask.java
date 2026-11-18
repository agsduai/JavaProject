/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：实操 25-02
 * 课名：分布式锁与看门狗
 * ------------------------------------------------------------
 * 知识点：
 *   - 【题目要求】实现迷你分布式锁（内存模拟 Redis SETNX+TTL）：acquire（SET NX EX 语义，key 不存在才设置）+ TTL 过期自动释放 + 续约看门狗（持有期间定时续 TTL）+ fencing 编号（45-08 复用）；场景：A 拿锁后"假死"超过 TTL -> B 获得锁 -> A 醒来续约失败（锁已易主）必须终止业务。
 *   - 【考核点】SET NX EX 原子性、TTL 与看门狗续约、锁易主后的客户端自觉（续约失败即放弃）、fencing 防过期写入。
 *   - 【易错点】SETNX 与 EXPIRE 分两步（非原子，挂了变死锁）；续约失败继续执行（资损/错乱）；无 fencing（过期者覆盖新持锁者）。
 *   - 【提示】锁记录存（owner, expireAt, fencingToken）；续约校验 owner 一致才续。
 * ============================================================
 */

package com.heima.practice.middleware;

import java.util.HashMap;
import java.util.Map;

// ==================== 示例代码 ====================

public class SetnxLockTask {
    static final Map<String, String> store = new HashMap<>();

    static synchronized boolean setnx(String key, String value, long ttlMs, long now) {
        String prev = store.get(key);
        if (prev != null && Long.parseLong(prev.split(":")[1]) > now) {
            return false;                      // 未过期 -> 获取失败
        }
        store.put(key, value + ":" + (now + ttlMs));
        return true;
    }

    public static void main(String[] args) {
        long now = 0;
        System.out.println("  A 获取：" + setnx("lock", "A", 1000, now));
        System.out.println("  B 获取（未过期）：" + setnx("lock", "B", 1000, now + 500));
        System.out.println("  B 获取（已过期）：" + setnx("lock", "B", 1000, now + 1500));
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现完整分布式锁（原子 SETNX + 看门狗续约 + fencing），重演"假死->易主->续约失败"时间线。
 * 2. 实现可重入（同 owner 同 key 计数），验证重入次数与释放匹配。
 * 3. 思考题：为什么看门狗续约失败后"必须终止业务"而不是继续？结合 fencing 被拒的下游证据分析。
 * 
 * 
 * ## 第26章 Spring Boot 进阶
 * ============================================================
 */
