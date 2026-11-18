/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：33-02
 * 课名：测试替身：Stub、Mock、Spy、Fake
 * ------------------------------------------------------------
 * 知识点：
 *   - **测试替身（Test Double）** 是"用一个假对象替换真实依赖"，目的是让单测**快、稳、可控**。真实数据库、HTTP 调用、时间、随机数都是要替换的目标。
 *   - 五种替身：
 *   - **Dummy**：只为填参数，从不被使用（如 `null` 或空对象）。
 *   - **Stub**：返回**预设值**，不关心被调了几次。用于"喂数据"。
 *   - **Spy**：包住真实对象，记录调用、可部分打桩。用于"既想真跑又想观察"。
 *   - **Mock**：**预设期望**（被调几次、参数是什么），不符合就失败。用于"验证交互"。
 *   - **Fake**：有真实逻辑的简化实现（内存版 Repository、内存版消息队列）。
 *   - 选择原则：**优先 Fake 和 Stub，谨慎用 Mock**。Mock 断言的是"实现细节"（调了哪个方法几次），重构时容易大面积红；Stub 断言的是"结果"，更稳。
 *   - 状态验证 vs 交互验证：`assertEquals(订单状态, PAID)` 是状态验证；`verify(smsSender).send(...)` 是交互验证。业务核心用状态验证，外部副作用（发短信、扣款）用交互验证。
 *   - 手写替身的三种方式：接口实现一个假类、匿名内部类、JDK 动态代理（`Proxy.newProxyInstance` 拦截所有方法）。动态代理适合"接口方法很多、只想关注几个"的场景。
 *   - 依赖注入是替身的前提：被测类必须**通过构造器/方法参数接收依赖**，而不是自己 `new`。自己 `new` 的依赖无法替换，只能改代码。
 *   - 时间也是依赖：`System.currentTimeMillis()` 直接调用就无法测试"过期"逻辑，应注入 `Clock`（JDK 8+ 自带 `java.time.Clock`，测试用 `Clock.fixed`）。
 *   - 易错：Mock 了值对象（`new Money(100)` 也去 mock，毫无意义）；替身和真实实现行为不一致（Fake 的 bug 让测试假通过）；忘记验证"不该发生的调用"（如库存不足时**不应**调用支付）。
 * ============================================================
 */

package com.heima.ch33;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class TestDoubles {
    interface UserRepo {
        String findName(long userId);
    }

    interface SmsSender {
        void send(String phone, String text);
    }

    interface OrderRepo {
        void save(long orderId, String status);
    }

    interface PaymentGateway {
        boolean charge(long userId, long cents);
    }

    /** 被测类：构造器注入所有外部依赖，才能替换 */
    static class OrderService {
        private final UserRepo userRepo;
        private final SmsSender smsSender;
        private final OrderRepo orderRepo;
        private final PaymentGateway gateway;
        private final Clock clock;

        OrderService(UserRepo userRepo, SmsSender smsSender, OrderRepo orderRepo,
                     PaymentGateway gateway, Clock clock) {
            this.userRepo = userRepo;
            this.smsSender = smsSender;
            this.orderRepo = orderRepo;
            this.gateway = gateway;
            this.clock = clock;
        }

        String placeOrder(long userId, long orderId, long cents, long expireAtMillis) {
            String name = userRepo.findName(userId);
            if (name == null) {
                return "USER_NOT_FOUND";
            }
            if (clock.millis() > expireAtMillis) {
                return "EXPIRED";
            }
            if (!gateway.charge(userId, cents)) {
                orderRepo.save(orderId, "PAY_FAILED");
                return "PAY_FAILED";
            }
            orderRepo.save(orderId, "PAID");
            smsSender.send("138****" + userId, "尊敬的" + name + "，您的订单 " + orderId + " 已支付");
            return "PAID";
        }
    }

    /** Stub：只返回预设值 */
    static class StubUserRepo implements UserRepo {
        private final Map<Long, String> data = new HashMap<>();

        StubUserRepo put(long id, String name) {
            data.put(id, name);
            return this;
        }

        public String findName(long userId) {
            return data.get(userId);
        }
    }

    /** Fake：内存版仓储，有真实逻辑 */
    static class FakeOrderRepo implements OrderRepo {
        final Map<Long, String> store = new HashMap<>();

        public void save(long orderId, String status) {
            store.put(orderId, status);
        }
    }

    /** Mock：记录调用，可断言交互 */
    static class MockSmsSender implements SmsSender {
        final List<String> calls = new ArrayList<>();

        public void send(String phone, String text) {
            calls.add(phone + "|" + text);
        }

        int callCount() {
            return calls.size();
        }
    }

    /** Spy：包住真实对象，转发并记录 */
    static class SpyPaymentGateway implements PaymentGateway {
        private final PaymentGateway real;
        final List<Long> chargedCents = new ArrayList<>();

        SpyPaymentGateway(PaymentGateway real) {
            this.real = real;
        }

        public boolean charge(long userId, long cents) {
            chargedCents.add(cents);
            return real.charge(userId, cents);
        }
    }

    static class AssertionFailedError extends RuntimeException {
        private static final long serialVersionUID = 1L;

        AssertionFailedError(String message) {
            super(message);
        }
    }

    static void check(String caseName, String expected, String actual) {
        boolean ok = expected.equals(actual);
        System.out.printf("    %-42s 期望=%-14s 实际=%-14s %s%n",
                caseName, expected, actual, ok ? "OK" : "FAIL");
        if (!ok) {
            throw new AssertionFailedError(caseName);
        }
    }

    static void checkTrue(String caseName, boolean condition) {
        System.out.printf("    %-42s %s%n", caseName, condition ? "OK" : "FAIL");
        if (!condition) {
            throw new AssertionFailedError(caseName);
        }
    }

    public static void main(String[] args) {
        Clock fixed = Clock.fixed(Instant.ofEpochMilli(1_000_000L), ZoneId.of("Asia/Shanghai"));

        System.out.println("=== 1. Stub + Fake + Mock：正常支付成功 ===");
        StubUserRepo userRepo = new StubUserRepo().put(7L, "张三");
        FakeOrderRepo orderRepo = new FakeOrderRepo();
        MockSmsSender sms = new MockSmsSender();
        SpyPaymentGateway pay = new SpyPaymentGateway((uid, cents) -> cents <= 50_000L);

        OrderService svc = new OrderService(userRepo, sms, orderRepo, pay, fixed);
        String r1 = svc.placeOrder(7L, 1001L, 9_900L, 2_000_000L);
        check("返回值", "PAID", r1);
        check("订单状态（状态验证）", "PAID", orderRepo.store.get(1001L));
        check("短信发送次数（交互验证）", "1", String.valueOf(sms.callCount()));
        checkTrue("短信内容含用户姓名", sms.calls.get(0).contains("张三"));
        check("扣款金额被 Spy 记录", "9900", String.valueOf(pay.chargedCents.get(0)));

        System.out.println();
        System.out.println("=== 2. 用户不存在：不应扣款、不应发短信 ===");
        MockSmsSender sms2 = new MockSmsSender();
        FakeOrderRepo repo2 = new FakeOrderRepo();
        SpyPaymentGateway pay2 = new SpyPaymentGateway((uid, cents) -> true);
        OrderService svc2 = new OrderService(new StubUserRepo(), sms2, repo2, pay2, fixed);
        check("返回值", "USER_NOT_FOUND", svc2.placeOrder(99L, 1002L, 100L, 2_000_000L));
        check("未调用扣款", "0", String.valueOf(pay2.chargedCents.size()));
        check("未发送短信", "0", String.valueOf(sms2.callCount()));
        checkTrue("未落库任何订单", repo2.store.isEmpty());

        System.out.println();
        System.out.println("=== 3. 已过期：同样不应扣款 ===");
        SpyPaymentGateway pay3 = new SpyPaymentGateway((uid, cents) -> true);
        OrderService svc3 = new OrderService(new StubUserRepo().put(1L, "李四"),
                new MockSmsSender(), new FakeOrderRepo(), pay3, fixed);
        check("返回值", "EXPIRED", svc3.placeOrder(1L, 1003L, 100L, 500_000L));
        check("未调用扣款", "0", String.valueOf(pay3.chargedCents.size()));

        System.out.println();
        System.out.println("=== 4. 支付失败：状态为 PAY_FAILED，不发短信 ===");
        MockSmsSender sms4 = new MockSmsSender();
        FakeOrderRepo repo4 = new FakeOrderRepo();
        OrderService svc4 = new OrderService(new StubUserRepo().put(2L, "王五"), sms4, repo4,
                new SpyPaymentGateway((uid, cents) -> false), fixed);
        check("返回值", "PAY_FAILED", svc4.placeOrder(2L, 1004L, 100_000L, 2_000_000L));
        check("订单状态", "PAY_FAILED", repo4.store.get(1004L));
        check("不发成功短信", "0", String.valueOf(sms4.callCount()));

        System.out.println();
        System.out.println("=== 5. 动态代理做通用 Spy（不改被测代码）===");
        List<String> log = new ArrayList<>();
        UserRepo proxied = (UserRepo) Proxy.newProxyInstance(
                UserRepo.class.getClassLoader(),
                new Class<?>[]{UserRepo.class},
                new InvocationHandler() {
                    public Object invoke(Object proxy, Method method, Object[] a) {
                        log.add(method.getName() + "(" + a[0] + ")");
                        return method.getName().equals("findName") && (Long) a[0] == 42L ? "赵六" : null;
                    }
                });
        OrderService svc5 = new OrderService(proxied, new MockSmsSender(), new FakeOrderRepo(),
                (uid, cents) -> true, fixed);
        check("代理返回预设值", "PAID", svc5.placeOrder(42L, 1005L, 1L, 2_000_000L));
        check("代理记录到调用", "findName(42)", String.join(",", log));

        System.out.println();
        System.out.println("=== 6. 五种替身对照 ===");
        String[][] t = {
                {"Dummy", "只为填参数，永不被调用", "null / 空实现"},
                {"Stub", "返回预设值", "StubUserRepo"},
                {"Spy", "转发真实调用并记录", "SpyPaymentGateway"},
                {"Mock", "预设期望，验证交互次数与参数", "MockSmsSender"},
                {"Fake", "有真实逻辑的简化实现", "FakeOrderRepo"}
        };
        for (String[] r : t) {
            System.out.printf("  %-8s %-34s %s%n", r[0], r[1], r[2]);
        }
        System.out.println();
        System.out.println("  选型建议：结果用 Stub/Fake 验证，副作用用 Mock 验证；");
        System.out.println("  值对象（Money、OrderId）不要 mock，直接 new。");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 用 `Clock.fixed` 与 `Clock.offset` 写两个用例，分别覆盖"刚好过期"（`now == expireAt`）与"过期前一毫秒"。
 * 2. 给 `MockSmsSender` 加"断言从未调用"的方法，并在用户不存在、支付失败两个用例里调用它。
 * 3. 解释为什么"Mock 了 `List` 去验证 `add` 被调用一次"是坏味道，给出改用 Fake 的写法。
 * ============================================================
 */
