/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：83-02
 * 课名：四类优惠的算法与取整
 * ------------------------------------------------------------
 * 知识点：
 *   - **满减**：**达到门槛就减固定金额，多档取最高可命中档**——**门槛按原价小计判，不按件数**，**多档之间是「取最优」不是「累加」**。
 *   - **阶梯优惠**：**按件数或金额分档**（满 2 件 9 折、满 3 件 85 折）——**档位要连续可算**，**不能出现「买 4 件比买 3 件还贵」的断档**。
 *   - **折扣（带上限）**：**按比例减，但必须封顶**——**不封顶的折扣在大额订单上会直接击穿毛利**，**封顶值要与预算联动**。
 *   - **立减**：**每件减固定额或整单减固定额**——**最容易做，也最容易被刷**（凑单后整单退），**必须配合限购与退款回收**。
 *   - **三种取整方式的差异只在小数位上，但方向不同**：**四舍五入**对用户友好、**向下取整**对商家有利、**银行家舍入（四舍六入五成双）**在大量结算时偏差最小——**同一系统内只能选一种**。
 *   - 易错点：用 `double` 存金额导致 0.1+0.2 类误差；折扣上限按「优惠额」封顶却忘了同步改分摊；阶梯档位写成 `if` 链漏了等于号，恰好 3 件时不打折。
 * ============================================================
 */

package com.heima.ch83;

// ==================== 示例代码 ====================

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

public class DiscountRounding {
    record Cart(long subtotalFen, int qty) {
    }

    // 满减：多档取「最高可命中档」，不是逐档累加
    record Tier(long thresholdFen, long reduceFen) {
    }

    static long fullReduce(long subtotal, List<Tier> tiers) {
        long best = 0;
        for (Tier t : tiers) {
            if (subtotal >= t.thresholdFen()) {
                best = Math.max(best, t.reduceFen());
            }
        }
        return best;
    }

    // 阶梯：按件数分档，档位必须单调，不能出现断档
    static int ladderPermille(int qty) {
        if (qty >= 5) {
            return 800;
        }
        if (qty >= 3) {
            return 850;
        }
        if (qty >= 2) {
            return 900;
        }
        return 1000;
    }

    // 折扣带上限：先按比例算，再与上限取小
    static long discountWithCap(long subtotal, int permille, long capFen) {
        long raw = Math.round(subtotal * permille / 1000.0);
        long cut = subtotal - raw;
        return Math.min(cut, capFen);
    }

    static long halfUp(double v) {
        return Math.round(v);
    }

    static long floor(double v) {
        return (long) Math.floor(v);
    }

    static long banker(double v) {
        return BigDecimal.valueOf(v).setScale(0, RoundingMode.HALF_EVEN).longValue();
    }

    static String yuan(long fen) {
        return String.format("%.2f", fen / 100.0);
    }

    public static void main(String[] args) {
        List<Tier> tiers = new ArrayList<>();
        tiers.add(new Tier(30000, 5000));
        tiers.add(new Tier(50000, 10000));
        tiers.add(new Tier(80000, 18000));

        System.out.println("【1. 满减：多档取最优，不是累加】");
        for (long s : new long[]{29999, 30000, 49999, 50000, 80000, 120000}) {
            System.out.printf("  原价 %9s  ->  减 %7s  ->  应付 %9s%n",
                    yuan(s), yuan(fullReduce(s, tiers)), yuan(s - fullReduce(s, tiers)));
        }
        System.out.println("  500 元订单命中「满 500 减 100」，不会同时叠加「满 300 减 50」。");

        System.out.println();
        System.out.println("【2. 阶梯：按件数分档，档位要单调】");
        long unit = 9900;
        for (int q = 1; q <= 6; q++) {
            int pm = ladderPermille(q);
            long total = Math.round(unit * q * pm / 1000.0);
            System.out.printf("  %d 件  折扣 %3d‰  应付 %9s  折合单价 %7s%n",
                    q, pm, yuan(total), yuan(total / q));
        }
        System.out.println("  折合单价随件数单调下降，说明档位没有断档。");

        System.out.println();
        System.out.println("【3. 折扣带上限：不封顶会击穿毛利】");
        for (long s : new long[]{10000, 50000, 100000, 500000}) {
            long noCap = s - Math.round(s * 900 / 1000.0);
            long capped = discountWithCap(s, 900, 10000);
            System.out.printf("  原价 %9s  不封顶减 %8s  封顶 100 元减 %8s  差额 %9s%n",
                    yuan(s), yuan(noCap), yuan(capped), yuan(noCap - capped));
        }

        System.out.println();
        System.out.println("【4. 三种取整方式：只在恰好 .5 上分歧】");
        System.out.printf("  %-10s %-10s %-10s %-10s %s%n", "金额(分)", "半值", "四舍五入", "银行家", "两者是否不同");
        for (long fen : new long[]{1001, 1002, 1003, 1005, 1007}) {
            double v = fen * 0.5;
            long h = halfUp(v);
            long b = banker(v);
            System.out.printf("  %-10d %-10s %-10d %-10d %s%n",
                    fen, String.format("%.1f", v), h, b, h != b ? "是" : "否");
        }
        System.out.println("  恰好 .5 才分歧：500.5 向上进成 501，银行家进成 500（取偶）；501.5 两者都是 502。");
        System.out.println("  向下取整则不参与比较：它永远比四舍五入少，是商家有利、用户吃亏的单边规则。");

        long hSum = 0;
        long bSum = 0;
        for (long fen = 1; fen <= 200; fen++) {
            hSum += halfUp(fen * 0.5);
            bSum += banker(fen * 0.5);
        }
        System.out.printf("  1~200 分各取一半：四舍五入合计 %d 分，银行家合计 %d 分，差 %d 分%n",
                hSum, bSum, hSum - bSum);
        System.out.println("  银行家把「逢 5 必进」的系统性偏差抵掉一半，大量结算时总额更接近真实值。");

        System.out.println();
        System.out.println("【5. 四类优惠各算一遍：同一笔 600 元 3 件的订单】");
        long s = 60000;
        int q = 3;
        System.out.printf("  满减（满 500 减 100）      -%s%n", yuan(fullReduce(s, tiers)));
        System.out.printf("  阶梯（3 件 85 折）         -%s%n", yuan(s - Math.round(s * ladderPermille(q) / 1000.0)));
        System.out.printf("  折扣（9 折上限 100 元）    -%s%n", yuan(discountWithCap(s, 900, 10000)));
        System.out.printf("  立减（整单减 20 元）       -%s%n", yuan(2000));
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 给满减加一档「满 1000 减 250」，验证 1200 元订单只减 250 而不是叠加成 400。
 * 2. 把阶梯档位改成「满 3 件 85 折、满 4 件 8 折」，验证折合单价仍然单调下降。
 * 3. 思考题：折扣上限如果按「优惠额」封顶，但分摊时按「比例」分摊，会出什么不一致？举一个具体数字。
 * ============================================================
 */

