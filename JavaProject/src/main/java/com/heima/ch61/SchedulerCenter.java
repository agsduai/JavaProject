/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：61-02
 * 课名：调度中心架构：注册、发现与故障转移
 * ------------------------------------------------------------
 * 知识点：
 *   - **执行器注册与心跳**（29-01 的调度版，但语义不同）：执行器启动时注册到调度中心（地址/支持的 jobHandler/负载），**周期性心跳**维持在线；超过阈值未心跳则标记下线——**与注册中心不同：调度中心还要记录"哪些执行器能跑哪些任务"**（路由依据）。
 *   - **路由策略**：**轮询/随机/一致性哈希**（29-04 的负载均衡）+ **分片广播**（61-03，所有执行器各跑一片）+ **故障转移**（失败重试其他执行器）+ **忙碌转移**（执行器排队过多时换一台）。
 *   - **任务派发模型**：调度中心**主动调用执行器**（HTTP/RPC，触发 -> 执行 -> 回调结果），还是**执行器拉取任务**（pull 模型）？**主动派发**时效好但需要中心知道执行器地址；**拉取**解耦但有时延——XXL-JOB 用主动派发，ElasticJob 用注册中心 + 本地触发。
 *   - **调度中心的高可用**：中心本身也要集群（45 章共识/主备），**触发时刻的重复触发防护**（同一任务同一时刻只允许一个中心触发——用分布式锁或数据库唯一约束，39/45 章思想）。
 *   - **时钟与触发精度**：cron 触发依赖"时间片扫描"（每秒扫一次到期任务）或"时间轮"（28-02）——**扫描粒度决定触发精度**（秒级扫描 vs 时间轮毫秒级）；**夏令时/闰秒**不是玩笑（跨时区业务尤其）。
 *   - 易错点：执行器心跳超时就判定死亡（网络抖动误杀，需容忍次数）；中心多实例同时触发（任务重复执行）；执行器地址硬编码（扩容要改配置）；触发时间用本地时区（跨时区业务错乱）。
 * ============================================================
 */

package com.heima.ch61;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class SchedulerCenter {
    record Executor(String id, List<String> handlers, long lastHeartbeat,
                    int runningTasks, boolean online) {
    }

    static final long TIMEOUT_MS = 30_000;

    /** 心跳判定：连续超时才摘除（容忍一次抖动） */
    static List<Executor> alive(List<Executor> all, long now) {
        List<Executor> out = new ArrayList<>();
        for (Executor e : all) {
            boolean ok = e.online() && now - e.lastHeartbeat() <= TIMEOUT_MS;
            System.out.printf("  %-8s handlers=%-18s 心跳间隔=%-6dms -> %s%n",
                    e.id(), e.handlers(), now - e.lastHeartbeat(), ok ? "在线" : "摘除");
            if (ok) {
                out.add(e);
            }
        }
        return out;
    }

    /** 路由：按 handler 过滤 + 忙碌转移（取负载最低） */
    static Executor route(List<Executor> alive, String handler) {
        return alive.stream()
                .filter(e -> e.handlers().contains(handler))
                .min((a, b) -> Integer.compare(a.runningTasks(), b.runningTasks()))
                .orElse(null);
    }

    /** 触发去重：同一任务同一时间片只允许一个中心实例触发 */
    static boolean tryTrigger(Map<String, String> triggerLocks, String jobId,
                              String slot, String centerId) {
        String key = jobId + "@" + slot;
        String holder = triggerLocks.putIfAbsent(key, centerId);
        if (holder == null) {
            System.out.printf("  中心 %s 获得触发权：%s%n", centerId, key);
            return true;
        }
        System.out.printf("  中心 %s 重复触发被拒（%s 已由 %s 触发）%n", centerId, key, holder);
        return false;
    }

    public static void main(String[] args) {
        long now = 100_000;
        List<Executor> all = new ArrayList<>(List.of(
                new Executor("exec-1", List.of("closeOrderJob"), now - 5_000, 2, true),
                new Executor("exec-2", List.of("closeOrderJob", "reportJob"), now - 28_000, 0, true),
                new Executor("exec-3", List.of("closeOrderJob"), now - 45_000, 0, true),
                new Executor("exec-4", List.of("reportJob"), now - 3_000, 5, true)));
        System.out.println("【在线判定】");
        List<Executor> online = alive(all, now);
        System.out.println();
        System.out.println("【路由（忙碌转移）】");
        Executor target = route(online, "closeOrderJob");
        System.out.println("  closeOrderJob -> " + (target == null ? "无可用执行器"
                : target.id() + "（运行中 " + target.runningTasks() + "）"));
        System.out.println();
        System.out.println("【多中心触发去重】");
        Map<String, String> locks = new LinkedHashMap<>();
        tryTrigger(locks, "closeOrderJob", "2026-11-18T02:00", "center-A");
        tryTrigger(locks, "closeOrderJob", "2026-11-18T02:00", "center-B");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现执行器注册中心：注册/心跳/超时摘除 + 连续 N 次超时才摘除，模拟"一次心跳抖动"验证不误杀。
 * 2. 实现四种路由策略（轮询/随机/最少任务/分片广播），用 5 个执行器对比任务分布均匀度。
 * 3. 思考题：为什么"主动派发"比"执行器拉取"更适合精确时刻的调度？从触发时效与时钟同步两个角度分析。
 * ============================================================
 */
