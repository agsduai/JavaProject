### 38-04 索引与映射设计

**知识点**
- **mapping 是字段的"类型 + 分析方式"声明**：每个字段要回答三个问题——什么类型（text/keyword/long/date/boolean）、要不要分词（analyzer）、要不要进倒排（index）与聚合排序（doc_values）。设计错了轻则查询失效，重则必须重建索引。
- **text 与 keyword 的分工**：标题、描述 → `text`（分词、全文检索、评分）；状态、类目、手机号 → `keyword`（不分词，精确过滤、排序、聚合）。两个字段都要时用 multi-field：`title` 是 text，`title.keyword` 是 keyword，一个字段两套能力。
- **动态映射（dynamic mapping）是便利也是陷阱**：写入未声明的新字段时 ES 自动猜类型——首个值像数字就建成 long、像日期就建成 date、其他建成带 `.keyword` 子字段的 text。**猜错类型后不可修改，只能重建索引**。生产规范：`dynamic: false`（新字段只存 _source 不建索引）或干脆关闭自动添加。
- **mapping 不可变的例外**：可以"追加"新字段，不能改已有字段的类型/analyzer。改 analyzer 的标准动作是：建新索引（带别名切换）→ reindex → 原子切换别名。别名（alias）是零停机重建索引的基石。
- **倒排索引之外的二级结构**：`doc_values` 是正排的列式存储，支持排序/聚合/脚本（keyword/数值默认开启）；`_source` 存原始 JSON，用于返回结果与 reindex。`index: false` 只存不搜，`fielddata` 是 text 字段做聚合的遗老遗少（内存杀手，禁用）。
- **索引模板（template）与分片规划**：日志类按天建索引用模板统一 mapping；业务索引分片数 = 预期单分片 10~40GB；**主分片数建好后不可改**（文档路由依赖它），副本数可以随时调。
- **null_value 与 copy_to**：keyword 字段默认 null 不索引，用 `null_value: ""` 补一个可检索占位；`copy_to: all` 把多个字段拼进一个搜索字段，实现"一次 match 查多列"。
- 易错点：状态字段被动态映射猜成 text+keyword 子字段，聚合出来的桶按分词词组（"已支付"变成"已"+"支付"）；date 类型猜错导致时间字符串拒写；一个索引几十个分片（每个分片都是一个 Lucene 实例，小分片是纯开销）；reindex 期间忘了别名切换，前端连的还是旧索引。

**示例代码**
```java
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class IndexMapping {
    record FieldDef(String name, String type, String analyzer, boolean indexed, String useCase) {
    }

    static void productMapping() {
        System.out.println("【商品索引 mapping 设计】");
        List<FieldDef> fields = List.of(
                new FieldDef("title", "text", "ik_max_word（+ik_smart 查询侧）", true,
                        "全文搜索与评分"),
                new FieldDef("title.keyword", "keyword", "不分词", true, "精确匹配与排序"),
                new FieldDef("category", "keyword", "-", true, "过滤 + terms 聚合"),
                new FieldDef("price", "scaled_float(精度100)", "-", true, "范围过滤与排序"),
                new FieldDef("status", "keyword", "-", true, "状态过滤，绝不用 text"),
                new FieldDef("description", "text", "ik_max_word", true, "低权重全文字段"),
                new FieldDef("innerRemark", "text", "-", false, "index:false 只存不搜"),
                new FieldDef("created", "date", "-", true, "范围过滤与排序"));
        for (FieldDef f : fields) {
            System.out.printf("  %-22s %-20s %-28s %s%n",
                    f.name(), f.type(), f.analyzer(), f.useCase());
        }
        System.out.println();
    }

    static void dynamicMappingTrap() {
        System.out.println("【动态映射的猜类型事故】");
        Map<String, String> guessed = new LinkedHashMap<>();
        guessed.put("orderId", "首值 1001 -> long（看起来没问题）");
        guessed.put("userNo", "首值 \"1001\" -> text + .keyword（后续数字串也能进，但聚合错位）");
        guessed.put("created", "首值 \"2026-01-01\" -> date（换成时间戳格式后拒写）");
        for (Map.Entry<String, String> e : guessed.entrySet()) {
            System.out.printf("  %-9s %s%n", e.getKey(), e.getValue());
        }
        System.out.println("  约束：已有字段类型不可改 -> 只能新索引 + reindex + 别名切换");
        System.out.println("  预防：显式 mapping；日志类索引 dynamic:false 或 strict");
        System.out.println();
    }

    static void reindexFlow() {
        System.out.println("【零停机改 mapping：别名切换四步】");
        String[] steps = {
                "1) 创建 product_v2（新 mapping，含新 analyzer）",
                "2) reindex：POST _reindex {source: product_v1, dest: product_v2}（后台异步）",
                "3) 切别名（原子操作）：移除 product_v1，添加 product_v2，都叫 product",
                "4) 应用侧永远只连别名 product，切换瞬间完成，客户端无感"};
        for (String s : steps) {
            System.out.println("  " + s);
        }
        System.out.println("  反例：应用直连 product_v1 实体名 -> 切换后前端仍写旧索引");
        System.out.println();
    }

    static void shardPlanning() {
        System.out.println("【分片与副本规划】");
        System.out.println("  主分片：单分片 10~40GB 为佳；3 个节点数据量 100GB -> 5 主分片");
        System.out.println("  副本：生产至少 1 副本（节点宕机不丢数据 + 读扩展）");
        System.out.println("  主分片数创建后不可改：路由公式 shard = hash(routing) % primary_count");
        System.out.println("  太多小分片：每个分片一个 Lucene 实例 + 一份固定内存开销，纯浪费");
        System.out.println("  日志场景：按天滚动索引 + 模板 + ILM 自动删除，分片数在模板里定");
    }

    public static void main(String[] args) {
        productMapping();
        dynamicMappingTrap();
        reindexFlow();
        shardPlanning();
    }
}
```

**课后练习**
1. 为你项目的核心业务对象写出完整 mapping（含 multi-field、copy_to），标注每个字段的选择理由，并检查动态映射是否已关闭。
2. 复现一次"动态映射猜错"：让一个字段先写入 `"1001"` 再写入 `2002`，观察结果，然后给出两种修复路径（reindex / 兼容写法）。
3. 思考题：为什么主分片数不可修改而副本数可以？结合路由公式推导"扩主分片必须 reindex"的原因。

---

### 38-05 DSL 查询：term、match 与 bool 组合

**知识点**
- **查询的执行模型**：Query DSL 是一个 JSON 树，叶子是 `term/match/range`，枝干是 `bool` 的四种组合（must=AND 计分、filter=AND 不计分、should=OR 加分、must_not=NOT 不计分）。理解这棵树就理解了 90% 的 ES 查询。
- **term 与 match 的本质区别**：`term` 拿查询词**原样**去倒排表精确匹配（不对查询词分词），适合 keyword/状态/ID；`match` 先对查询词**分词**，再对各词 OR（默认 should）/AND 组合，适合 text 全文。**对 keyword 字段用 match 会命中 0**（keyword 的 term 就是整串，你的长查询词当然查不到）。
- **filter 上下文是性能关键**：filter 不算相关性、结果可被节点级缓存（filter cache），重复过滤条件几乎零成本。**能用 filter 就不要放 must**——"status=PAID AND category=phone" 这类条件放进 must 纯属浪费评分计算。
- **should 的两副面孔**：bool 里只有 should（无 must/filter）→ 至少匹配 1 个（minimum_should_match 默认 1）；有 must/filter 时 → should 变成加分项（匹配了得分更高，不匹配也能命中）。想强制"至少匹配 N 个词"要显式写 `minimum_should_match`。
- **range 与日期数学**：`gte/lte` 支持 `now-7d/d` 这类表达式；数值范围走 point 结构（BKD 树），与倒排是两套机制。`exists` 查"字段存在"，注意 null 值默认不索引。
- **查询模板化**：业务代码里拼 DSL 的标准做法是参数模板（占位符 + 参数列表），防止"用户输入直接拼进 DSL JSON"的注入与语法破坏，等价于 SQL 的参数化。
- 易错点：把用户输入直接拼进 query_string（`AND` `OR` 这些操作符语法可以被人构造）；match 一个 100 字长句导致分词出 50 个 term、should 全 OR 命中一堆弱相关结果（应该限词数或用 match_phrase）；must 里塞几十个 filter 条件让查询变成 CPU 重灾区。

**示例代码**
```java
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DslQuery {
    record Doc(long id, String title, String category, String status, double price) {
    }

    static final List<Doc> INDEX = List.of(
            new Doc(1, "苹果 iPhone 15 手机壳", "phone", "ONSALE", 69.0),
            new Doc(2, "小米手机 保护壳", "phone", "ONSALE", 39.0),
            new Doc(3, "苹果 MacBook Pro 14", "laptop", "ONSALE", 14999.0),
            new Doc(4, "华为 Mate60 手机壳", "phone", "OFFSALE", 59.0),
            new Doc(5, "手机支架 铝合金", "stand", "ONSALE", 29.0));

    /** 模拟 match：查询词分词后 should OR */
    static List<Doc> matchText(String query) {
        List<String> terms = List.of(query.toLowerCase().split("\\s+"));
        List<Doc> hits = new java.util.ArrayList<>();
        for (Doc d : INDEX) {
            long count = terms.stream().filter(t -> d.title().toLowerCase().contains(t)).count();
            if (count > 0) {
                hits.add(d);
            }
        }
        return hits;
    }

    /** 模拟 term：原样匹配 keyword 字段 */
    static List<Doc> termKeyword(String field, String value) {
        List<Doc> hits = new java.util.ArrayList<>();
        for (Doc d : INDEX) {
            Object v = switch (field) {
                case "category" -> d.category();
                case "status" -> d.status();
                default -> null;
            };
            if (value.equals(v)) {
                hits.add(d);
            }
        }
        return hits;
    }

    static void termVsMatch() {
        System.out.println("【term vs match：分不分查询词，结果天差地别】");
        System.out.println("  match(title, \"苹果 手机\") -> " + ids(matchText("苹果 手机"))
                + "（分词为 苹果/手机，任一命中）");
        System.out.println("  term(title,  \"苹果 手机\") -> 原样找这个整串 term，0 命中");
        System.out.println("  term(status, \"ONSALE\")   -> " + ids(termKeyword("status", "ONSALE"))
                + "（keyword 精确匹配）");
        System.out.println("  match(status,\"ONSALE\")   -> status 是 keyword，查询词被 match 分词后仍是 ONSALE，");
        System.out.println("                            结果恰好相同，但这是巧合不是设计，规范要求 term");
        System.out.println();
    }

    static void boolTree() {
        System.out.println("【bool 树：must 计分 / filter 缓存 / should 加分 / must_not 排除】");
        System.out.println("  查询：类目 phone + 在售 + 标题含 手机，排除 OFFSALE");
        List<Doc> must = matchText("手机");
        List<Doc> filterCategory = termKeyword("category", "phone");
        List<Doc> filterStatus = termKeyword("status", "ONSALE");
        List<Doc> result = new java.util.ArrayList<>(must);
        result.retainAll(filterCategory);
        result.retainAll(filterStatus);
        System.out.println("    must(match 手机)      = " + ids(must) + "  <- 参与评分");
        System.out.println("    filter(category=phone)= " + ids(filterCategory) + "  <- 不评分，可缓存");
        System.out.println("    filter(status=ONSALE) = " + ids(filterStatus) + "  <- 不评分，可缓存");
        System.out.println("    结果 = " + ids(result));
        System.out.println("  对照：把 category 放进 must -> 每次请求都重算评分，白白烧 CPU");
        System.out.println();
    }

    static void minimumShouldMatch() {
        System.out.println("【should 的两副面孔】");
        System.out.println("  bool{should:[苹果,手机]}（无 must）-> 至少匹配 1 个即可命中（OR 语义）");
        System.out.println("  bool{filter:[...],should:[苹果,手机]} -> should 只加分：匹配 0 个也能命中");
        System.out.println("  需要“至少 2 个词”时：minimum_should_match: 2");
        System.out.println("  现象：全文搜索结果太宽 -> 先查 minimum_should_match 是不是被 should 默认稀释");
        System.out.println();
    }

    static void injectionGuard() {
        System.out.println("【DSL 注入防护：模板与参数化】");
        String userQuery = "手机\" } , { \"term\": { \"status\": \"OFFSALE\" } }";
        Map<String, Object> safe = new LinkedHashMap<>();
        safe.put("query_text", userQuery);
        System.out.println("  用户输入：" + userQuery);
        System.out.println("  直接拼 JSON：结构被破坏，还能被塞进额外过滤条件");
        System.out.println("  参数化模板：{\"match\": {\"title\": \"{{q}}\"}}，q 作为参数传入");
        System.out.println("    -> 特殊字符整体作为词参与分词，无法逃逸出结构");
    }

    static List<Long> ids(List<Doc> docs) {
        return docs.stream().map(Doc::id).toList();
    }

    public static void main(String[] args) {
        termVsMatch();
        boolTree();
        minimumShouldMatch();
        injectionGuard();
    }
}
```

**课后练习**
1. 用 bool 组合实现"在售手机壳，价格 30~100，标题至少含 手机 或 壳 中两个词"，写出完整 DSL 并说明每个子句放在哪个上下文。
2. 复现 keyword 字段误用 match 的现象，然后分别用 term 与 wildcard 说明正确写法，并解释为什么 wildcard 前缀 `%x%` 在大索引上依然危险。
3. 把示例中的 matchText 升级为"评分版"：命中词越多分越高、按分排序输出，验证它与 bool should 加分语义一致。
4. 思考题：filter cache 为什么对 term/range 有效而对评分查询无效？这对"过滤条件写在哪里"的设计给出最终答案。

---

### 38-06 相关性评分与 BM25

**知识点**
- **相关性回答"哪条结果排前面"**：没有评分的搜索只是过滤。Lucene 默认用 BM25：得分由**词频 TF**（这篇文档里该词出现几次，有饱和上限——出现 100 次不比 10 次强多少）、**逆文档频率 IDF**（越稀有的词越有区分度："的"贡献为 0，"iPhone"贡献很大）、**字段长度归一**（短字段里命中一个词比长字段命中权重高）三项合成。
- **TF-IDF 是 BM25 的前身**：TF×log(总文档/含词文档)。BM25 在其上加两个可调参数：k1（词频饱和速度，默认 1.2）与 b（字段长度归一强度，默认 0.75），实践中 BM25 的排序明显更稳。
- **多字段评分**：multi_match 的 `best_fields`（默认，取最高分字段）、`most_fields`（各字段分相加，多分析器互补）、`cross_fields`（把多字段当一个大字段，对"姓 名"跨字段短语很关键）。boost 参数放大某字段的权重（标题 boost=3，描述 boost=1）。
- **function_score 做"业务加权"**：相关性只是排序的一半，另一半是业务信号——销量高一点、新上架一点、库存充足一点。`function_score` 在 BM25 分数上乘/加一个函数（field_value_factor、随机、衰减 gauss），是搜索排序从"纯文本相关"走向"业务排序"的正式入口。
- **rescore 二次精排**：第一轮用便宜的方法取 top 100~200，再对这批用短语匹配/更贵函数精排取 top 10 —— 大索引上"既要召回又要排序质量"的标准折衷。
- **explain 是评分的显微镜**：`explain: true` 返回每个 term 的 IDF/TF/字段长度明细，排不对的时候不要猜，直接看公式每一项。
- 易错点：Boost 乱调（×10 之后其他字段等于不存在，排序被单一字段劫持）；把"价格低"塞进 function_score 的加法里（价格影响远大于 BM25，直接变成"按价格排序"）；对长文本字段忘开 `norms:false`（无谓的内存）；期望"改动评分参数立即生效"（要重建或 rescore 验证，并压测排序质量）。

**示例代码**
```java
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RelevanceScore {
    record Doc(long id, String title, int sales) {
    }

    static final List<Doc> DOCS = List.of(
            new Doc(1, "手机壳", 500),
            new Doc(2, "手机壳 手机支架", 120),
            new Doc(3, "透明手机保护壳 手机壳", 80));

    /** BM25 可读版（单项查询）：IDF × 词频项（含字段长度归一） */
    static double bm25(int tf, int df, long totalDocs, int fieldLen, double avgLen,
                       double k1, double b) {
        double idf = Math.log(1 + (totalDocs - df + 0.5) / (df + 0.5));
        double tfTerm = tf * (k1 + 1)
                / (tf + k1 * (1 - b + b * fieldLen / avgLen));
        return idf * tfTerm;
    }

    static void scoreTable() {
        System.out.println("【同一查询词“手机壳”，三篇文档的得分要素】");
        long total = DOCS.size();
        String term = "手机壳";
        int df = 2;   // 文档1、3 含“手机壳”（本例按词项精确计数）
        double avgLen = DOCS.stream().mapToDouble(d -> d.title().length()).average().orElse(1);
        for (Doc d : DOCS) {
            int tf = countOccurrences(d.title(), term);
            int len = d.title().length();
            double score = bm25(tf, df, total, len, avgLen, 1.2, 0.75);
            System.out.printf("  doc%d tf=%d len=%-12d score=%.4f%n", d.id(), tf, len, score);
        }
        System.out.println("  要点：tf 有饱和（重复出现增益递减）；字段越短归一后越占优");
        System.out.println();
    }

    private static int countOccurrences(String text, String term) {
        int c = 0, idx = 0;
        while ((idx = text.indexOf(term, idx)) >= 0) {
            c++;
            idx += term.length();
        }
        return c;
    }

    static void idfSense() {
        System.out.println("【IDF 的直觉：稀有词才有区分度】");
        String[][] terms = {{"的", "df≈全部文档", "≈0.0"}, {"手机", "df=8000/100000", "≈2.5"},
                {"iphone15", "df=300/100000", "≈5.8"}};
        for (String[] t : terms) {
            System.out.printf("  词 %-10s 文档频率 %-14s IDF≈%s%n", t[0], t[1], t[2]);
        }
        System.out.println("  查询分词后：停用词几乎不加分，品牌型号词主导排序");
        System.out.println();
    }

    static void functionScore() {
        System.out.println("【BM25 × 业务信号：function_score 演示】");
        double bm25Weight = 1.0, salesWeight = 0.0005;
        for (Doc d : DOCS) {
            double bm25 = bm25(countOccurrences(d.title(), "手机壳"), df(), DOCS.size(),
                    d.title().length(), avgLen(), 1.2, 0.75);
            double boost = 1.0 + salesWeight * d.sales();
            System.out.printf("  doc%d bm25=%.3f 销量=%d 业务加权=×%.2f -> %.3f%n",
                    d.id(), bm25, d.sales(), boost, bm25 * boost);
        }
        System.out.println("  设计原则：业务因子做乘法且幅度温和，让“相关性”仍是第一排序键");
        System.out.println("  加法陷阱：+sales/1000 会直接淹没 BM25，排序退化为按销量");
    }

    static int df() {
        return 2;
    }

    static double avgLen() {
        return DOCS.stream().mapToDouble(d -> d.title().length()).average().orElse(1);
    }

    public static void main(String[] args) {
        scoreTable();
        idfSense();
        functionScore();
    }
}
```

**课后练习**
1. 手算验证：文档 A（tf=3, len=6）与文档 B（tf=3, len=60）查询同一词，用示例公式算分并解释字段长度归一的方向。
2. 给商品搜索设计一个 function_score：相关性 × 业务加权（销量 + 时效衰减 gauss(now, 30d, 180d)），写出公式并论证权重幅度不会劫持排序。
3. 用 explain 复盘一次"排序不符合直觉"的查询，把每个 term 的 IDF/TF 项摘出来，指出真正导致错序的那一项。
4. 思考题：为什么 rescore 要限定在 top N 上做？如果对全部命中结果精排，代价曲线是什么形状？
