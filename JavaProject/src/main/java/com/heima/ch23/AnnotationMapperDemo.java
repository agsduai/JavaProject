/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：23-07
 * 课名：注解开发
 * ------------------------------------------------------------
 * 知识点：
 *   - MyBatis 支持把 SQL 直接写在接口方法上：`@Select`、`@Insert`、`@Update`、`@Delete`。
 *   - 注解方式省掉 XML，适合简单 SQL；复杂 SQL（动态 SQL、多表关联）仍然推荐 XML。
 *   - `@Param("name")` 给参数命名，是注解方式下多参数的必备写法。
 *   - `@Results`/`@Result` 等价于 `resultMap`；`@Options(useGeneratedKeys = true, keyProperty = "id")` 回填自增主键。
 *   - `@Mapper` 标记接口由 MyBatis 扫描（Spring Boot 中也可用 `@MapperScan` 批量扫描）。
 *   - 易错：同一个方法既在 XML 又在注解里定义，启动会报"mapped statement 重复"；两者选其一。
 * ============================================================
 */

package com.heima.ch23;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.LinkedHashMap;
import java.util.Map;

// ==================== 示例代码 ====================

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

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 把 23-03 练习里的 `CourseMapper` 改写成注解方式，对比 XML 与注解的优缺点。
 * 2. 用 `@Options(useGeneratedKeys = true, keyProperty = "id")` 实现插入后拿到自增 id，并验证。
 * 3. 解释为什么复杂动态 SQL 不适合用注解写，给出你的判断标准。
 * ============================================================
 */
