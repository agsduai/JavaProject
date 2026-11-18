/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：27-05
 * 课名：分页查询：参数、排序与总数
 * ------------------------------------------------------------
 * 知识点：
 *   - 分页三要素：`page`（页码，从 1 开始）、`size`（每页条数，必须有上限）、`sort`（排序字段 + 方向）。
 *   - SQL 本质：`LIMIT size OFFSET (page-1)*size`。深分页（`page=10000`）会扫描大量行，优化手段是"游标分页"（`WHERE id > lastId LIMIT size`）。
 *   - `size` 不设上限的后果：前端传 `size=1000000`，一次查全表，内存和带宽直接打满，等于给自己做了个 DoS。
 *   - 排序字段必须白名单：`ORDER BY ${sort}` 拼接字符串就是注入点，`sort=id;DROP TABLE item` 直接删库。正确做法是映射 `"price" -> "price_cent"`。
 *   - 返回结构：`{list, total, page, size, pages}`。`total` 单独查一次 `COUNT(*)`，或者用"查 size+1 条判断有没有下一页"省掉 count。
 *   - 页码越界：`page` 超过总页数返回空列表而不是报错；`page < 1` 归一化成 1。
 *   - 易错：`OFFSET` 从 0 开始但页码从 1 开始，少减 1 导致第一页数据丢失；`total=0` 时算 `pages` 除零。
 * ============================================================
 */

package com.heima.ch27;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class PageQuery {
    /** 通用分页结果 */
    static class Page<T> {
        List<T> list;
        long total;
        int page;
        int size;
        int pages;

        Page(List<T> list, long total, int page, int size) {
            this.list = list;
            this.total = total;
            this.page = page;
            this.size = size;
            this.pages = size == 0 ? 0 : (int) ((total + size - 1) / size);
        }

        @Override
        public String toString() {
            return "Page{page=" + page + ", size=" + size + ", total=" + total
                    + ", pages=" + pages + ", list=" + list + "}";
        }
    }

    /** 排序白名单：前端传的 key -> 数据库列名 */
    static final Map<String, String> SORT_WHITELIST = new LinkedHashMap<>();

    static {
        SORT_WHITELIST.put("id", "id");
        SORT_WHITELIST.put("price", "price_cent");
        SORT_WHITELIST.put("stock", "stock");
        SORT_WHITELIST.put("createdAt", "created_at");
    }

    /** 模拟商品表 */
    static final List<Map<String, Object>> TABLE = new ArrayList<>();

    public static void main(String[] args) {
        for (int i = 1; i <= 23; i++) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", (long) i);
            row.put("name", "商品" + i);
            row.put("price_cent", 1000 * ((i * 37) % 50 + 1));
            row.put("stock", (i * 13) % 100);
            row.put("created_at", 1700000000L + i * 3600L);
            TABLE.add(row);
        }
        System.out.println("表里共 " + TABLE.size() + " 条");

        System.out.println();
        System.out.println("=== 第 1 页，每页 5 条 ===");
        System.out.println("  " + query(1, 5, "id", true));

        System.out.println();
        System.out.println("=== 第 3 页，按价格降序 ===");
        System.out.println("  " + query(3, 5, "price", false));

        System.out.println();
        System.out.println("=== 最后一页不满 ===");
        System.out.println("  " + query(5, 5, "id", true));

        System.out.println();
        System.out.println("=== 页码越界：返回空列表，不报错 ===");
        System.out.println("  " + query(99, 5, "id", true));

        System.out.println();
        System.out.println("=== page < 1 归一化成 1 ===");
        System.out.println("  " + query(0, 5, "id", true));

        System.out.println();
        System.out.println("=== size 超上限被截断到 100 ===");
        Page<Map<String, Object>> capped = query(1, 1000000, "id", true);
        System.out.println("  实际 size=" + capped.size + "，返回 " + capped.list.size() + " 条");

        System.out.println();
        System.out.println("=== 非法排序字段：拒绝，不拼接 ===");
        try {
            System.out.println("  " + query(1, 5, "id; DROP TABLE item", true));
        } catch (IllegalArgumentException e) {
            System.out.println("  400 " + e.getMessage() + "（没有拼进 SQL）");
        }

        System.out.println();
        System.out.println("=== 空表：pages 不能除零 ===");
        TABLE.clear();
        System.out.println("  " + query(1, 5, "id", true));

        System.out.println();
        System.out.println("=== 深分页的代价 ===");
        System.out.println("  LIMIT 5 OFFSET 0      -> 读 5 行");
        System.out.println("  LIMIT 5 OFFSET 50000  -> 先读 50005 行再丢掉前 50000 行");
        System.out.println("  游标分页: WHERE id > 50000 ORDER BY id LIMIT 5 -> 走主键索引，只读 5 行");
    }

    static Page<Map<String, Object>> query(int page, int size, String sortKey, boolean asc) {
        String column = SORT_WHITELIST.get(sortKey);
        if (column == null) {
            throw new IllegalArgumentException("不支持的排序字段: " + sortKey
                    + "，可选: " + SORT_WHITELIST.keySet());
        }
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);

        List<Map<String, Object>> sorted = new ArrayList<>(TABLE);
        sorted.sort((a, b) -> {
            long va = ((Number) a.get(column)).longValue();
            long vb = ((Number) b.get(column)).longValue();
            int cmp = Long.compare(va, vb);
            return asc ? cmp : -cmp;
        });

        int from = (safePage - 1) * safeSize;
        int to = Math.min(from + safeSize, sorted.size());
        List<Map<String, Object>> slice = from >= sorted.size()
                ? new ArrayList<>()
                : new ArrayList<>(sorted.subList(from, to));

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> r : slice) {
            rows.add(new LinkedHashMap<>(r));
        }
        return new Page<>(rows, sorted.size(), safePage, safeSize);
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. `size` 不设上限会带来哪些问题？如果业务确实需要导出 10 万条，应该怎么设计接口？
 * 2. 用游标分页（`WHERE id > lastId`）实现"下一页"，说明它为什么不能跳页，以及什么场景下必须用它。
 * 3. 为什么 `ORDER BY ${sort}` 有注入风险，而 `ORDER BY id` 拼接 `LIMIT ?,?` 用占位符就没有？从预编译的原理说明。
 * ============================================================
 */
