/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：49-03
 * 课名：聚合设计与领域事件实战
 * ------------------------------------------------------------
 * 知识点：
 *   - **聚合间协作的三种模式**：① 同步调用（聚合 A 直接调 B —— 耦合最强，慎用）；② **领域事件最终一致**（A 发事件，B 订阅处理 —— 默认选择）；③ 过程管理器（Saga，编排跨聚合流程，39-09 TCC/本地消息表的领域版）。**默认选事件，强实时才同步**。
 *   - **事件的设计原则**：过去时命名、携带"消费者所需最小字段"（ orderId + paidAmount + paidAt，不要整个聚合序列化）、不可变、带事件 ID（消费幂等键）。**事件瘦身**是消费者稳定的前提——事件字段即契约（46-08 数据契约的同源思想）。
 *   - **事件的可靠发布**：进程内事件（Spring 事件总线，同事务）与跨服务事件（MQ）不同——**跨服务必须本地消息表/outbox**（39-09），"聚合保存 + 事件入库"同事务提交，后台投递。
 *   - **同一上下文内的一致性策略**：强一致规则放聚合内（同事务）；跨聚合规则用事件 + 消费端校验（最终一致窗口内业务可接受吗？不能接受就合并进同一聚合或同步调用）。
 *   - **用例编排归应用层**：应用服务（Application Service）负责"加载聚合 -> 调领域方法 -> 保存 -> 发事件"的编排，**不含业务规则**（规则在聚合）——贫血与充血的分界就在这层是否膨胀。
 *   - **测试策略**：聚合单元测试（纯内存，无 Mock 依赖）跑不变量；应用层测试用例编排（Mock 仓储）；事件消费者测试幂等与失败重试（40-09 mock 思想）。
 *   - 易错点：事件带聚合全量字段（契约爆炸，上游改字段下游全炸）；进程内事件当跨服务用（无持久化，宕机丢事件）；应用服务里写满业务规则（充血退化回贫血）；事件消费无幂等（重放翻倍）。
 * ============================================================
 */

package com.heima.ch49;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class DomainEventFlow {
    /** 领域事件：过去时、不可变、最小字段 */
    record OrderPaid(String eventId, String orderId, String userId, long paidAtMs, int amountCents) {
    }

    /** 聚合内产生事件（聚合不负责投递） */
    static class Order {
        final List<Object> pendingEvents = new ArrayList<>();

        void markPaid(String orderId, String userId, int cents) {
            pendingEvents.add(new OrderPaid("EV-" + orderId, orderId, userId,
                    System.currentTimeMillis(), cents));
            System.out.println("  聚合产生事件 OrderPaid（等待应用层投递）");
        }
    }

    /** 应用层：编排 + outbox 同事务 */
    static class OrderApplicationService {
        final Map<String, List<Object>> outbox = new LinkedHashMap<>();   // 待投递事件

        void pay(String orderId, String userId, int cents) {
            Order order = new Order();                      // 真实场景先查仓储
            order.markPaid(orderId, userId, cents);
            // 同事务：保存聚合 + 事件入 outbox（39-09 本地消息表）
            outbox.put(orderId, order.pendingEvents);
            System.out.println("  应用层：聚合保存 + 事件入 outbox（同一事务）");
        }
    }

    static void flow() {
        System.out.println("【完整事件流：聚合 -> outbox -> MQ -> 消费者】");
        OrderApplicationService app = new OrderApplicationService();
        app.pay("9001", "1001", 6900);
        System.out.println("  后台投递线程：outbox -> MQ（失败重试，N 次进死信）");
        // 消费者（库存上下文）
        OrderPaid ev = new OrderPaid("EV-9001", "9001", "1001", 0, 6900);
        boolean first = true;
        for (int attempt = 1; attempt <= 2; attempt++) {
            String seenKey = ev.eventId();
            boolean processed = seenKey.equals("EV-9001") && first;
            System.out.printf("  消费尝试#%d：幂等键=%s -> %s%n", attempt, seenKey,
                    processed ? "首次处理：扣减库存" : "重复事件 -> 忽略（幂等生效）");
            if (processed) {
                first = false;
            }
        }
        System.out.println();
    }

    static void eventDesignRules() {
        System.out.println("【事件设计五条军规】");
        List<String> rules = List.of(
                "过去时命名：OrderPaid / OrderCancelled（不是 PayOrder）",
                "最小字段：消费者要什么给什么，不序列化整个聚合",
                "不可变：事件是历史事实，无 setter",
                "带事件 ID：消费幂等的天然键（39 章/43 章同一思想）",
                "带发生时间与版本：消费端排序与乱序防护（39-08）");
        for (String r : rules) {
            System.out.println("  - " + r);
        }
        System.out.println();
    }

    static void consistencyChoice() {
        System.out.println("【同一上下文：强一致还是事件？】");
        String[][] rows = {
                {"订单-明细", "同一聚合（强一致）", "不变量必须同事务"},
                {"订单-库存", "事件最终一致", "超卖由库存侧兜底（预留+校验）"},
                {"订单-优惠券核销", "事件 + 消费端校验", "核销失败进补偿队列"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-12s %-16s %s%n", r[0], r[1], r[2]);
        }
        System.out.println("  判断式：最终一致窗口内业务可接受吗？不可接受 -> 合并聚合或同步");
    }

    public static void main(String[] args) {
        flow();
        eventDesignRules();
        consistencyChoice();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 为订单上下文补全事件清单（OrderPlaced/OrderPaid/OrderCancelled...），为每个事件写最小字段集与消费方列表。
 * 2. 实现 outbox 模式模拟：聚合保存与事件入 outbox 同事务，后台投递失败重试，验证消费幂等。
 * 3. 思考题：为什么"应用服务不含业务规则"是充血模型的底线？举一个规则漏到应用服务后引发的复用问题。
 * ============================================================
 */
