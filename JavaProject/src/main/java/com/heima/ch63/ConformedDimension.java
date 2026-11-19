/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：63-08
 * 课名：一致性维度与总线矩阵
 * ------------------------------------------------------------
 * 知识点：
 *   - **一致性维度（Conformed Dimension）**：**同一维度在多个事实表中定义完全一致**（同样的代理键、同样的属性、同样的取值）——**没有一致性维度，跨过程分析就对不上**（订单表的"杭州"与支付表的"杭州市"）。
 *   - **总线矩阵（Bus Matrix）**：**行=业务过程（事实表）、列=维度**，交叉点标记"该过程用该维度"——**它是数仓的全局地图**，保证"每个维度被多个事实表共享、每个过程覆盖核心维度"。
 *   - **一致性维度的落地**：**维度表集中建设**（一份 dim_product 被订单/退款/库存/推荐事实表引用）；**维度变更统一走 SCD**（63-04）；**新增属性的影响面评估**（46-08 血缘）。
 *   - **总线矩阵的价值**：**规划阶段**避免"每个报表建一套维度"（数据孤岛）；**评审阶段**检查"是否有过程缺关键维度"或"是否有维度只被一个过程用"（后者可能是设计冗余）。
 *   - **与 46-10 指标平台的关系**：一致性维度是"切片方向的统一"，指标平台是"度量口径的统一"——**两者共同构成"数字对得上"的基础**。
 *   - 易错点：每个主题各建一套用户维度（跨主题分析失败）；维度属性命名不统一（city/city_name/cityName）；总线矩阵画完不维护（新过程不登记）；一致性维度被单点修改（影响所有下游）。
 * ============================================================
 */

package com.heima.ch63;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class ConformedDimension {
    /** 总线矩阵：行=过程，列=维度 */
    static final List<String> DIMENSIONS =
            List.of("日期", "用户", "商品", "渠道", "门店");

    static final Map<String, List<String>> BUS_MATRIX = new LinkedHashMap<>();

    static void build() {
        BUS_MATRIX.put("下单", List.of("日期", "用户", "商品", "渠道"));
        BUS_MATRIX.put("支付", List.of("日期", "用户", "渠道"));
        BUS_MATRIX.put("退款", List.of("日期", "用户", "商品", "渠道"));
        BUS_MATRIX.put("库存", List.of("日期", "商品", "门店"));
        BUS_MATRIX.put("到店", List.of("日期", "用户", "门店"));
    }

    static void printMatrix() {
        System.out.println("【总线矩阵】");
        StringBuilder header = new StringBuilder("  过程\\维度  ");
        DIMENSIONS.forEach(d -> header.append(String.format("%-6s", d)));
        System.out.println(header);
        BUS_MATRIX.forEach((process, dims) -> {
            StringBuilder line = new StringBuilder(String.format("  %-8s  ", process));
            for (String d : DIMENSIONS) {
                line.append(String.format("%-6s", dims.contains(d) ? "✓" : "-"));
            }
            System.out.println(line);
        });
        System.out.println();
    }

    static void consistencyCheck() {
        System.out.println("【一致性检查】");
        Map<String, Integer> usage = new LinkedHashMap<>();
        BUS_MATRIX.values().forEach(dims -> dims.forEach(d -> usage.merge(d, 1, Integer::sum)));
        usage.forEach((dim, count) -> {
            String verdict = count >= 2 ? "被 " + count + " 个过程共享（一致性维度）"
                    : "仅 1 个过程使用（检查是否应共享）";
            System.out.printf("  %-4s %s%n", dim, verdict);
        });
        System.out.println();

        List<String> orphan = new ArrayList<>();
        BUS_MATRIX.forEach((process, dims) -> {
            if (!dims.contains("日期")) {
                orphan.add(process);
            }
        });
        System.out.println("  缺日期维度的过程：" + (orphan.isEmpty() ? "无" : orphan)
                + "（任何事实表都应能按时间切）");
    }

    static void namingConflict() {
        System.out.println("【命名不一致的代价】");
        Map<String, String> cityByTable = new LinkedHashMap<>();
        cityByTable.put("fact_order.dim_user_city", "杭州");
        cityByTable.put("fact_pay.dim_user_city", "杭州市");
        cityByTable.put("fact_refund.dim_user_city", "杭州");
        System.out.println("  " + cityByTable);
        System.out.println("  按城市 join 三个事实表 -> 支付数据对不上（值域不一致）");
        System.out.println("  正解：一份 dim_user + 统一值域 + 变更走 SCD");
    }

    public static void main(String[] args) {
        build();
        printMatrix();
        consistencyCheck();
        namingConflict();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 为你的业务画出总线矩阵（≥4 个过程 × ≥5 个维度），标出一致性维度与"只被一个过程使用"的维度。
 * 2. 实现一致性检查器：自动找出"缺关键维度（日期/用户）"的过程与"命名冲突"的维度属性。
 * 3. 思考题：为什么"一致性维度"是跨主题分析的前提？构造两个主题各自建用户维度导致城市口径不一致的场景。
 * ============================================================
 */
