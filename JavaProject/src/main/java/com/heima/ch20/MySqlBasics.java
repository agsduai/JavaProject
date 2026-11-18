/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：20-01
 * 课名：数据库基础与MySQL安装使用
 * ------------------------------------------------------------
 * 知识点：
 *   - 数据库（Database）按照一定结构组织、存储和管理数据的仓库；MySQL 是关系型数据库，数据存放在"表"中。
 *   - 关系型数据库的核心概念：数据库（schema）→ 表（table）→ 行（记录）→ 列（字段）。
 *   - 常用客户端：命令行 `mysql -u root -p`、Navicat、DataGrip、DBeaver、IDEA 内置 Database 工具。
 *   - 常用命令：`show databases;` 查看所有库、`use 库名;` 切换库、`show tables;` 查看表、`desc 表名;` 查看表结构。
 *   - SQL 分类：DDL（定义，建库建表）、DML（操作，增删改）、DQL（查询）、DCL（权限控制）。
 *   - 易错：MySQL 语句以分号结尾；库名、表名、字段名推荐小写 + 下划线，避免大小写敏感性带来的问题。
 * ============================================================
 */

package com.heima.ch20;

// ==================== 示例代码 ====================

public class MySqlBasics {
    // 把常用 SQL 以文本块集中管理，方便复制到客户端执行，也便于版本控制
    private static final String CREATE_DB = """
            create database if not exists heima_db
                default character set utf8mb4
                default collate utf8mb4_general_ci;
            """;

    private static final String[] CHECKS = {
            "select version();      -- 查看版本",
            "select database();     -- 查看当前库",
            "show tables;           -- 查看当前库的表",
            "desc student;          -- 查看表结构"
    };

    public static void main(String[] args) {
        System.out.println("【建库脚本】");
        System.out.println(CREATE_DB);
        System.out.println("【环境检查】");
        for (String sql : CHECKS) {
            System.out.println("  " + sql);
        }
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 安装并启动 MySQL，用命令行登录，创建名为 `practice_db` 的数据库并指定字符集为 utf8mb4。
 * 2. 分别用命令行和图形化客户端查看 `practice_db` 中的所有表，说明两种方式的优缺点。
 * 3. 写出查看 MySQL 版本、查看当前数据库、查看表结构的 SQL 语句。
 * ============================================================
 */
