/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：20-06
 * 课名：多表关联查询
 * ------------------------------------------------------------
 * 知识点：
 *   - 外键关联：从表通过外键字段指向主表的主键，体现"一对多"关系（如一个班级有多个学生）。
 *   - 内连接 `inner join ... on ...`：只保留两张表都匹配上的行，`inner` 可省略。
 *   - 左外连接 `left join`：保留左表全部行，右表无匹配时补 `null`；右外连接反之。
 *   - 自连接：同一张表起两个别名，用于"员工 → 上级"这类自身关联。
 *   - 子查询：`select ... where 字段 in (select ...)`，注意子查询返回多行时要用 `in`。
 *   - 易错：多表查询时字段重名必须加表名或别名前缀（如 `s.name`），否则报 `ambiguous` 错误。
 * ============================================================
 */

package com.heima.ch20;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

/**
 * 多表查询的结果在 Java 里通常有两种封装方式：
 *   1) 扁平结构：一行一个对象，把关联字段平铺进来（适合列表展示）
 *   2) 嵌套结构：主表对象里持有一个 List<从表对象>（适合详情展示）
 */
public class JoinResultModel {
    public static void main(String[] args) {
        // 模拟"班级 left join 学生"的查询结果：一行 = 班级名 + 学生名
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"一班", "张三"});
        rows.add(new String[]{"一班", "李四"});
        rows.add(new String[]{"二班", "王五"});
        rows.add(new String[]{"三班", null});   // 没有学生的班级

        // 方式一：扁平结构
        System.out.println("=== 扁平结构 ===");
        for (String[] row : rows) {
            System.out.println("班级：" + row[0] + "，学生：" + row[1]);
        }

        // 方式二：嵌套结构，用 LinkedHashMap 保持查询顺序
        Map<String, List<String>> classToStudents = new LinkedHashMap<>();
        for (String[] row : rows) {
            classToStudents.computeIfAbsent(row[0], k -> new ArrayList<>());
            if (row[1] != null) {                 // 左连接补的 null 要过滤
                classToStudents.get(row[0]).add(row[1]);
            }
        }

        System.out.println("=== 嵌套结构 ===");
        classToStudents.forEach((cname, students) ->
                System.out.println(cname + "（" + students.size() + " 人）：" + students));

        // 统计：人数为 0 的班级也要出现在结果里（左连接 + count 的意义）
        classToStudents.forEach((cname, students) ->
                System.out.println(cname + " 人数 = " + students.size()));
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 创建 `department`（部门）和 `employee`（员工）两张表，建立外键关联，用内连接查询每个员工及其部门名称。
 * 2. 用左外连接查询所有部门及其员工人数，没有员工的部门显示 0 人。
 * 3. 用子查询查询 `book` 表中价格高于平均价格的书籍，再用连接查询改写一次。
 * ============================================================
 */
