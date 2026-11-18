/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：23-06
 * 课名：动态SQL
 * ------------------------------------------------------------
 * 知识点：
 *   - 动态 SQL 让一条 SQL 应对多组条件，避免为每种组合写一个方法。
 *   - `<if test="条件">` 条件拼接；`test` 里写 OGNL 表达式，判断字符串非空用 `name != null and name != ''`。
 *   - `<where>` 自动加 `WHERE`，并**去掉开头多余的 `AND/OR`**；`<set>` 自动加 `SET` 并去掉末尾多余的逗号。
 *   - `<foreach>` 遍历集合，常用于 `IN (...)` 和批量插入；属性 `collection/item/separator/open/close`。
 *   - `<choose>/<when>/<otherwise>` 相当于 `switch-case`，只会命中一个分支。
 *   - `<sql>` + `<include>` 抽取公共片段，如公共列名列表。
 *   - 易错：`WHERE 1=1` 是老写法，用 `<where>` 更干净；`<foreach>` 的 `collection` 名要和 `@Param` 一致。
 * ============================================================
 */

package com.heima.ch23;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

// ==================== 示例代码 ====================

public class DynamicSqlBuilder {
    public static void main(String[] args) {
        // 1. <if> + <where>：按需拼接条件
        System.out.println("=== 条件查询 ===");
        System.out.println(selectSql("张三", null, null));
        System.out.println(selectSql(null, 18, null));
        System.out.println(selectSql("张三", 18, 3));
        System.out.println(selectSql(null, null, null));
        System.out.println();

        // 2. <set>：动态更新，自动去掉多余逗号
        System.out.println("=== 动态更新 ===");
        System.out.println(updateSql("李四", null, null));
        System.out.println(updateSql("李四", 22, null));
        System.out.println(updateSql(null, null, 5));
        System.out.println();

        // 3. <foreach>：IN 查询
        System.out.println("=== IN 查询 ===");
        System.out.println(inSql(Arrays.asList(1, 2, 3)));
        System.out.println(inSql(new ArrayList<>()));
        System.out.println();

        // 4. <foreach>：批量插入
        System.out.println("=== 批量插入 ===");
        System.out.println(batchInsert(Arrays.asList("张三", "李四", "王五")));
        System.out.println();

        // 5. <choose>：只命中一个分支
        System.out.println("=== choose 分支 ===");
        System.out.println(chooseSql("id", "1"));
        System.out.println(chooseSql("name", "张三"));
        System.out.println(chooseSql(null, null));
    }

    // 模拟 <where><if>...</if></where>
    private static String selectSql(String name, Integer minAge, Integer classId) {
        StringBuilder sb = new StringBuilder("SELECT id, name, age, class_id FROM student");
        List<String> conds = new ArrayList<>();
        if (name != null && !name.isEmpty()) {
            conds.add("name LIKE CONCAT('%', '" + name + "', '%')");
        }
        if (minAge != null) {
            conds.add("age >= " + minAge);
        }
        if (classId != null) {
            conds.add("class_id = " + classId);
        }
        if (conds.isEmpty()) {
            return sb.toString();
        }
        // <where> 的职责：加 WHERE + 去掉第一个 AND
        return sb + " WHERE " + String.join(" AND ", conds);
    }

    // 模拟 <set><if>...</if></set>
    private static String updateSql(String name, Integer age, Integer classId) {
        List<String> sets = new ArrayList<>();
        if (name != null && !name.isEmpty()) {
            sets.add("name = '" + name + "'");
        }
        if (age != null) {
            sets.add("age = " + age);
        }
        if (classId != null) {
            sets.add("class_id = " + classId);
        }
        if (sets.isEmpty()) {
            return "UPDATE student  -- 没有要更新的字段，MyBatis 会生成非法 SQL 并抛错";
        }
        // <set> 的职责：加 SET + 去掉末尾多余逗号
        return "UPDATE student SET " + String.join(", ", sets) + " WHERE id = ?";
    }

    // 模拟 <foreach collection="ids" item="id" open="(" separator="," close=")">
    private static String inSql(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return "SELECT * FROM student WHERE 1 = 0  -- 空集合必须短路，否则 IN () 是语法错误";
        }
        StringJoinerLike j = new StringJoinerLike("(", ")", ", ");
        for (Integer id : ids) {
            j.add(String.valueOf(id));
        }
        return "SELECT * FROM student WHERE id IN " + j;
    }

    // 模拟批量插入
    private static String batchInsert(List<String> names) {
        StringBuilder sb = new StringBuilder("INSERT INTO student(name, age) VALUES ");
        List<String> rows = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) {
            rows.add("(#{list[" + i + "].name}, #{list[" + i + "].age})");
        }
        return sb + String.join(", ", rows) + "   -- 共 " + names.size() + " 行";
    }

    // 模拟 <choose><when>...<otherwise>
    private static String chooseSql(String by, String value) {
        if ("id".equals(by)) {
            return "SELECT * FROM student WHERE id = " + value;
        } else if ("name".equals(by)) {
            return "SELECT * FROM student WHERE name = '" + value + "'";
        }
        return "SELECT * FROM student ORDER BY id DESC LIMIT 10  -- otherwise 分支";
    }

    // 极简 join 辅助
    static class StringJoinerLike {
        private final String open;
        private final String close;
        private final String sep;
        private final List<String> items = new ArrayList<>();

        StringJoinerLike(String open, String close, String sep) {
            this.open = open;
            this.close = close;
            this.sep = sep;
        }

        void add(String s) {
            items.add(s);
        }

        @Override
        public String toString() {
            return open + String.join(sep, items) + close;
        }
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 写一个"多条件 + 可选排序 + 分页"的动态查询，要求所有分支都能正确生成 SQL。
 * 2. 用 `<foreach>` 实现 `IN` 查询，并处理空集合的情况（提示：`<if test="ids != null and ids.size() > 0">`）。
 * 3. 说明 `<where>` 和 `<set>` 各自帮你处理了哪个容易写错的细节。
 * ============================================================
 */
