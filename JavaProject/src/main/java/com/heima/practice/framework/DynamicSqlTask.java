/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：实操 23-02
 * 课名：动态 SQL 拼接器
 * ------------------------------------------------------------
 * 知识点：
 *   - 【题目要求】实现动态 SQL 拼接（模拟 <if>/<where>/<foreach>）：查询条件对象（可选 name/status/idList）-> 按 null 判断拼 WHERE 片段（首条件无 AND）-> foreach 生成 IN 占位符列表 -> 输出最终 SQL 与参数顺序；注入恶意输入验证参数化下"值永不进结构"。
 *   - 【考核点】动态 SQL 条件拼接、WHERE 首条件与 AND 处理、foreach 占位符生成、参数化防注入。
 *   - 【易错点】首条件多余 AND（where 标签语义）；foreach 占位符数量与参数数量不匹配；拼接用户输入进 SQL（注入）。
 *   - 【提示】实现"片段列表 + 连接器"：条件非空才加入片段，连接器负责首片段无 AND。
 * ============================================================
 */

package com.heima.practice.framework;

import java.util.ArrayList;
import java.util.List;

// ==================== 示例代码 ====================

public class DynamicSqlTask {
    public static void main(String[] args) {
        List<String> fragments = new ArrayList<>();
        String name = "abc";           // 非 null -> 参与
        Integer minAge = null;         // null -> 跳过
        if (name != null) {
            fragments.add("name LIKE ?");
        }
        if (minAge != null) {
            fragments.add("age >= ?");
        }
        StringBuilder where = new StringBuilder();
        for (int i = 0; i < fragments.size(); i++) {
            if (i > 0) {
                where.append(" AND ");
            }
            where.append(fragments.get(i));
        }
        System.out.println("  动态 SQL：SELECT * FROM t WHERE " + where);
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现完整动态 SQL 拼接器（if/where/foreach），四种参数组合输出四种 SQL 与参数序。
 * 2. 对比"字符串拼接用户输入"与"参数绑定"在同恶意输入下的 SQL 形态（36-02 复习）。
 * 3. 思考题：为什么动态 SQL 的每个片段都用占位符而非拼值？从注入与缓存（预编译复用）两个角度分析。
 * 
 * 
 * ## 第24章 Spring Boot 入门
 * ============================================================
 */
