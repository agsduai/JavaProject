/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：55-08
 * 课名：噪声邻居与性能隔离
 * ------------------------------------------------------------
 * 知识点：
 *   - **噪声邻居（noisy neighbor）是共享架构的头号风险**：一个大租户的重任务（批量导出/报表/压测自己）耗尽共享资源（DB CPU/连接池/带宽），**所有租户一起变慢**——共享表/独立库共享实例都有此风险，独立部署天然免疫。
 *   - **防护三件套**：① **配额限流**（租户级 API 配额 + 批处理并发上限，55-06 配置上限）；② **资源分组**（批处理任务进独立线程池/独立节点池，47-08 隔离）；③ **实时检测与自动处置**（租户级指标监控 -> 异常租户自动限流/隔离，42 章告警联动）。
 *   - **检测信号**：按租户维度的 DB 时间、API 耗时、队列占用——**单租户占用共享资源 >X% 即告警**（46-10 的租户维度）；识别后的动作：限流 -> 降级 -> 沟通（通知租户升级独立资源）。
 *   - **大租户的升级路径**：噪声邻居反复出现 -> 引导升级独立库/独立部署（商业化与技术联动）——**架构方案成为售卖选项**。
 *   - 易错点：无租户维度监控（发现问题但不知道是谁）；限流一刀切（影响正常流量）；批处理与在线同池（55-01 的共享表风险）；大租户议价后无限资源（议价要带技术约束）。
 * ============================================================
 */

package com.heima.ch55;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class NoisyNeighborDemo {
    record TenantUsage(String tenant, double dbTimePct, long apiCalls) {
    }

    static List<String> detect(List<TenantUsage> usage, double alarmPct) {
        List<String> actions = new ArrayList<>();
        for (TenantUsage u : usage) {
            if (u.dbTimePct() > alarmPct) {
                actions.add("[" + u.tenant() + "] DB 时间占比 "
                        + String.format("%.0f%%", u.dbTimePct() * 100)
                        + " -> 自动限流 + 通知升级独立资源");
            }
        }
        if (actions.isEmpty()) {
            actions.add("无噪声邻居");
        }
        return actions;
    }

    public static void main(String[] args) {
        List<TenantUsage> usage = List.of(
                new TenantUsage("t-small-1", 0.02, 5000),
                new TenantUsage("t-big-7", 0.55, 120000),      // 大租户吃掉一半 DB
                new TenantUsage("t-small-2", 0.03, 6000));
        System.out.println(detect(usage, 0.3));
        System.out.println("  其余租户的 P99 同时劣化 —— 检测信号就是按租户分桶的 DB 时间");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现噪声邻居检测器（租户维度资源统计 + 阈值动作），模拟大租户压测场景的自动限流。
 * 2. 实现"批处理资源分组"：导出/报表任务独立线程池 + 租户级并发上限，对比混池时的 P99。
 * 3. 思考题：为什么噪声邻居的最终解是"商业化升级"而非纯技术？从成本转嫁与资源隔离的投入产出分析。
 * ============================================================
 */
