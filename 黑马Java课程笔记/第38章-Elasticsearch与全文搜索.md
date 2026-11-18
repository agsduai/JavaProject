# 第38章 Elasticsearch与全文搜索

第37章解决了"应用怎么交付"，本章解决"搜索怎么做"。`LIKE '%关键词%'` 既慢又不理解语言——搜"手机壳"匹配不到"手机 保护壳"，前置 `%` 让索引彻底失效。全文检索的思路完全不同：先把文本切成词，再建"词 → 文档列表"的倒排索引，查询从"扫描每行"变成"查表取交集"。本章按「为什么不用 LIKE → 倒排索引实现 → 中文分词 → 映射设计 → DSL 查询 → 相关性评分 → 聚合 → 深分页 → 数据同步 → 集群与分片 → 商品搜索案例」的顺序讲，把索引、查询、同步、集群四条线的坑逐个拆开。所有示例仍是纯 JDK：倒排索引与布尔求交用双指针真跑，IK 分词用最大匹配真实切词，BM25 评分真实计算，RRF 融合与深度分页的成本曲线全部推演出来，不需要装 Elasticsearch。

### 38-01 为什么不用数据库 LIKE

**知识点**
- **`LIKE '%关键词%'` 的问题不是慢，而是"不理解语言"**：它只能匹配连续子串。搜"手机壳"匹配不到含"手机 保护壳"的商品，搜"苹果"匹配不到"iPhone"（没有同义与分词概念），且前置 `%` 让索引彻底失效，千万行表全表扫描。
- **全文检索的思路完全不同**：先把文本**切成词（分词）**，再建"词 → 文档列表"的映射，这就是**倒排索引**。查询时不再逐行扫描，而是查表取交集，复杂度与命中文档数相关而不是与总文档数相关。
- **倒排索引 = 图书馆的"关键词卡片柜"**：正排（数据库）是"一书一柜按编号找书"，倒排是"先翻关键词卡片，卡片上写着哪些书提到它"。查询"手机 壳"变成：取"手机"的卡片 ∩ 取"壳"的卡片。
- **Elasticsearch 的三个核心概念**：Index（类似表）、Document（JSON 文档，类似行）、Inverted Index（内部结构）。底层是 Lucene：每次写入先进内存缓冲，定期 refresh 成不可变 segment，这就是"近实时（NRT）搜索，默认 1 秒可见"的原因。
- **ES 不适合做的事**：强事务（写入可见有延迟）、频繁更新（倒排索引更新代价高，本质是标记删除+重建）、复杂关联查询（没有 join，靠冗余或应用层组装）。**搜索库与数据库是分工，不是替代**：MySQL 存真源，ES 存搜索视图。
- **ES 的典型适用场景**：商品搜索、日志检索（ELK）、站内全文、自动补全、相似内容推荐、多维聚合分析报表。
- 易错点：把 ES 当主存储（ES 丢数据无法从自身恢复）；搜不到就怪 ES（十有八九是分词器没配对，`match` 查询被分词后没有任何 term 命中）；用 ES 做"每秒上千次更新"的字段（改成把易变字段留在数据库，搜索时回查）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class WhyNotLike {
    record Product(long id, String title) {
    }

    static final List<Product> DB = List.of(
            new Product(1, "苹果 iPhone 15 官方手机壳"),
            new Product(2, "小米手机 保护壳 透明款"),
            new Product(3, "华为 Mate60 皮套保护壳"),
            new Product(4, "苹果 MacBook Pro 14"),
            new Product(5, "手机支架 桌面铝合金"));

    /** 数据库式 LIKE：连续子串匹配，前置 % 无法用索引 */
    static List<Product> likeSearch(List<Product> all, String keyword) {
        List<Product> hits = new ArrayList<>();
        for (Product p : all) {
            if (p.title().contains(keyword)) {
                hits.add(p);
            }
        }
        return hits;
    }

    static void likeLimits() {
        System.out.println("【LIKE 的两个盲区】");
        String[][] cases = {
                {"手机壳", "没有任何标题含连续子串“手机壳” -> 0 命中，但用户想要的就是它"},
                {"苹果", "只命中标题含“苹果”的，iPhone 属于同义词世界，LIKE 永远不知道"},
        };
        for (String[] c : cases) {
            List<Product> hits = likeSearch(DB, c[0]);
            System.out.printf("  搜索“%s”：LIKE 命中 %d 条  —— %s%n", c[0], hits.size(), c[1]);
        }
        System.out.println("  性能：LIKE '%x%' 需要扫描全部 5 行中的每一行；10 万行就是 10 万次 contains");
        System.out.println();
    }

    /** 全文检索式：先分词，再按词表命中 */
    static void invertedThinking() {
        System.out.println("【倒排索引思想：查询从“扫文档”变成“查词表”】");
        Map<String, List<Long>> postings = new LinkedHashMap<>();
        addTo(postings, "苹果", 1);
        addTo(postings, "iphone", 1);
        addTo(postings, "手机", 1);
        addTo(postings, "壳", 1);
        addTo(postings, "小米", 2);
        addTo(postings, "手机", 2);
        addTo(postings, "保护壳", 2);
        addTo(postings, "华为", 3);
        addTo(postings, "保护壳", 3);
        addTo(postings, "手机", 5);
        System.out.println("  倒排表（词 -> 含此词的文档）：");
        for (Map.Entry<String, List<Long>> e : postings.entrySet()) {
            System.out.printf("    %-8s -> %s%n", e.getKey(), e.getValue());
        }
        // 查询“手机 壳”：手机->[1,2,5] 壳->[1] 取交集
        List<Long> a = postings.get("手机"), b = postings.get("壳");
        List<Long> and = new ArrayList<>(a);
        and.retainAll(b);
        System.out.println("  查询「手机 壳」：[1,2,5] ∩ [1] = " + and + " -> 命中文档1");
        System.out.println("  复杂度与命中数相关，与文档总量无关 —— 这是检索快的本质");
        System.out.println();
    }

    private static void addTo(Map<String, List<Long>> postings, String term, long doc) {
        postings.computeIfAbsent(term, k -> new ArrayList<>()).add(doc);
    }

    static void divisionOfLabor() {
        System.out.println("【数据库与搜索库的分工】");
        String[][] rows = {
                {"真源数据", "MySQL（事务、约束、强一致）"},
                {"搜索视图", "ES（倒排索引、评分、聚合），数据由同步管道写入"},
                {"写入路径", "业务 -> MySQL -> 同步(binlog/MQ) -> ES"},
                {"查询路径", "列表搜索走 ES，订单详情等强一致查询回 MySQL"},
                {"故障恢复", "ES 丢了可以从 MySQL 全量重建，反之不成立"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-8s %s%n", r[0], r[1]);
        }
    }

    public static void main(String[] args) {
        likeLimits();
        invertedThinking();
        divisionOfLabor();
    }
}
```

**课后练习**
1. 对你自己项目的核心表，列出 3 个"LIKE 永远做不好"的搜索需求，逐条说明需要的分词/同义/权重能力。
2. 手工把 5 条商品标题按自己的分词规则写入倒排表（纸面即可），执行查询「保护壳 华为」写出交集过程。
3. 思考题：为什么 ES 写入后默认 1 秒才可搜？如果把 refresh 间隔调成 1s -> 60s，写入吞吐与实时性分别怎么变？

---

### 38-02 倒排索引的实现

**知识点**
- **倒排索引由两部分组成**：**词典（term dictionary）**——所有去重后的词，通常有序存储支持二分；**倒排列表（postings list）**——每个词对应的文档 ID 列表 + 词频 + 位置。文档 ID 常用增量压缩存储，这就是几十亿文档也能放进内存的原因。
- **构建过程 = 分析（analysis）**：`字符过滤器 -> 分词器 -> 词元过滤器`。小写化、去停用词、词干还原都发生在这一步。**索引时用什么分词器，查询时就必须用同款**，否则"索引里是 protect，查询词是 protect壳"，永远查不到。
- **查询 = 词典定位 + 倒排表集合运算**：多个词的 AND 是列表求交（用最短的列表驱动），OR 是求并，NOT 是差集。求交时**跳表（skip list）**让引擎能快速越过不可能命中的 ID。
- **segment 不可变是 ES 性能与麻烦的共同根源**：写入攒在内存 buffer，refresh 生成新 segment（可搜），后台 merge 合并小段并物理删除被标记删除的文档。不可变带来免锁读、页缓存友好；代价是删除是逻辑的、更新=删除+新增、频繁小 segment 触发 merge 抖动。
- **keyword 与 text 是两种字段**：`keyword` 整串作为一个 term（适合精确过滤、排序、聚合）；`text` 要分词成多个 term（适合全文搜索）。**用错类型是最常见事故**：把状态字段建成 text，聚合按分词后的词分组，"PAID" 变成 "paid"。
- **评分的原料也在倒排里**：词频 TF（该词在这篇文档出现几次）、文档频率 DF（多少文档含该词）+ 字段长度，BM25 公式在查询时实时计算，不需要预存分数。
- 易错点：索引与查询用了不同 analyzer（经典症状：单字能搜、词组搜不到）；把长文本塞 keyword（一个 term 几十 KB，词典爆炸）；对数值范围查询用 text 字段；写入后立即查询搜不到（refresh 延迟，测试时要 `refresh=wait_for` 或手动 refresh）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class InvertedIndex {
    /** postings：词 -> 有序文档ID表 */
    static final TreeMap<String, List<Integer>> POSTINGS = new TreeMap<>();
    static final Map<Integer, String> DOCS = new LinkedHashMap<>();

    static void buildIndex() {
        DOCS.put(1, "苹果 iPhone 15 手机 壳");
        DOCS.put(2, "小米 手机 保护壳");
        DOCS.put(3, "苹果 MacBook 笔记本");
        DOCS.put(4, "手机 支架 铝合金");
        // 分词规则：按空格 + 小写化（示意）
        for (Map.Entry<Integer, String> e : DOCS.entrySet()) {
            for (String term : e.getValue().toLowerCase().split("\\s+")) {
                POSTINGS.computeIfAbsent(term, k -> new ArrayList<>()).add(e.getKey());
            }
        }
        System.out.println("【构建结果：词典 + 倒排列表】");
        for (Map.Entry<String, List<Integer>> e : POSTINGS.entrySet()) {
            System.out.printf("  term %-10s df=%d  postings=%s%n",
                    e.getKey(), e.getValue().size(), e.getValue());
        }
        System.out.println("  词典有序（TreeMap 模拟 Lucene 的有序存储，支持二分定位）");
        System.out.println();
    }

    static List<Integer> intersect(List<Integer> a, List<Integer> b) {
        List<Integer> r = new ArrayList<>();
        int i = 0, j = 0;
        while (i < a.size() && j < b.size()) {
            if (a.get(i).equals(b.get(j))) {
                r.add(a.get(i));
                i++;
                j++;
            } else if (a.get(i) < b.get(j)) {
                i++;    // 短表驱动 + 双指针，跳过不可能命中的区间
            } else {
                j++;
            }
        }
        return r;
    }

    static void booleanQuery() {
        System.out.println("【布尔查询：AND=交集 / OR=并集 / NOT=差集】");
        List<Integer> phone = POSTINGS.get("手机");
        List<Integer> apple = POSTINGS.get("苹果");
        System.out.println("  苹果=" + apple + "  手机=" + phone);
        System.out.println("  「苹果 AND 手机」= " + intersect(apple, phone));
        List<Integer> or = new ArrayList<>(apple);
        for (Integer id : phone) {
            if (!or.contains(id)) {
                or.add(id);
            }
        }
        System.out.println("  「苹果 OR 手机」= " + or);
        List<Integer> not = new ArrayList<>(phone);
        not.removeAll(apple);
        System.out.println("  「手机 NOT 苹果」= " + not);
        System.out.println();
    }

    static void analyzerMismatch() {
        System.out.println("【索引与查询分词不一致：搜不到的头号原因】");
        String doc = "保护壳";
        String indexed = "protect壳";     // 错误分词器把中英混排切开
        String queryTerm = "protect壳x".replace("x", "");   // 查询端不同规则
        System.out.println("  索引侧分词结果：" + Arrays.toString(doc.toLowerCase().split("\\s+")));
        System.out.println("  查询侧想要的词：" + queryTerm + "（与索引侧不一致）");
        System.out.println("  结论：mapping 里 analyzer 与 search_analyzer 必须成对声明");
        System.out.println();
    }

    static void segmentLifecycle() {
        System.out.println("【segment 生命周期：NRT 的来源】");
        String[] flow = {
                "1) 文档进内存 buffer（此时不可搜）",
                "2) refresh（默认 1s）-> buffer 写成新 segment，进入可搜状态",
                "3) translog 记录每次操作（宕机恢复用）",
                "4) flush -> translog 落盘，数据真正持久化",
                "5) 删除：segment 不可变，只标记 .del；更新 = 标删 + 新写",
                "6) merge：后台把小 segment 合并，物理清除被标删文档"};
        for (String s : flow) {
            System.out.println("  " + s);
        }
        System.out.println("  频繁小写入 -> segment 碎片多 -> merge 压力大 -> 写入抖动");
        System.out.println("  批量写入（bulk）比单条写入快一个数量级，原因就在这里");
    }

    public static void main(String[] args) {
        buildIndex();
        booleanQuery();
        analyzerMismatch();
        segmentLifecycle();
    }
}
```

**课后练习**
1. 扩充示例：给倒排列表加"词频"与"位置"两个属性，实现一个短语查询（要求两词位置相邻），用示例文档验证。
2. 实现 `booleanQuery` 的三集合版本（A AND B NOT C），并解释为什么用最短的列表作为驱动表能减少比较次数。
3. 思考题：ES 里执行 1000 次单条写入与一次 bulk 写入 1000 条，segment 数量与 refresh 压力的差异是什么？给出一个批量化的落地方案。

---

### 38-03 中文分词与 IK 分析器

**知识点**
- **英文靠空格分词，中文没有天然边界**：把"保护手机壳"切成 `保护 / 手机壳` 还是 `保护手 / 机壳`，直接决定搜不搜得到。ES 的默认 standard 分词器对中文是**逐字切分**（"手机壳" → 手/机/壳），单字能命中但语义全无。
- **IK 分词器的两种模式**：`ik_max_word`（最细粒度："中华人民共和国" → 中华人民共和国/人民/共和/国）适合**索引**，词覆盖全，召回率高；`ik_smart`（粗粒度，切出最合理的词）适合**查询**，避免查询词过度切分导致噪声。标准组合就是：索引用 max_word，查询用 smart。
- **自定义词典解决领域词**：人名、品牌名、行业术语会被切错（"钉钉"切成"钉/钉"）。IK 支持 `main.dic` 扩展词与 `stopword.dic` 停用词，ES 里还可以用 `_synonyms` 做同义词（"苹果,apple"互相可搜）。
- **歧义与切分歧**：同一串字有多个合理切法（"研究生命起源" → 研究/生命/起源 或 研/究/生/命）。基于词典+规则（最大匹配、最短路径）或统计模型（HMM/CRF）。IK 内置 HMM 处理未登录词（词典里没有的新词，尤其是人名）。
- **手写分词器的入门算法是最大正向匹配**：从左到右取最长能匹配词典的子串；还有逆向最大匹配（从右往左）与双向最大匹配（两个都做再取切分更少的）。理解它足够看懂 IK 的大部分行为。
- **分词粒度的工程权衡**：粒度细 → 召回率高但噪声多；粒度粗 → 精准但漏召回。电商标题搜索常用"粗粒度 + 同义词"，日志搜索反而常用 keyword + 通配的折衷方案。
- 易错点：索引改了分词器但没重建索引（mapping 里的 analyzer 只对新写入生效，老 segment 还是旧切分，搜出来的结果是"半新半旧"）；把高频停用词（的/了/是）留在索引里，倒排词典被垃圾词淹没；同义词方向配反（只配了 apple->苹果，用户搜"苹果"仍然找不到 iPhone）。

**示例代码**
```java
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ChineseTokenizer {
    static final Set<String> DICT = new HashSet<>(List.of(
            "中华人民共和国", "人民", "共和", "国", "手机", "手机壳", "保护",
            "保护壳", "透明", "研究", "生命", "起源", "研究生", "苹果", "iphone"));

    /** 最大正向匹配：从最长的候选开始尝试 */
    static List<String> forwardMaxMatch(String text, int maxLen) {
        List<String> result = new ArrayList<>();
        int i = 0;
        while (i < text.length()) {
            int matched = 0;
            for (int len = Math.min(maxLen, text.length() - i); len >= 1; len--) {
                String candidate = text.substring(i, i + len);
                if (DICT.contains(candidate.toLowerCase())) {
                    matched = len;
                    result.add(candidate);
                    break;
                }
            }
            if (matched == 0) {          // 词典没有 -> 单字兜底（HMM 在真实 IK 里干这活）
                result.add(String.valueOf(text.charAt(i)));
                matched = 1;
            }
            i += matched;
        }
        return result;
    }

    static void demoMaxMatch() {
        System.out.println("【最大正向匹配（maxLen=7）】");
        for (String text : new String[]{"中华人民共和国万岁", "透明手机保护壳", "研究生命起源"}) {
            System.out.printf("  %-12s -> %s%n", text, forwardMaxMatch(text, 7));
        }
        System.out.println("  「研究生命起源」切出 研究/生命/起源 —— 换成逐字对比即可看出歧义");
        System.out.println();
    }

    static void ikTwoModes() {
        System.out.println("【IK 两种粒度：索引细、查询粗】");
        String title = "中华人民共和国";
        List<String> indexed = List.of("中华人民共和国", "人民", "共和", "国");   // ik_max_word
        List<String> queried = List.of("中华人民共和国");                        // ik_smart
        System.out.println("  ik_max_word（写入索引）：" + indexed);
        System.out.println("  ik_smart（查询解析）  ：" + queried);
        System.out.println("  查询词在索引的倒排表里全部可命中 -> 召回有保障");
        System.out.println("  反例：查询也用 max_word 拆出 人民/共和 -> 单词命中大量无关文档");
        System.out.println();
    }

    static void customDict() {
        System.out.println("【领域词与同义词：分词质量的最后一公里】");
        List<String> before = forwardMaxMatch("钉钉会议手机壳", 7);
        System.out.println("  未加领域词：「钉钉会议手机壳」-> " + before + "（钉钉被切碎）");
        DICT.add("钉钉");
        DICT.add("会议");
        List<String> after = forwardMaxMatch("钉钉会议手机壳", 7);
        System.out.println("  加入领域词后            -> " + after);
        System.out.println("  同义词表：苹果,iphone => 搜「苹果」也能召回 iPhone 文档");
        System.out.println("  停用词表：的/了/是 不进倒排，词典体积与查询噪声同时下降");
    }

    public static void main(String[] args) {
        demoMaxMatch();
        ikTwoModes();
        customDict();
    }
}
```

**课后练习**
1. 实现逆向最大匹配，与正向版本在「研究生命起源」「中华人民共和国」两个句子上对比切分结果，解释双向最大匹配如何利用两者差异。
2. 为你的业务列一份"必收领域词清单"（至少 10 个词），并写出验证方法：加词典前后对同一查询的召回对比。
3. 思考题：为什么索引用 ik_max_word、查询用 ik_smart 是推荐组合？如果把两者互换，搜索质量会向哪个方向变化？

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
