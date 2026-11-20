/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：81-06
 * 课名：延迟双删与 binlog 订阅
 * ------------------------------------------------------------
 * 知识点：
 *   - **延迟双删**：**写库前删一次缓存 → 写库 → 睡 500ms 再删一次**——**第二次删是为了清掉「写库期间被并发读回填的旧值」**，**延迟要大于一次读库 + 回填的耗时**。
 *   - **延迟双删的局限**：**延迟时间靠猜**（读库慢就失效）、**第二次删失败就没兜底**——**所以它只是「降低概率」，不是「保证一致」**。
 *   - **binlog 订阅（canal / Debezium 思路）**：**数据库变更写入 binlog → 订阅组件解析 → 投递到 MQ → 缓存服务消费并删 key**——**业务代码零侵入，且删除可重试、可顺序化**。
 *   - **binlog 方案的优点**：**写路径不关心缓存**（少一次调用）、**删除失败可重试**、**可回放补数据**、**天然支持多级缓存一起失效**。
 *   - **binlog 方案的代价**：**多一个组件要运维**、**有秒级延迟**（不是实时）、**要处理顺序与重复消费（删除幂等所以重复无害）**。
 *   - 易错点：延迟双删的 sleep 写死 100ms（读库 200ms 时完全无效）；binlog 消费失败不重试（缓存永久脏）；以为 binlog 是「强一致」（它仍是最终一致，只是窗口更小更可控）。
 * ============================================================
 */

package com.heima.ch81;

// ==================== 示例代码 ====================


import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;

public class DelayedDoubleDelete {
    static final Map<String, String> DB = new HashMap<>();
    static final Map<String, String> CACHE = new HashMap<>();
    static final List<String> LOG = Collections.synchronizedList(new ArrayList<>());

    static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // 读：命中直接返回；未命中先取库快照，再「慢查询」80ms 后回填。
    // snapshotTaken 用来告诉外部线程：快照已经取到，现在可以改库了。
    static String read(String k, CountDownLatch snapshotTaken) {
        String v = CACHE.get(k);
        if (v != null) {
            LOG.add("读 " + k + " 命中缓存=" + v);
            return v;
        }
        v = DB.get(k);                              // 快照：这一刻库里的值
        if (snapshotTaken != null) {
            snapshotTaken.countDown();
        }
        sleep(80);                                  // 模拟 80ms 慢查询
        if (v != null) {
            CACHE.put(k, v);                        // 回填的是 80ms 前的旧值
            LOG.add("读 " + k + " 未命中，读库=" + v + "，80ms 后回填");
        }
        return v;
    }

    static String read(String k) {
        return read(k, null);
    }

    static void scenarioDelayed(long delayMs) throws InterruptedException {
        DB.clear();
        CACHE.clear();
        LOG.clear();
        DB.put("stock:1", "100");
        CACHE.put("stock:1", "100");

        // 固定时序：删缓存 -> 读者取到旧快照 -> 写库 -> 睡 delayMs -> 第二次删 -> 读者回填
        CACHE.remove("stock:1");
        LOG.add("第 1 次删缓存 stock:1");
        CountDownLatch snapshot = new CountDownLatch(1);
        Thread reader = new Thread(() -> read("stock:1", snapshot));
        reader.start();
        snapshot.await();                           // 读者已经拿到 100，正在 80ms 读库
        DB.put("stock:1", "90");
        LOG.add("写库 stock:1=90");
        sleep(delayMs);                             // 第二次删的延迟
        CACHE.remove("stock:1");
        LOG.add("第 2 次删缓存（延迟 " + delayMs + "ms）");
        reader.join();                              // 等读者把旧值回填完
        String after = read("stock:1");             // 再读一次，看缓存里留下了什么
        boolean converged = String.valueOf(after).equals(DB.get("stock:1"));
        System.out.printf("【延迟双删 delay=%dms】最终读到 %s，缓存=%s，库=%s -> %s%n",
                delayMs, after, CACHE.get("stock:1"), DB.get("stock:1"),
                converged ? "收敛" : "仍是旧值，不一致");
        for (String s : LOG) {
            System.out.println("    " + s);
        }
        System.out.println();
    }

    // ---------- binlog 订阅：业务只写库，缓存由订阅端删 ----------
    static class BinlogSubscriber {
        final LinkedBlockingQueue<String> queue = new LinkedBlockingQueue<>();
        int retries;

        void publish(String key) {
            queue.offer(key);                       // 模拟 binlog 事件
        }

        void consumeOnce(boolean failFirst) {
            String k = queue.poll();
            if (k == null) {
                return;
            }
            if (failFirst) {
                retries++;
                queue.offer(k);                     // 消费失败，重新入队（至少一次语义）
                LOG.add("订阅端删除失败，重新入队 " + k);
                return;
            }
            CACHE.remove(k);
            LOG.add("订阅端删除缓存 " + k + "（幂等，重复无害）");
        }
    }

    public static void main(String[] args) throws InterruptedException {
        scenarioDelayed(50);        // 延迟 50ms < 80ms 读库：第二次删早于回填，旧值留下
        scenarioDelayed(150);       // 延迟 150ms > 80ms 读库：第二次删晚于回填，收敛

        System.out.println("========== binlog 订阅 ==========");
        DB.clear();
        CACHE.clear();
        LOG.clear();
        DB.put("stock:2", "50");
        CACHE.put("stock:2", "50");
        BinlogSubscriber sub = new BinlogSubscriber();

        DB.put("stock:2", "40");                    // 业务只写库，完全不碰缓存
        sub.publish("stock:2");                     // 库变更进 binlog
        sub.consumeOnce(true);                      // 第一次消费失败
        sub.consumeOnce(false);                     // 重试成功
        System.out.println("  最终缓存=" + CACHE.getOrDefault("stock:2", "null")
                + "，库=" + DB.get("stock:2") + "，重试次数=" + sub.retries);
        for (String s : LOG) {
            System.out.println("    " + s);
        }

        System.out.println();
        System.out.println("【选型】延迟双删 = 零额外组件、只是降低概率；binlog = 多一个组件、可重试可回放");
        System.out.println("        对一致性要求高（价格/库存）用 binlog；普通信息类用「删缓存 + TTL」即可");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 把 scenarioDelayed 的读库耗时改成 200ms，测试 delay 多少才能稳定收敛，找出经验公式。
 * 2. 给 BinlogSubscriber 加「重复消费」场景（同一条 binlog 投递两次），验证删除幂等。
 * 3. 思考题：为什么 binlog 方案里「重复消费」是安全的，而「更新缓存」方案里重复消费可能出错？
 * ============================================================
 */

