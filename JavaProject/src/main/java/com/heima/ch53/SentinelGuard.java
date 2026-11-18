/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：53-05
 * 课名：Sentinel 限流熔断实战
 * ------------------------------------------------------------
 * 知识点：
 *   - **Sentinel 的资源模型**：一切皆资源（resource），规则挂在资源上——**流控规则**（QPS/线程数，直接/关联/链路）、**熔断降级**（慢调用比例/异常比例/异常数，47-03 三态同构）、**热点限流**（按参数值限流——爆款 SKU 单独配额，39 章热点问题的组件级解）、**系统自适应保护**（按 Load/RT 全局兜底）。
 *   - **滑窗统计是 Sentinel 的心脏**（44 章滑动窗口同源）：秒级滑窗统计 QPS/RT/异常数，规则判定基于窗口数据——**窗口粒度决定灵敏与抖动的平衡**。
 *   - **流控效果**：快速失败（默认）/Warm Up（预热爬坡，防冷启动被打爆——缓存未热时）/排队等待（匀速通过，削峰）——**按场景选效果**：秒杀用排队+队列阈值，常规 API 用快速失败。
 *   - **规则持久化**：默认内存（重启丢）——**生产必须推模式持久化到 Nacos**（规则变 config dataId，控制台修改推 Nacos，客户端监听）——41 章配置审计纪律适用。
 *   - **与手写限流的对照**：44 章手写过令牌桶/Semaphore——Sentinel=滑动窗口统计+规则引擎+控制台三合一；**理解手写后，Sentinel 只是"把这三件事产品化"**。
 *   - 易错点：流控规则只配 QPS 不配线程数（线程耗尽型故障未防）；热点限流参数索引错（限了个寂寞）；规则不持久化（重启归零）；链路模式下入口资源名写错（规则不生效）。
 * ============================================================
 */

package com.heima.ch53;

import java.util.ArrayList;
import java.util.List;

// ==================== 示例代码 ====================

public class SentinelGuard {
    /** 滑动窗口统计：最近 N 秒失败率 -> 熔断判定（慢调用比例模式） */
    record Sample(long ts, long rtMs, boolean error) {
    }

    static boolean slowRatioExceeded(List<Sample> window, long slowThreshold,
                                     double ratioThreshold, int minRequest) {
        List<Sample> recent = window.stream()
                .filter(s -> s.rtMs() >= slowThreshold || s.error())
                .toList();
        if (window.size() < minRequest) {
            return false;                       // 样本不足不熔断（统计意义）
        }
        double ratio = (double) recent.size() / window.size();
        return ratio >= ratioThreshold;
    }

    public static void main(String[] args) {
        List<Sample> window = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            window.add(new Sample(i, i < 6 ? 900 : 50, false));   // 6 个慢调用
        }
        boolean tripped = slowRatioExceeded(window, 500, 0.5, 5);
        System.out.printf("  慢调用比例 %.0f%% -> 熔断器 %s%n",
                60.0, tripped ? "OPEN" : "CLOSED");
        System.out.println("  对照：minRequest 未满足时不熔断 —— 防样本少误判");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现完整滑动窗口熔断（慢调用比例 + 异常比例双规则 + 半开恢复 47-03），输出三态迁移日志。
 * 2. 实现热点限流：按 SKU 参数限流（爆款 SKU 100 QPS，其他 1000），验证参数维度生效。
 * 3. 思考题：为什么 Sentinel 规则持久化必须推模式而非拉模式？从规则生效时效与控制台体验分析。
 * ============================================================
 */
