/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：65-08
 * 课名：消息存储与漫游
 * ------------------------------------------------------------
 * 知识点：
 *   - **消息存储的三层需求**：**在线推送**（最新消息，内存/缓存）、**离线补发**（未 ACK 消息，按会话 seq）、**历史漫游**（全量历史，分页查询）——**三者共用一份底层存储**（消息表），只是查询维度不同。
 *   - **存储模型的关键决策**：**按会话分片**（conversationId 作为分片键，同会话消息物理相邻，`WHERE convId=? ORDER BY seq DESC LIMIT 20` 极快 39-05）；**row key 设计**（convId + seq 作为主键，天然有序）；**冷热分离**（近 3 个月热存，更早归档/对象存储 39-10）。
 *   - **消息与"会话列表"的分离**：**会话列表是派生数据**（最后一条消息/未读数/时间），**单独存一张轻量表**（每用户每会话一行）——**否则每次打开 App 要扫全部消息表**。
 *   - **漫游的权限与合规**：**消息属于会话成员**（不是单方数据，55-04 双键校验）；**撤回/删除**（用户删除只影响自己视图，服务端保留用于合规或按策略真删 68-10）；**加密存储**（68-04）。
 *   - **与 39/46 章的对照**：消息表是"高写入 + 按会话范围查询"——**分片键选 convId**（不是 userId，因为群聊消息要共享）；**历史查询走分片内 seq 范围**；**全量分析走 CDC 到数仓**（46 章）。
 *   - 易错点：按 userId 分片（群消息要写 N 份，写放大 65-06）；会话列表实时聚合消息表（慢）；历史删除只删客户端（服务端无限涨）；消息无 TTL（存储成本失控）。
 * ============================================================
 */

package com.heima.ch65;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class MessageStorage {
    record StoredMsg(String convId, long seq, String from, String payload, long atMs) {
    }

    /** 分片：convId 哈希到 N 个分片 */
    static final int SHARDS = 4;
    static final List<Map<String, List<StoredMsg>>> shards = new ArrayList<>();

    static {
        for (int i = 0; i < SHARDS; i++) {
            shards.add(new LinkedHashMap<>());
        }
    }

    static int shardOf(String convId) {
        return Math.abs(convId.hashCode()) % SHARDS;
    }

    static void append(String convId, String from, String payload, long atMs) {
        int shard = shardOf(convId);
        List<StoredMsg> list = shards.get(shard)
                .computeIfAbsent(convId, k -> new ArrayList<>());
        long seq = list.size() + 1;
        list.add(new StoredMsg(convId, seq, from, payload, atMs));
        System.out.printf("  写入 conv=%-6s seq=%-3d -> 分片%d（共 %d 条）%n",
                convId, seq, shard, list.size());
    }

    /** 分片内按 seq 倒序取最近 N 条（无需跨分片） */
    static List<StoredMsg> recent(String convId, int limit) {
        List<StoredMsg> list = shards.get(shardOf(convId)).getOrDefault(convId, List.of());
        int from = Math.max(0, list.size() - limit);
        return list.subList(from, list.size());
    }

    /** 历史漫游分页：按 seq 游标（39-03 的游标分页） */
    static List<StoredMsg> history(String convId, long beforeSeq, int limit) {
        List<StoredMsg> list = shards.get(shardOf(convId)).getOrDefault(convId, List.of());
        List<StoredMsg> out = new ArrayList<>();
        for (StoredMsg m : list) {
            if (m.seq() < beforeSeq) {
                out.add(m);
            }
        }
        int from = Math.max(0, out.size() - limit);
        return out.subList(from, out.size());
    }

    static void conversationList(String userId, List<String> convIds) {
        System.out.println("【会话列表（派生数据，单独存储）】");
        for (String convId : convIds) {
            List<StoredMsg> recent = recent(convId, 1);
            long total = shards.get(shardOf(convId)).getOrDefault(convId, List.of()).size();
            System.out.printf("  %s 用户=%s 最后一条=%s 总数=%d%n", convId, userId,
                    recent.isEmpty() ? "无" : recent.get(0).payload(), total);
        }
        System.out.println("  真实系统：这张表每用户每会话一行（最后消息/未读数/时间），不扫消息表");
    }

    public static void main(String[] args) {
        System.out.println("【按会话分片写入】");
        for (int i = 1; i <= 5; i++) {
            append("c-1", "u-1", "消息" + i, i * 1000L);
        }
        append("c-2", "u-3", "另一会话", 6000L);

        System.out.println();
        System.out.println("【最近 2 条（分片内查询）】");
        recent("c-1", 2).forEach(m -> System.out.printf("  seq=%d %s%n", m.seq(), m.payload()));

        System.out.println();
        System.out.println("【历史漫游：seq<4 的最近 2 条】");
        history("c-1", 4, 2).forEach(m -> System.out.printf("  seq=%d %s%n", m.seq(), m.payload()));

        System.out.println();
        conversationList("u-1", List.of("c-1"));
        System.out.println();
        System.out.println("  分片键=convId：同会话物理相邻；群消息只存一份（65-06 读扩散）");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现消息存储：convId 分片 + 最近 N 条 + seq 游标分页，用 1000 条消息验证"查询只碰一个分片"。
 * 2. 实现会话列表表：每用户每会话一行（最后消息 seq/未读数/时间），验证"打开 App 只查这张表"。
 * 3. 思考题：为什么分片键选 convId 而不是 userId？从"群消息只存一份"与"跨用户共享"两个角度分析（65-06 的呼应）。
 * ============================================================
 */
