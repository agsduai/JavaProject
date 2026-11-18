/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：25-06
 * 课名：主从复制、哨兵与集群
 * ------------------------------------------------------------
 * 知识点：
 *   - **主从复制**：从节点 `replicaof 主节点IP 端口` 后全量同步（RDB）+ 增量同步（replication backlog 环形缓冲）。主写从读，分摊读压力；主节点挂了需要人工切换。
 *   - **哨兵（Sentinel）**：独立进程监控主从，主观下线 + 客观下线（多数哨兵认定）后自动选一个从节点升主，并通知客户端新地址。解决"自动故障转移"。
 *   - **Cluster 集群**：数据分片，16384 个槽位均分到多个主节点，每个主节点带从节点；客户端按 key 的 CRC16 取模路由。解决"单机内存与写能力上限"。
 *   - 集群限制：多 key 操作要求 key 在同一槽（用 `{tag}` 哈希标签强制同槽）；不支持多数据库（只有 db0）；事务/Lua 只能操作同槽 key。
 *   - 选型：数据量小于单机内存、只要高可用 -> 主从 + 哨兵；数据量大或写入压力大 -> Cluster。
 *   - 易错：哨兵本身也要部署 3 个以上且奇数；集群至少 3 主 3 从；客户端必须支持集群协议，普通 Jedis 单机连接连不上集群。
 * ============================================================
 */

package com.heima.ch25;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class ClusterTopology {
    public static void main(String[] args) {
        System.out.println("=== 主从复制：读写分离 ===");
        System.out.println("  主节点 6379  <-- 写请求");
        System.out.println("    ├── 从节点 6380  <-- 读请求");
        System.out.println("    └── 从节点 6381  <-- 读请求");
        System.out.println("  同步流程：全量 RDB 传输 -> 增量命令传播 -> 心跳维持");
        System.out.println("  缺点：主节点挂了不会自动切换，需要人工 promote");

        System.out.println();
        System.out.println("=== 哨兵：自动故障转移 ===");
        List<String> steps = List.of(
                "哨兵每秒 PING 主从节点",
                "超时未响应 -> 标记主观下线（sdown）",
                "超过 quorum 个哨兵都认定 -> 客观下线（odown）",
                "哨兵之间选出一个 Leader 执行故障转移",
                "从节点中选数据最新的升为主节点",
                "通知其余从节点复制新主，通知客户端新地址");
        for (int i = 0; i < steps.size(); i++) {
            System.out.println("  " + (i + 1) + ". " + steps.get(i));
        }
        System.out.println("  部署建议：3 个哨兵、奇数个，且不要和主节点同机");

        System.out.println();
        System.out.println("=== Cluster：16384 个槽位分片 ===");
        Map<String, String> slots = new LinkedHashMap<>();
        slots.put("主节点A", "0 - 5460");
        slots.put("主节点B", "5461 - 10922");
        slots.put("主节点C", "10923 - 16383");
        slots.forEach((k, v) -> System.out.printf("  %-8s 槽位 %s%n", k, v));
        System.out.println();
        System.out.println("  key 路由：slot = CRC16(key) % 16384");
        String[] keys = {"user:1", "user:2", "order:100"};
        for (String k : keys) {
            int slot = Math.abs(k.hashCode() * 31) % 16384;
            String owner = slot <= 5460 ? "主节点A" : slot <= 10922 ? "主节点B" : "主节点C";
            System.out.printf("    %-10s -> slot %-5d -> %s%n", k, slot, owner);
        }

        System.out.println();
        System.out.println("=== 多 key 操作要同槽：用 {} 哈希标签 ===");
        String[] bad = {"user:1:name", "user:1:age"};
        String[] good = {"{user:1}:name", "{user:1}:age"};
        System.out.println("  不同槽 -> MGET 报 CROSSSLOT 错误：" + List.of(bad));
        System.out.println("  同一槽 -> 正常执行：" + List.of(good));
        System.out.println("  原理：只对 {} 里的内容做 CRC16，两者算出同一个槽");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 画出一个 3 主 3 从的 Cluster 拓扑，说明任意一个主节点宕机后，集群还能否对外服务。
 * 2. 为什么 Cluster 要求多 key 操作在同一槽？举一个实际业务里会踩到的例子（如购物车批量查询）。
 * 3. 哨兵的 quorum 和 majority 有什么区别？quorum 设为 2、部署 3 个哨兵时，能容忍几个哨兵挂掉？
 * ============================================================
 */
