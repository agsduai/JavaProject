/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：60-11
 * 课名：综合案例：一次 HTTP 请求的框架穿越
 * ------------------------------------------------------------
 * 知识点：
 *   - **把前六课串成一条完整链路**：**Tomcat 连接器**接收请求（60-08）-> **Spring MVC DispatcherServlet** 路由 -> **事务代理**开启事务（60-05）-> **业务 Bean** 执行（60-02 的 IOC 装配 + 60-03 的生命周期已就绪）-> **AOP 切面**记录日志与指标（60-04）-> **MyBatis Mapper 代理**查库（60-09）-> **日志**全链路记录（60-10）-> **Netty**（若走 RPC 或网关转发，60-06/60-07）——**一个请求穿过了六层框架机制**。
 *   - **"穿越"的价值是排障定位**：接口慢 -> 是 Tomcat 线程排队（60-08）、事务持有过长（60-05）、AOP 链过深（60-04）、还是 MyBatis N+1（60-09）？**知道每一层在哪、怎么观测，才能逐层排除**。
 *   - **观测点的分布**：连接器（线程池水位/排队）、容器（URL 耗时）、事务（连接持有时间）、AOP（切面耗时）、MyBatis（SQL 耗时与次数）、日志（trace 串联）——**42 章的三支柱在这些框架层各有对应的埋点**。
 *   - **框架能力与代价的对照**：IOC（解耦 vs 启动耗时与"魔法感"）、AOP（横切复用 vs 自调用失效与代理开销）、事务（声明式 vs 传播行为误配）、ORM（易用 vs 缓存不一致与 N+1）——**理解代价才能正确使用**（52-05 权衡思想）。
 *   - 易错点：排障只看应用日志不看框架层指标（不知道线程池满了）；AOP 链过深（几十个切面，性能与栈深度双伤）；事务范围套住远程调用（60-05 反模式）；MyBatis N+1（循环里查库）。
 * ============================================================
 */

package com.heima.ch60;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class FrameworkJourney {
    record Stage(String layer, String action, long costMs, String watching) {
    }

    /** 模拟一次请求穿过六层框架 */
    static List<Stage> journey(boolean slowSql, boolean deepAop) {
        List<Stage> stages = new ArrayList<>();
        stages.add(new Stage("Tomcat 连接器", "线程池分配线程 + 解析 HTTP", 2,
                "线程池水位/排队（60-08）"));
        stages.add(new Stage("DispatcherServlet", "URL 匹配 HandlerMapping", 1, "路由耗时"));
        stages.add(new Stage("AOP 代理链", "事务开启 + 日志 + 指标" + (deepAop ? "（20 个切面）" : "（3 个切面）"),
                deepAop ? 8 : 1, "切面链耗时（60-04）"));
        stages.add(new Stage("事务", "获取数据库连接", 3, "连接持有时间（60-05）"));
        stages.add(new Stage("业务 Bean", "执行领域逻辑", 5, "业务耗时（60-02）"));
        stages.add(new Stage("MyBatis", slowSql ? "SELECT（N+1，查 50 次）" : "SELECT（1 次，参数绑定）",
                slowSql ? 120 : 4, "SQL 次数与耗时（60-09）"));
        stages.add(new Stage("事务提交", "提交并释放连接", 2, "提交耗时"));
        stages.add(new Stage("AOP 后置", "记录耗时 + 上报指标", 1, "后置链（60-04）"));
        stages.add(new Stage("日志落盘", "异步队列 -> 后台写入", 1, "队列积压（60-10）"));
        return stages;
    }

    static void report(List<Stage> stages) {
        long total = stages.stream().mapToLong(Stage::costMs).sum();
        System.out.println("【请求耗时分解】总耗时 " + total + "ms");
        for (Stage s : stages) {
            double pct = total == 0 ? 0 : s.costMs() * 100.0 / total;
            System.out.printf("  %-16s %4dms %5.1f%%  %-28s 观测点：%s%n",
                    s.layer(), s.costMs(), pct, s.action(), s.watching());
        }
        String bottleneck = stages.stream()
                .max((a, b) -> Long.compare(a.costMs(), b.costMs()))
                .map(Stage::layer).orElse("-");
        System.out.println("  瓶颈层：" + bottleneck + " -> 按观测点定位再优化");
        System.out.println();
    }

    public static void main(String[] args) {
        System.out.println("【正常请求】");
        report(journey(false, false));
        System.out.println("【异常请求：N+1 + 切面过深】");
        report(journey(true, true));

        Map<String, String> lessons = new LinkedHashMap<>();
        lessons.put("IOC", "解耦的代价是启动耗时与隐式装配（60-02/03）");
        lessons.put("AOP", "复用横切逻辑的代价是自调用失效与代理开销（60-04）");
        lessons.put("事务", "声明式的代价是传播行为误配与长事务（60-05）");
        lessons.put("Netty", "免锁的代价是 EventLoop 上不能阻塞（60-06/07）");
        lessons.put("ORM", "易用的代价是缓存不一致与 N+1（60-09）");
        lessons.put("日志", "异步的代价是丢尾与 MDC 断链（60-10）");
        System.out.println("【框架能力与代价对照】");
        lessons.forEach((k, v) -> System.out.printf("  %-6s %s%n", k, v));
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 在真实项目里给一次请求打全链路耗时埋点（连接器/切面/事务/SQL/日志），输出分解报告并找出瓶颈层。
 * 2. 复现一个 N+1 查询（循环里查库），用 MyBatis 插件统计 SQL 次数，然后用 join 或批量查询优化并对比。
 * 3. 思考题：为什么"理解框架代价"比"记住框架用法"更重要？结合本章六条"能力与代价"与 52-05 权衡框架写出你的理解。
 * ============================================================
 */
