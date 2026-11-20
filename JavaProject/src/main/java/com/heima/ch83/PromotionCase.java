/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：83-11
 * 课名：综合案例：大促券全链路
 * ------------------------------------------------------------
 * 知识点：
 *   - **一次领券要穿过 6 个环节**：**校验活动 → 校验资格 → 预扣预算 → 生成券码 → 落库 → 返回**，**任一环节失败都要能回滚前面的预扣**。
 *   - **领券与核销是两个独立的并发场景**：**领券峰值在整点（5 万 QPS），核销峰值在下单时段（2 万 QPS）**，**两者共用预算与库存，要分别压测**。
 *   - **超发与超卖必须在同一套「预扣 + 幂等」框架里解决**：**预算预扣、库存预扣、用户限购计数**，**三个都是「读-判-写」，都要原子化**。
 *   - **券的「可用」判定要在下单时重算**：**领券时满足门槛，下单时可能因为商品变价、券过期、活动下线而不可用**——**不能只信领券时的校验结果**。
 *   - **全链路要有对账**：**发出的券数 × 面额 = 预扣预算**、**核销的券数 × 面额 = 实扣预算**、**过期券要释放**，**三个数每天对一次，差额就是 bug**。
 *   - 易错点：领券成功但预算没扣（重启丢内存）；核销成功但券状态没变（双花）；过期券不释放预算（预算假性耗尽）；活动下线后旧券仍能核销（资损）。
 * ============================================================
 */

package com.heima.ch83;

// ==================== 示例代码 ====================

import java.util.ArrayList;
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

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 把预算调到只够 500 张券，验证超出的部分全部被拒且占用预算严格等于发放张数 × 面额。
 * 2. 给核销加幂等键（userId + orderId），验证同一订单重复核销只生效一次。
 * 3. 思考题：活动下线后，已发出的券还能不能核销？两种策略（允许/拒绝）各自的资损风险是什么？
 * ============================================================
 */

/*
 * ============================================================
 * 本章综合练习（无参考答案）
 * ------------------------------------------------------------
 * 【场景】
 * 双十一当天要发一批「满 300 减 50」全场券，总预算 2500 万元，可用库存 10 万件，预计 5 万 QPS 的领券峰值，日活 800 万。运营的目标是「把预算花完、把库存卖完、不要超发、不要被黄牛刷走」，财务的目标是「每一分优惠都能对账、能分摊到行、能按行退」。请设计这套促销引擎并给出验证数据。
 * 【任务】
 * 1. 定链路：画出从原价到应付的价格计算阶段，写清每一步的输入输出，并说明折扣与满减互换顺序会差多少钱。
 * 2. 算优惠：实现满减、阶梯、折扣（带上限）、立减四类算法，说明按行取整与整单取整的差额，并给出固定规则。
 * 3. 配叠加：设计券的互斥组与叠加规则，用穷举求出最优组合，并给出一个「按面额贪心会算贵」的反例。
 * 4. 控库存与预算：用 CAS 扣减防超卖，用预扣预算防超发，给出限购（用户 + 设备）与幂等键方案。
 * 5. 验效果：算出表面 ROI 与增量 ROI 的差距，说明为什么「券都核销了」不等于「活动赚钱了」。
 * 【验收清单】
 * [ ] 价格链路阶段固定且可追溯，顺序差异有具体金额对比，不是「大概差不多」。
 * [ ] 四类优惠的取整规则统一且写死，分摊后各行优惠之和严格等于优惠总额。
 * [ ] 券组合有穷举最优解，且指出了贪心算法的失效场景与差额。
 * [ ] 库存与预算都有并发扣减方案，超卖/超发能被复现也能被修好，限购覆盖用户与设备两个维度。
 * [ ] 效果评估区分表面 ROI 与增量 ROI，结论带增量口径，不是只看核销率。
 * ============================================================
 */

