/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：49-01
 * 课名：领域驱动设计总览
 * ------------------------------------------------------------
 * 知识点：
 *   - **DDD 解决的问题是"复杂业务在代码中的表达"**：贫血模型（POJO + 一堆 Service）把业务规则散落在 Service 方法里，改一个促销规则要横跨五个类；DDD 把**业务规则收进领域模型**，让代码结构映射业务结构。**业务复杂时 DDD 是投资，业务简单时是负担**——CRUD 系统不必上 DDD。
 *   - **两条腿：战略设计与战术设计**：战略设计回答"系统怎么拆"——限界上下文（Bounded Context）、通用语言（Ubiquitous Language）、上下文映射（Context Map）；战术设计回答"一个上下文内部怎么写"——聚合（Aggregate）、实体（Entity）、值对象（Value Object）、领域服务、领域事件。**战略先于战术**：上下文划错了，战术写得再漂亮也是错的模型。
 *   - **通用语言是 DDD 的灵魂**：业务说"核销"、代码里就该有 `redeem`（而不是 `updateStatus2`）——**语言统一后，沟通成本与代码歧义同步消失**。看到代码里出现"管理器/工具/助手"类命名，多半是通用语言缺失。
 *   - **限界上下文 ≠ 微服务**（29 章的拆分其实就是在划上下文）：一个上下文内语言统一（"商品"在库存上下文指 SKU，在营销上下文指可参加活动的商品池）——**同一个词在不同上下文含义不同，跨上下文必须转换**。上下文可以包含多个服务，也可以一个服务承载多个小上下文。
 *   - **DDD 与分层的关系**：领域层（模型与规则）在最里、应用层（用例编排）在其上、基础设施（DB/MQ）在外——**依赖方向永远指向领域层**（49-04 六边形架构）。
 *   - **落地路径**：事件风暴（Event Storming）工作坊梳理业务事件 -> 按事件聚类划上下文 -> 每个上下文内识别聚合 -> 写代码。**从"事件"出发比从"表"出发更接近业务**。
 *   - 易错点：给 CRUD 系统硬套 DDD（聚合根/仓储全套上，代码量翻倍收益为零）；一个"大模型"贯穿全系统（没有上下文边界，语义混乱）；战术设计先行（聚合划错）；把 DDD 等同于"代码分层模板"（DDD 是思维方式不是目录结构）。
 * ============================================================
 */

package com.heima.ch49;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class DddOverview {
    record ContextMap(String context, String language, String keyConcept, String integration) {
    }

    static void strategic() {
        System.out.println("【战略设计：电商的限界上下文映射】");
        List<ContextMap> ctxs = List.of(
                new ContextMap("交易上下文", "订单（含金额/状态机）", "Order 不可变快照", "发布 OrderPaid 事件"),
                new ContextMap("库存上下文", "扣减（只关心 SKU 与数量）", "StockItem", "订阅 OrderPaid 扣库存"),
                new ContextMap("营销上下文", "核销（优惠券兑换）", "Coupon Redemption", "订阅 OrderCreated 预占"),
                new ContextMap("履约上下文", "发货（收货地址/物流）", "Shipment", "订阅 OrderPaid 创建运单"));
        for (ContextMap c : ctxs) {
            System.out.printf("  %-8s 通用语言=%-18s 核心概念=%-18s 集成=%s%n",
                    c.context(), c.language(), c.keyConcept(), c.integration());
        }
        System.out.println("  同一个“商品”：库存里是 SKU，营销里是活动商品池 —— 上下文内各自建模");
        System.out.println();
    }

    static void anemicVsRich() {
        System.out.println("【贫血 vs 充血：同一规则的两种归属】");
        System.out.println("  规则：订单总价 = 明细之和 - 优惠券 - 积分抵扣（优惠券不可超订单额）");
        Map<String, String> anemic = new LinkedHashMap<>();
        anemic.put("OrderPO", "只有 getter/setter");
        anemic.put("OrderService.calcAmount()", "规则全在这里（还有 CouponService、PointService 各管一段）");
        anemic.put("改规则", "找 Service -> 三个类都可能被改 -> 交叉影响");
        for (Map.Entry<String, String> e : anemic.entrySet()) {
            System.out.printf("  贫血  %-22s %s%n", e.getKey(), e.getValue());
        }
        List<String> rich = List.of(
                "Order.addCoupon(coupon) -> 规则在聚合内， coupon 超额直接抛业务异常",
                "Order.totalAmount() 是聚合的派生属性（不落库，实时计算）",
                "改规则 -> 只改 Order 一个类 -> 单元测试聚焦聚合");
        for (String r : rich) {
            System.out.println("  充血  " + r);
        }
        System.out.println();
    }

    static void eventStorming() {
        System.out.println("【事件风暴：从业务事件出发建模】");
        List<String> steps = List.of(
                "1) 橙色便利贴：领域事件（订单已创建/已支付/已发货...）按时间线排布",
                "2) 蓝色：命令（创建订单/支付）与触发者（用户/定时任务/事件）",
                "3) 黄色：实体与聚合（哪些事件总是绑在一起发生）",
                "4) 粉色：外部系统（支付渠道/物流）",
                "5) 聚类：事件紧密关联的划进同一上下文 —— 上下文边界自己浮现");
        for (String s : steps) {
            System.out.println("  " + s);
        }
        System.out.println("  产出：上下文映射图 + 每个上下文的事件清单 —— 直接对应 29 章的服务拆分");
        System.out.println();
    }

    static void whenDdd() {
        System.out.println("【决策：什么复杂度值得 DDD】");
        String[][] rows = {
                {"后台管理 CRUD", "不用", "贫血 + Service 直改库，简单直接"},
                {"规则会变的核心域", "全量 DDD", "促销/库存/计费：规则收进聚合"},
                {"支撑域（如通知）", "轻量", "上下文划清 + 事件驱动，战术从简"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-14s %s%n", r[0], r[1]);
        }
        System.out.println("  核心域重投入、非核心域轻投入 —— DDD 也是成本管理");
    }

    public static void main(String[] args) {
        strategic();
        anemicVsRich();
        eventStorming();
        whenDdd();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 用事件风暴的方法梳理你负责的业务（列出 ≥8 个领域事件），聚类出限界上下文，画出上下文映射图。
 * 2. 找一段贫血代码（或虚构），把其中一条业务规则搬进聚合，对比改动时的影响面差异。
 * 3. 思考题：为什么"同一个词在不同上下文含义不同"反而健康？从语义歧义与模型准确性角度分析跨上下文翻译的必要性。
 * ============================================================
 */
