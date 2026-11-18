/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：19-07
 * 课名：反射+注解综合案例
 * ------------------------------------------------------------
 * 知识点：
 *   - 反射 + 注解是框架实现"声明式编程"的核心：注解负责标记意图，反射负责执行逻辑。
 *   - 典型应用：简易测试框架（`@Test`）、参数校验（`@NotNull`、`@Range`）、依赖注入（`@Inject`）、接口文档（`@ApiOperation`）。
 *   - 实现套路：遍历类的字段/方法 → `isAnnotationPresent` 判断注解 → 读取注解属性 → 执行对应逻辑。
 *   - 注解在运行时通过 `getAnnotation` 拿到的是 JDK 动态生成的代理对象，属性值由方法调用返回。
 *   - 这套机制让业务代码保持干净，校验/日志/事务等横切逻辑被"织入"。
 *   - 易错：注解本身不执行任何逻辑，必须配合反射（或注解处理器）才能产生效果。
 * ============================================================
 */

package com.heima.ch19;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

// ==================== 示例代码 ====================

// 校验注解：不能为空
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@interface NotNull {
    String message() default "不能为空";
}

// 校验注解：数值范围
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@interface Range {
    int min() default Integer.MIN_VALUE;

    int max() default Integer.MAX_VALUE;

    String message() default "超出范围";
}

class AnnotationValidateRegisterForm {
    @NotNull(message = "用户名不能为空")
    private String username;

    @NotNull(message = "密码不能为空")
    private String password;

    @Range(min = 1, max = 120, message = "年龄必须在 1~120 之间")
    private int age;

    public AnnotationValidateRegisterForm(String username, String password, int age) {
        this.username = username;
        this.password = password;
        this.age = age;
    }
}

public class AnnotationValidate {
    public static void main(String[] args) throws Exception {
        // 合法数据
        System.out.println("---- 合法对象 ----");
        validate(new AnnotationValidateRegisterForm("张三", "123456", 25));

        // 非法数据
        System.out.println("---- 非法对象 ----");
        validate(new AnnotationValidateRegisterForm(null, "", 200));
    }

    // 通用校验器：反射读取注解并检查
    public static List<String> validate(Object obj) throws Exception {
        List<String> errors = new ArrayList<>();
        Class<?> clazz = obj.getClass();
        for (Field field : clazz.getDeclaredFields()) {
            field.setAccessible(true);
            Object value = field.get(obj);

            if (field.isAnnotationPresent(NotNull.class)) {
                NotNull notNull = field.getAnnotation(NotNull.class);
                if (value == null || value.toString().isBlank()) {
                    errors.add(field.getName() + "：" + notNull.message());
                }
            }

            if (field.isAnnotationPresent(Range.class) && value instanceof Integer) {
                Range range = field.getAnnotation(Range.class);
                int v = (Integer) value;
                if (v < range.min() || v > range.max()) {
                    errors.add(field.getName() + "：" + range.message() + "（当前 " + v + "）");
                }
            }
        }

        if (errors.isEmpty()) {
            System.out.println("校验通过");
        } else {
            errors.forEach(e -> System.out.println("校验失败 -> " + e));
        }
        return errors;
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 定义一个 `@Length(min, max)` 注解校验字符串长度，配合反射写一个通用校验方法。
 * 2. 实现一个简易测试框架：用 `@Test` 标注测试方法，反射遍历并执行所有标注方法，统计成功和失败数量。
 * 3. 综合运用注解和反射，实现一个"简易依赖注入"：用 `@Inject` 标注字段，反射自动为它赋值。
 * ============================================================
 */
