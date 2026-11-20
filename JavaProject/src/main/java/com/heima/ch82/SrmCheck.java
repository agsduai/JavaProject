/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：82-09
 * 课名：SRM：先证明分流没偏，再谈实验结论
 * ------------------------------------------------------------
 * 知识点：
 *   - **SRM（Sample Ratio Mismatch）**：**实际分流比例和设计比例显著不符**——**设计 50/50，实测 49.6/50.4**，**说明分流逻辑有 bug，后面所有结论都不可信**。
 *   - **SRM 是实验的第一道体检**：**不通过 SRM 就直接看效果，等于在脏数据上做统计**——**很多「实验效果惊人」最后都查出来是分流偏差**。
 *   - **卡方检验判 SRM**：**χ² = Σ(观测-期望)²/期望**，**自由度 1 时 χ² > 3.84 对应 p < 0.05**——**样本越大，能抓出的偏差越小**。
 *   - **常见的 SRM 根因**：**分桶字段有 null/空值**（全部落到默认组）、**分流依赖的字段在新版本里变了**、**埋点丢失导致某组数据少**、**缓存了分组结果但配置改了**。
 *   - **SRM 与「效果不好」的区别**：**SRM 是「数据本身不可信」**（要先修分流），**效果不好是「数据可信但结论是负的」**（要认）——**两者处理方式完全不同**。
 *   - 易错点：只看总比例不看分组明细；样本小的时候 SRM 抓不出来就以为没问题；SRM 通过但埋点缺失（每组数据都少同样比例，比例正常但绝对值错）。
 * ============================================================
 */

package com.heima.ch82;

// ==================== 示例代码 ====================

import java.util.ArrayList;
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
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 把 null 用户数从 3600 改成 1000，重跑，观察卡方是否还能抓到 SRM。
 * 2. 给 check 加第三个分组（三组 33.3/33.3/33.3），说明卡方检验怎么扩展到多组。
 * 3. 思考题：如果两组都少了 5% 的埋点数据（比例仍是 50/50），SRM 会通过吗？这种问题该怎么发现？
 * ============================================================
 */

