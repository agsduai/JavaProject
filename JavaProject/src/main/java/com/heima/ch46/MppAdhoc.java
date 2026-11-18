/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：46-07
 * 课名：MPP 与即席查询
 * ------------------------------------------------------------
 * 知识点：
 *   - **MPP（大规模并行处理）引擎服务"即席分析"**：Trino/StarRocks/Doris 在内存中把查询拆到多节点并行执行——**分析师写一条 SQL 秒级出结果**，区别于 Spark 批作业（分钟级调度）。**交互式分析是数据平台体验的分水岭**。
 *   - **CBO（基于代价的优化）依赖统计信息**：行数、NDV（列基数）、直方图——**统计过期的执行计划必然翻车**（本该裁剪的全表扫）。湖表的统计来自 46-02 的文件统计与 ANALYZE 收集。
 *   - **物化视图（materialized view）是预聚合的工程化**：高频查询模式（日 GMV、类目销量）预计算存储——**查询从扫明细变成扫汇总**，秒级变毫秒级；代价是**刷新与一致性**（T+刷新、实时刷新、查询改写命中）。
 *   - **资源隔离是共享集群的秩序**：即席查询大扫描会拖垮整集群——按业务方/查询类型分队列（prod 报表队列 / adhoc 队列），限制扫描上限与并发（42 章限流思想）。**"一条 SQL 打挂数据平台"是真实事故**。
 *   - **查询治理**：慢查询排行榜（42 章 slow log 思想）、扫描量告警、强制 LIMIT、结果集大小限制。**分析师的教育与工具护栏并行**。
 *   - **与湖表的关系**：MPP 引擎直接查 Iceberg（外部表）或把热数据本地化（StarRocks 内表 + 湖加速）——**热数据进引擎、冷数据留湖**是常见的两级架构。
 *   - 易错点：不配资源队列（即席与生产报表互相干扰）；统计信息从不更新（执行计划劣化无人知）；物化视图刷新与源表脱节（数字对不上）；把 MPP 当 OLTP 用（高频点查应该走 MySQL/Redis）。
 * ============================================================
 */

package com.heima.ch46;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

// ==================== 示例代码 ====================

public class MppAdhoc {
    record Query(String sql, String queue, long scanGb, long costMs) {
    }

    static void resourceQueue() {
        System.out.println("【资源队列：一条大 SQL 不能拖垮平台】");
        List<Query> queries = List.of(
                new Query("日报聚合（dwd.orders 30 天）", "prod-etl", 3200, 42_000),
                new Query("分析师：类目销量探索", "adhoc", 180, 8_500),
                new Query("分析师：全表 SELECT * ", "adhoc", 5800, -1));
        for (Query q : queries) {
            String verdict = q.costMs() < 0 ? "拦截：扫描上限 3TB，SELECT * 无 LIMIT"
                    : String.format("执行 %ds（%s 队列）", q.costMs() / 1000, q.queue());
            System.out.printf("  扫描 %5dGB  %-28s -> %s%n", q.scanGb(), q.sql(), verdict);
        }
        System.out.println("  队列隔离：prod 队列保 SLA，adhoc 限并发限扫描");
        System.out.println();
    }

    static void statsMatter() {
        System.out.println("【统计信息过期：执行计划翻车现场】");
        System.out.println("  查询：WHERE dt='2026-11-17' AND shop_id=88");
        System.out.println("  过期统计：shop_id NDV=1000（实际 50 万）-> 误判选择性 -> 放弃分区裁剪 -> 全表扫");
        List<String> fixes = List.of(
                "写入后自动 ANALYZE（统计随数据更新）",
                "慢查询排行榜 + 扫描量告警（劣化当天发现）",
                "执行计划 diff：新计划扫描量突增即告警");
        for (String f : fixes) {
            System.out.println("  对策：" + f);
        }
        System.out.println();
    }

    static void materializedView() {
        System.out.println("【物化视图：高频查询的预计算】");
        record Mv(String def, String refresh, long queryMs, String verdict) {
        }
        List<Mv> mvs = List.of(
                new Mv("daily_gmv：按天聚合 GMV", "每日 0:30 刷新", 45, "命中改写，毫秒级"),
                new Mv("category_sales：类目小时销量", "每小时刷新", 120, "命中"),
                new Mv("任意维度即席查询", "-", 8_500, "不命中 -> 走明细表"));
        for (Mv m : mvs) {
            System.out.printf("  %-26s 刷新=%-12s %dms  %s%n", m.def(), m.refresh(), m.queryMs(), m.verdict());
        }
        System.out.println("  一致性：查询改写必须校验刷新时间，跨刷新窗口的口径要声明（T+0.5h）");
        System.out.println();
    }

    static void hotColdSplit() {
        System.out.println("【两级架构：热数据进引擎，冷数据留湖】");
        List<String> rows = List.of(
                "近 90 天明细 -> StarRocks 内表（本地列存，点查毫秒级）",
                "全量历史 -> Iceberg 湖表（Trino 查询，秒级）",
                "加速通道：湖查询的热分区自动缓存进引擎（cache + 异步物化）",
                "收益：明细查询不撑爆引擎存储，历史查询不迁移数据");
        for (String r : rows) {
            System.out.println("  " + r);
        }
        System.out.println("  39-10 冷热分离 + 46-03 生命周期在查询层的延续");
    }

    public static void main(String[] args) {
        resourceQueue();
        statsMatter();
        materializedView();
        hotColdSplit();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 为你的 MPP 集群设计队列与限流规则（队列名/并发/扫描上限），并用一条大 SQL 演示拦截。
 * 2. 演示统计信息过期导致的计划劣化：对比 ANALYZE 前后同一查询的扫描量与耗时。
 * 3. 思考题：为什么"一条 SELECT * 能打挂数据平台"？从共享集群的资源隔离与队列调度机制分析护栏的设计要点。
 * ============================================================
 */
