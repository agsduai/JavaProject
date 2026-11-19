/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：28-11
 * 课名：综合案例：订单异步履约
 * ------------------------------------------------------------
 * 知识点：
 *   - 完整链路：下单（同步扣库存 + 建订单 + 写本地消息表）→ 补偿任务投递 `order.created` → 积分/短信/物流三个消费组各自消费 → 失败重试 → 超限进死信 → 人工补偿。
 *   - 每一步都要有**可观测性**：traceId 贯穿生产与消费，消费日志带 msgId 和业务键，死信有告警。
 *   - 幂等点分布：积分消费按 `orderNo` 去重、短信按 `orderNo + 模板` 去重、物流按 `orderNo` 状态机。
 *   - 降级策略：短信服务长时间不可用时，先落库待发，不阻塞其它消费者；积分可以延迟补，物流必须最终送达。
 *   - 本章所有能力在这个案例里合流：可靠投递（28-03）、手动 ack 与死信（28-04）、幂等（28-05）、顺序（28-06）、本地消息表（28-09）。
 *   - 易错：把三个下游写成一个消费者串行调用，一个慢全慢；消费失败直接吞掉异常，日志里查不到。
 * ============================================================
 */

package com.heima.ch28;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// ==================== 示例代码 ====================

public class OrderAsyncCase {
    static final Map<String, String> ORDER = new HashMap<>();
    static final Map<String, String> MSG = new HashMap<>();
    static final List<String> MQ = new ArrayList<>();
    static final Set<String> POINTS_DEDUP = new HashSet<>();
    static final Map<String, Integer> POINTS = new HashMap<>();
    static final List<String> SMS_SENT = new ArrayList<>();
    static final Map<String, String> LOGISTICS = new HashMap<>();
    static final List<String> DEAD = new ArrayList<>();
    static final Map<String, Integer> RETRY = new HashMap<>();

    static void log(String traceId, String msg) {
        System.out.println("  [" + traceId + "] " + msg);
    }

    /** 下单：同步核心链路 + 本地消息表 */
    static String placeOrder(String traceId, String orderNo, String userId) {
        log(traceId, "扣库存 OK");
        ORDER.put(orderNo, "CREATED");
        MSG.put("MSG-" + orderNo, "PENDING");
        log(traceId, "建订单 " + orderNo + " + 写消息表(PENDING)，同一事务提交");
        return orderNo;
    }

    /** 补偿投递 */
    static void relay(String traceId) {
        for (Map.Entry<String, String> e : MSG.entrySet()) {
            if ("PENDING".equals(e.getValue())) {
                MQ.add(e.getKey());
                e.setValue("SENT");
                log(traceId, "投递 " + e.getKey() + " -> MQ");
            }
        }
    }

    /** 三个消费组分别消费，各自幂等 */
    static void consume(String traceId, String msgId, String orderNo, String userId) {
        // 积分
        if (POINTS_DEDUP.add(orderNo)) {
            POINTS.merge(userId, 100, Integer::sum);
            log(traceId, "积分组：+100，累计 " + POINTS.get(userId));
        } else {
            log(traceId, "积分组：去重命中，跳过");
        }
        // 短信（模拟第一次失败，进重试）
        int attempt = RETRY.merge(msgId + ":sms", 1, Integer::sum);
        if (attempt == 1) {
            log(traceId, "短信组：网关超时，nack 进重试队列（第 1 次）");
        } else {
            SMS_SENT.add(orderNo);
            log(traceId, "短信组：重试成功，已发送（第 " + attempt + " 次）");
        }
        // 物流
        if (!LOGISTICS.containsKey(orderNo)) {
            LOGISTICS.put(orderNo, "WAIT_PICK");
            log(traceId, "物流组：创建发货单，状态 WAIT_PICK");
        } else {
            log(traceId, "物流组：已存在发货单，跳过");
        }
    }

    public static void main(String[] args) {
        String traceId = "trace-8f2a";
        System.out.println("=== 下单 ===");
        String orderNo = placeOrder(traceId, "ORD-20261117-001", "u1001");
        System.out.println("  接口返回成功，用户不用等积分/短信/物流");

        System.out.println();
        System.out.println("=== 补偿任务投递 + 消费 ===");
        relay(traceId);
        for (String msgId : MQ) {
            consume(traceId, msgId, orderNo, "u1001");
        }

        System.out.println();
        System.out.println("=== 重试轮 ===");
        for (String msgId : MQ) {
            consume(traceId, msgId, orderNo, "u1001");
        }

        System.out.println();
        System.out.println("=== 重复投递（模拟 ack 前宕机）===");
        consume(traceId, "MSG-" + orderNo + "-dup", orderNo, "u1001");

        System.out.println();
        System.out.println("=== 最终状态 ===");
        System.out.println("  订单=" + ORDER);
        System.out.println("  消息表=" + MSG);
        System.out.println("  积分=" + POINTS);
        System.out.println("  短信=" + SMS_SENT);
        System.out.println("  物流=" + LOGISTICS);
        System.out.println("  死信=" + DEAD + "（本次无）");
        System.out.println("  结论：重复消息被幂等挡住，失败消息靠重试恢复，核心链路不受下游影响");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 把短信消费改成"连续失败 3 次进死信"，并打印死信告警。
 * 2. 给案例加上"物流服务整体不可用"的降级：消息暂存，不影响积分和短信。
 * 3. 用一张时序图画出 traceId 从下单到三个消费者的完整流转。
 * ============================================================
 */

/*
 * ============================================================
 * 本章综合练习（无参考答案）
 * ------------------------------------------------------------
 * 【场景】
 * 下单成功后不再同步做加积分、发短信、通知仓库，改成发消息：生产者可靠投递、消费者幂等消费、失败重试与死信、延迟消息做超时关单，最后处理一次消息积压。
 * 【任务】
 * 1. 用内存队列模拟 Broker，实现生产者发送确认与失败重试（最多 3 次，退避递增），消息持久化到文件后重启不丢。
 * 2. 实现消费者：手动确认、处理成功才 ack、失败重试超过 3 次进入死信队列，死信可查询与人工重放。
 * 3. 实现幂等消费：每条消息带业务唯一键，消费前查已处理表，重复消息直接 ack 不重复执行。
 * 4. 实现延迟消息：下单 30 分钟未支付自动关单，用延迟队列或定时扫描实现，并说明两者在精度与成本上的取舍。
 * 【验收清单】
 * [ ] 消费者故意抛异常时消息进入重试，3 次后进死信，业务数据只被成功处理一次。
 * [ ] 同一消息重复投递 5 次，积分只加一次。
 * [ ] 延迟关单在设定时间点附近触发，已支付的订单不会被误关。
 * [ ] 模拟积压（生产者 1000 条、消费者 1 条/秒）后能用扩容消费者把积压消化掉，并给出耗时估算。
 * ============================================================
 */
