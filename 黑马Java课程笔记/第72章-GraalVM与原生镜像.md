# 第72章 GraalVM 与原生镜像

JVM 的两个"贵"——启动慢与内存高——在弹性与 Serverless 场景下直接决定成本和体验。AOT 原生镜像用构建期的静态分析换来毫秒启动与几十 MB 内存，代价是构建时间长、峰值性能略低、动态特性需要显式配置。本章按「为什么需要原生镜像 → AOT 与封闭世界假设 → 反射资源代理配置 → 类初始化时机 → Spring Boot 3 AOT → 调试排障 → 性能特征 → 容器与 Serverless → 构建流水线 → 迁移评估 → 迁移案例」的顺序讲。所有示例仍是纯 JDK：静态可达性分析与剪枝、三类 AOT 配置生成与运行期校验、初始化时机决策、Bean 注册代码生成、故障症状表与排障四步、预热曲线与总耗时临界点、扩容耗时对比、流水线阶段与确定性校验、四维迁移打分全部真实运行。

### 72-01 为什么需要原生镜像

**知识点**
- **JVM 的两个"贵"**：**启动慢**（类加载 + 解释执行 + JIT 预热，Spring Boot 应用常见 10~30 秒）与**内存高**（JVM 固定开销 + 元空间 + JIT 代码缓存，小服务常驻 300MB+）——**在 Serverless/弹性场景下，这两点直接决定成本与体验**（47-07 的冷启动问题）。
- **原生镜像（Native Image）的思路**：**AOT（提前编译）**——在构建期把字节码编译成机器码，产出**独立可执行文件**（不依赖 JVM），**启动毫秒级、内存几十 MB**。
- **代价也是两个**：**构建慢**（分钟级，需完整静态分析）、**峰值性能可能不如 JIT**（JIT 能在运行时根据实际热点优化，AOT 只能按静态信息优化）——**"启动快"与"峰值高"是一对取舍**。
- **适用场景的判据**：**启动时间敏感**（Serverless/FaaS、CLI 工具、短生命周期任务）、**内存受限**（边缘/嵌入式）、**扩容频繁**（弹性伸缩）——**长驻高吞吐服务未必需要**（预热后 JIT 更强）。
- 与 37/47 章的关系：容器化解决"环境一致"，原生镜像解决"启动与内存"——**两者结合是 Serverless 的最优形态**（72-08）。
- 易错点：以为原生镜像"全面更快"（峰值往往更慢）；不考虑构建时间（CI 变慢）；忽略动态特性不兼容（反射/代理 72-03）；盲目全量迁移（应先评估 72-10）。

**示例代码**
```java
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class WhyNativeImage {
    record Dimension(String name, String jvm, String nativeImage, String verdict) {
    }

    static void compare() {
        System.out.println("【JVM vs 原生镜像】");
        List<Dimension> dims = List.of(
                new Dimension("启动时间", "10~30s（Spring Boot）", "0.05~0.2s", "原生大幅胜出"),
                new Dimension("常驻内存", "200~500MB", "30~100MB", "原生胜出"),
                new Dimension("峰值吞吐", "高（JIT 优化）", "略低（静态编译）", "JVM 胜出"),
                new Dimension("构建时间", "秒级", "分钟级（静态分析）", "JVM 胜出"),
                new Dimension("动态能力", "反射/代理/动态类全支持", "需显式配置", "JVM 胜出"),
                new Dimension("可调试性", "成熟（JFR/调试器）", "受限（无 JIT 视图）", "JVM 胜出"));
        for (Dimension d : dims) {
            System.out.printf("  %-8s JVM=%-24s 原生=%-20s %s%n",
                    d.name(), d.jvm(), d.nativeImage(), d.verdict());
        }
        System.out.println();
    }

    static void fitMatrix() {
        System.out.println("【适用场景判断】");
        String[][] rows = {
                {"Serverless / FaaS", "强推荐", "冷启动决定体验与成本"},
                {"CLI 工具", "强推荐", "毫秒级启动，用户无感"},
                {"边缘/嵌入式", "推荐", "内存受限"},
                {"长驻高吞吐服务", "不推荐", "预热后 JIT 更强"},
                {"依赖大量反射的框架", "谨慎", "配置成本高（72-03）"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-20s %-8s %s%n", r[0], r[1], r[2]);
        }
        System.out.println();
    }

    static void costModel() {
        System.out.println("【成本模型：启动时间如何换算成钱】");
        long requestsPerDay = 100_000;
        double startupSec = 15;
        double nativeStartupSec = 0.1;
        long coldStartsPerDay = 500;
        double jvmSeconds = coldStartsPerDay * startupSec;
        double nativeSeconds = coldStartsPerDay * nativeStartupSec;
        System.out.printf("  日请求 %,d，日冷启动 %d 次%n", requestsPerDay, coldStartsPerDay);
        System.out.printf("  JVM 冷启动耗时 %.0f 秒/日；原生 %.0f 秒/日（省 %.0f%%）%n",
                jvmSeconds, nativeSeconds, (1 - nativeSeconds / jvmSeconds) * 100);
        System.out.println("  换算成计费时间（按秒计费）：省的启动时间 = 直接的成本节省");
        System.out.println("  更关键的是用户体验：冷启动 15s 几乎等于不可用");
        System.out.println();
    }

    public static void main(String[] args) {
        compare();
        fitMatrix();
        costModel();
        System.out.println("  结论：启动/内存敏感 -> 原生；峰值/动态能力敏感 -> JVM");
    }
}
```

**课后练习**
1. 量化你服务的启动时间与常驻内存，按"日冷启动次数"算 JVM 与原生镜像的成本差异（可用模拟参数）。
2. 为 6 类服务（FaaS/CLI/边缘/长驻/批处理/桌面）判断是否适合原生镜像，写出判据。
3. 思考题：为什么"原生镜像峰值性能可能更低"？从 JIT 的运行时热点优化与 AOT 的静态分析局限对比。

---

### 72-02 AOT 与封闭世界假设

**知识点**
- **封闭世界假设（Closed-World Assumption）是 AOT 的地基**：**构建期必须能看到"运行时会被用到的所有代码"**——不能在运行时动态加载未知类（`Class.forName` 的字符串参数、脚本引擎、动态代理生成类）。
- **AOT 的三步**：**静态分析**（从入口点（main）出发，找到所有可达的类与方法）-> **编译**（生成机器码 + 堆布局 + 元数据）-> **链接**（打包成可执行文件）。
- **被"剪掉"的代码**：不可达的方法会被删除（这是体积小的原因）——**但通过反射调用的方法在分析时"看起来不可达"**，会被误删（72-03 的配置就是解决这个）。
- **与 JIT 的对比**：JIT **运行期**看到真实调用（可内联、可去虚拟化、可逃逸分析）；AOT **构建期**只能按静态信息保守优化——**信息缺失 = 优化保守 = 峰值略低**。
- **AOT 友好的代码写法**：**显式注册反射**（72-03）、**避免动态类加载**、**用工厂/注解处理器替代反射**、**序列化框架用 AOT 友好版本**（如 Jackson 的 AOT 支持）。
- 易错点：以为"代码不用改就能 AOT"（动态特性必然要配置）；忽略类初始化时机（72-04）；用脚本引擎/JSP 等运行时编译特性（AOT 无解）；分析不完整导致运行期 NoClassDefFoundError。

**示例代码**
```java
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ClosedWorld {
    /** 模拟类图：类 -> 直接引用的类（构建期可见的静态引用） */
    static final Map<String, List<String>> staticRefs = new LinkedHashMap<>();

    static void addClass(String name, String... refs) {
        staticRefs.put(name, List.of(refs));
    }

    /** 静态可达性分析：从入口 BFS */
    static Set<String> reachable(String entry) {
        Set<String> visited = new HashSet<>();
        List<String> queue = new ArrayList<>();
        queue.add(entry);
        while (!queue.isEmpty()) {
            String cur = queue.remove(0);
            if (!visited.add(cur)) {
                continue;
            }
            for (String ref : staticRefs.getOrDefault(cur, List.of())) {
                if (!visited.contains(ref)) {
                    queue.add(ref);
                }
            }
        }
        return visited;
    }

    public static void main(String[] args) {
        addClass("Main", "OrderController", "ReflectionLoader");
        addClass("OrderController", "OrderService");
        addClass("OrderService", "OrderDao", "OrderSerializer");
        addClass("OrderDao", "DataSource");
        addClass("OrderSerializer", "OrderDto");
        addClass("ReflectionLoader");                     // 只通过反射加载
        addClass("OrderDto");
        addClass("PluginA");                              // 通过 Class.forName 加载
        addClass("PluginB");

        Set<String> reachable = reachable("Main");
        System.out.println("【静态可达类（AOT 会编译进镜像）】");
        reachable.forEach(c -> System.out.println("  " + c));

        List<String> all = new ArrayList<>(staticRefs.keySet());
        List<String> pruned = new ArrayList<>();
        for (String c : all) {
            if (!reachable.contains(c)) {
                pruned.add(c);
            }
        }
        System.out.println();
        System.out.println("【被剪掉的类（分析时不可达）】" + pruned);
        System.out.println("  风险：PluginA/PluginB 是运行时 Class.forName 加载的 -> 被误删");
        System.out.println("  解法：显式注册（72-03 的 reflection-config）");

        System.out.println();
        System.out.println("【封闭世界 vs 开放世界】");
        Map<String, String> compare = new LinkedHashMap<>();
        compare.put("AOT", "构建期确定全部代码；动态加载必须提前声明");
        compare.put("JIT", "运行期边加载边编译；Class.forName 任意字符串都能工作");
        compare.forEach((k, v) -> System.out.printf("  %-4s %s%n", k, v));

        System.out.println();
        System.out.println("【AOT 友好的写法】");
        List<String> friendly = List.of(
                "用接口 + 显式实现替代反射实例化",
                "用注解处理器在构建期生成代码（而非运行时反射）",
                "序列化框架选 AOT 支持版本（如 Jackson 的 AOT 模块）",
                "避免脚本引擎 / JSP / 运行时字节码生成");
        friendly.forEach(f -> System.out.println("  - " + f));
    }
}
```

**课后练习**
1. 实现静态可达性分析器：给定类图与入口，输出"可达类/被剪类"，构造反射加载的类被误删的场景。
2. 改造一段"用 `Class.forName` 实例化插件"的代码为"显式注册工厂"，说明为何对 AOT 友好。
3. 思考题：为什么"封闭世界假设"必然导致"反射需要配置"？从静态分析无法推断字符串参数分析。

---

### 72-03 反射、资源与代理配置

**知识点**
- **AOT 需要三类显式配置**：**反射**（`reflect-config.json`：哪些类/方法/字段会被反射调用）、**资源**（`resource-config.json`：哪些文件要打进镜像）、**动态代理**（`proxy-config.json`：运行时生成的代理接口）——**配置缺失 = 运行期 `NoSuchMethodException`/`FileNotFoundException`**。
- **反射配置的粒度**：类级（`allDeclaredMethods`/`allDeclaredFields`）与精确级（指定方法名与签名）——**粒度越细体积越小**，但维护成本越高；**实践：先开类级跑通，再逐步收细**。
- **与 66 章 Agent 的关系**：Agent 增强的是"字节码"，AOT 需要的是"可达性声明"——**两者都在处理"运行时才知道的东西"，但方向相反**（Agent 是运行时改，AOT 是构建期定）。
- **Spring Boot 3 的 AOT 处理**：框架自己生成大量 `reflect-config`（通过注解处理器扫描 `@Configuration`/`@Bean`/`@Entity`），**用户只需补"框架推断不到的部分"**（自定义反射、动态代理、资源文件）——**这就是 Spring Boot 3 支持 native 的方式**（72-05）。
- **配置的诊断**：native-image 的 `--trace-class-initialization`、`-H:+PrintAnalysisCallTree`、运行期 `-agentlib:native-image-agent`（跑一遍收集配置）——**"跑一遍收集"是最实用的方法**。
- 易错点：反射配置遗漏（运行期才报错）；资源文件没配置（`getResource` 返回 null）；动态代理没声明（`Proxy.newProxyInstance` 失败）；配置过度（全量 `allDeclared*` 导致体积膨胀）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AotConfiguration {
    record ReflectEntry(String className, List<String> methods, List<String> fields,
                        boolean allDeclared) {
        String toJson() {
            String methodsJson = methods.isEmpty() ? "[]" : methods.toString();
            return String.format(
                    "{\"name\":\"%s\",\"allDeclaredMethods\":%b,\"methods\":%s}",
                    className, allDeclared, methodsJson);
        }
    }

    record ProxyEntry(List<String> interfaces) {
        String toJson() {
            return "{\"interfaces\":" + interfaces + "}";
        }
    }

    record ResourceEntry(String pattern) {
        String toJson() {
            return "{\"pattern\":\"" + pattern + "\"}";
        }
    }

    static final List<ReflectEntry> reflectConfig = new ArrayList<>();
    static final List<ProxyEntry> proxyConfig = new ArrayList<>();
    static final List<ResourceEntry> resourceConfig = new ArrayList<>();

    static void collect() {
        reflectConfig.add(new ReflectEntry("com.heima.User", List.of("getName"), List.of(),
                false));
        reflectConfig.add(new ReflectEntry("com.heima.Order", List.of(), List.of(),
                true));
        proxyConfig.add(new ProxyEntry(List.of("com.heima.OrderService")));
        resourceConfig.add(new ResourceEntry("application.yml"));
        resourceConfig.add(new ResourceEntry("i18n/messages_zh_CN.properties"));
    }

    /** 校验：模拟运行期反射/资源访问，检查配置是否覆盖 */
    static List<String> validate(List<String> reflectUses, List<String> resourceUses,
                                 List<List<String>> proxyUses) {
        List<String> issues = new ArrayList<>();
        for (String use : reflectUses) {
            String cls = use.split("#")[0];
            String member = use.contains("#") ? use.split("#")[1] : null;
            boolean covered = reflectConfig.stream().anyMatch(e ->
                    e.className().equals(cls)
                            && (e.allDeclared() || member == null || e.methods().contains(member)));
            if (!covered) {
                issues.add("反射未配置：" + use);
            }
        }
        for (String res : resourceUses) {
            boolean covered = resourceConfig.stream()
                    .anyMatch(r -> res.startsWith(r.pattern().replace("**", ""))
                            || res.equals(r.pattern()));
            if (!covered) {
                issues.add("资源未配置：" + res);
            }
        }
        for (List<String> ifaces : proxyUses) {
            boolean covered = proxyConfig.stream()
                    .anyMatch(p -> p.interfaces().equals(ifaces));
            if (!covered) {
                issues.add("动态代理未配置：" + ifaces);
            }
        }
        return issues;
    }

    public static void main(String[] args) {
        collect();
        System.out.println("【reflect-config.json 片段】");
        reflectConfig.forEach(e -> System.out.println("  " + e.toJson()));
        System.out.println("【proxy-config.json】");
        proxyConfig.forEach(e -> System.out.println("  " + e.toJson()));
        System.out.println("【resource-config.json】");
        resourceConfig.forEach(e -> System.out.println("  " + e.toJson()));

        System.out.println();
        System.out.println("【运行期访问校验】");
        List<String> reflectUses = List.of(
                "com.heima.User#getName",
                "com.heima.Order#getAmount",
                "com.heima.Product#getPrice");
        List<String> resourceUses = List.of(
                "application.yml",
                "i18n/messages_zh_CN.properties",
                "static/logo.png");
        List<List<String>> proxyUses = List.of(
                List.of("com.heima.OrderService"),
                List.of("com.heima.UserService"));
        List<String> issues = validate(reflectUses, resourceUses, proxyUses);
        issues.forEach(i -> System.out.println("  " + i));

        System.out.println();
        System.out.println("【最实用的收集方法】");
        Map<String, String> method = new LinkedHashMap<>();
        method.put("步骤 1", "用 -agentlib:native-image-agent 跑一遍完整测试用例");
        method.put("步骤 2", "自动生成 reflect/resource/proxy 配置");
        method.put("步骤 3", "人工复核（去掉测试期临时反射的噪音）");
        method.put("步骤 4", "配置进版本库，随代码演进");
        method.forEach((k, v) -> System.out.printf("  %-6s %s%n", k, v));
    }
}
```

**课后练习**
1. 实现配置校验器：给定运行期的反射/资源/代理使用清单，检查是否被配置覆盖，输出缺失项。
2. 用 `native-image-agent`（或模拟）收集一份配置，对比"手工配置"与"agent 生成"的差异。
3. 思考题：为什么"类级 `allDeclared*`"会让体积膨胀？从"整类方法/字段全部保留"与"可达性剪枝失效"分析。

---

### 72-04 类初始化时机

**知识点**
- **AOT 的类初始化有两个时机**：**构建期初始化**（`--initialize-at-build-time`，把静态初始化提前到构建，启动更快、内存更省）与**运行期初始化**（默认，`--initialize-at-run-time`）——**选错会导致"构建期读了构建机的配置"**（如数据库连接在构建期被建立）。
- **构建期初始化的适用**：**纯常量计算**（静态常量表、枚举映射）、**无副作用的工具类**——**安全且收益大**（省启动时间）。
- **运行期初始化的必须**：**读环境变量/配置/文件**、**连数据库/网络**、**依赖运行时状态的单例**——**构建期执行会用构建机的环境，产出错误结果**（最隐蔽的 bug）。
- **Spring Boot 的处理**：框架大部分类标记为构建期初始化（提升启动），用户类默认运行期——**自定义 `@Configuration` 若在静态块里读了环境变量，必须标记运行期**。
- **与 66 章的关系**：Agent 也是"在类加载前后做事"（premain 在 main 前）——**AOT 的"构建期初始化"相当于把"运行期的一次性初始化"提前到构建期**，本质是"时间前移"。
- 易错点：静态块读环境变量（构建期读到构建机的值）；静态单例连数据库（构建失败或连错库）；随机数/时间戳在构建期固定（每次启动都是同一个值）；无 `--trace-class-initialization` 排查（不知道哪个类在构建期初始化了）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ClassInitialization {
    /** 类定义：静态初始化做什么 */
    record ClassDef(String name, String staticInit, boolean pureComputation,
                    boolean readsEnvironment, String recommendation) {
    }

    static final List<ClassDef> classes = new ArrayList<>();

    static {
        classes.add(new ClassDef("Constants", "构建常量表（如枚举映射）", true, false,
                "构建期初始化（安全，启动更快）"));
        classes.add(new ClassDef("DateFormats", "创建 DateTimeFormatter 实例", true, false,
                "构建期初始化（无状态、线程安全）"));
        classes.add(new ClassDef("DbConfig", "static { 读 DB_URL 环境变量 }", false, true,
                "运行期初始化（构建期会读到构建机的值）"));
        classes.add(new ClassDef("CacheWarmup", "static { 连接 Redis 预加载 }", false, true,
                "运行期初始化（副作用 + 外部依赖）"));
        classes.add(new ClassDef("InstanceId", "static { id = UUID.randomUUID() }", false,
                true, "运行期初始化（否则所有实例同一个 ID）"));
        classes.add(new ClassDef("RegExPool", "预编译正则 Pattern", true, false,
                "构建期初始化（无损）"));
    }

    /** 模拟构建：把"构建期初始化的类"在构建时执行一次 */
    static void simulateBuild(String buildMachineEnv) {
        System.out.println("【构建期执行静态初始化（构建机环境：" + buildMachineEnv + "）】");
        Map<String, String> builtValues = new LinkedHashMap<>();
        for (ClassDef c : classes) {
            if (c.readsEnvironment()) {
                builtValues.put(c.name(), "构建机值：" + buildMachineEnv);
            } else if (c.pureComputation()) {
                builtValues.put(c.name(), "纯计算结果（与机器无关）");
            }
        }
        builtValues.forEach((k, v) -> System.out.printf("  %-14s -> %s%n", k, v));
        System.out.println();

        System.out.println("【部署到生产（生产环境：prod-db）】");
        for (ClassDef c : classes) {
            if (c.readsEnvironment()) {
                System.out.printf("  %-14s 若构建期初始化 -> 仍用「%s」（错！）%n",
                        c.name(), builtValues.get(c.name()));
                System.out.printf("  %-14s 运行期初始化 -> 读到「prod-db」（正确）%n",
                        c.name());
            }
        }
        System.out.println();
    }

    static void decisionTable() {
        System.out.println("【初始化时机决策表】");
        String[][] rows = {
                {"纯常量/无副作用", "构建期", "启动快、内存省"},
                {"读环境变量/配置", "运行期", "避免构建机环境泄漏"},
                {"连数据库/网络", "运行期", "构建期不该有外部依赖"},
                {"生成随机数/时间戳", "运行期", "否则所有实例共享同一值"},
                {"预编译正则/格式化器", "构建期", "无损且有收益"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-20s %-8s %s%n", r[0], r[1], r[2]);
        }
        System.out.println();
    }

    public static void main(String[] args) {
        simulateBuild("build-agent-ci");
        decisionTable();
        System.out.println("【排查手段】--trace-class-initialization=类名 观察该类在哪个阶段初始化");
        System.out.println("  Spring Boot：默认用户类运行期初始化；有静态副作用的类显式标注");
    }
}
```

**课后练习**
1. 实现"初始化时机决策表"判定器：给定类定义（是否读环境/是否有副作用），输出建议时机与理由。
2. 复现"构建期读环境变量"的 bug：构建机是测试库，生产启动后仍连测试库，说明修复方式。
3. 思考题：为什么"随机数在构建期固定"是隐蔽 bug？从"所有实例共享同一 ID"的后果分析。

---

### 72-05 Spring Boot 3 AOT 与 native

**知识点**
- **Spring Boot 3 支持 native 的关键是 AOT 引擎**：构建时（`mvn -Pnative native:compile` 或 `spring-boot:process-aot`）**扫描应用上下文 -> 生成 `reflect-config`/`proxy-config`/`resource-config`/`BeanDefinition` 代码**——**把"运行期反射装配"变成"构建期生成的代码"**（60-02 的 IOC 在 AOT 下的变形）。
- **AOT 处理的三类产物**：**反射/资源/代理配置**（72-03）、**Bean 注册代码**（生成 `ApplicationContextInitializer`）、**代理类**（CGLIB 代理在构建期生成）——**所以启动时几乎不反射**。
- **限制与约束**：**`@Profile` 在构建期固定**（运行时切 profile 需要在构建期决定，或多 profile 构建）、**懒加载语义受限**、**`@Bean` 方法返回类型必须可分析**、**部分动态特性不可用**（如 `@Scheduled` 的 cron 在运行期可改，但有些动态注册不行）。
- **GraalVM 的 Spring Boot 支持模块**：官方提供 `spring-boot-starter-native`（或 `org.springframework.aot` 依赖）处理框架层的 native 适配——**用户只需处理业务代码的动态特性**。
- **与 37/47 章的关系**：37 章容器化（镜像小）、47 章 Serverless（冷启动）——**native 镜像把两者结合**（几十 MB 镜像 + 毫秒启动）。
- 易错点：以为"加个依赖就能 native"（业务反射仍要配置）；profile 在运行期切换（构建期已固定）；用反射读配置类（配置需在构建期可见）；不跑集成测试（native 下的行为差异需验证）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SpringAotDemo {
    record BeanDef(String name, String type, String scope, boolean lazy,
                   List<String> deps) {
    }

    /** 模拟 AOT 引擎：扫描 Bean 定义 -> 生成注册代码（而非运行期反射） */
    static String generateRegistrar(List<BeanDef> beans) {
        StringBuilder sb = new StringBuilder();
        sb.append("// 构建期生成，勿修改\n");
        sb.append("public class GeneratedBeanRegistrar implements ApplicationContextInitializer {\n");
        sb.append("  public void initialize(ConfigurableApplicationContext ctx) {\n");
        for (BeanDef b : beans) {
            sb.append("    ctx.registerBean(\"").append(b.name()).append("\", ")
                    .append(b.type()).append(".class, () -> new ").append(b.type())
                    .append("(");
            for (int i = 0; i < b.deps().size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append("ctx.getBean(\"").append(b.deps().get(i)).append("\")");
            }
            sb.append("));\n");
        }
        sb.append("  }\n}\n");
        return sb.toString();
    }

    /** 检查 Bean 定义是否 AOT 友好 */
    static List<String> check(List<BeanDef> beans) {
        List<String> issues = new ArrayList<>();
        for (BeanDef b : beans) {
            if (b.type().contains("InvocationHandler")) {
                issues.add(b.name() + "：动态代理类型无法在构建期确定");
            }
            if (b.scope().equals("prototype") && b.lazy()) {
                issues.add(b.name() + "：prototype + lazy 在 AOT 下语义受限");
            }
        }
        return issues;
    }

    public static void main(String[] args) {
        List<BeanDef> beans = List.of(
                new BeanDef("dataSource", "DataSource", "singleton", false, List.of()),
                new BeanDef("orderDao", "OrderDao", "singleton", false, List.of("dataSource")),
                new BeanDef("orderService", "OrderService", "singleton", false,
                        List.of("orderDao")),
                new BeanDef("requestCtx", "RequestCtx", "prototype", true, List.of()));

        System.out.println("【Bean 定义（运行期反射装配的形态）】");
        beans.forEach(b -> System.out.printf("  %-12s %-16s scope=%-10s lazy=%-5s deps=%s%n",
                b.name(), b.type(), b.scope(), b.lazy(), b.deps()));

        System.out.println();
        System.out.println("【AOT 生成的注册器（构建期固定）】");
        System.out.println(generateRegistrar(beans));

        System.out.println();
        System.out.println("【AOT 友好性检查】" + (check(beans).isEmpty() ? "通过" : check(beans)));

        System.out.println();
        System.out.println("【构建命令与产物】");
        Map<String, String> build = new LinkedHashMap<>();
        build.put("处理 AOT", "mvn spring-boot:process-aot");
        build.put("编译原生", "mvn -Pnative native:compile");
        build.put("产物", "target/应用名（可执行文件，约 60~120MB）");
        build.put("运行", "./应用名（0.1s 启动，无 JVM）");
        build.forEach((k, v) -> System.out.printf("  %-8s %s%n", k, v));

        System.out.println();
        System.out.println("【Spring Boot 3 的 native 限制清单】");
        List<String> limits = List.of(
                "@Profile 在构建期固定（多环境需分别构建或运行期用配置类）",
                "懒加载语义受限（AOT 生成的是静态注册）",
                "@Bean 方法返回类型需可分析（避免 Object/泛型擦除）",
                "动态注册 Bean（BeanDefinitionRegistryPostProcessor 动态加类）需显式声明");
        limits.forEach(l -> System.out.println("  - " + l));
    }
}
```

**课后练习**
1. 实现 Bean 注册器代码生成：从 Bean 定义列表生成 `ApplicationContextInitializer` 风格的注册代码。
2. 列出 Spring Boot 3 native 的 4 条限制，各给一个"会踩坑"的业务写法与规避方式。
3. 思考题：为什么 `@Profile` 在 native 下必须在构建期固定？从"条件装配代码是构建期生成的"分析。

---

### 72-06 原生镜像的调试与排障

**知识点**
- **native 镜像的典型故障**：**`NoSuchMethodException`**（反射未配置）、**`FileNotFoundException`/null 资源**（资源未配置）、**`Proxy` 创建失败**（代理未配置）、**构建期初始化错误**（静态块连了外部服务）、**`NoClassDefFoundError`**（可达性剪枝误删）。
- **排障四步**：**看异常栈**（native 的栈比 JVM 简略）-> **用 `--trace-class-initialization`/`--trace-object-instantiation`**（定位类初始化）-> **用 agent 重新收集配置**（补充遗漏）-> **本地最小复现**（剥离到最小用例）。
- **日志与诊断能力的差异**：**没有 JFR 的完整能力**、**没有 attach 调试器**（部分支持 gdb 级调试）、**堆转储受限**——**所以要在构建期做好"可观测性预埋"**（42 章的埋点必须在镜像里生效）。
- **与 66 章的关系**：Agent 在 native 下**不可用**（没有字节码）——**AOT 与 Agent 是互斥的运行时形态**，监控方案要换（用框架埋点/代理层）。
- **排障的工程化**：**保留 JVM 版本并行**（native 出问题可切回）、**灰度发布**（41-07）、**集成测试覆盖**（native 下跑一遍全量用例）。
- 易错点：只跑单元测试不跑集成测试（反射问题在启动时才暴露）；出问题就回滚不排查（丢弃 native 收益）；本地能跑镜像不能跑（构建环境差异）；无 JVM 版本兜底（故障时无退路）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class NativeDebugging {
    record Symptom(String error, String cause, String fix) {
    }

    static void symptomTable() {
        System.out.println("【常见故障与修复】");
        List<Symptom> symptoms = List.of(
                new Symptom("NoSuchMethodException: X.getY()",
                        "反射调用未在 reflect-config 注册", "补 reflect-config 或收窄反射使用"),
                new Symptom("getResourceAsStream 返回 null",
                        "资源文件未打进镜像", "补 resource-config（含通配符）"),
                new Symptom("IllegalArgumentException: interface not visible",
                        "动态代理接口未在 proxy-config", "补 proxy-config"),
                new Symptom("构建时报连接超时",
                        "某类在构建期初始化并连接外部服务", "标记该类为运行期初始化"),
                new Symptom("NoClassDefFoundError 运行期",
                        "动态加载的类被可达性剪枝删除", "显式注册或改为静态引用"),
                new Symptom("配置值全是默认值",
                        "配置类在构建期初始化，读到构建机环境", "改为运行期初始化"));
        for (Symptom s : symptoms) {
            System.out.printf("  %-38s%n    原因：%-28s 修复：%s%n",
                    s.error(), s.cause(), s.fix());
        }
        System.out.println();
    }

    static void diagnoseFlow() {
        System.out.println("【排障四步】");
        Map<String, String> steps = new LinkedHashMap<>();
        steps.put("1 看栈", "native 栈信息较少，先定位到类与方法");
        steps.put("2 跟踪", "--trace-class-initialization=类名 / --trace-object-instantiation");
        steps.put("3 收集", "-agentlib:native-image-agent 跑测试，补配置");
        steps.put("4 复现", "剥离到最小用例（单类 + 单配置）验证");
        steps.forEach((k, v) -> System.out.printf("  %s. %s%n", k, v));
        System.out.println();
    }

    static void capabilities() {
        System.out.println("【能力差异：JVM vs native】");
        String[][] rows = {
                {"JFR", "完整", "受限（部分事件不可用）"},
                {"Heap dump", "支持", "受限"},
                {"Attach 调试器", "支持", "不支持（需 gdb 级）"},
                {"动态 Agent", "支持（66 章）", "不可用（无字节码）"},
                {"System.gc 手动触发", "支持", "受限（GC 策略固定）"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-18s JVM=%-12s native=%s%n", r[0], r[1], r[2]);
        }
        System.out.println();
        System.out.println("  含义：可观测性要在构建期预埋（42 章埋点必须生效）");
        System.out.println();
    }

    static void rollbackPlan() {
        System.out.println("【上线策略：保留两条路】");
        List<String> plan = List.of(
                "构建双产物：JVM 镜像 + native 镜像（都推仓库）",
                "灰度：先 5% 流量走 native（41-07）",
                "对比指标：启动时间/内存/P99 错误率/业务指标",
                "异常 -> 切回 JVM 镜像（秒级，41-11 回滚）",
                "全量后保留 JVM 镜像 1 个月作兜底");
        plan.forEach(p -> System.out.println("  - " + p));
    }

    public static void main(String[] args) {
        symptomTable();
        diagnoseFlow();
        capabilities();
        rollbackPlan();
    }
}
```

**课后练习**
1. 构造一个"反射未配置"的 native 故障模拟，按四步排障流程走一遍，输出每一步的证据。
2. 列出你服务的"动态特性清单"（反射/代理/资源/动态类加载），标注哪些需要 AOT 配置。
3. 思考题：为什么 native 下"Agent 不可用"？从"没有字节码可增强"与"类在构建期已编译"分析。

---

### 72-07 性能特征对比

**知识点**
- **启动性能**：native **毫秒级**（无类加载/无 JIT 预热）；JVM **秒级**（Spring Boot 10~30s）——**差距 2~3 个数量级**。
- **内存占用**：native 常驻**30~100MB**（无 JVM 元空间/JIT 代码缓存）；JVM **200~500MB**——**小服务差距 3~5 倍**。
- **峰值吞吐**：**JIT 通常更高**（运行期热点优化：内联、去虚拟化、逃逸分析、分层编译）；native **略低 5~20%**（静态分析保守）——**但差距在缩小**（PGO 优化）。
- **尾延迟**：JVM 有 **GC 停顿**（即使 G1/ZGC 也有）；native **无 JIT 编译线程争抢**、GC 更简单——**尾延迟可能更稳定**。
- **预热曲线**：JVM **前 1~5 分钟性能爬坡**（JIT 编译）；native **从一开始就是满性能**——**短任务场景 native 总耗时更低**。
- 与 30 章的关系：30 章讲的 JIT/GC 调优在 native 下**大半失效**（无 JIT、GC 简化）——**调优手段要换**。
- 易错点：只比启动不比峰值（长驻服务可能更慢）；用短基准测试比 native（JVM 没预热完）；忽略预热曲线（JVM 前几分钟差很多）；以为 native 全面优于 JVM。

**示例代码**
```java
import java.util.ArrayList;
import java.util.List;

public class PerformanceCompare {
    /** 预热曲线：JVM 逐步接近峰值，native 从 0 时刻即满性能 */
    static double jvmThroughput(int second) {
        if (second <= 0) {
            return 0;
        }
        // 简化模型：指数爬坡到峰值 10000，5 分钟（300s）接近峰值
        return 10_000 * (1 - Math.exp(-second / 60.0));
    }

    static double nativeThroughput(int second) {
        return 9_000;                    // 静态编译，峰值略低但无爬坡
    }

    static void warmupCurve() {
        System.out.println("【吞吐预热曲线（QPS）】");
        System.out.printf("  %-8s %-12s %-12s %s%n", "时间", "JVM", "native", "说明");
        for (int s : new int[]{1, 10, 30, 60, 120, 300}) {
            System.out.printf("  %-8s %-12.0f %-12.0f %s%n",
                    s + "s", jvmThroughput(s), nativeThroughput(s),
                    jvmThroughput(s) > nativeThroughput(s) ? "JVM 反超" : "native 领先");
        }
        System.out.println("  短任务（<60s）native 总吞吐更高；长驻服务 JVM 反超");
        System.out.println();
    }

    /** 任务时长对"总耗时"的影响：启动时间计入总耗时 */
    static void totalTime() {
        System.out.println("【不同任务时长的总耗时】");
        double jvmStartup = 15, nativeStartup = 0.1;
        double jvmSteadyQps = 10_000, nativeSteadyQps = 9_000;
        System.out.printf("  %-14s %-14s %-14s %s%n", "任务量", "JVM 总耗时", "native 总耗时", "胜者");
        for (int tasks : new int[]{100, 1_000, 100_000, 10_000_000}) {
            double jvmTotal = jvmStartup + tasks / jvmSteadyQps;
            double nativeTotal = nativeStartup + tasks / nativeSteadyQps;
            System.out.printf("  %-14d %-14.2f %-14.2f %s%n", tasks, jvmTotal, nativeTotal,
                    jvmTotal > nativeTotal ? "native" : "JVM");
        }
        System.out.println("  临界点：任务量小 -> native 完胜；任务量大 -> JVM 吞吐优势覆盖启动开销");
        System.out.println();
    }

    static void tailLatency() {
        System.out.println("【尾延迟构成】");
        List<String> rows = new ArrayList<>(List.of(
                "JVM：GC 停顿（ZGC <1ms，但仍有）+ JIT 编译线程 CPU 争抢",
                "native：GC 更简单（无并发标记线程竞争）+ 无 JIT 线程",
                "结论：native 的 P99.9 通常更平稳（但缺少 JIT 优化可能 P50 略差）"));
        rows.forEach(r -> System.out.println("  - " + r));
        System.out.println();
    }

    public static void main(String[] args) {
        warmupCurve();
        totalTime();
        tailLatency();
        System.out.println("  选型：启动敏感/短任务/内存受限 -> native；长驻高吞吐 -> JVM（或先 native 后评估）");
    }
}
```

**课后练习**
1. 用预热曲线模型计算"处理 10 万请求"在 JVM 与 native 下的总耗时（含启动），找出临界任务量。
2. 设计一次压测方案：对比 JVM 与 native 的 QPS/P99/内存，要求包含预热与非预热两组。
3. 思考题：为什么"短任务 native 总耗时更低"？从"启动开销在总耗时中的占比"分析。

---

### 72-08 与容器/Serverless 结合

**知识点**
- **native 镜像天然适合容器**：**镜像小**（基于 `scratch` 或 `distroless`，20~80MB vs JVM 镜像 200MB+）、**启动快**（毫秒级，滚动更新/扩容瞬时完成）、**内存低**（同样资源跑更多副本）。
- **与 37 章的结合**：容器的"秒级启动"是为了应用启动服务的——**如果应用启动要 30 秒，容器秒级启动也没用**（37-01 的对照）；native 让"容器快"真正落地。
- **与 47-07 Serverless 的结合**：FaaS 的核心成本是**冷启动计费时间**——native 把冷启动从秒级降到毫秒级，**直接降低 FaaS 成本并提升体验**（72-01 的成本模型）。
- **构建与部署实践**：**多阶段构建**（构建阶段用 GraalVM 镜像，运行阶段用 scratch）、**静态链接**（glibc vs musl 选择，跨发行版更稳）、**镜像扫描**（36-10 的供应链安全，native 镜像无 shell 更利于安全）。
- **与 41/47 章的联动**：CI 流水线增加 native 构建（41-03 的产物）、K8s 的启动探针可缩短（47-02 的 `startupProbe` 从 30s 改 1s）、HPA 扩容更快（47-08）。
- 易错点：用 libc 动态链接（跨基础镜像版本可能失败）；镜像里留 shell（安全面变大）；CI 构建时间过长（native 构建 5~15 分钟）；忘记调小 `startupProbe`（白白等待）。

**示例代码**
```java
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class NativeContainer {
    static void imageCompare() {
        System.out.println("【镜像体积与启动】");
        String[][] rows = {
                {"JVM + 完整 JDK 镜像", "~450MB", "15~30s", "启动慢、镜像大"},
                {"JVM + JRE slim", "~200MB", "10~20s", "常见折衷"},
                {"native + distroless", "~60~120MB", "0.05~0.2s", "最优启动"},
                {"native + scratch", "~30~60MB", "0.05~0.1s", "最小，需静态链接"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-24s %-12s %-12s %s%n", r[0], r[1], r[2], r[3]);
        }
        System.out.println();
    }

    /** 扩容速度：从 0 到可服务 */
    static void scaleOut() {
        System.out.println("【扩容到可服务的时间】");
        double jvmBoot = 20, nativeBoot = 0.15;
        double imagePullJvm = 25, imagePullNative = 4;   // 镜像拉取（秒）
        double probeJvm = 30, probeNative = 2;           // 探针等待
        double totalJvm = imagePullJvm + jvmBoot + probeJvm;
        double totalNative = imagePullNative + nativeBoot + probeNative;
        System.out.printf("  JVM：拉镜像 %.0fs + 启动 %.0fs + 探针等待 %.0fs = %.0fs%n",
                imagePullJvm, jvmBoot, probeJvm, totalJvm);
        System.out.printf("  native：拉镜像 %.0fs + 启动 %.1fs + 探针等待 %.0fs = %.1fs%n",
                imagePullNative, nativeBoot, probeNative, totalNative);
        System.out.printf("  差距 %.0f 倍 -> 突发流量的响应能力完全不同（47-08）%n",
                totalJvm / totalNative);
        System.out.println();
    }

    static void buildPipeline() {
        System.out.println("【多阶段构建】");
        Map<String, String> stages = new LinkedHashMap<>();
        stages.put("阶段 1 构建", "FROM ghcr.io/graalvm/native-image AS build；拷贝源码并 native-image 编译");
        stages.put("阶段 2 运行", "FROM gcr.io/distroless/base（或 scratch）；只 COPY 可执行文件");
        stages.put("产物", "单文件可执行（无 JVM、无 shell、无包管理器）");
        stages.put("收益", "体积小、攻击面小（36-10）、启动快");
        stages.forEach((k, v) -> System.out.printf("  %-8s %s%n", k, v));
        System.out.println();
    }

    static void k8sTuning() {
        System.out.println("【K8s 配置调整（对比 JVM 版）】");
        String[][] rows = {
                {"startupProbe.initialDelay", "30s", "1s"},
                {"startupProbe.failureThreshold", "30", "5"},
                {"livenessProbe.period", "10s", "5s"},
                {"resources.requests.memory", "512Mi", "128Mi"},
                {"HPA 扩容响应", "~1min", "~10s"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-32s JVM=%-10s native=%s%n", r[0], r[1], r[2]);
        }
        System.out.println();
        System.out.println("  注意：native 内存更省但峰值 CPU 可能更高（无 JIT 优化）-> 压测确认 limits");
    }

    public static void main(String[] args) {
        imageCompare();
        scaleOut();
        buildPipeline();
        k8sTuning();
        System.out.println("  与 37 章的关系：容器让交付一致，native 让「容器快」名副其实");
    }
}
```

**课后练习**
1. 计算"从 0 扩容到可服务"在 JVM 与 native 下的耗时（含拉镜像与探针），量化对突发流量的影响。
2. 写出 native 多阶段 Dockerfile（构建阶段 + distroless 运行阶段），标注每行的作用。
3. 思考题：为什么 native 镜像"攻击面更小"？从"无 shell/无包管理器/无 JVM"分析（36 章供应链安全）。

---

### 72-09 构建流水线

**知识点**
- **native 构建进入 CI 的四个关注点**：**构建时间**（5~15 分钟，要缓存依赖与分层）、**构建资源**（内存 4~8GB，CI runner 要够）、**确定性**（同样输入产出可复现）、**产物管理**（可执行文件大，制品库要支持）。
- **构建缓存的三个层次**：**依赖缓存**（Maven/Gradle 本地库）、**Docker 层缓存**（37-02 的构建缓存）、**native-image 缓存**（部分工具支持，但收益有限）——**分层 Dockerfile 是主要手段**。
- **流水线阶段设计**（41-03 的扩展）：编译 -> 测试 -> **AOT 处理** -> **native 编译** -> 镜像构建 -> 扫描 -> 推送 -> 部署——**native 编译要放在测试之后**（测试失败不浪费 15 分钟）。
- **与 41 章的联动**：native 产物作为**不可变制品**（41-04）、金丝雀发布（41-07）、灰度对比（JVM vs native）。
- 易错点：native 构建放最前面（测试失败白构建）；无依赖缓存（每次全量下载）；CI runner 内存不足（构建 OOM）；不扫镜像（36-10）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class NativePipeline {
    record Stage(String name, int minutes, boolean blocking, String note) {
    }

    static void pipeline() {
        System.out.println("【流水线阶段（native 构建）】");
        List<Stage> stages = List.of(
                new Stage("编译", 2, true, "javac / mvn compile"),
                new Stage("单元测试", 4, true, "失败即停（不浪费 native 构建）"),
                new Stage("AOT 处理", 1, true, "spring-boot:process-aot 生成配置"),
                new Stage("native 编译", 12, true, "native-image（最贵）"),
                new Stage("镜像构建", 1, true, "多阶段：distroless + 可执行文件"),
                new Stage("镜像扫描", 2, false, "CVE 扫描（36-10）"),
                new Stage("推送与部署", 2, true, "推制品库 -> 部署"));
        int total = 0;
        for (Stage s : stages) {
            total += s.minutes();
            System.out.printf("  %-12s %2dmin %s %s%n", s.name(), s.minutes(),
                    s.blocking() ? "阻断" : "告警", s.note());
        }
        System.out.printf("  全链路约 %d 分钟（native 编译占 %.0f%%）%n", total,
                stages.get(3).minutes() * 100.0 / total);
        System.out.println();
    }

    static void optimization() {
        System.out.println("【缩短构建时间的三招】");
        Map<String, String> opt = new LinkedHashMap<>();
        opt.put("依赖缓存", "缓存 ~/.m2 或 ~/.gradle（省 1~3 分钟）");
        opt.put("分层 Dockerfile", "先 COPY pom 下载依赖，再 COPY 源码（37-03 缓存策略）");
        opt.put("并行", "单元测试与 AOT 处理并行（互不依赖）");
        opt.put("跳过非必要内容", "只编译需要的模块；用 --gc=serial 减小编译负担（视场景）");
        opt.forEach((k, v) -> System.out.printf("  %-12s %s%n", k, v));
        System.out.println();
    }

    static void artifactManagement() {
        System.out.println("【产物管理】");
        List<String> notes = new ArrayList<>();
        notes.add("产物：可执行文件（60~120MB）+ 镜像（含可执行文件）");
        notes.add("版本：语义化版本 + 提交摘要（41-04），不可变");
        notes.add("保留：release 永久、SNAPSHOT 30 天（41-04 保留策略）");
        notes.add("校验：sha256 + 签名（供应链安全 36-10）");
        notes.forEach(n -> System.out.println("  - " + n));
        System.out.println();
    }

    static void determinism() {
        System.out.println("【构建确定性】");
        System.out.println("  native-image 的产物可能受构建环境（CPU 特性/时区/路径）影响");
        System.out.println("  做法：固定构建镜像（如 ghcr.io/graalvm/native-image:21 固定 digest）");
        System.out.println("        固定时区/编码；避免把构建时间写进产物");
        System.out.println("  验证：同一提交构建两次，比对 sha256（41-04 制品不可变）");
    }

    public static void main(String[] args) {
        pipeline();
        optimization();
        artifactManagement();
        determinism();
        System.out.println();
        System.out.println("  铁律：native 编译放最后（前面的门禁失败就不浪费 12 分钟）");
    }
}
```

**课后练习**
1. 设计 native 流水线（7 个阶段 + 并行 + 缓存），估算优化前后的总时长。
2. 写出固定构建环境的方案（镜像 digest、时区、编码），说明如何验证"构建确定性"。
3. 思考题：为什么"native 编译要放在所有门禁之后"？从"构建成本"与"快速反馈"的权衡分析。

---

### 72-10 迁移评估与决策

**知识点**
- **迁移评估的四个维度**：**收益**（启动/内存/成本）、**成本**（构建时间/配置工作量/团队学习）、**风险**（动态特性不兼容/行为差异）、**回退能力**（能否双版本并存）——**四维打分，而不是"技术上很酷"**（52-02 ADR 的方法）。
- **评估的量化方法**：**启动与内存的收益可量化**（72-01/07 的模型）；**配置成本**（反射/代理/资源的配置条数 × 每条工时）；**风险**（动态特性清单的占比）。
- **迁移路线**：**先小服务试点**（低风险验证）-> **再核心服务**（有试点经验）-> **保留 JVM 版本并行**（灰度 + 回滚）——**与 49-10 演进纪律一致**。
- **不迁移的信号**：动态特性极多（配置成本爆炸）、构建时间不可接受、峰值性能要求高、团队无 native 经验——**"不迁移"也是正确决策**。
- 与 52 章的关系：这是一个典型的**架构决策记录（ADR）**：背景/选项/决策/负面后果/回顾条件——**把本章的评估结论写成 ADR**。
- 易错点：没量化收益就迁移（为技术而技术）；低估配置成本（反射配置是长尾工作）；无并行运行期（出问题无法回退）；迁移后不做压测（峰值劣化未发现）。

**示例代码**
```java
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MigrationDecision {
    record Candidate(String service, double startupSec, long memoryMb,
                     int dynamicFeatures, int configItems, double peakQpsNeed) {
    }

    /** 四维打分（0~10 分，越高越适合迁移） */
    static Map<String, Integer> score(Candidate c) {
        Map<String, Integer> s = new LinkedHashMap<>();
        // 收益：启动越慢、内存越大，收益越高
        int benefit = (int) Math.min(10, c.startupSec() / 3 + c.memoryMb() / 50.0);
        // 成本：动态特性与配置项越多，成本越高 -> 得分低
        int cost = (int) Math.max(0, 10 - c.dynamicFeatures() * 2 - c.configItems() / 2);
        // 风险：动态特性多则风险高 -> 得分低
        int risk = (int) Math.max(0, 10 - c.dynamicFeatures() * 2.5);
        // 峰值要求高 -> 不适合（JIT 更强）
        int peak = c.peakQpsNeed() > 5000 ? 3 : 9;
        s.put("收益", Math.min(10, benefit));
        s.put("成本可控", Math.min(10, cost));
        s.put("风险可控", Math.min(10, risk));
        s.put("峰值适配", peak);
        return s;
    }

    static String verdict(Map<String, Integer> s) {
        int total = s.values().stream().mapToInt(Integer::intValue).sum();
        if (total >= 28) {
            return "推荐迁移（试点验证）";
        }
        if (total >= 18) {
            return "谨慎评估（先解决动态特性）";
        }
        return "不迁移（JVM 更合适）";
    }

    public static void main(String[] args) {
        List<Candidate> candidates = List.of(
                new Candidate("CLI 图片处理工具", 0.5, 80, 1, 2, 10),
                new Candidate("后台管理 API", 25, 600, 6, 20, 300),
                new Candidate("订单核心服务", 18, 800, 12, 45, 8000),
                new Candidate("定时任务 Worker", 12, 400, 3, 8, 200));
        System.out.println("【迁移评估】");
        for (Candidate c : candidates) {
            Map<String, Integer> s = score(c);
            System.out.printf("  %-18s %s%n", c.service(), s);
            System.out.printf("      启动=%.1fs 内存=%dMB 动态特性=%d 配置项=%d 峰值=%.0f%n",
                    c.startupSec(), c.memoryMb(), c.dynamicFeatures(), c.configItems(),
                    c.peakQpsNeed());
            System.out.printf("      结论：%s%n", verdict(s));
        }

        System.out.println();
        System.out.println("【迁移路线】");
        Map<String, String> roadmap = new LinkedHashMap<>();
        roadmap.put("第 1 步", "选低风险服务试点（CLI / Worker）");
        roadmap.put("第 2 步", "沉淀配置模板与排障手册（72-03/06）");
        roadmap.put("第 3 步", "评估核心服务（订单）的配置成本与峰值影响");
        roadmap.put("第 4 步", "双版本灰度（JVM 兜底 41-07）");
        roadmap.forEach((k, v) -> System.out.printf("  %-6s %s%n", k, v));

        System.out.println();
        System.out.println("【决策记录（ADR 要素）】背景/选项/决策/负面后果/回顾条件（52-02）");
        System.out.println("  不迁移的信号：动态特性极多、构建时间不可接受、峰值要求高");
    }
}
```

**课后练习**
1. 用四维打分评估你团队的 4 个服务，输出"迁移优先级"排序与理由。
2. 为一个服务写 ADR：背景（启动慢）、选项（JVM 优化/native/保留）、决策、负面后果、回顾条件。
3. 思考题：为什么"不迁移"也是正确决策？从"配置成本长尾"与"峰值性能取舍"分析。

---

### 72-11 综合案例：把一个服务改成 native

**知识点**
- **把本章收口成一次完整迁移**：**评估**（72-10 四维打分）-> **AOT 配置**（反射/资源/代理 72-03）-> **初始化调整**（72-04）-> **框架适配**（Spring Boot 3 AOT 72-05）-> **排障**（72-06）-> **性能验证**（72-07）-> **容器与流水线**（72-08/09）。
- **迁移的真实产出**：启动 25s -> 0.15s、内存 600MB -> 90MB、镜像 320MB -> 85MB、配置新增 30 条（反射/资源）——**收益与成本都可量化**。
- **踩坑清单（典型五条）**：反射未配置（NoSuchMethod）、资源未打进（FileNotFound）、构建期初始化读了构建机环境、动态代理未声明、JIT 相关调优参数失效。
- **验收标准**：功能回归全绿（native 下跑全量集成测试）、性能达标（启动/内存/峰值都在预期内）、可回退（JVM 镜像并行）、可观测（埋点在 native 下生效）。
- 与全书的关系：native 迁移把 37（容器）、41（流水线）、47（弹性）、30（性能）串成一次实践——**它是"部署形态演进"的收官**。
- 易错点：只跑单测不跑集成测试；不对比峰值性能；配置不随代码演进；迁移后放弃 JVM 兜底。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class NativeMigrationCase {
    record Metric(String name, String before, String after, String gain) {
    }

    static void results() {
        System.out.println("【迁移结果（某后台 API 服务）】");
        List<Metric> metrics = List.of(
                new Metric("启动时间", "25.0s", "0.15s", "167 倍"),
                new Metric("常驻内存", "600MB", "90MB", "6.7 倍"),
                new Metric("镜像体积", "320MB", "85MB", "3.8 倍"),
                new Metric("峰值 QPS", "12,000", "11,000", "略降 8%"),
                new Metric("P99 延迟", "85ms", "88ms", "略升（可接受）"),
                new Metric("新增配置", "0", "32 条（反射/资源/代理）", "成本项"));
        for (Metric m : metrics) {
            System.out.printf("  %-10s %-10s -> %-24s %s%n", m.name(), m.before(), m.after(),
                    m.gain());
        }
        System.out.println();
    }

    static void pitfalls() {
        System.out.println("【踩坑清单】");
        Map<String, String> pits = new LinkedHashMap<>();
        pits.put("反射未配置", "启动报 NoSuchMethodException -> 补 reflect-config");
        pits.put("资源未打进", "getResource 返回 null -> 补 resource-config");
        pits.put("构建期初始化", "连了构建机数据库 -> 标记运行期初始化（72-04）");
        pits.put("代理未声明", "Proxy.newProxyInstance 失败 -> 补 proxy-config");
        pits.put("JIT 调优失效", "-XX:+UseG1GC 等参数无效 -> 改用 native 的 GC 选项");
        pits.forEach((k, v) -> System.out.printf("  %-12s %s%n", k, v));
        System.out.println();
    }

    static void acceptance() {
        System.out.println("【验收清单】");
        List<String> gates = new ArrayList<>(List.of(
                "功能：全量集成测试在 native 下通过（不只单测）",
                "启动：< 1s（探针可 1s 内就绪）",
                "内存：< 150MB（对比 JVM 版降 3 倍以上）",
                "峰值：QPS 降幅 < 15%（压测确认）",
                "可观测：日志/指标/trace 在 native 下正常",
                "回退：JVM 镜像并行保留，灰度可切"));
        gates.forEach(g -> System.out.println("  [ ] " + g));
        System.out.println();
    }

    static void lessons() {
        System.out.println("【经验总结】");
        List<String> lessons = List.of(
                "先做评估（72-10），不为技术而技术",
                "配置收集用 agent 跑测试（72-03），比手工靠谱",
                "构建放流水线最后（72-09），门禁失败不浪费 12 分钟",
                "灰度对比 JVM 与 native 的真实指标（41-07）",
                "保留 JVM 版本至少 1 个月，作为兜底（41-11）");
        lessons.forEach(l -> System.out.println("  - " + l));
    }

    public static void main(String[] args) {
        results();
        pitfalls();
        acceptance();
        lessons();
        System.out.println();
        System.out.println("  一句话：native 迁移是「收益明确、成本可量化、风险可回退」的工程决策");
    }
}
```

**课后练习**
1. 端到端规划一次 native 迁移（评估/配置/排障/验证/回退），输出可执行的任务清单与时间估算。
2. 写出"踩坑清单"里五类问题的复现方法（每类给一个最小代码示例）与修复步骤。
3. 思考题：为什么"保留 JVM 版本兜底"是迁移的前提？从"行为差异排查"与"故障回退"分析。

## 本章综合练习

### 综合题：原生镜像迁移评估

**场景**

把订单服务迁移到 GraalVM 原生镜像：构建配置、反射与资源注册、启动时间与内存对比、构建耗时与镜像体积权衡、动态代理与序列化兼容、生产适用性结论。

**任务**
1. 完成一次原生镜像构建：配置 native-image 参数、注册反射类与资源文件，跑通启动与一个接口。
2. 解决兼容问题：处理反射、动态代理、序列化框架的注册，记录每个报错与修复方式。
3. 对比数据：JVM 模式 vs 原生镜像的启动时间、常驻内存、峰值内存、镜像体积、首次请求延迟。
4. 给出适用性结论：什么服务适合原生镜像（短生命周期/弹性伸缩），什么服务不适合（大量反射/JIT 预热收益大）。

**验收清单**
- [ ] 原生镜像能正常启动并完成核心接口调用。
- [ ] 反射/资源相关的运行时报错全部解决，有配置清单。
- [ ] 对比数据完整，启动时间与内存改善有量化结论。
- [ ] 结论指出构建耗时与调试成本的代价，不是一边倒推荐。
