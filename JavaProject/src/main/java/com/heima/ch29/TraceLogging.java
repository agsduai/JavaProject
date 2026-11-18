/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：29-10
 * 课名：链路追踪与日志聚合
 * ------------------------------------------------------------
 * 知识点：
 *   - 微服务下一个请求跨多个服务，传统单机日志无法串起来。**链路追踪**用 `traceId`（整条链路）+ `spanId`（单个调用）把调用树还原出来。
 *   - 传递方式：HTTP 头（`traceparent`/`X-B3-TraceId`）、MQ 消息属性、线程上下文（`ThreadLocal` + 线程池透传）。**异步和线程池是 traceId 最容易丢的地方**。
 *   - 采样：全量采集成本太高，通常按 1%~10% 采样，但**出错的请求必须 100% 采集**（尾部采样）。
 *   - 日志聚合：ELK/Loki 集中存储，按 `traceId` 一次查出整条链路的所有日志。日志格式必须结构化（JSON），字段固定：时间、级别、traceId、服务名、方法、耗时、业务键。
 *   - 关键指标：每个 span 的耗时、下游错误率、慢调用 Top N。用这些定位"到底慢在哪一跳"。
 *   - 易错：只在入口生成 traceId，下游重新生成导致链路断裂；日志里打业务对象 `toString()` 带出敏感信息；日志级别全开导致磁盘打满。
 * ============================================================
 */

package com.heima.ch29;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class TraceLogging {
    /** 一个 span：一次方法/一次远程调用 */
    record Span(String traceId, String spanId, String parentId, String service,
                String method, long startMs, long costMs, boolean error) { }

    static final List<Span> SPANS = new ArrayList<>();
    static long clock = 0;

    static String newId(String prefix, int seq) {
        return prefix + "-" + Integer.toHexString(seq);
    }

    static Span record(String traceId, String spanId, String parentId, String service,
                       String method, long costMs, boolean error) {
        Span s = new Span(traceId, spanId, parentId, service, method, clock, costMs, error);
        SPANS.add(s);
        clock += costMs;
        return s;
    }

    public static void main(String[] args) {
        String traceId = "trace-4a1f";
        System.out.println("=== 一次下单请求的调用树（traceId=" + traceId + "）===");

        Span gateway = record(traceId, newId("s", 1), null, "gateway", "POST /api/orders", 0, false);
        Span order = record(traceId, newId("s", 2), gateway.spanId(), "order-service", "createOrder", 0, false);
        Span item = record(traceId, newId("s", 3), order.spanId(), "item-service", "deductStock", 120, false);
        Span user = record(traceId, newId("s", 4), order.spanId(), "user-service", "getAddress", 35, false);
        Span points = record(traceId, newId("s", 5), order.spanId(), "points-service", "addPoints", 480, true);
        Span db = record(traceId, newId("s", 6), order.spanId(), "order-service", "insertOrder", 60, false);

        long total = 0;
        for (Span s : SPANS) {
            total += s.costMs();
        }
        SPANS.set(0, new Span(gateway.traceId(), gateway.spanId(), null, gateway.service(),
                gateway.method(), 0, total, false));

        for (Span s : SPANS) {
            String indent = s.parentId() == null ? "" : "  ";
            System.out.printf("%s[%s] %-14s %-18s %5dms %s  spanId=%s parent=%s%n",
                    indent, s.traceId(), s.service(), s.method(), s.costMs(),
                    s.error() ? "ERROR" : "OK", s.spanId(), s.parentId());
        }

        System.out.println();
        System.out.println("=== 定位慢在哪一跳 ===");
        Span slowest = SPANS.get(0);
        for (Span s : SPANS) {
            if (s.costMs() > slowest.costMs()) {
                slowest = s;
            }
        }
        System.out.println("  最慢：" + slowest.service() + "." + slowest.method() + " " + slowest.costMs() + "ms");
        System.out.println("  总耗时 " + total + "ms，慢调用占 " + (slowest.costMs() * 100 / total) + "%");

        System.out.println();
        System.out.println("=== 结构化日志（按 traceId 聚合）===");
        Map<String, String> logLine = new LinkedHashMap<>();
        logLine.put("ts", "2026-11-17T10:00:01.234");
        logLine.put("level", "ERROR");
        logLine.put("traceId", traceId);
        logLine.put("service", "points-service");
        logLine.put("method", "addPoints");
        logLine.put("costMs", "480");
        logLine.put("bizKey", "ORD-20261117-001");
        logLine.put("msg", "下游超时，已进重试队列");
        System.out.println("  " + logLine);
        System.out.println("  一条命令捞出整条链路：grep '" + traceId + "' app-*.log");

        System.out.println();
        System.out.println("=== traceId 最容易丢的三个地方 ===");
        System.out.println("  1. 线程池：提交任务时没把 MDC 上下文带过去 -> 用包装 Runnable 透传");
        System.out.println("  2. MQ：发送时没放进消息头 -> 消费端从 header 恢复 traceId");
        System.out.println("  3. 异步回调：CompletableFuture 默认用公共线程池，上下文丢失");
        System.out.println("  采样：正常请求 1%~10%，错误请求 100%（尾部采样）");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 给示例加一个"按服务统计平均耗时与错误率"的汇总输出。
 * 2. 实现一个包装 `Runnable` 的 `TraceRunnable`，把 traceId 从提交线程传到执行线程。
 * 3. 设计日志字段规范：至少列出 8 个必填字段，并说明敏感字段如何处理。
 * ============================================================
 */
