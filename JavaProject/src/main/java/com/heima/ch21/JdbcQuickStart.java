/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：21-01
 * 课名：JDBC概述与快速入门
 * ------------------------------------------------------------
 * 知识点：
 *   - JDBC 是一套接口规范（`java.sql` 包），由数据库厂商提供驱动实现，Java 代码只面向接口编程。
 *   - 核心四件套：`DriverManager` 管理驱动与连接、`Connection` 代表一次连接、`Statement` 执行 SQL、`ResultSet` 封装查询结果。
 *   - 连接地址格式：`jdbc:mysql://主机:端口/库名?参数`，常用参数 `useSSL=false`、`serverTimezone=Asia/Shanghai`、`characterEncoding=utf8`。
 *   - 驱动类：MySQL 8.x 用 `com.mysql.cj.jdbc.Driver`，5.x 用 `com.mysql.jdbc.Driver`；JDBC 4.0 之后可省略 `Class.forName`。
 *   - 标准步骤：加载驱动 → 获取连接 → 创建 Statement → 执行 SQL → 处理结果 → 释放资源。
 *   - 易错：`Connection`、`Statement`、`ResultSet` 都是稀缺资源，必须关闭；关闭顺序与打开顺序相反。
 * ============================================================
 */

package com.heima.ch21;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

// ==================== 示例代码 ====================

public class JdbcQuickStart {
    // 数据库连接四要素
    private static final String URL =
            "jdbc:mysql://localhost:3306/heima_db?useSSL=false&serverTimezone=Asia/Shanghai&characterEncoding=utf8";
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
        // 1. 加载驱动（JDBC 4.0 起可省略，显式写出来便于理解）
        Class.forName("com.mysql.cj.jdbc.Driver");

        // 2. 获取连接
        Connection conn = DriverManager.getConnection(URL, USER, PASSWORD);
        System.out.println("连接成功：" + conn);

        // 3. 创建 Statement
        Statement stmt = conn.createStatement();

        // 4. 执行查询
        ResultSet rs = stmt.executeQuery("select id, name, age from student");

        // 5. 处理结果：next() 指向下一行，列可用序号或列名获取
        while (rs.next()) {
            int id = rs.getInt("id");
            String name = rs.getString("name");
            int age = rs.getInt("age");
            System.out.printf("%d\t%s\t%d%n", id, name, age);
        }

        // 6. 释放资源：后开的先关
        rs.close();
        stmt.close();
        conn.close();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 在本地 MySQL 中建好 `heima_db` 库和 `student` 表，用 JDBC 连接并打印表里所有记录的姓名。
 * 2. 把连接地址中的 `serverTimezone` 去掉再运行，观察报错信息并解释原因。
 * 3. 画出 JDBC 六步流程图，标注每一步用到的核心类。
 * ============================================================
 */
