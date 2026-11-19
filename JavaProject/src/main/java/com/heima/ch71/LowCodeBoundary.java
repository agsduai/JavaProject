/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：71-09
 * 课名：性能与安全边界
 * ------------------------------------------------------------
 * 知识点：
 *   - **低代码的性能风险**：**元数据解析开销**（每次请求解析 JSON）、**动态 SQL 拼接**（查询条件拼装，易全表扫）、**前端渲染负担**（几十个字段的表单）、**N+1 查询**（引用字段逐个查）——**平台层必须做缓存与批量化**（元数据缓存、查询批量化）。
 *   - **动态 SQL 的安全红线**：**表名/字段名/排序方向必须白名单**（39-02 的标识符纪律）、**值必须参数化**（36-02）——**低代码平台是 SQL 注入的重灾区**（因为它在拼 SQL）。
 *   - **脚本扩展的安全**：沙箱（资源/网络/文件限制 66-10）、超时（执行时长上限）、白名单 API（只能调指定函数）——**不允许任意代码执行**。
 *   - **平台保护**：单表单字段数上限、单租户规则数上限、查询超时、结果集大小限制（44/69 章的资源保护）——**防止"配置导致的事故"**（55-08 噪声邻居的低代码版）。
 *   - 与 36/39 章的关系：动态 SQL 的注入防护、索引与查询优化——**低代码没改变这些底层规则，只是把写 SQL 的人从开发者变成了配置者**。
 *   - 易错点：拼 SQL 用字符串替换（注入）；元数据每次解析（性能差）；引用字段逐个查（N+1）；无限流无超时（复杂配置拖垮平台）。
 * ============================================================
 */

package com.heima.ch71;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class LowCodeBoundary {
    /** 白名单：允许的字段与排序方向 */
    static final List<String> ALLOWED_FIELDS = List.of(
            "title", "amount", "apply_date", "status", "created_at");

    /** 安全的动态查询构建：标识符白名单 + 值参数化 */
    static Map<String, Object> buildQuery(Map<String, String> filters,
                                          String orderBy, String direction) {
        Map<String, Object> query = new LinkedHashMap<>();
        List<String> whereParts = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        for (Map.Entry<String, String> e : filters.entrySet()) {
            if (!ALLOWED_FIELDS.contains(e.getKey())) {
                System.out.println("  拒绝：字段 " + e.getKey() + " 不在白名单（防注入/防越权）");
                return Map.of("error", "invalid_field");
            }
            whereParts.add(e.getKey() + " = ?");        // 标识符白名单 + 值占位
            params.add(e.getValue());
        }
        if (!ALLOWED_FIELDS.contains(orderBy)) {
            System.out.println("  拒绝：排序字段 " + orderBy + " 不在白名单");
            return Map.of("error", "invalid_order");
        }
        if (!List.of("asc", "desc").contains(direction.toLowerCase())) {
            System.out.println("  拒绝：排序方向非法");
            return Map.of("error", "invalid_direction");
        }
        query.put("sql", "SELECT * FROM lc_expense WHERE "
                + String.join(" AND ", whereParts)
                + " ORDER BY " + orderBy + " " + direction.toUpperCase());
        query.put("params", params);
        return query;
    }

    /** 元数据缓存：避免每次请求解析 */
    static final Map<String, String> metaCache = new LinkedHashMap<>();
    static int parseCount = 0;

    static String getMeta(String formId) {
        String cached = metaCache.get(formId);
        if (cached != null) {
            return cached + "（缓存）";
        }
        parseCount++;
        String parsed = "form:" + formId + " with 12 fields";
        metaCache.put(formId, parsed);
        return parsed + "（首次解析）";
    }

    /** 引用字段批量加载：避免 N+1 */
    static Map<Long, String> batchLoadUsers(List<Long> ids) {
        Map<Long, String> out = new LinkedHashMap<>();
        ids.forEach(id -> out.put(id, "user-" + id));
        System.out.printf("  批量加载 %d 个用户：1 次查询（而非 %d 次）%n", ids.size(), ids.size());
        return out;
    }

    static void limits() {
        System.out.println("【平台保护阈值】");
        Map<String, String> limits = new LinkedHashMap<>();
        limits.put("单表单字段数", "≤ 200（超出拆分子表单）");
        limits.put("单租户规则数", "≤ 500（超出需评审）");
        limits.put("查询超时", "3s（超时终止并提示）");
        limits.put("结果集上限", "1000 行（超出强制分页）");
        limits.put("脚本执行", "500ms + 沙箱（66-10）");
        limits.forEach((k, v) -> System.out.printf("  %-12s %s%n", k, v));
    }

    public static void main(String[] args) {
        System.out.println("【安全动态查询】");
        Map<String, String> ok = new LinkedHashMap<>();
        ok.put("status", "APPROVED");
        ok.put("amount", "1200");
        System.out.println("  正常：" + buildQuery(ok, "created_at", "desc"));

        System.out.println();
        Map<String, String> bad = new LinkedHashMap<>();
        bad.put("status", "APPROVED");
        bad.put("1=1 OR title", "x");
        System.out.println("  恶意：" + buildQuery(bad, "created_at", "desc"));

        System.out.println();
        System.out.println("【排序字段注入尝试】");
        System.out.println("  " + buildQuery(ok, "created_at; DROP TABLE lc_expense--", "desc"));

        System.out.println();
        System.out.println("【元数据缓存】");
        System.out.println("  " + getMeta("expense"));
        System.out.println("  " + getMeta("expense"));
        System.out.println("  解析次数：" + parseCount);

        System.out.println();
        System.out.println("【N+1 批量化】");
        batchLoadUsers(List.of(1L, 2L, 3L, 4L, 5L));

        System.out.println();
        limits();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现安全动态查询：字段白名单 + 值参数化 + 排序白名单，用 4 个请求（含 2 个恶意）验证拦截。
 * 2. 实现元数据缓存与失效（配置更新时清缓存），量化"解析次数"下降。
 * 3. 思考题：为什么"低代码平台是 SQL 注入重灾区"？从"平台在拼 SQL"与"配置者安全素养参差"分析。
 * ============================================================
 */
