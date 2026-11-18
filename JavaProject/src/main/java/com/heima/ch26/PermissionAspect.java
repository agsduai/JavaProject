/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：26-04
 * 课名：AOP 实战：自定义注解做权限校验
 * ------------------------------------------------------------
 * 知识点：
 *   - 思路：定义一个注解 `@RequireRole("admin")`，标在需要权限的方法上；切面读注解的元数据决定是否放行。
 *   - 注解三要素：`@Retention(RUNTIME)`（运行期可反射读取）、`@Target(METHOD/TYPE)`（能标在哪）、`@Documented`（可选）。
 *   - 切面用 `@annotation(...)` 切入点，或在 `@Around` 里 `method.getAnnotation(RequireRole.class)` 读取。
 *   - 权限上下文从 `ThreadLocal` 或 `RequestContextHolder` 取当前登录用户，避免每个方法都传 `User` 参数。
 *   - 拦截方式：不满足条件直接抛 `AccessDeniedException`，交给全局异常处理器转成 403。
 *   - 同一套机制可扩展成：`@RateLimit(qps=10)` 限流、`@Idempotent` 幂等、`@Log` 审计。
 *   - 易错：注解加在接口上而实现类没加（JDK 代理读实现类方法时拿不到接口注解）；`ThreadLocal` 用完不 `remove` 导致线程池串数据。
 * ============================================================
 */

package com.heima.ch26;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

// ==================== 示例代码 ====================

public class PermissionAspect {
    /** 当前登录用户，真实项目里由拦截器解析 token 后放入 */
    static final ThreadLocal<String> CURRENT_ROLE = new ThreadLocal<>();

    public static void main(String[] args) {
        AdminService target = new AdminServiceImpl();
        AdminService proxy = (AdminService) Proxy.newProxyInstance(
                PermissionAspect.class.getClassLoader(),
                new Class<?>[]{AdminService.class},
                new RoleCheckHandler(target));

        System.out.println("=== 普通用户访问 ===");
        CURRENT_ROLE.set("user");
        try {
            proxy.queryScore(1L);
            System.out.println("  查询成绩成功（该方法没有权限要求）");
        } catch (RuntimeException e) {
            System.out.println("  " + e.getMessage());
        }
        try {
            proxy.deleteUser(1L);
        } catch (RuntimeException e) {
            System.out.println("  删除用户被拦截：" + e.getMessage());
        }

        System.out.println();
        System.out.println("=== 管理员访问 ===");
        CURRENT_ROLE.set("admin");
        try {
            System.out.println("  " + proxy.deleteUser(1L));
        } catch (RuntimeException e) {
            System.out.println("  " + e.getMessage());
        } finally {
            CURRENT_ROLE.remove();     // 必须清理，否则线程池复用时串数据
        }

        System.out.println();
        System.out.println("=== 注解定义 ===");
        System.out.println("""
                  @Retention(RetentionPolicy.RUNTIME)
                  @Target(ElementType.METHOD)
                  public @interface RequireRole {
                      String value();
                  }
                """);
        System.out.println("  切面里：RequireRole r = method.getAnnotation(RequireRole.class);");
        System.out.println("          if (r != null && !r.value().equals(CURRENT_ROLE.get())) throw ...");
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface RequireRole {
        String value();
    }

    interface AdminService {
        String queryScore(Long id);

        @RequireRole("admin")
        String deleteUser(Long id);
    }

    static class AdminServiceImpl implements AdminService {
        @Override
        public String queryScore(Long id) {
            return "用户" + id + " 的成绩：92";
        }

        @Override
        public String deleteUser(Long id) {
            return "已删除用户 " + id;
        }
    }

    static class RoleCheckHandler implements InvocationHandler {
        private final Object target;

        RoleCheckHandler(Object target) {
            this.target = target;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            RequireRole require = method.getAnnotation(RequireRole.class);
            if (require != null) {
                String current = CURRENT_ROLE.get();
                if (!require.value().equals(current)) {
                    throw new SecurityException("无权限：需要 " + require.value() + "，当前是 " + current);
                }
            }
            return method.invoke(target, args);
        }
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 用同一套思路实现 `@RateLimit(qps = 5)`：同一方法每秒最多放行 5 次，超出抛异常。
 * 2. 为什么 `ThreadLocal` 必须 `remove()`？举一个线程池下没清理导致用户 A 看到用户 B 数据的场景。
 * 3. 注解标在接口方法上、实现类方法上、两者都标，JDK 代理和 CGLIB 分别能读到哪一个？
 * ============================================================
 */
