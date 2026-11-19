/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：62-05
 * 课名：生产者：幂等与事务
 * ------------------------------------------------------------
 * 知识点：
 *   - **生产者发送的可靠性三要素**：`acks`（确认级别 62-03）、`retries`（重试次数）、`enable.idempotence`（幂等）——**幂等生产者（默认开启）通过 `PID + 序列号` 让 Broker 去重**，解决"重试导致的重复写入"（39/43 章幂等原则的 Kafka 实现）。
 *   - **幂等 ≠ 恰好一次**：幂等只保证"**单个生产者单会话**内不重复"，**跨会话（重启后新 PID）或跨生产者**仍会重复——**端到端恰好一次需要"事务 + 消费端幂等"**（62-07）。
 *   - **Kafka 事务**：`initTransactions -> beginTransaction -> send -> sendOffsetsToTransaction -> commitTransaction`——**把"生产消息"与"提交消费 offset"打包成一个原子操作**，实现"读-处理-写"的 exactly-once（43-06 的 2PC sink 就是它）。
 *   - **事务的前提**：`transactional.id`（跨会话保持标识）、`isolation.level=read_committed`（消费者只读已提交）——**未提交的消息对 read_committed 消费者不可见**（与 HW 同一层语义）。
 *   - **批次与压缩**：`linger.ms`（等一会儿凑批）+ `batch.size` + `compression.type`（lz4/zstd）——**吞吐与延迟的旋钮**（48-03 批处理思想的 Kafka 版）；`max.in.flight.requests.per.connection` 影响乱序风险（开启幂等后安全）。
 *   - 易错点：以为开了幂等就"绝对不重复"（跨会话仍可能）；事务超时（`transaction.timeout.ms`）导致提交失败；`read_committed` 消费者看不到未提交消息被误判为"消息丢了"；linger 调太大（延迟升高）。
 * ============================================================
 */

package com.heima.ch62;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class ProducerSemantics {
    record Envelope(String pid, long seq, String key, String value, boolean committed) {
    }

    /** Broker 端幂等去重：按 (pid, seq) 幂等 */
    static class Broker {
        final Map<String, Long> lastSeqByPid = new LinkedHashMap<>();
        final List<Envelope> log = new ArrayList<>();
        boolean pendingTx = false;

        String append(String pid, long seq, String key, String value, boolean transactional,
                      boolean commitNow) {
            Long last = lastSeqByPid.get(pid);
            if (last != null && seq <= last) {
                return "DUPLICATE 丢弃（pid=" + pid + " seq=" + seq + " <= last=" + last + "）";
            }
            lastSeqByPid.put(pid, seq);
            log.add(new Envelope(pid, seq, key, value, !transactional || commitNow));
            return "APPEND ok（" + (transactional && !commitNow ? "未提交" : "已提交") + "）";
        }

        List<Envelope> readCommitted() {
            return log.stream().filter(Envelope::committed).toList();
        }
    }

    static void idempotentDemo() {
        System.out.println("【幂等生产者：重试不重复】");
        Broker broker = new Broker();
        System.out.println("  第 1 次发送：" + broker.append("pid-1", 1, "k", "m1", false, true));
        System.out.println("  重试同一条：" + broker.append("pid-1", 1, "k", "m1", false, true));
        System.out.println("  第 2 条：    " + broker.append("pid-1", 2, "k", "m2", false, true));
        System.out.println("  日志条数=" + broker.log.size() + "（重复被去重）");
        System.out.println();
    }

    static void transactionDemo() {
        System.out.println("【事务：未提交对 read_committed 不可见】");
        Broker broker = new Broker();
        System.out.println("  txn 内写 2 条（未提交）：");
        System.out.println("    " + broker.append("pid-tx", 1, "k", "a", true, false));
        System.out.println("    " + broker.append("pid-tx", 2, "k", "b", true, false));
        System.out.println("  read_committed 可见：" + broker.readCommitted().size() + " 条");
        System.out.println("  提交后再写一条已提交：");
        System.out.println("    " + broker.append("pid-tx", 3, "k", "c", true, true));
        System.out.println("  read_committed 可见：" + broker.readCommitted().size() + " 条");
        System.out.println();
        System.out.println("  跨会话（新 pid）不共享去重状态 -> 仍需消费端幂等（43-06）");
    }

    public static void main(String[] args) {
        idempotentDemo();
        transactionDemo();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现幂等生产者模拟：pid + seq 去重，模拟"网络超时重试 3 次"验证 Broker 只落一条。
 * 2. 实现事务生产者：模拟"发送 3 条 + 提交 offset"原子操作，断言失败回滚后消费者看不到这批消息。
 * 3. 思考题：为什么"幂等生产者"不能保证端到端恰好一次？从跨会话 PID 变化与"消费侧重复处理"两个角度分析。
 * ============================================================
 */
