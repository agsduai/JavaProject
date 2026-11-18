/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：50-01
 * 课名：阻塞与非阻塞的并发模型
 * ------------------------------------------------------------
 * 知识点：
 *   - **高并发 IO 的本质矛盾：线程昂贵，等待免费**——平台线程（传统 Thread）1:1 映射内核线程，默认 1MB 栈，万级并发就耗尽内存与调度；但大部分时间线程在“等 IO”（DB/HTTP 响应），CPU 闲着。**并发模型的一切设计都是在解决“怎么用少量线程扛大量等待”**。
 *   - **三种并发模型**：① **阻塞 + 线程池**（简单，线程数=并发上限，池满了排队）；② **非阻塞回调/响应式**（少量事件循环线程 + 异步回调，吞吐高但代码变“回调地狱/响应式链”）；③ **虚拟线程**（阻塞语义 + 海量线程，代码同步写、运行异步化——JDK 21 的答案）。**三者解决同一个问题，体验截然不同**。
 *   - **响应式的代价在心智**：整条调用链必须全异步（一个阻塞调用卡死事件循环）、栈不可读（异常堆栈跨回调）、调试困难。**虚拟线程的承诺是“同步代码获得异步吞吐”**——这是 Java 官方对响应式复杂性的回应。
 *   - **选型的第一问：IO 密集还是 CPU 密集**——IO 密集（等 DB/HTTP）虚拟线程收益巨大；CPU 密集虚拟线程无收益（核数决定），线程池依旧正确。
 *   - **配套生态**：响应式要全套非阻塞驱动（R2DBC/非阻塞 HTTP 客户端）；虚拟线程**现有 JDBC 阻塞驱动直接可用**（这正是它省心的根源）。40-04 SSE 与 48-07 流式服务的连接并发也在此框架下重审。
 *   - 易错点：CPU 密集任务用虚拟线程（只增加调度开销）；响应式链里混入阻塞调用（事件循环被卡，全服务卡顿）；把线程池大小调大当作高并发方案（上下文切换与内存双重反噬）；以为异步 = 更快单请求（异步提升吞吐，单请求延迟可能反而略增）。
 * ============================================================
 */

package com.heima.ch50;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// ==================== 示例代码 ====================

public class BlockingVsNonBlocking {
    /** 模拟一次下游 IO：等 100ms 返回 */
    static String callDownstream(String req) {
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return "resp:" + req;
    }

    static void model1() throws Exception {
        System.out.println("【模型①：阻塞 + 固定线程池】");
        try (ExecutorService pool = Executors.newFixedThreadPool(10)) {
            long start = System.currentTimeMillis();
            List<String> results = new ArrayList<>();
            List<java.util.concurrent.Future<String>> futures = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                final String req = "req-" + i;
                futures.add(pool.submit(() -> callDownstream(req)));
            }
            for (java.util.concurrent.Future<String> f : futures) {
                results.add(f.get());
            }
            long cost = System.currentTimeMillis() - start;
            System.out.printf("  10 线程处理 20 个请求（每个等 100ms）-> %dms%n", cost);
            System.out.println("  特点：代码直观；并发上限=线程数（第 11 个请求排队）");
        }
        System.out.println();
    }

    static void model2() throws Exception {
        System.out.println("【模型②：虚拟线程（阻塞语义 + 海量线程）】");
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            long start = System.currentTimeMillis();
            List<java.util.concurrent.Future<String>> futures = new ArrayList<>();
            for (int i = 0; i < 1000; i++) {
                final String req = "req-" + i;
                futures.add(pool.submit(() -> callDownstream(req)));
            }
            for (java.util.concurrent.Future<String> f : futures) {
                f.get();
            }
            long cost = System.currentTimeMillis() - start;
            System.out.printf("  1000 个虚拟线程（阻塞写法）-> %dms（1000 个平台线程不可想象）%n", cost);
        }
        System.out.println("  特点：同步代码 + 万级并发 —— 模型①的心智 + 更高吞吐");
        System.out.println();
    }

    static void model3() {
        System.out.println("【模型③：非阻塞/响应式（少量事件循环）】");
        System.out.println("  事件循环线程数 ≈ CPU 核数，等待期间线程处理别的请求");
        List<String> traits = List.of(
                "吞吐：极高（少量线程扛大量连接，Netty/Reactor 生态）",
                "代码：Mono/Flux 链式（不可阻塞、异常跨回调、栈难读）",
                "生态约束：全链路非阻塞（驱动/客户端都要非阻塞版本）",
                "适用：网关/推送/高扇出聚合（50-11 案例）");
        for (String t : traits) {
            System.out.println("  " + t);
        }
        System.out.println();
    }

    static void decision() {
        System.out.println("【第一决策：负载类型】");
        String[][] rows = {
                {"IO 密集（等 DB/HTTP）", "虚拟线程首选，响应式次之", "等待占比高才值得换模型"},
                {"CPU 密集（计算/压缩）", "固定大小线程池 = 核数", "虚拟线程无收益，只加调度开销"},
                {"超高并发连接（>10k）", "响应式/Netty", "百万连接场景虚拟线程也吃紧"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-16s %-22s %s%n", r[0], r[1], r[2]);
        }
    }

    public static void main(String[] args) throws Exception {
        model1();
        model2();
        model3();
        decision();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实测三模型：同一 IO 任务（100ms 延迟 × 500 请求）分别用固定池/虚拟线程/模拟异步跑，记录吞吐与内存（-Xmx 限制下观察）。
 * 2. 列出你系统的每个服务的负载类型（IO/CPU 密集），标注并发模型建议。
 * 3. 思考题：为什么“单请求延迟”在三模型下差异不大而“吞吐”差异巨大？从线程等待的复用机制推导。
 * ============================================================
 */
