### 33-01 单元测试基础：从手写 main 到测试框架

**知识点**
- 单元测试的定义：**针对最小可测单元（通常是一个方法）**，隔离外部依赖，快速、可重复、可自动判定成败。它和"写个 main 打印看看"的本质区别是**断言 + 自动汇总 + 可回归**。
- 一个测试方法的四段结构（AAA）：**Arrange 准备数据 → Act 执行被测方法 → Assert 断言结果**，复杂用例再加 After 清理。
- JUnit 5 的核心注解：`@Test`、`@BeforeEach`/`@AfterEach`（每个用例前后）、`@BeforeAll`/`@AfterAll`（整个类一次，必须 `static`）、`@DisplayName`、`@Disabled`、`@ParameterizedTest` + `@ValueSource`/`@CsvSource`。
- 断言的正确姿势：**一个用例只验证一个行为**，失败信息要能自解释。`assertEquals(expected, actual)` 的参数顺序是"期望在前、实际在后"，写反了失败信息会误导人。
- `assertThrows` 用来验证异常路径：不仅验证抛了异常，还要验证**异常类型**，必要时再验证 message。只写 `try/catch` 不断言等于没测。
- 测试方法命名规范：`方法名_场景_期望结果`，例如 `discount_vipUser_returns90Percent`。名字本身就是文档。
- 测试的**独立性**：用例之间不能有顺序依赖，不能共享可变状态。每个用例自己造数据，跑单个和跑全部结果必须一致。
- 常见反模式：测试里 `Thread.sleep` 等异步（应改用轮询或注入时钟）、测试依赖真实数据库/网络、一个用例断言十几个不相关的东西、`System.out` 里人眼看结果。
- 想真正"自动判定"，就需要一个**运行器**：扫描类里所有 `@Test` 方法 → 逐个实例化并调用 → 捕获断言失败 → 汇总通过/失败。JUnit 做的就是这件事，本课用纯 JDK 反射实现一个 60 行的迷你版。
- 易错：`@BeforeAll` 写成非 `static`；测试方法写成 `private`（框架反射调用不到）；断言用 `==` 比较对象（比较的是引用）；捕获异常后忘了 `fail()`，导致异常没抛也判通过。

**示例代码**
```java
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class UnitTestBasics {
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface Test {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface BeforeEach {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface AfterEach {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface DisplayName {
        String value();
    }

    static class AssertionFailedError extends RuntimeException {
        private static final long serialVersionUID = 1L;

        AssertionFailedError(String message) {
            super(message);
        }
    }

    static class Assert {
        static void assertEquals(long expected, long actual) {
            if (expected != actual) {
                throw new AssertionFailedError("期望 " + expected + "，实际 " + actual);
            }
        }

        static void assertEquals(Object expected, Object actual) {
            if (expected == null ? actual != null : !expected.equals(actual)) {
                throw new AssertionFailedError("期望 [" + expected + "]，实际 [" + actual + "]");
            }
        }

        static void assertTrue(boolean condition, String message) {
            if (!condition) {
                throw new AssertionFailedError(message);
            }
        }

        static void assertThrows(Class<? extends Throwable> expected, Runnable action) {
            try {
                action.run();
            } catch (Throwable t) {
                if (expected.isInstance(t)) {
                    return;
                }
                throw new AssertionFailedError("期望抛 " + expected.getSimpleName()
                        + "，实际抛 " + t.getClass().getSimpleName());
            }
            throw new AssertionFailedError("期望抛 " + expected.getSimpleName() + "，但什么都没抛");
        }
    }

    /** 迷你测试运行器：反射扫描 + 生命周期回调 + 结果汇总 */
    static class MiniRunner {
        static class Report {
            int passed;
            final List<String> failures = new ArrayList<>();

            boolean allGreen() {
                return failures.isEmpty();
            }
        }

        static Report run(Class<?> testClass) {
            Report report = new Report();
            List<Method> tests = new ArrayList<>();
            List<Method> beforeEach = new ArrayList<>();
            List<Method> afterEach = new ArrayList<>();
            for (Method m : testClass.getDeclaredMethods()) {
                if (m.isAnnotationPresent(Test.class)) {
                    tests.add(m);
                }
                if (m.isAnnotationPresent(BeforeEach.class)) {
                    beforeEach.add(m);
                }
                if (m.isAnnotationPresent(AfterEach.class)) {
                    afterEach.add(m);
                }
            }
            tests.sort(Comparator.comparing(Method::getName));
            System.out.println("  发现 " + tests.size() + " 个测试方法");
            for (Method test : tests) {
                String name = test.isAnnotationPresent(DisplayName.class)
                        ? test.getAnnotation(DisplayName.class).value()
                        : test.getName();
                try {
                    Object instance = newInstance(testClass);
                    for (Method b : beforeEach) {
                        invoke(b, instance);
                    }
                    try {
                        invoke(test, instance);
                    } finally {
                        for (Method a : afterEach) {
                            invoke(a, instance);
                        }
                    }
                    report.passed++;
                    System.out.println("    [PASS] " + name);
                } catch (Throwable t) {
                    Throwable root = unwrap(t);
                    report.failures.add(name + " -> " + root.getMessage());
                    System.out.println("    [FAIL] " + name + " -> " + root.getMessage());
                }
            }
            return report;
        }

        static Object newInstance(Class<?> c) throws Exception {
            Constructor<?> ctor = c.getDeclaredConstructor();
            ctor.setAccessible(true);
            return ctor.newInstance();
        }

        static void invoke(Method m, Object target) throws Exception {
            m.setAccessible(true);
            try {
                m.invoke(target);
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause();
                if (cause instanceof Exception) {
                    throw (Exception) cause;
                }
                throw new RuntimeException(cause);
            }
        }

        static Throwable unwrap(Throwable t) {
            return t.getCause() != null && t.getCause() != t ? unwrap(t.getCause()) : t;
        }
    }

    /** 被测类：订单折扣计算 */
    static class DiscountService {
        long discountPercent(int level, long amountCents) {
            if (amountCents < 0) {
                throw new IllegalArgumentException("金额不能为负: " + amountCents);
            }
            if (level <= 0) {
                return 100;
            }
            if (level == 1) {
                return 95;
            }
            if (level == 2) {
                return 90;
            }
            return amountCents >= 100_000 ? 80 : 85;
        }

        long finalPrice(int level, long amountCents) {
            return amountCents * discountPercent(level, amountCents) / 100;
        }
    }

    /** 测试类：每个用例自己造 SUT，互不影响 */
    static class DiscountServiceTest {
        private DiscountService service;
        private int setupCount;

        @BeforeEach
        void setUp() {
            service = new DiscountService();
            setupCount++;
        }

        @AfterEach
        void tearDown() {
            service = null;
        }

        @Test
        @DisplayName("普通用户不打折")
        void level0_returnsOriginalPrice() {
            Assert.assertEquals(10_000L, service.finalPrice(0, 10_000L));
        }

        @Test
        @DisplayName("VIP1 打 95 折")
        void level1_returns95Percent() {
            Assert.assertEquals(9_500L, service.finalPrice(1, 10_000L));
        }

        @Test
        @DisplayName("VIP3 大额（满 1000 元）打 8 折")
        void level3_bigAmount_returns80Percent() {
            Assert.assertEquals(80_000L, service.finalPrice(3, 100_000L));
        }

        @Test
        @DisplayName("负数金额抛 IllegalArgumentException")
        void negativeAmount_throwsIllegalArgument() {
            Assert.assertThrows(IllegalArgumentException.class, () -> service.finalPrice(1, -1L));
        }

        @Test
        @DisplayName("故意写错的用例：演示断言失败长什么样")
        void intentionallyWrong_demonstratesFailure() {
            Assert.assertEquals(9_999L, service.finalPrice(1, 10_000L));
        }

        @Test
        @DisplayName("前置方法每个用例都执行一次")
        void setupRunsPerTest() {
            Assert.assertEquals(1, setupCount);
            Assert.assertTrue(service != null, "service 应已初始化");
        }
    }

    public static void main(String[] args) {
        System.out.println("=== 1. 运行测试类 ===");
        MiniRunner.Report report = MiniRunner.run(DiscountServiceTest.class);

        System.out.println();
        System.out.println("=== 2. 汇总 ===");
        System.out.println("  通过 " + report.passed + "，失败 " + report.failures.size());
        for (String f : report.failures) {
            System.out.println("    失败用例: " + f);
        }
        System.out.println("  整体结果: " + (report.allGreen() ? "GREEN" : "RED"));

        System.out.println();
        System.out.println("=== 3. 断言顺序的重要性 ===");
        try {
            Assert.assertEquals(9_999L, 10_000L);
        } catch (AssertionFailedError e) {
            System.out.println("  正确顺序(期望在前) -> " + e.getMessage());
        }
        try {
            Assert.assertEquals(10_000L, 9_999L);
        } catch (AssertionFailedError e) {
            System.out.println("  写反顺序(实际在前) -> " + e.getMessage() + "（读起来像期望 10000 却得到 9999，容易误判）");
        }

        System.out.println();
        System.out.println("=== 4. JUnit 5 注解对照 ===");
        String[][] t = {
                {"@Test", "标记测试方法", "本课 @Test"},
                {"@BeforeEach", "每个用例前执行", "本课 @BeforeEach"},
                {"@AfterEach", "每个用例后执行", "本课 @AfterEach"},
                {"@BeforeAll", "整个类一次，必须 static", "示例省略"},
                {"@DisplayName", "可读的用例名", "本课 @DisplayName"},
                {"@ParameterizedTest", "参数化，一份逻辑多组数据", "见 33-03"},
                {"@Disabled", "临时跳过", "示例省略"},
                {"@Tag", "分组，按标签跑", "示例省略"}
        };
        for (String[] r : t) {
            System.out.printf("  %-22s %-26s %s%n", r[0], r[1], r[2]);
        }
    }
}
```

**课后练习**
1. 给 `MiniRunner` 加上"超时"能力（每个用例最多跑 500ms，超时判失败），说明为什么单测里不该出现长 `sleep`。
2. 增加一个 `@Disabled` 注解，被标记的用例跳过但计入"跳过数"，输出 `通过/失败/跳过` 三项统计。
3. 把 `DiscountService` 的等级判断从 `if` 链改成 `switch`，确认测试全部仍通过——体会"有测试才敢重构"。

### 33-02 测试替身：Stub、Mock、Spy、Fake

**知识点**
- **测试替身（Test Double）** 是"用一个假对象替换真实依赖"，目的是让单测**快、稳、可控**。真实数据库、HTTP 调用、时间、随机数都是要替换的目标。
- 五种替身：
  - **Dummy**：只为填参数，从不被使用（如 `null` 或空对象）。
  - **Stub**：返回**预设值**，不关心被调了几次。用于"喂数据"。
  - **Spy**：包住真实对象，记录调用、可部分打桩。用于"既想真跑又想观察"。
  - **Mock**：**预设期望**（被调几次、参数是什么），不符合就失败。用于"验证交互"。
  - **Fake**：有真实逻辑的简化实现（内存版 Repository、内存版消息队列）。
- 选择原则：**优先 Fake 和 Stub，谨慎用 Mock**。Mock 断言的是"实现细节"（调了哪个方法几次），重构时容易大面积红；Stub 断言的是"结果"，更稳。
- 状态验证 vs 交互验证：`assertEquals(订单状态, PAID)` 是状态验证；`verify(smsSender).send(...)` 是交互验证。业务核心用状态验证，外部副作用（发短信、扣款）用交互验证。
- 手写替身的三种方式：接口实现一个假类、匿名内部类、JDK 动态代理（`Proxy.newProxyInstance` 拦截所有方法）。动态代理适合"接口方法很多、只想关注几个"的场景。
- 依赖注入是替身的前提：被测类必须**通过构造器/方法参数接收依赖**，而不是自己 `new`。自己 `new` 的依赖无法替换，只能改代码。
- 时间也是依赖：`System.currentTimeMillis()` 直接调用就无法测试"过期"逻辑，应注入 `Clock`（JDK 8+ 自带 `java.time.Clock`，测试用 `Clock.fixed`）。
- 易错：Mock 了值对象（`new Money(100)` 也去 mock，毫无意义）；替身和真实实现行为不一致（Fake 的 bug 让测试假通过）；忘记验证"不该发生的调用"（如库存不足时**不应**调用支付）。

**示例代码**
```java
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
```

**课后练习**
1. 用 `Clock.fixed` 与 `Clock.offset` 写两个用例，分别覆盖"刚好过期"（`now == expireAt`）与"过期前一毫秒"。
2. 给 `MockSmsSender` 加"断言从未调用"的方法，并在用户不存在、支付失败两个用例里调用它。
3. 解释为什么"Mock 了 `List` 去验证 `add` 被调用一次"是坏味道，给出改用 Fake 的写法。

### 33-03 测试用例设计：等价类、边界值与参数化

**知识点**
- 测试用例的价值不在于数量，而在于**用最少用例覆盖最多风险**。核心方法：等价类划分、边界值分析、判定表、状态迁移、错误推测。
- **等价类划分**：把输入域切成若干类，每类里任取一个值效果相同。有效等价类（正常输入）+ 无效等价类（非法输入）。**无效等价类必须逐个单独测**，因为一个非法值可能掩盖另一个。
- **边界值分析**：bug 最爱藏在边界。对区间 `[a, b]`，至少测 `a-1, a, a+1, b-1, b, b+1` 六个点。off-by-one 是最高频缺陷。
- **参数化测试**：一份逻辑 + 一张数据表。JUnit 5 用 `@ParameterizedTest` + `@ValueSource`（单值）、`@CsvSource`（多列）、`@MethodSource`（复杂对象）。手写版就是"数据表 + for 循环"。
- **判定表**：多个条件组合时，列出所有条件组合与期望动作。适合"会员等级 × 是否首单 × 是否有券"这类规则叠加。
- **状态迁移测试**：把业务状态机画出来，覆盖"合法迁移"和"非法迁移"两类。非法迁移（如已取消订单再支付）往往比合法路径更容易漏测。
- **错误推测**：基于经验主动想"哪里会错"——空集合、`null`、超长字符串、并发、时区、闰年、金额精度、重复提交。
- 用例设计产出物是**用例表**（输入 / 前置条件 / 期望），先写表再写代码，避免"看着实现写测试"（那样只会覆盖已实现的路径，漏掉没实现的需求）。
- 覆盖强度阶梯：语句覆盖 < 判定覆盖 < 条件组合覆盖 < 路径覆盖。实际项目做到"判定 + 边界"通常足够，关键资金链路再上路径覆盖。
- 易错：只测正常值（happy path）不测异常；用 `Random` 造数据（失败无法复现）；把多个无效等价类塞进一个用例；只测 `[0,100]` 里的 50，不测 -1、0、100、101。

**示例代码**
```java
import java.util.ArrayList;
import java.util.List;

public class TestDesignCases {
    /** 被测：优惠券可用性校验 */
    static class CouponValidator {
        /** @return 不可用原因，可用时返回 "OK" */
        String validate(Integer amountCents, Integer thresholdCents, Boolean firstOrder,
                        Boolean member, String couponType) {
            if (amountCents == null || thresholdCents == null) {
                return "PARAM_NULL";
            }
            if (couponType == null) {
                return "TYPE_UNKNOWN";
            }
            if (amountCents < 0) {
                return "AMOUNT_NEGATIVE";
            }
            if (thresholdCents < 0) {
                return "THRESHOLD_NEGATIVE";
            }
            if (amountCents < thresholdCents) {
                return "BELOW_THRESHOLD";
            }
            switch (couponType) {
                case "CASH":
                    return "OK";
                case "NEW_USER":
                    return Boolean.TRUE.equals(firstOrder) ? "OK" : "NOT_FIRST_ORDER";
                case "MEMBER_ONLY":
                    return Boolean.TRUE.equals(member) ? "OK" : "NOT_MEMBER";
                default:
                    return "TYPE_UNKNOWN";
            }
        }
    }

    /** 迷你参数化：一行数据一个用例 */
    static class Case {
        final String desc;
        final Integer amount;
        final Integer threshold;
        final Boolean firstOrder;
        final Boolean member;
        final String type;
        final String expected;

        Case(String desc, Integer amount, Integer threshold, Boolean firstOrder,
             Boolean member, String type, String expected) {
            this.desc = desc;
            this.amount = amount;
            this.threshold = threshold;
            this.firstOrder = firstOrder;
            this.member = member;
            this.type = type;
            this.expected = expected;
        }
    }

    static int passed;
    static final List<String> failed = new ArrayList<>();

    static void runAll(String group, CouponValidator v, List<Case> cases) {
        System.out.println("  " + group);
        for (Case c : cases) {
            String actual = v.validate(c.amount, c.threshold, c.firstOrder, c.member, c.type);
            boolean ok = c.expected.equals(actual);
            if (ok) {
                passed++;
            } else {
                failed.add(c.desc + " 期望=" + c.expected + " 实际=" + actual);
            }
            System.out.printf("    %-4s %-34s 期望=%-16s 实际=%s%n",
                    ok ? "OK" : "FAIL", c.desc, c.expected, actual);
        }
    }

    static Case c(String desc, Integer amount, Integer threshold, Boolean first, Boolean member,
                  String type, String expected) {
        return new Case(desc, amount, threshold, first, member, type, expected);
    }

    public static void main(String[] args) {
        CouponValidator v = new CouponValidator();

        System.out.println("=== 1. 边界值：门槛 10000 分（100 元）===");
        List<Case> boundary = new ArrayList<>();
        boundary.add(c("9999 分（门槛-1）", 9999, 10000, false, false, "CASH", "BELOW_THRESHOLD"));
        boundary.add(c("10000 分（刚好达标）", 10000, 10000, false, false, "CASH", "OK"));
        boundary.add(c("10001 分（门槛+1）", 10001, 10000, false, false, "CASH", "OK"));
        boundary.add(c("0 分（下边界）", 0, 0, false, false, "CASH", "OK"));
        boundary.add(c("-1 分（下边界-1）", -1, 0, false, false, "CASH", "AMOUNT_NEGATIVE"));
        runAll("边界值用例（6 个点）", v, boundary);

        System.out.println();
        System.out.println("=== 2. 无效等价类：每个非法输入单独一个用例 ===");
        List<Case> invalid = new ArrayList<>();
        invalid.add(c("金额为 null", null, 100, false, false, "CASH", "PARAM_NULL"));
        invalid.add(c("门槛为 null", 100, null, false, false, "CASH", "PARAM_NULL"));
        invalid.add(c("券类型为 null", 100, 100, false, false, null, "TYPE_UNKNOWN"));
        invalid.add(c("券类型未知字符串", 100, 100, false, false, "FOO", "TYPE_UNKNOWN"));
        invalid.add(c("门槛为负", 100, -1, false, false, "CASH", "THRESHOLD_NEGATIVE"));
        invalid.add(c("金额为负", -1, 0, false, false, "CASH", "AMOUNT_NEGATIVE"));
        runAll("无效等价类（6 个独立用例）", v, invalid);

        System.out.println();
        System.out.println("=== 3. 判定表：首单券 × 会员券 的条件组合 ===");
        List<Case> table = new ArrayList<>();
        table.add(c("NEW_USER + 首单=true", 20000, 10000, true, false, "NEW_USER", "OK"));
        table.add(c("NEW_USER + 首单=false", 20000, 10000, false, false, "NEW_USER", "NOT_FIRST_ORDER"));
        table.add(c("NEW_USER + 首单=null", 20000, 10000, null, false, "NEW_USER", "NOT_FIRST_ORDER"));
        table.add(c("MEMBER_ONLY + 会员=true", 20000, 10000, false, true, "MEMBER_ONLY", "OK"));
        table.add(c("MEMBER_ONLY + 会员=false", 20000, 10000, false, false, "MEMBER_ONLY", "NOT_MEMBER"));
        table.add(c("MEMBER_ONLY + 会员=null", 20000, 10000, false, null, "MEMBER_ONLY", "NOT_MEMBER"));
        table.add(c("会员券但金额不达标", 5000, 10000, false, true, "MEMBER_ONLY", "BELOW_THRESHOLD"));
        runAll("判定表用例（条件组合优先于单条件）", v, table);

        System.out.println();
        System.out.println("=== 4. 只测 happy path 会漏掉多少 ===");
        int happyOnly = 1;
        int total = boundary.size() + invalid.size() + table.size();
        System.out.println("  只测 1 个正常用例 -> 覆盖 1 条路径");
        System.out.println("  按本课方法设计   -> " + total + " 个用例，覆盖 "
                + (total + 1) + " 条路径（含 OK 分支）");
        System.out.println("  漏测成本：金额为负、门槛为负、null 入参、类型未知、"
                + "首单/会员为 null 全部不会被发现");

        System.out.println();
        System.out.println("=== 5. 用例设计检查清单 ===");
        String[] checklist = {
                "每个区间是否测了 a-1 / a / a+1 / b-1 / b / b+1",
                "每个引用类型是否测了 null",
                "集合是否测了空集合与单元素",
                "字符串是否测了空串、超长、特殊字符",
                "金额是否用整数分而不是 double",
                "枚举/字符串类型是否测了未知值",
                "布尔条件是否测了 null（Boolean 而非 boolean）",
                "非法迁移（已取消再支付）是否覆盖",
                "并发与重复提交是否单独验证",
                "每条用例失败信息能否自解释"
        };
        for (int i = 0; i < checklist.length; i++) {
            System.out.printf("  %2d. %s%n", i + 1, checklist[i]);
        }

        System.out.println();
        System.out.println("=== 结果 ===");
        System.out.println("  通过 " + passed + "，失败 " + failed.size());
        for (String f : failed) {
            System.out.println("    " + f);
        }
    }
}
```

**课后练习**
1. 为 `CouponValidator` 补充"满减券阶梯门槛"（如 100-10、200-30），先用判定表列用例再写代码。
2. 找出 `validate` 里一个**边界值方法测不出、但路径覆盖能测出**的分支，说明原因。
3. 把 `amountCents` 改成 `double amount`，指出会产生什么类型的测试问题，并说明为什么金额必须用整数分。
