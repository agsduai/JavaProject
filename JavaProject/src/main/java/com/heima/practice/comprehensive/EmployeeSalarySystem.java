/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 课号：实操 11-01
 * 课名：员工薪资体系（继承 + 多态 + 抽象类）
 * ------------------------------------------------------------
 * 知识点：
 *   - 【题目要求】定义抽象类 `Employee`：属性 name、baseSalary，抽象方法 `double calcSalary()`，普通方法 `void printInfo()` 输出姓名与薪资。子类：`Manager`（奖金 bonus）、`Salesman`（销售额 sales，提成 5%）、`Intern`（日薪 dayRate × 出勤天数）。用 `Employee[]` 统一遍历输出薪资；对 `Salesman` 额外输出销售额（`instanceof` + 强转）。
 *   - 【考核点】继承、抽象类与抽象方法、方法重写、多态、向上转型、instanceof、父类引用调用子类重写方法。
 *   - 【易错点】抽象类不能 new；子类必须重写全部抽象方法；强转前必须用 instanceof 判断。
 *   - 【提示】`printInfo` 放在父类中调用 `calcSalary()`，即可自动呈现多态效果。
 * ============================================================
 */

package com.heima.practice.comprehensive;

// ==================== 示例代码 ====================

// 技术示范（非本题答案）：抽象类 + 多态数组
abstract class EmployeeSalarySystemShape {
    abstract double area();
}

class EmployeeSalarySystemCircle extends EmployeeSalarySystemShape {
    private double r;

    EmployeeSalarySystemCircle(double r) {
        this.r = r;
    }

    @Override
    double area() {
        return Math.PI * r * r;
    }
}

public class EmployeeSalarySystem {
    public static void main(String[] args) {
        EmployeeSalarySystemShape[] shapes = {new EmployeeSalarySystemCircle(2)};
        for (EmployeeSalarySystemShape s : shapes) {
            System.out.printf("%.2f%n", s.area());
        }
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 增加 `Director` 子类（基础工资 + 股份分红），加入数组并统计公司总薪资。
 * 2. 定义 `Payable` 接口（`double calcSalary()`），让 `Employee` 实现该接口，比较“抽象类 + 接口”的设计差异。
 * 3. 统计各类员工人数与平均薪资（按 `instanceof` 分组），输出格式化的薪资报表。
 * ============================================================
 */
