# 第23章 MyBatis

第21章用 JDBC 写 DAO，能跑通，但每一张表都要重复写"取连接、拼 SQL、设参数、遍历结果集、封装对象、关资源"。MyBatis 把这些模板代码抽走，只留下"SQL + 参数 + 结果映射"三件事让你写。本章先讲清 ORM 与 MyBatis 的定位，再依次掌握 `SqlSessionFactory`、Mapper 接口、参数与结果映射、动态 SQL、注解开发、关联查询和缓存。示例代码用纯 JDK 手写一个"迷你 MyBatis"，把框架内部做的事逐层摊开，读完再看真实 `pom.xml` 里的配置会非常直观。

### 23-01 为什么需要MyBatis

**知识点**
- 原生 JDBC 的五个痛点：注册驱动/取连接、SQL 硬编码、参数下标易错、结果集手工封装、资源关闭易漏。
- ORM（Object Relational Mapping，对象关系映射）把"表的一行"和"一个对象"对应起来，让代码面向对象而不是面向 `ResultSet`。
- MyBatis 是半自动 ORM：SQL 由开发者手写，框架负责参数绑定、结果映射、连接与事务管理，SQL 完全可控。
- 对比全自动 ORM（如 Hibernate/JPA）：MyBatis 上手快、SQL 可优化，适合互联网项目；JPA 更屏蔽数据库差异，适合领域模型稳定的系统。
- MyBatis 的核心构成：`SqlSessionFactory`（工厂）、`SqlSession`（会话）、`Mapper`（接口 + SQL 映射）、`Executor`（执行器）。
- 易错：MyBatis 不是"不用写 SQL"，而是"不用写 JDBC 样板代码"；SQL 依然是你的责任，也是性能调优的主战场。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
```

**课后练习**
1. 用 JDBC 写一个"按姓名模糊查询学生"的方法，数出其中有多少行是与业务无关的样板代码。
2. 用自己的话说明"半自动 ORM"和"全自动 ORM"的区别，各举一个适用场景。
3. 列出 MyBatis 的四个核心对象，并说明它们各自的职责与生命周期。

### 23-02 快速入门：SqlSessionFactory与SqlSession

**知识点**
- `SqlSessionFactoryBuilder` 读全局配置（`mybatis-config.xml`）构建 `SqlSessionFactory`；工厂是**单例**，应用启动时创建一次，整个应用共享。
- `SqlSessionFactory.openSession()` 得到 `SqlSession`；`SqlSession` 非线程安全，**每次请求/每次操作都要新建并关闭**。
- `openSession()` 默认不自动提交，需要 `sqlSession.commit()`；`openSession(true)` 才是自动提交。
- 全局配置文件管数据源、事务管理器、类型别名、插件、`<mappers>` 注册。
- 现代 Spring Boot 项目里这些都由 starter 自动配置，但排查问题（比如"Mapper 没被扫描到"）必须懂这层。
- 易错：把 `SqlSessionFactory` 每次 `new` 一个，或把 `SqlSession` 存成成员变量跨线程复用，都是典型事故。

**示例代码**
```java
import java.util.HashMap;
import java.util.Map;

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
```

**课后练习**
1. 说出 `SqlSessionFactory` 和 `SqlSession` 在生命周期上的区别，并解释为什么一个单例、一个短命。
2. 默认 `openSession()` 不自动提交，如果不 commit 会有什么现象？用一段代码验证。
3. 把 `SqlSession` 写成类的成员变量，在 Web 环境下可能出什么事故？

### 23-03 Mapper接口与映射文件

**知识点**
- Mapper 由两部分组成：Java 接口（方法签名）+ XML 映射文件（SQL）。MyBatis 用**动态代理**为接口生成实现，方法名与 SQL 的 `id` 一一对应。
- 两个必须满足的约定：接口全限定名 = XML 的 `namespace`；方法名 = `<select>/<insert>/<update>/<delete>` 的 `id`。
- 参数类型用 `parameterType`（可省略），返回类型用 `resultType`（单条）或 `resultMap`（自定义映射）。
- Mapper 接口不需要实现类，也不应该被 `new`；只能通过 `sqlSession.getMapper(XxxMapper.class)` 获取。
- 返回值约定：查一条返回对象（查不到返回 `null`），查多条返回 `List`，增删改返回影响行数 `int`。
- 易错：`namespace` 写错会抛 `Invalid bound statement (not found)`，这是 MyBatis 最高频的报错，先查 namespace 再查 id。

**示例代码**
```java
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MapperProxyDemo {
    public static void main(String[] args) {
        // 模拟 XML 里的 statement 注册表：namespace.id -> SQL
        // 真实项目里 namespace 就是 Mapper 接口的全限定名
        String namespace = "com.heima.mapper.StudentMapper";
        System.out.println("XML 的 namespace = " + namespace);
        System.out.println();
        Map<String, String> statements = new LinkedHashMap<>();
        statements.put(namespace + ".findById",
                "SELECT id, name, age FROM student WHERE id = ?");
        statements.put(namespace + ".findAll",
                "SELECT id, name, age FROM student");
        statements.put(namespace + ".insert",
                "INSERT INTO student(name, age) VALUES (?, ?)");

        StudentMapper mapper = MapperFactory.create(StudentMapper.class, namespace, statements);

        System.out.println("mapper 的实际类型：" + mapper.getClass().getName());
        System.out.println("是动态代理吗：" + Proxy.isProxyClass(mapper.getClass()));
        System.out.println();

        System.out.println("findById(1) -> " + mapper.findById(1));
        System.out.println();
        System.out.println("findAll() -> " + mapper.findAll());
        System.out.println();
        System.out.println("insert(new Student(0, \"赵六\", 19)) -> " + mapper.insert(new Student(0, "赵六", 19)));

        System.out.println();
        // 演示 XML 里漏写某条 SQL 会怎样（namespace 写错时报的也是同一个错）
        try {
            mapper.deleteById(1);
        } catch (IllegalStateException e) {
            System.out.println("预期异常：" + e.getMessage());
        }
    }

    interface StudentMapper {
        Student findById(int id);

        List<Student> findAll();

        int insert(Student student);

        int deleteById(int id);   // XML 里故意没写这条 SQL
    }

    record Student(int id, String name, int age) {
    }

    // 用 JDK 动态代理模拟 MyBatis 的 MapperProxy
    static class MapperFactory {
        @SuppressWarnings("unchecked")
        static <T> T create(Class<T> mapperType, String namespace, Map<String, String> statements) {
            InvocationHandler handler = (proxy, method, methodArgs) -> {
                if (method.getDeclaringClass() == Object.class) {
                    return switch (method.getName()) {
                        case "toString" -> mapperType.getSimpleName() + "Proxy";
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == methodArgs[0];
                        default -> null;
                    };
                }
                // 约定：namespace + "." + 方法名 = statement 的 id
                String key = namespace + "." + method.getName();
                String sql = statements.get(key);
                if (sql == null) {
                    throw new IllegalStateException(
                            "Invalid bound statement (not found): " + key);
                }
                System.out.println("  [SQL] " + sql);
                System.out.println("  [参数] " + java.util.Arrays.toString(methodArgs));
                return fakeExecute(method, methodArgs);
            };
            return (T) Proxy.newProxyInstance(
                    mapperType.getClassLoader(), new Class<?>[]{mapperType}, handler);
        }

        private static Object fakeExecute(Method method, Object[] args) {
            String name = method.getName();
            if ("findById".equals(name)) {
                return new Student((int) args[0], "张三", 20);
            }
            if ("findAll".equals(name)) {
                return new ArrayList<>(List.of(
                        new Student(1, "张三", 20),
                        new Student(2, "李四", 21)));
            }
            if ("insert".equals(name)) {
                return 1;
            }
            return null;
        }
    }
}
```

**课后练习**
1. 手写一个 `CourseMapper` 接口和对应 XML，实现按 id 查、查全部、按名称模糊查三个方法。
2. 故意把 XML 的 `namespace` 改错，运行后记录完整报错信息，并说明排查顺序。
3. 解释 MyBatis 为什么能"接口不写实现类"就能调用 SQL，动态代理在其中做了什么。

### 23-04 参数传递与占位符

**知识点**
- `#{}` 是**预编译占位符**，生成 `?`，由 `PreparedStatement` 设参，能防 SQL 注入；`${}` 是**字符串拼接**，直接把值拼进 SQL，有注入风险。
- 单个基本类型参数：`#{}` 里名字随便写，如 `#{id}`、`#{value}` 都能取到。
- 多个参数：MyBatis 会封装成 `Map`，键是 `arg0/arg1` 或 `param1/param2`；推荐用 `@Param("name")` 显式命名。
- 对象参数：`#{}` 里写属性名，如 `#{name}`、`#{age}`，走 getter 取值。
- `Map` 参数：`#{}` 里写 key。
- `${}` 的合法用途只有动态表名、动态列名、`ORDER BY` 字段这类"结构"部分，且必须做白名单校验。
- 易错：`ORDER BY #{column}` 不生效（会变成 `ORDER BY 'name'` 按常量排序），这里必须用 `${}` 加白名单。

**示例代码**
```java
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ParameterBinding {
    public static void main(String[] args) {
        // 1. #{} 与 ${} 的替换结果对比
        String sql1 = "SELECT * FROM student WHERE name = #{name} AND age > #{age}";
        String sql2 = "SELECT * FROM student WHERE name = '${name}' AND age > ${age}";

        Map<String, Object> params = new HashMap<>();
        params.put("name", "张三");
        params.put("age", 18);

        System.out.println("=== #{} 预编译 ===");
        System.out.println("SQL   ：" + toPrepared(sql1));
        System.out.println("参数  ：" + extractParams(sql1, params));
        System.out.println();

        System.out.println("=== ${} 拼接 ===");
        System.out.println("SQL   ：" + toConcatenated(sql2, params));
        System.out.println();

        // 2. 注入攻击演示（仅拼接才会中招）
        Map<String, Object> attack = new HashMap<>();
        attack.put("name", "张三' OR '1'='1");
        attack.put("age", 0);
        System.out.println("=== 恶意输入：张三' OR '1'='1 ===");
        System.out.println("#{} 生成的 SQL：" + toPrepared(sql1) + "  （值被当作纯字符串，安全）");
        System.out.println("${} 生成的 SQL：" + toConcatenated(sql2, attack));
        System.out.println("  -> 条件恒真，全表数据被拖走");
        System.out.println();

        // 3. ${} 的唯一合法场景：动态排序字段 + 白名单
        String orderBy = "score";
        System.out.println("=== ORDER BY 动态字段 ===");
        System.out.println("错误写法 ORDER BY #{orderBy} -> ORDER BY ?（按常量排序，等于没排）");
        System.out.println("正确写法 ORDER BY ${orderBy} -> ORDER BY " + safeColumn(orderBy));

        String bad = "score; DROP TABLE student";
        System.out.println("恶意字段 " + bad + " -> 白名单校验后：" + safeColumn(bad));
    }

    private static String toPrepared(String sql) {
        return sql.replaceAll("#\\{\\w+}", "?");
    }

    private static String toConcatenated(String sql, Map<String, Object> params) {
        Matcher m = Pattern.compile("\\$\\{(\\w+)}").matcher(sql);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            Object v = params.get(m.group(1));
            m.appendReplacement(sb, Matcher.quoteReplacement(String.valueOf(v)));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String extractParams(String sql, Map<String, Object> params) {
        Matcher m = Pattern.compile("#\\{(\\w+)}").matcher(sql);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            if (!sb.isEmpty()) {
                sb.append(", ");
            }
            sb.append(params.get(m.group(1)));
        }
        return "[" + sb + "]";
    }

    private static String safeColumn(String col) {
        // 白名单：只允许已知列名
        return switch (col) {
            case "id", "name", "age", "score" -> col;
            default -> "id";
        };
    }
}
```

**课后练习**
1. 写一个三参数查询方法，分别用 `@Param` 命名和默认 `arg0/arg1` 取值，对比可读性。
2. 用 `ORDER BY ${column}` 实现前端可选排序，并加上白名单校验。
3. 解释为什么 `#{}` 能防注入而 `${}` 不能，从 SQL 编译过程的角度回答。

### 23-05 结果映射resultMap

**知识点**
- `resultType` 自动映射的前提：**列名与属性名一致**（忽略大小写，`user_name` 与 `userName` 不一致）。
- 两种解决列名不匹配的办法：SQL 里写别名（`user_name AS userName`），或用 `resultMap` 显式映射。
- `resultMap` 核心子标签：`id`（主键，提升性能）、`result`（普通列）、`association`（一对一）、`collection`（一对多）。
- 开启驼峰映射：`<setting name="mapUnderscoreToCamelCase" value="true"/>`，可省掉大量 `resultMap`。
- `resultType` 可以是基本类型、POJO、`Map`；查多行时 `resultType` 仍写单条类型，方法返回 `List<T>`。
- 易错：`resultType` 写成 `java.util.List` 会报错，MyBatis 要的是"元素类型"。

**示例代码**
```java
import java.lang.reflect.Field;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ResultMapDemo {
    public static void main(String[] args) throws Exception {
        // 模拟一行结果集：列名是下划线风格，和 Java 属性名不一致
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", 1);
        row.put("user_name", "张三");
        row.put("user_age", 20);
        row.put("class_id", 3);

        System.out.println("数据库返回的列：" + row.keySet());
        System.out.println("目标对象属性：" + fieldNames(Student.class));
        System.out.println();

        // 1. 自动映射（不开驼峰）：user_name 对不上 userName，映射失败
        System.out.println("=== 自动映射（关闭驼峰） ===");
        Student s1 = autoMapping(row, Student.class, false);
        System.out.println(s1);
        System.out.println("-> userName/userAge 为 null，因为列名与属性名不一致");
        System.out.println();

        // 2. 开启驼峰映射
        System.out.println("=== 自动映射（开启驼峰 mapUnderscoreToCamelCase） ===");
        Student s2 = autoMapping(row, Student.class, true);
        System.out.println(s2);
        System.out.println();

        // 3. resultMap 显式映射
        System.out.println("=== resultMap 显式映射 ===");
        Map<String, String> resultMap = new HashMap<>();
        resultMap.put("id", "id");
        resultMap.put("user_name", "userName");
        resultMap.put("user_age", "userAge");
        resultMap.put("class_id", "classId");
        Student s3 = explicitMapping(row, Student.class, resultMap);
        System.out.println(s3);
        System.out.println();

        // 4. 用 SQL 别名也能达到同样效果
        System.out.println("=== SQL 别名方案 ===");
        System.out.println("SELECT id, user_name AS userName, user_age AS userAge, class_id AS classId FROM student");
        System.out.println("-> 列名直接变成驼峰，resultType 就能自动映射，无需 resultMap");

        System.out.println();
        System.out.println("=== 常见错误 ===");
        System.out.println("resultType=\"java.util.List\" -> 报错，应写元素类型 Student，方法返回 List<Student>");
    }

    private static Student autoMapping(Map<String, Object> row, Class<Student> type,
                                       boolean camelCase) throws Exception {
        Student s = type.getDeclaredConstructor().newInstance();
        for (Map.Entry<String, Object> e : row.entrySet()) {
            String col = e.getKey();
            String prop = camelCase ? toCamel(col) : col;
            Field f = findField(type, prop);
            if (f != null) {
                f.setAccessible(true);
                f.set(s, e.getValue());
            }
        }
        return s;
    }

    private static Student explicitMapping(Map<String, Object> row, Class<Student> type,
                                           Map<String, String> resultMap) throws Exception {
        Student s = type.getDeclaredConstructor().newInstance();
        for (Map.Entry<String, Object> e : row.entrySet()) {
            String prop = resultMap.get(e.getKey());
            if (prop == null) {
                continue;
            }
            Field f = findField(type, prop);
            if (f != null) {
                f.setAccessible(true);
                f.set(s, e.getValue());
            }
        }
        return s;
    }

    private static String toCamel(String col) {
        StringBuilder sb = new StringBuilder();
        boolean upper = false;
        for (char c : col.toCharArray()) {
            if (c == '_') {
                upper = true;
            } else {
                sb.append(upper ? Character.toUpperCase(c) : c);
                upper = false;
            }
        }
        return sb.toString();
    }

    private static Field findField(Class<?> type, String name) {
        try {
            return type.getDeclaredField(name);
        } catch (NoSuchFieldException e) {
            return null;
        }
    }

    private static List<String> fieldNames(Class<?> type) {
        List<String> names = new ArrayList<>();
        for (Field f : type.getDeclaredFields()) {
            names.add(f.getName());
        }
        return names;
    }

    static class Student {
        private Integer id;
        private String userName;
        private Integer userAge;
        private Integer classId;

        public Integer getId() {
            return id;
        }

        public void setId(Integer id) {
            this.id = id;
        }

        public String getUserName() {
            return userName;
        }

        public void setUserName(String userName) {
            this.userName = userName;
        }

        public Integer getUserAge() {
            return userAge;
        }

        public void setUserAge(Integer userAge) {
            this.userAge = userAge;
        }

        public Integer getClassId() {
            return classId;
        }

        public void setClassId(Integer classId) {
            this.classId = classId;
        }

        @Override
        public String toString() {
            return "Student{id=" + id + ", userName='" + userName + "', userAge=" + userAge
                    + ", classId=" + classId + "}";
        }
    }
}
```

**课后练习**
1. 建一张 `user` 表（`user_name`、`user_age`、`create_time`），分别用别名、驼峰配置、`resultMap` 三种方式映射到 `User` 对象，比较代码量。
2. 说明 `resultMap` 里 `id` 和 `result` 标签的区别，为什么主键要用 `id`。
3. 一个查询返回 5 行数据，`resultType` 该写什么？方法签名该怎么写？

### 23-06 动态SQL

**知识点**
- 动态 SQL 让一条 SQL 应对多组条件，避免为每种组合写一个方法。
- `<if test="条件">` 条件拼接；`test` 里写 OGNL 表达式，判断字符串非空用 `name != null and name != ''`。
- `<where>` 自动加 `WHERE`，并**去掉开头多余的 `AND/OR`**；`<set>` 自动加 `SET` 并去掉末尾多余的逗号。
- `<foreach>` 遍历集合，常用于 `IN (...)` 和批量插入；属性 `collection/item/separator/open/close`。
- `<choose>/<when>/<otherwise>` 相当于 `switch-case`，只会命中一个分支。
- `<sql>` + `<include>` 抽取公共片段，如公共列名列表。
- 易错：`WHERE 1=1` 是老写法，用 `<where>` 更干净；`<foreach>` 的 `collection` 名要和 `@Param` 一致。

**示例代码**
```java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class DynamicSqlBuilder {
    public static void main(String[] args) {
        // 1. <if> + <where>：按需拼接条件
        System.out.println("=== 条件查询 ===");
        System.out.println(selectSql("张三", null, null));
        System.out.println(selectSql(null, 18, null));
        System.out.println(selectSql("张三", 18, 3));
        System.out.println(selectSql(null, null, null));
        System.out.println();

        // 2. <set>：动态更新，自动去掉多余逗号
        System.out.println("=== 动态更新 ===");
        System.out.println(updateSql("李四", null, null));
        System.out.println(updateSql("李四", 22, null));
        System.out.println(updateSql(null, null, 5));
        System.out.println();

        // 3. <foreach>：IN 查询
        System.out.println("=== IN 查询 ===");
        System.out.println(inSql(Arrays.asList(1, 2, 3)));
        System.out.println(inSql(new ArrayList<>()));
        System.out.println();

        // 4. <foreach>：批量插入
        System.out.println("=== 批量插入 ===");
        System.out.println(batchInsert(Arrays.asList("张三", "李四", "王五")));
        System.out.println();

        // 5. <choose>：只命中一个分支
        System.out.println("=== choose 分支 ===");
        System.out.println(chooseSql("id", "1"));
        System.out.println(chooseSql("name", "张三"));
        System.out.println(chooseSql(null, null));
    }

    // 模拟 <where><if>...</if></where>
    private static String selectSql(String name, Integer minAge, Integer classId) {
        StringBuilder sb = new StringBuilder("SELECT id, name, age, class_id FROM student");
        List<String> conds = new ArrayList<>();
        if (name != null && !name.isEmpty()) {
            conds.add("name LIKE CONCAT('%', '" + name + "', '%')");
        }
        if (minAge != null) {
            conds.add("age >= " + minAge);
        }
        if (classId != null) {
            conds.add("class_id = " + classId);
        }
        if (conds.isEmpty()) {
            return sb.toString();
        }
        // <where> 的职责：加 WHERE + 去掉第一个 AND
        return sb + " WHERE " + String.join(" AND ", conds);
    }

    // 模拟 <set><if>...</if></set>
    private static String updateSql(String name, Integer age, Integer classId) {
        List<String> sets = new ArrayList<>();
        if (name != null && !name.isEmpty()) {
            sets.add("name = '" + name + "'");
        }
        if (age != null) {
            sets.add("age = " + age);
        }
        if (classId != null) {
            sets.add("class_id = " + classId);
        }
        if (sets.isEmpty()) {
            return "UPDATE student  -- 没有要更新的字段，MyBatis 会生成非法 SQL 并抛错";
        }
        // <set> 的职责：加 SET + 去掉末尾多余逗号
        return "UPDATE student SET " + String.join(", ", sets) + " WHERE id = ?";
    }

    // 模拟 <foreach collection="ids" item="id" open="(" separator="," close=")">
    private static String inSql(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return "SELECT * FROM student WHERE 1 = 0  -- 空集合必须短路，否则 IN () 是语法错误";
        }
        StringJoinerLike j = new StringJoinerLike("(", ")", ", ");
        for (Integer id : ids) {
            j.add(String.valueOf(id));
        }
        return "SELECT * FROM student WHERE id IN " + j;
    }

    // 模拟批量插入
    private static String batchInsert(List<String> names) {
        StringBuilder sb = new StringBuilder("INSERT INTO student(name, age) VALUES ");
        List<String> rows = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) {
            rows.add("(#{list[" + i + "].name}, #{list[" + i + "].age})");
        }
        return sb + String.join(", ", rows) + "   -- 共 " + names.size() + " 行";
    }

    // 模拟 <choose><when>...<otherwise>
    private static String chooseSql(String by, String value) {
        if ("id".equals(by)) {
            return "SELECT * FROM student WHERE id = " + value;
        } else if ("name".equals(by)) {
            return "SELECT * FROM student WHERE name = '" + value + "'";
        }
        return "SELECT * FROM student ORDER BY id DESC LIMIT 10  -- otherwise 分支";
    }

    // 极简 join 辅助
    static class StringJoinerLike {
        private final String open;
        private final String close;
        private final String sep;
        private final List<String> items = new ArrayList<>();

        StringJoinerLike(String open, String close, String sep) {
            this.open = open;
            this.close = close;
            this.sep = sep;
        }

        void add(String s) {
            items.add(s);
        }

        @Override
        public String toString() {
            return open + String.join(sep, items) + close;
        }
    }
}
```

**课后练习**
1. 写一个"多条件 + 可选排序 + 分页"的动态查询，要求所有分支都能正确生成 SQL。
2. 用 `<foreach>` 实现 `IN` 查询，并处理空集合的情况（提示：`<if test="ids != null and ids.size() > 0">`）。
3. 说明 `<where>` 和 `<set>` 各自帮你处理了哪个容易写错的细节。

### 23-07 注解开发

**知识点**
- MyBatis 支持把 SQL 直接写在接口方法上：`@Select`、`@Insert`、`@Update`、`@Delete`。
- 注解方式省掉 XML，适合简单 SQL；复杂 SQL（动态 SQL、多表关联）仍然推荐 XML。
- `@Param("name")` 给参数命名，是注解方式下多参数的必备写法。
- `@Results`/`@Result` 等价于 `resultMap`；`@Options(useGeneratedKeys = true, keyProperty = "id")` 回填自增主键。
- `@Mapper` 标记接口由 MyBatis 扫描（Spring Boot 中也可用 `@MapperScan` 批量扫描）。
- 易错：同一个方法既在 XML 又在注解里定义，启动会报"mapped statement 重复"；两者选其一。

**示例代码**
```java
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.LinkedHashMap;
import java.util.Map;

public class AnnotationMapperDemo {
    public static void main(String[] args) throws Exception {
        // 运行时读取接口上的注解，等价于 MyBatis 启动时的 Mapper 解析过程
        Map<String, String> statements = new LinkedHashMap<>();
        Class<?> mapper = StudentMapper.class;

        for (Method m : mapper.getDeclaredMethods()) {
            String sql = null;
            String kind = null;
            if (m.isAnnotationPresent(Select.class)) {
                sql = m.getAnnotation(Select.class).value();
                kind = "SELECT";
            } else if (m.isAnnotationPresent(Insert.class)) {
                sql = m.getAnnotation(Insert.class).value();
                kind = "INSERT";
            } else if (m.isAnnotationPresent(Update.class)) {
                sql = m.getAnnotation(Update.class).value();
                kind = "UPDATE";
            } else if (m.isAnnotationPresent(Delete.class)) {
                sql = m.getAnnotation(Delete.class).value();
                kind = "DELETE";
            }
            if (sql == null) {
                continue;
            }
            statements.put(mapper.getName() + "." + m.getName(), sql);
            StringBuilder paramDesc = new StringBuilder();
            for (Parameter p : m.getParameters()) {
                if (!paramDesc.isEmpty()) {
                    paramDesc.append(", ");
                }
                paramDesc.append(p.isAnnotationPresent(Param.class)
                        ? p.getAnnotation(Param.class).value()
                        : p.getName());
            }
            System.out.println("[" + kind + "] " + m.getName() + "(" + paramDesc + ")");
            System.out.println("        " + sql.replaceAll("\\s+", " "));
        }

        System.out.println();
        System.out.println("共解析出 " + statements.size() + " 条 statement");
        System.out.println();

        // 演示 useGeneratedKeys 回填主键
        Student s = new Student(0, "张三", 20);
        System.out.println("插入前：" + s);
        s.id = 101;   // 模拟 @Options(useGeneratedKeys = true, keyProperty = "id")
        System.out.println("插入后：" + s + "   <- 主键已回填");

        System.out.println();
        System.out.println("注意：同一方法不要同时用注解和 XML 定义，否则启动报 mapped statement 重复。");
    }

    // ---- 自定义注解（对应 MyBatis 的注解）----
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface Select {
        String value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface Insert {
        String value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface Update {
        String value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface Delete {
        String value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.PARAMETER)
    @interface Param {
        String value();
    }

    // ---- Mapper 接口 ----
    interface StudentMapper {
        @Select("SELECT id, name, age FROM student WHERE id = #{id}")
        Student findById(@Param("id") int id);

        @Select("SELECT id, name, age FROM student WHERE name LIKE CONCAT('%', #{name}, '%')")
        java.util.List<Student> findByName(@Param("name") String name);

        @Insert("INSERT INTO student(name, age) VALUES (#{name}, #{age})")
        int insert(Student student);

        @Update("UPDATE student SET name = #{name}, age = #{age} WHERE id = #{id}")
        int update(Student student);

        @Delete("DELETE FROM student WHERE id = #{id}")
        int deleteById(@Param("id") int id);
    }

    static class Student {
        int id;
        String name;
        int age;

        Student(int id, String name, int age) {
            this.id = id;
            this.name = name;
            this.age = age;
        }

        @Override
        public String toString() {
            return "Student{id=" + id + ", name='" + name + "', age=" + age + "}";
        }
    }
}
```

**课后练习**
1. 把 23-03 练习里的 `CourseMapper` 改写成注解方式，对比 XML 与注解的优缺点。
2. 用 `@Options(useGeneratedKeys = true, keyProperty = "id")` 实现插入后拿到自增 id，并验证。
3. 解释为什么复杂动态 SQL 不适合用注解写，给出你的判断标准。

### 23-08 关联查询（一对一与一对多）

**知识点**
- 一对一（学生 → 班级）：`<association property="clazz" javaType="Clazz">`，也可用"分步查询"（`select` + `column`）。
- 一对多（班级 → 学生）：`<collection property="students" ofType="Student">`。
- 两种实现路径：**嵌套结果**（一条 JOIN SQL，用 `resultMap` 分组）和**嵌套查询**（两条 SQL，N+1 问题）。
- 嵌套结果性能更好（一次查询），嵌套查询代码更清晰但可能触发 N+1，需要开启延迟加载。
- `resultMap` 的 `id` 标签在一对多里很关键：MyBatis 靠它判断"哪几行属于同一个主对象"。
- 易错：一对多忘记写 `id`，会导致每行都 new 一个班级对象，集合里全是重复班级。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AssociationDemo {
    public static void main(String[] args) {
        // 模拟一条 JOIN 查询的结果集（班级列重复出现）
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(row(1, "一班", 101, "张三"));
        rows.add(row(1, "一班", 102, "李四"));
        rows.add(row(2, "二班", 103, "王五"));

        System.out.println("=== JOIN 结果集 ===");
        System.out.println("class_id | class_name | stu_id | stu_name");
        rows.forEach(r -> System.out.printf("%8s | %10s | %6s | %8s%n",
                r.get("class_id"), r.get("class_name"), r.get("stu_id"), r.get("stu_name")));
        System.out.println();

        // 嵌套结果映射：按 class_id 分组
        System.out.println("=== 嵌套结果（resultMap + collection）===");
        Map<Integer, Clazz> clazzMap = new LinkedHashMap<>();
        for (Map<String, Object> r : rows) {
            int cid = (int) r.get("class_id");
            Clazz c = clazzMap.computeIfAbsent(cid,
                    k -> new Clazz(cid, (String) r.get("class_name")));
            c.students.add(new Student((int) r.get("stu_id"), (String) r.get("stu_name")));
        }
        clazzMap.values().forEach(System.out::println);
        System.out.println("-> 只发了 1 条 SQL");
        System.out.println();

        // 如果 resultMap 漏写 <id>，会退化成"每行一个班级"
        System.out.println("=== 漏写 <id> 的后果 ===");
        List<Clazz> wrong = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Clazz c = new Clazz((int) r.get("class_id"), (String) r.get("class_name"));
            c.students.add(new Student((int) r.get("stu_id"), (String) r.get("stu_name")));
            wrong.add(c);
        }
        wrong.forEach(System.out::println);
        System.out.println("-> 3 个班级对象，其中一班重复了两次");
        System.out.println();

        // 嵌套查询（N+1）：1 条查班级 + N 条查学生
        System.out.println("=== 嵌套查询（N+1 问题）===");
        List<Clazz> classes = List.of(new Clazz(1, "一班"), new Clazz(2, "二班"));
        int sqlCount = 1;
        System.out.println("  [SQL 1] SELECT * FROM class");
        for (Clazz c : classes) {
            sqlCount++;
            System.out.println("  [SQL " + sqlCount + "] SELECT * FROM student WHERE class_id = " + c.id);
        }
        System.out.println("-> 共 " + sqlCount + " 条 SQL，班级越多越慢（N+1）");
        System.out.println();

        // 一对一：学生 -> 班级
        System.out.println("=== 一对一（association）===");
        Student stu = new Student(101, "张三");
        stu.clazz = clazzMap.get(1);
        System.out.println(stu.name + " 属于 " + stu.clazz.name);
    }

    private static Map<String, Object> row(int cid, String cname, int sid, String sname) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("class_id", cid);
        m.put("class_name", cname);
        m.put("stu_id", sid);
        m.put("stu_name", sname);
        return m;
    }

    static class Clazz {
        int id;
        String name;
        List<Student> students = new ArrayList<>();

        Clazz(int id, String name) {
            this.id = id;
            this.name = name;
        }

        @Override
        public String toString() {
            return name + "(id=" + id + ") " + students;
        }
    }

    static class Student {
        int id;
        String name;
        Clazz clazz;

        Student(int id, String name) {
            this.id = id;
            this.name = name;
        }

        @Override
        public String toString() {
            return name + "(id=" + id + ")";
        }
    }
}
```

**课后练习**
1. 用嵌套结果实现"班级 → 学生"一对多查询，检查返回的班级数量是否正确。
2. 用嵌套查询实现同样的功能，统计实际发出的 SQL 条数，说明什么是 N+1。
3. 说明 `association` 的 `javaType` 和 `collection` 的 `ofType` 分别指什么，为什么不能混用。

### 23-09 缓存机制

**知识点**
- 一级缓存（`SqlSession` 级）：默认开启，同一个会话内相同 SQL + 相同参数只查一次库。
- 一级缓存失效的四种情况：`commit`/`close`、执行增删改、`clearCache()`、查询参数不同。
- 二级缓存（`Mapper` 级 / namespace 级）：跨会话共享，默认关闭，需在 XML 加 `<cache/>` 且实体类实现 `Serializable`。
- 二级缓存的坑：多表关联时，一个表更新不会让另一个 namespace 的缓存失效，容易出现脏数据；生产环境更常用 Redis 做集中缓存。
- `useCache="false"` 可对单条查询关闭二级缓存；`flushCache="true"` 让语句执行后清空缓存。
- 易错：把"一级缓存"当成"跨请求缓存"——它只在同一个 `SqlSession` 里有效，Web 请求间不共享。

**示例代码**
```java
import java.util.HashMap;
import java.util.Map;

public class CacheDemo {
    public static void main(String[] args) {
        System.out.println("=== 一级缓存（同一 SqlSession）===");
        SqlSession session = new SqlSession();
        System.out.println("第 1 次查：" + session.select("SELECT * FROM student WHERE id = 1"));
        System.out.println("第 2 次查：" + session.select("SELECT * FROM student WHERE id = 1"));
        System.out.println("命中缓存，未访问数据库");
        System.out.println("第 3 次查（不同参数）：" + session.select("SELECT * FROM student WHERE id = 2"));
        System.out.println();

        System.out.println("=== 执行 update 后一级缓存被清空 ===");
        session.update("UPDATE student SET name = '张三丰' WHERE id = 1");
        System.out.println("再查：" + session.select("SELECT * FROM student WHERE id = 1"));
        System.out.println("-> 缓存已失效，重新查库");
        System.out.println();

        System.out.println("=== commit 也会清空一级缓存 ===");
        session.select("SELECT * FROM student WHERE id = 1");
        session.commit();
        session.select("SELECT * FROM student WHERE id = 1");
        System.out.println();

        System.out.println("=== 二级缓存（跨 SqlSession，namespace 级）===");
        SecondLevelCache cache = new SecondLevelCache();
        SqlSession s1 = new SqlSession(cache);
        SqlSession s2 = new SqlSession(cache);
        System.out.println("s1 查询：" + s1.select("SELECT * FROM student WHERE id = 1"));
        System.out.println("s1 关闭（一级缓存销毁，数据写入二级缓存）");
        s1.close();
        System.out.println("s2 查询：" + s2.select("SELECT * FROM student WHERE id = 1"));
        System.out.println("-> 命中二级缓存，未访问数据库");
        System.out.println();

        System.out.println("=== 二级缓存的坑 ===");
        System.out.println("namespace=StudentMapper 更新了 student 表，但 namespace=ScoreMapper");
        System.out.println("的缓存不会失效，可能读到旧的关联数据（脏读）");
        System.out.println("-> 多表操作建议关闭二级缓存，改用 Redis 统一管理");
    }

    static class SqlSession implements AutoCloseable {
        private final Map<String, String> level1 = new HashMap<>();
        private final SecondLevelCache level2;
        private int dbHits = 0;

        SqlSession() {
            this(null);
        }

        SqlSession(SecondLevelCache level2) {
            this.level2 = level2;
        }

        String select(String sql) {
            if (level1.containsKey(sql)) {
                return level1.get(sql) + "   [一级缓存]";
            }
            if (level2 != null && level2.has(sql)) {
                String v = level2.get(sql);
                level1.put(sql, v);
                return v + "   [二级缓存]";
            }
            dbHits++;
            String value = "结果集#" + dbHits;
            level1.put(sql, value);
            return value + "   [查询数据库]";
        }

        void update(String sql) {
            dbHits++;
            System.out.println("  [SQL] " + sql + "   -> 清空一级缓存");
            level1.clear();
            if (level2 != null) {
                level2.clear();
            }
        }

        void commit() {
            System.out.println("  [事务] commit -> 清空一级缓存");
            level1.clear();
        }

        @Override
        public void close() {
            if (level2 != null) {
                level2.putAll(level1);
            }
            level1.clear();
        }
    }

    static class SecondLevelCache {
        private final Map<String, String> data = new HashMap<>();

        boolean has(String sql) {
            return data.containsKey(sql);
        }

        String get(String sql) {
            return data.get(sql);
        }

        void putAll(Map<String, String> m) {
            data.putAll(m);
        }

        void clear() {
            data.clear();
        }
    }
}
```

**课后练习**
1. 写一段代码验证"同一个 `SqlSession` 内两次相同查询只发一次 SQL"。
2. 列出让一级缓存失效的四种操作，并各自举例。
3. 说明二级缓存为什么在多表场景下容易产生脏数据，给出你的规避方案。

### 23-10 综合案例：学生管理持久层

**知识点**
- 真实项目的持久层分层：`entity`（实体）→ `mapper`（接口 + XML）→ `service`（业务）→ `controller`（接口）。
- Mapper 只负责"一张表的 SQL"，不要在里面写业务判断；事务边界放在 service 层。
- 分页查询需要两条 SQL：`COUNT(*)` 查总数 + `LIMIT` 查当页数据。
- 批量操作优先用 `<foreach>` 拼一条 SQL，比循环调用 N 次效率高得多。
- 参数对象（Query 对象）比一长串形参更好维护，也方便动态 SQL 复用。
- 易错：`LIMIT` 的偏移量要自己算 `(pageNum - 1) * pageSize`，不是 `pageNum * pageSize`。

**示例代码**
```java
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class StudentMapperCase {
    public static void main(String[] args) {
        StudentRepository repo = new StudentRepository();
        repo.save(new Student(0, "张三", 20, 1));
        repo.save(new Student(0, "李四", 21, 1));
        repo.save(new Student(0, "王五", 19, 2));
        repo.save(new Student(0, "赵六", 22, 2));
        repo.save(new Student(0, "孙七", 20, 3));

        System.out.println("=== 全部学生 ===");
        repo.findAll().forEach(System.out::println);
        System.out.println();

        System.out.println("=== 按条件查询（班级=1 且年龄>=20）===");
        StudentQuery q = new StudentQuery();
        q.classId = 1;
        q.minAge = 20;
        repo.findByQuery(q).forEach(System.out::println);
        System.out.println();

        System.out.println("=== 分页查询（第 2 页，每页 2 条）===");
        Page<Student> page = repo.findPage(2, 2);
        System.out.println("总条数=" + page.total + "，总页数=" + page.totalPages()
                + "，当前页=" + page.pageNum);
        page.records.forEach(System.out::println);
        System.out.println();

        System.out.println("=== 按 id 批量查询 ===");
        repo.findByIds(List.of(1, 3, 5)).forEach(System.out::println);
        System.out.println();

        System.out.println("=== 更新与删除 ===");
        Student s = repo.findById(2);
        s.age = 23;
        System.out.println("更新影响行数：" + repo.update(s));
        System.out.println("删除 id=5 影响行数：" + repo.deleteById(5));
        System.out.println("剩余：" + repo.findAll().size() + " 条");
    }

    // ---------- 实体 ----------
    static class Student {
        int id;
        String name;
        int age;
        int classId;

        Student(int id, String name, int age, int classId) {
            this.id = id;
            this.name = name;
            this.age = age;
            this.classId = classId;
        }

        @Override
        public String toString() {
            return String.format("Student{id=%d, name='%s', age=%d, classId=%d}",
                    id, name, age, classId);
        }
    }

    // ---------- 查询条件对象 ----------
    static class StudentQuery {
        String nameLike;
        Integer minAge;
        Integer classId;
    }

    // ---------- 分页结果 ----------
    record Page<T>(List<T> records, int total, int pageNum, int pageSize) {
        int totalPages() {
            return (total + pageSize - 1) / pageSize;
        }
    }

    // ---------- 持久层 ----------
    static class StudentRepository {
        private final List<Student> table = new ArrayList<>();
        private int seq = 0;

        int save(Student s) {
            s.id = ++seq;
            table.add(s);
            return 1;
        }

        Student findById(int id) {
            return table.stream().filter(s -> s.id == id).findFirst().orElse(null);
        }

        List<Student> findAll() {
            return new ArrayList<>(table);
        }

        // 对应动态 SQL：<if> 拼接条件
        List<Student> findByQuery(StudentQuery q) {
            return table.stream()
                    .filter(s -> q.nameLike == null || s.name.contains(q.nameLike))
                    .filter(s -> q.minAge == null || s.age >= q.minAge)
                    .filter(s -> q.classId == null || s.classId == q.classId)
                    .collect(Collectors.toList());
        }

        // 对应两条 SQL：COUNT(*) + LIMIT offset, size
        Page<Student> findPage(int pageNum, int pageSize) {
            int total = table.size();
            int offset = (pageNum - 1) * pageSize;   // 易错点：不是 pageNum * pageSize
            List<Student> records = table.stream()
                    .skip(Math.max(0, offset))
                    .limit(pageSize)
                    .collect(Collectors.toList());
            return new Page<>(records, total, pageNum, pageSize);
        }

        // 对应 <foreach collection="ids">
        List<Student> findByIds(List<Integer> ids) {
            return table.stream().filter(s -> ids.contains(s.id)).collect(Collectors.toList());
        }

        int update(Student s) {
            for (int i = 0; i < table.size(); i++) {
                if (table.get(i).id == s.id) {
                    table.set(i, s);
                    return 1;
                }
            }
            return 0;
        }

        int deleteById(int id) {
            return table.removeIf(s -> s.id == id) ? 1 : 0;
        }
    }
}
```

**课后练习**
1. 给 `StudentRepository` 增加"按班级分组统计人数"的方法，返回 `Map<Integer, Long>`。
2. 实现分页查询的完整逻辑，并用 5 条数据验证第 1/2/3 页的结果与总页数。
3. 把本案例的 Repository 换成真实 MyBatis Mapper：写出 `StudentMapper` 接口、`StudentMapper.xml` 和 `service` 层事务控制代码。
