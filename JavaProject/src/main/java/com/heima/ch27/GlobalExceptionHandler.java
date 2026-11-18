/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：27-04
 * 课名：统一异常处理与错误码
 * ------------------------------------------------------------
 * 知识点：
 *   - 三类异常分开：`BusinessException`（可预期，带业务码，比如"库存不足"）、参数校验异常（400）、未预期异常（500，必须打完整堆栈但不返回给前端）。
 *   - 统一出口：Spring 里用 `@RestControllerAdvice` + `@ExceptionHandler`，一处把异常转成 `{code,msg,data}`，Controller 里就不用到处 `try-catch`。
 *   - 错误码要稳定：`1001 用户不存在`、`2001 库存不足`、`2002 重复下单`，前端可以按码做不同交互（弹窗 / 跳转 / 静默重试），文案改了不影响前端。
 *   - 不要吞异常：`catch (Exception e) { }` 会让线上问题彻底消失；至少 `log.error("...", e)`。
 *   - 区分"给用户看的消息"和"给开发看的消息"：`msg` 面向用户要友好（"库存不足，仅剩 2 件"），日志里要有 traceId、参数、堆栈。
 *   - 包装第三方异常：`catch (SQLException e) { throw new BusinessException(5001, "数据访问失败", e); }`，保留 cause 链，否则排查时只剩一句"系统错误"。
 *   - 易错：把 `e.printStackTrace()` 当日志（生产环境进不了日志文件）；把堆栈信息直接返回给前端（泄露表名、路径）；`finally` 里 return 覆盖掉异常。
 * ============================================================
 */

package com.heima.ch27;

import java.util.LinkedHashMap;
import java.util.Map;

// ==================== 示例代码 ====================

public class GlobalExceptionHandler {
    /** 业务异常：带稳定错误码，可预期 */
    static class BusinessException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        final int code;

        BusinessException(int code, String message) {
            super(message);
            this.code = code;
        }

        BusinessException(int code, String message, Throwable cause) {
            super(message, cause);
            this.code = code;
        }
    }

    /** 参数校验异常 */
    static class ValidationException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        ValidationException(String message) {
            super(message);
        }
    }

    public static void main(String[] args) {
        System.out.println("=== 业务异常：库存不足 ===");
        System.out.println("  " + call(() -> buy(1L, 5)));

        System.out.println();
        System.out.println("=== 业务异常：商品不存在 ===");
        System.out.println("  " + call(() -> buy(999L, 1)));

        System.out.println();
        System.out.println("=== 参数校验异常 ===");
        System.out.println("  " + call(() -> buy(1L, 0)));

        System.out.println();
        System.out.println("=== 未预期异常：不泄露细节 ===");
        System.out.println("  " + call(() -> {
            throw new NullPointerException("item.name 为 null");
        }));

        System.out.println();
        System.out.println("=== 第三方异常被包装，cause 链保留 ===");
        try {
            wrapAndThrow();
        } catch (BusinessException e) {
            System.out.println("  对外: code=" + e.code + " msg=" + e.getMessage());
            System.out.println("  对内: cause=" + e.getCause());
        }

        System.out.println();
        System.out.println("=== 错误码表 ===");
        Map<Integer, String> codes = new LinkedHashMap<>();
        codes.put(400, "参数格式错误");
        codes.put(401, "未登录");
        codes.put(403, "无权限");
        codes.put(1001, "用户不存在");
        codes.put(2001, "库存不足");
        codes.put(2002, "重复下单");
        codes.put(500, "服务器内部错误");
        codes.forEach((k, v) -> System.out.printf("  %-6d %s%n", k, v));
    }

    /** 模拟 Controller 外层：异常统一在这里转成响应 */
    static Map<String, Object> call(Runnable action) {
        try {
            action.run();
            return result(200, "ok");
        } catch (BusinessException e) {
            log("WARN", "业务异常 code=" + e.code + " msg=" + e.getMessage());
            return result(e.code, e.getMessage());
        } catch (ValidationException e) {
            log("WARN", "参数校验失败: " + e.getMessage());
            return result(400, e.getMessage());
        } catch (RuntimeException e) {
            // 未预期异常：日志留全量堆栈，响应只给通用文案
            log("ERROR", "未预期异常: " + e);
            return result(500, "服务器繁忙，请稍后重试");
        }
    }

    /** 模拟业务：库存只有 2 件 */
    static void buy(Long itemId, int count) {
        if (count <= 0) {
            throw new ValidationException("购买数量必须大于 0");
        }
        if (itemId != 1L) {
            throw new BusinessException(1001, "商品不存在");
        }
        int stock = 2;
        if (count > stock) {
            throw new BusinessException(2001, "库存不足，仅剩 " + stock + " 件");
        }
    }

    static void wrapAndThrow() {
        try {
            throw new IllegalStateException("Connection refused: 127.0.0.1:3306");
        } catch (RuntimeException e) {
            throw new BusinessException(5001, "数据访问失败，请稍后重试", e);
        }
    }

    static void log(String level, String msg) {
        System.out.println("    [" + level + "] " + msg);
    }

    static Map<String, Object> result(int code, String msg) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("msg", msg);
        body.put("data", null);
        return body;
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 为什么未预期异常不能把 `e.getMessage()` 直接返回给前端？举两个会泄露信息的例子。
 * 2. 设计错误码分段规则（比如 1xxx 用户、2xxx 订单、5xxx 系统），说明为什么不要让前端依赖 `msg` 文案做判断。
 * 3. `finally` 里写 `return` 会吞掉异常，写段代码验证一下，并说明为什么编译器只是警告而不报错。
 * ============================================================
 */
