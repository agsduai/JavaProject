/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：83-04
 * 课名：价格分摊与退款分摊
 * ------------------------------------------------------------
 * 知识点：
 *   - **分摊是「把整单优惠拆到行」的过程，目的是让退款能按行算**：**用户退一件时，退的钱必须等于「这一行的实付」**，而不是「原价按比例估算」。
 *   - **各行独立四舍五入必然不守恒**：**每行都进 1 分，行数一多，分摊之和就大于优惠总额**，财务立刻对不上——**必须用「最大余额法」：先按比例取整，再把余下的分按小数部分从大到小补齐**。
 *   - **最大余额法的三个性质**：**总额严格守恒**、**每行误差 < 1 分**、**分摊额不超过该行金额**（否则会出现负数行）。
 *   - **退款分摊要用「原分摊额」而不是「重新按比例算」**：**订单退掉一行后，剩余行的分摊额不能变**，否则「先退 A 再退 B」和「一起退」退的钱不一样，用户会来吵架。
 *   - **运费也要分摊**：**包邮订单退到不满足包邮门槛时，要扣回运费**——**运费分摊按行金额比例，扣回时按同样的比例扣**，两边口径必须一致。
 *   - 易错点：分摊用 `double` 累加导致总额差几分；退部分行时重新分摊；优惠金额大于某行金额导致该行为负；运费分摊与优惠分摊用了不同算法。
 * ============================================================
 */

package com.heima.ch83;

// ==================== 示例代码 ====================

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class PriceAllocation {
    record Line(String sku, long amountFen) {
    }

    // 分摊结果要带「余数」，排序补齐时才知道给谁
    record Alloc(int idx, long baseFen, long rem, long finalFen) {
    }

    static String yuan(long fen) {
        return String.format("%.2f", fen / 100.0);
    }

    // 朴素做法：每行独立四舍五入，不保证守恒
    static List<Long> naive(List<Line> lines, long total) {
        long sum = 0;
        for (Line l : lines) {
            sum += l.amountFen();
        }
        List<Long> out = new ArrayList<>();
        for (Line l : lines) {
            out.add(Math.round(total * l.amountFen() / (double) sum));
        }
        return out;
    }

    // 最大余额法：先取整，再把剩余的分按小数部分从大到小补齐
    static List<Alloc> largestRemainder(List<Line> lines, long total) {
        long sum = 0;
        for (Line l : lines) {
            sum += l.amountFen();
        }
        List<Alloc> out = new ArrayList<>();
        long given = 0;
        for (int i = 0; i < lines.size(); i++) {
            long num = total * lines.get(i).amountFen();
            long base = num / sum;
            long rem = num % sum;
            out.add(new Alloc(i, base, rem, base));
            given += base;
        }
        long left = total - given;
        // 余数大的先补；余数相同时按行号，保证结果可复现
        List<Alloc> byRem = new ArrayList<>(out);
        byRem.sort(Comparator.comparingLong(Alloc::rem).reversed()
                .thenComparingInt(Alloc::idx));
        for (int i = 0; i < left; i++) {
            Alloc a = byRem.get(i);
            out.set(a.idx(), new Alloc(a.idx(), a.baseFen(), a.rem(), a.finalFen() + 1));
        }
        return out;
    }

    public static void main(String[] args) {
        List<Line> lines = new ArrayList<>();
        lines.add(new Line("SKU-A", 43900));
        lines.add(new Line("SKU-B", 12900));
        lines.add(new Line("SKU-C", 8800));
        long subtotal = 0;
        for (Line l : lines) {
            subtotal += l.amountFen();
        }
        long discount = 10000;

        System.out.printf("【1. 订单原价 %s 元，优惠 %s 元，分摊到 3 行】%n",
                yuan(subtotal), yuan(discount));
        for (Line l : lines) {
            System.out.printf("  %-6s 原价 %9s  占比 %6.2f%%%n",
                    l.sku(), yuan(l.amountFen()), l.amountFen() * 100.0 / subtotal);
        }

        List<Long> naiveAlloc = naive(lines, discount);
        long naiveSum = 0;
        for (long v : naiveAlloc) {
            naiveSum += v;
        }
        System.out.printf("%n【2. 朴素四舍五入】%n");
        for (int i = 0; i < lines.size(); i++) {
            System.out.printf("  %-6s 分摊 %8s%n", lines.get(i).sku(), yuan(naiveAlloc.get(i)));
        }
        System.out.printf("  合计 %s，与优惠总额差 %d 分 -> %s%n",
                yuan(naiveSum), naiveSum - discount,
                naiveSum == discount ? "守恒" : "不守恒，财务对不上");

        List<Alloc> lr = largestRemainder(lines, discount);
        long lrSum = 0;
        for (Alloc a : lr) {
            lrSum += a.finalFen();
        }
        System.out.printf("%n【3. 最大余额法】%n");
        System.out.printf("  %-6s %-10s %-10s %-10s %s%n", "行", "理论值", "取整", "补余后", "该行实付");
        for (Alloc a : lr) {
            long amount = lines.get(a.idx()).amountFen();
            System.out.printf("  %-6s %-10s %-10s %-10s %s%n",
                    lines.get(a.idx()).sku(),
                    String.format("%.4f", discount * amount / (double) subtotal),
                    yuan(a.baseFen()), yuan(a.finalFen()), yuan(amount - a.finalFen()));
        }
        System.out.printf("  合计 %s，与优惠总额差 %d 分 -> %s%n",
                yuan(lrSum), lrSum - discount, lrSum == discount ? "严格守恒" : "不守恒");

        System.out.println();
        System.out.println("【4. 退单行 B：退的是「B 的实付」，不是按比例重算】");
        long bPaid = lines.get(1).amountFen() - lr.get(1).finalFen();
        long reAllocOnRest = Math.round(discount * lines.get(1).amountFen()
                / (double) subtotal);
        System.out.printf("  B 原价 %s，分摊 %s，实付 %s%n",
                yuan(lines.get(1).amountFen()), yuan(lr.get(1).finalFen()), yuan(bPaid));
        System.out.printf("  若退 B 时按比例重算优惠：%s（口径不一致，会多退/少退）%n", yuan(reAllocOnRest));
        System.out.println("  正确做法：退款额 = 行原价 - 行分摊额，分摊额在下单时就固定住。");

        System.out.println();
        System.out.println("【5. 先退 A 再退 B vs 一起退 A+B】");
        long refundA = lines.get(0).amountFen() - lr.get(0).finalFen();
        long refundB = bPaid;
        long together = refundA + refundB;
        System.out.printf("  先退 A %s，再退 B %s，合计 %s%n", yuan(refundA), yuan(refundB), yuan(together));
        System.out.printf("  一起退 A+B %s%n", yuan(refundA + refundB));
        System.out.println("  两者相等，因为每行分摊额是固定的；若退款时重算，两者就会不等。");

        System.out.println();
        System.out.println("【6. 分摊额不得超过该行金额（否则出现负数行）】");
        System.out.println("  场景：1.00 元的小件 + 9.00 元的大件，整单优惠 5.00 元");
        List<Line> tiny = new ArrayList<>();
        tiny.add(new Line("SKU-X", 100));    // 1.00 元
        tiny.add(new Line("SKU-Y", 900));    // 9.00 元
        long smallDiscount = 500;

        // 错误做法：按「行数」均摊，不看每行值多少钱
        System.out.println();
        System.out.println("  错误做法：按行数均摊（每行 2.50 元）");
        long each = smallDiscount / tiny.size();
        boolean negative = false;
        for (Line l : tiny) {
            long pay = l.amountFen() - each;
            negative |= pay < 0;
            System.out.printf("    %-6s 原价 %6s 摊 %6s 实付 %6s%s%n",
                    l.sku(), yuan(l.amountFen()), yuan(each), yuan(pay),
                    pay < 0 ? "   <- 实付为负，财务直接报错" : "");
        }
        System.out.printf("  按行均摊必然出现负数行：%s%n", negative ? "已复现" : "未复现");
        System.out.println("  1.00 元的商品被摊了 2.50 元优惠，等于「卖一单倒贴 1.50 元」。");

        // 正确做法：按金额比例摊，小额行自然不会超
        System.out.println();
        System.out.println("  正确做法：按行金额比例摊（金额大的摊得多，小额行摊得少）");
        List<Alloc> fixed = largestRemainder(tiny, smallDiscount);
        long sum = 0;
        long maxRatio = 0;
        for (Alloc a : fixed) {
            long amount = tiny.get(a.idx()).amountFen();
            sum += a.finalFen();
            maxRatio = Math.max(maxRatio, a.finalFen() * 100 / amount);
            System.out.printf("    %-6s 原价 %6s 摊 %6s 实付 %6s  摊掉本行 %d%%%n",
                    tiny.get(a.idx()).sku(), yuan(amount), yuan(a.finalFen()),
                    yuan(amount - a.finalFen()), a.finalFen() * 100 / amount);
        }
        System.out.printf("    合计 %s 元，与优惠总额 %s 元差 %d 分 -> %s%n",
                yuan(sum), yuan(smallDiscount), sum - smallDiscount,
                sum == smallDiscount ? "守恒" : "不守恒");
        System.out.printf("  按金额比例摊时，单行最多摊掉本行 100%%（这里最多 %d%%），不可能出现负行%n",
                maxRatio);
        System.out.println("  所以「封顶」不是额外规则，而是按金额比例摊的自然后果；");
        System.out.println("  只有当你按件数、按行数、按固定额摊时，才需要额外的封顶 + 余量再分配。");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 把订单改成 7 行、优惠改成 9999 分，验证最大余额法仍然严格守恒且每行误差 < 1 分。
 * 2. 给分摊加「运费分摊」：运费 800 分按行金额比例分摊，验证退到不满足包邮时能按同样比例扣回。
 * 3. 思考题：如果某行金额很小（1 分），却被补余补了 1 分，该行实付变成 0，这算不算 bug？怎么处理更合理？
 * ============================================================
 */

