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
