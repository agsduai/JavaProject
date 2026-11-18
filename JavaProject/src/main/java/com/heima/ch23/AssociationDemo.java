/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：23-08
 * 课名：关联查询（一对一与一对多）
 * ------------------------------------------------------------
 * 知识点：
 *   - 一对一（学生 → 班级）：`<association property="clazz" javaType="Clazz">`，也可用"分步查询"（`select` + `column`）。
 *   - 一对多（班级 → 学生）：`<collection property="students" ofType="Student">`。
 *   - 两种实现路径：**嵌套结果**（一条 JOIN SQL，用 `resultMap` 分组）和**嵌套查询**（两条 SQL，N+1 问题）。
 *   - 嵌套结果性能更好（一次查询），嵌套查询代码更清晰但可能触发 N+1，需要开启延迟加载。
 *   - `resultMap` 的 `id` 标签在一对多里很关键：MyBatis 靠它判断"哪几行属于同一个主对象"。
 *   - 易错：一对多忘记写 `id`，会导致每行都 new 一个班级对象，集合里全是重复班级。
 * ============================================================
 */

package com.heima.ch23;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

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

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 用嵌套结果实现"班级 → 学生"一对多查询，检查返回的班级数量是否正确。
 * 2. 用嵌套查询实现同样的功能，统计实际发出的 SQL 条数，说明什么是 N+1。
 * 3. 说明 `association` 的 `javaType` 和 `collection` 的 `ofType` 分别指什么，为什么不能混用。
 * ============================================================
 */
