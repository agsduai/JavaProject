/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：24-05
 * 课名：IOC容器与依赖注入
 * ------------------------------------------------------------
 * 知识点：
 *   - IOC（Inversion of Control，控制反转）：对象的创建与依赖装配交给容器，而不是自己 `new`。
 *   - DI（Dependency Injection，依赖注入）是 IOC 的实现方式，三种写法：构造器注入、`@Autowired` 字段注入、setter 注入。
 *   - **构造器注入是首选**：依赖不可变（`final`）、便于测试、循环依赖会直接暴露。
 *   - 常用注解：`@Component`（通用）、`@Controller`/`@RestController`（Web 层）、`@Service`（业务层）、`@Repository`（持久层）、`@Configuration` + `@Bean`（第三方类装配）。
 *   - Bean 默认**单例**（`singleton`），容器启动时创建；`@Scope("prototype")` 改成每次获取新建。
 *   - `@Autowired` 默认按类型注入，同类型多个时配合 `@Qualifier` 按名字选。
 *   - 易错：字段注入在单元测试里必须启动容器，构造器注入可以直接 `new` 出来测；这就是官方推荐构造器注入的原因。
 * ============================================================
 */

package com.heima.ch24;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;

// ==================== 示例代码 ====================

public class IocContainer {
    public static void main(String[] args) throws Exception {
        MiniContext context = new MiniContext();
        context.register(StudentRepository.class);
        context.register(StudentService.class);
        context.register(StudentController.class);

        System.out.println("=== 容器启动，创建单例 Bean ===");
        context.refresh();

        System.out.println();
        System.out.println("=== 从容器获取 Controller 并调用 ===");
        StudentController controller = context.getBean(StudentController.class);
        controller.query(1);

        System.out.println();
        System.out.println("=== 单例验证 ===");
        StudentService a = context.getBean(StudentService.class);
        StudentService b = context.getBean(StudentService.class);
        System.out.println("两次获取是同一个对象吗：" + (a == b));

        System.out.println();
        System.out.println("=== 构造器注入的好处 ===");
        System.out.println("  - 依赖可以声明为 final，创建后不可变");
        System.out.println("  - 单元测试可直接 new StudentService(mockRepo)，不用起容器");
        System.out.println("  - 循环依赖在启动时就报错，而不是运行到一半才出问题");
    }

    // ---------- 迷你 IOC 容器 ----------
    static class MiniContext {
        private final Map<Class<?>, Class<?>> definitions = new LinkedHashMap<>();
        private final Map<Class<?>, Object> singletons = new LinkedHashMap<>();

        void register(Class<?> type) {
            definitions.put(type, type);
        }

        void refresh() throws Exception {
            for (Class<?> type : definitions.keySet()) {
                getOrCreate(type);
            }
        }

        @SuppressWarnings("unchecked")
        <T> T getBean(Class<T> type) {
            return (T) singletons.get(type);
        }

        private Object getOrCreate(Class<?> type) throws Exception {
            if (singletons.containsKey(type)) {
                return singletons.get(type);
            }
            System.out.println("  创建 Bean：" + type.getSimpleName());
            Object instance = instantiate(type);
            singletons.put(type, instance);
            return instance;
        }

        // 优先用"参数最多的构造器"注入，这是 Spring 4.3+ 的单构造器自动注入规则
        private Object instantiate(Class<?> type) throws Exception {
            Constructor<?>[] ctors = type.getDeclaredConstructors();
            Constructor<?> target = ctors[0];
            for (Constructor<?> c : ctors) {
                if (c.getParameterCount() > target.getParameterCount()) {
                    target = c;
                }
            }
            if (target.getParameterCount() == 0) {
                Object o = target.newInstance();
                injectFields(o);
                return o;
            }
            Class<?>[] paramTypes = target.getParameterTypes();
            Object[] params = new Object[paramTypes.length];
            for (int i = 0; i < paramTypes.length; i++) {
                params[i] = getOrCreate(paramTypes[i]);
            }
            return target.newInstance(params);
        }

        private void injectFields(Object bean) throws Exception {
            for (Field f : bean.getClass().getDeclaredFields()) {
                if (f.isAnnotationPresent(Autowired.class)) {
                    f.setAccessible(true);
                    f.set(bean, getOrCreate(f.getType()));
                }
            }
        }
    }

    // ---------- 模拟 Spring 注解 ----------
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @interface Component {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    @interface Autowired {
    }

    // ---------- 三层组件 ----------
    @Component
    static class StudentRepository {
        String findName(int id) {
            System.out.println("    [Repository] 查询数据库 id=" + id);
            return "张三";
        }
    }

    @Component
    static class StudentService {
        private final StudentRepository repository;   // 构造器注入 + final

        StudentService(StudentRepository repository) {
            this.repository = repository;
        }

        String getName(int id) {
            System.out.println("    [Service] 业务处理 id=" + id);
            return repository.findName(id);
        }
    }

    @Component
    static class StudentController {
        @Autowired
        private StudentService service;

        void query(int id) {
            System.out.println("    [Controller] 收到请求 id=" + id);
            String name = service.getName(id);
            System.out.println("    [Controller] 返回：" + name);
        }
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 把 `StudentController` 的字段注入改成构造器注入，说明改动后测试代码有什么变化。
 * 2. 写一个 `@Configuration` + `@Bean` 的配置类，装配一个第三方类（比如自己写的 `DataSource`）。
 * 3. 解释 Bean 默认单例的含义，说明单例 Bean 里放可变成员变量会带来什么问题。
 * ============================================================
 */
