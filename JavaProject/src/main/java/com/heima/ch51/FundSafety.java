/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：51-07
 * 课名：资金安全与风控
 * ------------------------------------------------------------
 * 知识点：
 *   - **资金安全的三道防线**：**事前风控**（交易前识别风险：限流/拦截/增强验证）、**事中校验**（支付链路的参数与权限校验，44-09 四道闸的资金版）、**事后对账与审计**（51-05 + 操作审计不可抵赖）。**任何一道都不能独立兜底**。
 *   - **风控的规则与模型**：规则层（单用户限额、频次、黑名单、地域异常——确定性高、可解释）；模型层（评分/异常检测——覆盖规则盲区）；**决策动作分级**：放行 / 增强验证（二次确认/短信）/ 拦截（44-09 的分级思想）。
 *   - **操作审计与最小权限**：后台的退款/调价/改单操作——**操作人+审批+全量审计日志**（44-09 trace 的资金版）；退款审批走双人（41-10 紧急通道的资金版）；**系统账号与人工操作权限分离**。
 *   - **防内部作恶**：改金额接口只允许服务调用（外部不可达）、金额以数据库为准（模型输出仅参考 40-06）、敏感操作二次确认（独立确认通道）——**信任边界在资金域一寸不让**（36 章原则）。
 *   - **限额体系**：单笔/单日/单月限额（用户级、商户级、渠道级）——限额是风控的硬约束，**超限直接拒绝**而不是风控评分。
 *   - 易错点：风控只拦用户不防内部（内部改单无审批）；规则拦截无解释（客诉无法处理）；审计日志可篡改（无 append-only 与权限隔离）；限额定错了单位（分/元混淆导致限额失效）。
 * ============================================================
 */

package com.heima.ch51;

import java.util.ArrayList;
import java.util.List;

// ==================== 示例代码 ====================

public class FundSafety {
    enum Decision {
        ALLOW, CHALLENGE, BLOCK
    }

    record RiskResult(Decision decision, String reason, String action) {
    }

    static class RiskEngine {
        RiskResult evaluate(String userId, long amountCents, int recentCount) {
            // 规则层：确定性约束（硬限额优先）
            if (amountCents > 500_000) {
                return new RiskResult(Decision.BLOCK, "超单笔限额 5000 元", "拒绝并记录");
            }
            if (recentCount > 20) {
                return new RiskResult(Decision.BLOCK, "频次异常（>20 次/时）", "拒绝，疑似撞库");
            }
            // 规则层：软风险 -> 增强验证
            if (amountCents > 100_000) {
                return new RiskResult(Decision.CHALLENGE, "大额交易", "二次确认/短信验证");
            }
            // 模型层评分（示意）
            double score = 0.15;
            if (score > 0.9) {
                return new RiskResult(Decision.BLOCK, "模型高风险", "拒绝");
            }
            return new RiskResult(Decision.ALLOW, "通过", "正常放行");
        }
    }

    static void threeLines() {
        System.out.println("【三道防线：事前/事中/事后】");
        List<String> lines = List.of(
                "事前：风控引擎（限额/频次/模型）-> 决策 ALLOW/CHALLENGE/BLOCK",
                "事中：四道闸（工具/参数/动作/预算 44-09）+ 验签 + 状态机",
                "事后：对账（51-05）+ 审计（操作不可抵赖）+ 差异闭环");
        for (String l : lines) {
            System.out.println("  " + l);
        }
        System.out.println("  单道防线都会漏：组合冗余才构成安全（36-01 纵深防御）");
        System.out.println();
    }

    static void decisionDemo() {
        System.out.println("【风控决策分级演示】");
        RiskEngine engine = new RiskEngine();
        List<Object[]> cases2 = new ArrayList<>();
        cases2.add(new Object[]{"u1", 500_500L, 2});
        cases2.add(new Object[]{"u2", 150_000L, 2});
        cases2.add(new Object[]{"u3", 6900L, 25});
        cases2.add(new Object[]{"u4", 6900L, 3});
        for (Object[] c : cases2) {
            RiskResult r = engine.evaluate((String) c[0], (Long) c[1], (Integer) c[2]);
            System.out.printf("  user=%s 金额=%d 分 近1h=%d 次 -> %s（%s）动作：%s%n",
                    c[0], c[1], c[2], r.decision(), r.reason(), r.action());
        }
        System.out.println();
    }

    static void auditDuties() {
        System.out.println("【操作审计与内部防线】");
        List<String> duties = List.of(
                "退款/调价/改单：操作人 + 审批人 + 变更前后快照（不可抵赖）",
                "append-only 审计存储：日志不可修改删除（权限隔离）",
                "金额以 DB 为准：模型/请求参数仅参考（40-06 原则）",
                "敏感操作独立确认通道（44-09）：不信任文本确认",
                "系统账号权限最小化：支付服务账号只给所需表（36-01）");
        for (String d : duties) {
            System.out.println("  - " + d);
        }
        System.out.println("  审计不是日志附件，是法律证据 —— 存储与权限按证据标准建设");
    }

    public static void main(String[] args) {
        threeLines();
        decisionDemo();
        auditDuties();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现风控引擎完整版（硬限额/频次/大额增强验证/模型评分四级），写出决策矩阵测试。
 * 2. 设计后台退款操作的审计表结构（操作人/审批人/前后快照），演示一次"无审批被拦截"。
 * 3. 思考题：为什么资金域的审计存储要 append-only？从法律证据效力与内部作恶防线两个角度分析。
 * ============================================================
 */
