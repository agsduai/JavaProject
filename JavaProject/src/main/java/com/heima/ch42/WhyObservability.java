/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：42-01
 * 课名：为什么需要可观测性
 * ------------------------------------------------------------
 * 知识点：
 *   - **监控回答"系统有没有问题"，可观测性回答"为什么有问题"**：监控是预设问题（CPU 高吗？错误率超了吗？），可观测性是任意问题的探索能力——出了事先看什么、能不能看出东西。**不预设问题也能排查，才是可观测**。
 *   - **三支柱各管一层**：**指标（Metrics）**聚合数字，回答"系统整体健康吗"，便宜、快、适合告警；**日志（Logs）**离散事件明细，回答"当时发生了什么"，量大、可检索；**链路（Traces）**请求的跨服务路径，回答"慢在哪一跳、错在哪一步"。三者通过关联字段（trace_id）互相跳转。
 *   - **白盒监控优于黑盒监控**：黑盒（从外部探测：接口 200 吗）只能告诉你"坏了"；白盒（内部暴露指标：队列堆积、缓存命中率）在用户感知前就告警。**好的监控体系黑白结合，以白盒为主**。
 *   - **可观测性是设计出来的不是加出来的**：代码里埋点（关键路径打指标、异常带上下文日志、请求传递 trace_id）、中间件暴露指标、统一日志格式——**上线后再补可观测性，等于在运行的火车上装仪表盘**。
 *   - **告警与排障的分层**：告警要少而准（对接值班），排障信息要多而全（对接排查）。**把告警当排障信息源用，值班人员会被淹死**——这是 42-06 的主题。
 *   - **成本意识**：日志与链路是存储大户，采样与保留策略（42-08）从第一天就要设计。可观测性平台的成本随业务线性增长，不治理就是隐形的第二套核心系统。
 *   - 易错点：只装了监控组件（Prometheus/ELK）没在代码里埋点（白盒能力为零）；三支柱数据无法关联（日志没带 trace_id，只能靠时间猜）；把"有 Grafana 面板"当成有可观测性（没人看的面板等于没有）；线上排障靠登录机器 grep（无结构化日志时唯一手段，说明平台缺位）。
 * ============================================================
 */

package com.heima.ch42;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class WhyObservability {
    record Symptom(String symptom, String blackBoxSays, String whiteBoxSays) {
    }

    static void monitoringVsObservability() {
        System.out.println("【同一批症状：黑盒 vs 白盒的信息量】");
        List<Symptom> cases = List.of(
                new Symptom("接口偶发 500", "“服务不健康”", "下单库连接池耗尽，等待队列峰值 47"),
                new Symptom("高峰期变慢", "“响应时间上升”", "缓存命中率 99%->82%，回源 QPS 翻倍"),
                new Symptom("夜间 CPU 尖刺", "“凌晨 2 点异常”", "定时任务全量统计走了无索引查询"));
        for (Symptom s : cases) {
            System.out.printf("  %-12s 黑盒：%-12s 白盒：%s%n", s.symptom(), s.blackBoxSays(), s.whiteBoxSays());
        }
        System.out.println("  白盒能力 = 代码里主动埋的指标与日志，组件装上不等于能力出现");
        System.out.println();
    }

    static void threePillars() {
        System.out.println("【三支柱的分工与关联】");
        String[][] rows = {
                {"指标", "聚合数字（QPS/P99/错误率）", "告警与趋势", "便宜，存储 O(采样点)"},
                {"日志", "离散事件明细", "查明细节与上下文", "贵，保留期短"},
                {"链路", "一次请求的跨服务路径", "定位慢/错在哪一跳", "按采样存储"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-4s %-26s %-12s %s%n", r[0], r[1], r[2], r[3]);
        }
        Map<String, String> join = new LinkedHashMap<>();
        join.put("告警(指标)", "P99 超阈值 -> 点开该时段日志");
        join.put("日志", "错误日志带 trace_id=a1b2 -> 跳链路");
        join.put("链路", "span order->stock 耗时 800ms -> 锁定下游");
        for (Map.Entry<String, String> e : join.entrySet()) {
            System.out.println("  关联：" + e.getKey() + " -> " + e.getValue());
        }
        System.out.println("  trace_id 是三支柱的通用钥匙，埋点时就该规划");
        System.out.println();
    }

    static void instrumentChecklist() {
        System.out.println("【代码侧埋点清单：上线前自检】");
        List<String> items = List.of(
                "每个对外接口：QPS、错误率、延迟分位数（带 route 与 version 标签）",
                "关键业务事件打业务指标：下单量、支付成功率",
                "异常日志带上下文：userId/orderId/trace_id，不打印敏感字段",
                "资源指标：连接池使用率、队列长度、缓存命中率（白盒核心）",
                "trace_id 全链路传递：HTTP 头 -> RPC -> MQ 消息头",
                "日志结构化：JSON 格式，禁止拼字符串");
        for (String i : items) {
            System.out.println("  [ ] " + i);
        }
    }

    public static void main(String[] args) {
        monitoringVsObservability();
        threePillars();
        instrumentChecklist();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 对你负责的服务做一次"黑盒/白盒盘点"：列出当前能回答与不能回答的 5 个问题，补齐缺失的埋点。
 * 2. 实现"三支柱关联"的最小原型：一条错误日志带 trace_id，一个指标带同 trace 维度，演示告警 -> 日志 -> 链路的跳转路径。
 * 3. 思考题：为什么说"上线后再补可观测性像在运行的火车上装仪表盘"？列出三个必须在设计期决定的可观测性参数。
 * ============================================================
 */
