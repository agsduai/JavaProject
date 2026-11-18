/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：26-01
 * 课名：AOP：把横切逻辑抽出来
 * ------------------------------------------------------------
 * 知识点：
 *   - 横切关注点：日志、耗时、事务、权限、限流、缓存这些"每个方法都要做一点"的逻辑，和业务逻辑正交，混在一起会导致大量重复代码。
 *   - 继承/工具类解决不了：每个方法都要手动调用一次，漏写就没效果；也无法统一开关。
 *   - AOP 的思路：不改原方法代码，在方法执行的前后"织入"额外逻辑。Java 里靠**动态代理**实现。
 *   - 两种代理：
 *   - JDK 动态代理：`Proxy.newProxyInstance`，要求目标类实现接口，代理对象和目标实现同一个接口。
 *   - CGLIB：生成目标类的子类，不要求接口，但 `final` 类/方法无法代理。Spring Boot 2.x 起默认用 CGLIB（`proxy-target-class=true`）。
 *   - Spring 只在**通过代理对象调用**时生效：同类内部 `this.method()` 不走代理，注解失效（这是 `@Transactional`、`@Cacheable`、`@Async` 全部失效的同一个根因）。
 *   - 织入时机：编译期（AspectJ）、类加载期、运行期（Spring AOP 用运行期代理）。
 *   - 易错：`private` 方法不能被代理；`final` 方法不能被 CGLIB 覆盖；`@Transactional` 加在 `private` 上静默失效。
 * ============================================================
 */

package com.heima.ch26;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

// ==================== 示例代码 ====================

public class AopIntro {
    public static void main(String[] args) {
        System.out.println("=== 不用 AOP：每个方法自己写日志与耗时 ===");
        OrderServiceRaw raw = new OrderServiceRaw();
        raw.createOrder("键盘");
        raw.cancelOrder(1001L);

        System.out.println();
        System.out.println("=== 用 JDK 动态代理：日志与耗时统一织入 ===");
        OrderService target = new OrderServiceImpl();
        OrderService proxy = (OrderService) Proxy.newProxyInstance(
                AopIntro.class.getClassLoader(),
                new Class<?>[]{OrderService.class},
                new LogTimeHandler(target));
        proxy.createOrder("鼠标");
        proxy.cancelOrder(1002L);

        System.out.println();
        System.out.println("=== 代理对象是什么 ===");
        System.out.println("  proxy.getClass() = " + proxy.getClass().getName());
        System.out.println("  是 OrderService 的实现吗：" + (proxy instanceof OrderService));
        System.out.println("  真实对象被包在 InvocationHandler 里，调用先经过 handler");
    }

    interface OrderService {
        String createOrder(String item);

        boolean cancelOrder(Long id);
    }

    /** 目标类：只写业务，不含任何日志代码 */
    static class OrderServiceImpl implements OrderService {
        @Override
        public String createOrder(String item) {
            return "订单已创建：" + item;
        }

        @Override
        public boolean cancelOrder(Long id) {
            return id != null && id > 0;
        }
    }

    /** 横切逻辑：日志 + 耗时 */
    static class LogTimeHandler implements InvocationHandler {
        private final Object target;

        LogTimeHandler(Object target) {
            this.target = target;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            long start = System.nanoTime();
            System.out.println("  [前置] 调用 " + method.getName() + "，参数 " + java.util.Arrays.toString(args));
            try {
                Object result = method.invoke(target, args);
                System.out.println("  [后置] 返回 " + result);
                return result;
            } catch (Exception e) {
                System.out.println("  [异常] " + e.getCause());
                throw e.getCause();
            } finally {
                long cost = (System.nanoTime() - start) / 1000;
                System.out.println("  [耗时] " + method.getName() + " 用了 " + cost + " 微秒");
            }
        }
    }

    /** 反面示例：业务里手动埋点，重复且容易漏 */
    static class OrderServiceRaw {
        String createOrder(String item) {
            long start = System.nanoTime();
            System.out.println("  [日志] createOrder 参数=" + item);
            String r = "订单已创建：" + item;
            System.out.println("  [耗时] " + (System.nanoTime() - start) / 1000 + " 微秒");
            return r;
        }

        boolean cancelOrder(Long id) {
            long start = System.nanoTime();
            System.out.println("  [日志] cancelOrder 参数=" + id);
            boolean r = id != null && id > 0;
            System.out.println("  [耗时] " + (System.nanoTime() - start) / 1000 + " 微秒");
            return r;
        }
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 列出你写过的项目里 3 个横切关注点，说明用 AOP 前后代码量的差别。
 * 2. JDK 动态代理和 CGLIB 的适用条件分别是什么？Spring Boot 为什么默认选 CGLIB？
 * 3. 为什么 `@Transactional` 加在同类内部调用的方法上会失效？画出调用链解释。
 * ============================================================
 */
