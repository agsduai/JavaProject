/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：43-11
 * 课名：综合案例：实时大屏（订单成交监控）
 * ------------------------------------------------------------
 * 知识点：
 *   - **把本章串成一条实时链路**：MySQL 订单（binlog）-> CDC 采集（43-09）-> Kafka -> Flink 清洗关联维表（43-07）-> 事件时间窗口聚合（43-02/03）-> 水位线控制关闭（43-04）-> 幂等写入 Redis（43-06）-> 大屏接口读 Redis。**每个环节都对应前面一课的设计决策**。
 *   - **大屏的需求即参数**：GMV 每分钟刷新（滚动窗口 60s + 总累计）、成交笔数、实时趋势线（滑动窗口近 10 分钟）、TOP 商品榜（窗口聚合 + 排序）。**每个指标都能追溯到窗口参数与口径定义**——口径文档（含退款是否扣除）是需求评审的一部分。
 *   - **正确性保障**：事件时间语义（跨天准确）、幂等 upsert（重放无损）、未配对旁路（对账兜底）、与离线口径每日 diff。**大屏数字敢被 CEO 盯，靠的是这条链路**。
 *   - **运维保障**：checkpoint 心跳、lag 监控、反压定位预案（43-08）、水位线空闲源标记、窗口结果"是否最终"标记（前端闪烁提示临时值）。
 *   - **容量与降级**：大促峰值 QPS 提前压测（重点测倾斜 key——爆款商品）、Redis 写入批量合并、降级预案（CDC 断流时大屏显示"数据延迟"而不是停更数字）。
 *   - 易错点：大屏只做"显示"不管口径（数字被质疑时无人能解释）；实时与离线两套口径独立演化（数字打架）；不做峰值倾斜压测（大促当天爆款 key 堵死）；降级预案缺失（CDC 断流大屏冻结没人发现）。
 * ============================================================
 */

package com.heima.ch43;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

// ==================== 示例代码 ====================

public class RealtimeDashboard {
    /** 指标层：Redis 模拟（幂等 upsert） */
    static Map<String, Double> redis = new LinkedHashMap<>();

    record OrderEvent(long id, long eventTs, String shopId, double amount, boolean refunded) {
    }

    static List<OrderEvent> genEvents() {
        List<OrderEvent> events = new ArrayList<>();
        long base = 1763347200000L;
        for (int i = 0; i < 8; i++) {
            events.add(new OrderEvent(9000 + i, base + i * 7000,
                    i % 3 == 0 ? "shop-001(爆款)" : "shop-00" + (i % 3 + 1),
                    69 + i * 10, false));
        }
        return events;
    }

    static void pipeline() {
        System.out.println("【链路总览：CDC -> Kafka -> Flink -> Redis -> 大屏】");
        List<String> steps = List.of(
                "1) MySQL binlog -> CDC（Debezium/Flink CDC）-> topic ods_orders",
                "2) Flink 清洗：过滤测试单 + 维表关联（商品类目，本地缓存）",
                "3) 事件时间滚动窗口 60s：GMV/笔数（keyBy shopId，倾斜加盐）",
                "4) 滑动窗口 10min/1min：趋势线",
                "5) 幂等写 Redis（窗口键主键 upsert）",
                "6) 大屏接口轮询 Redis，展示「是否最终」标记");
        for (String s : steps) {
            System.out.println("  " + s);
        }
        System.out.println();
    }

    static void aggregate() {
        System.out.println("【一分钟滚动窗口聚合（含倾斜加盐）】");
        List<OrderEvent> events = genEvents();
        TreeMap<Long, Double> plain = new TreeMap<>();
        for (OrderEvent e : events) {
            plain.merge(e.eventTs() / 60000, e.amount(), Double::sum);
        }
        for (Map.Entry<Long, Double> w : plain.entrySet()) {
            System.out.printf("  窗口 min%d GMV=%.1f 元（含爆款 shop-001）%n", w.getKey(), w.getValue());
        }
        System.out.println("  倾斜处理：shop-001 加盐 shop-001#0~2 局部聚合，全局合并结果一致");
        System.out.println();
    }

    static void idempotentWrite() {
        System.out.println("【幂等写入与最终标记】");
        // 窗口 [w, w+60s) 首次触发
        redis.put("gmv:11-17:min0", 69.0);
        System.out.println("  T+61s 首次触发：gmv:min0=69.0 (临时)");
        redis.put("gmv:11-17:min0", 139.0);   // 迟到订单更新
        System.out.println("  T+64s 迟到更新：gmv:min0=139.0 (临时)");
        redis.put("gmv:11-17:min0", 139.0);
        System.out.println("  T+364s 水位线越过窗口+5min -> 最终态：gmv:min0=139.0 (final)");
        System.out.println("  大屏契约：临时值带闪烁提示，最终值定格 —— 下游不再误读");
        System.out.println();
    }

    static void ops() {
        System.out.println("【上线保障清单】");
        List<String> items = List.of(
                "口径文档：GMV 是否含退款/取消，与离线对账共用定义",
                "对账：实时 vs 离线每日 diff > 0.5% 告警",
                "压测：大促峰值 + 爆款倾斜 key 专项",
                "监控：checkpoint 心跳 / lag / 反压 / 水位线卡住",
                "降级：CDC 断流 -> 大屏显示“数据延迟 N 分钟”而非冻结数字",
                "回放：原始事件流在 Kafka 保留 24h，统计错误可重放修复");
        for (String i : items) {
            System.out.println("  [ ] " + i);
        }
    }

    public static void main(String[] args) {
        pipeline();
        aggregate();
        idempotentWrite();
        ops();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 端到端实现迷你版实时大屏（内存模拟全链路）：CDC 事件 -> 窗口聚合 -> 幂等写 -> 接口读，注入一笔迟到订单验证"临时 -> 最终"标记。
 * 2. 写出大屏三个指标的口径文档（GMV/笔数/趋势线），包括退款处理、窗口参数、与离线对账的 diff 规则。
 * 3. 思考题：为什么"CEO 盯着的大屏"对可观测性与正确性的要求反而最高？从数字被质疑时的解释链路（口径 -> 窗口 -> 链路）推导。
 * ============================================================
 */
