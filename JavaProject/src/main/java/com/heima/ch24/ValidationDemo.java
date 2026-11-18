/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：24-07
 * 课名：参数校验与统一响应
 * ------------------------------------------------------------
 * 知识点：
 *   - 统一响应体 `Result<T>` 固定三个字段：`code`（业务码）、`message`（提示）、`data`（数据），前端只按一种格式解析。
 *   - 业务码与 HTTP 状态码不是一回事：HTTP 200 表示"请求被正确处理"，`code=500` 表示"业务处理失败"。
 *   - 参数校验用 Bean Validation：`@NotNull`、`@NotBlank`、`@Min`、`@Max`、`@Size`、`@Email`，配 `@Valid` 或 `@Validated` 触发。
 *   - 校验失败抛 `MethodArgumentNotValidException`，由 `@RestControllerAdvice` 统一捕获后转成 `Result`。
 *   - 分组校验（`@Validated(AddGroup.class)`）让同一个 DTO 在新增和修改时有不同规则。
 *   - 易错：只在 Controller 上加 `@Valid` 而 DTO 字段没有约束注解，等于没校验；嵌套对象校验要加 `@Valid`。
 * ============================================================
 */

package com.heima.ch24;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class ValidationDemo {
    public static void main(String[] args) {
        StudentService service = new StudentService();

        System.out.println("=== 合法请求 ===");
        print(service.create(new StudentDTO("张三", 20, "zhangsan@heima.com")));
        System.out.println();

        System.out.println("=== 姓名空白 ===");
        print(service.create(new StudentDTO("   ", 20, "zhangsan@heima.com")));
        System.out.println();

        System.out.println("=== 年龄越界 ===");
        print(service.create(new StudentDTO("李四", 200, "lisi@heima.com")));
        System.out.println();

        System.out.println("=== 邮箱格式错误 + 年龄越界（多个错误一起返回）===");
        print(service.create(new StudentDTO("王五", -1, "not-an-email")));
        System.out.println();

        System.out.println("=== 统一响应体结构 ===");
        System.out.println("""
                {
                  "code": 200,
                  "message": "success",
                  "data": { "id": 1, "name": "张三" }
                }
                """);
        System.out.println("业务码约定：200 成功、400 参数错误、401 未登录、500 业务异常");
    }

    private static void print(Result<?> r) {
        System.out.println("  code=" + r.code + ", message=" + r.message + ", data=" + r.data);
    }

    // ---------- 统一响应体 ----------
    record Result<T>(int code, String message, T data) {
        static <T> Result<T> ok(T data) {
            return new Result<>(200, "success", data);
        }

        static <T> Result<T> fail(int code, String message) {
            return new Result<>(code, message, null);
        }
    }

    // ---------- DTO ----------
    record StudentDTO(String name, int age, String email) {
    }

    // ---------- Service（含校验逻辑，模拟 @Valid 触发）----------
    static class StudentService {
        private int seq = 0;

        Result<StudentDTO> create(StudentDTO dto) {
            List<String> errors = validate(dto);
            if (!errors.isEmpty()) {
                return Result.fail(400, String.join("; ", errors));
            }
            seq++;
            return Result.ok(new StudentDTO(dto.name().trim(), dto.age(), dto.email()));
        }

        // 对应 Bean Validation 的约束注解
        private List<String> validate(StudentDTO dto) {
            List<String> errors = new ArrayList<>();
            if (dto.name() == null || dto.name().isBlank()) {
                errors.add("姓名不能为空（@NotBlank）");
            } else if (dto.name().length() > 10) {
                errors.add("姓名长度不能超过 10（@Size(max = 10)）");
            }
            if (dto.age() < 1 || dto.age() > 150) {
                errors.add("年龄必须在 1~150 之间（@Min/@Max）");
            }
            if (dto.email() == null || !dto.email().matches("^[\\w.-]+@[\\w.-]+\\.[A-Za-z]{2,}$")) {
                errors.add("邮箱格式不正确（@Email）");
            }
            return errors;
        }
    }

    // ---------- 常见约束注解对照 ----------
    static {
        Map<String, String> table = new LinkedHashMap<>();
        table.put("@NotNull", "不能为 null");
        table.put("@NotBlank", "字符串不能为 null、不能为空、不能全空白");
        table.put("@NotEmpty", "集合/字符串不能为空");
        table.put("@Min/@Max", "数值范围");
        table.put("@Size(min,max)", "长度范围");
        table.put("@Email", "邮箱格式");
        table.put("@Pattern", "正则匹配");
        table.forEach((k, v) -> System.out.println("  " + k + " -> " + v));
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 给 `StudentDTO` 加上完整的约束注解，并在 Controller 上加 `@Valid`，用 Postman 验证错误提示。
 * 2. 用 `@RestControllerAdvice` + `@ExceptionHandler(MethodArgumentNotValidException.class)` 把校验错误转成 `Result`。
 * 3. 说明 `@Valid` 和 `@Validated` 的区别，分组校验应该用哪一个。
 * ============================================================
 */
