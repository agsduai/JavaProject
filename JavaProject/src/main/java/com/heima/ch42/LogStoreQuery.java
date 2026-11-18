/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：42-03
 * 课名：日志存储与查询
 * ------------------------------------------------------------
 * 知识点：
 *   - **日志存储的核心矛盾是"量大与可查"**：一个中型服务一天几十亿条日志，全量存 ES 成本爆炸。**分层存储是标准解**：热数据（7~14 天，ES 可检索）、温数据（30~90 天，压缩归档可回捞）、冷数据（合规期，对象存储）。**保留期按排障真实需求定，不按"以防万一"定**。
 *   - **索引按天滚动与查询路由**：`log-2026.11.17` 每天一个索引，查询带时间范围就只碰相关索引——**不带时间范围的日志查询是平台事故**（全索引扫描）。日志平台默认强制时间窗（如最多 7 天）。
 *   - **日志查询的正确姿势**：先缩小范围（服务 + 时间窗 + level）再搜关键词；避免前置通配符（`*error`）；高频查询场景靠字段过滤而不是全文匹配。**让排障的人第一次查询就能命中，靠的是规范的字段而非搜索技巧**。
 *   - **日志量的治理**：采样（DEBUG 按比例打）、聚合（循环日志合并成一条汇总）、分级开关（生产只 INFO+）。**日志量治理是成本工程**：一条日志的成本 = 写入 + 存储 + 索引 + 查询。
 *   - **从日志到证据链**：排障时日志的价值在于"时间线重建"——同一 trace_id 的日志按时间排序就是一次请求的完整生命史；同一 orderId 跨天检索就是业务对象的变更史。**日志平台的导出与分享功能（永久链接）是协作的基础**。
 *   - 易错点：日志量随业务增长没人管（成本失控）；堆栈多行被拆散导致检索出来的错误日志不可读；把日志平台当调试终端（DEBUG 全量开）；敏感字段明文入库（合规风险 + 泄露面扩大）。
 * ============================================================
 */

package com.heima.ch42;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

// ==================== 示例代码 ====================

public class LogStoreQuery {
    record LogEntry(String ts, String level, String service, String traceId,
                    String message, String hot) {
    }

    /** 模拟按天索引：查询只碰时间窗内的索引 */
    static List<String> dailyIndices(String from, String to) {
        List<String> all = List.of("log-2026.11.10", "log-2026.11.11", "log-2026.11.12",
                "log-2026.11.13", "log-2026.11.14", "log-2026.11.15", "log-2026.11.16",
                "log-2026.11.17");
        return all.stream().filter(idx -> {
            String day = idx.substring(4);
            return day.compareTo(from) >= 0 && day.compareTo(to) <= 0;
        }).toList();
    }

    static void tieredStorage() {
        System.out.println("【分层存储：热/温/冷的成本阶梯】");
        String[][] tiers = {
                {"热", "7~14 天", "ES 全文可检索", "最贵：副本+索引"},
                {"温", "30~90 天", "压缩归档，回捞分钟级", "中：对象存储"},
                {"冷", "合规期（如 1 年）", "不可检索，仅审计取证", "最便宜"},
        };
        for (String[] t : tiers) {
            System.out.printf("  %-3s 保留%-10s %-20s 成本=%s%n", t[0], t[1], t[2], t[3]);
        }
        System.out.println("  保留期依据：线上事故 90% 在 7 天内完成复盘 -> 热数据 14 天够用");
        System.out.println();
    }

    static void queryDiscipline() {
        System.out.println("【查询纪律：先收窄再搜索】");
        List<String> indices = dailyIndices("2026.11.15", "2026.11.17");
        System.out.printf("  时间窗 11.15~11.17 -> 只碰 %d 个索引（%s）%n",
                indices.size(), indices);
        String[][] queries = {
                {"service=order AND level=ERROR AND 关键词", "PASS：字段过滤先行"},
                {"*退款失败*", "FAIL：前置通配符全索引扫"},
                {"无时间范围 + 关键词", "FAIL：平台级事故，默认拒绝"},
        };
        for (String[] q : queries) {
            System.out.printf("  查询 %-32s %s%n", q[0], q[1]);
        }
        System.out.println();
    }

    static void timelineRebuild() {
        System.out.println("【时间线重建：trace_id 是排障的骨架】");
        List<LogEntry> logs = List.of(
                new LogEntry("10:00:00.101", "INFO", "gateway", "a1b2", "收到请求 POST /order", "热"),
                new LogEntry("10:00:00.105", "INFO", "order", "a1b2", "校验通过，扣库存", "热"),
                new LogEntry("10:00:00.900", "WARN", "stock", "a1b2", "库存服务超时，重试1", "热"),
                new LogEntry("10:00:01.100", "INFO", "stock", "a1b2", "重试成功", "热"),
                new LogEntry("10:00:01.120", "INFO", "order", "a1b2", "下单成功 9001", "热"));
        logs.stream().sorted(Comparator.comparing(LogEntry::ts))
                .forEach(l -> System.out.printf("  %s [%-5s] %-8s %s%n",
                        l.ts(), l.level(), l.service(), l.message()));
        System.out.println("  结论：stock 服务重试耗时 200ms，整体链路 1.019s —— 一目了然");
        System.out.println("  同一 trace_id 的日志 = 一次请求的完整生命史");
    }

    public static void main(String[] args) {
        tieredStorage();
        queryDiscipline();
        timelineRebuild();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 为你的日志平台设计分层保留策略（热/温/冷的天数与依据），估算每月存储成本并给出一条降本手段。
 * 2. 实现"多行堆栈合并"的最小模拟：给定一组带堆栈的文本行，输出合并后的 JSON 日志条数，验证采集器配置的效果。
 * 3. 思考题：为什么日志平台要强制时间窗？从索引数量与查询扇出推导无界查询的成本曲线。
 * ============================================================
 */
