/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：47-03
 * 课名：流量治理与故障注入
 * ------------------------------------------------------------
 * 知识点：
 *   - **VirtualService 是流量治理的主战场**：按权重切流（金丝雀，41-07 的基础设施版）、按 header 切流（内部员工先灰）、超时/重试策略（45 章 timeout 预算的落地）、故障注入（混沌）。**所有策略都是配置，秒级生效可回滚**。
 *   - **DestinationRule 管目标侧策略**：子集定义（v1/v2 由 label 划分）、负载均衡算法、连接池大小、熔断（连续 5xx 熔断 N 秒）、TLS 模式。**VirtualService 管怎么路由，DestinationRule 管到那之后怎么办**。
 *   - **金丝雀的 Mesh 版本**：`v1: 95%, v2: 5%` 权重切流，或按 header（`x-canary: true`）定向——**比 41-07 的应用层实现更底层更精准**，且指标天然带版本维度（Sidecar 遥测）。
 *   - **故障注入做混沌实验**：注入延迟（+500ms）/ 注入错误（固定比例 503）——**主动验证依赖方的韧性**（47-10 混沌工程的原语）。注入范围可限定到特定路径与比例。
 *   - **重试与超时的网格级预算**：`timeout: 200ms, retries: 2`——**总预算 = 超时 × (1+重试)**，网关视角（44-08）必须知道下游的这个预算再定自己的；网格级统一配置避免"每个服务自己拍脑袋"。
 *   - **灰度与探针联动**：金丝雀切流前检查 v2 的就绪与错误率（42 章），自动判据（41-07）可接 Mesh 的遥测实现全自动放量。
 *   - 易错点：金丝雀权重切流但会话粘性丢失（同用户在 v1/v2 跳变，41-07 的教训）；重试预算与上游叠加（47-01 双重重试）；故障注入忘删配置（生产持续 500ms 延迟没人发现）；熔断阈值全局一刀切（不同接口容忍度不同）。
 * ============================================================
 */

package com.heima.ch47;

import java.util.ArrayList;
import java.util.List;

// ==================== 示例代码 ====================

public class MeshTrafficPolicy {
    record RouteRule(String subset, int weight, String match) {
    }

    static void canary() {
        System.out.println("【金丝雀切流：两种匹配方式】");
        List<RouteRule> weighted = List.of(
                new RouteRule("v1", 95, "默认"),
                new RouteRule("v2", 5, "默认"));
        List<RouteRule> headered = List.of(
                new RouteRule("v2", 100, "header x-canary=true（内部员工）"),
                new RouteRule("v1", 100, "其余全部流量"));
        System.out.println("  权重模式：");
        for (RouteRule r : weighted) {
            System.out.printf("    subset=%s weight=%d%%（%s）%n", r.subset(), r.weight(), r.match());
        }
        System.out.println("  Header 模式：");
        for (RouteRule r : headered) {
            System.out.printf("    subset=%s -> %s%n", r.subset(), r.match());
        }
        System.out.println("  组合拳：先 header 定向（内部）再权重放量（外部）——41-07 的完整复刻");
        System.out.println();
    }

    static void timeoutBudget() {
        System.out.println("【网格级超时与重试预算】");
        int timeoutMs = 200, retries = 2;
        int worst = timeoutMs * (1 + retries);
        System.out.printf("  策略：timeout=%dms, retries=%d -> 单调用最坏 %dms%n", timeoutMs, retries, worst);
        System.out.println("  上游网关预算必须 > 下游最坏值（44-08 预算分配）：");
        List<String> chain = List.of(
                "gateway: 3000ms 预算",
                "  -> order(VirtualService): 200ms×3 = 600ms",
                "    -> stock: 150ms×2 = 300ms");
        for (String c : chain) {
            System.out.println("  " + c);
        }
        System.out.println("  网格统一配置的价值：预算不再散落在每个服务的代码里");
        System.out.println();
    }

    static void faultInjection() {
        System.out.println("【故障注入：验证依赖方的韧性】");
        record Inject(String target, String type, String spec, String expect) {
        }
        List<Inject> injects = List.of(
                new Inject("order -> stock", "延迟", "固定 +500ms，比例 10%",
                        "调用方超时预算是否足够？熔断是否按预期打开？"),
                new Inject("order -> payment", "错误", "HTTP 503，比例 5%",
                        "降级逻辑触发？用户体验路径？"),
                new Inject("gateway -> order", "中止", "request abort，比例 1%",
                        "监控告警时间线？值班响应速度？"));
        for (Inject i : injects) {
            System.out.printf("  %-16s %-4s %-24s 验证：%s%n",
                    i.target(), i.type(), i.spec(), i.expect());
        }
        System.out.println("  注入三要素：目标 + 类型比例 + 稳态假设（47-10）；实验后立即回收");
        System.out.println();
    }

    static void circuitBreaking() {
        System.out.println("【DestinationRule 熔断：连接池与异常检测】");
        List<String> rules = List.of(
                "连接池：maxConnections=100, http1MaxPendingRequests=50（防排队雪崩）",
                "异常检测：连续 5 个 5xx -> 弹出实例 30s（逐出后探测恢复）",
                "逐出比例上限 50%（防止全部逐出导致零可用）",
                "与 SDK 熔断的差异：网格级统一，不需要每个服务实现");
        for (String r : rules) {
            System.out.println("  - " + r);
        }
        System.out.println("  观察：熔断打开期间错误类型从“超时”变“立即失败”——定位线索");
    }

    public static void main(String[] args) {
        canary();
        timeoutBudget();
        faultInjection();
        circuitBreaking();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 用 VirtualService 实现完整金丝雀（header 定向 -> 权重放量），期间用遥测验证版本维度错误率，模拟自动判据放量。
 * 2. 设计一次故障注入实验：稳态假设、注入参数、观察指标、中止条件与恢复验证，实验后写报告。
 * 3. 思考题：为什么"重试发生在 Sidecar"改善了业务线程池占用？对比 SDK 重试（占用线程）与 Sidecar 重试（代理层等待）的线程模型差异。
 * ============================================================
 */
