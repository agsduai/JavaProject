/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：61-11
 * 课名：综合案例：订单超时关闭调度平台
 * ------------------------------------------------------------
 * 知识点：
 *   - **把本章收口成一个真实场景**：订单超时关闭——**触发方式**（时间轮/延迟队列 28-02 vs 定时扫描）、**分片执行**（61-03：按订单 id 分片扫）、**幂等**（61-05：已关闭/已支付的订单跳过，51-03 状态机裁决）、**重试**（61-06：DB 超时可重试，数据异常不重试）、**可观测**（61-09：每次关闭多少单/失败多少/耗时）。
 *   - **核心矛盾是"扫全表还是查索引"**：超时未支付订单的查询必须有索引（`(status, created_at)` 39-02），否则每次扫描全表（千万级订单扫不动）；**任务频率与单次扫描量要平衡**（每分钟扫 1 分钟窗口的订单）。
 *   - **与支付回调的竞态**（51-03 的经典场景）：扫描关单与支付回调同时到达——**状态机只进不退**（已支付则关单失败，进差异单；已关闭则回调触发退款）。
 *   - **分片与扩容**：订单量增长 -> 分片数增加（不是执行器数），**每片处理时间窗内的订单**——这样扩容后两片不会抢同一批订单（用 `id % totalShards` 严格划分）。
 *   - **验收标准**：超时订单关闭时延 < 2 分钟、无重复关闭、无误关已支付订单、失败可重跑、指标可看。
 *   - 易错点：全表扫描（无索引）；关单不校验支付状态（误关已支付订单）；分片按执行器数（扩容后重复处理）；无指标（不知道关了多少/失败多少）。
 * ============================================================
 */

package com.heima.ch61;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class CloseOrderJob {
    record Order(long id, String status, long createdAtMs, long paidAtMs) {
        boolean timeout(long now, long timeoutMs) {
            return status.equals("WAITING_PAY") && now - createdAtMs > timeoutMs;
        }
    }

    /** 分片扫描 + 状态机裁决（只进不退） */
    static void runShard(List<Order> orders, int shard, int totalShards,
                         long now, long timeoutMs) {
        int scanned = 0, closed = 0, skipped = 0;
        for (Order o : orders) {
            if (o.id() % totalShards != shard) {
                continue;                                  // 严格按 id 分片，扩容不重叠
            }
            scanned++;
            if (!o.timeout(now, timeoutMs)) {
                continue;
            }
            if (o.paidAtMs() > 0) {
                skipped++;
                System.out.printf("    订单%d 已支付（%d）-> 跳过关单（状态机裁决）%n",
                        o.id(), o.paidAtMs());
                continue;
            }
            closed++;
        }
        System.out.printf("  分片%d：扫描=%d 关闭=%d 跳过=%d%n", shard, scanned, closed, skipped);
    }

    public static void main(String[] args) {
        long now = 1_000_000;
        long timeout = 15 * 60_000L;
        List<Order> orders = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            String status = i % 4 == 0 ? "PAID" : "WAITING_PAY";
            long createdAt = now - (i < 12 ? 20 * 60_000L : 5 * 60_000L);
            long paidAt = status.equals("PAID") ? now - 60_000 : 0;
            orders.add(new Order(i, status, createdAt, paidAt));
        }
        System.out.println("【订单超时关闭：4 分片执行】");
        for (int shard = 0; shard < 4; shard++) {
            runShard(orders, shard, 4, now, timeout);
        }
        System.out.println();
        System.out.println("【关键设计】");
        Map<String, String> design = new LinkedHashMap<>();
        design.put("索引", "(status, created_at) —— 否则千万订单全表扫（39-02）");
        design.put("分片", "id % 总片数 —— 扩片不重叠、扩执行器不重复");
        design.put("幂等", "已支付/已关闭跳过 —— 与支付回调竞态由状态机裁决（51-03）");
        design.put("频率", "每分钟扫 1 分钟窗口 —— 时延 < 2 分钟且单次量可控");
        design.put("观测", "每片扫描/关闭/跳过数 + 失败告警（61-09）");
        design.forEach((k, v) -> System.out.printf("  %-4s %s%n", k, v));
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 端到端实现超时关单任务：分片扫描 + 状态机裁决 + 幂等 + 指标输出，用 1000 条订单验证"无重复关闭、无误关已支付"。
 * 2. 构造"关单与支付回调竞态"：同一订单同时被关单任务和支付回调处理，验证状态机裁决（已支付则关单失败并进差异单）。
 * 3. 思考题：为什么分片要按"订单 id % 总片数"而不是"按执行器数均分"？从扩容后数据归属的稳定性分析。
 * ============================================================
 */
