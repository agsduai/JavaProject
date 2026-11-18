/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：41-11
 * 课名：综合案例：一条完整的发布流水线
 * ------------------------------------------------------------
 * 知识点：
 *   - **把本章串成一条端到端流水线**：提交（Conventional Commits）-> PR 流水线（编译/检查/测试）-> 合入 main -> 构建制品（版本号+签名）-> 自动部署 test -> 冒烟 -> 人工放行 prod -> 金丝雀 1%~100%（自动判据）-> 发布单归档 -> 观察与复盘。**第 37 章的容器、第 41 章的流程在此汇合**。
 *   - **每一步的"通过标准"都要机器可判**：测试通过率 100%、扫描无 Critical、冒烟接口 200、金丝雀错误率相对基线 +2% 以内。**写不进判据的验证就是装饰**。
 *   - **故障演练贯穿**：流水线红灯演练（挂掉一个测试看拦截）、金丝雀自动回退演练（制造错误率飙升）、回滚演练（切回旧制品的实测时长）。**演练过的故障才叫预案，没演练过的叫想象**。
 *   - **一次真实发布的时间预算**：PR 验证 8 分钟、构建 3 分钟、test 冒烟 5 分钟、金丝雀全程 90 分钟——**从 commit 到全量约 2 小时，全程无需盯守**（金丝雀自动判据接管）。
 *   - **发布文化**：发布是日常动作不是仪式——高频小步、失败自动回退、台账自动记录。**发布越频繁，单次风险越小**（变更小、演练多、回滚熟练）。
 *   - 易错点：流水线全绿但没人看过部署结果（验证缺失）；金丝雀判据写了没接自动回退（指标只是装饰）；发布单与制品脱节（审计断链）；把本章当作一次性建设（发布工程是持续运营的）。
 * ============================================================
 */

package com.heima.ch41;

import java.util.ArrayList;
import java.util.List;

// ==================== 示例代码 ====================

public class PipelineCase {
    enum StepResult {
        PASS, FAIL, ROLLBACK
    }

    record Phase(int no, String name, String action, String gate, StepResult result) {
    }

    static void fullPipeline() {
        System.out.println("【order 服务 2.4.1-142 的完整发布时间线】");
        List<Phase> phases = List.of(
                new Phase(1, "提交", "feat(order): 批量取消 + 单测", "Conventional 格式校验", StepResult.PASS),
                new Phase(2, "PR 流水线", "编译30s + lint/scan60s + 单测180s", "全绿 + 1 人审批", StepResult.PASS),
                new Phase(3, "构建制品", "jar + 镜像，签名入库", "版本号含提交摘要", StepResult.PASS),
                new Phase(4, "test 环境", "自动部署 + 冒烟 5 项接口", "接口全部 200", StepResult.PASS),
                new Phase(5, "staging", "脱敏数据回归 + 压测 P99 达标", "无 Critical 漏洞", StepResult.PASS),
                new Phase(6, "prod 金丝雀", "1% -> 5% -> 25% -> 50% -> 100%",
                        "错误率相对基线 +2% 内", StepResult.PASS),
                new Phase(7, "归档", "发布单 R-3001 自动生成", "制品/审批/证据齐全", StepResult.PASS));
        int minute = 0;
        for (Phase p : phases) {
            System.out.printf("  T+%dmin 步骤%d %-12s %s%n        闸门：%s -> %s%n",
                    minute, p.no(), p.name(), p.action(), p.gate(), p.result());
            minute += switch (p.no()) {
                case 1, 2 -> 8;
                case 3 -> 3;
                case 4, 5 -> 5;
                default -> 90;
            };
        }
        System.out.printf("  commit 到全量约 %d 分钟，全程无需盯守%n", minute);
        System.out.println();
    }

    static void drills() {
        System.out.println("【三项常态化演练】");
        record Drill(String name, String action, String expect, String measured) {
        }
        List<Drill> drills = List.of(
                new Drill("红灯拦截", "在 PR 分支故意提交失败测试", "流水线 FAIL，PR 无法合并",
                        "实测拦截生效"),
                new Drill("金丝雀回退", "向金丝雀实例注入 5xx", "错误率超阈值自动回退并告警",
                        "实测 2 分 10 秒完成回退"),
                new Drill("回滚", "部署上一版本制品", "回滚到稳定版，时长 < 2 分钟",
                        "实测 1 分 30 秒"));
        for (Drill d : drills) {
            System.out.printf("  %-8s %s%n    期望：%s%n    实测：%s%n",
                    d.name(), d.action(), d.expect(), d.measured());
        }
        System.out.println("  演练频率：每迭代一次，结果进发布复盘");
        System.out.println();
    }

    static void timeBudget() {
        System.out.println("【发布时间预算与人工介入点】");
        List<String> rows = List.of(
                "自动段（约 50 分钟）：PR 验证 -> 构建 -> test/staging 部署与验证",
                "人工点 1：prod 放行审批（1 分钟，看到的是自动汇总的变更内容+验证证据）",
                "自动段（约 90 分钟）：金丝雀放量，自动判据接管（暂停/告警/回退）",
                "人工点 2（仅异常）：回退决定或紧急通道");
        for (String r : rows) {
            System.out.println("  " + r);
        }
        System.out.println("  原则：人工只出现在「决策」，一切「执行」都自动化");
    }

    public static void main(String[] args) {
        fullPipeline();
        drills();
        timeBudget();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 把本章时间线在你的项目真实跑一遍（可以缩小规模），记录每步实际耗时与"通过证据"，形成你的发布手册第一版。
 * 2. 实现金丝雀自动判据与自动回退的闭环（模拟指标流即可），并做一次"注入 5xx"演练记录回退时长。
 * 3. 思考题：为什么说"发布越频繁，单次风险越小"？用本章 DORA 指标与演练机制解释高频发布的风险控制闭环。
 * ============================================================
 */
