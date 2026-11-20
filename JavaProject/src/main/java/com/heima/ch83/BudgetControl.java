/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：83-09
 * 课名：预算控制与超发防护
 * ------------------------------------------------------------
 * 知识点：
 *   - **预算不是一个数字，是「预扣 + 实扣 + 释放」的三段式**：**领券先预扣、核销转实扣、过期或退款释放**——**只有预扣没有释放，预算会被未核销的券永久占住**。
 *   - **「先查余额再扣」在并发下必然超发**：**两个请求都读到剩余 100 元，都判断「够发 50」，结果发出 100 元**——**和库存超卖是同一个 bug**。
 *   - **预扣要用原子操作**：**`compareAndSet` 或 `decrementAndGet` 后判负再回滚**——**判负回滚要能区分「真的超了」和「并发竞争」，否则会把正常请求也拒掉**。
 *   - **预算要按「维度」分层**：**总预算 → 活动预算 → 券模板预算 → 单用户预算**，**任何一层超了都要拦住**，**只控总预算会出现「一个券模板吃光全部预算」**。
 *   - **熔断阈值要按速率算，不能按绝对值写死**：**先按「预算 / 计划时长」算出均值，超过均值 3 倍就告警**——**等预算见底才报警，已经晚了**，**按速率才能提前预判**。
 *   - 易错点：预算用 `double` 存（累加误差）；预扣后没有超时释放任务；只控总额不控单券；告警阈值按绝对值写死，活动大小一变就失效。
 * ============================================================
 */

package com.heima.ch83;

// ==================== 示例代码 ====================

import java.util.ArrayList;
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

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 把预算改成「按小时配额」（每小时最多发总预算的 1/24），验证突发流量不会一次吃光全天预算。
 * 2. 给 Budget 加一个 `snapshot()` 方法，返回预扣/实扣/释放/剩余四个数字，说明这四个数为什么必须都对得上。
 * 3. 思考题：如果预扣成功但券发放失败（写库异常），预算怎么回滚？回滚失败又怎么办？
 * ============================================================
 */

