/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：21-05
 * 课名：PreparedStatement增删改与批量操作
 * ------------------------------------------------------------
 * 知识点：
 *   - 增删改统一用 `executeUpdate()`，返回影响行数；插入后要拿自增主键需指定 `Statement.RETURN_GENERATED_KEYS`。
 *   - 批量操作：`addBatch()` 攒一批，`executeBatch()` 一次提交，返回每条的更新行数数组。
 *   - 批量执行必须在 URL 上加 `rewriteBatchedStatements=true` 才能真正合并成一条多值 SQL，否则性能提升有限。
 *   - 获取自增主键：`ps.getGeneratedKeys()` 得到 `ResultSet`，取第 1 列。
 *   - 批量更新时最好放在一个事务里，避免部分成功部分失败。
 *   - 易错：`addBatch` 前必须 `setXxx` 全部参数；`executeBatch` 后要 `clearBatch` 才能复用。
 * ============================================================
 */

package com.heima.ch21;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

// ==================== 示例代码 ====================

public class JdbcPreparedBatch {
    private static final String URL =
            "jdbc:mysql://localhost:3306/heima_db?useSSL=false&serverTimezone=Asia/Shanghai"
                    + "&rewriteBatchedStatements=true";
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
            // 1. 插入并获取自增主键
            String insertSql = "insert into jdbc_demo (name, score) values (?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(
                    insertSql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, "批量测试");
                ps.setInt(2, 88);
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) {
                        System.out.println("新插入记录主键：" + keys.getInt(1));
                    }
                }
            }

            // 2. 批量插入：一次网络往返提交多条
            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                for (int i = 1; i <= 100; i++) {
                    ps.setString(1, "批量学生" + i);
                    ps.setInt(2, 50 + i % 50);
                    ps.addBatch();
                    if (i % 20 == 0) {
                        int[] counts = ps.executeBatch();
                        System.out.println("本批提交 " + counts.length + " 条");
                        ps.clearBatch();
                    }
                }
                int[] rest = ps.executeBatch();
                System.out.println("剩余提交 " + rest.length + " 条");
            }

            // 3. 批量更新：统一加 5 分
            try (PreparedStatement ps = conn.prepareStatement(
                    "update jdbc_demo set score = score + ? where name like ?")) {
                ps.setInt(1, 5);
                ps.setString(2, "批量学生%");
                ps.addBatch();
                int[] counts = ps.executeBatch();
                System.out.println("批量更新影响行数：" + counts[0]);
            }

            // 4. 单条删除
            try (PreparedStatement ps = conn.prepareStatement(
                    "delete from jdbc_demo where score < ?")) {
                ps.setInt(1, 55);
                System.out.println("删除行数：" + ps.executeUpdate());
            }
        }
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 用批量插入向 `jdbc_book` 表插入 500 条数据，对比"逐条 `executeUpdate`"和"`addBatch`"的耗时。
 * 2. 写一个插入方法，插入成功后返回自增主键，插入失败返回 `-1`。
 * 3. 说明 `rewriteBatchedStatements=true` 的作用，以及不加这个参数时批量插入为什么慢。
 * ============================================================
 */
