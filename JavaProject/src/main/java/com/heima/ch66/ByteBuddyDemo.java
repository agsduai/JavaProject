/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：66-05
 * 课名：ByteBuddy：更友好的增强
 * ------------------------------------------------------------
 * 知识点：
 *   - **ByteBuddy 是 ASM 之上的高层封装**：用**声明式 API** 描述"要拦截什么、怎么增强"（`ElementMatchers` 匹配方法、`Advice` 插入前后逻辑、`AgentBuilder` 一键装 Agent）——**不需要手写指令**，代价是少量性能开销与"抽象泄漏"（复杂场景仍要懂字节码）。
 *   - **三个核心抽象**：**Matcher**（`named("query").and(takesArguments(1))`——匹配目标）、**Advice**（`@Advice.OnMethodEnter/Exit`——在方法前后插入 Java 代码，**编译期生成字节码**）、**AgentBuilder**（`-javaagent` 或 attach 的装配）。
 *   - **Advice 的三种注入点**：**Enter**（入口，可修改参数）、**Exit**（出口，可读取返回值/异常、替换返回值）、**Field/Method 拦截**（`@SuperCall` 调用原方法）——**覆盖了 90% 的埋点需求**。
 *   - **Advice 与 Interceptor 的区别**：`Advice` 是"内联插入"（无额外对象分配，性能最好，但不能改方法签名）；`MethodDelegation` 是"委托调用"（灵活，有开销）——**埋点优先 Advice**。
 *   - **与 66-04 的对比**：ASM 手写指令（灵活但易错）vs ByteBuddy Advice（安全高效但有抽象成本）——**生产 Agent 通常用 ByteBuddy，复杂字节码操作才下沉到 ASM**。
 *   - 易错点：Matcher 写得过宽（拦截了不该拦的类）；Advice 里抛异常（影响业务方法）；Exit 里没判断异常（吞掉业务异常）；Agent 依赖与目标应用冲突（类加载器隔离）。
 * ============================================================
 */

package com.heima.ch66;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

// ==================== 示例代码 ====================

public class ByteBuddyDemo {
    /** 模拟 Matcher：按类名/方法名/参数个数匹配 */
    record Matcher(String classPrefix, String methodName, int argCount) {
        boolean matches(String cls, String method, int args) {
            return (classPrefix.isEmpty() || cls.startsWith(classPrefix))
                    && (methodName.isEmpty() || methodName.equals(method))
                    && (argCount < 0 || argCount == args);
        }
    }

    /** 模拟 Advice：enter/exit 两段逻辑 */
    interface Advice {
        void enter(String cls, String method, Object[] args);

        void exit(String cls, String method, Object result, Throwable error);
    }

    /** 模拟被增强的业务方法 */
    static class Biz {
        String query(String orderId) {
            if (orderId == null) {
                throw new IllegalArgumentException("orderId 不能为空");
            }
            return "order-" + orderId;
        }

        int count(int a, int b) {
            return a + b;
        }
    }

    /** 简易 AgentBuilder：按 matcher 包装方法，Advice 前后执行 */
    static class MiniAgent {
        final Matcher matcher;
        final Advice advice;

        MiniAgent(Matcher matcher, Advice advice) {
            this.matcher = matcher;
            this.advice = advice;
        }

        Function<Object[], Object> wrap(String cls, String method,
                                       Function<Object[], Object> target) {
            if (!matcher.matches(cls, method, -1)) {
                return target;
            }
            return args -> {
                advice.enter(cls, method, args);
                Object result = null;
                Throwable error = null;
                try {
                    result = target.apply(args);
                    return result;
                } catch (RuntimeException e) {
                    error = e;
                    throw e;
                } finally {
                    advice.exit(cls, method, result, error);
                }
            };
        }
    }

    public static void main(String[] args) {
        List<String> log = new ArrayList<>();
        Advice loggingAdvice = new Advice() {
            public void enter(String cls, String method, Object[] args) {
                log.add("ENTER " + cls + "." + method + " args=" + java.util.Arrays.toString(args));
            }

            public void exit(String cls, String method, Object result, Throwable error) {
                log.add("EXIT  " + cls + "." + method + " result=" + result
                        + (error == null ? "" : " error=" + error.getClass().getSimpleName()));
            }
        };
        MiniAgent agent = new MiniAgent(new Matcher("Biz", "query", 1), loggingAdvice);

        Biz biz = new Biz();
        var query = agent.wrap("Biz", "query", callArgs -> biz.query((String) callArgs[0]));
        var count = agent.wrap("Biz", "count",
                callArgs -> biz.count((Integer) callArgs[0], (Integer) callArgs[1]));

        System.out.println("【被匹配的方法】");
        System.out.println("  query(9001) -> " + query.apply(new Object[]{"9001"}));
        System.out.println();
        System.out.println("【未被匹配的方法（不增强）】");
        System.out.println("  count(1,2) -> " + count.apply(new Object[]{1, 2}));
        System.out.println();
        System.out.println("【异常路径：exit 也能拿到异常】");
        try {
            query.apply(new Object[]{null});
        } catch (IllegalArgumentException e) {
            System.out.println("  业务异常按原样抛出：" + e.getMessage());
        }
        System.out.println();
        System.out.println("【Advice 日志】");
        log.forEach(l -> System.out.println("  " + l));
        System.out.println();
        Map<String, String> notes = new LinkedHashMap<>();
        notes.put("Matcher", "按类名/方法名/参数匹配，过宽会误伤（66-02 的包过滤）");
        notes.put("Advice", "enter 可读参数、exit 可读返回值与异常，不改变方法签名");
        notes.put("异常", "Advice 自身不能抛异常，否则污染业务方法");
        notes.forEach((k, v) -> System.out.printf("  %-8s %s%n", k, v));
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 用真实 ByteBuddy 写一个 Agent：给指定包内所有 public 方法插入耗时统计，挂到示例程序验证。
 * 2. 实现 Advice 的"参数修改"（enter 里改参数）与"返回值替换"（exit 里改返回），验证两者对业务的影响。
 * 3. 思考题：为什么"Advice 内联插入"比"MethodDelegation 委托调用"性能好？从对象分配与调用栈开销分析。
 * ============================================================
 */
