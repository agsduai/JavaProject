/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：39-05
 * 课名：分库分表的拆分策略
 * ------------------------------------------------------------
 * 知识点：
 *   - **垂直拆分按业务切，水平拆分按数据切**：垂直分库把订单、商品、用户拆成独立库（第 29 章的服务拆分在数据层的投影）；垂直分表把大字段、低频字段拆出去（订单表拆出 order_detail 的大文本）。水平分表把同一张表按分片键切成 N 张同构表。
 *   - **水平拆分先解决"分片键选谁"**：分片键决定了数据分布，也决定了**所有查询的形状**。选 `user_id`：同一用户的订单在一起，"查某用户的订单"走单分片，但"平台按时间查全部订单"要扫全分片。选 `order_id`：单订单查询快，但用户侧列表要广播。**没有完美分片键，只有业务主路径的取舍**。
 *   - **基因法让多维度都能命中单分片**：把 user_id 的低位"基因"嵌进 order_id（如 order_id = 时间戳 | user_id 低 8 位 | 序列），按 order_id 查和按 user_id 查都路由到同一分片。这是 C 端系统最常用的技巧，39-07 的分布式 ID 会实现它。
 *   - **冗余一份"异构索引表"解决第二个维度**：下单时同时写 order 表（按 user_id 分片）与 order_by_shop 索引表（按 shop_id 分片），商家侧查索引表拿 order_id 列表再回主表。用"一次多写"换"多维度单分片查询"，代价是写放大与一致性维护。
 *   - **不参与分片的查询怎么办**：后台报表、风控全量扫描这类查询不追求实时，走**异构副本**（同步到 ES/数仓/未分片的从库）而不是扫全分片。广播查询是分库分表的头号性能陷阱。
 *   - **中间件两种形态**：SDK 模式（ShardingSphere-JDBC，嵌入应用，无中间层，性能好但升级随应用）与 Proxy 模式（独立代理，语言无关，多一跳延迟）。无论哪种，**跨分片 join、跨分片事务、聚合归并**都是它们的软肋，设计时绕开而不是硬用。
 *   - 易错点：用自增主键当分片键还希望按用户查（必然广播）；分片键选了会变数据的字段（用户换城市？订单改状态？迁移地狱）；一张 800 列的大宽表直接水平拆（先把垂直拆完再水平）；分片后仍写 `JOIN` 多表 SQL（跨分片 join 语义与性能都不可控）。
 * ============================================================
 */

package com.heima.ch39;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class ShardingStrategy {
    record Order(long orderId, long userId, double amount) {
    }

    /** 8 张表：t_order_0 ~ t_order_7，按 user_id 路由 */
    static final Map<Integer, List<Order>> SHARDS = new LinkedHashMap<>();
    static final int SHARD_COUNT = 8;

    static void verticalFirst() {
        System.out.println("【拆分顺序：先垂直，后水平】");
        String[][] steps = {
                {"垂直分库", "订单库 / 商品库 / 用户库 物理独立", "对应微服务边界"},
                {"垂直分表", "order 拆出 order_detail(大文本)、order_stat(低频统计)",
                        "热表变窄，缓存友好"},
                {"水平分表", "order -> t_order_0..7（按 user_id）", "解决单表行数与写入热点"},
                {"水平分库", "每 4 张表一个库实例", "解决单机连接数/磁盘/IO 上限"},
        };
        for (String[] s : steps) {
            System.out.printf("  %-8s %-44s %s%n", s[0], s[1], s[2]);
        }
        System.out.println();
    }

    static int routeByUser(long userId) {
        return (int) (userId % SHARD_COUNT);
    }

    static void shardKeyChoice() {
        System.out.println("【分片键选择：主路径决定一切】");
        List<Order> orders = List.of(
                new Order(90001, 101, 99.0),
                new Order(90002, 102, 199.0),
                new Order(90003, 101, 59.0));
        for (Order o : orders) {
            SHARDS.computeIfAbsent(routeByUser(o.userId()), k -> new ArrayList<>()).add(o);
        }
        for (Map.Entry<Integer, List<Order>> e : SHARDS.entrySet()) {
            System.out.printf("  t_order_%d: %s%n", e.getKey(), e.getValue());
        }
        System.out.println("  按 user_id 查：route(101)=" + routeByUser(101)
                + " -> 只查 1 个分片 ✓");
        System.out.println("  按时间查全部：8 个分片全部广播 -> 后台查询应走异构副本");
        System.out.println();
    }

    static void geneEmbedding() {
        System.out.println("【基因法：把 user_id 基因埋进 order_id】");
        long userId = 101;
        long gene = userId & 0xFF;                    // 取 user_id 低 8 位作基因
        long orderId = (1700000000L << 20) | (gene << 12) | 1234;   // 时间戳|基因|序列
        long extracted = (orderId >> 12) & 0xFF;
        System.out.printf("  userId=%d 基因=%d -> orderId=%d%n", userId, gene, orderId);
        System.out.printf("  从 orderId 反提取基因=%d -> 同一路由分片 t_order_%d%n",
                extracted, extracted % SHARD_COUNT);
        System.out.println("  按 orderId 查与按 userId 查命中同一分片，两个维度都免除广播");
        System.out.println();
    }

    static void indexTable() {
        System.out.println("【冗余索引表：第三个维度（商家侧）怎么办】");
        System.out.println("  主表：t_order（按 user_id 分片）");
        System.out.println("  索引表：t_order_by_shop（按 shop_id 分片，只存 shop_id/order_id/user_id）");
        System.out.println("  商家查订单：先查索引表拿 order_id 列表 -> 回主表取详情（仍在单分片）");
        System.out.println("  代价：双写（同事务或本地消息表）、索引表只增不改、一致性对账");
        System.out.println("  原则：宁可写放大，不可查询广播");
    }

    public static void main(String[] args) {
        verticalFirst();
        shardKeyChoice();
        geneEmbedding();
        indexTable();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 为你的核心业务表确定分片键：列出前 5 个高频查询，标注每个查询在所选分片键下是"单分片"还是"广播"，并给出广播查询的异构方案。
 * 2. 实现基因法 ID：user_id 埋基因生成 order_id，验证两个维度路由到同一分片，写出位段图。
 * 3. 思考题：为什么"宁可写放大，不可查询广播"？从 QPS 与分片数的乘积效应推导广播查询在高峰期的灾难性成本。
 * ============================================================
 */
