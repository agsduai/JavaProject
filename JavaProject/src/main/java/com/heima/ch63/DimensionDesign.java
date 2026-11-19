/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：63-03
 * 课名：维度表设计：代理键与退化维度
 * ------------------------------------------------------------
 * 知识点：
 *   - **代理键（Surrogate Key）是无业务含义的自增整数**：维度表用 `product_key` 而不是 `product_id`（业务主键）——**代理键让维度可以保留历史版本**（同一商品多次变更 -> 多个代理键，63-04），业务主键仍然保留为普通属性。
 *   - **代理键的生成与稳定性**：数仓内部统一生成（自增/哈希），**不依赖业务库的 id**（业务库可能分库分表 39 章、id 可能复用）；代理键一旦分配**永不复用**。
 *   - **退化维度（Degenerate Dimension）**：没有独立维度表的维度（订单号、支付流水号）——**直接放在事实表里**，因为它没有描述属性、只用于"精确查找某笔单据"。
 *   - **维度表的其他常见属性**：**自然键**（业务 id）、**维度属性**（名称/类目/等级）、**管理属性**（`etl_time`/`is_current`/`version`）、**默认成员**（`-1` 未知/`0` 占位，处理 NULL 外键）。
 *   - **维度表的"宽"与"稳定"**：维度表通常**宽而稳定**（几百列、变化慢）；**事实表窄而长**（几列 + 海量行）——**这是维度建模的性能来源**（宽表减少 join、事实表小而快）。
 *   - 易错点：用业务 id 当代理键（历史版本冲突）；NULL 外键直接入事实表（关联不上，需默认成员）；退化维度硬造维度表（无意义的空表）；维度表频繁更新属性（未用 SCD 63-04）。
 * ============================================================
 */

package com.heima.ch63;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class DimensionDesign {
    /** 维度行：代理键 + 自然键 + 属性 + 管理字段 */
    record ProductDim(int productKey, String productId, String name, String category,
                      String brand, boolean isCurrent) {
    }

    /** 事实行：外键（代理键）+ 退化维度 + 度量 */
    record OrderItemFact(long orderId, int dateKey, int userKey, int productKey,
                         int quantity, long amountCents) {
    }

    static void surrogateKey() {
        System.out.println("【代理键与历史版本】");
        List<ProductDim> dim = new ArrayList<>();
        dim.add(new ProductDim(1001, "P-100", "手机壳 A", "配件", "自有", false));
        dim.add(new ProductDim(1002, "P-100", "手机壳 A（改名）", "配件", "自有", true));
        dim.add(new ProductDim(1003, "P-200", "蓝牙耳机", "音频", "第三方", true));
        dim.forEach(d -> System.out.printf("  key=%-5d id=%-6s name=%-12s current=%b%n",
                d.productKey(), d.productId(), d.name(), d.isCurrent()));
        System.out.println("  同一商品 P-100 有两个代理键 -> 历史事实仍指向旧版本（可追溯）");
        System.out.println();
    }

    static void degenerate() {
        System.out.println("【退化维度：无独立维度表的维度】");
        List<OrderItemFact> facts = List.of(
                new OrderItemFact(9001, 20261118, 501, 1002, 2, 13800),
                new OrderItemFact(9002, 20261118, 502, 1003, 1, 29900));
        facts.forEach(f -> System.out.printf("  orderId=%-5d date=%-9d user=%-4d product=%-5d 数量=%d 金额=%d分%n",
                f.orderId(), f.dateKey(), f.userKey(), f.productKey(), f.quantity(), f.amountCents()));
        System.out.println("  订单号直接放事实表（无描述属性、只用于定位单据）");
        System.out.println();
    }

    static void defaultMember() {
        System.out.println("【默认成员：处理 NULL 外键】");
        Map<Integer, String> dim = new LinkedHashMap<>();
        dim.put(-1, "未知（来源缺失）");
        dim.put(0, "占位（不适用）");
        dim.put(1002, "手机壳 A（改名）");
        long nullFkRows = 3;
        long totalRows = 100;
        System.out.printf("  维度表含默认成员：%s%n", dim);
        System.out.printf("  外键为 NULL 的事实行 %d/%d -> 关联到 key=-1，统计不丢行%n",
                nullFkRows, totalRows);
        System.out.println("  反模式：内连接维度表会丢掉 NULL 外键的行（汇总对不上）");
    }

    public static void main(String[] args) {
        surrogateKey();
        degenerate();
        defaultMember();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现维度表设计与事实表关联：构造"商品改名"场景，验证历史事实指向旧代理键、新事实指向新代理键。
 * 2. 对比"内连接维度表"与"含默认成员的外连接"在 100 行事实（含 3 行 NULL 外键）上的汇总差异。
 * 3. 思考题：为什么代理键"永不复用"？从"历史事实的可追溯性"与"事实表外键稳定性"分析。
 * ============================================================
 */
