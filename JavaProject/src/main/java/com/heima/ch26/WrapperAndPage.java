/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：26-06
 * 课名：条件构造器与分页
 * ------------------------------------------------------------
 * 知识点：
 *   - `QueryWrapper` / `LambdaQueryWrapper`：用 Java 代码拼 `WHERE` 条件，避免手写 SQL 字符串拼接。
 *   - 常用方法：`eq`（=）、`ne`（<>）、`gt/ge/lt/le`（> >= < <=）、`like`（%x%）、`likeRight`（x%）、`in`、`between`、`isNull`、`orderByDesc`、`last`（拼在最后，慎用）。
 *   - 条件生效控制：`eq(condition, column, value)` 第一个参数为 `false` 时该条件不拼，用于"参数为空就不查这个字段"。
 *   - `LambdaQueryWrapper` 用方法引用 `User::getAge` 代替字符串列名，重构时不会写错、编译器能检查，**推荐优先使用**。
 *   - 分页：`Page<User> page = new Page<>(1, 10)` + `mapper.selectPage(page, wrapper)`，需要注册 `MybatisPlusInterceptor` + `PaginationInnerInterceptor`，否则分页不生效（返回全量）。
 *   - `Page` 的字段：`records`（当前页数据）、`total`（总条数）、`pages`（总页数）、`current`、`size`、`hasNext`。
 *   - 分页插件原理：拦截 SQL 后自动改写成 `LIMIT offset, size` 并额外执行一条 `COUNT`。
 *   - 易错：忘配分页插件，`selectPage` 返回全部数据但 `total` 为 0；`orderByDesc` 拼了用户可控的字段名会有 SQL 注入风险（列名不能参数化）。
 * ============================================================
 */

package com.heima.ch26;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

// ==================== 示例代码 ====================

public class WrapperAndPage {
    static final List<User> TABLE = new ArrayList<>();

    public static void main(String[] args) {
        init();

        System.out.println("=== QueryWrapper 拼条件 ===");
        QueryWrapper w1 = new QueryWrapper()
                .eq("age", 20)
                .orderByDesc("id");
        System.out.println("  " + w1.toSql());
        System.out.println("  命中：" + query(w1));

        QueryWrapper w2 = new QueryWrapper()
                .ge("age", 18)
                .le("age", 30)
                .like("user_name", "张");
        System.out.println("  " + w2.toSql());
        System.out.println("  命中：" + query(w2));

        System.out.println();
        System.out.println("=== 条件为空就不拼（eq 的第一个参数）===");
        String keyword = null;
        QueryWrapper w3 = new QueryWrapper()
                .eq("deleted", 0)
                .eq(keyword != null, "user_name", keyword);
        System.out.println("  keyword 为 null：" + w3.toSql());

        keyword = "张";
        QueryWrapper w4 = new QueryWrapper()
                .eq("deleted", 0)
                .eq(keyword != null, "user_name", keyword);
        System.out.println("  keyword 有值：" + w4.toSql());

        System.out.println();
        System.out.println("=== 分页 ===");
        Page page = new Page(2, 3);      // 第 2 页，每页 3 条
        Page result = selectPage(page, new QueryWrapper().orderByDesc("id"));
        System.out.println("  total=" + result.total + " pages=" + result.pages
                + " current=" + result.current + " size=" + result.size
                + " hasNext=" + result.hasNext);
        System.out.println("  当前页数据：" + result.records);

        System.out.println();
        System.out.println("=== 分页插件必须注册 ===");
        System.out.println("""
                @Bean
                public MybatisPlusInterceptor mybatisPlusInterceptor() {
                    MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
                    interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
                    return interceptor;
                }
                """);
        System.out.println("  不注册 -> selectPage 返回全量数据，total 恒为 0");
    }

    static void init() {
        String[][] data = {
                {"1", "张三", "18"}, {"2", "李四", "20"}, {"3", "王五", "22"},
                {"4", "张小明", "25"}, {"5", "赵六", "30"}, {"6", "孙七", "35"},
                {"7", "周八", "20"}, {"8", "吴九", "28"}};
        for (String[] d : data) {
            TABLE.add(new User(Long.parseLong(d[0]), d[1], Integer.parseInt(d[2])));
        }
    }

    static List<User> query(QueryWrapper w) {
        return TABLE.stream()
                .filter(w::test)
                .collect(Collectors.toList());
    }

    static Page selectPage(Page page, QueryWrapper w) {
        List<User> all = query(w);
        page.total = all.size();
        page.pages = (all.size() + page.size - 1) / page.size;
        int from = (int) Math.min((page.current - 1) * page.size, all.size());
        int to = (int) Math.min((long) from + page.size, all.size());
        page.records = new ArrayList<>(all.subList(from, to));
        page.hasNext = page.current < page.pages;
        return page;
    }

    static class User {
        Long id;
        String userName;
        Integer age;

        User(Long id, String userName, Integer age) {
            this.id = id;
            this.userName = userName;
            this.age = age;
        }

        @Override
        public String toString() {
            return id + ":" + userName + "(" + age + ")";
        }
    }

    /** 极简 QueryWrapper：只演示条件拼装与匹配 */
    static class QueryWrapper {
        private final List<Cond> conds = new ArrayList<>();
        private String orderBy;
        private boolean desc;

        QueryWrapper eq(String col, Object v) {
            conds.add(new Cond(col, "=", v));
            return this;
        }

        QueryWrapper eq(boolean enable, String col, Object v) {
            if (enable) {
                conds.add(new Cond(col, "=", v));
            }
            return this;
        }

        QueryWrapper ge(String col, Object v) {
            conds.add(new Cond(col, ">=", v));
            return this;
        }

        QueryWrapper le(String col, Object v) {
            conds.add(new Cond(col, "<=", v));
            return this;
        }

        QueryWrapper like(String col, String v) {
            conds.add(new Cond(col, "LIKE", "%" + v + "%"));
            return this;
        }

        QueryWrapper orderByDesc(String col) {
            this.orderBy = col;
            this.desc = true;
            return this;
        }

        String toSql() {
            String where = conds.stream()
                    .map(c -> c.col + " " + c.op + " " + fmt(c.val))
                    .collect(Collectors.joining(" AND "));
            String sql = "SELECT * FROM t_user WHERE " + where;
            if (orderBy != null) {
                sql += " ORDER BY " + orderBy + (desc ? " DESC" : " ASC");
            }
            return sql;
        }

        boolean test(User u) {
            Map<String, Function<User, Object>> getters = new LinkedHashMap<>();
            getters.put("id", x -> x.id);
            getters.put("user_name", x -> x.userName);
            getters.put("age", x -> x.age);
            getters.put("deleted", x -> 0);
            for (Cond c : conds) {
                Object actual = getters.get(c.col).apply(u);
                if (!match(actual, c)) {
                    return false;
                }
            }
            return true;
        }

        static boolean match(Object actual, Cond c) {
            if ("LIKE".equals(c.op)) {
                String needle = String.valueOf(c.val).replace("%", "");
                return String.valueOf(actual).contains(needle);
            }
            int cmp = Long.compare(
                    Long.parseLong(String.valueOf(actual)),
                    Long.parseLong(String.valueOf(c.val)));
            switch (c.op) {
                case "=":
                    return cmp == 0;
                case ">=":
                    return cmp >= 0;
                case "<=":
                    return cmp <= 0;
                default:
                    return false;
            }
        }

        static String fmt(Object v) {
            return v instanceof String ? "'" + v + "'" : String.valueOf(v);
        }
    }

    static class Cond {
        final String col;
        final String op;
        final Object val;

        Cond(String col, String op, Object val) {
            this.col = col;
            this.op = op;
            this.val = val;
        }
    }

    static class Page {
        long total;
        long pages;
        long current;
        long size;
        boolean hasNext;
        List<User> records = new ArrayList<>();

        Page(long current, long size) {
            this.current = current;
            this.size = size;
        }
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 用 `LambdaQueryWrapper` 写一个查询：年龄 18~30、用户名以"张"开头、按 id 倒序。
 * 2. 为什么推荐 `LambdaQueryWrapper` 而不是字符串列名的 `QueryWrapper`？举一个重构后字符串方案出错的例子。
 * 3. `selectPage` 返回 `total=0` 但 `records` 有数据，最可能的原因是什么？怎么排查？
 * ============================================================
 */
