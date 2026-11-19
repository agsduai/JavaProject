/**
 * ============================================================
 * 课程来源：黑马程序员《2026版 AI+Java 零基础全套视频课程》
 *            B站 BV1TJxCzSEEZ（阿玮老师）
 * 说明：官方课程视频到第 14 章结束，本章为按同一格式补充的扩展内容
 * 课号：67-03
 * 课名：距离计算：Haversine 与近似
 * ------------------------------------------------------------
 * 知识点：
 *   - **Haversine 是球面距离的标准公式**：用经纬度差与两点的纬度计算大圆距离，**地球按半径 6371km 的球近似**（实际是椭球，误差 0.3% 左右，LBS 场景可接受）。
 *   - **公式的直觉**：`haversine(θ) = sin²(θ/2)`，把两点投影到单位球面上算中心角的弦长，再乘半径——**比"平面勾股 + 余弦修正"更稳**（后者在高纬度和长距离误差大）。
 *   - **平面近似的适用边界**：**短距离（<50km）且不跨极地**时，`Δlat×111km`、`Δlng×111km×cos(lat)` 的误差 <1%——**附近的人/店常用它做粗筛**（快），**精确距离再用 Haversine**（准）。
 *   - **性能优化：边界框预筛**（67-06）：先用 `lat±Δ、lng±Δ` 的矩形把候选缩小（走索引），再对候选算 Haversine——**避免对全表算距离**（39-02 的"先裁剪后精算"同款思想）。
 *   - **与 67-01/67-02 的联动**：**距离算错的三层原因**——坐标系不一致（最大误差）、纬度未修正（中纬误差）、公式本身（最小误差）。**排查从大到小**。
 *   - 易错点：用平面公式跨省算（误差几十公里）；经度差忘记乘 cos(lat)（高纬误差翻倍）；半径单位混用（米/公里）；对 1 亿条数据逐条算 Haversine（不预筛）。
 * ============================================================
 */

package com.heima.ch67;

import java.util.ArrayList;
import java.util.List;

// ==================== 示例代码 ====================

public class DistanceCalc {
    static final double EARTH_RADIUS_KM = 6371.0;

    /** Haversine 球面距离 */
    static double haversine(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.sqrt(a));
    }

    /** 平面近似（短距离可用） */
    static double planar(double lat1, double lng1, double lat2, double lng2) {
        double dLatKm = (lat2 - lat1) * 111.0;
        double dLngKm = (lng2 - lng1) * 111.0 * Math.cos(Math.toRadians((lat1 + lat2) / 2));
        return Math.sqrt(dLatKm * dLatKm + dLngKm * dLngKm);
    }

    /** 边界框：给定圆心与半径，算出经纬度范围（用于索引预筛） */
    static double[] boundingBox(double lat, double lng, double radiusKm) {
        double dLat = radiusKm / 111.0;
        double dLng = radiusKm / (111.0 * Math.cos(Math.toRadians(lat)));
        return new double[]{lat - dLat, lat + dLat, lng - dLng, lng + dLng};
    }

    record Shop(String name, double lat, double lng) {
    }

    static void nearBy(List<Shop> shops, double lat, double lng, double radiusKm) {
        System.out.printf("【附近 %.0fkm：先边界框预筛，再 Haversine 精算】%n", radiusKm);
        double[] box = boundingBox(lat, lng, radiusKm);
        System.out.printf("  边界框：lat[%.4f, %.4f] lng[%.4f, %.4f]%n",
                box[0], box[1], box[2], box[3]);
        List<String> hits = new ArrayList<>();
        int prefiltered = 0;
        for (Shop s : shops) {
            boolean inBox = s.lat() >= box[0] && s.lat() <= box[1]
                    && s.lng() >= box[2] && s.lng() <= box[3];
            if (!inBox) {
                continue;
            }
            prefiltered++;
            double d = haversine(lat, lng, s.lat(), s.lng());
            if (d <= radiusKm) {
                hits.add(String.format("%s(%.1fkm)", s.name(), d));
            }
        }
        System.out.printf("  候选 %d 个 -> 命中 %s%n", prefiltered, hits);
        System.out.println();
    }

    public static void main(String[] args) {
        System.out.println("【两种公式的误差对比】");
        double[][] cases = {
                {30.2741, 120.1551, 30.2800, 120.1600},       // 约 0.7km
                {30.2741, 120.1551, 30.5000, 120.4000},       // 约 35km
                {30.2741, 120.1551, 31.2304, 121.4737},       // 杭州-上海 约 165km
                {30.2741, 120.1551, 39.9042, 116.4074}        // 杭州-北京 约 1150km
        };
        for (double[] c : cases) {
            double h = haversine(c[0], c[1], c[2], c[3]);
            double p = planar(c[0], c[1], c[2], c[3]);
            System.out.printf("  Haversine=%8.3f km  平面=%8.3f km  误差=%5.2f%%%n",
                    h, p, Math.abs(p - h) / h * 100);
        }
        System.out.println("  短距离可平面近似；长距离必须球面");
        System.out.println();

        List<Shop> shops = List.of(
                new Shop("A 店", 30.2750, 120.1560),
                new Shop("B 店", 30.2800, 120.1600),
                new Shop("C 店", 30.3000, 120.1800),
                new Shop("D 店", 31.2304, 121.4737));
        nearBy(shops, 30.2741, 120.1551, 3.0);
        System.out.println("  预筛价值：边界框内的才精算，避免对全表算 Haversine");
    }
}

/*
 * ============================================================
 * 课后练习题（无参考答案）
 * ------------------------------------------------------------
 * 1. 实现 Haversine 与平面近似，用 4 档距离量化误差，给出"可平面近似"的距离阈值。
 * 2. 实现"边界框预筛 + 精算"：用 1000 个随机点验证结果与"全量 Haversine"一致，但计算次数下降一个数量级。
 * 3. 思考题：为什么边界框预筛是"矩形"而不是"圆形"？从索引可用性（矩形可走 B+ 树/GeoHash）分析。
 * ============================================================
 */
