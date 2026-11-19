/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：66-09
 * 课名：Agent 的性能与稳定性
 * ------------------------------------------------------------
 * 知识点：
 *   - **Agent 的两类开销**：**加载期开销**（Transformer 对每个类执行一次：解析字节码 + 改写 + 重算栈帧——**类多时拖慢启动**）、**运行期开销**（插入的埋点代码：计时/ThreadLocal/上报——**调用量大时吃 CPU**）。
 *   - **加载期优化的三条**：**快速过滤**（先字符串前缀判断，命不中直接返回 null）、**惰性增强**（只增指定类，不全量扫）、**缓存**（同一类只解析一次）——**一个应用上万类，每次多 1ms 就是 10 秒启动延迟**。
 *   - **运行期优化的三条**：**采样**（42-08）、**异步上报**（队列 + 批量，66-06）、**埋点裁剪**（只记关键方法，不记全部）。
 *   - **稳定性是 Agent 的第一原则**：**增强失败必须降级**（原样加载，66-02）；**上报失败必须静默**（不能抛到业务）；**Agent 自身要能被关闭**（配置开关 + 动态卸载 transformer）；**内存要与业务隔离**（Agent 的队列/缓存不能无界）。
 *   - **Agent 的故障模式**：**类加载失败**（应用起不来，最严重）、**VerifyError**（改错字节码）、**性能劣化**（全量增强）、**内存泄漏**（Agent 队列无界）、**版本冲突**（依赖的 ASM/ByteBuddy 与应用冲突）。
 *   - 易错点：Transformer 里做重量级操作（启动慢）；埋点同步写日志（吞吐暴跌）；Agent 依赖不打 shade（类冲突）；上报队列无界（OOM）。
 * ============================================================
 */

package com.heima.ch66;

import java.util.ArrayList;
import java.util.List;

// ==================== 示例代码 ====================

public class AgentPerformance {
    /** 加载期：对比"全量解析"与"前缀过滤"的成本 */
    static void loadCost() {
        System.out.println("【加载期成本：1 万个类】");
        long classes = 10_000;
        long fullParseNs = 200_000;             // 每类完整解析 0.2ms
        long filterNs = 50;                     // 前缀判断 50ns
        int targetRatio = 5;                    // 只有 5% 的类需要增强
        double fullMs = classes * fullParseNs / 1_000_000.0;
        double filteredMs = classes * filterNs / 1_000_000.0
                + classes * targetRatio / 100.0 * fullParseNs / 1_000_000.0;
        System.out.printf("  无过滤：%.1f ms%n", fullMs);
        System.out.printf("  前缀过滤（5%% 命中）：%.1f ms（快 %.0f 倍）%n",
                filteredMs, fullMs / filteredMs);
        System.out.println("  结论：先字符串判断，命不中直接返回 null");
        System.out.println();
    }

    /** 运行期：埋点开销与采样 */
    static void runtimeCost() {
        System.out.println("【运行期成本与采样】");
        long callsPerSec = 500_000;
        long perCallNs = 80;
        double noSamplePct = callsPerSec * perCallNs / 1e7;      // 单核百分比
        double sampledPct = noSamplePct / 100;
        System.out.printf("  50 万 QPS 全量埋点：约 %.0f%% 单核（不可忽视）%n", noSamplePct);
        System.out.printf("  1%% 采样后：约 %.1f%% 单核（可接受）%n", sampledPct);
        System.out.println("  代价：采样后只能看宏观指标，个案排查靠开关临时全量");
        System.out.println();
    }

    /** 稳定性四道闸 */
    static void stabilityGates() {
        System.out.println("【Agent 稳定性四道闸】");
        List<String> gates = List.of(
                "增强失败降级：transformer 包 try-catch，失败返回 null（原样加载）",
                "上报失败静默：队列满丢弃并计数，绝不抛到业务线程",
                "可关闭：配置开关 + removeTransformer，出问题能一键停用",
                "资源有界：队列/缓存设上限，Agent 内存与业务隔离");
        gates.forEach(g -> System.out.println("  - " + g));
        System.out.println();

        System.out.println("【常见故障与后果】");
        List<String[]> faults = new ArrayList<>();
        faults.add(new String[]{"类加载失败", "应用起不来（最严重）", "增强降级 + 灰度"});
        faults.add(new String[]{"VerifyError", "类定义失败", "改完字节码先本地验证"});
        faults.add(new String[]{"性能劣化", "吞吐下降/延迟升高", "过滤 + 采样 + 异步"});
        faults.add(new String[]{"内存泄漏", "OOM", "队列有界 + 监控"});
        faults.add(new String[]{"类冲突", "NoSuchMethodError", "依赖 shade + 隔离类加载器"});
        for (String[] f : faults) {
            System.out.printf("  %-12s %-22s 对策：%s%n", f[0], f[1], f[2]);
        }
    }

    public static void main(String[] args) {
        loadCost();
        runtimeCost();
        stabilityGates();
        System.out.println();
        System.out.println("  铁律：Agent 是旁路，出问题先停 Agent，再查业务");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 量化你项目里"1 万个类的加载时间"，对比"无过滤增强"与"前缀过滤增强"的启动耗时差异（可用模拟或实测）。
 * 2. 实现"上报队列有界 + 丢弃计数 + 告警"，注入高并发验证业务吞吐不受影响。
 * 3. 思考题：为什么"Agent 出问题先停 Agent"？从"旁路组件"与"故障归因"两个角度分析。
 * ============================================================
 */
