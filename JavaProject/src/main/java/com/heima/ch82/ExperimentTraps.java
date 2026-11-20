/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：82-10
 * 课名：实验陷阱：多重比较、偷看与新奇效应
 * ------------------------------------------------------------
 * 知识点：
 *   - **多重比较**：**一次实验看 10 个指标，每个都用 α=0.05 判**，**至少一个假阳性的概率是 1-0.95¹⁰ ≈ 40%**——**「总有一个指标涨了」不是好消息，是数学必然**。
 *   - **Bonferroni 校正**：**看 m 个指标就把阈值降到 α/m**——**10 个指标用 0.005**，**代价是更难测出真实效应**（要更大样本）。
 *   - **偷看（peeking）**：**实验没跑完就反复看 p 值，看到显著就停**——**每次看都是一次独立检验，看 10 次假阳性率从 5% 涨到 40%**，**这叫「p-hacking」**。
 *   - **新奇效应（novelty effect）**：**用户对新 UI 好奇，首周数据虚高**，**第 2~4 周回落到真实水平**——**只看 3 天就下结论，会把「好奇」当成「价值」**。
 *   - **对策**：**指标预先登记**（只看事先说好的 1 个主指标 + 少量护栏指标）、**固定样本量/固定时长再揭盲**、**至少跑满一个完整周（覆盖工作日与周末）**。
 *   - 易错点：事后挑指标（先看哪个涨了再讲故事）；看 5 次数据就停；用首日数据决策；把「相对提升 20%」当成绝对值（基线 0.1% 涨到 0.12%）。
 * ============================================================
 */

package com.heima.ch82;

// ==================== 示例代码 ====================

import java.util.Random;

public class ExperimentTraps {
    // 12 个均匀分布求和近似标准正态
    static double gaussian(Random r) {
        double s = 0;
        for (int i = 0; i < 12; i++) {
            s += r.nextDouble();
        }
        return s - 6;
    }

    public static void main(String[] args) {
        System.out.println("【1. 多重比较：10 个指标、真实无差异】");
        int metrics = 10;
        double fwer = 1 - Math.pow(0.95, metrics);
        System.out.printf("  每个指标 α=0.05，至少一个假阳性的概率 = 1 - 0.95^%d = %.2f%%%n",
                metrics, fwer * 100);
        System.out.printf("  Bonferroni 校正后阈值 = 0.05/%d = %.4f%n", metrics, 0.05 / metrics);
        System.out.println("  -> 校正前「总能挑出一个涨了的指标」，校正后基本挑不出来（这就是它该有的样子）");

        // 固定种子模拟：2000 个实验，每个实验看 10 个无差异指标
        Random rnd = new Random(20261120L);
        int expCount = 2000;
        int flagged = 0;
        int flaggedCorrected = 0;
        for (int e = 0; e < expCount; e++) {
            boolean any = false;
            boolean anyCorrected = false;
            for (int m = 0; m < metrics; m++) {
                double z = gaussian(rnd);
                double p = 2 * (1 - normalCdf(Math.abs(z)));
                if (p < 0.05) {
                    any = true;
                }
                if (p < 0.05 / metrics) {
                    anyCorrected = true;
                }
            }
            if (any) {
                flagged++;
            }
            if (anyCorrected) {
                flaggedCorrected++;
            }
        }
        System.out.printf("  固定种子模拟 %d 个无差异实验：未校正命中 %d 个（%.1f%%），"
                        + "校正后 %d 个（%.1f%%）%n",
                expCount, flagged, flagged * 100.0 / expCount,
                flaggedCorrected, flaggedCorrected * 100.0 / expCount);
        System.out.println();

        System.out.println("【2. 偷看：看 10 次 vs 只看 1 次】");
        Random r2 = new Random(20261120L);
        int looks = 10;
        int peekFlagged = 0;
        int onceFlagged = 0;
        int runs = 2000;
        for (int i = 0; i < runs; i++) {
            boolean any = false;
            for (int k = 0; k < looks; k++) {
                if (Math.abs(gaussian(r2)) > 1.96) {
                    any = true;
                }
            }
            if (any) {
                peekFlagged++;
            }
            if (Math.abs(gaussian(r2)) > 1.96) {
                onceFlagged++;
            }
        }
        System.out.printf("  A/A 实验（真实无差异）跑 %d 次：%n", runs);
        System.out.printf("    只看 1 次就停   -> 假阳性 %.1f%%（符合设计的 5%%）%n",
                onceFlagged * 100.0 / runs);
        System.out.printf("    看 10 次取最显著 -> 假阳性 %.1f%%（理论值 1-0.95^10 = %.1f%%）%n",
                peekFlagged * 100.0 / runs, (1 - Math.pow(0.95, looks)) * 100);
        System.out.println("  -> 多看的每一次都在给自己「中奖」的机会，必须预先固定样本量");

        System.out.println();
        System.out.println("【3. 新奇效应：首周数据会骗人】");
        double[] daily = {+1.20, +0.85, +0.42, +0.18, +0.05, -0.02, -0.08};
        System.out.println("  上线后每天相对基线的转化率变化（pp）：");
        double sum = 0;
        for (int d = 0; d < daily.length; d++) {
            System.out.printf("    第 %d 天 %+.2fpp%n", d + 1, daily[d]);
            sum += daily[d];
        }
        System.out.printf("  首日均值 %+.2fpp -> 七天均值 %+.2fpp，衰减了 %.0f%%%n",
                daily[0], sum / daily.length,
                (1 - (sum / daily.length) / daily[0]) * 100);
        System.out.println("  -> 只看前 3 天会得出「提升 0.82pp」；跑满一周只有「提升 0.37pp」");
        System.out.println("     如果收益线是 0.5pp，前三天的结论会直接让你上线一个亏钱的功能");

        System.out.println();
        System.out.println("【4. 三条纪律】");
        System.out.println("  1. 指标预先登记：1 个主指标 + 2~3 个护栏指标，其余只做参考不下结论");
        System.out.println("  2. 样本量/时长预先固定，到点才揭盲，中间不看 p 值");
        System.out.println("  3. 至少跑满 7 天（覆盖工作日与周末），警惕首周虚高");
    }

    static double normalCdf(double z) {
        return 0.5 * (1 + erf(z / Math.sqrt(2)));
    }

    static double erf(double x) {
        double sign = x < 0 ? -1 : 1;
        double ax = Math.abs(x);
        double p = 0.3275911;
        double a1 = 0.254829592, a2 = -0.284496736, a3 = 1.421413741,
               a4 = -1.453152027, a5 = 1.061405429;
        double t = 1.0 / (1.0 + p * ax);
        double y = 1 - (((((a5 * t + a4) * t) + a3) * t + a2) * t + a1) * t * Math.exp(-ax * ax);
        return sign * y;
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 把指标数从 10 改成 3，重跑多重比较，观察未校正命中率如何下降到 14% 左右。
 * 2. 把偷看的次数从 10 改成 5，对比假阳性率，说明「看两次也不行」的原因。
 * 3. 思考题：新奇效应和「真实提升但需要时间渗透」怎么区分？给出至少两个可验证的判据。
 * ============================================================
 */

