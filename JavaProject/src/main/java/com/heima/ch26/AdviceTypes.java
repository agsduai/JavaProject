/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：26-02
 * 课名：AOP 核心概念与五种通知
 * ------------------------------------------------------------
 * 知识点：
 *   - 术语对照（Spring AOP 与 AspectJ 注解）：
 *   - **切面（Aspect）**：横切逻辑的模块，`@Aspect` 标注的类。
 *   - **连接点（JoinPoint）**：可以被织入的点，Spring 里就是方法调用。
 *   - **切入点（Pointcut）**：用表达式选中哪些连接点，`@Pointcut("execution(* com.heima.service.*.*(..))")`。
 *   - **通知（Advice）**：织入的具体动作。
 *   - **目标对象（Target）/ 代理（Proxy）/ 织入（Weaving）**。
 *   - 五种通知：`@Before`（前置）、`@AfterReturning`（正常返回后，可拿返回值）、`@AfterThrowing`（抛异常后）、`@After`（最终，类似 finally）、`@Around`（环绕，可控制是否执行目标方法、可改返回值，功能最强）。
 *   - 切入点表达式：`execution(修饰符 返回类型 包.类.方法(参数))`，`*` 匹配任意，`..` 匹配任意参数/任意层级包。常用 `execution(* com.heima..controller.*.*(..))`。
 *   - `@Around` 里必须调用 `proceed()` 目标方法才会执行；不调用就是"拦截"，可以用来做权限拦截。
 *   - 多个切面时的顺序：`@Order(值)` 越小优先级越高，前置通知按序执行、后置通知逆序执行（像栈）。
 *   - 易错：`@Around` 里吞掉异常会导致 `@AfterThrowing` 不触发；`JoinPoint` 参数必须是第一个参数；`@AfterReturning` 想拿返回值要写 `returning = "ret"` 且形参名一致。
 * ============================================================
 */

package com.heima.ch26;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

// ==================== 示例代码 ====================

public class AdviceTypes {
    public static void main(String[] args) {
        System.out.println("=== 五种通知的执行顺序 ===");
        Calculator target = new CalculatorImpl();
        Calculator proxy = (Calculator) Proxy.newProxyInstance(
                AdviceTypes.class.getClassLoader(),
                new Class<?>[]{Calculator.class},
                new AllAdviceHandler(target));

        System.out.println("调用 add(1, 2)：");
        System.out.println("  结果 = " + proxy.add(1, 2));

        System.out.println();
        System.out.println("调用 divide(1, 0)：");
        try {
            proxy.divide(1, 0);
        } catch (ArithmeticException e) {
            System.out.println("  调用方捕获：" + e.getMessage());
        }

        System.out.println();
        System.out.println("=== 多个切面的顺序（@Order）===");
        List<String> log = new ArrayList<>();
        log.add("Order(1) 前置");
        log.add("Order(2) 前置");
        log.add("目标方法执行");
        log.add("Order(2) 后置");
        log.add("Order(1) 后置");
        log.forEach(s -> System.out.println("  " + s));
        System.out.println("  -> 前置按 Order 升序，后置逆序，像栈一样");

        System.out.println();
        System.out.println("=== 切入点表达式 ===");
        String[] exprs = {
                "execution(* com.heima.service.*.*(..))",
                "execution(* com.heima..controller.*.*(..))",
                "execution(public String com.heima.service.UserService.findById(Long))",
                "@annotation(com.heima.annotation.Log)"};
        String[] means = {
                "service 包下所有类的所有方法",
                "controller 及其子包下所有类的所有方法",
                "精确到某个方法签名",
                "所有标了 @Log 注解的方法"};
        for (int i = 0; i < exprs.length; i++) {
            System.out.println("  " + exprs[i]);
            System.out.println("      -> " + means[i]);
        }
    }

    interface Calculator {
        int add(int a, int b);

        int divide(int a, int b);
    }

    static class CalculatorImpl implements Calculator {
        @Override
        public int add(int a, int b) {
            return a + b;
        }

        @Override
        public int divide(int a, int b) {
            return a / b;
        }
    }

    /** 用一个 handler 模拟 @Around 包住其余四种通知 */
    static class AllAdviceHandler implements InvocationHandler {
        private final Object target;

        AllAdviceHandler(Object target) {
            this.target = target;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            System.out.println("  @Around 开始（proceed 之前）");
            System.out.println("  @Before");
            try {
                Object result = method.invoke(target, args);   // 相当于 proceed()
                System.out.println("  @AfterReturning 返回值=" + result);
                return result;
            } catch (Exception e) {
                System.out.println("  @AfterThrowing " + e.getCause());
                throw e.getCause();
            } finally {
                System.out.println("  @After（无论成败都执行）");
                System.out.println("  @Around 结束（proceed 之后）");
            }
        }
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 写出一个切入点表达式，要求拦截 `com.heima.controller` 包下所有 public 方法。
 * 2. `@Around` 和 `@Before + @AfterReturning` 的能力差别在哪？什么场景只能用 `@Around`？
 * 3. 两个切面 `@Order(1)` 和 `@Order(2)`，画出 5 种通知的完整执行顺序。
 * ============================================================
 */
