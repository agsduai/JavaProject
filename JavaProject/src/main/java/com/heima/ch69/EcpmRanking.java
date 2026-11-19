/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：69-04
 * 课名：eCPM 与排序
 * ------------------------------------------------------------
 * 知识点：
 *   - **eCPM（千次展示预期收入）是跨计费模式的统一货币**：`eCPM = 出价 × 预估点击率 × 1000`（CPC）、`eCPM = 出价 × 预估 CTR × 预估 CVR × 1000`（CPA）——**只有统一到 eCPM 才能把 CPC/CPA/CPM 广告放在一起排序**。
 *   - **pCTR/pCVR 的预估精度直接决定收入**：预估偏高 -> 排到前面但实际不点击（浪费曝光）；预估偏低 -> 好广告排不上（平台收入损失）——**校准（calibration）** 是必备（54-05 的 pCTR 校准）。
 *   - **多目标排序**：除了 eCPM，还要考虑**用户体验分**（内容质量/相关性）、**广告主 ROI**（转化质量）——**加权融合**（`score = eCPM^a × quality^b`），权重是运营旋钮。
 *   - **冷启动广告的处理**：新广告无历史 CTR -> 用**行业均值/相似广告**预估（54-10 的冷启动）；**探索配额**给新广告一定曝光以收集数据（54-10 的扶持池同款）。
 *   - **与 54 章的关系**：同一个排序框架，**目标函数不同**（推荐优化停留/转化，广告优化 eCPM）——技术上同源，产品上不同。
 *   - 易错点：纯 eCPM 排序（低质广告霸屏）；pCTR 未校准（系统性高估/低估）；冷启动无兜底（新广告零曝光）；不考虑广告密度（体验崩）。
 * ============================================================
 */

package com.heima.ch69;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class EcpmRanking {
    record Ad(String name, String model, double bidCents, double pctr, double pcvr,
              double qualityScore) {
        /** 统一到 eCPM（千次展示收入） */
        double ecpm() {
            return switch (model) {
                case "CPM" -> bidCents;
                case "CPC" -> bidCents * pctr * 1000;
                case "CPA" -> bidCents * pctr * pcvr * 1000;
                default -> 0;
            };
        }

        /** 融合分：eCPM 与质量分的加权（质量做乘性惩罚） */
        double blendedScore(double qualityWeight) {
            return ecpm() * Math.pow(qualityScore, qualityWeight);
        }
    }

    static void rank(List<Ad> ads, double qualityWeight) {
        List<Ad> sorted = new ArrayList<>(ads);
        sorted.sort(Comparator.comparingDouble(
                (Ad a) -> a.blendedScore(qualityWeight)).reversed());
        for (Ad a : sorted) {
            System.out.printf("  %-10s %-4s 出价=%6.1f pCTR=%.3f eCPM=%8.2f 质量=%.2f 融合=%8.2f%n",
                    a.name(), a.model(), a.bidCents(), a.pctr(), a.ecpm(),
                    a.qualityScore(), a.blendedScore(qualityWeight));
        }
    }

    /** 校准：把预估 pCTR 与真实 CTR 对齐（简化线性校准） */
    static double calibrate(double pctr, double alpha, double beta) {
        return Math.max(0, Math.min(1, alpha * pctr + beta));
    }

    public static void main(String[] args) {
        List<Ad> ads = List.of(
                new Ad("品牌 A（CPM）", "CPM", 30, 0.02, 0.01, 0.9),
                new Ad("效果 B（CPC）", "CPC", 200, 0.04, 0.03, 0.8),
                new Ad("效果的 C（CPA）", "CPA", 5000, 0.03, 0.05, 0.7),
                new Ad("低质 D（CPC）", "CPC", 300, 0.06, 0.01, 0.3));

        System.out.println("【统一到 eCPM 排序（质量权重 0）】");
        rank(ads, 0.0);
        System.out.println();
        System.out.println("【加入质量惩罚（质量权重 1.5）】");
        rank(ads, 1.5);
        System.out.println("  低质 D 被压下去，品牌 A 上升 —— 质量权重是体验与收入的旋钮");

        System.out.println();
        System.out.println("【pCTR 校准】");
        Map<String, String> calib = new LinkedHashMap<>();
        calib.put("未校准", "预估值系统性偏高 -> 排序靠前但实际不点");
        calib.put("校准后", "预估≈真实 -> eCPM 可信");
        calib.forEach((k, v) -> System.out.printf("  %-8s %s%n", k, v));
        System.out.printf("  示例：预估 0.04 经 alpha=0.9/beta=-0.002 校准 -> %.4f%n",
                calibrate(0.04, 0.9, -0.002));

        System.out.println();
        System.out.println("【冷启动广告】");
        System.out.println("  新广告无历史 CTR -> 用类目均值 0.02 做先验 + 探索配额 5% 曝光");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现三种计费模式的 eCPM 换算与排序，验证"CPM/CPC/CPA 广告可同榜比较"。
 * 2. 调整质量权重（0/0.5/1.5/3），观察排序变化，给出"体验与收入平衡"的建议档位。
 * 3. 思考题：为什么 pCTR 校准直接决定平台收入？构造"整体高估 30%"的算例，计算多付与少收的损失。
 * ============================================================
 */
