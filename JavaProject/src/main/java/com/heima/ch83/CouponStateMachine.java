/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：83-05
 * 课名：券状态机与并发核销
 * ------------------------------------------------------------
 * 知识点：
 *   - **券的生命周期是一个状态机**：**已发放（ISSUED）→ 已锁定（LOCKED）→ 已使用（USED）**，**旁支是已过期（EXPIRED）与已作废（VOID）**，**只有明确的状态图才能防住「一券多用」**。
 *   - **「锁定」这一态是必须的**：**下单时锁券、支付成功才置为已使用、超时未付要回滚成已发放**——**没有锁定态就只能「先扣后用」，用户下单不付款会把券白占住**。
 *   - **非法迁移要显式拒绝**：**已使用不能回锁定、已过期不能锁定、已作废不能复活**——**用 `Set<迁移>` 白名单校验，比一堆 `if` 更不容易漏**。
 *   - **并发核销必须用 CAS**：**「读状态 → 判断 → 写状态」三步不是原子的**，**两个请求同时读到 ISSUED 就会双双成功**，**必须 `compareAndSet(ISSUED, LOCKED)`**。
 *   - **幂等键要跟券绑定**：**同一个券的重复请求要返回「第一次的结果」而不是报错**，否则用户重试会看到「券已被使用」而恐慌。
 *   - 易错点：用 `synchronized` 包住整个核销（吞吐掉到个位数）；锁券后没设超时回滚；把「已过期」当异常抛给用户（应该是正常业务态）；状态字段用 String 随便写。
 * ============================================================
 */

package com.heima.ch83;

// ==================== 示例代码 ====================

import java.util.ArrayList;
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

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 给状态机加一条「已锁定超时 15 分钟自动回滚」的定时任务，验证回滚后券可以再次被锁定。
 * 2. 把 ALLOWED 改成用 `Map<State, Set<State>>` 从配置加载，说明这样做的好处与风险。
 * 3. 思考题：如果核销要跨两个服务（券服务 + 订单服务），CAS 就不够了，应该用什么方案保证不重复？
 * ============================================================
 */

