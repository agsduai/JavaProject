# -*- coding: utf-8 -*-
LESSONS = [
    dict(
        cls="SampleSize",
        title="样本量与显著性：什么时候才算「真的涨了」",
        points=[
            "**点估计不是结论**：**转化率 3.00% vs 3.30%** 看着「涨了 0.3 个点」，**但样本只有 2 万时这可能只是噪声**——**必须做假设检验，看「这个差异有多大可能是巧合」**。",
            "**两比例 z 检验**：**把两组转化率之差除以标准误得到 z**，**|z| > 1.96 时 p < 0.05**（双侧），**才能说「在 5% 显著性水平下差异显著」**。",
            "**样本量与效应大小成平方反比**：**想发现的差异缩小一半，样本量要涨 4 倍**——**想验证 0.1pp 的提升，样本量是验证 0.5pp 的 20 多倍**，**这是「小改动测不出来」的根本原因**。",
            "**最小样本量公式**：**n = (z_α/2 + z_β)² × [p₁(1-p₁) + p₂(1-p₂)] / (p₂-p₁)²**——**α 管「别乱报」，β 管「别漏报」**，**功效 0.8 意味着真有差异时 80% 能测出来**。",
            "**置信区间比 p 值信息量大**：**p < 0.05 只说「不为零」，置信区间告诉你「大概在 0.19~0.41 个点之间」**——**业务决策要看区间下界够不够覆盖成本**。",
            "易错点：样本不够就下结论；只报 p 值不报区间；用「提升 10%」这种相对说法掩盖绝对值很小；功效不足却说「没差异」（应该是「没测出来」）。",
        ],
        code=r'''public class SampleSize {
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
}''',
        exercises=[
            "把基线改成 1%，重算发现 0.1pp 提升所需的样本量，说明「低频业务为什么更难做实验」。",
            "把功效从 0.8 改成 0.9（Z_BETA = 1.2816），观察样本量涨多少，解释「宁可多花样本也别漏报」的取舍。",
            "思考题：为什么「p = 0.049 显著」和「p = 0.051 不显著」在业务上几乎没有区别，但流程上要区别对待？",
        ],
    ),
    dict(
        cls="SrmCheck",
        title="SRM：先证明分流没偏，再谈实验结论",
        points=[
            "**SRM（Sample Ratio Mismatch）**：**实际分流比例和设计比例显著不符**——**设计 50/50，实测 49.6/50.4**，**说明分流逻辑有 bug，后面所有结论都不可信**。",
            "**SRM 是实验的第一道体检**：**不通过 SRM 就直接看效果，等于在脏数据上做统计**——**很多「实验效果惊人」最后都查出来是分流偏差**。",
            "**卡方检验判 SRM**：**χ² = Σ(观测-期望)²/期望**，**自由度 1 时 χ² > 3.84 对应 p < 0.05**——**样本越大，能抓出的偏差越小**。",
            "**常见的 SRM 根因**：**分桶字段有 null/空值**（全部落到默认组）、**分流依赖的字段在新版本里变了**、**埋点丢失导致某组数据少**、**缓存了分组结果但配置改了**。",
            "**SRM 与「效果不好」的区别**：**SRM 是「数据本身不可信」**（要先修分流），**效果不好是「数据可信但结论是负的」**（要认）——**两者处理方式完全不同**。",
            "易错点：只看总比例不看分组明细；样本小的时候 SRM 抓不出来就以为没问题；SRM 通过但埋点缺失（每组数据都少同样比例，比例正常但绝对值错）。",
        ],
        code=r'''import java.util.ArrayList;
import java.util.List;

public class SrmCheck {
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

    // 两组卡方（期望比例 0.5/0.5）
    static double chi2(long o1, long o2) {
        double total = o1 + o2;
        double e = total / 2.0;
        return Math.pow(o1 - e, 2) / e + Math.pow(o2 - e, 2) / e;
    }

    // df = 1 时，chi2 = z^2
    static double pValueDf1(double chi2) {
        return 2 * (1 - normalCdf(Math.sqrt(chi2)));
    }

    static final double CRITICAL_005 = 3.8415;

    static void check(String label, long control, long treatment) {
        double x2 = chi2(control, treatment);
        double p = pValueDf1(x2);
        double total = control + treatment;
        double actual = control * 100.0 / total;
        String verdict = x2 > CRITICAL_005 ? "SRM 告警：分流有偏，结论不可信" : "通过";
        System.out.printf("【%s】对照 %d / 实验 %d（%.2f%% : %.2f%%）%n",
                label, control, treatment, actual, 100 - actual);
        System.out.printf("  chi^2 = %.3f   p = %.5f   临界值 3.8415   -> %s%n", x2, p, verdict);
        System.out.println();
    }

    public static void main(String[] args) {
        long total = 1_000_000;

        System.out.println("【1. 三种分流结果的卡方判定】共 " + total + " 人，设计 50:50");
        check("完美分流", 500_000, 500_000);
        check("轻微偏差 0.1%", 499_500, 500_500);
        check("明显偏差 0.36%", 498_200, 501_800);

        System.out.println("【2. 根因复现：null userId 全落对照组】");
        long nullUsers = 3_600;                  // 0.36% 的用户 userId 为空
        long normal = total - nullUsers;         // 996,400 人正常 hash
        long ctrl = normal / 2 + nullUsers;      // null 全进对照组
        long trt = normal - normal / 2;
        System.out.printf("  %d 个 userId 为 null 的用户，hash 拿不到值，默认走对照组%n", nullUsers);
        check("模拟 bug 后的分流", ctrl, trt);
        System.out.println("  -> 卡方把 0.36% 的偏差抓了出来；如果只看「大概一半一半」，根本发现不了");
        System.out.println();

        System.out.println("【3. 样本量决定 SRM 的灵敏度】设计 50:50，真实偏差固定为 0.36%");
        long[] totals = {20_000, 100_000, 1_000_000};
        for (long t : totals) {
            long dev = Math.round(t * 0.0036);       // 偏差占比固定，绝对人数随样本涨
            long o1 = t / 2 - dev / 2;
            long o2 = t / 2 + dev / 2;
            double x2 = chi2(o1, o2);
            System.out.printf("  总样本 %-10d 偏差 %6d 人（0.36%%）  chi^2 = %7.3f  -> %s%n",
                    t, dev, x2, x2 > CRITICAL_005 ? "抓到 SRM" : "抓不到");
        }
        System.out.println("  -> 同样是 0.36% 的偏差：2 万样本 chi^2 = 0.26、10 万样本 1.30 都抓不到，");
        System.out.println("     100 万样本 chi^2 = 12.96 才越过 3.84 的临界值");
        System.out.println("     所以「小样本阶段 SRM 通过」不等于分流真的没偏，只能说「这个量级看不出来」");
        System.out.println();

        System.out.println("【4. 排查清单】SRM 告警后按顺序查");
        List<String> steps = new ArrayList<>();
        steps.add("分流字段是否有 null / 空串 / 默认值（最常见）");
        steps.add("新旧版本的分流逻辑是否一致（灰度期间两套代码同时在跑）");
        steps.add("埋点上报是否某组丢失（客户端版本差异）");
        steps.add("分组结果是否被缓存、缓存 key 是否漏了实验 id");
        steps.add("上游流量本身是否有偏（某个渠道只命中某组）");
        for (int i = 0; i < steps.size(); i++) {
            System.out.printf("  %d. %s%n", i + 1, steps.get(i));
        }

        System.out.println();
        System.out.println("【结论】SRM 是实验的「体检」：先证明分流没偏，再谈效果好坏。");
        System.out.println("  比例不对 -> 修分流、重跑实验；比例对但效果差 -> 认这个结论。");
    }
}''',
        exercises=[
            "把 null 用户数从 3600 改成 1000，重跑，观察卡方是否还能抓到 SRM。",
            "给 check 加第三个分组（三组 33.3/33.3/33.3），说明卡方检验怎么扩展到多组。",
            "思考题：如果两组都少了 5% 的埋点数据（比例仍是 50/50），SRM 会通过吗？这种问题该怎么发现？",
        ],
    ),
    dict(
        cls="ExperimentTraps",
        title="实验陷阱：多重比较、偷看与新奇效应",
        points=[
            "**多重比较**：**一次实验看 10 个指标，每个都用 α=0.05 判**，**至少一个假阳性的概率是 1-0.95¹⁰ ≈ 40%**——**「总有一个指标涨了」不是好消息，是数学必然**。",
            "**Bonferroni 校正**：**看 m 个指标就把阈值降到 α/m**——**10 个指标用 0.005**，**代价是更难测出真实效应**（要更大样本）。",
            "**偷看（peeking）**：**实验没跑完就反复看 p 值，看到显著就停**——**每次看都是一次独立检验，看 10 次假阳性率从 5% 涨到 40%**，**这叫「p-hacking」**。",
            "**新奇效应（novelty effect）**：**用户对新 UI 好奇，首周数据虚高**，**第 2~4 周回落到真实水平**——**只看 3 天就下结论，会把「好奇」当成「价值」**。",
            "**对策**：**指标预先登记**（只看事先说好的 1 个主指标 + 少量护栏指标）、**固定样本量/固定时长再揭盲**、**至少跑满一个完整周（覆盖工作日与周末）**。",
            "易错点：事后挑指标（先看哪个涨了再讲故事）；看 5 次数据就停；用首日数据决策；把「相对提升 20%」当成绝对值（基线 0.1% 涨到 0.12%）。",
        ],
        code=r'''import java.util.Random;

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
}''',
        exercises=[
            "把指标数从 10 改成 3，重跑多重比较，观察未校正命中率如何下降到 14% 左右。",
            "把偷看的次数从 10 改成 5，对比假阳性率，说明「看两次也不行」的原因。",
            "思考题：新奇效应和「真实提升但需要时间渗透」怎么区分？给出至少两个可验证的判据。",
        ],
    ),
    dict(
        cls="GrayReleaseCase",
        title="综合案例：大促前的一次灰度发布",
        points=[
            "**案例背景**：**商品详情页要同时上线「新版价格展示」和「推荐算法换模型」**——**日活 800 万、峰值 QPS 2 万**，**要求 3 分钟内可回滚，两个改动各自独立评估**。",
            "**第一步选形态**：**价格展示是前端渲染逻辑 → 功能开关（秒级回滚）**；**推荐模型是后端服务 → 金丝雀 + 规则分流**——**能用开关的别用蓝绿，能切流量的别回滚代码**。",
            "**第二步定节奏**：**1%(10min) → 5%(15min) → 25%(30min) → 50%(30min) → 100%**，**每档检查错误率/P99/转化率**——**小档只卡技术指标，大档才判业务指标**（样本量不够）。",
            "**第三步设门禁**：**P99 > 400ms 或错误率 > 1% 或错误率 > 基线 3 倍 → 自动回退到上一档**——**连续 2 个 5 分钟窗口不达标才动手**，**防单点抖动造成回滚风暴**。",
            "**第四步判效果**：**推荐算法用两比例 z 检验 + SRM 校验**，**价格展示样本不够就说「测不出来」**——**不能因为「感觉变好了」就全量**。",
            "**复盘价值**：**这次 25% 档被 P99 拦下，暴露了连接池配置问题**——**如果没有门禁，这个问题会在 100% 流量下持续 30 分钟**，**影响面差 24 倍**。",
        ],
        code=r'''public class GrayReleaseCase {
    // 放量档位
    record Stage(String name, int pct, int windowMin, double qps,
                 double errRate, double p99Ms, double cvr, String verdict) {
    }

    static final double PEAK_QPS = 20_000;
    static final double ERR_LIMIT = 0.010;
    static final double P99_LIMIT = 400;
    static final double BASE_ERR = 0.002;
    static final double BASE_CVR = 0.030;

    // 门禁判定
    static String gate(Stage s) {
        if (s.errRate() > ERR_LIMIT) {
            return "拦截：错误率 " + String.format("%.2f%%", s.errRate() * 100) + " > 1.00%";
        }
        if (s.errRate() > BASE_ERR * 3) {
            return "拦截：错误率是基线 " + String.format("%.1f 倍", s.errRate() / BASE_ERR);
        }
        if (s.p99Ms() > P99_LIMIT) {
            return "拦截：P99 " + String.format("%.0fms", s.p99Ms()) + " > 400ms";
        }
        return "通过";
    }

    static double requests(Stage s) {
        return s.qps() * s.windowMin() * 60;
    }

    public static void main(String[] args) {
        System.out.println("【案例】商品详情页灰度：日活 800 万，峰值 QPS 2 万");
        System.out.println();

        // 25% 档开始出现连接池竞争，P99 劣化
        Stage[] plan = {
                new Stage("1%", 1, 10, PEAK_QPS * 0.01, 0.0021, 262, 0.0302, ""),
                new Stage("5%", 5, 15, PEAK_QPS * 0.05, 0.0024, 271, 0.0301, ""),
                new Stage("25%", 25, 30, PEAK_QPS * 0.25, 0.0031, 480, 0.0299, ""),
                new Stage("50%", 50, 30, PEAK_QPS * 0.50, 0.0062, 760, 0.0290, ""),
                new Stage("100%", 100, 30, PEAK_QPS, 0.0200, 940, 0.0271, ""),
        };

        System.out.println("【放量时间线】");
        System.out.printf("%-6s %-8s %-12s %-10s %-9s %-9s %-9s %s%n",
                "档位", "窗口", "请求数", "错误率", "P99", "转化率", "门禁", "说明");
        boolean stopped = false;
        double blockedRequests = 0;
        int blockedIdx = -1;
        for (int i = 0; i < plan.length; i++) {
            Stage s = plan[i];
            String g;
            String note;
            if (stopped) {
                g = "—";
                note = "未执行（已回退）";
            } else {
                g = gate(s);
                if (g.equals("通过")) {
                    note = "继续放量";
                } else {
                    note = "自动回退到上一档";
                    stopped = true;
                    blockedIdx = i;
                    // 每 5 分钟检查一次，第一个检查点就拦下
                    blockedRequests = s.qps() * 5 * 60;
                }
            }
            System.out.printf("%-6s %-8s %-12.0f %-10s %-9s %-9s %-9s %s%n",
                    s.name(), s.windowMin() + "min", requests(s),
                    String.format("%.2f%%", s.errRate() * 100),
                    String.format("%.0fms", s.p99Ms()),
                    String.format("%.2f%%", s.cvr() * 100),
                    g, note);
        }

        System.out.println();
        System.out.println("【拦截点分析】");
        Stage bad = plan[blockedIdx];
        System.out.printf("  25%% 档 P99 %.0fms 超过 400ms 阈值，自动回退到 5%%%n", bad.p99Ms());
        System.out.println("  根因：新版本加了价格计算逻辑，连接池没扩容，25% 流量下开始排队");
        System.out.println("  修复：连接池 50 -> 200，重新从 1% 开始放量");
        System.out.println();

        System.out.println("【有门禁 vs 无门禁：影响面对比】");
        System.out.println("  假设没有门禁：25% 档的 P99 问题被忽略，一路放到 100%，问题在 100% 流量下暴露");
        double withGate = blockedRequests;
        Stage full = plan[plan.length - 1];
        double withoutGate = requests(full);
        System.out.printf("  无门禁：一路放到 100%%，跑满 30 分钟 -> %.0f 次请求，错误率 2.00%%%n",
                withoutGate);
        System.out.printf("          失败请求 %.0f 次%n", withoutGate * full.errRate());
        System.out.printf("  有门禁：25%% 档 5 分钟内拦下 -> %.0f 次请求，错误率 0.31%%%n",
                withGate);
        System.out.printf("          失败请求 %.0f 次%n", withGate * bad.errRate());
        System.out.printf("  -> 请求量差 %.0f 倍，失败量差 %.0f 倍%n",
                withoutGate / withGate,
                (withoutGate * full.errRate()) / (withGate * bad.errRate()));
        System.out.println();

        System.out.println("【实验判定：推荐算法（后端服务，金丝雀 + 分层实验）】");
        long n = 200_000;
        long c1 = 6_000;
        long c2 = 6_600;
        double p1 = (double) c1 / n;
        double p2 = (double) c2 / n;
        double pooled = (double) (c1 + c2) / (2 * n);
        double se = Math.sqrt(pooled * (1 - pooled) * 2.0 / n);
        double z = (p2 - p1) / se;
        double seCi = Math.sqrt(p1 * (1 - p1) / n + p2 * (1 - p2) / n);
        double lo = (p2 - p1) - 1.959964 * seCi;
        double hi = (p2 - p1) + 1.959964 * seCi;
        System.out.printf("  对照 %.2f%% vs 实验 %.2f%%，每组 %d 人%n", p1 * 100, p2 * 100, n);
        System.out.printf("  z = %.3f -> p < 0.0001，95%%CI = [%+.3fpp, %+.3fpp]%n",
                z, lo * 100, hi * 100);
        System.out.println("  SRM 校验：200000 / 200000，chi^2 = 0.000 -> 通过");
        System.out.println("  -> 显著且下界 0.19pp > 0，可以全量；但要盯第 2~4 周是否衰减");
        System.out.println();

        System.out.println("【实验判定：价格展示（前端，样本不够）】");
        long n2 = 40_000;
        long d1 = 1_200;
        long d2 = 1_220;
        double q1 = (double) d1 / n2;
        double q2 = (double) d2 / n2;
        double pooled2 = (double) (d1 + d2) / (2 * n2);
        double se2 = Math.sqrt(pooled2 * (1 - pooled2) * 2.0 / n2);
        double z2 = (q2 - q1) / se2;
        System.out.printf("  对照 %.2f%% vs 实验 %.2f%%，每组 %d 人%n", q1 * 100, q2 * 100, n2);
        System.out.printf("  z = %.3f -> 不显著（|z| < 1.96），只能说「没测出差异」%n", z2);
        System.out.printf("  想验证 0.05pp 的提升需要每组约 %.0f 万人%n",
                Math.ceil(Math.pow(1.959964 + 0.841621, 2)
                        * (q1 * (1 - q1) + q2 * (1 - q2))
                        / Math.pow(0.0005, 2)) / 10_000);
        System.out.println("  -> 结论：价格展示继续观察，不因为「看起来涨了」就全量");
        System.out.println();

        System.out.println("【复盘：这次灰度做对的三件事】");
        System.out.println("  1. 形态选对：前端用开关、后端用金丝雀，回滚都是秒级");
        System.out.println("  2. 门禁拦住了：25% 档 P99 超阈值自动回退，影响面缩小 24 倍");
        System.out.println("  3. 结论分开：推荐算法显著可全量，价格展示样本不够就不下结论");
        System.out.println();
        System.out.println("【下次要改的】");
        System.out.println("  1. 连接池容量要进发布前检查清单（这次是靠门禁兜住的）");
        System.out.println("  2. 价格展示实验的样本量要提前算，避免跑了两周才发现不够");
        System.out.println("  3. 全量后要留 7 天观察窗口，警惕新奇效应衰减");
    }
}''',
        exercises=[
            "把 25% 档的 P99 改成 380ms（低于阈值），重跑，观察是否继续放量到 100%，并计算影响面。",
            "给门禁加一条「转化率低于基线 20%」的业务判据，观察在哪一档触发。",
            "思考题：如果推荐算法实验 SRM 不通过（比如 49.2/50.8），你会先修分流还是先看效果？为什么？",
        ],
    ),
]
