/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：78-11
 * 课名：综合案例：订单履约事件流
 * ------------------------------------------------------------
 * 知识点：
 *   - **把本章收口成一条完整事件流**：**下单（Outbox 78-02）-> 支付成功 -> 库存预留 -> 发货 -> 完成**，**任一步失败走 Saga 补偿**（78-06），**全程幂等**（78-03）、**按订单分区保序**（78-04）、**可追溯**（78-09）。
 *   - **状态机是履约的核心**：**每个事件只允许合法状态迁移**（`CREATED -> PAID -> RESERVED -> SHIPPED -> COMPLETED`），**重复/乱序事件被状态机拒绝**——**这是业务层幂等的最后一道防线**。
 *   - **补偿与正向流程对称**：**支付失败 -> 取消订单**；**库存不足 -> 退款 + 取消**；**发货失败 -> 释放库存 + 退款**——**每一步都要有对应的逆向操作**。
 *   - **验收清单**：**不丢事件**（Outbox）、**不重复处理**（幂等）、**同一订单有序**（分区键）、**失败可补偿**（Saga）、**全链路可查**（traceId + eventId）。
 *   - 与 49/78 章的关系：本章是 49 章 DDD 领域事件与 78 章事件驱动模式的**联合落地**——**从建模到运行**。
 *   - 易错点：状态机允许任意跳转（脏数据）；补偿不对称（退了款没释放库存）；重复事件导致重复发货；traceId 断链（排障靠猜）。
 * ============================================================
 */

package com.heima.ch78;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// ==================== 示例代码 ====================

public class OrderFulfillmentCase {
    enum State { CREATED, PAID, RESERVED, SHIPPED, COMPLETED, CANCELLED }

    static class OrderFulfillment {
        final long orderId;
        State state = State.CREATED;
        final Set<String> processedEvents = new LinkedHashSet<>();
        final List<String> timeline = new ArrayList<>();
        final List<String> compensations = new ArrayList<>();

        OrderFulfillment(long orderId) {
            this.orderId = orderId;
            timeline.add("CREATED");
        }

        /** 事件处理：幂等 + 状态机 */
        void onEvent(String eventId, String type) {
            if (processedEvents.contains(eventId)) {
                System.out.printf("  [幂等] 重复事件 %s 已忽略%n", eventId);
                return;
            }
            boolean ok = switch (type) {
                case "PAYMENT_SUCCEEDED" -> transition(State.PAID);
                case "INVENTORY_RESERVED" -> transition(State.RESERVED);
                case "SHIPPED" -> transition(State.SHIPPED);
                case "DELIVERED" -> transition(State.COMPLETED);
                case "PAYMENT_FAILED" -> cancel("支付失败");
                case "INVENTORY_SHORTAGE" -> cancel("库存不足");
                default -> false;
            };
            if (ok) {
                processedEvents.add(eventId);
            }
            System.out.printf("  %-8s %-20s -> %-10s %s%n", eventId, type, state,
                    ok ? "接受" : "拒绝（非法迁移/已取消）");
        }

        boolean transition(State next) {
            if (state == State.CANCELLED || state == State.COMPLETED) {
                return false;
            }
            int cur = state.ordinal();
            int nxt = next.ordinal();
            if (nxt != cur + 1) {
                return false;
            }
            state = next;
            timeline.add(next.name());
            return true;
        }

        boolean cancel(String reason) {
            if (state == State.SHIPPED || state == State.COMPLETED
                    || state == State.CANCELLED) {
                return false;
            }
            if (state.ordinal() >= State.PAID.ordinal()) {
                compensations.add("退款");
            }
            if (state.ordinal() >= State.RESERVED.ordinal()) {
                compensations.add("释放库存");
            }
            state = State.CANCELLED;
            timeline.add("CANCELLED(" + reason + ")");
            return true;
        }

        void report() {
            System.out.println("  order " + orderId + "：");
            System.out.println("    状态：" + state);
            System.out.println("    时间线：" + timeline);
            System.out.println("    补偿：" + (compensations.isEmpty() ? "无" : compensations));
        }
    }

    static final Map<Long, OrderFulfillment> orders = new LinkedHashMap<>();

    public static void main(String[] args) {
        OrderFulfillment o1 = new OrderFulfillment(1001);
        orders.put(1001L, o1);

        System.out.println("【正常履约：下单 -> 支付 -> 预留库存 -> 发货 -> 签收】");
        o1.onEvent("e1", "PAYMENT_SUCCEEDED");
        o1.onEvent("e2", "INVENTORY_RESERVED");
        o1.onEvent("e3", "SHIPPED");
        o1.onEvent("e4", "DELIVERED");
        System.out.println();
        o1.report();

        System.out.println();
        System.out.println("【重复投递：e2 再来一次】");
        o1.onEvent("e2", "INVENTORY_RESERVED");
        System.out.println();
        o1.report();

        System.out.println();
        System.out.println("【乱序：先收到 SHIPPED 再收到 INVENTORY_RESERVED】");
        OrderFulfillment o2 = new OrderFulfillment(1002);
        o2.onEvent("e1", "PAYMENT_SUCCEEDED");
        o2.onEvent("e3", "SHIPPED");
        o2.onEvent("e2", "INVENTORY_RESERVED");
        System.out.println();
        o2.report();
        System.out.println("  注：真实系统应短暂缓存乱序事件，待前置事件到达后重放，而不是直接丢弃");

        System.out.println();
        System.out.println("【失败补偿：支付成功、库存不足】");
        OrderFulfillment o3 = new OrderFulfillment(1003);
        o3.onEvent("e1", "PAYMENT_SUCCEEDED");
        o3.onEvent("e2", "INVENTORY_SHORTAGE");
        o3.onEvent("e3", "SHIPPED");
        System.out.println();
        o3.report();

        System.out.println();
        System.out.println("【验收清单】Outbox 不丢 / 幂等不重 / 分区保序 / Saga 可补偿 / trace 可查");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 扩展状态机：加入"取消中/退款中"中间态，验证重复事件与乱序事件都被正确处理。
 * 2. 模拟"支付成功 -> 库存不足 -> 退款 -> 释放库存"的完整补偿链，检查补偿顺序与幂等。
 * 3. 思考题：为什么"状态机是业务幂等的最后防线"？从"重复/乱序事件无法只靠 msgId 去重"分析。
 * ============================================================
 */

/*
 * ============================================================
 * 本章综合练习（无参考答案）
 * ------------------------------------------------------------
 * 【场景】
 * 用事件驱动重构订单履约：本地消息表/事务消息保证「业务成功则事件必发」、消费幂等、顺序消费、死信与重试、最终一致对账、事件溯源。要能回答「消息丢了怎么办」。
 * 【任务】
 * 1. 实现本地消息表：业务写库与写消息表在同一本地事务，再由投递器异步发送，验证不出现「订单成功但事件没发」。
 * 2. 实现消费幂等：用业务唯一键或去重表，重复消费 3 次结果不变。
 * 3. 实现顺序消费：同一订单的事件按序处理（按订单号路由到同一分区/队列），说明乱序会导致什么业务错误。
 * 4. 实现死信与对账：重试 N 次失败进入死信队列并告警，定时对账补偿不一致的订单。
 * 【验收清单】
 * [ ] 模拟投递器在发送前崩溃，重启后事件仍被补发，业务与事件最终一致。
 * [ ] 重复消费不产生重复扣减/重复发货。
 * [ ] 同一订单的事件处理顺序正确，状态不会倒退。
 * [ ] 死信可查可重放，对账能发现并修复不一致数据。
 * ============================================================
 */
