/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：68-07
 * 课名：隐私计算入门：差分隐私
 * ------------------------------------------------------------
 * 知识点：
 *   - **差分隐私（Differential Privacy）的核心承诺**：**查询结果对"任何单条记录是否存在"不敏感**——加入或删除一个人的数据，发布结果的分布几乎不变，从而无法推断个体信息。**它不是加密，而是"结果加噪"**。
 *   - **ε（隐私预算）参数**：**ε 越小越隐私、噪声越大、数据越失真**；ε 越大越精确、隐私越弱——**ε 是"隐私与可用性"的旋钮**，需要业务确认（通常 0.1~1）。
 *   - **拉普拉斯机制**：对数值查询加**拉普拉斯噪声**，噪声尺度 = `敏感度 / ε`——**敏感度 = 单条记录变化对结果的最大影响**（计数查询敏感度=1；求和查询敏感度=单条最大值）。
 *   - **组合定理**：多次查询会累积消耗隐私预算（ε 相加）——**同一数据集查询次数越多，隐私保护越弱**，所以要做**预算管理**（记录已用 ε，超预算拒绝）。
 *   - **本地差分隐私（LDP）**：**在客户端加噪再上报**（苹果/谷歌的统计采集）——服务端永远看不到原始数据，代价是噪声更大；**中心差分隐私（CDP）**：服务端加噪，精确度更高但要求服务端可信。
 *   - 易错点：以为"删掉姓名"就匿名（其余属性可重标识）；ε 设得极大（等于没加噪）；无限次查询（组合定理导致隐私耗尽）；只对一次查询加噪却发布均值/分布等多统计量（组合泄露）。
 * ============================================================
 */

package com.heima.ch68;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

// ==================== 示例代码 ====================

public class DifferentialPrivacy {
    static final Random RANDOM = new Random(42);

    /** 拉普拉斯采样：scale = 敏感度 / ε */
    static double laplace(double sensitivity, double epsilon) {
        double u = RANDOM.nextDouble() - 0.5;
        return -sensitivity / epsilon * Math.signum(u)
                * Math.log(1 - 2 * Math.abs(u));
    }

    /** 计数查询（敏感度 = 1） */
    static long countQuery(List<Integer> ages, int minAge, double epsilon) {
        long real = ages.stream().filter(a -> a >= minAge).count();
        double noisy = real + laplace(1.0, epsilon);
        return Math.max(0, Math.round(noisy));
    }

    /** 求和查询（敏感度 = 单条最大值） */
    static double sumQuery(List<Double> incomes, double maxIncome, double epsilon) {
        double real = incomes.stream().mapToDouble(Double::doubleValue).sum();
        return real + laplace(maxIncome, epsilon);
    }

    /** 隐私预算管理：累计 ε 超限拒绝 */
    static class Budget {
        final double total;
        double used = 0;

        Budget(double total) {
            this.total = total;
        }

        boolean consume(double epsilon) {
            if (used + epsilon > total) {
                return false;
            }
            used += epsilon;
            return true;
        }
    }

    public static void main(String[] args) {
        List<Integer> ages = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            ages.add(18 + i % 50);
        }
        System.out.println("【计数查询：年龄 >= 30 的人数】");
        System.out.printf("  真实值：%d%n", ages.stream().filter(a -> a >= 30).count());
        for (double eps : new double[]{0.1, 1.0, 10.0}) {
            List<Long> runs = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                runs.add(countQuery(ages, 30, eps));
            }
            System.out.printf("  ε=%-4.1f -> 5 次结果 %s（波动=%d）%n", eps, runs,
                    runs.stream().mapToLong(Long::longValue).max().orElse(0)
                            - runs.stream().mapToLong(Long::longValue).min().orElse(0));
        }
        System.out.println("  ε 越小噪声越大：隐私更强但可用性下降");

        System.out.println();
        System.out.println("【求和查询：收入总和（敏感度=单条最大 5 万）】");
        List<Double> incomes = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            incomes.add(8000.0 + i * 100);
        }
        double realSum = incomes.stream().mapToDouble(Double::doubleValue).sum();
        double noisySum = sumQuery(incomes, 50_000, 1.0);
        System.out.printf("  真实=%.0f 加噪=%.0f（相对误差 %.1f%%）%n",
                realSum, noisySum, Math.abs(noisySum - realSum) / realSum * 100);

        System.out.println();
        System.out.println("【隐私预算：组合定理】");
        Budget budget = new Budget(1.0);
        for (int i = 1; i <= 5; i++) {
            boolean ok = budget.consume(0.25);
            System.out.printf("  第%d次查询（ε=0.25）-> %s，已用 %.2f/%.2f%n", i,
                    ok ? "允许" : "拒绝（预算耗尽）", budget.used, budget.total);
        }

        System.out.println();
        System.out.println("【两种模式】");
        Map<String, String> modes = new LinkedHashMap<>();
        modes.put("中心 DP", "数据集中后服务端加噪，精度高，要求服务端可信");
        modes.put("本地 DP", "客户端加噪后上报，隐私最强，噪声大、结果粗");
        modes.forEach((k, v) -> System.out.printf("  %-6s %s%n", k, v));
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现拉普拉斯机制：对同一计数查询用 ε=0.1/1/10 各跑 20 次，统计结果方差与真实值偏差。
 * 2. 实现隐私预算管理器：总预算 1.0，每次查询消耗 0.2，验证第 6 次被拒。
 * 3. 思考题：为什么"多次查询"会削弱隐私？用"同一数据集查两次并用差值推断个体"的例子说明组合定理。
 * ============================================================
 */
