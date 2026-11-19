# 第59章 大数据离线计算与 Spark 原理

第46章讲了湖仓存储，本章讲它的计算引擎。离线计算的定位是"数据全部落地后的大批量加工"（T+1 报表、特征回填、对账），与第43章的流处理互为镜像。Spark 的四个关键认知决定了它的一切性能表现：惰性求值让优化器看到全图、窄依赖让算子可以流水线执行、Shuffle 是唯一的重分布成本、血统让失败只重算一小块。本章按「全景 → RDD → 惰性求值与 DAG → 分区与并行度 → Shuffle 代价 → 宽窄依赖与容错 → Catalyst 优化器 → 数据倾斜调优 → 资源模型与推测执行 → 与湖仓集成 → 订单加工链案例」的顺序讲。所有示例仍是纯 JDK：分区与 Stage 切分真实推演、Shuffle 传输量真算、血统重算范围对比、倾斜诊断与加盐两阶段聚合真跑闭环。

### 59-01 离线计算的定位与 Spark 全景

**知识点**
- **离线计算是"数据已全部落地后的大批量加工"**：T+1 报表、用户画像、特征回填、对账——**与流处理（43 章）的分工是"有界数据 vs 无界数据"**（43-01 的分界再来一次，这次站在批的一侧）。**能跑批的别上流**，43-01 的结论在这里是主角。
- **Spark 的四层结构**：**Spark Core**（RDD、调度、内存管理）-> **Spark SQL**（DataFrame/Dataset + Catalyst 优化器）-> **Structured Streaming**（微批，43-10）；**Spark on YARN/K8s**（资源管理层）。**Core 是地基，SQL 是事实上的主入口**。
- **Spark 相对 MapReduce 的三大改进**：① **内存计算**（中间结果不落盘，MR 每步落 HDFS）；② **DAG 优化**（多步算子合并，MR 只有 map/shuffle/reduce 三段）；③ **惰性求值 + 血统容错**（59-03/59-06）。
- **执行模型的一句话总结**：Driver 解析作业成 DAG -> 切分 Stage -> 分发 Task 到 Executor -> Executor 在内存里迭代计算 -> Shuffle 时落盘 -> 结果回 Driver（或写湖表 46 章）。
- **Spark 不是数据库**：无索引、无事务、无并发更新——**它是"一次大规模扫描加工"的引擎**；交互式点查用 MPP（46-07），事务用 MySQL。
- **学习重点排序**：分区与并行度（59-04）-> Shuffle（59-05）-> 数据倾斜（59-08）-> 资源模型（59-09）——**80% 的 Spark 性能问题在这四件事里**。
- 易错点：用 Spark 做点查（该用 MPP）；小数据量上 Spark（调度开销大于计算）；把 DataFrame 与 RDD 混着用不设断点（优化器失效）；以为"内存计算"意味着不落盘（Shuffle 必落盘）。

**示例代码**
```java
import java.util.List;

public class SparkOverview {
    record Layer(String name, String duty, String keyConcept) {
    }

    static void architecture() {
        System.out.println("【Spark 四层结构】");
        List<Layer> layers = List.of(
                new Layer("Spark Core", "RDD + 调度 + 内存管理", "分区/血统/惰性求值"),
                new Layer("Spark SQL", "DataFrame/Dataset + Catalyst", "逻辑计划 -> 物理计划"),
                new Layer("Structured Streaming", "微批流处理（43-10）", "增量查询"),
                new Layer("资源管理", "YARN / K8s / Standalone", "Executor 申请与释放"));
        for (Layer l : layers) {
            System.out.printf("  %-22s %-26s %s%n", l.name(), l.duty(), l.keyConcept());
        }
        System.out.println();
    }

    static void vsMapReduce() {
        System.out.println("【相对 MapReduce 的三个关键改进】");
        String[][] rows = {
                {"中间结果", "每步落 HDFS", "内存/本地盘，仅在 Shuffle 落盘"},
                {"执行模型", "固定 map-shuffle-reduce 三段", "任意 DAG，多算子合并"},
                {"容错", "重跑整个作业", "血统重算受影响分区（59-06）"},
                {"开发入口", "Java API 为主", "SQL/DataFrame 为主（59-07）"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-8s MR=%-26s Spark=%s%n", r[0], r[1], r[2]);
        }
        System.out.println();
    }

    public static void main(String[] args) {
        architecture();
        vsMapReduce();
        System.out.println("  性能问题 80% 集中在：分区/并行度、Shuffle、倾斜、资源模型");
    }
}
```

**课后练习**
1. 画出你的数据加工任务在 Spark 中的执行路径（Driver -> Stage -> Task），标注哪些步骤会落盘。
2. 用一个真实需求判断"该批还是该流"（43-01 决策表），说明延迟、成本、复杂度三方面理由。
3. 思考题：为什么 Spark 说"内存计算"却仍然绕不开 Shuffle 落盘？从数据交换的物理必然性分析。

---

### 59-02 RDD：弹性分布式数据集

**知识点**
- **RDD 的五个核心属性**：① **分区列表**（数据被切成 N 份）；② **依赖关系**（从哪个父 RDD 转换来）；③ **计算函数**（每个分区的计算逻辑）；④ **分区器**（可选，用于 Shuffle 前的数据分布，59-05）；⑤ **首选位置**（数据本地性，任务调度到数据所在节点）。
- **RDD 的两种操作**：**转换（Transformation）**（map/filter/join/groupBy，惰性，返回新 RDD）与**行动（Action）**（count/collect/saveAsTextFile，触发计算）——**"转换不执行"是理解 Spark 的基石**（59-03）。
- **"弹性"的三层含义**：**分区可重算**（血统容错 59-06）、**内存与磁盘可切换**（内存不够溢写本地盘）、**任务可重试**（失败任务在别的 Executor 重跑）。
- **RDD 的不可变性**：每次转换产生新 RDD，原 RDD 不变——**这让血统可以追溯、任务可以重算**（49-06 事件溯源的同款思想：不可变的历史 -> 可重放的结果）。
- **RDD vs DataFrame**：RDD 是"无结构的对象集合"（强类型但优化器无从下手）；DataFrame 是"带 schema 的行集合"（优化器可重排/裁剪/下推，59-07）。**现代写法优先 DataFrame**，RDD 只用于"需要细粒度控制"或"非结构化数据"。
- 易错点：以为 `rdd.map()` 立刻执行（惰性）；在每个 map 里创建数据库连接（连接数爆炸）；用 `collect()` 拉全量数据到 Driver（OOM 的经典原因）；RDD 里放不可序列化对象（Executor 反序列化失败）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class RddDemo {
    /** 极简 RDD：分区列表 + 依赖 + 计算函数 */
    static final class MiniRdd<T> {
        final List<List<T>> partitions;
        final String lineage;

        MiniRdd(List<List<T>> partitions, String lineage) {
            this.partitions = partitions;
            this.lineage = lineage;
        }

        static <T> MiniRdd<T> fromPartitions(List<List<T>> data, String label) {
            return new MiniRdd<>(data, label);
        }

        /** 转换：惰性 —— 只记录血统，不计算（本实现为了可视化会提前构造分区结构） */
        <R> MiniRdd<R> map(Function<T, R> fn, String opName) {
            List<List<R>> out = new ArrayList<>();
            for (List<T> p : partitions) {
                List<R> mapped = new ArrayList<>();
                for (T item : p) {
                    mapped.add(fn.apply(item));
                }
                out.add(mapped);
            }
            return new MiniRdd<>(out, lineage + " -> " + opName);
        }

        MiniRdd<T> filter(java.util.function.Predicate<T> pred, String opName) {
            List<List<T>> out = new ArrayList<>();
            for (List<T> p : partitions) {
                List<T> kept = new ArrayList<>();
                for (T item : p) {
                    if (pred.test(item)) {
                        kept.add(item);
                    }
                }
                out.add(kept);
            }
            return new MiniRdd<>(out, lineage + " -> " + opName);
        }

        /** 行动：触发收集（真实 Spark 返回结果到 Driver 或写存储） */
        List<T> collect() {
            List<T> all = new ArrayList<>();
            partitions.forEach(all::addAll);
            return all;
        }

        long count() {
            return partitions.stream().mapToLong(List::size).sum();
        }

        int partitionCount() {
            return partitions.size();
        }
    }

    public static void main(String[] args) {
        var rdd = MiniRdd.fromPartitions(
                List.of(List.of(6900L, 3900L), List.of(12900L, 500L), List.of(20000L)),
                "orders(3 partitions)");
        System.out.println("  分区数：" + rdd.partitionCount() + " 记录数：" + rdd.count());
        System.out.println("  血统：" + rdd.lineage);

        var filtered = rdd.filter(v -> v >= 3900L, "filter(>=3900)");
        var mapped = filtered.map(v -> v / 100, "map(cents->yuan)");
        System.out.println("  变换后血统：" + mapped.lineage);
        System.out.println("  行动 collect：" + mapped.collect() + "（此时才真正物化结果）");
        System.out.println("  各分区：" + mapped.partitions);
    }
}
```

**课后练习**
1. 实现一个 MiniRdd：支持 map/filter/flatMap/count/collect，并打印血统链，验证"转换只记录、行动才计算"。
2. 用 MiniRdd 实现"按分区求和"与"全局求和"（后者需要跨分区合并），说明数据何时需要移动（预示 59-05 Shuffle）。
3. 思考题：RDD 的不可变性带来了哪些能力？分别对应"血统容错""并发安全""可重放"三个场景说明。

---

### 59-03 惰性求值与 DAG 调度

**知识点**
- **惰性求值的本质：先构图，后优化，再执行**——转换操作只构建"逻辑执行图"，直到遇到行动操作才提交作业。**好处**：优化器整体看得到全图（可合并操作、可重排），避免"边算边存中间结果"。
- **窄依赖可以流水线执行**：`map -> filter -> map` 三个窄依赖算子可以在**同一个 Task 内逐条记录连续处理**（pipeline），无需落盘中间结果——**这是 Spark 快的核心原因之一**。
- **DAG 切分为 Stage 的规则：以 Shuffle 为界**——遇到宽依赖（59-06）就切出新 Stage；**一个 Stage 内全是窄依赖**，可以 pipeline；Stage 之间通过 Shuffle 数据交换落盘。
- **Task 是 Stage 的执行单元**：一个 Stage 的 Task 数 = 其最后一个 RDD 的分区数（59-04）；Task 被分发到数据所在的 Executor（数据本地性）。
- **DAG 的可视化与调优**：`df.explain(true)` 看逻辑/物理计划、Spark UI 看 Stage 与 Task 分布——**"为什么这个作业慢"的第一步永远是看 DAG 里有没有意外的 Shuffle**。
- 易错点：以为转换会执行（惰性）；在循环里反复 `count()`（每次触发全量计算）；不知道 `repartition` 会引入 Shuffle（59-05）；算子顺序不当导致 Filter 没被下推（DataFrame 会自动下推，RDD 不会）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.List;

public class DagDemo {
    /** 算子：标明依赖类型 */
    record Op(String name, boolean shuffles) {
    }

    /** 把算子序列按 Shuffle 边界切成 Stage */
    static List<List<Op>> toStages(List<Op> ops) {
        List<List<Op>> stages = new ArrayList<>();
        List<Op> current = new ArrayList<>();
        for (Op op : ops) {
            current.add(op);
            if (op.shuffles()) {                 // 宽依赖是 Stage 边界
                stages.add(new ArrayList<>(current));
                current.clear();
            }
        }
        if (!current.isEmpty()) {
            stages.add(current);
        }
        return stages;
    }

    static void plan(List<Op> ops, List<Integer> partitionsPerStage) {
        System.out.println("【算子序列】" + ops);
        List<List<Op>> stages = toStages(ops);
        System.out.println("【Stage 切分（以 Shuffle 为界）】");
        for (int i = 0; i < stages.size(); i++) {
            System.out.printf("  Stage %d：%s（Task 数=%d）%n", i, stages.get(i),
                    i < partitionsPerStage.size() ? partitionsPerStage.get(i) : -1);
        }
        long shuffleCount = ops.stream().filter(Op::shuffles).count();
        System.out.printf("  Shuffle 次数：%d（每次都是全量数据重分布 + 落盘）%n", shuffleCount);
        System.out.println();
    }

    public static void main(String[] args) {
        System.out.println("【好的顺序：先过滤再聚合】");
        plan(List.of(
                new Op("load", false),
                new Op("filter(amount>10000)", false),
                new Op("select(cols)", false),
                new Op("groupBy(city).sum(amount)", true),
                new Op("sort(desc)", true)), List.of(8, 200, 1));

        System.out.println("【坏的顺序：先聚合再过滤】");
        plan(List.of(
                new Op("load", false),
                new Op("groupBy(city).sum(amount)", true),
                new Op("filter(city='杭州')", false)), List.of(8, 200));
        System.out.println("  同样结果，Shuffle 数据量差一个数量级 —— 算子顺序决定性能");
    }
}
```

**课后练习**
1. 实现 Stage 切分器：输入算子序列（标注宽/窄依赖），输出 Stage 列表、每个 Stage 的 Task 数，并对比两种算子顺序的 Shuffle 数据量估算。
2. 为你的一个 Spark（或 SQL）作业画出 DAG，标出所有 Shuffle 点，找出"可以移动到 Shuffle 之前"的过滤。
3. 思考题：为什么"先 filter 后 join"通常比"先 join 后 filter"快？从 Shuffle 数据量与中间结果规模推导。

---

### 59-04 分区与并行度

**知识点**
- **分区数是并行度的物理上限**：分区数 = 并发 Task 数的上限（一个分区一个 Task）。**分区太少**（如 1 个）-> 只有 1 个 Task 跑，其余 Executor 空闲；**分区太多**（如 10 万小分区）-> Task 调度开销超过计算本身。
- **经验值**：**单分区数据量 128MB~1GB**；总分区数 ≈ 集群可用核数 × 2~3（让 Task 有波次感，避免长尾影响整体）；**Shuffle 后分区数可单独调整**（`spark.sql.shuffle.partitions` 默认 200，小数据量要下调）。
- **分区与文件的关系**：读文件时**分区数 ≈ 文件大小 ÷ 128MB**（HDFS 块大小），小文件多会导致分区过多（46-02 小文件问题的 Spark 侧）——**读前先 compaction**。
- **`repartition` vs `coalesce`**：`repartition(n)` 全量 Shuffle（可增可减，均匀）；`coalesce(n)` 仅合并（减少分区不 Shuffle，可能不均）——**减分区优先 coalesce，增分区或要均匀用 repartition**。
- **数据本地性**：Task 尽量调度到数据所在节点（`NODE_LOCAL` > `RACK_LOCAL` > `ANY`）——**分区过大 + 副本少时，远程拉取会严重拖慢**（46-03 存算分离的代价之一）。
- 易错点：用默认 200 分区跑小数据（任务开销占大头）；`coalesce(1)` 写单文件（并行度归零）；分区数按"总核数"而非"核数×2~3"设（长尾拖慢整体）；不监控 Task 时长分布（倾斜的前兆 59-08）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.List;

public class PartitionDemo {
    /** 按目标分区大小切分总数据量 */
    static int partitionsBySize(long totalBytes, long targetBytes) {
        return (int) Math.max(1, (totalBytes + targetBytes - 1) / targetBytes);
    }

    /** 评估分区是否合理：单分区大小 + Task 波次 */
    static void assess(int partitions, long totalBytes, int cores) {
        double perPartitionMb = totalBytes / 1024.0 / 1024 / partitions;
        double waves = partitions / (double) Math.max(1, cores);
        System.out.printf("  分区=%d 单分区≈%.0fMB 波次≈%.1f%n", partitions, perPartitionMb, waves);
        if (perPartitionMb > 1024) {
            System.out.println("    问题：单分区过大 -> 内存压力/GC 长尾");
        }
        if (perPartitionMb < 100) {
            System.out.println("    问题：分区过碎 -> 调度开销超过计算");
        }
        if (waves < 1) {
            System.out.println("    问题：分区数少于核数 -> 资源闲置");
        }
        if (waves > 10) {
            System.out.println("    提示：波次偏多，长尾 Task 会拖慢整体");
        }
    }

    /** coalesce 与 repartition 的语义差异（用分布模拟） */
    static void compareRebalance() {
        List<Integer> before = new ArrayList<>(List.of(100, 100, 0, 0, 0, 0));
        System.out.println("  原始分区数据量：" + before);
        List<Integer> coalesced = List.of(before.get(0) + before.get(1), 0, 0, 0);   // 只合并，不搬移
        System.out.println("  coalesce(4)：    " + coalesced + "（不均，但零 Shuffle）");
        List<Integer> repartitioned = List.of(50, 50, 50, 50);                       // 全量重分布
        System.out.println("  repartition(4)： " + repartitioned + "（均匀，但全量 Shuffle）");
    }

    public static void main(String[] args) {
        long totalBytes = 20L * 1024 * 1024 * 1024;      // 20GB
        int cores = 32;
        System.out.println("【分区数评估：20GB 数据 / 32 核】");
        for (long target : new long[]{512L * 1024 * 1024, 256L * 1024 * 1024}) {
            int p = partitionsBySize(totalBytes, target);
            System.out.printf("目标分区 %dMB -> %d 个分区%n", target / 1024 / 1024, p);
            assess(p, totalBytes, cores);
        }
        System.out.println();
        compareRebalance();
        System.out.println("  口诀：单分区 128MB~1GB，波次 2~3，减分区用 coalesce");
    }
}
```

**课后练习**
1. 为 50GB 数据、64 核集群计算推荐分区数（按 128MB/512MB 两档），并评估波次与单分区大小。
2. 用模拟数据对比 `coalesce` 与 `repartition` 后的分区均匀度与"搬移数据量"，说明各自的适用场景。
3. 思考题：为什么"分区数 = 核数"不是最优？从长尾 Task（最后一个 Task 决定总时长）与波次调度分析。

---

### 59-05 Shuffle 原理与代价

**知识点**
- **Shuffle 是"跨分区的数据重分布"**：`groupByKey`/`join`/`distinct`/`repartition` 都需要把相同 key 的数据聚到一起——**必须写本地临时文件、再经网络拉取**（Spark 的 Shuffle 是"Map 端写文件 + Reduce 端拉取"模型）。
- **Shuffle 的三项成本**：① **磁盘 IO**（Map 端写、Reduce 端读）；② **网络传输**（跨节点拉取）；③ **内存**（拉取缓冲与聚合状态）。**一次 Shuffle 的成本常是整个作业的主要开销**——所以"减少 Shuffle 次数、缩小 Shuffle 数据量"是第一优化方向。
- **Map 端预聚合（combiner）**：`reduceByKey`/`aggregateByKey` 在 Map 端先本地聚合（同 key 先合并再发），**大幅减少网络数据量**；`groupByKey` 不做预聚合（全量移动）——**能用 reduceByKey 就不用 groupByKey**（这是 Spark 最经典的优化建议）。
- **Shuffle 文件数量**：Map Task 数 × Reduce Task 数 个文件（或合并机制减少），**小分区 + 大分区数 = 海量小文件**（46-02 的问题在 Shuffle 侧再现）——`spark.shuffle.file.buffer` 与合并（consolidation）参数可缓解。
- **Shuffle 的替代方案**：**Broadcast Join**（小表广播到每个 Executor，大表不移动，零 Shuffle——59-07 的典型优化）；**分桶表**（预分区存储，join 时免 Shuffle，46-05 表格式的分桶）；**预聚合表**（DWS 层 46-01）。
- 易错点：`groupByKey` 处理大 key（单 key 全量数据压在一个 Task）；Shuffle 分区数默认 200 跑大作业（单分区过大）；不监控 Shuffle 读写量（Spark UI 的 Shuffle Read/Write 是首要指标）；join 大表时不用 broadcast（小表被 Shuffle 白白移动）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ShuffleCost {
    record Row(String key, long amount) {
    }

    /** 对比 groupByKey 与 reduceByKey 的网络传输量 */
    static void compareAggregation(List<Row> rows) {
        Map<String, List<Long>> grouped = new LinkedHashMap<>();
        for (Row r : rows) {
            grouped.computeIfAbsent(r.key(), k -> new ArrayList<>()).add(r.amount());
        }
        long groupByKeyBytes = grouped.values().stream().mapToLong(List::size).sum() * 8;

        Map<String, Long> reduced = new LinkedHashMap<>();
        for (Row r : rows) {
            reduced.merge(r.key(), r.amount(), Long::sum);
        }
        long reduceByKeyBytes = reduced.size() * 8L;          // 每 key 一个聚合值

        System.out.printf("  记录数=%d 唯一 key=%d%n", rows.size(), grouped.size());
        System.out.printf("  groupByKey 网络量 ≈ %d 字节（全量移动）%n", groupByKeyBytes);
        System.out.printf("  reduceByKey 网络量 ≈ %d 字节（Map 端预聚合）%n", reduceByKeyBytes);
        System.out.printf("  优化收益：%.1f 倍%n", (double) groupByKeyBytes / reduceByKeyBytes);
    }

    /** Broadcast Join：小表不 Shuffle，大表原地映射 */
    static void broadcastJoin(List<Row> bigTable, Map<String, String> smallDim) {
        long shuffledRows = 0;                                 // 大表不移动
        List<String> out = new ArrayList<>();
        for (Row r : bigTable) {
            String dimValue = smallDim.getOrDefault(r.key(), "UNKNOWN");
            out.add(r.key() + "=" + dimValue);
        }
        System.out.printf("  Broadcast Join：大表 %d 行零 Shuffle，小表 %d 行广播%n",
                bigTable.size(), smallDim.size());
        System.out.println("  示例输出：" + out.subList(0, Math.min(3, out.size())));
    }

    public static void main(String[] args) {
        List<Row> rows = new ArrayList<>();
        String[] keys = {"a", "b", "c", "d"};
        for (int i = 0; i < 1000; i++) {
            rows.add(new Row(keys[i % keys.length], 100 + i));
        }
        System.out.println("【聚合算子对比】");
        compareAggregation(rows);
        System.out.println();
        System.out.println("【Broadcast Join】");
        Map<String, String> dim = Map.of("a", "手机", "b", "电脑", "c", "配件");
        broadcastJoin(rows.subList(0, 10), dim);
        System.out.println();
        System.out.println("  优化优先级：消除 Shuffle > 缩小 Shuffle 量 > 调 Shuffle 参数");
    }
}
```

**课后练习**
1. 用模拟数据量化 `groupByKey` 与 `reduceByKey` 的网络传输量差异（不同 key 基数），说明为什么"大 key 基数"时差距缩小。
2. 为"订单表 join 商品维度表（1 万行）"设计 Broadcast Join，估算省下的 Shuffle 数据量。
3. 思考题：为什么"小表广播"能零 Shuffle？从"移动数据 vs 移动代码"的取舍分析（并说明广播表大小的上限考量）。

---

### 59-06 宽窄依赖与容错

**知识点**
- **窄依赖（Narrow Dependency）**：一个父分区只被一个子分区使用（`map`/`filter`/`union`）——**分区失败只需重算对应的父分区**，成本低，且支持 pipeline 执行（59-03）。
- **宽依赖（Wide Dependency）**：一个子分区依赖多个父分区（`groupByKey`/`join`/`repartition`）——**分区失败要重算全部父分区**，成本高，且必然产生 Shuffle 边界。
- **血统（Lineage）容错机制**：RDD 记录"从哪来、怎么算"，**Executor 失败时按血统重算丢失的分区**（而非重跑整个作业）——这是"弹性"的本义。**血统链越长，重算代价越大**。
- **检查点（Checkpoint）截断血统**：迭代算法（机器学习/图计算）血统会拉长到几百步——**定期 checkpoint 落盘，切断血统**（43-05 checkpoint 的离线版）；DataFrame 的 `checkpoint()` 同理。
- **推测执行（Speculative Execution）**：慢 Task（`straggler`）在其他节点**并行跑一份**，谁先完成用谁——**对付长尾**（硬件降级/GC 抖动导致），代价是资源浪费（59-09 细讲）。
- **容错与幂等**：重算/重跑要求**输出幂等**（按分区覆盖写、按主键 upsert）——**否则重跑产生重复数据**（39/43 章幂等原则的离线版）。
- 易错点：血统过长（重算成本爆炸，应 checkpoint）；用 `cache()` 代替 checkpoint（缓存丢了血统还在，仍要重算）；输出非幂等（重跑数据翻倍）；把宽依赖当窄依赖理解（以为是分区级重算）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class LineageDemo {
    record Partition(int id, List<String> data) {
    }

    /** 窄依赖：一对一映射，失败只重算对应分区 */
    static Partition narrow(Partition parent, java.util.function.UnaryOperator<String> fn) {
        return new Partition(parent.id(), parent.data().stream().map(fn).toList());
    }

    /** 宽依赖：需要全部父分区，失败要重算所有 */
    static List<Partition> wide(List<Partition> parents, int outputPartitions) {
        Map<Integer, List<String>> buckets = new LinkedHashMap<>();
        for (int i = 0; i < outputPartitions; i++) {
            buckets.put(i, new ArrayList<>());
        }
        for (Partition p : parents) {
            for (String item : p.data()) {
                int bucket = Math.abs(item.hashCode()) % outputPartitions;
                buckets.get(bucket).add(item);
            }
        }
        List<Partition> out = new ArrayList<>();
        buckets.forEach((id, data) -> out.add(new Partition(id, data)));
        return out;
    }

    static void failureCost() {
        List<Partition> input = List.of(
                new Partition(0, List.of("a1", "a2")),
                new Partition(1, List.of("b1", "b2")),
                new Partition(2, List.of("c1", "c2")));
        System.out.println("【窄依赖失败代价】");
        List<Partition> narrowed = new ArrayList<>();
        for (Partition p : input) {
            narrowed.add(narrow(p, s -> s.toUpperCase()));
        }
        System.out.println("  分区 1 失败 -> 只需重算父分区 1（" + input.get(1).data() + "）");
        System.out.println("  重算成本：1 个分区");

        System.out.println("【宽依赖失败代价】");
        List<Partition> widened = wide(narrowed, 2);
        System.out.println("  输出分区：" + widened);
        System.out.println("  分区 0 失败 -> 需要全部 3 个父分区重算并重新 Shuffle");
        System.out.println("  重算成本：3 个分区 + 全量 Shuffle");
        System.out.println();
    }

    /** 血统链与 checkpoint 截断 */
    static void checkpointTruncate() {
        StringBuilder lineage = new StringBuilder("load");
        for (int i = 1; i <= 100; i++) {
            lineage.append(" -> op").append(i);
        }
        System.out.println("【血统长度】100 步迭代后血统长度 = 100（重算要跑 100 步）");
        System.out.println("  checkpoint 每 20 步一次 -> 失败时最多重算 20 步");
        System.out.println("  迭代算法（ML/图计算）必须 checkpoint，否则单分区失败 = 重跑全部");
    }

    public static void main(String[] args) {
        failureCost();
        checkpointTruncate();
    }
}
```

**课后练习**
1. 实现窄/宽依赖的失败重算模拟：随机让一个分区失败，统计两种依赖下的"重算分区数"与"数据移动量"。
2. 为一段 200 步的迭代算法设计 checkpoint 策略（间隔与存储位置），量化"最坏重算步数"的改善。
3. 思考题：为什么"输出必须幂等"是容错的前提？构造一个重跑后数据翻倍的场景并给出修复（按分区覆盖写）。

---

### 59-07 Spark SQL 与 Catalyst 优化器

**知识点**
- **DataFrame 的主流地位来自 Catalyst 优化器**：SQL/DataFrame 先转成**逻辑计划**（未解析 -> 已解析 -> 优化后），再转**物理计划**（选 join 策略、决定分区、插入 Shuffle），最后生成 RDD 执行——**同一段 SQL 的好坏取决于优化器给了什么计划**。
- **四个关键优化规则**：① **谓词下推（Predicate Pushdown）**——filter 尽量推到数据源（Parquet 的 Row Group 裁剪 46-02 由它触发）；② **列裁剪（Column Pruning）**——只读用到的列；③ **常量折叠/表达式简化**；④ **Join 重排序与策略选择**（Broadcast Hash Join / Sort Merge Join / Shuffle Hash Join）。
- **Join 策略的选择依据**：**小表（<10MB 默认阈值）自动 Broadcast**；大表 join 大表用 **Sort Merge Join**（两侧先按 key 排序再归并，Shuffle 后有序）；**Shuffle Hash Join** 适用于一侧远小于另一侧且分区内能放下哈希表。
- **AQE（Adaptive Query Execution，Spark 3.x 默认开启）**：**运行时**根据 Shuffle 统计信息调整：合并小分区（coalesce partitions）、**动态切换 Broadcast Join**（Shuffle 后才发现某侧很小）、**动态处理倾斜**（59-08）——**这让很多手工调参变得不必要**。
- **`explain` 是唯一可信的证据**：`df.explain(true)` 看四段计划；Spark UI 的 SQL 页看每个算子的实际行数与耗时——**"优化"必须基于计划与实际统计，而不是猜**。
- 易错点：用 UDF 包住列（谓词下推失效，36/39 章"函数包列索引失效"的 Spark 版）；不看 explain 就调参数；小表 join 不广播（默认阈值被改小或表超阈值）；以为 DataFrame 一定比 RDD 快（自定义逻辑复杂时 RDD 反而清晰）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.List;

public class CatalystDemo {
    record PlanStep(String stage, String plan, String note) {
    }

    static void planEvolution() {
        System.out.println("【SQL 到执行的计划演进】");
        List<PlanStep> steps = List.of(
                new PlanStep("未解析逻辑计划", "Project(name) <- Filter(amount>10000) <- Relation(orders)",
                        "表/列未绑定"),
                new PlanStep("已解析逻辑计划", "绑定到 catalog 的 orders 表与列类型", "schema 校验"),
                new PlanStep("优化后逻辑计划", "Filter 下推到 Relation（谓词下推）；只保留 name 列（列裁剪）",
                        "Catalyst 规则应用"),
                new PlanStep("物理计划", "Scan(parquet, rowGroup min/max 裁剪) -> Filter -> BroadcastHashJoin(dim)",
                        "选择 join 策略 + 分区"),
                new PlanStep("执行 RDD", "生成 Task 在 Executor 执行", "真正跑起来"));
        for (PlanStep s : steps) {
            System.out.printf("  %-14s %s%n       %s%n", s.stage(), s.plan(), s.note());
        }
        System.out.println();
    }

    /** Join 策略决策：表大小 + 阈值 */
    static String chooseJoinStrategy(long leftBytes, long rightBytes, long broadcastThreshold) {
        if (Math.min(leftBytes, rightBytes) <= broadcastThreshold) {
            return "BroadcastHashJoin（小表广播，零 Shuffle）";
        }
        return "SortMergeJoin（两侧 Shuffle + 排序 + 归并）";
    }

    /** AQE 的三个运行时优化 */
    static void aqeBenefits() {
        System.out.println("【AQE 运行时优化】");
        String[][] rows = {
                {"合并小分区", "Shuffle 后 200 个 5MB 分区 -> 合并成 10 个 100MB", "减少 Task 调度开销"},
                {"动态 Broadcast", "预估 500MB 的表 Shuffle 后实际仅 8MB -> 改广播", "消除一次大 Shuffle"},
                {"动态倾斜处理", "发现某分区数据量是均值 5 倍 -> 自动拆分", "缓解长尾（59-08）"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-14s %-46s %s%n", r[0], r[1], r[2]);
        }
        System.out.println();
    }

    public static void main(String[] args) {
        planEvolution();
        long threshold = 10L * 1024 * 1024;
        System.out.println("【Join 策略选择】");
        System.out.println("  订单 20GB join 商品维度 8MB  -> "
                + chooseJoinStrategy(20L << 30, 8L << 20, threshold));
        System.out.println("  订单 20GB join 用户表 2GB    -> "
                + chooseJoinStrategy(20L << 30, 2L << 30, threshold));
        System.out.println();
        aqeBenefits();
        System.out.println("  UDF 反模式：filter(udf(amount)) -> 谓词下推失效，全表扫描后过滤");
    }
}
```

**课后练习**
1. 写两条等价 SQL（一条用 UDF 包列、一条用原生表达式），用 `explain` 对比计划差异，说明下推失效的代价。
2. 实现 Join 策略选择器（输入两侧大小、阈值、是否已排序），输出推荐策略并说明理由。
3. 思考题：为什么 AQE 不能完全替代人工调优？从"统计信息不全""倾斜的根因在业务"两个角度分析。

---

### 59-08 数据倾斜与作业调优

**知识点**
- **数据倾斜是 Spark 性能问题的第一大杀手**：某个 key（爆款商品/大卖家/默认值 null）占数据量的 30% 以上——**Shuffle 后某个 Task 处理量是其他 Task 的几十倍**，整个 Stage 等这一个 Task（长尾）。
- **识别方法**：Spark UI 看 Task 的 Shuffle Read Size 分布（最大/中位数比值 > 3 即倾斜）；`groupBy(key).count().orderBy(desc)` 看 top key 占比；**AQE 的动态倾斜处理会自动发现**（59-07）。
- **五种解法**（按成本排序）：① **过滤/预处理**（null 或异常 key 提前剔除，最常见）；② **提高并行度**（对轻度倾斜有效，治标）；③ **两阶段聚合（加盐打散）**（39-06/43-08 同款：key 加随机后缀局部聚合再全局聚合）；④ **大 key 单独处理**（拆成独立作业/广播小表思路）；⑤ **Broadcast Join**（如果倾斜发生在 join 且一侧可广播）。
- **倾斜的根因常在业务**：默认值（`user_id = null` 或 `-1` 的兜底数据全挤一个 key）、热点商品、测试数据混入——**治理数据比调参更治本**。
- **调优的优先级**：消除倾斜 > 减少 Shuffle > 调整并行度 > 调内存参数——**先看 Spark UI 的 Task 分布，再决定改什么**（不看数据就调参是赌博）。
- 易错点：只加并行度（倾斜 Task 还是那个 key，更碎但更慢）；加盐后忘记二次聚合（结果错）；不处理 null key（全挤到 default 分区）；调大 executor 内存解决倾斜（只是让单 Task 晚点 OOM）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class SkewDemo {
    record Row(String key, long amount) {
    }

    /** 评估倾斜：输出各分区数据量、最大/中位比 */
    static void diagnose(List<Row> rows, int partitions) {
        Map<Integer, Integer> perPartition = new LinkedHashMap<>();
        for (Row r : rows) {
            int p = Math.abs(r.key().hashCode()) % partitions;
            perPartition.merge(p, 1, Integer::sum);
        }
        List<Integer> sizes = new ArrayList<>(perPartition.values());
        sizes.sort(Integer::compare);
        int max = sizes.get(sizes.size() - 1);
        int median = sizes.get(sizes.size() / 2);
        System.out.printf("  分区数=%d 各分区行数=%s%n", partitions, perPartition);
        System.out.printf("  最大/中位 = %d/%d = %.1f 倍 -> %s%n", max, median,
                (double) max / median, (double) max / median > 3 ? "倾斜！" : "正常");
    }

    /** 两阶段聚合：加盐 -> 局部聚合 -> 去盐 -> 全局聚合 */
    static Map<String, Long> saltedAggregate(List<Row> rows, int saltBuckets) {
        Random rnd = new Random(7);
        Map<String, Long> local = new LinkedHashMap<>();
        for (Row row : rows) {
            String saltedKey = row.key() + "#" + rnd.nextInt(saltBuckets);
            local.merge(saltedKey, row.amount(), Long::sum);
        }
        Map<String, Long> global = new LinkedHashMap<>();
        local.forEach((saltedKey, sum) -> {
            String originalKey = saltedKey.substring(0, saltedKey.lastIndexOf('#'));
            global.merge(originalKey, sum, Long::sum);
        });
        return global;
    }

    public static void main(String[] args) {
        List<Row> rows = new ArrayList<>();
        for (int i = 0; i < 10000; i++) {
            rows.add(new Row("hot-sku", 100));            // 80% 集中在一个 key
        }
        for (int i = 0; i < 2500; i++) {
            rows.add(new Row("sku-" + i, 100));
        }
        System.out.println("【倾斜诊断】");
        diagnose(rows, 8);
        System.out.println();
        System.out.println("【两阶段聚合（加盐 16 桶）】");
        Map<String, Long> result = saltedAggregate(rows, 16);
        System.out.println("  hot-sku 聚合结果：" + result.get("hot-sku") + "（正确性保持）");
        System.out.println("  局部聚合桶数：" + 16 + " -> 单桶负载降到 1/16");
        System.out.println();
        System.out.println("  治本：查清 hot-sku 为什么这么大（爆款/测试数据/默认值）");
    }
}
```

**课后练习**
1. 实现倾斜诊断器（分区数据量分布 + 最大/中位比 + top key 占比），用 3 组模拟数据（正常/轻度/严重）验证。
2. 实现完整两阶段聚合，与单阶段结果对拍（正确性），并对比两种方式的"最大分区负载"。
3. 思考题：为什么"只提高并行度"不能解决倾斜？从"同一 key 必然落同一分区"的哈希规则推导。

---

### 59-09 资源模型与推测执行

**知识点**
- **Spark 的资源三件套**：`executor-memory`（堆内存，含计算与缓存）、`executor-cores`（每个 Executor 的并发槽位）、`executor 数量`——**总核数 = executor 数 × cores**，Task 并发 = 总核数。
- **内存分区**：堆内分**执行内存**（Shuffle/Join/排序的缓冲）与**存储内存**（cache/persist）——两者可动态借用（Spark 3.x 统一内存管理），**执行内存不足会溢写磁盘（严重拖慢）**，存储内存被挤占会淘汰缓存。
- **caching 的正确姿势**：**只在多次使用的 DataFrame 上 cache**（一次性的 cache 是浪费）；`persist(MEMORY_AND_DISK)` 防止 OOM；**cache 用完 `unpersist`**（否则占内存到作业结束）。
- **推测执行（Speculative Execution）**：慢 Task 在其他节点并行启动副本，**先完成者胜出**——对付硬件抖动/GC 长尾；**代价**：资源翻倍（只在稳定环境关闭，异构集群开启）；**输出必须幂等**（重复执行写同一分区）。
- **资源调优的判据**：**Executor 不宜过大**（单 Executor 核数 4~5 为宜，太大 GC 停顿影响所有 Task；太小调度开销高）；**内存按"每核 4~8GB"估算**（缓存放存储内存，Shuffle 缓冲按核数）；YARN/K8s 的动态分配（47-08 弹性的 Spark 版）按需申请。
- 易错点：`cache()` 后不 `unpersist`（内存泄漏到作业结束）；Executor 内存全给计算（cache 无处放、Shuffle 频繁溢写）；推测执行开着但输出非幂等（数据重复）；只看 CPU 不看 GC（30 章 GC 日志的 Spark 版）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.List;

public class ResourceModel {
    record ExecutorConfig(int executors, int coresPerExecutor, int heapMb) {
        int totalCores() {
            return executors * coresPerExecutor;
        }

        /** 统一内存：执行与存储各占一半（0.5 简化模型，真实为 0.6/0.5 可借） */
        long executionMemMb() {
            return (long) (heapMb * 0.5) * executors;
        }

        long storageMemMb() {
            return (long) (heapMb * 0.5) * executors;
        }
    }

    /** 评估配置是否合理 */
    static void assess(ExecutorConfig c, long inputMb, int partitions) {
        System.out.printf("  Executor=%d×%d核，堆=%dMB -> 总核=%d，执行内存=%dMB%n",
                c.executors(), c.coresPerExecutor(), c.heapMb(),
                c.totalCores(), c.executionMemMb());
        if (c.coresPerExecutor() > 5) {
            System.out.println("    提示：单 Executor 核数 >5，GC 停顿会同时卡住多个 Task");
        }
        if (c.coresPerExecutor() < 2 && c.executors() > 50) {
            System.out.println("    提示：Executor 过碎，调度与心跳开销升高");
        }
        if (partitions > c.totalCores() * 10) {
            System.out.println("    提示：分区/核数 >10，波次过多，长尾放大（59-04）");
        }
        long perPartitionMb = inputMb / Math.max(1, partitions);
        if (perPartitionMb > c.executionMemMb() / Math.max(1, c.totalCores())) {
            System.out.println("    风险：单分区数据 > 每 Tasks 可用执行内存 -> 溢写磁盘");
        }
    }

    /** 推测执行的取舍 */
    static void speculation() {
        System.out.println("【推测执行：长尾任务的对冲】");
        record Task(String id, long ms, String note) {
        }
        List<Task> tasks = List.of(
                new Task("t-1", 500, "正常"),
                new Task("t-2", 800, "正常"),
                new Task("t-3", 5000, "慢节点（磁盘/GC 抖动）"));
        long sequential = tasks.stream().mapToLong(Task::ms).sum();
        long withSpeculation = Math.max(800, Math.max(500, 500)) + 500;   // 副本 500ms 后启动
        System.out.printf("  无推测执行：Stage 时长 = 最慢 Task = %dms%n",
                tasks.stream().mapToLong(Task::ms).max().orElse(0));
        System.out.printf("  有推测执行：慢 Task 被副本超越 -> 约 %dms（副本启动延迟 500ms）%n",
                withSpeculation);
        System.out.println("  代价：资源浪费；前提：输出幂等（重算不产生重复数据）");
    }

    public static void main(String[] args) {
        System.out.println("【资源配置评估】");
        assess(new ExecutorConfig(10, 4, 8192), 50_000, 200);
        System.out.println();
        assess(new ExecutorConfig(8, 8, 4096), 50_000, 200);
        System.out.println();
        speculation();
    }
}
```

**课后练习**
1. 为 64 核、512GB 内存的集群设计三套 Executor 配置（大/中/小），按"每核内存""Executor 数"评估并说明推荐方案。
2. 实现"每 Task 可用执行内存"计算器，输入分区数据量判断溢写风险，给出调整建议。
3. 思考题：为什么推测执行要求输出幂等？构造一个"两份副本都完成"导致数据翻倍的场景并说明防护。

---

### 59-10 Spark 与湖仓集成

**知识点**
- **Spark 是湖表的"批处理主力写入方与读取方"**：`INSERT INTO`/`MERGE INTO` 写 Iceberg（46-05 快照提交），`INSERT OVERWRITE` 分区重写；**读时走 Iceberg 的元数据裁剪**（分区 + 文件统计 + 46-02 Row Group）。
- **写入模式的正确选择**：**追加**（`INSERT INTO`，日志/事件类）、**分区覆盖**（`INSERT OVERWRITE PARTITION`，T+1 重算某天——幂等重跑的正确姿势 59-06）、**MERGE**（按主键 upsert，维表/CDC 合并——39-11 双写思想的湖端实现）。**避免"全表覆盖"**（重写全部数据，成本爆炸）。
- **小文件治理是 Spark 写湖的必修课**：`coalesce`/`AQE` 控制输出文件数（59-04）；**写后异步 compaction**（46-06）；**目标文件 128MB~1GB**（46-02）。
- **与 46 章的分层衔接**：ODS（CDC 流入）-> DWD（Spark 清洗 + 质量校验 46-08）-> DWS（预聚合）-> ADS（物化视图 46-07）——**Spark 作业是层与层之间的搬运工**。
- **Spark + Iceberg 的优化点**：分区裁剪与列裁剪自动生效；**`MERGE INTO` 的写放大**（需读匹配文件 + 重写）；**分桶表**（bucketed）+ `bucket join` 可免 Shuffle（59-05）。
- 易错点：全表覆盖写（每次重写 TB 级）；MERGE 无主键（笛卡尔匹配）；小文件不治理（读时分区数爆炸 59-04）；写湖不做幂等（重跑数据翻倍）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SparkLakeDemo {
    record Row(long orderId, String dt, long amount, String status) {
    }

    /** 三种写入模式的语义对比 */
    static Map<String, List<Row>> applyWrite(String mode, List<Row> existing, List<Row> incoming) {
        Map<String, List<Row>> result = new LinkedHashMap<>();
        switch (mode) {
            case "append" -> {
                List<Row> all = new ArrayList<>(existing);
                all.addAll(incoming);
                result.put("append", all);
            }
            case "overwrite-partition" -> {
                String targetDt = incoming.get(0).dt();
                List<Row> kept = existing.stream().filter(r -> !r.dt().equals(targetDt)).toList();
                List<Row> merged = new ArrayList<>(kept);
                merged.addAll(incoming);
                result.put("overwrite-partition", merged);
            }
            default -> {                       // merge by primary key
                Map<Long, Row> map = new LinkedHashMap<>();
                for (Row r : existing) {
                    map.put(r.orderId(), r);
                }
                for (Row r : incoming) {
                    map.put(r.orderId(), r);
                }
                result.put("merge", new ArrayList<>(map.values()));
            }
        }
        return result;
    }

    /** 输出文件数评估：目标文件大小 */
    static int outputFiles(long outputBytes, long targetBytes) {
        return (int) Math.max(1, (outputBytes + targetBytes - 1) / targetBytes);
    }

    public static void main(String[] args) {
        List<Row> existing = new ArrayList<>(List.of(
                new Row(1, "2026-11-17", 6900, "PAID"),
                new Row(2, "2026-11-17", 3900, "PAID"),
                new Row(3, "2026-11-18", 12900, "PAID")));
        List<Row> incoming = List.of(
                new Row(1, "2026-11-17", 6900, "REFUNDED"),
                new Row(4, "2026-11-18", 500, "PAID"));

        System.out.println("【append：重复行风险】" + applyWrite("append", existing, incoming).get("append"));
        System.out.println("【merge：按主键覆盖】" + applyWrite("merge", existing, incoming).get("merge"));
        System.out.println();

        long outputBytes = 200L * 1024 * 1024 * 1024;    // 200GB
        System.out.println("【输出文件数】200GB / 目标 512MB -> "
                + outputFiles(outputBytes, 512L << 20) + " 个文件");
        System.out.println("  小于 128MB 的文件视为碎文件 -> 触发 compaction（46-06）");
        System.out.println();
        System.out.println("  幂等重跑：分区覆盖或 MERGE，绝不用全表覆盖");
    }
}
```

**课后练习**
1. 实现三种写入模式的模拟（append/分区覆盖/MERGE），构造"重跑两次"验证哪种模式幂等。
2. 为 500GB 日增数据设计 Spark 写湖方案（分区策略、目标文件大小、compaction 频率）。
3. 思考题：为什么"分区覆盖"是 T+1 重算的最佳模式？从"只影响目标分区"与"其他分区数据不重写"两个角度分析。

---

### 59-11 综合案例：订单数据离线加工链

**知识点**
- **把本章收口成一条完整的离线加工链**：ODS（raw 订单事件）-> Spark 清洗（窄依赖 map/filter，59-02）-> 按城市/品类聚合（宽依赖 groupBy + Shuffle，59-05）-> 倾斜处理（加盐两阶段，59-08）-> 写 DWS 分区（分区覆盖幂等，59-10）-> 与维表 join（Broadcast，59-07）-> 输出报表。
- **性能预算与调优点**：读 20GB -> 过滤后 8GB（59-03 先过滤）-> 聚合 Shuffle 2GB（combiner 预聚合）-> 输出 40 个文件（128MB 目标，59-04）；**每一步都有可量化的开销**，优化要对准最大项。
- **正确性验收**：与串行实现结果对拍（33 章思想）、重跑幂等（两次结果一致）、质量校验（行数/金额合计 46-08）——**Spark 作业的验收不是"跑通了"，是"跑对了且可复现"**。
- **运维要点**：作业名与标签（可追溯）、失败重试与告警（42 章）、Shuffle 临时目录磁盘水位、输出小文件监控。
- 易错点：先聚合后过滤（Shuffle 量爆炸）；输出用 append 导致重跑翻倍；倾斜不处理（Stage 卡在 1 个 Task）；无质量校验（数据错了当成功）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class OfflinePipelineCase {
    record Order(long id, String city, String category, long amountCents, long ts) {
    }

    record Stat(String city, String category, long orders, long gmvCents) {
    }

    /** 完整加工链：过滤 -> 局部聚合（模拟 combiner） -> 全局聚合 -> 输出 */
    static List<Stat> pipeline(List<Order> raw, long minAmountCents) {
        // 1) 窄依赖：过滤（下推到读取阶段）
        List<Order> filtered = raw.stream()
                .filter(o -> o.amountCents() >= minAmountCents)
                .toList();
        System.out.printf("  1) 过滤：%d -> %d 条（窄依赖，无 Shuffle）%n", raw.size(), filtered.size());

        // 2) Map 端预聚合（combiner 思想，减少 Shuffle 量）
        Map<String, long[]> local = new LinkedHashMap<>();
        for (Order o : filtered) {
            String key = o.city() + "|" + o.category();
            long[] agg = local.computeIfAbsent(key, k -> new long[2]);
            agg[0] += 1;
            agg[1] += o.amountCents();
        }
        System.out.printf("  2) Map 端预聚合：%d 条 -> %d 个局部聚合结果%n",
                filtered.size(), local.size());

        // 3) 全局聚合（宽依赖，Shuffle）
        Map<String, long[]> global = new LinkedHashMap<>();
        local.forEach((key, agg) -> {
            long[] g = global.computeIfAbsent(key, k -> new long[2]);
            g[0] += agg[0];
            g[1] += agg[1];
        });
        System.out.printf("  3) 全局聚合：%d 个结果（Shuffle 移动量已由预聚合缩小）%n", global.size());

        // 4) 输出
        List<Stat> out = new ArrayList<>();
        global.forEach((key, agg) -> {
            String[] parts = key.split("\\|");
            out.add(new Stat(parts[0], parts[1], agg[0], agg[1]));
        });
        return out;
    }

    public static void main(String[] args) {
        List<Order> raw = new ArrayList<>();
        String[] cities = {"杭州", "上海", "北京"};
        String[] categories = {"phone", "laptop", "stand"};
        for (int i = 0; i < 5000; i++) {
            raw.add(new Order(i, cities[i % 3], categories[i % 3],
                    (i % 50) * 1000L + 500, i));
        }
        System.out.println("【订单离线加工链（5000 条样本）】");
        List<Stat> stats = pipeline(raw, 10_000);

        System.out.println();
        System.out.println("【DWS 输出样本（前 4 行）】");
        stats.stream().limit(4).forEach(s -> System.out.printf(
                "  %-4s %-7s 订单%5d GMV=%d 分%n", s.city(), s.category(), s.orders(), s.gmvCents()));

        // 正确性对拍：串行直接聚合
        Map<String, long[]> naive = new LinkedHashMap<>();
        for (Order o : raw) {
            if (o.amountCents() < 10_000) {
                continue;
            }
            long[] agg = naive.computeIfAbsent(o.city() + "|" + o.category(), k -> new long[2]);
            agg[0]++;
            agg[1] += o.amountCents();
        }
        boolean equal = stats.size() == naive.size() && stats.stream().allMatch(s ->
                naive.containsKey(s.city() + "|" + s.category())
                        && naive.get(s.city() + "|" + s.category())[0] == s.orders());
        System.out.println();
        System.out.println("【对拍结果】管道结果 == 串行结果：" + equal);
        System.out.println("【重跑幂等】分区覆盖写入，二次执行结果一致（59-10）");
    }
}
```

**课后练习**
1. 扩展加工链：加入"按日分区"与"倾斜 key（爆款）"的处理，输出各阶段数据量与耗时估算。
2. 为这条链设计验收清单（对拍/幂等/质量校验/文件数/耗时基线），并逐项给出验证方法。
3. 思考题：为什么"对拍"是离线作业上线前的必做项？从"分布式实现易错点（聚合/去重/类型）"与"数据错误的下游成本"分析。
