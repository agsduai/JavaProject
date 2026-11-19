/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：78-03
 * 课名：幂等消费与去重
 * ------------------------------------------------------------
 * 知识点：
 *   - **至少一次投递 = 必然重复**：**网络超时、ack 丢失、relay 重试**都会导致同一条消息被消费多次——**幂等不是可选项，是事件驱动的前提**。
 *   - **幂等的三种实现**：**去重表**（msgId 唯一索引，处理前插入）、**状态机**（只允许合法状态迁移，重复事件自然被忽略）、**业务唯一键**（订单号 + 操作类型做唯一约束）——**按场景选，最稳的是"去重表 + 状态机"双保险**。
 *   - **并发重复**：两个消费者同时收到同一 msgId，**去重表要能"抢占"**（唯一索引插入成功者处理，失败者等待/跳过）——**先插入再处理，而不是先处理后插入**。
 *   - **处理失败与去重记录**：**处理失败要删去重记录或标记失败**（否则重试被误判为重复）——**"处理中"状态要有超时兜底**。
 *   - 与 39 章的关系：39 章的"接口幂等"用幂等键，**消息幂等用 msgId/业务键**——**同一种思想，不同的入口**。
 *   - 易错点：先处理再记去重（并发重复）；失败也留在去重表（重试被吞）；只靠内存 Set（重启丢失）；去重表不清理（无限增长）。
 * ============================================================
 */

package com.heima.ch78;

import java.util.LinkedHashMap;
import java.util.Map;

// ==================== 示例代码 ====================

public class IdempotentConsumer {
    enum State { PROCESSING, DONE, FAILED }

    record Message(String msgId, String orderId, double amount) {
    }

    static final Map<String, State> dedup = new LinkedHashMap<>();
    static final Map<String, String> results = new LinkedHashMap<>();
    static int processed;

    /** 消费：先占位（插入去重记录），再处理 */
    static String consume(Message msg) {
        State existing = dedup.get(msg.msgId());
        if (existing == State.DONE) {
            System.out.printf("  重复消息 %s -> 直接返回缓存结果%n", msg.msgId());
            return results.get(msg.msgId());
        }
        if (existing == State.PROCESSING) {
            System.out.printf("  并发重复 %s -> 拒绝（另一个消费者处理中）%n", msg.msgId());
            return "SKIP";
        }
        if (existing == State.FAILED) {
            System.out.printf("  上次失败 %s -> 允许重试%n", msg.msgId());
        }

        dedup.put(msg.msgId(), State.PROCESSING);
        try {
            String result = doProcess(msg);
            dedup.put(msg.msgId(), State.DONE);
            results.put(msg.msgId(), result);
            processed++;
            return result;
        } catch (RuntimeException e) {
            dedup.put(msg.msgId(), State.FAILED);
            System.out.println("  处理失败：" + e.getMessage());
            return "FAILED";
        }
    }

    static String doProcess(Message msg) {
        if (msg.amount() < 0) {
            throw new IllegalArgumentException("金额非法");
        }
        System.out.printf("  处理消息 %s：订单 %s 增加积分 %.1f%n",
                msg.msgId(), msg.orderId(), msg.amount());
        return "POINTS+" + msg.amount();
    }

    public static void main(String[] args) {
        Message m1 = new Message("msg-1", "order-1001", 10.0);

        System.out.println("【首次消费】");
        System.out.println("  结果：" + consume(m1));

        System.out.println();
        System.out.println("【重复投递（ack 丢失）】");
        System.out.println("  结果：" + consume(m1));
        System.out.println("  实际业务处理次数：" + processed);

        System.out.println();
        System.out.println("【并发重复：第二个消费者先看到 PROCESSING】");
        dedup.put("msg-2", State.PROCESSING);
        System.out.println("  结果：" + consume(new Message("msg-2", "order-1002", 5.0)));

        System.out.println();
        System.out.println("【处理失败后重试】");
        Message bad = new Message("msg-3", "order-1003", -1);
        System.out.println("  第一次：" + consume(bad));
        System.out.println("  修复后重试：" + consume(new Message("msg-3", "order-1003", 8.0)));

        System.out.println();
        System.out.println("【去重表】");
        dedup.forEach((k, v) -> System.out.printf("  %-8s %s%n", k, v));
        System.out.println("  生产：msgId 唯一索引 + 状态超时回收 + 定期归档");

        System.out.println();
        System.out.println("【三种幂等方式】去重表 / 状态机 / 业务唯一键；并发场景要「先占位」");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现去重表消费：首次/重复/并发/失败重试四场景，验证业务只处理一次。
 * 2. 用状态机实现"订单事件幂等"：重复的 `PAID` 事件不重复扣库存。
 * 3. 思考题：为什么"先处理再记录去重"在并发下会失效？从"检查与执行非原子"分析。
 * ============================================================
 */
