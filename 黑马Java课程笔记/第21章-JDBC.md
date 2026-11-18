# 第21章 JDBC

JDBC（Java DataBase Connectivity）是 Java 官方提供的数据库访问规范。本章从"拿到连接、发出 SQL、处理结果"这条主线出发，先跑通增删改查，再解决 SQL 注入、事务、资源释放这些真实开发绕不开的问题，最后用工具类和 DAO 模式把代码整理成能复用的结构。

### 21-01 JDBC概述与快速入门

**知识点**
- JDBC 是一套接口规范（`java.sql` 包），由数据库厂商提供驱动实现，Java 代码只面向接口编程。
- 核心四件套：`DriverManager` 管理驱动与连接、`Connection` 代表一次连接、`Statement` 执行 SQL、`ResultSet` 封装查询结果。
- 连接地址格式：`jdbc:mysql://主机:端口/库名?参数`，常用参数 `useSSL=false`、`serverTimezone=Asia/Shanghai`、`characterEncoding=utf8`。
- 驱动类：MySQL 8.x 用 `com.mysql.cj.jdbc.Driver`，5.x 用 `com.mysql.jdbc.Driver`；JDBC 4.0 之后可省略 `Class.forName`。
- 标准步骤：加载驱动 → 获取连接 → 创建 Statement → 执行 SQL → 处理结果 → 释放资源。
- 易错：`Connection`、`Statement`、`ResultSet` 都是稀缺资源，必须关闭；关闭顺序与打开顺序相反。

**示例代码**
```java
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

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
```

**课后练习**
1. 在本地 MySQL 中建好 `heima_db` 库和 `student` 表，用 JDBC 连接并打印表里所有记录的姓名。
2. 把连接地址中的 `serverTimezone` 去掉再运行，观察报错信息并解释原因。
3. 画出 JDBC 六步流程图，标注每一步用到的核心类。

### 21-02 Statement执行DDL与DML

**知识点**
- `executeUpdate(sql)` 用于 `insert`、`update`、`delete`、`create table` 等，返回受影响的行数。
- `executeQuery(sql)` 用于 `select`，返回 `ResultSet`。
- `execute(sql)` 通用于任意 SQL，返回 `boolean`：`true` 表示有结果集。
- 建表、删表属于 DDL，`executeUpdate` 同样适用，返回 0。
- 拼接 SQL 字符串是入门写法，只适合固定内容，有用户输入时必须换成 `PreparedStatement`。
- 易错：`executeUpdate` 的返回值是"影响行数"，`update` 值没变化时 MySQL 可能返回 0，不能当作失败。

**示例代码**
```java
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

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
```

**课后练习**
1. 用 JDBC 在数据库中创建一张 `jdbc_book` 表，再插入 3 条记录，打印每一步的影响行数。
2. 写一个方法 `int updateScore(String name, int delta)`，把指定学生的分数增加指定值并返回影响行数。
3. 说明 `execute`、`executeQuery`、`executeUpdate` 三个方法的区别与适用场景。

### 21-03 ResultSet遍历与结果封装

**知识点**
- `ResultSet` 是游标，初始指向"第一行之前"，`next()` 返回 `true` 表示成功移动到下一行。
- 取值方式：按列序号（从 1 开始）或按列名；按列名可读性更好，按序号性能略高。
- 常用方法：`getInt`、`getString`、`getDouble`、`getDate`、`getObject`。
- `getObject(列名, 类型.class)` 可直接转成对应 Java 类型，适合泛化封装。
- 把每行数据封装成 JavaBean（实体类）是 DAO 的基础做法：一行 → 一个对象，多行 → `List<对象>`。
- 易错：`ResultSet` 只能顺序读一遍，读完后要重新查询；`getXxx` 前必须先 `next()` 成功。

**示例代码**
```java
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

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
        List<JdbcResultSetMapStudent> list = new ArrayList<>();

        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "select id, name, score from jdbc_demo order by score desc")) {

            // 遍历结果集，逐行封装为实体对象
            while (rs.next()) {
                JdbcResultSetMapStudent s = new JdbcResultSetMapStudent();
                s.setId(rs.getInt("id"));
                s.setName(rs.getString("name"));
                s.setScore(rs.getInt("score"));
                list.add(s);
            }
        }

        System.out.println("共查询到 " + list.size() + " 条记录：");
        for (JdbcResultSetMapStudent s : list) {
            System.out.println(s);
        }

        // 统计与筛选：结果集已关闭，直接在集合上做
        int sum = list.stream().mapToInt(JdbcResultSetMapStudent::getScore).sum();
        System.out.println("平均分：" + (list.isEmpty() ? 0 : sum / list.size()));

        list.stream()
                .filter(s -> s.getScore() >= 80)
                .forEach(s -> System.out.println("优秀：" + s.getName()));
    }
}

class JdbcResultSetMapStudent {
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
```

**课后练习**
1. 查询 `jdbc_demo` 表全部数据，封装成 `List<Student>`，按分数降序打印。
2. 写一个方法 `Student findById(int id)`，查不到时返回 `null`，并在 `main` 中处理空值。
3. 对比"按列序号取值"和"按列名取值"的优缺点，各写一段示例。

### 21-04 SQL注入与PreparedStatement

**知识点**
- SQL 注入：用户输入被拼进 SQL 后改变了原语句语义，典型如输入 `' or '1'='1` 绕过密码校验。
- 根本原因：`Statement` 把"数据"当成了"SQL 语法"的一部分。
- `PreparedStatement` 预编译 SQL，用 `?` 占位，参数通过 `setXxx` 绑定，数据永远只当数据处理。
- 绑定方法：`setInt`、`setString`、`setDouble`、`setObject`、`setDate`；序号从 1 开始。
- 额外收益：SQL 只编译一次可重复执行、自动处理引号与类型转换、可读性更好。
- 易错：占位符 `?` 不能用于表名、列名、`order by` 字段，这些位置只能拼接并做白名单校验。

**示例代码**
```java
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

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
```

**课后练习**
1. 用 `Statement` 写一个登录校验方法，再用 `PreparedStatement` 改写，构造恶意输入验证前者被绕过、后者安全。
2. 写一个按分数区间查询的方法 `List<Student> findByScore(int min, int max)`，使用占位符绑定两个参数。
3. 说明为什么 `?` 不能用于表名和排序字段，并给出安全的替代方案。

### 21-05 PreparedStatement增删改与批量操作

**知识点**
- 增删改统一用 `executeUpdate()`，返回影响行数；插入后要拿自增主键需指定 `Statement.RETURN_GENERATED_KEYS`。
- 批量操作：`addBatch()` 攒一批，`executeBatch()` 一次提交，返回每条的更新行数数组。
- 批量执行必须在 URL 上加 `rewriteBatchedStatements=true` 才能真正合并成一条多值 SQL，否则性能提升有限。
- 获取自增主键：`ps.getGeneratedKeys()` 得到 `ResultSet`，取第 1 列。
- 批量更新时最好放在一个事务里，避免部分成功部分失败。
- 易错：`addBatch` 前必须 `setXxx` 全部参数；`executeBatch` 后要 `clearBatch` 才能复用。

**示例代码**
```java
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

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
```

**课后练习**
1. 用批量插入向 `jdbc_book` 表插入 500 条数据，对比"逐条 `executeUpdate`"和"`addBatch`"的耗时。
2. 写一个插入方法，插入成功后返回自增主键，插入失败返回 `-1`。
3. 说明 `rewriteBatchedStatements=true` 的作用，以及不加这个参数时批量插入为什么慢。

### 21-06 JDBC事务控制

**知识点**
- JDBC 默认自动提交：每条 SQL 执行完立即生效，无法整体回滚。
- 手动事务：`conn.setAutoCommit(false)` 开启 → 多条 SQL → `conn.commit()` 提交 / `conn.rollback()` 回滚。
- 事务必须作用在同一个 `Connection` 上，换连接就不在同一事务里。
- 转账是经典场景：扣款和入账必须同时成功或同时失败。
- 可以在 `catch` 中 `rollback`，在 `finally` 中恢复 `setAutoCommit(true)` 并关闭连接。
- 易错：`conn.close()` 前未提交且未回滚时，未提交的数据会丢失；`DDL` 语句会隐式提交。

**示例代码**
```java
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

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
```

**课后练习**
1. 模拟下单扣库存：库存充足则扣减库存并生成订单，库存不足则回滚，两个操作放在同一事务中。
2. 故意在入账后抛出异常，验证 `rollback` 后扣款也被撤销。
3. 说明 JDBC 默认自动提交会带来什么问题，以及为什么事务必须使用同一个 `Connection`。

### 21-07 JDBC工具类封装

**知识点**
- 重复的连接四要素和资源关闭代码应抽到工具类：`JdbcUtil.getConnection()` / `JdbcUtil.close(...)`。
- 配置文件 `db.properties` 放在 `src/main/resources`，用 `Properties` 加载，换库只改配置不改代码。
- 连接池：预先创建若干连接重复使用，避免频繁创建销毁；常见实现 HikariCP（Spring Boot 默认）、Druid。
- 连接池核心参数：初始连接数、最大连接数、最大等待时间、最小空闲数。
- `DataSource`（`javax.sql`）是连接池的统一接口，`dataSource.getConnection()` 用法与 `DriverManager` 一致。
- 易错：工具类中不要用 `static Connection` 保存连接，多线程下会被多个线程共享导致事务串线。

**示例代码**
```java
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

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
        try (Connection conn = JdbcUtilDemoUtil.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("select count(*) from jdbc_demo")) {
            if (rs.next()) {
                System.out.println("jdbc_demo 共 " + rs.getInt(1) + " 条记录");
            }
        }

        // 演示配置来源
        Properties p = JdbcUtilDemoUtil.loadConfig();
        System.out.println("当前连接的库：" + p.getProperty("jdbc.url"));
    }
}

/**
 * 简易 JDBC 工具类：负责读取配置、获取连接、静默关闭资源。
 * 生产项目应替换为 HikariCP / Druid 连接池。
 */
class JdbcUtilDemoUtil {
    private static final Properties CONFIG = loadConfig();

    public static Properties loadConfig() {
        Properties p = new Properties();
        // 优先读取 classpath 下的 db.properties，读不到则使用内置默认值
        try (InputStream in = JdbcUtilDemoUtil.class.getClassLoader()
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
```

**课后练习**
1. 把本章前面几课的连接代码改成使用 `JdbcUtil`，统计每个类减少了多少行重复代码。
2. 在 `src/main/resources` 下创建 `db.properties`，把连接四要素写进去，让工具类读取，验证改配置即可切换数据库。
3. 说出连接池相比 `DriverManager` 每次新建连接的优势，并列出至少 3 个连接池常用参数。

### 21-08 DAO模式与学生管理综合案例

**知识点**
- DAO（Data Access Object）把"数据库操作"从业务代码中剥离，每张表对应一个 DAO 接口 + 实现类。
- 分层结构：实体类（entity）→ DAO（数据访问）→ Service（业务）→ 测试/界面（main）。
- 通用方法命名约定：`insert`、`deleteById`、`update`、`findById`、`findAll`、`findByXxx`。
- 实体类只放字段 + 无参/全参构造 + getter/setter + `toString`，不写业务逻辑。
- 统一异常处理：DAO 内部把 `SQLException` 转成运行时异常抛出，避免每层都写 `try-catch`。
- 易错：DAO 方法里不要 `System.out` 打印，应返回数据由上层决定如何展示，否则无法复用。

**示例代码**
```java
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

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
        JdbcDaoCaseDao dao = new JdbcDaoCaseDao();

        // 新增
        JdbcDaoCaseStudent s = new JdbcDaoCaseStudent(0, "综合案例", 77);
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
        List<JdbcDaoCaseStudent> all = dao.findAll();
        System.out.println("全部记录数：" + all.size());

        // 删除
        System.out.println("删除影响行数：" + dao.deleteById(id));
    }
}

/** 实体类：与表字段一一对应 */
class JdbcDaoCaseStudent {
    private int id;
    private String name;
    private int score;

    public JdbcDaoCaseStudent() {
    }

    public JdbcDaoCaseStudent(int id, String name, int score) {
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
class JdbcDaoCaseDao {
    // 连接参数放在 DAO 内部，避免依赖入口类的成员
    private static final String URL =
            "jdbc:mysql://localhost:3306/heima_db?useSSL=false&serverTimezone=Asia/Shanghai";
    private static final String USER = "root";
    private static final String PASSWORD = "1234";

    private Connection getConnection() throws Exception {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }

    /** 新增，返回自增主键 */
    public int insert(JdbcDaoCaseStudent s) {
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
    public int update(JdbcDaoCaseStudent s) {
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
    public JdbcDaoCaseStudent findById(int id) {
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
    public List<JdbcDaoCaseStudent> findByMinScore(int minScore) {
        String sql = "select id, name, score from jdbc_demo where score >= ? order by score desc";
        List<JdbcDaoCaseStudent> list = new ArrayList<>();
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
    public List<JdbcDaoCaseStudent> findAll() {
        String sql = "select id, name, score from jdbc_demo order by id";
        List<JdbcDaoCaseStudent> list = new ArrayList<>();
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
    private JdbcDaoCaseStudent mapRow(ResultSet rs) throws Exception {
        return new JdbcDaoCaseStudent(
                rs.getInt("id"), rs.getString("name"), rs.getInt("score"));
    }
}
```

**课后练习**
1. 按 DAO 模式实现一个完整的图书管理模块：新增图书、按 ID 删除、修改价格、按书名模糊查询、分页查询。
2. 在 DAO 之上加一层 `Service`，实现"借书"业务：先查库存，库存大于 0 才扣减并生成借阅记录，整个过程使用事务。
3. 把 `JdbcDaoCaseDao` 里的连接获取换成第 21-07 课的 `JdbcUtil`，并说明这样改带来哪些好处。
