/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：28-08
 * 课名：延迟队列与定时任务的选型
 * ------------------------------------------------------------
 * 知识点：
 *   - 定时任务三种形态：**单机 `@Scheduled`**（简单，多实例会重复执行）、**分布式调度平台**（XXL-JOB/ElasticJob，有分片和失败重试）、**延迟队列**（精确到秒级，适合海量单条任务）。
 *   - 选型看任务特征：固定周期全量扫描（对账、清理）用调度平台；海量单条延迟（订单超时）用延迟队列；少量低频任务用单机定时器 + 分布式锁。
 *   - 延迟队列的三种实现：RabbitMQ 延迟交换机（TTL + DLX）、RocketMQ 延迟等级、Redis ZSet（score 存到期时间戳 + 轮询）。
 *   - Redis ZSet 方案的要点：`ZADD key score member`，消费端 `ZRANGEBYSCORE 0 now LIMIT 0 N` 取到期任务，用 `ZREM` 的返回值（删除成功数）保证多实例下只有一个抢到。
 *   - 定时任务必须幂等：调度平台重试、多实例重叠执行都会导致重复跑。
 *   - 易错：用 `@Scheduled` 做订单超时取消，部署 3 个实例后同一订单被取消 3 次；ZSet 取任务不加 `ZREM` 原子性判断，多实例重复处理。
 * ============================================================
 */

package com.heima.ch28;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

// ==================== 示例代码 ====================

public class DelayQueueDesign {
    /** 用 TreeMap 模拟 Redis ZSet：key=到期时间戳，value=任务列表 */
    static final TreeMap<Long, List<String>> ZSET = new TreeMap<>();

    static void addTask(long dueAt, String taskId) {
        ZSET.computeIfAbsent(dueAt, k -> new ArrayList<>()).add(taskId);
        System.out.println("  加入延迟队列：" + taskId + "，到期时间 +" + dueAt + "ms");
    }

    /** 模拟 ZRANGEBYSCORE + ZREM：只有删除成功的实例才执行 */
    static List<String> pollDue(long now, int limit) {
        List<String> got = new ArrayList<>();
        while (got.size() < limit && !ZSET.isEmpty()) {
            Map.Entry<Long, List<String>> first = ZSET.firstEntry();
            if (first.getKey() > now) {
                break;
            }
            List<String> tasks = first.getValue();
            String task = tasks.remove(0);
            got.add(task);
            if (tasks.isEmpty()) {
                ZSET.remove(first.getKey());
            }
        }
        return got;
    }

    public static void main(String[] args) {
        System.out.println("=== 三种定时方案对比 ===");
        System.out.println("  单机 @Scheduled      ：零依赖，多实例重复执行，需要分布式锁");
        System.out.println("  分布式调度平台       ：分片、失败重试、可视化，适合周期性全量任务");
        System.out.println("  延迟队列（ZSet/DLX） ：秒级精度、海量单条任务，适合订单超时");

        System.out.println();
        System.out.println("=== Redis ZSet 延迟队列 ===");
        addTask(30_000, "ORDER-1:cancel");
        addTask(5_000, "ORDER-2:cancel");
        addTask(30_000, "ORDER-3:cancel");
        addTask(60_000, "COUPON-9:expire");

        long[] ticks = {5_000, 30_000, 60_000};
        for (long now : ticks) {
            System.out.println("  时间 +" + now + "ms 轮询：");
            List<String> due = pollDue(now, 10);
            if (due.isEmpty()) {
                System.out.println("    无到期任务");
            }
            for (String t : due) {
                System.out.println("    实例A 抢到并执行 " + t);
            }
        }

        System.out.println();
        System.out.println("=== 多实例竞争：ZREM 返回值决定谁执行 ===");
        ZSET.clear();
        addTask(1_000, "ORDER-4:cancel");
        System.out.println("  实例A 执行 ZREM -> 返回 1，抢到，执行取消");
        System.out.println("  实例B 执行 ZREM -> 返回 0，说明已被抢走，直接跳过");
        System.out.println("  剩余队列=" + ZSET);
        System.out.println("  注意：任务本身仍要幂等，ZREM 只保证不重复抢，不保证业务只成功一次");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 把 `TreeMap` 换成 `ConcurrentSkipListMap` 并加多线程轮询，验证不会重复取到同一任务。
 * 2. 说明为什么"单机 `@Scheduled` + 3 个实例"会重复执行，给出两种修复方式。
 * 3. 对比 RabbitMQ 延迟交换机与 Redis ZSet 的精度、可靠性、运维成本。
 * ============================================================
 */
