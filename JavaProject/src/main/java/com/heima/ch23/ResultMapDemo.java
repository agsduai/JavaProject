/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：23-05
 * 课名：结果映射resultMap
 * ------------------------------------------------------------
 * 知识点：
 *   - `resultType` 自动映射的前提：**列名与属性名一致**（忽略大小写，`user_name` 与 `userName` 不一致）。
 *   - 两种解决列名不匹配的办法：SQL 里写别名（`user_name AS userName`），或用 `resultMap` 显式映射。
 *   - `resultMap` 核心子标签：`id`（主键，提升性能）、`result`（普通列）、`association`（一对一）、`collection`（一对多）。
 *   - 开启驼峰映射：`<setting name="mapUnderscoreToCamelCase" value="true"/>`，可省掉大量 `resultMap`。
 *   - `resultType` 可以是基本类型、POJO、`Map`；查多行时 `resultType` 仍写单条类型，方法返回 `List<T>`。
 *   - 易错：`resultType` 写成 `java.util.List` 会报错，MyBatis 要的是"元素类型"。
 * ============================================================
 */

package com.heima.ch23;

import java.lang.reflect.Field;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

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

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 建一张 `user` 表（`user_name`、`user_age`、`create_time`），分别用别名、驼峰配置、`resultMap` 三种方式映射到 `User` 对象，比较代码量。
 * 2. 说明 `resultMap` 里 `id` 和 `result` 标签的区别，为什么主键要用 `id`。
 * 3. 一个查询返回 5 行数据，`resultType` 该写什么？方法签名该怎么写？
 * ============================================================
 */
