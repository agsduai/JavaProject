/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：62-03
 * 课名：ISR 与 HW/LEO：可靠性的账本
 * ------------------------------------------------------------
 * 知识点：
 *   - **三个偏移量概念**：**LEO（Log End Offset）**——每个副本自己的日志末尾位置；**HW（High Watermark）**——**所有 ISR 副本都已同步到的位置**；**消费者可见位置 = HW**——**HW 之后的消息即使 Leader 已写入，消费者也读不到**（未确认安全）。
 *   - **ISR（In-Sync Replicas）的定义**：与 Leader 保持同步的副本集合（`replica.lag.time.max.ms` 内追上了 Leader 的 LEO）——**ISR 是动态的**：Follower 落后超时被踢出 ISR，追上后再加入。
 *   - **可靠性公式**：**已提交（committed）= 已复制到所有 ISR 且 ≤ HW 的消息**——`acks=all` 时，生产者收到确认意味着消息已进入所有 ISR；**`min.insync.replicas` 决定最少几个 ISR 才接受写入**（ISR 不足时拒绝写入，保护不丢数据）。
 *   - **HW 的推进与截断**：Leader 收到 Follower 的拉取请求后更新 Follower 的 LEO，**取所有 ISR 的最小 LEO 作为新 HW**；**Leader 切换时**，新 Leader 会**截断到自己的 HW**（丢弃未提交的消息）——这就是"为什么 acks=1 可能丢消息"。
 *   - **`acks` 三档**：`0`（不等待，最快可能丢）、`1`（Leader 写入即确认，Leader 挂可能丢）、`all/-1`（所有 ISR 确认，最安全最慢）——**金融/订单用 all + min.insync.replicas=2，日志/埋点可用 1**。
 *   - 易错点：`acks=all` 但 `min.insync.replicas=1`（ISR 只剩 Leader 时仍接受写入，等于 acks=1）；以为 HW 之前的消息一定不丢（取决于 ISR 与 unclean 配置）；不看 ISR 收缩告警（磁盘慢导致 Follower 落后，可靠性悄悄下降）。
 * ============================================================
 */

package com.heima.ch62;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class IsrAndWatermark {
    record ReplicaState(String broker, long leo, boolean inSync) {
    }

    record PartitionState(List<ReplicaState> replicas, long hw) {
    }

    /** HW = min(LEO of ISR)；ISR 为空时 HW 不推进 */
    static PartitionState refresh(List<ReplicaState> replicas, long currentHw) {
        long minLeo = Long.MAX_VALUE;
        boolean any = false;
        for (ReplicaState r : replicas) {
            if (r.inSync()) {
                minLeo = Math.min(minLeo, r.leo());
                any = true;
            }
        }
        long hw = any ? Math.max(currentHw, minLeo) : currentHw;
        return new PartitionState(replicas, hw);
    }

    /** 生产者写入：按 acks 决定何时确认 */
    static String produce(PartitionState st, String acks, int minIsr, long newLeo) {
        List<ReplicaState> updated = new ArrayList<>();
        long leaderLeo = 0;
        for (ReplicaState r : st.replicas()) {
            if (r.broker().equals(leader(st))) {
                leaderLeo = newLeo;
                updated.add(new ReplicaState(r.broker(), newLeo, r.inSync()));
            } else {
                updated.add(r);
            }
        }
        long isrCount = updated.stream().filter(ReplicaState::inSync).count();
        if (acks.equals("all") && isrCount < minIsr) {
            return "拒绝写入：ISR=" + isrCount + " < min.insync.replicas=" + minIsr;
        }
        return switch (acks) {
            case "0" -> "立即返回（不等待）-> 可能丢";
            case "1" -> "Leader 写入即返回（LEO=" + leaderLeo + "）-> Leader 挂可能丢";
            default -> "所有 ISR 确认后返回（ISR=" + isrCount + "）-> 已提交";
        };
    }

    static String leader(PartitionState st) {
        return st.replicas().get(0).broker();
    }

    public static void main(String[] args) {
        System.out.println("【HW 推进：取 ISR 最小 LEO】");
        List<ReplicaState> replicas = List.of(
                new ReplicaState("broker-1", 105, true),
                new ReplicaState("broker-2", 100, true),
                new ReplicaState("broker-3", 98, false));      // 落后被踢出 ISR
        PartitionState st = refresh(replicas, 95);
        System.out.println("  ISR=[broker-1(105), broker-2(100)]，滞后 broker-3(98) 不在 ISR");
        System.out.println("  HW=min(105,100)=100 -> 消费者只能读到 offset<100");
        System.out.println();

        System.out.println("【acks 三档的行为】");
        System.out.println("  acks=0  -> " + produce(st, "0", 2, 106));
        System.out.println("  acks=1  -> " + produce(st, "1", 2, 106));
        System.out.println("  acks=all-> " + produce(st, "all", 2, 106));
        System.out.println();

        System.out.println("【ISR 不足时拒绝写入（保护不丢）】");
        PartitionState shrunk = new PartitionState(List.of(
                new ReplicaState("broker-1", 110, true),
                new ReplicaState("broker-2", 95, false),
                new ReplicaState("broker-3", 93, false)), 108);
        System.out.println("  " + produce(shrunk, "all", 2, 111));
        System.out.println();
        System.out.println("  可靠性公式：committed = 已复制到所有 ISR 且 <= HW");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现 HW/LEO 模拟器：3 副本动态加入/退出 ISR，逐步写入消息并输出每步的 LEO/HW/ISR，验证"HW 只前进不后退"。
 * 2. 对比 `acks=1` 与 `acks=all` 在"Leader 写入后立即宕机"场景下的数据丢失量，说明 `min.insync.replicas` 的作用。
 * 3. 思考题：为什么消费者只能读到 HW 之前的消息？从"未提交消息在 Leader 切换时会被截断"的角度推导。
 * ============================================================
 */
