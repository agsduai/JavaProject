/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：24-08
 * 课名：全局异常处理与拦截器
 * ------------------------------------------------------------
 * 知识点：
 *   - `@RestControllerAdvice` + `@ExceptionHandler` 集中处理异常，避免每个 Controller 都写 try-catch。
 *   - 处理顺序：**更具体的异常类型优先**，所以 `BusinessException` 的处理器会先于 `Exception` 的处理器命中。
 *   - 自定义业务异常 `BusinessException` 携带 `code`，配合统一响应体返回给前端。
 *   - `HandlerInterceptor` 三个方法：`preHandle`（请求前，返回 `false` 中断）、`postHandle`（Controller 之后、视图渲染前）、`afterCompletion`（请求完全结束后，必执行）。
 *   - 拦截器常用于：登录校验、权限判断、请求耗时统计、链路 traceId 注入。
 *   - 拦截器 vs 过滤器：过滤器属于 Servlet 规范、在 DispatcherServlet 之前；拦截器属于 Spring MVC、能拿到 Handler 信息。
 *   - 易错：`afterCompletion` 在 `preHandle` 返回 `false` 时**不会执行**（只有 `preHandle` 返回 `true` 才会进后两个）。
 * ============================================================
 */

package com.heima.ch24;

import java.util.ArrayList;
import java.util.List;

// ==================== 示例代码 ====================

public class ExceptionAndInterceptor {
    public static void main(String[] args) {
        StudentController controller = new StudentController();

        System.out.println("=== 正常请求 ===");
        System.out.println(controller.findById(1));
        System.out.println();

        System.out.println("=== 资源不存在（业务异常）===");
        System.out.println(controller.findById(99));
        System.out.println();

        System.out.println("=== 非法参数（参数异常）===");
        System.out.println(controller.findById(-1));
        System.out.println();

        System.out.println("=== 未预期异常（兜底）===");
        System.out.println(controller.broken());
        System.out.println();

        System.out.println("=== 拦截器执行顺序 ===");
        AuthInterceptor interceptor = new AuthInterceptor();
        String token = "valid-token";
        boolean pass = interceptor.preHandle("/api/students", token);
        if (pass) {
            System.out.println("    [Controller] 处理业务");
            interceptor.postHandle("/api/students");
        }
        interceptor.afterCompletion("/api/students", pass);
        System.out.println();

        System.out.println("=== token 缺失时被拦截 ===");
        pass = interceptor.preHandle("/api/students", null);
        interceptor.afterCompletion("/api/students", pass);
        System.out.println("  -> preHandle 返回 false，Controller 不执行，postHandle 也不执行");
    }

    // ---------- 统一响应 ----------
    record Result<T>(int code, String message, T data) {
        static <T> Result<T> ok(T data) {
            return new Result<>(200, "success", data);
        }

        static <T> Result<T> fail(int code, String message) {
            return new Result<>(code, message, null);
        }
    }

    // ---------- 自定义异常 ----------
    static class BusinessException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        final int code;

        BusinessException(int code, String message) {
            super(message);
            this.code = code;
        }
    }

    // ---------- 全局异常处理器（模拟 @RestControllerAdvice）----------
    static class GlobalExceptionHandler {
        // 更具体的异常优先命中
        Result<String> handleBusiness(BusinessException e) {
            System.out.println("    [Advice] 命中 BusinessException 处理器");
            return Result.fail(e.code, e.getMessage());
        }

        Result<String> handleIllegalArgument(IllegalArgumentException e) {
            System.out.println("    [Advice] 命中 IllegalArgumentException 处理器");
            return Result.fail(400, "参数不合法：" + e.getMessage());
        }

        Result<String> handleAll(Exception e) {
            System.out.println("    [Advice] 命中 Exception 兜底处理器");
            return Result.fail(500, "服务器开小差了，请稍后重试");
        }
    }

    // ---------- Controller ----------
    static class StudentController {
        private final GlobalExceptionHandler advice = new GlobalExceptionHandler();
        private final List<String> db = new ArrayList<>(List.of("张三", "李四"));

        Result<String> findById(int id) {
            try {
                if (id <= 0) {
                    throw new IllegalArgumentException("id 必须大于 0");
                }
                if (id > db.size()) {
                    throw new BusinessException(404, "学生不存在，id=" + id);
                }
                return Result.ok(db.get(id - 1));
            } catch (BusinessException e) {
                return advice.handleBusiness(e);
            } catch (IllegalArgumentException e) {
                return advice.handleIllegalArgument(e);
            }
        }

        Result<String> broken() {
            try {
                throw new NullPointerException("模拟空指针");
            } catch (Exception e) {
                return advice.handleAll(e);
            }
        }
    }

    // ---------- 拦截器 ----------
    static class AuthInterceptor {
        boolean preHandle(String path, String token) {
            System.out.println("    [Interceptor] preHandle  " + path + "  token=" + token);
            if (token == null || token.isBlank()) {
                System.out.println("    [Interceptor] 未携带 token，返回 401 并中断请求");
                return false;
            }
            return true;
        }

        void postHandle(String path) {
            System.out.println("    [Interceptor] postHandle  " + path + "（Controller 已执行完）");
        }

        void afterCompletion(String path, boolean pass) {
            if (pass) {
                System.out.println("    [Interceptor] afterCompletion  " + path + "（请求结束，必执行）");
            } else {
                System.out.println("    [Interceptor] afterCompletion 不执行（preHandle 返回了 false）");
            }
        }
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 写一个 `GlobalExceptionHandler`，处理 `BusinessException`、`MethodArgumentNotValidException` 和 `Exception` 三类。
 * 2. 实现一个统计接口耗时的拦截器，在 `afterCompletion` 里打印耗时。
 * 3. 说明拦截器和过滤器的区别，登录校验更适合用哪一个，为什么。
 * ============================================================
 */
