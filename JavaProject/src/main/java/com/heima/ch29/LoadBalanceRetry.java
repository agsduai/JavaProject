/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：29-05
 * 课名：负载均衡与重试策略
 * ------------------------------------------------------------
 * 知识点：
 *   - 客户端负载均衡（Spring Cloud LoadBalancer、Dubbo）在调用方本地选实例，少一跳、更快；服务端负载均衡（Nginx、LVS）集中转发，运维简单。
 *   - 常见算法：**轮询**（均匀，适合实例同质）、**加权轮询**（按机器配置分配）、**最少连接/最小活跃**（适合长耗时请求）、**一致性哈希**（同一 key 落同一实例，利于缓存命中）、**随机**（简单，大样本下接近均匀）。
 *   - 健康实例才参与分配：注册中心标记下线的实例要立刻从本地列表中摘除，否则会持续打到挂掉的节点。
 *   - 重试要区分错误类型：**连接失败**可以重试另一台实例；**业务异常（4xx）**不该重试；**超时**要看接口是否幂等。
 *   - 重试策略：固定间隔、指数退避、带抖动的退避（避免所有客户端同时重试造成"重试风暴"）。
 *   - 熔断与重试配合：重试次数用尽后触发熔断，直接把后续请求快速失败，给下游恢复时间。
 *   - 易错：重试仍打同一台实例（没有换节点）；重试次数配 5 次且每层都重试，实际放大 5^3 倍。
 * ============================================================
 */

package com.heima.ch29;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

// ==================== 示例代码 ====================

public class LoadBalanceRetry {
    static final List<String> HEALTHY = new ArrayList<>(List.of("10.0.0.11", "10.0.0.12", "10.0.0.13"));
    static final List<String> ALL = new ArrayList<>(List.of("10.0.0.11", "10.0.0.12", "10.0.0.13", "10.0.0.14"));

    static String roundRobin(int[] cursor) {
        String pick = HEALTHY.get(cursor[0] % HEALTHY.size());
        cursor[0]++;
        return pick;
    }

    static String leastActive(int[] active) {
        int best = 0;
        for (int i = 1; i < active.length; i++) {
            if (active[i] < active[best]) {
                best = i;
            }
        }
        return HEALTHY.get(best);
    }

    static String consistentHash(String key) {
        int idx = Math.floorMod(key.hashCode(), HEALTHY.size());
        return HEALTHY.get(idx);
    }

    public static void main(String[] args) {
        System.out.println("=== 轮询 ===");
        int[] cursor = {0};
        Map<String, Integer> stat = new LinkedHashMap<>();
        for (int i = 0; i < 9; i++) {
            String pick = roundRobin(cursor);
            stat.merge(pick, 1, Integer::sum);
        }
        System.out.println("  9 次分配：" + stat);

        System.out.println();
        System.out.println("=== 最少活跃连接 ===");
        int[] active = {7, 2, 5};
        for (int i = 0; i < 3; i++) {
            String pick = leastActive(active);
            int idx = HEALTHY.indexOf(pick);
            active[idx]++;
            System.out.println("  选 " + pick + "（当前活跃 " + (active[idx] - 1) + " -> " + active[idx] + "）");
        }

        System.out.println();
        System.out.println("=== 一致性哈希：同一用户固定落同一实例（利于本地缓存）===");
        for (String user : List.of("u1001", "u1002", "u1001", "u1003", "u1002")) {
            System.out.println("  " + user + " -> " + consistentHash(user));
        }

        System.out.println();
        System.out.println("=== 摘除不健康实例 ===");
        System.out.println("  注册中心标记 10.0.0.13 下线后，本地列表变为 " + HEALTHY);
        System.out.println("  仍在 ALL 里的 10.0.0.14 未注册，不参与分配：" + ALL);

        System.out.println();
        System.out.println("=== 重试：换节点 + 退避 + 抖动 ===");
        Random random = new Random(42);
        String[] nodes = {"10.0.0.11", "10.0.0.12", "10.0.0.13"};
        for (int attempt = 1; attempt <= 3; attempt++) {
            String node = nodes[attempt - 1];
            boolean ok = attempt == 3;
            long backoff = (long) (100 * Math.pow(2, attempt - 1) * (0.5 + random.nextDouble()));
            System.out.println("  第 " + attempt + " 次 -> " + node + "，结果=" + (ok ? "成功" : "超时")
                    + (ok ? "" : "，退避 " + backoff + "ms（含抖动）"));
            if (ok) {
                break;
            }
        }

        System.out.println();
        System.out.println("=== 重试放大计算 ===");
        int layers = 3;
        int retries = 3;
        long amplified = (long) Math.pow(retries + 1, layers);
        System.out.println("  " + layers + " 层调用每层重试 " + retries + " 次 => 最坏放大 " + amplified + " 倍");
        System.out.println("  对策：只在最外层重试，内层超时后快速失败");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现加权轮询：实例 A/B/C 权重 5/3/2，输出 10 次分配结果并验证比例。
 * 2. 说明"每层都重试 3 次、共 3 层"为什么会放大到 64 倍，给出治理方案。
 * 3. 一致性哈希在实例扩容时会迁移多少 key？普通取模又会迁移多少？
 * ============================================================
 */
