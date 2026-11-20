/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：82-06
 * 课名：指标对比与自动回滚判据
 * ------------------------------------------------------------
 * 知识点：
 *   - **自动回滚的第一原则：判据要能抵抗噪声**——**单点抖动就回滚会造成「回滚风暴」**，**必须用「连续 N 个窗口」或「滑动窗口累计」**。
 *   - **绝对阈值 + 相对阈值双保险**：**绝对阈值防「本来就差」**（错误率 > 1%），**相对阈值防「比基线差很多」**（错误率是基线的 3 倍）——**大促期间基线本身就高，只看绝对阈值会漏**。
 *   - **业务指标的回滚要更保守**：**技术指标（错误率/延迟）可以秒级判定**，**业务指标（转化率）有天然波动，要等样本量够 + 连续 2~3 个窗口都差**才回滚。
 *   - **回滚动作要分级**：**一级 = 关开关（亚秒）**、**二级 = 切流量回上一档（秒级）**、**三级 = 回滚代码（分钟级）**——**能用一级解决就别用三级**。
 *   - **回滚后必须验证**：**回滚动作发出 ≠ 已恢复**，**要看指标真的回到基线**——**很多事故是「回滚了但没生效」，因为配置缓存没刷新**。
 *   - 易错点：单窗口判定就回滚（噪声触发）；只看绝对阈值；回滚后不看指标确认；回滚了代码但缓存/连接池里还是旧配置。
 * ============================================================
 */

package com.heima.ch82;

// ==================== 示例代码 ====================

import java.util.ArrayList;
import java.util.List;

public class AutoRollback {
    // 一个观测窗口的数据
    record Window(int idx, long requests, double errRate, double p99Ms, double cvr) {
    }

    record Baseline(double errRate, double p99Ms, double cvr) {
    }

    // 回滚判据：连续 N 个窗口不达标才回滚
    static class Judge {
        final Baseline base;
        final int consecutive;
        final List<String> history = new ArrayList<>();
        int badStreak;

        Judge(Baseline base, int consecutive) {
            this.base = base;
            this.consecutive = consecutive;
        }

        boolean fired;

        // 返回 null 表示继续观察，否则返回回滚原因
        String evaluate(Window w) {
            if (fired) {
                history.add("窗口" + w.idx() + "：已回滚，不再判定");
                return "已回滚";
            }
            List<String> reasons = new ArrayList<>();

            // 技术指标：绝对阈值
            if (w.errRate() > 0.01) {
                reasons.add(String.format("错误率 %.2f%% > 绝对阈值 1.00%%", w.errRate() * 100));
            }
            // 技术指标：相对阈值（基线 3 倍）
            if (w.errRate() > base.errRate() * 3) {
                reasons.add(String.format("错误率是基线 %.1f 倍（%.2f%% vs %.2f%%）",
                        w.errRate() / base.errRate(), w.errRate() * 100, base.errRate() * 100));
            }
            if (w.p99Ms() > base.p99Ms() * 2) {
                reasons.add(String.format("P99 是基线 %.1f 倍（%.0fms vs %.0fms）",
                        w.p99Ms() / base.p99Ms(), w.p99Ms(), base.p99Ms()));
            }
            // 业务指标：相对跌幅 > 20%
            if (w.cvr() < base.cvr() * 0.8) {
                reasons.add(String.format("转化率跌到基线的 %.0f%%（%.2f%% vs %.2f%%）",
                        w.cvr() / base.cvr() * 100, w.cvr() * 100, base.cvr() * 100));
            }

            if (reasons.isEmpty()) {
                badStreak = 0;
                history.add("窗口" + w.idx() + "：正常");
                return null;
            }
            badStreak++;
            String joined = String.join("；", reasons);
            if (badStreak >= consecutive) {
                fired = true;
                history.add("窗口" + w.idx() + "：连续 " + badStreak + " 个窗口不达标 -> 回滚");
                return joined + "（连续 " + badStreak + " 个窗口）";
            }
            history.add("窗口" + w.idx() + "：不达标但只连续 " + badStreak + " 个，继续观察");
            return null;
        }
    }

    public static void main(String[] args) {
        Baseline base = new Baseline(0.002, 250, 0.030);

        System.out.println("【场景1】单点抖动：第 3 个窗口 P99 突然飙到 900ms，随后立刻恢复");
        Judge j1 = new Judge(base, 2);
        List<Window> run1 = List.of(
                new Window(1, 50_000, 0.0020, 255, 0.0301),
                new Window(2, 50_000, 0.0022, 248, 0.0299),
                new Window(3, 50_000, 0.0021, 900, 0.0300),   // 抖动
                new Window(4, 50_000, 0.0019, 252, 0.0302),
                new Window(5, 50_000, 0.0020, 249, 0.0300));
        for (Window w : run1) {
            String r = j1.evaluate(w);
            System.out.printf("  窗口%d P99=%4.0fms -> %s%n", w.idx(), w.p99Ms(),
                    r == null ? "继续" : "回滚：" + r);
        }
        System.out.println("  结论：只抖动一个窗口，被「连续 2 个窗口」规则挡住了，不回滚");

        System.out.println();
        System.out.println("【场景2】真的坏了：从第 2 个窗口起错误率一直是基线的 3.7 倍以上");
        Judge j2 = new Judge(base, 2);
        List<Window> run2 = List.of(
                new Window(1, 50_000, 0.0020, 250, 0.0300),
                new Window(2, 50_000, 0.0075, 260, 0.0298),   // 3.75 倍
                new Window(3, 50_000, 0.0080, 265, 0.0295),   // 连续第 2 个
                new Window(4, 50_000, 0.0082, 270, 0.0290));
        for (Window w : run2) {
            String r = j2.evaluate(w);
            System.out.printf("  窗口%d 错误率=%.2f%% -> %s%n", w.idx(), w.errRate() * 100,
                    r == null ? "继续" : (r.equals("已回滚") ? "已回滚，后续窗口不再判定" : "回滚：" + r));
        }

        System.out.println();
        System.out.println("【场景3】业务指标跌了：转化率 3.00% -> 2.30%，技术指标全正常");
        Judge j3 = new Judge(base, 2);
        List<Window> run3 = List.of(
                new Window(1, 50_000, 0.0021, 251, 0.0230),
                new Window(2, 50_000, 0.0020, 249, 0.0228),
                new Window(3, 50_000, 0.0022, 253, 0.0231));
        for (Window w : run3) {
            String r = j3.evaluate(w);
            System.out.printf("  窗口%d 转化率=%.2f%% -> %s%n", w.idx(), w.cvr() * 100,
                    r == null ? "继续" : (r.equals("已回滚") ? "已回滚，后续窗口不再判定" : "回滚：" + r));
        }
        System.out.println("  结论：技术指标全绿也会回滚——「能跑但没人买」同样是事故");

        System.out.println();
        System.out.println("【回滚分级】");
        System.out.println("  一级 关开关    ：<1s，适用「新逻辑可以整体关掉」");
        System.out.println("  二级 切流量    ：~5s，适用「旧版本实例还在」");
        System.out.println("  三级 回滚代码  ：2~10min，适用「开关兜不住的结构性问题」");
        System.out.println("  原则：能一级解决就别用三级；每次回滚后必须确认指标真的回到基线");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 把 consecutive 从 2 改成 1，重跑场景1，观察抖动是否触发回滚，说明「回滚风暴」是怎么来的。
 * 2. 给 Judge 加一条「连续 5 个窗口错误率缓慢上升」的漂移检测，说明为什么单窗口判定抓不到慢故障。
 * 3. 思考题：为什么业务指标的回滚阈值要用「相对基线的百分比」而不是绝对值？从「不同业务的转化率量级不同」分析。
 * ============================================================
 */

