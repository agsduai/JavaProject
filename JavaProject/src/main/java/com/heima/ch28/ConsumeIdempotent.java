/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：28-05
 * 课名：消费幂等：重复消息怎么办
 * ------------------------------------------------------------
 * 知识点：
 *   - 至少一次（at-least-once）投递必然带来重复：网络超时重投、消费者 ack 前宕机、Broker 主从切换都可能让同一条消息被消费两次。MQ 普遍不提供"恰好一次"，业务侧必须自己做幂等。
 *   - 幂等的判定基准是**业务唯一键**，不是消息 ID：同一条消息可能被重新包装（重试时 msgId 变了），但"订单号 + 事件类型"永远唯一。
 *   - 三种落地方式：**去重表**（唯一索引，插入成功才处理）、**状态机**（只允许 `待支付 → 已支付`，重复的"已支付"事件直接丢弃）、**Redis SETNX**（短窗口去重，适合高频事件）。
 *   - 去重表要和处理逻辑在**同一个事务**里，否则"插入去重记录成功但业务回滚"会让消息被永久丢弃。
 *   - 幂等不是"永远只处理一次"，而是"重复处理的结果和只处理一次相同"。查类操作天然幂等，加类操作必须靠唯一键。
 *   - 易错：用 `select` 判断存在再 `insert`（并发下两个线程都查到不存在）；去重记录设置 TTL 太短，超过窗口的重复消息仍会重复执行。
 * ============================================================
 */

package com.heima.ch28;

import java.util.HashSet;
import java.util.Set;

// ==================== 示例代码 ====================

public class ConsumeIdempotent {
    /** 去重表：模拟数据库唯一索引 */
    static final Set<String> IDEMPOTENT_TABLE = new HashSet<>();
    /** 业务表：订单 -> 状态 */
    static final java.util.Map<String, String> ORDER_STATUS = new java.util.HashMap<>();
    static int pointsAdded = 0;

    /** 处理"订单已支付"事件：加积分 + 改状态 */
    static String handlePaid(String orderNo, String eventId) {
        String key = orderNo + ":PAID";
        if (!IDEMPOTENT_TABLE.add(key)) {
            System.out.println("    事件 " + eventId + " 重复投递 => 去重表命中，直接返回上次结果，积分不再增加");
            return "DUPLICATE";
        }
        String status = ORDER_STATUS.get(orderNo);
        if ("PAID".equals(status)) {
            System.out.println("    事件 " + eventId + " 状态已是 PAID => 状态机拦截");
            return "IGNORED";
        }
        ORDER_STATUS.put(orderNo, "PAID");
        pointsAdded += 100;
        System.out.println("    事件 " + eventId + " 首次处理：状态 WAIT_PAY -> PAID，积分 +100（累计 " + pointsAdded + "）");
        return "OK";
    }

    public static void main(String[] args) {
        ORDER_STATUS.put("ORD-1", "WAIT_PAY");
        System.out.println("=== 场景一：同一条消息被重复投递 3 次 ===");
        handlePaid("ORD-1", "msg-a");
        handlePaid("ORD-1", "msg-a-retry");
        handlePaid("ORD-1", "msg-a-retry2");
        System.out.println("  最终状态=" + ORDER_STATUS.get("ORD-1") + "，积分=" + pointsAdded);

        System.out.println();
        System.out.println("=== 场景二：并发两个消费者同时处理（先 select 再 insert 的坑）===");
        System.out.println("  线程A: select 去重表 -> 不存在");
        System.out.println("  线程B: select 去重表 -> 不存在");
        System.out.println("  线程A: insert 成功，加积分");
        System.out.println("  线程B: insert 唯一键冲突 -> 必须捕获异常回滚，而不是继续加积分");
        System.out.println("  结论：靠唯一索引兜底，不能靠先查后插");

        System.out.println();
        System.out.println("=== 场景三：去重记录 TTL 太短 ===");
        System.out.println("  去重记录 1 分钟后过期，而重试队列的延迟是 5 分钟");
        System.out.println("  -> 第 5 分钟的重复消息查不到记录，积分被加了第二次");
        System.out.println("  经验：TTL >= 消息最大重试窗口 + 业务幂等期（常见 24h 或 7d）");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 把去重表换成"订单状态机"实现，去掉 `IDEMPOTENT_TABLE`，验证重复消息仍被拦截。
 * 2. 说明为什么"先 select 判断再 insert"在并发下会失效，唯一索引在其中起什么作用。
 * 3. 给示例加上"处理失败要回滚去重记录"的逻辑，并解释为什么必须与业务同事务。
 * ============================================================
 */
