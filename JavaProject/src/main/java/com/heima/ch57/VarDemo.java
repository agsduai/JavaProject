/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：57-07
 * 课名：var 与局部变量类型推断
 * ------------------------------------------------------------
 * 知识点：
 *   - **var 是局部变量的类型推断**（Java 10）：`var list = new ArrayList<String>()`——**编译器按初始化表达式推断右侧具体类型**，不是 `Object`、不是动态类型。**只允许局部变量**（方法内、for、try-with-resources），成员变量/参数/返回值禁用。
 *   - **var 的正确用法**：① 右侧类型一目了然时（`var order = orderRepository.findById(id)`）；② 减少泛型噪音（`var map = new HashMap<String, List<Order>>()`）；③ 循环变量（`for (var entry : map.entrySet())`）。**读代码的人不需要跳转就能推断类型**时才用。
 *   - **不该用 var 的场景**：右侧是方法调用且返回类型不直观（`var result = service.process(...)` 读者不知是啥）、数值字面量（`var x = 0` 是 int，可能不是你想的 long）、需要显式宽类型（`List<String> l = new ArrayList<>()` 比 `var l = new ArrayList<String>()` 更灵活）。
 *   - **var 与匿名类/交叉类型的坑**：`var x = new Object() { void f() {} }` 推断为匿名类（可用 f）——**这是唯一能拿到匿名类方法的写法**，但也容易写出局部"黑魔法"。
 *   - **团队规范比技术细节重要**：常见约定是"变量名足够表达类型语义时用 var"（`var orders = ...` 可以，`var d = ...` 不行）——**写进团队规范与 code review 清单**。
 *   - 易错点：以为 var 影响性能（编译期完全静态）；在 lambdavs 里用 var 省略参数类型（需显式标注才能加注解）；用 var 掩盖了复杂返回类型（可读性下降）。
 * ============================================================
 */

package com.heima.ch57;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class VarDemo {
    record Order(String id, long cents) {
    }

    /** 推荐：右侧类型直观，var 消除泛型噪音 */
    static Map<String, List<Order>> groupByUser(List<Order> orders) {
        var result = new HashMap<String, List<Order>>();
        for (var order : orders) {
            result.computeIfAbsent(order.id().substring(0, 2), k -> new java.util.ArrayList<>())
                    .add(order);
        }
        return result;
    }

    /** 不推荐：返回类型不直观，读者需要跳转 */
    static Object risky() {
        return "a string";
    }

    public static void main(String[] args) {
        var orders = List.of(new Order("u1-9001", 6900), new Order("u2-9002", 3900),
                new Order("u1-9003", 12900));
        var grouped = groupByUser(orders);
        for (var entry : grouped.entrySet()) {
            System.out.printf("  %s -> %d 单%n", entry.getKey(), entry.getValue().size());
        }

        var suspicious = risky();            // 推断为 Object：看不出内容
        System.out.println("  var 掩盖类型：" + suspicious.getClass().getSimpleName());

        // 数值字面量的陷阱
        var smallNumber = 0;                 // int，不是 long
        long explicit = 0L;
        System.out.printf("  int=%d long=%d 同一字面量不同声明%n", smallNumber, explicit);
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 审查项目里 10 处 `var` 用法，按"右侧是否直观"分类，把不合适的改回显式类型并记录理由。
 * 2. 写一段代码验证 var 在 for 循环、try-with-resources、匿名类三种场景下的推断结果（用 `getClass()` 输出）。
 * 3. 思考题：为什么 var 不能用于方法参数与返回值？从 API 契约（调用方必须知道类型）与源码可读性分析。
 * ============================================================
 */
