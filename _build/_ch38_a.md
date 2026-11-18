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
