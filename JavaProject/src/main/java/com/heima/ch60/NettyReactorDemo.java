/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：60-06
 * 课名：Netty：主从 Reactor 线程模型
 * ------------------------------------------------------------
 * 知识点：
 *   - **Netty 的线程模型是"主从 Reactor 多线程"**：**BossGroup**（1~N 个线程）只负责 `accept` 新连接，把连接注册到 **WorkerGroup**（默认 CPU 核数 × 2）里的某个 EventLoop；**每个 EventLoop 是一个单线程事件循环，绑定多个 Channel**——**一个 Channel 的所有 IO 事件都在同一个 EventLoop 线程上处理**，天然无锁。
 *   - **单线程事件循环的意义**：Channel 的状态（读缓冲/写队列/编解码器状态）**只被一个线程访问**，不需要加锁——**这就是 Netty 高性能的根源**（50-03 事件循环思想的网络版）。代价：**任何阻塞操作都会卡住该 EventLoop 上的所有 Channel**。
 *   - **"不要在 ChannelHandler 里做耗时操作"是 Netty 第一铁律**：耗时业务要交给业务线程池（`DefaultEventExecutorGroup` 或自己的线程池）；阻塞（JDBC/同步 HTTP）同理——**否则一个慢请求拖垮整条连接组**。
 *   - **写操作是异步的**：`channel.writeAndFlush(msg)` 返回 `ChannelFuture`，**实际写入由 EventLoop 异步完成**；写缓冲（ChannelOutboundBuffer）满时会"写不进去"（`isWritable()==false`），需要背压处理（43-08 同源的 IO 背压）。
 *   - **与 47-02 Sidecar / 50-02 事件循环的对照**：Netty 的 EventLoop 就是"少量线程 + 非阻塞 + 事件驱动"的经典实现；**Reactor 模式（26-01 的 AOP 链、50-03 的 WebFlux）在 IO 层的原型就是它**。
 *   - 易错点：在 handler 里做阻塞调用（全连接组卡死）；以为 `writeAndFlush` 是同步的（返回 Future）；`ChannelHandler` 被多连接共享（状态错乱，需要 `@Sharable` 才可共享）；业务线程池与 EventLoop 数量配比失衡。
 * ============================================================
 */

package com.heima.ch60;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class NettyReactorDemo {
    record Event(String channelId, String type, long durationMs) {
    }

    /** 单线程 EventLoop：串行处理绑定到它的所有 Channel 事件 */
    static class EventLoop implements Runnable {
        final String name;
        final List<Event> queue = new ArrayList<>();
        final List<String> log = new ArrayList<>();

        EventLoop(String name) {
            this.name = name;
        }

        void submit(Event e) {
            queue.add(e);
        }

        public void run() {
            for (Event e : queue) {
                log.add(name + " 处理 " + e.channelId() + " 的 " + e.type()
                        + "（" + e.durationMs() + "ms）");
            }
        }
    }

    /** 主从 Reactor：Boss 分发连接，Worker 处理 IO */
    static void run(int workers) {
        List<EventLoop> workerLoops = new ArrayList<>();
        for (int i = 0; i < workers; i++) {
            workerLoops.add(new EventLoop("worker-" + i));
        }
        // 4 个连接按 hash 绑定到不同 worker
        Map<String, String> assignment = new LinkedHashMap<>();
        for (int i = 0; i < 4; i++) {
            String channel = "conn-" + i;
            int idx = Math.abs(channel.hashCode()) % workers;
            assignment.put(channel, "worker-" + idx);
            workerLoops.get(idx).submit(new Event("conn-" + i, "READ", 5 + i));
        }
        System.out.println("【连接分配（一个 Channel 绑定一个 EventLoop）】" + assignment);
        System.out.println("【并行处理】");
        workerLoops.forEach(EventLoop::run);
        workerLoops.forEach(l -> l.log.forEach(s -> System.out.println("  " + s)));
        System.out.println();
    }

    /** 阻塞操作的代价：同一 EventLoop 上的其他连接被拖住 */
    static void blockingImpact() {
        EventLoop loop = new EventLoop("worker-0");
        loop.submit(new Event("conn-A", "READ", 5));
        loop.submit(new Event("conn-B", "BUSINESS(阻塞 200ms)", 200));
        loop.submit(new Event("conn-C", "READ", 5));
        loop.run();
        System.out.println("【阻塞操作的影响】");
        loop.log.forEach(s -> System.out.println("  " + s));
        System.out.println("  conn-C 的等待 = conn-A + conn-B 的耗时（B 阻塞了整条事件循环）");
        System.out.println("  正解：耗时业务交给业务线程池，EventLoop 只做 IO");
    }

    public static void main(String[] args) {
        run(2);
        blockingImpact();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现完整主从 Reactor 模拟：Boss 接收连接 -> 按 hash 分发 Worker -> 每个 Worker 单线程轮询，统计各连接的等待时间。
 * 2. 在模拟器里加入"业务线程池"：把耗时事件移出 EventLoop，对比"移出前/后"其他连接的最大等待时间。
 * 3. 思考题：为什么"一个 Channel 固定绑定一个 EventLoop"能免锁？从状态访问的线程唯一性推导（并说明这个设计的代价）。
 * ============================================================
 */
