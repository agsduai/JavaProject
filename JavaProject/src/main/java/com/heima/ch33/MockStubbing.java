/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：33-05
 * 课名：打桩与交互验证：手写一个 MiniMockito
 * ------------------------------------------------------------
 * 知识点：
 *   - Mock 框架要解决三件事：**创建代理**（`mock`）、**预设返回值**（`when(...).thenReturn(...)`）、**验证交互**（`verify(mock, times(n))`）。
 *   - 实现原理（Mockito 风格）：JDK 动态代理拦截所有方法调用 → 记录每次调用的"方法 + 参数" → `when()` 取"最后一次调用"作为打桩目标 → `thenReturn` 把结果存进 map → 下次同参数调用直接返回缓存值。
 *   - `when()` 之后必须**清空调用记录**：否则 `when(repo.find(1L))` 这次调用本身会被算作一次交互，`verify(times(1))` 就会误报 2 次。
 *   - 未打桩的方法返回**默认值**（对象 `null`、`int` 0、`boolean` false），而不是抛异常。这是 Mockito 的默认行为，容易造成 NPE，需要留意。
 *   - **参数匹配器**：精确值匹配最简单；`any()`、`eq()`、`argThat()` 用于"不关心具体值"的场景。Mockito 要求"一旦用了匹配器，所有参数都得用匹配器"，手写版可以放宽，但要理解这条约束的来源。
 *   - `verify(times(n))` / `never()` / `atLeast(n)` 是**交互验证**，断言的是"协作方式"。它比状态验证更脆弱：重构内部实现（换了个方法调用）就会红，所以只用于**外部副作用**（发短信、扣款、发 MQ）。
 *   - `thenThrow` 用来测异常分支；`thenReturn(a, b)` 连续返回不同值，可测"重试成功"；`doAnswer` 用 lambda 动态计算结果（如"回显参数"）。
 *   - Mock 的边界：**不要 mock 值对象、不要 mock 静态方法（需 PowerMock/内联 mock）、不要 mock final 类**（JDK 代理做不到，需要 ByteBuddy 类代理）。
 *   - 自研 vs 框架：生产用 Mockito（`mockito-core` + `mockito-junit-jupiter`），理解原理是为了在"框架行为诡异"时能定位（如 `when` 失效、`NullPointerException` 来自未打桩的返回值）。
 *   - 易错：`when` 里调用的是 mock 的方法但返回值被用在别处（`when(mock.get())` 里再调 mock 会破坏 lastInvocation）；`verify` 次数与实际不符却以为是框架 bug（多算了 `when` 那次调用）；对 `void` 方法用 `when`（应改 `doThrow().when(mock).method()`）。
 * ============================================================
 */

package com.heima.ch33;

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

// ==================== 示例代码 ====================

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

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 给 `MiniMockito` 增加 `thenReturn(v1, v2)` 连续返回，用它模拟"第一次超时、第二次成功"的重试逻辑。
 * 2. 实现 `thenAnswer(Function<Object[], Object>)`，让 `findName` 直接回显参数（`"user-" + id`）。
 * 3. 说明为什么"用 mock 验证 `List.add` 被调用一次"是坏味道，写出等价但更好的测试。
 * ============================================================
 */
