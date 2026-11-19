/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：57-10
 * 课名：现代 Java 的迁移策略与兼容性
 * ------------------------------------------------------------
 * 知识点：
 *   - **迁移是工程不是语法**：目标版本确定（57-01 LTS 路线）-> 依赖扫描（`mvn dependency:tree` + `jdeprscan`）-> 编译目标调整（`maven.compiler.release`）-> 废弃 API 修复 -> 运行时参数核对（GC/容器感知）-> 灰度验证（41-07）。
 *   - **`--release` 比 `-source/-target` 更安全**：`--release 21` 同时约束语法与**API 可见性**（防止误用高版本才有的 API 却声称兼容低版本）——**构建配置必须写 release**。
 *   - **三条渐进路线**：① **先升级 JDK 不动代码**（最低风险，验证运行时兼容）；② **再启用新特性**（record/sealed 只在新增代码用，老代码不动）；③ **最后回头重构**（49-10 演进纪律，按痛点而非按版本）。**反模式：一次升级+全量重构**。
 *   - **字节码增强类库是最敏感的依赖**：Lombok、Mockito、AspectJ、CGLIB 都依赖内部 API——**升级前先确认其支持目标 JDK**（53-08 SkyWalking Agent 的同款问题）；Spring Boot 3.x 要求 JDK 17+ 也是这个原因。
 *   - **废弃与移除的清单**：`SecurityManager`（21 永久禁用）、`finalize`（标记废弃）、`Thread.stop/suspend`（早已废弃）、若干 `sun.misc`——**升级时先跑 `jdeprscan` 拿到全量清单**，逐条决定"替换/屏蔽/接受"。
 *   - 易错点：只改 pom 的 java.version 不查运行时（升级后依赖崩）；直接用预览特性（下次升级即失败）；一步到位重构（回滚困难）；不评估字节码库（Lombok 版本旧导致编译失败）。
 * ============================================================
 */

package com.heima.ch57;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class MigrationPlan {
    record Step(int order, String action, String risk, String rollback) {
    }

    static void roadmap() {
        System.out.println("【四步迁移路线（每步可独立回退）】");
        List<Step> steps = List.of(
                new Step(1, "升级 JDK 运行时，代码零改动", "低：运行时差异", "回退 JDK 版本"),
                new Step(2, "编译目标改 --release 21，修废弃 API", "中：编译错误集中暴露", "逐个提交回退"),
                new Step(3, "新代码启用 record/sealed/模式匹配", "低：增量引入", "新代码可单独回退"),
                new Step(4, "按痛点重构老代码（49-10）", "中：行为变更风险", "保留黄金样本回归"));
        for (Step s : steps) {
            System.out.printf("  第%d步 %-34s 风险=%-16s 回退=%s%n",
                    s.order(), s.action(), s.risk(), s.rollback());
        }
        System.out.println();
    }

    static void dependencyCheck() {
        System.out.println("【升级前依赖扫描清单】");
        Map<String, String> checks = new LinkedHashMap<>();
        checks.put("字节码增强库", "Lombok/Mockito/AspectJ/CGLIB：确认支持目标 JDK");
        checks.put("框架版本", "Spring Boot 3.x 要求 JDK 17+，3.2+ 支持 21");
        checks.put("废弃 API", "jdeprscan --release 21 全量清单");
        checks.put("GC/容器参数", "G1 默认化；容器内存感知已内置（37-08）");
        checks.put("预览特性", "生产代码禁用（--enable-preview 不承诺）");
        checks.forEach((k, v) -> System.out.printf("  %-10s %s%n", k, v));
        System.out.println();
    }

    public static void main(String[] args) {
        roadmap();
        dependencyCheck();
        System.out.println("  验证标准：四步全部走完后，回归测试通过 + 压测指标不劣化（41 章门禁）");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 为一个真实项目制定四步迁移计划（含依赖扫描结果、每步验证标准与回退方式），输出可评审文档。
 * 2. 用 `jdeprscan`（或等价工具）扫描你的依赖，列出废弃 API 清单并分类（替换/接受/屏蔽）。
 * 3. 思考题：为什么"先升级运行时再重构代码"比"一次做完"更安全？从故障归因（两件事混在一起）与回滚粒度分析。
 * ============================================================
 */
