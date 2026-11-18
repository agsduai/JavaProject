/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：24-09
 * 课名：综合案例：整合MyBatis的学生服务
 * ------------------------------------------------------------
 * 知识点：
 *   - 整合三件套：`mybatis-spring-boot-starter` 依赖 + `spring.datasource.*` 配置 + `@MapperScan("com.heima.mapper")`。
 *   - 分层职责：Controller 只做参数接收与响应封装，Service 做业务与事务，Mapper 只写 SQL。
 *   - `@Transactional` 标在 Service 的 public 方法上，默认只对 `RuntimeException` 回滚；受检异常要写 `rollbackFor = Exception.class`。
 *   - 事务失效的三个常见原因：方法非 public、同类内部调用（不走代理）、异常被自己 try-catch 吃掉。
 *   - 统一响应 + 全局异常 + 事务，构成一个后端接口的最小闭环。
 *   - 易错：`@Transactional` 加在 Controller 上不生效也不合理；事务应该包住"一组必须同时成功的写操作"。
 * ============================================================
 */

package com.heima.ch24;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class StudentServiceCase {
    public static void main(String[] args) {
        StudentRepository repository = new StudentRepository();
        StudentService service = new StudentService(repository);

        System.out.println("=== 新增学生（成功）===");
        System.out.println(service.create("张三", 20));
        System.out.println();

        System.out.println("=== 新增学生（姓名重复，业务异常）===");
        System.out.println(service.create("张三", 21));
        System.out.println();

        System.out.println("=== 批量导入：第 3 条非法，整批回滚 ===");
        List<String> names = List.of("李四", "王五", "   ");
        System.out.println(service.batchCreate(names));
        System.out.println("回滚后库中学生数：" + repository.count() + "（应仍为 1）");
        System.out.println();

        System.out.println("=== 查询 ===");
        System.out.println(service.findById(1));
        System.out.println(service.findById(99));
        System.out.println();

        System.out.println("=== 分页 ===");
        repository.save(new Student(0, "赵六", 22));
        repository.save(new Student(0, "孙七", 19));
        System.out.println(service.page(1, 2));
        System.out.println();

        System.out.println("=== 事务失效的三种情况 ===");
        Map<String, String> pitfalls = new LinkedHashMap<>();
        pitfalls.put("方法不是 public", "代理无法拦截，注解被忽略");
        pitfalls.put("同类内部方法调用", "this.xxx() 不走代理，注解失效");
        pitfalls.put("异常被 try-catch 吞掉", "代理感知不到异常，不会回滚");
        pitfalls.forEach((k, v) -> System.out.println("  " + k + " -> " + v));
    }

    // ---------- 统一响应 ----------
    record Result<T>(int code, String message, T data) {
        static <T> Result<T> ok(T data) {
            return new Result<>(200, "success", data);
        }

        static <T> Result<T> fail(int code, String message) {
            return new Result<>(code, message, null);
        }
    }

    record PageResult<T>(List<T> records, int total, int pageNum, int pageSize) {
    }

    static class BusinessException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        BusinessException(String message) {
            super(message);
        }
    }

    // ---------- 实体 ----------
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

    // ---------- Mapper（模拟 MyBatis 的 SQL 层）----------
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

        boolean existsByName(String name) {
            return table.stream().anyMatch(s -> s.name.equals(name));
        }

        List<Student> findPage(int offset, int size) {
            return table.stream().skip(offset).limit(size).toList();
        }

        int count() {
            return table.size();
        }

        void clear() {
            table.clear();
        }
    }

    // ---------- Service（模拟 @Transactional）----------
    static class StudentService {
        private final StudentRepository repository;

        StudentService(StudentRepository repository) {
            this.repository = repository;
        }

        Result<Student> create(String name, int age) {
            try {
                if (name == null || name.isBlank()) {
                    throw new BusinessException("姓名不能为空");
                }
                if (repository.existsByName(name)) {
                    throw new BusinessException("学生已存在：" + name);
                }
                Student s = new Student(0, name, age);
                repository.save(s);
                return Result.ok(s);
            } catch (BusinessException e) {
                return Result.fail(400, e.getMessage());
            }
        }

        // 模拟 @Transactional(rollbackFor = Exception.class)：任一失败则全部回滚
        Result<List<Student>> batchCreate(List<String> names) {
            List<Student> snapshot = new ArrayList<>(repository.table);
            List<Student> created = new ArrayList<>();
            try {
                for (String name : names) {
                    if (name == null || name.isBlank()) {
                        throw new BusinessException("第 " + (created.size() + 1) + " 条姓名非法");
                    }
                    Student s = new Student(0, name, 20);
                    repository.save(s);
                    created.add(s);
                }
                return Result.ok(created);
            } catch (Exception e) {
                // 回滚：恢复到方法执行前的状态
                repository.clear();
                repository.table.addAll(snapshot);
                repository.seq = snapshot.size();
                System.out.println("    [事务] 捕获 " + e.getClass().getSimpleName()
                        + "，执行 rollback");
                return Result.fail(400, e.getMessage());
            }
        }

        Result<Student> findById(int id) {
            Student s = repository.findById(id);
            return s == null ? Result.fail(404, "学生不存在，id=" + id) : Result.ok(s);
        }

        Result<PageResult<Student>> page(int pageNum, int pageSize) {
            if (pageNum < 1 || pageSize < 1) {
                return Result.fail(400, "pageNum 和 pageSize 必须大于 0");
            }
            int total = repository.count();
            int offset = (pageNum - 1) * pageSize;
            List<Student> records = repository.findPage(offset, pageSize);
            return Result.ok(new PageResult<>(records, total, pageNum, pageSize));
        }
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 用 Spring Boot + MyBatis 实现本章案例：`student` 表、`StudentMapper`、`StudentService`、`StudentController`，跑通增删改查。
 * 2. 给批量导入方法加 `@Transactional(rollbackFor = Exception.class)`，构造一个中途失败的场景验证整批回滚。
 * 3. 解释事务失效的三种原因，各给出一个修复方案。
 * ============================================================
 */
