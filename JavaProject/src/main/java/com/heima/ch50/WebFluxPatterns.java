/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：50-03
 * 课名：WebFlux 的分层与陷阱
 * ------------------------------------------------------------
 * 知识点：
 *   - **WebFlux 与 MVC 的并存**：Spring 5+ 两种栈——Servlet 栈（Tomcat，阻塞友好）与 Reactive 栈（Netty，WebFlux）。**选 WebFlux 的前提是整条链路非阻塞**：R2DBC（数据库）、WebClient（HTTP）、无阻塞锁。混栈是最常见的翻车起点。
 *   - **Controller 返回 Mono/Flux 即接入异步**：框架负责订阅——**返回值类型就是契约**（返回 Mono 的接口天然支持流式/背压，40-04 SSE 在 WebFlux 下就是 Flux 返回值）。
 *   - **陷阱清单**：① Mono 里 .block()（直接阻塞事件循环，测试环境看似正常、生产高并发雪崩）；② 在 controller 里手工 subscribe（自己订阅后框架拿不到结果，返回空）；③ 链路中同步锁/ThreadLocal（线程切换失效）；④ 日志 MDC 丢 trace（50-02 的上下文问题）；⑤ R2DBC 没上（DB 还是 JDBC 阻塞——整个改造白做）。
 *   - **什么场景值得 WebFlux**：聚合网关、长连接推送、高扇出并行调用（下游多个 + 各自延迟）；**普通 CRUD 服务用 MVC + 虚拟线程更省心**（50-07 选型）。
 *   - **与虚拟线程的关系**：JDK 21 后"为吞吐而 WebFlux"的必要性下降——**同步代码 + 虚拟线程达到相近吞吐**；WebFlux 的剩余价值在流式/背压语义（SSE、实时推送）与生态（Gateway/R2DBC 已是事实标准）。
 *   - 易错点：为"显得先进"上 WebFlux（团队不熟 Reactor，隐蔽 bug 群）；block() 出现在生产代码（压测才暴露）；webflux + JDBC 混搭（假异步真阻塞）；错误处理缺失（onErrorReturn 全局兜底漏配）。
 * ============================================================
 */

package com.heima.ch50;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

// ==================== 示例代码 ====================

public class WebFluxPatterns {
    /** 用“小事件循环”模拟：2 个 worker 线程扛全部请求 */
    static final ExecutorService EVENT_LOOP = Executors.newFixedThreadPool(2);
    static final List<String> CALLS = new ArrayList<>();

    static String blockingDbCall() {
        try {
            Thread.sleep(50);                   // 模拟 JDBC 阻塞
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return "row";
    }

    static void eventLoopStarvation() throws Exception {
        System.out.println("【事件循环饥饿：一个阻塞调用拖垮全部并发】");
        for (int i = 0; i < 10; i++) {
            EVENT_LOOP.submit(() -> {
                CALLS.add(blockingDbCall());    // 毒药：worker 线程上阻塞 50ms
                return null;
            });
        }
        EVENT_LOOP.shutdown();
        EVENT_LOOP.awaitTermination(5, TimeUnit.SECONDS);
        System.out.printf("  10 个请求 × 50ms 阻塞 ÷ 2 个 worker = 至少 %.0fms 才能清空%n", 10 * 50 / 2.0);
        System.out.println("  期间这 2 个线程无法处理任何其他请求 —— 并发上限被阻塞锁死");
        System.out.println("  WebFlux 等价场景：Mono.fromCallable(::blockingDbCall) 未切弹性调度器");
        System.out.println();
    }

    static void subscribeTrap() {
        System.out.println("【手工 subscribe 陷阱：返回值拿不到】");
        List<String> wrong = List.of(
                "错误写法：pipeline.subscribe() 之后 return null",
                "    -> 框架拿到的是空响应（你自己订阅，框架没东西可消费）",
                "正确写法：return pipeline（把管道交给框架订阅）",
                "后台任务例外：无人消费的管道才需要自己 subscribe 并管理生命周期");
        for (String w : wrong) {
            System.out.println("  " + w);
        }
        System.out.println();
    }

    static void checklist() {
        System.out.println("【WebFlux 上线检查单】");
        List<String> items = List.of(
                "全链路非阻塞：R2DBC/WebClient，禁 JDBC/RestTemplate 阻塞客户端",
                "无 .block()：CI 静态扫描 + BlockHound 测试拦截",
                "无手工 subscribe 泄漏：返回管道交给框架",
                "trace_id 方案改造（Context 或 MDC 桥），禁裸 ThreadLocal",
                "错误兜底：全局 onError 处理 + 每条管道 onErrorReturn",
                "团队培训：Reactor 调试与排障技能确认");
        for (String i : items) {
            System.out.println("  [ ] " + i);
        }
        System.out.println("  任何一项不达标 -> 选 MVC + 虚拟线程（50-07）");
    }

    public static void main(String[] args) throws Exception {
        eventLoopStarvation();
        subscribeTrap();
        checklist();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 用"小事件循环"模拟复现阻塞饥饿：2 个 worker 承载 20 个含阻塞调用的任务，记录清空时间；去掉阻塞再对比。
 * 2. 对比同一聚合接口的 MVC 与 WebFlux 实现，从代码行数、可读性、压测吞吐三个维度记录结论。
 * 3. 思考题：为什么"一个 .block()"能在生产雪崩？从事件循环线程数与请求并发的关系推导传播范围。
 * ============================================================
 */
