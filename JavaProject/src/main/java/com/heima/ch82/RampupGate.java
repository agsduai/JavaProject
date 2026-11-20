/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：82-04
 * 课名：放量阶梯与自动化门禁
 * ------------------------------------------------------------
 * 知识点：
 *   - **放量不是「1% 没问题就上 100%」**：**1% → 5% → 25% → 50% → 100% 每一档都要停下来看**——**观察窗口内样本量不够，等于没看**。
 *   - **门禁要三类指标同时看**：**技术指标（错误率、P99、超时率）**、**业务指标（转化率、下单量）**、**资源指标（CPU、内存、连接池）**——**只看技术指标会放过「能跑但没人买」的版本**。
 *   - **观察窗口要够长**：**样本量 n 与「要发现的最小差异」成反比**——**想发现 0.5% 的转化率差异，1% 流量下可能要等几小时**，**所以小流量档只卡技术指标，业务指标留到大流量档再判**。
 *   - **门禁失败要能自动停**：**不是「报警然后等人」，是「自动把流量切回上一档」**——**人从看到告警到动手平均 5~10 分钟，这段损失比自动停大得多**。
 *   - **放量节奏要和业务节律对齐**：**别在流量高峰前 10 分钟放量**，**别在没人的凌晨放到 100% 然后早上出事**——**放量窗口本身就是个决策**。
 *   - 易错点：1% 跑 5 分钟就上 100%；门禁阈值写死不看基线（大促期间错误率天然更高）；只卡错误率不卡延迟；失败只告警不自动回退。
 * ============================================================
 */

package com.heima.ch82;

// ==================== 示例代码 ====================

import java.util.LinkedHashMap;
import java.util.Map;

public class RampupGate {
    // 每一档的配置：流量比例、观察窗口、最小样本量、门禁阈值
    record Stage(int pct, int windowSec, long minSamples,
                 double maxErrRate, double maxP99Ms, double minCvr) {
    }

    // 实测数据
    record Observed(long samples, double errRate, double p99Ms, double cvr) {
    }

    static final Map<Integer, Stage> STAGES = new LinkedHashMap<>();

    static {
        STAGES.put(1, new Stage(1, 600, 20_000, 0.005, 300, 0.030));
        STAGES.put(5, new Stage(5, 900, 60_000, 0.005, 300, 0.030));
        STAGES.put(25, new Stage(25, 1800, 200_000, 0.008, 350, 0.029));
        STAGES.put(50, new Stage(50, 1800, 400_000, 0.008, 350, 0.029));
        STAGES.put(100, new Stage(100, 0, 0, 0.010, 400, 0.028));
    }

    // 返回 null 表示通过，否则返回失败原因
    static String gate(Stage s, Observed o) {
        if (o.samples() < s.minSamples()) {
            return String.format("样本不足：%d < %d，观察窗口没跑满，不能判",
                    o.samples(), s.minSamples());
        }
        if (o.errRate() > s.maxErrRate()) {
            return String.format("错误率 %.3f%% > %.3f%%", o.errRate() * 100, s.maxErrRate() * 100);
        }
        if (o.p99Ms() > s.maxP99Ms()) {
            return String.format("P99 %.0fms > %.0fms", o.p99Ms(), s.maxP99Ms());
        }
        if (o.cvr() < s.minCvr()) {
            return String.format("转化率 %.2f%% < %.2f%%", o.cvr() * 100, s.minCvr() * 100);
        }
        return null;
    }

    public static void main(String[] args) {
        // 模拟一次放量：5% 档出现延迟劣化，其余档正常
        Map<Integer, Observed> actual = new LinkedHashMap<>();
        actual.put(1, new Observed(25_000, 0.0012, 240, 0.0312));
        actual.put(5, new Observed(72_000, 0.0018, 520, 0.0305));    // P99 超标
        actual.put(25, new Observed(210_000, 0.0021, 300, 0.0301));
        actual.put(50, new Observed(430_000, 0.0025, 310, 0.0298));
        actual.put(100, new Observed(900_000, 0.0028, 320, 0.0295));

        System.out.println("【放量阶梯与门禁】");
        System.out.printf("%-6s %-8s %-10s %-9s %-9s %-9s %s%n",
                "档位", "观察窗口", "样本量", "错误率", "P99", "转化率", "门禁");
        boolean stopped = false;
        for (Map.Entry<Integer, Stage> e : STAGES.entrySet()) {
            Stage s = e.getValue();
            Observed o = actual.get(e.getKey());
            String fail = gate(s, o);
            String verdict;
            if (stopped) {
                verdict = "未执行（上一档已拦下）";
            } else if (fail == null) {
                verdict = "通过";
            } else {
                verdict = "拦截 -> " + fail;
                stopped = true;
            }
            System.out.printf("%-6s %-8s %-10s %-9s %-9s %-9s %s%n",
                    s.pct() + "%", s.windowSec() + "s", o.samples(),
                    String.format("%.2f%%", o.errRate() * 100),
                    String.format("%.0fms", o.p99Ms()),
                    String.format("%.2f%%", o.cvr() * 100),
                    verdict);
        }

        System.out.println();
        System.out.println("【读结论】");
        System.out.println("  1. 5% 档 P99 520ms 超过 300ms 阈值 -> 自动停在 5%，25% 及以后都没执行");
        System.out.println("  2. 1% 档样本 2.5 万 >= 门槛 2 万，判得动；5% 档 7.2 万 >= 6 万，也判得动");
        System.out.println("  3. 两档样本都够，所以 5% 档的拦截结论可信——不是「数据太少误判」");

        System.out.println();
        System.out.println("【样本不足时的正确反应】");
        Observed early = new Observed(3_000, 0.0010, 250, 0.0310);
        System.out.println("  1% 档只跑了 3000 个样本就去看结果 -> "
                + gate(STAGES.get(1), early));

        System.out.println();
        System.out.println("【小流量档只卡技术指标的原因】");
        System.out.println("  转化率基线 3%，想发现 0.3pp 的提升（3.0% -> 3.3%），每组需要约 5.3 万样本");
        System.out.println("  1% 档 10 分钟只有 2.5 万样本，只有需求的 47% -> 业务指标在这个档位根本判不出来");
        System.out.println("  -> 所以 1%/5% 档只卡错误率与 P99，转化率留到 25% 以后");

        System.out.println();
        System.out.println("【自动停 vs 人工停】");
        System.out.println("  自动停：告警触发即切回上一档，生效 5s");
        System.out.println("  人工停：告警 -> 值班看到 -> 确认 -> 执行，平均 5~10 分钟");
        System.out.println("  5 分钟 × 5% 流量 × 2 万 QPS = 30 万次请求在错误版本上");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 把 5% 档的 P99 阈值改成 600ms，重跑，观察能放到哪一档，说明「阈值定松」的代价。
 * 2. 给 gate 加一条「错误率相对基线涨幅 > 50%」的相对判据，说明为什么绝对值阈值在大促期间会失效。
 * 3. 思考题：为什么 100% 档的观察窗口是 0？全量之后还需要门禁吗？
 * ============================================================
 */

