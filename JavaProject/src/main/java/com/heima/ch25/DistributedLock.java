/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：25-10
 * 课名：分布式锁
 * ------------------------------------------------------------
 * 知识点：
 *   - 为什么需要：单体应用里 `synchronized` 能锁住，但服务部署了 3 个实例后，锁只在各自 JVM 内有效，超卖照样发生。
 *   - 基本要求：互斥（同一时刻只有一个持有者）、防死锁（必须有过期时间）、防误删（只能删自己的锁）、可重入（可选）、高可用。
 *   - 正确加锁：`SET lock:order:1001 <唯一标识> NX PX 30000` —— `NX` 保证只有 key 不存在时才设置，`PX` 保证一定过期。
 *   - 正确解锁：必须用 Lua 脚本"判断 value 是不是自己的，是才删除"，避免 A 的锁超时后被 B 拿到、A 醒来把 B 的锁删了。
 *   - 锁续期（看门狗）：业务执行超过锁过期时间时，后台线程定期续期。Redisson 的 `RLock` 内置这套机制。
 *   - 生产建议直接用 Redisson：`RLock lock = redisson.getLock("order:1001"); lock.lock(); try { ... } finally { lock.unlock(); }`，它处理了可重入、续期、Pub/Sub 唤醒。
 *   - 局限：Redis 主从异步复制，主节点加锁成功后未同步就宕机，从节点升主后锁丢失。强一致场景要用 RedLock 或 ZooKeeper/etcd。
 *   - 易错：`SETNX` + `EXPIRE` 两条命令不是原子的（中间宕机就永久锁死）；解锁不校验持有者；锁的粒度太大导致吞吐骤降。
 * ============================================================
 */

package com.heima.ch25;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

// ==================== 示例代码 ====================

public class DistributedLock {
    // 模拟 Redis：key -> 持有者标识
    static final Map<String, String> REDIS = new ConcurrentHashMap<>();
    static int stock = 10;
    static final AtomicInteger SOLD = new AtomicInteger();

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== 无锁：多实例并发扣库存 ===");
        stock = 10;
        SOLD.set(0);
        Thread[] ts = new Thread[20];
        for (int i = 0; i < ts.length; i++) {
            ts[i] = new Thread(DistributedLock::sellWithoutLock);
        }
        for (Thread t : ts) {
            t.start();
        }
        for (Thread t : ts) {
            t.join();
        }
        System.out.println("  卖出 " + SOLD.get() + " 件，剩余 " + stock + " 件（超卖了）");

        System.out.println();
        System.out.println("=== 有锁：SET NX PX + Lua 解锁 ===");
        stock = 10;
        SOLD.set(0);
        REDIS.clear();
        Thread[] ts2 = new Thread[20];
        for (int i = 0; i < ts2.length; i++) {
            final int no = i;
            ts2[i] = new Thread(() -> sellWithLock("instance-" + no));
        }
        for (Thread t : ts2) {
            t.start();
        }
        for (Thread t : ts2) {
            t.join();
        }
        System.out.println("  卖出 " + SOLD.get() + " 件，剩余 " + stock + " 件（正确）");

        System.out.println();
        System.out.println("=== 加锁命令 ===");
        System.out.println("  SET lock:stock 8f3a-uuid NX PX 30000");
        System.out.println("  NX = 不存在才设置（互斥）；PX = 30 秒后自动过期（防死锁）");

        System.out.println();
        System.out.println("=== 解锁必须用 Lua 校验持有者 ===");
        System.out.println("""
                  if redis.call('GET', KEYS[1]) == ARGV[1] then
                      return redis.call('DEL', KEYS[1])
                  else
                      return 0
                  end
                """);
        System.out.println("  否则：A 超时释放 -> B 拿到锁 -> A 执行完把 B 的锁删了");

        System.out.println();
        System.out.println("=== 看门狗续期 ===");
        System.out.println("  业务耗时 40s > 锁 30s，Redisson 后台每 10s 把过期时间重置为 30s");
        System.out.println("  释放锁后停止续期，避免死锁");
    }

    static void sellWithoutLock() {
        if (stock > 0) {
            sleep(1);                      // 模拟检查与扣减之间的时间窗
            stock--;
            SOLD.incrementAndGet();
        }
    }

    static void sellWithLock(String owner) {
        String key = "lock:stock";
        // 模拟 SET key owner NX PX 30000
        if (REDIS.putIfAbsent(key, owner) != null) {
            return;                        // 没抢到锁，直接放弃（真实场景可重试）
        }
        try {
            if (stock > 0) {
                sleep(1);
                stock--;
                SOLD.incrementAndGet();
            }
        } finally {
            // 模拟 Lua：只有持有者才能删
            REDIS.remove(key, owner);
        }
    }

    static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 为什么 `SETNX` 之后再 `EXPIRE` 是不安全的？写出正确的一条命令。
 * 2. 描述"锁超时后误删别人锁"的完整时序，并写出用 Lua 解决的脚本。
 * 3. Redisson 的看门狗在什么情况下会失效（比如进程被 kill -9）？此时锁会怎样？
 * ============================================================
 */
