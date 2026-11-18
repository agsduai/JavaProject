/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：42-02
 * 课名：结构化日志与采集管道
 * ------------------------------------------------------------
 * 知识点：
 *   - **非结构化日志是可观测性的第一杀手**：`log.info("用户" + userId + "下单成功")` 人类能读，机器无法可靠解析——字段名漂移、格式随心情变。**结构化日志（JSON 每行一条）让日志变成可查询的数据**：字段固定、类型明确、可索引。
 *   - **一条合格日志的字段骨架**：`timestamp`（毫秒级）、`level`（DEBUG/INFO/WARN/ERROR）、`service`、`trace_id`/`span_id`、`message`（人类可读的一句话）、业务上下文（userId/orderId）、异常堆栈（error 级）。**骨架全团队统一**，业务字段按需扩展。
 *   - **级别的语义纪律**：ERROR 表示"需要人处理的异常"（打多了=狼来了）；WARN 表示"偏离预期但自动恢复"（重试成功、降级触发）；INFO 是关键业务事件（下单、支付、状态迁移）。**DEBUG 生产默认关闭，需要时动态开**（开关按请求灰度）。
 *   - **采集管道的三段**：应用输出 stdout/文件 -> 采集器（Filebeat/Fluent Bit）转发 -> 中心化存储（ES/日志平台）。**应用不直接写中心**（网络抖动不应影响业务进程），本地缓冲 + 重试由采集器负责。
 *   - **日志的上下文传播**：MDC（Mapped Diagnostic Context）把 trace_id/userId 放进线程上下文，日志框架每次输出自动带上——**避免每条日志手工拼**。跨线程（异步任务）要显式传递，漏传是 trace 断裂的常见原因。
 *   - **敏感信息治理**：日志出现密码/token/身份证 = 合规事故。规范：脱敏注解统一处理、日志扫描工具进 CI（36 章的敏感信息检查在这里落地）。
 *   - 易错点：循环里打 INFO（日志洪水）；异常被 catch 后只打 message 不打堆栈（排障断线索）；多行堆栈被采集器按行拆散（要配置多行合并）；日志级别全靠 System.out（无级别无格式不可采集）。
 * ============================================================
 */

package com.heima.ch42;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// ==================== 示例代码 ====================

public class StructuredLogging {
    /** 极简结构化日志器：演示字段骨架与 MDC 思想 */
    static class Logger {
        final String service;

        Logger(String service) {
            this.service = service;
        }

        String format(String level, String message, Map<String, Object> ctx) {
            StringBuilder sb = new StringBuilder("{");
            sb.append("\"ts\":\"").append(Instant.now()).append("\",");
            sb.append("\"level\":\"").append(level).append("\",");
            sb.append("\"service\":\"").append(service).append("\",");
            sb.append("\"trace_id\":\"").append(MDC.get("trace_id")).append("\",");
            sb.append("\"message\":\"").append(message).append("\"");
            for (Map.Entry<String, Object> e : ctx.entrySet()) {
                sb.append(",\"").append(e.getKey()).append("\":\"").append(e.getValue()).append("\"");
            }
            return sb.append("}").toString();
        }

        void info(String message, Map<String, Object> ctx) {
            emit("INFO", message, ctx);
        }

        void error(String message, Exception e, Map<String, Object> ctx) {
            ctx.put("exception", e.getClass().getSimpleName());
            emit("ERROR", message, ctx);
        }

        private void emit(String level, String message, Map<String, Object> ctx) {
            System.out.println(format(level, message, ctx));
        }
    }

    /** 线程上下文（MDC 的思想）：trace_id 自动进每条日志 */
    static class MDC {
        static final ThreadLocal<String> CTX = new ThreadLocal<>();

        static void put(String key, String value) {
            if ("trace_id".equals(key)) {
                CTX.set(value);
            }
        }

        static String get(String key) {
            return "trace_id".equals(key) ? CTX.get() : null;
        }
    }

    static void goodVsBad() {
        System.out.println("【同一次下单：拼字符串 vs 结构化】");
        Logger log = new Logger("order-service");
        MDC.put("trace_id", UUID.randomUUID().toString().substring(0, 8));
        System.out.println("  反例：log.info(\"用户1001下单成功，订单号" + 9001 + "金额69.0\")");
        System.out.println("    -> 机器无法稳定提取 userId/orderId，换个人写法就变");
        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("userId", "1001");
        ctx.put("orderId", "9001");
        ctx.put("amount", "69.0");
        log.info("下单成功", ctx);
        System.out.println("  正例如上：字段固定，可按 userId/orderId 精确检索");
        System.out.println();
    }

    static void levelDiscipline() {
        System.out.println("【级别的语义：ERROR 是给值班看的】");
        record Event(String event, String level, String why) {
        }
        List<Event> events = List.of(
                new Event("支付回调验签失败", "ERROR", "资金相关，需要人工介入"),
                new Event("调用库存服务超时后重试成功", "WARN", "偏离预期但自愈"),
                new Event("订单创建", "INFO", "关键业务事件"),
                new Event("循环里的每条记录处理", "DEBUG", "生产默认关"));
        for (Event e : events) {
            System.out.printf("  %-16s %-5s %s%n", e.event(), e.level(), e.why());
        }
        System.out.println("  ERROR 频率应接近 0：日志告警群里 ERROR 刷屏 = 无人再看");
        System.out.println();
    }

    static void pipeline() {
        System.out.println("【采集管道：应用不直连中心】");
        List<String> path = List.of(
                "应用 -> stdout（容器环境）或滚动文件",
                "采集器（Filebeat/Fluent Bit）tail + 多行合并（堆栈成一条）",
                "本地缓冲队列：中心不可达时先落盘，恢复后重试",
                "中心存储（ES）按天索引，冷数据转归档");
        for (String s : path) {
            System.out.println("  " + s);
        }
        System.out.println("  应用进程只管快速写出（异步 appender），采集的可靠性归采集器");
    }

    public static void main(String[] args) {
        goodVsBad();
        levelDiscipline();
        pipeline();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 把你项目里 5 条拼字符串日志改造成结构化日志（MDC + 固定字段），验证按 orderId 检索的效率差异。
 * 2. 实现"异步线程丢失 trace_id"的修复：用 TaskDecorator/手动传递让线程池任务也能带上 trace_id，并写一个测试证明。
 * 3. 思考题：为什么应用进程不应直接把日志推给中心存储？从进程稳定性与采集可靠性分离的角度分析。
 * ============================================================
 */
