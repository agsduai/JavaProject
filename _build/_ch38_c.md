### 38-07 聚合分析

**知识点**
- **聚合 = "在搜索结果上做统计"**：与 SQL 的 GROUP BY 对应，但跑在倒排/列存(doc_values)上、针对的是**本次查询命中的集合**，天然支持"先过滤再统计"。
- **两大类聚合**：桶聚合（bucket）按字段切分组——terms（类目、状态）、range（价格段）、date_histogram（按天）；度量聚合（metric）算数值——avg/max/sum/cardinality/percentiles。桶里可以再嵌桶、再嵌度量，构成"类目 → 价格分布 → 均价"的多层报表。
- **聚合字段必须开 doc_values**（keyword/数值默认开）：text 字段聚合需要 fielddata（整字段加载进堆内存），是性能杀手，规范禁止。这就是 38-04 强调状态字段用 keyword 的原因。
- **terms 聚合不是精确 top**：默认按分片本地 top N（shard_size）合并，可能漏掉稀疏桶。需要精确时调大 `shard_size` 或用 `composite` 分页聚合。cardinality 也是近似值（HyperLogLog）。
- **聚合与搜索上下文的关系**：默认聚合作用于 query 命中的文档；`post_filter` 在聚合之后才过滤（用于"点了类目筛选但左侧类目栏仍显示全部"）；`global` 聚合完全无视查询条件。
- **分页聚合**：terms 聚合取 top 10 之外的桶要用 composite（按 after_key 翻页），不能靠 from/size —— 聚合没有深度分页问题但有桶数上限（search.max_buckets）。
- 易错点：对 text 字段做 terms 聚合（报 fielddata 错或按分词分组）；date_histogram 不设 calendar_interval 逻辑（按天/月写错）；聚合桶数上万（响应巨大 + JVM 压力，应改 composite 或减少粒度）；把聚合当实时 OLAP 大数据量扫描（超过亿级的分析应走专门数仓）。

**示例代码**
```java
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class AggregationDemo {
    record Doc(long id, String category, String status, double price, String day) {
    }

    static final List<Doc> HITS = List.of(
            new Doc(1, "phone", "ONSALE", 69.0, "2026-11-15"),
            new Doc(2, "phone", "ONSALE", 39.0, "2026-11-15"),
            new Doc(3, "laptop", "ONSALE", 14999.0, "2026-11-16"),
            new Doc(4, "phone", "OFFSALE", 59.0, "2026-11-16"),
            new Doc(5, "stand", "ONSALE", 29.0, "2026-11-17"),
            new Doc(6, "stand", "ONSALE", 35.0, "2026-11-17"));

    static void termsAgg() {
        System.out.println("【terms 聚合：类目分布】");
        Map<String, Integer> buckets = new TreeMap<>();
        for (Doc d : HITS) {
            buckets.merge(d.category(), 1, Integer::sum);
        }
        buckets.entrySet().stream()
                .sorted((a, b) -> b.getValue() - a.getValue())
                .forEach(e -> System.out.printf("  %-8s doc_count=%d%n", e.getKey(), e.getValue()));
        System.out.println("  字段类型必须是 keyword（doc_values），text 聚合 = 禁止");
        System.out.println();
    }

    static void metricInsideBucket() {
        System.out.println("【桶内嵌度量：每个类目的均价与最高价】");
        Map<String, List<Double>> byCat = new LinkedHashMap<>();
        for (Doc d : HITS) {
            byCat.computeIfAbsent(d.category(), k -> new java.util.ArrayList<>()).add(d.price());
        }
        for (Map.Entry<String, List<Double>> e : byCat.entrySet()) {
            List<Double> ps = e.getValue();
            double avg = ps.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            double max = ps.stream().mapToDouble(Double::doubleValue).max().orElse(0);
            System.out.printf("  %-8s count=%d avg=%.1f max=%.1f%n",
                    e.getKey(), ps.size(), avg, max);
        }
        System.out.println("  DSL 结构：aggs: {by_cat: {terms: {field: category},");
        System.out.println("             aggs: {avg_price: {avg: {field: price}}}}}");
        System.out.println();
    }

    static void histogramAndDate() {
        System.out.println("【直方图类聚合：价格段与按天】");
        Map<String, Integer> priceBuckets = new LinkedHashMap<>();
        String[] ranges = {"0-50", "50-100", "100+"};
        for (Doc d : HITS) {
            String b = d.price() < 50 ? ranges[0] : d.price() < 100 ? ranges[1] : ranges[2];
            priceBuckets.merge(b, 1, Integer::sum);
        }
        System.out.println("  price range：" + priceBuckets);
        Map<String, Integer> byDay = new TreeMap<>();
        for (Doc d : HITS) {
            byDay.merge(d.day(), 1, Integer::sum);
        }
        System.out.println("  date_histogram(calendar_interval=day)：" + byDay);
        System.out.println();
    }

    static void postFilterIdea() {
        System.out.println("【post_filter：聚合看全集，结果看筛选集】");
        System.out.println("  页面布局：左侧类目栏要显示“全部命中里的类目分布”，");
        System.out.println("  结果列表只显示“用户已选类目”的文档");
        Map<String, Integer> aggBuckets = new TreeMap<>();
        for (Doc d : HITS) {
            aggBuckets.merge(d.category(), 1, Integer::sum);
        }
        List<Doc> filtered = HITS.stream().filter(d -> d.category().equals("phone")).toList();
        System.out.println("  聚合（post_filter 之前）：" + aggBuckets);
        System.out.println("  结果（post_filter 之后）  ："
                + filtered.stream().map(d -> d.id()).toList());
        System.out.println("  若把类目筛选放进 query，左侧栏就只剩一个类目——经典 bug");
    }

    public static void main(String[] args) {
        termsAgg();
        metricInsideBucket();
        histogramAndDate();
        postFilterIdea();
    }
}
```

**课后练习**
1. 为商品搜索页写出完整 DSL：bool 过滤（在售）+ terms 聚合（类目、品牌）+ 桶内均价 + post_filter（用户选中的品牌），并解释各部分为何放在那个上下文。
2. 用 date_histogram 实现"近 30 天每日订单量与销售额"，写出聚合树；再把每日订单量换成分时段（小时）直方图，对比两种 interval 的适用场景。
3. 思考题：为什么 terms 聚合在多分片下可能漏桶？写出 shard_size、size、总文档数三者的关系，以及一个"必须精确"的应对方案。

---

### 38-08 分页与深度分页

**知识点**
- **from+size 的默认限制是 10000**：不是性能调优经验值，而是硬限制（`index.max_result_window`）。因为每个分片都要返回 from+size 条文档到协调节点做归并排序——查第 9900 条时，3 个分片各取 9910 条，归并内存与网络成本随页深线性暴涨。
- **三种翻页方案**：`from+size` 只适合浅翻页（搜索结果前几页）；`search_after`（用上一页最后一条的排序值做游标）适合无限滚动与深页，代价 O(页大小)且**要求排序唯一**；`scroll` 快照式遍历（历史数据导出），新代码用带 `search_after` 的 point-in-time（PIT）替代。
- **search_after 的排序必须唯一**：排序键相同就无法定位"下一页从哪开始"，标准做法是 `_score` 后面拼 `_id`（或业务唯一键）作为兜底排序。**两个文档排序值完全相同 = 翻页丢数据/重复数据**。
- **跳页与游标页的取舍**：电商搜索页用户要跳第 5 页（from+size 可以做，页深有限）；日志平台是无限下拉（search_after 天然匹配）；导出百万行走 PIT+search_after 批量拉取。
- **深度分页的正确产品解法**：限制可跳页数（Google 只给你到第几十页）；改成筛选 + 排序引导（用户深翻页本身说明排序或筛选没起作用）。
- **scroll/PIT 的资源语义**：scroll 会保持快照上下文占资源，忘记 clear 就泄漏；PIT 也有 keep_alive，同样要在结束显式释放。
- 易错点：把 from+size 的 10000 限制当成"ES 慢"（是方案选错了）；search_after 忘加唯一排序键导致翻页数据丢失；scroll 上下文不用了不清理（节点堆内存被吃光）；导出任务用 from+size 循环翻页（页深一超限直接报错）。

**示例代码**
```java
import java.util.Comparator;
import java.util.List;

public class DeepPagination {
    record Doc(long id, String title, double score) {
    }

    static final List<Doc> ALL = List.of(
            new Doc(1, "苹果手机壳", 12.5),
            new Doc(2, "小米手机壳", 11.8),
            new Doc(3, "华为手机壳", 11.2),
            new Doc(4, "透明手机壳", 10.9),
            new Doc(5, "硅胶手机壳", 10.5),
            new Doc(6, "金属手机壳", 10.1),
            new Doc(7, "皮纹手机壳", 9.8));

    static void fromSizeCost() {
        System.out.println("【from+size 的真实成本：归并窗口随页深放大】");
        int shards = 3;
        for (int page : new int[]{1, 100, 1000}) {
            int from = (page - 1) * 10;
            int perShard = from + 10;
            System.out.printf("  取第 %3d 页：每分片返回 %5d 条 -> 协调节点归并 %5d 条%n",
                    page, perShard, perShard * shards);
        }
        System.out.println("  max_result_window=10000：from+size 超过即 400 错误，是保护不是刁难");
        System.out.println();
    }

    static void searchAfterPaging() {
        System.out.println("【search_after：游标式翻页，成本与页深无关】");
        Comparator<Doc> cmp = Comparator.comparingDouble(Doc::score).reversed()
                .thenComparingLong(Doc::id);            // 分数 + id 双键保证唯一
        List<Doc> sorted = ALL.stream().sorted(cmp).toList();
        int pageSize = 3;
        int start = 0;
        for (int page = 1; start < sorted.size(); page++) {
            List<Doc> window = sorted.subList(start, Math.min(start + pageSize, sorted.size()));
            Doc last = window.get(window.size() - 1);
            System.out.printf("  第 %d 页: %s  after=%d%n", page,
                    window.stream().map(Doc::id).toList(), last.id());
            start += pageSize;
        }
        System.out.println("  每次只取 pageSize 条，翻到第 10000 页成本不变");
        System.out.println("  前提：排序键必须唯一（score+id），否则两文档同分无法定位游标");
        System.out.println();
    }

    static void pitIdea() {
        System.out.println("【PIT/scroll：全量导出的正确姿势】");
        String[] steps = {
                "1) POST index/_pit?keep_alive=5m  -> 打开快照上下文，得到 pit_id",
                "2) 循环：search 带 pit_id + search_after，每批 1000 条",
                "3) 期间新写入的文档不出现在本次遍历（一致性快照）",
                "4) 结束后 DELETE _pit 释放资源（忘记释放 = 堆内存泄漏）"};
        for (String s : steps) {
            System.out.println("  " + s);
        }
        System.out.println("  适用：数仓导出、重建索引；不适用：用户翻页（会被快照语义困惑）");
        System.out.println();
    }

    static void decisionTable() {
        System.out.println("【选型决策表】");
        String[][] rows = {
                {"搜索结果前几页 + 跳页", "from+size", "页深 ≤ 数百，产品上限制可跳页数"},
                {"无限下拉 / 信息流", "search_after", "排序键唯一，成本恒定"},
                {"全量导出 / reindex", "PIT + search_after", "快照一致性，批量拉取"},
                {"老代码维护", "scroll", "记得 clear，新项目不再选用"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-22s %-20s %s%n", r[0], r[1], r[2]);
        }
    }

    public static void main(String[] args) {
        fromSizeCost();
        searchAfterPaging();
        pitIdea();
        decisionTable();
    }
}
```

**课后练习**
1. 实测：建 10 万文档索引，用 from+size 翻到 9000 与用 search_after 翻同样深度，对比耗时，解释成本曲线差异。
2. 复现"search_after 排序键不唯一导致翻页丢文档"：去掉 id 兜底排序，构造同分文档，观察翻页结果，再修复验证。
3. 写一个 PIT 导出工具：带进度与速率控制，结束后确保释放 PIT；故意不释放并用 _cat/nodes 观察 JVM 堆变化。
4. 思考题：为什么产品层面"允许跳到任意页"通常是伪需求？从用户行为与排序质量两个角度分析。

---

### 38-09 数据同步：让搜索库跟上数据库

**知识点**
- **同步的目标是一致性体验**：用户下单成功后，搜不到刚改的库存/状态，就是同步问题。四种主流方案：**同步双写**（业务代码里写完 MySQL 再写 ES）、**异步双写（MQ）**、**基于 binlog 的 CDC（Canal → MQ → ES）**、**定时全量/增量拉取**。
- **同步双写的问题在代码结构**：ES 故障会拖垮主业务（要 try-catch 降级）、成功标准不一致（DB 成功 ES 失败算什么）、耦合了存储细节。适合小系统过渡，不适合作为长期架构。
- **MQ 异步双写是最常用的平衡点**：业务事务只保证 MySQL，发一条领域事件（如 `ProductChanged`）到 MQ，消费者更新 ES。**丢消息要靠可靠投递**（本地消息表，第 28 章的方法）+ **消费失败重试 + 死信告警**。
- **CDC（Canal）方案解耦最彻底**：伪装成 MySQL 从库订阅 binlog，业务代码零侵入；天然捕获所有写入（包括别的服务/脚本改的）。代价是引入组件链路、binlog 格式要求（ROW）、以及"删库脚本也会被同步"（要白名单表）。
- **顺序与幂等是同步的正确性底线**：同一商品两次变更必须按 binlog/MQ 顺序应用（按商品 ID 分区）；消费者必须幂等（版本号/更新时间比较，旧事件丢弃）。**乱序 + 不幂等 = 数据悄悄回退**。
- **兜底是定时校验修复**：任何方案都可能丢一条，每天/每小时跑对账任务（比对 MySQL 与 ES 的版本号/更新时间），差异记录并修复。**架构上承认"最终一致"，工程上用对账收敛误差**。
- 易错点：删了 MySQL 数据 ES 里还在（同步管道只处理 insert/update）；ES 写入失败被 try-catch 吞掉（连日志都没有，数据悄悄落后）；大促批量改价直接打爆 ES 写入（要削峰与 bulk 合并）；同步表全量订阅（应按业务白名单过滤，减少无谓写入）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DataSync {
    record Row(String status, long version, long updatedAt) {
    }

    /** 简化事件：商品变更 + 版本号 */
    record Event(long productId, long version, String op) {
    }

    static final Map<Long, Row> MYSQL = new LinkedHashMap<>();
    static final Map<Long, Row> ES = new LinkedHashMap<>();
    static final List<String> SYNC_LOG = new ArrayList<>();

    static void compareApproaches() {
        System.out.println("【四种同步方案对比】");
        String[][] rows = {
                {"同步双写", "低", "ES 故障拖垮主业务，代码耦合", "小系统过渡"},
                {"MQ 异步", "中", "要处理可靠投递/幂等/顺序", "绝大多数业务（推荐起步）"},
                {"CDC binlog", "中高", "多一套组件链路，运维成本", "大系统、多写入方"},
                {"定时拉取", "低", "延迟分钟级，无实时性", "兜底对账、报表类索引"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-10s 实时性%-3s %s%n      %s%n",
                    r[0], r[1], r[2], r[3]);
        }
        System.out.println();
    }

    static void mqSync() {
        System.out.println("【MQ 异步同步：事件 -> 消费 -> 幂等写 ES】");
        updateDb(1001, 1, "ONSALE");
        updateDb(1001, 2, "OFFSALE");
        List<Event> mq = List.of(new Event(1001, 2, "OFFSALE"), new Event(1001, 1, "ONSALE"));
        System.out.println("  MQ 里事件乱序到达：" + mq);
        for (Event e : mq) {
            Row current = ES.get(e.productId());
            if (current != null && e.version() <= current.version()) {
                SYNC_LOG.add("事件 v" + e.version() + " 丢弃（版本过旧，幂等生效）");
                continue;
            }
            ES.put(e.productId(), new Row(e.op(), e.version(), e.version()));
            SYNC_LOG.add("事件 v" + e.version() + " 应用 -> status=" + e.op());
        }
        for (String s : SYNC_LOG) {
            System.out.println("  " + s);
        }
        System.out.println("  MySQL 现状：" + MYSQL.get(1001) + "  ES 现状：" + ES.get(1001));
        System.out.println("  结论：按版本幂等，乱序也不回退；顺序问题退化为“旧事件被丢弃”");
        System.out.println();
    }

    static void updateDb(long id, long version, String status) {
        MYSQL.put(id, new Row(status, version, version));
    }

    static void reconcile() {
        System.out.println("【对账兜底：承认丢失，定时修复】");
        // 模拟 ES 漏了一条更新
        MYSQL.put(1002L, new Row("ONSALE", 5, 5));
        ES.put(1002L, new Row("OFFSALE", 4, 4));
        System.out.println("  1002：MySQL " + MYSQL.get(1002L) + " vs ES " + ES.get(1002L));
        if (ES.get(1002L).version() < MYSQL.get(1002L).version()) {
            ES.put(1002L, MYSQL.get(1002L));
            System.out.println("  对账任务发现版本落后 -> 以 MySQL 为准修复 ES");
        }
        System.out.println("  对账 SQL 思路：两边各取 version/updatedAt，diff 出落后清单");
        System.out.println("  原则：同步管道保证“快”，对账任务保证“最终对”");
    }

    public static void main(String[] args) {
        compareApproaches();
        mqSync();
        reconcile();
    }
}
```

**课后练习**
1. 把示例扩展成"本地消息表"版本：业务事务里写消息表，后台投递 MQ，失败重试，画出状态机并实现核心表结构。
2. 实现按商品 ID 分区的顺序消费模拟：多线程乱序消费同 ID 事件，验证无版本幂等时会回退、有版本幂等时结果正确。
3. 思考题：CDC 方案里，某同事直接 UPDATE MySQL 修数据，ES 会不会跟着变？这是优点还是隐患？给出治理规则。
