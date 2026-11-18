/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：26-03
 * 课名：AOP 实战：统一日志与接口耗时
 * ------------------------------------------------------------
 * 知识点：
 *   - 目标：所有 Controller 方法自动记录"请求方法、参数、返回值、耗时、异常"，业务代码零侵入。
 *   - 关键实现点：
 *   - `@Around` 里用 `ProceedingJoinPoint`，`getSignature()` 拿方法签名，`getArgs()` 拿参数。
 *   - 参数里有 `HttpServletRequest`、`MultipartFile` 这类不能序列化的对象，要过滤掉，否则日志会报错或打印一堆无用内容。
 *   - 耗时用 `System.nanoTime()`，不要用 `currentTimeMillis`（受系统时间调整影响）。
 *   - 异常要记 error 日志后**原样抛出**，不能吞掉，否则全局异常处理器收不到。
 *   - 日志量大时要采样或只记录慢请求（超过 500ms 才打）。
 *   - 与 `MDC` 配合：把 traceId 放进 MDC，日志格式里带 `%X{traceId}`，一个请求的所有日志能串起来。
 *   - 易错：在切面里再查一次数据库（把日志切面写成业务切面），会显著拖慢接口；日志里打印完整大对象导致 OOM。
 * ============================================================
 */

package com.heima.ch26;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

// ==================== 示例代码 ====================

public class LogAspectDemo {
    public static void main(String[] args) {
        UserService target = new UserServiceImpl();
        UserService proxy = (UserService) Proxy.newProxyInstance(
                LogAspectDemo.class.getClassLoader(),
                new Class<?>[]{UserService.class},
                new LogAspect(target));

        System.out.println("=== 正常请求 ===");
        System.out.println("  返回：" + proxy.findById(1L));

        System.out.println();
        System.out.println("=== 慢请求（超过阈值才打详细日志）===");
        System.out.println("  返回：" + proxy.listUsers(200));

        System.out.println();
        System.out.println("=== 抛异常的请求 ===");
        try {
            proxy.findById(null);
        } catch (IllegalArgumentException e) {
            System.out.println("  上层收到异常：" + e.getMessage());
        }
    }

    interface UserService {
        String findById(Long id);

        String listUsers(int size);
    }

    static class UserServiceImpl implements UserService {
        @Override
        public String findById(Long id) {
            if (id == null) {
                throw new IllegalArgumentException("id 不能为空");
            }
            return "用户" + id;
        }

        @Override
        public String listUsers(int size) {
            sleep(size);                       // 模拟慢查询
            return size + " 条用户数据";
        }
    }

    /** 模拟 @Around 日志切面 */
    static class LogAspect implements InvocationHandler {
        private static final long SLOW_MS = 100;
        private final Object target;

        LogAspect(Object target) {
            this.target = target;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String traceId = Integer.toHexString(System.identityHashCode(Thread.currentThread()));
            String sig = method.getDeclaringClass().getSimpleName() + "." + method.getName();
            String params = formatArgs(method, args);

            long start = System.nanoTime();
            try {
                Object result = method.invoke(target, args);
                long costMs = (System.nanoTime() - start) / 1_000_000;
                if (costMs >= SLOW_MS) {
                    System.out.println("  [SLOW] traceId=" + traceId + " " + sig + params
                            + " cost=" + costMs + "ms");
                } else {
                    System.out.println("  [INFO] traceId=" + traceId + " " + sig + params
                            + " cost=" + costMs + "ms result=" + brief(result));
                }
                return result;
            } catch (Exception e) {
                long costMs = (System.nanoTime() - start) / 1_000_000;
                System.out.println("  [ERROR] traceId=" + traceId + " " + sig + params
                        + " cost=" + costMs + "ms ex=" + e.getCause());
                throw e.getCause();
            }
        }

        /** 过滤掉不能/不该打日志的参数 */
        static String formatArgs(Method method, Object[] args) {
            if (args == null || args.length == 0) {
                return "()";
            }
            Map<String, Object> map = new LinkedHashMap<>();
            String[] names = method.getParameters()[0].getName().isEmpty()
                    ? null : Arrays.stream(method.getParameters()).map(p -> p.getName()).toArray(String[]::new);
            for (int i = 0; i < args.length; i++) {
                if (args[i] instanceof byte[]) {
                    map.put(name(names, i), "<binary " + ((byte[]) args[i]).length + " bytes>");
                } else {
                    map.put(name(names, i), args[i]);
                }
            }
            return " " + map;
        }

        static String name(String[] names, int i) {
            return names != null && i < names.length ? names[i] : "arg" + i;
        }

        static String brief(Object o) {
            String s = String.valueOf(o);
            return s.length() > 30 ? s.substring(0, 30) + "..." : s;
        }
    }

    static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 为什么日志切面必须把异常原样抛出？如果吞掉会有什么后果？
 * 2. 参数里有 `MultipartFile` 和 `HttpServletRequest` 时怎么处理？写出过滤逻辑的思路。
 * 3. 给这个切面加"只记录耗时超过 500ms 的请求"和"每天日志量上限"两个开关，说明如何配置化。
 * ============================================================
 */
