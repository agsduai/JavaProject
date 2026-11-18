/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：23-10
 * 课名：综合案例：学生管理持久层
 * ------------------------------------------------------------
 * 知识点：
 *   - 真实项目的持久层分层：`entity`（实体）→ `mapper`（接口 + XML）→ `service`（业务）→ `controller`（接口）。
 *   - Mapper 只负责"一张表的 SQL"，不要在里面写业务判断；事务边界放在 service 层。
 *   - 分页查询需要两条 SQL：`COUNT(*)` 查总数 + `LIMIT` 查当页数据。
 *   - 批量操作优先用 `<foreach>` 拼一条 SQL，比循环调用 N 次效率高得多。
 *   - 参数对象（Query 对象）比一长串形参更好维护，也方便动态 SQL 复用。
 *   - 易错：`LIMIT` 的偏移量要自己算 `(pageNum - 1) * pageSize`，不是 `pageNum * pageSize`。
 * ============================================================
 */

package com.heima.ch23;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

// ==================== 示例代码 ====================

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

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 给 `StudentRepository` 增加"按班级分组统计人数"的方法，返回 `Map<Integer, Long>`。
 * 2. 实现分页查询的完整逻辑，并用 5 条数据验证第 1/2/3 页的结果与总页数。
 * 3. 把本案例的 Repository 换成真实 MyBatis Mapper：写出 `StudentMapper` 接口、`StudentMapper.xml` 和 `service` 层事务控制代码。
 * ============================================================
 */
