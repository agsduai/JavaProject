/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：50-08
 * 课名：CompletableFuture 与两者整合
 * ------------------------------------------------------------
 * 知识点：
 *   - **CompletableFuture 是“回调与组合”的中坚**：JDK 8 引入，_supplyAsync/thenApply/thenCombine/allOf 提供组合能力——**它是响应式思想在 JDK 内的最早形态**，也是虚拟线程前“并发编排”的事实标准（32 章 JUC 的编排层）。
 *   - **CF 的位置在三者地图上**：CF（回调组合，JDK 内置）< Reactor（完整响应式流 + 背压）< 虚拟线程（同步语义）——**CF 依然是默认可用项**：不引框架、不用新 JDK 特性、语义够用的编排场景用它。
 *   - **CF 与虚拟线程的配合**：CF 的默认执行器在 JDK 21 之后是虚拟线程执行器（`CompletableFuture` 静态方法的 async 变体走虚拟线程）——**回调写法 + 虚拟线程执行**融合；`orTimeout` 提供超时（48-07 预算思想的 JDK 原生）。
 *   - **CF 的坑**：默认池是 ForkJoinPool.commonPool（CPU 密集导向，IO 任务会饿死其他并行流）——**IO 任务显式传 executor**；异常处理链遗漏（exceptionally/handle 缺失 -> 异常静默吞掉）；thenApply 与 thenApplyAsync 的线程语义混淆。
 *   - **迁移视角**：存量 CF 代码无需重写——虚拟线程时代 CF 仍是有效编排工具；**新代码的简单场景用虚拟线程同步写，复杂组合（扇入/竞速）用 CF**——按表达式复杂度选。
 *   - 易错点：commonPool 跑阻塞 IO（全局并行流与 CF 一起挨饿）；allOf 后不 collect 结果（只等完成不取值）；超时缺失（orTimeout 不用，请求悬挂）；异常链断裂（上一阶段异常未处理，下一阶段不执行也无提示）。
 * ============================================================
 */

package com.heima.ch50;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

// ==================== 示例代码 ====================

public class CfIntegration {
    static final ExecutorService VIRTUAL = Executors.newVirtualThreadPerTaskExecutor();

    static CompletableFuture<String> call(String name, long ms) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Thread.sleep(ms);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return name + "-ok";
        }, VIRTUAL);
    }

    static void combination() throws Exception {
        System.out.println("【CF 组合：扇入合并 + 竞速】");
        long start = System.currentTimeMillis();
        CompletableFuture<String> all = call("stock", 120)
                .thenCombine(call("price", 80), (s, p) -> s + "+" + p);
        System.out.println("  thenCombine 合并：" + all.get(1, TimeUnit.SECONDS)
                + "（耗时 " + (System.currentTimeMillis() - start) + "ms）");
        CompletableFuture<String> race = call("fast-source", 50)
                .applyToEither(call("slow-source", 300), s -> s);
        System.out.println("  applyToEither 竞速：" + race.get(1, TimeUnit.SECONDS));
        System.out.println();
    }

    static void timeoutAndError() throws Exception {
        System.out.println("【orTimeout 超时与异常链】");
        CompletableFuture<String> hung = call("hung", 2000)
                .orTimeout(200, TimeUnit.MILLISECONDS)
                .exceptionally(ex -> "timeout-fallback");
        System.out.println("  超时兜底结果：" + hung.get(1, TimeUnit.SECONDS));
        CompletableFuture<String> failed = CompletableFuture.<String>failedFuture(
                new IllegalStateException("downstream error"))
                .handle((v, ex) -> ex == null ? v : "degraded:" + ex.getMessage());
        System.out.println("  handle 转译异常：" + failed.get(1, TimeUnit.SECONDS));
        System.out.println("  规则：每个 CF 链必有超时（orTimeout）与异常终态（handle）");
        System.out.println();
    }

    static void allOfCollect() throws Exception {
        System.out.println("【allOf 的正确姿势：等完成 + 收集结果】");
        List<CompletableFuture<String>> futures = List.of(
                call("a", 50), call("b", 80), call("c", 60));
        CompletableFuture<Void> all = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0]));
        all.get(1, TimeUnit.SECONDS);
        List<String> results = new ArrayList<>();
        for (CompletableFuture<String> f : futures) {
            results.add(f.join());
        }
        System.out.println("  收集结果：" + results);
        System.out.println("  坑对照：只 allOf().get() 不 join() —— 异常与结果都拿不到");
    }

    public static void main(String[] args) throws Exception {
        combination();
        timeoutAndError();
        allOfCollect();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 用 CF 实现“三下游竞速取最快 + 超时兜底”，对比虚拟线程扇出实现的代码量与可读性。
 * 2. 复现 commonPool 阻塞饥饿：CF 默认执行器跑阻塞任务 + 并行流同时工作，观察互扰；改显式虚拟执行器对比。
 * 3. 思考题：为什么 CF 在虚拟线程时代仍有一席之地？从“组合表达式复杂度”与两种写法的适用边界分析选型。
 * ============================================================
 */
