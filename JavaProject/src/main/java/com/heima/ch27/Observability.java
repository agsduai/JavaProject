/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：27-08
 * 课名：日志、监控与链路追踪
 * ------------------------------------------------------------
 * 知识点：
 *   - 日志级别怎么用：`ERROR` 需要人介入（异常、资损）、`WARN` 可自愈但要关注（重试成功、降级）、`INFO` 关键业务节点（下单成功、支付回调）、`DEBUG` 排查用（入参出参，生产默认关闭）。
 *   - 结构化日志：`时间 级别 traceId 线程 类名 消息 key=value`。别用字符串拼接把关键字段埋在句子里，否则没法检索和统计。
 *   - traceId：一次外部请求生成一个，通过 `MDC.put("traceId", ...)` 或 `ThreadLocal` 贯穿全链路，跨服务通过 HTTP 头 / MQ 属性传递。没有 traceId，一个并发 500 的服务日志基本没法看。
 *   - 三个绝不：绝不打密码/令牌/身份证/银行卡；绝不 `printStackTrace()`；绝不 `catch` 住异常什么都不做。
 *   - 日志切割：按天 + 按大小滚动，保留 7~30 天，总量设上限。单文件几百 MB 会导致排查时打不开。
 *   - 可观测性三支柱：日志（发生了什么）、指标（整体健康度：QPS、P99 延迟、错误率、连接池占用）、链路追踪（一次请求经过了哪些服务，哪一段慢）。
 *   - 黄金指标：延迟、流量、错误、饱和度。告警要基于"错误率 > 1% 持续 5 分钟"这类组合条件，而不是单次异常。
 *   - 健康检查分两种：`liveness`（进程活着吗，失败就重启）和 `readiness`（能接流量吗，失败就从负载均衡摘掉）。数据库连不上应该 `readiness` 失败而不是 `liveness` 失败，否则会陷入无限重启。
 *   - 易错：在循环里打 `INFO` 日志（日志量爆炸）；日志里打大对象 `toString()`（一次几百 KB）；异步线程里丢了 MDC 上下文。
 * ============================================================
 */

package com.heima.ch27;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

// ==================== 示例代码 ====================

public class Observability {
    enum Level {
        DEBUG(0), INFO(1), WARN(2), ERROR(3);
        final int weight;

        Level(int weight) {
            this.weight = weight;
        }
    }

    /** 等价于 MDC：每个请求一个 traceId */
    static final ThreadLocal<String> TRACE_ID = new ThreadLocal<>();
    static Level threshold = Level.INFO;
    static final List<Long> LATENCIES = Collections.synchronizedList(new ArrayList<>());
    static final AtomicLong TOTAL = new AtomicLong();
    static final AtomicLong ERRORS = new AtomicLong();

    public static void main(String[] args) {
        System.out.println("=== 结构化日志：带 traceId ===");
        TRACE_ID.set("a1b2c3d4");
        log(Level.INFO, "OrderController", "收到下单请求 userId=1001 itemId=42 count=2");
        log(Level.DEBUG, "ItemService", "查询商品缓存未命中 id=42");       // 生产不开 DEBUG
        log(Level.INFO, "OrderService", "扣库存成功 itemId=42 before=100 after=98");
        log(Level.WARN, "PayClient", "支付网关超时，准备重试 attempt=1 cost=3021ms");
        log(Level.ERROR, "OrderService", "下单失败 orderNo=ORD20261117001 reason=库存不足");
        TRACE_ID.remove();

        System.out.println();
        System.out.println("=== 没有 traceId 的日志基本没法排查 ===");
        log(Level.INFO, "OrderService", "另一个请求的日志（traceId=null）");

        System.out.println();
        System.out.println("=== 敏感信息必须脱敏 ===");
        String phone = "13812345678";
        String token = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ6aGFuZ3NhbiJ9.abc";
        log(Level.INFO, "UserService", "登录 phone=" + maskPhone(phone)
                + " token=" + maskToken(token));
        System.out.println("  反例（绝不能出现在日志里）: phone=" + phone + " token=" + token);

        System.out.println();
        System.out.println("=== 指标：QPS / 错误率 / P99 ===");
        for (int i = 0; i < 100; i++) {
            long cost = 20 + (i % 10) * 5;
            if (i == 87) {
                cost = 1800;                 // 一个长尾请求
            }
            record(cost, i % 50 == 0);
        }
        System.out.printf("  总请求=%d 错误数=%d 错误率=%.2f%%%n",
                TOTAL.get(), ERRORS.get(), ERRORS.get() * 100.0 / TOTAL.get());
        System.out.printf("  P50=%dms P95=%dms P99=%dms MAX=%dms%n",
                percentile(50), percentile(95), percentile(99), percentile(100));
        System.out.println("  平均值会掩盖长尾：平均只有 "
                + String.format("%.1f", avg()) + "ms，但 P99 已经到 " + percentile(99) + "ms");

        System.out.println();
        System.out.println("=== 告警规则示例 ===");
        Map<String, String> alerts = new LinkedHashMap<>();
        alerts.put("错误率", "rate(error[5m]) / rate(total[5m]) > 0.01 持续 5m");
        alerts.put("P99 延迟", "histogram_quantile(0.99, latency) > 500ms 持续 5m");
        alerts.put("连接池", "hikari_active / hikari_max > 0.9 持续 2m");
        alerts.put("磁盘", "disk_free < 10%");
        alerts.forEach((k, v) -> System.out.printf("  %-10s %s%n", k, v));

        System.out.println();
        System.out.println("=== 健康检查两种探针 ===");
        System.out.println("  /actuator/health/liveness  -> 进程能响应就行，失败 -> 重启容器");
        System.out.println("  /actuator/health/readiness -> 依赖（DB/Redis）都通才返回 UP，失败 -> 摘流量");
        System.out.println("  DB 挂了让 liveness 失败 = 无限重启，问题永远好不了");
    }

    /** 一条结构化日志 */
    static void log(Level level, String logger, String msg) {
        if (level.weight < threshold.weight) {
            return;
        }
        String trace = TRACE_ID.get() == null ? "-" : TRACE_ID.get();
        System.out.printf("  2026-11-17T10:23:45.123+08:00 %-5s [%s] [%s] %s - %s%n",
                level, trace, Thread.currentThread().getName(), logger, msg);
    }

    static void record(long costMs, boolean error) {
        LATENCIES.add(costMs);
        TOTAL.incrementAndGet();
        if (error) {
            ERRORS.incrementAndGet();
        }
    }

    static long percentile(int p) {
        List<Long> copy;
        synchronized (LATENCIES) {
            copy = new ArrayList<>(LATENCIES);
        }
        if (copy.isEmpty()) {
            return 0;
        }
        Collections.sort(copy);
        int idx = (int) Math.ceil(p / 100.0 * copy.size()) - 1;
        return copy.get(Math.max(0, Math.min(idx, copy.size() - 1)));
    }

    static double avg() {
        synchronized (LATENCIES) {
            long sum = 0;
            for (long v : LATENCIES) {
                sum += v;
            }
            return LATENCIES.isEmpty() ? 0 : (double) sum / LATENCIES.size();
        }
    }

    static String maskPhone(String phone) {
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }

    static String maskToken(String token) {
        return token.length() <= 10 ? "***" : token.substring(0, 6) + "..." + token.substring(token.length() - 4);
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 为什么"数据库连不上"应该让 `readiness` 失败而不是 `liveness` 失败？如果配反了线上会出现什么现象？
 * 2. 设计一个 traceId 的传递方案：网关生成 -> 服务 A -> 通过 HTTP 调服务 B -> B 写日志。说明 Header 名称和异步线程里怎么保留。
 * 3. 日志里打印了用户手机号和完整 token，会带来哪些合规与安全风险？给出脱敏函数应该遵守的两条原则。
 * ============================================================
 */
