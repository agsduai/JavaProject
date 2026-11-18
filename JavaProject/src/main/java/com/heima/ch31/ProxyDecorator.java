/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：31-05
 * 课名：代理模式与装饰器模式
 * ------------------------------------------------------------
 * 知识点：
 *   - **代理模式**：为目标对象提供一个替身，客户端通过替身访问目标。核心是"控制访问"，常见用途：权限校验、延迟加载、远程调用、日志埋点。
 *   - **静态代理**：手写一个实现同接口的类，持有目标引用。缺点是一种接口就要写一个代理类，接口方法一多就是体力活。
 *   - **JDK 动态代理**：`Proxy.newProxyInstance(loader, interfaces, handler)` 在运行期生成代理类，`InvocationHandler.invoke` 里统一织入逻辑。前提是**目标必须实现接口**。
 *   - **CGLIB 代理**：通过继承生成子类，不要求接口，但无法代理 `final` 类/`final` 方法。Spring AOP 默认"有接口用 JDK，无接口用 CGLIB"。
 *   - **装饰器模式**：同样实现同接口并持有目标，但目的是"**增强功能**"而不是"控制访问"，且可以层层叠加（`new BufferedInputStream(new FileInputStream(...))`）。
 *   - 代理与装饰的判别口诀：**代理控制"能不能用"，装饰增强"用完什么样"**；代理通常由框架生成、客户端不感知，装饰由客户端显式组装。
 *   - 装饰器的顺序有意义：`缓存(日志(实现))` 与 `日志(缓存(实现))` 的日志条数不同，前者只记录未命中，后者记录每次调用。
 *   - 易错：动态代理里 `method.invoke(target, args)` 传错目标（传成 `proxy` 会无限递归）；`invoke` 里吞掉异常导致业务错误消失；装饰器忘了转发某个方法（编译能过，运行时行为缺失）。
 * ============================================================
 */

package com.heima.ch31;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

// ==================== 示例代码 ====================

public class ProxyDecorator {
    interface UserService {
        String findName(long id);
    }

    static class UserServiceImpl implements UserService {
        public String findName(long id) {
            sleep(60);
            return "用户" + id;
        }
    }

    /** 静态代理：只能代理 UserService，换接口要重写 */
    static class LoggingUserService implements UserService {
        private final UserService target;

        LoggingUserService(UserService target) {
            this.target = target;
        }

        public String findName(long id) {
            System.out.println("    [日志] 查询用户 id=" + id);
            return target.findName(id);
        }
    }

    /** 装饰器：加缓存，与目标同接口，可任意叠加 */
    static class CachedUserService implements UserService {
        private final UserService target;
        private final Map<Long, String> cache = new HashMap<>();

        CachedUserService(UserService target) {
            this.target = target;
        }

        public String findName(long id) {
            String v = cache.get(id);
            if (v == null) {
                v = target.findName(id);
                cache.put(id, v);
                System.out.println("    [缓存] 未命中，已写入 id=" + id);
            } else {
                System.out.println("    [缓存] 命中 id=" + id);
            }
            return v;
        }
    }

    static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static void main(String[] args) {
        System.out.println("=== 1. 静态代理 + 装饰器叠加 ===");
        UserService svc = new CachedUserService(new LoggingUserService(new UserServiceImpl()));
        long t0 = System.nanoTime();
        System.out.println("  结果: " + svc.findName(1001));
        System.out.println("  结果: " + svc.findName(1001));
        System.out.printf("  两次调用总耗时 %.1f ms（第二次走缓存）%n", (System.nanoTime() - t0) / 1e6);

        System.out.println();
        System.out.println("=== 2. JDK 动态代理：不写代理类，统一加耗时统计 ===");
        final UserService target = new UserServiceImpl();
        UserService dynamic = (UserService) Proxy.newProxyInstance(
                UserService.class.getClassLoader(),
                new Class<?>[]{UserService.class},
                new InvocationHandler() {
                    public Object invoke(Object proxy, Method method, Object[] a) throws Throwable {
                        long start = System.nanoTime();
                        try {
                            return method.invoke(target, a);
                        } finally {
                            System.out.printf("    [耗时] %s.%s = %.1f ms%n",
                                    "UserService", method.getName(), (System.nanoTime() - start) / 1e6);
                        }
                    }
                });
        System.out.println("  代理类: " + dynamic.getClass().getName());
        System.out.println("  结果: " + dynamic.findName(2002));

        System.out.println();
        System.out.println("=== 3. 顺序不同的装饰器，行为不同 ===");
        UserService logOutside = new LoggingUserService(new CachedUserService(new UserServiceImpl()));
        System.out.println("  日志在外层（每次调用都记日志）：");
        logOutside.findName(3003);
        logOutside.findName(3003);
        UserService cacheOutside = new CachedUserService(new LoggingUserService(new UserServiceImpl()));
        System.out.println("  缓存在外层（第二次不进日志）：");
        cacheOutside.findName(3003);
        cacheOutside.findName(3003);

        System.out.println();
        System.out.println("=== 代理 vs 装饰 ===");
        System.out.println("  代理：控制访问（权限/延迟/远程），通常框架生成，客户端不感知");
        System.out.println("  装饰：增强功能（缓存/压缩/加密），客户端显式 new 出来层层包装");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 用动态代理实现"权限校验 + 耗时统计"两个增强，说明 `InvocationHandler` 里如何判断方法是否需要鉴权。
 * 2. 把 `CachedUserService` 改成有容量上限的 LRU 缓存装饰器（可参考 `LinkedHashMap.removeEldestEntry`）。
 * 3. 解释为什么 `method.invoke(proxy, args)` 会导致无限递归，画出调用栈。
 * ============================================================
 */
