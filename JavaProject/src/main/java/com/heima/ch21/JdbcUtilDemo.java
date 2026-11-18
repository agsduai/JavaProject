/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：21-07
 * 课名：JDBC工具类封装
 * ------------------------------------------------------------
 * 知识点：
 *   - 重复的连接四要素和资源关闭代码应抽到工具类：`JdbcUtil.getConnection()` / `JdbcUtil.close(...)`。
 *   - 配置文件 `db.properties` 放在 `src/main/resources`，用 `Properties` 加载，换库只改配置不改代码。
 *   - 连接池：预先创建若干连接重复使用，避免频繁创建销毁；常见实现 HikariCP（Spring Boot 默认）、Druid。
 *   - 连接池核心参数：初始连接数、最大连接数、最大等待时间、最小空闲数。
 *   - `DataSource`（`javax.sql`）是连接池的统一接口，`dataSource.getConnection()` 用法与 `DriverManager` 一致。
 *   - 易错：工具类中不要用 `static Connection` 保存连接，多线程下会被多个线程共享导致事务串线。
 * ============================================================
 */

package com.heima.ch21;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

// ==================== 示例代码 ====================

public class JdbcUtilDemo {
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
        // 通过工具类获取连接
        try (Connection conn = JdbcUtil.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("select count(*) from jdbc_demo")) {
            if (rs.next()) {
                System.out.println("jdbc_demo 共 " + rs.getInt(1) + " 条记录");
            }
        }

        // 演示配置来源
        Properties p = JdbcUtil.loadConfig();
        System.out.println("当前连接的库：" + p.getProperty("jdbc.url"));
    }
}

/**
 * 简易 JDBC 工具类：负责读取配置、获取连接、静默关闭资源。
 * 生产项目应替换为 HikariCP / Druid 连接池。
 */
class JdbcUtil {
    private static final Properties CONFIG = loadConfig();

    public static Properties loadConfig() {
        Properties p = new Properties();
        // 优先读取 classpath 下的 db.properties，读不到则使用内置默认值
        try (InputStream in = JdbcUtil.class.getClassLoader()
                .getResourceAsStream("db.properties")) {
            if (in != null) {
                p.load(in);
            }
        } catch (Exception e) {
            System.out.println("读取配置文件失败，使用默认配置");
        }
        p.putIfAbsent("jdbc.url",
                "jdbc:mysql://localhost:3306/heima_db?useSSL=false&serverTimezone=Asia/Shanghai");
        p.putIfAbsent("jdbc.username", "root");
        p.putIfAbsent("jdbc.password", "1234");
        p.putIfAbsent("jdbc.driver", "com.mysql.cj.jdbc.Driver");
        return p;
    }

    /** 获取连接 */
    public static Connection getConnection() throws Exception {
        Class.forName(CONFIG.getProperty("jdbc.driver"));
        return DriverManager.getConnection(
                CONFIG.getProperty("jdbc.url"),
                CONFIG.getProperty("jdbc.username"),
                CONFIG.getProperty("jdbc.password"));
    }

    /** 关闭资源，参数可传 null，顺序为 ResultSet → Statement → Connection */
    public static void close(AutoCloseable... resources) {
        for (AutoCloseable r : resources) {
            if (r != null) {
                try {
                    r.close();
                } catch (Exception ignored) {
                    // 关闭异常不向上抛，避免掩盖业务异常
                }
            }
        }
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 把本章前面几课的连接代码改成使用 `JdbcUtil`，统计每个类减少了多少行重复代码。
 * 2. 在 `src/main/resources` 下创建 `db.properties`，把连接四要素写进去，让工具类读取，验证改配置即可切换数据库。
 * 3. 说出连接池相比 `DriverManager` 每次新建连接的优势，并列出至少 3 个连接池常用参数。
 * ============================================================
 */
