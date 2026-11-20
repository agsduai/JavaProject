# -*- coding: utf-8 -*-
LESSONS = [
    dict(
        cls="TimeWindow",
        title="活动时间窗口与时区",
        points=[
            "**活动时间必须用「绝对时间戳 + 明确时区」表达**：**「10 月 1 日 0 点」在代码里等于 `Instant`，不是 `LocalDateTime`**——**`LocalDateTime` 没有时区，落到不同机器上含义就变了**。",
            "**服务器时区与用户时区可能不同**：**服务器跑 UTC、用户在东八区**，**用 `LocalDateTime.now()` 判活动开始，会提前 8 小时开抢或延后 8 小时结束**。",
            "**夏令时会让「每天 0 点」这个表述本身失效**：**有的时区某天没有 0 点（时钟跳 2 点）**，**`atStartOfDay` 要处理「不存在的时间」与「重复的时间」两种情况**。",
            "**活动开始/结束要「按区间判」而不是「按点判」**：**`[start, end)` 左闭右开**，**避免「结束那一刻还能下单、下一毫秒又不行」的边界争议**，**也避免同一秒被两个条件同时命中**。",
            "**客户端时间不可信**：**用户改系统时间就能绕过「限时活动」**，**判定必须在服务端做，客户端只做展示**。",
            "易错点：用字符串比时间（`\"2026-10-01\" < \"2026-10-1\"` 为真）；活动结束时间写成 `23:59:59` 漏掉最后一秒；跨时区活动按本地时间各自开抢导致有人早 8 小时。",
        ],
        code=r'''import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class TimeWindow {
    record Window(String name, Instant start, Instant end) {
        // 左闭右开：start 可以下单，end 那一刻已经结束
        boolean contains(Instant t) {
            return !t.isBefore(start) && t.isBefore(end);
        }

        String show(ZoneId zone) {
            DateTimeFormatter f = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            return start.atZone(zone).format(f) + " ~ " + end.atZone(zone).format(f)
                    + " (" + zone + ")";
        }
    }

    public static void main(String[] args) {
        ZoneId shanghai = ZoneId.of("Asia/Shanghai");
        ZoneId utc = ZoneId.of("UTC");
        ZoneId newYork = ZoneId.of("America/New_York");

        System.out.println("【1. 同一个 Instant，在不同时区看到的「活动时间」】");
        Instant start = ZonedDateTime.of(2026, 10, 1, 0, 0, 0, 0, shanghai).toInstant();
        Instant end = ZonedDateTime.of(2026, 10, 8, 0, 0, 0, 0, shanghai).toInstant();
        Window w = new Window("国庆大促", start, end);
        System.out.println("  " + w.show(shanghai));
        System.out.println("  " + w.show(utc));
        System.out.println("  " + w.show(newYork));
        System.out.println("  时间戳是同一个（" + start.toEpochMilli() + "），只是显示不同。");

        System.out.println();
        System.out.println("【2. 用 LocalDateTime 判活动的坑：服务器跑 UTC】");
        LocalDateTime declared = LocalDateTime.of(2026, 10, 1, 0, 0);
        // 正确写法：明确按活动声明的时区解释
        Instant right = declared.atZone(shanghai).toInstant();
        // 错误写法：把「本地墙上时间」直接当 UTC 解释
        Instant wrong = declared.atZone(utc).toInstant();
        System.out.println("  运营声明：东八区 10-01 00:00 开始");
        System.out.printf("  正确解释（按东八区）：%s%n", right);
        System.out.printf("  错误解释（当成 UTC）  ：%s%n", wrong);
        long lateHours = Duration.between(right, wrong).toHours();
        System.out.printf("  两者相差 %+d 小时：按 UTC 解释会让活动晚开 %d 小时，用户到 08:00 才看到入口%n",
                lateHours, lateHours);

        // 反向的坑更常见：库里存的是 UTC 墙上时间，代码却按东八区解析
        LocalDateTime utcWall = LocalDateTime.of(2026, 9, 30, 16, 0);
        Instant misread = utcWall.atZone(shanghai).toInstant();
        System.out.printf("  反向坑：UTC 墙上时间 09-30 16:00 被当成东八区 -> %s%n", misread);
        System.out.printf("  比正确时刻早开 %d 小时，等于活动还没开始就放量了%n",
                Duration.between(misread, right).toHours());
        System.out.println("  结论：时间必须成对存「Instant + 活动时区」，绝不单独存 LocalDateTime。");

        System.out.println();
        System.out.println("【3. 左闭右开：结束那一刻到底算不算】");
        Instant justBefore = end.minusMillis(1);
        System.out.printf("  结束前 1ms  %s  -> %s%n", justBefore, w.contains(justBefore) ? "可下单" : "已结束");
        System.out.printf("  结束时刻    %s  -> %s%n", end, w.contains(end) ? "可下单" : "已结束");
        System.out.printf("  结束后 1ms  %s  -> %s%n", end.plusMillis(1), w.contains(end.plusMillis(1)) ? "可下单" : "已结束");
        System.out.println("  用 [start, end) 就没有「23:59:59 漏一秒」的问题。");

        System.out.println();
        System.out.println("【4. 夏令时：某些时区某天没有 00:00】");
        ZoneId santiago = ZoneId.of("America/Santiago");
        LocalDate d = LocalDate.of(2026, 9, 6);
        ZonedDateTime zdt = d.atStartOfDay(santiago);
        System.out.printf("  %s 在 %s 的「当天开始」= %s%n", d, santiago, zdt);
        System.out.println("  这一天时钟从 00:00 直接跳到 01:00，atStartOfDay 自动给出 01:00，不会抛异常。");
        ZonedDateTime naive = ZonedDateTime.of(d, java.time.LocalTime.MIDNIGHT, santiago);
        System.out.printf("  若强行写 00:00：%s（被自动调整为 %s）%n", naive, naive.toLocalTime());

        System.out.println();
        System.out.println("【5. 重复时间：秋季回拨那天的 01:30 出现两次】");
        ZoneId ny = ZoneId.of("America/New_York");
        LocalDateTime dup = LocalDateTime.of(2026, 11, 1, 1, 30);
        ZonedDateTime first = dup.atZone(ny).withEarlierOffsetAtOverlap();
        ZonedDateTime second = dup.atZone(ny).withLaterOffsetAtOverlap();
        System.out.printf("  第一次 01:30 -> %s%n", first);
        System.out.printf("  第二次 01:30 -> %s%n", second);
        System.out.printf("  两者相差 %d 分钟，是同一个本地时间的不同瞬间%n",
                Duration.between(first, second).toMinutes());

        System.out.println();
        System.out.println("【6. 客户端时间不可信】");
        // 用固定时刻模拟「服务端时钟」，保证输出可复现
        Instant serverNow = Instant.parse("2026-09-30T15:00:00Z");
        Instant clientSays = Instant.parse("2026-10-01T02:00:00Z");   // 客户端时钟被调快 3 小时
        System.out.printf("  服务端时钟 %s（东八区 %s）%n", serverNow,
                serverNow.atZone(shanghai).toLocalDateTime());
        System.out.printf("  客户端声称 %s，比服务端快 %d 小时%n", clientSays,
                Duration.between(serverNow, clientSays).toHours());
        System.out.printf("  若信客户端：%s（客户端以为已开始）%n",
                w.contains(clientSays) ? "允许下单" : "拒绝下单");
        System.out.printf("  若信服务端：%s（还差 %d 分钟）%n",
                w.contains(serverNow) ? "允许下单" : "拒绝下单",
                Duration.between(serverNow, start).toMinutes());
        System.out.println("  服务端只信自己的时钟；客户端时间只用来展示倒计时，绝不能参与「能不能买」的判定。");

        System.out.println();
        System.out.println("【7. 活动时长与「每天几点」的表达】");
        long days = Duration.between(start, end).toDays();
        System.out.printf("  活动跨度 %d 天（按绝对时间算，不受时区影响）%n", days);
        System.out.println("  如果需求是「每天 10 点开抢 1 小时」，就要按用户时区每天算窗口，");
        System.out.println("  不能写死一个绝对时间，否则跨时区用户会在半夜抢。");
    }
}
''',
        exercises=[
            "把活动改成「每天 20:00-22:00」，用 `ZonedDateTime` 生成 7 个窗口，验证每个窗口都是左闭右开。",
            "给 Window 加一个 `remaining(Instant now)` 方法，返回剩余秒数，说明为什么不能用客户端时间算。",
            "思考题：如果运营说「活动到 10 月 7 日为止」，你会把它解释成 10-07 00:00 还是 10-08 00:00？为什么必须问清楚？",
        ],
    ),
    dict(
        cls="BudgetControl",
        title="预算控制与超发防护",
        points=[
            "**预算不是一个数字，是「预扣 + 实扣 + 释放」的三段式**：**领券先预扣、核销转实扣、过期或退款释放**——**只有预扣没有释放，预算会被未核销的券永久占住**。",
            "**「先查余额再扣」在并发下必然超发**：**两个请求都读到剩余 100 元，都判断「够发 50」，结果发出 100 元**——**和库存超卖是同一个 bug**。",
            "**预扣要用原子操作**：**`compareAndSet` 或 `decrementAndGet` 后判负再回滚**——**判负回滚要能区分「真的超了」和「并发竞争」，否则会把正常请求也拒掉**。",
            "**预算要按「维度」分层**：**总预算 → 活动预算 → 券模板预算 → 单用户预算**，**任何一层超了都要拦住**，**只控总预算会出现「一个券模板吃光全部预算」**。",
            "**熔断阈值要按速率算，不能按绝对值写死**：**先按「预算 / 计划时长」算出均值，超过均值 3 倍就告警**——**等预算见底才报警，已经晚了**，**按速率才能提前预判**。",
            "易错点：预算用 `double` 存（累加误差）；预扣后没有超时释放任务；只控总额不控单券；告警阈值按绝对值写死，活动大小一变就失效。",
        ],
        code=r'''import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;

public class BudgetControl {
    // 朴素预算：读-判-写，必然超发
    // 用栅栏把「读余额」和「扣余额」拉开，把真实环境里的并发窗口放大成必然
    static class NaiveBudget {
        long left;
        final int expectReaders;
        final java.util.concurrent.atomic.AtomicInteger readers
                = new java.util.concurrent.atomic.AtomicInteger();

        NaiveBudget(long left, int expectReaders) {
            this.left = left;
            this.expectReaders = expectReaders;
        }

        boolean reserve(long amount) {
            long cur = left;                     // 第 1 步：读余额
            if (cur < amount) {
                return false;
            }
            readers.incrementAndGet();
            while (readers.get() < expectReaders) {   // 等所有线程都读完
                Thread.onSpinWait();
            }
            left = cur - amount;                 // 第 3 步：写（覆盖别人的扣减）
            return true;
        }
    }

    // 预扣式预算：CAS 保证不超发
    static class Budget {
        final long total;
        final AtomicLong reserved = new AtomicLong();
        final AtomicLong consumed = new AtomicLong();
        final AtomicLong released = new AtomicLong();

        Budget(long total) {
            this.total = total;
        }

        boolean reserve(long amount) {
            while (true) {
                long cur = reserved.get();
                if (cur + amount > total) {
                    return false;
                }
                if (reserved.compareAndSet(cur, cur + amount)) {
                    return true;
                }
            }
        }

        void consume(long amount) {
            // 预扣转为实扣：占用减少、消耗增加，总额不变
            reserved.addAndGet(-amount);
            consumed.addAndGet(amount);
        }

        void release(long amount) {
            released.addAndGet(amount);
            reserved.addAndGet(-amount);
        }

        // 真正还能发的钱 = 总额 - 还占着的 - 已经花掉的
        long left() {
            return total - reserved.get() - consumed.get();
        }
    }

    static String yuan(long fen) {
        return String.format("%.2f", fen / 100.0);
    }

    public static void main(String[] args) throws Exception {
        long total = 1000000;   // 1 万元预算
        long coupon = 5000;     // 每张 50 元
        int threads = 300;

        System.out.printf("【1. 总预算 %s 元，每张券 %s 元，300 人同时领】%n", yuan(total), yuan(coupon));

        NaiveBudget naive = new NaiveBudget(total, threads);
        AtomicLong naiveOk = new AtomicLong();
        CountDownLatch s1 = new CountDownLatch(1);
        CountDownLatch d1 = new CountDownLatch(threads);
        for (int i = 0; i < threads; i++) {
            new Thread(() -> {
                try {
                    s1.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                if (naive.reserve(coupon)) {
                    naiveOk.incrementAndGet();
                }
                d1.countDown();
            }).start();
        }
        s1.countDown();
        d1.await();
        long naiveIssued = naiveOk.get() * coupon;
        System.out.println("  用栅栏强制「300 个线程先全部读完余额，再一起扣」，把微秒级窗口放大成必然");
        System.out.printf("  朴素写法：发出 %d 张，声称占用预算 %s 元，而总预算只有 %s 元，超发 %s 元%n",
                naiveOk.get(), yuan(naiveIssued), yuan(total),
                yuan(Math.max(0, naiveIssued - total)));
        System.out.printf("  预算上限本来只够 %d 张，实际却发出 %d 张%n", total / coupon, naiveOk.get());
        System.out.printf("  更糟的是余额变量只剩最后一次写入：%s 元，账面上看不出已经发了 %d 张%n",
                yuan(naive.left), naiveOk.get());

        System.out.println();
        Budget b = new Budget(total);
        AtomicLong ok = new AtomicLong();
        CountDownLatch s2 = new CountDownLatch(1);
        CountDownLatch d2 = new CountDownLatch(threads);
        for (int i = 0; i < threads; i++) {
            new Thread(() -> {
                try {
                    s2.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                if (b.reserve(coupon)) {
                    ok.incrementAndGet();
                }
                d2.countDown();
            }).start();
        }
        s2.countDown();
        d2.await();
        System.out.printf("  CAS 写法：发出 %d 张，占用预算 %s 元，剩余 %s 元%n",
                ok.get(), yuan(ok.get() * coupon), yuan(b.left()));
        System.out.printf("  发出张数恒为 %d，剩余 %s 元不够一张，所以不再发%n",
                total / coupon, yuan(b.left()));

        System.out.println();
        System.out.println("【2. 预扣 -> 实扣 / 释放】");
        Budget b2 = new Budget(total);
        b2.reserve(coupon * 4);
        System.out.printf("  预扣 4 张：剩余 %s 元%n", yuan(b2.left()));
        b2.consume(coupon * 3);
        b2.release(coupon);
        System.out.printf("  核销 3 张、过期释放 1 张：剩余 %s 元，已消耗 %s 元%n",
                yuan(b2.left()), yuan(b2.consumed.get()));
        System.out.println("  只预扣不释放，那 1 张过期券的预算就永远占着，活动看起来「预算用完了」其实没花出去。");

        System.out.println();
        System.out.println("【3. 分层预算：总预算 / 券模板 / 单用户】");
        long activityBudget = 1000000;
        long templateA = 600000;
        long templateB = 500000;
        long perUser = 20000;
        System.out.printf("  活动预算 %s，券A 模板 %s，券B 模板 %s（两者之和 %s > 活动预算）%n",
                yuan(activityBudget), yuan(templateA), yuan(templateB), yuan(templateA + templateB));
        System.out.println("  模板预算之和可以大于总预算，因为它们是「上限」不是「预留」；");
        System.out.println("  真实发放时先扣总预算，再扣模板预算，任何一层不足都拒绝。");

        System.out.println();
        System.out.println("【4. 单用户预算：同一人最多领 200 元】");
        List<Long> userGrants = new ArrayList<>();
        long userUsed = 0;
        for (int i = 0; i < 6; i++) {
            if (userUsed + coupon <= perUser) {
                userUsed += coupon;
                userGrants.add(coupon);
                System.out.printf("  第 %d 次领券：成功，累计 %s 元%n", i + 1, yuan(userUsed));
            } else {
                System.out.printf("  第 %d 次领券：拒绝，累计 %s 元 + %s 元 > 上限 %s 元%n",
                        i + 1, yuan(userUsed), yuan(coupon), yuan(perUser));
            }
        }

        System.out.println();
        System.out.println("【5. 按消耗速率熔断，而不是等预算见底】");
        int plannedMinutes = 30;                  // 活动计划 30 分钟发完
        long avgRate = total / plannedMinutes;    // 平均每分钟可发
        long limit = avgRate * 3;                 // 超过平均速率 3 倍就告警
        System.out.printf("  预算 %s 元计划 %d 分钟发完，平均速率 %s 元/分钟，告警线 = 3 倍 = %s 元/分钟%n",
                yuan(total), plannedMinutes, yuan(avgRate), yuan(limit));

        long[] perMinute = {30000, 45000, 110000, 120000, 150000};
        long spent = 0;
        int firstAlarm = -1;
        long spentAtAlarm = 0;
        for (int i = 0; i < perMinute.length; i++) {
            long before = spent;
            spent += perMinute[i];
            boolean alarm = perMinute[i] > limit;
            if (alarm && firstAlarm < 0) {
                firstAlarm = i + 1;
                spentAtAlarm = spent;
            }
            System.out.printf("  第 %d 分钟消耗 %s 元（均值的 %.1f 倍），累计 %s 元%s%n",
                    i + 1, yuan(perMinute[i]), perMinute[i] / (double) avgRate,
                    yuan(spent), alarm ? "   <- 超过 " + yuan(limit) + " 元/分钟，告警" : "");
        }
        System.out.printf("  首次告警在第 %d 分钟，此时累计只花掉 %s 元（占预算 %.1f%%）%n",
                firstAlarm, yuan(spentAtAlarm), spentAtAlarm * 100.0 / total);
        long remaining = total - spent;
        System.out.printf("  按最后 1 分钟的 %s 元/分钟，剩余 %s 元只够撑 %.1f 分钟%n",
                yuan(perMinute[perMinute.length - 1]), yuan(remaining),
                remaining / (double) perMinute[perMinute.length - 1]);
        System.out.println("  等预算见底再报警已经晚了：速率告警在只花掉一小部分时就给运营留出了处置时间。");
    }
}
''',
        exercises=[
            "把预算改成「按小时配额」（每小时最多发总预算的 1/24），验证突发流量不会一次吃光全天预算。",
            "给 Budget 加一个 `snapshot()` 方法，返回预扣/实扣/释放/剩余四个数字，说明这四个数为什么必须都对得上。",
            "思考题：如果预扣成功但券发放失败（写库异常），预算怎么回滚？回滚失败又怎么办？",
        ],
    ),
    dict(
        cls="MarketingAttribution",
        title="营销归因与增量 ROI",
        points=[
            "**表面 ROI = 活动期成交额 / 优惠成本，这个数几乎总是虚高的**：**因为下单的人里有一大部分本来就会买**，**券只是把钱送给了原本的买家**。",
            "**增量 ROI 要减去「本来就会发生」的部分**：**增量 = 实验组成交 - 对照组成交（按同一口径、同一时间窗）**，**增量 ROI = 增量毛利 / 优惠成本**。",
            "**对照组不能是「活动前一周」**：**大促前后本来就有自然波动（周末、发薪日、天气）**，**必须同时段随机分流做 A/B，才能剥离时间因素**。",
            "**券的「核销率」高不代表有效**：**无门槛券核销率天然高，但增量可能接近 0**，**有门槛券核销率低，却可能撬动了真实的凑单**。",
            "**归因窗口要固定**：**「领券后 7 天内下单算活动效果」与「30 天」会得出完全不同的 ROI**，**窗口越长，把自然成交算进来的越多**。",
            "易错点：用活动期总 GMV 对比活动前一周；把「用了券的订单」全算成增量；忽略退货（退货后的 GMV 不是真成交）；只算 GMV 不算毛利（高毛利品类被低毛利品类拉低）。",
        ],
        code=r'''public class MarketingAttribution {
    record Group(String name, long users, long orders, long gmvFen, long grossFen,
                 long couponCostFen, long refundFen) {
        long netGmv() {
            return gmvFen - refundFen;
        }

        double conversion() {
            return orders * 100.0 / users;
        }

        double aov() {
            return orders == 0 ? 0 : netGmv() / 100.0 / orders;
        }
    }

    static String yuan(long fen) {
        return String.format("%.2f", fen / 100.0);
    }

    static String pct(double v) {
        return String.format("%.2f%%", v);
    }

    public static void main(String[] args) {
        // 同期随机分流：实验组发券，对照组不发
        // 口径：GMV 为下单金额，refundFen 为退货金额，grossFen 为「已扣退货后的净毛利」
        Group exp = new Group("实验组（发券）", 500_000, 62_000,
                1_860_000_000L, 470_000_000L, 155_000_000L, 111_600_000L);
        Group ctrl = new Group("对照组（不发券）", 500_000, 54_000,
                1_620_000_000L, 397_000_000L, 0L, 64_800_000L);

        System.out.println("【1. 两组基础数据（同期随机分流，各 50 万人）】");
        System.out.printf("  %-16s %-10s %-10s %-14s %-14s %s%n",
                "组", "订单数", "转化率", "净GMV(元)", "净毛利(元)", "客单价(元)");
        for (Group g : new Group[]{exp, ctrl}) {
            System.out.printf("  %-16s %-10d %-10s %-14s %-14s %.2f%n",
                    g.name(), g.orders(), pct(g.conversion()), yuan(g.netGmv()),
                    yuan(g.grossFen()), g.aov());
        }

        System.out.println();
        System.out.println("【2. 表面 ROI：拿实验组自己的成交去除优惠成本】");
        double surfaceRoi = exp.grossFen() / (double) exp.couponCostFen();
        System.out.printf("  表面 ROI = 实验组毛利 %s / 优惠成本 %s = %.2f%n",
                yuan(exp.grossFen()), yuan(exp.couponCostFen()), surfaceRoi);
        System.out.println("  这个数把「本来就会买的人」的毛利也算成了活动功劳。");

        System.out.println();
        System.out.println("【3. 增量 ROI：减去对照组本来就会发生的部分】");
        long incOrders = exp.orders() - ctrl.orders();
        long incGmv = exp.netGmv() - ctrl.netGmv();
        long incGross = exp.grossFen() - ctrl.grossFen();
        double incRoi = incGross / (double) exp.couponCostFen();
        System.out.printf("  增量订单 %d 单，增量净GMV %s 元，增量净毛利 %s 元%n",
                incOrders, yuan(incGmv), yuan(incGross));
        System.out.println("  注意这里是「单」不是「人」：订单差与人数差不是一回事，口径写错会把结论放大好几倍。");
        System.out.printf("  增量 ROI = %s / %s = %.2f%n",
                yuan(incGross), yuan(exp.couponCostFen()), incRoi);
        System.out.printf("  表面 ROI %.2f vs 增量 ROI %.2f，虚高 %.1f 倍%n",
                surfaceRoi, incRoi, surfaceRoi / incRoi);

        System.out.println();
        System.out.println("【4. 券成本摊到增量订单上，每单花多少】");
        long subsidyPerOrder = exp.couponCostFen() / incOrders;
        long grossPerOrder = incGross / incOrders;
        System.out.printf("  优惠成本 %s 元 / 增量订单 %d 单 = 每单补贴 %s 元%n",
                yuan(exp.couponCostFen()), incOrders, yuan(subsidyPerOrder));
        System.out.printf("  增量订单客单价 %s 元，每单净毛利 %s 元%n",
                yuan(incGmv / incOrders), yuan(grossPerOrder));
        System.out.printf("  每单补贴 %s 元 > 每单毛利 %s 元，单看增量订单是「补贴高于毛利」%n",
                yuan(subsidyPerOrder), yuan(grossPerOrder));
        System.out.println("  这正是增量 ROI < 1 的含义：增量订单自己养不活自己的优惠成本。");

        System.out.println();
        System.out.println("【5. 退货对结论的影响】");
        System.out.printf("  实验组退货 %s 元，占 GMV %.2f%%%n",
                yuan(exp.refundFen()), exp.refundFen() * 100.0 / exp.gmvFen());
        System.out.printf("  对照组退货 %s 元，占 GMV %.2f%%%n",
                yuan(ctrl.refundFen()), ctrl.refundFen() * 100.0 / ctrl.gmvFen());
        long incGmvGross = exp.gmvFen() - ctrl.gmvFen();
        System.out.printf("  含退货的增量 GMV %s 元，扣退货后 %s 元，缩水 %.1f%%%n",
                yuan(incGmvGross), yuan(incGmv),
                (incGmvGross - incGmv) * 100.0 / incGmvGross);
        System.out.printf("  若拿含退货 GMV 去算 ROI 会得到 %.2f，用净毛利算只有 %.2f%n",
                incGmvGross / (double) exp.couponCostFen(), incRoi);
        System.out.printf("  券吸引来的用户退货率更高（%.0f%% vs %.0f%%）时，只看下单 GMV 会高估近两成。%n",
                exp.refundFen() * 100.0 / exp.gmvFen(), ctrl.refundFen() * 100.0 / ctrl.gmvFen());

        System.out.println();
        System.out.println("【6. 归因窗口：7 天 vs 30 天】");
        // 把增量净GMV 按日拆开：前 7 天占大头，第 8~30 天是长尾
        long[] daily = {900_000_00L, 450_000_00L, 260_000_00L, 150_000_00L,
                92_000_00L, 50_000_00L, 30_000_00L};
        long seven = 0;
        for (long v : daily) {
            seven += v;
        }
        long tail = 670_000_00L;          // 第 8~30 天的长尾归因 GMV
        long thirty = seven + tail;
        System.out.printf("  前 7 天逐日增量净GMV（元）：");
        for (long v : daily) {
            System.out.printf("%s ", yuan(v));
        }
        System.out.println();
        System.out.printf("  7 天窗口合计 %s 元，加上 8~30 天长尾 %s 元 = 30 天窗口 %s 元%n",
                yuan(seven), yuan(tail), yuan(thirty));
        double roi7 = incGross / (double) exp.couponCostFen();
        double roi30 = incGross * (thirty / (double) seven) / exp.couponCostFen();
        System.out.printf("  同一笔优惠成本 %s 元：7 天口径 ROI %.2f，30 天口径 ROI %.2f%n",
                yuan(exp.couponCostFen()), roi7, roi30);
        System.out.printf("  窗口从 7 天拉到 30 天，ROI 从 %.2f 涨到 %.2f，涨的 %.0f%% 全是自然成交%n",
                roi7, roi30, (roi30 / roi7 - 1) * 100);
        System.out.println("  窗口越长越好看，所以口径必须活动前定死并写进复盘模板，不能事后挑一个好看的。");
    }
}
''',
        exercises=[
            "把实验组转化率从 12.4% 调到 11.0%，重算增量 ROI，说明「表面 ROI 仍然好看但增量接近 0」是什么情形。",
            "给对照组也加一个「平台通用券」成本，说明为什么对照必须「除被测变量外完全一致」。",
            "思考题：如果活动期间同时上了「降价 + 发券」，怎么拆分各自的增量？说出至少两种可行方案。",
        ],
    ),
    dict(
        cls="PromotionCase",
        title="综合案例：大促券全链路",
        points=[
            "**一次领券要穿过 6 个环节**：**校验活动 → 校验资格 → 预扣预算 → 生成券码 → 落库 → 返回**，**任一环节失败都要能回滚前面的预扣**。",
            "**领券与核销是两个独立的并发场景**：**领券峰值在整点（5 万 QPS），核销峰值在下单时段（2 万 QPS）**，**两者共用预算与库存，要分别压测**。",
            "**超发与超卖必须在同一套「预扣 + 幂等」框架里解决**：**预算预扣、库存预扣、用户限购计数**，**三个都是「读-判-写」，都要原子化**。",
            "**券的「可用」判定要在下单时重算**：**领券时满足门槛，下单时可能因为商品变价、券过期、活动下线而不可用**——**不能只信领券时的校验结果**。",
            "**全链路要有对账**：**发出的券数 × 面额 = 预扣预算**、**核销的券数 × 面额 = 实扣预算**、**过期券要释放**，**三个数每天对一次，差额就是 bug**。",
            "易错点：领券成功但预算没扣（重启丢内存）；核销成功但券状态没变（双花）；过期券不释放预算（预算假性耗尽）；活动下线后旧券仍能核销（资损）。",
        ],
        code=r'''import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;

public class PromotionCase {
    record Coupon(String code, String userId, long faceFen, boolean used, boolean expired) {
        Coupon use() {
            return new Coupon(code, userId, faceFen, true, expired);
        }
    }

    static class Platform {
        final long budgetFen;
        final AtomicLong reserved = new AtomicLong();
        final AtomicLong consumed = new AtomicLong();
        final AtomicLong released = new AtomicLong();
        final Map<String, Coupon> coupons = new HashMap<>();
        final Map<String, Integer> userLimit = new HashMap<>();
        final int perUser;
        final AtomicLong rejected = new AtomicLong();
        final AtomicLong duplicated = new AtomicLong();

        Platform(long budgetFen, int perUser) {
            this.budgetFen = budgetFen;
            this.perUser = perUser;
        }

        // 领券：校验 -> 预扣 -> 落库，任一步失败都回滚
        synchronized boolean claim(String userId, long face, String code) {
            if (coupons.containsKey(code)) {
                duplicated.incrementAndGet();
                return false;
            }
            int got = userLimit.getOrDefault(userId, 0);
            if (got >= perUser) {
                rejected.incrementAndGet();
                return false;
            }
            while (true) {
                long cur = reserved.get();
                if (cur + face > budgetFen) {
                    rejected.incrementAndGet();
                    return false;
                }
                if (reserved.compareAndSet(cur, cur + face)) {
                    break;
                }
            }
            coupons.put(code, new Coupon(code, userId, face, false, false));
            userLimit.put(userId, got + 1);
            return true;
        }

        // 核销：只有「未用、未过期、且属于本人」的券能核销
        synchronized String redeem(String userId, String code) {
            Coupon c = coupons.get(code);
            if (c == null) {
                return "券不存在";
            }
            if (!c.userId().equals(userId)) {
                return "不是本人的券";
            }
            if (c.expired()) {
                return "券已过期";
            }
            if (c.used()) {
                return "券已使用（重复核销被拦）";
            }
            coupons.put(code, c.use());
            // 预扣转实扣：占用减少、消耗增加
            reserved.addAndGet(-c.faceFen());
            consumed.addAndGet(c.faceFen());
            return "核销成功，抵扣 " + yuan(c.faceFen()) + " 元";
        }

        // 过期释放预算
        synchronized void expire(String code) {
            Coupon c = coupons.get(code);
            if (c == null || c.used() || c.expired()) {
                return;
            }
            coupons.put(code, new Coupon(c.code(), c.userId(), c.faceFen(), false, true));
            reserved.addAndGet(-c.faceFen());
            released.addAndGet(c.faceFen());
        }

        // 可发余额 = 预算 - 仍占用 - 已消耗
        long left() {
            return budgetFen - reserved.get() - consumed.get();
        }
    }

    static String yuan(long fen) {
        return String.format("%.2f", fen / 100.0);
    }

    public static void main(String[] args) throws Exception {
        long budget = 25_000_000_00L / 100;   // 25 万元
        long face = 5000;                     // 50 元券
        int perUser = 2;
        Platform p = new Platform(budget, perUser);

        System.out.printf("【1. 活动配置】总预算 %s 元，每张 %s 元，每人限 %d 张%n",
                yuan(budget), yuan(face), perUser);

        System.out.println();
        System.out.println("【2. 领券峰值：1000 个用户并发抢，每人不重复】");
        int users = 1000;
        AtomicLong ok = new AtomicLong();
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(users);
        for (int i = 0; i < users; i++) {
            final int uid = i;
            new Thread(() -> {
                try {
                    start.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                String userId = "u" + uid;
                for (int k = 0; k < 3; k++) {
                    if (p.claim(userId, face, userId + "-" + k)) {
                        ok.incrementAndGet();
                    }
                }
                done.countDown();
            }).start();
        }
        start.countDown();
        done.await();

        System.out.printf("  成功领券 %d 张，占用预算 %s 元，剩余 %s 元%n",
                ok.get(), yuan(p.reserved.get()), yuan(p.left()));
        System.out.printf("  因限购被拒 %d 次（每人都试了 3 次，限 %d 张）%n",
                p.rejected.get(), perUser);
        System.out.printf("  校验：占用 %s == 张数 %d x %s = %s  -> %s%n",
                yuan(p.reserved.get()), ok.get(), yuan(face), yuan(ok.get() * face),
                p.reserved.get() == ok.get() * face ? "对得上" : "对不上");

        System.out.println();
        System.out.println("【3. 核销：正常、重复、越权、过期 四种情形】");
        System.out.printf("  起始：已消耗 %s，占用 %s，剩余 %s%n",
                yuan(p.consumed.get()), yuan(p.reserved.get()), yuan(p.left()));
        System.out.printf("  u0 核销自己的券：%s%n", p.redeem("u0", "u0-0"));
        System.out.printf("    -> 已消耗 %s，占用 %s，剩余 %s（预扣转实扣，总额不变）%n",
                yuan(p.consumed.get()), yuan(p.reserved.get()), yuan(p.left()));
        System.out.printf("  u0 再次核销同一张：%s%n", p.redeem("u0", "u0-0"));
        System.out.printf("  u1 核销 u0 的券：%s%n", p.redeem("u1", "u0-0"));
        System.out.printf("    -> 两次非法核销都没动账：已消耗 %s，占用 %s%n",
                yuan(p.consumed.get()), yuan(p.reserved.get()));
        p.expire("u0-1");
        System.out.printf("  u0 核销已过期的券（u0-1 已被过期任务释放）：%s%n", p.redeem("u0", "u0-1"));
        System.out.printf("    -> 过期释放 1 张后：占用 %s，剩余 %s（释放的预算回到可发池）%n",
                yuan(p.reserved.get()), yuan(p.left()));

        System.out.println();
        System.out.println("【4. 过期释放：预算要还回来】");
        long before = p.left();
        int expiredCount = 0;
        for (String code : new ArrayList<>(p.coupons.keySet())) {
            Coupon c = p.coupons.get(code);
            if (!c.used() && !c.expired()) {
                expiredCount++;
            }
            p.expire(code);
        }
        long after = p.left();
        System.out.printf("  批量过期 %d 张，剩余 %s 元 -> %s 元，释放 %s 元%n",
                expiredCount, yuan(before), yuan(after), yuan(after - before));

        // 对账恒等式：预算 = 已核销 + 仍占用 + 剩余（释放的已经回到「剩余」里，不能再加一次）
        long consumed = p.consumed.get();
        long stillReserved = p.reserved.get();
        long leftNow = p.left();
        System.out.printf("  已核销 %s 元 + 仍占用 %s 元 + 剩余 %s 元 = %s 元%n",
                yuan(consumed), yuan(stillReserved), yuan(leftNow),
                yuan(consumed + stillReserved + leftNow));
        System.out.printf("  校验「已核销 + 仍占用 + 剩余」== 预算 %s 元  -> %s%n", yuan(budget),
                consumed + stillReserved + leftNow == budget ? "守恒" : "不守恒");
        System.out.printf("  累计释放 %s 元（含 u0-1 那 1 张）已经并回「剩余」，对账时再加一次就会重复计数%n",
                yuan(p.released.get()));
        System.out.println("  这是对账脚本最常见的 bug：把「已释放」当成独立的一项去加，凭空多出预算。");

        System.out.println();
        System.out.println("【5. 下单时重算「券可用」，不能只信领券时的校验】");
        long orderAmount = 28000;    // 280 元
        long threshold = 30000;      // 券门槛 300 元
        System.out.printf("  领券时订单 %s 元，满足门槛 %s 元%n", yuan(30000), yuan(threshold));
        System.out.printf("  下单时订单变成 %s 元（商品降价/取消一件）%n", yuan(orderAmount));
        System.out.printf("  重算结果：%s%n", orderAmount >= threshold ? "可用" : "不可用，券要退回");
        System.out.println("  如果下单时直接复用领券时的结论，就会「不满门槛也把券用了」。");

        System.out.println();
        System.out.println("【6. 三本账每天对一次，差额就是 bug】");
        List<String> books = new ArrayList<>();
        books.add("发放账：券数 " + p.coupons.size() + " 张 x " + yuan(face) + " = " + yuan(p.coupons.size() * face));
        books.add("预算账：预扣 " + yuan(p.reserved.get()) + "，实扣 " + yuan(p.consumed.get())
                + "，释放 " + yuan(p.released.get()));
        books.add("核销账：已用 " + p.coupons.values().stream().filter(Coupon::used).count()
                + " 张 = " + yuan(p.consumed.get()));
        for (String s : books) {
            System.out.println("  " + s);
        }
        long issuedTotal = p.coupons.size() * face;
        long budgetTotal = p.reserved.get() + p.consumed.get() + p.released.get();
        long redeemTotal = p.consumed.get();
        System.out.printf("  三本账核对：发放 %s == 预算 %s -> %s；核销 %s == 实扣 %s -> %s%n",
                yuan(issuedTotal), yuan(budgetTotal),
                issuedTotal == budgetTotal ? "对得上" : "对不上",
                yuan(redeemTotal), yuan(p.consumed.get()),
                redeemTotal == p.consumed.get() ? "对得上" : "对不上");
        System.out.println("  本次运行三本账全部对得上；任何一本对不上，就说明有券漏记、预算漏扣、或核销漏更新状态。");
        System.out.println("  对账脚本的价值不在「平时跑通」，而在出事那天能第一时间定位是哪一本账先歪的。");
    }
}
''',
        exercises=[
            "把预算调到只够 500 张券，验证超出的部分全部被拒且占用预算严格等于发放张数 × 面额。",
            "给核销加幂等键（userId + orderId），验证同一订单重复核销只生效一次。",
            "思考题：活动下线后，已发出的券还能不能核销？两种策略（允许/拒绝）各自的资损风险是什么？",
        ],
    ),
]
