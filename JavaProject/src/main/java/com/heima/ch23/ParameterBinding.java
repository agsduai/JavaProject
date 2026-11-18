/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：23-04
 * 课名：参数传递与占位符
 * ------------------------------------------------------------
 * 知识点：
 *   - `#{}` 是**预编译占位符**，生成 `?`，由 `PreparedStatement` 设参，能防 SQL 注入；`${}` 是**字符串拼接**，直接把值拼进 SQL，有注入风险。
 *   - 单个基本类型参数：`#{}` 里名字随便写，如 `#{id}`、`#{value}` 都能取到。
 *   - 多个参数：MyBatis 会封装成 `Map`，键是 `arg0/arg1` 或 `param1/param2`；推荐用 `@Param("name")` 显式命名。
 *   - 对象参数：`#{}` 里写属性名，如 `#{name}`、`#{age}`，走 getter 取值。
 *   - `Map` 参数：`#{}` 里写 key。
 *   - `${}` 的合法用途只有动态表名、动态列名、`ORDER BY` 字段这类"结构"部分，且必须做白名单校验。
 *   - 易错：`ORDER BY #{column}` 不生效（会变成 `ORDER BY 'name'` 按常量排序），这里必须用 `${}` 加白名单。
 * ============================================================
 */

package com.heima.ch23;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// ==================== 示例代码 ====================

public class ParameterBinding {
    public static void main(String[] args) {
        // 1. #{} 与 ${} 的替换结果对比
        String sql1 = "SELECT * FROM student WHERE name = #{name} AND age > #{age}";
        String sql2 = "SELECT * FROM student WHERE name = '${name}' AND age > ${age}";

        Map<String, Object> params = new HashMap<>();
        params.put("name", "张三");
        params.put("age", 18);

        System.out.println("=== #{} 预编译 ===");
        System.out.println("SQL   ：" + toPrepared(sql1));
        System.out.println("参数  ：" + extractParams(sql1, params));
        System.out.println();

        System.out.println("=== ${} 拼接 ===");
        System.out.println("SQL   ：" + toConcatenated(sql2, params));
        System.out.println();

        // 2. 注入攻击演示（仅拼接才会中招）
        Map<String, Object> attack = new HashMap<>();
        attack.put("name", "张三' OR '1'='1");
        attack.put("age", 0);
        System.out.println("=== 恶意输入：张三' OR '1'='1 ===");
        System.out.println("#{} 生成的 SQL：" + toPrepared(sql1) + "  （值被当作纯字符串，安全）");
        System.out.println("${} 生成的 SQL：" + toConcatenated(sql2, attack));
        System.out.println("  -> 条件恒真，全表数据被拖走");
        System.out.println();

        // 3. ${} 的唯一合法场景：动态排序字段 + 白名单
        String orderBy = "score";
        System.out.println("=== ORDER BY 动态字段 ===");
        System.out.println("错误写法 ORDER BY #{orderBy} -> ORDER BY ?（按常量排序，等于没排）");
        System.out.println("正确写法 ORDER BY ${orderBy} -> ORDER BY " + safeColumn(orderBy));

        String bad = "score; DROP TABLE student";
        System.out.println("恶意字段 " + bad + " -> 白名单校验后：" + safeColumn(bad));
    }

    private static String toPrepared(String sql) {
        return sql.replaceAll("#\\{\\w+}", "?");
    }

    private static String toConcatenated(String sql, Map<String, Object> params) {
        Matcher m = Pattern.compile("\\$\\{(\\w+)}").matcher(sql);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            Object v = params.get(m.group(1));
            m.appendReplacement(sb, Matcher.quoteReplacement(String.valueOf(v)));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String extractParams(String sql, Map<String, Object> params) {
        Matcher m = Pattern.compile("#\\{(\\w+)}").matcher(sql);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            if (!sb.isEmpty()) {
                sb.append(", ");
            }
            sb.append(params.get(m.group(1)));
        }
        return "[" + sb + "]";
    }

    private static String safeColumn(String col) {
        // 白名单：只允许已知列名
        return switch (col) {
            case "id", "name", "age", "score" -> col;
            default -> "id";
        };
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 写一个三参数查询方法，分别用 `@Param` 命名和默认 `arg0/arg1` 取值，对比可读性。
 * 2. 用 `ORDER BY ${column}` 实现前端可选排序，并加上白名单校验。
 * 3. 解释为什么 `#{}` 能防注入而 `${}` 不能，从 SQL 编译过程的角度回答。
 * ============================================================
 */
