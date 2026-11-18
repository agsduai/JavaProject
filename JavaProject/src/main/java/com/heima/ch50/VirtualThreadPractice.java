/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：50-05
 * 课名：虚拟线程实战与并发编排
 * ------------------------------------------------------------
 * 知识点：
 *   - **虚拟线程的编排原语**：`newVirtualThreadPerTaskExecutor()`（每任务一线程）+ Future/CompletionStage 编排；**结构化并发**（StructuredTaskScope，JDK 21 预览 API——任务生命周期嵌套在作用域内、取消自动传播）理念重要，本课用 Executor + Future 模拟其语义（避免 preview 编译门槛），生产待其转正。
 *   - **扇出聚合模式**（50-11 案例原型）：并行调 N 个下游（各自虚拟线程）-> 全部成功合并 / 任一失败快速失败或降级——**timeout 与取消要显式管理**：`Future.get(timeout)` + 取消其余任务，避免“快的等慢的”。
 *   - **与 Semaphore 的限流配合**：虚拟线程廉价但**下游不便宜**——并发保护下游用 `Semaphore.acquire()`（阻塞在信号量上会卸载，不占载体）——**线程数无限、下游并发有限**的正确表达。
 *   - **与现有设施的关系**：ThreadLocal 可用（每虚拟线程独立副本，但百万副本要算内存账 50-06）；连接池不变——DB 连接仍是稀缺资源，虚拟线程排队等连接，**瓶颈平移到池，吞吐上限由池决定**（池大小重新评估）；Spring Boot 3.2+ 一行开启 `spring.threads.virtual.enabled=true`。
 *   - **错误处理语义**：单任务异常不影响其他任务（各自 Future）；批量场景收集异常而不是快速失败（报告型任务）；关键路径快速失败 + 取消其余（成本控制）。
 *   - 易错点：虚拟线程内再套自定义线程池（资源叠加失控）；Future.get 无超时（一个慢下游拖死整批）；取消不传播（幽灵任务继续跑 43 章概念）；把虚拟线程当异步框架（它是并发原语，生态在追赶 50-07）。
 * ============================================================
 */

package com.heima.ch50;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

// ==================== 示例代码 ====================

public class VirtualThreadPractice {
    static String call(String name, long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return name + "-ok";
    }

    static void fanoutAggregation() throws Exception {
        System.out.println("【扇出聚合：并行 3 下游 + 总超时】");
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            long start = System.currentTimeMillis();
            Future<String> f1 = pool.submit(() -> call("stock", 120));
            Future<String> f2 = pool.submit(() -> call("price", 80));
            Future<String> f3 = pool.submit(() -> call("promo", 200));
            String r1 = f1.get(500, TimeUnit.MILLISECONDS);
            String r2 = f2.get(500, TimeUnit.MILLISECONDS);
            String r3 = f3.get(500, TimeUnit.MILLISECONDS);
            System.out.printf("  合并：%s / %s / %s -> 耗时 %dms（≈最慢者而非之和）%n",
                    r1, r2, r3, System.currentTimeMillis() - start);
        }
        System.out.println("  对照串行 400ms；并行收益 = max(下游) —— IO 密集的核心红利");
        System.out.println();
    }

    static void semaphoreGuard() throws Exception {
        System.out.println("【Semaphore：线程无限，下游并发有限】");
        Semaphore permit = new Semaphore(10);       // 下游只承受 10 并发
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<String>> futures = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                futures.add(pool.submit(() -> {
                    permit.acquire();               // 阻塞在信号量：卸载，不占载体
                    try {
                        return call("db-call", 50);
                    } finally {
                        permit.release();
                    }
                }));
            }
            for (Future<String> f : futures) {
                f.get();
            }
        }
        System.out.println("  100 个虚拟线程完成；同一时刻下游最多 10 并发");
        System.out.println("  关键：限流点从“线程池大小”变成“显式 Semaphore”（50-06 池化误区）");
        System.out.println();
    }

    static void timeoutWithCancel() throws Exception {
        System.out.println("【超时与取消：慢下游不拖垮整批】");
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<String> fast = pool.submit(() -> call("fast", 50));
            Future<String> slow = pool.submit(() -> call("slow", 2000));
            String r = fast.get(200, TimeUnit.MILLISECONDS);
            slow.cancel(true);
            System.out.printf("  fast=%s；slow 已取消（释放资源，不拖尾）%n", r);
        }
        System.out.println("  结构化并发（preview API）将把这类模式标准化：作用域内自动取消传播");
        System.out.println("  生产注意：cancel(true) 中断要被任务体响应（sleep/阻塞点感知中断）");
    }

    public static void main(String[] args) throws Exception {
        fanoutAggregation();
        semaphoreGuard();
        timeoutWithCancel();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现扇出聚合的降级版：下游超时返回占位值（合并继续），对比“快速失败”与“降级合并”两种语义的业务差异。
 * 2. 用压测验证“连接池是真瓶颈”：池 10 与池 50 的吞吐差异，推导池大小公式（池 ≈ 目标并发 × 下游耗时占比）。
 * 3. 思考题：为什么虚拟线程时代“限流用 Semaphore 而不是线程池大小”？从线程与并发解耦的本质分析两种限流点的差异。
 * ============================================================
 */
