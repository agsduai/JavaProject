/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：21-06
 * 课名：JDBC事务控制
 * ------------------------------------------------------------
 * 知识点：
 *   - JDBC 默认自动提交：每条 SQL 执行完立即生效，无法整体回滚。
 *   - 手动事务：`conn.setAutoCommit(false)` 开启 → 多条 SQL → `conn.commit()` 提交 / `conn.rollback()` 回滚。
 *   - 事务必须作用在同一个 `Connection` 上，换连接就不在同一事务里。
 *   - 转账是经典场景：扣款和入账必须同时成功或同时失败。
 *   - 可以在 `catch` 中 `rollback`，在 `finally` 中恢复 `setAutoCommit(true)` 并关闭连接。
 *   - 易错：`conn.close()` 前未提交且未回滚时，未提交的数据会丢失；`DDL` 语句会隐式提交。
 * ============================================================
 */

package com.heima.ch21;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

// ==================== 示例代码 ====================

public class JdbcTransaction {
    private static final String URL =
            "jdbc:mysql://localhost:3306/heima_db?useSSL=false&serverTimezone=Asia/Shanghai";
    private static final String USER = "root";
    private static final String PASSWORD = "1234";

    public static void main(String[] args) {
        try {
            run();
        } catch (Exception e) {
            System.out.println("运行失败：" + e.getMessage());
            System.out.println("请检查：1) 是否已引入 mysql-connector-j 依赖；"
                    + "2) MySQL 服务是否已启动；3) 连接四要素（URL/用户名/密码）是否正确。");
        }
    }

    private static void run() throws Exception {
        // 正常转账
        boolean ok = transfer("张三", "李四", 200);
        System.out.println("正常转账结果：" + ok);

        // 余额不足，应回滚
        boolean fail = transfer("张三", "李四", 999999);
        System.out.println("超额转账结果：" + fail);

        printBalances();
    }

    /**
     * 转账：扣款与入账在同一事务中，任一步失败全部回滚。
     */
    private static boolean transfer(String from, String to, double money) {
        String outSql = "update account set balance = balance - ? where name = ? and balance >= ?";
        String inSql = "update account set balance = balance + ? where name = ?";

        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD)) {
            conn.setAutoCommit(false);          // 开启事务
            try (PreparedStatement out = conn.prepareStatement(outSql);
                 PreparedStatement in = conn.prepareStatement(inSql)) {

                out.setDouble(1, money);
                out.setString(2, from);
                out.setDouble(3, money);
                int rows = out.executeUpdate();

                if (rows == 0) {
                    // 扣款失败（余额不足或账户不存在）
                    conn.rollback();
                    System.out.println("扣款失败，已回滚");
                    return false;
                }

                in.setDouble(1, money);
                in.setString(2, to);
                in.executeUpdate();

                conn.commit();                  // 两步都成功才提交
                return true;
            } catch (Exception e) {
                conn.rollback();                // 出现异常整体回滚
                System.out.println("发生异常，已回滚：" + e.getMessage());
                return false;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (Exception e) {
            System.out.println("连接异常：" + e.getMessage());
            return false;
        }
    }

    private static void printBalances() {
        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD);
             PreparedStatement ps = conn.prepareStatement(
                     "select name, balance from account order by id");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                System.out.printf("%s 余额：%.2f%n", rs.getString("name"), rs.getDouble("balance"));
            }
        } catch (Exception e) {
            System.out.println("查询失败：" + e.getMessage());
        }
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 模拟下单扣库存：库存充足则扣减库存并生成订单，库存不足则回滚，两个操作放在同一事务中。
 * 2. 故意在入账后抛出异常，验证 `rollback` 后扣款也被撤销。
 * 3. 说明 JDBC 默认自动提交会带来什么问题，以及为什么事务必须使用同一个 `Connection`。
 * ============================================================
 */
