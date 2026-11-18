/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：29-06
 * 课名：网关：路由、鉴权、限流
 * ------------------------------------------------------------
 * 知识点：
 *   - 网关是所有外部流量的**唯一入口**，承担：路由转发、统一鉴权、限流熔断、协议转换、日志埋点、跨域处理。它的存在让内部服务不必各自实现这些横切能力。
 *   - 路由规则：按路径前缀（`/api/order/**` → order-service）、按域名、按 Header。路由要支持**灰度**（按用户 ID 或 Header 分流到新版本）。
 *   - 鉴权放在网关：统一校验 token，把解析出的用户 ID 放进请求头传给下游（下游信任内网头，不再重复解析）。注意内部头必须由网关**覆盖**而非透传，否则外部可以伪造。
 *   - 限流三个维度：**QPS/并发数**（令牌桶、漏桶、滑动窗口）、**用户级**（防单用户刷接口）、**接口级**（保护慢接口）。限流算法选令牌桶（允许突发）或漏桶（严格匀速）。
 *   - 网关自身要高可用：多实例 + 前面挂 SLB，网关不能成为单点。
 *   - 易错：网关做了鉴权但下游还信任外部传入的 `X-User-Id`（越权）；限流只做全局限流，一个用户刷满把所有人挡住；网关里写业务逻辑，越写越厚变成"网关巨石"。
 * ============================================================
 */

package com.heima.ch29;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class ApiGateway {
    /** 路由规则 */
    record Route(String prefix, String target, boolean needAuth) { }

    static final List<Route> ROUTES = new ArrayList<>(List.of(
            new Route("/api/users", "user-service", false),
            new Route("/api/items", "item-service", false),
            new Route("/api/orders", "order-service", true),
            new Route("/api/points", "points-service", true)
    ));

    /** 令牌桶：容量 capacity，每秒补充 rate 个 */
    static class TokenBucket {
        final int capacity;
        final int rate;
        double tokens;
        long lastRefillMs;

        TokenBucket(int capacity, int rate, long now) {
            this.capacity = capacity;
            this.rate = rate;
            this.tokens = capacity;
            this.lastRefillMs = now;
        }

        boolean tryAcquire(long now) {
            double add = (now - lastRefillMs) / 1000.0 * rate;
            tokens = Math.min(capacity, tokens + add);
            lastRefillMs = now;
            if (tokens >= 1) {
                tokens -= 1;
                return true;
            }
            return false;
        }
    }

    static String route(String path) {
        for (Route r : ROUTES) {
            if (path.startsWith(r.prefix())) {
                return r.target();
            }
        }
        return null;
    }

    static boolean needAuth(String path) {
        for (Route r : ROUTES) {
            if (path.startsWith(r.prefix())) {
                return r.needAuth();
            }
        }
        return true;
    }

    public static void main(String[] args) {
        System.out.println("=== 路由转发 ===");
        for (String p : List.of("/api/items/1", "/api/orders/9", "/api/points/1001", "/api/unknown")) {
            String target = route(p);
            System.out.println("  " + p + " -> " + (target == null ? "404 无匹配路由" : target));
        }

        System.out.println();
        System.out.println("=== 统一鉴权 + 覆盖内部头（防伪造）===");
        Map<String, String> headers = new HashMap<>();
        headers.put("X-User-Id", "9999");
        System.out.println("  外部请求自带 X-User-Id=" + headers.get("X-User-Id") + "（伪造）");
        String token = "Bearer valid-token-for-u1001";
        if (needAuth("/api/orders") && token.startsWith("Bearer ")) {
            headers.put("X-User-Id", "u1001");
            System.out.println("  网关校验 token 通过，强制覆盖 X-User-Id=" + headers.get("X-User-Id"));
        }
        System.out.println("  下游只信任网关写入的头，绝不透传外部同名头");

        System.out.println();
        System.out.println("=== 接口级限流：令牌桶 容量5 / 每秒2 ===");
        TokenBucket bucket = new TokenBucket(5, 2, 0);
        long now = 0;
        int allowed = 0;
        int rejected = 0;
        for (int i = 1; i <= 12; i++) {
            boolean ok = bucket.tryAcquire(now);
            System.out.println("  t=" + now + "ms 第 " + i + " 个请求 -> " + (ok ? "放行" : "429 限流"));
            if (ok) {
                allowed++;
            } else {
                rejected++;
            }
            now += 300;
        }
        System.out.println("  放行 " + allowed + "，拒绝 " + rejected);
        System.out.println("  令牌桶允许突发（初始 5 个），漏桶则严格匀速");

        System.out.println();
        System.out.println("=== 限流维度 ===");
        Map<String, String> dims = new LinkedHashMap<>();
        dims.put("全局 QPS", "保护整个集群，防止被打穿");
        dims.put("单用户 QPS", "防刷接口，避免一个用户挤占所有人");
        dims.put("单接口 QPS", "保护慢接口（如导出报表）不拖垮线程池");
        dims.put("单 IP 连接数", "防爬虫和异常客户端");
        for (Map.Entry<String, String> e : dims.entrySet()) {
            System.out.println("  " + e.getKey() + "：" + e.getValue());
        }
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 把令牌桶换成漏桶，用同样的请求序列对比放行结果。
 * 2. 设计按用户 ID 的限流：同一用户每秒最多 3 次，超限返回 429，写出核心数据结构。
 * 3. 说明"网关鉴权 + 下游透传外部头"会导致什么安全问题，给出修复方式。
 * ============================================================
 */
