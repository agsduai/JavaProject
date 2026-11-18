/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：33-01
 * 课名：单元测试基础：从手写 main 到测试框架
 * ------------------------------------------------------------
 * 知识点：
 *   - 单元测试的定义：**针对最小可测单元（通常是一个方法）**，隔离外部依赖，快速、可重复、可自动判定成败。它和"写个 main 打印看看"的本质区别是**断言 + 自动汇总 + 可回归**。
 *   - 一个测试方法的四段结构（AAA）：**Arrange 准备数据 → Act 执行被测方法 → Assert 断言结果**，复杂用例再加 After 清理。
 *   - JUnit 5 的核心注解：`@Test`、`@BeforeEach`/`@AfterEach`（每个用例前后）、`@BeforeAll`/`@AfterAll`（整个类一次，必须 `static`）、`@DisplayName`、`@Disabled`、`@ParameterizedTest` + `@ValueSource`/`@CsvSource`。
 *   - 断言的正确姿势：**一个用例只验证一个行为**，失败信息要能自解释。`assertEquals(expected, actual)` 的参数顺序是"期望在前、实际在后"，写反了失败信息会误导人。
 *   - `assertThrows` 用来验证异常路径：不仅验证抛了异常，还要验证**异常类型**，必要时再验证 message。只写 `try/catch` 不断言等于没测。
 *   - 测试方法命名规范：`方法名_场景_期望结果`，例如 `discount_vipUser_returns90Percent`。名字本身就是文档。
 *   - 测试的**独立性**：用例之间不能有顺序依赖，不能共享可变状态。每个用例自己造数据，跑单个和跑全部结果必须一致。
 *   - 常见反模式：测试里 `Thread.sleep` 等异步（应改用轮询或注入时钟）、测试依赖真实数据库/网络、一个用例断言十几个不相关的东西、`System.out` 里人眼看结果。
 *   - 想真正"自动判定"，就需要一个**运行器**：扫描类里所有 `@Test` 方法 → 逐个实例化并调用 → 捕获断言失败 → 汇总通过/失败。JUnit 做的就是这件事，本课用纯 JDK 反射实现一个 60 行的迷你版。
 *   - 易错：`@BeforeAll` 写成非 `static`；测试方法写成 `private`（框架反射调用不到）；断言用 `==` 比较对象（比较的是引用）；捕获异常后忘了 `fail()`，导致异常没抛也判通过。
 * ============================================================
 */

package com.heima.ch33;

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

// ==================== 示例代码 ====================

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

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 给 `MiniRunner` 加上"超时"能力（每个用例最多跑 500ms，超时判失败），说明为什么单测里不该出现长 `sleep`。
 * 2. 增加一个 `@Disabled` 注解，被标记的用例跳过但计入"跳过数"，输出 `通过/失败/跳过` 三项统计。
 * 3. 把 `DiscountService` 的等级判断从 `if` 链改成 `switch`，确认测试全部仍通过——体会"有测试才敢重构"。
 * ============================================================
 */
