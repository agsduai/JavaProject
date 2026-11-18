/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：54-06
 * 课名：重排：多样性、打散与业务规则
 * ------------------------------------------------------------
 * 知识点：
 *   - **精排分数最高 ≠ 最终展示顺序**：Top 结果全是同类目（10 个手机壳）体验崩坏——**重排解决"模型不知道的体验规则"**：类目打散（相邻不同类目）、同店打散、置顶插坑（运营/广告位）、负反馈过滤（已曝光未点击的降权）。
 *   - **多样性算法**：**MMR**（最大边际相关：相关性与多样性加权轮选）——每一步选"与已选集合差异大且相关"的项；简单打散（滑动窗口内类目不重复）是工程常用版。
 *   - **业务规则的优先级语义**：置顶 > 强插 > 打散 > 过滤——**规则要可配置可审计**（运营改规则不该改代码，44-08 配置化思想）；**每条规则的效果要可归因**（规则命中日志 + A/B）。
 *   - **与精排分数的兼容**：打散会牺牲部分 pCTR——**打散强度是体验与收入的旋钮**（A/B 度量最优档位 54-08）。
 *   - 易错点：规则硬编码（改规则=发版）；打散无上限（相关性崩）；置顶滥用（用户信任度下降，42 章点踩率上升）；曝光过滤内存无界（54-07）。
 * ============================================================
 */

package com.heima.ch54;

import java.util.ArrayList;
import java.util.List;

// ==================== 示例代码 ====================

public class RerankDemo {
    record Item(long id, String category, double score) {
    }

    /** 滑动窗口打散：窗口 W 内同类目最多 1 个 */
    static List<Item> diversify(List<Item> ranked, int window) {
        List<Item> out = new ArrayList<>();
        List<Item> pool = new ArrayList<>(ranked);
        while (!pool.isEmpty()) {
            boolean placed = false;
            for (int i = 0; i < pool.size(); i++) {
                Item candidate = pool.get(i);
                boolean clash = out.stream()
                        .skip(Math.max(0, out.size() - window + 1))
                        .anyMatch(p -> p.category().equals(candidate.category()));
                if (!clash) {
                    out.add(candidate);
                    pool.remove(i);
                    placed = true;
                    break;
                }
            }
            if (!placed) {                  // 全部冲突 -> 放最优先的
                out.add(pool.remove(0));
            }
        }
        return out;
    }

    public static void main(String[] args) {
        List<Item> ranked = List.of(
                new Item(1, "case", 0.9), new Item(2, "case", 0.8),
                new Item(3, "charger", 0.7), new Item(4, "case", 0.6));
        List<Item> out = diversify(ranked, 2);
        for (Item i : out) {
            System.out.printf("  %d [%s] %.2f%n", i.id(), i.category(), i.score());
        }
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现完整重排器（窗口打散 + 置顶插坑 + 曝光过滤），输出重排前后对比与牺牲的 pCTR 总量。
 * 2. 实现 MMR 变版（相关性与多样性加权），对比窗口打散的多样性指标（类目熵）。
 * 3. 思考题：为什么"打散强度"要 A/B 调优而不是拍脑袋？从短期 CTR 损失与长期留存收益的时间维度分析。
 * ============================================================
 */
