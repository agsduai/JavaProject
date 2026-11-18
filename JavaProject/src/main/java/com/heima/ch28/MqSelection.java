/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：28-10
 * 课名：消息中间件选型与常见坑
 * ------------------------------------------------------------
 * 知识点：
 *   - **RabbitMQ**：延迟队列、死信、优先级支持好，单机吞吐万级，适合业务消息（订单、通知）。**Kafka**：十万级吞吐、分区有序、生态强，适合日志、埋点、流处理，但延迟消息和死信要靠自己实现。**RocketMQ**：事务消息、延迟等级、消息回溯，介于两者之间，适合电商交易链路。
 *   - 选型维度：吞吐、延迟、可靠性语义、顺序性、延迟消息、运维成本、团队熟悉度。不要只看吞吐。
 *   - 常见坑：消息体过大（几百 KB 的 JSON 塞进消息，Broker 压力大且难排查）→ 只传 ID，让消费者回查；主题设计过细（一业务一 topic，运维爆炸）→ 按领域划分。
 *   - 消费端连接数、预取数（prefetch）配置不当会严重影响吞吐：prefetch 太小消费者空转，太大单机吃满。
 *   - 监控必备：生产/消费 TPS、lag、死信数量、重试次数、Broker 磁盘与连接数。
 *   - 易错：生产环境用默认 guest/guest 账号；队列没有设 TTL 导致无限堆积；没有做消息体 schema 版本管理，改字段直接让老消费者反序列化失败。
 * ============================================================
 */

package com.heima.ch28;

import java.util.LinkedHashMap;
import java.util.Map;

// ==================== 示例代码 ====================

public class MqSelection {
    record Mq(String name, int tps, String order, String delayMsg, String txMsg, String fit) { }

    public static void main(String[] args) {
        Map<String, Mq> table = new LinkedHashMap<>();
        table.put("RabbitMQ", new Mq("RabbitMQ", 20_000, "队列内有序", "原生支持", "不支持", "业务消息、通知、延迟任务"));
        table.put("Kafka", new Mq("Kafka", 1_000_000, "分区有序", "需自实现", "支持(事务)", "日志、埋点、流处理"));
        table.put("RocketMQ", new Mq("RocketMQ", 100_000, "分区有序", "延迟等级", "原生支持", "电商交易、订单链路"));

        System.out.println("=== 选型对比 ===");
        System.out.printf("%-12s %-10s %-10s %-10s %-12s %s%n",
                "产品", "吞吐(条/s)", "顺序性", "延迟消息", "事务消息", "典型场景");
        for (Mq m : table.values()) {
            System.out.printf("%-12s %-10d %-10s %-10s %-12s %s%n",
                    m.name(), m.tps(), m.order(), m.delayMsg(), m.txMsg(), m.fit());
        }

        System.out.println();
        System.out.println("=== 常见坑与对策 ===");
        String[][] pitfalls = {
                {"消息体几百 KB 的 JSON", "只传业务 ID，消费者回查；大对象放对象存储"},
                {"一个业务一个 topic", "按领域划分 topic，用 tag/header 区分事件"},
                {"prefetch=1 导致空转", "按消费耗时调整，批量场景调大到几十"},
                {"队列无 TTL 无限堆积", "设置消息 TTL + 最大长度，溢出进死信"},
                {"默认 guest 账号", "独立账号 + vhost 隔离 + 最小权限"},
                {"消息体字段直接改", "加版本号，消费者兼容新旧两版"}
        };
        for (String[] p : pitfalls) {
            System.out.println("  - " + p[0] + " => " + p[1]);
        }

        System.out.println();
        System.out.println("=== 容量估算示例 ===");
        long peakQps = 5_000;
        int msgBytes = 2 * 1024;
        int retentionDays = 3;
        long dailyBytes = peakQps * 3600L * 8 * msgBytes;
        long totalBytes = dailyBytes * retentionDays;
        System.out.println("  峰值 " + peakQps + " qps，消息 " + msgBytes + "B，保留 " + retentionDays + " 天");
        System.out.println("  日增约 " + dailyBytes / 1024 / 1024 / 1024 + "GB，总需约 "
                + totalBytes / 1024 / 1024 / 1024 + "GB（含副本要再乘 3）");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 为"订单超时取消"和"用户行为埋点"分别选一个 MQ，说明理由。
 * 2. 消息体 500KB、峰值 2000 qps、保留 7 天，估算需要的磁盘容量（含 3 副本）。
 * 3. 列出你在 MQ 监控面板上必须看到的 5 个指标，并说明每个指标异常的典型原因。
 * ============================================================
 */
