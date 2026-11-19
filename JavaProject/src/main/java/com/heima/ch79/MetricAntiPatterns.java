/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：79-10
 * 课名：效能度量与反模式
 * ------------------------------------------------------------
 * 知识点：
 *   - **Goodhart 定律**：**"当一个指标成为目标，它就不再是好指标"**——**用代码行数考核 -> 代码膨胀；用故事点考核 -> 估算注水；用缺陷数考核 -> 不报缺陷**。
 *   - **三个常见反模式**：**活动指标当产出**（工时/代码行/提交数）、**跨团队排名**（制造内卷与数据造假）、**只看速度不看质量**（部署频率上去了，故障率也上去了）。
 *   - **好的度量**：**成对指标**（速度 + 稳定性）、**团队自比**（这个季度 vs 上季度）、**面向系统**（流动效率/瓶颈）而不是"给人打分"。
 *   - **度量的目的**：**发现改进点**，不是**评判个人**——**一旦用于考核，数据就会失真**（这是组织问题，不是技术问题）。
 *   - 与 79-01 的关系：DORA 四指标是"系统指标"，**本章讲"怎么用才不跑偏"**——**指标是方向盘，不是鞭子**。
 *   - 易错点：用 LOC 排名；用"在线时长"衡量敬业；用缺陷数扣绩效；把团队对比公开化。
 * ============================================================
 */

package com.heima.ch79;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class MetricAntiPatterns {
    record Team(String name, int features, int loc, int bugs, int deploys, int incidents) {
    }

    static void locRanking(List<Team> teams) {
        System.out.println("【反模式：按代码行数排名】");
        List<Team> sorted = new ArrayList<>(teams);
        sorted.sort(Comparator.comparingInt(Team::loc).reversed());
        for (Team t : sorted) {
            System.out.printf("  %-6s LOC=%-6d 功能数=%-3d -> 排名按 LOC%n",
                    t.name(), t.loc(), t.features());
        }
        System.out.println("  问题：写得多不等于价值高，反而奖励臃肿代码");
        System.out.println();
    }

    static void valueView(List<Team> teams) {
        System.out.println("【正确视角：单位功能成本 + 质量】");
        for (Team t : teams) {
            double locPerFeature = (double) t.loc() / t.features();
            double bugsPerFeature = (double) t.bugs() / t.features();
            System.out.printf("  %-6s 功能=%-3d LOC/功能=%6.1f 缺陷/功能=%.2f 发布=%d 事故=%d%n",
                    t.name(), t.features(), locPerFeature, bugsPerFeature,
                    t.deploys(), t.incidents());
        }
        System.out.println("  B 团队功能相同、代码更少、缺陷更少 -> 实际更高效");
        System.out.println();
    }

    static void goodhart() {
        System.out.println("【Goodhart 演示：用「缺陷数」考核】");
        int reported = 10;
        for (int round = 1; round <= 3; round++) {
            System.out.printf("  第 %d 月：考核压力上升，上报缺陷=%d（真实缺陷约 %d）%n",
                    round, reported, 10 + round * 2);
            reported -= 3;                            // 大家开始不报
        }
        System.out.println("  结果：指标「变好」，系统实际变差 —— 指标被博弈了");
        System.out.println();
    }

    static void principles() {
        System.out.println("【度量原则】");
        String[][] rows = {
                {"成对", "速度 + 稳定性", "部署频率 + 变更失败率"},
                {"自比", "团队 vs 自己的过去", "本季度 vs 上季度"},
                {"系统", "流动效率/瓶颈", "等待时间占比"},
                {"不可考核个人", "只用于改进", "一旦排名就失真"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-10s %-22s %s%n", r[0], r[1], r[2]);
        }
        System.out.println();
    }

    public static void main(String[] args) {
        List<Team> teams = List.of(
                new Team("A", 10, 5000, 30, 20, 4),
                new Team("B", 10, 1200, 8, 40, 1),
                new Team("C", 6, 2000, 5, 12, 0));

        locRanking(teams);
        valueView(teams);
        goodhart();
        principles();

        System.out.println("【结论】度量要成对、自比、面向系统；用于考核个人必然失真");
        Map<String, String> summary = new LinkedHashMap<>();
        summary.put("LOC", "活动指标，不反映价值");
        summary.put("故事点", "估算单位，不能跨团队比较");
        summary.put("DORA", "系统指标，适合找瓶颈");
        summary.forEach((k, v) -> System.out.printf("  %-8s %s%n", k, v));
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 用两组"功能数/代码行/缺陷/发布"数据，演示 LOC 排名与单位功能成本的结论相反。
 * 2. 设计一组"成对指标"（速度 + 稳定性），说明如何避免 Goodhart。
 * 3. 思考题：为什么"指标用于个人考核"必然失真？从博弈行为与数据上报分析。
 * ============================================================
 */
