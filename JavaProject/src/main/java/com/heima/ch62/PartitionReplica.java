/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：62-02
 * 课名：分区、副本与 Leader 选举
 * ------------------------------------------------------------
 * 知识点：
 *   - **分区是并行的单位，副本是可靠的单位**：每个分区有 **1 个 Leader + N 个 Follower**（`replication.factor`），**生产与消费都只走 Leader**（Kafka 2.4+ 支持 Follower 读，但默认仍走 Leader），Follower 只负责**同步复制**。
 *   - **副本分配要跨 Broker**：同一分区的 Leader 与 Follower 落在不同 Broker（甚至不同机架，`broker.rack`）——**否则 Broker 一挂，分区的主副一起丢**（`replica.assignment` 的机架感知）。
 *   - **Leader 选举不是选举算法**：Kafka 的 Leader 选举由 **Controller**（某个 Broker 兼任）**从 ISR（同步副本集合）中选一个**（通常选第一个）——**它不是 Raft/Paxos（45 章）**，而是"基于 ISR 的指定式选主"，**ISR 为空时分区不可用**（`unclean.leader.election.enable=false` 时）。
 *   - **Controller 的角色**：管理分区 Leader 分配、Broker 上下线、Topic 创建删除——**Controller 挂了会重新竞选**（ZK 时代的 controller epoch / KRaft 时代的元数据仲裁），**Controller 的故障会导致短暂的元数据操作失败**。
 *   - **分区数与并行度**：**消费者数超过分区数时，多余消费者空闲**（62-06 的分配规则）——**分区数是消费并行度的硬上限**（59-04 的分区思想在 Kafka 的对应）。
 *   - 易错点：单副本部署（Broker 挂了数据丢）；副本数与 Broker 数相同但同机架（机架故障全挂）；允不允许 unclean 选举没想清楚（可用性 vs 数据一致性）；分区数创建后想减少（不可能，只能建新 Topic 迁移）。
 * ============================================================
 */

package com.heima.ch62;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class PartitionReplica {
    record Replica(String broker, String rack, boolean inSync, long leo) {
    }

    record Partition(int id, List<Replica> replicas, String leader) {
        List<String> isr() {
            List<String> out = new ArrayList<>();
            for (Replica r : replicas) {
                if (r.inSync()) {
                    out.add(r.broker());
                }
            }
            return out;
        }

        /** 从 ISR 选 Leader（Controller 语义：选第一个 ISR） */
        String electLeader(boolean allowUnclean) {
            List<String> candidates = isr();
            if (!candidates.isEmpty()) {
                return candidates.get(0);
            }
            if (allowUnclean) {
                return replicas.get(0).broker();
            }
            return null;                                   // 不可用
        }
    }

    static void faultTolerance() {
        System.out.println("【副本与故障容忍】");
        Partition p = new Partition(0, List.of(
                new Replica("broker-1", "rack-a", true, 100),
                new Replica("broker-2", "rack-b", true, 99),
                new Replica("broker-3", "rack-c", true, 100)), "broker-1");
        System.out.println("  ISR：" + p.isr() + " Leader=" + p.leader());
        System.out.println("  broker-1 挂 -> 新 Leader=" + p.electLeader(false) + "（从 ISR 选）");

        Partition broken = new Partition(1, List.of(
                new Replica("broker-1", "rack-a", false, 100),
                new Replica("broker-2", "rack-b", false, 99)), "broker-1");
        System.out.println("  ISR 为空（全部落后）-> 禁 unclean 选举：" + broken.electLeader(false)
                + "（分区不可用）");
        System.out.println("  允许 unclean 选举：" + broken.electLeader(true)
                + "（恢复可用但可能丢数据）");
        System.out.println();
    }

    static void assignment() {
        System.out.println("【副本分布：机架感知】");
        Map<String, List<String>> racks = new LinkedHashMap<>();
        racks.put("rack-a", List.of("broker-1", "broker-2"));
        racks.put("rack-b", List.of("broker-3", "broker-4"));
        System.out.println("  集群：" + racks);
        System.out.println("  好：分区 0 的副本 {broker-1(a), broker-3(b)} -> 单机架故障仍可用");
        System.out.println("  坏：分区 0 的副本 {broker-1(a), broker-2(a)} -> rack-a 挂全丢");
        System.out.println("  replication.factor=3 + broker.rack 配置 = 跨机架容灾");
    }

    public static void main(String[] args) {
        faultTolerance();
        assignment();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现分区副本模型：3 副本跨 3 机架，模拟"单机架故障/单 Broker 故障/两副本落后"三种场景，输出可用性与数据一致性结论。
 * 2. 计算 6 个 Broker、3 机架、`replication.factor=3` 的分区副本分配方案，验证任一台机器挂掉后每个分区仍有 ≥1 个 ISR 副本。
 * 3. 思考题：为什么 Kafka 的 Leader 选举不采用 Raft？从"依赖外部协调（ZK/KRaft）"与"分区级选主"的设计差异分析。
 * ============================================================
 */
