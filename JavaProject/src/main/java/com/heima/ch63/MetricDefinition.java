/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：63-09
 * 课名：指标口径与原子指标
 * ------------------------------------------------------------
 * 知识点：
 *   - **指标分层（46-10 的建模落地）**：**原子指标**（不可再拆的最细度量：支付金额、订单数）+ **派生指标**（原子指标 × 修饰词 × 时间周期：近 7 天支付金额）+ **复合指标**（多个派生指标计算：客单价 = 支付金额 ÷ 订单数）。
 *   - **修饰词是"筛选条件"的标准化**：`近 7 天`、`新用户`、`App 渠道`——**修饰词要注册**（避免"近 7 天"有的含当天有的不含）；**时间周期与统计口径必须唯一**。
 *   - **比率类指标的正确实现**：**存分子分母，查询时计算**（63-02 的不可加性）——**平均值的平均是错的**（辛普森悖论 54-08 的同类问题）；**去重类指标**（UV）不能跨天简单相加（要 union 去重，或用近似算法 46-07）。
 *   - **口径文档是资产**：每个指标记录"业务含义/计算公式/数据源/过滤条件/负责人/更新频率"——**口径变更要评审 + 版本化 + 通知下游**（46-08 契约的指标版）。
 *   - **与 42-05 SLO 的类比**：指标口径就是"数据的 SLI 定义"——**定义不清的指标无法用于决策**（52-01 非功能量化的思路）。
 *   - 易错点：比率存成事实（汇总错误）；UV 跨天相加（重复计数）；"近 7 天"含当天与否不统一（数字对不上）；口径改了不通知下游（报表突变）。
 * ============================================================
 */

package com.heima.ch63;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class MetricDefinition {
    record AtomicMetric(String code, String name, String formula, String owner) {
    }

    record DerivedMetric(String code, String name, String atomicCode, List<String> modifiers,
                         String period) {
    }

    static final Map<String, AtomicMetric> ATOMIC = new LinkedHashMap<>();
    static final List<DerivedMetric> DERIVED = new ArrayList<>();

    static void registry() {
        ATOMIC.put("pay_amount", new AtomicMetric("pay_amount", "支付金额",
                "SUM(fact_pay.amount) WHERE status=PAID", "数据组-小李"));
        ATOMIC.put("order_cnt", new AtomicMetric("order_cnt", "订单数",
                "COUNT(DISTINCT fact_order.order_id)", "数据组-小李"));
        DERIVED.add(new DerivedMetric("d7_pay_amount", "近7天支付金额", "pay_amount",
                List.of(), "近7天（不含当天）"));
        DERIVED.add(new DerivedMetric("new_user_order_cnt", "新用户订单数", "order_cnt",
                List.of("新用户", "App渠道"), "自然日"));
        System.out.println("【原子指标】");
        ATOMIC.values().forEach(m -> System.out.printf("  %-12s %-8s 公式=%s%n",
                m.code(), m.name(), m.formula()));
        System.out.println("【派生指标】");
        DERIVED.forEach(d -> System.out.printf("  %-18s = %s × %s × %s%n",
                d.name(), d.atomicCode(), d.modifiers(), d.period()));
        System.out.println();
    }

    /** 比率类：存分子分母，查询时算 */
    static void ratioMetric() {
        System.out.println("【客单价：分子分母 vs 平均的平均】");
        record Group(String name, long amount, long orders) {
        }
        List<Group> groups = List.of(
                new Group("App", 100_000, 100),
                new Group("Web", 10_000, 100));
        long totalAmount = groups.stream().mapToLong(Group::amount).sum();
        long totalOrders = groups.stream().mapToLong(Group::orders).sum();
        double correct = (double) totalAmount / totalOrders;
        double wrong = groups.stream().mapToDouble(g -> (double) g.amount() / g.orders())
                .average().orElse(0);
        System.out.printf("  正确：总金额/总订单 = %d/%d = %.2f%n", totalAmount, totalOrders, correct);
        System.out.printf("  错误：各分组客单价再平均 = %.2f（被单量少的分组拉偏）%n", wrong);
        System.out.println("  结论：比率指标必须存分子分母（或存明细），不能存比率值");
        System.out.println();
    }

    /** UV 类：跨天不能简单相加 */
    static void distinctMetric() {
        System.out.println("【UV：跨天不能相加】");
        Map<String, List<String>> uvByDay = new LinkedHashMap<>();
        uvByDay.put("11-16", List.of("u1", "u2", "u3"));
        uvByDay.put("11-17", List.of("u2", "u3", "u4"));
        long naive = uvByDay.values().stream().mapToLong(List::size).sum();
        long real = uvByDay.values().stream().flatMap(List::stream).distinct().count();
        System.out.printf("  逐日 UV 相加 = %d（错误，u2/u3 重复计数）%n", naive);
        System.out.printf("  去重后 UV = %d（正确）%n", real);
        System.out.println("  跨天/跨维度去重指标：用明细去重或 HyperLogLog 近似（46-07）");
        System.out.println();
        System.out.println("  口径纪律：公式/过滤/时间边界/负责人 全部登记，变更走评审与版本化");
    }

    public static void main(String[] args) {
        registry();
        ratioMetric();
        distinctMetric();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 注册 5 个原子指标与 5 个派生指标（含修饰词与时间周期），写出每个的口径文档字段。
 * 2. 用分组数据验证"平均值的平均"与"总金额/总订单"的差异，说明比率指标的正确实现。
 * 3. 思考题：为什么"去重类指标"跨天不能相加？设计一个"近 30 天活跃用户"的正确计算方案。
 * ============================================================
 */
