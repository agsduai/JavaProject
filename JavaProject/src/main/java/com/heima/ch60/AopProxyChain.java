/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：60-04
 * 课名：Spring AOP：代理创建与拦截链
 * ------------------------------------------------------------
 * 知识点：
 *   - **AOP 的实现路径只有两条**：**JDK 动态代理**（基于接口，`Proxy.newProxyInstance`，50-08/26-01 已实战）与 **CGLIB**（基于继承，生成子类字节码，可代理无接口类）——**Spring 默认：有接口用 JDK 代理，无接口用 CGLIB**（Spring Boot 2.x 起默认强制 CGLIB，`proxyTargetClass=true`）。
 *   - **代理生成的时机**：`BeanPostProcessor.postProcessAfterInitialization`（60-03 第六步）——**容器在 Bean 初始化完成后判断"是否需要被切面拦截"，需要则返回代理对象替换原对象**。这就是"注入的其实是代理"的原因。
 *   - **拦截链（MethodInterceptor 链）**：多个切面按 `@Order` 排序组成责任链（26-01 已实战），**前置逻辑正序、后置逆序**——与 26-01 的 AOP 链完全一致，本章补的是"链是怎么织进容器的"。
 *   - **切点（Pointcut）匹配是性能关键**：每个方法调用都要判断是否命中切点——**命中判断用缓存**（Spring 缓存切点匹配结果），但**切点表达式写得宽（`execution(* com..*(..))`）会拖慢全应用**。
 *   - **AOP 的边界**：只拦截**外部通过代理的调用**——**同类内部方法调用（`this.method()`）不经过代理，切面失效**（60-05 事务失效的第一大原因）；`private/final` 方法无法被 CGLIB 代理。
 *   - 易错点：同类自调用导致切面失效（AOP 与事务的共同坑）；`final` 类/方法用 CGLIB（无法继承）；切点过宽（性能与可读性双降）；以为注解存在就一定生效（需被代理）。
 * ============================================================
 */

package com.heima.ch60;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

// ==================== 示例代码 ====================

public class AopProxyChain {
    interface OrderService {
        String place(String orderId);
    }

    static class OrderServiceImpl implements OrderService {
        public String place(String orderId) {
            System.out.println("    [目标方法] place(" + orderId + ")");
            return "placed:" + orderId;
        }
    }

    record Advice(String name, int order, String before, String after) {
    }

    /** 责任链式代理：前置正序、后置逆序（26-01 的容器接入版） */
    static OrderService wrap(OrderService target, List<Advice> advices) {
        List<Advice> sorted = new ArrayList<>(advices);
        sorted.sort((a, b) -> Integer.compare(a.order(), b.order()));
        return (OrderService) Proxy.newProxyInstance(
                OrderService.class.getClassLoader(),
                new Class<?>[]{OrderService.class},
                (InvocationHandler) (proxy, method, args) -> {
                    List<Advice> entered = new ArrayList<>();
                    for (Advice a : sorted) {
                        System.out.println("    [前置] " + a.before());
                        entered.add(a);
                    }
                    Object result;
                    try {
                        result = method.invoke(target, args);
                    } finally {
                        for (int i = entered.size() - 1; i >= 0; i--) {
                            System.out.println("    [后置] " + entered.get(i).after());
                        }
                    }
                    return result;
                });
    }

    public static void main(String[] args) {
        List<Advice> advices = List.of(
                new Advice("transaction", 1, "开启事务", "提交事务"),
                new Advice("logging", 2, "记录入参", "记录耗时"),
                new Advice("metrics", 3, "开始计时", "上报指标"));
        System.out.println("【切面顺序（@Order 1/2/3）与责任链执行】");
        OrderService proxy = wrap(new OrderServiceImpl(), advices);
        String r = proxy.place("9001");
        System.out.println("  返回：" + r);
        System.out.println();
        System.out.println("  注意：前置正序（transaction -> logging -> metrics），后置逆序");
        System.out.println("  若用 this.place() 内部调用 -> 不经过代理 -> 三个切面全部失效");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现"切点匹配器"：为方法匹配 `execution(* service.*Service.*(..))` 这类表达式，用 20 个方法验证命中结果。
 * 2. 在同一 Spring 应用里同时用 JDK 代理与 CGLIB 代理各实现一个切面，用 `AopUtils` 打印代理类型，解释默认策略。
 * 3. 思考题：为什么"同类自调用切面失效"是 AOP 的结构性限制而非 bug？从"代理只能拦截经过自己的调用"的机制推导。
 * ============================================================
 */
