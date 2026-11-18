/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：48-08
 * 课名：高可用与多副本路由
 * ------------------------------------------------------------
 * 知识点：
 *   - **推理集群的高可用形态**：多副本无状态（模型权重相同）-> 任意副本可服务；**有状态的例外**：KV 会话亲和（48-02 多轮复用）——亲和路由与负载均衡的矛盾要用“亲和 TTL + 超载时降级重算”平衡。
 *   - **路由权重与真实容量对齐**：48-06 的各池容量不同 -> 网关按“剩余槽位/水位”加权（least-outstanding），**而不是轮询**——热副本不排队、冷副本不闲置（42 章负载均衡思想的推理版）。
 *   - **故障语义**：副本 OOM/挂掉 -> 网关摘除 + 在途请求重试到其他副本（**幂等前提**：流式请求中断后重试要客户端可接受——40-04 断线策略）；引擎错误分类决定是否重试（402 上下文超限不可重试）。
 *   - **优先级与抢占**：VIP/内部诊断请求可插队；过载时低优先级请求被抢占（48-04 引擎抢占）或网关直接拒绝（降级阶梯 44-10 的 L4）——**优先级队列是过载时的秩序**。
 *   - **发布与回滚**：新引擎版本/新量化权重走灰度（48-05 验证门禁后）——**金丝雀副本**承接 5% 流量，指标对比后全量（41-07 思想的推理版）。
 *   - **跨可用区部署**：模型权重多副本分属 AZ（GPU 供给与故障域），路由感知 AZ 延迟——**GPU 配额的跨区规划**（47-09 多集群的数据层思想在 GPU 池的应用）。
 *   - 易错点：轮询路由无视水位（热副本排队爆炸）；会话亲和无限 TTL（KV 占满单副本）；OOM 副本未摘除（连续失败 5 分钟才摘）；灰度只比延迟不比质量（加速配置的质量回退上线）。
 * ============================================================
 */

package com.heima.ch48;

import java.util.ArrayList;
import java.util.List;

// ==================== 示例代码 ====================

public class ServingHA {
    record Replica(String name, int maxStreams, int inflight, double blockPct, boolean healthy) {
        double load() {
            return (double) inflight / maxStreams;
        }
    }

    static void weightedRouting() {
        System.out.println("【按水位加权：least-outstanding 路由】");
        List<Replica> replicas = List.of(
                new Replica("gpu-1", 32, 28, 80, true),
                new Replica("gpu-2", 32, 12, 45, true),
                new Replica("gpu-3", 32, 30, 88, true));
        for (Replica r : replicas) {
            System.out.printf("  %-6s 在途 %2d/%2d（%.0f%%） block池 %.0f%%%n",
                    r.name(), r.inflight(), r.maxStreams(), r.load() * 100, r.blockPct());
        }
        Replica best = replicas.stream()
                .filter(r -> r.healthy && r.blockPct < 85)
                .min((a, b) -> Double.compare(a.load(), b.load())).orElse(null);
        System.out.printf("  新请求 -> %s（最低水位且 block 池健康）%n", best.name());
        System.out.println("  对照轮询：轮到 gpu-3（88% 水位）-> 排队 + 抢占风险");
        System.out.println();
    }

    static void affinityVsBalance() {
        System.out.println("【会话亲和与负载均衡的平衡】");
        List<String> rules = List.of(
                "亲和 TTL：会话 KV 保留 10 分钟（TTL 内粘同副本）",
                "超载降级：亲和副本水位 > 90% -> 放弃亲和，换副本重算（体验优先）",
                "KV 优雅淘汰：TTL 到期回收（LRU），防单副本 KV 占满",
                "指标：affinity_hit_rate（低于 80% 说明容量不足或 TTL 不合理）");
        for (String r : rules) {
            System.out.println("  - " + r);
        }
        System.out.println();
    }

    static void failureSemantics() {
        System.out.println("【副本故障的分级处理】");
        String[][] rows = {
                {"网络抖动/单请求 5xx", "重试其他副本（流式请求按断线策略）", "自动，秒级"},
                {"副本 OOM 反复", "摘除副本 + 告警 + 触发重建", "分钟级"},
                {"模型加载失败（发布期）", "金丝雀副本隔离，全量留在旧版本", "发布门禁拦截"},
                {"引擎全局错误（配置坏）", "降级路由切备用池（44-08 L2）", "自动+告警"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-20s %-34s %s%n", r[0], r[1], r[2]);
        }
        System.out.println("  幂等前提：流式中断重试要客户端可接受（40-04 断线策略声明）");
        System.out.println();
    }

    static void canaryReplica() {
        System.out.println("【新引擎/新权重的金丝雀发布】");
        List<String> steps = List.of(
                "1) 新版本独立副本（不接流量），离线评测门禁（48-05）",
                "2) 切 5% 流量：TTFT/TPOT/质量抽检对比基线",
                "3) 指标达标 -> 25% -> 100%（41-07 放量节奏）",
                "4) 异常 -> 副本级回滚（秒级，权重归零）",
                "5) 全量后旧版本保留 24h 作回滚池");
        for (String s : steps) {
            System.out.println("  " + s);
        }
        System.out.println("  与 41 章发布工程的唯一差异：验证指标多了 TTFT/TPOT 与质量抽检");
    }

    public static void main(String[] args) {
        weightedRouting();
        affinityVsBalance();
        failureSemantics();
        canaryReplica();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现水位加权路由器（模拟副本与请求流），对比轮询与 least-outstanding 的 P99 与排队深度。
 * 2. 设计会话亲和策略（TTL/超载降级/淘汰），构造单副本过载场景验证降级路径。
 * 3. 思考题：为什么推理金丝雀的验证指标必须包含“质量抽检”而普通服务不用？从加速配置对输出质量的影响（48-05）推导。
 * ============================================================
 */
