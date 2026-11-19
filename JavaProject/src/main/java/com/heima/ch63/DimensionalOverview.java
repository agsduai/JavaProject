/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：63-01
 * 课名：维度建模总览：从业务过程到星型模型
 * ------------------------------------------------------------
 * 知识点：
 *   - **维度建模的出发点不是"表结构"而是"业务过程"**：先问"业务上发生了什么事"（下单、支付、退款、发货），再为每个过程建一张**事实表**（度量）和一组**维度表**（描述）——**这是与"第三范式建模"的根本差异**（范式面向"无冗余存储"，维度建模面向"分析查询快"）。
 *   - **星型模型的结构**：中心是**事实表**（外键 + 度量），周围是**维度表**（描述性属性）——**一个事实表关联多个维度**，形状像星星；**雪花模型**是把维度再规范化拆成多层（63-07 权衡）。
 *   - **事实 vs 维度的判据**：**"能用数字累加/求平均的是事实，用来描述'谁在何时何地做了什么'的是维度"**——金额、数量、时长是事实；用户、商品、日期、渠道是维度。
 *   - **粒度是建模的第一决策**（63-02 第二步）：一张事实表只表达**一个粒度**（"每个订单一行"还是"每个订单每个商品一行"）——**不同粒度不能混在一张事实表**（会导致度量重复计算）。
 *   - **与 46 章分层的关系**：46-01 的 DWD/DWS/ADS 是"数据流的分层"，维度建模是"每层内部怎么组织表"——**DWD 常按维度建模建事实+维度，DWS 按主题做聚合**。
 *   - 易错点：把业务库表直接当维度模型（OLTP 范式表 join 复杂、查询慢）；一张事实表混多个粒度（汇总翻倍）；维度表里放度量（无法聚合）；没有日期维度（分析必备）。
 * ============================================================
 */

package com.heima.ch63;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class DimensionalOverview {
    record Dimension(String name, List<String> attributes) {
    }

    record FactTable(String name, List<String> foreignKeys, List<String> measures,
                     String grain) {
    }

    static void starSchema() {
        System.out.println("【订单主题星型模型】");
        FactTable fact = new FactTable("fact_order_item",
                List.of("date_key", "user_key", "product_key", "channel_key", "order_id"),
                List.of("quantity", "amount", "discount_amount", "cost_amount"),
                "每个订单的每个商品一行");
        System.out.printf("  事实表：%s%n    粒度：%s%n    外键：%s%n    度量：%s%n",
                fact.name(), fact.grain(), fact.foreignKeys(), fact.measures());

        List<Dimension> dims = List.of(
                new Dimension("dim_date", List.of("date_key", "year", "month", "week", "is_holiday")),
                new Dimension("dim_user", List.of("user_key", "level", "city", "register_date")),
                new Dimension("dim_product", List.of("product_key", "category", "brand", "price_band")),
                new Dimension("dim_channel", List.of("channel_key", "channel_name", "is_online")));
        System.out.println("  维度表：");
        for (Dimension d : dims) {
            System.out.printf("    %-14s 属性=%s%n", d.name(), d.attributes());
        }
        System.out.println("  形状：事实表居中，四个维度环绕（星型）");
        System.out.println();
    }

    static void factVsDimension() {
        System.out.println("【事实与维度的判据】");
        String[][] rows = {
                {"金额/数量/时长", "事实（可累加/可平均）", "度量"},
                {"用户/商品/渠道", "维度（描述谁/什么/哪里）", "外键 + 属性"},
                {"订单号", "退化维度（无独立维度表，放事实表）", "63-03"},
                {"状态", "维度（可枚举，做状态维度表）", "便于按状态切片"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-14s %-28s %s%n", r[0], r[1], r[2]);
        }
        System.out.println();
    }

    public static void main(String[] args) {
        starSchema();
        factVsDimension();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 选一个业务过程（下单/支付/发货），画出星型模型：事实表（粒度+度量）与 4 个维度表（属性）。
 * 2. 判断 10 个字段"是事实还是维度"，对边界字段（状态、订单号）说明处理方式。
 * 3. 思考题：为什么"一张事实表只能有一个粒度"？用"订单粒度 + 商品粒度混表"导致金额重复计算的例子说明。
 * ============================================================
 */
