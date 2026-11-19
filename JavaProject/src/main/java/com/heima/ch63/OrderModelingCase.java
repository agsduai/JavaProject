/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：63-11
 * 课名：综合案例：电商订单主题建模
 * ------------------------------------------------------------
 * 知识点：
 *   - **把本章收口成一套完整建模**：确定业务过程（下单/支付/退款）-> 各自粒度（订单商品级/支付尝试级/退款商品级）-> 共享一致性维度（日期/用户/商品/渠道/门店）-> 三类事实表（事务 + 周期快照 + 累积快照）-> SCD2 商品/用户维度 -> 指标注册（原子/派生/复合）-> 评审与结构校验。
 *   - **设计决策的显式记录**：**为什么订单用商品粒度**（可汇总出订单级）；**为什么退款独立事实表**（金额方向与口径不同）；**为什么库存用周期快照**（状态类半可加）；**为什么商品维度 SCD2**（类目变更要可追溯）——**每条都是 ADR 素材**（52-02）。
 *   - **一致性验收**：跨过程 join 一致性维度能对上（63-08）；指标口径唯一（63-09）；结构校验全绿（63-10）；拉链区间连续（63-05）。
 *   - **与 46 章链路的对接**：ODS 原样 -> DWD 维度建模（本章）-> DWS 按主题聚合 -> ADS 报表/接口；**Spark 作业负责层间搬运**（59-11），质量规则负责层间断言（46-08）。
 *   - 易错点：三个过程各建一套维度（口径分裂）；退款金额混进支付事实表（GMV 计算错误）；指标口径散落在 SQL 里（无注册）；没有结构校验（脏数据无人拦截）。
 * ============================================================
 */

package com.heima.ch63;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class OrderModelingCase {
    record Dim(String name, String strategy, String note) {
    }

    record Fact(String name, String grain, List<String> dims, List<String> measures) {
    }

    static void model() {
        System.out.println("【一致性维度（共享）】");
        List<Dim> dims = List.of(
                new Dim("dim_date", "静态生成", "年/季/月/周/节假日"),
                new Dim("dim_user", "SCD2", "等级变更可追溯"),
                new Dim("dim_product", "SCD2", "类目调整可追溯"),
                new Dim("dim_channel", "SCD1", "纠错型，无需历史"),
                new Dim("dim_store", "SCD2", "门店区域调整"));
        dims.forEach(d -> System.out.printf("  %-12s %-8s %s%n", d.name(), d.strategy(), d.note()));

        System.out.println();
        System.out.println("【三个过程的事实表】");
        List<Fact> facts = List.of(
                new Fact("fact_order_item", "订单+商品", List.of("日期", "用户", "商品", "渠道"),
                        List.of("数量", "金额", "优惠", "成本")),
                new Fact("fact_pay_attempt", "支付尝试", List.of("日期", "用户", "渠道"),
                        List.of("支付金额", "手续费")),
                new Fact("fact_refund_item", "退款+商品", List.of("日期", "用户", "商品", "渠道"),
                        List.of("退款金额", "退款数量")));
        facts.forEach(f -> System.out.printf("  %-18s 粒度=%-10s 维度=%-28s 度量=%s%n",
                f.name(), f.grain(), f.dims(), f.measures()));
        System.out.println();
    }

    /** 跨过程一致性：同一用户在同一时间范围的各过程金额 */
    static void crossProcessCheck() {
        System.out.println("【跨过程一致性校验（同一 dim_user 代理键）】");
        Map<String, Long> orderByUser = new LinkedHashMap<>();
        orderByUser.put("u-key-501", 108_00L);
        Map<String, Long> payByUser = new LinkedHashMap<>();
        payByUser.put("u-key-501", 108_00L);
        Map<String, Long> refundByUser = new LinkedHashMap<>();
        refundByUser.put("u-key-501", 39_00L);
        for (String key : orderByUser.keySet()) {
            long order = orderByUser.get(key);
            long pay = payByUser.getOrDefault(key, 0L);
            long refund = refundByUser.getOrDefault(key, 0L);
            long netPay = pay - refund;
            System.out.printf("  用户 %s：下单=%d 支付=%d 退款=%d 净支付=%d -> %s%n",
                    key, order, pay, refund, netPay,
                    netPay == order ? "对账一致" : "差异需排查（39-09 对账口径）");
        }
        System.out.println("  前提：三个过程共享同一 dim_user（否则 join 不上）");
        System.out.println();
    }

    static void designDecisions() {
        System.out.println("【设计决策记录（ADR 素材）】");
        Map<String, String> adr = new LinkedHashMap<>();
        adr.put("订单用商品粒度", "可汇总出订单级；订单粒度无法下钻到商品");
        adr.put("退款独立事实表", "金额方向与口径不同，混表会让 GMV 计算歧义");
        adr.put("库存用周期快照", "状态类度量半可加，按天快照支持趋势");
        adr.put("商品维度 SCD2", "类目调整后需按「当时类目」统计");
        adr.put("支付用尝试粒度", "可分析失败重试，但金额需按订单去重");
        adr.forEach((k, v) -> System.out.printf("  %-14s %s%n", k, v));
    }

    public static void main(String[] args) {
        model();
        crossProcessCheck();
        designDecisions();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 为你的业务完成一套完整建模（≥3 个过程 + 5 个一致性维度 + 指标注册），输出评审文档。
 * 2. 实现跨过程一致性校验：订单/支付/退款三表按用户+日期 join，输出"净支付 vs 下单金额"的差异明细。
 * 3. 思考题：为什么"退款独立事实表"能避免 GMV 歧义？用"支付金额减去退款金额"与"混表后 SUM 金额"两种口径对比说明。
 * ============================================================
 */

/*
 * ============================================================
 * 本章综合练习（无参考答案）
 * ------------------------------------------------------------
 * 【场景】
 * 给电商数仓建模：星型模型 vs 雪花模型选型、事实表粒度选择、维度退化与缓慢变化维、拉链表、一致性维度、指标口径统一。要能回答「退款算不算 GMV」。
 * 【任务】
 * 1. 设计交易域模型：订单事实表（粒度=订单明细行）+ 用户/商品/时间/门店维度，说明为什么粒度不能选「订单」而选「明细行」。
 * 2. 实现缓慢变化维：用户等级变更保留历史（拉链表：start_date/end_date/is_current），验证按历史时间点能查到当时的等级。
 * 3. 处理维度退化：把订单号这类「维度」直接放进事实表，说明什么情况下该退化。
 * 4. 统一指标口径：写出 GMV、成交额、退款率三个指标的精确定义与 SQL，说明下单未支付、已退款、跨天退款分别怎么算。
 * 【验收清单】
 * [ ] 事实表粒度明确，任何指标都能从该粒度上聚合得到。
 * [ ] 拉链表能还原任意历史时点的维度状态，且不产生重复行。
 * [ ] 指标口径写成可执行 SQL，两个不同人写出来结果一致。
 * [ ] 能解释「同一指标两个部门数字不同」的常见原因（口径、时区、是否含退款）。
 * ============================================================
 */
