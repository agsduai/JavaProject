/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：72-11
 * 课名：综合案例：把一个服务改成 native
 * ------------------------------------------------------------
 * 知识点：
 *   - **把本章收口成一次完整迁移**：**评估**（72-10 四维打分）-> **AOT 配置**（反射/资源/代理 72-03）-> **初始化调整**（72-04）-> **框架适配**（Spring Boot 3 AOT 72-05）-> **排障**（72-06）-> **性能验证**（72-07）-> **容器与流水线**（72-08/09）。
 *   - **迁移的真实产出**：启动 25s -> 0.15s、内存 600MB -> 90MB、镜像 320MB -> 85MB、配置新增 30 条（反射/资源）——**收益与成本都可量化**。
 *   - **踩坑清单（典型五条）**：反射未配置（NoSuchMethod）、资源未打进（FileNotFound）、构建期初始化读了构建机环境、动态代理未声明、JIT 相关调优参数失效。
 *   - **验收标准**：功能回归全绿（native 下跑全量集成测试）、性能达标（启动/内存/峰值都在预期内）、可回退（JVM 镜像并行）、可观测（埋点在 native 下生效）。
 *   - 与全书的关系：native 迁移把 37（容器）、41（流水线）、47（弹性）、30（性能）串成一次实践——**它是"部署形态演进"的收官**。
 *   - 易错点：只跑单测不跑集成测试；不对比峰值性能；配置不随代码演进；迁移后放弃 JVM 兜底。
 * ============================================================
 */

package com.heima.ch72;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class NativeMigrationCase {
    record Metric(String name, String before, String after, String gain) {
    }

    static void results() {
        System.out.println("【迁移结果（某后台 API 服务）】");
        List<Metric> metrics = List.of(
                new Metric("启动时间", "25.0s", "0.15s", "167 倍"),
                new Metric("常驻内存", "600MB", "90MB", "6.7 倍"),
                new Metric("镜像体积", "320MB", "85MB", "3.8 倍"),
                new Metric("峰值 QPS", "12,000", "11,000", "略降 8%"),
                new Metric("P99 延迟", "85ms", "88ms", "略升（可接受）"),
                new Metric("新增配置", "0", "32 条（反射/资源/代理）", "成本项"));
        for (Metric m : metrics) {
            System.out.printf("  %-10s %-10s -> %-24s %s%n", m.name(), m.before(), m.after(),
                    m.gain());
        }
        System.out.println();
    }

    static void pitfalls() {
        System.out.println("【踩坑清单】");
        Map<String, String> pits = new LinkedHashMap<>();
        pits.put("反射未配置", "启动报 NoSuchMethodException -> 补 reflect-config");
        pits.put("资源未打进", "getResource 返回 null -> 补 resource-config");
        pits.put("构建期初始化", "连了构建机数据库 -> 标记运行期初始化（72-04）");
        pits.put("代理未声明", "Proxy.newProxyInstance 失败 -> 补 proxy-config");
        pits.put("JIT 调优失效", "-XX:+UseG1GC 等参数无效 -> 改用 native 的 GC 选项");
        pits.forEach((k, v) -> System.out.printf("  %-12s %s%n", k, v));
        System.out.println();
    }

    static void acceptance() {
        System.out.println("【验收清单】");
        List<String> gates = new ArrayList<>(List.of(
                "功能：全量集成测试在 native 下通过（不只单测）",
                "启动：< 1s（探针可 1s 内就绪）",
                "内存：< 150MB（对比 JVM 版降 3 倍以上）",
                "峰值：QPS 降幅 < 15%（压测确认）",
                "可观测：日志/指标/trace 在 native 下正常",
                "回退：JVM 镜像并行保留，灰度可切"));
        gates.forEach(g -> System.out.println("  [ ] " + g));
        System.out.println();
    }

    static void lessons() {
        System.out.println("【经验总结】");
        List<String> lessons = List.of(
                "先做评估（72-10），不为技术而技术",
                "配置收集用 agent 跑测试（72-03），比手工靠谱",
                "构建放流水线最后（72-09），门禁失败不浪费 12 分钟",
                "灰度对比 JVM 与 native 的真实指标（41-07）",
                "保留 JVM 版本至少 1 个月，作为兜底（41-11）");
        lessons.forEach(l -> System.out.println("  - " + l));
    }

    public static void main(String[] args) {
        results();
        pitfalls();
        acceptance();
        lessons();
        System.out.println();
        System.out.println("  一句话：native 迁移是「收益明确、成本可量化、风险可回退」的工程决策");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 端到端规划一次 native 迁移（评估/配置/排障/验证/回退），输出可执行的任务清单与时间估算。
 * 2. 写出"踩坑清单"里五类问题的复现方法（每类给一个最小代码示例）与修复步骤。
 * 3. 思考题：为什么"保留 JVM 版本兜底"是迁移的前提？从"行为差异排查"与"故障回退"分析。
 * ============================================================
 */
