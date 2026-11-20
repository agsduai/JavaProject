/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：83-03
 * 课名：叠加互斥与最优组合
 * ------------------------------------------------------------
 * 知识点：
 *   - **券不是「越多越便宜」，而是「有约束的组合优化」**：**互斥组（同组只能选一张）+ 门槛（原价判定）+ 叠加顺序**，三者一起决定最终优惠。
 *   - **互斥要按「组」而不是按「张」**：**全场券与品类券常常同组互斥**，**但运费券、积分、会员折扣通常是独立维度**，混在一起配就会出现「两张券同时生效」。
 *   - **贪心按面额选券会算贵**：**面额大的券可能门槛高、可能挤掉了能叠加的折扣**，**正确做法是小规模穷举（券数 ≤ 5 时 2^n × n! 完全可算）**。
 *   - **折扣券与满减券的顺序必须固定**：**折扣先做**，满减作用在更小的金额上、优惠更少；**满减先做**，折扣作用在更小的金额上、优惠也更少——**两种都亏，所以要把「门槛按原价、顺序按规则」写死，再穷举选最优**。
 *   - **最优解要能解释给运营**：**输出「选了哪几张、按什么顺序、省了多少」**，而不是只给一个数字；**运营要能看懂为什么没选那张面额更大的券**。
 *   - 易错点：只判门槛不判互斥（两张同组券同时生效）；用贪心代替穷举且没验证差额；券数超过 8 还硬穷举（组合爆炸，要换整数规划或剪枝）。
 * ============================================================
 */

package com.heima.ch83;

// ==================== 示例代码 ====================

import java.util.ArrayList;
import java.util.List;

public class CouponCombination {
    // permille=0 表示减法券；permille>0 表示折扣券（带封顶）
    record Coupon(String id, String group, long thresholdFen, long reduceFen,
                  int permille, long capFen) {
        boolean isDiscount() {
            return permille > 0;
        }

        // 面额：减法券就是固定额，折扣券按原价折算（只用于贪心排序）
        long face(long subtotal) {
            if (!isDiscount()) {
                return reduceFen;
            }
            long cut = subtotal - Math.round(subtotal * permille / 1000.0);
            return Math.min(cut, capFen);
        }
    }

    static String yuan(long fen) {
        return String.format("%.2f", fen / 100.0);
    }

    // 按给定顺序求优惠额；门槛一律按「原价小计」判，这是关键约定
    static long apply(List<Coupon> order, long subtotal) {
        long amount = subtotal;
        long saved = 0;
        for (Coupon c : order) {
            if (subtotal < c.thresholdFen()) {
                continue;                       // 门槛不满足，这张券作废
            }
            long cut;
            if (c.isDiscount()) {
                cut = Math.min(amount - Math.round(amount * c.permille() / 1000.0), c.capFen());
            } else {
                cut = Math.min(c.reduceFen(), amount);
            }
            cut = Math.max(cut, 0);
            amount -= cut;
            saved += cut;
        }
        return saved;
    }

    // 互斥校验：同一 group 最多出现一张
    static boolean exclusiveOk(List<Coupon> picked) {
        List<String> groups = new ArrayList<>();
        for (Coupon c : picked) {
            if (c.group() == null || c.group().isEmpty()) {
                continue;
            }
            if (groups.contains(c.group())) {
                return false;
            }
            groups.add(c.group());
        }
        return true;
    }

    // 穷举：所有子集 x 所有顺序，取最大优惠
    static long best(List<Coupon> all, long subtotal, List<Coupon> bestOrder) {
        int n = all.size();
        long max = 0;
        for (int mask = 0; mask < (1 << n); mask++) {
            List<Coupon> picked = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                if ((mask & (1 << i)) != 0) {
                    picked.add(all.get(i));
                }
            }
            if (!exclusiveOk(picked)) {
                continue;
            }
            for (List<Coupon> perm : permutations(picked)) {
                long saved = apply(perm, subtotal);
                if (saved > max) {
                    max = saved;
                    bestOrder.clear();
                    bestOrder.addAll(perm);
                }
            }
        }
        return max;
    }

    static List<List<Coupon>> permutations(List<Coupon> list) {
        List<List<Coupon>> out = new ArrayList<>();
        if (list.isEmpty()) {
            out.add(new ArrayList<>());
            return out;
        }
        for (int i = 0; i < list.size(); i++) {
            Coupon head = list.get(i);
            List<Coupon> rest = new ArrayList<>(list);
            rest.remove(i);
            for (List<Coupon> p : permutations(rest)) {
                List<Coupon> one = new ArrayList<>();
                one.add(head);
                one.addAll(p);
                out.add(one);
            }
        }
        return out;
    }

    static String ids(List<Coupon> list) {
        StringBuilder sb = new StringBuilder();
        for (Coupon c : list) {
            if (sb.length() > 0) {
                sb.append(" -> ");
            }
            sb.append(c.id());
        }
        return sb.length() == 0 ? "（不用券）" : sb.toString();
    }

    public static void main(String[] args) {
        List<Coupon> all = new ArrayList<>();
        all.add(new Coupon("C1 满200减40", "A", 20000, 4000, 0, 0));
        all.add(new Coupon("C2 满300减70", "A", 30000, 7000, 0, 0));
        all.add(new Coupon("C3 无门槛减15", null, 0, 1500, 0, 0));
        all.add(new Coupon("C4 8.8折上限60", null, 0, 0, 880, 6000));

        long subtotal = 30000;
        System.out.printf("【1. 购物车原价 %s 元，4 张券（C1/C2 同组互斥）】%n", yuan(subtotal));
        for (Coupon c : all) {
            System.out.printf("  %-14s 组 %-3s 门槛 %8s  面额 %7s%n",
                    c.id(), c.group() == null ? "-" : c.group(),
                    yuan(c.thresholdFen()), yuan(c.face(subtotal)));
        }

        List<Coupon> bestOrder = new ArrayList<>();
        long bestSaved = best(all, subtotal, bestOrder);
        System.out.printf("%n【2. 穷举最优】%s%n", ids(bestOrder));
        System.out.printf("  优惠 %s 元，应付 %s 元%n", yuan(bestSaved), yuan(subtotal - bestSaved));

        System.out.println();
        System.out.println("【3. 贪心按面额从大到小 + 固定顺序，会算贵】");
        List<Coupon> sorted = new ArrayList<>(all);
        sorted.sort((x, y) -> Long.compare(y.face(subtotal), x.face(subtotal)));
        List<Coupon> greedy = new ArrayList<>();
        List<String> usedGroups = new ArrayList<>();
        for (Coupon c : sorted) {
            if (c.group() != null && usedGroups.contains(c.group())) {
                continue;
            }
            greedy.add(c);
            if (c.group() != null) {
                usedGroups.add(c.group());
            }
        }
        long greedySaved = apply(greedy, subtotal);
        System.out.printf("  贪心选择 %s%n", ids(greedy));
        System.out.printf("  优惠 %s 元，应付 %s 元%n", yuan(greedySaved), yuan(subtotal - greedySaved));
        System.out.printf("  与最优差 %s 元：面额最大的券先做减法，把折扣的基数压小了%n",
                yuan(bestSaved - greedySaved));

        System.out.println();
        System.out.println("【4. 同一组券的两种选择，差多少】");
        long onlyC1 = apply(List.of(all.get(0)), subtotal);
        long onlyC2 = apply(List.of(all.get(1)), subtotal);
        System.out.printf("  只选 C1：减 %s    只选 C2：减 %s    差 %s%n",
                yuan(onlyC1), yuan(onlyC2), yuan(onlyC2 - onlyC1));

        System.out.println();
        System.out.println("【5. 顺序对折扣券的影响：同一组券换个顺序】");
        List<Coupon> dFirst = new ArrayList<>(List.of(all.get(3), all.get(1)));
        List<Coupon> rFirst = new ArrayList<>(List.of(all.get(1), all.get(3)));
        System.out.printf("  折扣先：减 %s    满减先：减 %s    差 %s%n",
                yuan(apply(dFirst, subtotal)), yuan(apply(rFirst, subtotal)),
                yuan(Math.abs(apply(dFirst, subtotal) - apply(rFirst, subtotal))));
        System.out.println("  门槛按原价判，所以两种顺序都有效；差的是折扣作用在哪个基数上。");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 再加一张「满 500 减 120」进 A 组，验证 300 元订单不会选它（门槛不满足），并说明穷举为什么仍能得出正确答案。
 * 2. 把券数加到 8 张，估算 2^8 × 8! 的枚举量，说明为什么超过 8 张要换算法。
 * 3. 思考题：如果运营要求「优先用快过期的券」，这算不算一个新的优化目标？它和「优惠最大」冲突时怎么排序？
 * ============================================================
 */

