/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：82-08
 * 课名：样本量与显著性：什么时候才算「真的涨了」
 * ------------------------------------------------------------
 * 知识点：
 *   - **点估计不是结论**：**转化率 3.00% vs 3.30%** 看着「涨了 0.3 个点」，**但样本只有 2 万时这可能只是噪声**——**必须做假设检验，看「这个差异有多大可能是巧合」**。
 *   - **两比例 z 检验**：**把两组转化率之差除以标准误得到 z**，**|z| > 1.96 时 p < 0.05**（双侧），**才能说「在 5% 显著性水平下差异显著」**。
 *   - **样本量与效应大小成平方反比**：**想发现的差异缩小一半，样本量要涨 4 倍**——**想验证 0.1pp 的提升，样本量是验证 0.5pp 的 20 多倍**，**这是「小改动测不出来」的根本原因**。
 *   - **最小样本量公式**：**n = (z_α/2 + z_β)² × [p₁(1-p₁) + p₂(1-p₂)] / (p₂-p₁)²**——**α 管「别乱报」，β 管「别漏报」**，**功效 0.8 意味着真有差异时 80% 能测出来**。
 *   - **置信区间比 p 值信息量大**：**p < 0.05 只说「不为零」，置信区间告诉你「大概在 0.19~0.41 个点之间」**——**业务决策要看区间下界够不够覆盖成本**。
 *   - 易错点：样本不够就下结论；只报 p 值不报区间；用「提升 10%」这种相对说法掩盖绝对值很小；功效不足却说「没差异」（应该是「没测出来」）。
 * ============================================================
 */

package com.heima.ch82;

// ==================== 示例代码 ====================

public class SampleSize {
    // 正态分布 CDF：Abramowitz-Stegun 近似，误差 < 2e-7
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

    static final double Z_ALPHA = 1.959964;   // 双侧 alpha = 0.05
    static final double Z_BETA = 0.841621;    // 功效 = 0.8

    // 两比例检验，每组所需样本量
    static long sampleSizePerGroup(double p1, double p2) {
        double d = Math.abs(p2 - p1);
        double num = Math.pow(Z_ALPHA + Z_BETA, 2) * (p1 * (1 - p1) + p2 * (1 - p2));
        return (long) Math.ceil(num / (d * d));
    }

    // 两比例 z 检验（合并方差）
    static double zScore(long n1, long c1, long n2, long c2) {
        double p1 = (double) c1 / n1;
        double p2 = (double) c2 / n2;
        double pooled = (double) (c1 + c2) / (n1 + n2);
        double se = Math.sqrt(pooled * (1 - pooled) * (1.0 / n1 + 1.0 / n2));
        return (p2 - p1) / se;
    }

    static double pValue(double z) {
        return 2 * (1 - normalCdf(Math.abs(z)));
    }

    // 差值置信区间（非合并方差）
    static double[] diffCi(long n1, long c1, long n2, long c2) {
        double p1 = (double) c1 / n1;
        double p2 = (double) c2 / n2;
        double se = Math.sqrt(p1 * (1 - p1) / n1 + p2 * (1 - p2) / n2);
        double d = p2 - p1;
        return new double[]{d - Z_ALPHA * se, d + Z_ALPHA * se};
    }

    static void report(String label, long n1, long c1, long n2, long c2) {
        double p1 = (double) c1 / n1;
        double p2 = (double) c2 / n2;
        double z = zScore(n1, c1, n2, c2);
        double p = pValue(z);
        double[] ci = diffCi(n1, c1, n2, c2);
        System.out.printf("【%s】每组 %d 人%n", label, n1);
        System.out.printf("  对照组 %d/%d = %.2f%%   实验组 %d/%d = %.2f%%   差 %+.2fpp%n",
                c1, n1, p1 * 100, c2, n2, p2 * 100, (p2 - p1) * 100);
        System.out.printf("  z = %.3f   p = %.4f   95%%CI = [%+.3fpp, %+.3fpp]%n",
                z, p, ci[0] * 100, ci[1] * 100);
        if (p < 0.05) {
            System.out.println("  -> 显著：区间不含 0，可以下「有提升」的结论");
        } else {
            System.out.println("  -> 不显著：区间含 0，只能说「没测出差异」，不能说「没差异」");
        }
        System.out.println();
    }

    public static void main(String[] args) {
        double base = 0.030;

        System.out.println("【1. 最小样本量：效应越小，样本越贵】基线转化率 3.00%");
        System.out.printf("%-14s %-16s %-12s %s%n", "要发现的提升", "实验组转化率", "每组样本量", "相对 0.5pp");
        long ref = sampleSizePerGroup(base, base + 0.005);
        double[] deltas = {0.001, 0.003, 0.005};
        for (double d : deltas) {
            long n = sampleSizePerGroup(base, base + d);
            System.out.printf("%-14s %-16s %-12d %.1f 倍%n",
                    String.format("%.1fpp", d * 100),
                    String.format("%.2f%%", (base + d) * 100),
                    n, (double) n / ref);
        }
        System.out.println("  -> 效应缩到 1/5（0.5pp -> 0.1pp），样本量涨到 23 倍：这就是「小改动测不出来」");
        System.out.println();

        System.out.println("【2. 同样涨 0.3 个点，样本量决定结论】");
        report("2 万样本", 20_000, 600, 20_000, 660);
        report("20 万样本", 200_000, 6_000, 200_000, 6_600);

        System.out.println("【3. 样本量够不够，用公式核对】");
        long need = sampleSizePerGroup(base, base + 0.003);
        System.out.printf("  想以 80%% 功效发现 0.3pp 提升，每组需要 %d 人%n", need);
        System.out.printf("  2 万样本只有需求的 %.0f%% -> 难怪测不出来%n", 20_000 * 100.0 / need);
        System.out.printf("  20 万样本是需求的 %.1f 倍 -> 有富余，结论稳%n", 200_000.0 / need);
        System.out.println();

        System.out.println("【4. 决策看区间下界，不看 p 值】");
        double[] ci = diffCi(200_000, 6_000, 200_000, 6_600);
        System.out.printf("  20 万样本的 95%%CI 下界 = %+.3fpp%n", ci[0] * 100);
        System.out.println("  如果改造成本要求「至少涨 0.25pp 才回本」，下界 0.19pp < 0.25pp");
        System.out.println("  -> 虽然显著，但收益可能覆盖不了成本，这个结论要交给业务判断");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 把基线改成 1%，重算发现 0.1pp 提升所需的样本量，说明「低频业务为什么更难做实验」。
 * 2. 把功效从 0.8 改成 0.9（Z_BETA = 1.2816），观察样本量涨多少，解释「宁可多花样本也别漏报」的取舍。
 * 3. 思考题：为什么「p = 0.049 显著」和「p = 0.051 不显著」在业务上几乎没有区别，但流程上要区别对待？
 * ============================================================
 */

