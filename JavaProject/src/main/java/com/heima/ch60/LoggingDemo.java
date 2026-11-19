/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：60-10
 * 课名：日志框架：SLF4J 桥接与异步
 * ------------------------------------------------------------
 * 知识点：
 *   - **日志生态的三层**：**门面（SLF4J）**——应用只依赖它；**绑定（Logback/Log4j2）**——真正实现；**桥接（jul-to-slf4j、log4j-over-slf4j）**——把老框架的调用转发到新实现。**依赖里出现多个绑定是最常见的日志故障**（SLF4J 打印 "Class path contains multiple SLF4J bindings" 并随机选一个）。
 *   - **日志级别的语义**（42-02 的框架层解释）：ERROR（需人处理）、WARN（偏离预期自愈）、INFO（关键业务事件）、DEBUG（排查细节）、TRACE（框架内部）——**生产开 DEBUG 是性能与成本的常见事故源**。
 *   - **异步日志（AsyncAppender）**：日志写入丢到队列由后台线程落盘——**减少业务线程的 IO 阻塞**；**代价**：队列满时的丢弃策略（丢日志 vs 阻塞业务，必须显式配置），**进程崩溃时队列里的日志丢失**（比起同步日志，"最后几条丢失"是可接受的技术取舍）。
 *   - **参数化日志（`log.info("order={}", id)`）**：**只有级别开启时才求值参数**（对比字符串拼接"无论级别都拼接"）——**这是日志性能的第一条纪律**（高频路径上字符串拼接能吃掉可观 CPU，42-02 的日志量治理在此有性能解释）。
 *   - **MDC（Mapped Diagnostic Context）**：线程绑定的上下文字段（trace_id/userId）自动进每条日志——**异步日志与线程池下 MDC 会丢**（需要 `TaskDecorator` 传递，50-05/55-03 的 ThreadLocal 审计同源）。
 *   - 易错点：多个日志绑定共存（行为不确定）；`log.debug("x" + heavy())` 字符串拼接（级别关了也执行）；异步队列无界（内存涨）；MDC 在异步线程丢失（trace 断裂 42-07）。
 * ============================================================
 */

package com.heima.ch60;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class LoggingDemo {
    enum Level {
        TRACE, DEBUG, INFO, WARN, ERROR;

        boolean enabled(Level configured) {
            return this.ordinal() >= configured.ordinal();
        }
    }

    /** 参数化日志：级别关闭时不求值 */
    static class Logger {
        final Level configured;
        final List<String> records = new ArrayList<>();
        final List<String> evaluated = new ArrayList<>();

        Logger(Level configured) {
            this.configured = configured;
        }

        void info(String template, Object... args) {
            if (!Level.INFO.enabled(configured)) {
                return;
            }
            records.add(render(template, args));
            for (Object a : args) {
                evaluated.add(String.valueOf(a));
            }
        }

        void debug(String template, Object... args) {
            if (!Level.DEBUG.enabled(configured)) {
                return;
            }
            records.add(render(template, args));
            for (Object a : args) {
                evaluated.add(String.valueOf(a));
            }
        }

        String render(String template, Object... args) {
            String out = template;
            for (Object a : args) {
                out = out.replaceFirst("\\{}", java.util.regex.Matcher.quoteReplacement(String.valueOf(a)));
            }
            return out;
        }
    }

    /** 代价昂贵的参数：用 Supplier 延迟求值 */
    static String heavyToString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            sb.append(i);
        }
        return "heavy(" + sb.length() + ")";
    }

    /** 异步日志队列：满时的丢弃策略 */
    static void asyncQueue(String policy, int capacity, int produced) {
        int queued = Math.min(capacity, produced);
        int dropped = Math.max(0, produced - capacity);
        String result = switch (policy) {
            case "drop" -> "丢弃 " + dropped + " 条（业务不阻塞）";
            case "block" -> "业务线程阻塞等待队列";
            default -> "队列无界（内存风险）";
        };
        System.out.printf("  容量=%d 产生=%d 入队=%d -> %s%n", capacity, produced, queued, result);
    }

    public static void main(String[] args) {
        System.out.println("【参数化日志：级别关闭时不求值】");
        var logger = new Logger(Level.INFO);
        logger.debug("debug with {}", heavyToString());
        logger.info("order={} amount={}", "9001", 6900);
        System.out.println("  INFO 日志：" + logger.records);
        System.out.println("  实际渲染的参数：" + logger.evaluated
                + "（debug 未渲染；注意参数表达式本身仍会被求值，彻底惰性要用 Supplier）");
        System.out.println();

        System.out.println("【异步队列策略】");
        asyncQueue("drop", 1000, 5000);
        asyncQueue("block", 1000, 5000);
        System.out.println();

        System.out.println("【常见日志故障】");
        Map<String, String> faults = new LinkedHashMap<>();
        faults.put("多个 SLF4J 绑定", "依赖里同时有 logback 与 log4j-slf4j-impl -> 随机生效");
        faults.put("字符串拼接", "log.debug(\"x\" + heavy()) -> 级别关闭也执行");
        faults.put("MDC 丢失", "线程池/异步日志 -> trace_id 断链（42-07）");
        faults.put("级别误配", "生产开 DEBUG -> 磁盘与 IO 压力（42-03）");
        faults.forEach((k, v) -> System.out.printf("  %-14s %s%n", k, v));
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 用 `mvn dependency:tree` 检查一个项目的日志依赖，找出所有 SLF4J 绑定与桥接包，输出"应保留/应排除"清单。
 * 2. 实现"日志性能对比"：同一循环里分别用字符串拼接与参数化日志（DEBUG 关闭），统计耗时差异。
 * 3. 思考题：为什么"异步日志丢最后几条"是可接受的？从吞吐收益、崩溃场景概率、日志用途（排障 vs 审计）三个角度分析。
 * ============================================================
 */
