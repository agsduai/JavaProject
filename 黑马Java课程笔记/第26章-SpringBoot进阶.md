# 第26章 Spring Boot进阶

第24章把服务跑起来了，但真正上线的项目还会遇到一类"横切"需求：每个接口都要打日志、算耗时、校验权限、限流；每张表都要写一遍几乎相同的增删改查；用户登录后要拿着令牌访问后续接口；前端要传图片。这些需求如果都手写，代码会散落在每个方法里。本章就解决这四件事：用 AOP 把日志/耗时/权限从业务代码里抽出来，用 MyBatis-Plus 消灭样板 CRUD，用规范的文件上传接口接收图片，用 JWT 做无状态登录鉴权，最后串成一个"登录 -> 拿 token -> 带 token 访问"的完整闭环。示例仍然全部用纯 JDK 实现：AOP 用 `java.lang.reflect.Proxy` 真跑一遍代理链，MyBatis-Plus 用反射模拟 `BaseMapper` 的通用 CRUD，JWT 用 `javax.crypto` 真算 HMAC-SHA256 签名，上传用内存字节流做完整校验，不引入任何外部依赖。

### 26-01 AOP：把横切逻辑抽出来

**知识点**
- 横切关注点：日志、耗时、事务、权限、限流、缓存这些"每个方法都要做一点"的逻辑，和业务逻辑正交，混在一起会导致大量重复代码。
- 继承/工具类解决不了：每个方法都要手动调用一次，漏写就没效果；也无法统一开关。
- AOP 的思路：不改原方法代码，在方法执行的前后"织入"额外逻辑。Java 里靠**动态代理**实现。
- 两种代理：
  - JDK 动态代理：`Proxy.newProxyInstance`，要求目标类实现接口，代理对象和目标实现同一个接口。
  - CGLIB：生成目标类的子类，不要求接口，但 `final` 类/方法无法代理。Spring Boot 2.x 起默认用 CGLIB（`proxy-target-class=true`）。
- Spring 只在**通过代理对象调用**时生效：同类内部 `this.method()` 不走代理，注解失效（这是 `@Transactional`、`@Cacheable`、`@Async` 全部失效的同一个根因）。
- 织入时机：编译期（AspectJ）、类加载期、运行期（Spring AOP 用运行期代理）。
- 易错：`private` 方法不能被代理；`final` 方法不能被 CGLIB 覆盖；`@Transactional` 加在 `private` 上静默失效。

**示例代码**
```java
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class AopIntro {
    public static void main(String[] args) {
        System.out.println("=== 不用 AOP：每个方法自己写日志与耗时 ===");
        OrderServiceRaw raw = new OrderServiceRaw();
        raw.createOrder("键盘");
        raw.cancelOrder(1001L);

        System.out.println();
        System.out.println("=== 用 JDK 动态代理：日志与耗时统一织入 ===");
        OrderService target = new OrderServiceImpl();
        OrderService proxy = (OrderService) Proxy.newProxyInstance(
                AopIntro.class.getClassLoader(),
                new Class<?>[]{OrderService.class},
                new LogTimeHandler(target));
        proxy.createOrder("鼠标");
        proxy.cancelOrder(1002L);

        System.out.println();
        System.out.println("=== 代理对象是什么 ===");
        System.out.println("  proxy.getClass() = " + proxy.getClass().getName());
        System.out.println("  是 OrderService 的实现吗：" + (proxy instanceof OrderService));
        System.out.println("  真实对象被包在 InvocationHandler 里，调用先经过 handler");
    }

    interface OrderService {
        String createOrder(String item);

        boolean cancelOrder(Long id);
    }

    /** 目标类：只写业务，不含任何日志代码 */
    static class OrderServiceImpl implements OrderService {
        @Override
        public String createOrder(String item) {
            return "订单已创建：" + item;
        }

        @Override
        public boolean cancelOrder(Long id) {
            return id != null && id > 0;
        }
    }

    /** 横切逻辑：日志 + 耗时 */
    static class LogTimeHandler implements InvocationHandler {
        private final Object target;

        LogTimeHandler(Object target) {
            this.target = target;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            long start = System.nanoTime();
            System.out.println("  [前置] 调用 " + method.getName() + "，参数 " + java.util.Arrays.toString(args));
            try {
                Object result = method.invoke(target, args);
                System.out.println("  [后置] 返回 " + result);
                return result;
            } catch (Exception e) {
                System.out.println("  [异常] " + e.getCause());
                throw e.getCause();
            } finally {
                long cost = (System.nanoTime() - start) / 1000;
                System.out.println("  [耗时] " + method.getName() + " 用了 " + cost + " 微秒");
            }
        }
    }

    /** 反面示例：业务里手动埋点，重复且容易漏 */
    static class OrderServiceRaw {
        String createOrder(String item) {
            long start = System.nanoTime();
            System.out.println("  [日志] createOrder 参数=" + item);
            String r = "订单已创建：" + item;
            System.out.println("  [耗时] " + (System.nanoTime() - start) / 1000 + " 微秒");
            return r;
        }

        boolean cancelOrder(Long id) {
            long start = System.nanoTime();
            System.out.println("  [日志] cancelOrder 参数=" + id);
            boolean r = id != null && id > 0;
            System.out.println("  [耗时] " + (System.nanoTime() - start) / 1000 + " 微秒");
            return r;
        }
    }
}
```

**课后练习**
1. 列出你写过的项目里 3 个横切关注点，说明用 AOP 前后代码量的差别。
2. JDK 动态代理和 CGLIB 的适用条件分别是什么？Spring Boot 为什么默认选 CGLIB？
3. 为什么 `@Transactional` 加在同类内部调用的方法上会失效？画出调用链解释。

### 26-02 AOP 核心概念与五种通知

**知识点**
- 术语对照（Spring AOP 与 AspectJ 注解）：
  - **切面（Aspect）**：横切逻辑的模块，`@Aspect` 标注的类。
  - **连接点（JoinPoint）**：可以被织入的点，Spring 里就是方法调用。
  - **切入点（Pointcut）**：用表达式选中哪些连接点，`@Pointcut("execution(* com.heima.service.*.*(..))")`。
  - **通知（Advice）**：织入的具体动作。
  - **目标对象（Target）/ 代理（Proxy）/ 织入（Weaving）**。
- 五种通知：`@Before`（前置）、`@AfterReturning`（正常返回后，可拿返回值）、`@AfterThrowing`（抛异常后）、`@After`（最终，类似 finally）、`@Around`（环绕，可控制是否执行目标方法、可改返回值，功能最强）。
- 切入点表达式：`execution(修饰符 返回类型 包.类.方法(参数))`，`*` 匹配任意，`..` 匹配任意参数/任意层级包。常用 `execution(* com.heima..controller.*.*(..))`。
- `@Around` 里必须调用 `proceed()` 目标方法才会执行；不调用就是"拦截"，可以用来做权限拦截。
- 多个切面时的顺序：`@Order(值)` 越小优先级越高，前置通知按序执行、后置通知逆序执行（像栈）。
- 易错：`@Around` 里吞掉异常会导致 `@AfterThrowing` 不触发；`JoinPoint` 参数必须是第一个参数；`@AfterReturning` 想拿返回值要写 `returning = "ret"` 且形参名一致。

**示例代码**
```java
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

public class AdviceTypes {
    public static void main(String[] args) {
        System.out.println("=== 五种通知的执行顺序 ===");
        Calculator target = new CalculatorImpl();
        Calculator proxy = (Calculator) Proxy.newProxyInstance(
                AdviceTypes.class.getClassLoader(),
                new Class<?>[]{Calculator.class},
                new AllAdviceHandler(target));

        System.out.println("调用 add(1, 2)：");
        System.out.println("  结果 = " + proxy.add(1, 2));

        System.out.println();
        System.out.println("调用 divide(1, 0)：");
        try {
            proxy.divide(1, 0);
        } catch (ArithmeticException e) {
            System.out.println("  调用方捕获：" + e.getMessage());
        }

        System.out.println();
        System.out.println("=== 多个切面的顺序（@Order）===");
        List<String> log = new ArrayList<>();
        log.add("Order(1) 前置");
        log.add("Order(2) 前置");
        log.add("目标方法执行");
        log.add("Order(2) 后置");
        log.add("Order(1) 后置");
        log.forEach(s -> System.out.println("  " + s));
        System.out.println("  -> 前置按 Order 升序，后置逆序，像栈一样");

        System.out.println();
        System.out.println("=== 切入点表达式 ===");
        String[] exprs = {
                "execution(* com.heima.service.*.*(..))",
                "execution(* com.heima..controller.*.*(..))",
                "execution(public String com.heima.service.UserService.findById(Long))",
                "@annotation(com.heima.annotation.Log)"};
        String[] means = {
                "service 包下所有类的所有方法",
                "controller 及其子包下所有类的所有方法",
                "精确到某个方法签名",
                "所有标了 @Log 注解的方法"};
        for (int i = 0; i < exprs.length; i++) {
            System.out.println("  " + exprs[i]);
            System.out.println("      -> " + means[i]);
        }
    }

    interface Calculator {
        int add(int a, int b);

        int divide(int a, int b);
    }

    static class CalculatorImpl implements Calculator {
        @Override
        public int add(int a, int b) {
            return a + b;
        }

        @Override
        public int divide(int a, int b) {
            return a / b;
        }
    }

    /** 用一个 handler 模拟 @Around 包住其余四种通知 */
    static class AllAdviceHandler implements InvocationHandler {
        private final Object target;

        AllAdviceHandler(Object target) {
            this.target = target;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            System.out.println("  @Around 开始（proceed 之前）");
            System.out.println("  @Before");
            try {
                Object result = method.invoke(target, args);   // 相当于 proceed()
                System.out.println("  @AfterReturning 返回值=" + result);
                return result;
            } catch (Exception e) {
                System.out.println("  @AfterThrowing " + e.getCause());
                throw e.getCause();
            } finally {
                System.out.println("  @After（无论成败都执行）");
                System.out.println("  @Around 结束（proceed 之后）");
            }
        }
    }
}
```

**课后练习**
1. 写出一个切入点表达式，要求拦截 `com.heima.controller` 包下所有 public 方法。
2. `@Around` 和 `@Before + @AfterReturning` 的能力差别在哪？什么场景只能用 `@Around`？
3. 两个切面 `@Order(1)` 和 `@Order(2)`，画出 5 种通知的完整执行顺序。

### 26-03 AOP 实战：统一日志与接口耗时

**知识点**
- 目标：所有 Controller 方法自动记录"请求方法、参数、返回值、耗时、异常"，业务代码零侵入。
- 关键实现点：
  - `@Around` 里用 `ProceedingJoinPoint`，`getSignature()` 拿方法签名，`getArgs()` 拿参数。
  - 参数里有 `HttpServletRequest`、`MultipartFile` 这类不能序列化的对象，要过滤掉，否则日志会报错或打印一堆无用内容。
  - 耗时用 `System.nanoTime()`，不要用 `currentTimeMillis`（受系统时间调整影响）。
  - 异常要记 error 日志后**原样抛出**，不能吞掉，否则全局异常处理器收不到。
  - 日志量大时要采样或只记录慢请求（超过 500ms 才打）。
- 与 `MDC` 配合：把 traceId 放进 MDC，日志格式里带 `%X{traceId}`，一个请求的所有日志能串起来。
- 易错：在切面里再查一次数据库（把日志切面写成业务切面），会显著拖慢接口；日志里打印完整大对象导致 OOM。

**示例代码**
```java
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

public class LogAspectDemo {
    public static void main(String[] args) {
        UserService target = new UserServiceImpl();
        UserService proxy = (UserService) Proxy.newProxyInstance(
                LogAspectDemo.class.getClassLoader(),
                new Class<?>[]{UserService.class},
                new LogAspect(target));

        System.out.println("=== 正常请求 ===");
        System.out.println("  返回：" + proxy.findById(1L));

        System.out.println();
        System.out.println("=== 慢请求（超过阈值才打详细日志）===");
        System.out.println("  返回：" + proxy.listUsers(200));

        System.out.println();
        System.out.println("=== 抛异常的请求 ===");
        try {
            proxy.findById(null);
        } catch (IllegalArgumentException e) {
            System.out.println("  上层收到异常：" + e.getMessage());
        }
    }

    interface UserService {
        String findById(Long id);

        String listUsers(int size);
    }

    static class UserServiceImpl implements UserService {
        @Override
        public String findById(Long id) {
            if (id == null) {
                throw new IllegalArgumentException("id 不能为空");
            }
            return "用户" + id;
        }

        @Override
        public String listUsers(int size) {
            sleep(size);                       // 模拟慢查询
            return size + " 条用户数据";
        }
    }

    /** 模拟 @Around 日志切面 */
    static class LogAspect implements InvocationHandler {
        private static final long SLOW_MS = 100;
        private final Object target;

        LogAspect(Object target) {
            this.target = target;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String traceId = Integer.toHexString(System.identityHashCode(Thread.currentThread()));
            String sig = method.getDeclaringClass().getSimpleName() + "." + method.getName();
            String params = formatArgs(method, args);

            long start = System.nanoTime();
            try {
                Object result = method.invoke(target, args);
                long costMs = (System.nanoTime() - start) / 1_000_000;
                if (costMs >= SLOW_MS) {
                    System.out.println("  [SLOW] traceId=" + traceId + " " + sig + params
                            + " cost=" + costMs + "ms");
                } else {
                    System.out.println("  [INFO] traceId=" + traceId + " " + sig + params
                            + " cost=" + costMs + "ms result=" + brief(result));
                }
                return result;
            } catch (Exception e) {
                long costMs = (System.nanoTime() - start) / 1_000_000;
                System.out.println("  [ERROR] traceId=" + traceId + " " + sig + params
                        + " cost=" + costMs + "ms ex=" + e.getCause());
                throw e.getCause();
            }
        }

        /** 过滤掉不能/不该打日志的参数 */
        static String formatArgs(Method method, Object[] args) {
            if (args == null || args.length == 0) {
                return "()";
            }
            Map<String, Object> map = new LinkedHashMap<>();
            String[] names = method.getParameters()[0].getName().isEmpty()
                    ? null : Arrays.stream(method.getParameters()).map(p -> p.getName()).toArray(String[]::new);
            for (int i = 0; i < args.length; i++) {
                if (args[i] instanceof byte[]) {
                    map.put(name(names, i), "<binary " + ((byte[]) args[i]).length + " bytes>");
                } else {
                    map.put(name(names, i), args[i]);
                }
            }
            return " " + map;
        }

        static String name(String[] names, int i) {
            return names != null && i < names.length ? names[i] : "arg" + i;
        }

        static String brief(Object o) {
            String s = String.valueOf(o);
            return s.length() > 30 ? s.substring(0, 30) + "..." : s;
        }
    }

    static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
```

**课后练习**
1. 为什么日志切面必须把异常原样抛出？如果吞掉会有什么后果？
2. 参数里有 `MultipartFile` 和 `HttpServletRequest` 时怎么处理？写出过滤逻辑的思路。
3. 给这个切面加"只记录耗时超过 500ms 的请求"和"每天日志量上限"两个开关，说明如何配置化。

### 26-04 AOP 实战：自定义注解做权限校验

**知识点**
- 思路：定义一个注解 `@RequireRole("admin")`，标在需要权限的方法上；切面读注解的元数据决定是否放行。
- 注解三要素：`@Retention(RUNTIME)`（运行期可反射读取）、`@Target(METHOD/TYPE)`（能标在哪）、`@Documented`（可选）。
- 切面用 `@annotation(...)` 切入点，或在 `@Around` 里 `method.getAnnotation(RequireRole.class)` 读取。
- 权限上下文从 `ThreadLocal` 或 `RequestContextHolder` 取当前登录用户，避免每个方法都传 `User` 参数。
- 拦截方式：不满足条件直接抛 `AccessDeniedException`，交给全局异常处理器转成 403。
- 同一套机制可扩展成：`@RateLimit(qps=10)` 限流、`@Idempotent` 幂等、`@Log` 审计。
- 易错：注解加在接口上而实现类没加（JDK 代理读实现类方法时拿不到接口注解）；`ThreadLocal` 用完不 `remove` 导致线程池串数据。

**示例代码**
```java
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class PermissionAspect {
    /** 当前登录用户，真实项目里由拦截器解析 token 后放入 */
    static final ThreadLocal<String> CURRENT_ROLE = new ThreadLocal<>();

    public static void main(String[] args) {
        AdminService target = new AdminServiceImpl();
        AdminService proxy = (AdminService) Proxy.newProxyInstance(
                PermissionAspect.class.getClassLoader(),
                new Class<?>[]{AdminService.class},
                new RoleCheckHandler(target));

        System.out.println("=== 普通用户访问 ===");
        CURRENT_ROLE.set("user");
        try {
            proxy.queryScore(1L);
            System.out.println("  查询成绩成功（该方法没有权限要求）");
        } catch (RuntimeException e) {
            System.out.println("  " + e.getMessage());
        }
        try {
            proxy.deleteUser(1L);
        } catch (RuntimeException e) {
            System.out.println("  删除用户被拦截：" + e.getMessage());
        }

        System.out.println();
        System.out.println("=== 管理员访问 ===");
        CURRENT_ROLE.set("admin");
        try {
            System.out.println("  " + proxy.deleteUser(1L));
        } catch (RuntimeException e) {
            System.out.println("  " + e.getMessage());
        } finally {
            CURRENT_ROLE.remove();     // 必须清理，否则线程池复用时串数据
        }

        System.out.println();
        System.out.println("=== 注解定义 ===");
        System.out.println("""
                  @Retention(RetentionPolicy.RUNTIME)
                  @Target(ElementType.METHOD)
                  public @interface RequireRole {
                      String value();
                  }
                """);
        System.out.println("  切面里：RequireRole r = method.getAnnotation(RequireRole.class);");
        System.out.println("          if (r != null && !r.value().equals(CURRENT_ROLE.get())) throw ...");
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface RequireRole {
        String value();
    }

    interface AdminService {
        String queryScore(Long id);

        @RequireRole("admin")
        String deleteUser(Long id);
    }

    static class AdminServiceImpl implements AdminService {
        @Override
        public String queryScore(Long id) {
            return "用户" + id + " 的成绩：92";
        }

        @Override
        public String deleteUser(Long id) {
            return "已删除用户 " + id;
        }
    }

    static class RoleCheckHandler implements InvocationHandler {
        private final Object target;

        RoleCheckHandler(Object target) {
            this.target = target;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            RequireRole require = method.getAnnotation(RequireRole.class);
            if (require != null) {
                String current = CURRENT_ROLE.get();
                if (!require.value().equals(current)) {
                    throw new SecurityException("无权限：需要 " + require.value() + "，当前是 " + current);
                }
            }
            return method.invoke(target, args);
        }
    }
}
```

**课后练习**
1. 用同一套思路实现 `@RateLimit(qps = 5)`：同一方法每秒最多放行 5 次，超出抛异常。
2. 为什么 `ThreadLocal` 必须 `remove()`？举一个线程池下没清理导致用户 A 看到用户 B 数据的场景。
3. 注解标在接口方法上、实现类方法上、两者都标，JDK 代理和 CGLIB 分别能读到哪一个？

### 26-05 MyBatis-Plus：消灭样板 CRUD

**知识点**
- MyBatis 的痛点：每张表都要写 `insert`、`selectById`、`updateById`、`deleteById`、`selectList`，10 张表就是 50 个方法 + 50 段 SQL，且大同小异。
- MyBatis-Plus（MP）在 MyBatis 上做增强，**只做增强不做改变**：原有 MyBatis 用法全部保留，MP 额外提供通用 CRUD。
- 核心接口 `BaseMapper<T>`：继承它就白拿 17 个方法，`insert`、`deleteById`、`updateById`、`selectById`、`selectList`、`selectPage`、`selectCount` 等，泛型 T 就是实体类。
- 三个关键注解：
  - `@TableName("t_user")`：实体类对应的表名（类名与表名不一致时必须写）。
  - `@TableId(type = IdType.AUTO)`：主键字段，`AUTO` 数据库自增、`ASSIGN_ID` 雪花算法、`ASSIGN_UUID`。
  - `@TableField("user_name")`：字段名与列名不一致时映射；`@TableField(exist = false)` 表示该字段不是表字段。
- 自动填充：`@TableField(fill = FieldFill.INSERT)` 配合 `MetaObjectHandler` 自动写 `createTime`、`updateTime`、`createBy`。
- 逻辑删除：`@TableLogic` 标注的字段，`deleteById` 实际执行 `UPDATE ... SET deleted = 1`，查询自动加 `deleted = 0`。
- 配置：`mybatis-plus.mapper-locations`、`mybatis-plus.configuration.map-underscore-to-camel-case=true`、`mybatis-plus.global-config.db-config.id-type=auto`。
- 易错：实体类没写 `@TableName` 且类名与表名不一致，报 `Table 'xxx' doesn't exist`；`@TableId` 漏写导致主键策略用默认值；继承 `BaseMapper` 后忘了在启动类加 `@MapperScan`。

**示例代码**
```java
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MyBatisPlusIntro {
    public static void main(String[] args) {
        System.out.println("=== 继承 BaseMapper 就有的方法 ===");
        String[] methods = {
                "insert(T entity)", "deleteById(Serializable id)", "deleteByMap(Map)",
                "delete(Wrapper)", "deleteBatchIds(Collection)",
                "updateById(T entity)", "update(T entity, Wrapper)",
                "selectById(Serializable id)", "selectBatchIds(Collection)",
                "selectByMap(Map)", "selectOne(Wrapper)", "selectCount(Wrapper)",
                "selectList(Wrapper)", "selectMaps(Wrapper)", "selectPage(Page, Wrapper)"};
        for (String m : methods) {
            System.out.println("  " + m);
        }

        System.out.println();
        System.out.println("=== 实体类 ===");
        System.out.println("""
                @TableName("t_user")
                public class User {
                    @TableId(type = IdType.AUTO)
                    private Long id;

                    @TableField("user_name")
                    private String userName;

                    private Integer age;

                    @TableField(fill = FieldFill.INSERT)
                    private String createTime;

                    @TableLogic
                    private Integer deleted;
                }
                """);

        System.out.println("=== 用 BaseMapper 完成增删改查（纯 JDK 模拟）===");
        UserMapper mapper = new UserMapper();
        User u1 = new User();
        u1.userName = "张三";
        u1.age = 18;
        mapper.insert(u1);

        User u2 = new User();
        u2.userName = "李四";
        u2.age = 20;
        mapper.insert(u2);

        System.out.println("  insert 两条后，表内容：");
        mapper.selectList().forEach(u -> System.out.println("    " + u));

        System.out.println();
        System.out.println("  selectById(1) -> " + mapper.selectById(1L));

        u1.age = 19;
        mapper.updateById(u1);
        System.out.println("  updateById 后 selectById(1) -> " + mapper.selectById(1L));

        mapper.deleteById(2L);
        System.out.println("  deleteById(2) 后剩余 " + mapper.selectCount() + " 条（逻辑删除，行还在，deleted=1）");
        System.out.println("  底层 SQL：UPDATE t_user SET deleted = 1 WHERE id = 2 AND deleted = 0");
    }

    /** 模拟实体 */
    static class User {
        Long id;
        String userName;
        Integer age;
        String createTime;
        Integer deleted = 0;

        @Override
        public String toString() {
            return "User{id=" + id + ", userName='" + userName + "', age=" + age
                    + ", deleted=" + deleted + "}";
        }
    }

    /** 模拟 BaseMapper<User>：通用 CRUD 由反射按字段名自动生成 */
    static class UserMapper {
        private final Map<Long, User> table = new LinkedHashMap<>();
        private long seq = 0;

        int insert(User entity) {
            entity.id = ++seq;                       // IdType.AUTO
            entity.createTime = "2026-11-17 10:00:00";  // MetaObjectHandler 自动填充
            entity.deleted = 0;
            table.put(entity.id, entity);
            return 1;
        }

        User selectById(Long id) {
            User u = table.get(id);
            return u != null && u.deleted == 0 ? u : null;   // @TableLogic 自动过滤
        }

        int updateById(User entity) {
            User old = table.get(entity.id);
            if (old == null || old.deleted != 0) {
                return 0;
            }
            copyNonNull(entity, old);                // MP 默认只更新非 null 字段
            return 1;
        }

        int deleteById(Long id) {
            User u = table.get(id);
            if (u == null || u.deleted != 0) {
                return 0;
            }
            u.deleted = 1;                           // 逻辑删除
            return 1;
        }

        List<User> selectList() {
            List<User> res = new ArrayList<>();
            for (User u : table.values()) {
                if (u.deleted == 0) {
                    res.add(u);
                }
            }
            return res;
        }

        long selectCount() {
            return selectList().size();
        }

        /** 反射拷贝非 null 字段，等价于 MP 的 updateById 语义 */
        static void copyNonNull(Object src, Object dst) {
            for (Field f : src.getClass().getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) {
                    continue;
                }
                f.setAccessible(true);
                try {
                    Object v = f.get(src);
                    if (v != null) {
                        f.set(dst, v);
                    }
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException(e);
                }
            }
        }
    }
}
```

**课后练习**
1. 写出一个 `Order` 实体类，包含 `id`、`orderNo`、`userId`、`amount`、`createTime`、`deleted`，标全 MP 需要的注解。
2. `IdType.AUTO`、`ASSIGN_ID`、`ASSIGN_UUID` 分别适合什么场景？分库分表时为什么不能用 `AUTO`？
3. 逻辑删除开启后，`selectById` 和 `deleteById` 生成的 SQL 分别是什么？如何查询已删除的数据？

### 26-06 条件构造器与分页

**知识点**
- `QueryWrapper` / `LambdaQueryWrapper`：用 Java 代码拼 `WHERE` 条件，避免手写 SQL 字符串拼接。
- 常用方法：`eq`（=）、`ne`（<>）、`gt/ge/lt/le`（> >= < <=）、`like`（%x%）、`likeRight`（x%）、`in`、`between`、`isNull`、`orderByDesc`、`last`（拼在最后，慎用）。
- 条件生效控制：`eq(condition, column, value)` 第一个参数为 `false` 时该条件不拼，用于"参数为空就不查这个字段"。
- `LambdaQueryWrapper` 用方法引用 `User::getAge` 代替字符串列名，重构时不会写错、编译器能检查，**推荐优先使用**。
- 分页：`Page<User> page = new Page<>(1, 10)` + `mapper.selectPage(page, wrapper)`，需要注册 `MybatisPlusInterceptor` + `PaginationInnerInterceptor`，否则分页不生效（返回全量）。
- `Page` 的字段：`records`（当前页数据）、`total`（总条数）、`pages`（总页数）、`current`、`size`、`hasNext`。
- 分页插件原理：拦截 SQL 后自动改写成 `LIMIT offset, size` 并额外执行一条 `COUNT`。
- 易错：忘配分页插件，`selectPage` 返回全部数据但 `total` 为 0；`orderByDesc` 拼了用户可控的字段名会有 SQL 注入风险（列名不能参数化）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class WrapperAndPage {
    static final List<User> TABLE = new ArrayList<>();

    public static void main(String[] args) {
        init();

        System.out.println("=== QueryWrapper 拼条件 ===");
        QueryWrapper w1 = new QueryWrapper()
                .eq("age", 20)
                .orderByDesc("id");
        System.out.println("  " + w1.toSql());
        System.out.println("  命中：" + query(w1));

        QueryWrapper w2 = new QueryWrapper()
                .ge("age", 18)
                .le("age", 30)
                .like("user_name", "张");
        System.out.println("  " + w2.toSql());
        System.out.println("  命中：" + query(w2));

        System.out.println();
        System.out.println("=== 条件为空就不拼（eq 的第一个参数）===");
        String keyword = null;
        QueryWrapper w3 = new QueryWrapper()
                .eq("deleted", 0)
                .eq(keyword != null, "user_name", keyword);
        System.out.println("  keyword 为 null：" + w3.toSql());

        keyword = "张";
        QueryWrapper w4 = new QueryWrapper()
                .eq("deleted", 0)
                .eq(keyword != null, "user_name", keyword);
        System.out.println("  keyword 有值：" + w4.toSql());

        System.out.println();
        System.out.println("=== 分页 ===");
        Page page = new Page(2, 3);      // 第 2 页，每页 3 条
        Page result = selectPage(page, new QueryWrapper().orderByDesc("id"));
        System.out.println("  total=" + result.total + " pages=" + result.pages
                + " current=" + result.current + " size=" + result.size
                + " hasNext=" + result.hasNext);
        System.out.println("  当前页数据：" + result.records);

        System.out.println();
        System.out.println("=== 分页插件必须注册 ===");
        System.out.println("""
                @Bean
                public MybatisPlusInterceptor mybatisPlusInterceptor() {
                    MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
                    interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
                    return interceptor;
                }
                """);
        System.out.println("  不注册 -> selectPage 返回全量数据，total 恒为 0");
    }

    static void init() {
        String[][] data = {
                {"1", "张三", "18"}, {"2", "李四", "20"}, {"3", "王五", "22"},
                {"4", "张小明", "25"}, {"5", "赵六", "30"}, {"6", "孙七", "35"},
                {"7", "周八", "20"}, {"8", "吴九", "28"}};
        for (String[] d : data) {
            TABLE.add(new User(Long.parseLong(d[0]), d[1], Integer.parseInt(d[2])));
        }
    }

    static List<User> query(QueryWrapper w) {
        return TABLE.stream()
                .filter(w::test)
                .collect(Collectors.toList());
    }

    static Page selectPage(Page page, QueryWrapper w) {
        List<User> all = query(w);
        page.total = all.size();
        page.pages = (all.size() + page.size - 1) / page.size;
        int from = (int) Math.min((page.current - 1) * page.size, all.size());
        int to = (int) Math.min((long) from + page.size, all.size());
        page.records = new ArrayList<>(all.subList(from, to));
        page.hasNext = page.current < page.pages;
        return page;
    }

    static class User {
        Long id;
        String userName;
        Integer age;

        User(Long id, String userName, Integer age) {
            this.id = id;
            this.userName = userName;
            this.age = age;
        }

        @Override
        public String toString() {
            return id + ":" + userName + "(" + age + ")";
        }
    }

    /** 极简 QueryWrapper：只演示条件拼装与匹配 */
    static class QueryWrapper {
        private final List<Cond> conds = new ArrayList<>();
        private String orderBy;
        private boolean desc;

        QueryWrapper eq(String col, Object v) {
            conds.add(new Cond(col, "=", v));
            return this;
        }

        QueryWrapper eq(boolean enable, String col, Object v) {
            if (enable) {
                conds.add(new Cond(col, "=", v));
            }
            return this;
        }

        QueryWrapper ge(String col, Object v) {
            conds.add(new Cond(col, ">=", v));
            return this;
        }

        QueryWrapper le(String col, Object v) {
            conds.add(new Cond(col, "<=", v));
            return this;
        }

        QueryWrapper like(String col, String v) {
            conds.add(new Cond(col, "LIKE", "%" + v + "%"));
            return this;
        }

        QueryWrapper orderByDesc(String col) {
            this.orderBy = col;
            this.desc = true;
            return this;
        }

        String toSql() {
            String where = conds.stream()
                    .map(c -> c.col + " " + c.op + " " + fmt(c.val))
                    .collect(Collectors.joining(" AND "));
            String sql = "SELECT * FROM t_user WHERE " + where;
            if (orderBy != null) {
                sql += " ORDER BY " + orderBy + (desc ? " DESC" : " ASC");
            }
            return sql;
        }

        boolean test(User u) {
            Map<String, Function<User, Object>> getters = new LinkedHashMap<>();
            getters.put("id", x -> x.id);
            getters.put("user_name", x -> x.userName);
            getters.put("age", x -> x.age);
            getters.put("deleted", x -> 0);
            for (Cond c : conds) {
                Object actual = getters.get(c.col).apply(u);
                if (!match(actual, c)) {
                    return false;
                }
            }
            return true;
        }

        static boolean match(Object actual, Cond c) {
            if ("LIKE".equals(c.op)) {
                String needle = String.valueOf(c.val).replace("%", "");
                return String.valueOf(actual).contains(needle);
            }
            int cmp = Long.compare(
                    Long.parseLong(String.valueOf(actual)),
                    Long.parseLong(String.valueOf(c.val)));
            switch (c.op) {
                case "=":
                    return cmp == 0;
                case ">=":
                    return cmp >= 0;
                case "<=":
                    return cmp <= 0;
                default:
                    return false;
            }
        }

        static String fmt(Object v) {
            return v instanceof String ? "'" + v + "'" : String.valueOf(v);
        }
    }

    static class Cond {
        final String col;
        final String op;
        final Object val;

        Cond(String col, String op, Object val) {
            this.col = col;
            this.op = op;
            this.val = val;
        }
    }

    static class Page {
        long total;
        long pages;
        long current;
        long size;
        boolean hasNext;
        List<User> records = new ArrayList<>();

        Page(long current, long size) {
            this.current = current;
            this.size = size;
        }
    }
}
```

**课后练习**
1. 用 `LambdaQueryWrapper` 写一个查询：年龄 18~30、用户名以"张"开头、按 id 倒序。
2. 为什么推荐 `LambdaQueryWrapper` 而不是字符串列名的 `QueryWrapper`？举一个重构后字符串方案出错的例子。
3. `selectPage` 返回 `total=0` 但 `records` 有数据，最可能的原因是什么？怎么排查？

### 26-07 文件上传

**知识点**
- 前端表单必须是 `enctype="multipart/form-data"`，否则后端收不到文件；Spring MVC 用 `MultipartFile` 接收。
- 单文件：`@RequestParam("file") MultipartFile file`；多文件：`MultipartFile[] files` 或 `List<MultipartFile>`。
- 配置限制：`spring.servlet.multipart.max-file-size=10MB`、`max-request-size=100MB`，超出抛 `MaxUploadSizeExceededException`。
- 安全校验（缺一不可）：
  1. 文件大小（业务上限，别只靠框架配置）；
  2. 扩展名白名单（`.jpg/.png/.pdf`）；
  3. **MIME / 文件头魔数**校验（扩展名可以伪造，`Content-Type` 也可以伪造）；
  4. 文件名重命名（UUID），绝不使用用户上传的原始文件名拼接路径——否则 `../../etc/passwd` 就是路径穿越漏洞；
  5. 存储目录不在 Web 根目录下，或存储后由后端做权限校验再输出。
- 常见魔数：JPEG `FF D8 FF`、PNG `89 50 4E 47`、GIF `47 49 46 38`、PDF `25 50 44 46`、ZIP/DOCX `50 4B 03 04`。
- 存储位置：本地磁盘（单机）、对象存储 OSS/MinIO（推荐，天然支持 CDN 与水平扩展）。数据库只存 URL。
- 易错：把文件存进数据库 BLOB（大文件拖垮数据库）；用原始文件名直接拼路径；上传目录可被直接访问导致脚本被解析执行。

**示例代码**
```java
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class FileUploadDemo {
    static final long MAX_SIZE = 2 * 1024 * 1024;          // 2MB
    static final List<String> ALLOWED_EXT = List.of("jpg", "jpeg", "png", "pdf");
    static final Map<String, byte[]> MAGIC = new LinkedHashMap<>();

    static {
        MAGIC.put("jpg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});
        MAGIC.put("png", new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47});
        MAGIC.put("pdf", new byte[]{0x25, 0x50, 0x44, 0x46});
    }

    public static void main(String[] args) throws IOException {
        Path uploadDir = Files.createTempDirectory("upload-demo-");
        try {
            System.out.println("=== 正常上传 PNG ===");
            byte[] png = concat(new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A},
                    "fake-png-body".getBytes());
            System.out.println("  " + upload("头像.png", png, uploadDir));

            System.out.println();
            System.out.println("=== 伪造扩展名：exe 改名成 jpg ===");
            byte[] exe = concat("MZ".getBytes(), "fake-exe".getBytes());
            System.out.println("  " + upload("virus.jpg", exe, uploadDir));

            System.out.println();
            System.out.println("=== 路径穿越：文件名带 ../ ===");
            byte[] ok = concat(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}, "jpeg".getBytes());
            System.out.println("  " + upload("../../evil.jpg", ok, uploadDir));
            System.out.println();
            System.out.println("=== 超出大小限制 ===");
            byte[] big = new byte[(int) MAX_SIZE + 1];
            big[0] = (byte) 0xFF;
            big[1] = (byte) 0xD8;
            big[2] = (byte) 0xFF;
            System.out.println("  " + upload("big.jpg", big, uploadDir));

            System.out.println();
            System.out.println("=== 不在白名单的扩展名 ===");
            System.out.println("  " + upload("shell.jsp", ok, uploadDir));

            System.out.println();
            System.out.println("=== 上传目录内容 ===");
            try (var s = Files.list(uploadDir)) {
                s.forEach(p -> System.out.println("  " + p.getFileName()));
            }
        } finally {
            // 清理临时目录
            try (var s = Files.walk(uploadDir)) {
                s.sorted((a, b) -> b.getNameCount() - a.getNameCount())
                        .forEach(p -> {
                            try {
                                Files.deleteIfExists(p);
                            } catch (IOException ignored) {
                                // 清理失败不影响演示
                            }
                        });
            }
        }

        System.out.println();
        System.out.println("=== 控制器写法 ===");
        System.out.println("""
                @PostMapping("/upload")
                public Result<String> upload(@RequestParam("file") MultipartFile file) throws IOException {
                    if (file.isEmpty()) {
                        return Result.fail("文件为空");
                    }
                    String original = file.getOriginalFilename();
                    // 1. 原始文件名不许带路径，也不许含 ..
                    if (original == null || original.contains("/") || original.contains("\\\\")
                            || original.contains("..")) {
                        return Result.fail("非法文件名");
                    }
                    // 2. 扩展名白名单
                    String ext = original.substring(original.lastIndexOf('.') + 1).toLowerCase();
                    if (!ALLOWED_EXT.contains(ext)) {
                        return Result.fail("不支持的文件类型");
                    }
                    // 3. 落盘用 UUID 重命名，二次确认路径在目录内
                    String newName = UUID.randomUUID() + "." + ext;
                    Path base = Paths.get(uploadDir).toAbsolutePath().normalize();
                    Path target = base.resolve(newName).normalize();
                    if (!target.startsWith(base)) {
                        return Result.fail("非法文件名");
                    }
                    file.transferTo(target.toFile());
                    return Result.ok("/files/" + newName);
                }
                """);
    }

    /** 返回校验结果描述；通过则落盘 */
    static String upload(String filename, byte[] content, Path dir) throws IOException {
        if (content == null || content.length == 0) {
            return "拒绝：文件为空";
        }
        if (content.length > MAX_SIZE) {
            return "拒绝：文件 " + content.length + " 字节，超过上限 " + MAX_SIZE;
        }
        // 第一道：原始文件名不能带路径分隔符，也不能是 ..
        if (filename.contains("/") || filename.contains("\\") || filename.contains("..")) {
            return "拒绝：文件名含路径穿越字符（" + filename + "）";
        }
        String ext = extOf(filename);
        if (!ALLOWED_EXT.contains(ext)) {
            return "拒绝：扩展名 ." + ext + " 不在白名单 " + ALLOWED_EXT;
        }
        if (!magicMatches(ext, content)) {
            return "拒绝：文件头与扩展名不符（伪造的 ." + ext + "）";
        }
        // 第二道：即使校验被绕过，落盘也用 UUID 重命名，永不拼接原始文件名
        String safeName = UUID.randomUUID() + "." + ext;
        Path target = dir.resolve(safeName).normalize();
        if (!target.startsWith(dir)) {
            return "拒绝：路径穿越";
        }
        Files.write(target, content);
        return "成功：原始名 " + filename + " -> 存储为 " + safeName;
    }

    static String extOf(String filename) {
        int i = filename.lastIndexOf('.');
        return i < 0 ? "" : filename.substring(i + 1).toLowerCase();
    }

    static boolean magicMatches(String ext, byte[] content) {
        byte[] magic = MAGIC.get(ext);
        if (magic == null) {
            return true;      // pdf 之外的类型按扩展名放行
        }
        if (content.length < magic.length) {
            return false;
        }
        for (int i = 0; i < magic.length; i++) {
            if (content[i] != magic[i]) {
                return false;
            }
        }
        return true;
    }

    static byte[] concat(byte[] a, byte[] b) {
        byte[] r = Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, r, a.length, b.length);
        return r;
    }
}
```

**课后练习**
1. 为什么"检查扩展名"不足以防止上传木马？写出文件头校验的思路和三种常见类型的魔数。
2. 用户上传文件名是 `../../../webapps/ROOT/shell.jsp`，不做处理会发生什么？给出三层防御措施。
3. 设计一个"头像上传"接口：限制 2MB、只允许 jpg/png、返回可访问 URL，说明文件存哪里、URL 怎么生成。

### 26-08 JWT：无状态令牌

**知识点**
- 传统 Session 的问题：服务端存 session，多实例要共享（粘性会话或 Redis），横向扩展麻烦，跨域也麻烦。
- JWT（JSON Web Token）是**自包含**的令牌：服务端不存，客户端每次带上，服务端验签即可。三部分组成，用 `.` 分隔：
  - Header：`{"alg":"HS256","typ":"JWT"}`，Base64URL 编码。
  - Payload：claims，标准字段 `sub`（主体）、`iat`（签发时间）、`exp`（过期时间）、`jti`（唯一 id），也可放自定义字段如 `userId`、`role`。**注意 payload 只是 Base64 编码，不是加密，任何人都能解开看**。
  - Signature：`HMACSHA256(base64Url(header) + "." + base64Url(payload), secret)`。
- 验签流程：拆三段 -> 重算签名比对 -> 校验 `exp` 未过期 -> 取出 claims。
- 关键原则：
  - 绝不在 payload 放密码、手机号、身份证等敏感信息。
  - secret 必须足够长（>= 32 字节）且只存在服务端配置/环境变量里，不能提交到 git。
  - `exp` 必须设，且不宜过长（一般 30 分钟 ~ 2 小时）；长会话用 refresh token。
  - 令牌泄露无法主动失效（无状态），需要"黑名单/白名单"或短期 token + 刷新机制兜底。
- 易错：用 `none` 算法（不验签）导致任意伪造；把 `alg` 交给客户端决定（算法混淆攻击）；secret 硬编码在代码里。

**示例代码**
```java
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class JwtDemo {
    static final String SECRET = "heima-2026-java-course-secret-key-32bytes!";
    static final Base64.Encoder B64E = Base64.getUrlEncoder().withoutPadding();
    static final Base64.Decoder B64D = Base64.getUrlDecoder();

    public static void main(String[] args) throws Exception {
        System.out.println("=== 签发 token ===");
        String token = createToken(1001L, "张三", "user", 3600);
        System.out.println("  " + token);
        System.out.println("  长度 " + token.length() + " 字符，三段用 . 分隔");

        System.out.println();
        System.out.println("=== 三段内容（注意 payload 是明文可解的）===");
        String[] parts = token.split("\\.");
        System.out.println("  Header  : " + decode(parts[0]));
        System.out.println("  Payload : " + decode(parts[1]));
        System.out.println("  Signature（二进制，不可读）: " + parts[2].substring(0, 16) + "...");

        System.out.println();
        System.out.println("=== 验签通过 ===");
        Map<String, Object> claims = verify(token);
        System.out.println("  claims = " + claims);

        System.out.println();
        System.out.println("=== 篡改 payload：把 role 改成 admin ===");
        String forgedPayload = B64E.encodeToString(
                decode(parts[1]).replace("\"user\"", "\"admin\"").getBytes(StandardCharsets.UTF_8));
        String forged = parts[0] + "." + forgedPayload + "." + parts[2];
        try {
            verify(forged);
        } catch (RuntimeException e) {
            System.out.println("  " + e.getMessage() + "（签名对不上，篡改被识破）");

        }

        System.out.println();
        System.out.println("=== 换一个 secret 签发的 token ===");
        String other = createTokenWithSecret(1001L, "hacker-secret-hacker-secret-hacker!", 3600);
        try {
            verify(other);
        } catch (RuntimeException e) {
            System.out.println("  " + e.getMessage());
        }

        System.out.println();
        System.out.println("=== 过期 token ===");
        String expired = createToken(1001L, "张三", "user", -10);
        try {
            verify(expired);
        } catch (RuntimeException e) {
            System.out.println("  " + e.getMessage());
        }

        System.out.println();
        System.out.println("=== 与 Session 对比 ===");
        Map<String, String> cmp = new LinkedHashMap<>();
        cmp.put("存储位置", "Session 存服务端；JWT 存客户端");
        cmp.put("扩展性", "Session 多实例要共享；JWT 天然无状态");
        cmp.put("注销", "Session 删了即失效；JWT 到期前无法主动失效");
        cmp.put("体积", "Session 只传 sessionId；JWT 每次带完整 claims");
        cmp.forEach((k, v) -> System.out.printf("  %-8s %s%n", k, v));
    }

    static String createToken(long userId, String name, String role, int expireSeconds) throws Exception {
        return createTokenWithSecret(userId, name, role, expireSeconds, SECRET);
    }

    static String createTokenWithSecret(long userId, String secret, int expireSeconds) throws Exception {
        return createTokenWithSecret(userId, "张三", "user", expireSeconds, secret);
    }

    static String createTokenWithSecret(long userId, String name, String role,
                                        int expireSeconds, String secret) throws Exception {
        long now = System.currentTimeMillis() / 1000;
        String header = B64E.encodeToString(
                "{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String payload = B64E.encodeToString(("{\"userId\":" + userId
                + ",\"name\":\"" + name + "\",\"role\":\"" + role + "\""
                + ",\"iat\":" + now + ",\"exp\":" + (now + expireSeconds) + "}")
                .getBytes(StandardCharsets.UTF_8));
        String signingInput = header + "." + payload;
        String signature = B64E.encodeToString(hmac(signingInput, secret));
        return signingInput + "." + signature;
    }

    /** 验签 + 校验过期，返回 claims */
    static Map<String, Object> verify(String token) throws Exception {
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new SecurityException("token 格式错误");
        }
        String expected = B64E.encodeToString(hmac(parts[0] + "." + parts[1], SECRET));
        // 用 MessageDigest.isEqual 做定长比较，避免时序攻击
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                parts[2].getBytes(StandardCharsets.UTF_8))) {
            throw new SecurityException("签名校验失败：token 被篡改或 secret 不一致");
        }
        Map<String, Object> claims = parseClaims(decode(parts[1]));
        long exp = ((Number) claims.get("exp")).longValue();
        long now = System.currentTimeMillis() / 1000;
        if (now > exp) {
            throw new SecurityException("token 已过期，exp=" + exp + "，now=" + now);
        }
        return claims;
    }

    static byte[] hmac(String data, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    }

    static String decode(String b64) {
        return new String(B64D.decode(b64), StandardCharsets.UTF_8);
    }

    /** 极简 JSON 解析：只处理本课生成的扁平结构 */
    static Map<String, Object> parseClaims(String json) {
        Map<String, Object> map = new LinkedHashMap<>();
        String body = json.substring(1, json.length() - 1);
        for (String kv : body.split(",")) {
            String[] p = kv.split(":", 2);
            String key = p[0].replace("\"", "").trim();
            String val = p[1].trim();
            if (val.startsWith("\"")) {
                map.put(key, val.replace("\"", ""));
            } else {
                map.put(key, Long.parseLong(val));
            }
        }
        return map;
    }
}
```

**课后练习**
1. 手写一遍 JWT 的三段结构，说明为什么 payload 不能放密码。
2. 为什么签名要用 `HMAC` 而不是 `MD5(token)`？两者在防篡改上的差别是什么？
3. JWT 无法主动失效，设计一个"用户改密码后立即让旧 token 失效"的方案（提示：Redis 存 token 版本号或 jti 黑名单）。

### 26-09 登录鉴权闭环

**知识点**
- 完整流程：`POST /login`（账号+密码）-> 校验密码 -> 签发 token -> 返回给前端 -> 前端存起来（内存/localStorage）-> 后续请求带 `Authorization: Bearer <token>` -> 拦截器验签 -> 放行并把用户信息放入上下文。
- 密码存储：**绝不能明文**，也不能只用 MD5（彩虹表可查）。正确做法是加盐哈希 + 慢哈希（BCrypt、Argon2），每次盐不同，同一密码两次哈希结果不同。
- 登录接口本身要放行，其它接口默认拦截；用白名单（`/login`、`/register`、静态资源）而不是黑名单。
- 令牌放哪：`localStorage` 简单但会被 XSS 读取；`HttpOnly Cookie` 能防 XSS 但要注意 CSRF。移动端/前后端分离项目常用 `Authorization` 头。
- 刷新机制：access token 短期（30min）+ refresh token 长期（7 天，存 Redis 可撤销）。access 过期时用 refresh 换新的，避免频繁重新登录。
- 登出：无状态 token 无法真正失效，通常把 jti 或 token 加入 Redis 黑名单直到其自然过期，或维护"用户 token 版本号"。
- 易错：登录接口返回用户密码字段（序列化时要用 `@JsonIgnore`）；token 放在 URL 参数里（会进日志和浏览器历史）；没校验 token 的 `alg`。

**示例代码**
```java
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public class LoginFlow {
    /** 模拟用户表：username -> (salt, passwordHash) */
    static final Map<String, String[]> USERS = new LinkedHashMap<>();
    static final Base64.Encoder B64 = Base64.getEncoder();

    public static void main(String[] args) throws Exception {
        // 注册：密码加盐 + PBKDF2 慢哈希
        register("zhangsan", "Passw0rd!");
        register("lisi", "123456");

        System.out.println("=== 注册后存的是什么 ===");
        for (Map.Entry<String, String[]> e : USERS.entrySet()) {
            System.out.println("  " + e.getKey() + " salt=" + e.getValue()[0]
                    + " hash=" + e.getValue()[1].substring(0, 24) + "...");
        }

        System.out.println();
        System.out.println("=== 登录成功 ===");
        Map<String, String> resp = login("zhangsan", "Passw0rd!");
        System.out.println("  " + resp);
        String token = resp.get("token");

        System.out.println();
        System.out.println("=== 登录失败：密码错误 ===");
        System.out.println("  " + login("zhangsan", "wrong"));

        System.out.println();
        System.out.println("=== 用户不存在（提示要模糊，不能告诉攻击者账号是否存在）===");
        System.out.println("  " + login("nobody", "whatever"));

        System.out.println();
        System.out.println("=== 带 token 访问受保护接口 ===");
        System.out.println("  " + access(token, "/api/user/profile"));

        System.out.println();
        System.out.println("=== 不带 token ===");
        System.out.println("  " + access(null, "/api/user/profile"));

        System.out.println();
        System.out.println("=== 密码为什么不能用 MD5 ===");
        String md5a = md5("123456");
        String md5b = md5("123456");
        System.out.println("  MD5 两次结果相同：" + md5a.equals(md5b) + " -> " + md5a);
        System.out.println("  彩虹表里 123456 的 MD5 一查就知道原文");
        System.out.println("  PBKDF2 加盐后两次结果不同，攻击者无法批量反查");
    }

    static void register(String username, String rawPassword) throws Exception {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        String hash = pbkdf2(rawPassword, salt);
        USERS.put(username, new String[]{B64.encodeToString(salt), hash});
    }

    static Map<String, String> login(String username, String rawPassword) throws Exception {
        String[] stored = USERS.get(username);
        Map<String, String> result = new LinkedHashMap<>();
        if (stored == null) {
            result.put("code", "401");
            result.put("msg", "用户名或密码错误");
            return result;
        }
        String hash = pbkdf2(rawPassword, Base64.getDecoder().decode(stored[0]));
        if (!MessageDigest.isEqual(hash.getBytes(StandardCharsets.UTF_8),
                stored[1].getBytes(StandardCharsets.UTF_8))) {
            result.put("code", "401");
            result.put("msg", "用户名或密码错误");
            return result;
        }
        result.put("code", "200");
        result.put("msg", "登录成功");
        result.put("token", JwtMini.create(username, 1800));
        return result;
    }

    static String access(String token, String path) {
        if (token == null || token.isBlank()) {
            return "401 未登录：请先调用 /login 获取 token";
        }
        try {
            Map<String, Object> claims = JwtMini.verify(token);
            return "200 放行 " + path + "，当前用户=" + claims.get("sub");
        } catch (RuntimeException e) {
            return "401 " + e.getMessage();
        }
    }

    static String pbkdf2(String password, byte[] salt) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 10000, 256);
        SecretKeyFactory f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        return B64.encodeToString(f.generateSecret(spec).getEncoded());
    }

    static String md5(String s) throws Exception {
        return B64.encodeToString(
                MessageDigest.getInstance("MD5").digest(s.getBytes(StandardCharsets.UTF_8)));
    }

    /** 极简 JWT，仅演示闭环 */
    static class JwtMini {
        static final String SECRET = "heima-2026-demo-secret-key-32-bytes!!";
        static final Base64.Encoder E = Base64.getUrlEncoder().withoutPadding();
        static final Base64.Decoder D = Base64.getUrlDecoder();

        static String create(String username, int expireSeconds) {
            long now = System.currentTimeMillis() / 1000;
            String h = E.encodeToString("{\"alg\":\"HS256\"}".getBytes(StandardCharsets.UTF_8));
            String p = E.encodeToString(("{\"sub\":\"" + username + "\",\"exp\":"
                    + (now + expireSeconds) + "}").getBytes(StandardCharsets.UTF_8));
            return h + "." + p + "." + E.encodeToString(sign(h + "." + p));
        }

        static Map<String, Object> verify(String token) {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                throw new SecurityException("token 格式错误");
            }
            if (!MessageDigest.isEqual(E.encodeToString(sign(parts[0] + "." + parts[1]))
                    .getBytes(StandardCharsets.UTF_8), parts[2].getBytes(StandardCharsets.UTF_8))) {
                throw new SecurityException("token 无效");
            }
            String payload = new String(D.decode(parts[1]), StandardCharsets.UTF_8);
            long exp = Long.parseLong(payload.replaceAll(".*\"exp\":(\\d+).*", "$1"));
            if (System.currentTimeMillis() / 1000 > exp) {
                throw new SecurityException("token 已过期");
            }
            Map<String, Object> claims = new LinkedHashMap<>();
            claims.put("sub", payload.replaceAll(".*\"sub\":\"([^\"]+)\".*", "$1"));
            claims.put("exp", exp);
            return claims;
        }

        static byte[] sign(String data) {
            try {
                javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
                mac.init(new javax.crypto.spec.SecretKeySpec(
                        SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
                return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
```

**课后练习**
1. 为什么注册接口返回的密码哈希也不能返回给前端？说出至少两个序列化层面的防护手段。
2. 设计 access token + refresh token 的完整时序，说明 refresh token 为什么要存 Redis 而 access token 不用。
3. 用户修改密码后如何让已签发的 token 立刻失效？给出两种方案并比较优缺点。

### 26-10 拦截器与鉴权上下文

**知识点**
- 拦截器（`HandlerInterceptor`）三个方法：`preHandle`（Controller 前，返回 false 中断）、`postHandle`（Controller 后、视图渲染前）、`afterCompletion`（请求完成后，做清理）。
- 与 Filter 的区别：Filter 是 Servlet 规范、在 DispatcherServlet 之前；Interceptor 是 Spring MVC 的、能拿到 `HandlerMethod`（知道要执行哪个方法、有什么注解）。
- 注册：`WebMvcConfigurer#addInterceptors`，`addPathPatterns("/**")` + `excludePathPatterns("/login", "/register", "/error")`。
- 鉴权上下文：`preHandle` 里验 token -> 解析出 userId/role -> 放进 `ThreadLocal`（或 `RequestContextHolder`）-> `afterCompletion` 里 `remove()`。
- 与 26-04 的注解切面配合：拦截器负责"认证"（你是谁），切面负责"授权"（你能不能做这件事）。
- 常见坑：拦截器里抛异常不会走 `@RestControllerAdvice` 的 `@ExceptionHandler`（因为发生在 Handler 之前），要么直接写响应，要么交给 `HandlerExceptionResolver`。
- 静态资源与 `OPTIONS` 预检请求要放行，否则前端跨域请求全挂。
- 易错：`ThreadLocal` 没清理导致内存泄漏与串数据；`excludePathPatterns` 写了 `/login` 但实际路径是 `/api/login`。

**示例代码**
```java
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AuthInterceptor {
    /** 模拟 ThreadLocal 里的登录用户 */
    static final ThreadLocal<String> CURRENT_USER = new ThreadLocal<>();
    static final String SECRET = "heima-2026-demo-secret-key-32-bytes!!";

    public static void main(String[] args) {
        UserController controller = new UserControllerImpl();
        UserController proxy = (UserController) Proxy.newProxyInstance(
                AuthInterceptor.class.getClassLoader(),
                new Class<?>[]{UserController.class},
                new InterceptorChain(controller));

        String token = sign("zhangsan");

        System.out.println("=== 白名单接口：不校验 token ===");
        System.out.println("  " + proxy.login("zhangsan", "Passw0rd!"));

        System.out.println();
        System.out.println("=== 受保护接口：带合法 token ===");
        System.out.println("  " + proxy.profile("Bearer " + token));

        System.out.println();
        System.out.println("=== 受保护接口：不带 token ===");
        System.out.println("  " + proxy.profile(null));

        System.out.println();
        System.out.println("=== 受保护接口：token 被改过 ===");
        System.out.println("  " + proxy.profile("Bearer " + token.substring(0, token.length() - 2) + "xx"));

        System.out.println();
        System.out.println("=== 请求结束后 ThreadLocal 已清理 ===");
        System.out.println("  CURRENT_USER = " + CURRENT_USER.get());

        System.out.println();
        System.out.println("=== 拦截器注册 ===");
        System.out.println("""
                @Configuration
                public class WebConfig implements WebMvcConfigurer {
                    @Override
                    public void addInterceptors(InterceptorRegistry registry) {
                        registry.addInterceptor(new AuthInterceptor())
                                .addPathPatterns("/**")
                                .excludePathPatterns("/login", "/register", "/error", "/files/**");
                    }
                }
                """);
        System.out.println("=== 与 Filter 的分工 ===");
        Map<String, String> cmp = new LinkedHashMap<>();
        cmp.put("Filter", "Servlet 规范，能改 request/response，拿不到 HandlerMethod");
        cmp.put("Interceptor", "Spring MVC，能拿到方法上的注解，适合鉴权与日志");
        cmp.put("AOP 切面", "方法级，能拿到参数与返回值，适合业务埋点");
        cmp.forEach((k, v) -> System.out.printf("  %-12s %s%n", k, v));
    }

    /** 模拟控制器 */
    interface UserController {
        String login(String username, String password);

        String profile(String authorization);
    }

    static class UserControllerImpl implements UserController {
        @Override
        public String login(String username, String password) {
            return "200 登录成功，token=" + sign(username);
        }

        @Override
        public String profile(String authorization) {
            return "200 当前用户=" + CURRENT_USER.get();
        }
    }

    /** 模拟 HandlerInterceptor：preHandle -> 目标方法 -> afterCompletion */
    static class InterceptorChain implements InvocationHandler {
        private static final List<String> WHITE_LIST = List.of("login", "register");
        private final Object target;

        InterceptorChain(Object target) {
            this.target = target;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            // ---- preHandle ----
            if (!WHITE_LIST.contains(method.getName())) {
                String header = args.length > 0 && args[0] instanceof String ? (String) args[0] : null;
                if (header == null || !header.startsWith("Bearer ")) {
                    return "401 未登录";
                }
                try {
                    CURRENT_USER.set(parseSubject(header.substring(7)));
                } catch (RuntimeException e) {
                    return "401 " + e.getMessage();
                }
            }
            try {
                // ---- 目标方法 ----
                return method.invoke(target, args);
            } finally {
                // ---- afterCompletion ----
                CURRENT_USER.remove();
            }
        }
    }

    static String sign(String username) {
        String h = b64("{\"alg\":\"HS256\"}");
        String p = b64("{\"sub\":\"" + username + "\"}");
        return h + "." + p + "." + b64(hmac(h + "." + p));
    }

    static String parseSubject(String token) {
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new SecurityException("token 格式错误");
        }
        if (!b64(hmac(parts[0] + "." + parts[1])).equals(parts[2])) {
            throw new SecurityException("token 无效");
        }
        return new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8)
                .replaceAll(".*\"sub\":\"([^\"]+)\".*", "$1");
    }

    static String b64(String s) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    static String b64(byte[] b) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    static byte[] hmac(String data) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(
                    SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
```

**课后练习**
1. 为什么拦截器里抛出的异常不会被 `@RestControllerAdvice` 捕获？给出两种处理办法。
2. `preHandle` 返回 `false` 和抛异常有什么区别？分别适合什么场景。
3. 前后端分离项目里 `OPTIONS` 预检请求为什么必须放行？不放行会看到什么现象。

### 26-11 综合案例：用户模块

**知识点**
- 模块划分：`UserController`（参数接收与响应）-> `UserService`（业务规则、密码加密、token 签发）-> `UserMapper`（数据访问）。Controller 不写业务，Service 不碰 HTTP 对象。
- 接口设计：
  - `POST /api/user/register` 注册：用户名唯一、密码强度、返回用户 id。
  - `POST /api/user/login` 登录：返回 token + 基础信息（不含密码）。
  - `GET /api/user/profile` 查自己：从 token 取 userId，不接受前端传 id（否则就是越权漏洞）。
  - `PUT /api/user/profile` 改资料：只允许改昵称、头像等白名单字段。
- 统一响应：`Result<T>{code, msg, data}`，业务异常抛 `BusinessException`，由全局异常处理器转成对应 code。
- 越权防护：所有"查/改自己的数据"的接口，userId 一律从 token 里取，绝不能信任前端传参（水平越权最常见的成因）。
- 事务边界：注册涉及"插入用户 + 初始化账户"两步，要加 `@Transactional`；但发短信、发邮件这类外部调用要放在事务外（或事务提交后）。
- 易错：返回实体直接序列化导致密码泄露；更新接口用前端传的整个对象覆盖（把 role 也改了）；唯一索引没建，并发注册出两个同名用户。

**示例代码**
```java
import java.util.LinkedHashMap;
import java.util.Map;

public class UserModuleCase {
    /** 模拟数据库 */
    static final Map<Long, Map<String, Object>> USER_TABLE = new LinkedHashMap<>();
    static long seq = 0;

    public static void main(String[] args) {
        System.out.println("=== 注册 ===");
        System.out.println("  " + register("zhangsan", "Passw0rd!", "张三"));
        System.out.println("  " + register("zhangsan", "Passw0rd!", "张三"));
        System.out.println("  " + register("lisi", "123", "李四"));
        System.out.println("  " + register("lisi", "Passw0rd!", "李四"));

        System.out.println();
        System.out.println("=== 登录 ===");
        String token = null;
        Map<String, Object> loginResp = login("zhangsan", "Passw0rd!");
        System.out.println("  " + loginResp);
        token = (String) loginResp.get("token");

        System.out.println();
        System.out.println("=== 查自己的资料（userId 来自 token）===");
        System.out.println("  " + profile(token));
        System.out.println("  注意：接口不接受前端传 id，杜绝水平越权");

        System.out.println();
        System.out.println("=== 改资料：只允许白名单字段 ===");
        Map<String, Object> patch = new LinkedHashMap<>();
        patch.put("nickname", "张三丰");
        patch.put("avatar", "/files/a.png");
        patch.put("role", "admin");            // 恶意字段，必须被忽略
        System.out.println("  " + updateProfile(token, patch));

        System.out.println();
        System.out.println("=== 未登录访问 ===");
        System.out.println("  " + profile(null));
    }

    static Map<String, Object> register(String username, String rawPassword, String nickname) {
        Map<String, Object> resp = new LinkedHashMap<>();
        if (username == null || username.length() < 4) {
            return fail(resp, 400, "用户名至少 4 位");
        }
        if (!rawPassword.matches("^(?=.*[A-Za-z])(?=.*\\d).{8,}$")) {
            return fail(resp, 400, "密码至少 8 位且含字母和数字");
        }
        for (Map<String, Object> u : USER_TABLE.values()) {
            if (username.equals(u.get("username"))) {
                return fail(resp, 409, "用户名已存在");
            }
        }
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("id", ++seq);
        user.put("username", username);
        user.put("password", "hashed:" + Integer.toHexString(rawPassword.hashCode()));
        user.put("nickname", nickname);
        user.put("role", "user");
        USER_TABLE.put((Long) user.get("id"), user);

        resp.put("code", 200);
        resp.put("msg", "注册成功");
        resp.put("data", Map.of("id", user.get("id")));
        return resp;
    }

    static Map<String, Object> login(String username, String rawPassword) {
        Map<String, Object> resp = new LinkedHashMap<>();
        Map<String, Object> found = null;
        for (Map<String, Object> u : USER_TABLE.values()) {
            if (username.equals(u.get("username"))) {
                found = u;
                break;
            }
        }
        String hashed = "hashed:" + Integer.toHexString(rawPassword.hashCode());
        if (found == null || !hashed.equals(found.get("password"))) {
            return fail(resp, 401, "用户名或密码错误");
        }
        resp.put("code", 200);
        resp.put("msg", "登录成功");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("token", "mock-jwt-" + found.get("id"));
        data.put("nickname", found.get("nickname"));
        data.put("role", found.get("role"));
        resp.put("data", data);            // 不含 password 字段
        resp.put("token", data.get("token"));
        return resp;
    }

    static Map<String, Object> profile(String token) {
        Map<String, Object> resp = new LinkedHashMap<>();
        Long userId = currentUserId(token);
        if (userId == null) {
            return fail(resp, 401, "未登录");
        }
        Map<String, Object> user = USER_TABLE.get(userId);
        Map<String, Object> data = new LinkedHashMap<>(user);
        data.remove("password");           // 出参永不带密码
        resp.put("code", 200);
        resp.put("msg", "ok");
        resp.put("data", data);
        return resp;
    }

    static Map<String, Object> updateProfile(String token, Map<String, Object> patch) {
        Map<String, Object> resp = new LinkedHashMap<>();
        Long userId = currentUserId(token);
        if (userId == null) {
            return fail(resp, 401, "未登录");
        }
        Map<String, Object> user = USER_TABLE.get(userId);
        // 白名单：只有这些字段允许改，role/password 一律忽略
        for (String field : new String[]{"nickname", "avatar"}) {
            if (patch.containsKey(field)) {
                user.put(field, patch.get(field));
            }
        }
        resp.put("code", 200);
        resp.put("msg", "更新成功");
        resp.put("data", Map.of("nickname", user.get("nickname")));
        return resp;
    }

    static Long currentUserId(String token) {
        if (token == null || !token.startsWith("mock-jwt-")) {
            return null;
        }
        try {
            return Long.parseLong(token.substring("mock-jwt-".length()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static Map<String, Object> fail(Map<String, Object> resp, int code, String msg) {
        resp.put("code", code);
        resp.put("msg", msg);
        return resp;
    }
}
```

**课后练习**
1. 把本案例的 token 换成 26-08 的 JWT 实现，并加上"token 过期返回 401、前端跳登录页"的处理。
2. 为什么更新资料要用白名单字段而不是黑名单（排除 password、role）？举一个黑名单漏掉字段被提权的例子。
3. 注册接口并发提交两次相同用户名，如何保证只成功一次？从数据库和代码两个层面说明。
