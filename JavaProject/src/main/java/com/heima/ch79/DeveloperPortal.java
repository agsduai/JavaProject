/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：79-09
 * 课名：开发者门户与 Golden Path
 * ------------------------------------------------------------
 * 知识点：
 *   - **开发者门户（Developer Portal）是内部平台的"入口"**：**服务目录**（有哪些服务、谁负责、什么级别）、**自助文档**、**脚手架入口**、**环境申请**、**记分卡**——**把散落的工具收口到一个地方**。
 *   - **Golden Path（黄金路径）**：**平台推荐的"默认正确做法"**——**脚手架 + 流水线模板 + 监控/日志默认接入 + 部署规范**；**走默认路径最省力，偏离要有理由**——**降低认知负担**。
 *   - **服务记分卡（Scorecard）**：**用可自动检查的项给服务打分**（有 owner、有 runbook、有 SLO、有告警、有文档、依赖最新）——**分数不是考核，是改进引导**（79-10 的度量反模式）。
 *   - **分级（Tier）**：**Tier1 核心**（高可用/严格 SLO）、**Tier2 重要**、**Tier3 一般**——**不同级别要求不同**，**避免"所有服务都按最高标准"**。
 *   - 与 56 章的关系：56 章讲"稳定性工程"，**门户的记分卡是"稳定性要求的落地检查表"**——**从原则到可勾选项**。
 *   - 易错点：门户只是"链接集合"（没有自助能力）；Golden Path 不维护（模板过期）；记分卡用来排名惩罚（团队抵触）；所有服务同一标准（资源浪费）。
 * ============================================================
 */

package com.heima.ch79;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class DeveloperPortal {
    enum Tier { TIER1, TIER2, TIER3 }

    record Service(String name, String owner, Tier tier, boolean hasRunbook,
                   boolean hasSlo, boolean hasAlerts, boolean hasDocs, int outdatedDeps) {
    }

    record Score(Service service, int score, List<String> gaps) {
    }

    /** 记分卡：可自动检查的项，按 tier 加权 */
    static Score score(Service s) {
        int total = 0;
        int max = 0;
        List<String> gaps = new ArrayList<>();

        int ownerWeight = s.tier() == Tier.TIER1 ? 25 : 15;
        max += ownerWeight;
        if (s.owner() != null && !s.owner().isBlank()) {
            total += ownerWeight;
        } else {
            gaps.add("缺 owner");
        }

        int runbookWeight = s.tier() == Tier.TIER1 ? 25 : 15;
        max += runbookWeight;
        if (s.hasRunbook()) {
            total += runbookWeight;
        } else {
            gaps.add("缺 runbook");
        }

        int sloWeight = s.tier() == Tier.TIER1 ? 25 : 10;
        max += sloWeight;
        if (s.hasSlo()) {
            total += sloWeight;
        } else {
            gaps.add("缺 SLO");
        }

        int alertWeight = 15;
        max += alertWeight;
        if (s.hasAlerts()) {
            total += alertWeight;
        } else {
            gaps.add("缺告警");
        }

        int docWeight = 10;
        max += docWeight;
        if (s.hasDocs()) {
            total += docWeight;
        } else {
            gaps.add("缺文档");
        }

        if (s.outdatedDeps() > 5) {
            gaps.add("依赖过期 " + s.outdatedDeps() + " 个");
        } else {
            total += 10;
        }
        max += 10;

        return new Score(s, (int) Math.round(total * 100.0 / max), gaps);
    }

    public static void main(String[] args) {
        List<Service> services = new ArrayList<>(List.of(
                new Service("gateway", "架构组", Tier.TIER1, true, true, true, true, 0),
                new Service("order", "交易团队", Tier.TIER1, true, true, false, true, 2),
                new Service("report", "数据团队", Tier.TIER2, false, false, true, true, 8),
                new Service("demo", "个人", Tier.TIER3, false, false, false, false, 20)));

        System.out.println("【服务目录】");
        for (Service s : services) {
            System.out.printf("  %-8s owner=%-6s tier=%-6s runbook=%-5s slo=%-5s alerts=%-5s docs=%s%n",
                    s.name(), s.owner(), s.tier(), s.hasRunbook(), s.hasSlo(),
                    s.hasAlerts(), s.hasDocs());
        }

        System.out.println();
        System.out.println("【记分卡】");
        List<Score> scores = new ArrayList<>();
        services.forEach(s -> scores.add(score(s)));
        scores.sort(Comparator.comparingInt(Score::score).reversed());
        for (Score sc : scores) {
            System.out.printf("  %-8s %3d 分  %s%n", sc.service().name(), sc.score(),
                    sc.gaps().isEmpty() ? "无缺口" : "缺口：" + sc.gaps());
        }

        System.out.println();
        System.out.println("【Golden Path 检查】");
        String[][] path = {
                {"脚手架", "从模板生成，包名/目录/CI 就绪", "79-02"},
                {"流水线", "套用模板，含扫描与测试", "79-07"},
                {"可观测", "日志/指标/追踪默认接入", "42 章"},
                {"部署", "制品晋升 + 回滚预案", "79-08"},
                {"运维", "runbook + SLO + 告警", "56 章"},
        };
        for (String[] p : path) {
            System.out.printf("  %-8s %-30s %s%n", p[0], p[1], p[2]);
        }

        System.out.println();
        System.out.println("【原则】Golden Path 是默认最优解，不是强制；记分卡引导改进，不用于排名惩罚");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现服务记分卡（owner/runbook/SLO/告警/文档/依赖），按 Tier 加权并输出缺口。
 * 2. 为 Tier1 服务设计 Golden Path 检查项，说明每项对应的稳定性要求（56 章）。
 * 3. 思考题：为什么"记分卡用于排名惩罚"会失败？从 Goodhart 定律与团队行为分析。
 * ============================================================
 */
