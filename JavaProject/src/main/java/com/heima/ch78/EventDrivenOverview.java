/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：78-01
 * 课名：从 RPC 到事件驱动
 * ------------------------------------------------------------
 * 知识点：
 *   - **同步 RPC 的耦合**：**调用方必须知道被调方地址、接口、超时**，**被调方故障会直接拖垮调用方**，**链式调用放大延迟与失败率**——**这就是"下单要等积分、短信、推荐全部成功"的痛点**。
 *   - **事件驱动的本质：把"调用"换成"事实通知"**——**生产者只发布"发生了什么"（OrderCreated）**，**消费者各自订阅、各自处理**，**生产者不关心谁消费、消费多久**。
 *   - **三个收益**：**解耦**（新增消费者不改生产者）、**削峰**（事件排队，消费者按能力处理 43 章）、**最终一致**（先落库再异步推进）——**代价是"一致性延迟"与"链路可观测性变难"**（78-09）。
 *   - **什么时候不该用**：**需要强一致/即时返回**（支付扣款结果）、**调用链很短**（多一层 MQ 反而增加复杂度）、**团队没有事件治理能力**（事件散落、无人负责 78-10）。
 *   - 与 28 章的关系：28 章讲"消息队列的确认/重试/死信"，**本章讲"把消息队列用成架构模式"**——**从工具到设计**。
 *   - 易错点：所有调用都改事件（简单查询也异步）；事件里塞全量数据（变成"大对象传输"）；没有事件契约（78-07）；以为"发了事件就一定被消费"（至少一次 + 幂等 78-03）。
 * ============================================================
 */

package com.heima.ch78;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class EventDrivenOverview {
    record Step(String service, String action, long latencyMs, boolean required) {
    }

    record Event(String type, String payload, long producedAt) {
    }

    static void syncChain() {
        System.out.println("【同步 RPC：下单链路】");
        List<Step> chain = List.of(
                new Step("订单服务", "创建订单", 30, true),
                new Step("库存服务", "扣减库存", 40, true),
                new Step("积分服务", "增加积分", 20, false),
                new Step("短信服务", "发送通知", 80, false),
                new Step("推荐服务", "更新画像", 60, false));
        long total = 0;
        double success = 1.0;
        for (Step s : chain) {
            total += s.latencyMs();
            success *= 0.995;
            System.out.printf("  -> %-8s %-10s %3dms%s%n", s.service(), s.action(),
                    s.latencyMs(), s.required() ? "（强依赖）" : "（弱依赖）");
        }
        System.out.printf("  总延迟=%dms，链路成功率≈%.1f%%%n", total, success * 100);
        System.out.println("  任一弱依赖变慢/失败，都会拖累下单");
        System.out.println();
    }

    static void eventChain() {
        System.out.println("【事件驱动：下单只做两件事】");
        List<Event> events = new ArrayList<>();
        long now = System.currentTimeMillis();
        events.add(new Event("OrderCreated", "order-1001", now));
        System.out.println("  订单服务：本地事务写订单 + outbox（78-02）");
        System.out.printf("  发布事件：%s%n", events.get(0));

        Map<String, Long> consumerLatency = new LinkedHashMap<>();
        consumerLatency.put("库存服务", 40L);
        consumerLatency.put("积分服务", 20L);
        consumerLatency.put("短信服务", 80L);
        consumerLatency.put("推荐服务", 60L);
        long max = 0;
        for (Map.Entry<String, Long> e : consumerLatency.entrySet()) {
            max = Math.max(max, e.getValue());
            System.out.printf("  <- %-8s 异步消费 %-10s %3dms%n",
                    e.getKey(), "OrderCreated", e.getValue());
        }
        System.out.printf("  下单接口只等本地事务（约 30ms），最慢消费者 %dms 不影响返回%n", max);
        System.out.println();
    }

    static void comparison() {
        System.out.println("【对比】");
        String[][] rows = {
                {"耦合", "调用方依赖被调方接口", "只依赖事件契约"},
                {"失败影响", "弱依赖失败拖垮主链路", "消费者失败不影响生产者"},
                {"延迟", "链式累加", "主链路只等本地事务"},
                {"一致性", "强一致（同事务）", "最终一致（延迟）"},
                {"可观测", "调用链清晰", "需 trace 串联（78-09）"},
                {"复杂度", "低", "高（幂等/顺序/治理）"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-10s RPC=%-24s 事件=%s%n", r[0], r[1], r[2]);
        }
        System.out.println();
    }

    public static void main(String[] args) {
        syncChain();
        eventChain();
        comparison();
        System.out.println("【结论】核心交易走同步，周边动作走事件；事件不是免费的解耦");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 画出一个你熟悉的下单链路，标出哪些步骤必须同步、哪些可以事件化，并说明理由。
 * 2. 计算同步链路的"总延迟/成功率"，与事件化后的"主链路延迟"对比。
 * 3. 思考题：为什么"所有调用都改事件"是反模式？从"一致性延迟 + 排障成本"分析。
 * ============================================================
 */
