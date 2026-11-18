/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：46-08
 * 课名：数据质量与治理
 * ------------------------------------------------------------
 * 知识点：
 *   - **数据质量问题的成本随时间指数上涨**：上游悄悄改了枚举值，当天无人发现 -> 下游聚合全错 -> 报表决策错误 -> 一个月后发现 -> 回溯修复所有历史分区。**质量检查必须"在写入时"而不是"在使用时"**。
 *   - **数据契约（data contract）是生产者与消费者的合同**：schema（列名/类型/可空）、语义（单位、口径、枚举值域）、SLA（更新频率、延迟上限）、变更流程（加列兼容、删列走两版本）。**契约以版本化文档 + 自动校验存在**，不是聊天记录。
 *   - **质量规则的四个层级**：**完整性**（非空率、行数波动）、**有效性**（枚举值域、格式、范围）、**一致性**（跨表核对：订单表总额 = 支付表总额）、**及时性**（分区准时产出）。每条规则带**告警阈值与阻断策略**（软警告 vs 硬阻断下游）。
 *   - **血缘（lineage）回答"这个数字从哪来"**：列级血缘把 ADS 报表指标一路追到 ODS 原始字段——**排障与影响面分析的基础设施**：上游表变更，血缘自动列出全部受影响下游（41-09 影响面思想的数据库版）。
 *   - **数据目录（catalog）是治理的门面**：表注册、责任人、业务描述、质量评分、使用热度——**让"找数据"从问人变成搜目录**。没有目录的团队 50% 的时间花在"这张表是什么"上。
 *   - **变更管理**：上游 schema 变更走契约评审（兼容性检查）-> 通知订阅方 -> 灰度切换——**与 41-09 数据库变更协同同构**，只是粒度是表。
 *   - 易错点：质量规则只在报表出事后补（永远慢一步）；契约只有文档没有校验（形同虚设）；血缘只有表级没有列级（影响面太粗）；质量评分没人看（治理变成表格式主义）。
 * ============================================================
 */

package com.heima.ch46;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class DataGovernance {
    record DqRule(String name, String kind, String expr, double threshold, boolean blocking) {
    }

    static void qualityChecks() {
        System.out.println("【DWD.orders 的每日质量检查】");
        List<DqRule> rules = List.of(
                new DqRule("非空率", "完整性", "order_id null 比例", 0.001, true),
                new DqRule("行数波动", "完整性", "今日行数 / 7 日均值", 0.3, true),
                new DqRule("金额范围", "有效性", "amount >= 0 且 < 10万", 0.001, true),
                new DqRule("状态枚举", "有效性", "status ∈ {PAID,REFUNDED,...}", 0, true),
                new DqRule("跨表一致", "一致性", "orders.amount 合计 = payments 合计", 0.005, false));
        for (DqRule r : rules) {
            System.out.printf("  %-8s %-6s %-30s 阈值=%.3f %s%n",
                    r.name(), r.kind(), r.expr(), r.threshold(), r.blocking() ? "硬阻断" : "告警");
        }
        System.out.println("  阻断规则失败 -> 下游分区标记不可用，禁止消费");
        System.out.println();
    }

    static void contract() {
        System.out.println("【数据契约：上游 orders 事件】");
        Map<String, String> schema = new LinkedHashMap<>();
        schema.put("order_id", "string, 非空, 全局唯一");
        schema.put("amount", "decimal(10,2), 非空, >0, 单位=元");
        schema.put("status", "enum(PAID, REFUNDED, CLOSED)");
        schema.put("event_ts", "utc 毫秒, 服务端打点");
        for (Map.Entry<String, String> e : schema.entrySet()) {
            System.out.printf("  %-9s %s%n", e.getKey(), e.getValue());
        }
        List<String> clauses = List.of(
                "SLA：事件延迟 P99 < 60s；断流 > 5min 告警",
                "兼容变更：允许加列；改类型/删列需两版本过渡 + 30 天通知",
                "校验：生产者侧 schema 校验拦截 + 消费者侧质量规则兜底");
        for (String c : clauses) {
            System.out.println("  " + c);
        }
        System.out.println("  契约是版本化文档 + 自动校验，不是口头约定");
        System.out.println();
    }

    static void lineage() {
        System.out.println("【列级血缘：ads.daily_gmv 的来路】");
        List<String> path = List.of(
                "ads.daily_gmv.gmv",
                "  <- dws.orders_hourly.amount_sum",
                "    <- dwd.orders.amount（清洗后）",
                "      <- ods.orders.payload.amt（原始）",
                "        <- MySQL orders.amount（binlog）");
        for (String p : path) {
            System.out.println("  " + p);
        }
        System.out.println("  用途1 排障：GMV 错了 -> 沿血缘逐层核对是哪层引入");
        System.out.println("  用途2 影响面：ods.orders 加列 -> 血缘列出全部下游（41-09 思想）");
        System.out.println();
    }

    static void catalogScore() {
        System.out.println("【数据目录与质量评分】");
        record Entry(String table, String owner, double quality, String heat) {
        }
        List<Entry> entries = List.of(
                new Entry("dwd.orders", "data-lee", 0.98, "高（12 个下游）"),
                new Entry("dwd.clicks", "data-zhao", 0.72, "中（3 个下游）"),
                new Entry("tmp.test_001", "无（僵尸表）", 0.0, "90 天未使用 -> 下线清单"));
        for (Entry e : entries) {
            System.out.printf("  %-14s 责任人=%-10s 质量=%.2f 热度=%s%n",
                    e.table(), e.owner(), e.quality(), e.heat());
        }
        System.out.println("  治理闭环：评分驱动责任人修复；僵尸表走下线流程而不是无限堆积");
    }

    public static void main(String[] args) {
        qualityChecks();
        contract();
        lineage();
        catalogScore();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 为你最核心的 DWD 表写数据契约（schema/语义/SLA/变更流程），并实现三条质量规则的真实校验脚本。
 * 2. 用血缘解决一次"报表数字不对"：从 ADS 指标逐层下钻定位引入层，记录定位耗时。
 * 3. 思考题：为什么"硬阻断"规则要谨慎使用？从"阻断下游产出的连锁影响"与"错误数据扩散"的代价对比分析分级策略。
 * ============================================================
 */
