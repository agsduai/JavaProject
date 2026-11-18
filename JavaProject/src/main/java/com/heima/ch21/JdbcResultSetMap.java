/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：21-03
 * 课名：ResultSet遍历与结果封装
 * ------------------------------------------------------------
 * 知识点：
 *   - `ResultSet` 是游标，初始指向"第一行之前"，`next()` 返回 `true` 表示成功移动到下一行。
 *   - 取值方式：按列序号（从 1 开始）或按列名；按列名可读性更好，按序号性能略高。
 *   - 常用方法：`getInt`、`getString`、`getDouble`、`getDate`、`getObject`。
 *   - `getObject(列名, 类型.class)` 可直接转成对应 Java 类型，适合泛化封装。
 *   - 把每行数据封装成 JavaBean（实体类）是 DAO 的基础做法：一行 → 一个对象，多行 → `List<对象>`。
 *   - 易错：`ResultSet` 只能顺序读一遍，读完后要重新查询；`getXxx` 前必须先 `next()` 成功。
 * ============================================================
 */

package com.heima.ch21;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

// ==================== 示例代码 ====================

public class JdbcResultSetMap {
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
        List<StudentRecord> list = new ArrayList<>();

        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "select id, name, score from jdbc_demo order by score desc")) {

            // 遍历结果集，逐行封装为实体对象
            while (rs.next()) {
                StudentRecord s = new StudentRecord();
                s.setId(rs.getInt("id"));
                s.setName(rs.getString("name"));
                s.setScore(rs.getInt("score"));
                list.add(s);
            }
        }

        System.out.println("共查询到 " + list.size() + " 条记录：");
        for (StudentRecord s : list) {
            System.out.println(s);
        }

        // 统计与筛选：结果集已关闭，直接在集合上做
        int sum = list.stream().mapToInt(StudentRecord::getScore).sum();
        System.out.println("平均分：" + (list.isEmpty() ? 0 : sum / list.size()));

        list.stream()
                .filter(s -> s.getScore() >= 80)
                .forEach(s -> System.out.println("优秀：" + s.getName()));
    }
}

class StudentRecord {
    private int id;
    private String name;
    private int score;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    @Override
    public String toString() {
        return "Student{id=" + id + ", name='" + name + "', score=" + score + '}';
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 查询 `jdbc_demo` 表全部数据，封装成 `List<Student>`，按分数降序打印。
 * 2. 写一个方法 `Student findById(int id)`，查不到时返回 `null`，并在 `main` 中处理空值。
 * 3. 对比"按列序号取值"和"按列名取值"的优缺点，各写一段示例。
 * ============================================================
 */
