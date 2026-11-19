/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：69-03
 * 课名：竞价机制：GFP、GSP 与 VCG
 * ------------------------------------------------------------
 * 知识点：
 *   - **竞价机制决定"谁赢、付多少"**：**GFP（广义第一价格）**——出价最高者赢，付自己的出价（简单，但广告主会"压价试探"）；**GSP（广义第二价格）**——最高者赢，付**第二名的出价**（主流，激励说真话）；**VCG**——每个广告位付"自己造成的他人损失"（理论最优，复杂，实际少用）。
 *   - **GSP 的核心激励**：广告主**报出自己的真实估值是最优策略**（因为付费由别人决定）——**减少策略性压价，提高平台收入与配置效率**。
 *   - **多个广告位的定价**：GSP 下第 i 名的付费 ≈ **第 i+1 名的出价 × (第 i+1 名的点击率 ÷ 第 i 名的点击率)**——**点击率高的位置要付更多**（位置溢价）；这是"按质量调整"的定价，不是简单的第二价格。
 *   - **质量分（Quality Score）**：平台把 **出价 × 预估 CTR** 作为排序依据（而不是纯出价）——**低质高价广告会被高质低价挤出**，保护用户体验（69-01 的三方平衡）。
 *   - **底价（reserve price）**：低于底价不成交（保平台收入下限）；**动态底价**按流量质量调整（高价值流量底价更高）。
 *   - 易错点：把 GSP 当"付第二高出价"（实际要按点击率比值调整）；只看出价不看质量分（低质广告霸屏）；无底价（垃圾流量也被低价买走）；机制频繁变更（广告主策略混乱）。
 * ============================================================
 */

package com.heima.ch69;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class AuctionMechanism {
    record Bidder(String name, double bidCents, double pctr) {
        double qualityScore() {
            return bidCents * pctr;
        }
    }

    record Slot(String name, int position, double ctrFactor) {
    }

    record Result(String slot, String winner, double payCents, String model) {
    }

    /** GFP：按质量分排序，付自己的出价 */
    static List<Result> gfp(List<Bidder> bidders, List<Slot> slots) {
        List<Bidder> ranked = new ArrayList<>(bidders);
        ranked.sort(Comparator.comparingDouble(Bidder::qualityScore).reversed());
        List<Result> out = new ArrayList<>();
        for (int i = 0; i < Math.min(slots.size(), ranked.size()); i++) {
            Bidder b = ranked.get(i);
            out.add(new Result(slots.get(i).name(), b.name(), b.bidCents(), "GFP"));
        }
        return out;
    }

    /** GSP：第 i 名付 第 i+1 名的 (出价 × 点击率比) */
    static List<Result> gsp(List<Bidder> bidders, List<Slot> slots) {
        List<Bidder> ranked = new ArrayList<>(bidders);
        ranked.sort(Comparator.comparingDouble(Bidder::qualityScore).reversed());
        List<Result> out = new ArrayList<>();
        for (int i = 0; i < Math.min(slots.size(), ranked.size()); i++) {
            Bidder me = ranked.get(i);
            Bidder next = i + 1 < ranked.size() ? ranked.get(i + 1) : null;
            double pay;
            if (next == null) {
                pay = me.bidCents() * 0.5;                       // 无下一位：按底价折扣
            } else {
                pay = next.bidCents() * (next.pctr() / me.pctr());
                pay = Math.min(pay, me.bidCents());               // 不超过自己出价
            }
            out.add(new Result(slots.get(i).name(), me.name(), pay, "GSP"));
        }
        return out;
    }

    public static void main(String[] args) {
        List<Bidder> bidders = List.of(
                new Bidder("广告主 A", 300, 0.02),
                new Bidder("广告主 B", 200, 0.05),
                new Bidder("广告主 C", 100, 0.08));
        List<Slot> slots = List.of(
                new Slot("首位", 1, 1.0),
                new Slot("第 2 位", 2, 0.7));

        System.out.println("【出价与质量分（出价 × pCTR）】");
        bidders.forEach(b -> System.out.printf("  %-8s 出价=%.0f pCTR=%.2f 质量分=%.1f%n",
                b.name(), b.bidCents(), b.pctr(), b.qualityScore()));

        System.out.println();
        System.out.println("【GFP 结果】");
        gfp(bidders, slots).forEach(r -> System.out.printf("  %-6s %-8s 付 %.1f 分%n",
                r.slot(), r.winner(), r.payCents()));

        System.out.println();
        System.out.println("【GSP 结果（注意付费不是简单取第二名出价）】");
        gsp(bidders, slots).forEach(r -> System.out.printf("  %-6s %-8s 付 %.1f 分%n",
                r.slot(), r.winner(), r.payCents()));

        System.out.println();
        System.out.println("  观察：C 出价最低但质量分最高 -> 排在首位（质量分保护体验）");
        System.out.println("  GSP 付费 = 下一位出价 × (下一位 pCTR / 我的 pCTR)，并按我的出价封顶");

        System.out.println();
        System.out.println("【三种机制对比】");
        Map<String, String> compare = new LinkedHashMap<>();
        compare.put("GFP", "付自己出价；简单，但易压价试探");
        compare.put("GSP", "按下一名调整付费；激励说真话，主流");
        compare.put("VCG", "付自己造成的他人损失；理论最优，实现复杂");
        compare.forEach((k, v) -> System.out.printf("  %-4s %s%n", k, v));
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现 GFP 与 GSP 两种机制，用同一组出价对比"排序、 winner、付费"，验证 GSP 付费低于 GFP。
 * 2. 构造"压价"场景：某广告主把出价从 300 降到 250，观察 GSP 下是否仍能保住首位（说明激励相容）。
 * 3. 思考题：为什么 GSP 要用"质量分"排序而不是纯出价？从"用户体验"与"平台长期收入"两个角度分析。
 * ============================================================
 */
