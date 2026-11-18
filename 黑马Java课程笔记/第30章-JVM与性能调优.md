# 第30章 JVM 与性能调优

第29章讲的是"系统之间怎么配合"，本章回到"单个进程内部发生了什么"。同样一段代码，堆开小了会 OOM，开大了单次 GC 要停顿一秒；同样是"接口变慢"，可能是死循环吃满 CPU，也可能是线程全在等一把锁。这些问题的答案都在 JVM 里：内存怎么划分、对象怎么创建、垃圾怎么回收、类怎么加载、进程卡住时用什么工具看。本章按「结构 → 对象 → GC → 类加载 → 工具 → 实战」的顺序讲，最后用一次线上故障的完整排查串起来。所有示例仍是纯 JDK：内存池、GC 次数、线程 CPU 时间、死锁检测都通过 `java.lang.management` 真实读取，GC 日志解析用正则真跑一遍，不需要装任何 APM 或诊断工具。

### 30-01 JVM 运行时内存结构

**知识点**
- **线程私有**三块：**程序计数器**（当前字节码行号，唯一不会 OOM 的区域）、**虚拟机栈**（每个方法一个栈帧：局部变量表、操作数栈、动态链接、返回地址）、**本地方法栈**（Native 方法）。
- **线程共享**两块：**堆**（对象实例与数组，GC 主战场，可 OOM）、**方法区/元空间**（类元信息、常量、静态变量，JDK 8 起用本地内存实现，叫 Metaspace）。
- 栈溢出 `StackOverflowError` 由递归过深或栈帧过大引起，调 `-Xss` 可以改栈大小；堆溢出 `OutOfMemoryError: Java heap space` 由对象持续增长引起，调 `-Xmx`。
- 元空间溢出 `OutOfMemoryError: Metaspace` 常见于动态生成类（CGLIB 代理、反射、热部署），调 `-XX:MaxMetaspaceSize`。
- **直接内存**（NIO 的 `DirectByteBuffer`）不在堆里，不受 `-Xmx` 限制，但受物理内存限制，溢出报 `OutOfMemoryError: Direct buffer memory`。
- 字符串常量池在 JDK 7 之后移到了堆里；`String.intern()` 会往池里放引用，滥用会撑大堆。
- 易错：以为 `-Xmx` 就是进程总内存（还要加元空间、直接内存、线程栈、JVM 自身开销）；把"堆大"当成"性能好"（堆越大，单次 Full GC 越久）。

**示例代码**
```java
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.lang.management.MemoryUsage;

public class JvmMemoryLayout {
    public static void main(String[] args) {
        System.out.println("=== JVM 基本信息 ===");
        System.out.println("  Java 版本：" + System.getProperty("java.version"));
        System.out.println("  虚拟机：" + System.getProperty("java.vm.name") + " "
                + System.getProperty("java.vm.version"));
        Runtime rt = Runtime.getRuntime();
        System.out.println("  可用处理器：" + rt.availableProcessors());

        System.out.println();
        System.out.println("=== 堆内存（-Xms / -Xmx 控制）===");
        System.out.println("  最大堆 " + mb(rt.maxMemory()) + "MB");
        System.out.println("  已申请 " + mb(rt.totalMemory()) + "MB");
        System.out.println("  已使用 " + mb(rt.totalMemory() - rt.freeMemory()) + "MB");

        System.out.println();
        System.out.println("=== 内存池明细（堆 / 非堆）===");
        for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
            MemoryUsage u = pool.getUsage();
            String kind = pool.getType() == MemoryType.HEAP ? "堆  " : "非堆";
            System.out.printf("  %s %-24s used=%5dMB committed=%5dMB max=%5dMB%n",
                    kind, pool.getName(), mb(u.getUsed()), mb(u.getCommitted()), mb(u.getMax()));
        }

        System.out.println();
        System.out.println("=== 各区域职责与溢出类型 ===");
        String[][] regions = {
                {"程序计数器", "线程私有", "当前字节码行号", "不会 OOM"},
                {"虚拟机栈", "线程私有", "-Xss", "StackOverflowError（递归过深）"},
                {"本地方法栈", "线程私有", "-Xss", "StackOverflowError"},
                {"堆", "线程共享", "-Xms/-Xmx", "OutOfMemoryError: Java heap space"},
                {"元空间", "线程共享", "-XX:MaxMetaspaceSize", "OutOfMemoryError: Metaspace"},
                {"直接内存", "进程级", "-XX:MaxDirectMemorySize", "OutOfMemoryError: Direct buffer memory"}
        };
        System.out.printf("  %-12s %-10s %-22s %s%n", "区域", "归属", "参数", "溢出表现");
        for (String[] r : regions) {
            System.out.printf("  %-12s %-10s %-22s %s%n", r[0], r[1], r[2], r[3]);
        }

        System.out.println();
        System.out.println("=== 栈帧：方法调用一次压一帧 ===");
        System.out.println("  depth(0) -> " + depth(0) + " 层返回，每层一个栈帧");
        System.out.println("  栈帧内容：局部变量表 + 操作数栈 + 动态链接 + 返回地址");
    }

    /** 演示栈帧压栈与出栈 */
    static int depth(int n) {
        if (n >= 5) {
            return n;
        }
        return depth(n + 1);
    }

    static long mb(long bytes) {
        return bytes < 0 ? -1 : bytes / 1024 / 1024;
    }
}
```

**课后练习**
1. 用 `-Xss256k` 跑一个深度 10000 的递归，观察 `StackOverflowError`，再改回默认值对比。
2. 用 `-Xmx64m` 启动本课示例，观察最大堆输出，并说明为什么进程实际内存大于 64MB。
3. 列出一次方法调用在虚拟机栈上产生的栈帧内容，说明局部变量表里放什么。

### 30-02 对象创建与内存布局

**知识点**
- 对象创建流程：类加载检查 → 分配内存（**指针碰撞**或**空闲列表**）→ 初始化零值 → 设置对象头 → 执行构造方法。
- 内存布局：**对象头**（Mark Word + 类型指针）、**实例数据**、**对齐填充**（8 字节对齐）。
- **Mark Word** 复用存储：哈希码、GC 分代年龄、锁标志位、偏向线程 ID。这就是"锁升级"能复用同一块内存的原因。
- **指针压缩**（`-XX:+UseCompressedOops`，64 位默认开启）：对象引用从 8 字节压到 4 字节，堆小于 32GB 时生效，能省 30%~50% 内存。
- **TLAB**（Thread Local Allocation Buffer）：每个线程在 Eden 里预占一小块，分配对象不用加锁，是"分配飞快"的关键。
- **逃逸分析**：对象没逃出方法，就可能被**栈上分配**或**标量替换**，甚至整个对象被拆成几个局部变量。这解释了"为什么小对象不一定进堆"。
- 对象大小估算：`对象头 12B（压缩后）+ 字段 + 对齐`。一个只有 `int` 字段的对象通常 16B；加一个 `long` 变 24B。
- 易错：堆开到 40GB 却不知道指针压缩失效（内存反而更紧张）；用 `Integer` 当计数器（每个都是对象）；在循环里创建大量短命大对象，把 Eden 冲爆。

**示例代码**
```java
public class ObjectLayout {
    /** 只有 int：12B 头 + 4B 字段 = 16B（正好对齐） */
    static class IntHolder {
        int value;
    }

    /** 12B 头 + 8B long = 20B，对齐到 24B */
    static class LongHolder {
        long value;
    }

    /** 12B 头 + 4B int + 8B long = 24B，对齐到 24B */
    static class MixedHolder {
        int a;
        long b;
    }

    /** 12B 头 + 4B 引用（压缩）= 16B */
    static class RefHolder {
        Object ref;
    }

    public static void main(String[] args) {
        System.out.println("=== 对象内存布局 ===");
        System.out.println("  | Mark Word (8B) | 类型指针 (4B, 压缩后) | 实例数据 | 对齐填充 |");
        System.out.println("  64 位未压缩时类型指针 8B，对象头 16B；压缩后对象头 12B");

        System.out.println();
        System.out.println("=== 常见对象大小估算 ===");
        System.out.printf("  %-14s %-8s %-8s %s%n", "类型", "头部", "字段", "估算总大小");
        System.out.printf("  %-14s %-8s %-8s %s%n", "IntHolder", "12B", "4B", "16B");
        System.out.printf("  %-14s %-8s %-8s %s%n", "LongHolder", "12B", "8B", "24B（20B 对齐）");
        System.out.printf("  %-14s %-8s %-8s %s%n", "MixedHolder", "12B", "12B", "24B");
        System.out.printf("  %-14s %-8s %-8s %s%n", "RefHolder", "12B", "4B", "16B");

        System.out.println();
        System.out.println("=== 实测：不同字段组合的分配开销 ===");
        int n = 1_000_000;
        measure("IntHolder", n, () -> {
            IntHolder[] arr = new IntHolder[n];
            for (int i = 0; i < n; i++) {
                arr[i] = new IntHolder();
            }
            return arr.length;
        });
        measure("LongHolder", n, () -> {
            LongHolder[] arr = new LongHolder[n];
            for (int i = 0; i < n; i++) {
                arr[i] = new LongHolder();
            }
            return arr.length;
        });
        measure("MixedHolder", n, () -> {
            MixedHolder[] arr = new MixedHolder[n];
            for (int i = 0; i < n; i++) {
                arr[i] = new MixedHolder();
            }
            return arr.length;
        });

        System.out.println();
        System.out.println("=== 装箱 vs 基本类型：内存差 4 倍以上 ===");
        long before = used();
        int[] prim = new int[n];
        for (int i = 0; i < n; i++) {
            prim[i] = i;
        }
        long primCost = used() - before;
        before = used();
        Integer[] boxed = new Integer[n];
        for (int i = 0; i < n; i++) {
            boxed[i] = i;
        }
        long boxedCost = used() - before;
        System.out.println("  int[" + n + "] 约 " + primCost / 1024 + "KB");
        System.out.println("  Integer[" + n + "] 约 " + boxedCost / 1024 + "KB（每个对象都有对象头）");
        System.out.println("  合计引用保留：" + (prim.length + boxed.length));

        System.out.println();
        System.out.println("=== 逃逸分析：对象没逃出方法就可以被优化掉 ===");
        int sum = 0;
        for (int i = 0; i < 100_000; i++) {
            sum += localSum(i, i + 1);
        }
        System.out.println("  循环内创建临时对象 10 万次，sum=" + sum + "（JIT 可做标量替换，不真的分配）");
        System.out.println("  触发条件：对象不逃逸 + 方法被 JIT 编译（热点代码）");

        System.out.println();
        System.out.println("=== TLAB：分配不加锁 ===");
        System.out.println("  每个线程在 Eden 预占一块 TLAB，new 对象先在自己的 TLAB 里指针碰撞");
        System.out.println("  TLAB 用完才需要 CAS 申请新块 -> 这就是多线程下分配依然很快的原因");
    }

    /** 对象不逃逸：只在方法内使用 */
    static int localSum(int a, int b) {
        int[] pair = {a, b};
        return pair[0] + pair[1];
    }

    static long used() {
        Runtime rt = Runtime.getRuntime();
        System.gc();
        return rt.totalMemory() - rt.freeMemory();
    }

    interface Alloc {
        int run();
    }

    static void measure(String name, int n, Alloc alloc) {
        long begin = System.nanoTime();
        int size = alloc.run();
        long cost = (System.nanoTime() - begin) / 1_000_000;
        System.out.printf("  分配 %s x%d 耗时 %dms，返回 %d%n", name, n, cost, size);
    }
}
```

**课后练习**
1. 用 `-XX:-UseCompressedOops` 重启示例，对比引用字段对象的估算大小变化。
2. 给 `MixedHolder` 调整字段顺序（`long` 放前面），说明为什么总大小可能变小。
3. 解释为什么 `Integer[]` 比 `int[]` 占用的内存远不止 4 倍，从对象头与对齐的角度分析。

### 30-03 垃圾回收基础：可达性分析与分代

**知识点**
- 判断对象死活用**可达性分析**（不是引用计数，引用计数解决不了循环引用）：从 **GC Roots** 出发能走到的对象才存活。
- GC Roots 包括：虚拟机栈中的局部变量、方法区静态属性、常量引用、本地方法栈的 JNI 引用、活跃线程、同步锁持有的对象。
- **分代假说**：绝大多数对象朝生夕死；熬过越多次 GC 的对象越难死。于是堆分成 **Eden + Survivor(×2) + Old**，新生代用复制算法，老年代用标记-清除/标记-整理。
- **对象晋升**：在 Survivor 之间复制一次年龄 +1，达到 `-XX:MaxTenuringThreshold`（默认 15）进老年代；**动态年龄判断**：Survivor 中同龄对象总和超过一半，年龄 ≥ 该值的直接晋升。
- **大对象直接进老年代**（`-XX:PretenureSizeThreshold`），避免在 Eden/Survivor 之间反复复制。
- **Minor GC** 只扫新生代（快，几十毫秒）；**Major/Full GC** 扫全堆（慢，可能秒级，会 STW）。调优的核心目标通常是"减少 Full GC 次数与时长"。
- 易错：以为 `System.gc()` 一定会立刻回收（只是建议，`-XX:+DisableExplicitGC` 可直接忽略）；把"对象不可达"和"对象被回收"划等号（还要看 finalize/虚引用，且实际由 GC 时机决定）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.List;

public class GcBasics {
    /** 模拟一个节点，可形成环 */
    static class Node {
        final String name;
        Node next;

        Node(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    static final List<byte[]> STATIC_HOLDER = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        System.out.println("=== 引用计数解决不了循环引用 ===");
        Node a = new Node("A");
        Node b = new Node("B");
        a.next = b;
        b.next = a;
        System.out.println("  A.next=" + a.next + "，B.next=" + b.next + "，互相引用，计数都不为 0");
        a = null;
        b = null;
        System.out.println("  但 A、B 从 GC Root 已不可达 -> 可达性分析能正确回收");

        System.out.println();
        System.out.println("=== GC Roots 示例 ===");
        String local = "栈上的局部变量（GC Root）";
        STATIC_HOLDER.add(new byte[1024]);
        System.out.println("  1. 虚拟机栈局部变量：" + local);
        System.out.println("  2. 静态属性：" + STATIC_HOLDER.size() + " 个对象被 static 持有");
        System.out.println("  3. 常量池引用、JNI 引用、活跃线程、锁对象");

        System.out.println();
        System.out.println("=== 分代结构 ===");
        System.out.println("  ┌──────────── 新生代 (Young) ────────────┐┌── 老年代 (Old) ──┐");
        System.out.println("  │  Eden  │ Survivor0 │ Survivor1        ││  长期存活对象    │");
        System.out.println("  └────────────────────────────────────────┘└─────────────────┘");
        System.out.println("  默认比例 Eden:S0:S1 = 8:1:1（-XX:SurvivorRatio=8）");

        System.out.println();
        System.out.println("=== 对象晋升过程 ===");
        int age = 0;
        int threshold = 15;
        for (int gc = 1; gc <= 3; gc++) {
            age++;
            System.out.println("  第 " + gc + " 次 Minor GC：存活对象在 S0/S1 间复制，年龄=" + age
                    + (age >= threshold ? " -> 晋升老年代" : " -> 仍在新生代"));
        }
        System.out.println("  动态年龄判断：Survivor 中同龄对象总和 > 50%，该年龄及以上直接晋升");

        System.out.println();
        System.out.println("=== Minor GC vs Full GC ===");
        System.out.println("  Minor GC：只扫新生代，STW 短（几 ms ~ 几十 ms），频率高");
        System.out.println("  Full GC ：扫全堆 + 元空间，STW 长（几百 ms ~ 秒），频率低但危害大");

        System.out.println();
        System.out.println("=== 实测：制造一批短命对象，观察 GC 次数 ===");
        long before = gcCount();
        for (int round = 0; round < 20; round++) {
            List<byte[]> tmp = new ArrayList<>();
            for (int i = 0; i < 200; i++) {
                tmp.add(new byte[1024 * 32]);
            }
            tmp.clear();
        }
        long after = gcCount();
        System.out.println("  分配约 " + (20 * 200 * 32 / 1024) + "MB 短命对象，GC 次数变化 "
                + before + " -> " + after);

        System.out.println();
        System.out.println("=== 大对象直接进老年代 ===");
        byte[] big = new byte[8 * 1024 * 1024];
        System.out.println("  分配 8MB 数组，若超过 PretenureSizeThreshold 直接进老年代，避免反复复制");
        System.out.println("  大对象引用保持：" + (big.length > 0));
    }

    static long gcCount() {
        long sum = 0;
        for (java.lang.management.GarbageCollectorMXBean gc
                : java.lang.management.ManagementFactory.getGarbageCollectorMXBeans()) {
            long c = gc.getCollectionCount();
            sum += c < 0 ? 0 : c;
        }
        return sum;
    }
}
```

**课后练习**
1. 用 `-Xmn32m -XX:+PrintGCDetails`（JDK 8）或 `-Xlog:gc*`（JDK 9+）运行示例，读出 Minor GC 次数与耗时。
2. 解释为什么引用计数无法回收循环引用，而可达性分析可以。
3. 说明"动态年龄判断"在什么情况下会让对象提前晋升，会带来什么后果。

### 30-04 垃圾收集器：从 Serial 到 ZGC

**知识点**
- **Serial / Serial Old**：单线程，STW 全程暂停，简单、内存开销小，适合客户端与容器小堆。
- **ParNew / Parallel Scavenge + Parallel Old**：多线程并行，追求吞吐量（`-XX:+UseParallelGC`），适合批处理。
- **CMS**：并发标记清除，追求低停顿，但有**内存碎片**、**并发模式失败**（Concurrent Mode Failure）导致退化成 Serial Old 的长停顿；JDK 14 已移除。
- **G1**（JDK 9+ 默认）：把堆切成等大 Region，按"回收价值"优先回收（Garbage First），可设**目标停顿时间** `-XX:MaxGCPauseMillis=200`；大对象用 Humongous Region。
- **ZGC**（JDK 15+ 生产可用）：染色指针 + 读屏障，停顿与堆大小几乎无关（亚毫秒级），支持 TB 级堆；JDK 21 已是分代 ZGC。
- **Shenandoah**：与 ZGC 类似目标，低延迟，OpenJDK 提供。
- 选型：容器小堆（< 4GB）用 G1；大堆 + 极低延迟用 ZGC；吞吐优先的离线任务用 Parallel；不要在生产用 Serial（除非单核小内存）。
- 易错：以为"并发收集器完全不 STW"（初始标记、最终标记、转移阶段仍有短暂停顿）；把 CMS 的碎片问题带到 G1 上（G1 是复制算法，但 Humongous 分配仍可能触发 Full GC）。

**示例代码**
```java
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class GcCollectors {
    public static void main(String[] args) {
        System.out.println("=== 当前 JVM 实际使用的收集器 ===");
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            System.out.println("  " + gc.getName()
                    + "：次数=" + gc.getCollectionCount()
                    + "，累计耗时=" + gc.getCollectionTime() + "ms");
        }
        System.out.println("  JDK 21 默认：G1（新生代 + 老年代各一个收集器实例）");

        System.out.println();
        System.out.println("=== 收集器对比 ===");
        Map<String, String[]> table = new LinkedHashMap<>();
        table.put("Serial", new String[]{"单线程", "全程 STW", "客户端/小堆", "简单、内存占用小"});
        table.put("Parallel", new String[]{"多线程", "全程 STW", "批处理/吞吐优先", "吞吐最高"});
        table.put("CMS", new String[]{"并发", "低停顿", "已废弃", "碎片 + 并发失败"});
        table.put("G1", new String[]{"Region 分治", "可设目标停顿", "通用/默认", "可预测停顿"});
        table.put("ZGC", new String[]{"染色指针", "亚毫秒级", "大堆/极低延迟", "停顿与堆大小无关"});
        System.out.printf("  %-10s %-14s %-14s %-16s %s%n", "收集器", "工作方式", "停顿", "适用场景", "特点");
        for (Map.Entry<String, String[]> e : table.entrySet()) {
            String[] v = e.getValue();
            System.out.printf("  %-10s %-14s %-14s %-16s %s%n", e.getKey(), v[0], v[1], v[2], v[3]);
        }

        System.out.println();
        System.out.println("=== G1 的 Region 布局（堆 4GB，Region 1MB）===");
        int regions = 4096;
        int eden = (int) (regions * 0.4);
        int survivor = (int) (regions * 0.05);
        int old = (int) (regions * 0.45);
        int humongous = regions - eden - survivor - old;
        System.out.println("  Eden " + eden + " 个 + Survivor " + survivor + " 个 + Old " + old
                + " 个 + Humongous " + humongous + " 个 = " + regions);
        System.out.println("  回收时按 Region 的「垃圾占比 × 大小」排序，优先回收收益最高的");

        System.out.println();
        System.out.println("=== 停顿目标与代价 ===");
        int[] targets = {50, 100, 200, 500};
        for (int t : targets) {
            double youngRatio = t < 100 ? 0.3 : t < 300 ? 0.45 : 0.6;
            System.out.printf("  MaxGCPauseMillis=%3dms -> 新生代占比约 %.0f%%%s%n",
                    t, youngRatio * 100,
                    t < 100 ? "（停顿更短，但 GC 更频繁、吞吐下降）" : "");
        }
        System.out.println("  停顿目标不是越小越好：太小会让每次只回收少量 Region，GC 次数暴增");

        System.out.println();
        System.out.println("=== 实测：一次内存压力下的收集器表现 ===");
        List<byte[]> keep = new ArrayList<>();
        long begin = System.currentTimeMillis();
        for (int i = 0; i < 300; i++) {
            keep.add(new byte[256 * 1024]);
            if (i % 100 == 0) {
                keep.subList(0, keep.size() / 2).clear();
            }
        }
        long cost = System.currentTimeMillis() - begin;
        System.out.println("  分配 + 部分释放 300 x 256KB 耗时 " + cost + "ms，当前持有 " + keep.size() + " 块");
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            System.out.println("  " + gc.getName() + " 次数=" + gc.getCollectionCount()
                    + " 耗时=" + gc.getCollectionTime() + "ms");
        }
        System.out.println("  真实调优要看：GC 次数、单次停顿、Full GC 占比、晋升速率");
    }
}
```

**课后练习**
1. 用 `-XX:+UseSerialGC`、`-XX:+UseParallelGC`、`-XX:+UseG1GC` 分别启动示例，对比输出与耗时。
2. 解释 G1 为什么能"可预测停顿"，Region 在其中起什么作用。
3. ZGC 的停顿为什么与堆大小几乎无关？从染色指针与并发转移的角度说明。

### 30-05 类加载机制与双亲委派

**知识点**
- 类的生命周期：加载 → 验证 → 准备 → 解析 → 初始化 → 使用 → 卸载。**准备**阶段给静态变量赋零值，**初始化**阶段才执行 `<clinit>`（静态赋值与静态块）。
- **双亲委派**：类加载请求先交给父加载器，父加载器加载不了才自己加载。好处是**安全**（核心类不会被自定义的 `java.lang.String` 覆盖）与**唯一**（同一个类只加载一次）。
- 三层加载器：**Bootstrap**（C++ 实现，加载 `java.base`）→ **Platform/Ext** → **Application**（classpath）。JDK 9 之后是模块化后的 Platform ClassLoader。
- **打破双亲委派**的三种场景：SPI（`ServiceLoader` 用线程上下文类加载器加载实现类，如 JDBC 驱动）、Tomcat（每个 WebApp 独立加载器实现应用隔离）、热部署/OSGi（同一类多版本共存）。
- **类初始化的时机**：`new`、访问静态字段（非 final 常量）、调用静态方法、反射、子类初始化先初始化父类、`main` 类。**被动引用**不触发初始化（如通过子类访问父类静态字段、数组声明、访问 `static final` 常量）。
- 同一个类被不同加载器加载，会产生两个**互不兼容**的 Class 对象，`instanceof` 为 false，强转抛 `ClassCastException`。
- 易错：以为 `Class.forName` 和 `ClassLoader.loadClass` 一样（前者默认初始化，后者不初始化）；自定义加载器忘了重写 `findClass` 而是覆盖 `loadClass`，破坏了双亲委派。

**示例代码**
```java
public class ClassLoading {
    static class Parent {
        static {
            System.out.println("  Parent 静态块执行");
        }

        static int value = 10;

        static void hello() {
            System.out.println("  Parent.hello()");
        }
    }

    static class Child extends Parent {
        static {
            System.out.println("  Child 静态块执行");
        }

        static int childValue = 20;
    }

    static final String CONSTANT = "编译期常量，不触发初始化";

    public static void main(String[] args) throws Exception {
        System.out.println("=== 主动引用：触发初始化 ===");
        System.out.println("  准备 new Child() 之前");
        new Child();
        System.out.println("  new 之后（先父后子）");

        System.out.println();
        System.out.println("=== 被动引用：不触发初始化 ===");
        System.out.println("  访问编译期常量 CONSTANT = " + CONSTANT + "（常量在编译期已内联到调用方）");
        Parent[] arr = new Parent[3];
        System.out.println("  声明数组 Parent[3] 长度 " + arr.length + "（只创建数组对象，不初始化 Parent）");
        int v = Child.value;
        System.out.println("  通过子类访问父类静态字段 Child.value = " + v + "（只初始化 Parent，不初始化 Child）");

        System.out.println();
        System.out.println("=== 类加载器层次 ===");
        ClassLoader app = ClassLoading.class.getClassLoader();
        System.out.println("  Application ClassLoader：" + app);
        System.out.println("  Platform  ClassLoader：" + app.getParent());
        System.out.println("  Bootstrap ClassLoader：" + app.getParent().getParent() + "（C++ 实现，返回 null）");

        System.out.println();
        System.out.println("=== 双亲委派：加载 java.lang.String 的请求一路向上 ===");
        Class<?> stringClass = Class.forName("java.lang.String");
        System.out.println("  String 的加载器：" + stringClass.getClassLoader() + "（null 表示 Bootstrap）");
        System.out.println("  -> 自定义加载器收到请求先委派给父，父能加载就不自己加载");
        System.out.println("  -> 防止伪造核心类，也保证同一个类只被加载一次");

        System.out.println();
        System.out.println("=== 打破双亲委派：SPI 与 Tomcat ===");
        System.out.println("  SPI：JDBC 的 Driver 由各厂商 jar 提供，Bootstrap 加载器看不到 classpath");
        System.out.println("       -> 用 Thread.currentThread().getContextClassLoader() 反向委派");
        System.out.println("  Tomcat：每个 WebApp 一个 WebappClassLoader，先自己加载再委派父");
        System.out.println("       -> 实现应用隔离，两个应用可以有不同版本的同一个库");

        System.out.println();
        System.out.println("=== 同一个类、不同加载器 = 两个不同的 Class ===");
        System.out.println("  classLoaderA.loadClass(X) != classLoaderB.loadClass(X)");
        System.out.println("  后果：instanceof 为 false，强转抛 ClassCastException");
        System.out.println("  这也是热部署容易踩的坑：旧对象还持有旧加载器的类");
    }
}
```

**课后练习**
1. 写一个自定义 `ClassLoader` 从指定目录读取 `.class`，加载后打印 `getClassLoader()`。
2. 用 `Class.forName("...")` 与 `ClassLoader.loadClass("...")` 分别加载 `Child`，对比静态块是否执行。
3. 解释 Tomcat 为什么要打破双亲委派，如果不打破会发生什么。

### 30-06 常用排查工具：jps / jstat / jstack / jmap

**知识点**
- **jps**：列出当前用户的 Java 进程与主类，`-l` 显示完整类名，`-v` 显示 JVM 参数。
- **jstat**：看 GC 与内存池实时数据。`jstat -gcutil <pid> 1000` 每秒打印各分区使用率与 GC 次数/耗时，是判断"是否频繁 Full GC"的首选。
- **jstack**：导出线程栈，定位**死锁**、**CPU 飙高**、**线程阻塞**。`jstack -l <pid>` 会额外打印锁信息，末尾直接给 "Found one Java-level deadlock"。
- **jmap**：`jmap -histo:live <pid>` 看对象数量与占用（快速定位谁在涨）；`jmap -dump:format=b,file=heap.hprof <pid>` 导出堆快照供 MAT 分析。
- **jcmd**：JDK 7+ 的统一入口，`jcmd <pid> VM.flags`、`GC.heap_info`、`Thread.print`、`GC.class_histogram` 都能干，生产推荐优先用 jcmd（jmap 在部分 JDK 版本会 STW）。
- **arthas**：阿里开源诊断工具，`dashboard`、`thread`、`watch`、`trace` 可以不停机定位方法耗时与入参出参。
- 生产注意：`jmap -dump` 会 STW（尤其大堆），要评估影响；`-XX:+HeapDumpOnOutOfMemoryError` 让 JVM 在 OOM 时自动 dump，是必备参数。
- 易错：dump 文件几个 GB 直接拉到本地打开（应该先在服务器上 `jmap -histo` 缩小范围）；只看 CPU 不看 GC（很多"CPU 高"其实是 GC 线程在拼命回收）。

**示例代码**
```java
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.lang.management.MemoryUsage;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;

public class DiagnosticTools {
    public static void main(String[] args) throws Exception {
        String pid = ManagementFactory.getRuntimeMXBean().getName();
        System.out.println("=== jps 等价信息 ===");
        System.out.println("  当前进程：" + pid + "（pid@host）");
        System.out.println("  主类：" + DiagnosticTools.class.getName());
        System.out.println("  启动参数：" + ManagementFactory.getRuntimeMXBean().getInputArguments());

        System.out.println();
        System.out.println("=== jstat -gcutil 等价：各分区使用率 ===");
        System.out.printf("  %-26s %6s %6s %6s%n", "内存池", "已用%", "已提交MB", "最大MB");
        for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
            if (pool.getType() != MemoryType.HEAP) {
                continue;
            }
            MemoryUsage u = pool.getUsage();
            double pct = u.getMax() > 0 ? u.getUsed() * 100.0 / u.getMax() : 0;
            System.out.printf("  %-26s %5.1f%% %8d %8d%n", pool.getName(), pct,
                    u.getCommitted() / 1024 / 1024, u.getMax() / 1024 / 1024);
        }
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            System.out.println("  " + gc.getName() + "：YGC/FGC 次数=" + gc.getCollectionCount()
                    + "，累计停顿=" + gc.getCollectionTime() + "ms");
        }

        System.out.println();
        System.out.println("=== jstack 等价：线程状态统计 ===");
        ThreadMXBean tmx = ManagementFactory.getThreadMXBean();
        ThreadInfo[] infos = tmx.dumpAllThreads(false, false);
        java.util.Map<String, Integer> stateCount = new java.util.TreeMap<>();
        for (ThreadInfo ti : infos) {
            stateCount.merge(ti.getThreadState().name(), 1, Integer::sum);
        }
        System.out.println("  总线程数 " + tmx.getThreadCount() + "，峰值 " + tmx.getPeakThreadCount());
        stateCount.forEach((k, v) -> System.out.println("    " + k + " = " + v));

        System.out.println();
        System.out.println("=== 死锁检测（jstack 末尾的 Found one Java-level deadlock）===");
        Object lockA = new Object();
        Object lockB = new Object();
        Thread t1 = new Thread(() -> {
            synchronized (lockA) {
                sleep(50);
                synchronized (lockB) {
                    System.out.println("  t1 拿到两把锁（不会执行）");
                }
            }
        }, "worker-A");
        Thread t2 = new Thread(() -> {
            synchronized (lockB) {
                sleep(50);
                synchronized (lockA) {
                    System.out.println("  t2 拿到两把锁（不会执行）");
                }
            }
        }, "worker-B");
        t1.setDaemon(true);
        t2.setDaemon(true);
        t1.start();
        t2.start();
        sleep(300);

        long[] deadlocked = tmx.findDeadlockedThreads();
        if (deadlocked != null) {
            System.out.println("  检测到 " + deadlocked.length + " 个死锁线程：");
            for (ThreadInfo ti : tmx.getThreadInfo(deadlocked, true, true)) {
                System.out.println("    " + ti.getThreadName() + " 状态=" + ti.getThreadState());
                for (StackTraceElement e : ti.getStackTrace()) {
                    System.out.println("      at " + e);
                }
                System.out.println("    等待锁：" + (ti.getLockName() == null ? "-" : ti.getLockName())
                        + "，持有锁：" + (ti.getLockOwnerName() == null ? "-" : ti.getLockOwnerName()));
            }
        } else {
            System.out.println("  未检测到死锁（本次调度未形成环）");
        }

        System.out.println();
        System.out.println("=== jmap -histo 等价：对象数量 Top ===");
        System.out.println("  用 jmap -histo:live <pid> 查看，输出列：#instances、#bytes、class name");
        System.out.println("  常见可疑对象：byte[]、char[]、HashMap$Node、ArrayList、业务 DTO");
        System.out.println("  若某个业务类实例数持续增长且不下降 -> 内存泄漏嫌疑");

        System.out.println();
        System.out.println("=== 工具速查 ===");
        String[][] tools = {
                {"jps -lvm", "看进程、主类、JVM 参数"},
                {"jstat -gcutil <pid> 1000", "每秒看各分区使用率与 GC 次数"},
                {"jstack -l <pid>", "线程栈 + 死锁检测"},
                {"jmap -histo:live <pid>", "对象直方图，找谁在涨"},
                {"jmap -dump:format=b,file=a.hprof <pid>", "导出堆快照给 MAT"},
                {"jcmd <pid> GC.heap_info", "堆概况（生产优先用 jcmd）"},
                {"arthas trace <class> <method>", "不停机看方法耗时与入参"}
        };
        for (String[] t : tools) {
            System.out.printf("  %-42s %s%n", t[0], t[1]);
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
1. 启动示例并在另一个终端执行 `jps -l`、`jstat -gcutil <pid> 1000`，对照代码输出理解每列含义。
2. 用 `jstack -l <pid>` 找到示例中的死锁，读出两个线程各自持有的锁。
3. 说明为什么生产环境优先用 `jcmd` 而不是 `jmap`，`jmap -dump` 有什么风险。

### 30-07 内存泄漏排查：从现象到 GC Root 引用链

**知识点**
- **内存泄漏**在 Java 里的准确说法是"对象已经没用了，但仍然被 GC Root 可达"，所以 GC 收不掉。表现：老年代占用持续上涨、Full GC 后回落很少、最终 `OutOfMemoryError: Java heap space`。
- 排查顺序：**确认是不是泄漏**（Full GC 后老年代是否回落）→ **看对象直方图**（`jmap -histo:live` 谁的数量在涨）→ **导出堆快照**（`jmap -dump` / OOM 自动 dump）→ **在 MAT 里看支配树与 GC Root 路径**。
- 常见泄漏源：**静态集合只增不减**（本地缓存无上限）、**监听器/回调注册后没注销**、**ThreadLocal 用完不 remove**（线程池里线程复用，值一直挂着）、**连接/流未关闭**、**内部类持有外部类**、**队列生产快于消费**。
- `ThreadLocal` 泄漏的根因：`ThreadLocalMap` 的 key 是弱引用、value 是强引用，key 被回收后 value 还在，线程池线程不死就一直留着。
- 判断"缓存"和"泄漏"的区别：缓存有**上限 + 淘汰策略 + 过期时间**，泄漏没有。给本地缓存加 `removeEldestEntry` 或换成带 TTL 的实现。
- 易错：只看 `jmap -histo` 里 `byte[]` 很多就认定泄漏（`byte[]` 往往是受害者，要顺着引用链找持有者）；在生产直接 `jmap -dump` 大堆（会 STW 数秒，优先 `-XX:+HeapDumpOnOutOfMemoryError` 让它自己 dump）。

**示例代码**
```java
import java.util.LinkedHashMap;
import java.util.Map;

public class MemoryLeakHunt {
    /** 反例：无上限的本地缓存，只 put 不 remove */
    static final Map<String, byte[]> LEAKY_CACHE = new LinkedHashMap<>();

    /** 正例：LRU 上限 200，超了就淘汰最久未使用的 */
    static final Map<String, byte[]> BOUNDED_CACHE = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, byte[]> eldest) {
            return size() > 200;
        }
    };

    static final int ENTRY_KB = 4;

    public static void main(String[] args) {
        System.out.println("=== 第一步：判断有没有泄漏（看 Full GC 后老年代是否回落）===");
        long base = usedAfterGc();
        System.out.println("  基线已用堆：" + kb(base) + "KB");

        System.out.println();
        System.out.println("=== 反例：无上限缓存，每 500 次采样一次堆占用 ===");
        long[] samples = new long[5];
        for (int i = 0; i < 5; i++) {
            for (int j = 0; j < 500; j++) {
                LEAKY_CACHE.put("user:" + (i * 500 + j), new byte[ENTRY_KB * 1024]);
            }
            samples[i] = usedAfterGc();
            System.out.println("  写入 " + LEAKY_CACHE.size() + " 条后，已用堆 "
                    + kb(samples[i]) + "KB（相对基线 +" + kb(samples[i] - base) + "KB）");
        }
        System.out.println("  趋势：" + trend(samples));
        System.out.println("  典型特征：每轮都涨，GC 后不回落 -> 泄漏");

        System.out.println();
        System.out.println("=== 第二步：谁在持有？模拟 GC Root 引用链 ===");
        System.out.println("  GC Root: 线程 main 的栈帧");
        System.out.println("    -> MemoryLeakHunt.<clinit> / 静态字段 LEAKY_CACHE");
        System.out.println("      -> LinkedHashMap.table (Node[] 数组)");
        System.out.println("        -> Node.key = \"user:2499\"，Node.value = byte[" + (ENTRY_KB * 1024) + "]");
        System.out.println("  结论：对象不是没人用，而是被静态集合死死引用 -> 只能靠代码修");

        System.out.println();
        System.out.println("=== 第三步：修复后对比（有上限 + LRU）===");
        long[] fixed = new long[5];
        long fixedBase = usedAfterGc();
        for (int i = 0; i < 5; i++) {
            for (int j = 0; j < 500; j++) {
                BOUNDED_CACHE.put("user:" + (i * 500 + j), new byte[ENTRY_KB * 1024]);
            }
            fixed[i] = usedAfterGc();
            System.out.println("  写入 " + ((i + 1) * 500) + " 条后，缓存条数=" + BOUNDED_CACHE.size()
                    + "，已用堆 " + kb(fixed[i]) + "KB（相对基线 +" + kb(fixed[i] - fixedBase) + "KB）");
        }
        System.out.println("  趋势：" + trend(fixed));
        System.out.println("  典型特征：堆占用到上限后走平 -> 这是缓存，不是泄漏");

        System.out.println();
        System.out.println("=== 常见泄漏源清单 ===");
        String[][] leaks = {
                {"静态 Map/List 只增不减", "加容量上限 + 淘汰策略，或换 Caffeine/Redis"},
                {"ThreadLocal 不 remove", "finally 里 remove()，线程池场景必做"},
                {"监听器/回调注册不注销", "生命周期结束时反注册，或用弱引用"},
                {"连接/流未关闭", "try-with-resources"},
                {"内部类持有外部类", "改成 static 嵌套类，或只传需要的字段"},
                {"生产快于消费的队列", "有界队列 + 背压 + 监控队列深度"}
        };
        System.out.printf("  %-26s %s%n", "泄漏源", "修法");
        for (String[] l : leaks) {
            System.out.printf("  %-26s %s%n", l[0], l[1]);
        }

        System.out.println();
        System.out.println("=== 生产必备参数 ===");
        System.out.println("  -XX:+HeapDumpOnOutOfMemoryError");
        System.out.println("  -XX:HeapDumpPath=/data/dump/");
        System.out.println("  -Xlog:gc*:file=/data/log/gc.log:time,uptime:filecount=10,filesize=50M");
        System.out.println("  OOM 现场只发生一次，dump 是唯一能事后复盘的东西");
    }

    static String trend(long[] s) {
        long diff = s[s.length - 1] - s[0];
        if (diff > 1024) {
            return "持续上升（+" + kb(diff) + "KB），疑似泄漏";
        }
        if (diff < -1024) {
            return "下降（" + kb(diff) + "KB），GC 生效";
        }
        return "基本持平（" + kb(diff) + "KB），正常";
    }

    static long usedAfterGc() {
        Runtime rt = Runtime.getRuntime();
        System.gc();
        sleep(20);
        return rt.totalMemory() - rt.freeMemory();
    }

    static long kb(long bytes) {
        return bytes / 1024;
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
1. 把 `LEAKY_CACHE` 的 value 换成 1MB 数组、循环 200 次，用 `-Xmx128m` 复现 OOM，并加上 dump 参数找到 dump 文件。
2. 写一个 `ThreadLocal<byte[]>` 在线程池里不 `remove` 的例子，说明为什么线程复用会导致泄漏。
3. 说明"缓存"和"泄漏"在监控曲线上有什么区别，各自该看哪几个指标。

### 30-08 CPU 飙高与线程状态分析

**知识点**
- 排查顺序：`top -Hp <pid>` 找到占 CPU 最高的**线程 id**（十进制）→ 转十六进制 → `jstack <pid> | grep -A 30 <nid>` 找到该线程的栈 → 看它卡在哪个方法。
- 线程状态要分清：**RUNNABLE**（在跑或在等 CPU）、**BLOCKED**（等 synchronized 锁）、**WAITING/TIMED_WAITING**（`wait`/`park`/`sleep`）、**NEW/TERMINATED**。
- "CPU 高"通常有三类：**死循环/正则回溯**（RUNNABLE 且栈顶是自己的业务方法）、**频繁 GC**（栈顶是 `GCTaskThread`，要转去看 GC 日志）、**大量线程上下文切换**（线程数暴涨、`BLOCKED` 多）。
- "CPU 不高但接口慢"更常见：线程都在 `WAITING` 等下游（连接池耗尽、锁竞争、`sleep` 式重试）。这时候看的是**线程池活跃数**与**等待队列长度**，不是 CPU。
- **线程数不是越多越好**：CPU 密集型的合理线程数 ≈ 核数 + 1；IO 密集型 ≈ 核数 × (1 + 等待时间/计算时间)，但受下游连接池上限约束。
- 易错：拿 `jstack` 只抓一次（瞬时快照可能刚好错过）；用 `grep` 匹配线程名而不是 nid（nid 才是线程号）；看到 `RUNNABLE` 就以为在执行业务代码（可能在读 socket，属于 IO 等待但状态仍是 RUNNABLE）。

**示例代码**
```java
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class CpuHotspot {
    static volatile boolean running = true;
    static volatile long sink;

    public static void main(String[] args) throws Exception {
        ThreadMXBean tmx = ManagementFactory.getThreadMXBean();
        if (tmx.isThreadCpuTimeSupported() && !tmx.isThreadCpuTimeEnabled()) {
            tmx.setThreadCpuTimeEnabled(true);
        }
        System.out.println("=== 模拟一个「CPU 飙高」的线上进程 ===");
        System.out.println("  biz-hot-1 / biz-hot-2：业务线程里写了死循环（反例）");
        System.out.println("  idle-worker：正常轮询线程，大部分时间在 sleep");
        System.out.println("  cpu-time-supported=" + tmx.isThreadCpuTimeSupported()
                + "，cpu-time-enabled=" + tmx.isThreadCpuTimeEnabled());

        Thread hot1 = new Thread(() -> spin("biz-hot-1"), "biz-hot-1");
        Thread hot2 = new Thread(() -> spin("biz-hot-2"), "biz-hot-2");
        Thread idle = new Thread(() -> {
            while (running) {
                sleep(50);
            }
        }, "idle-worker");
        for (Thread t : new Thread[]{hot1, hot2, idle}) {
            t.setDaemon(true);
            t.start();
        }

        sleep(300);
        System.out.println();
        System.out.println("=== 第一步：按 CPU 时间排序，找最热的线程 ===");
        long[] ids = {hot1.threadId(), hot2.threadId(), idle.threadId()};
        long[] before = cpuSnapshot(tmx, ids);
        long wallStart = System.nanoTime();
        sleep(500);
        long wallMs = (System.nanoTime() - wallStart) / 1_000_000;
        long[] after = cpuSnapshot(tmx, ids);

        List<Row> rows = new ArrayList<>();
        for (int i = 0; i < ids.length; i++) {
            long deltaMs = (after[i] - before[i]) / 1_000_000;
            double pct = wallMs > 0 ? deltaMs * 100.0 / wallMs : 0;
            rows.add(new Row(ids[i], threadName(tmx, ids[i]), deltaMs, pct));
        }
        rows.sort(Comparator.comparingDouble((Row r) -> r.cpuPct).reversed());
        System.out.printf("  %-8s %-14s %10s %8s%n", "nid", "线程名", "CPU时间ms", "占用率");
        for (Row r : rows) {
            System.out.printf("  %-8s %-14s %10d %7.1f%%%n",
                    Long.toHexString(r.id), r.name, r.cpuMs, r.cpuPct);
        }

        System.out.println();
        System.out.println("=== 第二步：把最热线程的 nid 转十六进制，去 jstack 里找 ===");
        Row hottest = rows.get(0);
        System.out.println("  top -Hp <pid> 看到十进制 tid=" + hottest.id
                + " -> 十六进制 nid=0x" + Long.toHexString(hottest.id));
        System.out.println("  jstack <pid> | grep -A 30 'nid=0x" + Long.toHexString(hottest.id) + "'");
        System.out.println("  该线程当前状态与栈顶：");
        ThreadInfo ti = tmx.getThreadInfo(hottest.id, 8);
        if (ti != null) {
            System.out.println("    \"" + ti.getThreadName() + "\" " + ti.getThreadState()
                    + (ti.getLockName() == null ? "" : " on " + ti.getLockName()));
            for (StackTraceElement e : ti.getStackTrace()) {
                System.out.println("      at " + e);
            }
        }
        System.out.println("  栈顶是业务方法 -> 死循环/算法问题；栈顶是 GCTaskThread -> 去看 GC");
        System.out.println("  （JVM 内部线程名：GC Thread#、G1 Young RemSet Sampling、C2 CompilerThread）");

        System.out.println();
        System.out.println("=== 第三步：线程状态分布 ===");
        ThreadInfo[] all = tmx.dumpAllThreads(false, false);
        int runnable = 0, blocked = 0, waiting = 0, timed = 0, other = 0;
        for (ThreadInfo info : all) {
            switch (info.getThreadState()) {
                case RUNNABLE -> runnable++;
                case BLOCKED -> blocked++;
                case WAITING -> waiting++;
                case TIMED_WAITING -> timed++;
                default -> other++;
            }
        }
        System.out.println("  RUNNABLE=" + runnable + " BLOCKED=" + blocked
                + " WAITING=" + waiting + " TIMED_WAITING=" + timed + " 其他=" + other);
        System.out.println("  总线程数=" + tmx.getThreadCount() + "，峰值=" + tmx.getPeakThreadCount());
        System.out.println("  判读：RUNNABLE 多 + 业务栈 -> CPU 型；WAITING 多 -> 等下游型");

        System.out.println();
        System.out.println("=== 第四步：线程池该开多大 ===");
        int cores = Runtime.getRuntime().availableProcessors();
        System.out.println("  本机核数：" + cores);
        System.out.println("  CPU 密集型：核数 + 1 = " + (cores + 1));
        int ioThreads = cores * (1 + 200 / 20);
        System.out.println("  IO 密集型（等待 200ms / 计算 20ms）：核数 x (1 + 10) = " + ioThreads);
        System.out.println("  但还要受下游连接池上限约束：线程再多，连接只有 20 个，多出来的都在排队");

        running = false;
        hot1.join(100);
        hot2.join(100);
        idle.join(100);
        System.out.println();
        System.out.println("  已停止模拟线程，sink=" + (sink != 0));
    }

    /** 反例：死循环占满一个核 */
    static void spin(String tag) {
        long x = tag.hashCode();
        while (running) {
            x = x * 6364136223846793005L + 1442695040888963407L;
            sink = x;
        }
    }

    static long[] cpuSnapshot(ThreadMXBean tmx, long[] ids) {
        long[] out = new long[ids.length];
        for (int i = 0; i < ids.length; i++) {
            out[i] = tmx.getThreadCpuTime(ids[i]);
        }
        return out;
    }

    static String threadName(ThreadMXBean tmx, long id) {
        ThreadInfo info = tmx.getThreadInfo(id);
        return info == null ? "unknown" : info.getThreadName();
    }

    static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    record Row(long id, String name, long cpuMs, double cpuPct) {
    }
}
```

**课后练习**
1. 用 `jstack` 抓取示例进程，找到 `biz-hot-1` 的 nid 与栈顶方法，写出完整命令。
2. 造一个 20 个线程抢同一把锁的例子，观察 `BLOCKED` 数量与 `jstack` 里的 `waiting to lock`。
3. 一个 8 核机器跑 IO 密集型任务（等待 90ms、计算 10ms），估算线程数上限，并说明为什么实际还要打折。

### 30-09 GC 日志分析与参数调优

**知识点**
- 开 GC 日志：JDK 9+ 用 `-Xlog:gc*:file=gc.log:time,uptime:filecount=10,filesize=50M`，JDK 8 用 `-XX:+PrintGCDetails -XX:+PrintGCDateStamps -Xloggc:gc.log`。
- 日志里要抓四个数：**Young GC 频率**、**Young GC 平均停顿**、**Full GC 次数**、**Full GC 后老年代占用**。前两个决定"平时卡不卡"，后两个决定"会不会雪崩"。
- 典型判读：
  - Young GC 很频繁（< 1s 一次）且每次回收量小 → 新生代太小，调 `-Xmn` 或 `-XX:NewRatio`。
  - 每次 Young GC 后存活对象很多 → 对象生命周期偏长，检查是否有大缓存或批处理。
  - Full GC 频繁但回收后老年代降不下来 → 内存泄漏，去 dump。
  - Full GC 后老年代降下来了但仍频繁 → 老年代太小或晋升太快，调 `-Xmx`/`-XX:MaxTenuringThreshold`。
- **停顿目标**不是越小越好：`MaxGCPauseMillis` 设太小，G1 每次只回收少量 Region，GC 次数暴增、吞吐下降。
- **堆大小**经验：`-Xms` 与 `-Xmx` 设成一样，避免动态扩容抖动；容器里必须让 JVM 感知 cgroup 限制（JDK 10+ 默认 `UseContainerSupport`，或显式 `-XX:MaxRAMPercentage=75`）。
- 易错：只调 `-Xmx` 不调 `-Xmn`（新生代默认只占 1/3）；把 `-Xmn` 设得过大导致老年代过小、Full GC 更频繁；在 32GB 边界上开堆导致指针压缩失效。

**示例代码**
```java
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GcLogAnalysis {
    /** 一行 Young GC 日志的简化形态 */
    static final String[] LOG = {
            "[2.113s][info][gc] GC(0) Pause Young (Normal) 512M->48M(1024M) 8.120ms",
            "[2.980s][info][gc] GC(1) Pause Young (Normal) 560M->52M(1024M) 7.640ms",
            "[3.851s][info][gc] GC(2) Pause Young (Normal) 564M->55M(1024M) 9.010ms",
            "[4.702s][info][gc] GC(3) Pause Young (Normal) 567M->58M(1024M) 8.330ms",
            "[5.560s][info][gc] GC(4) Pause Young (Normal) 570M->60M(1024M) 7.980ms",
            "[6.401s][info][gc] GC(5) Pause Young (Normal) 572M->61M(1024M) 8.450ms",
            "[7.260s][info][gc] GC(6) Pause Young (Normal) 573M->62M(1024M) 8.100ms",
            "[8.120s][info][gc] GC(7) Pause Young (Normal) 574M->63M(1024M) 8.770ms",
            "[9.010s][info][gc] GC(8) Pause Young (Normal) 575M->64M(1024M) 8.220ms",
            "[9.880s][info][gc] GC(9) Pause Young (Normal) 576M->65M(1024M) 8.600ms",
            "[10.750s][info][gc] GC(10) Pause Young (Normal) 577M->66M(1024M) 9.140ms",
            "[12.100s][info][gc] GC(11) Pause Full (Ergonomics) 1000M->820M(1024M) 620.500ms",
            "[13.400s][info][gc] GC(12) Pause Young (Normal) 700M->120M(1024M) 9.300ms",
            "[14.300s][info][gc] GC(13) Pause Young (Normal) 720M->125M(1024M) 9.050ms",
            "[15.200s][info][gc] GC(14) Pause Young (Normal) 725M->128M(1024M) 9.400ms",
            "[16.100s][info][gc] GC(15) Pause Young (Normal) 728M->130M(1024M) 9.200ms",
            "[17.000s][info][gc] GC(16) Pause Young (Normal) 730M->132M(1024M) 9.600ms",
            "[18.900s][info][gc] GC(17) Pause Full (Ergonomics) 1010M->880M(1024M) 780.200ms",
            "[20.200s][info][gc] GC(18) Pause Young (Normal) 800M->150M(1024M) 9.800ms",
            "[21.100s][info][gc] GC(19) Pause Young (Normal) 802M->152M(1024M) 9.500ms"
    };

    static final Pattern P = Pattern.compile(
            "GC\\((\\d+)\\)\\s+Pause\\s+(Young|Full)[^\\n]*?(\\d+)M->(\\d+)M\\((\\d+)M\\)\\s+([\\d.]+)ms");

    public static void main(String[] args) {
        System.out.println("=== 解析 GC 日志（简化样例 " + LOG.length + " 行）===");
        List<Event> events = new ArrayList<>();
        for (String line : LOG) {
            Matcher m = P.matcher(line);
            if (m.find()) {
                events.add(new Event(Integer.parseInt(m.group(1)), m.group(2),
                        Long.parseLong(m.group(3)), Long.parseLong(m.group(4)),
                        Long.parseLong(m.group(5)), Double.parseDouble(m.group(6))));
            }
        }
        System.out.println("  成功解析 " + events.size() + " 条，未匹配 " + (LOG.length - events.size()) + " 条");

        System.out.println();
        System.out.println("=== 关键指标 ===");
        int youngCount = 0, fullCount = 0;
        double youngPauseSum = 0, youngPauseMax = 0, fullPauseSum = 0, fullPauseMax = 0;
        long heapMb = 0, lastFullAfter = 0, peakBefore = 0;
        for (Event e : events) {
            heapMb = e.heapMb;
            peakBefore = Math.max(peakBefore, e.beforeMb);
            if ("Young".equals(e.type)) {
                youngCount++;
                youngPauseSum += e.pauseMs;
                youngPauseMax = Math.max(youngPauseMax, e.pauseMs);
            } else {
                fullCount++;
                fullPauseSum += e.pauseMs;
                fullPauseMax = Math.max(fullPauseMax, e.pauseMs);
                lastFullAfter = e.afterMb;
            }
        }
        double durationSec = 21.1;
        System.out.printf("  运行时长            %.1fs%n", durationSec);
        System.out.printf("  Young GC 次数       %d（平均 %.0fms 一次）%n",
                youngCount, durationSec * 1000 / Math.max(1, youngCount));
        System.out.printf("  Young GC 平均停顿   %.1fms，最大 %.1fms%n",
                youngPauseSum / Math.max(1, youngCount), youngPauseMax);
        System.out.printf("  Full  GC 次数       %d%n", fullCount);
        System.out.printf("  Full  GC 平均停顿   %.1fms，最大 %.1fms%n",
                fullPauseSum / Math.max(1, fullCount), fullPauseMax);
        System.out.printf("  堆峰值              %dMB / %dMB%n", peakBefore, heapMb);
        System.out.printf("  Full GC 后老年代    %dMB（回收率仅 %.0f%%）%n",
                lastFullAfter, (1 - lastFullAfter * 1.0 / heapMb) * 100);
        System.out.printf("  GC 总停顿           %.1fms，占运行时间 %.1f%%%n",
                youngPauseSum + fullPauseSum, (youngPauseSum + fullPauseSum) / (durationSec * 1000) * 100);

        System.out.println();
        System.out.println("=== 诊断结论 ===");
        double avgYoungInterval = durationSec * 1000 / Math.max(1, youngCount);
        if (avgYoungInterval < 1500) {
            System.out.println("  [问题1] Young GC 间隔 " + (long) avgYoungInterval
                    + "ms，过于频繁 -> 新生代偏小，先加大 -Xmn");
        }
        if (fullCount > 0) {
            System.out.println("  [问题2] 出现 " + fullCount + " 次 Full GC，单次最长 "
                    + (long) fullPauseMax + "ms -> 会直接造成接口超时");
        }
        double reclaim = 1 - lastFullAfter * 1.0 / heapMb;
        if (reclaim < 0.3) {
            System.out.println("  [问题3] Full GC 后只回收了 " + Math.round(reclaim * 100)
                    + "%，老年代仍占 " + lastFullAfter + "MB -> 疑似内存泄漏，去 dump");
        }
        System.out.println("  [推断] 每次 Young GC 后存活约 "
                + (events.get(0).afterMb - 40) + "MB 以上并持续上涨，说明有对象在长期存活");

        System.out.println();
        System.out.println("=== 调参建议（堆 1GB，容器限制 1.5GB）===");
        String[][] params = {
                {"-Xms1g -Xmx1g", "固定堆，避免扩容抖动"},
                {"-Xmn400m", "新生代从 1/3 提到 40%，降低 Young GC 频率"},
                {"-XX:MaxGCPauseMillis=200", "G1 目标停顿，先给 200ms 别给 50ms"},
                {"-XX:MaxTenuringThreshold=6", "存活对象多时适当降低，避免长期占 Survivor"},
                {"-XX:MaxRAMPercentage=70", "容器内按比例设堆，给元空间/直接内存留余量"},
                {"-Xlog:gc*:file=gc.log:time,uptime:filecount=10,filesize=50M", "日志滚动，别写满磁盘"}
        };
        for (String[] p : params) {
            System.out.printf("  %-58s %s%n", p[0], p[1]);
        }
        System.out.println();
        System.out.println("  调参顺序：先定位（泄漏/太小/晋升快）-> 再改一个参数 -> 压测对比 -> 再改下一个");
        System.out.println("  一次改五个参数，出了问题根本不知道是哪个引起的");
    }

    record Event(int id, String type, long beforeMb, long afterMb, long heapMb, double pauseMs) {
    }
}
```

**课后练习**
1. 在示例里再加 3 条 Young GC 日志，说明平均间隔与停顿如何变化，结论是否改变。
2. 如果 Full GC 后老年代能降到 100MB，但每 10 秒还是来一次 Full GC，应该调什么？为什么？
3. 解释为什么 `-Xmn` 设得过大反而会让 Full GC 更频繁。

### 30-10 性能压测与瓶颈定位

**知识点**
- 三个核心指标：**吞吐量**（QPS/TPS）、**延迟**（P50/P95/P99/P999，绝不能只看平均值）、**错误率**。平均值会把长尾藏起来，用户感受到的是 P99。
- **利特尔法则**：`并发数 = QPS × 平均延迟`。反过来说，20 个连接、每个请求 50ms，理论 QPS 上限就是 400；想提到 1000 就必须加连接或降延迟。
- **利用率与排队**：服务利用率 ρ 越接近 1，排队延迟非线性爆炸（近似 `响应时间 = 服务时间 / (1 - ρ)`）。所以生产别把下游压到 80% 以上。
- **阿姆达尔定律**：只优化占 20% 耗时的模块，整体最多快 1.25 倍；优化前先做**耗时分解**（CPU、IO、锁等待、序列化、网络）。
- 压测要看**拐点**：并发往上加，QPS 不再涨而 P99 开始飙升的那个点，就是系统容量上限。
- 瓶颈定位顺序：**先看是不是压测机自己打满**（CPU/带宽/端口耗尽）→ 再看应用（线程池满、GC、锁）→ 再看中间件（连接池、慢 SQL、Redis 大 key）→ 最后看下游。
- 易错：只压不监控（不知道瓶颈在哪一层）；用平均延迟下结论；压测数据和线上数据量差几个数量级（索引命中率完全不同）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PerfTuning {
    public static void main(String[] args) {
        System.out.println("=== 1. 延迟分布：平均值会骗人 ===");
        long[] latencies = {
                12, 13, 12, 14, 13, 12, 15, 13, 12, 14,
                13, 12, 14, 13, 12, 13, 14, 12, 13, 14,
                15, 13, 12, 14, 13, 12, 13, 14, 13, 12,
                16, 14, 13, 15, 14, 13, 900, 1200, 1500, 2100
        };
        long[] sorted = latencies.clone();
        Arrays.sort(sorted);
        double avg = Arrays.stream(latencies).average().orElse(0);
        System.out.printf("  样本 %d 个，平均 %.1fms%n", latencies.length, avg);
        System.out.printf("  P50=%dms  P90=%dms  P95=%dms  P99=%dms  Max=%dms%n",
                pct(sorted, 50), pct(sorted, 90), pct(sorted, 95), pct(sorted, 99), sorted[sorted.length - 1]);
        System.out.println("  平均 65ms 看着还行，但 P99 已到 1500ms -> 1% 的用户在骂人");

        System.out.println();
        System.out.println("=== 2. 利特尔法则：并发、QPS、延迟的关系 ===");
        int[] concurrency = {10, 20, 50, 100};
        int latencyMs = 50;
        System.out.println("  假设单请求平均延迟 " + latencyMs + "ms");
        System.out.printf("  %-10s %-12s %s%n", "并发数", "理论QPS", "说明");
        for (int c : concurrency) {
            int qps = c * 1000 / latencyMs;
            System.out.printf("  %-10d %-12d %s%n", c, qps, c >= 50 ? "需要更多连接/更短延迟" : "受连接数直接限制");
        }
        System.out.println("  目标 QPS=1000 且延迟 50ms -> 至少需要 " + (1000 * latencyMs / 1000) + " 个并发");
        System.out.println("  连接池只有 20 个时，QPS 上限就是 " + (20 * 1000 / latencyMs) + "，调线程数没用");

        System.out.println();
        System.out.println("=== 3. 利用率与排队延迟（为什么不能压到 90%）===");
        System.out.printf("  %-10s %-14s %-14s %s%n", "利用率", "排队倍数", "响应时间", "感受");
        for (double rho : new double[]{0.3, 0.5, 0.7, 0.8, 0.9, 0.95}) {
            double factor = 1 / (1 - rho);
            double rt = latencyMs * factor;
            String feel = rho < 0.6 ? "轻松" : rho < 0.8 ? "正常" : rho < 0.9 ? "开始抖" : "雪崩边缘";
            System.out.printf("  %-10.0f%% %-14.1f %-14.0f %s%n", rho * 100, factor, rt, feel);
        }
        System.out.println("  70% -> 90% 利用率，延迟翻了近 3 倍；这就是压测拐点的来源");

        System.out.println();
        System.out.println("=== 4. 阿姆达尔定律：优化收益上限 ===");
        double[] parts = {45, 25, 15, 10, 5};
        String[] names = {"数据库查询", "业务计算", "序列化", "网络传输", "日志与其他"};
        System.out.printf("  %-14s %8s %s%n", "环节", "耗时占比", "优化 50% 后整体收益");
        for (int i = 0; i < parts.length; i++) {
            double gain = parts[i] / 2;
            double newTotal = 100 - gain;
            System.out.printf("  %-14s %7.0f%% %14.1f%%%n", names[i], parts[i], (100 - newTotal));
        }
        System.out.println("  优化最大的那块（数据库 45%），也只能把总耗时降 22.5%");
        System.out.println("  想快一倍：要么把数据库耗时砍到 10% 以内，要么上缓存绕过它");

        System.out.println();
        System.out.println("=== 5. 压测曲线：找拐点 ===");
        int[] qpsAt = {500, 900, 1500, 1800, 1850, 1860, 1200};
        int[] p99At = {40, 45, 60, 90, 180, 450, 2000};
        int[] errAt = {0, 0, 0, 0, 0, 1, 12};
        System.out.printf("  %-8s %-8s %-8s %-8s %s%n", "并发", "QPS", "P99ms", "错误%", "判读");
        int[] conc = {10, 20, 40, 60, 80, 100, 120};
        for (int i = 0; i < conc.length; i++) {
            String verdict = errAt[i] > 0 ? "已过载" : p99At[i] > 150 ? "接近拐点" : "健康";
            System.out.printf("  %-8d %-8d %-8d %-8d %s%n", conc[i], qpsAt[i], p99At[i], errAt[i], verdict);
        }
        System.out.println("  拐点在并发 60~80 之间：QPS 不再涨、P99 抬头 -> 这就是容量上限");
        System.out.println("  线上留 30%~40% 余量，所以这个系统对外报 1200 QPS 比较安全");

        System.out.println();
        System.out.println("=== 6. 压测前的检查清单 ===");
        List<String> checklist = new ArrayList<>(List.of(
                "压测机自身 CPU/带宽/文件句柄是否够（别把施压方压死）",
                "应用线程池、连接池、队列长度是否已配置且可观测",
                "GC 日志已开，压测期间盯着 Full GC",
                "数据库数据量与线上同量级，索引命中率接近",
                "限流、熔断阈值先调到不触发，否则压出来的是限流值",
                "准备回滚方案：压完把参数改回去"
        ));
        for (int i = 0; i < checklist.size(); i++) {
            System.out.println("  " + (i + 1) + ". " + checklist.get(i));
        }
    }

    static long pct(long[] sorted, int p) {
        int idx = (int) Math.ceil(p / 100.0 * sorted.length) - 1;
        idx = Math.max(0, Math.min(sorted.length - 1, idx));
        return sorted[idx];
    }
}
```

**课后练习**
1. 给示例补 10 个 3000ms 以上的样本，观察 P95/P99/平均各变化多少，说明为什么压测报告必须给分位数。
2. 某接口 QPS 800、平均延迟 40ms，连接池只有 20 个，够不够？用利特尔法则算一遍。
3. 一个请求里数据库占 60%、计算占 30%、网络占 10%，把数据库换成缓存后耗时降到原来的多少？用阿姆达尔定律解释。

### 30-11 线上问题排查综合实战

**知识点**
- 线上排查的固定套路：**先止损再定位**（限流/降级/回滚/扩容），**保留现场**（dump、日志、指标快照），**按证据缩小范围**，**根因 + 复现 + 修复 + 防复发**四件套。
- 三类高频故障的入口信号：
  - **CPU 100%** → `top -Hp` + `jstack`，看是业务死循环、GC 线程还是线程数暴涨。
  - **内存涨/OOM** → GC 日志看 Full GC 后是否回落，`jmap -histo:live` 看谁在涨，dump 找引用链。
  - **接口超时/RT 上涨** → 看线程池活跃数与队列、连接池等待、下游 RT、慢 SQL、锁等待。
- **雪崩链条**往往是"一个下游变慢 → 上游线程全被占住 → 上游也不可用"。所以必须有**超时 + 熔断 + 隔离 + 限流**四件套，且超时预算要逐层递减。
- 排查时**时间线**极其重要：把"几点几分做了什么变更、指标怎么变"对齐，能直接指向根因（发布、配置变更、流量突增、定时任务、数据倾斜）。
- 复盘要写：影响面（多少用户/订单/金额）、时间线、根因、为什么监控没提前发现、改进项与 owner。**不追责，追机制**。
- 易错：一上来就重启（现场全丢，同样的坑下周再踩）；只修表象（把超时调大，雪崩更快）；没有回滚预案；复盘写成"某某操作失误"而不是"为什么这个操作能造成这么大影响"。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class OnlineTroubleshootCase {
    static final List<String> TIMELINE = new ArrayList<>();
    static final Map<String, String> METRICS = new LinkedHashMap<>();

    public static void main(String[] args) {
        System.out.println("=== 故障现象（14:02 开始）===");
        System.out.println("  订单服务 P99 从 80ms 涨到 3200ms，错误率 12%");
        System.out.println("  CPU 42%（不高），线程池 active=200/200，队列堆积 1.8 万");
        System.out.println("  下游「用户服务」RT 从 30ms 涨到 1200ms，但它的 CPU 只有 35%");
        System.out.println("  20 分钟前刚发过一个版本：给用户服务加了「查积分」的同步调用");

        System.out.println();
        System.out.println("=== 第一步：止损（先恢复，不查原因）===");
        step("14:05", "回滚用户服务到上一版本", "RT 从 1200ms 降到 40ms");
        step("14:06", "订单服务线程池队列清理 + 临时扩容 2 个实例", "active 从 200 降到 60");
        step("14:08", "错误率回到 0.2%，P99 回到 120ms", "业务恢复");
        System.out.println("  原则：能回滚就先回滚，定位可以在恢复之后做");

        System.out.println();
        System.out.println("=== 第二步：保留现场 ===");
        String[] evidence = {
                "订单服务：jstack x3（间隔 5s），看线程都卡在哪",
                "订单服务：jmap -histo:live，确认没有对象异常增长",
                "用户服务：GC 日志 + jstat，确认不是 GC 问题",
                "两边：线程池指标、连接池等待数、下游调用 RT 曲线",
                "变更记录：14:00 之前的所有发布/配置/DDL 操作",
                "入口流量：QPS 是否突增（本次没有，稳定在 900）"
        };
        for (String e : evidence) {
            System.out.println("  - " + e);
        }

        System.out.println();
        System.out.println("=== 第三步：按证据缩小范围 ===");
        diagnose("CPU 42% 不高", "排除死循环、GC 打满", "去看等待类问题");
        diagnose("线程池 active=200/200 且队列堆积", "请求进得来出不去", "瓶颈在依赖调用，不在计算");
        diagnose("用户服务 CPU 35% 但 RT 1200ms", "不是算力不足", "在等什么：锁、连接池、还是串行调用");
        diagnose("jstack 显示大量线程停在 getUserPoints", "订单线程都在等用户服务", "确认是同步调用拖死上游");
        diagnose("用户服务栈显示大量 BLOCKED 在同一把锁", "积分查询用了全局锁 + 串行查库", "根因锁定");

        System.out.println();
        System.out.println("=== 第四步：根因 ===");
        System.out.println("  新版本在「下单主链路」里同步调用了用户服务的积分查询；");
        System.out.println("  该查询内部用 synchronized 包住了一次全表扫描，QPS 900 时全部串行化；");
        System.out.println("  用户服务 RT 上升 -> 订单服务 200 个线程被占满 -> 队列堆积 -> 超时与错误率飙升。");
        System.out.println("  本质：把非核心依赖放进了核心链路，且没有超时、没有隔离、没有熔断。");

        System.out.println();
        System.out.println("=== 第五步：修复（分短期/长期）===");
        String[][] fixes = {
                {"短期", "积分查询改为异步消息，下单只发事件不等结果", "主链路不再依赖用户服务"},
                {"短期", "所有跨服务调用加超时（订单->用户 300ms）", "慢下游不会无限占用线程"},
                {"短期", "线程池按依赖隔离，用户服务调用独立池", "一个依赖挂掉不拖垮整个服务"},
                {"长期", "积分查询去掉全局锁，改成分片查询 + 缓存", "消除串行瓶颈"},
                {"长期", "接入熔断：连续失败 20 次或错误率 > 50% 直接快速失败", "故障时不再打满下游"},
                {"长期", "压测加入「下游变慢」场景，验证超时与降级", "把故障演练变成常规动作"}
        };
        System.out.printf("  %-8s %-52s %s%n", "类型", "动作", "目的");
        for (String[] f : fixes) {
            System.out.printf("  %-8s %-52s %s%n", f[0], f[1], f[2]);
        }

        System.out.println();
        System.out.println("=== 第六步：复盘要点 ===");
        String[] review = {
                "影响面：14:02-14:08，约 6 分钟，失败订单 1.2 万笔，涉及金额 86 万",
                "为什么监控没提前发现：只有 RT 告警，没有「线程池饱和度」与「下游 RT」告警",
                "为什么变更能上线：缺少「核心链路新增同步依赖」的评审卡点",
                "改进项：加线程池/连接池饱和度告警、下游 RT 告警、变更分级与灰度",
                "改进项 owner 与截止时间必须写清楚，否则复盘等于没做"
        };
        for (String r : review) {
            System.out.println("  - " + r);
        }

        System.out.println();
        System.out.println("=== 排查时间线 ===");
        for (String t : TIMELINE) {
            System.out.println("  " + t);
        }

        System.out.println();
        System.out.println("=== 关键指标快照 ===");
        METRICS.forEach((k, v) -> System.out.printf("  %-22s %s%n", k, v));

        System.out.println();
        System.out.println("=== 一句话总结 ===");
        System.out.println("  线上问题 90% 不是「代码写错了一行」，而是「依赖关系没设计好」：");
        System.out.println("  核心链路不能依赖非核心服务，任何跨进程调用都必须有超时、隔离和熔断。");
    }

    static void step(String time, String action, String result) {
        TIMELINE.add(time + "  " + action + "  ->  " + result);
        METRICS.put(time, result);
        System.out.println("  [" + time + "] " + action);
        System.out.println("          结果：" + result);
    }

    static void diagnose(String evidence, String inference, String next) {
        System.out.println("  证据：" + evidence);
        System.out.println("    推断：" + inference);
        System.out.println("    下一步：" + next);
    }
}
```

**课后练习**
1. 按本课的六步套路，为"Redis 连接池耗尽导致接口超时"写一份排查方案（现象、证据、根因、修复）。
2. 说明为什么"把超时从 1s 调到 5s"在雪崩场景下会让情况更糟。
3. 写一份复盘模板，至少包含影响面、时间线、根因、监控缺口、改进项与 owner 六项。
