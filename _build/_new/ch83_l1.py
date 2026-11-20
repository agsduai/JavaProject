# -*- coding: utf-8 -*-
LESSONS = [
    dict(
        cls="PriceChain",
        title="营销系统全景与价格链路",
        points=[
            "**一笔订单的价格不是「原价减优惠」，而是一条链路**：**原价小计 → 单品折扣 → 满减 → 券 → 积分抵扣 → 运费 → 应付**，**每一步都要落库留痕**，否则财务对不上账、客诉说不清。",
            "**只有「乘法型」和「减法型」的相对顺序会改变结果**：折扣是乘法、满减/券是减法，**先乘后减比先减后乘更便宜**（乘法作用在更大的基数上）；**两个减法之间可交换**，谁先谁后都一样。",
            "**门槛必须钉死在「原价小计」上**：如果用「减完之后的金额」判门槛，**一笔折扣就会把订单打下门槛，后面的满减直接失效**，这是最容易被漏掉的资损点。",
            "**取整位置决定谁吃亏**：**按行取整**每行都可能多进 1 分，行数越多偏差越大；**整单取整**只有一次误差。**规则要写死并公示**，不能让不同服务各自四舍五入。",
            "**运费与积分是链路的最后两环**：**包邮门槛通常按「优惠后金额」判**，而**积分抵扣一般有上限（如应付的 50%）**，两处都能被刷。",
            "易错点：把价格计算散落在订单、结算、支付三个服务里各算一遍；折扣用 double 累加出现 0.01 误差；优惠后金额为负却直接落库。",
        ],
        code=r'''import java.util.ArrayList;
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
''',
        exercises=[
            "把满减门槛从 400 改成 450，重算两个顺序的应付，验证差额是否仍然存在。",
            "给链路加一环「会员价」：会员价按单品 95 折且不参与满减门槛计算，说明它会放在哪一步、为什么。",
            "思考题：如果价格计算在订单服务和结算服务各算一次，两次结果差 1 分，用户看到哪个？怎么保证只算一次？",
        ],
    ),
    dict(
        cls="DiscountRounding",
        title="四类优惠的算法与取整",
        points=[
            "**满减**：**达到门槛就减固定金额，多档取最高可命中档**——**门槛按原价小计判，不按件数**，**多档之间是「取最优」不是「累加」**。",
            "**阶梯优惠**：**按件数或金额分档**（满 2 件 9 折、满 3 件 85 折）——**档位要连续可算**，**不能出现「买 4 件比买 3 件还贵」的断档**。",
            "**折扣（带上限）**：**按比例减，但必须封顶**——**不封顶的折扣在大额订单上会直接击穿毛利**，**封顶值要与预算联动**。",
            "**立减**：**每件减固定额或整单减固定额**——**最容易做，也最容易被刷**（凑单后整单退），**必须配合限购与退款回收**。",
            "**三种取整方式的差异只在小数位上，但方向不同**：**四舍五入**对用户友好、**向下取整**对商家有利、**银行家舍入（四舍六入五成双）**在大量结算时偏差最小——**同一系统内只能选一种**。",
            "易错点：用 `double` 存金额导致 0.1+0.2 类误差；折扣上限按「优惠额」封顶却忘了同步改分摊；阶梯档位写成 `if` 链漏了等于号，恰好 3 件时不打折。",
        ],
        code=r'''import java.math.BigDecimal;
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
''',
        exercises=[
            "给满减加一档「满 1000 减 250」，验证 1200 元订单只减 250 而不是叠加成 400。",
            "把阶梯档位改成「满 3 件 85 折、满 4 件 8 折」，验证折合单价仍然单调下降。",
            "思考题：折扣上限如果按「优惠额」封顶，但分摊时按「比例」分摊，会出什么不一致？举一个具体数字。",
        ],
    ),
    dict(
        cls="CouponCombination",
        title="叠加互斥与最优组合",
        points=[
            "**券不是「越多越便宜」，而是「有约束的组合优化」**：**互斥组（同组只能选一张）+ 门槛（原价判定）+ 叠加顺序**，三者一起决定最终优惠。",
            "**互斥要按「组」而不是按「张」**：**全场券与品类券常常同组互斥**，**但运费券、积分、会员折扣通常是独立维度**，混在一起配就会出现「两张券同时生效」。",
            "**贪心按面额选券会算贵**：**面额大的券可能门槛高、可能挤掉了能叠加的折扣**，**正确做法是小规模穷举（券数 ≤ 5 时 2^n × n! 完全可算）**。",
            "**折扣券与满减券的顺序必须固定**：**折扣先做**，满减作用在更小的金额上、优惠更少；**满减先做**，折扣作用在更小的金额上、优惠也更少——**两种都亏，所以要把「门槛按原价、顺序按规则」写死，再穷举选最优**。",
            "**最优解要能解释给运营**：**输出「选了哪几张、按什么顺序、省了多少」**，而不是只给一个数字；**运营要能看懂为什么没选那张面额更大的券**。",
            "易错点：只判门槛不判互斥（两张同组券同时生效）；用贪心代替穷举且没验证差额；券数超过 8 还硬穷举（组合爆炸，要换整数规划或剪枝）。",
        ],
        code=r'''import java.util.ArrayList;
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
''',
        exercises=[
            "再加一张「满 500 减 120」进 A 组，验证 300 元订单不会选它（门槛不满足），并说明穷举为什么仍能得出正确答案。",
            "把券数加到 8 张，估算 2^8 × 8! 的枚举量，说明为什么超过 8 张要换算法。",
            "思考题：如果运营要求「优先用快过期的券」，这算不算一个新的优化目标？它和「优惠最大」冲突时怎么排序？",
        ],
    ),
]
