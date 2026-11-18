/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：19-06
 * 课名：注解的定义与使用
 * ------------------------------------------------------------
 * 知识点：
 *   - 注解（Annotation）是 JDK5 引入的元数据，用 `@注解名` 标记在类、方法、字段、参数上，本身不影响程序逻辑。
 *   - 定义注解：`public @interface MyAnnotation { String value() default ""; int count() default 1; }`。
 *   - 元注解用于修饰注解：`@Target` 指定作用位置，`@Retention` 指定保留阶段，`@Documented`、`@Inherited`。
 *   - `@Retention` 三档：`SOURCE`（源码，编译丢弃）、`CLASS`（默认，保留到 class 文件）、`RUNTIME`（运行时，可被反射读取）。
 *   - 注解属性类型只能是基本类型、String、Class、枚举、注解以及它们的数组。
 *   - 易错：只有 `RetentionPolicy.RUNTIME` 的注解才能通过反射读取，写框架时最常犯的错就是漏写这一项。
 * ============================================================
 */

package com.heima.ch19;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;

// ==================== 示例代码 ====================

// 自定义注解：运行时保留，作用于方法
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@interface LogInfo {
    String value() default "";        // 默认属性，使用时可省略属性名

    int level() default 1;

    String[] tags() default {};
}

class AnnotationDefineOrderService {
    @LogInfo(value = "创建订单", level = 2, tags = {"order", "create"})
    public void createOrder(String orderId) {
        System.out.println("创建订单：" + orderId);
    }

    @LogInfo("查询订单")
    public void queryOrder(String orderId) {
        System.out.println("查询订单：" + orderId);
    }

    public void noAnnotation() {
        System.out.println("没有注解的方法");
    }
}

public class AnnotationDefine {
    public static void main(String[] args) throws Exception {
        Class<AnnotationDefineOrderService> clazz = AnnotationDefineOrderService.class;
        for (Method m : clazz.getDeclaredMethods()) {
            // 判断方法上是否有指定注解
            if (m.isAnnotationPresent(LogInfo.class)) {
                LogInfo info = m.getAnnotation(LogInfo.class);
                System.out.println("方法：" + m.getName()
                        + "，描述：" + info.value()
                        + "，级别：" + info.level()
                        + "，标签：" + String.join(",", info.tags()));
            } else {
                System.out.println("方法：" + m.getName() + "，无注解");
            }
        }
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 定义一个 `@Author(name, date)` 注解，标注在类和方法上，用反射读取并打印注解信息。
 * 2. 定义一个 `@Range(min, max)` 注解用于校验数值字段，编写方法检查对象的字段值是否在范围内。
 * 3. 说明 `@Retention(RetentionPolicy.SOURCE/CLASS/RUNTIME)` 三者的区别，并指出 Spring 的 `@Autowired` 属于哪一种。
 * ============================================================
 */
