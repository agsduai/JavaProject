# 第24章 Spring Boot入门

第23章的 MyBatis 解决了"持久层怎么写"，但一个完整的后端服务还要处理 Web 请求、参数校验、统一响应、异常兜底、配置分环境、对象装配与生命周期。Spring Boot 的价值就是把这一整套东西用"约定 + 自动配置"打包好，让 `main` 方法跑起来就是一个能对外提供接口的服务。本章先讲它解决了什么痛点，再依次掌握起步依赖、自动配置、配置文件、IOC/DI、REST 接口、参数校验、统一异常处理，最后整合 MyBatis 做一个完整的小服务。示例代码全部用纯 JDK 手写一个"迷你 Spring Boot"，把容器、注解扫描、路由分发、AOP 切面这些机制逐层实现，读完再看真实的 `@RestController`、`@Service` 会非常清晰。

### 24-01 为什么需要Spring Boot

**知识点**
- 传统 Spring 项目的三大痛点：配置量大（XML 或 Java Config 一大片）、依赖版本要自己对齐、部署要外部 Tomcat。
- Spring Boot 的三个核心能力：**起步依赖**（starter 一次性引入一组配套 jar）、**自动配置**（按 classpath 上的类自动装配 Bean）、**内嵌容器**（`java -jar` 直接起服务）。
- "约定优于配置"：默认端口 8080、默认扫描启动类所在包及其子包、默认配置文件 `application.yml`。
- 它不是替代 Spring，而是 Spring 的"脚手架"；底层仍然是 IOC 容器、AOP、MVC。
- 版本策略：`2.x` 对应 JDK 8+，`3.x` 要求 JDK 17+，本项目统一用 JDK 21 + Spring Boot 3.x。
- 易错：`spring-boot-starter-parent` 管的是"依赖版本"，`spring-boot-starter-web` 管的是"功能依赖"，两者职责不同。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class WhySpringBoot {
    public static void main(String[] args) {
        System.out.println("=== 传统 Spring 项目启动要做的事 ===");
        List<String> traditional = List.of(
                "web.xml 配置 DispatcherServlet",
                "spring-mvc.xml 配置视图解析器、注解驱动",
                "applicationContext.xml 配置数据源、事务管理器",
                "手工对齐 spring / mybatis / mysql 驱动的版本",
                "下载并配置外部 Tomcat，部署 war 包");
        traditional.forEach(s -> System.out.println("  - " + s));

        System.out.println();
        System.out.println("=== Spring Boot 项目启动要做的事 ===");
        List<String> boot = List.of(
                "pom.xml 引入 spring-boot-starter-web",
                "写一个带 @SpringBootApplication 的启动类",
                "运行 main 方法");
        boot.forEach(s -> System.out.println("  - " + s));
        System.out.println("  共 " + boot.size() + " 步，对比 " + traditional.size() + " 步");

        System.out.println();
        System.out.println("=== starter 帮你引入了什么 ===");
        Map<String, List<String>> starters = new LinkedHashMap<>();
        starters.put("spring-boot-starter-web", List.of(
                "spring-webmvc", "spring-web", "tomcat-embed-core",
                "jackson-databind", "spring-boot-starter-json"));
        starters.put("spring-boot-starter-jdbc", List.of(
                "spring-jdbc", "HikariCP", "spring-tx"));
        starters.put("mybatis-spring-boot-starter", List.of(
                "mybatis", "mybatis-spring", "spring-boot-starter-jdbc"));

        starters.forEach((k, v) -> {
            System.out.println("  " + k);
            v.forEach(dep -> System.out.println("      +- " + dep));
        });

        System.out.println();
        System.out.println("=== 自动配置做了什么 ===");
        List<String> autoConfig = new ArrayList<>();
        autoConfig.add("发现 classpath 有 tomcat-embed -> 启动内嵌 Tomcat，默认端口 8080");
        autoConfig.add("发现 classpath 有 DataSource 驱动 -> 创建 DataSource Bean");
        autoConfig.add("发现 classpath 有 mybatis -> 创建 SqlSessionFactory、扫描 Mapper");
        autoConfig.add("发现 classpath 有 jackson -> 注册 JSON 消息转换器");
        autoConfig.forEach(s -> System.out.println("  - " + s));

        System.out.println();
        System.out.println("结论：Boot 没有魔法，它只是把\"看见什么类就装配什么 Bean\"写成了规则。");
    }
}
```

**课后练习**
1. 列出一个传统 Spring MVC 项目必须手写的 5 个配置文件，说明 Spring Boot 分别用什么机制替代了它们。
2. 解释 starter 和 parent 的区别，为什么说 starter 是"依赖聚合"而不是"代码"。
3. Spring Boot 3.x 为什么要求 JDK 17+？从 Jakarta EE 命名空间迁移的角度回答。

### 24-02 第一个Spring Boot工程

**知识点**
- 工程结构：启动类放在最外层包（如 `com.heima`），其它包（`controller`、`service`、`mapper`）都放在它下面，才能被默认扫描到。
- `@SpringBootApplication` 是三个注解的组合：`@SpringBootConfiguration` + `@EnableAutoConfiguration` + `@ComponentScan`。
- 启动类必须放在能被扫描到的位置；放到 `com.heima.web` 下会导致 `com.heima.service` 不被扫描。
- `SpringApplication.run(App.class, args)` 返回值是 `ConfigurableApplicationContext`，即 IOC 容器本体。
- `application.yml` 里可改端口 `server.port`、应用名 `spring.application.name`、日志级别。
- 易错：端口被占用时启动失败，报 `Port 8080 was already in use`；改端口或杀掉占用进程。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class FirstBootApp {
    public static void main(String[] args) {
        System.out.println("=== 启动类 ===");
        System.out.println("""
                @SpringBootApplication
                public class HeimaApplication {
                    public static void main(String[] args) {
                        SpringApplication.run(HeimaApplication.class, args);
                    }
                }
                """);

        // 模拟 @ComponentScan：从启动类所在包开始，扫描所有子包
        String basePackage = "com.heima";
        List<String> candidates = List.of(
                "com.heima.controller.StudentController",
                "com.heima.service.StudentService",
                "com.heima.service.impl.StudentServiceImpl",
                "com.heima.mapper.StudentMapper",
                "com.other.util.StringUtil");   // 不在扫描范围内

        System.out.println("=== 组件扫描（basePackage = " + basePackage + "）===");
        List<String> scanned = new ArrayList<>();
        for (String c : candidates) {
            boolean hit = c.startsWith(basePackage + ".");
            System.out.println((hit ? "  [注册] " : "  [跳过] ") + c);
            if (hit) {
                scanned.add(c);
            }
        }
        System.out.println("共注册 " + scanned.size() + " 个候选组件");

        System.out.println();
        System.out.println("=== 启动过程 ===");
        List<String> steps = List.of(
                "创建 SpringApplication 实例",
                "推断应用类型（SERVLET / REACTIVE / NONE）",
                "加载 ApplicationContextInitializer 与 Listener",
                "准备 Environment（读 application.yml、命令行参数、环境变量）",
                "创建并刷新 IOC 容器：扫描组件、执行自动配置、实例化单例 Bean",
                "启动内嵌 Web 服务器（Tomcat）",
                "发布 ApplicationReadyEvent，启动完成");
        for (int i = 0; i < steps.size(); i++) {
            System.out.println("  " + (i + 1) + ". " + steps.get(i));
        }

        System.out.println();
        System.out.println("=== application.yml ===");
        Map<String, Object> yml = new LinkedHashMap<>();
        yml.put("server.port", 8080);
        yml.put("spring.application.name", "heima-demo");
        yml.put("logging.level.com.heima", "debug");
        yml.forEach((k, v) -> System.out.println("  " + k + ": " + v));

        System.out.println();
        System.out.println("启动成功后访问：http://localhost:8080/");
        System.out.println("若报 Port 8080 was already in use，改 server.port 或结束占用进程。");
    }
}
```

**课后练习**
1. 用 Spring Initializr 或 IDEA 新建一个 Boot 工程，跑通 `HelloController`，访问 `http://localhost:8080/hello`。
2. 把启动类移到 `com.heima.web` 包下，观察 `com.heima.service` 里的 `@Service` 是否还被注入，解释原因。
3. 把端口改成 9090，验证生效；再说明 `application.yml` 和 `application.properties` 的优先级关系。

### 24-03 自动配置原理

**知识点**
- 自动配置的入口是 `@EnableAutoConfiguration`，它通过 `@Import(AutoConfigurationImportSelector.class)` 加载配置类清单。
- Spring Boot 2.7+ 的清单文件是 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`（3.x 沿用）。
- 每个自动配置类上都有一串**条件注解**，只有条件满足才生效：
  - `@ConditionalOnClass`：classpath 上有指定类才生效。
  - `@ConditionalOnMissingBean`：容器里没有该 Bean 才生效（所以你自己定义的会覆盖默认的）。
  - `@ConditionalOnProperty`：配置项为指定值才生效。
- `@EnableConfigurationProperties` 把 `application.yml` 里的配置绑定到 `@ConfigurationProperties` 类。
- 排查自动配置：启动加 `--debug`，控制台会打印 `CONDITIONS EVALUATION REPORT`，明确哪些生效、哪些没生效及原因。
- 易错：以为"引了 starter 就一定生效"，实际上缺数据源、缺配置项时会因为条件不满足而跳过。

**示例代码**
```java
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AutoConfigPrinciple {
    public static void main(String[] args) {
        // 模拟 classpath 上存在的类
        List<String> classpath = List.of(
                "jakarta.servlet.Servlet",
                "com.zaxxer.hikari.HikariDataSource",
                "com.mysql.cj.jdbc.Driver",
                "org.apache.ibatis.session.SqlSessionFactory");
        // 模拟用户自定义的 Bean
        List<String> userBeans = List.of("myDataSource");
        // 模拟配置项
        Map<String, String> props = new LinkedHashMap<>();
        props.put("spring.datasource.url", "jdbc:mysql://localhost:3306/heima_db");
        props.put("mybatis.mapper-locations", "classpath:mapper/*.xml");

        List<AutoConfig> configs = new ArrayList<>();
        configs.add(new AutoConfig("ServletWebServerFactoryAutoConfiguration",
                "jakarta.servlet.Servlet", null, null, "启动内嵌 Tomcat"));
        configs.add(new AutoConfig("DataSourceAutoConfiguration",
                "com.zaxxer.hikari.HikariDataSource", "dataSource",
                "spring.datasource.url", "创建 HikariCP 数据源"));
        configs.add(new AutoConfig("MybatisAutoConfiguration",
                "org.apache.ibatis.session.SqlSessionFactory", "sqlSessionFactory",
                "mybatis.mapper-locations", "创建 SqlSessionFactory 并扫描 Mapper"));

        System.out.println("=== 条件评估报告（模拟 --debug 输出）===");
        for (AutoConfig c : configs) {
            List<String> reasons = new ArrayList<>();
            boolean onClass = classpath.contains(c.onClass);
            if (!onClass) {
                reasons.add("缺少类 " + c.onClass);
            }
            if (c.missingBean != null && userBeans.contains(c.missingBean)) {
                reasons.add("容器已有 Bean " + c.missingBean + "，跳过默认配置");
            }
            if (c.onProperty != null && !props.containsKey(c.onProperty)) {
                reasons.add("缺少配置项 " + c.onProperty);
            }
            if (reasons.isEmpty()) {
                System.out.println("  [生效] " + c.name);
                System.out.println("         -> " + c.action);
            } else {
                System.out.println("  [跳过] " + c.name);
                reasons.forEach(r -> System.out.println("         原因：" + r));
            }
        }

        System.out.println();
        System.out.println("=== 三种条件注解的语义 ===");
        System.out.println("  @ConditionalOnClass      classpath 有某个类才生效（starter 在不在）");
        System.out.println("  @ConditionalOnMissingBean 容器里没有该 Bean 才生效（让你能覆盖默认）");
        System.out.println("  @ConditionalOnProperty    配置项为指定值才生效（功能开关）");

        System.out.println();
        System.out.println("=== 自动配置类清单文件（3.x）===");
        System.out.println("  META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports");
        System.out.println("  内容形如：");
        System.out.println("    org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration");
        System.out.println("    org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration");
    }

    record AutoConfig(String name, String onClass, String missingBean,
                      String onProperty, String action) {
    }
}
```

**课后练习**
1. 启动时加 `--debug`，从 `CONDITIONS EVALUATION REPORT` 里找出 `DataSourceAutoConfiguration` 是否生效及原因。
2. 自己写一个 `@ConditionalOnMissingBean` 的配置类，验证自定义 Bean 能覆盖 Boot 的默认 Bean。
3. 说明 `@ConditionalOnProperty` 的典型使用场景，并给出一个配置示例。

### 24-04 配置文件与Profile

**知识点**
- 两种格式：`application.properties`（`key=value`）和 `application.yml`（缩进层级），YAML 更易读、支持列表和嵌套对象。
- 配置优先级（高 → 低）：命令行参数 → 环境变量 → `application-{profile}.yml` → `application.yml`。
- `spring.profiles.active=dev` 激活环境；`application-dev.yml`、`application-prod.yml` 分别存各环境配置。
- 一个 YAML 文件里可用 `---` 分隔多个文档，配合 `spring.config.activate.on-profile` 实现多环境。
- `@Value("${key}")` 注入单个配置项；`@ConfigurationProperties(prefix = "xxx")` 批量绑定到一个对象。
- 敏感信息（密码、密钥）不要硬编码，用环境变量或配置中心。
- 易错：YAML 用 Tab 缩进会解析失败，必须用空格；键名大小写敏感。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ConfigAndProfile {
    public static void main(String[] args) {
        // 模拟三个来源的配置，按优先级从低到高
        Map<String, String> base = new LinkedHashMap<>();
        base.put("server.port", "8080");
        base.put("spring.datasource.url", "jdbc:mysql://localhost:3306/heima_db");
        base.put("logging.level.root", "info");

        Map<String, String> dev = new LinkedHashMap<>();
        dev.put("server.port", "9090");
        dev.put("logging.level.root", "debug");
        dev.put("spring.datasource.url", "jdbc:mysql://localhost:3306/heima_db_dev");

        Map<String, String> prod = new LinkedHashMap<>();
        prod.put("server.port", "80");
        prod.put("logging.level.root", "warn");
        prod.put("spring.datasource.url", "jdbc:mysql://10.0.0.8:3306/heima_db");

        Map<String, String> cli = new LinkedHashMap<>();
        cli.put("server.port", "8888");   // 命令行参数优先级最高

        System.out.println("=== 不激活 profile ===");
        print(merge(base));

        System.out.println();
        System.out.println("=== 激活 dev ===");
        print(merge(base, dev));

        System.out.println();
        System.out.println("=== 激活 prod + 命令行覆盖端口 ===");
        print(merge(base, prod, cli));

        System.out.println();
        System.out.println("=== YAML 多文档写法 ===");
        System.out.println("""
                spring:
                  application:
                    name: heima-demo
                ---
                spring:
                  config:
                    activate:
                      on-profile: dev
                server:
                  port: 9090
                ---
                spring:
                  config:
                    activate:
                      on-profile: prod
                server:
                  port: 80
                """);

        System.out.println("=== 优先级（高 -> 低）===");
        List<String> order = new ArrayList<>(List.of(
                "命令行参数 --server.port=8888",
                "Java 系统属性 -Dserver.port=8888",
                "操作系统环境变量 SERVER_PORT",
                "application-{profile}.yml",
                "application.yml"));
        for (int i = 0; i < order.size(); i++) {
            System.out.println("  " + (i + 1) + ". " + order.get(i));
        }

        System.out.println();
        System.out.println("注意：YAML 必须用空格缩进，用 Tab 会解析失败。");
    }

    @SafeVarargs
    private static Map<String, String> merge(Map<String, String>... layers) {
        Map<String, String> result = new LinkedHashMap<>();
        for (Map<String, String> layer : layers) {
            result.putAll(layer);   // 后面的覆盖前面的
        }
        return result;
    }

    private static void print(Map<String, String> config) {
        config.forEach((k, v) -> System.out.printf("  %-28s = %s%n", k, v));
    }
}
```

**课后练习**
1. 建 `application.yml` + `application-dev.yml` + `application-prod.yml`，用 `spring.profiles.active` 切换，验证端口与日志级别变化。
2. 用 `@ConfigurationProperties(prefix = "app")` 把一组配置绑定到 `AppProperties` 类，并在 Controller 里注入使用。
3. 说明为什么数据库密码不应该写在 `application.yml` 里，给出两种更安全的做法。

### 24-05 IOC容器与依赖注入

**知识点**
- IOC（Inversion of Control，控制反转）：对象的创建与依赖装配交给容器，而不是自己 `new`。
- DI（Dependency Injection，依赖注入）是 IOC 的实现方式，三种写法：构造器注入、`@Autowired` 字段注入、setter 注入。
- **构造器注入是首选**：依赖不可变（`final`）、便于测试、循环依赖会直接暴露。
- 常用注解：`@Component`（通用）、`@Controller`/`@RestController`（Web 层）、`@Service`（业务层）、`@Repository`（持久层）、`@Configuration` + `@Bean`（第三方类装配）。
- Bean 默认**单例**（`singleton`），容器启动时创建；`@Scope("prototype")` 改成每次获取新建。
- `@Autowired` 默认按类型注入，同类型多个时配合 `@Qualifier` 按名字选。
- 易错：字段注入在单元测试里必须启动容器，构造器注入可以直接 `new` 出来测；这就是官方推荐构造器注入的原因。

**示例代码**
```java
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;

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
```

**课后练习**
1. 把 `StudentController` 的字段注入改成构造器注入，说明改动后测试代码有什么变化。
2. 写一个 `@Configuration` + `@Bean` 的配置类，装配一个第三方类（比如自己写的 `DataSource`）。
3. 解释 Bean 默认单例的含义，说明单例 Bean 里放可变成员变量会带来什么问题。

### 24-06 RESTful接口开发

**知识点**
- `@RestController` = `@Controller` + `@ResponseBody`，方法返回值直接序列化成 JSON。
- 请求映射：`@GetMapping`、`@PostMapping`、`@PutMapping`、`@DeleteMapping`，对应 REST 的查、增、改、删。
- 取参三种方式：`@PathVariable`（路径变量 `/students/{id}`）、`@RequestParam`（查询参数 `?page=1`）、`@RequestBody`（JSON 请求体）。
- REST 风格 URL 用**名词复数**表示资源：`/students`、`/students/1`，不要写成 `/getStudentById`。
- 状态码语义：200 成功、201 创建成功、204 删除成功、400 参数错误、404 不存在、500 服务端异常。
- `@RequestMapping` 可以标在类上做统一前缀。
- 易错：`@RequestParam` 默认必填，可选参数要写 `required = false` 或给 `defaultValue`。

**示例代码**
```java
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
```

**课后练习**
1. 写一组 `CourseController`，实现 `/api/courses` 的增删改查，返回 `ResponseEntity` 并设置正确的状态码。
2. 用 `@PathVariable` 和 `@RequestParam` 各实现一个查询接口，说明两者适用场景的区别。
3. 说明为什么 REST 风格要求 URL 用名词，`/getStudentById?id=1` 有什么问题。

### 24-07 参数校验与统一响应

**知识点**
- 统一响应体 `Result<T>` 固定三个字段：`code`（业务码）、`message`（提示）、`data`（数据），前端只按一种格式解析。
- 业务码与 HTTP 状态码不是一回事：HTTP 200 表示"请求被正确处理"，`code=500` 表示"业务处理失败"。
- 参数校验用 Bean Validation：`@NotNull`、`@NotBlank`、`@Min`、`@Max`、`@Size`、`@Email`，配 `@Valid` 或 `@Validated` 触发。
- 校验失败抛 `MethodArgumentNotValidException`，由 `@RestControllerAdvice` 统一捕获后转成 `Result`。
- 分组校验（`@Validated(AddGroup.class)`）让同一个 DTO 在新增和修改时有不同规则。
- 易错：只在 Controller 上加 `@Valid` 而 DTO 字段没有约束注解，等于没校验；嵌套对象校验要加 `@Valid`。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ValidationDemo {
    public static void main(String[] args) {
        StudentService service = new StudentService();

        System.out.println("=== 合法请求 ===");
        print(service.create(new StudentDTO("张三", 20, "zhangsan@heima.com")));
        System.out.println();

        System.out.println("=== 姓名空白 ===");
        print(service.create(new StudentDTO("   ", 20, "zhangsan@heima.com")));
        System.out.println();

        System.out.println("=== 年龄越界 ===");
        print(service.create(new StudentDTO("李四", 200, "lisi@heima.com")));
        System.out.println();

        System.out.println("=== 邮箱格式错误 + 年龄越界（多个错误一起返回）===");
        print(service.create(new StudentDTO("王五", -1, "not-an-email")));
        System.out.println();

        System.out.println("=== 统一响应体结构 ===");
        System.out.println("""
                {
                  "code": 200,
                  "message": "success",
                  "data": { "id": 1, "name": "张三" }
                }
                """);
        System.out.println("业务码约定：200 成功、400 参数错误、401 未登录、500 业务异常");
    }

    private static void print(Result<?> r) {
        System.out.println("  code=" + r.code + ", message=" + r.message + ", data=" + r.data);
    }

    // ---------- 统一响应体 ----------
    record Result<T>(int code, String message, T data) {
        static <T> Result<T> ok(T data) {
            return new Result<>(200, "success", data);
        }

        static <T> Result<T> fail(int code, String message) {
            return new Result<>(code, message, null);
        }
    }

    // ---------- DTO ----------
    record StudentDTO(String name, int age, String email) {
    }

    // ---------- Service（含校验逻辑，模拟 @Valid 触发）----------
    static class StudentService {
        private int seq = 0;

        Result<StudentDTO> create(StudentDTO dto) {
            List<String> errors = validate(dto);
            if (!errors.isEmpty()) {
                return Result.fail(400, String.join("; ", errors));
            }
            seq++;
            return Result.ok(new StudentDTO(dto.name().trim(), dto.age(), dto.email()));
        }

        // 对应 Bean Validation 的约束注解
        private List<String> validate(StudentDTO dto) {
            List<String> errors = new ArrayList<>();
            if (dto.name() == null || dto.name().isBlank()) {
                errors.add("姓名不能为空（@NotBlank）");
            } else if (dto.name().length() > 10) {
                errors.add("姓名长度不能超过 10（@Size(max = 10)）");
            }
            if (dto.age() < 1 || dto.age() > 150) {
                errors.add("年龄必须在 1~150 之间（@Min/@Max）");
            }
            if (dto.email() == null || !dto.email().matches("^[\\w.-]+@[\\w.-]+\\.[A-Za-z]{2,}$")) {
                errors.add("邮箱格式不正确（@Email）");
            }
            return errors;
        }
    }

    // ---------- 常见约束注解对照 ----------
    static {
        Map<String, String> table = new LinkedHashMap<>();
        table.put("@NotNull", "不能为 null");
        table.put("@NotBlank", "字符串不能为 null、不能为空、不能全空白");
        table.put("@NotEmpty", "集合/字符串不能为空");
        table.put("@Min/@Max", "数值范围");
        table.put("@Size(min,max)", "长度范围");
        table.put("@Email", "邮箱格式");
        table.put("@Pattern", "正则匹配");
        table.forEach((k, v) -> System.out.println("  " + k + " -> " + v));
    }
}
```

**课后练习**
1. 给 `StudentDTO` 加上完整的约束注解，并在 Controller 上加 `@Valid`，用 Postman 验证错误提示。
2. 用 `@RestControllerAdvice` + `@ExceptionHandler(MethodArgumentNotValidException.class)` 把校验错误转成 `Result`。
3. 说明 `@Valid` 和 `@Validated` 的区别，分组校验应该用哪一个。

### 24-08 全局异常处理与拦截器

**知识点**
- `@RestControllerAdvice` + `@ExceptionHandler` 集中处理异常，避免每个 Controller 都写 try-catch。
- 处理顺序：**更具体的异常类型优先**，所以 `BusinessException` 的处理器会先于 `Exception` 的处理器命中。
- 自定义业务异常 `BusinessException` 携带 `code`，配合统一响应体返回给前端。
- `HandlerInterceptor` 三个方法：`preHandle`（请求前，返回 `false` 中断）、`postHandle`（Controller 之后、视图渲染前）、`afterCompletion`（请求完全结束后，必执行）。
- 拦截器常用于：登录校验、权限判断、请求耗时统计、链路 traceId 注入。
- 拦截器 vs 过滤器：过滤器属于 Servlet 规范、在 DispatcherServlet 之前；拦截器属于 Spring MVC、能拿到 Handler 信息。
- 易错：`afterCompletion` 在 `preHandle` 返回 `false` 时**不会执行**（只有 `preHandle` 返回 `true` 才会进后两个）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.List;

public class ExceptionAndInterceptor {
    public static void main(String[] args) {
        StudentController controller = new StudentController();

        System.out.println("=== 正常请求 ===");
        System.out.println(controller.findById(1));
        System.out.println();

        System.out.println("=== 资源不存在（业务异常）===");
        System.out.println(controller.findById(99));
        System.out.println();

        System.out.println("=== 非法参数（参数异常）===");
        System.out.println(controller.findById(-1));
        System.out.println();

        System.out.println("=== 未预期异常（兜底）===");
        System.out.println(controller.broken());
        System.out.println();

        System.out.println("=== 拦截器执行顺序 ===");
        AuthInterceptor interceptor = new AuthInterceptor();
        String token = "valid-token";
        boolean pass = interceptor.preHandle("/api/students", token);
        if (pass) {
            System.out.println("    [Controller] 处理业务");
            interceptor.postHandle("/api/students");
        }
        interceptor.afterCompletion("/api/students", pass);
        System.out.println();

        System.out.println("=== token 缺失时被拦截 ===");
        pass = interceptor.preHandle("/api/students", null);
        interceptor.afterCompletion("/api/students", pass);
        System.out.println("  -> preHandle 返回 false，Controller 不执行，postHandle 也不执行");
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

    // ---------- 自定义异常 ----------
    static class BusinessException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        final int code;

        BusinessException(int code, String message) {
            super(message);
            this.code = code;
        }
    }

    // ---------- 全局异常处理器（模拟 @RestControllerAdvice）----------
    static class GlobalExceptionHandler {
        // 更具体的异常优先命中
        Result<String> handleBusiness(BusinessException e) {
            System.out.println("    [Advice] 命中 BusinessException 处理器");
            return Result.fail(e.code, e.getMessage());
        }

        Result<String> handleIllegalArgument(IllegalArgumentException e) {
            System.out.println("    [Advice] 命中 IllegalArgumentException 处理器");
            return Result.fail(400, "参数不合法：" + e.getMessage());
        }

        Result<String> handleAll(Exception e) {
            System.out.println("    [Advice] 命中 Exception 兜底处理器");
            return Result.fail(500, "服务器开小差了，请稍后重试");
        }
    }

    // ---------- Controller ----------
    static class StudentController {
        private final GlobalExceptionHandler advice = new GlobalExceptionHandler();
        private final List<String> db = new ArrayList<>(List.of("张三", "李四"));

        Result<String> findById(int id) {
            try {
                if (id <= 0) {
                    throw new IllegalArgumentException("id 必须大于 0");
                }
                if (id > db.size()) {
                    throw new BusinessException(404, "学生不存在，id=" + id);
                }
                return Result.ok(db.get(id - 1));
            } catch (BusinessException e) {
                return advice.handleBusiness(e);
            } catch (IllegalArgumentException e) {
                return advice.handleIllegalArgument(e);
            }
        }

        Result<String> broken() {
            try {
                throw new NullPointerException("模拟空指针");
            } catch (Exception e) {
                return advice.handleAll(e);
            }
        }
    }

    // ---------- 拦截器 ----------
    static class AuthInterceptor {
        boolean preHandle(String path, String token) {
            System.out.println("    [Interceptor] preHandle  " + path + "  token=" + token);
            if (token == null || token.isBlank()) {
                System.out.println("    [Interceptor] 未携带 token，返回 401 并中断请求");
                return false;
            }
            return true;
        }

        void postHandle(String path) {
            System.out.println("    [Interceptor] postHandle  " + path + "（Controller 已执行完）");
        }

        void afterCompletion(String path, boolean pass) {
            if (pass) {
                System.out.println("    [Interceptor] afterCompletion  " + path + "（请求结束，必执行）");
            } else {
                System.out.println("    [Interceptor] afterCompletion 不执行（preHandle 返回了 false）");
            }
        }
    }
}
```

**课后练习**
1. 写一个 `GlobalExceptionHandler`，处理 `BusinessException`、`MethodArgumentNotValidException` 和 `Exception` 三类。
2. 实现一个统计接口耗时的拦截器，在 `afterCompletion` 里打印耗时。
3. 说明拦截器和过滤器的区别，登录校验更适合用哪一个，为什么。

### 24-09 综合案例：整合MyBatis的学生服务

**知识点**
- 整合三件套：`mybatis-spring-boot-starter` 依赖 + `spring.datasource.*` 配置 + `@MapperScan("com.heima.mapper")`。
- 分层职责：Controller 只做参数接收与响应封装，Service 做业务与事务，Mapper 只写 SQL。
- `@Transactional` 标在 Service 的 public 方法上，默认只对 `RuntimeException` 回滚；受检异常要写 `rollbackFor = Exception.class`。
- 事务失效的三个常见原因：方法非 public、同类内部调用（不走代理）、异常被自己 try-catch 吃掉。
- 统一响应 + 全局异常 + 事务，构成一个后端接口的最小闭环。
- 易错：`@Transactional` 加在 Controller 上不生效也不合理；事务应该包住"一组必须同时成功的写操作"。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
```

**课后练习**
1. 用 Spring Boot + MyBatis 实现本章案例：`student` 表、`StudentMapper`、`StudentService`、`StudentController`，跑通增删改查。
2. 给批量导入方法加 `@Transactional(rollbackFor = Exception.class)`，构造一个中途失败的场景验证整批回滚。
3. 解释事务失效的三种原因，各给出一个修复方案。
