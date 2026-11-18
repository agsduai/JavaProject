/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：47-07
 * 课名：Serverless 函数
 * ------------------------------------------------------------
 * 知识点：
 *   - **FaaS 的交付单位是“函数”而不是“服务”**：上传代码 -> 平台负责伸缩与运维 -> 按调用计费（空闲零成本）。适合**事件驱动、突发、无状态**的工作负载：图片处理、Webhook、定时任务、轻量 API。
 *   - **冷启动是 FaaS 的第一约束**：函数实例从零拉起（容器创建 + 运行时初始化 + 连接建立），Java 冷启动秒级（比 Go/Node 慢）——**对策**：预留实例（保底并发）、快照恢复（Firecracker snapshot）、GraalVM native-image（Java 的主流答案，启动毫秒级）。
 *   - **无状态是硬约束**：本地内存/文件不持久（实例随时回收）——**状态外置**到 Redis/DB/对象存储；连接池语义变化（不能常驻长连接，用短连或代理）。44-05 的本地模型不适合 FaaS（大模型常驻）——**有状态与常驻组件不适合 FaaS**。
 *   - **事件模型与幂等**：FaaS 常由消息/定时触发——**至少一次投递**（28 章语义），函数必须幂等；重试与死信由平台管，业务要配置。
 *   - **可观测性外包给平台**：平台日志/指标开箱即用（42 章的简化版），但**自定义指标与 trace 要主动埋**；冷启动率、并发上限、错误率是 FaaS 特有指标。
 *   - **与 K8s 服务的关系**：核心常驻服务（有状态、长连接、稳定 QPS）留在 K8s；**边缘场景（突发/事件/低频）用 FaaS**——混合架构是常态，不是二选一。
 *   - 易错点：把核心交易链路放 FaaS（冷启动抖动不可接受）；函数里建常驻连接池（实例回收泄漏）；忽略至少一次语义（重复处理无幂等）；供应商锁定无迁移预案（平台私有 API 大面积使用）。
 * ============================================================
 */

package com.heima.ch47;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class ServerlessFn {
    static void coldStartMath() {
        System.out.println("【冷启动分解：为什么 Java 慢】");
        record Phase(String phase, long jvmMs, long nativeMs) {
        }
        List<Phase> phases = List.of(
                new Phase("容器/沙箱拉起", 300, 120),
                new Phase("运行时初始化", 1200, 80),
                new Phase("框架/Spring 上下文", 2500, 150),
                new Phase("首请求 JIT 预热", 800, 20));
        long jvm = phases.stream().mapToLong(Phase::jvmMs).sum();
        long nat = phases.stream().mapToLong(Phase::nativeMs).sum();
        for (Phase p : phases) {
            System.out.printf("  %-18s JVM=%-6d Native=%d%n", p.phase(), p.jvmMs(), p.nativeMs());
        }
        System.out.printf("  合计：JVM %dms vs GraalVM native %dms%n", jvm, nat);
        System.out.println("  对策链：native-image -> 预留实例 -> 快照恢复 -> 缩小依赖");
        System.out.println();
    }

    static void statelessRules() {
        System.out.println("【无状态硬约束的状态外置】");
        String[][] rows = {
                {"会话", "Redis/JWT 无会话", "本地内存不可依赖"},
                {"计数/幂等键", "Redis SETNX + TTL", "函数实例随时回收"},
                {"大文件", "对象存储直传", "本地磁盘临时且小"},
                {"常驻连接", "短连/HTTP 代理", "池化被回收打断"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-10s %-24s %s%n", r[0], r[1], r[2]);
        }
        System.out.println("  判据：这个函数回收重建后，行为是否完全一致？");
        System.out.println();
    }

    static void eventSemantics() {
        System.out.println("【事件投递：至少一次 + 平台重试】");
        List<String> flow = List.of(
                "触发：消息队列/定时器/HTTP",
                "投递失败/超时 -> 平台按策略重试（指数退避）",
                "多次失败 -> 死信队列（28 章语义在 FaaS 的复用）",
                "业务函数必须幂等（28/43 章原则）：按事件 ID 去重",
                "定时器注意：错过触发点的补偿语义要确认（错过的 cron 是否补跑）");
        for (String s : flow) {
            System.out.println("  " + s);
        }
        System.out.println();
    }

    static void fitMatrix() {
        System.out.println("【哪些负载适合 FaaS】");
        Map<String, String> fit = new LinkedHashMap<>();
        fit.put("适合", "图片/文件处理、Webhook、低频定时任务、突发活动页");
        fit.put("不适合", "核心交易链路、长连接服务、大模型推理（常驻 GPU）、高 QPS 稳态服务");
        fit.put("混合策略", "K8s 常驻核心 + FaaS 处理边缘事件，事件总线衔接");
        for (Map.Entry<String, String> e : fit.entrySet()) {
            System.out.printf("  %-6s %s%n", e.getKey(), e.getValue());
        }
    }

    public static void main(String[] args) {
        coldStartMath();
        statelessRules();
        eventSemantics();
        fitMatrix();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 把一个低频定时任务迁移到 FaaS（或模拟），记录冷启动耗时与成本对比（常驻 vs 按调用）。
 * 2. 用 GraalVM native-image（或模拟数据）改造一个 Spring 函数，量化启动时间改善，并评估 AOT 对反射/动态代理的限制。
 * 3. 思考题：为什么“核心交易链路不适合 FaaS”？从冷启动 P99、长连接、可观测深度三个维度分析。
 * ============================================================
 */
