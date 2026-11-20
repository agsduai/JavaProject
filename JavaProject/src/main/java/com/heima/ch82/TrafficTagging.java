/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：82-02
 * 课名：流量染色与全链路透传
 * ------------------------------------------------------------
 * 知识点：
 *   - **染色的本质**：**给一次请求打上「它属于哪个版本/哪个实验组」的标记**，**后续每一跳都要带着它**，否则**下游不知道自己该走哪条路**。
 *   - **染色必须全链路透传**：**网关打标 → RPC 透传 → 线程池透传 → 消息队列透传 → 数据库/缓存 key 隔离**——**任何一跳丢失，下游就退化成基线**。
 *   - **ThreadLocal 是默认做法，也是最容易丢的地方**：**异步任务、线程池复用、`CompletableFuture`、定时任务**都会丢——**必须显式装饰（wrap Runnable/Callable）或用支持透传的实现**。
 *   - **染色的三种载体**：**HTTP Header（跨进程）**、**RPC attachment（跨服务）**、**ThreadLocal（进程内）**——**跨进程靠协议，进程内靠上下文**。
 *   - **灰度数据也要隔离**：**缓存 key 加版本前缀、消息队列加 tag、数据库双写**——**否则灰度写的数据会被基线读到**，**这是比代码 bug 更难查的事故**。
 *   - 易错点：只在入口打标不做透传；用 `@Async` 就丢了上下文；线程池里复用旧标记；缓存不隔离导致灰度数据污染基线。
 * ============================================================
 */

package com.heima.ch82;

// ==================== 示例代码 ====================

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class TrafficTagging {
    // ---------- 进程内上下文 ----------
    static final ThreadLocal<String> TAG = new ThreadLocal<>();

    static void setTag(String t) {
        TAG.set(t);
    }

    static String tag() {
        return TAG.get();
    }

    static void clear() {
        TAG.remove();
    }

    // 下游服务：有 tag 走灰度，没 tag 走基线
    static String callDownstream(String where) {
        String t = tag();
        String route = (t == null) ? "基线" : ("灰度-" + t);
        System.out.printf("    %-22s 收到 tag=%-6s -> 走 %s%n", where, String.valueOf(t), route);
        return route;
    }

    // 手动装饰：把当前线程的 tag 捕获下来，交给线程池执行
    static Runnable wrap(Runnable task) {
        String captured = tag();
        return () -> {
            String old = tag();
            setTag(captured);
            try {
                task.run();
            } finally {
                if (old == null) {
                    clear();
                } else {
                    setTag(old);
                }
            }
        };
    }

    public static void main(String[] args) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);

        System.out.println("【场景1】同步调用链：网关打标，服务A、服务B 依次调用");
        setTag("gray-v2");
        callDownstream("服务A(同步)");
        callDownstream("服务B(同步)");
        clear();

        System.out.println();
        System.out.println("【场景2】服务A 把活丢给线程池（未装饰）：ThreadLocal 丢失");
        setTag("gray-v2");
        pool.submit(() -> callDownstream("服务B(线程池-裸提交)")).get();
        clear();

        System.out.println();
        System.out.println("【场景3】服务A 用 wrap 装饰后再提交：tag 被捕获并还原");
        setTag("gray-v2");
        pool.submit(wrap(() -> callDownstream("服务B(线程池-wrap)"))).get();
        clear();

        System.out.println();
        System.out.println("【场景4】线程池复用：上一个任务留下的脏标记污染了下一个基线任务");
        // 必须固定同一个线程才能复现「线程复用」，多线程池可能把两个任务分到不同线程
        ExecutorService single = Executors.newSingleThreadExecutor();
        single.submit(() -> {
            setTag("gray-v2");                     // 故意不清理
            callDownstream("服务B(不清理)");
        }).get();
        single.submit(() -> callDownstream("服务C(本该走基线)")).get();
        single.shutdown();
        single.awaitTermination(2, TimeUnit.SECONDS);

        System.out.println();
        System.out.println("【场景5】跨进程：tag 靠 Header 传递，每跳都要显式带上");
        Map<String, String> headers = new HashMap<>();
        headers.put("x-gray-tag", "gray-v2");
        System.out.println("    网关写入 Header: " + headers);
        System.out.println("    服务A 透传 Header: " + headers.get("x-gray-tag"));
        System.out.println("    服务B 忘了透传      : null -> 走基线（染色断在这里）");

        pool.shutdown();
        pool.awaitTermination(2, TimeUnit.SECONDS);

        System.out.println();
        System.out.println("【结论】染色不是「打一个标」就完了，是「每一跳都不能丢」：");
        System.out.println("  同步调用靠 ThreadLocal，异步必须 wrap，跨进程靠 Header/attachment，");
        System.out.println("  线程池用完必须 clear——否则脏标记会让基线用户走进灰度。");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 把场景4 改成「用 finally 清理」，验证服务C 恢复走基线，说明为什么清理要放在 finally。
 * 2. 用 `CompletableFuture.supplyAsync` 替代 `pool.submit` 复现场景2，再加一层自定义 Executor 修好它。
 * 3. 思考题：灰度期间缓存 key 不加版本前缀，会出什么事故？从「基线读到灰度数据」推演一次。
 * ============================================================
 */

