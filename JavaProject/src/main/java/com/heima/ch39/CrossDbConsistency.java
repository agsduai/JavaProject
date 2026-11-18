/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：39-09
 * 课名：跨库事务与一致性
 * ------------------------------------------------------------
 * 知识点：
 *   - **分库后单库事务失效**：订单在 A 库、库存扣减在 B 库，本地事务罩不住两个库。可选方案从强到弱：**2PC/XA**（强一致，协调者单点、锁持有时间长，吞吐差）、**TCC**（Try-Confirm-Cancel，业务侵入大但性能好）、**本地消息表**（最终一致，实现简单，最常用）、**Saga**（长流程编排，正反补偿）。
 *   - **本地消息表是 80% 场景的正确答案**：业务表更新与"待发送消息"记录在**同一个本地事务**里提交，后台任务扫描消息表投递到 MQ，下游消费成功回执。保证"本地事务成功则消息一定发出去"，最终一致由重试 + 幂等兜底。第 28 章已讲过 MQ 侧，本章补数据库侧的表结构。
 *   - **TCC 的本质是把"预留"显式化**：Try 冻结库存（扣到 frozen 字段）、Confirm 真实扣减、Cancel 释放冻结。三个接口都要幂等、都要允许空回滚（Cancel 在 Try 未到达时也要成功）、都要防悬挂（Cancel 之后到达的 Try 拒绝）。**业务侵入极大**，只用于资金等强一致场景。
 *   - **Saga 适合长流程**：下单 → 扣库存 → 扣优惠券 → 通知物流，每步有补偿动作，失败时按已执行步骤反向补偿。没有隔离性（中间态可见），需要业务接受"补偿期间数据不一致"。
 *   - **能不分事务就不分**：很多"跨库事务"其实可以通过**调整边界**消除——把强关联的表放同一个库（按用户分片天然做到）、用状态机 + 异步核对替代实时强一致（订单状态 PENDING → 核对任务修复）、先落库后异步。
 *   - **对账是所有最终一致方案的收尾**：定时比对两侧（订单 vs 支付流水、库存 vs 扣减记录），差异进差异表人工/自动修复。**架构承认最终一致，对账保证误差收敛**。
 *   - 易错点：用 XA 硬扛高并发（锁等待拖垮连接池）；TCC 忘写空回滚与防悬挂（Cancel 比 Try 先到，资金状态错乱）；本地消息表只加不扫（消息堆积，下游永远收不到）；补偿动作不幂等（重试一次多扣一次）。
 * ============================================================
 */

package com.heima.ch39;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class CrossDbConsistency {
    /** 本地消息表 */
    record Outbox(long id, String topic, String payload, String status, int retry) {
    }

    static Map<Long, String> orderDb = new LinkedHashMap<>();      // A 库：订单
    static Map<Long, Integer> stockDb = new LinkedHashMap<>();     // B 库：库存
    static List<Outbox> outbox = new ArrayList<>();
    static List<String> audit = new ArrayList<>();

    static void localMessageTable() {
        System.out.println("【本地消息表：一次扣库存的完整闭环】");
        long orderId = 9001, productId = 55;
        // 本地事务 1（A 库）：插入订单 + 写 outbox，原子提交
        orderDb.put(orderId, "CREATED");
        outbox.add(new Outbox(1, "OrderCreated", "order=9001,product=55,qty=1", "PENDING", 0));
        System.out.println("  A 库事务：订单 9001 = CREATED + outbox#1 PENDING（同一事务）");
        // 后台投递
        Outbox m = outbox.get(0);
        audit.add("投递 outbox#1 -> MQ -> 库存服务");
        // 本地事务 2（B 库）：扣库存 + 回执（幂等：按 orderId 判重）
        if (!audit.contains("扣减55 for 9001")) {
            stockDb.merge(productId, -1, Integer::sum);
            audit.add("扣减55 for 9001（幂等键=orderId）");
        }
        outbox.set(0, new Outbox(1, m.topic(), m.payload(), "SENT", m.retry() + 1));
        System.out.println("  B 库事务：库存 55 " + stockDb.get(productId) + "（消费幂等）");
        System.out.println("  outbox 状态：" + outbox.get(0).status() + "，retry=" + outbox.get(0).retry());
        System.out.println("  失败路径：投递失败 retry+1，退避重试；N 次后进死信告警");
        System.out.println();
    }

    static void tccDemo() {
        System.out.println("【TCC：预留 -> 确认/取消，以及空回滚】");
        int stock = 10, frozen = 0;
        boolean tryOk = stock >= 1;
        if (tryOk) {
            stock--;
            frozen++;
            System.out.println("  Try：库存 10 -> " + stock + "，冻结 " + frozen + "（预留但不提交）");
        }
        boolean confirm = true;
        if (confirm) {
            frozen--;
            System.out.println("  Confirm：冻结释放，真实扣减完成 -> " + stock);
        } else {
            stock += frozen;
            frozen = 0;
            System.out.println("  Cancel：解冻归还 -> " + stock);
        }
        System.out.println("  空回滚场景：Try 网络丢失，Cancel 先到 -> 必须成功返回（记空标记）");
        System.out.println("  悬挂场景：Cancel 后 Try 才到达 -> 检查空标记直接拒绝");
        System.out.println("  三个接口全部要求幂等 + 幂等键（xid）");
        System.out.println();
    }

    static void reconcile() {
        System.out.println("【对账：最终一致的收敛装置】");
        orderDb.put(9002L, "CREATED");
        // 模拟丢一条消息：库存没扣
        List<Long> paidOrders = new ArrayList<>(List.of(9001L));
        List<Long> deducted = new ArrayList<>(List.of(9001L));
        for (Long id : paidOrders) {
            if (!deducted.contains(id)) {
                audit.add("对账差异：订单 " + id + " 已创建但未扣库存 -> 自动补偿");
            }
        }
        for (String s : audit) {
            System.out.println("  " + s);
        }
        System.out.println("  对账 SQL：两边按业务键 LEFT JOIN，diff 进差异表");
        System.out.println("  修复策略：可自动（补发消息）/ 需人工（金额对不上），分级处理");
    }

    public static void main(String[] args) {
        localMessageTable();
        tccDemo();
        reconcile();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现完整的本地消息表：含 PENDING/SENT/DEAD 状态、指数退避重试、消费幂等键，用并发消费验证不会重复扣减。
 * 2. 实现带空回滚与防悬挂的 TCC 三接口（内存版），构造"Cancel 先到"的时序测试，证明资金状态正确。
 * 3. 思考题：为什么说"调整边界消除事务"优于任何分布式事务方案？举出一个你可以通过按用户分片消除跨库事务的场景。
 * ============================================================
 */
