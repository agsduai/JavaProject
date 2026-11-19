/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：69-01
 * 课名：广告系统全景：从展示到结算
 * ------------------------------------------------------------
 * 知识点：
 *   - **广告系统的本质是"三方撮合"**：**广告主**（要曝光，愿出价）、**用户**（要看内容，不想被打扰）、**平台**（要提高收入，要保体验）——**每一次广告展示都是一次"竞价 + 排序 + 计费"**。
 *   - **计费模式的四种**：**CPM**（千次展示付费，品牌广告）、**CPC**（按点击付费，效果广告）、**CPA**（按转化付费，电商/金融）、**oCPX**（按目标转化出价，平台优化点击/转化）——**计费模式决定优化目标**（CPC 优化 CTR，CPA 优化 CVR）。
 *   - **广告链路五段**：**定向**（找出符合投放条件的广告）-> **粗排**（预估 eCPM 取 top）-> **精排**（精细预估 CTR/CVR）-> **竞价**（按机制定价）-> **计费**（曝光/点击/转化扣费）——**与 54 章推荐的漏斗同构，多了"竞价与计费"两层**。
 *   - **与自然流量混排**：广告与推荐内容放在同一个列表里（信息流），**用 eCPM 与推荐分统一排序**，同时控制**广告密度**（如每 5 条 1 个广告）——**体验与收入的平衡**（54-06 的重排思想）。
 *   - **与 54 章的关系**：推荐优化"用户满意度"，广告优化"平台收入"，两者共享召回/排序技术但目标函数不同——**很多平台用同一个排序框架，双目标融合**。
 *   - 易错点：只优化 CTR 不控质量（点击率高但转化差，广告主流失）；广告密度无上限（体验崩，42 章点踩率上升）；把曝光计费与点击计费混算；归因窗口不明确（转化算给谁）。
 * ============================================================
 */

package com.heima.ch69;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class AdSystemOverview {
    record Billing(String model, String payWhen, String optimizedMetric) {
    }

    static void billingModels() {
        System.out.println("【四种计费模式】");
        List<Billing> models = List.of(
                new Billing("CPM", "曝光时", "展示质量"),
                new Billing("CPC", "点击时", "CTR"),
                new Billing("CPA", "转化时", "CVR"),
                new Billing("oCPX", "转化时（平台优化）", "CTR×CVR"));
        for (Billing b : models) {
            System.out.printf("  %-5s 付费时机=%-12s 优化目标=%s%n",
                    b.model(), b.payWhen(), b.optimizedMetric());
        }
        System.out.println();
    }

    static void pipeline() {
        System.out.println("【广告链路五段（对比推荐四段）】");
        String[][] rows = {
                {"定向", "按人群/地域/时段筛广告", "≈ 召回（54-02）"},
                {"粗排", "预估 eCPM 取 top N", "≈ 粗排（54-05）"},
                {"精排", "精细预估 pCTR/pCVR", "≈ 精排"},
                {"竞价", "按机制定价（GSP/VCG）", "广告特有（69-03）"},
                {"计费", "曝光/点击/转化扣费", "广告特有（69-09）"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-4s %-22s %s%n", r[0], r[1], r[2]);
        }
        System.out.println();
    }

    static void mixedRanking() {
        System.out.println("【广告与自然内容混排】");
        record Item(String type, double score, double bidCents) {
            double ecpm() {
                return type.equals("ad") ? score * bidCents * 1000 : 0;
            }
        }
        List<Item> list = new ArrayList<>(List.of(
                new Item("organic", 0.92, 0),
                new Item("ad", 0.03, 200),
                new Item("organic", 0.88, 0),
                new Item("ad", 0.02, 500)));
        list.sort((a, b) -> Double.compare(
                b.type().equals("ad") ? b.ecpm() : b.score() * 1000,
                a.type().equals("ad") ? a.ecpm() : a.score() * 1000));
        System.out.println("  统一排序（广告用 eCPM，自然内容用推荐分×1000）：");
        for (Item i : list) {
            System.out.printf("    %-8s score=%.2f eCPM=%.0f 元/千次%n",
                    i.type(), i.score(), i.ecpm());
        }
        System.out.println("  实际另外要控：广告密度（每 5 条 1 个）、同类目打散（54-06）");

        System.out.println();
        System.out.println("【三方目标的张力】");
        Map<String, String> tension = new LinkedHashMap<>();
        tension.put("广告主", "要 ROI：曝光要转化成订单");
        tension.put("用户", "要好体验：不想看无关/低质广告");
        tension.put("平台", "要收入：短期提价，长期伤生态");
        tension.forEach((k, v) -> System.out.printf("  %-4s %s%n", k, v));
    }

    public static void main(String[] args) {
        billingModels();
        pipeline();
        mixedRanking();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 画出广告链路五段图，标注每段的输入输出与优化目标，对比 54 章推荐漏斗的差异。
 * 2. 为信息流设计"广告密度 + eCPM 混排"规则：每 5 条插 1 个广告且同类目打散，用 20 条内容模拟排序。
 * 3. 思考题：为什么"只优化 CTR"会伤害广告生态？从"点击欺诈/标题党/转化差"导致的广告主流失分析。
 * ============================================================
 */
