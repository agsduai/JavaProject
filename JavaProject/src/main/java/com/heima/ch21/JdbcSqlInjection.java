/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：21-04
 * 课名：SQL注入与PreparedStatement
 * ------------------------------------------------------------
 * 知识点：
 *   - SQL 注入：用户输入被拼进 SQL 后改变了原语句语义，典型如输入 `' or '1'='1` 绕过密码校验。
 *   - 根本原因：`Statement` 把"数据"当成了"SQL 语法"的一部分。
 *   - `PreparedStatement` 预编译 SQL，用 `?` 占位，参数通过 `setXxx` 绑定，数据永远只当数据处理。
 *   - 绑定方法：`setInt`、`setString`、`setDouble`、`setObject`、`setDate`；序号从 1 开始。
 *   - 额外收益：SQL 只编译一次可重复执行、自动处理引号与类型转换、可读性更好。
 *   - 易错：占位符 `?` 不能用于表名、列名、`order by` 字段，这些位置只能拼接并做白名单校验。
 * ============================================================
 */

package com.heima.ch21;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

// ==================== 示例代码 ====================

public class JdbcSqlInjection {
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
        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD)) {
            String inputName = "张三";
            String evilInput = "' or '1'='1";

            // 反例：字符串拼接，恶意输入会改变 SQL 结构
            String badSql = "select count(*) from jdbc_demo where name = '" + evilInput + "'";
            System.out.println("拼接后的 SQL：" + badSql);
            try (PreparedStatement ps = conn.prepareStatement(badSql);
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    System.out.println("拼接方式查到条数：" + rs.getInt(1) + "（可能被注入）");
                }
            }

            // 正例：占位符 + 参数绑定
            String goodSql = "select id, name, score from jdbc_demo where name = ? and score > ?";
            try (PreparedStatement ps = conn.prepareStatement(goodSql)) {
                ps.setString(1, evilInput);   // 整段输入被当作普通字符串
                ps.setInt(2, 0);
                try (ResultSet rs = ps.executeQuery()) {
                    int count = 0;
                    while (rs.next()) {
                        count++;
                    }
                    System.out.println("预编译方式查到条数：" + count + "（注入无效）");
                }
            }

            // 正常参数查询
            try (PreparedStatement ps = conn.prepareStatement(
                    "select id, name, score from jdbc_demo where name = ?")) {
                ps.setString(1, inputName);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        System.out.println("查到：" + rs.getString("name")
                                + " 分数=" + rs.getInt("score"));
                    }
                }
            }
        }
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 用 `Statement` 写一个登录校验方法，再用 `PreparedStatement` 改写，构造恶意输入验证前者被绕过、后者安全。
 * 2. 写一个按分数区间查询的方法 `List<Student> findByScore(int min, int max)`，使用占位符绑定两个参数。
 * 3. 说明为什么 `?` 不能用于表名和排序字段，并给出安全的替代方案。
 * ============================================================
 */
