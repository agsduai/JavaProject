/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：43-08
 * 课名：反压与性能调优
 * ------------------------------------------------------------
 * 知识点：
 *   - **反压（backpressure）是流系统的"堵车"**：下游算子处理不过来，压力沿数据流向上游传导，最终表现为 **Source 消费延迟（lag）持续增长**。它是流作业最常见的病，症状是"lag 涨"，根因却五花八门。
 *   - **定位反压的标准路径**：先看 lag（确认堵了）→ 看 Flink WebUI 的反压状态（哪个算子繁忙背压高）→ 看该算子的火焰图（42-09）→ 找热点。**热点通常四类：数据倾斜、外部调用慢、状态过大、序列化开销**。
 *   - **数据倾斜是流处理的第一性能杀手**：某个 key（大卖家、爆款商品、某个用户）占 50% 流量，单并行子任务累死其他空闲。对策：**加盐打散**（key 加随机后缀聚合两次）、**局部预聚合**（先本地聚合再全局）、热点 key 单独通道。
 *   - **外部调用是第二杀手**：算子里同步调 HTTP/DB，单条 50ms × 1000 QPS = 并发不够直接堵。对策：**批量调用**（攒 100 条一次查）、**异步 IO**（AsyncFunction 并发多路）、**本地缓存**（维表 43-07）。
 *   - **状态过大拖慢一切**：RocksDB 读写放大、checkpoint 越来越久。对策：TTL（43-05）、状态键设计（少存大对象）、增量 checkpoint。
 *   - **扩容的边界**：并行度提高受三面墙限制——Source 分区数（Kafka 分区 12 个，并行度 24 也是白搭）、keyBy 后的 key 数（倾斜 key 无法再分）、下游系统容量（DB 扛不住更高并发写）。**扩容前先确认瓶颈不是代码**。
 *   - 易错点：lag 一涨就加机器（倾斜时加多少都堵在同一个子任务）；反压只看 CPU（CPU 空闲也会反压——在等外部 IO）；批量大小拍脑袋（太大 checkpoint 变重）；忽略序列化（对象太大，网络传输成瓶颈）。
 * ============================================================
 */

package com.heima.ch43;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class Backpressure {
    static void locate() {
        System.out.println("【反压定位路径：lag -> 算子 -> 火焰图】");
        record Stage(String op, String status, String hint) {
        }
        List<Stage> stages = List.of(
                new Stage("source(kafka)", "繁忙(背压高)", "被下游拖住，不是根因"),
                new Stage("map(解析)", "空闲", "正常"),
                new Stage("keyBy+window", "繁忙(背压高)", "嫌疑最大 -> 看子任务分布"),
                new Stage("sink(jdbc)", "繁忙(背压高)", "也可能是根因 -> 看写入耗时"));
        for (Stage s : stages) {
            System.out.printf("  %-14s %-12s %s%n", s.op(), s.status(), s.hint());
        }
        System.out.println("  WebUI 显示 window 子任务：subtask-0 繁忙、subtask-1~3 空闲 -> 数据倾斜");
        System.out.println();
    }

    static void skewAndFix() {
        System.out.println("【数据倾斜：加盐打散两次聚合】");
        record RawKey(String key, int records) {
        }
        List<RawKey> raw = List.of(
                new RawKey("shop=001（爆款）", 520),
                new RawKey("shop=002", 30),
                new RawKey("shop=003", 25));
        for (RawKey r : raw) {
            System.out.printf("  原始分布 %s: %d 条 -> 单子任务扛 52%% 流量%n", r.key(), r.records());
        }
        System.out.println("  预聚合（两阶段）：");
        Map<String, Integer> salted = new LinkedHashMap<>();
        salted.put("shop=001#0", 180);
        salted.put("shop=001#1", 170);
        salted.put("shop=001#2", 170);
        for (Map.Entry<String, Integer> e : salted.entrySet()) {
            System.out.printf("    局部聚合 %s -> %d 条（打散到 3 个子任务）%n", e.getKey(), e.getValue());
        }
        System.out.println("    全局聚合：合并加盐分片 -> 结果与不加盐一致");
        System.out.println();
    }

    static void externalCallFix() {
        System.out.println("【外部调用慢：批量 + 异步】");
        double perCall = 50;                    // ms
        int qps = 1000;
        System.out.printf("  同步逐条调外部接口：%.0fms × %d QPS 需要并发 %.0f%n",
                perCall, qps, perCall * qps / 1000);
        System.out.println("  优化一：攒批 100 条一次查 -> 外部调用降为 10 次/s");
        System.out.println("  优化二：AsyncFunction 并发 50 路 -> 吞吐提升 50 倍");
        System.out.println("  优化三：维表本地缓存（43-07）-> 热点 key 免外部调用");
        System.out.println("  注意：异步不改顺序语义要带请求上下文（乱序完成按事件时间重排）");
        System.out.println();
    }

    static void scaleLimit() {
        System.out.println("【扩容的三面墙】");
        String[][] walls = {
                {"Source 分区数", "Kafka 12 分区，并行 24 -> 12 个空转", "先扩分区或确认源是瓶颈"},
                {"key 基数", "keyBy(shopId)，倾斜 shop 无法拆", "加盐或热点单独通道"},
                {"下游容量", "JDBC sink 1200 写/s 到顶", "扩下游连接/批量，或换存储"},
        };
        for (String[] w : walls) {
            System.out.printf("  %-12s %-36s %s%n", w[0], w[1], w[2]);
        }
        System.out.println("  顺序：先代码（倾斜/外部调用/状态），再扩容——加机器是最贵的手段");
    }

    public static void main(String[] args) {
        locate();
        skewAndFix();
        externalCallFix();
        scaleLimit();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 构造一个倾斜数据源（一个 key 占 60%），实测单子任务 lag 与加盐打散后的均衡效果，对比聚合结果一致性。
 * 2. 把同步外部调用改造成异步批量（模拟 50ms 延迟与 1000 QPS），对比改造前后的吞吐与 lag 曲线。
 * 3. 思考题：为什么"CPU 空闲也可能反压"？从算子等待外部 IO 的角度解释，并给出对应的监控指标。
 * ============================================================
 */
