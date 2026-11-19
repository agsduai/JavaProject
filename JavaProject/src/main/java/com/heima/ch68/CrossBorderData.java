/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：68-06
 * 课名：数据出境与本地化
 * ------------------------------------------------------------
 * 知识点：
 *   - **数据出境是强监管红线**：向境外提供个人信息需满足**安全评估/标准合同/认证**三条路径之一（《个人信息保护法》第 38 条）；**关键信息基础设施与达到数量阈值的个人信息必须境内存储 + 出境评估**。
 *   - **四类应对策略**：**本地化部署**（数据不出境，按区域独立部署 55-10）、**去标识化后出境**（脱敏/聚合后的数据风险降低但仍需评估）、**隐私计算跨境**（联邦学习/多方安全计算，数据不动模型动 68-08）、**禁止出境**（L4 高敏数据一律不出）。
 *   - **工程落点**：**数据标记"归属地域"**（分区域存储 46-03 桶策略）、**出口流量审计**（哪个系统往外发了什么）、**SDK 与第三方服务的境外调用**（日志/监控/APM 上报也可能出境——**Agent 上报 66-10 的合规风险**）。
 *   - **与 55-10 的关系**：55-10 讲了 SaaS 的多地域部署，本章把它上升为"合规要求"：**注册时确定归属地，数据按归属地路由，跨区只允许聚合结果**。
 *   - 易错点：以为"匿名化"就能出境（可重新识别的仍算出境）；忽略监控/日志/APM 的境外上报；跨境备份（备份也是数据出境）；三方 SDK 暗地传数据。
 * ============================================================
 */

package com.heima.ch68;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class CrossBorderData {
    enum Region {
        CN, EU, US
    }

    enum Level {
        L1, L2, L3, L4
    }

    record DataAsset(String name, Level level, Region allowedRegion,
                     boolean aggregated) {
    }

    record Transfer(String asset, Region from, Region to, String purpose) {
    }

    static final List<DataAsset> assets = new ArrayList<>();

    static {
        assets.add(new DataAsset("用户手机号", Level.L3, Region.CN, false));
        assets.add(new DataAsset("身份证号", Level.L4, Region.CN, false));
        assets.add(new DataAsset("订单金额统计（按天聚合）", Level.L2, Region.CN, true));
        assets.add(new DataAsset("EU 用户邮箱", Level.L3, Region.EU, false));
    }

    /** 出境判定：地域 + 级别 + 是否聚合 */
    static String evaluate(Transfer t) {
        DataAsset asset = assets.stream()
                .filter(a -> a.name().equals(t.asset()))
                .findFirst().orElse(null);
        if (asset == null) {
            return "拒绝：未登记的数据资产（无合规依据）";
        }
        if (t.from() == t.to()) {
            return "允许：同地域流转";
        }
        if (asset.level() == Level.L4) {
            return "拒绝：L4 高敏数据禁止出境（本地化存储）";
        }
        if (asset.allowedRegion() != null && asset.allowedRegion() != t.from()) {
            return "拒绝：资产归属地 " + asset.allowedRegion() + "，不能从 " + t.from() + " 出境";
        }
        if (asset.aggregated() && asset.level() == Level.L2) {
            return "有条件允许：聚合数据出境需做安全评估备案";
        }
        return "需评估：走标准合同/安全评估/认证三条路径之一";
    }

    static void controls() {
        System.out.println("【出境控制点】");
        Map<String, String> controls = new LinkedHashMap<>();
        controls.put("存储", "按归属地域分桶存储（46-03），注册时确定归属");
        controls.put("出口", "出口流量审计：哪个服务往境外发了什么");
        controls.put("三方", "SDK/APM/日志上报是否出境（66-10 的 Agent 上报）");
        controls.put("备份", "备份也是数据，跨境备份同样受管");
        controls.put("审计", "每次出境尝试留痕并可追溯（68-09）");
        controls.forEach((k, v) -> System.out.printf("  %-6s %s%n", k, v));
        System.out.println();
    }

    public static void main(String[] args) {
        System.out.println("【数据资产登记】");
        assets.forEach(a -> System.out.printf("  %-24s 级别=%-3s 归属=%-3s 聚合=%b%n",
                a.name(), a.level(), a.allowedRegion(), a.aggregated()));

        System.out.println();
        System.out.println("【出境请求评估】");
        List<Transfer> requests = List.of(
                new Transfer("订单金额统计（按天聚合）", Region.CN, Region.EU, "海外报表"),
                new Transfer("用户手机号", Region.CN, Region.US, "海外客服"),
                new Transfer("身份证号", Region.CN, Region.US, "风控"),
                new Transfer("EU 用户邮箱", Region.EU, Region.CN, "集团汇总"),
                new Transfer("未登记字段", Region.CN, Region.US, "测试"));
        for (Transfer t : requests) {
            System.out.printf("  %-26s %s -> %s（%s）%n    %s%n", t.asset(), t.from(), t.to(),
                    t.purpose(), evaluate(t));
        }

        System.out.println();
        controls();
        System.out.println("【三条合规路径】安全评估（数量/敏感度达标）/ 标准合同（中小企业常见）/ 认证");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现出境评估器：资产级别 × 归属地 × 目的地 × 是否聚合，用 6 个请求验证判定（含"未登记拒绝"）。
 * 2. 列出你系统里所有"可能出境"的数据流（含监控/日志/APM/三方 SDK），标注风险与治理动作。
 * 3. 思考题：为什么"匿名化数据"仍可能被认定为出境？从"可重新识别（重标识攻击）"分析。
 * ============================================================
 */
