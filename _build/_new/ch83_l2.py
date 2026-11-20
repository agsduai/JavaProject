# -*- coding: utf-8 -*-
LESSONS = [
    dict(
        cls="PriceAllocation",
        title="价格分摊与退款分摊",
        points=[
            "**分摊是「把整单优惠拆到行」的过程，目的是让退款能按行算**：**用户退一件时，退的钱必须等于「这一行的实付」**，而不是「原价按比例估算」。",
            "**各行独立四舍五入必然不守恒**：**每行都进 1 分，行数一多，分摊之和就大于优惠总额**，财务立刻对不上——**必须用「最大余额法」：先按比例取整，再把余下的分按小数部分从大到小补齐**。",
            "**最大余额法的三个性质**：**总额严格守恒**、**每行误差 < 1 分**、**分摊额不超过该行金额**（否则会出现负数行）。",
            "**退款分摊要用「原分摊额」而不是「重新按比例算」**：**订单退掉一行后，剩余行的分摊额不能变**，否则「先退 A 再退 B」和「一起退」退的钱不一样，用户会来吵架。",
            "**运费也要分摊**：**包邮订单退到不满足包邮门槛时，要扣回运费**——**运费分摊按行金额比例，扣回时按同样的比例扣**，两边口径必须一致。",
            "易错点：分摊用 `double` 累加导致总额差几分；退部分行时重新分摊；优惠金额大于某行金额导致该行为负；运费分摊与优惠分摊用了不同算法。",
        ],
        code=r'''import java.util.ArrayList;
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
''',
        exercises=[
            "把订单改成 7 行、优惠改成 9999 分，验证最大余额法仍然严格守恒且每行误差 < 1 分。",
            "给分摊加「运费分摊」：运费 800 分按行金额比例分摊，验证退到不满足包邮时能按同样比例扣回。",
            "思考题：如果某行金额很小（1 分），却被补余补了 1 分，该行实付变成 0，这算不算 bug？怎么处理更合理？",
        ],
    ),
    dict(
        cls="CouponStateMachine",
        title="券状态机与并发核销",
        points=[
            "**券的生命周期是一个状态机**：**已发放（ISSUED）→ 已锁定（LOCKED）→ 已使用（USED）**，**旁支是已过期（EXPIRED）与已作废（VOID）**，**只有明确的状态图才能防住「一券多用」**。",
            "**「锁定」这一态是必须的**：**下单时锁券、支付成功才置为已使用、超时未付要回滚成已发放**——**没有锁定态就只能「先扣后用」，用户下单不付款会把券白占住**。",
            "**非法迁移要显式拒绝**：**已使用不能回锁定、已过期不能锁定、已作废不能复活**——**用 `Set<迁移>` 白名单校验，比一堆 `if` 更不容易漏**。",
            "**并发核销必须用 CAS**：**「读状态 → 判断 → 写状态」三步不是原子的**，**两个请求同时读到 ISSUED 就会双双成功**，**必须 `compareAndSet(ISSUED, LOCKED)`**。",
            "**幂等键要跟券绑定**：**同一个券的重复请求要返回「第一次的结果」而不是报错**，否则用户重试会看到「券已被使用」而恐慌。",
            "易错点：用 `synchronized` 包住整个核销（吞吐掉到个位数）；锁券后没设超时回滚；把「已过期」当异常抛给用户（应该是正常业务态）；状态字段用 String 随便写。",
        ],
        code=r'''import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class CouponStateMachine {
    enum State {ISSUED, LOCKED, USED, EXPIRED, VOID}

    // 白名单式迁移表：只允许这里列出的边，其他一律拒绝
    static final Map<State, Set<State>> ALLOWED = Map.of(
            State.ISSUED, EnumSet.of(State.LOCKED, State.EXPIRED, State.VOID),
            State.LOCKED, EnumSet.of(State.USED, State.ISSUED, State.EXPIRED),
            State.USED, EnumSet.noneOf(State.class),
            State.EXPIRED, EnumSet.noneOf(State.class),
            State.VOID, EnumSet.noneOf(State.class));

    static class Coupon {
        final String id;
        final AtomicReference<State> state;

        Coupon(String id) {
            this(id, 1);
        }

        Coupon(String id, int readers) {
            this.id = id;
            this.state = new AtomicReference<>(State.ISSUED);
            this.readBarrier = new CountDownLatch(readers);
        }

        // 非原子写法，只用于演示「为什么不能这么写」
        // 用栅栏把「读」和「写」拉开：让所有线程都读到 ISSUED 之后再一起写
        final CountDownLatch readBarrier;

        boolean redeemNaive() {
            State s = state.get();
            if (s != State.ISSUED) {
                return false;
            }
            readBarrier.countDown();
            try {
                readBarrier.await();     // 等所有线程都读完，再一起写
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            state.set(State.LOCKED);
            return true;
        }

        // CAS 写法：比较并交换，只有一个线程能成功
        boolean redeem() {
            return state.compareAndSet(State.ISSUED, State.LOCKED);
        }

        boolean transit(State to) {
            while (true) {
                State from = state.get();
                if (!ALLOWED.get(from).contains(to)) {
                    return false;
                }
                if (state.compareAndSet(from, to)) {
                    return true;
                }
            }
        }

        State get() {
            return state.get();
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("【1. 合法迁移矩阵】");
        for (State from : State.values()) {
            List<String> to = new ArrayList<>();
            for (State t : ALLOWED.get(from)) {
                to.add(t.name());
            }
            System.out.printf("  %-8s -> %s%n", from, to.isEmpty() ? "（终态，不可再变）" : String.join(", ", to));
        }

        System.out.println();
        System.out.println("【2. 非法迁移被拒绝】");
        Coupon c1 = new Coupon("C1");
        c1.transit(State.LOCKED);
        c1.transit(State.USED);
        System.out.printf("  当前状态 %s%n", c1.get());
        System.out.printf("  已使用 -> 回锁定：%s%n", c1.transit(State.LOCKED) ? "允许（错！）" : "拒绝");
        System.out.printf("  已使用 -> 作废：%s%n", c1.transit(State.VOID) ? "允许（错！）" : "拒绝");

        Coupon c2 = new Coupon("C2");
        c2.transit(State.EXPIRED);
        System.out.printf("  已过期 -> 锁定：%s%n", c2.transit(State.LOCKED) ? "允许（错！）" : "拒绝");

        System.out.println();
        System.out.println("【3. 正常链路：锁券 -> 支付成功置已使用；超时回滚】");
        Coupon c3 = new Coupon("C3");
        System.out.printf("  下单锁券：%s，状态 %s%n", c3.redeem() ? "成功" : "失败", c3.get());
        System.out.printf("  超时未付回滚：%s，状态 %s%n", c3.transit(State.ISSUED) ? "成功" : "失败", c3.get());
        System.out.printf("  再次下单锁券：%s，状态 %s%n", c3.redeem() ? "成功" : "失败", c3.get());
        System.out.printf("  支付成功：%s，状态 %s%n", c3.transit(State.USED) ? "成功" : "失败", c3.get());

        System.out.println();
        System.out.println("【4. 并发核销：20 个线程抢同一张券】");
        int threads = 20;
        Coupon race = new Coupon("RACE", threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger naiveOk = new AtomicInteger();
        for (int i = 0; i < threads; i++) {
            new Thread(() -> {
                try {
                    start.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                if (race.redeemNaive()) {
                    naiveOk.incrementAndGet();
                }
                done.countDown();
            }).start();
        }
        start.countDown();
        done.await();
        System.out.printf("  非原子写法（读-判-写）：成功 %d 次，状态 %s%n", naiveOk.get(), race.get());
        System.out.printf("  20 个线程全部读到 ISSUED、全部判定「可用」，于是 %d 个都返回成功%n", naiveOk.get());
        System.out.printf("  但状态字段只留下最后一次写入，%d 次核销里有 %d 次「成功了却没留痕」——双花的经典形态%n",
                naiveOk.get(), naiveOk.get() - 1);
        System.out.println("  真实环境里这个窗口只有几微秒，靠运气偶尔触发；一旦触发就是资损，所以必须从设计上堵死。");

        Coupon safe = new Coupon("SAFE");
        CountDownLatch start2 = new CountDownLatch(1);
        CountDownLatch done2 = new CountDownLatch(threads);
        AtomicInteger casOk = new AtomicInteger();
        for (int i = 0; i < threads; i++) {
            new Thread(() -> {
                try {
                    start2.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                if (safe.redeem()) {
                    casOk.incrementAndGet();
                }
                done2.countDown();
            }).start();
        }
        start2.countDown();
        done2.await();
        System.out.printf("  CAS 写法：成功 %d 次，状态 %s%n", casOk.get(), safe.get());
        System.out.println("  成功次数恒为 1，这就是 compareAndSet 的作用。");

        System.out.println();
        System.out.println("【5. 幂等：同一请求重试不重复核销】");
        Map<String, String> idem = new ConcurrentHashMap<>();
        Coupon c5 = new Coupon("C5");
        System.out.printf("  首次核销：%s%n", redeemOnce(c5, idem, "req-001"));
        System.out.printf("  重试同一请求：%s%n", redeemOnce(c5, idem, "req-001"));
        System.out.printf("  换一个请求再核销：%s%n", redeemOnce(c5, idem, "req-002"));
        System.out.println("  重试返回第一次的结果，用户看到的不是「券已被使用」而是「下单成功」。");
    }

    static String redeemOnce(Coupon c, Map<String, String> idem, String reqId) {
        String hit = idem.get(reqId);
        if (hit != null) {
            return "命中幂等缓存 -> " + hit;
        }
        String result = c.redeem() ? "锁定成功" : "券不可用（当前 " + c.get() + "）";
        idem.put(reqId, result);
        return result;
    }
}
''',
        exercises=[
            "给状态机加一条「已锁定超时 15 分钟自动回滚」的定时任务，验证回滚后券可以再次被锁定。",
            "把 ALLOWED 改成用 `Map<State, Set<State>>` 从配置加载，说明这样做的好处与风险。",
            "思考题：如果核销要跨两个服务（券服务 + 订单服务），CAS 就不够了，应该用什么方案保证不重复？",
        ],
    ),
    dict(
        cls="StockDeduction",
        title="库存扣减与防超卖",
        points=[
            "**超卖的根因不是「扣减慢」，而是「读-判-写」三步不是原子的**：**两个请求都读到库存 1，都判断「够」，都写 0，结果卖出 2 件**。",
            "**扣减必须在存储层做原子操作**：**`update stock set qty = qty - 1 where sku = ? and qty >= 1`**，**靠影响行数判断是否成功**，**这是最可靠的一道闸**。",
            "**内存里用 CAS 是同一思路**：**`compareAndSet(old, old - n)`，失败就重试**——**注意重试要有次数上限，否则高并发下会活锁**。",
            "**分段库存（库存分桶）能降低热点**：**把 100 件拆成 4 段各 25 件，请求按 hash 落到某段**——**总成功数不变，但落在同一段上的并发请求从 200 降到约 50，冲突面按段数线性下降**。",
            "**预扣与实扣要分开**：**下单预扣、支付实扣、超时回补**——**预扣不实扣，库存会被未付款订单长期占用**，**要有超时回补任务**。",
            "易错点：先查库存再扣（中间有时间窗）；扣减失败却已经把订单落库；回补时忘了幂等导致库存越补越多；分段库存的段容量写死导致某段先空、整体还有货。",
        ],
        code=r'''import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class StockDeduction {
    // 朴素库存：读-判-写，三步之间没有原子性
    // 用一道栅栏把「读」和「写」拆开：所有线程先读完，再一起写，
    // 把真实环境里几十微秒的并发窗口放大成必然发生
    static class NaiveStock {
        long qty;
        final int expectReaders;
        final AtomicInteger readers = new AtomicInteger();

        NaiveStock(long qty, int expectReaders) {
            this.qty = qty;
            this.expectReaders = expectReaders;
        }

        boolean deduct() {
            long cur = qty;                      // 第 1 步：读
            if (cur <= 0) {
                return false;
            }
            readers.incrementAndGet();
            while (readers.get() < expectReaders) {   // 等所有线程都读完
                Thread.onSpinWait();
            }
            qty = cur - 1;                       // 第 3 步：写（覆盖别人的写）
            return true;
        }
    }

    // CAS 库存：比较并交换，失败重试
    static class CasStock {
        final AtomicLong qty;
        final AtomicInteger retries = new AtomicInteger();

        CasStock(long qty) {
            this.qty = new AtomicLong(qty);
        }

        boolean deduct() {
            while (true) {
                long cur = qty.get();
                if (cur <= 0) {
                    return false;
                }
                if (qty.compareAndSet(cur, cur - 1)) {
                    return true;
                }
                retries.incrementAndGet();
            }
        }
    }

    // 分段库存：按 hash 落到某一段，段内再 CAS
    static class SegmentedStock {
        final CasStock[] segs;

        SegmentedStock(long total, int segCount) {
            segs = new CasStock[segCount];
            long per = total / segCount;
            long extra = total % segCount;
            for (int i = 0; i < segCount; i++) {
                segs[i] = new CasStock(per + (i < extra ? 1 : 0));
            }
        }

        boolean deduct(String key) {
            int idx = Math.floorMod(key.hashCode(), segs.length);
            // 本段空了就试下一段，避免「某段空、整体还有货」
            for (int i = 0; i < segs.length; i++) {
                if (segs[(idx + i) % segs.length].deduct()) {
                    return true;
                }
            }
            return false;
        }

        long left() {
            long s = 0;
            for (CasStock c : segs) {
                s += c.qty.get();
            }
            return s;
        }
    }

    static int runConcurrent(int threads, java.util.function.IntConsumer task) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger ok = new AtomicInteger();
        for (int i = 0; i < threads; i++) {
            final int id = i;
            new Thread(() -> {
                try {
                    start.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                try {
                    task.accept(id);
                } finally {
                    done.countDown();
                }
            }).start();
        }
        start.countDown();
        done.await();
        return ok.get();
    }

    public static void main(String[] args) throws Exception {
        int stock = 100;
        int buyers = 200;

        System.out.println("【1. 朴素写法：库存 100，200 人各买 1 件】");
        NaiveStock naive = new NaiveStock(stock, buyers);
        AtomicInteger naiveOk = new AtomicInteger();
        runConcurrent(buyers, id -> {
            if (naive.deduct()) {
                naiveOk.incrementAndGet();
            }
        });
        System.out.println("  用栅栏强制「200 个线程先全部读完，再一起写」，把微秒级窗口放大成必然");
        System.out.printf("  卖出 %d 件，库存变量停在 %d，超卖 %d 件%n",
                naiveOk.get(), naive.qty, naiveOk.get() - stock);
        System.out.printf("  %d 次写入互相覆盖，只剩最后一次生效：这就是「读-判-写」不是原子的代价%n",
                naiveOk.get() - (stock - naive.qty));
        System.out.println("  注意超卖不是「扣慢了」，而是「判断和扣减之间有缝」——把窗口放大到必然发生，才看得见。");

        System.out.println();
        System.out.println("【2. CAS 写法：同样的 200 人】");
        CasStock cas = new CasStock(stock);
        AtomicInteger casOk = new AtomicInteger();
        runConcurrent(buyers, id -> {
            if (cas.deduct()) {
                casOk.incrementAndGet();
            }
        });
        System.out.printf("  卖出 %d 件，剩余库存 %d，超卖 %d 件%n",
                casOk.get(), cas.qty.get(), Math.max(0, casOk.get() - stock));
        System.out.printf("  CAS 失败重试 %d 次（说明确实在竞争，但结果是对的）%n", cas.retries.get());
        System.out.println("  成功次数恒等于库存数，这是防超卖的核心：让「判断 + 扣减」变成一个原子操作。");

        System.out.println();
        System.out.println("【3. 分段库存：100 件拆 4 段】");
        SegmentedStock seg = new SegmentedStock(stock, 4);
        for (int i = 0; i < 4; i++) {
            System.out.printf("  段 %d 初始 %d 件%n", i, seg.segs[i].qty.get());
        }
        // 先算清「竞争面」：200 个 key 按同一套 hash 落到 4 段，各段分到多少
        int[] bucket = new int[4];
        for (int i = 0; i < buyers; i++) {
            bucket[Math.floorMod(("user-" + i).hashCode(), 4)]++;
        }
        System.out.printf("  200 个请求按 hash 落到 4 段：%d / %d / %d / %d%n",
                bucket[0], bucket[1], bucket[2], bucket[3]);
        long singlePairs = (long) buyers * (buyers - 1) / 2;
        long segPairs = 0;
        for (int n : bucket) {
            segPairs += (long) n * (n - 1) / 2;
        }
        System.out.printf("  同一把锁上的潜在冲突对数：单段 %d 对 -> 四段合计 %d 对（约 1/%d）%n",
                singlePairs, segPairs, singlePairs / Math.max(1, segPairs));

        AtomicInteger segOk = new AtomicInteger();
        runConcurrent(buyers, id -> {
            if (seg.deduct("user-" + id)) {
                segOk.incrementAndGet();
            }
        });
        System.out.printf("  卖出 %d 件，剩余 %d 件，超卖 %d 件（总成功数不受分段影响）%n",
                segOk.get(), seg.left(), Math.max(0, segOk.get() - stock));
        int totalRetry = 0;
        for (CasStock c : seg.segs) {
            totalRetry += c.retries.get();
        }
        System.out.printf("  四段实际 CAS 失败合计 %d 次（本次运行观测值，随线程调度浮动）%n", totalRetry);
        System.out.println("  真正的收益是结构性的：每段只有约 1/4 的请求在抢，冲突面按段数线性摊薄；");
        System.out.println("  重试次数是观测值不能当结论，冲突对数才是可复现的口径。");

        System.out.println();
        System.out.println("【4. 预扣 / 实扣 / 回补 的三段式】");
        long stockQty = 10;
        long reserved = 3;
        System.out.printf("  初始库存 %d，下单预扣 %d -> 可售 %d%n", stockQty, reserved, stockQty - reserved);
        System.out.printf("  支付成功实扣 %d -> 可售 %d（预扣转为实扣，总量不变）%n", reserved, stockQty - reserved);
        System.out.printf("  另一单超时回补 %d -> 可售 %d%n", 2, stockQty - reserved + 2);
        System.out.println("  预扣不实扣、超时不回补，库存就会被未付款订单长期占死。");

        System.out.println();
        System.out.println("【5. 回补必须幂等，否则越补越多】");
        System.out.println("  先看不做幂等：同一订单 order-1 的回补请求重投 3 次");
        long buggy = 5;
        for (int i = 1; i <= 3; i++) {
            buggy = buggy + 1;
            System.out.printf("    第 %d 次回补 -> 库存 %d%n", i, buggy);
        }
        System.out.printf("  库存从 5 涨到 %d，凭空多出 %d 件——这 2 件就是「无中生有」的货%n", buggy, buggy - 6);

        System.out.println();
        System.out.println("  再看带幂等键：同一 order-1 只认第一次");
        List<String> rolledBack = new ArrayList<>();
        long q = 5;
        for (int i = 1; i <= 3; i++) {
            long before = q;
            q = rollback(q, rolledBack, "order-1");
            System.out.printf("    第 %d 次回补 -> 库存 %d%s%n", i, q, q == before ? "（重复请求，跳过）" : "");
        }
        System.out.printf("  库存停在 %d，只加了一次%n", q);
    }

    static long rollback(long qty, List<String> done, String orderId) {
        if (done.contains(orderId)) {
            return qty;
        }
        done.add(orderId);
        return qty + 1;
    }
}
''',
        exercises=[
            "把线程数从 200 改成 2000，观察 CAS 重试次数的量级变化，说明为什么高并发下要限流。",
            "把分段数从 4 改成 16，验证总成功数仍为 100，并说明段数上限由什么决定。",
            "思考题：如果库存扣减成功但订单落库失败，怎么保证不丢库存？给出补偿方案与幂等键设计。",
        ],
    ),
    dict(
        cls="PurchaseLimit",
        title="限购与防刷",
        points=[
            "**限购要按「维度」设计，不是只按用户**：**用户维度防正常人多买，设备维度防换号，IP 维度防批量注册，支付账号维度防同一张卡**——**单维度一定会被绕过**。",
            "**「限购 N 件」要区分「每单限购」与「活动期累计限购」**：**每单限购用 `min(购买数, N)` 就能实现，累计限购必须落库计数并原子累加**。",
            "**计数必须原子且带窗口**：**用 `AtomicInteger` 或 Redis `INCR` + 过期时间**，**「先查再写」同样会超**，**和库存是同一类问题**。",
            "**幂等键是防重的最后一道闸**：**同一 `(userId, activityId, requestId)` 只允许成功一次**，**重试返回第一次结果**，**这能挡住「网络超时后客户端重试」造成的重复下单**。",
            "**风控规则要能解释**：**命中「同设备 10 分钟内 20 个账号」要能给出具体证据**，**否则误杀正常用户（如家庭共用设备）时无法申诉**。",
            "易错点：只在网关限购（绕过网关直连服务就失效）；限购计数用内存 Map（多实例各算各的）；把「限购」做成「黑名单」导致正常用户被永久拦；风控阈值写死无法按活动调整。",
        ],
        code=r'''import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class PurchaseLimit {
    record Attempt(String userId, String deviceId, String ip, String requestId, int qty) {
    }

    // 多维度计数器：任一维度超限就拒绝
    static class LimitEngine {
        final int perUser;
        final int perDevice;
        final Map<String, AtomicInteger> userCount = new ConcurrentHashMap<>();
        final Map<String, AtomicInteger> deviceCount = new ConcurrentHashMap<>();
        final Set<String> idemKeys = ConcurrentHashMap.newKeySet();
        final Map<String, String> idemResult = new ConcurrentHashMap<>();

        LimitEngine(int perUser, int perDevice) {
            this.perUser = perUser;
            this.perDevice = perDevice;
        }

        String buy(Attempt a) {
            String key = a.userId() + "|" + a.requestId();
            String hit = idemResult.get(key);
            if (hit != null) {
                return "幂等重放 -> " + hit;
            }
            AtomicInteger u = userCount.computeIfAbsent(a.userId(), k -> new AtomicInteger());
            AtomicInteger d = deviceCount.computeIfAbsent(a.deviceId(), k -> new AtomicInteger());

            // 先占额度再判断，失败要回滚，避免「判断通过但额度被抢走」
            if (u.addAndGet(a.qty()) > perUser) {
                u.addAndGet(-a.qty());
                return "拒绝：用户 " + a.userId() + " 累计超限（上限 " + perUser + "）";
            }
            if (d.addAndGet(a.qty()) > perDevice) {
                d.addAndGet(-a.qty());
                u.addAndGet(-a.qty());
                return "拒绝：设备 " + a.deviceId() + " 累计超限（上限 " + perDevice + "）";
            }
            String result = "成功：买入 " + a.qty() + " 件";
            idemResult.put(key, result);
            idemKeys.add(key);
            return result;
        }
    }

    public static void main(String[] args) {
        LimitEngine eng = new LimitEngine(2, 3);

        System.out.println("【1. 单用户：上限 2 件，分两次买】");
        System.out.printf("  %s%n", eng.buy(new Attempt("u1", "d1", "1.1.1.1", "r1", 1)));
        System.out.printf("  %s%n", eng.buy(new Attempt("u1", "d1", "1.1.1.1", "r2", 1)));
        System.out.printf("  %s%n", eng.buy(new Attempt("u1", "d1", "1.1.1.1", "r3", 1)));
        System.out.println("  第三次被拒，因为用户维度累计已经到 2。");

        System.out.println();
        System.out.println("【2. 换账号但同一设备：设备维度兜住】");
        System.out.println("  u1 已经在设备 d1 上用掉 2 件额度，设备上限是 3");
        System.out.printf("  %s%n", eng.buy(new Attempt("u2", "d1", "1.1.1.1", "r4", 1)));
        System.out.printf("  %s%n", eng.buy(new Attempt("u3", "d1", "1.1.1.1", "r5", 1)));
        System.out.printf("  设备 d1 当前计数 %d（上限 %d）%n",
                eng.deviceCount.get("d1").get(), eng.perDevice);
        System.out.println("  u2 是新账号、自己一件没买，但设备额度只剩 1 件，只买到 1 件；");
        System.out.println("  u3 再来一件时设备额度已满 3，即便账号全新也被拒——单维度限购挡不住换号，设备维度能。");

        System.out.println();
        System.out.println("【3. 幂等：同一请求重试不重复扣额度】");
        System.out.printf("  %s%n", eng.buy(new Attempt("u4", "d2", "2.2.2.2", "r6", 1)));
        System.out.printf("  %s%n", eng.buy(new Attempt("u4", "d2", "2.2.2.2", "r6", 1)));
        System.out.printf("  用户 u4 当前计数 %d（重试没有变成 2）%n",
                eng.userCount.get("u4").get());

        System.out.println();
        System.out.println("【4. 只做单维度限购会漏掉什么】");
        Map<String, Set<String>> deviceUsers = new HashMap<>();
        String[] devices = {"d9", "d9", "d9", "d9", "d9"};
        String[] users = {"a1", "a2", "a3", "a4", "a5"};
        for (int i = 0; i < devices.length; i++) {
            deviceUsers.computeIfAbsent(devices[i], k -> new HashSet<>()).add(users[i]);
        }
        System.out.printf("  设备 d9 在 10 分钟内出现 %d 个不同账号 -> 命中风控规则%n",
                deviceUsers.get("d9").size());
        System.out.println("  如果只按用户限购，每个账号各买 2 件，5 个账号就是 10 件，全从同一台设备刷走。");

        System.out.println();
        System.out.println("【5. 风控结论要带证据，否则误杀无法申诉】");
        List<String> evidence = new ArrayList<>();
        evidence.add("设备 d9 关联账号 5 个");
        evidence.add("同一 IP 段 2.2.2.0/24 关联账号 5 个");
        evidence.add("下单间隔中位数 0.8s（正常用户约 35s）");
        for (String e : evidence) {
            System.out.println("  - " + e);
        }
        System.out.println("  有证据才能人工复核；只给一个「风险分 87」等于没给。");
    }
}
''',
        exercises=[
            "给限购加「活动期累计」与「每单限购」两套阈值，验证 u1 每单最多 2 件但活动期最多 3 件。",
            "把 IP 维度也加进引擎，说明为什么家庭共用 WiFi 会被误杀，以及怎么降低误杀率。",
            "思考题：如果限购计数放 Redis，Redis 主从切换丢了一次 INCR，会超卖多少？怎么补？",
        ],
    ),
]
