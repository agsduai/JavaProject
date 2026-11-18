/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：43-02
 * 课名：时间语义：事件时间与处理时间
 * ------------------------------------------------------------
 * 知识点：
 *   - **同一笔订单有三个时间**：**事件时间**（业务发生时刻，订单里的 create_time）、**处理时间**（流系统收到它的机器时钟）、**摄入时间**（进入框架的时刻）。**用哪个时间分桶，决定了统计口径**。
 *   - **处理时间的陷阱在乱序与延迟**：网络抖动让订单 B（11:59:58 生成）比订单 A（11:59:59 生成）晚到——按处理时间分桶，B 掉进了第二天，GMV 在 24 点边界处永远对不上批结果。**事件时间按业务发生时刻分桶，乱序不改变归属**。
 *   - **事件时间的前提是数据带可信时间戳**：源头的钟不准（客户端时间可改）、时钟回拨（39-07 的雪花时钟回拨同一问题）、字段缺失——**时间戳治理是流处理的隐形工程**：服务端打点为主、客户端时间为参考、格式统一 UTC 毫秒。
 *   - **两种语义的适用场景**：处理时间适合"现在系统怎么样"（监控类，低延迟优先）；事件时间适合"业务到底发生了什么"（计费/对账/报表，正确性优先）。**钱相关的统计必须事件时间**。
 *   - **迟到数据的两种命运**：事件时间窗口已关闭后到达的数据——丢弃（丢 GMV）或允许迟到（更新窗口结果，需要窗口能重算，43-04 水位线控制）。**"允许迟到多久"是业务决策**：电商订单通常允许 5~15 分钟。
 *   - **回溯（backfill）的正确性**：事件时间让"重放昨天流量"得到与当时一致的窗口结果——这是流批一体的根基；处理时间重放会得到完全不同的分桶。
 *   - 易错点：用处理时间算 GMV（跨天边界永远对不上）；信任客户端时间戳（可伪造可漂移）；窗口关闭=数据丢失（没设计迟到策略）；时间字段混用秒/毫秒（差三个数量级的分桶）。
 * ============================================================
 */

package com.heima.ch43;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class TimeSemantics {
    record Order(long id, long eventTs, long processTs, double amount) {
        String eventDay() {
            return eventTs >= DAY2 ? "11-18" : "11-17";
        }

        String processDay() {
            return processTs >= DAY2 ? "11-18" : "11-17";
        }
    }

    static final long DAY2 = 1763433600000L;   // 11-18 00:00

    static void disorderDemo() {
        System.out.println("【跨天边界的两笔订单：两种语义的不同归属】");
        List<Order> orders = List.of(
                new Order(101, DAY2 - 1000, DAY2 + 800, 199.0),    // 23:59:59 生成，晚到 0.8s
                new Order(102, DAY2 + 500, DAY2 + 900, 69.0));     // 00:00:00.5 生成
        for (Order o : orders) {
            System.out.printf("  订单%d 事件时间=%s 处理时间=%s%n",
                    o.id(), o.eventDay(), o.processDay());
        }
        System.out.println("  订单 101 事件时间属 11-17，处理时间却落到 11-18");
        System.out.println("  处理时间 GMV：11-17 少 199 元 —— 与批结果永远差这一笔");
        System.out.println("  事件时间 GMV：与批完全一致（按业务发生时刻归属）");
        System.out.println();
    }

    static void timestampGovernance() {
        System.out.println("【时间戳治理：事件时间的前提】");
        String[][] rows = {
                {"客户端打点", "钟不准/可伪造", "仅作参考，服务端打点为准"},
                {"服务端打点", "统一 NTP，相对可信", "主来源（网关入口打点）"},
                {"时钟回拨", "短暂倒退", "单调化处理（39-07 同款方案）"},
                {"单位混乱", "秒 vs 毫秒", "统一 UTC 毫秒，schema 声明"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-10s %-16s %s%n", r[0], r[1], r[2]);
        }
        System.out.println();
    }

    static void latePolicy() {
        System.out.println("【迟到数据的两种命运：业务决策】");
        record LateOrder(long id, int lateSec, String decision, String reason) {
        }
        List<LateOrder> lates = List.of(
                new LateOrder(201, 30, "允许迟到：更新窗口", "GMV 精确性优先"),
                new LateOrder(202, 300, "允许迟到（上限 5 分钟）", "支付回调链路可能慢"),
                new LateOrder(203, 3600, "丢弃 -> 进旁路统计", "一小时前的订单不该影响实时大屏"));
        for (LateOrder l : lates) {
            System.out.printf("  订单%d 迟到 %ds -> %s（%s）%n",
                    l.id(), l.lateSec(), l.decision(), l.reason());
        }
        System.out.println("  “允许迟到多久”写进需求文档，不是技术拍脑袋");
        System.out.println();
    }

    static void replayValue() {
        System.out.println("【事件时间的回放价值：重放=复现】");
        System.out.println("  把昨天的原始事件流按事件时间重放：");
        System.out.println("  事件时间窗口 -> 得到与昨天完全一致的统计（可用于修复错误统计）");
        System.out.println("  处理时间窗口 -> 得到“按今天重放时刻”的统计（毫无用处）");
        System.out.println("  结论：事件时间是流批一体与可修复性的根基");
    }

    public static void main(String[] args) {
        disorderDemo();
        timestampGovernance();
        latePolicy();
        replayValue();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 用示例数据扩展成 1000 单模拟：按两种语义分别统计两天的 GMV，量化处理时间在边界处的误差。
 * 2. 为你的业务制定时间戳治理规范：主时钟源、格式、回拨处理、缺失兜底，并检查现有数据源是否达标。
 * 3. 思考题：为什么"钱相关的统计必须事件时间"？从对账一致性与重放修复两个角度推导。
 * ============================================================
 */
