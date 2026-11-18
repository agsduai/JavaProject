/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：45-08
 * 课名：租约与分布式锁
 * ------------------------------------------------------------
 * 知识点：
 *   - **锁的本质问题是在不可靠网络上的互斥**：持锁者崩溃但锁还在它名下（其他人永远拿不到）——**租约（lease）解法：锁带 TTL，到期自动失效**。TTL 之内持锁者可用，之外系统自动回收。
 *   - **租约的时钟依赖**：TTL 的计时在服务端（共识系统裁决，可靠）；**客户端基于"剩余租约时间"做的乐观假设依赖本地时钟有界偏移**（Lease Read 的理论基础）。时钟漂移大时要么缩短租约要么放弃乐观。
 *   - **Fencing Token 是防脑裂的最后防线**：锁服务每次发放锁都带**单调递增编号**；下游存储只接受编号更大的请求——即使旧持锁者（GC 停顿后醒来）还以为自己在持锁，它的旧编号请求会被拒绝。**没有 fencing 的分布式锁在高 GC 场景必然出事**。
 *   - **GC 停顿的经典事故链**：A 拿锁 -> A 进入 GC 停顿 10s -> 租约过期锁转给 B -> B 修改数据 -> A 醒来以为自己持锁继续写 -> 覆盖 B。**每一步都"合理"，结局是数据错乱**——fencing 编号让 A 的写入被拒。
 *   - **ZK 锁 vs Redis 锁的取舍**：ZK（45-05）自带公平性、存活性、 fencing 语义（顺序节点编号）——但每次加锁走共识，延迟与吞吐受限；Redis 锁（SETNX+TTL）快但**单实例锁在主从切换时会丢**（RedLock 的争议），需要 fencing 兜底。**结论：性能敏感 + 能容忍偶发失效 -> Redis 锁 + fencing；正确性敏感 -> ZK/etcd 锁**。
 *   - **能不用分布式锁就不用**：很多"用锁"的场景其实是"防重复执行"——幂等键（39-09）、数据库唯一约束、乐观版本号往往更简单可靠。**锁是协调成本最高的同步原语，最后才考虑**。
 *   - 易错点：锁不设 TTL（持锁者崩溃死锁）；锁续约失败还继续执行（应先检查租约有效性）；无 fencing（旧持锁者污染新持锁者的数据）；把锁当业务互斥的万能药（幂等能解决的不用锁）。
 * ============================================================
 */

package com.heima.ch45;

import java.util.LinkedHashMap;
import java.util.Map;

// ==================== 示例代码 ====================

public class LeaseFencing {
    /** 锁授权：租约到期时间 + 单调递增 fencing 编号 */
    record LockGrant(String owner, long expiresAt, int fencingToken) {
    }

    /** 锁服务：租约 + fencing token */
    static class LockService {
        static int tokenSeq = 0;
        final Map<String, LockGrant> held = new LinkedHashMap<>();

        LockGrant acquire(String key, String owner, long ttlMs, long now) {
            LockGrant cur = held.get(key);
            if (cur != null && cur.expiresAt() > now) {
                return null;                     // 仍被持有
            }
            LockGrant g = new LockGrant(owner, now + ttlMs, ++tokenSeq);
            held.put(key, g);
            System.out.printf("  %s 获得锁 %s，fencing=%d，TTL=%dms%n",
                    owner, key, g.fencingToken(), ttlMs);
            return g;
        }
    }

    /** 下游存储：只接受更大 fencing 编号 */
    static class FencedStore {
        int maxToken = 0;

        boolean write(int token, String value) {
            if (token <= maxToken) {
                System.out.printf("  拒绝写入 fencing=%d（当前最大 %d）—— 旧持锁者的过期写入%n",
                        token, maxToken);
                return false;
            }
            maxToken = token;
            System.out.printf("  接受写入 fencing=%d value=%s%n", token, value);
            return true;
        }
    }

    static void gcIncident() {
        System.out.println("【GC 停顿事故链：无 fencing 的悲剧】");
        LockService svc = new LockService();
        FencedStore store = new FencedStore();
        long now = 0;
        LockGrant a = svc.acquire("order-lock", "A", 5000, now);
        System.out.println("  A 拿锁进入业务 -> GC 停顿 8 秒...");
        now = 8000;
        LockGrant b = svc.acquire("order-lock", "B", 5000, now);   // A 租约已过期
        store.write(b.fencingToken(), "B 修改: 库存=18");
        System.out.println("  A 醒来，以为自己还持锁：");
        store.write(a.fencingToken(), "A 修改: 库存=25");
        System.out.println("  无 fencing：A 覆盖了 B —— 数据错乱");
        System.out.println("  有 fencing：A 的旧编号写入被拒绝（上面已演示拒绝路径）");
        System.out.println();
    }

    static void fencingDemo() {
        System.out.println("【fencing 生效现场】");
        FencedStore store = new FencedStore();
        store.write(5, "B 的写入");
        store.write(3, "旧持锁者 A 的迟到写入");
        store.write(6, "C 的合法写入");
        System.out.println("  编号单调 + 下游拒绝旧编号 = 过期者无法破坏现状");
        System.out.println();
    }

    static void alternatives() {
        System.out.println("【先问能不能不用锁】");
        String[][] rows = {
                {"防重复执行", "幂等键 + 唯一约束（39-09）", "无锁，天然可靠"},
                {"防并发覆盖", "乐观版本号 CAS（32 章）", "无锁，冲突重试"},
                {"资源配额互斥", "数据库原子 UPDATE ... WHERE 剩余>0", "无锁，行锁内部化"},
                {"真全局互斥（极少数）", "ZK/etcd 锁 + fencing", "有锁，走共识"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-12s %-32s %s%n", r[0], r[1], r[2]);
        }
        System.out.println("  口诀：幂等 > 版本号 > 数据库约束 > 分布式锁");
    }

    public static void main(String[] args) {
        gcIncident();
        fencingDemo();
        alternatives();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 完整实现租约锁服务（TTL、续约、过期回收）+ FencedStore，重演本章 GC 事故链，验证 fencing 的拦截效果。
 * 2. 把一个你现有的"分布式锁"场景改造成幂等键或乐观版本号方案，对比两者代码量与故障面。
 * 3. 思考题：为什么 Redis 主从切换会"丢锁"？从主从异步复制与锁过期写路径推导，并说明 fencing 如何兜底。
 * ============================================================
 */
