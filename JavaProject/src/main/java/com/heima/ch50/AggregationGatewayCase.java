/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：50-11
 * 课名：综合案例：聚合网关
 * ------------------------------------------------------------
 * 知识点：
 *   - **把本章串成一个经典场景**：BFF/聚合网关——一个用户请求需要并行调 4~6 个下游（商品/价格/库存/营销/评论/推荐），合并后返回。**IO 密集 + 高扇出 + 严格超时**——并发模型的试金石（50-01 的决策原点）。
 *   - **虚拟线程版实现**：每下游一个虚拟线程 + Semaphore 限每个下游并发 + 总超时取消 + 部分失败降级（超时下游返回占位数据而不是整体失败）——**“部分可用优于全失败”的聚合语义**（44-08 降级阶梯的网关版）。
 *   - **超时预算分配**：网关总预算 800ms -> 各下游按重要性分配（商品 400ms/评论 200ms 可失败/推荐 150ms 可失败）——44-08 预算链的网关落地；先完成的先返回（流式聚合可选）。
 *   - **可观测**：每下游耗时打点 + 失败率分下游 + 队列深度（42 章：聚合网关的 SLI 天然按下游维度展开）；取消传播（用户断开 -> 全部下游取消，48-07 同款）。
 *   - **压测验收**：对比三种实现的吞吐/P99——串行（基线）、平台线程池（池大小受限）、虚拟线程（接近理想）——**在真实下游延迟分布下跑**（50-09 实验纪律）。
 *   - 易错点：聚合失败语义一刀切（一个下游挂全页面挂）；超时预算不分级（全部 800ms，慢下游拖死）；无并发保护（大促下游被打爆）；缓存缺失（热点聚合结果缓存，46-10 数据 API 同款）。
 * ============================================================
 */

package com.heima.ch50;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

// ==================== 示例代码 ====================

public class AggregationGatewayCase {
    record Downstream(String name, int costMs, boolean critical, int maxConcurrency) {
    }

    static final List<Downstream> DOWNSTREAMS = List.of(
            new Downstream("product", 150, true, 20),
            new Downstream("price", 100, true, 20),
            new Downstream("stock", 120, true, 15),
            new Downstream("reviews", 200, false, 10),
            new Downstream("recommend", 250, false, 10));

    static String call(String name, int costMs) {
        try {
            Thread.sleep(costMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return name + "-data";
    }

    static void aggregate(String requestId) throws Exception {
        System.out.println("---- 请求 " + requestId + " ----");
        long start = System.currentTimeMillis();
        Map<String, String> merged = new LinkedHashMap<>();
        List<String> degraded = new ArrayList<>();
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> futures = new ArrayList<>();
            for (Downstream d : DOWNSTREAMS) {
                Semaphore permit = new Semaphore(d.maxConcurrency());
                int budget = d.critical() ? 400 : 200;
                futures.add(pool.submit(() -> {
                    try {
                        permit.acquire();
                        try {
                            String data = call(d.name(), d.costMs());
                            merged.put(d.name(), data);
                        } finally {
                            permit.release();
                        }
                    } catch (Exception e) {
                        if (d.critical()) {
                            merged.put(d.name(), "error");
                        } else {
                            merged.put(d.name(), "placeholder");
                            degraded.add(d.name());
                        }
                    }
                    return null;
                }));
            }
            for (Future<?> f : futures) {
                f.get(450, TimeUnit.MILLISECONDS);
            }
        }
        long cost = System.currentTimeMillis() - start;
        System.out.printf("  合并结果：%s%n", merged);
        System.out.printf("  降级项：%s  总耗时 %dms（预算 800ms）%n", degraded, cost);
        System.out.println("  语义：关键下游 400ms 预算，非关键 200ms 超时占位 —— 部分可用优于全失败");
        System.out.println();
    }

    static void budgetTable() {
        System.out.println("【超时预算分配表】");
        String[][] rows = {
                {"product/price/stock", "400ms（关键）", "超时 -> error 标记，页面骨架可用"},
                {"reviews/recommend", "200ms（非关键）", "超时 -> 占位数据，不阻塞整体"},
                {"网关总预算", "800ms", "44-08 预算链：上游知道下游最坏值"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-18s %-16s %s%n", r[0], r[1], r[2]);
        }
        System.out.println();
    }

    static void ops() {
        System.out.println("【聚合网关运营清单】");
        List<String> items = List.of(
                "按下游打点：耗时/失败率/超时率（42 章 SLI 按维度展开）",
                "取消传播：用户断开 -> 全部下游取消（48-07）",
                "热点聚合缓存：60s 短缓存挡重复请求（46-10）",
                "下游并发保护：每下游 Semaphore 独立配置",
                "压测验收：串行/池/虚拟线程三版对比（50-09 纪律）",
                "降级演练：单下游故障的页面可用性验证（47-10）");
        for (String i : items) {
            System.out.println("  [ ] " + i);
        }
        System.out.println("  本章收官：并发模型 → 实战场景 → 运营闭环，与 40-04/48-07 的流式与取消贯通");
    }

    public static void main(String[] args) throws Exception {
        aggregate("req-1001");
        aggregate("req-1002（大促高峰，模拟下游抖动）");
        budgetTable();
        ops();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现聚合网关完整版：含每下游独立 Semaphore、超时预算、降级占位与取消传播，压测对比串行/池/虚拟线程三版。
 * 2. 设计聚合结果的短缓存策略（键设计、TTL、失效时机），量化命中率对下游 QPS 的削减。
 * 3. 思考题：为什么聚合网关是“并发模型的试金石”？从 IO 密集度、扇出、超时预算三个维度综合评估三种模型的表现差异。
 * ============================================================
 */
