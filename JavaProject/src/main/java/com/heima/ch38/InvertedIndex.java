/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：38-02
 * 课名：倒排索引的实现
 * ------------------------------------------------------------
 * 知识点：
 *   - **倒排索引由两部分组成**：**词典（term dictionary）**——所有去重后的词，通常有序存储支持二分；**倒排列表（postings list）**——每个词对应的文档 ID 列表 + 词频 + 位置。文档 ID 常用增量压缩存储，这就是几十亿文档也能放进内存的原因。
 *   - **构建过程 = 分析（analysis）**：`字符过滤器 -> 分词器 -> 词元过滤器`。小写化、去停用词、词干还原都发生在这一步。**索引时用什么分词器，查询时就必须用同款**，否则"索引里是 protect，查询词是 protect壳"，永远查不到。
 *   - **查询 = 词典定位 + 倒排表集合运算**：多个词的 AND 是列表求交（用最短的列表驱动），OR 是求并，NOT 是差集。求交时**跳表（skip list）**让引擎能快速越过不可能命中的 ID。
 *   - **segment 不可变是 ES 性能与麻烦的共同根源**：写入攒在内存 buffer，refresh 生成新 segment（可搜），后台 merge 合并小段并物理删除被标记删除的文档。不可变带来免锁读、页缓存友好；代价是删除是逻辑的、更新=删除+新增、频繁小 segment 触发 merge 抖动。
 *   - **keyword 与 text 是两种字段**：`keyword` 整串作为一个 term（适合精确过滤、排序、聚合）；`text` 要分词成多个 term（适合全文搜索）。**用错类型是最常见事故**：把状态字段建成 text，聚合按分词后的词分组，"PAID" 变成 "paid"。
 *   - **评分的原料也在倒排里**：词频 TF（该词在这篇文档出现几次）、文档频率 DF（多少文档含该词）+ 字段长度，BM25 公式在查询时实时计算，不需要预存分数。
 *   - 易错点：索引与查询用了不同 analyzer（经典症状：单字能搜、词组搜不到）；把长文本塞 keyword（一个 term 几十 KB，词典爆炸）；对数值范围查询用 text 字段；写入后立即查询搜不到（refresh 延迟，测试时要 `refresh=wait_for` 或手动 refresh）。
 * ============================================================
 */

package com.heima.ch38;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

// ==================== 示例代码 ====================

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

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 扩充示例：给倒排列表加"词频"与"位置"两个属性，实现一个短语查询（要求两词位置相邻），用示例文档验证。
 * 2. 实现 `booleanQuery` 的三集合版本（A AND B NOT C），并解释为什么用最短的列表作为驱动表能减少比较次数。
 * 3. 思考题：ES 里执行 1000 次单条写入与一次 bulk 写入 1000 条，segment 数量与 refresh 压力的差异是什么？给出一个批量化的落地方案。
 * ============================================================
 */
