/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：83-01
 * 课名：营销系统全景与价格链路
 * ------------------------------------------------------------
 * 知识点：
 *   - **一笔订单的价格不是「原价减优惠」，而是一条链路**：**原价小计 → 单品折扣 → 满减 → 券 → 积分抵扣 → 运费 → 应付**，**每一步都要落库留痕**，否则财务对不上账、客诉说不清。
 *   - **只有「乘法型」和「减法型」的相对顺序会改变结果**：折扣是乘法、满减/券是减法，**先乘后减比先减后乘更便宜**（乘法作用在更大的基数上）；**两个减法之间可交换**，谁先谁后都一样。
 *   - **门槛必须钉死在「原价小计」上**：如果用「减完之后的金额」判门槛，**一笔折扣就会把订单打下门槛，后面的满减直接失效**，这是最容易被漏掉的资损点。
 *   - **取整位置决定谁吃亏**：**按行取整**每行都可能多进 1 分，行数越多偏差越大；**整单取整**只有一次误差。**规则要写死并公示**，不能让不同服务各自四舍五入。
 *   - **运费与积分是链路的最后两环**：**包邮门槛通常按「优惠后金额」判**，而**积分抵扣一般有上限（如应付的 50%）**，两处都能被刷。
 *   - 易错点：把价格计算散落在订单、结算、支付三个服务里各算一遍；折扣用 double 累加出现 0.01 误差；优惠后金额为负却直接落库。
 * ============================================================
 */

package com.heima.ch83;

// ==================== 示例代码 ====================

import java.util.ArrayList;
import java.util.List;

public class PriceChain {
    record Item(String sku, long unitFen, int qty) {
        long subtotal() {
            return unitFen * qty;
        }
    }

    // 链路的每一环都留痕：财务对账要能从应付一路回放到原价
    record Stage(String name, long deltaFen, long amountFen) {
    }

    static String yuan(long fen) {
        return String.format("%.2f", fen / 100.0);
    }

    // 折扣按「分」四舍五入：permille=900 表示 9 折
    static long discount(long fen, int permille) {
        return Math.round(fen * permille / 1000.0);
    }

    static void print(String title, List<Stage> stages) {
        System.out.println(title);
        for (Stage s : stages) {
            String d = s.deltaFen() == 0 ? "  " : (s.deltaFen() > 0 ? "+" : "-");
            System.out.printf("  %-22s %s%8s  -> %10s%n", s.name(), d,
                    yuan(Math.abs(s.deltaFen())), yuan(s.amountFen()));
        }
    }

    static long subtotal(List<Item> items) {
        long s = 0;
        for (Item it : items) {
            s += it.subtotal();
        }
        return s;
    }

    // 顺序一：先折扣，后满减，再券，最后积分
    static List<Stage> discountFirst(List<Item> items) {
        List<Stage> st = new ArrayList<>();
        long base = subtotal(items);
        st.add(new Stage("原价小计", 0, base));

        long afterDiscount = discount(base, 900);
        st.add(new Stage("9 折", afterDiscount - base, afterDiscount));

        long afterReduce = afterDiscount >= 40000 ? afterDiscount - 5000 : afterDiscount;
        st.add(new Stage("满 400 减 50", afterReduce - afterDiscount, afterReduce));

        long afterCoupon = afterReduce >= 30000 ? afterReduce - 3000 : afterReduce;
        st.add(new Stage("券 满 300 减 30", afterCoupon - afterReduce, afterCoupon));

        long afterPoints = Math.max(0, afterCoupon - 1000);
        st.add(new Stage("积分抵扣 1000 分", afterPoints - afterCoupon, afterPoints));

        long shipping = afterPoints >= 9900 ? 0 : 800;
        st.add(new Stage("运费（满 99 包邮）", shipping, afterPoints + shipping));
        return st;
    }

    // 顺序二：先满减，后折扣，再券，最后积分
    static List<Stage> reduceFirst(List<Item> items) {
        List<Stage> st = new ArrayList<>();
        long base = subtotal(items);
        st.add(new Stage("原价小计", 0, base));

        long afterReduce = base >= 40000 ? base - 5000 : base;
        st.add(new Stage("满 400 减 50", afterReduce - base, afterReduce));

        long afterDiscount = discount(afterReduce, 900);
        st.add(new Stage("9 折", afterDiscount - afterReduce, afterDiscount));

        long afterCoupon = afterDiscount >= 30000 ? afterDiscount - 3000 : afterDiscount;
        st.add(new Stage("券 满 300 减 30", afterCoupon - afterDiscount, afterCoupon));

        long afterPoints = Math.max(0, afterCoupon - 1000);
        st.add(new Stage("积分抵扣 1000 分", afterPoints - afterCoupon, afterPoints));

        long shipping = afterPoints >= 9900 ? 0 : 800;
        st.add(new Stage("运费（满 99 包邮）", shipping, afterPoints + shipping));
        return st;
    }

    static long payable(List<Stage> st) {
        return st.get(st.size() - 1).amountFen();
    }

    public static void main(String[] args) {
        List<Item> items = new ArrayList<>();
        items.add(new Item("SKU-A", 19900, 2));
        items.add(new Item("SKU-B", 8990, 1));

        System.out.println("【1. 一笔订单的完整价格链路】");
        for (Item it : items) {
            System.out.printf("  行 %-6s %8s x %d = %8s%n",
                    it.sku(), yuan(it.unitFen()), it.qty(), yuan(it.subtotal()));
        }
        print("  顺序A：折扣 -> 满减 -> 券 -> 积分", discountFirst(items));
        print("  顺序B：满减 -> 折扣 -> 券 -> 积分", reduceFirst(items));

        long a = payable(discountFirst(items));
        long b = payable(reduceFirst(items));
        System.out.printf("%n【2. 顺序敏感性】顺序A 应付 %s，顺序B 应付 %s，差 %s 元%n",
                yuan(a), yuan(b), yuan(Math.abs(a - b)));
        System.out.println("  原因：折扣是乘法、满减是减法，先乘后减让乘法作用在更大的基数上，所以更便宜。");
        System.out.println("  注意两个减法之间可交换：满减与券互换顺序，应付完全不变。");

        System.out.println();
        System.out.println("【3. 两个减法型优惠互换顺序】");
        long x = 43911 - 5000 - 3000;
        long y = 43911 - 3000 - 5000;
        System.out.printf("  满减先：%s    券先：%s    差 %d 分%n", yuan(x), yuan(y), Math.abs(x - y));

        System.out.println();
        System.out.println("【4. 按行取整 vs 整单取整（8 折，每行 10.01 元）】");
        long unit = 1001;
        for (int rows : new int[]{3, 10, 30}) {
            long perRow = 0;
            for (int i = 0; i < rows; i++) {
                perRow += discount(unit, 800);
            }
            long whole = discount(unit * rows, 800);
            System.out.printf("  %2d 行：按行合计 %8s  整单 %8s  差 %3d 分%n",
                    rows, yuan(perRow), yuan(whole), perRow - whole);
        }
        System.out.println("  行数越多，按行取整多进的分越多；所以分摊必须用「总额守恒」的算法，而不是各行独立取整。");

        System.out.println();
        System.out.println("【5. 门槛必须钉在原价上】");
        long base = 30000;
        long afterDiscount = discount(base, 880);
        System.out.printf("  原价 %s，先打 88 折 -> %s%n", yuan(base), yuan(afterDiscount));
        System.out.println("  若用「折扣后金额」判「满 300 减 70」：" + (afterDiscount >= 30000 ? "仍命中" : "不命中，满减直接失效"));
        System.out.printf("  用「原价」判：命中，减 70 后应付 %s%n", yuan(afterDiscount - 7000));
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 把满减门槛从 400 改成 450，重算两个顺序的应付，验证差额是否仍然存在。
 * 2. 给链路加一环「会员价」：会员价按单品 95 折且不参与满减门槛计算，说明它会放在哪一步、为什么。
 * 3. 思考题：如果价格计算在订单服务和结算服务各算一次，两次结果差 1 分，用户看到哪个？怎么保证只算一次？
 * ============================================================
 */

