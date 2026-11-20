/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：82-11
 * 课名：综合案例：大促前的一次灰度发布
 * ------------------------------------------------------------
 * 知识点：
 *   - **案例背景**：**商品详情页要同时上线「新版价格展示」和「推荐算法换模型」**——**日活 800 万、峰值 QPS 2 万**，**要求 3 分钟内可回滚，两个改动各自独立评估**。
 *   - **第一步选形态**：**价格展示是前端渲染逻辑 → 功能开关（秒级回滚）**；**推荐模型是后端服务 → 金丝雀 + 规则分流**——**能用开关的别用蓝绿，能切流量的别回滚代码**。
 *   - **第二步定节奏**：**1%(10min) → 5%(15min) → 25%(30min) → 50%(30min) → 100%**，**每档检查错误率/P99/转化率**——**小档只卡技术指标，大档才判业务指标**（样本量不够）。
 *   - **第三步设门禁**：**P99 > 400ms 或错误率 > 1% 或错误率 > 基线 3 倍 → 自动回退到上一档**——**连续 2 个 5 分钟窗口不达标才动手**，**防单点抖动造成回滚风暴**。
 *   - **第四步判效果**：**推荐算法用两比例 z 检验 + SRM 校验**，**价格展示样本不够就说「测不出来」**——**不能因为「感觉变好了」就全量**。
 *   - **复盘价值**：**这次 25% 档被 P99 拦下，暴露了连接池配置问题**——**如果没有门禁，这个问题会在 100% 流量下持续 30 分钟**，**影响面差 24 倍**。
 * ============================================================
 */

package com.heima.ch82;

// ==================== 示例代码 ====================

public class GrayReleaseCase {
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
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 把 25% 档的 P99 改成 380ms（低于阈值），重跑，观察是否继续放量到 100%，并计算影响面。
 * 2. 给门禁加一条「转化率低于基线 20%」的业务判据，观察在哪一档触发。
 * 3. 思考题：如果推荐算法实验 SRM 不通过（比如 49.2/50.8），你会先修分流还是先看效果？为什么？
 * ============================================================
 */

/*
 * ============================================================
 * 本章综合练习（无参考答案）
 * ------------------------------------------------------------
 * 【场景】
 * 双十一前两周，商品详情页要上线「新版价格展示 + 推荐位算法」两个改动，涉及 3 个服务、日活 800 万。要求：任何一次放量都能在 3 分钟内回滚，改动对转化率的影响必须用数据说清（而不是「感觉变好了」），并且两个改动要能各自独立评估、互不干扰。请设计灰度与实验方案并给出验证数据。
 * 【任务】
 * 1. 选形态：为「价格展示改版」和「推荐算法换模型」分别选发布形态，说明资源成本与回滚耗时的取舍。
 * 2. 做分流：设计一套规则（白名单 → 地域 → 比例），保证同一用户在整个放量过程中稳定命中同一版本，并验证比例从 5% 调到 10% 时老用户不被打散。
 * 3. 定门禁：给出 1%→5%→25%→50%→100% 每档的观察窗口与放量条件，写清「错误率、P99、业务指标」三类门禁的阈值与最小样本量。
 * 4. 验效果：用两比例 z 检验判断转化率差异是否显著，用 SRM 卡方检验确认分流没偏，并说明为什么不能只看「涨了 0.3 个点」。
 * 5. 能回滚：给出自动回滚判据（绝对 + 相对双阈值）与回滚后的数据兼容处理，说明回滚为什么不能只回代码。
 * 【验收清单】
 * [ ] 发布形态有明确选型理由，资源成本与回滚耗时是算出来的数字，不是形容词。
 * [ ] 同一用户在整个放量过程中命中稳定，5% 的用户集合是 10% 的子集，有代码验证。
 * [ ] 每档放量都有观察窗口、最小样本量与门禁阈值，未达标能自动停在当前档位。
 * [ ] 效果判定有显著性检验与 SRM 校验，结论带置信区间，不是只看点估计。
 * [ ] 自动回滚有双阈值判据，且说明了回滚代码之外还要处理的数据与缓存兼容问题。
 * ============================================================
 */

