/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：20-02
 * 课名：建表与数据类型
 * ------------------------------------------------------------
 * 知识点：
 *   - 建表语法：`create table 表名 (字段名 类型 [约束] [comment '注释'], ...) engine=InnoDB default charset=utf8mb4;`
 *   - 常用数值类型：`tinyint`（1 字节）、`int`（4 字节）、`bigint`（8 字节）、`decimal(m,d)`（精确小数，金额必用）。
 *   - 常用字符串类型：`char(n)` 定长、`varchar(n)` 变长、`text` 长文本；手机号用 `varchar`，因为要存前导 0。
 *   - 日期类型：`date`（年月日）、`time`（时分秒）、`datetime`（年月日时分秒）、`timestamp`（时间戳，受时区影响）。
 *   - 表选项：`engine=InnoDB` 支持事务和外键，是 MySQL 默认存储引擎；`charset=utf8mb4` 支持 emoji。
 *   - 易错：金额用 `double` 会丢精度，必须用 `decimal(10,2)`；`varchar` 长度按字符计，不是字节。
 * ============================================================
 */

package com.heima.ch20;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

// ==================== 示例代码 ====================

/**
 * Java 类型与 MySQL 类型的对应关系，写实体类时按这张表来选类型。
 */
public class TypeMapping {
    public static void main(String[] args) {
        // 小整数 -> tinyint -> byte / Integer
        byte age = 18;
        // 普通整数 -> int -> Integer
        int id = 1;
        // 大整数 -> bigint -> Long
        long orderNo = 20261117001L;
        // 金额 -> decimal(10,2) -> BigDecimal（绝不用 double）
        BigDecimal price = new BigDecimal("19.90");
        // 定长字符串 -> char(1) -> String
        String gender = "男";
        // 变长字符串 -> varchar(20) -> String
        String name = "张三";
        // 日期 -> date -> LocalDate
        LocalDate birthday = LocalDate.of(2008, 5, 12);
        // 日期时间 -> datetime -> LocalDateTime
        LocalDateTime createTime = LocalDateTime.now();

        System.out.println("id=" + id + "（int）");
        System.out.println("age=" + age + "（tinyint）");
        System.out.println("orderNo=" + orderNo + "（bigint）");
        System.out.println("price=" + price + "（decimal）");
        System.out.println("gender=" + gender + "（char(1)）");
        System.out.println("name=" + name + "（varchar(20)）");
        System.out.println("birthday=" + birthday + "（date）");
        System.out.println("createTime=" + createTime + "（datetime）");

        // 金额计算用 BigDecimal，避免 0.1 + 0.2 这类精度问题
        BigDecimal a = new BigDecimal("0.1");
        BigDecimal b = new BigDecimal("0.2");
        System.out.println("0.1 + 0.2 = " + a.add(b));
        System.out.println("double 版 = " + (0.1 + 0.2));
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 创建一张 `book` 表，包含编号（主键自增）、书名、作者、价格、出版日期、库存六个字段，并给每个字段加注释。
 * 2. 创建一张 `employee` 表，包含工号、姓名、性别、手机号、入职时间、月薪，思考每个字段该用什么类型并说明理由。
 * 3. 用 `show create table` 查看自己创建的表，解释输出中 `engine` 和 `charset` 的含义。
 * ============================================================
 */
