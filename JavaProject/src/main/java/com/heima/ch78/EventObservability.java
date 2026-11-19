/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：78-09
 * 课名：事件驱动的可观测性
 * ------------------------------------------------------------
 * 知识点：
 *   - **事件链路的 trace 要跨"生产 -> MQ -> 消费"**：**traceId 随消息头传递**，**生产者创建 span，消费者创建子 span**——**没有 traceId，一条事件经过三个服务后无法串联**（42-04 的分布式追踪）。
 *   - **三个关键指标**：**生产速率/失败率**、**消费 lag**、**端到端延迟**（事件产生到消费完成）——**lag 大不代表慢，端到端延迟才代表用户体验**。
 *   - **事件元数据**：**eventId（幂等 78-03）**、**occurredAt（业务时间）**、**traceId（链路）**、**schemaVersion（契约 78-07）**——**缺一个都会在排障时抓瞎**。
 *   - **消费延迟的分解**：**排队延迟（lag/速率）+ 处理延迟（单条耗时）+ 重试延迟**——**分开度量才能定位是"消费不过来"还是"单条太慢"**。
 *   - 与 42 章的关系：42 章的"日志/指标/追踪"三支柱，**在事件驱动里要额外加"事件维度"**——**按 eventType/topic 聚合**。
 *   - 易错点：traceId 不传（断链）；只监控 lag（忽略端到端）；eventId 用随机数（无法去重）；不记录 occurredAt（无法算业务延迟）。
 * ============================================================
 */

package com.heima.ch78;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class EventObservability {
    record Span(String traceId, String spanId, String parentId, String name,
                long startMs, long durationMs) {
        long endMs() {
            return startMs + durationMs;
        }
    }

    record EventMeta(String eventId, String traceId, long occurredAt, String schemaVersion) {
    }

    static final List<Span> spans = new ArrayList<>();

    static Span span(String traceId, String spanId, String parentId, String name,
                     long start, long duration) {
        Span s = new Span(traceId, spanId, parentId, name, start, duration);
        spans.add(s);
        return s;
    }

    static void printTree(String traceId) {
        System.out.println("  trace " + traceId + "：");
        for (Span s : spans) {
            if (!s.traceId().equals(traceId)) {
                continue;
            }
            int depth = 0;
            String parent = s.parentId();
            while (parent != null) {
                depth++;
                String target = parent;
                Span parentSpan = null;
                for (Span x : spans) {
                    if (x.spanId().equals(target)) {
                        parentSpan = x;
                        break;
                    }
                }
                parent = parentSpan == null ? null : parentSpan.parentId();
            }
            System.out.printf("    %s%-22s [%d, %d] %dms%n",
                    "  ".repeat(depth), s.name(), s.startMs(), s.endMs(), s.durationMs());
        }
    }

    public static void main(String[] args) {
        EventMeta meta = new EventMeta("evt-1001", "trace-abc", 1_000, "2");

        System.out.println("【事件元数据】");
        System.out.println("  eventId=" + meta.eventId() + " traceId=" + meta.traceId()
                + " occurredAt=" + meta.occurredAt() + " schemaVersion=" + meta.schemaVersion());

        System.out.println();
        System.out.println("【跨生产/消费的 span】");
        span("trace-abc", "s1", null, "OrderService.createOrder", 1_000, 30);
        span("trace-abc", "s2", "s1", "OutboxRelay.publish", 1_030, 5);
        span("trace-abc", "s3", "s2", "MQ.consume(order.created)", 1_200, 80);
        span("trace-abc", "s4", "s3", "PointsService.addPoints", 1_210, 60);
        span("trace-abc", "s5", "s3", "SmsService.send", 1_280, 40);
        printTree("trace-abc");

        System.out.println();
        System.out.println("【端到端延迟分解】");
        long produced = 1_000;
        long consumed = 1_280 + 40;
        long queueDelay = 1_200 - 1_030;             // 排队 170ms
        long processDelay = 80;                      // 消费处理 80ms
        System.out.printf("  事件产生=%d，消费完成=%d，端到端=%dms%n",
                produced, consumed, consumed - produced);
        System.out.printf("  排队延迟=%dms，处理延迟=%dms%n", queueDelay, processDelay);

        System.out.println();
        System.out.println("【消费 lag 与端到端不是一回事】");
        Map<String, Long> metrics = new LinkedHashMap<>();
        metrics.put("producedRate", 2_500L);
        metrics.put("consumedRate", 2_400L);
        metrics.put("lag", 12_000L);
        metrics.put("endToEndP99Ms", 850L);
        metrics.forEach((k, v) -> System.out.printf("  %-16s %s%n", k, v));
        System.out.println("  lag 大但端到端稳定 -> 可能只是流量高峰；端到端恶化才是真问题");

        System.out.println();
        System.out.println("【排障清单】traceId 透传 / eventId 唯一 / occurredAt 业务时间 / 按 eventType 聚合");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 给事件加 traceId 并跨生产/消费建 span，画出调用树，计算端到端延迟。
 * 2. 区分"排队延迟"与"处理延迟"，用 lag 与单条耗时两个指标定位瓶颈。
 * 3. 思考题：为什么"lag 大"不一定代表用户体验差？从"端到端延迟 vs 队列长度"分析。
 * ============================================================
 */
