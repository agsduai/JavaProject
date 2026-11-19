/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：60-08
 * 课名：Tomcat：连接器与容器
 * ------------------------------------------------------------
 * 知识点：
 *   - **Tomcat 的两大组件：连接器（Connector）与容器（Container）**：连接器负责**接收连接、解析 HTTP、生成 Request/Response**；容器负责**按 URL 找到 Servlet 并调用**（Engine -> Host -> Context -> Wrapper 四层）。
 *   - **连接器的核心结构**：`Acceptor`（接收连接）-> `Poller`（NIO 多路复用）-> `Worker`（处理请求）——**NIO 模型下少量线程扛大量连接**（60-06 的 Netty 模型同源，只是实现不同）；**BIO 模式下是"一连接一线程"**（老版本，连接数上不去）。
 *   - **Servlet 的生命周期**：`init`（容器启动时一次）-> `service`（**每个请求，多线程并发**）-> `destroy`（容器关闭）——**Servlet 是单例多线程，`Servlet` 里的实例变量是并发共享的**（17 章的线程安全在这里是最容易翻车的地方）。
 *   - **线程池与最大连接数**：`maxThreads`（工作线程上限）、`acceptCount`（等待队列长度）、`maxConnections`（最大连接数）——**三者配比不当的两种故障**：线程太少（请求排队慢）、队列太长（超时后才被拒绝）。
 *   - **Spring Boot 的嵌入式 Tomcat**：`server.tomcat.*` 配置 + `TomcatServletWebServerFactory` 定制；**虚拟线程模式下（50 章）线程复用逻辑变化**——`spring.threads.virtual.enabled=true` 时请求处理走虚拟线程。
 *   - 易错点：Servlet 里放可变实例变量（并发错乱）；`maxThreads` 调大但下游连接池没扩（线程全堵在获取连接）；`ServletContext` 当缓存用（无过期策略）；误以为 Tomcat 是"每次请求 new 一个 Servlet"。
 * ============================================================
 */

package com.heima.ch60;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class TomcatDemo {
    record Route(String pattern, String servlet) {
    }

    /** 容器映射：URL -> Servlet（精确 > 路径 > 扩展名 > 默认，简化版） */
    static String resolve(List<Route> routes, String path) {
        for (Route r : routes) {
            if (r.pattern().equals(path)) {
                return r.servlet() + "（精确匹配）";
            }
        }
        for (Route r : routes) {
            if (path.startsWith(r.pattern())) {
                return r.servlet() + "（路径匹配：" + r.pattern() + "）";
            }
        }
        return "DefaultServlet（静态资源/404）";
    }

    /** 线程池三参数的影响：模拟排队与拒绝 */
    static void threadPool(int maxThreads, int acceptCount, int requests, long serviceMs) {
        int running = Math.min(maxThreads, requests);
        int queued = Math.min(acceptCount, Math.max(0, requests - maxThreads));
        int rejected = Math.max(0, requests - maxThreads - acceptCount);
        double totalMs = Math.ceil((double) requests / maxThreads) * serviceMs;
        System.out.printf("  maxThreads=%d acceptCount=%d 请求=%d 单请求=%dms%n",
                maxThreads, acceptCount, requests, serviceMs);
        System.out.printf("    并发处理=%d 排队=%d 拒绝=%d 总耗时≈%.0fms%n",
                running, queued, rejected, totalMs);
        if (rejected > 0) {
            System.out.println("    结论：拒绝 " + rejected + " 个 -> 用户看到 503（队列不足）");
        }
        if (maxThreads * serviceMs / 1000.0 > 2 && requests > maxThreads) {
            System.out.println("    结论：线程不足 -> 排队时间过长（扩容或异步化）");
        }
    }

    /** Servlet 生命周期与并发共享状态 */
    static void servletLifecycle() {
        System.out.println("【Servlet 生命周期】init（一次）-> service（每请求，多线程）-> destroy（一次）");
        Map<String, String> state = new LinkedHashMap<>();
        state.put("实例变量 counter", "并发共享！必须用 AtomicInteger");
        state.put("局部变量", "每请求独立，安全");
        state.put("成员 service 引用", "无状态，安全");
        state.forEach((k, v) -> System.out.printf("  %-16s %s%n", k, v));
    }

    public static void main(String[] args) {
        List<Route> routes = List.of(
                new Route("/api/order", "OrderServlet"),
                new Route("/api/", "ApiDispatchServlet"),
                new Route("*.do", "LegacyServlet"));
        System.out.println("【URL 路由解析】");
        for (String p : new String[]{"/api/order", "/api/user/1", "/static/a.js"}) {
            System.out.printf("  %-16s -> %s%n", p, resolve(routes, p));
        }
        System.out.println();
        System.out.println("【线程池参数组合】");
        threadPool(200, 100, 500, 50);
        System.out.println();
        threadPool(50, 20, 500, 50);
        System.out.println();
        servletLifecycle();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现 Servlet 路由匹配器（精确/路径/扩展名/默认四级），用 10 个 URL 验证优先级顺序。
 * 2. 实现"请求排队模拟器"：给定 maxThreads/acceptCount/到达速率/服务时长，输出平均排队时间、拒绝率与 P99（用队列论近似）。
 * 3. 思考题：为什么 `maxThreads` 调大不一定提升吞吐？从"下游连接池/CPU/DB"三个瓶颈的联动分析。
 * ============================================================
 */
