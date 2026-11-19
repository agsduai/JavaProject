/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：69-10
 * 课名：实时竞价链路（RTB）
 * ------------------------------------------------------------
 * 知识点：
 *   - **RTB（Real-Time Bidding）是最高强度的广告链路**：用户打开页面 -> 媒体发出竞价请求（带用户/上下文）-> 多个 DSP 在 **100ms 内**返回出价 -> 价高者得 -> 展示广告 -> 计费——**整个链路包含网络往返、召回、预估、竞价、渲染，必须在 100ms 内完成**。
 *   - **延迟预算的分配**（44-08 的极端版）：网络 30ms（媒体<->平台）+ 召回 20ms + 预估 20ms + 竞价 5ms + 渲染 10ms + 缓冲 15ms——**每一段都要有超时降级**（超时即不出价）。
 *   - **超时即放弃的策略**：广告竞价"错过就没有"——**宁可不出价也不能超时**（超时会拖慢用户页面渲染，伤害媒体体验）；**降级优先级**：精排超时 -> 用粗排分；预估超时 -> 用类目均值。
 *   - **与实时链路的关系**：RTB 是**同步链路**（请求-响应），与 43 章的异步流处理不同——**但特征更新、曝光回流仍走异步**（Kafka/流处理）。
 *   - **工程要点**：**全内存计算**（无 DB 查询）、**预估模型轻量化**（小模型/预计算向量）、**连接池复用**、**无 GC 停顿**（30 章调优，低延迟场景 G1/ZGC）。
 *   - 易错点：链路中查数据库（必超时）；无超时降级（拖垮媒体）；预估模型太重（GPU 推理调不起）；日志同步写（IO 阻塞）。
 * ============================================================
 */

package com.heima.ch69;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class RtbPipeline {
    record Stage(String name, long budgetMs, long costMs) {
        boolean overBudget() {
            return costMs > budgetMs;
        }
    }

    record BidResult(String stage, String action, long elapsedMs, boolean degraded) {
    }

    /** 分阶段执行 + 超时降级 */
    static List<BidResult> execute(List<Stage> stages, long totalBudgetMs) {
        List<BidResult> results = new ArrayList<>();
        long elapsed = 0;
        boolean anyTimeout = false;
        for (Stage s : stages) {
            long remain = totalBudgetMs - elapsed;
            if (remain <= 0) {
                results.add(new BidResult(s.name(), "SKIP（总预算耗尽）", elapsed, true));
                anyTimeout = true;
                continue;
            }
            if (s.overBudget() || s.costMs() > remain) {
                results.add(new BidResult(s.name(),
                        "降级（超时 " + s.costMs() + "ms>" + Math.min(s.budgetMs(), remain) + "ms）",
                        elapsed + Math.min(remain, s.budgetMs()), true));
                elapsed += Math.min(remain, s.budgetMs());
                anyTimeout = true;
            } else {
                results.add(new BidResult(s.name(), "正常", elapsed + s.costMs(), false));
                elapsed += s.costMs();
            }
        }
        System.out.printf("  总耗时 %dms / 预算 %dms -> %s%n", elapsed, totalBudgetMs,
                anyTimeout ? "有降级" : "全正常");
        return results;
    }

    static void normalPath() {
        System.out.println("【正常链路（预算 100ms）】");
        List<Stage> stages = List.of(
                new Stage("网络接收", 15, 8),
                new Stage("召回（内存倒排）", 20, 12),
                new Stage("pCTR 预估（小模型）", 25, 15),
                new Stage("竞价与定价", 5, 3),
                new Stage("响应组装", 10, 4));
        execute(stages, 100).forEach(r -> System.out.printf(
                "  %-18s %-28s elapsed=%dms%s%n", r.stage(), r.action(), r.elapsedMs(),
                r.degraded() ? "（降级）" : ""));
        System.out.println();
    }

    static void timeoutPath() {
        System.out.println("【预估超时：降级用类目均值】");
        List<Stage> stages = List.of(
                new Stage("网络接收", 15, 12),
                new Stage("召回", 20, 18),
                new Stage("pCTR 预估（主模型超时）", 25, 60),
                new Stage("竞价与定价", 5, 3),
                new Stage("响应组装", 10, 4));
        execute(stages, 100).forEach(r -> System.out.printf(
                "  %-22s %-32s elapsed=%dms%s%n", r.stage(), r.action(), r.elapsedMs(),
                r.degraded() ? "（降级）" : ""));
        System.out.println("  降级策略：主模型超时 -> 用类目均值 pCTR（仍能出价，不超时）");
        System.out.println();
    }

    static void engineering() {
        System.out.println("【工程约束】");
        Map<String, String> constraints = new LinkedHashMap<>();
        constraints.put("零 DB 查询", "全部走内存/本地缓存（一次 DB 往返 1~5ms，必超时）");
        constraints.put("模型轻量", "小模型/预计算向量，避免 GPU 推理调用");
        constraints.put("连接复用", "HTTP 长连接/连接池（握手成本不可忽略）");
        constraints.put("GC 控制", "G1/ZGC + 低分配（30 章调优），避免 STOP-THE-WORLD");
        constraints.put("日志异步", "同步写日志 = IO 阻塞（42-02 同款）");
        constraints.forEach((k, v) -> System.out.printf("  %-10s %s%n", k, v));
        System.out.println();
        System.out.println("  与 43 章的差异：RTB 是同步链路，错过即放弃；异步流处理可重放补算");
    }

    public static void main(String[] args) {
        normalPath();
        timeoutPath();
        engineering();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现 RTB 链路模拟：5 段各带预算与耗时，注入"预估超时"，验证降级后仍能出价且不超总预算。
 * 2. 分析各段延迟预算：给定总预算 100ms，重新分配 5 段的时间并说明依据（哪些必须快）。
 * 3. 思考题：为什么 RTB 宁可不出价也不能超时？从"媒体页面渲染阻塞"与"用户体验"分析。
 * ============================================================
 */
