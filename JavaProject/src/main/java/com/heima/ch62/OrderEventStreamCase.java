/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：62-11
 * 课名：综合案例：订单事件流的 Kafka 落地
 * ------------------------------------------------------------
 * 知识点：
 *   - **把本章收口成一条真实的订单事件流**：生产者（订单服务）按 `orderId` 分区 -> `acks=all` + 幂等 -> Topic（`delete` 保留 7 天，可重放对账）-> 多个消费组各司其职（履约/积分/搜索/数仓同步）-> 消费端手动提交 + 幂等键 -> Lag 监控与积压处置。
 *   - **Topic 设计要点**：**分区数按消费并行度规划**（预计 10 个消费者 -> 至少 10 分区，留余量 12）；**key 用 orderId**（同订单事件有序）；**副本 3 + 机架感知**（容灾）；**retention 与业务对账窗口对齐**（7 天足够 T+1 对账与短期重放）。
 *   - **消费组设计**：**一个业务一个消费组**（履约组/积分组/数仓组各自独立 offset，互不影响 62-01）；**消费者数 ≤ 分区数**；**组内消费者处理能力要均衡**（否则倾斜 62-10）。
 *   - **一致性链路**：生产端幂等 + `acks=all` -> Broker 侧 HW 保证已提交 -> 消费端"处理后提交 + 业务幂等" -> 最终一致（39/43 章）+ 对账兜底（39-09）。
 *   - **失败处理**：消费失败重试（本地重试队列 + 延迟队列 28-02）-> 超过次数进死信 Topic -> 告警与人工重放（重放要带幂等，62-07）。
 *   - 易错点：分区数按"当前消费者数"定（扩容无空间）；所有业务共用一个消费组（消费进度互相拖累）；消费端自动提交（重复/丢）；死信无监控（消息静默丢失）。
 * ============================================================
 */

package com.heima.ch62;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class OrderEventStreamCase {
    record Event(long offset, String orderId, String type) {
    }

    record ConsumerGroupState(String group, long committedOffset, List<String> processedIds,
                              boolean autoCommit) {
    }

    /** 消费：处理后提交 + 幂等（eventId 去重） */
    static ConsumerGroupState consume(ConsumerGroupState state, List<Event> log,
                                      int maxPoll, boolean failOnce) {
        long processed = 0;
        for (Event e : log) {
            if (e.offset() <= state.committedOffset()) {
                continue;
            }
            if (processed >= maxPoll) {
                break;
            }
            String eventId = state.group() + "#" + e.offset();
            if (state.processedIds().contains(eventId)) {
                System.out.printf("    [%s] offset=%d 重复事件 -> 跳过（幂等）%n",
                        state.group(), e.offset());
                continue;
            }
            if (failOnce) {
                System.out.printf("    [%s] offset=%d 处理失败 -> 不提交 offset（消息重投）%n",
                        state.group(), e.offset());
                return state;
            }
            state.processedIds().add(eventId);
            processed++;
        }
        long newCommitted = state.committedOffset() + processed;
        System.out.printf("    [%s] 处理 %d 条 -> committed offset=%d%n",
                state.group(), processed, newCommitted);
        return new ConsumerGroupState(state.group(), newCommitted,
                state.processedIds(), state.autoCommit());
    }

    public static void main(String[] args) {
        List<Event> log = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            log.add(new Event(i, "order-" + (9001 + i / 2),
                    i % 2 == 0 ? "OrderCreated" : "OrderPaid"));
        }
        System.out.println("【Topic 设计】");
        Map<String, String> topic = new LinkedHashMap<>();
        topic.put("分区数", "12（按 10 消费者规划 + 余量）");
        topic.put("key", "orderId（同订单事件有序）");
        topic.put("副本", "3 + broker.rack（跨机架）");
        topic.put("保留", "delete 7 天（覆盖 T+1 对账与短期重放）");
        topic.put("生产", "acks=all + 幂等（min.insync.replicas=2）");
        topic.forEach((k, v) -> System.out.printf("  %-6s %s%n", k, v));

        System.out.println();
        System.out.println("【消费组独立推进】");
        var fulfillment = new ConsumerGroupState("group-fulfillment", 0, new ArrayList<>(), false);
        var points = new ConsumerGroupState("group-points", 0, new ArrayList<>(), false);
        System.out.println("  履约组：");
        fulfillment = consume(fulfillment, log, 10, false);
        System.out.println("  积分组（只消费了一半就宕机）：");
        points = consume(points, log, 3, false);
        points = consume(points, log, 10, false);
        System.out.println("    两组 offset 互不影响：" + fulfillment.committedOffset()
                + " vs " + points.committedOffset());

        System.out.println();
        System.out.println("【消费失败：重投与幂等】");
        var retry = new ConsumerGroupState("group-search", 0, new ArrayList<>(), false);
        retry = consume(retry, log, 10, true);
        retry = consume(retry, log, 10, false);
        System.out.println();
        System.out.println("  一致性链路：生产幂等 + acks=all + 处理后提交 + 业务幂等 + 对账兜底");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 端到端实现订单事件流模拟：3 个消费组、分区按 orderId、失败重投、幂等去重，输出各组最终状态。
 * 2. 为"履约消费组"设计死信方案：失败 3 次进死信 Topic + 告警 + 人工重放（带幂等），画出数据流。
 * 3. 思考题：为什么"一个业务一个消费组"？构造"两个业务共用一个组"导致消费进度互相拖累的场景并说明后果。
 * ============================================================
 */

/*
 * ============================================================
 * 本章综合练习（无参考答案）
 * ------------------------------------------------------------
 * 【场景】
 * 用纯 JDK 实现迷你 Kafka：分区与顺序写、分段日志与索引、消费位点与重复消费、消费者组再均衡、ISR 与 acks 语义。目标是搞清「Kafka 为什么快、为什么可能重复」。
 * 【任务】
 * 1. 实现分区与顺序追加写：每个分区一个 append-only 文件，写入只追加不修改，说明顺序写为什么比随机写快。
 * 2. 实现分段与稀疏索引：按大小滚动新段，用 offset 索引快速定位，验证查任意 offset 只需读一个段。
 * 3. 实现消费位点：消费者提交 offset，模拟「处理成功但提交前崩溃」验证会重复消费，说明至少一次语义。
 * 4. 实现 acks 语义：acks=0/1/all 三种配置下，模拟 broker 崩溃验证消息可能丢失的场景。
 * 【验收清单】
 * [ ] 同一分区内消息严格有序，跨分区不保证有序（并有说明）。
 * [ ] 按 offset 查找能命中正确段与位置，不需要全文件扫描。
 * [ ] 能复现重复消费并说明幂等消费的必要性。
 * [ ] acks=all 时已确认的消息在单 broker 崩溃后不丢（或说明需要几个副本）。
 * ============================================================
 */
