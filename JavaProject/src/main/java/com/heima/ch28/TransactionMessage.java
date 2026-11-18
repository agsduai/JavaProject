/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：28-09
 * 课名：事务消息与本地消息表
 * ------------------------------------------------------------
 * 知识点：
 *   - 核心矛盾：**数据库事务和消息发送不在同一个事务里**。先发消息再提交事务 → 事务回滚了消息已投出（下游多一条脏数据）；先提交事务再发消息 → 发消息失败（下游永远收不到）。
 *   - **本地消息表**（最常用、最可靠）：业务表和消息表在同一个本地事务里写入，再由定时任务扫描"待发送"记录投递 MQ，投递成功后改状态。靠"本地事务原子性 + 定时补偿"保证最终一致。
 *   - **事务消息**（RocketMQ）：先发半消息 → Broker 存下来但不投递 → 执行本地事务 → 提交/回滚半消息 → Broker 对未知状态回查。本质也是"两阶段 + 回查"。
 *   - 两种方案都要**下游幂等**，因为补偿重投必然带来重复。
 *   - 补偿扫描要有上限和告警：一直发不出去的消息不能无限重试，超过 N 次要人工介入。
 *   - 易错：把消息发送放在 `@Transactional` 方法内但用的是"发送成功即返回"的异步客户端，事务还没提交消息就出去了；补偿任务没有加锁，多实例重复投递。
 * ============================================================
 */

package com.heima.ch28;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class TransactionMessage {
    /** 业务表 */
    static final Map<String, String> ORDER_TABLE = new LinkedHashMap<>();
    /** 本地消息表：msgId -> 状态 */
    static final Map<String, String> MSG_TABLE = new LinkedHashMap<>();
    /** 模拟 MQ */
    static final List<String> MQ = new ArrayList<>();

    /** 一个本地事务里同时写业务表和消息表 */
    static boolean createOrderInTx(String orderNo, boolean failAfterWrite) {
        try {
            ORDER_TABLE.put(orderNo, "CREATED");
            MSG_TABLE.put("MSG-" + orderNo, "PENDING");
            System.out.println("  事务内：写订单 " + orderNo + " + 写消息 MSG-" + orderNo + "(PENDING)");
            if (failAfterWrite) {
                throw new IllegalStateException("库存不足，回滚");
            }
            System.out.println("  事务提交：订单和消息一起生效");
            return true;
        } catch (RuntimeException e) {
            ORDER_TABLE.remove(orderNo);
            MSG_TABLE.remove("MSG-" + orderNo);
            System.out.println("  事务回滚：" + e.getMessage() + "，订单和消息都不存在");
            return false;
        }
    }

    /** 补偿任务：扫描 PENDING 投递 MQ */
    static int relay() {
        int sent = 0;
        for (Map.Entry<String, String> e : MSG_TABLE.entrySet()) {
            if ("PENDING".equals(e.getValue())) {
                MQ.add(e.getKey());
                e.setValue("SENT");
                sent++;
                System.out.println("  补偿任务投递 " + e.getKey() + " -> MQ，状态 PENDING => SENT");
            }
        }
        return sent;
    }

    public static void main(String[] args) {
        System.out.println("=== 正常路径 ===");
        createOrderInTx("ORD-1", false);
        relay();
        System.out.println("  MQ=" + MQ + "，消息表=" + MSG_TABLE);

        System.out.println();
        System.out.println("=== 业务失败：消息不会漏出去 ===");
        createOrderInTx("ORD-2", true);
        int sent = relay();
        System.out.println("  本轮补偿投递 " + sent + " 条，MQ=" + MQ);
        System.out.println("  -> 事务回滚把消息表记录也回滚了，下游不会收到脏事件");

        System.out.println();
        System.out.println("=== 反例：先发消息再提交事务 ===");
        MQ.add("MSG-ORD-3");
        System.out.println("  发消息成功，MQ=" + MQ);
        System.out.println("  随后事务回滚，订单没建成 -> 下游收到一条不存在的订单事件");

        System.out.println();
        System.out.println("=== 反例：先提交事务再发消息 ===");
        ORDER_TABLE.put("ORD-4", "CREATED");
        MSG_TABLE.put("MSG-ORD-4", "PENDING");
        System.out.println("  订单已提交，此时发消息进程崩溃 -> 消息表留着 PENDING");
        System.out.println("  靠补偿任务兜底：relay() 下次扫描仍会把它投出去");
        relay();
        System.out.println("  最终 MQ=" + MQ + "，消息表=" + MSG_TABLE);
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 给补偿任务加上"最多重试 5 次，超过标记 FAILED 并告警"的逻辑。
 * 2. 画出本地消息表的完整时序：业务写库 → 消息写库 → 提交 → 补偿投递 → 下游消费 → 幂等。
 * 3. 对比事务消息与本地消息表的可靠性、侵入性、运维成本，说明各自适用场景。
 * ============================================================
 */
