/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：42-05
 * 课名：黄金指标与 SLO
 * ------------------------------------------------------------
 * 知识点：
 *   - **Google SRE 四大黄金信号**：**延迟**（多快，分成功/失败请求看）、**流量**（多少请求）、**错误**（多少失败）、**饱和度**（资源多满：CPU/内存/连接池/队列）。任何服务先补齐这四类指标再谈别的——**四信号覆盖 80% 的事故发现**。
 *   - **SLI/SLO/SLA 的层级**：SLI 是指标（P99 延迟、成功率），SLO 是目标（P99 < 200ms 占 99.9%），SLA 是对外违约条款（SLO + 赔付）。**工程团队对 SLO 负责，商务对 SLA 负责**。
 *   - **错误预算（error budget）是 SLO 的工程化表达**：SLO 99.9% = 每月 43 分钟不可用预算。预算花完 -> 冻结功能发布只修可靠性；预算充足 -> 可以激进发布。**错误预算把"稳定 vs 快速迭代"的争吵变成数字决策**。
 *   - **SLO 要按用户体验定义而不是按机器定义**：用户关心"下单成功率"而不是"CPU<80%"。基础设施指标是原因，SLO 是结果——**告警挂在结果上，排障看原因**。
 *   - **依赖服务的 SLO 传导**：你的服务依赖库存服务，它的 P99 800ms 会吃掉你的延迟预算。**依赖方的 SLO 是你 SLO 的输入**，跨团队 SLO 协议（谁对谁承诺什么）是微服务治理的一部分。
 *   - **饱和度信号最有预见性**：连接池 85%、磁盘 80%、队列持续增长——这些信号在事故前就亮。**延迟/错误是事故本身，饱和度是事故的前兆**。
 *   - 易错点：SLO 定 100%（不可能达成，告警必然误报）；SLO 只看平均值（掩盖长尾）；错误预算花完还继续冲需求（SLO 形同虚设）；每个指标都设告警（告警通胀，42-06 处理）。
 * ============================================================
 */

package com.heima.ch42;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class SloGoldenSignals {
    record Signal(String name, String question, String example, String role) {
    }

    static void goldenFour() {
        System.out.println("【四大黄金信号：订单服务】");
        List<Signal> signals = List.of(
                new Signal("延迟", "用户等多久？", "P99: 成功 180ms / 失败 950ms", "SLO 主指标"),
                new Signal("流量", "系统扛多少？", "QPS 1200，大促 8000", "容量规划输入"),
                new Signal("错误", "失败率多少？", "HTTP 5xx 0.05% + 业务失败 0.3%", "SLO 主指标"),
                new Signal("饱和度", "资源还剩多少？", "连接池 62%，队列 12，磁盘 55%", "事故前兆"));
        for (Signal s : signals) {
            System.out.printf("  %-4s %-10s %-34s %s%n", s.name(), s.question(), s.example(), s.role());
        }
        System.out.println("  延迟要分开成功/失败请求：失败快速返回会拉低延迟假象");
        System.out.println();
    }

    static void errorBudget() {
        System.out.println("【错误预算：把稳定性变成可花的钱】");
        double slo = 99.9;
        int minutesPerMonth = 30 * 24 * 60;
        double budget = minutesPerMonth * (1 - slo / 100);
        System.out.printf("  SLO %.1f%% -> 每月不可用预算 %.0f 分钟（30 天口径）%n", slo, budget);
        record Spend(String event, double minutes) {
        }
        List<Spend> spends = List.of(
                new Spend("11-05 发布故障", 12),
                new Spend("11-12 依赖库存服务故障", 18),
                new Spend("11-20 慢查询拖垮主库", 9));
        double used = 0;
        for (Spend s : spends) {
            used += s.minutes();
            System.out.printf("  %-18s -%.0f 分钟（累计 %.0f/%.0f）%n", s.event(), s.minutes(), used, budget);
        }
        System.out.printf("  预算余额 %.0f 分钟 -> %s%n", budget - used,
                budget - used < 5 ? "耗尽：冻结功能发布，专注可靠性" : "健康：可正常迭代");
        System.out.println();
    }

    static void sloDesign() {
        System.out.println("【SLO 设计：用户视角 + 分层传导】");
        Map<String, String> slis = new LinkedHashMap<>();
        slis.put("下单成功率", "（成功单数/下单请求数）> 99.95%");
        slis.put("下单 P99", "< 300ms（其中库存依赖承诺 P99 < 150ms）");
        slis.put("查询可用性", "月度 99.9%");
        for (Map.Entry<String, String> e : slis.entrySet()) {
            System.out.printf("  %-10s %s%n", e.getKey(), e.getValue());
        }
        System.out.println("  反例：CPU < 80% 不是 SLO（那是原因不是结果）");
        System.out.println("  传导：依赖方 SLO 破约 = 你的预算被扣 -> 跨团队 SLO 协议");
        System.out.println();
    }

    static void saturationFirst() {
        System.out.println("【饱和度是前兆：按剩余量告警】");
        record Resource(String name, String warn, String critical, String lead) {
        }
        List<Resource> rs = List.of(
                new Resource("DB 连接池", "> 80% 持续 5m", "> 90%", "连接池耗尽前 10 分钟"),
                new Resource("磁盘", "> 75%", "> 85%", "提前数天"),
                new Resource("MQ 积压", "> 5 万条", "> 20 万", "消费能力不足的前兆"),
                new Resource("线程池队列", "> 100", "持续增长", "线程耗尽前 5 分钟"));
        for (Resource r : rs) {
            System.out.printf("  %-10s WARN %-14s CRITICAL %-8s 领先=%s%n",
                    r.name(), r.warn(), r.critical(), r.lead());
        }
        System.out.println("  饱和度告警的价值：在用户感知前介入");
    }

    public static void main(String[] args) {
        goldenFour();
        errorBudget();
        sloDesign();
        saturationFirst();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 为你的核心服务定义一套 SLO：3 条 SLI、目标值、测量口径，并算出对应的月度错误预算。
 * 2. 实现错误预算记账器：输入本月故障清单（时长），输出预算消耗曲线与"是否冻结发布"的判定。
 * 3. 思考题：为什么饱和度告警能"在用户感知前介入"而错误率告警不能？从两类信号的物理含义推导各自的最佳告警时机。
 * ============================================================
 */
