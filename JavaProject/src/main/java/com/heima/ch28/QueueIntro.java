/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：28-01
 * 课名：为什么需要消息队列
 * ------------------------------------------------------------
 * 知识点：
 *   - 消息队列解决三类问题：**解耦**（生产者不用知道谁在消费、有几个消费者）、**异步**（非核心链路移出主流程，接口 RT 从 100ms 降到 20ms）、**削峰**（瞬时流量先入队，消费者按自己的处理能力匀速取）。
 *   - 同步调用的耦合代价：下单接口里串行调积分、短信、物流，任一环节超时或报错，要么整单失败，要么用户白等；加一个新下游就要改一次主流程代码。
 *   - 异步的代价：**一致性从强变最终**——下单成功那一刻积分可能还没加；**问题定位变难**——链路断在队列里，必须有 traceId 和消费日志；**运维复杂度上升**——多了一个必须高可用的中间件。
 *   - 判断标准：核心链路（扣库存、创建订单）保持同步；可补偿、可延迟、非强一致的旁路（通知、积分、报表、搜索索引）走异步。
 *   - 削峰要看"消费能力"而不是"队列能存多少"：队列只是缓冲，如果消费者长期处理不过来，积压只会无限增长，最终还是要限流或扩容。
 *   - 易错：把"异步"当成"不用管结果"，结果消息丢了没人知道；在事务提交前就发消息，事务回滚了消息却已经投出去（第 28-09 课专门解决）。
 * ============================================================
 */

package com.heima.ch28;

// ==================== 示例代码 ====================

public class QueueIntro {
    static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** 模拟一次 RPC 或数据库操作 */
    static void step(String name, long ms) {
        sleep(ms);
        System.out.println("    " + name + " 完成（" + ms + "ms）");
    }

    public static void main(String[] args) {
        System.out.println("=== 同步串行：下单接口里干完所有事 ===");
        long begin = System.currentTimeMillis();
        step("写订单库", 20);
        step("加积分", 30);
        step("发短信", 40);
        step("通知物流", 30);
        long syncCost = System.currentTimeMillis() - begin;
        System.out.println("  同步总耗时 " + syncCost + "ms，用户一直在等；短信服务一挂，订单也得回滚");

        System.out.println();
        System.out.println("=== 异步解耦：下单只做核心链路，其余投递消息 ===");
        java.util.Deque<String> queue = new java.util.ArrayDeque<>();
        begin = System.currentTimeMillis();
        step("写订单库", 20);
        queue.addLast("order.created:1001");
        long asyncCost = System.currentTimeMillis() - begin;
        System.out.println("  异步总耗时 " + asyncCost + "ms，队列待处理 " + queue.size() + " 条");

        System.out.println();
        System.out.println("=== 削峰：瞬时涌入 20 个请求，消费者每批只处理 5 个 ===");
        for (int i = 1; i <= 20; i++) {
            queue.addLast("order.created:" + (1000 + i));
        }
        System.out.println("  队列积压 " + queue.size() + " 条");
        int batch = 0;
        while (!queue.isEmpty()) {
            batch++;
            int handled = 0;
            for (int i = 0; i < 5 && !queue.isEmpty(); i++) {
                queue.pollFirst();
                handled++;
            }
            System.out.println("  第 " + batch + " 批处理 " + handled + " 条，剩余 " + queue.size() + " 条");
        }
        System.out.println("  峰值被摊平，数据库不会被瞬时流量打穿");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 把示例里的"加积分"改成必须成功才允许下单，说明此时还能不能用异步，为什么。
 * 2. 统计同步流程与异步流程的耗时差，并解释这个差值在什么情况下会反过来（异步反而更慢）。
 * 3. 设计一个判断表：列出你熟悉的 5 个业务动作，逐个标注"必须同步 / 可以异步 / 只能异步"。
 * ============================================================
 */
