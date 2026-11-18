### 38-10 集群、分片与副本

**知识点**
- **三个角色**：master（管集群元数据：建索引、分配分片）、data（存数据、执行查询）、coordinating（接收请求、归并结果——每个节点都能当协调者，请求发给谁谁就是）。小集群三节点可以身兼三职，大集群按职责分离。
- **分片 = 索引的水平切片**：主分片数在创建时定死（路由公式 `hash(routing) % primary_count`），扩容只能 reindex 或用路由调整（routing 迁移期间双写）。**分片是分布式的单位**：查询要协调节点把请求扇出到所有主/副分片副本，取 top N 归并。
- **副本 = 主分片的拷贝，三重作用**：数据冗余（节点挂了不丢数据）、读扩展（读请求分担到副本）、故障转移（主挂了副本自动提升为主）。**单节点集群副本永远是 unassigned（没地方放）**，这也是本地测试集群 yellow 的原因。
- **集群健康三色**：green（主副全部分配）、yellow（副本未分配，数据不丢但冗余缺失）、red（有主分片丢失，数据真缺了）。报警规则：生产 red 立即处理，yellow 观察。
- **脑裂与 quorum**：老版本网络分区时两个 master 并存导致元数据分裂；现代 ES 用"多数派投票"（7.x 起集群状态变更需过半 master-eligible 节点同意）根治。**master-eligible 节点数保持奇数（3 或 5）**是规划铁律。
- **写入与查询的路由**：写请求协调节点按路由算出目标分片转发；读请求扇出到"主+副"中选择（自适应副本选择）。文档不能改 routing（除非显式指定），否则写入与查询落在不同分片，永远查不到。
- **均衡与水位**：磁盘水位 85% 停止分配新分片、90% 迁移分片、95% 索引只读（flood-stage）。磁盘满导致的"索引只读块"是运维高频事故，恢复要手动放开写入块。
- 易错点：单节点集群配 1 副本（永远 yellow，还白占写入性能）；分片规划过大（几千个空分片的"分片风暴"吃光 master）；滚动重启不看分片分配延迟设置（分片疯狂重平衡）；客户端连了一个节点当"主节点"用（任意节点都是协调节点，没有"主节点专用"的客户端接入点）。

**示例代码**
```java
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class EsCluster {
    record Shard(String index, int shardNo, boolean primary, String nodeId, String state) {
    }

    record Node(String id, String role, String ip) {
    }

    static final List<Node> NODES = List.of(
            new Node("node-1", "master+data", "10.0.0.11"),
            new Node("node-2", "data", "10.0.0.12"),
            new Node("node-3", "data", "10.0.0.13"));

    static void topology() {
        System.out.println("【三节点集群与 product 索引的分片分布】");
        for (Node n : NODES) {
            System.out.printf("  %-7s %-12s %s%n", n.id(), n.role(), n.ip());
        }
        List<Shard> shards = List.of(
                new Shard("product", 0, true, "node-1", "STARTED"),
                new Shard("product", 0, false, "node-2", "STARTED"),
                new Shard("product", 1, true, "node-2", "STARTED"),
                new Shard("product", 1, false, "node-3", "STARTED"),
                new Shard("product", 2, true, "node-3", "STARTED"),
                new Shard("product", 2, false, "node-1", "STARTED"));
        System.out.println("  product：3 主分片 + 1 副本，主副本不同节点：");
        for (Shard s : shards) {
            System.out.printf("    %-7s p%-2d %-8s %s  %s%n", s.index(), s.shardNo(),
                    s.primary() ? "primary" : "replica", s.nodeId(), s.state());
        }
        System.out.println("  node-3 挂掉 -> 它上面的副本变主（p2 提升到 node-1 的副本）");
        System.out.println();
    }

    static void routing() {
        System.out.println("【路由公式：写入与查询落在同一分片】");
        long[] docIds = {1001, 1002, 1003, 1004, 1005};
        int primaryCount = 3;
        for (long id : docIds) {
            int shard = (int) (Math.abs(id * 31L) % primaryCount);
            System.out.printf("  doc %d -> shard %d%n", id, shard);
        }
        System.out.println("  公式 shard = hash(routing) % primary_count");
        System.out.println("  推论：主分片数变了路由全变 -> 扩主分片必须 reindex");
        System.out.println("  自定义 routing（如按租户）可以让同一租户的数据落同一分片");
        System.out.println();
    }

    static void healthAndBrainSplit() {
        System.out.println("【集群健康与 master 选举】");
        String[][] colors = {
                {"green", "主+副全部分配", "正常"},
                {"yellow", "副本未分配", "冗余缺失，排查磁盘/单节点"},
                {"red", "主分片丢失", "数据缺失，立即处理"},
        };
        for (String[] c : colors) {
            System.out.printf("  %-7s %-16s %s%n", c[0], c[1], c[2]);
        }
        System.out.println("  master 选举：master-eligible 节点过半才有效（quorum）");
        Map<String, Integer> split = new LinkedHashMap<>();
        split.put("分区A（node-1, node-2）", 2);
        split.put("分区B（node-3）", 1);
        System.out.println("  网络分区：" + split);
        System.out.println("  分区A 有过半(2/3) -> 能选 master 继续服务；分区B 无 master -> 只读/拒绝");
        System.out.println("  规划铁律：master-eligible 用奇数（3 或 5），偶数只会浪费一台");
        System.out.println();
    }

    static void diskWatermark() {
        System.out.println("【磁盘水位：三个阈值对应的系统行为】");
        String[][] marks = {
                {"85%（low）", "不再往该节点分配新分片"},
                {"90%（high）", "开始把分片迁走"},
                {"95%（flood_stage）", "索引进入只读块，写入全部拒绝"},
        };
        for (String[] m : marks) {
            System.out.printf("  %-16s %s%n", m[0], m[1]);
        }
        System.out.println("  高频事故：磁盘满 -> 索引只读 -> 清理磁盘后写入仍失败");
        System.out.println("  恢复：PUT index/_settings 解除 index.blocks.write: true");
    }

    public static void main(String[] args) {
        topology();
        routing();
        healthAndBrainSplit();
        diskWatermark();
    }
}
```

**课后练习**
1. 搭一个 3 节点集群（或用 docker 模拟），创建 3 主 1 副索引，逐个 kill 数据节点观察健康颜色变化与副本提升，记录 `_cat/shards` 输出。
2. 用自定义 routing 实验同一文档写入与查询的路由一致性，然后尝试改主分片数，记录报错并解释路由公式。
3. 思考题：为什么"master-eligible 偶数个"是反模式？从 quorum 推导 4 台与 3 台在容忍故障数上的差别。

---

### 38-11 综合案例：商品搜索全链路

**知识点**
- **把全章串成一条查询链路**：用户输入 → 查询预处理（去噪、同义扩展）→ 分词（ik_smart）→ bool 查询（must 全文 + filter 条件 + should 加权）→ BM25 评分 + function_score 业务加权 → 分页（search_after 或 from+size）→ 聚合（类目/品牌导航栏）→ 高亮返回 → 前端渲染。每一课的知识都在这条链路上各就各位。
- **索引设计**：title/description 用 text（ik_max_word 索引侧），category/brand/status 用 keyword，price 用 scaled_float，销量与上架时间作为 function_score 输入；`copy_to` 把 title+brand+category 拼进一个 all 字段简化查询。
- **查询侧四层过滤**：keyword（全文 must，参与评分）、条件（filter：状态/价格区间/类目，不评分可缓存）、业务加权（function_score：销量+时效）、排序兜底（score desc, id asc 保证 search_after 唯一）。
- **左侧导航栏的聚合要用"全集"**：terms 聚合挂在 query 之后、post_filter 之前，用户选了品牌后类目栏仍显示所有类目的计数（否则用户只能看到单一类目，无法切换——38-07 的 post_filter 知识点）。
- **高亮与返回裁剪**：highlight 标记命中词片段；`_source` 只取前端需要的字段（不要把整个文档原样返回）；空结果时降级（放宽 minimum_should_match 或去掉业务过滤再查一次）。
- **上线前验收清单**：mapping 全部显式声明；同步链路有对账兜底；压测目标 QPS 达标（P99 < 200ms）；排序盲测（改评分参数前后对比 top 10）；空查询/超长查询/特殊字符等边界用例。
- 易错点：搜索接口每次请求都全量查 ES 拉所有字段（用 _source 过滤 + 分页）；没有降级的空结果页（体验断裂）；聚合与结果用了不同过滤条件导致导航栏计数与结果数不一致；上线后从不看慢查询日志（slow log 是搜索质量的第一观测面）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ProductSearchCase {
    record Product(long id, String title, String category, String brand,
                   String status, double price, int sales, long listedDaysAgo) {
    }

    static final List<Product> ES_VIEW = List.of(
            new Product(1, "苹果 iPhone 15 官方手机壳", "phone", "Apple", "ONSALE", 69, 5200, 10),
            new Product(2, "小米手机 透明保护壳", "phone", "Xiaomi", "ONSALE", 39, 8100, 30),
            new Product(3, "苹果 MacBook Pro 14", "laptop", "Apple", "ONSALE", 14999, 900, 90),
            new Product(4, "华为 Mate60 手机壳 皮质", "phone", "Huawei", "OFFSALE", 59, 300, 60),
            new Product(5, "手机支架 铝合金 桌面", "stand", "Baseus", "ONSALE", 29, 12000, 5));

    record SearchRequest(String keyword, String category, String brand,
                         double minPrice, double maxPrice, boolean onSaleOnly) {
    }

    /** 查询预处理：小写 + 同义扩展 */
    static List<String> preprocess(String input) {
        List<String> terms = new ArrayList<>();
        for (String t : input.toLowerCase().split("\\s+")) {
            terms.add(t);
            if (t.equals("苹果")) {
                terms.add("apple");          // 同义词扩展：苹果 <-> apple
            }
        }
        return terms;
    }

    static boolean titleMatch(Product p, List<String> terms) {
        String lower = (p.title() + " " + p.brand()).toLowerCase();
        return terms.stream().anyMatch(lower::contains);   // 至少 1 个词命中（should 语义）
    }

    static double score(Product p, List<String> terms) {
        String lower = (p.title() + " " + p.brand()).toLowerCase();
        double bm25 = terms.stream().filter(lower::contains).count() * 2.0;
        double salesBoost = 1.0 + p.sales() / 100000.0;
        double recencyBoost = 1.0 + Math.max(0, 60 - p.listedDaysAgo()) / 200.0;
        return bm25 * salesBoost * recencyBoost;
    }

    static void execute(SearchRequest req) {
        System.out.println("---- 查询：" + req.keyword() + " category=" + req.category()
                + " brand=" + req.brand() + " price=" + req.minPrice() + "~" + req.maxPrice()
                + (req.onSaleOnly() ? " 仅在售" : ""));
        List<String> terms = preprocess(req.keyword());
        // 1) 全文 must（参与评分）
        List<Product> matched = new ArrayList<>();
        for (Product p : ES_VIEW) {
            if (titleMatch(p, terms)) {
                matched.add(p);
            }
        }
        // 2) filter 条件（不评分、可缓存）
        matched = matched.stream()
                .filter(p -> req.category() == null || req.category().equals(p.category()))
                .filter(p -> req.brand() == null || req.brand().equalsIgnoreCase(p.brand()))
                .filter(p -> p.price() >= req.minPrice() && p.price() <= req.maxPrice())
                .filter(p -> !req.onSaleOnly() || "ONSALE".equals(p.status()))
                .toList();
        // 3) 评分排序（score desc, id asc 保证游标唯一）
        List<Product> ranked = matched.stream()
                .sorted(Comparator.comparingDouble((Product p) -> score(p, terms)).reversed()
                        .thenComparingLong(Product::id))
                .toList();
        for (Product p : ranked) {
            System.out.printf("    [%.3f] %d %s（%s）￥%.0f 销量%d%n",
                    score(p, terms), p.id(), p.title(), p.brand(), p.price(), p.sales());
        }
        // 4) 聚合：挂在 filter 全集上（post_filter 语义），导航栏始终显示全部类目
        Map<String, Integer> catAgg = new LinkedHashMap<>();
        List<Product> aggScope = ES_VIEW.stream()
                .filter(p -> req.brand() == null || req.brand().equalsIgnoreCase(p.brand()))
                .toList();
        for (Product p : aggScope) {
            catAgg.merge(p.category(), 1, Integer::sum);
        }
        System.out.println("    类目导航栏：" + catAgg);
        System.out.println();
    }

    static void highlightDemo() {
        System.out.println("【高亮与 _source 裁剪】");
        Product p = ES_VIEW.get(0);
        String hl = p.title().replace("手机壳", "<em>手机壳</em>");
        System.out.println("  返回字段：id/title(高亮)/price/brand —— 不回传 sales、listedDays 等");
        System.out.println("  高亮片段：" + hl);
    }

    public static void main(String[] args) {
        execute(new SearchRequest("苹果 手机", null, null, 0, 999999, true));
        execute(new SearchRequest("苹果 手机", "phone", null, 0, 100, true));
        execute(new SearchRequest("支架", null, "Baseus", 0, 999999, false));
        highlightDemo();
    }
}
```

**课后练习**
1. 把本章案例在你的 ES 里真实落地：建索引（显式 mapping）→ bulk 写入 100 条商品 → 实现查询 DSL（bool+function_score+聚合+post_filter+高亮）→ 对比预期结果。
2. 压测与调优：对搜索接口做 500 QPS 压测，记录 P99；打开 slow log（阈值 200ms），找到最慢的一条查询并给出优化（filter 化、聚合瘦身、_source 裁剪）。
3. 设计空结果降级：关键词无命中时自动放宽（去掉品牌过滤 / 降低 minimum_should_match / 推荐同类目热销），实现并说明每级降级的触发条件。
4. 思考题：商品搜索里"相关性与销量谁该排前面"？给出你的 function_score 设计，并说明在什么业务阶段应该把天平偏向哪边。
