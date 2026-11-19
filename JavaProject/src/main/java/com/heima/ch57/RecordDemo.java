/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：57-02
 * 课名：record：值语义与不可变设计
 * ------------------------------------------------------------
 * 知识点：
 *   - **record 是"不可变数据载体"的语言级支持**：`record Point(int x, int y)` 自动生成**全参构造、访问器（注意是 `x()` 不是 `getX()`）、equals/hashCode/toString**——替代 50 行样板。**它是类不是语法糖**：可以由编译器校验的"值对象"形状。
 *   - **紧凑构造器做校验**：`record Money(long cents) { Money { if (cents < 0) throw ...; } }`——**校验收在类型内部**（49-02 值对象不变量思想的语言级实现）。
 *   - **record 是浅不可变的**：字段 final 不能改，但**若字段是可变对象（数组/List），内容仍可变**——防御性拷贝是使用者的责任（`List.copyOf`）；**record 不适合做"带行为变化的实体"**（需要可变状态用普通类）。
 *   - **record 实现了值语义的比较**：`equals` 按字段值（不是引用）——**可以直接用作 Map 的 key 与 Set 元素**（49-02 值对象做 key 的场景）；但含数组字段时 equals 用 `Arrays.equals` 语义需自己覆盖。
 *   - **record 与其它特性协作**：record 实现接口（如 `Comparable`）、record 作为 sealed 的许可子类、record 嵌套 record（57-05 模式匹配解构）。
 *   - **适用判据**：DTO/事件/消息/坐标/金额/结果对象——**凡是"一组不可变的命名值"都用 record**；需要继承体系或可变状态则用普通类。
 *   - 易错点：给 record 加 getter 风格方法（`getX()` 与访问器并存造成混乱）；把可变 List 直接存进 record（外部仍能改）；record 里放耗时/带副作用逻辑（应只是数据 + 轻校验）。
 * ============================================================
 */

package com.heima.ch57;

import java.util.List;
import java.util.Objects;

// ==================== 示例代码 ====================

public class RecordDemo {
    /** 值对象：金额（分）+ 币种，构造即校验 */
    record Money(long cents, String currency) {
        Money {
            if (cents < 0) {
                throw new IllegalArgumentException("金额不能为负");
            }
            Objects.requireNonNull(currency, "币种不能为空");
        }

        Money plus(Money other) {
            if (!currency.equals(other.currency())) {
                throw new IllegalArgumentException("币种不同不可相加");
            }
            return new Money(cents + other.cents(), currency);
        }

        String display() {
            return String.format("%d.%02d %s", cents / 100, cents % 100, currency);
        }
    }

    /** 防御性拷贝：List 字段用 List.copyOf 冻结 */
    record Order(String id, List<String> items) {
        Order {
            items = List.copyOf(items);        // 紧凑构造器里赋值即生效
        }
    }

    public static void main(String[] args) {
        Money a = new Money(1999, "CNY");
        Money b = new Money(500, "CNY");
        System.out.println("  相加：" + a.plus(b).display());
        try {
            new Money(-1, "CNY");
        } catch (IllegalArgumentException e) {
            System.out.println("  校验拦截：" + e.getMessage());
        }

        List<String> source = new java.util.ArrayList<>(List.of("sku-1", "sku-2"));
        Order order = new Order("o-1", source);
        source.add("sku-3");                   // 改外部列表
        System.out.println("  record 内是副本：" + order.items());
        System.out.println("  equals 按值：" + new Money(100, "CNY").equals(new Money(100, "CNY")));
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 把项目里一个 30 行以上的 DTO（含构造/getter/equals）改写为 record，统计代码行数变化并用单测保证行为等价。
 * 2. 实现 `record Range(int from, int to)`：紧凑构造器校验 `from <= to`，提供 `contains(int)` 与 `intersect(Range)`。
 * 3. 思考题：为什么 record 的访问器是 `x()` 而不是 `getX()`？从"值对象的纯数据语义"与 JavaBean 惯例的差异分析。
 * ============================================================
 */
