/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：30-10
 * 课名：性能压测与瓶颈定位
 * ------------------------------------------------------------
 * 知识点：
 *   - 三个核心指标：**吞吐量**（QPS/TPS）、**延迟**（P50/P95/P99/P999，绝不能只看平均值）、**错误率**。平均值会把长尾藏起来，用户感受到的是 P99。
 *   - **利特尔法则**：`并发数 = QPS × 平均延迟`。反过来说，20 个连接、每个请求 50ms，理论 QPS 上限就是 400；想提到 1000 就必须加连接或降延迟。
 *   - **利用率与排队**：服务利用率 ρ 越接近 1，排队延迟非线性爆炸（近似 `响应时间 = 服务时间 / (1 - ρ)`）。所以生产别把下游压到 80% 以上。
 *   - **阿姆达尔定律**：只优化占 20% 耗时的模块，整体最多快 1.25 倍；优化前先做**耗时分解**（CPU、IO、锁等待、序列化、网络）。
 *   - 压测要看**拐点**：并发往上加，QPS 不再涨而 P99 开始飙升的那个点，就是系统容量上限。
 *   - 瓶颈定位顺序：**先看是不是压测机自己打满**（CPU/带宽/端口耗尽）→ 再看应用（线程池满、GC、锁）→ 再看中间件（连接池、慢 SQL、Redis 大 key）→ 最后看下游。
 *   - 易错：只压不监控（不知道瓶颈在哪一层）；用平均延迟下结论；压测数据和线上数据量差几个数量级（索引命中率完全不同）。
 * ============================================================
 */

package com.heima.ch30;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

// ==================== 示例代码 ====================

public class PerfTuning {
    public static void main(String[] args) {
        System.out.println("=== 1. 延迟分布：平均值会骗人 ===");
        long[] latencies = {
                12, 13, 12, 14, 13, 12, 15, 13, 12, 14,
                13, 12, 14, 13, 12, 13, 14, 12, 13, 14,
                15, 13, 12, 14, 13, 12, 13, 14, 13, 12,
                16, 14, 13, 15, 14, 13, 900, 1200, 1500, 2100
        };
        long[] sorted = latencies.clone();
        Arrays.sort(sorted);
        double avg = Arrays.stream(latencies).average().orElse(0);
        System.out.printf("  样本 %d 个，平均 %.1fms%n", latencies.length, avg);
        System.out.printf("  P50=%dms  P90=%dms  P95=%dms  P99=%dms  Max=%dms%n",
                pct(sorted, 50), pct(sorted, 90), pct(sorted, 95), pct(sorted, 99), sorted[sorted.length - 1]);
        System.out.println("  平均 65ms 看着还行，但 P99 已到 1500ms -> 1% 的用户在骂人");

        System.out.println();
        System.out.println("=== 2. 利特尔法则：并发、QPS、延迟的关系 ===");
        int[] concurrency = {10, 20, 50, 100};
        int latencyMs = 50;
        System.out.println("  假设单请求平均延迟 " + latencyMs + "ms");
        System.out.printf("  %-10s %-12s %s%n", "并发数", "理论QPS", "说明");
        for (int c : concurrency) {
            int qps = c * 1000 / latencyMs;
            System.out.printf("  %-10d %-12d %s%n", c, qps, c >= 50 ? "需要更多连接/更短延迟" : "受连接数直接限制");
        }
        System.out.println("  目标 QPS=1000 且延迟 50ms -> 至少需要 " + (1000 * latencyMs / 1000) + " 个并发");
        System.out.println("  连接池只有 20 个时，QPS 上限就是 " + (20 * 1000 / latencyMs) + "，调线程数没用");

        System.out.println();
        System.out.println("=== 3. 利用率与排队延迟（为什么不能压到 90%）===");
        System.out.printf("  %-10s %-14s %-14s %s%n", "利用率", "排队倍数", "响应时间", "感受");
        for (double rho : new double[]{0.3, 0.5, 0.7, 0.8, 0.9, 0.95}) {
            double factor = 1 / (1 - rho);
            double rt = latencyMs * factor;
            String feel = rho < 0.6 ? "轻松" : rho < 0.8 ? "正常" : rho < 0.9 ? "开始抖" : "雪崩边缘";
            System.out.printf("  %-10.0f%% %-14.1f %-14.0f %s%n", rho * 100, factor, rt, feel);
        }
        System.out.println("  70% -> 90% 利用率，延迟翻了近 3 倍；这就是压测拐点的来源");

        System.out.println();
        System.out.println("=== 4. 阿姆达尔定律：优化收益上限 ===");
        double[] parts = {45, 25, 15, 10, 5};
        String[] names = {"数据库查询", "业务计算", "序列化", "网络传输", "日志与其他"};
        System.out.printf("  %-14s %8s %s%n", "环节", "耗时占比", "优化 50% 后整体收益");
        for (int i = 0; i < parts.length; i++) {
            double gain = parts[i] / 2;
            double newTotal = 100 - gain;
            System.out.printf("  %-14s %7.0f%% %14.1f%%%n", names[i], parts[i], (100 - newTotal));
        }
        System.out.println("  优化最大的那块（数据库 45%），也只能把总耗时降 22.5%");
        System.out.println("  想快一倍：要么把数据库耗时砍到 10% 以内，要么上缓存绕过它");

        System.out.println();
        System.out.println("=== 5. 压测曲线：找拐点 ===");
        int[] qpsAt = {500, 900, 1500, 1800, 1850, 1860, 1200};
        int[] p99At = {40, 45, 60, 90, 180, 450, 2000};
        int[] errAt = {0, 0, 0, 0, 0, 1, 12};
        System.out.printf("  %-8s %-8s %-8s %-8s %s%n", "并发", "QPS", "P99ms", "错误%", "判读");
        int[] conc = {10, 20, 40, 60, 80, 100, 120};
        for (int i = 0; i < conc.length; i++) {
            String verdict = errAt[i] > 0 ? "已过载" : p99At[i] > 150 ? "接近拐点" : "健康";
            System.out.printf("  %-8d %-8d %-8d %-8d %s%n", conc[i], qpsAt[i], p99At[i], errAt[i], verdict);
        }
        System.out.println("  拐点在并发 60~80 之间：QPS 不再涨、P99 抬头 -> 这就是容量上限");
        System.out.println("  线上留 30%~40% 余量，所以这个系统对外报 1200 QPS 比较安全");

        System.out.println();
        System.out.println("=== 6. 压测前的检查清单 ===");
        List<String> checklist = new ArrayList<>(List.of(
                "压测机自身 CPU/带宽/文件句柄是否够（别把施压方压死）",
                "应用线程池、连接池、队列长度是否已配置且可观测",
                "GC 日志已开，压测期间盯着 Full GC",
                "数据库数据量与线上同量级，索引命中率接近",
                "限流、熔断阈值先调到不触发，否则压出来的是限流值",
                "准备回滚方案：压完把参数改回去"
        ));
        for (int i = 0; i < checklist.size(); i++) {
            System.out.println("  " + (i + 1) + ". " + checklist.get(i));
        }
    }

    static long pct(long[] sorted, int p) {
        int idx = (int) Math.ceil(p / 100.0 * sorted.length) - 1;
        idx = Math.max(0, Math.min(sorted.length - 1, idx));
        return sorted[idx];
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 给示例补 10 个 3000ms 以上的样本，观察 P95/P99/平均各变化多少，说明为什么压测报告必须给分位数。
 * 2. 某接口 QPS 800、平均延迟 40ms，连接池只有 20 个，够不够？用利特尔法则算一遍。
 * 3. 一个请求里数据库占 60%、计算占 30%、网络占 10%，把数据库换成缓存后耗时降到原来的多少？用阿姆达尔定律解释。
 * ============================================================
 */
