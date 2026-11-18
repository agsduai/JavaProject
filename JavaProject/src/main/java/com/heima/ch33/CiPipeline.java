/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：33-10
 * 课名：CI 流水线与质量门禁
 * ------------------------------------------------------------
 * 知识点：
 *   - **CI（持续集成）** 的核心不是"自动构建"，而是"**每次提交都在 10 分钟内给出可信的通过/失败结论**"。慢的流水线等于没有流水线。
 *   - 标准阶段：**检出 → 编译 → 静态检查 → 单元测试 → 集成测试 → 打包 → 制品归档 → 通知**。前面的阶段失败就立刻终止（fail fast），不要跑完所有阶段才发现编译不过。
 *   - **质量门禁**是流水线里的硬闸门：编译 0 error、静态检查 0 ERROR、单测全绿、新增代码覆盖率 ≥ 阈值、无高危依赖漏洞。任一不满足即阻断合并。
 *   - **流水线要可复现**：固定 JDK 版本、固定依赖版本（`pom.xml` 锁版本）、固定随机种子、隔离外部依赖。本地能过、CI 不能过，多半是环境或时序差异。
 *   - **构建缓存**：依赖缓存（`~/.m2`）、编译缓存能显著缩短时间。但缓存 key 要包含依赖文件哈希，否则会用到过期缓存。
 *   - **并行化**：测试按模块/包拆分并行跑；静态检查与单元测试可并行。注意并行时数据隔离（数据库、端口、临时目录）。
 *   - **失败信息要可行动**：报告要给出"哪个文件哪一行、期望什么、实际什么、怎么复现"。只输出 `BUILD FAILED` 等于没给信息。
 *   - **制品（artifact）**：构建产物带版本号与提交哈希，可追溯到具体 commit。不要用 `latest` 这种可变标签。
 *   - **流水线的可观测性**：每阶段耗时、失败率、平均修复时间。发现"测试阶段从 2 分钟涨到 8 分钟"就该处理，别等它变成 30 分钟。
 *   - 易错：CI 里跑 `mvn test` 但跳过失败（`-Dmaven.test.failure.ignore=true`）；测试依赖本地数据库；覆盖率阈值设在总覆盖率而不是新增代码（老代码拖累永远不达标）；流水线里 `sleep 30` 等部署。
 * ============================================================
 */

package com.heima.ch33;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class CiPipeline {
    enum Status {
        PASS, FAIL, SKIP
    }

    /** 阶段结果 */
    static class StageResult {
        final String name;
        final Status status;
        final long costMs;
        final String detail;

        StageResult(String name, Status status, long costMs, String detail) {
            this.name = name;
            this.status = status;
            this.costMs = costMs;
            this.detail = detail;
        }
    }

    /** 一个阶段 */
    interface Stage {
        StageResult run(Context ctx) throws Exception;
    }

    /** 流水线上下文：阶段间共享的"工作区" */
    static class Context {
        final Map<String, Object> data = new LinkedHashMap<>();
        final List<StageResult> results = new ArrayList<>();
        final List<String> artifacts = new ArrayList<>();
        boolean blocked;
        String blockReason;

        void block(String reason) {
            this.blocked = true;
            this.blockReason = reason;
        }
    }

    /** 质量门禁 */
    static class QualityGate {
        int minCoveragePercent = 70;
        int maxCyclomatic = 10;
        int maxStaticErrors = 0;
        boolean allowWarnings = true;

        String evaluate(Context ctx) {
            int errors = (int) ctx.data.getOrDefault("staticErrors", 0);
            int coverage = (int) ctx.data.getOrDefault("coverage", 0);
            int cc = (int) ctx.data.getOrDefault("maxCyclomatic", 0);
            int failedTests = (int) ctx.data.getOrDefault("failedTests", 0);

            List<String> reasons = new ArrayList<>();
            if (failedTests > 0) {
                reasons.add("单元测试失败 " + failedTests + " 个");
            }
            if (errors > maxStaticErrors) {
                reasons.add("静态检查 ERROR=" + errors + " 超过阈值 " + maxStaticErrors);
            }
            if (coverage < minCoveragePercent) {
                reasons.add("覆盖率 " + coverage + "% 低于阈值 " + minCoveragePercent + "%");
            }
            if (cc > maxCyclomatic) {
                reasons.add("最大圈复杂度 " + cc + " 超过阈值 " + maxCyclomatic);
            }
            return reasons.isEmpty() ? null : String.join("；", reasons);
        }
    }

    /** 流水线执行器 */
    static class Pipeline {
        final List<Stage> stages = new ArrayList<>();
        final QualityGate gate = new QualityGate();

        Pipeline add(Stage s) {
            stages.add(s);
            return this;
        }

        void run(String title) {
            System.out.println("=== " + title + " ===");
            Context ctx = new Context();
            long totalStart = System.nanoTime();
            for (Stage stage : stages) {
                String name = stage.getClass().getSimpleName();
                try {
                    StageResult r = stage.run(ctx);
                    ctx.results.add(r);
                    String mark = r.status == Status.PASS ? "PASS" : r.status == Status.FAIL ? "FAIL" : "SKIP";
                    System.out.printf("  [%-4s] %-22s %5d ms  %s%n",
                            mark, r.name, r.costMs, r.detail);
                    if (r.status == Status.FAIL) {
                        ctx.block("阶段 " + r.name + " 失败: " + r.detail);
                        break;
                    }
                } catch (Exception e) {
                    ctx.block("阶段异常: " + e.getMessage());
                    System.out.printf("  [FAIL] %-22s 异常 %s%n", name, e.getMessage());
                    break;
                }
            }
            long totalMs = (System.nanoTime() - totalStart) / 1_000_000;

            System.out.println();
            if (ctx.blocked) {
                System.out.println("  流水线结果: BLOCKED");
                System.out.println("  原因: " + ctx.blockReason);
            } else {
                String gateFail = gate.evaluate(ctx);
                if (gateFail != null) {
                    System.out.println("  流水线结果: BLOCKED（质量门禁）");
                    System.out.println("  原因: " + gateFail);
                } else {
                    System.out.println("  流水线结果: PASS");
                    System.out.println("  制品: " + ctx.artifacts);
                }
            }
            System.out.println("  总耗时: " + totalMs + " ms（真实流水线要求 10 分钟内）");
            System.out.println();
        }
    }

    /** ---------- 具体阶段 ---------- */
    static class CheckoutStage implements Stage {
        public StageResult run(Context ctx) {
            ctx.data.put("commit", "a1b2c3d");
            ctx.data.put("branch", "feature/order");
            return new StageResult("检出代码", Status.PASS, 120,
                    "commit=a1b2c3d branch=feature/order");
        }
    }

    static class CompileStage implements Stage {
        private final boolean broken;

        CompileStage(boolean broken) {
            this.broken = broken;
        }

        public StageResult run(Context ctx) {
            if (broken) {
                return new StageResult("编译", Status.FAIL, 800,
                        "OrderService.java:42 找不到符号 total");
            }
            return new StageResult("编译", Status.PASS, 800, "23 个源文件编译通过");
        }
    }

    static class StaticCheckStage implements Stage {
        private final int errors;
        private final int warns;

        StaticCheckStage(int errors, int warns) {
            this.errors = errors;
            this.warns = warns;
        }

        public StageResult run(Context ctx) {
            ctx.data.put("staticErrors", errors);
            return new StageResult("静态检查", Status.PASS, 400,
                    "ERROR=" + errors + " WARN=" + warns);
        }
    }

    static class UnitTestStage implements Stage {
        private final int total;
        private final int failed;
        private final int coverage;

        UnitTestStage(int total, int failed, int coverage) {
            this.total = total;
            this.failed = failed;
            this.coverage = coverage;
        }

        public StageResult run(Context ctx) {
            ctx.data.put("failedTests", failed);
            ctx.data.put("coverage", coverage);
            ctx.data.put("maxCyclomatic", 8);
            if (failed > 0) {
                return new StageResult("单元测试", Status.FAIL, 1500,
                        total + " 个用例，" + failed + " 个失败（DiscountServiceTest）");
            }
            return new StageResult("单元测试", Status.PASS, 1500,
                    total + " 个用例全通过，覆盖率 " + coverage + "%");
        }
    }

    static class PackageStage implements Stage {
        public StageResult run(Context ctx) {
            String version = "1.0.0-" + ctx.data.get("commit");
            ctx.artifacts.add("order-service-" + version + ".jar");
            return new StageResult("打包", Status.PASS, 1200, "产物 order-service-" + version + ".jar");
        }
    }

    static class NotifyStage implements Stage {
        public StageResult run(Context ctx) {
            return new StageResult("通知", Status.PASS, 50, "已推送结果到群聊");
        }
    }

    static Pipeline standardPipeline(boolean compileBroken, int staticErrors,
                                     int tests, int failed, int coverage) {
        return new Pipeline()
                .add(new CheckoutStage())
                .add(new CompileStage(compileBroken))
                .add(new StaticCheckStage(staticErrors, 3))
                .add(new UnitTestStage(tests, failed, coverage))
                .add(new PackageStage())
                .add(new NotifyStage());
    }

    public static void main(String[] args) {
        standardPipeline(false, 0, 42, 0, 85).run("场景 1：一切正常");

        standardPipeline(true, 0, 42, 0, 85).run("场景 2：编译失败（fail fast，后续阶段不跑）");

        standardPipeline(false, 2, 42, 0, 85).run("场景 3：静态检查有 ERROR");

        standardPipeline(false, 0, 42, 3, 85).run("场景 4：单元测试有失败");

        standardPipeline(false, 0, 42, 0, 55).run("场景 5：覆盖率不达标");

        System.out.println("=== 阶段耗时分布（优化依据）===");
        String[][] cost = {
                {"单元测试", "1500", "并行拆分、去掉 Spring 容器"},
                {"打包", "1200", "增量编译、缓存 ~/.m2"},
                {"编译", "800", "只编译变更模块"},
                {"静态检查", "400", "只扫变更文件（增量模式）"},
                {"检出代码", "120", "浅克隆 + 缓存"}
        };
        System.out.printf("  %-12s %-10s %s%n", "阶段", "耗时ms", "优化手段");
        for (String[] r : cost) {
            System.out.printf("  %-12s %-10s %s%n", r[0], r[1], r[2]);
        }

        System.out.println();
        System.out.println("=== 门禁阈值建议 ===");
        String[][] gate = {
                {"编译", "0 error", "硬阻断"},
                {"静态检查", "0 ERROR", "硬阻断"},
                {"单元测试", "0 失败", "硬阻断"},
                {"新增代码覆盖率", ">= 80%", "硬阻断"},
                {"整体覆盖率", ">= 70%", "提示，不阻断"},
                {"最大圈复杂度", "<= 10", "只对新增文件阻断"},
                {"高危依赖漏洞", "0", "硬阻断"}
        };
        System.out.printf("  %-18s %-12s %s%n", "指标", "阈值", "级别");
        for (String[] r : gate) {
            System.out.printf("  %-18s %-12s %s%n", r[0], r[1], r[2]);
        }

        System.out.println();
        System.out.println("=== 流水线常见坑 ===");
        String[] pits = {
                "用 -Dmaven.test.failure.ignore=true 让失败测试不阻断",
                "测试连本地数据库，CI 上必然红",
                "覆盖率门槛设在总量，老代码拖累永远不达标",
                "用 sleep 等部署完成，改成健康检查轮询",
                "缓存 key 不含 pom 哈希，用到过期依赖",
                "只输出 BUILD FAILED，不告诉人哪一行错了",
                "流水线 40 分钟，开发者改成攒一天再提交"
        };
        for (String p : pits) {
            System.out.println("  - " + p);
        }
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 给流水线加一个"依赖漏洞扫描"阶段，输出高危依赖数，并把"高危 > 0"设为门禁阻断项。
 * 2. 把 `UnitTestStage` 改造成并行执行（两个测试分片各跑一半），并保证总耗时减半、结果合并正确。
 * 3. 设计一条"只对新增代码生效"的覆盖率门禁规则，说明为什么它比"整体覆盖率"更容易落地。
 * ============================================================
 */
