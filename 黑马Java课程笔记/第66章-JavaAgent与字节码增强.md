# 第66章 Java Agent 与字节码增强

第60章讲了"SkyWalking 怎么用 Agent 做零侵入埋点"，本章讲"自己怎么写一个 Agent"。Java Agent 是在 JVM 加载类时或运行时修改字节码的官方机制，是 APM、热修复、诊断工具的技术底座。本章按「premain 与 agentmain → 类加载与 Instrumentation → Class 文件结构 → ASM → ByteBuddy → 无侵入埋点 → 能力边界 → 热修复与动态诊断 → 性能与稳定性 → 安全与治理 → 自研 APM 案例」的顺序讲。所有示例仍是纯 JDK：真实解析自身的 Class 文件头、Transformer 链与降级、方法级插桩指令推演、Advice 前后增强、埋点采样与开销估算、热修复灰度切换、Agent 准入评估与埋点脱敏全部真实运行。

### 66-01 Agent 全景：premain 与 agentmain

**知识点**
- **Java Agent 是"在 JVM 启动前或运行中增强类行为"的官方机制**：通过 `-javaagent:xxx.jar` 或 `VirtualMachine.attach` 挂载，**拿到 `Instrumentation` 实例**，从而在类加载时（或已加载后）修改字节码——**这是 APM、热修复、诊断工具的技术底座**（60-08 的 SkyWalking Agent 就在这里）。
- **两种入口**：**premain**（`-javaagent` 启动时加载，**在 main 之前执行**，可注册 ClassFileTransformer 拦截后续所有类加载）；**agentmain**（运行时 attach，**可重定义已加载的类**，用于热更新与动态诊断）。
- **Agent 的三件套**：`MANIFEST.MF` 声明入口（`Premain-Class`/`Agent-Class`/`Can-Redefine-Classes`）、**入口方法签名**（`premain(String args, Instrumentation inst)`）、**Transformer 实现**（`transform(className, bytes) -> newBytes 或 null`）。
- **能做什么与不能做什么**：能改方法体（插桩、埋点、耗时统计）、能改类结构（加字段/方法，有限制）、不能改**方法签名**（参数/返回值）、不能改**已有字段的类型**、不能删方法（JVM 的 redefinition 限制）。
- **与 60 章的关系**：60-08 讲了"SkyWalking Agent 怎么用字节码增强做零侵入埋点"，本章讲"**自己怎么写一个 Agent**"——从使用者视角到实现者视角。
- 易错点：以为 Agent 能任意改类（受 redefinition 限制）；premain 里做耗时操作（拖慢整个 JVM 启动）；Transformer 抛异常（类加载失败，应用直接崩）；忘记 `Can-Retransform-Classes`（attach 后无法重定义）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AgentOverview {
    record Entry(String name, String when, String capability, String limit) {
    }

    static void entries() {
        System.out.println("【两种 Agent 入口】");
        List<Entry> entries = List.of(
                new Entry("premain", "-javaagent 启动时，main 之前",
                        "拦截后续所有类加载（ClassFileTransformer）", "不能改已加载的类"),
                new Entry("agentmain", "运行时 attach（VirtualMachine）",
                        "可重定义已加载的类（retransform/redefine）", "受 redefinition 限制"));
        for (Entry e : entries) {
            System.out.printf("  %-10s %-28s 能力=%-32s 限制=%s%n",
                    e.name(), e.when(), e.capability(), e.limit());
        }
        System.out.println();
    }

    static void lifecycle() {
        System.out.println("【预加载链路（premain）】");
        List<String> steps = List.of(
                "1) JVM 解析 -javaagent:agent.jar",
                "2) 读取 MANIFEST.MF 的 Premain-Class",
                "3) 加载入口类，调用 premain(String args, Instrumentation inst)",
                "4) Agent 注册 ClassFileTransformer",
                "5) JVM 继续启动 -> 每个类加载时回调 transformer",
                "6) Transformer 返回 null（不改）或 newBytes（改后加载）");
        steps.forEach(s -> System.out.println("  " + s));
        System.out.println();
    }

    static void limits() {
        System.out.println("【能力边界（redefinition 限制）】");
        Map<String, String> limits = new LinkedHashMap<>();
        limits.put("可改", "方法体指令、常量池引用、已有方法的实现");
        limits.put("可加", "新方法、新字段（Instrumentation.redefineClasses 有限支持）");
        limits.put("不可改", "方法签名（参数/返回值）、字段类型、父类、接口列表");
        limits.put("不可删", "已有方法/字段");
        limits.forEach((k, v) -> System.out.printf("  %-4s %s%n", k, v));
        System.out.println();
        System.out.println("  推论：Agent 增强是「包裹与插桩」，不是「重构类结构」");
    }

    public static void main(String[] args) {
        entries();
        lifecycle();
        limits();
    }
}
```

**课后练习**
1. 写一个最小 premain Agent（打印所有加载的类名并计数），用 `-javaagent` 挂到任意程序上验证输出。
2. 对比 premain 与 agentmain 的时机与能力差异，各举一个真实用途（APM vs 热修复）。
3. 思考题：为什么 Agent 不能改方法签名？从 JVM 的类元数据与已编译调用点的链接方式分析。

---

### 66-02 类加载与 Instrumentation

**知识点**
- **类加载的三步**：**加载**（读取字节码 -> 生成 Class 对象）、**链接**（验证/准备/解析）、**初始化**（执行 `<clinit>`）——**Agent 拦截的时机是"加载"阶段**：拿到字节码数组，改完再交给 JVM 定义类。
- **双亲委派与 Agent 的关系**：Transformer 在**所有类加载器**的加载路径上都会被调用（含 JDK 类、三方库、应用类）——**`className` 是内部格式**（`com/heima/Foo`，斜杠），**要过滤**（只增强目标包，否则性能与误伤双问题）。
- **`Instrumentation` 的核心 API**：`addTransformer(t, canRetransform)`、`retransformClasses(...)`（重跑 transformer）、`redefineClasses(...)`（直接替换字节码）、`isModifiableClass(...)`（判断能否改）。
- **Transformer 的返回值语义**：返回 `null` = 不修改；返回新 `byte[]` = 用新字节码定义类；**抛异常 = 类加载失败**（应用启动崩）——**所以 transformer 必须包 try-catch，增强失败要降级为"原样加载"**（44-08 降级思想），否则 Agent 会变成故障源。
- **性能要求**：Transformer 对**每个类**都调用（一个应用几千上万类），**必须极快**（微秒级）：先做字符串前缀过滤，再解析字节码；**不要在里面做 IO 或重计算**。
- 易错点：Transformer 抛异常（应用起不来）；不过滤类名（增强 JDK 类导致诡异问题）；在 transformer 里做 IO（启动慢十倍）；忘记 `canRetransform` 标志（后续无法重新增强）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.List;

public class InstrumentationDemo {
    /** 简化的 ClassFileTransformer：入参类名与字节码，返回 null 或新字节码 */
    interface Transformer {
        byte[] transform(String className, byte[] classBytes);
    }

    /** 简化版 Instrumentation：注册 transformer 链，按顺序应用 */
    static class MiniInstrumentation {
        final List<Transformer> transformers = new ArrayList<>();
        final List<String> loadLog = new ArrayList<>();

        void addTransformer(Transformer t) {
            transformers.add(t);
            System.out.println("  注册 transformer：" + t.getClass().getSimpleName());
        }

        /** 模拟类加载：依次过 transformer，任一返回非 null 则替换字节码 */
        byte[] loadClass(String className, byte[] bytes) {
            byte[] current = bytes;
            for (Transformer t : transformers) {
                try {
                    byte[] next = t.transform(className, current);
                    if (next != null) {
                        current = next;
                        loadLog.add(className + " 被 " + t.getClass().getSimpleName() + " 增强");
                    }
                } catch (RuntimeException e) {
                    loadLog.add(className + " 增强失败 -> 降级为原样加载（" + e.getMessage() + "）");
                }
            }
            return current;
        }
    }

    /** 目标包过滤：只增强 com/heima/ 下的类（性能与安全双要求） */
    static class PackageFilterTransformer implements Transformer {
        final String prefix;

        PackageFilterTransformer(String prefix) {
            this.prefix = prefix;
        }

        public byte[] transform(String className, byte[] classBytes) {
            if (!className.startsWith(prefix)) {
                return null;                        // 快速否定，不做任何解析
            }
            return (">>" + new String(classBytes)).getBytes();   // 简化：标记增强
        }
    }

    /** 会抛异常的 transformer：验证降级 */
    static class BuggyTransformer implements Transformer {
        public byte[] transform(String className, byte[] classBytes) {
            if (className.contains("Broken")) {
                throw new IllegalStateException("模拟增强失败");
            }
            return null;
        }
    }

    public static void main(String[] args) {
        MiniInstrumentation inst = new MiniInstrumentation();
        inst.addTransformer(new PackageFilterTransformer("com/heima/"));
        inst.addTransformer(new BuggyTransformer());

        System.out.println();
        System.out.println("【类加载模拟】");
        String[][] classes = {
                {"com/heima/OrderService", "class OrderService"},
                {"java/lang/String", "class String"},
                {"org/spring/Bean", "class Bean"},
                {"com/heima/BrokenService", "class BrokenService"}};
        for (String[] c : classes) {
            byte[] loaded = inst.loadClass(c[0], c[1].getBytes());
            System.out.printf("  %-26s -> %s%n", c[0],
                    new String(loaded).startsWith(">>") ? "已增强" : "原样");
        }
        System.out.println();
        System.out.println("【加载日志】");
        inst.loadLog.forEach(l -> System.out.println("  " + l));
        System.out.println();
        System.out.println("  要点：命名空间用斜杠；先前缀过滤；异常降级为原样加载");
    }
}
```

**课后练习**
1. 实现完整 Transformer 链：包过滤 + 计时 + 异常降级，用 5 个类（含 1 个会抛异常的）验证增强与降级。
2. 用真实 Agent API 写一个统计"每个包加载了多少类"的 Agent，挂到一个 Spring Boot 应用观察输出。
3. 思考题：为什么 Transformer 必须对异常做降级？从"Agent 是旁路组件"与"增强失败不应影响主流程"分析。

---

### 66-03 字节码基础：Class 文件结构

**知识点**
- **Class 文件是 JVM 的"可执行格式"**：固定以魔数 `0xCAFEBABE` 开头，随后是版本号、常量池、访问标志、字段表、方法表、属性表——**一切 Java 代码（含 Kotlin/Scala）最终都是这个结构**。
- **文件头的关键字段**：`magic`（4 字节，0xCAFEBABE，用于快速校验）、`minor_version`/`major_version`（**major 决定 JDK 版本兼容**：52=Java 8、61=Java 17、65=Java 21）、`constant_pool_count`（常量池条目数）、`constant_pool`（字符串/类名/方法签名的去重表）。
- **方法表的结构**：`access_flags`（public/static 等）、`name_index`、`descriptor_index`（描述符如 `(I)Ljava/lang/String;`）、`attributes`（**其中 `Code` 属性里有真正的指令序列**）——**字节码增强改的就是 Code 属性里的指令**。
- **栈帧与指令**：JVM 是基于栈的虚拟机，指令如 `aload_0`（压入 this）、`invokevirtual`（调用虚方法）、`ireturn`（返回 int）——**插桩就是在这串指令里"插入新指令"**。
- **版本兼容的实践意义**：**Agent 用高版本 JDK 编译，被挂载的应用用低版本运行会 `UnsupportedClassVersionError`**——**Agent 要用目标应用的最低版本编译**（或提供多版本）。
- 易错点：以为改源码就能改行为（运行时是字节码）；Agent 与目标应用 JDK 版本不匹配；混淆过的类（方法名被改）导致切点匹配失败；忽略常量池的索引引用（插指令要改索引）。

**示例代码**
```java
import java.io.DataInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class ClassFileStructure {
    record ClassHeader(int magic, int minor, int major, int constantPoolCount) {
        String jdkVersion() {
            return switch (major) {
                case 52 -> "Java 8";
                case 55 -> "Java 11";
                case 61 -> "Java 17";
                case 65 -> "Java 21";
                case 69 -> "Java 25";
                default -> "未知（major=" + major + "）";
            };
        }
    }

    /** 解析 Class 文件头：魔数 + 版本 + 常量池计数 */
    static ClassHeader parseHeader(byte[] bytes) {
        int magic = ((bytes[0] & 0xFF) << 24) | ((bytes[1] & 0xFF) << 16)
                | ((bytes[2] & 0xFF) << 8) | (bytes[3] & 0xFF);
        int minor = ((bytes[4] & 0xFF) << 8) | (bytes[5] & 0xFF);
        int major = ((bytes[6] & 0xFF) << 8) | (bytes[7] & 0xFF);
        int cpCount = ((bytes[8] & 0xFF) << 8) | (bytes[9] & 0xFF);
        return new ClassHeader(magic, minor, major, cpCount);
    }

    static void inspectOwnClass() throws Exception {
        System.out.println("【解析本类自身的 Class 文件】");
        String resource = ClassFileStructure.class.getSimpleName() + ".class";
        try (InputStream in = ClassFileStructure.class.getResourceAsStream(resource)) {
            if (in == null) {
                System.out.println("  未找到 class 资源（可能运行在特殊环境）");
                return;
            }
            byte[] bytes = in.readAllBytes();
            ClassHeader h = parseHeader(bytes);
            System.out.printf("  文件大小=%d 字节%n", bytes.length);
            System.out.printf("  magic=0x%08X（期望 0xCAFEBABE）-> %s%n", h.magic(),
                    h.magic() == 0xCAFEBABE ? "合法" : "非法");
            System.out.printf("  版本=%d.%d -> %s%n", h.major(), h.minor(), h.jdkVersion());
            System.out.printf("  常量池条目数=%d%n", h.constantPoolCount());
        }
        System.out.println();
    }

    static void inspectFake() {
        System.out.println("【非法/异常 Class 文件识别】");
        List<byte[]> samples = new ArrayList<>();
        samples.add(new byte[]{(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE, 0, 0, 0, 65, 0, 20});
        samples.add(new byte[]{0x7F, (byte) 0x45, 0x4C, 0x46, 0, 0, 0, 65, 0, 20});
        for (byte[] s : samples) {
            ClassHeader h = parseHeader(s);
            System.out.printf("  magic=0x%08X -> %s%n", h.magic(),
                    h.magic() == 0xCAFEBABE ? "Class 文件（版本 " + h.jdkVersion() + "）"
                            : "非 Class 文件（拒绝加载）");
        }
        System.out.println();
        System.out.println("  用途：Agent 在 transform 里用魔数快速判断输入是否为合法字节码");
    }

    public static void main(String[] args) throws Exception {
        inspectOwnClass();
        inspectFake();
        System.out.println("  JDK 21 编译产物 major=65；Agent 挂到 JDK 17 应用需用 17 编译");
    }
}
```

**课后练习**
1. 扩展解析器：跳过常量池并读出 `access_flags`、方法数量与每个方法的名字（需要解析常量池的 UTF8 条目）。
2. 用 `javap -v` 对照你解析出的 Class 文件头信息，验证 magic/version/常量池计数一致。
3. 思考题：为什么 Class 文件要以魔数开头？从"格式快速识别"与"错误信息友好"两个角度分析。

---

### 66-04 ASM：读取与修改字节码

**知识点**
- **ASM 是"直接操作字节码"的底层库**（Spring/CGLIB/很多 APM 都用它）：核心是 **Visitor 模式**——`ClassReader` 逐条"读"字节码并回调 `ClassVisitor`，`ClassWriter` 逐条"写"出新字节码——**像 SAX 解析 XML 一样流式处理**（31 章访问者模式的经典应用）。
- **读改写的三段式**：`ClassReader`（源）+ `ClassVisitor`（你的修改逻辑）+ `ClassWriter`（目标）——**在 `visitMethod` 里返回自定义的 `MethodVisitor`，在 `visitCode`/`visitInsn` 里插入指令**。
- **插入埋点的标准姿势**：方法入口插 `System.nanoTime()` 并存入局部变量，`visitInsn` 遇到 `RETURN` 系列时插入"计算耗时 + 上报"——**修改的是指令序列，源码完全不变**。
- **ASM 的代价与风险**：API 极底层（要懂 JVM 指令与栈帧）、**版本兼容**（不同 Class 版本要匹配 ASM 版本）、**改错一个偏移就 `VerifyError`**——**生产建议用更高层封装（ByteBuddy 66-05）**。
- **与 60-08 的呼应**：SkyWalking Agent 的字节码增强就是"插件按框架匹配 -> ASM 改字节码 -> 插桩"——**本章把这层拆开看**。
- 易错点：忘记调 `visitEnd`（字节码不完整）；`ClassWriter` 用错 COMPUTE_FRAMES（栈帧计算失败）；改字节码后没重新计算 maxStack/maxLocals（VerifyError）；ASM 版本与 Class 版本不匹配。

**示例代码**
```java
import java.util.ArrayList;
import java.util.List;

public class AsmDemo {
    /** 极简指令模型：模拟 ASM 的 Visitor 流式处理 */
    record Insn(String op, String operand) {
        @Override
        public String toString() {
            return operand.isEmpty() ? op : op + " " + operand;
        }
    }

    /** ClassReader：逐条遍历指令并回调 visitor */
    static class Reader {
        final List<Insn> code;

        Reader(List<Insn> code) {
            this.code = code;
        }

        void accept(java.util.function.Consumer<Insn> visitor) {
            code.forEach(visitor);
        }
    }

    /** MethodVisitor 模拟：在入口与 return 处插桩 */
    static class TimingVisitor {
        final List<Insn> out = new ArrayList<>();
        int localVar = 1;

        void visitCode() {
            out.add(new Insn("invokestatic", "System.nanoTime"));
            out.add(new Insn("lstore", String.valueOf(localVar)));
            out.add(new Insn("// 方法开始计时", ""));
        }

        void visitInsn(Insn insn) {
            if (insn.op().endsWith("return")) {
                out.add(new Insn("ldc", "orderService.query"));
                out.add(new Insn("lload", String.valueOf(localVar)));
                out.add(new Insn("invokestatic", "System.nanoTime"));
                out.add(new Insn("lsub", ""));
                out.add(new Insn("invokestatic", "Metric.record"));
                out.add(new Insn("// 上报耗时", ""));
            }
            out.add(insn);
        }

        void add(Insn insn) {
            out.add(insn);
        }
    }

    /** 模拟对一个方法做耗时插桩 */
    static List<Insn> instrument(List<Insn> original) {
        TimingVisitor mv = new TimingVisitor();
        mv.visitCode();
        new Reader(original).accept(mv::visitInsn);
        return mv.out;
    }

    public static void main(String[] args) {
        List<Insn> original = List.of(
                new Insn("aload_0", ""),
                new Insn("invokevirtual", "OrderDao.select"),
                new Insn("areturn", ""));
        System.out.println("【原始指令序列】");
        original.forEach(i -> System.out.println("  " + i));

        System.out.println();
        System.out.println("【增强后（入口计时 + return 前上报）】");
        instrument(original).forEach(i -> System.out.println("  " + i));

        System.out.println();
        System.out.println("【关键点】");
        List<String> notes = List.of(
                "插桩位置：visitCode 之后（方法入口）、RETURN 之前",
                "局部变量编号：要避开已有变量（本例 lstore 1）",
                "改完后 ClassWriter 需重算 maxStack/maxLocals，否则 VerifyError",
                "ASM 版本要与 Class 文件版本匹配（major 65 需较新 ASM）");
        notes.forEach(n -> System.out.println("  - " + n));
    }
}
```

**课后练习**
1. 扩展插桩：为非 void 方法与 void 方法分别插入计时（`areturn` 与 `return` 两条路径），验证两种返回都上报。
2. 用真实 ASM 写一个"给所有 public 方法插入耗时统计"的 Agent，挂到示例程序上验证输出。
3. 思考题：为什么插桩要考虑"局部变量编号"？从"字节码里的局部变量表槽位"与"栈帧大小计算"分析。

---

### 66-05 ByteBuddy：更友好的增强

**知识点**
- **ByteBuddy 是 ASM 之上的高层封装**：用**声明式 API** 描述"要拦截什么、怎么增强"（`ElementMatchers` 匹配方法、`Advice` 插入前后逻辑、`AgentBuilder` 一键装 Agent）——**不需要手写指令**，代价是少量性能开销与"抽象泄漏"（复杂场景仍要懂字节码）。
- **三个核心抽象**：**Matcher**（`named("query").and(takesArguments(1))`——匹配目标）、**Advice**（`@Advice.OnMethodEnter/Exit`——在方法前后插入 Java 代码，**编译期生成字节码**）、**AgentBuilder**（`-javaagent` 或 attach 的装配）。
- **Advice 的三种注入点**：**Enter**（入口，可修改参数）、**Exit**（出口，可读取返回值/异常、替换返回值）、**Field/Method 拦截**（`@SuperCall` 调用原方法）——**覆盖了 90% 的埋点需求**。
- **Advice 与 Interceptor 的区别**：`Advice` 是"内联插入"（无额外对象分配，性能最好，但不能改方法签名）；`MethodDelegation` 是"委托调用"（灵活，有开销）——**埋点优先 Advice**。
- **与 66-04 的对比**：ASM 手写指令（灵活但易错）vs ByteBuddy Advice（安全高效但有抽象成本）——**生产 Agent 通常用 ByteBuddy，复杂字节码操作才下沉到 ASM**。
- 易错点：Matcher 写得过宽（拦截了不该拦的类）；Advice 里抛异常（影响业务方法）；Exit 里没判断异常（吞掉业务异常）；Agent 依赖与目标应用冲突（类加载器隔离）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class ByteBuddyDemo {
    /** 模拟 Matcher：按类名/方法名/参数个数匹配 */
    record Matcher(String classPrefix, String methodName, int argCount) {
        boolean matches(String cls, String method, int args) {
            return (classPrefix.isEmpty() || cls.startsWith(classPrefix))
                    && (methodName.isEmpty() || methodName.equals(method))
                    && (argCount < 0 || argCount == args);
        }
    }

    /** 模拟 Advice：enter/exit 两段逻辑 */
    interface Advice {
        void enter(String cls, String method, Object[] args);

        void exit(String cls, String method, Object result, Throwable error);
    }

    /** 模拟被增强的业务方法 */
    static class Biz {
        String query(String orderId) {
            if (orderId == null) {
                throw new IllegalArgumentException("orderId 不能为空");
            }
            return "order-" + orderId;
        }

        int count(int a, int b) {
            return a + b;
        }
    }

    /** 简易 AgentBuilder：按 matcher 包装方法，Advice 前后执行 */
    static class MiniAgent {
        final Matcher matcher;
        final Advice advice;

        MiniAgent(Matcher matcher, Advice advice) {
            this.matcher = matcher;
            this.advice = advice;
        }

        Function<Object[], Object> wrap(String cls, String method,
                                       Function<Object[], Object> target) {
            if (!matcher.matches(cls, method, -1)) {
                return target;
            }
            return args -> {
                advice.enter(cls, method, args);
                Object result = null;
                Throwable error = null;
                try {
                    result = target.apply(args);
                    return result;
                } catch (RuntimeException e) {
                    error = e;
                    throw e;
                } finally {
                    advice.exit(cls, method, result, error);
                }
            };
        }
    }

    public static void main(String[] args) {
        List<String> log = new ArrayList<>();
        Advice loggingAdvice = new Advice() {
            public void enter(String cls, String method, Object[] args) {
                log.add("ENTER " + cls + "." + method + " args=" + java.util.Arrays.toString(args));
            }

            public void exit(String cls, String method, Object result, Throwable error) {
                log.add("EXIT  " + cls + "." + method + " result=" + result
                        + (error == null ? "" : " error=" + error.getClass().getSimpleName()));
            }
        };
        MiniAgent agent = new MiniAgent(new Matcher("Biz", "query", 1), loggingAdvice);

        Biz biz = new Biz();
        var query = agent.wrap("Biz", "query", callArgs -> biz.query((String) callArgs[0]));
        var count = agent.wrap("Biz", "count",
                callArgs -> biz.count((Integer) callArgs[0], (Integer) callArgs[1]));

        System.out.println("【被匹配的方法】");
        System.out.println("  query(9001) -> " + query.apply(new Object[]{"9001"}));
        System.out.println();
        System.out.println("【未被匹配的方法（不增强）】");
        System.out.println("  count(1,2) -> " + count.apply(new Object[]{1, 2}));
        System.out.println();
        System.out.println("【异常路径：exit 也能拿到异常】");
        try {
            query.apply(new Object[]{null});
        } catch (IllegalArgumentException e) {
            System.out.println("  业务异常按原样抛出：" + e.getMessage());
        }
        System.out.println();
        System.out.println("【Advice 日志】");
        log.forEach(l -> System.out.println("  " + l));
        System.out.println();
        Map<String, String> notes = new LinkedHashMap<>();
        notes.put("Matcher", "按类名/方法名/参数匹配，过宽会误伤（66-02 的包过滤）");
        notes.put("Advice", "enter 可读参数、exit 可读返回值与异常，不改变方法签名");
        notes.put("异常", "Advice 自身不能抛异常，否则污染业务方法");
        notes.forEach((k, v) -> System.out.printf("  %-8s %s%n", k, v));
    }
}
```

**课后练习**
1. 用真实 ByteBuddy 写一个 Agent：给指定包内所有 public 方法插入耗时统计，挂到示例程序验证。
2. 实现 Advice 的"参数修改"（enter 里改参数）与"返回值替换"（exit 里改返回），验证两者对业务的影响。
3. 思考题：为什么"Advice 内联插入"比"MethodDelegation 委托调用"性能好？从对象分配与调用栈开销分析。

---

### 66-06 无侵入埋点：方法耗时与链路

**知识点**
- **无侵入埋点的价值**：业务代码零改动就获得"每个方法的耗时/异常/调用链"——**这是 APM 的核心能力**（42-07 的自动探针），也是"老系统接入可观测"的唯一低成本路径（改造代码的成本远高于挂 Agent）。
- **埋点的三要素**：**方法耗时**（enter 记开始时间，exit 算差）、**异常捕获**（exit 拿 Throwable，上报异常类型与栈）、**调用链**（enter 把 `traceId/spanId` 压入 ThreadLocal，exit 弹出——**跨线程要传递** 42-07 的断裂点）。
- **切点选择的工程权衡**：**全量方法**（开销大、噪音多）、**按包/注解过滤**（推荐：只增强 `@Trace` 或 controller/service 层）、**按框架匹配**（Spring Controller、JDBC、Redis 客户端——APM 插件式设计）。
- **开销控制**：单次增强的开销约**几十纳秒**（纳秒计时 + ThreadLocal 读写）——**但乘以亿级调用就不可忽略**；对策：**采样**（按比例记录，42-08）、**异步上报**（队列 + 批量，不能同步打日志）、**关闭开关**（出问题可整体禁用）。
- **与 42 章的关系**：42 章讲"三支柱与 trace 模型"，本章讲"trace 数据是怎么被采集的"——**Agent 是数据的生产者**。
- 易错点：埋点里同步写日志/网络（性能直接崩）；ThreadLocal 不清理（线程池污染）；全量增强（开销爆炸）；无采样（存储爆炸）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TracingAgent {
    /** 跨线程的追踪上下文（42-07 的 ThreadLocal 方案） */
    static final ThreadLocal<String> TRACE = new ThreadLocal<>();
    /** 采集到的 Span */
    static final List<String> spans = new ArrayList<>();
    /** 采样率：1/100 */
    static final int SAMPLE_EVERY = 100;
    static int callSeq = 0;

    static boolean sampled() {
        callSeq++;
        return callSeq % SAMPLE_EVERY == 0;
    }

    /** 被增强的业务方法（模拟） */
    static String queryOrder(String orderId) {
        String traceId = TRACE.get();
        boolean sample = sampled();
        long start = System.nanoTime();
        try {
            if (orderId.equals("bad")) {
                throw new IllegalStateException("order not found");
            }
            return "order-" + orderId;
        } finally {
            long costNs = System.nanoTime() - start;
            if (sample) {
                spans.add(String.format("span queryOrder trace=%s cost=%dns", traceId, costNs));
            }
        }
    }

    static void handleRequest(String requestId) {
        String traceId = "trace-" + requestId;
        TRACE.set(traceId);
        try {
            queryOrder("9001");
            queryOrder("9002");
            try {
                queryOrder("bad");
            } catch (IllegalStateException e) {
                spans.add("error queryOrder trace=" + traceId + " " + e.getMessage());
            }
        } finally {
            TRACE.remove();                    // 必须清理（线程池复用）
        }
    }

    static void overheadAnalysis() {
        System.out.println("【单次埋点开销的放大效应】");
        long perCallNs = 80;                   // 纳秒计时 + ThreadLocal + 判断
        for (long qps : new long[]{1_000, 100_000, 1_000_000}) {
            double cpuMsPerSec = qps * perCallNs / 1_000_000.0;
            System.out.printf("  QPS=%8d -> 埋点 CPU 占用 %.1fms/s（%.1f%% 单核）%n",
                    qps, cpuMsPerSec, cpuMsPerSec / 10);
        }
        System.out.println("  对策：按包过滤 + 采样 + 异步上报（42-08）");
        System.out.println();
    }

    public static void main(String[] args) {
        System.out.println("【100 个请求（采样 1/100）】");
        for (int i = 0; i < 100; i++) {
            handleRequest(String.valueOf(i));
        }
        System.out.println("  采集到 span/error 条数：" + spans.size());
        spans.forEach(s -> System.out.println("  " + s));
        System.out.println();
        System.out.println("【ThreadLocal 清理验证】");
        System.out.println("  主线程 TRACE 已清理：" + (TRACE.get() == null));

        System.out.println();
        overheadAnalysis();
        System.out.println("【切点选择对比】");
        Map<String, String> cut = new LinkedHashMap<>();
        cut.put("全量方法", "开销最大、噪音最多（不推荐）");
        cut.put("按包/注解", "@Trace 标注的 service 层（推荐）");
        cut.put("按框架", "Controller/JDBC/Redis 客户端（APM 插件式）");
        cut.forEach((k, v) -> System.out.printf("  %-8s %s%n", k, v));
    }
}
```

**课后练习**
1. 实现完整埋点：耗时 + 异常 + traceId 传递 + 采样 + 异步队列，用 1000 次调用统计开销与采样命中率。
2. 复现"ThreadLocal 未清理"的问题：线程池复用下 traceId 串号，验证清理后修复。
3. 思考题：为什么埋点不能同步上报？从"业务线程被 IO 阻塞"与"上报失败影响业务"两个角度分析。

---

### 66-07 Agent 的边界：不能做什么

**知识点**
- **JVM 的 redefinition 限制是硬边界**：**不能改方法签名**（参数/返回值）、**不能改字段类型**、**不能删除方法/字段**、**不能改父类与接口列表**——**Agent 是"增强"，不是"重构"**。
- **能不能加方法/字段？** 标准 `Instrumentation` **不支持新增方法或字段**（只能改已有方法的实现、改常量池引用）；**ByteBuddy 的 `redefine` 可有限新增**（通过重写类结构，但不是所有场景都安全，且需 `Can-Redefine-Classes`）。
- **不能拦截的场景**：**JDK 内部类**（`java.*` 常被限制增强，且极危险）、**native 方法**（无字节码可改）、**已被 JIT 编译的热点代码**（retransform 会触发重新编译，但时机与开销不可控）、**final/private 方法**（可增强但没有虚调用语义）。
- **Agent 与技术选型的边界**：**业务逻辑增强用 AOP（60-04）**；**跨切面（日志/监控/安全）才用 Agent**——**Agent 是"最后手段"，因为它不可见、难调试、有版本耦合**。
- **与 59/60 的呼应**：59-10 的"表结构演进要重建"、60-03 的"Bean 生命周期不可逆"——**任何"运行时修改"都有结构约束**，Agent 也不例外。
- 易错点：以为 Agent 能"任意改代码"（redefinition 限制）；尝试增强 JDK 类（风险极高且常失败）；用 Agent 替代正常设计（不可见的技术债）；忽略 JIT 与 retransform 的交互（改完行为可能不立即生效）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AgentLimits {
    record Capability(String change, boolean allowed, String reason) {
    }

    static void matrix() {
        System.out.println("【redefinition 能力矩阵】");
        List<Capability> caps = List.of(
                new Capability("修改方法体（插桩/替换实现）", true, "最常用，APM 全靠它"),
                new Capability("修改常量池引用（如换字符串）", true, "改常量值可行"),
                new Capability("新增方法", false, "标准 Instrumentation 不支持"),
                new Capability("新增字段", false, "同上"),
                new Capability("修改方法签名", false, "调用点已链接，无法改"),
                new Capability("修改字段类型", false, "内存布局已确定"),
                new Capability("删除方法/字段", false, "同上"),
                new Capability("修改父类/接口列表", false, "类型体系不可变"));
        for (Capability c : caps) {
            System.out.printf("  %-32s %s  原因：%s%n", c.change(),
                    c.allowed() ? "允许" : "禁止", c.reason());
        }
        System.out.println();
    }

    static void notEnhanceable() {
        System.out.println("【难以增强的场景】");
        Map<String, String> cases = new LinkedHashMap<>();
        cases.put("JDK 内部类", "java.* 增强极危险，多数环境禁止");
        cases.put("native 方法", "没有字节码，无处插桩");
        cases.put("已被 JIT 编译的热点", "retransform 需重新编译，时机与开销不可控");
        cases.put("final/private 方法", "可改字节码，但无虚调用语义，切点匹配易错");
        cases.forEach((k, v) -> System.out.printf("  %-16s %s%n", k, v));
        System.out.println();
    }

    static void chooseTool() {
        System.out.println("【工具选型边界】");
        String[][] rows = {
                {"业务横切逻辑（日志/事务）", "Spring AOP（60-04）", "声明式、可调试、无版本耦合"},
                {"老系统零改造接入监控", "Java Agent（本章）", "不可见、有耦合，但成本最低"},
                {"热修复线上 bug", "Agent + redefine（66-08）", "应急手段，事后必须回归代码"},
                {"纯诊断（看栈/看字节码）", "jstack/javap/arthas", "不改变行为，最安全"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-24s -> %-28s %s%n", r[0], r[1], r[2]);
        }
        System.out.println();
        System.out.println("  原则：能改代码就别用 Agent；必须零侵入时才用，并把它当「基础设施」治理（66-10）");
    }

    public static void main(String[] args) {
        matrix();
        notEnhanceable();
        chooseTool();
    }
}
```

**课后练习**
1. 用真实 Agent 尝试"给类新增一个方法"，记录报错信息并确认限制（用 `javassist`/ByteBuddy redefine 也试一次对比）。
2. 列出你项目里"适合 Agent"与"应该改代码"的场景各 3 个，说明判据（侵入性 vs 可维护性）。
3. 思考题：为什么"不能改方法签名"是 JVM 的结构性限制？从调用点已编译为固定描述符的角度分析。

---

### 66-08 热修复与动态诊断

**知识点**
- **热修复（Hot Swap / Hot Fix）的两种实现**：**JVM 自带 HotSwap**（Debug 模式改方法体，重启断点生效）与 **Agent redefine**（运行时替换字节码，生产可用）——**后者是线上应急的主流**，但**只能改方法体**（66-07 的限制）。
- **热修复的适用与不适用**：**适用**（改一个 if 条件、加日志、降级开关、空指针保护）；**不适用**（新增字段/方法、改签名、改类结构）——**后者只能发版**。
- **动态诊断的常见动作**：**看方法入参与返回值**（不打断点）、**看某个方法的调用栈与耗时**、**看静态字段值**、**触发一次 GC / 打印线程栈**——**arthas 类工具就是"Agent + 命令"**。
- **热修复的风险**：**改错就是二次故障**（字节码错误 -> VerifyError -> 类加载失败）；**无版本管理**（改了什么没人知道）；**修复被遗忘**（下次发版回退成旧代码，问题复发）——**所以热修复必须：灰度（一台机器先验证）、记录（修了什么、何时、谁）、跟进（下次发版合入源码）**。
- **与 41 章的关系**：热修复是"绕过发布流程的应急通道"——**41-10 的"紧急发布也要记录"在这里同样适用**（热修复台账）。
- 易错点：热修复当常规手段（技术债）；改完不验证就全量推（二次故障）；不记录（下次发版丢修复）；在 Agent 里做复杂逻辑（易错）。

**示例代码**
```java
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class HotFixDemo {
    /** 模拟类的"可替换方法体"：版本 -> 行为 */
    static final Map<Integer, java.util.function.Function<String, String>> behaviors =
            new LinkedHashMap<>();
    static int activeVersion = 1;
    static final List<String> audit = new java.util.ArrayList<>();

    static {
        behaviors.put(1, arg -> arg == null ? "NPE（旧代码）" : "order-" + arg);
        behaviors.put(2, arg -> arg == null ? "order-unknown（热修复：空值兜底）" : "order-" + arg);
    }

    /** 被增强的方法：调用时取当前生效的行为 */
    static String query(String orderId) {
        return behaviors.get(activeVersion).apply(orderId);
    }

    /** 动态诊断：不修改行为，只观察 */
    static void diagnose(String orderId) {
        System.out.println("  [诊断] query 入参=" + orderId
                + " 当前生效版本=v" + activeVersion);
    }

    /** 热修复：灰度 -> 全量，带审计 */
    static void hotfix(int targetVersion, boolean grayFirst) {
        if (!behaviors.containsKey(targetVersion)) {
            System.out.println("  热修复失败：版本不存在");
            return;
        }
        if (grayFirst) {
            System.out.println("  灰度：先切 v" + targetVersion + " 到 1 台实例，观察 10 分钟");
            audit.add("灰度 v" + targetVersion + "（1 台实例）");
        }
        activeVersion = targetVersion;
        audit.add("全量切换 v" + targetVersion);
        System.out.println("  已切换到 v" + targetVersion + "（全量）");
    }

    public static void main(String[] args) {
        System.out.println("【热修复前】");
        diagnose("9001");
        System.out.println("  正常入参：" + query("9001"));
        System.out.println("  异常入参：" + query(null));

        System.out.println();
        System.out.println("【热修复：空值兜底（改方法体，不改签名）】");
        hotfix(2, true);
        System.out.println("  正常入参：" + query("9001"));
        System.out.println("  异常入参：" + query(null));

        System.out.println();
        System.out.println("【热修复台账】");
        audit.forEach(a -> System.out.println("  " + a));

        System.out.println();
        System.out.println("【热修复的三条纪律】");
        Map<String, String> rules = new LinkedHashMap<>();
        rules.put("灰度", "先在 1 台实例验证，确认无 VerifyError 与业务异常");
        rules.put("台账", "记录修了什么/何时/谁/影响范围（41-10 审计）");
        rules.put("跟版", "下次发版必须把修复合入源码，否则回退即复发");
        rules.forEach((k, v) -> System.out.printf("  %-4s %s%n", k, v));
        System.out.println();
        System.out.println("【不能热修复的场景】新增字段/方法、改签名、改类结构 -> 只能发版（66-07）");
    }
}
```

**课后练习**
1. 用 Agent implement 一次真实热修复：给一个空指针方法加兜底，验证修复前后行为与"不改签名"的约束。
2. 设计"热修复台账"模板（时间/版本/修改人/影响类方法/灰度范围/合入发版号），并演练一次完整流程。
3. 思考题：为什么"热修复不做灰度"是危险操作？从字节码错误导致类加载失败（应用起不来）的后果分析。

---

### 66-09 Agent 的性能与稳定性

**知识点**
- **Agent 的两类开销**：**加载期开销**（Transformer 对每个类执行一次：解析字节码 + 改写 + 重算栈帧——**类多时拖慢启动**）、**运行期开销**（插入的埋点代码：计时/ThreadLocal/上报——**调用量大时吃 CPU**）。
- **加载期优化的三条**：**快速过滤**（先字符串前缀判断，命不中直接返回 null）、**惰性增强**（只增指定类，不全量扫）、**缓存**（同一类只解析一次）——**一个应用上万类，每次多 1ms 就是 10 秒启动延迟**。
- **运行期优化的三条**：**采样**（42-08）、**异步上报**（队列 + 批量，66-06）、**埋点裁剪**（只记关键方法，不记全部）。
- **稳定性是 Agent 的第一原则**：**增强失败必须降级**（原样加载，66-02）；**上报失败必须静默**（不能抛到业务）；**Agent 自身要能被关闭**（配置开关 + 动态卸载 transformer）；**内存要与业务隔离**（Agent 的队列/缓存不能无界）。
- **Agent 的故障模式**：**类加载失败**（应用起不来，最严重）、**VerifyError**（改错字节码）、**性能劣化**（全量增强）、**内存泄漏**（Agent 队列无界）、**版本冲突**（依赖的 ASM/ByteBuddy 与应用冲突）。
- 易错点：Transformer 里做重量级操作（启动慢）；埋点同步写日志（吞吐暴跌）；Agent 依赖不打 shade（类冲突）；上报队列无界（OOM）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.List;

public class AgentPerformance {
    /** 加载期：对比"全量解析"与"前缀过滤"的成本 */
    static void loadCost() {
        System.out.println("【加载期成本：1 万个类】");
        long classes = 10_000;
        long fullParseNs = 200_000;             // 每类完整解析 0.2ms
        long filterNs = 50;                     // 前缀判断 50ns
        int targetRatio = 5;                    // 只有 5% 的类需要增强
        double fullMs = classes * fullParseNs / 1_000_000.0;
        double filteredMs = classes * filterNs / 1_000_000.0
                + classes * targetRatio / 100.0 * fullParseNs / 1_000_000.0;
        System.out.printf("  无过滤：%.1f ms%n", fullMs);
        System.out.printf("  前缀过滤（5%% 命中）：%.1f ms（快 %.0f 倍）%n",
                filteredMs, fullMs / filteredMs);
        System.out.println("  结论：先字符串判断，命不中直接返回 null");
        System.out.println();
    }

    /** 运行期：埋点开销与采样 */
    static void runtimeCost() {
        System.out.println("【运行期成本与采样】");
        long callsPerSec = 500_000;
        long perCallNs = 80;
        double noSamplePct = callsPerSec * perCallNs / 1e7;      // 单核百分比
        double sampledPct = noSamplePct / 100;
        System.out.printf("  50 万 QPS 全量埋点：约 %.0f%% 单核（不可忽视）%n", noSamplePct);
        System.out.printf("  1%% 采样后：约 %.1f%% 单核（可接受）%n", sampledPct);
        System.out.println("  代价：采样后只能看宏观指标，个案排查靠开关临时全量");
        System.out.println();
    }

    /** 稳定性四道闸 */
    static void stabilityGates() {
        System.out.println("【Agent 稳定性四道闸】");
        List<String> gates = List.of(
                "增强失败降级：transformer 包 try-catch，失败返回 null（原样加载）",
                "上报失败静默：队列满丢弃并计数，绝不抛到业务线程",
                "可关闭：配置开关 + removeTransformer，出问题能一键停用",
                "资源有界：队列/缓存设上限，Agent 内存与业务隔离");
        gates.forEach(g -> System.out.println("  - " + g));
        System.out.println();

        System.out.println("【常见故障与后果】");
        List<String[]> faults = new ArrayList<>();
        faults.add(new String[]{"类加载失败", "应用起不来（最严重）", "增强降级 + 灰度"});
        faults.add(new String[]{"VerifyError", "类定义失败", "改完字节码先本地验证"});
        faults.add(new String[]{"性能劣化", "吞吐下降/延迟升高", "过滤 + 采样 + 异步"});
        faults.add(new String[]{"内存泄漏", "OOM", "队列有界 + 监控"});
        faults.add(new String[]{"类冲突", "NoSuchMethodError", "依赖 shade + 隔离类加载器"});
        for (String[] f : faults) {
            System.out.printf("  %-12s %-22s 对策：%s%n", f[0], f[1], f[2]);
        }
    }

    public static void main(String[] args) {
        loadCost();
        runtimeCost();
        stabilityGates();
        System.out.println();
        System.out.println("  铁律：Agent 是旁路，出问题先停 Agent，再查业务");
    }
}
```

**课后练习**
1. 量化你项目里"1 万个类的加载时间"，对比"无过滤增强"与"前缀过滤增强"的启动耗时差异（可用模拟或实测）。
2. 实现"上报队列有界 + 丢弃计数 + 告警"，注入高并发验证业务吞吐不受影响。
3. 思考题：为什么"Agent 出问题先停 Agent"？从"旁路组件"与"故障归因"两个角度分析。

---

### 66-10 Agent 安全与治理

**知识点**
- **Agent 是"最高权限的旁路"**：它能读改任意类、能拿到所有参数（含敏感数据 36 章）、能访问 JVM 内部——**一旦被滥用或被攻破，等于应用完全失守**。
- **三类安全风险**：**权限过大**（Agent 无沙箱，等同应用进程权限）、**数据泄露**（埋点采集了密码/身份证，36-09）、**供应链**（引入来路不明的 Agent jar = 引入后门，36-10 依赖治理）。
- **治理的五条**：**来源可信**（只用自研或审计过的 Agent）、**最小增强**（只增强需要的类）、**数据脱敏**（埋点参数脱敏后再上报 68-03）、**可关闭**（配置开关 + 运行时卸载）、**审计**（Agent 装了哪些、增强了什么、上报到哪）。
- **与 36 章的对应**：Agent 是"运行时注入"，**与依赖注入/反序列化同级的安全面**——**生产环境禁止动态 attach 未知 Agent**（`-Djdk.attach.allowAttachSelf` 等开关要收紧）。
- **合规视角**：Agent 采集的数据可能含个人信息——**要符合 68 章的分类分级与最小必要原则**；**跨境部署的 Agent 上报数据不能出境**（68-06）。
- 易错点：生产环境允许任意 attach（安全后门）；埋点采集全量参数（敏感泄露）；Agent jar 来源不明（供应链风险）；无审计（不知道装了哪些 Agent）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AgentSecurity {
    /** 埋点参数的脱敏（68-03 的最小版） */
    static String mask(String field, String value) {
        if (value == null) {
            return null;
        }
        if (field.contains("phone") && value.length() >= 7) {
            return value.substring(0, 3) + "****" + value.substring(value.length() - 4);
        }
        if (field.contains("idCard") && value.length() >= 8) {
            return value.substring(0, 4) + "**********" + value.substring(value.length() - 2);
        }
        if (field.contains("password") || field.contains("token")) {
            return "***";
        }
        return value;
    }

    /** Agent 注册表：来源/增强范围/数据去向/可关闭 */
    record AgentInfo(String name, String source, List<String> enhancedPackages,
                     String sink, boolean killSwitch) {
    }

    static final List<AgentInfo> registry = new ArrayList<>();

    static void register(AgentInfo info) {
        registry.add(info);
        System.out.printf("  注册 Agent：%s 来源=%s 增强=%s 上报=%s 可关闭=%b%n",
                info.name(), info.source(), info.enhancedPackages(), info.sink(),
                info.killSwitch());
    }

    static String evaluate(AgentInfo info) {
        List<String> issues = new ArrayList<>();
        if (!info.source().equals("self") && !info.source().equals("audited")) {
            issues.add("来源不可信（供应链风险）");
        }
        if (info.enhancedPackages().contains("*")) {
            issues.add("增强范围过宽（应最小化）");
        }
        if (!info.killSwitch()) {
            issues.add("无开关（出问题无法停用）");
        }
        if (info.sink().contains("external") && info.sink().contains("raw")) {
            issues.add("原始数据出境（合规风险，68-06）");
        }
        return issues.isEmpty() ? "通过" : "拒绝：" + issues;
    }

    public static void main(String[] args) {
        System.out.println("【参数脱敏】");
        System.out.printf("  phone=13800001234 -> %s%n", mask("phone", "13800001234"));
        System.out.printf("  idCard=110101199001011234 -> %s%n",
                mask("idCard", "110101199001011234"));
        System.out.printf("  password=abc123 -> %s%n", mask("password", "abc123"));
        System.out.println();

        System.out.println("【Agent 治理注册表】");
        register(new AgentInfo("apm-agent", "self", List.of("com.heima.service"),
                "internal-otel", true));
        register(new AgentInfo("unknown-agent", "third-party", List.of("*"),
                "external-raw", false));
        register(new AgentInfo("debug-agent", "audited", List.of("com.heima.order"),
                "local-file", true));
        System.out.println();

        System.out.println("【准入评估】");
        for (AgentInfo info : registry) {
            System.out.printf("  %-14s -> %s%n", info.name(), evaluate(info));
        }
        System.out.println();

        System.out.println("【治理五条】");
        Map<String, String> rules = new LinkedHashMap<>();
        rules.put("来源可信", "自研或审计过的 Agent，禁止来路不明 jar");
        rules.put("最小增强", "只增强需要的包，禁止全量");
        rules.put("数据脱敏", "参数脱敏后再上报（36-09/68-03）");
        rules.put("可关闭", "配置开关 + removeTransformer");
        rules.put("审计", "装了哪些、增强了什么、数据去向");
        rules.forEach((k, v) -> System.out.printf("  %-6s %s%n", k, v));
    }
}
```

**课后练习**
1. 实现 Agent 准入评估器：来源/增强范围/数据去向/开关四维打分，用 4 个 Agent 示例验证"拒绝与通过"。
2. 实现埋点脱敏：对手机号/身份证/密码/token 四类字段掩码，验证日志中无明文。
3. 思考题：为什么"生产环境禁止动态 attach 未知 Agent"？从"等同代码注入"与"供应链攻击"两个角度分析。

---

### 66-11 综合案例：自研 APM Agent

**知识点**
- **把本章收口成一个可运行的迷你 APM**：**入口**（premain 注册 transformer 66-01/02）-> **过滤**（只增强业务包 66-02）-> **增强**（Advice 插耗时与异常 66-05/06）-> **上下文**（traceId ThreadLocal + 跨线程传递）-> **采样与异步上报**（66-06/09）-> **降级与开关**（66-02/09）-> **治理**（注册表 + 脱敏 66-10）。
- **APM Agent 的数据模型**：**Span**（类名/方法名/开始/耗时/状态/异常/traceId）；**聚合指标**（方法级 P50/P99/错误率 42-04）；**调用链**（span 父子关系 42-07）——**先落 Span，再聚合**。
- **验收的五条**：**零侵入**（业务代码未改）、**可观测**（能查到方法耗时与异常）、**开销可控**（采样 + 过滤后 CPU < 1%）、**可降级**（Agent 失败不影响业务）、**可治理**（可开关、有审计、数据脱敏）。
- **与全书的关系**：Agent 是"42 章可观测"的生产端、是"60 章框架内核"的运行时应用、是"68 章隐私"的采集边界——**一个组件横跨三章**。
- 易错点：只采集不聚合（数据没人看）；无父子 span（调用链断裂）；不开采样（存储爆炸）；Agent 与业务同队列（互相影响）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class ApmAgentCase {
    /** Span：一次方法调用 */
    record Span(String traceId, String spanId, String parentSpanId, String cls,
                String method, long startNs, long costNs, boolean error, String errorMsg) {
    }

    static final List<Span> collected = new ArrayList<>();
    static final ThreadLocal<String> TRACE = new ThreadLocal<>();
    static final ThreadLocal<String> SPAN = new ThreadLocal<>();
    static final int SAMPLE_EVERY = 10;
    static int seq = 0;

    /** 模拟增强后的方法：入口创建 span，出口记录 */
    static String enhancedQuery(String orderId) {
        String traceId = TRACE.get();
        String parent = SPAN.get();
        String spanId = "s-" + (++seq);
        long start = System.nanoTime();
        boolean error = false;
        String errorMsg = null;
        SPAN.set(spanId);
        try {
            if (orderId == null) {
                throw new IllegalArgumentException("orderId 空");
            }
            return "order-" + orderId;
        } catch (RuntimeException e) {
            error = true;
            errorMsg = e.getClass().getSimpleName();
            throw e;
        } finally {
            SPAN.set(parent);
            if (seq % SAMPLE_EVERY == 0) {
                collected.add(new Span(traceId, spanId, parent, "Biz", "enhancedQuery",
                        start, System.nanoTime() - start, error, errorMsg));
            }
        }
    }

    /** 模拟请求入口：设置 traceId */
    static void handle(String reqId, String orderId) {
        TRACE.set("trace-" + reqId);
        SPAN.set(null);
        try {
            enhancedQuery(orderId);
        } catch (RuntimeException ignored) {
            // 业务异常由上层处理，Agent 只记录
        } finally {
            TRACE.remove();
            SPAN.remove();
        }
    }

    static void report() {
        System.out.println("【采集到的 Span（采样 1/10）】" + collected.size() + " 条");
        collected.stream().limit(5).forEach(s -> System.out.printf(
                "  trace=%-10s span=%-5s parent=%-5s %s.%s cost=%dns error=%b%s%n",
                s.traceId(), s.spanId(), s.parentSpanId() == null ? "root" : s.parentSpanId(),
                s.cls(), s.method(), s.costNs(), s.error(),
                s.errorMsg() == null ? "" : " " + s.errorMsg()));

        System.out.println();
        System.out.println("【方法级聚合指标】");
        long total = collected.size();
        long errors = collected.stream().filter(Span::error).count();
        List<Long> costs = new ArrayList<>(collected.stream().map(Span::costNs).sorted().toList());
        long p50 = costs.isEmpty() ? 0 : costs.get(costs.size() / 2);
        long p99 = costs.isEmpty() ? 0 : costs.get(Math.min(costs.size() - 1,
                (int) (costs.size() * 0.99)));
        System.out.printf("  Biz.enhancedQuery：调用=%d 错误=%d（%.0f%%）P50=%dns P99=%dns%n",
                total, errors, total == 0 ? 0 : errors * 100.0 / total, p50, p99);
    }

    static void acceptance() {
        System.out.println();
        System.out.println("【验收五条】");
        Map<String, String> gates = new LinkedHashMap<>();
        gates.put("零侵入", "业务代码未改动，仅靠 Agent 增强");
        gates.put("可观测", "方法耗时/异常/traceId 可查");
        gates.put("开销可控", "前缀过滤 + 1/10 采样，CPU 可忽略");
        gates.put("可降级", "transformer 异常则原样加载（66-02）");
        gates.put("可治理", "开关 + 注册表 + 参数脱敏（66-10）");
        gates.forEach((k, v) -> System.out.printf("  %-6s %s%n", k, v));
    }

    public static void main(String[] args) {
        Random random = new Random(7);
        for (int i = 0; i < 100; i++) {
            String orderId = random.nextInt(10) == 0 ? null : String.valueOf(9000 + i);
            handle(String.valueOf(i), orderId);
        }
        report();
        acceptance();
        System.out.println();
        System.out.println("  一句话：Agent 把「不可观测的老系统」变成「有数据的新系统」，代价是治理责任");
    }
}
```

**课后练习**
1. 端到端实现迷你 APM：过滤 + 增强 + traceId + 采样 + 聚合，用 200 次调用（含 10% 异常）输出指标。
2. 加入"父子 span"：让 Controller 方法调 Service 方法，验证调用链树可还原（父 spanId 正确）。
3. 思考题：为什么 APM 要"先落 Span 再聚合"？从"个案排查需要明细"与"趋势分析需要聚合"的双需求分析。

## 本章综合练习

### 综合题：无侵入的链路埋点 Agent

**场景**

写一个 Java Agent 做无侵入埋点：premain/agentmain、字节码增强、方法耗时统计、异常捕获、动态 attach 热更新、性能开销评估。要求业务代码零改动。

**任务**
1. 实现 premain 启动挂载：通过 -javaagent 参数加载，打印加载成功的类数量。
2. 实现字节码增强：在目标方法前后插入计时逻辑，统计调用次数、耗时分布（P50/P99），不改业务源码。
3. 实现 agentmain 动态挂载：对运行中的进程 attach，替换增强逻辑并验证立即生效。
4. 评估开销：对比开启/关闭 Agent 时的吞吐与延迟，给出「可接受的开销上限」判断。

**验收清单**
- [ ] 业务代码完全未修改，埋点数据正确输出。
- [ ] 增强后的类能正常加载运行，不出现 VerifyError。
- [ ] 动态 attach 后新逻辑生效，无需重启进程。
- [ ] 开销有量化数据（延迟增加百分比），并说明哪些方法不适合增强。
