/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 课号：实操 8-02
 * 课名：银行账户类
 * ------------------------------------------------------------
 * 知识点：
 *   - 【题目要求】设计 `Account`：账号、户主、余额（private，余额不能为负）。提供 `deposit(double)`、`withdraw(double)`（余额不足提示并拒绝）、`transfer(Account target, double amount)`（从当前账户转到目标账户，余额不足则失败）。主方法创建两个账户，演示存款、取款、转账，并打印双方余额。
 *   - 【考核点】封装、对象作参数、方法间调用、数据安全、返回值设计。
 *   - 【易错点】转账应先校验再扣款，避免“扣了钱没到账”；余额比较不要用 `==`。
 *   - 【提示】`transfer` 内部可复用 `withdraw` 与 `deposit`，但要注意失败时的一致性。
 * ============================================================
 */

package com.heima.practice.advanced;

// ==================== 示例代码 ====================

// 技术示范（非本题答案）：对象作为方法参数
public class BankAccount {
    private double money;

    public BankAccount(double money) {
        this.money = money;
    }

    public void add(double amount) {
        money += amount;
    }

    public double getMoney() {
        return money;
    }

    public static void main(String[] args) {
        BankAccount a = new BankAccount(100);
        BankAccount b = new BankAccount(50);
        a.add(b.getMoney() / 2);
        System.out.println(a.getMoney());
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 增加交易流水记录（用 `String[]` 或 `ArrayList` 保存），每次操作后追加，主方法输出最近 5 条。
 * 2. 把 `transfer` 改为返回 `boolean`，失败时输出具体原因（余额不足/金额非法/目标账户为空）。
 * 3. 设计 `AccountTest` 模拟 10 次随机存取款（金额 1~200），输出最终余额、成功次数与失败次数。
 * 
 * 
 * ## 第9章 面向对象原理篇
 * ============================================================
 */
