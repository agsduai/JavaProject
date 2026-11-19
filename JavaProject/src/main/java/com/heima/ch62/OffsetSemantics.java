/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：62-07
 * 课名：Offset 管理与精确一次
 * ------------------------------------------------------------
 * 知识点：
 *   - **Offset 提交的两种方式**：**自动提交**（`enable.auto.commit=true`，按 `auto.commit.interval.ms` 周期性提交）与**手动提交**（`commitSync`/`commitAsync`）——**自动提交会重复消费**（"处理完成"与"offset 提交"之间崩溃 -> 重启后重复处理上次窗口）；**业务处理必须手动提交**（先处理成功后提交）。
 *   - **提交时机决定语义**：**先提交后处理** -> 可能丢（提交了但没处理完）；**先处理后提交** -> 至少一次（可能重复）；**事务内提交** -> 恰好一次（62-05 的事务把"消息输出 + offset 提交"绑定）。
 *   - **重复消费的幂等兜底**（39/43 章）：即使做到"处理后提交"，**Rebalance 或事务超时仍可能重复**——消费端必须有幂等键（业务 ID/事件 ID），**Kafka 的 offset 语义是"至少一次"，恰好一次靠端到端设计**。
 *   - **Offset 存储位置**：`__consumer_offsets` 内部 Topic（按 group + partition 存），**重置 offset**（`--to-earliest/--to-latest/--to-offset`）是重放的入口（43-01 可重放源）。
 *   - **`isolation.level`**：`read_uncommitted`（默认，能看到未提交事务消息）与 `read_committed`（只读已提交，配合事务实现 EOS）——**用事务时必须配 `read_committed`**。
 *   - 易错点：自动提交 + 长处理（重复消费）；提交了 offset 但处理失败（数据丢失）；重置 offset 未停消费者（重复处理）；`read_committed` 下看不到未提交消息被当成"丢消息"。
 * ============================================================
 */

package com.heima.ch62;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class OffsetSemantics {
    record Message(long offset, String key, String value) {
    }

    record ConsumerState(long processedOffset, long committedOffset) {
        ConsumerState process(Message m) {
            return new ConsumerState(m.offset(), committedOffset);
        }

        ConsumerState commit() {
            return new ConsumerState(processedOffset, processedOffset);
        }
    }

    /** 模拟"处理 3 条后崩溃"，对比两种提交策略的重启行为 */
    static void compareCommitStrategy(boolean commitBeforeProcess) {
        List<Message> log = List.of(
                new Message(0, "k1", "m1"), new Message(1, "k2", "m2"),
                new Message(2, "k3", "m3"), new Message(3, "k4", "m4"));
        long committed = 0;
        List<String> processed = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Message m = log.get(i);
            if (commitBeforeProcess) {
                committed = m.offset();                 // 先提交
                processed.add(m.value());
            } else {
                processed.add(m.value());               // 先处理
                committed = m.offset();                 // 后提交
            }
        }
        // 崩溃：最后一次提交之前的都不会重放
        List<String> afterRestart = new ArrayList<>();
        for (Message m : log) {
            if (m.offset() > committed) {
                afterRestart.add(m.value());
            }
        }
        System.out.printf("  %s提交：已处理=%s committed=%d -> 重启后从 %s 开始%n",
                commitBeforeProcess ? "先" : "后", processed, committed, afterRestart);
        if (commitBeforeProcess) {
            System.out.println("    风险：offset 已提交但处理未完成的那条 -> 丢失（至少一次被破坏）");
        } else {
            System.out.println("    语义：至少一次（可能重复处理 committed 之外的部分）");
        }
    }

    static void eosWithTransaction() {
        System.out.println("【恰好一次：事务把输出与 offset 绑定】");
        Map<String, String> steps = new LinkedHashMap<>();
        steps.put("1", "beginTransaction（含 offset 提交）");
        steps.put("2", "处理消息 -> 产出结果到下游 Topic");
        steps.put("3", "sendOffsetsToTransaction（offset 纳入同一事务）");
        steps.put("4", "commitTransaction：结果与 offset 一起原子生效");
        steps.put("5", "失败则 abort：下游无脏数据、offset 不前进");
        steps.forEach((k, v) -> System.out.printf("  %s. %s%n", k, v));
        System.out.println();
        System.out.println("  前提：transactional.id + read_committed + 消费端仍幂等（跨会话兜底）");
    }

    public static void main(String[] args) {
        System.out.println("【提交策略对比】");
        compareCommitStrategy(true);
        System.out.println();
        compareCommitStrategy(false);
        System.out.println();
        eosWithTransaction();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现消费者 offset 语义模拟：自动提交/手动提交/事务提交三种模式，在"处理到第 3 条崩溃"场景下对比最终结果（丢失/重复/恰好一次）。
 * 2. 实现"重置 offset 重放"：把 group offset 置 0 重读全部历史，验证与"重放期间新写入消息"的边界。
 * 3. 思考题：为什么 Kafka 自称"至少一次"，而 EOS 要靠事务 + 幂等？从"跨会话/跨系统"的边界分析 43-06 的等价结论。
 * ============================================================
 */
