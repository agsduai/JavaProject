/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：33-08
 * 课名：异常契约、参数校验与并发测试
 * ------------------------------------------------------------
 * 知识点：
 *   - **异常契约**：方法除了"返回什么"，还要约定"什么情况下抛什么异常"。契约要写进 JavaDoc 的 `@throws`，测试要覆盖它。异常类型选错（该 `IllegalArgumentException` 却抛 `NullPointerException`）会让调用方无法精准处理。
 *   - 异常分类的用法：**参数非法** → `IllegalArgumentException`（含 `Objects.requireNonNull` 的 `NullPointerException`）；**状态不允许** → `IllegalStateException`；**受检的业务失败** → 自定义 `XxxException extends Exception`；**不该发生** → `AssertionError` / 直接让 NPE 暴露。
 *   - 不要用异常做流程控制：能用返回值表达"业务失败"（如 `Result.fail("NO_STOCK")`）就不要抛异常。异常只用于"调用方写错了"或"环境异常"。
 *   - **异常包装**：底层异常（SQLException）不要直接抛给上层，要转成领域异常并**保留 cause**（`new OrderException("创建订单失败", e)`）。丢掉 cause 会毁掉排查线索。
 *   - 测试异常的三个层次：**类型**（`assertThrows(IllegalArgumentException.class, ...)`）、**message 关键片段**（`contains("金额")`）、**cause 链**（`assertEquals(SQLException.class, e.getCause().getClass())`）。只测类型常常不够。
 *   - **不该抛的异常**要显式验证：正常路径不应抛异常（用 `assertDoesNotThrow`），或者"某异常绝不该出现"（捕获后 `fail()`）。
 *   - **参数校验**放在方法入口，越早越好（fail fast）。`Objects.requireNonNull(x, "x 不能为空")`、`Objects.checkIndex`、`Math.addExact`（溢出即抛）都是 JDK 自带的好工具。
 *   - **并发测试**的难点是"不确定性"。三个手段：`CountDownLatch` 让线程同时起跑、重复多次（1000 轮）提高暴露概率、断言**不变式**（总量守恒）而不是具体交错顺序。
 *   - 并发测试不要断言"顺序"，只断言"结果合法"。同时给测试设超时（`@Timeout`），避免挂死。
 *   - 易错：`catch (Exception e) {}` 吞异常（测试永远绿）；断言 message 用全等（改文案就红，应用 `contains`）；并发测试只跑一次就认为没问题；用 `Thread.sleep` 代替同步。
 * ============================================================
 */

package com.heima.ch33;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

// ==================== 示例代码 ====================

public class ExceptionContract {
    static class OrderException extends Exception {
        private static final long serialVersionUID = 1L;

        OrderException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** 模拟底层存储异常 */
    static class DiskFullException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        DiskFullException(String msg) {
            super(msg);
        }
    }

    static class OrderRepo {
        private final boolean broken;
        private final AtomicInteger writes = new AtomicInteger();

        OrderRepo(boolean broken) {
            this.broken = broken;
        }

        int write(String orderNo) {
            if (broken) {
                throw new DiskFullException("磁盘已满");
            }
            return writes.incrementAndGet();
        }

        int writes() {
            return writes.get();
        }
    }

    static class OrderService {
        private final OrderRepo repo;

        OrderService(OrderRepo repo) {
            this.repo = repo;
        }

        /**
         * 创建订单。
         *
         * @throws NullPointerException     orderNo 为 null
         * @throws IllegalArgumentException orderNo 为空白、金额不为正
         * @throws OrderException           底层写入失败（保留 cause）
         */
        String create(String orderNo, long amountCents) throws OrderException {
            Objects.requireNonNull(orderNo, "orderNo 不能为 null");
            if (orderNo.isBlank()) {
                throw new IllegalArgumentException("orderNo 不能为空白");
            }
            if (amountCents <= 0) {
                throw new IllegalArgumentException("金额必须为正，实际=" + amountCents);
            }
            try {
                repo.write(orderNo);
                return "CREATED:" + orderNo;
            } catch (RuntimeException e) {
                throw new OrderException("创建订单失败: " + orderNo, e);
            }
        }

        /** 状态类错误用 IllegalStateException */
        void pay(String status) {
            if (!"CREATED".equals(status)) {
                throw new IllegalStateException("当前状态不允许支付: " + status);
            }
        }
    }

    static int passed;
    static final List<String> failures = new ArrayList<>();

    static void check(String name, boolean ok, String detail) {
        if (ok) {
            passed++;
            System.out.printf("    %-56s OK%n", name);
        } else {
            failures.add(name + " -> " + detail);
            System.out.printf("    %-56s FAIL: %s%n", name, detail);
        }
    }

    interface ThrowingCall {
        void run() throws Exception;
    }

    /** 断言异常类型 + message 片段 + cause 类型 */
    static void assertThrows(String name, Class<? extends Throwable> type,
                             String messagePart, Class<? extends Throwable> causeType,
                             ThrowingCall call) {
        try {
            call.run();
            check(name, false, "期望抛 " + type.getSimpleName() + "，但没抛");
        } catch (Throwable t) {
            if (!type.isInstance(t)) {
                check(name, false, "期望 " + type.getSimpleName() + "，实际 " + t.getClass().getSimpleName());
                return;
            }
            if (messagePart != null && (t.getMessage() == null || !t.getMessage().contains(messagePart))) {
                check(name, false, "message 不含 [" + messagePart + "]，实际=[" + t.getMessage() + "]");
                return;
            }
            if (causeType != null && (t.getCause() == null || !causeType.isInstance(t.getCause()))) {
                check(name, false, "cause 期望 " + causeType.getSimpleName()
                        + "，实际 " + (t.getCause() == null ? "null" : t.getCause().getClass().getSimpleName()));
                return;
            }
            check(name, true, "");
        }
    }

    static void assertDoesNotThrow(String name, ThrowingCall call) {
        try {
            call.run();
            check(name, true, "");
        } catch (Throwable t) {
            check(name, false, "不该抛却抛了 " + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("=== 1. 参数非法：类型 + message 双重断言 ===");
        OrderService ok = new OrderService(new OrderRepo(false));
        assertThrows("orderNo=null -> NullPointerException", NullPointerException.class,
                "orderNo", null, () -> ok.create(null, 100L));
        assertThrows("orderNo 空白 -> IllegalArgumentException", IllegalArgumentException.class,
                "空白", null, () -> ok.create("   ", 100L));
        assertThrows("金额 0 -> IllegalArgumentException", IllegalArgumentException.class,
                "金额必须为正", null, () -> ok.create("ORD-1", 0L));
        assertThrows("金额 -1 -> IllegalArgumentException", IllegalArgumentException.class,
                "实际=-1", null, () -> ok.create("ORD-1", -1L));

        System.out.println();
        System.out.println("=== 2. 底层异常包装：保留 cause 才能排查 ===");
        OrderService broken = new OrderService(new OrderRepo(true));
        assertThrows("写入失败 -> OrderException 且 cause 是 DiskFullException",
                OrderException.class, "创建订单失败", DiskFullException.class,
                () -> broken.create("ORD-2", 100L));

        System.out.println();
        System.out.println("=== 3. 状态错误用 IllegalStateException ===");
        assertThrows("已支付订单再支付 -> IllegalStateException", IllegalStateException.class,
                "不允许支付", null, () -> ok.pay("PAID"));
        assertDoesNotThrow("CREATED 状态可以支付", () -> ok.pay("CREATED"));

        System.out.println();
        System.out.println("=== 4. 正常路径不该抛异常 ===");
        assertDoesNotThrow("合法订单创建成功", () -> ok.create("ORD-3", 9_900L));
        check("合法订单确实写入一次", ok.repo.writes() == 1, "writes=" + ok.repo.writes());

        System.out.println();
        System.out.println("=== 5. 吞异常的反面教材 ===");
        boolean swallowed;
        try {
            badCatch(() -> {
                throw new IllegalStateException("真实故障");
            });
            swallowed = true;
        } catch (Throwable t) {
            swallowed = false;
        }
        check("catch(Exception){} 会吞掉故障（所以测试永远绿）", swallowed,
                "异常竟然漏出来了");

        System.out.println();
        System.out.println("=== 6. 并发测试：同时起跑 + 断言不变式 ===");
        AtomicInteger counter = new AtomicInteger();
        int threads = 8;
        int perThread = 2000;
        int rounds = 3;
        int badRounds = 0;
        for (int round = 1; round <= rounds; round++) {
            counter.set(0);
            ExecutorService pool = Executors.newFixedThreadPool(threads);
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(threads);
            for (int t = 0; t < threads; t++) {
                pool.submit(() -> {
                    try {
                        start.await();
                        for (int i = 0; i < perThread; i++) {
                            counter.incrementAndGet();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            done.await(20, TimeUnit.SECONDS);
            pool.shutdownNow();
            int expect = threads * perThread;
            boolean okRound = counter.get() == expect;
            if (!okRound) {
                badRounds++;
            }
            System.out.printf("    第 %d 轮：期望 %d，实际 %d %s%n",
                    round, expect, counter.get(), okRound ? "OK" : "FAIL");
        }
        check("3 轮并发计数均无丢失", badRounds == 0, "异常轮数=" + badRounds);

        System.out.println();
        System.out.println("=== 7. 并发测试：非线程安全集合会暴露 ===");
        List<Integer> unsafe = new ArrayList<>();
        ExecutorService pool2 = Executors.newFixedThreadPool(8);
        CountDownLatch start2 = new CountDownLatch(1);
        CountDownLatch done2 = new CountDownLatch(8);
        for (int t = 0; t < 8; t++) {
            pool2.submit(() -> {
                try {
                    start2.await();
                    for (int i = 0; i < 500; i++) {
                        unsafe.add(i);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done2.countDown();
                }
            });
        }
        start2.countDown();
        done2.await(20, TimeUnit.SECONDS);
        pool2.shutdownNow();
        System.out.println("    ArrayList 期望 4000 个元素，实际 " + unsafe.size()
                + "（可能丢失，且可能抛 ArrayIndexOutOfBoundsException）");
        check("不变式：元素数不应超过期望", unsafe.size() <= 4000, "size=" + unsafe.size());

        System.out.println();
        System.out.println("=== 8. 异常契约设计清单 ===");
        String[] rules = {
                "参数非法 -> IllegalArgumentException / NullPointerException",
                "状态不允许 -> IllegalStateException",
                "可恢复业务失败 -> 返回值 Result，不要抛异常",
                "底层异常必须包装并保留 cause",
                "JavaDoc 写明 @throws，测试覆盖类型 + message + cause",
                "正常路径显式断言不抛异常",
                "并发测试：同时起跑 + 多轮 + 只断言不变式"
        };
        for (String r : rules) {
            System.out.println("    - " + r);
        }

        System.out.println();
        System.out.println("=== 结果 ===");
        System.out.println("  通过 " + passed + "，失败 " + failures.size());
        for (String f : failures) {
            System.out.println("    " + f);
        }
    }

    static void badCatch(Runnable r) {
        try {
            r.run();
        } catch (Exception e) {
            // 反面教材：吞掉异常，测试无法发现问题
        }
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 把 `badCatch` 改成"记录日志后重新抛出领域异常"，写出对应测试，验证 cause 链完整。
 * 2. 为 `create` 增加 `amountCents` 溢出校验（用 `Math.addExact` 累加明细），测试溢出时抛什么异常。
 * 3. 把并发计数改成 `int count++`（非原子），跑 10 轮统计有几轮丢数据，说明为什么"跑一次就绿"不可靠。
 * ============================================================
 */
