/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：51-03
 * 课名：支付状态机与幂等
 * ------------------------------------------------------------
 * 知识点：
 *   - **支付单状态机是支付核心的骨架**：`CREATED -> PAYING -> SUCCESS / FAILED / CLOSED`，**只进不退**（SUCCESS 不可回 FAILED）；每个转移有前置条件与触发事件——**状态机保证并发回调/查询/关单的合并结果正确**。
 *   - **并发场景的状态机裁决**：回调（成功）与关单任务（超时关闭）同时到达——**用状态机 + 数据库行锁（乐观版本号）串行化**：先到者赢，后到者按当前状态裁决（已 SUCCESS 则关单无效；已 CLOSED 则回调触发"已关闭但有款"差异单进人工/自动退款）。
 *   - **幂等键体系是防重复扣款的根基**：三层——**业务幂等键**（orderId+支付渠道尝试号）、**渠道交易号**（channelTxId 唯一约束）、**回调幂等**（渠道通知 ID 去重表）。**同一支付请求无论重放多少次，最多扣一次款**（39/43 章幂等原则的资金级执行）。
 *   - **创建支付单的幂等**：用户双击支付按钮 -> 两次创建请求 -> **以 orderId+场景为幂等键**返回同一支付单（而不是创建两张）；过期支付单自动关闭（延迟队列 28 章）后允许新建。
 *   - **状态转移的持久化原子性**：状态更新 + 账务记账 + 事件发出必须原子（39-09）——支付域内用**本地事务**（同库）或 outbox；跨域靠事件最终一致。
 *   - 易错点：状态机用 if-else 散落实现（转移规则无法审计，49 章状态建模退步）；重复回调重复记账（幂等缺失，直接资损）；关单与回调竞态未处理（已关单但扣款成功无人处理）；乐观锁缺失（两个并发回调都成功）。
 * ============================================================
 */

package com.heima.ch51;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class PaymentStateMachine {
    enum Status {
        CREATED, PAYING, SUCCESS, FAILED, CLOSED
    }

    /** 状态机：合法转移表（只进不退） */
    static final Map<Status, List<Status>> TRANSITIONS = Map.of(
            Status.CREATED, List.of(Status.PAYING, Status.CLOSED),
            Status.PAYING, List.of(Status.SUCCESS, Status.FAILED, Status.CLOSED));

    static class Payment {
        String id;
        Status status = Status.CREATED;
        long version = 0;                       // 乐观锁版本

        Payment(String id) {
            this.id = id;
        }

        /** CAS 式转移：先到者赢，后到者按当前状态裁决 */
        synchronized boolean transition(Status target, String trigger) {
            List<Status> allowed = TRANSITIONS.get(status);
            if (allowed == null || !allowed.contains(target)) {
                System.out.printf("  [%s] %s --%s--> %s 被拒绝（当前 %s 无此转移）%n",
                        id, status, trigger, target, status);
                return false;
            }
            status = target;
            version++;
            System.out.printf("  [%s] %s --%s--> %s (v%d)%n", id, status, trigger, target, version);
            return true;
        }
    }

    static void raceAdjudication() {
        System.out.println("【竞态裁决：回调成功 vs 超时关单同时到达】");
        Payment p = new Payment("PAY-1");
        p.transition(Status.PAYING, "用户发起");
        boolean closed = p.transition(Status.CLOSED, "关单任务（超时）");
        boolean paid = p.transition(Status.SUCCESS, "渠道回调（晚到）");
        System.out.println("  终态：" + p.status);
        if (closed && paid) {
            System.out.println("  竞态结果：先关单后回调成功 -> 已关单但有款");
            System.out.println("  处置：差异单 -> 自动原路退款 + 告警（51-05 差异闭环）");
        }
        System.out.println("  状态机只进不退：CLOSED 拒绝 SUCCESS 转移 -> 数据不被打乱");
        System.out.println();
    }

    static void idempotencyLayers() {
        System.out.println("【三层幂等键：重复请求最多扣一次款】");
        String[][] layers = {
                {"业务幂等键", "orderId+attemptNo -> 建单接口返回同一支付单", "防用户双击重复建单"},
                {"渠道交易号", "channelTxId 唯一索引", "防回调重放重复记账"},
                {"回调通知 ID", "notifyId 去重表", "渠道同一通知发多次"},
        };
        for (String[] l : layers) {
            System.out.printf("  %-10s %-38s %s%n", l[0], l[1], l[2]);
        }
        Map<String, Integer> dedupe = new LinkedHashMap<>();
        dedupe.put("notify-n1", 1);
        Integer seen = dedupe.putIfAbsent("notify-n1", 2);
        System.out.println("  模拟：同一 notifyId 第二次到达 -> putIfAbsent 发现已存在 -> 忽略（返回首次结果）");
        System.out.println();
    }

    static void atomicity() {
        System.out.println("【状态转移的原子性打包】");
        List<String> bundle = List.of(
                "① 支付单状态更新（乐观锁 version 校验）",
                "② 账务分录插入（51-08 借贷平衡）",
                "③ 事件写入 outbox（PaymentSucceeded）",
                "以上在同一数据库事务内提交 —— 39-09 本地事务的资金级应用");
        for (String s : bundle) {
            System.out.println("  " + s);
        }
        System.out.println("  反例：状态改了事件没发 -> 订单永远待支付（对账发现，51-05）");
    }

    public static void main(String[] args) {
        raceAdjudication();
        idempotencyLayers();
        atomicity();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现完整状态机（转移表 + 乐观锁 + 拒绝日志），并发模拟"回调与关单同时到达"1000 次，验证无数据错乱。
 * 2. 设计三层幂等键的表结构（唯一索引设计），用并发插入验证唯一约束拦截重复。
 * 3. 思考题：为什么状态机"只进不退"是资金安全的根基？从"已成功的支付被标记失败"会引发的下游连锁（订单/履约/账务）分析。
 * ============================================================
 */
