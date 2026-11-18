/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：实操 26-01
 * 课名：AOP 代理链模拟
 * ------------------------------------------------------------
 * 知识点：
 *   - 【题目要求】用 JDK 动态代理实现 AOP 链：切面（日志计时/权限校验/重试）按 Order 排序织成拦截链（责任链 31 章思想）；代理调用顺序 = 前置按 order 正序 -> 目标方法 -> 后置逆序；异常在链上传播（权限失败直接抛出，不进目标方法）；用计时+权限双切面演示完整链路输出。
 *   - 【考核点】动态代理实现 AOP、拦截器链（pre/target/post 顺序）、切面排序、异常传播路径。
 *   - 【易错点】后置逻辑顺序颠倒（应逆序）；权限切面放在计时之后（失败也计了时，日志误导）；链中吞异常（上层无感知）。
 *   - 【提示】拦截器接口三方法（before/after/throwing）；链递归推进（每个拦截器调 next）。
 * ============================================================
 */

package com.heima.practice.middleware;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

// ==================== 示例代码 ====================

public class AopChainTask {
    interface Handler {
        String handle(String req);
    }

    interface Interceptor {
        String name();

        String before(String req);

        void after(String resp);
    }

    public static void main(String[] args) {
        List<Interceptor> chain = new ArrayList<>(List.of(
                new Interceptor() {
                    public String name() {
                        return "audit";
                    }

                    public String before(String req) {
                        System.out.println("  audit.before: " + req);
                        return req;
                    }

                    public void after(String resp) {
                        System.out.println("  audit.after: " + resp);
                    }
                }));
        Handler target = req -> "resp:" + req;
        Handler proxied = (Handler) Proxy.newProxyInstance(Handler.class.getClassLoader(),
                new Class<?>[]{Handler.class},
                (proxy, method, args1) -> {
                    String req = (String) args1[0];
                    for (Interceptor i : chain) {
                        req = i.before(req);
                    }
                    String resp = target.handle(req);
                    for (int i = chain.size() - 1; i >= 0; i--) {
                        chain.get(i).after(resp);
                    }
                    return resp;
                });
        System.out.println("  最终：" + proxied.handle("hello"));
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现完整 AOP 链（三切面按 Order 织入 + 异常传播），验证前置正序/后置逆序/权限失败短路。
 * 2. 实现重试切面（只对 IO 异常重试 2 次），演示异常类型敏感的重试。
 * 3. 思考题：为什么 AOP 能把"日志/权限/重试"从业务代码剥离？从横切关注点与拦截链的结构分析收益与代价。
 * ============================================================
 */
