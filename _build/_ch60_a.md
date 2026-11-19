### 60-01 为什么读源码：方法与路线

**知识点**
- **读源码的目的不是"背实现"，是获得三种能力**：① **排障能力**（框架报错时知道去哪个类、哪个方法、哪个配置项）；② **正确使用能力**（知道框架的边界与代价，避免误用）；③ **设计借鉴能力**（把框架的结构搬到自己的代码里——49/31 章的模式在真实项目中的样本）。
- **读源码的正确姿势：从"问题"出发，不从"第一行"出发**——带着具体问题（"@Transactional 为什么没生效"）沿调用链定位，比从 `main` 往下读高效十倍。**通读框架源码是少数人的选择，按需精读是所有人的能力**。
- **三条切入路径**：① **入口法**（找框架的启动入口：`SpringApplication.run`、`ServerBootstrap.bind`，顺主流程走）；② **断点法**（IDE 里在关键 API 下断点，看调用栈）；③ **文档法**（先读官方"生命周期/扩展点"文档建立地图，再看代码填充）。
- **精读的产出必须落地**：一张调用链图、一段验证代码、一条团队文档（"这个坑以后注意"）——**只读不产出等于没读**（52 章输出倒逼输入）。
- **本章选读标准**：Spring（IOC/AOP/事务）、Netty（线程模型/ByteBuf）、Tomcat（连接器/容器）、MyBatis（SqlSession/执行器）、SLF4J（桥接/异步）——**覆盖"业务开发最高频依赖"的核心机制**。
- 易错点：一开始就通读（迷路放弃）；只读博客不读源码（二手信息过期）；读但不验证（理解错了不自知）；读完不沉淀（下次重新读）。

**示例代码**
```java
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ReadSourceMethod {
    record Approach(String name, String how, String fit) {
    }

    static void approaches() {
        System.out.println("【三条切入路径】");
        List<Approach> list = List.of(
                new Approach("入口法", "从启动入口顺主流程：SpringApplication.run -> refresh", "建立全局地图"),
                new Approach("断点法", "关键 API 下断点，观察调用栈与参数", "定位具体行为"),
                new Approach("文档法", "先读官方生命周期/扩展点文档，再读代码", "避免迷路"));
        for (Approach a : list) {
            System.out.printf("  %-6s %-46s %s%n", a.name(), a.how(), a.fit());
        }
        System.out.println();
    }

    static void questionDriven() {
        System.out.println("【问题驱动的精读示例】");
        Map<String, String> questions = new LinkedHashMap<>();
        questions.put("@Transactional 不生效", "查代理创建时机：同类自调用不走代理（60-05）");
        questions.put("Bean 初始化顺序不对", "查 Bean 生命周期：构造 -> 属性注入 -> 初始化回调（60-03）");
        questions.put("Netty 内存泄漏", "查 ByteBuf 引用计数与释放路径（60-07）");
        questions.put("日志打不出来", "查 SLF4J 绑定与多实现冲突（60-10）");
        questions.forEach((q, path) -> System.out.printf("  问题：%-22s -> %s%n", q, path));
        System.out.println();
        System.out.println("  每个问题都对应一条具体的类/方法 —— 这才是可执行的读源码计划");
    }

    public static void main(String[] args) {
        approaches();
        questionDriven();
    }
}
```

**课后练习**
1. 列出你项目里遇到过的 5 个"框架行为不符合预期"的问题，为每个写出"该读哪个类/方法"的具体清单。
2. 选一个你天天用的框架，用入口法画出它的启动主流程（10 个步骤以内），标注你能解释的与解释不了的部分。
3. 思考题：为什么"问题驱动"比"通读"更适合绝大多数工程师？从学习效率与知识留存率两个角度分析。

---

### 60-02 Spring IOC 容器：BeanDefinition 与实例化

**知识点**
- **IOC 容器的心智模型只有三步**：**读配置 -> 建 BeanDefinition（元数据）-> 按需实例化**。`BeanDefinition` 记录"这个 Bean 的类、作用域、是否懒加载、构造参数、属性依赖、初始化方法"——**它不是 Bean 实例，是"造 Bean 的图纸"**。
- **容器启动的主流程（`refresh` 方法）**：加载配置（XML/注解/JavaConfig）-> 注册 BeanDefinition -> 执行 `BeanFactoryPostProcessor`（可在实例化前改图纸）-> 注册 `BeanPostProcessor`（实例化前后回调）-> 实例化非懒加载单例 -> 发布容器就绪事件。
- **`BeanFactory` vs `ApplicationContext`**：前者是"最小容器"（按需 `getBean` 才实例化）；后者是"完整容器"（继承 `BeanFactory` + 事件/国际化/资源加载 + **预实例化单例**）——**日常用的是 `ApplicationContext`，它的启动就把单例建好了**。
- **BeanDefinition 的可编程修改**：`BeanFactoryPostProcessor` 在"图纸阶段"动手（改属性/换类/加依赖），`BeanPostProcessor` 在"实例阶段"动手（代理包装/属性填充）——**AOP 正是 BeanPostProcessor 实现的**（60-04）。
- **作用域（Scope）**：`singleton`（容器内单例，默认）、`prototype`（每次 `getBean` 新建）、`request`/`session`（Web 环境）——**prototype Bean 的依赖注入到单例里会"注入一次不再更新"**（经典坑）。
- 易错点：以为 `getBean` 每次都创建（单例只建一次）；混淆 BeanFactoryPostProcessor 与 BeanPostProcessor 的执行时机；prototype 注入单例的"失效"问题；同名 Bean 覆盖（默认允许，容易掩盖配置错误）。

**示例代码**
```java
import java.lang.reflect.Constructor;
import java.util.LinkedHashMap;
import java.util.Map;

public class IocContainerDemo {
    /** BeanDefinition：图纸 */
    record BeanDefinition(String name, Class<?> type, String scope, boolean lazy) {
    }

    /** 极简容器：注册图纸 -> 按需实例化 -> 单例缓存 */
    static class MiniContainer {
        final Map<String, BeanDefinition> definitions = new LinkedHashMap<>();
        final Map<String, Object> singletons = new LinkedHashMap<>();

        void register(BeanDefinition def) {
            definitions.put(def.name(), def);
            System.out.printf("  [注册图纸] %s (%s, scope=%s)%n",
                    def.name(), def.type().getSimpleName(), def.scope());
        }

        /** 预实例化单例（对应 ApplicationContext 刷新末段） */
        void preInstantiate() throws Exception {
            for (BeanDefinition def : definitions.values()) {
                if (!def.lazy() && def.scope().equals("singleton")) {
                    getBean(def.name());
                }
            }
        }

        Object getBean(String name) throws Exception {
            BeanDefinition def = definitions.get(name);
            if (def == null) {
                throw new IllegalArgumentException("No bean named " + name);
            }
            if (def.scope().equals("singleton")) {
                Object cached = singletons.get(name);
                if (cached != null) {
                    return cached;
                }
                Object created = instantiate(def);
                singletons.put(name, created);
                System.out.printf("  [实例化] %s -> %s（单例，已缓存）%n",
                        name, created.getClass().getSimpleName());
                return created;
            }
            Object created = instantiate(def);
            System.out.printf("  [实例化] %s -> 新实例（prototype，不缓存）%n", name);
            return created;
        }

        Object instantiate(BeanDefinition def) throws Exception {
            Constructor<?> ctor = def.type().getDeclaredConstructor();
            ctor.setAccessible(true);
            return ctor.newInstance();
        }
    }

    static class OrderService {
        public OrderService() {
        }

        public String describe() {
            return "OrderService";
        }
    }

    static class RequestContext {
        public RequestContext() {
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("【容器启动：注册图纸 -> 预实例化单例】");
        var container = new MiniContainer();
        container.register(new BeanDefinition("orderService", OrderService.class, "singleton", false));
        container.register(new BeanDefinition("requestCtx", RequestContext.class, "prototype", true));
        container.register(new BeanDefinition("lazyBean", OrderService.class, "singleton", true));
        container.preInstantiate();

        System.out.println();
        System.out.println("【按需获取】");
        Object a = container.getBean("orderService");
        Object b = container.getBean("orderService");
        System.out.println("  两次 getBean 是同一实例：" + (a == b));
        Object c = container.getBean("requestCtx");
        Object d = container.getBean("requestCtx");
        System.out.println("  prototype 两次不同：" + (c != d));
        System.out.println("  懒加载 Bean 首次获取才创建：" + container.getBean("lazyBean").getClass().getSimpleName());
    }
}
```

**课后练习**
1. 扩展 MiniContainer：支持构造器注入（按类型查找依赖）、懒加载、`BeanFactoryPostProcessor` 风格的"图纸后处理"。
2. 用断点在 `AbstractApplicationContext.refresh()` 里走一遍，记录 12 个关键方法的顺序，与本章主流程对照。
3. 思考题：为什么"prototype Bean 注入到 singleton"会失效？从"注入发生在单例创建时、只发生一次"的时机分析。

---

### 60-03 Spring Bean 生命周期与循环依赖

**知识点**
- **Bean 的生命周期七步**：实例化（构造器）-> 属性填充（依赖注入）-> `Aware` 回调（BeanName/BeanFactory）-> `BeanPostProcessor.postProcessBeforeInitialization` -> 初始化（`@PostConstruct` -> `InitializingBean.afterPropertiesSet` -> `init-method`）-> `BeanPostProcessor.postProcessAfterInitialization`（**AOP 代理在这里生成**）-> 使用 -> 销毁（`@PreDestroy` -> `DisposableBean.destroy`）。
- **循环依赖的三级缓存**：`singletonObjects`（成品）、`earlySingletonObjects`（半成品提前暴露）、`singletonFactories`（ObjectFactory 占位）——**A 依赖 B、B 依赖 A 时，A 先暴露"半成品引用"，B 拿到它完成创建，A 再完成**。**这就是 setter/字段注入能解决循环依赖、构造器注入不能的原因**（构造器阶段对象还没创建）。
- **三级缓存存的是 ObjectFactory 而非对象**：为了让**提前暴露的引用可能被 AOP 代理替换**（若 A 需要代理，B 拿到的是代理而不是原始对象）——**这是"为什么需要第三级"的核心答案**（面试高频，也是 60-04 的伏笔）。
- **`@Lazy` 是循环依赖的另一种解法**：注入代理占位，真正用时才解析——**但它把问题推迟到运行期**（首次调用时才可能报错），不如重构依赖关系。
- **循环依赖的正确解法是"重构"**：抽公共依赖、改事件驱动（49-03）、上移职责——**三级缓存是框架的兜底，不是设计的目标**。
- 易错点：用构造器注入又出现循环依赖（Spring 无法解决，直接报错）；`@Async`/AOP 代理与循环依赖叠加（拿到代理还是原始对象的差异）；把三级缓存当"性能优化"（它是兼容性兜底）；单例 Bean 里持有 prototype 依赖（60-02 的坑）。

**示例代码**
```java
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

public class BeanLifecycle {
    /** 三级缓存模拟 */
    static class Container {
        final Map<String, Object> singletonObjects = new HashMap<>();      // 成品
        final Map<String, Object> earlySingletonObjects = new HashMap<>(); // 半成品
        final Map<String, Supplier<Object>> singletonFactories = new HashMap<>(); // 工厂

        final Map<String, Supplier<Object>> creators = new LinkedHashMap<>();

        void register(String name, Supplier<Object> creator) {
            creators.put(name, creator);
        }

        Object getBean(String name) {
            Object ready = singletonObjects.get(name);
            if (ready != null) {
                return ready;
            }
            Object early = earlySingletonObjects.get(name);
            if (early != null) {
                System.out.println("    命中二级缓存（半成品）：" + name);
                return early;
            }
            Supplier<Object> factory = singletonFactories.get(name);
            if (factory != null) {
                Object o = factory.get();
                earlySingletonObjects.put(name, o);
                System.out.println("    命中三级缓存（工厂生成半成品）：" + name);
                return o;
            }
            return create(name);
        }

        Object create(String name) {
            System.out.println("    创建 " + name + "：实例化 -> 暴露半成品工厂 -> 属性填充");
            // 1) 实例化
            Object instance = new Object() {
                @Override
                public String toString() {
                    return name;
                }
            };
            // 2) 提前暴露（三级缓存）
            singletonFactories.put(name, () -> instance);
            // 3) 属性填充（可能触发依赖的创建）
            wiring(name);
            // 4) 初始化完成后移入一级缓存
            singletonObjects.put(name, instance);
            earlySingletonObjects.remove(name);
            singletonFactories.remove(name);
            System.out.println("    完成 " + name + "：移入一级缓存（成品）");
            return instance;
        }

        void wiring(String name) {
            if (name.equals("serviceA")) {
                System.out.println("      serviceA 依赖 serviceB -> 触发 getBean(serviceB)");
                getBean("serviceB");
            } else if (name.equals("serviceB")) {
                System.out.println("      serviceB 依赖 serviceA -> 触发 getBean(serviceA)");
                getBean("serviceA");
            }
        }
    }

    public static void main(String[] args) {
        var c = new Container();
        c.register("serviceA", Object::new);
        c.register("serviceB", Object::new);
        System.out.println("【循环依赖解析：A <-> B】");
        Object a = c.getBean("serviceA");
        System.out.println("  A 获取成功：" + a);
        System.out.println("  一级缓存键：" + c.singletonObjects.keySet());
        System.out.println();
        System.out.println("  关键：B 拿到的 A 是\"提前暴露的半成品\"，A 完成初始化后回收缓存");
    }
}
```

**课后练习**
1. 扩展容器模拟：支持"构造器注入"并在循环依赖时抛异常，对比 setter 注入的差异，解释原因。
2. 在真实 Spring 应用里用一个 `BeanPostProcessor` 打印所有 Bean 的创建顺序与初始化耗时，输出"启动耗时 Top5 Bean"。
3. 思考题：为什么三级缓存中存的是 `ObjectFactory` 而不是对象？从"AOP 代理可能替换引用"的角度推导。

---

### 60-04 Spring AOP：代理创建与拦截链

**知识点**
- **AOP 的实现路径只有两条**：**JDK 动态代理**（基于接口，`Proxy.newProxyInstance`，50-08/26-01 已实战）与 **CGLIB**（基于继承，生成子类字节码，可代理无接口类）——**Spring 默认：有接口用 JDK 代理，无接口用 CGLIB**（Spring Boot 2.x 起默认强制 CGLIB，`proxyTargetClass=true`）。
- **代理生成的时机**：`BeanPostProcessor.postProcessAfterInitialization`（60-03 第六步）——**容器在 Bean 初始化完成后判断"是否需要被切面拦截"，需要则返回代理对象替换原对象**。这就是"注入的其实是代理"的原因。
- **拦截链（MethodInterceptor 链）**：多个切面按 `@Order` 排序组成责任链（26-01 已实战），**前置逻辑正序、后置逆序**——与 26-01 的 AOP 链完全一致，本章补的是"链是怎么织进容器的"。
- **切点（Pointcut）匹配是性能关键**：每个方法调用都要判断是否命中切点——**命中判断用缓存**（Spring 缓存切点匹配结果），但**切点表达式写得宽（`execution(* com..*(..))`）会拖慢全应用**。
- **AOP 的边界**：只拦截**外部通过代理的调用**——**同类内部方法调用（`this.method()`）不经过代理，切面失效**（60-05 事务失效的第一大原因）；`private/final` 方法无法被 CGLIB 代理。
- 易错点：同类自调用导致切面失效（AOP 与事务的共同坑）；`final` 类/方法用 CGLIB（无法继承）；切点过宽（性能与可读性双降）；以为注解存在就一定生效（需被代理）。

**示例代码**
```java
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

public class AopProxyChain {
    interface OrderService {
        String place(String orderId);
    }

    static class OrderServiceImpl implements OrderService {
        public String place(String orderId) {
            System.out.println("    [目标方法] place(" + orderId + ")");
            return "placed:" + orderId;
        }
    }

    record Advice(String name, int order, String before, String after) {
    }

    /** 责任链式代理：前置正序、后置逆序（26-01 的容器接入版） */
    static OrderService wrap(OrderService target, List<Advice> advices) {
        List<Advice> sorted = new ArrayList<>(advices);
        sorted.sort((a, b) -> Integer.compare(a.order(), b.order()));
        return (OrderService) Proxy.newProxyInstance(
                OrderService.class.getClassLoader(),
                new Class<?>[]{OrderService.class},
                (InvocationHandler) (proxy, method, args) -> {
                    List<Advice> entered = new ArrayList<>();
                    for (Advice a : sorted) {
                        System.out.println("    [前置] " + a.before());
                        entered.add(a);
                    }
                    Object result;
                    try {
                        result = method.invoke(target, args);
                    } finally {
                        for (int i = entered.size() - 1; i >= 0; i--) {
                            System.out.println("    [后置] " + entered.get(i).after());
                        }
                    }
                    return result;
                });
    }

    public static void main(String[] args) {
        List<Advice> advices = List.of(
                new Advice("transaction", 1, "开启事务", "提交事务"),
                new Advice("logging", 2, "记录入参", "记录耗时"),
                new Advice("metrics", 3, "开始计时", "上报指标"));
        System.out.println("【切面顺序（@Order 1/2/3）与责任链执行】");
        OrderService proxy = wrap(new OrderServiceImpl(), advices);
        String r = proxy.place("9001");
        System.out.println("  返回：" + r);
        System.out.println();
        System.out.println("  注意：前置正序（transaction -> logging -> metrics），后置逆序");
        System.out.println("  若用 this.place() 内部调用 -> 不经过代理 -> 三个切面全部失效");
    }
}
```

**课后练习**
1. 实现"切点匹配器"：为方法匹配 `execution(* service.*Service.*(..))` 这类表达式，用 20 个方法验证命中结果。
2. 在同一 Spring 应用里同时用 JDK 代理与 CGLIB 代理各实现一个切面，用 `AopUtils` 打印代理类型，解释默认策略。
3. 思考题：为什么"同类自调用切面失效"是 AOP 的结构性限制而非 bug？从"代理只能拦截经过自己的调用"的机制推导。

---

### 60-05 Spring 事务：代理与传播行为

**知识点**
- **声明式事务本质是 AOP**：`@Transactional` 由 `BeanPostProcessor` 生成代理，代理在方法前后**开启/提交/回滚事务**——**它和其他切面一样受"自调用失效"限制**（60-04 的结论直接适用）。**事务失效的第一大原因就是自调用**。
- **七个传播行为（Propagation）的语义**：`REQUIRED`（默认：有事务加入，无则新建）、`REQUIRES_NEW`（挂起当前，新建独立事务）、`NESTED`（嵌套，用 savepoint）、`SUPPORTS`（有则加入，无则以非事务执行）、`NOT_SUPPORTED`（挂起事务，非事务执行）、`MANDATORY`（必须在事务中）、`NEVER`（必须无事务）——**最常用 REQUIRED 与 REQUIRES_NEW，混用是事故源**。
- **回滚规则的默认值**：**只对 `RuntimeException` 与 `Error` 回滚，受检异常不回滚**——`@Transactional(rollbackFor = Exception.class)` 是必须显式声明的（36 章"异常吞掉"的代价在事务上表现为"提交了半个业务"）。
- **事务失效的五大原因**：自调用、方法非 public（代理无法拦截）、异常被 catch 未抛出、类未被 Spring 管理、传播行为配错——**每一个都对应一条排障路径**（60-01 的问题驱动法）。
- **事务与数据库隔离级别的分工**：Spring 的传播行为管"事务边界"，数据库的隔离级别管"并发可见性"（20-02）——**两层概念不能混**。
- 易错点：自调用导致事务失效（把方法移到另一个 Bean 或用 `AopContext.currentProxy()`）；受检异常被吞（未声明 rollbackFor）；`REQUIRES_NEW` 用在循环里（连接池耗尽）；大事务里做远程调用（60-05 的经典反模式：事务持有时间被网络 IO 拉长）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.List;

public class TransactionDemo {
    /** 简化的"事务管理器"：记录事务边界与回滚决策 */
    static class TxManager {
        final List<String> log = new ArrayList<>();
        int depth = 0;

        void begin(String name, String propagation) {
            if (propagation.equals("REQUIRED")) {
                if (depth == 0) {
                    log.add("BEGIN " + name);
                } else {
                    log.add("JOIN  " + name + "（加入当前事务）");
                }
                depth++;
            } else if (propagation.equals("REQUIRES_NEW")) {
                log.add("SUSPEND 当前事务，BEGIN " + name + "（独立事务）");
                depth++;
            }
        }

        void commit(String name) {
            depth--;
            log.add("COMMIT " + name);
        }

        void rollback(String name, String reason) {
            depth--;
            log.add("ROLLBACK " + name + "（" + reason + "）");
        }
    }

    /** 模拟：外层 REQUIRED，内层 REQUIRES_NEW，内层失败 */
    static void simulate() {
        var tx = new TxManager();
        tx.begin("outer.placeOrder", "REQUIRED");
        System.out.println("  执行：写订单");
        tx.begin("inner.logAudit", "REQUIRES_NEW");
        System.out.println("  执行：写审计（独立事务）");
        tx.rollback("inner.logAudit", "受检异常未声明 rollbackFor");
        System.out.println("  外层继续（独立事务回滚不影响外层）");
        tx.commit("outer.placeOrder");
        System.out.println();
        System.out.println("【事务日志】");
        tx.log.forEach(l -> System.out.println("  " + l));
        System.out.println();
    }

    /** 回滚规则演示：受检异常默认不回滚 */
    static void rollbackRule() {
        record Case(String exceptionType, boolean checked, boolean rollbackForDeclared,
                    String outcome) {
        }
        List<Case> cases = List.of(
                new Case("RuntimeException", false, false, "回滚（默认规则）"),
                new Case("IOException（受检）", true, false, "提交！（默认不回滚，高危）"),
                new Case("IOException（受检）", true, true, "回滚（声明了 rollbackFor）"));
        System.out.println("【回滚规则】");
        for (Case c : cases) {
            System.out.printf("  %-22s 受检=%-5s 声明rollbackFor=%-5s -> %s%n",
                    c.exceptionType(), c.checked(), c.rollbackForDeclared(), c.outcome());
        }
    }

    public static void main(String[] args) {
        simulate();
        rollbackRule();
        System.out.println();
        System.out.println("  铁律：@Transactional(rollbackFor = Exception.class) 应成为默认写法");
    }
}
```

**课后练习**
1. 实现传播行为模拟器：支持 REQUIRED/REQUIRES_NEW/NESTED 三种，用"外层成功+内层失败"与"外层失败+内层成功"两组场景输出事务日志。
2. 在真实 Spring 应用里复现"自调用导致事务失效"：同 Bean 内方法 A 调 B（B 有 @Transactional），验证 B 的事务未生效，然后用抽 Bean 或 `AopContext.currentProxy()` 修复。
3. 思考题：为什么"大事务里做远程调用"是反模式？从事务持有数据库连接与锁的时长分析（并给出拆分方案）。

---

### 60-06 Netty：主从 Reactor 线程模型

**知识点**
- **Netty 的线程模型是"主从 Reactor 多线程"**：**BossGroup**（1~N 个线程）只负责 `accept` 新连接，把连接注册到 **WorkerGroup**（默认 CPU 核数 × 2）里的某个 EventLoop；**每个 EventLoop 是一个单线程事件循环，绑定多个 Channel**——**一个 Channel 的所有 IO 事件都在同一个 EventLoop 线程上处理**，天然无锁。
- **单线程事件循环的意义**：Channel 的状态（读缓冲/写队列/编解码器状态）**只被一个线程访问**，不需要加锁——**这就是 Netty 高性能的根源**（50-03 事件循环思想的网络版）。代价：**任何阻塞操作都会卡住该 EventLoop 上的所有 Channel**。
- **"不要在 ChannelHandler 里做耗时操作"是 Netty 第一铁律**：耗时业务要交给业务线程池（`DefaultEventExecutorGroup` 或自己的线程池）；阻塞（JDBC/同步 HTTP）同理——**否则一个慢请求拖垮整条连接组**。
- **写操作是异步的**：`channel.writeAndFlush(msg)` 返回 `ChannelFuture`，**实际写入由 EventLoop 异步完成**；写缓冲（ChannelOutboundBuffer）满时会"写不进去"（`isWritable()==false`），需要背压处理（43-08 同源的 IO 背压）。
- **与 47-02 Sidecar / 50-02 事件循环的对照**：Netty 的 EventLoop 就是"少量线程 + 非阻塞 + 事件驱动"的经典实现；**Reactor 模式（26-01 的 AOP 链、50-03 的 WebFlux）在 IO 层的原型就是它**。
- 易错点：在 handler 里做阻塞调用（全连接组卡死）；以为 `writeAndFlush` 是同步的（返回 Future）；`ChannelHandler` 被多连接共享（状态错乱，需要 `@Sharable` 才可共享）；业务线程池与 EventLoop 数量配比失衡。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class NettyReactorDemo {
    record Event(String channelId, String type, long durationMs) {
    }

    /** 单线程 EventLoop：串行处理绑定到它的所有 Channel 事件 */
    static class EventLoop implements Runnable {
        final String name;
        final List<Event> queue = new ArrayList<>();
        final List<String> log = new ArrayList<>();

        EventLoop(String name) {
            this.name = name;
        }

        void submit(Event e) {
            queue.add(e);
        }

        public void run() {
            for (Event e : queue) {
                log.add(name + " 处理 " + e.channelId() + " 的 " + e.type()
                        + "（" + e.durationMs() + "ms）");
            }
        }
    }

    /** 主从 Reactor：Boss 分发连接，Worker 处理 IO */
    static void run(int workers) {
        List<EventLoop> workerLoops = new ArrayList<>();
        for (int i = 0; i < workers; i++) {
            workerLoops.add(new EventLoop("worker-" + i));
        }
        // 4 个连接按 hash 绑定到不同 worker
        Map<String, String> assignment = new LinkedHashMap<>();
        for (int i = 0; i < 4; i++) {
            String channel = "conn-" + i;
            int idx = Math.abs(channel.hashCode()) % workers;
            assignment.put(channel, "worker-" + idx);
            workerLoops.get(idx).submit(new Event("conn-" + i, "READ", 5 + i));
        }
        System.out.println("【连接分配（一个 Channel 绑定一个 EventLoop）】" + assignment);
        System.out.println("【并行处理】");
        workerLoops.forEach(EventLoop::run);
        workerLoops.forEach(l -> l.log.forEach(s -> System.out.println("  " + s)));
        System.out.println();
    }

    /** 阻塞操作的代价：同一 EventLoop 上的其他连接被拖住 */
    static void blockingImpact() {
        EventLoop loop = new EventLoop("worker-0");
        loop.submit(new Event("conn-A", "READ", 5));
        loop.submit(new Event("conn-B", "BUSINESS(阻塞 200ms)", 200));
        loop.submit(new Event("conn-C", "READ", 5));
        loop.run();
        System.out.println("【阻塞操作的影响】");
        loop.log.forEach(s -> System.out.println("  " + s));
        System.out.println("  conn-C 的等待 = conn-A + conn-B 的耗时（B 阻塞了整条事件循环）");
        System.out.println("  正解：耗时业务交给业务线程池，EventLoop 只做 IO");
    }

    public static void main(String[] args) {
        run(2);
        blockingImpact();
    }
}
```

**课后练习**
1. 实现完整主从 Reactor 模拟：Boss 接收连接 -> 按 hash 分发 Worker -> 每个 Worker 单线程轮询，统计各连接的等待时间。
2. 在模拟器里加入"业务线程池"：把耗时事件移出 EventLoop，对比"移出前/后"其他连接的最大等待时间。
3. 思考题：为什么"一个 Channel 固定绑定一个 EventLoop"能免锁？从状态访问的线程唯一性推导（并说明这个设计的代价）。

---

### 60-07 Netty：ByteBuf 与零拷贝

**知识点**
- **ByteBuf 是 Netty 的数据容器**：**读写双指针**（`readerIndex`/`writerIndex`），读不移动数据、写不覆盖未读数据——**对比 JDK ByteBuffer 的单指针**（读要 flip）；动态扩容、池化复用（`PooledByteBufAllocator` 是默认，减少 GC）。
- **引用计数（Reference Counting）**：ByteBuf 用 `retain()/release()` 管理生命周期，**归零即归还池**——**忘记 release = 内存泄漏**（Netty 最经典的坑）；`SimpleChannelInboundHandler` 会自动释放，`ChannelInboundHandlerAdapter` 需要手动。
- **Netty 的"零拷贝"是三个不同层次的东西**：① **堆外内存**（DirectBuffer，避免 JVM 堆与内核缓冲区之间的一次拷贝）；② **`CompositeByteBuf`**（多个 buffer 逻辑拼接，不物理复制）；③ **`FileRegion`/`transferTo`**（发送文件时内核态直接拷贝，不经用户态）——**面试常问的"零拷贝"多指②③，而不是"完全没有拷贝"**。
- **池化的收益与风险**：池化（复用 buffer）降低 GC 与分配开销，但**泄漏的代价更大**（池被占满后分配阻塞/失败）；Netty 提供 `ResourceLeakDetector`（`-Dio.netty.leakDetection.level=paranoid`）检测泄漏。
- **编解码（Codec）**：`LengthFieldBasedFrameDecoder` 处理**粘包半包**（18-01 的长度头协议正是它的手工版）；`StringDecoder/StringEncoder` 做文本转换——**编解码器也是有状态的，不能随意 `@Sharable`**。
- 易错点：忘记 `release()`（泄漏检测日志刷屏）；把 ByteBuf 交给其他线程使用（线程不安全）；用 `ByteBuf.array()`（堆外缓冲没有 array，抛异常）；以为写入一定立即发出（60-06 的异步写）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.List;

public class ByteBufDemo {
    /** 极简 ByteBuf：双指针 + 引用计数 */
    static class MiniByteBuf {
        final byte[] data = new byte[64];
        int readerIndex = 0;
        int writerIndex = 0;
        int refCnt = 1;
        final String name;

        MiniByteBuf(String name) {
            this.name = name;
        }

        void writeBytes(byte[] bytes) {
            System.arraycopy(bytes, 0, data, writerIndex, bytes.length);
            writerIndex += bytes.length;
        }

        byte[] readBytes(int len) {
            byte[] out = new byte[len];
            System.arraycopy(data, readerIndex, out, 0, len);
            readerIndex += len;
            return out;
        }

        int readableBytes() {
            return writerIndex - readerIndex;
        }

        void retain() {
            refCnt++;
            System.out.println("    " + name + " retain -> refCnt=" + refCnt);
        }

        boolean release() {
            refCnt--;
            System.out.println("    " + name + " release -> refCnt=" + refCnt);
            if (refCnt == 0) {
                System.out.println("    " + name + " 归还池（可复用）");
                return true;
            }
            return false;
        }
    }

    /** 粘包/半包：长度头协议的解码（18-01 的 Netty 实现版） */
    static void decodeByLengthField(List<String> frames, String wire) {
        System.out.println("  线上字节流（含长度头）：" + wire);
        int i = 0;
        while (i + 4 <= wire.length()) {
            int len = Integer.parseInt(wire.substring(i, i + 4));
            i += 4;
            if (i + len > wire.length()) {
                System.out.println("  剩余 " + (wire.length() - i) + " 字节不足一帧 -> 等待更多数据（半包）");
                break;
            }
            frames.add(wire.substring(i, i + len));
            i += len;
        }
        System.out.println("  解析出的帧：" + frames);
    }

    public static void main(String[] args) {
        var buf = new MiniByteBuf("buf-1");
        buf.writeBytes("HELLO".getBytes());
        System.out.println("  写入后 writer=" + buf.writerIndex + " readable=" + buf.readableBytes());
        byte[] read = buf.readBytes(2);
        System.out.println("  读取 2 字节：" + new String(read) + "，剩余 readable=" + buf.readableBytes());
        System.out.println("  注意：读不移动数据，只挪 readerIndex（省去 flip）");
        System.out.println();
        buf.retain();
        buf.release();
        buf.release();
        System.out.println();
        List<String> frames = new ArrayList<>();
        System.out.println("【长度头解码（粘包/半包）】");
        decodeByLengthField(frames, "0005HELLO0005WORLD0003AB");
    }
}
```

**课后练习**
1. 扩展 MiniByteBuf：实现 `slice()`（共享底层数组的视图）与 `composite(ByteBuf...)`（逻辑拼接不复制），验证二者零拷贝语义。
2. 实现"引用计数泄漏检测"：每次分配记录，作业结束对比"分配数 - 释放数"，非零即泄漏并打印分配点。
3. 思考题：为什么"池化 buffer 的泄漏"比"不池化"更严重？从池容量耗尽后的分配阻塞与故障放大分析。

---

### 60-08 Tomcat：连接器与容器

**知识点**
- **Tomcat 的两大组件：连接器（Connector）与容器（Container）**：连接器负责**接收连接、解析 HTTP、生成 Request/Response**；容器负责**按 URL 找到 Servlet 并调用**（Engine -> Host -> Context -> Wrapper 四层）。
- **连接器的核心结构**：`Acceptor`（接收连接）-> `Poller`（NIO 多路复用）-> `Worker`（处理请求）——**NIO 模型下少量线程扛大量连接**（60-06 的 Netty 模型同源，只是实现不同）；**BIO 模式下是"一连接一线程"**（老版本，连接数上不去）。
- **Servlet 的生命周期**：`init`（容器启动时一次）-> `service`（**每个请求，多线程并发**）-> `destroy`（容器关闭）——**Servlet 是单例多线程，`Servlet` 里的实例变量是并发共享的**（17 章的线程安全在这里是最容易翻车的地方）。
- **线程池与最大连接数**：`maxThreads`（工作线程上限）、`acceptCount`（等待队列长度）、`maxConnections`（最大连接数）——**三者配比不当的两种故障**：线程太少（请求排队慢）、队列太长（超时后才被拒绝）。
- **Spring Boot 的嵌入式 Tomcat**：`server.tomcat.*` 配置 + `TomcatServletWebServerFactory` 定制；**虚拟线程模式下（50 章）线程复用逻辑变化**——`spring.threads.virtual.enabled=true` 时请求处理走虚拟线程。
- 易错点：Servlet 里放可变实例变量（并发错乱）；`maxThreads` 调大但下游连接池没扩（线程全堵在获取连接）；`ServletContext` 当缓存用（无过期策略）；误以为 Tomcat 是"每次请求 new 一个 Servlet"。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TomcatDemo {
    record Route(String pattern, String servlet) {
    }

    /** 容器映射：URL -> Servlet（精确 > 路径 > 扩展名 > 默认，简化版） */
    static String resolve(List<Route> routes, String path) {
        for (Route r : routes) {
            if (r.pattern().equals(path)) {
                return r.servlet() + "（精确匹配）";
            }
        }
        for (Route r : routes) {
            if (path.startsWith(r.pattern())) {
                return r.servlet() + "（路径匹配：" + r.pattern() + "）";
            }
        }
        return "DefaultServlet（静态资源/404）";
    }

    /** 线程池三参数的影响：模拟排队与拒绝 */
    static void threadPool(int maxThreads, int acceptCount, int requests, long serviceMs) {
        int running = Math.min(maxThreads, requests);
        int queued = Math.min(acceptCount, Math.max(0, requests - maxThreads));
        int rejected = Math.max(0, requests - maxThreads - acceptCount);
        double totalMs = Math.ceil((double) requests / maxThreads) * serviceMs;
        System.out.printf("  maxThreads=%d acceptCount=%d 请求=%d 单请求=%dms%n",
                maxThreads, acceptCount, requests, serviceMs);
        System.out.printf("    并发处理=%d 排队=%d 拒绝=%d 总耗时≈%.0fms%n",
                running, queued, rejected, totalMs);
        if (rejected > 0) {
            System.out.println("    结论：拒绝 " + rejected + " 个 -> 用户看到 503（队列不足）");
        }
        if (maxThreads * serviceMs / 1000.0 > 2 && requests > maxThreads) {
            System.out.println("    结论：线程不足 -> 排队时间过长（扩容或异步化）");
        }
    }

    /** Servlet 生命周期与并发共享状态 */
    static void servletLifecycle() {
        System.out.println("【Servlet 生命周期】init（一次）-> service（每请求，多线程）-> destroy（一次）");
        Map<String, String> state = new LinkedHashMap<>();
        state.put("实例变量 counter", "并发共享！必须用 AtomicInteger");
        state.put("局部变量", "每请求独立，安全");
        state.put("成员 service 引用", "无状态，安全");
        state.forEach((k, v) -> System.out.printf("  %-16s %s%n", k, v));
    }

    public static void main(String[] args) {
        List<Route> routes = List.of(
                new Route("/api/order", "OrderServlet"),
                new Route("/api/", "ApiDispatchServlet"),
                new Route("*.do", "LegacyServlet"));
        System.out.println("【URL 路由解析】");
        for (String p : new String[]{"/api/order", "/api/user/1", "/static/a.js"}) {
            System.out.printf("  %-16s -> %s%n", p, resolve(routes, p));
        }
        System.out.println();
        System.out.println("【线程池参数组合】");
        threadPool(200, 100, 500, 50);
        System.out.println();
        threadPool(50, 20, 500, 50);
        System.out.println();
        servletLifecycle();
    }
}
```

**课后练习**
1. 实现 Servlet 路由匹配器（精确/路径/扩展名/默认四级），用 10 个 URL 验证优先级顺序。
2. 实现"请求排队模拟器"：给定 maxThreads/acceptCount/到达速率/服务时长，输出平均排队时间、拒绝率与 P99（用队列论近似）。
3. 思考题：为什么 `maxThreads` 调大不一定提升吞吐？从"下游连接池/CPU/DB"三个瓶颈的联动分析。

---

### 60-09 MyBatis：SqlSession 与执行器

**知识点**
- **MyBatis 的三层结构**：**SqlSessionFactory**（全局单例，持有 Configuration）-> **SqlSession**（一次会话/一个事务，**非线程安全**）-> **Executor**（真正执行 SQL，含缓存与批处理）。**Mapper 接口只是 SqlSession 的代理入口**（23-01 的代理在此得到源码层解释）。
- **Executor 的三种类型**：`SIMPLE`（默认，每次新建 Statement）、`REUSE`（复用 Statement）、`BATCH`（批量执行，**事务提交时统一 flush**）——**批量插入必须用 BATCH**（否则一条一条发，21-02 的性能教训在 ORM 层）。
- **一级缓存 vs 二级缓存**：一级缓存（SqlSession 级，**默认开启**，同一会话内相同查询复用）——**在事务/会话内数据可能被其他会话改掉而缓存不知**（脏读风险）；二级缓存（Mapper/namespace 级，**需显式开启**，跨会话共享）——**多表关联/写操作时容易不一致**，生产普遍关闭。
- **参数绑定与 `#{}` vs `${}`**：`#{}` 走 `PreparedStatement` 占位符（**防注入**，36-02 的源码层印证）；`${}` 是字符串替换（**注入风险**）——**`${}` 只允许用于表名/列名等标识符，且必须白名单**（39-02 的标识符纪律）。
- **插件（Interceptor）机制**：MyBatis 用动态代理拦截 `Executor/StatementHandler/ParameterHandler/ResultSetHandler` 四大组件——**分页插件、SQL 审计、多租户注入（55-03）都靠它**；**插件链的顺序决定行为**（多个插件叠加时注意）。
- 易错点：SqlSession 跨线程复用（线程不安全）；用 `${}` 拼用户输入（注入）；BATCH 模式忘 flush（数据没写进去）；二级缓存开着又频繁跨表写（读到旧数据）。

**示例代码**
```java
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MybatisDemo {
    interface UserMapper {
        String findById(long id);

        int insert(String name);
    }

    /** 模拟 SqlSession + Executor + 一级缓存 */
    static class MiniSqlSession {
        final Map<String, String> database = new LinkedHashMap<>();
        final Map<String, String> localCache = new LinkedHashMap<>();    // 一级缓存
        final List<String> auditLog = new ArrayList<>();
        boolean batchMode;

        MiniSqlSession(boolean batchMode) {
            this.batchMode = batchMode;
            database.put("user:1", "alice");
            database.put("user:2", "bob");
        }

        String select(String key) {
            if (localCache.containsKey(key)) {
                auditLog.add("CACHE HIT " + key);
                return localCache.get(key);
            }
            auditLog.add("SQL SELECT " + key);
            String value = database.get(key);
            if (value != null) {
                localCache.put(key, value);
            }
            return value;
        }

        int insert(String key, String value) {
            auditLog.add(batchMode ? "BATCH ADD " + key : "SQL INSERT " + key);
            database.put(key, value);
            localCache.clear();                       // 写操作清空一级缓存
            return 1;
        }

        /** Mapper 代理：接口方法 -> SqlSession 调用（23-01 的源码层解释） */
        @SuppressWarnings("unchecked")
        <T> T getMapper(Class<T> mapperType) {
            return (T) Proxy.newProxyInstance(mapperType.getClassLoader(),
                    new Class<?>[]{mapperType},
                    (InvocationHandler) (proxy, method, args) -> {
                        if (method.getName().equals("findById")) {
                            return select("user:" + args[0]);
                        }
                        if (method.getName().equals("insert")) {
                            return insert("user:" + (database.size() + 1), (String) args[0]);
                        }
                        throw new UnsupportedOperationException(method.getName());
                    });
        }
    }

    public static void main(String[] args) {
        System.out.println("【Mapper 代理 + 一级缓存】");
        var session = new MiniSqlSession(false);
        UserMapper mapper = session.getMapper(UserMapper.class);
        System.out.println("  第 1 次查询：" + mapper.findById(1));
        System.out.println("  第 2 次查询：" + mapper.findById(1) + "（命中一级缓存）");
        mapper.insert("carol");
        System.out.println("  写后查询 1：" + mapper.findById(1) + "（缓存已清，重新查）");
        System.out.println("  审计日志：" + session.auditLog);
        System.out.println();

        System.out.println("【BATCH 模式：延迟执行】");
        var batchSession = new MiniSqlSession(true);
        UserMapper batchMapper = batchSession.getMapper(UserMapper.class);
        for (int i = 0; i < 3; i++) {
            batchMapper.insert("user-" + i);
        }
        System.out.println("  审计日志：" + batchSession.auditLog);
        System.out.println("  注意：BATCH 模式需事务提交时才真正 flush（未提交可能一条没写）");
        System.out.println();

        System.out.println("【#{} 与 ${} 的区别】");
        Map<String, String> modes = new LinkedHashMap<>();
        modes.put("#{id}", "PreparedStatement 占位符 -> 值永不进 SQL 结构（防注入）");
        modes.put("${id}", "字符串替换 -> 可注入！仅限白名单标识符（表名/列名）");
        modes.forEach((k, v) -> System.out.printf("  %-8s %s%n", k, v));
    }
}
```

**课后练习**
1. 扩展 MiniSqlSession：实现 `REUSE` 与 `BATCH` 两种 Executor 的执行日志差异，量化批量写入的"语句发送次数"。
2. 实现 MyBatis 插件模拟：写一个拦截器在 `select` 前后打印耗时与 SQL 次数，统计一个业务流程的 N+1 查询。
3. 思考题：为什么一级缓存在"长事务"里容易读到旧数据？从"缓存生命周期 = SqlSession 生命周期"与其余会话的写入分析。

---

### 60-10 日志框架：SLF4J 桥接与异步

**知识点**
- **日志生态的三层**：**门面（SLF4J）**——应用只依赖它；**绑定（Logback/Log4j2）**——真正实现；**桥接（jul-to-slf4j、log4j-over-slf4j）**——把老框架的调用转发到新实现。**依赖里出现多个绑定是最常见的日志故障**（SLF4J 打印 "Class path contains multiple SLF4J bindings" 并随机选一个）。
- **日志级别的语义**（42-02 的框架层解释）：ERROR（需人处理）、WARN（偏离预期自愈）、INFO（关键业务事件）、DEBUG（排查细节）、TRACE（框架内部）——**生产开 DEBUG 是性能与成本的常见事故源**。
- **异步日志（AsyncAppender）**：日志写入丢到队列由后台线程落盘——**减少业务线程的 IO 阻塞**；**代价**：队列满时的丢弃策略（丢日志 vs 阻塞业务，必须显式配置），**进程崩溃时队列里的日志丢失**（比起同步日志，"最后几条丢失"是可接受的技术取舍）。
- **参数化日志（`log.info("order={}", id)`）**：**只有级别开启时才求值参数**（对比字符串拼接"无论级别都拼接"）——**这是日志性能的第一条纪律**（高频路径上字符串拼接能吃掉可观 CPU，42-02 的日志量治理在此有性能解释）。
- **MDC（Mapped Diagnostic Context）**：线程绑定的上下文字段（trace_id/userId）自动进每条日志——**异步日志与线程池下 MDC 会丢**（需要 `TaskDecorator` 传递，50-05/55-03 的 ThreadLocal 审计同源）。
- 易错点：多个日志绑定共存（行为不确定）；`log.debug("x" + heavy())` 字符串拼接（级别关了也执行）；异步队列无界（内存涨）；MDC 在异步线程丢失（trace 断裂 42-07）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class LoggingDemo {
    enum Level {
        TRACE, DEBUG, INFO, WARN, ERROR;

        boolean enabled(Level configured) {
            return this.ordinal() >= configured.ordinal();
        }
    }

    /** 参数化日志：级别关闭时不求值 */
    static class Logger {
        final Level configured;
        final List<String> records = new ArrayList<>();
        final List<String> evaluated = new ArrayList<>();

        Logger(Level configured) {
            this.configured = configured;
        }

        void info(String template, Object... args) {
            if (!Level.INFO.enabled(configured)) {
                return;
            }
            records.add(render(template, args));
            for (Object a : args) {
                evaluated.add(String.valueOf(a));
            }
        }

        void debug(String template, Object... args) {
            if (!Level.DEBUG.enabled(configured)) {
                return;
            }
            records.add(render(template, args));
            for (Object a : args) {
                evaluated.add(String.valueOf(a));
            }
        }

        String render(String template, Object... args) {
            String out = template;
            for (Object a : args) {
                out = out.replaceFirst("\\{}", java.util.regex.Matcher.quoteReplacement(String.valueOf(a)));
            }
            return out;
        }
    }

    /** 代价昂贵的参数：用 Supplier 延迟求值 */
    static String heavyToString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            sb.append(i);
        }
        return "heavy(" + sb.length() + ")";
    }

    /** 异步日志队列：满时的丢弃策略 */
    static void asyncQueue(String policy, int capacity, int produced) {
        int queued = Math.min(capacity, produced);
        int dropped = Math.max(0, produced - capacity);
        String result = switch (policy) {
            case "drop" -> "丢弃 " + dropped + " 条（业务不阻塞）";
            case "block" -> "业务线程阻塞等待队列";
            default -> "队列无界（内存风险）";
        };
        System.out.printf("  容量=%d 产生=%d 入队=%d -> %s%n", capacity, produced, queued, result);
    }

    public static void main(String[] args) {
        System.out.println("【参数化日志：级别关闭时不求值】");
        var logger = new Logger(Level.INFO);
        logger.debug("debug with {}", heavyToString());
        logger.info("order={} amount={}", "9001", 6900);
        System.out.println("  INFO 日志：" + logger.records);
        System.out.println("  实际渲染的参数：" + logger.evaluated
                + "（debug 未渲染；注意参数表达式本身仍会被求值，彻底惰性要用 Supplier）");
        System.out.println();

        System.out.println("【异步队列策略】");
        asyncQueue("drop", 1000, 5000);
        asyncQueue("block", 1000, 5000);
        System.out.println();

        System.out.println("【常见日志故障】");
        Map<String, String> faults = new LinkedHashMap<>();
        faults.put("多个 SLF4J 绑定", "依赖里同时有 logback 与 log4j-slf4j-impl -> 随机生效");
        faults.put("字符串拼接", "log.debug(\"x\" + heavy()) -> 级别关闭也执行");
        faults.put("MDC 丢失", "线程池/异步日志 -> trace_id 断链（42-07）");
        faults.put("级别误配", "生产开 DEBUG -> 磁盘与 IO 压力（42-03）");
        faults.forEach((k, v) -> System.out.printf("  %-14s %s%n", k, v));
    }
}
```

**课后练习**
1. 用 `mvn dependency:tree` 检查一个项目的日志依赖，找出所有 SLF4J 绑定与桥接包，输出"应保留/应排除"清单。
2. 实现"日志性能对比"：同一循环里分别用字符串拼接与参数化日志（DEBUG 关闭），统计耗时差异。
3. 思考题：为什么"异步日志丢最后几条"是可接受的？从吞吐收益、崩溃场景概率、日志用途（排障 vs 审计）三个角度分析。

---

### 60-11 综合案例：一次 HTTP 请求的框架穿越

**知识点**
- **把前六课串成一条完整链路**：**Tomcat 连接器**接收请求（60-08）-> **Spring MVC DispatcherServlet** 路由 -> **事务代理**开启事务（60-05）-> **业务 Bean** 执行（60-02 的 IOC 装配 + 60-03 的生命周期已就绪）-> **AOP 切面**记录日志与指标（60-04）-> **MyBatis Mapper 代理**查库（60-09）-> **日志**全链路记录（60-10）-> **Netty**（若走 RPC 或网关转发，60-06/60-07）——**一个请求穿过了六层框架机制**。
- **"穿越"的价值是排障定位**：接口慢 -> 是 Tomcat 线程排队（60-08）、事务持有过长（60-05）、AOP 链过深（60-04）、还是 MyBatis N+1（60-09）？**知道每一层在哪、怎么观测，才能逐层排除**。
- **观测点的分布**：连接器（线程池水位/排队）、容器（URL 耗时）、事务（连接持有时间）、AOP（切面耗时）、MyBatis（SQL 耗时与次数）、日志（trace 串联）——**42 章的三支柱在这些框架层各有对应的埋点**。
- **框架能力与代价的对照**：IOC（解耦 vs 启动耗时与"魔法感"）、AOP（横切复用 vs 自调用失效与代理开销）、事务（声明式 vs 传播行为误配）、ORM（易用 vs 缓存不一致与 N+1）——**理解代价才能正确使用**（52-05 权衡思想）。
- 易错点：排障只看应用日志不看框架层指标（不知道线程池满了）；AOP 链过深（几十个切面，性能与栈深度双伤）；事务范围套住远程调用（60-05 反模式）；MyBatis N+1（循环里查库）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class FrameworkJourney {
    record Stage(String layer, String action, long costMs, String watching) {
    }

    /** 模拟一次请求穿过六层框架 */
    static List<Stage> journey(boolean slowSql, boolean deepAop) {
        List<Stage> stages = new ArrayList<>();
        stages.add(new Stage("Tomcat 连接器", "线程池分配线程 + 解析 HTTP", 2,
                "线程池水位/排队（60-08）"));
        stages.add(new Stage("DispatcherServlet", "URL 匹配 HandlerMapping", 1, "路由耗时"));
        stages.add(new Stage("AOP 代理链", "事务开启 + 日志 + 指标" + (deepAop ? "（20 个切面）" : "（3 个切面）"),
                deepAop ? 8 : 1, "切面链耗时（60-04）"));
        stages.add(new Stage("事务", "获取数据库连接", 3, "连接持有时间（60-05）"));
        stages.add(new Stage("业务 Bean", "执行领域逻辑", 5, "业务耗时（60-02）"));
        stages.add(new Stage("MyBatis", slowSql ? "SELECT（N+1，查 50 次）" : "SELECT（1 次，参数绑定）",
                slowSql ? 120 : 4, "SQL 次数与耗时（60-09）"));
        stages.add(new Stage("事务提交", "提交并释放连接", 2, "提交耗时"));
        stages.add(new Stage("AOP 后置", "记录耗时 + 上报指标", 1, "后置链（60-04）"));
        stages.add(new Stage("日志落盘", "异步队列 -> 后台写入", 1, "队列积压（60-10）"));
        return stages;
    }

    static void report(List<Stage> stages) {
        long total = stages.stream().mapToLong(Stage::costMs).sum();
        System.out.println("【请求耗时分解】总耗时 " + total + "ms");
        for (Stage s : stages) {
            double pct = total == 0 ? 0 : s.costMs() * 100.0 / total;
            System.out.printf("  %-16s %4dms %5.1f%%  %-28s 观测点：%s%n",
                    s.layer(), s.costMs(), pct, s.action(), s.watching());
        }
        String bottleneck = stages.stream()
                .max((a, b) -> Long.compare(a.costMs(), b.costMs()))
                .map(Stage::layer).orElse("-");
        System.out.println("  瓶颈层：" + bottleneck + " -> 按观测点定位再优化");
        System.out.println();
    }

    public static void main(String[] args) {
        System.out.println("【正常请求】");
        report(journey(false, false));
        System.out.println("【异常请求：N+1 + 切面过深】");
        report(journey(true, true));

        Map<String, String> lessons = new LinkedHashMap<>();
        lessons.put("IOC", "解耦的代价是启动耗时与隐式装配（60-02/03）");
        lessons.put("AOP", "复用横切逻辑的代价是自调用失效与代理开销（60-04）");
        lessons.put("事务", "声明式的代价是传播行为误配与长事务（60-05）");
        lessons.put("Netty", "免锁的代价是 EventLoop 上不能阻塞（60-06/07）");
        lessons.put("ORM", "易用的代价是缓存不一致与 N+1（60-09）");
        lessons.put("日志", "异步的代价是丢尾与 MDC 断链（60-10）");
        System.out.println("【框架能力与代价对照】");
        lessons.forEach((k, v) -> System.out.printf("  %-6s %s%n", k, v));
    }
}
```

**课后练习**
1. 在真实项目里给一次请求打全链路耗时埋点（连接器/切面/事务/SQL/日志），输出分解报告并找出瓶颈层。
2. 复现一个 N+1 查询（循环里查库），用 MyBatis 插件统计 SQL 次数，然后用 join 或批量查询优化并对比。
3. 思考题：为什么"理解框架代价"比"记住框架用法"更重要？结合本章六条"能力与代价"与 52-05 权衡框架写出你的理解。
