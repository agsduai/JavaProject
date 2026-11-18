/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：51-08
 * 课名：清结算与复式记账
 * ------------------------------------------------------------
 * 知识点：
 *   - **账务的基石是复式记账**：每笔资金变动记**借贷两方**（借：应收/银行存款，贷：收入/应付）——**有借必有贷，借贷必相等**；任何时刻试算平衡（借方合计 = 贷方合计）是账务正确性的硬校验。**"钱去哪了"每一分都可追**。
 *   - **账户体系**：用户账户、商户账户、平台收入账户、渠道清算户——**账户只是记账的维度**，余额 = 该账户分录的合计。内部户（平台收入）与外部户（用户/商户）分账簿。
 *   - **清算与结算**：**清算**算清楚谁欠谁多少（渠道 T+1 账单 -> 应结算金额，扣除手续费）；**结算**实际资金划拨（打款给商户）。**结算依赖对账通过**（51-05 未平的差异暂停结算）。
 *   - **分账与手续费**：平台型业务（用户付 100 -> 商户 90 + 平台 10）的分账分录——每笔交易拆多条分录，**手续费、退款、调价都影响分账**——分账规则是商户契约（46-08）。
 *   - **账务与业务事件的原子性**：支付成功事件 -> 账务分录（51-03 同事务打包）——**账务落后于业务 = 账实不符**，日终"业务流水 vs 账务分录"再对一遍（第二层对账）。
 *   - 易错点：单式记账（只有流水没有借贷，对不平）；分录与业务事件不同事务（丢分录）；手续费计算舍入不一致（渠道与本地差分）；退款分录方向搞反（试算失衡）。
 * ============================================================
 */

package com.heima.ch51;

import java.util.ArrayList;
import java.util.List;

// ==================== 示例代码 ====================

public class ClearingLedger {
    /** 分录：复式记账（借贷相等） */
    record Entry(String account, String direction, long amountCents) {
        boolean isDebit() {
            return "DEBIT".equals(direction);
        }
    }

    record Voucher(String voucherId, List<Entry> entries, String bizType) {
        boolean balanced() {
            long debit = entries.stream().filter(Entry::isDebit).mapToLong(Entry::amountCents).sum();
            long credit = entries.stream().filter(e -> !e.isDebit()).mapToLong(Entry::amountCents).sum();
            return debit == credit;
        }
    }

    static void voucherDemo() {
        System.out.println("【一笔支付的分录：用户付 100 元，手续费 0.6 元，商户入账 99.4 元】");
        List<Entry> entries = List.of(
                new Entry("渠道清算户", "DEBIT", 10000),          // 借：收到渠道资金
                new Entry("商户应付户", "CREDIT", 9940),          // 贷：应付商户
                new Entry("平台收入户", "CREDIT", 60));           // 贷：手续费收入
        Voucher v = new Voucher("V-1", entries, "PAYMENT");
        for (Entry e : entries) {
            System.out.printf("  %-6s %-8s %d 分%n", e.account(), e.isDebit() ? "借" : "贷", e.amountCents());
        }
        System.out.printf("  借方合计 %d = 贷方合计 %d -> 试算平衡：%b%n",
                entries.stream().filter(Entry::isDebit).mapToLong(Entry::amountCents).sum(),
                entries.stream().filter(e -> !e.isDebit()).mapToLong(Entry::amountCents).sum(),
                v.balanced());
        System.out.println("  10000 = 9940 + 60 —— 每一分钱都有去向");
        System.out.println();
    }

    static void refundVoucher() {
        System.out.println("【退款分录：反向冲正】");
        List<Entry> entries = List.of(
                new Entry("商户应付户", "DEBIT", 2000),           // 借：冲减应付商户
                new Entry("渠道清算户", "CREDIT", 2000));         // 贷：资金退回用户
        Voucher v = new Voucher("V-2", entries, "REFUND");
        System.out.printf("  退款 20 元：借商户应付 %d = 贷渠道清算 %d -> 平衡：%b%n",
                2000, 2000, v.balanced());
        System.out.println("  方向搞反 -> 试算失衡当场发现 —— 复式记账自带纠错检测");
        System.out.println();
    }

    static void clearingFlow() {
        System.out.println("【清算结算：从渠道账单到打款】");
        List<String> flow = List.of(
                "T+1 渠道账单 -> 对账通过（51-05）",
                "清算：应收渠道金额 - 手续费 = 应结算商户额（分账规则）",
                "结算批次生成 -> 财务复核 -> 打款指令",
                "差异未平的商户 -> 暂停结算（差额进待处理）",
                "结算完成 -> 分录核销 -> 商户余额可查");
        for (String s : flow) {
            System.out.println("  " + s);
        }
        System.out.println("  对账是结算的前置闸门：差异未平不放款 —— 资金安全顺序不可颠倒");
    }

    public static void main(String[] args) {
        voucherDemo();
        refundVoucher();
        clearingFlow();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现分录生成器：支付/退款/手续费三类业务生成借贷平衡分录，注入"方向搞反"验证试算失衡检测。
 * 2. 设计商户分账规则表（费率/封顶/舍入），实现一笔 100 元交易的分账分录并与渠道账单核对。
 * 3. 思考题：为什么复式记账"自带纠错检测"？从借贷平衡的数学约束与单式记账的对账成本对比分析。
 * ============================================================
 */
