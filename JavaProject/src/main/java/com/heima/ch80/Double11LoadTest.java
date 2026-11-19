/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：80-11
 * 课名：综合案例：一次双十一压测
 * ------------------------------------------------------------
 * 知识点：
 *   - **把本章收口成一次完整压测**：**目标设定（80-01）-> 模型选择（80-04）-> 基准/压测（80-02/80-08）-> 指标分析（80-03）-> 瓶颈定位（80-05~80-07）-> 容量决策（80-09）-> 回归门禁（80-10）**。
 *   - **压测目标要可量化**：**目标 QPS、P99 上限、错误率上限、持续时间**（如"峰值 5 万 QPS、P99 < 200ms、错误率 < 0.1%、持续 30 分钟"）——**没有目标就无法判定通过**。
 *   - **阶梯加压**：**每 5 分钟提升 20% 流量**，**观察吞吐/延迟/错误率/资源水位**——**在拐点前停下，留出安全余量**（80-09）。
 *   - **压测报告**：**目标 -> 环境 -> 模型 -> 曲线 -> 瓶颈 -> 结论 -> 风险与建议**——**报告要能让决策者做扩容/限流决定**。
 *   - 与 56 章的关系：56 章"稳定性工程"是日常，**本章是"大促前的实战演练"**——**压测发现的问题，比大促当天发现便宜 100 倍**。
 *   - 易错点：无目标（压到哪算哪）；闭环模型（80-04 低估）；不染色（脏数据）；只看平均值；压完不沉淀报告。
 * ============================================================
 */

package com.heima.ch80;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class Double11LoadTest {
    record Sample(int minute, long qps, double p99Ms, double errorRate,
                  double cpu, double dbConnUtilization) {
    }

    record Goal(long targetQps, double p99LimitMs, double errorRateLimit) {
    }

    static final Goal goal = new Goal(50_000, 200, 0.001);

    /** 阶梯加压：每分钟 +20%，直到目标 */
    static List<Sample> ramp() {
        List<Sample> samples = new ArrayList<>();
        long qps = 10_000;
        int minute = 0;
        while (qps <= goal.targetQps() * 1.2) {
            minute += 1;
            double load = (double) qps / goal.targetQps();
            double p99 = 40 + 160 * Math.pow(load, 3);          // 非线性
            double err = load > 1.05 ? (load - 1.05) * 0.05 : 0.0002;
            double cpu = Math.min(0.99, 0.25 + 0.6 * load);
            double db = Math.min(0.99, 0.2 + 0.75 * load);
            samples.add(new Sample(minute, qps, p99, err, cpu, db));
            qps = Math.round(qps * 1.2);
        }
        return samples;
    }

    static void report(List<Sample> samples) {
        System.out.println("【阶梯加压曲线】");
        System.out.printf("  %-6s %-9s %-9s %-9s %-7s %-8s %s%n",
                "分钟", "QPS", "P99(ms)", "错误率", "CPU", "DB连接", "判断");
        for (Sample s : samples) {
            String verdict;
            if (s.errorRate() > goal.errorRateLimit()) {
                verdict = "错误率超标";
            } else if (s.p99Ms() > goal.p99LimitMs()) {
                verdict = "P99 超标";
            } else if (s.dbConnUtilization() > 0.85) {
                verdict = "DB 接近瓶颈";
            } else if (s.cpu() > 0.85) {
                verdict = "CPU 接近瓶颈";
            } else {
                verdict = "正常";
            }
            System.out.printf("  %-6d %-9d %-9.0f %-9.4f %-7.0f%% %-8.0f%% %s%n",
                    s.minute(), s.qps(), s.p99Ms(), s.errorRate() * 100,
                    s.cpu() * 100, s.dbConnUtilization() * 100, verdict);
        }
    }

    static void conclusion(List<Sample> samples) {
        long safeQps = 0;
        for (Sample s : samples) {
            if (s.p99Ms() <= goal.p99LimitMs()
                    && s.errorRate() <= goal.errorRateLimit()
                    && s.dbConnUtilization() <= 0.85) {
                safeQps = s.qps();
            }
        }
        System.out.println();
        System.out.println("【容量结论】");
        System.out.printf("  目标 QPS=%,d，安全承载=%,d（P99/错误率/DB 水位均达标）%n",
                goal.targetQps(), safeQps);
        System.out.printf("  安全余量=%.1f%%%n", (1 - (double) safeQps / goal.targetQps()) * 100);

        if (safeQps < goal.targetQps()) {
            System.out.println("  未达标 -> 扩容或限流");
        } else {
            System.out.println("  达标 -> 可支撑大促，但仍需保留降级预案");
        }

        System.out.println();
        System.out.println("【瓶颈】");
        Sample last = samples.get(samples.size() - 1);
        System.out.printf("  最高压时 CPU=%.0f%%，DB 连接=%.0f%% -> DB 连接池是首要瓶颈%n",
                last.cpu() * 100, last.dbConnUtilization() * 100);
        System.out.println("  下一步：扩大连接池 / 读写分离 / 缓存热点 / SQL 优化（77 章）");
    }

    static void reportCard() {
        System.out.println();
        System.out.println("【压测报告模板】");
        Map<String, String> report = new LinkedHashMap<>();
        report.put("目标", "峰值 5 万 QPS，P99<200ms，错误率<0.1%，持续 30 分钟");
        report.put("环境", "生产同构，影子库，流量染色");
        report.put("模型", "开放模型，阶梯加压每分钟 +20%");
        report.put("曲线", "QPS-延迟-错误率-资源水位");
        report.put("瓶颈", "DB 连接池 85%+，P99 在 5.5 万 QPS 后陡增");
        report.put("结论", "安全承载 4.5 万 QPS，需扩容 DB 连接或加缓存");
        report.put("风险", "未覆盖支付回调峰值，需单独压测");
        report.forEach((k, v) -> System.out.printf("  %-6s %s%n", k, v));
    }

    public static void main(String[] args) {
        List<Sample> samples = ramp();
        report(samples);
        conclusion(samples);
        reportCard();

        System.out.println();
        System.out.println("【验收】目标可量化 / 模型不遗漏 / 曲线有拐点 / 结论可决策 / 报告可沉淀");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 设定一组压测目标（QPS/P99/错误率），用阶梯加压模拟并找出安全承载点。
 * 2. 调整 DB 连接池水位曲线，观察瓶颈从 DB 转移到 CPU，说明"瓶颈会迁移"。
 * 3. 思考题：为什么"压测报告"比"压测数据"更重要？从"决策者需要结论而非原始曲线"分析。
 * ============================================================
 */
