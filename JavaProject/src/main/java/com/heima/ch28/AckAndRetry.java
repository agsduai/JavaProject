/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：28-04
 * 课名：消费确认、重试与死信队列
 * ------------------------------------------------------------
 * 知识点：
 *   - **自动 ack**：消息一投出就从 Broker 删除，消费者拿到后进程崩溃 → 消息永久丢失。只适合"丢了也无所谓"的场景（日志、埋点）。
 *   - **手动 ack**：业务处理成功后调用 `basicAck`；失败调用 `basicNack`（`requeue=true` 重回队列，或 `false` 进死信）。
 *   - 重回队列要小心：立刻 requeue 会让失败消息瞬间又回到队首，形成"毒消息"死循环，通常配合延迟重试（重试队列 / 延迟交换机）。
 *   - **死信队列（DLQ）**：超过最大重试次数、被拒绝、TTL 过期的消息转入 DLQ，由人工或补偿任务处理，避免无限重试拖垮消费者。
 *   - 消费端幂等是前提：手动 ack 在"业务成功但 ack 前宕机"时会重复投递，所以消费者必须能承受重复（28-05）。
 *   - 易错：ack 写在业务逻辑之前；重试次数存在内存里（重启就归零）；死信队列没人看，等于换个地方丢消息。
 * ============================================================
 */

package com.heima.ch28;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

// ==================== 示例代码 ====================

public class AckAndRetry {
    static final Map<String, Integer> ATTEMPTS = new HashMap<>();
    static final int MAX_RETRY = 3;
    static final Deque<String> MAIN = new ArrayDeque<>();
    static final Deque<String> RETRY = new ArrayDeque<>();
    static final Deque<String> DEAD = new ArrayDeque<>();

    /** 模拟业务处理：消息里带 fail 就失败 */
    static boolean handle(String msg) {
        int times = ATTEMPTS.merge(msg, 1, Integer::sum);
        boolean ok = !msg.contains("fail");
        System.out.println("    处理 " + msg + " 第 " + times + " 次 => " + (ok ? "成功，ack" : "失败，nack"));
        return ok;
    }

    /** 处理失败后决定去向：还能重试进重试队列，超限进死信 */
    static void route(String msg) {
        if (ATTEMPTS.get(msg) < MAX_RETRY) {
            RETRY.addLast(msg);
        } else {
            DEAD.addLast(msg);
        }
    }

    public static void main(String[] args) {
        MAIN.add("ORDER-1001:ok");
        MAIN.add("ORDER-1002:fail");
        MAIN.add("ORDER-1003:ok");
        System.out.println("=== 手动 ack：成功才确认，失败进重试 ===");
        while (!MAIN.isEmpty()) {
            String msg = MAIN.pollFirst();
            if (!handle(msg)) {
                route(msg);
            }
        }
        System.out.println("  第一轮结束：重试队列=" + RETRY + "，死信队列=" + DEAD);

        int round = 1;
        while (!RETRY.isEmpty()) {
            System.out.println("  第 " + round + " 轮延迟重试（模拟 2s 后再投，避免毒消息打转）");
            int size = RETRY.size();
            for (int i = 0; i < size; i++) {
                String msg = RETRY.pollFirst();
                if (!handle(msg)) {
                    route(msg);
                }
            }
            round++;
        }
        System.out.println("  重试耗尽，死信队列=" + DEAD + "，转人工或补偿任务处理");
        System.out.println("  尝试次数记录：" + ATTEMPTS);

        System.out.println();
        System.out.println("=== 对比：自动 ack 的丢失场景 ===");
        System.out.println("  消息投出即删除 -> 消费者进程被 kill -> Broker 无副本 -> 消息永久丢失");
        System.out.println("  手动 ack 也可能重复：业务已提交、ack 前宕机 -> 重新投递 -> 必须靠幂等兜住");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 给示例加上"重试队列延迟 N 秒后再投"的时间字段，并说明为什么不能立即 requeue。
 * 2. 实现一个死信告警：死信队列长度超过阈值时打印告警日志。
 * 3. 分析"业务成功但 ack 失败"与"ack 成功但业务失败"两种异常，分别会导致什么后果。
 * ============================================================
 */
