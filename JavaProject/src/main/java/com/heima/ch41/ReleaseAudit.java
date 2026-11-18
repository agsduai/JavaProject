/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：41-10
 * 课名：发布自动化与审计
 * ------------------------------------------------------------
 * 知识点：
 *   - **发布的理想形态是"按钮工程"**：触发 = 打版本 tag；执行 = 流水线全自动（测试 -> 制品 -> 晋升 -> 部署 -> 验证）；人工只出现在两处——prod 放行审批与异常时的回滚决定。**自动化不是减少步骤，是把步骤变成可重复、可审计的机器动作**。
 *   - **发布单（change record）是审计单元**：谁、何时、发布了什么（制品版本）、到哪些环境、变更类型（常规/紧急/回滚）、审批人、验证结果、工单关联。**每一条生产变更都应能从发布单回溯到提交与验证证据**。
 *   - **紧急发布的绿色通道与代价**：紧急修复也走流水线但允许并行审批、跳过部分低风险门禁（如 staging 观察 24h）。**"紧急"是路径更快，不是没有记录**——事故后的"谁改了什么"如果答不出来，紧急发布就成了二次事故源。
 *   - **审批的最小化原则**：审批人要有上下文（变更内容 + 影响面 + 验证证据），自动汇总进审批卡片；高频低风险变更（纯配置、文案）降级为自动放行 + 事后抽查。**审批膨胀 = 团队学会绕过流程**。
 *   - **发布窗口与冻结期**：大促/月末结算冻结发布（只允许 hotfix）；常规发布窗口避开周五下午与节前。冻结期是业务节奏，写进日历并自动拦截。
 *   - **度量发布工程**：部署频率、变更前置时间（commit->prod）、变更失败率、MTTR——DORA 四指标。**度量驱动改进，而不是 KPI 表演**。
 *   - 易错点：跳板机上手工 `kubectl set image`（无记录无审计）；发布单事后补填（等于没有）；紧急发布习惯化（每次都说紧急，流程形同虚设）；审批链条 5 层（变更前置时间被审批拖到一周）。
 * ============================================================
 */

package com.heima.ch41;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class ReleaseAudit {
    record ReleaseRecord(String id, String artifact, String operator, String approver,
                         String type, String result, String refs) {
    }

    static final List<ReleaseRecord> LEDGER = List.of(
            new ReleaseRecord("R-3001", "order:2.4.1-142", "ci-bot", "lead-wang",
                    "常规", "成功：金丝雀 1h 全量", "JIRA-1234"),
            new ReleaseRecord("R-3002", "order:2.4.2-143", "ci-bot", "lead-wang",
                    "紧急", "成功：跳过 staging 观察，事故修复", "INC-456"),
            new ReleaseRecord("R-3003", "order:2.4.1-141", "ci-bot", "auto(回滚)",
                    "回滚", "成功：金丝雀错误率触发自动回退", "R-3001"));

    static void ledger() {
        System.out.println("【发布台账：每条生产变更一行】");
        for (ReleaseRecord r : LEDGER) {
            System.out.printf("  %-7s %-18s %-9s %-10s %-4s %s%n        %s%n",
                    r.id(), r.artifact(), r.operator(), r.approver(), r.type(), r.result(), r.refs());
        }
        System.out.println("  可回答：生产现在跑什么？谁批的？出事时改了什么？");
        System.out.println();
    }

    static void emergencyLane() {
        System.out.println("【紧急通道：更快，而不是更少记录】");
        Map<String, String> normal = new LinkedHashMap<>();
        normal.put("门禁", "全量测试 + staging 观察 24h + 金丝雀");
        normal.put("审批", "双人（变更人 + 负责人）");
        Map<String, String> emergency = new LinkedHashMap<>();
        emergency.put("门禁", "全量测试 + 金丝雀（跳过 staging 观察期）");
        emergency.put("审批", "单人 + 事后 24h 内补复盘");
        System.out.println("  常规：" + normal);
        System.out.println("  紧急：" + emergency);
        System.out.println("  共同点：发布单、制品版本、审计记录一样不少");
        System.out.println("  约束：紧急发布占比 > 10% -> 流程有问题，触发复盘");
        System.out.println();
    }

    static void dora() {
        System.out.println("【DORA 四指标：发布工程的体检表】");
        record Metric(String name, String def, String elite, String ours) {
        }
        List<Metric> ms = List.of(
                new Metric("部署频率", "prod 发布次数/天", "按需（日多次）", "2 次/周"),
                new Metric("变更前置时间", "commit -> prod", "< 1 天", "3 天"),
                new Metric("变更失败率", "引发降级/回滚的比例", "< 15%", "8%"),
                new Metric("MTTR", "故障恢复时长", "< 1 小时", "40 分钟"));
        for (Metric m : ms) {
            System.out.printf("  %-12s %-18s 精英=%-14s 我们=%s%n",
                    m.name(), m.def(), m.elite(), m.ours());
        }
        System.out.println("  短板定位：前置时间 3 天主要卡在人工审批 -> 审批最小化");
    }

    public static void main(String[] args) {
        ledger();
        emergencyLane();
        dora();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 为你的团队设计发布单模板与台账存储（表格或数据库），实现"发布单 -> 制品 -> 提交"三层链接可点击跳转。
 * 2. 统计你团队过去一个月的 DORA 四指标，找出最差一项并给出改进动作与目标值。
 * 3. 思考题：为什么"紧急发布跳过审批但不能跳过记录"？从事后追责与流程信任两个角度分析。
 * ============================================================
 */
