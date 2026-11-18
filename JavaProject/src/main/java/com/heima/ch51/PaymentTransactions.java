/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：51-09
 * 课名：分布式事务在支付
 * ------------------------------------------------------------
 * 知识点：
 *   - **支付域内的一致性分层**：**强一致**（支付单状态+账务分录+outbox 同库同事务，51-03）；**最终一致**（跨域：订单/履约/积分，事件驱动 49-03）；**对账兜底**（51-05 收口）——39-09 的方案光谱在资金域的取舍：**XA 不用（锁与吞吐）、TCC 用于资金冻结场景、本地消息表是主力**。
 *   - **TCC 在支付的真实场景**：**余额冻结**（账户系统：Try 冻结额度、Confirm 扣减、Cancel 解冻）——预扣资源的场景 TCC 语义最自然；**空回滚与悬挂的防护**（44 章同款三态纪律）在资金上绝对不能省。
 *   - **本地消息表的支付版细节**：outbox 与支付单同库（同事务）-> 投递 PaymentSucceeded；**消息按订单号分区保序**（39-08）；**消费幂等键 = 事件 ID**；投递失败指数退避 + 死信告警——**"事件丢了"在支付域等于"订单永远待支付"**，可靠级别最高。
 *   - **反向补偿**：支付成功但下游履约失败——**支付不回滚**（钱已到账），下游走补偿（重发履约/人工）——**"钱的事实不可逆，业务流程可补偿"**是支付域分布式事务的核心认知。
 *   - **一致性监控**：业务流水 vs 账务分录日终核对（51-08 第二层对账）、outbox 积压告警、事件消费 lag——**一致性指标进告警**（42 章方法论）。
 *   - 易错点：跨域用同步调用串联（一条链断全链断，49-08 分布式单体）；消息无分区保序（状态乱序回退）；消费幂等缺失（重复履约/重复发货）；把"支付成功"事件的消费失败当可丢（必须死信+人工）。
 * ============================================================
 */

package com.heima.ch51;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// ==================== 示例代码 ====================

public class PaymentTransactions {
    /** TCC：账户余额冻结（资金域最自然的 TCC 场景） */
    static class AccountTcc {
        final Map<String, long[]> accounts = new ConcurrentHashMap<>();   // user -> {可用, 冻结}

        AccountTcc() {
            accounts.put("u1", new long[]{100_000, 0});
        }

        boolean tryFreeze(String user, long amount) {
            long[] a = accounts.get(user);
            if (a[0] < amount) {
                return false;
            }
            a[0] -= amount;
            a[1] += amount;
            System.out.printf("  Try：冻结 %d 分（可用 %d / 冻结 %d）%n", amount, a[0], a[1]);
            return true;
        }

        void confirm(String user, long amount) {
            long[] a = accounts.get(user);
            a[1] -= amount;
            System.out.printf("  Confirm：扣除冻结 %d 分（冻结剩 %d）%n", amount, a[1]);
        }

        void cancel(String user, long amount) {
            long[] a = accounts.get(user);
            a[1] -= amount;
            a[0] += amount;
            System.out.printf("  Cancel：解冻 %d 分（可用恢复 %d）%n", amount, a[0]);
        }
    }

    static void tccFlow() {
        System.out.println("【TCC 三阶段与空回滚防护】");
        AccountTcc tcc = new AccountTcc();
        boolean ok = tcc.tryFreeze("u1", 6900);
        if (ok) {
            tcc.confirm("u1", 6900);
        } else {
            tcc.cancel("u1", 6900);
        }
        System.out.println("  空回滚场景：Try 网络丢失，Cancel 先到 -> 记空标记，Cancel 成功返回");
        System.out.println("  悬挂场景：Cancel 之后 Try 才到 -> 检查空标记直接拒绝");
        System.out.println("  幂等键 = 事务 xid（44-09 同款），三接口全部幂等");
        System.out.println();
    }

    static void outboxDelivery() {
        System.out.println("【outbox 投递与分区保序】");
        record OutboxEvent(String eventId, String orderId, String type) {
        }
        List<OutboxEvent> outbox = List.of(
                new OutboxEvent("e1", "9001", "PaymentSucceeded"),
                new OutboxEvent("e2", "9001", "RefundSucceeded"),
                new OutboxEvent("e3", "9002", "PaymentSucceeded"));
        System.out.println("  投递路由：按 orderId 分区（同一订单事件保序）");
        Map<String, List<String>> partitioned = new java.util.LinkedHashMap<>();
        for (OutboxEvent e : outbox) {
            partitioned.computeIfAbsent(e.orderId(), k -> new ArrayList<>()).add(e.type());
        }
        for (Map.Entry<String, List<String>> e : partitioned.entrySet()) {
            System.out.printf("  分区[%s] -> %s%n", e.getKey(), e.getValue());
        }
        System.out.println("  消费幂等：eventId 去重表（51-03 三层幂等的跨域层）");
        System.out.println("  监控：outbox 积压 >0 持续 5min 告警（42 章）");
        System.out.println();
    }

    static void compensatationPrinciple() {
        System.out.println("【反向补偿：钱不可逆，流程可补偿】");
        List<String> cases = List.of(
                "支付成功但履约失败 -> 钱不退（支付事实成立），履约重试/人工补发",
                "支付成功但用户申请退款 -> 走退款链路（51-06），不是回滚支付",
                "积分发放失败 -> 补偿队列重发，不影响支付状态",
                "原则：支付状态机是事实锚点，其他域围绕它补偿（49-03）");
        for (String c : cases) {
            System.out.println("  " + c);
        }
        System.out.println("  对照错误做法：履约失败把支付状态回滚 FAILED -> 账实不符 + 客诉");
    }

    public static void main(String[] args) {
        tccFlow();
        outboxDelivery();
        compensatationPrinciple();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现 AccountTcc 完整版（空回滚标记 + 悬挂拒绝 + 幂等），并发 Try/Cancel 竞态验证资金状态正确。
 * 2. 实现 outbox 投递器（分区保序 + 幂等消费 + 死信），注入"消费失败"验证重试与告警路径。
 * 3. 思考题：为什么"支付成功但履约失败"不能回滚支付？从资金事实不可逆与补偿机制的设计哲学分析。
 * ============================================================
 */
