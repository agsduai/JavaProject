/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：51-05
 * 课名：对账系统
 * ------------------------------------------------------------
 * 知识点：
 *   - **对账是资金安全的最后防线**：回调丢失、状态机 bug、渠道异常——所有上游失误最终都会体现在"渠道账单 vs 本地流水"的差异里。**每天 T+1 对账一次是底线**（高频交易可小时级），对账发现的问题必须有闭环处理流程。
 *   - **对账的输入与流程**：渠道账单文件（下载/解析/入库）-> 与本地支付流水按**渠道交易号**对齐 -> 三态分类：**两边一致**（通过）、**长款**（渠道有本地无——回调丢失/处理失败）、**短款**（本地有渠道无——重复记账/伪造）-> 差异单进处理流程。
 *   - **差异处理的分级闭环**：长款（渠道成功本地未处理）-> 自动补处理（查单 -> 状态机推进 -> 补记账）；短款（本地成功渠道无）-> 严重问题（可能资损/伪造）-> 立即冻结 + 人工排查；金额不一致 -> 精确到分的差异记录 -> 人工裁决。**差异单是工单，有 SLA 有责任人**（41-10 台账思想）。
 *   - **对账文件本身的可靠性**：渠道账单可能分片、有总额行（校验行数与金额合计）、字符集/分隔符差异（46-09 数据集成的老问题）——**文件级校验先行**（总额对不上不进明细对账，先查文件解析）。
 *   - **对账的粒度与时效**：日对账（T+1）兜底 + **准实时对账**（小时级增量）缩短发现窗口——**发现延迟决定资损敞口**（凌晨丢 1000 笔回调，第二天才对账 = 敞口一天）。
 *   - 易错点：对账结果只出报表无闭环（差异单无人处理）；只对金额不对状态（状态漏推发现不了）；渠道账单字段假设过强（渠道改版解析崩）；差异单无 SLA（积压一个月）。
 * ============================================================
 */

package com.heima.ch51;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class ReconciliationSystem {
    record LocalTx(String channelTxId, long amountCents, String status) {
    }

    record ChannelTx(String channelTxId, long amountCents, String status) {
    }

    enum DiffType {
        MATCH, LONG, SHORT, AMOUNT_MISMATCH
    }

    record Diff(DiffType type, String channelTxId, String detail, String action) {
    }

    static void reconcile() {
        System.out.println("【日终对账：渠道账单 vs 本地流水】");
        Map<String, LocalTx> local = new LinkedHashMap<>();
        local.put("ch-1", new LocalTx("ch-1", 6900, "SUCCESS"));
        local.put("ch-2", new LocalTx("ch-2", 3900, "SUCCESS"));
        local.put("ch-3", new LocalTx("ch-3", 5900, "SUCCESS"));
        Map<String, ChannelTx> channel = new LinkedHashMap<>();
        channel.put("ch-1", new ChannelTx("ch-1", 6900, "SUCCESS"));
        channel.put("ch-2", new ChannelTx("ch-2", 3900, "SUCCESS"));
        channel.put("ch-4", new ChannelTx("ch-4", 12900, "SUCCESS"));   // 渠道有本地无（回调丢失）
        channel.put("ch-3", new ChannelTx("ch-3", 590, "SUCCESS"));     // 金额不一致
        List<Diff> diffs = new ArrayList<>();
        for (Map.Entry<String, ChannelTx> e : channel.entrySet()) {
            LocalTx lt = local.get(e.getKey());
            if (lt == null) {
                diffs.add(new Diff(DiffType.LONG, e.getKey(),
                        "渠道成功本地缺失", "查单补处理：推进状态机 + 补记账"));
            } else if (lt.amountCents() != e.getValue().amountCents()) {
                diffs.add(new Diff(DiffType.AMOUNT_MISMATCH, e.getKey(),
                        "本地 " + lt.amountCents() + " vs 渠道 " + e.getValue().amountCents(),
                        "冻结 + 人工裁决（精确到分）"));
            } else {
                diffs.add(new Diff(DiffType.MATCH, e.getKey(), "一致", "通过"));
            }
        }
        for (String id : local.keySet()) {
            if (!channel.containsKey(id)) {
                diffs.add(new Diff(DiffType.SHORT, id, "本地成功渠道无",
                        "严重：疑似伪造/重复记账 -> 冻结 + 立即排查"));
            }
        }
        for (Diff d : diffs) {
            System.out.printf("  [%-15s] %-6s %s%n    处置：%s%n", d.type(), d.channelTxId(),
                    d.detail(), d.action());
        }
        System.out.println();
    }

    static void fileValidation() {
        System.out.println("【账单文件先校验，再进明细对账】");
        record Check(String item, String method, String result) {
        }
        List<Check> checks = List.of(
                new Check("总行数", "文件行数 = 明细行 + 头尾行", "PASS"),
                new Check("金额合计", "明细金额合计 = 汇总行金额", "FAIL：差 12900 分"),
                new Check("处理", "总额不平 -> 不进明细对账", "FAIL：先查解析/分片"));
        for (Check c : checks) {
            System.out.printf("  %-8s %-30s %s%n", c.item(), c.method(), c.result());
        }
        System.out.println("  原因：文件分片下载漏了一段 -> 明细对账会误报海量短款");
        System.out.println("  文件级校验把「解析问题」与「真实差异」分开 —— 排障不混线");
        System.out.println();
    }

    static void slaLoop() {
        System.out.println("【差异单的 SLA 闭环】");
        List<String> loop = List.of(
                "差异单生成 -> 分级：长款（自动可处理）/ 短款（冻结人工）/ 金额差（人工）",
                "长款自动补处理：查单 -> 状态机推进 -> 补记账 -> 复核",
                "短款冻结：相关支付单挂起，禁止后续操作，4 小时内响应",
                "月度差异分析：分类统计根因（回调丢失/状态机 bug/渠道异常）-> 进改进项",
                "目标：差异单平均处理时长 < 24h，长款自动处理率 > 90%");
        for (String s : loop) {
            System.out.println("  " + s);
        }
        System.out.println("  对账不是报表，是闭环流程 —— 42-11 复盘思想在资金域的常态运行");
    }

    public static void main(String[] args) {
        reconcile();
        fileValidation();
        slaLoop();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现对账器完整版（本地/渠道 Map 对齐 + 四类差异 + 差异单分级），构造四类差异各一例验证。
 * 2. 实现账单文件校验器（行数/总额/编码），注入"分片丢失"场景验证文件级拦截。
 * 3. 思考题：为什么"短款"比"长款"更危险？从资金安全（伪造/资损）与处理自动化的可能性两个角度对比。
 * ============================================================
 */
