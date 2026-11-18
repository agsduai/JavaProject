/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：21-02
 * 课名：Statement执行DDL与DML
 * ------------------------------------------------------------
 * 知识点：
 *   - `executeUpdate(sql)` 用于 `insert`、`update`、`delete`、`create table` 等，返回受影响的行数。
 *   - `executeQuery(sql)` 用于 `select`，返回 `ResultSet`。
 *   - `execute(sql)` 通用于任意 SQL，返回 `boolean`：`true` 表示有结果集。
 *   - 建表、删表属于 DDL，`executeUpdate` 同样适用，返回 0。
 *   - 拼接 SQL 字符串是入门写法，只适合固定内容，有用户输入时必须换成 `PreparedStatement`。
 *   - 易错：`executeUpdate` 的返回值是"影响行数"，`update` 值没变化时 MySQL 可能返回 0，不能当作失败。
 * ============================================================
 */

package com.heima.ch21;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

// ==================== 示例代码 ====================

public class JdbcStatementDml {
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
        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD);
             Statement stmt = conn.createStatement()) {

            // DDL：建表
            String ddl = """
                    create table if not exists jdbc_demo (
                        id    int primary key auto_increment,
                        name  varchar(20) not null,
                        score int
                    ) engine = InnoDB default charset = utf8mb4
                    """;
            stmt.executeUpdate(ddl);
            System.out.println("建表完成");

            // DML：插入，返回受影响行数
            int insertCount = stmt.executeUpdate(
                    "insert into jdbc_demo (name, score) values ('张三', 90)");
            System.out.println("插入行数：" + insertCount);

            // DML：批量插入（多条语句用分号分隔需开启 allowMultiQueries，这里逐条执行）
            for (int i = 1; i <= 3; i++) {
                stmt.executeUpdate("insert into jdbc_demo (name, score) values ('学生" + i + "', " + (60 + i * 5) + ")");
            }

            // DML：更新
            int updateCount = stmt.executeUpdate(
                    "update jdbc_demo set score = score + 5 where name = '张三'");
            System.out.println("更新行数：" + updateCount);

            // DML：删除
            int deleteCount = stmt.executeUpdate(
                    "delete from jdbc_demo where score < 65");
            System.out.println("删除行数：" + deleteCount);

            // execute 的返回值：true 表示返回的是结果集
            boolean hasResultSet = stmt.execute("select * from jdbc_demo");
            System.out.println("是否有结果集：" + hasResultSet);
        }
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 用 JDBC 在数据库中创建一张 `jdbc_book` 表，再插入 3 条记录，打印每一步的影响行数。
 * 2. 写一个方法 `int updateScore(String name, int delta)`，把指定学生的分数增加指定值并返回影响行数。
 * 3. 说明 `execute`、`executeQuery`、`executeUpdate` 三个方法的区别与适用场景。
 * ============================================================
 */
