### 33-04 覆盖率：行覆盖、分支覆盖与未覆盖分支分析

**知识点**
- **覆盖率**回答"测试跑了多少代码"，是**发现测试盲区**的探针，不是质量目标。追 100% 覆盖率会写出大量"执行了但没断言"的空测试。
- 四种粒度：
  - **行覆盖**（Line）：某行被执行过。最弱，`if (a && b)` 一行也算覆盖。
  - **分支覆盖**（Branch）：每个 `if`/`switch`/三元/`&&`/`||` 的两个方向都走到。比行覆盖有意义得多。
  - **条件覆盖**：复合条件里每个原子条件取过 true/false。
  - **路径覆盖**：方法内所有执行路径组合。分支多时路径爆炸，实际少用。
- JaCoCo 的原理：**字节码插桩**——在 class 文件里每个分支跳转前插入探针（记录 boolean 数组），运行结束后 dump 出 `.exec` 文件，再结合源码生成报告。它不修改源码。
- 覆盖率报告要看的不是总数，而是**红色（未覆盖）的那几行**：往往是异常分支、`null` 判断、`default` 分支、边界 `else`。这些恰是最容易出 bug 的地方。
- 覆盖率的合理区间：核心业务（资金、库存、权限）分支覆盖 80% 以上；DTO/getter/setter、配置类不必强求；生成的代码排除。
- **覆盖率不等于断言**：`service.calculate()` 被调用就算覆盖，但不断言结果，测试毫无价值。变异测试（PIT）才能验证"断言是否有效"——故意改坏代码，看测试会不会红。
- 未覆盖分支的常见成因：只写 happy path、`catch` 块无法触发（依赖抛异常）、防御性 `null` 检查、`default` 兜底分支、并发分支。
- 纯 JDK 无法直接读字节码覆盖率，本课用**显式埋点 + 分支表**模拟 JaCoCo 的统计思路：业务代码在每个分支处打点，测试跑完后统计"哪些点没被命中"。
- 易错：把覆盖率当 KPI 导致写"调用即通过"的测试；只统计行覆盖不看分支；忽略 `catch` 分支；覆盖率高但断言缺失（变异测试会暴露）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CoverageMetrics {
    /** 探针：模拟 JaCoCo 插入的 boolean 数组 */
    static class Probe {
        static final Set<String> HIT = new LinkedHashSet<>();
        static final Set<String> ALL = new LinkedHashSet<>();

        static void declare(String id) {
            ALL.add(id);
        }

        static void hit(String id) {
            HIT.add(id);
        }

        static void reset() {
            HIT.clear();
        }

        static double rate() {
            return ALL.isEmpty() ? 1.0 : (double) HIT.size() / ALL.size();
        }
    }

    /** 被测：运费计算，每个分支埋一个探针 */
    static class ShippingCalculator {
        static {
            Probe.declare("B1_weight<=0");
            Probe.declare("B2_weight<=1");
            Probe.declare("B3_weight<=5");
            Probe.declare("B4_remote");
            Probe.declare("B5_vip");
            Probe.declare("B6_freeThreshold");
            Probe.declare("B7_negativeAmount");
        }

        /** @return 运费（分） */
        long calc(int weightGram, boolean remote, boolean vip, long orderCents) {
            if (weightGram <= 0) {
                Probe.hit("B1_weight<=0");
                throw new IllegalArgumentException("重量必须为正: " + weightGram);
            }
            long base;
            if (weightGram <= 1000) {
                Probe.hit("B2_weight<=1");
                base = 800;
            } else if (weightGram <= 5000) {
                Probe.hit("B3_weight<=5");
                base = 800 + (weightGram - 1000) / 500 * 200;
            } else {
                base = 800 + 8 * 200 + (weightGram - 5000) / 1000 * 300;
            }
            if (remote) {
                Probe.hit("B4_remote");
                base += 1000;
            }
            if (vip) {
                Probe.hit("B5_vip");
                base = base * 80 / 100;
            }
            if (orderCents >= 9_900L) {
                Probe.hit("B6_freeThreshold");
                return 0;
            }
            if (orderCents < 0) {
                Probe.hit("B7_negativeAmount");
                throw new IllegalStateException("订单金额为负");
            }
            return base;
        }
    }

    static class Case {
        final String desc;
        final int weight;
        final boolean remote;
        final boolean vip;
        final long order;
        final boolean expectThrow;

        Case(String desc, int weight, boolean remote, boolean vip, long order, boolean expectThrow) {
            this.desc = desc;
            this.weight = weight;
            this.remote = remote;
            this.vip = vip;
            this.order = order;
            this.expectThrow = expectThrow;
        }
    }

    static long lastResult;

    static void run(ShippingCalculator c, Case k) {
        try {
            lastResult = c.calc(k.weight, k.remote, k.vip, k.order);
            if (k.expectThrow) {
                System.out.printf("    %-30s 期望抛异常但没抛 -> FAIL%n", k.desc);
            } else {
                System.out.printf("    %-30s 运费=%-6d OK%n", k.desc, lastResult);
            }
        } catch (RuntimeException e) {
            if (k.expectThrow) {
                System.out.printf("    %-30s 抛出 %-22s OK%n", k.desc, e.getClass().getSimpleName());
            } else {
                System.out.printf("    %-30s 意外异常 %s -> FAIL%n", k.desc, e.getMessage());
            }
        }
    }

    static void report(String title) {
        System.out.println("  " + title);
        System.out.printf("    分支覆盖率 = %d/%d = %.0f%%%n",
                Probe.HIT.size(), Probe.ALL.size(), Probe.rate() * 100);
        List<String> missed = new ArrayList<>(Probe.ALL);
        missed.removeAll(Probe.HIT);
        if (missed.isEmpty()) {
            System.out.println("    未覆盖分支: 无");
        } else {
            System.out.println("    未覆盖分支: " + missed);
        }
    }

    public static void main(String[] args) {
        ShippingCalculator calc = new ShippingCalculator();

        System.out.println("=== 1. 第一轮：只有 happy path（典型的低质量测试）===");
        Probe.reset();
        List<Case> round1 = new ArrayList<>();
        round1.add(new Case("1kg 普通 订单 50 元", 1000, false, false, 5_000, false));
        round1.add(new Case("3kg 普通 订单 50 元", 3_000, false, false, 5_000, false));
        for (Case k : round1) {
            run(calc, k);
        }
        report("第一轮结果");

        System.out.println();
        System.out.println("=== 2. 第二轮：补齐边界与异常分支 ===");
        Probe.reset();
        List<Case> round2 = new ArrayList<>();
        round2.add(new Case("1kg 普通", 1_000, false, false, 5_000, false));
        round2.add(new Case("1kg 偏远", 1_000, true, false, 5_000, false));
        round2.add(new Case("1kg VIP", 1_000, false, true, 5_000, false));
        round2.add(new Case("满 99 免运费", 1_000, false, false, 9_900, false));
        round2.add(new Case("0g 非法", 0, false, false, 5_000, true));
        round2.add(new Case("负金额", 1_000, false, false, -1, true));
        for (Case k : round2) {
            run(calc, k);
        }
        report("第二轮结果");

        System.out.println();
        System.out.println("=== 3. 两轮对比 ===");
        System.out.println("  第一轮 2 个用例：只覆盖常规路径，异常与优惠分支全漏");
        System.out.println("  第二轮 6 个用例：分支覆盖补齐到 100%");
        System.out.println("  关键：第二轮多出来的 4 个用例，正好是 4 个高风险分支");

        System.out.println();
        System.out.println("=== 4. 覆盖率的四种粒度 ===");
        String[][] t = {
                {"行覆盖", "该行是否执行过", "最弱，复合条件一行算一个"},
                {"分支覆盖", "if/switch/&&/|| 两方向", "性价比最高，项目基线"},
                {"条件覆盖", "复合条件内每个原子条件", "适合复杂布尔表达式"},
                {"路径覆盖", "所有路径组合", "分支多时爆炸，慎用"},
                {"变异覆盖", "改坏代码测试是否会红", "验证断言有效性（PIT）"}
        };
        for (String[] r : t) {
            System.out.printf("  %-12s %-28s %s%n", r[0], r[1], r[2]);
        }

        System.out.println();
        System.out.println("=== 5. 覆盖率报告该看什么 ===");
        String[] points = {
                "看红色行，不看总百分比",
                "catch 块是否被触发过（异常路径最易漏）",
                "default / else 兜底分支是否覆盖",
                "null 判断分支是否两边都走到",
                "覆盖率涨了但断言没加 -> 无效覆盖",
                "DTO / getter / 配置类从统计中排除"
        };
        for (String p : points) {
            System.out.println("  - " + p);
        }
    }
}
```

**课后练习**
1. 给 `calc` 增加"首重 1kg 内偏远地区加价"的规则，先补探针再补用例，让分支覆盖保持 100%。
2. 写一个"覆盖率高但断言缺失"的测试类（只调用不断言），说明为什么 PIT 变异测试能发现它。
3. 说明为什么 `if (orderCents >= 9_900L) return 0;` 之后的 `if (orderCents < 0)` 是**不可达分支**，并给出修正顺序。

### 33-05 打桩与交互验证：手写一个 MiniMockito

**知识点**
- Mock 框架要解决三件事：**创建代理**（`mock`）、**预设返回值**（`when(...).thenReturn(...)`）、**验证交互**（`verify(mock, times(n))`）。
- 实现原理（Mockito 风格）：JDK 动态代理拦截所有方法调用 → 记录每次调用的"方法 + 参数" → `when()` 取"最后一次调用"作为打桩目标 → `thenReturn` 把结果存进 map → 下次同参数调用直接返回缓存值。
- `when()` 之后必须**清空调用记录**：否则 `when(repo.find(1L))` 这次调用本身会被算作一次交互，`verify(times(1))` 就会误报 2 次。
- 未打桩的方法返回**默认值**（对象 `null`、`int` 0、`boolean` false），而不是抛异常。这是 Mockito 的默认行为，容易造成 NPE，需要留意。
- **参数匹配器**：精确值匹配最简单；`any()`、`eq()`、`argThat()` 用于"不关心具体值"的场景。Mockito 要求"一旦用了匹配器，所有参数都得用匹配器"，手写版可以放宽，但要理解这条约束的来源。
- `verify(times(n))` / `never()` / `atLeast(n)` 是**交互验证**，断言的是"协作方式"。它比状态验证更脆弱：重构内部实现（换了个方法调用）就会红，所以只用于**外部副作用**（发短信、扣款、发 MQ）。
- `thenThrow` 用来测异常分支；`thenReturn(a, b)` 连续返回不同值，可测"重试成功"；`doAnswer` 用 lambda 动态计算结果（如"回显参数"）。
- Mock 的边界：**不要 mock 值对象、不要 mock 静态方法（需 PowerMock/内联 mock）、不要 mock final 类**（JDK 代理做不到，需要 ByteBuddy 类代理）。
- 自研 vs 框架：生产用 Mockito（`mockito-core` + `mockito-junit-jupiter`），理解原理是为了在"框架行为诡异"时能定位（如 `when` 失效、`NullPointerException` 来自未打桩的返回值）。
- 易错：`when` 里调用的是 mock 的方法但返回值被用在别处（`when(mock.get())` 里再调 mock 会破坏 lastInvocation）；`verify` 次数与实际不符却以为是框架 bug（多算了 `when` 那次调用）；对 `void` 方法用 `when`（应改 `doThrow().when(mock).method()`）。

**示例代码**
```java
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class MockStubbing {
    interface UserRepo {
        String findName(long userId);

        int countAll();

        boolean isActive(long userId);

        void delete(long userId);
    }

    interface SmsSender {
        void send(String phone, String text);
    }

    static class VerificationError extends RuntimeException {
        private static final long serialVersionUID = 1L;

        VerificationError(String message) {
            super(message);
        }
    }

    /** 一次方法调用的记录 */
    static class Invocation {
        final Method method;
        final Object[] args;

        Invocation(Method method, Object[] args) {
            this.method = method;
            this.args = args == null ? new Object[0] : args.clone();
        }

        String key() {
            return method.getName() + Arrays.deepToString(args);
        }

        @Override
        public String toString() {
            return method.getName() + Arrays.toString(args);
        }
    }

    /** 每个 mock 对象对应一个 Handler */
    static class MockHandler implements InvocationHandler {
        final List<Invocation> invocations = new ArrayList<>();
        final Map<String, Object> stubs = new HashMap<>();
        Invocation last;

        public Object invoke(Object proxy, Method method, Object[] args) {
            String name = method.getName();
            if ("toString".equals(name) && (args == null || args.length == 0)) {
                return "mock(" + method.getDeclaringClass().getSimpleName() + ")";
            }
            if ("hashCode".equals(name) && (args == null || args.length == 0)) {
                return System.identityHashCode(proxy);
            }
            if ("equals".equals(name) && args != null && args.length == 1) {
                return proxy == args[0];
            }
            Invocation inv = new Invocation(method, args);
            invocations.add(inv);
            last = inv;
            if (stubs.containsKey(inv.key())) {
                Object v = stubs.get(inv.key());
                if (v instanceof RuntimeException) {
                    throw (RuntimeException) v;
                }
                return v;
            }
            return defaultValue(method.getReturnType());
        }

        static Object defaultValue(Class<?> type) {
            if (!type.isPrimitive()) {
                return null;
            }
            if (type == boolean.class) {
                return false;
            }
            if (type == long.class) {
                return 0L;
            }
            if (type == double.class) {
                return 0.0d;
            }
            if (type == float.class) {
                return 0.0f;
            }
            if (type == char.class) {
                return (char) 0;
            }
            return 0;
        }

        int countOf(String methodName, Object... args) {
            int n = 0;
            for (Invocation i : invocations) {
                if (i.method.getName().equals(methodName)
                        && Arrays.deepEquals(i.args, args == null ? new Object[0] : args)) {
                    n++;
                }
            }
            return n;
        }
    }

    static final Map<Object, MockHandler> HANDLERS = new IdentityHashMap<>();

    @SuppressWarnings("unchecked")
    static <T> T mock(Class<T> type) {
        MockHandler handler = new MockHandler();
        Object proxy = Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
        HANDLERS.put(proxy, handler);
        return (T) proxy;
    }

    static MockHandler handlerOf(Object mock) {
        MockHandler h = HANDLERS.get(mock);
        if (h == null) {
            throw new IllegalArgumentException("不是本框架创建的 mock: " + mock);
        }
        return h;
    }

    /** when(mock.findName(1L)) -> 取最后一次调用作为打桩键 */
    static class Stubber<T> {
        private final MockHandler handler;

        Stubber(MockHandler handler) {
            this.handler = handler;
        }

        void thenReturn(T value) {
            Invocation inv = handler.last;
            if (inv == null) {
                throw new IllegalStateException("when() 里没有发生任何 mock 调用");
            }
            handler.stubs.put(inv.key(), value);
            handler.invocations.remove(handler.invocations.size() - 1);
            handler.last = null;
        }

        void thenThrow(RuntimeException e) {
            Invocation inv = handler.last;
            if (inv == null) {
                throw new IllegalStateException("when() 里没有发生任何 mock 调用");
            }
            handler.stubs.put(inv.key(), e);
            handler.invocations.remove(handler.invocations.size() - 1);
            handler.last = null;
        }
    }

    static <T> Stubber<T> when(T ignoredReturnValue) {
        return new Stubber<>(LAST_HANDLER.get());
    }

    static final ThreadLocal<MockHandler> LAST_HANDLER = new ThreadLocal<>();

    /** 在 mock 调用后记录 handler，供 when 使用 */
    static <T> T track(T mock) {
        LAST_HANDLER.set(handlerOf(mock));
        return mock;
    }

    static class Verifier {
        final MockHandler handler;
        final int expected;
        final String mode;

        Verifier(MockHandler handler, int expected, String mode) {
            this.handler = handler;
            this.expected = expected;
            this.mode = mode;
        }

        void call(String methodName, Object... args) {
            int actual = handler.countOf(methodName, args);
            boolean ok;
            if ("times".equals(mode)) {
                ok = actual == expected;
            } else if ("never".equals(mode)) {
                ok = actual == 0;
            } else {
                ok = actual >= expected;
            }
            String desc = "times".equals(mode) ? "恰好 " + expected + " 次"
                    : "never".equals(mode) ? "0 次" : "至少 " + expected + " 次";
            if (!ok) {
                throw new VerificationError("期望 " + methodName + Arrays.toString(args)
                        + " 被调用 " + desc + "，实际 " + actual + " 次");
            }
            System.out.printf("    verify %-14s %-18s 期望 %-10s 实际 %d 次 OK%n",
                    methodName, Arrays.toString(args), desc, actual);
        }
    }

    static Verifier verify(Object mock, int times) {
        return new Verifier(handlerOf(mock), times, "times");
    }

    static Verifier never(Object mock) {
        return new Verifier(handlerOf(mock), 0, "never");
    }

    static Verifier atLeast(Object mock, int n) {
        return new Verifier(handlerOf(mock), n, "atLeast");
    }

    /** 被测类 */
    static class UserService {
        private final UserRepo repo;
        private final SmsSender sms;

        UserService(UserRepo repo, SmsSender sms) {
            this.repo = repo;
            this.sms = sms;
        }

        String greet(long userId) {
            String name = repo.findName(userId);
            if (name == null) {
                return "访客";
            }
            if (!repo.isActive(userId)) {
                return "账号已停用";
            }
            sms.send("138****" + userId, "欢迎回来，" + name);
            return "欢迎回来，" + name;
        }
    }

    public static void main(String[] args) {
        System.out.println("=== 1. 打桩 + 状态验证 ===");
        UserRepo repo = mock(UserRepo.class);
        SmsSender sms = mock(SmsSender.class);

        when(track(repo).findName(1L)).thenReturn("张三");
        when(track(repo).isActive(1L)).thenReturn(true);

        UserService svc = new UserService(repo, sms);
        String r = svc.greet(1L);
        System.out.println("    greet(1) = " + r);

        System.out.println();
        System.out.println("=== 2. 交互验证：发短信恰好一次，且参数正确 ===");
        verify(sms, 1).call("send", "138****1", "欢迎回来，张三");
        verify(repo, 1).call("findName", 1L);

        System.out.println();
        System.out.println("=== 3. 未打桩返回默认值（Mockito 默认行为，也是 NPE 来源）===");
        long unknown = 2L;
        System.out.println("    findName(2) = " + repo.findName(unknown) + "（未打桩 -> null）");
        System.out.println("    countAll()  = " + repo.countAll() + "（int 默认 0）");
        System.out.println("    isActive(2) = " + repo.isActive(unknown) + "（boolean 默认 false）");
        System.out.println("    greet(2)    = " + svc.greet(unknown) + "（走 null 分支返回访客）");

        System.out.println();
        System.out.println("=== 4. 验证失败长什么样 ===");
        try {
            verify(sms, 3).call("send", "138****1", "欢迎回来，张三");
        } catch (VerificationError e) {
            System.out.println("    捕获 VerificationError: " + e.getMessage());
        }
        try {
            never(repo).call("delete", 1L);
            System.out.println("    never(delete) 通过（确实没调用）");
        } catch (VerificationError e) {
            System.out.println("    意外: " + e.getMessage());
        }

        System.out.println();
        System.out.println("=== 5. thenThrow 测异常分支 ===");
        UserRepo broken = mock(UserRepo.class);
        when(track(broken).findName(9L)).thenThrow(new IllegalStateException("数据库连接失败"));
        try {
            broken.findName(9L);
        } catch (RuntimeException e) {
            System.out.println("    打桩异常生效: " + e.getClass().getSimpleName() + " - " + e.getMessage());
        }

        System.out.println();
        System.out.println("=== 6. 停用账号：不应发短信（负向验证）===");
        UserRepo repo3 = mock(UserRepo.class);
        SmsSender sms3 = mock(SmsSender.class);
        when(track(repo3).findName(5L)).thenReturn("王五");
        when(track(repo3).isActive(5L)).thenReturn(false);
        String r3 = new UserService(repo3, sms3).greet(5L);
        System.out.println("    greet(5) = " + r3);
        never(sms3).call("send", "138****5", "欢迎回来，王五");
        System.out.println("    结论：状态验证（返回值）+ 交互验证（未发短信）才是完整断言");

        System.out.println();
        System.out.println("=== 7. when() 为什么必须清空那次调用记录 ===");
        System.out.println("    when(mock.findName(1)) 本身会调用一次 mock 并记录；");
        System.out.println("    不清空的话 verify(mock, 1).call(\"findName\", 1) 会看到 2 次而误报失败。");
        System.out.println("    Mockito 内部同样依赖这个 lastInvocation + 清除机制。");

        System.out.println();
        System.out.println("=== 8. Mock 的边界 ===");
        String[] limits = {
                "值对象（Money/OrderId）不要 mock，直接 new",
                "静态方法 JDK 代理拦不到，需 mockito-inline / PowerMock",
                "final 类无法用 JDK 代理，需 ByteBuddy 类代理",
                "void 方法不能用 when，需 doThrow().when(mock).method()",
                "mock 只验证协作，业务结果仍应用 Stub/Fake 做状态验证"
        };
        for (String s : limits) {
            System.out.println("    - " + s);
        }
    }
}
```

**课后练习**
1. 给 `MiniMockito` 增加 `thenReturn(v1, v2)` 连续返回，用它模拟"第一次超时、第二次成功"的重试逻辑。
2. 实现 `thenAnswer(Function<Object[], Object>)`，让 `findName` 直接回显参数（`"user-" + id`）。
3. 说明为什么"用 mock 验证 `List.add` 被调用一次"是坏味道，写出等价但更好的测试。

### 33-06 测试分层：单元、集成与端到端

**知识点**
- **测试金字塔**：底层大量单元测试（快、稳、定位准），中层少量集成测试（验证组件协作），顶层极少量端到端测试（验证用户视角的完整链路）。倒金字塔（大量 E2E）会导致构建慢、失败难定位。
- **单元测试**：单类/单方法，依赖全部替换（Stub/Fake），毫秒级，不碰网络/文件/数据库。
- **集成测试**：真实组件协作，但外部系统可替换。典型是"Service + 真实 Repository（内存库）+ 真实事务"，验证 SQL/映射/事务边界。
- **端到端测试**：从 HTTP 入口到数据库全链路。数量要少（10~30 个核心链路），因为慢且脆弱。
- 分层的**判定标准**不是"用了多少层"，而是"**失败时能否快速定位**"。单元测试红了指向一个方法，E2E 红了只能知道"下单不行"。
- 集成测试的隔离手段：每个用例独立事务回滚（`@Transactional`）、独立临时库/schema、内存实现（H2/SQLite）、Testcontainers（真容器）。
- **测试数据隔离**：用例间共享数据是 flaky 的头号原因。方案：每用例造唯一数据（UUID 后缀）、`@BeforeEach` 清表、临时目录用 `@TempDir`。
- **测试顺序无关**：单独跑和全量跑结果必须一致。用固定随机种子（`new Random(42)`）而不是无种子随机。
- 端到端测试的稳定性技巧：不用 `Thread.sleep`（改轮询 + 超时）、给 HTTP 客户端设超时、测试端口用 0（随机端口）、失败时保留现场（日志、临时文件不删）。
- 易错：把集成测试当单元测试写（每个方法都起 Spring 容器，构建 10 分钟）；E2E 覆盖所有分支；用例间靠执行顺序传数据；集成测试连生产库。

**示例代码**
```java
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TestPyramid {
    /** ---------- 领域层 ---------- */
    static class StockRepo {
        private final Map<String, Integer> stock = new LinkedHashMap<>();

        void set(String sku, int qty) {
            stock.put(sku, qty);
        }

        int get(String sku) {
            return stock.getOrDefault(sku, 0);
        }

        boolean tryDeduct(String sku, int qty) {
            int cur = get(sku);
            if (cur < qty) {
                return false;
            }
            stock.put(sku, cur - qty);
            return true;
        }
    }

    static class StockService {
        private final StockRepo repo;

        StockService(StockRepo repo) {
            this.repo = repo;
        }

        String deduct(String sku, int qty) {
            if (qty <= 0) {
                return "BAD_QTY";
            }
            return repo.tryDeduct(sku, qty) ? "OK" : "NO_STOCK";
        }
    }

    /** ---------- 应用层（HTTP 入口）---------- */
    static class OrderApi {
        private final StockService service;

        OrderApi(StockService service) {
            this.service = service;
        }

        HttpServer start(int port) throws IOException {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
            server.createContext("/deduct", this::handle);
            server.setExecutor(null);
            server.start();
            return server;
        }

        private void handle(HttpExchange ex) throws IOException {
            URI uri = ex.getRequestURI();
            String query = uri.getRawQuery() == null ? "" : uri.getRawQuery();
            String sku = "A";
            int qty = 1;
            for (String kv : query.split("&")) {
                int i = kv.indexOf('=');
                if (i < 0) {
                    continue;
                }
                String k = kv.substring(0, i);
                String v = kv.substring(i + 1);
                if ("sku".equals(k)) {
                    sku = v;
                } else if ("qty".equals(k)) {
                    try {
                        qty = Integer.parseInt(v);
                    } catch (NumberFormatException e) {
                        respond(ex, 400, "BAD_PARAM");
                        return;
                    }
                }
            }
            respond(ex, 200, service.deduct(sku, qty));
        }

        private void respond(HttpExchange ex, int code, String body) throws IOException {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "text/plain; charset=UTF-8");
            ex.sendResponseHeaders(code, bytes.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    static int passed;
    static final List<String> failures = new ArrayList<>();

    static void check(String layer, String name, boolean ok, String detail) {
        if (ok) {
            passed++;
            System.out.printf("    [%s] %-46s OK%n", layer, name);
        } else {
            failures.add(layer + " / " + name + " -> " + detail);
            System.out.printf("    [%s] %-46s FAIL: %s%n", layer, name, detail);
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("=== 1. 单元测试：Stub 替身，不碰真实仓储 ===");
        StockRepo stub = new StockRepo() {
            @Override
            int get(String sku) {
                return 10;
            }

            @Override
            boolean tryDeduct(String sku, int qty) {
                return qty <= 10;
            }
        };
        StockService unitSvc = new StockService(stub);
        check("UNIT", "qty=3 扣减成功", "OK".equals(unitSvc.deduct("A", 3)), "返回值不符");
        check("UNIT", "qty=11 库存不足", "NO_STOCK".equals(unitSvc.deduct("A", 11)), "返回值不符");
        check("UNIT", "qty=0 参数非法", "BAD_QTY".equals(unitSvc.deduct("A", 0)), "返回值不符");
        check("UNIT", "qty=-1 参数非法", "BAD_QTY".equals(unitSvc.deduct("A", -1)), "返回值不符");

        System.out.println();
        System.out.println("=== 2. 集成测试：真实仓储 + 真实服务，验证状态变化 ===");
        StockRepo realRepo = new StockRepo();
        realRepo.set("SKU-1", 5);
        StockService integSvc = new StockService(realRepo);
        check("INTEG", "扣 2 返回 OK", "OK".equals(integSvc.deduct("SKU-1", 2)), "返回值不符");
        check("INTEG", "扣减后库存 = 3", realRepo.get("SKU-1") == 3, "实际=" + realRepo.get("SKU-1"));
        check("INTEG", "再扣 4 失败", "NO_STOCK".equals(integSvc.deduct("SKU-1", 4)), "返回值不符");
        check("INTEG", "失败后库存不变 = 3", realRepo.get("SKU-1") == 3, "实际=" + realRepo.get("SKU-1"));
        check("INTEG", "扣 3 后库存 = 0", "OK".equals(integSvc.deduct("SKU-1", 3))
                && realRepo.get("SKU-1") == 0, "库存=" + realRepo.get("SKU-1"));

        System.out.println();
        System.out.println("=== 3. 端到端测试：真起 HTTP 服务 + 真发请求 ===");
        StockRepo e2eRepo = new StockRepo();
        e2eRepo.set("A", 2);
        HttpServer server = new OrderApi(new StockService(e2eRepo)).start(0);
        int port = server.getAddress().getPort();
        System.out.println("    HttpServer 随机端口 = " + port + "（端口 0 避免冲突）");
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
        try {
            String r1 = get(client, port, "/deduct?sku=A&qty=1");
            check("E2E", "第 1 次扣减", "OK".equals(r1), "body=" + r1);
            String r2 = get(client, port, "/deduct?sku=A&qty=1");
            check("E2E", "第 2 次扣减", "OK".equals(r2), "body=" + r2);
            String r3 = get(client, port, "/deduct?sku=A&qty=1");
            check("E2E", "第 3 次扣减库存不足", "NO_STOCK".equals(r3), "body=" + r3);
            String r4 = get(client, port, "/deduct?sku=A&qty=abc");
            check("E2E", "非法参数", "BAD_PARAM".equals(r4), "body=" + r4);
            check("E2E", "最终库存 = 0", e2eRepo.get("A") == 0, "库存=" + e2eRepo.get("A"));
        } finally {
            server.stop(0);
            System.out.println("    服务已停止（finally 里清理，避免端口泄漏）");
        }

        System.out.println();
        System.out.println("=== 4. 三层对比 ===");
        String[][] t = {
                {"单元", "毫秒", "单类，依赖全替身", "方法级定位", "大量（几百）"},
                {"集成", "百毫秒~秒", "真实组件协作", "组件边界", "中量（几十）"},
                {"端到端", "秒~分钟", "HTTP 到存储全链路", "链路级定位", "少量（核心 10~30）"}
        };
        System.out.printf("  %-6s %-14s %-22s %-14s %s%n", "层级", "耗时", "范围", "失败定位", "建议数量");
        for (String[] r : t) {
            System.out.printf("  %-6s %-14s %-22s %-14s %s%n", r[0], r[1], r[2], r[3], r[4]);
        }

        System.out.println();
        System.out.println("=== 5. 防 flaky 检查清单 ===");
        String[] tips = {
                "每用例造唯一数据（UUID 后缀），不依赖上一个用例",
                "固定随机种子，失败可复现",
                "异步用轮询 + 超时，不用 sleep",
                "HTTP 端口用 0 让系统分配，避免占用冲突",
                "临时文件用 Files.createTempDirectory，结束删除",
                "集成测试每用例事务回滚或清表",
                "客户端设置超时，避免测试挂死",
                "断言失败信息带上实际值"
        };
        for (String s : tips) {
            System.out.println("    - " + s);
        }

        System.out.println();
        System.out.println("=== 结果 ===");
        System.out.println("  通过 " + passed + "，失败 " + failures.size());
        for (String f : failures) {
            System.out.println("    " + f);
        }
    }

    static String get(HttpClient client, int port, String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(3))
                .GET()
                .build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        return resp.body().trim();
    }
}
```

**课后练习**
1. 为 `OrderApi` 增加 `POST /deduct`（JSON body），写集成测试验证非法 JSON 返回 400 而不是 500。
2. 把 E2E 用例改成"并发 10 个请求扣库存 2"，断言成功数恰好 2（提示：`StockRepo` 需改成线程安全）。
3. 说明为什么"每个测试方法都加 `@SpringBootTest`"会让构建从 30 秒变成 10 分钟，并给出分层改造方案。

### 33-07 测试数据构建：ObjectMother、Builder 与夹具清理

**知识点**
- 测试代码里最烦的两件事：**造一个合法对象要写 10 行**、**用例之间数据互相污染**。前者用 ObjectMother/Builder 解决，后者用夹具（fixture）清理解决。
- **ObjectMother**：一组静态工厂方法，返回"典型场景的现成对象"。`Users.aVipUser()`、`Orders.paidOrder()`。优点是语义清晰、一行造数据；缺点是固定值多，容易变成"什么都想加参数"的巨型工厂。
- **Test Data Builder**：链式构建 + 合理默认值，只覆盖关心的字段。`anOrder().withAmount(9900).build()`。比 ObjectMother 灵活，是当前主流（`java-faker`、`Instancio` 都是这个思路）。
- 关键设计：**Builder 必须有能构建出"合法对象"的默认值**。每个用例只改它要测的那一个字段，其余保持默认——这样用例失败时原因唯一。
- **测试夹具（Fixture）**：用例运行前准备好的数据与环境。清理三原则：`finally`/`@AfterEach` 一定执行、删除自己创建的东西、不删除别人的。
- **唯一性**：并发或共享库场景下，用 `UUID` / 时间戳 / 自增序号做后缀，避免主键冲突。不要用固定 ID（`id=1`）在共享库里写数据。
- **临时文件**：用 `Files.createTempDirectory` 而不是硬编码路径；测试结束递归删除；JUnit 5 的 `@TempDir` 会自动清理。
- **不变式断言**：造完数据后先断言"这个夹具确实是我以为的样子"（如"库存确实为 5"），否则夹具写错会让测试失败指向错误的地方。
- 反模式：`@BeforeAll` 里造全局共享可变数据（用例顺序耦合）；Builder 的默认值不合法（每次都要改三个字段）；测试里硬编码时间（用固定 `Clock`）；一个 Builder 服务所有场景导致参数爆炸。
- 易错：Builder 复用同一个实例（`build()` 应每次返回新对象，或至少不共享可变状态）；清理时漏掉关联表；用生产数据做测试夹具（含真实手机号）。

**示例代码**
```java
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

public class TestDataBuilder {
    /** 被测领域对象：不可变 */
    static final class Order {
        final String orderNo;
        final long userId;
        final long amountCents;
        final String status;
        final String couponCode;
        final long createdAt;

        Order(String orderNo, long userId, long amountCents, String status,
              String couponCode, long createdAt) {
            this.orderNo = orderNo;
            this.userId = userId;
            this.amountCents = amountCents;
            this.status = status;
            this.couponCode = couponCode;
            this.createdAt = createdAt;
        }

        boolean payable() {
            return "CREATED".equals(status) && amountCents > 0;
        }

        Order withStatus(String s) {
            return new Order(orderNo, userId, amountCents, s, couponCode, createdAt);
        }

        @Override
        public String toString() {
            return "Order{" + orderNo + ", user=" + userId + ", cents=" + amountCents
                    + ", status=" + status + ", coupon=" + couponCode + "}";
        }
    }

    /** Test Data Builder：默认值即"合法可支付订单" */
    static class OrderBuilder {
        private String orderNo = "ORD-DEFAULT";
        private long userId = 1000L;
        private long amountCents = 9_900L;
        private String status = "CREATED";
        private String couponCode = null;
        private long createdAt = 1_700_000_000_000L;

        static OrderBuilder anOrder() {
            return new OrderBuilder();
        }

        OrderBuilder withOrderNo(String v) {
            this.orderNo = v;
            return this;
        }

        OrderBuilder withUserId(long v) {
            this.userId = v;
            return this;
        }

        OrderBuilder withAmountCents(long v) {
            this.amountCents = v;
            return this;
        }

        OrderBuilder withStatus(String v) {
            this.status = v;
            return this;
        }

        OrderBuilder withCoupon(String v) {
            this.couponCode = v;
            return this;
        }

        OrderBuilder createdAt(long v) {
            this.createdAt = v;
            return this;
        }

        Order build() {
            if (orderNo == null || orderNo.isBlank()) {
                throw new IllegalStateException("orderNo 不能为空");
            }
            if (status == null) {
                throw new IllegalStateException("status 不能为空");
            }
            return new Order(orderNo, userId, amountCents, status, couponCode, createdAt);
        }
    }

    /** ObjectMother：常见场景一行拿到 */
    static class Orders {
        static Order aNormalOrder() {
            return OrderBuilder.anOrder().build();
        }

        static Order aPaidOrder() {
            return OrderBuilder.anOrder().withStatus("PAID").build();
        }

        static Order aCancelledOrder() {
            return OrderBuilder.anOrder().withStatus("CANCELLED").build();
        }

        static Order anOrderWithCoupon(String code) {
            return OrderBuilder.anOrder().withCoupon(code).build();
        }

        static List<Order> threeOrdersOfSameUser() {
            List<Order> list = new ArrayList<>();
            for (int i = 1; i <= 3; i++) {
                list.add(OrderBuilder.anOrder()
                        .withOrderNo("ORD-U1000-" + i)
                        .withUserId(1000L)
                        .withAmountCents(1_000L * i)
                        .build());
            }
            return list;
        }
    }

    /** 夹具：临时目录 + 文件，结束时清理 */
    static class FileFixture implements AutoCloseable {
        final Path dir;
        final List<Path> created = new ArrayList<>();

        FileFixture() throws IOException {
            this.dir = Files.createTempDirectory("order-test-");
        }

        Path write(String name, String content) throws IOException {
            Path p = dir.resolve(name);
            Files.writeString(p, content, StandardCharsets.UTF_8);
            created.add(p);
            return p;
        }

        List<String> listNames() throws IOException {
            try (Stream<Path> s = Files.list(dir)) {
                return s.map(p -> p.getFileName().toString()).sorted().toList();
            }
        }

        public void close() throws IOException {
            try (Stream<Path> s = Files.walk(dir)) {
                List<Path> all = s.sorted(Comparator.reverseOrder()).toList();
                for (Path p : all) {
                    Files.deleteIfExists(p);
                }
            }
        }
    }

    static int passed;
    static final List<String> failures = new ArrayList<>();

    static void check(String name, boolean ok, String detail) {
        if (ok) {
            passed++;
            System.out.printf("    %-52s OK%n", name);
        } else {
            failures.add(name + " -> " + detail);
            System.out.printf("    %-52s FAIL: %s%n", name, detail);
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("=== 1. Builder：只改关心的字段 ===");
        Order cheap = OrderBuilder.anOrder().withAmountCents(1L).build();
        System.out.println("    " + cheap);
        check("1 分订单仍可支付", cheap.payable(), "payable=false");

        Order big = OrderBuilder.anOrder().withAmountCents(999_999L).build();
        check("大额订单不影响其他字段", big.userId == 1000L && "CREATED".equals(big.status),
                "默认值被意外改动");

        System.out.println();
        System.out.println("=== 2. ObjectMother：语义化的一行数据 ===");
        System.out.println("    普通订单: " + Orders.aNormalOrder());
        System.out.println("    已支付  : " + Orders.aPaidOrder());
        System.out.println("    已取消  : " + Orders.aCancelledOrder());
        System.out.println("    带券    : " + Orders.anOrderWithCoupon("CASH10"));

        check("已支付订单不可再支付", !Orders.aPaidOrder().payable(), "payable 应为 false");
        check("已取消订单不可支付", !Orders.aCancelledOrder().payable(), "payable 应为 false");
        check("带券订单仍可支付", Orders.anOrderWithCoupon("CASH10").payable(), "payable 应为 true");

        System.out.println();
        System.out.println("=== 3. 批量夹具：同一用户的 3 笔订单 ===");
        List<Order> batch = Orders.threeOrdersOfSameUser();
        long total = batch.stream().mapToLong(o -> o.amountCents).sum();
        System.out.println("    订单数=" + batch.size() + " 总金额=" + total + " 分");
        check("批量夹具数量正确", batch.size() == 3, "size=" + batch.size());
        check("批量夹具总额 = 6000", total == 6_000L, "total=" + total);
        check("全部属于同一用户", batch.stream().allMatch(o -> o.userId == 1000L), "userId 不一致");

        System.out.println();
        System.out.println("=== 4. 唯一性：避免共享库主键冲突 ===");
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Order unique = OrderBuilder.anOrder().withOrderNo("ORD-" + suffix).build();
        System.out.println("    唯一订单号: " + unique.orderNo);
        check("订单号带唯一后缀", unique.orderNo.length() > 12, "长度不足");
        check("默认订单号未被污染", "ORD-DEFAULT".equals(Orders.aNormalOrder().orderNo),
                "Builder 共享了可变状态");

        System.out.println();
        System.out.println("=== 5. 夹具清理：try-with-resources 保证删除 ===");
        Path created;
        try (FileFixture fx = new FileFixture()) {
            created = fx.write("orders.csv", "orderNo,amount\nORD-1,9900\n");
            fx.write("users.csv", "userId,name\n1000,张三\n");
            System.out.println("    临时目录: " + fx.dir);
            System.out.println("    文件列表: " + fx.listNames());
            check("写入 2 个文件", fx.listNames().size() == 2, "数量不符");
            check("文件内容可读", Files.readString(created, StandardCharsets.UTF_8).contains("ORD-1"),
                    "内容不符");
        }
        check("夹具关闭后目录已删除", !Files.exists(created), "文件仍存在: " + created);

        System.out.println();
        System.out.println("=== 6. 固定时钟：让时间相关的用例可重复 ===");
        Clock fixed = Clock.fixed(Instant.ofEpochMilli(1_700_000_000_000L), ZoneId.of("Asia/Shanghai"));
        Order fresh = OrderBuilder.anOrder().createdAt(fixed.millis()).build();
        long ageMs = fixed.millis() - fresh.createdAt;
        check("刚创建的订单 age = 0", ageMs == 0, "age=" + ageMs);
        Clock later = Clock.offset(fixed, java.time.Duration.ofMinutes(31));
        long laterAge = later.millis() - fresh.createdAt;
        check("31 分钟后 age = 31 分钟", laterAge == 31 * 60_000L, "age=" + laterAge);
        check("31 分钟后超过 30 分钟未支付阈值", laterAge > 30 * 60_000L, "未超阈值");

        System.out.println();
        System.out.println("=== 7. 造数据的四条原则 ===");
        String[] rules = {
                "默认值必须合法：每个用例只改一个字段",
                "build() 每次返回新对象，不共享可变状态",
                "语义化命名：aPaidOrder() 比 new Order(...,\"PAID\",...) 更可读",
                "夹具必清理：try-with-resources / @AfterEach / @TempDir"
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
}
```

**课后练习**
1. 给 `OrderBuilder` 增加 `withItems(int count)`，自动生成明细行，并保证 `build()` 时校验"明细总额 == 订单金额"。
2. 用 `@TempDir` 的等价写法（`Files.createTempDirectory` + `finally`）改写 `FileFixture`，说明两者在异常路径上的差别。
3. 找出"Builder 复用同一实例导致用例互相污染"的一种具体写法，并说明为什么 `build()` 返回不可变对象能规避它。
