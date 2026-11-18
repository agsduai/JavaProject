/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：实操 21-01
 * 课名：DAO 模式与结果映射
 * ------------------------------------------------------------
 * 知识点：
 *   - 【题目要求】实现迷你 DAO：RowMapper 函数接口（ResultSet -> 对象，内存 Map 模拟 ResultSet 行）；JdbcTemplate 方法（query(sql, rowMapper, params)/update）；实现 UserDao（findById/findAll/insert）；SQL 与参数分离（预编译语义：参数永不拼进 SQL，36 章注入防线）；输出每条 SQL 的参数绑定日志。
 *   - 【考核点】DAO 分层、RowMapper 抽象、预编译与参数绑定、模板方法模式（31 章呼应）。
 *   - 【易错点】SQL 拼接参数（注入根源）；RowMapper 里处理连接（模板已管，mapper 只管行）；update 返回值忽略（影响行数校验）。
 *   - 【提示】JdbcTemplate 的回调接口让"资源管理"与"行映射"分离——模板方法的经典收益。
 * ============================================================
 */

package com.heima.practice.framework;

import java.util.ArrayList;
import java.util.List;

// ==================== 示例代码 ====================

public class JdbcDaoTask {
    static List<String> audit = new ArrayList<>();

    static List<String> query(String sql, Object... params) {
        audit.add("SQL=" + sql + " params=" + java.util.Arrays.toString(params));
        return List.of();        // 模拟结果
    }

    public static void main(String[] args) {
        String userInput = "a' OR '1'='1";
        query("SELECT * FROM t WHERE name = ?", userInput);   // 参数绑定
        System.out.println("  审计：" + audit);
        System.out.println("  参数在绑定层，永不拼进 SQL —— 注入无法成立");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现完整 JdbcTemplate + UserDao（CRUD + 参数绑定审计），注入恶意输入验证注入不成立。
 * 2. 实现 update 返回影响行数并做校验（影响 0 行抛异常），演示乐观锁 update 的行数用法。
 * 3. 思考题：为什么模板方法能消除 JDBC 样板代码？从"变的（SQL/映射）与不变的（连接/异常/关闭）"分离分析。
 * ============================================================
 */
