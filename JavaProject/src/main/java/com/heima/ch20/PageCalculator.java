/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：20-05
 * 课名：排序、分页与聚合函数
 * ------------------------------------------------------------
 * 知识点：
 *   - 排序：`order by 字段 [asc|desc]`，`asc` 升序（默认）、`desc` 降序；多字段排序用逗号分隔，前面的优先。
 *   - 分页：`limit 起始索引, 每页条数`；起始索引 = `(页码 - 1) * 每页条数`。
 *   - 聚合函数：`count(*)` 统计行数、`sum` 求和、`avg` 平均值、`max`/`min` 最值，都会忽略 `null`。
 *   - 分组：`group by 字段` 把相同值归为一组，`select` 中只能出现分组字段和聚合函数。
 *   - `having` 对分组后的结果过滤，`where` 对分组前的行过滤，两者不能混用。
 *   - 易错：`count(字段)` 不统计 `null` 值，`count(*)` 统计所有行；分页索引从 0 开始。
 * ============================================================
 */

package com.heima.ch20;

// ==================== 示例代码 ====================

/**
 * 分页参数换算：前端传"第几页、每页几条"，后端换算成 limit 的起始索引。
 * 这是所有列表页接口都要写的逻辑。
 */
public class PageCalculator {
    /** 起始索引 = (页码 - 1) * 每页条数 */
    public static int offset(int pageNo, int pageSize) {
        if (pageNo < 1 || pageSize < 1) {
            throw new IllegalArgumentException("页码和每页条数必须大于 0");
        }
        return (pageNo - 1) * pageSize;
    }

    /** 总页数 = 向上取整(总条数 / 每页条数) */
    public static int totalPages(int totalCount, int pageSize) {
        if (pageSize < 1) {
            throw new IllegalArgumentException("每页条数必须大于 0");
        }
        return (totalCount + pageSize - 1) / pageSize;
    }

    public static String limitClause(int pageNo, int pageSize) {
        return "limit " + offset(pageNo, pageSize) + ", " + pageSize;
    }

    public static void main(String[] args) {
        int total = 25;
        int size = 10;
        System.out.println("总条数 " + total + "，每页 " + size + " 条，共 "
                + totalPages(total, size) + " 页");
        for (int page = 1; page <= totalPages(total, size); page++) {
            System.out.println("第 " + page + " 页 -> " + limitClause(page, size));
        }

        // 最后一页不足一整页也能正常计算
        System.out.println("每页 4 条时共 " + totalPages(total, 4) + " 页");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 查询 `book` 表中价格最高的 3 本书，输出书名和价格。
 * 2. 按作者分组统计 `book` 表中每位作者的书本数量和平均价格，只显示书本数量大于 1 的作者。
 * 3. 对 `student` 表实现分页查询：每页 3 条，分别写出第 1 页和第 3 页的 SQL。
 * ============================================================
 */
