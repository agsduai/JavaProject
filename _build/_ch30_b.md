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
