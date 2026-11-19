/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：66-06
 * 课名：无侵入埋点：方法耗时与链路
 * ------------------------------------------------------------
 * 知识点：
 *   - **无侵入埋点的价值**：业务代码零改动就获得"每个方法的耗时/异常/调用链"——**这是 APM 的核心能力**（42-07 的自动探针），也是"老系统接入可观测"的唯一低成本路径（改造代码的成本远高于挂 Agent）。
 *   - **埋点的三要素**：**方法耗时**（enter 记开始时间，exit 算差）、**异常捕获**（exit 拿 Throwable，上报异常类型与栈）、**调用链**（enter 把 `traceId/spanId` 压入 ThreadLocal，exit 弹出——**跨线程要传递** 42-07 的断裂点）。
 *   - **切点选择的工程权衡**：**全量方法**（开销大、噪音多）、**按包/注解过滤**（推荐：只增强 `@Trace` 或 controller/service 层）、**按框架匹配**（Spring Controller、JDBC、Redis 客户端——APM 插件式设计）。
 *   - **开销控制**：单次增强的开销约**几十纳秒**（纳秒计时 + ThreadLocal 读写）——**但乘以亿级调用就不可忽略**；对策：**采样**（按比例记录，42-08）、**异步上报**（队列 + 批量，不能同步打日志）、**关闭开关**（出问题可整体禁用）。
 *   - **与 42 章的关系**：42 章讲"三支柱与 trace 模型"，本章讲"trace 数据是怎么被采集的"——**Agent 是数据的生产者**。
 *   - 易错点：埋点里同步写日志/网络（性能直接崩）；ThreadLocal 不清理（线程池污染）；全量增强（开销爆炸）；无采样（存储爆炸）。
 * ============================================================
 */

package com.heima.ch66;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class TracingAgent {
    /** 跨线程的追踪上下文（42-07 的 ThreadLocal 方案） */
    static final ThreadLocal<String> TRACE = new ThreadLocal<>();
    /** 采集到的 Span */
    static final List<String> spans = new ArrayList<>();
    /** 采样率：1/100 */
    static final int SAMPLE_EVERY = 100;
    static int callSeq = 0;

    static boolean sampled() {
        callSeq++;
        return callSeq % SAMPLE_EVERY == 0;
    }

    /** 被增强的业务方法（模拟） */
    static String queryOrder(String orderId) {
        String traceId = TRACE.get();
        boolean sample = sampled();
        long start = System.nanoTime();
        try {
            if (orderId.equals("bad")) {
                throw new IllegalStateException("order not found");
            }
            return "order-" + orderId;
        } finally {
            long costNs = System.nanoTime() - start;
            if (sample) {
                spans.add(String.format("span queryOrder trace=%s cost=%dns", traceId, costNs));
            }
        }
    }

    static void handleRequest(String requestId) {
        String traceId = "trace-" + requestId;
        TRACE.set(traceId);
        try {
            queryOrder("9001");
            queryOrder("9002");
            try {
                queryOrder("bad");
            } catch (IllegalStateException e) {
                spans.add("error queryOrder trace=" + traceId + " " + e.getMessage());
            }
        } finally {
            TRACE.remove();                    // 必须清理（线程池复用）
        }
    }

    static void overheadAnalysis() {
        System.out.println("【单次埋点开销的放大效应】");
        long perCallNs = 80;                   // 纳秒计时 + ThreadLocal + 判断
        for (long qps : new long[]{1_000, 100_000, 1_000_000}) {
            double cpuMsPerSec = qps * perCallNs / 1_000_000.0;
            System.out.printf("  QPS=%8d -> 埋点 CPU 占用 %.1fms/s（%.1f%% 单核）%n",
                    qps, cpuMsPerSec, cpuMsPerSec / 10);
        }
        System.out.println("  对策：按包过滤 + 采样 + 异步上报（42-08）");
        System.out.println();
    }

    public static void main(String[] args) {
        System.out.println("【100 个请求（采样 1/100）】");
        for (int i = 0; i < 100; i++) {
            handleRequest(String.valueOf(i));
        }
        System.out.println("  采集到 span/error 条数：" + spans.size());
        spans.forEach(s -> System.out.println("  " + s));
        System.out.println();
        System.out.println("【ThreadLocal 清理验证】");
        System.out.println("  主线程 TRACE 已清理：" + (TRACE.get() == null));

        System.out.println();
        overheadAnalysis();
        System.out.println("【切点选择对比】");
        Map<String, String> cut = new LinkedHashMap<>();
        cut.put("全量方法", "开销最大、噪音最多（不推荐）");
        cut.put("按包/注解", "@Trace 标注的 service 层（推荐）");
        cut.put("按框架", "Controller/JDBC/Redis 客户端（APM 插件式）");
        cut.forEach((k, v) -> System.out.printf("  %-8s %s%n", k, v));
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现完整埋点：耗时 + 异常 + traceId 传递 + 采样 + 异步队列，用 1000 次调用统计开销与采样命中率。
 * 2. 复现"ThreadLocal 未清理"的问题：线程池复用下 traceId 串号，验证清理后修复。
 * 3. 思考题：为什么埋点不能同步上报？从"业务线程被 IO 阻塞"与"上报失败影响业务"两个角度分析。
 * ============================================================
 */
