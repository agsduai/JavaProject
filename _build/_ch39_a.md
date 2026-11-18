### 39-01 数据量大了会发生什么

**知识点**
- **单表性能拐点是经验值不是铁律**：千万级通常还能撑（看索引与查询模式），五千万到一亿级开始明显劣化。决定拐点的不是行数本身，而是 **B+ 树的深度**：主键索引树高从 3 层变 4 层，每次查找多一次磁盘/页读取，所有查询同时变慢。
- **量变引起质变的四个信号**：① 通过索引查单行仍快，但**无索引条件查询**从毫秒变秒；② 缓冲池（innodb_buffer_pool）命中率下降，数据页换进换出；③ 备份/DDL 时间从分钟变小时（`ALTER TABLE` 锁表风险飙升）；④ 主从延迟开始周期性飙高。
- **先优化索引与查询，再考虑拆**：绝大多数"数据量大了很慢"的真实原因是一个没走索引的 SQL。**分库分表是最后手段**，做之前必须先做：索引优化、SQL 治理、缓存挡读、历史归档。直接上分库分表是把架构复杂度当成特效药。
- **数据量的另一个代价是"写"**：B+ 树页分裂、redo/undo 膨胀、长事务持锁更久。读可以通过缓存与从库扛，**写瓶颈才是分库的真实动因**。
- **容量规划要按"三年后"算**：日增 50 万行，一年 1.8 亿行。规划分片数不是按今天的量，而是按三年后的量 ÷ 单分片合理上限（经验值 1000~2000 万行/表），一次规划到位，避免二次扩容。
- **判断该走哪条路**：读慢 → 缓存 + 读写分离 + 归档；写慢 → 批量化 + 削峰 + 分库；单表大但增长停止 → 归档即可；持续增长 → 分库分表。
- 易错点：用行数判断要不要拆（要看 QPS、索引命中、增长曲线）；把分库分表当成"性能优化第一步"；忽略归档——很多系统 80% 的数据是两年前的冷数据，归档完单表直接回到安全区。

**示例代码**
```java
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class BigTableProblems {
    /** 简化的 B+ 树深度估算：单页存 M 个键，树高 = ceil(logM(行数)) */
    static int btreeHeight(long rows, int keysPerPage) {
        long nodes = 1;
        int height = 1;
        while (nodes * (long) keysPerPage < rows) {
            nodes *= keysPerPage;
            height++;
        }
        return height;
    }

    static void btreeDepth() {
        System.out.println("【B+ 树深度：为什么行数增加让所有查询都变慢】");
        long[][] cases = {{1_000_000L, 3}, {20_000_000L, 3}, {200_000_000L, 3}};
        for (long[] c : cases) {
            long rows = c[0];
            int h = btreeHeight(rows, (int) c[1]);
            System.out.printf("  单表 %2d,000,000 行 -> 主键树高 %d 层（每次定位多一跳）%n",
                    rows / 1_000_000, h);
        }
        System.out.println("  树高 3->4：全表所有点查的 IO 基础成本同步上升 33%");
        System.out.println();
    }

    static void fourSignals() {
        System.out.println("【数据量撑不住的四个信号】");
        String[][] signals = {
                {"无索引查询变慢", "EXISTS 子查询/模糊条件 从 50ms -> 5s"},
                {"缓冲池命中率下降", "buffer pool hit 99% -> 85%，磁盘 IO 翻倍"},
                {"DDL 时间失控", "加一列从 2s -> 2h，期间锁表风险"},
                {"主从延迟周期性飙高", "大查询拖住 SQL 线程，Seconds_Behind_Master 抖动"},
        };
        for (String[] s : signals) {
            System.out.printf("  - %-18s %s%n", s[0], s[1]);
        }
        System.out.println();
    }

    static void decisionTree() {
        System.out.println("【拆不拆：先走便宜的路】");
        List<String> steps = List.of(
                "1) 慢查询治理：EXPLAIN 逐条过，补索引、改写法（通常解决 80% 的慢）",
                "2) 缓存挡读：热点读走 Redis，数据库只承担写与冷读",
                "3) 读写分离：报表与后台查询下放到从库",
                "4) 历史归档：两年前数据进归档库/表，主表回到安全区",
                "5) 以上都做完了写瓶颈仍在 -> 才进入分库分表设计");
        for (String s : steps) {
            System.out.println("  " + s);
        }
        System.out.println();
    }

    static void capacityPlan() {
        System.out.println("【容量规划：按三年后的量算分片数】");
        long dailyGrowth = 500_000;
        long threeYears = dailyGrowth * 365 * 3;
        long perTableLimit = 20_000_000;
        int shards = (int) Math.ceil((double) threeYears / perTableLimit);
        System.out.printf("  日增 %d 行 -> 三年 %d 行%n", dailyGrowth, threeYears);
        System.out.printf("  单表上限 %d 行 -> 至少 %d 个分片，取 2 的幂 -> 8 分片%n",
                perTableLimit, shards);
        System.out.println("  分片数选 2 的幂（8/16/32）：扩容翻倍时路由迁移最规整");
        System.out.println("  反例：按当前量 2000 万就 2 分片，一年后被迫二次扩容");
    }

    public static void main(String[] args) {
        btreeDepth();
        fourSignals();
        decisionTree();
        capacityPlan();
    }
}
```

**课后练习**
1. 用示例的 btreeHeight 公式估算你项目主表三年后的树高，写出"何时必须做归档/分表"的量化判断。
2. 给你的数据库跑一次慢查询审计（slow log 或手动 EXPLAIN 全量接口），列出前 5 条慢 SQL 与修复方案。
3. 思考题：为什么说"写瓶颈才是分库的真实动因，读慢一般不用分库"？读慢的三板斧分别解决哪类问题？

---

### 39-02 索引优化与执行计划

**知识点**
- **EXPLAIN 是唯一可信的证据**：type 从好到坏：`system > const > eq_ref > ref > range > index > ALL`，出现 ALL（全表扫描）基本就是事故；rows 是预估扫描行数；key/Extra 说明用没用索引、有没有回表、有没有 filesort。
- **最左前缀原则**：联合索引 `(a, b, c)` 只能支持 a、a+b、a+b+c 的查询。条件里跳过 a 直接查 b，索引就用不上；a 有条件但 b 范围查询，c 就断了（范围之后失效）。**联合索引的列顺序 = 等值列在前、范围列在后、排序列跟随**。
- **回表与覆盖索引**：二级索引存的是（索引列+主键），SELECT 的列不在索引里就要回主键树再取一次。`SELECT id, status FROM t WHERE status=?` 用联合索引 `(status, id)` 可以**索引覆盖**不回表，Extra 显示 `Using index`。大查询覆盖索引常常是十倍差距。
- **索引选择性**：区分度 = 不重复值数/总行数。`status` 只有 3 个值，单独索引选择性差，但联合索引里作为前缀做等值过滤依然有效；`性别` 这种字段单独建索引是浪费。
- **隐式类型转换是隐形索引杀手**：`WHERE phone = 13800000000`（字符串列配数字）触发转换，索引失效；函数包住列（`DATE(create_time)=...`）同样失效，应改写为范围条件 `create_time >= '...' AND < '...'`。
- **深分页的 index 层优化**：`LIMIT 100000, 10` 要扫过前 10 万行。先用覆盖索引取出目标主键（延迟关联），再用主键回表，扫描成本从"全字段×10万"降到"索引×10万"。
- 易错点：为每个查询条件单独建一堆单列索引（MySQL 只会用一个，联合索引才是正解）；`ORDER BY` 列不在索引里导致 filesort； believing `FORCE INDEX` 能解决设计问题（治标）；线上加索引用 `ALGORITHM=INPLACE, LOCK=NONE` 忘了确认版本支持（8.0 大多支持但要看列类型）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.List;

public class SlowQueryOptimize {
    /** 模拟联合索引前缀消耗：等值列可被优化器重排，范围列之后失效 */
    static int usablePrefix(List<String> eqCols, String rangeCol, String[] indexCols) {
        int used = 0;
        for (String idxCol : indexCols) {
            if (eqCols.contains(idxCol)) {
                used++;
            } else if (idxCol.equals(rangeCol)) {
                used++;               // 范围列本身可用，其后全部失效
                break;
            } else if (used < eqCols.size() && !eqCols.contains(idxCol)) {
                break;                // 前缀列缺席，断链
            }
        }
        return used;
    }

    static void leftmostRule() {
        System.out.println("【最左前缀：联合索引 (user_id, status, create_time)】");
        String[] indexCols = {"user_id", "status", "create_time"};
        String[][] queries = {
                {"user_id=,status=,create_time>", "等值x2+范围", "ref，时间列走范围"},
                {"user_id=,status=", "等值x2", "ref"},
                {"user_id=", "等值x1", "ref"},
                {"status=,user_id=", "等值x2（顺序故意颠倒）", "优化器重排等值列，仍可全用"},
                {"status=,create_time>", "跳过 user_id", "前缀断链，0 列可用"},
        };
        for (String[] q : queries) {
            List<String> eq = new ArrayList<>();
            String range = null;
            for (String part : q[0].split(",")) {
                if (part.endsWith("=")) {
                    eq.add(part.substring(0, part.length() - 1));
                } else {
                    range = part.substring(0, part.length() - 1);
                }
            }
            int used = usablePrefix(eq, range, indexCols);
            System.out.printf("  WHERE %-34s %-16s -> 用上 %d 列（%s）%n", q[0], q[1], used, q[2]);
        }
        System.out.println("  规则：等值列按索引顺序消耗；范围列可用但截断其后；缺前缀列=全废");
        System.out.println();
    }

    static void coveringIndex() {
        System.out.println("【覆盖索引：回表次数决定查询速度】");
        int matchRows = 10_000;
        System.out.printf("  SELECT * WHERE status=PAID 命中 %d 行：%n", matchRows);
        System.out.printf("    索引(status)        ：%d 次索引扫描 + %d 次回表取全字段%n", matchRows, matchRows);
        System.out.printf("    索引(status,id)     ：%d 次索引扫描 + 0 次回表（Using index）%n", matchRows);
        System.out.println("    延迟关联：先取 100 个主键再回表 -> 只回表 100 次");
        System.out.println("  设计口诀：高频查询的 SELECT 列尽量进联合索引，代价是索引变宽");
        System.out.println();
    }

    static void indexKillers() {
        System.out.println("【四个让索引失效的真实写法】");
        String[][] cases = {
                {"WHERE phone = 13800000000", "字符串列配数字 -> 隐式转换全表扫"},
                {"WHERE DATE(create_time)=CURDATE()", "函数包列 -> 改为时间范围条件"},
                {"WHERE name LIKE '%机'", "前置 % -> 只能放弃 B+ 树（或用倒排/前缀索引）"},
                {"WHERE status != 'PAID'", "不等于 -> 通常仍需扫描，靠选择性与数据分布判断"},
        };
        for (String[] c : cases) {
            System.out.printf("  %-38s %s%n", c[0], c[1]);
        }
        System.out.println();
    }

    static void explainChecklist() {
        System.out.println("【EXPLAIN 审核五步】");
        List<String> steps = List.of(
                "type 是不是 ALL/index -> 基本要 ref 以上",
                "rows 与实际命中数差几个数量级 -> 统计信息过期或索引选择错",
                "key 是否是预期索引 -> 不是就查最左前缀/类型转换",
                "Extra 有 Using filesort/Using temporary -> 排序与分组列进索引",
                "Extra 有 Using index -> 覆盖索引生效，记录为优化成果");
        for (String s : steps) {
            System.out.println("  " + s);
        }
    }

    public static void main(String[] args) {
        leftmostRule();
        coveringIndex();
        indexKillers();
        explainChecklist();
    }
}
```

**课后练习**
1. 对你项目里最慢的三条 SQL 各做一次 EXPLAIN，把 type/rows/key/Extra 记录成表格，并给出优化前后对比。
2. 为 `(user_id, status, create_time)` 联合索引发明三个"能用到 3 列"与三个"只能用到 1 列"的查询，说明判断依据。
3. 把一条 `LIKE '%关键词%'` 的需求改造成可行的方案（前缀索引 / 倒排 / 搜索引擎），写出选型理由。
4. 思考题：为什么"等值列在前、范围列在后"是联合索引排序的黄金法则？从索引的有序性推导结论。

---

### 39-03 深分页优化

**知识点**
- **`LIMIT 1000000, 20` 的真实成本**：不是"跳过一百万行很快"，而是**扫完前 1000020 行、扔掉前 100 万行**。每多翻一页成本线性增长，且页越深锁与 buffer pool 压力越大。页深上万时，一次查询就能把数据库拖挂。
- **游标分页（keyset pagination）是根本解**：记住上一页最后一条的排序键（如 `id=100023`），下一页查询 `WHERE id > 100023 ORDER BY id LIMIT 20`。成本恒定 O(页大小)，配合主键索引完美。代价：**只能顺序翻页，不能跳页**——这与第 38 章 ES 的 search_after 是同一个思想。
- **跳页需求用延迟关联**：`SELECT t.* FROM t JOIN (SELECT id FROM t ORDER BY id LIMIT 1000000, 20) tmp ON t.id=tmp.id`。内层只扫覆盖索引（窄），回表只剩 20 次。再配合"业务上限制可跳页数"基本解决。
- **排序键设计决定游标可用性**：按 `create_time DESC, id DESC` 翻页时，条件是 `(create_time, id) < (last_time, last_id)` 的行序元组比较，MySQL 8.0 支持行构造器写法 `WHERE (create_time, id) < (?, ?)`；老版本用等价展开写法。**排序键必须带唯一列兜底**，否则同时刻数据翻页会漏。
- **总数（count(*)）是另一个坑**：深表 `SELECT COUNT(*)` 全索引扫描，页面每次请求都算一次总数是灾难。方案：近似总数（估算值 + "约"字）、缓存计数、或产品上去掉总数只留"下一页"。
- **与 ES 深分页的统一认知**：任何存储的深分页本质都一样——**offset 方案的成本随深度线性涨，游标方案成本恒定**。选型取决于产品需要跳页还是流式浏览。
- 易错点：前端强制要"总条数+跳页"就默默用 offset 硬扛（深页必炸）；游标分页的排序键不含唯一列导致漏数据；缓存 count 后写路径忘了失效（数字长期不准）；用 `LIMIT` 做数据导出循环（offset 越翻越慢，应按 id 游标批量拉）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class DeepPaginationFix {
    record Order(long id, String status, long createTime) {
    }

    /** 模拟一张有序表：id 递增 */
    static List<Order> table(int n) {
        List<Order> rows = new ArrayList<>();
        for (long i = 1; i <= n; i++) {
            rows.add(new Order(i, i % 3 == 0 ? "PAID" : "CREATED", 1700000000L + i));
        }
        return rows;
    }

    static void offsetCost() {
        System.out.println("【offset 分页的成本曲线】");
        List<Order> rows = table(2_000_000);
        int[] offsets = {20, 20_000, 200_000, 1_000_000};
        for (int off : offsets) {
            long scanned = Math.min(off + 20, rows.size());
            System.out.printf("  LIMIT %7d, 20 -> 必须扫过 %8d 行（命中率 %.4f%%）%n",
                    off, scanned, 20.0 / scanned * 100);
        }
        System.out.println("  扫描行数与 offset 同阶增长；offset=百万时一次查询等于半次全表扫描");
        System.out.println();
    }

    static void keysetPaging() {
        System.out.println("【游标分页：WHERE id > lastId LIMIT 20，成本恒定】");
        List<Order> rows = table(2_000_000);
        long cursor = 0;
        int page = 0;
        while (page < 3 && cursor < rows.size()) {
            int start = (int) cursor;
            List<Order> window = rows.subList(start, Math.min(start + 20, rows.size()));
            System.out.printf("  第 %d 页: id %d ~ %d（只扫 20 行）%n",
                    page + 1, window.get(0).id(), window.get(window.size() - 1).id());
            cursor = start + 20;
            page++;
        }
        System.out.println("  SQL：SELECT * FROM orders WHERE id > ? ORDER BY id LIMIT 20");
        System.out.println("  任何页深都只扫描 20 行，这就是游标的全部意义");
        System.out.println();
    }

    static void compositeKeyset() {
        System.out.println("【复合排序键的游标：按 create_time DESC 翻页】");
        List<Order> sorted = table(100).stream()
                .sorted(Comparator.comparingLong(Order::createTime).reversed()
                        .thenComparingLong(Order::id))
                .limit(6).toList();
        Order last = sorted.get(2);
        System.out.printf("  上一页最后一条：(create_time=%d, id=%d)%n", last.createTime(), last.id());
        List<Order> next = sorted.stream()
                .sorted(Comparator.comparingLong(Order::createTime).reversed()
                        .thenComparingLong(Order::id))
                .skip(3).limit(3).toList();
        System.out.println("  WHERE (create_time,id) < (?, ?) ORDER BY create_time DESC, id DESC LIMIT 3");
        System.out.println("  下一页: " + next.stream().map(o -> o.id()).toList());
        System.out.println("  唯一键兜底：同一毫秒的多条订单不会漏、不会重");
        System.out.println();
    }

    static void delayedJoin() {
        System.out.println("【必须跳页时：延迟关联只回表 20 次】");
        long total = 2_000_000, offset = 1_000_000, pageSize = 20;
        long narrowScan = offset + pageSize;
        System.out.printf("  原写法：扫描 %d 行 × 全字段宽行 -> 回表 %d 次%n", narrowScan, narrowScan);
        System.out.printf("  延迟关联：内层覆盖索引扫 %d 行（窄），回表 %d 次%n", narrowScan, pageSize);
        System.out.println("  SQL：SELECT o.* FROM orders o JOIN");
        System.out.println("       (SELECT id FROM orders ORDER BY id LIMIT 1000000, 20) t ON o.id=t.id");
        System.out.println("  配套产品约束：可跳页范围限制在最近 N 页（跳到 50 万条之外本就是伪需求）");
    }

    public static void main(String[] args) {
        offsetCost();
        keysetPaging();
        compositeKeyset();
        delayedJoin();
    }
}
```

**课后练习**
1. 在 100 万行测试表上实测 `LIMIT 0,20` / `LIMIT 999980,20` / 游标分页三种写法的耗时，把数字记下来作为团队的深分页证据。
2. 为订单列表实现复合游标分页（create_time + id），处理"同一时间多条订单"的漏数据问题，并用并发插入验证。
3. 设计 count 优化方案：首屏给"约 128 万+"近似值，翻页后隐藏总数。写出近似值的计算方式（information_schema 估算或采样）。
4. 思考题：为什么导出任务用 `LIMIT offset 循环` 会越跑越慢？用本章公式推导第 100 万次循环的扫描量。
