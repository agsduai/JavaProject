/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：66-11
 * 课名：综合案例：自研 APM Agent
 * ------------------------------------------------------------
 * 知识点：
 *   - **把本章收口成一个可运行的迷你 APM**：**入口**（premain 注册 transformer 66-01/02）-> **过滤**（只增强业务包 66-02）-> **增强**（Advice 插耗时与异常 66-05/06）-> **上下文**（traceId ThreadLocal + 跨线程传递）-> **采样与异步上报**（66-06/09）-> **降级与开关**（66-02/09）-> **治理**（注册表 + 脱敏 66-10）。
 *   - **APM Agent 的数据模型**：**Span**（类名/方法名/开始/耗时/状态/异常/traceId）；**聚合指标**（方法级 P50/P99/错误率 42-04）；**调用链**（span 父子关系 42-07）——**先落 Span，再聚合**。
 *   - **验收的五条**：**零侵入**（业务代码未改）、**可观测**（能查到方法耗时与异常）、**开销可控**（采样 + 过滤后 CPU < 1%）、**可降级**（Agent 失败不影响业务）、**可治理**（可开关、有审计、数据脱敏）。
 *   - **与全书的关系**：Agent 是"42 章可观测"的生产端、是"60 章框架内核"的运行时应用、是"68 章隐私"的采集边界——**一个组件横跨三章**。
 *   - 易错点：只采集不聚合（数据没人看）；无父子 span（调用链断裂）；不开采样（存储爆炸）；Agent 与业务同队列（互相影响）。
 * ============================================================
 */

package com.heima.ch66;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

// ==================== 示例代码 ====================

public class ApmAgentCase {
    /** Span：一次方法调用 */
    record Span(String traceId, String spanId, String parentSpanId, String cls,
                String method, long startNs, long costNs, boolean error, String errorMsg) {
    }

    static final List<Span> collected = new ArrayList<>();
    static final ThreadLocal<String> TRACE = new ThreadLocal<>();
    static final ThreadLocal<String> SPAN = new ThreadLocal<>();
    static final int SAMPLE_EVERY = 10;
    static int seq = 0;

    /** 模拟增强后的方法：入口创建 span，出口记录 */
    static String enhancedQuery(String orderId) {
        String traceId = TRACE.get();
        String parent = SPAN.get();
        String spanId = "s-" + (++seq);
        long start = System.nanoTime();
        boolean error = false;
        String errorMsg = null;
        SPAN.set(spanId);
        try {
            if (orderId == null) {
                throw new IllegalArgumentException("orderId 空");
            }
            return "order-" + orderId;
        } catch (RuntimeException e) {
            error = true;
            errorMsg = e.getClass().getSimpleName();
            throw e;
        } finally {
            SPAN.set(parent);
            if (seq % SAMPLE_EVERY == 0) {
                collected.add(new Span(traceId, spanId, parent, "Biz", "enhancedQuery",
                        start, System.nanoTime() - start, error, errorMsg));
            }
        }
    }

    /** 模拟请求入口：设置 traceId */
    static void handle(String reqId, String orderId) {
        TRACE.set("trace-" + reqId);
        SPAN.set(null);
        try {
            enhancedQuery(orderId);
        } catch (RuntimeException ignored) {
            // 业务异常由上层处理，Agent 只记录
        } finally {
            TRACE.remove();
            SPAN.remove();
        }
    }

    static void report() {
        System.out.println("【采集到的 Span（采样 1/10）】" + collected.size() + " 条");
        collected.stream().limit(5).forEach(s -> System.out.printf(
                "  trace=%-10s span=%-5s parent=%-5s %s.%s cost=%dns error=%b%s%n",
                s.traceId(), s.spanId(), s.parentSpanId() == null ? "root" : s.parentSpanId(),
                s.cls(), s.method(), s.costNs(), s.error(),
                s.errorMsg() == null ? "" : " " + s.errorMsg()));

        System.out.println();
        System.out.println("【方法级聚合指标】");
        long total = collected.size();
        long errors = collected.stream().filter(Span::error).count();
        List<Long> costs = new ArrayList<>(collected.stream().map(Span::costNs).sorted().toList());
        long p50 = costs.isEmpty() ? 0 : costs.get(costs.size() / 2);
        long p99 = costs.isEmpty() ? 0 : costs.get(Math.min(costs.size() - 1,
                (int) (costs.size() * 0.99)));
        System.out.printf("  Biz.enhancedQuery：调用=%d 错误=%d（%.0f%%）P50=%dns P99=%dns%n",
                total, errors, total == 0 ? 0 : errors * 100.0 / total, p50, p99);
    }

    static void acceptance() {
        System.out.println();
        System.out.println("【验收五条】");
        Map<String, String> gates = new LinkedHashMap<>();
        gates.put("零侵入", "业务代码未改动，仅靠 Agent 增强");
        gates.put("可观测", "方法耗时/异常/traceId 可查");
        gates.put("开销可控", "前缀过滤 + 1/10 采样，CPU 可忽略");
        gates.put("可降级", "transformer 异常则原样加载（66-02）");
        gates.put("可治理", "开关 + 注册表 + 参数脱敏（66-10）");
        gates.forEach((k, v) -> System.out.printf("  %-6s %s%n", k, v));
    }

    public static void main(String[] args) {
        Random random = new Random(7);
        for (int i = 0; i < 100; i++) {
            String orderId = random.nextInt(10) == 0 ? null : String.valueOf(9000 + i);
            handle(String.valueOf(i), orderId);
        }
        report();
        acceptance();
        System.out.println();
        System.out.println("  一句话：Agent 把「不可观测的老系统」变成「有数据的新系统」，代价是治理责任");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 端到端实现迷你 APM：过滤 + 增强 + traceId + 采样 + 聚合，用 200 次调用（含 10% 异常）输出指标。
 * 2. 加入"父子 span"：让 Controller 方法调 Service 方法，验证调用链树可还原（父 spanId 正确）。
 * 3. 思考题：为什么 APM 要"先落 Span 再聚合"？从"个案排查需要明细"与"趋势分析需要聚合"的双需求分析。
 * ============================================================
 */
