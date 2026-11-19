/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：77-10
 * 课名：分库分表后的 SQL 约束
 * ------------------------------------------------------------
 * 知识点：
 *   - **分片键决定路由**：**带分片键的查询 -> 单分片**；**不带分片键 -> 广播到所有分片**（性能杀手）；**跨分片 JOIN/聚合 -> 内存归并或冗余**——**分片后 SQL 不再是"想怎么写就怎么写"**。
 *   - **全局唯一 ID**：**雪花算法**（时间戳 + 机器号 + 序列）、**号段模式**（数据库批量发号）、**UUID**（无序，不推荐做主键）——**分片后自增主键会冲突，必须换 ID 方案**。
 *   - **跨分片查询的四种处理**：**禁止**（产品层面约束）、**广播 + 归并**（小表/低频）、**冗余字段**（把维表字段复制到事实表）、**异构索引表**（按另一维度再分片一份）。
 *   - **分页与排序的坑**：**`ORDER BY time LIMIT 10` 需要每个分片各取 10 条再归并**（归并排序）；**深分页要游标**；**跨分片 COUNT/SUM 要二次聚合**。
 *   - 与 25 章的关系：25 章讲"海量数据与分库分表"的拆分策略，**本章讲拆分后 SQL 与 ID 的工程约束**——**拆分是架构决策，SQL 约束是落地代价**。
 *   - 易错点：用自增 ID 分片（冲突）；不带分片键查询（广播）；跨分片 JOIN（笛卡尔积）；跨分片深分页；不设计异构索引。
 * ============================================================
 */

package com.heima.ch77;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class ShardedSql {
    static final int SHARDS = 4;

    static int shardOf(long orderId) {
        return (int) (orderId % SHARDS);
    }

    /** 简化雪花：41 位时间 + 10 位机器 + 12 位序列 */
    static class Snowflake {
        final long epoch = 1_700_000_000_000L;
        final long workerId;
        long lastTs = -1;
        long seq;

        Snowflake(long workerId) {
            this.workerId = workerId;
        }

        synchronized long nextId(long nowMs) {
            if (nowMs == lastTs) {
                seq = (seq + 1) & 0xFFF;
                if (seq == 0) {
                    while (nowMs <= lastTs) {
                        nowMs++;
                    }
                }
            } else {
                seq = 0;
                lastTs = nowMs;
            }
            lastTs = Math.max(lastTs, nowMs);
            return ((nowMs - epoch) << 22) | (workerId << 12) | seq;
        }
    }

    static void route(String sql, Long orderId) {
        if (orderId != null) {
            System.out.printf("  %-58s -> 分片 %d（单分片）%n", sql, shardOf(orderId));
        } else {
            System.out.printf("  %-58s -> 广播 %d 个分片（危险）%n", sql, SHARDS);
        }
    }

    static List<Long> mergeSorted(List<List<Long>> shardResults, int limit) {
        List<Long> out = new ArrayList<>();
        int[] pos = new int[shardResults.size()];
        while (out.size() < limit) {
            int best = -1;
            long bestVal = Long.MAX_VALUE;
            for (int i = 0; i < shardResults.size(); i++) {
                if (pos[i] < shardResults.get(i).size()) {
                    long v = shardResults.get(i).get(pos[i]);
                    if (v < bestVal) {
                        bestVal = v;
                        best = i;
                    }
                }
            }
            if (best == -1) {
                break;
            }
            out.add(bestVal);
            pos[best]++;
        }
        return out;
    }

    public static void main(String[] args) {
        System.out.println("【分片路由】");
        route("SELECT * FROM orders WHERE order_id = 10086", 10086L);
        route("SELECT * FROM orders WHERE user_id = 7", null);
        route("SELECT * FROM orders WHERE status = 1 ORDER BY created_at", null);

        System.out.println();
        System.out.println("【雪花 ID】");
        Snowflake sf = new Snowflake(3);
        long base = 1_764_000_000_000L;
        for (int i = 0; i < 3; i++) {
            long id = sf.nextId(base + i);
            System.out.printf("  id=%d -> 分片 %d（时间有序，分片均匀）%n", id, shardOf(id));
        }

        System.out.println();
        System.out.println("【跨分片 ORDER BY 归并】");
        Map<Integer, List<Long>> byShard = new LinkedHashMap<>();
        for (int s = 0; s < SHARDS; s++) {
            byShard.put(s, new ArrayList<>());
        }
        for (long id = 1; id <= 40; id++) {
            byShard.get(shardOf(id)).add(id);
        }
        List<List<Long>> lists = new ArrayList<>(byShard.values());
        System.out.println("  各分片（已排序）：" + byShard);
        System.out.println("  全局 LIMIT 10：" + mergeSorted(lists, 10));

        System.out.println();
        System.out.println("【分片后的 SQL 约束】");
        String[][] rules = {
                {"查询必须带分片键", "否则广播全部 shard", "order_id/user_id 二选一"},
                {"禁止跨分片 JOIN", "改冗余字段或异构索引表", "维表字段复制到事实表"},
                {"跨分片分页", "每片取 N 条再归并", "深分页改游标"},
                {"聚合", "各片 COUNT/SUM 后二次聚合", "注意去重（COUNT DISTINCT）"},
                {"自增主键", "换雪花/号段", "保证全局唯一且有序"},
        };
        for (String[] r : rules) {
            System.out.printf("  %-18s %-26s %s%n", r[0], r[1], r[2]);
        }
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现"按 user_id 与 order_id 双维度分片"的路由，说明异构索引表的作用。
 * 2. 模拟 4 个分片的 `ORDER BY created_at LIMIT 20` 归并，验证结果全局有序且不重不漏。
 * 3. 思考题：为什么"分片后自增主键会冲突"？从"每个分片独立自增序列"分析，并给出两种全局 ID 方案。
 * ============================================================
 */
