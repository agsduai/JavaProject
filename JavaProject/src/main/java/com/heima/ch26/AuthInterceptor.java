/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：26-10
 * 课名：拦截器与鉴权上下文
 * ------------------------------------------------------------
 * 知识点：
 *   - 拦截器（`HandlerInterceptor`）三个方法：`preHandle`（Controller 前，返回 false 中断）、`postHandle`（Controller 后、视图渲染前）、`afterCompletion`（请求完成后，做清理）。
 *   - 与 Filter 的区别：Filter 是 Servlet 规范、在 DispatcherServlet 之前；Interceptor 是 Spring MVC 的、能拿到 `HandlerMethod`（知道要执行哪个方法、有什么注解）。
 *   - 注册：`WebMvcConfigurer#addInterceptors`，`addPathPatterns("/**")` + `excludePathPatterns("/login", "/register", "/error")`。
 *   - 鉴权上下文：`preHandle` 里验 token -> 解析出 userId/role -> 放进 `ThreadLocal`（或 `RequestContextHolder`）-> `afterCompletion` 里 `remove()`。
 *   - 与 26-04 的注解切面配合：拦截器负责"认证"（你是谁），切面负责"授权"（你能不能做这件事）。
 *   - 常见坑：拦截器里抛异常不会走 `@RestControllerAdvice` 的 `@ExceptionHandler`（因为发生在 Handler 之前），要么直接写响应，要么交给 `HandlerExceptionResolver`。
 *   - 静态资源与 `OPTIONS` 预检请求要放行，否则前端跨域请求全挂。
 *   - 易错：`ThreadLocal` 没清理导致内存泄漏与串数据；`excludePathPatterns` 写了 `/login` 但实际路径是 `/api/login`。
 * ============================================================
 */

package com.heima.ch26;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class AuthInterceptor {
    /** 模拟 ThreadLocal 里的登录用户 */
    static final ThreadLocal<String> CURRENT_USER = new ThreadLocal<>();
    static final String SECRET = "heima-2026-demo-secret-key-32-bytes!!";

    public static void main(String[] args) {
        UserController controller = new UserControllerImpl();
        UserController proxy = (UserController) Proxy.newProxyInstance(
                AuthInterceptor.class.getClassLoader(),
                new Class<?>[]{UserController.class},
                new InterceptorChain(controller));

        String token = sign("zhangsan");

        System.out.println("=== 白名单接口：不校验 token ===");
        System.out.println("  " + proxy.login("zhangsan", "Passw0rd!"));

        System.out.println();
        System.out.println("=== 受保护接口：带合法 token ===");
        System.out.println("  " + proxy.profile("Bearer " + token));

        System.out.println();
        System.out.println("=== 受保护接口：不带 token ===");
        System.out.println("  " + proxy.profile(null));

        System.out.println();
        System.out.println("=== 受保护接口：token 被改过 ===");
        System.out.println("  " + proxy.profile("Bearer " + token.substring(0, token.length() - 2) + "xx"));

        System.out.println();
        System.out.println("=== 请求结束后 ThreadLocal 已清理 ===");
        System.out.println("  CURRENT_USER = " + CURRENT_USER.get());

        System.out.println();
        System.out.println("=== 拦截器注册 ===");
        System.out.println("""
                @Configuration
                public class WebConfig implements WebMvcConfigurer {
                    @Override
                    public void addInterceptors(InterceptorRegistry registry) {
                        registry.addInterceptor(new AuthInterceptor())
                                .addPathPatterns("/**")
                                .excludePathPatterns("/login", "/register", "/error", "/files/**");
                    }
                }
                """);
        System.out.println("=== 与 Filter 的分工 ===");
        Map<String, String> cmp = new LinkedHashMap<>();
        cmp.put("Filter", "Servlet 规范，能改 request/response，拿不到 HandlerMethod");
        cmp.put("Interceptor", "Spring MVC，能拿到方法上的注解，适合鉴权与日志");
        cmp.put("AOP 切面", "方法级，能拿到参数与返回值，适合业务埋点");
        cmp.forEach((k, v) -> System.out.printf("  %-12s %s%n", k, v));
    }

    /** 模拟控制器 */
    interface UserController {
        String login(String username, String password);

        String profile(String authorization);
    }

    static class UserControllerImpl implements UserController {
        @Override
        public String login(String username, String password) {
            return "200 登录成功，token=" + sign(username);
        }

        @Override
        public String profile(String authorization) {
            return "200 当前用户=" + CURRENT_USER.get();
        }
    }

    /** 模拟 HandlerInterceptor：preHandle -> 目标方法 -> afterCompletion */
    static class InterceptorChain implements InvocationHandler {
        private static final List<String> WHITE_LIST = List.of("login", "register");
        private final Object target;

        InterceptorChain(Object target) {
            this.target = target;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            // ---- preHandle ----
            if (!WHITE_LIST.contains(method.getName())) {
                String header = args.length > 0 && args[0] instanceof String ? (String) args[0] : null;
                if (header == null || !header.startsWith("Bearer ")) {
                    return "401 未登录";
                }
                try {
                    CURRENT_USER.set(parseSubject(header.substring(7)));
                } catch (RuntimeException e) {
                    return "401 " + e.getMessage();
                }
            }
            try {
                // ---- 目标方法 ----
                return method.invoke(target, args);
            } finally {
                // ---- afterCompletion ----
                CURRENT_USER.remove();
            }
        }
    }

    static String sign(String username) {
        String h = b64("{\"alg\":\"HS256\"}");
        String p = b64("{\"sub\":\"" + username + "\"}");
        return h + "." + p + "." + b64(hmac(h + "." + p));
    }

    static String parseSubject(String token) {
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new SecurityException("token 格式错误");
        }
        if (!b64(hmac(parts[0] + "." + parts[1])).equals(parts[2])) {
            throw new SecurityException("token 无效");
        }
        return new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8)
                .replaceAll(".*\"sub\":\"([^\"]+)\".*", "$1");
    }

    static String b64(String s) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    static String b64(byte[] b) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    static byte[] hmac(String data) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(
                    SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 为什么拦截器里抛出的异常不会被 `@RestControllerAdvice` 捕获？给出两种处理办法。
 * 2. `preHandle` 返回 `false` 和抛异常有什么区别？分别适合什么场景。
 * 3. 前后端分离项目里 `OPTIONS` 预检请求为什么必须放行？不放行会看到什么现象。
 * ============================================================
 */
