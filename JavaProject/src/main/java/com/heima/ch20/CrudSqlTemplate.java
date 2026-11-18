/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：20-03
 * 课名：数据的增删改
 * ------------------------------------------------------------
 * 知识点：
 *   - 插入：`insert into 表名 (字段1, 字段2) values (值1, 值2);` 或省略字段名（值必须按建表顺序且全部给出）。
 *   - 批量插入：`insert into 表名 (字段...) values (...), (...), (...);` 一条语句插入多行效率更高。
 *   - 更新：`update 表名 set 字段1=值1, 字段2=值2 where 条件;` 不写 `where` 会更新全表。
 *   - 删除：`delete from 表名 where 条件;` 不写 `where` 会删除全表数据（表结构还在）。
 *   - `truncate table 表名` 清空表，比 `delete` 快且自增主键归零，但不可回滚、不触发触发器。
 *   - 易错：`update`/`delete` 忘记 `where` 是生产事故高发点，执行前先用 `select` 验证条件。
 * ============================================================
 */

package com.heima.ch20;

// ==================== 示例代码 ====================

/**
 * 增删改的 SQL 在 Java 里通常以常量或方法拼接的方式生成。
 * 注意：真实项目里凡是有用户输入的地方都必须用 PreparedStatement 占位符，不能直接拼字符串。
 */
public class CrudSqlTemplate {
    // 插入模板：字段顺序与占位符一一对应
    public static String insertSql(String table, String... columns) {
        String cols = String.join(", ", columns);
        String marks = String.join(", ", java.util.Collections.nCopies(columns.length, "?"));
        return "insert into " + table + " (" + cols + ") values (" + marks + ")";
    }

    // 更新模板
    public static String updateSql(String table, String setClause, String whereClause) {
        return "update " + table + " set " + setClause + " where " + whereClause;
    }

    // 删除模板
    public static String deleteSql(String table, String whereClause) {
        return "delete from " + table + " where " + whereClause;
    }

    public static void main(String[] args) {
        System.out.println(insertSql("student", "name", "age", "gender"));
        System.out.println(updateSql("student", "age = ?", "name = ?"));
        System.out.println(deleteSql("student", "id = ?"));

        // 危险写法：where 条件为空会删除全表，必须做非空校验
        String where = "";
        if (where == null || where.isBlank()) {
            System.out.println("拒绝执行：" + "delete from student where " + where + "（条件为空）");
        } else {
            System.out.println(deleteSql("student", where));
        }
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 向 `book` 表批量插入 5 条图书记录，其中至少包含一本价格超过 100 元的书。
 * 2. 把 `book` 表中所有价格低于 50 元的书提价 10%，再用查询验证结果。
 * 3. 删除 `book` 表中库存为 0 的记录，并说明 `delete` 和 `truncate` 的区别。
 * ============================================================
 */
