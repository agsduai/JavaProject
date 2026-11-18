/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：42-04
 * 课名：指标模型与时间序列
 * ------------------------------------------------------------
 * 知识点：
 *   - **指标 = 时间序列 + 维度标签**：`http_server_requests_seconds{service="order",route="/order",version="2.4.1"}`。**维度（label）决定可切片性**：带 version 才能做金丝雀对比（41-07），带 route 才能定位慢接口。**维度基数是设计红线**：userId 做 label 会产生百万级序列，直接打爆存储（基数爆炸是监控平台的第一事故）。
 *   - **四种指标类型**：Counter（只增计数：请求总数）、Gauge（瞬时值：连接池大小）、Histogram（分桶：延迟分布，算 P99）、Summary（客户端分位数，难聚合，少用）。**延迟必须用 Histogram**：跨实例聚合需要原始分桶。
 *   - **P99 的正确理解**：99% 的请求比这快。它比平均值敏感（平均值掩盖长尾）、比 Max 稳定（Max 被单次 GC 抖动污染）。**观测长尾用 P99，抓极端异常看 P99.9 或 Max**。
 *   - **采集模型：Pull vs Push**：Prometheus 拉模型（服务暴露 /metrics，Prometheus 定期抓，发现服务挂了=抓不到=up=0）；Push（StatsD/推网关，任务型短进程用）。**Pull 天然带健康检查语义**。
 *   - **时间序列的降采样与聚合**：原始精度 15s 保留 30 天，5m 粒度保留 1 年（ Thanos/VictoriaMetrics 的分层）。查询时的 rate/increase 函数处理 Counter 的单调性（重启清零要用 clamp 处理）。
 *   - **指标的命名纪律**：`<namespace>_<subsystem>_<name>_<unit>`（如 `http_request_duration_seconds`），单位进名字，复数不加。命名混乱的指标库半年后没人敢用。
 *   - 易错点：userId/orderId 当 label（基数爆炸）；用平均值当 SLO（掩盖长尾）；Counter 直接画图忘记 rate（曲线只升不降没有信息量）；指标不带 service/version 标签（跨服务对比无从谈起）。
 * ============================================================
 */

package com.heima.ch42;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

// ==================== 示例代码 ====================

public class MetricsModel {
    /** Histogram 分桶：桶边界（秒），统计落桶数 */
    static double[] BUCKETS = {0.01, 0.05, 0.1, 0.25, 0.5, 1, 2.5, 5, 10};

    static int bucketOf(double seconds) {
        for (int i = 0; i < BUCKETS.length; i++) {
            if (seconds <= BUCKETS[i]) {
                return i;
            }
        }
        return BUCKETS.length;   // +Inf
    }

    static void histogramQuantile() {
        System.out.println("【Histogram -> 分位数：跨实例聚合的基础】");
        int[] counts = {820, 150, 20, 6, 3, 1, 0, 0, 0, 0};   // 各桶累计
        int total = 1000;
        double target = 0.99 * total;
        long cum = 0;
        double p99 = Double.NaN;
        for (int i = 0; i < counts.length; i++) {
            cum += counts[i];
            if (cum >= target && Double.isNaN(p99)) {
                p99 = i < BUCKETS.length ? BUCKETS[i] : Double.POSITIVE_INFINITY;
            }
        }
        System.out.println("  请求延迟分布（1000 次）：");
        System.out.printf("  P50 ≈ %.2fs  P90 ≈ %.2fs  P99 ≈ %.2fs%n", 0.05, 0.25, p99);
        System.out.println("  分桶粒度决定分位数精度：桶太粗 P99 只能落在桶边界上");
        System.out.println("  聚合语义：先合并各实例的桶计数再算分位数（Summary 做不到）");
        System.out.println();
    }

    static void cardinalityDanger() {
        System.out.println("【基数爆炸：label 设计的红线】");
        record Series(String label, int cardinality, String verdict) {
        }
        List<Series> labels = List.of(
                new Series("service", 12, "安全：服务数有限"),
                new Series("route", 86, "安全：接口路径有限"),
                new Series("version", 4, "安全：同时在跑的版本数"),
                new Series("user_id", 5_000_000, "灾难：每个用户一条时间序列"),
                new Series("order_id", 800_000_000, "灾难：存储直接打爆"));
        for (Series s : labels) {
            System.out.printf("  %-9s 基数=%-12d %s%n", s.label(), s.cardinality(), s.verdict());
        }
        System.out.println("  规则：label 的取值必须有限可枚举；无限值进日志不进指标");
        System.out.println();
    }

    static void rateOfCounter() {
        System.out.println("【Counter -> rate：让单调计数有意义】");
        // 每分钟采样一次的请求总数
        TreeMap<Integer, Long> counter = new TreeMap<>();
        long v = 0;
        for (int min = 0; min <= 10; min++) {
            v += 120 + (min % 3) * 30;
            counter.put(min, v);
        }
        System.out.println("  原始曲线（只增）：");
        for (Map.Entry<Integer, Long> e : counter.entrySet()) {
            System.out.printf("    min%02d 总数=%d%n", e.getKey(), e.getValue());
        }
        System.out.println("  rate(2m) 曲线（每分钟增量≈120~180 QPS）才有波动信息");
        System.out.println("  注意：服务重启 counter 归零，rate 函数要能处理负增量");
        System.out.println();
    }

    static void pullVsPush() {
        System.out.println("【Pull 的健康语义】");
        List<String> notes = List.of(
                "Prometheus 每 15s 抓一次 /metrics",
                "实例挂掉 -> 抓取失败 -> up{instance}=0 -> 天然的健康检查指标",
                "短生命周期任务（定时批处理）抓不到 -> 用 PushGateway 主动推",
                "抓取目标靠服务发现（K8s SD），扩缩容自动纳入");
        for (String n : notes) {
            System.out.println("  - " + n);
        }
    }

    public static void main(String[] args) {
        histogramQuantile();
        cardinalityDanger();
        rateOfCounter();
        pullVsPush();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现一个带 Histogram 的模拟指标器：记录 1000 个延迟样本，输出各桶计数与 P50/P90/P99，并对比"实例 A+B 先聚合再算分位"与"各自算分位再平均"的差异。
 * 2. 审计你项目的指标 label：找出基数最大的三个 label，给出处理方案（改日志/降维/删除）。
 * 3. 思考题：为什么延迟监控必须用 Histogram 而 Summary 难以跨实例聚合？从分位数计算的可合并性推导。
 * ============================================================
 */
