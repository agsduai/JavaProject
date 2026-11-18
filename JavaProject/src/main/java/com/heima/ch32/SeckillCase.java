/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：32-11
 * 课名：综合案例：高并发秒杀链路
 * ------------------------------------------------------------
 * 知识点：
 *   - 秒杀的核心矛盾：**瞬时流量远大于库存与系统容量**。解法是"层层削减"：前端限流 → 网关限流 → 应用层限流 → 缓存预扣 → 异步下单 → 数据库最终扣减。
 *   - **超卖**的根因是"检查库存"与"扣减库存"不是原子操作。单机用 `CAS`/`synchronized`，分布式用 Redis `DECR`（Lua 保证原子）或数据库 `update ... where stock >= n`。
 *   - **限流**三种算法：计数器（有临界问题）、**滑动窗口**（更平滑）、**令牌桶**（允许突发）、**漏桶**（恒定速率）。秒杀常用令牌桶 + 排队。
 *   - **异步下单**：请求先入队（内存队列/MQ），返回"排队中"，消费者按数据库能力匀速消费。这样"削峰"把瞬时 10 万 QPS 摊平成持续 500 QPS。
 *   - **幂等**：同一用户重复点击不能重复下单。用 `userId + activityId` 做唯一键，`putIfAbsent` 占位或数据库唯一索引兜底。
 *   - **库存预扣 + 最终一致**：Redis 预扣成功才允许下单，下单失败要**回补库存**（补偿），并记录流水以便对账。
 *   - 秒杀接口的设计原则：**快失败**（库存没了立刻返回售罄，不排队）、**少依赖**（不在主链路做 RPC）、**可降级**（风控超时直接放行或拒绝，按业务定）。
 *   - 压测要点：关注 **QPS、P99、错误率、库存准确性**四个指标，其中"库存准确性"（是否超卖/少卖）比性能更关键。
 *   - 易错：用"查库存 → if > 0 → 扣减"两步（必然超卖）；限流只在网关做（内网直调绕过）；异步下单没有幂等（重复消费导致重复扣款）；库存回补没做（少卖）。
 * ============================================================
 */

package com.heima.ch32;

import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

// ==================== 示例代码 ====================

public class SeckillCase {
    /** 令牌桶限流：容量 + 匀速补充 */
    static class TokenBucket {
        private final int capacity;
        private final double refillPerMs;
        private double tokens;
        private long lastRefill = System.currentTimeMillis();
        private final AtomicLong passed = new AtomicLong();
        private final AtomicLong rejected = new AtomicLong();

        TokenBucket(int capacity, double refillPerSecond) {
            this.capacity = capacity;
            this.refillPerMs = refillPerSecond / 1000.0;
            this.tokens = capacity;
        }

        synchronized boolean tryAcquire() {
            long now = System.currentTimeMillis();
            tokens = Math.min(capacity, tokens + (now - lastRefill) * refillPerMs);
            lastRefill = now;
            if (tokens >= 1) {
                tokens -= 1;
                passed.incrementAndGet();
                return true;
            }
            rejected.incrementAndGet();
            return false;
        }
    }

    /** 秒杀核心服务 */
    static class SeckillService {
        private final int stock;
        private final AtomicInteger remaining;
        private final Map<String, Boolean> bought = new ConcurrentHashMap<>();
        private final BlockingQueue<String> orderQueue = new ArrayBlockingQueue<>(10000);
        private final AtomicInteger created = new AtomicInteger();
        private final AtomicInteger soldOut = new AtomicInteger();
        private final AtomicInteger duplicate = new AtomicInteger();
        private final AtomicInteger rollback = new AtomicInteger();

        SeckillService(int stock) {
            this.stock = stock;
            this.remaining = new AtomicInteger(stock);
        }

        /** 主链路：幂等 -> 预扣库存 -> 入队；全内存，毫秒级返回 */
        String seckill(long userId, long activityId) {
            String key = userId + ":" + activityId;
            if (bought.putIfAbsent(key, Boolean.TRUE) != null) {
                duplicate.incrementAndGet();
                return "REPEAT";
            }
            while (true) {
                int cur = remaining.get();
                if (cur <= 0) {
                    bought.remove(key);
                    soldOut.incrementAndGet();
                    return "SOLD_OUT";
                }
                if (remaining.compareAndSet(cur, cur - 1)) {
                    break;
                }
            }
            if (!orderQueue.offer(key)) {
                remaining.incrementAndGet();
                bought.remove(key);
                return "QUEUE_FULL";
            }
            return "QUEUED";
        }

        /** 消费者：按数据库能力匀速创建订单 */
        void startConsumers(int n) {
            for (int i = 0; i < n; i++) {
                Thread t = new Thread(() -> {
                    while (true) {
                        try {
                            String key = orderQueue.poll(200, TimeUnit.MILLISECONDS);
                            if (key == null) {
                                if (Thread.currentThread().isInterrupted()) {
                                    return;
                                }
                                continue;
                            }
                            Thread.sleep(1);
                            if (key.hashCode() % 200 == 0) {
                                rollback.incrementAndGet();
                                remaining.incrementAndGet();
                                bought.remove(key);
                            } else {
                                created.incrementAndGet();
                            }
                        } catch (InterruptedException e) {
                            return;
                        }
                    }
                }, "order-consumer-" + i);
                t.setDaemon(true);
                t.start();
            }
        }

        String report() {
            return String.format("库存=%d 剩余=%d 已下单=%d 售罄拒绝=%d 重复=%d 回补=%d 队列=%d",
                    stock, remaining.get(), created.get(), soldOut.get(),
                    duplicate.get(), rollback.get(), orderQueue.size());
        }
    }

    public static void main(String[] args) throws Exception {
        int stock = 100;
        int users = 2000;
        SeckillService svc = new SeckillService(stock);
        svc.startConsumers(4);
        TokenBucket bucket = new TokenBucket(500, 50000);

        System.out.println("=== 秒杀链路：限流 -> 幂等 -> 预扣 -> 异步下单 ===");
        System.out.println("  库存 " + stock + "，模拟 " + users + " 个用户同时抢");
        System.out.println("  其中 200 个用户重复点击 2 次（验证幂等）");

        ExecutorService pool = Executors.newFixedThreadPool(64);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(users + 200);
        Map<String, AtomicInteger> results = new ConcurrentHashMap<>();
        for (String k : new String[]{"QUEUED", "SOLD_OUT", "REPEAT", "LIMITED"}) {
            results.put(k, new AtomicInteger());
        }

        long t0 = System.nanoTime();
        for (int i = 1; i <= users; i++) {
            final long uid = i;
            pool.submit(() -> {
                try {
                    start.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                String r;
                if (!bucket.tryAcquire()) {
                    r = "LIMITED";
                } else {
                    r = svc.seckill(uid, 1001L);
                }
                results.get(r).incrementAndGet();
                done.countDown();
            });
        }
        for (int i = 1; i <= 200; i++) {
            final long uid = i;
            pool.submit(() -> {
                try {
                    start.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                if (bucket.tryAcquire()) {
                    String r = svc.seckill(uid, 1001L);
                    results.get(r).incrementAndGet();
                } else {
                    results.get("LIMITED").incrementAndGet();
                }
                done.countDown();
            });
        }
        start.countDown();
        done.await(30, TimeUnit.SECONDS);
        long ms = (System.nanoTime() - t0) / 1_000_000;
        pool.shutdownNow();

        System.out.printf("%n  请求处理耗时 %d ms%n", ms);
        System.out.println("  结果分布：");
        for (Map.Entry<String, AtomicInteger> e : results.entrySet()) {
            System.out.printf("    %-10s %d%n", e.getKey(), e.getValue().get());
        }
        System.out.println("  限流：放行 " + bucket.passed.get() + "，拒绝 " + bucket.rejected.get());

        System.out.println();
        System.out.println("  等待异步下单消费完...");
        for (int i = 0; i < 60; i++) {
            if (svc.orderQueue.isEmpty()) {
                Thread.sleep(300);
                break;
            }
            Thread.sleep(100);
        }
        System.out.println("  " + svc.report());

        System.out.println();
        System.out.println("=== 正确性校验 ===");
        int created = svc.created.get();
        int remain = svc.remaining.get();
        boolean noOversell = created <= stock;
        boolean consistent = (created + remain + svc.rollback.get()) == stock;
        System.out.println("  是否超卖（已下单 <= 库存）：" + noOversell);
        System.out.println("  库存守恒（已下单 + 剩余 + 回补 == 初始库存）：" + consistent);
        System.out.println("  重复请求被拦截：" + svc.duplicate.get() + " 次");

        System.out.println();
        System.out.println("=== 链路设计要点 ===");
        String[][] points = {
                {"1. 限流", "令牌桶 500 容量 + 5万/秒补充", "挡住明显超量的流量"},
                {"2. 幂等", "userId:activityId 唯一键 putIfAbsent", "重复点击不重复下单"},
                {"3. 预扣", "CAS 循环扣减，扣不动立刻返回售罄", "不会超卖，快失败"},
                {"4. 异步", "入队后立即返回 QUEUED", "把瞬时峰值摊平"},
                {"5. 消费", "4 个消费者匀速建单", "保护数据库"},
                {"6. 补偿", "失败回补库存 + 释放幂等键", "不少卖，可重试"}
        };
        for (String[] p : points) {
            System.out.printf("  %-10s %-40s %s%n", p[0], p[1], p[2]);
        }
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 把 `remaining` 从 `AtomicInteger` 换成 `synchronized` 方法，对比吞吐差异，说明为什么高并发下 CAS 更优。
 * 2. 去掉幂等判断（直接删 `putIfAbsent`），观察"重复用户"是否真的重复下单，统计超卖量。
 * 3. 把令牌桶容量改成 50、补充速率改成 1000/秒，重新跑，说明"限流越紧，售罄越快还是越慢"。
 * 4. 设计"库存回补失败"的兜底方案（提示：本地消息表 + 定时对账），说明为什么不能只靠内存补偿。
 * ============================================================
 */
