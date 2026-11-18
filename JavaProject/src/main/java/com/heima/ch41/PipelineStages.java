/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：41-03
 * 课名：流水线的阶段设计
 * ------------------------------------------------------------
 * 知识点：
 *   - **流水线是一条有准入与准出的质量闸门链**：每个阶段（stage）只有"通过/失败"两种结果，失败即终止——**流水线的价值不在跑得快，而在"绿灯可信"**：绿灯意味着合并的代码满足全部质量约定。
 *   - **经典五阶段**：`编译构建 → 静态检查 → 单元测试 → 打包制品 → 部署验证`。顺序的原则是**便宜的放前面**：编译 30 秒先跑，集成测试 10 分钟放后——失败要尽早暴露，别让开发者等 10 分钟才知道少了个分号。
 *   - **并行与扇出**：互不依赖的阶段并行（静态检查 ∥ 单元测试 ∥ 安全扫描），多模块工程按模块扇出（矩阵构建）。流水线耗时 = 最长路径耗时，优化先砍关键路径。
 *   - **阶段产物必须显式传递**：构建产出的 jar、测试报告、覆盖率、扫描结果都是"产物（artifact）"，下游阶段引用而不是重算。**产物不可变**：测试通过的那个 jar 必须就是部署的那个 jar——重新构建意味着"测的不是发的"。
 *   - **验证环境（smoke test）自动化**：部署到测试环境后自动跑健康检查与核心接口回归，绿灯才能进下一环境。人工点开页面看一眼不是验证。
 *   - **流水线的两种触发语义**：PR 触发（验证合并后的结果，用 merge commit）与 main 触发（构建制品并晋升）。**PR 流水线跑测试但不出制品**，避免未合入代码产生"幽灵版本"。
 *   - **失败即停 + 快速失败**：任何阶段失败流水线终止、通知提交人、门禁拦截合并。红灯超过 30 分钟没人处理，团队会开始"无视红灯"——**红灯响应时间是流水线可信度的生命线**。
 *   - 易错点：流水线里手工点击审批太多（自动化变成人肉流水线）；测试失败后"重跑碰运气"变成习惯（flaky 测试是绿灯不可信的头号原因）；构建产物不固定版本（部署的是什么没人说得清）；在流水线里直接 build 镜像又 push 又部署一步做完（阶段不可回退）。
 * ============================================================
 */

package com.heima.ch41;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class PipelineStages {
    record Stage(String name, int costSec, boolean parallel, boolean blocking) {
    }

    static final List<Stage> STAGES = List.of(
            new Stage("compile", 30, false, true),
            new Stage("lint+scan", 60, true, true),
            new Stage("unit-test", 180, true, true),
            new Stage("package-jar", 45, false, true),
            new Stage("deploy-test", 60, false, true),
            new Stage("smoke-test", 90, false, true),
            new Stage("deploy-prod", 120, false, true));

    static void criticalPath() {
        System.out.println("【流水线耗时 = 关键路径】");
        System.out.println("  compile(30s) -> [lint(60s) ∥ unit-test(180s)] -> package(45s)");
        System.out.println("              -> deploy-test(60s) -> smoke(90s) -> deploy-prod(120s)");
        int path1 = 30 + Math.max(60, 180) + 45 + 60 + 90 + 120;
        int path2 = 30 + 60 + 180 + 45 + 60 + 90 + 120;
        System.out.printf("  并行后总耗时 %d s；若不并行 %d s（省 %d s）%n",
                path1, path2, path2 - path1);
        System.out.println("  优化顺序：先砍关键路径上最贵的阶段（unit-test 提速/分片）");
        System.out.println();
    }

    static void gateWalkthrough() {
        System.out.println("【闸门推演：一次带故障的运行】");
        record Run(String stage, String result, String note) {
        }
        List<Run> runs = List.of(
                new Run("compile", "PASS", "30s"),
                new Run("unit-test", "FAIL", "OrderServiceTest 第 42 行断言失败"),
                new Run("deploy-test", "SKIP", "上游失败，自动跳过"),
                new Run("deploy-prod", "SKIP", "同上"));
        for (Run r : runs) {
            System.out.printf("  %-12s %-5s %s%n", r.stage(), r.result(), r.note());
        }
        System.out.println("  规则：blocking 阶段失败 -> 后续全部 SKIP -> 门禁拦截合并");
        System.out.println("  失败通知 10 分钟内响应；红灯超 30 分钟视为团队事故");
        System.out.println();
    }

    static void artifactImmutability() {
        System.out.println("【制品不可变：测的就是发的】");
        Map<String, String> correct = Map.of(
                "PR 流水线", "merge commit 构建 -> 测试通过 -> 记录制品 sha256",
                "main 流水线", "引用 PR 验证过的制品（不再重新构建）-> 打正式版本号 -> 晋升部署",
                "生产部署", "下载制品库中的版本，校验 sha256 一致");
        for (Map.Entry<String, String> e : correct.entrySet()) {
            System.out.printf("  %-10s %s%n", e.getKey(), e.getValue());
        }
        System.out.println("  反例：测试完重新 build 一次再部署 -> 依赖可能拉到新版，测的不是发的");
        System.out.println();
    }

    static void triggerSemantics() {
        System.out.println("【两种触发：PR 验证，main 出制品】");
        List<String> rows = List.of(
                "PR 触发：跑 编译/检查/测试，不打包不部署，验证的是「合并后 main 会是什么样」",
                "main 触发：合并后跑 打包+制品入库+部署测试环境，main 永远处于可发布状态",
                "tag 触发：打版本 tag -> 从制品库取已验证制品 -> 生产发布流程");
        for (String r : rows) {
            System.out.println("  " + r);
        }
    }

    public static void main(String[] args) {
        criticalPath();
        gateWalkthrough();
        artifactImmutability();
        triggerSemantics();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 为你的项目画出流水线阶段图（含并行与耗时），找出关键路径上最贵的阶段并给出一条优化方案。
 * 2. 复现"测的不是发的"事故：测试通过后改一行代码重新构建再部署，解释制品不可变原则如何阻断它。
 * 3. 思考题：为什么 flaky 测试会摧毁流水线的全部价值？给出 flaky 的定义、检测方法与处理流程（隔离/修复/删除的决策树）。
 * ============================================================
 */
