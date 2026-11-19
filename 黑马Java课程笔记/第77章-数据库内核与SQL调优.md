# 第77章 数据库内核与SQL调优

SQL 写起来像声明式，跑起来却是"解析、优化、执行、存储"四段工程。不懂内核，调优只能靠猜：为什么索引没走、为什么深分页越翻越慢、为什么加了索引反而更慢。本章按「查询旅程 → B+树与页分裂 → 缓冲池与LRU → MVCC → 锁与死锁 → redo与崩溃恢复 → 执行计划 → 慢SQL改写 → 索引设计 → 分片后的SQL约束 → 慢查询治理平台」的顺序讲。所有示例仍是纯 JDK：页访问与逻辑/物理读记账、B+树叶分裂与树高计算、LRU命中率与缓存污染、RC/RR可见性对比、等待图死锁检测、WAL重放恢复、成本模型与join顺序枚举、六类索引失效改写、最左前缀与覆盖索引、雪花ID与跨分片归并、慢日志归一化聚合全部真实运行。

### 77-01 为什么 Java 工程师要懂数据库内核

**知识点**
- **一次查询的完整旅程**：**解析（SQL -> AST）-> 优化（选索引/定 join 顺序）-> 执行（迭代器火山模型）-> 存储引擎（读页/写页）**——**Java 层看到的 `jdbcTemplate.query` 只是这条链的入口**。
- **懂内核才能解释"为什么慢"**：**逻辑读 vs 物理读**（缓冲池命中率）、**扫描行数 vs 返回行数**（索引选择度）、**锁等待 vs 执行耗时**（并发瓶颈）——**没有这些指标，调优只能靠猜**。
- **数据库的"三级存储"**：**缓冲池（内存）-> 磁盘页（16KB）-> 行**——**所有 IO 以页为单位**，**读一行也会加载整页**，这是"索引覆盖"能省 IO 的根本原因（77-02/77-09）。
- **Java 工程师最常见的三个盲区**：**以为索引一定走**（函数/隐式转换会让索引失效 77-08）、**以为事务隔离靠应用层**（MVCC 在数据库内 77-04）、**以为分库分表后 SQL 还能随便写**（跨分片查询要归并 77-10）。
- 与第 20/54 章的关系：20 章讲"怎么用 MySQL/JDBC"，本章讲"MySQL 内部怎么执行你的 SQL"——**从会用到底层原理**。
- 易错点：只看接口耗时（不看 DB 内部指标）；用 `SELECT *`（破坏覆盖索引）；把"加索引"当万能药（写放大/优化器不选）；不区分"慢在 DB"还是"慢在应用"。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DbKernelOverview {
    record Stage(String name, long logicalReads, long physicalReads, double costMs) {
    }

    /** 模拟缓冲池：pageId -> 是否已缓存 */
    static final Set<Long> bufferPool = new LinkedHashSet<>();
    static long logical;
    static long physical;

    static void readPage(long pageId) {
        logical++;
        if (bufferPool.contains(pageId)) {
            return;
        }
        physical++;
        bufferPool.add(pageId);
    }

    /** 模拟一次带索引的查询：3 个索引页 + 5 个回表页 */
    static Stage executeIndexQuery() {
        long beforeLogical = logical;
        long beforePhysical = physical;
        for (long p = 100; p < 103; p++) {
            readPage(p);
        }
        for (long p = 500; p < 505; p++) {
            readPage(p);
        }
        long l = logical - beforeLogical;
        long ph = physical - beforePhysical;
        return new Stage("走二级索引（含回表）", l, ph, ph * 1.0 + l * 0.02);
    }

    /** 模拟全表扫描：20 个数据页 */
    static Stage executeFullScan() {
        long beforeLogical = logical;
        long beforePhysical = physical;
        for (long p = 500; p < 520; p++) {
            readPage(p);
        }
        long l = logical - beforeLogical;
        long ph = physical - beforePhysical;
        return new Stage("全表扫描", l, ph, ph * 1.0 + l * 0.02);
    }

    static void journey() {
        System.out.println("【一次查询的旅程】");
        String[][] steps = {
                {"1. 连接/协议", "JDBC 发 SQL，服务端解析", "词法/语法分析 -> AST"},
                {"2. 优化器", "选访问路径与 join 顺序", "基于成本（77-07）"},
                {"3. 执行器", "按计划调用存储引擎", "火山模型：next() 逐行"},
                {"4. 存储引擎", "读缓冲池/磁盘页", "16KB 页，LRU 淘汰（77-03）"},
                {"5. 返回结果", "结果集编码回传", "网络 + 反序列化"},
        };
        for (String[] s : steps) {
            System.out.printf("  %-12s %-22s %s%n", s[0], s[1], s[2]);
        }
        System.out.println();
    }

    public static void main(String[] args) {
        journey();

        System.out.println("【第一次执行：缓冲池为空】");
        Stage first = executeIndexQuery();
        System.out.printf("  %s：逻辑读=%d 物理读=%d 预估耗时=%.2fms%n",
                first.name(), first.logicalReads(), first.physicalReads(), first.costMs());

        System.out.println();
        System.out.println("【第二次执行：页已在缓冲池】");
        Stage second = executeIndexQuery();
        System.out.printf("  %s：逻辑读=%d 物理读=%d 预估耗时=%.2fms%n",
                second.name(), second.logicalReads(), second.physicalReads(), second.costMs());

        System.out.println();
        System.out.println("【对比全表扫描（20 页）】");
        Stage scan = executeFullScan();
        System.out.printf("  %s：逻辑读=%d 物理读=%d 预估耗时=%.2fms%n",
                scan.name(), scan.logicalReads(), scan.physicalReads(), scan.costMs());

        System.out.println();
        System.out.println("【结论】");
        System.out.println("  1) 物理读才是真 IO，逻辑读是内存访问（快 50 倍）");
        System.out.println("  2) 命中率 = (逻辑读-物理读)/逻辑读；冷启动后才会稳定");
        System.out.println("  3) 扫描行数远大于返回行数 -> 索引选择度差（77-09）");
        System.out.println("  4) 调优先看指标：逻辑读/物理读/扫描行数/锁等待");
    }
}
```

**课后练习**
1. 给一次真实查询统计"逻辑读/物理读/扫描行数/返回行数"，判断瓶颈在 IO 还是 CPU。
2. 对比"走索引 + 回表"与"全表扫描"的页访问次数，说明何时全表扫描反而更快。
3. 思考题：为什么"读一行也会加载整页"？从 16KB 页与局部性原理分析，并联系覆盖索引。

---

### 77-02 B+ 树索引与页分裂

**知识点**
- **B+ 树是"为磁盘页设计的平衡树"**：**内部节点只存键与指针**（扇出大）、**叶子节点存键与数据/主键**、**叶子间用链表相连**（范围扫描友好）——**3 层即可支撑千万级行**（扇出 1000 时：1000^3 = 10 亿）。
- **聚簇索引 vs 二级索引**：**InnoDB 主键即聚簇索引**（叶子存整行）；**二级索引叶子存主键值**，查非索引列要**回表**（再走一次聚簇索引）——**回表是二级索引最大的成本**。
- **页分裂**：**叶子页写满后再插入**，会把页**一分为二**（通常各半），**分裂导致空间碎片与写放大**——**顺序主键（自增）只在最右页分裂，随机主键（UUID）会到处分裂**。
- **树高与 IO 次数**：**一次点查 = 树高次页访问**（根常在缓冲池，实际 1~2 次物理读）；**范围查 = 定位 + 叶子链表扫描**——**所以范围查询要控制返回量**。
- 与 54 章的关系：54 章讲"推荐系统"，本章的 B+ 树是 34 章"数据结构"在磁盘上的工程版——**同样的平衡思想，不同的存储介质**。
- 易错点：用 UUID 做主键（随机写、页分裂、索引膨胀）；`SELECT *` 导致回表；范围查询不带边界（扫全表）；以为"索引越多越快"（写放大 77-09）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.List;

public class BPlusTreeIndex {
    static final int LEAF_CAP = 8;

    static class LeafPage {
        final List<Integer> keys = new ArrayList<>();

        int maxKey() {
            return keys.isEmpty() ? Integer.MIN_VALUE : keys.get(keys.size() - 1);
        }
    }

    static class BPlusTree {
        final List<LeafPage> leaves = new ArrayList<>();
        int splits;
        long inserted;

        BPlusTree() {
            leaves.add(new LeafPage());
        }

        /** 按"页的最大键"定位叶子（真实实现走内部节点，这里用目录线性查找） */
        int locate(int key) {
            for (int i = 0; i < leaves.size(); i++) {
                if (key <= leaves.get(i).maxKey()) {
                    return i;
                }
            }
            return leaves.size() - 1;
        }

        void insert(int key) {
            int idx = locate(key);
            LeafPage page = leaves.get(idx);
            int pos = 0;
            while (pos < page.keys.size() && page.keys.get(pos) < key) {
                pos++;
            }
            if (pos < page.keys.size() && page.keys.get(pos) == key) {
                return;
            }
            page.keys.add(pos, key);
            inserted++;
            if (page.keys.size() > LEAF_CAP) {
                split(idx);
            }
        }

        /** 页分裂：中间一分为二，右页插入目录 */
        void split(int idx) {
            LeafPage page = leaves.get(idx);
            int mid = page.keys.size() / 2;
            LeafPage right = new LeafPage();
            right.keys.addAll(page.keys.subList(mid, page.keys.size()));
            page.keys.subList(mid, page.keys.size()).clear();
            leaves.add(idx + 1, right);
            splits++;
        }

        /** 树高：叶子数按内部扇出逐层收敛 */
        int height() {
            int pages = leaves.size();
            int h = 1;
            int fanout = LEAF_CAP / 2 + 1;
            while (pages > 1) {
                pages = (pages + fanout - 1) / fanout;
                h++;
            }
            return h;
        }

        boolean search(int key) {
            LeafPage page = leaves.get(locate(key));
            int lo = 0;
            int hi = page.keys.size() - 1;
            while (lo <= hi) {
                int mid = (lo + hi) >>> 1;
                int v = page.keys.get(mid);
                if (v == key) {
                    return true;
                }
                if (v < key) {
                    lo = mid + 1;
                } else {
                    hi = mid - 1;
                }
            }
            return false;
        }

        double avgFill() {
            int total = 0;
            for (LeafPage p : leaves) {
                total += p.keys.size();
            }
            return (double) total / leaves.size() / LEAF_CAP;
        }
    }

    public static void main(String[] args) {
        BPlusTree tree = new BPlusTree();
        for (int i = 1; i <= 2000; i++) {
            tree.insert(i);
        }

        System.out.println("【顺序插入 1..2000（页容量 8）】");
        System.out.printf("  叶子页数=%d，分裂次数=%d，树高=%d，平均填充率=%.0f%%%n",
                tree.leaves.size(), tree.splits, tree.height(), tree.avgFill() * 100);

        System.out.println();
        System.out.println("【点查】");
        System.out.println("  search(777) = " + tree.search(777));
        System.out.println("  search(3000) = " + tree.search(3000));
        System.out.println("  一次点查的页访问次数 = 树高 = " + tree.height());

        System.out.println();
        System.out.println("【二级索引回表成本】");
        long rows = 2000;
        long tablePages = (rows + 99) / 100;              // 每页 100 行
        System.out.println("  二级索引定位到主键：" + tree.height() + " 次页访问");
        System.out.println("  回表走聚簇索引：" + tree.height() + " 次页访问");
        System.out.println("  返回 10 行 -> 约 " + (tree.height() * 2 * 10) + " 次页访问（未命中缓存时）");
        System.out.println("  表数据页共 " + tablePages + " 页");

        System.out.println();
        System.out.println("【页分裂对比】");
        System.out.println("  自增主键：只在最右页分裂，碎片少、写放大低");
        System.out.println("  UUID 主键：随机插入，页分裂分散，索引膨胀 1.5~2 倍");
        System.out.println("  分裂后平均填充率约 50%~70%，这是 B+ 树的空间代价");
    }
}
```

**课后练习**
1. 用"页容量 4/8/16"分别插入 1 万条，统计分裂次数、树高与平均填充率，说明页大小的影响。
2. 实现"随机顺序插入"对比自增插入，统计分裂次数差异并解释 UUID 主键的代价。
3. 思考题：为什么二级索引查询非索引列要"回表"？从叶子节点存主键而非整行分析，并给出避免回表的方法。

---

### 77-03 缓冲池与 LRU

**知识点**
- **缓冲池是数据库性能的第一道防线**：**磁盘页读入内存后按页缓存**，**命中则零磁盘 IO**——**OLTP 场景命中率通常 > 99%**，**命中率下降往往意味着缓存被大查询污染**。
- **淘汰算法**：**朴素 LRU**（最近最少使用）简单但**全表扫描会把热数据挤出去**（缓存污染）；**InnoDB 用改进 LRU**（young/old 分区 + 中点插入），**大扫描只污染 old 区**——**这是"全表扫描拖慢整个库"的机制**。
- **预读（read-ahead）**：**顺序访问时一次多读几页**（线性预读/随机预读），**提升范围扫描吞吐**，但**预读过多会浪费缓冲池**。
- **脏页与刷盘**：**修改先改缓冲池页并写 redo**（77-06），**脏页由后台线程择机刷盘**——**刷盘抖动会造成延迟毛刺**（checkpoint 压力）。
- 与 30 章的关系：30 章讲 JVM 内存与 GC，**缓冲池是"数据库的堆"**——**同样有容量、淘汰、命中率、抖动问题**。
- 易错点：认为"加内存一定快"（命中率已 99.9% 时收益极小）；大报表直连主库（污染缓存）；不监控命中率；把"逻辑读高"误判为慢（逻辑读是内存操作）。

**示例代码**
```java
import java.util.LinkedHashMap;
import java.util.Map;

public class BufferPoolLru {
    static class LruPool extends LinkedHashMap<Long, String> {
        private static final long serialVersionUID = 1L;
        final int capacity;
        long hits;
        long misses;
        long evictions;

        LruPool(int capacity) {
            super(16, 0.75f, true);                 // accessOrder = true
            this.capacity = capacity;
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, String> eldest) {
            boolean evict = size() > capacity;
            if (evict) {
                evictions++;
            }
            return evict;
        }

        String access(long pageId) {
            String v = get(pageId);
            if (v == null) {
                misses++;
                v = "page-" + pageId;
                put(pageId, v);
            } else {
                hits++;
            }
            return v;
        }

        double hitRate() {
            long total = hits + misses;
            return total == 0 ? 0 : (double) hits / total;
        }
    }

    /** 80/20 倾斜访问：80% 请求落在 20% 的热页 */
    static void workload(LruPool pool, int requests, boolean skewed) {
        long seed = 42;
        for (int i = 0; i < requests; i++) {
            seed = (seed * 1103515245 + 12345) & 0x7fffffffL;
            long pageId;
            if (skewed) {
                if (seed % 100 < 80) {
                    pageId = seed % 200;            // 热区：0..199
                } else {
                    pageId = 200 + seed % 800;      // 冷区：200..999
                }
            } else {
                pageId = seed % 1000;               // 均匀
            }
            pool.access(pageId);
        }
    }

    static void report(String label, LruPool pool, int requests) {
        System.out.printf("  %-18s 容量=%-4d 命中率=%5.1f%% 淘汰=%-6d 物理读=%d/%d%n",
                label, pool.capacity, pool.hitRate() * 100, pool.evictions,
                pool.misses, requests);
    }

    public static void main(String[] args) {
        int requests = 100_000;

        System.out.println("【均匀访问（无热点）】");
        for (int cap : new int[]{50, 100, 200, 500}) {
            LruPool pool = new LruPool(cap);
            workload(pool, requests, false);
            report("uniform", pool, requests);
        }

        System.out.println();
        System.out.println("【80/20 倾斜访问（有热点）】");
        for (int cap : new int[]{50, 100, 200, 500}) {
            LruPool pool = new LruPool(cap);
            workload(pool, requests, true);
            report("skewed", pool, requests);
        }

        System.out.println();
        System.out.println("【缓存污染：一次全表扫描】");
        LruPool pool = new LruPool(100);
        workload(pool, 10_000, true);
        double before = pool.hitRate();
        for (long p = 1000; p < 2000; p++) {
            pool.access(p);                          // 扫描 1000 个冷页
        }
        workload(pool, 10_000, true);
        System.out.printf("  扫描前命中率=%.1f%%，扫描后=%.1f%%（热页被挤出）%n",
                before * 100, pool.hitRate() * 100);
        System.out.println("  InnoDB 的 young/old 分区就是为缓解这个问题");

        System.out.println();
        System.out.println("【结论】热点越集中，命中率对容量越不敏感；全表扫描会污染缓存");
    }
}
```

**课后练习**
1. 用 80/20 与均匀两种负载，测不同容量下的命中率，画出"容量-命中率"曲线并找拐点。
2. 模拟"全表扫描前后"的命中率变化，说明缓存污染，并设计"大查询走从库"的隔离方案。
3. 思考题：为什么 InnoDB 的 LRU 要把新页插到"中点"而不是头部？从"防止一次性扫描挤掉热数据"分析。

---

### 77-04 事务与 MVCC

**知识点**
- **MVCC 的核心：每行保留多个版本，读不加锁**——**写操作生成新版本（带事务 ID）**，**读操作按"可见性规则"挑一个版本**——**读写互不阻塞**，这是 InnoDB 高并发的关键。
- **两个隐藏列**：**`trx_id`（最后修改该版本的事务）** 与 **`roll_ptr`（指向 undo log 中的旧版本）**——**版本链就是 undo 链**，回滚与一致性读都靠它。
- **Read View 的可见性规则**：**版本 trx_id < 最小活跃事务 -> 可见**；**在活跃集合中 -> 不可见**；**>= 最大事务号 -> 不可见**；**其余可见**——**RC 每条语句新建 Read View，RR 整个事务复用第一个**。
- **RC vs RR 的差别就来自 Read View 的创建时机**：**RC 能读到别人已提交的新数据（不可重复读）**；**RR 始终读事务开始时的快照（可重复读）**——**不是"锁"带来的，是"快照"带来的**。
- 与 32 章的关系：32 章讲 Java 并发（锁/CAS），**MVCC 是"数据库版的无锁读"**——**同样的思想：用版本代替互斥**。
- 易错点：以为 RR 完全无幻读（当前读仍可能幻读，需间隙锁 77-05）；长事务不提交（undo 链膨胀、快照过旧）；用 RC 跑对账（同一事务内数据会变）；把"快照读"与"当前读"混用。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class MvccReadView {
    record Version(int trxId, String value) {
    }

    static class Row {
        final List<Version> versions = new ArrayList<>();

        void write(int trxId, String value) {
            versions.add(new Version(trxId, value));
        }

        /** 找最新可见版本 */
        String read(ReadView view) {
            for (int i = versions.size() - 1; i >= 0; i--) {
                Version v = versions.get(i);
                if (view.visible(v.trxId())) {
                    return v.value();
                }
            }
            return null;
        }
    }

    static class ReadView {
        final int upLimit;                       // 创建时下一个事务号
        final Set<Integer> active;               // 创建时活跃事务
        final int minActive;

        ReadView(int upLimit, Set<Integer> active) {
            this.upLimit = upLimit;
            this.active = new LinkedHashSet<>(active);
            int min = upLimit;
            for (int id : active) {
                min = Math.min(min, id);
            }
            this.minActive = min;
        }

        boolean visible(int trxId) {
            if (trxId >= upLimit) {
                return false;                    // 视图创建后才开始的事务
            }
            if (active.contains(trxId)) {
                return false;                    // 创建时还未提交
            }
            return true;
        }
    }

    /** 模拟事务管理器：分配递增事务号 */
    static class TrxManager {
        int next = 1;
        final Set<Integer> active = new LinkedHashSet<>();

        int begin() {
            int id = next++;
            active.add(id);
            return id;
        }

        void commit(int id) {
            active.remove(id);
        }

        ReadView snapshot() {
            return new ReadView(next, active);
        }
    }

    public static void main(String[] args) {
        Row row = new Row();
        TrxManager tm = new TrxManager();

        int t0 = tm.begin();
        row.write(t0, "原始值");
        tm.commit(t0);

        int t1 = tm.begin();                     // T1 开始，做快照（RR）
        ReadView rrView = tm.snapshot();

        int t2 = tm.begin();                     // T2 开始
        row.write(t2, "T2 修改值");
        tm.commit(t2);

        System.out.println("【版本链】");
        for (Version v : row.versions) {
            System.out.printf("  trx=%d value=%s%n", v.trxId(), v.value());
        }

        System.out.println();
        System.out.println("【T1 在 RC 下（每条语句新快照）】");
        ReadView rcView = tm.snapshot();
        System.out.println("  读到：" + row.read(rcView) + "（能看到 T2 已提交）");

        System.out.println();
        System.out.println("【T1 在 RR 下（复用事务开始时的快照）】");
        System.out.println("  读到：" + row.read(rrView) + "（看不到 T2 的提交）");

        System.out.println();
        System.out.println("【可见性判断（RR 视图）】");
        System.out.println("  视图 upLimit=" + rrView.upLimit
                + " active=" + rrView.active + " minActive=" + rrView.minActive);
        for (Version v : row.versions) {
            System.out.printf("  trx=%d -> %s%n", v.trxId(),
                    rrView.visible(v.trxId()) ? "可见" : "不可见");
        }

        System.out.println();
        System.out.println("【结论】RR 的可重复读来自快照，不来自锁；长事务会让 undo 链越来越长");
    }
}
```

**课后练习**
1. 构造"两个事务交替写"的版本链，分别用 RC/RR 快照读取，验证结果差异。
2. 实现"当前读"（读最新已提交版本），对比快照读，说明 `SELECT ... FOR UPDATE` 的语义。
3. 思考题：为什么长事务会拖垮数据库？从"undo 链膨胀 + 快照过旧 + purge 受阻"三方面分析。

---

### 77-05 锁与死锁

**知识点**
- **InnoDB 的锁粒度**：**行锁**（Record Lock，锁索引记录）、**间隙锁**（Gap Lock，锁索引区间，防幻读）、**临键锁**（Next-Key = 行锁 + 间隙锁）、**意向锁**（表级，表示"我要锁某些行"）——**锁的是索引，不是行本身**（没走索引会锁全表）。
- **共享锁 vs 排他锁**：**S 锁可共存、X 锁互斥**；**`SELECT ... FOR UPDATE` 加 X 锁**、**`LOCK IN SHARE MODE` 加 S 锁**——**普通 SELECT 走 MVCC 不加锁**（77-04）。
- **死锁的四个必要条件**：**互斥、占有且等待、不可抢占、循环等待**——**数据库的死锁检测就是"等待图找环"**，**发现环后回滚代价小的事务（通常 undo 量小）**。
- **避免死锁的工程手段**：**固定加锁顺序**（按主键排序更新）、**缩短事务**（锁持有时间）、**降低隔离级别**（RC 减少间隙锁）、**唯一索引让冲突变等待**——**没有银弹，只能减少概率**。
- 与 32 章的关系：32 章讲 Java 的 `synchronized`/死锁检测，**数据库锁是"跨进程、跨机器"的同一问题**——**等待图、超时、回滚**是通用解法。
- 易错点：不按顺序更新多行（死锁高发）；长事务持有行锁（阻塞全表更新）；`SELECT ... FOR UPDATE` 不带索引（锁全表）；把死锁当"偶发故障"不治理。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class LockDeadlock {
    /** 简化锁管理器：资源 -> 持有事务；等待图：事务 -> 它等待的事务 */
    static class LockManager {
        final Map<String, String> holder = new LinkedHashMap<>();
        final Map<String, Set<String>> waits = new LinkedHashMap<>();
        final List<String> events = new ArrayList<>();

        boolean tryLock(String trx, String resource) {
            String owner = holder.get(resource);
            if (owner == null || owner.equals(trx)) {
                holder.put(resource, trx);
                waits.computeIfAbsent(trx, k -> new LinkedHashSet<>());
                events.add(trx + " 获得 " + resource);
                return true;
            }
            waits.computeIfAbsent(trx, k -> new LinkedHashSet<>()).add(owner);
            events.add(trx + " 等待 " + resource + "（持有者 " + owner + "）");
            return false;
        }

        void releaseAll(String trx) {
            holder.entrySet().removeIf(e -> e.getValue().equals(trx));
            waits.remove(trx);
            for (Set<String> set : waits.values()) {
                set.remove(trx);
            }
            events.add(trx + " 释放全部锁");
        }

        /** 等待图找环：DFS 三色标记 */
        String findCycle() {
            Map<String, Integer> color = new LinkedHashMap<>();
            for (String t : waits.keySet()) {
                color.put(t, 0);
            }
            for (String t : waits.keySet()) {
                String cycle = dfs(t, color, new ArrayList<>());
                if (cycle != null) {
                    return cycle;
                }
            }
            return null;
        }

        String dfs(String node, Map<String, Integer> color, List<String> path) {
            Integer c = color.get(node);
            if (c != null && c == 1) {
                return String.join(" -> ", path) + " -> " + node;
            }
            if (c != null && c == 2) {
                return null;
            }
            color.put(node, 1);
            path.add(node);
            for (String next : waits.getOrDefault(node, Set.of())) {
                String found = dfs(next, color, path);
                if (found != null) {
                    return found;
                }
            }
            path.remove(path.size() - 1);
            color.put(node, 2);
            return null;
        }
    }

    public static void main(String[] args) {
        LockManager lm = new LockManager();

        System.out.println("【场景：T1 锁 A 再等 B，T2 锁 B 再等 A】");
        lm.tryLock("T1", "row-A");
        lm.tryLock("T2", "row-B");
        lm.tryLock("T1", "row-B");
        lm.tryLock("T2", "row-A");

        lm.events.forEach(e -> System.out.println("  " + e));

        System.out.println();
        String cycle = lm.findCycle();
        System.out.println("  死锁检测：" + (cycle == null ? "无环" : "发现环 " + cycle));

        System.out.println();
        System.out.println("【选 victim 回滚（这里回滚事务号较大的 T2）】");
        lm.releaseAll("T2");
        boolean ok = lm.tryLock("T1", "row-B");
        System.out.println("  T2 回滚后，T1 重新申请 row-B：" + (ok ? "成功" : "仍失败"));
        System.out.println("  当前持有：" + lm.holder);

        System.out.println();
        System.out.println("【避免死锁的四条工程规则】");
        String[][] rules = {
                {"固定顺序", "多行更新按主键升序", "消除循环等待"},
                {"短事务", "锁内不做 RPC/大计算", "减少持有时间"},
                {"走索引", "FOR UPDATE 必须命中索引", "避免锁全表"},
                {"降隔离", "RC 减少间隙锁", "降低冲突面"},
        };
        for (String[] r : rules) {
            System.out.printf("  %-8s %-26s %s%n", r[0], r[1], r[2]);
        }
    }
}
```

**课后练习**
1. 构造三个事务的等待环，用等待图检测并输出环路径，验证 victim 回滚后恢复。
2. 模拟"不按顺序更新"与"按主键排序更新"两种写法，对比死锁概率。
3. 思考题：为什么"没走索引的 FOR UPDATE 会锁全表"？从"行锁锁的是索引记录"分析。

---

### 77-06 redo log 与崩溃恢复

**知识点**
- **WAL（Write-Ahead Logging）的核心规则**：**先写日志，再写数据页**——**日志顺序追加（快）**，**数据页随机写（慢）**；**崩溃后用日志重放未刷盘的修改**。
- **redo log 的两阶段**：**事务提交时只需保证 redo 落盘（fsync）**，**数据页可以稍后刷**——**这就是"提交快"的原因**；**checkpoint 表示"此前的日志对应的页都已刷盘"**，恢复只需从 checkpoint 之后重放。
- **undo log 与 redo log 分工**：**redo 保证"已提交的不丢"（ durability）**，**undo 保证"未提交的可回滚"（atomicity）**——**两者配合实现事务的 A/C/D**。
- **两阶段提交（redo + binlog）**：**prepare（redo 落盘）-> 写 binlog -> commit**——**崩溃恢复时比对两者**，**保证主从一致**（这是 MySQL 特有的复杂点）。
- 与 45 章的关系：45 章讲"分布式共识/Raft 日志"，**单机数据库的 WAL 是"日志复制"的起点**——**先有本地日志，才有跨机复制**。
- 易错点：把 redo 当"缓存"（它是持久化日志）；以为"提交了就一定刷了数据页"（只刷了日志）；checkpoint 太频繁（刷盘压力）或太稀疏（恢复慢）；忽略 `innodb_flush_log_at_trx_commit` 的取舍。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class WALRecovery {
    record LogRecord(long lsn, int trxId, long pageId, String after) {
    }

    static final List<LogRecord> wal = new ArrayList<>();
    static final Map<Long, String> diskPages = new LinkedHashMap<>();
    static final Map<Long, String> bufferPages = new LinkedHashMap<>();
    static final Set<Long> dirtyPages = new LinkedHashSet<>();
    static long lsn;
    static long checkpointLsn;

    static void update(int trxId, long pageId, String value) {
        lsn++;
        wal.add(new LogRecord(lsn, trxId, pageId, value));   // 1) 先写日志
        bufferPages.put(pageId, value);                     // 2) 再改缓冲池
        dirtyPages.add(pageId);
        System.out.printf("  trx%d 更新 page%d -> %s（lsn=%d，日志已落盘，页仍脏）%n",
                trxId, pageId, value, lsn);
    }

    static void checkpoint() {
        for (Long p : dirtyPages) {
            diskPages.put(p, bufferPages.get(p));
        }
        dirtyPages.clear();
        checkpointLsn = lsn;
        System.out.printf("  checkpoint：脏页刷盘完成，checkpointLsn=%d%n", checkpointLsn);
    }

    static void crashAndRecover() {
        System.out.println("  *** 崩溃：缓冲池丢失，磁盘保留已刷页 ***");
        bufferPages.clear();
        dirtyPages.clear();
        System.out.println("  崩溃后磁盘：" + diskPages);
        int replayed = 0;
        for (LogRecord r : wal) {
            if (r.lsn() > checkpointLsn) {
                diskPages.put(r.pageId(), r.after());
                replayed++;
            }
        }
        System.out.printf("  从 checkpointLsn=%d 之后重放 %d 条日志%n", checkpointLsn, replayed);
        System.out.println("  恢复后磁盘：" + diskPages);
    }

    public static void main(String[] args) {
        diskPages.put(1L, "A0");
        diskPages.put(2L, "B0");

        System.out.println("【初始磁盘】" + diskPages);
        System.out.println();
        System.out.println("【事务 100：更新 A、B，然后 checkpoint】");
        update(100, 1, "A1");
        update(100, 2, "B1");
        checkpoint();

        System.out.println();
        System.out.println("【事务 101：更新 A 后提交，但未 checkpoint 就崩溃】");
        update(101, 1, "A2");
        crashAndRecover();

        System.out.println();
        System.out.println("【结论】");
        System.out.println("  1) 提交只需 redo 落盘，数据页可延后刷（提交快）");
        System.out.println("  2) 崩溃恢复从 checkpoint 之后重放，已提交的不丢");
        System.out.println("  3) redo 与 binlog 的两阶段提交保证主从一致");
        System.out.println("  4) checkpoint 越频繁恢复越快，但平时刷盘压力越大");

        System.out.println();
        System.out.println("【redo vs undo】");
        String[][] rows = {
                {"redo", "记录修改后的值", "崩溃恢复，保证已提交不丢"},
                {"undo", "记录修改前的值", "事务回滚 + MVCC 旧版本"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-6s %-16s %s%n", r[0], r[1], r[2]);
        }
    }
}
```

**课后练习**
1. 模拟"更新两页后崩溃"与"checkpoint 后崩溃"，验证恢复结果都等于已提交状态。
2. 加入"未提交事务"的日志，恢复时用 undo 回滚，验证原子性。
3. 思考题：为什么"提交快"和"恢复快"是一对矛盾？从 checkpoint 频率与刷盘压力分析。

---

### 77-07 执行计划与优化器

**知识点**
- **优化器是基于成本的（CBO）**：**枚举访问路径**（全表扫描/主键/二级索引/覆盖索引）与 **join 顺序**，**用统计信息估算行数**，**选总成本最低的计划**——**统计信息过期会导致选错计划**（这是"昨天还快今天慢"的常见原因）。
- **访问路径的成本模型**：**全表扫描 = 页数（顺序 IO）**；**索引点查 = 树高 + 回表（随机 IO，权重更高）**；**覆盖索引 = 树高（不回表）**——**随机 IO 通常按 4 倍顺序 IO 计**，所以"小表全扫"可能比"索引回表"更便宜。
- **join 的三种算法**：**Nested Loop**（小表驱动，走索引）、**Hash Join**（大表等值，内存哈希）、**Sort-Merge Join**（有序输入）——**MySQL 8 之前主要 NL，8.0.20+ 有 Hash Join**（本章按通用模型讲）。
- **join 顺序的搜索空间**：**N 张表有 N! 种顺序**，优化器用**贪心/动态规划**剪枝——**小表驱动、结果集小的先 join** 是经验法则，但**最终看成本**。
- 与 54 章的关系：54 章推荐系统的"召回排序"也是"多路候选 + 成本/收益打分"，**优化器就是 SQL 的"打分器"**。
- 易错点：只看 `EXPLAIN` 的 type 不看 rows（估算行数才是关键）；统计信息不更新（`ANALYZE TABLE`）；join 字段无索引（笛卡尔积）；`SELECT *` 让覆盖索引失效。

**示例代码**
```java
import java.util.ArrayList;
import java.util.List;

public class QueryOptimizer {
    record Table(String name, long rows, long pages, double filterSelectivity) {
        long resultRows() {
            return Math.max(1, (long) (rows * filterSelectivity));
        }
    }

    record Plan(String path, double cost, long estRows) {
    }

    /** 全表扫描：顺序 IO，成本 = 页数 */
    static Plan fullScan(Table t) {
        return new Plan(t.name() + " 全表扫描", t.pages(), t.resultRows());
    }

    /** 二级索引：树高 + 回表随机 IO（按 4 倍权重） */
    static Plan indexScan(Table t) {
        double cost = 3 + t.resultRows() * 4.0 / 100;     // 每页 100 行，随机 IO 权重 4
        return new Plan(t.name() + " 二级索引 + 回表", cost, t.resultRows());
    }

    /** 覆盖索引：树高 + 叶子扫描，不回表 */
    static Plan coveringIndex(Table t) {
        double cost = 3 + t.resultRows() * 1.0 / 100;
        return new Plan(t.name() + " 覆盖索引", cost, t.resultRows());
    }

    static Plan choose(Table t) {
        List<Plan> plans = List.of(fullScan(t), indexScan(t), coveringIndex(t));
        Plan best = plans.get(0);
        for (Plan p : plans) {
            if (p.cost() < best.cost()) {
                best = p;
            }
        }
        System.out.printf("  %-8s rows=%-8d pages=%-5d -> 选择 %-20s cost=%.1f%n",
                t.name(), t.rows(), t.pages(), best.path(), best.cost());
        return best;
    }

    /** join 顺序：枚举排列，成本 = 中间结果行数之和 */
    static void joinOrder(List<Table> tables) {
        int[] idx = new int[tables.size()];
        for (int i = 0; i < idx.length; i++) {
            idx[i] = i;
        }
        List<int[]> perms = new ArrayList<>();
        permute(idx, 0, perms);

        double bestCost = Double.MAX_VALUE;
        String bestOrder = "";
        for (int[] p : perms) {
            double cost = 0;
            long rows = 1;
            StringBuilder order = new StringBuilder();
            for (int i : p) {
                Table t = tables.get(i);
                rows = Math.max(rows, t.resultRows());
                cost += rows;
                order.append(t.name()).append(' ');
            }
            if (cost < bestCost) {
                bestCost = cost;
                bestOrder = order.toString().trim();
            }
        }
        System.out.printf("  枚举 %d 种 join 顺序 -> 最优：%s（成本 %.0f）%n",
                perms.size(), bestOrder, bestCost);
    }

    static void permute(int[] a, int k, List<int[]> out) {
        if (k == a.length) {
            out.add(a.clone());
            return;
        }
        for (int i = k; i < a.length; i++) {
            int tmp = a[k];
            a[k] = a[i];
            a[i] = tmp;
            permute(a, k + 1, out);
            tmp = a[k];
            a[k] = a[i];
            a[i] = tmp;
        }
    }

    public static void main(String[] args) {
        System.out.println("【单表访问路径选择】");
        List<Table> tables = List.of(
                new Table("orders", 1_000_000, 20_000, 0.001),
                new Table("users", 100_000, 2_000, 0.05),
                new Table("dict", 100, 1, 1.0));
        tables.forEach(QueryOptimizer::choose);

        System.out.println();
        System.out.println("【三表 join 顺序】");
        joinOrder(tables);

        System.out.println();
        System.out.println("【成本模型说明】");
        System.out.println("  全表扫描 = 页数（顺序 IO）");
        System.out.println("  索引回表 = 3 + 行数/100 * 4（随机 IO 权重 4）");
        System.out.println("  覆盖索引 = 3 + 行数/100（不回表）");
        System.out.println("  小表全扫可能优于大表索引回表：dict 只有 1 页");

        System.out.println();
        System.out.println("【统计信息过期的影响】");
        System.out.println("  rows 估算偏小 -> 优化器误选索引回表 -> 实际扫大量行 -> 慢查询");
        System.out.println("  对策：ANALYZE TABLE、监控执行计划变化、慢 SQL 回归");
    }
}
```

**课后练习**
1. 调整三张表的行数与选择度，观察"全表扫描/索引/覆盖索引"的选择如何变化。
2. 给 join 成本加入"驱动表结果集"权重，验证"小表驱动"是否总是最优。
3. 思考题：为什么"统计信息过期"会导致计划突变？从 CBO 依赖 rows 估算分析，并给出监控方案。

---

### 77-08 慢 SQL 诊断与改写

**知识点**
- **索引失效的六大场景**：**列上做函数/运算**（`DATE(col)=...`）、**前导模糊**（`LIKE '%x'`）、**隐式类型转换**（字符串列传数字）、**OR 连接不同列**、**NOT IN / !=**（低选择度）、**联合索引不满足最左前缀**——**每一条都能用"改写"救回来**。
- **深分页的两种解法**：**游标分页**（`WHERE id > lastId LIMIT n`）与**延迟关联**（先用覆盖索引取主键，再回表）——**`LIMIT 1000000, 20` 的本质是"扫描 100 万行再丢弃"**。
- **诊断四步**：**看慢日志（耗时/扫描行数）-> EXPLAIN（type/key/rows/Extra）-> 判断瓶颈（IO/CPU/锁）-> 改写并回归**——**没有 EXPLAIN 的改写都是猜测**。
- **改写的边界**：**能改 SQL 就不加索引**（索引有写放大）；**能加索引就不拆表**；**必须拆表时先保 SQL 兼容**（77-10）。
- 与 64 章的关系：开放平台的"慢接口治理"与"慢 SQL 治理"同构——**先度量、再定位、后优化、最后设门禁**。
- 易错点：给列加函数还怪索引失效；`LIKE '%x%'` 硬扛；深分页不改；`SELECT *`；用 `OR` 又不想改 `UNION`。

**示例代码**
```java
import java.util.ArrayList;
import java.util.List;

public class SlowSqlRewrite {
    record Case(String name, String badSql, String problem, long tableRows,
                long scannedRows, String rewrite, long rewrittenScan) {
        double speedup() {
            return (double) scannedRows / Math.max(1, rewrittenScan);
        }
    }

    public static void main(String[] args) {
        List<Case> cases = new ArrayList<>();
        cases.add(new Case(
                "函数包裹列",
                "SELECT * FROM orders WHERE DATE(created_at) = '2026-11-18'",
                "列上做函数，索引失效，全表扫描",
                10_000_000, 10_000_000,
                "WHERE created_at >= '2026-11-18 00:00:00' AND created_at < '2026-11-19 00:00:00'",
                8_000));
        cases.add(new Case(
                "前导模糊",
                "SELECT * FROM users WHERE name LIKE '%张%'",
                "前导 % 无法走 B+ 树，全表扫描",
                2_000_000, 2_000_000,
                "改用全文索引/搜索引擎；或 WHERE name LIKE '张%'（前缀匹配）",
                4_000));
        cases.add(new Case(
                "隐式转换",
                "SELECT * FROM orders WHERE order_no = 20261118001",
                "order_no 是 varchar，数字比较触发隐式转换，索引失效",
                10_000_000, 10_000_000,
                "WHERE order_no = '20261118001'（保持类型一致）",
                1));
        cases.add(new Case(
                "OR 跨列",
                "SELECT * FROM orders WHERE user_id = 1 OR status = 2",
                "OR 两侧列不同，优化器可能放弃索引",
                10_000_000, 10_000_000,
                "UNION ALL 两条分别走索引：user_id=1 / status=2",
                60_000));
        cases.add(new Case(
                "深分页",
                "SELECT * FROM orders ORDER BY id LIMIT 1000000, 20",
                "扫描 100 万行再丢弃，越翻越慢",
                10_000_000, 1_000_020,
                "WHERE id > 1000000 ORDER BY id LIMIT 20（游标分页）",
                20));
        cases.add(new Case(
                "回表过多",
                "SELECT * FROM orders WHERE user_id = 1 ORDER BY created_at LIMIT 20",
                "二级索引后回表 20 次可接受；若 SELECT * 且无覆盖则放大",
                10_000_000, 20,
                "联合索引 (user_id, created_at) + 只取需要列（覆盖索引）",
                3));

        System.out.println("【慢 SQL 案例库】");
        for (Case c : cases) {
            System.out.printf("%n  [%s] %s%n", c.name(), c.badSql());
            System.out.println("    问题：" + c.problem());
            System.out.printf("    扫描行数：%,d -> 改写后 %,d（提升 %.0f 倍）%n",
                    c.scannedRows(), c.rewrittenScan(), c.speedup());
            System.out.println("    改写：" + c.rewrite());
        }

        System.out.println();
        System.out.println("【诊断四步】");
        String[][] steps = {
                {"1. 慢日志", "耗时、扫描行数、返回行数", "先找最贵的 SQL"},
                {"2. EXPLAIN", "type/key/rows/Extra", "确认是否走索引、估算行数"},
                {"3. 定位", "IO/CPU/锁等待", "区分慢在扫描还是等待"},
                {"4. 改写+回归", "改写后对比扫描行数", "防止改坏，纳入门禁"},
        };
        for (String[] s : steps) {
            System.out.printf("  %-10s %-30s %s%n", s[0], s[1], s[2]);
        }

        System.out.println();
        System.out.println("【改写优先级】改 SQL > 加索引 > 拆表；每次只改一处并回归");
    }
}
```

**课后练习**
1. 为六类失效场景各构造一条 SQL，写出改写前后扫描行数估算与 EXPLAIN 关注点。
2. 实现"延迟关联"改写：先用覆盖索引取主键再回表，比较与 `SELECT *` 的 IO 差异。
3. 思考题：为什么"隐式类型转换"会让索引失效？从 B+ 树按类型比较与函数转换分析。

---

### 77-09 索引设计方法论

**知识点**
- **索引设计三问**：**哪些列出现在 WHERE/JOIN/ORDER BY**、**选择度多高**（`distinct/rows`）、**是否需要覆盖查询列**——**选择度低的列（性别/状态）单独建索引几乎无用**。
- **联合索引的最左前缀**：**`(a,b,c)` 能支持 `a`、`a,b`、`a,b,c`**，**不能支持 `b` 或 `b,c`**——**等值列在前、范围列在后**（范围列后面的列无法用于定位）。
- **三星索引**：**一星：WHERE 列在索引中**；**二星：ORDER BY 列顺序一致**；**三星：SELECT 列被索引覆盖**——**三星索引是"理想态"，实际按查询频率取舍**。
- **索引的代价**：**写放大**（每次 INSERT/UPDATE 维护所有索引）、**空间**（二级索引存主键）、**优化器选择成本**（索引太多反而选错）——**索引不是越多越好**。
- 与 77-02 的关系：**B+ 树的页分裂与回表成本决定了索引设计**——**设计的是"树"，不是"字段清单"**。
- 易错点：给每个 WHERE 列单独建索引（应建联合索引）；联合索引顺序随意（等值在前）；冗余索引（`(a)` 与 `(a,b)` 前者冗余）；不删无用索引。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class IndexDesign {
    record Column(String name, long distinct, long rows) {
        double selectivity() {
            return (double) distinct / rows;
        }
    }

    static boolean leftmostPrefix(List<String> index, List<String> where) {
        if (where.size() > index.size()) {
            return false;
        }
        for (int i = 0; i < where.size(); i++) {
            if (!index.get(i).equals(where.get(i))) {
                return false;
            }
        }
        return true;
    }

    static boolean covering(List<String> index, List<String> select) {
        return index.containsAll(select);
    }

    static boolean redundant(List<String> a, List<String> b) {
        // a 是 b 的前缀 -> a 冗余
        if (a.size() >= b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (!a.get(i).equals(b.get(i))) {
                return false;
            }
        }
        return true;
    }

    public static void main(String[] args) {
        long rows = 10_000_000;
        List<Column> columns = List.of(
                new Column("user_id", 2_000_000, rows),
                new Column("status", 5, rows),
                new Column("created_at", 3_000_000, rows),
                new Column("amount", 1_000_000, rows));
        System.out.println("【列选择度】");
        columns.forEach(c -> System.out.printf("  %-12s distinct=%,d 选择度=%.3f%n",
                c.name(), c.distinct(), c.selectivity()));

        System.out.println();
        System.out.println("【候选联合索引】");
        List<List<String>> candidates = List.of(
                List.of("user_id", "created_at"),
                List.of("status", "created_at"),
                List.of("user_id", "status", "created_at"));
        List<String> where = List.of("user_id");
        List<String> select = List.of("created_at", "amount");
        for (List<String> idx : candidates) {
            boolean prefix = leftmostPrefix(idx, where);
            boolean cover = covering(idx, select);
            System.out.printf("  %-34s 最左前缀=%s 覆盖=%s%n",
                    idx, prefix ? "是" : "否", cover ? "是" : "否");
        }

        System.out.println();
        System.out.println("【查询：WHERE user_id=? ORDER BY created_at LIMIT 20】");
        System.out.println("  推荐索引：(user_id, created_at, amount)");
        System.out.println("  理由：user_id 等值在前，created_at 排序紧随其后，amount 覆盖查询");
        System.out.println("  -> 一星（WHERE）+ 二星（ORDER BY）+ 三星（覆盖）");

        System.out.println();
        System.out.println("【冗余索引检测】");
        List<List<String>> existing = new ArrayList<>(List.of(
                List.of("user_id"),
                List.of("user_id", "created_at"),
                List.of("status")));
        Set<Integer> drop = new LinkedHashSet<>();
        for (int i = 0; i < existing.size(); i++) {
            for (int j = 0; j < existing.size(); j++) {
                if (i != j && redundant(existing.get(i), existing.get(j))) {
                    drop.add(i);
                }
            }
        }
        for (int i = 0; i < existing.size(); i++) {
            System.out.printf("  %-30s %s%n", existing.get(i),
                    drop.contains(i) ? "冗余（被更长前缀覆盖）" : "保留");
        }

        System.out.println();
        System.out.println("【设计原则】等值在前、范围在后、排序列跟随、覆盖常用列、及时删冗余");
    }
}
```

**课后练习**
1. 给"订单表按用户查最近 20 条"设计联合索引，写出最左前缀与覆盖判断。
2. 实现"冗余索引检测"：判断 `(a)` 是否被 `(a,b)` 覆盖，并给出删除建议。
3. 思考题：为什么"选择度低的列"不适合单独建索引？从 B+ 树区分度与回表行数分析。

---

### 77-10 分库分表后的 SQL 约束

**知识点**
- **分片键决定路由**：**带分片键的查询 -> 单分片**；**不带分片键 -> 广播到所有分片**（性能杀手）；**跨分片 JOIN/聚合 -> 内存归并或冗余**——**分片后 SQL 不再是"想怎么写就怎么写"**。
- **全局唯一 ID**：**雪花算法**（时间戳 + 机器号 + 序列）、**号段模式**（数据库批量发号）、**UUID**（无序，不推荐做主键）——**分片后自增主键会冲突，必须换 ID 方案**。
- **跨分片查询的四种处理**：**禁止**（产品层面约束）、**广播 + 归并**（小表/低频）、**冗余字段**（把维表字段复制到事实表）、**异构索引表**（按另一维度再分片一份）。
- **分页与排序的坑**：**`ORDER BY time LIMIT 10` 需要每个分片各取 10 条再归并**（归并排序）；**深分页要游标**；**跨分片 COUNT/SUM 要二次聚合**。
- 与 25 章的关系：25 章讲"海量数据与分库分表"的拆分策略，**本章讲拆分后 SQL 与 ID 的工程约束**——**拆分是架构决策，SQL 约束是落地代价**。
- 易错点：用自增 ID 分片（冲突）；不带分片键查询（广播）；跨分片 JOIN（笛卡尔积）；跨分片深分页；不设计异构索引。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ShardedSql {
    static final int SHARDS = 4;

    static int shardOf(long orderId) {
        return (int) (orderId % SHARDS);
    }

    /** 简化雪花：41 位时间 + 10 位机器 + 12 位序列 */
    static class Snowflake {
        final long epoch = 1_700_000_000_000L;
        final long workerId;
        long lastTs = -1;
        long seq;

        Snowflake(long workerId) {
            this.workerId = workerId;
        }

        synchronized long nextId(long nowMs) {
            if (nowMs == lastTs) {
                seq = (seq + 1) & 0xFFF;
                if (seq == 0) {
                    while (nowMs <= lastTs) {
                        nowMs++;
                    }
                }
            } else {
                seq = 0;
                lastTs = nowMs;
            }
            lastTs = Math.max(lastTs, nowMs);
            return ((nowMs - epoch) << 22) | (workerId << 12) | seq;
        }
    }

    static void route(String sql, Long orderId) {
        if (orderId != null) {
            System.out.printf("  %-58s -> 分片 %d（单分片）%n", sql, shardOf(orderId));
        } else {
            System.out.printf("  %-58s -> 广播 %d 个分片（危险）%n", sql, SHARDS);
        }
    }

    static List<Long> mergeSorted(List<List<Long>> shardResults, int limit) {
        List<Long> out = new ArrayList<>();
        int[] pos = new int[shardResults.size()];
        while (out.size() < limit) {
            int best = -1;
            long bestVal = Long.MAX_VALUE;
            for (int i = 0; i < shardResults.size(); i++) {
                if (pos[i] < shardResults.get(i).size()) {
                    long v = shardResults.get(i).get(pos[i]);
                    if (v < bestVal) {
                        bestVal = v;
                        best = i;
                    }
                }
            }
            if (best == -1) {
                break;
            }
            out.add(bestVal);
            pos[best]++;
        }
        return out;
    }

    public static void main(String[] args) {
        System.out.println("【分片路由】");
        route("SELECT * FROM orders WHERE order_id = 10086", 10086L);
        route("SELECT * FROM orders WHERE user_id = 7", null);
        route("SELECT * FROM orders WHERE status = 1 ORDER BY created_at", null);

        System.out.println();
        System.out.println("【雪花 ID】");
        Snowflake sf = new Snowflake(3);
        long base = 1_764_000_000_000L;
        for (int i = 0; i < 3; i++) {
            long id = sf.nextId(base + i);
            System.out.printf("  id=%d -> 分片 %d（时间有序，分片均匀）%n", id, shardOf(id));
        }

        System.out.println();
        System.out.println("【跨分片 ORDER BY 归并】");
        Map<Integer, List<Long>> byShard = new LinkedHashMap<>();
        for (int s = 0; s < SHARDS; s++) {
            byShard.put(s, new ArrayList<>());
        }
        for (long id = 1; id <= 40; id++) {
            byShard.get(shardOf(id)).add(id);
        }
        List<List<Long>> lists = new ArrayList<>(byShard.values());
        System.out.println("  各分片（已排序）：" + byShard);
        System.out.println("  全局 LIMIT 10：" + mergeSorted(lists, 10));

        System.out.println();
        System.out.println("【分片后的 SQL 约束】");
        String[][] rules = {
                {"查询必须带分片键", "否则广播全部 shard", "order_id/user_id 二选一"},
                {"禁止跨分片 JOIN", "改冗余字段或异构索引表", "维表字段复制到事实表"},
                {"跨分片分页", "每片取 N 条再归并", "深分页改游标"},
                {"聚合", "各片 COUNT/SUM 后二次聚合", "注意去重（COUNT DISTINCT）"},
                {"自增主键", "换雪花/号段", "保证全局唯一且有序"},
        };
        for (String[] r : rules) {
            System.out.printf("  %-18s %-26s %s%n", r[0], r[1], r[2]);
        }
    }
}
```

**课后练习**
1. 实现"按 user_id 与 order_id 双维度分片"的路由，说明异构索引表的作用。
2. 模拟 4 个分片的 `ORDER BY created_at LIMIT 20` 归并，验证结果全局有序且不重不漏。
3. 思考题：为什么"分片后自增主键会冲突"？从"每个分片独立自增序列"分析，并给出两种全局 ID 方案。

---

### 77-11 综合案例：慢查询治理平台

**知识点**
- **把本章收口成一个治理闭环**：**采集慢日志**（77-01）-> **归一化 fingerprint**（把常量替换为占位符）-> **聚合排序**（总耗时/次数/最大耗时）-> **EXPLAIN 归因**（77-07/77-08）-> **索引建议**（77-09）-> **治理看门禁**（回归对比）。
- **SQL 归一化的价值**：**同一条 SQL 的不同参数应聚成一组**（`WHERE id=1` 与 `id=2` 是同一 fingerprint），**否则慢日志会被参数淹没**——**归一化要处理数字、字符串、IN 列表**。
- **排序口径**：**总耗时 = 次数 × 平均耗时**（治理收益最大）；**最大耗时**（体验最差）；**扫描行数**（IO 成本）——**三个口径要一起看**。
- **治理的闭环**：**发现 -> 归因 -> 改写/加索引 -> 回归验证 -> 加入门禁**——**没有回归的优化不可信**（80-10 性能门禁）。
- 与 42 章的关系：慢查询治理平台是"可观测性"在数据库侧的落地——**采集、聚合、告警、归因**四步与 APM 同构。
- 易错点：只看单条最慢（忽略高频中慢）；不做归一化（无法聚合）；建议索引不看选择度；优化后不回归。

**示例代码**
```java
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SlowQueryPlatform {
    record SlowLog(String sql, long costMs, long scannedRows, long returnedRows, String user) {
    }

    record Fingerprint(String pattern, long count, long totalMs, long maxMs,
                       long totalScanned, String sample) {
        double avgMs() {
            return (double) totalMs / count;
        }
    }

    /** 归一化：字符串、数字、IN 列表都替换成 ? */
    static String normalize(String sql) {
        String s = sql.replaceAll("'[^']*'", "?");
        s = s.replaceAll("\\b\\d+\\b", "?");
        s = s.replaceAll("IN\\s*\\([^)]*\\)", "IN (?)");
        s = s.replaceAll("\\s+", " ").trim();
        return s;
    }

    /** 从 WHERE 中提取等值列，给出索引建议 */
    static List<String> suggestIndex(String sql) {
        List<String> cols = new ArrayList<>();
        Matcher m = Pattern.compile("(?i)\\bwhere\\b(.*?)(?:\\border\\b|\\blimit\\b|$)")
                .matcher(sql);
        if (m.find()) {
            Matcher eq = Pattern.compile("(\\w+)\\s*=\\s*\\?").matcher(m.group(1));
            while (eq.find()) {
                cols.add(eq.group(1));
            }
        }
        return cols;
    }

    public static void main(String[] args) {
        List<SlowLog> logs = List.of(
                new SlowLog("SELECT * FROM orders WHERE user_id = 1 AND status = 2", 820, 120_000, 3, "app"),
                new SlowLog("SELECT * FROM orders WHERE user_id = 2 AND status = 2", 910, 130_000, 5, "app"),
                new SlowLog("SELECT * FROM orders WHERE user_id = 3 AND status = 2", 760, 110_000, 2, "app"),
                new SlowLog("SELECT * FROM orders WHERE created_at >= '2026-11-01'", 2100, 900_000, 20_000, "report"),
                new SlowLog("SELECT * FROM users WHERE name LIKE '%张%'", 1500, 400_000, 1_000, "crm"),
                new SlowLog("SELECT * FROM orders WHERE order_no = 'A1001'", 12, 1, 1, "app"),
                new SlowLog("SELECT * FROM orders WHERE order_no = 'A1002'", 9, 1, 1, "app"));

        Map<String, List<SlowLog>> grouped = new LinkedHashMap<>();
        for (SlowLog log : logs) {
            grouped.computeIfAbsent(normalize(log.sql()), k -> new ArrayList<>()).add(log);
        }

        List<Fingerprint> fps = new ArrayList<>();
        for (Map.Entry<String, List<SlowLog>> e : grouped.entrySet()) {
            List<SlowLog> list = e.getValue();
            long totalMs = 0;
            long maxMs = 0;
            long scanned = 0;
            for (SlowLog l : list) {
                totalMs += l.costMs();
                maxMs = Math.max(maxMs, l.costMs());
                scanned += l.scannedRows();
            }
            fps.add(new Fingerprint(e.getKey(), list.size(), totalMs, maxMs, scanned,
                    list.get(0).sql()));
        }
        fps.sort(Comparator.comparingLong(Fingerprint::totalMs).reversed());

        System.out.println("【慢查询聚合（按总耗时排序）】");
        int rank = 0;
        for (Fingerprint f : fps) {
            rank++;
            System.out.printf("%n  #%d fingerprint: %s%n", rank, f.pattern());
            System.out.printf("     次数=%d 总耗时=%dms 平均=%.0fms 最大=%dms 扫描行=%,d%n",
                    f.count(), f.totalMs(), f.avgMs(), f.maxMs(), f.totalScanned());
            if (f.avgMs() <= 50) {
                System.out.println("     索引建议：已很快，无需新增索引");
            } else {
                List<String> idx = suggestIndex(f.pattern());
                System.out.println("     索引建议：" + (idx.isEmpty() ? "无等值条件，考虑范围/覆盖"
                        : "联合索引 " + idx + "（等值列在前）"));
            }
        }

        System.out.println();
        System.out.println("【治理优先级】");
        System.out.println("  1) 高频中慢（总耗时最高）：orders 的 user_id+status");
        System.out.println("  2) 低频极慢（体验最差）：报表 created_at 范围 + 覆盖索引");
        System.out.println("  3) 前导模糊：改搜索引擎/前缀匹配");
        System.out.println("  4) 已经很快的（order_no 唯一索引）：不动");

        System.out.println();
        System.out.println("【闭环】采集 -> 归一化 -> 聚合 -> EXPLAIN 归因 -> 改写/索引 -> 回归门禁");
    }
}
```

**课后练习**
1. 实现 SQL 归一化（字符串/数字/IN 列表），把 10 条慢日志聚成 3 个 fingerprint。
2. 为聚合结果加"总扫描行数"排序，对比"按总耗时"排序的治理优先级差异。
3. 思考题：为什么"高频中慢"往往比"低频极慢"治理收益更高？从总耗时与用户体验分布分析。
