/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：62-06
 * 课名：消费者组与 Rebalance
 * ------------------------------------------------------------
 * 知识点：
 *   - **消费者组的分配规则**：**一个分区只能被组内一个消费者消费**（组内互斥），**一个消费者可以消费多个分区**；分配策略（Range/RoundRobin/Sticky/CooperativeSticky）决定"哪个消费者拿哪几个分区"。
 *   - **消费并行度的上限 = 分区数**：10 个分区 -> 最多 10 个消费者有活干（第 11 个空闲）——**这是"分区数创建后只能增"的根本原因**（62-02）。
 *   - **Rebalance（再平衡）**：消费者加入/离开/订阅变化/分区数变化时，组内重新分配分区——**期间消费暂停**（Stop-The-World），**频繁 Rebalance 是消费延迟的常见原因**。
 *   - **Rebalance 的三次演进**：Eager（全部停、全量重分配）-> Cooperative（增量式，只迁移变化的分区）-> **Static Membership**（`group.instance.id`，短暂重启不触发 Rebalance）——**生产建议 Cooperative + 合理 session.timeout**。
 *   - **Rebalance 的触发源排查**：`max.poll.interval.ms` 超时（处理太慢被踢出组，最常见的"莫名 Rebalance"）、心跳线程与处理线程分离（`heartbeat.interval.ms`）、GC 长时间停顿（30 章）。
 *   - 易错点：消费者处理慢导致 `max.poll.interval.ms` 超时（反复 Rebalance）；消费者数 > 分区数（多余空闲）；用 Eager 策略（每次全量重分配，抖动大）；静态成员未配置（滚动发布触发多次 Rebalance）。
 * ============================================================
 */

package com.heima.ch62;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// ==================== 示例代码 ====================

public class ConsumerGroup {
    /** Range 分配：按消费者数量均分（余数给前面的消费者） */
    static Map<String, List<Integer>> rangeAssign(List<String> consumers, int partitions) {
        Map<String, List<Integer>> out = new LinkedHashMap<>();
        int base = partitions / consumers.size();
        int remain = partitions % consumers.size();
        int next = 0;
        for (int i = 0; i < consumers.size(); i++) {
            List<Integer> mine = new ArrayList<>();
            int n = base + (i < remain ? 1 : 0);
            for (int j = 0; j < n; j++) {
                mine.add(next++);
            }
            out.put(consumers.get(i), mine);
        }
        return out;
    }

    /** Cooperative 再平衡：只迁移"多余"的分区，保留原有归属 */
    static Map<String, Set<Integer>> cooperative(Map<String, Set<Integer>> current,
                                                 List<String> consumers, int partitions) {
        Map<String, Set<Integer>> out = new LinkedHashMap<>();
        consumers.forEach(c -> out.put(c, new LinkedHashSet<>()));
        // 1) 保留仍然合法的归属
        current.forEach((consumer, parts) -> {
            if (out.containsKey(consumer)) {
                out.get(consumer).addAll(parts);
            }
        });
        // 2) 找出未分配的分区
        Set<Integer> assigned = new LinkedHashSet<>();
        out.values().forEach(assigned::addAll);
        List<Integer> orphan = new ArrayList<>();
        for (int i = 0; i < partitions; i++) {
            if (!assigned.contains(i)) {
                orphan.add(i);
            }
        }
        // 3) 按消费者负载均衡地补齐（简化：逐个分给当前最少者）
        for (Integer p : orphan) {
            String target = consumers.stream()
                    .min((a, b) -> Integer.compare(out.get(a).size(), out.get(b).size()))
                    .orElse(consumers.get(0));
            out.get(target).add(p);
        }
        return out;
    }

    public static void main(String[] args) {
        System.out.println("【Range 分配：6 分区 / 3 消费者】");
        rangeAssign(List.of("c-1", "c-2", "c-3"), 6)
                .forEach((c, p) -> System.out.printf("  %s -> %s%n", c, p));

        System.out.println();
        System.out.println("【消费者数 > 分区数：多余的闲置】");
        rangeAssign(List.of("c-1", "c-2", "c-3", "c-4", "c-5", "c-6", "c-7", "c-8"), 6)
                .forEach((c, p) -> System.out.printf("  %s -> %s%s%n", c, p,
                        p.isEmpty() ? "（空闲，消费并行上限=分区数）" : ""));

        System.out.println();
        System.out.println("【Cooperative 再平衡：扩到 4 消费者】");
        Map<String, Set<Integer>> before = new LinkedHashMap<>();
        before.put("c-1", new LinkedHashSet<>(List.of(0, 1)));
        before.put("c-2", new LinkedHashSet<>(List.of(2, 3)));
        before.put("c-3", new LinkedHashSet<>(List.of(4, 5)));
        Map<String, Set<Integer>> after = cooperative(before,
                List.of("c-1", "c-2", "c-3", "c-4"), 6);
        after.forEach((c, p) -> System.out.printf("  %s -> %s%n", c, p));
        System.out.println("  只有 c-3 的 5 号分区迁移给 c-4 -> 其余消费者不受影响（增量）");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现 Range 与 Cooperative 两种分配策略，用"3->4 消费者扩容"场景对比迁移的分区数量（Cooperative 应更少）。
 * 2. 复现"处理过慢导致 Rebalance"：模拟消费者处理 200ms/条、`max.poll.interval.ms=5s`，观察被踢出组与重复消费。
 * 3. 思考题：为什么"分区数 = 消费并行度上限"？从"组内分区互斥"的分配规则推导，并说明分区数规划的依据。
 * ============================================================
 */
