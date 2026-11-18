/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：38-10
 * 课名：集群、分片与副本
 * ------------------------------------------------------------
 * 知识点：
 *   - **三个角色**：master（管集群元数据：建索引、分配分片）、data（存数据、执行查询）、coordinating（接收请求、归并结果——每个节点都能当协调者，请求发给谁谁就是）。小集群三节点可以身兼三职，大集群按职责分离。
 *   - **分片 = 索引的水平切片**：主分片数在创建时定死（路由公式 `hash(routing) % primary_count`），扩容只能 reindex 或用路由调整（routing 迁移期间双写）。**分片是分布式的单位**：查询要协调节点把请求扇出到所有主/副分片副本，取 top N 归并。
 *   - **副本 = 主分片的拷贝，三重作用**：数据冗余（节点挂了不丢数据）、读扩展（读请求分担到副本）、故障转移（主挂了副本自动提升为主）。**单节点集群副本永远是 unassigned（没地方放）**，这也是本地测试集群 yellow 的原因。
 *   - **集群健康三色**：green（主副全部分配）、yellow（副本未分配，数据不丢但冗余缺失）、red（有主分片丢失，数据真缺了）。报警规则：生产 red 立即处理，yellow 观察。
 *   - **脑裂与 quorum**：老版本网络分区时两个 master 并存导致元数据分裂；现代 ES 用"多数派投票"（7.x 起集群状态变更需过半 master-eligible 节点同意）根治。**master-eligible 节点数保持奇数（3 或 5）**是规划铁律。
 *   - **写入与查询的路由**：写请求协调节点按路由算出目标分片转发；读请求扇出到"主+副"中选择（自适应副本选择）。文档不能改 routing（除非显式指定），否则写入与查询落在不同分片，永远查不到。
 *   - **均衡与水位**：磁盘水位 85% 停止分配新分片、90% 迁移分片、95% 索引只读（flood-stage）。磁盘满导致的"索引只读块"是运维高频事故，恢复要手动放开写入块。
 *   - 易错点：单节点集群配 1 副本（永远 yellow，还白占写入性能）；分片规划过大（几千个空分片的"分片风暴"吃光 master）；滚动重启不看分片分配延迟设置（分片疯狂重平衡）；客户端连了一个节点当"主节点"用（任意节点都是协调节点，没有"主节点专用"的客户端接入点）。
 * ============================================================
 */

package com.heima.ch38;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class EsCluster {
    record Shard(String index, int shardNo, boolean primary, String nodeId, String state) {
    }

    record Node(String id, String role, String ip) {
    }

    static final List<Node> NODES = List.of(
            new Node("node-1", "master+data", "10.0.0.11"),
            new Node("node-2", "data", "10.0.0.12"),
            new Node("node-3", "data", "10.0.0.13"));

    static void topology() {
        System.out.println("【三节点集群与 product 索引的分片分布】");
        for (Node n : NODES) {
            System.out.printf("  %-7s %-12s %s%n", n.id(), n.role(), n.ip());
        }
        List<Shard> shards = List.of(
                new Shard("product", 0, true, "node-1", "STARTED"),
                new Shard("product", 0, false, "node-2", "STARTED"),
                new Shard("product", 1, true, "node-2", "STARTED"),
                new Shard("product", 1, false, "node-3", "STARTED"),
                new Shard("product", 2, true, "node-3", "STARTED"),
                new Shard("product", 2, false, "node-1", "STARTED"));
        System.out.println("  product：3 主分片 + 1 副本，主副本不同节点：");
        for (Shard s : shards) {
            System.out.printf("    %-7s p%-2d %-8s %s  %s%n", s.index(), s.shardNo(),
                    s.primary() ? "primary" : "replica", s.nodeId(), s.state());
        }
        System.out.println("  node-3 挂掉 -> 它上面的副本变主（p2 提升到 node-1 的副本）");
        System.out.println();
    }

    static void routing() {
        System.out.println("【路由公式：写入与查询落在同一分片】");
        long[] docIds = {1001, 1002, 1003, 1004, 1005};
        int primaryCount = 3;
        for (long id : docIds) {
            int shard = (int) (Math.abs(id * 31L) % primaryCount);
            System.out.printf("  doc %d -> shard %d%n", id, shard);
        }
        System.out.println("  公式 shard = hash(routing) % primary_count");
        System.out.println("  推论：主分片数变了路由全变 -> 扩主分片必须 reindex");
        System.out.println("  自定义 routing（如按租户）可以让同一租户的数据落同一分片");
        System.out.println();
    }

    static void healthAndBrainSplit() {
        System.out.println("【集群健康与 master 选举】");
        String[][] colors = {
                {"green", "主+副全部分配", "正常"},
                {"yellow", "副本未分配", "冗余缺失，排查磁盘/单节点"},
                {"red", "主分片丢失", "数据缺失，立即处理"},
        };
        for (String[] c : colors) {
            System.out.printf("  %-7s %-16s %s%n", c[0], c[1], c[2]);
        }
        System.out.println("  master 选举：master-eligible 节点过半才有效（quorum）");
        Map<String, Integer> split = new LinkedHashMap<>();
        split.put("分区A（node-1, node-2）", 2);
        split.put("分区B（node-3）", 1);
        System.out.println("  网络分区：" + split);
        System.out.println("  分区A 有过半(2/3) -> 能选 master 继续服务；分区B 无 master -> 只读/拒绝");
        System.out.println("  规划铁律：master-eligible 用奇数（3 或 5），偶数只会浪费一台");
        System.out.println();
    }

    static void diskWatermark() {
        System.out.println("【磁盘水位：三个阈值对应的系统行为】");
        String[][] marks = {
                {"85%（low）", "不再往该节点分配新分片"},
                {"90%（high）", "开始把分片迁走"},
                {"95%（flood_stage）", "索引进入只读块，写入全部拒绝"},
        };
        for (String[] m : marks) {
            System.out.printf("  %-16s %s%n", m[0], m[1]);
        }
        System.out.println("  高频事故：磁盘满 -> 索引只读 -> 清理磁盘后写入仍失败");
        System.out.println("  恢复：PUT index/_settings 解除 index.blocks.write: true");
    }

    public static void main(String[] args) {
        topology();
        routing();
        healthAndBrainSplit();
        diskWatermark();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 搭一个 3 节点集群（或用 docker 模拟），创建 3 主 1 副索引，逐个 kill 数据节点观察健康颜色变化与副本提升，记录 `_cat/shards` 输出。
 * 2. 用自定义 routing 实验同一文档写入与查询的路由一致性，然后尝试改主分片数，记录报错并解释路由公式。
 * 3. 思考题：为什么"master-eligible 偶数个"是反模式？从 quorum 推导 4 台与 3 台在容忍故障数上的差别。
 * ============================================================
 */
