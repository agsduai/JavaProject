/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：63-07
 * 课名：星型与雪花的权衡
 * ------------------------------------------------------------
 * 知识点：
 *   - **星型模型**：维度表**不规范化**（把"商品类目"直接放商品维度里，即使有冗余）——**join 次数少、查询快、易理解**；**代价是维度表冗余**（类目名重复存储）。
 *   - **雪花模型**：维度表**规范化拆分**（商品维度 -> 类目维度 -> 一级类目维度）——**省空间、维度一致性好**；**代价是 join 层数多、查询慢、理解成本高**。
 *   - **选择的判据是"分析场景的 join 成本"**：数仓分析查询动辄扫 TB 级，**每多一层 join 都是巨大的 IO 与 CPU**——**维度建模的默认答案是星型**；雪花只在"维度层级极深且需要严格一致"时使用。
 *   - **折衷方案**：**维度表适度冗余**（把常用层级属性拍平：`category_l1/category_l2` 都放商品维度）+ **一致性维度**（63-08 保证同一维度在不同事实表里定义一致）——**用"受控冗余"换查询性能**。
 *   - **与 46 章列存的配合**：星型的宽维度表 + 列存（46-02）的列裁剪，**join 时只读需要的列**——这是"星型 + 列存"成为数据仓库标准形态的原因。
 *   - 易错点：全盘雪花化（join 七八层，查询慢十倍）；星型里冗余不一致（同一类目在不同维度表名字不同）；维度表拍平后没有更新机制（冗余数据过期）。
 * ============================================================
 */

package com.heima.ch63;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==================== 示例代码 ====================

public class StarVsSnowflake {
    record SnowProduct(int productKey, String name, int categoryKey) {
    }

    record SnowCategory(int categoryKey, String categoryName, int l1Key) {
    }

    record SnowL1(int l1Key, String l1Name) {
    }

    record StarProduct(int productKey, String name, String categoryName, String l1Name) {
    }

    static void snowflakeQuery() {
        System.out.println("【雪花：三层 join】");
        Map<Integer, SnowProduct> products = Map.of(
                1, new SnowProduct(1, "手机壳 A", 10),
                2, new SnowProduct(2, "蓝牙耳机", 20));
        Map<Integer, SnowCategory> categories = Map.of(
                10, new SnowCategory(10, "手机配件", 100),
                20, new SnowCategory(20, "音频设备", 200));
        Map<Integer, SnowL1> l1 = Map.of(
                100, new SnowL1(100, "数码"),
                200, new SnowL1(200, "数码"));
        int joins = 0;
        for (SnowProduct p : products.values()) {
            SnowCategory c = categories.get(p.categoryKey());
            joins++;
            SnowL1 top = l1.get(c.l1Key());
            joins++;
            System.out.printf("  %-10s -> %-8s -> %-4s（已 join %d 次）%n",
                    p.name(), c.categoryName(), top.l1Name(), joins);
        }
        System.out.println();
    }

    static void starQuery() {
        System.out.println("【星型：维度拍平，零 join 到类目】");
        List<StarProduct> dim = List.of(
                new StarProduct(1, "手机壳 A", "手机配件", "数码"),
                new StarProduct(2, "蓝牙耳机", "音频设备", "数码"));
        dim.forEach(p -> System.out.printf("  %-10s %-8s %-4s（已 join 0 次）%n",
                p.name(), p.categoryName(), p.l1Name()));
        System.out.println();
    }

    static void tradeoff() {
        System.out.println("【权衡表】");
        String[][] rows = {
                {"join 次数", "多（层级深度）", "少（维度拍平）"},
                {"存储", "省（无冗余）", "冗余（受控）"},
                {"查询性能", "慢（海量数据放大）", "快（推荐）"},
                {"维度一致性", "天然好（一份类目）", "需一致性维度保"},
                {"适用", "维度层级极深+强一致", "绝大多数分析场景"},
        };
        for (String[] r : rows) {
            System.out.printf("  %-8s 雪花=%-18s 星型=%s%n", r[0], r[1], r[2]);
        }
        System.out.println();
        System.out.println("  折衷：维度适度拍平（category_l1/l2 放一张表）+ 一致性维度（63-08）");
    }

    public static void main(String[] args) {
        snowflakeQuery();
        starQuery();
        tradeoff();
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 为同一份商品数据分别设计雪花（3 层）与星型（拍平）模型，对比"查某一级类目下商品数"的 join 次数与可读性。
 * 2. 设计"适度拍平"方案：哪些属性拍平、哪些保留引用，给出判据（查询频率 × 层级深度）。
 * 3. 思考题：为什么"星型 + 列存"是数据仓库的标准形态？从 join 成本与列裁剪的协同效应分析。
 * ============================================================
 */
