/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：21-08
 * 课名：DAO模式与学生管理综合案例
 * ------------------------------------------------------------
 * 知识点：
 *   - DAO（Data Access Object）把"数据库操作"从业务代码中剥离，每张表对应一个 DAO 接口 + 实现类。
 *   - 分层结构：实体类（entity）→ DAO（数据访问）→ Service（业务）→ 测试/界面（main）。
 *   - 通用方法命名约定：`insert`、`deleteById`、`update`、`findById`、`findAll`、`findByXxx`。
 *   - 实体类只放字段 + 无参/全参构造 + getter/setter + `toString`，不写业务逻辑。
 *   - 统一异常处理：DAO 内部把 `SQLException` 转成运行时异常抛出，避免每层都写 `try-catch`。
 *   - 易错：DAO 方法里不要 `System.out` 打印，应返回数据由上层决定如何展示，否则无法复用。
 * ============================================================
 */

package com.heima.ch21;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

// ==================== 示例代码 ====================

public class JdbcDaoCase {
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
        StudentDao dao = new StudentDao();

        // 新增
        StudentEntity s = new StudentEntity(0, "综合案例", 77);
        int id = dao.insert(s);
        System.out.println("新增成功，主键 = " + id);

        // 按主键查询
        System.out.println("按 ID 查询：" + dao.findById(id));

        // 条件查询
        System.out.println("按分数查询：" + dao.findByMinScore(60));

        // 更新
        s.setId(id);
        s.setScore(95);
        System.out.println("更新影响行数：" + dao.update(s));

        // 查询全部
        List<StudentEntity> all = dao.findAll();
        System.out.println("全部记录数：" + all.size());

        // 删除
        System.out.println("删除影响行数：" + dao.deleteById(id));
    }
}

/** 实体类：与表字段一一对应 */
class StudentEntity {
    private int id;
    private String name;
    private int score;

    public StudentEntity() {
    }

    public StudentEntity(int id, String name, int score) {
        this.id = id;
        this.name = name;
        this.score = score;
    }

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

/** DAO：只负责与数据库交互，返回数据或影响行数 */
class StudentDao {
    // 连接参数放在 DAO 内部，避免依赖入口类的成员
    private static final String URL =
            "jdbc:mysql://localhost:3306/heima_db?useSSL=false&serverTimezone=Asia/Shanghai";
    private static final String USER = "root";
    private static final String PASSWORD = "1234";

    private Connection getConnection() throws Exception {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }

    /** 新增，返回自增主键 */
    public int insert(StudentEntity s) {
        String sql = "insert into jdbc_demo (name, score) values (?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql,
                     PreparedStatement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, s.getName());
            ps.setInt(2, s.getScore());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getInt(1) : -1;
            }
        } catch (Exception e) {
            throw new IllegalStateException("新增失败", e);
        }
    }

    /** 按主键删除 */
    public int deleteById(int id) {
        String sql = "delete from jdbc_demo where id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            return ps.executeUpdate();
        } catch (Exception e) {
            throw new IllegalStateException("删除失败", e);
        }
    }

    /** 更新 */
    public int update(StudentEntity s) {
        String sql = "update jdbc_demo set name = ?, score = ? where id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, s.getName());
            ps.setInt(2, s.getScore());
            ps.setInt(3, s.getId());
            return ps.executeUpdate();
        } catch (Exception e) {
            throw new IllegalStateException("更新失败", e);
        }
    }

    /** 按主键查询，查不到返回 null */
    public StudentEntity findById(int id) {
        String sql = "select id, name, score from jdbc_demo where id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        } catch (Exception e) {
            throw new IllegalStateException("查询失败", e);
        }
    }

    /** 条件查询 */
    public List<StudentEntity> findByMinScore(int minScore) {
        String sql = "select id, name, score from jdbc_demo where score >= ? order by score desc";
        List<StudentEntity> list = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, minScore);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
            return list;
        } catch (Exception e) {
            throw new IllegalStateException("查询失败", e);
        }
    }

    /** 查询全部 */
    public List<StudentEntity> findAll() {
        String sql = "select id, name, score from jdbc_demo order by id";
        List<StudentEntity> list = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
            return list;
        } catch (Exception e) {
            throw new IllegalStateException("查询失败", e);
        }
    }

    /** 把一行结果映射成对象，供各查询方法复用 */
    private StudentEntity mapRow(ResultSet rs) throws Exception {
        return new StudentEntity(
                rs.getInt("id"), rs.getString("name"), rs.getInt("score"));
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 按 DAO 模式实现一个完整的图书管理模块：新增图书、按 ID 删除、修改价格、按书名模糊查询、分页查询。
 * 2. 在 DAO 之上加一层 `Service`，实现"借书"业务：先查库存，库存大于 0 才扣减并生成借阅记录，整个过程使用事务。
 * 3. 把 `JdbcDaoCaseDao` 里的连接获取换成第 21-07 课的 `JdbcUtil`，并说明这样改带来哪些好处。
 * ============================================================
 */
