/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：69-07
 * 课名：归因与转化追踪
 * ------------------------------------------------------------
 * 知识点：
 *   - **归因（Attribution）回答"用户转化了，算哪个广告的功劳"**：用户上午看了广告 A、下午点了广告 B、晚上下单——**这单算 A（曝光归因）还是 B（点击归因）？**
 *   - **四种归因模型**：**最后一次点击**（最常用，简单但有偏）、**首次点击**（看重拉新）、**线性归因**（多个触点平分）、**时间衰减**（越近权重越高）——**模型选择是业务决策**（广告主与平台可能立场不同）。
 *   - **归因窗口**：点击后 **7 天/30 天**内的转化才算——**窗口越长归因越宽松**（广告主更愿意投），但**跨窗口的归因重叠**需要去重（同一转化只能算一次）。
 *   - **转化追踪的实现**：**点击时下发 click_id**（广告平台）-> 落地页/App 携带 click_id -> 转化时回传 click_id -> 平台按 click_id 找到广告并记转化——**click_id 是归因的链路凭据**。
 *   - **与 51/54 章的关系**：转化是"资金事件"（订单），归因是"来源标记"——**订单表要能回溯来源**（51 章审计 + 54 章数据回路）。
 *   - 易错点：同一转化被多个广告重复归因（重复计费/重复算 ROI）；归因窗口无配置（每次改代码）；click_id 丢失（App 跳转/跨域导致断链）；曝光归因与点击归因混算。
 * ============================================================
 */

package com.heima.ch69;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// ==================== 示例代码 ====================

public class Attribution {
    record Touch(String clickId, String adId, String type, long atMs) {
    }

    record Conversion(String orderId, long atMs, double amountCents) {
    }

    /** 最后一次点击归因（窗口内最近一次点击） */
    static String lastClick(List<Touch> touches, Conversion c, long windowMs) {
        Touch best = null;
        for (Touch t : touches) {
            if (t.type().equals("CLICK") && c.atMs() - t.atMs() <= windowMs
                    && c.atMs() >= t.atMs()) {
                if (best == null || t.atMs() > best.atMs()) {
                    best = t;
                }
            }
        }
        return best == null ? "自然流量" : best.adId();
    }

    /** 线性归因：窗口内所有触点平分 */
    static Map<String, Double> linear(List<Touch> touches, Conversion c, long windowMs) {
        List<Touch> valid = new ArrayList<>();
        for (Touch t : touches) {
            if (c.atMs() - t.atMs() <= windowMs && c.atMs() >= t.atMs()) {
                valid.add(t);
            }
        }
        Map<String, Double> credits = new LinkedHashMap<>();
        if (valid.isEmpty()) {
            credits.put("自然流量", c.amountCents());
            return credits;
        }
        double share = c.amountCents() / valid.size();
        for (Touch t : valid) {
            credits.merge(t.adId(), share, Double::sum);
        }
        return credits;
    }

    /** 去重：同一订单只归因一次（按 orderId） */
    static void dedupe(List<Conversion> conversions) {
        Set<String> seen = new LinkedHashSet<>();
        for (Conversion c : conversions) {
            if (!seen.add(c.orderId())) {
                System.out.println("  订单 " + c.orderId() + " 重复回传 -> 忽略（归因去重）");
            }
        }
    }

    public static void main(String[] args) {
        long hour = 3_600_000L;
        List<Touch> touches = List.of(
                new Touch("c-1", "ad-A", "IMPRESSION", 1 * hour),
                new Touch("c-1", "ad-A", "CLICK", 1 * hour + 60_000),
                new Touch("c-2", "ad-B", "IMPRESSION", 5 * hour),
                new Touch("c-2", "ad-B", "CLICK", 5 * hour + 30_000),
                new Touch("c-3", "ad-C", "CLICK", 20 * hour));
        Conversion conv = new Conversion("o-1", 21 * hour, 6900);

        System.out.println("【触点序列】");
        touches.forEach(t -> System.out.printf("  %-4s %-5s %s @%d h%n",
                t.clickId(), t.adId(), t.type(), t.atMs() / hour));

        System.out.println();
        System.out.println("【转化】订单 " + conv.orderId() + " 金额 " + conv.amountCents() + " 分");
        long window = 24 * hour;
        System.out.printf("  最后一次点击归因 -> %s%n", lastClick(touches, conv, window));
        System.out.printf("  线性归因 -> %s%n", linear(touches, conv, window));

        System.out.println();
        System.out.println("【归因窗口的影响】");
        for (long w : new long[]{1 * hour, 10 * hour, 24 * hour}) {
            System.out.printf("  窗口 %2d 小时 -> 最后点击归因=%s，线性参与触点=%d 个%n",
                    w / hour, lastClick(touches, conv, w), linear(touches, conv, w).size());
        }

        System.out.println();
        System.out.println("【去重】");
        dedupe(List.of(conv, conv));

        System.out.println();
        System.out.println("【四种归因模型】");
        Map<String, String> models = new LinkedHashMap<>();
        models.put("最后点击", "算最近一次点击；主流，偏重收口环节");
        models.put("首次点击", "算最早一次触点；偏重拉新");
        models.put("线性", "所有触点平分；公平但分散");
        models.put("时间衰减", "越近权重越高；折衷");
        models.forEach((k, v) -> System.out.printf("  %-6s %s%n", k, v));
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现四种归因模型，用同一批触点与转化对比各自分到的金额，说明差异来源。
 * 2. 实现归因去重：同一订单被重复回传 3 次，只记一次，验证金额不重复累计。
 * 3. 思考题：为什么"归因窗口"是广告主与平台的博弈点？从"平台想算更多转化"与"广告主要求真实性"分析。
 * ============================================================
 */
