/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：43-05
 * 课名：状态管理与容错
 * ------------------------------------------------------------
 * 知识点：
 *   - **流处理是"常驻的有状态计算"**：窗口累计值、去重集合、join 缓存都是**状态**——它们必须活在进程里（快）又能被持久化（挂了可恢复）。**状态管理是流系统与"无状态微服务"的本质区别**。
 *   - **状态的两类存储**：**算子本地状态**（RocksDB/堆内，读写快，计算用）+ **周期性快照**（checkpoint，写到分布式存储 HDFS/S3，恢复用）。**本地状态是主副本，checkpoint 是保险**。
 *   - **Checkpoint 机制（Chandy-Lamport 快照）**：Source 插入屏障（barrier）随数据流流动，barrier 到达某算子时该算子把当前状态快照到远端存储；barrier 对齐保证"快照时点前后数据不混"。**恢复 = 所有算子回到最近一次 checkpoint 的状态，从 Source 重放之后的数据**。
 *   - **恢复依赖"可重放源"**：Kafka 的 offset 就是重放指针——checkpoint 里存了"当时消费到 offset X"，故障后从 X 重读。**不可重放的源（实时 HTTP 推送）配 checkpoint 毫无意义**——这也是"流处理要对接可重放消息系统"的架构原因。
 *   - **状态治理是运维主战场**：TTL（key 1 天不活动就清理，防止状态无限膨胀）、状态大小监控（RocksDB 落盘量、checkpoint 大小与耗时）、大状态拆分（按 key 哈希分散）。**状态失控 = checkpoint 超时 = 一直恢复失败 = 作业死亡循环**。
 *   - **checkpoint 与可用性的平衡**：间隔太密（1s）→ 开销大；太疏（10min）→ 恢复要重放 10 分钟数据。典型 30s~2min，大状态作业放宽并加增量 checkpoint（只传变化部分）。
 *   - 易错点：状态无 TTL（运行三月 RocksDB 500GB）；checkpoint 一直失败没人看（恢复时才发现作业早就"假活"）；把业务数据只存本地内存（挂了全丢）；恢复期间重复消费未做幂等（43-06）。
 * ============================================================
 */

package com.heima.ch43;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class StateCheckpoint {
    /** 算子状态：窗口累计 + 最近 offset */
    static class OperatorState {
        final Map<String, Double> windowTotals = new LinkedHashMap<>();
        long kafkaOffset = 0;
        int version = 0;

        void process(String key, double amount, long offset) {
            windowTotals.merge(key, amount, Double::sum);
            kafkaOffset = offset;
        }

        Map<String, Object> snapshot() {
            Map<String, Object> snap = new LinkedHashMap<>(windowTotals);
            snap.put("__offset", kafkaOffset);
            return snap;
        }

        void restore(Map<String, Object> snap) {
            windowTotals.clear();
            for (Map.Entry<String, Object> e : snap.entrySet()) {
                if (!e.getKey().equals("__offset")) {
                    windowTotals.put(e.getKey(), (Double) e.getValue());
                } else {
                    kafkaOffset = (long) e.getValue();
                }
            }
        }
    }

    static void checkpointCycle() {
        System.out.println("【一个 checkpoint 周期与故障恢复】");
        OperatorState state = new OperatorState();
        state.process("11-17", 69.0, 100);
        state.process("11-17", 199.0, 101);
        Map<String, Object> snap = state.snapshot();
        System.out.println("  T0 checkpoint#1：" + snap + " -> 持久化到 S3");
        state.process("11-17", 29.0, 102);          // 这条之后发生故障
        System.out.println("  T1 继续处理 offset=102（GMV=297）后作业崩溃");
        OperatorState recovered = new OperatorState();
        recovered.restore(snap);
        System.out.println("  恢复：状态回到 checkpoint#1（GMV=268, offset=101）");
        System.out.println("  重放：从 Kafka offset=101 重读 -> 重新处理 102 -> GMV=297");
        System.out.println("  重复消费了 offset=102！下游必须幂等（43-06）");
        System.out.println();
    }

    static void ttlDiscipline() {
        System.out.println("【状态 TTL：防止状态无限膨胀】");
        record StateItem(String key, String ttl, String effect) {
        }
        List<StateItem> items = List.of(
                new StateItem("去重集合（订单ID）", "24h", "一天前的订单不会再来，清理"),
                new StateItem("窗口累计", "size+允许迟到", "窗口关闭即删"),
                new StateItem("join 左流缓存", "10min", "按 join 语义定"),
                new StateItem("无 TTL 的 user 状态", "——", "三个月 500GB，checkpoint 超时"));
        for (StateItem i : items) {
            System.out.printf("  %-18s TTL=%-14s %s%n", i.key(), i.ttl(), i.effect());
        }
        System.out.println("  规则：每个状态都要回答“什么时候可以删”");
        System.out.println();
    }

    static void healthSignals() {
        System.out.println("【流作业的存活判据：checkpoint 是心跳】");
        List<String> signals = List.of(
                "checkpoint 成功率 100%（失败 1 次即告警，连续失败=作业病危）",
                "checkpoint 耗时 < 间隔的 50%（否则永远在 checkpoint）",
                "Source 消费延迟（lag）持续增长 = 处理不过来",
                "RocksDB 落盘量增速稳定（突然暴涨=状态泄漏）");
        for (String s : signals) {
            System.out.println("  - " + s);
        }
        System.out.println("  对照：进程活着但 checkpoint 失败一周 —— “假活”，恢复时才发现状态早没了");
    }

    public static void main(String[] args) {
        checkpointCycle();
        ttlDiscipline();
        healthSignals();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现示例的状态快照/恢复闭环：注入故障点（处理 2 条后崩溃），验证恢复 + 重放后最终结果正确，并记录重复消费的条数。
 * 2. 为你的流作业（或模拟作业）做状态清单：每个状态的类型、TTL、大小估算，找出无 TTL 的状态并补上。
 * 3. 思考题：为什么"不可重放的源配 checkpoint 毫无意义"？从恢复语义推导流系统对上游消息系统的架构要求。
 * ============================================================
 */
