/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：79-01
 * 课名：研发效能全景
 * ------------------------------------------------------------
 * 知识点：
 *   - **DORA 四个指标**：**部署频率**（多久发一次）、**变更前置时间**（commit 到上线）、**变更失败率**（发布导致故障的比例）、**恢复时长 MTTR**——**前两个看速度，后两个看稳定性**，必须成对看。
 *   - **价值流（Value Stream）**：**从需求到上线拆成若干阶段**，**每阶段记录"处理时间"与"等待时间"**——**真正拖慢交付的往往是等待（排队/评审/环境）而不是编码**。
 *   - **效能不能只看"工作量"**：**代码行数、故事点、工时**都是"活动指标"，**容易造假且与价值无关**；**要看向"流动效率"**（处理时间/总时间）与"交付结果"。
 *   - **瓶颈识别**：**价值流中等待时间最长的阶段就是瓶颈**——**优化非瓶颈阶段不会提升整体吞吐**（约束理论）。
 *   - 与 41 章的关系：41 章 CI/CD 讲"流水线怎么建"，**本章讲"流水线建得好不好、瓶颈在哪"**——**从工具到度量**。
 *   - 易错点：只追部署频率（忽视失败率）；用代码行数考核（鼓励臃肿）；优化编码（瓶颈常在评审/测试/环境）；把"忙"当"高效"。
 * ============================================================
 */

package com.heima.ch79;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class DevOpsMetrics {
    record Deploy(int day, boolean success, double leadTimeHours, double restoreMinutes) {
    }

    record Stage(String name, double processHours, double waitHours) {
        double totalHours() {
            return processHours + waitHours;
        }
    }

    static void dora(List<Deploy> deploys) {
        int days = 30;
        long success = deploys.stream().filter(Deploy::success).count();
        double deployFreq = (double) deploys.size() / days;
        double avgLead = deploys.stream().mapToDouble(Deploy::leadTimeHours).average().orElse(0);
        double failureRate = deploys.isEmpty() ? 0
                : (double) (deploys.size() - success) / deploys.size();
        double mttr = deploys.stream().filter(d -> !d.success())
                .mapToDouble(Deploy::restoreMinutes).average().orElse(0);

        System.out.println("【DORA 四指标】");
        System.out.printf("  部署频率：%.2f 次/天（%.1f 次/月）%n", deployFreq, deployFreq * days);
        System.out.printf("  变更前置时间：%.1f 小时%n", avgLead);
        System.out.printf("  变更失败率：%.1f%%%n", failureRate * 100);
        System.out.printf("  平均恢复时长：%.0f 分钟%n", mttr);
        System.out.println();
    }

    static void valueStream(List<Stage> stages) {
        double total = 0;
        double process = 0;
        Stage bottleneck = stages.get(0);
        for (Stage s : stages) {
            total += s.totalHours();
            process += s.processHours();
            if (s.waitHours() > bottleneck.waitHours()) {
                bottleneck = s;
            }
        }
        System.out.println("【价值流】");
        for (Stage s : stages) {
            double efficiency = s.totalHours() == 0 ? 0 : s.processHours() / s.totalHours();
            System.out.printf("  %-10s 处理=%5.1fh 等待=%5.1fh 流动效率=%4.0f%%%n",
                    s.name(), s.processHours(), s.waitHours(), efficiency * 100);
        }
        System.out.printf("  总时长=%.1fh，处理=%.1fh，整体流动效率=%.1f%%%n",
                total, process, process / total * 100);
        System.out.printf("  瓶颈：%s（等待 %.1fh 最长）%n", bottleneck.name(), bottleneck.waitHours());
        System.out.println();
    }

    public static void main(String[] args) {
        List<Deploy> deploys = new ArrayList<>();
        for (int d = 1; d <= 30; d++) {
            boolean ok = d % 10 != 0;                // 每 10 次失败 1 次
            double lead = 18 + (d % 5) * 3;
            double restore = ok ? 0 : 40 + (d % 3) * 10;
            deploys.add(new Deploy(d, ok, lead, restore));
        }
        dora(deploys);

        List<Stage> stages = List.of(
                new Stage("需求评审", 4, 20),
                new Stage("编码", 24, 6),
                new Stage("代码评审", 2, 18),
                new Stage("测试", 8, 30),
                new Stage("发布", 1, 4));
        valueStream(stages);

        Map<String, String> actions = new LinkedHashMap<>();
        actions.put("瓶颈", "测试等待 30h -> 环境按需创建（79-06）+ 自动化回归");
        actions.put("次瓶颈", "需求评审等待 20h -> 需求模板 + 评审 SLA");
        actions.put("编码", "24h 处理时间不是瓶颈，加人收益有限（约束理论）");
        System.out.println("【改进优先级】");
        actions.forEach((k, v) -> System.out.printf("  %-6s %s%n", k, v));

        System.out.println();
        System.out.println("【结论】优化瓶颈才提升吞吐；先度量流动效率，再谈工具");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 用你团队近 30 次发布的数据算 DORA 四指标，判断"快而不稳"还是"稳而不快"。
 * 2. 画一条价值流（需求到上线），标出每阶段处理/等待时间，找出瓶颈并给出改进项。
 * 3. 思考题：为什么"优化非瓶颈阶段"不提升整体吞吐？用约束理论解释。
 * ============================================================
 */
