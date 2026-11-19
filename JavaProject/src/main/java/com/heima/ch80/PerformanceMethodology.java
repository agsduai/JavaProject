/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：80-01
 * 课名：性能工程方法论
 * ------------------------------------------------------------
 * 知识点：
 *   - **性能的三个维度**：**吞吐（QPS/TPS）**、**延迟（P50/P99/P999）**、**错误率**——**三者必须一起看**：只提吞吐会把延迟打爆，只看平均延迟会掩盖长尾（80-03）。
 *   - **USE 方法（资源视角）**：**Utilization（使用率）**、**Saturation（饱和度/排队）**、**Errors（错误）**——**对 CPU/内存/磁盘/网络逐个检查**，**饱和比使用率更早暴露瓶颈**。
 *   - **RED 方法（服务视角）**：**Rate（请求速率）**、**Errors（错误率）**、**Duration（延迟分布）**——**微服务/接口层用 RED，主机/资源层用 USE**。
 *   - **测量先于优化**：**没有基线就没有"变快"**；**先定位瓶颈（CPU/IO/锁/下游），再改**——**凭感觉优化往往改错地方**（80-05 的火焰图）。
 *   - 与 30/56 章的关系：30 章 JVM 调优、56 章稳定性容量，**本章讲"怎么系统地测量与定位"**——**工具、方法、指标**三件套。
 *   - 易错点：只看平均值（长尾被平均掉）；只压测不监控（不知道瓶颈）；把"CPU 高"当根因（可能是在自旋/GC）；不设错误率阈值（压测把下游打挂）。
 * ============================================================
 */

package com.heima.ch80;

import java.util.LinkedHashMap;
import java.util.Map;

// ==================== 示例代码 ====================

public class PerformanceMethodology {
    record Resource(String name, double utilization, double saturation,
                    long errors, double queueDepth) {
    }

    record ServiceMetric(String name, double qps, double errorRate,
                         double p50Ms, double p99Ms, double p999Ms) {
    }

    static void use(java.util.List<Resource> resources) {
        System.out.println("【USE：资源视角】");
        System.out.printf("  %-8s %-10s %-12s %-8s %-8s %s%n",
                "资源", "使用率", "饱和度", "错误", "排队", "判断");
        for (Resource r : resources) {
            String verdict;
            if (r.errors() > 0) {
                verdict = "错误：先查故障";
            } else if (r.saturation() > 0.8 || r.queueDepth() > 10) {
                verdict = "饱和：瓶颈在这里";
            } else if (r.utilization() > 0.8) {
                verdict = "高使用率：接近瓶颈";
            } else {
                verdict = "正常";
            }
            System.out.printf("  %-8s %8.0f%% %11.0f%% %8d %8.0f %s%n",
                    r.name(), r.utilization() * 100, r.saturation() * 100,
                    r.errors(), r.queueDepth(), verdict);
        }
        System.out.println();
    }

    static void red(java.util.List<ServiceMetric> services) {
        System.out.println("【RED：服务视角】");
        for (ServiceMetric s : services) {
            System.out.printf("  %-10s QPS=%-8.0f 错误率=%-5.2f%% P50=%-6.1f P99=%-7.1f P999=%.1f%n",
                    s.name(), s.qps(), s.errorRate() * 100,
                    s.p50Ms(), s.p99Ms(), s.p999Ms());
        }
        System.out.println();
    }

    public static void main(String[] args) {
        use(java.util.List.of(
                new Resource("CPU", 0.72, 0.30, 0, 2),
                new Resource("内存", 0.85, 0.10, 0, 0),
                new Resource("磁盘", 0.95, 0.90, 0, 45),
                new Resource("网络", 0.40, 0.05, 12, 1)));

        red(java.util.List.of(
                new ServiceMetric("gateway", 12_000, 0.02, 8, 45, 120),
                new ServiceMetric("order", 3_500, 0.10, 20, 180, 900),
                new ServiceMetric("payment", 800, 0.50, 35, 420, 2_000)));

        System.out.println("【定位顺序】");
        String[][] steps = {
                {"1. 看 RED", "哪个接口错误/延迟异常", "先找用户可感知的问题"},
                {"2. 看 USE", "哪个资源饱和", "CPU/磁盘/连接池/线程池"},
                {"3. 看依赖", "下游超时/重试", "重试会放大流量（80-04）"},
                {"4. 看长尾", "P99/P999 与 P50 的差距", "长尾常来自 GC/锁/慢查询"},
        };
        for (String[] s : steps) {
            System.out.printf("  %-10s %-26s %s%n", s[0], s[1], s[2]);
        }

        System.out.println();
        Map<String, String> conclusion = new LinkedHashMap<>();
        conclusion.put("磁盘", "使用率 95% + 排队 45 -> 第一瓶颈");
        conclusion.put("payment", "错误率 0.5% + P999 2s -> 下游/锁问题");
        conclusion.put("CPU", "72% 不是瓶颈，先别扩容");
        System.out.println("【结论】");
        conclusion.forEach((k, v) -> System.out.printf("  %-8s %s%n", k, v));
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 给你的服务画一张 USE 表（CPU/内存/磁盘/网络/连接池），标出饱和项。
 * 2. 用 RED 指标对比三个接口，判断应该先优化哪一个。
 * 3. 思考题：为什么"CPU 使用率高"不一定是瓶颈？从"自旋/GC/系统调用"举反例。
 * ============================================================
 */
