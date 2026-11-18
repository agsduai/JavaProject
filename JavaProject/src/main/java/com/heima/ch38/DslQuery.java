/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：38-05
 * 课名：DSL 查询：term、match 与 bool 组合
 * ------------------------------------------------------------
 * 知识点：
 *   - **查询的执行模型**：Query DSL 是一个 JSON 树，叶子是 `term/match/range`，枝干是 `bool` 的四种组合（must=AND 计分、filter=AND 不计分、should=OR 加分、must_not=NOT 不计分）。理解这棵树就理解了 90% 的 ES 查询。
 *   - **term 与 match 的本质区别**：`term` 拿查询词**原样**去倒排表精确匹配（不对查询词分词），适合 keyword/状态/ID；`match` 先对查询词**分词**，再对各词 OR（默认 should）/AND 组合，适合 text 全文。**对 keyword 字段用 match 会命中 0**（keyword 的 term 就是整串，你的长查询词当然查不到）。
 *   - **filter 上下文是性能关键**：filter 不算相关性、结果可被节点级缓存（filter cache），重复过滤条件几乎零成本。**能用 filter 就不要放 must**——"status=PAID AND category=phone" 这类条件放进 must 纯属浪费评分计算。
 *   - **should 的两副面孔**：bool 里只有 should（无 must/filter）→ 至少匹配 1 个（minimum_should_match 默认 1）；有 must/filter 时 → should 变成加分项（匹配了得分更高，不匹配也能命中）。想强制"至少匹配 N 个词"要显式写 `minimum_should_match`。
 *   - **range 与日期数学**：`gte/lte` 支持 `now-7d/d` 这类表达式；数值范围走 point 结构（BKD 树），与倒排是两套机制。`exists` 查"字段存在"，注意 null 值默认不索引。
 *   - **查询模板化**：业务代码里拼 DSL 的标准做法是参数模板（占位符 + 参数列表），防止"用户输入直接拼进 DSL JSON"的注入与语法破坏，等价于 SQL 的参数化。
 *   - 易错点：把用户输入直接拼进 query_string（`AND` `OR` 这些操作符语法可以被人构造）；match 一个 100 字长句导致分词出 50 个 term、should 全 OR 命中一堆弱相关结果（应该限词数或用 match_phrase）；must 里塞几十个 filter 条件让查询变成 CPU 重灾区。
 * ============================================================
 */

package com.heima.ch38;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

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

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 用 bool 组合实现"在售手机壳，价格 30~100，标题至少含 手机 或 壳 中两个词"，写出完整 DSL 并说明每个子句放在哪个上下文。
 * 2. 复现 keyword 字段误用 match 的现象，然后分别用 term 与 wildcard 说明正确写法，并解释为什么 wildcard 前缀 `%x%` 在大索引上依然危险。
 * 3. 把示例中的 matchText 升级为"评分版"：命中词越多分越高、按分排序输出，验证它与 bool should 加分语义一致。
 * 4. 思考题：filter cache 为什么对 term/range 有效而对评分查询无效？这对"过滤条件写在哪里"的设计给出最终答案。
 * ============================================================
 */
