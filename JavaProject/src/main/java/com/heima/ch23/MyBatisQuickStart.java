/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：23-02
 * 课名：快速入门：SqlSessionFactory与SqlSession
 * ------------------------------------------------------------
 * 知识点：
 *   - `SqlSessionFactoryBuilder` 读全局配置（`mybatis-config.xml`）构建 `SqlSessionFactory`；工厂是**单例**，应用启动时创建一次，整个应用共享。
 *   - `SqlSessionFactory.openSession()` 得到 `SqlSession`；`SqlSession` 非线程安全，**每次请求/每次操作都要新建并关闭**。
 *   - `openSession()` 默认不自动提交，需要 `sqlSession.commit()`；`openSession(true)` 才是自动提交。
 *   - 全局配置文件管数据源、事务管理器、类型别名、插件、`<mappers>` 注册。
 *   - 现代 Spring Boot 项目里这些都由 starter 自动配置，但排查问题（比如"Mapper 没被扫描到"）必须懂这层。
 *   - 易错：把 `SqlSessionFactory` 每次 `new` 一个，或把 `SqlSession` 存成成员变量跨线程复用，都是典型事故。
 * ============================================================
 */

package com.heima.ch23;

import java.util.HashMap;
import java.util.Map;

// ==================== 示例代码 ====================

public class MyBatisQuickStart {
    public static void main(String[] args) {
        // 模拟：解析 mybatis-config.xml
        Map<String, String> config = new HashMap<>();
        config.put("driver", "com.mysql.cj.jdbc.Driver");
        config.put("url", "jdbc:mysql://localhost:3306/heima_db?useSSL=false&serverTimezone=Asia/Shanghai");
        config.put("username", "root");
        config.put("password", "1234");
        config.put("mapper", "com.heima.mapper.StudentMapper");

        // 1. 构建工厂：全应用只做一次（重量级、线程安全）
        SqlSessionFactory factory = new SqlSessionFactoryBuilder().build(config);
        System.out.println("工厂已创建，数据源 = " + factory.config.get("url"));
        System.out.println("已注册 Mapper：" + factory.config.get("mapper"));
        System.out.println();

        // 2. 每次操作开一个会话（轻量级、非线程安全）
        try (SqlSession session = factory.openSession()) {
            System.out.println("会话已打开，autoCommit=" + session.autoCommit);
            StudentMapper mapper = session.getMapper(StudentMapper.class);
            System.out.println("按 id 查询：" + mapper.findById(1));
            System.out.println("此时未提交，其它连接看不到这条数据");
            session.commit();
            System.out.println("已 commit");
        }

        System.out.println();
        // 3. 自动提交会话
        try (SqlSession session = factory.openSession(true)) {
            System.out.println("自动提交会话 autoCommit=" + session.autoCommit);
        }

        System.out.println();
        System.out.println("结论：工厂单例、会话短命；会话用完必须 close（try-with-resources 最省心）。");
    }

    // ---- 迷你框架骨架 ----
    static class SqlSessionFactoryBuilder {
        SqlSessionFactory build(Map<String, String> config) {
            return new SqlSessionFactory(config);
        }
    }

    static class SqlSessionFactory {
        final Map<String, String> config;

        SqlSessionFactory(Map<String, String> config) {
            this.config = config;
        }

        SqlSession openSession() {
            return openSession(false);
        }

        SqlSession openSession(boolean autoCommit) {
            return new SqlSession(autoCommit);
        }
    }

    static class SqlSession implements AutoCloseable {
        final boolean autoCommit;
        private boolean closed = false;

        SqlSession(boolean autoCommit) {
            this.autoCommit = autoCommit;
        }

        <T> T getMapper(Class<T> type) {
            // 真实环境由 MyBatis 用动态代理生成实现类，这里给一个最小可用的桩
            if (type == StudentMapper.class) {
                return type.cast((StudentMapper) id -> new Student(id, "张三", 20));
            }
            throw new IllegalArgumentException("未注册的 Mapper：" + type.getName());
        }

        void commit() {
            checkOpen();
            System.out.println("  [SqlSession] commit -> 事务提交");
        }

        public void close() {
            closed = true;
            System.out.println("  [SqlSession] 连接归还连接池");
        }

        private void checkOpen() {
            if (closed) {
                throw new IllegalStateException("SqlSession 已关闭，不能继续使用");
            }
        }
    }

    record Student(int id, String name, int age) {
    }

    interface StudentMapper {
        Student findById(int id);
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 说出 `SqlSessionFactory` 和 `SqlSession` 在生命周期上的区别，并解释为什么一个单例、一个短命。
 * 2. 默认 `openSession()` 不自动提交，如果不 commit 会有什么现象？用一段代码验证。
 * 3. 把 `SqlSession` 写成类的成员变量，在 Web 环境下可能出什么事故？
 * ============================================================
 */
