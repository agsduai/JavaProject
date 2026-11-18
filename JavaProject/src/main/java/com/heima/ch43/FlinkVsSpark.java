/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：43-10
 * 课名：Flink 与 Spark Streaming 概念对比
 * ------------------------------------------------------------
 * 知识点：
 *   - **两家框架的根本差异在时间模型**：Spark Streaming（微批 micro-batch）把流当"一小段一小段的批"（DStream/Structured Streaming 的触发间隔），天然批思维；Flink 是真正的逐事件流引擎（DataFlow 模型），事件时间/水位线/状态是一等公民。**"流批一体"的深度 Flink 更彻底**。
 *   - **微批的代价与收益**：代价是延迟下限=批间隔（100ms~秒级）与窗口语义的近似；收益是吞吐高效、模型简单、复用批优化器。**秒级延迟够用的报表类，微批完全胜任**——不必迷信"真流"。
 *   - **Flink 的核心优势场景**：亚秒延迟（风控/告警）、复杂事件时间语义（精确窗口 + 迟到处理）、大状态（RocksDB）、精确一次 sink（2PC）。Spark 优势：SQL 生态统一、批资源复用、团队已有 Spark 基建。
 *   - **API 演进趋势趋同**：Flink SQL 与 Spark Structured Streaming 的 SQL 接口都支持窗口/水位线/(join)——**声明式 SQL 是实时计算的终局形态**，代码 API 是兜底。选型看团队技能栈与运维能力，而不是 API 花哨程度。
 *   - **运维复杂度对比**：Flink 作业是常驻有状态进程（checkpoint、恢复、升级要 savepoint）；Spark 批任务跑完即退（Spark Streaming 的 trigger AvailableNow 其实是"带流外观的批"）。**把批任务迁移上 Flink 要接受"7×24 服务"的运维等级**。
 *   - **选型决策树**：延迟要求 <1s 或强事件时间 -> Flink；T+1 或分钟级且已有 Spark 体系 -> Spark；两者都不熟且是纯 SQL 报表 -> 云厂商托管（省运维）。**没有银弹，只有场景匹配**。
 *   - 易错点：用微批做亚秒级风控（延迟下限不允许）；把 Flink 作业当批任务（没有 savepoint 就升级 = 状态全丢）；迷信框架基准测试（自己的数据倾斜一次就推翻所有 benchmark）。
 * ============================================================
 */

package com.heima.ch43;

import java.util.ArrayList;
import java.util.List;

// ==================== 示例代码 ====================

public class FlinkVsSpark {
    record Capability(String dim, String flink, String spark, String guidance) {
    }

    static void compare() {
        System.out.println("【能力对比：按场景看差异】");
        List<Capability> rows = List.of(
                new Capability("延迟模型", "逐事件，毫秒级", "微批，触发间隔为下限",
                        "亚秒风控选 Flink"),
                new Capability("事件时间", "一等公民（WM/迟到/窗口）", "Structured Streaming 支持，微批近似",
                        "精确口径选 Flink"),
                new Capability("状态管理", "RocksDB 大状态 + TTL", "状态在批模型内管理",
                        "亿级 key 状态选 Flink"),
                new Capability("SQL 生态", "Flink SQL 成熟", "Spark SQL 最成熟",
                        "纯 SQL 报表两者皆可"),
                new Capability("运维形态", "7×24 常驻作业", "跑完即退（AvailableNow）",
                        "团队运维能力是约束"),
                new Capability("批复用", "批模式有限复用", "同一引擎批流共用",
                        "已有 Spark 基建优先"));
        for (Capability r : rows) {
            System.out.printf("  %-8s %-26s %-28s %s%n", r.dim(), r.flink(), r.spark(), r.guidance());
        }
        System.out.println();
    }

    static void microBatchFit() {
        System.out.println("【微批够用的场景：不要过度设计】");
        List<String> cases = List.of(
                "实时报表（1 分钟刷新可接受）-> 微批 30s 一次，吞吐高效",
                "夜间准实时对账（5 分钟级）-> 微批甚至 cron 批",
                "风控拦截（<1s 必须）-> 只能真流（Flink）",
                "实时特征计算（亚秒 + 大状态）-> Flink 的主场");
        for (String c : cases) {
            System.out.println("  - " + c);
        }
        System.out.println("  判断式：延迟预算决定引擎，而不是“哪个更流行”");
        System.out.println();
    }

    static void opsDifference() {
        System.out.println("【运维形态差异：常驻 vs 即退】");
        List<String> flink = List.of(
                "升级：savepoint 快照 -> 停作业 -> 新版本从 savepoint 恢复（无 savepoint = 状态重算）",
                "扩容：改并行度也要走 savepoint（Rescale 触发状态重分布）",
                "观测：checkpoint 心跳（43-05）+ lag + 反压三件套");
        for (String s : flink) {
            System.out.println("  Flink：" + s);
        }
        List<String> spark = List.of(
                "升级：改代码重跑即可（状态在批间传递或外部化）",
                "失败：重跑该批（幂等 sink 才安全）",
                "观测：每批耗时与积压");
        for (String s : spark) {
            System.out.println("  Spark：" + s);
        }
        System.out.println("  结论：Flink 的能力溢价对应的是运维溢价");
    }

    public static void main(String[] args) {
        compare();
        microBatchFit();
        opsDifference();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 为三个场景（1 分钟报表/亚秒风控/5 分钟对账）分别选引擎并说明理由，形成一份团队选型决策记录。
 * 2. 模拟 Flink savepoint 升级流程：写状态快照 -> 修改逻辑 -> 恢复，对比"无 savepoint 直接重启"的数据差异。
 * 3. 思考题：为什么说"延迟预算决定引擎"？列出从业务需求推导延迟预算的三步方法。
 * ============================================================
 */
