/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：51-01
 * 课名：支付系统全景
 * ------------------------------------------------------------
 * 知识点：
 *   - **支付系统是"资金的路由器"**：用户 -> 商户收银台 -> 支付网关（自己）-> 支付渠道（微信/支付宝/银行）-> 清结算 -> 商户账户。**每一跳都是资金**，每一跳的错误都以钱为单位——这就是为什么支付系统有全书最严格的质量标准（36 章安全、39 章一致性、44-09 权限在这里全部强化）。
 *   - **核心域划分**：**收银**（面向用户：聚合支付方式）、**支付核心**（状态机+渠道路由）、**渠道网关**（对接外部支付渠道）、**账务**（复式记账，51-08）、**对账**（51-05）、**清算结算**（资金划拨）、**风控**（51-07）。**域与域之间事件驱动**（49-03），资金路径上强一致。
 *   - **支付系统的三高要求**：**一致性**（一分钱都不能错——39-09 的本地消息表在资金域是标配）、**可靠性**（渠道抖动不能影响用户支付——降级路由多渠道）、**可审计**（每笔资金变动可追溯——事件溯源 49-06 的天然场景）。
 *   - **钱的单位纪律**：全部用**分（long/int）**存储与计算，绝不用 double/float（14 章 BigDecimal 在支付域的纪律升级：整数分单位是行业标准）；展示层才转元。
 *   - **与订单域的关系**：订单域发"待支付"事件 -> 支付域创建支付单 -> 渠道回调 -> 支付域发"支付成功"事件 -> 订单域推进状态。**支付单与订单单分离**（一笔订单可能多笔支付尝试）——单据模型是设计第一课。
 *   - 易错点：金额用浮点（对账差分）；支付单与订单混用（重试语义混乱）；没有幂等键体系（重复扣款）；渠道返回"处理中"被当成失败（状态机断裂）。
 * ============================================================
 */

package com.heima.ch51;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class PaymentOverview {
    record Domain(String name, String duty, String keyDesign) {
    }

    static void domains() {
        System.out.println("【支付系统七个核心域】");
        List<Domain> ds = List.of(
                new Domain("收银", "聚合支付方式，面向用户", "收银台参数与有效期"),
                new Domain("支付核心", "支付单状态机 + 渠道路由", "幂等键体系（51-03）"),
                new Domain("渠道网关", "对接微信/支付宝/银行", "签名验签 + 熔断路由（51-02）"),
                new Domain("账务", "复式记账，账户余额", "借贷平衡（51-08）"),
                new Domain("对账", "渠道账单 vs 本地流水", "差异处理闭环（51-05）"),
                new Domain("清算结算", "资金划拨与分账", "T+1 批次与差错"),
                new Domain("风控", "交易风险识别", "规则+模型，拦截与放行（51-07）"));
        for (Domain d : ds) {
            System.out.printf("  %-6s %-22s %s%n", d.name(), d.duty(), d.keyDesign());
        }
        System.out.println();
    }

    static void moneyDiscipline() {
        System.out.println("【金额纪律：全程用分】");
        String bad = "double amount = 0.1 + 0.2;  // = 0.30000000000000004";
        System.out.println("  反例：" + bad);
        System.out.println("  正例：long cents = 10 + 20;     // = 30 分，整数运算无误差");
        List<String> rules = List.of(
                "存储/计算/传输全用分（long），序列化也用分",
                "展示层才转元（除以 100，注意格式化）",
                "BigDecimal 仅用于费率计算（如 0.6% 手续费），结果回整数分",
                "四舍五入规则统一（银行家舍入 vs 四舍五入，写入契约 46-08）");
        for (String r : rules) {
            System.out.println("  - " + r);
        }
        System.out.println();
    }

    static void orderVsPayment() {
        System.out.println("【订单单 vs 支付单：为什么要两张单】");
        Map<String, String> compare = new LinkedHashMap<>();
        compare.put("订单单（订单域）", "业务事实：买了什么，状态=待支付/已支付/已关闭");
        compare.put("支付单（支付域）", "资金事实：怎么付的，一笔订单可有多笔支付尝试");
        compare.put("关系", "1 个订单 : N 个支付单（用户换渠道重付/超时重试）");
        compare.put("状态推进", "支付单成功 -> 事件 -> 订单推进已支付（唯一推进入口）");
        for (Map.Entry<String, String> e : compare.entrySet()) {
            System.out.printf("  %-16s %s%n", e.getKey(), e.getValue());
        }
        System.out.println("  反例：订单表直接存渠道流水号 -> 多次尝试覆盖混乱，退款找不到依据");
        System.out.println();
    }

    static void eventFlow() {
        System.out.println("【域间事件流（49-03 的支付版）】");
        List<String> flow = List.of(
                "订单域：OrderCreated(待支付) -> 支付域创建支付单",
                "用户支付 -> 渠道回调 -> 支付单 PAID",
                "支付域：PaymentSucceeded -> 订单域（推进已支付）",
                "                       -> 履约域（创建运单，29 章事件链）",
                "                       -> 账务域（记账，借贷分录 51-08）",
                "全部事件经 outbox 保证不丢（39-09），消费幂等（39/43 章）");
        for (String s : flow) {
            System.out.println("  " + s);
        }
    }

    public static void main(String[] args) {
        domains();
        moneyDiscipline();
        orderVsPayment();
        eventFlow();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 画出你的支付系统域划分图（或基于本章七域裁剪），标注每个域的一致性级别与事件流。
 * 2. 审查项目里的金额字段：找出所有 double/float 金额与"元字符串"存储，改成整数分并记录改造清单。
 * 3. 思考题：为什么支付单与订单必须分离？从"多次支付尝试""部分退款""渠道对账"三个场景分析单据模型。
 * ============================================================
 */
