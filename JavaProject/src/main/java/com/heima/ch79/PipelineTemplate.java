/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：79-07
 * 课名：流水线编排与复用
 * ------------------------------------------------------------
 * 知识点：
 *   - **流水线要"模板化 + 参数化"**：**同一套模板服务所有服务**（构建/测试/扫描/部署），**差异用参数表达**（语言/JDK/部署环境）——**避免每个服务复制一份 YAML**。
 *   - **并行与矩阵**：**无依赖的 stage 并行**（lint/test/scan），**多环境用矩阵展开**（dev/staging/prod）——**总时长 = 关键路径**（最长链），不是所有 stage 之和。
 *   - **stage 的依赖关系**：**DAG 而不是线性列表**——**构建 -> (单测 || 扫描) -> 镜像 -> (部署 dev || 部署 staging)**；**DAG 能表达"谁等谁"**（61-08 的工作流编排）。
 *   - **复用机制**：**模板继承/组合**（include + 参数覆盖）、**共享步骤库**（build/test/deploy 原子步骤）——**复用程度决定维护成本**。
 *   - 与 41 章的关系：41 章讲"CI/CD 的理念与发布策略"，**本章讲"流水线 DSL 与执行编排"**——**从流程到引擎**。
 *   - 易错点：所有 stage 串行（慢）；并行无上限（打爆 runner）；矩阵爆炸（环境组合过多）；模板参数无校验（配错环境）。
 * ============================================================
 */

package com.heima.ch79;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class PipelineTemplate {
    record Stage(String name, long durationSec, String group) {
    }

    /** 模板展开：把 ${env} 替换成矩阵中的环境 */
    static List<Stage> expand(List<Stage> template, List<String> environments) {
        List<Stage> out = new ArrayList<>();
        for (Stage s : template) {
            if (s.name().contains("${env}")) {
                for (String env : environments) {
                    out.add(new Stage(s.name().replace("${env}", env), s.durationSec(), env));
                }
            } else {
                out.add(s);
            }
        }
        return out;
    }

    /** 计算总时长：同 group 并行取 max，其余串行相加 */
    static long totalDuration(List<Stage> stages) {
        long serial = 0;
        Map<String, Long> groups = new LinkedHashMap<>();
        for (Stage s : stages) {
            if (s.group() == null) {
                serial += s.durationSec();
            } else {
                groups.merge(s.group(), s.durationSec(), Math::max);
            }
        }
        long parallel = groups.values().stream().mapToLong(Long::longValue).sum();
        System.out.println("  串行部分=" + serial + "s，并行部分（按组取最大）=" + parallel + "s");
        groups.forEach((g, d) -> System.out.printf("    组 %-8s 最长 %ds%n", g, d));
        return serial + parallel;
    }

    static void execute(List<Stage> stages) {
        long serial = 0;
        Map<String, Long> groups = new LinkedHashMap<>();
        for (Stage s : stages) {
            if (s.group() == null) {
                serial += s.durationSec();
                System.out.printf("  [串行] %-22s %3ds%n", s.name(), s.durationSec());
            } else {
                groups.merge(s.group(), s.durationSec(), Math::max);
            }
        }
        groups.forEach((g, d) -> System.out.printf("  [并行] 组 %-8s 最长 %3ds%n", g, d));
        System.out.printf("  墙钟时间≈%ds（关键路径）%n", serial + groups.values().stream()
                .mapToLong(Long::longValue).sum());
    }

    public static void main(String[] args) {
        List<Stage> template = new ArrayList<>(List.of(
                new Stage("checkout", 5, null),
                new Stage("build", 60, null),
                new Stage("unit-test", 90, "test"),
                new Stage("code-scan", 40, "test"),
                new Stage("package", 30, null),
                new Stage("deploy-${env}", 45, null),
                new Stage("smoke-${env}", 20, null)));

        System.out.println("【模板】");
        template.forEach(s -> System.out.printf("  %-22s %3ds group=%s%n",
                s.name(), s.durationSec(), s.group()));

        System.out.println();
        System.out.println("【矩阵展开：dev / staging / prod】");
        List<Stage> stages = expand(template, List.of("dev", "staging", "prod"));
        stages.forEach(s -> System.out.printf("  %-22s %3ds group=%s%n",
                s.name(), s.durationSec(), s.group()));

        System.out.println();
        System.out.println("【执行编排】");
        execute(stages);
        System.out.println();
        System.out.println("【总时长】");
        long total = totalDuration(stages);

        System.out.println();
        System.out.println("【如果全部串行】");
        long allSerial = stages.stream().mapToLong(Stage::durationSec).sum();
        System.out.printf("  全部串行=%ds，编排后=%ds，节省 %.0f%%%n",
                allSerial, total, (1 - (double) total / allSerial) * 100);

        System.out.println();
        System.out.println("【要点】DAG 表达依赖；同组并行取最长；矩阵展开多环境；总时长看关键路径");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现模板展开（`${env}` 矩阵）与并行组时长计算，验证总时长等于关键路径。
 * 2. 给 stage 加显式依赖（如 deploy 依赖 package），实现拓扑排序执行。
 * 3. 思考题：为什么"并行度无上限"会拖慢整体？从 runner 资源竞争与关键路径分析。
 * ============================================================
 */
