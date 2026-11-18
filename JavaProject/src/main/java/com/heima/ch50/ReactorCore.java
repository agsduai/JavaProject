/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：50-02
 * 课名：Reactor 核心：Mono/Flux 与背压
 * ------------------------------------------------------------
 * 知识点：
 *   - **响应式的数据结构只有两个**：`Mono<T>`（0~1 个元素的异步序列）与 `Flux<T>`（0~N 个）。**它们是“未来的流”**：声明处理管道，订阅时才执行（惰性）。理解“声明 vs 执行”的分离是 Reactor 的入门关。
 *   - **操作符即管道**：map/filter/flatMap/concatMap/zip/merge——**flatMap 决定并发度**（flatMapSequential 保序，flatMap 并发乱序完成）；错误传播沿管道（onErrorResume/onErrorMap），相当于同步代码的 try-catch 在管道里流动。
 *   - **背压（backpressure）**：消费者控制生产者速度（request(n) 拉取）——43-08 反压在应用层的对应机制；无界流（无限 Flux）+ 慢消费者必须背压策略（buffer/drop/latest），否则内存爆炸。
 *   - **订阅才执行**：`flux.map(...)` 只是搭管道；`subscribe()` 才启动——**忘了订阅是响应式第一 bug**（代码写了没效果）；WebFlux 框架帮你订阅，但测试与后台任务要自己 subscribe。
 *   - **阻塞是响应式的毒药**：管道中任何一步调用阻塞 API（JDBC/Thread.sleep/同步锁）会卡死事件循环线程——**检测手段**：BlockHound 类工具在测试中拦截阻塞调用。
 *   - **上下文传递**：MDC/trace_id 在响应式里不能用 ThreadLocal 直传（线程切换）——用 Reactor Context 或改造日志方案（42-02 在响应式的对应改造）。
 *   - 易错点：Mono 里做阻塞 IO（卡死 worker 线程）；subscribe 泄漏（无限流不取消）；flatMap 无界并发（下游被打爆，要 concurrency 参数）；异常没接住（onError 缺失 -> 管道静默终止）。
 * ============================================================
 */

package com.heima.ch50;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;
import java.util.concurrent.TimeUnit;

// ==================== 示例代码 ====================

public class ReactorCore {
    /** JDK 自带的 Reactive Streams API（java.util.concurrent.Flow）演示背压 */
    static class TickSubscriber implements Flow.Subscriber<Integer> {
        private Flow.Subscription subscription;
        private final String name;
        private int received = 0;

        TickSubscriber(String name) {
            this.name = name;
        }

        public void onSubscribe(Flow.Subscription s) {
            this.subscription = s;
            s.request(3);                       // 背压：只拉 3 个（消费者控制速度）
            System.out.printf("  [%s] 订阅成功，request(3)%n", name);
        }

        public void onNext(Integer item) {
            received++;
            System.out.printf("  [%s] 收到 %d（共 %d）%n", name, item, received);
            if (received % 3 == 0) {
                subscription.request(3);        // 处理完再拉下一批
            }
        }

        public void onError(Throwable t) {
            System.out.printf("  [%s] 错误：%s%n", name, t.getMessage());
        }

        public void onComplete() {
            System.out.printf("  [%s] 完成%n", name);
        }
    }

    static void backpressureDemo() throws Exception {
        System.out.println("【背压：消费者 request(n) 控制生产速度】");
        try (SubmissionPublisher<Integer> publisher = new SubmissionPublisher<>()) {
            publisher.subscribe(new TickSubscriber("slow-consumer"));
            for (int i = 1; i <= 10; i++) {
                int lag = publisher.estimateMaximumLag();
                publisher.submit(i);
                TimeUnit.MILLISECONDS.sleep(50);
                if (i == 5) {
                    System.out.printf("  [生产者] 已提交 5 个，未消费积压（lag 估计）≈ %d%n", lag);
                }
            }
            publisher.close();
            TimeUnit.MILLISECONDS.sleep(300);
        }
        System.out.println("  生产 > 消费时：积压可观测、消费者限流拉取 —— 43-08 反压的应用层原语");
        System.out.println();
    }

    static void lazyVsEager() {
        System.out.println("【声明 vs 订阅：管道搭好不执行，订阅才跑】");
        List<String> executed = new ArrayList<>();
        Runnable pipeline = () -> {           // 模拟“声明好的管道”
            executed.add("map(加工)");
            executed.add("filter(过滤)");
        };
        System.out.println("  管道已声明：" + executed.isEmpty() + "（尚未执行任何步骤）");
        System.out.println("  订阅（subscribe）触发执行：");
        pipeline.run();
        System.out.println("  执行轨迹：" + executed);
        System.out.println("  响应式第一 bug：搭好管道忘了订阅 —— 代码写了没效果");
        System.out.println();
    }

    static void blockingPoison() {
        System.out.println("【阻塞是响应式的毒药】");
        List<String> consequences = List.of(
                "Mono 里调 JDBC 阻塞 50ms -> 事件循环线程被占 50ms",
                "事件循环线程 = CPU 核数（如 8）-> 并发上限骤降为 160 QPS",
                "对照：全部非阻塞时同 8 线程可扛数千并发",
                "检测：BlockHound 类工具在测试期拦截阻塞调用",
                "铁律：响应式链路里禁止 Thread.sleep / JDBC / 同步锁 IO");
        for (String c : consequences) {
            System.out.println("  " + c);
        }
        System.out.println();
    }

    public static void main(String[] args) throws Exception {
        backpressureDemo();
        lazyVsEager();
        blockingPoison();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 用 Flow API 实现一个"并发拉取 3 个下游 -> 合并结果"的管道，对比串行处理的总耗时。
 * 2. 构造"慢消费者 + 无界生产"的场景，观察积压增长，然后加背压策略（限 request 速率）验证行为。
 * 3. 思考题：为什么"忘了订阅"是响应式最常见的 bug？从声明与执行分离的语义分析，列出三个典型漏订阅场景。
 * ============================================================
 */
