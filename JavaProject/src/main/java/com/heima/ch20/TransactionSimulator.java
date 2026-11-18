/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：20-07
 * 课名：约束与事务
 * ------------------------------------------------------------
 * 知识点：
 *   - 常用约束：`primary key`（主键，唯一且非空）、`not null`、`unique`、`default`、`foreign key`、`check`。
 *   - 主键推荐用 `int auto_increment` 或 `bigint`，业务字段（如身份证号）不适合做主键。
 *   - 事务：一组要么全部成功、要么全部失败的 SQL 操作；InnoDB 支持事务。
 *   - 事务控制：`start transaction` / `begin` 开启，`commit` 提交，`rollback` 回滚。
 *   - ACID 四大特性：原子性（Atomicity）、一致性（Consistency）、隔离性（Isolation）、持久性（Durability）。
 *   - 隔离级别：读未提交、读已提交、可重复读（MySQL 默认）、串行化；级别越高越安全但并发越差。
 *   - 易错：MySQL 的 DDL（建表、删表）会隐式提交事务，不能回滚。
 * ============================================================
 */

package com.heima.ch20;

// ==================== 示例代码 ====================

/**
 * 事务在 JDBC 中的写法预演：关闭自动提交 -> 执行多条 SQL -> 全部成功才 commit。
 * 这里用内存账户模拟，逻辑与真实数据库完全一致。
 */
public class TransactionSimulator {
    static class Account {
        String name;
        double balance;

        Account(String name, double balance) {
            this.name = name;
            this.balance = balance;
        }
    }

    // 模拟一张表
    private static final Account A = new Account("张三", 1000);
    private static final Account B = new Account("李四", 500);
    // 事务未提交前的改动先暂存，提交时才写回
    private static Double pendingA, pendingB;

    public static void main(String[] args) {
        boolean ok = transfer(200);
        System.out.println("转账结果：" + ok);
        System.out.println("张三余额：" + A.balance + "，李四余额：" + B.balance);

        // 余额不足 -> 回滚
        boolean fail = transfer(999999);
        System.out.println("超额转账结果：" + fail);
        System.out.println("张三余额：" + A.balance + "，李四余额：" + B.balance);
    }

    /** 返回 true 表示已提交，false 表示已回滚 */
    static boolean transfer(double money) {
        // start transaction
        pendingA = A.balance;
        pendingB = B.balance;
        try {
            if (pendingA < money) {
                throw new IllegalStateException("余额不足");
            }
            pendingA -= money;      // 扣款
            pendingB += money;      // 入账
            if (pendingB < 0) {
                throw new IllegalStateException("入账异常");
            }
            // commit
            A.balance = pendingA;
            B.balance = pendingB;
            return true;
        } catch (Exception e) {
            // rollback：丢弃暂存值，原数据不变
            pendingA = null;
            pendingB = null;
            System.out.println("  回滚原因：" + e.getMessage());
            return false;
        }
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 创建一张 `orders` 表，给订单编号加唯一约束、给金额加非空约束、给状态加默认值，插入数据验证约束是否生效。
 * 2. 用事务模拟银行转账：A 账户转 300 给 B 账户，中途故意制造异常并回滚，验证两个账户余额都未变化。
 * 3. 写出 ACID 四个特性的含义，并说明"可重复读"隔离级别能解决什么问题。
 * ============================================================
 */
