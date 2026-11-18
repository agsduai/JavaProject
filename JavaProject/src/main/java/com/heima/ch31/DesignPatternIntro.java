/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：31-01
 * 课名：设计模式概览与六大原则
 * ------------------------------------------------------------
 * 知识点：
 *   - 设计模式是"被反复验证过的、针对特定问题的解决方案模板"，它不是语法，而是一套**命名与沟通工具**：说出"这里用策略"比描述三百字更快。
 *   - GoF 23 种按目的分三类：**创建型** 5 种（单例、工厂方法、抽象工厂、建造者、原型）、**结构型** 7 种（代理、装饰、适配器、外观、桥接、组合、享元）、**行为型** 11 种（策略、状态、观察者、责任链、模板方法、命令、迭代器、中介者、备忘录、访问者、解释器）。
 *   - 六大原则：**单一职责 SRP**（一个类只有一个变化原因）、**开闭 OCP**（对扩展开放、对修改关闭）、**里氏替换 LSP**（子类可替换父类且不破坏语义）、**接口隔离 ISP**（小接口优于胖接口）、**依赖倒置 DIP**（依赖抽象不依赖实现）、**迪米特 LoD**（最少知道原则）。
 *   - 原则是权衡工具，不是教条：给一个只有 3 行的类写"接口 + 工厂 + 配置"，抽象成本远高于收益，这叫**过度设计**。
 *   - 认场景比背结构重要：看到"按类型 if/else 分支"→ 策略或工厂；看到"创建过程参数多且可选"→ 建造者；看到"不改源码要加行为"→ 代理或装饰；看到"一长串 if 逐级判断"→ 责任链。
 *   - 依赖倒置的完整含义有两层：**高层模块不依赖低层模块，两者都依赖抽象**；**抽象不依赖细节，细节依赖抽象**。只抽了接口、却仍在业务类里 `new` 具体实现，倒置只做了一半。
 *   - 里氏替换最容易被违反：子类抛父类没声明的受检异常、子类把 `add` 改成"不支持"、子类收窄参数范围，都会让"面向父类编程"的代码在运行期崩掉。
 *   - 迪米特法则的实操判断：方法里出现 `a.getB().getC().doSomething()` 这种连续两跳，就说明"知道了太多"，应让 `a` 自己暴露一个方法。
 *   - 易错：把"用上模式"当成绩（面试能背、代码更绕）；模式套错场景（用装饰器做权限校验、用单例装全局可变状态）；以为模式能替代测试。
 * ============================================================
 */

package com.heima.ch31;

import java.util.ArrayList;
import java.util.List;

// ==================== 示例代码 ====================

public class DesignPatternIntro {
    /** 变化点抽成接口：接口隔离 + 依赖倒置的落点 */
    interface Discount {
        String name();

        double apply(double price);
    }

    static class FullReduction implements Discount {
        public String name() {
            return "满100减20";
        }

        public double apply(double price) {
            return price >= 100 ? price - 20 : price;
        }
    }

    static class PercentOff implements Discount {
        public String name() {
            return "全场9折";
        }

        public double apply(double price) {
            return price * 0.9;
        }
    }

    /** 新增活动只加一个类，不改 Checkout —— 开闭原则 */
    static class NewUserCoupon implements Discount {
        public String name() {
            return "新人立减15";
        }

        public double apply(double price) {
            return Math.max(0, price - 15);
        }
    }

    /** 违规版：按类型分支，每加一种活动都要改这里，且要回归所有旧分支 */
    static class BadCheckout {
        double total(int type, double price) {
            if (type == 1) {
                return price >= 100 ? price - 20 : price;
            } else if (type == 2) {
                return price * 0.9;
            } else if (type == 3) {
                return Math.max(0, price - 15);
            }
            return price;
        }
    }

    /** 重构版：只依赖抽象，折扣可以任意组合、任意排序 */
    static class Checkout {
        private final List<Discount> chain = new ArrayList<>();

        Checkout add(Discount d) {
            chain.add(d);
            return this;
        }

        double total(double price) {
            double p = price;
            for (Discount d : chain) {
                double next = round2(d.apply(p));
                System.out.printf("    - %-10s %7.2f -> %7.2f%n", d.name(), p, next);
                p = next;
            }
            return p;
        }
    }

    static double round2(double v) {
        return Math.round(v * 100) / 100.0;
    }

    public static void main(String[] args) {
        System.out.println("=== 违规版：类型分支 ===");
        BadCheckout bad = new BadCheckout();
        System.out.printf("  type=1 100元 -> %.2f%n", bad.total(1, 100));
        System.out.printf("  type=2 100元 -> %.2f%n", bad.total(2, 100));
        System.out.println("  问题：想加 type=4，必须改 BadCheckout 源码，还得回归 type=1/2/3");

        System.out.println();
        System.out.println("=== 重构版：组合折扣链 ===");
        Checkout ck = new Checkout()
                .add(new FullReduction())
                .add(new PercentOff())
                .add(new NewUserCoupon());
        System.out.println("  原价 200.00，依次应用：");
        System.out.printf("  最终应付 %.2f%n", ck.total(200));

        System.out.println();
        System.out.println("=== 六大原则速查 ===");
        String[][] principles = {
                {"单一职责 SRP", "一个类只有一个变化原因", "UserService 里既校验又发邮件"},
                {"开闭 OCP", "对扩展开放、对修改关闭", "加活动要改 if/else"},
                {"里氏替换 LSP", "子类可替换父类不破坏语义", "子类抛父类没声明的异常"},
                {"接口隔离 ISP", "小接口优于胖接口", "一个 20 个方法的 UserDao"},
                {"依赖倒置 DIP", "依赖抽象不依赖实现", "业务类里 new MySQLDao()"},
                {"迪米特 LoD", "最少知道原则", "a.getB().getC().doIt()"}
        };
        for (String[] r : principles) {
            System.out.printf("  %-14s %-26s 反例：%s%n", r[0], r[1], r[2]);
        }
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 给 `Checkout` 增加"第二件半价"活动，要求不改动任何已有类，说明这体现了哪条原则。
 * 2. 找一个你写过的类，列出它的"变化原因"，判断是否违反单一职责，并给出拆分方案。
 * 3. 举一个"子类替换父类后语义被破坏"的例子，说明为什么编译期发现不了。
 * ============================================================
 */
