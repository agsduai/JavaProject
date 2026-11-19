# 第20章 MySQL基础

本章开始接触数据库：先理解数据库与表的概念，掌握建库建表与常用数据类型；再熟练 CRUD、条件查询、排序分页与聚合统计；最后学习多表关联、约束、事务与索引。数据库是"数据真正落地"的地方，也是后面 JDBC 与项目实战的前提。

### 20-01 数据库基础与MySQL安装使用

**知识点**
- 数据库（Database）按照一定结构组织、存储和管理数据的仓库；MySQL 是关系型数据库，数据存放在"表"中。
- 关系型数据库的核心概念：数据库（schema）→ 表（table）→ 行（记录）→ 列（字段）。
- 常用客户端：命令行 `mysql -u root -p`、Navicat、DataGrip、DBeaver、IDEA 内置 Database 工具。
- 常用命令：`show databases;` 查看所有库、`use 库名;` 切换库、`show tables;` 查看表、`desc 表名;` 查看表结构。
- SQL 分类：DDL（定义，建库建表）、DML（操作，增删改）、DQL（查询）、DCL（权限控制）。
- 易错：MySQL 语句以分号结尾；库名、表名、字段名推荐小写 + 下划线，避免大小写敏感性带来的问题。

**示例代码**
```sql
-- 查看当前 MySQL 版本
select version();

-- 查看所有数据库
show databases;

-- 创建数据库（指定字符集，避免中文乱码）
create database if not exists heima_db
    default character set utf8mb4
    default collate utf8mb4_general_ci;

-- 使用数据库
use heima_db;

-- 查看当前所在数据库
select database();

-- 查看库中所有表
show tables;

-- 删除数据库（谨慎操作）
-- drop database heima_db;
```

```java
public class MySqlBasics {
    // 把常用 SQL 以文本块集中管理，方便复制到客户端执行，也便于版本控制
    private static final String CREATE_DB = """
            create database if not exists heima_db
                default character set utf8mb4
                default collate utf8mb4_general_ci;
            """;

    private static final String[] CHECKS = {
            "select version();      -- 查看版本",
            "select database();     -- 查看当前库",
            "show tables;           -- 查看当前库的表",
            "desc student;          -- 查看表结构"
    };

    public static void main(String[] args) {
        System.out.println("【建库脚本】");
        System.out.println(CREATE_DB);
        System.out.println("【环境检查】");
        for (String sql : CHECKS) {
            System.out.println("  " + sql);
        }
    }
}
```

**课后练习**
1. 安装并启动 MySQL，用命令行登录，创建名为 `practice_db` 的数据库并指定字符集为 utf8mb4。
2. 分别用命令行和图形化客户端查看 `practice_db` 中的所有表，说明两种方式的优缺点。
3. 写出查看 MySQL 版本、查看当前数据库、查看表结构的 SQL 语句。

### 20-02 建表与数据类型

**知识点**
- 建表语法：`create table 表名 (字段名 类型 [约束] [comment '注释'], ...) engine=InnoDB default charset=utf8mb4;`
- 常用数值类型：`tinyint`（1 字节）、`int`（4 字节）、`bigint`（8 字节）、`decimal(m,d)`（精确小数，金额必用）。
- 常用字符串类型：`char(n)` 定长、`varchar(n)` 变长、`text` 长文本；手机号用 `varchar`，因为要存前导 0。
- 日期类型：`date`（年月日）、`time`（时分秒）、`datetime`（年月日时分秒）、`timestamp`（时间戳，受时区影响）。
- 表选项：`engine=InnoDB` 支持事务和外键，是 MySQL 默认存储引擎；`charset=utf8mb4` 支持 emoji。
- 易错：金额用 `double` 会丢精度，必须用 `decimal(10,2)`；`varchar` 长度按字符计，不是字节。

**示例代码**
```sql
use heima_db;

-- 学生表
create table if not exists student (
    id          int primary key auto_increment comment '主键ID',
    name        varchar(20) not null comment '姓名',
    age         tinyint unsigned comment '年龄',
    gender      char(1) comment '性别：男/女',
    phone       varchar(11) comment '手机号',
    birthday    date comment '生日',
    create_time datetime default current_timestamp comment '创建时间'
) engine = InnoDB default charset = utf8mb4 comment '学生信息表';

-- 商品表：金额使用 decimal
create table if not exists product (
    id          int primary key auto_increment,
    title       varchar(100) not null comment '商品名称',
    price       decimal(10, 2) not null comment '价格',
    stock       int unsigned default 0 comment '库存',
    description text comment '描述'
) engine = InnoDB default charset = utf8mb4 comment '商品表';

-- 查看表结构
desc student;
show create table product;
```

```java
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Java 类型与 MySQL 类型的对应关系，写实体类时按这张表来选类型。
 */
public class TypeMapping {
    public static void main(String[] args) {
        // 小整数 -> tinyint -> byte / Integer
        byte age = 18;
        // 普通整数 -> int -> Integer
        int id = 1;
        // 大整数 -> bigint -> Long
        long orderNo = 20261117001L;
        // 金额 -> decimal(10,2) -> BigDecimal（绝不用 double）
        BigDecimal price = new BigDecimal("19.90");
        // 定长字符串 -> char(1) -> String
        String gender = "男";
        // 变长字符串 -> varchar(20) -> String
        String name = "张三";
        // 日期 -> date -> LocalDate
        LocalDate birthday = LocalDate.of(2008, 5, 12);
        // 日期时间 -> datetime -> LocalDateTime
        LocalDateTime createTime = LocalDateTime.now();

        System.out.println("id=" + id + "（int）");
        System.out.println("age=" + age + "（tinyint）");
        System.out.println("orderNo=" + orderNo + "（bigint）");
        System.out.println("price=" + price + "（decimal）");
        System.out.println("gender=" + gender + "（char(1)）");
        System.out.println("name=" + name + "（varchar(20)）");
        System.out.println("birthday=" + birthday + "（date）");
        System.out.println("createTime=" + createTime + "（datetime）");

        // 金额计算用 BigDecimal，避免 0.1 + 0.2 这类精度问题
        BigDecimal a = new BigDecimal("0.1");
        BigDecimal b = new BigDecimal("0.2");
        System.out.println("0.1 + 0.2 = " + a.add(b));
        System.out.println("double 版 = " + (0.1 + 0.2));
    }
}
```

**课后练习**
1. 创建一张 `book` 表，包含编号（主键自增）、书名、作者、价格、出版日期、库存六个字段，并给每个字段加注释。
2. 创建一张 `employee` 表，包含工号、姓名、性别、手机号、入职时间、月薪，思考每个字段该用什么类型并说明理由。
3. 用 `show create table` 查看自己创建的表，解释输出中 `engine` 和 `charset` 的含义。

### 20-03 数据的增删改

**知识点**
- 插入：`insert into 表名 (字段1, 字段2) values (值1, 值2);` 或省略字段名（值必须按建表顺序且全部给出）。
- 批量插入：`insert into 表名 (字段...) values (...), (...), (...);` 一条语句插入多行效率更高。
- 更新：`update 表名 set 字段1=值1, 字段2=值2 where 条件;` 不写 `where` 会更新全表。
- 删除：`delete from 表名 where 条件;` 不写 `where` 会删除全表数据（表结构还在）。
- `truncate table 表名` 清空表，比 `delete` 快且自增主键归零，但不可回滚、不触发触发器。
- 易错：`update`/`delete` 忘记 `where` 是生产事故高发点，执行前先用 `select` 验证条件。

**示例代码**
```sql
use heima_db;

-- 插入单条记录（指定字段）
insert into student (name, age, gender, phone, birthday)
values ('张三', 18, '男', '13800138000', '2008-05-12');

-- 插入时不写字段名：必须按建表顺序给出全部字段值
insert into student
values (null, '李四', 19, '女', '13900139000', '2007-08-20', now());

-- 批量插入
insert into student (name, age, gender, phone, birthday) values
('王五', 20, '男', '13700137000', '2006-03-01'),
('赵六', 21, '女', '13600136000', '2005-11-23'),
('钱七', 22, '男', '13500135000', '2004-07-15');

-- 更新：只改满足条件的行
update student set age = 19, phone = '13811112222' where name = '张三';

-- 更新多个字段：把所有女生年龄加 1
update student set age = age + 1 where gender = '女';

-- 删除指定记录
delete from student where name = '钱七';

-- 清空表（自增归零，不可回滚）
-- truncate table student;

-- 查看结果
select * from student;
```

```java
/**
 * 增删改的 SQL 在 Java 里通常以常量或方法拼接的方式生成。
 * 注意：真实项目里凡是有用户输入的地方都必须用 PreparedStatement 占位符，不能直接拼字符串。
 */
public class CrudSqlTemplate {
    // 插入模板：字段顺序与占位符一一对应
    public static String insertSql(String table, String... columns) {
        String cols = String.join(", ", columns);
        String marks = String.join(", ", java.util.Collections.nCopies(columns.length, "?"));
        return "insert into " + table + " (" + cols + ") values (" + marks + ")";
    }

    // 更新模板
    public static String updateSql(String table, String setClause, String whereClause) {
        return "update " + table + " set " + setClause + " where " + whereClause;
    }

    // 删除模板
    public static String deleteSql(String table, String whereClause) {
        return "delete from " + table + " where " + whereClause;
    }

    public static void main(String[] args) {
        System.out.println(insertSql("student", "name", "age", "gender"));
        System.out.println(updateSql("student", "age = ?", "name = ?"));
        System.out.println(deleteSql("student", "id = ?"));

        // 危险写法：where 条件为空会删除全表，必须做非空校验
        String where = "";
        if (where == null || where.isBlank()) {
            System.out.println("拒绝执行：" + "delete from student where " + where + "（条件为空）");
        } else {
            System.out.println(deleteSql("student", where));
        }
    }
}
```

**课后练习**
1. 向 `book` 表批量插入 5 条图书记录，其中至少包含一本价格超过 100 元的书。
2. 把 `book` 表中所有价格低于 50 元的书提价 10%，再用查询验证结果。
3. 删除 `book` 表中库存为 0 的记录，并说明 `delete` 和 `truncate` 的区别。

### 20-04 基础查询与条件过滤

**知识点**
- 查询语法顺序：`select 字段列表 from 表名 where 条件 group by 分组 having 过滤 order by 排序 limit 分页`。
- `select *` 查询所有列；`select 字段1, 字段2` 只查指定列，生产环境不推荐 `*`。
- 条件运算符：`=`、`<>`/`!=`、`>`、`<`、`>=`、`<=`、`between ... and ...`、`in (...)`、`like`。
- `like` 通配符：`%` 匹配任意多个字符，`_` 匹配单个字符；`like '%张%'` 表示包含"张"。
- 空值判断必须用 `is null` / `is not null`，不能用 `= null`。
- 别名：`select name as 姓名` 或 `select name 姓名`；`distinct` 去重。
- 易错：`and` 优先级高于 `or`，混用时要用括号明确分组。

**示例代码**
```sql
use heima_db;

-- 查询全部字段
select * from student;

-- 查询指定字段，并起别名
select name as 姓名, age as 年龄, gender 性别 from student;

-- 条件查询：年龄大于 20
select * from student where age > 20;

-- 区间查询
select * from student where age between 18 and 21;

-- 枚举查询
select * from student where gender in ('女');

-- 模糊查询：姓名包含"张"
select * from student where name like '%张%';

-- 模糊查询：姓李且名字只有两个字
select * from student where name like '李_';

-- 空值判断
select * from student where phone is null;

-- 逻辑组合：用括号明确优先级
select * from student where (age >= 20 or gender = '女') and phone is not null;

-- 去重
select distinct gender from student;
```

```java
import java.util.ArrayList;
import java.util.List;

/**
 * 条件查询的动态拼接：实际项目里常按"有没有传参"来决定是否追加条件。
 * 这里只演示拼接逻辑，真正执行时每个值都要用 ? 占位并由 PreparedStatement 绑定。
 */
public class ConditionQueryBuilder {
    public static void main(String[] args) {
        List<String> conditions = new ArrayList<>();
        List<Object> params = new ArrayList<>();

        String name = "张";          // 用户可能不填
        Integer minAge = 18;         // 用户可能不填
        String gender = null;        // 用户没选

        if (name != null && !name.isBlank()) {
            conditions.add("name like ?");
            params.add("%" + name + "%");
        }
        if (minAge != null) {
            conditions.add("age >= ?");
            params.add(minAge);
        }
        if (gender != null && !gender.isBlank()) {
            conditions.add("gender = ?");
            params.add(gender);
        }

        String where = conditions.isEmpty() ? "" : " where " + String.join(" and ", conditions);
        String sql = "select * from student" + where;
        System.out.println("SQL：" + sql);
        System.out.println("参数：" + params);

        // 注意 and 与 or 混用时必须加括号
        System.out.println("错误：" + "where age >= 20 or gender = '女' and phone is not null");
        System.out.println("正确：" + "where (age >= 20 or gender = '女') and phone is not null");
    }
}
```

**课后练习**
1. 查询 `book` 表中价格在 50~100 元之间的书，只显示书名和价格，并用中文别名。
2. 查询 `student` 表中手机号以 `138` 开头的学生，再查询姓名第二个字是"三"的学生。
3. 查询 `product` 表中描述为空且库存大于 0 的商品，说明为什么不能用 `description = null`。

### 20-05 排序、分页与聚合函数

**知识点**
- 排序：`order by 字段 [asc|desc]`，`asc` 升序（默认）、`desc` 降序；多字段排序用逗号分隔，前面的优先。
- 分页：`limit 起始索引, 每页条数`；起始索引 = `(页码 - 1) * 每页条数`。
- 聚合函数：`count(*)` 统计行数、`sum` 求和、`avg` 平均值、`max`/`min` 最值，都会忽略 `null`。
- 分组：`group by 字段` 把相同值归为一组，`select` 中只能出现分组字段和聚合函数。
- `having` 对分组后的结果过滤，`where` 对分组前的行过滤，两者不能混用。
- 易错：`count(字段)` 不统计 `null` 值，`count(*)` 统计所有行；分页索引从 0 开始。

**示例代码**
```sql
use heima_db;

-- 按年龄升序
select * from student order by age asc;

-- 按年龄降序，年龄相同按姓名升序
select * from student order by age desc, name asc;

-- 分页：第 1 页，每页 2 条
select * from student order by id limit 0, 2;

-- 分页：第 2 页
select * from student order by id limit 2, 2;

-- 聚合函数
select count(*) as 总人数 from student;
select count(phone) as 有手机号的人数 from student;
select sum(age) as 年龄总和, avg(age) as 平均年龄,
       max(age) as 最大年龄, min(age) as 最小年龄 from student;

-- 分组统计：每个性别的人数与平均年龄
select gender, count(*) as 人数, round(avg(age), 1) as 平均年龄
from student
group by gender;

-- having：筛选分组后人数大于 2 的性别
select gender, count(*) as 人数
from student
group by gender
having count(*) > 2;

-- 完整语法顺序
select gender, count(*) as 人数
from student
where age >= 18
group by gender
having count(*) >= 1
order by 人数 desc
limit 0, 10;
```

```java
/**
 * 分页参数换算：前端传"第几页、每页几条"，后端换算成 limit 的起始索引。
 * 这是所有列表页接口都要写的逻辑。
 */
public class PageCalculator {
    /** 起始索引 = (页码 - 1) * 每页条数 */
    public static int offset(int pageNo, int pageSize) {
        if (pageNo < 1 || pageSize < 1) {
            throw new IllegalArgumentException("页码和每页条数必须大于 0");
        }
        return (pageNo - 1) * pageSize;
    }

    /** 总页数 = 向上取整(总条数 / 每页条数) */
    public static int totalPages(int totalCount, int pageSize) {
        if (pageSize < 1) {
            throw new IllegalArgumentException("每页条数必须大于 0");
        }
        return (totalCount + pageSize - 1) / pageSize;
    }

    public static String limitClause(int pageNo, int pageSize) {
        return "limit " + offset(pageNo, pageSize) + ", " + pageSize;
    }

    public static void main(String[] args) {
        int total = 25;
        int size = 10;
        System.out.println("总条数 " + total + "，每页 " + size + " 条，共 "
                + totalPages(total, size) + " 页");
        for (int page = 1; page <= totalPages(total, size); page++) {
            System.out.println("第 " + page + " 页 -> " + limitClause(page, size));
        }

        // 最后一页不足一整页也能正常计算
        System.out.println("每页 4 条时共 " + totalPages(total, 4) + " 页");
    }
}
```

**课后练习**
1. 查询 `book` 表中价格最高的 3 本书，输出书名和价格。
2. 按作者分组统计 `book` 表中每位作者的书本数量和平均价格，只显示书本数量大于 1 的作者。
3. 对 `student` 表实现分页查询：每页 3 条，分别写出第 1 页和第 3 页的 SQL。

### 20-06 多表关联查询

**知识点**
- 外键关联：从表通过外键字段指向主表的主键，体现"一对多"关系（如一个班级有多个学生）。
- 内连接 `inner join ... on ...`：只保留两张表都匹配上的行，`inner` 可省略。
- 左外连接 `left join`：保留左表全部行，右表无匹配时补 `null`；右外连接反之。
- 自连接：同一张表起两个别名，用于"员工 → 上级"这类自身关联。
- 子查询：`select ... where 字段 in (select ...)`，注意子查询返回多行时要用 `in`。
- 易错：多表查询时字段重名必须加表名或别名前缀（如 `s.name`），否则报 `ambiguous` 错误。

**示例代码**
```sql
use heima_db;

-- 班级表
create table if not exists classes (
    id    int primary key auto_increment,
    cname varchar(20) not null
) engine = InnoDB default charset = utf8mb4;

-- 学生表增加班级外键
create table if not exists student2 (
    id       int primary key auto_increment,
    name     varchar(20) not null,
    age      tinyint,
    class_id int,
    constraint fk_class foreign key (class_id) references classes (id)
) engine = InnoDB default charset = utf8mb4;

insert into classes (cname) values ('一班'), ('二班'), ('三班');
insert into student2 (name, age, class_id) values
('张三', 18, 1), ('李四', 19, 1), ('王五', 20, 2), ('赵六', 21, null);

-- 内连接：只显示有班级的学生
select s.name, s.age, c.cname
from student2 s
inner join classes c on s.class_id = c.id;

-- 左外连接：显示所有班级，没有学生的班级学生字段为 null
select c.cname, s.name
from classes c
left join student2 s on c.id = s.class_id;

-- 统计每个班级的人数（左连接 + 聚合，没人的班级显示 0）
select c.cname, count(s.id) as 人数
from classes c
left join student2 s on c.id = s.class_id
group by c.id, c.cname;

-- 子查询：查询与张三同班的学生
select name from student2
where class_id = (select class_id from student2 where name = '张三')
  and name <> '张三';

-- 子查询 + in：查询有学生的班级
select cname from classes
where id in (select distinct class_id from student2 where class_id is not null);
```

```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 多表查询的结果在 Java 里通常有两种封装方式：
 *   1) 扁平结构：一行一个对象，把关联字段平铺进来（适合列表展示）
 *   2) 嵌套结构：主表对象里持有一个 List<从表对象>（适合详情展示）
 */
public class JoinResultModel {
    public static void main(String[] args) {
        // 模拟"班级 left join 学生"的查询结果：一行 = 班级名 + 学生名
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"一班", "张三"});
        rows.add(new String[]{"一班", "李四"});
        rows.add(new String[]{"二班", "王五"});
        rows.add(new String[]{"三班", null});   // 没有学生的班级

        // 方式一：扁平结构
        System.out.println("=== 扁平结构 ===");
        for (String[] row : rows) {
            System.out.println("班级：" + row[0] + "，学生：" + row[1]);
        }

        // 方式二：嵌套结构，用 LinkedHashMap 保持查询顺序
        Map<String, List<String>> classToStudents = new LinkedHashMap<>();
        for (String[] row : rows) {
            classToStudents.computeIfAbsent(row[0], k -> new ArrayList<>());
            if (row[1] != null) {                 // 左连接补的 null 要过滤
                classToStudents.get(row[0]).add(row[1]);
            }
        }

        System.out.println("=== 嵌套结构 ===");
        classToStudents.forEach((cname, students) ->
                System.out.println(cname + "（" + students.size() + " 人）：" + students));

        // 统计：人数为 0 的班级也要出现在结果里（左连接 + count 的意义）
        classToStudents.forEach((cname, students) ->
                System.out.println(cname + " 人数 = " + students.size()));
    }
}
```

**课后练习**
1. 创建 `department`（部门）和 `employee`（员工）两张表，建立外键关联，用内连接查询每个员工及其部门名称。
2. 用左外连接查询所有部门及其员工人数，没有员工的部门显示 0 人。
3. 用子查询查询 `book` 表中价格高于平均价格的书籍，再用连接查询改写一次。

### 20-07 约束与事务

**知识点**
- 常用约束：`primary key`（主键，唯一且非空）、`not null`、`unique`、`default`、`foreign key`、`check`。
- 主键推荐用 `int auto_increment` 或 `bigint`，业务字段（如身份证号）不适合做主键。
- 事务：一组要么全部成功、要么全部失败的 SQL 操作；InnoDB 支持事务。
- 事务控制：`start transaction` / `begin` 开启，`commit` 提交，`rollback` 回滚。
- ACID 四大特性：原子性（Atomicity）、一致性（Consistency）、隔离性（Isolation）、持久性（Durability）。
- 隔离级别：读未提交、读已提交、可重复读（MySQL 默认）、串行化；级别越高越安全但并发越差。
- 易错：MySQL 的 DDL（建表、删表）会隐式提交事务，不能回滚。

**示例代码**
```sql
use heima_db;

-- 账户表：余额不能为负（check 约束 MySQL 8.0.16+ 生效）
create table if not exists account (
    id      int primary key auto_increment,
    name    varchar(20) not null,
    balance decimal(10, 2) not null default 0.00,
    constraint chk_balance check (balance >= 0)
) engine = InnoDB default charset = utf8mb4;

insert into account (name, balance) values ('张三', 1000.00), ('李四', 500.00);

-- 转账事务：张三转 200 给李四
start transaction;
update account set balance = balance - 200 where name = '张三';
update account set balance = balance + 200 where name = '李四';
-- 两条都成功才提交
commit;

select * from account;

-- 回滚演示：余额不足时撤销
start transaction;
update account set balance = balance - 5000 where name = '李四';
select * from account where name = '李四';  -- 事务内能看到扣减结果
rollback;                                    -- 撤销，数据恢复
select * from account where name = '李四';

-- 查看当前隔离级别
select @@transaction_isolation;
```

```java
/**
 * 事务在 JDBC 中的写法预演：关闭自动提交 -> 执行多条 SQL -> 全部成功才 commit。
 * 这里用内存账户模拟，逻辑与真实数据库完全一致。
 */
public class TransactionSimulator {
    static class Account {
        String name;
        double balance;

        Account(String name, double balance) {
            this.name = name;
            this.balance = balance;
        }
    }

    // 模拟一张表
    private static final Account A = new Account("张三", 1000);
    private static final Account B = new Account("李四", 500);
    // 事务未提交前的改动先暂存，提交时才写回
    private static Double pendingA, pendingB;

    public static void main(String[] args) {
        boolean ok = transfer(200);
        System.out.println("转账结果：" + ok);
        System.out.println("张三余额：" + A.balance + "，李四余额：" + B.balance);

        // 余额不足 -> 回滚
        boolean fail = transfer(999999);
        System.out.println("超额转账结果：" + fail);
        System.out.println("张三余额：" + A.balance + "，李四余额：" + B.balance);
    }

    /** 返回 true 表示已提交，false 表示已回滚 */
    static boolean transfer(double money) {
        // start transaction
        pendingA = A.balance;
        pendingB = B.balance;
        try {
            if (pendingA < money) {
                throw new IllegalStateException("余额不足");
            }
            pendingA -= money;      // 扣款
            pendingB += money;      // 入账
            if (pendingB < 0) {
                throw new IllegalStateException("入账异常");
            }
            // commit
            A.balance = pendingA;
            B.balance = pendingB;
            return true;
        } catch (Exception e) {
            // rollback：丢弃暂存值，原数据不变
            pendingA = null;
            pendingB = null;
            System.out.println("  回滚原因：" + e.getMessage());
            return false;
        }
    }
}
```

**课后练习**
1. 创建一张 `orders` 表，给订单编号加唯一约束、给金额加非空约束、给状态加默认值，插入数据验证约束是否生效。
2. 用事务模拟银行转账：A 账户转 300 给 B 账户，中途故意制造异常并回滚，验证两个账户余额都未变化。
3. 写出 ACID 四个特性的含义，并说明"可重复读"隔离级别能解决什么问题。

### 20-08 索引与视图

**知识点**
- 索引是帮助 MySQL 快速查找数据的数据结构（B+ 树），类似书的目录，能大幅提升查询速度。
- 创建索引：`create index 索引名 on 表名 (字段);`，删除用 `drop index 索引名 on 表名;`。
- 主键自动创建聚簇索引；`unique` 约束字段自动创建唯一索引。
- 索引失效常见场景：`like '%xx'` 前置通配、字段上使用函数、隐式类型转换、`or` 连接非索引字段。
- 视图是虚拟表，保存的是查询语句而非数据：`create view 视图名 as select ...`。
- 视图作用：简化复杂查询、隐藏敏感字段、统一数据口径。
- 易错：索引不是越多越好，会降低增删改速度并占用空间；小表、低区分度字段不必建索引。

**示例代码**
```sql
use heima_db;

-- 建索引前查看执行计划
explain select * from student2 where name = '张三';

-- 给 name 字段建普通索引
create index idx_student2_name on student2 (name);

-- 再次查看执行计划，观察 key 列是否命中索引
explain select * from student2 where name = '张三';

-- 复合索引：遵循最左前缀原则
create index idx_student2_age_class on student2 (age, class_id);
explain select * from student2 where age = 18;               -- 命中
explain select * from student2 where class_id = 1;           -- 不命中（跳过最左列）

-- 查看表上所有索引
show index from student2;

-- 创建视图：只暴露学生姓名与班级，隐藏年龄
create or replace view v_student_class as
select s.name as 姓名, c.cname as 班级
from student2 s
left join classes c on s.class_id = c.id;

select * from v_student_class;

-- 删除索引与视图
-- drop index idx_student2_name on student2;
-- drop view v_student_class;
```

```java
import java.util.ArrayList;
import java.util.List;

/**
 * 索引失效的判断在代码层面体现为"查询条件是否能让数据库用上索引"。
 * 这里用规则校验的方式，演示代码里如何提前拦截会导致全表扫描的写法。
 */
public class IndexRuleChecker {
    /** 检查 like 写法是否会导致索引失效 */
    static boolean likeUsesIndex(String pattern) {
        return !pattern.startsWith("%") && !pattern.startsWith("_");
    }

    /** 检查条件字段上是否套了函数 */
    static boolean hasFunctionOnColumn(String condition) {
        String lower = condition.toLowerCase();
        return lower.contains("date(") || lower.contains("substr(")
                || lower.contains("upper(") || lower.contains("lower(")
                || lower.contains("+ 0") || lower.contains("cast(");
    }

    public static void main(String[] args) {
        List<String> patterns = new ArrayList<>(List.of("张%", "%张", "%张%", "张_"));
        for (String p : patterns) {
            System.out.println("like '" + p + "' 命中索引：" + likeUsesIndex(p));
        }

        String[] conditions = {
                "name = ?",
                "date(create_time) = '2026-11-17'",
                "substr(name, 1, 1) = '张'",
                "age = ?"
        };
        for (String c : conditions) {
            System.out.println(c + " -> 字段上有函数：" + hasFunctionOnColumn(c));
        }

        // 改正示例：把函数挪到比较值一侧
        System.out.println("改前：date(create_time) = '2026-11-17'");
        System.out.println("改后：create_time >= '2026-11-17 00:00:00' "
                + "and create_time < '2026-11-18 00:00:00'");
    }
}
```

**课后练习**
1. 给 `book` 表的书名和作者字段分别建索引，用 `explain` 对比建索引前后的执行计划差异。
2. 创建一个视图，展示 `book` 表的书名、作者和价格（价格只显示 8 折后的结果），并查询该视图。
3. 列出至少 4 种会导致索引失效的写法，并各给出一个改正示例。

## 本章综合练习

### 综合题：电商库表设计与查询实战

**场景**

给一个小型电商设计数据库：用户、商品、订单、订单明细四张表，写清约束与索引，然后完成 8 条真实业务查询，包括多表关联、分组聚合、排序分页与事务。

**任务**
1. 写出四张表的建表语句，包含主键、外键、非空、唯一、默认值与检查约束，并说明每张表为什么这样选数据类型。
2. 为订单表设计索引：按用户查订单、按时间范围查订单、按状态统计，各写一条索引并说明最左前缀的影响。
3. 写 4 条查询：查某用户的订单列表（含商品名与金额）、统计每月成交额、查销售额前 10 的商品、查从未下单的用户。
4. 用事务演示下单：插入订单 → 扣减库存 → 插入明细，中途人为制造失败，验证回滚后数据一致。

**验收清单**
- [ ] 四张表能成功创建，外键约束生效（插入不存在的商品会失败）。
- [ ] 每条查询结果与手工核对一致，聚合查询没有重复计数。
- [ ] 用 EXPLAIN 能看到索引被命中，未命中时能说出原因。
- [ ] 事务回滚后库存与订单数据都回到初始状态。
