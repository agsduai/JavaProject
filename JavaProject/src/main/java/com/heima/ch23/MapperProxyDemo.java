/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：23-03
 * 课名：Mapper接口与映射文件
 * ------------------------------------------------------------
 * 知识点：
 *   - Mapper 由两部分组成：Java 接口（方法签名）+ XML 映射文件（SQL）。MyBatis 用**动态代理**为接口生成实现，方法名与 SQL 的 `id` 一一对应。
 *   - 两个必须满足的约定：接口全限定名 = XML 的 `namespace`；方法名 = `<select>/<insert>/<update>/<delete>` 的 `id`。
 *   - 参数类型用 `parameterType`（可省略），返回类型用 `resultType`（单条）或 `resultMap`（自定义映射）。
 *   - Mapper 接口不需要实现类，也不应该被 `new`；只能通过 `sqlSession.getMapper(XxxMapper.class)` 获取。
 *   - 返回值约定：查一条返回对象（查不到返回 `null`），查多条返回 `List`，增删改返回影响行数 `int`。
 *   - 易错：`namespace` 写错会抛 `Invalid bound statement (not found)`，这是 MyBatis 最高频的报错，先查 namespace 再查 id。
 * ============================================================
 */

package com.heima.ch23;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

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

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 手写一个 `CourseMapper` 接口和对应 XML，实现按 id 查、查全部、按名称模糊查三个方法。
 * 2. 故意把 XML 的 `namespace` 改错，运行后记录完整报错信息，并说明排查顺序。
 * 3. 解释 MyBatis 为什么能"接口不写实现类"就能调用 SQL，动态代理在其中做了什么。
 * ============================================================
 */
