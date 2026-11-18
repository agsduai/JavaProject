/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：20-04
 * 课名：基础查询与条件过滤
 * ------------------------------------------------------------
 * 知识点：
 *   - 查询语法顺序：`select 字段列表 from 表名 where 条件 group by 分组 having 过滤 order by 排序 limit 分页`。
 *   - `select *` 查询所有列；`select 字段1, 字段2` 只查指定列，生产环境不推荐 `*`。
 *   - 条件运算符：`=`、`<>`/`!=`、`>`、`<`、`>=`、`<=`、`between ... and ...`、`in (...)`、`like`。
 *   - `like` 通配符：`%` 匹配任意多个字符，`_` 匹配单个字符；`like '%张%'` 表示包含"张"。
 *   - 空值判断必须用 `is null` / `is not null`，不能用 `= null`。
 *   - 别名：`select name as 姓名` 或 `select name 姓名`；`distinct` 去重。
 *   - 易错：`and` 优先级高于 `or`，混用时要用括号明确分组。
 * ============================================================
 */

package com.heima.ch20;

import java.util.ArrayList;
import java.util.List;

// ==================== 示例代码 ====================

/**
 * 条件查询的动态拼接：实际项目里常按"有没有传参"来决定是否追加条件。
 * 这里只演示拼接逻辑，真正执行时每个值都要用 ? 占位并由 PreparedStatement 绑定。
 */
public class ConditionQueryBuilder {
    public static void main(String[] args) {
        List<String> conditions = new ArrayList<>();
        List<Object> params = new ArrayList<>();

        String name = "张";          // 用户可能不填
        Integer minAge = 18;         // 用户可能不填
        String gender = null;        // 用户没选

        if (name != null && !name.isBlank()) {
            conditions.add("name like ?");
            params.add("%" + name + "%");
        }
        if (minAge != null) {
            conditions.add("age >= ?");
            params.add(minAge);
        }
        if (gender != null && !gender.isBlank()) {
            conditions.add("gender = ?");
            params.add(gender);
        }

        String where = conditions.isEmpty() ? "" : " where " + String.join(" and ", conditions);
        String sql = "select * from student" + where;
        System.out.println("SQL：" + sql);
        System.out.println("参数：" + params);

        // 注意 and 与 or 混用时必须加括号
        System.out.println("错误：" + "where age >= 20 or gender = '女' and phone is not null");
        System.out.println("正确：" + "where (age >= 20 or gender = '女') and phone is not null");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 查询 `book` 表中价格在 50~100 元之间的书，只显示书名和价格，并用中文别名。
 * 2. 查询 `student` 表中手机号以 `138` 开头的学生，再查询姓名第二个字是"三"的学生。
 * 3. 查询 `product` 表中描述为空且库存大于 0 的商品，说明为什么不能用 `description = null`。
 * ============================================================
 */
