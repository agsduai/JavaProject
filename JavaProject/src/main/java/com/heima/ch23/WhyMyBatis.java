/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：23-01
 * 课名：为什么需要MyBatis
 * ------------------------------------------------------------
 * 知识点：
 *   - 原生 JDBC 的五个痛点：注册驱动/取连接、SQL 硬编码、参数下标易错、结果集手工封装、资源关闭易漏。
 *   - ORM（Object Relational Mapping，对象关系映射）把"表的一行"和"一个对象"对应起来，让代码面向对象而不是面向 `ResultSet`。
 *   - MyBatis 是半自动 ORM：SQL 由开发者手写，框架负责参数绑定、结果映射、连接与事务管理，SQL 完全可控。
 *   - 对比全自动 ORM（如 Hibernate/JPA）：MyBatis 上手快、SQL 可优化，适合互联网项目；JPA 更屏蔽数据库差异，适合领域模型稳定的系统。
 *   - MyBatis 的核心构成：`SqlSessionFactory`（工厂）、`SqlSession`（会话）、`Mapper`（接口 + SQL 映射）、`Executor`（执行器）。
 *   - 易错：MyBatis 不是"不用写 SQL"，而是"不用写 JDBC 样板代码"；SQL 依然是你的责任，也是性能调优的主战场。
 * ============================================================
 */

package com.heima.ch23;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class WhyMyBatis {
    public static void main(String[] args) {
        System.out.println("=== 原生 JDBC 要写的样板代码 ===");
        List<String> jdbcSteps = List.of(
                "Class.forName(\"com.mysql.cj.jdbc.Driver\")",
                "DriverManager.getConnection(url, user, pwd)",
                "conn.prepareStatement(sql)",
                "ps.setInt(1, id); ps.setString(2, name)",
                "ResultSet rs = ps.executeQuery()",
                "while (rs.next()) { 手工 new 对象、逐个 getXxx }",
                "rs.close(); ps.close(); conn.close();  // 每个都要 try-finally");
        jdbcSteps.forEach(s -> System.out.println("  - " + s));

        System.out.println();
        System.out.println("=== MyBatis 只让你写 ===");
        List<String> mybatisSteps = List.of(
                "SQL（写在 Mapper XML 或注解里）",
                "参数（方法形参 + #{} 占位符）",
                "结果映射（resultType / resultMap）");
        mybatisSteps.forEach(s -> System.out.println("  - " + s));

        // 用一张对照表量化"重复代码"的减少
        Map<String, Integer> tableCount = new LinkedHashMap<>();
        tableCount.put("student", 4);   // 增删改查
        tableCount.put("course", 4);
        tableCount.put("score", 3);

        int total = tableCount.values().stream().mapToInt(Integer::intValue).sum();
        int jdbcLinesPerMethod = 12;
        int mybatisLinesPerMethod = 3;

        System.out.println();
        System.out.println("=== 代码量对比（" + tableCount.size() + " 张表 / " + total + " 个方法）===");
        System.out.println("JDBC   ：约 " + total * jdbcLinesPerMethod + " 行");
        System.out.println("MyBatis：约 " + total * mybatisLinesPerMethod + " 行");
        System.out.println("节省   ：" + (total * jdbcLinesPerMethod - total * mybatisLinesPerMethod) + " 行样板代码");

        // 半自动 ORM 的定位：SQL 仍由人写，框架接管周边
        List<String> owned = new ArrayList<>();
        owned.add("SQL 语句 -> 开发者");
        owned.add("参数绑定 -> MyBatis");
        owned.add("结果映射 -> MyBatis");
        owned.add("连接/事务/资源关闭 -> MyBatis");
        owned.forEach(s -> System.out.println("  " + s));
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 用 JDBC 写一个"按姓名模糊查询学生"的方法，数出其中有多少行是与业务无关的样板代码。
 * 2. 用自己的话说明"半自动 ORM"和"全自动 ORM"的区别，各举一个适用场景。
 * 3. 列出 MyBatis 的四个核心对象，并说明它们各自的职责与生命周期。
 * ============================================================
 */
