/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：81-10
 * 课名：缓存监控与命中率治理
 * ------------------------------------------------------------
 * 知识点：
 *   - **四个必看指标**：**命中率**（hits/(hits+misses)）、**回源 QPS**、**缓存 RT（P99）**、**内存使用率与大 key 数量**——**命中率掉了就是事故前兆**。
 *   - **命中率要分层看**：**整体命中率**会掩盖问题，**按 key 前缀/业务线分组**才能定位「哪类数据在打库」（如 `sku:*` 96%、`order:*` 38%）。
 *   - **命中率低的四个原因**：**TTL 太短**（频繁过期）、**key 设计太细**（同一对象拆成多个 key，回填不完整）、**缓存容量不足**（LRU 把热 key 挤掉）、**写入路径误删**（写一次删一片）。
 *   - **大 key 与热 key**：**大 key**（单个 value 几 MB）会拖慢整个实例、**热 key**（单 key QPS 过高）会打爆单分片——**都要靠采样统计提前发现**。
 *   - **监控落地**：**应用侧埋点（命中/未命中/回源耗时）+ 缓存侧指标（内存/连接/慢查询）+ 告警（命中率跌破阈值、回源 QPS 突增）**。
 *   - 易错点：只监控缓存 RT 不看命中率（RT 好但一直回源）；命中率按总量算不分业务（掩盖局部问题）；不做容量水位告警（OOM 才发现）。
 * ============================================================
 */

package com.heima.ch81;

// ==================== 示例代码 ====================

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

public class CacheMetrics {
    static class Counter {
        long hits;
        long misses;
        long totalNanos;

        void hit(long nanos) {
            hits++;
            totalNanos += nanos;
        }

        void miss(long nanos) {
            misses++;
            totalNanos += nanos;
        }

        long total() {
            return hits + misses;
        }

        double hitRate() {
            return total() == 0 ? 0 : (double) hits / total();
        }

        double avgMs() {
            return total() == 0 ? 0 : totalNanos / 1_000_000.0 / total();
        }
    }

    static void report(String name, Counter c, double warnThreshold) {
        String verdict = c.hitRate() < warnThreshold ? "告警：命中率低于 " + (int) (warnThreshold * 100) + "%"
                : "正常";
        System.out.printf("  %-12s 请求=%6d 命中率=%5.1f%% 平均=%.2fms  %s%n",
                name, c.total(), c.hitRate() * 100, c.avgMs(), verdict);
    }

    public static void main(String[] args) {
        Map<String, Counter> byPrefix = new TreeMap<>();
        byPrefix.put("sku:", new Counter());
        byPrefix.put("order:", new Counter());
        byPrefix.put("user:", new Counter());
        byPrefix.put("cfg:", new Counter());

        // 模拟真实流量：sku 命中高、order 命中低（TTL 太短）、user 中等、cfg 极高
        double[] hitRates = {0.96, 0.38, 0.82, 0.995};
        String[] prefixes = {"sku:", "order:", "user:", "cfg:"};
        int[] volumes = {50_000, 20_000, 15_000, 5_000};

        for (int i = 0; i < prefixes.length; i++) {
            Counter c = byPrefix.get(prefixes[i]);
            for (int n = 0; n < volumes[i]; n++) {
                boolean hit = (n % 1000) < hitRates[i] * 1000;
                long cost = hit ? 300_000L : 8_000_000L;      // 命中 0.3ms，未命中 8ms
                if (hit) {
                    c.hit(cost);
                } else {
                    c.miss(cost);
                }
            }
        }

        System.out.println("【按 key 前缀分组】");
        Counter all = new Counter();
        for (Map.Entry<String, Counter> e : byPrefix.entrySet()) {
            report(e.getKey(), e.getValue(), 0.90);
            all.hits += e.getValue().hits;
            all.misses += e.getValue().misses;
            all.totalNanos += e.getValue().totalNanos;
        }
        System.out.println();
        System.out.println("【整体】");
        report("ALL", all, 0.90);
        System.out.println("  -> 整体 81% 只说明「有问题」，说不出是谁：分前缀才看到 order 38%、user 82%");

        System.out.println();
        System.out.println("【order 命中率低的排查清单】");
        Map<String, String> checks = new LinkedHashMap<>();
        checks.put("TTL", "order 列表 TTL=30s，太短 -> 调到 5 分钟 + 抖动");
        checks.put("key 粒度", "把 order:list:user:1 拆成 3 个 key，回填不完整");
        checks.put("容量", "实例内存 80%，LRU 把 order 挤掉 -> 扩内存或降 TTL");
        checks.put("写路径", "每次下单删 order:list:*，误删一片 -> 改为精确删除");
        checks.forEach((k, v) -> System.out.printf("  %-10s %s%n", k, v));

        System.out.println();
        System.out.println("【告警规则】命中率 < 90% 持续 5 分钟 / 回源 QPS 突增 3 倍 / 内存 > 85% / 大 key > 1MB");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 把 order 的 TTL 从 30 秒改成 300 秒，重新计算命中率，说明 TTL 与命中率的定量关系。
 * 2. 给 report 加 P99 统计（用数组存每次耗时），观察命中率正常但 P99 很差的情况。
 * 3. 思考题：为什么「整体命中率」会掩盖问题？举一个两个业务线命中率互相掩盖的例子。
 * ============================================================
 */

