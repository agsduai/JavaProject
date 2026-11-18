/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：24-06
 * 课名：RESTful接口开发
 * ------------------------------------------------------------
 * 知识点：
 *   - `@RestController` = `@Controller` + `@ResponseBody`，方法返回值直接序列化成 JSON。
 *   - 请求映射：`@GetMapping`、`@PostMapping`、`@PutMapping`、`@DeleteMapping`，对应 REST 的查、增、改、删。
 *   - 取参三种方式：`@PathVariable`（路径变量 `/students/{id}`）、`@RequestParam`（查询参数 `?page=1`）、`@RequestBody`（JSON 请求体）。
 *   - REST 风格 URL 用**名词复数**表示资源：`/students`、`/students/1`，不要写成 `/getStudentById`。
 *   - 状态码语义：200 成功、201 创建成功、204 删除成功、400 参数错误、404 不存在、500 服务端异常。
 *   - `@RequestMapping` 可以标在类上做统一前缀。
 *   - 易错：`@RequestParam` 默认必填，可选参数要写 `required = false` 或给 `defaultValue`。
 * ============================================================
 */

package com.heima.ch24;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class RestApiDemo {
    public static void main(String[] args) throws Exception {
        // 模拟 Spring MVC 启动时扫描 @RequestMapping 建立路由表
        Map<String, Method> routes = new LinkedHashMap<>();
        Class<?> controller = StudentController.class;

        String basePath = "";
        if (controller.isAnnotationPresent(RequestMapping.class)) {
            basePath = controller.getAnnotation(RequestMapping.class).value();
        }
        for (Method m : controller.getDeclaredMethods()) {
            String path = null;
            String httpMethod = null;
            if (m.isAnnotationPresent(GetMapping.class)) {
                path = m.getAnnotation(GetMapping.class).value();
                httpMethod = "GET";
            } else if (m.isAnnotationPresent(PostMapping.class)) {
                path = m.getAnnotation(PostMapping.class).value();
                httpMethod = "POST";
            } else if (m.isAnnotationPresent(PutMapping.class)) {
                path = m.getAnnotation(PutMapping.class).value();
                httpMethod = "PUT";
            } else if (m.isAnnotationPresent(DeleteMapping.class)) {
                path = m.getAnnotation(DeleteMapping.class).value();
                httpMethod = "DELETE";
            }
            if (path == null) {
                continue;
            }
            routes.put(httpMethod + " " + basePath + path, m);
        }

        System.out.println("=== 路由表 ===");
        routes.keySet().forEach(k -> System.out.println("  " + k));
        System.out.println();

        // 模拟一次请求分发
        StudentController instance = new StudentController();
        System.out.println("=== GET /api/students/1 ===");
        Method m = routes.get("GET /api/students/{id}");
        Object result = m.invoke(instance, 1);
        System.out.println("响应体：" + result);
        System.out.println();

        System.out.println("=== GET /api/students?page=1&size=2 ===");
        m = routes.get("GET /api/students");
        System.out.println("响应体：" + m.invoke(instance, 1, 2));
        System.out.println();

        System.out.println("=== POST /api/students ===");
        m = routes.get("POST /api/students");
        System.out.println("响应体：" + m.invoke(instance, new Student(0, "赵六", 22)));
        System.out.println();

        System.out.println("=== 状态码语义 ===");
        Map<Integer, String> codes = new LinkedHashMap<>();
        codes.put(200, "查询/更新成功");
        codes.put(201, "创建成功");
        codes.put(204, "删除成功，无响应体");
        codes.put(400, "请求参数不合法");
        codes.put(404, "资源不存在");
        codes.put(500, "服务端异常");
        codes.forEach((k, v) -> System.out.printf("  %d -> %s%n", k, v));

        System.out.println();
        System.out.println("易错：@RequestParam 默认必填，可选参数要写 required = false 或 defaultValue。");
    }

    // ---------- 模拟注解 ----------
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.METHOD})
    @interface RequestMapping {
        String value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface GetMapping {
        String value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface PostMapping {
        String value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface PutMapping {
        String value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface DeleteMapping {
        String value();
    }

    // ---------- Controller ----------
    @RequestMapping("/api/students")
    static class StudentController {
        private final List<Student> db = new ArrayList<>(List.of(
                new Student(1, "张三", 20),
                new Student(2, "李四", 21),
                new Student(3, "王五", 19)));

        @GetMapping("/{id}")
        Student getById(int id) {
            return db.stream().filter(s -> s.id == id).findFirst().orElse(null);
        }

        @GetMapping("")
        List<Student> page(int pageNum, int size) {
            int offset = (pageNum - 1) * size;
            return db.stream().skip(offset).limit(size).toList();
        }

        @PostMapping("")
        Student create(Student s) {
            s.id = db.size() + 1;
            db.add(s);
            return s;
        }

        @PutMapping("/{id}")
        Student update(Student s) {
            for (int i = 0; i < db.size(); i++) {
                if (db.get(i).id == s.id) {
                    db.set(i, s);
                    return s;
                }
            }
            return null;
        }

        @DeleteMapping("/{id}")
        boolean delete(int id) {
            return db.removeIf(s -> s.id == id);
        }
    }

    // 实体用普通类而不是 record：主键需要在新增时由服务端赋值
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
 * 1. 写一组 `CourseController`，实现 `/api/courses` 的增删改查，返回 `ResponseEntity` 并设置正确的状态码。
 * 2. 用 `@PathVariable` 和 `@RequestParam` 各实现一个查询接口，说明两者适用场景的区别。
 * 3. 说明为什么 REST 风格要求 URL 用名词，`/getStudentById?id=1` 有什么问题。
 * ============================================================
 */
